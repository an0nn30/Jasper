# The Enterprise look

Enterprise is a bundled plugin (`dev.jasper.enterprise`), off by default, that provides Jasper's presentation: the
window chrome and the way controls are drawn. With it on, each workspace window has IntelliJ Classic's title bar on
macOS and Windows (the system's on Linux), the classic menus, a classic toolbar row, labelled tool window stripes and a
Run window in place of the command palette card, and controls are drawn the classic way, in the Enterprise theme or
any other. Remote, File Server and Credential Vault switch to classic layouts of their own.

The look is data in the plugin's descriptor: a kit file (`presentation/enterprise.kit.toml`), the IBM Plex fonts and
the Enterprise theme. Its code does nothing: other plugins learn the look from Jasper
([adapting a plugin to a look](looks.md#adapting-a-plugin-to-a-look)). It has no commands and no settings.

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
`enterprise` shows the appearance's default theme (Jasper Meadow Light in Light, Slate Cobalt in Dark), without a
warning, until Enterprise is on again.

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
| Enterprise skipped with "its look could not be installed; Jasper uses its standard look" | the look could not be installed while Jasper started; that session runs the standard look (on Linux keeping the system title bar and the menus in the window that were already set up), without the plugin, so Remote, File Server and Credential Vault keep their default layouts; the next start uses the standard look |
| Enterprise skipped with "Presentation invalid: …" | the kit file broke a rule; the message names the key |

## What changes

### Windows and menus

- **Title bars.** Workspace windows have IntelliJ Classic's title bar
  ([the classic title bar](looks.md#the-classic-title-bar)):
  - On macOS, a 28-point band in the toolbar's colour, seamless with the toolbar row, holds the traffic lights and the
    window's title, light or dark with the theme shown. A double-click in it does what System Settings › Desktop &
    Dock says; full screen removes it.
  - On Windows, Jasper's 28-pixel title bar in the toolbar's colour holds the menus and then the window's title,
    left-aligned and dimmed while the window is inactive. `window.native_decorations = true` (Settings › Window) gives
    the system title bar instead, with the menus as a row in the window.
  - On Linux, the system title bar.

  Other windows, such as Settings, About and the Credential Vault manager, keep the system title bar on macOS and
  Linux and Jasper's on Windows. Settings hides `window.native_decorations` on macOS and Linux. Compact keeps the
  system title bar, without the band.
- **Window title.** `Workstation — {session} — {directory}`: `{session}` is the session's name for a session a plugin
  provides, such as a Remote host, the host of a pane in a remote directory, or "Local: *shell*"; `{directory}` is the
  remote path, or the local folder with `~` for your home folder. A part with nothing to show drops with its separator.
- **Menus** are in the screen menu bar on macOS, with **About Jasper** and **Settings…** in the application menu; in
  the title bar on Windows; and a row in the window on Linux, with **About Jasper** at the end of Help on both. The
  order is File, Edit, View, Session and Transfer (when a plugin fills them), the plugins' own menus, Tools (when a
  plugin fills it), Window and Help. **Window** holds what the standard look's Pane and Tab menus hold, then the tool
  window toggles, which the standard look keeps under View. Plugin windows, such as the Credential Vault manager, show
  a short File menu in the screen menu bar on macOS and have none on Linux and Windows, where Close Window's shortcut
  still closes them.
- **Toolbar row** under the title bar: **New Session**, **Split** (Split Right, Split Down), **Maximize**, the plugins'
  toolbar items (such as Remote's session combo and Run button), then at the trailing edge **Search Everywhere**, an
  icon-only magnifier whose tooltip names the palette's shortcut and which opens the Run window, and **Settings…**. By
  default each button is a 22 px icon named by its tooltip, the built-in ones IntelliJ's classic icons, and Split marks
  its menu with a small ▼ in its corner. **Toolbar: Icons and Labels** (`window.toolbar = "icons_and_labels"`) shows
  each button's icon, then its label, Split ending in ▼; Search Everywhere stays an icon. **Toolbar: Hidden** removes
  the row, Search Everywhere and the plugins' items with it; the palette's shortcut and **▶ Run…** still open the Run
  window. Its height, `window.toolbar_height`, defaults to 28 px, IntelliJ's.
- **Tabs** are classic: always shown, as wide as their labels, each with a × (a terminal tab also with a status dot), a
  **+** after the last, the active one underlined.
- **Tool windows** ([tool windows and the dock](workspace.md#tool-windows-and-the-dock)) sit on labelled stripes: left
  and right stripes as thick as a label, with their second group at the bottom end, and a bottom strip with a left and
  a right group. Each label starts with its tool window's icon, IntelliJ's classic tool-window icons for Sessions,
  SFTP, Tunnels, Transfers, File Servers and the Event Log, and a monochrome glyph for any other. When a window's
  toggle shortcut is a digit key held with Cmd, Option or Ctrl (Ctrl or Alt on Linux and Windows), its label then
  starts with the digit, such as "3: Transfers". A click toggles the window and a right-click opens its menu. Frames
  are flush, under header strips 24 px high, whose buttons draw IntelliJ's classic icons for add, split, zoom,
  settings, new window and search, as the toolbar does. Settings hides `window.rail_width`, which does not apply.
- **The active tool window.** A click anywhere in a tool window, opening it from its stripe, or its toggle in the
  Window menu or its shortcut (such as Cmd+Option+2, Alt+2 on Linux and Windows, for SFTP) makes it the active one:
  its header strip takes the focused colour and the keyboard moves into it, to its first control that takes keys,
  buttons last. As in IntelliJ, the toggle of a tool window that shows but is not the active one activates it, and
  only the active one hides; Transfers' action only shows its window, so it activates it every time. A click in a
  terminal pane ends that; dragging a divider does not.
- **Event Log** is the Notifications panel's name, and it starts in the bottom strip, on its left
  ([Notifications](notifications.md#the-notifications-panel)).
- **Status bar** in ruled cells: **▶ Run…** first, which opens the Run window, then the session with its dot and the
  folder, the plugins' items, the grid size, "UTF-8" and, while `config.toml` has a problem, its indicator.
- **The Run window** replaces the command palette card. It opens centred across the workspace, in its upper part, stays
  open while you work and shows 8 rows by default ([Run window](command-palette.md#the-run-window)).

### Dialogs and controls

Overlays and sheets, such as Unlock Vault and the Vault's entry sheets, are drawn inside their window as classic
dialogs: the window dims under a still scrim below its title bar and the dialog has a title strip with its title and,
when it has one, its colour swatch. Centred dialogs stay separate windows. Dialog bodies take the theme's app surface colour and Settings'
navigation its card colour. Nothing in the windows moves: overlays and tabs appear and go at once, and Settings hides
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
Plex Sans, and the default size is 12 (Settings: "Use the default font size"). The Editor's documents default to IBM
Plex Mono too ([defaults](#defaults)). Terminal text keeps `[terminal.font]`.

### Branding

The look has no branding of its own: windows, the Dock or taskbar keep Jasper's Shell Prompt icon, and a start that
opens a window (not `--background`, `--no-window` or `--safe-mode`) shows Jasper's Meadow splash until the first window
has painted ([branding](looks.md#branding)). Its turtle is animated unless `config.toml` sets
`ui.animations.enabled = false`, which Settings hides under this look.

### Defaults

The look gives five core settings and one Editor setting other defaults. A value you saved in `config.toml`, the
Editor's settings file or Settings wins, and **Reset to Default** returns to the look's value.

| Setting | Under Enterprise | Standard look |
|---|---|---|
| `ui.theme.light` | `enterprise` | `jasper-meadow` |
| `ui.theme.dark` | `enterprise` | `slate-cobalt` |
| `palette.visible_rows` | `8` | `5` |
| `window.toolbar_height` | `28` | `30` |
| `window.toolbar` | `"icons"` | `"icons_and_labels"` |
| The Editor's `font_family` ([Editor](editor.md)) | `"IBM Plex Mono"` | the first installed of JetBrains Mono, Menlo, Consolas and DejaVu Sans Mono, else Monospaced |

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

Each of these plugins reads the look when it starts and, under a classic look such as Enterprise, composes its
classic layout for that run:

- **Remote**: the Sessions and Tunnels panels start on the right and SFTP on the left; the toolbar shows IntelliJ's
  run-configuration combo for the session and a Run button, tooltip **Connect**; Sessions is a Name/Host/Port tree over
  the host's Properties, Tunnels a Type/Local/Remote/Via table; Transfers is a bottom panel (Cmd+Option+3, or Alt+3 on
  Linux and Windows) ([Remote](remote.md)).
- **File Server**: the File Servers panel starts on the left beneath SFTP, as a Folder/Address/Status table over the
  server's Properties ([File Server](file-server.md)).
- **Credential Vault**: the manager has a toolbar row, a Categories tree, a sortable table, a Details pane with Fields
  and Properties, and a status row ([Credential Vault](credential-vault.md#under-a-classic-look)).

Any plugin can do the same ([adapting a plugin to a look](looks.md#adapting-a-plugin-to-a-look)).

## Building a look

A plugin provides a look by naming a kit file with `presentation` in its descriptor and declaring the capability
[`presentation.provide`](sdk-reference.html#dev.jasper.sdk.Capabilities.PRESENTATION_PROVIDE). Enterprise's kit file is
the worked example. [Looks](looks.md) is the kit file reference, and [Creating themes](creating-themes.md) covers the
theme a look ships.
