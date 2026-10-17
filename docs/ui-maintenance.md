# UI maintenance

Use Java 25 JetBrains Runtime and the wrapper. All commands here stay headless:

```sh
./gradlew :jasper-ui:check verifyUiArchitecture
./gradlew :jasper-ui:test --tests '*KitCalibrationTest' -Dflatlaf.uiScale=2
./gradlew :jasper-ui:gallery -Pjasper.galleryScale=1
./gradlew :jasper-ui:gallery -Pjasper.galleryScale=2
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-ui:verifyUiDistribution
```

The gallery is a test-runtime entry point. It writes individual PNG/JSON captures, full-resolution
contact sheets of eight and a manifest under `jasper-ui/build/gallery/<theme>-<scale>x`. `galleryScale` accepts
1/2; `galleryTheme` accepts dark/light (Slate Cobalt's variants). JSON records bounds, actual enabled/focus
state, typed descriptor busy/error/visibility state, route, central font and scale. It excludes values
and secrets. Coverage tests require every node, enum variants, nine originals and real panel
transitions. Test and gallery classes stay out of the production jar. At scale 2 the form font is scaled too.
`-Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=light` renders the gallery and the Enterprise fixtures under the
Enterprise fixture kit into `build/gallery/enterprise-light-<scale>x/`; `tools/visual/check_pairs.py` compares the 2×
renders with the Enterprise mocks (opt-in).
The gallery and the kit fixtures (`KitFixtures`, `Edt` and the `dev.jasper.ui.gallery` builders) are jasper-ui's
`testFixtures` source set, which jasper-app's tests use through `testFixtures(project(":jasper-ui"))`. Inside
`check`, `KitMatrixTest` renders every fixture under `Kit.DEFAULT` over `KitFixtures.themes()`, and the gallery with the
Enterprise fixtures under the Enterprise fixture kit over the theme set and Enterprise Light and under the complement
kit and the seven covering-array kits (`control-covering-array.tsv`) over five palettes; jasper-app's
`PaintedContrastTest` samples every declared pair over the theme set and `KitFixtures.degenerate()`.

Install `FlatLafInstaller.install(theme, kit)` before constructing fixtures. Logical sizes go through `UIScale` once;
the capture's device transform is 1. Do not scale both component geometry and capture graphics.
The gallery supplies the semantic host surface behind transparent composition and excludes native
window chrome. It paints the kit popup directly without opening a native popup; headless captures
suppress the native caps-lock query and supply deterministic focus. Every view/controller, binding,
registry and focus/caret lifetime closes in `finally`; the process exits normally.

Compare original crops using [calibration](design/theme-engine/calibration.md). Correct components
or fixtures when measurements differ; do not loosen thresholds. Captures use Slate Cobalt Dark or Light (the
calibration themes are retired); renderers never fall back to fixed colours. Font rasterization and
native focus/window acceptance remain separate from deterministic geometry tests.

When adding a descriptor, update UI and SDK `Nodes`, `KitValues`, testkit contract coverage and the
actual renderer together. Add an actual mounted fixture plus a behavior or rendered regression;
enumerating a catalog does not prove rendering. Preserve old constructors where the contract requires
compatibility. Named artwork must express explicit intent, not infer feature semantics from text.
Keep fixed geometry in named metrics read through a kit (`KitContext.metrics()`, `ChromePainter`, `NativeStyles`);
`StaticDefaultTest` rejects other `DesignMetrics.DEFAULT` reads and static metric finals. Preserve central font changes and logical scaling.

When changing appearance, test contrasting chrome/workspace colors, replay/rollback and disposal.
A derived role's recipe lives in `DerivedPalette`: changing one, or a built-in theme, updates `DerivedColorTest.INPUTS`
and `derived-roles.tsv` in the same commit. A new metric role gets its row, in enum order, in `compact-roles.tsv` and
`classic-roles.tsv`; the classic catalog inherits every default role outside its delta, so retuning one edits both tables.
A change to the classic catalog, type scale or FlatLaf overlay edits `classic-roles.tsv`, `classic-type.tsv` or
`ui-color-keys-classic.tsv` in the same commit.
A painter that repairs a pair declares it in `PaintedContrastTest`.
A face declares the pairs it paints in `pairs()`, and a change to them edits `face-pairs.tsv` (every face's declared
pairs) in the same commit. Change a control's look through its face; a classic face paints every declared pair with
`ThemePainting.painted`.
When changing layout, test normal and enlarged fonts, narrow/short hosts, real viewport boundaries
and 1×/2× geometry. Focus rings must remain external to full nominal fill bounds. Field placeholders
have one paint owner. Validate actual FlatLaf delegates and scrollbars rather than fallback Metal.

`verifyUiDistribution` assembles `:jasper-app:installDist`, checks the engine jar in the application's
lib directory and rejects UI classes/jars in bundled plugin runtime artifacts. `:jasper-ui:check`
also runs JavaDoc doclint and compile-checked documentation snippets. Full repository verification
and the single independent whole-branch review belong to Plan 1 Task 14; consumer migration and
native screenshots remain Plans 2/3.

Plan 1 Task 14's 2026-10-04 audit matches every one of the 26 sealed module/SDK node types to a real
renderer and exhaustive host projection, all eight event projections and all 14 node enum
vocabularies. `ControlRegistry` validation/creation have no unsupported-kind placeholder defaults;
adding a sealed node now fails compilation until dispatch handles it. Mounted gallery coverage and
`KitValuesTest` verify actual fixture kinds and complete projected fields, rather than source counts alone.
The [exact temporary-path ledger](design/theme-engine/temporary-paths.md), linked from Plan 2, assigns
27 app algorithm/value copies, the old theme bridge, differently named helpers and resource duplicates
to removal/adaptation owners. The app saved-choice `config.TerminalColors` remains configuration.
The reusable algorithm authority is the module; existing app consumer/caching/publication lifetimes
continue until Plan 2 explicitly redirects them.

Task 14 verification evidence: `./gradlew check --rerun-tasks` passed headlessly in 5m 6s with
99/99 tasks executed. XML: **9,043 tests, 9,037 passed, 0 failures/errors, 6 skipped**. All five
architecture gates and UI/SDK doclint passed. Branch diff whitespace and all 178 changed Java sources
(against `192443b3`) passed the hygiene scan. The independent whole-branch review remains pending. Native macOS focus, traffic lights, gestures/window ownership and final application/plugin
screenshots remain user-run; no GUI, benchmark or AOT training was run for this gate. Review the
[gallery/calibration artifacts](design/theme-engine/calibration.md) before entering Plan 2 execution.

| Module | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| jasper-app | 2520 | 2519 | 0 | 0 | 1 |
| jasper-buddy | 150 | 150 | 0 | 0 | 0 |
| jasper-sdk | 59 | 59 | 0 | 0 | 0 |
| jasper-sdk-testkit | 106 | 106 | 0 | 0 | 0 |
| jasper-terminal | 397 | 393 | 0 | 0 | 4 |
| jasper-ui | 261 | 261 | 0 | 0 | 0 |
| plugins/editor | 4791 | 4791 | 0 | 0 | 0 |
| plugins/fileserver | 161 | 161 | 0 | 0 | 0 |
| plugins/history | 41 | 41 | 0 | 0 | 0 |
| plugins/remote | 391 | 391 | 0 | 0 | 0 |
| plugins/sample | 12 | 12 | 0 | 0 | 0 |
| plugins/snippets | 11 | 11 | 0 | 0 | 0 |
| plugins/vault | 143 | 142 | 0 | 0 | 1 |
| Total | 9043 | 9037 | 0 | 0 | 6 |

Counts above were read from 681 XML files in every module/plugin `build/test-results/test/` after
the forced run. The six skipped cases are the existing fish fixture, three Windows-only terminal
cases, font fallback availability and native Keychain integration. Compiler/doclint warnings remain
non-fatal; no tests or assertions were weakened for this verification.

See [architecture](ui-architecture.md), [module README](../jasper-ui/README.md) and
[plugin authoring](plugin-authoring.md).


## Combined final-review fix verification

The single combined fix wave addresses all four Important and four Minor findings: runtime authored
UI-to-semantic colors, secure stored secret details, live dropdown popup theming, nested-update
coherence, no-artwork icon rejection, selected segment medium weight, full named radii and retained
row-context hue. `AuthoredUiColorsTest` includes native/import → semantic → FlatLaf → painted output,
platform and precedence cases, bounded origin-aware failures, and differential checks against pinned
FlatLaf functions. Existing import parity fixtures/goldens are unchanged. Secret module/shared host/fake
contracts check defensive transfers, callback erasure, value-free copy actions and lifetime wiping.

Focused checks include `:jasper-ui:check :jasper-sdk:check :jasper-sdk-testkit:check`, hosted `*Kit*Test`,
UI/app/SDK architecture and `:jasper-ui:verifyUiDistribution`. Geometry/choice/secret/row regressions
also run with `-Dflatlaf.uiScale=2`. Final repository counts and gallery verification follow below.
Scoped rereview is complete: all eight original findings addressed, no new Critical/Important findings.
The known Minor below remains; native/user acceptance and Plans 2–3 completion are not claimed.


Historical (the importer, authored UI colours and their heuristics were removed in Slate Steel Plan 3).
Final-review compatibility ruling: canonical roles honor authored supported UI colors ahead of
FlatLaf theme-name/author-specific post-JSON heuristics. For example, Carbon's authored Panel
`#202020` and TextField `#112233` resolve/install `surface.input=#112233`; the foreign heuristic
`lighten($Panel.background,4%)` does not replace that role. The model has no vendor-name logic or
new provenance mechanism. Existing importer exclusions/provenance and ancillary legacy UI behavior
remain; this is not full vendor visual parity. Themes relying on those heuristics may need explicit
semantic adjustment during Plan 2 visual acceptance. A concrete Carbon regression preserves this ruling.

Final stabilized `./gradlew check` passed in 1m 56s (99 tasks: 15 executed, 84 up-to-date).
The following counts replace the historical pre-review baseline above; they were read from 682
XML files after the full run, not from the focused test output. 9,057 tests passed and the same six
environment-dependent cases were skipped. All architecture/doclint/compiled-documentation gates
passed. The focused 2x geometry/choice/secret/row run passed all 42 tests.

| Module | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| jasper-app | 2522 | 0 | 0 | 1 |
| jasper-buddy | 150 | 0 | 0 | 0 |
| jasper-sdk | 59 | 0 | 0 | 0 |
| jasper-sdk-testkit | 108 | 0 | 0 | 0 |
| jasper-terminal | 397 | 0 | 0 | 4 |
| jasper-ui | 277 | 0 | 0 | 0 |
| plugins/editor | 4791 | 0 | 0 | 0 |
| plugins/fileserver | 161 | 0 | 0 | 0 |
| plugins/history | 41 | 0 | 0 | 0 |
| plugins/remote | 391 | 0 | 0 | 0 |
| plugins/sample | 12 | 0 | 0 | 0 |
| plugins/snippets | 11 | 0 | 0 | 0 |
| plugins/vault | 143 | 0 | 0 | 1 |
| **Total** | **9063** | **0** | **0** | **6** |

## Known Minor expression-parity gap

Historical: `AuthoredUiColors` and its expression grammar were removed in Slate Steel Plan 3.

**Resolved in Plan 2 Tasks 2–3 (2026-10-05), independent review pending:** the three probes
below now match pinned FlatLaf 3.7 in `AuthoredUiColorsTest`. Spin ignores relative hue scaling;
percentage channels truncate and HSL channels use pinned float interpolation. The table is retained
as historical failure evidence, not current output. Focused module gate: 128 tests, zero failures.
Native visual acceptance and complete admitted-expression parity are not claimed.

Final scoped review of `a4e78995..771fd1a1` found a narrow admitted-expression mismatch with
pinned FlatLaf 3.7. The reviewer ran a bounded JBR 25 headless source-launcher probe against
compiled `jasper-ui` and the pinned jar (`java -Djava.awt.headless=true --class-path <ui classes>:<flatlaf 3.7 jar> --source 25 /dev/stdin`). Exact ARGB results:

| Expression | Pure generated | FlatLaf 3.7 |
| --- | --- | --- |
| `spin(#00ff00,30,relative)` | `ff00ff00` | `ff00ff7f` |
| `spin(#00ff00,30)` | `ff00ff80` | `ff00ff7f` |
| `rgba(#123456,50%)` | `80123456` | `7f123456` |

`AuthoredUiColors` applies relative adjustment to hue, whereas the pinned spin parser ignores that
option and rotates by an absolute angle. Spin/rgba channel materialization also differs by one unit.
This is a real known Minor, distinct from the approved authored-color-over-vendor-heuristic policy.
The existing differential sample is bounded evidence, not a promise of complete expression parity.
Controller parked this after the single final fix wave. **Owner: Plan 2 Task 2 theme integration**,
before Task 3 imported-theme visual acceptance: add these differential regressions, keep hue rotation
absolute and match pinned channel materialization (or obtain an explicit documented rounding-policy
ruling). Affected themes must not receive exact parity acceptance until corrected. No architecture/API
task depends on this edge; native/user acceptance is still pending. See the
[final verification handoff](design/theme-engine/verification.md).

## Consumer migration completion

Plan 2 uses the kit as the only standard-control presentation authority. SDK 0.15 is incompatible with
pre-0.15 raw UI factories and raw presentation icons; all eight bundled manifests cut over together.
The [completion audit](design/theme-engine/completion.md) records exact gate scope, owner tests, headless
rendering, distribution checks and the named Plan 3 composition owners. App host Settings error/stub
content uses the dialog kit context and closes with its page; it is not covered by the plugin-only compiled gate.

## Checking a surface against its reference

After changing a kit metric, painter or a surface's composition, regenerate the actual-owner renders and compare
them with the preserved references using [`tools/visual`](../tools/visual/README.md): `compare_all.py` runs the
recorded pairs and `region.py` cuts the same region from a mock and a render at original pixels. The
[acceptance record](design/theme-engine/acceptance.md) lists the regeneration commands, the agreed differences and the
per-family notes; a shared metric change must keep every family's pairs where they are, not only the one being tuned.
