# Resizable and sortable table columns

Status: design, awaiting approval (2026-10-28).

## Why

Plugin tables lay their columns out by fixed weights the plugin picks (SFTP's Name 136, Size 77, Modified 87). Long file
names in SFTP and long host names in Sessions get cut off, and the user cannot widen the column they care about. Clicking
a header already reports `UiEvent.Sorted` and the header can show an arrow, but no plugin answers it, so the rows never
reorder. The app's own file chooser (`dev.jasper.app.files.FileChooserPanel`) is the one place that sorts today.

This is the first of two projects the user asked for. The second, dragging files between the SFTP tool window and
Finder / Explorer, is designed separately afterwards.

## Goals

1. **Resize.** Every SDK `Nodes.Table` and every `Nodes.Tree` with columns lets the user drag the boundary between two
   header titles to change their widths. Built once in `jasper-ui` (`dev.jasper.ui.swing.Tables` and the tree's
   header), so Sessions, SFTP, Tunnels, File Servers, Indexer settings, Vault's manager and any third-party plugin get it.
2. **Widths survive a restart.** A width the user dragged is still there after quitting and relaunching Jasper.
3. **Sort.** Clicking a header sorts by that column; clicking the same header again flips ascending / descending. The
   header's arrow (already supported by `Column.sorted`) shows the column and direction.
4. **Sort survives a restart.**
5. Wired up in the bundled plugins that show column headers: Remote (Sessions tree, SFTP tables including the wide
   layout, Tunnels), File Servers, Indexer settings, plus any other bundled table that has a header.

## Sorting rules (decided by the user)

- **Folders sort with everything else** — no "folders always on top". This is how Finder's list view behaves.
- **Sessions:** hosts sort within their group; the groups keep their current order (default group last, others
  alphabetical, as `HostRows` does now).
- Clicking the sorted column flips its direction; clicking another column sorts by it ascending.
- **Sessions in the default look** has no columns (a plain list). It stays a list. Its header gains a sort menu button
  offering Name, Host and Port; the current column shows its direction, and choosing it again flips the direction.
  The default look and the Enterprise column tree share one Sessions sort (decided by the user, 2026-10-28).
- **SFTP sorts the whole folder**, not only the 200-row page shown: the order goes into the directory cache's query.

## Platforms

macOS and Windows must work fully. Linux is best-effort ("experimental" on supported desktops). Nothing in this project
is platform-specific beyond the resize cursor.

## Non-goals

- Reordering columns by dragging.
- Hiding / showing columns.
- Multi-column (secondary) sort chosen by the user.
- Any change to drag and drop of files (separate project).

## Anti-goals

- A plugin that ignores the new events must keep working exactly as today (weights from its spec, no sort), apart from
  gaining live resizing.
- No animation while resizing or re-sorting (Jasper has no animations).
- No Swing in plugins: plugins only see SDK records and events (`PluginPresentationCheck`).

## How it fits the SDK

The SDK's pattern is owner-driven state: the host reports an event (`Sorted`, `Expanded`), the plugin decides and
renders the new spec. This design keeps that pattern.

- **Sort:** unchanged protocol. `UiEvent.Sorted(id, columnId)` already exists; the plugin keeps a sort key and direction,
  orders the rows, and sets `Column.sorted` on the sorted column. The plugin persists the choice in its own data
  (`data/columns.properties`, through the SDK's `ColumnStateFile`; amended in the plan).
- **Resize:** a new event in the SDK, mirrored in `dev.jasper.ui.model.UiEvent` and converted in
  `dev.jasper.app.plugins.KitValues`, reports a finished drag on a table's or tree's header with the new width of every
  column, keyed by column id, in unscaled pixels (*my call*). `Column` gains an optional width (unscaled pixels,
  `@since` the new SDK version). When present it overrides the column's `weight` in a table, and the content sizing of a
  tree's trailing column. When absent, layout is exactly as it is today. The plugin stores the reported widths and passes
  them back in later renders, persisted in the same plugin data file.
  - *My call:* trees keep content sizing until the user drags a boundary. Trees ignore `weight` today, and making them
    honour it would change Sessions' look for plugins that ignore the event.
  - *My call:* the last column, or a tree's first column, absorbs the difference so the columns still fill the
    viewport. In a table, columns without a width still share what is left by weight.
  - *My call:* while a view is open the host keeps the user's dragged widths for that node, even if the plugin
    re-renders without them, as long as the column ids are unchanged. So a plugin that ignores the event still resizes
    for the life of the view; it just doesn't remember the widths across restarts.
  - This adds SDK API, so it is a minor SDK version bump, with `@since`, an updated `docs/sdk-reference.html`
    (`./gradlew sdkReference`), the testkit and the plugin-authoring docs. *My call:* it takes whichever minor version
    is next when the plan is written; the Browser plugin's plan 4 also targets 0.25.0, so check `main` first.

## App-owned tables (my call)

The resize lives in `jasper-ui`, so the app's own tables and column trees (File chooser, Settings tables) resize too.
Only the File chooser also remembers widths and sort across restarts, in app state next to its existing chooser state;
the Settings tables resize for the life of the view only. The Keymap's laned tree (`ShortcutTree`, fixed lanes from
metrics) is left out and does not resize.

## Resize behaviour (my calls, following IntelliJ)

- The drag handle is the boundary between two titles in the header; the cursor turns into the horizontal-resize cursor
  over it.
- Dragging moves width between the two neighbouring columns; the table's total width stays the viewport's width (no
  horizontal scroll bar appears because of resizing).
- Each column has a minimum width (the existing `minimums`, or about the title's width) so it cannot vanish.
- Double-clicking a boundary fits the column on its left to its widest visible cell and title.
- A numeric column keeps its existing rule of being at least as wide as its widest plain cell.
- The event is sent once when the drag ends, not on every mouse move.
- Resizing a header that also reports clicks must not report a `Sorted` click.

## Default sort (my call)

Before the user picks a sort, each table keeps today's order. SFTP's first-ever order is Name ascending (folders mixed
in, case-insensitive, natural order is not required). Sessions keeps today's alphabetical-within-group order, which is
Name ascending.

## Persistence scope (my call)

Widths and sort are remembered per table, not per host or per folder. Each column set is stored separately: SFTP's
narrow and wide layouts, and the classic (Enterprise) and default presentations' column lists, which differ in weights
and order. That gives one set per layout and presentation, one for Sessions, and so on. All SFTP browser instances share
the same widths and sort. Vault's manager already handles `Sorted`; it gains persistence.

## Left to the builder

- Exact event and field names, and the minimum-width rule.
- How the header detects the boundary hit area and paints (or doesn't) a divider, within the existing faces
  (`DefaultTableFace`, `ClassicTableFace`, the tree faces).
- How each bundled plugin compares each column's values (sizes as numbers, dates as dates, names case-insensitive).
- Test shape: headless tests in `jasper-ui` for the header drag and double-click, testkit coverage of the event,
  plugin tests for sorting and persistence.
