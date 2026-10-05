# UI maintenance

Use Java 25 JetBrains Runtime and the wrapper. All commands here stay headless:

```sh
./gradlew :jasper-ui:check verifyUiArchitecture
./gradlew :jasper-ui:test --tests '*KitCalibrationTest' -Dflatlaf.uiScale=2
./gradlew :jasper-ui:gallery -Pjasper.galleryScale=1
./gradlew :jasper-ui:gallery -Pjasper.galleryScale=2
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=custom
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=calibration
./gradlew :jasper-ui:verifyUiDistribution
```

The gallery is a test-runtime entry point. It writes individual PNG/JSON captures, six full-resolution
contact sheets and a manifest under `jasper-ui/build/gallery/<theme>-<scale>x`. `galleryScale` accepts
1/2; `galleryTheme` accepts dark/light/custom/calibration. JSON records bounds, actual enabled/focus
state, typed descriptor busy/error/visibility state, route, central font and scale. It excludes values
and secrets. Coverage tests require all 26 nodes, enum variants, nine originals and real panel
transitions. Test/gallery classes and calibration colors stay out of the production jar.

Install `FlatLafInstaller` before constructing fixtures. Logical sizes go through `UIScale` once;
the capture's device transform is 1. Do not scale both component geometry and capture graphics.
The gallery supplies the semantic host surface behind transparent composition and excludes native
window chrome. It paints the kit popup directly without opening a native popup; headless captures
suppress the native caps-lock query and supply deterministic focus. Every view/controller, binding,
registry and focus/caret lifetime closes in `finally`; the process exits normally.

Compare original crops using [calibration](design/theme-engine/calibration.md). Correct components
or fixtures when measurements differ; do not loosen thresholds. Active theme captures test routing;
calibration colors are an explicit fixture theme, never renderer fallbacks. Font rasterization and
native focus/window acceptance remain separate from deterministic geometry tests.

When adding a descriptor, update UI and SDK `Nodes`, `KitValues`, testkit contract coverage and the
actual renderer together. Add an actual mounted fixture plus a behavior or rendered regression;
enumerating a catalog does not prove rendering. Preserve old constructors where the contract requires
compatibility. Named artwork must express explicit intent, not infer feature semantics from text.
Keep fixed geometry in named metrics. Preserve central font changes and logical scaling.

When changing appearance, test contrasting chrome/workspace colors, replay/rollback and disposal.
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
Scoped rereview remains pending; this does not claim native/user acceptance or Plans 2–3 completion.


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
