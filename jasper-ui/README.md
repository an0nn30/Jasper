# Jasper UI

Standalone theme engine and Swing UI kit. `jasper-ui` has no dependency on the
application, terminal, Buddy, SDK or plugins. The app adapts its configuration
and library APIs into these values; plugins use SDK contracts and host adapters.

The engine is `dev.jasper.ui.theme`: `JasperTheme` reads a Jasper theme file (the bundled Slate Steel is `slate-steel.json`) and gives
each variant's semantic palette, terminal and editor schemes, syntax styles, glass levels and FlatLaf UI keys
(`UiColorKeys`, from the roles) as an immutable `GeneratedTheme`; the app reads installed theme files with the same
loader. The app and plugins use this authority; [completion evidence](../docs/design/theme-engine/completion.md)
separates source migration from Plan 3 composition.

`TerminalColors` takes background before foreground and defensively copies exactly
16 nonnull ANSI colors. The terminal library's palette constructor takes foreground
before background; adapters must map these by meaning. `GlassLevels` validates opacity
from 0.1 to 1; saved app configuration is translated by the app adapter.

Package contracts live in each `package-info.java`. Theme and model packages
never import Swing. Themes supply colors; geometry and typography roles belong to
the kit. Views and bindings are EDT-confined and their owner closes them.

Use Java 25 on JetBrains Runtime and the repository wrapper:

```sh
./gradlew :jasper-ui:test --tests '*SlateSteel*' --tests '*JasperTheme*'
./gradlew :jasper-ui:check verifyUiArchitecture
```

Checks are headless. The bytecode architecture gate rejects other Jasper modules,
Swing dependencies in pure packages and package cycles. It requires only UI classes,
so it introduces no circular dependency on application checks.

The kit foundation now provides immutable `Nodes`/`ViewSpec`/`UiEvent` values, fixed logical
`DesignMetrics`, centrally resolved `Typography`, and monochrome `IconRegistry` artwork.
`ThemeBinding` publishes complete chrome/workspace pairs on EDT; subscriptions replay and
close independently. `AppearanceTransaction` installs real FlatLaf global defaults before
publication and restores the old LAF, LAF defaults and developer overrides on failure.
`KitContext` selects each view's theme route and supplies host-owned values. All 26 node renderers and four working panel controllers are implemented. The headless gallery
now exercises the eight initial mock compositions, panel transitions and state variants; final
whole-branch review remains Plan 1 Task 14. Existing consumers migrate in Plans 2/3.

Public usage, package/lifetime boundaries and compiled examples are in
[UI architecture](../docs/ui-architecture.md). [Maintenance](../docs/ui-maintenance.md)
documents gallery/distribution checks; [calibration](../docs/design/theme-engine/calibration.md)
records original crop/scale anchors and the inspected capture matrix (its calibration themes are retired; the gallery
renders Slate Steel Dark or Light).

```sh
./gradlew :jasper-ui:gallery -Pjasper.galleryScale=2 -Pjasper.galleryTheme=dark
./gradlew :jasper-ui:verifyUiDistribution
```

Panel `SINGLE` lists use inset rounded selection. A `DETAIL` stack in a panel footer uses compact identity and action metrics; a manager detail retains its own inset and category scale. `CONTEXTUAL_EMBEDDED` buttons reserve their full interactive geometry while painting only for selected, hovered, keyboard-active rows or direct control focus. Ordinary `EMBEDDED` remains persistently painted.

Borrowed panel headers may explicitly provide immutable `PanelHostHeader.Artwork(name, pathData)` for monochrome semantic tint and canonical panel optics. Custom paths retain their authored stroke; receiving controls own validated snapshots and replacement lifetimes. The compatible three-argument `Action` preserves arbitrary host `Icon` identity, colors, alpha and dimensions.
