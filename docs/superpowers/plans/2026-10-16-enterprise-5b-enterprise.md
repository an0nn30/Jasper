# Enterprise presentation 5b: the Enterprise plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turning Enterprise on gives classic controls, metrics and fonts: a kit's declared faces are registered and
drawn (with a per-face fallback and a notice), plugins contribute themes through a reserved plugin theme tier with
dormant ids, and the bundled, off-by-default `plugins/enterprise` plugin ships its kit, IBM Plex fonts with their OFL
notice, the Enterprise theme (Light and Dark, spec 7.6) and `dev.jasper.enterprise.api.Workstation`, proven by the
conformance test, the fixture comparison and the shipped-kit renders compared with the Workstation and Dark Mode mocks.

**Architecture:** jasper-ui's `FaceSet` names UI faces as well as data faces by registered full name, and `Typography`
draws a role in its kit's face when that face is registered, else in the platform face for that role. `KitParser`
builds the `FaceSet` from the font files' name tables (plan 5a's `FontFile`); the application registers the files right
after the bundled fonts, refusing a file whose family is not the declared one. The appearance package gains
`PluginThemes`, built by `PluginSelection` from every discovered plugin's `themes`, and `ThemeLibrary` holds built-ins,
then plugin themes, then folder themes. The Enterprise plugin is data and one call: `EnterprisePlugin.start` publishes
`Workstation`.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, tomlj 1.1.1, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3
and AssertJ 3.27.7, Python 3.11+ standard library (always `python3 -I`), `curl`, `unzip` and `shasum` for the font files.

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1, 3, 5.6, 7.4 to 7.7, 14.1, 14.2, 15, 16.1.2, 16.1.5, 16.1.6, 16.1.10, 17 and row 5 of 19.1). This is the second
of the two plans of spec row 5; [5a](2026-10-16-enterprise-5a-provider.md) (the provider machinery) executes first on the
same branch and its interfaces are used here by name. Spec 7.7 (the converter's override keys and goldens) was
delivered by plan 2 (`tools/themes/jasper_theme.py:57`, `:966-982`); this plan only relies on it.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-5`, branch
`claude/enterprise-provider`, which holds `main` at `e502e102b` (plans 3a to 3e, 4a to 4c, 5a, 6a and 8a) plus the plan
commits. Run every command from the worktree root. Line numbers were re-verified at `e502e102b` on 2026-10-17 (Execution
notes); every edit quotes the text it replaces and stops when that text is not found the stated number of times;
re-find moved text, never edit around it. Plan 6b runs in parallel in another worktree and may reach `main` first: Task 3
step 5 and Task 4 step 4 say what changes then.

## Before you start

```bash
grep -c 'Presentation provider: documentation and status' <(git log --format=%s)      # 5a: 1
ls jasper-app/src/main/java/dev/jasper/app/presentation/KitParser.java jasper-app/src/main/java/dev/jasper/app/plugins/PluginSelection.java
grep -c 'static .*enterprise()' jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java   # plan 4a's fixture kit: 1
ls jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicTableFace.java   # 4b, 4c
ls jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java jasper-app/src/test/resources/dev/jasper/app/testsupport/enterprise-fixture.json   # 6a
grep -c 'CHECKED = java.util.EnumSet.of(Owner.RUN_WINDOW, Owner.FACES);' jasper-app/src/test/java/dev/jasper/app/palette/RunWindowRenderTest.java   # 8a merged with 4b: 1
grep -c 'BORDER_DIVIDER("border.divider", true)' jasper-ui/src/main/java/dev/jasper/ui/theme/DerivedColor.java   # 1 once plan 6b's Task 1 is on main
```

If 5a is not on the branch, execute it first. Plan 4's Enterprise fixture kit is used under the name
`KitFixtures.enterprise()` (spec 19.1 row 4: "the Enterprise fixture kit"); if plan 4 named it otherwise, use its name in
Task 4. The names of plan 5a's "Plan 4's names" table apply here too.

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop`.
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache; `:jasper-ui:gallery` is headless and may be run.
- Work only in the worktree above on `claude/enterprise-provider`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel rule: Enterprise is off by default and no default-look painter changes, so every file the headless gallery writes for Slate Cobalt Dark and Light is byte-identical to the base and every reference pair and calibration suite stays green unchanged. The only new images are this plan's own renders under `jasper-app/build/enterprise-kit/`. Task 5 proves it. `KitFixtures.themes()` gains Enterprise Light and Dark, which only adds cases to `KitMatrixTest` and `PaintedContrastTest` (spec 16.1.2; plan 2's planned deviation 2).
- The IBM Plex files are exactly the OFL-1.1 TTFs of IBM's `@ibm/plex-sans@1.1.0` and `@ibm/plex-mono@1.1.0` GitHub releases (2024-11-13), checked by the SHA-256 sums in Task 3, renamed only; no other source and no other font.
- The Enterprise descriptor is spec 4.1's plus the icon lines of Rulings 6; the kit file is spec 5.1's without `[branding]` (plan 11 adds it); the theme is spec 7.6's tables exactly, the unnamed roles by Rulings 7.
- The plugin compiles against the SDK only, has no requirements and imports no plugin API (`verifyPluginArchitecture`); `EnterprisePlugin.start` only publishes `Workstation`.
- Javadoc doclint fails the build on errors (`-Xdoclint:all` in jasper-app and the SDK, `-Xdoclint:all,-missing` in jasper-ui); every `{@link}` resolves; no raw `<`, `>`, `&` or `->` in a comment.
- `docs/design/theme-engine/migration.csv`: every new source the ledger's pattern matches gets its row in the same commit (plan 5a's Global Constraints give the form), and `plugins/enterprise` owns at least one row as `dev.jasper.enterprise`.
- Third-party notices: IBM Plex is a `notices.toml` component whose new `files` key lists every font file a bundled plugin ships; `verifyThirdPartyNotices` checks it.
- Documentation edits are the smallest that make each document accurate; history and earlier STATUS entries are not rewritten.
- Source hygiene: no raw control, private-use or unpaired surrogate characters in any source file.

## Review Focus

1. A damaged install: one of the five Plex files is missing its name table, is not a font, or has another family:
   that face draws in the platform face, the look stays, and one Warning names the file. Task 1:
   `LookFontsTest` and `PresentationAttemptTest`-style `LookNoticeTest.aLookMissingFilesPostsOneWarning`.
2. A user who saved `ui.theme.dark = "enterprise"` and later turns Enterprise off: the next start shows Slate Cobalt
   with no configuration warning, the pick stays saved, and turning Enterprise on again restores it; a theme folder
   file named `enterprise.json`, and an import of a theme named "Enterprise", are refused while the plugin is discovered.
   Task 2: `PluginThemesTest`.
3. Two plugins that ship a theme with the same id (a bundled one and a `--plugin-dir` one), or one that collides with a
   built-in: the DEV copy wins, the other is dropped with a reported problem, and a built-in id is never shadowed. Task 2:
   `PluginThemesTest.theTierPrefersDevThenBundledThenUserThenTheLowestIdAndNeverShadowsABuiltIn`.
4. A fresh home with Enterprise opted in: the first snapshot carries `palette.visible_rows = 8`, `window.toolbar_height =
   34` and the Enterprise theme for both appearances, and the theme library offers Enterprise. Task 4:
   `EnterpriseHomeTest.aFreshHomeUnderEnterpriseStartsWithTheKitsDefaults`.
5. Enterprise under every theme (spec 3): the guards keep every declared pair readable over Enterprise Light and Dark as
   over the rest of the theme set, and the Enterprise colours match what the Workstation and Dark Mode mocks paint.
   Task 3: `EnterpriseThemeTest.theThemeMatchesTheMocksWhereTheyShowIt`; Task 4: `KitFixtures.themes()` with Enterprise
   in `PaintedContrastTest`.

## Verified facts

1. Plan 5a's `KitParser.Faces`, `UiFaces`, `DataFaces`, `FontFile.read` (full name ID 4, family ID 16 else 1),
   `PluginSelection.scan`, `PluginResources.of`, `Presentation.faces()`, `PresentationFacts.MAX_THEME_BYTES`, and its
   fact 19 on JBR font naming.
2. `FaceSet` holds only data faces (`FaceSet.java:10-23`, plan 1's deviation 4: "the UI faces in plan 5");
   `Typography.font` picks the data face for the MONO roles and plan 8a's `RUN_DETAIL` and the platform family otherwise
   (`Typography.java:73-91`).
3. `BundledFonts.register()` registers the Cousine faces once (`BundledFonts.java:16-30`); the application calls it first
   in its constructor (`JasperApplication.java:289`).
4. `ThemeController`'s constructor clears `defaultFont` and the form fonts before installing (`ThemeController.java:162-164`);
   `installFontDefaults` puts `defaultFont` only for a non-default `UiFontConfig` (`:243-251`); `SwingTypography.user`
   reads the developer `defaultFont` and `Jasper.uiFontFamilyOverride` (`SwingTypography.java:32-38`).
5. `KitGallery.installed` and `capture` set `defaultFont` to the platform family at the kit's body size at the gallery's
   scale, `bodySize(points)` (24 points for `*font24*`; plan 4c) (`KitGallery.java:190-222`); `KitFixtures.themes()` reads
   the converter goldens from `jasper.repoRoot` (`KitFixtures.java:134-147`), which jasper-ui's test task sets
   (`jasper-ui/build.gradle.kts:22`).
6. `ThemeLibrary` holds built-ins then imported themes (`ThemeLibrary.java:51-71`), skips a folder file with a built-in id
   (`:104-108`), and reuses an unchanged library (`:128`); `ThemeSelection.pick` reports an unknown pick and uses Slate
   Cobalt (`ThemeSelection.java:48-56`); `ThemeFolder.prepare` refuses built-in ids (`ThemeFolder.java:66-67`);
   `ThemeMenu.removable` offers removal for any listed non-built-in theme (`ThemeMenu.java:74-77`); the application makes
   the import folder at `JasperApplication.java:1067` and reads the library at `:297-298`; `ConfigurationController` rereads
   the folder with `ThemeLibrary.read(themesFolder, library)` (`ConfigurationController.java:122`).
7. `InstalledThemes.ThemeOption(id, label, preview)` (`InstalledThemes.java:21`); `SettingsAppearance.themeChoices` builds
   them (`SettingsAppearance.java:53-60`); `AppearanceSelection.options` makes each dropdown option with no description
   (`AppearanceSelection.java:89-95`); `Option(id, label, hue)` is `Option(id, label, hue, "", "", empty)`
   (`jasper-ui/.../model/Option.java:14`).
8. `ColourLiteralScanTest.THEMES` exempts only the built-in theme files (`ColourLiteralScanTest.java:15-16`, used at `:42` and `:57`).
9. `stagePlugins` lists each bundled plugin (`jasper-app/build.gradle.kts:42-58`); `settings.gradle.kts` includes every
   `plugins/<name>` with a build script; `pluginZips` zips every plugin (`gradle/plugin-packaging.gradle.kts:18-40`).
   `BundledSamplePluginTest` counts eight bundled plugins (`:78`, `:116`) and `PluginZipsTest` eight zips with their ids
   (`PluginZipsTest.java:19,42`).
10. `verifyThirdPartyNotices` reads `notices.toml` components' `modules`, `text` and `notice` with regular expressions
    (`gradle/third-party-notices.gradle.kts:20-39`); `ThirdPartyNotices.parse` ignores keys it does not read
    (`ThirdPartyNotices.java:87-101`); `OFL-1.1` is a known licence (`:30`).
11. The IBM Plex 1.1.0 releases (fetched 2026-10-16): `ibm-plex-sans.zip` SHA-256
    `fb365d910566e6d199cc2c15579a7dd9a267128e18431a394ed81f1970c69200` and `ibm-plex-mono.zip`
    `4bfc936d0e1fd19db6327a3786eabdbc3dc0d464500576f6458f6706df68d26c`; the five TTFs are listed in Task 3 with their sums;
    both zips' `LICENSE.txt` (SHA-256 `7e6b2818edbd8f6a01ae80641cc8f16a51080d08fb4e532be3a0b6f74adb07da`) is the OFL 1.1 with
    "Copyright © 2017 IBM Corp. with Reserved Font Name "Plex"". Renaming a file is not a Modified Version under the OFL.
12. Sampled at 2× pixels on 2026-10-16, the Workstation mock paints (2592,835) #FFFFFF, (2448,455) #2F65CA, (1728,144)
    #ECEDEF, (2434,204) #D9DFEA, (2600,1158) #F0F1F3, (700,1730) #E4E6E9, (1296,864) #FFFFFF; the Dark Mode mock at the same
    points #2E3135, #3D74D6, #3C3F44, #3D4658, #393C41, #383B40, #2E3135 (card, selection, toolbar row, focused header,
    table header, bottom strip, terminal).
13. A theme file built by Task 3's table loads with plan 2's loader (`JasperTheme.parse`, checked on 2026-10-16 against
    `main`'s jasper-ui jar): both variants, three schemes, eight light and seven dark overrides (nine and eight with plan
    6b's `border.divider`, Task 3 step 5).
14. `FakePluginHost.start(info, requires, optional, plugin)` starts a plugin and publishes its staged services to
    plugins that require it (`FakePluginHost.java:219-240`); services are published only in `start`
    (`FakePluginContext.java:200-207`).
15. On JBR 25 (checked 2026-10-16) the five Plex files' `Font.getFontName` are "IBM Plex Sans", "IBM Plex Sans Medm",
    "IBM Plex Sans SmBld", "IBM Plex Mono" and "IBM Plex Mono Medm", and each resolves by that name after
    `registerFont`. `javac` compiles `EnterprisePlugin.start` to exactly `PluginContext.services`, `EnterprisePlugin$1.<init>`
    and `Services.publish` (checked with `javap -c`). Task 3's theme file meets every value its test asserts, including
    the 0.6 L* tolerance of the unnamed-role rule (largest gap 0.17) and Dark `status.danger` at 4.50:1 on the card.
16. `tools/visual/pairs.json` is a list of `{name, mock, crop, render}` with `crop` as `x,y,w,h` in mock pixels
    (`tools/visual/README.md`), with an optional `min_agreement` and `colours` (plan 4c); `compare_all.py OUT_DIR [FILTER]`
    runs them and `check_pairs.py OUT_DIR [FILTER]` passes or fails the ones with a floor. `ThirdPartyNoticesTest` requires the
    components after Jasper sorted by name ignoring case (`ThirdPartyNoticesTest.java:27`), so "IBM Plex" goes before
    "IntelliJ Platform icons". tomlj's `TomlTable.keyPathSet()` lists every leaf key path, quoted keys whole.

## Rulings

Record each in `docs/STATUS.md` (Task 5) and in this plan's status banner.

1. **Faces are full names; a face that does not resolve degrades.** `FaceSet.of(uiRegular, uiMedium, uiSemibold,
   dataRegular, dataMedium)` names faces by registered full name; `Typography` draws a role in its face when
   `new Font(name)` resolves to that face, else the platform face (UI) or `Font.MONOSPACED` (data). Nothing is cached, so
   the order of registration and first use cannot fix a wrong answer. Cost if wrong: a font lookup per call, as today.
2. **The look's standard UI font reaches FlatLaf.** Under a kit that names a registered UI face, the theme controller
   installs that face at the kit's body size as `defaultFont` with the family override off, so FlatLaf-drawn Swing text
   (menus, native tables) uses it too; a set `ui.font.family` or `ui.font.size` still overrides it. The default kit is
   unchanged. Cost if wrong: Swing chrome in the platform font under Enterprise.
3. **One notice for a look's missing files.** Fonts that fail to register and `[defaults]` themes that did not load are
   listed in one Warning, "The <Name> look is missing some of its files", in the Plugins group (spec 5.8, 5.9 and 15
   say "a notice" and "named in the plugin's problem notice"). Cost if wrong: copy.
4. **The plugin theme tier's order and messages.** Plugin themes are listed by name after the built-ins and before the
   folder's; a losing or built-in-colliding plugin file is a library problem ("<Theme> is provided by the <Plugin>
   plugin; the <Other> plugin's file is ignored." and "<Theme> is built in; the <Plugin> plugin's file is ignored."),
   shown as a configuration warning like a bad folder file; only loadable plugins' files are read, so a dormant theme is
   named by its id. Cost if wrong: copy.
5. **Plugin themes cannot be removed or replaced** from Settings: `ThemeFolder.fixed(id)` (built in or provided by a
   discovered plugin) hides Remove, and Import refuses the id. The Appearance dropdown captions a plugin theme
   "From the <Plugin> plugin". Cost if wrong: one option.
6. **The descriptor has an icon.** Every bundled plugin ships an icon (`BundledSamplePluginTest`), so Enterprise adds
   `icon = "icons/enterprise.svg"`, `icon_hue = "blue"` and `icon_selected_ink = "foreground"` to spec 4.1's keys: a
   window outline with a title and a menu row. Cost if wrong: three lines and a file.
7. **The Enterprise theme's unnamed roles.** A neutral role 7.6 does not name takes, in Light, Slate Cobalt Light's L*
   (both cards are white, so the ΔL* from the card is kept) with the a* and b* of the named Enterprise neutral nearest in
   L* ("the chroma of the Enterprise neutrals"); in Dark, the card's L* plus that Light ΔL* (the lighter side) with the
   chroma of the nearest named Dark neutral. Neutral roles are the surfaces, the opaque non-error borders, the
   field-raised text, the segment, toggle and secondary-button roles, `status.idle` and `hue.neutral`. Translucent roles,
   links, errors, successes, `accent.hover` and the tab indicators keep Slate Cobalt's value for the variant (with plan 6b
   on the branch, `surface.tabstrip`, `tab.indicator`, `surface.scrim` and `border.divider` take 6b's ruling 2: Task 3
   step 5). Dark's
   `status.danger`, `hue.purple` and the `*.secondary` text on the accent keep their Light hue lightened in 0.25 L* steps
   to 4.5:1 on the card (7.6). The `light` scheme is Slate Cobalt Light's with the background #FFFFFF the mock and
   `surface.terminal` show; `dark` and `dark-terminal` take Slate Cobalt Dark's bold foreground, cursor and selection
   with their 7.6 backgrounds and foregrounds; the schemes are named "Enterprise Light", "Enterprise Dark" and
   "Enterprise Dark Terminal"; the editor schemes are Slate Cobalt's references. Cost if wrong: retuning values in one file.
