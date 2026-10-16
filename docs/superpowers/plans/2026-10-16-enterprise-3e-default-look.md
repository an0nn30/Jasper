# Enterprise presentation 3e: the default-look changes Implementation Plan

> **Status:** executed 2026-10-16 (`46b9bca82..7de3ee4ec`). Rulings 4 approved on 2026-10-16 under the user's overnight authority: File › Open File… shows Jasper's own file chooser, and spec 11.7 and 13 are amended to say so. Deviations from the text: Task 9: the plan's Rulings 14 linked the Enterprise design relative to the plans folder, which `AppDocumentationTest` refuses; that link now points into `../specs/`. Task 9: `fileserver-add` changed only in its "Reachable from" label (the read-only folder draws as before and that state has no warning), and its note says so. Task 9: `settings-keymap` (the Keymap lists Open File… and Share Folder…), `workspace-rails` and `workspace-integrated` (the Vault status item reads Vault: Locked) changed by design and carry notes too. Task 9: `vault-manager` and `vault-manager-title` differ run to run at the same code (a generated key's fingerprint and a JUnit temp folder, shown by two renders of one tree), so they carry no note and Step 5 skips them. `./gradlew check --continue`: 10,621 tests, 0 failures, 0 errors, 25 skipped.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spec section 13's visible changes, made once and applied in both looks: Remote's SSH menu becomes entries in
the Session and Transfer standard menus, where File Server adds Share Folder… and New File Server…; a new File › Open
File… application action; "Vault: Locked"/"Vault: Unlocked"; the Snippets verb "Insert"; titled leading groups on
Settings pages; File Server's warning as a WARNING Notice and its folder as a read-only field beside a separate
Choose…; plugin `stringList` host rows on the ordered-list editor; Project Index's Location column in the data face;
sentence-case plugin form labels; three recent palette rows; and spec 19.2's status-banner lines for the six
specifications these changes amend. Every action stays reachable.

**Architecture:** Each change lands at its owner and uses the vocabulary plans 3a to 3d already shipped: plugins move
their menu entries with `context.menus().standard(StandardMenu.SESSION | TRANSFER)` (3d Task 1), File Server's editor
builds 3b's `Nodes.Notice` and its 13-argument read-only `Nodes.Field` (3b Tasks 6 and 1), and the app adds one
`ActionId` routed through the window's existing Cmd-click path (`WindowContent.onOpenFile`) with a chooser the
application supplies from `AuxiliaryWindows`. Settings titles a page's leading run of rows in one place
(`SchemaPage.viewSpec`), which covers the core pages and every generated plugin page. Host rows render a
`stringList` exactly as the generated pages' `Editors.textList()` editor does, in the host's `KitSettingRows` and the
testkit's mirror alike. No jasper-ui file changes, so no kit rendering changes.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and
AssertJ 3.27.7, Python 3.11+ standard library for the mechanical edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(section 13 in full; the 12.2 bullet "Untitled leading groups" that section 13 refers to; 1 decisions 15 and 17; 3.5 and
3.9; 8.2 for the Notice and `Field.readOnly`; 11.3's `RECENT_ROWS`; 11.7 and 13 for File › Open File…; 14.4 to 14.7 for
"in both looks" and "every action stays reachable"; 17 for the documents; 19.1 row 3e; 19.2's rows for Snippets, Palette
plugins, Remote, Remote tunnels, SDK clipboard and Tools menu, and Vault). Plans 1 and 2 and plans 3a
(`2026-10-15-enterprise-3a-sdk-0-18-renames.md`), 3b (`2026-10-15-enterprise-3b-sdk-0-18-controls.md`) and 3d
(`2026-10-15-enterprise-3d-sdk-0-18-chrome.md`) are merged on `main` (`149cd8fb9`); plan 3c
(`2026-10-15-enterprise-3c-sdk-0-18-tables.md`) executes in parallel in `.claude/worktrees/enterprise-3c`, and this plan
uses none of its APIs (Execution notes, Ruling E1). The merged plans' rulings bind this plan (3b Rulings 1, 2, 5 and 7;
3d Rulings 9 and 11).
Plans 4 and 5 execute after this one.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-3e`, branch
`claude/enterprise-default-look`, on `main` at `149cd8fb9` (3a, 3b and 3d merged) and holding only commits to this
plan file (it is written, reviewed and re-anchored there, so their number varies). Do not rebase or merge: the base
stays `149cd8fb9` for the pixel baseline and Task 9's ranges, even when `main` moves (plan 3c may merge meanwhile).
Before Task 1, check that the base and 3a, 3b and 3d are in it:

```bash
git merge-base --is-ancestor 149cd8fb9 HEAD && echo "base 149cd8fb9 present"         # expect the line
grep -c 'VERSION = "0.18.0"' jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java      # 3a: expect 1
grep -c 'record Notice(' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java            # 3b: expect 1
grep -c 'boolean readOnly' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java          # 3b: expect at least 1
grep -c 'TRANSFER' jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java           # 3d: expect 1
```

If the first prints nothing or any grep prints 0, stop: this plan needs 3a, 3b and 3d. It needs nothing from 3c (no
`cellNodes` check). Run every command from the worktree root, never in `~/projects/moray`. Line numbers below were
re-verified at `149cd8fb9` (2026-10-16); every edit quotes the
exact text it replaces, and the edit scripts stop with a message when that text is not found the stated number of
times. If a script stops, re-find the quoted text (it moves, it does not change) and re-run; never edit around a
missing anchor, and record any anchor you had to re-quote in the status banner.

Then record the reference pairs' baseline: the render and the `compare_all.py` summary of every pair in
`tools/visual/pairs.json` before this plan changes anything (Global Constraints, pixel rule). The render commands are
the theme-engine acceptance's ("Regenerate" in `docs/design/theme-engine/acceptance.md`); the preview tasks set
`java.awt.headless=true` and open no window. Run from the worktree root:

```bash
./gradlew :jasper-app:themeSurfacePreview :jasper-app:settingsSurfacePreview :jasper-app:settings9bPreview :jasper-app:workspaceSurfacePreview
./gradlew :jasper-app:commandPalettePreview --args="$PWD/jasper-app/build/palette-surfaces --task9c"
./gradlew :jasper-app:test --tests '*StagedAnchoredPickerTest' --tests '*VaultSheetRenderTest' --tests '*StagedAddHostTest' --tests '*StagedFileServerFormTest' --tests '*SessionsCompositionTest'
mkdir -p build/plan-3e-pairs
cat > build/plan-3e-pairs/measure.py <<'PY'
"""Plan 3e: every reference pair's render and edge agreement, before Task 1 ("before") and in Task 9 ("after")."""
import json, pathlib, shutil, subprocess, sys
phase = sys.argv[1] if len(sys.argv) == 2 else ""
if phase not in ("before", "after"): raise SystemExit("usage: measure.py before|after")
root = pathlib.Path(".").resolve()
out = root / "build/plan-3e-pairs" / phase
if out.exists(): shutil.rmtree(out)
(out / "renders").mkdir(parents=True)
pairs = json.loads((root / "tools/visual/pairs.json").read_text(encoding="utf-8"))
missing = [pair["name"] for pair in pairs if not (root / pair["render"]).exists()]
if missing: raise SystemExit(f"renders missing for {missing}; run the render commands first")
for render in sorted({pair["render"] for pair in pairs}):
    target = out / "renders" / render; target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(root / render, target)
result = subprocess.run([sys.executable, "-I", "tools/visual/compare_all.py", str(out / "compare")],
                        capture_output=True, text=True, check=True)
summaries = {}
for line in result.stdout.splitlines():
    name, _, summary = line.partition(": ")
    summaries[name] = summary.split(" (note: ")[0]
(out / "summaries.json").write_text(json.dumps(summaries, indent=2) + "\n", encoding="utf-8")
print(f"{phase}: {len(summaries)} pairs measured")
if phase == "after":
    from PIL import Image, ImageChops
    before_dir = root / "build/plan-3e-pairs/before"
    before = json.loads((before_dir / "summaries.json").read_text(encoding="utf-8"))
    changes = {}
    for pair in pairs:
        name, render = pair["name"], pair["render"]
        old, new = before_dir / "renders" / render, out / "renders" / render
        changed, where = False, ""
        if old.read_bytes() != new.read_bytes():  # a pair changes when a pixel does (RGB; alpha alone hides it), not its bytes
            with Image.open(old) as a, Image.open(new) as b:
                box = None if a.size != b.size else ImageChops.difference(a.convert("RGB"), b.convert("RGB")).getbbox()
                changed = a.size != b.size or box is not None
                where = f"size {a.size} -> {b.size}" if a.size != b.size else f"pixels differ in {box}" if box else ""
        changes[name] = {"changed": changed, "where": where, "before": before.get(name, ""), "after": summaries.get(name, "")}
        print(f"{name}: {'CHANGED (' + where + ')' if changed else 'same'}; {changes[name]['before']} -> {changes[name]['after']}")
    (out / "changes.json").write_text(json.dumps(changes, indent=2) + "\n", encoding="utf-8")
