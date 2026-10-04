# Jasper notifications

**Status:** Design approved in chat by the user on 2026-10-02, section by section; the written spec was approved the
same day. [Plan 1 (core)](../plans/2026-10-02-notifications-1-core.md) is implemented on branch
`claude/notifications` (worktree `.worktrees/notifications`, cut from main `e91512f6`); the plan's status banner
records the execution deviations and the final-review fixes. Plan 1 is merged to main (`63e3b95c`). [Plan 2
(incidents)](../plans/2026-10-02-notifications-2-incidents.md) is merged to main (`c02673cd`). [Plan 3
(SDK)](../plans/2026-10-03-notifications-3-sdk.md) is merged to main (`3c64ea5c`). [Plan 4 (long commands and
Buddy)](../plans/2026-10-03-notifications-4-commands-buddy.md) is implemented on branch `claude/notifications-4`.

**Replaces, once implemented:** the modal `JOptionPane` defaults of `WindowContent.onError` / `onInfo`, the SDK's
`Notices` (`PluginContext.notices()`), `CommandNotifier`'s private OS sink, and Buddy's finished-card history
(`BuddyNotice` `DONE` / `FAILED`, acknowledge, orphan). Builds on the
[finished-command notifications design](2026-09-16-jasper-finished-command-notifications-design.md) (whose threshold
and focused-pane rule are kept) and the [plugin SDK design](2026-09-21-jasper-plugin-sdk-design.md).

## Purpose

Jasper has no notification system. Every application and plugin error ends in a modal `JOptionPane` that blocks the
window; exceptions reach only the scrubbed log; plugin start failures surface only if the user opens Settings ›
Plugins; long-command and activity outcomes are visible only through Buddy (off by default) or a macOS banner in the
packaged app. Linux and Windows have no OS notifications at all, and clicking a macOS banner does nothing.

Jasper gets one IntelliJ-style notification system that the application uses for everything (errors, exceptions,
plugin problems, outcomes of long work) and plugins use through the SDK. It has three surfaces: a themed card in the
bottom-right of the window, a history rail panel, and an Errors window that shows stack traces.

## Decisions (user, 2026-10-02)

1. **One system for everything.** App errors and info, exceptions, plugin problems, finished long commands and plugin
   activity outcomes all become notifications. Buddy keeps only its live, in-progress cards and its animation; its
   drawer stops being a second history.
2. **In memory only.** Notifications and incidents reset on restart and are never persisted. History can be cleared
   during a session.
3. **In-app when focused, OS notification otherwise,** on all three operating systems, and clicking an OS notification
   brings the user to it.
4. **IntelliJ-style groups.** Every notification belongs to a group with a display mode — card (auto-hide), sticky
   card, history only, off — defaulted by whoever declares the group and overridable per group in Settings ›
   Notifications.
5. **One Errors window** listing the session's exceptions with the selected one's full stack trace, opened by a
   notification's **Show more…**.
6. **Unread shows as a badge on the Notifications rail button.** No status-bar indicator.
7. **No animations anywhere** — cards, badge and panel appear and disappear instantly; nothing blinks, fades or slides.
   The user called animation an unnecessary performance cost.
8. **Clean break** (MVP rule): `Notices` is removed, SDK 0.13.0, every bundled plugin migrated; no compatibility shim.
9. Approach A of three: an app-owned center with separate presenters. Rejected: a separate library module (the views
   need the app's rail, layered pane, auxiliary windows and theming anyway) and growing Buddy into the system (Buddy is
   an optional, JDK-only companion capped at 50 cards).

## 1. Architecture

**Model — `dev.jasper.app.notifications`** (existing package; grows; stays Swing-free; outgoing dependencies stay
`dev.jasper.buddy.notice`, `dev.jasper.buddy.view`, plus `dev.jasper.app.lifecycle`):

- `NotificationCenter` — the single app-wide store. EDT-confined; `post` is safe from any thread and hops to the EDT
  through an injected UI executor. Holds history, unread state, the group registry, pending cards and listeners
  (added, updated, expired, read, removed, cleared).
