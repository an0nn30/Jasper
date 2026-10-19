# The Enterprise look

Enterprise is a bundled plugin (`dev.jasper.enterprise`), off by default, that provides Jasper's presentation: the
window chrome and the way controls are drawn. With it on, every window has the system title bar and classic controls,
in the Enterprise theme or any other, and each workspace window has its menus in the window, labelled tool window
stripes and a Run window in place of the command palette card. Remote, File Server and Credential Vault switch to
classic layouts of their own.

The look is data in the plugin's descriptor: a kit file (`presentation/enterprise.kit.toml`), the IBM Plex fonts, the
Enterprise theme and its branding. Its code does one thing: while the look runs it publishes the service
[`dev.jasper.enterprise.api.Workstation`](sdk-reference.html#dev.jasper.enterprise.api.Workstation), which other
plugins look for. It has no commands and no settings.

## Turning it on and off

1. Open Settings › Plugins (**File › Manage Plugins…**).
2. Turn **Enterprise** on, or off to go back to the standard look
   ([enabling and disabling](plugins.md#enabling-disabling-and-removing)).
3. The banner reads "Restart Jasper to apply your changes."; press **Restart Now**
   ([restarting](plugins.md#restarting)).

The look is chosen once, while Jasper starts, so a change applies only after the restart. One look is active at a time:
turning Enterprise on turns off any other plugin that provides a look, and the banner names it ("… was turned off: one
look can be active at a time."); [looks and plugin themes](plugins.md#looks-and-plugin-themes) says which look wins when
several are enabled. `jasper --safe-mode` always starts with the standard look
([command line](command-line.md#launching-jasper-with-options)).

The Enterprise look keeps its own tool window layout in `ui-state.toml` (under `[look."dev.jasper.enterprise"]`), apart
from the standard look's, so each comes back as you left it. After turning Enterprise off, a theme pick you saved as
`enterprise` shows Slate Cobalt, without a warning, until Enterprise is on again.

### If the look does not start

Jasper records each start with a look in `presentation-attempt.toml` in Jasper home: the look is pending until the
first window has painted, and a normal quit before then clears the mark. If Jasper stopped before the first window
painted, or the look failed while starting, the next start uses the standard look and shows the warning "Jasper started
with its standard look": "The last start with the Enterprise look did not finish, so Jasper uses its standard look
until you try again." **Try Again** forgets the record and restarts Jasper. The record names the look's files and
Jasper's build, so a new build or changed look files try the look again by themselves. Settings › Plugins then shows
Enterprise as skipped: "did not finish starting with this look last time".

| What you see | Why |
|---|---|
| "The Enterprise look is missing some of its files", one line per problem | a font that cannot be used, whose text then uses the platform font, or a default theme that did not load, whose setting keeps its core default. On macOS an installed IBM Plex font hides the bundled one: "…: an installed font named IBM Plex Sans hides it; that text uses the platform font" |
| Enterprise skipped with "its look could not be installed; Jasper uses its standard look" | the look could not be installed while Jasper started; that session runs the standard look, keeping the system title bar and the menus in the window that were already set up, without the JasperOps branding or the plugin, so Remote, File Server and Credential Vault keep their default layouts; the next start uses the standard look |
| Enterprise skipped with "Presentation invalid: …" | the kit file broke a rule; the message names the key |

## What changes

### Windows and menus

- **Title bars.** Every window has the system's title bar, on macOS, Linux and Windows; Settings hides
  `window.native_decorations`. On macOS the title bar is light or dark with the theme shown.
- **Window title.** `Workstation — {session} — {directory}`: `{session}` is the session's name for a session a plugin
  provides, such as a Remote host, the host of a pane in a remote directory, or "Local: *shell*"; `{directory}` is the
  remote path, or the local folder with `~` for your home folder. A part with nothing to show drops with its separator.
- **Menus** are a row in each workspace window; plugin windows, such as the Credential Vault manager, have none, and
  Close Window's shortcut still closes them. On macOS the screen menu bar is off, and **About Jasper** is at the end of
  Help. The order is File, Edit, View, Session and Transfer (when a plugin fills them), the plugins' own menus, Tools
  (when a plugin fills it), Window and Help. **Window** holds what the standard look's Pane and Tab menus hold, then
  the tool window toggles, which the standard look keeps under View.
- **Toolbar row** under the title bar: **New Session**, **Split** (Split Right, Split Down), **Maximize**, the plugins'
  toolbar items (such as Remote's session combo and Run button), **Search everywhere** with the palette's shortcut,
  which opens the Run window, and **Settings…** at the trailing edge. By default each button is a 22 px icon named by
  its tooltip, the built-in ones IntelliJ's classic icons, and Split marks its menu with a small ▼ in its corner.
  **Toolbar: Icons and Labels** (`window.toolbar = "icons_and_labels"`) shows each button's icon, then its label, Split
  ending in ▼. **Toolbar: Hidden** removes the row, Search everywhere and the plugins' items with it. Its height,
  `window.toolbar_height`, defaults to 34 px.
- **Tabs** are classic: always shown, as wide as their labels, each with a × (a terminal tab also with a status dot), a
  **+** after the last, the active one underlined.
- **Tool windows** ([tool windows and the dock](workspace.md#tool-windows-and-the-dock)) sit on labelled stripes: left
  and right stripes as thick as a label, with their second group at the bottom end, and a bottom strip with a left and
  a right group. When a window's toggle shortcut is a digit key held with Cmd, Option or Ctrl (Ctrl or Alt on Linux
  and Windows), its label starts with the digit, such as "3: Transfers". A click toggles the window and a right-click
  opens its menu. Frames are flush, under header strips 24 px high. Settings hides `window.rail_width`, which does not
  apply.
- **The active tool window.** A click anywhere in a tool window, or opening it from its stripe, makes it the active
  one: its header strip takes the focused colour and the keyboard moves into it, to its first control that takes keys,
  buttons last. A click in a terminal pane ends that; dragging a divider does not. Remote's and File Server's own
  toggle actions, such as **Toggle SFTP**, and their shortcuts open their windows without activating them.
- **Event Log** is the Notifications panel's name, and it starts in the bottom strip, on its left
  ([Notifications](notifications.md#the-notifications-panel)).
- **Status bar** in ruled cells: **▶ Run…** first, which opens the Run window, then the session with its dot and the
  folder, the plugins' items, the grid size, "UTF-8" and, while `config.toml` has a problem, its indicator.
- **The Run window** replaces the command palette card. It opens centred across the workspace, in its upper part, stays
  open while you work and shows 8 rows by default ([Run window](command-palette.md#the-run-window)).

### Dialogs and controls

Overlays and sheets, such as Unlock Vault and the Vault's entry sheets, are drawn inside their window as classic
dialogs: the window dims under a still scrim and the dialog has a title strip with its title and, when it has one, its
colour swatch. Centred dialogs stay separate windows. Dialog bodies take the theme's app surface colour and Settings'
navigation its card colour. Nothing moves: overlays and tabs appear and go at once, and Settings hides
`ui.animations.enabled`.

Controls are drawn the classic way:

| Control | Standard look | Enterprise |
|---|---|---|
| On/off settings | switches | checkboxes |
| One-of choices | segment tracks | radio buttons |
| Tabbed choices | segment tracks | folder tabs |
| Groups of fields | a section label over a card | a titled box |
| Form labels | plain | a label column ending in colons |
| Empty states | an icon tile and text | text only |
| Tables | plain | a header band and zebra rows |
| Trees | plain | classic disclosure and category rows |
| Numbers and sliders | steppers | spinners |
| Ordered lists | commands on each row | commands in a side column |
| Buttons and dropdowns | raised | classic gradients |
| Tags | tinted | boxed uppercase labels |
| A tunnel's route | icon tiles | edged cells |
| Prompts | an icon tile and title | the plain classic prompt |

Controls and rows are 22 px high, tool window headers 24 px, and Settings' navigation 230 px wide; controls and groups
have 3 px corners and fields 2 px.

Settings follows the look: its navigation sections collapse, a changed setting's **Reset to Default** is in the row's
context menu, Keymap is a table with Find by Shortcut, and Plugins lists checkbox rows
([Settings dialog](configuration.md#settings-dialog)).

### Type

The interface draws in IBM Plex Sans (regular, medium and semibold), and its monospaced text, such as paths and
fingerprints, in IBM Plex Mono; the fonts ship with the plugin. `[ui.font] family = "system"`, the default, means IBM
Plex Sans, and the default size is 12 (Settings: "Use the default font size"). Terminal text keeps `[terminal.font]`.

### Branding

The look is branded **JasperOps**. A start that opens a window (not `--background`, `--no-window` or `--safe-mode`)
shows a 720 × 440 splash with its art, the name, Jasper's version and copyright, and each plugin as it starts, until the
first window shows. Its icons replace Jasper's application icon in windows and in the Dock or taskbar. Only the splash
shows the name.

### Defaults

The look gives five settings other defaults. A value you saved in `config.toml` or Settings wins.

| Setting | Under Enterprise | Standard look |
|---|---|---|
| `ui.theme.light` | `enterprise` | `slate-cobalt` |
| `ui.theme.dark` | `enterprise` | `slate-cobalt` |
| `palette.visible_rows` | `8` | `5` |
| `window.toolbar_height` | `34` | `30` |
| `window.toolbar` | `"icons"` | `"icons_and_labels"` |

### The Enterprise theme

While the plugin is on, theme dropdowns list **Enterprise** (`enterprise`), captioned with its plugin
([Themes](themes.md)):

| Part | Contents |
|---|---|
| UI | Enterprise Light and Enterprise Dark, ports of IntelliJ's IntelliJ Light and New UI Dark themes |
| Terminal schemes | **Enterprise Light** (`enterprise/light`) and **Enterprise Dark** (`enterprise/dark`), from the console colours of those themes' editor schemes, which `terminal.scheme = "match"` uses with Light and Dark |
| Editor | light and dark colours for the Editor plugin, from the same editor schemes |

The theme comes and goes with the plugin; the look works with any theme. About Jasper › Licenses… lists IntelliJ's
themes as "IntelliJ Platform themes (the Enterprise theme)" and its icons, the classic ones included, as "IntelliJ
Platform icons (New UI and Classic)".

## Classic layouts in Remote, File Server and Credential Vault

Each of these plugins declares Enterprise as an optional requirement and, when it starts, looks for the `Workstation`
service. Finding it, the plugin composes its classic layout for that run:

- **Remote**: the Sessions and Tunnels panels start on the right and SFTP on the left; the toolbar shows IntelliJ's
  run-configuration combo for the session and a Run button, tooltip **Connect**; Sessions is a Name/Host/Port tree over
  the host's Properties, Tunnels a Type/Local/Remote/Via table; Transfers is a bottom panel (Cmd+Option+3, or Alt+3 on
  Linux and Windows) ([Remote](remote.md)).
- **File Server**: the File Servers panel starts on the left beneath SFTP, as a Folder/Address/Status table over the
  server's Properties ([File Server](file-server.md)).
- **Credential Vault**: the manager has a toolbar row, a Categories tree, a sortable table, a Details pane with Fields
  and Properties, and a status row ([Credential Vault](credential-vault.md#under-the-enterprise-look)).

Any plugin can do the same: [plugin APIs](plugin-apis.md) describes `dev.jasper.enterprise.api`.

## Building a look

A plugin provides a look by naming a kit file with `presentation` in its descriptor and declaring the capability
[`presentation.provide`](sdk-reference.html#dev.jasper.sdk.Capabilities.PRESENTATION_PROVIDE). Enterprise's kit file is
the worked example. [Looks](looks.md) is the kit file reference, and [Creating themes](creating-themes.md) covers the
theme a look ships.
