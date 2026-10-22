# UI maintenance

Use Java 25 on the JetBrains Runtime and the repository wrapper. Every command here is headless:

```sh
./gradlew :jasper-ui:check
./gradlew :jasper-ui:test --tests '*KitCalibrationTest' -Dflatlaf.uiScale=2
./gradlew :jasper-ui:gallery -Pjasper.galleryScale=1
./gradlew :jasper-ui:gallery -Pjasper.galleryScale=2
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=light -Pjasper.galleryScale=2
./gradlew :jasper-ui:verifyUiDistribution
```

`:jasper-ui:check` runs the module's tests, Javadoc with doclint (every group except missing comments; an error fails
the build), the root-project gate `verifyUiArchitecture` ([packages](ui-architecture.md#packages)) and
`checkThemeTool`, the theme converter's Python tests (`tools/themes`), which need Python 3.11 to 3.13 (3.14 fails
`test_deeply_nested_input_is_a_message`) as `python3` or `python`, or the interpreter `-Pjasper.python=<path>` names.
The test task depends on the module jar, passes `jasper.uiJar` and `jasper.repoRoot` to the tests, and takes every
`docs/**/*.md` and `jasper-ui/README.md` as inputs, so a documentation edit reruns the documentation tests.
`-Dflatlaf.uiScale=<n>` is forwarded to every test JVM, to run the same logical geometry at another scale.
`verifyUiDistribution` is not part of `check`.

## The gallery

`./gradlew :jasper-ui:gallery` runs `dev.jasper.ui.gallery.KitGallery` from the test runtime on JBR 25 with
`java.awt.headless=true`; it opens no window.

| Property | Values | Default |
| --- | --- | --- |
| `-Pjasper.galleryScale` | `1`, `2` (FlatLaf's user scale) | `1` |
| `-Pjasper.galleryTheme` | `dark`, `light`: the Slate Cobalt variant, or the Enterprise theme's under the Enterprise kit | `dark` |
| `-Pjasper.galleryKit` | `default` (the gallery fixtures under `Kit.DEFAULT`), `enterprise` (the gallery and Enterprise fixtures under the Enterprise fixture kit) | `default` |

It writes `jasper-ui/build/gallery/<theme>-<scale>x/` (`enterprise-<theme>-<scale>x/` for the Enterprise kit):

- `<fixture>.png` and `<fixture>.json` for each fixture. The JSON records the fixture, its reference image, state,
  route, scale, device scale (1), size, font, body size, `motionEnabled` (false), every kit-owned component (id,
  class, visible, enabled, focus, bounds) and every descriptor's state (id, kind, visible, enabled, busy, error). It
  holds no values and no secrets.
- `sheet-<n>.png`: full-resolution contact sheets of eight renders, four columns.
- `manifest.json`: theme, scale, device scale, look and feel class, fixture count and the number of node kinds.

The gallery installs `FlatLafInstaller.install(theme, kit)` before it builds any fixture and restores the previous
look and feel afterwards. Logical sizes go through FlatLaf's `UIScale` once and the capture's device transform is 1, so
component geometry and capture graphics are never both scaled; at scale 2 the form font is scaled too. The default
font is the kit's own regular UI face or the platform family, at the kit's body size (13 points for the default kit,
12 for Enterprise). It paints the semantic host surface behind transparent compositions, excludes native window
chrome, paints the kit popup directly instead of opening a native popup, turns off the native caps-lock query
(`PasswordField.showCapsLock`), supplies deterministic focus, closes every view, binding, registry and focus or caret
lifetime in `finally`, and exits normally.

Fixtures live in jasper-ui's `testFixtures` source set, which jasper-app's tests use through
`testFixtures(project(":jasper-ui"))`; none enter the production jar:

| Class | Holds |
| --- | --- |
| `KitGallery` | The catalog (`fixtures()`, `enterpriseCatalog()`), the capture and the headless entry point |
| `FormFixtures` | The eight reference compositions (`add-host`, `unlock-vault`, `new-file-server`, `create-vault`, `credential-vault`, `vault-add-menu`, `add-login-sheet`, `add-ssh-key-sheet`) |
| `PanelFixtures` | Four working demo panels (`sessions`, `tunnels`, `sftp`, `fileservers`) and their transitions |
| `StateFixtures` | Every vocabulary variant, with headless interaction and presentation states |
| `TableFixtures` | The table vocabulary: a column tree, a grouped table with cell dropdowns, cell stacks, and empty tables and ordered lists |
| `EnterpriseFixtures` | The seven `classic-*` compositions |
| `KitFixtures` | Themes and kits for tests: `dark()`/`light()` (Slate Cobalt), `enterpriseDark()`/`enterpriseLight()`, `enterprise()` (the classic catalog with the shipped kit file's tokens, the 12-point classic type, classic controls, slots and keys, platform faces), `complement()`, `withControls()`, `custom()` (every role 20% plum), `marked()` (every role its own colour), `themes()` (every built-in variant, the converter goldens `ocean`, `ocean-dawn` and `stray`, and Enterprise Dark and Light), `degenerate()` (eight palettes no theme should have), `colour(theme, key)` (a semantic or derived role's colour on a theme, which Enterprise tests read instead of colour literals), `painted(theme, pair)` (what a face paints for a pair), `context(...)` and `render(...)` |
| `Edt` | Runs test code on the EDT |

`GalleryCoverageTest` requires a real mounted fixture for every node kind and enum variant, all nine original
references and the four panels, and checks that the shipped jar holds no `/gallery/` or `/testsupport/` classes.
`EnterpriseGalleryTest` requires every Enterprise fixture to have a `classic-*` pair in
[`tools/visual/pairs.json`](../tools/visual/README.md) at twice its size. Enumerating a catalog does not prove
rendering: a new fixture is mounted and asserted on.

Compare renders with their references as
[Checking a surface against its reference](#checking-a-surface-against-its-reference) describes; the
[calibration record](design/theme-engine/calibration.md) holds the dated crops and scale anchors the kit was measured
against. Correct components or fixtures when measurements differ; do not loosen thresholds. Renderers never fall back
to fixed colours. Font rasterization and native focus or window acceptance stay separate from the deterministic
geometry tests.

## Rendering matrix and contrast

Inside `check`, `KitMatrixTest` renders:

- every gallery fixture under `Kit.DEFAULT` over `KitFixtures.themes()`;
- the gallery and the Enterprise fixtures under the Enterprise fixture kit over the same theme set;
- the complement kit and the seven covering-array kits (`control-covering-array.tsv`, strength 2 over the 14 control
  points) over five palettes: Slate Cobalt Dark and Light, Catppuccin Latte, `marked()` and `custom()`.

Under `custom()`, no 2 × 2 area may be a colour only another theme has, under either kit. Under `marked()`, every
one-colour 3 × 3 area of a classic surface must be a role, a derived role, a guarded pair or a blend of two. jasper-app's
`PaintedContrastTest` paints every declared pair (faces, the Run window, `ChromePainter.DOCK_PAIRS`) over the theme
set and `KitFixtures.degenerate()` and checks each is painted as its guard makes it.

## Golden tables

These tables freeze values; a change to what they freeze edits the table in the same commit.

| File (under `jasper-ui/src/test/resources/dev/jasper/ui/`) | Freezes | Test |
| --- | --- | --- |
| `metrics/compact-roles.tsv` | `DesignMetrics.DEFAULT`, one row per role in enum order | `DesignMetricsTest` |
| `metrics/classic-roles.tsv` | `DesignMetrics.CLASSIC` | `ClassicCatalogTest` |
| `metrics/default-type.tsv` | `TypeScale.DEFAULT` | `TypeScaleTest`, `TypographyTest` |
| `metrics/classic-type.tsv` | `TypeScale.CLASSIC` | `ClassicTypeTest` |
| `swing/face-pairs.tsv` | Every face's declared pairs | `PaintSpecTest` |
| `theme/ui-color-keys.tsv` | `UiKeySet.DEFAULT` FlatLaf keys | `UiColorKeysTest` |
| `theme/ui-color-keys-classic.tsv` | The classic FlatLaf overlay | `ClassicUiKeysTest` |
| `theme/derived-roles.tsv`, `theme/derived-roles-enterprise.tsv` | Every derived role of the built-in themes and of Enterprise | `DerivedRolesTableTest` |
| `gallery/control-covering-array.tsv` | The seven covering-array rows | `KitMatrixTest` |

## Rules the tests enforce

| Test | Rule |
| --- | --- |
| `verifyUiArchitecture` | The package edges each package-info lists, no package cycle, no other Jasper module, JDK-only `theme`, `model` and `kit` |
| `StaticDefaultTest` (jasper-app) | Geometry and type follow a kit: outside `dev.jasper.ui.metrics` and `dev.jasper.ui.kit`, only `AnimationSettings` reads `DesignMetrics.DEFAULT`; nothing reads `TypeScale.DEFAULT`, `FaceSet.PLATFORM`, `DesignMetrics.CLASSIC` or `TypeScale.CLASSIC`; only the kit parser reads `Kit.CLASSIC`; only the classes that choose the start-up kit or fall back to it read `Kit.DEFAULT`; no static initializer reads a metric or builds a painter |
| `VariantReadTest` (jasper-app) | `ControlVariants` is read only in `dev.jasper.ui.kit`, `dev.jasper.app.presentation` and `KitFaces` |
| `StrategyIsolationTest` (jasper-app) | Faces never reference `Kit`, `ControlVariants` or `ChromeSpec`; the reconciler references control classes only, never a face or `KitFaces` |
| `ProjectionReadTest` (jasper-app) | Each look projection's variants are read only by the factory or supplier that owns them |
| `ColourLiteralScanTest` (jasper-app) | No colour literal in `jasper-ui/src/main`, `jasper-app/src/main` or any `plugins/*/src/main`: Java string literals holding `#RRGGBB` or `#RRGGBBAA`, `Color(`, `ColorUIResource(` or `Color.decode(` given a hex value, and `#RRGGBB[AA]` in `.json`, `.toml`, `.properties`, `.xml`, `.css`, `.html` and `.tsv` resources. Exempt: the built-in theme files, the theme files in-repo `plugin.toml`s list (which must load with the production loader) and the boot splash's `SplashColors.java` |
| `KitValuesTest` (jasper-app) | Every node, field and event projects between the SDK and the module without loss |
| `PresentationConformanceTest` (jasper-app) | The Enterprise descriptor, kit file (every key listed, parsed without a problem, no `[branding]`), fonts and theme, and that the icons and splash art still in its resources are the design reference's files |
| `LookIdentityTest` (jasper-app) | No class in the app or jasper-ui jar, and no `dev/jasper/` class of a staged plugin other than Enterprise, holds "enterprise" in any case in its constant pool: a look is data the host reads, and plugins ask for the look through the SDK |
| `KitKeyBindingAuditTest` | A kit key binding with a blank action never hides the look and feel's binding for the same key |
| `JasperMeadowThemeTest` | Jasper Meadow has a Dark and a Light variant. In each, `surface.terminal` is the matching scheme's background and the scheme's text reaches 7:1 on it; primary, secondary and link text reach 4.5:1 on the app, panel, input and menu surfaces, as do on-accent and on-selection text on their backgrounds and every syntax colour on the editor background. In Dark, ANSI colours 1 to 15 reach 4.5:1 on the terminal background and the switch knob 3:1 on both tracks |
| `UiDocumentationTest` | `jasper-ui/README.md`, `docs/ui-architecture.md`, `docs/ui-maintenance.md` and `docs/design/theme-engine/calibration.md` exist; their relative Markdown links outside code fences resolve; each `<!-- example:name -->` block (a lower-case name) followed by a Java fence equals the `// example:name:start` to `:end` region of `UiExamplesTest.java` (`palette` and `view`) |

jasper-app's `AppDocumentationTest` checks the same files' links and `#anchors`.

## Changing the kit

### A descriptor

Update the UI and SDK `Nodes`, `KitValues`, the testkit's contract coverage and the renderer together. `Nodes.Node`
is sealed, so the control registry's dispatch fails to compile until it handles a new kind. Add a mounted fixture and
a behaviour or rendered regression. Named artwork expresses explicit intent; it never infers feature meaning from ids
or text. A control that shows the node's text sets `html.disable` before its first text or Action, and on the tooltip
it creates ([plain text](ui-architecture.md#views)); `ButtonsTest`, `PanelSurfacesTest` and `SettingsControlsTest`
check that a text starting with `<html>` builds no `BasicHTML` view. Then run `./gradlew sdkReference` to regenerate
[the SDK reference](sdk-reference.html), which `verifySdkReference` checks, and describe the node in
[plugin UI](plugin-ui.md).

### A metric role

Keep fixed geometry in named roles read through a kit (`KitContext.metrics()`, `ChromePainter`, `NativeStyles`);
`StaticDefaultTest` rejects other default-catalog reads and static metric finals. A new role gets its row, in enum
order, in both `compact-roles.tsv` and `classic-roles.tsv`; the classic catalog inherits every default value outside
its delta, so retuning a default role outside the delta edits both tables, and retuning the classic catalog edits
`classic-roles.tsv`.

### A metric token or a control point

A token is a `MetricTokens.Token` with its key, range and roles; the kit parser reads the keys from the enum. A
control point is a `ControlVariants` component and enum, a `KitParser` key (its `CONTROLS` list and its read), a face
pair (default and classic) wired in `KitFaces` with its declared pairs in `face-pairs.tsv`, a new column in
`control-covering-array.tsv` (the point count is checked by `ControlVariants.of` and by `KitMatrixTest`'s strength-2
check) and fixtures that show both values. `PresentationConformanceTest` expects the Enterprise kit file to list every
key, so a new token or point is added to `enterprise.kit.toml` and to that test's key list;
`KitFixtures.enterpriseTokens()` lists the shipped token values in enum order. Document it in [looks](looks.md) and in
the [control points](ui-architecture.md#control-points) or [metric tokens](ui-architecture.md#metric-tokens) table,
including the counts there (and the point count in the [rendering matrix](#rendering-matrix-and-contrast)).

### A face, painter or theme

- Change a control's look through its face. A face declares the pairs it paints in `pairs()` and a change to them
  edits `face-pairs.tsv`; a classic face paints every declared pair through its guard (`ThemePainting.painted`). A
  painter that repairs a pair declares it in `PaintedContrastTest`.
- A derived role's recipe lives in `DerivedPalette`. Changing one updates `DerivedColorTest.INPUTS` when the roles it
  reads change, and updates `derived-roles.tsv` and `derived-roles-enterprise.tsv`. Changing a built-in theme updates
  `derived-roles.tsv`; changing the Enterprise theme updates `derived-roles-enterprise.tsv`. Make these edits in the
  same commit. The Enterprise theme is the converter's port of IntelliJ's own themes: change
  `tools/themes/enterprise/port.json` or the converter, never `enterprise.json` by hand, and regenerate the file as
  `tools/themes/enterprise/SOURCE.md` says (`test_jasper_theme.py` compares the two byte for byte). A change to the
  classic FlatLaf overlay edits `ui-color-keys-classic.tsv`. Slate Cobalt's file stays identical to the block in
  [`slate-cobalt.md`](design/theme-engine/slate-cobalt.md).
- A new built-in theme is `<id>.json` under `jasper-ui/src/main/resources/dev/jasper/ui/theme/`, loaded in
  `JasperTheme.BuiltIns.read()` (its place there is its place in Settings). Add its rows to `derived-roles.tsv` and its
  id to the theme converter's `BUILT_IN` (`tools/themes/jasper_theme.py`), and add it to the tests that list the
  built-ins: `BuiltInThemesTest`, `KitFixturesTest`, `UiColorKeysTest`'s variant count, and jasper-app's
  `ThemeLibraryTest`, `ThemeSelectionTest`, `SettingsAppearanceTest` and `PackagedResourcesTest`. Name it in
  [themes](themes.md), in the [packages table](ui-architecture.md#packages) and
  [built-in list](ui-architecture.md#themes) of UI architecture and in the [module README](../jasper-ui/README.md).
- When changing appearance, test contrasting chrome and workspace colours, replay and rollback, and disposal.

### Layout

Test normal and enlarged fonts, narrow and short hosts, real viewport boundaries and 1× and 2× geometry. Focus rings
stay outside full nominal fill bounds. Field placeholders have one paint owner. Validate real FlatLaf delegates and
scroll bars, never a Metal fallback.

### Icons

- A built-in path glyph is an entry in `IconRegistry`'s built-in map: original 16-unit path data, listed in
  `FILLED_BUILT_INS` only when the artwork is a filled silhouette, and named in the
  [glyph list](ui-architecture.md#icons).
- A bundled SVG meaning takes a light SVG and its `_dark` sibling under `src/main/resources/dev/jasper/ui/icons/`, a
  row for each (resource path, upstream path, SHA-256) in `assets-selected.tsv` (the catalog), `assets-chrome.tsv`
  (the chrome set), `assets-classic.tsv` (IntelliJ's classic icons, under `classic/`; a file without a `_dark`
  sibling serves both variants) or `assets-editor.tsv` (the document actions, under `editor/`, with their names in
  `BuiltInArtwork.EDITOR_NAMES`), the file count in `BuiltInArtwork`'s manifest check (14, 20, 26 and 16) and the
  meaning in its map. A classic tool-window icon for the labelled stripes goes under `classic/toolwindows/` and into
  `BuiltInArtwork`'s stripe table, keyed by the icon name it draws for. The files are byte-identical upstream artwork
  under the Apache-2.0 `LICENSE.txt` beside them, with the upstream `NOTICE.txt` and the `ATTRIBUTION.txt` the
  Licenses window shows; each classic file keeps its JetBrains copyright comment, except the two `toolWindowServer`
  files, which upstream ships without one (`BuiltInArtworkTest` checks both). `tools/icons/fetch-classic-icons.py`
  reproduces the classic files.
- The host accepts a plugin icon name that `IconRegistry.names()` holds, and that includes every built-in name. A new
  name (a path glyph or a catalog meaning) is therefore also added to the testkit's `FakeKit.BUILT_INS`, to the
  built-in names in [plugin icons](sdk-icons.md) and in `tools/sdk-reference/guide/08-ui.html` (then run
  `./gradlew sdkReference`), and to the glyph or catalog list and counts in [icons](ui-architecture.md#icons).

## Distribution

`verifyUiDistribution` builds `:jasper-app:installDist`, checks that `jasper-ui.jar` is in
`jasper-app/build/install/jasper-app/lib/`, and fails when any `jasper-plugin-*` project's runtime classpath holds the
UI jar or a jar with `dev/jasper/ui/` classes, or when a staged plugin jar under the distribution's `lib/plugins/`
does. It never launches the application or trains the AOT cache.

## Checking a surface against its reference

After changing a kit metric, painter or a surface's composition, regenerate the renders of the real owners and compare
them with the preserved references using [`tools/visual`](../tools/visual/README.md), which lists the preview tasks
and tests that write each render. `compare_all.py` runs the recorded pairs, `region.py` cuts the same region from a
mock and a render at original pixels and `check_pairs.py` passes or fails the pairs that carry an agreement floor (the
`classic-*` pairs take the Enterprise gallery at Light 2×). The [acceptance record](design/theme-engine/acceptance.md)
lists the agreed differences and per-family notes; a shared metric change must keep every family's pairs where they
are, not only the one being tuned.
