# Building plugin UI

Jasper has one UI kit (`jasper-ui`). The application and every bundled plugin build their standard UI with it, so a
plugin's dialogs, tool windows and settings look exactly like Jasper's own, in whichever look and theme are active.
Plugins never see the kit's Swing classes: they describe their UI with immutable SDK values,
[`Nodes`](sdk-reference.html#dev.jasper.sdk.ui.Nodes), and Jasper turns them into kit controls. This guide shows how to
use that vocabulary; [Writing a Jasper plugin](plugin-authoring.md) covers where views go (tool windows, content
tabs, dialogs, popovers, the toolbar, Settings), lifetimes and threading.

## What the host owns, and what you own

| You describe | Jasper decides |
| --- | --- |
| Which surface (form kind, panel, split), its sections, rows and controls, their order and stable ids | Every size, inset, gap, radius, font and icon geometry |
| Text, values, options, enabled, visible, busy and error states, the one primary action | Colours, from the active theme's semantic roles |
| What an event means for your domain, and the next spec | Focus, keys (Enter, Escape, Tab, arrows), where validation shows, popups, scrolling, accessible names |

Text you supply, tooltips included, is shown as written: a string starting with `<html>` is not rendered as HTML.

The SDK has no way to paint ordinary controls, choose their colours or fonts, or size them. Only specialised content
(an editor canvas, a chart, a terminal-like view) is the plugin's own component, registered as
[custom content](#custom-content); Jasper refuses an ordinary Swing control offered that way. In the Jasper repository,
`verifyPluginArchitecture` also rejects plugin code that styles or paints ordinary controls.

## The lifecycle of a view

1. Build a [`ViewSpec`](sdk-reference.html#dev.jasper.sdk.ui.ViewSpec) from your current domain state and call
   `context.kit().create(spec, handler)`, which returns an owned
   [`UiView`](sdk-reference.html#dev.jasper.sdk.ui.UiView).
2. The handler receives [`UiEvent`](sdk-reference.html#dev.jasper.sdk.ui.UiEvent)s on the UI thread.
3. Change your state and call `view.update(nextSpec)`. Jasper reconciles by node id: typed text, caret, selection,
   focus and scroll survive, and theme changes, including the user's interface font, re-render without your
   involvement.
4. Close the view, or let the tool window, content tab or dialog that holds it close it. Events after that are dropped.

Keep ids stable for the life of a control; changing a node's id or kind replaces the control.

- Every kit call is UI-thread only. A handler that throws is logged against the plugin; calling `update` from inside an
  update throws `IllegalStateException("Reentrant view update")`.
- A view mounts in one place at a time ("View is already mounted"), and only the plugin that made it can mount it
  ("Foreign view").
- `ViewSpec` checks the tree when it is made, and `create` and `update` check every icon name ("Unknown icon: …"); both
  throw `IllegalArgumentException`. Node ids are unique across the view; ids of items, options, crumbs, columns and
  rows are unique within their owner. A view is at most 128 levels deep and 100,000 nodes. Selected, expanded and
  chosen ids must name existing items.
- `UiView` also has `spec()`, the current descriptor; `focus(id)`; `reveal(id)`, which scrolls a node into view without
  moving the focus; `selectAll(fieldId)`, which selects a field's live text without editing or focusing it;
  `composing(fieldId)`, true while an input method holds uncommitted text; `anchor(id)` for
  [popovers](plugin-authoring.md#dialogs-overlays-and-popovers); and the [secret](#secrets) methods.

[`ControlState`](sdk-reference.html#dev.jasper.sdk.ui.ControlState)`(visible, enabled, busy, error)` is every node's
state; `ControlState.READY` is visible, enabled, not busy and without an error. `error` is validation prose the
control shows.

### Events

| `UiEvent` | Carries | Sent by |
| --- | --- | --- |
| `Action` | the action key the node was given, such as a button's `action` | buttons, menu items, fields' Enter actions, forms' cancel, list and table activation |
| `Edited` | the node id and the text | fields, searchable choices with free entry, picker menus' queries, breadcrumbs while editing |
| `Selected` | the node id and the selected ids | choices, lists, tables, trees, breadcrumbs, a picker menu's active row |
| `Toggled` | the node id and the new state | toggles |
| `Expanded` | the tree, the item and whether it is now open | trees |
| `Removed`, `Reordered` | the list, the item and, for a move, its new index | ORDERED lists; the owner confirms in its next update |
| `SecretChanged` | the node id only, never the value | secrets |
| `Clicked` | the owner and the row | a plain left click on a row of a list, table or tree, after its `Selected` |
| `Sorted` | the owner and the column | a click on a table's or tree's column header; the owner orders the rows |

## Compositions

Pick the composition from what the surface is, not from how it should look:

| Surface | Descriptor |
| --- | --- |
| Small, focused prompt with an icon tile (unlock, create) | `Form` with `FormKind.PROMPT` or `PROMPT_COMPACT` and a hero |
| Dialog with grouped fields (add host, new server, new tunnel) | `Form` with `FormKind.FULL`; each group is a `Section` holding a `Card` |
| Sheet attached to a Jasper window or one of your content tabs | `Form` with `FormKind.SHEET`, shown with `DialogSpec.Presentation.ATTACHED_SHEET` |
| Modal prompt inside a window, over its dimmed content | Any `Form`, often `PROMPT` or `PROMPT_COMPACT`, shown with `DialogSpec.Presentation.OVERLAY` |
| Window-sized dialog with a pinned footer, such as the Credential Vault manager | `Form` with `FormKind.WINDOW` (exactly one section, no hero), shown in a modeless dialog, which opens at a window's size, can be resized and remembers its bounds |
| Tool window | `Panel` with a `Header`, optional search or navigation, a body and an optional footer |
| Sidebar, list and detail (a manager) | `Split` with `SplitKind.THREE_COLUMN`, or `SIDEBAR_DETAIL` without the sidebar |
| Document in a content tab | `Split` with `SplitKind.DOCUMENT` around a `Custom` |
| Settings page of your own | `Stack` with `LayoutRole.SETTINGS_PAGE` |
| Controls in the main toolbar | An INLINE `Stack` from a `ToolbarItem.view` factory: LABEL `Text`, DROPDOWN, SEARCHABLE or PICKER choices, `MenuButton`s, `Separator`s, at most one PRIMARY `Button` and icon-only GHOST `Button`s, which draw a 16-pixel glyph; see [the toolbar](plugin-authoring.md#the-toolbar) |
| Nothing to show yet, or a disconnected state | `Empty` with an icon, title, description and actions |

Inside cards:

- `CardKind.FORM` holds `FormRow`s: a label, with an optional caption under it, in a label column beside its control,
  and an optional trailing control such as a port field or a Browse button.
- `CardKind.SETTINGS` holds `SettingRow`s: a title and description with a trailing switch, dropdown or stepper. Mark a
  row `dependent` when it applies only while the row above is on.
- `CardKind.MIXED` combines an exclusive choice, rows and settings in one card, as an authentication card does.

Descriptors say what a control means, and the look draws it. GHOST is a quiet button, `ButtonPresentation.HELP` opens
help and `FormKind.PROMPT` is a short, focused question. A `Section` holding a `Card` is a titled group. `FormRow` and
`PropertyRow` labels carry no colon: the look punctuates them. `Empty`'s icon shows only where the look draws an icon
tile, and a count in `PanelHeader.detail` is passed as digits. `DialogSpec.Presentation`, the list kinds' densities,
`ScrollPolicy`, `PickerDensity`, `Toggle.compact` and STEPPER versus SLIDER are hints. `ScopeSpec.monospaceRows` and the
MONO text roles mean data values in the data face, not the terminal font. A menu from `Menus.create` goes where the look
puts plugin menus, and the TAB items may appear in a Window menu.

Under the Enterprise look ([the Enterprise look](enterprise.md)) the same descriptors and events draw classic controls:
an EXCLUSIVE choice draws radios, TABS draws folder tabs, a STEPPER or SLIDER draws a spinner, a Toggle draws a
checkbox, a Section with its Card draws a titled box, a Table draws a header band, zebra rows and banded headings, a
Tree draws ▶ and ▼, a PROMPT form puts its title in the dialog's header strip and its tile beside the message, an Empty
state shows its text and one button (its visible PRIMARY action, or else its first visible one) without a tile, a Chip
is boxed in upper case, a ROUTE stack draws edged cells, an ORDERED list's commands move to a side column that acts
on the selection, and in a toolbar view a DROPDOWN with a `PickerFace` draws as IntelliJ's run-configuration combo (its
icon, the name cut with an ellipsis at 240 pixels, an arrow box). A tool window's stripe label starts with its icon,
and six icon names draw IntelliJ's classic art in the toolbar, toolbar views and tool-window headers
([plugin icons](sdk-icons.md#the-enterprise-looks-classic-art)).

### A grouped dialog

This is the shape of New File Server and Add Host: section labels over cards, a monospace preview beside the actions,
and a primary that is disabled while the draft is invalid.

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

Show it with `context.windows().dialog(new DialogSpec("New Server", owner, true))`, `dialog.setContent(form.view())` and
`dialog.show()`. In a form, Enter presses the one PRIMARY button unless the form's `FormSubmission` is `NONE`, the
focused control takes Enter itself (a field whose Enter reports its `trailingAction`, a secret with a submit action, a
`PICKER` choice, editing breadcrumbs, or a list, table or tree row with an activate or Enter action), a popup is open,
the focus is in a text area, or the form or a visible, enabled control in it is busy or shows an error; Escape runs the
form's `cancelAction`. Validate cheaply in the handler to drive the primary's enabled state; keep authoritative
checks (files, ports in use) in the save, and report failures through the relevant control's `ControlState.error`.

### A docked panel

In a tool window, a `Panel`'s `Header` is the tool window's header: it holds the title, an inline count and at most one
primary action. Jasper shows the [`PanelHeader`](plugin-authoring.md#tool-windows)'s detail and status dot after the
title, ends the row with its actions and then Options and Hide, and owns docking. Search sits under the header; the
body is a list, a browser or an `Empty` that fills and centres in the space left; a footer can hold setting rows. The
header's count is the whole collection's: a count of 0 hides the count and the search, while a filter that matches
nothing keeps them, so the user can clear it.

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

Register it with `context.panels().register(new PanelSpec(...), host -> context.kit().create(...))` and keep your model
outside the view: Jasper builds it in the main window the first time it shows, and again after the main window closed
and opened anew. Two-line rows in a tool window are compact: the name leads
the title line and keeps its width before whatever follows it; a `Chip` and a `MONO_SMALL` endpoint form the second
line; one compact `Toggle` trails. A row switch is its own control: toggling it never activates the row, and its state
follows what your domain confirms (set `pending` while a start or stop is in flight). A note above an `Empty` body, such
as a settings-file problem, goes in a `SplitKind.DOCUMENT` split with the `Empty` last; a persistent footer is a
VERTICAL `Stack` whose last child is a `SettingRow`.

### Settings pages

Declared settings render as a generated page ([declared settings](plugin-authoring.md#declared-settings)). A page of
your own is a `Stack` with `LayoutRole.SETTINGS_PAGE` whose children are `Section`s, each holding a `Card` of kind
`SETTINGS`: the standard rows `host.row(setting)` gives for declared settings, keeping the control in the trailing slot
with an icon-only Reset while the value differs from its default or an edit is pending (under the Enterprise look,
Reset to Default is in the row's menu instead), and `SettingRow`s of your own. Use the standard
`Empty` where a collection of your own has nothing to show. `SettingRow.highlighted` and `FormRow.highlighted` mark a
row that matches the Settings search, and `SettingRow.titleNote` annotates its title.

### Documents

A document in a content tab is a `Split` with `SplitKind.DOCUMENT`, top to bottom: every visible child takes its
preferred height except the first visible `Custom`, `Empty` or `Split`, which fills the height left over, and hidden
children take no space. The other children are the document's bars: an INLINE `Stack` among them, directly or as a row
of a VERTICAL group, is one compact bar row whose first field or text fills the width the controls after it leave; a
group stacks its rows without gaps and shows a passive (`SelectionMode.NONE`) list's rows as lines of text; a hairline
separates neighbouring children. An INLINE `Stack` directly in the split whose first child is an empty `CAPTION` text,
followed by one or more `Button`s and nothing else, is an action bar: the empty text pushes the buttons to the trailing
edge, and while the document is narrower than the buttons the row scrolls sideways, bringing a button into view when it
takes the focus. An Editor tab is a document: the Markdown view buttons (an action bar, shown for Markdown documents), a
row offering to load web images or retry failed ones, a preview failure row, a banner row, its `Custom` text area, which
also holds the Markdown preview, a loading `Empty`, the Vim bar and the find bar.

## Building blocks

Every node has an `id` and a `state`; the other components are listed per node. Most nodes have shorter constructors
that fill in the defaults; the [SDK reference](sdk-reference.html#dev.jasper.sdk.ui.Nodes) lists them.

### Text and status

- [`Text`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Text)`(text, role)`: a
  [`TextRole`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.TextRole) of `PROMPT_TITLE`, `DETAIL_TITLE`,
  `WINDOW_TITLE`, `PANEL_TITLE`, `BODY`, `BODY_STRONG`, `LABEL`, `DESCRIPTION`, `SUPPORTING`, `CAPTION`,
  `SECTION_LABEL`, `COUNT`, `CHIP`, `MONO`, `MONO_SMALL` or `MONO_CAPTION`. The MONO roles mark data values (hosts,
  ports, paths, commands) in the data face. A `severity` draws the text in that severity's colour (INFO the accent,
  WARNING and DANGER the status colours); a `caret`, for MONO roles only, draws an insertion bar after that many
  characters, as a command line shows its cursor.
- [`Icon`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Icon)`(name, hue, size, accessibleName)`:
  [`IconSize`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.IconSize) `CAPTION`, `CONTROL`, `RAIL`, `EMPTY`, `PROMPT` (the
  large icon of a prompt's header, a detail heading or a sheet's title) or `SWATCH`, a solid square of the hue at
  caption size, such as a protocol's colour beside its name (its hue is not NONE and it takes no severity). A `severity`
  tints the glyph instead of the hue.
- [`Chip`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Chip)`(label, hue)`, a short tag;
  [`StatusDot`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.StatusDot)`(kind)`: `SUCCESS`, `WARNING`, `DANGER` or `IDLE`;
  [`Progress`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Progress)`(fraction, label)`, a fraction from 0 to 1 or -1 for
  indeterminate; [`Strength`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Strength)`(filled, label)`, a meter of 0 to 4
  filled segments, as for a password.
- [`Hue`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Hue): `BLUE`, `GREEN`, `AMBER`, `PURPLE`, `NEUTRAL` or `NONE`,
  each mapped to the theme's hue roles.

### Buttons and menus

- [`Button`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Button)`(label, icon, kind, action, tooltip)` reports
  `Action(action)`. [`ButtonKind`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.ButtonKind): `PRIMARY` (one per
  surface), `SECONDARY`, `GHOST`, `DESTRUCTIVE_GHOST`, `LINK`. A button with a blank label needs an icon and a tooltip,
  which is its accessible name.
- Its [`ButtonPresentation`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.ButtonPresentation): `STANDARD`, the padded
  kit button; `EMBEDDED`, content-sized for an arrangement that supplies the spacing, such as a row of inline links,
  with the focus shown as a hairline; `CONTEXTUAL_EMBEDDED`, the same control drawn only while its row is selected,
  hovered or keyboard-active, or the button itself is hovered or focused (outside a row it is `EMBEDDED`); `HELP`, a
  button that opens help. Use `EMBEDDED` for a persistent action such as a confirmed favourite.
- An `iconSeverity` tints the button's glyph, as a favourite's warning star; a present `selected` makes a GHOST or
  SECONDARY button a toggle button, such as a search's match-case option: it shows the pressed state while true, and a
  click reports the `Action` for the owner to confirm in its next update.
- [`MenuButton`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.MenuButton)`(label, icon, kind, action, items, tooltip)`:
  with a blank `action` the whole button opens the menu; otherwise its main part reports `Action(action)` and its arrow
  opens the menu. Each
  [`MenuItem`](sdk-reference.html#dev.jasper.sdk.ui.MenuItem)`(id, label, icon, action, enabled, separator)` reports its
  own `Action`; `withShortcut(actionId)` shows the current binding of one of the plugin's actions, or of a Jasper action
  such as `new_tab`, beside the label, in the platform's notation (symbols on macOS, words such as Ctrl+Shift+P on Linux
  and Windows), following the user's rebinding, and nothing while it is unbound.

### Fields

[`Field`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Field)`(kind, value, hint, label, trailingAction)` reports
`Edited` as the user types; the label is its accessible name even where it is not shown.

- [`FieldKind`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.FieldKind): `TEXT`, `SEARCH`, `PORT`, `PATH` (with a
  Browse… button while `trailingAction` is not blank), `TEXTAREA` and `NUMBER`, which needs a
  [`Numeric`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Numeric)`(min, max, step, decimals)`: the field admits only
  numbers of that shape, and the arrow keys step it within the bounds.
- Keys: in a single-line field other than PATH, Enter reports `trailingAction` when it is not blank, and otherwise
  reaches the form (a PATH field's Browse… button reports it instead, and Enter in a TEXTAREA starts a new line);
  `alternateAction` is what Shift+Enter reports in a single-line field (a find field's Previous); `cancelAction` is
  what Escape reports. A `listId` naming an `ItemList` in the same view makes Up, Down, Page Up and Page Down move
  that list's selection while the focus stays in the field. A `TEXTAREA` takes neither `alternateAction` nor
  `listId`, and a `NUMBER` field no `listId`.
- [`FieldPresentation`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.FieldPresentation): `STANDARD` shows the label
  over a filled input (a SEARCH field never shows it); `EMBEDDED` leaves the visible label to the arrangement around
  it; `TRANSPARENT` is `EMBEDDED` with no fill, on a surface that is the field's backdrop; `TECHNICAL` draws a data
  value in the data face; `PLAIN_PATH` is a PATH with Browse and no leading folder; `STEPPER` and `SLIDER` are hints for
  a NUMBER.
- A `readOnly` field is focusable, selectable and copyable but never edited, so it reports no `Edited`; a STEPPER or
  SLIDER is never read-only.

### Secrets

[`Secret`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Secret)`(hint, label, revealGroup, kind, copyAction,
submitAction)` keeps its value out of the descriptor: it reports only `SecretChanged`, and the owner reads the value
with `view.withSecret(id, chars -> …)`, whose copy is erased when the callback returns.

- [`SecretKind`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.SecretKind): `INPUT`, masked single-line entry with a
  reveal control; `DISCREET_INPUT`, the same with the reveal control shown only while the field holds text or has the
  focus; `CONFIRMATION`, an input without a reveal control of its own, following its reveal group; `DETAIL`, a
  read-only stored value with reveal and an optional copy action; `MULTILINE`, an editable, wrapping secure note shown
  in clear, with no reveal group or copy action.
- `submitAction`, for the input kinds, is what Enter reports; the field keeps the key, so the form's primary is not
  pressed.
- `view.clearSecret(id)` erases the stored value; `view.replaceSecret(id, chars)` copies a value in without reporting
  an edit (the caller erases its own array).

### Choices and pickers

[`Choice`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Choice)`(kind, options, selectedId, label, action)` picks among
[`Option`](sdk-reference.html#dev.jasper.sdk.ui.Option)s, each an id, a label and optionally a hue, an icon, a
description and a colour preview. The label is the accessible name.

| [`ChoiceKind`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.ChoiceKind) | Is | Reports |
| --- | --- | --- |
| `DROPDOWN` | A closed control with a popup list. It may carry a `PickerFace` that describes the selected option richly, such as a saved host's tile, name and endpoint. | `Selected` |
| `EXCLUSIVE` | Two to four options, all visible. | `Selected` |
| `SEARCHABLE` | A dropdown that filters as the user types, configured by a `ChoiceSearch`. | `Selected`; with free entry, `Edited`, and Enter reports `action` |
| `PICKER` | A rich face (a `PickerFace`, with an optional icon cue) whose click, Enter or Down reports `Action(action)`: the owner opens a popover, usually with a `PickerMenu`. | `Action` |
| `PREVIEWS` | Options shown with their colour previews. | `Selected` |
| `TABS` | Two or more views of one thing (Overview and Details); compose the chosen content below it. | `Selected` |

- [`ChoiceSearch`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.ChoiceSearch)`(pinned, noMatches, entry, placeholder)`:
  the first `pinned` options always show; `noMatches` is the line shown when typing matches nothing. A present `entry`
  makes the value free text, the options suggestions: the choice then has no selected id. A `placeholder` shows, in the
  placeholder role, while a free entry is empty (for example the group a blank value files a host under); it is never a
  value.
- [`PickerFace`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.PickerFace)`(primary, caption, icon, hue, density)`: an
  empty `primary` is the placeholder face; `technicalCaption` draws the caption, such as an endpoint, in the data face.
  The owner keeps the face describing the selected option.
- [`PickerMenu`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.PickerMenu) is a searchable menu: a `query` and its
  `placeholder`, [`PickerItem`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.PickerItem)s (a label, a caption, an icon,
  a hue, and a kind, `VALUE` or `UTILITY`), the checked value (`checkedId`) and the keyboard-active row (`activeId`),
  which are distinct, a `noMatches` line, a footer (`footerLabel`, `footerAction`) and a `cancelAction`. It reports
  `Edited` for the query and `Selected` as the active row moves; Enter or a click reports `Action` with the row's id or
  `footerAction`, and Escape reports `cancelAction`. Credential Vault's credential picker is one, in a popover over the
  requesting plugin's control.

### Toggles

[`Toggle`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Toggle)`(label, checked, action, compact)` is a boolean, drawn as
a switch or a checkbox. It reports `Toggled` with its node id and the opposite of `checked`, which is always the
confirmed state. `pending` means a change is in flight, such as a tunnel starting: the toggle stays operable, and the
default look shows the switch's knob midway on the warning colour. `tooltip` replaces the label as the tooltip; a
validation error still takes precedence.

### Layout

[`Stack`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Stack)`(role, children)` arranges children by
[`LayoutRole`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.LayoutRole). `INLINE`, `ACTIONS`, `FOOTER`, `NAVIGATION`
and `JOINED_ACTIONS` (buttons drawn as one joined group) lay them side by side; `VERTICAL` and `DETAIL` stack them.
`PROMPT_HEADER` is a prompt's header. `SUPPORTING` keeps leading adornments natural-sized beside a wrapping final text.
The `SETTINGS_` roles compose Settings pages, `SETTINGS_PAGE` being the page. A `cancelAction` is what Escape reports
from any control inside the stack that does not take Escape itself, as an inline find bar closes.

- **Route strip.** `LayoutRole.ROUTE` is a read-only route preview, such as New Tunnel's "This computer → Via → Target":
  its children are its steps, left to right, in equal columns, the kit's arrow before every step after the first. A step
  is a stack whose leading `Icon` shows on a tile in its hue, beside the step's lines of `Text` (a description and a
  `MONO_SMALL` line), which truncate within the column.
- [`Separator`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Separator) divides an INLINE or ACTIONS stack into groups:
  it stands between two items, never first, last or beside another, and hides while every item on one side of it is
  hidden.
- [`Split`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Split)`(kind, children)`, at least two children:
  [`SplitKind`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.SplitKind) `THREE_COLUMN` places a manager's sidebar, list
  and detail side by side; `SIDEBAR_DETAIL` is the same without the sidebar, its last child the detail; `DOCUMENT` is a
  [document surface](#documents).

### Forms, cards and sections

- [`Form`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Form)`(kind, title, hero, sections, preview, actions,
  cancelAction)`: [`FormKind`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.FormKind) `PROMPT`, `PROMPT_COMPACT`, `SHEET`,
  `FULL`, `SETTINGS` or `WINDOW`; at most one PRIMARY action. The `preview` stands at the footer's leading edge beside
  the actions; a one-line `footnote` takes the same place, drawn muted (an auto-lock note), so a form has one or the
  other. `FormSubmission.NONE` leaves Enter to the fields and buttons.
- [`Section`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Section)`(title, body)` and
  [`Card`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Card)`(kind, children)`, with
  [`FormRow`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.FormRow)`(label, caption, control, trailing)` and
  [`SettingRow`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.SettingRow)`(title, description, control, dependent)` as
  [above](#compositions).
- [`Empty`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Empty)`(icon, title, description, actions)`: nothing to show
  yet, or a disconnected state. Its actions may be buttons, a `MenuButton` (Choose Session) or none.
- [`Header`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Header)`(title, count, context, primary, overflow)` and
  [`Panel`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Panel)`(header, searchOrNavigation, body, footer)`: a header's
  primary is a `Button` or `MenuButton`, and a non-empty `overflow` adds a **More actions** menu of `MenuItem`s.

### Messages and properties

- [`Notice`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Notice)`(severity, text, actions)`: one sentence, shown after
  its severity's glyph and word ("Warning"), with at most one SECONDARY or LINK button. A Notice is a section of a form
  or a child of a VERTICAL stack, nowhere else.
- [`Properties`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Properties)`(title, rows)`: labelled values, such as a
  selected host's, under an optional heading (blank for none), with at least one
  [`PropertyRow`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.PropertyRow)`(label, value, technical, severity)`;
  `technical` puts the value in the data face and a severity colours it.

### Lists, tables and trees

- [`ItemList`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.ItemList)`(kind, items, selectedIds, activateAction,
  contextActions, selectionMode, …)` holds [`ListItem`](sdk-reference.html#dev.jasper.sdk.ui.ListItem)s, each with a
  leading node and title, subtitle and trailing nodes: `Text`, `Icon`, `Chip`, `StatusDot`, `Progress`, `Strength`,
  `Button`, `MenuButton`, `Toggle`, `Choice`, or INLINE stacks of those. A heading item is a section row with a title
  only, never selected. A row's own `contextActions` are offered on that row; the list's act on the selection.
  - [`ListKind`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.ListKind): `SINGLE`, `DOUBLE` and `COMPACT` are density
    hints; `ORDERED` lists, without headings, report `Removed` and `Reordered`; `RESULTS` is a one-line result list
    driven by a field elsewhere, as the command palette's: it never takes the focus, a left press activates a row, and
    it takes no row key actions.
  - [`SelectionMode`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.SelectionMode): `NONE` keeps rows readable but not
    selectable or activated; `SINGLE`; `MULTIPLE`, with Shift for a range and Cmd (Ctrl on Linux and Windows) or
    Space for one row.
  - Keys: double click reports `activateAction` and Enter `enterAction` (or `activateAction`); Delete and Backspace
    report `deleteAction`; while the rows have the focus, D reports `detailsAction`, the platform's Copy `copyAction`
    and Escape `cancelAction`. A blank action passes its key on. `visibleRows` is how many rows show before the list
    scrolls; `addAction` is a footer button; `emptyText` is the muted line shown while there are no items.
- [`Table`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Table)`(columns, rows, selectedIds, activateAction,
  contextActions, selectionMode, scroll)`: [`Column`](sdk-reference.html#dev.jasper.sdk.ui.Column)`(id, title, role,
  numeric, weight, sorted)`, the weight being the column's share of the width and `sorted` the sort indicator
  (ascending when true); [`TableRow`](sdk-reference.html#dev.jasper.sdk.ui.TableRow)`(id, cells, icon, muted)`. A column
  is a plain cell or a cell node (`cellNodes`), not both: `Text`, `StatusDot`, `Icon`, `Chip`, an INLINE stack of
  those, or a DROPDOWN `Choice` without a face, which reports `Selected` and draws without a border until hovered or
  focused. A `muted` row draws its text de-emphasised. `TableRow.heading(id, title, count, countNoun)` groups the rows
  below it and reads, for example, "6 groups"; it is never selected. With no rows the body shows `empty`, an `Empty`
  with at most one action, or the muted `emptyText`, below the column headers. `ScrollPolicy` is a hint: `STANDARD`
  reserves the scroll bar's width, `OVERLAY` shows a passing thumb over the rows while they scroll.
- [`Tree`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Tree)`(roots, expandedIds, selectedIds, activateAction,
  columns)` of [`TreeItem`](sdk-reference.html#dev.jasper.sdk.ui.TreeItem)s (title, icon, leaf, children, and
  optionally a count at the row's end, emphasis, a tooltip, a status dot, trailing values and a menu) reports
  `Expanded`. With `columns`, a header shows: the first column titles the items and each later one titles one of the
  items' `trailing` values, in order.
- [`Breadcrumbs`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Breadcrumbs)`(crumbs, label, editing, value, editAction,
  submitAction, cancelAction)`: a path of [`Crumb`](sdk-reference.html#dev.jasper.sdk.ui.Crumb)s that can turn into a
  typed path. A click on a crumb reports `Selected` with its id; a crumb's menu reports only its items' `Action`s; a
  click on the free area reports `editAction`. While `editing`, the kit shows a field holding `value`, reports typing
  as `Edited`, Enter as `submitAction` and Escape or leaving the field as `cancelAction`. When the crumbs do not fit,
  the leading ones slide out so the last stays visible.

### Custom

[`Custom`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.Custom)`(contentId, kind, accessibleName)` reserves a place for
[custom content](#custom-content).

## Icons

Icons are semantic names; Jasper draws the kit's artwork at the role's size and tints it from the theme. Every name a
view, action, status item, tool window, palette row, notification, session or content tab uses is checked: an unknown
name throws `IllegalArgumentException("Unknown icon: …")`. A glyph the kit lacks is registered as monochrome path
artwork with `context.kit().registerIcon(new IconAsset("<plugin id>:<name>", path))` and then used by that name.
[Plugin icons](sdk-icons.md) lists the [built-in names](sdk-icons.md#built-in-names) and the
[rules for registered artwork](sdk-icons.md#registering-artwork).

The plugin's own identity art (Settings › Plugins, and beside its notification groups in Settings › Appearance &
Behavior › Notifications) comes from the manifest's `icon`, `icon_hue` and `icon_selected_ink`
([the manifest](plugin-authoring.md#the-manifest)).

## Custom content

Specialised content is the plugin's own Swing component: put a `Nodes.Custom` in the view and register the component
for it with `view.registerContent(new CustomContent(contentId, kind, component, lifetime))`.

- [`CustomKind`](sdk-reference.html#dev.jasper.sdk.ui.Nodes.CustomKind): `EDITOR`, `TERMINAL`, `CHART` or `PREVIEW`;
  the `contentId` and kind must match a mounted `Custom` node, and a `contentId` appears once in a view.
- Jasper refuses ordinary controls ("Ordinary plugin controls require kit descriptors"): an `EDITOR` may be a
  `JTextArea`, `JTextPane` or `JEditorPane` or a purpose-built canvas; the other kinds only a canvas, a `JComponent`
  that is not a panel, text component, button, label, list, table, tree, combo box, spinner or slider. A `JScrollPane`
  is judged by its view. A component that already has a parent or an owner, or that the kit owns, is refused.
- Jasper sets the component's foreground (`text.primary`) and background (`surface.input`, at the workspace opacity) on
  every theme change and move, and makes it opaque only at full opacity. It also sets the component's font to the
  interface's body font when the content is registered, at every update of the view and at every theme or
  interface-font change, so a font the plugin sets on the registered component itself does not last: give a font of
  your own to a component inside it, as the Editor's `DocumentArea` does for its text area. A change of the interface
  font reaches the component as a `font` property change; content whose text should follow it reads `getFont()`. The
  `lifetime` closes when the `Custom` node leaves the view or the view closes.

### Colours for custom content

Only custom content reads colours, from [`Appearance`](sdk-reference.html#dev.jasper.sdk.ui.Appearance)
(`context.appearance()`):

- `color(SemanticColor.X)` on the UI thread reads one of the chrome theme's semantic roles
  ([creating themes](creating-themes.md)): [`SemanticColor`](sdk-reference.html#dev.jasper.sdk.ui.SemanticColor) has a
  constant for each, with its key, such as `TEXT_ONSELECTION` for text on `SELECTION_BG`, `TAB_INDICATOR` for the
  line along the active tab, or `SURFACE_TITLEBAR` and `TEXT_TITLEBAR` for the window header; `SemanticColor.of(key)`
  finds one by key.
- `editorPalette()`, `workspacePalette()` and `syntaxPalette()` (UI thread) are the workspace theme's colours for
  document widgets: the editor scheme, the workspace's surfaces, text, border, selection, hover, accent and status
  colours ([`WorkspacePalette`](sdk-reference.html#dev.jasper.sdk.ui.WorkspacePalette); its `selectionText` is
  `TEXT_ONSELECTION`), and a style per [`SyntaxRole`](sdk-reference.html#dev.jasper.sdk.ui.SyntaxRole).
  `variant()` and `workspaceVariant()` (any thread) say dark or light.
- `editorSchemes()` (UI thread) lists every installed theme's editor schemes, each theme's Dark and then Light one, as
  [`EditorScheme`](sdk-reference.html#dev.jasper.sdk.ui.EditorScheme) values with an id such as `jasper-meadow/dark`,
  for a plugin that lets its users pick a scheme of its own, as the Editor's **Color scheme** does; importing or
  removing a theme is a workspace change.
- Repaint in `appearance().onChanged(...)` for chrome and `onWorkspaceChanged(...)` for workspace content: the latter
  also fires when the colours change within one variant, when the workspace opacity changes and when the component
  moves to another window. For an editor-like control, reapply `editorPalette()` and `syntaxPalette()` on every
  workspace event, and keep the document and undo model when only its styles change. Never cache a colour across a
  change; close the subscriptions with the widget.
- A role that carries opacity is painted as given; the kit applies treatments such as focus rings and hovers on top.
- Content in the workspace area of a translucent window reads `appearance().workspaceOpacity(component)` on the UI
  thread (1 elsewhere, so tool windows and side panels stay opaque), fills its own background once with that alpha
  (`AlphaComposite.Src`, unantialiased rectangles) and keeps the components above it non-opaque. Keep selection,
  carets and bars solid. The Editor's `DocumentArea` is the reference.
- Content that needs the look's faces reads `appearance().presentation()` (any thread): `uiFace()` and `dataFace()`
  name the look's UI and data faces, empty for the platform's, and `bodySize()` is its body text size in points. They
  are the look's own; the user's interface font reaches custom content only as its font (above)
  ([adapting to the look](plugin-authoring.md#adapting-to-the-look)).
- Content that shows text in the terminal's font, as the Editor can, reads `appearance().terminalTypography()` (any
  thread): the saved terminal family, size, fallback fonts, ligatures and line height. Apply changes in
  `onTerminalTypographyChanged(...)` to a component inside the registered one (above)
  ([the terminal font](plugin-authoring.md#the-terminal-font)).

## Testing

`jasper-sdk-testkit` implements the same kit contract headlessly ([testkit](../jasper-sdk-testkit/README.md)).
`new FakeKit(pluginId).create(spec, handler)` returns a
[`FakeKitView`](sdk-reference.html#dev.jasper.sdk.testing.FakeKitView); like the real kit, it works only on the Swing
event dispatch thread, so run the test in `SwingUtilities.invokeAndWait`. A plugin's own kit under `FakePluginHost`
(`context.kit()`) is a `FakeKit` too.

- `spec()` is the current tree; `emit(event)` delivers user input (an edit of a read-only field is dropped); `type(id,
  text)` types into a field as a person would, and a read-only, hidden, disabled or busy field takes nothing;
  `setSecret(id, chars)` fills a secret and reports `SecretChanged`.
- `focused()`, `revealed()` and `fieldSelection(id)` show what `focus`, `reveal` and `selectAll` did;
  `setComposing(id, true)` plays an input method.
- [`FakeKit`](sdk-reference.html#dev.jasper.sdk.testing.FakeKit)'s `views()`, `icons()` and `failures()` list its views,
  registered icons and contained handler failures. `FakePluginHost` mounts views where Jasper would: `panelView`,
  `windowView`, `contentView`, `toolbarViews`, `settingsPanelView` and `popoverViews` return them.
- The fake kit gives registered custom content a plain 13-point `SansSerif` as its font; `contentFont(font)` replaces
  it and sets the content already registered, as a change of the user's interface font would.
- Assert on descriptors (which rows exist, which action is primary, which state is enabled), not on pixels; Jasper's own
  tests cover rendering. Both examples above are compiled and tested in
  `jasper-app/src/test/java/dev/jasper/app/documentation/PluginUiExamplesTest.java`.
