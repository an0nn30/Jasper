# Enterprise presentation 10a: Workstation, Remote and File Server compositions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** With Enterprise turned on, Remote and File Server compose their classic layouts as the Workstation and Dark Mode
mocks draw them (the "Session: [combo] [Connect]" toolbar view, the Sessions tree with columns and Properties, the
Tunnels and File Servers tables, the SFTP navigation row, the Transfers bottom panel and the Enterprise default slots, the
Add Host keys, the New File Server keys table), discovering Enterprise only through `dev.jasper.enterprise.api.Workstation`
in their `workstation` packages; Vault gets the same lookup for plan 10b; every default-look gesture keeps a classic path.

**Architecture:** Each consumer has a plugin-local `dev.jasper.<plugin>.workstation.Layout` (`ROWS`, `CLASSIC`), the only
code that names `Workstation`; `verifyPluginArchitecture` confines `dev.jasper.enterprise.api` to those packages. The
plugin reads its `Layout` once in `start` and hands it to each composition root (a panel's or editor's constructor),
which keeps it for its life: `node()` builds the rows composition or the classic one, renderers never branch. One host
gap the mocks need is closed in jasper-ui with a face pair read from the existing `table` control point: classic
`Properties` draw as the Workstation's grid when titled, as label-colon lines when untitled (plan 10b's Details pane). A
jasper-app test harness renders the real staged plugins under the shipped Enterprise kit, Plex fonts and theme and
compares each surface with its mock crop (`MockMatch`: edge agreement, colour bands and colour presence).

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, `jdeps` for the architecture gate, Python 3.11+ standard library for the edit scripts (always `python3 -I`), Pillow
and NumPy only for the opt-in `tools/visual` comparison.

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1, 3, 6.4, 8.2, 14.2 to 14.7, 16.1.4, 16.1.7, 16.1.9, 17 and row 10 of 19.1, as amended on 2026-10-15). Row
10 is delivered as two plans (Rulings 1): this one and
[10b](2026-10-16-enterprise-10b-vault-compositions.md) (the Credential Vault, spec 14.8). Mocks:
[`docs/design/enterprise/reference/`](../../design/enterprise/reference/) 01 Workstation, 02 New File Server, 03 Add Host,
04 New Tunnel and 08 Dark Mode (2× PNGs; the mock wins on layout, DESIGN_LANGUAGE.md on intent; names, hosts and counts
are fixtures).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-10`, branch
`claude/enterprise-plugin-compositions`, which holds `main` at `9c6351e07` with this plan's commits on top. Run every
command from the worktree root. Line numbers below were re-anchored on 2026-10-17 at `b23f853af` (Execution notes,
2026-10-17); later tasks move them. Every edit quotes the text it
replaces, and the edit scripts stop with a message when that text is not found the stated number of times: re-find the
quoted text (it moves, it does not change), adjust the anchor and re-run; never edit around a missing anchor.

## Before you start

This plan is written against the code as plans 3b, 3c, 3d, 3e, 4a, 4b, 4c, 5a, 5b, 6, 7, 8a, 9a and 11 leave it. The
branch already holds `main`; do not rebase (Execution notes R2). Check that they are in it:

```bash
git merge-base --is-ancestor 9c6351e07 HEAD && echo "main 9c6351e07 is in the branch"
grep -c 'record Properties(' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java          # 3b: 1
grep -c 'record Separator(' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java           # 3b: 1
grep -c 'Map<String, Nodes.Node> cellNodes' jasper-sdk/src/main/java/dev/jasper/sdk/ui/TableRow.java   # 3c: 1
grep -c 'List<Column> columns) implements Node' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java  # 3c Tree.columns: 1
grep -c 'record View(ToolbarViewFactory factory)' jasper-sdk/src/main/java/dev/jasper/sdk/ui/ToolbarItem.java  # 3d: 1
grep -c 'Nodes.Hue swatch' jasper-sdk/src/main/java/dev/jasper/sdk/ui/DialogSpec.java      # 3d: at least 1
grep -c 'StandardMenu.SESSION' plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java      # 3e: at least 1
ls jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicTableFace.java \
   jasper-ui/src/main/java/dev/jasper/ui/swing/PropertyLists.java                           # 3b, 4b, 4c
grep -c 'DesignMetrics CLASSIC' jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java    # 4a: 1
ls plugins/enterprise/src/main/java/dev/jasper/enterprise/api/Workstation.java \
   jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseResources.java              # 5b
grep -c 'static .*register' jasper-app/src/main/java/dev/jasper/app/application/LookFonts.java     # 5b Task 1: at least 1
ls tools/visual/check_pairs.py                                                              # 4c Task 5
git log --oneline | grep -ciE 'window chrome|dock chrome|run window|settings: documentation'      # plans 6 to 9
```

Every count prints at least 1 and every `ls` lists its files. If one does not, stop: the earlier plan is missing. Plans
6 to 9 were written in parallel with this one; before Task 1, read their STATUS entries (top of `docs/STATUS.md`) and
re-anchor this plan in place (its **refresh step**): where an earlier plan already owns something this plan lists
(a classic toolbar dropdown width, a panel body's surface, the Transfers default slot order, a dialog header strip), use
that plan's API and record the change in this plan's status banner. Names this plan relies on from plans it did not see:

| Name | From | Used in |
|---|---|---|
| `ToolbarItem.view(ToolbarViewFactory)`, `FakePluginHost.toolbarViews(UUID)` (`List<FakeKitView>`), `ToolbarEntry.View.factory()` returning `Optional<ToolbarEntry.Mounted>` with `component()` | 3d Task 5 | Task 5 |
| `DialogSpec(String, WindowOwner, boolean, Presentation, Nodes.Hue)`, `FakePluginHost.dialogSwatch(String, String)` | 3d Task 3 | Task 9 |
| `Nodes.Properties`, `Nodes.PropertyRow(String, String, boolean[, Optional<Severity>])`, `Nodes.Separator(String, ControlState)`, `PropertyLists.PropertiesControl` with `title`, `card`, `labels`, `values` and `Grid` | 3b Tasks 5, 7 | Tasks 3, 4, 7, 10 |
| `Nodes.Tree(…, List<Column> columns)`, `Nodes.Table(…, Optional<Empty> empty, String emptyText)`, the 8-component `TableRow(…, Map<String, Node> cellNodes, Optional<Heading> heading)`, `TableRow.heading(…)`, 16-component `Nodes.ItemList(…, String emptyText)` | 3c Task 1 | Tasks 4, 6, 7, 9 to 11 |
| `interface Face { List<PaintPair> pairs(); }`, `KitFaces` (`of`, `all()`, `declared`), `ThemePainting.role(KitContext, ColorRole)`, `ThemePainting.painted(KitContext, PaintPair)`, `KitContext.faces()` | 4b Task 1 | Task 3 |
| `DesignMetrics.CLASSIC` built by `classic()` with `values.put(Role.X, n)`, `classic-roles.tsv`, `face-pairs.tsv`, `PaintSpecTest` | 4a Task 3, 4b Task 1 | Task 3 |
| `KitFixtures.enterprise()`, `KitFixtures.enterpriseLight()`, `KitFixtures.context(GeneratedTheme, Kit)`, `KitFixtures.render(KitView, int, int, double)` | 4a Task 6, 4c Task 4 | Tasks 2, 3 |
| `EnterpriseResources.ROOT`, `RESOURCES`, `facts()`, `theme()`; 6a/5b's `testsupport.EnterpriseLook` (`kit()`, `theme()`, `controller(boolean)`, `restoreDefault()`), which Task 2 extends; `Capabilities.PRESENTATION_PROVIDE` | 5b Tasks 1, 4; 3a | Task 2 |
| `ThemeController.startup(boolean, ThemeSelection, Kit, Consumer<InstallationFailure>)`, `PluginHost.kit` (a `Supplier<Kit>` defaulting to `FlatLafInstaller::installedKit`) | 5a Task 10 | Task 2 |
| The `HostedPlugin` constructor as the staged hosts call it (3d adds `iconHue`; mirror `StagedRemoteHost`'s call) | 3d Task 2 | Task 2 |
| `PanelHostHeader.Frame { STANDARD, FLUSH, FLUSH_FOCUSED }` and the 4-argument `PanelHostHeader(String, boolean, List<Action>, Frame)`; `ToolWindowArea.frame(Host)` | 7 Task 10 | Task 2 |

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (`packageDist`, `trainAotCache`, `-Pjasper.aot=true`). `:jasper-ui:gallery` is headless and may be run; so may every JUnit render of this plan (`java.awt.headless=true` in the test tasks).
- Work only in the worktree above on `claude/enterprise-plugin-compositions`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel rule: the default look stays byte-identical. Every plugin change composes its classic layout only under `Layout.CLASSIC`; under `Layout.ROWS` each node it builds is the one it builds today. The jasper-ui change (Task 3) adds three metric roles read only by the classic face and one face pair whose default class calls 3b's code unchanged, so every file the headless gallery writes for Slate Cobalt Dark and Light at scale 1 is byte-identical to the base taken in Task 3 Step 1, and every existing `tools/visual/pairs.json` render is unchanged. Plan 10a changes no jasper-app production code: a native dialog's classic footer band is 6b's `NativeShells.nativeMarks` (Execution notes R7). No existing render may change; Task 13 proves both.
- Composition belongs to its owner (spec 3.6): only `dev.jasper.<plugin>.workstation` names `dev.jasper.enterprise.api`; the plugin reads `Layout.of(context.services())` once in `start` and passes the value to the constructors that compose; renderers never branch on a look.
- Every action stays reachable (spec 14.7): each default-look gesture of Remote and File Server has its classic path, listed and exercised by Task 12's `CompositionParityTest`.
- Mock matching (the user: "make sure our design system/kit matches the mocks perfectly when enabled"): every surface this plan composes has a JUnit render under the shipped Enterprise kit, Plex fonts and theme, Light and (for the Workstation surfaces) Dark, compared with its mock crop by `MockMatch`: the colour bands of its sample columns equal the measured bands of "Mock measurements" within the stated tolerance, every listed colour is present, and the edge agreement reaches the stated floor. Iterate until each assertion passes. A difference that only a face or chrome of an earlier plan can fix is fixed there (the task names the file) or, when that plan's ruling decides it, recorded here as a residual with its measured value; floors are never lowered silently.
- SDK 0.18.0 stays the version; no SDK type changes. The plugins use spec 8.2's additions only.
- `docs/design/theme-engine/migration.csv`: every new source the ledger's pattern matches (`ThemeMigrationCoverageTest.CANDIDATE`) gets its row in the same commit, eight comma-separated fields (semicolons inside fields); plugin owners are `dev.jasper.<plugin>`, app owners `app.<package>`; production surfaces are `standard-ui` with numeric task fields `10`, test helpers `nonvisual-helper,none,none`.
- Javadoc doclint fails the build on errors (`-Xdoclint:all` in jasper-app and the SDK, `-Xdoclint:all,-missing` in jasper-ui); every `{@link}` resolves; no raw `<`, `>`, `&` or `->` in a comment.
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten.
- Source hygiene: no raw control, private-use or unpaired surrogate characters in any source file; `…`, `—`, `★`, `▾` and `·` are ordinary characters.
- The identifier word "hero" stays reserved for `Form.hero` (3a's `PromptVocabularyTest`).

## Review Focus

1. Enterprise installed but turned off, or not installed at all (a `--plugin-dir` start without it): Remote, File Server and
   Vault start in their rows layouts with no `NoClassDefFoundError` and no plugin problem. Task 1:
   `LayoutTest.withoutAWorkstationTheLayoutIsRows` and `aMissingEnterpriseClassIsRows` (each plugin).
2. A user selects a host in the Sessions tree, then the window's active SSH pane changes, then they press the toolbar's
   Connect: Connect acts on the host they chose, not on the pane's; with nothing chosen it acts on the active pane's host,
   and with neither on the first host. Task 5: `SessionToolbarViewTest.connectActsOnTheChosenHostThenThePanesThenTheFirst`.
3. Every saved host deleted while the Sessions tree, its Properties and the toolbar combo show it: the tree, the
   Properties and the combo drop it in the same pass, nothing throws, and the combo falls back. Task 5:
   `SessionToolbarViewTest.aDeletedHostLeavesTheComboAndTheProperties`.
4. A failed tunnel or a starting one in the classic table: the Type cell's dot and word, the footer and the context menu
   follow the confirmed state, and activating a starting tunnel stops it (as the switch did). Task 6:
   `TunnelsTableTest.theFooterAndTheDotFollowTheConfirmedState`.
5. The SFTP panel disconnected under Enterprise with no saved host: the Choose Session dropdown is disabled with one
   "No saved sessions" option, the breadcrumbs read "/" disabled, the table keeps its three headers over "Not
   Connected", and nothing in the row can be activated. Task 7: `SftpClassicTest.disconnectedWithoutHostsIsInert`.

## Verified facts

At `b23f853af` (re-anchored on 2026-10-17) unless a plan is named.

1. Remote looks its optional Vault up with `try { vault = context.services().find(VaultApi.class); } catch
   (NoClassDefFoundError absent) { vault = Optional.empty(); }` (`RemotePlugin.java:148`); File Server does the same
   (`FileServerPlugin.java:142`). `Services.find` reads an optional provider's publication (`jasper-sdk` `Services.java:55`).
2. `verifyPluginArchitecture` runs `jdeps -verbose:class` on each plugin's classes and allows a target package only when
   it is the JDK, the plugin's own, a `declaredImports` entry, the SDK (not `sdk.testing`) or bundled
   (`gradle/plugin-architecture.gradle.kts:10`, `:37-55`); `declaredImports` has no entry for Vault.
3. Remote's manifest requires Vault and the Editor optionally (`plugins/remote/src/main/resources/plugin.toml:13`); File
   Server requires Vault optionally (`plugins/fileserver/src/main/resources/plugin.toml:13`); Vault has no `requires`
   (`plugins/vault/src/main/resources/plugin.toml`). Each build compiles against the SDK and the APIs it requires with
   `compileOnly` and tests with `testImplementation` (`plugins/remote/build.gradle.kts:7-22`).
4. `FakePluginHost.start(info, requires, optional, plugin)` gives a plugin the services its required and optional
   providers published (`FakePluginHost.java:219-250`); a plain lambda can be a provider (5b's `EnterprisePluginTest`).
5. Remote registers Sessions at `Anchor.LEFT` (`RemotePlugin.java:230`), Tunnels at `Anchor.LEFT` (`TunnelsUi.java:116`),
   SFTP at `Anchor.LEFT` with `withSplit()` (`SftpUi.java:57`); File Servers at `Anchor.LEFT` (`FileServerPlugin.java:198`).
   `Anchor` is `LEFT`, `RIGHT`, `BOTTOM` and a spec's `split` picks the side's secondary half (`Anchor.java:1-15`,
   `PanelSpec.java:22-62`). The fake prints a panel as `id|title|ANCHOR[+split]|toggle` (`RemotePluginTest.java:53`; File Server's toggle is
   `dev.jasper.fileserver.show-panel`, `FileServerPluginTest.java:201`).
6. `HostsPanel` builds one `Nodes.Panel` in `node()` (`HostsPanel.java:301`): a SEARCH field "Search sessions", a SINGLE
   `ItemList` `sessions.rows` of `HostRows` rows (`:321`), a hint and the selected row's card (`:385`) with `card.edit`
   and `card.connect`; ids `host:<uuid>`, `distro:<profile>`, `group:<name>`, `group.wsl`, `error` (`rowId`); its header is
   published with a blank detail (`:294-297`). `RemotePlugin.createPanel` makes it (`:619`) and `credentialLabel` gives
   "SSH agent", "Vault locked", "needs Credential Vault", "credential missing" or the credential's name (`:701`).
7. `HostRows.rows(hosts, query, collapsed, defaultGroup, distros, wslCollapsed)` returns a group row followed by its hosts,
   omitting a collapsed group's hosts (`HostRows.java:85-99`); `RemoteHost.label()` is `user@host:port`
   (`RemoteHost.java:44`).
8. `SessionsToolbar.refresh` registers one action per distribution and host and one `ToolbarItem.menu(icon, "Sessions",
   ids)` dropdown ending in "Manage Sessions..." (`SessionsToolbar.java:56-90`; `distroActionId` at `:100`); `activateHost` focuses a running pane for
   the host or opens one (`RemotePlugin.java:370`, `activateHost`).
9. `TunnelsPanel.node()` builds a SEARCH filter, a DOUBLE `ItemList` `tunnels.rows` with a trailing Toggle per tunnel and
   the "Quick forwards" heading, or a kit `Empty` "No Tunnels" (`TunnelsPanel.java:233-268`); `menu(rowId, tunnel, state)`
   is the row menu (Start or Stop, Open in Browser, Copy Address, Save as Tunnel… or Edit…, Duplicate, Delete…);
   `TunnelRows.mapping` and `port` format the listen and target parts (`TunnelRows.java:17-36`).
10. `SftpPanel.panel()` builds the navigation (`:324`: Back, Forward, Refresh, the hidden-items GHOST button, the Upload
    menu, breadcrumbs), a two-column `Table` `sftp.files` (`:369`) or an `Empty` "Not Connected" with a Choose Session
    menu while unloaded, and a footer with the transfers strip, paging, status and the Follow terminal folder row
    (`:299-322`). `SftpUi.create` makes it and hands it `transferStrip.apply(window)` (`SftpUi.java:75-80`).
11. `TransferUi` registers `SHOW` "Transfers" (`cmd+alt+3`/`alt+3`) to reveal the SFTP sidebar (`TransferUi.java:40`);
    `strip(window)` makes the window's `TransferStrip`, whose `node()` is a VERTICAL stack of a heading and an ItemList
    (`TransferStrip.java:64`).
12. `FileServersPanel.node()` builds a DOUBLE `ItemList` `fileserver.rows` with a trailing Toggle per server, the
    selected server's details `Card` (`details`, `:303`), or an `Empty` "No File Servers" with "Share Folder…" (`:207-240`);
    `menu(server)` is the row menu; `ServerRows.address`, `state`, `access` and `folder` format the cells.
13. `ServerEditor.form()` shows the authorized keys as a COMPACT `ItemList` `server.keys` of `key.<i>` rows with the
    actions Add from File…, Paste and Remove (`ServerEditor.java:436-450`); `REMOVE_KEY` parses the row ids with
    `substring(4)`; `AuthorizedKeys.parse(line)` gives `type()`, `fingerprint()` and `comment()`.
14. `HostEditor` puts the Authentication EXCLUSIVE choice unlabelled in its MIXED card and the Keys `ItemList` (ORDERED,
    add action "Add Stored Key…") in a FormRow (`HostEditor.java:365-372`).
15. The staged real-host fixtures: `PanelSurfaceFixture(Path, ResolvedTheme, String panel)` loads the staged Remote or File
    Server jar beside a locked Vault, mounts one panel through its real factory and the app's `PanelSite`, and
    `capture(scale)` prints the view (`PanelSurfaceFixture.java:39-85`, `:201-206`); its stores take fixture data
    (`hosts`, `tunnel`, `fileServer`, `sftp`); a plugin's class loader maps an exported package to its provider's loader
    (`Map.of("dev.jasper.vault.api", vault.loader)`, `:49`).
16. `KitFixtures.render(view, w, h, scale)` lays out and paints a view into a transparent ARGB image
    (`KitFixtures.java:304`); `tools/visual/compare_mock.py` measures luminance-edge agreement within one pixel over a
    shift search (`compare_mock.py:40-80`); 4c's `check_pairs.py` reads `min_agreement` and `colours`.
17. The Enterprise mocks measure as "Mock measurements" below (2× pixels halved, Pillow, `python3 -I`, 2026-10-16).

## Rulings

Decisions where the spec is silent, taken on the user's behalf (2026-10-16): "what — why — cost if wrong". Record them in
`docs/STATUS.md` (Task 13) and in this plan's status banner.

1. **Row 10 is two plans: 10a (Workstation consumption in all three plugins, Remote, File Server) and 10b (the
   Credential Vault)** — the spec row's work is about 24 tasks, and the Vault manager is a separate window with its own
   host gaps — cost if wrong: none; 10b follows 10a on the same branch.
2. **`Layout` is a plugin-local enum per consumer (`dev.jasper.remote.workstation.Layout`, and the same in File Server
   and Vault), with `Layout.of(Services)` the only lookup** — spec 14.3 names the packages and the two values; three tiny
   copies keep each plugin self-contained — cost if wrong: one more class to share.
3. **A composition root keeps its `Layout` for its life and `node()` dispatches on it to `rowsNode()` or `classicNode()`**
   — spec 3.6: "the owner branches once at its composition root"; the decision is a constructor argument, never
   re-read; the two editors whose constructor chains have several public forms (`HostEditor`, `ServerEditor`) take it
   through one `layout(Layout)` call right after construction, before their dialog shows — cost if wrong: separate classes
   per composition, the same nodes.
4. **Classic `Properties` follow the `table` control point: a titled list draws as the Workstation grid (a 22 px title
   band, an 89 px label column on `surface.panel`, values on `surface.card`, 1 px `border.row` rules), an untitled one as
   label-colon lines (an 80 px label column, 2 px row gap, as 10b's Details pane)** — the mocks draw both; Properties is
   not one of spec 6.4's fourteen points, and a fifteenth would change the closed kit file schema (spec 5.1) — cost if
   wrong: a point of its own later.
5. **The Session dropdown lists saved hosts as "name (hostname)" by name, then WSL distributions as "name (detail)", then
   "Manage Sessions…" as its last option, with no separator** — spec 14.4 asks for a separator, which a DROPDOWN
   `Option` cannot carry in SDK 0.18 (8.4 adds none); the order is the spec's — cost if wrong: one divider (a recorded
   residual).
6. **The toolbar selection is per window and shared with the Sessions tree; Connect acts on the chosen row, else the
   active SSH pane's host, else the first host** — spec 14.4's fallback, and Review Focus 2 — cost if wrong: none.
7. **The classic tunnels context menu is today's row menu** (it begins with Start or Stop and holds Edit…), so Duplicate,
   Delete…, Open in Browser, Copy Address and Save as Tunnel… stay reachable (spec 14.7); **activation is Enter or a double
   click**, and the footer keeps spec 14.4's copy "<name> — inactive. Click to toggle." for the selected tunnel, else the
   first (the mock shows it with no selection) — cost if wrong: one string.
8. **A broken `tunnels.toml` or `servers.toml` shows a DANGER `Notice` above the classic table** (3b's Notice), not a row
   — a table row cannot carry a severity — cost if wrong: one node.
9. **Under CLASSIC the transfer rows move out of the SFTP footer into a Transfers BOTTOM panel (`dev.jasper.remote.transfers.panel`)
   whose toggle is `TransferUi.SHOW`; with no transfer its body is an `Empty` "No Transfers"** — spec 14.4 — cost if
   wrong: copy.
10. **File Server's classic details are Properties titled "Properties — <name>" (Folder, Protocol, Port, Access,
    Reachable from, User, the TLS or host-key fingerprint, the addresses), and the default look's Show Details menu item
    reads Show Activity, showing the activity log and its Clear and Copy under the Properties** — spec 14.5 replaces the
    card; 14.7 keeps every gesture reachable, the activity log included — cost if wrong: one menu label.
11. **The SFTP navigation row's labelled buttons are "Hidden files" (the existing pressed-state button with its label and
    no icon) and "Upload" (the existing menu with its label, the classic menu face draws the ▾); a disconnected panel
    shows a DROPDOWN whose first option "Choose Session…" stays selected** — a DROPDOWN has no placeholder; choosing a
    host browses it and the dropdown returns to its first option — cost if wrong: one option.
12. **Add Host under CLASSIC: the Keys list's `emptyText` is "No keys" and the Authentication choice is wrapped in an
    empty-label FormRow**; New Tunnel needs no composition change and is only rendered and compared — spec 6.4 and 14.4;
    the default look keeps today's nodes (pixel rule) — cost if wrong: none.
13. **`CompositionParityTest` exists once per plugin module, in the test package that reaches its composition roots**
    (`dev.jasper.remote` for `SessionsToolbar`, `dev.jasper.fileserver.ui`; plan 10b adds `dev.jasper.vault.ui`'s): one
    test class cannot see three plugins' internals — cost if wrong: one class name thrice.
14. **Residuals the mocks show that this plan does not chase** (each owned elsewhere, each measured): the Disconnected
    mock stripes the second host of the Sessions tree (#F7F8FA), which plan 4c's Rulings 3 ("trees are not striped")
    excludes; the Tunnels mock's one row is 24 px against the classic table's 22 (`ROW_HEIGHT`); the Session combo is
    219 px wide in the mock and sized by plan 3d's toolbar strip to its widest option; Transfers' place after Event Log in
    the BOTTOM_LEFT strip (spec 14.4) is the dock's stripe order, which plan 7 owns (its Rulings 18 places each panel
    in its registered slot and orders nothing inside one) and no test checks — cost if wrong: plan 4c or 6 adds a rule,
    the band tolerances below already admit them; plan 7 adds the strip order and its assertion (Event Log, then
    "3: Transfers").
15. **Under `DialogMapping.CLASSIC` a native dialog's kit form (any kind but WINDOW) takes 6b's footer band
    (`KitView.dialogBand`), set by `NativeShells.classicFooters` in `NativeShells.dialog` (Task 9)** — spec 10.8's last
    sentence ("Form and dialog footers sit on `surface.footer` under a `border.window` rule") and the 02, 03 and 04 mocks'
    44 px footer bands; 6b sets the band only for the dialogs it draws in their window, and no other plan paints it for a
    native one; a WINDOW body keeps its own footer; the default mapping reads `classic()` false and changes nothing —
    cost if wrong: one flag in one method. **Superseded on 2026-10-17 (Execution notes R7): 6b's final code already sets
    the band on a classic native dialog's kit content (`NativeShells.nativeMarks`), so Task 9 adds no host change.**

## Mock measurements

Measured at 2× pixels on 2026-10-16 with Pillow (`python3 -I`), halved to logical pixels; colours are the mock's, which
the Enterprise theme's roles reproduce (5b's `EnterpriseThemeTest`). "Bands" read down one sample column as `start+length
colour` from the top of the crop, runs under 2 device px dropped. Crops are `x,y,w,h` in mock pixels. Each panel crop
starts under the panel's header strip and its rule (plan 7's, not measured here); `PanelSurfaceFixture.render` cuts the
view's header row to match (Task 2).

| Surface | Mock and crop | Sample column (mock x) | Light bands | Dark bands |
|---|---|---|---|---|
| Sessions body (connected) | 01 Connected, `2136,230,700,782` (350×391) | 2140 | `0+36 #F7F7F8, 36+1 #DDE0E4, 37+20 #F0F1F3, 57+1 #D3D6DA, 58+44 #FFFFFF, 102+22 #2F65CA, 124+111 #FFFFFF, 235+1 #C4C7CC, 236+22 #F7F7F8, 258+1 #DDE0E4, 259+22 #F7F7F8, 281+1 #E6E8EB, 282+22 #F7F7F8, 304+1 #E6E8EB, 305+22 #F7F7F8, 327+1 #E6E8EB, 328+22 #F7F7F8, 350+1 #E6E8EB, 351+40 #F7F7F8` | `0+36 #35383D, 36+1 #45484E, 37+20 #393C41, 57+1 #4A4D53, 58+44 #2E3135, 102+22 #3D74D6, 124+111 #2E3135, 235+1 #2A2C30, 236+22 #35383D, 258+1 #45484E, 259+22 #35383D, 281+1 #3F4247, 282+22 #35383D, 304+1 #3F4247, 305+22 #35383D, 327+1 #3F4247, 328+22 #35383D, 350+1 #3F4247, 351+40 #35383D` |
| Sessions body (disconnected) | 01 Disconnected, same crop | 2140 | as connected but `58+22 #FFFFFF, 80+22 #2F65CA, 102+22 #F7F8FA` (residual 14: may be #FFFFFF) | — |
| Tunnels body (one tunnel) | 01 Connected, `2136,1064,700,642` (350×321) | 2140 | `0+36 #F7F7F8, 36+1 #DDE0E4, 37+20 #F0F1F3, 57+1 #D3D6DA, 58+24 #FFFFFF, 82+1 #EEF0F2, 83+238 #FFFFFF` (residual 14: the row may be 22) | `0+36 #35383D, 36+1 #45484E, 37+20 #393C41, 57+1 #4A4D53, 58+24 #2E3135, 82+1 #3E4146, 83+238 #2E3135` |
| Tunnels body (none) | 01 Disconnected, `2136,1064,700,642` | 2140 | `0+20 #F0F1F3, 20+1 #D3D6DA, 21+300 #FFFFFF` (no filter; the Empty centred in the body) | — |
| SFTP body (connected) | 01 Connected, `48,230,600,712` (300×356) | 56 | `0+61 #F7F7F8, 61+1 #DDE0E4, 62+20 #F0F1F3, 82+1 #D3D6DA, 83+22 #2F65CA, 105+22 #F7F8FA, 127+22 #FFFFFF` … alternating to `325+2 #F7F8FA, 327+1 #DDE0E4, 328+28 #F7F7F8` | the same with `#35383D, #45484E, #393C41, #4A4D53, #3D74D6, #313439, #2E3135` |
| SFTP body (disconnected) | 01 Disconnected, same crop | 56 | `0+36 #F7F7F8, 36+1 #DDE0E4, 37+20 #F0F1F3, 57+1 #D3D6DA, 58+269 #FFFFFF, 327+1 #DDE0E4, 328+28 #F7F7F8` | — |
| File Servers body | 01 Connected and Disconnected, `48,994,600,712` (300×356) | 56 | `0+20 #F0F1F3, 20+1 #D3D6DA, 21+335 #FFFFFF` | `0+20 #393C41, 20+1 #4A4D53, 21+335 #2E3135` |
| Session toolbar view | 01 Connected, `660,110,720,68` (360×34) | 790 | `0+6 #ECEDEF, 6+1 #B4B8BF, 7+20 gradient #FFFFFF→#F1F2F4, 27+1 #B4B8BF, 28+6 #ECEDEF` | `#3C3F44` row, combo `#4A4D52→#45484D` |
| New File Server keys table | 02 SFTP, `2,60,1040,1172` (520×586) | 100 | from mock y 655: `0+2 #ECEDEF, 2+1 #B4B8BF, 3+20 #F0F1F3, 23+1 #D3D6DA, 24+44 #FFFFFF, 68+1 #B4B8BF, 69+3 #ECEDEF` | — |
| Dialog bodies and footers | 02 HTTP/FTP/SFTP/TFTP, 03 Default, 04 Local, `2,60,1040,H-62` | 20 | `0+B #ECEDEF, B+1 #C4C7CC, (B+1)+44 #E6E7EA` with B = 448, 560, 541, 386, 476 and 404 | — |
| Add Host keys box | 03 Default, `2,60,1040,1042` (520×521) | 300 | from mock y 560: `0+3 #ECEDEF, 3+1 #B4B8BF, 4+56 #FFFFFF, 60+1 #B4B8BF, 61+4 #ECEDEF` | — |

Positions and sizes inside the surfaces (logical px; ±1 unless noted):

| What | Value |
|---|---|
| Sessions | filter field 334 wide at x 8, 24 tall (border #B4B8BF); column titles at x 9 (Name), 176 (Host), 303 (Port), `#4A4F57`; separators after Name at 166 and after Host at 293 (`#D3D6DA`); rows 22; the dot 6 wide at x 26; names at x 32; the selected row `#2F65CA` with `#FFFFFF` text; group title "Local Network" medium `#3A3D42` at x 9 after a 6 px ▼ |
| Sessions Properties | title band 22, "Properties — <host>" medium `#4A4F57` at x 9; label column 89 (x 0 to 88) + 1 px `#E6E8EB` rule at x 89, the value cell from x 90 (Light and Dark, `#3F4247` in Dark); labels `#5C6370` at x 9; values `#1E1F22` (data face) at x 98; rows 22 + 1 px `#E6E8EB`; WARNING value "Vault locked" `#8A5A00`; actions row 40 tall: Edit… 78×24 and Connect 75×24 (primary `#2F65CA`, edge `#2655AE`), right-aligned 6 from the edge |
| Tunnels | filter 36 tall area; separators `#D3D6DA` at 76, 163 and 273, each title 9 after its separator (Type at 8); the Type cell's idle dot 7 wide `#A3A8B0` at x 8, the word at 21; Local and Remote in the data face; footer "test — inactive. Click to toggle." CAPTION `#5C6370` at x 8, y 91 |
| SFTP | navigation row 28 tall: ← → ⟳ glyphs at x 15, 40 and 65; separators `#C4C7CC` 1×18 at x 86 and before "Upload ▾"; "Hidden files" at x 100, "Upload ▾" at x 189; breadcrumbs field 284×24 at x 8; column separators at 136 and 213, titles Name 9, Size 146, Modified 223; zebra `#F7F8FA` on odd rows; Modified values `MMM dd` in the data face `#5C6370`; Follow terminal folder row 28 tall, checkbox 13 at x 8 |
| SFTP disconnected | Choose Session… dropdown 168×24 at x 8, gradient `#FFFFFF`→`#F1F2F4`; the breadcrumbs field disabled `#F0F1F3` with "/" `#9AA0A8`; "Not Connected" BODY_STRONG `#1E1F22` centred at y 177; its sentence `#5C6370` 21 below |
| File Servers | titles Folder 9, Address 120, Status 237; separators at 110 and 226; "test" at x 8, the HTTP tag 35×14 at x 35 (`#2655AE` on `#E1E8F7`, box `#B9C8EA`); the address right-aligned in the data face ending at 225; "Stopped" `#3A3D42`; Empty "No File Servers" with "Share Folder…" 102×24 |
| Session toolbar view | "Session:" LABEL `#5C6370` at x 6 (the S's first ink at 6.5 on its bowls' rows, 9.5 on its middle row; the toolbar separator at -7); combo 22 tall at x 59; Connect 24 tall, 75 wide, 3 after the combo |
| New File Server keys | "Authorized keys" LABEL `#3A3D42`; box 462×67 at x 29 (`#B4B8BF`); titles Type 9, Fingerprint 97, Comment 344; separators at 87 and 334; "No keys added" `#7A808A` centred; Add from File… 104×24, Paste 52×24, Remove 66×24 disabled, 6 apart |
| Add Host keys | the box 250×58 at x 143 beside "Keys" / "Tried in order"; "No keys" `#7A808A` centred; Add Stored Key… and Remove 94×24 at x 399, 4 apart |

## File structure

- New in each consumer: `plugins/{remote,fileserver,vault}/src/main/java/dev/jasper/<plugin>/workstation/Layout.java` and
  `package-info.java`; tests `.../workstation/LayoutTest.java` (Task 1) and, in Remote and File Server,
  `CompositionParityTest.java` in `dev.jasper.remote` and `dev.jasper.fileserver.ui` (Task 12).
- Build and gate: the three `plugin.toml` files and `build.gradle.kts` files, `gradle/plugin-architecture.gradle.kts`,
  `AGENTS.md` (Task 1).
- jasper-ui (Task 3): new `PropertiesFace`, `DefaultPropertiesFace`, `ClassicPropertiesFace` in `dev.jasper.ui.swing`;
  changed `PropertyLists`, `KitFaces`, `DesignMetrics` and the frozen `compact-roles.tsv`, `classic-roles.tsv`,
  `face-pairs.tsv`; test `ClassicPropertiesTest`.
- jasper-app tests (Task 2): `testsupport/MockMatch`, `testsupport/EnterpriseLook` (6a's, extended), `plugins/StagedEnterprise`,
  `PanelSurfaceFixture` (an Enterprise mode and the Sessions panel), `testsupport/MockMatchTest`; renders in
  `plugins/ClassicCompositionRenderTest` (Tasks 4 to 11).
- Remote: `ui/HostsPanel` (Task 4), `SessionsToolbar` and new `SessionSelection` (Task 5), `ui/tunnels/TunnelsPanel` and
  `TunnelsUi` (Task 6), `ui/sftp/SftpPanel` and `SftpUi` (Task 7), new `ui/transfers/TransfersPanel` and `TransferUi`
  (Task 8), `RemotePlugin` (Tasks 1, 4, 5, 8, 9), `ui/HostEditor` (Task 9).
- File Server: `ui/FileServersPanel` and `FileServerPlugin` (Task 10), `ui/ServerEditor` (Task 11).
- Tools and documents: `tools/visual/pairs.json` (each render task), `docs/remote.md`, `docs/file-server.md`,
  `docs/plugin-authoring.md`, `docs/STATUS.md`, this plan's banner (Task 13).

---

### Task 1: Workstation consumption in Remote, File Server and Vault

Spec 14.3: each consumer optionally requires Enterprise, compiles against it with `compileOnly`, declares
`dev.jasper.enterprise.api` among its imports, and turns the lookup into its own `Layout` in its `workstation` package;
`verifyPluginArchitecture` refuses the API anywhere else; AGENTS.md records it. Each plugin reads its layout once in
`start` (plan 10b and Tasks 4 to 11 use it).

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/workstation/Layout.java`, `package-info.java`; the same two in
  `plugins/fileserver/src/main/java/dev/jasper/fileserver/workstation/` and `plugins/vault/src/main/java/dev/jasper/vault/workstation/`
- Create: `plugins/{remote,fileserver,vault}/src/test/java/dev/jasper/<plugin>/workstation/LayoutTest.java` and
  `FakeEnterprise.java`
- Modify: `plugins/{remote,fileserver,vault}/src/main/resources/plugin.toml`, `plugins/{remote,fileserver,vault}/build.gradle.kts`,
  `RemotePlugin.java`, `FileServerPlugin.java`, `VaultPlugin.java` (the `layout` field and accessor),
  `gradle/plugin-architecture.gradle.kts:10` and its jdeps loop, `AGENTS.md`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java`, `plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java`,
  `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java` (one case each)

**Interfaces:**
- Consumes: 5b's `dev.jasper.enterprise.api.Workstation` and the `:jasper-plugin-enterprise` project.
- Produces: `public enum dev.jasper.<plugin>.workstation.Layout { ROWS, CLASSIC; public static Layout of(Services) }` in
  remote, fileserver and vault; package-private `Layout layout()` on `RemotePlugin`, `FileServerPlugin` and `VaultPlugin`;
  test helper `public final class dev.jasper.<plugin>.workstation.FakeEnterprise { public static final PluginInfo INFO;
  public static void start(FakePluginHost host) }` in each plugin's tests.

- [ ] **Step 1: Write the failing tests**

Create `plugins/remote/src/test/java/dev/jasper/remote/workstation/FakeEnterprise.java`:

```java
package dev.jasper.remote.workstation;

import dev.jasper.enterprise.api.Workstation;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.util.Set;

/** A stand-in for the Enterprise plugin on the testkit host: it publishes Workstation and nothing else, as its start does. */
public final class FakeEnterprise {
    /** Enterprise's identity, as its descriptor gives it. */
    public static final PluginInfo INFO = new PluginInfo("dev.jasper.enterprise", "Enterprise", "0.1.0", Set.of("presentation.provide"));

    private FakeEnterprise() { }

    /** Starts the stand-in; start a consumer after it with {@code dev.jasper.enterprise} among its optional requirements. */
    public static void start(FakePluginHost host) {
        host.start(INFO, Set.of(), Set.of(), context -> context.services().publish(Workstation.class, new Workstation() { }));
    }
}
```

Create `plugins/remote/src/test/java/dev/jasper/remote/workstation/LayoutTest.java`:

```java
package dev.jasper.remote.workstation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.services.Services;
import dev.jasper.sdk.testing.FakePluginHost;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Spec 14.3: Remote composes its classic layout exactly while Workstation is published. */
class LayoutTest {
    static final PluginInfo PROBE = new PluginInfo("dev.example.probe", "Probe", "1.0.0", Set.of());

    @Test void whileWorkstationIsPublishedTheLayoutIsClassic() {
        try (var host = new FakePluginHost()) {
            FakeEnterprise.start(host);
            var layout = new AtomicReference<Layout>();
            host.start(PROBE, Set.of(), Set.of(FakeEnterprise.INFO.id()), context -> layout.set(Layout.of(context.services())));
            assertThat(layout.get()).isEqualTo(Layout.CLASSIC);
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void withoutAWorkstationTheLayoutIsRows() {
        try (var host = new FakePluginHost()) {
            var layout = new AtomicReference<Layout>();
            host.start(PROBE, Set.of(), Set.of(FakeEnterprise.INFO.id()), context -> layout.set(Layout.of(context.services())));
            assertThat(layout.get()).isEqualTo(Layout.ROWS);
        }
    }

    /** Enterprise not installed: its API class is not visible to the plugin's loader. */
    @Test void aMissingEnterpriseClassIsRows() {
        Services missing = new Services() {
            @Override public <T> void publish(Class<T> api, T implementation) { throw new UnsupportedOperationException(); }
            @Override public <T> void publishPerConsumer(Class<T> api, Function<PluginInfo, T> perConsumer) { throw new UnsupportedOperationException(); }
            @Override public <T> T require(Class<T> api) { throw new UnsupportedOperationException(); }
            @Override public <T> Optional<T> find(Class<T> api) { throw new NoClassDefFoundError("dev/jasper/enterprise/api/Workstation"); }
        };
        assertThat(Layout.of(missing)).isEqualTo(Layout.ROWS);
    }
}
```

Create the same two files under `plugins/fileserver/src/test/java/dev/jasper/fileserver/workstation/` and
`plugins/vault/src/test/java/dev/jasper/vault/workstation/` with `package dev.jasper.fileserver.workstation;` and
`package dev.jasper.vault.workstation;` and "Remote" replaced by "File Server" and "Credential Vault" in the class
comments; nothing else differs.

Add one case to each plugin's own test. In `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java`, after
the class's first test method `hostContributesSemanticNetworkNavigationIcons`:

```java
    @Test void aStartUnderEnterpriseReadsTheClassicLayoutOnce(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            dev.jasper.remote.workstation.FakeEnterprise.start(host);
            var plugin = plugin(dir.resolve("ssh"));
            host.start(INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor", "dev.jasper.enterprise"), plugin);
            settle(host);
            assertThat(host.failures()).isEmpty();
            assertThat(plugin.layout()).isEqualTo(dev.jasper.remote.workstation.Layout.CLASSIC);
        }
        try (var host = new FakePluginHost()) {
            var plugin = plugin(dir.resolve("ssh-default"));
            host.start(INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor", "dev.jasper.enterprise"), plugin);
            settle(host);
            assertThat(plugin.layout()).isEqualTo(dev.jasper.remote.workstation.Layout.ROWS);
        }
    }
```

In `plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java`, a new method in the class (its
helpers `plugin()` and `INFO` are at `:51-60`):

```java
    @Test void aStartUnderEnterpriseReadsTheClassicLayoutOnce() {
        try (var host = new FakePluginHost()) {
            dev.jasper.fileserver.workstation.FakeEnterprise.start(host);
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.enterprise"), plugin);
            assertThat(host.failures()).isEmpty();
            assertThat(plugin.layout()).isEqualTo(dev.jasper.fileserver.workstation.Layout.CLASSIC);
        }
    }
```

In `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java`, a new method (its `plugin()` and `INFO` are at
`:29-40`). `VaultPlugin.start` builds kit views and status items on the UI thread, so the case runs inside
`KitViews.edt(...)` and declares `throws Exception`, as every other case of the class does:

```java
    @Test void aStartUnderEnterpriseReadsTheClassicLayoutOnce() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        try (var host = new FakePluginHost()) {
            dev.jasper.vault.workstation.FakeEnterprise.start(host);
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of("dev.jasper.enterprise"), plugin);
            assertThat(host.failures()).isEmpty();
            assertThat(plugin.layout()).isEqualTo(dev.jasper.vault.workstation.Layout.CLASSIC);
        }
    }); }
```

If a test class lacks an import the case uses (`FakePluginHost`, `Set`, `TempDir`, `Path`), add it.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:compileTestJava :jasper-plugin-fileserver:compileTestJava :jasper-plugin-vault:compileTestJava --continue`
Expected: FAIL to compile in each plugin: "package dev.jasper.enterprise.api does not exist" (the tests see no Enterprise
project yet) and "cannot find symbol … Layout".

- [ ] **Step 3: Add the requirement, the build edges and the three `Layout` enums**

Create `plugins/remote/src/main/java/dev/jasper/remote/workstation/Layout.java`:

```java
package dev.jasper.remote.workstation;

import dev.jasper.enterprise.api.Workstation;
import dev.jasper.sdk.services.Services;

/**
 * Which composition Remote's views take (Enterprise presentation spec 14.3): {@link #CLASSIC} while the Enterprise look
 * publishes {@link Workstation}, else {@link #ROWS}. The only Remote code that names the Enterprise API.
 */
public enum Layout {
    /** Today's rows, cards and switches. */
    ROWS,
    /** The Workstation mocks' trees, tables, Properties and toolbar view. */
    CLASSIC;

    /**
     * The layout for this run. Enterprise is an optional requirement: when it is not installed its API class is not
     * visible to this plugin, which reads as {@link #ROWS}.
     *
     * @param services the plugin's service registry
     * @return {@link #CLASSIC} while Workstation is published, else {@link #ROWS}
     */
    public static Layout of(Services services) {
        try { return services.find(Workstation.class).isPresent() ? CLASSIC : ROWS; }
        catch (NoClassDefFoundError absent) { return ROWS; }
    }
}
```

Create `plugins/remote/src/main/java/dev/jasper/remote/workstation/package-info.java`:

```java
/**
 * Remote's lookup of the Enterprise look: {@link dev.jasper.remote.workstation.Layout}. Only this package may name
 * {@code dev.jasper.enterprise.api} ({@code verifyPluginArchitecture}); the rest of the plugin reads the layout.
 */
package dev.jasper.remote.workstation;
```

Create the same two files under `plugins/fileserver/src/main/java/dev/jasper/fileserver/workstation/` and
`plugins/vault/src/main/java/dev/jasper/vault/workstation/` with their package names, "File Server's" and "Credential
Vault's" for "Remote's", and "File Server" and "the Credential Vault" in the enum's first sentence; nothing else differs.

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 10a Task 1 step 3: the optional requirement, the build edges, the layout field, the gate and AGENTS.md."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
E = '{ id = "dev.jasper.enterprise", version = ">=0.1", optional = true }'
edit("plugins/remote/src/main/resources/plugin.toml", [
    ('''{ id = "dev.jasper.editor", version = ">=0.2", optional = true }]''', '''{ id = "dev.jasper.editor", version = ">=0.2", optional = true }, ''' + E + ''']''', 1)])
edit("plugins/fileserver/src/main/resources/plugin.toml", [
    ('''requires = [{ id = "dev.jasper.vault", version = ">=0.2", optional = true }]''',
     '''requires = [{ id = "dev.jasper.vault", version = ">=0.2", optional = true }, ''' + E + ''']''', 1)])
edit("plugins/vault/src/main/resources/plugin.toml", [
    ('''capabilities = ["palette.contribute"]\n''', '''capabilities = ["palette.contribute"]\nrequires = [''' + E + ''']\n''', 1)])
edit("plugins/remote/build.gradle.kts", [
    ('''    compileOnly(project(":jasper-plugin-editor"))\n''', '''    compileOnly(project(":jasper-plugin-editor"))\n    compileOnly(project(":jasper-plugin-enterprise"))\n''', 1),
    ('''    testImplementation(project(":jasper-plugin-editor"))\n''', '''    testImplementation(project(":jasper-plugin-editor"))\n    testImplementation(project(":jasper-plugin-enterprise"))\n''', 1)])
edit("plugins/fileserver/build.gradle.kts", [
    ('''    compileOnly(project(":jasper-plugin-vault"))\n''', '''    compileOnly(project(":jasper-plugin-vault"))\n    compileOnly(project(":jasper-plugin-enterprise"))\n''', 1),
    ('''    testImplementation(project(":jasper-plugin-vault"))\n''', '''    testImplementation(project(":jasper-plugin-vault"))\n    testImplementation(project(":jasper-plugin-enterprise"))\n''', 1)])
edit("plugins/vault/build.gradle.kts", [
    ('''    compileOnly(project(":jasper-sdk"))\n''', '''    compileOnly(project(":jasper-sdk"))\n    compileOnly(project(":jasper-plugin-enterprise"))\n''', 1),
    ('''    testImplementation(project(":jasper-sdk-testkit"))\n''', '''    testImplementation(project(":jasper-sdk-testkit"))\n    testImplementation(project(":jasper-plugin-enterprise"))\n''', 1)])
for plugin, owner, read in [("remote", "plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java",
        ('''        try { vault = context.services().find(VaultApi.class); } catch (NoClassDefFoundError absent) { vault = Optional.empty(); }\n''',
         '''        try { vault = context.services().find(VaultApi.class); } catch (NoClassDefFoundError absent) { vault = Optional.empty(); }\n        layout = dev.jasper.remote.workstation.Layout.of(context.services());\n''')),
    ("fileserver", "plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java",
        ('''        try { vault = context.services().find(VaultApi.class); } catch (NoClassDefFoundError absent) { vault = Optional.empty(); }\n''',
         '''        try { vault = context.services().find(VaultApi.class); } catch (NoClassDefFoundError absent) { vault = Optional.empty(); }\n        layout = dev.jasper.fileserver.workstation.Layout.of(context.services());\n''')),
    ("vault", "plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java",
        ('''        this.context = context;\n        stopped = false;\n''',
         '''        this.context = context;\n        layout = dev.jasper.vault.workstation.Layout.of(context.services());\n        stopped = false;\n'''))]:
    L = f"dev.jasper.{plugin}.workstation.Layout"
    edit(owner, [
        ('''    private PluginContext context;\n''', f'''    private PluginContext context;
    /** Which composition this run takes (Enterprise presentation spec 14.3), read once in {{@link #start}}. */
    private {L} layout = {L}.ROWS;
    /** The composition this run takes. */
    {L} layout() {{ return layout; }}\n''', 1),
        (read[0], read[1], 1)])
edit("gradle/plugin-architecture.gradle.kts", [
    (''':jasper-plugin-remote" to listOf("dev.jasper.vault.api", "dev.jasper.editor.api")''',
     ''':jasper-plugin-remote" to listOf("dev.jasper.vault.api", "dev.jasper.editor.api", "dev.jasper.enterprise.api")''', 1),
    (''':jasper-plugin-fileserver" to listOf("dev.jasper.vault.api")''', ''':jasper-plugin-fileserver" to listOf("dev.jasper.vault.api", "dev.jasper.enterprise.api")''', 1),
    ('''":jasper-plugin-indexer" to listOf("dev.jasper.editor.api", "dev.jasper.remote.api"))''',
     '''":jasper-plugin-indexer" to listOf("dev.jasper.editor.api", "dev.jasper.remote.api"), ":jasper-plugin-vault" to listOf("dev.jasper.enterprise.api"))''', 1),
    ('''val pluginImports = pluginProjects.keys.associateWith { declaredImports[it] ?: listOf<String>() }\n''',
     '''val pluginImports = pluginProjects.keys.associateWith { declaredImports[it] ?: listOf<String>() }
// Enterprise presentation spec 14.3: a consumer names the Enterprise API only in its own workstation package.
val workstationPackages = mapOf(":jasper-plugin-remote" to "dev.jasper.remote.workstation",
    ":jasper-plugin-fileserver" to "dev.jasper.fileserver.workstation", ":jasper-plugin-vault" to "dev.jasper.vault.workstation")\n''', 1),
    ('''                val target = to.substringBeforeLast('.')\n''',
     '''                val target = to.substringBeforeLast('.')
                if (target == "dev.jasper.enterprise.api" && target !in own)
                    check(from.substringBeforeLast('.') == workstationPackages[path]) {
                        "$path references dev.jasper.enterprise.api outside ${workstationPackages[path] ?: "a workstation package"}: $line" }\n''', 1)])
edit("AGENTS.md", [
    ('''publishes `dev.jasper.enterprise.api.Workstation`, which other plugins reach through that package only.''',
     '''publishes `dev.jasper.enterprise.api.Workstation`, which other plugins reach through that package only. Remote, File Server and Vault optionally require Enterprise and name `dev.jasper.enterprise.api` only in their own `dev.jasper.<plugin>.workstation` package (`verifyPluginArchitecture`).''', 1)])
PY
```

The AGENTS.md anchor is the end of 5b's Enterprise sentence (5b Task 8 Step 1 appends it after the Editor sentence), so
the consumer sentence follows the one that introduces Enterprise; the bullet stays one line, as the file writes it.

- [ ] **Step 4: Run the tests and the gate**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.workstation.*' --tests 'dev.jasper.remote.RemotePluginTest' :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.workstation.*' --tests 'dev.jasper.fileserver.FileServerPluginTest' :jasper-plugin-vault:test --tests 'dev.jasper.vault.workstation.*' --tests 'dev.jasper.vault.VaultPluginTest' verifyPluginArchitecture :jasper-app:test --tests 'dev.jasper.app.plugins.BundledSamplePluginTest' --tests 'dev.jasper.app.plugins.PluginZipsTest' --tests 'dev.jasper.app.plugins.DescriptorParserTest' --continue`
Expected: PASS: the three `LayoutTest` classes (3 tests each), the new plugin cases, the existing plugin suites,
`verifyPluginArchitecture`, and the bundled-plugin tests (an optional requirement on the off-by-default Enterprise is
neither a problem nor a cycle).

Prove the gate refuses a stray reference: temporarily add `dev.jasper.enterprise.api.Workstation.class.getName();` as the
first statement of `RemotePlugin.start`, run `./gradlew verifyPluginArchitecture`, and expect FAIL with
":jasper-plugin-remote references dev.jasper.enterprise.api outside dev.jasper.remote.workstation: dev.jasper.remote.RemotePlugin
-> dev.jasper.enterprise.api.Workstation …". Remove the line and run it again: PASS.

- [ ] **Step 5: Run the ledger test and commit**

`Layout`, `FakeEnterprise` and `LayoutTest` match no `ThemeMigrationCoverageTest.CANDIDATE` pattern, so they take no
`migration.csv` row. Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS.

```bash
git add plugins/remote plugins/fileserver plugins/vault gradle/plugin-architecture.gradle.kts AGENTS.md
git commit -F - <<'EOF'
Plugin compositions: Remote, File Server and Vault find Workstation

Each optionally requires Enterprise, compiles against its API and reads
dev.jasper.<plugin>.workstation.Layout once in start: CLASSIC while
Workstation is published, ROWS otherwise, including when Enterprise is
not installed. verifyPluginArchitecture confines dev.jasper.enterprise.api
to those three packages, and AGENTS.md says so.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: The Enterprise render harness and the mock matcher

The JUnit side of spec 16.1.4 for plugin surfaces: the existing `testsupport.EnterpriseLook` (plans 6a and 5b) installs
the shipped kit, the real Plex files and the Enterprise theme, and gains the resolved theme the fixtures take;
`StagedEnterprise` starts the staged Enterprise plugin in a real plugin host so staged consumers find
`Workstation`; `PanelSurfaceFixture` gains an Enterprise mode and the Sessions panel; `MockMatch` compares a 2× render
with a mock crop as `tools/visual/compare_mock.py` does, plus colour bands and colour presence.

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/testsupport/MockMatch.java`,
  `jasper-app/src/test/java/dev/jasper/app/testsupport/MockMatchTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/StagedEnterprise.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/StagedEnterpriseTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java` (`selection`, `resolved`),
  `jasper-app/src/test/java/dev/jasper/app/plugins/PanelSurfaceFixture.java`, `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Task 1's manifests; 5b's `EnterpriseResources` and 6a/5b's `testsupport.EnterpriseLook` (`kit()`, `theme()`,
  `controller(boolean)`, `restoreDefault()`); 5a's `ThemeController.startup(…, Kit, …)`; `PluginClassLoader`, `HostedPlugin`,
  `ManagedVaultLoadingTest.jars(Path)`, `AppContractTest.onEdt`; 7's `MockCrops.fontsFloor`.
- Produces: `public final class MockMatch` with `REFERENCE`, `OUT`, `record Crop(String mock, int x, int y, int width, int
  height)` (`read()`, `width()`/`height()` in device px), `record Band(int start, int length, Color first, Color last)`,
  `static List<Band> bands(BufferedImage image, int x)` and `bands(image, x, top, bottom)`, `static List<Band> parse(String)`, `static String
  mismatch(List<Band> expected, List<Band> actual, int tolerance)`, `static double deltaE(Color, Color)`, `static boolean
  present(BufferedImage, Color)`, `static double agreement(BufferedImage mock, BufferedImage render)`, `static
  BufferedImage onto(BufferedImage, Color)`, `static void save(String name, BufferedImage mock, BufferedImage render)`;
  `dev.jasper.app.testsupport.EnterpriseLook` gains `static ThemeSelection selection()` and `static ResolvedTheme
  resolved(boolean dark)` (its `kit()`, `controller(boolean dark)` and `restoreDefault()` exist);
  `final class StagedEnterprise { static PluginClassLoader start(PluginHost host) }`; `PanelSurfaceFixture(Path,
  ResolvedTheme, String panel, boolean enterprise)`, `PanelSurfaceFixture.SESSIONS`, `String layout()`, `BufferedImage
  render(int width, int height)` (2×, opaque over the theme's `surface.panel`, the panel's header row cut off), `int
  headerHeight()`; in Enterprise mode the fixture frames the header `PanelHostHeader.Frame.FLUSH` (plan 7).

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/testsupport/MockMatchTest.java`:

```java
package dev.jasper.app.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import org.junit.jupiter.api.Test;

/** The matcher reads the mocks as Plan 10a's measurements do, and finds a shifted copy as compare_mock.py does. */
class MockMatchTest {
    static final String CONNECTED = "01 Workstation/Workstation - Connected (plugins in use).png";
    static final MockMatch.Crop SESSIONS = new MockMatch.Crop(CONNECTED, 2136, 230, 700, 782);

    @Test void theMockAgreesWithItselfAndAShiftedCopyIsFound() {
        var mock = SESSIONS.read();
        assertThat(MockMatch.agreement(mock, mock)).isEqualTo(1.0);
        var shifted = new MockMatch.Crop(CONNECTED, 2140, 234, 700, 782).read();
        assertThat(MockMatch.agreement(mock, shifted)).as("a 4 px shift is inside the search").isGreaterThan(0.95);
    }

    @Test void theSessionsColumnReadsAsMeasured() {
        var measured = MockMatch.parse("0+36 #F7F7F8, 36+1 #DDE0E4, 37+20 #F0F1F3, 57+1 #D3D6DA, 58+44 #FFFFFF, 102+22 #2F65CA, "
            + "124+111 #FFFFFF, 235+1 #C4C7CC, 236+22 #F7F7F8, 258+1 #DDE0E4, 259+22 #F7F7F8, 281+1 #E6E8EB, 282+22 #F7F7F8, "
            + "304+1 #E6E8EB, 305+22 #F7F7F8, 327+1 #E6E8EB, 328+22 #F7F7F8, 350+1 #E6E8EB, 351+40 #F7F7F8");
        assertThat(MockMatch.mismatch(measured, MockMatch.bands(SESSIONS.read(), 4), 0)).isEmpty();
        assertThat(MockMatch.mismatch(measured, MockMatch.bands(SESSIONS.read(), 4).subList(1, 19), 0)).isNotEmpty();
    }

    @Test void aGradientIsOneBandAndColoursAreFound() {
        var combo = new MockMatch.Crop(CONNECTED, 660, 110, 720, 68).read();
        assertThat(MockMatch.mismatch(MockMatch.parse("0+6 #ECEDEF, 6+1 #B4B8BF, 7+20 #FFFFFF~#F1F2F4, 27+1 #B4B8BF, 28+6 #ECEDEF"),
            MockMatch.bands(combo, 130), 0)).isEmpty();
        assertThat(MockMatch.present(combo, new Color(0x2F65CA))).isTrue();
        assertThat(MockMatch.present(combo, new Color(0xD03A3A))).isFalse();
        assertThat(MockMatch.deltaE(Color.WHITE, Color.WHITE)).isZero();
        assertThat(MockMatch.deltaE(Color.WHITE, Color.BLACK)).isGreaterThan(99);
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/plugins/StagedEnterpriseTest.java`:

```java
package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseLook;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The staged Enterprise plugin in the real host publishes Workstation, and a staged consumer composes CLASSIC under it. */
class StagedEnterpriseTest {
    @TempDir Path root;

    @Test void aStagedConsumerReadsTheClassicLayoutUnderEnterpriseAndRowsWithout() throws Exception {
        var controller = AppContractTest.onEdtValue(() -> EnterpriseLook.controller(false));
        try {
            try (var classic = new PanelSurfaceFixture(root.resolve("classic"), EnterpriseLook.resolved(false), PanelSurfaceFixture.TUNNELS, true)) {
                assertThat(classic.layout()).isEqualTo("CLASSIC");
            }
            try (var rows = new PanelSurfaceFixture(root.resolve("rows"), EnterpriseLook.resolved(false), PanelSurfaceFixture.TUNNELS, false)) {
                assertThat(rows.layout()).isEqualTo("ROWS");
            }
        } finally {
            AppContractTest.onEdt(() -> { controller.close(); EnterpriseLook.restoreDefault(); });
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile: "cannot find symbol … class MockMatch", "… resolved(boolean)", "constructor PanelSurfaceFixture …
cannot be applied" and "cannot find symbol … layout()".

- [ ] **Step 3: Write the harness**

Create `jasper-app/src/test/java/dev/jasper/app/testsupport/MockMatch.java`:

```java
package dev.jasper.app.testsupport;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Compares a 2x render with a crop of an Enterprise mock (spec 16.1.4) in JUnit: the luminance-edge agreement of
 * {@code tools/visual/compare_mock.py} over the same shift search, colour bands down a sample column, and colour presence.
 * Images are compared at their own (device) pixels; bands report logical pixels (device pixels halved).
 */
public final class MockMatch {
    /** The preserved mocks. */
    public static final Path REFERENCE = EnterpriseResources.ROOT.resolve("docs/design/enterprise/reference");
    /** Where renders and side-by-side images go, for inspection and for {@code tools/visual/pairs.json}. */
    public static final Path OUT = EnterpriseResources.ROOT.resolve("jasper-app/build/enterprise-compositions");
    private static final int SEARCH = 12;

    /**
     * A crop of one mock, in its 2x pixels.
     *
     * @param mock the mock's path under {@link #REFERENCE}
     * @param x left edge
     * @param y top edge
     * @param width width in device pixels
     * @param height height in device pixels
     */
    public record Crop(String mock, int x, int y, int width, int height) {
        /** The crop as an opaque RGB image. */
        public BufferedImage read() {
            try {
                BufferedImage whole = ImageIO.read(REFERENCE.resolve(mock).toFile());
                var copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                var graphics = copy.createGraphics();
                try { graphics.drawImage(whole.getSubimage(x, y, width, height), 0, 0, null); } finally { graphics.dispose(); }
                return copy;
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }

        /** The crop's logical width (device pixels halved). */
        public int logicalWidth() { return width / 2; }

        /** The crop's logical height (device pixels halved). */
        public int logicalHeight() { return height / 2; }
    }

    /**
     * One run down a column, in logical pixels: a single colour, or a smooth gradient from {@code first} to {@code last}.
     *
     * @param start first logical row
     * @param length logical rows
     * @param first the run's first colour
     * @param last the run's last colour
     */
    public record Band(int start, int length, Color first, Color last) {
        @Override public String toString() {
            String colours = hex(first) + (deltaE(first, last) <= 2 ? "" : "~" + hex(last));
            return start + "+" + length + " " + colours;
        }
    }

    private MockMatch() { }

    static String hex(Color colour) { return String.format("#%06X", colour.getRGB() & 0xFFFFFF); }

    /** {@code render} laid over {@code under} and made opaque, as the surface below a panel body shows it. */
    public static BufferedImage onto(BufferedImage render, Color under) {
        var opaque = new BufferedImage(render.getWidth(), render.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = opaque.createGraphics();
        try {
            graphics.setColor(under); graphics.fillRect(0, 0, render.getWidth(), render.getHeight());
            graphics.drawImage(render, 0, 0, null);
        } finally { graphics.dispose(); }
        return opaque;
    }

    /**
     * The bands down device column {@code x}: consecutive pixels within ΔE76 1.5 of each other form one run (so a gradient
     * is one band), runs under 2 device pixels (antialiasing) are dropped, and equal neighbours left by a dropped run merge.
     */
    public static List<Band> bands(BufferedImage image, int x) { return bands(image, x, 0, image.getHeight()); }

    /** The bands down device column {@code x} between device rows {@code top} (inclusive) and {@code bottom}, from {@code top}. */
    public static List<Band> bands(BufferedImage image, int x, int top, int bottom) {
        record Run(int start, int length, Color first, Color last) { }
        var runs = new ArrayList<Run>();
        int start = top; Color first = new Color(image.getRGB(x, top)), previous = first;
        for (int y = top + 1; y <= bottom; y++) {
            Color colour = y < bottom ? new Color(image.getRGB(x, y)) : null;
            if (colour == null || deltaE(colour, previous) > 1.5) {
                runs.add(new Run(start, y - start, first, previous));
                if (colour != null) { start = y; first = colour; }
            }
            if (colour != null) previous = colour;
        }
        var kept = new ArrayList<Run>();
        for (var run : runs) {
            if (run.length() < 2) continue;
            if (!kept.isEmpty() && deltaE(kept.getLast().last(), run.first()) <= 1.5) {
                var merged = kept.removeLast();
                kept.add(new Run(merged.start(), run.start() + run.length() - merged.start(), merged.first(), run.last()));
            } else kept.add(run);
        }
        var bands = new ArrayList<Band>();
        for (var run : kept) bands.add(new Band((run.start() - top) / 2, Math.max(1, (run.start() + run.length() - top) / 2 - (run.start() - top) / 2), run.first(), run.last()));
        return List.copyOf(bands);
    }

    /** Parses bands as the plan's measurement tables write them: {@code "0+36 #F7F7F8, 7+20 #FFFFFF~#F1F2F4"}. */
    public static List<Band> parse(String text) {
        var bands = new ArrayList<Band>();
        for (String part : text.split(",")) {
            String[] cells = part.strip().split("[+ ]");
            String[] colours = cells[2].split("~");
            Color first = Color.decode(colours[0]), last = colours.length > 1 ? Color.decode(colours[1]) : first;
            bands.add(new Band(Integer.parseInt(cells[0]), Integer.parseInt(cells[1]), first, last));
        }
        return List.copyOf(bands);
    }

    /**
     * Empty when {@code actual} matches {@code expected} band for band, each length within {@code tolerance} logical
     * pixels and each colour within ΔE76 4; otherwise both lists, for the assertion message.
     */
    public static String mismatch(List<Band> expected, List<Band> actual, int tolerance) {
        boolean same = expected.size() == actual.size();
        for (int i = 0; same && i < expected.size(); i++) {
            var e = expected.get(i); var a = actual.get(i);
            same = Math.abs(e.length() - a.length()) <= tolerance && deltaE(e.first(), a.first()) <= 4 && deltaE(e.last(), a.last()) <= 4;
        }
        return same ? "" : "expected " + expected + "\n  actual " + actual;
    }

    /** CIE76 colour difference under D65. */
    public static double deltaE(Color a, Color b) {
        double[] p = lab(a), q = lab(b);
        return Math.sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]));
    }

    private static double[] lab(Color colour) {
        double[] rgb = {colour.getRed() / 255.0, colour.getGreen() / 255.0, colour.getBlue() / 255.0};
        for (int i = 0; i < 3; i++) rgb[i] = rgb[i] <= 0.04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
        double x = (0.4124 * rgb[0] + 0.3576 * rgb[1] + 0.1805 * rgb[2]) / 0.95047;
        double y = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
        double z = (0.0193 * rgb[0] + 0.1192 * rgb[1] + 0.9505 * rgb[2]) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double f(double t) { return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116; }

    /** Whether a 2x2 block of {@code image} lies within ΔE76 6 of {@code colour}, as {@code check_pairs.py} asks. */
    public static boolean present(BufferedImage image, Color colour) {
        int w = image.getWidth(), h = image.getHeight();
        var close = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) close[y * w + x] = deltaE(new Color(image.getRGB(x, y)), colour) <= 6;
        for (int y = 0; y + 1 < h; y++) for (int x = 0; x + 1 < w; x++)
            if (close[y * w + x] && close[y * w + x + 1] && close[(y + 1) * w + x] && close[(y + 1) * w + x + 1]) return true;
        return false;
    }

    /**
     * compare_mock.py's score: both images on one canvas (magenta outside each), their luminance edges (central
     * differences above max(12, half the 90th percentile)), and the best symmetric fraction of edge pixels within one pixel
     * of the other's edges over render shifts of up to 12 pixels (a coarse 2-pixel search, then the 1-pixel neighbours).
     */
    public static double agreement(BufferedImage mock, BufferedImage render) {
        int w = Math.max(mock.getWidth(), render.getWidth()), h = Math.max(mock.getHeight(), render.getHeight());
        boolean[] a = edges(canvas(mock, w, h), w, h), b = edges(canvas(render, w, h), w, h);
        boolean[] da = dilate(a, w, h), db = dilate(b, w, h);
        double best = -1; int bx = 0, by = 0;
        for (int dy = -SEARCH; dy <= SEARCH; dy += 2) for (int dx = -SEARCH; dx <= SEARCH; dx += 2) {
            double score = score(a, da, shift(b, w, h, dx, dy), shift(db, w, h, dx, dy));
            if (score > best) { best = score; bx = dx; by = dy; }
        }
        int cx = bx, cy = by;
        for (int dy = cy - 1; dy <= cy + 1; dy++) for (int dx = cx - 1; dx <= cx + 1; dx++) {
            double score = score(a, da, shift(b, w, h, dx, dy), shift(db, w, h, dx, dy));
            if (score > best) best = score;
        }
        return best;
    }

    private static int[] canvas(BufferedImage image, int w, int h) {
        var pixels = new int[w * h];
        Arrays.fill(pixels, 0xFF00FF);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) pixels[y * w + x] = image.getRGB(x, y) & 0xFFFFFF;
        return pixels;
    }

    private static boolean[] edges(int[] pixels, int w, int h) {
        var gray = new float[w * h];
        for (int i = 0; i < gray.length; i++) {
            int p = pixels[i];
            gray[i] = (((p >> 16) & 0xFF) * 299 + ((p >> 8) & 0xFF) * 587 + (p & 0xFF) * 114) / 1000f;
        }
        var magnitude = new float[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float gx = x > 0 && x < w - 1 ? gray[y * w + x + 1] - gray[y * w + x - 1] : 0;
            float gy = y > 0 && y < h - 1 ? gray[(y + 1) * w + x] - gray[(y - 1) * w + x] : 0;
            magnitude[y * w + x] = (float) Math.hypot(gx, gy);
        }
        var sorted = magnitude.clone();
        Arrays.sort(sorted);
        double threshold = Math.max(12.0, sorted[(int) Math.round(0.9 * (sorted.length - 1))] * 0.5);
        var edges = new boolean[w * h];
        for (int i = 0; i < edges.length; i++) edges[i] = magnitude[i] > threshold;
        return edges;
    }

    private static boolean[] shift(boolean[] mask, int w, int h, int dx, int dy) {
        var result = new boolean[w * h];
        for (int y = Math.max(0, dy); y < Math.min(h, h + dy); y++)
            for (int x = Math.max(0, dx); x < Math.min(w, w + dx); x++) result[y * w + x] = mask[(y - dy) * w + x - dx];
        return result;
    }

    private static boolean[] dilate(boolean[] mask, int w, int h) {
        var out = mask.clone();
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            var moved = shift(mask, w, h, dx, dy);
            for (int i = 0; i < out.length; i++) out[i] |= moved[i];
        }
        return out;
    }

    private static double score(boolean[] a, boolean[] da, boolean[] b, boolean[] db) {
        long na = 0, nb = 0, ab = 0, ba = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i]) { na++; if (db[i]) ab++; }
            if (b[i]) { nb++; if (da[i]) ba++; }
        }
        return na == 0 || nb == 0 ? 0 : ((double) ab / na + (double) ba / nb) / 2;
    }

    /** Writes {@code <OUT>/<name>@2x.png} and a mock | render side-by-side {@code <OUT>/<name>-side.png}. */
    public static void save(String name, BufferedImage mock, BufferedImage render) {
        try {
            Path file = OUT.resolve(name + "@2x.png");
            Files.createDirectories(file.getParent());
            ImageIO.write(render, "png", file.toFile());
            var side = new BufferedImage(mock.getWidth() + render.getWidth() + 10, Math.max(mock.getHeight(), render.getHeight()), BufferedImage.TYPE_INT_RGB);
            var graphics = side.createGraphics();
            try { graphics.drawImage(mock, 0, 0, null); graphics.drawImage(render, mock.getWidth() + 10, 0, null); } finally { graphics.dispose(); }
            ImageIO.write(side, "png", OUT.resolve(name + "-side.png").toFile());
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
```

Add to `jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java` (plans 6a and 5b's shared fixture: its
`kit()` registers the real Plex files through `EnterpriseResources.kit()`, `controller(boolean)` installs the kit and
theme, `restoreDefault()` puts Slate Cobalt back), before its `restoreDefault` method:

```java
    /** The shipped theme for both appearances, as a selection (plan 10a). */
    public static ThemeSelection selection() {
        var shown = theme();
        return new ThemeSelection(ThemeLibrary.bundledOnly(), shown, shown, TerminalSchemeChoice.MATCH);
    }

    /** The shipped theme resolved for {@code dark}, as the staged panel fixtures take it (plan 10a). */
    public static dev.jasper.app.appearance.ResolvedTheme resolved(boolean dark) {
        return dev.jasper.app.appearance.ResolvedTheme.of(dark, selection());
    }
```

`ThemeSelection`, `ThemeLibrary` and `TerminalSchemeChoice` are already imported there.

Create `jasper-app/src/test/java/dev/jasper/app/plugins/StagedEnterprise.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** The staged Enterprise jar started in a real plugin host, so staged consumers find Workstation (spec 14.2). */
final class StagedEnterprise {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.enterprise", "Enterprise", "0.1.0", Set.of(Capabilities.PRESENTATION_PROVIDE));

    private StagedEnterprise() { }

    /** Starts it in {@code host}; the returned loader is the provider of {@code dev.jasper.enterprise.api}, closed by the caller. */
    static PluginClassLoader start(PluginHost host) throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins")).resolve(INFO.id());
        var loader = new PluginClassLoader(INFO.id(), ManagedVaultLoadingTest.jars(staged), StagedEnterprise.class.getClassLoader(), Map.of());
        var plugin = (Plugin) loader.loadClass("dev.jasper.enterprise.EnterprisePlugin").getConstructor().newInstance();
        AppContractTest.onEdt(() -> host.start(new HostedPlugin(INFO, Set.of(), Set.of(), Set.of("dev.jasper.enterprise.api"), loader, () -> plugin)));
        return loader;
    }
}
```

`HostedPlugin`'s six-argument constructor is the convenience form `StagedRemoteHost` calls (`HostedPlugin.java:24`).

Now extend `PanelSurfaceFixture`:

```bash
python3 -I - <<'PY'
"""Plan 10a Task 2 step 3: PanelSurfaceFixture's Enterprise mode, the Sessions panel and the render."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
F = "jasper-app/src/test/java/dev/jasper/app/plugins/PanelSurfaceFixture.java"
edit(F, [
    ('''    public static final String TUNNELS = "dev.jasper.remote.tunnels.panel", SFTP = "dev.jasper.remote.sftp.panel", FILE_SERVERS = "dev.jasper.fileserver.panel";''',
     '''    public static final String TUNNELS = "dev.jasper.remote.tunnels.panel", SFTP = "dev.jasper.remote.sftp.panel", FILE_SERVERS = "dev.jasper.fileserver.panel",
        SESSIONS = "dev.jasper.remote.panel";''', 1),
    ('''    private final PluginClassLoader loader;
''', '''    private final PluginClassLoader loader;
    /** The staged Enterprise plugin's loader in Enterprise mode (plan 10a), else null. */
    private final PluginClassLoader enterprise;
    private final ResolvedTheme theme;
    /** Enterprise mode frames the panel's header as plan 7's flush strip, as the dock does under Enterprise. */
    private final boolean flush;
    /** The id of the header row the panel's view draws on top ({@link #render} cuts it). */
    private final String headerId;
''', 1),
    ('''    public PanelSurfaceFixture(Path home, ResolvedTheme theme, String panel) throws Exception {
        this.home = home;''',
     '''    public PanelSurfaceFixture(Path home, ResolvedTheme theme, String panel) throws Exception { this(home, theme, panel, false); }

    /**
     * {@code enterprise}: the staged Enterprise plugin starts first in the same host and the plugin may see its API, so it
     * finds Workstation and composes its classic layout (plan 10a). The caller installs the Enterprise kit and theme first
     * ({@code EnterpriseLook.controller}) and passes the matching {@code theme}.
     */
    public PanelSurfaceFixture(Path home, ResolvedTheme theme, String panel, boolean enterprise) throws Exception {
        this.home = home; this.theme = theme; this.flush = enterprise;''', 1),
    ('''        boolean files = panel.equals(FILE_SERVERS);''',
     '''        this.enterprise = enterprise ? StagedEnterprise.start(vault.host) : null;
        Map<String, ClassLoader> exports = enterprise ? Map.of("dev.jasper.vault.api", vault.loader, "dev.jasper.enterprise.api", this.enterprise) : Map.of("dev.jasper.vault.api", vault.loader);
        var optional = enterprise ? Set.of("dev.jasper.vault", "dev.jasper.enterprise") : Set.of("dev.jasper.vault");
        boolean files = panel.equals(FILE_SERVERS);''', 1),
    ('''getClass().getClassLoader(), Map.of("dev.jasper.vault.api", vault.loader));''', '''getClass().getClassLoader(), exports);''', 1),
    ('''new HostedPlugin(FileServerRenderEvidenceTest.INFO, Set.of(), Set.of("dev.jasper.vault"), Set.of(), loader, () -> plugin)''',
     '''new HostedPlugin(FileServerRenderEvidenceTest.INFO, Set.of(), optional, Set.of(), loader, () -> plugin)''', 1),
    ('''new HostedPlugin(StagedRemoteHost.REMOTE, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), Set.of("dev.jasper.remote.api"), loader, () -> plugin)''',
     '''new HostedPlugin(StagedRemoteHost.REMOTE, Set.of(), enterprise ? Set.of("dev.jasper.vault", "dev.jasper.editor", "dev.jasper.enterprise")
                : Set.of("dev.jasper.vault", "dev.jasper.editor"), Set.of("dev.jasper.remote.api"), loader, () -> plugin)''', 1),
    ('''        root = panel.equals(TUNNELS) ? "tunnels" : panel.equals(SFTP) ? "sftp" : "fileserver.panel";''',
     '''        root = panel.equals(TUNNELS) ? "tunnels" : panel.equals(SFTP) ? "sftp" : panel.equals(SESSIONS) ? "sessions" : "fileserver.panel";
        headerId = panel.equals(FILE_SERVERS) ? "fileserver.header" : root + ".header";''', 1),
    ('''? new PanelHostHeader.Artwork(named.name(), named.pathData()) : null)).toList()));''',
     '''? new PanelHostHeader.Artwork(named.name(), named.pathData()) : null)).toList(),
            flush ? PanelHostHeader.Frame.FLUSH : PanelHostHeader.Frame.STANDARD));''', 1),
    ('''    public KitView view() { return view; }''',
     '''    public KitView view() { return view; }

    /** The plugin's {@code layout()} as a name: CLASSIC under Enterprise, ROWS otherwise (plan 10a Task 1). */
    public String layout() {
        try { var read = plugin.getClass().getDeclaredMethod("layout"); read.setAccessible(true); return read.invoke(plugin).toString(); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /**
     * The panel's body at {@code width} by {@code height} logical pixels, painted at 2x over the theme's panel surface:
     * the view is laid out {@link #headerHeight()} taller and its header row is cut off, so the render starts under the
     * header strip as the mock crops do (plan 7 owns and compares the strip).
     */
    public BufferedImage render(int width, int height) {
        size(width, height);
        size(width, height + headerHeight());
        int top = headerHeight();
        var whole = capture(2);
        return dev.jasper.app.testsupport.MockMatch.onto(whole.getSubimage(0, 2 * top, whole.getWidth(), 2 * height),
            theme.workspace().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL));
    }

    /** The bottom, in logical pixels from the view's top, of the header row the panel's view draws (its strip and rule). */
    public int headerHeight() {
        return onEdtValue(() -> {
            var header = StagedVaultHost.named(view, headerId);
            if (header == null) throw new AssertionError("Missing " + headerId);
            var bounds = SwingUtilities.convertRectangle(header.getParent(), header.getBounds(), view);
            return bounds.y + bounds.height;
        });
    }''', 1),
    ('''    @Override public void close() throws Exception { try { onEdt(site::notifyClosed); } finally { try { vault.close(); } finally { loader.close(); } } }''',
     '''    @Override public void close() throws Exception {
        try { onEdt(site::notifyClosed); } finally { try { vault.close(); } finally { try { loader.close(); } finally { if (enterprise != null) enterprise.close(); } } }
    }''', 1)])
PY
```

The test task already reads the mocks (`inputs.dir(rootProject.file("docs/design/enterprise/reference"))`,
`jasper-app/build.gradle.kts:108`), so the build needs no edit.
`PanelSurfaceFixture`'s Sessions mode mounts the panel `dev.jasper.remote.panel` through its real factory like the others;
the Vault, the plugin host and the stores are unchanged.

The panel's own `Nodes.Header` row is drawn inside the kit view (the dock borrows it and frames it: plan 7 Task 10's
`PanelHostHeader.Frame`), while every panel crop of "Mock measurements" starts under that strip (the strip and its rule
sit at mock y 180-229 above the Sessions and SFTP crops, 1014-1063 above Tunnels and 944-993 above File Servers). So in
Enterprise mode the fixture sends `PanelHostHeader.Frame.FLUSH`, as `ToolWindowArea.frame(Host)` does under a flush look
(the header takes the strip's height, 24 + 1 px rule), and `render` lays the view out that much taller and cuts the
header row off: a render's row 0 is the crop's row 0. The strip's own pixels (`surface.header` and
`surface.header.active` over `border.window`) are plan 7's, compared by its `DockMockTest`; this plan does not measure
them. The default mode keeps `Frame.STANDARD`, so the fixture's existing users (`FileServerRenderEvidenceTest`, the
staged Tunnels and SFTP tests) see the header as before; none of them calls `render`. If plan 7 named the frame
otherwise, use its enum and record it in the banner.

Add the ledger rows (`MockMatch`, `MockMatchTest` and `StagedEnterpriseTest` match the pattern through `java.awt` or
`new …Fixture`-like names; `StagedEnterprise` matches nothing and takes a row anyway, as a staged-host helper;
`PanelSurfaceFixture` and `testsupport/EnterpriseLook` already have their rows):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv")
rows = [
    ("jasper-app/src/test/java/dev/jasper/app/testsupport/MockMatch.java", "app.testsupport", "MockMatch"),
    ("jasper-app/src/test/java/dev/jasper/app/testsupport/MockMatchTest.java", "app.testsupport", "MockMatchTest"),
    ("jasper-app/src/test/java/dev/jasper/app/plugins/StagedEnterprise.java", "app.plugins", "StagedEnterprise"),
    ("jasper-app/src/test/java/dev/jasper/app/plugins/StagedEnterpriseTest.java", "app.plugins", "StagedEnterpriseTest")]
text = path.read_text(encoding="utf-8")
for file, owner, cls in rows:
    if file in text: raise SystemExit(f"already ledgered: {file}")
    text += f"{file},{owner},{cls},nonvisual-helper,none,none,{file},Test/fixture ownership; New in Enterprise plan 10a Task 2\n"
path.write_text(text, encoding="utf-8")
PY
```

If the ledger's last line has no trailing newline, the script's first row joins it: check with `tail -c1` first and
add the newline.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.testsupport.MockMatchTest' --tests 'dev.jasper.app.plugins.StagedEnterpriseTest' --tests 'dev.jasper.app.plugins.FileServerRenderEvidenceTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `MockMatchTest` (3), `StagedEnterpriseTest` (1), the existing fixture users unchanged, the ledger test.
If `theSessionsColumnReadsAsMeasured` fails, print `MockMatch.bands(SESSIONS.read(), 4)` and compare with the Python
measurement (`tools`-style: `python3 -I` with Pillow, column 2140 from y 230): the two must agree, as the plan's tables
are written from the Python one; fix `bands` (never the measured text).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/test docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Plugin compositions: the Enterprise render harness and the mock matcher

EnterpriseLook gains the resolved Enterprise theme the staged fixtures
take for headless renders; StagedEnterprise starts the
staged Enterprise plugin in a real host so staged consumers find
Workstation; PanelSurfaceFixture gains an Enterprise mode, the Sessions
panel and a 2x render over the panel surface. MockMatch compares a
render with a mock crop in JUnit: compare_mock.py's edge agreement,
colour bands down a sample column, and colour presence.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: Classic Properties: the grid and the label-colon lines

Rulings 4: Properties follow the kit's `table` point. The default face is 3b's code moved behind a face, unchanged; the
classic face draws a titled list as the Workstation grid and an untitled one as label-colon lines (plan 10b's Details).
Three metric roles, equal in both catalogs, carry the mock's widths.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/PropertiesFace.java`, `DefaultPropertiesFace.java`, `ClassicPropertiesFace.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/PropertyLists.java` (whole file), `KitFaces.java`,
  `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java`, `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv`,
  `classic-roles.tsv`, `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicPropertiesTest.java`

**Interfaces:**
- Consumes: 3b's `PropertyLists` (`PropertiesControl`, `Grid`, `valuePair`), 4b's `Face`, `KitFaces`, `ThemePainting.role` and
  `painted`, 4a's `ColorSlots.dialogSurface()` and `ROW_HEIGHT`, 4c's `ICON_GAP` use in table cells.
- Produces: package-private `interface PropertiesFace extends Face { JComponent container(PropertyLists.PropertiesControl c);
  String labelText(Nodes.Properties node, String label); void style(PropertyLists.PropertiesControl c);
  Composition.WidthLayout grid(PropertyLists.PropertiesControl c); void paint(PropertyLists.PropertiesControl c, Graphics2D g,
  int width, int height); }`; `final PropertiesFace KitFaces.properties`; `DesignMetrics.Role.PROPERTY_LABEL_WIDTH(89)`,
  `PROPERTY_PLAIN_LABEL_WIDTH(80)`, `PROPERTY_PLAIN_ROW_GAP(2)`; `PropertiesControl.rows` (the `<id>.rows` container),
  `face`, `labels`, `values`, `title`.

- [ ] **Step 1: Take the gallery base and write the failing test**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
rm -rf jasper-ui/build/plan10-base && mkdir -p jasper-ui/build/plan10-base && cp -R jasper-ui/build/gallery/dark-1x jasper-ui/build/gallery/light-1x jasper-ui/build/plan10-base/
```

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicPropertiesTest.java`:

```java
package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Plan 10a Rulings 4: classic Properties are the Workstation grid when titled and label-colon lines when untitled. */
class ClassicPropertiesTest {
    static ViewSpec properties(String title) {
        return new ViewSpec(new Nodes.Properties("props", ControlState.READY, title, List.of(
            new Nodes.PropertyRow("Host", "192.168.1.124", true), new Nodes.PropertyRow("User", "dustin", true),
            new Nodes.PropertyRow("Port", "22", true),
            new Nodes.PropertyRow("Credentials", "Vault locked", false, Optional.of(Nodes.Severity.WARNING)))));
    }

    static int rgb(BufferedImage image, int x, int y) { return image.getRGB(x, y) & 0xFFFFFF; }
    static int rgb(Color colour) { return colour.getRGB() & 0xFFFFFF; }

    @Test void aTitledListIsTheWorkstationGrid() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, properties("Properties — minecraft-local"), event -> { })) {
                var image = KitFixtures.render(view, 350, 120, 1);
                int row = ThemePainting.metric(context, DesignMetrics.Role.ROW_HEIGHT), hairline = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
                int label = ThemePainting.metric(context, DesignMetrics.Role.PROPERTY_LABEL_WIDTH);
                assertThat(row).as("the classic row").isEqualTo(22);
                assertThat(label).as("the mock's label column: x 0 to 88, its rule at x 89").isEqualTo(89);
                assertThat(rgb(image, 2, 0)).as("the rule above the title").isEqualTo(rgb(ThemePainting.role(context, SemanticColor.BORDER_WINDOW)));
                assertThat(rgb(image, 2, 1 + row / 2)).as("the title band").isEqualTo(rgb(ThemePainting.role(context, SemanticColor.SURFACE_PANEL)));
                assertThat(rgb(image, 2, 1 + row)).as("the rule under the title").isEqualTo(rgb(ThemePainting.role(context, SemanticColor.BORDER_SUBTLE)));
                int top = 2 + row;
                assertThat(rgb(image, 2, top + row / 2)).as("the label column").isEqualTo(rgb(ThemePainting.role(context, SemanticColor.SURFACE_PANEL)));
                assertThat(rgb(image, label + hairline + 2, top + 1)).as("the value cell").isEqualTo(rgb(ThemePainting.role(context, SemanticColor.SURFACE_CARD)));
                assertThat(rgb(image, label, top + row / 2)).as("the column rule").isEqualTo(rgb(ThemePainting.role(context, DerivedColor.BORDER_ROW)));
                assertThat(rgb(image, 2, top + row)).as("the rule under a row").isEqualTo(rgb(ThemePainting.role(context, DerivedColor.BORDER_ROW)));
                assertThat(view.getPreferredSize().height).as("title and four rows, each with its rule").isEqualTo(2 + row + 4 * (row + hairline));
                var value = (JLabel) KitViewTest.find(view, "props.value.0", JLabel.class);
                var at = SwingUtilities.convertPoint(value.getParent(), value.getLocation(), view);
                assertThat(at.x).as("values start one gap after the column rule").isEqualTo(label + hairline + ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP));
                assertThat(((JLabel) KitViewTest.find(view, "props.label.3", JLabel.class)).getText()).as("no colon in the grid").isEqualTo("Credentials");
                assertThat(rgb(((JLabel) KitViewTest.find(view, "props.value.3", JLabel.class)).getForeground()))
                    .isEqualTo(rgb(ThemePainting.painted(context, PropertyLists.valuePair(Nodes.Severity.WARNING))));
                KitFixtures.capture(context, view.spec(), 350, 120, "properties-grid");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void anUntitledListIsLabelColonLines() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, properties(""), event -> { })) {
                KitFixtures.render(view, 300, 120, 1);
                var label = (JLabel) KitViewTest.find(view, "props.label.0", JLabel.class);
                var value = (JLabel) KitViewTest.find(view, "props.value.0", JLabel.class);
                assertThat(label.getText()).isEqualTo("Host:");
                assertThat(SwingUtilities.convertPoint(value.getParent(), value.getLocation(), view).x)
                    .isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.PROPERTY_PLAIN_LABEL_WIDTH));
                var second = (JLabel) KitViewTest.find(view, "props.label.1", JLabel.class);
                assertThat(second.getY() - label.getY()).isEqualTo(label.getHeight() + ThemePainting.metric(context, DesignMetrics.Role.PROPERTY_PLAIN_ROW_GAP));
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theDefaultKitKeepsThreeBsCard() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), Kit.DEFAULT);
            try (var view = new KitView(context, properties("Properties"), event -> { })) {
                KitFixtures.render(view, 350, 160, 1);
                assertThat(KitViewTest.find(view, "props.rows", javax.swing.JComponent.class)).isInstanceOf(Cards.CardSurface.class);
                assertThat(((JLabel) KitViewTest.find(view, "props.label.0", JLabel.class)).getText()).isEqualTo("Host");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theClassicFaceDeclaresItsPairs() {
        var face = new ClassicPropertiesFace(SemanticColor.SURFACE_DIALOG);
        assertThat(face.pairs()).extracting(pair -> pair.slot().key() + " on " + pair.against().key()).containsExactly(
            "text.strong.secondary on surface.panel", "text.secondary on surface.panel", "text.primary on surface.card",
            "border.row on surface.card", "border.window on surface.panel", "text.secondary on surface.dialog",
            "text.primary on surface.dialog");
        assertThat(new DefaultPropertiesFace().pairs()).isEmpty();
    }
}
```

`KitViewTest.find(view, name, type)` and `release(context)` are the jasper-ui test helpers the face tests of plans 4b and
4c use (4b's `ClassicTogglesTest` imports them statically); if 4b named them otherwise, use its names.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicPropertiesTest'`
Expected: FAIL to compile: "cannot find symbol … PROPERTY_LABEL_WIDTH", "… ClassicPropertiesFace", "… DefaultPropertiesFace".

- [ ] **Step 3: The roles, the face pair and the control**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/PropertiesFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/**
 * How Properties lay out and paint (plan 10a Rulings 4), read from the kit's table point: the default card of SDK 0.18,
 * or the classic Workstation grid and label-colon lines.
 */
interface PropertiesFace extends Face {
    /** The rows' container, made once at the control's construction. */
    JComponent container(PropertyLists.PropertiesControl control);

    /** The text a row's label shows. */
    String labelText(Nodes.Properties node, String label);

    /** Fonts, colours, borders and the control's own layout, after each refresh. */
    void style(PropertyLists.PropertiesControl control);

    /** The layout of the rows' container. */
    Composition.WidthLayout grid(PropertyLists.PropertiesControl control);

    /** Paints the rows' container under its labels and values. */
    void paint(PropertyLists.PropertiesControl control, Graphics2D g, int width, int height);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultPropertiesFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Graphics2D;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.border.EmptyBorder;

/** SDK 0.18's Properties as plan 3b drew them: a section-label heading over a card of label and value rows. */
final class DefaultPropertiesFace implements PropertiesFace {
    @Override public List<PaintPair> pairs() { return List.of(); }

    @Override public JComponent container(PropertyLists.PropertiesControl control) { return new Cards.CardSurface(control.context()); }

    @Override public String labelText(Nodes.Properties node, String label) { return label; }

    @Override public void style(PropertyLists.PropertiesControl control) {
        var context = control.context(); var properties = (Nodes.Properties) control.node(); var theme = context.theme();
        for (int i = 0; i < control.labels.size(); i++) {
            var row = properties.rows().get(i); var label = control.labels.get(i); var value = control.values.get(i);
            Composition.style(label, context, Typography.Role.LABEL, SemanticColor.TEXT_SECONDARY);
            value.setFont(ThemePainting.font(context, row.technical() ? Typography.Role.MONO : Typography.Role.BODY, row.value()));
            value.setForeground(row.severity().map(severity -> SurfaceContrast.painted(theme, PropertyLists.valuePair(severity)))
                .orElseGet(() -> ThemePainting.color(context, SemanticColor.TEXT_PRIMARY)));
        }
        Composition.style(control.title, context, Typography.Role.SECTION_LABEL, SemanticColor.TEXT_TERTIARY);
        int pad = ThemePainting.metric(context, DesignMetrics.Role.CARD_PADDING) + ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
        control.rows.setBorder(new EmptyBorder(pad, pad, pad, pad));
        control.component().setLayout(new Composition.Vertical(ThemePainting.metric(context, DesignMetrics.Role.SECTION_LABEL_GAP)));
    }

    @Override public Composition.WidthLayout grid(PropertyLists.PropertiesControl control) { return new PropertyLists.Grid(control); }

    @Override public void paint(PropertyLists.PropertiesControl control, Graphics2D g, int width, int height) { }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicPropertiesFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.ColorRole;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;

/**
 * The classic Properties of the Enterprise mocks (plan 10a Rulings 4). Titled: the Workstation grid, a {@code ROW_HEIGHT}
 * title band on {@code surface.panel} between a {@code border.window} and a {@code border.subtle} rule, then rows of a
 * label column ({@code PROPERTY_LABEL_WIDTH}, {@code surface.panel}) and a value cell ({@code surface.card}), each row and
 * the column closed by a {@code border.row} rule. Untitled: label-colon lines on the dialog surface, the values at
 * {@code PROPERTY_PLAIN_LABEL_WIDTH}.
 */
final class ClassicPropertiesFace implements PropertiesFace {
    private final SemanticColor surface;

    ClassicPropertiesFace(SemanticColor dialogSurface) { this.surface = dialogSurface; }

    private PaintPair text(ColorRole slot, ColorRole against) { return new PaintPair(slot, against, SurfaceContrast.Guard.TEXT); }

    @Override public List<PaintPair> pairs() {
        return List.of(text(SemanticColor.TEXT_STRONG_SECONDARY, SemanticColor.SURFACE_PANEL), text(SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_PANEL),
            text(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CARD),
            new PaintPair(DerivedColor.BORDER_ROW, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.STRUCTURAL),
            new PaintPair(SemanticColor.BORDER_WINDOW, SemanticColor.SURFACE_PANEL, SurfaceContrast.Guard.STRUCTURAL),
            text(SemanticColor.TEXT_SECONDARY, surface), text(SemanticColor.TEXT_PRIMARY, surface));
    }

    static boolean titled(PropertyLists.PropertiesControl control) { return !((Nodes.Properties) control.node()).title().isBlank(); }

    @Override public JComponent container(PropertyLists.PropertiesControl control) { return new PropertyLists.GridSurface(control); }

    @Override public String labelText(Nodes.Properties node, String label) { return node.title().isBlank() ? label + ":" : label; }

    private int metric(PropertyLists.PropertiesControl control, DesignMetrics.Role role) { return ThemePainting.metric(control.context(), role); }

    @Override public void style(PropertyLists.PropertiesControl control) {
        var context = control.context(); var properties = (Nodes.Properties) control.node();
        boolean grid = titled(control);
        ColorRole labelGround = grid ? SemanticColor.SURFACE_PANEL : surface, valueGround = grid ? SemanticColor.SURFACE_CARD : surface;
        for (int i = 0; i < control.labels.size(); i++) {
            var row = properties.rows().get(i); var label = control.labels.get(i); var value = control.values.get(i);
            label.setFont(ThemePainting.font(context, Typography.Role.BODY));
            label.setForeground(ThemePainting.painted(context, text(SemanticColor.TEXT_SECONDARY, labelGround)));
            value.setFont(ThemePainting.font(context, row.technical() ? Typography.Role.MONO : Typography.Role.BODY, row.value()));
            value.setForeground(row.severity().map(severity -> ThemePainting.painted(context, new PaintPair(Labels.tint(severity), valueGround, SurfaceContrast.Guard.TEXT)))
                .orElseGet(() -> ThemePainting.painted(context, text(SemanticColor.TEXT_PRIMARY, valueGround))));
            label.setOpaque(false); value.setOpaque(false);
        }
        var title = control.title;
        title.setFont(ThemePainting.font(context, Typography.Role.BODY_STRONG));
        title.setForeground(ThemePainting.painted(context, text(SemanticColor.TEXT_STRONG_SECONDARY, SemanticColor.SURFACE_PANEL)));
        int row = metric(control, DesignMetrics.Role.ROW_HEIGHT), hairline = metric(control, DesignMetrics.Role.HAIRLINE), gap = metric(control, DesignMetrics.Role.ICON_GAP);
        int text = title.getFontMetrics(title.getFont()).getHeight(), above = Math.max(0, (row - text) / 2), below = Math.max(0, row - text - above);
        title.setOpaque(grid);
        title.setBackground(ThemePainting.role(context, SemanticColor.SURFACE_PANEL));
        title.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(hairline, 0, 0, 0, ThemePainting.role(context, SemanticColor.BORDER_WINDOW)),
            BorderFactory.createMatteBorder(0, 0, hairline, 0, ThemePainting.role(context, SemanticColor.BORDER_SUBTLE))),
            new EmptyBorder(above, hairline + gap, below, gap)));
        control.rows.setBorder(new EmptyBorder(0, 0, 0, 0));
        control.component().setLayout(new Composition.Vertical(grid ? 0 : metric(control, DesignMetrics.Role.SECTION_LABEL_GAP)));
    }

    /** The label column's width: at least the role, and wide enough for the widest label with its padding. */
    int labelColumn(PropertyLists.PropertiesControl control) {
        int widest = control.labels.stream().mapToInt(label -> label.getPreferredSize().width).max().orElse(0);
        int gap = metric(control, DesignMetrics.Role.ICON_GAP);
        return titled(control) ? Math.max(metric(control, DesignMetrics.Role.PROPERTY_LABEL_WIDTH), widest + 2 * gap + metric(control, DesignMetrics.Role.HAIRLINE))
            : Math.max(metric(control, DesignMetrics.Role.PROPERTY_PLAIN_LABEL_WIDTH), widest + gap);
    }

    @Override public Composition.WidthLayout grid(PropertyLists.PropertiesControl control) {
        return new Composition.WidthLayout() {
            int rowHeight(int index) {
                if (titled(control)) return metric(control, DesignMetrics.Role.ROW_HEIGHT);
                return Math.max(control.labels.get(index).getPreferredSize().height, control.values.get(index).getPreferredSize().height);
            }
            int gap() { return titled(control) ? metric(control, DesignMetrics.Role.HAIRLINE) : metric(control, DesignMetrics.Role.PROPERTY_PLAIN_ROW_GAP); }
            @Override public int height(Container parent, int width) {
                int height = 0, count = control.labels.size();
                for (int i = 0; i < count; i++) height += rowHeight(i) + (titled(control) || i < count - 1 ? gap() : 0);
                return height;
            }
            @Override public Dimension preferredLayoutSize(Container parent) {
                int values = control.values.stream().mapToInt(value -> value.getPreferredSize().width).max().orElse(0);
                int width = labelColumn(control) + metric(control, DesignMetrics.Role.HAIRLINE) + 2 * metric(control, DesignMetrics.Role.ICON_GAP) + values;
                return new Dimension(width, height(parent, width));
            }
            @Override public void layoutContainer(Container parent) {
                boolean grid = titled(control);
                int column = labelColumn(control), hairline = metric(control, DesignMetrics.Role.HAIRLINE), gap = metric(control, DesignMetrics.Role.ICON_GAP);
                int labelX = grid ? hairline + gap : 0, valueX = grid ? column + hairline + gap : column, y = 0;
                for (int i = 0; i < control.labels.size(); i++) {
                    int height = rowHeight(i);
                    control.labels.get(i).setBounds(labelX, y, Math.max(0, column - labelX - (grid ? gap : 0)), height);
                    control.values.get(i).setBounds(valueX, y, Math.max(0, parent.getWidth() - valueX - (grid ? gap : 0)), height);
                    y += height + gap();
                }
            }
        };
    }

    @Override public void paint(PropertyLists.PropertiesControl control, Graphics2D g, int width, int height) {
        if (!titled(control)) return;
        var context = control.context();
        int column = labelColumn(control), hairline = metric(control, DesignMetrics.Role.HAIRLINE), stride = metric(control, DesignMetrics.Role.ROW_HEIGHT) + hairline;
        g.setColor(ThemePainting.role(context, SemanticColor.SURFACE_PANEL)); g.fillRect(0, 0, column, height);
        g.setColor(ThemePainting.role(context, SemanticColor.SURFACE_CARD)); g.fillRect(column + hairline, 0, Math.max(0, width - column - hairline), height);
        g.setColor(ThemePainting.painted(context, new PaintPair(DerivedColor.BORDER_ROW, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.STRUCTURAL)));
        g.fillRect(column, 0, hairline, height);
        for (int i = 1; i <= control.labels.size(); i++) g.fillRect(0, i * stride - hairline, width, hairline);
    }
}
```

`Labels.tint(Severity)` is the colour role 3b's `valuePair` uses; `Composition.WidthLayout`, `Composition.Vertical` and
`Composition.style` are the jasper-ui layout helpers 3b's `Grid` uses.

Replace `jasper-ui/src/main/java/dev/jasper/ui/swing/PropertyLists.java` with (3b's file with the face hooks; the default
face's methods hold 3b's styling and layout unchanged):

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/** SDK 0.18's Properties (spec 8.2): a heading over its rows, drawn by the kit's {@link PropertiesFace}. */
final class PropertyLists {
    private PropertyLists() {}

    /** A value coloured by its severity on the card, held to the text floor. */
    static PaintPair valuePair(Nodes.Severity severity) {
        return new PaintPair(Labels.tint(severity), SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
    }

    static final class PropertiesControl extends ControlRegistry.Control {
        final JLabel title = Labels.literal();
        final PropertiesFace face;
        final JComponent rows;
        final ArrayList<JLabel> labels = new ArrayList<>(), values = new ArrayList<>();

        PropertiesControl(KitView view, Nodes.Properties node) {
            super(view, node, new JPanel());
            component.setOpaque(false);
            face = view.context().faces().properties;
            rows = face.container(this);
            KitOwnership.mark(title, node.id() + ".title");
            KitOwnership.mark(rows, node.id() + ".rows");
            rows.setLayout(face.grid(this));
            Stacks.mount(component, List.<JComponent>of(title, rows));
            refresh();
        }

        @Override void refresh() {
            state();
            var properties = (Nodes.Properties) node;
            int count = properties.rows().size();
            while (labels.size() > count) { rows.remove(labels.removeLast()); rows.remove(values.removeLast()); }
            while (labels.size() < count) {
                int index = labels.size();
                var label = Labels.literal(); var value = Labels.literal();
                KitOwnership.mark(label, node.id() + ".label." + index);
                KitOwnership.mark(value, node.id() + ".value." + index);
                label.setLabelFor(value);
                labels.add(label); values.add(value); rows.add(label); rows.add(value);
            }
            for (int i = 0; i < count; i++) {
                var row = properties.rows().get(i); var label = labels.get(i); var value = values.get(i);
                label.setText(face.labelText(properties, row.label()));
                value.setText(row.value());
                value.setToolTipText(row.value().isEmpty() ? null : row.value());
                // A data value keeps its end in view when it is shortened, as monospaced text does.
                value.putClientProperty("Jasper.leadingEllipsis", row.technical());
                label.setEnabled(component.isEnabled()); value.setEnabled(component.isEnabled());
            }
            title.setText(properties.title());
            title.setVisible(!properties.title().isBlank());
            title.setEnabled(component.isEnabled());
            face.style(this);
            component.revalidate(); component.repaint();
        }

        Nodes.Node node() { return node; }
        JComponent component() { return component; }
    }

    /** The classic rows' container: the face paints its bands and rules under the labels and values. */
    static final class GridSurface extends JPanel {
        final PropertiesControl owner;
        GridSurface(PropertiesControl owner) { this.owner = owner; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            var graphics = (Graphics2D) g.create();
            try { owner.face.paint(owner, graphics, getWidth(), getHeight()); } finally { graphics.dispose(); }
        }
    }

    /** A label column as wide as the widest label, then one value column; one line per row (the default face). */
    static final class Grid implements Composition.WidthLayout {
        final PropertiesControl owner;

        Grid(PropertiesControl owner) { this.owner = owner; }

        private int labelWidth() { return owner.labels.stream().mapToInt(label -> label.getPreferredSize().width).max().orElse(0); }
        private int columnGap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.COLUMN_GAP); }
        private int rowGap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.ROW_GAP); }
        private int rowHeight(int index) {
            return Math.max(owner.labels.get(index).getPreferredSize().height, owner.values.get(index).getPreferredSize().height);
        }

        @Override public int height(Container parent, int width) {
            Insets insets = parent.getInsets();
            int height = insets.top + insets.bottom;
            for (int i = 0; i < owner.labels.size(); i++) height += rowHeight(i) + (i == 0 ? 0 : rowGap());
            return height;
        }

        @Override public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            int values = owner.values.stream().mapToInt(value -> value.getPreferredSize().width).max().orElse(0);
            int width = insets.left + insets.right + labelWidth() + columnGap() + values;
            return new Dimension(width, height(parent, width));
        }

        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int labelWidth = labelWidth(), x = insets.left + labelWidth + columnGap(), y = insets.top;
            int valueWidth = Math.max(0, parent.getWidth() - insets.right - x);
            for (int i = 0; i < owner.labels.size(); i++) {
                int height = rowHeight(i);
                owner.labels.get(i).setBounds(insets.left, y, labelWidth, height);
                owner.values.get(i).setBounds(x, y, valueWidth, height);
                y += height + rowGap();
            }
        }
    }
}
```

Before replacing it, diff the branch's `PropertyLists.java` with 3b's text quoted in 3b Task 7 Step 3: if 3b's review or a
later plan changed it, carry those changes into the default face and the control above (the default face must paint
what the file paints now). Checked on 2026-10-17: the only later change is 5b's final review (`911699932`), which picks a
value's font for its text (`ThemePainting.font(context, role, row.value())`); both faces above carry it (Execution notes
R8). `context()` is `ControlRegistry.Control`'s final accessor (`ControlRegistry.java:95`); `node` and `component` are its
fields, which the two one-line accessors expose to the faces.

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 10a Task 3 step 3: the properties face in KitFaces and the three roles in both catalogs."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "KitFaces.java", [
    ('''    final TableFace table;\n''', '''    final TableFace table;\n    final PropertiesFace properties;\n''', 1),
    ('''        table = switch (controls.table()) {''', '''        properties = switch (controls.table()) {
            case PLAIN -> new DefaultPropertiesFace();
            case CLASSIC -> new ClassicPropertiesFace(colors.dialogSurface());
        };
        table = switch (controls.table()) {''', 1)])
path = pathlib.Path(S + "KitFaces.java"); text = path.read_text(encoding="utf-8")
all_line = re.search(r"List<Face> all\(\) \{ return List\.of\(([^)]*)\); \}", text)
if not all_line or ", table," not in all_line.group(1): raise SystemExit("KitFaces.all() not found or has no table face")
path.write_text(text.replace(all_line.group(0), all_line.group(0).replace(", table,", ", table, properties,")), encoding="utf-8")
edit("jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java", [
    ('''FORM_LABEL_WIDTH(96), ''', '''FORM_LABEL_WIDTH(96), PROPERTY_LABEL_WIDTH(89), PROPERTY_PLAIN_LABEL_WIDTH(80), PROPERTY_PLAIN_ROW_GAP(2), ''', 1)])
R = "jasper-ui/src/test/resources/dev/jasper/ui/metrics/"
edit(R + "compact-roles.tsv", [('''FORM_LABEL_WIDTH\t96\n''', '''FORM_LABEL_WIDTH\t96\nPROPERTY_LABEL_WIDTH\t89\nPROPERTY_PLAIN_LABEL_WIDTH\t80\nPROPERTY_PLAIN_ROW_GAP\t2\n''', 1)])
edit(R + "classic-roles.tsv", [('''FORM_LABEL_WIDTH\t100\n''', '''FORM_LABEL_WIDTH\t100\nPROPERTY_LABEL_WIDTH\t89\nPROPERTY_PLAIN_LABEL_WIDTH\t80\nPROPERTY_PLAIN_ROW_GAP\t2\n''', 1)])
PY
```

The three roles are equal in both catalogs (as 4c's `GROUP_ROW_INDENT`), so `DesignMetrics.classic()` needs no entry. If
4a wrote `classic-roles.tsv` in another order than the enum's, insert the three rows where its test expects them.

- [ ] **Step 4: Run the tests, freeze the pairs and prove the gallery**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicPropertiesTest' --tests 'dev.jasper.ui.swing.PropertiesTest' --tests 'dev.jasper.ui.metrics.*' --tests 'dev.jasper.ui.swing.PaintSpecTest' --tests 'dev.jasper.ui.swing.StrategyIsolationTest' --tests 'dev.jasper.ui.swing.VariantReadTest' --continue`
Expected: PASS except `PaintSpecTest`, which FAILS listing the seven `ClassicPropertiesFace` rows missing from
`face-pairs.tsv`. Add exactly those rows, in the printed form, after the `ClassicTableFace` rows (the face's place in
`all()`), and run the same command: PASS. `ClassicPropertiesTest` (4), 3b's `PropertiesTest` unchanged,
`DesignMetricsTest` and the classic catalog test with the three rows, the isolation and variant-read tests (the faces read
no `Kit`).

If a colour assertion of `aTitledListIsTheWorkstationGrid` fails, compare the face's role with the Workstation mock
("Mock measurements": the title rule `#C4C7CC`, the title band and label column `#F7F7F8`, the rule under the title
`#DDE0E4`, the row and column rules `#E6E8EB`, the value cell `#FFFFFF`): pick the semantic or derived role whose Enterprise
Light value is the mock's and change it in the face and the test together; record the change in the banner.

Then prove the default look unchanged:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
python3 -I - <<'PY'
import hashlib, pathlib, sys
def digests(root): return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.rglob("*")) if p.is_file()}
bad = []
for theme in ("dark-1x", "light-1x"):
    base, now = digests(pathlib.Path("jasper-ui/build/plan10-base") / theme), digests(pathlib.Path("jasper-ui/build/gallery") / theme)
    bad += [f"{theme}/{name}" for name in sorted(set(base) | set(now)) if base.get(name) != now.get(name)]
print("changed:", bad if bad else "none"); sys.exit(1 if bad else 0)
PY
```

Expected: `changed: none`.

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src
git commit -F - <<'EOF'
Plugin compositions: classic Properties as the Workstation grid

Properties follow the kit's table point. The default face is plan 3b's
card, unchanged; the classic face draws a titled list as the Workstation
mock's grid (a title band, a label column on surface.panel, values on
surface.card, border.row rules) and an untitled one as label-colon lines.
Three roles, equal in both catalogs, carry the mock's widths; the face's
pairs are frozen and the default gallery is byte-identical.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: The Sessions tree, its columns and Properties

Spec 14.4 "Sessions": under CLASSIC the Sessions panel is a Tree (Name, Host and Port columns) of the same rows: group
branches, hosts with a status dot, "★" after favourites, distributions; the filter reads "Filter sessions"; below it the
selected host's Properties ("Properties — <host>": Host, User and Port in the data face, Credentials "Vault: <name>" or a
WARNING "Vault locked") over an ACTIONS row Edit… and Connect; the header detail is the count. Rows layout unchanged.

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/HostsPanel.java`, `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`
  (`createPanel`), `jasper-app/src/test/java/dev/jasper/app/plugins/PanelSurfaceFixture.java` (`hosts` favourites and Vault
  credentials, `selectSession`)
- Create: `plugins/remote/src/test/java/dev/jasper/remote/ui/SessionsTreeTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicCompositionRenderTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (the two new test classes)

**Interfaces:**
- Consumes: Task 1's `Layout`; Task 2's `PanelSurfaceFixture(…, true)`, `render`, `MockMatch`; Task 3's classic Properties;
  3c's `Tree.columns`; 3b's `Properties`.
- Produces: `public HostsPanel(UiKit, Actions, Layout)` and package-private `HostsPanel(UiKit, Actions, LongSupplier, long,
  Layout)`; `static final List<Column> HostsPanel.COLUMNS`; ids `HostsPanel.PROPERTIES = "sessions.properties"`,
  `PROPERTY_ACTIONS = "sessions.properties.actions"`; the Tree keeps the list's id `sessions.rows` and its row ids;
  `public void HostsPanel.selectRow(String rowId)`, `public void onSelectionChanged(Consumer<Optional<String>>)`;
  `PanelSurfaceFixture.selectSession(UUID)`; `ClassicCompositionRenderTest.check(String, MockMatch.Crop, BufferedImage, int,
  String, int, double, String...)`.

- [ ] **Step 1: Write the failing tests**

Create `plugins/remote/src/test/java/dev/jasper/remote/ui/SessionsTreeTest.java`:

```java
package dev.jasper.remote.ui;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PanelHeader;
import dev.jasper.sdk.ui.TreeItem;
import dev.jasper.sdk.ui.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 14.4: the classic Sessions panel is a column tree over Properties, with the rows' gestures. */
class SessionsTreeTest {
    final List<String> events = new ArrayList<>();
    final HostsPanel.Actions actions = new HostsPanel.Actions(h -> events.add("connect " + h.name()), h -> events.add("split " + h.name()),
        h -> events.add("edit " + h.map(RemoteHost::name).orElse("new")), h -> events.add("duplicate " + h.name()), h -> events.add("delete " + h.name()),
        (h, favorite) -> events.add("favorite " + h.name() + " " + favorite), () -> events.add("import"));
    final RemoteHost mac = RemoteHost.create("Mac", "192.168.1.119", 22, "dustin", Auth.AGENT, "Local Network", Optional.empty());
    final RemoteHost minecraft = RemoteHost.create("minecraft-local", "192.168.1.124", 22, "dustin", new Auth.Vault(UUID.randomUUID()),
        "Local Network", Optional.empty()).withFavorite(true);

    HostsPanel panel(Layout layout) {
        var panel = new HostsPanel(new FakeKit("dev.jasper.remote"), actions, () -> 0, 500_000_000L, layout);
        panel.setHosts(List.of(mac, minecraft), Optional.empty());
        return panel;
    }

    static Nodes.Tree tree(HostsPanel panel) { return node(panel.view(), HostsPanel.ROWS, Nodes.Tree.class); }

    @Test void theClassicPanelIsAColumnTreeOfTheSameRows() throws Exception {
        edt(() -> {
            var panel = panel(Layout.CLASSIC);
            var tree = tree(panel);
            assertThat(tree.columns()).extracting(column -> column.title()).containsExactly("Name", "Host", "Port");
            assertThat(tree.roots()).singleElement().satisfies(group -> {
                assertThat(group.id()).isEqualTo("group:Local Network");
                assertThat(group.leaf()).isFalse();
                assertThat(group.children()).extracting(TreeItem::title).containsExactly("Mac", "minecraft-local ★");
                assertThat(group.children().getFirst().trailing()).containsExactly("192.168.1.119", "22");
                assertThat(group.children().getFirst().status()).contains(Nodes.StatusKind.IDLE);
            });
            assertThat(tree.expandedIds()).containsExactly("group:Local Network");
            assertThat(node(panel.view(), HostsPanel.SEARCH, Nodes.Field.class).hint()).isEqualTo("Filter sessions");
            assertThat(find(panel.view(), HostsPanel.CARD)).as("no card under CLASSIC").isEmpty();
        });
    }

    @Test void theSelectedHostsPropertiesAndActionsFollowTheSelection() throws Exception {
        edt(() -> {
            var panel = panel(Layout.CLASSIC);
            panel.setCredentialLabels(host -> host == minecraft ? "minecraft-local" : "SSH agent");
            panel.select(minecraft.id());
            var properties = node(panel.view(), HostsPanel.PROPERTIES, Nodes.Properties.class);
            assertThat(properties.title()).isEqualTo("Properties — minecraft-local");
            assertThat(properties.rows()).extracting(row -> row.label() + "=" + row.value() + (row.technical() ? " (data)" : ""))
                .containsExactly("Host=192.168.1.124 (data)", "User=dustin (data)", "Port=22 (data)", "Credentials=Vault: minecraft-local");
            assertThat(node(panel.view(), HostsPanel.EDIT, Nodes.Button.class).label()).isEqualTo("Edit…");
            assertThat(node(panel.view(), HostsPanel.CONNECT, Nodes.Button.class).label()).isEqualTo("Connect");
            press(panel.view(), HostsPanel.CONNECT);
            press(panel.view(), HostsPanel.EDIT);
            assertThat(events).containsExactly("connect minecraft-local", "edit minecraft-local");
            panel.setCredentialLabels(host -> "Vault locked");
            assertThat(node(panel.view(), HostsPanel.PROPERTIES, Nodes.Properties.class).rows().getLast().severity()).contains(Nodes.Severity.WARNING);
            panel.setHosts(List.of(mac), Optional.empty());
            assertThat(node(panel.view(), HostsPanel.PROPERTIES, Nodes.Properties.class).title()).as("the deleted host's Properties go")
                .isEqualTo("Properties — Mac");
        });
    }

    @Test void expandingCollapsesTheGroupAndActivationConnects() throws Exception {
        edt(() -> {
            var panel = panel(Layout.CLASSIC);
            var selections = new ArrayList<Optional<String>>();
            panel.onSelectionChanged(selections::add);
            emit(panel.view(), new UiEvent.Expanded(HostsPanel.ROWS, "group:Local Network", false));
            assertThat(tree(panel).expandedIds()).isEmpty();
            assertThat(panel.collapsed()).containsExactly("Local Network");
            emit(panel.view(), new UiEvent.Expanded(HostsPanel.ROWS, "group:Local Network", true));
            emit(panel.view(), new UiEvent.Selected(HostsPanel.ROWS, Set.of(HostsPanel.rowId(new HostRows.Host(mac)))));
            emit(panel.view(), new UiEvent.Clicked(HostsPanel.ROWS, "group:Local Network"));
            assertThat(tree(panel).expandedIds()).as("a click selects; only the disclosure collapses").containsExactly("group:Local Network");
            press(panel.view(), tree(panel).activateAction());
            assertThat(events).containsExactly("connect Mac");
            assertThat(selections).contains(Optional.of("host:" + mac.id()));
        });
    }

    @Test void theHeaderDetailIsTheCountUnderClassicOnly() throws Exception {
        edt(() -> {
            var classic = new ArrayList<PanelHeader>(); var rows = new ArrayList<PanelHeader>();
            panel(Layout.CLASSIC).onHeaderChanged(classic::add);
            panel(Layout.ROWS).onHeaderChanged(rows::add);
            assertThat(classic.getLast().detail()).isEqualTo("2");
            assertThat(rows.getLast().detail()).isEmpty();
        });
    }

    @Test void theRowsPanelIsUnchanged() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            assertThat(node(panel.view(), HostsPanel.ROWS, Nodes.ItemList.class).items()).hasSize(3);
            assertThat(find(panel.view(), HostsPanel.PROPERTIES)).isEmpty();
        });
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicCompositionRenderTest.java` with its first surface:

```java
package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.app.testsupport.MockMatch;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Plan 10a's mock matching (spec 16.1.4): the staged plugins' classic compositions in the real host, under the shipped
 * Enterprise kit, Plex fonts and theme, against the Workstation, Dark Mode and dialog mocks. Images go to
 * jasper-app/build/enterprise-compositions/ (light/ and dark/), paired in tools/visual/pairs.json.
 */
class ClassicCompositionRenderTest {
    static final String CONNECTED = "01 Workstation/Workstation - Connected (plugins in use).png",
        DISCONNECTED = "01 Workstation/Workstation - Disconnected.png", DARK = "08 Dark Mode/Workstation Dark - Connected (plugins in use).png";
    @TempDir Path root;

    /**
     * Saves the pair, then checks the render: its size, its bands down {@code sampleX}, its colours and its edge agreement,
     * which follows the UI face's glyphs and is asserted under the shipped IBM Plex ({@link MockCrops#fontsFloor}).
     */
    static void check(String name, MockMatch.Crop crop, BufferedImage render, int sampleX, String bands, int tolerance, double floor, String... colours) {
        var mock = crop.read();
        MockMatch.save(name, mock, render);
        assertThat(render.getWidth()).as(name + " width").isEqualTo(crop.width());
        assertThat(render.getHeight()).as(name + " height").isEqualTo(crop.height());
        assertThat(MockMatch.mismatch(MockMatch.parse(bands), MockMatch.bands(render, sampleX), tolerance)).as(name + " bands at x " + sampleX).isEmpty();
        for (String colour : colours) assertThat(MockMatch.present(render, Color.decode(colour))).as(name + " shows " + colour).isTrue();
        MockCrops.fontsFloor(name + " edge agreement", MockMatch.agreement(mock, render), floor);
    }

    /** Runs {@code body} under the installed Enterprise look in {@code dark}, then restores the default look. */
    static void under(boolean dark, ThrowingRunnable body) throws Exception {
        var controller = onEdtValue(() -> EnterpriseLook.controller(dark));
        try { body.run(); } finally { onEdt(() -> { controller.close(); EnterpriseLook.restoreDefault(); }); }
    }

    interface ThrowingRunnable { void run() throws Exception; }

    static final String SESSIONS_LIGHT = "0+36 #F7F7F8, 36+1 #DDE0E4, 37+20 #F0F1F3, 57+1 #D3D6DA, 58+44 #FFFFFF, 102+22 #2F65CA, "
        + "124+111 #FFFFFF, 235+1 #C4C7CC, 236+22 #F7F7F8, 258+1 #DDE0E4, 259+22 #F7F7F8, 281+1 #E6E8EB, 282+22 #F7F7F8, 304+1 #E6E8EB, "
        + "305+22 #F7F7F8, 327+1 #E6E8EB, 328+22 #F7F7F8, 350+1 #E6E8EB, 351+40 #F7F7F8";
    static final String SESSIONS_DARK = "0+36 #35383D, 36+1 #45484E, 37+20 #393C41, 57+1 #4A4D53, 58+44 #2E3135, 102+22 #3D74D6, "
        + "124+111 #2E3135, 235+1 #2A2C30, 236+22 #35383D, 258+1 #45484E, 259+22 #35383D, 281+1 #3F4247, 282+22 #35383D, 304+1 #3F4247, "
        + "305+22 #35383D, 327+1 #3F4247, 328+22 #35383D, 350+1 #3F4247, 351+40 #35383D";
    /** The Disconnected mock stripes its second host (#F7F8FA); classic trees are not striped (Rulings 14), so the render reads white. */
    static final String SESSIONS_DISCONNECTED = "0+36 #F7F7F8, 36+1 #DDE0E4, 37+20 #F0F1F3, 57+1 #D3D6DA, 58+22 #FFFFFF, 80+22 #2F65CA, "
        + "102+133 #FFFFFF, 235+1 #C4C7CC, 236+22 #F7F7F8, 258+1 #DDE0E4, 259+22 #F7F7F8, 281+1 #E6E8EB, 282+22 #F7F7F8, 304+1 #E6E8EB, "
        + "305+22 #F7F7F8, 327+1 #E6E8EB, 328+22 #F7F7F8, 350+1 #E6E8EB, 351+40 #F7F7F8";

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theSessionsTreeAndPropertiesMatchTheWorkstationMocks() throws Exception {
        for (boolean dark : new boolean[] {false, true}) under(dark, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("sessions-" + dark), EnterpriseLook.resolved(dark), PanelSurfaceFixture.SESSIONS, true)) {
                var ids = fixture.hosts(new String[][] {{"Mac", "192.168.1.119", "dustin", "Local Network"},
                    {"minecraft-local", "192.168.1.124", "dustin", "Local Network", "favorite"}});
                fixture.selectSession(ids.get(1));
                var crop = new MockMatch.Crop(dark ? DARK : CONNECTED, 2136, 230, 700, 782);
                check((dark ? "dark" : "light") + "/sessions", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 4,
                    dark ? SESSIONS_DARK : SESSIONS_LIGHT, 1, 0.55,
                    dark ? new String[] {"#3D74D6", "#393C41", "#35383D"} : new String[] {"#2F65CA", "#F0F1F3", "#F7F7F8", "#E6E8EB"});
            }
        });
        under(false, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("sessions-locked"), EnterpriseLook.resolved(false), PanelSurfaceFixture.SESSIONS, true)) {
                var ids = fixture.hosts(new String[][] {{"Mac", "192.168.1.119", "dustin", "Local Network", "favorite", "vault"},
                    {"minecraft-local", "192.168.1.124", "dustin", "Local Network"}});
                fixture.selectSession(ids.get(0));
                var crop = new MockMatch.Crop(DISCONNECTED, 2136, 230, 700, 782);
                check("light/sessions-locked", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 4, SESSIONS_DISCONNECTED, 1, 0.55,
                    "#2F65CA", "#8A5A00");
            }
        });
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:compileTestJava :jasper-app:compileTestJava --continue`
Expected: FAIL to compile: "no suitable constructor found for HostsPanel(FakeKit,Actions,()->0,long,Layout)", "cannot find
symbol … PROPERTIES", "… onSelectionChanged", "… selectSession".

- [ ] **Step 3: Compose the classic Sessions panel**

```bash
python3 -I - <<'PY'
"""Plan 10a Task 4 step 3: the classic Sessions composition."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
H = "plugins/remote/src/main/java/dev/jasper/remote/ui/HostsPanel.java"
CLASSIC = '''    // ---- the classic composition (Enterprise presentation spec 14.4)

    /** The classic tree's columns: the title, then a host's address and port (its trailing values). */
    static final List<Column> COLUMNS = List.of(new Column("name", "Name", Nodes.TextRole.BODY, false, 3, Optional.empty()),
        new Column("host", "Host", Nodes.TextRole.MONO, false, 2.3, Optional.empty()), new Column("port", "Port", Nodes.TextRole.MONO, true, 1, Optional.empty()));
    public static final String PROPERTIES = "sessions.properties", PROPERTY_ACTIONS = "sessions.properties.actions";

    /** Selects a row by its id ({@link #rowId}), as the window's toolbar view does; an unknown row changes nothing. */
    public void selectRow(String rowId) {
        if (closed || rowId.equals(selectedRow) || rows.stream().noneMatch(row -> rowId(row).equals(rowId))) return;
        selectedRow = rowId; render();
    }

    /** Hears every selection the user makes in the panel, as the row id, or empty for none. */
    public void onSelectionChanged(Consumer<Optional<String>> listener) { selectionChanged = Objects.requireNonNull(listener); }

    private boolean collapsedGroup(HostRows.Group group) { return group.wsl() ? wslCollapsed : collapsed.contains(group.name()); }

    private Nodes.Panel classicNode() {
        var header = new Nodes.Header(HEADER, ControlState.READY, "Sessions", OptionalInt.of(hosts.size() + distros.size()), Optional.empty(), Optional.empty(), List.of());
        var filter = new Nodes.Field(SEARCH, ControlState.READY, Nodes.FieldKind.SEARCH, query, "Filter sessions", "Filter sessions", "");
        boolean searching = !query.strip().isEmpty();
        var roots = new ArrayList<TreeItem>();
        var expanded = new HashSet<String>();
        HostRows.Group group = null;
        var children = new ArrayList<TreeItem>();
        for (HostRows.Row row : rows) {
            if (row instanceof HostRows.Group next) {
                if (group != null) roots.add(groupItem(group, children));
                group = next; children = new ArrayList<>();
                if (searching || !collapsedGroup(next)) expanded.add(rowId(next));
            } else if (group != null && !(row instanceof HostRows.Error)) children.add(treeItem(row));
            else roots.add(treeItem(row));
        }
        if (group != null) roots.add(groupItem(group, children));
        var tree = new Nodes.Tree(ROWS, ControlState.READY, roots, expanded, selectedRow == null ? Set.of() : Set.of(selectedRow), ACTIVATE, COLUMNS);
        var footer = new ArrayList<Nodes.Node>();
        if (hosts.isEmpty() || (searching && rows.size() == (error.isPresent() ? 1 : 0))) {
            String hint = hosts.isEmpty() ? (distros.isEmpty() ? "No hosts yet" : "No SSH hosts yet") + " — Add or Import from ~/.ssh/config"
                : distros.isEmpty() ? "No hosts match" : "No sessions match";
            footer.add(new Nodes.Text(HINT, ControlState.READY, hint, Nodes.TextRole.DESCRIPTION));
        }
        footer.addAll(classicDetails());
        Optional<Nodes.Node> bottom = footer.isEmpty() ? Optional.empty()
            : Optional.of(new Nodes.Stack(FOOTER, ControlState.READY, Nodes.LayoutRole.VERTICAL, footer));
        return new Nodes.Panel(ROOT, ControlState.READY, header, Optional.of(filter), tree, bottom);
    }

    private TreeItem groupItem(HostRows.Group group, List<TreeItem> children) {
        String id = rowId(group);
        boolean renamable = !group.wsl() && group.name().equals(defaultGroup);
        return new TreeItem(id, group.name(), Optional.empty(), false, children, OptionalInt.empty(), false,
            renamable ? "Right-click to rename this group" : "", Optional.empty(), List.of(),
            renamable ? Optional.of(List.of(menu("rename", id, "Rename group…"))) : Optional.empty());
    }

    private TreeItem treeItem(HostRows.Row row) {
        String id = rowId(row);
        return switch (row) {
            case HostRows.Error failure -> new TreeItem(id, failure.message(), Optional.empty(), true, List.of(), OptionalInt.empty(), true,
                failure.message(), Optional.of(Nodes.StatusKind.DANGER), List.of(), Optional.of(List.of()));
            case HostRows.Group group -> groupItem(group, List.of());
            case HostRows.Distro distro -> {
                int sessions = distro.sessions();
                String badge = distro.profile().detail().orElse(HostRows.WSL);
                yield new TreeItem(id, distro.profile().name(), Optional.empty(), true, List.of(), OptionalInt.empty(), false,
                    distro.profile().name() + ", " + badge + ", " + sessions + (sessions == 1 ? " session" : " sessions"),
                    Optional.of(sessions > 0 ? Nodes.StatusKind.SUCCESS : Nodes.StatusKind.IDLE), List.of(badge),
                    Optional.of(List.of(menu("open", id, "Open in new tab"), menu("open-split", id, "Open in split"))));
            }
            case HostRows.Host entry -> {
                RemoteHost host = entry.host();
                int sessions = sessionCount.applyAsInt(host);
                String facts = metadata.apply(host), via = via(host);
                yield new TreeItem(id, host.favorite() ? host.name() + " ★" : host.name(), Optional.empty(), true, List.of(), OptionalInt.empty(), false,
                    host.name() + ", " + host.label() + (facts.isBlank() ? "" : ", " + facts) + (via == null ? "" : ", via " + via)
                        + ", " + sessions + (sessions == 1 ? " session" : " sessions"),
                    Optional.of(sessions > 0 ? Nodes.StatusKind.SUCCESS : Nodes.StatusKind.IDLE), List.of(host.hostname(), String.valueOf(host.port())),
                    Optional.of(hostMenu(id, host)));
            }
        };
    }

    /** The selected host's or distribution's Properties and actions; nothing while no session is selected. */
    private List<Nodes.Node> classicDetails() {
        Optional<RemoteHost> host = selectedHost();
        if (host.isPresent()) {
            RemoteHost h = host.get();
            String credential = credentialLabels.apply(h);
            boolean locked = credential.equals("Vault locked");
            boolean named = h.auth() instanceof Auth.Vault && !locked && !credential.equals("needs Credential Vault") && !credential.equals("credential missing");
            boolean connected = sessionCount.applyAsInt(h) > 0;
            return List.of(new Nodes.Properties(PROPERTIES, ControlState.READY, "Properties — " + h.name(), List.of(
                    new Nodes.PropertyRow("Host", h.hostname(), true), new Nodes.PropertyRow("User", h.username(), true),
                    new Nodes.PropertyRow("Port", String.valueOf(h.port()), true),
                    new Nodes.PropertyRow("Credentials", named ? "Vault: " + credential : credential, false,
                        locked ? Optional.of(Nodes.Severity.WARNING) : Optional.empty()))),
                new Nodes.Stack(PROPERTY_ACTIONS, ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
                    new Nodes.Button(EDIT, ControlState.READY, "Edit…", "", Nodes.ButtonKind.SECONDARY, EDIT, "Edit session"),
                    new Nodes.Button(CONNECT, ControlState.READY, connected ? "Open Shell" : "Connect", "", Nodes.ButtonKind.PRIMARY, CONNECT, ""))));
        }
        Optional<ShellProfileInfo> distro = selectedDistro();
        if (distro.isEmpty()) return List.of();
        var profile = distro.get();
        return List.of(new Nodes.Properties(PROPERTIES, ControlState.READY, "Properties — " + profile.name(), List.of(
                new Nodes.PropertyRow("Distribution", profile.name(), false), new Nodes.PropertyRow("Version", profile.detail().orElse(HostRows.WSL), false),
                new Nodes.PropertyRow("Command", "wsl.exe -d " + profile.name(), true))),
            new Nodes.Stack(PROPERTY_ACTIONS, ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
                new Nodes.Button(CONNECT, ControlState.READY, "Open", "", Nodes.ButtonKind.PRIMARY, CONNECT, ""))));
    }

    @Override public void close() {'''
edit(H, [
    ('''import dev.jasper.remote.hosts.RemoteHost;\n''', '''import dev.jasper.remote.hosts.Auth;\nimport dev.jasper.remote.hosts.RemoteHost;\nimport dev.jasper.remote.workstation.Layout;\n''', 1),
    ('''import dev.jasper.sdk.ui.ControlState;\n''', '''import dev.jasper.sdk.ui.Column;\nimport dev.jasper.sdk.ui.ControlState;\n''', 1),
    ('''import dev.jasper.sdk.ui.UiEvent;\n''', '''import dev.jasper.sdk.ui.TreeItem;\nimport dev.jasper.sdk.ui.UiEvent;\n''', 1),
    ('''    private final Actions actions;\n''', '''    private final Actions actions;
    /** Which composition this panel takes, fixed at construction (spec 3.6). */
    private final Layout layout;
    private Consumer<Optional<String>> selectionChanged = row -> { };\n''', 1),
    ('''    public HostsPanel(UiKit kit, Actions actions) {
        this(kit, actions, System::nanoTime, multiClickNanos());
    }''', '''    public HostsPanel(UiKit kit, Actions actions) {
        this(kit, actions, System::nanoTime, multiClickNanos());
    }

    /** The panel in {@code layout}'s composition. */
    public HostsPanel(UiKit kit, Actions actions, Layout layout) {
        this(kit, actions, System::nanoTime, multiClickNanos(), layout);
    }''', 1),
    ('''    HostsPanel(UiKit kit, Actions actions, LongSupplier clock, long multiClickNanos) {
        this.clock = Objects.requireNonNull(clock);''', '''    HostsPanel(UiKit kit, Actions actions, LongSupplier clock, long multiClickNanos) { this(kit, actions, clock, multiClickNanos, Layout.ROWS); }

    HostsPanel(UiKit kit, Actions actions, LongSupplier clock, long multiClickNanos, Layout layout) {
        this.layout = Objects.requireNonNull(layout);
        this.clock = Objects.requireNonNull(clock);''', 1),
    ('''                selectedRow = selected.ids().stream().findFirst().filter(id -> rows.stream().anyMatch(row -> rowId(row).equals(id))).orElse(null);
                render();''', '''                selectedRow = selected.ids().stream().findFirst().filter(id -> rows.stream().anyMatch(row -> rowId(row).equals(id))).orElse(null);
                render();
                selectionChanged.accept(Optional.ofNullable(selectedRow));''', 1),
    ('''                if (row instanceof HostRows.Group group) toggle(group);
            });''', '''                if (layout == Layout.ROWS && row instanceof HostRows.Group group) toggle(group);
            });
            case UiEvent.Expanded expanded when expanded.id().equals(ROWS) -> rowById(expanded.itemId()).ifPresent(row -> {
                if (row instanceof HostRows.Group group && expanded.expanded() == collapsedGroup(group)) toggle(group);
            });''', 1),
    ('''        next.addAll(HostRows.rows(matching, "", needle.isEmpty() ? collapsed : Set.of(), defaultGroup, distroRows, needle.isEmpty() && wslCollapsed));''',
     '''        // The classic tree keeps every row and shows the collapsed groups through its expanded ids.
        boolean flat = layout == Layout.CLASSIC;
        next.addAll(HostRows.rows(matching, "", flat || !needle.isEmpty() ? Set.of() : collapsed, defaultGroup, distroRows, !flat && needle.isEmpty() && wslCollapsed));''', 1),
    ('''        headerChanged.accept(new PanelHeader("", false, List.of(''', '''        headerChanged.accept(new PanelHeader(layout == Layout.CLASSIC ? String.valueOf(count) : "", false, List.of(''', 1),
    ('''    private Nodes.Panel node() {
        var header = new Nodes.Header(HEADER, ControlState.READY, "Sessions"''', '''    private Nodes.Panel node() { return layout == Layout.CLASSIC ? classicNode() : rowsNode(); }

    private Nodes.Panel rowsNode() {
        var header = new Nodes.Header(HEADER, ControlState.READY, "Sessions"''', 1),
    ('''                var menu = new ArrayList<MenuItem>(List.of(menu("connect", id, "Connect in new tab"), menu("split", id, "Connect in split")));
                if (browseFiles != null) menu.add(menu("browse", id, "Browse files"));
                menu.addAll(List.of(menu("edit", id, "Edit…"), menu("duplicate", id, "Duplicate"), menu("delete", id, "Delete…"),
                    menu("favorite", id, host.favorite() ? "Remove from favorites" : "Add to favorites")));
                if (host.group().isEmpty() && defaultGroup.equals(HostRows.OTHER) && hosts.stream().allMatch(h -> h.group().isEmpty()))
                    menu.add(menu("rename", id, "Rename group…"));''', '''                var menu = hostMenu(id, host);''', 1),
    ('''    private static MenuItem menu(String verb, String rowId, String label) {''', '''    /** A host row's menu, the same in both compositions. */
    private List<MenuItem> hostMenu(String id, RemoteHost host) {
        var menu = new ArrayList<MenuItem>(List.of(menu("connect", id, "Connect in new tab"), menu("split", id, "Connect in split")));
        if (browseFiles != null) menu.add(menu("browse", id, "Browse files"));
        menu.addAll(List.of(menu("edit", id, "Edit…"), menu("duplicate", id, "Duplicate"), menu("delete", id, "Delete…"),
            menu("favorite", id, host.favorite() ? "Remove from favorites" : "Add to favorites")));
        if (host.group().isEmpty() && defaultGroup.equals(HostRows.OTHER) && hosts.stream().allMatch(h -> h.group().isEmpty()))
            menu.add(menu("rename", id, "Rename group…"));
        return menu;
    }

    private static MenuItem menu(String verb, String rowId, String label) {''', 1),
    ('''    @Override public void close() {''', CLASSIC, 1)])
R = "plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java"
edit(R, [
    ('''(h, favorite) -> reportMutation("Could not update favorites", store.put(h.withFavorite(favorite))), () -> importConfig(window)));''',
     '''(h, favorite) -> reportMutation("Could not update favorites", store.put(h.withFavorite(favorite))), () -> importConfig(window)), layout);''', 1)])
F = "jasper-app/src/test/java/dev/jasper/app/plugins/PanelSurfaceFixture.java"
edit(F, [
    ('''        Object agent = auth.getField("AGENT").get(null);''', '''        Object agent = auth.getField("AGENT").get(null);
        // Plan 10a: a fifth value "favorite" stars the host; a sixth "vault" gives it a Vault credential (locked here).
        var vaultAuth = loader.loadClass("dev.jasper.remote.hosts.Auth$Vault").getConstructor(UUID.class);''', 1),
    ('''.newInstance(id, values[i][0], values[i][1], 22, values[i][2], agent, values[i].length > 3 ? values[i][3] : "", false, Optional.empty(), Instant.EPOCH, Instant.EPOCH);''',
     '''.newInstance(id, values[i][0], values[i][1], 22, values[i][2], values[i].length > 5 && values[i][5].equals("vault") ? vaultAuth.newInstance(new UUID(0x4100L, i)) : agent,
                    values[i].length > 3 ? values[i][3] : "", values[i].length > 4 && values[i][4].equals("favorite"), Optional.empty(), Instant.EPOCH, Instant.EPOCH);''', 1),
    ('''    /** Puts a saved tunnel through the plugin's own tunnel store; returns its id. */''', '''    /** Selects a host in the window's Sessions panel, as a click does (plan 10a). */
    public void selectSession(UUID host) throws InterruptedException {
        onEdt(() -> {
            try {
                Object panel = ((Map<?, ?>) field(plugin, "panels")).get(vault.window);
                panel.getClass().getMethod("select", UUID.class).invoke(panel, host);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        StagedVaultHost.settle();
    }

    /** Puts a saved tunnel through the plugin's own tunnel store; returns its id. */''', 1)])
PY
```

The HostsPanel anchors are 3e-era text (3e changes no HostsPanel line); if the `rebuildRows` line reads differently,
keep its meaning: under CLASSIC no group is collapsed in `rows`. `HostsPanel.Auth` is `dev.jasper.remote.hosts.Auth`.

Add the ledger rows of the two new test classes (both match the pattern: `new …Panel`, `dev.jasper.sdk.ui`, `java.awt`):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv"); text = path.read_text(encoding="utf-8")
for file, owner, cls in [("plugins/remote/src/test/java/dev/jasper/remote/ui/SessionsTreeTest.java", "dev.jasper.remote", "SessionsTreeTest"),
                         ("jasper-app/src/test/java/dev/jasper/app/plugins/ClassicCompositionRenderTest.java", "app.plugins", "ClassicCompositionRenderTest")]:
    if file in text: raise SystemExit(f"already ledgered: {file}")
    text += f"{file},{owner},{cls},nonvisual-helper,none,none,{file},Test/fixture ownership; New in Enterprise plan 10a Task 4\n"
path.write_text(text, encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests and iterate on the render until it matches**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.SessionsTreeTest' --tests 'dev.jasper.remote.ui.HostsPanelTest' --tests 'dev.jasper.remote.ui.SessionsCompositionTest' --tests 'dev.jasper.remote.RemotePluginTest' :jasper-app:test --tests 'dev.jasper.app.plugins.ClassicCompositionRenderTest' --tests 'dev.jasper.app.plugins.StagedRemoteSessionsTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `SessionsTreeTest` (5), the rows suites unchanged, `ClassicCompositionRenderTest` (1). The render test is
the mock-matching gate: when it fails, open `jasper-app/build/enterprise-compositions/{light,dark}/sessions-side.png`
and fix the owner of the failing band (the plan never edits the measured strings):

| Band (logical px) | Owner |
|---|---|
| (above the crop) the header strip and its rule | plan 7 Task 10 (`PanelHostHeader.Frame.FLUSH`, `DockMockTest`); Task 2's `render` cuts the header row, so bands shifted by the strip's 25 px mean the cut is wrong |
| `0+36` filter area, `36+1` rule under it | the kit `Panels` search slot (`PANEL_SEARCH_HEIGHT`, its divider); plan 7 if its frames own it |
| `37+20` header band, `57+1` rule, the column titles | 4c's `ClassicTreeFace`/`ClassicHeaders` with 3c's column header |
| rows `58+44`, `102+22` selection | `ROW_HEIGHT`, `selection.bg`, this task's tree items |
| `235+1` to `350+1` | Task 3's `ClassicPropertiesFace` |
| `351+40` actions area | the ACTIONS stack (`BUTTON_HEIGHT`, the footer's padding) |

The agreement floor 0.55 is the gate for text and glyph geometry. The fixture's data differ from the mock's only in the
Credentials text ("SSH agent" for "Vault: minecraft-local"); if the measured agreement sits below the floor and the side
image shows only that difference, set the floor to the measured value rounded down to 0.05, and write the value and the
cause into the banner. Any other difference is a defect: fix it and rerun.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote jasper-app/src/test docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Plugin compositions: Remote's Sessions tree and Properties

Under the Enterprise look the Sessions panel is a column tree (Name, Host,
Port) of the same groups, hosts and distributions, with status dots, a
star after favourites and "Filter sessions", over the selected host's
Properties and Edit… and Connect; the header detail is the count. The
rows composition is unchanged. The staged plugin's render matches the
Workstation and Dark Mode mocks' Sessions panel.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: The "Session: [combo] [Connect]" toolbar view

Spec 14.4 "Toolbar view": under CLASSIC, `SessionsToolbar` contributes one `ToolbarItem.View` in place of its Sessions
dropdown: LABEL "Session:", a DROPDOWN of saved hosts "name (hostname)", WSL distributions "name (detail)" and "Manage
Sessions…", and a PRIMARY Connect. The window's choice is shared with its Sessions tree (Rulings 6).

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/SessionSelection.java`,
  `plugins/remote/src/test/java/dev/jasper/remote/SessionToolbarViewTest.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/SessionsToolbar.java`, `RemotePlugin.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/PanelSurfaceFixture.java` (`toolbarView`),
  `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicCompositionRenderTest.java`, `docs/design/theme-engine/migration.csv`
  (`SessionSelection` and `SessionToolbarViewTest`, Execution notes R9)

**Interfaces:**
- Consumes: Task 4's `HostsPanel.selectRow`, `onSelectionChanged`, `rowId`; 3d's `ToolbarItem.view`, `ToolbarEntry.View`
  and `Mounted`.
- Produces: `final class SessionSelection` (`Optional<String> get(UUID)`, `void set(UUID, Optional<String>)`, `void
  forget(UUID)`, `void onChanged(BiConsumer<UUID, Optional<String>>)`); `SessionsToolbar(PluginContext, HostStore, String,
  BiConsumer<WindowHandle, RemoteHost>, Consumer<WindowHandle>, Supplier<List<ShellProfileInfo>>,
  BiConsumer<WindowHandle, ShellProfileInfo>, Layout, SessionSelection, Function<WindowHandle, Optional<UUID>>)`; view ids
  `session`, `session.label`, `session.choice`, `session.connect`; options `host:<uuid>`, `distro:<profile>`, `none`,
  `manage`; `Optional<String> SessionsToolbar.current(WindowHandle)`; `PanelSurfaceFixture.toolbarView(int, int)`.

- [ ] **Step 1: Write the failing tests**

Create `plugins/remote/src/test/java/dev/jasper/remote/SessionToolbarViewTest.java`:

```java
package dev.jasper.remote;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.HostStore;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.UiEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 14.4: under CLASSIC the toolbar's Sessions dropdown becomes one "Session: [combo] [Connect]" view per window. */
class SessionToolbarViewTest {
    @TempDir Path dir;
    final List<String> events = new ArrayList<>();
    final SessionSelection selection = new SessionSelection();
    final AtomicReference<Optional<UUID>> activePane = new AtomicReference<>(Optional.empty());
    final RemoteHost mac = RemoteHost.create("Mac", "192.168.1.119", 22, "dustin", Auth.AGENT, "Local Network", Optional.empty());
    final RemoteHost minecraft = RemoteHost.create("minecraft-local", "192.168.1.124", 22, "dustin", Auth.AGENT, "Local Network", Optional.empty());
    final ShellProfileInfo ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));
    List<ShellProfileInfo> distros = List.of(ubuntu);
    HostStore store;
    SessionsToolbar toolbar;

    /** A context of Remote's identity on the testkit host, the store with two hosts, and the toolbar in {@code layout}. */
    PluginContext start(FakePluginHost host, Layout layout) throws Exception {
        var context = new AtomicReference<PluginContext>();
        host.start(RemotePluginTest.INFO, Set.of(), Set.of(), context::set);
        store = new HostStore(dir.resolve("hosts.toml"), Runnable::run, Runnable::run);
        store.put(mac).join(); store.put(minecraft).join();
        edt(() -> toolbar = new SessionsToolbar(context.get(), store, "network", (window, chosen) -> events.add("activate " + chosen.name()),
            window -> events.add("manage"), () -> distros, (window, profile) -> events.add("open " + profile.name()),
            layout, selection, window -> activePane.get()));
        return context.get();
    }

    static FakeKitView view(FakePluginHost host, UUID window) { return host.toolbarViews(window).getFirst(); }
    static Nodes.Choice choice(FakeKitView view) { return node(view, "session.choice", Nodes.Choice.class); }

    @Test void underClassicOneViewListsHostsThenDistributionsThenManage() throws Exception {
        try (var host = new FakePluginHost()) {
            start(host, Layout.CLASSIC);
            var window = host.addTerminalWindow();
            assertThat(host.toolbar()).as("no Sessions dropdown").noneMatch(item -> item.contains("Sessions"));
            var view = view(host, window);
            assertThat(node(view, "session.label", Nodes.Text.class).text()).isEqualTo("Session:");
            assertThat(choice(view).options()).extracting(Option::label)
                .containsExactly("Mac (192.168.1.119)", "minecraft-local (192.168.1.124)", "Ubuntu (WSL 2)", "Manage Sessions…");
            assertThat(choice(view).selectedId()).as("the first host while nothing is chosen").isEqualTo("host:" + mac.id());
            edt(() -> emit(view, new UiEvent.Selected("session.choice", Set.of("manage"))));
            assertThat(events).containsExactly("manage");
            assertThat(choice(view).selectedId()).as("Manage Sessions… is not a selection").isEqualTo("host:" + mac.id());
        }
    }

    @Test void connectActsOnTheChosenHostThenThePanesThenTheFirst() throws Exception {
        try (var host = new FakePluginHost()) {
            start(host, Layout.CLASSIC);
            var window = host.addTerminalWindow();
            var view = view(host, window);
            edt(() -> press(view, "session.connect"));
            activePane.set(Optional.of(minecraft.id()));
            edt(toolbar::refresh);
            edt(() -> press(view, "session.connect"));
            edt(() -> emit(view, new UiEvent.Selected("session.choice", Set.of("distro:" + ubuntu.id()))));
            edt(() -> press(view, "session.connect"));
            assertThat(events).containsExactly("activate Mac", "activate minecraft-local", "open Ubuntu");
            assertThat(selection.get(window)).contains("distro:" + ubuntu.id());
            selection.set(window, Optional.of("host:" + mac.id()));
            assertThat(choice(view).selectedId()).as("the tree's choice reaches the combo").isEqualTo("host:" + mac.id());
        }
    }

    @Test void aDeletedHostLeavesTheComboAndTheProperties() throws Exception {
        try (var host = new FakePluginHost()) {
            start(host, Layout.CLASSIC);
            var window = host.addTerminalWindow();
            var view = view(host, window);
            selection.set(window, Optional.of("host:" + minecraft.id()));
            store.remove(minecraft.id()).join(); store.remove(mac.id()).join();
            distros = List.of();
            edt(toolbar::refresh);
            assertThat(choice(view).options()).extracting(Option::id).containsExactly("none", "manage");
            assertThat(choice(view).selectedId()).isEqualTo("none");
            assertThat(node(view, "session.connect", Nodes.Button.class).state().enabled()).isFalse();
        }
    }

    @Test void theRowsLayoutKeepsTheSessionsDropdown() throws Exception {
        try (var host = new FakePluginHost()) {
            start(host, Layout.ROWS);
            var window = host.addTerminalWindow();
            assertThat(host.toolbar()).anyMatch(item -> item.contains("Sessions"));
            assertThat(host.toolbarViews(window)).isEmpty();
        }
    }
}
```

`HostStore.remove(UUID)` is the store's removal (`HostStore.java:78`).

Add to `ClassicCompositionRenderTest`:

```java
    static final String TOOLBAR_LIGHT = "0+6 #ECEDEF, 6+1 #B4B8BF, 7+20 #FEFEFE~#F1F2F4, 27+1 #B4B8BF, 28+6 #ECEDEF";
    static final String TOOLBAR_DARK = "0+6 #3C3F44, 6+1 #5A5E65, 7+20 #4A4D52~#45484D, 27+1 #5A5E65, 28+6 #3C3F44";

    @Test @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void theSessionToolbarViewMatchesTheWorkstationMocks() throws Exception {
        for (boolean dark : new boolean[] {false, true}) under(dark, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("toolbar-" + dark), EnterpriseLook.resolved(dark), PanelSurfaceFixture.SESSIONS, true)) {
                var ids = fixture.hosts(new String[][] {{"Mac", "192.168.1.119", "dustin", "Local Network"},
                    {"minecraft-local", "192.168.1.124", "dustin", "Local Network", "favorite"}});
                fixture.selectSession(ids.get(1));
                var crop = new MockMatch.Crop(dark ? DARK : CONNECTED, 660, 110, 720, 68);
                check((dark ? "dark" : "light") + "/session-toolbar", crop, fixture.toolbarView(crop.logicalWidth(), crop.logicalHeight()), 130,
                    dark ? TOOLBAR_DARK : TOOLBAR_LIGHT, 1, 0.50, dark ? new String[] {"#3D74D6", "#3C3F44"} : new String[] {"#2F65CA", "#ECEDEF", "#2655AE"});
            }
        });
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:compileTestJava :jasper-app:compileTestJava --continue`
Expected: FAIL to compile: "cannot find symbol … class SessionSelection", "constructor SessionsToolbar … cannot be applied",
"cannot find symbol … toolbarView".

- [ ] **Step 3: The shared selection and the view**

Create `plugins/remote/src/main/java/dev/jasper/remote/SessionSelection.java`:

```java
package dev.jasper.remote;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Each window's chosen session row ({@code host:<id>} or {@code distro:<profile>}), shared by its toolbar view and its
 * Sessions tree (Enterprise presentation spec 14.4). UI thread only.
 */
final class SessionSelection {
    private final Map<UUID, String> chosen = new HashMap<>();
    private final List<BiConsumer<UUID, Optional<String>>> listeners = new ArrayList<>();

    /** The window's chosen row, if any. */
    Optional<String> get(UUID window) { return Optional.ofNullable(chosen.get(window)); }

    /** Records the window's choice and tells every listener once; the same choice again changes nothing. */
    void set(UUID window, Optional<String> row) {
        Objects.requireNonNull(window); Objects.requireNonNull(row);
        if (get(window).equals(row)) return;
        if (row.isPresent()) chosen.put(window, row.get()); else chosen.remove(window);
        for (var listener : List.copyOf(listeners)) listener.accept(window, row);
    }

    /** The window closed. */
    void forget(UUID window) { chosen.remove(window); }

    /** Hears every change, with the window and its new choice. */
    void onChanged(BiConsumer<UUID, Optional<String>> listener) { listeners.add(Objects.requireNonNull(listener)); }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 10a Task 5 step 3: SessionsToolbar's classic view and its wiring in RemotePlugin."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
T = "plugins/remote/src/main/java/dev/jasper/remote/SessionsToolbar.java"
edit(T, [
    ('''import dev.jasper.remote.hosts.RemoteHost;\n''', '''import dev.jasper.remote.hosts.RemoteHost;\nimport dev.jasper.remote.workstation.Layout;\n''', 1),
    ('''import dev.jasper.sdk.ui.ActionSpec;\n''', '''import dev.jasper.sdk.ui.ActionSpec;\nimport dev.jasper.sdk.ui.ControlState;\nimport dev.jasper.sdk.ui.Nodes;\nimport dev.jasper.sdk.ui.Option;\n''', 1),
    ('''import dev.jasper.sdk.ui.ToolbarItem;\n''', '''import dev.jasper.sdk.ui.ToolbarItem;\nimport dev.jasper.sdk.ui.UiEvent;\nimport dev.jasper.sdk.ui.UiView;\nimport dev.jasper.sdk.ui.ViewSpec;\n''', 1),
    ('''import java.util.Map;\n''', '''import java.util.Map;\nimport java.util.Optional;\n''', 1),
    ('''import java.util.function.Consumer;\n''', '''import java.util.function.Consumer;\nimport java.util.function.Function;\n''', 1),
    ('''    private List<String> placed = List.of();
    private Subscription placement;
''', '''    private List<String> placed = List.of();
    private Subscription placement;
    /** Which composition the toolbar takes (spec 14.4): the dropdown under ROWS, one view per window under CLASSIC. */
    private final Layout layout;
    private final SessionSelection selection;
    private final Consumer<WindowHandle> showHosts;
    private final Function<WindowHandle, Optional<UUID>> activeHost;
    private final Map<UUID, UiView> views = new HashMap<>();
    static final String VIEW = "session", LABEL = "session.label", CHOICE = "session.choice", CONNECT = "session.connect",
        MANAGE_OPTION = "manage", NONE_OPTION = "none";
''', 1),
    ('''    SessionsToolbar(PluginContext context, HostStore store, String icon, BiConsumer<WindowHandle, RemoteHost> activate,
                    Consumer<WindowHandle> showHosts, Supplier<List<ShellProfileInfo>> distros,
                    BiConsumer<WindowHandle, ShellProfileInfo> openDistro) {
        this.context = context; this.store = store; this.icon = icon; this.activate = activate;''',
     '''    SessionsToolbar(PluginContext context, HostStore store, String icon, BiConsumer<WindowHandle, RemoteHost> activate,
                    Consumer<WindowHandle> showHosts, Supplier<List<ShellProfileInfo>> distros,
                    BiConsumer<WindowHandle, ShellProfileInfo> openDistro) {
        this(context, store, icon, activate, showHosts, distros, openDistro, Layout.ROWS, new SessionSelection(), window -> Optional.empty());
    }

    /**
     * {@code selection} is the windows' chosen rows, shared with their Sessions trees; {@code activeHost} is a window's active
     * SSH pane's host, the fallback while nothing is chosen.
     */
    SessionsToolbar(PluginContext context, HostStore store, String icon, BiConsumer<WindowHandle, RemoteHost> activate,
                    Consumer<WindowHandle> showHosts, Supplier<List<ShellProfileInfo>> distros,
                    BiConsumer<WindowHandle, ShellProfileInfo> openDistro, Layout layout, SessionSelection selection,
                    Function<WindowHandle, Optional<UUID>> activeHost) {
        this.layout = layout; this.selection = selection; this.showHosts = showHosts; this.activeHost = activeHost;
        this.context = context; this.store = store; this.icon = icon; this.activate = activate;''', 1),
    ('''            invoked -> showHosts.accept(invoked.window()));
        refresh();
    }''', '''            invoked -> showHosts.accept(invoked.window()));
        if (layout == Layout.CLASSIC) {
            placement = context.toolbar().add(ToolbarItem.view(this::create));
            selection.onChanged((window, row) -> renderViews());
        }
        refresh();
    }''', 1),
    ('''    void refresh() {
        var ids = new ArrayList<String>();''', '''    void refresh() {
        if (layout == Layout.CLASSIC) { renderViews(); return; }
        var ids = new ArrayList<String>();''', 1),
    ('''    /** A valid action id for any profile id: distribution names may hold capitals and dots, which action ids may not. */''',
     '''    // ---- the classic toolbar view (spec 14.4)

    private UiView create(WindowHandle window) {
        var view = context.kit().create(new ViewSpec(classic(window)), event -> classicEvent(window, event));
        views.put(window.id(), view);
        return view;
    }

    /** Every open window's view shows the current hosts, distributions and choice; a closed window's view is dropped. */
    private void renderViews() {
        for (var entry : List.copyOf(views.entrySet())) {
            var window = context.terminals().window(entry.getKey()).filter(WindowHandle::isOpen);
            if (window.isEmpty()) { views.remove(entry.getKey()); selection.forget(entry.getKey()); continue; }
            try { entry.getValue().update(new ViewSpec(classic(window.get()))); }
            catch (IllegalStateException closed) { views.remove(entry.getKey()); }
        }
    }

    private List<Option> options() {
        var options = new ArrayList<Option>();
        store.hosts().stream().sorted(Comparator.comparing(RemoteHost::name, String.CASE_INSENSITIVE_ORDER).thenComparing(RemoteHost::id))
            .forEach(host -> options.add(new Option("host:" + host.id(), host.name() + " (" + host.hostname() + ")", Optional.empty())));
        for (ShellProfileInfo profile : distros.get())
            options.add(new Option("distro:" + profile.id(), profile.name() + profile.detail().map(detail -> " (" + detail + ")").orElse(""), Optional.empty()));
        if (options.isEmpty()) options.add(new Option(NONE_OPTION, "No saved sessions", Optional.empty()));
        options.add(new Option(MANAGE_OPTION, "Manage Sessions…", Optional.empty()));
        return options;
    }

    /** The window's row: its choice while it is listed, else its active SSH pane's host, else the first host or distribution. */
    Optional<String> current(WindowHandle window) {
        var listed = options().stream().map(Option::id).filter(id -> id.startsWith("host:") || id.startsWith("distro:")).toList();
        var chosen = selection.get(window.id()).filter(listed::contains);
        if (chosen.isPresent()) return chosen;
        var pane = activeHost.apply(window).map(id -> "host:" + id).filter(listed::contains);
        return pane.isPresent() ? pane : listed.stream().findFirst();
    }

    private Nodes.Stack classic(WindowHandle window) {
        var current = current(window);
        return new Nodes.Stack(VIEW, ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Text(LABEL, ControlState.READY, "Session:", Nodes.TextRole.LABEL),
            new Nodes.Choice(CHOICE, ControlState.READY, Nodes.ChoiceKind.DROPDOWN, options(), current.orElse(NONE_OPTION), "Session", ""),
            new Nodes.Button(CONNECT, new ControlState(true, current.isPresent(), false, ""), "Connect", "", Nodes.ButtonKind.PRIMARY, CONNECT, "")));
    }

    private void classicEvent(WindowHandle window, UiEvent event) {
        switch (event) {
            case UiEvent.Selected chosen when chosen.id().equals(CHOICE) -> {
                String id = chosen.ids().stream().findFirst().orElse(NONE_OPTION);
                if (id.equals(MANAGE_OPTION)) showHosts.accept(window);
                else if (!id.equals(NONE_OPTION)) selection.set(window.id(), Optional.of(id));
                renderViews();
            }
            case UiEvent.Action action when action.id().equals(CONNECT) -> connect(window);
            default -> { }
        }
    }

    /** Connect: activates the window's host (focus a running pane, else open one) or opens its distribution. */
    void connect(WindowHandle window) {
        current(window).ifPresent(row -> {
            if (row.startsWith("host:")) store.host(UUID.fromString(row.substring("host:".length()))).ifPresent(host -> activate.accept(window, host));
            else distros.get().stream().filter(profile -> ("distro:" + profile.id()).equals(row)).findFirst()
                .ifPresent(profile -> openDistro.accept(window, profile));
        });
    }

    /** A valid action id for any profile id: distribution names may hold capitals and dots, which action ids may not. */''', 1)])
R = "plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java"
edit(R, [
    ('''    private SessionsToolbar sessionsToolbar;\n''', '''    private SessionsToolbar sessionsToolbar;
    /** Each window's chosen session, shared by its toolbar view and its Sessions tree under CLASSIC (spec 14.4). */
    private final SessionSelection sessionSelection = new SessionSelection();\n''', 1),
    ('''        }, () -> distros, this::openDistro);''', '''        }, () -> distros, this::openDistro, layout, sessionSelection, this::activeHostId);
        sessionSelection.onChanged((windowId, row) -> { var shown = panels.get(windowId); if (shown != null) row.ifPresent(shown::selectRow); });''', 1),
    ('''        panel.onHeaderChanged(host::setHeader);\n''', '''        panel.onHeaderChanged(host::setHeader);
        panel.onSelectionChanged(row -> sessionSelection.set(window.id(), row));\n''', 1),
    ('''    private int sessionCount(RemoteHost host) {''', '''    /** The host of the window's active pane, when Remote opened that pane. */
    private Optional<UUID> activeHostId(WindowHandle window) {
        return window.activeTab().flatMap(tab -> tab.activePane()).flatMap(pane -> Optional.ofNullable(panes.get(pane.id())));
    }

    private int sessionCount(RemoteHost host) {''', 1)])
F = "jasper-app/src/test/java/dev/jasper/app/plugins/PanelSurfaceFixture.java"
edit(F, [
    ('''    /** Selects a host in the window's Sessions panel, as a click does (plan 10a). */''', '''    /**
     * The window's contributed toolbar view (spec 10.3), made by its real factory and painted at 2x over the theme's
     * {@code surface.app}, the toolbar row's surface (plan 10a).
     */
    public BufferedImage toolbarView(int width, int height) {
        return onEdtValue(() -> {
            var entry = vault.contributions.toolbar().stream().filter(dev.jasper.app.contributions.ToolbarEntry.View.class::isInstance)
                .map(dev.jasper.app.contributions.ToolbarEntry.View.class::cast).findFirst().orElseThrow(() -> new AssertionError("no toolbar view"));
            var mounted = entry.factory().apply(vault.window).orElseThrow(() -> new AssertionError("the toolbar view's factory failed"));
            var component = mounted.component();
            component.setSize(width, height);
            for (int i = 0; i < 3; i++) SessionsSurfaceFixture.layout(component);
            var image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics(); try { graphics.scale(2, 2); component.printAll(graphics); } finally { graphics.dispose(); }
            mounted.close();
            return dev.jasper.app.testsupport.MockMatch.onto(image, theme.workspace().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
        });
    }

    /** Selects a host in the window's Sessions panel, as a click does (plan 10a). */''', 1)])
PY
```

`RemotePlugin.panes` maps pane ids to host ids (fact 6); `context.terminals().window(UUID)` is the SDK lookup
`StagedRemoteHost.askHostKey` uses. `SessionsSurfaceFixture.layout(Container)` is the fixture's recursive layout
(`PanelSurfaceFixture.size` uses it).

Add the ledger rows: `SessionToolbarViewTest` matches the pattern through `import dev.jasper.sdk.ui`, and
`SessionSelection` through `new ArrayList` (a helper false positive):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv"); text = path.read_text(encoding="utf-8")
listed = {line.split(",", 1)[0] for line in text.splitlines()}
T = "plugins/remote/src/test/java/dev/jasper/remote/SessionToolbarViewTest.java"
rows = [("plugins/remote/src/main/java/dev/jasper/remote/SessionSelection.java", "SessionSelection", "nonvisual-helper,10,none", T,
         "Helper false positive (new ArrayList); New in Enterprise plan 10a Task 5: each window's chosen session row, shared by the toolbar view and the Sessions tree"),
        (T, "SessionToolbarViewTest", "nonvisual-helper,none,none", T, "Test/fixture ownership; New in Enterprise plan 10a Task 5")]
for file, cls, kind, tests, note in rows:
    if file in listed: raise SystemExit(f"already ledgered: {file}")
    text += f"{file},dev.jasper.remote,{cls},{kind},{tests},{note}\n"
path.write_text(text, encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests and match the mock**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.SessionToolbarViewTest' --tests 'dev.jasper.remote.RemotePluginTest' --tests 'dev.jasper.remote.ui.SessionsTreeTest' :jasper-app:test --tests 'dev.jasper.app.plugins.ClassicCompositionRenderTest' --tests 'dev.jasper.app.plugins.StagedRemoteSessionsTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `SessionToolbarViewTest` (4), the Remote suites (the rows layout keeps its dropdown test
`hostContributesSemanticNetworkNavigationIcons`), `ClassicCompositionRenderTest` (2). The toolbar render's bands are the
combo's (owned by 4b's classic dropdown face and 3d's `KitView.toolbarStrip`): its 22 px height and its border and
gradient. The combo's width (219 in the mock, Rulings 14) is not asserted; the agreement floor 0.50 allows the narrower
combo. Fix a band defect in its owner; record the measured agreement in the banner.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote jasper-app/src/test docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Plugin compositions: the Session toolbar view

Under the Enterprise look Remote contributes one "Session: [combo]
[Connect]" view per window in place of its Sessions dropdown: saved hosts
as "name (hostname)", WSL distributions as "name (detail)", then Manage
Sessions…. Connect activates the window's chosen host (focus a running
pane, else open one) or opens the distribution; the choice is shared with
the window's Sessions tree and falls back to the active SSH pane's host.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: The Tunnels table

Spec 14.4 "Tunnels": under CLASSIC a Table (Type, Local, Remote, Via) whose Type cell is a status dot and word;
activating a row toggles the tunnel; the row menu keeps every action (Rulings 7); the footer reads "<name> — inactive.
Click to toggle."; with no tunnels an Empty "No Tunnels" with [New Tunnel…] under the kept headers; a broken file is a
DANGER Notice above the table (Rulings 8).

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsPanel.java`, `TunnelsUi.java`, `RemotePlugin.java`
- Create: `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsTableTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicCompositionRenderTest.java`, `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Task 1's `Layout`; 3c's `Table.empty`, `TableRow.cellNodes`, `TableRow.heading`; 3b's `Notice`.
- Produces: `public TunnelsPanel(UiKit, Actions, Layout)`, package-private `TunnelsPanel(UiKit, Actions, LongSupplier, long,
  Layout)`; `static final List<Column> TunnelsPanel.COLUMNS`, ids `TunnelsPanel.FOOTER = "tunnels.footer"`, the table keeps
  `tunnels.rows` and its row ids, its activate action is `TOGGLE`; `TunnelsUi(…, Function<UUID, Optional<RemoteHost>> paneHost,
  Layout layout)` and `(…, Consumer<String> clipboard, Layout layout)`.

- [ ] **Step 1: Write the failing tests**

Create `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsTableTest.java`:

```java
package dev.jasper.remote.ui.tunnels;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.tunnels.Counters;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TableRow;
import dev.jasper.sdk.ui.UiEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 14.4: the classic Tunnels panel is a table with the rows' gestures. */
class TunnelsTableTest {
    static final UUID MAC = UUID.randomUUID();
    final List<String> events = new ArrayList<>();
    final Map<UUID, TunnelState> states = new HashMap<>();
    final Tunnel test = Tunnel.create("test", MAC, TunnelKind.LOCAL, Tunnel.LOCALHOST, 8080, "localhost", 234);
    final Tunnel socks = Tunnel.create("socks", MAC, TunnelKind.DYNAMIC, Tunnel.LOCALHOST, 1080, "", 0);
    final TunnelsPanel.Actions actions = new TunnelsPanel.Actions(t -> events.add("start " + t.name()), t -> events.add("stop " + t.name()),
        t -> events.add("edit " + t.name()), t -> events.add("duplicate " + t.name()), t -> events.add("delete " + t.name()),
        t -> events.add("save " + t.name()), t -> events.add("browse " + t.name()), t -> events.add("copy " + t.name()), () -> events.add("add"));
    TunnelsPanel panel;

    TunnelsPanel panel(List<Tunnel> saved, Optional<String> error) {
        panel = new TunnelsPanel(new FakeKit("dev.jasper.remote"), actions, () -> 0, 500_000_000L, Layout.CLASSIC);
        panel.setTunnels(saved, List.of(), error, id -> states.getOrDefault(id, TunnelState.STOPPED), id -> Optional.of("Mac"));
        return panel;
    }

    Nodes.Table table() { return node(panel.view(), TunnelsPanel.ROWS, Nodes.Table.class); }

    @Test void theTableShowsTypeLocalRemoteAndVia() throws Exception {
        edt(() -> {
            panel(List.of(socks, test), Optional.empty());
            assertThat(table().columns()).extracting(column -> column.title()).containsExactly("Type", "Local", "Remote", "Via");
            TableRow row = table().rows().stream().filter(r -> r.id().equals(TunnelsPanel.rowId(test.id()))).findFirst().orElseThrow();
            assertThat(row.cells()).containsEntry("local", ":8080").containsEntry("remote", "localhost:234").containsEntry("via", "Mac");
            var type = (Nodes.Stack) row.cellNodes().get("type");
            assertThat(type.children()).extracting(node -> node.getClass().getSimpleName()).containsExactly("StatusDot", "Text");
            assertThat(((Nodes.Text) type.children().get(1)).text()).isEqualTo("Local");
            assertThat(table().rows().getFirst().cells()).as("socks").containsEntry("remote", "SOCKS5");
            assertThat(node(panel.view(), TunnelsPanel.FOOTER, Nodes.Text.class).text()).isEqualTo("socks — inactive. Click to toggle.");
            assertThat(node(panel.view(), TunnelsPanel.FILTER, Nodes.Field.class).hint()).isEqualTo("Filter tunnels");
        });
    }

    @Test void theFooterAndTheDotFollowTheConfirmedState() throws Exception {
        edt(() -> {
            states.put(test.id(), new TunnelState.Failed("Connection refused"));
            panel(List.of(test), Optional.empty());
            var type = (Nodes.Stack) table().rows().getFirst().cellNodes().get("type");
            assertThat(((Nodes.StatusDot) type.children().getFirst()).kind()).isEqualTo(Nodes.StatusKind.DANGER);
            assertThat(node(panel.view(), TunnelsPanel.FOOTER, Nodes.Text.class).text()).isEqualTo("test — failed: Connection refused. Click to toggle.");
            states.put(test.id(), new TunnelState.Starting("Connecting"));
            panel.refresh();
            emit(panel.view(), new UiEvent.Selected(TunnelsPanel.ROWS, Set.of(TunnelsPanel.rowId(test.id()))));
            press(panel.view(), table().activateAction());
            states.put(test.id(), new TunnelState.Up(Instant.now(), 8080, new Counters()));
            panel.refresh();
            press(panel.view(), table().activateAction());
            assertThat(events).as("activation stops a starting or up tunnel").containsExactly("stop test", "stop test");
            assertThat(node(panel.view(), TunnelsPanel.FOOTER, Nodes.Text.class).text()).isEqualTo("test — active. Click to toggle.");
            press(panel.view(), menuAction(table().rows().getFirst(), "Edit…"));
            assertThat(events).last().isEqualTo("edit test");
        });
    }

    @Test void noTunnelsIsTheEmptyStateUnderTheHeadersAndABrokenFileIsANotice() throws Exception {
        edt(() -> {
            panel(List.of(), Optional.of("line 3: unknown key"));
            assertThat(table().rows()).isEmpty();
            assertThat(table().empty()).hasValueSatisfying(empty -> {
                assertThat(empty.title()).isEqualTo("No Tunnels");
                assertThat(empty.actions()).singleElement().extracting(node -> ((Nodes.Button) node).label()).isEqualTo("New Tunnel…");
            });
            assertThat(node(panel.view(), TunnelsPanel.PROBLEM, Nodes.Notice.class).severity()).isEqualTo(Nodes.Severity.DANGER);
            assertThat(find(panel.view(), TunnelsPanel.FILTER)).as("no filter without tunnels").isEmpty();
            press(panel.view(), TunnelsPanel.EMPTY_NEW);
            assertThat(events).containsExactly("add");
        });
    }

    static String menuAction(TableRow row, String label) {
        return row.contextActions().orElseThrow().stream().filter(item -> item.label().equals(label)).findFirst().orElseThrow().action();
    }
}
```

Add to `ClassicCompositionRenderTest`:

```java
    static final String TUNNELS_LIGHT = "0+36 #F7F7F8, 36+1 #DDE0E4, 37+20 #F0F1F3, 57+1 #D3D6DA, 58+24 #FFFFFF, 82+1 #EEF0F2, 83+238 #FFFFFF";
    static final String TUNNELS_DARK = "0+36 #35383D, 36+1 #45484E, 37+20 #393C41, 57+1 #4A4D53, 58+24 #2E3135, 82+1 #3E4146, 83+238 #2E3135";
    static final String TUNNELS_NONE = "0+20 #F0F1F3, 20+1 #D3D6DA, 21+300 #FFFFFF";

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theTunnelsTableMatchesTheWorkstationMocks() throws Exception {
        for (boolean dark : new boolean[] {false, true}) under(dark, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("tunnels-" + dark), EnterpriseLook.resolved(dark), PanelSurfaceFixture.TUNNELS, true)) {
                var mac = fixture.hosts(new String[][] {{"Mac", "192.168.1.119", "dustin", "Local Network"}}).getFirst();
                fixture.tunnel("test", mac, "LOCAL", 8080, "localhost", 234);
                var crop = new MockMatch.Crop(dark ? DARK : CONNECTED, 2136, 1064, 700, 642);
                check((dark ? "dark" : "light") + "/tunnels", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 4,
                    dark ? TUNNELS_DARK : TUNNELS_LIGHT, 2, 0.55, dark ? new String[] {"#393C41", "#2E3135"} : new String[] {"#F0F1F3", "#A3A8B0", "#5C6370"});
            }
        });
        under(false, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("tunnels-none"), EnterpriseLook.resolved(false), PanelSurfaceFixture.TUNNELS, true)) {
                var crop = new MockMatch.Crop(DISCONNECTED, 2136, 1064, 700, 642);
                check("light/tunnels-none", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 4, TUNNELS_NONE, 1, 0.55, "#1E1F22", "#B4B8BF");
            }
        });
    }
```

The row band's tolerance is 2 (Rulings 14: the mock's row is 24, the classic table's 22).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:compileTestJava`
Expected: FAIL to compile: "no suitable constructor found for TunnelsPanel(FakeKit,Actions,()->0,long,Layout)", "cannot find
symbol … FOOTER".

- [ ] **Step 3: Compose the classic Tunnels panel**

```bash
python3 -I - <<'PY'
"""Plan 10a Task 6 step 3: the classic Tunnels table."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsPanel.java"
CLASSIC = '''    // ---- the classic composition (Enterprise presentation spec 14.4)

    /** The classic table's columns, in the Workstation mock's proportions. */
    static final List<Column> COLUMNS = List.of(new Column("type", "Type", Nodes.TextRole.BODY, false, 76, Optional.empty()),
        new Column("local", "Local", Nodes.TextRole.MONO, false, 87, Optional.empty()), new Column("remote", "Remote", Nodes.TextRole.MONO, false, 110, Optional.empty()),
        new Column("via", "Via", Nodes.TextRole.BODY, false, 77, Optional.empty()));
    public static final String FOOTER = "tunnels.footer", TOP = "tunnels.top";

    private Nodes.Panel classicNode() {
        var header = new Nodes.Header(HEADER, ControlState.READY, "Tunnels", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
        List<Row> rows = shown();
        if (selectedRow != null && rows.stream().noneMatch(row -> row.id().equals(selectedRow) && row.tunnel() != null)) selectedRow = null;
        boolean none = saved.isEmpty() && quick.isEmpty();
        var tableRows = new ArrayList<TableRow>();
        for (Row row : rows) {
            if (row.id().equals(QUICK_HEADING)) tableRows.add(TableRow.heading(QUICK_HEADING, "Quick forwards", OptionalInt.empty(), ""));
            else if (row.tunnel() != null) tableRows.add(tableRow(row));
        }
        Optional<Nodes.Empty> empty = none ? Optional.of(new Nodes.Empty(EMPTY, ControlState.READY,
            new Nodes.Icon(EMPTY + ".icon", ControlState.READY, "tunnel", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Tunnels"), "No Tunnels",
            "Forward a local port to a remote host, or use Forward Port from an SSH pane.",
            List.of(new Nodes.Button(EMPTY_NEW, ControlState.READY, "New Tunnel…", "", Nodes.ButtonKind.SECONDARY, EMPTY_NEW, "")))) : Optional.empty();
        boolean filtered = !none && tableRows.stream().noneMatch(row -> row.heading().isEmpty());
        var table = new Nodes.Table(ROWS, ControlState.READY, COLUMNS, tableRows, selectedRow == null ? Set.of() : Set.of(selectedRow), TOGGLE, List.of(),
            Nodes.SelectionMode.SINGLE, Nodes.ScrollPolicy.STANDARD, empty, filtered ? "No tunnels match" : "");
        var top = new ArrayList<Nodes.Node>();
        error.ifPresent(message -> top.add(new Nodes.Notice(PROBLEM, ControlState.READY, Nodes.Severity.DANGER, problemText(), List.of())));
        if (!none) top.add(new Nodes.Field(FILTER, ControlState.READY, Nodes.FieldKind.SEARCH, query, "Filter tunnels", "Filter tunnels", ""));
        Optional<Nodes.Node> search = top.isEmpty() ? Optional.empty()
            : top.size() == 1 && top.getFirst() instanceof Nodes.Field ? Optional.of(top.getFirst())
            : Optional.of(new Nodes.Stack(TOP, ControlState.READY, Nodes.LayoutRole.VERTICAL, top));
        Optional<Nodes.Node> footer = selected().or(() -> rows.stream().map(Row::tunnel).filter(Objects::nonNull).findFirst())
            .map(tunnel -> new Nodes.Text(FOOTER, ControlState.READY, footerText(tunnel), Nodes.TextRole.CAPTION));
        return new Nodes.Panel(ROOT, ControlState.READY, header, search, table, footer);
    }

    private TableRow tableRow(Row row) {
        Tunnel tunnel = row.tunnel(); String id = row.id();
        TunnelState state = states.apply(tunnel.id());
        Optional<String> hostName = hostNames.apply(tunnel.hostId());
        String word = switch (tunnel.kind()) { case LOCAL -> "Local"; case REMOTE -> "Remote"; case DYNAMIC -> "Dynamic"; };
        var type = new Nodes.Stack("row." + id + ".type", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.StatusDot("row." + id + ".dot", ControlState.READY, dot(state)), new Nodes.Text("row." + id + ".kind", ControlState.READY, word, Nodes.TextRole.BODY)));
        String port = TunnelRows.port(tunnel, state);
        String listen = (tunnel.bindAddress().equals(Tunnel.LOCALHOST) ? "" : bracketed(tunnel.bindAddress())) + ":" + port;
        String target = bracketed(tunnel.targetHost()) + ":" + tunnel.targetPort();
        String local = tunnel.kind() == TunnelKind.REMOTE ? target : listen;
        String remote = switch (tunnel.kind()) { case LOCAL -> target; case REMOTE -> listen; case DYNAMIC -> "SOCKS5"; };
        String detail = TunnelRows.detail(tunnel, state, hostName, clock.get());
        return new TableRow(id, Map.of("local", local, "remote", remote, "via", hostName.orElse("—")), Optional.empty(), false,
            Optional.of(menu(id, tunnel, state)), TunnelRows.title(tunnel, state) + ", " + TunnelRows.mapping(tunnel, state) + ", " + detail,
            Map.of("type", type), Optional.empty());
    }

    private static String bracketed(String host) { return host.contains(":") ? "[" + host + "]" : host; }

    /** The selected tunnel's, else the first one's, line: its name, its confirmed state and the gesture. */
    private String footerText(Tunnel tunnel) {
        TunnelState state = states.apply(tunnel.id());
        String word = switch (state) {
            case TunnelState.Up up -> "active";
            case TunnelState.Starting starting -> "starting";
            case TunnelState.Failed failed -> "failed: " + failed.reason();
            case TunnelState.Stopped stopped -> "inactive";
        };
        return TunnelRows.title(tunnel, state) + " — " + word + ". Click to toggle.";
    }

    @Override public void close() {'''
edit(P, [
    ('''import dev.jasper.remote.tunnels.TunnelState;\n''', '''import dev.jasper.remote.tunnels.TunnelState;\nimport dev.jasper.remote.workstation.Layout;\nimport dev.jasper.sdk.ui.Column;\n''', 1),
    ('''import dev.jasper.sdk.ui.PanelHeader;\n''', '''import dev.jasper.sdk.ui.PanelHeader;\nimport dev.jasper.sdk.ui.TableRow;\n''', 1),
    ('''import java.util.Locale;\n''', '''import java.util.Locale;\nimport java.util.Map;\n''', 1),
    ('''    private final Actions actions;\n''', '''    private final Actions actions;\n    /** Which composition this panel takes, fixed at construction (spec 3.6). */\n    private final Layout layout;\n''', 1),
    ('''    public TunnelsPanel(UiKit kit, Actions actions) {
        this(kit, actions, System::nanoTime, multiClickInterval());
    }''', '''    public TunnelsPanel(UiKit kit, Actions actions) {
        this(kit, actions, System::nanoTime, multiClickInterval());
    }

    /** The panel in {@code layout}'s composition. */
    public TunnelsPanel(UiKit kit, Actions actions, Layout layout) {
        this(kit, actions, System::nanoTime, multiClickInterval(), layout);
    }''', 1),
    ('''    TunnelsPanel(UiKit kit, Actions actions, LongSupplier nanos, long multiClickNanos) {
        this.actions = Objects.requireNonNull(actions);''', '''    TunnelsPanel(UiKit kit, Actions actions, LongSupplier nanos, long multiClickNanos) { this(kit, actions, nanos, multiClickNanos, Layout.ROWS); }

    TunnelsPanel(UiKit kit, Actions actions, LongSupplier nanos, long multiClickNanos, Layout layout) {
        this.layout = Objects.requireNonNull(layout);
        this.actions = Objects.requireNonNull(actions);''', 1),
    ('''    private Nodes.Panel node() {
        var header = new Nodes.Header(HEADER, ControlState.READY, "Tunnels",''', '''    private Nodes.Panel node() { return layout == Layout.CLASSIC ? classicNode() : rowsNode(); }

    private Nodes.Panel rowsNode() {
        var header = new Nodes.Header(HEADER, ControlState.READY, "Tunnels",''', 1),
    ('''    @Override public void close() {''', CLASSIC, 1)])
U = "plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java"
edit(U, [
    ('''import dev.jasper.remote.hosts.RemoteHost;\n''', '''import dev.jasper.remote.hosts.RemoteHost;\nimport dev.jasper.remote.workstation.Layout;\n''', 1),
    ('''    public TunnelsUi(PluginContext context, Executor ui, TunnelStore store, TunnelManager manager, Supplier<List<RemoteHost>> hosts,
                     Function<UUID, Optional<RemoteHost>> host, Function<UUID, Optional<RemoteHost>> paneHost, Consumer<String> clipboard) {
        this.context = context;''', '''    /** The tunnels UI in {@code layout}'s composition (spec 14.4). */
    public TunnelsUi(PluginContext context, Executor ui, TunnelStore store, TunnelManager manager, Supplier<List<RemoteHost>> hosts,
                     Function<UUID, Optional<RemoteHost>> host, Function<UUID, Optional<RemoteHost>> paneHost, Layout layout) {
        this(context, ui, store, manager, hosts, host, paneHost, text -> context.platform().copy(text), layout);
    }

    public TunnelsUi(PluginContext context, Executor ui, TunnelStore store, TunnelManager manager, Supplier<List<RemoteHost>> hosts,
                     Function<UUID, Optional<RemoteHost>> host, Function<UUID, Optional<RemoteHost>> paneHost, Consumer<String> clipboard) {
        this(context, ui, store, manager, hosts, host, paneHost, clipboard, Layout.ROWS);
    }

    public TunnelsUi(PluginContext context, Executor ui, TunnelStore store, TunnelManager manager, Supplier<List<RemoteHost>> hosts,
                     Function<UUID, Optional<RemoteHost>> host, Function<UUID, Optional<RemoteHost>> paneHost, Consumer<String> clipboard, Layout layout) {
        this.layout = layout;
        this.context = context;''', 1),
    ('''            () -> edit(window, Optional.empty(), Optional.empty())));
        panel.onHeaderChanged(panelHost::setHeader);''', '''            () -> edit(window, Optional.empty(), Optional.empty())), layout);
        panel.onHeaderChanged(panelHost::setHeader);''', 1),
    ('''    private final PluginContext context;\n''', '''    private final PluginContext context;\n    /** Which composition the Tunnels panels take (spec 14.4). */\n    private final Layout layout;\n''', 1)])
R = "plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java"
edit(R, [
    ('''            store::hosts, store::host, paneId -> Optional.ofNullable(panes.get(paneId)).flatMap(store::host));''',
     '''            store::hosts, store::host, paneId -> Optional.ofNullable(panes.get(paneId)).flatMap(store::host), layout);''', 1)])
PY
grep -n "new TunnelsPanel(context.kit(), new TunnelsPanel.Actions(" plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java
grep -n "edit(window, Optional.empty(), Optional.empty())), layout);" plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java
```

Expected: each `grep` prints one line. `TunnelRows.port` is package-private in the same package; `selected()`, `shown()`,
`Row`, `dot`, `menu`, `problemText`, `clock` are the panel's own members (fact 9). If `TunnelsUi`'s context field is
declared otherwise than `private final PluginContext context;`, add the `layout` field beside it by hand.

Add the ledger row of `TunnelsTableTest` (it matches `new …Panel`):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv"); text = path.read_text(encoding="utf-8")
file = "plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsTableTest.java"
if file in text: raise SystemExit("already ledgered")
path.write_text(text + f"{file},dev.jasper.remote,TunnelsTableTest,nonvisual-helper,none,none,{file},Test/fixture ownership; New in Enterprise plan 10a Task 6\n", encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests and match the mocks**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.tunnels.*' --tests 'dev.jasper.remote.TunnelsPluginTest' :jasper-app:test --tests 'dev.jasper.app.plugins.ClassicCompositionRenderTest' --tests 'dev.jasper.app.plugins.StagedRemoteTunnelsTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `TunnelsTableTest` (3), the tunnels suites unchanged, `ClassicCompositionRenderTest` (3). Render triage: the
header strip above the crop is cut by Task 2's `render` (plan 7 owns it); the filter area and its rule are the kit
`Panels`'; the header band, rule and row are 4c's `ClassicTableFace`; the footer is
this task's CAPTION text; the empty state is 4c's `ClassicEmptyFace` (its title BODY_STRONG `#1E1F22`, its sentence
`#5C6370` wrapping at the panel's width, its one button). Record the measured agreements in the banner.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote jasper-app/src/test docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Plugin compositions: Remote's Tunnels table

Under the Enterprise look the Tunnels panel is a table of Type (a status
dot and word), Local, Remote and Via; activating a row starts or stops the
tunnel by its confirmed state, the row menu keeps every action, the footer
names the selected tunnel's state, a broken file is a Notice, and with no
tunnels the table keeps its headers over "No Tunnels" and New Tunnel….

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 7: The SFTP navigation row and its disconnected state

Spec 14.4 "SFTP": under CLASSIC the navigation row is ← → ⟳, a Separator, "Hidden files", a Separator, "Upload ▾" over
the breadcrumbs; while disconnected a DROPDOWN "Choose Session…" and a disabled Breadcrumbs reading "/" sit above an
Empty "Not Connected" under the kept Name, Size and Modified headers; the table gains a Modified column; the header detail
is the connection name with the active dot (as today); the transfer rows leave the footer (Task 8).

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpPanel.java`, `SftpUi.java`, `RemotePlugin.java`
- Create: `plugins/remote/src/test/java/dev/jasper/remote/ui/sftp/SftpClassicTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicCompositionRenderTest.java`, `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Task 1's `Layout`; 3b's `Separator`; 3c's `Table.empty`.
- Produces: `public SftpPanel(UiKit, Layout)`; ids `SftpPanel.CHOOSE_DROPDOWN = "sftp.choose.dropdown"`, `TOOLS_SEPARATOR =
  "sftp.tools.separator"`, `UPLOAD_SEPARATOR = "sftp.upload.separator"`; the dropdown's options `choose`, `none` and
  `session:<uuid>`; `static String SftpPanel.shortModified(FileEntry)` (`MMM dd`); `SftpUi(…, Consumer<WindowHandle>
  releaseStrip, Layout layout)` and `(…, Consumer<String> clipboard, Layout layout)`.

- [ ] **Step 1: Write the failing tests**

Create `plugins/remote/src/test/java/dev/jasper/remote/ui/sftp/SftpClassicTest.java`:

```java
package dev.jasper.remote.ui.sftp;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.UiEvent;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 14.4: the classic SFTP navigation row, its disconnected state and the Modified column. */
class SftpClassicTest {
    final RemoteHost mac = RemoteHost.create("Mac", "192.168.1.119", 22, "dustin", Auth.AGENT, "", Optional.empty());

    SftpPanel panel() { return new SftpPanel(new FakeKit("dev.jasper.remote"), Layout.CLASSIC); }

    @Test void disconnectedWithoutHostsIsInert() throws Exception {
        edt(() -> {
            var panel = panel();
            var choose = node(panel.view(), SftpPanel.CHOOSE_DROPDOWN, Nodes.Choice.class);
            assertThat(choose.options()).extracting(Option::label).containsExactly("No saved sessions");
            assertThat(choose.state().enabled()).isFalse();
            var crumbs = node(panel.view(), SftpPanel.PATH, Nodes.Breadcrumbs.class);
            assertThat(crumbs.crumbs()).extracting(crumb -> crumb.label()).containsExactly("/");
            assertThat(crumbs.state().enabled()).isFalse();
            var table = node(panel.view(), SftpPanel.FILES, Nodes.Table.class);
            assertThat(table.columns()).extracting(column -> column.title()).containsExactly("Name", "Size", "Modified");
            assertThat(table.rows()).isEmpty();
            assertThat(table.empty()).hasValueSatisfying(empty -> assertThat(empty.title()).isEqualTo("Not Connected"));
            assertThat(find(panel.view(), SftpPanel.BACK)).as("no navigation tools while disconnected").isEmpty();
        });
    }

    @Test void chooseSessionBrowsesTheHostAndReturnsToItsPrompt() throws Exception {
        edt(() -> {
            var panel = panel();
            var chosen = new ArrayList<String>();
            panel.onChooseSession(host -> chosen.add(host.name()));
            panel.sessions(List.of(mac));
            var choose = node(panel.view(), SftpPanel.CHOOSE_DROPDOWN, Nodes.Choice.class);
            assertThat(choose.options()).extracting(Option::label).containsExactly("Choose Session…", "Mac");
            assertThat(choose.selectedId()).isEqualTo("choose");
            emit(panel.view(), new UiEvent.Selected(SftpPanel.CHOOSE_DROPDOWN, Set.of("session:" + mac.id())));
            assertThat(chosen).containsExactly("Mac");
            assertThat(node(panel.view(), SftpPanel.CHOOSE_DROPDOWN, Nodes.Choice.class).selectedId()).isEqualTo("choose");
        });
    }

    @Test void aListingShowsTheClassicRowAndTheModifiedColumn() throws Exception {
        edt(() -> {
            var panel = panel();
            long september = ZonedDateTime.of(2026, 9, 28, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli();
            panel.showPage("Mac", "/Users/dustin", List.of(new FileEntry("Applications", FileEntry.Kind.DIRECTORY, 0, september, 0755, "")), 0, 1, false);
            var tools = node(panel.view(), SftpPanel.TOOLS, Nodes.Stack.class);
            assertThat(tools.children()).extracting(node -> node.id()).containsExactly(SftpPanel.BACK, SftpPanel.FORWARD, SftpPanel.REFRESH,
                SftpPanel.TOOLS_SEPARATOR, SftpPanel.HIDDEN, SftpPanel.UPLOAD_SEPARATOR, SftpPanel.UPLOAD);
            assertThat(node(panel.view(), SftpPanel.HIDDEN, Nodes.Button.class).label()).isEqualTo("Hidden files");
            assertThat(node(panel.view(), SftpPanel.UPLOAD, Nodes.MenuButton.class).label()).isEqualTo("Upload");
            var table = node(panel.view(), SftpPanel.FILES, Nodes.Table.class);
            assertThat(table.rows().getFirst().cells()).containsEntry("modified", "Sep 28");
            assertThat(table.empty()).isEmpty();
            assertThat(find(panel.view(), SftpPanel.CHOOSE_DROPDOWN)).isEmpty();
        });
    }
}
```

If 3e or 3b changed `FileEntry`'s constructor, use its form (`StagedRemoteHost.fileEntry` builds one by reflection).

Add to `ClassicCompositionRenderTest`:

```java
    static final String SFTP_LIGHT = "0+61 #F7F7F8, 61+1 #DDE0E4, 62+20 #F0F1F3, 82+1 #D3D6DA, 83+22 #2F65CA, 105+22 #F7F8FA, 127+22 #FFFFFF, "
        + "149+22 #F7F8FA, 171+22 #FFFFFF, 193+22 #F7F8FA, 215+22 #FFFFFF, 237+22 #F7F8FA, 259+22 #FFFFFF, 281+22 #F7F8FA, 303+22 #FFFFFF, "
        + "325+2 #F7F8FA, 327+1 #DDE0E4, 328+28 #F7F7F8";
    static final String SFTP_DARK = "0+61 #35383D, 61+1 #45484E, 62+20 #393C41, 82+1 #4A4D53, 83+22 #3D74D6, 105+22 #313439, 127+22 #2E3135, "
        + "149+22 #313439, 171+22 #2E3135, 193+22 #313439, 215+22 #2E3135, 237+22 #313439, 259+22 #2E3135, 281+22 #313439, 303+22 #2E3135, "
        + "325+2 #313439, 327+1 #45484E, 328+28 #35383D";
    static final String SFTP_DISCONNECTED = "0+36 #F7F7F8, 36+1 #DDE0E4, 37+20 #F0F1F3, 57+1 #D3D6DA, 58+269 #FFFFFF, 327+1 #DDE0E4, 328+28 #F7F7F8";

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theSftpRowAndTableMatchTheWorkstationMocks() throws Exception {
        String[] folders = {"Applications", "Applications (Parallels)", "bin", "Desktop", "Documents", "Downloads", "go", "IdeaProjects",
            "Library", "Movies", "Music", "Pictures"};
        for (boolean dark : new boolean[] {false, true}) under(dark, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("sftp-" + dark), EnterpriseLook.resolved(dark), PanelSurfaceFixture.SFTP, true)) {
                var entries = new java.util.ArrayList<Object>();
                for (String folder : folders) entries.add(fixture.fileEntry(folder, "DIRECTORY", 0));
                fixture.sftp("showPage", new Class<?>[] {String.class, String.class, java.util.List.class, long.class, long.class, boolean.class},
                    "Mac", "/Users/dustin", entries, 0L, (long) entries.size(), false);
                var crop = new MockMatch.Crop(dark ? DARK : CONNECTED, 48, 230, 600, 712);
                check((dark ? "dark" : "light") + "/sftp", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 8,
                    dark ? SFTP_DARK : SFTP_LIGHT, 1, 0.55, dark ? new String[] {"#3D74D6", "#313439"} : new String[] {"#2F65CA", "#F7F8FA", "#C4C7CC"});
            }
        });
        under(false, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("sftp-off"), EnterpriseLook.resolved(false), PanelSurfaceFixture.SFTP, true)) {
                fixture.hosts(new String[][] {{"Mac", "192.168.1.119", "dustin", "Local Network"}});
                var crop = new MockMatch.Crop(DISCONNECTED, 48, 230, 600, 712);
                check("light/sftp-disconnected", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 8, SFTP_DISCONNECTED, 1, 0.55,
                    "#1E1F22", "#F0F1F3", "#B4B8BF");
            }
        });
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:compileTestJava`
Expected: FAIL to compile: "no suitable constructor found for SftpPanel(FakeKit,Layout)", "cannot find symbol … CHOOSE_DROPDOWN".

- [ ] **Step 3: Compose the classic SFTP panel**

```bash
python3 -I - <<'PY'
"""Plan 10a Task 7 step 3: the classic SFTP panel."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpPanel.java"
CLASSIC = '''    // ---- the classic composition (Enterprise presentation spec 14.4)

    public static final String CHOOSE_DROPDOWN = "sftp.choose.dropdown", TOOLS_SEPARATOR = "sftp.tools.separator", UPLOAD_SEPARATOR = "sftp.upload.separator";
    static final String CHOOSE_PROMPT = "choose", NO_SESSIONS = "none";
    /** The classic table's columns, in the Workstation mock's proportions. */
    static final List<Column> CLASSIC_COLUMNS = List.of(new Column("name", "Name", Nodes.TextRole.BODY, false, 136, Optional.empty()),
        new Column("size", "Size", Nodes.TextRole.DESCRIPTION, true, 77, Optional.empty()),
        new Column("modified", "Modified", Nodes.TextRole.MONO_SMALL, false, 87, Optional.empty()));

    private Nodes.Panel classicPanel() {
        var header = new Nodes.Header(HEADER, ControlState.READY, "SFTP", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
        var footer = List.<Nodes.Node>of(paging(), status(), resultLine(),
            new Nodes.SettingRow(FOLLOW_ROW, ControlState.READY, FOLLOW_TEXT, "",
                new Nodes.Toggle(FOLLOW, state(true, browsingEnabled), FOLLOW_TEXT, following, FOLLOW, true, FOLLOW_DESCRIPTION), false));
        return new Nodes.Panel(ROOT, ControlState.READY, header, Optional.of(loaded ? classicNavigation() : chooser()), classicTable(),
            Optional.of(new Nodes.Stack(FOOTER, ControlState.READY, Nodes.LayoutRole.VERTICAL, footer)));
    }

    private Nodes.Node classicNavigation() {
        boolean ready = loaded && !working;
        var tools = List.<Nodes.Node>of(
            tool(BACK, "back", "Back", browsingEnabled && ready && canBack), tool(FORWARD, "forward", "Forward", browsingEnabled && ready && canForward),
            tool(REFRESH, "refresh", "Refresh", browsingEnabled && ready), new Nodes.Separator(TOOLS_SEPARATOR, ControlState.READY),
            new Nodes.Button(HIDDEN, state(true, browsingEnabled && ready), "Hidden files", "", Nodes.ButtonKind.GHOST, HIDDEN, "Show hidden items",
                Nodes.ButtonPresentation.STANDARD, Optional.empty(), Optional.of(showHidden)),
            new Nodes.Separator(UPLOAD_SEPARATOR, ControlState.READY),
            new Nodes.MenuButton(UPLOAD, state(true, ready), "Upload", "", Nodes.ButtonKind.GHOST, UPLOAD, menu(MENU, "", selection()), "Upload files"));
        var crumbs = new ArrayList<Crumb>(); var steps = crumbs();
        for (int i = 0; i < steps.size(); i++) crumbs.add(new Crumb(crumbId(i), steps.get(i)[0], List.of()));
        var path = new Nodes.Breadcrumbs(PATH, state(true, browsingEnabled && !working), crumbs, "Remote directory", editingPath, pathValue, PATH_EDIT, PATH_GO, PATH_DONE);
        return new Nodes.Stack(NAVIGATION, ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Stack(TOOLS, ControlState.READY, Nodes.LayoutRole.INLINE, tools), path));
    }

    /** Disconnected: Choose Session… over the saved hosts, and the root as disabled breadcrumbs. */
    private Nodes.Node chooser() {
        var options = new ArrayList<Option>();
        if (sessions.isEmpty()) options.add(new Option(NO_SESSIONS, "No saved sessions", Optional.empty()));
        else {
            options.add(new Option(CHOOSE_PROMPT, "Choose Session…", Optional.empty()));
            for (var host : sessions) options.add(new Option(SESSION + host.id(), host.name(), Optional.empty()));
        }
        var choose = new Nodes.Choice(CHOOSE_DROPDOWN, state(true, !sessions.isEmpty() && !working), Nodes.ChoiceKind.DROPDOWN, options,
            options.getFirst().id(), "Choose an SSH session", "");
        var root = new Nodes.Breadcrumbs(PATH, state(true, false), List.of(new Crumb(crumbId(0), "/", List.of())), "Remote directory", false, "", PATH_EDIT, PATH_GO, PATH_DONE);
        return new Nodes.Stack(NAVIGATION, ControlState.READY, Nodes.LayoutRole.INLINE, List.of(choose, root));
    }

    private Nodes.Table classicTable() {
        var rows = new ArrayList<TableRow>();
        if (loaded) for (var entry : entries) {
            String id = rowId(entry);
            rows.add(new TableRow(id, Map.of("name", entry.name(), "size", formatSize(entry), "modified", shortModified(entry)), Optional.of(glyph(entry)), false,
                Optional.of(menu(ROW_MENU, ":" + id, selected.contains(id) ? selection() : List.of(entry))),
                entry.name() + " — " + formatSize(entry) + " — " + modified(entry)));
        }
        Optional<Nodes.Empty> empty = loaded ? Optional.empty() : Optional.of(new Nodes.Empty(EMPTY, ControlState.READY,
            new Nodes.Icon(EMPTY + ".icon", ControlState.READY, "folder", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "SFTP"), "Not Connected",
            "Connect to an SSH session to browse its files.", List.of()));
        return new Nodes.Table(FILES, state(true, !working), CLASSIC_COLUMNS, rows, loaded ? selected : Set.of(), OPEN, loaded ? menu(MENU, "", selection()) : List.of(),
            Nodes.SelectionMode.MULTIPLE, Nodes.ScrollPolicy.STANDARD, empty, "");
    }

    /** The modified day as the classic table shows it: "Sep 28". */
    static String shortModified(FileEntry entry) {
        return entry.modifiedMillis() < 0 ? "" : DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.modifiedMillis()));
    }

    @Override public void close() {'''
edit(P, [
    ('''import dev.jasper.remote.ui.transfers.TransferStrip;\n''', '''import dev.jasper.remote.ui.transfers.TransferStrip;\nimport dev.jasper.remote.workstation.Layout;\n''', 1),
    ('''import dev.jasper.sdk.ui.Nodes;\n''', '''import dev.jasper.sdk.ui.Nodes;\nimport dev.jasper.sdk.ui.Option;\n''', 1),
    ('''    private final boolean destination;\n''', '''    private final boolean destination;\n    /** Which composition the sidebar takes, fixed at construction (spec 3.6); an embedded browser keeps the rows. */\n    private final Layout layout;\n''', 1),
    ('''    public SftpPanel(UiKit kit) {
        changed=null;focuser=null;destination=false;''', '''    public SftpPanel(UiKit kit) { this(kit, Layout.ROWS); }

    /** The sidebar explorer in {@code layout}'s composition. */
    public SftpPanel(UiKit kit, Layout layout) {
        this.layout=java.util.Objects.requireNonNull(layout);
        changed=null;focuser=null;destination=false;''', 1),
    ('''        this.changed=Objects.requireNonNull(changed);this.focuser=Objects.requireNonNull(focus);destination=true;view=null;''',
     '''        this.changed=Objects.requireNonNull(changed);this.focuser=Objects.requireNonNull(focus);destination=true;view=null;layout=Layout.ROWS;''', 1),
    ('''            case UiEvent.Edited edited when edited.id().equals(PATH) -> { pathValue=edited.text();return true; }''',
     '''            case UiEvent.Edited edited when edited.id().equals(PATH) -> { pathValue=edited.text();return true; }
            case UiEvent.Selected chosen when chosen.id().equals(CHOOSE_DROPDOWN) -> {
                chosen.ids().stream().findFirst().filter(id -> id.startsWith(SESSION)).ifPresent(this::act);
                render();return true;
            }''', 1),
    ('''    private Nodes.Panel panel() {''', '''    private Nodes.Panel panel() { return layout == Layout.CLASSIC ? classicPanel() : rowsPanel(); }

    private Nodes.Panel rowsPanel() {''', 1),
    ('''    @Override public void close() {''', CLASSIC, 1)])
U = "plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java"
edit(U, [
    ('''    private volatile boolean closed;\n''', '''    private volatile boolean closed;\n    /** Which composition the sidebars take (spec 14.4). */\n    private final dev.jasper.remote.workstation.Layout layout;\n''', 1),
    ('''                  Supplier<List<RemoteHost>> hosts,Function<WindowHandle,Optional<PaneTarget>> activePane,Function<WindowHandle,dev.jasper.remote.ui.transfers.TransferStrip> transferStrip,Consumer<WindowHandle> releaseStrip) {
        this(context,ui,background,connections,endpoints,transfers,hosts,activePane,transferStrip,releaseStrip,text->context.platform().copy(text));
    }''', '''                  Supplier<List<RemoteHost>> hosts,Function<WindowHandle,Optional<PaneTarget>> activePane,Function<WindowHandle,dev.jasper.remote.ui.transfers.TransferStrip> transferStrip,Consumer<WindowHandle> releaseStrip) {
        this(context,ui,background,connections,endpoints,transfers,hosts,activePane,transferStrip,releaseStrip,text->context.platform().copy(text));
    }
    /** The sidebars in {@code layout}'s composition; under CLASSIC the window's transfers are the Transfers panel's. */
    public SftpUi(PluginContext context,Executor ui,Executor background,Connections connections,EndpointFactory endpoints,Supplier<TransferCoordinator> transfers,
                  Supplier<List<RemoteHost>> hosts,Function<WindowHandle,Optional<PaneTarget>> activePane,Function<WindowHandle,dev.jasper.remote.ui.transfers.TransferStrip> transferStrip,Consumer<WindowHandle> releaseStrip,
                  dev.jasper.remote.workstation.Layout layout) {
        this(context,ui,background,connections,endpoints,transfers,hosts,activePane,transferStrip,releaseStrip,text->context.platform().copy(text),layout);
    }''', 1),
    ('''                  Supplier<List<RemoteHost>> hosts,Function<WindowHandle,Optional<PaneTarget>> activePane,Function<WindowHandle,dev.jasper.remote.ui.transfers.TransferStrip> transferStrip,Consumer<WindowHandle> releaseStrip,Consumer<String> clipboard) {
        this.context=context;''', '''                  Supplier<List<RemoteHost>> hosts,Function<WindowHandle,Optional<PaneTarget>> activePane,Function<WindowHandle,dev.jasper.remote.ui.transfers.TransferStrip> transferStrip,Consumer<WindowHandle> releaseStrip,Consumer<String> clipboard) {
        this(context,ui,background,connections,endpoints,transfers,hosts,activePane,transferStrip,releaseStrip,clipboard,dev.jasper.remote.workstation.Layout.ROWS);
    }
    public SftpUi(PluginContext context,Executor ui,Executor background,Connections connections,EndpointFactory endpoints,Supplier<TransferCoordinator> transfers,
                  Supplier<List<RemoteHost>> hosts,Function<WindowHandle,Optional<PaneTarget>> activePane,Function<WindowHandle,dev.jasper.remote.ui.transfers.TransferStrip> transferStrip,Consumer<WindowHandle> releaseStrip,Consumer<String> clipboard,
                  dev.jasper.remote.workstation.Layout layout) {
        this.layout=layout;
        this.context=context;''', 1),
    ('''        var panel=new SftpPanel(context.kit());var window=host.window();''', '''        var panel=new SftpPanel(context.kit(),layout);var window=host.window();''', 1),
    ('''        panel.transfers(transferStrip.apply(window));''', '''        if(layout==dev.jasper.remote.workstation.Layout.ROWS) panel.transfers(transferStrip.apply(window));''', 1),
    ('''        host.onClosed(()-> { releaseStrip.accept(window);''', '''        host.onClosed(()-> { if(layout==dev.jasper.remote.workstation.Layout.ROWS) releaseStrip.accept(window);''', 1)])
R = "plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java"
edit(R, [
    ('''store::hosts,this::sftpPane,transferUi::strip,transferUi::release);''', '''store::hosts,this::sftpPane,transferUi::strip,transferUi::release,layout);''', 1)])
PY
```

`SftpPanel`'s helpers (`tool`, `crumbs`, `crumbId`, `menu`, `paging`, `status`, `resultLine`, `glyph`, `formatSize`,
`modified`) and constants (`SESSION`, `FOLLOW_ROW`, …) are its own (fact 10); `act(String)` already browses a
`session:<uuid>` id.

Add the ledger row of `SftpClassicTest` (it matches `new …Panel`):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv"); text = path.read_text(encoding="utf-8")
file = "plugins/remote/src/test/java/dev/jasper/remote/ui/sftp/SftpClassicTest.java"
if file in text: raise SystemExit("already ledgered")
path.write_text(text + f"{file},dev.jasper.remote,SftpClassicTest,nonvisual-helper,none,none,{file},Test/fixture ownership; New in Enterprise plan 10a Task 7\n", encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests and match the mocks**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.sftp.*' --tests 'dev.jasper.remote.SftpPluginTest' --tests 'dev.jasper.remote.SftpChooseSessionTest' :jasper-app:test --tests 'dev.jasper.app.plugins.ClassicCompositionRenderTest' --tests 'dev.jasper.app.plugins.StagedRemoteSftpTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `SftpClassicTest` (3), the SFTP suites unchanged (the rows composition and the embedded destination browser
keep their nodes), `ClassicCompositionRenderTest` (4). Render triage: the header strip above the crop is cut by Task 2's
`render` (plan 7 owns it); the navigation row's 28 and the breadcrumbs field's
24 are the INLINE stack, 4b's classic button and field faces and 3b's Separator (1 px `border.window`, as tall as a glyph
in the default look; the mock's 18 px separator is the classic face's height to give if 4b drew none); the header band,
rows and zebra are 4c's table face; the Follow row is 4b's classic toggle face.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote jasper-app/src/test docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Plugin compositions: the SFTP navigation row and Modified column

Under the Enterprise look the SFTP panel's tools read back, forward and
refresh, a separator, Hidden files, a separator and Upload; disconnected,
a Choose Session… dropdown and the root as disabled breadcrumbs sit over
the kept Name, Size and Modified headers and "Not Connected". The table
gains the Modified column; the embedded destination browser is unchanged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 8: The Transfers bottom panel and Enterprise's default slots

Spec 14.4 "Transfers" and "Default slots": under CLASSIC Transfers is its own BOTTOM panel registered with
`withToggleActionId(TransferUi.SHOW)`, so its stripe reads "3: Transfers"; "Transfers" (`TransferUi.SHOW`, ⌘⌥3) shows it
under CLASSIC and reveals the SFTP sidebar under ROWS. Sessions RIGHT, Tunnels RIGHT with `withSplit`, SFTP LEFT without
`withSplit`, Transfers BOTTOM (File Servers LEFT with `withSplit` is Task 10's). Plan 7's Rulings 18 gives a look's first
dock each panel's registered default slot; the order inside the BOTTOM_LEFT strip (spec 14.4: Transfers "after Event
Log") is the dock's and no plan checks it, so it is a recorded residual owned by plan 7 (Rulings 14); this task checks
the anchors only.

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransfersPanel.java`, `TransfersPanels.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransferStrip.java` (`list()`),
  `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`, `ui/tunnels/TunnelsUi.java`, `ui/sftp/SftpUi.java` (anchors)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java`, `plugins/remote/src/test/java/dev/jasper/remote/ui/transfers/TransfersPanelTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (the two new production classes and the new test)

**Interfaces:**
- Consumes: Task 7's `SftpUi(…, Layout)`; `TransferUi.strip(window)` and `release(window)`; `TransferStrip.handle`.
- Produces: `public final class TransfersPanel` (`PANEL = "dev.jasper.remote.transfers.panel"`, `ROOT = "transfers.panel"`,
  `EMPTY = "transfers.panel.empty"`, `TransfersPanel(UiKit, TransferStrip)`, `view()`, `close()`); `public final class
  TransfersPanels` (`TransfersPanels(PluginContext, TransferUi)`, `void show(WindowHandle)`); `Optional<Nodes.Node>
  TransferStrip.list()`.

- [ ] **Step 1: Write the failing tests**

Create `plugins/remote/src/test/java/dev/jasper/remote/ui/transfers/TransfersPanelTest.java`:

```java
package dev.jasper.remote.ui.transfers;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 14.4: the classic Transfers panel shows the window's transfer rows, or "No Transfers". */
class TransfersPanelTest {
    @Test void thePanelShowsTheRowsWithoutTheSidebarsHeading() throws Exception {
        edt(() -> {
            var cancelled = new ArrayList<UUID>();
            var strip = new TransferStrip(new TransferStrip.Actions((id, action) -> { }, cancelled::add, id -> { }));
            var panel = new TransfersPanel(new FakeKit("dev.jasper.remote"), strip);
            assertThat(node(panel.view(), TransfersPanel.EMPTY, Nodes.Empty.class).title()).isEqualTo("No Transfers");
            UUID id = UUID.randomUUID();
            strip.rows(List.of(new TransferRows.Row(id, "↑", "report.pdf", "Local: /tmp/report.pdf → prod: /srv", "Uploading",
                OptionalDouble.of(0.4), false, Optional.empty(), false, false)));
            assertThat(find(panel.view(), TransfersPanel.EMPTY)).isEmpty();
            assertThat(find(panel.view(), TransferStrip.HEADING)).as("the panel's own title heads it").isEmpty();
            assertThat(node(panel.view(), TransferStrip.ROWS, Nodes.ItemList.class).items()).hasSize(1);
            press(panel.view(), TransferStrip.CLOSE + id);
            assertThat(cancelled).containsExactly(id);
        });
    }
}
```

The `TransferRows.Row` components are the ones `StagedRemoteHost.transferRows` builds (`UUID, String arrow, String title,
String tooltip, String status, OptionalDouble fraction, boolean indeterminate, Optional<Action> action, boolean finished,
boolean cancelling`); if they moved, use the record's order. `TransferStrip.CLOSE` is package-private and the test is in
its package.

In `RemotePluginTest`, after `aStartUnderEnterpriseReadsTheClassicLayoutOnce`:

```java
    @Test void underEnterpriseThePanelsTakeTheirSlotsAndTransfersIsABottomPanel(@TempDir Path dir) throws Exception {
        try (var host = new FakePluginHost()) {
            dev.jasper.remote.workstation.FakeEnterprise.start(host);
            host.start(INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor", "dev.jasper.enterprise"), plugin(dir.resolve("ssh")));
            settle(host);
            assertThat(host.panels()).containsExactlyInAnyOrder(
                "dev.jasper.remote.transfers.panel|Transfers|BOTTOM|dev.jasper.remote.transfers",
                "dev.jasper.remote.sftp.panel|SFTP|LEFT|dev.jasper.remote.sftp.toggle",
                "dev.jasper.remote.tunnels.panel|Tunnels|RIGHT+split|dev.jasper.remote.tunnels",
                "dev.jasper.remote.panel|Sessions|RIGHT|dev.jasper.remote.hosts");
            var window = host.addTerminalWindow();
            host.invoke(dev.jasper.remote.ui.transfers.TransferUi.SHOW, window, null);
            settle(host);
            assertThat(host.panelView(dev.jasper.remote.ui.transfers.TransfersPanel.PANEL, window)).as("Transfers shows its panel").isPresent();
            host.openPanel(SftpUi.PANEL, window);
            settle(host);
            assertThat(dev.jasper.remote.testsupport.Kits.find(host.panelView(SftpUi.PANEL, window).orElseThrow(), dev.jasper.remote.ui.transfers.TransferStrip.ROOT))
                .as("the SFTP footer holds no transfers under CLASSIC").isEmpty();
        }
    }
```

If the fake prints panels otherwise (`RemotePluginTest.hostContributesSemanticNetworkNavigationIcons` shows the form),
follow it. `Kits.find` is named in full because `RemotePluginTest` declares its own `find(Component, Class)`, which hides
the static import.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:compileTestJava`
Expected: FAIL to compile: "cannot find symbol … class TransfersPanel".

- [ ] **Step 3: The panel, its registration and the slots**

Create `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransfersPanel.java`:

```java
package dev.jasper.remote.ui.transfers;

import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The classic Transfers bottom panel (Enterprise presentation spec 14.4): the window's transfer rows, which the default
 * look shows in the SFTP sidebar, or "No Transfers". One per window; UI thread only.
 */
public final class TransfersPanel implements AutoCloseable {
    public static final String PANEL = "dev.jasper.remote.transfers.panel", ROOT = "transfers.panel", HEADER = "transfers.panel.header",
        EMPTY = "transfers.panel.empty";
    private final TransferStrip strip;
    private final UiView view;
    private boolean closed;

    public TransfersPanel(UiKit kit, TransferStrip strip) {
        this.strip = Objects.requireNonNull(strip);
        view = kit.create(new ViewSpec(node()), event -> { if (!closed) strip.handle(event); });
        strip.onChanged(this::render);
    }

    public UiView view() { return view; }

    private void render() { if (!closed) view.update(new ViewSpec(node())); }

    private Nodes.Panel node() {
        var header = new Nodes.Header(HEADER, ControlState.READY, "Transfers", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
        Nodes.Node body = strip.list().orElseGet(() -> new Nodes.Empty(EMPTY, ControlState.READY,
            new Nodes.Icon(EMPTY + ".icon", ControlState.READY, "download", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Transfers"), "No Transfers",
            "Uploads and downloads from SFTP show here.", List.of()));
        return new Nodes.Panel(ROOT, ControlState.READY, header, Optional.empty(), body, Optional.empty());
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        strip.onChanged(() -> { });
        view.close();
    }
}
```

Create `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransfersPanels.java`:

```java
package dev.jasper.remote.ui.transfers;

import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.UiView;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registers the classic Transfers BOTTOM panel (spec 14.4), toggled by {@link TransferUi#SHOW} so its stripe reads
 * "3: Transfers", and shows it on that action. Each window's panel shows that window's transfer rows. UI thread only.
 */
public final class TransfersPanels {
    private final PluginContext context;
    private final TransferUi transfers;
    private final Map<UUID, PanelHost> hosts = new HashMap<>();

    public TransfersPanels(PluginContext context, TransferUi transfers) {
        this.context = context; this.transfers = transfers;
        context.panels().register(new PanelSpec(TransfersPanel.PANEL, "Transfers", "download", Anchor.BOTTOM).withToggleActionId(TransferUi.SHOW), this::create);
    }

    private UiView create(PanelHost host) {
        WindowHandle window = host.window();
        var panel = new TransfersPanel(context.kit(), transfers.strip(window));
        hosts.put(window.id(), host);
        host.onClosed(() -> { panel.close(); transfers.release(window); if (hosts.get(window.id()) == host) hosts.remove(window.id()); });
        return panel.view();
    }

    /** Shows the window's Transfers panel, opening it there when it is not open. */
    public void show(WindowHandle window) {
        PanelHost host = hosts.get(window.id());
        if (host == null) context.panels().toggle(TransfersPanel.PANEL, window); else host.show();
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 10a Task 8 step 3: the strip's list, the Transfers panel's wiring and Enterprise's default slots."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransferStrip.java"
edit(S, [
    ('''    /** Acts on one of the strip's events and says whether it was one. */''', '''    /** The rows (or the unavailable line) alone, without the sidebar's heading: the classic Transfers panel's body. */
    public Optional<Nodes.Node> list() {
        if (!visible()) return Optional.empty();
        if (!unavailable.isEmpty()) return Optional.of(new Nodes.Text(UNAVAILABLE, ControlState.READY, unavailable, Nodes.TextRole.DESCRIPTION));
        return Optional.of(new Nodes.ItemList(ROWS, ControlState.READY, Nodes.ListKind.DOUBLE, rows.stream().map(TransferStrip::item).toList(),
            selected == null ? Set.of() : Set.of(selected), "", List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty()));
    }

    /** Acts on one of the strip's events and says whether it was one. */''', 1)])
R = "plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java"
edit(R, [
    ('''    private dev.jasper.remote.ui.transfers.TransferUi transferUi;\n''', '''    private dev.jasper.remote.ui.transfers.TransferUi transferUi;
    /** The classic Transfers bottom panels (spec 14.4); null under ROWS. */
    private dev.jasper.remote.ui.transfers.TransfersPanels transfersPanels;\n''', 1),
    ('''        transferUi=new dev.jasper.remote.ui.transfers.TransferUi(context,transfers,ui,window->sftpUi.reveal(window));''',
     '''        transferUi=new dev.jasper.remote.ui.transfers.TransferUi(context,transfers,ui,window->{
            if(transfersPanels!=null) transfersPanels.show(window); else sftpUi.reveal(window);
        });
        if(layout==dev.jasper.remote.workstation.Layout.CLASSIC) transfersPanels=new dev.jasper.remote.ui.transfers.TransfersPanels(context,transferUi);''', 1),
    ('''        context.panels().register(new PanelSpec(PANEL, "Sessions", icon, Anchor.LEFT).withToggleActionId(HOSTS), this::createPanel);''',
     '''        // Enterprise's default slots (spec 14.4): Sessions on the right; the default look keeps the left.
        context.panels().register(new PanelSpec(PANEL, "Sessions", icon, layout == dev.jasper.remote.workstation.Layout.CLASSIC ? Anchor.RIGHT : Anchor.LEFT)
            .withToggleActionId(HOSTS), this::createPanel);''', 1)])
U = "plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java"
edit(U, [
    ('''        context.panels().register(new PanelSpec(PANEL, "Tunnels", icon, Anchor.LEFT).withToggleActionId(TOGGLE), this::create);''',
     '''        // Enterprise's default slots (spec 14.4): Tunnels under Sessions on the right.
        context.panels().register(layout == Layout.CLASSIC ? new PanelSpec(PANEL, "Tunnels", icon, Anchor.RIGHT).withToggleActionId(TOGGLE).withSplit()
            : new PanelSpec(PANEL, "Tunnels", icon, Anchor.LEFT).withToggleActionId(TOGGLE), this::create);''', 1)])
F = "plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java"
edit(F, [
    ('''        context.panels().register(new PanelSpec(PANEL,"SFTP","folder",Anchor.LEFT).withToggleActionId(TOGGLE).withSplit(),this::create);''',
     '''        // Enterprise's default slots (spec 14.4): SFTP leads the left side, File Servers under it.
        context.panels().register(layout==dev.jasper.remote.workstation.Layout.CLASSIC ? new PanelSpec(PANEL,"SFTP","folder",Anchor.LEFT).withToggleActionId(TOGGLE)
            : new PanelSpec(PANEL,"SFTP","folder",Anchor.LEFT).withToggleActionId(TOGGLE).withSplit(),this::create);''', 1)])
PY
```

The SFTP `register` line is in the constructor that Task 7 made the twelve-argument form, after `this.layout=layout;`.

Add the ledger rows (`TransfersPanel` and `TransfersPanels` are production surfaces; the test matches `new …Panel`):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv"); text = path.read_text(encoding="utf-8")
T = "plugins/remote/src/test/java/dev/jasper/remote/ui/transfers/TransfersPanelTest.java"
rows = [("plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransfersPanel.java", "TransfersPanel", "standard-ui,10,10",
         T + ";plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java",
         "New in Enterprise plan 10a Task 8: the classic Transfers bottom panel; owned SDK UiView of a kit Panel over the window's TransferStrip list or an Empty No Transfers"),
        ("plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransfersPanels.java", "TransfersPanels", "standard-ui,10,10",
         "plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java",
         "New in Enterprise plan 10a Task 8: registers the BOTTOM panel toggled by TransferUi.SHOW and shows it on that action"),
        (T, "TransfersPanelTest", "nonvisual-helper,none,none", T, "Test/fixture ownership; New in Enterprise plan 10a Task 8")]
# A row's own source column, not its tests column: the first row's tests name T (Execution notes R10).
listed = {line.split(",", 1)[0] for line in text.splitlines()}
for file, cls, kind, tests, note in rows:
    if file in listed: raise SystemExit(f"already ledgered: {file}")
    text += f"{file},dev.jasper.remote,{cls},{kind},{tests},{note}\n"
path.write_text(text, encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-remote:test :jasper-app:test --tests 'dev.jasper.app.plugins.StagedTransferLifecycleTest' --tests 'dev.jasper.app.plugins.RemoteShortcutConfigTest' --tests 'dev.jasper.app.plugins.ClassicCompositionRenderTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: the whole Remote suite (the default look's slots and its sidebar transfers unchanged), the new cases, the
staged transfer and shortcut tests, the renders (the SFTP render above now holds no transfer rows under CLASSIC).

- [ ] **Step 5: Commit**

```bash
git add plugins/remote docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Plugin compositions: the Transfers panel and Enterprise's default slots

Under the Enterprise look the window's transfer rows are a Transfers
bottom panel toggled by Transfers (⌘⌥3), whose stripe reads "3:
Transfers", and the SFTP footer no longer holds them; Sessions starts
on the right with Tunnels under it, SFTP leads the left. The default
look keeps its slots and shows transfers in the SFTP sidebar.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 9: Add Host's keys and authentication, and New Tunnel, against their mocks

Spec 14.4 "Add Host" and Rulings 12: under CLASSIC the Keys list sets `emptyText` "No keys" and the Authentication
choice sits in the control column (an empty-label FormRow); the side column (4c) reads Add Stored Key…, Remove, Move Up,
Move Down. New Tunnel's composition is unchanged and is rendered against its mock.

Add Host, New Tunnel and New File Server are FULL forms in native centred dialogs (a modal DIALOG that is not an
attached sheet opens natively under the classic mapping, `NativeShells.classicPlacement`). Plan 6b's final code already
gives such a dialog's kit content spec 10.8's footer band: under `DialogMapping.CLASSIC`, `NativeShells.dialog` applies
`nativeMarks(surface, systemTitle)` (`dialogBand`, and `hostTitled` for a FULL form under a system title bar), now and to
content set later (Execution notes R7; Rulings 15 is superseded, and this task adds no host change). The headless
harness (`AppContractTest.headlessWindows`) runs no shell, so `renderSurface` sets the flags the Enterprise host sets
before it lays the content out: `hostTitled` and `dialogBand`.

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/HostEditor.java`, `RemotePlugin.java` (`editHost`),
  `jasper-app/src/test/java/dev/jasper/app/plugins/PanelSurfaceFixture.java` (`invoke`, `surface`, `renderSurface`)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/HostEditorTest.java` (one case),
  `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicCompositionRenderTest.java`

**Interfaces:**
- Consumes: Task 1's `Layout`; 3c's `ItemList.emptyText`; 6b's `KitView.hostTitled(boolean)`, `dialogBand(boolean)` and
  `NativeShells.nativeMarks` (the native dialogs' flags, unchanged here).
- Produces: `public HostEditor HostEditor.layout(Layout)`; `PanelSurfaceFixture.invoke(String actionId)`, `AuxiliarySurface surface(String title)`, `BufferedImage
  renderSurface(AuxiliarySurface, int, int)` (2× over `surface.dialog`, the content's `hostTitled` and `dialogBand` set
  as the Enterprise host sets them).

- [ ] **Step 1: Write the failing tests**

Add to `plugins/remote/src/test/java/dev/jasper/remote/ui/HostEditorTest.java` (its `editor(others, editing, vault, names,
pick, cancel)` factory builds a form on a `FakeKit`, `HostEditorTest.java:28-31`):

```java
    @Test void underClassicTheKeysSayNoKeysAndTheAuthenticationSitsInTheControlColumn() throws Exception {
        dev.jasper.remote.testsupport.Kits.edt(() -> {
            var editor = editor(List.of(), Optional.empty(), true, id -> Optional.empty(), () -> new CompletableFuture<>(), () -> { })
                .layout(dev.jasper.remote.workstation.Layout.CLASSIC);
            var keys = dev.jasper.remote.testsupport.Kits.node(editor.view(), HostEditor.KEYS, dev.jasper.sdk.ui.Nodes.ItemList.class);
            org.assertj.core.api.Assertions.assertThat(keys.emptyText()).isEqualTo("No keys");
            var row = dev.jasper.remote.testsupport.Kits.node(editor.view(), "row." + HostEditor.AUTH, dev.jasper.sdk.ui.Nodes.FormRow.class);
            org.assertj.core.api.Assertions.assertThat(row.label()).isEmpty();
            org.assertj.core.api.Assertions.assertThat(row.control().id()).isEqualTo(HostEditor.AUTH);
            var rows = editor(List.of(), Optional.empty(), true, id -> Optional.empty(), () -> new CompletableFuture<>(), () -> { });
            org.assertj.core.api.Assertions.assertThat(dev.jasper.remote.testsupport.Kits.node(rows.view(), HostEditor.KEYS, dev.jasper.sdk.ui.Nodes.ItemList.class).emptyText())
                .as("the default look keeps today's list").isEmpty();
            org.assertj.core.api.Assertions.assertThat(dev.jasper.remote.testsupport.Kits.find(rows.view(), "row." + HostEditor.AUTH)).isEmpty();
        });
    }
```

Add to `ClassicCompositionRenderTest`:

```java
    static final String ADD_HOST = "0+476 #ECEDEF, 476+1 #C4C7CC, 477+44 #E6E7EA";
    /**
     * The keys box's white run under 4c's ordered face: its side column (spec 14.4's Add Stored Key…, Remove, Move Up and
     * Move Down, which supersedes the mock's two buttons) sets it taller than the mock's 56 (Execution notes R11).
     */
    static final int KEYS_BOX = 56;
    static final String ADD_HOST_KEYS = "0+3 #ECEDEF, 3+1 #B4B8BF, 4+" + KEYS_BOX + " #FFFFFF, " + (4 + KEYS_BOX) + "+1 #B4B8BF, " + (5 + KEYS_BOX) + "+4 #ECEDEF";
    static final String NEW_TUNNEL = "0+404 #ECEDEF, 404+1 #C4C7CC, 405+44 #E6E7EA";

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void addHostAndNewTunnelMatchTheirMocks() throws Exception {
        under(false, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("dialogs"), EnterpriseLook.resolved(false), PanelSurfaceFixture.SESSIONS, true)) {
                fixture.hosts(new String[][] {{"Mac", "192.168.1.119", "dustin", "Local Network"}});
                fixture.invoke("dev.jasper.remote.hosts.new");
                var addHost = fixture.surface("Add Host");
                var hostCrop = new MockMatch.Crop("03 Add Host/Add Host - Default.png", 2, 60, 1040, 1042);
                var host = fixture.renderSurface(addHost, hostCrop.logicalWidth(), hostCrop.logicalHeight());
                check("light/add-host", hostCrop, host, 18, ADD_HOST, 1, 0.55, "#ECEDEF", "#E6E7EA", "#B4B8BF", "#2F65CA");
                assertThat(MockMatch.mismatch(MockMatch.parse(ADD_HOST_KEYS), MockMatch.bands(host, 298, 500, 500 + 2 * (KEYS_BOX + 9)), 1)).as("the keys box").isEmpty();
                onEdt(addHost::requestClose);
                fixture.invoke("dev.jasper.remote.tunnels.new");
                var tunnelCrop = new MockMatch.Crop("04 New Tunnel/New Tunnel - Local.png", 2, 60, 1040, 898);
                check("light/new-tunnel", tunnelCrop, fixture.renderSurface(fixture.surface("New Tunnel"), tunnelCrop.logicalWidth(), tunnelCrop.logicalHeight()),
                    18, NEW_TUNNEL, 1, 0.55, "#ECEDEF", "#E6E7EA", "#2F65CA", "#2E9E4F");
            }
        });
    }
```

The keys-box band reads mock column 300 from y 560 (crop x 298, from crop row 500). `KEYS_BOX` starts at the mock's 56;
the first run prints the render's bands at that column (`MockMatch.bands(host, 298, 500, 900)`): set `KEYS_BOX` to the
render's white run between the two `#B4B8BF` rules (the 4c ordered face beside its side column, at least 56) and record
it in the banner as a residual against the mock (spec 14.4 supersedes the mock's column). The action ids are
`RemotePlugin.NEW_HOST` (`dev.jasper.remote.hosts.new`) and `TunnelsUi.NEW` (`dev.jasper.remote.tunnels.new`).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:compileTestJava :jasper-app:compileTestJava --continue`
Expected: FAIL to compile: "cannot find symbol … layout(Layout)", "… invoke(String)", "… renderSurface".

- [ ] **Step 3: The classic Add Host and the fixture's dialogs**

```bash
python3 -I - <<'PY'
"""Plan 10a Task 9 step 3: Add Host under CLASSIC and the fixture's dialog renders."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
H = "plugins/remote/src/main/java/dev/jasper/remote/ui/HostEditor.java"
edit(H, [
    ('''import dev.jasper.remote.hosts.RemoteHost;\n''', '''import dev.jasper.remote.hosts.RemoteHost;\nimport dev.jasper.remote.workstation.Layout;\n''', 1),
    ('''    private final UiView view;\n''', '''    private final UiView view;\n    /** Which composition the form takes; set once by {@link #layout} before the dialog shows (Rulings 3). */\n    private Layout layout = Layout.ROWS;\n''', 1),
    ('''    private void render() { if (!closed) view.update(new ViewSpec(form())); }''', '''    private void render() { if (!closed) view.update(new ViewSpec(form())); }

    /** Composes the form in {@code value}'s layout (Enterprise presentation spec 14.4); call once, right after construction. */
    public HostEditor layout(Layout value) { layout = java.util.Objects.requireNonNull(value); render(); return this; }''', 1),
    ('''        var authentication = section("host.authentication", "Authentication", Nodes.CardKind.MIXED, List.of(
            new Nodes.Choice(AUTH,''', '''        Nodes.Node authChoice = new Nodes.Choice(AUTH,''', 1),
    ('''                new Option(AGENT, "SSH Agent", Optional.empty())), vaultAuth ? VAULT : AGENT, "Authentication", ""),
            row("row." + CREDENTIAL,''', '''                new Option(AGENT, "SSH Agent", Optional.empty())), vaultAuth ? VAULT : AGENT, "Authentication", "");
        // The classic label column: the unlabelled choice sits in the control column (spec 6.4).
        if (layout == Layout.CLASSIC) authChoice = new Nodes.FormRow("row." + AUTH, ControlState.READY, "", "", authChoice, Optional.empty());
        var authentication = section("host.authentication", "Authentication", Nodes.CardKind.MIXED, List.of(
            authChoice,
            row("row." + CREDENTIAL,''', 1),
    ('''OptionalInt.of(Math.max(2, Math.min(3, keys.size())))))));''', '''OptionalInt.of(Math.max(2, Math.min(3, keys.size()))), "", "", "", "", "",
                layout == Layout.CLASSIC ? "No keys" : ""))));''', 1)])
R = "plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java"
edit(R, [
    ('''            host -> { if (!stopped && window.isOpen()) openHost(window, host); });''', '''            host -> { if (!stopped && window.isOpen()) openHost(window, host); });
        editor[0].layout(layout);''', 1)])
F = "jasper-app/src/test/java/dev/jasper/app/plugins/PanelSurfaceFixture.java"
edit(F, [
    ('''    /** Selects a host in the window's Sessions panel, as a click does (plan 10a). */''', '''    /** Runs one of the plugin's actions in the fixture window, as a menu or a shortcut does (plan 10a). */
    public void invoke(String actionId) throws InterruptedException { vault.invoke(actionId); StagedVaultHost.settle(); }

    /** The open dialog titled {@code title}, once it is shown. */
    public dev.jasper.app.windows.AuxiliarySurface surface(String title) throws InterruptedException { return vault.awaitSurface(title); }

    /**
     * A dialog's content at {@code width} by {@code height} logical pixels, painted at 2x over the theme's {@code
     * surface.dialog}. The headless windows run no shell, so the content takes the flags the Enterprise host gives a
     * dialog first: {@code hostTitled} (a FULL form under the native title) and {@code dialogBand} (6b's
     * {@code NativeShells.nativeMarks} under the classic mapping).
     */
    public BufferedImage renderSurface(dev.jasper.app.windows.AuxiliarySurface surface, int width, int height) {
        return onEdtValue(() -> {
            var holder = surface.holder();
            for (var child : holder.getComponents())
                if (child instanceof KitView content && !content.isClosed()) { content.hostTitled(true); content.dialogBand(true); }
            holder.setSize(width, height);
            for (int i = 0; i < 3; i++) SessionsSurfaceFixture.layout(holder);
            var image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics(); try { graphics.scale(2, 2); holder.printAll(graphics); } finally { graphics.dispose(); }
            return dev.jasper.app.testsupport.MockMatch.onto(image, theme.workspace().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_DIALOG));
        });
    }

    /** Selects a host in the window's Sessions panel, as a click does (plan 10a). */''', 1)])
PY
```

`HostEditor`'s `ControlState`, `Optional`, `Option` and `Nodes` imports exist (fact 14). `StagedVaultHost.invoke` and
`awaitSurface` are package-private members the fixture can call (it is in their package).

- [ ] **Step 4: Run the tests and match the mocks**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.HostEditorTest' --tests 'dev.jasper.remote.ui.HostCompositionTest' --tests 'dev.jasper.remote.ui.tunnels.*' :jasper-app:test --tests 'dev.jasper.app.windows.ClassicPlacementTest' --tests 'dev.jasper.app.plugins.ClassicCompositionRenderTest' --tests 'dev.jasper.app.plugins.StagedAddHostTest' --tests 'dev.jasper.app.plugins.FileServerRenderEvidenceTest' --tests 'dev.jasper.app.plugins.VaultRenderEvidenceTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: the new cases and the editor suites unchanged, `ClassicPlacementTest` unchanged (6b's `nativeMarks`),
`ClassicCompositionRenderTest` (5), and the default-look dialog evidence renders unchanged (this task changes no host
code). Render triage: the 44 px footer band and its rule are 6b's `Forms` band (`dialogBand`: `surface.footer` under
`border.window`, `DIALOG_FOOTER_VERTICAL` and `FORM_SIDES`), turned on for these native dialogs by 6b's
`NativeShells.nativeMarks` (and by `renderSurface` in the harness); the group boxes, colon labels, radios and checkboxes are 4b's faces; the keys box and its side column are 4c's ordered face; the
route strip is 4c's route face. The mocks' field values differ only in the fixture's names; record the agreements.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote jasper-app/src/test
git commit -F - <<'EOF'
Plugin compositions: Add Host's keys and New Tunnel against the mocks

Under the Enterprise look Add Host's keys list reads "No keys" while it
is empty and its Authentication choice sits in the control column; New
Tunnel needs no composition change. Both dialogs render in the real host under the shipped kit and match
the Add Host and New Tunnel mocks.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 10: The File Servers table and Properties

Spec 14.5: under CLASSIC a Table of Folder (the name and a protocol Chip), Address (data face) and Status (a status dot
and word), at LEFT with `withSplit` beneath SFTP; activating a row starts or stops the server; the footer reads "<name> —
stopped. Click to toggle."; the context menu keeps every action (Copy Address reads Copy URI, Show Details reads Show
Activity, Rulings 10); the chosen server's details are Properties under the table; with no servers an Empty "No File
Servers" with [Share Folder…] under the kept headers; a broken file is a Notice.

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/FileServersPanel.java`, `FileServerPlugin.java`
- Create: `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/FileServersTableTest.java`
- Modify: `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/UiTestAccess.java` (`find`, used here and by Task 12)
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicCompositionRenderTest.java`, `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Task 1's `Layout`; Task 3's classic Properties; 3c's cell nodes and `Table.empty`; 3b's `Notice`.
- Produces: `public FileServersPanel(UiKit, Actions, Function<UUID, List<ActivityLog.Entry>>, Path, String, ZoneId, String,
  Layout)`; `static final List<Column> FileServersPanel.COLUMNS`; ids `PROPERTIES = "fileserver.properties"`, `FOOTER =
  "fileserver.footer"`, `NOTICE = "fileserver.notice"`; the table keeps `fileserver.rows`, `server:<uuid>` rows and
  `ENTER_TOGGLE` as its activate action.

- [ ] **Step 1: Write the failing tests**

Create `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/FileServersTableTest.java`:

```java
package dev.jasper.fileserver.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState;
import dev.jasper.fileserver.workstation.Layout;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 14.5: the classic File Servers panel is a table over Properties, with the rows' gestures. */
class FileServersTableTest {
    static final Path HOME = Path.of(System.getProperty("java.io.tmpdir"), "fileserver-fixture");
    final List<String> events = new ArrayList<>();
    final ServerRegistry.View test = new ServerRegistry.View(ServerDefinition.create("test", Protocol.HTTP, HOME.resolve("test")), false,
        ServerState.STOPPED, List.of(URI.create("http://127.0.0.1:8080")), Optional.empty(), false);

    FileServersPanel panel(List<ServerRegistry.View> servers, Optional<String> problem) {
        var result = new java.util.concurrent.atomic.AtomicReference<FileServersPanel>();
        UiTestAccess.edt(() -> {
            var panel = new FileServersPanel(new FakeKit("dev.jasper.fileserver"), new FileServersPanel.Actions(
                id -> events.add("toggle"), id -> events.add("edit " + id.map(any -> "test").orElse("new")), id -> events.add("duplicate"),
                id -> events.add("delete"), id -> events.add("reveal"), text -> events.add("copy " + text), id -> events.add("password"),
                id -> events.add("clear"), id -> events.add("fingerprints")), id -> List.of(), HOME, "", ZoneOffset.UTC, "add", Layout.CLASSIC);
            panel.setServers(servers, problem);
            result.set(panel);
        });
        return result.get();
    }

    static Nodes.Node node(FileServersPanel panel, String id) { return UiTestAccess.node((FakeKitView) panel.view(), id); }
    static Nodes.Table table(FileServersPanel panel) { return (Nodes.Table) node(panel, FileServersPanel.ROWS); }
    static void emit(FileServersPanel panel, UiEvent event) { UiTestAccess.edt(() -> ((FakeKitView) panel.view()).emit(event)); }
    static String action(List<MenuItem> menu, String label) { return menu.stream().filter(item -> item.label().equals(label)).findFirst().orElseThrow().action(); }

    @Test void theTableShowsFolderAddressAndStatusAndTheFooter() {
        var panel = panel(List.of(test), Optional.empty());
        var table = table(panel);
        assertThat(table.columns()).extracting(column -> column.title()).containsExactly("Folder", "Address", "Status");
        var row = table.rows().getFirst();
        assertThat(row.cells()).containsEntry("address", "127.0.0.1:8080");
        var folder = (Nodes.Stack) row.cellNodes().get("folder");
        assertThat(folder.children()).extracting(child -> child.getClass().getSimpleName()).containsExactly("Text", "Chip");
        assertThat(((Nodes.Chip) folder.children().get(1)).label()).isEqualTo("HTTP");
        var status = (Nodes.Stack) row.cellNodes().get("status");
        assertThat(((Nodes.StatusDot) status.children().getFirst()).kind()).isEqualTo(Nodes.StatusKind.IDLE);
        assertThat(((Nodes.Text) status.children().get(1)).text()).isEqualTo("Stopped");
        assertThat(((Nodes.Text) node(panel, FileServersPanel.FOOTER)).text()).isEqualTo("test — stopped. Click to toggle.");
        assertThat(table.selectedIds()).as("only a chosen server is selected").isEmpty();
        assertThat(UiTestAccess.find((FakeKitView) panel.view(), FileServersPanel.PROPERTIES)).isEmpty();
    }

    @Test void activationTogglesAndTheChosenServerHasItsPropertiesAndActivity() {
        var panel = panel(List.of(test), Optional.empty());
        emit(panel, new UiEvent.Selected(FileServersPanel.ROWS, Set.of("server:" + test.definition().id())));
        UiTestAccess.edt(() -> ((FakeKitView) panel.view()).emit(new UiEvent.Action(table(panel).activateAction())));
        assertThat(events).containsExactly("toggle");
        var properties = (Nodes.Properties) node(panel, FileServersPanel.PROPERTIES);
        assertThat(properties.title()).isEqualTo("Properties — test");
        assertThat(properties.rows()).extracting(Nodes.PropertyRow::label).contains("Folder", "Protocol", "Port", "Access", "Reachable from", "Address");
        var menu = table(panel).rows().getFirst().contextActions().orElseThrow();
        assertThat(menu).extracting(MenuItem::label).contains("Start", "Edit…", "Copy URI", "Show Fingerprints…", "Show Activity");
        emit(panel, new UiEvent.Action(action(menu, "Copy URI")));
        emit(panel, new UiEvent.Action(action(menu, "Show Activity")));
        assertThat(events).contains("copy http://127.0.0.1:8080");
        assertThat(UiTestAccess.find((FakeKitView) panel.view(), "fileserver.activity.log")).as("the activity log under the Properties").isPresent();
        emit(panel, new UiEvent.Action("fileserver.activity.clear"));
        assertThat(events).last().isEqualTo("clear");
    }

    @Test void noServersIsTheEmptyStateUnderTheHeadersAndABrokenFileIsANotice() {
        var panel = panel(List.of(), Optional.of("line 2: bad port"));
        assertThat(table(panel).empty()).hasValueSatisfying(empty -> {
            assertThat(empty.title()).isEqualTo("No File Servers");
            assertThat(((Nodes.Button) empty.actions().getFirst()).label()).isEqualTo("Share Folder…");
        });
        assertThat(((Nodes.Notice) node(panel, FileServersPanel.NOTICE)).severity()).isEqualTo(Nodes.Severity.DANGER);
        emit(panel, new UiEvent.Action("fileserver.empty.new"));
        assertThat(events).containsExactly("edit new");
    }
}
```

`UiTestAccess.find` is added in Step 3 (the bridge has `node`, which returns null for a missing id);
`ServerRegistry.View`'s components are `(definition, temporary, state, addresses, user, restartNeeded)`
(`FileServersPanelTest.server`).

Add to `ClassicCompositionRenderTest`:

```java
    static final String FILE_SERVERS_LIGHT = "0+20 #F0F1F3, 20+1 #D3D6DA, 21+335 #FFFFFF";
    static final String FILE_SERVERS_DARK = "0+20 #393C41, 20+1 #4A4D53, 21+335 #2E3135";

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theFileServersTableMatchesTheWorkstationMocks() throws Exception {
        for (boolean dark : new boolean[] {false, true}) under(dark, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("servers-" + dark), EnterpriseLook.resolved(dark), PanelSurfaceFixture.FILE_SERVERS, true)) {
                fixture.fileServer("test", "HTTP");
                var crop = new MockMatch.Crop(dark ? DARK : CONNECTED, 48, 994, 600, 712);
                check((dark ? "dark" : "light") + "/file-servers", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 8,
                    dark ? FILE_SERVERS_DARK : FILE_SERVERS_LIGHT, 1, 0.55, dark ? new String[] {"#393C41", "#2E3135"} : new String[] {"#F0F1F3", "#2655AE", "#B9C8EA"});
            }
        });
        under(false, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("servers-none"), EnterpriseLook.resolved(false), PanelSurfaceFixture.FILE_SERVERS, true)) {
                var crop = new MockMatch.Crop(DISCONNECTED, 48, 994, 600, 712);
                check("light/file-servers-none", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 8, FILE_SERVERS_LIGHT, 1, 0.55,
                    "#1E1F22", "#5C6370", "#B4B8BF");
            }
        });
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-fileserver:compileTestJava`
Expected: FAIL to compile: "no suitable constructor found for FileServersPanel(…, Layout)", "cannot find symbol … FOOTER".

- [ ] **Step 3: Compose the classic File Servers panel**

```bash
python3 -I - <<'PY'
"""Plan 10a Task 10 step 3: the classic File Servers table and its Properties."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/FileServersPanel.java"
CLASSIC = '''    // ---- the classic composition (Enterprise presentation spec 14.5)

    /** The classic table's columns, in the Workstation mock's proportions. */
    static final List<Column> COLUMNS = List.of(new Column("folder", "Folder", Nodes.TextRole.BODY, false, 110, Optional.empty()),
        new Column("address", "Address", Nodes.TextRole.MONO, true, 117, Optional.empty()), new Column("status", "Status", Nodes.TextRole.BODY, false, 73, Optional.empty()));
    public static final String PROPERTIES = "fileserver.properties", FOOTER = "fileserver.footer", NOTICE = "fileserver.notice";

    private Nodes.Panel classicNode() {
        var header = new Nodes.Header(HEADER, ControlState.READY, "File Servers", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
        var rows = new ArrayList<TableRow>();
        for (ServerRows.Row row : ServerRows.rows(servers, Optional.empty())) {
            switch (row) {
                case ServerRows.Header heading -> rows.add(TableRow.heading("fileserver.temporary", heading.title(), OptionalInt.empty(), ""));
                case ServerRows.Server server -> rows.add(tableRow(server.view()));
                case ServerRows.Problem ignored -> { }
            }
        }
        Optional<Nodes.Empty> empty = servers.isEmpty() ? Optional.of(new Nodes.Empty(EMPTY, ControlState.READY,
            new Nodes.Icon("fileserver.empty.icon", ControlState.READY, "server", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "File Servers"),
            "No File Servers", "Share a local folder over HTTP with devices on your network.",
            List.of(new Nodes.Button("fileserver.empty.new", ControlState.READY, "Share Folder…", "", Nodes.ButtonKind.SECONDARY, "fileserver.empty.new", ""))))
            : Optional.empty();
        var table = new Nodes.Table(ROWS, ControlState.READY, COLUMNS, rows, chosen == null ? Set.of() : Set.of(rowId(chosen)), ENTER_TOGGLE, List.of(),
            Nodes.SelectionMode.SINGLE, Nodes.ScrollPolicy.STANDARD, empty, "");
        Optional<Nodes.Node> top = problem.map(error -> new Nodes.Stack(NOTICE + ".stack", ControlState.READY, Nodes.LayoutRole.VERTICAL,
            List.of(new Nodes.Notice(NOTICE, ControlState.READY, Nodes.Severity.DANGER, "servers.toml: " + error, List.of()))));
        var below = new ArrayList<Nodes.Node>();
        selectedView().ifPresent(server -> below.add(new Nodes.Text(FOOTER, ControlState.READY,
            server.definition().name() + " — " + ServerRows.state(server.state()).toLowerCase(Locale.ROOT) + ". Click to toggle.", Nodes.TextRole.CAPTION)));
        if (chosen != null) selectedView().ifPresent(server -> {
            below.add(properties(server));
            if (detailsOpen) below.addAll(activity());
        });
        return new Nodes.Panel(ROOT, ControlState.READY, header, top, table,
            below.isEmpty() ? Optional.empty() : Optional.of(new Nodes.Stack("fileserver.below", ControlState.READY, Nodes.LayoutRole.VERTICAL, below)));
    }

    private TableRow tableRow(ServerRegistry.View server) {
        ServerDefinition definition = server.definition();
        UUID id = definition.id(); String prefix = "row." + id;
        var folder = new Nodes.Stack(prefix + ".folder", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(text(prefix + ".name", definition.name(), Nodes.TextRole.BODY),
            new Nodes.Chip(prefix + ".protocol", ControlState.READY, definition.protocol().label(), protocolHue(definition.protocol()))));
        var status = new Nodes.Stack(prefix + ".status", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.StatusDot(prefix + ".dot", ControlState.READY, dot(server.state().phase())), text(prefix + ".state", ServerRows.state(server.state()), Nodes.TextRole.BODY)));
        return new TableRow(rowId(id), Map.of("address", ServerRows.address(server)), Optional.empty(), false, Optional.of(classicMenu(server)),
            definition.name() + ", " + definition.protocol().label() + ", " + ServerRows.address(server) + ", " + tooltip(server),
            Map.of("folder", folder, "status", status), Optional.empty());
    }

    /** Running is success, a transition or a wait for the Vault a warning, a failure danger, stopped idle. */
    static Nodes.StatusKind dot(Phase phase) {
        return switch (phase) {
            case RUNNING -> Nodes.StatusKind.SUCCESS;
            case STARTING, STOPPING, WAITING_FOR_VAULT -> Nodes.StatusKind.WARNING;
            case FAILED -> Nodes.StatusKind.DANGER;
            case STOPPED -> Nodes.StatusKind.IDLE;
        };
    }

    /** The row menu of the default look, with Copy URI for Copy Address and Show Activity for Show Details (Rulings 10). */
    private List<MenuItem> classicMenu(ServerRegistry.View server) {
        UUID id = server.definition().id();
        var items = new ArrayList<MenuItem>();
        for (MenuItem item : menu(server)) {
            if (item.action().equals("menu:copy:" + id)) items.add(menuItem("copy", id, "Copy URI", item.enabled()));
            else if (item.action().equals("menu:details:" + id)) items.add(menuItem("details", id, detailsOpen ? "Hide Activity" : "Show Activity", true));
            else items.add(item);
        }
        return List.copyOf(items);
    }

    private Nodes.Node properties(ServerRegistry.View server) {
        ServerDefinition definition = server.definition();
        var rows = new ArrayList<Nodes.PropertyRow>();
        rows.add(new Nodes.PropertyRow("Folder", ServerRows.folder(definition.folder(), home), true));
        rows.add(new Nodes.PropertyRow("Protocol", definition.protocol().label(), false));
        rows.add(new Nodes.PropertyRow("Port", String.valueOf(definition.port()), true));
        rows.add(new Nodes.PropertyRow("Access", ServerRows.access(definition), false));
        rows.add(new Nodes.PropertyRow("Reachable from", definition.bind().label(), false));
        server.user().ifPresent(user -> rows.add(new Nodes.PropertyRow("User", user, true)));
        if (definition.tls()) rows.add(new Nodes.PropertyRow("TLS certificate", tlsFingerprint.orElse("Created when the server first starts"), tlsFingerprint.isPresent()));
        if (definition.protocol() == Protocol.SFTP)
            rows.add(new Nodes.PropertyRow("SSH host key", hostKeyFingerprint.orElse("Created when the server first starts"), hostKeyFingerprint.isPresent()));
        for (int i = 0; i < server.addresses().size(); i++)
            rows.add(new Nodes.PropertyRow(server.addresses().size() == 1 ? "Address" : "Address " + (i + 1), server.addresses().get(i).toString(), true));
        List<String> warnings = ServerRows.warnings(definition);
        if (!warnings.isEmpty()) rows.add(new Nodes.PropertyRow("Warning", String.join(" · ", warnings), false, Optional.of(Nodes.Severity.WARNING)));
        return new Nodes.Properties(PROPERTIES, ControlState.READY, "Properties — " + definition.name(), rows);
    }

    /** The default look's activity log and its Clear and Copy, shown by Show Activity. */
    private List<Nodes.Node> activity() {
        var activityRows = new ArrayList<ListItem>();
        for (int i = 0; i < activityLines.size(); i++) activityRows.add(new ListItem("fileserver.activity.line." + i,
            Optional.empty(), List.of(text("fileserver.activity.text." + i, activityLines.get(i), Nodes.TextRole.MONO_SMALL)), List.of(), List.of()));
        return List.of(text("fileserver.activity.title", "Activity", Nodes.TextRole.SECTION_LABEL),
            new Nodes.ItemList("fileserver.activity.log", ControlState.READY, Nodes.ListKind.COMPACT, activityRows, Set.of(), "", List.of(),
                Nodes.SelectionMode.NONE, Optional.empty(), OptionalInt.of(8), "", ""),
            new Nodes.Stack("fileserver.activity.actions", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
                new Nodes.Button("fileserver.activity.clear", ControlState.READY, "Clear", "remove", Nodes.ButtonKind.GHOST, "fileserver.activity.clear", ""),
                new Nodes.Button("fileserver.activity.copy", ControlState.READY, "Copy", "copy", Nodes.ButtonKind.GHOST, "fileserver.activity.copy", ""))));
    }

    private Nodes.Node details(ServerRegistry.View server) {'''
edit(P, [
    ('''import dev.jasper.fileserver.runtime.ServerState.Phase;\n''', '''import dev.jasper.fileserver.runtime.ServerState.Phase;\nimport dev.jasper.fileserver.workstation.Layout;\nimport dev.jasper.sdk.ui.Column;\n''', 1),
    ('''import dev.jasper.sdk.ui.PanelHeader;\n''', '''import dev.jasper.sdk.ui.PanelHeader;\nimport dev.jasper.sdk.ui.TableRow;\n''', 1),
    ('''import java.util.Locale;\n''', '''import java.util.Locale;\nimport java.util.Map;\n''', 1),
    ('''    private final String headerAddIcon;\n''', '''    private final String headerAddIcon;\n    /** Which composition this panel takes, fixed at construction (spec 3.6). */\n    private final Layout layout;\n''', 1),
    ('''                            Path home, String firewallHint, ZoneId zone, String headerAddIcon) {
        this.actions = actions;''', '''                            Path home, String firewallHint, ZoneId zone, String headerAddIcon) {
        this(kit, actions, log, home, firewallHint, zone, headerAddIcon, Layout.ROWS);
    }

    /** The panel in {@code layout}'s composition. */
    public FileServersPanel(UiKit kit, Actions actions, Function<UUID, List<ActivityLog.Entry>> log,
                            Path home, String firewallHint, ZoneId zone, String headerAddIcon, Layout layout) {
        this.layout = java.util.Objects.requireNonNull(layout);
        this.actions = actions;''', 1),
    ('''    private Nodes.Panel node() {
        var header = new Nodes.Header(HEADER, ControlState.READY, "File Servers",''', '''    private Nodes.Panel node() { return layout == Layout.CLASSIC ? classicNode() : rowsNode(); }

    private Nodes.Panel rowsNode() {
        var header = new Nodes.Header(HEADER, ControlState.READY, "File Servers",''', 1),
    ('''    private Nodes.Node details(ServerRegistry.View server) {''', CLASSIC, 1)])
edit("plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/UiTestAccess.java", [
    ('''    public static Nodes.Node node(FakeKitView view, String id) {''',
     '''    /** The node {@code id} of the view's current spec, or empty when the view has none. */
    public static java.util.Optional<Nodes.Node> find(FakeKitView view, String id) { return java.util.Optional.ofNullable(node(view, id)); }
    public static Nodes.Node node(FakeKitView view, String id) {''', 1)])
F = "plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java"
edit(F, [
    ('''            "add");
        panel.onHeaderChanged(host::setHeader);''', '''            "add", layout);
        panel.onHeaderChanged(host::setHeader);''', 1),
    ('''        context.panels().register(new PanelSpec(PANEL, "File Servers", "server", Anchor.LEFT).withToggleActionId(SHOW), this::createPanel);''',
     '''        // Enterprise's default slots (spec 14.5): File Servers beneath SFTP on the left.
        context.panels().register(layout == dev.jasper.fileserver.workstation.Layout.CLASSIC
            ? new PanelSpec(PANEL, "File Servers", "server", Anchor.LEFT).withToggleActionId(SHOW).withSplit()
            : new PanelSpec(PANEL, "File Servers", "server", Anchor.LEFT).withToggleActionId(SHOW), this::createPanel);''', 1)])
PY
```

The panel's `text`, `menu`, `menuItem`, `rowId`, `tooltip`, `protocolHue`, `selectedView`, `chosen`, `detailsOpen`,
`activityLines`, `problem`, `home`, `tlsFingerprint` and `hostKeyFingerprint` are its own (fact 12); `menuItem(verb, id,
label, enabled)` makes `menu:<verb>:<id>` actions, so Copy URI and Show Activity run the default look's verbs.

Add to `plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java`'s
`aStartUnderEnterpriseReadsTheClassicLayoutOnce` (Task 1), after its last assertion:

```java
            assertThat(host.panels()).contains("dev.jasper.fileserver.panel|File Servers|LEFT+split|dev.jasper.fileserver.show-panel");
```

(the toggle is `FileServerPlugin.SHOW`, `dev.jasper.fileserver.show-panel`, as the class's existing panel assertion prints it,
`FileServerPluginTest.java:201`).

Add the ledger row of `FileServersTableTest` (it matches `new …Panel`):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv"); text = path.read_text(encoding="utf-8")
file = "plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/FileServersTableTest.java"
if file in text: raise SystemExit("already ledgered")
path.write_text(text + f"{file},dev.jasper.fileserver,FileServersTableTest,nonvisual-helper,none,none,{file},Test/fixture ownership; New in Enterprise plan 10a Task 10\n", encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests and match the mocks**

Run: `./gradlew :jasper-plugin-fileserver:test :jasper-app:test --tests 'dev.jasper.app.plugins.ClassicCompositionRenderTest' --tests 'dev.jasper.app.plugins.FileServerRenderEvidenceTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: the whole File Server suite (the rows composition unchanged), `FileServersTableTest` (3), the renders (6).
Render triage: the header strip above the crop is cut by Task 2's `render` (plan 7 owns it); the header band and rule
are 4c's table face; the HTTP tag is 4c's classic tag face (`#2655AE` on its tint
in a `tag.border.blue` box); the address's right alignment is the numeric column; the empty state is 4c's face.

- [ ] **Step 5: Commit**

```bash
git add plugins/fileserver jasper-app/src/test docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Plugin compositions: the File Servers table and Properties

Under the Enterprise look the File Servers panel is a table of Folder
(name and protocol tag), Address and Status (a dot and word) beneath
SFTP; activating a row starts or stops the server, the footer names its
state, the chosen server's details are Properties, Show Activity shows
its log, a broken file is a Notice, and with no servers the table keeps
its headers over "No File Servers" and Share Folder….

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 11: New File Server's authorized keys table, against its mocks

Spec 14.5: in New File Server under CLASSIC the authorized keys become a Table (Type, Fingerprint, Comment) with the
empty text "No keys added" and the existing ACTIONS row. The four protocol mocks are rendered against the real editor.

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/ServerEditor.java`, `FileServerPlugin.java` (`edit`),
  `jasper-app/src/test/java/dev/jasper/app/plugins/PanelSurfaceFixture.java` (`dispatch`)
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/ServerEditorTest.java` (one case),
  `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicCompositionRenderTest.java`

**Interfaces:**
- Consumes: Task 1's `Layout`; Task 9's `PanelSurfaceFixture.invoke`, `surface`, `renderSurface`; `HostedKitView.dispatch`.
- Produces: `public ServerEditor ServerEditor.layout(Layout)`; `static final List<Column> ServerEditor.KEY_COLUMNS`; the keys
  table keeps `server.keys` and the `key.<i>` row ids; `PanelSurfaceFixture.dispatch(String pluginField, UiEvent)`.

- [ ] **Step 1: Write the failing tests**

Add to `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/ServerEditorTest.java`:

```java
    @Test void underClassicTheAuthorizedKeysAreATable() {
        var events = new java.util.ArrayList<String>();
        var holder = new java.util.concurrent.atomic.AtomicReference<ServerEditor>();
        UiTestAccess.edt(() -> holder.set(new ServerEditor(new dev.jasper.sdk.testing.FakeKit("dev.jasper.fileserver"), java.util.List.of(),
            java.util.Optional.empty(), false, java.util.Set.of(dev.jasper.fileserver.definition.Protocol.SFTP), java.util.List.of(), false,
            id -> java.util.Optional.empty(), () -> java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty()),
            java.util.Optional::empty, new ServerEditor.KeySources(() -> java.util.Optional.of(ED25519), java.util.Optional::empty), false,
            definition -> events.add("save"), () -> events.add("cancel")).layout(dev.jasper.fileserver.workstation.Layout.CLASSIC)));
        var editor = holder.get();
        var view = (dev.jasper.sdk.testing.FakeKitView) editor.view();
        var keys = (dev.jasper.sdk.ui.Nodes.Table) UiTestAccess.node(view, ServerEditor.KEYS);
        assertThat(keys.columns()).extracting(column -> column.title()).containsExactly("Type", "Fingerprint", "Comment");
        assertThat(keys.emptyText()).isEqualTo("No keys added");
        UiTestAccess.edt(() -> view.emit(new dev.jasper.sdk.ui.UiEvent.Action(ServerEditor.ADD_KEY)));
        keys = (dev.jasper.sdk.ui.Nodes.Table) UiTestAccess.node(view, ServerEditor.KEYS);
        assertThat(keys.rows()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo("key.0");
            assertThat(row.cells()).containsEntry("type", "ssh-ed25519").containsEntry("comment", "alice@laptop");
            assertThat(row.cells().get("fingerprint")).startsWith("SHA256:");
        });
        UiTestAccess.edt(() -> view.emit(new dev.jasper.sdk.ui.UiEvent.Selected(ServerEditor.KEYS, java.util.Set.of("key.0"))));
        UiTestAccess.edt(() -> view.emit(new dev.jasper.sdk.ui.UiEvent.Action(ServerEditor.REMOVE_KEY)));
        assertThat(((dev.jasper.sdk.ui.Nodes.Table) UiTestAccess.node(view, ServerEditor.KEYS)).rows()).isEmpty();
    }
```

The class already declares the `ED25519` line the case uses (`ServerEditorTest.java:26`). If 3e's folder change or 3b's
protocol change made the 14-argument constructor take other arguments, build the editor as `ServerEditorTest`'s own
factory does and call `layout` on it.

Add to `ClassicCompositionRenderTest`:

```java
    static final String FS_KEYS = "0+2 #ECEDEF, 2+1 #B4B8BF, 3+20 #F0F1F3, 23+1 #D3D6DA, 24+44 #FFFFFF, 68+1 #B4B8BF, 69+3 #ECEDEF";

    @Test @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void newFileServerMatchesItsFourMocks() throws Exception {
        under(false, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("new-server"), EnterpriseLook.resolved(false), PanelSurfaceFixture.FILE_SERVERS, true)) {
                fixture.invoke("dev.jasper.fileserver.new");
                var editor = fixture.surface("New File Server");
                record Mock(String protocol, String file, int height, int body) { }
                for (var mock : java.util.List.of(new Mock("HTTP", "HTTP", 986, 448), new Mock("FTP", "FTP", 1210, 560),
                        new Mock("SFTP", "SFTP", 1172, 541), new Mock("TFTP", "TFTP", 862, 386))) {
                    fixture.dispatch("currentEditor", new dev.jasper.sdk.ui.UiEvent.Selected("server.protocol", java.util.Set.of(mock.protocol())));
                    var crop = new MockMatch.Crop("02 New File Server/New File Server - " + mock.file() + ".png", 2, 60, 1040, mock.height());
                    var render = fixture.renderSurface(editor, crop.logicalWidth(), crop.logicalHeight());
                    check("light/new-file-server-" + mock.file().toLowerCase(java.util.Locale.ROOT), crop, render, 18,
                        "0+" + mock.body() + " #ECEDEF, " + mock.body() + "+1 #C4C7CC, " + (mock.body() + 1) + "+44 #E6E7EA", 1, 0.55, "#ECEDEF", "#E6E7EA", "#B4B8BF");
                    if (mock.protocol().equals("SFTP"))
                        assertThat(MockMatch.mismatch(MockMatch.parse(FS_KEYS), MockMatch.bands(render, 98, 595, 740), 1)).as("the keys table").isEmpty();
                }
            }
        });
    }
```

The keys band reads mock column 100 from y 655 (crop x 98, crop rows 595 to 740).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-fileserver:compileTestJava :jasper-app:compileTestJava --continue`
Expected: FAIL to compile: "cannot find symbol … layout(Layout)" and "… dispatch(String,UiEvent)".

- [ ] **Step 3: The classic keys table and the fixture's dispatch**

```bash
python3 -I - <<'PY'
"""Plan 10a Task 11 step 3: New File Server's classic keys table."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
E = "plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/ServerEditor.java"
edit(E, [
    ('''import dev.jasper.fileserver.engine.ServerAddresses;\n''', '''import dev.jasper.fileserver.engine.ServerAddresses;\nimport dev.jasper.fileserver.workstation.Layout;\nimport dev.jasper.sdk.ui.Column;\nimport dev.jasper.sdk.ui.TableRow;\n''', 1),
    ('''    private final UiView view;\n''', '''    private final UiView view;\n    /** Which composition the form takes; set once by {@link #layout} before the dialog shows (Rulings 3). */\n    private Layout layout = Layout.ROWS;\n''', 1),
    ('''    private void render() { if (!closed) view.update(new ViewSpec(form())); }''', '''    private void render() { if (!closed) view.update(new ViewSpec(form())); }

    /** Composes the form in {@code value}'s layout (Enterprise presentation spec 14.5); call once, right after construction. */
    public ServerEditor layout(Layout value) { layout = java.util.Objects.requireNonNull(value); render(); return this; }

    /** The classic keys table's columns, in the New File Server mock's proportions. */
    static final List<Column> KEY_COLUMNS = List.of(new Column("type", "Type", Nodes.TextRole.MONO_SMALL, false, 87, Optional.empty()),
        new Column("fingerprint", "Fingerprint", Nodes.TextRole.MONO_SMALL, false, 247, Optional.empty()),
        new Column("comment", "Comment", Nodes.TextRole.BODY, false, 128, Optional.empty()));

    /** The authorized keys: today's compact list, or under CLASSIC a table of their type, fingerprint and comment. */
    private Nodes.Node keysNode(List<ListItem> keys) {
        if (layout == Layout.ROWS) return new Nodes.ItemList(KEYS, ControlState.READY, Nodes.ListKind.COMPACT, keys, selectedKeys, "", List.of(),
            Nodes.SelectionMode.MULTIPLE, Optional.empty(), OptionalInt.of(3));
        var rows = new ArrayList<TableRow>();
        for (int i = 0; i < keyLines.size(); i++) {
            String type = "", fingerprint = keyLines.get(i), comment = "";
            try { var key = AuthorizedKeys.parse(keyLines.get(i)); type = key.type(); fingerprint = key.fingerprint(); comment = key.comment(); }
            catch (IllegalArgumentException unreadable) { /* the stored line shows whole */ }
            rows.add(new TableRow("key." + i, java.util.Map.of("type", type, "fingerprint", fingerprint, "comment", comment), Optional.empty()));
        }
        return new Nodes.Table(KEYS, ControlState.READY, KEY_COLUMNS, rows, selectedKeys, "", List.of(), Nodes.SelectionMode.MULTIPLE,
            Nodes.ScrollPolicy.STANDARD, Optional.empty(), "No keys added");
    }''', 1),
    ('''            new Nodes.ItemList(KEYS, ControlState.READY, Nodes.ListKind.COMPACT, keys, selectedKeys, "", List.of(),
                Nodes.SelectionMode.MULTIPLE, Optional.empty(), OptionalInt.of(3)),''', '''            keysNode(keys),''', 1)])
F = "plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java"
edit(F, [('''        currentEditor = editor[0];\n        dialog.setContent(editor[0].view());''',
          '''        editor[0].layout(layout);\n        currentEditor = editor[0];\n        dialog.setContent(editor[0].view());''', 1)])
P = "jasper-app/src/test/java/dev/jasper/app/plugins/PanelSurfaceFixture.java"
edit(P, [('''    /** Runs one of the plugin's actions in the fixture window, as a menu or a shortcut does (plan 10a). */''',
          '''    /** Delivers {@code event} to the view of the plugin's {@code pluginField} (a form it keeps), as its kit would (plan 10a). */
    public void dispatch(String pluginField, dev.jasper.sdk.ui.UiEvent event) throws InterruptedException {
        onEdt(() -> {
            try {
                Object form = field(plugin, pluginField);
                var view = (HostedKitView) form.getClass().getMethod("view").invoke(form);
                view.dispatch(event);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        StagedVaultHost.settle();
    }

    /** Runs one of the plugin's actions in the fixture window, as a menu or a shortcut does (plan 10a). */''', 1)])
PY
```

If `dialog.setContent(editor[0].view());` follows `currentEditor = editor[0];` in another form on the branch, put the
`layout` call right after the editor's construction. `HostedKitView.dispatch(UiEvent)` is the package-private delivery the
host's kit uses; the fixture is in its package.

- [ ] **Step 4: Run the tests and match the mocks**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.ui.*' :jasper-app:test --tests 'dev.jasper.app.plugins.ClassicCompositionRenderTest' --tests 'dev.jasper.app.plugins.StagedFileServerFormTest' --continue`
Expected: PASS: the editor suites (the rows list unchanged), the new case, the renders (7). Render triage: the form's
groups, radios with swatches and colon labels are plan 4b's faces; the footer band and its rule are 6b's `Forms` band,
turned on for this native dialog by 6b's `NativeShells.nativeMarks` (and by `renderSurface` in the harness); the keys table box, header band and empty
line are 4c's table face and 3c's empty line. The mocks' "Authorized keys:" colon is a fixture (spec 14.5).

- [ ] **Step 5: Commit**

```bash
git add plugins/fileserver jasper-app/src/test
git commit -F - <<'EOF'
Plugin compositions: New File Server's authorized keys table

Under the Enterprise look New File Server lists authorized keys as a
table of Type, Fingerprint and Comment, reading "No keys added" while it
is empty, above the same Add from File…, Paste and Remove. The real
editor renders under the shipped kit and matches the HTTP, FTP, SFTP and
TFTP mocks.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 12: `CompositionParityTest` for Remote and File Server

Spec 14.7 and 16.1.9: every default-look gesture keeps a classic path. Each test lists the gestures of its plugin with
both paths and checks that they have the same effect (Rulings 13).

**Files:**
- Create: `plugins/remote/src/test/java/dev/jasper/remote/CompositionParityTest.java`,
  `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/CompositionParityTest.java`
- Modify: `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Tasks 4 to 11's classic compositions and their ids; `SessionsToolbar`'s two layouts (Task 5);
  `SftpPanel(UiKit, Layout)`, `CHOOSE_DROPDOWN` (Task 7); `TransfersPanel(UiKit, TransferStrip)` (Task 8); the SDK
  `Nodes.Table.emptyText()` (3c).
- Produces: none (the tests).

- [ ] **Step 1: Write the tests**

Create `plugins/remote/src/test/java/dev/jasper/remote/CompositionParityTest.java`:

```java
package dev.jasper.remote;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.HostStore;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.tunnels.Counters;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.remote.ui.HostRows;
import dev.jasper.remote.ui.HostsPanel;
import dev.jasper.remote.ui.sftp.SftpPanel;
import dev.jasper.remote.ui.transfers.TransferRows;
import dev.jasper.remote.ui.transfers.TransferStrip;
import dev.jasper.remote.ui.transfers.TransfersPanel;
import dev.jasper.remote.ui.tunnels.TunnelsPanel;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TableRow;
import dev.jasper.sdk.ui.TreeItem;
import dev.jasper.sdk.ui.UiEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Spec 14.7: each default-look gesture of Remote has a classic path with the same effect. A gesture runs once on the
 * rows composition and once on the classic one, each on fresh panels recording what the plugin is asked to do: the
 * Sessions, Tunnels and SFTP panels, and the window's transfer rows, which the default look shows in the SFTP footer and
 * the classic one in the Transfers panel.
 */
class CompositionParityTest {
    @TempDir Path dir;
    final RemoteHost mac = RemoteHost.create("Mac", "192.168.1.119", 22, "dustin", Auth.AGENT, "Local Network", Optional.empty());
    final ShellProfileInfo ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));
    final Tunnel db = Tunnel.create("db", mac.id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
    /** A quick forward (an empty name): its menu offers Save as Tunnel… in place of Edit…. */
    final Tunnel forward = Tunnel.create("", mac.id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 8080, "localhost", 80);
    static final FileEntry FOLDER = new FileEntry("Applications", FileEntry.Kind.DIRECTORY, 0, 1000, 0755, ""),
        FILE = new FileEntry("notes.toml", FileEntry.Kind.FILE, 6, 1000, 0644, "");
    /** A running upload, and a finished download whose row offers Resume. */
    final UUID running = UUID.randomUUID(), finished = UUID.randomUUID();

    /** One composition under test: its panels and what they asked for. */
    final class Compositions {
        final List<String> events = new ArrayList<>();
        final Map<UUID, TunnelState> states = new java.util.HashMap<>();
        final HostsPanel sessions;
        final TunnelsPanel tunnels;
        final SftpPanel sftp;
        final TransferStrip strip;
        /** The classic Transfers panel over {@link #strip}; null under ROWS, where the SFTP footer shows the rows. */
        final TransfersPanel transfers;
        Compositions(Layout layout) {
            sessions = new HostsPanel(new FakeKit("dev.jasper.remote"), new HostsPanel.Actions(h -> events.add("connect " + h.name()),
                h -> events.add("split " + h.name()), h -> events.add("edit " + h.map(RemoteHost::name).orElse("new")), h -> events.add("duplicate " + h.name()),
                h -> events.add("delete " + h.name()), (h, favorite) -> events.add("favorite " + h.name() + " " + favorite), () -> events.add("import")), layout);
            sessions.onDistros(profile -> events.add("open " + profile.name()), profile -> events.add("open-split " + profile.name()));
            sessions.onBrowseFiles(h -> events.add("browse " + h.name()));
            sessions.setDistros(List.of(ubuntu), profile -> 0);
            sessions.setHosts(List.of(mac), Optional.empty());
            tunnels = new TunnelsPanel(new FakeKit("dev.jasper.remote"), new TunnelsPanel.Actions(t -> events.add("start " + t.name()),
                t -> events.add("stop " + t.name()), t -> events.add("edit " + t.name()), t -> events.add("duplicate " + t.name()),
                t -> events.add("delete " + t.name()), t -> events.add("save " + t.name()), t -> events.add("browse " + t.name()),
                t -> events.add("copy " + t.name()), () -> events.add("new tunnel")), layout);
            tunnels.setTunnels(List.of(db), List.of(forward), Optional.empty(), id -> states.getOrDefault(id, TunnelState.STOPPED), id -> Optional.of("Mac"));
            sftp = new SftpPanel(new FakeKit("dev.jasper.remote"), layout);
            sftp.actions(new SftpPanel.Actions(path -> events.add("navigate " + path), () -> events.add("up"), () -> events.add("refresh"),
                follow -> events.add("follow " + follow), () -> events.add("upload"), () -> events.add("upload folder"), () -> events.add("download"),
                () -> events.add("new folder"), () -> events.add("delete"), () -> events.add("copy paths"), () -> events.add("copy to host"),
                () -> events.add("cancel"), page -> events.add("page " + page), shown -> events.add("hidden " + shown)));
            sftp.navigation(() -> events.add("back"), () -> events.add("forward"));
            sftp.onChooseSession(h -> events.add("choose " + h.name()));
            sftp.sessions(List.of(mac));
            strip = new TransferStrip(new TransferStrip.Actions((id, action) -> events.add(action + " " + job(id)),
                id -> events.add("cancel " + job(id)), id -> events.add("dismiss " + job(id))));
            strip.rows(List.of(
                new TransferRows.Row(running, "↑", "report.pdf → Mac:/srv", "Local: /tmp/report.pdf → dustin@Mac: /srv", "Uploading",
                    OptionalDouble.of(0.4), false, Optional.empty(), false, false),
                new TransferRows.Row(finished, "↓", "notes.toml → Local", "dustin@Mac: /srv/notes.toml → Local: /tmp", "Interrupted",
                    OptionalDouble.empty(), false, Optional.of(TransferRows.Action.RESUME), true, false)));
            if (layout == Layout.ROWS) { sftp.transfers(strip); transfers = null; }
            else transfers = new TransfersPanel(new FakeKit("dev.jasper.remote"), strip);
        }
        String job(UUID id) { return id.equals(running) ? "running" : "finished"; }
        /** A listed folder, as a connected sidebar shows it: the first entry selected, history both ways. */
        void browse() { sftp.showPage("Mac", "/Users/dustin", List.of(FOLDER, FILE), 0, 2, false); sftp.navigationAvailable(true, true); }
        /** A folder longer than one page, shown from its second page. */
        void paged() { sftp.showPage("Mac", "/Users/dustin", List.of(FILE), 200, 450, false); }
        /** Where the window's transfer rows are: the SFTP sidebar's footer, or the classic Transfers panel. */
        dev.jasper.sdk.ui.UiView transferView() { return transfers == null ? sftp.view() : transfers.view(); }
        /** The SFTP item labelled {@code label} in the Upload menu, the first row's menu or the folder's own menu. */
        void sftpMenu(String where, String label) {
            var table = node(sftp.view(), SftpPanel.FILES, Nodes.Table.class);
            List<MenuItem> items = switch (where) {
                case "upload" -> node(sftp.view(), SftpPanel.UPLOAD, Nodes.MenuButton.class).items();
                case "row" -> table.rows().getFirst().contextActions().orElseThrow();
                default -> table.contextActions();
            };
            press(sftp.view(), items.stream().filter(item -> item.label().equals(label)).findFirst().orElseThrow().action());
        }
        void selectHost(String row) { emit(sessions.view(), new UiEvent.Selected(HostsPanel.ROWS, Set.of(row))); }
        void selectTunnel() { emit(tunnels.view(), new UiEvent.Selected(TunnelsPanel.ROWS, Set.of("tunnel:" + db.id()))); }
        List<MenuItem> hostMenu(String row) {
            var node = node(sessions.view(), HostsPanel.ROWS, Nodes.Node.class);
            if (node instanceof Nodes.ItemList list) return list.items().stream().filter(item -> item.id().equals(row)).findFirst().flatMap(ListItem::contextActions).orElseThrow();
            return leaf(((Nodes.Tree) node).roots(), row).flatMap(TreeItem::contextActions).orElseThrow();
        }
        List<MenuItem> tunnelMenu() {
            var node = node(tunnels.view(), TunnelsPanel.ROWS, Nodes.Node.class);
            if (node instanceof Nodes.ItemList list) return list.items().stream().filter(item -> item.id().startsWith("tunnel:")).findFirst().flatMap(ListItem::contextActions).orElseThrow();
            return ((Nodes.Table) node).rows().stream().filter(row -> row.heading().isEmpty()).findFirst().flatMap(TableRow::contextActions).orElseThrow();
        }
        List<MenuItem> tunnelMenu(Tunnel tunnel) {
            String id = "tunnel:" + tunnel.id();
            var node = node(tunnels.view(), TunnelsPanel.ROWS, Nodes.Node.class);
            if (node instanceof Nodes.ItemList list) return list.items().stream().filter(item -> item.id().equals(id)).findFirst().flatMap(ListItem::contextActions).orElseThrow();
            return ((Nodes.Table) node).rows().stream().filter(row -> row.id().equals(id)).findFirst().flatMap(TableRow::contextActions).orElseThrow();
        }
        void menu(List<MenuItem> items, String label, Function<Compositions, dev.jasper.sdk.ui.UiView> view) {
            press(view.apply(this), items.stream().filter(item -> item.label().equals(label)).findFirst().orElseThrow().action());
        }
    }

    static Optional<TreeItem> leaf(List<TreeItem> items, String id) {
        for (var item : items) {
            if (item.id().equals(id)) return Optional.of(item);
            var found = leaf(item.children(), id);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    interface Path0 { void run(Compositions c) throws Exception; }
    record Gesture(String name, Path0 rows, Path0 classic) { }

    String host() { return HostsPanel.rowId(new HostRows.Host(mac)); }
    String distro() { return "distro:" + ubuntu.id(); }

    List<Gesture> gestures() {
        var gestures = new ArrayList<Gesture>();
        gestures.add(new Gesture("activate a host", c -> { c.selectHost(host()); press(c.sessions.view(), HostsPanel.ACTIVATE); },
            c -> { c.selectHost(host()); press(c.sessions.view(), HostsPanel.ACTIVATE); }));
        gestures.add(new Gesture("Connect on the card or Properties", c -> { c.selectHost(host()); press(c.sessions.view(), HostsPanel.CONNECT); },
            c -> { c.selectHost(host()); press(c.sessions.view(), HostsPanel.CONNECT); }));
        gestures.add(new Gesture("Edit on the card or Properties", c -> { c.selectHost(host()); press(c.sessions.view(), HostsPanel.EDIT); },
            c -> { c.selectHost(host()); press(c.sessions.view(), HostsPanel.EDIT); }));
        gestures.add(new Gesture("the star", c -> press(c.sessions.view(), "favorite:" + mac.id()),
            c -> c.menu(c.hostMenu(host()), "Add to favorites", x -> x.sessions.view())));
        for (String label : List.of("Connect in new tab", "Connect in split", "Browse files", "Edit…", "Duplicate", "Delete…", "Add to favorites"))
            gestures.add(new Gesture("host menu " + label, c -> c.menu(c.hostMenu(host()), label, x -> x.sessions.view()),
                c -> c.menu(c.hostMenu(host()), label, x -> x.sessions.view())));
        gestures.add(new Gesture("open a distribution", c -> { c.selectHost(distro()); press(c.sessions.view(), HostsPanel.ACTIVATE); },
            c -> { c.selectHost(distro()); press(c.sessions.view(), HostsPanel.ACTIVATE); }));
        gestures.add(new Gesture("distribution menu Open in split", c -> c.menu(c.hostMenu(distro()), "Open in split", x -> x.sessions.view()),
            c -> c.menu(c.hostMenu(distro()), "Open in split", x -> x.sessions.view())));
        gestures.add(new Gesture("collapse a group", c -> { emit(c.sessions.view(), new UiEvent.Clicked(HostsPanel.ROWS, "group:Local Network")); c.events.add("collapsed " + c.sessions.collapsed()); },
            c -> { emit(c.sessions.view(), new UiEvent.Expanded(HostsPanel.ROWS, "group:Local Network", false)); c.events.add("collapsed " + c.sessions.collapsed()); }));
        gestures.add(new Gesture("search", c -> { type(c.sessions.view(), HostsPanel.SEARCH, "zzz"); c.events.add("rows " + c.sessions.rows().size()); },
            c -> { type(c.sessions.view(), HostsPanel.SEARCH, "zzz"); c.events.add("rows " + c.sessions.rows().size()); }));
        gestures.add(new Gesture("start a tunnel", c -> emit(c.tunnels.view(), new UiEvent.Toggled("switch:" + db.id(), true)),
            c -> { c.selectTunnel(); press(c.tunnels.view(), TunnelsPanel.TOGGLE); }));
        gestures.add(new Gesture("stop a running tunnel", c -> { c.states.put(db.id(), new TunnelState.Up(Instant.now(), 5432, new Counters())); c.tunnels.refresh(); emit(c.tunnels.view(), new UiEvent.Toggled("switch:" + db.id(), false)); },
            c -> { c.states.put(db.id(), new TunnelState.Up(Instant.now(), 5432, new Counters())); c.tunnels.refresh(); c.selectTunnel(); press(c.tunnels.view(), TunnelsPanel.TOGGLE); }));
        gestures.add(new Gesture("edit a tunnel", c -> { c.selectTunnel(); press(c.tunnels.view(), TunnelsPanel.ACTIVATE); },
            c -> c.menu(c.tunnelMenu(), "Edit…", x -> x.tunnels.view())));
        gestures.add(new Gesture("delete a tunnel", c -> { c.selectTunnel(); press(c.tunnels.view(), TunnelsPanel.DELETE); },
            c -> c.menu(c.tunnelMenu(), "Delete…", x -> x.tunnels.view())));
        for (String label : List.of("Duplicate", "Copy Address"))
            gestures.add(new Gesture("tunnel menu " + label, c -> c.menu(c.tunnelMenu(), label, x -> x.tunnels.view()),
                c -> c.menu(c.tunnelMenu(), label, x -> x.tunnels.view())));
        Path0 up = c -> { c.states.put(db.id(), new TunnelState.Up(Instant.now(), 5432, new Counters())); c.tunnels.refresh(); };
        gestures.add(new Gesture("tunnel menu Open in Browser", c -> { up.run(c); c.menu(c.tunnelMenu(), "Open in Browser", x -> x.tunnels.view()); },
            c -> { up.run(c); c.menu(c.tunnelMenu(), "Open in Browser", x -> x.tunnels.view()); }));
        gestures.add(new Gesture("quick forward menu Save as Tunnel…", c -> c.menu(c.tunnelMenu(forward), "Save as Tunnel…", x -> x.tunnels.view()),
            c -> c.menu(c.tunnelMenu(forward), "Save as Tunnel…", x -> x.tunnels.view())));
        // A filter that matches nothing: the default look's "No tunnels match" line, the classic table's empty text.
        gestures.add(new Gesture("a search that matches no tunnel",
            c -> { type(c.tunnels.view(), TunnelsPanel.FILTER, "zzz"); c.events.add("empty " + node(c.tunnels.view(), TunnelsPanel.NO_MATCH, Nodes.Text.class).text()); },
            c -> { type(c.tunnels.view(), TunnelsPanel.FILTER, "zzz"); c.events.add("empty " + node(c.tunnels.view(), TunnelsPanel.ROWS, Nodes.Table.class).emptyText()); }));

        // SFTP, connected: the navigation row keeps its ids, so each tool is the same press in both looks.
        for (String tool : List.of(SftpPanel.BACK, SftpPanel.FORWARD, SftpPanel.REFRESH, SftpPanel.HIDDEN, SftpPanel.UPLOAD))
            gestures.add(new Gesture("SFTP " + tool, c -> { c.browse(); press(c.sftp.view(), tool); }, c -> { c.browse(); press(c.sftp.view(), tool); }));
        for (String where : List.of("upload", "row", "folder"))
            for (String label : List.of("Up", "New folder…", "Upload folder…", "Download selected…", "Copy to host…", "Copy paths", "Delete selected…"))
                gestures.add(new Gesture("SFTP " + where + " menu " + label, c -> { c.browse(); c.sftpMenu(where, label); },
                    c -> { c.browse(); c.sftpMenu(where, label); }));
        Path0 crumb = c -> { c.browse(); emit(c.sftp.view(), new UiEvent.Selected(SftpPanel.PATH,
            Set.of(node(c.sftp.view(), SftpPanel.PATH, Nodes.Breadcrumbs.class).crumbs().getFirst().id()))); };
        gestures.add(new Gesture("SFTP breadcrumb", crumb, crumb));
        Path0 open = c -> { c.browse(); press(c.sftp.view(), node(c.sftp.view(), SftpPanel.FILES, Nodes.Table.class).activateAction()); };
        gestures.add(new Gesture("SFTP open a folder", open, open));
        Path0 follow = c -> { c.browse(); emit(c.sftp.view(), new UiEvent.Toggled(SftpPanel.FOLLOW, false)); };
        gestures.add(new Gesture("SFTP Follow terminal folder", follow, follow));
        for (String page : List.of(SftpPanel.PREVIOUS, SftpPanel.NEXT))
            gestures.add(new Gesture("SFTP page " + page, c -> { c.paged(); press(c.sftp.view(), page); }, c -> { c.paged(); press(c.sftp.view(), page); }));
        // SFTP, disconnected: the default look's Choose Session menu, the classic Choose Session… dropdown.
        gestures.add(new Gesture("SFTP Choose Session",
            c -> press(c.sftp.view(), node(c.sftp.view(), SftpPanel.CHOOSE, Nodes.MenuButton.class).items().stream()
                .filter(item -> item.label().equals("Mac")).findFirst().orElseThrow().action()),
            c -> emit(c.sftp.view(), new UiEvent.Selected(SftpPanel.CHOOSE_DROPDOWN, Set.of(node(c.sftp.view(), SftpPanel.CHOOSE_DROPDOWN, Nodes.Choice.class)
                .options().stream().filter(option -> option.label().equals("Mac")).findFirst().orElseThrow().id())))));

        // The window's transfer rows: the SFTP footer under ROWS, the Transfers panel under CLASSIC (Task 8).
        gestures.add(new Gesture("cancel a running transfer", c -> click(c.transferView(), "transfer." + running + ".close"),
            c -> click(c.transferView(), "transfer." + running + ".close")));
        gestures.add(new Gesture("dismiss a finished transfer", c -> click(c.transferView(), "transfer." + finished + ".close"),
            c -> click(c.transferView(), "transfer." + finished + ".close")));
        gestures.add(new Gesture("a transfer's own action", c -> click(c.transferView(), "transfer." + finished + ".action"),
            c -> click(c.transferView(), "transfer." + finished + ".action")));
        return gestures;
    }

    @Test void everySessionsTunnelsSftpAndTransfersGestureHasAClassicPathWithTheSameEffect() throws Exception {
        for (var gesture : gestures()) {
            var effects = new ArrayList<List<String>>();
            for (var layout : List.of(Layout.ROWS, Layout.CLASSIC)) {
                var c = value(() -> new Compositions(layout));
                edt(() -> (layout == Layout.ROWS ? gesture.rows() : gesture.classic()).run(c));
                effects.add(List.copyOf(c.events));
            }
            assertThat(effects.get(0)).as(gesture.name() + ": the default look's path does something").isNotEmpty();
            assertThat(effects.get(1)).as(gesture.name() + ": the classic path does the same").isEqualTo(effects.get(0));
        }
    }

    @Test void theToolbarsHostDistributionAndManageSessionsGesturesHaveClassicPaths() throws Exception {
        for (String what : List.of("host", "distro", "manage")) {
            var effects = new ArrayList<List<String>>();
            for (var layout : List.of(Layout.ROWS, Layout.CLASSIC)) try (var host = new FakePluginHost()) {
                var events = new ArrayList<String>();
                var context = new AtomicReference<PluginContext>();
                host.start(RemotePluginTest.INFO, Set.of(), Set.of(), context::set);
                var store = new HostStore(dir.resolve(layout + "-" + what + ".toml"), Runnable::run, Runnable::run);
                store.put(mac).join();
                var toolbar = value(() -> new SessionsToolbar(context.get(), store, "network", (window, chosen) -> events.add("activate " + chosen.name()),
                    window -> events.add("manage"), () -> List.of(ubuntu), (window, profile) -> events.add("open " + profile.name()),
                    layout, new SessionSelection(), window -> Optional.empty()));
                var window = host.addTerminalWindow();
                if (layout == Layout.ROWS) {
                    String action = switch (what) {
                        case "host" -> "dev.jasper.remote.session.h" + mac.id();
                        case "distro" -> SessionsToolbar.distroActionId(ubuntu.id());
                        default -> "dev.jasper.remote.sessions.manage";
                    };
                    host.invoke(action, window, null);
                } else {
                    var view = host.toolbarViews(window).getFirst();
                    edt(() -> {
                        if (what.equals("manage")) emit(view, new UiEvent.Selected(SessionsToolbar.CHOICE, Set.of(SessionsToolbar.MANAGE_OPTION)));
                        else {
                            emit(view, new UiEvent.Selected(SessionsToolbar.CHOICE, Set.of(what.equals("host") ? "host:" + mac.id() : "distro:" + ubuntu.id())));
                            press(view, SessionsToolbar.CONNECT);
                        }
                    });
                }
                effects.add(List.copyOf(events));
                edt(toolbar::close);
            }
            assertThat(effects.get(0)).as(what).isNotEmpty();
            assertThat(effects.get(1)).as(what + ": the toolbar view does the same").isEqualTo(effects.get(0));
        }
    }

    @Test void transfersIsReachableInBothLooks() throws Exception {
        for (var layout : List.of(Layout.ROWS, Layout.CLASSIC)) try (var host = new FakePluginHost()) {
            if (layout == Layout.CLASSIC) dev.jasper.remote.workstation.FakeEnterprise.start(host);
            var plugin = new TestRemotePlugin(UI, context -> Optional.empty(), (delay, task) -> () -> { }, dir.resolve("ssh-" + layout));
            host.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor", "dev.jasper.enterprise"), plugin);
            settle(host);
            var window = host.addTerminalWindow();
            host.invoke(dev.jasper.remote.ui.transfers.TransferUi.SHOW, window, null);
            settle(host);
            String shown = layout == Layout.ROWS ? dev.jasper.remote.ui.sftp.SftpUi.PANEL : dev.jasper.remote.ui.transfers.TransfersPanel.PANEL;
            assertThat(host.panelView(shown, window)).as(layout + ": Transfers shows its panel").isPresent();
        }
    }
}
```

`SessionsToolbar.distroActionId` is package-visible static (`SessionsToolbar.java:96`); the manage action id is
`SessionsToolbar.MANAGE` (private there): if it is not `dev.jasper.remote.sessions.manage` on the branch, use its value.
The SFTP gestures cover every navigation action of the panel (Back, Forward, Refresh, Hidden files, Upload, each item of
the Upload menu, the row menu and the folder's menu, the breadcrumbs, opening a folder, Follow terminal folder, the two
paging buttons) and the disconnected Choose Session; the transfer gestures press each row's close button (cancel while
running, dismiss when finished) and its action button through the view that shows them: the SFTP footer under ROWS,
the Transfers panel under CLASSIC. The test reads every menu item and option by its label and every button by its node
id (`transfer.<id>.close`, `transfer.<id>.action`, as `TransferStrip.closeId`/`actionId` write them), so a classic
composition that drops or renames one fails the gesture by name. "Open in Editor" appears only with the Editor
(`SftpPanel.editing`), which this test does not set; `StagedRemoteSftpTest` covers it.

Create `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/CompositionParityTest.java`:

```java
package dev.jasper.fileserver.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState;
import dev.jasper.fileserver.workstation.Layout;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TableRow;
import dev.jasper.sdk.ui.UiEvent;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 14.7: each default-look gesture of the File Servers panel has a classic path with the same effect. */
class CompositionParityTest {
    static final Path HOME = Path.of(System.getProperty("java.io.tmpdir"), "fileserver-fixture");
    final ServerRegistry.View test = new ServerRegistry.View(ServerDefinition.create("test", Protocol.HTTP, HOME.resolve("test")), false,
        ServerState.STOPPED, List.of(URI.create("http://127.0.0.1:8080")), Optional.empty(), false);
    String row() { return "server:" + test.definition().id(); }

    final class Composition {
        final List<String> events = new ArrayList<>();
        final FileServersPanel panel;
        Composition(Layout layout) {
            var holder = new java.util.concurrent.atomic.AtomicReference<FileServersPanel>();
            UiTestAccess.edt(() -> {
                holder.set(new FileServersPanel(new FakeKit("dev.jasper.fileserver"), new FileServersPanel.Actions(
                    id -> events.add("toggle"), id -> events.add("edit " + id.map(any -> "test").orElse("new")), id -> events.add("duplicate"),
                    id -> events.add("delete"), id -> events.add("reveal"), text -> events.add("copy " + text), id -> events.add("password"),
                    id -> events.add("clear"), id -> events.add("fingerprints")), id -> List.of(), HOME, "", ZoneOffset.UTC, "add", layout));
                holder.get().setServers(List.of(test), Optional.empty());
            });
            panel = holder.get();
        }
        FakeKitView view() { return (FakeKitView) panel.view(); }
        void emit(UiEvent event) { UiTestAccess.edt(() -> view().emit(event)); }
        void press(String action) { emit(new UiEvent.Action(action)); }
        void select() { emit(new UiEvent.Selected(FileServersPanel.ROWS, Set.of(row()))); }
        List<MenuItem> menu() {
            var node = UiTestAccess.node(view(), FileServersPanel.ROWS);
            if (node instanceof Nodes.ItemList list) return list.items().stream().filter(item -> item.id().equals(row())).findFirst().flatMap(ListItem::contextActions).orElseThrow();
            return ((Nodes.Table) node).rows().stream().filter(r -> r.id().equals(row())).findFirst().flatMap(TableRow::contextActions).orElseThrow();
        }
        void menu(String label) { press(menu().stream().filter(item -> item.label().equals(label)).findFirst().orElseThrow().action()); }
        boolean detailsShown() {
            var node = UiTestAccess.find(view(), FileServersPanel.DETAILS);
            return node.map(n -> n.state().visible()).orElse(false) || UiTestAccess.find(view(), FileServersPanel.PROPERTIES).isPresent();
        }
    }

    interface Path0 { void run(Composition c); }
    record Gesture(String name, Path0 rows, Path0 classic) { }

    List<Gesture> gestures() {
        var gestures = new ArrayList<Gesture>();
        gestures.add(new Gesture("start or stop with the switch", c -> c.emit(new UiEvent.Toggled("switch:" + test.definition().id(), true)),
            c -> { c.select(); c.press(FileServersPanel.ENTER_TOGGLE); }));
        gestures.add(new Gesture("Enter toggles", c -> { c.select(); c.press(FileServersPanel.ENTER_TOGGLE); }, c -> { c.select(); c.press(FileServersPanel.ENTER_TOGGLE); }));
        gestures.add(new Gesture("the details", c -> { c.select(); c.press(FileServersPanel.ACTIVATE); c.events.add("details " + c.detailsShown()); },
            c -> { c.select(); c.events.add("details " + c.detailsShown()); }));
        gestures.add(new Gesture("the details' Edit…", c -> { c.select(); c.press(FileServersPanel.ACTIVATE); c.press("fileserver.details.edit"); },
            c -> c.menu("Edit…")));
        gestures.add(new Gesture("the details' Copy of an address", c -> { c.select(); c.press(FileServersPanel.ACTIVATE); c.press("uri:" + test.definition().id() + ":0"); },
            c -> c.menu("Copy URI")));
        gestures.add(new Gesture("Copy Address", c -> c.menu("Copy Address"), c -> c.menu("Copy URI")));
        for (String label : List.of("Start", "Edit…", "Duplicate", "Delete…", "Reveal Folder"))
            gestures.add(new Gesture("menu " + label, c -> c.menu(label), c -> c.menu(label)));
        gestures.add(new Gesture("clear the activity", c -> { c.select(); c.press(FileServersPanel.ACTIVATE); c.press("fileserver.activity.clear"); },
            c -> { c.menu("Show Activity"); c.press("fileserver.activity.clear"); }));
        gestures.add(new Gesture("copy the activity", c -> { c.select(); c.press(FileServersPanel.ACTIVATE); c.press("fileserver.activity.copy"); },
            c -> { c.menu("Show Activity"); c.press("fileserver.activity.copy"); }));
        return gestures;
    }

    @Test void everyFileServersGestureHasAClassicPathWithTheSameEffect() {
        for (var gesture : gestures()) {
            var rows = new Composition(Layout.ROWS); gesture.rows().run(rows);
            var classic = new Composition(Layout.CLASSIC); gesture.classic().run(classic);
            assertThat(rows.events).as(gesture.name() + ": the default look's path does something").isNotEmpty();
            assertThat(classic.events).as(gesture.name() + ": the classic path does the same").isEqualTo(rows.events);
        }
    }

    @Test void theEmptyStatesShareFolderIsTheSameInBothLooks() {
        for (var layout : List.of(Layout.ROWS, Layout.CLASSIC)) {
            var events = new ArrayList<String>();
            var holder = new java.util.concurrent.atomic.AtomicReference<FileServersPanel>();
            UiTestAccess.edt(() -> holder.set(new FileServersPanel(new FakeKit("dev.jasper.fileserver"), new FileServersPanel.Actions(id -> { },
                id -> events.add("edit " + id.map(any -> "test").orElse("new")), id -> { }, id -> { }, id -> { }, text -> { }, id -> { }, id -> { }, id -> { }),
                id -> List.of(), HOME, "", ZoneOffset.UTC, "add", layout)));
            UiTestAccess.edt(() -> ((FakeKitView) holder.get().view()).emit(new UiEvent.Action("fileserver.empty.new")));
            assertThat(events).as(layout.toString()).containsExactly("edit new");
        }
    }
}
```

The default look's "Start" and the classic "Start" are the same menu verb (`menu:toggle:<id>`); "Reveal Folder" is in
both menus. If a default-look row exposes another gesture than these on the branch (a later plan added one), add it to
the list with its classic path.

- [ ] **Step 2: Run the tests**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.CompositionParityTest' :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.ui.CompositionParityTest' --continue`
Expected: PASS (3 and 2 tests). A failing gesture names itself: either its classic path is missing (fix the classic
composition of its task) or the effects differ (the classic path calls another action; fix it there). These tests
characterise Tasks 4 to 11, so they pass on first run when those tasks are right; to see them fail, rename the classic
"Copy URI" label in `FileServersPanel.classicMenu` and run them (FAIL "menu … Copy URI"), then restore it.

- [ ] **Step 3: Ledger and commit**

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv"); text = path.read_text(encoding="utf-8")
for file, owner in [("plugins/remote/src/test/java/dev/jasper/remote/CompositionParityTest.java", "dev.jasper.remote"),
                    ("plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/CompositionParityTest.java", "dev.jasper.fileserver")]:
    if file in text: raise SystemExit(f"already ledgered: {file}")
    text += f"{file},{owner},CompositionParityTest,nonvisual-helper,none,none,{file},Test/fixture ownership; New in Enterprise plan 10a Task 12 (spec 14.7)\n"
path.write_text(text, encoding="utf-8")
PY
./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'
git add plugins/remote/src/test plugins/fileserver/src/test docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Plugin compositions: every Remote and File Server gesture has a classic path

CompositionParityTest runs each default-look gesture of the Sessions,
Tunnels, SFTP and File Servers panels, the transfer rows, the Sessions
dropdown and Transfers on the rows composition and its classic path on
the classic one, and checks that both ask the plugin for the same thing
(spec 14.7).

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

Expected: the ledger test PASSes before the commit.

---

### Task 13: Documentation, the visual pairs, STATUS and the whole build

Spec 17's documents for this plan's part, the opt-in `tools/visual/pairs.json` entries for every render (spec 16.1.4),
the default-look proof, the full build with its counts, and the STATUS entry.

**Files:**
- Modify: `docs/remote.md`, `docs/file-server.md`, `docs/plugin-authoring.md`, `tools/visual/pairs.json`, `docs/STATUS.md`,
  this plan's status banner (line 3)

- [ ] **Step 1: The documents**

Read each document first and keep its voice; make the smallest accurate edits:

- `docs/remote.md`: one paragraph, in the section that describes the Sessions panel's toolbar dropdown, saying that under
  the Enterprise look the toolbar shows "Session: [combo] [Connect]" (saved hosts, WSL distributions, Manage Sessions…;
  Connect focuses a running pane or opens one), the Sessions panel is a Name/Host/Port tree over the selected host's
  Properties, Tunnels is a Type/Local/Remote/Via table whose rows start or stop on activation, SFTP's tools read Hidden
  files and Upload with a Modified column, Transfers (⌘⌥3) is a bottom panel, and the panels start Sessions and Tunnels
  on the right, SFTP on the left.
- `docs/file-server.md`: one paragraph: under the Enterprise look the File Servers panel is a Folder/Address/Status table
  beneath SFTP whose rows start or stop on activation, the chosen server's details are Properties (Show Activity shows
  its log), and New File Server lists authorized keys as a Type/Fingerprint/Comment table.
- `docs/plugin-authoring.md`: where 5b documents consuming `Workstation`, add one sentence that an in-repo plugin names
  `dev.jasper.enterprise.api` only in its own `workstation` package, which `verifyPluginArchitecture` checks.

- [ ] **Step 2: The visual pairs**

```bash
python3 -I - <<'PY'
import json, pathlib
path = pathlib.Path("tools/visual/pairs.json")
pairs = json.loads(path.read_text(encoding="utf-8"))
if any(pair["name"].startswith("composition-") for pair in pairs): raise SystemExit("pairs.json already has composition pairs")
R = "docs/design/enterprise/reference/"
C, D, X = R + "01 Workstation/Workstation - Connected (plugins in use).png", R + "08 Dark Mode/Workstation Dark - Connected (plugins in use).png", R + "01 Workstation/Workstation - Disconnected.png"
O = "jasper-app/build/enterprise-compositions/"
new = [("light/sessions", C, [2136, 230, 700, 782]), ("dark/sessions", D, [2136, 230, 700, 782]), ("light/sessions-locked", X, [2136, 230, 700, 782]),
       ("light/session-toolbar", C, [660, 110, 720, 68]), ("dark/session-toolbar", D, [660, 110, 720, 68]),
       ("light/tunnels", C, [2136, 1064, 700, 642]), ("dark/tunnels", D, [2136, 1064, 700, 642]), ("light/tunnels-none", X, [2136, 1064, 700, 642]),
       ("light/sftp", C, [48, 230, 600, 712]), ("dark/sftp", D, [48, 230, 600, 712]), ("light/sftp-disconnected", X, [48, 230, 600, 712]),
       ("light/file-servers", C, [48, 994, 600, 712]), ("dark/file-servers", D, [48, 994, 600, 712]), ("light/file-servers-none", X, [48, 994, 600, 712]),
       ("light/add-host", R + "03 Add Host/Add Host - Default.png", [2, 60, 1040, 1042]),
       ("light/new-tunnel", R + "04 New Tunnel/New Tunnel - Local.png", [2, 60, 1040, 898])]
for protocol, height in [("HTTP", 986), ("FTP", 1210), ("SFTP", 1172), ("TFTP", 862)]:
    new.append((f"light/new-file-server-{protocol.lower()}", R + f"02 New File Server/New File Server - {protocol}.png", [2, 60, 1040, height]))
before = len(pairs)
for name, mock, crop in new:
    pairs.append({"name": "composition-" + name.replace("/", "-"), "mock": mock, "crop": crop, "render": O + name + "@2x.png",
                  "min_agreement": 0.55, "colours": (["#ECEDEF"] if "light" in name else ["#3C3F44"]) if "session-toolbar" in name
                  else ["#F0F1F3" if "light" in name else "#393C41"] if "new-" not in name and "add-host" not in name else ["#ECEDEF", "#E6E7EA"]})
path.write_text(json.dumps(pairs, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(before, "->", len(pairs))
PY
git diff --stat tools/visual/pairs.json
```

Expected: 20 pairs added and only insertions in the diff (restore and insert by hand if `json.dumps` reformatted
existing entries). Where Tasks 4 to 11 recorded a measured agreement below 0.55 with its cause, write that value into the
pair's `min_agreement`; where they lowered no floor, keep 0.55. Then run the opt-in comparison once:

```bash
python3 -I tools/visual/check_pairs.py jasper-app/build/composition-compare composition-
```

Expected: one PASS line per pair (it needs Pillow and NumPy; skip it if they are missing, as it is not part of `check`).

- [ ] **Step 3: Prove the default look unchanged**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
python3 -I - <<'PY'
import hashlib, pathlib, sys
def digests(root): return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.rglob("*")) if p.is_file()}
bad = []
for theme in ("dark-1x", "light-1x"):
    base, now = digests(pathlib.Path("jasper-ui/build/plan10-base") / theme), digests(pathlib.Path("jasper-ui/build/gallery") / theme)
    bad += [f"{theme}/{name}" for name in sorted(set(base) | set(now)) if base.get(name) != now.get(name)]
print("changed:", bad if bad else "none"); sys.exit(1 if bad else 0)
PY
git diff --stat main -- jasper-app/src/test/resources plugins/*/src/test/resources jasper-ui/src/test/resources | tail -3
```

Expected: `changed: none`; the resources diff names only Task 3's three tables. The existing `tools/visual/pairs.json`
renders are the default look's, which no task changed (every plugin change is under `Layout.CLASSIC`; the jasper-ui face's
default class is plan 3b's code).

- [ ] **Step 4: The whole build and its counts**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Count the results:

```bash
python3 -I - <<'PY'
import pathlib, xml.etree.ElementTree as ET
tests = failures = errors = skipped = 0
for report in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    suite = ET.parse(report).getroot()
    tests += int(suite.get("tests", 0)); failures += int(suite.get("failures", 0)); errors += int(suite.get("errors", 0)); skipped += int(suite.get("skipped", 0))
print(f"{tests:,} tests, {failures} failures, {errors} errors, {skipped} skipped")
PY
```

Record the line in the STATUS entry.

- [ ] **Step 5: STATUS, the banner, and commit**

Add a new entry at the top of `docs/STATUS.md`, in the style of the plan-1 and plan-2 entries:

```markdown
**Enterprise presentation, plan 10a: Workstation, Remote and File Server compositions — 2026-10-16**
(`claude/enterprise-plugin-compositions`, worktree `.claude/worktrees/enterprise-10`, `<first>..<last>`, then this
documentation commit; [plan](superpowers/plans/2026-10-16-enterprise-10a-plugin-compositions.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). Remote, File Server and Vault optionally
require Enterprise and read `dev.jasper.<plugin>.workstation.Layout` once in `start` (CLASSIC while `Workstation` is
published, ROWS otherwise, including when Enterprise is not installed); `verifyPluginArchitecture` confines
`dev.jasper.enterprise.api` to those packages. Under the Enterprise look Remote contributes the "Session: [combo]
[Connect]" toolbar view, the Sessions panel is a Name/Host/Port tree over Properties, Tunnels a Type/Local/Remote/Via
table, SFTP's tools read Hidden files and Upload over a Modified column (disconnected: Choose Session… over "Not
Connected"), Transfers a bottom panel toggled by ⌘⌥3, and the panels start Sessions and Tunnels right, SFTP and File
Servers left; File Server's panel is a Folder/Address/Status table over Properties and New File Server lists keys as a
table; Add Host's keys read "No keys". Classic Properties (jasper-ui) follow the table point: the Workstation grid when
titled, label-colon lines when untitled. `ClassicCompositionRenderTest` renders every surface from the staged plugins
under the shipped kit, Plex and theme, Light and Dark, against the mocks (bands, colours, edge agreement: <the measured
agreements>); `CompositionParityTest` checks every default-look gesture's classic path. Rulings (plan, "Rulings"): <one
clause each, 1 to 15>. Residuals: <Rulings 14's four, with their measured values, and any floor a task lowered, with its
cause>. The default look is byte-identical (gallery digests unchanged; no default render changed). `./gradlew check
--continue`: **<counts>**. GUI checks for the user: the Enterprise Workstation window against the Workstation and Dark
Mode mocks (the toolbar view's combo and Connect, the Sessions tree's star and Properties, the Tunnels and File Servers
tables' activation and context menus, the SFTP row, the Transfers panel and ⌘⌥3, the default slots on a fresh
Enterprise start), Add Host and New File Server. Not merged, not pushed.
```

Replace the angle-bracketed parts with the facts of the run. Set this plan's status banner (a new line 3) to "**Status:
executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and list any deviation from the plan's text there.

```bash
git add docs tools/visual/pairs.json
git commit -F - <<'EOF'
Plugin compositions: documentation, the visual pairs and status

The Remote and File Server guides describe the classic compositions,
the plugin guide the workstation-package rule, tools/visual/pairs.json
pairs every composition render with its mock crop (opt-in), and STATUS
records plan 10a with its rulings, residuals and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Execution notes (2026-10-17)

Refreshed at `b23f853af` (this branch: `main` at `9c6351e07` plus the plan's two commits). Plans 3b to 3e, 4a to 4c,
5a, 5b, 6a, 6b, 7, 8a, 9a and 11 are in `main`; 8b and 9b run in parallel worktrees. Method: every Create, Add-to and
edit script of Tasks 1 to 13 was applied in order to an exported copy of the branch, and every touched module was
compiled with the JetBrains Runtime 25 `javac` (main and test sources of Remote, File Server, Vault, jasper-ui and
jasper-app) against the plan-11 worktree's classes, whose SDK, testkit, plugins, terminal and Buddy sources equal
`main`'s. Before the fixes below: two edit anchors missing (Task 8's ledger, Task 9's `NativeShells`) and five compile
errors; after them, every anchor is found its stated number of times and every module compiles with zero errors. No
Gradle and no JUnit ran, so assertions, renders and floors are still to be proved by execution. Source hygiene and
`ThemeMigrationCoverageTest`'s candidate pattern were checked over every new source: clean after R9.

### Pre-flight scan

| Tasks | Shared file or interface | Finding | Ruling |
|---|---|---|---|
| 1 | manifests, builds, `Layout`, the gate, AGENTS.md | All anchors hold; `RemotePlugin.java:148`, `FileServerPlugin.java:142`, `VaultPlugin.start` read the layout once; the gate's names (`own`, `from`, `path`, `line`) exist | none |
| 2 | `EnterpriseLook`, `PanelSurfaceFixture`, `jasper-app/build.gradle.kts`, ledger | A `testsupport.EnterpriseLook` exists (6a, shipped by 5b); the build already reads the mocks; `exports` mistyped | R4, R5, R6 |
| 3 | `PropertyLists`, `KitFaces`, `DesignMetrics`, TSVs | 5b's final review changed the value font after 3b; the rest compiles as written | R8 |
| 4 | `HostsPanel`, `RemotePlugin.createPanel`, `PanelSurfaceFixture.selectSession`, `ClassicCompositionRenderTest` | `selectSession` calls `settle()`, which throws `InterruptedException`; floors are face-dependent | R6, R15 |
| 5 | `SessionsToolbar`, `SessionSelection`, `RemotePlugin`, `PanelSurfaceFixture.toolbarView`, ledger | `SessionSelection` and `SessionToolbarViewTest` match the ledger pattern and had no rows | R9 |
| 6 | `TunnelsPanel`, `TunnelsUi`, `RemotePlugin` | Agrees with itself; compiles | none |
| 7 | `SftpPanel`, `SftpUi`, `RemotePlugin` | Agrees with itself; compiles | none |
| 8 | `TransferStrip`, `TransfersPanel(s)`, `RemotePluginTest`, ledger | The ledger script's `file in text` finds `TransfersPanelTest` in the first row's tests column and stops before writing; `find` is hidden by `RemotePluginTest.find(Component, Class)` | R10, R12 |
| 9 | `HostEditor`, `RemotePlugin.editHost`, `NativeShells`, `PanelSurfaceFixture` | 6b's final code replaced the anchored loop with `nativeMarks`, which already bands classic native dialogs; the mock's keys box cannot hold spec 14.4's side column | R7, R11 |
| 10 | `FileServersPanel`, `FileServerPlugin`, `UiTestAccess`, `FileServerPluginTest` | The panel's toggle is `dev.jasper.fileserver.show-panel`, not `.show` | R13 |
| 11 | `ServerEditor`, `FileServerPlugin.edit`, `PanelSurfaceFixture.dispatch`, `ServerEditorTest` | `ED25519` is declared twice; `dispatch` calls `settle()`; triage names Task 9's removed method | R6, R7, R14 |
| 12 | both `CompositionParityTest`s | Agree with Tasks 4 to 11's ids and constructors; compile | none |
| 13 | docs, `pairs.json`, STATUS, banner | The toolbar pairs list a colour their crop lacks; the STATUS template names `classicFooters` | R7, R17 |
| 1 and 2 | `layout()` read by reflection | Package-private `layout()` is declared on `RemotePlugin` and `FileServerPlugin`, which the fixture instantiates | none |
| 1 and 4 to 11 | the `Layout` argument | Each composition root takes the plugin's `layout` from `start`; constructor forms compile | none |
| 1 and 8, 12 | `FakeEnterprise`, optional `dev.jasper.enterprise` | Consistent | none |
| 1 and 10 | `FileServerPluginTest.aStartUnderEnterpriseReadsTheClassicLayoutOnce` | Task 10 extends Task 1's method | R13 |
| 2 and 4 to 11 | `PanelSurfaceFixture(…, true)`, `render`, `EnterpriseLook.resolved`, `MockMatch`, `check` | One shared Enterprise fixture and one restore | R4, R6, R15 |
| 2, 4, 5, 9, 11 | `PanelSurfaceFixture` edit chain | Task 5 and Task 9 anchor on Task 4's `selectSession` comment, Task 11 on Task 9's `invoke` comment: task order is binding | none |
| 2 and 13 | `MockMatch.OUT` names and `pairs.json` | The 20 render names match the pairs' `render` paths | none |
| 3 and 4, 10 | classic Properties | The kit's `table` point is CLASSIC under Enterprise, so the Sessions and File Server Properties draw as the grid | R8 |
| 3 and 9b (parallel) | `DesignMetrics`, `compact-roles.tsv`, `classic-roles.tsv`, `face-pairs.tsv`, `KitFaces.all()` | Possible concurrent rows | R19 |
| 4 and 5 | `HostsPanel.selectRow`, `onSelectionChanged`; `RemotePlugin` | Distinct anchors; `panels` is keyed by window id | none |
| 4, 5, 6, 7, 8 and 12 | panel, toolbar, SFTP and transfer ids | Consistent with the classic compositions | none |
| 6 and 8 | `TunnelsUi` | Task 6 assigns `layout` before the `register` line Task 8 edits (javac flow check passes) | none |
| 7 and 8 | `SftpUi` | Task 8's `register` line sits in Task 7's twelve-argument constructor after `this.layout=layout;` | none |
| 9 and 11 | dialog renders and the native footer band | Both read 6b's band through `renderSurface` | R7 |
| 10 and 11 | `FileServerPlugin` | Disjoint anchors | none |
| 10 and 12 | `FileServersPanel` constructor, `UiTestAccess.find` | Consistent | none |
| 4 to 11 | `ClassicCompositionRenderTest` | Method counts 1, 2, 3, 4, 5, 6, 7 follow the task order | none |
| 2, 4 to 8, 10, 12 | `migration.csv` appends | Rows were missing in Task 5; Task 8's script stopped itself | R9, R10, R19 |
| 1, 4 to 9 | `RemotePlugin.java` | Every anchor found once, applied in task order | none |

### Rulings

R1. **The refresh is a javac dry run, not a Gradle run** — what: anchors, signatures and types are proved by applying the
plan in order to an exported copy and compiling every touched module; why: the orchestrator forbids Gradle here, and
javac proves exactly what a refresh must (anchors, names, types, flow); cost if wrong: an assertion or render fails at
execution, which the TDD steps expect and fix in place.

R2. **No rebase** — what: "Before you start" checks that `9c6351e07` is an ancestor instead of running `git rebase main`;
why: the orchestrator's rule, and the branch already holds `main`; cost if wrong: none.

R3. **Verified facts carry the 2026-10-17 line numbers** — what: facts 1, 3, 5, 6, 8, 10 to 13, 15 and 16 are re-anchored;
the names table holds (each name compiles); why: 255 commits since `deafed424`; cost if wrong: none (every edit quotes
its text).

R4. **Task 2 extends `testsupport.EnterpriseLook` instead of creating `application.EnterpriseLook`** — what: it adds
`selection()` and `resolved(boolean)` to plans 6a and 5b's fixture, the render tests restore with its
`restoreDefault()`, and the second class and its ledger row go; why: one Plex registration path
(`EnterpriseResources.kit()`) and one restore, as the chrome, dock and Settings mock tests use, where the plan's class
registered Plex a second way through `LookFonts.register` and restored with an unclosed controller; cost if wrong: one
more class later.

R5. **Task 2 leaves `jasper-app/build.gradle.kts` alone** — what: the edit and its `git add` go; why: the test task already
declares `docs/design/enterprise/reference` an input (`:108`); cost if wrong: none.

R6. **Three javac corrections** — what: `PanelSurfaceFixture`'s `exports` is `Map<String, ClassLoader>` (a
`Map<String, PluginClassLoader>` does not convert to `PluginClassLoader`'s parameter), and `selectSession` (Task 4) and
`dispatch` (Task 11) declare `throws InterruptedException` (`StagedVaultHost.settle()` throws it; their callers declare
`throws Exception`); why: compile errors otherwise; cost if wrong: none.

R7. **Task 9 adds no host change; Rulings 15 is superseded** — what: `NativeShells.classicFooters`, its call and
`ClassicDialogFooterTest` (with its ledger row) go; Tasks 9, 11 and 13 name 6b's `NativeShells.nativeMarks`; why: 6b's
final code (`37b8c0718`) already applies `nativeMarks(surface, systemTitle)` in `dialog`, giving every kit view of a
classic native dialog `dialogBand` (and a FULL form under a system title bar `hostTitled`), now and to content set later,
which `ClassicPlacementTest` covers; the plan's anchor (6b's earlier loop) is gone; Add Host, New Tunnel and New File
Server are modal DIALOGs that are not attached sheets, so they open natively and take those marks; cost if wrong: a
classic native WINDOW-form body would take the band too (no Remote or File Server dialog is one); one condition in
`nativeMarks` later.

R8. **The Properties faces pick a value's font for its text** — what: `DefaultPropertiesFace` and `ClassicPropertiesFace`
call `ThemePainting.font(context, role, row.value())`; why: 5b's final review (`911699932`) made `PropertyLists` do so,
and the pixel rule requires the default face to paint what the file paints now (it was the only difference); cost if
wrong: the gallery digest check catches it.

R9. **Task 5 ledgers `SessionSelection` and `SessionToolbarViewTest`** — what: a ledger step adds
`SessionSelection` (`nonvisual-helper,10,none`, a helper false positive: `new ArrayList` matches the candidate pattern)
and `SessionToolbarViewTest` (`nonvisual-helper,none,none`), the step's run adds `ThemeMigrationCoverageTest` and the
commit adds the ledger; why: `sourceDiscoveryIncludesTestFixturesAndHelperFalsePositives` fails otherwise; cost if
wrong: none.

R10. **Task 8's ledger script reads each row's own source column** — what: it compares against the set of first fields;
why: `file in text` found `TransfersPanelTest` in the first row's tests column and stopped before writing any row; cost
if wrong: none.

R11. **Add Host's keys box is checked at the classic ordered face's height** — what: `ADD_HOST_KEYS` is built from
`KEYS_BOX`, set on the first run to the render's white run between the box's rules (at least the mock's 56) and recorded
as a residual; the band window grows with it; why: spec 14.4 replaces the mock's Add Key… and Remove with Add Stored
Key…, Remove, Move Up and Move Down, and 4c's STATUS records that the box is taller and the body scrolls, so the mock's
56 cannot be met; cost if wrong: the box's height is compared with the face, not the mock.

R12. **Task 8 names `Kits.find` in full** — what: `dev.jasper.remote.testsupport.Kits.find(…)`; why: `RemotePluginTest`
declares `find(Component, Class)`, which hides the static import; cost if wrong: none.

R13. **Task 10's panel assertion uses `dev.jasper.fileserver.show-panel`** — what: the File Servers toggle as
`FileServerPluginTest.java:201` prints it; why: `.show` is not an action; cost if wrong: none.

R14. **Task 11 drops its `ED25519` constant** — what: the case uses the class's own (same text, `ServerEditorTest.java:26`);
why: a duplicate field does not compile; cost if wrong: none.

R15. **Edge agreement goes through `MockCrops.fontsFloor`** — what: `ClassicCompositionRenderTest.check` asserts the
floor under the shipped IBM Plex and prints it only where an installed Plex hides the plugin's files; bands, sizes and
colours are always asserted; why: the convention of 5b's, 7's and 9a's mock tests, and on this machine Plex resolves, so
every font-dependent floor is asserted as the orchestrator requires; cost if wrong: on a machine with Plex installed the
agreement is printed, not asserted.

R16. **The Dark strings keep the mock's accent** — what: `#3D74D6` stays in the measured Dark bands and colours though
spec 7.6 (amended by 5b) gives Enterprise Dark `#3C74D6`; why: they are the mock's measurements, and the two lie under
ΔE76 1, inside `MockMatch`'s 4 for bands and 6 for presence; cost if wrong: none.

R17. **The session-toolbar pairs list the toolbar's colours** — what: Light `#ECEDEF`, Dark `#3C3F44`; why:
`check_pairs.py` asks the render for each colour within ΔE 1.5, and the toolbar crop has no column header band
(`#F0F1F3` / `#393C41`); cost if wrong: one colour per pair.

R18. **Hand-offs to plan 10** — what: 4b's X10 (TFTP's read-only login in the control column) is not taken, since the
TFTP mock draws that field flush at the card's edge and the mock wins on layout; 4c's "the mock starts the Access radios
at the card's edge" stays a residual of 4b's label policy (SDK 0.18 gives a composition no way to undo a bare choice's
inset); 4c Rulings 10's single empty-state action is met (each Empty here has one); plan 7's header-action glyph sizes
and Transfers' place after Event Log stay plan 7 residuals (Rulings 14; plan 7 orders nothing inside a slot); 6a
ruling 31 (the auxiliary frames' File row against the 07 Vault mocks) is plan 10b's; why: scope and the mocks; cost if
wrong: one FormRow or one strip rule later.

R19. **Shared files with the parallel plans are reconciled at merge** — what: `migration.csv`, `docs/STATUS.md`,
`tools/visual/pairs.json`, the `DesignMetrics` role tables and frozen TSVs, `face-pairs.tsv`, `KitFaces.all()`,
`verifyPluginArchitecture` and AGENTS.md may also change in 8b and 9b; Task 3's TSV rows anchor on the
`FORM_LABEL_WIDTH` rows, so a role 9b adds elsewhere merges cleanly; why: the orchestrator's instruction; cost if wrong:
a conflict resolved by hand at merge.

R20. **Only plan 10a executes now** — what: Tasks 1 to 13; plan 10b follows on this branch; why: the orchestrator; cost if
wrong: none.

R21. **A timing-sensitive test may flake once** — what: other plans' builds share the machine; re-run such a test alone
before treating it as a failure; why: the orchestrator's machine note; cost if wrong: one extra run.
