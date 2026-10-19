# Notifications

Jasper and its plugins tell you about errors and results with notifications instead of dialogs that stop your work.
A notification shows as a card in a window, waits in the Notifications panel until you have read it, and, while
Jasper is in the background, also shows as the operating system's own notification. Notifications are kept in memory
only, at most the latest 500: they start fresh every time Jasper starts, and a resident Jasper (one still running with
no window open) keeps them until it quits completely.

## Cards

A card appears in the bottom-right corner of the tool window area of the window it concerns, or of the window you used
last, above the status bar and over any tool window docked there. It shows:

- an icon for its severity (information, warning or error), or one its sender chose, in the severity's colour;
- a bold title, followed by `×N` when it arrived more than once;
- up to five lines of details (the full text is in the Notifications panel);
- up to three links for what you can do next, as many as fit on one line, with the rest under **More ▾**.

Clicking a card's body marks it read, closes it and, when the notification points somewhere, goes there (the pointer
becomes a hand only then). A link also marks the notification read. The close button (tooltip "Close notification")
shows while the pointer is over the card, and always on a sticky card.

At most three cards show at once, the newest at the bottom; a fourth pushes out the oldest card that hides by itself.
Cards never take the keyboard, clicks beside them go where they would anyway, and nothing animates.

A card hides after ten seconds of you being in its window; the countdown pauses while the pointer is over the card and
while the window is in the background. A hidden card stays unread in the panel. Sticky cards, such as configuration
problems, stay until you close them. When the three cards showing are all sticky, more wait behind a **+N more** link
that opens the panel.

When the same notification (same group, severity, title, details and incident) arrives within 60 seconds of its last
arrival, it counts on the existing entry instead of adding one: the entry becomes unread again, moves to the top and
takes the newest arrival's links and target.

