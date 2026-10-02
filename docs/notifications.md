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

The Notifications button on the rail opens the panel, which starts on the right; right-click the button to move it.
**Notifications: Show History** in the command palette does the same. The button shows a dot while anything is
unread: red when an error is unread. Its tooltip gives the count.

Unread notifications are listed under **New**, in bold. Opening the panel in a focused window marks everything read;
what it read stays under **New** until you close the panel, and everything else is under **Earlier**, newest first.
Each row shows the full details, the notification's group and time, and its links (disabled once the notification no
longer applies). `✕` removes one row; **Clear All** removes them all.

## Groups

| Group | What it covers | Shows as |
|---|---|---|
| Jasper | errors and results of Jasper's own commands: a shell that could not start, a theme that could not be applied, Migrate Config | card |
| Configuration | problems in `config.toml`, themes and plugin settings files | sticky card |

A configuration notification offers **Show Problems** (the same list as the status bar's configuration indicator)
and **Open config.toml**. It goes away by itself once every problem is fixed.

## Shortcuts

**Notifications: Show History** and **Notifications: Dismiss Cards** (closes every card in the window) have no
default shortcut; assign one in Settings › Keymap.
