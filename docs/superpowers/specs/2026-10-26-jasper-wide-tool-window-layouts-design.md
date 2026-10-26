# Wide tool window layouts for Remote and File Server — design

Approved by the user in chat, 2026-10-26. Follows the floating tool windows design
(`2026-10-23-jasper-floating-tool-windows-design.md`), whose out-of-scope list named "bundled panels adopting the new
placement API with alternate layouts (Sessions, SFTP, Tunnels, File Servers)" as a follow-up.

## Intent

A tool window in a window of its own has room its docked slot lacks. Remote's Sessions, SFTP and Tunnels tool windows
and File Server's File Servers tool window use that room, through SDK 0.22.0's `PanelHost.placement()` and
`PanelHost.onPlacement`, and return to their docked layout when docked again.

## What IntelliJ does (the research behind the rule)

- Services puts its tree beside the selected item's details when docked at the bottom and stacks them on a side:
  `ServiceViewManagerImpl` calls `setSplitOrientation(!toolWindow.getAnchor().isHorizontal())` on every
  `SetSideToolAndAnchor` change. Problems does the same for its preview (`ProblemsView`:
  `vertical = !window.getAnchor().isHorizontal()`).
- Float and Window do not change a tool window's content: a floating IntelliJ window keeps its stripe's anchor.
- Remote Host, IntelliJ's SFTP browser, offers file size, permissions and modification date as extra details.

## The rule (ruling: the user's choice)

A tool window takes its **wide** layout in a window of its own (Float or Window) **and** when docked at the bottom:
`placement.ownWindow() || placement.anchor() == Anchor.BOTTOM`. Anywhere else it takes its docked layout. The layout
follows every placement change and is right from the start for a panel built in a window of its own. A detached window
first opens at its docked size; the kit's `SIDEBAR_DETAIL` split narrows its list column in proportion until the user
widens the window.

## The layouts

Both looks (the default rows and the classic Enterprise composition) get a wide layout.

| Tool window | Wide, default look | Wide, classic look |
|---|---|---|
| Sessions | The list (search, groups, rows) beside a detail column: name, address, the host's fields (host, user, port, credentials, jump host, group, learned system, sessions) and Edit…, Connect or Open Shell, Connect in Split and Browse Files; a distribution's fields and Open, Open in Split | The column tree beside the selected row's Properties with Edit… and Connect |
| SFTP | The same tools, breadcrumbs and footer; the table's columns are Name, Size, Modified (date and time) and Permissions (`rwxr-xr-x`) | The same four columns |
| Tunnels | The list, with its switches, beside a detail column: title, state, the route strip the tunnel editor draws, the fields (type, via, and while up connections, traffic and uptime), Start or Stop, Edit… or Save as Tunnel…, Open in Browser and Copy Address | The table beside the tunnel's Properties and its status line |
| File Servers | The list beside the details, always shown: the card's contents, actions and activity log; the row menu has no Show or Hide Details | The table beside Properties, the status line and the activity; the row menu has no Show or Hide Activity |

Each detail column shows what its docked composition's detail shows: Sessions the selected row; Tunnels the selected
tunnel, else the first; File Servers (default look) the selected server, else the first; File Servers (classic) the
chosen server only. With nothing listed at all (no hosts or distributions, no tunnels, no servers) the wide layout is
the docked one, whose empty state fills the window.

## Versions

Remote 0.16.0 → 0.17.0, File Server 0.6.0 → 0.7.0 (SDK range unchanged, `>=0.22.0, <0.23`).

## Left to the builder

Node ids, the exact field captions and the order of the detail's buttons, within the layouts above.
