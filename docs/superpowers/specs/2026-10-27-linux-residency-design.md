# Linux background residency

**Status:** Draft for user approval. Direction and default behavior agreed on 2026-10-27; implementation has not started.

## Intent and agreed behavior

Give Linux the same warm-JVM benefit as macOS and Windows across common distributions and desktops. Residency is a
mode of the Jasper process, independent of whether the desktop can show a tray icon.

The user chose Java D-Bus integration with an X11 tray fallback, and agreed that:

- Linux defaults `system.run_in_background` to true, preserving an explicitly saved false value, including the
  existing `background.enabled` alias under the parser's existing precedence rules.
- Installed Jasper starts in the background at desktop login when the setting is on.
- Closing the last window leaves the JVM and plugins running; closing panes still closes their terminal sessions.
- Launching Jasper normally reopens it through the existing single-instance handoff. Quit inside the app exits it.
- Residency continues when no tray host is available. Tray availability can change while Jasper runs.

The proposals below are engineering decisions for approval with this document, not additional user requirements.

## Existing foundations and boundaries

`CoreSettings.runsInBackgroundByDefault` currently enables macOS and Windows only. `ApplicationBootstrap.residentRole`
already combines the setting with launch eligibility independently of tray support. `SingleInstance`, `InstanceLock`
and `HandoffSocket` implement one owner per Jasper home and forward subsequent launches before toolkit initialization.
`JasperApplication` already handles last-window close, reopening, normal Quit and warm-up. Its warm-up composes and
paints an undisplayed rehearsal from canned terminal data, without a shell or PTY. Reuse these paths.

`ResidentTray` currently uses AWT for macOS/Windows and returns a no-op on Linux. Its installation returns a cleanup
callback. `LoginItem` currently manages a macOS LaunchAgent or Windows Run value for a jpackage executable.
Linux notifications already have their own `LinuxNotifications` backend, using `notify-send`.

The terminal, UI, Buddy and SDK module boundaries remain governed by the original design and the terminal and
app/Buddy refactor amendments. Linux adapters belong to `dev.jasper.app.platform`, whose outgoing Jasper dependency
allowlist is empty; bootstrap supplies callbacks, and application owns their lifetime. The current full-windows
design governs reopening and workspace persistence. This design changes Linux residency defaults and integration.

The deliverable excludes a system daemon, root installation of autostart entries, GNOME extension installation,
new notification behavior, session preservation after logout, and a new plugin API. Flatpak/Snap confinement would
require separate packaging work. Existing macOS and Windows behavior is retained.

## Approaches

1. **Java D-Bus StatusNotifierItem/DBusMenu plus an AWT X11 fallback (selected direction).** Keeps ownership in the
   JVM and avoids depending on a GTK/Qt indicator library. Jasper owns protocol compatibility and reconnection.
2. **Native indicator integration plus a legacy fallback.** Delegates more behavior to a native indicator stack,
   but introduces library discovery, native event-loop integration and distribution packaging constraints.
3. **AWT tray only.** Reuses the most code but does not meet the modern desktop/Wayland coverage goal.

The selected approach detects capabilities, not distribution or desktop names. Debian, Ubuntu, Fedora, openSUSE,
Arch and derivatives should run the same integration code, within Jasper's supported runtime/architecture baseline.

## Defaults and login startup

Change the Linux default through the settings schema, not by rewriting existing user values. Test missing, true,
false and aliased values. Existing presentation defaults retain their normal precedence. Residency remains a
restart-applied setting; login registration follows the accepted saved setting immediately, as on other platforms.

Preserve current eligibility restrictions for alternate homes, `--config`, `--safe-mode` and `--plugin-dir`.
Development launches retain their existing eligibility rules and never register login startup without an installed
executable. A stale `--background` login invocation with residency off exits without opening a window when idle;
preserve the existing race handling that serves an already-accepted foreground handoff before exiting.

Extend `LoginItem`'s pure plan generation with an explicit environment input for Linux XDG resolution, keeping
existing callers/tests portable. Write a user entry at `$XDG_CONFIG_HOME/autostart/dev.jasper.background.desktop`,
falling back to `~/.config/autostart` when the environment value is empty or not absolute. This is the user's XDG
configuration root, not Jasper's home directory. Register only a recognized installed executable, using the existing
`jpackage.app-path` criterion. Portable and development distributions without that identity do not register.

The desktop entry uses `Type=Application`, `Name=Jasper`, `Terminal=false` and an `Exec` invoking that absolute
executable with `--background`. Encode `Exec` according to the Desktop Entry specification, including literal `%`,
quotes, spaces and backslashes; never pass it through a shell. A `TryExec` check suppresses startup after the executable
has gone. Do not restrict `OnlyShowIn` to a list of desktops. Write atomically where supported, create the parent
directory as needed, and log failures without failing app launch. Disabling removes Jasper's user entry.

