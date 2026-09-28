# Settings dialog and core pages

Status: Design approved section by section on 2026-09-28; awaiting review of this written spec.
This is sub-project **B** of the Settings work. It builds on sub-project A, the settings foundation
(`docs/superpowers/specs/2026-09-27-jasper-settings-foundation-design.md`, merged to local `main` as
a31b53cb): `CoreSettings.SCHEMA`, `SettingValues`, `ConfigService.save(SettingsEdit)`, `saveKeybindings`,
`migrate`. Sub-project C (SDK settings API and plugin pages) follows; see [Roadmap](#roadmap-c).

## Outcome

An IntelliJ-style **Settings** dialog replaces "open config.toml in an editor". It looks and behaves like
the user's reference screenshot of IntelliJ IDEA's Settings (Keymap page): a search field over a
category tree on the left, a page with a title and back/forward arrows on the right, and a footer with
help, Cancel, Apply and a default OK. Every core setting is editable in it; Keymap and Plugins become
pages of it; the View menu's chrome choices save instead of lasting one session.

## Decisions (user, 2026-09-28)

1. **Keymap and Plugins become pages inside Settings.** "Keyboard Shortcuts…" and "Manage Plugins…"
   (menus and palette) open Settings at those pages; their standalone windows go away.
2. **View menu chrome choices save.** Toolbar mode, the status-bar toggle and View › Terminal Colors
   write the saved setting. The four size dialogs are replaced by one **Window Settings…** item that
   opens Settings at the Window page. Per-pane font zoom and Show/Hide Jasper stay session-only.
3. **Look**: the reference screenshot (see Outcome). No visual-companion mockups.
4. **Page tree** as in [Page tree](#page-tree).
5. **Keymap edits wait for Apply**, like every other page. Plugins stays immediate (its operations
   already wait for a restart) and keeps its restart banner.
6. **Approach**: an app-native Swing dialog with a small page model (no new dependency; `GridBagLayout`
   forms; FlatLaf styling).

## Alternatives considered

1. **App-native Swing dialog with a page model** (selected).
2. The same with MigLayout: tidier form code, but a new dependency for about 150 lines of layout.
3. One property-grid `JTable` page: easy to search and mark, but nothing like the reference look.

## Page tree

```
Appearance & Behavior        (group page: links to its children)
    Appearance               ui.theme.name, ui.font.family, ui.font.size
    Window                   tab height, rail width, toolbar, toolbar height, status bar,
                             status bar height, initial columns, initial lines
    Notifications            notifications.long_command_seconds
    Command Palette          palette.max_results
    Desk Buddy               buddy.enabled
    System Settings          system.run_in_background; config.toml actions (see Pages)
Keymap                       (custom page: the shortcut editor)
Terminal                     scrollback, bell, on exit, copy on select, option as Meta,
                             dim inactive panes
    Font & Colors            terminal.font.* (5), terminal.colors
    Cursor                   terminal.cursor.shape, terminal.cursor.blink
    Shell                    terminal.shell.program, terminal.shell.args, terminal.env,
                             terminal.shell_integration
Plugins                      (custom page: the plugin manager)
Tools                        (hidden while empty; plugin pages arrive in C)
```

Every one of the 33 core settings appears on exactly one page (a test enforces this).

## Design

### 1. Shell and page model

**Packages.** New package `dev.jasper.app.settingsui`: the dialog shell, the draft, schema and group
pages, editors and `CorePages`. It knows nothing about shortcuts or plugins. `shortcuthelp.KeymapPage`
and `pluginmanager.PluginsPage` implement `settingsui.SettingsPage`; `application` wires them in.
Dependencies: `settingsui → settings, config, windows, platform`; `shortcuthelp → settingsui`;
`pluginmanager → settingsui`. `verifyApplicationArchitecture` keeps the graph acyclic.

**`SettingsPage`** (modelled on IntelliJ's `Configurable`; four real implementations: `SchemaPage`,
`GroupPage`, `KeymapPage`, `PluginsPage`):

```java
public interface SettingsPage {
    String id();
    String title();
    Optional<String> parent();
    List<String> searchTerms();              // labels, descriptions, keywords, choice names
    JComponent component();                  // built lazily on first visit
    boolean modified();                      // against the dialog's baseline
    void reset();                            // drop this page's pending edits
    CompletableFuture<Void> apply();         // Apply and OK
    void highlight(String query);            // search highlight on the page
    void dispose();
}
```

**Window.** A modal dialog titled "Settings", over the active terminal window; with no terminal window
open (resident Jasper), an application-modal dialog without an owner. Its bounds are remembered in
`ui-state.toml` under `app.settings`. Singleton: opening it again brings it forward (and selects the
requested page, if any).

**Layout** (the reference screenshot):
- **Left**: a search field; below it the page tree. Top-level entries bold; chevron disclosure;
  full-width rounded selection; a page with unapplied changes shows its title in the theme's modified
  blue. The tree generalises `ShortcutTree`'s renderer, hit-testing and disclosure bookkeeping.
- **Right header**: the breadcrumb path (`Terminal › Font & Colors`) at left; back/forward arrows at
  right, over the pages visited since the dialog opened.
- **Right body**: the page component in a borderless scroll pane, under any banner (section 3).
- **Footer**: a "?" button at left opening the configuration guide in the browser, at the section the
  page's spec names (`https://github.com/an0nn30/moray/blob/main/docs/configuration.md#<anchor>`; the
  application does not ship its docs); **Cancel**, **Apply** and a default **OK** at right. Apply is enabled only while a page is
  modified. Escape means Cancel.
- Closing with unapplied changes (Cancel, Escape, the close button) asks *Discard N unsaved changes?*

**Opening**
- `Cmd+,` (`ActionId.OPEN_SETTINGS`) and the macOS application menu's Settings…
  (`Desktop.setPreferencesHandler`, when supported) open the last page visited, or Appearance first.
- `app.shortcuts` ("Keyboard Shortcuts…") opens Keymap; `plugins.manage` ("Manage Plugins…") opens
  Plugins; View › **Window Settings…** opens Window.

### 2. Draft, generated pages and editors

**`SettingsDraft`** (core settings):
- **baseline**: the `SettingValues` of the `ConfigService.State` current when the dialog opened;
- **pending**: `Map<Setting<?>, Optional<?>>`, where empty means "restore the default" (the key is
  removed on save);
- a setting is *modified* when pending and different from the baseline's saved value; a page is
  modified when any of its settings are;
- **apply** builds one `SettingsEdit(changes = pending, expected = baseline saved values)` and calls
  `ConfigurationController.save`; on success the pending map clears and the returned state becomes the
  baseline.

**Page specs** (`SchemaPage.Spec`) list a page's rows and how to show them; placement and editor hints
live here, not on `Setting` (this amends A's roadmap line "B adds page assignment and editor hints to
`Setting`"; C's plugin declarations will translate into the same specs):

```java
page("terminal.font", "Font & Colors").parent("terminal")
    .section("Font")
        .row(TERMINAL_FONT_FAMILY, font(MONOSPACED))
        .row(TERMINAL_FONT_SIZE, number(0.5, "pt"))
        .row(TERMINAL_FONT_FALLBACK, fontList())
        .row(TERMINAL_FONT_LIGATURES)
        .row(TERMINAL_FONT_LINE_HEIGHT, number(0.05, ""))
    .section("Colors")
        .row(TERMINAL_COLORS, choice(Map.of("match", "Match the theme", "light", "Light", "dark", "Dark")))
```

`settingsui.CorePages` defines the whole tree above.

**Rows** (IntelliJ form style, `GridBagLayout`):
- label at left and control beside it (a checkbox carries its own label);
- a grey one-line comment below from `Setting.applies`: `NEW_PANES` → *Applies to new panes*,
  `NEW_WINDOWS` → *Applies to new windows*, `RESTART` → *Takes effect after restart*; a spec may add
  its own short note;
- a small ↺ **Reset** link (bundled `general/reset` icon) at the row's end while the effective value
  differs from the default; it stages an empty value;
- sections render as IntelliJ's titled separators.

**Editors** (by kind; a hint overrides):

| Kind | Default editor | Hints |
|---|---|---|
| bool | checkbox | — |
| integer | spinner clamped to the kind's range | unit suffix (`px`, `s`) |
| number | spinner | step and unit |
| choice | combo of display names (token prettified: `icons_and_labels` → "Icons and labels") | explicit names |
| string | text field | `font(ALL \| MONOSPACED)`: editable combo of installed fonts; `theme()`: combo of the theme catalog's names (supplied by the application); `executable()`: text field with Browse… |
| string list | ordered list with + − ↑ ↓ | `fontList()`: + picks from the font combo |
| string map | Name/Value table with + − | — |

`ui.font.size` (default 0, meaning platform size) uses `optionalNumber("Use platform size")`: a checkbox
that, when unchecked, enables a spinner; checking it stages the default.

**Validation.** Every edit is checked with `kind.check()` as it happens. An invalid value shows the
loader's own message under the row in the theme's `Jasper.configErrorForeground` and is not staged; a
page with an invalid row cannot be applied (Apply and OK are disabled while any row is invalid).

### 3. Keymap, Plugins, group pages, System Settings extras, banners

**Keymap** (`shortcuthelp.KeymapPage`, replacing the `ShortcutHelp` window):
- hosts `ShortcutPanel` (search, Record, expand/collapse, section tree, inline edit card);
- the edit card's **Save** becomes **Set**, staging into a `KeymapDraft` (action id → binding, or empty
  to restore the default) instead of writing; the tree shows baseline plus staged bindings, so conflict
  checks and Reassign work against the staged state;
- rows whose binding differs from its default render in the modified blue, and so do groups
  containing such rows (as in the reference screenshot);
- `modified()` is "staged edits exist"; `apply()` calls `saveKeybindings` once with all staged edits;
  `reset()` drops them; the page refreshes when contributed actions change.

**Apply order** when both drafts are dirty: core settings first, then keybindings. If the keybinding
save fails, the error names Keymap, core settings stay applied, and the staged bindings stay staged.

**Plugins** (`pluginmanager.PluginsPage`): `PluginManager` builds `PluginManagerPanel` into the page
instead of its own window and opens its consent and confirm dialogs over the Settings dialog. The page
is never modified; the restart banner stays at its top. The per-plugin "Open Settings" item still opens
the plugin's TOML file until C.

**Group page** (Appearance & Behavior): its title and its children as links. Terminal, which has
children, is a schema page.

**System Settings extras**: specs may add action rows. This page adds the `config.toml` path with
**Open in Editor** (the old Cmd+, behaviour) and **Reveal**, **Reload from Disk**, and
**Migrate Config…** (enabled while the file has old keys or removable dead tables).

**Banners** (dialog-wide, above the page):
- old keys: *config.toml uses old setting names.* [Migrate Config…];
- rejected file: *config.toml has errors, so changes can't be saved until they're fixed.*
  [Open config.toml]; Apply and OK are disabled.

**Changes on disk while open**: rows without pending edits take the new values and the baseline
follows; pending edits stay. An Apply conflict reports *X changed on disk*, refreshes that baseline
and keeps the edit staged, so a second Apply deliberately overwrites.

### 4. Search and the View menu

**Tree search**:
- index: each page's `searchTerms()` (schema pages: title, row labels, descriptions, keywords, choice
  display names; Keymap: action names, group names, binding text; Plugins: plugin names and ids);
