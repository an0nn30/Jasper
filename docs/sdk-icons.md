# Plugin icons

SDK 0.15 uses semantic strings for ordinary presentation: actions, palette scopes and rows,
notifications, session tabs, panel stripes and header actions, toolbar menus, content tabs and
status items. Examples include `"lock"`, `"unlock"`, `"file"`, `"folder"`, `"server"`, `"tunnel"`,
`"download"` and `"add"`. Optional icons retain their Optional or nullable convention.

```java
context.actions().register(ActionSpec.of("your.plugin.open", "Open").withIcon("file"), event -> {});
status.setIcon("lock");
```

The host validates every name against the plugin's scoped registry. Unknown and foreign names
are rejected. Host and fake enforce the same rule. Retained host icons resolve the current theme
at paint time, including a change between Slate Cobalt Dark and Light.

Two built-in names are authored host artwork (SDK 0.18.1): `"run"`, IntelliJ's classic Run triangle,
and `"application"`, its run-configuration icon. Controls draw them in their own colours in both
looks; panels, prompts and tiles draw a monochrome glyph of the same name.

Under the Enterprise look a panel's stripe label starts with its icon at 13 px: `"network"`,
`"folder"`, `"tunnel"`, `"download"`, `"server"` and `"bell"` draw IntelliJ's classic tool-window
icons, and any other name (a registered path included) its monochrome glyph in the label's colour;
an identity name such as `more.vertical` keeps its authored art.

For original artwork, register a constrained monochrome path in a 16-unit coordinate space:

```java
var registration = context.kit().registerIcon(
    new IconAsset("your.plugin:mark", "M 2 2 L 14 14 M 2 14 L 14 2"));
```

Use `"your.plugin:mark"` wherever an icon name is accepted. Close the registration when no
presentation uses it; plugin shutdown clears its registry. Paths have bounded commands and geometry;
SVG markup, resource loading and authored colors are not supported standard-control icon routes.
The host supplies stroke, size and semantic tint. Standard UI has no arbitrary Swing Icon overload.

Manifest identity PNG/SVG images remain supported in the app-managed plugin preview. They are
separate from the kit glyph registry and do not grant a custom standard-control painter.

The 0.15 boundary removes `IconName`, `BundledIcon`, their fake raw Icon values, all `Appearance.icon`
overloads and SDK UIManager palette readers. Declare `sdk = ">=0.20.0, <0.21"` after migrating.

Native palette and notification views retain immutable glyph snapshots in registries scoped to the receiving view. Removing the original registration prevents new use; already-posted notification history can still show its captured glyph after expiry or plugin shutdown. Each receiver resolves tint from its live theme and releases unused registrations after successful reconciliation or when closed. Failed reconciliation keeps both glyph generations available until recovery or close.

Private app cleanup in Task11b removes the old authored standard-control catalog and resource loaders.
The native tray template and manifest identity fallback keep their original artwork and legal provenance.
The notification action now uses the module bell glyph. PresentationIcon/PresentationIcons remain the
supported app-internal immutable snapshot projection, including receiver-local aliases and failure retention.
