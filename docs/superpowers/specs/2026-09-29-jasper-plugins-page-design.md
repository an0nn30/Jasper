# The Plugins page as cards and details

Status: approved design, 2026-09-29. It replaces the list-and-text view of Settings › Plugins (File → Manage
Plugins…) with an IntelliJ-style page: a scrolling list of plugin cards beside a details pane. Built on the Settings
dialog (sub-project B) and plugin settings (sub-project C), both on local `main`.

## Outcome

Settings › Plugins shows each installed plugin as a card, grouped by where it came from, with an icon, its name,
version and vendor, a badge when it needs attention, and a checkbox that enables or disables it. Selecting a card shows
its details: a large icon, name, vendor and homepage link, an Enable/Disable button with a menu of the plugin's other
actions, and Overview and Details tabs. Plugins may name an icon and a homepage in `plugin.toml`; the bundled plugins
ship icons.

## Decisions (user, 2026-09-29)

1. **Icons** come from an optional `icon` key in `plugin.toml`, with a default icon for plugins without one; the
   bundled plugins ship an icon each.
2. **Tabs:** Overview (the description and an optional homepage link, from a new `homepage` key) and Details (status,
   capabilities, requirements, exports and locations). No What's New, Reviews or Marketplace.
3. **Cards are real components** (a panel per card with a real checkbox), as in IntelliJ, not a list renderer.

## Alternatives considered

- **A generic icon for every plugin**, or a coloured letter tile: no manifest change, but cards look alike. Rejected.
- **A What's New tab** from a changelog in the jar, or a single pane without tabs. Rejected for now.
- **A `JList` with a card renderer**: selection and keyboard movement for free, but the checkbox would only be painted,
  so clicks need hit-testing and accessibility suffers. Rejected.

## Design

### 1. Layout

- **Top bar:** a search field ("Search plugins") that filters the cards as you type, and a gear menu holding "Install
  from Zip…". The existing banner (restart required, safe mode, pending changes) and notice stay below it.
- **Card list** (left, scrolling vertically only): cards grouped under headers, in this order, each group shown only
  when it has cards:
  - "Development" (a plugin loaded from `--plugin-dev`)
  - "User-installed (n of m enabled)"
  - "Bundled (n of m enabled)"

  Within a group, cards are sorted by name, ignoring case. A card shows:
  - the plugin's icon at 40 px;
  - its name in bold, with version and vendor below it in the secondary text colour;
  - at most one badge, the first that applies, in this order:
    1. "Remove at restart"
    2. "Install at restart"
    3. "Restart to apply"
    4. "Needs review"
    5. "Failed"
    6. "Skipped"
  - an enable checkbox on the right.

  The selected card has the list's selection background.
- **Details pane** (right):
  - **Header:** the icon at 64 px and the name in a larger bold font. Below them, the vendor, and a "Plugin homepage ↗"
    link when the plugin has a homepage; the link opens in the browser. Then the Enable/Disable button with a ▾ menu,
    and the version.
  - **Overview tab:** the description as wrapping text; "No description." when it has none.
  - **Details tab**, under headings:
    - Status: the state label, the reason when there is one, and the error count when above zero.
    - Capabilities: in plain words, as the review dialog shows them.
    - Requires: each required and optional plugin, with its version range.
    - Exports: the exported packages.
    - Locations: the plugin's folder, settings file and data folder.
- **The ▾ menu** holds Open Settings, Open Folder, Open Data, a separator, and Remove… (or Discard Install for a
  pending install). It replaces the list's right-click menu; right-clicking a card opens the same menu. Items that do
  not apply are disabled, following the rules the current menu uses.
- With no plugins installed, the list shows "No plugins installed" and the details pane is empty.

### 2. Behaviour

- `PluginManager` keeps its logic: consent, review, removal, install, the restart flow. It drives the view through the
  same calls as today: `show(snapshot)`, `busy`, `message`, `notice` and `banner`.
- `show(snapshot)` rebuilds the cards. It keeps the selected plugin (by id) and the search text; when the selected
  plugin is gone, it selects the first visible card.
- **The checkbox shows the snapshot's state and nothing else.**
  - Clicking it calls the same handler as the Enable/Disable button.
  - A user plugin that needs review opens the review dialog, as the button does today.
  - Whatever the outcome (done, cancelled, refused or failed), the next snapshot sets the checkbox.
  - A plugin that cannot be toggled has its checkbox disabled, with the reason as its tooltip.
- **Busy:** while an operation runs, every checkbox, the Enable/Disable button, the ▾ menu and Install from Zip… are
  disabled.
- **Search** matches the name, vendor, description and id, ignoring case. Groups with no matches are hidden, and their
  counts reflect all their plugins, not the matches. With no matches the list shows "No plugins match". Selection moves
  to the first visible card when the selected one is filtered out.
- **Keyboard:**
  - Up and Down move the selection between visible cards.
  - Space toggles the selected card's checkbox.
  - Shift+F10 or the context-menu key opens the card's menu.
  - Cards are one focus stop; Tab moves on to the details pane.
- The Settings dialog's search keeps reaching the page through `PluginsPage.searchTerms`, as today.
- All plugin-supplied text (name, vendor, description, reason, homepage) is shown as plain text, never HTML.

### 3. Manifest keys

Two optional keys join `plugin.toml`:

```toml
icon = "icons/remote.svg"             # an SVG or PNG inside the plugin's jars
homepage = "https://example.dev/tool" # http or https
```