- Values: `Notification`, `Severity { INFO, WARNING, ERROR }`, `NotificationGroup(id, name, owner, defaultMode)`,
  `DisplayMode { CARD, STICKY, HISTORY, OFF }`, `NotificationAction(label, run, expires)`.
- `Delivery` — the pure routing rule (section 7), tested without windows like `CommandNotice` today.
- `Incidents` — the Errors window's model (section 5).
- `CommandNotifier` and `ActivityNotifier` stay here; they post outcomes to the center and keep feeding Buddy its live
  cards.

**Views — new package `dev.jasper.app.notificationui`** (outgoing: notifications, appearance, platform, lifecycle):
`CardLayer` (one card stack per window), `HistoryPanel` (the rail panel's view), `ErrorsView` (the Errors window's
content).

**Elsewhere:**

- `contributions` — `PanelEntry` gains an app-native badge (section 4).
- `workspace` — `WindowRail` paints badges; `WindowContent` gains a mount point that keeps a given component anchored to
  the bottom-right of the content area and drops the `JOptionPane` defaults. Workspace never imports `notificationui`.
- `platform` — `SystemNotifier` with macOS, Windows and Linux backends (section 9).
- `plugins` — the SDK adapter (`HostedNotifications`); plugin startup outcomes and `Containment` failures feed the
  center and incidents. SDK types stay confined to this package.
- `application` — composition: creates the center and each window's `CardLayer`, registers the built-in panel, opens
  the Errors window, routes OS-notification clicks, installs the uncaught-exception hook.

The package graph stays acyclic: `notificationui → notifications`, never the reverse; `application` hands the card
layer to `WindowContent` as a plain `JComponent`. Package-info files are updated with the new edges and owners.

## 2. The model

**`Notification`** — immutable value: `id` (UUID), `group`, `severity`, `title`, `content`, `icon` (optional; absent
means the severity default), `actions`, `activate` (optional default action), `incident` (optional incident id),
`source` (`jasper` or a plugin id), `targetWindow` (optional), `posted` (wall-clock time), `count` (coalesced
repeats, starts at 1), `expired`. Updates produce a new value under the same id.

**Text.** Titles, content and action labels are plain text and are never interpreted as HTML (no `<html>` label
rendering). Titles are non-blank, at most 200 characters; content may be empty and is cut to 4,000 characters;
at most 8 actions.

**History.** Newest first, capped at **500** entries; the oldest is dropped (with its card and any pending card).
Removing an entry also closes its card. **Clear All** removes every entry, closes every card, drops pending cards and
withdraws delivered macOS notifications.

**Read state** (global across windows):

- A notification is unread when posted (and again when a coalesced repeat arrives).
- It becomes read when its card is closed, clicked or one of its actions is used; when its OS notification is clicked;
  or when the history panel is shown in a focused window (which marks everything read).
- Auto-hiding a card does **not** mark it read. Expiry does not change read state.

**Coalescing.** A notification identical to an existing unexpired entry (same group, severity, title and content)
posted within **60 s** of that entry's last post bumps its `count`, refreshes its time, marks it unread and re-shows
its card per the group's mode, instead of adding an entry. Incidents use their own rule (section 5).

**Expiry.** A poster may expire a notification (SDK handle, or app code); a plugin's notifications all expire when it
stops. Expiry closes the card and keeps the history entry with its actions (and `activate`) disabled.

**Actions.** Run on the EDT. An action with `expires = true` (the default) expires its notification after running.
Using an action or clicking the body marks the notification read and closes its card. Exceptions thrown by app
actions are incidents with source `jasper`; by plugin actions, incidents attributed to the plugin.

## 3. The card

**Layout** (IntelliJ new-UI balloon):

- 16 px icon at the top-left: the poster's icon or the severity default (bundled new-UI info / warning / error icons).
- Bold title, wrapping; a muted `×N` after it when `count > 1`.
- Content, plain text, wrapping, at most **5 lines** with an ellipsis; the full text is always in the history panel.
- A bottom row of link-style action buttons: up to **3** shown, the rest in a `More ▾` menu. A notification with an
  incident gets **Show more…** last, opening the Errors window on that incident.