PY
python3 -I build/plan-3e-pairs/measure.py before
```

Expected: `before: N pairs measured`, N the number of entries in `pairs.json` (34 at `149cd8fb9`). `build/` is
ignored by git; Task 9 Step 4 runs the same script with `after` (if `build/plan-3e-pairs/measure.py` is gone by then,
write it again from this block, but never re-run `before`).

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md); `packageDist`, `trainAotCache` and `-Pjasper.aot=true` are off limits. `:jasper-ui:gallery` is headless (`java.awt.headless=true`) and may be run, as may the render tasks of the baseline and Task 9 (`themeSurfacePreview`, `settingsSurfacePreview`, `settings9bPreview`, `workspaceSurfacePreview`, `commandPalettePreview`), which set it too.
- Work only in the worktree above on `claude/enterprise-default-look`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Scope is spec section 13 and the 12.2 bullet it names, both looks, with spec 19.2's banner lines for Snippets, Palette plugins, Remote, Remote tunnels, SDK clipboard and Tools menu, and Vault. TFTP's login and a single offered protocol are already read-only fields (3b Task 3, Rulings 1): do not touch them. No SDK type, no jasper-ui file and no `Composition` branch changes here (the classic compositions are plans 9 and 10).
- "Every action stays reachable" (spec 14.7): every entry of Remote's former SSH menu is in the Session or Transfer menu, File › Open File… is also a palette command, and nothing loses its shortcut. The Editor plugin's Open in Editor… keeps `cmd+o`; File › Open File… has no default binding on any platform.
- The copy is exactly spec 13's: "Connect to SSH Host…" and the other menu entries keep their labels; "Share Folder…"; "Open File…" with the description "Open a file with the default editor"; "Vault: Locked", "Vault: Unlocked"; "Insert"; "General"; "No folder selected", "Choose…"; "Via host", "Jump host", "Reachable from", "Listen on", "Forward to".
- Pixel rule: no file under `jasper-ui/` changes, so every file the headless gallery writes for Slate Cobalt Dark and Light is byte-identical to the base (Task 9 proves the inputs unchanged). The reference pairs in `tools/visual/pairs.json` are measured, not assumed: every pair's render and `compare_all.py` summary is recorded before Task 1 (Workspace) and again in Task 9 Step 4. Section 13 is expected to change `fileserver-add` (the "Reachable from" label; the folder field drawn read-only), `host-add` ("Jump host"), `tunnel-new` ("Via host", "Listen on", "Forward to") and `workspace-palette` (a third recent row, so Most used starts one row lower). Every pair whose render changes keeps its mock and crop and gains a `note` naming the change and its measured edge agreement before and after (Task 9 Step 5). Another pair may change only through one of the by-design surfaces listed next (the workspace renders draw the Vault status item, for example); it then gets a note too and a line among the deviations. Any other change stops the executor. Other default-look surfaces that change by design and have no pair: the menu bar (Session and Transfer instead of SSH; File gains Open File…), the Vault status item's text, the New File Server editor's FTP and writable states (the Notice), the Settings pages Command Palette, Desk Buddy, System Settings, Terminal, Cursor, Shell and generated plugin pages (a heading over their first card), and Project Index's page (ordered lists, the mono Location column). Everything else renders as before.
- MVP rule (memory: no compat shims): `RemotePlugin.MENU` and the SSH top-level menu are removed outright, as are the host rows' text-area handling of `stringList` values; no alias, no migration.
- SDK types appear in the app only inside `dev.jasper.app.plugins` (AGENTS.md); `dev.jasper.app.application` must not depend on `dev.jasper.app.files`, so the Open File chooser lives in `dev.jasper.app.windows` (`AuxiliaryWindows`). `verifyApplicationArchitecture` and `verifyPluginArchitecture` stay green.
- Javadoc doclint fails the build on errors: the SDK and jasper-app run `-Xdoclint:all` and the testkit's javadoc the JDK's default doclint; every `{@link}` must resolve and no comment may contain a raw `<`, `>`, `&` or `->` (write "item-N" rather than an angle-bracketed placeholder).
- `ThemeMigrationCoverageTest`: this plan creates no source file. Every file it edits already has a `docs/design/theme-engine/migration.csv` row except `ActionId.java` and `SnippetsScopeTest.java`, which are not candidates and must stay so: add to them no Swing, AWT, FlatLaf or `dev.jasper.ui`/`dev.jasper.sdk.ui` import, no `new J…`, and no `new …List`, `new …Panel` and the like (use `List.of`). The test runs in every task's Step 4.
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten, except the six status banners spec 19.2 names.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file. The ellipsis, middle dot, em dash and › used in copy are ordinary characters, as the existing sources use them.

## Review Focus

1. A person adds a value a plugin's `stringList` rule refuses (Project Index's excluded folders refuse a name with a
   `/`): nothing is staged, the row shows the rule's message, the entry keeps the text to correct, Apply is refused, and
   a corrected Add clears the error. Task 7: `FakeKitMountingTest.aStringListRowIsTheOrderedListEditor` and
   `PluginPanelPageTest.aStringListHostRowIsTheGeneratedPagesOrderedList`.
2. An ordered list's rows change underneath an event (a stale `item-9`, a move to a position past the end): nothing
   moves, nothing throws, no error shows. Task 7: the same two tests.
3. File › Open File… cancelled: nothing opens and no error shows; the action is enabled, needs no shortcut and is also a
   palette command (`open_file`, found by "open file"). Task 2:
   `ApplicationActionsTest.openFileAsksTheChooserAndOpensTheChosenFileAsACmdClickDoes`.
4. Editing a saved server: its folder shows read-only, a cancelled Choose… keeps it, and typing into the field changes
   nothing (in the real host too, where the input is not editable). Task 5:
   `ServerCompositionTest.explicitBrowseCancellationKeepsTheChosenFolderAndPerformsNoImplicitChooserRead`,
   `ServerEditorTest.theFolderIsAReadOnlyFieldThatOnlyChooseChanges` and `StagedFileServerFormTest`.
5. A Settings page whose leading rows are all hidden on this platform (a row `shownWhen(false)` before the first
   section): no orphan page-name heading over an empty card. Task 4:
   `SchemaPageTest.aPagesLeadingUntitledRowsTakeThePagesName`.

## Rulings

Decisions where the spec is silent, each as "what — why — cost if wrong". Record them in `docs/STATUS.md` (Task 9).
Ruling 4's chooser is not gap-filling: it interprets binding text (spec 11.7 and 13 say "the native file chooser" and
"opens the native chooser"). It needs the user's confirmation, or a spec amendment line, before this plan merges; the
STATUS entry and the status banner record it as a deviation awaiting that ruling.

1. **The reference pairs are `tools/visual/pairs.json`'s entries whose renders this plan changes, as measured** against
   the baseline taken before Task 1 (expected: `fileserver-add`, `host-add`, `tunnel-new`, `workspace-palette`); each
   keeps its theme-engine mock and crop and gains a `note` naming the intended difference and its edge agreement before
   and after, which `compare_all.py` prints — the default look's references stay the theme-engine mocks
   (the Enterprise mocks show the classic look and are paired by plans 5 to 10, spec 16.1.4) — cost if wrong: a
   reviewer wanted new crops or new mocks; the notes say where to look.
2. **Session and Transfer contents and order.** Session: Connect to SSH Host…, Toggle SSH hosts, Split with Same Host,
   a separator, New Host…, Import from ~/.ssh/config…, then the tunnels group (its own separator, Toggle Tunnels, Start
   or Stop a Tunnel…, New Tunnel…, Forward a Port…, Stop All Tunnels). Transfer: Open SFTP here and Transfers, then File
   Server's section (Share Folder…, New File Server…) in plugin start order — the former SSH menu's order, split where
   spec 13 splits it — cost: a reorder later.
3. **Share Folder… runs exactly what the empty File Servers panel's button runs today** (the New File Server editor for
   a new server, `FileServersPanel.java:138`), so it does what New File Server… does; id
   `dev.jasper.fileserver.share-folder`, the server icon, no default binding — spec 13 names that behaviour — cost: two
   entries with one effect, until a later plan makes Share Folder… pick the folder first.
4. **File › Open File…** is `ActionId.OPEN_FILE` (`open_file`, "Open File…", `none` on both platforms, Global), placed
   after New Window in File and offered as a palette command; its description is the action's tooltip.
   **Deviation (spec 11.7 and 13, awaiting the user's ruling): "the native chooser" is read as Jasper's own chooser**,
   the one its host shows for plugins (`AuxiliaryWindows.choose`, `NativeShells.choosePaths`, documented as "Jasper's
   own chooser"), not a platform file dialog: one existing local file, titled "Open File", starting in the active pane's
   folder. Every other file pick in the app uses it, and the app has no native file dialog. Before plugins start (no
   auxiliary windows yet) the action opens nothing — cost if the user wants the platform dialog: swapping in an AWT
   `FileDialog` in `AuxiliaryWindows.openFile` is one method, and the tests that script the chooser stay as they are.
5. **Vault with no vault file keeps "Vault"**: it is neither locked nor unlocked, and the tooltip already says "No
   vault — click to create one"; the padlock icon stays — cost: one string.
6. **Snippets' first verb keeps its id `paste`** (palette history and the `paste_run` sibling key on it) and the
   scope's description "Paste or run a saved command" — spec 13 changes the label only — cost: one string.
7. **One rule titles leading groups**: `SchemaPage` heads a page's first run of shown rows with the page's title when
   no section heads it, which covers the core pages and every generated plugin page (the Vault's "Credential Vault"
   included); System Settings gets an explicit "General" section. Plugin-built pages (`PluginPanelPage`) are the
   plugin's own composition and are not retitled — cost: a plugin page author may want the same rule.
8. **One WARNING Notice** (`server.warning`) carries every warning, joined by " · " as today's line was, present only
   while there is a warning, in the line's place after Security & Startup; the presentation adds "Warning" (3b Rulings
   5) — a Notice is one sentence and today's line already joined them — cost: one Notice per warning later.
9. **The folder** is a read-only PATH field (hint "No folder selected") with the existing Choose… button as its row's
   trailing control; the editor also ignores an `Edited` for it. Tests set folders through the chooser: the fake's
   `queuePathSelection`, the editor's injected supplier, and a scripted chooser in the staged host — cost: none known.
10. **Sentence case covers exactly the five labels spec 13 lists** (they are every multi-word Title Case plugin form
    label, found by scanning every `FormRow` and form-row helper in `plugins/`), with their choices' accessible labels;
    jasper-ui's gallery and test fixtures that copy them ("Jump Host", "Reachable From", "Listen On" in `FormFixtures`,
    `GroupedFormGeometryTest`, `PanelSurfacesTest`) are not plugin forms and keep their text so the gallery stays
    byte-identical — cost: the fixtures read Title Case until plan 4 touches them.
11. **The host row's ordered list is the generated pages' `Editors.textList()` node**, id for id (`<id>:editor`,
    `<id>:entry`, `<id>:new`, `<id>:add`, rows `item-N`), proven equal by test; an `Edited` of the list's own id no
    longer stages anything in the host or the fake; a rejected Add keeps its entry and shows the row error; a stale
    Removed or Reordered is ignored — cost: none known.
12. **Location takes `MONO_SMALL`**, the data face the same table's Files column already uses — cost: one constant if
    `MONO` (body size) was meant.
13. **`RECENT_ROWS` becomes 3 and `SUGGESTED_ROWS` stays 10**, the empty query's total, so Most used shows one row
    fewer — spec 11.3 changes the recent count only — cost: one constant.
14. **Banner placement**: each 19.2 line is a new paragraph right after the specification's status paragraph, worded
    exactly "Amended on 2026-10-15 by the [Enterprise presentation design](../specs/2026-10-15-jasper-enterprise-presentation-design.md):
    <what>; everything else in this document stands." — cost: none.

## Verified facts

Read at `222969926` and re-verified at `149cd8fb9` (2026-10-16, with 3a, 3b and 3d merged); line numbers are
`149cd8fb9`'s.

1. Remote builds a top-level "SSH" menu: `PluginMenu menu = context.menus().create(MENU, "SSH")` with Connect, Hosts,
   Split, a separator, New Host, Import, a separator, `SFTP`, `TransferUi.SHOW`, then `tunnelsUi.addToMenu(menu)`
   (`plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java:216-219`); `MENU = "dev.jasper.remote.menu"`
   (`:72`); `SFTP = "dev.jasper.remote.sftp"` (`:88`); the class Javadoc names "the SSH menu" (`:67`).
2. `TunnelsUi.addToMenu` adds a separator, then TOGGLE, PICK, NEW, FORWARD and STOP_ALL (`TunnelsUi.java:131-138`);
   the class Javadoc names "the SSH menu group" (`:48`) and the method "The SSH menu's tunnels group" (`:130`).
   `TransferUi.SHOW = "dev.jasper.remote.transfers"` (`TransferUi.java:14`).
3. 3d Task 1 adds `StandardMenu.SESSION` and `TRANSFER`, mapped by name to `MenuTarget.Slot.SESSION`/`TRANSFER`, built
   fresh each pass and shown after Tab, before Tools, only while they have entries (3d Rulings 9); the fake keys their
   sections `"SESSION"` and `"TRANSFER"` (3d's `theSessionAndTransferMenusTakeSectionsLikeTools`, in the contract suite,
   `PluginContractTest.java:643-658`). `Menus.standard`
   appends "a new section at the end of a built-in menu, after a separator" (`Menus.java:5-11`); the fake renders a
   separator as `---` and plugin sections apart with `===` (`FakePluginHost.java:590-601`, `FakeUi.java:84-92`; the fake lists an item only while its action exists).
4. Tests naming the SSH menu: `RemotePluginTest.java:87,298`, `TunnelsPluginTest.java:87`, `SftpPluginTest.java:25`
   and the staged `RemoteShortcutConfigTest.java:79-82` (`menu.target().key()` equal to `"dev.jasper.remote.menu"`).
5. File Server registers `NEW` ("New File Server…", `FileServerPlugin.java:153-155`) and no menu entry for it; the
   empty File Servers panel's "Share Folder…" button runs `actions.edit().accept(Optional.empty())`
   (`FileServersPanel.java:138,213-214`), which `createPanel` maps to `edit(window, Optional.empty(), false)`
   (`FileServerPlugin.java:246-247`), the same call NEW makes.
6. `ActionId` is the built-in catalog, each with a macOS and an other-platform default and a context
   (`ActionId.java:10-52`); `NEW_WINDOW` is at `:13`. `KeyBindingsTest.actionCatalogCoversEveryPhaseOneActionAndUsesStableIds`
   lists the catalog in order (`KeyBindingsTest.java:18-31`) and `KeymapDefaultsTest` needs a row per action
   (`KeymapDefaultsTest.java:21-75`).
7. `WorkspaceActions` builds one `Action` per `ActionId` (`WorkspaceActions.java:18-24`), sets tooltips on Settings and
   Reload (`:26-27`), dispatches in `invoke` (`:33-90`, NEW_WINDOW at `:51`) and enables in `update`, where COMMAND_PALETTE,
   NEW_TAB, NEW_WINDOW, QUIT and TOGGLE_ZEN_MODE are always on (`:119`). `WindowChrome` builds File from NEW_TAB,
   NEW_WINDOW, CLOSE_TAB, CLOSE_PANE, OPEN_SETTINGS, RELOAD_CONFIG, QUIT (`WindowChrome.java:75-76`; 3d's Session and Transfer menus are built at `:330-331`). `WindowCommands`
   registers every ActionId but the palette as a palette command with keywords (`WindowCommands.java:39-59`).
8. A Cmd-click reaches `WindowContent.onOpenFile` (`WindowContent.java:163`, `:1005-1015`), which the application sets to
   `openFileAt(file, line, windowId)` (`JasperApplication.java:420`), routing an existing regular file to the default
   editor (`:534-551`); `Rehearsal.silence` and `WindowContent.close` reset it (`Rehearsal.java:145`,
   `WindowContent.java:1435`). `WindowContent.directory()` is the active pane's inheritable folder (`:898`).
9. The application's auxiliary windows exist once plugins start: `auxiliary = new AuxiliaryWindows(uiState,
   shells::create, shells::choosePaths)` (`JasperApplication.java:849`). `AuxiliaryWindows.choose(PathChoice, …)`
   delegates to its `PathChooser` (`AuxiliaryWindows.java:28-33`); `PathChoice` takes a terminal owner, a title, a mode,
   `multiple`, a terminal folder and sources (`PathChoice.java:15-27`); `NativeShells.choosePaths` shows Jasper's file
   chooser document-modally over the terminal window (`NativeShells.java:378-391`). `application`'s allowed edges
   include `windows` but not `files` (`application/package-info.java:3`).
10. Vault sets its status text once to "Vault" (`VaultPlugin.java:160`) and `refreshStatus` sets the icon, tooltip and
    action by `LockState` (`:349-355`); `LockState` is `NO_VAULT, LOCKED, UNLOCKED` (`LockState.java:4`);
    `VaultPluginTest.java:248,254,259` pin the status lines; `BundledSamplePluginTest.java:61` expects "Vault" in a
    fresh home (no vault).
11. Snippets' verbs are `PASTE = new PaletteVerb("paste", "Paste")`, `PASTE_RUN` ("Paste and run") and `EDIT` ("Edit
    file") (`SnippetsScope.java:24-26`); 3d Task 6 edits only its `ScopeSpec` line.
12. `SchemaPage.viewSpec` groups items into cards: a `SectionItem` closes the run and starts a titled one, a full-span
    row stands alone, `close` adds nothing for an untitled empty run and an untitled card otherwise
    (`SchemaPage.java:294-332`); 3b's `NoticeItem` joins the current run like an action row (`:319`); `sectionShown` hides
    a section whose rows are all hidden (`:242-251`). The pages with
    untitled leading rows are Command Palette, Desk Buddy, System Settings, Terminal, Cursor and Shell
    (`CorePages.java:138-172,184-185`); generated plugin pages emit a section only when a declaration names one
    (`PluginSettingsPage.java:55-66`). `PageKit.sectionTitles` reads the mounted `section-N.title` labels
    (`PageKit.java:46-56`).
13. New File Server shows its warnings as one DESCRIPTION line "Warning · a · b", blank when there are none
    (`ServerEditor.java:458`), from `ServerRows.warnings` (`ServerRows.java:67-75`); its folder is an editable PATH
    field with the hint "No folder selected" and a separate Choose… trailing button (`ServerEditor.java:404-406`), and
    an `Edited` of FOLDER sets the draft (`:170`); 3b's read-only helper already builds the 13-argument Field (`:376`). Folders are typed in `ServerEditorTest.java:53,81`,
    `ServerCompositionTest.java:56,83`, `ServerLifetimeTest.java:59`, `FileServerPluginTest.java:276`,
    `StagedFileServerFormTest.java:93,97,98`, `SurfaceFixtures.java:25` and `FileServerRenderEvidenceTest.java:98`
    (`((JTextComponent) StagedVaultHost.named(editor.holder(), "server.folder.input")).setText(home.toString())`, then
    Save and `await(editor::closed, "server saved")`, over `AppContractTest::headlessWindows` at `:54`, whose chooser
    answers nothing).
14. 3b: `Nodes.Notice(String id, ControlState state, Severity severity, String text)` is allowed as a form section, its
    text a non-blank sentence the presentation prefixes with the severity's word (3b Task 6, Rulings 2 and 5;
    `Nodes.java:701-720`, whose four-argument constructor gives no actions); the
    13-argument `Nodes.Field(…, boolean readOnly)` is focusable and never edited, and the fake drops an `Edited` sent to
    a read-only field (3b Task 1, Rulings 7).
15. The staged host's auxiliary windows choose nothing: `AppContractTest.headlessWindows(UiState)` builds
    `AuxiliaryWindows(state, shells)`, whose chooser returns an empty list (`AppContractTest.java:106-113`,
    `AuxiliaryWindows.java:18-20`); `StagedVaultHost` uses it (`StagedVaultHost.java:53`); `HostedUi.chooseDirectory`
    goes through `windows.choose` for a live terminal owner (`HostedUi.java:583-640,722-724`). `Location.local(Path)`
    and `localPath()` exist (`Location.java:22,28`).
16. Plugin form labels in Title Case: "Reachable From" (`ServerEditor.java:407,410`), "Jump Host" (`HostEditor.java:358`,
    twice), "Via Host" (`TunnelEditor.java:269`, twice), "Listen On" (`:280`) and "Forward To" (`:283`); tests pin them
    at `TunnelEditorTest.java:81-82` and `TunnelsCompositionTest.java:110,117`. A scan of every `FormRow` and form-row
    helper in `plugins/` finds no other multi-word Title Case label.
17. Host rows (`KitSettingRows`, app) render a `stringList` as a TEXTAREA joined by newlines and stage it from an
    `Edited` split on newlines (`KitSettingRows.java:41-53,117`); the testkit's mirror does the same
    (`jasper-sdk-testkit/.../KitSettingRows.java:33-46`, `FakeSetting.java:105`) and its `FakeSettingsPanelHost.event`
    routes owned row events (`FakeSettingsPanelHost.java:87-124`). The generated pages' editor for a `stringList` is
    `Editors.textList()`'s `Items`: an ORDERED `ItemList` of `item-N` rows over an INLINE entry Field `<id>:new` ("Add…",
    "Add to <label>") and an Add button `<id>:add`, handling `Edited`, `Action`, `Removed` and `Reordered`
    (`Editors.java:180-182,395-448`). Plugin panels get host rows through `SettingsPanelHost.row`; Project Index's
    markers, ignored paths and excludes are `stringList` host rows (`IndexerSettingsPanel.java:121-122`,
    `IndexerSettings.java:41-51`). `PluginPanelPage` re-renders after a handled host event and reads validity from the
    rows (`PluginPanelPage.java:96-97,122`).
18. Project Index's Location column is BODY and its Files column MONO_SMALL (`IndexerSettingsPanel.java:139-143`).
19. `PaletteController.RECENT_ROWS = 2, SUGGESTED_ROWS = 10` (`PaletteController.java:21`); the empty query shows the
    last `RECENT_ROWS` distinct rows run, then ranked ones up to `SUGGESTED_ROWS` (`:183-215`);
    `PaletteScopesTest.java:106-131` and `CommandPalettePreview.java:248` assume two recents.
20. `tools/visual/pairs.json` round-trips byte for byte through `json.dumps(…, indent=2) + "\n"`, and
    `compare_all.py` reads `name`, `mock`, `crop`, `render` and `render_crop` (`compare_all.py:6-7,23-37`).
21. The spec banners to amend: Snippets (`2026-09-15-jasper-snippets-design.md:3`), Palette plugins
    (`2026-09-21-jasper-palette-plugins-design.md:3-4`), Remote (`2026-09-22-jasper-remote-design.md:3-5`), Remote tunnels
    (`2026-09-30-jasper-remote-tunnels-design.md:3-6`), SDK clipboard and Tools menu
    (`2026-10-13-sdk-clipboard-and-tools-menu-design.md:3-4`), Vault (`2026-09-22-jasper-vault-design.md:7-9`).
22. Documents naming what changes: `docs/remote.md:42-43,171,234,288,299,301,307`, `docs/file-server.md:14`,
    `docs/credential-vault.md:43,96`, `docs/command-palette.md:10,39,157`, `docs/editor.md:9-10`,
    `docs/plugin-authoring.md:623-626`, `docs/plugin-ui.md:178`.
23. Every file this plan edits has a `migration.csv` row except `ActionId.java` and `SnippetsScopeTest.java`, which are
    not candidates (checked against `ThemeMigrationCoverageTest.CANDIDATE`, `ThemeMigrationCoverageTest.java:17-18`).
24. Dry run (2026-10-16, while writing this plan): every edit script of Tasks 1 to 9 applied cleanly, in order, to a copy
    of plan 3a's executed branch (`deafed424`). With the Task 1 and Task 5 production edits held back (they need 3d's
    `StandardMenu.SESSION` and 3b's Notice and read-only field), jasper-app, the testkit and the plugins compiled, and
    the full jasper-app and testkit suites passed but for the assertions that need those edits; the staged File Server
    form chose its folders through the scripted chooser and saved. 3b to 3d come in between, so the executor's runs are
    the evidence. Re-anchor dry run (2026-10-16, at `149cd8fb9`, no Gradle): every edit script of Tasks 1 to 8 and of
    Task 9 Steps 1 to 3 applied cleanly, in order, to an archive copy of the branch, and the verification greps of Task 1
    Step 4, Task 5 Step 4, Task 6 Step 4 and Task 7 Step 4 gave their expected results on that copy.

## File structure

- Remote: `RemotePlugin.java` (the two standard-menu sections), `ui/tunnels/TunnelsUi.java` (Javadoc),
  `ui/HostEditor.java` and `ui/tunnels/TunnelEditor.java` (labels).
- File Server: `FileServerPlugin.java` (Share Folder… and the Transfer section), `ui/ServerEditor.java` (Notice,
  read-only folder, label).
- Vault: `VaultPlugin.java` (status text). Snippets: `SnippetsScope.java` (verb label). Project Index:
  `ui/IndexerSettingsPanel.java` (Location face).
- App: `commands/ActionId.java`, `workspace/WorkspaceActions.java`, `workspace/WindowContent.java`,
  `workspace/WindowChrome.java`, `workspace/WindowCommands.java`, `windows/AuxiliaryWindows.java`,
  `application/JasperApplication.java`, `application/Rehearsal.java` (Open File…); `settingsui/SchemaPage.java`,
  `settingsui/CorePages.java` (leading titles); `plugins/KitSettingRows.java` (ordered lists);
  `palette/PaletteController.java` (three recents).
- Testkit: `KitSettingRows.java`, `FakeSettingsPanelHost.java` (ordered lists).
- Tests: the plugin and app tests named in each task; staged-host support in `AppContractTest.java`,
  `StagedVaultHost.java`, `KitViewTestAccess.java`, `SurfaceFixtures.java`.
- Documentation (Task 9): the guides of fact 22, the six spec banners of fact 21, `tools/visual/pairs.json`,
  `compare_all.py`, `tools/visual/README.md`, `docs/STATUS.md`, this plan's banner.

---

### Task 1: The Session and Transfer menus

Remote's top-level SSH menu goes; its entries move, in their order, into the Session menu (connection, hosts and the
tunnels group) and the Transfer menu (Open SFTP here, Transfers). File Server gains Share Folder… and puts it and New
File Server… in Transfer (Rulings 2 and 3). The menus themselves are 3d's.

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java:65-72,216-219`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java:48,130`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java:81,153-156`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java:83-88,297` (one new test),
  `plugins/remote/src/test/java/dev/jasper/remote/TunnelsPluginTest.java:86`,
  `plugins/remote/src/test/java/dev/jasper/remote/SftpPluginTest.java:25`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/RemoteShortcutConfigTest.java:80`,
  `plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java` (one new test)

**Interfaces:**
- Consumes: 3d Task 1's `StandardMenu.SESSION`, `StandardMenu.TRANSFER` and the fake's `"SESSION"`/`"TRANSFER"` menu
  targets.
- Produces: `public static final String FileServerPlugin.SHARE_FOLDER = "dev.jasper.fileserver.share-folder"`;
  `RemotePlugin.MENU` is removed.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 1 step 1: the Session and Transfer menus' tests."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java", [
    (r'''    @Test void newHostOpensAddHostWithoutTheSessionsPanelAndSitsInTheSshMenu(@TempDir Path dir) throws Exception {
''', r'''    /** Spec 13: every entry of the former SSH menu, in its order, in the Session and Transfer standard menus. */
    @Test void theSessionAndTransferMenusHoldEveryEntryTheSshMenuHad(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                host.start(INFO, Set.of(), Set.of(), plugin(dir.resolve("ssh")));
                settle(host);
                assertThat(host.failures()).isEmpty();
                assertThat(host.menu("SESSION")).containsExactly("item:" + RemotePlugin.CONNECT, "item:" + RemotePlugin.HOSTS,
                    "item:" + RemotePlugin.SPLIT, "---", "item:" + RemotePlugin.NEW_HOST, "item:" + RemotePlugin.IMPORT, "---",
                    "item:" + dev.jasper.remote.ui.tunnels.TunnelsUi.TOGGLE, "item:" + dev.jasper.remote.ui.tunnels.TunnelsUi.PICK,
                    "item:" + dev.jasper.remote.ui.tunnels.TunnelsUi.NEW, "item:" + dev.jasper.remote.ui.tunnels.TunnelsUi.FORWARD,
                    "item:" + dev.jasper.remote.ui.tunnels.TunnelsUi.STOP_ALL);
                assertThat(host.menu("TRANSFER")).containsExactly("item:" + RemotePlugin.SFTP,
                    "item:" + dev.jasper.remote.ui.transfers.TransferUi.SHOW);
                assertThat(host.menu("top:dev.jasper.remote.menu")).as("no SSH menu any more").isEmpty();
            }
        });
    }

    @Test void newHostOpensAddHostWithoutTheSessionsPanelAndSitsInTheSessionMenu(@TempDir Path dir) throws Exception {
''', 1),
    ('                assertThat(host.menu("top:" + RemotePlugin.MENU)).containsSubsequence("item:" + RemotePlugin.NEW_HOST, "item:" + RemotePlugin.IMPORT);',
     '                assertThat(host.menu("SESSION")).containsSubsequence("item:" + RemotePlugin.NEW_HOST, "item:" + RemotePlugin.IMPORT);', 1),
    ('            assertThat(host.menu("top:dev.jasper.remote.menu")).isNotEmpty();',
     '            assertThat(host.menu("SESSION")).isNotEmpty();', 1)])
edit("plugins/remote/src/test/java/dev/jasper/remote/TunnelsPluginTest.java", [
    ('            assertThat(host.menu("top:" + RemotePlugin.MENU)).containsSubsequence("item:" + TunnelsUi.TOGGLE,',
     '            assertThat(host.menu("SESSION")).containsSubsequence("item:" + TunnelsUi.TOGGLE,', 1)])