While Jasper is in the background, notifications collect in the panel and the operating system shows them too (see
[System notifications](#system-notifications)); sticky ones show their card when you come back. A resident Jasper with
no window open (once its last window closes, after Quit on macOS, or after a login launch) works the same
way, however long it stays so: what arrives goes to the panel as unread, the operating system shows it, and only sticky
notifications show their card when the next window opens. A notification that arrives while a window is opening,
before the first window of a normal start, or while a login launch is still starting up, waits and shows its card when
that window is focused. Waiting cards show in the order they arrived.

## The Notifications panel

The Notifications [tool window](workspace.md) starts in the upper half of the window's right side. Under the
[Enterprise look](enterprise.md) it is titled **Event Log** and starts on the left of the bottom strip. Its button,
or **Notifications: Show History** in the command palette, opens and closes it.

While anything is unread, the panel's button carries a badge: a dot on its icon under the default look, a count after
its label under the Enterprise look. The badge turns to the error colour while an error is unread, and the tooltip adds
the count, for example "Notifications (3 unread notifications, 1 error)".

Unread notifications are listed under **New**, in bold. Showing the panel in a focused window marks everything read;
what it read stays under **New** until you close the panel, and everything else is under **Earlier**, newest first.
Each row shows the full details, "<group> · <time>", and the links, which are disabled once the notification no longer
applies. A row has no click of its own. ✕ ("Remove notification") removes one row; **Clear All** removes them all.
With nothing to show the panel reads "No Notifications".

## Groups

Every notification belongs to a group, which decides how it appears. Jasper declares its own groups; each plugin gets
a group named after it and an activities group, and may declare more.

| Group | Id | What it covers | Default |
|---|---|---|---|
| Jasper | `jasper.general` | errors and results of Jasper's own commands, such as a shell that could not start, a theme that could not be applied or Migrate Config | card |
| Configuration | `jasper.config` | problems in `config.toml`, the plugin settings files and theme files | sticky card |
| Long-running commands | `jasper.commands` | a [command that ran long](#long-running-commands) and finished anywhere but the pane you were typing in | card |
| Plugin problems | `jasper.plugins` | a plugin that failed while Jasper asked it to do something, or could not be loaded, started or read at launch; a look that did not finish starting or is missing some of its files | card |
| Internal errors | `jasper.errors` | unexpected errors in Jasper itself | card |
| Memory & VM options | `jasper.vmoptions` | a start without your custom VM options or without the start-up cache, an options file Jasper had to ignore, a restart that would apply new options, a restart Jasper could not perform, an options file that could not be opened, saved or revealed, and the [low-memory warning](diagnostics.md#memory) | card |
| *plugin name* | *plugin id* | the plugin's own messages, such as a file that could not be saved | card |
| *plugin name* activities | *plugin id*`.activities` | the outcome of a plugin's background work, such as a transfer | card |
| Tunnels | `dev.jasper.remote.tunnels` | Remote's tunnel failures | card |
| Vault copies | `dev.jasper.vault.copies` | what the Credential Vault copied, such as "Copied the password for …" | card |
| Project indexing | `dev.jasper.indexer.prompts` | Project Index's questions, such as whether to index a project | sticky card |
| Index results | `dev.jasper.indexer.results` | finished indexing, such as "Indexed <name>" | history only |

A configuration notification is titled "config.toml has errors" or "config.toml has warnings", or "The configuration
has …" when any problem lies in another file, such as a plugin settings or theme file. It shows the first problem and
how many more there are, and offers **Show Problems** (the same list as the status bar's configuration indicator) and
**Open config.toml**; the title and the link name the file a `--config` launch reads. Once every problem is fixed it
expires by itself: its card closes and the panel keeps it with its links disabled.

To change how a group appears, use Settings › Appearance & Behavior › Notifications › **Groups**. It lists every group
known now, Jasper's and each plugin's once the plugin has run, under its owner. Each group offers **Card**, **Sticky
card**, **History only** (the Notifications panel, unread, no card) or **Off**, with its default marked "Default", and
**Reset**. Under the Enterprise look the page shows one table of Group, Display and Source ("Default" or "Custom"),
with **Reset to Default** in a row's menu. A change applies to the next notification; what is already in the panel or
on screen stays. The same choices live in `config.toml` under
[`[notifications.groups]`](configuration.md#notification-groups).

## Long-running commands

A command that ran at least `notifications.long_command_seconds` (10 seconds by default) and finished anywhere but the
pane you were typing in, a visible but unfocused split pane included, is posted in Long-running commands. The title is
the command on one line ("Command" when it shows nothing), and the details read "Finished in 1m 12s" (information) or
"Exited 2 · 45s" (warning). Its card shows in the pane's window, and clicking the card focuses the pane; while Jasper
is in the background its [system notification](#system-notifications) does. Closing the pane expires the
notification. The same command finishing with the same result and the same duration text within a minute counts on one
entry, which then points at the pane it last came from.

This needs [shell integration](terminal.md#shell-integration): the duration is measured between the command marks
its scripts emit, so a shell without them produces no notifications. `0` turns these notifications off. The
[Desk Buddy](#capsules-and-the-drawer), when shown, also shows a long-running command as a capsule while it runs.

## Plugin notifications

Plugins post notifications in their own groups, so you can tell them apart from Jasper's. When an error a plugin
reports has an exception behind it, its notification offers **Show more…**, which opens the Errors window at it. The
notifications a plugin posted itself expire when it stops.

When a plugin's background work ends, Jasper posts the outcome in the plugin's activities group: information when it
succeeded, an error when it failed, titled after the work with its details. Clicking it runs the plugin's action for
that work, if it has one. Cancelled work posts nothing.

At launch, each plugin that could not be loaded, was not started (for example, it needs another version of Jasper, or
a plugin it requires is missing or did not start) or could not be read is reported in Plugin problems ("<name> could
not be loaded", "<name> was not started", "A plugin could not be read") with **Open Plugins**, which opens Settings ›
Plugins. A plugin that failed while starting is reported once, by its incident's notification.

## The Errors window

When something fails unexpectedly, in Jasper or in a plugin, Jasper records it as an incident and tells you with an
error notification: "Unexpected error in Jasper", or, for a plugin, "<name> failed to start", "<name> failed to stop"
or "<name> failed in <operation>". A plugin's offers **Disable Plugin**, and both offer **Show more…**, which opens the
Errors window at that incident. **Notifications: Show Errors** in the command palette opens the window too. A shell that
could not start is recorded as well: each failure is announced as "Could not start the terminal" in the window it
happened in, with its folder and cause, and offers **Show more…**.

The **Errors** window lists this session's incidents, most recently seen first: each row shows the summary, the
source (Jasper or the plugin's name), "×N" once it happened more than once, and the time. When the same failure
happens again (the same source, exception and top frames of its root cause), its row counts up and moves to the top
instead of a new row appearing. For a failure of Jasper or of a plugin, its notification counts up too, at most once a
second, and its card shows again at most once a minute. The right side shows the selected incident: who failed and
what it was doing, when it was first and last seen and how often, its **Message** and its **Stack Trace**.

- **Copy** puts a report on the clipboard: Jasper's version, your OS and Java runtime, the plugin and its version for a
  plugin's incident, the operation and thread, how often it happened, and the stack trace. Paste it into a bug report.
- **Disable Plugin** (only for a plugin's incident) disables the plugin from the next launch, as the checkbox on the
  Plugins page does. Jasper confirms with "<name> will be disabled when Jasper restarts".
- **Open Log Folder** shows Jasper's [log files](diagnostics.md#logs).
- **Clear** empties the list. Notifications of cleared incidents stay in the Notifications panel with their links
  disabled.
- **Done** or Escape closes the window. The footer counts this session's errors ("No errors this session").

Incidents are kept in memory only, at most the latest 100, with a message of up to 16,384 characters and a stack trace
of up to 262,144 characters each. They are gone when Jasper quits completely; a resident Jasper keeps them, like the
panel's history.

## System notifications

While Jasper is in the background, or idle as a resident Jasper with every window closed, a notification whose group
shows cards also appears as the operating system's own: a banner on macOS, a balloon from Jasper's notification-area
icon on Windows, and a desktop notification through `notify-send` on Linux. Clicking it brings its window to the front
(opening one when none is open) and does what clicking its card would; a notification with nothing to run opens the
Errors window at its incident, or else the Notifications panel. Reading a notification in Jasper, its expiry, its
removal and Clear All take the system notification away where the system allows it. While Jasper starts or a window
opens nothing is sent, since the card is about to show; that includes a login launch until its start-up has settled.

- **macOS**: only the packaged `Jasper.app` sends banners, with Jasper's icon. The first one asks for permission; allow
  Jasper in System Settings → Notifications. Gradle and IDE runs send none.
- **Windows**: the balloon comes from Jasper's icon in the notification area: a resident Jasper's own icon, or one
  added for the balloon that goes when Jasper is focused again. A click on the balloon or on the icon opens the most
  recent notification, if it is still unread; once it is read, a click on the resident icon opens a window, and a
  click on the icon shown only for balloons does nothing, even if an older notification is unread.
- **Linux**: Jasper runs `notify-send` from `PATH` when there is one, with Jasper's icon (`notification-icon.png` in
  Jasper home). When it supports actions, a click on the notification comes back to Jasper; otherwise the notification
  only informs.

A notification that offers **Show more…** sends only its title, since its details can name files and hosts and system
notifications show on a locked screen. Turn system notifications off with `notifications.system = false`, or with
**Show system notifications** in Settings.

## Settings and commands

Settings › Appearance & Behavior › **Notifications** holds the notification settings; the Help button on that page
opens [finished-command notifications](configuration.md#finished-command-notifications).

| Setting | Key | Default | Notes |
|---|---|---|---|
| Show system notifications | `notifications.system` | `true` | Applies live |
| Notify after | `notifications.long_command_seconds` | `10` | Seconds, 0–3600; `0` turns long-running-command notifications and Buddy's command capsules off; applies live |
| Groups | `notifications.groups` | none | Group id to `"card"`, `"sticky"`, `"history"` or `"off"`; applies from the next notification |

| Command | Does |
|---|---|
| Notifications: Show History | Opens or closes the Notifications panel |
| Notifications: Dismiss Cards | Marks every card in the window read, showing or waiting, and closes them |
| Notifications: Show Errors | Opens the Errors window |

None has a default shortcut; assign one in Settings › Keymap.

## Desk Buddy

The Desk Buddy is Jasper, a small pixel-art turtle who floats above your windows and shows the work still going on:
long-running commands, plugins' background work and the connections plugins report. He keeps no outcomes; how work
ended is a notification.

### Showing him

Turn him on with **Show Jasper** (`buddy.enabled`, off by default) in Settings › Appearance & Behavior › **Desk
Buddy**; the change applies at once. He shows while at least one workspace window is open and not minimised. The palette
command **Show Jasper** / **Hide Jasper**, and **Hide Jasper** on his right-click menu, show or hide him for this
session only; saving a new `buddy.enabled` value ends that choice. When the system has no always-on-top or translucent
windows, he stays off for the session and the log says "Desk buddy disabled".

### What he does

| You | He |
|---|---|
| point at him | waves, for about a second |
| click | opens the drawer |
| double-click | brings the Jasper window you used last to the front |
| right-click | shows a bubble with **Hide Jasper**, which hides after five seconds or when the pointer leaves it |
| drag (3 px or more) | moves; letting go saves his place |

He fades in with sparkles and a wave when he appears, and greets you when you come back to a workspace window. Left
alone he blinks every few seconds, sits down after 20 seconds, and after a minute tucks into his shell and sleeps;
typing in a workspace window wakes him.

### Capsules and the drawer

Work in progress shows as capsules beside him:

- **A long-running command** gets a capsule once it has run `notifications.long_command_seconds`, or at once when it
  starts out of sight or you look away from its pane (another tab, another window, Jasper in the background). The
  capsule reads the command, or the title the program sets, and "Running · <time>"; it goes when the command ends.
  While any long command runs he sits and types. Clicking the capsule focuses the pane.
- **A plugin's background work**, such as a File Server transfer that takes longer than a second, shows with its
  progress until it ends; clicking it brings Jasper forward and runs the plugin's action for it.
- **A connection a plugin reports** shows its state: a dot while it has a problem, a cross while it is down.

At most three capsules show, the newest nearest him, carrying the total count when there are more. The column sits
above him, or below him when he is in the upper half of the screen or there is no room above. A single click on him
opens the drawer, which lists everything still going, newest first (up to 50): × on a capsule dismisses it, and
**Clear all** (with more than one) empties the drawer. Dismissing a capsule does not stop its work. The drawer hides
when the pointer leaves it, after you click a capsule, or when it is empty.

### His place

His position is saved in `buddy.toml` in Jasper home (a `--config` launch does not move it):

```toml
version = 1
x = 1640
y = 980
```

Jasper writes it when you finish dragging him and reads it at start; a file that is not exactly this shape, or larger
than 4 KiB, is ignored with a warning in the log. Without a saved place he starts at the bottom-right of the main
screen. A saved place stays as long as at least half of him is on a screen; otherwise he moves fully onto the nearest
screen.

## For developers

Jasper's own code posts through the application's notification center; see
[posting a notification](app-maintenance.md#posting-a-notification) and
[recording an incident](app-maintenance.md#recording-an-incident). Plugins post through the SDK's
[`Notifications`](sdk-reference.html#dev.jasper.sdk.notifications.Notifications), which needs no capability: a group is
a [`GroupSpec`](sdk-reference.html#dev.jasper.sdk.notifications.GroupSpec) whose id starts with the plugin's id and a dot,
and the plugin's own group and its `.activities` group are declared for it. A request carries plain text: a title of one
line and at most 200 characters, details of at most 4,000 characters, and at most eight actions (seven when the
notification carries a cause, since Jasper adds **Show more…**) with labels of at most 40 characters. Buddy's
embedding API is described in the [Buddy README](../jasper-buddy/README.md).
