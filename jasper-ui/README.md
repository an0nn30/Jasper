# Jasper UI

`jasper-ui` is Jasper's theme engine and Swing UI kit. Every window, dialog, tool window, Settings page and plugin
view of the workstation is drawn with it. The application uses it directly. Plugins never load it: they describe their
UI with the JDK-only SDK values in [`dev.jasper.sdk.ui`](../docs/sdk-reference.html#package-dev.jasper.sdk.ui), and the
application translates those into this module's model and renders them here, so a plugin's dialogs and panels take
the same theme and look as Jasper's own ([plugin UI](../docs/plugin-ui.md)).

The module depends on FlatLaf 3.7, FlatLaf Extras 3.7 and JSVG 2.0.0, and on no other Jasper module.

| Package | Holds |
| --- | --- |
| `dev.jasper.ui.theme` | Jasper theme files and their colours: semantic palettes, derived roles, terminal and editor schemes, syntax styles, glass levels, FlatLaf colour keys, paint-time contrast guards and the appearance binding |
| `dev.jasper.ui.model` | Immutable view descriptors (`Nodes`, `ViewSpec`) and the events views report (`UiEvent`) |
| `dev.jasper.ui.metrics` | Geometry catalogs (`DesignMetrics`), the public metric tokens (`MetricTokens`), type scales, faces, typography and the motion policy |
| `dev.jasper.ui.kit` | The look: `Kit`, its control variants (`ControlVariants`) and colour slots (`ColorSlots`) |
| `dev.jasper.ui.icons` | Named artwork: the built-in glyphs, the bundled IntelliJ Platform icons (New UI and classic) and scoped registrations |
| `dev.jasper.ui.swing` | Views (`KitView`), contexts, FlatLaf installation, dialogs, the Settings shell and the native chrome's painters |

`theme`, `model` and `kit` are JDK-only. Each package-info names the packages it may use, and views and bindings are
confined to the Event Dispatch Thread and closed by their owner.

## Themes and looks

A theme supplies colours and nothing else. `JasperTheme` reads a Jasper theme file; seven are built in: Slate Cobalt
(the Dark default), Catppuccin Latte, Catppuccin Frappé, Catppuccin Macchiato, Catppuccin Mocha, Ubuntu Yaru and Jasper
Meadow Light (the Light default). The app
reads installed and plugin theme files with the same loader. [Themes](../docs/themes.md) describes the format.

A kit is the look: the geometry, type scale, faces, control variants, colour slots and FlatLaf key set that controls
and the native chrome draw with. `Kit.DEFAULT` is the standard look and `Kit.CLASSIC` the classic base. A look plugin,
such as the bundled [Enterprise](../docs/enterprise.md) plugin, ships a kit file that starts from one of the two and
tunes it ([looks](../docs/looks.md)). One kit serves a process: it is chosen before the first window and changes only
after a restart.

## Build and test

Use Java 25 on the JetBrains Runtime and the repository wrapper. Every check is headless.

```sh
./gradlew :jasper-ui:check
./gradlew :jasper-ui:test --tests '*SlateCobalt*' --tests '*JasperTheme*'
./gradlew :jasper-ui:gallery -Pjasper.galleryScale=2 -Pjasper.galleryTheme=dark
./gradlew :jasper-ui:verifyUiDistribution
```

`:jasper-ui:check` runs the tests, Javadoc with doclint, the architecture gate `verifyUiArchitecture` and the theme
converter's tests, which need Python 3.11 or later. The gallery renders every fixture headlessly into
`jasper-ui/build/gallery/`. `verifyUiDistribution`, which builds the application distribution and is not part of
`check`, confirms that the application ships the engine jar and that no plugin carries its classes.

[UI architecture](../docs/ui-architecture.md) covers the packages, the theme engine, kits, metrics and density, type,
views, the descriptor vocabulary, keyboard behaviour and icons. [UI maintenance](../docs/ui-maintenance.md) covers the
gallery, the rendering matrix, the golden tables, the rules the tests enforce, how to change the kit and the
distribution check. [Calibration](../docs/design/theme-engine/calibration.md) records the reference crops and scale
anchors the kit was measured against.
