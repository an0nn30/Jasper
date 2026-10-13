# Building plugin UI with the Jasper theme engine

Jasper has one theme engine and one UI kit (`jasper-ui`). The application and every bundled plugin build their
standard UI with it, so a plugin's dialogs, panels and settings look exactly like Jasper's own. Plugins never see the
kit's Swing classes: they describe their UI with immutable SDK values (`dev.jasper.sdk.ui.Nodes`) and the host turns
them into kit controls. This guide shows how to use that vocabulary; the [authoring guide](plugin-authoring.md) covers
registration, lifetimes and threading.

## What the host owns, and what you own

| You describe | The host decides |
| --- | --- |
| Which surface (form kind, panel, split), its sections, rows and controls, their order and stable ids | Every size, inset, gap, radius, font and icon geometry |
| Text, values, options, enabled/visible/busy/error state, the one primary action | Colors, from the active theme's semantic roles, in Slate Steel Dark and Light |
| What an event means for your domain, and the next spec | Focus, keyboard (Enter, Escape, Tab, arrows), validation placement, popups, scrolling, accessibility names |

Do not paint, choose colors or fonts, or set pixel sizes for ordinary controls; there is no API for it, and the
architecture gate rejects plugin code that tries. Specialized content (an editor canvas, a chart, a terminal-like
view) is the only exception: register it as `CustomContent` inside a `Nodes.Custom` node and read colors from
`context.appearance()` (see [Colors for specialized content](#colors-for-specialized-content)).

## The lifecycle of a view

1. Build a `ViewSpec` from your current domain state and call `context.kit().create(spec, handler)`.
2. The handler receives `UiEvent`s (`Edited`, `Toggled`, `Selected`, `Action`, ...) on the UI thread.
3. Change your state and call `view.update(nextSpec)`. The host reconciles by node id: typed text, caret,
   selection, focus and scroll survive, and theme changes re-render without your involvement.
4. Close the view (or let its window, panel or dialog close it). Late callbacks after close are ignored by the host.

Keep ids stable for the life of a control; changing an id replaces the control.

## Compositions

Pick the composition from what the surface is, not from how it should look:

| Surface | Descriptor |
| --- | --- |
| Small, focused prompt with an icon tile (unlock, create) | `Form` with `FormKind.HERO` or `HERO_COMPACT` and a hero |
| Dialog with grouped fields (add host, new server, new tunnel) | `Form` with `FormKind.FULL`; each group is a `Section` holding a `Card` |
| Sheet attached to one of your windows | `Form` with `FormKind.SHEET`, shown with `DialogSpec` sheet presentation |
| Modal prompt inside a window, over its dimmed content (unlock, create) | Any `Form`, often `HERO`/`HERO_COMPACT`, shown with `DialogSpec.Presentation.OVERLAY` (SDK 0.15.4) |
| Window body with a pinned footer | `Form` with `FormKind.WINDOW` (one section) |
| Docked panel | `Panel` with a `Header`, optional search or navigation, a body and an optional footer |
| Sidebar, list and detail (a manager) | `Split` with `SplitKind.THREE_COLUMN` or `SIDEBAR_DETAIL` |
| Nothing to show yet, or a disconnected state | `Empty` with an icon, title, description and actions |

Inside cards:

- `CardKind.FORM` holds `FormRow`s: a label (with an optional caption below it) in the fixed label column beside
  its control, plus an optional trailing control such as a port field or a Browse button.
- `CardKind.SETTINGS` holds `SettingRow`s: a title and description with a trailing switch, dropdown or stepper.
  Mark a row `dependent` when it only applies while the row above is on.
- `CardKind.MIXED` combines a segmented choice, rows and settings in one card (an authentication card).

Controls: `Field` (TEXT, SEARCH, PORT, PATH with Browse, TEXTAREA, NUMBER with steppers or a slider), `Secret`
(masked input with reveal, confirmation, read-only detail with copy), `Choice` (DROPDOWN, SEGMENTED for two to four
options, SEARCHABLE, PICKER with a rich `PickerFace`, PREVIEWS), `Toggle` (a switch; settings never use checkboxes),
`Button` (one PRIMARY per surface, SECONDARY, GHOST, DESTRUCTIVE_GHOST, LINK), `MenuButton`, `Chip`, `StatusDot`,
`Progress`, `Strength`, `Icon`, `Text` with a semantic `TextRole`, `ItemList`, `Table`, `Tree` and `Breadcrumbs`.
Use `TextRole.MONO*` and `FieldPresentation.TECHNICAL` for technical values (hosts, ports, paths, commands).

### A grouped dialog

This is the shape of New File Server and Add Host: section labels over cards, a monospace preview beside the
actions, and a primary that is disabled while the draft is invalid.

<!-- example:uiform -->
```java
/** A New Server dialog: grouped cards, a monospace preview and one primary action. */
static final class ServerForm {
    private final java.util.function.Consumer<String> onSave;
    private final Runnable onCancel;
    private final UiView view;
    private String name = "";
    private boolean startup;

    ServerForm(dev.jasper.sdk.ui.UiKit kit, java.util.function.Consumer<String> onSave, Runnable onCancel) {
        this.onSave = onSave;
        this.onCancel = onCancel;
        view = kit.create(spec(), this::handle);
    }

    UiView view() { return view; }

    // Domain state changes, then the whole spec is reconciled by stable id: input, focus and selection survive.
    private void handle(UiEvent event) {
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals("name") -> { name = edited.text(); view.update(spec()); }
            case UiEvent.Toggled toggled when toggled.id().equals("startup") -> { startup = toggled.checked(); view.update(spec()); }
            case UiEvent.Action action when action.id().equals("save") -> onSave.accept(name.strip());
            case UiEvent.Action action when action.id().equals("cancel") -> onCancel.run();
            default -> { }
        }
    }

    private ViewSpec spec() {
        var general = new Nodes.Section("general", ControlState.READY, "Server", new Nodes.Card("general.card", ControlState.READY,
            Nodes.CardKind.FORM, List.of(new Nodes.FormRow("row.name", ControlState.READY, "Name", "",
                new Nodes.Field("name", ControlState.READY, Nodes.FieldKind.TEXT, name, "e.g. Downloads", "Name", ""), Optional.empty()))));
        var options = new Nodes.Section("options", ControlState.READY, "Startup", new Nodes.Card("options.card", ControlState.READY,
            Nodes.CardKind.SETTINGS, List.of(new Nodes.SettingRow("row.startup", ControlState.READY, "Start when Jasper starts", "",
                new Nodes.Toggle("startup", ControlState.READY, "Start when Jasper starts", startup, "startup", false), false))));
        // The primary stays disabled while the draft is invalid; the host greys it and ignores Enter.
        var valid = name.isBlank() ? new ControlState(true, false, false, "") : ControlState.READY;
        return new ViewSpec(new Nodes.Form("server", ControlState.READY, Nodes.FormKind.FULL, "New Server", Optional.empty(),
            List.of(general, options), Optional.of(new Nodes.Text("preview", ControlState.READY, "http://127.0.0.1:8080", Nodes.TextRole.MONO_CAPTION)),
            List.of(new Nodes.Button("cancel", ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, "cancel", ""),
                new Nodes.Button("save", valid, "Add Server", "", Nodes.ButtonKind.PRIMARY, "save", "")),
            "cancel"));
    }
}
```

Show it in a dialog with `context.windows().dialog(new DialogSpec("New Server", owner, true))`,
`dialog.setContent(form.view())` and `dialog.show()`. Validate cheaply in the handler to drive the primary's
enabled state; keep authoritative checks (files, ports in use) in the save operation and report failures through
the relevant control's `ControlState.error`.

### A docked panel

The header holds the title, an inline count and up to one primary action; the host adds More and Collapse and owns
docking. Search sits under the header; the body is a list, a browser or an `Empty` that fills and centers in the
remaining space; a persistent footer can hold settings rows. Collection-empty hides search and the count; a filter
with no matches keeps search so the user can clear it.

<!-- example:uipanel -->
```java
/** A docked panel: inline count, search, two-line rows with a chip and a row switch, and a full-body empty state. */
static ViewSpec serversPanel(List<String> names, Set<String> running, String query) {
    var header = new Nodes.Header("servers.header", ControlState.READY, "File Servers", OptionalInt.of(names.size()),
        Optional.empty(), Optional.of(new Nodes.Button("servers.add", ControlState.READY, "", "add", Nodes.ButtonKind.GHOST, "add", "New server")),
        List.of());
    if (names.isEmpty())
        return new ViewSpec(new Nodes.Panel("servers", ControlState.READY, header, Optional.empty(),
            new Nodes.Empty("servers.empty", ControlState.READY, new Nodes.Icon("servers.empty.icon", ControlState.READY, "server",
                Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "File servers"), "No File Servers",
                "Share a local folder over HTTP with devices on your network.",
                List.of(new Nodes.Button("servers.share", ControlState.READY, "Share Folder...", "", Nodes.ButtonKind.SECONDARY, "add", ""))),
            Optional.empty()));
    var rows = new ArrayList<ListItem>();
    for (String name : names) {
        if (!name.contains(query)) continue;
        rows.add(new ListItem(name, Optional.of(new Nodes.Icon(name + ".icon", ControlState.READY, "folder.filled", Nodes.Hue.BLUE, Nodes.IconSize.CONTROL, "Folder")),
            List.of(new Nodes.Text(name + ".name", ControlState.READY, name, Nodes.TextRole.BODY)),
            List.of(new Nodes.Chip(name + ".protocol", ControlState.READY, "HTTP", Nodes.Hue.BLUE),
                new Nodes.Text(name + ".endpoint", ControlState.READY, "127.0.0.1:8080", Nodes.TextRole.MONO_SMALL)),
            // A row switch is its own focusable control: toggling it never activates the row.
            List.of(new Nodes.Toggle(name + ".run", ControlState.READY, "Run " + name, running.contains(name), "run", true))));
    }
    var search = new Nodes.Field("servers.search", ControlState.READY, Nodes.FieldKind.SEARCH, query, "Search servers", "Search servers", "");
    return new ViewSpec(new Nodes.Panel("servers", ControlState.READY, header, Optional.of(search),
        new Nodes.ItemList("servers.rows", ControlState.READY, Nodes.ListKind.DOUBLE, rows, Set.of(), "open", List.of(),
            Nodes.SelectionMode.SINGLE, Optional.empty()),
        Optional.of(new Nodes.SettingRow("servers.follow", ControlState.READY, "Start servers with Jasper", "",
            new Nodes.Toggle("servers.autostart", ControlState.READY, "Start servers with Jasper", true, "autostart", true), false))));
}
```

Register the panel with `context.panels().register(new PanelSpec(...), host -> context.kit().create(...))` and keep
your model outside the view: Jasper asks for one instance per window. Two-line rows in a panel are compact: the name
leads the title line; a `Chip` and a `MONO_SMALL` endpoint form the second line; one compact `Toggle` trails. A row
switch is its own control: toggling it never activates the row, and its displayed state follows what your domain
confirms (set `pending` while a start or stop is in flight).

### Other building blocks

- **Route strip.** `Stack` with `LayoutRole.ROUTE`: equal columns, each a child stack of an `Icon` beside a description
  and a `MONO_SMALL` line; the host draws the arrows (New Tunnel's "This computer → Via → Target").
- **Rich dropdown face.** A DROPDOWN or PICKER `Choice` can carry a `PickerFace` (tile, primary text, caption) that
  describes the selected option, as New Tunnel's Via Host does.
- **Credential pickers.** Use the Vault API's anchored picker rather than building one; it brings search, groups,
  None and the creation footer.
- **Empty states.** `Empty(icon, title, description, actions)`; actions may be buttons, a `MenuButton` (Choose
  Session) or none.
- **Settings pages.** Declared settings render as the standard Settings page; a custom settings panel is a
  `Stack` with `LayoutRole.SETTINGS_PAGE` of `Section`s over SETTINGS cards (see
  [Declared settings](plugin-authoring.md#declared-settings)).

## Icons

Icons are semantic names (`"add"`, `"lock"`, `"server"`, `"folder.filled"`, `"key"`, `"laptop"`, `"target"`, ...);
the host draws the kit's artwork at the role's size and tints it from the theme. A glyph the kit lacks can be
registered as monochrome path artwork with `UiKit.registerIcon` (see [icons](sdk-icons.md)). Your plugin's own
identity art (Plugins page, Settings Tools, notification headers) comes from the manifest's `icon`, `icon_hue` and
`icon_selected_ink` keys described in the authoring guide. Category hues (`Hue.BLUE`, `GREEN`, `AMBER`, `PURPLE`,
`NEUTRAL`) map to the theme's hue roles, never to fixed colors.

## Colors for specialized content

Only custom content reads colors. Use `context.appearance().color(SemanticColor.X)` for canonical roles (including
the ten Slate Steel roles of SDK 0.15.5: raised fields, the selected control, the tab strip, empty tiles, the segmented
track, strong secondary text and the secondary button edge), or the
`editorPalette()`, `workspacePalette()` and `syntaxPalette()` for document widgets, and repaint in
`appearance().onChanged(...)` (chrome) or `onWorkspaceChanged(...)` (workspace content, including opacity). Never cache
a color across a change. A role that carries opacity is painted as given; the kit applies treatments such as focus
rings and hovers on top. Content in the workspace area of a translucent window paints its background with
`workspaceOpacity(component)`.

## Testing

`jasper-sdk-testkit` implements the same kit contract headlessly: `new FakeKit(pluginId).create(spec, handler)`
returns a `FakeKitView` whose `spec()` is the current tree and whose `emit(event)` delivers user input. Assert on
descriptors (which rows exist, which action is primary, which state is enabled), not on pixels; the host's own tests
cover rendering. `FakePluginHost` runs a whole plugin. Both examples above are compiled and tested in
`jasper-app/src/test/java/dev/jasper/app/documentation/PluginUiExamplesTest.java`.
