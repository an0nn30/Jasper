# Wide Tool Window Layouts Implementation Plan

> **Status (2026-10-26):** Tasks 1–5 done, with these deviations from the plan text. Task 1: the list column's hint sits
> in the docked layout's `FOOTER` stack, not as a bare `Text`. Task 2: the default look's wide SFTP table is Name,
> Modified, Permissions, Size (Size last, at the trailing edge as docked; the design's SFTP row says Name, Size, Modified,
> Permissions, but the default table face has no cell inset and the right-aligned Size touched Modified), while
> Enterprise's keeps the design's order. Task 3: the default look's wide detail says "Host missing" in its state line
> (ahead of an up tunnel's state) and in its Via field, as the docked row does, and the classic Properties' State row
> uses the status line's words ("active", "inactive", "starting", "failed: …") instead of `TunnelRows.stateText`.
> Task 4: `revealLatestActivity` skips the classic wide layout while no server is chosen (nothing is drawn to reveal), and
> the `Selected` handler calls it, so a first choice shows the latest line. The first full check found the theme migration ledger
> (`docs/design/theme-engine/migration.csv`) needs a row per new test file, and the staged-panel fixture
> (`PanelSurfaceFixture`) turned out to reach a real `PanelSite` placement headlessly, so Task 6 (real-host renders of
> the wide layouts, and the ledger rows) was added before the full check, which became Task 7.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remote's Sessions, SFTP and Tunnels tool windows and File Server's File Servers tool window take a wide layout in a window of their own or docked at the bottom, and their docked layout elsewhere.

**Architecture:** Each plugin's `workstation.Layout` gains `static boolean wide(PanelPlacement)`. Each panel class gains `wide(boolean)`, which re-renders its view in a wide composition: a `Nodes.Split` of kind `SIDEBAR_DETAIL` (list column beside a detail column) for Sessions, Tunnels and File Servers, and wider table columns for SFTP. The panel factories pass `Layout.wide(host.placement())` at build time and from `host.onPlacement`. Node ids the docked layout uses keep their ids and kinds in the wide one, so the kit keeps those controls.

**Tech Stack:** Java 25, the Jasper SDK 0.22.0 kit descriptors (`dev.jasper.sdk.ui.Nodes`), JUnit 5 + AssertJ, the SDK testkit (`FakeKit`, `FakePluginHost.placePanel`).

**Spec:** `docs/superpowers/specs/2026-10-26-jasper-wide-tool-window-layouts-design.md`

## Global Constraints

- Wide when `placement.ownWindow() || placement.anchor() == Anchor.BOTTOM`; docked layout otherwise.
- With nothing listed (no hosts and no distributions; no tunnels; no servers) the wide layout is the docked one.
- Plugins describe ViewSpecs only (`PluginPresentationCheck`): no Swing in plugin code.
- Every id in a ViewSpec is unique in its view.
- Remote 0.16.0 → 0.17.0, File Server 0.6.0 → 0.7.0; `sdk = ">=0.22.0, <0.23"` unchanged.
- Commits end with the trailer lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>`.
- Never launch the GUI.

## Review Focus

1. A selection made docked survives the switch to wide and back (the detail and the card show the same row).
2. A filter that hides every row while wide leaves an empty detail state, not a stale detail.
3. The row a detail shows is deleted while wide: the detail follows the next row the docked rule picks.
4. A panel built while already in a window of its own (restored there at start-up) starts wide.
5. A move from one own-window mode to another (Float → Window) or between sides while floating keeps the wide layout without a docked frame in between.

Each line has its test in the task that owns the code (marked "Review Focus n").

---

### Task 1: Sessions — the list beside the detail

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/workstation/Layout.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/HostsPanel.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java` (`createPanel`, ~line 850)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/workstation/LayoutTest.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/SessionsWideTest.java` (new)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/PanelPlacementTest.java` (new)

**Interfaces:**
- Produces: `public static boolean Layout.wide(dev.jasper.sdk.ui.PanelPlacement placement)` (Remote), used by Tasks 2 and 3.
- Produces: `HostsPanel.wide(boolean)`, `HostsPanel.wide()`, and the public ids `COLUMNS = "sessions.columns"`, `LIST_COLUMN = "sessions.list"`, `DETAIL_COLUMN = "sessions.detail"`, `DETAIL_CONTENT = "sessions.detail.content"`, `DETAIL_EMPTY = "sessions.detail.empty"`, `DETAIL_FIELDS = "sessions.detail.fields"`, `DETAIL_ACTIONS = "sessions.detail.actions"`, `SPLIT_BUTTON = "sessions.detail.split"`, `BROWSE_BUTTON = "sessions.detail.browse"`.
- Produces: `PanelPlacementTest` with helpers `static PanelPlacement docked(Anchor)`, `static PanelPlacement own(PanelMode)` (anchor LEFT), and a `RemotePlugin started(FakePluginHost)` helper that starts `TestRemotePlugin` (as `SftpPluginTest` does) and adds a terminal window; Tasks 2 and 3 add test methods to it.

- [ ] **Step 1: Write the failing tests**

`LayoutTest.aToolWindowIsWideInAWindowOfItsOwnOrDockedAtTheBottom`: `wide` is false for `(LEFT,false,DOCKED)` and `(RIGHT,true,DOCKED)`; true for `(BOTTOM,false,DOCKED)`, `(BOTTOM,true,DOCKED)`, `(LEFT,false,FLOAT)` and `(RIGHT,true,WINDOW)`.

`SessionsWideTest` (fixture as `SessionsTreeTest`: `mac` = `Mac`, `192.168.1.119`, port 22, user `dustin`, `Auth.AGENT`, group `Local Network`; `minecraft` = `minecraft-local`, `192.168.1.124`; `FakeKit("dev.jasper.remote")`; package-private constructor with `() -> 0, 500_000_000L, layout`; all on `Kits.edt`):
- `wideIsTheListBesideTheSelectedHostsDetail` (ROWS): after `wide(true)` the root `Nodes.Panel`'s body is a `Nodes.Split` with kind `SIDEBAR_DETAIL` and child ids `[LIST_COLUMN, DETAIL_COLUMN]`; the root panel has no search row; `LIST_COLUMN` is a `Nodes.Panel` whose search is `SEARCH` and body `ROWS` (an `ItemList`); `find(CARD)` is empty; `CARD_NAME` reads `Mac`; `DETAIL_FIELDS` is a FORM `Card` whose caption texts read `Host, User, Port, Credentials, Group, Sessions` and whose value texts include `192.168.1.119`, `dustin`, `22`, `SSH agent` (credential labels `host -> "SSH agent"`), `Local Network`, `Not connected`; `CONNECT` reads `Connect`; pressing `CONNECT` then the action of `SPLIT_BUTTON` records `connect Mac`, `split Mac`.
- `aConnectedHostsDetailOpensItsShellAndALockedVaultWarns`: `setHostDetails(host -> "Ubuntu 24.04", host -> 2)` and credential labels `"Vault locked"`: `CONNECT` reads `Open Shell`; the fields add `System` = `Ubuntu 24.04`; `Sessions` = `2 sessions`; the `Credentials` value text has severity `WARNING`.
- `aDistributionsDetailOpensIt`: `setDistros` with one WSL `ShellProfileInfo` `Ubuntu` (detail `WSL 2`), `selectDistro` it, `wide(true)`: `CONNECT` reads `Open`; a field `Command` = `wsl.exe -d Ubuntu`; `SPLIT_BUTTON` reads `Open in Split` and its action records the split-open callback.
- `aSearchThatHidesEveryRowEmptiesTheDetail` (Review Focus 2): wide, then `Edited(SEARCH, "zzz")`: `DETAIL_EMPTY` is a `Nodes.Empty` titled `No Session Selected`; `HINT` reads `No hosts match`.
- `theSelectionSurvivesTheSwitchBothWays` (Review Focus 1): `select(minecraft.id())`, `wide(true)`: `CARD_NAME` reads `minecraft-local`; `wide(false)`: `find(COLUMNS)` empty and `CARD_NAME` reads `minecraft-local`.
- `aDeletedHostsDetailFollowsTheNextRow` (Review Focus 3): wide, `select(minecraft.id())`, `setHosts(List.of(mac), …)`: `CARD_NAME` reads `Mac`.
- `withNothingToListTheWideLayoutIsTheDockedOne`: `setHosts(List.of(), …)`, `wide(true)`: `find(COLUMNS)` empty; `HINT` starts with `No hosts yet`.
- `classicWideIsTheTreeBesideProperties` (CLASSIC): wide: the split's `LIST_COLUMN` body is the `ROWS` `Tree` and its search is `SEARCH` ("Filter sessions"); `PROPERTIES` sits inside `DETAIL_COLUMN` titled `Properties — Mac`; `EDIT` reads `Edit…`, `CONNECT` `Connect`.

`PanelPlacementTest.theSessionsPanelIsWideInAWindowOfItsOwnAndAtTheBottom`: start, `plugin.store().put(mac)`, settle, `openPanel(RemotePlugin.PANEL)`; with `view = host.panelView(PANEL)`: `find(view, COLUMNS)` empty; after `placePanel(own(FLOAT))` present; after `placePanel(own(WINDOW))` still present (Review Focus 5); `placePanel(docked(LEFT))` absent; `placePanel(docked(BOTTOM))` present; `host.failures()` empty.
`PanelPlacementTest.aSessionsPanelBuiltInAWindowOfItsOwnStartsWide` (Review Focus 4): `placePanel(PANEL, own(WINDOW))` before `openPanel`: `COLUMNS` present.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests '*LayoutTest' --tests '*SessionsWideTest' --tests '*PanelPlacementTest'`
Expected: compilation fails (`wide`, `COLUMNS` undefined).

- [ ] **Step 3: Implement**

- `Layout.wide`: Javadoc says a tool window takes its wide layout in a window of its own or docked at the bottom, where IntelliJ's Services tool window also puts its details beside its list.
- `HostsPanel.wide(boolean value)`: no-op when unchanged and after `close()`; otherwise re-renders. `node()` picks the wide composition when `wide && !(hosts.isEmpty() && distros.isEmpty())`.
- Wide default-look root: `Panel(ROOT, the docked header, Optional.empty(), Split(COLUMNS, SIDEBAR_DETAIL, [list, detail]), Optional.empty())`. The list column is `Panel(LIST_COLUMN, hidden header with the same count, Optional.of(search), list, the docked hint Text or nothing)`; the detail column is `Panel(DETAIL_COLUMN, hidden header "Details", Optional.empty(), detail, Optional.empty())`. Hidden headers as `ErrorsView.hiddenHeader` (`ControlState(false, true, false, "")`).
- Detail, host: `Stack(DETAIL_CONTENT, DETAIL, [heading, fields, actions])`. Heading: `Stack("sessions.detail.heading", NAVIGATION, [Icon(CARD_TILE, "server", NEUTRAL, PROMPT, "SSH host"), Stack("sessions.detail.identity", VERTICAL, [Text(CARD_NAME, name, DETAIL_TITLE), Text(CARD_ADDRESS, label(), MONO_CAPTION)]), Button(EDIT, "Edit…", SECONDARY, EDIT, "Edit session"), Button(CONNECT, "Connect" | "Open Shell" with icon "terminal", PRIMARY, CONNECT)])`. Fields: FORM `Card(DETAIL_FIELDS)` of rows `Stack("sessions.detail.<key>.row", VERTICAL, [Text(caption, CAPTION), Text(value)])` (the Vault manager's `detailValue` shape) for Host, User, Port (MONO_SMALL), Credentials (`credentialLabels`, WARNING when `Vault locked`), Jump host (when `via` is set), Group (the host's group, else the default group name), System (`metadata`, when not blank), Sessions (`Not connected`, `1 session`, `n sessions`). Actions: `Stack(DETAIL_ACTIONS, ACTIONS, [Button(SPLIT_BUTTON, "Connect in Split", SECONDARY, MENU + "split:" + rowId), Button(BROWSE_BUTTON, "Browse Files", SECONDARY, MENU + "browse:" + rowId) only while browsing is wired])`.
- Detail, distribution: the same heading with icon `terminal`, `CARD_ADDRESS` the version, `CONNECT` `Open` (icon `terminal`); fields Distribution, Version, Command (MONO_SMALL), Sessions; actions `[Button(SPLIT_BUTTON, "Open in Split", SECONDARY, MENU + "open-split:" + rowId)]`.
- Detail, nothing selected: `Empty(DETAIL_EMPTY, Icon(DETAIL_EMPTY + ".icon", "server", NEUTRAL, EMPTY, "Sessions"), "No Session Selected", "Select a host or a WSL distribution to see its details.", List.of())`.
- Wide classic root: `Panel(ROOT, the classic header, Optional.empty(), Split(COLUMNS, SIDEBAR_DETAIL, [Panel(LIST_COLUMN, hidden header, Optional.of(filter), tree, hint), Panel(DETAIL_COLUMN, hidden header, Optional.empty(), Stack(DETAIL_CONTENT, DETAIL, classicDetails() or [Text(DETAIL_EMPTY, "Select a session to see its properties.", DESCRIPTION)]), Optional.empty())]), Optional.empty())`.
- Class Javadoc: one paragraph on the wide layout.
- `RemotePlugin.createPanel`: `panel.wide(Layout.wide(host.placement()))` before returning, and `host.onPlacement(placement -> panel.wide(Layout.wide(placement)))`.

- [ ] **Step 4: Run the tests**

Run: the Step 2 command, then `./gradlew :jasper-plugin-remote:test`
Expected: PASS, no other failures.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote
git commit -m "Remote: the Sessions tool window lays its list beside the selected row's detail when wide"
```

### Task 2: SFTP — the wide table

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpPanel.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java` (`create`)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/sftp/SftpWideTest.java` (new)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/PanelPlacementTest.java`

**Interfaces:**
- Consumes: `Layout.wide(PanelPlacement)`; `PanelPlacementTest`'s helpers.
- Produces: `SftpPanel.wide(boolean)`, `SftpPanel.wide()`, `static String SftpPanel.permissions(FileEntry)`, `static final List<Column> WIDE_COLUMNS`, `CLASSIC_WIDE_COLUMNS`.

- [ ] **Step 1: Write the failing tests**

`SftpWideTest` (entries `src` DIRECTORY 0755 and `a.txt` FILE size 1200, 0644, a fixed `modifiedMillis`; `showPage("prod", "/srv", entries, 0, 2, false)`; on `Kits.edt`):
- `permissionsReadAsLsDoes`: `0755` → `rwxr-xr-x`, `0640` → `rw-r-----`, `0` → `---------`, `-1` → `—`.
- `wideTheTableShowsModifiedAndPermissions` (ROWS): docked column titles `Name, Size`; `wide(true)`: `Name, Size, Modified, Permissions` with ids `name, size, modified, permissions`; `a.txt`'s cells: `modified` = `SftpPanel.modified(entry)`, `permissions` = `rw-r--r--`; a selection of `a.txt` made before the switch is still the table's `selectedIds`; `wide(false)`: `Name, Size` again.
- `classicWideAddsPermissionsAndTheFullModifiedTime` (CLASSIC): docked `Name, Size, Modified` with `modified` = `shortModified`; wide: `Name, Size, Modified, Permissions` with `modified` = `modified(entry)`.
- `theCopyToHostBrowserNeverWidens`: `SftpPanel.destination(...)` has no `wide` effect: its `destinationBody()` table keeps `Name, Size` (call `wide(true)` on it first).

`PanelPlacementTest.theSftpTableWidensInAWindowOfItsOwnAndAtTheBottom`: open `SftpUi.PANEL`, `showPage` on `SftpTestAccess.panel(plugin.sftp())`; the `FILES` table's column count is 2 docked left, 4 after `placePanel(own(FLOAT))`, 2 after `docked(LEFT)`, 4 after `docked(BOTTOM)`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests '*SftpWideTest' --tests '*PanelPlacementTest'`
Expected: compilation fails.

- [ ] **Step 3: Implement**

- `WIDE_COLUMNS`: name `Name` BODY 2.4; size `Size` DESCRIPTION numeric 1; modified `Modified` DESCRIPTION 1.6; permissions `Permissions` MONO_SMALL 1.2. `CLASSIC_WIDE_COLUMNS`: name 136, size 77, modified `Modified` MONO_SMALL 120, permissions `Permissions` MONO_SMALL 90.
- `permissions(entry)`: `—` when `permissions() < 0`, else the nine `rwx` characters of `permissions() & 0777` (`SftpEndpoint` keeps only those bits).
- `wide(boolean)`: no-op when unchanged, closed or `destination`; re-renders. `table()` and `classicTable()` take the wide columns and cells when `wide && !destination`. The tooltips stay.
- `SftpUi.create`: `panel.wide(Layout.wide(host.placement()))` and `host.onPlacement(placement -> panel.wide(Layout.wide(placement)))`.
- Class Javadoc: one sentence on the wide table.

- [ ] **Step 4: Run the tests**

Run: the Step 2 command, then `./gradlew :jasper-plugin-remote:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote
git commit -m "Remote: the SFTP table adds Modified and Permissions columns when wide"
```

### Task 3: Tunnels — the list beside the detail

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsPanel.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelEditor.java` (share the route strip)
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java` (`create`)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsWideTest.java` (new)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/PanelPlacementTest.java`

**Interfaces:**
- Consumes: `Layout.wide(PanelPlacement)`; `PanelPlacementTest`'s helpers.
- Produces: `TunnelsPanel.wide(boolean)`, `TunnelsPanel.wide()`; ids `COLUMNS = "tunnels.columns"`, `LIST_COLUMN = "tunnels.list"`, `DETAIL_COLUMN = "tunnels.detail"`, `DETAIL_CONTENT = "tunnels.detail.content"`, `DETAIL_NONE = "tunnels.detail.none"`, `DETAIL_TITLE = "tunnels.detail.title"`, `DETAIL_STATE = "tunnels.detail.state"`, `DETAIL_ROUTE = "tunnels.detail.route"`, `DETAIL_FIELDS = "tunnels.detail.fields"`, `DETAIL_ACTIONS = "tunnels.detail.actions"`, `DETAIL_TOGGLE = "tunnels.detail.toggle"`, `DETAIL_EDIT = "tunnels.detail.edit"`, `DETAIL_BROWSE = "tunnels.detail.browse"`, `DETAIL_COPY = "tunnels.detail.copy"`, `PROPERTIES = "tunnels.properties"`.
- Produces: `static Nodes.Node TunnelEditor.route(String id, TunnelKind kind, String listen, String via, String target)`: the ROUTE stack the editor draws, steps `id + ".from"`, `".via"`, `".to"`; the editor's own `route(via)` calls it.

- [ ] **Step 1: Write the failing tests**

`TunnelsWideTest` (fixture as `TunnelsTableTest`; `db` = LOCAL `localhost:5432 → db.internal:5432` and `web` = LOCAL `8080 → localhost:80`, both via host name `prod`; a fixed clock through `setClock`; on `Kits.edt`):
- `wideIsTheListBesideTheFirstTunnelsDetail` (ROWS): body `Split(SIDEBAR_DETAIL)` `[LIST_COLUMN, DETAIL_COLUMN]`; `LIST_COLUMN`'s search is `FILTER` and its body the `ROWS` `ItemList`, whose items keep their switch toggles; with nothing selected `DETAIL_TITLE` reads `db` (names sort `db` before `web`); `DETAIL_STATE` `Stopped`; route texts `DETAIL_ROUTE.from.where` `localhost:5432`, `.via.where` `prod`, `.to.where` `db.internal:5432`; fields `Type` = `Local`, `Via` = `prod`; `DETAIL_TOGGLE` reads `Start` and its action records `start db`; `DETAIL_EDIT` `Edit…` records `edit db`.
- `anUpTunnelsDetailCarriesItsCounters`: `db` Up since the clock minus 5 minutes, port 5432, `new Counters()`: `DETAIL_STATE` `Up for 5 min`; fields add `Connections` = `0`, `Traffic` = `↓0 B ↑0 B`, `Uptime` = `5 min`; `DETAIL_TOGGLE` `Stop` records `stop db`; `DETAIL_BROWSE` is enabled and records `browse db`.
- `aFailedTunnelsStateIsDanger`: `Failed("Connection refused")`: `DETAIL_STATE` reads `Connection refused` with severity `DANGER`.
- `aQuickForwardsDetailOffersSaveAsTunnel`: a quick forward only: `DETAIL_EDIT` reads `Save as Tunnel…` and records `save …`.
- `theDetailFollowsTheSelection`: `Selected(ROWS, {rowId(web)})`: `DETAIL_TITLE` `web`.
- `aFilterThatHidesEveryTunnelEmptiesTheDetail` (Review Focus 2): `Edited(FILTER, "zzz")`: `DETAIL_NONE` is an `Empty` titled `No Tunnel Selected`.
- `aDeletedTunnelsDetailFollowsTheFirst` (Review Focus 3): select `web`, `setTunnels([db], …)`: `DETAIL_TITLE` `db`.
- `withNoTunnelsTheWideLayoutIsTheDockedOne`: `find(COLUMNS)` empty, `EMPTY` present.
- `classicWideIsTheTableBesidePropertiesAndTheStatusLine` (CLASSIC): `LIST_COLUMN` body the `ROWS` `Table`; `PROPERTIES` titled `Properties — db` with rows `Type` `Local`, `Local` `:5432`, `Remote` `db.internal:5432`, `Via` `prod`, `State` `stopped`; `FOOTER` reads `db — inactive. Click to toggle.`; docked again, `FOOTER` stays and `find(PROPERTIES)` is empty.
- `theEditorsRouteIsTheSharedStrip`: an existing `TunnelEditorTest` route assertion still passes (no change to the editor's ids).

`PanelPlacementTest.theTunnelsPanelIsWideInAWindowOfItsOwnAndAtTheBottom`: put one tunnel through `plugin.tunnelStore().put(...)`, open `TunnelsUi.PANEL`; `COLUMNS` absent docked left, present `own(FLOAT)`, absent `docked(LEFT)`, present `docked(BOTTOM)`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests '*TunnelsWideTest' --tests '*TunnelEditorTest' --tests '*PanelPlacementTest'`
Expected: compilation fails.

- [ ] **Step 3: Implement**

- Wide when `wide && !(saved.isEmpty() && quick.isEmpty())`. The detail's tunnel is `selected()`, else the first tunnel `shown()` lists.
- Default-look root: `Panel(ROOT, header, Optional.empty(), Split(COLUMNS, SIDEBAR_DETAIL, [Panel(LIST_COLUMN, hidden header, Optional.of(filter), list, the no-match footer), Panel(DETAIL_COLUMN, hidden header, Optional.empty(), detail, Optional.empty())]), Optional.empty())`.
- Detail: `Stack(DETAIL_CONTENT, DETAIL, [heading, route, fields, actions])`. Heading `NAVIGATION`: `Icon("tunnels.detail.icon", "tunnel", NEUTRAL, PROMPT, "Tunnel")`, identity `[Text(DETAIL_TITLE, TunnelRows.title, DETAIL_TITLE), Text(DETAIL_STATE, state line, DESCRIPTION, severity)]`, `Button(DETAIL_EDIT, "Edit…" | "Save as Tunnel…", SECONDARY, MENU + "edit"|"save" + ":" + rowId)`, `Button(DETAIL_TOGGLE, "Stop" while running else "Start", PRIMARY, MENU + "stop"|"start" + ":" + rowId)`. State line: Stopped `Stopped` (or `Host missing · Edit to choose a host`, WARNING, when the host is gone); Starting its stage; Up `Up for <TunnelRows.duration>` or its counters' warning (WARNING); Failed its reason (DANGER). Route: `TunnelEditor.route(DETAIL_ROUTE, kind, listen, host name or "Host missing", target)` with listen `bracketed(bindAddress) + ":" + TunnelRows.port(...)` and target `bracketed(targetHost) + ":" + targetPort`. Fields (FORM card, caption/value rows as Task 1): Type (`Local`, `Remote`, `Dynamic`), Via, and while Up Connections, Traffic (`"↓" + bytes(received) + " ↑" + bytes(sent)`), Uptime. Actions `ACTIONS`: `Button(DETAIL_BROWSE, "Open in Browser", …, MENU + "browse:" + rowId)` enabled as the row menu's item, `Button(DETAIL_COPY, "Copy Address", …, MENU + "copy:" + rowId)` enabled as the row menu's item.
- Nothing to show: `Empty(DETAIL_NONE, Icon(DETAIL_NONE + ".icon", "tunnel", NEUTRAL, EMPTY, "Tunnels"), "No Tunnel Selected", "", List.of())`.
- Classic root: `Panel(ROOT, classic header, Optional.empty(), Split(COLUMNS, SIDEBAR_DETAIL, [Panel(LIST_COLUMN, hidden header, the docked search row, table, Optional.empty()), Panel(DETAIL_COLUMN, hidden header, Optional.empty(), Stack(DETAIL_CONTENT, DETAIL, [Properties(PROPERTIES, "Properties — " + title, rows), Text(FOOTER, footerText, CAPTION)] or [Text(DETAIL_NONE, "Select a tunnel to see its properties.", DESCRIPTION)]), Optional.empty())]), Optional.empty())`. Property rows: Type, Local, Remote (technical), Via, State (`TunnelRows.stateText`), and while Up Connections, Traffic, Uptime. Share the Local and Remote values with `tableRow`.
- `wide(boolean)`: no-op when unchanged or closed; re-renders.
- `TunnelsUi.create`: `panel.wide(Layout.wide(host.placement()))` and `host.onPlacement(...)` as Task 1.
- Class Javadoc: one paragraph on the wide layout.

- [ ] **Step 4: Run the tests**

Run: the Step 2 command, then `./gradlew :jasper-plugin-remote:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote
git commit -m "Remote: the Tunnels tool window lays its list beside the tunnel's detail when wide"
```

### Task 4: File Servers — the list beside the details

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/workstation/Layout.java`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/FileServersPanel.java`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java` (`createPanel`)
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/workstation/LayoutTest.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/FileServersWideTest.java` (new)
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java`

**Interfaces:**
- Produces: `public static boolean Layout.wide(PanelPlacement)` (File Server), `FileServersPanel.wide(boolean)`, `FileServersPanel.wide()`; ids `COLUMNS = "fileserver.columns"`, `LIST_COLUMN = "fileserver.list"`, `DETAIL_COLUMN = "fileserver.detail"`, `DETAIL_CONTENT = "fileserver.detail.content"`, `DETAIL_NONE = "fileserver.detail.none"`.

- [ ] **Step 1: Write the failing tests**

`LayoutTest.aToolWindowIsWideInAWindowOfItsOwnOrDockedAtTheBottom`: as Task 1's.

`FileServersWideTest` (fixture as `FileServersTableTest`, server `test`, HTTP, stopped, address `http://127.0.0.1:8080`, a second server `docs`):
- `wideIsTheListBesideTheDetailsAlwaysShown` (ROWS): body `Split(SIDEBAR_DETAIL)` `[LIST_COLUMN, DETAIL_COLUMN]`; `LIST_COLUMN` body the `ROWS` `ItemList`; `find(DETAILS)` empty; inside `DETAIL_CONTENT`: `fileserver.card.name` reads `test`, `fileserver.activity.log` present, `fileserver.details.toggle` reads `Start` and records `toggle`.
- `wideTheRowMenuHasNoShowDetails`: the row menu's labels lack `Show Details` and `Hide Details` and end with `Show Fingerprints…`; `wide(false)`: `Show Details` is back.
- `dockedAgainTheDetailsCardIsHiddenUntilShown` (Review Focus 1): select `docs`, wide, docked: `DETAILS` is present, not visible, and its `fileserver.card.name` reads `docs`.
- `aDeletedServersDetailsFollowTheFirst` (Review Focus 3): wide, select `docs`, `setServers([test])`: `fileserver.card.name` reads `test`.
- `withNoServersTheWideLayoutIsTheDockedOne`: `find(COLUMNS)` empty, `EMPTY` present.
- `classicWideIsTheTableBesidePropertiesAndActivity` (CLASSIC): before a choice `DETAIL_NONE` reads `Select a file server to see its properties.`; after `Selected(ROWS, {row})`: `PROPERTIES` titled `Properties — test`, `FOOTER` reads `test — stopped. Click to toggle.`, `fileserver.activity.log` present; the row menu lacks `Show Activity`.

`FileServerPluginTest.theFileServersPanelIsWideInAWindowOfItsOwnAndAtTheBottom`: put one definition through `plugin.store().put(...)`, settle, `openPanel`; `COLUMNS` absent docked left, present after `placePanel(PANEL, (LEFT,false,FLOAT))`, absent `(LEFT,false,DOCKED)`, present `(BOTTOM,false,DOCKED)`; and a panel placed `(LEFT,false,WINDOW)` before it is first opened starts with `COLUMNS` (Review Focus 4).

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests '*LayoutTest' --tests '*FileServersWideTest' --tests '*FileServerPluginTest'`
Expected: compilation fails.

- [ ] **Step 3: Implement**

- `Layout.wide`: as Task 1's, with its Javadoc.
- Wide when `wide && !servers.isEmpty()`.
- `details(server)` keeps its card; its children come from a new `private List<Nodes.Node> detailNodes(ServerRegistry.View)`, which the wide detail column wraps in `Stack(DETAIL_CONTENT, DETAIL, …)`.
- Default-look root: `Panel(ROOT, header, Optional.empty(), Split(COLUMNS, SIDEBAR_DETAIL, [Panel(LIST_COLUMN, hidden header, Optional.empty(), list, Optional.empty()), Panel(DETAIL_COLUMN, hidden header, Optional.empty(), Stack(DETAIL_CONTENT, DETAIL, detailNodes(selected view)), Optional.empty())]), Optional.empty())`.
- Classic root: `Panel(ROOT, header, the docked notice row, Split(COLUMNS, SIDEBAR_DETAIL, [Panel(LIST_COLUMN, hidden header, Optional.empty(), table, Optional.empty()), Panel(DETAIL_COLUMN, hidden header, Optional.empty(), Stack(DETAIL_CONTENT, DETAIL, chosen ? [properties, FOOTER text, activity()...] : [Text(DETAIL_NONE, "Select a file server to see its properties.", DESCRIPTION)]), Optional.empty())]), Optional.empty())`.
- While wide, `menu(server)` leaves out the `second` separator and the details item (so `classicMenu` leaves out Show/Hide Activity); `revealLatestActivity` reveals while `detailsOpen` or wide.
- `wide(boolean)`: no-op when unchanged or closed; re-renders and reveals the latest activity.
- `FileServerPlugin.createPanel`: `panel.wide(Layout.wide(host.placement()))` and `host.onPlacement(...)` as Task 1.
- Class Javadoc: one paragraph on the wide layout.

- [ ] **Step 4: Run the tests**

Run: the Step 2 command, then `./gradlew :jasper-plugin-fileserver:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/fileserver
git commit -m "File Server: the File Servers tool window lays its list beside the details when wide"
```

### Task 5: Versions and documentation

**Files:**
- Modify: `plugins/remote/src/main/resources/plugin.toml` (`version = "0.17.0"`), `plugins/fileserver/src/main/resources/plugin.toml` (`version = "0.7.0"`)
- Modify: `docs/remote.md` (The Sessions panel, Browsing, The Tunnels panel, Under a classic look), `docs/file-server.md` (The File Servers tool window, Under a classic look), `docs/workspace.md` (one sentence where a window of its own is described, if it lists what changes), `docs/STATUS.md` (plugin table versions and a dated entry), `docs/superpowers/specs/2026-10-23-jasper-floating-tool-windows-design.md` (the follow-up line: done by this plan)

- [ ] **Step 1: Find every place that reads the versions**

Run: `grep -rn '0\.16\.0\|"0\.6\.0"' --include='*.java' --include='*.toml' --include='*.md' plugins jasper-app/src docs/*.md README.md`
Expected: the two `plugin.toml` files and the STATUS table; any test that pins a version is updated with them.

- [ ] **Step 2: Bump both versions and update the docs**

Docs say what the user sees: in a window of its own or docked at the bottom, Sessions shows the list beside the selected host's or distribution's details (the fields and buttons Task 1 lists); SFTP adds Modified and Permissions columns; Tunnels shows the list beside the tunnel's details (route, counters, buttons); File Servers shows the details beside the list at all times, with no Show Details in the row menu. The classic bullets get the same, per Tasks 1 to 4. Smallest accurate edit; no new sections.

- [ ] **Step 3: Verify**

Run: `./gradlew :jasper-plugin-remote:check :jasper-plugin-fileserver:check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add plugins/remote/src/main/resources/plugin.toml plugins/fileserver/src/main/resources/plugin.toml docs
git commit -m "Remote 0.17.0, File Server 0.7.0: wide tool window layouts, documented"
```

### Task 6: Real-host renders of the wide layouts, and the ledger rows

**Files:**
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/PanelSurfaceFixture.java` (a placement the test can change)
- Create: `jasper-app/src/test/java/dev/jasper/app/plugins/WideLayoutRenderTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (rows for the five new plugin test files and the new render test)

**Interfaces:**
- Produces: `PanelSurfaceFixture.place(PanelSite.Placement placement)`: the site's placement supplier returns it from then on and `site.notifyPlacement(placement)` runs on the EDT, followed by `StagedVaultHost.settle()`.

- [ ] **Step 1: Write the test**

`WideLayoutRenderTest`, one test per tool window (Sessions with two hosts and one selected; SFTP with a page of a folder and two files shown through the fixture's `sftp(...)`; Tunnels with two tunnels, one up; File Servers with two servers), each under the default look (as `PanelSurfacePreview` builds its fixture) and under Enterprise (as `ClassicCompositionRenderTest.under` does): after `place(new Placement(<its slot>, ToolWindowMode.FLOAT))` and `size(760, 460)`, the list and detail column components (`<prefix>.list`, `<prefix>.detail`; for SFTP the `sftp.files` table's four column headers) are showing with positive widths and the list column is the narrower; the render saved as `jasper-app/build/wide-layouts/<look>/<panel>.png` is not a single colour; after `place(<its slot>, DOCKED)` the split's `<prefix>.columns` component is gone or not showing. Timeouts as `ClassicCompositionRenderTest`'s.

- [ ] **Step 2: Add the ledger rows**

Run: `./gradlew :jasper-app:test --tests '*ThemeMigrationCoverageTest'` first to see the missing paths, then add one row each, in the shape of the existing test-only rows (`…,dev.jasper.remote,<Class>,nonvisual-helper,none,none,<path>,Test/fixture ownership; New in wide tool window layouts plan Task <n>`).

- [ ] **Step 3: Run**

Run: `./gradlew :jasper-app:test --tests '*WideLayoutRenderTest' --tests '*ThemeMigrationCoverageTest' --tests '*ClassicCompositionRenderTest'`
Expected: PASS; eight PNGs under `jasper-app/build/wide-layouts/`.

- [ ] **Step 4: Commit**

```bash
git add jasper-app/src/test docs/design/theme-engine/migration.csv
git commit -m "Tests: real-host renders of the wide tool window layouts; the migration ledger lists the new tests"
```

### Task 7: Full check

- [ ] **Step 1:** Run `./gradlew check` in the background (about 8 minutes); expected BUILD SUCCESSFUL. Read the test counts from `*/build/test-results/test/*.xml`.
- [ ] **Step 2:** Run the source hygiene script from AGENTS.md over the changed Java files; expected no output.
- [ ] **Step 3:** Record the counts in the STATUS entry and commit.