Serialize and coalesce registration updates so a late older write cannot reverse a newer setting. Reconcile on an
eligible installed start and saved-setting change. Jasper's setting remains the authority for its generated entry;
users disable this feature in Jasper. A path change is corrected on the next eligible installed launch. Quit exits
the current process and leaves next-login registration intact. XDG autostart supplies no restart-after-Quit service.

## Tray ownership and desktop behavior

Linux gets an application-owned controller behind the existing resident indicator callback. Installation returns an
idempotent cleanup even when no icon exists yet: this cleanup owns discovery, retries, connections and future icons.
Do not reuse `ResidentTray`'s current `icon != null` flag as the application's residency decision.

The controller has three presentation states: no icon, StatusNotifierItem, or legacy X11. Prefer a successfully
registered modern item with a reported live host. A watcher service alone is insufficient evidence of a host.
When modern registration fails, an available legacy tray can be used. If neither works, remain resident without an
icon and keep discovery active. Switching withdraws the previous publication before publishing the replacement,
so Jasper never deliberately publishes two icons. A panel may still temporarily cache a removed item.

Observe watcher owner/host changes, and re-register after a panel restart. If both common watcher naming variants
exist, prefer `org.kde.StatusNotifierWatcher`; support the freedesktop variant as a fallback, without registering twice.
Check the selected owner's identity across asynchronous requests so replies from an old host cannot change state.
Subscribe before querying and reconcile the result to avoid missing startup races.

A vanished tray host is not a logout signal. D-Bus disconnects trigger bounded-backoff reconnection to the current
session bus; an initial absent/unusable bus still allows the X11 fallback. Use event subscriptions while connected.
If legacy capability changes cannot be reliably observed through public AWT APIs, a low-frequency probe is allowed;
the builder chooses and tests a capped interval. Coalesce repeated failures and log backend transitions, without
modal warnings or repeated notifications for an ordinary desktop with no tray.

Proposed Linux menu: **Open Jasper**, **New Window**, **Settings...**, separator, **Quit Jasper**. Use existing
`ResidentTray.Actions` callbacks, not duplicate application logic. Open raises the most recently active window or
reopens according to `system.reopen`; New Window follows the existing action's default-layout behavior. Settings
uses the existing callback. Quit follows normal close guards and complete shutdown. A canceled Quit leaves the
controller running. Direct activation invokes Open where the host supports it; the full menu remains available on
hosts that use the primary click to show a menu. The menu labels use Jasper's normal typographic conventions.

## D-Bus implementation and dependency gate

Use `dbus-java` core and exactly one Unix-socket transport. Keep D-Bus types inside the Linux platform adapter;
the application sees only its existing callbacks/cleanup. `org.kde.StatusNotifierItem` compatibility and
`com.canonical.dbusmenu` are required for current desktop hosts, alongside the freedesktop watcher compatibility
above. Export the standard properties, introspection and methods needed for a stable application-status item and
its menu, including menu layout/property queries and click events. Supply bundled icon pixmaps at multiple sizes
so hosts need not locate Jasper in an installed icon theme; verify ARGB encoding, dimensions and tooltip signatures.
Use fixed menu IDs and revisions, report valid standard defaults for unused features, and ignore unknown action IDs.

This design selects the library family, not an unverified version. The first implementation-plan task must pin a
published release and transport after checking JBR 25 compatibility, dependency/license inventory, service loading
in Jasper's packaged runtime, and filesystem plus abstract Unix-domain session-bus addresses. The upstream main
branch and its documentation are not proof that a particular release is available.

Prefer the JDK Unix-socket transport only if it meets that address matrix. Otherwise use the maintained junixsocket
transport and packaged native artifacts for Jasper's supported Linux architectures. The latter is the engineering
fallback, not a switch to a GTK native indicator. If neither meets those gates, revise this design before building
the integration. Tray protocols do not require file-descriptor passing. Add every shipped transitive jar/native
component to the existing notice inventory and prove other-platform package/startup compatibility.

## Legacy X11 implementation

Start with public AWT `SystemTray`/`TrayIcon` on the bundled JBR, sharing existing menu callbacks and icon assets.
Use runtime support detection and actual icon installation success; `DISPLAY` or a desktop name alone is not enough.
The fallback applies to an available X11 tray, including one reachable through XWayland if verified, not to native
Wayland by itself. Catch installation failure and leave residency running. Keep icon creation/removal on the EDT.

Before committing the production fallback, validate add/remove, late host availability, panel restart, menu
interaction, icon sizes and alpha on LXDE and a legacy tray configuration. Prefer public property notifications and
bounded probing to accessing JDK internals. If public AWT cannot meet recovery requirements, record the evidence
and amend the design for a narrowly scoped X11/JNA implementation; do not silently ship a nonrecovering fallback.

## Lifetime, concurrency and session boundaries

