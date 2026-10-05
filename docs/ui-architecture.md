# UI architecture

`jasper-ui` is the independent theme engine and Swing kit. The app composes it with Terminal
and Buddy; plugins compile against the JDK-only SDK projection. Existing application consumers
remain extraction sources until Plan 2. Plan 1's final branch review is Task 14.

| Package | Allowed outgoing UI packages | Ownership |
| --- | --- | --- |
| `theme` | none | Immutable documents, terminal/syntax/glass colors; EDT appearance binding |
| `themeio` | `theme` | Synchronous native TOML parse/write; caller owns files |
| `themeimport` | `theme`, `themeio` | Bounded offline conversion and pinned parent data |
| `model` | none | Immutable descriptors and events; no secret values |
| `metrics` | `theme` | Fixed logical geometry and central typography/motion roles |
| `icons` | `theme`, `metrics`, `model` | Named artwork and context-scoped tint caches |
| `swing` | `model`, `theme`, `metrics`, `icons`, data-only `themeimport.FlatLafThemeData` | EDT views, delegates, owned controls and registrations |

`verifyUiArchitecture` checks the acyclic package graph, independence from other Jasper modules,
JDK-only theme/model boundaries and Swing-free import/parsing. Parser and FlatLaf vendors remain
implementation dependencies. SDK `ui` values mirror the model without exposing Swing or UI types;
`dev.jasper.app.plugins.KitValues` is the translation boundary. Host and testkit implement the same
SDK facade. Plugins never receive the engine jar on their runtime classpath.

Resolve and generate authored colors without constructing Swing controls:

<!-- example:palette -->
```java
var document = ThemeToml.parse("""
        name = "Example"
        [colors]
        background = "#18202b"
        foreground = "#e6edf5"
        accent = "#79afff"
        [colors.normal]
        black = "#313b49"
        red = "#d75461"
        green = "#4b9a6c"
        yellow = "#c89d42"
        blue = "#4b82cf"
        magenta = "#9e6bc4"
        cyan = "#448fa4"
        white = "#c7ced7"
        [semantic]
        "surface.card" = "#20242b"
        """, "example.toml");
var generated = ThemeGenerator.generate(ThemeResolver.resolve(document));
var cardColor = generated.semantic().color(SemanticColor.SURFACE_CARD);
```

Theme seeds and semantic overrides supply colors. Geometry, spacing, artwork and typography roles
come from `DesignMetrics`, `Typography` and `IconRegistry`; theme import discards authored geometry.
`TerminalColors` orders background then foreground; the terminal palette orders foreground then
background. Translate by meaning. ANSI arrays are defensively copied.

Install appearance on EDT before widget creation. `AppearanceTransaction` installs real FlatLaf
and publishes a complete chrome/workspace `ThemePair`, restoring prior global defaults on failure.
Each `KitContext` selects a route. Binding subscriptions replay and close independently; views retain
stable control/document identity on updates. Font-only default changes refresh central typography.

The caller owns binding and registry lifetimes; a view owns listeners, its descriptor controls and
registered specialized content. Close the view first. This compiled example runs inside an EDT block;
`generated` is the previous example's output and `events` is a caller-owned list of `UiEvent`:

<!-- example:view -->
```java
try (var binding = new ThemeBinding(new ThemePair(generated, generated), failure -> {});
     var icons = new IconRegistry()) {
    var context = new KitContext(binding, ThemeRoute.WORKSPACE,
            DesignMetrics.DEFAULT, icons, () -> false);
    var root = new Nodes.Stack("editor", ControlState.READY, LayoutRole.VERTICAL,
            List.of(new Field("name", ControlState.READY, FieldKind.TEXT,
                    "", "Name", "Name", ""),
                    new Secret("password", ControlState.READY,
                            "Password", "Password", "")));
    try (var view = new KitView(context, new ViewSpec(root), events::add)) {
        view.withSecret("password", temporary -> {
            // Consume synchronously; the kit erases this temporary array on return.
        });
        view.clearSecret("password");
        binding.publish(new ThemePair(generated, generated));
    }
}
```

`withSecret` exposes only a temporary array and erases it in `finally`, including callback failure.
Asynchronous consumers must transfer a clone to an owner that erases it. Secrets never enter nodes,
events, metadata or diagnostics. Reveal state and wipeable documents belong to the view.

Containers own semantic layout. Forms consume a host-supplied logical available-content-height;
the host owns window chrome, sheet/scrim lifetime and cancellation cleanup. Panels own header,
context/navigation, virtual body and persistent footer. Hidden headers consume zero space.
Three-column managers use named sidebar/list widths (192/272 logical pixels), then remaining detail;
narrow hosts fit those roles proportionally. A trailing footer action anchors to the right edge.
Virtual rows retain bounded live controls; mutable fields, secrets and nested custom content stay in
stable composition outside rows. Specialized content registrations validate their kind and dispose
on replacement/close; they are not a route for arbitrary feature forms.

Calibration clarified nominal geometry: a 28-pixel control paints its full 28-pixel bounds. The view
owns the external 3-pixel focus/error halo, clipped by real viewports, without changing row gaps.
Named radii are radii; Java rounded rectangles receive twice that value as their arc diameter.
The kit paints placeholders once rather than delegating a duplicate FlatLaf hint.

`Choice.icon` is a named cue for PICKER only; empty means no cue. The original seven-argument
constructor retains its PICKER `key` default, with other kinds empty. The eight-argument form permits
explicit `lock` or empty artwork; nonempty cues on other kinds are rejected. MenuItem.hue explicitly selects category artwork hue; the original constructor delegates NEUTRAL.
No domain meaning is inferred from ids/text. Category tiles likewise follow explicit SHEET/DETAIL, HERO/RAIL and
single/double-list roles; ordinary CONTROL folder icons remain plain glyphs.

See [maintenance](ui-maintenance.md), [calibration evidence](design/theme-engine/calibration.md),
[module entry point](../jasper-ui/README.md) and [SDK architecture](sdk-architecture.md).