- **`icon`** is a path inside the plugin's jars, found as the `settings.toml` template is: the first jar that has the
  entry wins.
  - It must end in `.svg` or `.png` and be at most 256 KiB.
  - A missing, oversized or unreadable icon is logged at DEBUG, and the card uses the default icon.
  - A bad icon never stops a plugin from loading.
- **`homepage`** must be an absolute `http` or `https` URI. Any other value is ignored with a WARNING in the log naming
  the plugin; the plugin still loads, without a link.
- **Compatibility:** the descriptor parser still rejects unknown keys, so a plugin using `icon` or `homepage` fails on an
  older Jasper. `JasperSdk.VERSION` becomes `0.9.2` to mark manifests that may carry these keys, and a plugin that uses
  them declares `sdk = ">=0.9.2, <0.10"`. The SDK's Java API does not change.
- `docs/plugin-authoring.md` documents both keys.

### 4. Icon loading and drawing

- When building its rows, `PluginRuntime` reads each plugin's icon bytes, cached by plugin id and version so a refresh
  does not reopen jars. Reading an icon runs no plugin code, so it is safe for a user plugin that is still waiting for
  review.
- `PluginRuntime.Row` gains:
  - `homepage`: a string, empty when unset or ignored;
  - `icon`: an `Optional` of a small value holding the image bytes and whether it is SVG.
- `dev.jasper.app.pluginmanager` draws icons:
  - SVG through FlatLaf's `FlatSVGIcon`, which the application already uses;
  - PNG through `ImageIO`, scaled smoothly;
  - at 40 px on cards and 64 px in the details header, following the UI scale;
  - greyed for a plugin that is disabled, or that is not running because it needs review, failed or was skipped.
- **The default icon** is IntelliJ's plugin logo, already bundled with the application
  (`icons/intellij/nodes/pluginLogo.svg`, with its dark variant).
- An icon that fails to decode falls back to the default and is logged at DEBUG.

### 5. Bundled plugins

- Editor, Shell History, Snippets, Credential Vault, Remote and Sample each ship an SVG under
  `src/main/resources/icons/`, chosen from the IntelliJ icons the application bundles (Apache-2.0), and set `icon` in
  their `plugin.toml`.
- Each declares `sdk = ">=0.9.2, <0.10"` and bumps its patch version.
- Remote's `homepage` points at the project's repository.

### 6. Architecture

- `PluginManagerPanel` is replaced by a view made of smaller classes in `dev.jasper.app.pluginmanager`:
  - the card list, with selection, keyboard handling and search;
  - a card;
  - the details pane;
  - the icon drawing.
- `PluginManager` changes only where it wires the new view.
- The descriptor parser (`PluginDescriptor`, `DescriptorParser`) and `PluginRuntime` in `dev.jasper.app.plugins` gain
  the two keys and the icon reading.
- No SDK types appear outside `dev.jasper.app.plugins`, and `verifyApplicationArchitecture` passes unchanged.

## Testing

TDD, headless, no GUI launched by an agent.

- **View (`PluginManagerPanelTest`, rewritten):**
  - group headers and counts;
  - card order;
  - badges and their precedence;
  - that the checkbox calls the toggle handler, and returns to the snapshot's state after a snapshot that did not
    change it;
  - that a plugin that cannot be toggled has its checkbox disabled;
  - search filtering, hidden empty groups and "No plugins match";
  - that selection survives `show` and moves when filtered out;
  - keyboard Up, Down and Space;
  - that the details header shows the homepage link only for an `http` or `https` URI;
  - the Overview and Details tab contents;
  - the ▾ menu items and their enabled states per plugin state;
  - that busy disables the controls;
  - that plugin text is not rendered as HTML.
- **Parser:**
  - `icon` and `homepage` are accepted;
  - a non-http(s) homepage is dropped with a warning;
  - an icon path not ending in `.svg` or `.png` is dropped;
  - other unknown keys are still rejected.
- **Runtime:**
  - a compiled fixture plugin with an icon in its jar puts the bytes in its row;
  - a missing, oversized or corrupt icon gives none;
  - the bytes are read once per plugin version.
- **Icons:** an SVG and a PNG draw at 40 and 64 px; a broken image draws the default; a disabled plugin's icon is
  greyed.
- **Bundled plugins:** every bundled plugin's staged row has an icon.
- **Preview:** `settingsPreview` renders the Plugins page from a fixture set of bundled, user, needs-review and failed
  plugins, with a selected card and the Details tab, in dark and light, at the default font and at 18 pt.
- **Gate:** `./gradlew check` green.
- **GUI checklist for the user:**
  - cards and groups;
  - toggling a plugin from its card;
  - the review dialog from a checkbox;
  - the ▾ menu and the right-click menu;
  - search;
  - the homepage link;
  - icons in dark and light themes.

## Non-goals

- A marketplace, reviews, ratings, downloads or update checks.
- A What's New tab or changelogs.
- Plugin icons anywhere but the Plugins page.
- Changing how enabling, consent, removal or installs work.

## Workflow

Branch `claude/settings-polish` in `.worktrees/settings-polish`, which also holds the Settings layout and theme-picker
fixes (222a15e2). One plan via `superpowers:writing-plans` (TDD, one commit per task). Record deviations in the plan's
status banner and `docs/STATUS.md`. Merge only with the user's agreement; do not push without asking.