- A `✕` close button at the top-right: on hover for auto-hiding cards, always for sticky ones.
- Clicking the body (not an action) runs `activate` when present.
- Fixed width **360 px** (scaled with `UIScale`); height follows content.

**Placement.** In the target window's content area (the `WorkspaceRegions` bounds converted to the layered pane):
bottom-right corner, inset 12 px, above the status bar, over any open right or bottom panel. Mounted on the root's
layered pane at `PALETTE_LAYER` (above content; below the palette, popups and blocking overlays). Re-laid out from
component listeners on the layered pane and on the regions, as the command palette does.

**Stacking.** Newest at the bottom; at most **3** visible per window. A fourth pushes the oldest auto-hiding card out
of the stack (it stays in history). If all visible cards are sticky, a small `+N more` chip appears above them and
opens the history panel.

**Input.** The layer is transparent and sized to the stack only, so clicks elsewhere reach the terminal. Cards are not
focusable; they never take keyboard focus from the shell. Accessible names/descriptions carry severity, title and
content.

**Lifetime.**

- `CARD`: hides after **10 s** of the window being focused; the timer runs only while that window is focused and pauses
  while the pointer is over the card.
- `STICKY`: stays until closed, clicked, an action is used, or it expires.
- A card in a window that closes is discarded; its entry stays in history.

**No animation.** Cards appear and disappear instantly.

**Theming.** Chrome zone. New generated chrome keys `Jasper.notification.background` (`SURFACE_RAISED`),
`Jasper.notification.borderColor` (`BORDER`), `Jasper.notification.foreground` (`TEXT`),
`Jasper.notification.mutedForeground` (`TEXT_MUTED`) and `Jasper.notification.linkForeground` (`ACCENT`); a theme that
authors IntelliJ's `Notification.background` / `Notification.borderColor` wins. Shadow from `Popup.dropShadowColor`
(as the palette); rounded border. Severity is shown by the icon only, never a tinted background. Cards re-read their
colors on theme change.

