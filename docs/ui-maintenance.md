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

See [architecture](ui-architecture.md), [module README](../jasper-ui/README.md) and
[plugin authoring](plugin-authoring.md).