- match: `ShortcutText.matches`'s token rule (case-insensitive; every word must match somewhere);
- the tree keeps matching pages and their ancestors, expanded, each with a small highlighted match
  count; if the selected page stops matching, the first match is selected;
- clearing restores the manual expansion and selection.

**Page highlight**: the dialog passes the query to the visible page's `highlight(query)`. Schema pages
paint a rounded highlight (FlatLaf `SearchMatch` colours) behind matching row labels and scroll the first
into view; Keymap puts the query into its own search field; Plugins selects the first matching plugin.

**View menu**:
- toolbar mode, the status-bar toggle and View › Terminal Colors call
  `ConfigurationController.save(setting, value)` (a one-setting `SettingsEdit` whose `expected` is the
  current saved value); the change arrives through the normal reload. With a rejected file the menu
  shows the "config.toml has errors" message and nothing changes;
- the Tab/Rail/Toolbar/Status bar height items and their `editSize` dialogs are replaced by
  **Window Settings…**;
- removed: the session-override tracking for those fields in `WorkspaceConfiguration`, and
  `ThemeState`'s temporary terminal-colours override. Per-pane zoom and Show/Hide Jasper keep their
  current behaviour.

### 5. Wiring

- `ConfigurationController.register` wires Settings to an application-supplied dialog opener instead of
  `service.openSettings(editor)`; `ConfigService.openSettings` remains for **Open in Editor**.