**Actions in the keymap and palette** (unbound by default): **Notifications: Show History** (the panel's toggle) and
**Notifications: Dismiss Cards** (closes every card in the focused window, marking them read).

## 4. The history panel and the rail badge

**Panel.** The first built-in panel: id `jasper.notifications`, title "Notifications", icon
`BundledIcon.TOOLWINDOWS_NOTIFICATIONS`, default region RIGHT, movable like any panel; its toggle action is
**Notifications: Show History**. One panel instance per window, all showing the shared center.

- **Header:** **Clear All**.
- **Sections:** **New** (entries unread when the panel was shown) and **Earlier**, newest first in each. Entries stay
  in New until the panel is hidden.
- **Row:** icon; title (bold while unread) with `×N`; full content, wrapping, never truncated; a muted line with group
  name and time as `HH:mm` (fixed text — no relative-time timers); action links (disabled once expired), including
  **Show more…** for incidents (disabled once the incident has been cleared or dropped); a `✕` that removes the row.
- **Empty state:** "No notifications".
- Rows are ordinary components (actions must be clickable), not a `JList` renderer; the 500-entry cap bounds them.

**Badge.** `PanelEntry` gains `Badge(int count, Tone tone)`, `Tone { ACCENT, ERROR }`, changed through `Contributions`
with a `Kind.RAIL` notification. `WindowRail.PanelButton` carries it and paints it as a decorating icon. The
Notifications button shows a **dot** at the icon's top-right while anything is unread — `ERROR` tone when any unread
entry is an error, `ACCENT` otherwise. The count is in the tooltip and accessible description ("4 unread
notifications, 1 error"); the 28 px rail is too narrow for a legible number. The badge never blinks. Plugin panels do
not get badges through the SDK in this work.

**Side effect.** A built-in panel means the rail is always populated, so every window shows it by default; the
hidden-rail square framing (`02c4d38e`) applies only when the user hides the rail (View › Rail). With the rail hidden
there is no badge; cards still appear and the history is one action away.

## 5. The Errors window and incidents

**Window.** Singleton auxiliary window "Errors" through `AuxiliaryWindows.window(...)`: chrome theming, macOS title
bar, Close Window, remembered bounds (default about 900×560). Opened by **Show more…** (selecting that incident) and by
**Notifications: Show Errors** (palette/keymap, unbound).

- **Left:** incidents, newest first: source icon, summary (exception simple name and first line of the message), source
  name ("Remote", "Jasper"), `×N`, last-seen time. Up/down move through the list.
- **Right:** header with the source and what it was doing ("Remote · SFTP panel factory", "Jasper · thread
  `AWT-EventQueue-0`"), first and last seen, count; the full message, wrapping; the stack trace, read-only and
  monospaced, in `printStackTrace` form (causes, suppressed, `... N more`).
- **Buttons:** **Copy** (clipboard report: Jasper version, OS, Java runtime, plugin id and version when attributed,
  then the trace); **Disable Plugin** (plugin incidents only; the same runtime operation and restart semantics as the
  Plugins page checkbox, outcome posted to `jasper.plugins`); **Open Log Folder**; **Clear** (empties the list).

**Incident** — `id`, `source` (`jasper` or plugin id), `operation` (what it was doing), `thread`, exception class,
message, rendered trace text, `count`, `firstSeen`, `lastSeen`.

- The trace is rendered **once, at capture,** on the capturing thread, capped at **256 KiB**.
- Coalesced by source, exception class and the root cause's top **5** frames; a repeat bumps the count and last-seen
  time; the first trace is kept.
- Capped at **100** incidents in memory, oldest dropped. Never written to disk; the log keeps exactly its current
  scrubbed records.

**Each incident posts one ERROR notification** — group `jasper.errors` ("Internal errors") for Jasper,
`jasper.plugins` ("Plugin problems") for plugins; title like "Remote failed in its SFTP panel" or "Unexpected error in
Jasper"; content `<SimpleName>: <first line of message>`; actions **Show more…**, plus **Disable Plugin** for plugins.
A repeat updates that entry (`×N`, unread again) and re-shows its card **at most once a minute** per incident.

**Safety.** Capture never throws (a failed trace rendering falls back to the class name). A failure inside the
notification system itself is logged only, never notified, and capture is guarded against re-entry on the same
thread.

## 6. The SDK: 0.13.0, package `dev.jasper.sdk.notifications`

`dev.jasper.sdk.ui.Notices` and `PluginContext.notices()` are **removed**; `PluginContext.notifications()` replaces
them. Every bundled manifest declares `sdk = ">=0.13.0, <0.14"`.

```java
public interface Notifications {
    /** The plugin's default group: id = plugin id, named after the plugin, CARD. */
    NotificationGroup group();
    /** Declares another group; call during start so Settings can list it. */
    NotificationGroup group(GroupSpec spec);
}

public record GroupSpec(String id, String name, DisplayMode defaultMode) {}
public enum DisplayMode { CARD, STICKY, HISTORY, OFF }
public enum Severity { INFO, WARNING, ERROR }

public interface NotificationGroup {
    String id();
    NotificationHandle post(NotificationSpec spec);
    default NotificationHandle info(String title, String content) { ... }
    default NotificationHandle warning(String title, String content) { ... }
    default NotificationHandle error(String title, String content) { ... }
    /** Content is the cause's simple name and the first line of its message; Show more… opens the trace. */
    default NotificationHandle error(String title, Throwable cause) { ... }
}

public record NotificationSpec(Severity severity, String title, String content, Optional<Icon> icon,
        List<NotificationAction> actions, Optional<Runnable> activate,
        Optional<Throwable> cause, Optional<WindowHandle> window) {
    public static NotificationSpec of(Severity severity, String title) { ... }
    // withContent, withIcon, withAction, withActivate, withCause, withWindow
}

public record NotificationAction(String label, Runnable run, boolean expires) {
    /** An action that expires its notification after running. */
    public static NotificationAction of(String label, Runnable run) { ... }
}

public interface NotificationHandle {
    void expire();
    boolean expired();
}
```

**Contract.**

- `post` is safe from any thread and returns immediately. Actions and `activate` run on the UI thread inside the
  plugin's containment; what they throw becomes an incident attributed to the plugin.
- Validation as in section 2 (title, content, actions). Group ids follow the panel id pattern and must equal the plugin
  id or start with `<plugin-id>.`; `<plugin-id>.activities` is reserved for the host. Declaring the same id twice
  returns the same group.
- `post` after the context is closed throws `IllegalStateException`, like every other contributing call.
- `cause` — the host renders the trace immediately on the posting thread, records an incident attributed to the plugin
  and adds **Show more…**.
- `window` selects the card's window; absent means the window the user used last.
- When the plugin stops, its notifications expire.
- No capability is required. Plugins cannot observe other notifications.
- **Activity outcomes need no new API:** the host posts `ActivityHandle.succeed` / `fail` into the host-declared group
  `<plugin-id>.activities` ("<Plugin> activities", CARD), so muting a plugin's activity results never mutes its
  errors. `Activities` JavaDoc says so.

**Testkit.** `FakePluginHost.notifications()` records each post (group, spec, expired), the declared groups, and offers
helpers to run an action or the body. A shared contract suite runs against the fake and `HostedNotifications`.

**Migration.** The ~50 `notices()` uses in Remote, Vault, Editor, File Server and Snippets each become a proper title
plus content, passing `cause` wherever a `Throwable` is at hand (most of them), so they gain **Show more…** — e.g.
Tunnels' copy failure becomes title "Could not copy the address" with the cause. Plugins that want separate muting
declare groups (e.g. Remote `dev.jasper.remote.tunnels`). `HostedContext`'s own `openInEditor` / `browse` failures
post the same way.

**Docs:** `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `jasper-sdk/README.md`.

## 7. Routing and producers

**Jasper is focused** when any Jasper-owned window is active — terminal window, Settings, plugin window or the Errors
window (`KeyboardFocusManager`'s active window). Today only terminal windows count.

**Target window:** the poster's window, else the terminal window used last. With no window open (startup, resident
mode), cards wait until one opens and is focused.

**`Delivery`:**

| Mode | Jasper focused | Jasper in background |
|---|---|---|
| `OFF` | dropped (an incident is still recorded) | dropped |
| `HISTORY` | history + unread | history + unread |
| `CARD` | card in the target window | OS notification; history + unread; no card later |
| `STICKY` | card in the target window | OS notification; the card waits and appears when Jasper is next focused (in the target window if still open, else the focused one) |

With `notifications.system = false` the "OS notification" cells add nothing. Clicking an OS notification raises the
target window, runs `activate` if present (else opens the Errors window for an incident, or the history panel), and
marks the notification read.

**App groups and defaults:**

| Group | Name | Default |
|---|---|---|
| `jasper.general` | Jasper | `CARD` |
| `jasper.config` | Configuration | `STICKY` |
| `jasper.commands` | Long-running commands | `CARD` |
| `jasper.plugins` | Plugin problems | `CARD` |
| `jasper.errors` | Internal errors | `CARD` |

Every plugin also has its default group (`<plugin-id>`, `CARD`) and the host's `<plugin-id>.activities` (`CARD`).

**Producers:**

1. **App errors and info → `jasper.general`.** Everything that today reaches `WindowContent.onError` / `onInfo` or
   `JasperApplication.notice` / `info`: shell launch failure (with directory and cause, as an incident; the pane still
   closes, except that the window's only pane stays open showing its failure, so the window and its card survive —
   amended 2026-10-02, plan 1 Task 9 review), palette errors, theme install failure (today one modal per window; now one notification), rejected
   View-menu saves, config save / migrate / open / reveal failures, and the Migrate Config result (INFO).
2. **Config problems → `jasper.config`.** When the set from `ConfigurationController.shown()` gains a problem not
   notified before: title "config.toml has errors" (or "…has warnings"), content the first problem and "and N more",
   actions **Show Problems** (the existing dialog) and **Open config.toml**. When every problem is fixed, the
   notification expires. The status-bar indicator stays.
3. **Long-running commands → `jasper.commands`.** `CommandNotifier` keeps the existing rule for *whether* to post
   (ran at least `notifications.long_command_seconds`, did not finish in the focused pane); foreground vs background
   moves to `Delivery` and its private OS sink is removed. Title: the captured command. Content: "Finished in 2m 03s"
   (INFO) or "Exited 1 · 2m 03s" (WARNING). `activate` focuses the pane; the target is the pane's window.
4. **Activity outcomes → `<plugin-id>.activities`.** SUCCEEDED → INFO, FAILED → ERROR, `activate` = the activity's
   action; CANCELLED posts nothing. Connections stay Buddy-only (no bundled plugin uses them).
5. **Plugin problems → `jasper.plugins`.** After startup, one notification per FAILED or SKIPPED plugin, load failure,
   SDK mismatch and discovery problem (discovery problems are discarded today), each with **Open Plugins** and, when
   there is an exception, **Show more…**. `Containment` callback failures become incidents. Plugin install/update
   failures stay inline on the Plugins page.
6. **Uncaught exceptions → `jasper.errors`.** The default uncaught handler (all threads, the EDT included) records an
   incident.

**Unchanged, deliberately:** contextual inline feedback (Settings field errors and banners, palette error rows, theme
banners, plugin inline rows such as "tunnels.toml has errors", Plugins page badges), status-bar progress items, the
status-bar config indicator and its dialog, and confirm/input prompts (questions, not notifications).

## 8. Buddy

Buddy keeps live work only. `BuddyNotice` loses `DONE` and `FAILED` and the acknowledge / orphan machinery that kept
finished cards; `CommandNotifier` and `ActivityNotifier` dismiss a Buddy card when its work ends. The column and
drawer list live work: running, needs input, and connections. The working animation and greeting are unchanged.
Buddy docs (`docs/buddy-architecture.md`, `docs/configuration.md`) are updated.

## 9. OS notifiers

**`platform.SystemNotifier`** — `send(id, title, body, onClick)` and `withdraw(id)`; one daemon worker, closed at
shutdown (today's `jasper-notify` worker is never closed). Text is stripped of control characters and capped (title
120, body 300 characters). Incident notifications send their title only. Backends are injected functions, not
interfaces.

- **macOS** — the existing UserNotifications bridge (packaged `.app` only; Gradle/IDE runs send nothing, as today).
  The notification id becomes the request identifier; the delegate implements `didReceiveNotificationResponse`, calling
  back into Java (cached `JavaVM`, daemon-attached thread) with the id, which hops to the EDT and activates the
  notification. When a notification is read in-app, its banner is withdrawn with
  `removeDeliveredNotificationsWithIdentifiers`. The existing signing, staging and verification steps are unchanged.
- **Windows** — AWT `TrayIcon.displayMessage` with the matching `MessageType`. In resident mode the existing
  `ResidentTray` icon is reused; otherwise a Jasper tray icon is added on the first background notification and
  removed when Jasper is focused again. A click activates the most recent notification (`TrayIcon` cannot tell
  balloons apart).
- **Linux** — `notify-send`, resolved on `PATH` once. Arguments (no shell): `--app-name=Jasper`,
  `--icon=<app icon PNG written once to Jasper's data directory>`, `--`, title, body; body markup characters
  (`&`, `<`, `>`) are escaped. If `notify-send --help` lists `--action`, it adds `--action=default=Open --wait` and
  runs on the worker; stdout `default` activates the notification. Waiting processes are capped at 8 (the oldest is
  killed) and killed when their notification is read or Jasper shuts down. Without `--action` support it is
  fire-and-forget; without `notify-send` there are no OS notifications, logged once.

## 10. Settings and configuration

```toml
[notifications]
long_command_seconds = 10        # unchanged; 0 disables long-command notifications
system = true                    # OS notifications while Jasper is in the background
[notifications.groups]
"jasper.commands" = "history"    # card | sticky | history | off
"dev.jasper.remote.activities" = "off"
```

**Settings › Notifications** page: the system-notifications checkbox, the long-command threshold, and a table of every
known group (app groups and each running plugin's declared groups): name, owner, a mode combo with a "(default)"
marker, and Reset. Changes apply live. Entries for groups not currently known (a disabled plugin) are preserved. An
invalid mode is a config warning and the group's default applies.

## 11. Privacy

- The log is unchanged: no notification text, exception messages or traces are added to it; `AppLog`'s scrubbing
  rules stand.
- Exception messages and traces exist only in memory, in the Errors window and in what the user chooses to **Copy**.
- OS notifications for incidents carry only the title, because messages can contain paths or hostnames and banners
  show on lock screens. Other OS notifications carry the poster's title and content (long commands already show the
  command line today).
- Notification text is plain text, never HTML.

## 12. Testing

Headless, test-first, following the repository's patterns; injected clocks and schedulers, so no test sleeps.

- **Pure units:** the `Delivery` matrix; 60 s coalescing and once-a-minute incident cards; incident keys, caps and
  trace rendering (causes, suppressed, cap); read transitions; text limits; group id validation and reservation.
- **Center:** EDT confinement, any-thread posting, history cap with card/pending cleanup, Clear All, expiry on plugin
  stop.
- **Swing, headless:** `CardLayer` (stacking, the 3-card cap, `+N` chip, auto-hide running only while focused and
  pausing on hover, bounds that pass clicks through, no focus taken); `HistoryPanel` (sections, Clear All, row removal,
  disabled actions after expiry or cleared incident); `ErrorsView` (list, details, Copy content, buttons by source);
  light/dark render checks for cards and the rail badge, like the hidden-rail framing check.
- **SDK:** the contract suite against `FakePluginHost` and `HostedNotifications`; migrated plugin tests assert recorded
  notifications instead of `host.notices`.
- **OS backends:** the macOS headless fake-center native test gains identifiers, the click callback and withdrawal;
  Linux through an injected process runner (arguments, escaping, `--action` detection, `default` callback, missing
  binary, process cap); Windows tray behaviour through injected functions.
- **Gates:** `verifyApplicationArchitecture` (acyclic graph, SDK confinement), `verifySdkArchitecture`,
  `verifyPluginArchitecture`, doclint and compiled documentation examples, source hygiene — all within
  `./gradlew check`.
- **GUI acceptance by the user** (agents do not launch the GUI): cards in light and dark themes; target window with
  several windows open; background delivery and click-through on each OS; the Errors window; the badge.

## 13. Documentation

New user guide `docs/notifications.md`. Updated: `docs/configuration.md`, `docs/app-architecture.md` (package table),
`docs/app-maintenance.md` (notification identity section, producer rules), `docs/plugin-authoring.md`,
`docs/sdk-architecture.md`, `jasper-sdk/README.md`, `docs/buddy-architecture.md`, `docs/STATUS.md`.

## 14. Plans

One spec, six sequential plans, each a runnable deliverable:

1. **Core** — center, `Delivery` (complete except the OS notification itself, which plan 5 adds; sticky cards
   already wait for the user's return), `CardLayer`, history panel, rail badge, `jasper.general` and `jasper.config`
   producers; the `JOptionPane` defaults are removed.
2. **Incidents** — capture (uncaught handler, `Containment`, shell launch), the Errors window, `jasper.errors`.
3. **SDK 0.13.0** — `dev.jasper.sdk.notifications`, testkit and contract suite, removal of `Notices`, migration of all
   bundled plugins, plugin-problem notifications, activity outcomes.
4. **Long commands and Buddy** — `CommandNotifier` posts to the center; Buddy trimmed to live cards.
5. **OS notifiers** — macOS click and withdrawal, Windows tray, Linux `notify-send`, `notifications.system`.
6. **Settings** — `notifications.groups` and the Settings › Notifications page.

## 15. Out of scope

Persistence; animation of any kind; an API for plugins to observe notifications; SDK badges for plugin panels;
sounds; Do Not Disturb integration; filters or search in the history panel; native D-Bus or WinRT toasts;
notifications for connection state changes.