edit("plugins/remote/src/test/java/dev/jasper/remote/SftpPluginTest.java", [
    ('assertThat(host.menu("top:dev.jasper.remote.menu")).contains("item:dev.jasper.remote.sftp","item:dev.jasper.remote.transfers");',
     'assertThat(host.menu("TRANSFER")).containsExactly("item:dev.jasper.remote.sftp","item:dev.jasper.remote.transfers");', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/RemoteShortcutConfigTest.java", [
    ('                assertThat(menu.target().key()).isEqualTo("dev.jasper.remote.menu");',
     '                assertThat(menu.target()).as("spec 13: the Session menu").isEqualTo(MenuTarget.standard(MenuTarget.Slot.SESSION));', 1)])
edit("plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java", [
    (r'''    @Test void keyFilesSkipsLargeFilesAndFolders() throws Exception {
''', r'''    /** Spec 13: Share Folder… and New File Server… sit in the Transfer menu, and Share Folder… opens New File Server. */
    @Test void theTransferMenuSharesAFolderAndAddsAFileServer() {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            assertThat(host.menu("TRANSFER")).containsExactly("item:" + FileServerPlugin.SHARE_FOLDER, "item:" + FileServerPlugin.NEW);
            assertThat(host.actions()).contains(FileServerPlugin.SHARE_FOLDER + "|Share Folder…|true");
            UUID window = host.addTerminalWindow();
            UiTestAccess.edt(() -> assertThat(host.invoke(FileServerPlugin.SHARE_FOLDER, window, null)).isTrue());
            assertThat(host.windows()).containsExactly("dialog|New File Server|true");
        }
    }

    @Test void keyFilesSkipsLargeFilesAndFolders() throws Exception {
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.RemotePluginTest --tests dev.jasper.remote.TunnelsPluginTest --tests dev.jasper.remote.SftpPluginTest :jasper-plugin-fileserver:test --tests dev.jasper.fileserver.FileServerPluginTest :jasper-app:test --tests dev.jasper.app.plugins.RemoteShortcutConfigTest --continue`
Expected: FAIL. `:jasper-plugin-fileserver:compileTestJava` reports "cannot find symbol … SHARE_FOLDER". The Remote
tests fail on the empty `SESSION` and `TRANSFER` sections ("Expecting actual: [] to contain exactly …" and "Expecting
actual not to be empty"), and `RemoteShortcutConfigTest` finds no Remote section targeting the Session menu.

- [ ] **Step 3: Move the entries**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 1 step 3: Remote's entries in Session and Transfer; File Server's Share Folder… and its Transfer section."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java", [
    (" * provider, the Sessions panel (saved hosts and WSL distributions), the scope, the actions, the SSH menu, the status item and the dialogs.",
     " * provider, the Sessions panel (saved hosts and WSL distributions), the scope, the actions, its Session and Transfer menu entries, the status item and the dialogs.", 1),
    ('STATUS = "dev.jasper.remote.status", MENU = "dev.jasper.remote.menu", PANEL = "dev.jasper.remote.panel";',
     'STATUS = "dev.jasper.remote.status", PANEL = "dev.jasper.remote.panel";', 1),
    (r'''        PluginMenu menu = context.menus().create(MENU, "SSH");
        menu.add(CONNECT); menu.add(HOSTS); menu.add(SPLIT); menu.addSeparator(); menu.add(NEW_HOST); menu.add(IMPORT);
        menu.addSeparator();menu.add(SFTP);menu.add(dev.jasper.remote.ui.transfers.TransferUi.SHOW);
        tunnelsUi.addToMenu(menu);
''', r'''        // Spec 13: Remote's entries live in the Session and Transfer standard menus, which show only while they have entries.
        PluginMenu session = context.menus().standard(dev.jasper.sdk.ui.StandardMenu.SESSION);
        session.add(CONNECT); session.add(HOSTS); session.add(SPLIT); session.addSeparator(); session.add(NEW_HOST); session.add(IMPORT);
        tunnelsUi.addToMenu(session);
        PluginMenu transfer = context.menus().standard(dev.jasper.sdk.ui.StandardMenu.TRANSFER);
        transfer.add(SFTP); transfer.add(dev.jasper.remote.ui.transfers.TransferUi.SHOW);
''', 1)])
edit("plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java", [
    (" * Registers the tunnels surface — the Tunnels panel, its actions, the SSH menu group, Forward a Port on the terminal",
     " * Registers the tunnels surface — the Tunnels panel, its actions, the Session menu's group, Forward a Port on the terminal", 1),
    ("    /** The SSH menu's tunnels group. */", "    /** The Session menu's tunnels group, after a separator. */", 1)])
edit("plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java", [
    ('    public static final String NEW = ID + ".new";\n',
     '    public static final String NEW = ID + ".new";\n    public static final String SHARE_FOLDER = ID + ".share-folder";\n', 1),
    (r'''            invoked -> edit(invoked.window(), Optional.empty(), false));
        stopAll = context.actions().register(''', r'''            invoked -> edit(invoked.window(), Optional.empty(), false));
        // Spec 13: Share Folder… runs what the empty File Servers panel's button runs; it and New File Server… sit in Transfer.
        context.actions().register(ActionSpec.of(SHARE_FOLDER, "Share Folder…").withIcon(icon)
                .withKeywords(List.of("file server", "share", "folder", "serve")),
            invoked -> edit(invoked.window(), Optional.empty(), false));
        var transfer = context.menus().standard(dev.jasper.sdk.ui.StandardMenu.TRANSFER);
        transfer.add(SHARE_FOLDER);
        transfer.add(NEW);
        stopAll = context.actions().register(''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test :jasper-plugin-fileserver:test :jasper-app:test --tests dev.jasper.app.plugins.RemoteShortcutConfigTest --tests dev.jasper.app.plugins.AppContractTest --tests dev.jasper.app.workspace.WindowChromeContributionsTest --tests dev.jasper.app.appearance.ThemeMigrationCoverageTest verifyPluginArchitecture --continue`
Expected: BUILD SUCCESSFUL.

Run: `git grep -n 'remote\.menu\|RemotePlugin\.MENU' -- '*/src/main/*.java'`
Expected: no output (no production source names the SSH menu).

Run: `git grep -n 'remote\.menu\|RemotePlugin\.MENU' -- '*/src/test/*.java'`
Expected: exactly one line, the new test's `host.menu("top:dev.jasper.remote.menu")).as("no SSH menu any more")`
assertion in `RemotePluginTest.java`.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote/src plugins/fileserver/src jasper-app/src/test/java/dev/jasper/app/plugins/RemoteShortcutConfigTest.java
git commit -F - <<'EOF'
Session and Transfer take Remote's SSH menu and File Server's sharing

Remote's SSH menu is gone: Connect to SSH Host, the hosts toggle, Split
with Same Host, New Host, Import from ~/.ssh/config and the tunnels group
sit in the Session menu, Open SFTP here and Transfers in the Transfer
menu, in their former order. File Server adds Share Folder (what the
empty panel's button runs) and puts it and New File Server in Transfer.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: File › Open File…

A new application action, `open_file` ("Open File…", no default binding), in File after New Window and in the palette.
It asks the window's file chooser for one existing local file, starting in the active pane's folder, and hands the
file to the route a Cmd-click takes (`onOpenFile`, then `openFileAt`). The application supplies the chooser from its
auxiliary windows (Rulings 4).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java:13`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceActions.java:27,51,119`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java:163,669,1435`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java:75`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java:51`
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/AuxiliaryWindows.java:34` (a method before `window`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:420`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/Rehearsal.java:145`
- Test: `jasper-app/src/test/java/dev/jasper/app/config/KeyBindingsTest.java:19`,
  `jasper-app/src/test/java/dev/jasper/app/config/KeymapDefaultsTest.java:26`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/ApplicationActionsTest.java` (one test),
  `jasper-app/src/test/java/dev/jasper/app/windows/AuxiliaryWindowsTest.java` (one test),
  `jasper-app/src/test/java/dev/jasper/app/application/RehearsalTest.java:516-525`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `ActionId.OPEN_FILE`; `public java.util.function.Function<Optional<Path>, Optional<Path>>
  WindowContent.fileChooser`; package-private `void WindowContent.openFile()`; `public Optional<Path>
  AuxiliaryWindows.openFile(UUID window, Optional<Path> folder)`.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 2 step 1: File › Open File…'s tests."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/test/java/dev/jasper/app/config/KeyBindingsTest.java", [
    ("            ActionId.NEW_TAB, ActionId.CLOSE_TAB, ActionId.NEW_WINDOW,\n",
     "            ActionId.NEW_TAB, ActionId.CLOSE_TAB, ActionId.NEW_WINDOW, ActionId.OPEN_FILE,\n", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/config/KeymapDefaultsTest.java", [
    ('        new Row("reload_config", KeyContext.GLOBAL, "none", "none"),',
     '        new Row("open_file", KeyContext.GLOBAL, "none", "none"),  // Enterprise spec 13: File › Open File…, unbound\n'
     '        new Row("reload_config", KeyContext.GLOBAL, "none", "none"),', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/workspace/ApplicationActionsTest.java", [
    (r'''    @Test void focusingFindFieldRetargetsGlobalPaneActions() throws Exception {
''', r'''    /** Spec 13: File › Open File… asks the window's chooser from the pane's folder and opens the file as a Cmd-click does. */
    @Test void openFileAsksTheChooserAndOpensTheChosenFileAsACmdClickDoes() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending)));
        pending.remove().run();
        var file = java.nio.file.Path.of("notes.txt").toAbsolutePath();
        var answers = new ArrayDeque<Optional<java.nio.file.Path>>(List.of(Optional.of(file), Optional.empty()));
        var asked = new ArrayList<Optional<java.nio.file.Path>>();
        var opened = new ArrayList<String>();
        edt(() -> {
            WindowContent ui = owner[0];
            ui.fileChooser = folder -> { asked.add(folder); return answers.remove(); };
            ui.onOpenFile = (chosen, line) -> opened.add(chosen + ":" + line);
            assertThat(ui.action(ActionId.OPEN_FILE).isEnabled()).isTrue();
            assertThat(ui.action(ActionId.OPEN_FILE).getValue(Action.NAME)).isEqualTo("Open File…");
            assertThat(ui.action(ActionId.OPEN_FILE).getValue(Action.SHORT_DESCRIPTION)).isEqualTo("Open a file with the default editor");
            assertThat(ui.action(ActionId.OPEN_FILE).getValue(Action.ACCELERATOR_KEY)).as("no default binding").isNull();
            JMenu fileMenu = null;
            for (int i = 0; i < ui.menuBar().getMenuCount(); i++) if ("File".equals(ui.menuBar().getMenu(i).getText())) fileMenu = ui.menuBar().getMenu(i);
            var labels = new ArrayList<String>();
            for (var child : fileMenu.getMenuComponents()) if (child instanceof JMenuItem item) labels.add(item.getText());
            assertThat(labels).containsSubsequence("New Window", "Open File…");
            var command = ui.commands().find("open_file").orElseThrow();
            assertThat(command.title()).as("spec 14.7: also a palette command").isEqualTo("Open File…");
            assertThat(command.keywords()).contains("open file", "open", "editor", "browse");
            assertThat(dev.jasper.app.commands.CommandSearch.find(ui.commands().entries(), "open file", List.of(), 5).stream()
                .map(dev.jasper.app.commands.Command::id)).as("the palette finds it").contains("open_file");
            ui.invoke(ActionId.OPEN_FILE);
            assertThat(asked).singleElement().as("the chooser starts in the pane's folder").isEqualTo(Optional.ofNullable(ui.directory()));
            assertThat(opened).containsExactly(file + ":0");
            ui.invoke(ActionId.OPEN_FILE);
            assertThat(opened).as("cancelling opens nothing").containsExactly(file + ":0");
            ui.close();
        });
    }

    @Test void focusingFindFieldRetargetsGlobalPaneActions() throws Exception {
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/windows/AuxiliaryWindowsTest.java", [
    (r'''    @Test void onlyOneOverlayCanOwnATerminalWindow() {
''', r'''    /** Spec 13: File › Open File… asks for one existing local file over the terminal window, from its pane's folder. */
    @Test void openFileAsksForOneLocalFileOverTheTerminalWindow() {
        var asked = new ArrayList<PathChoice>();
        var file = java.nio.file.Path.of("notes.txt").toAbsolutePath();
        var folder = java.nio.file.Path.of("project").toAbsolutePath();
        var host = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { },
            title -> { }, Rectangle::new, (title, initial) -> java.util.Optional.empty()),
            (choice, cancellation) -> { asked.add(choice); return List.of(dev.jasper.app.files.Location.local(file)); });
        UUID window = UUID.randomUUID();
        assertThat(host.openFile(window, java.util.Optional.of(folder))).contains(file);
        assertThat(asked).singleElement().satisfies(choice -> {
            assertThat(choice.terminalOwner()).isEqualTo(window);
            assertThat(choice.auxiliaryOwner()).isNull();
            assertThat(choice.title()).isEqualTo("Open File");
            assertThat(choice.mode()).isEqualTo(PathChoice.Mode.OPEN);
            assertThat(choice.multiple()).isFalse();
            assertThat(choice.initial()).isEmpty();
            assertThat(choice.terminalFolder()).contains(dev.jasper.app.files.Location.local(folder));
            assertThat(choice.sources()).isEmpty();
        });
        var cancelled = new AuxiliaryWindows(UiState.inMemory(), surface -> null, (choice, cancellation) -> List.of());
        assertThat(cancelled.openFile(window, java.util.Optional.empty())).isEmpty();
    }

    @Test void onlyOneOverlayCanOwnATerminalWindow() {
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/application/RehearsalTest.java", [
    (r'''    @Test void aSilencedWindowOpensNoFile() throws Exception {
        var opened = new CopyOnWriteArrayList<String>();
        edt(() -> {
            var content = DesktopTestSupport.content(launcher(new ArrayDeque<>()));
            content.onOpenFile = (file, line) -> opened.add(file + ":" + line);
            Rehearsal.silence(content);
            content.onOpenFile.accept(java.nio.file.Path.of("notes.txt"), 3);
        });
        assertThat(opened).isEmpty();
''', r'''    @Test void aSilencedWindowOpensNoFile() throws Exception {
        var opened = new CopyOnWriteArrayList<String>();
        var chosen = new CopyOnWriteArrayList<Boolean>();
        edt(() -> {
            var content = DesktopTestSupport.content(launcher(new ArrayDeque<>()));
            content.onOpenFile = (file, line) -> opened.add(file + ":" + line);
            content.fileChooser = folder -> java.util.Optional.of(java.nio.file.Path.of("chosen.txt"));
            Rehearsal.silence(content);
            content.onOpenFile.accept(java.nio.file.Path.of("notes.txt"), 3);
            chosen.add(content.fileChooser.apply(java.util.Optional.empty()).isPresent());
        });
        assertThat(opened).isEmpty();
        assertThat(chosen).as("the rehearsal's window shows no chooser").containsExactly(false);
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.config.KeyBindingsTest --tests dev.jasper.app.config.KeymapDefaultsTest --tests dev.jasper.app.workspace.ApplicationActionsTest --tests dev.jasper.app.windows.AuxiliaryWindowsTest --tests dev.jasper.app.application.RehearsalTest`
Expected: FAIL: `:jasper-app:compileTestJava` reports "cannot find symbol … OPEN_FILE", "cannot find symbol …
fileChooser" and "cannot find symbol … openFile(java.util.UUID,java.util.Optional<java.nio.file.Path>)".

- [ ] **Step 3: Add the action, its chooser and its route**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 2 step 3: ActionId.OPEN_FILE, the window's chooser hook, the application's chooser."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
APP = "jasper-app/src/main/java/dev/jasper/app/"
edit(APP + "commands/ActionId.java", [
    ('    NEW_WINDOW("new_window", "New Window", "cmd+n", "ctrl+shift+n"),\n',
     '    NEW_WINDOW("new_window", "New Window", "cmd+n", "ctrl+shift+n"),\n'
     '    OPEN_FILE("open_file", "Open File…", "none", "none"),\n', 1)])
edit(APP + "workspace/WorkspaceActions.java", [
    ('        action(ActionId.RELOAD_CONFIG).putValue(Action.SHORT_DESCRIPTION, unavailable);\n',
     '        action(ActionId.RELOAD_CONFIG).putValue(Action.SHORT_DESCRIPTION, unavailable);\n'
     '        action(ActionId.OPEN_FILE).putValue(Action.SHORT_DESCRIPTION, "Open a file with the default editor");\n', 1),
    ('            case NEW_WINDOW -> owner.requestNewWindow();\n',
     '            case NEW_WINDOW -> owner.requestNewWindow();\n'
     '            case OPEN_FILE -> owner.openFile();\n', 1),
    ('                    case COMMAND_PALETTE, NEW_TAB, NEW_WINDOW, QUIT, TOGGLE_ZEN_MODE -> true;',
     '                    case COMMAND_PALETTE, NEW_TAB, NEW_WINDOW, OPEN_FILE, QUIT, TOGGLE_ZEN_MODE -> true;', 1)])
edit(APP + "workspace/WindowContent.java", [
    ('    public java.util.function.BiConsumer<Path, Integer> onOpenFile = (file, line) -> { };\n',
     '    public java.util.function.BiConsumer<Path, Integer> onOpenFile = (file, line) -> { };\n'
     '    /**\n'
     '     * File › Open File…\'s chooser: given the active pane\'s folder, the file the person chose, or empty when they cancelled.\n'
     '     * The application shows its file chooser over this window; by default nothing is chosen. EDT.\n'
     '     */\n'
     '    public java.util.function.Function<java.util.Optional<Path>, java.util.Optional<Path>> fileChooser = folder -> java.util.Optional.empty();\n', 1),
    ('    void requestNewWindow() { newWindow.accept(directory()); }\n',
     '    void requestNewWindow() { newWindow.accept(directory()); }\n'
     '    /** File › Open File…: {@link #fileChooser} from the active pane\'s folder, then the route a Cmd-click takes, at no line. */\n'
     '    void openFile() { fileChooser.apply(java.util.Optional.ofNullable(directory())).ifPresent(file -> onOpenFile.accept(file, 0)); }\n', 1),
    ('        onToggleBuddy = () -> {}; buddyEnabled = () -> false; onOpenFile = (file, line) -> {};\n',
     '        onToggleBuddy = () -> {}; buddyEnabled = () -> false; onOpenFile = (file, line) -> {}; fileChooser = folder -> java.util.Optional.empty();\n', 1)])
edit(APP + "workspace/WindowChrome.java", [
    ('        JMenu file = menu("File", ActionId.NEW_TAB, ActionId.NEW_WINDOW, ActionId.CLOSE_TAB, ActionId.CLOSE_PANE,',
     '        JMenu file = menu("File", ActionId.NEW_TAB, ActionId.NEW_WINDOW, ActionId.OPEN_FILE, ActionId.CLOSE_TAB, ActionId.CLOSE_PANE,', 1)])
edit(APP + "workspace/WindowCommands.java", [
    ('                case OPEN_SETTINGS -> List.of("preferences", "configuration");\n',
     '                case OPEN_FILE -> List.of("open", "editor", "browse");\n'
     '                case OPEN_SETTINGS -> List.of("preferences", "configuration");\n', 1)])
edit(APP + "windows/AuxiliaryWindows.java", [
    ('    public AuxiliarySurface window(String id, String title, Dimension preferred, boolean singleton) {\n',
     r'''    /**
     * File › Open File…'s chooser: one existing local file, chosen over terminal window {@code window} and starting in
     * {@code folder} when its pane has one; empty when cancelled. EDT.
     */
    public java.util.Optional<java.nio.file.Path> openFile(UUID window, java.util.Optional<java.nio.file.Path> folder) {
        var choice = new PathChoice(window, null, "Open File", java.util.Optional.empty(), PathChoice.Mode.OPEN, false,
            folder.map(dev.jasper.app.files.Location::local), java.util.Optional.empty(), List.of());
        return choose(choice, cancel -> { }).stream().filter(dev.jasper.app.files.Location::isLocal)
            .map(dev.jasper.app.files.Location::localPath).findFirst();
    }

    public AuxiliarySurface window(String id, String title, Dimension preferred, boolean singleton) {
''', 1)])
edit(APP + "application/JasperApplication.java", [
    ('            window.content().onOpenFile = (file, line) -> openFileAt(file, line, windowId);\n',
     '            window.content().onOpenFile = (file, line) -> openFileAt(file, line, windowId);\n'
     '            // File › Open File… (spec 13): the plugins\' file chooser over this window; nothing before plugins start.\n'
     '            window.content().fileChooser = folder -> auxiliary == null ? Optional.empty() : auxiliary.openFile(windowId, folder);\n', 1)])
edit(APP + "application/Rehearsal.java", [
    ('        content.onOpenFile = (file, line) -> { };\n',
     '        content.onOpenFile = (file, line) -> { };\n'
     '        content.fileChooser = folder -> java.util.Optional.empty();\n', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*' --tests dev.jasper.app.workspace.ApplicationActionsTest --tests dev.jasper.app.workspace.WindowChromeContributionsTest --tests dev.jasper.app.workspace.FileLinkClickTest --tests dev.jasper.app.windows.AuxiliaryWindowsTest --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.palette.*' --tests dev.jasper.app.appearance.ThemeMigrationCoverageTest :jasper-app:javadoc verifyApplicationArchitecture --continue`
Expected: BUILD SUCCESSFUL. `ConfigTemplateTest` sees the new action in the generated template;
`ShortcutHelpTest` lists `open_file` under Application with no shortcut. If another test lists the whole command
catalog in order, add `open_file` after `new_window` there and record the file in the status banner.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src
git commit -F - <<'EOF'
File › Open File…

A new application action, open_file ("Open File…", no default
binding), in File after New Window and in the palette. It asks the
plugins' file chooser over the window for one existing local file,
starting in the pane's folder, and opens it as a Cmd-click does: in the
default editor. The rehearsal's window shows no chooser.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: "Vault: Locked" and the Snippets verb "Insert"

Two copy changes of spec 13: the Vault status item reads "Vault: Locked" or "Vault: Unlocked" ("Vault" with no vault,
Rulings 5), and the Snippets scope's first verb reads "Insert" (id `paste`, Rulings 6).

**Files:**
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java:160,349-357`
- Modify: `plugins/snippets/src/main/java/dev/jasper/snippets/SnippetsScope.java:22-24`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java:254,259-260`,
  `plugins/snippets/src/test/java/dev/jasper/snippets/SnippetsScopeTest.java` (one test)

**Interfaces:**
- Consumes: nothing.
- Produces: package-private `static String VaultPlugin.statusText(LockState state)`.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 3 step 1: the Vault status text and the Snippets verb label."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java", [
    ('"dev.jasper.vault.status|RIGHT|Vault|Vault unlocked · locks in 15 min|dev.jasper.vault.lock"',
     '"dev.jasper.vault.status|RIGHT|Vault: Unlocked|Vault unlocked · locks in 15 min|dev.jasper.vault.lock"', 1),
    ('"dev.jasper.vault.status|RIGHT|Vault|Vault locked|dev.jasper.vault.open"',
     '"dev.jasper.vault.status|RIGHT|Vault: Locked|Vault locked|dev.jasper.vault.open"', 1),
    ('            assertThat(VaultPlugin.tooltip(LockState.UNLOCKED, Duration.ZERO)).isEqualTo("Vault unlocked");\n',
     '            assertThat(VaultPlugin.tooltip(LockState.UNLOCKED, Duration.ZERO)).isEqualTo("Vault unlocked");\n'
     '            assertThat(VaultPlugin.statusText(LockState.NO_VAULT)).as("spec 13; no vault is neither locked nor unlocked").isEqualTo("Vault");\n', 1)])
edit("plugins/snippets/src/test/java/dev/jasper/snippets/SnippetsScopeTest.java", [
    (r'''    @Test void searchRanksNamesThenKeywordsThenCommandsAndTagsPlaceholderCounts() throws Exception {
''', r'''    /** Spec 13: the first verb reads Insert and keeps its id; the other verbs keep their labels. */
    @Test void theFirstVerbReadsInsertAndKeepsItsId() {
        assertThat(SnippetsScope.PASTE.id()).isEqualTo("paste");
        assertThat(List.of(SnippetsScope.PASTE, SnippetsScope.PASTE_RUN, SnippetsScope.EDIT))
            .extracting(dev.jasper.sdk.palette.PaletteVerb::label).containsExactly("Insert", "Paste and run", "Edit file");
    }

    @Test void searchRanksNamesThenKeywordsThenCommandsAndTagsPlaceholderCounts() throws Exception {
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-vault:test --tests dev.jasper.vault.VaultPluginTest :jasper-plugin-snippets:test --tests dev.jasper.snippets.SnippetsScopeTest --continue`
Expected: FAIL. `:jasper-plugin-vault:compileTestJava` reports "cannot find symbol … statusText"; `SnippetsScopeTest`
fails with "Expecting actual: [\"Paste\", \"Paste and run\", \"Edit file\"] to contain exactly … \"Insert\"".

- [ ] **Step 3: Change the copy**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 3 step 3: Vault: Locked/Unlocked, and Insert."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java", [
    ('        status.setText("Vault");\n', '', 1),
    (r'''        LockState state = lock.state();
        status.setIcon(state == LockState.UNLOCKED ? unlocked : locked);
''', r'''        LockState state = lock.state();
        status.setText(statusText(state));
        status.setIcon(state == LockState.UNLOCKED ? unlocked : locked);
''', 1),
    ('    /** "Vault locked", "No vault — click to create one", or',
     r'''    /** The status item's text (spec 13): "Vault: Locked", "Vault: Unlocked", or "Vault" while there is no vault yet. */
    static String statusText(LockState state) {
        return switch (state) {
            case NO_VAULT -> "Vault";
            case LOCKED -> "Vault: Locked";
            case UNLOCKED -> "Vault: Unlocked";
        };
    }

    /** "Vault locked", "No vault — click to create one", or''', 1)])
edit("plugins/snippets/src/main/java/dev/jasper/snippets/SnippetsScope.java", [
    ('/** The Snippets scope: Enter pastes, Cmd/Ctrl+Enter pastes and runs, Shift+Enter edits the file. */',
     '/** The Snippets scope: Enter inserts (pastes), Cmd/Ctrl+Enter pastes and runs, Shift+Enter edits the file. */', 1),
    ('    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste");',
     '    /** Spec 13: the first verb reads Insert; its id stays {@code paste}, which palette history keys on. */\n'
     '    static final PaletteVerb PASTE = new PaletteVerb("paste", "Insert");', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-vault:test :jasper-plugin-snippets:test :jasper-plugin-history:test :jasper-app:test --tests dev.jasper.app.plugins.BundledSamplePluginTest --tests 'dev.jasper.app.plugins.Staged*' --tests dev.jasper.app.appearance.ThemeMigrationCoverageTest --continue`
Expected: BUILD SUCCESSFUL (`BundledSamplePluginTest` still sees "Vault" in its fresh home, which has no vault).

- [ ] **Step 5: Commit**

```bash
git add plugins/vault/src plugins/snippets/src
git commit -F - <<'EOF'
Vault: Locked, Vault: Unlocked, and the Snippets verb Insert

The Vault status item names the lock state ("Vault" while there is no
vault), and the Snippets scope's first verb reads Insert; its id stays
paste and the other verbs keep their labels.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: Titled leading groups on Settings pages

A page whose first group of rows has no section heading takes the page's title as its heading (spec 12.2, both looks):
Command Palette, Desk Buddy, Terminal, Cursor, Shell and every generated plugin page such as Credential Vault. System
Settings' first group is titled "General". One rule in `SchemaPage.viewSpec` (Rulings 7).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java:302-304,311-313,322,326`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java:180-185`
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageTest.java:170` (and one test),
  `jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java` (one test),
  `jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsPageTest.java` (one test and a helper)

**Interfaces:**
- Consumes: nothing.
- Produces: private `String SchemaPage.heading(String title, boolean rows, List<Nodes.Node> children)`.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 4 step 1: every page's first group of rows is titled."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageTest.java", [
    (r'''    @Test void aSectionWhoseRowsAreAllHiddenShowsNoHeader() {
''', r'''    /** Spec 12.2 and 13: leading rows that no section heads take the page's title; nothing heads rows that are not built. */
    @Test void aPagesLeadingUntitledRowsTakeThePagesName() {
        var leading = SchemaPage.page("window", "Window")
            .row(CoreSettings.WINDOW_STATUS_BAR)
            .section("Size")
            .row(CoreSettings.WINDOW_COLUMNS);
        assertThat(PageKit.sectionTitles(leading.build(context))).containsExactly("Window", "Size");
        var sectioned = SchemaPage.page("window", "Window").section("Bars").row(CoreSettings.WINDOW_STATUS_BAR);
        assertThat(PageKit.sectionTitles(sectioned.build(context))).as("a page that starts with a section keeps it").containsExactly("Bars");
        var hidden = SchemaPage.page("window", "Window")
            .row(CoreSettings.WINDOW_NATIVE_DECORATIONS).shownWhen(false)
            .section("Bars").row(CoreSettings.WINDOW_STATUS_BAR);
        assertThat(PageKit.sectionTitles(hidden.build(context))).as("no page-name heading over rows that are not built").containsExactly("Bars");
        var only = SchemaPage.page("window", "Window").row(CoreSettings.WINDOW_STATUS_BAR).row(CoreSettings.WINDOW_COLUMNS);
        assertThat(PageKit.sectionTitles(only.build(context))).containsExactly("Window");
    }

    @Test void aSectionWhoseRowsAreAllHiddenShowsNoHeader() {
''', 1),
    # An action row before any section is a leading row too: it now sits in the page-named Section's card.
    ("        var row = (Nodes.SettingRow) ((Nodes.Card) ((Nodes.Stack) page.viewSpec().root()).children().getFirst()).children().getFirst();\n",
     "        var first = (Nodes.Section) ((Nodes.Stack) page.viewSpec().root()).children().getFirst();\n"
     "        assertThat(first.title()).as(\"spec 12.2: the page's name heads its leading rows\").isEqualTo(\"System Settings\");\n"
     "        var row = (Nodes.SettingRow) ((Nodes.Card) first.body()).children().getFirst();\n", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java", [
    (r'''    @Test void decimalSettingsAreSteppers() {
''', r'''    /** Spec 12.2 and 13: every core page's first group is titled, by its own section or by the page's name. */
    @Test void everyCorePagesFirstGroupIsTitled() {
        var firsts = new java.util.LinkedHashMap<String, String>();
        for (var spec : CorePages.specs(APPEARANCE, system)) {
            var titles = sectionTitles(spec);
            firsts.put(spec.id(), titles.isEmpty() ? "" : titles.getFirst());
        }
        assertThat(firsts).containsEntry("command-palette", "Command Palette").containsEntry("desk-buddy", "Desk Buddy")
            .containsEntry("system", "General").containsEntry("terminal", "Terminal").containsEntry("terminal.cursor", "Cursor")
            .containsEntry("terminal.shell", "Shell").containsEntry("appearance", "Theme").containsEntry("window", "Toolbar & Status Bar")
            .containsEntry("notifications", "System Notifications").containsEntry("terminal.font", "Font");
        assertThat(firsts.values()).as("no page starts with an untitled card").doesNotContain("");
    }

    @Test void decimalSettingsAreSteppers() {
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsPageTest.java", [
    (r'''    private static void collect(java.awt.Component root, java.util.function.Predicate<String> name, List<javax.swing.JButton> found) {
''', r'''    /** Spec 12.2 and 13: a generated page's rows before its first section are titled with the page's name. */
    @Test void aGeneratedPagesLeadingRowsAreTitledWithThePagesName() {
        start(ID, "Pages", settings -> {
            settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode"));
            settings.declare(SettingSpec.of("sftp.parallel", SettingKind.integer(1, 8), 2).label("Parallel files").section("SFTP"));
        });
        var page = page();
        onEdt(() -> assertThat(sectionTitles(page.component())).containsExactly("Pages", "SFTP"));
    }

    /** The mounted section headings below {@code root}, in order. */
    private static List<String> sectionTitles(java.awt.Component root) {
        var titles = new ArrayList<String>();
        if (root instanceof javax.swing.JLabel label && label.getName() != null && label.getName().matches("section-\\d+\\.title"))
            titles.add(label.getText());
        if (root instanceof java.awt.Container container) for (var child : container.getComponents()) titles.addAll(sectionTitles(child));
        return titles;
    }

    private static void collect(java.awt.Component root, java.util.function.Predicate<String> name, List<javax.swing.JButton> found) {
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.settingsui.SchemaPageTest --tests dev.jasper.app.settingsui.CorePagesTest --tests dev.jasper.app.plugins.PluginSettingsPageTest --continue`
Expected: FAIL: `aPagesLeadingUntitledRowsTakeThePagesName` ("Expecting actual: [\"Size\"] to contain exactly
[\"Window\", \"Size\"]"), `everyCorePagesFirstGroupIsTitled` (Command Palette and the other five map to "") and
`aGeneratedPagesLeadingRowsAreTitledWithThePagesName` ("[\"SFTP\"]"); `anActionRowIsASettingsRowWithItsButtonsAtTheEnd`
fails with a `ClassCastException` (its page's first child is still a bare `Card`).

- [ ] **Step 3: Title the leading run**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 4 step 3: the page's title heads its leading untitled rows; System Settings opens with General."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
UI = "jasper-app/src/main/java/dev/jasper/app/settingsui/"
edit(UI + "SchemaPage.java", [
    (r'''                    close(children, group, title, sections++);
                    title = section.title();''', r'''                    close(children, group, heading(title, !group.isEmpty(), children), sections++);
                    title = section.title();''', 1),
    (r'''                            if(!group.isEmpty()){close(children,group,title,sections++);title=null;}
                            Nodes.Node content=row(owner);
                            children.add(title==null?content:new Nodes.Section("section-"+sections++,ControlState.READY,title,content));title=null;''',
     r'''                            if(!group.isEmpty()){close(children,group,heading(title,true,children),sections++);title=null;}
                            Nodes.Node content=row(owner);String shown=heading(title,true,children);
                            children.add(shown==null?content:new Nodes.Section("section-"+sections++,ControlState.READY,shown,content));title=null;''', 1),
    ('        close(children, group, title, sections);\n        return new ViewSpec(',
     '        close(children, group, heading(title, !group.isEmpty(), children), sections);\n        return new ViewSpec(', 1),
    ('    /** Ends a section: its title over a settings card of its rows; an empty untitled run adds nothing. */\n',
     r'''    /**
     * A run's heading: its section's title, or, for the page's first run of shown rows when no section heads it, the
     * page's own title (spec 12.2, both looks), so no page starts with an untitled card.
     */
    private String heading(String title, boolean rows, List<Nodes.Node> children) {
        return title == null && rows && children.isEmpty() ? spec.title : title;
    }

    /** Ends a section: its title over a settings card of its rows; an empty untitled run adds nothing. */
''', 1)])
edit(UI + "CorePages.java", [
    ("     * System Settings: Run in background, the Java Virtual Machine and Command line sections when supplied, then the\n",
     "     * System Settings: General (Run in background), the Java Virtual Machine and Command line sections when supplied, then the\n", 1),
    ("            .row(SYSTEM_RUN_IN_BACKGROUND);\n",
     "            .section(\"General\")\n            .row(SYSTEM_RUN_IN_BACKGROUND);\n", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.plugins.PluginSettings*' --tests 'dev.jasper.app.plugins.PluginPanelPageTest' --tests 'dev.jasper.app.pluginmanager.*' --tests dev.jasper.app.appearance.ThemeMigrationCoverageTest :jasper-app:javadoc --continue`
Expected: BUILD SUCCESSFUL. If a test located a row through the page's first child being a bare `Card`, read it
through the new `Section`'s body and record the file in the status banner.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src
git commit -F - <<'EOF'
Settings titles every page's first group

A page's leading rows that no section heads take the page's name as
their heading (Command Palette, Desk Buddy, Terminal, Cursor, Shell and
the generated plugin pages), and System Settings opens with General. A
page that starts with a section, or whose leading rows are all hidden
here, is unchanged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: File Server's warning Notice and read-only folder

New File Server's warnings become one WARNING Notice (Rulings 8) and its folder a read-only field that only the
separate Choose… changes (Rulings 9). Tests that typed a folder choose it instead: the editor's injected chooser, the
fake's queued selection, and a scripted chooser in the staged host.

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/ServerEditor.java:56,170,405,455-459`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/ServerEditorTest.java:23,36,52-53,81-84,175,228,231`
  (two new tests), `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/ServerCompositionTest.java:15,56,72-87,89-91`,
  `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/ServerLifetimeTest.java:51,59`,
  `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/UiTestAccess.java:42`,
  `plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java:276`
- Test (staged host): `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java:109-113`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/StagedVaultHost.java:44,53,196`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/KitViewTestAccess.java:125`,
  `jasper-app/src/test/java/dev/jasper/app/appearance/SurfaceFixtures.java:25`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/StagedFileServerFormTest.java:93,97,98`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/FileServerRenderEvidenceTest.java:54,98`

**Interfaces:**
- Consumes: 3b's `Nodes.Notice(String, ControlState, Severity, String)` and 13-argument `Nodes.Field(…, boolean readOnly)`.
- Produces: `public static final String ServerEditor.WARNING = "server.warning"`; test support
  `static AuxiliaryWindows AppContractTest.headlessWindows(dev.jasper.app.windows.PathChooser)`,
  `StagedVaultHost.chosenFolders`, `static void StagedVaultHost.chooseFolder(StagedVaultHost, AuxiliarySurface, Path)`,
  `public void KitViewTestAccess.chooseFolder(Path)`, `UiTestAccess.chooseFolder(ServerEditor)` (fileserver tests).

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 5 step 1: the Notice, the read-only folder, and folders chosen rather than typed."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
T = "plugins/fileserver/src/test/java/dev/jasper/fileserver/"
edit(T + "ui/ServerEditorTest.java", [
    ("    private int folderRequests, keyRequests;\n",
     "    private int folderRequests, keyRequests;\n    /** What the next Choose… answers; the temporary folder when null. */\n    private Path nextFolder;\n", 1),
    ("            () -> { folderRequests++; return Optional.of(folder); }, keys, linux, saved::add, () -> { })));",
     "            () -> { folderRequests++; return Optional.of(nextFolder == null ? folder : nextFolder); }, keys, linux, saved::add, () -> { })));", 1),
    ("    private static String text(ServerEditor editor, String id) { return ((Nodes.Text) node(editor, id)).text(); }\n",
     "    private static String text(ServerEditor editor, String id) { return ((Nodes.Text) node(editor, id)).text(); }\n"
     "    private static String notice(ServerEditor editor) { return ((Nodes.Notice) node(editor, ServerEditor.WARNING)).text(); }\n", 1),
    ('    private void basic(ServerEditor editor) { edit(editor, ServerEditor.NAME, "docs"); edit(editor, ServerEditor.FOLDER, folder.toString()); }',
     '    private void basic(ServerEditor editor) { edit(editor, ServerEditor.NAME, "docs"); action(editor, ServerEditor.CHOOSE_FOLDER); }', 1),
    (r'''        edit(editor, ServerEditor.FOLDER, folder.resolve("missing").toString()); action(editor, ServerEditor.SAVE);
        assertThat(message(editor)).contains("does not exist");
        action(editor, ServerEditor.CHOOSE_FOLDER);
        assertThat(folderRequests).isEqualTo(1);
''', r'''        nextFolder = folder.resolve("missing"); action(editor, ServerEditor.CHOOSE_FOLDER); action(editor, ServerEditor.SAVE);
        assertThat(message(editor)).contains("does not exist");
        nextFolder = folder; action(editor, ServerEditor.CHOOSE_FOLDER);
        assertThat(folderRequests).isEqualTo(2);
''', 1),
    ('text(editor, "server.warning")', 'notice(editor)', 3),
    (r'''    @Test void protocolChangesPreserveExplicitPortAndHiddenValues() {
''', r'''    /** Spec 13: the folder is a read-only fact that only Choose…, beside it, changes. */
    @Test void theFolderIsAReadOnlyFieldThatOnlyChooseChanges() {
        var editor = editor(Set.of(Protocol.HTTP));
        var folderField = (Nodes.Field) node(editor, ServerEditor.FOLDER);
        assertThat(folderField.readOnly()).isTrue();
        assertThat(folderField.value()).isEmpty();
        assertThat(folderField.hint()).isEqualTo("No folder selected");
        assertThat(((Nodes.FormRow) node(editor, "server.folder.row")).trailing()).get().isInstanceOfSatisfying(Nodes.Button.class, choose -> {
            assertThat(choose.id()).isEqualTo(ServerEditor.CHOOSE_FOLDER);
            assertThat(choose.label()).isEqualTo("Choose…");
        });
        edit(editor, ServerEditor.FOLDER, "/typed/elsewhere");
        assertThat(field(editor, ServerEditor.FOLDER)).as("typing never reaches a read-only field").isEmpty();
        action(editor, ServerEditor.CHOOSE_FOLDER);
        assertThat(field(editor, ServerEditor.FOLDER)).isEqualTo(folder.toString());
    }

    /** Spec 13: the warnings are one WARNING Notice, present only while there is a warning. */
    @Test void theWarningsAreAWarningNoticeOnlyWhileThereIsOne() {
        var editor = editor(Set.of(Protocol.HTTP, Protocol.FTP));
        assertThat(node(editor, ServerEditor.WARNING)).as("anonymous read-only HTTP warns of nothing").isNull();
        choose(editor, ServerEditor.PROTOCOL, "FTP");
        assertThat(node(editor, ServerEditor.WARNING)).isInstanceOfSatisfying(Nodes.Notice.class, notice -> {
            assertThat(notice.severity()).isEqualTo(Nodes.Severity.WARNING);
            assertThat(notice.text()).as("the presentation adds the word Warning").isEqualTo("Plain FTP sends the password unencrypted");
            assertThat(notice.actions()).isEmpty();
        });
        choose(editor, ServerEditor.PROTOCOL, "HTTP");
        assertThat(node(editor, ServerEditor.WARNING)).isNull();
    }

    @Test void protocolChangesPreserveExplicitPortAndHiddenValues() {
''', 1)])
edit(T + "ui/ServerCompositionTest.java", [
    ("    @TempDir Path root;\n", "    @TempDir Path root;\n    /** What Choose… answers; nothing (a cancelled chooser) when null. */\n    private Path nextFolder;\n", 1),
    ('                emit(editor,new UiEvent.Edited(ServerEditor.FOLDER,root.resolve("not-present").toString()));',
     '                nextFolder=root.resolve("not-present");emit(editor,new UiEvent.Action(ServerEditor.CHOOSE_FOLDER));', 1),
    # 3b Task 3 inserts its one-protocol assertions into this test before "String longPath"; these edits leave them alone.
    ("    @Test void explicitBrowseCancellationKeepsLongDraftAndPerformsNoImplicitChooserRead() {\n",
     "    @Test void explicitBrowseCancellationKeepsTheChosenFolderAndPerformsNoImplicitChooserRead() {\n", 1),
    ('            int[] requests={0};var kit=new FakeKit("dev.jasper.fileserver");\n',
     '            String chosenPath=root.resolve("very-long-synthetic-folder/another-segment/still-chosen").toString();\n'
     '            var answers=new java.util.ArrayDeque<Optional<Path>>(List.of(Optional.of(Path.of(chosenPath)),Optional.empty()));\n'
     '            int[] requests={0};var kit=new FakeKit("dev.jasper.fileserver");\n', 1),
    ("()->{requests[0]++;return Optional.empty();}", "()->{requests[0]++;return answers.remove();}", 1),
    (r'''                String longPath=root.resolve("very-long-synthetic-folder/another-segment/still-editable").toString();
                emit(editor,new UiEvent.Edited(ServerEditor.FOLDER,longPath));assertThat(requests[0]).isZero();
                emit(editor,new UiEvent.Action(ServerEditor.CHOOSE_FOLDER));assertThat(requests[0]).isEqualTo(1);
                assertThat(((Nodes.Field)node(editor,ServerEditor.FOLDER)).value()).isEqualTo(longPath);
''', r'''                assertThat(requests[0]).as("opening the editor reads no folder").isZero();
                emit(editor,new UiEvent.Action(ServerEditor.CHOOSE_FOLDER));
                assertThat(((Nodes.Field)node(editor,ServerEditor.FOLDER)).value()).isEqualTo(chosenPath);
                emit(editor,new UiEvent.Action(ServerEditor.CHOOSE_FOLDER));assertThat(requests[0]).isEqualTo(2);
                assertThat(((Nodes.Field)node(editor,ServerEditor.FOLDER)).value()).as("a cancelled Choose… keeps the folder").isEqualTo(chosenPath);
                assertThat(((Nodes.Field)node(editor,ServerEditor.FOLDER)).readOnly()).isTrue();
''', 1),
    ("            id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()),Optional::empty,ServerEditor.KeySources.NONE,false,d -> {},() -> {});",
     "            id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()),()->Optional.ofNullable(nextFolder),ServerEditor.KeySources.NONE,false,d -> {},() -> {});", 1)])
edit(T + "ui/ServerLifetimeTest.java", [
    ("                Optional::empty,ServerEditor.KeySources.NONE,false,save,()->completed++,dialog::close);",
     "                ()->Optional.of(root),ServerEditor.KeySources.NONE,false,save,()->completed++,dialog::close);", 1),
    ('        void valid() {emit(new UiEvent.Edited(ServerEditor.NAME,"fixture"));emit(new UiEvent.Edited(ServerEditor.FOLDER,root.toString()));}',
     '        void valid() {emit(new UiEvent.Edited(ServerEditor.NAME,"fixture"));emit(new UiEvent.Action(ServerEditor.CHOOSE_FOLDER));}', 1)])
edit(T + "ui/UiTestAccess.java", [
    ("    public static Field folder(ServerEditor editor) { return new Field(fake(editor.view()), ServerEditor.FOLDER); }\n",
     "    /** Choose… beside the read-only folder: the host's next queued directory, or the editor's own chooser. */\n"
     "    public static Button chooseFolder(ServerEditor editor) { return button(editor.view(), ServerEditor.CHOOSE_FOLDER); }\n", 1)])
edit(T + "FileServerPluginTest.java", [
    ("            UiTestAccess.folder(editor).setText(shared.toString());\n",
     "            host.queuePathSelection(List.of(shared));\n            UiTestAccess.chooseFolder(editor).doClick();\n", 1)])
A = "jasper-app/src/test/java/dev/jasper/app/"
edit(A + "plugins/AppContractTest.java", [
    ("    /** UI thread: plugin windows over shells that touch no native window. */\n",
     r'''    /** UI thread: {@link #headlessWindows()} whose file chooser is {@code chooser}, for tests that pick files. */
    static AuxiliaryWindows headlessWindows(dev.jasper.app.windows.PathChooser chooser) {
        return new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { },
            () -> new java.awt.Rectangle(0, 0, 10, 10), (title, initial) -> java.util.Optional.empty()), chooser);
    }

    /** UI thread: plugin windows over shells that touch no native window. */
''', 1)])
edit(A + "plugins/StagedVaultHost.java", [
    ("    final List<String> problems = new CopyOnWriteArrayList<>();\n",
     "    final List<String> problems = new CopyOnWriteArrayList<>();\n"
     "    /** The folders the host's file chooser answers, in order; it cancels when none is queued. */\n"
     "    final java.util.Deque<Path> chosenFolders = new java.util.concurrent.ConcurrentLinkedDeque<>();\n", 1),
    ("        auxiliary = onEdtValue(AppContractTest::headlessWindows);\n",
     "        auxiliary = onEdtValue(() -> AppContractTest.headlessWindows((choice, cancellation) -> {\n"
     "            Path next = chosenFolders.poll();\n"
     "            return next == null ? List.of() : List.of(dev.jasper.app.files.Location.local(next));\n"
     "        }));\n", 1),
    ("    static void type(AuxiliarySurface surface, String name, String text) { ((JTextComponent) require(surface, name)).setText(text); }\n",
     "    static void type(AuxiliarySurface surface, String name, String text) { ((JTextComponent) require(surface, name)).setText(text); }\n"
     "    /** Spec 13: a File Server folder is read-only, so tests choose it: queue it, then press Choose…. EDT. */\n"
     "    static void chooseFolder(StagedVaultHost host, AuxiliarySurface surface, Path folder) {\n"
     "        host.chosenFolders.add(folder); click(surface, \"server.folder.choose\");\n"
     "    }\n", 1)])
edit(A + "plugins/KitViewTestAccess.java", [
    ("    public void type(String id, String text) { onEdt(() -> StagedVaultHost.type(surface, id + \".input\", text)); }\n",
     "    public void type(String id, String text) { onEdt(() -> StagedVaultHost.type(surface, id + \".input\", text)); }\n"
     "    /** Chooses {@code folder} for New File Server through its Choose… button and the host's chooser. */\n"
     "    public void chooseFolder(Path folder) { onEdt(() -> StagedVaultHost.chooseFolder(vault, surface, folder)); }\n", 1)])
edit(A + "appearance/SurfaceFixtures.java", [
    ('owner.type("server.name", "synthetic-preview-server");owner.type("server.folder",home.toString());',
     'owner.type("server.name", "synthetic-preview-server");owner.chooseFolder(home);', 1)])
edit(A + "plugins/StagedFileServerFormTest.java", [
    ('type(form,"server.name.input","synthetic-preview-server");type(form,"server.folder.input",root.resolve("home").toString());',
     'type(form,"server.name.input","synthetic-preview-server");chooseFolder(vault,form,root.resolve("home"));\n'
     '                assertThat(((JTextField)require(form,"server.folder.input")).isEditable()).as("spec 13: the folder is a read-only field").isFalse();\n'
     '                assertThat(((JTextField)require(form,"server.folder.input")).getText()).isEqualTo(root.resolve("home").toAbsolutePath().normalize().toString());', 1),
    ('type(form,"server.folder.input",root.resolve("home/very-long-synthetic-folder-path/with-many-segments/that-stays-in-the-input").toString());',
     'chooseFolder(vault,form,root.resolve("home/very-long-synthetic-folder-path/with-many-segments/that-stays-in-the-input"));', 1),
    ('type(form,"server.folder.input",root.resolve("home").toString());captureServer("short"',
     'chooseFolder(vault,form,root.resolve("home"));captureServer("short"', 1)])
# The render evidence saves a server too: its chooser answers the synthetic home, and Choose… sets the folder.
edit(A + "plugins/FileServerRenderEvidenceTest.java", [
    ("        var auxiliary = onEdtValue(AppContractTest::headlessWindows);\n",
     "        var auxiliary = onEdtValue(() -> AppContractTest.headlessWindows((choice, cancellation) -> List.of(dev.jasper.app.files.Location.local(home))));\n", 1),
    ('                    ((JTextComponent) StagedVaultHost.named(editor.holder(), "server.folder.input")).setText(home.toString());\n',
     '                    ((javax.swing.AbstractButton) StagedVaultHost.named(editor.holder(), "server.folder.choose")).doClick(0);\n', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-fileserver:test :jasper-app:test --tests dev.jasper.app.plugins.StagedFileServerFormTest --continue`
Expected: FAIL. `:jasper-plugin-fileserver:compileTestJava` reports "cannot find symbol … WARNING" in `ServerEditorTest`
(Gradle then does not run the plugin's tests); `StagedFileServerFormTest` fails on "spec 13: the folder is a read-only
field" (the input is still editable).

- [ ] **Step 3: The Notice and the read-only folder**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 5 step 3: New File Server's WARNING Notice and read-only folder."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/ServerEditor.java", [
    ('        SAVE = "server.save", CANCEL = "server.cancel", MESSAGE = "server.message";',
     '        SAVE = "server.save", CANCEL = "server.cancel", MESSAGE = "server.message", WARNING = "server.warning";', 1),
    ("                    case NAME -> name = edit.text(); case FOLDER -> folder = edit.text();",
     "                    // The folder is read-only (spec 13): only Choose… sets it, so an edit of it is ignored.\n"
     "                    case NAME -> name = edit.text();", 1),
    ('            new Nodes.Field(FOLDER, ControlState.READY, Nodes.FieldKind.PATH, folder, "No folder selected", "Folder", ""),',
     '            new Nodes.Field(FOLDER, ControlState.READY, Nodes.FieldKind.PATH, folder, "No folder selected", "Folder", "", Optional.empty(),\n'
     '                Nodes.FieldPresentation.STANDARD, "", "", "", true),', 1),
    (r'''        var sections = List.<Nodes.Node>of(section("server.general", "Server", Nodes.CardKind.FORM, server),
            section("server.access", "Access", Nodes.CardKind.MIXED, access),
            section("server.security", "Security & Startup", Nodes.CardKind.SETTINGS, security),
            text("server.warning", warnings.isEmpty() ? "" : "Warning · " + String.join(" · ", warnings), Nodes.TextRole.DESCRIPTION),
            text("server.tls.dependency", ftp && requireTls && !tls ? "Require TLS needs Use TLS. Turn Use TLS on to change this requirement." : "", Nodes.TextRole.DESCRIPTION));''',
     r'''        var sections = new ArrayList<Nodes.Node>(List.of(section("server.general", "Server", Nodes.CardKind.FORM, server),
            section("server.access", "Access", Nodes.CardKind.MIXED, access),
            section("server.security", "Security & Startup", Nodes.CardKind.SETTINGS, security)));
        // Spec 13: the warnings are one WARNING Notice, shown while there is one; the presentation adds the word Warning.
        if (!warnings.isEmpty()) sections.add(new Nodes.Notice(WARNING, ControlState.READY, Nodes.Severity.WARNING, String.join(" · ", warnings)));
        sections.add(text("server.tls.dependency", ftp && requireTls && !tls ? "Require TLS needs Use TLS. Turn Use TLS on to change this requirement." : "", Nodes.TextRole.DESCRIPTION));''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-fileserver:test :jasper-app:test --tests 'dev.jasper.app.plugins.Staged*' --tests 'dev.jasper.app.plugins.FileServer*' --tests 'dev.jasper.app.appearance.Surface*' --tests dev.jasper.app.appearance.ThemeMigrationCoverageTest --continue`
Expected: BUILD SUCCESSFUL. The staged form chooses its folders through the host's chooser and saves for real;
`StagedFileServerFormTest` writes its captures under `jasper-app/build/theme-migration-evidence/fileserver-add/actual/`.

Run: `git grep -n 'Edited(ServerEditor.FOLDER\|server.folder.input")).setText\|"server.folder.input",' -- '*.java'`
Expected: no output (no test types a folder any more, whichever helper it used).

- [ ] **Step 5: Commit**

```bash
git add plugins/fileserver/src jasper-app/src/test
git commit -F - <<'EOF'
New File Server: a warning notice and a read-only folder

The editor's warnings are one WARNING Notice, shown while there is one,
and its folder is a read-only field that only the Choose… beside it
changes. Tests choose folders rather than typing them: through the
editor's chooser, the fake's queued selection and a scripted chooser in
the staged host.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: Sentence-case plugin form labels

The five Title Case plugin form labels and their choices' accessible labels become sentence case (decision 17,
Rulings 10): "Via host", "Jump host", "Reachable from", "Listen on", "Forward to".

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/HostEditor.java:358`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelEditor.java:269,280,283`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/ServerEditor.java:407,410`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelEditorTest.java:81-82`,
  `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsCompositionTest.java:110,117`,
  `plugins/remote/src/test/java/dev/jasper/remote/ui/HostEditorTest.java:110`,
  `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/ServerEditorTest.java` (one test)

**Interfaces:**
- Consumes: nothing.
- Produces: nothing new.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 6 step 1: the labels in sentence case."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
R = "plugins/remote/src/test/java/dev/jasper/remote/ui/"
edit(R + "tunnels/TunnelEditorTest.java", [
    ('assertThat(row.label()).isEqualTo("Listen On");', 'assertThat(row.label()).isEqualTo("Listen on");', 1),
    ('assertThat(row.label()).isEqualTo("Forward To");', 'assertThat(row.label()).isEqualTo("Forward to");', 1),
    ('            assertThat(saveEnabled(editor)).isTrue();\n            click(editor.view(), TunnelEditor.SAVE);\n',
     '            assertThat(choice(editor, TunnelEditor.HOST).label()).as("spec 13: sentence case").isEqualTo("Via host");\n'
     '            assertThat(saveEnabled(editor)).isTrue();\n            click(editor.view(), TunnelEditor.SAVE);\n', 1)])
edit(R + "tunnels/TunnelsCompositionTest.java", [
    ('.containsExactly("Name", "Via Host", "Type", "");', '.containsExactly("Name", "Via host", "Type", "");', 1),
    ('                assertThat(row.label()).isEqualTo("Listen On");', '                assertThat(row.label()).isEqualTo("Listen on");', 1)])
edit(R + "HostEditorTest.java", [
    ('            assertThat(choice(editor, HostEditor.JUMP).options()).as("none + bastion").hasSize(2);\n',
     '            assertThat(choice(editor, HostEditor.JUMP).options()).as("none + bastion").hasSize(2);\n'
     '            assertThat(choice(editor, HostEditor.JUMP).label()).as("spec 13: sentence case").isEqualTo("Jump host");\n'
     '            assertThat(node(editor.view(), "row." + HostEditor.JUMP, Nodes.FormRow.class).label()).isEqualTo("Jump host");\n', 1)])
edit("plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/ServerEditorTest.java", [
    (r'''    @Test void protocolChangesPreserveExplicitPortAndHiddenValues() {
''', r'''    /** Spec 13: plugin form labels are sentence case. */
    @Test void theReachableRowIsSentenceCase() {
        var editor = editor(Set.of(Protocol.HTTP));
        assertThat(((Nodes.FormRow) node(editor, "server.reachable.row")).label()).isEqualTo("Reachable from");
        assertThat(((Nodes.Choice) node(editor, ServerEditor.REACHABLE)).label()).isEqualTo("Reachable from");
    }

    @Test void protocolChangesPreserveExplicitPortAndHiddenValues() {
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.*' :jasper-plugin-fileserver:test --tests dev.jasper.fileserver.ui.ServerEditorTest --continue`
Expected: FAIL, each new or changed assertion with its old Title Case text (for example "expected: \"Listen on\" but
was: \"Listen On\"").

- [ ] **Step 3: Change the labels**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 6 step 3: Via host, Jump host, Reachable from, Listen on, Forward to."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("plugins/remote/src/main/java/dev/jasper/remote/ui/HostEditor.java", [('"Jump Host"', '"Jump host"', 2)])
edit("plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelEditor.java", [
    ('"Via Host"', '"Via host"', 2), ('"Listen On"', '"Listen on"', 1), ('"Forward To"', '"Forward to"', 1)])
edit("plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/ServerEditor.java", [('"Reachable From"', '"Reachable from"', 2)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test :jasper-plugin-fileserver:test :jasper-app:test --tests 'dev.jasper.app.plugins.Staged*' --tests 'dev.jasper.app.plugins.*RenderEvidenceTest' --tests 'dev.jasper.app.appearance.Surface*' --continue`
Expected: BUILD SUCCESSFUL.

Scan every plugin form label (the scan prints nothing when each is sentence case):

```bash
python3 -I - <<'PY'
import pathlib, re
pattern = re.compile(r'(?:FormRow\(\s*[^,]+,\s*(?:[^,()]+|state\([^)]*\)),\s*|\brow\(\s*[^,]+,\s*)"([^"]+)"')
for path in sorted(pathlib.Path("plugins").rglob("*.java")):
    if "/test/" in str(path): continue
    text = path.read_text(encoding="utf-8")
    for match in pattern.finditer(text):
        words = match.group(1).split()
        if len(words) > 1 and any(word[0].isupper() for word in words[1:]):
            print(path, text[:match.start()].count("\n") + 1, repr(match.group(1)))
PY
```

Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote/src plugins/fileserver/src
git commit -F - <<'EOF'
Plugin form labels in sentence case

Via host, Jump host, Reachable from, Listen on and Forward to, with
their choices' accessible labels, in both looks (DESIGN_LANGUAGE §3).

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: The ordered-list editor for plugin `stringList` host rows

A plugin's `stringList` host row becomes the generated pages' ordered-list editor (`Editors.textList()`), node for
node, in the host's `KitSettingRows` and the testkit's mirror alike (Rulings 11): rows move and remove themselves, the
entry below adds one, a rejected value keeps its entry and shows the row's error, and stale events change nothing.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/KitSettingRows.java:15,27,38-57,93,117`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/KitSettingRows.java:12,26,36-39,57`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeSettingsPanelHost.java:27-33,50,91,126`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeSetting.java:105`
- Test: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeKitMountingTest.java:41` (and one test),
  `jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java` (one test and a helper),
  `plugins/indexer/src/test/java/dev/jasper/indexer/ui/IndexerSettingsPanelTest.java:60`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: the host row control ids `settings:<key>:editor`, `:entry`, `:new` and `:add` around the `settings:<key>`
  ORDERED list of `item-N` rows; the testkit's `KitSettingRows.row(SettingSpec, Object, String, String, String entry)`.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 7 step 1: stringList host rows are the ordered-list editor, in the fake and the host."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeKitMountingTest.java", [
    (r'''                view.get().emit(new UiEvent.Edited("settings:list","one\ntwo"));
''', r'''                view.get().emit(new UiEvent.Removed("settings:list","item-0"));
                view.get().emit(new UiEvent.Edited("settings:list:new","one"));
                view.get().emit(new UiEvent.Action("settings:list:add"));
                view.get().emit(new UiEvent.Edited("settings:list:new","two"));
                view.get().emit(new UiEvent.Action("settings:list:add"));
''', 1),
    (r'''    @Test void rowsKeepTheSearchHighlightThePluginAskedForThroughHostProjection() throws Exception {
''', r'''    /** Spec 13: a stringList host row is the generated pages' ordered-list editor, here as in the host. */
    @Test void aStringListRowIsTheOrderedListEditor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var host=new FakePluginHost()) {
                var view=new AtomicReference<FakeKitView>();var rows=new AtomicReference<SettingsPanelHost>();var words=new AtomicReference<Setting<List<String>>>();
                host.start(new PluginInfo("test.list","List","1.0.0",Set.of()),Set.of(),Set.of(),context -> {
                    words.set(context.settings().declare(SettingSpec.of("words",SettingKind.stringList(word -> !word.contains("/")),List.of("a","b")).label("Words")));
                    context.settings().page((SettingsPanel) h -> {
                        rows.set(h);
                        var made=(FakeKitView)context.kit().create(new ViewSpec(new Nodes.Card("card",ControlState.READY,Nodes.CardKind.SETTINGS,List.of(h.row(words.get())))),
                            event -> {throw new AssertionError("Owned setting event leaked");});
                        view.set(made);return made;
                    });
                });
                host.settingsPanel("test.list");
                var editor=editor(view.get());
                assertThat(editor.id()).isEqualTo("settings:words:editor");
                assertThat(editor.children().getFirst()).isInstanceOfSatisfying(Nodes.ItemList.class, list -> {
                    assertThat(list.id()).isEqualTo("settings:words");
                    assertThat(list.kind()).isEqualTo(Nodes.ListKind.ORDERED);
                    assertThat(list.items()).extracting(ListItem::id).containsExactly("item-0","item-1");
                });
                assertThat(entry(view.get()).id()).isEqualTo("settings:words:new");
                view.get().emit(new UiEvent.Reordered("settings:words","item-1",0));
                assertThat(rows.get().pending(words.get())).containsExactly("b","a");
                view.get().emit(new UiEvent.Removed("settings:words","item-9"));
                view.get().emit(new UiEvent.Reordered("settings:words","item-0",7));
                assertThat(rows.get().pending(words.get())).as("a row that is gone moves nothing").containsExactly("b","a");
                view.get().emit(new UiEvent.Edited("settings:words:new","x/y"));
                view.get().emit(new UiEvent.Action("settings:words:add"));
                assertThat(rows.get().pending(words.get())).as("a rejected value is not staged").containsExactly("b","a");
                assertThat(entry(view.get()).value()).as("the entry keeps the text to correct").isEqualTo("x/y");
                assertThat(((Nodes.ItemList)editor(view.get()).children().getFirst()).state().error()).isNotBlank();
                assertThatThrownBy(() -> host.applySettingsPanel("test.list")).isInstanceOf(IllegalStateException.class);
                view.get().emit(new UiEvent.Edited("settings:words:new","c"));
                view.get().emit(new UiEvent.Action("settings:words:add"));
                assertThat(rows.get().pending(words.get())).containsExactly("b","a","c");
                assertThat(entry(view.get()).value()).isEmpty();
                assertThat(((Nodes.ItemList)editor(view.get()).children().getFirst()).state().error()).isEmpty();
                host.applySettingsPanel("test.list");
                assertThat(host.savedSettings("test.list")).containsEntry("words",List.of("b","a","c"));
                assertThat(view.get().owner.failures()).isEmpty();
            }
        });
    }
    /** The ordered-list editor in the trailing slot of the card's only host row. */
    private static Nodes.Stack editor(FakeKitView view) {
        var row=(Nodes.SettingRow)((Nodes.Card)view.spec().root()).children().getFirst();
        return (Nodes.Stack)((Nodes.Stack)row.control()).children().getFirst();
    }
    /** The ordered-list editor's entry field. */
    private static Nodes.Field entry(FakeKitView view) { return (Nodes.Field)((Nodes.Stack)editor(view).children().get(1)).children().getFirst(); }
    @Test void rowsKeepTheSearchHighlightThePluginAskedForThroughHostProjection() throws Exception {
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java", [
    (r'''    /** An application text setting labelled as a declared one, to build the generated page's editor beside it. */
''', r'''    /** Spec 13: a plugin's stringList host row is the generated page's ordered-list editor, and edits the same way. */
    @Test void aStringListHostRowIsTheGeneratedPagesOrderedList() throws Exception {
        var view = new AtomicReference<HostedKitView>();
        var rows = new AtomicReference<SettingsPanelHost>();
        var words = new AtomicReference<Setting<List<String>>>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(ID, "Panel", "1.0.0", Set.of()), Set.of(), Set.of(), Set.of(),
            getClass().getClassLoader(), () -> plugin -> {
                words.set(plugin.settings().declare(SettingSpec.of("words", SettingKind.stringList(word -> !word.contains("/")), List.of("a", "b"))
                    .label("Words")));
                plugin.settings().page((SettingsPanel) panelHost -> {
                    rows.set(panelHost);
                    var made = (HostedKitView) plugin.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("page",
                        dev.jasper.sdk.ui.ControlState.READY, dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL, List.of(panelHost.row(words.get())))), event -> { });
                    view.set(made);
                    return made;
                });
            })));
        var page = onEdtValue(() -> (PluginPanelPage) host.settingsPages(context).getFirst());
        onEdt(() -> {
            page.component();
            var control = hostControl(view.get(), 0);
            var generated = dev.jasper.app.settingsui.Editors.textList().build().apply(appList("words", "Words"), context);
            generated.show(List.of("a", "b"));
            assertThat(KitValues.toModule(control)).as("the generated page's ordered list")
                .isEqualTo(generated.node("settings:words", KitValues.toModule(control.state())));
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Reordered("settings:words", "item-1", 0));
            assertThat(rows.get().pending(words.get())).containsExactly("b", "a");
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Removed("settings:words", "item-9"));
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Reordered("settings:words", "item-0", 7));
            assertThat(rows.get().pending(words.get())).as("a row that is gone moves nothing").containsExactly("b", "a");
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Edited("settings:words:new", "x/y"));
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Action("settings:words:add"));
            assertThat(rows.get().pending(words.get())).as("a rejected value is not staged").containsExactly("b", "a");
            assertThat(page.valid()).isFalse();
            var editor = (dev.jasper.sdk.ui.Nodes.Stack) hostControl(view.get(), 0);
            var entry = (dev.jasper.sdk.ui.Nodes.Field) ((dev.jasper.sdk.ui.Nodes.Stack) editor.children().get(1)).children().getFirst();
            assertThat(entry.value()).as("the entry keeps the text to correct").isEqualTo("x/y");
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Edited("settings:words:new", "c"));
            view.get().dispatch(new dev.jasper.sdk.ui.UiEvent.Action("settings:words:add"));
            assertThat(rows.get().pending(words.get())).containsExactly("b", "a", "c");
            assertThat(page.valid()).isTrue();
            assertThat(page.modified()).isTrue();
        });
        onEdt(page::dispose);
    }

    /** An application string-list setting labelled as a declared one, to build the generated page's editor beside it. */
    private static dev.jasper.app.settings.Setting<List<String>> appList(String key, String label) {
        return dev.jasper.app.settings.Setting.of(key, dev.jasper.app.settings.SettingKind.stringList(value -> true), List.<String>of())
            .label(label).build();
    }

    /** An application text setting labelled as a declared one, to build the generated page's editor beside it. */
''', 1)])
edit("plugins/indexer/src/test/java/dev/jasper/indexer/ui/IndexerSettingsPanelTest.java", [
    (r'''                view.emit(new UiEvent.Edited("settings:excludes", ".git\nnew-hidden"));
''', r'''                view.emit(new UiEvent.Edited("settings:excludes:new", "new-hidden"));
                view.emit(new UiEvent.Action("settings:excludes:add"));
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests dev.jasper.sdk.testing.FakeKitMountingTest :jasper-app:test --tests dev.jasper.app.plugins.PluginPanelPageTest --continue`
Expected: FAIL. `aStringListRowIsTheOrderedListEditor` fails with a `ClassCastException` (the row's control is a
TEXTAREA `Field`, not a `Stack`); `independentFakeRowsUseDeclaredKindsValidationAndNestedProjection` fails because
the list events are not handled: they reach the plugin's handler, whose "Owned setting event leaked" the fake view
records as a failure, and the saved list stays `[before]`; `aStringListHostRowIsTheGeneratedPagesOrderedList`
fails on the node comparison (a TEXTAREA field against the generated `Stack`).

- [ ] **Step 3: Render and edit the list as the generated pages do**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 7 step 3: the ordered-list editor in the host's and the testkit's host rows."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/plugins/KitSettingRows.java", [
    ("    private final Map<String, String> errors = new HashMap<>(), edits = new HashMap<>();\n",
     "    private final Map<String, String> errors = new HashMap<>(), edits = new HashMap<>(), entries = new HashMap<>();\n"
     "    /** An ordered list's entry field and Add button are its control id followed by these. */\n"
     "    static final String ENTRY=\":new\",ADD=\":add\";\n", 1),
    ("    void reset(){errors.clear();edits.clear();}", "    void reset(){errors.clear();edits.clear();entries.clear();}", 1),
    ("        }else if(PluginSettingsPage.Hint.of(spec).font() && spec.kind() instanceof SettingKind.Text){\n",
     "        }else if(spec.kind() instanceof SettingKind.StringList){\n"
     "            // As the generated pages (Editors.textList, spec 13): the ordered list, then an entry and Add below it.\n"
     "            control=list(id,state,spec.label(),(List<?>)value,entries.getOrDefault(id,\"\"));\n"
     "        }else if(PluginSettingsPage.Hint.of(spec).font() && spec.kind() instanceof SettingKind.Text){\n", 1),
    (r'''            String text=value instanceof List<?> list?String.join("\n",list.stream().map(Object::toString).toList())
                :spec.kind() instanceof SettingKind.NumberRange && value instanceof Number number?Float.toString(number.floatValue()):value.toString();''',
     r'''            String text=spec.kind() instanceof SettingKind.NumberRange && value instanceof Number number?Float.toString(number.floatValue()):value.toString();''', 1),
    ("            var kind=numeric.isPresent()?Nodes.FieldKind.NUMBER:spec.kind() instanceof SettingKind.StringList?Nodes.FieldKind.TEXTAREA:PluginSettingsPage.Hint.of(spec)==PluginSettingsPage.Hint.FILE?Nodes.FieldKind.PATH:Nodes.FieldKind.TEXT;",
     "            var kind=numeric.isPresent()?Nodes.FieldKind.NUMBER:PluginSettingsPage.Hint.of(spec)==PluginSettingsPage.Hint.FILE?Nodes.FieldKind.PATH:Nodes.FieldKind.TEXT;", 1),
    ("    /** As the generated pages' font editor: free text over the installed families, which filter as you type. */\n",
     r'''    /**
     * The generated pages' ordered-list editor ({@code Editors.textList()}) node for node: the rows in order as
     * {@code item-N}, then an entry field and Add, which is enabled while the entry holds text.
     */
    private static Nodes.Stack list(String id,ControlState state,String label,List<?> items,String pending){
        var plain=new ControlState(state.visible(),state.enabled(),state.busy(),"");
        var rows=new ArrayList<ListItem>();
        for(int i=0;i<items.size();i++)rows.add(new ListItem("item-"+i,Optional.empty(),
            List.of(new Nodes.Text(id+":item-"+i,ControlState.READY,items.get(i).toString(),Nodes.TextRole.BODY)),List.of(),List.of()));
        var entry=new Nodes.Field(id+ENTRY,plain,Nodes.FieldKind.TEXT,pending,"Add…","Add to "+label,pending.isBlank()?"":id+ADD);
        var add=new Nodes.Button(id+ADD,new ControlState(state.visible(),state.enabled()&&!pending.isBlank(),state.busy(),""),"Add","add",
            Nodes.ButtonKind.SECONDARY,id+ADD,"");
        return new Nodes.Stack(id+":editor",plain,Nodes.LayoutRole.VERTICAL,List.of(new Nodes.ItemList(id,state,Nodes.ListKind.ORDERED,rows,Set.of(),"",List.of()),
            new Nodes.Stack(id+":entry",plain,Nodes.LayoutRole.INLINE,List.of(entry,add))));
    }
    /**
     * An ordered list's entry, Add, Remove and reorder, as the generated pages' editor: the entry's text is kept until an
     * Add stages it; a value the kind rejects keeps the entry and shows the row's error; a stale row changes nothing.
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    private boolean listEvent(UiEvent event){
        String id=event.id().endsWith(ENTRY)||event.id().endsWith(ADD)?event.id().substring(0,event.id().length()-ENTRY.length()):event.id();
        var setting=controls.get(id);
        if(setting==null||!(specs.apply(setting).kind() instanceof SettingKind.StringList))return false;
        var items=new ArrayList<Object>((List<?>)host.pending(setting));
        switch(event){
            case UiEvent.Edited edited when edited.id().equals(id+ENTRY) -> { entries.put(id,edited.text()); return true; }
            case UiEvent.Action action when action.id().equals(id+ADD) -> {
                String text=entries.getOrDefault(id,"");
                if(text.isBlank())return true;
                items.add(text);
            }
            case UiEvent.Removed removed when removed.id().equals(id) -> {
                int index=itemIndex(removed.itemId(),items.size());
                if(index<0)return true;
                items.remove(index);
            }
            case UiEvent.Reordered moved when moved.id().equals(id) -> {
                int index=itemIndex(moved.itemId(),items.size());
                if(index<0||moved.destination()<0||moved.destination()>=items.size())return true;
                items.add(moved.destination(),items.remove(index));
            }
            default -> { return false; }
        }
        try{host.stage((Setting)setting,List.copyOf(items));errors.remove(id);if(event instanceof UiEvent.Action)entries.remove(id);}
        catch(RuntimeException failure){errors.put(id,failure instanceof IllegalArgumentException && failure.getMessage()!=null?failure.getMessage():"Enter a valid value.");}
        host.changed();return true;
    }
    /** The index an ordered list's {@code item-N} row id names, or -1 for a row that is gone. */
    private static int itemIndex(String itemId,int size){
        if(!itemId.startsWith("item-"))return -1;
        try{int index=Integer.parseInt(itemId.substring("item-".length()));return index<size?index:-1;}
        catch(NumberFormatException notARow){return -1;}
    }
    /** As the generated pages' font editor: free text over the installed families, which filter as you type. */
''', 1),
    ("    boolean event(UiEvent event){\n", "    boolean event(UiEvent event){\n        if(listEvent(event))return true;\n", 1),
    ('                    case SettingKind.StringList ignored -> edited.text().isEmpty()?List.of():List.of(edited.text().split("\\n",-1));\n', "", 1)])
TK = "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/"
edit(TK + "KitSettingRows.java", [
    ("    static Nodes.Node row(SettingSpec<?> spec, Object value, String invalidText, String error) {",
     "    static Nodes.Node row(SettingSpec<?> spec, Object value, String invalidText, String error, String entry) {", 1),
    ("        } else if (spec.kind() instanceof SettingKind.Text && spec.editor().orElse(null) instanceof EditorHint.Font font) {\n",
     "        } else if (spec.kind() instanceof SettingKind.StringList) {\n"
     "            // As the host: the generated pages' ordered list, then an entry and Add below it.\n"
     "            input = list(id, state, spec.label(), (List<?>) value, entry);\n"
     "        } else if (spec.kind() instanceof SettingKind.Text && spec.editor().orElse(null) instanceof EditorHint.Font font) {\n", 1),
    (r'''            var kind = numeric.isPresent() ? Nodes.FieldKind.NUMBER : value instanceof List<?> ? Nodes.FieldKind.TEXTAREA
                : spec.editor().orElse(null) instanceof EditorHint.File ? Nodes.FieldKind.PATH : Nodes.FieldKind.TEXT;
            String text = invalidText != null ? invalidText : value instanceof List<?> items
                ? String.join("\n", items.stream().map(Object::toString).toList()) : String.valueOf(value);''',
     r'''            var kind = numeric.isPresent() ? Nodes.FieldKind.NUMBER
                : spec.editor().orElse(null) instanceof EditorHint.File ? Nodes.FieldKind.PATH : Nodes.FieldKind.TEXT;
            String text = invalidText != null ? invalidText : String.valueOf(value);''', 1),
    ("    /** The installed families a font setting's row offers, read once: all, and those that draw 'i' and 'W' alike. */\n",
     r'''    /** As the host: the generated pages' ordered-list editor, its rows {@code item-N}, then the entry and Add. */
    private static Nodes.Stack list(String id, ControlState state, String label, List<?> items, String pending) {
        var plain = new ControlState(state.visible(), state.enabled(), state.busy(), "");
        var rows = new ArrayList<ListItem>();
        for (int i = 0; i < items.size(); i++)
            rows.add(new ListItem("item-" + i, Optional.empty(),
                List.of(new Nodes.Text(id + ":item-" + i, ControlState.READY, items.get(i).toString(), Nodes.TextRole.BODY)), List.of(), List.of()));
        var entry = new Nodes.Field(id + ":new", plain, Nodes.FieldKind.TEXT, pending, "Add…", "Add to " + label, pending.isBlank() ? "" : id + ":add");
        var add = new Nodes.Button(id + ":add", new ControlState(state.visible(), state.enabled() && !pending.isBlank(), state.busy(), ""), "Add", "add",
            Nodes.ButtonKind.SECONDARY, id + ":add", "");
        return new Nodes.Stack(id + ":editor", plain, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.ItemList(id, state, Nodes.ListKind.ORDERED, rows, Set.of(), "", List.of()),
            new Nodes.Stack(id + ":entry", plain, Nodes.LayoutRole.INLINE, List.of(entry, add))));
    }

    /** The installed families a font setting's row offers, read once: all, and those that draw 'i' and 'W' alike. */
''', 1)])
edit(TK + "FakeSettingsPanelHost.java", [
    ('        String invalidText;\n        String error = "";\n',
     '        String invalidText;\n        String error = "";\n        /** An ordered list\'s entry text, kept until an Add stages it. */\n        String entry = "";\n', 1),
    ("        return KitSettingRows.row(handle.spec(), pending(handle), row.invalidText, row.error);",
     "        return KitSettingRows.row(handle.spec(), pending(handle), row.invalidText, row.error, row.entry);", 1),
    ("        Row row = rows.get(id);\n",
     "        Row row = rows.get(id);\n"
     "        for (Row listed : rows.values())\n"
     "            if (listed.setting.spec().kind() instanceof dev.jasper.sdk.settings.SettingKind.StringList && listEvent(listed, event)) return true;\n", 1),
    ('    @SuppressWarnings({"rawtypes", "unchecked"})\n    private void stageValue(',
     r'''    /** As the host's ordered-list row: the entry's text, Add, Remove and reorder; false when the event is not this row's. */
    private boolean listEvent(Row row, UiEvent event) {
        String id = "settings:" + row.setting.key();
        var items = new java.util.ArrayList<Object>((java.util.List<?>) pending(row.setting));
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals(id + ":new") -> { row.entry = edited.text(); refresh(); return true; }
            case UiEvent.Action action when action.id().equals(id + ":add") -> {
                if (row.entry.isBlank()) return true;
                items.add(row.entry);
            }
            case UiEvent.Removed removed when removed.id().equals(id) -> {
                int index = itemIndex(removed.itemId(), items.size());
                if (index < 0) return true;
                items.remove(index);
            }
            case UiEvent.Reordered moved when moved.id().equals(id) -> {
                int index = itemIndex(moved.itemId(), items.size());
                if (index < 0 || moved.destination() < 0 || moved.destination() >= items.size()) return true;
                items.add(moved.destination(), items.remove(index));
            }
            default -> { return false; }
        }
        try {
            stageValue(row.setting, java.util.List.copyOf(items));
            if (event instanceof UiEvent.Action) row.entry = "";
        } catch (RuntimeException invalid) {
            row.error = invalid.getMessage() == null ? "Enter a valid value." : invalid.getMessage();
        }
        changed();
        refresh();
        return true;
    }

    /** The index an ordered list's {@code item-N} row id names, or -1 for a row that is gone. */
    private static int itemIndex(String itemId, int size) {
        if (!itemId.startsWith("item-")) return -1;
        try { int index = Integer.parseInt(itemId.substring("item-".length())); return index < size ? index : -1; }
        catch (NumberFormatException notARow) { return -1; }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stageValue(''', 1)])
edit(TK + "FakeSetting.java", [
    ('                if (value instanceof List<?>) yield text.isEmpty() ? List.of() : List.of(text.split("\\n", -1));\n', "", 1)])
PY
```

The fake's `FakeSetting.edited` and the host's edit switch no longer split text into a list: an ordered list is edited
only through its rows, its entry and Add (Rulings 11).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-sdk-testkit:javadoc :jasper-app:test --tests 'dev.jasper.app.plugins.PluginPanelPageTest' --tests 'dev.jasper.app.plugins.PluginSettings*' --tests 'dev.jasper.app.plugins.*Contract*' --tests dev.jasper.app.appearance.ThemeMigrationCoverageTest :jasper-plugin-indexer:test :jasper-plugin-sample:test :jasper-plugin-editor:test :jasper-plugin-history:test --continue`
Expected: BUILD SUCCESSFUL. The generated History page's `stringList` was already this editor; Project Index's three
lists now are too.

Run: `git grep -n 'TEXTAREA' -- jasper-app/src/main/java/dev/jasper/app/plugins/KitSettingRows.java jasper-sdk-testkit/src/main`
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src jasper-sdk-testkit/src plugins/indexer/src/test
git commit -F - <<'EOF'
Plugin stringList host rows use the ordered-list editor

A plugin page's standard row for a stringList is the generated pages'
ordered list, node for node, in the host and the testkit's fake: rows
move and remove themselves, the entry below adds one, a value the rule
refuses keeps its entry and shows the row's error, and an event for a
row that is gone changes nothing. Project Index's markers, ignored
paths and excluded folders are edited this way.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 8: Project Index's Location column and three recent palette rows

Two one-constant changes of spec 13: Project Index's Location column in the data face (`MONO_SMALL`, Rulings 12) and
`RECENT_ROWS` 2 → 3 (Rulings 13).

**Files:**
- Modify: `plugins/indexer/src/main/java/dev/jasper/indexer/ui/IndexerSettingsPanel.java:140`
- Modify: `jasper-app/src/main/java/dev/jasper/app/palette/PaletteController.java:21`
- Test: `plugins/indexer/src/test/java/dev/jasper/indexer/ui/IndexerSettingsPanelTest.java:65-66`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/PaletteScopesTest.java:106-131`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/CommandPalettePreview.java:248`

**Interfaces:**
- Consumes: nothing.
- Produces: `PaletteController.RECENT_ROWS == 3`.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 8 step 1: the Location column's face and three recents."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("plugins/indexer/src/test/java/dev/jasper/indexer/ui/IndexerSettingsPanelTest.java", [
    ('                    .containsEntry("files", "2").containsEntry("state", "Fresh");\n',
     '                    .containsEntry("files", "2").containsEntry("state", "Fresh");\n'
     '                assertThat(table(view).columns()).filteredOn(column -> column.id().equals("location")).singleElement()\n'
     '                    .satisfies(column -> assertThat(column.role()).as("spec 13: the data face").isEqualTo(Nodes.TextRole.MONO_SMALL));\n', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/workspace/PaletteScopesTest.java", [
    ('                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("beta", "Beta"), PaletteRow.of("gamma", "Gamma"));\n'
     '                var quiet = new FakeScope() {\n',
     '                fake.rows = List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("beta", "Beta"), PaletteRow.of("gamma", "Gamma"),\n'
     '                    PaletteRow.of("delta", "Delta"));\n'
     '                var quiet = new FakeScope() {\n', 1),
    ('for (String row : new String[] {"gamma", "gamma", "gamma", "beta", "alpha"}) {',
     'for (String row : new String[] {"delta", "delta", "delta", "gamma", "beta", "alpha"}) {', 1),
    ('                assertThat(entries.subList(0, 3)).containsExactly("# Recently used", "test.fake/alpha", "test.fake/beta");\n'
     '                assertThat(entries.subList(3, 5)).as("ranked by use, without repeating the recents").containsExactly("# Most used", "test.fake/gamma");\n',
     '                assertThat(entries.subList(0, 4)).as("spec 13: three recent rows")\n'
     '                    .containsExactly("# Recently used", "test.fake/alpha", "test.fake/beta", "test.fake/gamma");\n'
     '                assertThat(entries.subList(4, 6)).as("ranked by use, without repeating the recents").containsExactly("# Most used", "test.fake/delta");\n', 1),
    ('                fake.rows = List.of(PaletteRow.of("beta", "Beta"), PaletteRow.of("gamma", "Gamma"));\n',
     '                fake.rows = List.of(PaletteRow.of("beta", "Beta"), PaletteRow.of("gamma", "Gamma"), PaletteRow.of("delta", "Delta"));\n', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/workspace/CommandPalettePreview.java", [
    ('if(!entries.get(0).equals("# Recently used")||!entries.get(3).equals("# Most used"))throw new AssertionError(entries);',
     'if(!entries.get(0).equals("# Recently used")||!entries.get(4).equals("# Most used"))throw new AssertionError(entries);', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-indexer:test --tests dev.jasper.indexer.ui.IndexerSettingsPanelTest :jasper-app:test --tests dev.jasper.app.workspace.PaletteScopesTest --continue`
Expected: FAIL: the Location column is BODY ("expected: MONO_SMALL but was: BODY"); the palette shows two recents
("# Most used" at index 3).

- [ ] **Step 3: Change the two constants**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 8 step 3: MONO_SMALL Location and RECENT_ROWS 3."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("plugins/indexer/src/main/java/dev/jasper/indexer/ui/IndexerSettingsPanel.java", [
    ('            new Column("location", "Location", Nodes.TextRole.BODY, false, 2.2, Optional.empty()),',
     '            new Column("location", "Location", Nodes.TextRole.MONO_SMALL, false, 2.2, Optional.empty()),  // spec 13: a data value', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/palette/PaletteController.java", [
    ("    static final int RECENT_ROWS = 2, SUGGESTED_ROWS = 10;", "    static final int RECENT_ROWS = 3, SUGGESTED_ROWS = 10;", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-indexer:test :jasper-app:test --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.workspace.Palette*' --tests 'dev.jasper.app.workspace.WindowCommandPalette*' --tests dev.jasper.app.appearance.ThemeMigrationCoverageTest --continue`
Expected: BUILD SUCCESSFUL. If another palette test counts two recent rows, give it a third run row as above and record
the file in the status banner.

- [ ] **Step 5: Commit**

```bash
git add plugins/indexer/src jasper-app/src
git commit -F - <<'EOF'
Project Index's Location in the data face; three recent palette rows

The Location column draws its paths in the data face, as the Files
column draws its counts, and the palette's empty query shows the last
three rows run instead of two, still ten rows in all.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 9: Documentation, spec banners, reference pairs, STATUS and the whole build

The guides say what changed (spec 17, smallest edits); the six specifications spec 19.2 names gain their banner
lines; the jasper-ui inputs are proven unchanged; `./gradlew check --continue` runs everything; every reference pair is
measured against the baseline, and each whose render changed gains a note with its measured edge agreement;
`docs/STATUS.md` gains this plan's entry with the counts from the result XML and the measured pairs; the plan's status
banner records the execution.

**Files:**
- Modify: `docs/remote.md:42-43,171,234,288,299,301,307`, `docs/file-server.md:14`, `docs/credential-vault.md:43,96`,
  `docs/command-palette.md:10,39,157`, `docs/editor.md:10`, `docs/plugin-authoring.md:623-626`, `docs/plugin-ui.md:178`
- Modify: `docs/superpowers/specs/2026-09-15-jasper-snippets-design.md:3`,
  `docs/superpowers/specs/2026-09-21-jasper-palette-plugins-design.md:4`,
  `docs/superpowers/specs/2026-09-22-jasper-remote-design.md:5`,
  `docs/superpowers/specs/2026-09-30-jasper-remote-tunnels-design.md:6`,
  `docs/superpowers/specs/2026-10-13-sdk-clipboard-and-tools-menu-design.md:4`,
  `docs/superpowers/specs/2026-09-22-jasper-vault-design.md:9`
- Modify: `tools/visual/pairs.json` (a `note` on each measured change), `tools/visual/compare_all.py:6,37`, `tools/visual/README.md:13-14`
- Modify: `docs/STATUS.md:2` (a new entry at the top), `docs/superpowers/plans/2026-10-16-enterprise-3e-default-look.md:1` (the status banner)

**Interfaces:**
- Consumes: Tasks 1 to 8.
- Produces: documentation only.

- [ ] **Step 1: Update the guides**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 9 step 1: the guides name section 13's changes (spec 17)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/remote.md", [
    ("The **SSH** menu carries the connection commands plus **New Host...**",
     "The **Session** menu carries the connection commands, the tunnel commands and **New Host...**", 1),
    ("panel's **+** without opening the panel; unbound by default) and **Import from ~/.ssh/config...**.",
     "panel's **+** without opening the panel; unbound by default) and **Import from ~/.ssh/config...**; the **Transfer**\n"
     "menu carries **Open SFTP here** and **Transfers**.", 1),
    ("**SSH > SFTP**", "**Transfer > Open SFTP here**", 1),
    ("**SSH > Transfers**", "**Transfer > Transfers**", 1),
    ("**SSH > Toggle Tunnels**", "**Session > Toggle Tunnels**", 1),
    ("**SSH > New Tunnel...**", "**Session > New Tunnel...**", 1),
    ("**SSH > Start or Stop a Tunnel...**", "**Session > Start or Stop a Tunnel...**", 1),
    ("(a terminal's context menu, the SSH menu or the palette)", "(a terminal's context menu, the Session menu or the palette)", 1)])
edit("docs/file-server.md", [
    ("Press **+** (or **New File Server…**) to add a server.",
     "Press **+** (or **Transfer › Share Folder…** or **New File Server…**) to add a server; **Choose…** picks its folder.", 1)])
edit("docs/credential-vault.md", [
    ("The padlock in the status bar unlocks/opens a locked vault and locks an unlocked vault; its tooltip",
     "The status bar's **Vault: Locked** or **Vault: Unlocked** item unlocks/opens a locked vault and locks an unlocked vault; its tooltip", 1),
    ("leave Jasper inactive and verify the padlock locks.", "leave Jasper inactive and verify the status item reads Vault: Locked.", 1)])
edit("docs/command-palette.md", [
    ("An empty query shows **Recently used**, the last two rows", "An empty query shows **Recently used**, the last three rows", 1),
    ("| Snippets | Paste | Paste and run | Edit file |", "| Snippets | Insert | Paste and run | Edit file |", 1),
    ("opens a fill-in step before Paste or\nPaste-and-run", "opens a fill-in step before Insert or\nPaste-and-run", 1)])
edit("docs/editor.md", [
    ("  palette, and **Open…** inside the Editor window.\n",
     "  palette, and **Open…** inside the Editor window.\n"
     "- **Open File…** in a terminal window's File menu or the palette (no default shortcut) opens the file you choose in\n"
     "  the [default editor](#default-editor), as a Cmd-click does.\n", 1)])
edit("docs/plugin-authoring.md", [
    ("generated from your declarations: rows in declaration order, a\nheading wherever `section` changes,",
     "generated from your declarations: rows in declaration order, a\nheading wherever `section` changes (the page's name heads rows before the first one),", 1),
    # After 3b Task 10 the editor-hint sentence ends with EditorHint.technical(); the new sentence follows it unchanged.
    ("`EditorHint.technical()` (SDK 0.18: a text field in the data face).",
     "`EditorHint.technical()` (SDK 0.18: a text field in the data face). A `stringList` is an ordered list with an entry\n"
     "below it that adds a value, on generated pages and in `host.row` rows alike.", 1)])
edit("docs/plugin-ui.md", [("as New Tunnel's Via Host does.", "as New Tunnel's Via host does.", 1)])
PY
```

If an anchor stops the script because 3b or 3d changed a sentence differently from their plan text (the
`docs/plugin-authoring.md` anchor is the end of the editor-hint sentence as 3b Task 10 leaves it), re-quote the current
text and insert the same words, leaving the existing sentence as it is; record it in the status banner.

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: BUILD SUCCESSFUL (links resolve and compiled examples are unchanged).

- [ ] **Step 2: The spec banners of 19.2**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 9 step 2: spec 19.2's status-banner lines, exactly as worded, after each status paragraph."""
import pathlib
SPECS = pathlib.Path("docs/superpowers/specs")
LINE = ("Amended on 2026-10-15 by the [Enterprise presentation design](2026-10-15-jasper-enterprise-presentation-design.md): "
        "{what}; everything else in this document stands.")
MENUS = "Remote's entries live in the Session and Transfer standard menus"
AMENDMENTS = [
    ("2026-09-15-jasper-snippets-design.md", "**Status:** Implemented on `claude/snippets`", "the first verb reads Insert"),
    ("2026-09-21-jasper-palette-plugins-design.md", "**Status:** Approved 2026-09-21 (user decisions recorded in section 2). Amends section 14 of", "the first verb reads Insert"),
    ("2026-09-22-jasper-remote-design.md", "**Status:** Approved 2026-09-22 (user decisions recorded in section 2). Consumes the Credential Vault", MENUS),
    ("2026-09-30-jasper-remote-tunnels-design.md", "**Status:** Approved in brainstorming 2026-09-30", MENUS),
    ("2026-10-13-sdk-clipboard-and-tools-menu-design.md", "Date: 2026-10-13. Status: approved in conversation", MENUS),
    ("2026-09-22-jasper-vault-design.md", "**Status:** Approved 2026-09-22 (user decisions recorded in section 2). Realises the \"Interop sketch\"",
     "the status item reads Vault: Locked or Vault: Unlocked"),
]
for name, anchor, what in AMENDMENTS:
    path = SPECS / name; text = path.read_text(encoding="utf-8")
    if text.count(anchor) != 1: raise SystemExit(f"{name}: expected 1 of {anchor!r}, found {text.count(anchor)}")
    line = LINE.format(what=what)
    if line in text: raise SystemExit(f"{name}: already amended")
    end = text.index("\n\n", text.index(anchor))
    path.write_text(text[:end] + "\n\n" + line + text[end:], encoding="utf-8")
    print(name, "amended")
PY
```

Expected: six lines ending in "amended". Read each banner once: the new paragraph sits right after the status
paragraph (for the SDK clipboard design, after its Date and Branch lines).

- [ ] **Step 3: Let `compare_all.py` print a pair's note**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 9 step 3: pairs.json entries may carry a note, which compare_all.py prints (Rulings 1)."""
import pathlib
def edit(name, old, new):
    p = pathlib.Path(name); t = p.read_text(encoding="utf-8")
    if t.count(old) != 1: raise SystemExit(f"{name}: expected 1 of {old!r}, found {t.count(old)}")
    p.write_text(t.replace(old, new), encoding="utf-8")
edit("tools/visual/compare_all.py", 'pairs.json entries: {"name", "mock", "crop", "render", "render_crop"?}.',
     'pairs.json entries: {"name", "mock", "crop", "render", "render_crop"?, "note"?}; a note names a deliberate difference.')
edit("tools/visual/compare_all.py", '        print(f"{pair[\'name\']}: {summary}")',
     '        note = f" (note: {pair[\'note\']})" if pair.get("note") else ""\n        print(f"{pair[\'name\']}: {summary}{note}")')
edit("tools/visual/README.md", "- `compare_all.py OUT_DIR [FILTER]` runs every pair in `pairs.json` (mock path, content crop, render path\n  under `*/build/`).",
     "- `compare_all.py OUT_DIR [FILTER]` runs every pair in `pairs.json` (mock path, content crop, render path\n  under `*/build/`, and an optional `note` naming a deliberate difference, which it prints).")
print("compare_all.py and README updated")
PY
```

Run: `python3 -I -c "import ast,sys; ast.parse(open('tools/visual/compare_all.py').read())" && git diff --stat -- tools/visual`
Expected: no error; two files changed (`compare_all.py`, `README.md`).

- [ ] **Step 4: Prove the pixel rule, run the whole build and read the counts**

The headless gallery's inputs are jasper-ui's sources only (`verifyUiArchitecture` forbids other Jasper modules), so
an unchanged jasper-ui tree means a byte-identical gallery. Run from the worktree root:

```bash
git diff --quiet "$(git merge-base HEAD main)" -- jasper-ui && echo "jasper-ui unchanged: the gallery is byte-identical"
```

Expected: `jasper-ui unchanged: the gallery is byte-identical`. If anything under `jasper-ui/` changed, stop: this
plan must not touch it.

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL, including `verifyUiArchitecture`, `verifySdkArchitecture`, `verifyApplicationArchitecture`,
`verifyPluginArchitecture`, the javadoc doclint of the SDK, the testkit, jasper-ui and jasper-app,
`ThemeMigrationCoverageTest`, `PromptVocabularyTest`, `KitMatrixTest`, `PaintedContrastTest` and
`AppDocumentationTest`.

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""The test counts of the last build, from the result XML."""
import pathlib, xml.etree.ElementTree as ET
totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
for path in sorted(pathlib.Path(".").glob("**/build/test-results/test/*.xml")):
    suite = ET.parse(path).getroot()
    for key in totals: totals[key] += int(suite.get(key, "0"))
print(f"{totals['tests']:,} tests, {totals['failures']} failures, {totals['errors']} errors, {totals['skipped']} skipped")
PY
```

Expected: one line such as `10,7xx tests, 0 failures, 0 errors, 25 skipped`; Step 6 writes it into STATUS.

Then check source hygiene (AGENTS.md):

```bash
python3 -I - $(git diff --name-only "$(git merge-base HEAD main)" -- '*.java') <<'PY'
import pathlib, sys
for name in sys.argv[1:]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
PY
```

Expected: no output.

Then measure every reference pair against the baseline taken before Task 1. `check` has just run the capture tests;
the same render commands as the baseline's bring every render up to date. Run from the worktree root:

```bash
./gradlew :jasper-app:themeSurfacePreview :jasper-app:settingsSurfacePreview :jasper-app:settings9bPreview :jasper-app:workspaceSurfacePreview
./gradlew :jasper-app:commandPalettePreview --args="$PWD/jasper-app/build/palette-surfaces --task9c"
./gradlew :jasper-app:test --tests '*StagedAnchoredPickerTest' --tests '*VaultSheetRenderTest' --tests '*StagedAddHostTest' --tests '*StagedFileServerFormTest' --tests '*SessionsCompositionTest'
python3 -I build/plan-3e-pairs/measure.py after
```

Expected: `after: N pairs measured` (the same N), then one line per pair, `same` or
`CHANGED (pixels differ in (x0, y0, x1, y1))`, with its summary before and after; `build/plan-3e-pairs/after/changes.json` keeps them for Steps 5 and 6.
`fileserver-add`, `host-add`, `tunnel-new` and `workspace-palette` should read CHANGED. Then:

- For every other pair that reads CHANGED, open its two renders (`build/plan-3e-pairs/before/renders/<render>` and
  `build/plan-3e-pairs/after/renders/<render>`) at the printed box. If the difference is one of the by-design surfaces
  of the Global Constraints' pixel rule (the Vault status item's text, the Session and Transfer menus, File's Open
  File…, a Settings page's new heading, the Notice, an ordered list, the read-only folder, a sentence-case label), give
  the pair one sentence in Step 5's `EXTRA_NOTES` naming it and add one to Step 6's `DEVIATIONS`. Anything else is a defect:
  stop and find its cause before going on.
- If one of the four reads `same`, its section 13 change lies outside that render: delete it from Step 5's `NOTES`
  and add a sentence saying so to Step 6's `DEVIATIONS`.

- [ ] **Step 5: The reference pairs' notes, as measured**

Run from the worktree root, with `EXTRA_NOTES` and `NOTES` as Step 4 left them:

```bash
python3 -I - <<'PY'
"""Plan 3e Task 9 step 5: each reference pair whose render changed names the change and its measured edge agreement."""
import json, pathlib, re
path = pathlib.Path("tools/visual/pairs.json"); text = path.read_text(encoding="utf-8")
pairs = json.loads(text)
if json.dumps(pairs, indent=2) + "\n" != text: raise SystemExit("pairs.json does not round-trip; stop and inspect it")
changes = json.loads(pathlib.Path("build/plan-3e-pairs/after/changes.json").read_text(encoding="utf-8"))
NOTES = {
    "fileserver-add": "Spec 13 (plan 3e): the label \"Reachable from\" is sentence case and the folder is a read-only field; FTP and writable states show the Warning notice.",
    "host-add": "Spec 13 (plan 3e): the label \"Jump host\" is sentence case.",
    "tunnel-new": "Spec 13 (plan 3e): the labels \"Via host\", \"Listen on\" and \"Forward to\" are sentence case.",
    "workspace-palette": "Spec 13 (plan 3e): the empty query shows three recent rows, so Most used starts one row lower.",
}
EXTRA_NOTES = {}  # pair name -> "Spec 13 (plan 3e): <the by-design change Step 4 found in it>."
NOTES.update(EXTRA_NOTES)
changed = {name for name, change in changes.items() if change["changed"]}
if changed != set(NOTES): raise SystemExit(f"changed but not noted: {sorted(changed - set(NOTES))}; noted but unchanged: {sorted(set(NOTES) - changed)}")
def score(summary):
    found = re.search(r"edge_agreement=([0-9.]+)", summary)
    if not found: raise SystemExit(f"no edge agreement in {summary!r}")
    return found.group(1)
for pair in pairs:
    if pair["name"] in NOTES:
        change = changes[pair["name"]]
        pair["note"] = f"{NOTES[pair['name']]} Edge agreement {score(change['before'])} before, {score(change['after'])} after."
path.write_text(json.dumps(pairs, indent=2) + "\n", encoding="utf-8")
print("notes written:", sorted(NOTES))
PY
```

Expected: `notes written: [...]`, the pairs left in `NOTES` plus any `EXTRA_NOTES`. Run
`python3 -I tools/visual/compare_all.py build/plan-3e-pairs/noted` and check that each noted pair's line ends with its
note.

- [ ] **Step 6: Write the STATUS entry and the status banner**

Run from the worktree root, with `COUNTS` set to the line Step 4 printed and `DEVIATIONS` listing anything an earlier
step told you to record (leave it empty when there is none):

```bash
COUNTS='<the line Step 4 printed>' python3 -I - <<'PY'
"""Plan 3e Task 9 step 6: the STATUS entry at the top and the plan's status banner."""
import datetime, json, os, pathlib, re, subprocess
DEVIATIONS = []  # one sentence each, for example "Task 4: SettingsDialogTest read the first card directly; it now reads the Section's body."
def git(*args): return subprocess.run(["git", *args], capture_output=True, text=True, check=True).stdout.strip()
counts = os.environ["COUNTS"].strip()
if not counts or counts.startswith("<"): raise SystemExit("set COUNTS to the line the count script printed")
base = git("merge-base", "HEAD", "main")
# The plan's own commits (writing, review, re-anchoring) come first and vary in number: find Task 1's by its subject.
TASK1 = "Session and Transfer take Remote's SSH menu and File Server's sharing"
commits = [line.split(" ", 1) for line in git("log", "--reverse", "--format=%h %s", f"{base}..HEAD").splitlines()]
starts = [sha for sha, subject in commits if subject == TASK1]
if len(starts) != 1: raise SystemExit(f"expected one Task 1 commit ({TASK1!r}), found {len(starts)}")
first, last = starts[0], commits[-1][0]
date = datetime.date.today().isoformat()
deviations = " ".join(DEVIATIONS) if DEVIATIONS else "none beyond the plan's Rulings"
changes = json.loads(pathlib.Path("build/plan-3e-pairs/after/changes.json").read_text(encoding="utf-8"))
def score(summary): return re.search(r"edge_agreement=([0-9.]+)", summary).group(1)
measured = ", ".join(f"`{name}` {score(change['before'])} → {score(change['after'])}"
                     for name, change in changes.items() if change["changed"])
entry = f"""**Enterprise presentation, plan 3e: the default-look changes — {date}** (`claude/enterprise-default-look`, worktree
`.claude/worktrees/enterprise-3e`, `{first}..{last}`, then this documentation commit;
[plan](superpowers/plans/2026-10-16-enterprise-3e-default-look.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). Spec section 13, in both looks. Remote's
SSH menu is gone: Connect to SSH Host…, Toggle SSH hosts and Split with Same Host, New Host… and Import from
~/.ssh/config…, and the tunnels group sit in the Session menu, Open SFTP here and Transfers in the Transfer menu,
where File Server adds the new Share Folder… (what the empty File Servers panel's button runs) and New File Server….
File › Open File… (`open_file`, no default binding; also a palette command) asks the plugins' file chooser over the
window for one local file, starting in the pane's folder, and opens it as a Cmd-click does. Vault's status item reads
"Vault: Locked" or "Vault: Unlocked" ("Vault" with no vault); the Snippets scope's first verb reads "Insert" (id
`paste`). A Settings page's leading rows that no section heads take the page's name (Command Palette, Desk Buddy,
Terminal, Cursor, Shell and every generated plugin page, such as Credential Vault), and System Settings opens with
"General". New File Server's warnings are one WARNING Notice and its folder a read-only field that only the separate
Choose… sets. A plugin's `stringList` host row is the generated pages' ordered-list editor, node for node, in the host
and the testkit's fake. Project Index's Location column uses `MONO_SMALL`. Plugin form labels are sentence case (Via
host, Jump host, Reachable from, Listen on, Forward to). The palette's empty query shows three recent rows. The
banners of the Snippets, Palette plugins, Remote, Remote tunnels, SDK clipboard and Tools menu, and Vault
specifications carry spec 19.2's lines. Pixels: no jasper-ui file changed, so the headless gallery is byte-identical;
measured against renders taken before Task 1, the reference pairs whose renders changed, all as section 13 asks, are
{measured} (edge agreement before → after), each with a `note` in `tools/visual/pairs.json`; every other pair's
render is pixel-identical. Rulings (plan, "Rulings"): the reference pairs keep their mocks and gain
notes; Session and Transfer keep the SSH menu's order; Share Folder… runs what New File Server… runs; Open File…
opens nothing before plugins start; "Vault" with no vault; the verb id stays `paste`;
one `SchemaPage` rule titles leading groups, plugin-built pages excepted; one Notice joins the warnings; the folder is
set only through the chooser, in tests too; sentence case covers exactly the five labels, and jasper-ui fixtures keep
their Title Case copies; the host list is `Editors.textList()`'s node; Location takes `MONO_SMALL`; `SUGGESTED_ROWS`
stays 10; the banner lines follow each status paragraph. Deviation from the spec (spec 11.7 and 13, awaiting the
user's ruling): File › Open File… shows Jasper's own chooser, which every other file pick uses, for "the native
chooser"; the app has no native file dialog (Rulings 4). Deviations from the plan's text: {deviations}. Hand-offs:
plan 9's classic Settings compositions draw the new leading headings as titled group boxes; plan 10's classic File
Servers table reuses Share Folder… for its empty state; plan 8's Browse… runs `ActionId.OPEN_FILE`.
`./gradlew check --continue`: **{counts}**. GUI checks for the user: the menu bar reads File, Edit, View, Pane, Tab,
Session, Transfer, Tools, … with no SSH menu, and every former SSH entry works from Session or Transfer; File › Open
File… opens a chosen file in the default editor and Cancel opens nothing, and the user rules on Jasper's chooser
there (Rulings 4); the Vault status item reads Vault: Locked
and Vault: Unlocked as you lock and unlock; New File Server with FTP shows the Warning notice, and Choose… sets the
read-only folder; Settings › Command Palette, Terminal and System Settings show their first headings; Settings › Tools
› Project Index edits its lists as ordered lists; the palette's empty query shows three recent rows. Not merged, not
pushed.

"""
status = pathlib.Path("docs/STATUS.md"); text = status.read_text(encoding="utf-8")
header = "# Jasper — Status and Handoff\n\n"
if not text.startswith(header): raise SystemExit("docs/STATUS.md: unexpected first lines")
status.write_text(header + entry + text[len(header):], encoding="utf-8")
plan = pathlib.Path("docs/superpowers/plans/2026-10-16-enterprise-3e-default-look.md"); body = plan.read_text(encoding="utf-8")
title = "# Enterprise presentation 3e: the default-look changes Implementation Plan\n"
if not body.startswith(title): raise SystemExit("plan: unexpected title")
banner = (f"\n> **Status:** executed {date} (`{first}..{last}`). Deviation from the spec (spec 11.7 and 13, awaiting the user's "
          f"ruling): File › Open File… shows Jasper's own chooser for \"the native chooser\" (Rulings 4). Deviations from the "
          f"text: {deviations}; `./gradlew check --continue`: {counts}.\n")
plan.write_text(title + banner + body[len(title):], encoding="utf-8")
print("STATUS entry and banner written for", f"{first}..{last}")
PY
```

Expected: `STATUS entry and banner written for <first>..<last>`. Read the new entry once: it sits above the previous
top entry (plan 3b's at `149cd8fb9`), names the right range and lists the measured pairs.

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: BUILD SUCCESSFUL (the STATUS entry's and the banners' links resolve).

- [ ] **Step 7: Commit**

```bash
git add docs tools/visual
git commit -F - <<'EOF'
Default-look changes: documentation, spec banners and status

The Remote, File Server, Credential Vault, command palette, Editor and
plugin guides name the Session and Transfer menus, Share Folder, Open
File, the Vault status text, Insert, three recents, sentence-case labels
and ordered-list host rows. The six specifications spec 19.2 names carry
their amendment lines, the reference pairs whose renders changed carry
notes with their measured edge agreement, and STATUS records plan 3e
with the whole build's counts.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

Then stop: do not push or merge. The user reviews the branch and the GUI checks above.

## Execution notes (2026-10-16)

Re-anchored at `149cd8fb9` (plans 3a, 3b and 3d merged; 3c running in parallel in `.claude/worktrees/enterprise-3c`).
Every quoted anchor, path and signature above was checked against the code there, and every edit script of Tasks 1 to
8 and Task 9 Steps 1 to 3 was applied, in order, to an archive copy of the branch with no misses (Verified facts 24).
The APIs the tasks consume exist as quoted: `StandardMenu.SESSION`/`TRANSFER`, `Menus.standard`,
`MenuTarget.standard(Slot.SESSION)`, the fake's `"SESSION"`/`"TRANSFER"` keys; `Nodes.Notice` with its four-argument
constructor and `actions()`; the 13-argument `Nodes.Field` (`…, presentation, alternateAction, cancelAction, listId,
readOnly`) and the fake's drop of an `Edited` sent to a read-only field; `PathChoice`'s nine components;
`Location.isLocal`/`localPath`; `AuxiliarySurface.Shell`'s six components; the seven-argument `Nodes.ItemList`
convenience; `Editors.Items` node for node as Task 7 mirrors it; `PluginPanelPageTest.hostControl` (added by 3b);
`IndexerSettingsPanelTest.table`; `PaletteVerb`'s two-argument constructor (3d added `role`). Only line numbers moved;
they are updated in place. Shared-file and machine notes follow the table.

### Pre-flight scan

| Pair or task | Shared file or interface | Conflict? | Ruling |
|---|---|---|---|
| Task 1 / Task 5 | `FileServerPluginTest.java` | No: Task 1 inserts a test before `keyFilesSkipsLargeFilesAndFolders` (`:219`), Task 5 rewrites `:276` | E4 |
| Task 1 / Task 2 | the menu bar (Session, Transfer from 3d; File gains Open File…) | No: Task 1 adds plugin sections, Task 2 edits `WindowChrome`'s File list only | none needed |
| Task 1 / Task 9 | `docs/remote.md`, `docs/file-server.md`, the Remote, Remote tunnels and SDK clipboard banners | No: Task 9 documents what Task 1 did | none needed |
| Task 2 / Task 5 | `AuxiliaryWindows` | No: Task 2 adds `openFile`; Task 5 builds `AuxiliaryWindows` through the existing three-argument constructor in `AppContractTest.headlessWindows(PathChooser)`, a new overload beside `headlessWindows()` and `headlessWindows(UiState)` that `onEdtValue(AppContractTest::headlessWindows)` (a `Supplier`) still resolves to the no-argument one | none needed |
| Task 2 / Task 8 | the palette (`open_file` becomes a command; `RECENT_ROWS` 3) | No: `CommandPalettePreview` fills its ten rows from history, so the new command adds no filler row; `workspace-palette` is measured in Task 9 either way | E9 |
| Task 3 / Task 9 | `docs/credential-vault.md`, `docs/command-palette.md`, the Snippets, Palette plugins and Vault banners | No | none needed |
| Task 4 / Task 7 | plugin settings pages (`PluginSettingsPageTest` generated pages; `PluginPanelPageTest` plugin-built pages) | No: Task 4's rule lives in `SchemaPage` (Rulings 7), Task 7's in the host rows; Task 4 Step 4 runs `PluginPanelPageTest` before Task 7 changes it | none needed |
| Task 4 / Task 9 | `docs/plugin-authoring.md:623-626` | No: two edits in one Task 9 script, applied in order | none needed |
| Task 5 / Task 6 | `ServerEditor.java`, `ServerEditorTest.java` | No: different lines; Task 6's insertion anchor (`protocolChangesPreserveExplicitPortAndHiddenValues`) survives Task 5's insertion before it; no Task 5 test reads the "Reachable From" label | E4 |
| Task 5 / Task 9 | `fileserver-add` pair; `docs/file-server.md` | No: Task 9 measures it (Tasks 5 and 6 both change it) | none needed |
| Task 6 / Task 9 | `docs/plugin-ui.md:178`; `host-add`, `tunnel-new` pairs | No | none needed |
| Task 7 / Task 8 | `IndexerSettingsPanelTest.java` | No: Task 7 rewrites `:60`, Task 8 inserts after `:65-66` | E4 |
| Task 7 / Task 9 | `docs/plugin-authoring.md:626` | No | none needed |
| Task 8 / Task 9 | `docs/command-palette.md:10`; `workspace-palette` pair | No | none needed |
| Task 1 (alone) | Step 4 expects one test line naming `remote.menu` | Agrees (dry run: `RemotePluginTest.java:93`) | none needed |
| Task 2 (alone) | Files list, Interfaces, Step 1 to Step 4 | Agrees: `fileChooser`, `openFile()` and `AuxiliaryWindows.openFile(UUID, Optional<Path>)` are used as declared; `OPEN_FILE` before `SPLIT_RIGHT` shifts the `SELECT_TAB_*` ordinals equally, which `update` compares only with each other | none needed |
| Task 3 (alone) | `SnippetsScopeTest` stays a non-candidate | Agrees: the new test imports nothing from `dev.jasper.sdk.ui` | none needed |
| Task 4 (alone) | `everyCorePagesFirstGroupIsTitled` lists all ten core pages | Agrees with `CorePages.java:94-200` | E10 |
| Task 5 (alone) | Step 4's "no test types a folder" grep | Agrees (dry run: no output) | none needed |
| Task 6 (alone) | Step 4's label scan | Agrees: at `149cd8fb9` it prints exactly the five labels, after Step 3 nothing | none needed |
| Task 7 (alone) | Rulings 11 versus Step 3's code for an `Edited` of the list's own id; Step 2's expected failure of the existing fake test | Text disagreed with the code; corrected | E7, E8 |
| Task 8 (alone) | `PaletteScopesTest` third recent; `CommandPalettePreview` index 4 | Agrees: ten history rows give three recents then Most used at index 4 | none needed |
| Task 9 (alone) | Step 6's "above the previous top entry" | Corrected: the top entry at `149cd8fb9` is plan 3b's | E3 |
| 3e / 3c: `Nodes.ItemList` | Task 7 builds the seven-argument convenience; 3c adds `ItemList.emptyText` | No compile conflict: 3c keeps every existing constructor as a convenience with new components last and empty | E2 |
| 3e / 3c: shared files | `docs/STATUS.md`, `docs/plugin-ui.md`, the testkit fake, `migration.csv`, `docs/plugin-authoring.md`, `KitValuesTest.java` | Only `docs/STATUS.md` overlaps textually | E3 |

### Rulings

E1. **No rebase and nothing from 3c.** The Workspace check drops `git rebase main` and the `cellNodes` check: the branch
already sits on `main` at `149cd8fb9`, and this plan's base stays there. Spec 13 needs only 3b's Notice and
`Field.readOnly` and 3d's Session and Transfer menus. No task uses `Tree.columns`, `TableRow.cellNodes`/`heading`,
`Table.empty`/`emptyText` or `ItemList.emptyText`: Project Index's Location column changes its `Column` role
(`MONO_SMALL`) and keeps its plain cells, and the host's ordered list mirrors `Editors.textList()`, which has no empty
line today — why: the computed task forbids rebasing, 3c runs in parallel, and section 13 asks for none of 3c's
vocabulary — cost if wrong: a reviewer who wants an empty line on the host lists or on Project Index's table gets it
in plans 9 and 10, and `aStringListHostRowIsTheGeneratedPagesOrderedList` makes the host list follow
`Editors.textList()` if that ever gains one.

E2. **Task 7's `ItemList` survives 3c's merge.** It uses the seven-argument convenience, which 3c keeps, with
`emptyText` defaulting to `""` on both sides of the node comparison — why: 3c's Global Constraints keep existing
constructors with new components last and empty — cost if wrong: one compile error at the second merge, fixed there.

E3. **Shared files are reconciled by the second of the 3c and 3e merges.** `docs/STATUS.md`: both plans add a top
entry; keep both, newest first (Task 9 Step 6 inserts below the title, above plan 3b's entry at `149cd8fb9`).
`migration.csv`: this plan adds no row (it creates no source file). `docs/plugin-ui.md`: this plan edits `:178`, 3c
inserts a bullet after `:182`; they should merge cleanly, otherwise keep both. The testkit fake: 3c edits
`FakeKitView.java`, this plan `KitSettingRows.java`, `FakeSettingsPanelHost.java` and `FakeSetting.java`, so nothing
overlaps. `docs/plugin-authoring.md`: this plan only. `KitValuesTest.java:116` keeps its "Via Host" projection fixture
(it is not a plugin form, so Rulings 10 leaves it, as it leaves jasper-ui's fixtures; 3c appends a test to that file)
— why: the computed task's reconciliation rule — cost if wrong: one hand-merged STATUS hunk.

E4. **Tasks run in order 1 to 9.** The file pairs in the table touch disjoint lines and the dry run applied them in
that order; running a later task first could move an anchor — why: one commit per task, in plan order — cost if wrong:
an edit script stops on a moved anchor, which the plan already handles (re-quote and record).

E5. **The Ruling 4 deviation still awaits the user.** Re-anchoring changes nothing about it: File › Open File… uses
`AuxiliaryWindows.choose` (Jasper's chooser, `NativeShells.java:378`), and the STATUS entry and banner record the
deviation — cost if wrong: as Rulings 4 says.

E6. **Doclint wording.** The Global Constraint now says the SDK and jasper-app run `-Xdoclint:all` and the testkit's
javadoc uses the JDK's default doclint (`jasper-sdk-testkit/build.gradle.kts` sets no option); Task 7 Step 4's
`:jasper-sdk-testkit:javadoc` still runs, and the new comments carry no raw angle brackets or ampersands either way —
cost: none.

E7. **An `Edited` of an ordered list's own id stages nothing but marks the row.** In the host, Step 3 removes the
`StringList` case, so such an edit reaches the edit switch's `default` ("Unexpected edit") and sets the row error; in
the fake, `FakeSetting.edited` no longer splits text, so the conversion fails and sets the row error. Rulings 11
("no longer stages anything") holds; no kit control emits an `Edited` for an `ItemList` id, so only a malformed event
shows the error — why: refusing a value the editor never produces is safer than ignoring it silently — cost if wrong:
one `return true` in each `listEvent` for an `Edited` of the list's own id.

E8. **Task 7 Step 2's expected failure, corrected.** `FakeKitView.emit` catches what the plugin's handler throws and
counts it as a view failure, so `independentFakeRowsUseDeclaredKindsValidationAndNestedProjection` fails on the saved
list (`[before]`, not `[one, two]`), not with an `AssertionError` reading "Owned setting event leaked". The step's text
now says so — cost: none.

E9. **`open_file` in the palette changes no expected palette rows.** `CommandPalettePreview` runs ten history rows, so
the empty query has no Suggested filler, and `PaletteScopesTest` asserts only the leading recent and Most used rows. If another render shows the
command, Task 9 Step 4 measures it and the pixel rule's by-design list covers it ("File's Open File…") — cost: one
EXTRA_NOTES line.

E10. **3b's settings `NoticeItem`s and the leading-heading rule.** A `NoticeItem` before a page's first section would
join the leading run and take the page's name as its heading. No page has one: System Settings' VM notices sit under
"Java Virtual Machine", after the new "General" section — why: the rule titles a leading card, whatever it holds —
cost if wrong: one more `heading` condition if a future page starts with a notice.

E11. **Concurrent builds on this machine.** Another plan's Gradle builds run at the same time. If a timing-sensitive
test fails once, re-run that test alone before treating it as a failure, and record a confirmed flake in the status
banner. Never run `./gradlew --stop`, which would stop the other sessions' daemons — cost: one re-run.
