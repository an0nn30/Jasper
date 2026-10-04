# Jasper UI

Standalone theme engine and Swing UI kit. `jasper-ui` has no dependency on the
application, terminal, Buddy, SDK or plugins. The app adapts its configuration
and library APIs into these values; plugins use SDK contracts and host adapters.

The initial extraction provides immutable theme documents, inheritance resolution,
terminal color values, syntax styles and workspace glass levels in `dev.jasper.ui.theme`,
and native TOML parsing in `dev.jasper.ui.themeio`. Existing application implementations
remain temporary extraction sources until the coordinated consumer migration.

`TerminalColors` takes background before foreground and defensively copies exactly
16 nonnull ANSI colors. The terminal library's palette constructor takes foreground
before background; adapters must map these by meaning. `GlassLevels` validates opacity
from 0.1 to 1; saved app configuration is translated by the app adapter.

Package contracts live in each `package-info.java`. Theme, model and parsing packages
never import Swing. Themes supply colors; geometry and typography roles belong to
the kit. Views and bindings are EDT-confined and their owner closes them.

Use Java 25 on JetBrains Runtime and the repository wrapper:

```sh
./gradlew :jasper-ui:test --tests '*ThemeResolverTest'
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
`KitContext` selects each view's theme route and supplies host-owned values. Renderer and
form/panel fixtures are supplied in subsequent implementation tasks.