8. **The apart direction (plan 2's hand-off).** Enterprise Dark authors its headers, stripe, table header and zebra, so
   `SurfaceContrast.apart`'s direction reaches only `surface.footer` there; the rule is left to plan 7, which owns the
   header strips. Cost if wrong: plan 7 revisits one derived role.
9. **The mock comparison.** The JUnit part samples the mocks where they paint a theme role (fact 12); the visual part
   is `tools/visual/pairs.json` crops pairing 2x renders of the four gallery panels (Sessions, Tunnels, SFTP, File
   Servers) under the shipped kit with the mocks' panel regions, with floors and colours that plan 4c's `check_pairs.py`
   holds (Execution notes, ruling 11). The classic compositions those regions show (the
   Sessions tree, Properties, the tunnels table) and the workspace chrome are plans 10, 6 and 7's, so the pairs compare
   controls, metrics and type only, and later plans re-pair them. Cost if wrong: crops move.
10. **Font registration accepts the typographic family.** A static face's legacy family (ID 1) carries its weight
    ("IBM Plex Sans Medm"), so the declared `family` is compared with name ID 16 (fact 19 of 5a). Cost if wrong: none.
11. **The branding files are plan 11's.** Spec 14.1 lists `presentation/icons/icon-16.png` to `icon-1024.png` and
    `presentation/splash/art.svg`; they belong to the `[branding]` table, which plan 11 adds with its validation, so this
    plan ships neither and the kit file has no `[branding]`. Cost if wrong: plan 11 adds the files it needs anyway.

---

### Task 1: A kit's faces: `FaceSet`, `Typography`, registration and the notice

A kit names its UI face (regular, medium, semibold) and data face (regular, medium) by file (spec 5.6). `FaceSet` now
holds both by registered full name, `KitParser` builds it from the files' name tables, the application registers the
files right after the bundled fonts (checking each file's family), and `Typography` draws a role in its face when that
face is registered, else in the platform face for that role. Under such a kit the theme controller installs the look's
standard UI font for FlatLaf. A look whose files are missing posts one Warning. The default kit is unchanged.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/FaceSet.java` (rewrite), `Typography.java:73-91`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java:178-222`
- Modify: `jasper-app/src/main/java/dev/jasper/app/presentation/KitParser.java` (faces), `Presentation.java` (dropped defaults)
- Create: `jasper-app/src/main/java/dev/jasper/app/application/LookFonts.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (constructor, `startPlugins`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java:46,162-164,234-246`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/metrics/FaceSetTest.java`; `jasper-app/src/test/java/dev/jasper/app/application/LookFontsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/application/LookNoticeTest.java`; `jasper-app/src/test/java/dev/jasper/app/presentation/KitParserTest.java`

**Interfaces:**
- Consumes: `KitParser.Faces`, `FontFile` (5a Task 3); `Presentation.faces()` (5a Task 8).
- Produces: `FaceSet.of(String uiRegular, String uiMedium, String uiSemibold, String dataRegular, String dataMedium)`
  (empty UI names keep the platform face for that weight, empty data names `Font.MONOSPACED`), `namesUiFaces()`,
  `uiFace(int weight)`, `dataRegular()`, `dataMedium()`, value equality; `Presentation.droppedDefaults()` (the
  `[defaults]` keys whose theme did not load); `static List<String> LookFonts.register(KitParser.Faces faces)` (the
  problems, one line each).

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/metrics/FaceSetTest.java`:

```java
package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Spec 5.6: a kit's faces by registered full name, and the platform face for any that is not registered. */
class FaceSetTest {
    @Test void facesAreNamedPerWeightAndCompareByValue() {
        var faces = FaceSet.of("Look Sans", "Look Sans Medm", "Look Sans SmBld", "Look Mono", "Look Mono Medm");
        assertThat(faces.namesUiFaces()).isTrue();
        assertThat(faces.uiFace(400)).isEqualTo("Look Sans");
        assertThat(faces.uiFace(500)).isEqualTo("Look Sans Medm");
        assertThat(faces.uiFace(600)).isEqualTo("Look Sans SmBld");
        assertThat(faces.dataRegular()).isEqualTo("Look Mono");
        assertThat(faces).isEqualTo(FaceSet.of("Look Sans", "Look Sans Medm", "Look Sans SmBld", "Look Mono", "Look Mono Medm"))
            .hasSameHashCodeAs(FaceSet.of("Look Sans", "Look Sans Medm", "Look Sans SmBld", "Look Mono", "Look Mono Medm"));
        assertThat(FaceSet.PLATFORM.namesUiFaces()).isFalse();
        assertThat(FaceSet.of("", "", "", "", "")).isEqualTo(FaceSet.PLATFORM);
    }

    @Test void aFaceThatIsNotRegisteredDrawsInThePlatformFace() {
        var platform = Typography.of(TypeScale.DEFAULT, FaceSet.PLATFORM, Typography.UserFont.NONE);
        var missing = Typography.of(TypeScale.DEFAULT, FaceSet.of("No Such Face 5b", "", "", "No Such Mono 5b", ""), Typography.UserFont.NONE);
        for (var role : Typography.Role.values()) {
            assertThat(missing.font(role).getFontName(Locale.ROOT)).as(role.name()).isEqualTo(platform.font(role).getFontName(Locale.ROOT));
            assertThat(missing.font(role).getSize2D()).as(role.name()).isEqualTo(platform.font(role).getSize2D());
        }
        assertThat(missing.font(Typography.Role.MONO).getFamily(Locale.ROOT)).isEqualTo(new Font(Font.MONOSPACED, Font.PLAIN, 1).getFamily(Locale.ROOT));
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/application/LookFontsTest.java` (the faces are the bundled Cousine
files renamed to a family no machine installs, because an installed copy of a font hides the file's full name on macOS:
Execution notes, ruling 3):

```java
package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.FontFile;
import dev.jasper.app.presentation.KitParser;
import dev.jasper.ui.metrics.FaceSet;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.metrics.Typography;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 5.6 and 5.9: a kit's font files register under their declared family, and a role draws in its face. */
class LookFontsTest {
    static byte[] bundled(String style) throws IOException {
        String path = "/dev/jasper/app/fonts/cousine/CousineNerdFontMono-" + style + ".ttf";
        try (var in = LookFontsTest.class.getResourceAsStream(path)) { return in.readAllBytes(); }
    }

    /** The bundled Cousine face as it ships; a machine that has Cousine installed may hide it. */
    static FontFile cousine(String style) throws IOException { return FontFile.read("fonts/cousine-" + style + ".ttf", bundled(style)); }

    /** Cousine with its family renamed in the name table (same length), so it registers on every machine. */
    static FontFile lookFace(String style) throws IOException {
        byte[] bytes = bundled(style);
        for (var charset : List.of(StandardCharsets.UTF_16BE, StandardCharsets.ISO_8859_1)) {
            byte[] from = "Cousine Nerd Font Mono".getBytes(charset), to = "Jasper Look Face Tests".getBytes(charset);
            for (int i = 0; i + from.length <= bytes.length; i++)
                if (Arrays.equals(bytes, i, i + from.length, from, 0, from.length)) System.arraycopy(to, 0, bytes, i, to.length);
        }
        return FontFile.read("fonts/look-" + style + ".ttf", bytes);
    }

    @Test void filesOfTheDeclaredFamilyRegisterAndTheirRolesDrawInThem() throws Exception {
        FontFile regular = lookFace("Regular"), bold = lookFace("Bold");
        assertThat(regular.named()).isTrue();
        assertThat(regular.family()).isEqualTo("Jasper Look Face Tests");
        var faces = new KitParser.Faces(Optional.of(new KitParser.UiFaces(regular.family(), regular, regular, bold)),
            Optional.of(new KitParser.DataFaces(regular.family(), regular, bold)));
        assertThat(LookFonts.register(faces)).isEmpty();
        assertThat(LookFonts.register(faces)).as("registering again is harmless").isEmpty();
        var typography = Typography.of(TypeScale.DEFAULT, FaceSet.of(regular.fullName(), regular.fullName(), bold.fullName(),
            regular.fullName(), bold.fullName()), Typography.UserFont.NONE);
        assertThat(typography.font(Typography.Role.BODY).getFontName(Locale.ROOT)).isEqualTo(regular.fullName());
        assertThat(typography.font(Typography.Role.SECTION_LABEL).getFontName(Locale.ROOT)).as("weight 600").isEqualTo(bold.fullName());
        assertThat(typography.font(Typography.Role.MONO).getFontName(Locale.ROOT)).isEqualTo(regular.fullName());
        assertThat(typography.font(Typography.Role.BODY).getSize2D()).isEqualTo(13f);
    }

    @Test void aFileOfAnotherFamilyOrWithoutNamesIsNamedAndNotRegistered() throws Exception {
        var regular = cousine("Regular");
        var unnamed = FontFile.read("fonts/broken.ttf", new byte[] {1, 2, 3});
        var faces = new KitParser.Faces(Optional.of(new KitParser.UiFaces("Another Family", regular, unnamed, regular)), Optional.empty());
        assertThat(LookFonts.register(faces)).containsExactly(
            "fonts/cousine-Regular.ttf: its family is " + regular.family() + ", not Another Family; that text uses the platform font",
            "fonts/broken.ttf: its names cannot be read; that text uses the platform font",
            "fonts/cousine-Regular.ttf: its family is " + regular.family() + ", not Another Family; that text uses the platform font");
    }

    /** Spec 5.9: a file whose full name does not resolve once registered (an installed font of its family hides it) is named. */
    @Test void aFileAnInstalledFontHidesIsNamedWhereItIsHidden() throws Exception {
        var regular = cousine("Regular");
        var problems = LookFonts.register(new KitParser.Faces(Optional.empty(), Optional.of(new KitParser.DataFaces(regular.family(), regular, regular))));
        boolean hidden = !new Font(regular.fullName(), Font.PLAIN, 1).getFontName(Locale.ROOT).equals(regular.fullName());
        String line = "fonts/cousine-Regular.ttf: an installed font named " + regular.family() + " hides it; that text uses the platform font";
        assertThat(problems).isEqualTo(hidden ? List.of(line, line) : List.of());
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/application/LookNoticeTest.java`:

```java
package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.plugins.PluginSelection;
import dev.jasper.app.testsupport.LookFixtures;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Map;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 5.8, 5.9 and 15: a look whose fonts or default themes did not load still starts, and says so once. */
class LookNoticeTest {
    @TempDir Path home;

    @Test void aLookMissingFilesPostsOneWarning() throws Exception {
        Path dev = LookFixtures.loadable(home.resolve("dev"), "dev.example.look", LookFixtures.descriptor("dev.example.look", "Example")
            + LookFixtures.provider("look.kit.toml") + "themes = [\"themes/look.json\"]\n", Map.of(
                "look.kit.toml", "kit = 1\nbase = \"default\"\n[type.ui]\nfamily = \"Example Sans\"\nregular = \"fonts/a.ttf\"\n"
                    + "medium = \"fonts/a.ttf\"\nsemibold = \"fonts/a.ttf\"\n[defaults]\n\"ui.theme.dark\" = \"look\"\n",
                "fonts/a.ttf", "not a font", "themes/look.json", "{ not a theme"));
        var selection = PluginSelection.of(new AppDirs(home, home.resolve("config.toml"), home.resolve("logs")), null, "", dev, false);
        assertThat(selection.presentation().look()).as("the look stays").isEqualTo("dev.example.look");
        var original = UIManager.getLookAndFeel();
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
                application[0].startPlugins(new AppDirs(home, home.resolve("config.toml"), home.resolve("logs")));
                var notices = application[0].notificationCenter().history().stream().map(entry -> entry.request())
                    .filter(request -> request.title().equals("The Example look is missing some of its files")).toList();
                assertThat(notices).singleElement().satisfies(request -> {
                    assertThat(request.severity()).isEqualTo(Severity.WARNING);
                    assertThat(request.content()).isEqualTo(String.join("\n",
                        "fonts/a.ttf: its names cannot be read; that text uses the platform font",
                        "fonts/a.ttf: its names cannot be read; that text uses the platform font",
                        "fonts/a.ttf: its names cannot be read; that text uses the platform font",
                        "[defaults].\"ui.theme.dark\": its theme did not load, so the setting keeps its core default"));
                });
            });
        } finally {
            edt(() -> { if (application[0] != null) application[0].quitNow(); });
            edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
        }
    }
}
```

In `KitParserTest.theShippedShapeParsesIntoEveryValueItNames`, add after the `colors()` assertions (the line
`assertThat(parsed.kit().colors().navigationSurface()).isEqualTo(SemanticColor.SURFACE_CARD);`, once in the file):

```java
        assertThat(parsed.kit().faces()).isEqualTo(dev.jasper.ui.metrics.FaceSet.of(PLEX_SANS, "IBM Plex Sans Medm", "IBM Plex Sans SmBld",
            PLEX_MONO, "IBM Plex Mono Medm"));
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.FaceSetTest' :jasper-app:test --tests 'dev.jasper.app.application.LookFontsTest' --tests 'dev.jasper.app.application.LookNoticeTest' --tests 'dev.jasper.app.presentation.KitParserTest' --continue`
Expected: FAIL; jasper-ui's `compileTestJava` reports "cannot find symbol: method of(…)" on `FaceSet`, and jasper-app's
reports "cannot find symbol: class LookFonts".

- [ ] **Step 3: Name the UI faces and draw them**

Replace `jasper-ui/src/main/java/dev/jasper/ui/metrics/FaceSet.java` with:

```java
package dev.jasper.ui.metrics;

import java.awt.Font;
import java.util.Objects;

/**
 * The faces a kit draws text in, by registered full name (spec 5.6): a UI face per weight and a data face per weight. JBR
 * does not pick the weights of static faces through {@code TextAttribute.WEIGHT}, so each weight names its own file's
 * face. An empty UI name keeps the platform policy of {@link Typography} for that weight; the data roles draw in
 * {@link #dataRegular()}, or in {@link #dataMedium()} from weight 500. A face that is not registered draws in the
 * platform face for its role. Compared by value.
 */
public final class FaceSet {
    /** Today's policy: the platform UI family and {@link Font#MONOSPACED} for the data roles. */
    public static final FaceSet PLATFORM = new FaceSet("", "", "", Font.MONOSPACED, Font.MONOSPACED);

    private final String uiRegular, uiMedium, uiSemibold, dataRegular, dataMedium;

    private FaceSet(String uiRegular, String uiMedium, String uiSemibold, String dataRegular, String dataMedium) {
        this.uiRegular = uiRegular; this.uiMedium = uiMedium; this.uiSemibold = uiSemibold;
        this.dataRegular = dataRegular; this.dataMedium = dataMedium;
    }

    /**
     * Faces by registered full name; an empty UI name keeps the platform's face for that weight, and an empty data name
     * is {@link Font#MONOSPACED}.
     */
    public static FaceSet of(String uiRegular, String uiMedium, String uiSemibold, String dataRegular, String dataMedium) {
        Objects.requireNonNull(uiRegular); Objects.requireNonNull(uiMedium); Objects.requireNonNull(uiSemibold);
        Objects.requireNonNull(dataRegular); Objects.requireNonNull(dataMedium);
        return new FaceSet(uiRegular, uiMedium, uiSemibold, dataRegular.isEmpty() ? Font.MONOSPACED : dataRegular,
            dataMedium.isEmpty() ? Font.MONOSPACED : dataMedium);
    }

    /** Whether any UI weight names a face of the kit's own. */
    public boolean namesUiFaces() { return !uiRegular.isEmpty() || !uiMedium.isEmpty() || !uiSemibold.isEmpty(); }

    /** The UI face for {@code weight} (400, 500 or 600); empty for the platform's. */
    public String uiFace(int weight) { return weight >= 600 ? uiSemibold : weight >= 500 ? uiMedium : uiRegular; }

    /** The data face at regular weight. */
    public String dataRegular() { return dataRegular; }
    /** The data face from weight 500. */
    public String dataMedium() { return dataMedium; }

    @Override public boolean equals(Object other) {
        return other instanceof FaceSet faces && uiRegular.equals(faces.uiRegular) && uiMedium.equals(faces.uiMedium)
            && uiSemibold.equals(faces.uiSemibold) && dataRegular.equals(faces.dataRegular) && dataMedium.equals(faces.dataMedium);
    }
    @Override public int hashCode() { return Objects.hash(uiRegular, uiMedium, uiSemibold, dataRegular, dataMedium); }
    @Override public String toString() {
        return "FaceSet[" + uiRegular + ", " + uiMedium + ", " + uiSemibold + ", " + dataRegular + ", " + dataMedium + "]";
    }
}
```

Then edit `Typography.font` and the gallery's fonts:

```bash
python3 -I - <<'PY'
"""Plan 5b Task 1 step 3: a kit's faces in Typography and the gallery."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/main/java/dev/jasper/ui/metrics/Typography.java", [
    ('''        int weight = selected && role == Role.LABEL ? 500 : scale.weight(role);
        String resolved = role == Role.MONO || role == Role.MONO_SMALL || role == Role.MONO_CAPTION || role == Role.PANEL_DETAIL_MONO || role == Role.RUN_DETAIL
            ? weight >= 500 ? faces.dataMedium() : faces.dataRegular() : family;''',
     '''        int weight = selected && role == Role.LABEL ? 500 : scale.weight(role);
        boolean data = role == Role.MONO || role == Role.MONO_SMALL || role == Role.MONO_CAPTION || role == Role.PANEL_DETAIL_MONO
            || role == Role.RUN_DETAIL;
        String face = data ? weight >= 500 ? faces.dataMedium() : faces.dataRegular() : platformDefault ? faces.uiFace(weight) : "";
        if (!face.isEmpty() && !face.equals(Font.MONOSPACED)) {
            // A kit's own face is a whole weight: drawn as it is, at the role's size. One that is not registered draws in
            // the platform face for this role (spec 5.9); nothing is cached, so registration order cannot matter.
            Font named = new Font(face, Font.PLAIN, 1);
            if (named.getFontName(Locale.ROOT).equals(face)) return named.deriveFont(scale.size(role) * bodySize / scale.base());
        }
        String resolved = data ? Font.MONOSPACED : family;''', 1)])
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java", [
    ('''real FlatLaf, the
     * platform family at the kit's body size''', '''real FlatLaf, the
     * kit's own UI face, else the platform family, at the kit's body size''', 1),
    ('''        Object priorCaps = UIManager.get("PasswordField.showCapsLock");
        String family = KitFixtures.platformFamily();''',
     '''        Object priorCaps = UIManager.get("PasswordField.showCapsLock");
        Object priorOverride = UIManager.get("Jasper.uiFontFamilyOverride");''', 1),
    ('''            UIManager.put("defaultFont", new Font(family, Font.PLAIN, bodySize(kit.type().body())));''',
     '''            UIManager.put("defaultFont", font(kit, false));
            // A kit with faces of its own draws them: the gallery's default font then sets only the size.
            if (kit.faces().namesUiFaces()) UIManager.put("Jasper.uiFontFamilyOverride", false);''', 1),
    ('''            UIManager.put("PasswordField.showCapsLock", priorCaps);''',
     '''            UIManager.put("PasswordField.showCapsLock", priorCaps);
            UIManager.put("Jasper.uiFontFamilyOverride", priorOverride);''', 1),
    ('''        String family = KitFixtures.platformFamily();
        float scale = UIScale.getUserScaleFactor();''',
     '''        float scale = UIScale.getUserScaleFactor();''', 1),
    ('''        UIManager.put("defaultFont", new Font(family, Font.PLAIN, bodySize(fixture.name().contains("font24") ? 24 : context.kit().type().body())));''',
     '''        UIManager.put("defaultFont", font(context.kit(), fixture.name().contains("font24")));''', 1),
    ('''    /** The developer font size for a body of {@code points} at the gallery's scale. */''',
     '''    /**
     * The gallery's default font: the platform family, or a kit's own regular UI face, at the kit's body size (24 points
     * when {@code large}) at the gallery's scale.
     */
    static Font font(dev.jasper.ui.kit.Kit kit, boolean large) {
        String face = kit.faces().uiFace(400).isEmpty() ? KitFixtures.platformFamily() : kit.faces().uiFace(400);
        return new Font(face, Font.PLAIN, bodySize(large ? 24 : kit.type().body()));
    }

    /** The developer font size for a body of {@code points} at the gallery's scale. */''', 1)])
PY
```

The anchors are the current text: plan 4c made the gallery's font follow its scale and the kit's body size
(`bodySize`), so under the default kit `font(kit, …)` is exactly the font the gallery set before
(`new Font(platformFamily, Font.PLAIN, bodySize(points))`).

- [ ] **Step 4: Parse the faces into the kit, register them and post the notice**

Create `jasper-app/src/main/java/dev/jasper/app/application/LookFonts.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.presentation.FontFile;
import dev.jasper.app.presentation.KitParser;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Registers a look's font files in this JVM (spec 4.9 and 5.6), right after the bundled fonts and before any font list
 * is cached. A file whose names cannot be read, that does not decode, or whose family is not the declared one is not
 * registered, and one whose full name does not resolve once registered (an installed font of its family hides it: macOS
 * finds installed fonts by family or PostScript name, never by full name) cannot be drawn: its face then draws in the
 * platform face, and it is named for the look's notice. EDT or bootstrap.
 */
final class LookFonts {
    private LookFonts() { }

    /** Registers every file {@code faces} names; the problems, one line each, in declared order. */
    static List<String> register(KitParser.Faces faces) {
        var problems = new ArrayList<String>();
        faces.ui().ifPresent(ui -> {
            for (FontFile file : List.of(ui.regular(), ui.medium(), ui.semibold())) register(file, ui.family(), problems);
        });
        faces.data().ifPresent(data -> {
            for (FontFile file : List.of(data.regular(), data.medium())) register(file, data.family(), problems);
        });
        return List.copyOf(problems);
    }

    private static void register(FontFile file, String family, List<String> problems) {
        String fallback = "; that text uses the platform font";
        if (!file.named()) { problems.add(file.path() + ": its names cannot be read" + fallback); return; }
        if (!file.family().equals(family)) { problems.add(file.path() + ": its family is " + file.family() + ", not " + family + fallback); return; }
        Font font;
        try { font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(file.bytes())); }
        catch (FontFormatException | IOException | RuntimeException undecodable) {
            problems.add(file.path() + ": it is not a font Java can read" + fallback);
            return;
        }
        if (!font.getFontName(Locale.ROOT).equals(file.fullName())) {
            problems.add(file.path() + ": Java names it " + font.getFontName(Locale.ROOT) + ", not " + file.fullName() + fallback);
            return;
        }
        // False when a font of this family or full name is already registered or installed: a copy this JVM registered
        // still resolves by its full name, an installed one (macOS) does not, and that face would draw in Dialog.
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
        if (!new Font(file.fullName(), Font.PLAIN, 1).getFontName(Locale.ROOT).equals(file.fullName()))
            problems.add(file.path() + ": an installed font named " + file.family() + " hides it" + fallback);
    }
}
```

Then:

```bash
python3 -I - <<'PY'
"""Plan 5b Task 1 step 4: the kit's FaceSet, registration, the look font and the notice."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
PR = "jasper-app/src/main/java/dev/jasper/app/presentation/"
edit(PR + "KitParser.java", [
    ('''        Kit kit = new Kit(context.id(), metrics, type.scale(), start.faces(), controls, colors, start.uiKeys());''',
     '''        Kit kit = new Kit(context.id(), metrics, type.scale(), faces(type.faces(), start.faces()), controls, colors, start.uiKeys());''', 1),
    ('''    private static String family(TomlTable table, String name) throws Invalid {''',
     '''    /** The declared faces by their files' full names over {@code base}'s; a file with unreadable names keeps the platform face. */
    private static dev.jasper.ui.metrics.FaceSet faces(Faces declared, dev.jasper.ui.metrics.FaceSet base) {
        if (declared.ui().isEmpty() && declared.data().isEmpty()) return base;
        var ui = declared.ui();
        var data = declared.data();
        return dev.jasper.ui.metrics.FaceSet.of(ui.map(face -> face.regular().fullName()).orElse(base.uiFace(400)),
            ui.map(face -> face.medium().fullName()).orElse(base.uiFace(500)), ui.map(face -> face.semibold().fullName()).orElse(base.uiFace(600)),
            data.map(face -> face.regular().fullName()).orElse(base.dataRegular()), data.map(face -> face.medium().fullName()).orElse(base.dataMedium()));
    }

    private static String family(TomlTable table, String name) throws Invalid {''', 1)])
edit(PR + "Presentation.java", [
    (''' * @param projections every consumer's projection
 */
public record Presentation(Optional<Provider> provider, Kit kit, ChromeSpec chrome, boolean classicBase, KitParser.Faces faces,
                           DefaultSource defaults, Projections projections) {''',
     ''' * @param projections every consumer's projection
 * @param droppedDefaults the {@code [defaults]} keys left out because the theme they name did not load
 */
public record Presentation(Optional<Provider> provider, Kit kit, ChromeSpec chrome, boolean classicBase, KitParser.Faces faces,
                           DefaultSource defaults, Projections projections, java.util.List<String> droppedDefaults) {''', 1),
    ('''        Objects.requireNonNull(defaults); Objects.requireNonNull(projections);
    }''', '''        Objects.requireNonNull(defaults); Objects.requireNonNull(projections);
        droppedDefaults = java.util.List.copyOf(droppedDefaults);
    }''', 1),
    ('''            Projections.of(ChromeSpec.DEFAULT, false, false, "", host, LANDED));''',
     '''            Projections.of(ChromeSpec.DEFAULT, false, false, "", host, LANDED), java.util.List.of());''', 1),
    ('''            Projections.of(parsed.chrome(), parsed.classicBase(), parsed.faces().ui().isPresent(), provider.id(), host, LANDED));''',
     '''            Projections.of(parsed.chrome(), parsed.classicBase(), parsed.faces().ui().isPresent(), provider.id(), host, LANDED), parsed.dropped());''', 1),
    ('''            Projections.fallback(committedDesktop(), host));''', '''            Projections.fallback(committedDesktop(), host), java.util.List.of());''', 1)])
A = "jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java"
edit(A, [
    ('''        dev.jasper.app.platform.BundledFonts.register();
        ConfigSnapshot startup''',
     '''        dev.jasper.app.platform.BundledFonts.register();
        // The look's own faces, before any font list is cached (spec 4.9).
        lookFontProblems = LookFonts.register(presentation.faces());
        ConfigSnapshot startup''', 1),
    ('''    /** The presentation every surface of this process receives (spec 4.9). */''',
     '''    /** The look's font files that could not be registered, for its notice. */
    private List<String> lookFontProblems = List.of();
    /** The presentation every surface of this process receives (spec 4.9). */''', 1),
    ('''        // Spec 4.6: the first window's first paint proves the look, off the EDT.''',
     '''        // Spec 5.8 and 5.9: a look that starts without some of its files says so once.
        var missing = new ArrayList<String>(lookFontProblems);
        presentation.droppedDefaults().forEach(key -> missing.add("[defaults].\\"" + key + "\\": its theme did not load, so the setting keeps its core default"));
        presentation.provider().filter(provider -> !missing.isEmpty()).ifPresent(provider -> notificationCenter.post(NotificationRequest.of(
            IncidentNotices.PLUGINS.id(), Severity.WARNING, "The " + provider.name() + " look is missing some of its files")
            .withContent(String.join("\\n", missing))));
        // Spec 4.6: the first window's first paint proves the look, off the EDT.''', 1)])
AP = "jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java"
edit(AP, [
    ('''        FORM_FONTS.forEach(key -> UIManager.put(key, null));
        UIManager.put("defaultFont", null);''',
     '''        FORM_FONTS.forEach(key -> UIManager.put(key, null));
        // Under a kit with a registered UI face of its own, FlatLaf's text uses it too (spec 5.6); otherwise the platform's.
        lookFont = lookFont(kit);
        UIManager.put("defaultFont", lookFont);''', 1),
    ('''    private final Font platformFont;''', '''    private final Font platformFont;
    /** The look's standard UI font, or null for the platform's. */
    private final FontUIResource lookFont;''', 1),
    ('''        UIManager.put("defaultFont", defaults ? null : family);''', '''        UIManager.put("defaultFont", defaults ? lookFont : family);''', 1),
    ('''    private FontUIResource resolveFont(UiFontConfig choice) {''',
     '''    /** {@code kit}'s regular UI face at its body size when the kit names one and it is registered; else null. */
    private static FontUIResource lookFont(Kit kit) {
        String face = kit.faces().uiFace(400);
        if (face.isEmpty()) return null;
        Font font = new Font(face, Font.PLAIN, 1);
        return font.getFontName(java.util.Locale.ROOT).equals(face) ? new FontUIResource(font.deriveFont(kit.type().body())) : null;
    }

    private FontUIResource resolveFont(UiFontConfig choice) {''', 1)])
PY
grep -n "this.kit = Objects.requireNonNull(kit);" jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java
```

The last command must show that `this.kit` is assigned before the `lookFont(kit)` line (plan 5a's Task 10 put it at the
top of the private constructor); `kit` there is the constructor's parameter. Add the ledger rows of `LookFonts.java`,
`LookFontsTest.java` and `LookNoticeTest.java` (they import `java.awt` or `dev.jasper.ui`, or use `new ArrayList`), in plan
5a's form.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.appearance.*' --tests 'dev.jasper.app.architecture.*' --continue`
Expected: PASS: `FaceSetTest` (2), `LookFontsTest` (3), `LookNoticeTest` (1), `KitParserTest` with the faces, and every
jasper-ui test (`TypographyTest`, the calibration and gallery suites: the default kit draws exactly as before).

- [ ] **Step 6: Prove the gallery unchanged and commit**

Run `./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark` and `-Pjasper.galleryTheme=light` and compare
`jasper-ui/build/gallery/dark-1x` and `light-1x` with the copies made from `HEAD` (this task's uncommitted base) the same
way in a scratch worktree (`scratch=$(mktemp -d)/base; git worktree add --detach "$scratch" HEAD`, run the gallery there,
`cmp` each file, then `git worktree remove --force "$scratch"`): every file is byte-identical (the JSON beside each PNG
too).

```bash
git add jasper-ui/src jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Enterprise: a kit's faces, registered and drawn

FaceSet names UI faces per weight as well as data faces, by registered
full name, and Typography draws a role in its kit's face when that face is
registered, else in the platform face for that role. KitParser builds the
FaceSet from the font files' name tables; the application registers the
files right after the bundled fonts, refusing one whose family is not the
declared one, and a look missing some of its files posts one Warning.
Under such a kit FlatLaf's default font is the look's face. The default
kit draws exactly as before.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: The plugin theme tier

Plugins contribute themes through their descriptor's `themes` (spec 7.4): the bootstrap pre-pass reads the files of
every loadable plugin, and `ThemeLibrary` holds built-ins, then plugin themes, then folder themes. A DEV plugin's theme
wins over a BUNDLED one's over a USER one's, then the lowest plugin id, and no plugin theme shadows a built-in. The theme
ids of every discovered plugin are reserved like built-in ids (a folder file is ignored, Import refuses, Remove is not
offered), the themes of plugins that are not loadable are dormant (a saved pick of one falls back silently and is kept,
7.5), the Appearance page captions plugin themes with their provider, and the literal scan exempts in-repo plugin
theme files, each of which must load.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/PluginThemes.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeLibrary.java`, `ThemeSelection.java:48-56`, `ThemeFolder.java:30-32,66-67,93-94`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginSelection.java` (`themes()`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:297-298,1066-1067`, `SettingsAppearance.java:53-60`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/InstalledThemes.java:20-23`, `AppearanceSelection.java:92`, `ThemeMenu.java:74-77`
- Modify: `jasper-app/src/test/java/dev/jasper/app/architecture/ColourLiteralScanTest.java:15-16`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/PluginThemesTest.java`, `AppearanceSelectionTest.java`

**Interfaces:**
- Consumes: `PluginDescriptor.themes()`, `themeIds()` (5a Task 1), `PluginResources.of` (5a Task 5), `PluginSelection.scan` (5a Task 9).
- Produces: `public record PluginThemes(List<PluginThemes.Provided> provided, Map<String, String> reserved, Set<String> dormant,
  List<ThemeLibrary.Problem> problems)` with `NONE`, `Provided(JasperTheme theme, String pluginName)`, `Declared(String
  pluginId, String pluginName, int rank, boolean loadable, String themeId, Path file, Optional<byte[]> bytes)` and
  `static PluginThemes of(List<Declared>)`; `ThemeLibrary.read(Path folder, ThemeLibrary previous, PluginThemes plugins)`,
  `ThemeLibrary.of(PluginThemes)`, `Optional<String> provider(String themeId)`, `Optional<String> reservedBy(String themeId)`,
  `Set<String> dormant()`; `ThemeFolder(Path folder, Supplier<ThemeLibrary> library)`, `boolean fixed(String id)`;
  `InstalledThemes.ThemeOption(String id, String label, Option.Palette preview, String provider)` (the 3-argument form
  has no provider); `PluginSelection.themes()`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/appearance/PluginThemesTest.java`:

```java
package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.config.TerminalSchemeChoice;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 7.4 and 7.5: the plugin theme tier, its reserved ids and dormant picks. */
class PluginThemesTest {
    @TempDir Path home;

    static PluginThemes.Declared declared(String plugin, String name, int rank, boolean loadable, String id, String themeName) {
        return new PluginThemes.Declared(plugin, name, rank, loadable, id, Path.of(plugin, "themes", id + ".json"),
            Optional.ofNullable(themeName).map(value -> ThemeTestSupport.uiThemeJson(value).getBytes(StandardCharsets.UTF_8)));
    }

    @Test void theTierPrefersDevThenBundledThenUserThenTheLowestIdAndNeverShadowsABuiltIn() {
        var tier = PluginThemes.of(List.of(
            declared("b.bundled", "Bravo", 1, true, "shared", "Shared Bravo"),
            declared("a.user", "Alpha", 2, true, "shared", "Shared Alpha"),
            declared("z.dev", "Zulu", 0, true, "shared", "Shared Zulu"),
            declared("c.bundled", "Charlie", 1, true, "slate-cobalt", "Fake Cobalt"),
            declared("d.bundled", "Delta", 1, true, "own", "Delta Own")));
        assertThat(tier.provided()).extracting(provided -> provided.theme().id() + "@" + provided.pluginName())
            .containsExactly("own@Delta", "shared@Zulu");
        assertThat(tier.problems()).extracting(ThemeLibrary.Problem::message).containsExactly(
            "Shared Zulu is provided by the Zulu plugin; the Bravo plugin's file is ignored.",
            "Slate Cobalt is built in; the Charlie plugin's file is ignored.",
            "Shared Zulu is provided by the Zulu plugin; the Alpha plugin's file is ignored.");
        var library = ThemeLibrary.of(tier);
        assertThat(library.themes()).extracting(theme -> theme.id()).containsSubsequence("slate-cobalt", "catppuccin-mocha", "own", "shared");
        assertThat(library.theme("slate-cobalt").orElseThrow().name()).isEqualTo("Slate Cobalt");
        assertThat(library.provider("shared")).contains("Zulu");
        assertThat(library.provider("slate-cobalt")).isEmpty();
        assertThat(library.problems()).hasSize(3);
    }

    @Test void everyDiscoveredPluginsIdsAreReservedAndANotLoadablePluginsAreDormant() throws Exception {
        var tier = PluginThemes.of(List.of(declared("dev.jasper.enterprise", "Enterprise", 1, false, "enterprise", null)));
        assertThat(tier.provided()).isEmpty();
        assertThat(tier.dormant()).containsExactly("enterprise");
        assertThat(tier.reserved()).containsEntry("enterprise", "Enterprise");
        Path folder = Files.createDirectories(home.resolve("themes"));
        Files.writeString(folder.resolve("enterprise.json"), ThemeTestSupport.uiThemeJson("Impostor"));
        var library = ThemeLibrary.read(folder, ThemeLibrary.of(tier), tier);
        assertThat(library.theme("enterprise")).as("a folder file with a reserved id is ignored").isEmpty();
        assertThat(library.problems()).extracting(ThemeLibrary.Problem::message)
            .containsExactly("enterprise is provided by the Enterprise plugin; this file is ignored.");
        assertThat(ThemeLibrary.read(folder, library).dormant()).as("a reread keeps the tier").containsExactly("enterprise");
        var themes = new ThemeFolder(folder, () -> library);
        Path source = Files.writeString(home.resolve("import.json"), ThemeTestSupport.uiThemeJson("Enterprise"));
        assertThatThrownBy(() -> themes.prepare(source)).isInstanceOf(ThemeFolder.ImportFailure.class)
            .hasMessage("Enterprise is provided by the Enterprise plugin and can't be replaced");
        assertThat(themes.fixed("enterprise")).isTrue();
        assertThat(themes.fixed("slate-cobalt")).isTrue();
        assertThat(themes.fixed("gruvbox")).isFalse();
    }

    /** ThemeMenu runs prepare on its worker, so the library a folder reads must be one that needs no EDT. */
    @Test void aFolderRefusesAReservedIdOnTheImportWorker() throws Exception {
        var library = ThemeLibrary.of(PluginThemes.of(List.of(declared("dev.jasper.enterprise", "Enterprise", 1, false, "enterprise", null))));
        Path folder = Files.createDirectories(home.resolve("themes"));
        var themes = new ThemeFolder(folder, () -> {
            if (java.awt.EventQueue.isDispatchThread()) throw new AssertionError("the folder read its library on the EDT");
            return library;
        });
        Path source = Files.writeString(home.resolve("import.json"), ThemeTestSupport.uiThemeJson("Enterprise"));
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread worker = Thread.ofPlatform().name("theme-import-worker").start(() -> {
            try { themes.prepare(source); } catch (Throwable thrown) { failure.set(thrown); }
        });
        worker.join();
        assertThat(failure.get()).isInstanceOf(ThemeFolder.ImportFailure.class)
            .hasMessage("Enterprise is provided by the Enterprise plugin and can't be replaced");
    }

    @Test void aDormantPickFallsBackSilentlyAndAnUnknownOneWarns() {
        var library = ThemeLibrary.of(PluginThemes.of(List.of(declared("dev.jasper.enterprise", "Enterprise", 1, false, "enterprise", null))));
        var problems = new ArrayList<String>();
        var selection = ThemeSelection.of(library, "enterprise", "nowhere", TerminalSchemeChoice.MATCH, (key, message) -> problems.add(key));
        assertThat(selection.theme(true).id()).isEqualTo("slate-cobalt");
        assertThat(selection.theme(false).id()).isEqualTo("slate-cobalt");
        assertThat(problems).as("only the unknown pick is reported").containsExactly("ui.theme.light");
        var loaded = ThemeLibrary.of(PluginThemes.of(List.of(declared("dev.jasper.enterprise", "Enterprise", 1, true, "enterprise", "Enterprise"))));
        assertThat(ThemeSelection.of(loaded, "enterprise", "enterprise", TerminalSchemeChoice.MATCH, (key, message) -> problems.add(key))
            .theme(true).id()).as("turning the provider back on restores the pick").isEqualTo("enterprise");
    }

    @Test void aPluginsFileThatDoesNotLoadIsReportedAlone() {
        var tier = PluginThemes.of(List.of(
            new PluginThemes.Declared("p.one", "One", 1, true, "broken", Path.of("p.one/themes/broken.json"), Optional.of("{".getBytes(StandardCharsets.UTF_8))),
            new PluginThemes.Declared("p.one", "One", 1, true, "absent", Path.of("p.one/themes/absent.json"), Optional.empty()),
            declared("p.one", "One", 1, true, "fine", "Fine")));
        assertThat(tier.provided()).extracting(provided -> provided.theme().id()).containsExactly("fine");
        assertThat(tier.problems()).extracting(ThemeLibrary.Problem::message).hasSize(2)
            .anySatisfy(message -> assertThat(message).isEqualTo("The One plugin's theme file themes/absent.json is not in its jars."));
    }
}
```

Append to `AppearanceSelectionTest` (before its final closing brace):

```java
    /** Spec 7.4: a plugin's theme is captioned with its provider. */
    @Test void aPluginThemeIsCaptionedWithItsProvider() {
        var themes = new InstalledThemes(List.of(THEMES.dark().getFirst(), new InstalledThemes.ThemeOption("enterprise", "Enterprise", OCEAN, "Enterprise")),
            THEMES.light(), THEMES.schemes(), java.util.Map.of("slate-cobalt", "Slate Cobalt", "enterprise", "Enterprise"));
        var editor = new AppearanceSelection(new AppearanceChoices(100f, false, () -> Optional.of(false),
            ignored -> new dev.jasper.app.lifecycle.Subscription(() -> {}), () -> themes, Optional.empty()), PageKit.context(draft(""), id -> { }, () -> { }));
        assertThat(pick(editor, 1).options()).extracting(Option::description).containsExactly("", "From the Enterprise plugin");
    }
```

In `ColourLiteralScanTest`, replace the `THEMES` definition with built-ins plus in-repo plugin theme files, and add a
loader test:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/architecture/ColourLiteralScanTest.java"); text = path.read_text(encoding="utf-8")
old = '''    static final Set<Path> THEMES = dev.jasper.ui.theme.JasperTheme.builtIns().stream()
        .map(theme -> ROOT.resolve("jasper-ui/src/main/resources/dev/jasper/ui/theme/" + theme.id() + ".json")).collect(java.util.stream.Collectors.toSet());'''
new = '''    /** Spec 7.4: the files named by in-repo plugin.toml themes entries, which the production loader must read. */
    static final Set<Path> PLUGIN_THEMES = pluginThemes();
    static final Set<Path> THEMES = java.util.stream.Stream.concat(dev.jasper.ui.theme.JasperTheme.builtIns().stream()
        .map(theme -> ROOT.resolve("jasper-ui/src/main/resources/dev/jasper/ui/theme/" + theme.id() + ".json")), PLUGIN_THEMES.stream())
        .collect(java.util.stream.Collectors.toSet());

    static Set<Path> pluginThemes() {
        var files = new java.util.TreeSet<Path>();
        try (var plugins = Files.list(ROOT.resolve("plugins"))) {
            for (Path plugin : plugins.toList()) {
                Path resources = plugin.resolve("src/main/resources"), descriptor = resources.resolve("plugin.toml");
                if (!Files.isRegularFile(descriptor)) continue;
                var toml = org.tomlj.Toml.parse(Files.readString(descriptor));
                var themes = toml.getArrayOrEmpty("themes");
                for (int i = 0; i < themes.size(); i++) files.add(resources.resolve(themes.getString(i)));
            }
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        return Set.copyOf(files);
    }

    @Test void everyInRepoPluginThemeLoadsWithTheProductionLoader() throws IOException {
        for (Path file : PLUGIN_THEMES) {
            String name = file.getFileName().toString();
            var theme = dev.jasper.ui.theme.JasperTheme.parse(Files.readString(file), name.substring(0, name.length() - ".json".length()));
            assertThat(theme.has(true) || theme.has(false)).as(file.toString()).isTrue();
        }
    }'''
if text.count(old) != 1: raise SystemExit("ColourLiteralScanTest: THEMES moved")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.PluginThemesTest' --tests 'dev.jasper.app.settingsui.AppearanceSelectionTest' --tests 'dev.jasper.app.architecture.ColourLiteralScanTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol: class PluginThemes" and the four-argument `ThemeOption`.

- [ ] **Step 3: Write `PluginThemes` and the library's tier**

Create `jasper-app/src/main/java/dev/jasper/app/appearance/PluginThemes.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.ThemeFileException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The themes plugins provide (spec 7.4 and 7.5): the themes of loadable plugins, ordered by name, after the built-ins and
 * before the folder's; the theme ids of every discovered plugin, reserved like built-in ids, with the plugin that declares
 * each; the ids only plugins that are not loadable declare (dormant); and the files that could not be used. Immutable.
 *
 * @param provided the plugin themes, by name then id
 * @param reserved every discovered plugin's theme ids, each with its plugin's name
 * @param dormant the ids declared only by plugins that are not loadable
 * @param problems the files that could not be used
 */
public record PluginThemes(List<Provided> provided, Map<String, String> reserved, Set<String> dormant, List<ThemeLibrary.Problem> problems) {
    /** No plugin themes. */
    public static final PluginThemes NONE = new PluginThemes(List.of(), Map.of(), Set.of(), List.of());

    /**
     * A plugin theme.
     *
     * @param theme the theme
     * @param pluginName the name of the plugin that provides it
     */
    public record Provided(JasperTheme theme, String pluginName) { }

    /**
     * A theme file a discovered plugin declares.
     *
     * @param pluginId the plugin's id
     * @param pluginName its name
     * @param rank its origin's precedence: 0 for development, 1 for bundled, 2 for installed
     * @param loadable whether the plugin will load at this start
     * @param themeId the file's theme id (its name without {@code .json})
     * @param file where it is, for problems
     * @param bytes its first bytes, read only for a loadable plugin; empty when absent
     */
    public record Declared(String pluginId, String pluginName, int rank, boolean loadable, String themeId, Path file, Optional<byte[]> bytes) { }

    /** Copies the parts. */
    public PluginThemes {
        provided = List.copyOf(provided); reserved = Map.copyOf(reserved); dormant = Set.copyOf(dormant); problems = List.copyOf(problems);
    }

    /** The tier from every declared file: DEV before BUNDLED before USER, then the lowest plugin id; no built-in id is taken. */
    public static PluginThemes of(List<Declared> declared) {
        var reserved = new LinkedHashMap<String, String>();
        var problems = new ArrayList<ThemeLibrary.Problem>();
        var winners = new LinkedHashMap<String, Provided>();
        var winnerIds = new LinkedHashMap<String, String>();
        var sorted = new ArrayList<>(declared);
        sorted.sort(Comparator.comparingInt(Declared::rank).thenComparing(Declared::pluginId));
        for (Declared file : sorted) {
            var builtIn = JasperTheme.builtIn(file.themeId());
            if (builtIn.isEmpty()) reserved.putIfAbsent(file.themeId(), file.pluginName());
            if (!file.loadable()) continue;
            if (builtIn.isPresent()) {
                problems.add(new ThemeLibrary.Problem(file.file(), 0, builtIn.get().name() + " is built in; the " + file.pluginName() + " plugin's file is ignored."));
                continue;
            }
            var winner = winners.get(file.themeId());
            if (winner != null) {
                problems.add(new ThemeLibrary.Problem(file.file(), 0, winner.theme().name() + " is provided by the " + winner.pluginName()
                    + " plugin; the " + file.pluginName() + " plugin's file is ignored."));
                continue;
            }
            String where = "themes/" + file.themeId() + ".json";
            if (file.bytes().isEmpty()) {
                problems.add(new ThemeLibrary.Problem(file.file(), 0, "The " + file.pluginName() + " plugin's theme file " + where + " is not in its jars."));
                continue;
            }
            try {
                var theme = JasperTheme.parse(text(file.bytes().get()), file.themeId());
                winners.put(file.themeId(), new Provided(theme, file.pluginName()));
                winnerIds.put(file.themeId(), file.pluginId());
            } catch (ThemeFileException failure) {
                problems.add(new ThemeLibrary.Problem(file.file(), failure.line(), failure.detail()));
            } catch (CharacterCodingException | RuntimeException failure) {
                problems.add(new ThemeLibrary.Problem(file.file(), 0, "Could not use this file: " + Objects.requireNonNullElse(failure.getMessage(), failure.getClass().getSimpleName())));
            }
        }
        var dormant = new LinkedHashSet<String>();
        for (Declared file : declared) if (!file.loadable() && !winners.containsKey(file.themeId()) && JasperTheme.builtIn(file.themeId()).isEmpty())
            dormant.add(file.themeId());
        var provided = new ArrayList<>(winners.values());
        provided.sort(Comparator.comparing((Provided item) -> item.theme().name().toLowerCase(Locale.ROOT)).thenComparing(item -> item.theme().id()));
        return new PluginThemes(provided, reserved, dormant, problems);
    }

    private static String text(byte[] bytes) throws CharacterCodingException {
        if (bytes.length > ThemeLibrary.MAX_BYTES) throw new CharacterCodingException() {
            @Override public String getMessage() { return "Theme files are at most 1 MiB."; }
        };
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    /** The provided theme with this id. */
    Optional<Provided> provided(String id) { return provided.stream().filter(item -> item.theme().id().equals(id)).findFirst(); }
}
```

Then edit the library, the selection and the folder:

```bash
python3 -I - <<'PY'
"""Plan 5b Task 2 step 3: the plugin tier in the library, the selection and the folder."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
AP = "jasper-app/src/main/java/dev/jasper/app/appearance/"
edit(AP + "ThemeLibrary.java", [
    (''' * The installed themes: the built-in themes, Slate Cobalt first, and every valid {@code <home>/themes/<id>.json}.''',
     ''' * The installed themes: the built-in themes, Slate Cobalt first, then the themes plugins provide (spec 7.4), then every
 * valid {@code <home>/themes/<id>.json}; a folder file never replaces a built-in or plugin theme id.''', 1),
    ('''    private static final ThemeLibrary BUNDLED = new ThemeLibrary(List.of(), Map.of(), List.of());

    private final List<JasperTheme> imported, themes;''',
     '''    private static final ThemeLibrary BUNDLED = new ThemeLibrary(List.of(), Map.of(), List.of(), PluginThemes.NONE);

    private final PluginThemes plugins;
    private final List<JasperTheme> imported, themes;''', 1),
    ('''    private ThemeLibrary(List<JasperTheme> imported, Map<String, String> texts, List<Problem> problems) {
        this.imported = List.copyOf(imported);
        this.texts = Map.copyOf(texts);
        this.problems = List.copyOf(problems);
        var all = new ArrayList<JasperTheme>();
        all.addAll(JasperTheme.builtIns());
        all.addAll(this.imported);''',
     '''    private ThemeLibrary(List<JasperTheme> imported, Map<String, String> texts, List<Problem> problems, PluginThemes plugins) {
        this.plugins = plugins;
        this.imported = List.copyOf(imported);
        this.texts = Map.copyOf(texts);
        var allProblems = new ArrayList<>(plugins.problems());
        allProblems.addAll(problems);
        this.problems = List.copyOf(allProblems);
        var all = new ArrayList<JasperTheme>();
        all.addAll(JasperTheme.builtIns());
        plugins.provided().forEach(provided -> all.add(provided.theme()));
        all.addAll(this.imported);''', 1),
    ('''    /** Only the built-in themes. */
    public static ThemeLibrary bundledOnly() { return BUNDLED; }''',
     '''    /** Only the built-in themes. */
    public static ThemeLibrary bundledOnly() { return BUNDLED; }

    /** The built-in themes and {@code plugins}' tier, with no folder. */
    public static ThemeLibrary of(PluginThemes plugins) {
        return plugins.equals(PluginThemes.NONE) ? BUNDLED : new ThemeLibrary(List.of(), Map.of(), List.of(), plugins);
    }''', 1),
    ('''    public static ThemeLibrary read(Path folder, ThemeLibrary previous) { return read(folder, previous, JasperTheme::parse); }

    /** {@link #read(Path, ThemeLibrary)} with the function that turns a file's text and id into a theme. */
    static ThemeLibrary read(Path folder, ThemeLibrary previous, BiFunction<String, String, JasperTheme> parser) {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(previous, "previous");''',
     '''    public static ThemeLibrary read(Path folder, ThemeLibrary previous) { return read(folder, previous, previous.plugins); }

    /** As {@link #read(Path, ThemeLibrary)}, with {@code plugins}' tier between the built-ins and the folder (spec 7.4). */
    public static ThemeLibrary read(Path folder, ThemeLibrary previous, PluginThemes plugins) {
        return read(folder, previous, plugins, JasperTheme::parse);
    }

    /** {@link #read(Path, ThemeLibrary)} with the function that turns a file's text and id into a theme. */
    static ThemeLibrary read(Path folder, ThemeLibrary previous, BiFunction<String, String, JasperTheme> parser) {
        return read(folder, previous, previous.plugins, parser);
    }

    static ThemeLibrary read(Path folder, ThemeLibrary previous, PluginThemes plugins, BiFunction<String, String, JasperTheme> parser) {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(plugins, "plugins");''', 1),
    ('''                problems.add(new Problem(file, 0, builtIn.get().name() + " is built in; this file is ignored."));
                continue;
            }''',
     '''                problems.add(new Problem(file, 0, builtIn.get().name() + " is built in; this file is ignored."));
                continue;
            }
            String provider = plugins.reserved().get(id);
            if (provider != null) {
                String name = plugins.provided(id).map(provided -> provided.theme().name()).orElse(id);
                problems.add(new Problem(file, 0, name + " is provided by the " + provider + " plugin; this file is ignored."));
                continue;
            }''', 1),
    ('''        if (sameThemes(imported, previous.imported) && problems.equals(previous.problems)) return previous;
        return new ThemeLibrary(imported, texts, problems);''',
     '''        if (plugins == previous.plugins && sameThemes(imported, previous.imported) && problems.equals(previous.folderProblems())) return previous;
        return new ThemeLibrary(imported, texts, problems, plugins);''', 1),
    ('''    /** The files that were skipped, in file-name order. */
    public List<Problem> problems() { return problems; }''',
     '''    /** The files that were skipped: plugin files first, then the folder's in file-name order. */
    public List<Problem> problems() { return problems; }

    private List<Problem> folderProblems() { return problems.subList(plugins.problems().size(), problems.size()); }

    /** The name of the plugin that provides the theme {@code id}; empty for a built-in or folder theme. */
    public Optional<String> provider(String id) { return plugins.provided(id).map(PluginThemes.Provided::pluginName); }

    /** The name of the discovered plugin whose theme ids include {@code id}, which no folder file may take. */
    public Optional<String> reservedBy(String id) { return Optional.ofNullable(plugins.reserved().get(id)); }

    /** The theme ids only plugins that are not loadable declare: a saved pick of one falls back silently (spec 7.5). */
    public java.util.Set<String> dormant() { return plugins.dormant(); }''', 1)])
edit(AP + "ThemeSelection.java", [
    ('''        var theme = library.theme(id);
        if (theme.isPresent() && theme.get().has(dark)) return theme.get();''',
     '''        var theme = library.theme(id);
        if (theme.isPresent() && theme.get().has(dark)) return theme.get();
        // Spec 7.5: the pick of a theme whose plugin is off is kept, and shows the default without a warning.
        if (theme.isEmpty() && library.dormant().contains(id)) return JasperTheme.bundled();''', 1)])
edit(AP + "ThemeFolder.java", [
    ('''    private final Path folder;

    public ThemeFolder(Path folder) { this.folder = Objects.requireNonNull(folder, "folder"); }''',
     '''    private final Path folder;
    private final java.util.function.Supplier<ThemeLibrary> library;

    /** A folder with no plugin themes to keep out. */
    public ThemeFolder(Path folder) { this(folder, ThemeLibrary::bundledOnly); }

    /** A folder that refuses the theme ids {@code library}'s plugins reserve (spec 7.4). */
    public ThemeFolder(Path folder, java.util.function.Supplier<ThemeLibrary> library) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.library = Objects.requireNonNull(library, "library");
    }''', 1),
    ('''        if (builtIn.isPresent()) throw new ImportFailure(builtIn.get().name() + " is built in and can't be replaced");''',
     '''        if (builtIn.isPresent()) throw new ImportFailure(builtIn.get().name() + " is built in and can't be replaced");
        var provider = library.get().reservedBy(id);
        if (provider.isPresent()) throw new ImportFailure(theme.name() + " is provided by the " + provider.get() + " plugin and can't be replaced");''', 1),
    ('''    /** Whether {@code id} is a built-in theme's, which no file in the folder replaces or removes. */''',
     '''    /** Whether {@code id} is a built-in theme's or a plugin's, which Settings neither replaces nor removes. */
    public boolean fixed(String id) { return builtIn(id) || library.get().reservedBy(id).isPresent(); }

    /** Whether {@code id} is a built-in theme's, which no file in the folder replaces or removes. */''', 1)])
S = "jasper-app/src/main/java/dev/jasper/app/settingsui/"
edit(S + "InstalledThemes.java", [
    ('''    /** A theme for one appearance: its id, its label and its tile preview. */
    public record ThemeOption(String id, String label, Option.Palette preview) {
        public ThemeOption { Objects.requireNonNull(id, "id"); Objects.requireNonNull(label, "label"); Objects.requireNonNull(preview, "preview"); }
    }''',
     '''    /** A theme for one appearance: its id, its label, its tile preview and the plugin that provides it ({@code ""} for none). */
    public record ThemeOption(String id, String label, Option.Palette preview, String provider) {
        public ThemeOption {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(label, "label"); Objects.requireNonNull(preview, "preview");
            Objects.requireNonNull(provider, "provider");
        }
        /** A built-in or imported theme. */
        public ThemeOption(String id, String label, Option.Palette preview) { this(id, label, preview, ""); }
    }''', 1)])
edit(S + "AppearanceSelection.java", [
    ('''        for (var theme : installed) options.add(new Option(theme.id(), theme.label(), Optional.empty()));''',
     '''        for (var theme : installed) options.add(new Option(theme.id(), theme.label(), Optional.empty(), "",
            theme.provider().isEmpty() ? "" : "From the " + theme.provider() + " plugin", Optional.empty()));''', 1)])
edit(S + "ThemeMenu.java", [
    ('''        return theme.isEmpty() || ThemeFolder.builtIn(theme) || !themes.get().names().containsKey(theme) ? Optional.empty() : Optional.of(theme);''',
     '''        return theme.isEmpty() || hooks.folder().fixed(theme) || !themes.get().names().containsKey(theme) ? Optional.empty() : Optional.of(theme);''', 1)])
A = "jasper-app/src/main/java/dev/jasper/app/application/"
edit(A + "SettingsAppearance.java", [
    ('''            .map(theme -> new InstalledThemes.ThemeOption(theme.id(), library.label(theme), preview(theme, dark))).toList();''',
     '''            .map(theme -> new InstalledThemes.ThemeOption(theme.id(), library.label(theme), preview(theme, dark), library.provider(theme.id()).orElse(""))).toList();''', 1)])
edit(A + "JasperApplication.java", [
    ('''        var library = themesFolder == null ? dev.jasper.app.appearance.ThemeLibrary.bundledOnly()
            : dev.jasper.app.appearance.ThemeLibrary.read(themesFolder, dev.jasper.app.appearance.ThemeLibrary.bundledOnly());''',
     '''        var library = themesFolder == null ? dev.jasper.app.appearance.ThemeLibrary.of(selection.themes())
            : dev.jasper.app.appearance.ThemeLibrary.read(themesFolder, dev.jasper.app.appearance.ThemeLibrary.bundledOnly(), selection.themes());''', 1),
    ('''            Optional<dev.jasper.app.settingsui.ThemeImports> imports = themesFolder == null ? Optional.empty()
                : Optional.of(SettingsAppearance.imports(new dev.jasper.app.appearance.ThemeFolder(themesFolder),''',
     '''            // ThemeFolder.prepare runs on the import worker (ThemeMenu.chooseAndImport), so the folder reads the session's
            // fixed plugin tier, never ThemeController.library(), which requires the EDT.
            var pluginThemes = dev.jasper.app.appearance.ThemeLibrary.of(selection.themes());
            Optional<dev.jasper.app.settingsui.ThemeImports> imports = themesFolder == null ? Optional.empty()
                : Optional.of(SettingsAppearance.imports(new dev.jasper.app.appearance.ThemeFolder(themesFolder, () -> pluginThemes),''', 1)])
P = "jasper-app/src/main/java/dev/jasper/app/plugins/PluginSelection.java"
edit(P, [
    ('''    private final Presentations presentations;
    private final AttemptStore store;''',
     '''    private final Presentations presentations;
    private final AttemptStore store;
    private final dev.jasper.app.appearance.PluginThemes themes;''', 1),
    ('''                            Optional<SkippedLook> skipped, Presentations presentations, AttemptStore store) {
        this.presentations = presentations; this.store = store;''',
     '''                            Optional<SkippedLook> skipped, Presentations presentations, AttemptStore store,
                            dev.jasper.app.appearance.PluginThemes themes) {
        this.presentations = presentations; this.store = store; this.themes = themes;''', 1),
    ('''            Presentation.standard(host()), PresentationAttempt.NONE, Optional.empty(), Presentations.NONE, null);''',
     '''            Presentation.standard(host()), PresentationAttempt.NONE, Optional.empty(), Presentations.NONE, null,
            dev.jasper.app.appearance.PluginThemes.NONE);''', 1),
    ('''        return new PluginSelection(options, build, candidates, problems, resolution, presentation, attempt, skipped, presentations, store);''',
     '''        return new PluginSelection(options, build, candidates, problems, resolution, presentation, attempt, skipped, presentations, store,
            themes(candidates, resolution));''', 1),
    ('''            PluginResolver.without(resolution, provider.get(), PluginStatus.LOOK_FAILED), presentation.fallback(host()), attempt, skipped,
            presentations, store);''',
     '''            PluginResolver.without(resolution, provider.get(), PluginStatus.LOOK_FAILED), presentation.fallback(host()), attempt, skipped,
            presentations, store, themes);''', 1),
    ('''    List<PluginCandidate> candidates() { return candidates; }''',
     '''    /** The plugin theme tier (spec 7.4): read once here and kept for the whole session, a blamed look's included. */
    public dev.jasper.app.appearance.PluginThemes themes() { return themes; }

    /** Every discovered plugin's declared theme files; only loadable plugins' files are read. */
    private static dev.jasper.app.appearance.PluginThemes themes(List<PluginCandidate> candidates, PluginResolver.Resolution resolution) {
        var declared = new ArrayList<dev.jasper.app.appearance.PluginThemes.Declared>();
        for (PluginCandidate candidate : candidates) {
            boolean loadable = resolution.load().stream().anyMatch(loaded -> loaded.id().equals(candidate.id()) && loaded.origin() == candidate.origin());
            var reader = PluginResources.of(candidate);
            var paths = candidate.descriptor().themes();
            var ids = candidate.descriptor().themeIds();
            int rank = switch (candidate.origin()) { case DEV -> 0; case BUNDLED -> 1; case USER -> 2; };
            for (int i = 0; i < paths.size(); i++)
                declared.add(new dev.jasper.app.appearance.PluginThemes.Declared(candidate.id(), candidate.descriptor().name(), rank, loadable,
                    ids.get(i), candidate.directory().resolve(paths.get(i)),
                    loadable ? reader.read(paths.get(i), dev.jasper.app.presentation.PresentationFacts.MAX_THEME_BYTES) : java.util.Optional.empty()));
        }
        return dev.jasper.app.appearance.PluginThemes.of(declared);
    }

    List<PluginCandidate> candidates() { return candidates; }''', 1)])
PY
```

Add the ledger row of `PluginThemes.java` and of
`PluginThemesTest.java` (they use `new ArrayList`), in plan 5a's form.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*' --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.architecture.*'`
Expected: PASS: `PluginThemesTest` (5), the new Appearance case, `ColourLiteralScanTest` (no in-repo plugin theme yet, so
the loader test reads none) and every existing library, folder, selection, menu and Settings test.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Enterprise: the plugin theme tier

The pre-pass reads the theme files of every loadable plugin, and the
library holds built-ins, then plugin themes, then folder themes: DEV over
BUNDLED over USER, then the lowest id, never a built-in id. Every
discovered plugin's theme ids are reserved: a folder file is ignored,
Import refuses it and Settings offers no Remove. A saved pick of a theme
whose plugin is off falls back silently and is kept. The Appearance page
captions plugin themes with their provider, and the literal scan exempts
in-repo plugin theme files, which must load.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: The Enterprise plugin

`plugins/enterprise` (`:jasper-plugin-enterprise`, spec 14.1 and 14.2): the descriptor, the kit file, the five IBM Plex
files and their OFL notice, `themes/enterprise.json` (spec 7.6, Light and Dark), `dev.jasper.enterprise.api.Workstation`
and `EnterprisePlugin`, whose `start` only publishes it. `stagePlugins` bundles it, and it is off by default.
`verifyThirdPartyNotices` requires every font file a bundled plugin ships to be listed in a component's `files`.

**Files:**
- Create: `plugins/enterprise/build.gradle.kts`
- Create: `plugins/enterprise/src/main/resources/plugin.toml`, `icons/enterprise.svg`, `presentation/enterprise.kit.toml`,
  `presentation/fonts/ibm-plex-sans-regular.ttf`, `ibm-plex-sans-medium.ttf`, `ibm-plex-sans-semibold.ttf`,
  `ibm-plex-mono-regular.ttf`, `ibm-plex-mono-medium.ttf`, `themes/enterprise.json`
- Create: `plugins/enterprise/src/main/java/dev/jasper/enterprise/EnterprisePlugin.java`, `package-info.java`,
  `api/Workstation.java`, `api/package-info.java`
- Create: `jasper-app/src/main/resources/dev/jasper/app/about/licenses/ibm-plex.OFL.txt`
- Modify: `jasper-app/build.gradle.kts:42-58` (`stagePlugins`), `jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml`,
  `gradle/third-party-notices.gradle.kts`, `docs/design/theme-engine/migration.csv`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java:78,116`, `PluginZipsTest.java:19,42`,
  `jasper-app/src/test/java/dev/jasper/app/architecture/ColourLiteralScanTest.java` (the loader test names Enterprise)
- Test: `plugins/enterprise/src/test/java/dev/jasper/enterprise/EnterprisePluginTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/appearance/EnterpriseThemeTest.java`

**Interfaces:**
- Consumes: plan 5a's descriptor keys, `presentation.provide`, `enabled_by_default`; Task 2's plugin theme tier.
- Produces: `public interface dev.jasper.enterprise.api.Workstation` (presence only); the bundled plugin
  `dev.jasper.enterprise` 0.1.0, off by default.

- [ ] **Step 1: Fetch the IBM Plex files and check them**

Download only IBM's own releases, into a new empty folder, check them, and copy the five TTFs and the licence:

```bash
work=$(mktemp -d)
curl -fsSL -o "$work/ibm-plex-sans.zip" "https://github.com/IBM/plex/releases/download/%40ibm/plex-sans%401.1.0/ibm-plex-sans.zip"
curl -fsSL -o "$work/ibm-plex-mono.zip" "https://github.com/IBM/plex/releases/download/%40ibm/plex-mono%401.1.0/ibm-plex-mono.zip"
shasum -a 256 -c - <<SUMS
fb365d910566e6d199cc2c15579a7dd9a267128e18431a394ed81f1970c69200  $work/ibm-plex-sans.zip
4bfc936d0e1fd19db6327a3786eabdbc3dc0d464500576f6458f6706df68d26c  $work/ibm-plex-mono.zip
SUMS
fonts=plugins/enterprise/src/main/resources/presentation/fonts
mkdir -p "$fonts" jasper-app/src/main/resources/dev/jasper/app/about/licenses
unzip -p "$work/ibm-plex-sans.zip" ibm-plex-sans/fonts/complete/ttf/IBMPlexSans-Regular.ttf > "$fonts/ibm-plex-sans-regular.ttf"
unzip -p "$work/ibm-plex-sans.zip" ibm-plex-sans/fonts/complete/ttf/IBMPlexSans-Medium.ttf > "$fonts/ibm-plex-sans-medium.ttf"
unzip -p "$work/ibm-plex-sans.zip" ibm-plex-sans/fonts/complete/ttf/IBMPlexSans-SemiBold.ttf > "$fonts/ibm-plex-sans-semibold.ttf"
unzip -p "$work/ibm-plex-mono.zip" ibm-plex-mono/fonts/complete/ttf/IBMPlexMono-Regular.ttf > "$fonts/ibm-plex-mono-regular.ttf"
unzip -p "$work/ibm-plex-mono.zip" ibm-plex-mono/fonts/complete/ttf/IBMPlexMono-Medium.ttf > "$fonts/ibm-plex-mono-medium.ttf"
unzip -p "$work/ibm-plex-sans.zip" ibm-plex-sans/LICENSE.txt > jasper-app/src/main/resources/dev/jasper/app/about/licenses/ibm-plex.OFL.txt
shasum -a 256 -c - <<SUMS
975dcda37d80f038dcd143c22e33ca2d97a0cc5a929aace1c749153b0fe1afa5  $fonts/ibm-plex-sans-regular.ttf
331c8639d7598b2cde62a911a71db195e30cb655cd6bdf2e324a7e984955f907  $fonts/ibm-plex-sans-medium.ttf
a20caf8286023a6a7a85e40b1d2a4ae9fc3e3b1f9eda8f4c542dd4986af67bb1  $fonts/ibm-plex-sans-semibold.ttf
fe11304a5fe956d5744e9b6a246cc83d90425245e75a62230044966ca96a7f50  $fonts/ibm-plex-mono-regular.ttf
0bede3debdea8488bbb927f8f0650d915073209734a67fe8cd5a3320b572511c  $fonts/ibm-plex-mono-medium.ttf
7e6b2818edbd8f6a01ae80641cc8f16a51080d08fb4e532be3a0b6f74adb07da  jasper-app/src/main/resources/dev/jasper/app/about/licenses/ibm-plex.OFL.txt
SUMS
rm -rf "$work"
```

Expected: every line reports `OK`. If a download fails or a sum differs, stop and report: no other copy of the fonts is
acceptable (Global Constraints).

- [ ] **Step 2: Write the failing tests**

Create `plugins/enterprise/src/test/java/dev/jasper/enterprise/EnterprisePluginTest.java`:

```java
package dev.jasper.enterprise;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.enterprise.api.Workstation;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Spec 14.2: Workstation is published while Enterprise runs, and start can do nothing else. */
class EnterprisePluginTest {
    static final PluginInfo ENTERPRISE = new PluginInfo("dev.jasper.enterprise", "Enterprise", "0.1.0", Set.of("presentation.provide"));
    static final PluginInfo CONSUMER = new PluginInfo("dev.example.consumer", "Consumer", "1.0.0", Set.of());

    @Test void aPluginThatOptionallyRequiresEnterpriseFindsWorkstation() {
        try (var host = new FakePluginHost()) {
            host.start(ENTERPRISE, Set.of(), Set.of(), new EnterprisePlugin());
            var found = new AtomicReference<Optional<Workstation>>();
            host.start(CONSUMER, Set.of(), Set.of("dev.jasper.enterprise"), context -> found.set(context.services().find(Workstation.class)));
            assertThat(found.get()).isPresent();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void withoutEnterpriseAConsumerFindsNone() {
        try (var host = new FakePluginHost()) {
            var found = new AtomicReference<Optional<Workstation>>();
            host.start(CONSUMER, Set.of(), Set.of("dev.jasper.enterprise"), context -> found.set(context.services().find(Workstation.class)));
            assertThat(found.get()).isEmpty();
        }
    }

    /** Spec 4.6: start cannot throw, so a failed Enterprise start is never the plugin's own doing. */
    @Test void startOnlyPublishesWorkstation() throws Exception {
        byte[] bytes;
        try (var in = EnterprisePlugin.class.getResourceAsStream("EnterprisePlugin.class")) { bytes = in.readAllBytes(); }
        var start = ClassFile.of().parse(bytes).methods().stream().filter(method -> method.methodName().equalsString("start")).findFirst().orElseThrow();
        var steps = new ArrayList<String>();
        start.code().orElseThrow().forEach(element -> {
            if (element instanceof InvokeInstruction call) steps.add(call.owner().asInternalName() + "." + call.name().stringValue());
            if (element instanceof ThrowInstruction) steps.add("throw");
        });
        assertThat(steps).containsExactly("dev/jasper/sdk/plugin/PluginContext.services", "dev/jasper/enterprise/EnterprisePlugin$1.<init>",
            "dev/jasper/sdk/services/Services.publish");
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/appearance/EnterpriseThemeTest.java`:

```java
package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Spec 7.6: the Enterprise theme's two variants, as the tables give them and as the mocks paint them. */
class EnterpriseThemeTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot"));
    static final Path FILE = ROOT.resolve("plugins/enterprise/src/main/resources/themes/enterprise.json");
    static final Map<String, String> LIGHT = Map.ofEntries(Map.entry("surface.app", "#ECEDEF"), Map.entry("surface.dialog", "#ECEDEF"),
        Map.entry("surface.titlebar", "#E1E3E6"), Map.entry("surface.panel", "#F7F7F8"), Map.entry("surface.sidebar", "#F7F7F8"),
        Map.entry("surface.card", "#FFFFFF"), Map.entry("surface.input", "#FFFFFF"), Map.entry("surface.terminal", "#FFFFFF"),
        Map.entry("accent", "#2F65CA"), Map.entry("selection.bg", "#2F65CA"), Map.entry("hue.blue", "#2F65CA"),
        Map.entry("text.onAccent", "#FFFFFF"), Map.entry("text.onSelection", "#FFFFFF"), Map.entry("text.onAccent.secondary", "#DCE5F7"),
        Map.entry("text.onSelection.secondary", "#DCE5F7"), Map.entry("status.warning", "#8A5A00"), Map.entry("hue.amber", "#B5651D"),
        Map.entry("border.window", "#C4C7CC"), Map.entry("border.control", "#B4B8BF"), Map.entry("border.input", "#B4B8BF"),
        Map.entry("button.secondary.border", "#B4B8BF"), Map.entry("text.primary", "#1E1F22"), Map.entry("text.strong.secondary", "#3A3D42"),
        Map.entry("text.secondary", "#5C6370"), Map.entry("text.tertiary", "#5C6370"), Map.entry("text.placeholder", "#7A808A"),
        Map.entry("status.success", "#2E9E4F"), Map.entry("hue.green", "#2E9E4F"), Map.entry("status.danger", "#D03A3A"),
        Map.entry("hue.purple", "#8A4FBF"));
    static final Map<String, String> DARK = Map.ofEntries(Map.entry("surface.app", "#3C3F44"), Map.entry("surface.dialog", "#3C3F44"),
        Map.entry("surface.titlebar", "#36393E"), Map.entry("surface.panel", "#35383D"), Map.entry("surface.sidebar", "#35383D"),
        Map.entry("surface.card", "#2E3135"), Map.entry("surface.input", "#2E3135"), Map.entry("surface.terminal", "#2E3135"),
        Map.entry("accent", "#3D74D6"), Map.entry("selection.bg", "#3D74D6"), Map.entry("hue.blue", "#3D74D6"),
        Map.entry("text.onAccent", "#FFFFFF"), Map.entry("text.onSelection", "#FFFFFF"), Map.entry("button.secondary.bg", "#45484D"),
        Map.entry("surface.control.selected", "#50545B"), Map.entry("hue.amber", "#D08A4A"), Map.entry("border.window", "#2A2C30"),
        Map.entry("border.control", "#5A5E65"), Map.entry("border.input", "#5A5E65"), Map.entry("button.secondary.border", "#5A5E65"),
        Map.entry("text.primary", "#DFE1E5"), Map.entry("text.strong.secondary", "#C4C8CE"), Map.entry("text.secondary", "#A0A5AD"),
        Map.entry("text.tertiary", "#A0A5AD"), Map.entry("text.placeholder", "#8E939B"), Map.entry("status.success", "#4CB86A"),
        Map.entry("hue.green", "#4CB86A"), Map.entry("status.warning", "#E3C27A"));
    static final Map<DerivedColor, String> LIGHT_OVERRIDES = Map.of(DerivedColor.SURFACE_HEADER, "#E1E3E6", DerivedColor.SURFACE_HEADER_ACTIVE, "#D9DFEA",
        DerivedColor.SURFACE_STRIPE, "#E4E6E9", DerivedColor.SURFACE_TABLE_HEADER, "#F0F1F3", DerivedColor.SURFACE_ZEBRA, "#F7F8FA",
        DerivedColor.SURFACE_ROW_HOVER, "#EEF2FA", DerivedColor.WARNING_GROUND, "#FBF1DA", DerivedColor.WARNING_BORDER, "#E6CF95");
    static final Map<DerivedColor, String> DARK_OVERRIDES = Map.of(DerivedColor.SURFACE_HEADER, "#36393E", DerivedColor.SURFACE_HEADER_ACTIVE, "#3D4658",
        DerivedColor.SURFACE_STRIPE, "#383B40", DerivedColor.SURFACE_TABLE_HEADER, "#393C41", DerivedColor.SURFACE_ZEBRA, "#313439",
        DerivedColor.WARNING_GROUND, "#4A4128", DerivedColor.WARNING_BORDER, "#6B5A30");
    /** The neutral roles 7.6 leaves to its rule (Rulings 7). */
    static final List<String> NEUTRAL = List.of("surface.panel.raised", "surface.field.raised", "surface.control", "surface.control.hover",
        "surface.control.selected", "surface.menu", "surface.tabstrip", "surface.emptyTile", "border.field.raised", "border.field.raised.hover",
        "border.menu", "text.field.raised", "segment.track", "segment.selected.bg", "toggle.track.off", "toggle.knob", "button.secondary.bg",
        "button.secondary.bg.hover", "status.idle", "hue.neutral");

    static JasperTheme theme() throws Exception { return JasperTheme.parse(Files.readString(FILE), "enterprise"); }
    static Color role(JasperTheme theme, boolean dark, String key) { return theme.palette(dark).color(SemanticColor.of(key).orElseThrow()); }

    @Test void bothVariantsAreSpecSevenSixsTables() throws Exception {
        var theme = theme();
        assertThat(theme.name()).isEqualTo("Enterprise");
        LIGHT.forEach((key, hex) -> assertThat(ColorMath.hex(role(theme, false, key))).as("light " + key).isEqualToIgnoringCase(hex));
        DARK.forEach((key, hex) -> assertThat(ColorMath.hex(role(theme, true, key))).as("dark " + key).isEqualToIgnoringCase(hex));
        LIGHT_OVERRIDES.forEach((role, hex) -> assertThat(ColorMath.hex(theme.generated(false).derivedOverrides().get(role))).as("light " + role).isEqualToIgnoringCase(hex));
        DARK_OVERRIDES.forEach((role, hex) -> assertThat(ColorMath.hex(theme.generated(true).derivedOverrides().get(role))).as("dark " + role).isEqualToIgnoringCase(hex));
        assertThat(theme.generated(true).derivedOverrides()).as("Dark's row hover keeps its recipe").doesNotContainKey(DerivedColor.SURFACE_ROW_HOVER);
    }

    @Test void theUnnamedRolesFollowTheRule() throws Exception {
        var theme = theme();
        var cobalt = JasperTheme.bundled();
        double lightCard = ColorMath.lightness(role(theme, false, "surface.card")), darkCard = ColorMath.lightness(role(theme, true, "surface.card"));
        for (String key : NEUTRAL) {
            double light = ColorMath.lightness(role(theme, false, key));
            assertThat(light).as("light " + key + " keeps Slate Cobalt Light's L*").isCloseTo(ColorMath.lightness(role(cobalt, false, key)), org.assertj.core.data.Offset.offset(0.6));
            if (DARK.containsKey(key)) continue;
            assertThat(ColorMath.lightness(role(theme, true, key)) - darkCard).as("dark " + key + " keeps Light's distance from the card, lighter")
                .isCloseTo(Math.abs(light - lightCard), org.assertj.core.data.Offset.offset(0.6));
        }
        for (String key : List.of("status.danger", "hue.purple", "text.onAccent.secondary", "text.onSelection.secondary"))
            assertThat(ColorMath.contrast(role(theme, true, key), role(theme, true, "surface.card"))).as("dark " + key).isGreaterThanOrEqualTo(4.5);
        assertThat(theme.schemes()).extracting(scheme -> scheme.id().toString())
            .containsExactly("enterprise/light", "enterprise/dark", "enterprise/dark-terminal");
        var darkTerminal = theme.schemes().get(2).colors();
        assertThat(ColorMath.hex(darkTerminal.background())).isEqualToIgnoringCase("#1F2125");
        assertThat(ColorMath.hex(darkTerminal.foreground())).isEqualToIgnoringCase("#D5D8DD");
        assertThat(darkTerminal.ansi()).isEqualTo(cobalt.matching(true).colors().ansi());
        assertThat(ColorMath.hex(theme.matching(true).colors().background())).isEqualToIgnoringCase("#2E3135");
    }

    /** Verified fact 12: where the mocks paint a theme role, at their 2x pixels. */
    @Test void theThemeMatchesTheMocksWhereTheyShowIt() throws Exception {
        var reference = ROOT.resolve("docs/design/enterprise/reference");
        var mocks = Map.of(false, reference.resolve("01 Workstation/Workstation - Connected (plugins in use).png"),
            true, reference.resolve("08 Dark Mode/Workstation Dark - Connected (plugins in use).png"));
        var theme = theme();
        for (boolean dark : new boolean[] {false, true}) {
            var image = ImageIO.read(mocks.get(dark).toFile());
            var generated = theme.generated(dark);
            Map<int[], Color> points = Map.of(new int[] {2592, 835}, role(theme, dark, "surface.card"), new int[] {2448, 455}, role(theme, dark, "selection.bg"),
                new int[] {1728, 144}, role(theme, dark, "surface.app"), new int[] {2434, 204}, generated.derivedOverrides().get(DerivedColor.SURFACE_HEADER_ACTIVE),
                new int[] {2600, 1158}, generated.derivedOverrides().get(DerivedColor.SURFACE_TABLE_HEADER),
                new int[] {700, 1730}, generated.derivedOverrides().get(DerivedColor.SURFACE_STRIPE), new int[] {1296, 864}, theme.matching(dark).colors().background());
            points.forEach((at, color) -> assertThat(ColorMath.hex(new Color(image.getRGB(at[0], at[1]) & 0xFFFFFF)))
                .as((dark ? "dark" : "light") + " mock at " + at[0] + "," + at[1]).isEqualToIgnoringCase(ColorMath.hex(color)));
        }
    }
}
```

`TerminalColors` exposes `background()`, `foreground()` and `ansi()`, and `ColorMath.hex` returns `#RRGGBB` for an opaque
colour (checked by compiling the class at `e502e102b`).

In `BundledSamplePluginTest`, the bundled set grows by Enterprise (off by default):

```bash
python3 -I - <<'PY'
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
T = "jasper-app/src/test/java/dev/jasper/app/plugins/"
edit(T + "BundledSamplePluginTest.java", [
    ('''        assertThat(runtime.get().statusLines()).as("the sample and the seven bundled feature plugins").hasSize(8)''',
     '''        assertThat(runtime.get().statusLines()).as("the sample, the seven bundled feature plugins and Enterprise, off by default").hasSize(9)
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.enterprise", "BUNDLED", "DISABLED", "off by default"))''', 1),
    ('''            assertThat(rows).hasSize(8).allSatisfy(row -> assertThat(row.icon()).as(row.id()).isPresent());''',
     '''            assertThat(rows).hasSize(9).allSatisfy(row -> assertThat(row.icon()).as(row.id()).isPresent());''', 1)])
edit(T + "PluginZipsTest.java", [
    ('''        assertThat(zips).as("one zip per plugins/ directory").hasSize(8);''', '''        assertThat(zips).as("one zip per plugins/ directory").hasSize(9);''', 1),
    ('''"dev.jasper.fileserver", "dev.jasper.indexer");''', '''"dev.jasper.fileserver", "dev.jasper.indexer", "dev.jasper.enterprise");''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/architecture/ColourLiteralScanTest.java", [
    ('''    @Test void everyInRepoPluginThemeLoadsWithTheProductionLoader() throws IOException {''',
     '''    @Test void everyInRepoPluginThemeLoadsWithTheProductionLoader() throws IOException {
        assertThat(PLUGIN_THEMES).contains(ROOT.resolve("plugins/enterprise/src/main/resources/themes/enterprise.json"));''', 1)])
PY
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.EnterpriseThemeTest' --tests 'dev.jasper.app.plugins.PluginZipsTest' --continue`
Expected: FAIL: `EnterpriseThemeTest` cannot read `plugins/enterprise/src/main/resources/themes/enterprise.json`
(`NoSuchFileException`), and `PluginZipsTest` finds eight zips. (`:jasper-plugin-enterprise` does not exist yet, so its
test cannot run.)

- [ ] **Step 4: Write the plugin**

Create `plugins/enterprise/build.gradle.kts`:

```kotlin
// The Enterprise look: its kit file, fonts and theme are data the host reads; its code only publishes Workstation.
// It compiles against the SDK only and bundles nothing.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
}
```

Create `plugins/enterprise/src/main/resources/plugin.toml`:

```toml
id = "dev.jasper.enterprise"
name = "Enterprise"
version = "0.1.0"
entry = "dev.jasper.enterprise.EnterprisePlugin"
sdk = ">=0.18.0, <0.19"
description = "The Enterprise look: native title bars, menus in the window, labelled tool window stripes, a Run window and classic controls, in the Enterprise theme or any other."
vendor = "Jasper"
icon_hue = "blue"
icon_selected_ink = "foreground"
icon = "icons/enterprise.svg"
enabled_by_default = false
capabilities = ["presentation.provide"]
exports = ["dev.jasper.enterprise.api"]
presentation = "presentation/enterprise.kit.toml"
themes = ["themes/enterprise.json"]
```

Create `plugins/enterprise/src/main/resources/icons/enterprise.svg`:

```xml
<!-- Jasper artwork: a window with its title and menu rows; no third-party artwork imported. -->
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="#9bbbe0" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
<rect x="2" y="2.5" width="12" height="11" rx="1"/><path d="M2 5.5h12M2 8h12"/>
</svg>
```

Create `plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml` (spec 5.1 without `[branding]`, which
plan 11 adds):

```toml
kit = 1
base = "classic"

[chrome]
title_bar = "native"
menu_bar = "window"
window_title = "Workstation — {session} — {directory}"
tool_windows = "stripes"
editor_tabs = "classic"
status_bar = "cells"
palette = "run_window"
dialogs = "classic"
motion = "none"

[controls]
toggle = "checkbox"
exclusive = "radios"
tabs = "folder"
group = "titled_box"
form_label = "colon"
empty = "text"
table = "classic"
tree = "classic"
stepper = "spinner"
ordered_list = "side_column"
buttons = "classic"
tag = "boxed"
route = "edged"
prompt = "plain"

[metrics]
control_height = 22
row_height = 22
control_radius = 3
field_radius = 2
group_radius = 3
focus_ring = 2
tool_window_header = 24
group_padding = 12
dependent_indent = 20
navigation_width = 230
status_dot = 7

[type]
base = 12

[type.ui]
family = "IBM Plex Sans"
regular = "fonts/ibm-plex-sans-regular.ttf"
medium = "fonts/ibm-plex-sans-medium.ttf"
semibold = "fonts/ibm-plex-sans-semibold.ttf"

[type.data]
family = "IBM Plex Mono"
regular = "fonts/ibm-plex-mono-regular.ttf"
medium = "fonts/ibm-plex-mono-medium.ttf"

[colors]
dialog_surface = "surface.app"
navigation_surface = "surface.card"

[defaults]
"ui.theme.light" = "enterprise"
"ui.theme.dark" = "enterprise"
"palette.visible_rows" = 8
"window.toolbar_height" = 34
```

Create `plugins/enterprise/src/main/resources/themes/enterprise.json` (spec 7.6 and Rulings 7; verified fact 13):

```json
{
  "jasper-theme": 1, "name": "Enterprise", "author": "Jasper",
  "ui": {
    "light": {
      "surface.app": "#ECEDEF", "surface.panel": "#F7F7F8", "surface.panel.raised": "#EEEFF1", "surface.dialog": "#ECEDEF",
      "surface.card": "#FFFFFF", "surface.sidebar": "#F7F7F8", "surface.input": "#FFFFFF", "surface.field.raised": "#FFFFFF",
      "surface.control": "#FFFFFF", "surface.control.hover": "#F0F1F3", "surface.control.selected": "#D7D9DC", "surface.menu": "#FFFFFF",
      "surface.terminal": "#FFFFFF", "surface.tabstrip": "#DDDFE2", "surface.emptyTile": "#DDDFE2", "surface.scrim": "#1B1F2740",
      "border.window": "#C4C7CC", "border.subtle": "#0000000F", "border.input": "#B4B8BF", "border.field.raised": "#D0D3D8",
      "border.field.raised.hover": "#B2B6BD", "border.control": "#B4B8BF", "border.menu": "#D0D3D8", "border.error": "#C42B31",
      "text.primary": "#1E1F22", "text.secondary": "#5C6370", "text.strong.secondary": "#3A3D42", "text.tertiary": "#5C6370",
      "text.placeholder": "#7A808A", "text.field.raised": "#4C4F55", "text.onAccent": "#FFFFFF", "text.onAccent.secondary": "#DCE5F7",
      "text.onSelection": "#FFFFFF", "text.onSelection.secondary": "#DCE5F7", "text.link": "#2563C9", "text.link.hover": "#1D4F9F",
      "text.error": "#B3262C", "text.success": "#1E7A46", "accent": "#2F65CA", "accent.hover": "#3470D2",
      "selection.bg": "#2F65CA", "hover.overlay": "#000000", "segment.track": "#EBECEE", "segment.selected.bg": "#FFFFFF",
      "toggle.track.off": "#C6C9CE", "toggle.knob": "#FFFFFF", "button.secondary.bg": "#FFFFFF", "button.secondary.bg.hover": "#F3F3F4",
      "button.secondary.border": "#B4B8BF", "tab.indicator": "#2563C9", "tab.indicator.inactive": "#90AFE3", "status.success": "#2E9E4F",
      "status.warning": "#8A5A00", "status.danger": "#D03A3A", "status.idle": "#9FA2A9", "hue.blue": "#2F65CA",
      "hue.green": "#2E9E4F", "hue.amber": "#B5651D", "hue.purple": "#8A4FBF", "hue.neutral": "#5B626F",
      "surface.titlebar": "#E1E3E6", "surface.header": "#E1E3E6", "surface.header.active": "#D9DFEA", "surface.stripe": "#E4E6E9",
      "surface.tableHeader": "#F0F1F3", "surface.zebra": "#F7F8FA", "surface.rowHover": "#EEF2FA", "warning.ground": "#FBF1DA",
      "warning.border": "#E6CF95"
    },
    "dark": {
      "surface.app": "#3C3F44", "surface.panel": "#35383D", "surface.panel.raised": "#3A3D42", "surface.dialog": "#3C3F44",
      "surface.card": "#2E3135", "surface.sidebar": "#35383D", "surface.input": "#2E3135", "surface.field.raised": "#2E3135",
      "surface.control": "#2E3135", "surface.control.hover": "#393C41", "surface.control.selected": "#50545B", "surface.menu": "#2E3135",
      "surface.terminal": "#2E3135", "surface.tabstrip": "#474A4F", "surface.emptyTile": "#474A4F", "surface.scrim": "#0A0C108C",
      "border.window": "#2A2C30", "border.subtle": "#FFFFFF14", "border.input": "#5A5E65", "border.field.raised": "#50545B",
      "border.field.raised.hover": "#6A6E75", "border.control": "#5A5E65", "border.menu": "#50545B", "border.error": "#F0716B",
      "text.primary": "#DFE1E5", "text.secondary": "#A0A5AD", "text.strong.secondary": "#C4C8CE", "text.tertiary": "#A0A5AD",
      "text.placeholder": "#8E939B", "text.field.raised": "#D7D9DD", "text.onAccent": "#FFFFFF", "text.onAccent.secondary": "#DCE5F7",
      "text.onSelection": "#FFFFFF", "text.onSelection.secondary": "#DCE5F7", "text.link": "#84B3FF", "text.link.hover": "#A8C9FF",
      "text.error": "#FF8A83", "text.success": "#52D273", "accent": "#3D74D6", "accent.hover": "#2563C9",
      "selection.bg": "#3D74D6", "hover.overlay": "#FFFFFF", "segment.track": "#3D4045", "segment.selected.bg": "#2E3135",
      "toggle.track.off": "#595D64", "toggle.knob": "#2E3135", "button.secondary.bg": "#45484D", "button.secondary.bg.hover": "#373A3F",
      "button.secondary.border": "#5A5E65", "tab.indicator": "#5B9BF5", "tab.indicator.inactive": "#436596", "status.success": "#4CB86A",
      "status.warning": "#E3C27A", "status.danger": "#FF645D", "status.idle": "#7C8088", "hue.blue": "#3D74D6",
      "hue.green": "#4CB86A", "hue.amber": "#D08A4A", "hue.purple": "#BB7CF1", "hue.neutral": "#BFC3C9",
      "surface.titlebar": "#36393E", "surface.header": "#36393E", "surface.header.active": "#3D4658", "surface.stripe": "#383B40",
      "surface.tableHeader": "#393C41", "surface.zebra": "#313439", "warning.ground": "#4A4128", "warning.border": "#6B5A30"
    }
  },
  "terminal": {
    "light": {
      "name": "Enterprise Light", "bg": "#FFFFFF", "fg": "#1B1F27", "fg.bold": "#000000", "cursor": "#2563C9", "selection": "#C9DAF2",
      "black": "#1B1F27", "red": "#C42B31", "green": "#1E7A46", "yellow": "#8A6200", "blue": "#1F5FBF", "magenta": "#7A3E9D",
      "cyan": "#0A728E", "white": "#5A6270", "bright.black": "#6B7484", "bright.red": "#B3262C", "bright.green": "#1F8043", "bright.yellow": "#946800",
      "bright.blue": "#2867C8", "bright.magenta": "#8A4AB0", "bright.cyan": "#0D7790", "bright.white": "#000000"
    },
    "dark": {
      "name": "Enterprise Dark", "bg": "#2E3135", "fg": "#DFE1E5", "fg.bold": "#FFFFFF", "cursor": "#5B9BF5", "selection": "#3A5A8C",
      "black": "#4A515C", "red": "#FFA49D", "green": "#5FE88A", "yellow": "#FFD75F", "blue": "#8FC0FF", "magenta": "#EBA2FF",
      "cyan": "#4FE0E8", "white": "#D9DEE5", "bright.black": "#A5AEB9", "bright.red": "#FFCEC8", "bright.green": "#9CF5B6", "bright.yellow": "#FFE89A",
      "bright.blue": "#BEDCFF", "bright.magenta": "#F6CCFF", "bright.cyan": "#98F2F5", "bright.white": "#FFFFFF"
    },
    "dark-terminal": {
      "name": "Enterprise Dark Terminal", "bg": "#1F2125", "fg": "#D5D8DD", "fg.bold": "#FFFFFF", "cursor": "#5B9BF5", "selection": "#3A5A8C",
      "black": "#4A515C", "red": "#FFA49D", "green": "#5FE88A", "yellow": "#FFD75F", "blue": "#8FC0FF", "magenta": "#EBA2FF",
      "cyan": "#4FE0E8", "white": "#D9DEE5", "bright.black": "#A5AEB9", "bright.red": "#FFCEC8", "bright.green": "#9CF5B6", "bright.yellow": "#FFE89A",
      "bright.blue": "#BEDCFF", "bright.magenta": "#F6CCFF", "bright.cyan": "#98F2F5", "bright.white": "#FFFFFF"
    }
  },
  "editor": {
    "light": {
      "background": "terminal.bg", "foreground": "terminal.fg", "caret": "terminal.cursor", "selection": "terminal.selection",
      "currentLine": "hover.overlay @ 5%", "gutter": "terminal.bg", "lineNumbers": "text.tertiary", "keyword": "terminal.ansi.magenta",
      "type": "terminal.ansi.cyan", "function": "terminal.ansi.blue", "variable": "terminal.fg", "constant": "terminal.ansi.yellow",
      "number": "terminal.ansi.yellow", "string": "terminal.ansi.green", "comment": "terminal.ansi.white, italic", "doc_comment": "terminal.ansi.white, italic",
      "annotation": "terminal.ansi.yellow", "operator": "text.secondary", "punctuation": "text.secondary", "tag": "terminal.ansi.red",
      "attribute": "terminal.ansi.yellow", "regex": "terminal.ansi.cyan", "error": "terminal.ansi.red"
    },
    "dark": {
      "background": "terminal.bg", "foreground": "terminal.fg", "caret": "terminal.cursor", "selection": "terminal.selection",
      "currentLine": "hover.overlay @ 5%", "gutter": "terminal.bg", "lineNumbers": "text.tertiary", "keyword": "terminal.ansi.magenta",
      "type": "terminal.ansi.cyan", "function": "terminal.ansi.blue", "variable": "terminal.fg", "constant": "terminal.ansi.yellow",
      "number": "terminal.ansi.yellow", "string": "terminal.ansi.green", "comment": "terminal.ansi.bright.black, italic", "doc_comment": "terminal.ansi.bright.black, italic",
      "annotation": "terminal.ansi.yellow", "operator": "text.secondary", "punctuation": "text.secondary", "tag": "terminal.ansi.red",
      "attribute": "terminal.ansi.yellow", "regex": "terminal.ansi.cyan", "error": "terminal.ansi.red"
    }
  }
}
```

Create `plugins/enterprise/src/main/java/dev/jasper/enterprise/api/Workstation.java`:

```java
package dev.jasper.enterprise.api;

/** Published only while the Enterprise presentation is active; a plugin that finds it composes its classic layout. */
public interface Workstation { }
```

Create `plugins/enterprise/src/main/java/dev/jasper/enterprise/api/package-info.java`:

```java
/**
 * The contract other plugins compile against: {@link dev.jasper.enterprise.api.Workstation}, a presence-only service
 * published while the Enterprise look runs (the equivalent of IntelliJ's optional dependency on its Classic UI plugin).
 * A consumer declares {@code { id = "dev.jasper.enterprise", version = ">=0.1", optional = true }}, finds the service and
 * composes its classic layout; without it, its default one. Exported by the plugin descriptor; nothing else is.
 */
package dev.jasper.enterprise.api;
```

Create `plugins/enterprise/src/main/java/dev/jasper/enterprise/EnterprisePlugin.java`:

```java
package dev.jasper.enterprise;

import dev.jasper.enterprise.api.Workstation;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;

/**
 * The Enterprise look. Its kit file, fonts and theme are data the host reads from the descriptor before the first window;
 * this code only publishes {@link Workstation}, so its start cannot fail.
 */
public final class EnterprisePlugin implements Plugin {
    /** Publishes {@link Workstation} and nothing else. */
    @Override public void start(PluginContext context) {
        context.services().publish(Workstation.class, new Workstation() { });
    }
}
```

Create `plugins/enterprise/src/main/java/dev/jasper/enterprise/package-info.java`:

```java
/**
 * The Enterprise plugin's entry point. The look itself is the descriptor's presentation, fonts and theme: data the host
 * reads; this package only publishes {@link dev.jasper.enterprise.api.Workstation}.
 */
package dev.jasper.enterprise;
```

- [ ] **Step 5: Bundle it and list its fonts**

```bash
python3 -I - <<'PY'
"""Plan 5b Task 3 step 5: stagePlugins, the IBM Plex notice and the font-files check."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/build.gradle.kts", [
    ('''    from(project(":jasper-plugin-indexer").configurations.named("runtimeClasspath")) { into("dev.jasper.indexer") }
    into(layout.buildDirectory.dir("plugins"))''',
     '''    from(project(":jasper-plugin-indexer").configurations.named("runtimeClasspath")) { into("dev.jasper.indexer") }
    from(project(":jasper-plugin-enterprise").tasks.named("jar")) { into("dev.jasper.enterprise") }
    into(layout.buildDirectory.dir("plugins"))''', 1)])
edit("jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml", [
    ('''[[component]]
name = "IntelliJ Platform icons"''',
     '''[[component]]
name = "IBM Plex"
version = "1.1.0"
license = "OFL-1.1"
homepage = "https://github.com/IBM/plex"
text = "ibm-plex.OFL.txt"
files = ["dev.jasper.enterprise/presentation/fonts/ibm-plex-mono-medium.ttf", "dev.jasper.enterprise/presentation/fonts/ibm-plex-mono-regular.ttf",
  "dev.jasper.enterprise/presentation/fonts/ibm-plex-sans-medium.ttf", "dev.jasper.enterprise/presentation/fonts/ibm-plex-sans-regular.ttf",
  "dev.jasper.enterprise/presentation/fonts/ibm-plex-sans-semibold.ttf"]

[[component]]
name = "IntelliJ Platform icons"''', 1),
    ('''# folder, or absolute classpath resources. license is an SPDX id ThirdPartyNotices knows.''',
     '''# folder, or absolute classpath resources. license is an SPDX id ThirdPartyNotices knows. files are the font files a
# bundled plugin ships, as <plugin id>/<path in its jar>; the check fails when one is in no component.''', 1)])
edit("gradle/third-party-notices.gradle.kts", [
    ('''    val index = noticesApp.file("src/main/resources/dev/jasper/app/about/licenses/notices.toml")
    inputs.file(index)''',
     '''    val index = noticesApp.file("src/main/resources/dev/jasper/app/about/licenses/notices.toml")
    inputs.file(index)
    inputs.files(rootProject.fileTree("plugins") { include("*/src/main/resources/plugin.toml", "*/src/main/resources/**/*.ttf", "*/src/main/resources/**/*.otf") })''', 1),
    ('''        val listed = mutableListOf<String>()''', '''        val listed = mutableListOf<String>()
        val listedFiles = mutableListOf<String>()''', 1),
    ('''            Regex("(?s)modules\\\\s*=\\\\s*\\\\[(.*?)]").find(block)?.groupValues?.get(1)?.let { list ->
                Regex("\\"([^\\"]+)\\"").findAll(list).forEach { listed.add(it.groupValues[1]) }
            }''',
     '''            Regex("(?s)modules\\\\s*=\\\\s*\\\\[(.*?)]").find(block)?.groupValues?.get(1)?.let { list ->
                Regex("\\"([^\\"]+)\\"").findAll(list).forEach { listed.add(it.groupValues[1]) }
            }
            Regex("(?s)files\\\\s*=\\\\s*\\\\[(.*?)]").find(block)?.groupValues?.get(1)?.let { list ->
                Regex("\\"([^\\"]+)\\"").findAll(list).forEach { listedFiles.add(it.groupValues[1]) }
            }''', 1),
    ('''        listed.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { problems.add("$it is listed twice") }''',
     '''        listed.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { problems.add("$it is listed twice") }
        // Font files ship inside plugin jars, not as Maven modules: each must be listed in a component's files (spec 14.1).
        val fonts = rootProject.subprojects.filter { it.path.startsWith(":jasper-plugin-") }.flatMap { plugin ->
            val pluginResources = plugin.file("src/main/resources")
            val descriptor = pluginResources.resolve("plugin.toml")
            val id = if (descriptor.isFile) Regex("(?m)^id\\\\s*=\\\\s*\\"([^\\"]+)\\"").find(descriptor.readText())?.groupValues?.get(1) else null
            if (id == null) emptyList<String>() else pluginResources.walkTopDown().filter { it.isFile && (it.extension == "ttf" || it.extension == "otf") }
                .map { "$id/" + it.relativeTo(pluginResources).invariantSeparatorsPath }.toList()
        }.toSortedSet()
        (fonts - listedFiles.toSet()).forEach { problems.add("$it ships but no component in notices.toml lists it in files") }
        (listedFiles.toSet() - fonts).forEach { problems.add("$it is listed in notices.toml files but no bundled plugin ships it") }''', 1)])
PY
```

Check the Kotlin edits with `sed -n 1,60p gradle/third-party-notices.gradle.kts` (the regular expressions are written
with `\\s` and `\"` inside Kotlin strings, as the file's existing `modules` expression is). Add the ledger row that makes
`plugins/enterprise` an owner, and the test's row:

```
plugins/enterprise/src/main/java/dev/jasper/enterprise/EnterprisePlugin.java,dev.jasper.enterprise,EnterprisePlugin,nonvisual-helper,none,none,plugins/enterprise/src/test/java/dev/jasper/enterprise/EnterprisePluginTest.java,New in Enterprise plan 5b Task 3; publishes Workstation only; the look is descriptor data
plugins/enterprise/src/test/java/dev/jasper/enterprise/EnterprisePluginTest.java,dev.jasper.enterprise,EnterprisePluginTest,nonvisual-helper,none,none,plugins/enterprise/src/test/java/dev/jasper/enterprise/EnterprisePluginTest.java,Test/fixture ownership; New in Enterprise plan 5b Task 3
```

and `EnterpriseThemeTest.java`'s row (owner `app.appearance`; it imports `java.awt` and `dev.jasper.ui`) in plan 5a's form.

**If plan 6b's Task 1 is on the branch** (the last "Before you start" check prints 1: `border.divider` is the ninth
overridable derived role, approved on 2026-10-17), the theme takes 6b's chrome targets as 6b's ruling 2 and Execution
notes R47 give them, over Rulings 7's values for those roles (Light `surface.tabstrip` `#E8E9EB`, `tab.indicator`
`#2F65CA`, `surface.scrim` `#00000036`, `border.divider` `#D3D6DA`; Dark `#3A3D42`, `#3D74D6`, `#00000036`,
`#4A4D53`), and the test follows (Execution notes, ruling 8). Otherwise skip this script, leave the eight values to plan
6b's merge (its R47) and say so in the status banner.

```bash
grep -c 'BORDER_DIVIDER("border.divider", true)' jasper-ui/src/main/java/dev/jasper/ui/theme/DerivedColor.java   # 1: run the script
python3 -I - <<'PY'
"""Plan 5b Task 3 step 5, with plan 6b on the branch: its chrome targets in the shipped theme (6b R47)."""
import json, pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
F = "plugins/enterprise/src/main/resources/themes/enterprise.json"
edit(F, [
    ('''"surface.tabstrip": "#DDDFE2"''', '''"surface.tabstrip": "#E8E9EB"''', 1),
    ('''"surface.tabstrip": "#474A4F"''', '''"surface.tabstrip": "#3A3D42"''', 1),
    ('''"surface.scrim": "#1B1F2740"''', '''"surface.scrim": "#00000036"''', 1),
    ('''"surface.scrim": "#0A0C108C"''', '''"surface.scrim": "#00000036"''', 1),
    ('''"tab.indicator": "#2563C9"''', '''"tab.indicator": "#2F65CA"''', 1),
    ('''"tab.indicator": "#5B9BF5"''', '''"tab.indicator": "#3D74D6"''', 1),
    ('''      "warning.border": "#E6CF95"''', '''      "warning.border": "#E6CF95", "border.divider": "#D3D6DA"''', 1),
    ('''"warning.ground": "#4A4128", "warning.border": "#6B5A30"''', '''"warning.ground": "#4A4128", "warning.border": "#6B5A30", "border.divider": "#4A4D53"''', 1)])
json.loads(pathlib.Path(F).read_text(encoding="utf-8"))
edit("jasper-app/src/test/java/dev/jasper/app/appearance/EnterpriseThemeTest.java", [
    ('''"surface.control.selected", "surface.menu", "surface.tabstrip", "surface.emptyTile",''',
     '''"surface.control.selected", "surface.menu", "surface.emptyTile",''', 1),
    ('''DerivedColor.WARNING_GROUND, "#FBF1DA", DerivedColor.WARNING_BORDER, "#E6CF95");''',
     '''DerivedColor.WARNING_GROUND, "#FBF1DA", DerivedColor.WARNING_BORDER, "#E6CF95", DerivedColor.BORDER_DIVIDER, "#D3D6DA");''', 1),
    ('''DerivedColor.WARNING_GROUND, "#4A4128", DerivedColor.WARNING_BORDER, "#6B5A30");''',
     '''DerivedColor.WARNING_GROUND, "#4A4128", DerivedColor.WARNING_BORDER, "#6B5A30", DerivedColor.BORDER_DIVIDER, "#4A4D53");''', 1)])
PY
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-enterprise:check :jasper-app:test --tests 'dev.jasper.app.appearance.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.architecture.*' --tests 'dev.jasper.app.about.*' :jasper-app:verifyThirdPartyNotices --continue`
Expected: PASS: `EnterprisePluginTest` (3), `EnterpriseThemeTest` (3), `verifyPluginArchitecture` for the new plugin (it
references only the JDK and the SDK), the staged-plugin tests with nine bundled plugins (Enterprise DISABLED "off by
default"), `PluginZipsTest`, `ColourLiteralScanTest` (the theme file exempt and loading), `ThirdPartyNoticesTest` and
`verifyThirdPartyNotices` ("Verified third-party notices for …").

- [ ] **Step 7: Commit**

```bash
git add plugins/enterprise jasper-app/build.gradle.kts jasper-app/src gradle/third-party-notices.gradle.kts docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Enterprise: the bundled plugin

plugins/enterprise ships the Enterprise look as data: its descriptor (off
by default, presentation.provide), the kit file of spec 5.1 without
[branding], IBM Plex Sans and Mono 1.1.0 with their OFL notice, and the
Enterprise theme in Light and Dark (spec 7.6). Its code only publishes
dev.jasper.enterprise.api.Workstation. stagePlugins bundles it, and
verifyThirdPartyNotices now requires every font file a bundled plugin
ships to be listed in a component's files.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Conformance, the fixture, the shipped-kit renders and a fresh home

Spec 16.1.5 and 16.1.6: `PresentationConformanceTest` checks the shipped descriptor, kit, fonts, theme and notice, from the
sources and from the staged jar; `EnterpriseFixtureTest` checks that the parsed kit equals jasper-ui's Enterprise fixture;
`EnterpriseKitRenderTest` registers the real Plex files and renders the gallery under the shipped kit in Enterprise Light
and Dark, writing images under `jasper-app/build/enterprise-kit/`; `tools/visual/pairs.json` gains the opt-in crops
pairing those renders with the Workstation and Dark Mode mocks (Rulings 9); `KitFixtures.themes()` gains Enterprise
Light and Dark (16.1.2); `EnterpriseHomeTest` is Review Focus 4. Plan 6a's chrome tests and plan 8a's Run window render
switch from their fixtures to the shipped kit, Plex and the Enterprise theme (plan 6a Rulings 26, plan 8a R8, R9 and
R16; Execution notes, rulings 9 and 10).

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseResources.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/plugins/PresentationConformanceTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/EnterpriseHomeTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/presentation/EnterpriseFixtureTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/application/EnterpriseKitRenderTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/application/EnterpriseFirstWindowTest.java`,
  `jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles-enterprise.tsv` (generated in Step 3)
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/theme/DerivedRolesTableTest.java` (the Enterprise theme's derived roles),
  `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicTagFace.java:13-18,63` (the box against its tint)
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java:130-147` (`themes()`),
  `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java:25-30`, `jasper-ui/build.gradle.kts:18-27`,
  `jasper-app/build.gradle.kts` (`tasks.test` inputs), `tools/visual/pairs.json`, `tools/visual/README.md`,
  `docs/design/theme-engine/migration.csv`
- Modify: `jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeMenusTest.java:133-134`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseMenuRowMockTest.java:6-8,26-30,91-92`,
  `jasper-app/src/test/java/dev/jasper/app/palette/RunWindowRenderTest.java:24-44,91-92,127-136`
- Delete: `jasper-app/src/test/resources/dev/jasper/app/testsupport/enterprise-fixture.json`

**Interfaces:**
- Consumes: Task 1's `LookFonts.register`, `FaceSet.uiFace`; Task 2's `PluginSelection.themes()`, `ThemeLibrary.of(PluginThemes)`,
  `provider`, `dormant`; Task 3's plugin; plan 5a's `PresentationFacts.read(Source)`, `PluginSelection.scan`,
  `PluginStateStore.enabling(id, enabled, optIn)`, `ConfigService(Path, boolean, DefaultSource)`; plan 4's
  `KitFixtures.enterprise()`, `KitFixtures.enterpriseTokens()`, `Kit.CLASSIC`, `TypeScale.withBody` (5a's "Plan 4's names").
- Produces: `public final class dev.jasper.app.testsupport.EnterpriseResources` with `ROOT`, `RESOURCES`,
  `static KitParser.Files files()`, `static PresentationFacts facts()`, `static Kit kit()` (Plex registered),
  `static ChromeSpec chrome()`, `static JasperTheme theme()`; the renders
  `jasper-app/build/enterprise-kit/{light,dark}/<fixture>.png` and `<panel>@2x.png`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseResources.java`:

```java
package dev.jasper.app.testsupport;

import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.app.presentation.FontFile;
import dev.jasper.app.presentation.KitParser;
import dev.jasper.app.presentation.PresentationFacts;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.theme.JasperTheme;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The Enterprise plugin's resources as the sources hold them, read the way the host reads a jar (bounded, inside the
 * root): its kit (with its IBM Plex files registered in this JVM, as the application registers them), chrome and theme.
 */
public final class EnterpriseResources {
    /** The repository, from the test task's {@code jasper.repoRoot}. */
    public static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
    /** The plugin's resources folder: the root of its jar. */
    public static final Path RESOURCES = ROOT.resolve("plugins/enterprise/src/main/resources");

    private EnterpriseResources() { }

    /** Reads a jar path under {@link #RESOURCES}: empty when absent or outside, else at most {@code maxBytes + 1} bytes. */
    public static KitParser.Files files() {
        return (path, maxBytes) -> {
            Path file = RESOURCES.resolve(path).normalize();
            if (!file.startsWith(RESOURCES) || !Files.isRegularFile(file)) return Optional.empty();
            try (var in = Files.newInputStream(file)) { return Optional.of(in.readNBytes(maxBytes + 1)); }
            catch (IOException unreadable) { return Optional.empty(); }
        };
    }

    /** The shipped kit's facts, read by the production code. */
    public static PresentationFacts facts() {
        return PresentationFacts.read(new PresentationFacts.Source("dev.jasper.enterprise", "presentation/enterprise.kit.toml", files(),
            List.of("themes/enterprise.json")));
    }

    /** The shipped kit, parsed by the production parser, after its IBM Plex files are registered in this JVM. */
    public static Kit kit() {
        var parsed = facts().parsed().orElseThrow();
        for (FontFile file : parsed.faces().files()) {
            // A false return means a copy is registered already, which draws by the same name.
            try { GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(file.bytes()))); }
            catch (FontFormatException | IOException failure) { throw new AssertionError(file.path() + " is not a font", failure); }
        }
        return parsed.kit();
    }

    /** The shipped kit file's chrome points. */
    public static ChromeSpec chrome() { return facts().parsed().orElseThrow().chrome(); }

    private static JasperTheme theme;

    /** The shipped Enterprise theme, parsed once by the production loader. */
    public static synchronized JasperTheme theme() {
        if (theme != null) return theme;
        try { theme = JasperTheme.parse(Files.readString(RESOURCES.resolve("themes/enterprise.json")), "enterprise"); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        return theme;
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/plugins/PresentationConformanceTest.java`:

```java
package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.about.ThirdPartyNotices;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.presentation.FontFile;
import dev.jasper.app.testsupport.EnterpriseResources;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

/** Spec 16.1.6: the shipped Enterprise plugin is what spec 4.1, 5.1, 7.6 and 14.1 say, from the sources and from its jar. */
class PresentationConformanceTest {
    /** Every key of spec 5.1's kit file except [branding], which plan 11 adds. */
    static final Set<String> KEYS = Set.of("kit", "base",
        "chrome/title_bar", "chrome/menu_bar", "chrome/window_title", "chrome/tool_windows", "chrome/editor_tabs", "chrome/status_bar",
        "chrome/palette", "chrome/dialogs", "chrome/motion",
        "controls/toggle", "controls/exclusive", "controls/tabs", "controls/group", "controls/form_label", "controls/empty",
        "controls/table", "controls/tree", "controls/stepper", "controls/ordered_list", "controls/buttons", "controls/tag",
        "controls/route", "controls/prompt",
        "metrics/control_height", "metrics/row_height", "metrics/control_radius", "metrics/field_radius", "metrics/group_radius",
        "metrics/focus_ring", "metrics/tool_window_header", "metrics/group_padding", "metrics/dependent_indent",
        "metrics/navigation_width", "metrics/status_dot",
        "type/base", "type/ui/family", "type/ui/regular", "type/ui/medium", "type/ui/semibold",
        "type/data/family", "type/data/regular", "type/data/medium",
        "colors/dialog_surface", "colors/navigation_surface",
        "defaults/ui.theme.light", "defaults/ui.theme.dark", "defaults/palette.visible_rows", "defaults/window.toolbar_height");
    /** The official IBM Plex 1.1.0 files (verified fact 11), by their name in the plugin. */
    static final Map<String, String> SUMS = Map.of(
        "presentation/fonts/ibm-plex-sans-regular.ttf", "975dcda37d80f038dcd143c22e33ca2d97a0cc5a929aace1c749153b0fe1afa5",
        "presentation/fonts/ibm-plex-sans-medium.ttf", "331c8639d7598b2cde62a911a71db195e30cb655cd6bdf2e324a7e984955f907",
        "presentation/fonts/ibm-plex-sans-semibold.ttf", "a20caf8286023a6a7a85e40b1d2a4ae9fc3e3b1f9eda8f4c542dd4986af67bb1",
        "presentation/fonts/ibm-plex-mono-regular.ttf", "fe11304a5fe956d5744e9b6a246cc83d90425245e75a62230044966ca96a7f50",
        "presentation/fonts/ibm-plex-mono-medium.ttf", "0bede3debdea8488bbb927f8f0650d915073209734a67fe8cd5a3320b572511c");

    @Test void theDescriptorIsSpecFourOnes() throws Exception {
        var descriptor = DescriptorParser.parse(Files.readString(EnterpriseResources.RESOURCES.resolve("plugin.toml")));
        assertThat(descriptor.id()).isEqualTo("dev.jasper.enterprise");
        assertThat(descriptor.name()).isEqualTo("Enterprise");
        assertThat(descriptor.version()).isEqualTo(Version.parse("0.1.0"));
        assertThat(descriptor.entry()).isEqualTo("dev.jasper.enterprise.EnterprisePlugin");
        assertThat(descriptor.capabilities()).containsExactly(DescriptorParser.PRESENTATION_PROVIDE);
        assertThat(descriptor.exports()).containsExactly("dev.jasper.enterprise.api");
        assertThat(descriptor.requires()).as("no requirements (spec 14.1)").isEmpty();
        assertThat(descriptor.presentation()).isEqualTo("presentation/enterprise.kit.toml");
        assertThat(descriptor.themes()).containsExactly("themes/enterprise.json");
        assertThat(descriptor.themeIds()).containsExactly("enterprise");
        assertThat(descriptor.enabledByDefault()).isFalse();
        assertThat(descriptor.iconHue()).isEqualTo(dev.jasper.ui.model.Nodes.Hue.BLUE);
        assertThat(EnterpriseResources.RESOURCES.resolve(descriptor.icon())).isRegularFile();
    }

    @Test void theKitListsEveryKeyAndParsesWithoutAProblem() throws Exception {
        var toml = Toml.parse(Files.readString(EnterpriseResources.RESOURCES.resolve("presentation/enterprise.kit.toml")));
        assertThat(toml.errors()).isEmpty();
        var keys = new TreeSet<String>();
        toml.keyPathSet().forEach(path -> keys.add(String.join("/", path)));
        assertThat(keys).containsExactlyInAnyOrderElementsOf(KEYS);
        var facts = EnterpriseResources.facts();
        assertThat(facts.problem()).as("every token in range, the constraint held, every file present").isEmpty();
        var parsed = facts.parsed().orElseThrow();
        assertThat(parsed.classicBase()).isTrue();
        assertThat(parsed.dropped()).as("the [defaults] themes resolve").isEmpty();
        assertThat(parsed.defaults().value(CoreSettings.UI_THEME_LIGHT)).isEqualTo("enterprise");
        assertThat(parsed.defaults().value(CoreSettings.UI_THEME_DARK)).isEqualTo("enterprise");
        assertThat(parsed.defaults().value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(8);
        assertThat(parsed.defaults().value(CoreSettings.WINDOW_TOOLBAR_HEIGHT)).isEqualTo(34);
    }

    @Test void theFontsAreIbmsDecodeAndEveryWeightResolvesToItsFamily() throws Exception {
        var faces = EnterpriseResources.facts().parsed().orElseThrow().faces();
        var ui = faces.ui().orElseThrow();
        var data = faces.data().orElseThrow();
        assertThat(ui.family()).isEqualTo("IBM Plex Sans");
        assertThat(data.family()).isEqualTo("IBM Plex Mono");
        assertThat(faces.files()).extracting(FontFile::fullName)
            .containsExactly("IBM Plex Sans", "IBM Plex Sans Medm", "IBM Plex Sans SmBld", "IBM Plex Mono", "IBM Plex Mono Medm");
        var families = new ArrayList<String>();
        for (FontFile file : faces.files()) {
            Path path = EnterpriseResources.RESOURCES.resolve(file.path());
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))))
                .as(file.path() + " is IBM's file").isEqualTo(SUMS.get(file.path()));
            assertThat(file.bytes().length).as(file.path()).isLessThanOrEqualTo(2 * 1024 * 1024);
            Font font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(file.bytes()));
            assertThat(font.getFontName(Locale.ROOT)).as(file.path()).isEqualTo(file.fullName());
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            assertThat(new Font(file.fullName(), Font.PLAIN, 12).getFontName(Locale.ROOT)).as(file.path() + " resolves").isEqualTo(file.fullName());
            families.add(file.family());
        }
        assertThat(families).containsExactly("IBM Plex Sans", "IBM Plex Sans", "IBM Plex Sans", "IBM Plex Mono", "IBM Plex Mono");
        assertThat(SUMS.keySet()).containsExactlyInAnyOrderElementsOf(faces.files().stream().map(FontFile::path).toList());
    }

    @Test void theThemeLoadsWithBothVariantsAndTheNoticeIsListed() {
        var theme = EnterpriseResources.theme();
        assertThat(theme.has(true)).isTrue();
        assertThat(theme.has(false)).isTrue();
        assertThat(theme.name()).isEqualTo("Enterprise");
        var plex = ThirdPartyNotices.thirdParty().stream().filter(component -> component.name().equals("IBM Plex")).toList();
        assertThat(plex).singleElement().satisfies(component -> {
            assertThat(component.license()).isEqualTo("OFL-1.1");
            assertThat(component.version()).isEqualTo("1.1.0");
            assertThat(ThirdPartyNotices.read(component.text())).contains("Reserved Font Name \"Plex\"", "SIL OPEN FONT LICENSE Version 1.1");
        });
    }

    /** The staged jar carries the same kit: the host reads it with the same digest and no problem. */
    @Test void theStagedPluginIsTheSourcesAndOffByDefault() {
        var problems = new ArrayList<String>();
        var candidates = PluginDiscovery.scan(Path.of(System.getProperty("jasper.stagedPlugins")), PluginCandidate.Origin.BUNDLED, problems);
        assertThat(problems).isEmpty();
        var enterprise = candidates.stream().filter(candidate -> candidate.id().equals("dev.jasper.enterprise")).findFirst().orElseThrow();
        var facts = Presentations.read(List.of(enterprise), dev.jasper.app.presentation.AttemptStore.Record.EMPTY, "").of(enterprise).orElseThrow();
        assertThat(facts.problem()).isEmpty();
        assertThat(facts.digest()).isEqualTo(EnterpriseResources.facts().digest());
        assertThat(PluginStateStore.effective(null, enterprise)).as("off until the user turns it on").isFalse();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/presentation/EnterpriseFixtureTest.java`:

```java
package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.testsupport.KitFixtures;
import org.junit.jupiter.api.Test;

/** Spec 16.1.5: the shipped kit, parsed by the production parser, is jasper-ui's Enterprise fixture. */
class EnterpriseFixtureTest {
    @Test void theShippedKitIsTheFixtureKit() {
        Kit shipped = EnterpriseResources.facts().parsed().orElseThrow().kit();
        Kit fixture = KitFixtures.enterprise();
        assertThat(shipped.metrics()).isEqualTo(fixture.metrics());
        assertThat(shipped.type()).isEqualTo(fixture.type());
        assertThat(shipped.controls()).isEqualTo(fixture.controls());
        assertThat(shipped.colors()).isEqualTo(fixture.colors());
        assertThat(shipped.uiKeys()).isEqualTo(fixture.uiKeys());
        assertThat(shipped.metrics()).as("spec 5.1's tokens over the classic catalog")
            .isEqualTo(Kit.CLASSIC.metrics().with(KitFixtures.enterpriseTokens()));
        assertThat(shipped.type()).isEqualTo(Kit.CLASSIC.type().withBody(12));
        assertThat(shipped.faces().uiFace(400)).isEqualTo("IBM Plex Sans");
        assertThat(shipped.faces().uiFace(500)).isEqualTo("IBM Plex Sans Medm");
        assertThat(shipped.faces().uiFace(600)).isEqualTo("IBM Plex Sans SmBld");
        assertThat(shipped.faces().dataRegular()).isEqualTo("IBM Plex Mono");
        assertThat(shipped.faces().dataMedium()).isEqualTo("IBM Plex Mono Medm");
        assertThat(EnterpriseResources.facts().parsed().orElseThrow().chrome()).as("plan 6a's EnterpriseLook.CHROME is the shipped table")
            .isEqualTo(dev.jasper.app.testsupport.EnterpriseLook.CHROME);
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/application/EnterpriseKitRenderTest.java`:

```java
package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.ui.gallery.KitGallery;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Spec 16.1.5: the real {@code enterprise.kit.toml} through the production parser, the real Plex files registered, and
 * the gallery rendered under it in Enterprise Light and Dark. Images go to {@code jasper-app/build/enterprise-kit/}; the
 * four panels are also rendered at 2x for {@code tools/visual/pairs.json}.
 */
class EnterpriseKitRenderTest {
    static final Path OUT = EnterpriseResources.ROOT.resolve("jasper-app/build/enterprise-kit");
    static final List<String> PANELS = List.of("sessions", "tunnels", "sftp", "fileservers");

    @Test void theShippedKitRendersInPlexInBothVariants() throws Exception {
        var parsed = EnterpriseResources.facts().parsed().orElseThrow();
        assertThat(LookFonts.register(parsed.faces())).as("every Plex file registers").isEmpty();
        var theme = EnterpriseResources.theme();
        var fixtures = KitGallery.fixtures();
        for (boolean dark : new boolean[] {false, true}) {
            Path directory = Files.createDirectories(OUT.resolve(dark ? "dark" : "light"));
            var plexLabels = new ArrayList<String>();
            SwingUtilities.invokeAndWait(() -> KitGallery.installed(theme.generated(dark), parsed.kit(), (context, focus) -> {
                var typography = context.typography();
                assertThat(typography.font(Typography.Role.BODY).getFontName(Locale.ROOT)).isEqualTo("IBM Plex Sans");
                assertThat(typography.font(Typography.Role.BODY).getSize2D()).as("[type].base").isEqualTo(12f);
                assertThat(typography.font(Typography.Role.SECTION_LABEL).getFontName(Locale.ROOT)).isEqualTo("IBM Plex Sans SmBld");
                assertThat(typography.font(Typography.Role.MONO).getFontName(Locale.ROOT)).isEqualTo("IBM Plex Mono");
                try {
                    for (var fixture : fixtures) {
                        var capture = KitGallery.capture(fixture, context, focus);
                        ImageIO.write(capture.image(), "png", directory.resolve(fixture.name() + ".png").toFile());
                    }
                    for (var fixture : fixtures) {
                        if (!PANELS.contains(fixture.name())) continue;
                        try (var view = new KitView(context, fixture.spec(), event -> { })) {
                            var image = KitFixtures.render(view, fixture.width(), fixture.height(), 2);
                            ImageIO.write(image, "png", directory.resolve(fixture.name() + "@2x.png").toFile());
                            for (Component component : descendants(view))
                                if (component instanceof JComponent text && text.getFont() != null
                                    && text.getFont().getFontName(Locale.ROOT).startsWith("IBM Plex"))
                                    plexLabels.add(fixture.name());
                        }
                    }
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
            }));
            assertThat(plexLabels).as((dark ? "dark" : "light") + ": every panel paints Plex text").containsAll(PANELS);
            for (String panel : PANELS) assertThat(directory.resolve(panel + "@2x.png")).isRegularFile();
        }
    }

    private static List<Component> descendants(Container root) {
        var all = new ArrayList<Component>();
        for (Component child : root.getComponents()) {
            all.add(child);
            if (child instanceof Container nested) all.addAll(descendants(nested));
        }
        return all;
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/plugins/EnterpriseHomeTest.java`:

```java
package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.config.ConfigService;
import dev.jasper.sdk.JasperSdk;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Review Focus 4: a fresh home with Enterprise turned on starts with its kit's defaults and offers its theme. */
class EnterpriseHomeTest {
    @TempDir Path root;

    @Test void aFreshHomeUnderEnterpriseStartsWithTheKitsDefaults() throws Exception {
        var options = new PluginRuntime.Options(Path.of(System.getProperty("jasper.stagedPlugins")), root.resolve("user"), null, false,
            root.resolve("plugins.toml"), root.resolve("plugins.lock"));
        var off = PluginSelection.scan(options, "", Version.parse(JasperSdk.VERSION), PluginSelection.host());
        assertThat(off.presentation().look()).as("off by default").isEqualTo("default");
        var offLibrary = ThemeLibrary.of(off.themes());
        assertThat(offLibrary.theme("enterprise")).as("its theme is dormant while it is off").isEmpty();
        assertThat(offLibrary.dormant()).contains("enterprise");

        new PluginStateStore(options.stateFile(), options.lockFile(), Duration.ofSeconds(2))
            .transact(PluginStateStore.enabling("dev.jasper.enterprise", true, true));
        var on = PluginSelection.scan(options, "", Version.parse(JasperSdk.VERSION), PluginSelection.host());
        assertThat(on.presentation().look()).isEqualTo("dev.jasper.enterprise");
        assertThat(on.presentation().provider().orElseThrow().name()).isEqualTo("Enterprise");
        assertThat(on.presentation().droppedDefaults()).isEmpty();
        assertThat(on.presentation().kit().faces().uiFace(400)).isEqualTo("IBM Plex Sans");
        try (var service = new ConfigService(root.resolve("config.toml"), false, on.presentation().defaults())) {
            var snapshot = service.initialState().snapshot();
            assertThat(snapshot.visibleRows()).isEqualTo(8);
            assertThat(snapshot.toolbarHeight()).isEqualTo(34);
            assertThat(snapshot.lightTheme()).isEqualTo("enterprise");
            assertThat(snapshot.darkTheme()).isEqualTo("enterprise");
        }
        var library = ThemeLibrary.of(on.themes());
        assertThat(library.theme("enterprise")).hasValueSatisfying(theme -> assertThat(theme.name()).isEqualTo("Enterprise"));
        assertThat(library.provider("enterprise")).contains("Enterprise");
        assertThat(library.themes(true)).extracting(theme -> theme.id()).contains("enterprise");
        assertThat(library.themes(false)).extracting(theme -> theme.id()).contains("enterprise");
    }
}
```

`ConfigService` with a `config.toml` that does not exist reads the defaults (a fresh home); if its constructor writes the
file, that is the existing first-run behaviour and the assertions still hold.

Create `jasper-app/src/test/java/dev/jasper/app/application/EnterpriseFirstWindowTest.java` (spec 5.8's "a test starts a
fresh home under Enterprise and checks that the first window opens with the kit's defaults"; the staged plugin is passed
as the `--plugin-dir` folder, which needs no opt-in, so the test reaches it through the public pre-pass):

```java
package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.plugins.PluginSelection;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 5.8 and 16.1.8: the first window of a fresh home under Enterprise opens with the kit's defaults. */
class EnterpriseFirstWindowTest {
    @TempDir Path home;

    @Test void theFirstWindowOfAFreshHomeUnderEnterpriseUsesTheKitsDefaults() throws Exception {
        var dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path enterprise = Path.of(System.getProperty("jasper.stagedPlugins")).resolve("dev.jasper.enterprise");
        var selection = PluginSelection.of(dirs, null, "", enterprise, false);
        assertThat(selection.presentation().look()).isEqualTo("dev.jasper.enterprise");
        var service = new ConfigService(home.resolve("config.toml"), false, selection.presentation().defaults());
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown,
                    null, null, selection);
                var first = application[0].windowConfiguration();
                assertThat(first.visibleRows()).isEqualTo(8);
                assertThat(first.toolbarHeight()).isEqualTo(34);
                assertThat(first.lightTheme()).isEqualTo("enterprise");
                assertThat(first.darkTheme()).isEqualTo("enterprise");
                assertThat(application[0].themes().current().chrome().name()).as("the window's theme is the kit's pick").startsWith("Enterprise");
                assertThat(application[0].themes().kit()).isSameAs(selection.presentation().kit());
            });
        } finally {
            if (application[0] != null) { edt(application[0]::quit); assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue(); }
            service.close();
            edt(() -> dev.jasper.app.appearance.ThemeTestSupport.controller(true));
        }
    }
}
```

Replace `jasper-ui/src/test/java/dev/jasper/ui/theme/DerivedRolesTableTest.java` (spec 7.1 freezes the derived roles of
the shipped themes, and the Enterprise theme now ships; its rows go to their own table, since the built-in table is
plan 2's and a theme file outside jasper-ui is read from `jasper.repoRoot` as `KitFixtures.themes()` reads it):

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Spec 7.1: every shipped variant's derived roles, frozen: the built-ins in derived-roles.tsv, the Enterprise plugin's
 * theme in derived-roles-enterprise.tsv; a recipe or shipped theme change edits them in the same commit.
 */
class DerivedRolesTableTest {
    @Test void theBuiltInVariantsDerivedRolesAreFrozen() throws IOException {
        assertThat(table(JasperTheme.builtIns())).isEqualTo(expected("derived-roles.tsv"));
    }

    @Test void theEnterpriseVariantsDerivedRolesAreFrozen() throws IOException {
        Path root = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
        var theme = JasperTheme.parse(Files.readString(root.resolve("plugins/enterprise/src/main/resources/themes/enterprise.json")), "enterprise");
        String actual = table(List.of(theme));
        // The computed table, for Step 3 to freeze and for any later recipe change to compare against.
        Path written = Path.of("build/derived-roles/derived-roles-enterprise.tsv");
        Files.createDirectories(written.getParent());
        Files.writeString(written, "# The Enterprise theme's derived roles (DerivedPalette), frozen: theme id, variant, role, colour.\n" + actual);
        assertThat(actual).isEqualTo(expected("derived-roles-enterprise.tsv"));
    }

    static String table(Iterable<JasperTheme> themes) {
        var actual = new StringBuilder();
        for (var theme : themes) for (boolean dark : new boolean[] {true, false}) if (theme.has(dark)) {
            var derived = DerivedPalette.of(theme.generated(dark));
            for (var role : DerivedColor.values())
                actual.append(theme.id()).append('\t').append(dark ? "dark" : "light").append('\t').append(role.key()).append('\t')
                    .append(ColorMath.hex(derived.color(role))).append('\n');
        }
        return actual.toString();
    }

    static String expected(String resource) throws IOException {
        var expected = new StringBuilder();
        try (var in = DerivedRolesTableTest.class.getResourceAsStream(resource)) {
            if (in == null) return "(" + resource + " is missing)\n";
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
                if (!line.isBlank() && !line.startsWith("#")) expected.append(line).append('\n');
        }
        return expected.toString();
    }
}
```

Update the theme set's expected list in `KitFixturesTest` (spec 16.1.2: "Enterprise Light and Dark"):

```bash
python3 -I - <<'PY'
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java", [
    ('''    /** Spec 16.1.2's theme set: the shipped variants, then the converter goldens that have a UI variant. */''',
     '''    /** Spec 16.1.2's theme set: the shipped variants, the converter goldens that have a UI variant, then Enterprise. */''', 1),
    ('''"Ocean Dark", "Ocean Dawn Light", "Stray Dark");''', '''"Ocean Dark", "Ocean Dawn Light", "Stray Dark",
            "Enterprise Dark", "Enterprise Light");''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.testsupport.KitFixturesTest' --tests 'dev.jasper.ui.theme.DerivedRolesTableTest' :jasper-app:test --tests 'dev.jasper.app.plugins.PresentationConformanceTest' --tests 'dev.jasper.app.plugins.EnterpriseHomeTest' --tests 'dev.jasper.app.presentation.EnterpriseFixtureTest' --tests 'dev.jasper.app.application.EnterpriseKitRenderTest' --tests 'dev.jasper.app.application.EnterpriseFirstWindowTest' --continue`
Expected: `KitFixturesTest.theThemeSetIsTheShippedVariantsThenTheConvertedOnes` FAILS (the actual list ends at "Stray
Dark"), and `DerivedRolesTableTest.theEnterpriseVariantsDerivedRolesAreFrozen` FAILS ("derived-roles-enterprise.tsv is
missing"; run `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.DerivedRolesTableTest'` for it). The five new jasper-app classes already pass: they characterise Task 3's plugin. A failure there names a gap
between the plugin and spec 4.1, 5.1, 7.6 or 14.1: fix the plugin's files (never the expectations, which are the
spec's), rerun, and record the fix in the status banner.

- [ ] **Step 3: Add Enterprise to the theme set**

```bash
python3 -I - <<'PY'
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java", [
    ('''     * The theme set every look is checked over (spec 16.1.2): Slate Cobalt Dark and Light, the four Catppuccin flavours and
     * the converter goldens' UI variants (ocean, ocean-dawn, stray), in that order.
     */''',
     '''     * The theme set every look is checked over (spec 16.1.2): Slate Cobalt Dark and Light, the four Catppuccin flavours,
     * the converter goldens' UI variants (ocean, ocean-dawn, stray) and Enterprise Dark and Light (the Enterprise plugin's
     * theme file), in that order.
     */''', 1),
    ('''            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
        return java.util.List.copyOf(themes);''',
     '''            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
        var enterprise = expected.getParent().getParent().getParent().getParent().resolve("plugins/enterprise/src/main/resources/themes/enterprise.json");
        try {
            var theme = JasperTheme.parse(java.nio.file.Files.readString(enterprise), "enterprise");
            for (boolean dark : new boolean[] {true, false}) themes.add(theme.generated(dark));
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        return java.util.List.copyOf(themes);''', 1)])
edit("jasper-ui/build.gradle.kts", [
    ('''    inputs.dir(rootProject.file("tools/themes/testdata/intellij/loose")).withPathSensitivity(PathSensitivity.RELATIVE)
}''',
     '''    inputs.dir(rootProject.file("tools/themes/testdata/intellij/loose")).withPathSensitivity(PathSensitivity.RELATIVE)
    // KitFixtures.themes() includes the Enterprise plugin's theme (spec 16.1.2).
    inputs.file(rootProject.file("plugins/enterprise/src/main/resources/themes/enterprise.json")).withPathSensitivity(PathSensitivity.RELATIVE)
}''', 1)])
edit("jasper-app/build.gradle.kts", [
    ('''    val configExample = rootProject.layout.projectDirectory.file("config.example.toml")
    inputs.file(configExample).withPathSensitivity(PathSensitivity.RELATIVE)''',
     '''    val configExample = rootProject.layout.projectDirectory.file("config.example.toml")
    inputs.file(configExample).withPathSensitivity(PathSensitivity.RELATIVE)
    // The Enterprise conformance, fixture and render tests read the plugin's sources (spec 16.1.5 and 16.1.6).
    inputs.dir(rootProject.file("plugins/enterprise/src/main/resources")).withPathSensitivity(PathSensitivity.RELATIVE)''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicTagFace.java", [
    (''' * against {@code surface.card}, the ground the faint tint stands on and the role {@code tag.border} is mixed with; the
 * opaque hue surface is only painted at the tile share.''',
     ''' * against {@code surface.card}, the ground the faint tint stands on and the role {@code tag.border} is mixed with; the
 * opaque hue surface is only painted at the tile share. The box also stands the structural guard apart from that tint as
 * painted, moving toward {@code text.primary} where it does not (Enterprise Dark's blue tag, plan 5b).''', 1),
    ('''        label.edge = ThemePainting.painted(chip.context(), edge(node.hue()));''',
     '''        var edge = ThemePainting.painted(chip.context(), edge(node.hue()));
        var card = ThemePainting.color(chip.context(), SemanticColor.SURFACE_CARD);
        var tile = ColorMath.opacity(label.fill == null ? label.getForeground() : label.fill, ThemePainting.tileShare(chip.context()));
        var tint = ColorMath.mix(tile, card, tile.getAlpha() / 255.0);
        label.edge = SurfaceContrast.Guard.STRUCTURAL.apply(edge, tint, ThemePainting.color(chip.context(), SemanticColor.TEXT_PRIMARY));''', 1)])
PY
```

`expected` is `<repo>/tools/themes/testdata/expected`, so four `getParent()` calls give the repository root. The
`ClassicTagFace` edit is Execution notes, ruling 13: with Enterprise Dark in the theme set, plan 4c's
`ClassicIndicatorsTest.aClassicTagsLabelAndBoxStandOutAsPainted` measured the blue tag's box at 1.28:1 on its tint (the
box was guarded against `surface.card` only); a box that already holds keeps its colour, so every other palette, the
default look and Enterprise Light paint as before.

Freeze the Enterprise theme's derived roles: the failing run above wrote the computed table to
`jasper-ui/build/derived-roles/derived-roles-enterprise.tsv`. Check it has 46 rows (Light and Dark, 23 roles each) and
that its values come from spec 7.6's theme (the override keys give their values, Light's `surface.rowHover` `#eef2fa`
among them, while Dark's `surface.rowHover` keeps its recipe, `#2f3642` in the dry run), then copy it:

```bash
python3 -I - <<'PY'
import pathlib
source = pathlib.Path("jasper-ui/build/derived-roles/derived-roles-enterprise.tsv")
rows = [line for line in source.read_text(encoding="utf-8").splitlines() if line and not line.startswith("#")]
if len(rows) != 46 or {row.split("\t")[0] for row in rows} != {"enterprise"}: raise SystemExit(f"unexpected table: {len(rows)} rows")
pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles-enterprise.tsv").write_text(
    source.read_text(encoding="utf-8"), encoding="utf-8")
PY
```

- [ ] **Step 4: The chrome tests and the Run window under the shipped look**

Plan 6a Rulings 26: `EnterpriseLook` takes the shipped kit with Plex registered, its chrome and the Enterprise theme;
`enterprise-fixture.json` goes; the row test asserts IBM Plex again (6a Rulings 20); `EnterpriseMenuRowMockTest` checks
the titles on every platform (6a Rulings 29). Plan 8a R8, R9 and R16: `RunWindowRenderTest.render` draws the shipped kit
and theme (Enterprise Dark for Dark) at the gallery's font, which is now the kit's own face at its 12-point body, and
FONTS joins `CHECKED`; the footer buttons' left edges, which follow the classic label padding where the mocks draw every
button 70 px wide, move to a FOOTER owner that plan 8b checks (Execution notes, ruling 10).

```bash
git rm -q jasper-app/src/test/resources/dev/jasper/app/testsupport/enterprise-fixture.json
python3 -I - <<'PY'
"""Plan 5b Task 4 step 4: plan 6a's chrome tests and plan 8a's Run window render under the shipped look."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
L = "jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java"
text = pathlib.Path(L).read_text(encoding="utf-8")
theme = re.compile(r"    /\*\*(?:(?!\*/).)*\*/\n    public static synchronized JasperTheme theme\(\) \{\n.*?\n        return theme;\n    \}\n", re.S)
if len(theme.findall(text)) != 1: raise SystemExit("EnterpriseLook: theme() moved")
pathlib.Path(L).write_text(theme.sub("    /** The shipped Enterprise theme, Light and Dark. */\n"
    "    public static JasperTheme theme() { return EnterpriseResources.theme(); }\n", text), encoding="utf-8")
edit(L, [
    ('''import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.JasperTheme;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
''', '''import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.theme.JasperTheme;
''', 1),
    (''' * Enterprise as the chrome tests run it before plan 5b ships the plugin: plan 4a's Enterprise fixture kit (the classic
 * base with the kit file's tokens and 12 px body, in the platform faces), spec 5.1's chrome table, and a fixture theme
 * holding spec 7.6's named roles and override keys over Slate Cobalt (the roles the mocks show). Tests restore the
 * default look when they finish. Plan 5b replaces the kit, faces and theme with the shipped ones.''',
     ''' * Enterprise as the chrome tests run it: the shipped kit with its IBM Plex files registered, its chrome table and the
 * shipped Enterprise theme, read from the plugin's sources ({@link EnterpriseResources}). Tests restore the default look
 * when they finish.''', 1),
    ('''    /** Spec 5.1's {@code [chrome]} table, as the Enterprise kit file lists it. */''',
     '''    /** Spec 5.1's {@code [chrome]} table, which the shipped kit file lists ({@code EnterpriseFixtureTest} holds them equal). */''', 1),
    ('''    private static JasperTheme theme;
''', '', 1),
    ('''    /** Plan 4a's Enterprise fixture kit. */
    public static Kit kit() { return KitFixtures.enterprise(); }

    /** The Enterprise kit file's chrome points. */
    public static ChromeSpec chrome() { return CHROME; }''',
     '''    /** The shipped Enterprise kit; its IBM Plex files are registered first. */
    public static Kit kit() { return EnterpriseResources.kit(); }

    /** The shipped kit file's chrome points. */
    public static ChromeSpec chrome() { return EnterpriseResources.chrome(); }''', 1),
    ('''    /** A controller showing the fixture theme's Light or Dark variant under the fixture kit. EDT. */''',
     '''    /** A controller showing the Enterprise theme's Light or Dark variant under the shipped kit. EDT. */''', 1),
    ('''"the Enterprise fixture must install"''', '''"the Enterprise look must install"''', 1)])
W = "jasper-app/src/test/java/dev/jasper/app/workspace/"
edit(W + "WindowChromeMenusTest.java", [
    ('''                assertThat(file.getFont().getSize()).as("the Enterprise kit's 12 px body").isEqualTo(12);''',
     '''                assertThat(file.getFont().getSize()).as("the Enterprise kit's 12 px body").isEqualTo(12);
                assertThat(file.getFont().getFontName(java.util.Locale.ROOT)).as("plan 6a Rulings 20: the kit's own face").isEqualTo("IBM Plex Sans");''', 1)])
edit(W + "EnterpriseMenuRowMockTest.java", [
    ('''import static org.junit.jupiter.api.Assumptions.assumeTrue;
''', '', 1),
    ('''import com.formdev.flatlaf.util.SystemInfo;
''', '', 1),
    (''' * Spec 16.1.4 against the mocks: the Enterprise menu row, rendered at 2× under the Enterprise fixture kit and theme''',
     ''' * Spec 16.1.4 against the mocks: the Enterprise menu row, rendered at 2× under the shipped kit, IBM Plex and the theme''', 1),
    ('''        // Plan 6a Rulings 29: the fixture kit draws the platform face, and the titles' metrics are the macOS face's.
        assumeTrue(SystemInfo.isMacOS, "the mocks' title metrics hold for the macOS face until plan 5b registers IBM Plex");
''', '', 1)])
R = "jasper-app/src/test/java/dev/jasper/app/palette/RunWindowRenderTest.java"
edit(R, [
    ('''import dev.jasper.ui.testsupport.KitFixtures;
''', '', 1),
    (''' * Plan 8a Rulings 12: the Run window under the Enterprise fixture kit (the classic catalog with the shipped tokens and
 * its 12-point body) and the Enterprise Light fixture theme against the two 06 Run Dialog mocks, at 2x.''',
     ''' * Plan 8a Rulings 12: the Run window under the shipped Enterprise kit (its IBM Plex faces and 12-point body) and the
 * Enterprise theme against the two 06 Run Dialog mocks, at 2x.''', 1),
    (''' * binding when its action returns). Plan 4b's classic checkbox and buttons are held to the mock; what plan 5b's IBM Plex
 * draws is measured and printed until that plan is in this branch ({@link #CHECKED}, plan 8a R9).''',
     ''' * binding when its action returns). Plan 4b's classic checkbox and buttons and plan 5b's IBM Plex text are held to the
 * mock; the footer buttons' left edges are measured and printed until plan 8b sizes them ({@link #CHECKED}, plan 8a R9).''', 1),
    ('''    /** Who draws a measured part: this plan's layout and surfaces, plan 4b's classic checkbox and buttons, or plan 5b's IBM Plex text. */
    enum Owner { RUN_WINDOW, FACES, FONTS }
    /** The owners whose parts are held to the mock now: FACES since the merge with plan 4b; FONTS joins with plan 5b. */
    static final java.util.Set<Owner> CHECKED = java.util.EnumSet.of(Owner.RUN_WINDOW, Owner.FACES);''',
     '''    /**
     * Who draws a measured part: this plan's layout and surfaces, plan 4b's classic checkbox and buttons, plan 5b's IBM
     * Plex text, or plan 8b's footer buttons (the mocks draw every one 70 px wide).
     */
    enum Owner { RUN_WINDOW, FACES, FONTS, FOOTER }
    /** The owners whose parts are held to the mock now: FACES since the merge with plan 4b, FONTS since plan 5b; FOOTER joins with plan 8b. */
    static final java.util.Set<Owner> CHECKED = java.util.EnumSet.of(Owner.RUN_WINDOW, Owner.FACES, Owner.FONTS);''', 1),
    ('''"primary button left", 300, 796, 1, 0, c -> derived(c, DerivedColor.SURFACE_FOOTER), true, 1, Owner.FONTS),''',
     '''"primary button left", 300, 796, 1, 0, c -> derived(c, DerivedColor.SURFACE_FOOTER), true, 1, Owner.FOOTER),''', 1),
    ('''"cancel button left", 596, 796, 1, 0, c -> derived(c, DerivedColor.SURFACE_FOOTER), true, 1, Owner.FONTS),''',
     '''"cancel button left", 596, 796, 1, 0, c -> derived(c, DerivedColor.SURFACE_FOOTER), true, 1, Owner.FOOTER),''', 1),
    ('''        var kitValue = KitFixtures.enterprise();
        var theme = dark ? KitFixtures.dark() : KitFixtures.enterpriseLight();''',
     '''        var kitValue = dev.jasper.app.testsupport.EnterpriseResources.kit();
        var theme = dev.jasper.app.testsupport.EnterpriseResources.theme().generated(dark);''', 1),
    ('''                // The kit's 12-point body as the application's font setting (5a's kit defaults); the gallery installs 13.
                javax.swing.UIManager.put("defaultFont", new java.awt.Font(KitFixtures.platformFamily(), java.awt.Font.PLAIN, 12));
''', '', 1),
    ('''            assertThat(ColorMath.contrast(palette.color(SemanticColor.TEXT_ONACCENT), palette.color(SemanticColor.ACCENT))).as("the selected row").isGreaterThanOrEqualTo(4.5);''',
     '''            // Spec 7.6 gives Enterprise Dark text.onAccent #FFFFFF on accent #3D74D6, 4.499:1; the row paints its guarded pair.
            var selected = dev.jasper.ui.theme.SurfaceContrast.painted(c.theme(), dev.jasper.ui.swing.RunWindowPresentation.SELECTED_TITLE);
            System.out.printf("run-window dark: selected row text %s on %s, %.3f:1 (spec 7.6's #FFFFFF is %.3f:1)%n", ColorMath.hex(selected),
                ColorMath.hex(palette.color(SemanticColor.ACCENT)), ColorMath.contrast(selected, palette.color(SemanticColor.ACCENT)),
                ColorMath.contrast(palette.color(SemanticColor.TEXT_ONACCENT), palette.color(SemanticColor.ACCENT)));
            assertThat(ColorMath.contrast(selected, palette.color(SemanticColor.ACCENT))).as("the selected row, as painted").isGreaterThanOrEqualTo(4.5);''', 1)])
PY
grep -c 'KitFixtures' jasper-app/src/test/java/dev/jasper/app/palette/RunWindowRenderTest.java
```

The last command prints 0. `KitGallery.installed` puts the kit's own face at its body size since Task 1, which is the
12-point font plan 8a's `render` put by hand.

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowChromeMenusTest' --tests 'dev.jasper.app.workspace.ChromeRenderTest' --tests 'dev.jasper.app.workspace.EnterpriseMenuRowMockTest' --tests 'dev.jasper.app.palette.RunWindowRenderTest' --tests 'dev.jasper.app.presentation.EnterpriseFixtureTest' --continue`
Expected: PASS. Record from the output, for the STATUS entry, the menu-row edge agreements, the titles' largest drift
(6a Rulings 26: a title past 4 px is retuned by 6a Rulings 18's measurement, side and item padding in Task 3's roles and
tables) and the Run window's edge agreements and the two FOOTER lines. A Run window probe or fill outside its tolerance
under Plex is a layout or face defect (8a R9): fix it or stop and report; never widen a tolerance.

If plan 6b is on the branch (Task 3 step 5 ran its script), also do the rest of 6b's Execution notes R47 here, before
the run above: each `MockProfile.fonts(what, actual, expected, tolerance)` call 6b left in its mock tests (its R36: "New
Session"'s ink width, the search field's left, "Settings…"'s start, the status rules before UTF-8 and the warning, the
run item's width, the Vault titles' widths) becomes `assertThat(actual).as(what).isCloseTo(expected, within(tolerance))`
and `MockProfile.fonts` goes once nothing calls it; 6b's R39 swatch is re-measured under the shipped theme and its value
updated where 6b's test pins it; add the tests 6b names in R47 to the run above. Record each number in the status
banner. Without 6b on the branch, these are 6b's to do at its merge.

- [ ] **Step 5: Add the visual pairs**

Append the crops (Rulings 9; `crop` is `x,y,w,h` in the mock's 2x pixels) to `tools/visual/pairs.json` as text before
its closing `]`, in the file's own two-space style (a `json.dumps` of the whole file would rewrite earlier entries):

```bash
python3 -I - <<'PY'
import json, pathlib
path = pathlib.Path("tools/visual/pairs.json")
text = path.read_text(encoding="utf-8")
pairs = json.loads(text)
before = len(pairs)
if any(pair["name"].startswith("enterprise-") for pair in pairs): raise SystemExit("pairs.json already has Enterprise pairs")
if not text.endswith("\n  }\n]\n"): raise SystemExit("pairs.json does not end with an object and ]")
mocks = {"light": "docs/design/enterprise/reference/01 Workstation/Workstation - Connected (plugins in use).png",
         "dark": "docs/design/enterprise/reference/08 Dark Mode/Workstation Dark - Connected (plugins in use).png"}
crops = {"sessions": [2134, 184, 697, 516], "tunnels": [2134, 1014, 697, 268], "sftp": [49, 184, 598, 759], "fileservers": [49, 945, 598, 150]}
added = [{"name": f"enterprise-{variant}-{panel}", "mock": mock, "crop": crop,
          "render": f"jasper-app/build/enterprise-kit/{variant}/{panel}@2x.png"} for variant, mock in mocks.items() for panel, crop in crops.items()]
block = ",\n".join("  " + json.dumps(pair, indent=2, ensure_ascii=False).replace("\n", "\n  ") for pair in added)
path.write_text(text[:-len("\n]\n")] + ",\n" + block + "\n]\n", encoding="utf-8")
if len(json.loads(path.read_text(encoding="utf-8"))) != before + 8: raise SystemExit("expected eight more pairs")
print(before, "->", before + 8, "pairs")
PY
git diff --stat tools/visual/pairs.json
```

Expected: `47 -> 55 pairs` (34 at `222969926`, plus 8a's four `run-window-*`, 6a's two `chrome-*` and 4c's seven
`classic-*`; more if a later plan added some) and only additions in the diff. Step 6 adds each pair's `min_agreement`
and `colours` from the measured renders (Execution notes, ruling 11). In `tools/visual/README.md`, name the renders:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("tools/visual/README.md"); text = path.read_text(encoding="utf-8")
for old, new in [("`:jasper-app:settings9bPreview`, the staged render evidence tests and the workspace preview).",
                  "`:jasper-app:settings9bPreview`, the staged render evidence tests, `EnterpriseKitRenderTest` and the\n  workspace preview)."),
                 ("(the classic look's `classic-*`, the `run-window-*` and the `chrome-*` pairs)",
                  "(the classic look's `classic-*`, the `run-window-*`, the `chrome-*` and the `enterprise-*` pairs)"),
                 ("`RunWindowRenderTest` writes the `run-window-*` renders under `jasper-app/build/run-window/`.",
                  "`RunWindowRenderTest` writes the `run-window-*` renders under `jasper-app/build/run-window/`, and\n  `EnterpriseKitRenderTest` the `enterprise-*` ones under `jasper-app/build/enterprise-kit/`.")]:
    if text.count(old) != 1: raise SystemExit(f"README anchor not found once: {old}")
    text = text.replace(old, new)
path.write_text(text, encoding="utf-8")
PY
```

- [ ] **Step 6: Run the theme-set suites and the new tests**

Run: `./gradlew :jasper-ui:test :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.application.EnterpriseKitRenderTest' --tests 'dev.jasper.app.application.EnterpriseFirstWindowTest' --tests 'dev.jasper.app.appearance.*' --tests 'dev.jasper.app.architecture.*' --continue`
Expected: PASS: `PresentationConformanceTest` (5), `EnterpriseFixtureTest` (1), `EnterpriseKitRenderTest` (1),
`EnterpriseHomeTest` (1), `EnterpriseFirstWindowTest` (1), `DerivedRolesTableTest` (2), and over the theme set with Enterprise added `KitFixturesTest`, `KitMatrixTest`,
`DerivedColorTest`, `DerivedPaletteTest`, `KnownRepairsTest` and `PaintedContrastTest`. A recipe, guard or painted-pair
failure over Enterprise Light or Dark is a finding about the derived roles or guards, not about the theme (its values are
spec 7.6's): stop and report it. Then check the images:

```bash
ls jasper-app/build/enterprise-kit/light | wc -l; ls jasper-app/build/enterprise-kit/dark | wc -l
python3 -I tools/visual/compare_all.py jasper-app/build/visual-enterprise enterprise-
```

Expected: the two counts are equal (one PNG per gallery fixture plus the four `@2x` panels); `compare_all.py` writes the
eight comparisons (Pillow and NumPy are installed on the dev Mac). Mock matching is required (Execution notes, ruling
11): give each `enterprise-*` pair in `pairs.json` a `min_agreement` of its measured agreement less 0.05, rounded down
to two decimals, and `colours` listing the fact-12 colours of its variant that its mock crop shows (Light `#FFFFFF`,
`#2F65CA`, `#ECEDEF`, `#D9DFEA`, `#F0F1F3`, `#E4E6E9`; Dark `#2E3135`, `#3D74D6`, `#3C3F44`, `#3D4658`, `#393C41`,
`#383B40`), edited in place in the file's style, then run:

```bash
python3 -I tools/visual/check_pairs.py jasper-app/build/visual-enterprise enterprise-
```

Expected: eight PASS lines. A colour the render lacks is a theme or face defect: fix it, or stop and report; drop a
colour only when the mock crop lacks it. Look at the `-side.png` files and note in the status banner what differs
because the classic compositions are plans 6, 7 and 10's.

- [ ] **Step 7: Commit**

Add the ledger rows of `PresentationConformanceTest.java` (owner `app.plugins`; it imports `java.awt` and
`dev.jasper.ui` and uses `new ArrayList`), `EnterpriseHomeTest.java` (`app.plugins`; it names `dev.jasper.app.appearance`),
`EnterpriseFixtureTest.java` (`app.presentation`), `EnterpriseKitRenderTest.java` and `EnterpriseFirstWindowTest.java`
(`app.application`; the dry run's coverage test names both) and
`EnterpriseResources.java` (`app.testsupport`; it imports `dev.jasper.ui`), each
`<path>,<owner>,<class>,nonvisual-helper,none,none,<its own path>,Test/fixture ownership; New in Enterprise plan 5b Task 4`
(plan 5a's form: app owners are written `app.<package>`), and run
`./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'` (PASS) before committing.

```bash
git add jasper-app/src/test jasper-ui/src jasper-ui/build.gradle.kts jasper-app/build.gradle.kts tools/visual docs/design/theme-engine/migration.csv
git status --short   # nothing left unstaged; enterprise-fixture.json shows as deleted (D)
git commit -F - <<'EOF'
Enterprise: conformance, the fixture, the shipped-kit renders

PresentationConformanceTest checks the shipped descriptor, every kit key,
the IBM Plex files (their sums, names and families), the theme and the
notice, from the sources and from the staged jar. EnterpriseFixtureTest
compares the parsed kit with jasper-ui's Enterprise fixture, and
EnterpriseKitRenderTest renders the gallery under the real kit and fonts
in Enterprise Light and Dark into build/enterprise-kit, with 2x panels
paired with the Workstation and Dark Mode mocks in tools/visual/pairs.json.
KitFixtures.themes() gains Enterprise Light and Dark, whose derived roles
are frozen in derived-roles-enterprise.tsv (the classic tag's box now
stands apart from its tint too), and a fresh home with Enterprise turned
on starts, and opens its first window, with its kit's defaults. Plan 6a's
chrome tests and plan 8a's Run window render draw the shipped kit, Plex
and theme; the fixture theme is gone and the FONTS probes are checked.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: Documentation, STATUS and the whole build

The smallest accurate edits to the documents spec 17 names for this plan's part (the `themes` key and consuming
`Workstation`, plugin themes and their reserved ids, what `"system"` and an unset `ui.font.size` mean under a look with its
own font, the notices' `files`, and AGENTS.md's module list), the pixel proof, the full build with its counts, a new
`docs/STATUS.md` entry at the top and this plan's status banner.

**Files:**
- Modify: `docs/plugin-authoring.md` (the descriptor example and the presentation paragraph plan 5a added),
  `docs/themes.md:8,48-49,73,75,157`, `docs/configuration.md:851`, `docs/app-maintenance.md:345`,
  `AGENTS.md:24`, `docs/STATUS.md:1`
- Modify: this plan's status banner (first lines of this file)

**Interfaces:**
- Consumes: everything above.
- Produces: nothing new.

- [ ] **Step 1: Edit the documents**

```bash
python3 -I - <<'PY'
"""Plan 5b Task 5 step 1: the smallest accurate documentation edits."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/plugin-authoring.md", [
    ('''presentation = "presentation/look.kit.toml"  # optional, SDK 0.18.0: a kit file in your jars; needs presentation.provide
''', '''presentation = "presentation/look.kit.toml"  # optional, SDK 0.18.0: a kit file in your jars; needs presentation.provide
themes = ["themes/look.json"]      # optional, SDK 0.18.0: Jasper theme files in your jars, offered in Settings › Appearance
''', 1),
    ('''plugin off until the user turns it on in Settings › Plugins; development and installed plugins ignore it.
''', '''plugin off until the user turns it on in Settings › Plugins; development and installed plugins ignore it.

`themes` lists [Jasper theme files](themes.md#the-jasper-theme-format) in your jars; a file's name is its theme's id
(`themes/look.json` is `look`). While the plugin is enabled, its themes are listed in Settings › Appearance after the
built-in ones, captioned "From the <name> plugin", and cannot be removed or replaced there; while it is off, a setting
that names one uses Slate Cobalt without a warning and keeps the name. A built-in id is ignored with a configuration
warning, and when two plugins ship the same id the development copy wins, then the bundled one, then the installed one,
then the lowest plugin id. A kit file's `[defaults]` may name its own plugin's themes.

The bundled Enterprise plugin (`plugins/enterprise`, off by default) is a worked presentation provider: its look is the
kit file, IBM Plex fonts and theme its descriptor names, and its code only publishes
`dev.jasper.enterprise.api.Workstation` while the Enterprise look is active. A plugin that composes a classic layout
declares `requires = [{ id = "dev.jasper.enterprise", version = ">=0.1", optional = true }]`, compiles against that
package and checks `context.services().find(Workstation.class)` in `start`.
''', 1)])
edit("docs/themes.md", [
    ('''its own terminal scheme. Other themes are Jasper theme files in the themes folder: a UI theme recolours the whole application and brings its own terminal and
editor schemes; a terminal-only theme adds terminal schemes.''',
     '''its own terminal scheme. Plugins can ship themes: the bundled Enterprise plugin brings **Enterprise** (Light and Dark)
while it is turned on. Other themes are Jasper theme files in the themes folder: a UI theme recolours the whole application and brings its own terminal and
editor schemes; a terminal-only theme adds terminal schemes.''', 1),
    ('''such as `slate-cobalt.json` or `catppuccin-mocha.json`, is ignored. Dropdowns list the built-in themes first, Slate Cobalt
then the Catppuccin flavours, then the installed themes by name; two themes with the same name show their ids.''',
     '''such as `slate-cobalt.json` or `catppuccin-mocha.json`, is ignored, and so is one named after a theme an installed
plugin ships, such as `enterprise.json`, even while that plugin is off. Dropdowns list the built-in themes first, Slate
Cobalt then the Catppuccin flavours, then the plugins' themes by name, captioned with their plugin, then the installed
themes by name; two themes with the same name show their ids. A setting that names the theme of a plugin that is off
uses Slate Cobalt without a warning and keeps the name, so turning the plugin on again restores it.''', 1),
    ('''imported theme already has asks before replacing it; a built-in theme cannot be replaced.''',
     '''imported theme already has asks before replacing it; a built-in theme or a plugin's theme cannot be replaced.''', 1),
    ('''  unavailable for a built-in theme and Same as UI theme.''', '''  unavailable for a built-in theme, a plugin's theme and Same as UI theme.''', 1),
    ('''production code or text resources of jasper-ui, the app and the plugins outside the built-in theme files.''',
     '''production code or text resources of jasper-ui, the app and the plugins outside the built-in theme files and the theme
files a plugin's descriptor lists.''', 1)])
edit("docs/configuration.md", [
    ('''`family = "system"` (the default) selects the current platform UI font. You can''',
     '''`family = "system"` (the default) selects the look's UI font: the platform font under the default look, IBM Plex
Sans under Enterprise; a named family overrides it. An unset `size` uses the look's size (the platform size, or 12 under
Enterprise); a set size scales every role. You can''', 1)])
edit("docs/app-maintenance.md", [
    ('''entry that no longer ships. Read the new version's license''',
     '''entry that no longer ships. A font file a bundled plugin ships is listed in its component's `files` as
`<plugin id>/<path in its jar>`, and the check names any that is not. Read the new version's license''', 1)])
edit("AGENTS.md", [
    ('''it exports `dev.jasper.editor.api`, and other plugins reach it through that package only.''',
     '''it exports `dev.jasper.editor.api`, and other plugins reach it through that package only. Enterprise is bundled too (`plugins/enterprise`, off by default): its look is descriptor data (its kit file, the IBM Plex fonts and the Enterprise theme), and its code only publishes `dev.jasper.enterprise.api.Workstation`, which other plugins reach through that package only.''', 1)])
PY
git diff --stat docs AGENTS.md
```

Expected: six files changed, insertions only apart from the replaced sentences.

- [ ] **Step 2: Prove the default look unchanged**

Enterprise is off by default, and Task 1 proved the gallery byte-identical after the only jasper-ui production change. Run
the gallery again on this branch and on the base in a scratch worktree, and compare:

```bash
base=$(git merge-base HEAD main)
scratch=$(mktemp -d)/base
git worktree add --detach "$scratch" "$base"
for theme in dark light; do
  ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=$theme
  (cd "$scratch" && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=$theme)
  for file in jasper-ui/build/gallery/$theme-1x/*.png; do
    cmp -s "$file" "$scratch/$file" || echo "DIFFERS: $file"
  done
  ls jasper-ui/build/gallery/$theme-1x/*.png | wc -l
done
git worktree remove --force "$scratch"
```

Expected: no `DIFFERS` line, and the same PNG count for both themes as the base writes. (The base's gallery task runs in
the scratch worktree with its own build directory; nothing in this worktree is touched.)

- [ ] **Step 3: Run the whole build**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Then count the results:

```bash
python3 -I - <<'PY'
import pathlib, xml.etree.ElementTree as ET
tests = failures = errors = skipped = 0
for report in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    root = ET.parse(report).getroot()
    tests += int(root.get("tests")); failures += int(root.get("failures")); errors += int(root.get("errors")); skipped += int(root.get("skipped"))
print(f"{tests:,} tests, {failures} failures, {errors} errors, {skipped} skipped")
PY
```

Expected: 0 failures and 0 errors; note the four numbers for the entry below.

- [ ] **Step 4: Write the STATUS entry and the banner**

Insert at the top of `docs/STATUS.md`, right after the `# Jasper — Status and Handoff` heading and its blank line, one
paragraph (fill `<range>` with the first and last commit of this plan, `<counts>` with Step 3's line, `<deviations>`
with every deviation from this plan's text, or "none beyond the plan's Rulings", and `<mock notes>` with Task 4 step 6's
observation):

```markdown
**Enterprise presentation, plan 5b: the Enterprise plugin — 2026-10-17** (`claude/enterprise-provider`, worktree
`.claude/worktrees/enterprise-5`, `<range>`, then this documentation commit;
[plan](superpowers/plans/2026-10-16-enterprise-5b-enterprise.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). Turning Enterprise on gives classic
controls, metrics and fonts. `FaceSet` names UI and data faces by registered full name and `Typography` draws a role in
its face when it is registered, else the platform's; the application registers a look's font files after the bundled
fonts, refusing a file of another family, and a look missing files posts one Warning. Plugin themes are a library tier
between the built-ins and the folder (`PluginThemes`): a discovered plugin's ids are reserved, a disabled plugin's are
dormant (a pick falls back silently and is kept), and Settings cannot remove or replace them. `plugins/enterprise` is
bundled and off by default: its descriptor (spec 4.1 plus an icon), the kit file of 5.1 without `[branding]`, IBM Plex
Sans and Mono 1.1.0 (IBM's release files, checked by SHA-256, with their OFL notice; `verifyThirdPartyNotices` now
requires every bundled font file in a component's `files`), the Enterprise theme in Light and Dark (7.6) and
`EnterprisePlugin`, which only publishes `dev.jasper.enterprise.api.Workstation`. `PresentationConformanceTest`,
`EnterpriseFixtureTest`, `EnterpriseKitRenderTest` (images in `jasper-app/build/enterprise-kit/`, with
`tools/visual/pairs.json` crops of the Workstation and Dark Mode mocks that `check_pairs.py` holds to measured floors:
<mock notes>) and `EnterpriseHomeTest` prove it; `KitFixtures.themes()` gains Enterprise Light and Dark. Plan 6a's chrome
tests (`EnterpriseLook`, whose fixture theme is gone) and plan 8a's Run window render now draw the shipped kit, Plex and
theme, and the Run window's FONTS probes are checked (<chrome and Run window numbers>). <6b: "Plan 6b's chrome targets
and `border.divider` are in the theme" or "plan 6b's chrome targets are left to its merge">. Found: spec 7.6 gives
Enterprise Dark `text.onAccent` #FFFFFF on `accent` #3D74D6, 4.499:1, so the TEXT guard paints selected and accent text
black there where the Dark Mode mock draws white; awaiting the user's ruling (one red step, #3C74D6, gives 4.505:1).
The default look is unchanged (gallery byte-identical). Rulings
(plan, "Rulings"): (1) faces by full name, degrading per face; (2) the look's UI font reaches FlatLaf unless
`ui.font` is set; (3) one Warning for missing files; (4) the theme tier's order and messages; (5) plugin themes are fixed
in Settings and captioned; (6) the descriptor has an icon; (7) the theme's unnamed roles; (8) the apart direction is plan
7's; (9) the mock comparison pairs the four gallery panels; (10) the family is name ID 16; (11) the icons and splash
art of 14.1 are plan 11's; Execution notes (2026-10-17) rulings 1 to 17. Deviations from the plan's text:
<deviations>. `./gradlew check --continue`: **<counts>**.
GUI checks for the user: turn Enterprise on in Settings › Plugins, restart, and compare Enterprise Light and Dark with
the Workstation and Dark Mode mocks (controls, metrics and fonts; the window chrome is plans 6 to 9's); turn it off and
check that Slate Cobalt returns without a warning; with IBM Plex installed on macOS, check the look's Warning names the
hidden files (Execution notes, ruling 3). Not merged, not pushed.
```

Replace this plan's first heading line with the same heading followed by a status banner:

```markdown
> **Status (2026-10-17): executed on `claude/enterprise-provider` (`<range>`).** Deviations from this plan's text: <deviations>.
```

- [ ] **Step 5: Commit**

```bash
git add docs AGENTS.md
git commit -F - <<'EOF'
Enterprise plugin: documentation and status

The plugin guide names the themes key, the plugin theme rules and how a
plugin consumes Workstation; the themes guide covers plugin themes and
their reserved ids; the configuration guide says what the default UI font
means under a look with its own font; the maintenance guide names the
notices' files key. STATUS records plan 5b with its rulings and the full
build's counts.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Execution notes (2026-10-17)

**Refresh.** Every anchor, quoted text, path, signature and line number this plan relies on was re-checked against the
worktree at `e502e102b` (`main`: plans 3a to 3e, 4a to 4c, 5a, 6a and 8a; not 6b, which runs in parallel, nor 7 to 11).
Method, without Gradle: a dry run in a scratch copy of the tree (`git archive HEAD`) applied every step in order (each
edit script with its stated counts, each created file, Task 3's conditional both ways, the 14 ledger rows), compiled
every module's main, test-fixture and test sources and the new plugin with `javac` (JBR 25, the Gradle cache's jars),
and ran the tests through the JUnit 6.1.3 launcher headless, with a staged `dev.jasper.enterprise` jar built from the
plugin's classes and resources. IBM Plex was not downloaded (the pre-flight fetches nothing; Task 3 step 1 does, and
facts 11 and 15 stand from 2026-10-16), so the dry run used five JBR Droid faces under the Plex file names. Results:
jasper-ui 827 tests, all passing but `GalleryCoverageTest.shippedJarExcludesGalleryAndTestSupport` (it needs Gradle's
jar; it fails the same on the unchanged tree); jasper-app's `appearance`, `settingsui`, `application`, `plugins`,
`architecture`, `presentation`, `workspace`, `palette`, `pluginmanager`, `config` and `about` suites (2,112 tests) fail
exactly the unchanged tree's 177 environment failures (Gradle's staged plugins, jars, build info and docs inputs) plus
seven that assert IBM Plex itself (the conformance font case, the face names in `EnterpriseFixtureTest`,
`EnterpriseHomeTest` and `EnterpriseKitRenderTest`, the row test's Plex name, and the Run window's FONTS probes); with
the placeholder names put in, `EnterpriseHomeTest` and `EnterpriseFixtureTest` pass whole. `ThemeMigrationCoverageTest`
passes with the plan's rows; `javadoc` with Task 1 to 4's doclint options exits 0 over jasper-ui and jasper-app; the
source-hygiene scan finds nothing new. Not checked here (Gradle only): the Kotlin of `verifyThirdPartyNotices`,
`verifyPluginArchitecture`, `stagePlugins`, `pluginZips` and the headless gallery's byte comparison.

Changes made to this plan's text: the Workspace paragraph and "Before you start" (6a, 8a and 6b checks); facts 2 to 10,
13 and 16; Rulings 7 and 9; the Files lines of Tasks 1 to 5; Task 1 (the `Typography` anchor with `RUN_DETAIL`, the
`KitGallery` edits on 4c's `bodySize`, `LookFontsTest` rewritten, `LookFonts`' installed-font check, `LookNoticeTest`'s
loadable look, the `KitParserTest` anchor, the counts, the gallery base); Task 2 (`"\uFEFF"`); Task 3 (the
`TerminalColors` note, the Kotlin names, the 6b conditional in step 5); Task 4 (`EnterpriseResources.kit()` and
`chrome()`, the chrome-equality assertion, the `ClassicTagFace` edit, the corrected derived-table example, a new step 4
for plans 6a and 8a and 6b's R47, the pairs script, floors and README lines, steps renumbered 5 to 7, the ledger rows,
the commit); Task 5 (dates, the STATUS template).

### Pre-flight scan

Pairs of tasks that share a file or an interface:

| Tasks | Shared file or interface | Agreement | Ruling |
|---|---|---|---|
| 1 → 4 | `FaceSet.of`, `uiFace`, `namesUiFaces`; `LookFonts.register`; `KitGallery.installed`'s `font(kit, …)`; `ThemeController`'s look font | Consumers use the produced shapes; compiles and runs in order | 3, 6, 9, 10 |
| 1 → 2 | `JasperApplication.java`: `BundledFonts` and the notice (1), the library and the import folder (2) | Disjoint spans; each anchor holds after the other | 15 |
| 1 → 2 | `Presentation` gains `droppedDefaults` (1); `PluginSelection.blamed()` keeps calling `presentation.fallback` (2) | Agree | none |
| 1 to 4 | `docs/design/theme-engine/migration.csv`, append-only | 14 rows of eight fields; the coverage test names exactly these | 14 |
| 2 → 3 | `ColourLiteralScanTest.PLUGIN_THEMES` and its loader test (2); Task 3 adds `enterprise.json` and asserts it is listed | Agree | none |
| 2 → 4 | `PluginSelection.themes()`, `ThemeLibrary.of`, `provider`, `dormant` in `EnterpriseHomeTest` | Agree | none |
| 3 → 4 | The plugin's descriptor, kit, fonts and theme read by `EnterpriseResources`; `enterprise.json` (and 6b's values) frozen in `derived-roles-enterprise.tsv` | Agree in order: the table is generated after Task 3 | 8 |
| 3 → 5 | The plugin and `Workstation` as the documents describe them | Agree | none |
| 4 → 5 | The STATUS template's renders, mock notes (step 6) and chrome and Run window numbers (step 4) | Agree after the renumbering | 14 |
| 1 ↔ 8a (merged) | `Typography.font`'s data roles include `RUN_DETAIL` | The anchor had moved | 1 |
| 1, 4 ↔ 4c (merged) | `KitGallery`'s font at the gallery's scale; `pairs.json` (47 entries, `check_pairs.py`); `ClassicTagFace` under the theme set | Anchors moved; `json.dumps` rewrote the file; the tag's box failed on Enterprise Dark | 6, 7, 11, 13 |
| 4 ↔ 6a (merged) | `EnterpriseLook`, `enterprise-fixture.json`, `WindowChromeMenusTest`, `EnterpriseMenuRowMockTest`, `ChromeRenderTest` | 6a Rulings 26 named the switch; the plan had no step for it | 9 |
| 4 ↔ 8a (merged), 8b (later) | `RunWindowRenderTest`: `render`, `CHECKED`, the footer probes, the Dark test | 8a R8, R9, R16 named the switch; the footer's 70 px is 8b's; Dark's selected row fails raw | 10, 12 |
| 3, 4 ↔ 6b (parallel) | `enterprise.json`'s chrome targets, `EnterpriseThemeTest`, `EnterpriseLook`, `MockProfile.fonts`, 6b's `EnterpriseChromeTargetsTest` | Whichever lands second applies 6b's R47 | 8, 15 |
| 5 ↔ 6b, 7 to 11 (parallel) | `docs/themes.md`, `AGENTS.md`, `docs/STATUS.md`, `migration.csv`, `pairs.json` | Different sentences and appended rows | 15 |

Each task against itself:

| Task | Agreement of its own text | Ruling |
|---|---|---|
| 1 | Did not compile (a compound `var`); `LookFontsTest` failed on the dev Mac (Cousine is installed there); `LookNoticeTest` logged a load failure; the `Typography` and `KitGallery` anchors had moved; Step 5's count; 3 + 2 + 1 cases and `KitParserTest` pass, jasper-ui unchanged otherwise | 1, 2, 3, 4, 6, 14 |
| 2 | A raw U+FEFF in `PluginThemes`; otherwise agree; 5 + 1 cases and `ColourLiteralScanTest` pass | 5 |
| 3 | The "check two names" note is settled; the Kotlin shadows a name; 3 + 3 cases pass (fact 15's `javap` claim holds); the 6b case was missing | 5, 8, 17 |
| 4 | Lacked the 6a and 8a switches, mock floors and a working pairs script; the derived-table example named an override as a recipe; a ledger row was conditional; Enterprise Dark exposed two guard findings | 7, 9, 10, 11, 12, 13, 14 |
| 5 | Every document anchor holds; dates and the STATUS template updated | 14 |

### Rulings

1. **Anchors and line numbers re-anchored.** `Typography.font`'s data roles now include plan 8a's `RUN_DETAIL` (the
   script's old and new text carry it, so the Run window's detail draws in the kit's data face too); facts and Files
   lines give `e502e102b`'s numbers. Why: 8a and 4c changed the text since `222969926`. Cost if wrong: none; the scripts
   anchor on text.
2. **Compile fix.** `LookFontsTest`'s `var regular = …, bold = …` becomes `FontFile regular = …, bold = …`. Why: javac
   refuses `var` in a compound declaration. Cost if wrong: none.
3. **An installed copy of a font hides the file's full name; it is named in the look's notice.** On macOS JBR finds an
   installed font by family or PostScript name, never by full name, and `registerFont` returns false for a family that
   is installed, so a face named by full name resolves to Dialog (checked with the bundled Cousine, which the dev Mac
   has installed: `new Font("Cousine Nerd Font Mono Regular")` is Dialog). `LookFonts` now checks after registering that
   the full name resolves and otherwise adds "<file>: an installed font named <family> hides it; that text uses the
   platform font" (spec 5.9: a font that does not register degrades its face, with a notice); `LookFontsTest` registers
   Cousine renamed in its name table to "Jasper Look Face Tests" (same length), which no machine installs, and gains a
   case that expects the line exactly where the name is hidden. Why: the test as written failed on the dev Mac, and a
   user with IBM Plex installed would get the platform face silently. Cost if wrong: on such a Mac Enterprise draws the
   platform face with a Warning; resolving by PostScript name (name ID 6) is the follow-up, not this plan's.
4. **`LookNoticeTest` starts a loadable look** (`LookFixtures.loadable`). Why: with `LookFixtures.plugin` the provider's
   start failed (`ClassNotFoundException`), so the look's notice shared the run with an unrelated load failure. Cost if
   wrong: none.
5. **Source hygiene.** `PluginThemes.text` strips `"\uFEFF"`, as `ThemeLibrary.readText` does (the plan had the raw character);
   the font-files check names its folder `pluginResources` (it shadowed the outer `resources`) and types its empty list.
   Cost if wrong: none.
6. **The gallery's font follows plan 4c.** `KitGallery.font(kit, large)` returns the kit's regular UI face, else the
   platform family, at `bodySize(points)`, which is exactly the font 4c's gallery sets under the default kit, so the
   default gallery stays byte-identical; `installed`'s javadoc says so. Cost if wrong: none.
7. **`pairs.json` is appended as text.** The file does not round-trip through `json.dumps` (earlier entries would be
   rewritten), so the script inserts the eight objects before the closing `]` in its two-space style: 47 to 55 pairs.
   Cost if wrong: none.
8. **Plan 6b's chrome targets enter the theme only when 6b's Task 1 is on this branch** (`BORDER_DIVIDER` overridable in
   this worktree), in Task 3 step 5: Light `surface.tabstrip` `#E8E9EB`, `tab.indicator` `#2F65CA`, `surface.scrim`
   `#00000036`, `border.divider` `#D3D6DA`; Dark `#3A3D42`, `#3D74D6`, `#00000036`, `#4A4D53` (6b's ruling 2 and R47,
   over Rulings 7 for those roles), `surface.tabstrip` leaves `EnterpriseThemeTest.NEUTRAL` and `border.divider` joins
   both override maps; Task 4 then does the rest of R47 (6b's printed face-width probes become assertions, R39's swatch
   is re-measured) and the derived table is generated with the override. Otherwise the eight values are 6b's to copy at
   its merge, and the banner says so. Why: the orchestrator's instruction; 6b's R19 and R47 say the same from its side.
   Checked both ways in the dry run (`EnterpriseThemeTest` passes with and without 6b's flag). Cost if wrong: eight theme
   values and three test lines move at the later merge.
9. **Plan 6a Rulings 26 happens in Task 4 step 4.** `EnterpriseLook` reads `EnterpriseResources` (the shipped kit with
   Plex registered, its chrome, the Enterprise theme); `enterprise-fixture.json` is deleted; `WindowChromeMenusTest`
   asserts "IBM Plex Sans" again (6a Rulings 20); `EnterpriseMenuRowMockTest` checks the titles on every platform (6a
   Rulings 29); `EnterpriseLook.CHROME` stays as the spec 5.1 table and `EnterpriseFixtureTest` holds it equal to the
   shipped kit's chrome. Why: the fixture stood in for 5b only. Cost if wrong: a title past 4 px is retuned by 6a's
   Rulings 18 measurement.
10. **Plan 8a R8, R9 and R16 happen in Task 4 step 4.** `RunWindowRenderTest.render` draws the shipped kit and the
    Enterprise theme (Enterprise Dark for Dark) at the gallery's font, now the kit's face at its 12-point body (the
    hand-set 12-point platform font goes); FONTS joins `CHECKED`; "primary button left" and "cancel button left" move to
    a new `FOOTER` owner that stays printed, because the mocks draw every footer button 70 px wide and that ruling is
    plan 8b's. A FONTS probe that misses under Plex is a layout or face defect: fixed, or the task stops and reports; no
    tolerance widens. Cost if wrong: 8b's refresh reads `FOOTER`.
11. **Mock matching is checked, not opt-in.** Each `enterprise-*` pair gets a `min_agreement` of its measured agreement
    less 0.05 (rounded down to two decimals) and `colours` from fact 12 that its mock crop shows, and
    `check_pairs.py … enterprise-` must print eight PASS lines, as plan 4c's `classic-*` pairs do. Why: the orchestrator
    makes the comparison with the Workstation and Dark Mode mocks a hard requirement. Cost if wrong: a floor tuned once.
12. **Finding for the user: Enterprise Dark's `text.onAccent` on `accent` is 4.499:1.** Spec 7.6 gives #FFFFFF on
    #3D74D6 (the Dark Mode mock's), just under the TEXT guard's 4.5, so the guard paints the Run window's selected row,
    and any TEXT-guarded label on the accent, black (#000000, 4.67:1) where the mock draws white (the `*.secondary` text
    on the accent, 3.56:1, goes black too). The theme keeps spec 7.6's values exactly (binding; the orchestrator says
    exactly); 8a's Dark test asserts the pair as painted and prints both contrasts; the STATUS entry asks the user to
    rule. One red step, `#3C74D6` for `accent`, `selection.bg` and `hue.blue`, gives 4.505:1 and keeps white. Cost if
    wrong: Enterprise Dark's selected rows and accent labels show black text until the user rules.
13. **The classic tag's box is held against its tint too** (`ClassicTagFace.style`): with Enterprise Dark in the theme
    set, 4c's `ClassicIndicatorsTest.aClassicTagsLabelAndBoxStandOutAsPainted` measured the blue tag's box at 1.28:1 on
    the tint inside it (the box was guarded against `surface.card` only); the box now meets the structural guard against
    the tint as painted, moving toward `text.primary` where it does not. A box that already holds keeps its colour, so
    the default look, Enterprise Light and every other palette paint as before; jasper-ui passes whole with it. Why: the
    plan says such a failure is a guard finding, and this one has a one-line repair in the face. Cost if wrong: Enterprise
    Dark's blue tag box is slightly lighter than its recipe.
14. **Text corrections.** `LookFontsTest` has three cases; Task 1's gallery base is `HEAD` in a `mktemp` worktree; the
    derived-table example (Light's `surface.rowHover` is an override, Dark's keeps the recipe); Task 4's ledger rows are
    definite (the coverage test names `EnterpriseKitRenderTest` and `EnterpriseFirstWindowTest`); Task 4's commit lists
    the deletion; the STATUS entry and banner are dated 2026-10-17, name these rulings, the chrome and Run window numbers,
    the 6b outcome and ruling 12's finding. Cost if wrong: none.
15. **Order is binding; parallel plans reconcile at merge.** Tasks run in order, one commit each; every later anchor is
    an earlier task's output, as the dry run shows. Plan 6b (parallel) also edits `EnterpriseLook`, the fixture theme,
    `EnterpriseThemeTest`'s expectations, `MockProfile`, `pairs.json`, `docs/themes.md`, `migration.csv` and STATUS;
    plans 7 to 11 append rows, pairs and STATUS paragraphs. Keep every side's rows, pairs and paragraphs; for 6b, ruling
    8 says which side copies the chrome targets. Do not rebase or merge here. Cost if wrong: merge work only.
16. **Timing-sensitive tests.** `LookNoticeTest`, `EnterpriseFirstWindowTest`, the chrome renders and the Run window
    render wait on the EDT; another plan's Gradle build runs at the same time, so one may flake once: re-run it alone
    before treating it as a failure, and never run `./gradlew --stop`. Cost if wrong: one rerun.
17. **What the pre-flight did not verify.** The IBM Plex downloads, their SHA-256 sums and zip paths (Task 3 step 1 stops
    on any mismatch), fact 15's name resolution of the real files, and the Gradle-only checks named in the refresh
    paragraph; Task 3 step 6 and Task 5 step 3 run them. Cost if wrong: the task that runs them stops and reports.

### Task 4 review, round 1 (2026-10-17)

Task 5 carries these into the status banner's deviations, the STATUS entry's `<mock notes>`, its chrome and Run window
numbers and its GUI checks.

18. **The renders hold FlatLaf's user scale at 1.** The gallery's `sftp-connected-short-font24` capture set a 24-point
    default font, which raised FlatLaf's user scale (no `flatlaf.uiScale` in the test task) to 1.5 for every later
    capture and for the 2x panels, so half the gallery PNGs and all four `@2x` panels were drawn at 1.5. Like
    `:jasper-ui:gallery`, `EnterpriseKitRenderTest` now sets `flatlaf.uiScale=1` while it renders; it asserts the scale
    after every capture and before each panel, and each panel's title (Plex SemiBold at `PANEL_TITLE`'s size) and one
    body label (Plex Sans at 12 points). Re-measured edge agreements and floors (ruling 11): Light sessions 0.310 (0.26),
    tunnels 0.057 (0.00), SFTP 0.062 (0.01), File Servers 0.320 (0.27); Dark 0.322 (0.27), 0.059 (0.00), 0.063 (0.01),
    0.317 (0.26). Tunnels and SFTP stay low because the gallery panels are empty where the mocks show the tunnels table
    and the file list (Rulings 9).
19. **Fact-12 colours the mock crops show but the renders lack, dropped from `colours`; deviation awaiting the user's
    ruling** (ruling 11 says drop only what the crop lacks): `#D9DFEA` (Light sessions, SFTP) and `#3D4658` (Dark
    sessions, SFTP) are the focused header strip across the top 44 px of those crops, which the gallery panels do not
    draw (header strips are plan 7's, Rulings 8 and 9); `#2E3135` (Dark tunnels, SFTP) is the card under the mocks'
    tunnels table and file list, which the empty gallery panels do not paint (plans 6, 7 and 10); `#E4E6E9` (Light File
    Servers) appears in that crop only as 44 scattered anti-aliased glyph pixels, not as `surface.stripe`, and the 1x
    render has none. Each returns to its pair when the composition that paints it lands.
20. **Two mock checks sit on their tolerance** (none widened): in `EnterpriseMenuRowMockTest` Help starts 4 px right of
    the mock, `within(4)`, in Light and Dark (Window 2, Tools 2 or 3, the rest 0 or 1); in `RunWindowRenderTest` "help
    text top" is 82 against the mock's 85, tolerance 3, in both states. Follow-up: retune `ClassicMenuRow`'s side and
    padding or the Run window's help offset so both sit inside their tolerance.
21. **Follow-up: menu accelerators under Plex.** `Typography`'s glyph fallback covers kit-drawn text only; FlatLaf draws
    menu accelerators in `defaultFont`, which Rulings 2 makes IBM Plex Sans, and Plex has no glyphs for the macOS modifier
    symbols, so the classic menus' accelerators may draw as boxes. Not checked headlessly or in the GUI: add a headless
    test of a classic-row `JMenuItem` with a Cmd+Shift accelerator under `EnterpriseLook` (or set
    `MenuItem.acceleratorFont` to the platform face when the look's face cannot draw the text), and list the menus'
    accelerators with the GUI checks.