Start discovery once ordinary startup or background warm-up has settled, using the existing indicator hook. No
D-Bus connection or registration blocks first paint or the EDT. Serialize controller state on a bounded worker;
publish immutable icon/menu data to protocol callbacks. Post application actions to the EDT, checking the current
generation and closed state again at execution. Bound outbound calls, retry delays and queued work. No periodic
JIT exercise, shell keepalive or Linux memory pinning is introduced.

Cleanup first prevents new callbacks, then cancels retries, detaches subscriptions, removes any AWT icon on the EDT,
unexports objects and closes the owned connection. It must not wait indefinitely for the desktop. Headless tests
must never initialize a tray or open a desktop connection merely by loading a class.

Residency lasts within the graphical login session. It must not inhibit logout or keep a dead display alive. Preserve
the existing shutdown hooks and application cleanup; validate real logout/login with the supported sessions. A
session-bus interruption alone cannot be treated as logout because bus recovery is required. If native acceptance
shows that a supported session leaves Jasper alive after logout, session-lifetime integration is a release blocker
requiring a scoped design amendment. Do not introduce systemd lingering or an always-restarting user service.

The existing one-owner-per-Jasper-home rule remains. Multiple simultaneous graphical logins sharing one home do
not get independent resident processes in this deliverable; alternate homes retain their existing nonresident
restrictions. Keep process and tray discovery scoped to the launch's session environment.

## Validation and delivery

Plan this as two runnable deliverables: (1) Linux default residency and XDG autostart; (2) modern and legacy tray
integration together with backend switching. Each gets a complete implementation plan, TDD where behavior changes,
per-task implementer/reviewer work, and a final branch review under the repository workflow. The dependency and AWT
capability gates precede committing to detailed tray implementation steps.

Automated checks must cover:

- Defaults and explicit-off/alias preservation; eligibility; no-window background startup; warm reopening and Quit.
- XDG roots, desktop-entry escaping, executable disappearance, disable, failed writes and rapid setting changes.
- No bus/host, watcher without host, late host, registration failure, owner replacement, reconnect, fallback switching,
  cancellation and stale callbacks; no duplicate publications and no UI-thread blocking.
- Real wire-level exported signatures, Properties/GetAll, icon data, DBusMenu layout/events and registration using an
  isolated test bus and host fixtures on Linux, not only Java mocks. Test both required Unix address forms.
- Legacy recovery and cleanup; regression checks for Windows balloon ownership and macOS menu behavior.
- `./gradlew check`, dependency notices, architecture checks and headless packaged runtime verification. No SDK
  change is intended, so external-plugin rebuilding is needed only if the eventual plan changes that scope.

Native acceptance must record distro, desktop, display server, JBR, CPU architecture and tray-host version. Cover
KDE Plasma, XFCE, MATE, LXQt and LXDE, plus GNOME with and without its indicator extension. Use Ubuntu/Debian and
Fedora as baseline distro families, add openSUSE and Arch-family coverage, and distinguish required tested cases from
broader protocol compatibility. Include Wayland and X11 where the desktop supports them. Cover login, warm-up with
no visible rehearsal/shell, last-window close, reopen/activation, menus, explicit Quit, logout/login, panel restart,
extension enable/disable and background-disabled upgrades. Exact version selection belongs to the implementation
plan, informed by Jasper's then-supported releases. Headless wire tests cannot establish native desktop acceptance.

GUI runs and benchmarks require the user's authorization under AGENTS.md. Measure cold versus resident reopening
and idle resources only when authorized; the warm JVM is the mechanism, not a promise that Linux never pages memory.
No benchmark or GUI run is authorized by approval of this document alone.

Implementation updates the existing configuration, workspace, packaging and maintenance guides wherever behavior
changes, using small edits, plus settings help and generated SDK reference if its source guides change. Record plan
deviations in each plan banner and `docs/STATUS.md`. This draft itself does not change current-behavior guides.

## Research references

- [XDG autostart](https://specifications.freedesktop.org/autostart/latest/) and
  [Desktop Entry Exec encoding](https://specifications.freedesktop.org/desktop-entry-spec/latest/exec-variables.html).
- [StatusNotifier specification](https://specifications.freedesktop.org/status-notifier-item/latest-single/).
- [dbus-java](https://github.com/hypfvieh/dbus-java) and its
  [transport quickstart](https://hypfvieh.github.io/dbus-java/quick-start.html).
- [LXQt panel backends](https://github.com/lxqt/lxqt-panel),
  [XFCE status tray](https://xfce.org/about/tour416),
  [MATE panel history](https://github.com/mate-desktop/mate-panel/blob/master/NEWS), and
  [LXDE X11 tray implementation](https://github.com/lxde/lxpanel/blob/master/plugins/tray.c).
- [GNOME AppIndicator extension](https://extensions.gnome.org/extension/615/appindicator-support/).

These establish the interoperability direction; they are not evidence of tested Jasper desktop compatibility.
