# Notifications

Jasper tells you about errors and results with notifications instead of dialogs that stop your work. Nothing is
saved: notifications start fresh every time Jasper starts, and you can clear them at any time.

## Cards

A notification appears as a card in the bottom-right corner of the window it concerns, or the window you used last:

- an icon for its severity (information, warning or error), or one its sender chose;
- a bold title, followed by `×N` when the same notification arrived several times within a minute;
- up to five lines of details (the full text is in the Notifications panel);
- links for what you can do next; more than three are under **More ▾**.

Clicking a card's body goes where it points, when it points somewhere, and closes the card. Close it with `✕`. At most
three cards show at once; the newest is at the bottom. Cards never take the keyboard from your terminal, and clicks
beside them reach the terminal as usual. Nothing animates.

Most cards hide after ten seconds of you being in that window; the countdown pauses while the pointer is over the
card and while the window is in the background. A hidden card stays unread in the panel. Sticky cards, such as
configuration problems, stay until you close them. When three sticky cards are showing, more wait behind a
**+N more** link that opens the panel.

When Jasper is in the background, notifications collect in the panel; sticky ones show their card when you come
back. Notifications that arrive before the first window opens show when it does.

## The Notifications panel

The Notifications button on the right stripe opens the panel, which starts in the upper half of the window's right
side; drag the button or the panel's header to move it, or use **Move to** on the button's right-click menu or the
header's ⋮ menu. **Notifications: Show History** in the command palette also opens and closes it. The button shows a
dot while anything is unread: red when an error is unread. Its tooltip gives the count.

Unread notifications are listed under **New**, in bold. Opening the panel in a focused window marks everything read;
what it read stays under **New** until you close the panel, and everything else is under **Earlier**, newest first.
Each row shows the full details, the notification's group and time, and its links (disabled once the notification no
longer applies). `✕` removes one row; **Clear All** removes them all.

## Groups

| Group | What it covers | Shows as |
|---|---|---|
| Jasper | errors and results of Jasper's own commands: a shell that could not start, a theme that could not be applied, Migrate Config | card |
| Configuration | problems in `config.toml`, themes and plugin settings files | sticky card |
| Long-running commands | a command that ran at least `notifications.long_command_seconds` and finished anywhere but the pane you were typing in | card |
| Plugin problems | a plugin that failed while Jasper asked it to do something, or that could not be loaded, started or read at launch | card |
| Internal errors | unexpected errors in Jasper itself | card |
| *Plugin name*, one per plugin | the plugin's own messages, such as a host that could not be saved | card |
| *Plugin name* activities | a plugin's background work that finished or failed, such as a sync or an upload | card |
| Tunnels | Remote's tunnel failures | card |

A configuration notification offers **Show Problems** (the same list as the status bar's configuration indicator)
and **Open config.toml**. It goes away by itself once every problem is fixed.

## Long-running commands

A command that ran at least `notifications.long_command_seconds` (10 seconds by default) and finished anywhere but the
pane you were typing in is posted in Long-running commands: the command, and "Finished in 2m 03s" or "Exited 1 · 2m
03s". Its card shows in the pane's window, and clicking the card focuses the pane; while Jasper is in the background
its [system notification](#system-notifications) does (a history row has no click of its own). Closing the pane
expires it.
The same command finishing the same way within a minute counts on one entry, which then points at the pane it last
came from. While a command runs, Buddy (when it is on) shows it above his head; Buddy keeps only work that is still
going. See [configuration](configuration.md#finished-command-notifications).

## Plugin notifications

Plugins post notifications in their own groups, so you can tell them apart from Jasper's. When an error a plugin
reports has an exception behind it, its notification offers **Show more…**, which opens the Errors window at it. When
a plugin's background work finishes or fails, Jasper posts the outcome in the plugin's activities group; clicking it
runs the plugin's action for that work, if it has one.

At launch, each plugin that could not be loaded, was not started (it needs another version of Jasper, or a plugin it
requires is missing) or could not be read is reported in Plugin problems with **Open Plugins**, which opens
Settings › Plugins. A plugin that failed while starting is reported once, by its incident's notification.

## The Errors window

When something fails unexpectedly, in Jasper or in a plugin, Jasper records it as an incident and tells you with an
error notification: "Unexpected error in Jasper", or, for a plugin, "Remote failed in …" or "Remote failed to start".
**Show more…** opens the Errors window at that incident, and **Notifications: Show Errors** in the command palette opens
it too. A shell that could not start is recorded too: each failure is announced in the window it happened in, with its
folder and cause, and offers **Show more…**.

The list on the left holds this session's incidents, most recently seen first. When the same failure happens again, its
row counts up and moves to the top instead of a new row appearing. For a failure of Jasper or of a plugin, its
notification counts up too, at most once a second, and its card shows again at most once a minute. The right side shows
the selected incident: who failed and what it was doing, when it was first and last seen, its message and its stack
trace.

- **Copy** puts a report on the clipboard: Jasper's version, your OS and Java runtime, the plugin and its version for a
  plugin's incident, and the stack trace. Paste it into a bug report.
- **Disable Plugin** (only for a plugin's incident) disables the plugin from the next launch, as the checkbox on the
  Plugins page does.
- **Open Log Folder** shows Jasper's log files.
- **Clear** empties the list. Notifications of cleared incidents stay in the Notifications panel with their links
  disabled.

Incidents are kept in memory only, at most the latest 100, and are gone when Jasper quits. Incidents add nothing to the
log: an incident's message and stack trace are never written to it, and the log keeps the same short, scrubbed records
as before.

## System notifications

While Jasper is in the background, or running with every window closed, a notification whose group shows cards also
appears as the operating system's own: a banner on macOS, a balloon from Jasper's notification-area icon on Windows,
and a desktop notification through `notify-send` on Linux. Clicking it brings its window to the front (opening one
when none is open) and does what clicking its card would; a notification with nothing to run opens the Errors window
for an internal error or a plugin problem, or else the Notifications panel. Reading a notification in Jasper, its
expiry and its removal take the system notification away where the system allows it. While Jasper starts or a window
opens nothing is sent, since the card is about to show.

- **macOS**: only the packaged `Jasper.app` sends banners, with Jasper's icon. The first one asks for permission; allow
  Jasper in System Settings → Notifications. Gradle and IDE runs send none.
- **Windows**: the balloon comes from Jasper's icon in the notification area, which stays while Jasper is out of focus
  (and for as long as a resident Jasper has no window). A click on the balloon or on the icon opens the most recent
  unread notification.
- **Linux**: Jasper runs `notify-send` from `PATH` when there is one. When it supports actions, a click on the
  notification comes back to Jasper; otherwise the notification only informs.

An internal error or a plugin problem sends only its title, since its details can name files and hosts and system
notifications show on a locked screen. Turn system notifications off with `notifications.system = false`, or in
Settings › Notifications ([configuration](configuration.md#finished-command-notifications)).

## Shortcuts

**Notifications: Show History**, **Notifications: Show Errors** and **Notifications: Dismiss Cards** (closes every card
in the window) have no default shortcut; assign one in Settings › Keymap.