- `ConfigurationController` gains `state()` (the current `ConfigService.State`), `save(SettingsEdit)`,
  `save(Setting<T>, T)` and `onState(Consumer<ConfigService.State>)` returning a `Subscription`.
- `JasperApplication` installs the preferences handler, creates the `SettingsDialog` with `CorePages`,
  the Keymap and Plugins pages, and points `app.shortcuts`, `plugins.manage` and Window Settings… at
  `SettingsDialog.open(pageId)`. The `ShortcutHelp` window class and `PluginManager`'s window go away.
- Package-info contracts: `settingsui` (EDT-only UI; `SettingsDialog` owns its pages and disposes them
  on close); updated allowed dependencies for `shortcuthelp`, `pluginmanager`, `application`.

## Testing

TDD; headless; no GUI launched by an agent.

- `SettingsDraft`: modified tracking, reset, the `SettingsEdit` Apply builds, conflict handling,
  baseline updates on external change.
- Page spec builder; every editor: value round trip, invalid input (message, not staged), Reset link
  visibility, the "Use platform size" checkbox.
- `CorePages`: every core setting appears on exactly one page; the tree matches [Page tree](#page-tree).
- Dialog shell with test pages: tree building, empty Tools hidden, blue modified titles, back/forward,
  Apply enablement, OK/Cancel, the discard prompt, both banners, the Apply order.
- Search: filtering, ancestors, counts, restoring state, row highlight.
- Keymap page: Set stages without writing; staged bindings show; one `saveKeybindings` on Apply; Cancel
  drops. Existing shortcut tests move to the page.
- Plugins page: `PluginManagerTest` and `PluginManagerPanelTest` adapted to page hosting.
- View menu: toolbar, status bar and terminal colours save through the controller; rejected file
  message.
- Headless preview `SettingsDialogPreview` (Gradle task `settingsPreview`): Appearance, Font & Colors,
  Keymap, Plugins and a search state, dark and light, default UI font and 18 pt. Inspected against the
  reference screenshot.
- GUI checklist for the user: open via Cmd+, and the application menu; record a shortcut; the plugin
  restart banner; the discard prompt; switching theme while the dialog is open.

## Delivery

One spec, two plans, each mergeable alone:

- **B1: dialog and core pages.** Shell, draft, schema and group pages, editors, `CorePages`, System
  Settings extras, banners, search and highlight, `Cmd+,` and the preferences handler, the preview.
  While B1 stands alone, Keymap and Plugins appear in the tree as link pages that open the existing
  windows; B2 replaces them.
- **B2: Keymap, Plugins and the View menu.** `KeymapPage` with staging, `PluginsPage`, removal of the
  old windows, View menu persistence and removal of the session-override code.

## Non-goals

- Plugin settings pages (C).
- Named keymap schemes (the reference screenshot's "macOS copy" selector), keymap import/export.
- The pin button; per-project settings markers.
- Settings import/export or sync.

## Roadmap: C

Unchanged from A's spec: SDK 0.9 `dev.jasper.sdk.settings` declarations translated in
`dev.jasper.app.plugins` into B's `SchemaPage.Spec` (placement under **Tools** by default), a plugin
settings write path on `TomlDocument`, a custom-page escape hatch shaped like `SettingsPage`, testkit
fakes and contract tests, and the reworked plugins. The Plugins page's per-plugin "Open Settings" then
navigates to the plugin's page.

## Workflow

Branch `claude/settings-dialog` in `.worktrees/settings-dialog`, from local `main` at 16ec0f86. Plans via
`superpowers:writing-plans` (TDD, one commit per task), B1 first. Deviations go in each plan's status
banner and `docs/STATUS.md`. Merge only with the user's agreement; do not push without asking.
