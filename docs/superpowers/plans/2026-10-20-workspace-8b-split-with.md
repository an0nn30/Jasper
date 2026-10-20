# Workspace tabs 8b: split with a choice Implementation Plan

**Status: not executed.** Written against `claude/workspace-tabs` at `c1540b4e9` ("Docs: workspace tabs plan 8a, tabs
hold panes"; its code is `2d7233474`'s) with plan 8a's text, its review fixes included, applied on top (see Workspace).
Record every deviation from this text here and in `docs/STATUS.md`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A new pane can hold a terminal, an editor or a plugin's view. The SDK's `TabPlacement` reads `NEW_TAB`,
`SPLIT_RIGHT`, `SPLIT_DOWN` and `NEW_WINDOW`, and a plugin registers a split item that Jasper lists in every Split list
(the Tab menu's, a terminal's and the Editor's right-click menus) with its own bindable Right and Down actions. The
Editor opens a new editor tab or an editor pane through its own split item, and any file opened from outside it opens in
a new tab. Move Pane to New Tab (⌥⌘T, Ctrl+Alt+Shift+T elsewhere) gives the focused pane a tab of its own.

**Architecture:** The SDK gains `SplitItemSpec`, `SplitChoice`, `Windows.splitItem`, `Windows.splitChoices` and three
pane commands on `ContentTab` (`split`, `moveToNewTab`, `mayMoveToNewTab`). The app keeps split items in `Contributions`
(a `MENUS` change every window renders), `HostedUi` registers each item's two actions as the plugin's own, and
`WindowChrome` builds one Split submenu from the model for the Tab menu and a terminal's context menu. Move Pane to New
Tab is a built-in action over `ParentTabsView.moveToNewTab`, which plugins reach through the tab registry and three new
`WorkspaceHost` functions, as `moveToNewWindow` reaches the window manager. The testkit's fake and the app's contract
fixture take the two levels (a window's tabs, each holding panes) and show them through `windowTabs`, so the contract
pins placements, split items and the pane commands on both runtimes.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, RSyntaxTextArea (the Editor's popup), Gradle 9.7
(Kotlin DSL), JUnit 6.1.3 and AssertJ 3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-20-jasper-tabs-and-panes-design.md`](../specs/2026-10-20-jasper-tabs-and-panes-design.md)
(sections 2.5, 2.7-2.12, 8, 9, 10, 11, 14's second plan and 16). It builds on
[plan 8a](2026-10-20-workspace-8a-tabs-hold-panes.md)'s text, which executes first, and plan 8c
(`2026-10-20-workspace-8c-moving-panes.md`) builds on this plan's.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch
`claude/workspace-tabs`. Every anchor below was matched at its stated count, and every file creation and edit script of
Tasks 1-7 replayed in order, after plan 8a's Tasks 1-6 (its committed text), on a `git archive` copy of `c1540b4e9`, with
no failure. Nothing was compiled or run. Line numbers are `c1540b4e9`'s for text plan 8a leaves alone, and plan 8a's result's (marked
"after 8a") for text it writes; anchors in text plan 8a writes quote it as plan 8a leaves it. Plan 8a's Tasks 1 and 2
have since landed (`4f2884089`, `6b6db31a4`, then the fix `a656f8345`): their files match plan 8a's text but for that
fix (Javadoc in `TabRegistry.move(Tab, UUID, GroupId, Side)`, `WorkspaceDropResolver`, `DockDropOverlay` and
`WorkspaceView.apply`, a renamed pattern variable in `TabDrag.showLanding`, two `TabDragTest` cases and two
`WindowManagerTest` imports), which no anchor here quotes.

Run every command from the worktree root. Every edit quotes the text it replaces, and the scripts stop with a message
when that text is not found the stated number of times. Then re-find the quoted text (a change since may have reworded
it), adjust the anchor, re-run, and record it in this plan's banner. Never edit around a missing anchor. Later tasks
anchor on text that earlier tasks of this plan wrote, and quote it as they leave it.

**Hand-offs absorbed:**
- Plan 8a's "Left for plans 8b and 8c": the SDK's `TabPlacement.ACTIVE_GROUP` becomes `NEW_TAB` with `HostedUi`'s
  mapping (Task 1); the testkit's `FakeWorkspace`, the app's `TerminalFixture` and the contract's placement case take the
  two levels with it (Task 1); ⌥⌘T and Ctrl+Alt+Shift+T, which 8a's `TabShortcutCollisionTest` and `KeyBindingsTest`
  pin free, become Move Pane to New Tab's (Task 2). 8a's hand-off to 8c (the tab drag's machinery, `WorkspaceDropResolver`, `Drop.Strip`) is
  untouched.
- Plan 8a's Rulings 17 ("plan 8b adds its Split lists and Move Pane to New Tab to the Tab menu and the context menus"):
  Tasks 2 and 5.
- The brief's Editor check: `docs/editor.md` still lists the Editor's own ⌘W "Close Tab" and ⇧⌘] / ⇧⌘[ (Task 6,
  Rulings 15).
- The independent review of plan 8a: after 8a, `TabPlacement.ACTIVE_GROUP`'s Javadoc says "a new tab" while the
  testkit's `FakeWorkspace` and the contract case `aPlacementPutsANewContentTabInTheActiveGroup…` still stack items in
  the active group; Task 1 moves both (the fake opens a new tab, and the case pins a new tab on both runtimes) and gives
  `TabHandle`'s class Javadoc the item one pane shows (spec 3).

**Left for plan 8c** (what it extends): `ContentTab.split` and `moveToNewTab` select the item first and then act on the
focused pane, so a pane drag (8c) needs no new SDK member; `ParentTabsView.moveToNewTab` moves an item between two of a
window's tabs without the registry, the in-window case of 8c's drop onto a strip's empty end or "+";
`FakeWorkspace.moveToNewTab` and `TerminalFixture.moveToNewTab` play it in the contract.

## Spec issues

None. Where the spec leaves a choice (the Split lists' one shape, their order, the name New Editor Pane, where plugins
reach the Split choices), the Rulings record it.

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`), Java 25 on JBR 25. No GUI launch, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md).
- One commit per task, in the worktree above, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- Clean break (MVP): `TabPlacement.ACTIVE_GROUP` goes with no alias and no `KitCompatibility` entry (Rulings 1); the
  Editor's `dev.jasper.editor.new` and its "close-tab" command go with no alias; a saved binding naming the old action
  is reported as an unknown key. No shim, no migration, no "this was removed" text.
- The SDK stays 0.21.0, unreleased (spec 11): new members say `@since 0.21.0`; `JasperSdk.VERSION` and the bundled
  manifests' ranges do not change.
- SDK signatures name no `javax.swing` or `java.awt` type beyond the ones `gradle/architecture/PluginPresentationCheck.java`
  allows (Verified facts 9); `verifySdkArchitecture` runs it and the signature fixtures.
- Package edges (package-info "Allowed outgoing" lists; `verifyApplicationArchitecture`): none changes. `dev.jasper.sdk.ui`
  already depends on `dev.jasper.sdk.terminal` (`ActionContext`), which `ContentTab.split`'s `Direction` and the opener's
  `WindowHandle` use. SDK types appear in the app only in `dev.jasper.app.plugins`.
- Plugin versions take the next minor at execution: the Editor is 0.11.0 today (`plugins/editor/src/main/resources/plugin.toml:3`),
  so 0.12.0 unless it moved; no other plugin changes (Rulings 16).
- The theme-migration ledger `docs/design/theme-engine/migration.csv`: a new file that matches
  `ThemeMigrationCoverageTest`'s pattern (an import of `javax.swing`, `java.awt` or `dev.jasper.sdk.ui`, or a
  `new …List`, `new …Menu`, `new …Panel` and the like) gets `ledger.add(...)` with "New in workspace-tabs plan 8b Task N"
  (plan 4a Rulings 21).
- Javadoc is part of `check` (`-Xdoclint:all`, fail on error): no raw `<`, `>` or `&` in a comment, every `{@link}`
  resolves, and every new public member has a comment with its `@param`, `@return` and `@throws`.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- User-facing text says "tab" and "pane" (spec 3); the Split lists' entries read "Terminal Right", "Terminal Down",
  "<label> Right", "<label> Down" (spec 2.10), and a split item's actions "Split Right with <label>" and "Split Down with
  <label>" (spec 8).
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").
- Tests are headless (`java.awt.headless=true`): views and menus driven directly, the contract on both runtimes
  (`FakeContractTest`, `AppContractTest`).

### The two helper scripts

Create them once, before Task 1, in the scratch directory `build/plan8b/` (ignored by git; never commit them). They are
plan 3a's helpers, unchanged:

```bash
mkdir -p build/plan8b
cp build/plan7c/edits.py build/plan7c/ledger.py build/plan8b/
ls build/plan8b/edits.py build/plan8b/ledger.py
```

`edits.py` gives `edit(path, [(old, new, count), ...])`, `span(path, start, end, new)` and `regex(path, pattern,
replacement, count)`; `ledger.py` gives `rename(old, new, surface)`, `drop(path, successor)` and `add(path, owner,
surface, evidence, disposition)`. Every script below runs as `python3 -I - <<'PY'` from the worktree root and starts with
`import sys; sys.path.insert(0, "build/plan8b")`. Java text inside a script is in triple-quoted strings; where it holds a
Java backslash it is written `\\`.

## Review Focus

1. **A maximized pane moved to a new tab.** The old tab shows its other panes, no longer maximized, and the moved pane
   shows alone in its new tab, right after the old one. Task 2:
   `MovePaneToNewTabTest.aMaximizedPaneMovesOutAndItsOldTabShowsTheRest`.
2. **A split item whose registration closes, or whose plugin stops, while a window is open.** Its entries leave the Tab
   menu's Split submenu at once and the next context menu, and its actions leave the palette. Task 5:
   `SplitListsTest.anItemThatGoesLeavesEverySplitListAndThePalette`; Task 3's contract case for its actions and choices.
3. **Split with a choice from an editor that is not the focused pane** (a right-click on the other pane of a split
   tab). The new pane opens beside that editor. Task 4: the contract case
   `aContentTabSplitsBesideItsOwnPaneAndMovesItToANewTab` focuses the shell first.
4. **A third-party plugin whose id starts earlier than the Editor's** (`com.example…` starts before `dev.jasper.editor`).
   The Split lists still read Terminal, Editor, then its items. Task 3: the contract case declares a bundled plugin
   started last and pins it first among the items.
5. **A file opened from outside while an editor pane of a split tab is focused, or while the main window shows its empty
   workspace.** It opens in a new tab (the empty workspace taking it), never beside the focused pane. Task 6:
   `EditorWindowTest.aFileOpenedFromOutsideOpensInANewTabOfItsWindow` (beside a split tab, and into a window with no
   tab, the fake's empty workspace). The application's empty workspace taking a registry `NEW_TAB` is plan 8a's code and
   test (`ParentTabsViewTest.whatOpensWithoutAPaneOpensAsATabAndTheEmptyWorkspaceTakesTheFirst`). The staged Editor in a
   real main window is not reachable headlessly (`StagedEditorHost`'s windows are stand-in hosts), so Task 7's GUI
   checks hold that case.

## Verified facts

From `c1540b4e9` with plan 8a applied (see Workspace).

1. **Placements.** After 8a, `TabPlacement` (`jasper-sdk/.../ui/TabPlacement.java`) is `ACTIVE_GROUP` (Javadoc: a new
   tab), `SPLIT_RIGHT`, `SPLIT_DOWN`, `NEW_WINDOW`; `Windows.openTab`'s three-argument form passes `ACTIVE_GROUP`
   (`Windows.java:28-30`); `HostedUi` maps it to the registry's `NEW_TAB` (after 8a, `HostedUi.java:663`). Only the
   Editor names it among the plugins (`EditorWindow.java:310`, `:745`). `TabPlacement` came with SDK 0.20 on this branch;
   `main`'s SDK is 0.18.2.
2. **The fakes stack.** `FakeWorkspace` puts a new tab at the end of the active group and a split in a new group after
   the target's (`jasper-sdk-testkit/.../FakeWorkspace.java:105-147`; `FakeTerminals.java:187-197`;
   `FakeUi.java:532-536`); the app's `TerminalFixture` does the same (`jasper-app/src/test/.../plugins/TerminalFixture.java:121-162`,
   `:212-222`); the contract case `aPlacementPutsANewContentTabInTheActiveGroupBesideItOrInANewWindow`
   (`PluginContractTest.java:1651-1710`) pins it. Plan 8a leaves all of them unchanged (its Rulings 4).
3. **Actions.** `Contributions.addAction` refuses a repeated id across every contributor (`Contributions.java:85-96`);
   `WindowContributions.syncActions` gives every model action a window `Action` and a palette command
   (`WindowContributions.java:456-485`, after 8a); `HostedUi.actions()` adds a plugin's action to `ownActions`, which its
   menus, toolbar and status items may place (`HostedUi.java:191-209`). A contributed action's binding comes from
   `KeyBindings.withExtensions` (`config/KeyBindings.java:156-191`).
4. **Menus.** After 8a, the Tab menu is `GROUP_ITEMS` (Split Right, Split Down, the four Focus Pane items, the prompts),
   a rule, Rename Tab, a rule, the tab items (`WindowChrome.java:456-472`); a terminal's context menu is Copy, Paste,
   Find, Clear Terminal, Split Right, Split Down, Maximize Pane, Close Pane, a rule, the window menu's entries, then the
   contributed context sections (`:592-611`). The standard bar builds the Tab menu once (`:97`); the classic bar gathers
   it into Window and builds it again on every contribution pass (`ClassicMenuBar.fresh`, `WindowChrome.java:421`), which
   `WindowChromeMenusTest.theClassicWindowMenuHoldsTheTabMenuThenTheToolWindows` reads. A terminal's context menu opens
   after its pane is selected and focused (`TabHost.java:148-151`), so the focused pane is the one clicked.
5. **The Editor.** `EditorPlugin.start` registers `dev.jasper.editor.new` "New Editor File" (`EditorPlugin.java:109-110`),
   New Editor Window and its toolbar button (`:112-114`, `:139`) and puts Open, New and New Window in the File menu
   (`:135-138`). Every document the Editor opens goes through `EditorWindow.add(session, ref)` with `ACTIVE_GROUP`
   (`EditorWindow.java:744-746`): Cmd-click and `jasper editor` (`edit`, `openAt`), Open in Editor… (`openFrom`,
   `openLocations`), Recent Files (`RecentFilesScope` → `open`), a drop (`openLocalFiles`), `:e` and New File
   (`newUntitled`). Its right-click menu is RSyntaxTextArea's popup, which `DocumentArea.addToPopup` extends
   (`DocumentArea.java:130-135`).
6. **The Editor's ⌘W and ⇧⌘].** Its command `close-tab` "Close Tab", ⌘W / Ctrl+W, unbound in the text area
   (`EditorWindow.java:153`), runs before Jasper's bindings as a content command (`WindowContent.dispatchShortcut`, after
   8a `:1733-1751`): it closes the file's item, which after 8a closes its pane and its tab with its last pane. The
   Editor has had no next-tab or previous-tab command since `2288f6be0` (the skip at `EditorWindow.java:776` is dead
   code); after 8a ⇧⌘] and ⇧⌘[ are Jasper's Next and Previous Tab on macOS, and Ctrl+PageDown and Ctrl+PageUp elsewhere,
   while Ctrl+Shift+] and [ are bound to nothing (`docs/editor.md:54`, `:59`). Plan 8a's review fixes rewrite the ⌘W row
   to "Close Pane (the file)" and leave the command's own title, and the ⇧⌘] row, as they are.
7. **The collision check.** After 8a no built-in default and no bundled plugin's default is ⌥⌘T or Ctrl+Alt+Shift+T
   (8a Verified facts 9). Two of 8a's tests assert both keys free on each platform:
   `TabShortcutCollisionTest.theTabAndPaneDefaultsTakeNoBundledPluginsDefaultOnEitherPlatform` and
   `KeyBindingsTest.tabDefaultsFollowEachPlatformsTerminals` ("free for plan 8b's Move Pane to New Tab"). The Editor's
   content commands are ⌘S, ⇧⌘S, ⌥⌘S, ⌘N, ⌘W, ⌘F, ⌥⌘F, ⌘G, ⇧⌘G, ⌘L, ⌘/, ⌘C and ⌘V, Ctrl for ⌘ elsewhere (`EditorWindow.java:146-163`,
   `:780-781`). `macDefaultsUseCmdAndLeaveMacOsItsKeys` and `linuxDefaultsLeaveTheShellItsKeys`
   (`KeymapDefaultsTest.java:92-118`) admit Cmd and Ctrl+Shift chords.
8. **Start order and bundled plugins.** Plugins start dependencies first, then by id (`PluginResolver.order`,
   `PluginResolver.java:216-227`); `PluginRuntime` tells `FileEditors` the bundled ids before any plugin starts
   (`PluginRuntime.java:705-706`), and both contract harnesses' `declareBundled` set them before a start
   (`AppContractTest.java:560-563`, `FakePluginHost.java:1801-1810`).
9. **SDK signatures.** `PluginPresentationCheck.checkSdk` refuses a `javax.swing` or `java.awt` type in a public SDK
   signature except `Color`, `Dimension`, `Appearance`'s `Component`, `CustomContent`'s `JComponent` and
   `ContentCommand`'s `KeyStroke` (`gradle/architecture/PluginPresentationCheck.java:144-172`). Only `HostedUi` and
   `FakeUi` implement `Windows`, and only `HostedUi.OwnedContent` and `FakeContent` implement `ContentTab`.
10. **The ledger.** `ThemeMigrationCoverageTest.CANDIDATE` (`appearance/ThemeMigrationCoverageTest.java:17-18`) matches a
    `javax.swing` import or a `new ArrayList`, so `contributions/SplitItem.java`, the Editor's `ui/PaneItems.java` and the
    tests `MovePaneToNewTabTest` and `SplitListsTest` need rows; `SplitItemSpec` and `SplitChoice` do not match.
11. **Docs that name what changes.** `jasper-sdk/README.md:51-62` (after 8a: `ACTIVE_GROUP`, "a window's tabs sit in
    groups"), `docs/plugin-authoring.md:924` (after 8a), `docs/sdk-architecture.md:398-404`, `docs/command-palette.md:73-74`
    ("a new group to the right"), `docs/configuration.md:713`, `README.md:113` and `docs/editor.md` (Verified facts 6).
12. **Revert to Saved and Reload vimrc.** Both are Editor content commands with no shortcut (`EditorWindow.java:160`,
    `:164`); an editor's right-click menu lists Save, Find…, Go to Line… and Toggle Comment (`POPUP`, `:51`, `:759`), and
    each of its entries first selects that editor (`tabCommands`, `:756-757`). After 8a the pane strips and their ⋯
    menu are gone, the window's strip hides with one tab under the standard look, and the palette lists no content
    commands, so only the strip entry's menu reaches the two. `EditorWindow.revert()` and `reloadVimrc()` are public
    (`:542`, `:368`); `DocumentSession.revert` asks before discarding edits (`session/DocumentSession.java:143-148`).
    `docs/editor.md:10`, `:42`, `:61` and `:210` and `docs/keyboard-shortcuts.md:93` still name the ⋯ menu.
13. **`terminal.open`.** `Terminals.openTab` and `Terminals.split` require it for a local shell, through
    `CapabilityGate.require` in the application (`HostedTerminals.java:295`; the gate is `HostedContext.gate`, `:66`) and
    `FakeTerminals.require` (`:34`) in the fake (`FakeTerminals.java:208`, `:215`); the application audits each open
    (`HostedTerminals.java:275`, `:287`). The Editor declares it (`plugins/editor/src/main/resources/plugin.toml:11`);
    `EditorWindowTest`'s fake context declares no capability (`EditorWindowTest.java:63`). A plugin's action ids start
    with its id (`HostedUi.java:195`).

## Rulings

Record them in `docs/STATUS.md` (Task 7). "What — why".

1. **`TabPlacement.ACTIVE_GROUP` becomes `NEW_TAB`**, keeping plan 8a's Javadoc; `Windows.openTab`'s three-argument form
   passes it and `HostedUi` maps it to the registry's `NEW_TAB`. No `KitCompatibility` entry: no released SDK had
   `ACTIVE_GROUP` (Verified facts 1), and a 0.20 manifest's range is refused before any class is read. Reason: spec 11's
   clean break.
2. **The fakes take the two levels** (spec 4 and 13): a window lists its items tab by tab, then pane by pane; whatever
   opens without a pane (a scripted tab, `Terminals.openTab`, `NEW_TAB`, a moved tab) opens a new tab at the end of the
   strip; a split, a split placement and Split with a choice open a new pane right after the focused pane (the target's
   for `Terminals.split`), in its tab; the shown item closing selects its tab's pane used before it, or, with the tab's
   last pane, the tab shown before. `FakePluginHost.windowTabs` and `ContractHarness.windowTabs` show the strip. Reason:
   plan 8a's Rulings 4 leaves them to this plan; the contract can then pin the two levels on both runtimes.
3. **Split items:** `Windows.splitItem(SplitItemSpec, BiConsumer<WindowHandle, TabPlacement>)` returns a `Subscription`;
   `SplitItemSpec(id, label, icon)`. Its actions are the id's namespace and `split_right_` or `split_down_` before its
   last part (`dev.jasper.editor.editor` gives `dev.jasper.editor.split_right_editor`), titled "Split Right with <label>"
   and "Split Down with <label>", with no default and the keywords split, right or down, new, pane and the label's
   words; the plugin may place them as its own. Running one calls the opener with the window and `SPLIT_RIGHT` or
   `SPLIT_DOWN`. Reason: spec 10's `split_right_editor` and spec 8's "Split Right with Editor" follow from one id.
4. **Plugins reach the Split lists through the SDK:** `Windows.splitChoices()` gives them (`SplitChoice(label,
   rightAction, downAction)`, `SplitChoice.TERMINAL` first), and `ContentTab.split(SplitChoice, Direction)` runs one
   beside that item's pane, selecting it first; a choice not in the lists is refused. Reason: the Editor builds its own
   right-click menu (RSyntaxTextArea's popup), and spec 2.10 gives it the same list; the SDK passes no Swing type.
5. **The lists' order:** Terminal, then the bundled plugins' split items, then the others', each in the order they
   registered. Reason: spec 2.10 puts Editor Right and Down before every plugin's items, and the Editor, a bundled
   plugin, starts after any plugin whose id sorts first (Verified facts 8). With the Editor off, the lists read Terminal
   and the plugins' items.
6. **One Split list shape everywhere** (spec 2.10's): Terminal Right, Terminal Down, then each item's Right and Down. The
   Tab menu's Split Right and Split Down and a terminal's context menu's give way to a "Split" submenu holding it, whose
   Terminal entries run `split_right` and `split_down` and show their shortcuts. Reason: spec 8 names those menus as the
   lists' places, and the user said "lets move them under a submenu so we don't make the context menu huge".
7. **The toolbar's Split button keeps Split Right and Split Down.** Reason: spec 8 lists the Split lists' places and the
   toolbar is not one. Cost if wrong: its popup takes the list.
8. **The palette lists every Split entry as its own action:** Split Right, Split Down and each item's "Split Right with
   …" and "Split Down with …", as registered actions are palette commands. Reason: spec 8's "each one is its own
   bindable action".
9. **Move Pane to New Tab** (`move_pane_to_new_tab`, "Move Pane to New Tab", ⌥⌘T / Ctrl+Alt+Shift+T) moves the focused
   pane's item into a new tab right after its tab, which shows; nothing closes and nothing moves between windows (no
   `TAB_MOVED`). It is disabled for a tab's only pane (spec 9), a pinned item and one being closed. Reason: spec 9; spec 7
   keeps pinned and closing panes where they are.
10. **Menus:** the Tab menu reads Split ▸, Move Pane to New Tab, Focus Pane Left, Right, Up and Down, Previous and Next
    Prompt, a rule, Rename Tab, a rule, Next Tab, Previous Tab and Select Tab 1 to 9; a terminal's context menu reads
    Copy, Paste, Find, Clear Terminal, Split ▸, Move Pane to New Tab, Maximize Pane, Close Pane, a rule and the ⋮
    entries; the Editor's right-click menu ends with a rule, Split ▸ and Move Pane to New Tab. Reason: spec 2.10 and 9
    name the places; plan 8a's Rulings 17 ordered the rest.
11. **`ContentTab.moveToNewTab()` and `mayMoveToNewTab()`** give the Editor its context-menu entry: Move Pane to New Tab
    for that item, and whether it would move. Reason: spec 9 puts the entry in the Editor's context menu, which the
    plugin builds.
12. **The Editor's commands:** `dev.jasper.editor.new` "New Editor File" becomes `dev.jasper.editor.new_editor_tab` "New
    Editor Tab" (no default), an untitled file in a new tab; the Editor registers the split item
    `dev.jasper.editor.editor` labelled "Editor", whose actions `dev.jasper.editor.split_right_editor` "Split Right with
    Editor" and `dev.jasper.editor.split_down_editor` "Split Down with Editor" open an untitled file in a new pane beside
    the focused one; Revert to Saved and Reload vimrc become actions too (Rulings 20). Spec 10's bare ids
    (`new_editor_tab`, `split_right_editor`, `split_down_editor`) become these namespaced ones, because a plugin's action
    ids start with its id (Verified facts 13). New Editor Window and its toolbar button stay. The File menu reads Open in
    Editor…, New Editor Tab, Split Right with Editor, New Editor Window (Rulings 21). Reason: spec 8 and 10.
13. **New Editor Pane is Split Right with Editor** (`dev.jasper.editor.split_right_editor`, spec 10's
    `split_right_editor`): one action, as spec 10 has one row for it; the palette finds it by "new editor pane" (its
    keywords), the File menu shows it under its title (`PluginMenu.add` takes an action id and no label) and the Split
    lists show it as Editor Right. Reason: spec 8 names both; two actions for one command would put two rows in Settings › Keymap. Cost if
    wrong: a menu label or one more action.
14. **Every Editor open uses `NEW_TAB`:** Cmd-click, `jasper editor`, Open in Editor…, Recent Files, a dropped file,
    `:e` and New File open in a new tab of their window, the main window's empty workspace taking it. Reason: spec 2.8
    and 8; with one item per pane there is no stacking placement left.
15. **The Editor's ⌘W / Ctrl+W command is "Close Pane"** (id `close-pane`): it closes the file's pane, and its tab with
    its last pane, as Jasper's ⌘W does and as plan 8a's row in `docs/editor.md` already says, so a tab's menu no longer
    shows two "Close Tab"s that differ. `docs/editor.md`'s next and previous tab row names Jasper's keys, which switch the
    window's tabs. Reason: Verified facts 6.
16. **Versions:** the Editor takes the next minor (0.11.0 to 0.12.0 at the time of writing). No other plugin changes:
    Remote and Indexer open tabs and splits only through `Terminals`, whose order the fake keeps, and none names
    `ACTIVE_GROUP`. Reason: the series' ruling (next minor at execution).
17. **The app keeps split items in `Contributions`** (`SplitItem`, `addSplitItem`, `splitItems()`), a `MENUS` change.
    Reason: the Split lists are menus every window renders from the one model, as the contributed sections are.
18. **A content tab reaches its window through the registry:** Terminal Right and Down beside it go through
    `TabRegistry.splitTerminal` and `WorkspaceHost.splitTerminal`, Move Pane to New Tab through `mayMoveToNewTab` and
    `moveToNewTab`; a plugin's choice runs its action with the item selected. Reason: as `moveToNewWindow` reaches the
    window manager; the contract fixture plays the same host.
19. **Terminal Right and Terminal Down beside a content tab need `terminal.open`** (`ContentTab.split` with
    `SplitChoice.TERMINAL`): the check comes before anything is selected or opens, and the application audits the open as
    it audits `Terminals.split`'s. Reason: the new pane is a local shell, which `Terminals.split` and `openTab` already
    gate (Verified facts 13), and the SDK has no other way to run a built-in action; the Editor declares it already.
    Cost if wrong: a plugin without the capability gets a refusal where it might have expected a terminal.
20. **Revert to Saved and Reload vimrc become Editor actions** (`dev.jasper.editor.revert`,
    `dev.jasper.editor.reload_vimrc`, no default), so the palette and Settings › Keymap reach them; Revert to Saved also
    joins an editor's right-click menu. Each runs as its content command does, on the Editor's selected file, and Revert
    asks before discarding edits. Reason: after 8a only the strip entry's menu reaches them, and the strip hides with one
    tab (Verified facts 12). Cost if wrong: two extra palette entries.
21. **The Editor's File menu offers both of spec 2.7's choices:** New Editor Tab, then Split Right with Editor (New
    Editor Pane), before New Editor Window. Reason: spec 2.7 names "new editor tab, new editor pane in currently focused
    tab"; `PluginMenu.add(actionId)` takes no label, so the entry keeps its action's title. Cost if wrong: one File menu
    entry.
22. **The Split submenus show no icons,** though a split item's actions carry its icon for the palette. Reason: a
    uniform list; the Terminal entries have none, and macOS menus rarely show them. Cost if wrong: a cosmetic difference.

## File structure

- SDK `dev.jasper.sdk.ui`: `TabPlacement` (Task 1, replaced), `Windows` (Tasks 1, 3, 4), `ContentTab` (Task 4),
  `StandardMenu` (Task 5, Javadoc); new `SplitItemSpec`, `SplitChoice` (Task 3). `dev.jasper.sdk.terminal`: `Terminals`, `Direction` (Task 1, Javadoc).
- Testkit `dev.jasper.sdk.testing`: `FakeWorkspace` (Task 1, replaced; Task 4), `FakeTerminals` (Tasks 1, 4), `FakeUi`
  (Tasks 1, 3), `FakeContent` (Task 4), `FakePluginHost` (Tasks 1, 3); testFixtures `ContractHarness` (Task 1),
  `PluginContractTest` (Tasks 1, 3, 4).
- App `dev.jasper.app.contributions`: new `SplitItem`, `Contributions` (Task 3); `TabRegistry`, `WorkspaceHost` (Task 4,
  the latter replaced). `dev.jasper.app.commands`: `ActionId` (Task 2). `dev.jasper.app.shortcuthelp`:
  `ShortcutCatalog` (Task 2). `dev.jasper.app.plugins`: `HostedUi` (Tasks 1, 3, 4), `HostedContext` (Tasks 3, 4),
  `FileEditors` (Task 3). `dev.jasper.app.workspace`: `WorkspaceActions`, `WindowContent`, `ParentTabsView` (Task 2; `WindowContent`
  again in Task 4), `WindowChrome` (Tasks 2, 5).
- Editor: `EditorPlugin`, `ui/EditorWindow` (Tasks 1, 6), new `ui/PaneItems` (Task 6), `plugin.toml` (Task 6).
- Tests: each task's Files list.
- Documents: `docs/editor.md`, `docs/keyboard-shortcuts.md` (Task 6); `README.md`, `docs/configuration.md`, `docs/command-palette.md`,
  `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `jasper-sdk/README.md`, `docs/STATUS.md`, this plan's status
  banner (Task 7).

## Before you start

Plan 8a is executed first; its last commit is "Tabs and panes: documentation and status" (or its final fix wave).

```bash
git branch --show-current                                                                                    # claude/workspace-tabs
git status --short                                                                                           # nothing
grep -c '    ACTIVE_GROUP,' jasper-sdk/src/main/java/dev/jasper/sdk/ui/TabPlacement.java                     # 1
grep -c 'case ACTIVE_GROUP -> dev.jasper.app.contributions.TabRegistry.Placement.NEW_TAB;' jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java  # 1
grep -c 'MOVE_TAB_TO_NEW_WINDOW("move_tab_to_new_window"' jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java  # 1
grep -c 'private static final ActionId\[\] GROUP_ITEMS = {ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.FOCUS_PANE_LEFT,' jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java  # 1
grep -c 'version = "0.11.0"' plugins/editor/src/main/resources/plugin.toml                                   # 1 (else the Editor moved: take its next minor)
```

Every command prints the stated result. Each executed plan of this series added a `docs/STATUS.md` entry at the top;
this plan's goes above plan 8a's. Create the two helper scripts (Global Constraints).

---

### Task 1: `TabPlacement.NEW_TAB`, and two levels in the fakes

Spec 4, 11 and 13: the SDK's `ACTIVE_GROUP` becomes `NEW_TAB` (Rulings 1), and the testkit's fake and the app's contract
fixture take the two levels plan 8a gave the app (Rulings 2): a window's tabs, each holding panes, which
`windowTabs` shows. The contract pins `NEW_TAB`, the split placements, `Terminals.openTab`, `Terminals.split` and a moved
tab on both runtimes. The Editor names the new constant; its other changes wait for Task 6.

**Files:**
- Replace: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/TabPlacement.java`;
  `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeWorkspace.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Windows.java` (`openTab`'s three-argument form, after 8a `:18-30`),
  `terminal/TabHandle.java` (class Javadoc `:6-10`: the item one pane shows, spec 3);
  `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java` (the mapping, after 8a `:663`);
  `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java` (`:310`, `:745`);
  `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeTerminals.java` (`split` `:186-198`),
  `FakeUi.java` (`openComponent` `:529-536`), `FakePluginHost.java` (`addTerminalTab`'s and `addContentTab`'s Javadoc,
  `windowTabs`)
- Test: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java` (`windowTabs`),
  `PluginContractTest.java` (the placement case `:1651-1710` replaced, one case added);
  `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`;
  `jasper-app/src/test/java/dev/jasper/app/plugins/TerminalFixture.java`, `AppContractTest.java`

**Interfaces:**
- Consumes: plan 8a's `TabRegistry.Placement.NEW_TAB` and its `TabPlacement` Javadoc.
- Produces:
  - `dev.jasper.sdk.ui.TabPlacement`: `NEW_TAB`, `SPLIT_RIGHT`, `SPLIT_DOWN`, `NEW_WINDOW`;
  - `FakePluginHost.windowTabs(UUID windowId)` → `List<List<UUID>>` (public, `@since 0.21.0`) and
    `ContractHarness.windowTabs(UUID)` → `List<List<UUID>>`: the window's tabs in strip order, each its items pane by
    pane;
  - `FakeWorkspace` (package-private): `Group` is a tab of the strip; `addTab(Window, TabHandle.Kind, String, Tab beside)`,
    `openTerminal(Window, String, PaneInfo, Tab beside)` (a new pane right after `beside` in its tab, or a new tab when it
    is null), `tabsOf(Window)` → `List<List<UUID>>`;
  - `TerminalFixture.windowTabs(UUID)` → `List<List<UUID>>`; its split and `addContentTab(UUID, UUID, Supplier<String>,
    boolean beside)` place beside the window's selected item.

- [ ] **Step 1: Write the failing tests**

Add `windowTabs` to the harness and its two implementations, replace the placement case and add a case for terminals:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8b")
from edits import edit, span
K = "jasper-sdk-testkit/src/"
H = K + "testFixtures/java/dev/jasper/sdk/testing/contract/"
edit(H + "ContractHarness.java", [(
    """    /** UI thread: adds and selects a content tab, as the application or another plugin shows a document; it has no pane. */
    java.util.UUID addContentTab(java.util.UUID windowId, String title);
""", """    /** UI thread: adds and selects a content tab, as the application or another plugin shows a document; it has no pane. */
    java.util.UUID addContentTab(java.util.UUID windowId, String title);

    /**
     * UI thread: a window's tabs as its strip shows them (tabs and panes spec 4), each tab's items pane by pane, by the
     * ids {@code TabHandle.id()} reports; empty for a window that is not open.
     */
    List<List<java.util.UUID>> windowTabs(java.util.UUID windowId);
""", 1)])
edit(K + "test/java/dev/jasper/sdk/testing/FakeContractTest.java", [(
    "            @Override public void closeTab(java.util.UUID tabId) { host.closeTab(tabId); }\n",
    """            @Override public void closeTab(java.util.UUID tabId) { host.closeTab(tabId); }
            @Override public List<List<java.util.UUID>> windowTabs(java.util.UUID windowId) { return host.windowTabs(windowId); }
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java", [(
    "            @Override public void closeTab(UUID tabId) { onEdt(() -> terminalFixture.closeTab(tabId)); }\n",
    """            @Override public void closeTab(UUID tabId) { onEdt(() -> terminalFixture.closeTab(tabId)); }
            @Override public List<List<UUID>> windowTabs(UUID windowId) { return onEdtValue(() -> terminalFixture.windowTabs(windowId)); }
""", 1)])
C = H + "PluginContractTest.java"
span(C, "    /** Workspace tabs spec 14's TabPlacement: the end of the active group, a new group after it, or a new child window. */\n",
    """            assertThat(below.get().window().id()).isEqualTo(main);
        });
    }
""", """    /**
     * Tabs and panes spec 11's TabPlacement: NEW_TAB opens a new tab at the end of the window's strip, SPLIT_RIGHT and
     * SPLIT_DOWN a new pane right after the focused pane, in its tab, and NEW_WINDOW a new child window.
     */
    @Test void aPlacementOpensANewTabANewPaneBesideTheFocusedOneOrANewWindow() {
        UUID main = h.addTerminalWindow(), left = h.addTerminalTab(main, "left");
        UUID pane = h.addTerminalPane(left, "zsh", Path.of("/"));
        UUID right = h.addTerminalTab(main, "right");
        h.addTerminalPane(right, "zsh", Path.of("/"));
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), context -> {
            alpha.set(context);
            for (var topic : List.of(TerminalEvents.WINDOW_OPENED, TerminalEvents.TAB_OPENED, TerminalEvents.TAB_SELECTED))
                context.events().subscribe(topic, event -> heard.add(java.util.Map.entry(topic, event)));
        });
        var below = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
        var placed = new AtomicReference<List<UUID>>();
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            var windows = alpha.get().windows();
            var owner = terminals.window(main).orElseThrow();
            UUID split = terminals.split(terminals.pane(pane).orElseThrow(), Direction.RIGHT, OpenRequest.local()).orElseThrow().tab().id();
            windows.openTab(new dev.jasper.sdk.ui.ContentSpec("here", "Here"), owner, kitView(alpha.get()));
            UUID here = owner.activeTab().orElseThrow().id();
            below.set(windows.openTab(new dev.jasper.sdk.ui.ContentSpec("below", "Below"), owner, kitView(alpha.get()),
                dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN));
            UUID under = owner.activeTab().orElseThrow().id();
            terminals.tab(left).orElseThrow().select();
            windows.openTab(new dev.jasper.sdk.ui.ContentSpec("beside", "Beside"), owner, kitView(alpha.get()),
                dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
            UUID beside = owner.activeTab().orElseThrow().id();
            windows.openTab(new dev.jasper.sdk.ui.ContentSpec("next", "Next"), null, kitView(alpha.get()),
                dev.jasper.sdk.ui.TabPlacement.NEW_TAB);
            UUID next = owner.activeTab().orElseThrow().id();
            placed.set(List.of(left, beside, split, right, here, under, next));
            assertThat(owner.tabs()).extracting(TabHandle::id).as("tab by tab, then pane by pane").containsExactlyElementsOf(placed.get());
        });
        List<UUID> ids = placed.get();
        assertThat(h.windowTabs(main)).as("a new tab at the end of the strip, a new pane right after the focused one").containsExactly(
            List.of(ids.get(0), ids.get(1), ids.get(2)), List.of(ids.get(3)), List.of(ids.get(4), ids.get(5)), List.of(ids.get(6)));
        h.flush();
        heard.clear();
        var away = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
        h.ui(() -> away.set(alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("away", "Away"),
            alpha.get().terminals().window(main).orElseThrow(), kitView(alpha.get()), dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW)));
        h.flush();
        var child = new AtomicReference<List<UUID>>();
        h.ui(() -> child.set(List.of(away.get().window().id(), away.get().window().tabs().getFirst().id())));
        assertThat(heard).as("the new child window opens, then the tab, selected in it").containsExactly(
            java.util.Map.entry(TerminalEvents.WINDOW_OPENED, new TerminalEvents.WindowEvent(child.get().get(0))),
            java.util.Map.entry(TerminalEvents.TAB_OPENED, new TerminalEvents.TabEvent(child.get().get(0), child.get().get(1))),
            java.util.Map.entry(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(child.get().get(0), child.get().get(1))));
        assertThat(h.windowTabs(child.get().get(0))).containsExactly(List.of(child.get().get(1)));
        h.ui(() -> {
            var owner = alpha.get().terminals().window(main).orElseThrow();
            assertThat(away.get().window().isMain()).as("a new child window").isFalse();
            assertThat(owner.tabs()).as("the owner's window is not touched").hasSize(7);
            assertThat(owner.activeTab().map(TabHandle::id)).as("nor its active tab").contains(ids.get(6));
            assertThat(alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("below", "Again"), null, kitView(alpha.get()),
                dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW)).as("an open key is selected where it is").isSameAs(below.get());
            assertThat(below.get().window().id()).isEqualTo(main);
        });
    }

    /**
     * Tabs and panes spec 4 and 13: Terminals.openTab opens a new tab, a split a new pane right after its target, in its
     * tab, and a tab moved in arrives as a tab of its own at the end of the strip.
     */
    @Test void openTabMakesANewTabASplitAPaneOfItsTargetsTabAndAMovedTabATabOfItsOwn() {
        UUID main = h.addTerminalWindow(), first = h.addTerminalTab(main, "first");
        UUID pane = h.addTerminalPane(first, "zsh", Path.of("/"));
        UUID other = h.addTerminalWindow(), stays = h.addTerminalTab(other, "stays");
        h.addTerminalPane(stays, "zsh", Path.of("/"));
        UUID moving = h.addTerminalTab(other, "moving");
        h.addTerminalPane(moving, "make", Path.of("/src"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), alpha::set);
        var opened = new AtomicReference<List<UUID>>();
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            UUID down = terminals.split(terminals.pane(pane).orElseThrow(), Direction.DOWN, OpenRequest.local()).orElseThrow().tab().id();
            UUID right = terminals.split(terminals.pane(pane).orElseThrow(), Direction.RIGHT, OpenRequest.local()).orElseThrow().tab().id();
            UUID tab = terminals.openTab(terminals.window(main).orElseThrow(), OpenRequest.local()).orElseThrow().tab().id();
            opened.set(List.of(down, right, tab));
        });
        UUID down = opened.get().get(0), right = opened.get().get(1), tab = opened.get().get(2);
        assertThat(h.windowTabs(main)).as("splits in their target's tab, right after it; openTab a tab of its own")
            .containsExactly(List.of(first, right, down), List.of(tab));
        h.moveTab(moving, main);
        assertThat(h.windowTabs(main)).as("a moved tab arrives as a tab of its own")
            .containsExactly(List.of(first, right, down), List.of(tab), List.of(moving));
        assertThat(h.windowTabs(other)).containsExactly(List.of(stays));
    }
""")
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeContractTest'`
Expected: FAIL to compile: `FakePluginHost` has no `windowTabs`, and `TabPlacement` has no `NEW_TAB`.

- [ ] **Step 3: Implement**

Replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/ui/TabPlacement.java` with:

```java
package dev.jasper.sdk.ui;

/**
 * Where {@link Windows#openTab(ContentSpec, dev.jasper.sdk.WindowOwner, UiView, TabPlacement)} puts a new content tab
 * (tabs and panes spec 11). A content tab is an item, the one a pane shows: a window holds tabs, and each tab holds
 * panes. A tab already open for the same key is selected where it is, whatever the placement.
 *
 * @since 0.20.0
 */
public enum TabPlacement {
    /**
     * In a new tab of the owner's window, or of the main window when there is no owner: at the end of its tab strip and
     * shown, the main window's empty workspace taking it in its place.
     */
    NEW_TAB,
    /** In a new pane to the right of the focused pane of that window's shown tab. */
    SPLIT_RIGHT,
    /** In a new pane below the focused pane of that window's shown tab. */
    SPLIT_DOWN,
    /**
     * In a new child window of its own, opened for it; the owner's window is not touched. The topics report
     * {@code WINDOW_OPENED}, then {@code TAB_OPENED} in the new window.
     */
    NEW_WINDOW
}
```

Replace the whole of `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeWorkspace.java` with:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.TerminalEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The windows, tabs and panes a test scripts, with the application's rules (workspace tabs spec 6 and 15; tabs and panes
 * spec 4 and 13). The window opened while no main window is open is the main window, which stays open with an empty
 * workspace when its last tab leaves; any other window is a child window, which closes with its last tab. A window holds
 * tabs in strip order, and a tab holds panes, each showing one item, which the SDK calls a tab ({@code TabHandle}); a
 * window lists its items tab by tab, then pane by pane. Whatever opens without a pane, a moved item included, opens in a
 * new tab at the end of the strip; a split or a placement beside a pane opens a new pane right after it, in its tab.
 * Closing the selected item selects the item its tab showed before it, or, with the tab's last pane, the tab shown
 * before. A terminal item holds one terminal pane, the active pane is the terminal pane of the selected item of the
 * window activated last, focusing a pane selects its item, a pane closes with its item, an item moved to another window
 * is announced once as a move, and every change is announced on the terminal topics.
 */
final class FakeWorkspace {
    static final class Pane {
        final UUID id = UUID.randomUUID(); final Tab tab; PaneInfo info; String selection; String job; boolean open = true;
        FakeSessions.Session session;
        /**
         * Set while the pane is held (restored and not shown yet): the snapshot it starts with. A held pane reports
         * NOT_STARTED with no grid, drops what is sent to it, and starts on its tab's next selection or focus.
         */
        PaneInfo held;
        final List<String> sent = new ArrayList<>();
        Pane(Tab tab, PaneInfo info) { this.tab = tab; this.info = info; }
    }
    /** One tab of a window's strip: its items, one per pane; {@code recent} holds them most recently selected first. */
    static final class Group {
        final List<Tab> recent = new ArrayList<>();
    }
    /** An item, the one a pane shows: what the SDK calls a tab. */
    static final class Tab {
        final UUID id = UUID.randomUUID(); final TabHandle.Kind kind; Window window; Group group; String title; Pane pane; boolean open = true;
        Tab(Window window, TabHandle.Kind kind, String title) { this.window = window; this.kind = kind; this.title = title; }
    }
    static final class Window {
        final UUID id = UUID.randomUUID();
        /** Whether this is the main window, which outlives its last tab. */
        final boolean main;
        /** Every item, tab by tab in strip order, then pane by pane. */
        final List<Tab> tabs = new ArrayList<>();
        /** The window's tabs, most recently shown first: the first is the shown tab. */
        final List<Group> recent = new ArrayList<>();
        Tab selected; boolean open = true;
        Window(boolean main) { this.main = main; }
        /** @return the shown tab, or null in an empty workspace */
        Group active() { return recent.isEmpty() ? null : recent.getFirst(); }
    }

    private final FakePluginHost host;
    final List<Window> windows = new ArrayList<>();
    /** Every pane ever added, so a test can still read what was sent to one that closed. */
    private final List<Pane> everyPane = new ArrayList<>();
    private final List<Tab> everyTab = new ArrayList<>();
    final List<String> openRequests = new ArrayList<>();
    /** The shell profiles the test says the application detected. */
    List<ShellProfileInfo> profiles = List.of();
    /** Every live {@code onProfilesChanged} listener, of every plugin. */
    final List<Runnable> profileListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    Window lastActive;
    private UUID reportedActive;

    final FakeSessions sessions;

    FakeWorkspace(FakePluginHost host) { this.host = host; this.sessions = new FakeSessions(host, this); }

    List<Pane> everyOpenPane() { return everyPane.stream().filter(pane -> pane.open).toList(); }

    Optional<Window> window(UUID id) { return windows.stream().filter(window -> window.id.equals(id)).findFirst(); }
    /** @return the main window, while it is open */
    Optional<Window> main() { return windows.stream().filter(window -> window.main).findFirst(); }
    Optional<Tab> tab(UUID id) { return everyTab.stream().filter(tab -> tab.open && tab.id.equals(id)).findFirst(); }
    Optional<Pane> pane(UUID id) { return everyPane.stream().filter(pane -> pane.open && pane.id.equals(id)).findFirst(); }
    Optional<Pane> anyPane(UUID id) { return everyPane.stream().filter(pane -> pane.id.equals(id)).findFirst(); }
    Optional<Pane> activePane() {
        return Optional.ofNullable(lastActive).filter(window -> window.open).map(window -> window.selected).map(tab -> tab.pane);
    }

    /** The window's tabs in strip order, each its items' ids pane by pane. */
    List<List<UUID>> tabsOf(Window window) {
        var tabs = new ArrayList<List<UUID>>();
        Group current = null;
        for (Tab tab : window.tabs) {
            if (tab.group != current) { tabs.add(new ArrayList<>()); current = tab.group; }
            tabs.getLast().add(tab.id);
        }
        return tabs.stream().map(List::copyOf).toList();
    }

    private void refreshActive() {
        UUID now = activePane().map(pane -> pane.id).orElse(null);
        if (Objects.equals(now, reportedActive)) return;
        reportedActive = now;
        host.publishApp(TerminalEvents.ACTIVE_PANE_CHANGED, new TerminalEvents.ActivePaneChanged(Optional.ofNullable(now)));
    }

    /** A window: the main window when none is open, else a child window. */
    Window addWindow() {
        var window = new Window(main().isEmpty());
        windows.add(window);
        host.publishApp(TerminalEvents.WINDOW_OPENED, new TerminalEvents.WindowEvent(window.id));
        return window;
    }

    /** A terminal item in a new tab, selected; {@link #addPane} gives it its one terminal pane. */
    Tab addTab(Window window, String title) { return addTab(window, TabHandle.Kind.TERMINAL, title, null); }

    /** An item of {@code kind} in a new tab at the end of the strip, selected. */
    Tab addTab(Window window, TabHandle.Kind kind, String title) { return addTab(window, kind, title, null); }

    /** An item of {@code kind}, selected, in a new pane right after {@code beside}; in a new tab when it is null. */
    Tab addTab(Window window, TabHandle.Kind kind, String title, Tab beside) {
        var tab = announceTab(window, kind, title, beside);
        select(tab);
        return tab;
    }

    /**
     * A terminal item with its one pane in a new tab, as the application opens one for {@code openTab}: announced, given
     * its pane, then selected, so the active pane goes straight to the new pane.
     */
    Pane openTerminal(Window window, String title, PaneInfo info) { return openTerminal(window, title, info, null); }

    /** As {@link #openTerminal(Window, String, PaneInfo)}, a split's item in a new pane right after {@code beside}. */
    Pane openTerminal(Window window, String title, PaneInfo info, Tab beside) {
        var pane = addPane(announceTab(window, TabHandle.Kind.TERMINAL, title, beside), info);
        select(pane.tab);
        return pane;
    }

    private Tab announceTab(Window window, TabHandle.Kind kind, String title, Tab beside) {
        var tab = new Tab(window, kind, title);
        everyTab.add(tab);
        place(tab, window, beside);
        host.publishApp(TerminalEvents.TAB_OPENED, new TerminalEvents.TabEvent(window.id, tab.id));
        return tab;
    }

    /**
     * Puts {@code tab} in a new pane right after {@code beside}, in its tab, or in a new tab at the end of the strip when
     * {@code beside} is null or not an open item of {@code window}.
     */
    private void place(Tab tab, Window window, Tab beside) {
        Group group;
        int index;
        if (beside != null && beside.open && beside.window == window) {
            group = beside.group;
            index = window.tabs.indexOf(beside) + 1;
        } else {
            group = new Group();
            window.recent.add(group);
            index = window.tabs.size();
        }
        tab.window = window;
        tab.group = group;
        window.tabs.add(index, tab);
        group.recent.add(tab);
    }

    void select(Tab tab) {
        if (!tab.open) return;
        Window window = tab.window;
        tab.group.recent.remove(tab);
        tab.group.recent.addFirst(tab);
        window.recent.remove(tab.group);
        window.recent.addFirst(tab.group);
        if (window.selected != tab) {
            window.selected = tab;
            host.publishApp(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(window.id, tab.id));
            refreshActive();
            // As the application's window does on every selection: each content tab is active while it is its window's selected tab.
            for (var content : host.contentTabs())
                if (content instanceof FakeContent shown && !shown.closed && shown.tab.window == window) shown.active(shown.tab == tab);
        }
        // A held pane starts once its tab is shown, after the selection, as Jasper starts a restored terminal tab at the
        // end of the turn that shows it (workspace tabs spec 13).
        if (tab.pane != null && tab.pane.held != null) start(tab.pane);
    }

    /** A held pane starts: its local shell runs, with the grid it had before it was held. */
    private void start(Pane pane) {
        pane.info = FakeTerminals.withState(pane.held, dev.jasper.sdk.terminal.SessionState.RUNNING, java.util.OptionalInt.empty());
        pane.held = null;
        host.publishApp(TerminalEvents.SESSION_STATE_CHANGED,
            new TerminalEvents.SessionStateChanged(pane.id, dev.jasper.sdk.terminal.SessionState.RUNNING, java.util.OptionalInt.empty()));
    }

    /** A terminal item's one pane; a second, or one in a content item, is refused. */
    Pane addPane(Tab tab, PaneInfo info) {
        if (tab.kind != TabHandle.Kind.TERMINAL) throw new IllegalArgumentException("A content tab holds no pane");
        if (tab.pane != null) throw new IllegalStateException("A terminal tab holds one pane");
        var pane = new Pane(tab, info);
        everyPane.add(pane);
        tab.pane = pane;
        host.publishApp(TerminalEvents.PANE_OPENED, new TerminalEvents.PaneEvent(tab.id, pane.id));
        refreshActive();
        return pane;
    }

    void activate(Window window) {
        if (!window.open) return;
        lastActive = window;
        host.publishApp(TerminalEvents.WINDOW_ACTIVATED, new TerminalEvents.WindowEvent(window.id));
        refreshActive();
    }

    void focus(Pane pane) {
        if (!pane.open) return;
        select(pane.tab);
        host.publishApp(TerminalEvents.PANE_FOCUSED, new TerminalEvents.PaneEvent(pane.tab.id, pane.id));
        refreshActive();
    }

    /**
     * Moves an item to a new tab at the end of another open window's strip, where it is selected; a child window left
     * with no item closes after the move is announced.
     */
    void move(Tab tab, Window to) {
        if (!tab.open || !to.open || tab.window == to) return;
        Window from = tab.window;
        if (takeOut(tab)) reselect(from);
        // As the application's window does: the active pane is reported as soon as the source lets the tab go.
        refreshActive();
        place(tab, to, null);
        host.publishApp(TerminalEvents.TAB_MOVED, new TerminalEvents.TabMoved(tab.id, from.id, to.id));
        select(tab);
        if (from.tabs.isEmpty() && !from.main) closeWindow(from);
        refreshActive();
    }

    /** A pane closes with its item. */
    void close(Pane pane) { if (pane.open) close(pane.tab); }

    /** Closes an item, its terminal pane with it; a child window's last item takes the window with it. */
    void close(Tab tab) {
        if (!tab.open) return;
        Window window = tab.window;
        Pane pane = tab.pane;
        if (pane != null && pane.open) {
            sessions.closing(pane);
            pane.open = false;
            host.publishApp(TerminalEvents.PANE_CLOSED, new TerminalEvents.PaneEvent(tab.id, pane.id));
        }
        tab.open = false;
        boolean selected = takeOut(tab);
        host.publishApp(TerminalEvents.TAB_CLOSED, new TerminalEvents.TabEvent(window.id, tab.id));
        if (selected) reselect(window);
        if (window.tabs.isEmpty() && !window.main) closeWindow(window);
        refreshActive();
    }

    /** Takes {@code tab} out of its window's tabs, its tab going with its last pane; true when it was the selected item. */
    private boolean takeOut(Tab tab) {
        Window window = tab.window;
        window.tabs.remove(tab);
        tab.group.recent.remove(tab);
        if (tab.group.recent.isEmpty()) window.recent.remove(tab.group);
        if (window.selected != tab) return false;
        window.selected = null;
        return true;
    }

    /** Its selected item left: the item its tab showed before it, else the tab shown before, takes over. */
    private void reselect(Window window) {
        if (!window.recent.isEmpty()) select(window.active().recent.getFirst());
    }

    private void closeWindow(Window window) {
        window.open = false;
        windows.remove(window);
        if (lastActive == window) lastActive = null;
        host.closeToolbarViews(window.id);
        host.publishApp(TerminalEvents.WINDOW_CLOSED, new TerminalEvents.WindowEvent(window.id));
        refreshActive();
    }
}
```

Then run the edit script for the SDK, the app's mapping, the Editor's two placements and the fakes:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8b")
from edits import edit, span
S = "jasper-sdk/src/main/java/dev/jasper/sdk/"
edit(S + "ui/Windows.java", [
    ("({@link TabPlacement#ACTIVE_GROUP})", "({@link TabPlacement#NEW_TAB})", 1),
    ("        return openTab(spec, owner, content, TabPlacement.ACTIVE_GROUP);\n",
     "        return openTab(spec, owner, content, TabPlacement.NEW_TAB);\n", 1)])
edit(S + "terminal/TabHandle.java", [(
    """ * One tab of a Jasper window: a terminal tab, whose {@link #terminal()} is its one pane, or a content tab, such as an
 * editor, a tool view or a plugin's view. Handles keep an id and no Swing object; two handles for the same tab are equal.
""", """ * One item of a Jasper window, the one a pane shows, which the SDK calls a tab (tabs and panes spec 3): a terminal tab,
 * whose {@link #terminal()} is its one pane, or a content tab, such as an editor, a tool view or a plugin's view. A
 * window holds tabs in its tab strip, each holding panes, and {@link WindowHandle#tabs()} lists their items. Handles keep
 * an id and no Swing object; two handles for the same tab are equal.
""", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java", [(
    "                    case ACTIVE_GROUP -> dev.jasper.app.contributions.TabRegistry.Placement.NEW_TAB;\n",
    "                    case NEW_TAB -> dev.jasper.app.contributions.TabRegistry.Placement.NEW_TAB;\n", 1)])
edit("plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java", [
    ("    public void newUntitled(WindowOwner owner) { newUntitled(owner, dev.jasper.sdk.ui.TabPlacement.ACTIVE_GROUP); }\n",
     "    public void newUntitled(WindowOwner owner) { newUntitled(owner, dev.jasper.sdk.ui.TabPlacement.NEW_TAB); }\n", 1),
    ("        add(session, ownerRef, dev.jasper.sdk.ui.TabPlacement.ACTIVE_GROUP);\n",
     "        add(session, ownerRef, dev.jasper.sdk.ui.TabPlacement.NEW_TAB);\n", 1)])
K = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/"
edit(K + "FakeTerminals.java", [
    ("            // A split is a terminal tab of its own in a new group after the target's, as in the application (spec 9.4).\n",
     "            // A split is a terminal tab of its own in a new pane right after the target, in its tab (tabs and panes spec 4).\n", 1),
    ("model.tab.group);", "model.tab);", 2)])
edit(K + "FakeUi.java", [(
    """                var beside = placement == dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT || placement == dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN ? shown.active() : null;
""", """                // A split placement opens a new pane right after the window's selected item; NEW_TAB opens a new tab.
                var beside = placement == dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT || placement == dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN ? shown.selected : null;
""", 1)])
edit(K + "FakePluginHost.java", [
    ("     * Adds and selects a terminal tab; {@link #addTerminalPane} gives it its one pane.\n",
     """     * Adds and selects a terminal tab, in a new tab of its own at the end of the window's strip (tabs and panes spec 4);
     * {@link #addTerminalPane} gives it its one pane.
""", 1),
    ("""     * Adds and selects a content tab, as the application or another plugin shows a document or a tool view: a tab with no
     * pane, which {@code TabHandle.kind()} reports as {@code CONTENT}.
""", """     * Adds and selects a content tab, as the application or another plugin shows a document or a tool view: a tab with no
     * terminal pane, which {@code TabHandle.kind()} reports as {@code CONTENT}, in a new tab of its own at the end of the
     * window's strip.
""", 1),
    ("""    /**
     * Moves a tab the test added to another open window, where it is selected, as dragging it there does: a terminal
""", """    /**
     * A window's tabs as its strip shows them (tabs and panes spec 4), each tab's items pane by pane: the ids
     * {@code TabHandle.id()} reports.
     *
     * @param windowId an open window
     * @return the tabs in strip order, each a list of item ids
     * @since 0.21.0
     */
    public List<List<UUID>> windowTabs(UUID windowId) {
        return workspace.tabsOf(workspace.window(windowId).orElseThrow(() -> new IllegalArgumentException("No such window: " + windowId)));
    }

    /**
     * Moves a tab the test added to a new tab at the end of another open window's strip, where it is selected, as
     * dragging it there does: a terminal
""", 1)])
F = "jasper-app/src/test/java/dev/jasper/app/plugins/TerminalFixture.java"
edit(F, [
    (""" * A scripted workspace behind a real {@link TerminalRegistry}: what windows do, without windows. A terminal tab holds one
 * pane, a split opens a terminal tab of its own in a new group after the pane's, focusing a pane selects its tab, and a
 * pane closes with its tab (workspace tabs spec 9.4). A window's tabs sit in groups, listed group by group, as the
 * testkit's fake keeps them: a new tab opens at the end of the active group, closing a group's selected tab selects the
 * tab the group showed before it, and closing a group's last tab makes the group used before it active. A main window
""", """ * A scripted workspace behind a real {@link TerminalRegistry}: what windows do, without windows. A terminal tab holds one
 * pane, a split opens a terminal tab of its own in a new pane right after the pane's, in its tab, focusing a pane
 * selects its tab, and a pane closes with its tab. A window holds tabs, each holding panes, as the testkit's fake keeps
 * them (tabs and panes spec 4; the registry calls each pane's item a tab): its items are listed tab by tab, then pane
 * by pane, whatever opens without a pane opens in a new tab at the end of the strip, closing the selected item selects
 * the item its tab showed before it, and closing a tab's last pane shows the tab shown before it. A main window
""", 1),
    ("""    /** One group of a window's tabs; {@code recent} holds them most recently selected first. */
""", """    /** One tab of a window's strip: its items, one per pane; {@code recent} holds them most recently selected first. */
""", 1),
    ("        /** The groups that hold tabs, most recently active first: the first is the active group. */\n",
     "        /** The window's tabs, most recently shown first: the first is the shown tab. */\n", 1),
    ("                // A split is a terminal tab of its own, in a new group beside the pane's group (spec 9.4).\n",
     "                // A split is a terminal tab of its own, in a new pane right after the pane's, in its tab (tabs and panes spec 4).\n", 1),
    ("pane.tab.group);", "pane.tab);", 2),
    ("""    /** A terminal tab at the end of the active group, selected; {@link #addPane} gives it its one pane. */
    UUID addTab(UUID windowId, String title) { return addTab(windowId, title, null); }

    /** A terminal tab, selected, in a new group after {@code beside}; at the end of the active group when it is null. */
    private UUID addTab(UUID windowId, String title, Group beside) {
""", """    /** A terminal tab in a new tab at the end of the strip, selected; {@link #addPane} gives it its one pane. */
    UUID addTab(UUID windowId, String title) { return addTab(windowId, title, null); }

    /** A terminal tab, selected, in a new pane right after {@code beside}; in a new tab when it is null. */
    private UUID addTab(UUID windowId, String title, Tab beside) {
""", 1),
    ("""     * A terminal tab with its one pane, as a window opens one for {@code openTab} or a split (a split's in a new group
     * after {@code beside}): announced, given its pane, then selected, so the active pane goes straight to the new pane.
     * Returns the pane's id.
     */
    private UUID openTerminal(UUID windowId, String title, Path directory, Group beside) {
""", """     * A terminal tab with its one pane, as a window opens one for {@code openTab} or a split (a split's in a new pane right
     * after {@code beside}, in its tab): announced, given its pane, then selected, so the active pane goes straight to the
     * new pane. Returns the pane's id.
     */
    private UUID openTerminal(UUID windowId, String title, Path directory, Tab beside) {
""", 1),
    ("    private Tab newTab(Window window, String title, Group beside) {\n",
     "    private Tab newTab(Window window, String title, Tab beside) {\n", 1),
    ("    /** Takes {@code tab} out of its window's groups; true when it was the window's selected tab. */\n",
     "    /** Takes {@code tab} out of its window's tabs, its tab going with its last pane; true when it was the selected tab. */\n", 1),
    ("    /** Its selected tab left: the tab the active group showed before it, else the group used before, takes over. */\n",
     "    /** Its selected tab left: the item its tab showed before it, else the tab shown before, takes over. */\n", 1),
    ("""    /** A content tab, such as a plugin's view, with the id and title its window gives it, at the end of the active group; it opens selected. */
    void addContentTab(UUID windowId, UUID tabId, Supplier<String> title) { addContentTab(windowId, tabId, title, false); }

    /** As above; with {@code beside}, in a new group after the active group, as a split placement opens it. */
""", """    /** A content tab, such as a plugin's view, with the id and title its window gives it, in a new tab at the end of the strip; it opens selected. */
    void addContentTab(UUID windowId, UUID tabId, Supplier<String> title) { addContentTab(windowId, tabId, title, false); }

    /** As above; with {@code beside}, in a new pane right after the window's selected item, as a split placement opens it. */
""", 1),
    ("        place(tab, window, beside ? window.active() : null);\n", "        place(tab, window, beside ? window.selected : null);\n", 1),
    ("     * Moves a tab to the end of another window's active group, where it is selected: the move is reported once, its pane\n",
     "     * Moves a tab to a new tab at the end of another window's strip, where it is selected: the move is reported once, its pane\n", 1),
    ("    private UUID openSession(UUID windowId, SessionRequest request, Group beside) {\n",
     "    private UUID openSession(UUID windowId, SessionRequest request, Tab beside) {\n", 1),
    ("    List<String> opened() { return List.copyOf(opened); }\n", """    List<String> opened() { return List.copyOf(opened); }

    /** A window's tabs in strip order, each its items' ids pane by pane (tabs and panes spec 4); empty for a closed window. */
    List<List<UUID>> windowTabs(UUID windowId) {
        Window window = windows.get(windowId);
        var result = new ArrayList<List<UUID>>();
        if (window == null) return result;
        Group current = null;
        for (Tab tab : window.tabs) {
            if (tab.group != current) { result.add(new ArrayList<>()); current = tab.group; }
            result.getLast().add(tab.id);
        }
        return result.stream().map(List::copyOf).toList();
    }
""", 1)])
span(F, "    /** Puts {@code tab} at the end of {@code window}'s active group, or in a new group after {@code beside}. */\n",
    "        group.recent.add(tab);\n    }\n", """    /**
     * Puts {@code tab} in a new pane right after {@code beside}, in its tab, or in a new tab at the end of {@code window}'s
     * strip when {@code beside} is null or not one of its items.
     */
    private void place(Tab tab, Window window, Tab beside) {
        Group group;
        int index;
        if (beside != null && beside.window == window && window.tabs.contains(beside)) {
            group = beside.group;
            index = window.tabs.indexOf(beside) + 1;
        } else {
            group = new Group();
            window.recent.add(group);
            index = window.tabs.size();
        }
        tab.window = window;
        tab.group = group;
        window.tabs.add(index, tab);
        group.recent.add(tab);
    }
""")
PY
```

Plan 8a already rewords `Terminals.split`, `Direction` and `TerminalEvents` for panes. `FakeWorkspace`'s `move` and the
fixture's `moveTab` keep their bodies: with the new `place`, a moved tab opens a new tab at the end of the strip.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-plugin-editor:test :jasper-app:test --tests 'dev.jasper.app.plugins.*' --continue`
Expected: PASS: both contract runtimes (`FakeContractTest`, `AppContractTest`) pass the two cases above and every other
case unchanged (each adds tabs at the end of the strip, as the end of its one active group was); `verifySdkArchitecture`
and doclint pass over the renamed constant.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-sdk/src jasper-sdk-testkit/src jasper-app/src plugins/editor/src
git commit -F - <<'EOF'
SDK: TabPlacement.NEW_TAB, and tabs of panes in the fakes

ACTIVE_GROUP becomes NEW_TAB. The testkit's fake and the app's contract
fixture hold a window's tabs, each holding panes: whatever opens
without a pane opens a new tab, and a split a new pane right after the
focused one. windowTabs shows the strip, and the contract pins the
placements, Terminals.openTab, Terminals.split and a moved tab on both
runtimes.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: Move Pane to New Tab

Spec 9 and 10: Move Pane to New Tab moves the focused pane into a new tab right after its own, which shows, and is
disabled for a tab's only pane, a pinned item and one being closed (Rulings 9). Its default is ⌥⌘T / Ctrl+Alt+Shift+T,
which the collision check and `KeyBindingsTest` now pin to it. The Tab menu lists it after Split Right and Split Down and a terminal's
context menu after them too (Rulings 10; Task 5 folds the two splits into the Split submenu); the palette lists it as a
built-in action.

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/MovePaneToNewTabTest.java` (and its ledger row)
- Replace: `jasper-app/src/test/java/dev/jasper/app/plugins/TabShortcutCollisionTest.java` (plan 8a's)
- Modify: `commands/ActionId.java` (after `MAXIMIZE_PANE` `:26`); `workspace/WorkspaceActions.java` (`invoke`, `update`,
  after 8a `:62`, `:125`); `workspace/WindowContent.java` (after `mayMoveToMainWindow`, after 8a `:1294`);
  `workspace/ParentTabsView.java` (before `reorder` `:155`); `workspace/WindowChrome.java` (`GROUP_ITEMS` and
  `contextMenu`, after 8a `:457-458`, `:595-596`); `shortcuthelp/ShortcutCatalog.java` (`category`, after 8a `:86-87`)
- Test: `config/KeymapDefaultsTest.java`, `config/KeyBindingsTest.java`, `shortcuthelp/ShortcutHelpTest.java`,
  `workspace/TabMenuTest.java`, `workspace/TerminalContextMenuTest.java` (plan 8a's assertions)

**Interfaces:**
- Consumes: plan 8a's `ParentTabsView.openParentTab(Tab, ParentTabId, int)`, `holding(Tab)`, `WorkspaceView.remove(Tab)`,
  `tabCount()`, `WindowContent.selectedTab()`, `windowLayout()`, `parentTabOf(Tab)`.
- Produces:
  - `ActionId.MOVE_PANE_TO_NEW_TAB` (`move_pane_to_new_tab`, "Move Pane to New Tab", `alt+cmd+t` /
    `ctrl+alt+shift+t`), in the Panes category;
  - `WindowContent.mayMoveToNewTab(Tab)` → `boolean`, `WindowContent.moveToNewTab(Tab)`;
  - `ParentTabsView.moveToNewTab(Tab)`: the item's pane into a new tab right after its own, shown.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/MovePaneToNewTabTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.TabId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tabs and panes spec 9: Move Pane to New Tab gives the focused pane a tab of its own, right after its tab. */
class MovePaneToNewTabTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A connected window under the standard chrome with two tabs, the first split in two with its right pane focused. */
    private WindowContent window() throws Exception {
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            owner[0] = content(idleLauncher(pending), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            owner[0].connectContributions(model);
            owner[0].invoke(ActionId.NEW_TAB);
            owner[0].invoke(ActionId.SELECT_TAB_1);
            owner[0].split(Side.RIGHT);
        });
        while (!pending.isEmpty()) pending.remove().run();
        return owner[0];
    }

    /** Each tab's items, in strip order. */
    private static List<List<TabId>> tabs(WindowContent window) {
        return window.windowLayout().parentTabs().stream().map(ParentTab::layout).map(layout -> layout.tabs()).toList();
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        var texts = new ArrayList<String>();
        for (var child : menu.getComponents()) if (child instanceof JMenuItem item) {
            if (item.getText().equals(text)) return item;
            texts.add(item.getText());
        }
        throw new AssertionError("no " + text + " in " + texts);
    }

    @Test void theFocusedPaneMovesIntoANewTabRightAfterItsOwnWhichShows() throws Exception {
        var window = window();
        edt(() -> {
            Tab left = window.tabs().get(0), right = window.tabs().get(1), other = window.tabs().get(2);
            assertThat(window.selectedTab()).isSameAs(right);
            var component = right.component();
            window.updateActions();
            assertThat(window.action(ActionId.MOVE_PANE_TO_NEW_TAB).isEnabled()).isTrue();
            window.invoke(ActionId.MOVE_PANE_TO_NEW_TAB);
            assertThat(tabs(window)).as("a tab of its own, right after its old one")
                .containsExactly(List.of(left.id()), List.of(right.id()), List.of(other.id()));
            assertThat(window.selectedTab()).as("the new tab shows").isSameAs(right);
            assertThat(window.activeParentTab()).isEqualTo(window.windowLayout().parentTabs().get(1).id());
            assertThat(right.closed()).as("nothing closes").isFalse();
            assertThat(right.component()).isSameAs(component);
            assertThat(right.window()).as("nothing leaves the window").isEqualTo(window.id());
            window.updateActions();
            assertThat(window.action(ActionId.MOVE_PANE_TO_NEW_TAB).isEnabled()).as("a tab's only pane stays").isFalse();
            window.invoke(ActionId.MOVE_PANE_TO_NEW_TAB);
            assertThat(window.parentTabCount()).isEqualTo(3);
        });
    }

    /** Review Focus 1: a maximized pane moves out, and its old tab shows the rest, no longer maximized. */
    @Test void aMaximizedPaneMovesOutAndItsOldTabShowsTheRest() throws Exception {
        var window = window();
        edt(() -> {
            Tab left = window.tabs().get(0), right = window.tabs().get(1);
            window.invoke(ActionId.MAXIMIZE_PANE);
            assertThat(window.workspace().model().maximized()).isPresent();
            window.invoke(ActionId.MOVE_PANE_TO_NEW_TAB);
            var old = window.windowLayout().parentTabs().getFirst().layout();
            assertThat(old.tabs()).containsExactly(left.id());
            assertThat(old.maximized()).as("its old tab shows the rest").isEmpty();
            assertThat(window.windowLayout().parentTabs().get(1).layout().tabs()).containsExactly(right.id());
            assertThat(window.selectedTab()).isSameAs(right);
        });
    }

    /** Rulings 9: a pinned item, as under a modal prompt, stays where it is. */
    @Test void aPinnedItemStays() throws Exception {
        var window = window();
        edt(() -> {
            var held = window.selectedTab().pin();
            window.updateActions();
            assertThat(window.action(ActionId.MOVE_PANE_TO_NEW_TAB).isEnabled()).isFalse();
            window.invoke(ActionId.MOVE_PANE_TO_NEW_TAB);
            assertThat(window.parentTabCount()).isEqualTo(2);
            held.close();
            window.updateActions();
            assertThat(window.action(ActionId.MOVE_PANE_TO_NEW_TAB).isEnabled()).isTrue();
        });
    }

    /** A plugin's item moves the same way, and stays in its window. */
    @Test void aPluginsItemMovesToANewTabToo() throws Exception {
        var window = window();
        edt(() -> {
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_DOWN);
            assertThat(window.parentTabOf(view)).isEqualTo(window.parentTabOf(window.tabs().getFirst()));
            window.moveToNewTab(view);
            assertThat(window.parentTabCount()).isEqualTo(3);
            assertThat(window.windowLayout().parentTabs().get(1).layout().tabs()).containsExactly(view.id());
            assertThat(window.selectedTab()).isSameAs(view);
            assertThat(view.closed()).isFalse();
        });
    }

    /** Rulings 8 and 10: a terminal's context menu, the palette and the default keys offer it. */
    @Test void theContextMenuThePaletteAndItsKeysOfferIt() throws Exception {
        var window = window();
        edt(() -> {
            assertThat(window.commands().find("move_pane_to_new_tab")).as("in the palette").isPresent();
            assertThat(item(window.chrome().contextMenu(), "Move Pane to New Tab").isEnabled()).isTrue();
            var stroke = window.bindings().strokeFor(ActionId.MOVE_PANE_TO_NEW_TAB).orElseThrow();
            assertThat(window.dispatchShortcut(stroke, window.currentPane())).isTrue();
            assertThat(window.parentTabCount()).as("its keys run it").isEqualTo(3);
        });
    }
}
```

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/plugins/TabShortcutCollisionTest.java` with:

```java
package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tabs and panes spec 10 and 16, the collision check on each platform: no bundled plugin's default shortcut loses to a
 * tab or pane action's, and Move Pane to New Tab holds its keys.
 */
class TabShortcutCollisionTest {
    /** The actions of spec 10's table that Jasper defines itself. */
    private static final EnumSet<ActionId> TABS_AND_PANES = EnumSet.of(ActionId.NEW_TAB, ActionId.SPLIT_RIGHT,
        ActionId.SPLIT_DOWN, ActionId.CLOSE_PANE, ActionId.CLOSE_TAB, ActionId.CLOSE_WINDOW, ActionId.NEXT_TAB,
        ActionId.PREVIOUS_TAB, ActionId.SELECT_TAB_1, ActionId.SELECT_TAB_2, ActionId.SELECT_TAB_3, ActionId.SELECT_TAB_4,
        ActionId.SELECT_TAB_5, ActionId.SELECT_TAB_6, ActionId.SELECT_TAB_7, ActionId.SELECT_TAB_8, ActionId.SELECT_TAB_9,
        ActionId.FOCUS_PANE_LEFT, ActionId.FOCUS_PANE_RIGHT, ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN,
        ActionId.MAXIMIZE_PANE, ActionId.MOVE_PANE_TO_NEW_TAB, ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TO_MAIN_WINDOW);

    @TempDir Path root;
    private PluginRuntime runtime;
    private Contributions contributions;

    @AfterEach void stop() throws Exception {
        if (runtime != null) {
            var stopped = onEdtValue(runtime::stop);
            CompletableFuture.allOf(stopped.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        }
    }

    /** Starts the bundled plugins, as the build stages them, and gives their actions as the keymap resolves them. */
    private List<KeyBindings.Extension> started() throws Exception {
        UnboundVault.settings(root.resolve("plugins")); // the runtime starts the staged Vault, whose keychain stays unused
        onEdt(() -> {
            contributions = new Contributions();
            var buddy = new BuddyTestSupport();
            runtime = new PluginRuntime(new PluginRuntime.Options(Path.of(System.getProperty("jasper.stagedPlugins")),
                root.resolve("plugins"), null, false, root.resolve("plugins.toml"), root.resolve("plugins.lock")),
                new ActivityNotifier(buddy.companion(), () -> { }), PluginRuntime.SettingsReports.IGNORED, contributions,
                AppContractTest.headlessWindows(), new TerminalRegistry());
            runtime.start(Map.of(), true);
        });
        return onEdtValue(() -> contributions.actions().stream().map(ActionEntry::extension).toList());
    }

    @Test void theTabAndPaneDefaultsTakeNoBundledPluginsDefaultOnEitherPlatform() throws Exception {
        var extensions = started();
        assertThat(extensions).as("the bundled plugins' actions").isNotEmpty();
        onEdt(() -> {
            for (boolean mac : new boolean[]{true, false}) {
                String platform = mac ? "macOS" : "Linux and Windows";
                var resolved = KeyBindings.defaults(mac).withExtensions(extensions);
                for (ActionId action : TABS_AND_PANES)
                    assertThat(resolved.problems()).as("%s on %s", action.id(), platform)
                        .noneMatch(problem -> problem.kind() == KeyBindings.Problem.Kind.DEFAULT_DROPPED
                            && problem.message().contains("'" + action.id() + "'"));
                String other = mac ? "ctrl+cmd+t" : "ctrl+alt+shift+y";
                assertThat(KeyBindings.withOverrides(mac, Map.of(ActionId.MOVE_PANE_TO_NEW_TAB.id(), other)).strokeFor(ActionId.MOVE_PANE_TO_NEW_TAB))
                    .as("Move Pane to New Tab can be bound on %s", platform).isEqualTo(KeyBindings.parse(other, mac));
                var movePane = KeyBindings.parse(mac ? "alt+cmd+t" : "ctrl+alt+shift+t", mac).orElseThrow();
                assertThat(resolved.bindings().idsFor(movePane)).as("Move Pane to New Tab's keys on %s", platform)
                    .containsExactly(ActionId.MOVE_PANE_TO_NEW_TAB.id());
            }
        });
    }
}
```

Then run the edit script for the keymap, help and menu tests, and add the new test's ledger row:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8b")
from edits import edit
import ledger
T = "jasper-app/src/test/java/dev/jasper/app/"
W = T + "workspace/"
edit(T + "config/KeymapDefaultsTest.java", [(
    """        new Row("maximize_pane", KeyContext.GLOBAL, "cmd+shift+enter", "ctrl+alt+shift+enter"), // 35, parent tabs spec 8
""", """        new Row("maximize_pane", KeyContext.GLOBAL, "cmd+shift+enter", "ctrl+alt+shift+enter"), // 35, parent tabs spec 8
        new Row("move_pane_to_new_tab", KeyContext.GLOBAL, "alt+cmd+t", "ctrl+alt+shift+t"),  // tabs and panes spec 9 and 10
""", 1)])
edit(T + "config/KeyBindingsTest.java", [
    ("            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MAXIMIZE_PANE,\n",
     "            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MAXIMIZE_PANE, ActionId.MOVE_PANE_TO_NEW_TAB,\n", 1),
    ("    /** Tabs and panes spec 10: each platform's tab and pane keys, and the keys plan 8b's Move Pane to New Tab takes. */\n",
     "    /** Tabs and panes spec 10: each platform's tab and pane keys, Move Pane to New Tab's included. */\n", 1),
    ('            assertThat(bindings.actionFor(movePane)).as("free for plan 8b\'s Move Pane to New Tab").isEmpty();\n',
     '            assertThat(bindings.actionFor(movePane)).as("Move Pane to New Tab").contains(ActionId.MOVE_PANE_TO_NEW_TAB);\n', 1)])
edit(T + "shortcuthelp/ShortcutHelpTest.java", [(
    """        for (String id : List.of("close_pane", "maximize_pane", "focus_pane_left"))
""", """        for (String id : List.of("close_pane", "maximize_pane", "focus_pane_left", "move_pane_to_new_tab"))
""", 1)])
edit(W + "TabMenuTest.java", [(
    """            assertThat(items(window, "Tab")).containsExactly("Split Right", "Split Down", "Focus Pane Left", "Focus Pane Right",
""", """            assertThat(items(window, "Tab")).containsExactly("Split Right", "Split Down", "Move Pane to New Tab", "Focus Pane Left",
                "Focus Pane Right",
""", 1)])
edit(W + "TerminalContextMenuTest.java", [(
    """            assertThat(labels(menu)).containsExactly("Copy", "Paste", "Find", "Clear Terminal", "Split Right", "Split Down",
                "Maximize Pane", "Close Pane", "---", "Move Tab to New Window");
""", """            assertThat(labels(menu)).containsExactly("Copy", "Paste", "Find", "Clear Terminal", "Split Right", "Split Down",
                "Move Pane to New Tab", "Maximize Pane", "Close Pane", "---", "Move Tab to New Window");
""", 1)])
ledger.add(W + "MovePaneToNewTabTest.java", "app.workspace", "MovePaneToNewTabTest", W + "MovePaneToNewTabTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 8b Task 2; Move Pane to New Tab")
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL: `ActionId.MOVE_PANE_TO_NEW_TAB`, `WindowContent.moveToNewTab` and `mayMoveToNewTab` do not exist.

- [ ] **Step 3: Implement**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8b")
from edits import edit
A = "jasper-app/src/main/java/dev/jasper/app/"
W = A + "workspace/"
edit(A + "commands/ActionId.java", [(
    """    MAXIMIZE_PANE("maximize_pane", "Maximize Pane", "cmd+shift+enter", "ctrl+alt+shift+enter"),
""", """    MAXIMIZE_PANE("maximize_pane", "Maximize Pane", "cmd+shift+enter", "ctrl+alt+shift+enter"),
    MOVE_PANE_TO_NEW_TAB("move_pane_to_new_tab", "Move Pane to New Tab", "alt+cmd+t", "ctrl+alt+shift+t"),
""", 1)])
edit(W + "WorkspaceActions.java", [
    ("            case MOVE_TO_MAIN_WINDOW -> owner.moveToMainWindow();\n", """            case MOVE_TO_MAIN_WINDOW -> owner.moveToMainWindow();
            case MOVE_PANE_TO_NEW_TAB -> owner.moveToNewTab(owner.selectedTab());
""", 1),
    ("                    case MOVE_TO_MAIN_WINDOW -> owner.mayMoveToMainWindow();\n", """                    case MOVE_TO_MAIN_WINDOW -> owner.mayMoveToMainWindow();
                    // A tab's only pane, a pinned item and one being closed stay (workspace plan 8b Rulings 9).
                    case MOVE_PANE_TO_NEW_TAB -> owner.mayMoveToNewTab(owner.selectedTab());
""", 1)])
edit(W + "WindowContent.java", [(
    "    boolean mayMoveToMainWindow() { return !closed && manager != null && !isMain() && movable(tabs()); }\n",
    """    boolean mayMoveToMainWindow() { return !closed && manager != null && !isMain() && movable(tabs()); }

    /**
     * Whether Move Pane to New Tab may take {@code tab} (tabs and panes spec 9; workspace plan 8b Rulings 9): an item of
     * this window whose tab holds another pane, neither pinned nor being closed.
     *
     * @param tab an item, or null
     * @return whether it would move
     */
    boolean mayMoveToNewTab(Tab tab) {
        if (closed || tab == null || !registry.movable(tab)) return false;
        return parentTabs.holding(tab).map(view -> view.tabCount() > 1).orElse(false);
    }

    /**
     * Move Pane to New Tab (tabs and panes spec 9): {@code tab}'s pane leaves its tab for a new tab right after it, which
     * shows; its other panes stay, nothing closes and nothing leaves the window. Nothing when {@link #mayMoveToNewTab}
     * says no.
     *
     * @param tab an item of this window
     */
    void moveToNewTab(Tab tab) { if (mayMoveToNewTab(tab)) parentTabs.moveToNewTab(tab); }
""", 1)])
edit(W + "ParentTabsView.java", [(
    """    /**
     * Moves the parent tab {@code id} to {@code index} among the others in the bar, where a drag along the bar dropped
""", """    /**
     * Move Pane to New Tab (tabs and panes spec 9): {@code tab} leaves its tab, whose other panes stay, for a new tab right
     * after it in the strip, which shows. Its component moves between the two workspaces: nothing closes, nothing leaves
     * the window, and the registry hears no move. Nothing for a tab's only pane or an item the window does not hold.
     */
    void moveToNewTab(Tab tab) {
        WorkspaceView from = holding(tab).orElse(null);
        if (closed || from == null || from.tabCount() < 2) return;
        int at = model.indexOf(from.id()) + 1;
        from.remove(tab);
        openParentTab(tab, ParentTabId.random(), at);
    }

    /**
     * Moves the parent tab {@code id} to {@code index} among the others in the bar, where a drag along the bar dropped
""", 1)])
edit(W + "WindowChrome.java", [
    ("""    private static final ActionId[] GROUP_ITEMS = {ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.FOCUS_PANE_LEFT,
        ActionId.FOCUS_PANE_RIGHT, ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN, ActionId.PREVIOUS_PROMPT, ActionId.NEXT_PROMPT};
""", """    private static final ActionId[] GROUP_ITEMS = {ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MOVE_PANE_TO_NEW_TAB,
        ActionId.FOCUS_PANE_LEFT, ActionId.FOCUS_PANE_RIGHT, ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN,
        ActionId.PREVIOUS_PROMPT, ActionId.NEXT_PROMPT};
""", 1),
    ("""        for (ActionId id : new ActionId[]{ActionId.COPY, ActionId.PASTE, ActionId.FIND, ActionId.CLEAR_TERMINAL,
            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MAXIMIZE_PANE, ActionId.CLOSE_PANE}) menu.add(owner.action(id));
""", """        for (ActionId id : new ActionId[]{ActionId.COPY, ActionId.PASTE, ActionId.FIND, ActionId.CLEAR_TERMINAL,
            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MOVE_PANE_TO_NEW_TAB, ActionId.MAXIMIZE_PANE,
            ActionId.CLOSE_PANE}) menu.add(owner.action(id));
""", 1)])
edit(A + "shortcuthelp/ShortcutCatalog.java", [(
    "                FOCUS_PANE_UP, FOCUS_PANE_DOWN -> \"Panes\";\n",
    "                FOCUS_PANE_UP, FOCUS_PANE_DOWN, MOVE_PANE_TO_NEW_TAB -> \"Panes\";\n", 1)])
PY
```

`WorkspaceActions.invoke` reaches the new case for a plugin's item too: no content command claims the id, so the switch
runs it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.plugins.TabShortcutCollisionTest' --continue`
Expected: PASS: `KeymapDefaultsTest` takes the new row on both platforms (Cmd, and Ctrl+Shift with Alt), the collision
test finds ⌥⌘T and Ctrl+Alt+Shift+T held by Move Pane to New Tab alone and `KeyBindingsTest` finds them bound to it,
and `WindowChromeMenusTest`'s Window menu still starts with Split Right. Then `./gradlew :jasper-app:check`: PASS (the ledger test sees `MovePaneToNewTabTest`'s row).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Tabs and panes: Move Pane to New Tab

The focused pane moves into a new tab right after its own, which
shows; a tab's only pane, a pinned item and one being closed stay. It
is Option+Cmd+T, Ctrl+Alt+Shift+T elsewhere, and the Tab menu, a
terminal's context menu and the palette offer it.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: Split items in the SDK

Spec 8 and 11: a plugin registers a split item (Rulings 3); Jasper lists it after the terminal, the bundled plugins'
first (Rulings 5), and registers its Right and Down actions as the plugin's own; running one calls the opener with the
window and the placement. `Windows.splitChoices()` gives a plugin the lists (Rulings 4). The app keeps the items in
`Contributions` (Rulings 17); the menus that show them are Task 5's.

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/SplitItemSpec.java`, `SplitChoice.java`;
  `jasper-app/src/main/java/dev/jasper/app/contributions/SplitItem.java` (and its ledger row)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Windows.java` (imports, after `openTab` `:46`);
  `jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java` (before `onChanged` `:141`);
  `plugins/HostedUi.java` (a field after `:88`, two helpers before `windows()`, two members in it),
  `plugins/HostedContext.java` (after `ui.kit(kit)` `:92`), `plugins/FileEditors.java` (after `bundled(Set)` `:54`);
  `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java` (`closeAll` `:160`, a helper before `actions()`
  `:181`, two members in `windows()`), `FakePluginHost.java` (the split items `:78`, `bundledPlugins` `:1801`,
  `declareBundled`'s Javadoc)
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java`; `PluginContractTest.java` (a case);
  `jasper-app/src/test/java/dev/jasper/app/contributions/ContributionsTest.java`

**Interfaces:**
- Consumes: Task 1's `TabPlacement.SPLIT_RIGHT` and `SPLIT_DOWN`; `Contributions.addAction`, `HostedUi.tracked`,
  `ownActions`, `requireNamespace`, `containment`; `FileEditors.bundled(Set)`.
- Produces:
  - `dev.jasper.sdk.ui.SplitItemSpec(String id, String label, Optional<String> icon)`, `of(String, String)`,
    `withIcon(String)`, `String rightAction()`, `String downAction()`;
  - `dev.jasper.sdk.ui.SplitChoice(String label, String rightAction, String downAction)` and
    `SplitChoice.TERMINAL` (`Terminal`, `split_right`, `split_down`);
  - `Windows.splitItem(SplitItemSpec, BiConsumer<WindowHandle, TabPlacement>)` → `Subscription`,
    `Windows.splitChoices()` → `List<SplitChoice>` (abstract; `HostedUi` and `FakeUi` implement them);
  - `dev.jasper.app.contributions.SplitItem(String id, String label, Icon icon, String rightAction, String downAction,
    boolean bundled)`; `Contributions.addSplitItem(SplitItem)` → `Subscription` (a `MENUS` change, ids unique),
    `Contributions.splitItems()` → `List<SplitItem>` (the bundled plugins' first, each in registration order);
  - `FakePluginHost.SplitItem(String pluginId, SplitItemSpec spec)` and its `splitItems` list (package-private).

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8b")
from edits import edit
edit("jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java", [(
    "    @Test void actionSpecValidatesAndCopies() {\n", """    /** Tabs and panes spec 10 and 11 (workspace plan 8b Rulings 3): a split item's id names its two actions. */
    @Test void aSplitItemsIdNamesItsRightAndDownActions() {
        var editor = SplitItemSpec.of("dev.jasper.editor.editor", "Editor").withIcon("file");
        assertThat(editor.rightAction()).isEqualTo("dev.jasper.editor.split_right_editor");
        assertThat(editor.downAction()).isEqualTo("dev.jasper.editor.split_down_editor");
        assertThat(editor.icon()).contains("file");
        var longest = SplitItemSpec.of("dev.x." + "a".repeat(110), "Long");
        assertThat(ActionSpec.of(longest.rightAction(), "Split Right with Long").id()).as("still an action id").hasSize(128);
        for (String id : new String[]{"", "nodot", "Upper.case", "dev.x." + "a".repeat(111)})
            assertThatIllegalArgumentException().as(id).isThrownBy(() -> SplitItemSpec.of(id, "Label"));
        assertThatIllegalArgumentException().isThrownBy(() -> SplitItemSpec.of("dev.x.notes", " "));
        assertThatNullPointerException().isThrownBy(() -> new SplitItemSpec("dev.x.notes", "Notes", null));
        assertThat(SplitChoice.TERMINAL).isEqualTo(new SplitChoice("Terminal", "split_right", "split_down"));
    }

    @Test void actionSpecValidatesAndCopies() {
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/contributions/ContributionsTest.java", [(
    "    @Test void actionsAreUniqueObservableAndInvocable() {\n", """    /** Workspace plan 8b Rulings 5 and 17: split items are unique, the bundled plugins' first, and a change of the menus. */
    @Test void splitItemsAreUniqueTheBundledFirstAndChangeTheMenus() {
        model.onChanged(changes::add);
        var notes = model.addSplitItem(new SplitItem("com.example.notes.notes", "Notes", null,
            "com.example.notes.split_right_notes", "com.example.notes.split_down_notes", false));
        model.addSplitItem(new SplitItem("dev.jasper.editor.editor", "Editor", null,
            "dev.jasper.editor.split_right_editor", "dev.jasper.editor.split_down_editor", true));
        model.addSplitItem(new SplitItem("org.example.sketch.sketch", "Sketch", null,
            "org.example.sketch.split_right_sketch", "org.example.sketch.split_down_sketch", false));
        assertThatIllegalArgumentException().isThrownBy(() -> model.addSplitItem(new SplitItem("com.example.notes.notes", "Again",
            null, "com.example.notes.a", "com.example.notes.b", false)));
        assertThat(model.splitItems()).extracting(SplitItem::label).containsExactly("Editor", "Notes", "Sketch");
        assertThat(changes).containsExactly(Contributions.Kind.MENUS, Contributions.Kind.MENUS, Contributions.Kind.MENUS);
        notes.close();
        notes.close();
        assertThat(model.splitItems()).extracting(SplitItem::label).containsExactly("Editor", "Sketch");
        assertThat(changes).hasSize(4);
    }

    @Test void actionsAreUniqueObservableAndInvocable() {
""", 1)])
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java", [(
    "    @Test void openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane() {\n", """    /**
     * Tabs and panes spec 8 and 11: a split item joins every Split list after the terminal, the bundled plugins' first,
     * and Jasper registers its right and down actions, which the plugin may place and which call its opener with the
     * window and the placement.
     */
    @Test void aSplitItemJoinsTheSplitListsWithRightAndDownActionsThatCallItsOpener() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "zsh");
        UUID pane = h.addTerminalPane(tab, "zsh", Path.of("/"));
        List<String> opened = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        var notes = new AtomicReference<Subscription>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            notes.set(context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("test.alpha.notes", "Notes"),
                (shown, placement) -> opened.add(shown.id() + " " + placement)));
            context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("test.alpha.boom", "Boom"),
                (shown, placement) -> { throw new IllegalStateException("opener failure"); });
            assertThatIllegalArgumentException().as("one item per id").isThrownBy(() -> context.windows().splitItem(
                dev.jasper.sdk.ui.SplitItemSpec.of("test.alpha.notes", "Again"), (shown, placement) -> { }));
            assertThatIllegalArgumentException().as("another plugin's namespace").isThrownBy(() -> context.windows().splitItem(
                dev.jasper.sdk.ui.SplitItemSpec.of("test.beta.notes", "Foreign"), (shown, placement) -> { }));
            context.menus().standard(StandardMenu.TAB).add("test.alpha.split_right_notes");
        });
        h.declareBundled("test.bundled");
        h.start(info("test.bundled"), Set.of(), Set.of(), context -> context.windows().splitItem(
            dev.jasper.sdk.ui.SplitItemSpec.of("test.bundled.sketch", "Sketch"), (shown, placement) -> { }));
        assertThat(h.actions()).containsExactly(
            "test.alpha.split_right_notes|Split Right with Notes|true", "test.alpha.split_down_notes|Split Down with Notes|true",
            "test.alpha.split_right_boom|Split Right with Boom|true", "test.alpha.split_down_boom|Split Down with Boom|true",
            "test.bundled.split_right_sketch|Split Right with Sketch|true", "test.bundled.split_down_sketch|Split Down with Sketch|true");
        assertThat(h.menu("TAB")).as("the plugin places them as its own").containsExactly("item:test.alpha.split_right_notes");
        h.ui(() -> assertThat(alpha.get().windows().splitChoices()).as("the terminal, then the bundled plugins' items first")
            .containsExactly(dev.jasper.sdk.ui.SplitChoice.TERMINAL,
                new dev.jasper.sdk.ui.SplitChoice("Sketch", "test.bundled.split_right_sketch", "test.bundled.split_down_sketch"),
                new dev.jasper.sdk.ui.SplitChoice("Notes", "test.alpha.split_right_notes", "test.alpha.split_down_notes"),
                new dev.jasper.sdk.ui.SplitChoice("Boom", "test.alpha.split_right_boom", "test.alpha.split_down_boom")));
        h.ui(() -> {
            assertThat(h.invoke("test.alpha.split_right_notes", window, pane)).isTrue();
            assertThat(h.invoke("test.alpha.split_down_notes", window, null)).isTrue();
            assertThat(h.invoke("test.alpha.split_right_boom", window, null)).as("a throwing opener is contained").isTrue();
        });
        assertThat(opened).containsExactly(window + " SPLIT_RIGHT", window + " SPLIT_DOWN");
        h.ui(() -> notes.get().close());
        assertThat(h.actions()).as("its actions go with it").doesNotContain(
            "test.alpha.split_right_notes|Split Right with Notes|true", "test.alpha.split_down_notes|Split Down with Notes|true");
        h.ui(() -> assertThat(alpha.get().windows().splitChoices()).extracting(dev.jasper.sdk.ui.SplitChoice::label)
            .containsExactly("Terminal", "Sketch", "Boom"));
        assertThat(h.menu("TAB")).as("a placement of a closed action vanishes").isEmpty();
    }

    @Test void openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane() {
""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests 'dev.jasper.sdk.ui.UiValuesTest'`
Expected: FAIL to compile: `SplitItemSpec` and `SplitChoice` do not exist.

- [ ] **Step 3: Implement**

Create `jasper-sdk/src/main/java/dev/jasper/sdk/ui/SplitItemSpec.java`:

```java
package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A plugin's split item (tabs and panes spec 8 and 11): what a new pane can hold besides a terminal. Jasper lists it in
 * every Split list and registers two actions for it, which can be bound like any action and have no default shortcut:
 * {@link #rightAction()}, titled "Split Right with" and the label, and {@link #downAction()}, titled "Split Down with"
 * and the label. The Split lists show it as the label followed by "Right" and "Down".
 *
 * @param id namespaced id that starts with the plugin's id and a dot, its last part naming the item, for example
 *           {@code dev.example.notes.notes}; at most 116 characters, so that its actions' ids are action ids
 * @param label non-blank name the Split lists and the actions' titles show, for example {@code Notes}
 * @param icon icon for the palette, a kit name or registered plugin icon
 * @since 0.21.0
 */
public record SplitItemSpec(String id, String label, Optional<String> icon) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates the id and the label. */
    public SplitItemSpec {
        if (id == null || id.length() > 116 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced split item id: " + id);
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A split item needs a label");
        Objects.requireNonNull(icon, "icon");
    }

    /**
     * A split item with only an id and a label.
     *
     * @param id namespaced id
     * @param label non-blank label
     * @return the spec
     */
    public static SplitItemSpec of(String id, String label) {
        return new SplitItemSpec(id, label, Optional.empty());
    }

    /**
     * A copy with an icon.
     *
     * @param value the icon, or null for none
     * @return the copy
     */
    public SplitItemSpec withIcon(String value) {
        return new SplitItemSpec(id, label, Optional.ofNullable(value));
    }

    /**
     * The action that opens the item in a new pane to the right of the focused pane: the id's namespace, then
     * {@code split_right_} and its last part, as {@code dev.example.notes.split_right_notes}.
     *
     * @return the action's id
     */
    public String rightAction() {
        return action("split_right_");
    }

    /**
     * The action that opens the item in a new pane below the focused pane, as {@code dev.example.notes.split_down_notes}.
     *
     * @return the action's id
     */
    public String downAction() {
        return action("split_down_");
    }

    private String action(String prefix) {
        int dot = id.lastIndexOf('.');
        return id.substring(0, dot + 1) + prefix + id.substring(dot + 1);
    }
}
```

Create `jasper-sdk/src/main/java/dev/jasper/sdk/ui/SplitChoice.java`:

```java
package dev.jasper.sdk.ui;

import java.util.Objects;

/**
 * One choice of Jasper's Split lists (tabs and panes spec 2.10 and 8): what a new pane can hold, with the actions that
 * open it in a new pane to the right of and below the focused pane. {@link Windows#splitChoices()} lists them.
 *
 * @param label what the lists show before "Right" and "Down": {@code Terminal}, or a split item's label
 * @param rightAction the action that opens it to the right: {@code split_right} for the terminal
 * @param downAction the action that opens it below: {@code split_down} for the terminal
 * @since 0.21.0
 */
public record SplitChoice(String label, String rightAction, String downAction) {
    /** The terminal, every list's first choice: Jasper's own Split Right and Split Down. */
    public static final SplitChoice TERMINAL = new SplitChoice("Terminal", "split_right", "split_down");

    /** Requires every value. */
    public SplitChoice {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(rightAction, "rightAction");
        Objects.requireNonNull(downAction, "downAction");
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/contributions/SplitItem.java`:

```java
package dev.jasper.app.contributions;

import java.util.Objects;
import javax.swing.Icon;

/**
 * A plugin's split item (tabs and panes spec 8 and 11; workspace plan 8b Rulings 3 and 17): what a new pane can hold,
 * listed in every window's Split lists with the two contributed actions that open it to the right of and below the
 * focused pane.
 *
 * @param id the item's namespaced id
 * @param label what the Split lists show before "Right" and "Down"
 * @param icon the item's icon, or null
 * @param rightAction the contributed action that opens it in a new pane to the right
 * @param downAction the contributed action that opens it in a new pane below
 * @param bundled whether a plugin that ships with Jasper registered it: the lists show those first (Rulings 5)
 */
public record SplitItem(String id, String label, Icon icon, String rightAction, String downAction, boolean bundled) {
    /** Requires every value but the icon. */
    public SplitItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(rightAction, "rightAction");
        Objects.requireNonNull(downAction, "downAction");
    }
}
```

Then run the edit script for the SDK's `Windows`, the app and the testkit:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8b")
from edits import edit
import ledger
S = "jasper-sdk/src/main/java/dev/jasper/sdk/ui/Windows.java"
edit(S, [
    ("import dev.jasper.sdk.WindowOwner;\n", "import dev.jasper.sdk.WindowOwner;\nimport dev.jasper.sdk.terminal.WindowHandle;\n", 1),
    ("import java.util.function.Supplier;\n", "import java.util.function.BiConsumer;\nimport java.util.function.Supplier;\n", 1),
    ("    ContentTab openTab(ContentSpec spec, WindowOwner owner, UiView content, TabPlacement placement);\n",
     """    ContentTab openTab(ContentSpec spec, WindowOwner owner, UiView content, TabPlacement placement);

    /**
     * Registers a split item (tabs and panes spec 8 and 11). Jasper lists it in every Split list (the Tab menu's and the
     * right-click menus' Split submenus) after the terminal, the bundled plugins' items first and each in the order they
     * registered, and registers its {@link SplitItemSpec#rightAction()} and {@link SplitItemSpec#downAction()}, which can
     * be bound under {@code [keybindings]} and placed in the plugin's menus and toolbar as its own actions. Running one
     * calls {@code opener} on the UI thread with the window it ran in and {@link TabPlacement#SPLIT_RIGHT} or
     * {@link TabPlacement#SPLIT_DOWN}; the plugin opens its view there, usually with
     * {@link #openTab(ContentSpec, WindowOwner, UiView, TabPlacement)}. A failing opener is contained.
     *
     * @param spec the item
     * @param opener opens the item's view in the window, at the placement
     * @return the registration; closing it takes the item out of the lists and removes its two actions
     * @throws IllegalArgumentException when the id does not start with the plugin's id and a dot, an item with the id is
     *                                  registered, or one of its actions' ids is in use
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     * @since 0.21.0
     */
    Subscription splitItem(SplitItemSpec spec, BiConsumer<WindowHandle, TabPlacement> opener);

    /**
     * What every Split list offers, in its order (tabs and panes spec 2.10 and 8): {@link SplitChoice#TERMINAL}, then each
     * registered split item, the bundled plugins' first and each in the order they registered. A plugin that builds its
     * own menu, as the Editor builds its right-click menu, lists these.
     *
     * @return the choices
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     * @since 0.21.0
     */
    List<SplitChoice> splitChoices();
""", 1)])
A = "jasper-app/src/main/java/dev/jasper/app/"
edit(A + "contributions/Contributions.java", [(
    "    public Subscription onChanged(Consumer<Kind> listener) {\n", """    private final List<SplitItem> splitItems = new ArrayList<>();

    /**
     * Lists a split item in every window's Split lists (tabs and panes spec 8; workspace plan 8b Rulings 17); its
     * contributor registers its two actions with {@link #addAction}. Ids are unique.
     *
     * @param item the item
     * @return removes it from the lists
     * @throws IllegalArgumentException when an item with its id is listed
     */
    public Subscription addSplitItem(SplitItem item) {
        requireEdt();
        Objects.requireNonNull(item, "item");
        for (SplitItem existing : splitItems)
            if (existing.id().equals(item.id())) throw new IllegalArgumentException("Split item already registered: " + item.id());
        splitItems.add(item);
        changed(Kind.MENUS);
        return new Subscription(() -> { if (splitItems.remove(item)) changed(Kind.MENUS); });
    }

    /**
     * The split items in the Split lists' order (workspace plan 8b Rulings 5): the bundled plugins' first, each in the
     * order it was listed.
     *
     * @return the items
     */
    public List<SplitItem> splitItems() {
        return splitItems.stream().sorted(Comparator.comparing((SplitItem item) -> !item.bundled())).toList();
    }

    public Subscription onChanged(Consumer<Kind> listener) {
""", 1)])
ledger.add(A + "contributions/SplitItem.java", "app.contributions", "SplitItem",
    "jasper-app/src/test/java/dev/jasper/app/contributions/ContributionsTest.java",
    "New in workspace-tabs plan 8b Task 3; a plugin's split item in the Split lists, its icon a nonvisual value")
P = A + "plugins/"
edit(P + "FileEditors.java", [(
    "    void bundled(Set<String> pluginIds) { bundled = Set.copyOf(pluginIds); }\n",
    """    void bundled(Set<String> pluginIds) { bundled = Set.copyOf(pluginIds); }

    /** Whether {@code pluginId} ships with Jasper; its split items come first in the Split lists (workspace plan 8b Rulings 5). */
    boolean bundled(String pluginId) { return bundled.contains(pluginId); }
""", 1)])
edit(P + "HostedContext.java", [("        ui.kit(kit);\n", "        ui.kit(kit);\n        ui.bundled = () -> host.fileEditors.bundled(id);\n", 1)])
edit(P + "HostedUi.java", [
    ("    private final List<Runnable> closers = new ArrayList<>();\n", """    private final List<Runnable> closers = new ArrayList<>();
    /** Whether this plugin ships with Jasper, which puts its split items first (workspace plan 8b Rulings 5); its context sets it. */
    BooleanSupplier bundled = () -> false;
""", 1),
    ("    Windows windows() {\n", """    /**
     * One of a split item's two actions (workspace plan 8b Rulings 3): no default, the palette's words for it, and a
     * contained call of the opener with the window it ran in and {@code placement}.
     */
    private ActionEntry splitAction(String id, String title, Icon icon, String side, String label,
                                    java.util.function.BiConsumer<WindowHandle, dev.jasper.sdk.ui.TabPlacement> opener,
                                    dev.jasper.sdk.ui.TabPlacement placement) {
        var keywords = new ArrayList<>(List.of("split", side, "new", "pane"));
        for (String word : label.toLowerCase(java.util.Locale.ROOT).split(" ")) if (!word.isBlank()) keywords.add(word.strip());
        return model.addAction(id, title, icon, keywords, Optional.empty(), invocation -> containment.run(pluginId, "split item " + id,
            () -> opener.accept(terminals.windowHandle(invocation.windowId()), placement)));
    }

    /** The Split lists' choices (tabs and panes spec 2.10; workspace plan 8b Rulings 4 and 5): the terminal, then every split item. */
    private List<dev.jasper.sdk.ui.SplitChoice> splitChoices() {
        var choices = new ArrayList<dev.jasper.sdk.ui.SplitChoice>();
        choices.add(dev.jasper.sdk.ui.SplitChoice.TERMINAL);
        for (var item : model.splitItems()) choices.add(new dev.jasper.sdk.ui.SplitChoice(item.label(), item.rightAction(), item.downAction()));
        return List.copyOf(choices);
    }

    Windows windows() {
""", 1),
    ("            @Override public Subscription onTabsClosing(", """            @Override public Subscription splitItem(dev.jasper.sdk.ui.SplitItemSpec spec,
                    java.util.function.BiConsumer<WindowHandle, dev.jasper.sdk.ui.TabPlacement> opener) {
                guard("splitItem");
                java.util.Objects.requireNonNull(spec, "spec");
                java.util.Objects.requireNonNull(opener, "opener");
                requireNamespace(spec.id(), "A split item");
                Icon icon = spec.icon().map(HostedUi.this::icon).orElse(null);
                if (model.splitItems().stream().anyMatch(item -> item.id().equals(spec.id())))
                    throw new IllegalArgumentException("Split item already registered: " + spec.id());
                ActionEntry right = splitAction(spec.rightAction(), "Split Right with " + spec.label(), icon, "right", spec.label(),
                    opener, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
                ActionEntry down;
                try {
                    down = splitAction(spec.downAction(), "Split Down with " + spec.label(), icon, "down", spec.label(),
                        opener, dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN);
                } catch (RuntimeException failure) { right.close(); throw failure; }
                var listed = model.addSplitItem(new dev.jasper.app.contributions.SplitItem(spec.id(), spec.label(), icon,
                    spec.rightAction(), spec.downAction(), bundled.getAsBoolean()));
                ownActions.add(spec.rightAction());
                ownActions.add(spec.downAction());
                return tracked(() -> {
                    ownActions.remove(spec.rightAction());
                    ownActions.remove(spec.downAction());
                    listed.close();
                    right.close();
                    down.close();
                });
            }
            @Override public List<dev.jasper.sdk.ui.SplitChoice> splitChoices() {
                guard("splitChoices");
                return HostedUi.this.splitChoices();
            }
            @Override public Subscription onTabsClosing(""", 1)])
K = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/"
edit(K + "FakePluginHost.java", [
    ("    final Map<String, Declared> subcommands = new LinkedHashMap<>();\n", """    final Map<String, Declared> subcommands = new LinkedHashMap<>();
    /** A plugin's split item, as {@code Windows.splitItem} registered it. */
    record SplitItem(String pluginId, dev.jasper.sdk.ui.SplitItemSpec spec) { }
    /** Every plugin's split items, in registration order. */
    final List<SplitItem> splitItems = new CopyOnWriteArrayList<>();
""", 1),
    ("    private final Set<String> bundledPlugins = ConcurrentHashMap.newKeySet();\n",
     "    final Set<String> bundledPlugins = ConcurrentHashMap.newKeySet();\n", 1),
    ("""     * Marks a plugin as one that ships with Jasper, before it starts: its {@code Platform.editor} registration replaces
     * a non-bundled plugin's, as the application's bundled plugins' do.
""", """     * Marks a plugin as one that ships with Jasper, before it starts: its {@code Platform.editor} registration replaces
     * a non-bundled plugin's, and its split items come first in the Split lists, as the application's bundled plugins' do.
""", 1)])
edit(K + "FakeUi.java", [
    ("        actions.clear(); toolbar.clear(); sections.clear(); topLevelIds.clear(); status.clear();\n",
     """        host.splitItems.removeIf(item -> item.pluginId().equals(pluginId()));
        actions.clear(); toolbar.clear(); sections.clear(); topLevelIds.clear(); status.clear();
""", 1),
    ("    Actions actions() {\n", """    /** One of a split item's two actions, as the application registers it: no default, the words the palette finds it by. */
    private Action splitAction(dev.jasper.sdk.ui.SplitItemSpec spec, String id, String side,
                               java.util.function.BiConsumer<WindowHandle, dev.jasper.sdk.ui.TabPlacement> opener,
                               dev.jasper.sdk.ui.TabPlacement placement) {
        var keywords = new ArrayList<>(List.of("split", side.toLowerCase(java.util.Locale.ROOT), "new", "pane"));
        for (String word : spec.label().toLowerCase(java.util.Locale.ROOT).split(" ")) if (!word.isBlank()) keywords.add(word.strip());
        var action = new Action(new ActionSpec(id, "Split " + side + " with " + spec.label(), spec.icon(), keywords, Optional.empty()),
            invoked -> opener.accept(invoked.window(), placement));
        actions.put(id, action);
        return action;
    }

    Actions actions() {
""", 1),
    ("            @Override public Subscription onTabsClosing(", """            @Override public Subscription splitItem(dev.jasper.sdk.ui.SplitItemSpec spec,
                    java.util.function.BiConsumer<WindowHandle, dev.jasper.sdk.ui.TabPlacement> opener) {
                context.requireOpen();
                java.util.Objects.requireNonNull(spec, "spec");
                java.util.Objects.requireNonNull(opener, "opener");
                requireNamespace(spec.id(), "A split item");
                spec.icon().ifPresent(context.kit()::validateIcon);
                if (host.splitItems.stream().anyMatch(item -> item.spec().id().equals(spec.id())))
                    throw new IllegalArgumentException("Split item already registered: " + spec.id());
                for (String id : List.of(spec.rightAction(), spec.downAction()))
                    if (actions.containsKey(id)) throw new IllegalArgumentException("Action already registered: " + id);
                var right = splitAction(spec, spec.rightAction(), "Right", opener, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
                var down = splitAction(spec, spec.downAction(), "Down", opener, dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN);
                var listed = new FakePluginHost.SplitItem(pluginId(), spec);
                host.splitItems.add(listed);
                return () -> {
                    actions.remove(spec.rightAction(), right);
                    actions.remove(spec.downAction(), down);
                    host.splitItems.remove(listed);
                };
            }
            @Override public List<dev.jasper.sdk.ui.SplitChoice> splitChoices() {
                context.requireOpen();
                var choices = new ArrayList<dev.jasper.sdk.ui.SplitChoice>(List.of(dev.jasper.sdk.ui.SplitChoice.TERMINAL));
                host.splitItems.stream()
                    .sorted(java.util.Comparator.comparing((FakePluginHost.SplitItem item) -> !host.bundledPlugins.contains(item.pluginId())))
                    .forEach(item -> choices.add(new dev.jasper.sdk.ui.SplitChoice(item.spec().label(), item.spec().rightAction(),
                        item.spec().downAction())));
                return List.copyOf(choices);
            }
            @Override public Subscription onTabsClosing(""", 1)])
PY
```

`SplitItemSpec`'s pattern is `ActionSpec`'s; its Java text holds one backslash pair, written as the file shows it. The
`Comparator` import `Contributions` needs is already there (`Contributions.java:7`).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.contributions.*' --continue`
Expected: PASS: the contract case passes on both runtimes, `verifySdkArchitecture` passes (the new types name no Swing
or AWT type, and `dev.jasper.sdk.ui` keeps its one edge to `dev.jasper.sdk.terminal`) and so do its signature fixtures,
and doclint passes over the new Javadoc. Then `./gradlew :jasper-app:check`: PASS (the ledger test sees `SplitItem`'s
row).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-sdk/src jasper-sdk-testkit/src jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
SDK: split items

A plugin registers a split item with Windows.splitItem. Jasper lists
it in every Split list after the terminal, the bundled plugins' items
first, and registers its Split Right with and Split Down with actions,
which can be bound and call the plugin's opener with the window and
the placement. Windows.splitChoices gives a plugin the lists.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: A content tab's pane commands

Spec 8 and 9: a plugin's item splits beside its own pane with any Split choice (Rulings 4) and moves its pane to a new
tab (Rulings 11), which is how the Editor's right-click menu offers both (Task 6). The terminal choice and Move Pane to
New Tab reach the item's window through the tab registry and three new `WorkspaceHost` functions (Rulings 18); a
plugin's choice runs its action with the item selected. The terminal choice needs `terminal.open`, as `Terminals.split`
does (Rulings 19). The contract pins all three, and the refusal, on both runtimes.

**Files:**
- Replace: `jasper-app/src/main/java/dev/jasper/app/contributions/WorkspaceHost.java` (plan 8a's)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentTab.java` (after `moveToMainWindow` `:38`),
  `ui/Windows.java` (Task 3's `splitChoices` Javadoc); `jasper-app/src/main/java/dev/jasper/app/contributions/TabRegistry.java`
  (after `moveToMainWindow`, after 8a `:385`); `plugins/HostedUi.java` (`OwnedContent`, after 8a `:545`; after Task 3's
  `bundled`), `plugins/HostedContext.java` (after Task 3's `ui.bundled` line);
  `workspace/WindowContent.java` (`workspaceHost`, after 8a `:1153-1156`);
  `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeContent.java` (before `moveTo` `:55`),
  `FakeWorkspace.java` (Task 1's, before `close(Pane)`), `FakeTerminals.java` (`openedInfo` `:246`)
- Test: `PluginContractTest.java` (two cases); `jasper-app/src/test/java/dev/jasper/app/plugins/TerminalFixture.java`,
  `AppContractTest.java` (its workspace host); `jasper-app/src/test/java/dev/jasper/app/contributions/TabRegistryTest.java`;
  `jasper-app/src/test/java/dev/jasper/app/workspace/MovePaneToNewTabTest.java` (Task 2's)

**Interfaces:**
- Consumes: Task 2's `WindowContent.mayMoveToNewTab(Tab)`, `moveToNewTab(Tab)`; Task 3's `SplitChoice`,
  `Windows.splitChoices()`, `HostedUi.splitChoices()`, `Contributions.action(String)`; `WindowContent.split(Side)`,
  `selectTab(Tab)`; `TabRegistry.movable(Tab)`, `select(Tab)`; Task 3's `HostedUi.bundled` and `HostedContext`'s
  `ui.bundled` line; `CapabilityGate.require`, `audit` (`HostedContext.gate`); `FakeTerminals.require(String)`.
- Produces:
  - `ContentTab.moveToNewTab()`, `boolean mayMoveToNewTab()`, `split(SplitChoice choice, Direction direction)` (abstract,
    `@since 0.21.0`; `split` refuses a choice not in `splitChoices()` with `IllegalArgumentException`, and
    `SplitChoice.TERMINAL` without `terminal.open` with `MissingCapabilityException`);
  - `HostedUi.terminalOpen` (package-private `Consumer<String>`, which `HostedContext` sets: requires `terminal.open`,
    then audits);
  - `WorkspaceHost(UUID, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select, Runnable toFront,
    Function<TabId, Optional<GroupId>> groupOf, Beside attachBeside, BiConsumer<Tab, Side> splitTerminal,
    Predicate<Tab> mayMoveToNewTab, Consumer<Tab> moveToNewTab)`, its seven-, six- and five-argument forms opening no
    terminal beside a tab and moving no pane;
  - `TabRegistry.splitTerminal(Tab, Side)`, `boolean mayMoveToNewTab(Tab)`, `moveToNewTab(Tab)`;
  - `FakeWorkspace.moveToNewTab(Tab)`, `FakeTerminals.openedInfo(OpenRequest.Local)` (package-private);
    `TerminalFixture.splitBeside(UUID)`, `mayMoveToNewTab(UUID)`, `moveToNewTab(UUID)`.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8b")
from edits import edit
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java", [(
    "    @Test void openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane() {\n", """    /**
     * Tabs and panes spec 8 and 9: a content tab splits beside its own pane with any Split choice, the terminal's
     * included, whichever pane was focused, and Move Pane to New Tab gives its pane a tab of its own right after its tab;
     * its tab's only pane and a pinned one stay.
     */
    @Test void aContentTabSplitsBesideItsOwnPaneAndMovesItToANewTab() {
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("test.alpha.sketch", "Sketch"), (window, placement) ->
                context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("sketch", "Sketch"), window, kitView(context), placement));
        });
        var notes = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
        var ids = new AtomicReference<List<UUID>>();
        h.ui(() -> {
            var windows = alpha.get().windows();
            var owner = alpha.get().terminals().window(main).orElseThrow();
            notes.set(windows.openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"), owner, kitView(alpha.get())));
            UUID note = owner.activeTab().orElseThrow().id();
            assertThat(notes.get().mayMoveToNewTab()).as("its tab's only pane").isFalse();
            notes.get().moveToNewTab();
            alpha.get().terminals().tab(shell).orElseThrow().select();
            notes.get().split(dev.jasper.sdk.ui.SplitChoice.TERMINAL, Direction.RIGHT);
            var terminal = owner.activeTab().orElseThrow();
            assertThat(terminal.kind()).as("Terminal Right").isEqualTo(TabHandle.Kind.TERMINAL);
            alpha.get().terminals().tab(shell).orElseThrow().select();
            notes.get().split(windows.splitChoices().get(1), Direction.DOWN);
            UUID sketch = owner.activeTab().orElseThrow().id();
            ids.set(List.of(note, terminal.id(), sketch));
            assertThatIllegalArgumentException().as("not one of Jasper's choices").isThrownBy(() -> notes.get().split(
                new dev.jasper.sdk.ui.SplitChoice("Other", "test.alpha.split_right_other", "test.alpha.split_down_other"), Direction.RIGHT));
        });
        UUID note = ids.get().get(0), terminal = ids.get().get(1), sketch = ids.get().get(2);
        assertThat(h.windowTabs(main)).as("beside its own pane, whichever pane was focused")
            .containsExactly(List.of(shell), List.of(note, sketch, terminal));
        h.ui(() -> {
            assertThat(notes.get().mayMoveToNewTab()).isTrue();
            notes.get().moveToNewTab();
            assertThat(alpha.get().terminals().window(main).orElseThrow().activeTab().map(TabHandle::id)).as("its new tab shows")
                .contains(note);
        });
        assertThat(h.windowTabs(main)).as("a tab of its own, right after its old tab")
            .containsExactly(List.of(shell), List.of(sketch, terminal), List.of(note));
        h.ui(() -> {
            notes.get().split(dev.jasper.sdk.ui.SplitChoice.TERMINAL, Direction.DOWN);
            var prompt = alpha.get().windows().dialog(new DialogSpec("Question", notes.get(), true));
            assertThat(notes.get().mayMoveToNewTab()).as("a pane with a modal prompt stays").isFalse();
            notes.get().moveToNewTab();
            prompt.close();
        });
        assertThat(h.windowTabs(main)).hasSize(3);
        assertThat(h.windowTabs(main).get(2)).hasSize(2).startsWith(note);
    }

    /**
     * Workspace plan 8b Rulings 19: Terminal Right and Terminal Down beside a content tab open a local shell, so they need
     * {@code terminal.open}, as {@code Terminals.split} does; without it the call is refused, and nothing opens or is
     * selected.
     */
    @Test void aContentTabsTerminalSplitNeedsTerminalOpen() {
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        var note = new AtomicReference<UUID>();
        h.ui(() -> {
            var owner = alpha.get().terminals().window(main).orElseThrow();
            var notes = alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"), owner, kitView(alpha.get()));
            note.set(owner.activeTab().orElseThrow().id());
            alpha.get().terminals().tab(shell).orElseThrow().select();
            for (Direction direction : List.of(Direction.RIGHT, Direction.DOWN))
                assertThatThrownBy(() -> notes.split(dev.jasper.sdk.ui.SplitChoice.TERMINAL, direction))
                    .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> {
                        assertThat(failure.pluginId()).isEqualTo("test.alpha");
                        assertThat(failure.capability()).isEqualTo(Capabilities.TERMINAL_OPEN);
                    });
            assertThat(owner.activeTab().map(TabHandle::id)).as("nothing is selected").contains(shell);
        });
        assertThat(h.windowTabs(main)).as("nothing opens").containsExactly(List.of(shell), List.of(note.get()));
    }

    @Test void openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane() {
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/contributions/TabRegistryTest.java", [(
    "    /**\n     * Workspace plan 4a: the window a move leaves hears of it only after the move has reported itself, so a failed attach\n",
    """    /** Workspace plan 8b Rulings 18: a tab reaches its window for a terminal beside it and for Move Pane to New Tab. */
    @Test void aTabReachesItsWindowForATerminalBesideItAndMovePaneToNewTab() {
        var heard = new ArrayList<String>();
        boolean[] shares = {true};
        registry.addHost(new WorkspaceHost(main, tab -> {}, tab -> {}, tab -> {}, () -> {}, tab -> Optional.empty(),
            (tab, group, side) -> {}, (tab, side) -> heard.add("terminal " + side + " of " + tab.title()),
            tab -> shares[0], tab -> heard.add("new tab " + tab.title())));
        Tab notes = registry.open("dev.example.editor", "notes", "Notes", main, new JPanel());
        registry.splitTerminal(notes, Side.DOWN);
        assertThat(registry.mayMoveToNewTab(notes)).isTrue();
        registry.moveToNewTab(notes);
        shares[0] = false;
        assertThat(registry.mayMoveToNewTab(notes)).as("its tab's only pane").isFalse();
        registry.moveToNewTab(notes);
        shares[0] = true;
        try (var pin = notes.pin()) { assertThat(registry.mayMoveToNewTab(notes)).as("a pinned tab").isFalse(); }
        notes.close();
        registry.splitTerminal(notes, Side.RIGHT);
        registry.moveToNewTab(notes);
        assertThat(heard).as("a closed tab reaches nothing").containsExactly("terminal DOWN of Notes", "new tab Notes");
    }

    /**
     * Workspace plan 4a: the window a move leaves hears of it only after the move has reported itself, so a failed attach
""", 1)])
W = "jasper-app/src/test/java/dev/jasper/app/workspace/MovePaneToNewTabTest.java"
edit(W, [
    ("    /** A plugin's item moves the same way, and stays in its window. */\n",
     "    /** A plugin's item moves the same way, through the registry as {@code ContentTab.moveToNewTab} asks. */\n", 1),
    ("            window.moveToNewTab(view);\n", """            assertThat(model.tabs().mayMoveToNewTab(view)).isTrue();
            model.tabs().moveToNewTab(view);
""", 1),
    ("    /** Rulings 8 and 10: a terminal's context menu, the palette and the default keys offer it. */\n", """    /**
     * Workspace plan 8b Rulings 18: Terminal Right beside a plugin's item, as {@code ContentTab.split} asks, opens right
     * after the item in its tab, focused, though another pane had the focus.
     */
    @Test void aTerminalOpensBesideAPluginsItemThoughAnotherPaneWasFocused() throws Exception {
        var window = window();
        edt(() -> {
            Tab left = window.tabs().get(0), right = window.tabs().get(1);
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_DOWN);
            window.selectTab(left);
            assertThat(window.selectedTab()).isSameAs(left);
            model.tabs().splitTerminal(view, Side.RIGHT);
            Tab terminal = window.selectedTab();
            assertThat(terminal).as("the new terminal, focused").isNotIn(left, right, view);
            assertThat(terminal.kind()).isEqualTo(Tab.Kind.TERMINAL);
            assertThat(tabs(window).getFirst()).as("right after the item, in its tab")
                .containsExactly(left.id(), right.id(), view.id(), terminal.id());
        });
    }

    /** Rulings 8 and 10: a terminal's context menu, the palette and the default keys offer it. */
""", 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:compileTestFixturesJava :jasper-app:compileTestJava --continue`
Expected: FAIL: `ContentTab` has no `split`, `moveToNewTab` or `mayMoveToNewTab`, and `TabRegistry` no `splitTerminal`,
`mayMoveToNewTab` or `moveToNewTab`.

- [ ] **Step 3: Implement**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/contributions/WorkspaceHost.java` with:

```java
package dev.jasper.app.contributions;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.TabId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One window's workspace as the tab registry sees it (workspace tabs spec 9.1). EDT only.
 *
 * @param id the window's id
 * @param attach shows a tab the registry placed in this window, in a new tab of its own (tabs and panes spec 13)
 * @param detach takes a tab out of this window's layout, once the tab has closed (the registry disposes it first), as
 *     it moves to another window, or when the registry takes back an attach that failed
 * @param select shows a tab of this window and activates its group
 * @param toFront raises the window
 * @param groupOf the group that holds a tab of this window
 * @param attachBeside shows a tab the registry placed in this window in a new group on a side of one of its groups
 * @param splitTerminal selects a tab of this window, then opens a terminal in a new pane on a side of its pane (tabs and
 *     panes spec 8's Terminal Right and Terminal Down beside a plugin's item; workspace plan 8b Rulings 18)
 * @param mayMoveToNewTab whether Move Pane to New Tab would move a tab of this window (tabs and panes spec 9)
 * @param moveToNewTab Move Pane to New Tab: a tab's pane leaves its tab for a new tab right after it, which shows
 */
public record WorkspaceHost(UUID id, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select,
                            Runnable toFront, Function<TabId, Optional<GroupId>> groupOf, Beside attachBeside,
                            BiConsumer<Tab, Side> splitTerminal, Predicate<Tab> mayMoveToNewTab, Consumer<Tab> moveToNewTab) {
    /** Shows a tab in a new pane beside a pane of the window (workspace tabs spec 14's split placements). */
    @FunctionalInterface
    public interface Beside {
        /**
         * Shows {@code tab} in a new group on {@code side} of {@code group}.
         *
         * @param tab a tab the registry placed in this window
         * @param group a pane of this window; null for its focused pane
         * @param side where the new group goes
         */
        void attach(Tab tab, GroupId group, Side side);
    }

    /** Requires every value. */
    public WorkspaceHost {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(attach, "attach"); Objects.requireNonNull(detach, "detach");
        Objects.requireNonNull(select, "select"); Objects.requireNonNull(toFront, "toFront"); Objects.requireNonNull(groupOf, "groupOf");
        Objects.requireNonNull(attachBeside, "attachBeside"); Objects.requireNonNull(splitTerminal, "splitTerminal");
        Objects.requireNonNull(mayMoveToNewTab, "mayMoveToNewTab"); Objects.requireNonNull(moveToNewTab, "moveToNewTab");
    }

    /**
     * A workspace that opens no terminal beside a tab and moves no pane to a new tab, as a fake window in a test does.
     *
     * @param id the window's id
     * @param attach shows a tab the registry placed in this window
     * @param detach takes a tab out of this window
     * @param select shows a tab of this window
     * @param toFront raises the window
     * @param groupOf the group that holds a tab of this window
     * @param attachBeside shows a tab the registry placed beside one of its groups
     */
    public WorkspaceHost(UUID id, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select, Runnable toFront,
                         Function<TabId, Optional<GroupId>> groupOf, Beside attachBeside) {
        this(id, attach, detach, select, toFront, groupOf, attachBeside, (tab, side) -> { }, tab -> false, tab -> { });
    }

    /**
     * A workspace that attaches a tab placed beside a pane as it attaches any other, as a fake window in a test does.
     *
     * @param id the window's id
     * @param attach shows a tab the registry placed in this window
     * @param detach takes a tab out of this window
     * @param select shows a tab of this window
     * @param toFront raises the window
     * @param groupOf the group that holds a tab of this window
     */
    public WorkspaceHost(UUID id, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select, Runnable toFront,
                         Function<TabId, Optional<GroupId>> groupOf) {
        this(id, attach, detach, select, toFront, groupOf, (tab, group, side) -> attach.accept(tab));
    }

    /**
     * A workspace that keeps no groups, as a fake window in a test does.
     *
     * @param id the window's id
     * @param attach shows a tab the registry placed in this window
     * @param detach takes a tab out of this window
     * @param select shows a tab of this window
     * @param toFront raises the window
     */
    public WorkspaceHost(UUID id, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select, Runnable toFront) {
        this(id, attach, detach, select, toFront, tab -> Optional.empty());
    }
}
```

Then run the edit script for the SDK, the registry, `HostedUi`, the window and the fakes:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8b")
from edits import edit
S = "jasper-sdk/src/main/java/dev/jasper/sdk/ui/"
edit(S + "ContentTab.java", [("    void moveToMainWindow();\n", """    void moveToMainWindow();
    /**
     * Move Pane to New Tab (tabs and panes spec 9): the pane showing this item leaves its tab for a new tab right after it,
     * which shows; nothing closes or leaves the window. Its tab's only pane, a tab with a modal prompt open and a tab
     * being closed stay where they are ({@link #mayMoveToNewTab()}).
     *
     * @since 0.21.0
     */
    void moveToNewTab();
    /**
     * Whether {@link #moveToNewTab()} would move this item, for a menu entry that shows disabled otherwise.
     *
     * @return false for its tab's only pane, a tab with a modal prompt open, a tab being closed and a closed one
     * @since 0.21.0
     */
    boolean mayMoveToNewTab();
    /**
     * Split with a choice beside this item (tabs and panes spec 8): selects it, then opens {@code choice} in a new pane on
     * {@code direction}'s side of its pane, as the Split lists' entries do: a terminal for {@link SplitChoice#TERMINAL},
     * else the split item's view, through its action. A closed tab opens nothing.
     *
     * @param choice one of {@link Windows#splitChoices()}
     * @param direction the new pane's side
     * @throws IllegalArgumentException when {@code choice} is not one of {@link Windows#splitChoices()}
     * @throws dev.jasper.sdk.MissingCapabilityException when {@code choice} is {@link SplitChoice#TERMINAL} and the plugin
     *     lacks {@code terminal.open}
     * @since 0.21.0
     */
    void split(SplitChoice choice, dev.jasper.sdk.terminal.Direction direction);
""", 1)])
edit(S + "Windows.java", [(
    "     * own menu, as the Editor builds its right-click menu, lists these.\n",
    """     * own menu, as the Editor builds its right-click menu, lists these and runs one beside an item with
     * {@link ContentTab#split(SplitChoice, dev.jasper.sdk.terminal.Direction)}.
""", 1)])
A = "jasper-app/src/main/java/dev/jasper/app/"
edit(A + "contributions/TabRegistry.java", [("        if (target != null) move(tab, target.id());\n    }\n", """        if (target != null) move(tab, target.id());
    }

    /**
     * Split with a choice's Terminal Right and Terminal Down beside {@code tab}, as {@code ContentTab.split} asks (tabs
     * and panes spec 8; workspace plan 8b Rulings 18): its window selects it and opens a terminal in a new pane on
     * {@code side} of its pane. A closed tab, or one no window holds, opens nothing.
     *
     * @param tab the tab
     * @param side where the new pane goes
     */
    public void splitTerminal(Tab tab, Side side) {
        requireEdt();
        Objects.requireNonNull(side, "side");
        var host = tabs.contains(tab) && !tab.closed() ? hosts.get(tab.window()) : null;
        if (host != null) host.splitTerminal().accept(tab, side);
    }

    /**
     * Whether Move Pane to New Tab would move {@code tab} (tabs and panes spec 9): a tab that may move ({@link #movable})
     * whose window's tab holds another pane.
     *
     * @param tab the tab
     * @return whether it would move
     */
    public boolean mayMoveToNewTab(Tab tab) {
        requireEdt();
        var host = movable(tab) ? hosts.get(tab.window()) : null;
        return host != null && host.mayMoveToNewTab().test(tab);
    }

    /**
     * {@code ContentTab.moveToNewTab()} (tabs and panes spec 9): the tab's pane leaves its tab for a new tab right after
     * it, in its window, which shows it; nothing when {@link #mayMoveToNewTab} says no.
     *
     * @param tab the tab
     */
    public void moveToNewTab(Tab tab) {
        if (mayMoveToNewTab(tab)) hosts.get(tab.window()).moveToNewTab().accept(tab);
    }
""", 1)])
edit(A + "plugins/HostedUi.java", [
    ("    BooleanSupplier bundled = () -> false;\n", """    BooleanSupplier bundled = () -> false;
    /**
     * Requires {@code terminal.open}, then audits {@code what}: a terminal a content tab opens beside itself (workspace
     * plan 8b Rulings 19). Its context sets it.
     */
    java.util.function.Consumer<String> terminalOpen = what -> { };
""", 1),
    ("        public void moveToMainWindow() { requireUi(\"move content\"); entry.moveToMainWindow(); }\n",
    """        public void moveToMainWindow() { requireUi("move content"); entry.moveToMainWindow(); }
        public void moveToNewTab() { requireUi("move content"); model.tabs().moveToNewTab(entry); }
        public boolean mayMoveToNewTab() { requireUi("move content"); return model.tabs().mayMoveToNewTab(entry); }
        public void split(dev.jasper.sdk.ui.SplitChoice choice, dev.jasper.sdk.terminal.Direction direction) {
            requireUi("split content");
            java.util.Objects.requireNonNull(choice, "choice");
            java.util.Objects.requireNonNull(direction, "direction");
            if (!splitChoices().contains(choice)) throw new IllegalArgumentException("Not one of Jasper's split choices: " + choice.label());
            if (entry.closed()) return;
            boolean right = direction == dev.jasper.sdk.terminal.Direction.RIGHT;
            if (choice.equals(dev.jasper.sdk.ui.SplitChoice.TERMINAL)) {
                // A local shell, as Terminals.split opens one: terminal.open first (workspace plan 8b Rulings 19).
                terminalOpen.accept("opened a terminal beside content tab " + entry.id().value());
                model.tabs().splitTerminal(entry, right ? dev.jasper.app.layout.Side.RIGHT : dev.jasper.app.layout.Side.DOWN);
                return;
            }
            // A plugin's choice opens beside the focused pane, so the item is selected first (workspace plan 8b Rulings 18).
            model.tabs().select(entry);
            java.util.UUID window = entry.window();
            String action = right ? choice.rightAction() : choice.downAction();
            if (window != null) model.action(action).ifPresent(found -> found.invoke(new Contributions.Invocation(window, Optional.empty())));
        }
""", 1)])
edit(A + "plugins/HostedContext.java", [("        ui.bundled = () -> host.fileEditors.bundled(id);\n", """        ui.bundled = () -> host.fileEditors.bundled(id);
        ui.terminalOpen = what -> { gate.require(Capabilities.TERMINAL_OPEN); gate.audit(Capabilities.TERMINAL_OPEN, what); };
""", 1)])
edit(A + "workspace/WindowContent.java", [(
    "            (tab, group, side) -> attach(tab, false, group != null ? group : workspace().model().activeGroup(), side));\n",
    """            (tab, group, side) -> attach(tab, false, group != null ? group : workspace().model().activeGroup(), side),
            // A plugin's item: Terminal Right and Down beside it, and Move Pane to New Tab (workspace plan 8b Rulings 18).
            (tab, side) -> { if (parentTabs.contains(tab)) { selectTab(tab); split(side); } },
            this::mayMoveToNewTab, this::moveToNewTab);
""", 1)])
K = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/"
edit(K + "FakeTerminals.java", [("    private static PaneInfo openedInfo(OpenRequest.Local local) {\n",
    "    static PaneInfo openedInfo(OpenRequest.Local local) {\n", 1)])
edit(K + "FakeContent.java", [("    private void moveTo(FakeWorkspace.Window window){\n", """    /** Tabs and panes spec 9: its tab's only pane, a pinned item and one waiting on a close decision stay. */
    public boolean mayMoveToNewTab(){return movable()&&tab.group.recent.size()>1;}
    public void moveToNewTab(){if(!mayMoveToNewTab())return;ui.host.workspace.moveToNewTab(tab);select();}
    /** Spec 8: selected first, then the terminal beside it, or the split item's action, as the application runs them. */
    public void split(SplitChoice choice,dev.jasper.sdk.terminal.Direction direction){
        Objects.requireNonNull(choice,"choice");Objects.requireNonNull(direction,"direction");
        if(!ui.windows().splitChoices().contains(choice))throw new IllegalArgumentException("Not one of Jasper's split choices: "+choice.label());
        if(closed)return;
        boolean terminal=choice.equals(SplitChoice.TERMINAL);
        // Rulings 19: a local shell needs terminal.open, refused before anything is selected or opens.
        if(terminal)ui.context.terminals.require(dev.jasper.sdk.Capabilities.TERMINAL_OPEN);
        select();
        if(terminal){
            var local=(dev.jasper.sdk.terminal.OpenRequest.Local)dev.jasper.sdk.terminal.OpenRequest.local();
            ui.host.workspace.focus(ui.host.workspace.openTerminal(tab.window,"opened",FakeTerminals.openedInfo(local),tab));
            return;
        }
        ui.host.invoke(direction==dev.jasper.sdk.terminal.Direction.RIGHT?choice.rightAction():choice.downAction(),tab.window.id,null);
    }
    private void moveTo(FakeWorkspace.Window window){
""", 1)])
edit(K + "FakeWorkspace.java", [("    /** A pane closes with its item. */\n", """    /**
     * Move Pane to New Tab (tabs and panes spec 9): {@code tab} leaves its tab, which keeps its other panes, for a new tab
     * right after it, where it is selected. A tab's only pane stays.
     */
    void moveToNewTab(Tab tab) {
        Window window = tab.window;
        Group from = tab.group;
        if (!tab.open || from.recent.size() < 2) return;
        int end = 0;
        for (int i = 0; i < window.tabs.size(); i++) if (window.tabs.get(i).group == from) end = i;
        window.tabs.remove(tab);
        from.recent.remove(tab);
        var group = new Group();
        window.recent.add(group);
        tab.group = group;
        group.recent.add(tab);
        // The tab's old run of items ends one place earlier now: the new tab's item goes right after it.
        window.tabs.add(end, tab);
        select(tab);
    }

    /** A pane closes with its item. */
""", 1)])
T = "jasper-app/src/test/java/dev/jasper/app/plugins/"
edit(T + "TerminalFixture.java", [("    List<String> opened() { return List.copyOf(opened); }\n", """    List<String> opened() { return List.copyOf(opened); }

    /**
     * A terminal in a new pane right after the item {@code tabId}, in its tab, focused, as Split with a choice's Terminal
     * Right and Terminal Down open one beside a plugin's item (tabs and panes spec 8): either side lists it right after.
     */
    void splitBeside(UUID tabId) {
        Tab beside = tabs.get(tabId);
        if (beside != null) focusPane(openTerminal(beside.window.id, "split", null, beside));
    }

    /** Whether Move Pane to New Tab would move the item {@code tabId}: its tab holds another pane (tabs and panes spec 9). */
    boolean mayMoveToNewTab(UUID tabId) {
        Tab tab = tabs.get(tabId);
        return tab != null && tab.group.recent.size() > 1;
    }

    /** Move Pane to New Tab: the item {@code tabId} leaves its tab for a new tab right after it, where it is selected. */
    void moveToNewTab(UUID tabId) {
        if (!mayMoveToNewTab(tabId)) return;
        Tab tab = tabs.get(tabId);
        Window window = tab.window;
        Group from = tab.group;
        int end = 0;
        for (int i = 0; i < window.tabs.size(); i++) if (window.tabs.get(i).group == from) end = i;
        window.tabs.remove(tab);
        from.recent.remove(tab);
        var group = new Group();
        window.recent.add(group);
        tab.group = group;
        group.recent.add(tab);
        window.tabs.add(end, tab);
        select(tab);
    }
""", 1)])
edit(T + "AppContractTest.java", [(
    """                (tab, group, side) -> terminalFixture.addContentTab(id, tab.id().value(), tab::title, true)));
""", """                (tab, group, side) -> terminalFixture.addContentTab(id, tab.id().value(), tab::title, true),
                (tab, side) -> terminalFixture.splitBeside(tab.id().value()),
                tab -> terminalFixture.mayMoveToNewTab(tab.id().value()),
                tab -> {
                    terminalFixture.moveToNewTab(tab.id().value());
                    for (var other : tabs.inWindow(id)) other.active(other == tab);
                }));
""", 1)])
PY
```

`HostedUi.OwnedContent.split` calls Task 3's private `splitChoices()`, the outer class's.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.workspace.*' --continue`
Expected: PASS: the two contract cases on both runtimes, `TabRegistryTest`'s case, `MovePaneToNewTabTest`'s cases over
the registry, and every workspace test over the new host (the window's `workspaceHost` passes all ten functions; the other hosts take the shorter forms). Then
`./gradlew :jasper-app:check :jasper-plugin-editor:check`: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-sdk/src jasper-sdk-testkit/src jasper-app/src
git commit -F - <<'EOF'
SDK: a content tab splits beside itself and moves to a new tab

ContentTab.split opens any Split choice beside the item's own pane,
the terminal's through the registry, with terminal.open, and a
plugin's through its action with the item selected; moveToNewTab and
mayMoveToNewTab are Move Pane to New Tab for the item. The contract
pins them on both runtimes.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: The Split lists in Jasper's menus

Spec 2.10 and 8: the Tab menu and a terminal's context menu show one Split submenu (Rulings 6): Terminal Right and
Terminal Down, then each split item's Right and Down, the bundled plugins' first (Rulings 5). Each entry is its action,
with its shortcut and no icon (Rulings 22), so the palette and the keymap reach the same commands (Rulings 8). The fixed Tab menu's submenu is
refilled on every contribution pass, so an item that comes or goes shows at once; the classic bar's Window menu builds
its Tab part afresh each pass, Split included.

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/SplitListsTest.java` (and its ledger row)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java` (a field `:45`, the constructor `:97`,
  `rebuildContributedMenus` `:413`, `GROUP_ITEMS` and `tabMenu` (after Task 2, `:456-472`), two helpers before
  `toolWindowItems`, `contextMenu` (after Task 2)); `jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java`
  (`TAB`'s Javadoc `:12`)
- Test: `workspace/TabMenuTest.java`, `TerminalContextMenuTest.java` (Task 2's assertions), `WindowChromeMenusTest.java`
  (`:96-108`)

**Interfaces:**
- Consumes: Task 2's `ActionId.MOVE_PANE_TO_NEW_TAB` and its `GROUP_ITEMS` and `contextMenu` text; Task 3's
  `Contributions.splitItems()` and `SplitItem`; `WindowContributions.action(String)`.
- Produces: `WindowChrome.fillSplit(JMenu, Function<Action, JMenuItem>)` and `splitMenu()` (private); the Tab menu's
  first entry and a terminal's context menu's fifth are a `JMenu` titled "Split".

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/SplitListsTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.SplitItem;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.lifecycle.Subscription;
import java.awt.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tabs and panes spec 2.10 and 8: the Split lists of the Tab menu and a terminal's context menu, and what they run. */
class SplitListsTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final List<String> opened = new ArrayList<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private WindowContent window() throws Exception {
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            owner[0] = content(idleLauncher(pending), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            owner[0].connectContributions(model);
        });
        while (!pending.isEmpty()) pending.remove().run();
        return owner[0];
    }

    /**
     * A plugin's split item as {@code HostedUi} registers one: its two actions, with the item's icon, which open a plugin
     * view in a new pane beside the focused one, then the item.
     */
    private Subscription item(String plugin, String name, String label, boolean bundled) {
        String right = plugin + ".split_right_" + name, down = plugin + ".split_down_" + name;
        var rightAction = model.addAction(right, "Split Right with " + label, new ImageIcon(), List.of(), Optional.empty(), invocation -> {
            opened.add(right);
            model.tabs().open(plugin, right + opened.size(), label, invocation.windowId(), new JPanel(), TabRegistry.Placement.SPLIT_RIGHT);
        });
        var downAction = model.addAction(down, "Split Down with " + label, new ImageIcon(), List.of(), Optional.empty(), invocation -> {
            opened.add(down);
            model.tabs().open(plugin, down + opened.size(), label, invocation.windowId(), new JPanel(), TabRegistry.Placement.SPLIT_DOWN);
        });
        var listed = model.addSplitItem(new SplitItem(plugin + "." + name, label, null, right, down, bundled));
        return new Subscription(() -> { listed.close(); rightAction.close(); downAction.close(); });
    }

    /** The Tab menu's Split submenu, from the menu bar. */
    private static JMenu tabSplit(WindowContent window) {
        var bar = window.menuBar();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu == null || !"Tab".equals(menu.getText())) continue;
            for (Component child : menu.getMenuComponents())
                if (child instanceof JMenu split && "Split".equals(split.getText())) return split;
        }
        throw new AssertionError("no Split in the Tab menu");
    }

    /** A terminal's context menu's Split submenu. */
    private static JMenu contextSplit(WindowContent window) {
        for (Component child : window.chrome().contextMenu().getComponents())
            if (child instanceof JMenu split && "Split".equals(split.getText())) return split;
        throw new AssertionError("no Split in the context menu");
    }

    private static List<String> texts(JMenu menu) {
        var texts = new ArrayList<String>();
        for (Component child : menu.getMenuComponents()) if (child instanceof JMenuItem item) texts.add(item.getText());
        return texts;
    }

    private static JMenuItem entry(JMenu menu, String text) {
        for (Component child : menu.getMenuComponents())
            if (child instanceof JMenuItem item && text.equals(item.getText())) return item;
        throw new AssertionError("no " + text + " in " + texts(menu));
    }

    /** Rulings 5, 6 and 22: Terminal Right and Down, then each item's Right and Down, the bundled plugins' first; no icons. */
    @Test void bothListsReadTheTerminalThenEachItemTheBundledFirst() throws Exception {
        var window = window();
        edt(() -> {
            assertThat(texts(tabSplit(window))).containsExactly("Terminal Right", "Terminal Down");
            item("com.example.notes", "notes", "Notes", false);
            item("dev.jasper.editor", "editor", "Editor", true);
            var expected = List.of("Terminal Right", "Terminal Down", "Editor Right", "Editor Down", "Notes Right", "Notes Down");
            assertThat(texts(tabSplit(window))).as("the Tab menu's").containsExactlyElementsOf(expected);
            assertThat(texts(contextSplit(window))).as("a terminal's context menu's").containsExactlyElementsOf(expected);
            var keys = window.bindings();
            assertThat(entry(tabSplit(window), "Terminal Right").getAccelerator()).as("Split Right's shortcut")
                .isEqualTo(keys.strokeFor(ActionId.SPLIT_RIGHT).orElseThrow());
            assertThat(entry(contextSplit(window), "Terminal Down").getAccelerator())
                .isEqualTo(keys.strokeFor(ActionId.SPLIT_DOWN).orElseThrow());
            for (JMenu split : List.of(tabSplit(window), contextSplit(window)))
                for (Component child : split.getMenuComponents())
                    assertThat(((JMenuItem) child).getIcon()).as("%s shows no icon", ((JMenuItem) child).getText()).isNull();
        });
    }

    /** Spec 8 and Rulings 8: each entry runs its own action beside the focused pane, and the palette lists each. */
    @Test void eachEntryRunsItsActionBesideTheFocusedPane() throws Exception {
        var window = window();
        edt(() -> {
            item("dev.jasper.editor", "editor", "Editor", true);
            entry(tabSplit(window), "Terminal Right").doClick();
            assertThat(window.workspace().tabCount()).as("a terminal beside the first").isEqualTo(2);
            entry(contextSplit(window), "Editor Down").doClick();
            assertThat(opened).containsExactly("dev.jasper.editor.split_down_editor");
            assertThat(window.workspace().tabCount()).as("the plugin's view in a new pane of the same tab").isEqualTo(3);
            assertThat(window.parentTabCount()).isEqualTo(1);
            for (String id : List.of("split_right", "split_down", "dev.jasper.editor.split_right_editor", "dev.jasper.editor.split_down_editor"))
                assertThat(window.commands().find(id)).as("%s in the palette", id).isPresent();
        });
    }

    /** Review Focus 2: an item that goes leaves both lists at once, and the palette. */
    @Test void anItemThatGoesLeavesEverySplitListAndThePalette() throws Exception {
        var window = window();
        edt(() -> {
            var notes = item("com.example.notes", "notes", "Notes", false);
            var split = tabSplit(window);
            assertThat(texts(split)).contains("Notes Right", "Notes Down");
            notes.close();
            assertThat(texts(tabSplit(window))).as("the Tab menu's, at once").containsExactly("Terminal Right", "Terminal Down");
            assertThat(tabSplit(window)).as("the same submenu, refilled").isSameAs(split);
            assertThat(texts(contextSplit(window))).containsExactly("Terminal Right", "Terminal Down");
            assertThat(window.commands().find("com.example.notes.split_right_notes")).isEmpty();
        });
    }
}
```

Then run the edit script for the menu tests that name the old items, and add the new test's ledger row:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8b")
from edits import edit
import ledger
W = "jasper-app/src/test/java/dev/jasper/app/workspace/"
edit(W + "TabMenuTest.java", [(
    """            assertThat(items(window, "Tab")).containsExactly("Split Right", "Split Down", "Move Pane to New Tab", "Focus Pane Left",
""", """            assertThat(items(window, "Tab")).as("its plain items; Split is a submenu").containsExactly("Move Pane to New Tab", "Focus Pane Left",
""", 1)])
edit(W + "TerminalContextMenuTest.java", [(
    """            assertThat(labels(menu)).containsExactly("Copy", "Paste", "Find", "Clear Terminal", "Split Right", "Split Down",
                "Move Pane to New Tab", "Maximize Pane", "Close Pane", "---", "Move Tab to New Window");
""", """            assertThat(labels(menu)).containsExactly("Copy", "Paste", "Find", "Clear Terminal", "Split",
                "Move Pane to New Tab", "Maximize Pane", "Close Pane", "---", "Move Tab to New Window");
""", 1)])
edit(W + "WindowChromeMenusTest.java", [(
    """            assertThat(window.getFirst()).isEqualTo("Split Right");
            assertThat(window).as("the Tab menu's group, prompt and tab items with the plugin's Tab section, then the toggles")
                .containsSubsequence("Split Right", "Next Prompt", "---", "Next Tab", "Previous Tab", "---", "split", "---", "Toggle Hosts");
""", """            assertThat(window.getFirst()).isEqualTo("submenu:Split");
            assertThat(window).as("the Tab menu's Split list, pane, prompt and tab items with the plugin's Tab section, then the toggles")
                .containsSubsequence("submenu:Split", "Move Pane to New Tab", "Next Prompt", "---", "Next Tab", "Previous Tab", "---",
                    "split", "---", "Toggle Hosts");
""", 1),
    ('            model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TAB)).set(List.of(new MenuEntry.Item("dev.x.split")));\n',
     """            model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TAB)).set(List.of(new MenuEntry.Item("dev.x.split")));
            // A split item whose actions carry an icon (workspace plan 8b Rulings 22).
            for (String side : List.of("Right", "Down"))
                model.addAction("dev.x.split_" + side.toLowerCase(java.util.Locale.ROOT) + "_sketch", "Split " + side + " with Sketch",
                    new ImageIcon(), List.of(), Optional.empty(), invocation -> { });
            model.addSplitItem(new dev.jasper.app.contributions.SplitItem("dev.x.sketch", "Sketch", new ImageIcon(),
                "dev.x.split_right_sketch", "dev.x.split_down_sketch", false));
""", 1),
    ('            assertThat(window.getLast()).as("the tool-window toggles close the menu").isEqualTo("Toggle Hosts");\n',
     """            assertThat(window.getLast()).as("the tool-window toggles close the menu").isEqualTo("Toggle Hosts");
            var split = (JMenu) menu(owner, "Window").getMenuComponent(0);
            assertThat(items(split)).containsExactly("Terminal Right", "Terminal Down", "Sketch Right", "Sketch Down");
            for (Component child : split.getMenuComponents())
                assertThat(((JMenuItem) child).getIcon()).as("%s: the Split submenu shows no icons", ((JMenuItem) child).getText()).isNull();
""", 1)])
ledger.add(W + "SplitListsTest.java", "app.workspace", "SplitListsTest", W + "SplitListsTest.java",
    "Test/fixture ownership; New in workspace-tabs plan 8b Task 5; the Split lists of the Tab menu and a terminal's context menu")
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.SplitListsTest' --tests 'dev.jasper.app.workspace.TabMenuTest' --tests 'dev.jasper.app.workspace.TerminalContextMenuTest'`
Expected: FAIL: no Split submenu in the Tab menu or the context menu, and the plain items still start with Split Right.

- [ ] **Step 3: Implement**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8b")
from edits import edit
C = "jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java"
edit(C, [
    ("    private final java.util.EnumMap<MenuTarget.Slot, JMenu> standardMenus = new java.util.EnumMap<>(MenuTarget.Slot.class);\n",
     """    private final java.util.EnumMap<MenuTarget.Slot, JMenu> standardMenus = new java.util.EnumMap<>(MenuTarget.Slot.class);
    /** The built-once Tab menu's Split submenu, filled again on every contribution pass (workspace plan 8b Rulings 6). */
    private final JMenu tabSplit;
""", 1),
    ("        JMenu tab = tabMenu();\n", "        tabSplit = splitMenu();\n        JMenu tab = tabMenu(tabSplit);\n", 1),
    ("            var sections = contributions.model().menus();\n", """            fillSplit(tabSplit, MenuBarItems.Item::new);
            var sections = contributions.model().menus();
""", 1),
    ("""    /** The Tab menu's pane and prompt items, before Rename Tab and its tab items (workspace plan 8a Rulings 17). */
    private static final ActionId[] GROUP_ITEMS = {ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MOVE_PANE_TO_NEW_TAB,
        ActionId.FOCUS_PANE_LEFT, ActionId.FOCUS_PANE_RIGHT, ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN,
        ActionId.PREVIOUS_PROMPT, ActionId.NEXT_PROMPT};
""", """    /** The Tab menu's pane and prompt items after its Split submenu, before Rename Tab and its tab items (plan 8b Rulings 10). */
    private static final ActionId[] GROUP_ITEMS = {ActionId.MOVE_PANE_TO_NEW_TAB, ActionId.FOCUS_PANE_LEFT,
        ActionId.FOCUS_PANE_RIGHT, ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN, ActionId.PREVIOUS_PROMPT, ActionId.NEXT_PROMPT};
""", 1),
    ("""    /** Tab: splitting and focusing panes and moving between prompts, a rule, Rename Tab, a rule, the tab commands. */
    private JMenu tabMenu() {
        JMenu menu = menu("Tab", GROUP_ITEMS);
""", """    /** Tab, built afresh for a layout that gathers it into another menu on every pass, with a Split submenu of its own. */
    private JMenu tabMenu() { return tabMenu(splitMenu()); }

    /**
     * Tab (workspace plan 8b Rulings 10): the Split submenu {@code split}, Move Pane to New Tab, focusing panes and moving
     * between prompts, a rule, Rename Tab, a rule, the tab commands.
     */
    private JMenu tabMenu(JMenu split) {
        JMenu menu = menu("Tab");
        menu.add(split);
        for (ActionId id : GROUP_ITEMS) menu.add(new MenuBarItems.Item(owner.action(id)));
""", 1),
    ("    /** One item per tool window whose toggle action this window has, in registration order. */\n", """    /** A Split submenu for the menu bar, filled with the window's Split list. */
    private JMenu splitMenu() {
        JMenu split = menu("Split");
        fillSplit(split, MenuBarItems.Item::new);
        return split;
    }

    /**
     * Fills {@code split} with the window's Split list (tabs and panes spec 2.10 and 8; workspace plan 8b Rulings 5 and 6):
     * Terminal Right and Terminal Down, then each split item's Right and Down, the bundled plugins' first. Each entry is
     * its action, showing its shortcut and no icon (Rulings 22), under the list's own label; an entry whose action this
     * window no longer has is left out.
     *
     * @param split the submenu
     * @param item makes a menu-bar item or an ordinary popup item for an action
     */
    private void fillSplit(JMenu split, java.util.function.Function<Action, JMenuItem> item) {
        split.removeAll();
        var entries = new ArrayList<Map.Entry<String, Action>>();
        entries.add(Map.entry("Terminal Right", owner.action(ActionId.SPLIT_RIGHT)));
        entries.add(Map.entry("Terminal Down", owner.action(ActionId.SPLIT_DOWN)));
        if (contributions != null) for (var listed : contributions.model().splitItems()) {
            Action right = contributions.action(listed.rightAction()), down = contributions.action(listed.downAction());
            if (right != null) entries.add(Map.entry(listed.label() + " Right", right));
            if (down != null) entries.add(Map.entry(listed.label() + " Down", down));
        }
        for (var entry : entries) {
            JMenuItem built = item.apply(entry.getValue());
            built.setHideActionText(true);
            built.setText(entry.getKey());
            built.putClientProperty("html.disable", true);
            built.setIcon(null);
            split.add(built);
        }
    }

    /** One item per tool window whose toggle action this window has, in registration order. */
""", 1),
    ("""        for (ActionId id : new ActionId[]{ActionId.COPY, ActionId.PASTE, ActionId.FIND, ActionId.CLEAR_TERMINAL,
            ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.MOVE_PANE_TO_NEW_TAB, ActionId.MAXIMIZE_PANE,
            ActionId.CLOSE_PANE}) menu.add(owner.action(id));
""", """        for (ActionId id : new ActionId[]{ActionId.COPY, ActionId.PASTE, ActionId.FIND, ActionId.CLEAR_TERMINAL})
            menu.add(owner.action(id));
        // The Split list, as the Tab menu has it (tabs and panes spec 2.10; workspace plan 8b Rulings 6).
        JMenu split = new JMenu("Split");
        fillSplit(split, JMenuItem::new);
        menu.add(split);
        for (ActionId id : new ActionId[]{ActionId.MOVE_PANE_TO_NEW_TAB, ActionId.MAXIMIZE_PANE, ActionId.CLOSE_PANE})
            menu.add(owner.action(id));
""", 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java", [(
    "    /** The Tab menu: splitting and focusing tab groups, moving between prompts, and the tab commands. */\n",
    """    /**
     * The Tab menu: the Split submenu, Move Pane to New Tab, focusing panes, moving between prompts, and the tab commands.
     */
""", 1)])
PY
```

`setHideActionText(true)` keeps the list's label when an action's title changes (a plugin's `setTitle`); the item still
takes the action's enabled state and accelerator. `setIcon(null)` drops the icon the item takes from its action, which
the window sets once, when it makes the action (`WindowContributions.syncActions`). The context menu's styling call (`menuTree`) reaches the submenu.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --continue`
Expected: PASS, the classic Window menu included (`WindowChromeMenusTest`: its Tab part starts with the Split
submenu, with no icons). Then `./gradlew :jasper-sdk:check :jasper-app:check`: PASS (doclint over `StandardMenu`; the
ledger test sees `SplitListsTest`'s row).

- [ ] **Step 5: Commit**

```bash
git add -A jasper-sdk/src jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Tabs and panes: the Split lists

The Tab menu and a terminal's context menu show one Split submenu:
Terminal Right and Terminal Down, then each split item's Right and
Down, the bundled plugins' first. Each entry is its own action with
its shortcut and no icon, and an item that comes or goes shows at
once.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: The Editor in a tab or a pane

Spec 2.7, 2.8, 2.10 and 8: New Editor Tab opens an untitled file in a new tab, and the Editor's own split item gives
Split Right with Editor (New Editor Pane) and Split Down with Editor (Rulings 12 and 13). Every file the Editor opens
from outside opens in a new tab (Rulings 14). An editor's right-click menu ends with the Split submenu and Move Pane to
New Tab (Rulings 10), and lists Revert to Saved, which becomes an action with Reload vimrc (Rulings 20). The File menu
offers New Editor Tab and Split Right with Editor (Rulings 21). The Editor's ⌘W command is Close Pane (Rulings 15), and
`docs/editor.md` and `docs/keyboard-shortcuts.md` say what each key does now and where each command is. The Editor takes
its next minor (Rulings 16).

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/ui/PaneItems.java` (and its ledger row)
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java` (imports, constants `:47-48`, actions
  `:109-110`, File menu `:137`); `ui/EditorWindow.java` (`POPUP` `:51`, `close-tab` `:153`, `add` `:773`);
  `ui/Command.java` (`:12`); `plugins/editor/src/main/resources/plugin.toml` (`version`); `docs/editor.md` (`:9-15`,
  `:36`, `:42-43`, `:59-61`, `:173`, `:210`; plan 8a rewrites the ⌘W row `:54`); `docs/keyboard-shortcuts.md` (`:93`)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`, `ui/EditorWindowTest.java` (its context
  takes `terminal.open` `:63`, as the Editor's manifest does);
  `jasper-app/src/test/java/dev/jasper/app/plugins/TabShortcutCollisionTest.java` (Task 2's),
  `EditorDocumentPreservationTest.java` (`:70`)

**Interfaces:**
- Consumes: Task 1's `TabPlacement.NEW_TAB` and `FakePluginHost.windowTabs`; Task 3's `SplitItemSpec`,
  `Windows.splitItem`, `splitChoices`, `SplitChoice`, `Contributions.splitItems()`; Task 4's `ContentTab.split`,
  `moveToNewTab`, `mayMoveToNewTab` (the terminal choice needing `terminal.open`); `EditorWindow.newUntitled(WindowOwner,
  TabPlacement)`, `revert()`, `reloadVimrc()`.
- Produces:
  - Editor actions `dev.jasper.editor.new_editor_tab` ("New Editor Tab"), and through its split item
    `dev.jasper.editor.editor` ("Editor") `dev.jasper.editor.split_right_editor` ("Split Right with Editor") and
    `dev.jasper.editor.split_down_editor` ("Split Down with Editor"), `dev.jasper.editor.revert` ("Revert to Saved") and
    `dev.jasper.editor.reload_vimrc` ("Reload vimrc"), none with a default; `dev.jasper.editor.new` is gone; the File
    menu's items Open in Editor…, New Editor Tab, Split Right with Editor, New Editor Window;
  - `EditorPlugin.NEW_TAB`, `REVERT`, `RELOAD_VIMRC` and `EditorPlugin.SPLIT` (package-private);
  - `dev.jasper.editor.ui.PaneItems.install(JPopupMenu, Supplier<List<SplitChoice>>, ContentTab)` (package-private);
  - the Editor's content command `close-pane` "Close Pane" (⌘W / Ctrl+W); `close-tab` is gone; an editor's right-click
    menu lists Revert to Saved after Toggle Comment.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8b")
from edits import edit
E = "plugins/editor/src/test/java/dev/jasper/editor/"
edit(E + "EditorPluginTest.java", [
    ("                \"dev.jasper.editor.new|New Editor File|true\", \"dev.jasper.editor.show|Show Editor|true\");\n",
     "                \"dev.jasper.editor.new_editor_tab|New Editor Tab|true\", \"dev.jasper.editor.show|Show Editor|true\");\n", 1),
    ("            assertThat(host.menu(\"FILE\")).contains(\"item:dev.jasper.editor.open\", \"item:dev.jasper.editor.new\");\n",
     "            assertThat(host.menu(\"FILE\")).contains(\"item:dev.jasper.editor.open\", \"item:dev.jasper.editor.new_editor_tab\");\n", 1),
    ("            assertThat(host.menu(\"FILE\")).containsSubsequence(\"item:dev.jasper.editor.new\", \"item:dev.jasper.editor.new_window\");\n",
     "            assertThat(host.menu(\"FILE\")).containsSubsequence(\"item:dev.jasper.editor.new_editor_tab\", \"item:dev.jasper.editor.new_window\");\n", 1),
    ("            assertThat(host.invoke(\"dev.jasper.editor.new\", terminal, null)).isTrue();\n",
     "            assertThat(host.invoke(\"dev.jasper.editor.new_editor_tab\", terminal, null)).isTrue();\n", 1),
    ("    /** Workspace tabs spec 6.5: New Editor Window, with no default key, opens an untitled document in a new child window. */\n",
     """    /**
     * Tabs and panes spec 8 and 10: New Editor Tab opens an untitled file in a new tab, and the Editor's split item, whose
     * Split Right is New Editor Pane, one in a new pane beside the focused pane; the File menu offers both (Rulings 21).
     * None has a default key, nor have the actions Revert to Saved and Reload vimrc (Rulings 20).
     */
    @Test void newEditorTabOpensATabAndTheEditorsSplitItemAPaneBesideTheFocusedOne() throws Exception {
        try (var host = host()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
            UUID pane = host.addTerminalPane(shell, dev.jasper.sdk.terminal.PaneInfo.unknown());
            assertThat(host.actions()).contains("dev.jasper.editor.new_editor_tab|New Editor Tab|true",
                "dev.jasper.editor.split_right_editor|Split Right with Editor|true",
                "dev.jasper.editor.split_down_editor|Split Down with Editor|true",
                "dev.jasper.editor.revert|Revert to Saved|true", "dev.jasper.editor.reload_vimrc|Reload vimrc|true");
            for (String id : List.of("dev.jasper.editor.new_editor_tab", "dev.jasper.editor.split_right_editor",
                    "dev.jasper.editor.split_down_editor", "dev.jasper.editor.revert", "dev.jasper.editor.reload_vimrc"))
                assertThat(host.actionSpec(id)).as(id).hasValueSatisfying(spec -> assertThat(spec.defaultBinding()).as("no default key").isEmpty());
            assertThat(host.actionSpec("dev.jasper.editor.split_right_editor")).hasValueSatisfying(spec ->
                assertThat(spec.keywords()).as("the palette finds New Editor Pane").contains("new", "editor", "pane"));
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of(), Set.of(), consumer::set);
            assertThat(consumer.get().windows().splitChoices()).containsExactly(dev.jasper.sdk.ui.SplitChoice.TERMINAL,
                new dev.jasper.sdk.ui.SplitChoice("Editor", "dev.jasper.editor.split_right_editor", "dev.jasper.editor.split_down_editor"));
            assertThat(host.invoke("dev.jasper.editor.new_editor_tab", main, null)).isTrue();
            assertThat(host.windowTabs(main)).as("a new tab").hasSize(2);
            host.focusTerminalPane(pane);
            assertThat(host.invoke("dev.jasper.editor.split_right_editor", main, pane)).isTrue();
            assertThat(host.windowTabs(main).getFirst()).as("Editor Right: a pane beside the focused shell").hasSize(2).startsWith(shell);
            assertThat(host.invoke("dev.jasper.editor.split_down_editor", main, null)).isTrue();
            assertThat(host.windowTabs(main).getFirst()).as("Editor Down: beside the focused editor").hasSize(3);
            assertThat(host.contentTabs()).hasSize(3).allSatisfy(tab -> assertThat(host.contentPresentation(tab).title()).startsWith("Untitled"));
            assertThat(host.menu("FILE")).as("New Editor Tab, then New Editor Pane").containsSubsequence("item:dev.jasper.editor.open",
                "item:dev.jasper.editor.new_editor_tab", "item:dev.jasper.editor.split_right_editor", "item:dev.jasper.editor.new_window");
        }
    }

    /** Workspace tabs spec 6.5: New Editor Window, with no default key, opens an untitled document in a new child window. */
""", 1)])
edit(E + "ui/EditorWindowTest.java", [
    ('        context = host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of()), Set.of(), Set.of(), c -> { });\n',
     """        // terminal.open, as the Editor's manifest declares: Terminal Right and Down beside an editor need it (plan 8b Rulings 19).
        context = host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of("terminal.open")), Set.of(), Set.of(), c -> { });
""", 1),
    ("    @Test void editLeavesAFolderAndAFileInAMissingFolderToTheDesktop() {\n", """    /**
     * Review Focus 5 and Rulings 14: a file opened from outside opens in a new tab of its window, never beside a pane, and
     * a window with no tab, as the main window's empty workspace, takes it as its one tab.
     */
    @Test void aFileOpenedFromOutsideOpensInANewTabOfItsWindow() throws Exception {
        UUID main = host.addTerminalWindow(), shell = host.addTerminalTab(main, "zsh");
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        window.newUntitled(handle, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
        assertThat(host.windowTabs(main)).as("an editor pane beside the shell").hasSize(1);
        assertThat(window.edit(file("a.txt", "a\\n"), OptionalInt.empty(), Optional.of(handle))).as("a Cmd-click").isTrue();
        window.openAt(file("b.txt", "b\\n"), 1, 1, handle);
        window.openLocal(file("c.txt", "c\\n"));
        drain();
        var tabs = host.windowTabs(main);
        assertThat(tabs).as("each file in a tab of its own").hasSize(4);
        assertThat(tabs.getFirst()).hasSize(2).startsWith(shell);
        assertThat(tabs.subList(1, 4)).allSatisfy(tab -> assertThat(tab).hasSize(1));
        UUID empty = host.addTerminalWindow();
        window.openAt(file("d.txt", "d\\n"), 1, 1, context.terminals().window(empty).orElseThrow());
        drain();
        assertThat(host.windowTabs(empty)).as("its one tab").hasSize(1).allSatisfy(tab -> assertThat(tab).hasSize(1));
    }

    /** Spec 2.10 and 9, Rulings 20: an editor's right-click menu lists Revert to Saved, splits and moves its pane. */
    @Test void anEditorsRightClickMenuSplitsBesideItAndMovesItsPaneToANewTab() throws Exception {
        context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("dev.jasper.editor.editor", "Editor"),
            (shown, placement) -> window.newUntitled(shown, placement));
        UUID main = host.addTerminalWindow();
        host.addTerminalTab(main, "zsh");
        WindowHandle handle = context.terminals().window(main).orElseThrow();
        window.openLocal(file("a.txt", "a\\n"), handle);
        drain();
        var popup = window.selected().orElseThrow().area().getPopupMenu();
        opened(popup);
        assertThat(texts(submenu(popup, "Split"))).containsExactly("Terminal Right", "Terminal Down", "Editor Right", "Editor Down");
        assertThat(item(popup.getComponents(), "Revert to Saved").isEnabled()).as("Rulings 20").isTrue();
        assertThat(item(popup.getComponents(), "Move Pane to New Tab").isEnabled()).as("its tab's only pane").isFalse();
        item(submenu(popup, "Split").getMenuComponents(), "Editor Right").doClick();
        UUID editor = host.windowTabs(main).get(1).getFirst();
        assertThat(host.windowTabs(main).get(1)).as("an untitled editor beside it, in its tab").hasSize(2);
        item(submenu(popup, "Split").getMenuComponents(), "Terminal Down").doClick();
        assertThat(host.windowTabs(main).get(1)).as("a terminal beside it").hasSize(3).startsWith(editor);
        opened(popup);
        assertThat(item(popup.getComponents(), "Move Pane to New Tab").isEnabled()).isTrue();
        item(popup.getComponents(), "Move Pane to New Tab").doClick();
        assertThat(host.windowTabs(main)).as("its own tab, right after its old one").hasSize(3);
        assertThat(host.windowTabs(main).get(2)).containsExactly(editor);
    }

    /** The menu opening, as a right-click shows it: the Editor's pane entries are built again. */
    private static void opened(javax.swing.JPopupMenu popup) {
        for (var listener : popup.getPopupMenuListeners())
            if (listener instanceof PaneItems items) items.popupMenuWillBecomeVisible(new javax.swing.event.PopupMenuEvent(popup));
    }

    private static javax.swing.JMenu submenu(javax.swing.JPopupMenu popup, String text) {
        for (var child : popup.getComponents()) if (child instanceof javax.swing.JMenu menu && text.equals(menu.getText())) return menu;
        throw new AssertionError("no " + text + " submenu");
    }

    private static List<String> texts(javax.swing.JMenu menu) {
        var texts = new ArrayList<String>();
        for (var child : menu.getMenuComponents()) if (child instanceof javax.swing.JMenuItem entry) texts.add(entry.getText());
        return texts;
    }

    private static javax.swing.JMenuItem item(java.awt.Component[] children, String text) {
        for (var child : children) if (child instanceof javax.swing.JMenuItem entry && text.equals(entry.getText())) return entry;
        throw new AssertionError("no " + text);
    }

    @Test void editLeavesAFolderAndAFileInAMissingFolderToTheDesktop() {
""", 1)])
T = "jasper-app/src/test/java/dev/jasper/app/plugins/TabShortcutCollisionTest.java"
edit(T, [
    ("import dev.jasper.app.contributions.Contributions;\n",
     "import dev.jasper.app.contributions.Contributions;\nimport dev.jasper.app.contributions.SplitItem;\n", 1),
    ("""                    .containsExactly(ActionId.MOVE_PANE_TO_NEW_TAB.id());
            }
        });
    }
""", """                    .containsExactly(ActionId.MOVE_PANE_TO_NEW_TAB.id());
            }
        });
    }

    /**
     * Tabs and panes spec 10 and 16 (workspace plan 8b Rulings 12 and 20): the Editor's New Editor Tab, its split item's
     * two actions, Revert to Saved and Reload vimrc are registered, have no default on either platform, and can be bound.
     */
    @Test void theEditorsNewActionsHaveNoDefaultAndCanBeBound() throws Exception {
        var extensions = started();
        var ids = List.of("dev.jasper.editor.new_editor_tab", "dev.jasper.editor.split_right_editor",
            "dev.jasper.editor.split_down_editor", "dev.jasper.editor.revert", "dev.jasper.editor.reload_vimrc");
        onEdt(() -> {
            assertThat(contributions.splitItems()).extracting(SplitItem::label).as("the Editor's split item").contains("Editor");
            assertThat(extensions).extracting(KeyBindings.Extension::id).containsAll(ids);
            for (boolean mac : new boolean[]{true, false}) {
                var defaults = KeyBindings.defaults(mac).withExtensions(extensions).bindings();
                for (String id : ids) assertThat(defaults.strokeFor(id)).as("%s has no default", id).isEmpty();
                String key = mac ? "ctrl+cmd+e" : "ctrl+alt+shift+e";
                for (String id : ids) {
                    var bound = KeyBindings.withOverrides(mac, Map.of(id, key)).withExtensions(extensions).bindings();
                    assertThat(bound.strokeFor(id)).as("%s can be bound", id).isEqualTo(KeyBindings.parse(key, mac));
                }
            }
        });
    }
""", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/EditorDocumentPreservationTest.java", [(
    '"Open…", "New File", "Close Tab", "Find…",', '"Open…", "New File", "Close Pane", "Find…",', 1)])
PY
```

The two `EditorWindowTest` files' text `"a\\n"` is the Java escape `"a\n"`, written `\\n` in the script.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.EditorPluginTest' --tests 'dev.jasper.editor.ui.EditorWindowTest'`
Expected: FAIL: no `dev.jasper.editor.new_editor_tab`, no split item, no Revert to Saved or Reload vimrc action, and no
Split submenu or Revert to Saved in an editor's right-click menu.

- [ ] **Step 3: Implement**

Create `plugins/editor/src/main/java/dev/jasper/editor/ui/PaneItems.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.sdk.terminal.Direction;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.SplitChoice;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * The pane entries of an editor's right-click menu (tabs and panes spec 2.10 and 9; workspace plan 8b Rulings 10): after
 * a rule, a Split submenu with Jasper's Split choices, each Right and Down, and Move Pane to New Tab, which shows
 * disabled while the editor is its tab's only pane. They are built again each time the menu opens, so a split item that
 * registers or goes later shows at once. UI thread only.
 */
final class PaneItems implements PopupMenuListener {
    private final JPopupMenu popup;
    private final Supplier<List<SplitChoice>> choices;
    private final ContentTab pane;
    private final List<Component> added = new ArrayList<>();

    private PaneItems(JPopupMenu popup, Supplier<List<SplitChoice>> choices, ContentTab pane) {
        this.popup = popup;
        this.choices = choices;
        this.pane = pane;
    }

    /**
     * Adds the entries at the end of {@code popup}, for the editor {@code pane} holds, and builds them again whenever the
     * menu opens.
     *
     * @param popup the text area's right-click menu
     * @param choices Jasper's Split choices, asked each time
     * @param pane the editor's item
     * @return the entries' owner
     */
    static PaneItems install(JPopupMenu popup, Supplier<List<SplitChoice>> choices, ContentTab pane) {
        var items = new PaneItems(popup, choices, pane);
        popup.addPopupMenuListener(items);
        items.rebuild();
        return items;
    }

    /** The entries again, from the current choices and whether the pane may move; none once the pane is closed. */
    void rebuild() {
        for (Component component : added) popup.remove(component);
        added.clear();
        if (pane.isClosed()) return;
        var split = new JMenu("Split");
        for (SplitChoice choice : choices.get()) {
            split.add(item(choice.label() + " Right", () -> pane.split(choice, Direction.RIGHT)));
            split.add(item(choice.label() + " Down", () -> pane.split(choice, Direction.DOWN)));
        }
        var move = item("Move Pane to New Tab", pane::moveToNewTab);
        move.setEnabled(pane.mayMoveToNewTab());
        add(new JPopupMenu.Separator());
        add(split);
        add(move);
    }

    private void add(Component component) {
        popup.add(component);
        added.add(component);
    }

    private static JMenuItem item(String text, Runnable run) {
        var item = new JMenuItem(text);
        item.putClientProperty("html.disable", true);
        item.addActionListener(event -> run.run());
        return item;
    }

    @Override public void popupMenuWillBecomeVisible(PopupMenuEvent event) { rebuild(); }
    @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { }
    @Override public void popupMenuCanceled(PopupMenuEvent event) { }
}
```

Then run the edit script for the plugin, its window, its version and its guide:

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8b")
from edits import edit
import ledger
M = "plugins/editor/src/main/"
P = M + "java/dev/jasper/editor/EditorPlugin.java"
edit(P, [
    ("import dev.jasper.sdk.ui.PluginMenu;\n", "import dev.jasper.sdk.ui.PluginMenu;\nimport dev.jasper.sdk.ui.SplitItemSpec;\n", 1),
    ("""    static final String OPEN = "dev.jasper.editor.open", NEW = "dev.jasper.editor.new", SHOW = "dev.jasper.editor.show",
        NEW_WINDOW = "dev.jasper.editor.new_window";
""", """    static final String OPEN = "dev.jasper.editor.open", NEW_TAB = "dev.jasper.editor.new_editor_tab", SHOW = "dev.jasper.editor.show",
        NEW_WINDOW = "dev.jasper.editor.new_window", REVERT = "dev.jasper.editor.revert",
        RELOAD_VIMRC = "dev.jasper.editor.reload_vimrc";
    /**
     * The Editor's split item (tabs and panes spec 8 and 10): Split Right with Editor, which is New Editor Pane, and Split
     * Down with Editor (workspace plan 8b Rulings 13).
     */
    static final SplitItemSpec SPLIT = SplitItemSpec.of("dev.jasper.editor.editor", "Editor").withIcon("file");
""", 1),
    ("""        context.actions().register(ActionSpec.of(NEW, "New Editor File").withKeywords(List.of("editor", "new", "file", "untitled")),
            invoked -> window().newUntitled(invoked.window()));
""", """        // Tabs and panes spec 8 and 10: an untitled file in a new tab, or, through the split item, in a new pane beside the
        // focused one; none has a default key.
        context.actions().register(ActionSpec.of(NEW_TAB, "New Editor Tab").withKeywords(List.of("editor", "new", "tab", "file", "untitled")),
            invoked -> window().newUntitled(invoked.window(), dev.jasper.sdk.ui.TabPlacement.NEW_TAB));
        context.windows().splitItem(SPLIT, (shown, placement) -> window().newUntitled(shown, placement));
        // Workspace plan 8b Rulings 20: the tab strip's menu, which hides with one tab, was these two's only place; as
        // actions with no default key they reach the palette and the keymap.
        context.actions().register(ActionSpec.of(REVERT, "Revert to Saved").withKeywords(List.of("editor", "revert", "saved", "discard")),
            invoked -> window().revert());
        context.actions().register(ActionSpec.of(RELOAD_VIMRC, "Reload vimrc").withKeywords(List.of("editor", "vim", "vimrc", "reload")),
            invoked -> window().reloadVimrc());
""", 1),
    ("        file.add(NEW);\n", "        file.add(NEW_TAB);\n        file.add(SPLIT.rightAction()); // New Editor Pane (workspace plan 8b Rulings 21)\n", 1)])
U = M + "java/dev/jasper/editor/ui/"
edit(U + "EditorWindow.java", [
    ('    private static final Set<String> POPUP = Set.of("save", "find", "go-to-line", "toggle-comment");\n',
     """    // Revert to Saved too: the tab strip's menu, its other place, hides with one tab (workspace plan 8b Rulings 20).
    private static final Set<String> POPUP = Set.of("save", "find", "go-to-line", "toggle-comment", "revert");
""", 1),
    ("""            new Command("close-tab", "Close Tab", Optional.of(KeyNames.stroke(KeyEvent.VK_W, 0)), false, this::closeCurrent),
""", """            new Command("close-pane", "Close Pane", Optional.of(KeyNames.stroke(KeyEvent.VK_W, 0)), false, this::closeCurrent),
""", 1),
    ("        handle.onClosed(() -> remove(tab));\n", """        handle.onClosed(() -> remove(tab));
        // Tabs and panes spec 2.10 and 9: the right-click menu's Split submenu and Move Pane to New Tab.
        PaneItems.install(tab.area().getPopupMenu(), context.windows()::splitChoices, handle);
""", 1)])
edit(U + "Command.java", [(
    " * @param bound whether the shortcut goes in input maps; Close Tab's Cmd/Ctrl+W does not (the window's menu owns it)\n",
    " * @param bound whether the shortcut goes in input maps; Close Pane's Cmd/Ctrl+W does not (the window's menu owns it)\n", 1)])
edit(M + "resources/plugin.toml", [('version = "0.11.0"\n', 'version = "0.12.0"\n', 1)])
ledger.add(U + "PaneItems.java", "dev.jasper.editor", "PaneItems", "plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java",
    "New in workspace-tabs plan 8b Task 6; the vendor popup's Split submenu and Move Pane to New Tab, plain JMenu and JMenuItem as DocumentArea.menuItem builds")
edit("docs/editor.md", [
    ("""  palette, and **Open…** in an editor tab's ⋯ menu.
""", """  palette, and, while the window shows its tab strip, **Open…** in the menu of a file's tab.
""", 1),
    ("""- **New Editor File**, or **New File** (Cmd/Ctrl+N) inside an editor tab, for an untitled tab; **New Editor Window**
  in the File menu, the palette or the toolbar's New Editor Window button (no default shortcut) opens the untitled tab in a
  new child window.
""", """- **New Editor Tab** in the File menu or the palette, or **New File** (Cmd/Ctrl+N) inside an editor, for an untitled file
  in a new tab; **Split Right with Editor** (New Editor Pane, also in the File menu) and **Split Down with Editor** put
  one in a new pane beside the focused pane, from the palette or as Editor Right and Editor Down in the Split submenus of
  the Tab menu and of a terminal's or an editor's right-click menu; **New Editor Window** in the File menu, the palette or
  the toolbar's New Editor Window button opens the untitled tab in a new child window. None of them has a default
  shortcut; Settings › Keymap binds them.
""", 1),
    ("Opening a file that is already open selects its tab. Files larger than `max_file_mib` and binary\n",
     "Opening a file that is already open selects its tab; any other file opens in a new tab of the window it was opened\nfrom. Files larger than `max_file_mib` and binary\n", 1),
    ("""name, the parent folder's name. The ⋯ button at the right of the tab row lists every command with
its shortcut.""", """name, the parent folder's name. While the window shows its tab strip, the menu of the file's tab
lists every command with its shortcut.""", 1),
    ("| ⇧⌘] / Ctrl+Shift+], ⇧⌘[ / Ctrl+Shift+[ | Next, previous tab |\n",
     "| ⇧⌘] / Ctrl+PageDown, ⇧⌘[ / Ctrl+PageUp | Next, previous tab of the window |\n", 1),
    ("Revert to Saved, Language… and Toggle Soft Wrap have no shortcut; they are in the ⋯ menu.\n",
     """Revert to Saved has no shortcut; it is in an editor's right-click menu and the palette. Language… and Toggle Soft Wrap
are in the status line. An editor's right-click menu ends with **Split** (Terminal Right, Terminal Down, Editor Right,
Editor Down, then each plugin's split items), which opens the choice in a new pane beside the editor, and **Move Pane to
New Tab**, which gives the editor's pane a tab of its own while its tab holds another pane.
""", 1),
    ("and again with **Reload vimrc** (in the ⋯ menu and the command palette), which",
     "and again with **Reload vimrc** (in the command palette and Settings › Keymap), which", 1),
    ("The Editor's commands (Save, Find, Close Tab), Jasper's window shortcuts", "The Editor's commands (Save, Find, Close Pane), Jasper's window shortcuts", 1)])
edit("docs/keyboard-shortcuts.md", [(
    "The bundled Editor's tabs have their own shortcuts, listed in its ⋯ menu and in [Editor](editor.md).\n",
    "The bundled Editor's tabs have their own shortcuts, listed in [Editor](editor.md) and, while the window shows its tab\n"
    "strip, in the menu of a file's tab.\n", 1)])
PY
```

`EditorWindow.newUntitled(WindowOwner)` and `add(session, ref)` already use `NEW_TAB` (Task 1), so Cmd-click (`edit`),
`jasper editor` (`openAt`), Open in Editor… (`openLocations` → `open`), Recent Files (`open`), a drop
(`openLocalFiles`), `:e` and New File all open a new tab; the tests above pin the outside routes.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:check :jasper-app:test --tests 'dev.jasper.app.plugins.*' --continue`
Expected: PASS: the Editor's tests above and the rest (`EditorTabTest`'s own `close-tab` command is a local fixture),
the staged Editor's actions in `TabShortcutCollisionTest`, and the Editor's own staged tests (`StagedEditorHost`;
`EditorDocumentPreservationTest` reads Close Pane among the staged Editor's commands). Then
`./gradlew :jasper-app:check`: PASS (the ledger test sees `PaneItems`'s row; `AppDocumentationTest` checks
`docs/editor.md`'s links).

- [ ] **Step 5: Commit**

```bash
git add -A plugins/editor jasper-app/src docs/editor.md docs/keyboard-shortcuts.md docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Editor: a new tab or a pane beside the focused one

New Editor Tab opens an untitled file in a new tab, and the Editor's
split item gives Split Right with Editor (New Editor Pane) and Split
Down with Editor; the File menu offers New Editor Tab and Split Right
with Editor. Every file opened from outside opens in a new tab.
Revert to Saved and Reload vimrc become actions. An editor's
right-click menu lists Revert to Saved and ends with Split and Move
Pane to New Tab, and its Cmd+W command is Close Pane. Editor 0.12.0.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 7: Documentation, STATUS and the whole build

The SDK guides, the README, the configuration and palette guides and `docs/STATUS.md` say what landed (spec 11's
"`jasper-sdk/README.md`, `docs/plugin-authoring.md` and `docs/sdk-architecture.md` move with the code"); the source
hygiene check and `./gradlew check` close the plan.

**Files:**
- Modify: `jasper-sdk/README.md` (the package table `:21`, Main and child windows, after 8a `:56`, `:62`, a section
  before Tabs of every kind); `docs/plugin-authoring.md` (the testkit's list `:701`, after 8a `:924-926`); `docs/sdk-architecture.md` (after 8a
  `:398-400`); `docs/command-palette.md` (`:73-74`); `docs/configuration.md` (`:713`); `README.md` (after 8a `:113`);
  `docs/STATUS.md` (a new entry at the top); this plan's status banner (`:3`)

**Interfaces:**
- Consumes: every task's names, as they landed.
- Produces: the STATUS entry plan 8c's and the 6a re-plan's writers read.

- [ ] **Step 1: Edit the documents**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8b")
from edits import edit
edit("jasper-sdk/README.md", [
    ("`ContentTab`, `TabPlacement`, `ContentSpec`,", "`ContentTab`, `TabPlacement`, `SplitItemSpec`, `SplitChoice`, `ContentSpec`,", 1),
    ("takes a `TabPlacement`: `ACTIVE_GROUP` (the three-argument form", "takes a `TabPlacement`: `NEW_TAB` (the three-argument form", 1),
    ("a window's tabs sit in groups, and `contentTab` and `contentView` find a plugin's content tab by its title.\n",
     """a window holds tabs of panes, which `windowTabs` lists, and `contentTab` and `contentView` find a plugin's content tab
by its title.

## Tabs, panes and split items (SDK 0.21.0)

A window holds tabs, each holding panes; a `TabHandle` is the item one pane shows, and `WindowHandle.tabs()` lists them
tab by tab, then pane by pane. `Windows.splitItem(SplitItemSpec, opener)` adds a plugin's view to every Split list
(after the terminal, the bundled plugins' items first) with two bindable actions, `Split Right with` and `Split Down
with` the item's label, whose ids follow from the item's (`dev.example.notes.notes` gives
`dev.example.notes.split_right_notes`); running one calls the opener with the window and `SPLIT_RIGHT` or `SPLIT_DOWN`.
`Windows.splitChoices()` lists the Split choices for a menu a plugin builds itself, `ContentTab.split(choice, direction)`
opens one beside that item's pane (the terminal's needs `terminal.open`), and `ContentTab.moveToNewTab()` (with
`mayMoveToNewTab()`) gives its pane a tab of its own.
""", 1)])
edit("docs/plugin-authoring.md", [
    ("it in a new tab of the window (`TabPlacement.ACTIVE_GROUP`)", "it in a new tab of the window (`TabPlacement.NEW_TAB`)", 1),
    ("`moveTab`, `activateTerminalWindow`, `focusTerminalPane` and `closeTerminalPane` script a workspace; `host.sent(paneId)` and\n",
     "`moveTab`, `activateTerminalWindow`, `focusTerminalPane` and `closeTerminalPane` script a workspace;\n"
     "`host.windowTabs(windowId)` lists a window's tabs, each its items pane by pane; `host.sent(paneId)` and\n", 1),
    ("content tab of its window: `WindowHandle.tabs()` lists it and the `TAB_*` topics report it.\n",
     """content tab of its window: `WindowHandle.tabs()` lists it and the `TAB_*` topics report it.

Since SDK 0.21, `Windows.splitItem(SplitItemSpec.of(id, label), (window, placement) -> ...)` puts a plugin's view in every
Split list (the Tab menu's and the right-click menus' Split submenus) after the terminal, with two actions, "Split Right
with" and "Split Down with" the label, that users bind under `[keybindings]` and the plugin may place in its menus; the
opener receives the window and `SPLIT_RIGHT` or `SPLIT_DOWN` and opens its view there with `openTab`. A plugin that
builds its own right-click menu, as the Editor does, lists `Windows.splitChoices()` and runs one with
`ContentTab.split(choice, direction)` (the terminal's needs `terminal.open`), and offers Move Pane to New Tab with
`ContentTab.moveToNewTab()`, disabled while `mayMoveToNewTab()` is false.
""", 1)])
edit("docs/sdk-architecture.md", [("owners are rejected.\n", """owners are rejected. A split item (`Windows.splitItem`, SDK 0.21) joins every window's Split lists through the
application's contributions model, its two actions registered as the plugin's own; `ContentTab.split` and
`moveToNewTab` reach the item's window through the tab registry.
""", 1)])
edit("docs/command-palette.md", [(
    """Settings opens the existing configuration file in the [default editor](editor.md#default-editor). Split Right
opens a terminal tab in a new group to the right; Split Down opens one in a new group below.
""", """Settings opens the existing configuration file in the [default editor](editor.md#default-editor). Split Right
opens a terminal in a new pane to the right of the focused pane and Split Down one below; Split Right with Editor, Split
Down with Editor and each plugin's split items do the same with their views, and Move Pane to New Tab gives the focused
pane a tab of its own.
""", 1)])
edit("docs/configuration.md", [("`close_pane`, `close_tab`, `split_right`, `split_down`,",
    "`close_pane`, `close_tab`, `split_right`, `split_down`, `move_pane_to_new_tab`,", 1)])
edit("README.md", [("Cmd+W close pane, Cmd+Option+W close tab.",
    "Cmd+W close pane, Cmd+Option+W close tab, Cmd+Option+T move the focused pane into a tab of its own. The Split submenu "
    "of the Tab menu and of a terminal's or an editor's right-click menu opens a terminal, an editor or a plugin's view "
    "in a new pane beside the focused one.", 1)])
PY
```

Each edit replaces one sentence or adds one paragraph where the guide already names placements, actions or keys
(AGENTS.md "Documentation edits"); `docs/editor.md` moved in Task 6.

- [ ] **Step 2: Add the STATUS entry**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan8b")
from edits import edit
edit("docs/STATUS.md", [("# Jasper — Status and Handoff\n\n", """# Jasper — Status and Handoff

**Workspace tabs, plan 8b: split with a choice — <date>** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-20-workspace-8b-split-with.md),
[spec](superpowers/specs/2026-10-20-jasper-tabs-and-panes-design.md)). A new pane can hold a terminal, an editor or a
plugin's view. The SDK (0.21.0, unreleased) renames `TabPlacement.ACTIVE_GROUP` to `NEW_TAB` and gains split items:
`Windows.splitItem(SplitItemSpec, opener)` lists a plugin's view in every Split list and registers its Split Right with
and Split Down with actions, which bind like any action and call the opener with the window and `SPLIT_RIGHT` or
`SPLIT_DOWN`; `Windows.splitChoices()`, `ContentTab.split` (the terminal's needs `terminal.open`), `moveToNewTab` and
`mayMoveToNewTab` let a plugin build the same entries into its own menu. The testkit's fake and the app's contract
fixture hold a window's tabs of panes and show them through `windowTabs`, and the contract pins the placements, the
split items and the pane commands on both runtimes. The app keeps split items in `Contributions` (`SplitItem`); the Tab
menu and a terminal's context menu show one Split submenu (Terminal Right, Terminal Down, then each item's Right and
Down, the bundled plugins' first, with no icons). Move Pane to New Tab (`move_pane_to_new_tab`, Option+Cmd+T,
Ctrl+Alt+Shift+T elsewhere) gives the focused pane a tab of its own right after its tab, from the Tab menu, the context
menus and the palette. The Editor (0.12.0) has New Editor Tab
(`dev.jasper.editor.new_editor_tab`, was New Editor File) and a split item whose actions are Split Right with Editor (New
Editor Pane, in the File menu after New Editor Tab) and Split Down with Editor; Revert to Saved
(`dev.jasper.editor.revert`) and Reload vimrc (`dev.jasper.editor.reload_vimrc`) are actions too; every file it opens
from outside opens in a new tab; its right-click menu lists Revert to Saved and ends with Split and Move Pane to New Tab;
its Cmd+W command is Close Pane. Rulings (plan, "Rulings"): (1) `NEW_TAB`, with no `KitCompatibility` entry; (2) the
fakes take the two levels; (3) split items and their actions' ids and titles; (4)
plugins reach the Split lists through `splitChoices` and `ContentTab.split`; (5) the lists put the bundled plugins'
items first; (6) one Split submenu everywhere, replacing Split Right and Split Down in the menus; (7) the toolbar's
Split button stays; (8) the palette lists every entry as its action; (9) Move Pane to New Tab and what stays; (10) the
menus' order; (11) `ContentTab.moveToNewTab` and `mayMoveToNewTab`; (12) the Editor's commands; (13) New Editor Pane is
Split Right with Editor; (14) every Editor open uses `NEW_TAB`; (15) the Editor's Close Pane and its guide's keys; (16)
only the Editor's version moves; (17) split items live in `Contributions`; (18) a content tab reaches its window through
the registry; (19) a terminal beside a content tab needs `terminal.open`; (20) Revert to Saved and Reload vimrc are Editor
actions; (21) the Editor's File menu offers New Editor Tab and Split Right with Editor; (22) the Split submenus show no
icons. Spec issues: none. Deviations: <none, or each with its cause>. `./gradlew check --continue`: **<counts>**.
GUI checks for the user:
- the Tab menu's Split submenu and a terminal's right-click Split submenu: Terminal Right (Cmd+D) and Terminal Down
  (Cmd+Shift+D) with their keys, then Editor Right and Editor Down, none with an icon; each opens its pane beside the
  focused one;
- under the Enterprise look the Window menu, which holds the Tab menu's items, shows the same Split submenu;
- an editor's right-click menu: Split opens a terminal or an editor beside that editor, also when another pane had the
  focus; Move Pane to New Tab is disabled while the editor is its tab's only pane; Revert to Saved asks before
  discarding edits;
- Option+Cmd+T (Ctrl+Alt+Shift+T on Linux and Windows) moves the focused pane into a tab right after its own, every
  shell running, and typing then reaches the moved pane; a maximized pane's old tab shows the rest;
- New Editor Tab, Split Right with Editor and Split Down with Editor from the palette; typing "new editor pane" finds
  the split;
- a Cmd-click on a file, `jasper editor FILE`, Open in Editor… and Recent Files each open a new tab, also while an
  editor pane of a split tab has the focus, and while the main window shows its empty workspace the file fills it, as
  the window's one tab, with no empty tab beside it (Review Focus 5);
- the File menu reads Open in Editor…, New Editor Tab, Split Right with Editor, New Editor Window;
- Settings › Keymap lists Move Pane to New Tab with its keys and the Editor's five new actions (New Editor Tab, Split
  Right and Split Down with Editor, Revert to Saved, Reload vimrc) unbound, and binding one works.

Not merged, not pushed.

""", 1)])
PY
```

Set this plan's status banner (line 3) to "**Status: executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`."
and list any deviation from the plan's text there.

- [ ] **Step 3: Check the sources and build everything**

```bash
python3 - <<'PY'
import pathlib
for name in [str(p) for root in ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins") for p in pathlib.Path(root).rglob("*.java")]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
print("checked")
PY
git grep -n -e 'ACTIVE_GROUP' -e '"dev.jasper.editor.new"' -e '"close-tab", "Close Tab"' -- ':!docs/superpowers' ':!docs/STATUS.md' ':!plugins/editor/src/test/java/dev/jasper/editor/ui/EditorTabTest.java'
git grep -n '"Close Tab"' -- plugins/editor jasper-app/src/test/java/dev/jasper/app/plugins ':!plugins/editor/src/test/java/dev/jasper/editor/ui/EditorTabTest.java'
```

Expected: `checked` alone, and both `git grep`s print nothing: only `EditorTabTest`'s own fixture keeps a `close-tab`
command titled "Close Tab", and Jasper's own Close Tab is named in the workspace tests, outside these paths.

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Read the counts from the XML in `*/build/test-results/test/` and put them in the STATUS entry
in place of `<counts>`, with the dates and commit range.

- [ ] **Step 4: Commit**

```bash
git add -A README.md docs jasper-sdk/README.md
git commit -F - <<'EOF'
Tabs and panes: split with a choice, documentation and status

The SDK, plugin-authoring and architecture guides describe NEW_TAB,
split items and a content tab's pane commands; the README, the
palette and configuration guides name the Split submenu and Move Pane
to New Tab; STATUS records plan 8b with its rulings and the full
build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Self-review

Checked against the spec with this plan's text in hand:

- **Spec coverage.** 2.5 and 11 (split with a choice, the SDK): Tasks 3 and 4. 2.7 and 8 (the Editor in a tab or a
  pane, New Editor Tab, New Editor Pane, Split Down with Editor, through its own split item; New Editor Window stays):
  Task 6, Rulings 12 and 13. 2.8 and 8 (opening from outside opens a new editor tab: Cmd-click, `jasper FILE`, Open in
  Editor…, Recent Files): Tasks 1 and 6, Rulings 14, Review Focus 5. 2.9 (a plugin's view as a tab or a pane): Tasks 1
  and 3 (`NEW_TAB`, `SPLIT_*`, the opener's placement). 2.10 and 8 (the Split submenus, their entries and order; the
  Tab menu and the palette): Tasks 5 and 6, Rulings 5, 6 and 8. 2.11 and 9 (Move Pane to New Tab, its entries, its
  default, disabled for a tab's only pane): Tasks 2, 4, 5 and 6. 2.12 and 10 (every action bindable): Tasks 2, 3 and 6's
  keymap tests. 11 (`TabPlacement` renamed, `SPLIT_*` and `Terminals.openTab` meanings, split items, the testkit and the
  contract on both runtimes, the three SDK documents): Tasks 1, 3, 4 and 7. 16 (keymap and collision tests per action,
  the contract on both runtimes): Tasks 2, 3 and 6. Section 7's pane drag is plan 8c's.
- **Type consistency.** `SplitItemSpec.rightAction()` and `downAction()` (Task 3) are the ids `Contributions.SplitItem`,
  the fakes and `WindowChrome.fillSplit` read; `SplitChoice.TERMINAL` (Task 3) is what `ContentTab.split` (Task 4) and
  `PaneItems` (Task 6) compare against; `TabRegistry.splitTerminal`, `mayMoveToNewTab`, `moveToNewTab` (Task 4) call the
  `WorkspaceHost` functions of the same names, which `WindowContent` fills with Task 2's `mayMoveToNewTab` and
  `moveToNewTab`; `FakePluginHost.windowTabs` and `ContractHarness.windowTabs` (Task 1) are what Tasks 3, 4 and 6 read;
  `HostedUi.terminalOpen` (Task 4) is set in `HostedContext` beside Task 3's `bundled`, and the fake checks the same
  capability through `FakeTerminals.require`; `EditorPlugin.REVERT` and `RELOAD_VIMRC` (Task 6) are the ids
  `TabShortcutCollisionTest` and `EditorPluginTest` read.
- **The review's rulings.** Rulings 19 (Task 4), 20 and 21 (Task 6) and 22 (Task 5) each have a test: the contract's
  `aContentTabsTerminalSplitNeedsTerminalOpen`, `EditorPluginTest`'s and `EditorWindowTest`'s cases with
  `TabShortcutCollisionTest.theEditorsNewActionsHaveNoDefaultAndCanBeBound`, and `SplitListsTest` with
  `WindowChromeMenusTest`.
- **Review Focus.** Each line names its test and its task; Review Focus 5's staged Editor in a real main window is a GUI
  check, with plan 8a's test for the application's empty workspace and Task 6's for the fake's.
- **Proportion.** The plan carries complete tests and new files, and every edit as an anchor and its replacement, as
  the brief requires; prose decides only names, order and wording.
