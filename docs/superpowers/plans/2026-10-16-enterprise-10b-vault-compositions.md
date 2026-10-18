# Enterprise presentation 10b: the Credential Vault composition Implementation Plan

> **Status: executed on 2026-10-18** (`6cd4d80cf..5c6808c56`, then the documentation commit); see `docs/STATUS.md`. Deviations from the plan's text: Execution notes R1 to R20; beyond them the tasks' host follow-ups to meet the 07 mocks (Task 3: the bar's menu button at the panel action size, its search a field tall, `DOCUMENT_RULE_GAP` between a classic document split's bar and body, a classic window form's footer band; Task 4: the table at its list column's header band on `surface.card`, a 20 px `surface.zebra` text footer, the classic rail tree's disclosure; Task 5: the manager detail grid (`DETAIL_GRID_INSET`, `DETAIL_GRID_TOP`, `ClassicGroupFace.GRID_EDGE`), classic `DETAIL_PADDING` and `DETAIL_SECTION_GAP` 12, `ICON_SWATCH`, and every Fields row's Copy through `CopyCards.plain`; Task 6: a sheet in a classic dialog's card starts at `FORM_TOP`, classic `SHEET_SECTION_GAP` 10, the group rules sampled at x 44 (the plan's x 34 crosses the render's rounded corners) with the first rule at 18, not 16 (R13 residual); Task 7: the classic compact prompt's `PROMPT_COMPACT_WIDTH` 400, `PROMPT_SIDES` and `PROMPT_TOP` 14, `PROMPT_COMPACT_INTRO_GAP` 12, `PROMPT_FIELD_HEIGHT` 22 and the footnote gap over a classic band); the Details band sampled at x 1085, not 1086 (R4); the Unlock tile measures 30 × 30 in the mock's crop, not 35; Task 10's gallery comparison finds every PNG unchanged and three component-tree dumps per theme renamed (`SecretFields$SecretControl$3` to `$2`).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** With Enterprise turned on, the Credential Vault composes the 07 Credential Vault mocks (spec 14.8): the classic
manager (a toolbar row with the + New menu, the selection's verbs, Lock Vault and the search field; a Categories tree with
counts; a sortable item table with its footer; a Details pane with its Fields group and Properties; a status row with
Done), its ATTACHED_SHEET editors (Add Login, Add SSH Key, Add Secure Note) with their swatches, and the Unlock Vault
PROMPT overlay; every default-look gesture of the manager keeps a classic path.

**Architecture:** As in plan 10a: the Vault reads `dev.jasper.vault.workstation.Layout` once in `start` (10a Task 1) and
hands it to its composition roots (`VaultManagerWindow`, `ManagerPanel`, `EntryPresenter`, `EditorForm`, `PasswordPanel`),
which keep it for their life and build the rows composition or the classic one. Three host gaps the mocks need are closed
in jasper-ui behind face pairs read from existing control points, each default face holding today's code: the rules
(a separator's size and colour, a document split's hairline, a manager column's edge, and whether a manager column's
header is a header strip) follow the `buttons` point; the reveal of a secret (a "Show" checkbox beside an input, "Show
password" under a prompt's field, "Show" and "Copy" buttons on a stored value) follows the `toggle` point. A document
split also fills with a Split child, and the catalog gains the bar's search width and the classic manager's widths. The
renders run the staged Vault in the real plugin host under the shipped Enterprise kit, Plex and theme and compare each
surface with its mock crop through 10a's `MockMatch`.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`), Pillow and NumPy only for the opt-in
`tools/visual` comparison.

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1, 3, 5.2, 6.4, 8.2, 10.7, 10.8, 14.3, 14.7, 14.8, 16.1.4, 16.1.9, 17, 18 and row 10 of 19.1, as amended on
2026-10-15). The second of row 10's two plans; [10a](2026-10-16-enterprise-10a-plugin-compositions.md) executes first on
the same branch, and its rulings, harness and `Layout` lookup are used here by name. Mocks:
[`07 Credential Vault`](../../design/enterprise/reference/07%20Credential%20Vault) (2× PNGs, Light only; names, paths, dates
and counts are fixtures; "Remember in Keychain" is out of scope, spec 18).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-10`, branch
`claude/enterprise-plugin-compositions`, after plan 10a's last commit (the branch is `main` at `f866788de`, every other
plan merged). Run every command from the worktree root. Line numbers are from `f866788de` (refreshed on 2026-10-17, see
"Execution notes (2026-10-17)"). Every edit quotes the text it replaces
and the edit scripts stop with a message when that text is not found the stated number of times: re-find the quoted
text, adjust the anchor and re-run; never edit around a missing anchor.

## Before you start

```bash
git log --oneline -1 --grep 'Plugin compositions: documentation, the visual pairs and status'   # 10a's last commit
ls plugins/vault/src/main/java/dev/jasper/vault/workstation/Layout.java \
   jasper-app/src/test/java/dev/jasper/app/testsupport/MockMatch.java \
   jasper-app/src/test/java/dev/jasper/app/plugins/StagedEnterprise.java \
   jasper-app/src/test/java/dev/jasper/app/plugins/ClassicCompositionRenderTest.java        # 10a Tasks 1, 2, 4
grep -c 'Layout layout() { return layout; }' plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java   # 10a Task 1: 1
grep -c 'final PropertiesFace properties;' jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java       # 10a Task 3: 1
grep -c 'boolean flush()' jasper-ui/src/main/java/dev/jasper/ui/swing/PanelHeaders.java                    # plan 7 Task 10: 1
grep -c 'Nodes.Hue swatch' jasper-sdk/src/main/java/dev/jasper/sdk/ui/DialogSpec.java                      # 3d: at least 1
grep -c 'String footnote' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java                            # 3b Task 8: at least 1
grep -c 'dialogSwatch(String pluginId, String title)' jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java  # 3d: 1
```

Every command prints its line, files or count; if not, execute the missing plan first. The **refresh step** (reading
the STATUS entries of plans 4b, 5b, 6b, 7 and 10a and re-anchoring on them) was done on 2026-10-17: see "Execution notes
(2026-10-17)" at the end, whose rulings bind where they differ from the task text. Plan 6b draws OVERLAY and
ATTACHED_SHEET dialogs in their window (`ClassicDialog`: the header strip with `DialogSpec.swatch` and the title, the
footer band, `KitView.hostTitled` and `dialogBand`) and plan 7 the flush header strip (`PanelHeaders.flush()`,
`StripBorder`). This plan composes the content those draw and reuses plan 7's strip for the manager's pane headers.

## Global Constraints

- Plan 10a's Global Constraints apply unchanged: the toolchain and `./gradlew` (never `--stop`), no GUI and no AOT training (`:jasper-ui:gallery` and the JUnit renders are headless), the worktree and one commit per task ending with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>`, no push or merge, composition at its owner (only `dev.jasper.vault.workstation` names `dev.jasper.enterprise.api`), every action reachable, mock matching, SDK 0.18.0 with no SDK type change, the migration ledger, doclint, the smallest documentation edits, source hygiene and the reserved word "hero".
- Pixel rule: the default look stays byte-identical. Every Vault change is under `Layout.CLASSIC`; under `Layout.ROWS` each node the Vault builds is the one it builds today (the hue the editors pass resolves to `Hue.NONE`, the 4-argument `DialogSpec`'s value). In jasper-ui the default faces call today's code, the new roles' default values are unread (`DOCUMENT_BAR_SEARCH_WIDTH` 0, `SEPARATOR_HEIGHT`) or unchanged, and a document split's new fill applies only to a Split child, which no default view has (`git grep -n 'SplitKind.DOCUMENT'` at `f866788de`: the Editor's tab and its gallery fixture, the File Servers and Tunnels problem bodies and the testkit fixture, none with a Split child). Tasks 1, 2 and 10 prove it with the gallery digests. Task 9's auxiliary frames change only under a look with classic menus (`ShellChrome.classicMenus`), never in the default look.
- The Vault's secret hygiene is unchanged: a secret never enters a descriptor; the classic Details pane shows stored secrets through `Nodes.Secret` DETAIL nodes written with `UiView.replaceSecret`, as the rows composition does (`ManagerPanel.java:195-205`).
- Every new candidate source gets its `docs/design/theme-engine/migration.csv` row in the commit that creates it (Execution notes R6): `ClassicManagerTest` and `ClassicVaultRenderTest` (Task 3), `ClassicEditorsTest` (Task 6), `ClassicUnlockTest` (Task 7) and the Vault's `CompositionParityTest` (Task 8). jasper-ui sources are outside the ledger's scan.

## Review Focus

1. The vault locks while the classic manager shows a selected login and an Add Login sheet is open: the sheet closes and
   erases its draft, the toolbar's Edit…, Delete, Copy Username, Copy Password, Lock Vault, New and the search disable,
   the Details pane is gone, the status row reads "Locked", and the body is the locked state with Unlock. Task 3:
   `ClassicManagerTest.lockingDisablesTheToolbarAndClosesTheSheet`.
2. A search that matches nothing in a category, then another category: the table keeps its four headers over "No
   matching entries", the footer reads "0 of 2 items", and the toolbar's selection verbs are disabled. Task 4:
   `ClassicManagerTest.aSearchThatMatchesNothingKeepsTheHeadersAndCountsZero`.
3. The item table sorted by Modified (twice, descending), then a refresh from the store: the order survives, the
   selection stays on its entry, and exactly one column shows a sort indicator. Task 4:
   `ClassicManagerTest.theSortSurvivesARefreshAndTheSelectionStays`.
4. "Show" on the Details password, then another login selected: the new entry's password shows masked (a reveal never
   carries over to another entry), and Copy copies the selected entry's own password. Task 2 (the host):
   `ClassicRevealTest.aStoredValuesShowIsPerEntry`; Task 5: `ClassicManagerTest.theDetailsPaneFollowsTheSelection`.
5. Unlock Vault with auto-lock off: no footnote and nothing dangling in the footer; with 15 minutes "Auto-locks after 15
   min idle"; Unlock is disabled while the field is empty, also after a wrong password clears it. Task 7:
   `ClassicUnlockTest.theFootnoteFollowsTheAutoLockAndUnlockNeedsAPassword`.

## Verified facts

At `f866788de` (re-verified on 2026-10-17).

1. `ManagerPanel` builds one WINDOW `Form` (`node()`, `ManagerPanel.java:348-356`) whose one section is the body: the
   locked card, the grants card or `entries()` (`:238-264`), a THREE_COLUMN `Split` of the categories `ItemList` (`KIND`,
   ids `all` and the `VaultManager.Type` names), the item `ItemList` (`ROWS`, activate `EDIT`) under the `SEARCH` field and
   `detail()` (`:271-320`); its preview is `status()` (`:361-373`) and its action Done. `actions()` is the window's title
   actions (Lock and the + menu, `:221-230`). `handle` and `act` route `SEARCH`, `KIND`, `ROWS`, `GRANTS`, `EDIT`, `DELETE`,
   `COPY_PUBLIC`, the three `detail.*.copy` actions, `LOCK`, `UNLOCK`, `BACK`, `REVOKE`, `CLOSE`, `add.<TYPE>` and
   `add.generate` (`:128-164`); `render` writes the selected entry's secret with `view.replaceSecret(secretId(), secret)`
   (`:195-205`). `ManagerPanelTest.panel` builds it with the 11-argument constructor (`ManagerPanelTest.java:130-133`).
2. A WINDOW form has exactly one section (`Nodes.java:611-612` in the SDK: "WINDOW requires one body section and no hero").
3. `SplitViews`: a DOCUMENT split's fill is its first visible Custom or Empty child (`SplitViews.java:29-35`, `:101-105`);
   it paints a `border.subtle` hairline at each boundary, inside the bar (`:42-64`, the colour at `:55`); THREE_COLUMN
   widths are `MANAGER_SIDEBAR_WIDTH`, `MANAGER_LIST_WIDTH` and the rest, scaled down only below sidebar + list + sidebar
   (`:83-99`), 192 and 272 in both catalogs today (`compact-roles.tsv:242-243`, `classic-roles.tsv:242-243`). A manager
   column's panel (`Panels.Surface`, `Panels.java:323-350`) fills its column and paints its trailing `border.subtle` edge
   for columns 0 and 1 (`:343`); `ViewReconciler.applyColumns` assigns the columns before any control updates
   (`ViewReconciler.java:35`, `:194-207`, the panel's column at `:197`).
4. `Stacks.DocumentBarLayout` (`Stacks.java:856-930`, `natural` at `:865-868`): in an INLINE stack among a DOCUMENT
   split's bars the first visible field or text fills (`barFields` and `barTexts`, `:65`, set at `:208-211`), other texts
   take at least `DOCUMENT_BAR_STATUS_WIDTH`, other children their preferred width, padded by
   `DOCUMENT_BAR_VERTICAL_PADDING` (4) and `DOCUMENT_BAR_SIDE_PADDING` (6); `ViewReconciler.applyDocumentBars` marks the
   bar's buttons as panel actions and its fields as panel searches (`ViewReconciler.java:247-260`).
5. `SecretFields.SecretControl` creates the reveal and copy `KitButton`s (`SecretFields.java:86-134`), mounts an INPUT
   secret's reveal inside the field surface's east edge and a DISCREET_INPUT's in a slot there (`:103-108`), a DETAIL
   secret's reveal and copy in a `GridLayout` row beside the value (`:194-219`), shows the discreet reveal only while
   focused or filled (`revealVisibility`, `:318-322`), gives the reveal its glyph and texts (`refreshReveal`, `:324-342`)
   and learns it is in a prompt form through `prompt(boolean)` (`:383-387`); `KitView.revealSecrets` reveals a secret and
   its reveal group (`KitView.java:219`). `ControlRegistry.Control` exposes `view`, `node`, `component`, the final
   `context()`, `interactive()` and `own(...)` to the package (`ControlRegistry.java:94-131`). The secret's reveal and
   copy are `KitButton.inner`, which 4b's classic button face keeps flat until hovered only while their kind is a ghost
   (`ClassicButtonFace.flat`), so the classic face's SECONDARY kind draws them as standard buttons.
6. 3b's `Separators.Rule` is `HAIRLINE` wide and `ICON_CONTROL` tall and paints `border.subtle` (3b Task 5 Step 3).
7. `EditorForm.form()` builds a SHEET form of untitled cards and titled `Section`s from `group(...)`, an EXCLUSIVE choice
   row unlabelled (`section`, `:205-212`), and the entry's PROMPT icon and title as its hero (`EditorForm.java:214-231`); `EntryEditor.login`, `key`
   and `note` declare the rows, groups and identity hues BLUE, GREEN and AMBER (`EntryEditor.java:29-115`); `EntryPresenter.show`
   opens every editor as an ATTACHED_SHEET `DialogSpec` (`EntryPresenter.java:15-24`), called by `VaultManagerWindow.dialog`
   (`VaultManagerWindow.java:105-108`) from `add` and `edit` (`:110-135`).
8. `PasswordPanel.unlock(kit, prompt)` (`PasswordPanel.java:54-58`) builds a PROMPT_COMPACT form whose hero is the lock
   tile, "Unlock Vault" and the intro, over one INPUT secret "Master password" (`:152-176`); `VaultPlugin.showUnlock` opens
   it as an OVERLAY "Unlock Vault" when the manager is not open (`VaultPlugin.java:287-302`, the panel at `:294`); a locked
   `dev.jasper.vault.open` asks for the unlock before it shows the manager (`openThen`, `:193-204`); the auto-lock is
   `settings.autoLock()`, 15 minutes by default (`VaultSettings.java:26`).
9. `VaultManagerWindow.show` creates `new WindowSpec(ID, "Credential Vault", new Dimension(900, 555), true,
   WindowSpec.SizeBasis.CONTENT)` and sets the panel's title actions (`VaultManagerWindow.java:56-75`); `changed()` closes
   the editors when the vault is not unlocked (`:77-83`); `VaultPlugin.start` constructs it after reading the layout
   (`VaultPlugin.java:110` reads it into the field `layout`, `:140` constructs the window, 10a Task 1).
10. `StagedVaultHost` runs the staged Vault jar in the real host with headless windows (`StagedVaultHost.java:36-71`),
    `invoke`, `surface`, `awaitSurface`, `createVault`, `invokeSave`, `kitView` (`:78-180`, `:227-240`); `HostedKitView.dispatch`
    delivers an SDK event to the plugin as its kit does (`HostedKitView.java:38`). Its plugin views take their theme from
    the `WorkspaceAppearanceFixture` it is given; without one (`null`) the host's `HostedUi.kitThemes` falls back to the
    bundled default theme (`HostedUi.java:352-357`), so an Enterprise render passes
    `new WorkspaceAppearanceFixture(EnterpriseLook.resolved(false))`, as 10a's `PanelSurfaceFixture` does. Their kit is
    the installed one (`PluginHost.java:100`, `FlatLafInstaller::installedKit`), which `ClassicCompositionRenderTest.under`
    installs.
11. 3c's `TableRow` (8 components with `cellNodes` and `heading`, `TableRow.java:29-30`; a cell node may be an INLINE
    Stack of Icons and Texts) and `Nodes.Table(..., ScrollPolicy, Optional<Empty>, String emptyText)` (`Nodes.java:653`);
    SDK `TreeItem` has `count` (`TreeItem.java:15`) and no hue; `Column.sorted` shows the sort indicator and a header
    click reports `UiEvent.Sorted(id, columnId)` (`Column.java:11`, `UiEvent.java:37`); a tree's expansion reports
    `UiEvent.Expanded(id, itemId, expanded)` (`UiEvent.java:22`).
12. The 07 mocks measure as "Mock measurements" below (re-measured on 2026-10-16 with the band script of plan 10a; the
    toolbar, Categories, Items, sheet and Unlock columns spot-checked again on 2026-10-17).
13. `NativeShells.frame` gives every auxiliary frame the minimal File row (`menuBar`, `NativeShells.java:693-712`: New
    Window, Close Window with ⌘W, Quit off macOS) and, under a classic look (`ShellChrome.classicMenus`), styles it as the
    terminal windows' row (`ShellChrome.styleMenus`, `NativeShells.java:304-308`, `:323`); the Close Window item runs
    `surface.closeCommand()` unless the window records shortcuts (`WindowInput.captureShortcut`). 6a ruling 31 recorded
    the 07 mocks' missing menu row as a residual; 10a R18 handed it to this plan. Headless tests cannot create a `JFrame`;
    `ShellChromeTest` tests the shells' values on a `JRootPane`.
14. Plan 10a's `StrategyIsolationTest` and `VariantReadTest` are jasper-app tests (`dev.jasper.app.architecture`) that scan
    the built jasper-ui jar: the reconciler (`ViewReconciler` and its nested classes) may not name `KitFaces` or any face
    in its constant pool.

## Rulings

"What — why — cost if wrong"; record them in `docs/STATUS.md` (Task 10) and the banner. Rulings 14 is the orchestrator's
ruling of 2026-10-17; the Execution notes' rulings R1 to R20 refine these.

1. **The reveal follows the `toggle` point (`RevealFace`): classic draws an input's reveal as a "Show" checkbox after the
   field, a prompt form's as "Show password" under the field, a discreet input's always, and a stored value's reveal and
   copy as "Show" (or "Hide") and "Copy" standard buttons** — the Add Login, Unlock and Item selected mocks draw all three;
   no SDK route reveals a secret and the plugin must not hold one in a descriptor, so the host draws it — cost if wrong: a
   face of its own.
2. **The rules follow the `buttons` point (`SeparatorFace`): classic separators are 18 px (`SEPARATOR_HEIGHT`) in
   `border.window`, and a document split's hairline and a manager column's edge take the face's colour** — the mock's
   toolbar, row and column rules are `#C4C7CC`; the default face keeps `ICON_CONTROL` and `border.subtle` — cost if wrong:
   one colour.
3. **The classic manager is one WINDOW section: a DOCUMENT split of the toolbar row over the THREE_COLUMN split, its
   fill; a document split now also fills with its first Split child; the window has no title actions (the row carries
   them)** — a WINDOW form has one section (fact 2); the bar already puts the search at the trailing edge (its first text,
   the shortcut caption, fills) — cost if wrong: one fill rule.
4. **The classic catalog's manager is the mock's: `MANAGER_SIDEBAR_WIDTH` 171, `MANAGER_LIST_WIDTH` 348, a 34 px bar
   (`DOCUMENT_BAR_VERTICAL_PADDING` 6 around a 22 px field) with a 200 px search (`DOCUMENT_BAR_SEARCH_WIDTH`, 0 and unread
   in the default catalog)** — History, Snippets and the Editor's bars take the same classic values under Enterprise —
   cost if wrong: three values.
5. **Swatches where SDK 0.18 carries them: the Name cells and the Details heading use `IconSize.SWATCH`, the editors
   `DialogSpec.swatch`; the Categories tree's rows show plain kind glyphs (`TreeItem` has an icon name and no hue) and the
   + New menu its hued glyphs (`MenuItem` has a hue and no size)** — a residual for SDK 0.19 — cost if wrong: seven small
   shapes.
6. **"Used by" lists the plugins granted the entry, each by its id's last segment capitalised ("Remote"), else "Not
   used"** — the Vault knows grants, not sessions; the mock's "Mac session" is a fixture — cost if wrong: a Vault API for
   consumer labels.
7. **"Private key" and "Public key" read in sentence case under CLASSIC only; the default look keeps "Private Key"** —
   decision 17 asks for sentence case in both looks, but the pixel rule binds this plan and 3e's scan read `FormRow` literals,
   not `EditorForm` rows; the default-look change is handed to the user in STATUS — cost if wrong: two words in the
   default look later.
8. **The Lock Vault shortcut caption is the action's default binding as the platform writes it ("⇧⌘L" on macOS,
   "Ctrl+Shift+L" elsewhere)** — a plugin cannot read a live binding — cost if wrong: a stale caption after a rebinding.
9. **The Categories tree shows counts (`TreeItem.count`), not a column, and All Items stays expanded unless the user
   collapses it** — spec 14.8's "count column" against the mock's headerless tree — cost if wrong: none.
10. **The classic editors drop their PROMPT hero (the dialog's header strip shows the swatch and title, spec 10.8), title
    their groups "Login", "Authentication", "SSH Key" and "Secure Note", and label the Authentication choice "Method"** —
    the Add Login and Add SSH Key mocks — cost if wrong: copy.
11. **Unlock Vault under CLASSIC: the lock tile beside the intro (no heading; the header strip titles it), "Master
    password" in the label column, the host's "Show password", and `Form.footnote` "Auto-locks after <n> min idle", none
    when auto-lock is off** — spec 14.8 and Review Focus 5 — cost if wrong: copy.
12. **The manager window is 820 × 571 (content) under CLASSIC** — the mock's window less its 29 px title bar — cost if
    wrong: one size.
13. **Residuals left to their owners, measured**: the dialog header strips and frames are plan 6's; the Fields rows
    carry the classic label column's colon ("Username:"), which the mock omits; the status row's cell rules are 18 px
    `#C4C7CC` where the mock draws full-height `#D3D6DA` (ΔE76 5.4); the status dot is round and 7 px, the kit's
    `StatusDot`; the Details pane's Show and Copy are the classic `BUTTON_HEIGHT` 24 where the mock draws 20; the Details column paints `surface.panel` (`Panels.Surface`), which reads `#F7F7F8` where the mock
    has `#ECEDEF` (ΔE76 3.6, inside the band tolerance) — cost if wrong: one more face rule each.
14. **Under a classic look an auxiliary frame shows no in-window menu row; Close Window's key stays bound on the frame**
    (the orchestrator's ruling of 2026-10-17 under the user's overnight authority, the mock winning on layout; it
    supersedes 6a ruling 31 and closes 10a R18's hand-off) — the 07 Credential Vault mocks draw the native title directly
    over the window's toolbar row; the Vault window's actions stay on its own controls (the toolbar row, Done) and keys
    (⌘W, Ctrl+W elsewhere, closes it). A plugin cannot change the host's frame menus through SDK 0.18, so the change is
    the host's, for every auxiliary frame (Task 9: `ShellChrome.frameMenuRow()`, `NativeShells.bindCloseWindow`), and spec
    10.1 is amended — cost if wrong: New Window and (off macOS) Quit leave the auxiliary frames' row, reachable from any
    terminal window's menus; restoring the row is one branch in `NativeShells.frame`.

## Mock measurements

Measured at 2× with Pillow (`python3 -I`), halved; crops are `x,y,w,h` in mock pixels; "Bands" read down one device
column of the crop (`MockMatch.bands`), as in plan 10a. The manager crop is the window's content below its title bar:
`2,60,1640,1142` (820 × 571) of "Credential Vault 3 - Item selected.png" and "2 - New menu.png".

| Surface | Sample column (crop device x) and rows | Bands |
|---|---|---|
| Manager toolbar and status (New menu) | 1600, all rows | `0+6 #ECEDEF, 6+1 #B4B8BF, 7+20 #FFFFFF, 27+1 #B4B8BF, 28+6 #ECEDEF, 34+1 #C4C7CC, 35+24 #E1E3E6, 59+1 #C4C7CC, 60+474 #ECEDEF, 534+1 #C4C7CC, 535+6 #E6E7EA, 541+1 #2655AE, 542+22 #2F65CA, 564+1 #2655AE, 565+6 #E6E7EA` |
| Categories (Item selected) | 6, rows 0..1070 | `0+34 #ECEDEF, 34+1 #C4C7CC, 35+24 #E1E3E6, 59+1 #C4C7CC, 60+4 #F7F7F8, 64+22 #2F65CA, 86+448 #F7F7F8, 534+1 #C4C7CC` |
| Items (Item selected) | 854, rows 70..1070 | `0+20 #F0F1F3, 20+1 #D3D6DA, 21+22 #2F65CA, 43+22 #F7F8FA, 65+413 #FFFFFF, 478+1 #DDE0E4, 479+20 #F7F8FA, 499+1 #C4C7CC` |
| Details (Item selected) | 1086, rows 70..1070 | `0+24 #E1E3E6, 24+1 #C4C7CC, 25+77 #ECEDEF, 102+1 #D3D6DA, 103+26 #FFFFFF, 129+1 #E6E8EB, 130+26 #FFFFFF, 156+1 #D3D6DA, 157+8 #ECEDEF, 165+1 #C4C7CC, 166+61 #ECEDEF, 227+1 #B4B8BF, 228+22 #FEFEFE~#EEF0F2, 250+1 #B4B8BF, 251+248 #ECEDEF, 499+1 #C4C7CC` |
| Details (none, New menu) | 1600, rows 70..1070 | `0+24 #E1E3E6, 24+1 #C4C7CC, 25+474 #ECEDEF, 499+1 #C4C7CC` |
| Add Login sheet body and footer | "4 - Add Login.png" `382,112,880,494` (440 × 247), x 18 | `0+206 #ECEDEF, 206+1 #C4C7CC, 207+40 #E6E7EA` |
| Add SSH Key sheet body and footer | "5 - Add SSH Key.png" `382,112,880,430` (440 × 215), x 18 | `0+174 #ECEDEF, 174+1 #C4C7CC, 175+40 #E6E7EA` |
| Unlock Vault body and footer | "1 - Unlock Vault (over Workstation).png" `1106,838,800,310` (400 × 155), x 14 | `0+114 #ECEDEF, 114+1 #C4C7CC, 115+40 #E6E7EA`; the focused field at x 394: `0+54 #ECEDEF, 54+2 #C9D7F2, 56+1 #2F65CA, 57+20 #FFFFFF, 77+1 #2F65CA, 78+2 #C9D7F2` (a headless render has no focus owner: not compared) |

The sheet and overlay crops start under the dialog's 26 px header strip (plan 6), so they hold the form and its footer.
The footer band (`surface.footer` under a `border.window` rule) is plan 6b's `Forms` band, which the host turns on with
`KitView.dialogBand` for the dialogs it draws in their window (`NativeShells.classicContent` and `CLASSIC_FLAGS`:
`hostTitled`, `dialogBand`, `dialogCard` and a transparent holder); `ClassicVaultRenderTest.render` does the same,
because the test's headless windows run no shell.

| What | Value (logical px, ±1) |
|---|---|
| Window | 820 × 571 content; panes 171, 348 and the rest, divided by 1 px `#C4C7CC` |
| Toolbar row | 34 tall on `#ECEDEF`, a 1 px `#C4C7CC` rule below; "+" at x 18, "New" at 32, "▾" `#5C6370` at 62; separators 1 × 18 `#C4C7CC` at x 84 and 400; Edit… 100, Delete 148, Copy Username 201, Copy Password 305, Lock Vault 416 and its caption "⇧⌘L" `#7A808A` at 477; the search field 200 × 22 at x 612, y 6, hint "Search vault" `#9AA0A8` |
| Categories | header strip 24 on `#E1E3E6`, "Categories" semibold at x 9; rows 22 from y 64; All Items with ▼ at x 9, glyph at 24, text at 40, count `#7A808A` right-aligned at 162; children's text at 54 |
| Items | header band 20 `#F0F1F3` over a `#D3D6DA` rule; separators at 111, 191 and 263; titles 9 after each, Name with ▴; rows 22, the second `#F7F8FA`; swatch 10 × 10 at x 7, name at 23; Username in the data face; Type and Modified ("Sep 24, 2026") `#5C6370`; footer 20 on `#F7F8FA` under a `#DDE0E4` rule, "2 of 2 items" CAPTION `#5C6370` at x 9 |
| Details (selected) | header strip 24 "Details"; swatch 10 at x 12, y 115; the name DETAIL_TITLE at x 30; "Login · Password" `#5C6370` under it; the Fields group (`#C4C7CC`, title "Fields" `#3A3D42`) over an inner `#D3D6DA` box of rows 26 divided by `#E6E8EB`; labels `#5C6370` at x 31, values in the data face at x 115; Copy 39 × 20 at x 211; Show 41 × 20 at x 211 then Copy at 255; "Modified:" and "Used by:" `#5C6370` at x 13, values at x 92, rows 18 apart; Edit… and Delete… 86 × 24 at x 12 and 104, y 291 |
| Details (none) | "Select an item to view its details." DESCRIPTION at x 12, y 115 |
| Status row | 36 tall on `#E6E7EA` under a `#C4C7CC` rule; the dot 7 at x 11; "Unlocked · Keychain" `#3A3D42` at x 24; cell rules `#D3D6DA` at x 133 and 242; "Auto-lock: 15 min" at 144; the path MONO_CAPTION `#5C6370` at 254; Done 74 × 24 at x 734, primary `#2F65CA` with `#2655AE` edges |
| Add Login | groups "Login" and "Authentication" (`#C4C7CC` boxes, titles `#3A3D42`); labels "Name:", "Username:", "Method:", "Password:" at x 28; fields from x 121, 22 tall, rows 30 apart; radios 13 at x 121 and 211 ("Password", "Private Key"); the "Show" checkbox 13 at x 364; footer 40 on `#E6E7EA`, Cancel 69 × 24 at x 280 and Save (disabled) at 356 |
| Add SSH Key | group "SSH Key"; Name, "Private key" and "Public key" fields 214 wide with Browse… 72 × 24 at x 341; Comment hint "Optional" |
| Unlock Vault | 400 wide; body 114 on `#ECEDEF`: the lock tile 35 × 35 `#2F65CA` at x 12, y 20, the intro `#3A3D42` at x 55; "Master password:" at x 15, the field 273 × 22 at x 115, "Show password" checkbox under it at x 117; footer 40: "Auto-locks after 15 min idle" CAPTION `#5C6370` at x 14, Cancel 70 × 24 at x 240, Unlock (disabled) at 324 |

## File structure

- jasper-ui (Tasks 1, 2): new `SeparatorFace`, `DefaultSeparatorFace`, `ClassicSeparatorFace`, `RevealFace`,
  `DefaultRevealFace`, `ClassicRevealFace` in `dev.jasper.ui.swing`; changed `Separators`, `SplitViews`, `Panels`,
  `PanelHeaders`, `ViewReconciler`, `Stacks`, `SecretFields`, `KitFaces`, `DesignMetrics`, the frozen `compact-roles.tsv`,
  `classic-roles.tsv` and `face-pairs.tsv`; tests `ClassicSeparatorsTest`, `ClassicRevealTest`.
- Vault: `ui/ManagerPanel` (Tasks 3 to 5), `ui/VaultManagerWindow` (Tasks 3, 6), `VaultPlugin` (Tasks 3, 7),
  `ui/EntryPresenter`, `ui/EditorForm`, `ui/EntryEditor` (Task 6), `ui/PasswordPanel` (Task 7); tests
  `ui/ClassicManagerTest`, `ui/ClassicEditorsTest`, `ui/ClassicUnlockTest`, `ui/CompositionParityTest`.
- jasper-app tests: `plugins/StagedVaultHost` (an Enterprise mode, `seedLogins`, `dispatchToManager`),
  `plugins/ClassicVaultRenderTest`.
- jasper-app main (Task 9): `windows/ShellChrome` (`frameMenuRow()` replaces `styleMenus`), `windows/NativeShells` (`frame`,
  `bindCloseWindow`); test `windows/ShellChromeTest`; `docs/app-architecture.md` and spec 10.1 with its header line.
- Ledger (Tasks 3, 6, 7, 8): `docs/design/theme-engine/migration.csv`.
- Documents (Task 10): `docs/credential-vault.md`, `tools/visual/pairs.json`, `docs/STATUS.md`, this plan's banner.

---

### Task 1: The classic rules, the manager's bar and columns, and its pane strips

Rulings 2 to 4: the rules follow the kit's `buttons` point through a face pair whose default class holds today's code; a
document split fills with a Split child; the bar gives a non-filling field the catalog's search width; the classic
catalog carries the mock's manager widths and bar padding; a manager column's header paints plan 7's header strip.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/SeparatorFace.java`, `DefaultSeparatorFace.java`, `ClassicSeparatorFace.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java`, `Separators.java:30-42` (3b's `Rule`), `SplitViews.java:29-35,55,101`,
  `Panels.java:343`, `PanelHeaders.java:167-168` (plan 7's `flush()`), `ViewReconciler.java:197`, `Stacks.java:865-867`,
  `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (the enum and 4a's `classic()`),
  `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv`, `classic-roles.tsv`,
  `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicSeparatorsTest.java`

**Interfaces:**
- Consumes: 4b's `Face`, `KitFaces` (`controls.buttons()`: `RAISED`, `CLASSIC`; `colors.dialogSurface()`), `ThemePainting.role`
  and `painted`; 3b's `Separators.Rule`; plan 7's `PanelHeaders.HeaderControl.flush()`; 10a Task 3's `KitFaces.properties`.
- Produces: package-private `interface SeparatorFace extends Face { Dimension size(KitContext); Color rule(KitContext);
  boolean paneStrips(); }`; `final SeparatorFace KitFaces.separators`; `boolean PanelHeaders.HeaderControl.pane` (the
  header heads a manager column; the reconciler sets it and names no face, Execution notes R2);
  `DesignMetrics.Role.DOCUMENT_BAR_SEARCH_WIDTH` (default 0, classic 200) and `SEPARATOR_HEIGHT` (18 in both); classic
  `MANAGER_SIDEBAR_WIDTH` 171, `MANAGER_LIST_WIDTH` 348, `DOCUMENT_BAR_VERTICAL_PADDING` 6. Task 3's toolbar row and
  status row, Task 4's columns and pane strips rely on these.

- [ ] **Step 1: Take the gallery base and write the failing test**

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
rm -rf jasper-ui/build/plan10b-base && mkdir -p jasper-ui/build/plan10b-base && cp -R jasper-ui/build/gallery/dark-1x jasper-ui/build/gallery/light-1x jasper-ui/build/plan10b-base/
```

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicSeparatorsTest.java`:

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
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Plan 10b Rulings 2 to 4: the classic rules, the manager's bar and columns, and its pane strips. */
class ClassicSeparatorsTest {
    static final ControlState READY = ControlState.READY;

    static Nodes.Panel pane(String id, String title) {
        var header = new Nodes.Header(id + ".header", new ControlState(!title.isEmpty(), true, false, ""), title.isEmpty() ? "Items" : title,
            OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
        return new Nodes.Panel(id, READY, header, Optional.empty(), new Nodes.Text(id + ".body", READY, "Body", Nodes.TextRole.BODY), Optional.empty());
    }

    /** The classic manager's shape (Task 3): a toolbar row over the three columns, in one document split. */
    static ViewSpec manager() {
        return new ViewSpec(new Nodes.Split("document", READY, Nodes.SplitKind.DOCUMENT, List.of(
            new Nodes.Stack("bar", READY, Nodes.LayoutRole.INLINE, List.of(
                new Nodes.Button("new", READY, "New", "", Nodes.ButtonKind.GHOST, "new", ""),
                new Nodes.Separator("rule", READY),
                new Nodes.Button("lock", READY, "Lock Vault", "", Nodes.ButtonKind.GHOST, "lock", ""),
                new Nodes.Text("shortcut", READY, "Ctrl+Shift+L", Nodes.TextRole.CAPTION),
                new Nodes.Field("search", READY, Nodes.FieldKind.SEARCH, "", "Search vault", "Search vault", ""))),
            new Nodes.Split("columns", READY, Nodes.SplitKind.THREE_COLUMN, List.of(pane("categories", "Categories"), pane("items", ""),
                pane("details", "Details"))))));
    }

    static int rgb(Color colour) { return colour.getRGB() & 0xFFFFFF; }

    @Test void theClassicManagerFillsUnderItsBarWithTheMocksColumnsAndRules() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, manager(), event -> { })) {
                var image = KitFixtures.render(view, 820, 400, 1);
                var bar = KitViewTest.find(view, "bar", JComponent.class);
                var columns = KitViewTest.find(view, "columns", JComponent.class);
                var search = KitViewTest.find(view, "search", JComponent.class);
                assertThat(columns.getY()).as("the columns start under the bar").isEqualTo(bar.getHeight());
                assertThat(columns.getHeight()).as("a Split child fills a document split").isEqualTo(400 - bar.getHeight());
                assertThat(search.getWidth()).as("the bar's search width").isEqualTo(200);
                assertThat(bar.getHeight()).as("the bar pads its tallest child by 6 on each side").isGreaterThanOrEqualTo(search.getHeight() + 12);
                assertThat(SwingUtilities.convertPoint(search.getParent(), search.getLocation(), view).x + 200)
                    .as("the search ends at the bar's side padding").isEqualTo(820 - ThemePainting.metric(context, DesignMetrics.Role.DOCUMENT_BAR_SIDE_PADDING));
                assertThat(KitViewTest.find(view, "categories", JComponent.class).getWidth()).isEqualTo(171);
                assertThat(KitViewTest.find(view, "items", JComponent.class).getWidth()).isEqualTo(348);
                assertThat(KitViewTest.find(view, "details", JComponent.class).getWidth()).isEqualTo(820 - 171 - 348);
                var rule = KitViewTest.find(view, "rule", JComponent.class);
                assertThat(rule.getPreferredSize()).isEqualTo(new Dimension(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE),
                    ThemePainting.metric(context, DesignMetrics.Role.SEPARATOR_HEIGHT)));
                int window = rgb(ThemePainting.role(context, SemanticColor.BORDER_WINDOW));
                var at = SwingUtilities.convertPoint(rule.getParent(), rule.getLocation(), view);
                assertThat(image.getRGB(at.x + rule.getWidth() / 2, at.y + rule.getHeight() / 2) & 0xFFFFFF).as("the separator").isEqualTo(window);
                assertThat(image.getRGB(400, bar.getHeight() - 1) & 0xFFFFFF).as("the bar's hairline").isEqualTo(window);
                assertThat(image.getRGB(170, 300) & 0xFFFFFF).as("the rail's edge").isEqualTo(window);
                var header = (PanelHeaders.HeaderControl) view.control("categories.header");
                assertThat(header.pane).as("the header heads a manager column").isTrue();
                assertThat(header.flush()).as("a manager column's header is a strip").isTrue();
                KitFixtures.capture(context, view.spec(), 820, 400, "classic-manager-rules");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aDockPanelsHeaderIsNoPaneStrip() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(pane("solo", "Sessions")), event -> { })) {
                KitFixtures.render(view, 300, 200, 1);
                var header = (PanelHeaders.HeaderControl) view.control("solo.header");
                assertThat(header.pane).isFalse();
                assertThat(header.flush()).isFalse();
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theDefaultKitKeepsItsRulesBarAndColumns() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), Kit.DEFAULT);
            try (var view = new KitView(context, manager(), event -> { })) {
                var image = KitFixtures.render(view, 820, 400, 1);
                var bar = KitViewTest.find(view, "bar", JComponent.class);
                var search = KitViewTest.find(view, "search", JComponent.class);
                assertThat(search.getWidth()).as("a non-filling field keeps its own width").isEqualTo(search.getPreferredSize().width);
                assertThat(KitViewTest.find(view, "categories", JComponent.class).getWidth()).isEqualTo(192);
                assertThat(KitViewTest.find(view, "items", JComponent.class).getWidth()).isEqualTo(272);
                assertThat(KitViewTest.find(view, "rule", JComponent.class).getPreferredSize()).isEqualTo(new Dimension(
                    ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), ThemePainting.metric(context, DesignMetrics.Role.ICON_CONTROL)));
                assertThat(image.getRGB(400, bar.getHeight() - 1) & 0xFFFFFF).isEqualTo(rgb(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE)));
                assertThat(((PanelHeaders.HeaderControl) view.control("categories.header")).flush()).as("no strip in the default kit").isFalse();
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theFacesDeclareTheirPairs() {
        assertThat(new ClassicSeparatorFace(SemanticColor.SURFACE_DIALOG).pairs()).extracting(pair -> pair.slot().key() + " on " + pair.against().key())
            .containsExactly("border.window on surface.dialog");
        assertThat(new DefaultSeparatorFace().pairs()).isEmpty();
    }
}
```

`KitViewTest.find`, `release`, `KitFixtures.render`, `capture`, `enterpriseLight()`, `enterprise()` and `light()` are the
helpers 10a's `ClassicPropertiesTest` uses; `KitView.control(String)` is package-private (`KitView.java:432`).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicSeparatorsTest'`
Expected: FAIL to compile: "cannot find symbol … SEPARATOR_HEIGHT", "… ClassicSeparatorFace", "… DefaultSeparatorFace",
"… pane".

- [ ] **Step 3: The face pair, the fill, the bar's search width, the roles and the pane strips**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/SeparatorFace.java`:

```java
package dev.jasper.ui.swing;

import java.awt.Color;
import java.awt.Dimension;

/**
 * How the rules between controls draw (plan 10b Rulings 2), read from the kit's buttons point: a separator's size and
 * colour, the colour of a document split's hairline and of a manager column's edge, and whether a manager column's
 * header is a header strip.
 */
interface SeparatorFace extends Face {
    /** A separator's size. */
    Dimension size(KitContext context);

    /** The colour of a separator, a document split's hairline and a manager column's edge. */
    Color rule(KitContext context);

    /** Whether the header of a manager column's panel paints as a header strip. */
    boolean paneStrips();
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultSeparatorFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;

/** The rules as plan 3b and the manager columns draw them: a control-tall hairline in border.subtle. */
final class DefaultSeparatorFace implements SeparatorFace {
    @Override public List<PaintPair> pairs() { return List.of(); }

    @Override public Dimension size(KitContext context) {
        return new Dimension(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), ThemePainting.metric(context, DesignMetrics.Role.ICON_CONTROL));
    }

    @Override public Color rule(KitContext context) { return ThemePainting.color(context, SemanticColor.BORDER_SUBTLE); }

    @Override public boolean paneStrips() { return false; }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicSeparatorFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;

/**
 * The classic rules of the Enterprise mocks (plan 10b Rulings 2): {@code SEPARATOR_HEIGHT} tall separators and every
 * manager rule in {@code border.window}, and manager column headers as header strips.
 */
final class ClassicSeparatorFace implements SeparatorFace {
    private final SemanticColor surface;

    ClassicSeparatorFace(SemanticColor dialogSurface) { surface = dialogSurface; }

    private PaintPair pair() { return new PaintPair(SemanticColor.BORDER_WINDOW, surface, SurfaceContrast.Guard.STRUCTURAL); }

    @Override public List<PaintPair> pairs() { return List.of(pair()); }

    @Override public Dimension size(KitContext context) {
        return new Dimension(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), ThemePainting.metric(context, DesignMetrics.Role.SEPARATOR_HEIGHT));
    }

    @Override public Color rule(KitContext context) { return ThemePainting.painted(context, pair()); }

    @Override public boolean paneStrips() { return true; }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 10b Task 1 step 3: the separator face, the fill, the search width, the roles and the pane strips."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "KitFaces.java", [
    ('''    final PropertiesFace properties;\n''', '''    final PropertiesFace properties;\n    final SeparatorFace separators;\n''', 1),
    ('''        properties = switch (controls.table()) {''', '''        separators = switch (controls.buttons()) {
            case CLASSIC -> new ClassicSeparatorFace(colors.dialogSurface());
            default -> new DefaultSeparatorFace();
        };
        properties = switch (controls.table()) {''', 1),
    (''', properties,''', ''', properties, separators,''', 1)])
edit(S + "Separators.java", [
    ('''            return new Dimension(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), ThemePainting.metric(context, DesignMetrics.Role.ICON_CONTROL));''',
     '''            return context.faces().separators.size(context);''', 1),
    ('''            graphics.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));''',
     '''            graphics.setColor(context.faces().separators.rule(context));''', 1),
    ('''    /** One hairline in {@code border.subtle}, as tall as a control glyph; the stack's row centres it. */''',
     '''    /** One hairline as the kit's separator face sizes and colours it; the stack's row centres it. */''', 1)])
edit(S + "SplitViews.java", [
    ('''        /** DOCUMENT: the children that may fill the remaining height (Custom and Empty), in order. */''',
     '''        /** DOCUMENT: the children that may fill the remaining height (Custom, Empty and Split), in order. */''', 1),
    ('''            for (var c : controls) if (c.node instanceof Nodes.Custom || c.node instanceof Nodes.Empty) fills.add(c.component);''',
     '''            for (var c : controls) if (c.node instanceof Nodes.Custom || c.node instanceof Nodes.Empty || c.node instanceof Nodes.Split) fills.add(c.component);''', 1),
    ('''                g.setColor(ThemePainting.color(owner.context(), dev.jasper.ui.theme.SemanticColor.BORDER_SUBTLE));''',
     '''                g.setColor(owner.context().faces().separators.rule(owner.context()));''', 1),
    ('''        /** DOCUMENT: the first visible Custom or Empty child, which takes the height the others leave. */''',
     '''        /** DOCUMENT: the first visible Custom, Empty or Split child, which takes the height the others leave. */''', 1)])
edit(S + "Panels.java", [
    ('''                        g.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));''',
     '''                        g.setColor(context.faces().separators.rule(context));''', 1)])
edit(S + "PanelHeaders.java", [
    ('''        /** Whether the host frames this header as a flush frame's strip (spec 10.7). */
        boolean flush() { return hostHeader.frame() != PanelHostHeader.Frame.STANDARD; }''',
     '''        /** Whether this header heads a manager column (plan 10b Rulings 2); the reconciler sets it before each update. */
        boolean pane;

        /**
         * Whether the host frames this header as a flush frame's strip (spec 10.7), or it heads a manager column under a
         * kit whose separators draw pane strips.
         */
        boolean flush() { return pane && context().faces().separators.paneStrips() || hostHeader.frame() != PanelHostHeader.Frame.STANDARD; }''', 1)])
edit(S + "ViewReconciler.java", [
    ('''        if (control instanceof Panels.PanelControl panel) panel.column = column;\n''',
     '''        if (control instanceof Panels.PanelControl panel) panel.column = column;
        // A manager column's header asks the kit whether it draws as a header strip (plan 10b Rulings 2).
        if (node instanceof Nodes.Panel panel && next.get(panel.header().id()) instanceof PanelHeaders.HeaderControl header)
            header.pane = column >= 0;\n''', 1)])
edit(S + "Stacks.java", [
    ('''        int natural(Component child, Component fill) {
            int width = child.getPreferredSize().width;''',
     '''        int natural(Component child, Component fill) {
            int width = child.getPreferredSize().width;
            // A field that does not fill takes the catalog's search width where it has one (plan 10b Rulings 4).
            int search = metric(DesignMetrics.Role.DOCUMENT_BAR_SEARCH_WIDTH);
            if (search > 0 && child != fill && owner.barFields.contains(child) && !owner.barTexts.contains(child)) return search;''', 1)])
M = "jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java"
edit(M, [
    ('''DOCUMENT_BAR_LINE_GAP(4),''', '''DOCUMENT_BAR_LINE_GAP(4), DOCUMENT_BAR_SEARCH_WIDTH(0), SEPARATOR_HEIGHT(18),''', 1),
    ('''        int[] table = declared();\n''', '''        // Plan 10b: the classic manager (Rulings 4), the 07 Credential Vault mocks' columns, bar padding and search width.
        values.put(Role.MANAGER_SIDEBAR_WIDTH, 171);
        values.put(Role.MANAGER_LIST_WIDTH, 348);
        values.put(Role.DOCUMENT_BAR_VERTICAL_PADDING, 6);
        values.put(Role.DOCUMENT_BAR_SEARCH_WIDTH, 200);
        int[] table = declared();\n''', 1)])
R = "jasper-ui/src/test/resources/dev/jasper/ui/metrics/"
edit(R + "compact-roles.tsv", [('''DOCUMENT_BAR_LINE_GAP\t4\n''', '''DOCUMENT_BAR_LINE_GAP\t4\nDOCUMENT_BAR_SEARCH_WIDTH\t0\nSEPARATOR_HEIGHT\t18\n''', 1)])
edit(R + "classic-roles.tsv", [
    ('''MANAGER_SIDEBAR_WIDTH\t192\n''', '''MANAGER_SIDEBAR_WIDTH\t171\n''', 1),
    ('''MANAGER_LIST_WIDTH\t272\n''', '''MANAGER_LIST_WIDTH\t348\n''', 1),
    ('''DOCUMENT_BAR_VERTICAL_PADDING\t4\n''', '''DOCUMENT_BAR_VERTICAL_PADDING\t6\n''', 1),
    ('''DOCUMENT_BAR_LINE_GAP\t4\n''', '''DOCUMENT_BAR_LINE_GAP\t4\nDOCUMENT_BAR_SEARCH_WIDTH\t200\nSEPARATOR_HEIGHT\t18\n''', 1)])
PY
```

The `PanelHeaders` anchor is plan 7's `flush()` as it stands at `f866788de` (`PanelHeaders.java:167-168`). The reconciler
sets only `pane`; the header control asks the face, because `StrategyIsolationTest` forbids the reconciler to name
`KitFaces` or a face (Execution notes R2). `controls.buttons()` has the values `RAISED` and `CLASSIC` (4b); the `default`
arm keeps the switch total if a later plan adds one. If `Separators.java` still imports `SemanticColor` or `DesignMetrics`
only for the replaced lines, remove the unused import. `ClassicCatalogTest` reads `classic-roles.tsv` whole and lists no
classic delta by name, so the TSV edits are its whole update.

- [ ] **Step 4: Run the tests, freeze the pair and prove the gallery**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicSeparatorsTest' --tests 'dev.jasper.ui.swing.SeparatorsTest' --tests 'dev.jasper.ui.swing.PanelHeaderStripTest' --tests 'dev.jasper.ui.swing.EditorKitTest' --tests 'dev.jasper.ui.swing.PanelSurfacesTest' --tests 'dev.jasper.ui.metrics.*' --tests 'dev.jasper.ui.swing.PaintSpecTest' :jasper-app:test --tests 'dev.jasper.app.architecture.StrategyIsolationTest' --tests 'dev.jasper.app.architecture.VariantReadTest' --continue`
Expected: PASS except `PaintSpecTest`, which FAILS listing the one `ClassicSeparatorFace` row missing from
`face-pairs.tsv`. Add exactly that row, in the printed form, after the `ClassicPropertiesFace` rows (the face's place in
`all()`), and run the same command: PASS. `ClassicSeparatorsTest` (4); 3b's `SeparatorsTest`, plan 7's
`PanelHeaderStripTest`, the Editor's document bars and the panel surfaces unchanged; the architecture scans (jasper-app
tests over the built jasper-ui jar) find no face named by the reconciler.

The classic separators and rules reach every Enterprise surface (SFTP's two toolbar separators, document-split
hairlines and manager columns), so run the Enterprise mock suites once (Execution notes R3):
`./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.ClassicCompositionRenderTest' --tests 'dev.jasper.app.application.EnterpriseKitRenderTest' --tests 'dev.jasper.app.workspace.EnterpriseWorkstationMockTest' --continue`.
Expected: PASS within their floors (SFTP's separators move toward the 01 mock's 16 px `#C4C7CC`, 10a D9).

If the classic test's bar is not at least the field plus 12 tall, or the search does not end at the side padding, read
`Stacks.DocumentBarLayout.layoutContainer` (it clips the last child at the trailing edge) before changing a value. Then:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
python3 -I - <<'PY'
import hashlib, pathlib, sys
def digests(root): return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.rglob("*")) if p.is_file()}
bad = []
for theme in ("dark-1x", "light-1x"):
    base, now = digests(pathlib.Path("jasper-ui/build/plan10b-base") / theme), digests(pathlib.Path("jasper-ui/build/gallery") / theme)
    bad += [f"{theme}/{name}" for name in sorted(set(base) | set(now)) if base.get(name) != now.get(name)]
print("changed:", bad if bad else "none"); sys.exit(1 if bad else 0)
PY
```

Expected: `changed: none`.

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src
git commit -F - <<'EOF'
Vault compositions: classic rules, the manager's bar and columns

Separators, a document split's hairline and a manager column's edge
follow the kit's buttons point: the default face is today's code, the
classic one draws 18 px border.window rules and makes a manager column's
header plan 7's header strip. A document split fills with a Split child,
a bar's non-filling field takes the catalog's search width, and the
classic catalog carries the Vault mock's columns and bar padding. The
face's pair is frozen and the default gallery is byte-identical.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: The classic reveal: Show, Show password, and Show and Copy

Rulings 1: the reveal follows the kit's `toggle` point. The default face is `SecretFields`' code moved behind a face,
unchanged; the classic face draws an input's reveal as a "Show" checkbox after the field (4b's checkbox face paints the
box), a prompt's under the field as "Show password", a discreet input's always, and a stored value's reveal and copy as
standard buttons with text.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/swing/RevealFace.java`, `DefaultRevealFace.java`, `ClassicRevealFace.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/SecretFields.java:44,50-51,103-108,208-215,220,318-342,383-387`,
  `KitFaces.java`, `jasper-ui/src/test/resources/dev/jasper/ui/swing/face-pairs.tsv`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicRevealTest.java`

**Interfaces:**
- Consumes: 4b's `ToggleFace` through `Switches.SwitchButton` (its `face`, `focus` and `position`), `controls.toggle()`
  (`SWITCH`, `CHECKBOX`), `CHECKBOX_LABEL_GAP`; Task 1's `KitFaces.separators`; `KitView.revealSecrets` and
  `replaceSecret`.
- Produces: package-private `interface RevealFace extends Face { void mountInput(SecretFields.SecretControl);
  JPanel detailActions(SecretFields.SecretControl); void prompt(SecretFields.SecretControl, boolean); void
  visibility(SecretFields.SecretControl); void style(SecretFields.SecretControl); }`; `final RevealFace KitFaces.reveal`;
  `SecretControl.face`, `box`, `boxLabel`, `boxRow`, `fieldRow`; the component names `<secret id>.show` and
  `<secret id>.show.label`. Tasks 5 to 7 rely on these.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicRevealTest.java`:

```java
package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.SemanticColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Plan 10b Rulings 1: the classic reveal is "Show", "Show password" in a prompt, and "Show" and "Copy" on a stored value. */
class ClassicRevealTest {
    static final ControlState READY = ControlState.READY;

    static ViewSpec row(Nodes.SecretKind kind) {
        return new ViewSpec(new Nodes.Card("card", READY, Nodes.CardKind.FORM, List.of(new Nodes.FormRow("row", READY, "Password", "",
            new Nodes.Secret("pw", READY, "", "Password", "", kind, ""), Optional.empty(), false))));
    }

    static ViewSpec prompt() {
        return new ViewSpec(new Nodes.Form("unlock", READY, Nodes.FormKind.PROMPT_COMPACT, "Unlock Vault", Optional.empty(), List.of(
            new Nodes.FormRow("row", READY, "Master password", "", new Nodes.Secret("pw", READY, "", "Master password", "", Nodes.SecretKind.INPUT, ""),
                Optional.empty(), false)), Optional.empty(),
            List.of(new Nodes.Button("submit", READY, "Unlock", "", Nodes.ButtonKind.PRIMARY, "submit", "")), ""));
    }

    static ViewSpec stored(String id) {
        return new ViewSpec(new Nodes.Card("card", READY, Nodes.CardKind.FORM, List.of(new Nodes.FormRow("row", READY, "Password", "",
            new Nodes.Secret(id, READY, "", "Password", "", Nodes.SecretKind.DETAIL, "copy"), Optional.empty(), false))));
    }

    static KitContext classic() { return KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise()); }

    @Test void anInputsRevealIsAShowCheckboxAfterTheField() {
        Edt.run(() -> {
            var context = classic();
            try (var view = new KitView(context, row(Nodes.SecretKind.INPUT), event -> { })) {
                KitFixtures.render(view, 440, 60, 1);
                var control = (SecretFields.SecretControl) view.control("pw");
                var box = KitViewTest.find(view, "pw.show", Switches.SwitchButton.class);
                assertThat(KitViewTest.find(view, "pw.show.label", javax.swing.JLabel.class).getText()).isEqualTo("Show");
                assertThat(control.reveal.getParent()).as("no glyph inside the field").isNull();
                var input = control.input;
                int fieldEnd = SwingUtilities.convertPoint(control.surface.getParent(), control.surface.getLocation(), view).x + control.surface.getWidth();
                assertThat(SwingUtilities.convertPoint(box.getParent(), box.getLocation(), view).x).as("after the field").isGreaterThanOrEqualTo(fieldEnd);
                char masked = input.getEchoChar();
                box.doClick(0);
                assertThat(input.getEchoChar()).as("Show reveals").isEqualTo((char) 0);
                assertThat(box.isSelected()).isTrue();
                box.doClick(0);
                assertThat(input.getEchoChar()).isEqualTo(masked);
                KitFixtures.capture(context, view.spec(), 440, 60, "reveal-checkbox");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aPromptsRevealIsShowPasswordUnderTheField() {
        Edt.run(() -> {
            var context = classic();
            try (var view = new KitView(context, prompt(), event -> { })) {
                KitFixtures.render(view, 400, 160, 1);
                var control = (SecretFields.SecretControl) view.control("pw");
                assertThat(control.boxLabel.getText()).isEqualTo("Show password");
                var boxAt = SwingUtilities.convertPoint(control.box.getParent(), control.box.getLocation(), view);
                var fieldAt = SwingUtilities.convertPoint(control.surface.getParent(), control.surface.getLocation(), view);
                assertThat(boxAt.y).as("under the field").isGreaterThanOrEqualTo(fieldAt.y + control.surface.getHeight());
                assertThat(boxAt.x).as("on the field's leading edge").isEqualTo(fieldAt.x);
                KitFixtures.capture(context, view.spec(), 400, 160, "reveal-prompt");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aDiscreetInputsCheckboxAlwaysShows() {
        Edt.run(() -> {
            var context = classic();
            try (var view = new KitView(context, row(Nodes.SecretKind.DISCREET_INPUT), event -> { })) {
                KitFixtures.render(view, 440, 60, 1);
                var control = (SecretFields.SecretControl) view.control("pw");
                assertThat(control.boxRow.isVisible()).as("empty and unfocused, the checkbox still shows").isTrue();
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aStoredValueHasShowAndCopyButtons() {
        Edt.run(() -> {
            var context = classic(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stored("detail.secret.a"), events::add)) {
                view.replaceSecret("detail.secret.a", "synthetic-a".toCharArray());
                KitFixtures.render(view, 300, 60, 1);
                var reveal = KitViewTest.find(view, "detail.secret.a.reveal", Buttons.KitButton.class);
                var copy = KitViewTest.find(view, "detail.secret.a.copy", Buttons.KitButton.class);
                assertThat(reveal.getText()).isEqualTo("Show");
                assertThat(reveal.getIcon()).isNull();
                assertThat(reveal.kind).isEqualTo(Nodes.ButtonKind.SECONDARY);
                assertThat(copy.getText()).isEqualTo("Copy");
                assertThat(copy.kind).isEqualTo(Nodes.ButtonKind.SECONDARY);
                assertThat(SwingUtilities.convertPoint(copy.getParent(), copy.getLocation(), view).x).as("Copy follows Show")
                    .isGreaterThan(SwingUtilities.convertPoint(reveal.getParent(), reveal.getLocation(), view).x + reveal.getWidth() - 1);
                reveal.doClick(0);
                assertThat(reveal.getText()).isEqualTo("Hide");
                copy.doClick(0);
                assertThat(events).contains(new UiEvent.Action("copy"));
                KitFixtures.capture(context, view.spec(), 300, 60, "reveal-stored");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aStoredValuesShowIsPerEntry() {
        Edt.run(() -> {
            var context = classic();
            try (var view = new KitView(context, stored("detail.secret.a"), event -> { })) {
                view.replaceSecret("detail.secret.a", "synthetic-a".toCharArray());
                KitFixtures.render(view, 300, 60, 1);
                KitViewTest.find(view, "detail.secret.a.reveal", Buttons.KitButton.class).doClick(0);
                assertThat(((SecretFields.SecretControl) view.control("detail.secret.a")).revealed).isTrue();
                view.update(stored("detail.secret.b"));
                view.replaceSecret("detail.secret.b", "synthetic-b".toCharArray());
                KitFixtures.render(view, 300, 60, 1);
                var next = (SecretFields.SecretControl) view.control("detail.secret.b");
                assertThat(next.revealed).as("another entry's value shows masked").isFalse();
                assertThat(next.reveal.getText()).isEqualTo("Show");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theDefaultKitKeepsTheGlyphInTheField() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), Kit.DEFAULT);
            try (var view = new KitView(context, row(Nodes.SecretKind.INPUT), event -> { })) {
                KitFixtures.render(view, 440, 60, 1);
                var control = (SecretFields.SecretControl) view.control("pw");
                assertThat(control.box).isNull();
                assertThat(control.reveal.getParent()).as("the glyph sits in the field").isSameAs(control.surface);
                assertThat(control.reveal.getIcon()).isNotNull();
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theFacesDeclareTheirPairs() {
        assertThat(new ClassicRevealFace(SemanticColor.SURFACE_DIALOG).pairs()).extracting(pair -> pair.slot().key() + " on " + pair.against().key())
            .containsExactly("text.primary on surface.dialog");
        assertThat(new DefaultRevealFace().pairs()).isEmpty();
    }
}
```

`KitView.update(ViewSpec)` and `replaceSecret(String, char[])` are public (`KitView.java:62`, `:213`); a new secret id is a
new control (the reconciler keys controls by id), so the second entry starts unrevealed.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicRevealTest'`
Expected: FAIL to compile: "cannot find symbol … ClassicRevealFace", "… DefaultRevealFace", "… box", "… boxLabel".

- [ ] **Step 3: The face pair and the secret control's hooks**

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/RevealFace.java`:

```java
package dev.jasper.ui.swing;

import javax.swing.JPanel;

/**
 * How a secret's reveal and copy draw (plan 10b Rulings 1), read from the kit's toggle point: the default glyph
 * buttons, or the classic "Show" checkbox and "Show" and "Copy" buttons.
 */
interface RevealFace extends Face {
    /** Places an INPUT or DISCREET_INPUT secret's reveal, once, after the field surface and the reveal exist. */
    void mountInput(SecretFields.SecretControl control);

    /** The row holding a DETAIL secret's reveal and copy, made once. */
    JPanel detailActions(SecretFields.SecretControl control);

    /** The secret learned whether it is in a prompt form. */
    void prompt(SecretFields.SecretControl control, boolean prompt);

    /** Shows or hides the reveal for the secret's kind, focus and content. */
    void visibility(SecretFields.SecretControl control);

    /** The reveal's and copy's texts, glyphs, fonts and borders, after each refresh and each reveal. */
    void style(SecretFields.SecretControl control);
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/DefaultRevealFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.JPanel;

/** The reveal as SecretFields drew it before plan 10b: a glyph in the field, and glyph buttons beside a stored value. */
final class DefaultRevealFace implements RevealFace {
    @Override public List<PaintPair> pairs() { return List.of(); }

    @Override public void mountInput(SecretFields.SecretControl control) {
        var kind = ((Nodes.Secret) control.node).kind();
        var reveal = control.reveal;
        if (kind == Nodes.SecretKind.DISCREET_INPUT) {
            var slot=new JPanel(new BorderLayout()) {
                @Override public Dimension getPreferredSize() { return reveal.getPreferredSize(); }
            };
            slot.setOpaque(false);slot.add(reveal);control.surface.add(slot,BorderLayout.EAST);
        } else if (!control.multiline && kind != Nodes.SecretKind.CONFIRMATION) control.surface.add(reveal, BorderLayout.EAST);
    }

    @Override public JPanel detailActions(SecretFields.SecretControl control) {
        var actions = new JPanel(new GridLayout(1, 0)) {
            // Reveal and copy keep the detail references' measured spacing at every theme/font refresh.
            @Override public void doLayout() { ((GridLayout) getLayout()).setHgap(ThemePainting.metric(control.context(), DesignMetrics.Role.DETAIL_ACTION_GAP)); super.doLayout(); }
            @Override public Dimension getPreferredSize() { ((GridLayout) getLayout()).setHgap(ThemePainting.metric(control.context(), DesignMetrics.Role.DETAIL_ACTION_GAP)); return super.getPreferredSize(); }
        };
        actions.setOpaque(false);
        actions.add(control.reveal);
        actions.add(control.copy);
        return actions;
    }

    @Override public void prompt(SecretFields.SecretControl control, boolean prompt) { }

    @Override public void visibility(SecretFields.SecretControl control) {
        var kind=((Nodes.Secret)control.node).kind();
        control.reveal.setVisible(!control.multiline && kind!=Nodes.SecretKind.CONFIRMATION
            && (kind!=Nodes.SecretKind.DISCREET_INPUT || control.focusWithin || control.buffer.getLength()>0));
    }

    @Override public void style(SecretFields.SecretControl control) {
        var reveal = control.reveal; var context = control.context();
        reveal.setToolTipText(control.revealed ? "Hide password" : "Show password");
        reveal.getAccessibleContext().setAccessibleName(reveal.getToolTipText());
        reveal.getAccessibleContext()
                .setAccessibleDescription(control.revealed ? "Password visible" : "Password hidden");
        reveal.setSelected(control.revealed);
        reveal.putClientProperty("Jasper.button.icon", "reveal");
        reveal.setIcon(
                context
                        .icons()
                        .icon(
                                "reveal",
                                context.theme().semantic(),
                                SemanticColor.TEXT_SECONDARY,
                                com.formdev.flatlaf.util.UIScale.scale(
                                        context.icons().size(context.metrics(), Nodes.IconSize.CONTROL))));
        reveal.setDisabledIcon(reveal.getIcon());
        reveal.repaint();
    }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicRevealFace.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The classic reveal of the Enterprise mocks (plan 10b Rulings 1): a "Show" checkbox after an input (the kit's checkbox
 * face paints the box), "Show password" under a prompt's field, and a stored value's "Show" (or "Hide") and "Copy" as
 * secondary buttons with text.
 */
final class ClassicRevealFace implements RevealFace {
    private final SemanticColor surface;

    ClassicRevealFace(SemanticColor dialogSurface) { surface = dialogSurface; }

    private PaintPair label() { return new PaintPair(SemanticColor.TEXT_PRIMARY, surface, SurfaceContrast.Guard.TEXT); }

    @Override public List<PaintPair> pairs() { return List.of(label()); }

    @Override public void mountInput(SecretFields.SecretControl control) {
        var kind = ((Nodes.Secret) control.node).kind();
        if (control.multiline || control.detail || kind == Nodes.SecretKind.CONFIRMATION) return;
        var box = new Switches.SwitchButton(control.context());
        KitOwnership.mark(box, control.node.id() + ".show");
        box.focus = new ThemePainting.Focus(box, box, control.view);
        control.own(box.focus);
        ActionListener toggle = event -> { if (control.interactive()) control.view.revealSecrets(control, !control.revealed); };
        box.addActionListener(toggle);
        control.own(() -> box.removeActionListener(toggle));
        var text = Labels.literal();
        KitOwnership.mark(text, control.node.id() + ".show.label");
        text.setLabelFor(box);
        var click = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { if (box.isEnabled()) box.doClick(0); }
        };
        text.addMouseListener(click);
        control.own(() -> text.removeMouseListener(click));
        var row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(box, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        var field = new JPanel(new BorderLayout());
        field.setOpaque(false);
        field.add(control.surface, BorderLayout.CENTER);
        field.add(row, BorderLayout.EAST);
        control.box = box; control.boxLabel = text; control.boxRow = row; control.fieldRow = field;
    }

    @Override public JPanel detailActions(SecretFields.SecretControl control) {
        var actions = new JPanel(new BorderLayout()) {
            @Override public void doLayout() { ((BorderLayout) getLayout()).setHgap(ThemePainting.metric(control.context(), DesignMetrics.Role.DETAIL_ACTION_GAP)); super.doLayout(); }
        };
        actions.setOpaque(false);
        actions.add(control.reveal, BorderLayout.WEST);
        actions.add(control.copy, BorderLayout.EAST);
        return actions;
    }

    @Override public void prompt(SecretFields.SecretControl control, boolean prompt) {
        if (control.boxRow == null) return;
        control.fieldRow.remove(control.boxRow);
        control.fieldRow.add(control.boxRow, prompt ? BorderLayout.SOUTH : BorderLayout.EAST);
        style(control);
        control.fieldRow.revalidate();
    }

    @Override public void visibility(SecretFields.SecretControl control) {
        control.reveal.setVisible(control.detail);
        if (control.boxRow != null) control.boxRow.setVisible(true);
    }

    @Override public void style(SecretFields.SecretControl control) {
        var context = control.context(); var reveal = control.reveal; boolean revealed = control.revealed;
        reveal.setToolTipText(revealed ? "Hide password" : "Show password");
        reveal.getAccessibleContext().setAccessibleName(reveal.getToolTipText());
        reveal.getAccessibleContext().setAccessibleDescription(revealed ? "Password visible" : "Password hidden");
        reveal.setSelected(revealed);
        if (control.detail) {
            button(control, reveal, revealed ? "Hide" : "Show");
            button(control, control.copy, "Copy");
        }
        if (control.box != null) {
            boolean prompt = control.surface.prompt;
            control.box.setSelected(revealed);
            control.box.position = revealed ? 1 : 0;
            control.box.setEnabled(control.component.isEnabled());
            control.box.getAccessibleContext().setAccessibleName("Show password");
            control.boxLabel.setText(prompt ? "Show password" : "Show");
            control.boxLabel.setFont(ThemePainting.font(context, Typography.Role.BODY));
            control.boxLabel.setForeground(ThemePainting.painted(context, label()));
            control.boxLabel.setEnabled(control.component.isEnabled());
            ((BorderLayout) control.boxRow.getLayout()).setHgap(ThemePainting.metric(context, DesignMetrics.Role.CHECKBOX_LABEL_GAP));
            ((BorderLayout) control.fieldRow.getLayout()).setHgap(ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP));
            ((BorderLayout) control.fieldRow.getLayout()).setVgap(ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP));
            control.box.repaint();
        }
        reveal.repaint();
    }

    /** A stored value's action as a secondary button with text: the classic buttons face paints it. */
    private static void button(SecretFields.SecretControl control, Buttons.KitButton button, String text) {
        var context = control.context();
        button.kind = Nodes.ButtonKind.SECONDARY;
        button.putClientProperty("Jasper.button.icon", null);
        button.setIcon(null);
        button.setDisabledIcon(null);
        button.setText(text);
        button.setFont(ThemePainting.font(context, Typography.Role.LABEL));
        int side = ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
        button.setBorder(new EmptyBorder(0, side, 0, side));
        button.repaint();
    }
}
```

`Switches.SwitchButton` is 4b's checkbox under the CHECKBOX toggle point: its face paints the box from `isSelected()`
and `position` (4b Task 1); if 4b's face reads only one of them, keep both assignments. Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 10b Task 2 step 3: the reveal face in KitFaces and SecretFields' hooks."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "KitFaces.java", [
    ('''    final SeparatorFace separators;\n''', '''    final SeparatorFace separators;\n    final RevealFace reveal;\n''', 1),
    ('''        separators = switch (controls.buttons()) {''', '''        reveal = switch (controls.toggle()) {
            case CHECKBOX -> new ClassicRevealFace(colors.dialogSurface());
            default -> new DefaultRevealFace();
        };
        separators = switch (controls.buttons()) {''', 1),
    (''', separators,''', ''', separators, reveal,''', 1)])
edit(S + "SecretFields.java", [
    ('''        boolean detailActionsBelow;\n''', '''        boolean detailActionsBelow;
        /** How this secret's reveal and copy draw (plan 10b Rulings 1). */
        final RevealFace face;
        /** The classic face's "Show" checkbox, its label, the row of both and the row of the field and that row; null under the default face. */
        Switches.SwitchButton box;
        JLabel boxLabel;
        JPanel boxRow, fieldRow;\n''', 1),
    ('''            super(view, node, new JPanel(new BorderLayout()));
            detail = node.kind() == Nodes.SecretKind.DETAIL;''', '''            super(view, node, new JPanel(new BorderLayout()));
            face = view.context().faces().reveal;
            detail = node.kind() == Nodes.SecretKind.DETAIL;''', 1),
    ('''            if (node.kind() == Nodes.SecretKind.DISCREET_INPUT) {
                var slot=new JPanel(new BorderLayout()) {
                    @Override public Dimension getPreferredSize() { return reveal.getPreferredSize(); }
                };
                slot.setOpaque(false);slot.add(reveal);surface.add(slot,BorderLayout.EAST);
            } else if (!multiline && node.kind() != Nodes.SecretKind.CONFIRMATION) surface.add(reveal, BorderLayout.EAST);''',
     '''            face.mountInput(this);''', 1),
    ('''                var actions = new JPanel(new GridLayout(1, 0)) {
                    // Reveal and copy keep the detail references' measured spacing at every theme/font refresh.
                    @Override public void doLayout() { ((GridLayout) getLayout()).setHgap(ThemePainting.metric(context(), DesignMetrics.Role.DETAIL_ACTION_GAP)); super.doLayout(); }
                    @Override public Dimension getPreferredSize() { ((GridLayout) getLayout()).setHgap(ThemePainting.metric(context(), DesignMetrics.Role.DETAIL_ACTION_GAP)); return super.getPreferredSize(); }
                };
                actions.setOpaque(false);
                actions.add(reveal);
                actions.add(copy);''', '''                var actions = face.detailActions(this);''', 1),
    ('''            } else container.add(surface, BorderLayout.CENTER);''',
     '''            } else container.add(fieldRow != null ? fieldRow : surface, BorderLayout.CENTER);''', 1),
    ('''        private void revealVisibility() {
            var kind=((Nodes.Secret)node).kind();
            reveal.setVisible(!multiline && kind!=Nodes.SecretKind.CONFIRMATION
                && (kind!=Nodes.SecretKind.DISCREET_INPUT || focusWithin || buffer.getLength()>0));
        }''', '''        private void revealVisibility() { face.visibility(this); }''', 1),
    ('''        void prompt(boolean value) {
            surface.prompt = value;
            label.setVisible(!embedded && !value);
            lock.setVisible(!embedded && !value);
        }''', '''        void prompt(boolean value) {
            surface.prompt = value;
            label.setVisible(!embedded && !value);
            lock.setVisible(!embedded && !value);
            face.prompt(this, value);
        }''', 1)])
path = pathlib.Path(S + "SecretFields.java"); text = path.read_text(encoding="utf-8")
start = text.index("        private void refreshReveal() {")
end = text.index("        private void refreshArea(", start)
path.write_text(text[:start] + "        private void refreshReveal() { face.style(this); }\n\n" + text[end:], encoding="utf-8")
PY
grep -c 'face.style(this)' jasper-ui/src/main/java/dev/jasper/ui/swing/SecretFields.java
```

Expected: `1`. Before the last replacement, compare the removed `refreshReveal` body with `DefaultRevealFace.style`
(`git diff` shows both): they must be the same statements with `control.` in front of the control's members; if plan
3b or a later plan changed `refreshReveal`, carry the change into the default face. `GridLayout` and `Dimension` stay
imported in `SecretFields` (`java.awt.*`).

The classic face reads `CHECKBOX_LABEL_GAP` (4b), `ICON_GAP`, `DETAIL_ACTION_GAP` and `FIELD_HORIZONTAL_PADDING`, which
both catalogs have; its buttons take the kit's `BUTTON_HEIGHT` (Rulings 13). No metric role is added.

- [ ] **Step 4: Run the tests, freeze the pair and prove the gallery**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ClassicRevealTest' --tests 'dev.jasper.ui.swing.SecretFieldsTest' --tests 'dev.jasper.ui.swing.KitViewTest' --tests 'dev.jasper.ui.swing.ClassicTogglesTest' --tests 'dev.jasper.ui.swing.PaintSpecTest' :jasper-app:test --tests 'dev.jasper.app.architecture.StrategyIsolationTest' --tests 'dev.jasper.app.architecture.VariantReadTest' --continue`
Expected: PASS except `PaintSpecTest`, which FAILS listing the one `ClassicRevealFace` row; add it after the
`ClassicSeparatorFace` row and run again: PASS. Then repeat Task 1 Step 4's gallery digest commands against
`jasper-ui/build/plan10b-base`. Expected: `changed: none`.

The classic reveal reaches every secret input under Enterprise (Add Host's and New File Server's passwords, the
fixture forms of 6b's dialog comparison, the gallery's secrets in 5b's kit renders), so run once (Execution notes R3):
`./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.ClassicCompositionRenderTest' --tests 'dev.jasper.app.application.EnterpriseKitRenderTest' --tests 'dev.jasper.app.windows.EnterpriseDialogMockTest' --tests 'dev.jasper.app.windows.VaultSheetRenderTest' --continue`.
Expected: PASS within their floors. A form that grows by the "Show" row (a prompt's "Show password" under its field)
moves a dialog's height, not its strip or band; a floor or band that moves anyway is handled as R3 says.

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src
git commit -F - <<'EOF'
Vault compositions: the classic reveal

A secret's reveal follows the kit's toggle point. The default face is
SecretFields' glyph code moved behind a face, unchanged; the classic one
puts a "Show" checkbox after an input, "Show password" under a prompt's
field, keeps a discreet input's checkbox showing, and gives a stored
value "Show" and "Copy" buttons. The pair is frozen and the default
gallery is byte-identical.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: The classic manager window: the toolbar row, the status row and the Enterprise host mode

Rulings 3, 8 and 12: under CLASSIC the manager is a DOCUMENT split of the toolbar row over its body, the window is 820 ×
571 without title actions, and the status row reads the lock state, the auto-lock and the vault file between rules. The
columns keep today's rail, list and detail until Tasks 4 and 5 (only the search moves into the row). `StagedVaultHost`
gains its Enterprise mode, and the render harness for the Vault starts here.

**Files:**
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/ui/ManagerPanel.java:35-38,49,64-73,75,348-356` (and new methods
  before `close()`), `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultManagerWindow.java:47-66`,
  `plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java:140`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/StagedVaultHost.java:47-71,184,274-275`
- Create: `plugins/vault/src/test/java/dev/jasper/vault/ui/ClassicManagerTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicVaultRenderTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (both new test sources match the ledger's pattern; Step 4)

**Interfaces:**
- Consumes: 10a Task 1's `Layout` and `VaultPlugin.layout`; 10a Task 2's `MockMatch`, `StagedEnterprise.start`,
  `testsupport.EnterpriseLook` (10a R4), `WorkspaceAppearanceFixture`; 10a Task 4's `ClassicCompositionRenderTest.under`; Task 1's separators, document fill and bar width;
  3b's `Nodes.Separator`.
- Produces: `ManagerPanel(UiKit, Layout, …11 callbacks…)` (the 11-argument constructor means `Layout.ROWS`),
  `Layout ManagerPanel.layout()`, `ManagerPanel.TOOLBAR` ("toolbar") and `LOCK_SHORTCUT`, the toolbar ids
  `toolbar.new`, `toolbar.rule.1`, `toolbar.edit`, `toolbar.delete`, `toolbar.copy-username`, `toolbar.copy-password`,
  `toolbar.rule.2`, `toolbar.lock`, `toolbar.shortcut` (then `SEARCH`), the status ids `status.state`, `status.provider`,
  `status.rule.1`, `status.autolock`, `status.rule.2`, `status.path`; private `classicEntries()` (Task 4 replaces it);
  `VaultManagerWindow(PluginContext, LockManager, VaultManager, VaultService, Supplier<Status>, Runnable, Layout)`;
  `StagedVaultHost(Path, boolean, WorkspaceAppearanceFixture, boolean enterprise)`, `List<UUID> seedLogins(String[][])`,
  `void dispatchToManager(UiEvent)`; `ClassicVaultRenderTest.render(AuxiliarySurface, int, int)` and `bands(...)`.

- [ ] **Step 1: Write the failing tests**

Create `plugins/vault/src/test/java/dev/jasper/vault/ui/ClassicManagerTest.java`:

```java
package dev.jasper.vault.ui;

import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.service.VaultService;
import dev.jasper.vault.workstation.Layout;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Plan 10b: the classic manager (spec 14.8) as the 07 Credential Vault mocks compose it. */
class ClassicManagerTest {
    @TempDir Path directory;
    static final PluginInfo INFO = new PluginInfo("dev.jasper.vault", "Vault", "0.1.0", Set.of());
    static final VaultManagerWindow.Status STATUS = new VaultManagerWindow.Status("keychain", "15 min", "/synthetic/vault.jv");
    static final Instant MODIFIED = Instant.parse("2026-09-24T12:00:00Z");
    static final List<String> VERBS = List.of("toolbar.edit", "toolbar.delete", "toolbar.copy-username", "toolbar.copy-password");
    final UUID amp = UUID.randomUUID(), proxmox = UUID.randomUUID();
    final Map<UUID, Object> entries = new HashMap<>();
    final List<String> asked = new ArrayList<>();

    static Account login(UUID id, String name, String username, Instant modified) {
        // Each login has its own synthetic password, so a test can tell whose value a view holds.
        return new Account(id, name, username, new Auth.Password(("synthetic-" + name).toCharArray()), modified, modified);
    }

    /** The mock's two logins: amp, modified on the fixture day, and Proxmox, a day earlier. */
    List<VaultManager.Row> rows() {
        entries.put(amp, login(amp, "amp", "ubuntu", MODIFIED));
        entries.put(proxmox, login(proxmox, "Proxmox", "root", MODIFIED.minusSeconds(86_400)));
        return List.of(new VaultManager.Row(amp, "amp", "ubuntu", VaultManager.Type.LOGIN),
            new VaultManager.Row(proxmox, "Proxmox", "root", VaultManager.Type.LOGIN));
    }

    ManagerPanel panel() {
        return new ManagerPanel(new FakeKit("dev.jasper.vault"), Layout.CLASSIC, id -> Optional.ofNullable(entries.get(id)),
            type -> asked.add("add." + type.name()), id -> asked.add("edit " + id), id -> asked.add("delete " + id),
            id -> asked.add("copy-public " + id), grant -> asked.add("revoke"), () -> asked.add("generate"), () -> asked.add("lock"),
            () -> asked.add("unlock"), () -> asked.add("close"));
    }

    static boolean enabled(UiView view, String id) { return find(view, id).orElseThrow().state().enabled(); }

    @Test void theToolbarRowLeadsTheBodyAndTheStatusRowFollows() throws Exception {
        edt(() -> {
            var panel = panel(); var view = panel.view();
            panel.refresh(rows(), List.of(), STATUS, true);
            var body = (Nodes.Split) node(view, ManagerPanel.ROOT, Nodes.Form.class).sections().getFirst();
            assertThat(body.kind()).isEqualTo(Nodes.SplitKind.DOCUMENT);
            assertThat(body.children().getFirst().id()).isEqualTo(ManagerPanel.TOOLBAR);
            assertThat(((Nodes.Split) body.children().get(1)).kind()).isEqualTo(Nodes.SplitKind.THREE_COLUMN);
            var toolbar = node(view, ManagerPanel.TOOLBAR, Nodes.Stack.class);
            assertThat(toolbar.role()).isEqualTo(Nodes.LayoutRole.INLINE);
            assertThat(toolbar.children()).extracting(Nodes.Node::id).containsExactly("toolbar.new", "toolbar.rule.1", "toolbar.edit",
                "toolbar.delete", "toolbar.copy-username", "toolbar.copy-password", "toolbar.rule.2", "toolbar.lock", "toolbar.shortcut",
                ManagerPanel.SEARCH);
            var add = node(view, "toolbar.new", Nodes.MenuButton.class);
            assertThat(add.label()).isEqualTo("New");
            assertThat(add.icon()).isEqualTo("add");
            assertThat(add.items()).extracting(item -> item.action()).containsExactly("add.LOGIN", "add.SSH_KEY", "add.NOTE", "", ManagerPanel.ADD_GENERATE);
            assertThat(button(view, "toolbar.edit").label()).isEqualTo("Edit…");
            assertThat(button(view, "toolbar.edit").action()).isEqualTo(ManagerPanel.EDIT);
            assertThat(button(view, "toolbar.delete").action()).isEqualTo(ManagerPanel.DELETE);
            assertThat(button(view, "toolbar.copy-username").action()).isEqualTo("detail.username.copy");
            assertThat(button(view, "toolbar.copy-password").action()).isEqualTo("detail.password.copy");
            assertThat(button(view, "toolbar.lock").action()).isEqualTo(ManagerPanel.LOCK);
            assertThat(node(view, "toolbar.shortcut", Nodes.Text.class).text()).isEqualTo(ManagerPanel.LOCK_SHORTCUT);
            assertThat(find(panel.view(), ManagerPanel.SEARCH).orElseThrow()).isInstanceOf(Nodes.Field.class);
            for (String verb : VERBS) assertThat(enabled(view, verb)).as(verb + " without a selection").isFalse();
            assertThat(enabled(view, "toolbar.lock")).isTrue();
            emit(view, new UiEvent.Selected(ManagerPanel.ROWS, Set.of(amp.toString())));
            for (String verb : VERBS) assertThat(enabled(view, verb)).as(verb + " with a login").isTrue();
            press(view, ManagerPanel.EDIT);
            assertThat(asked).containsExactly("edit " + amp);
            assertThat(node(view, ManagerPanel.STATUS, Nodes.Stack.class).role()).isEqualTo(Nodes.LayoutRole.INLINE);
            assertThat(node(view, "status.state", Nodes.StatusDot.class).kind()).isEqualTo(Nodes.StatusKind.SUCCESS);
            assertThat(node(view, "status.provider", Nodes.Text.class).text()).isEqualTo("Unlocked · Keychain");
            assertThat(node(view, "status.autolock", Nodes.Text.class).text()).isEqualTo("Auto-lock: 15 min");
            assertThat(node(view, "status.path", Nodes.Text.class).role()).isEqualTo(Nodes.TextRole.MONO_CAPTION);
            assertThat(button(view, ManagerPanel.OKAY).label()).isEqualTo("Done");
            assertThat(button(view, ManagerPanel.OKAY).kind()).isEqualTo(Nodes.ButtonKind.PRIMARY);
            panel.close();
        });
    }

    @Test void lockingDisablesTheToolbarAndClosesTheSheet() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                PluginContext context = host.start(INFO, Set.of(), Set.of(), c -> { });
                host.addTerminalWindow(); WindowHandle owner = context.terminals().windows().getFirst();
                var service = new VaultService(f.lock, () -> Optional.of(owner), prompt -> { }, p -> { }, p -> { },
                    new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerWindow(context, f.lock, f.manager, service, () -> STATUS, () -> { }, Layout.CLASSIC);
                f.manager.saveAccount(login(amp, "amp", "ubuntu", MODIFIED)); f.drain();
                window.show(owner, Optional.of(amp));
                var view = window.panel.view();
                assertThat(window.panel.layout()).isEqualTo(Layout.CLASSIC);
                assertThat(enabled(view, "toolbar.copy-password")).isTrue();
                var sheet = new AtomicReference<EntryEditor>();
                window.dialog("Add Login", close -> {
                    var form = EntryEditor.login(context.kit(), null, f.manager::saveAccount, close); sheet.set(form); return form;
                });
                type(sheet.get().view(), EntryEditor.PASSWORD, "synthetic-draft");
                f.manager.invalidate(); f.lock.lock(); window.changed();
                assertThat(closed(sheet.get().view())).as("locking closes the sheet and erases its draft").isTrue();
                for (String id : List.of("toolbar.new", "toolbar.edit", "toolbar.delete", "toolbar.copy-username", "toolbar.copy-password",
                    "toolbar.lock", ManagerPanel.SEARCH)) assertThat(enabled(view, id)).as(id + " while locked").isFalse();
                assertThat(find(view, "detail")).as("no Details pane while locked").isEmpty();
                assertThat(node(view, "status.provider", Nodes.Text.class).text()).isEqualTo("Locked");
                assertThat(node(view, "status.state", Nodes.StatusDot.class).kind()).isEqualTo(Nodes.StatusKind.IDLE);
                assertThat(node(view, ManagerPanel.LOCKED, Nodes.Empty.class).actions()).extracting(n -> ((Nodes.Button) n).action())
                    .containsExactly(ManagerPanel.UNLOCK);
                window.close();
            }
        });
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicVaultRenderTest.java`:

```java
package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockMatch;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Plan 10b's mock matching (spec 16.1.4): the staged Vault's classic composition in the real host, under the shipped
 * Enterprise kit, Plex fonts and theme (Light: the 07 mocks have no Dark), against the 07 Credential Vault mocks. Images
 * go to jasper-app/build/enterprise-compositions/light/, paired in tools/visual/pairs.json.
 */
class ClassicVaultRenderTest {
    static final String V = "07 Credential Vault/Credential Vault ";
    static final MockMatch.Crop SELECTED = new MockMatch.Crop(V + "3 - Item selected.png", 2, 60, 1640, 1142),
        NO_SELECTION = new MockMatch.Crop(V + "2 - New menu.png", 2, 60, 1640, 1142);
    static final String[][] LOGINS = {{"amp", "ubuntu", "synthetic-password-1"}, {"Proxmox", "root", "synthetic-password-2"}};
    static final String TOOLBAR = "0+6 #ECEDEF, 6+1 #B4B8BF, 7+20 #FFFFFF, 27+1 #B4B8BF, 28+6 #ECEDEF, 34+1 #C4C7CC";
    static final String STATUS = "0+1 #C4C7CC, 1+6 #E6E7EA, 7+1 #2655AE, 8+22 #2F65CA, 30+1 #2655AE, 31+6 #E6E7EA";
    @TempDir Path root;

    /**
     * The staged Vault in Enterprise mode, created (unbound) and unlocked, its manager open. Its views take the Enterprise
     * Light theme through the appearance fixture (without one the host falls back to the bundled default theme).
     */
    static StagedVaultHost vault(Path data) throws Exception {
        var vault = new StagedVaultHost(data, false, onEdtValue(() -> new WorkspaceAppearanceFixture(EnterpriseLook.resolved(false))), true);
        try { vault.createVault("synthetic-host-pass"); } catch (Throwable failure) { vault.close(); throw failure; }
        return vault;
    }

    /**
     * The surface's kit view and everything below it in its holder, {@code width} by {@code height} logical pixels,
     * painted at 2x over {@code surface.dialog}: what the mock crops show under a title bar or a dialog's header strip.
     * The headless windows run no shell, so a dialog's holder and view first take what the Enterprise host gives the
     * dialogs it draws in their window (plan 6b's {@code NativeShells.classicContent}: a transparent holder and
     * {@code CLASSIC_FLAGS}, {@code hostTitled}, {@code dialogBand} and {@code dialogCard}); the manager window's view
     * keeps its own.
     */
    static BufferedImage render(AuxiliarySurface surface, int width, int height) {
        return onEdtValue(() -> {
            var holder = surface.holder();
            if (surface.kind() != AuxiliarySurface.Kind.WINDOW) {
                holder.setOpaque(false);
                var content = StagedVaultHost.kitView(holder);
                content.hostTitled(true); content.dialogBand(true); content.dialogCard(true);
            }
            holder.setSize(width, height);
            for (int i = 0; i < 3; i++) SessionsSurfaceFixture.layout(holder);
            var view = StagedVaultHost.kitView(holder);
            int above = SwingUtilities.convertPoint(view.getParent(), view.getLocation(), holder).y;
            holder.setSize(width, height + above);
            for (int i = 0; i < 3; i++) SessionsSurfaceFixture.layout(holder);
            var image = new BufferedImage(width * 2, (height + above) * 2, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try { graphics.scale(2, 2); holder.printAll(graphics); } finally { graphics.dispose(); }
            Color under = EnterpriseLook.resolved(false).workspace().semantic().color(SemanticColor.SURFACE_DIALOG);
            return MockMatch.onto(image.getSubimage(0, above * 2, width * 2, height * 2), under);
        });
    }

    /** The render's bands down device column {@code x} between device rows {@code top} and {@code bottom} match {@code expected} (±1 px). */
    static void bands(String name, BufferedImage render, int x, int top, int bottom, String expected) {
        assertThat(MockMatch.mismatch(MockMatch.parse(expected), MockMatch.bands(render, x, top, bottom), 1))
            .as(name + " at x " + x + ", rows " + top + ".." + bottom).isEmpty();
    }

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theManagerHasTheMocksToolbarAndStatusRows() throws Exception {
        ClassicCompositionRenderTest.under(false, () -> {
            try (var vault = vault(root.resolve("frame"))) {
                vault.seedLogins(LOGINS);
                var render = render(vault.surface("Credential Vault"), NO_SELECTION.logicalWidth(), NO_SELECTION.logicalHeight());
                MockMatch.save("light/vault-frame", NO_SELECTION.read(), render);
                bands("the toolbar row", render, 1600, 0, 70, TOOLBAR);
                bands("the status row and Done", render, 1600, 1068, 1142, STATUS);
                assertThat(MockMatch.present(render, Color.decode("#C4C7CC"))).as("the classic rules").isTrue();
                assertThat(vault.problems).isEmpty();
            }
        });
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-vault:compileTestJava :jasper-app:compileTestJava --continue`
Expected: FAIL to compile: "no suitable constructor found for ManagerPanel(FakeKit,Layout,…)", "cannot find symbol …
TOOLBAR", "… LOCK_SHORTCUT", "… layout()", "no suitable constructor found for VaultManagerWindow(…,Layout)", "… seedLogins",
"no suitable constructor found for StagedVaultHost(Path,boolean,WorkspaceAppearanceFixture,boolean)".

- [ ] **Step 3: Compose the classic frame and the host mode**

```bash
python3 -I - <<'PY'
"""Plan 10b Task 3 step 3: the classic manager's toolbar and status rows, its window, and StagedVaultHost's Enterprise mode."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
U = "plugins/vault/src/main/java/dev/jasper/vault/ui/"
edit(U + "ManagerPanel.java", [
    ('''import dev.jasper.vault.service.UnlockPrompt;\n''', '''import dev.jasper.vault.service.UnlockPrompt;\nimport dev.jasper.vault.workstation.Layout;\n''', 1),
    ('''        ADD_GENERATE = "add.generate", UNLOCK_PREFIX = "unlock.";\n''', r'''        ADD_GENERATE = "add.generate", UNLOCK_PREFIX = "unlock.", TOOLBAR = "toolbar";
    /** The Lock Vault action's default binding as the platform writes it (plan 10b Rulings 8). */
    static final String LOCK_SHORTCUT = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac") ? "\u21E7\u2318L" : "Ctrl+Shift+L";
''', 1),
    ('''    private final UiView view, titleActions;\n''', '''    private final UiView view, titleActions;\n    private final Layout layout;\n''', 1),
    ('''    ManagerPanel(UiKit kit, Function<UUID, Optional<Object>> entry, Consumer<VaultManager.Type> onAdd,
                 Consumer<UUID> onEdit, Consumer<UUID> onDelete, Consumer<UUID> onCopy,
                 Consumer<Grant> onRevoke, Runnable onGenerate,
                 Runnable onLock, Runnable onUnlock, Runnable onClose) {
        this.entry = entry;''', '''    ManagerPanel(UiKit kit, Function<UUID, Optional<Object>> entry, Consumer<VaultManager.Type> onAdd,
                 Consumer<UUID> onEdit, Consumer<UUID> onDelete, Consumer<UUID> onCopy,
                 Consumer<Grant> onRevoke, Runnable onGenerate,
                 Runnable onLock, Runnable onUnlock, Runnable onClose) {
        this(kit, Layout.ROWS, entry, onAdd, onEdit, onDelete, onCopy, onRevoke, onGenerate, onLock, onUnlock, onClose);
    }

    /** The manager in {@code layout}'s composition (Enterprise presentation spec 14.8), kept for its life. */
    ManagerPanel(UiKit kit, Layout layout, Function<UUID, Optional<Object>> entry, Consumer<VaultManager.Type> onAdd,
                 Consumer<UUID> onEdit, Consumer<UUID> onDelete, Consumer<UUID> onCopy,
                 Consumer<Grant> onRevoke, Runnable onGenerate,
                 Runnable onLock, Runnable onUnlock, Runnable onClose) {
        this.layout = layout;
        this.entry = entry;''', 1),
    ('''    UiView view() { return view; }\n''', '''    UiView view() { return view; }\n    Layout layout() { return layout; }\n''', 1),
    ('''        Nodes.Node body = !unlocked ? new Nodes.Stack("locked.body", ControlState.READY, Nodes.LayoutRole.DETAIL, lockedCard())
            : card == Card.GRANTS ? new Nodes.Stack("grants.body", ControlState.READY, Nodes.LayoutRole.DETAIL, grantsCard()) : entries();''',
     '''        Nodes.Node body = !unlocked ? new Nodes.Stack("locked.body", ControlState.READY, Nodes.LayoutRole.DETAIL, lockedCard())
            : card == Card.GRANTS ? new Nodes.Stack("grants.body", ControlState.READY, Nodes.LayoutRole.DETAIL, grantsCard())
            : layout == Layout.CLASSIC ? classicEntries() : entries();
        if (layout == Layout.CLASSIC) {
            // One section: the toolbar row over the body, which fills (plan 10b Rulings 3); the passive locked state centres.
            if (!unlocked && unlock == null) body = lockedCard().getFirst();
            body = new Nodes.Split("classic", ControlState.READY, Nodes.SplitKind.DOCUMENT, List.of(toolbar(), body));
        }''', 1),
    ('''            Optional.of(status()), actions, prompting ? unlock.cancel : CLOSE);''',
     '''            Optional.of(layout == Layout.CLASSIC ? classicStatus() : status()), actions, prompting ? unlock.cancel : CLOSE);''', 1),
    ('''    /** The window closed (its surface already disposed the view and erased its storage) or is closing. */''',
     '''    /** The classic columns: today's rail, list and detail with the search moved into the toolbar row (Tasks 4 and 5 recompose them). */
    private Nodes.Node classicEntries() {
        var split = (Nodes.Split) entries();
        var browser = (Nodes.Panel) split.children().get(1);
        return new Nodes.Split(split.id(), split.state(), split.kind(), List.of(split.children().get(0),
            new Nodes.Panel(browser.id(), browser.state(), browser.header(), Optional.empty(), browser.body(), browser.footer()),
            split.children().get(2)));
    }

    /**
     * The classic toolbar row (spec 14.8, plan 10b Rulings 3): + New, the selection's verbs, Lock Vault and its shortcut,
     * then the search, which the bar puts at its trailing edge. The verbs run the same actions as the rows composition's.
     */
    private Nodes.Stack toolbar() {
        var row = visible.stream().filter(item -> item.id().equals(selection)).findFirst().orElse(null);
        Object value = row == null ? null : entry.apply(row.id()).orElse(null);
        boolean chosen = unlocked && card == Card.ENTRIES && row != null;
        boolean account = chosen && value instanceof Account;
        boolean secret = account && password(((Account) value).auth()) != null;
        var adds = new ArrayList<MenuItem>();
        for (var type : VaultManager.Type.values()) adds.add(new MenuItem("add." + type.name(), type.toString(), icon(type), "add." + type.name(), true, false, hue(type)));
        adds.add(new MenuItem("add.separator", "", "", "", true, true));
        adds.add(new MenuItem(ADD_GENERATE, "Generate SSH Key...", "sparkle", ADD_GENERATE, true, false, Nodes.Hue.PURPLE));
        return new Nodes.Stack(TOOLBAR, ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.MenuButton(TOOLBAR + ".new", new ControlState(true, unlocked, false, ""), "New", "add", Nodes.ButtonKind.GHOST, "", adds, "Add entry"),
            new Nodes.Separator(TOOLBAR + ".rule.1", ControlState.READY),
            verb("edit", "Edit…", EDIT, chosen), verb("delete", "Delete", DELETE, chosen),
            verb("copy-username", "Copy Username", "detail.username.copy", account),
            verb("copy-password", "Copy Password", "detail.password.copy", secret),
            new Nodes.Separator(TOOLBAR + ".rule.2", ControlState.READY),
            verb("lock", "Lock Vault", LOCK, unlocked),
            text(TOOLBAR + ".shortcut", LOCK_SHORTCUT, Nodes.TextRole.CAPTION),
            new Nodes.Field(SEARCH, new ControlState(true, unlocked && card == Card.ENTRIES, false, ""), Nodes.FieldKind.SEARCH, query,
                "Search vault", "Search vault", "")));
    }

    private static Nodes.Button verb(String id, String label, String action, boolean enabled) {
        return new Nodes.Button(TOOLBAR + "." + id, new ControlState(true, enabled, false, ""), label, "", Nodes.ButtonKind.GHOST, action, "");
    }

    /** The classic status row (spec 14.8): the lock state with its dot and store, the auto-lock and the vault file, between rules. */
    private Nodes.Node classicStatus() {
        String state = !unlocked ? "Locked" : status.deviceSecret().isEmpty() ? "Unlocked" : "Unlocked · " + sentence(status.deviceSecret());
        return new Nodes.Stack(STATUS, ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.StatusDot("status.state", ControlState.READY, unlocked ? Nodes.StatusKind.SUCCESS : Nodes.StatusKind.IDLE),
            text("status.provider", state, Nodes.TextRole.CAPTION),
            new Nodes.Separator("status.rule.1", ControlState.READY),
            text("status.autolock", "Auto-lock: " + status.autoLock(), Nodes.TextRole.CAPTION),
            new Nodes.Separator("status.rule.2", ControlState.READY),
            text("status.path", status.path(), Nodes.TextRole.MONO_CAPTION)));
    }

    /** The window closed (its surface already disposed the view and erased its storage) or is closing. */''', 1)])
edit(U + "VaultManagerWindow.java", [
    ('''import dev.jasper.vault.service.VaultService;\n''', '''import dev.jasper.vault.service.VaultService;\nimport dev.jasper.vault.workstation.Layout;\n''', 1),
    ('''    private final EntryPresenter editors;\n''', '''    private final EntryPresenter editors;\n    private final Layout layout;\n''', 1),
    ('''    public VaultManagerWindow(PluginContext context, LockManager lock, VaultManager manager,
                              VaultService service, Supplier<Status> status, Runnable generate) {
        this.context = context; this.lock = lock; this.manager = manager; this.service = service;''',
     '''    public VaultManagerWindow(PluginContext context, LockManager lock, VaultManager manager,
                              VaultService service, Supplier<Status> status, Runnable generate) {
        this(context, lock, manager, service, status, generate, Layout.ROWS);
    }

    /** The manager window in {@code layout}'s composition (Enterprise presentation spec 14.8). */
    public VaultManagerWindow(PluginContext context, LockManager lock, VaultManager manager,
                              VaultService service, Supplier<Status> status, Runnable generate, Layout layout) {
        this.layout = layout;
        this.context = context; this.lock = lock; this.manager = manager; this.service = service;''', 1),
    ('''new WindowSpec(ID, "Credential Vault", new Dimension(900, 555), true, WindowSpec.SizeBasis.CONTENT)''',
     '''new WindowSpec(ID, "Credential Vault", layout == Layout.CLASSIC ? new Dimension(820, 571) : new Dimension(900, 555), true, WindowSpec.SizeBasis.CONTENT)''', 1),
    ('''            var shown = new ManagerPanel(context.kit(), manager::entry,''', '''            var shown = new ManagerPanel(context.kit(), layout, manager::entry,''', 1),
    ('''            window.setTitleActions(Optional.of(shown.titleActions()));''',
     '''            // The classic toolbar row carries the title actions (plan 10b Rulings 3).
            window.setTitleActions(layout == Layout.CLASSIC ? Optional.empty() : Optional.of(shown.titleActions()));''', 1)])
edit("plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java", [
    ('''managerWindow = new VaultManagerWindow(context, lock, manager, service, this::managerStatus, this::showGenerator);''',
     '''managerWindow = new VaultManagerWindow(context, lock, manager, service, this::managerStatus, this::showGenerator, layout);''', 1)])
H = "jasper-app/src/test/java/dev/jasper/app/plugins/StagedVaultHost.java"
edit(H, [
    ('''    final UUID window;\n''', '''    final UUID window;
    /** The staged Enterprise plugin's loader in Enterprise mode (plan 10b), else null. */
    final PluginClassLoader enterpriseLoader;\n''', 1),
    ('''    StagedVaultHost(Path data, boolean dark, WorkspaceAppearanceFixture appearance) throws Exception {\n''',
     '''    StagedVaultHost(Path data, boolean dark, WorkspaceAppearanceFixture appearance) throws Exception { this(data, dark, appearance, false); }

    /**
     * {@code enterprise}: the staged Enterprise plugin starts first in the same host and the Vault may see its API, so it
     * composes its classic layout (plan 10b). The caller installs the Enterprise kit and theme first
     * ({@code EnterpriseLook.controller}).
     */
    StagedVaultHost(Path data, boolean dark, WorkspaceAppearanceFixture appearance, boolean enterprise) throws Exception {\n''', 1),
    ('''            PluginRuntime.ShellProfiles.NONE, new CopyOnWriteArrayList<>(), appearance, GlassSource.NONE);\n''',
     '''            PluginRuntime.ShellProfiles.NONE, new CopyOnWriteArrayList<>(), appearance, GlassSource.NONE);
        enterpriseLoader = enterprise ? StagedEnterprise.start(host) : null;\n''', 1),
    ('''ManagedVaultLoadingTest.jars(staged), getClass().getClassLoader(), Map.of());''',
     '''ManagedVaultLoadingTest.jars(staged), getClass().getClassLoader(),
            enterprise ? Map.of("dev.jasper.enterprise.api", enterpriseLoader) : Map.of());''', 1),
    ('''            host.start(new HostedPlugin(VAULT, Set.of(), Set.of(), Set.of("dev.jasper.vault.api"), loader, () -> plugin));''',
     '''            host.start(new HostedPlugin(VAULT, Set.of(), enterprise ? Set.of("dev.jasper.enterprise") : Set.of(), Set.of("dev.jasper.vault.api"),
                loader, () -> plugin));''', 1),
    ('''            onEdt(auxiliary::close);
            loader.close();''', '''            onEdt(auxiliary::close);
            try { loader.close(); } finally { if (enterpriseLoader != null) enterpriseLoader.close(); }''', 1),
    ('''    static Component named(Container root, String name) {''', '''    /** Saves synthetic logins ({name, username, password}) through the Vault's own manager, as an editor's Save does (plan 10b). */
    List<UUID> seedLogins(String[][] logins) throws Exception {
        var managerField = plugin.getClass().getDeclaredField("manager"); managerField.setAccessible(true);
        Object manager = onEdtValue(() -> { try { return managerField.get(plugin); } catch (ReflectiveOperationException e) { throw new AssertionError(e); } });
        Class<?> account = loader.loadClass("dev.jasper.vault.model.Account"), auth = loader.loadClass("dev.jasper.vault.model.Auth");
        Class<?> password = loader.loadClass("dev.jasper.vault.model.Auth$Password");
        var modified = java.time.Instant.parse("2026-09-24T12:00:00Z");
        var ids = new java.util.ArrayList<UUID>();
        for (String[] login : logins) {
            UUID id = UUID.randomUUID(); ids.add(id);
            Object value = account.getConstructor(UUID.class, String.class, String.class, auth, java.time.Instant.class, java.time.Instant.class)
                .newInstance(id, login[0], login[1], password.getConstructor(char[].class).newInstance((Object) login[2].toCharArray()), modified, modified);
            invokeSave(manager, "saveAccount", account, value);
        }
        settle();
        return List.copyOf(ids);
    }

    /** Delivers {@code event} to the open manager's view, as its kit would from a click or a key (plan 10b). */
    void dispatchToManager(dev.jasper.sdk.ui.UiEvent event) throws InterruptedException {
        onEdt(() -> {
            try {
                Object window = field(plugin, "managerWindow"), panel = field(window, "panel");
                var view = panel.getClass().getDeclaredMethod("view"); view.setAccessible(true);
                ((HostedKitView) view.invoke(panel)).dispatch(event);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        settle();
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var declared = owner.getClass().getDeclaredField(name); declared.setAccessible(true); return declared.get(owner);
    }

    static Component named(Container root, String name) {''', 1)])
PY
```

`text(...)`, `icon(...)`, `hue(...)`, `password(...)` and `sentence(...)` are `ManagerPanel`'s existing helpers
(`ManagerPanel.java:182-184,232-234,368`). "⇧⌘L" is written as the Java escapes `\u21E7\u2318L` (the raw Python
string keeps the backslashes, so the source holds the escape); `java.util.Locale` is already imported.

- [ ] **Step 4: Run the tests and compare with the mock**

First add the two ledger rows (Execution notes R6), each checked against the set of first fields so a test column
never counts as a row:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv")
text = path.read_text(encoding="utf-8")
sources = {line.split(",", 1)[0] for line in text.splitlines()}
rows = [
    ("plugins/vault/src/test/java/dev/jasper/vault/ui/ClassicManagerTest.java", "dev.jasper.vault", "ClassicManagerTest", "New in Enterprise plan 10b Task 3 (spec 14.8)"),
    ("jasper-app/src/test/java/dev/jasper/app/plugins/ClassicVaultRenderTest.java", "app.plugins", "ClassicVaultRenderTest", "New in Enterprise plan 10b Task 3 (spec 16.1.4)"),
]
added = 0
for source, owner, surface, note in rows:
    if source in sources: continue
    text += f"{source},{owner},{surface},nonvisual-helper,none,none,{source},Test/fixture ownership; {note}\n"; added += 1
path.write_text(text, encoding="utf-8")
print("added", added)
PY
```

Expected: `added 2`. Then run:
`./gradlew :jasper-plugin-vault:test --tests 'dev.jasper.vault.ui.*' --tests 'dev.jasper.vault.VaultPluginTest' :jasper-app:test --tests 'dev.jasper.app.plugins.ClassicVaultRenderTest' --tests 'dev.jasper.app.plugins.VaultRenderEvidenceTest' --tests 'dev.jasper.app.plugins.ManagedVaultLoadingTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `ClassicManagerTest` (2), the Vault's existing UI tests unchanged (they build the rows composition through
the 6- and 11-argument constructors), `ClassicVaultRenderTest` (1), the default evidence renders.

If a band of `theManagerHasTheMocksToolbarAndStatusRows` differs, open `jasper-app/build/enterprise-compositions/light/vault-frame-side.png`
(the mock beside the render) and find the element at fault: a toolbar taller than 34 is the bar's tallest child
(compare the GHOST buttons' and the field's heights with `BUTTON_HEIGHT` and `FIELD_HEIGHT`); a missing `#C4C7CC` rule
is Task 1's hairline; a status row other than 36 is the WINDOW footer (plan 6's). Fix the composition or Task 1's
classic value, never the expected bands, and run again until it passes; a difference that belongs to another plan's
surface goes into the banner as a residual with its measured bands (Execution notes R3, R4).

- [ ] **Step 5: Commit**

```bash
git add plugins/vault/src jasper-app/src/test docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Vault compositions: the classic manager window, toolbar and status rows

Under the Enterprise layout the manager is one document split: the
toolbar row (+ New, Edit..., Delete, Copy Username, Copy Password, Lock
Vault with its shortcut, the search) over its columns, and the status
row reads the lock state, the auto-lock and the vault file between
rules. The window is 820 x 571 and carries no title actions. The staged
Vault gains an Enterprise mode and the render matches the mock's rows.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: The Categories tree and the sortable item table

Rulings 5 and 9: the rail is a Categories tree (All Items over Logins, SSH Keys and Secure Notes, each with its count)
under a header strip; the list is a Name/Username/Type/Modified table whose Name cells carry the kind's swatch, sorted by
a header click, over a footer counting what shows. A search that matches nothing keeps the headers; the sort survives a
refresh.

**Files:**
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/ui/ManagerPanel.java` (imports, `:54-55`, `handle` `:128-146`,
  `filter` `:187-193`, Task 3's `classicEntries()`, new helpers)
- Modify: `plugins/vault/src/test/java/dev/jasper/vault/ui/ClassicManagerTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicVaultRenderTest.java`

**Interfaces:**
- Consumes: Task 1's columns and pane strips; Task 3's `classicEntries()`, `ClassicVaultRenderTest.render`, `bands`,
  `StagedVaultHost.dispatchToManager`; 3c's `Nodes.Table` (with `empty`, `emptyText`), `TableRow`, `Column`; SDK `TreeItem`.
- Produces: `ManagerPanel.ITEMS_FOOTER` ("items.footer"), `COLUMN_NAME`, `COLUMN_USERNAME`, `COLUMN_TYPE`,
  `COLUMN_MODIFIED` ("name", "username", "type", "modified"); the classic `KIND` node is a `Nodes.Tree`, the classic
  `ROWS` node a `Nodes.Table` (activate `EDIT`); the header `categories.header` is visible under CLASSIC; private
  `username(Row)`, `modified(Row)` (Task 5 uses them).

- [ ] **Step 1: Write the failing tests**

Add to `ClassicManagerTest` the imports `dev.jasper.sdk.ui.Column`, `dev.jasper.sdk.ui.TableRow`, `dev.jasper.sdk.ui.TreeItem`,
`java.time.ZoneId`, `java.time.format.DateTimeFormatter` and `java.util.Locale`, and these members before its closing brace:

```java
    static String date(Instant timestamp) {
        return DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH).withZone(ZoneId.systemDefault()).format(timestamp);
    }

    @Test void theCategoriesAreATreeWithCountsAndTheItemsATable() throws Exception {
        edt(() -> {
            var panel = panel(); var view = panel.view();
            panel.refresh(rows(), List.of(), STATUS, true);
            var tree = node(view, ManagerPanel.KIND, Nodes.Tree.class);
            var all = tree.roots().getFirst();
            assertThat(all.title()).isEqualTo("All Items");
            assertThat(all.count()).hasValue(2);
            assertThat(all.children()).extracting(TreeItem::title).containsExactly("Logins", "SSH Keys", "Secure Notes");
            assertThat(all.children()).extracting(item -> item.count().getAsInt()).containsExactly(2, 0, 0);
            assertThat(tree.expandedIds()).containsExactly(ManagerPanel.ALL_TYPES);
            assertThat(tree.selectedIds()).containsExactly(ManagerPanel.ALL_TYPES);
            assertThat(node(view, "categories.header", Nodes.Header.class).state().visible()).isTrue();
            var table = node(view, ManagerPanel.ROWS, Nodes.Table.class);
            assertThat(table.columns()).extracting(Column::title).containsExactly("Name", "Username", "Type", "Modified");
            assertThat(table.columns().getFirst().sorted()).hasValue(true);
            assertThat(table.activateAction()).as("Enter and double-click edit").isEqualTo(ManagerPanel.EDIT);
            assertThat(table.rows()).extracting(TableRow::id).containsExactly(amp.toString(), proxmox.toString());
            var first = table.rows().getFirst();
            assertThat(first.cells()).containsEntry(ManagerPanel.COLUMN_USERNAME, "ubuntu").containsEntry(ManagerPanel.COLUMN_TYPE, "Login")
                .containsEntry(ManagerPanel.COLUMN_MODIFIED, date(MODIFIED));
            var name = (Nodes.Stack) first.cellNodes().get(ManagerPanel.COLUMN_NAME);
            var swatch = (Nodes.Icon) name.children().getFirst();
            assertThat(swatch.size()).isEqualTo(Nodes.IconSize.SWATCH);
            assertThat(swatch.hue()).isEqualTo(Nodes.Hue.BLUE);
            assertThat(((Nodes.Text) name.children().get(1)).text()).isEqualTo("amp");
            assertThat(node(view, ManagerPanel.ITEMS_FOOTER, Nodes.Text.class).text()).isEqualTo("2 of 2 items");
            emit(view, new UiEvent.Expanded(ManagerPanel.KIND, ManagerPanel.ALL_TYPES, false));
            assertThat(node(view, ManagerPanel.KIND, Nodes.Tree.class).expandedIds()).as("the user collapsed All Items").isEmpty();
            emit(view, new UiEvent.Selected(ManagerPanel.KIND, Set.of(VaultManager.Type.SSH_KEY.name())));
            assertThat(node(view, ManagerPanel.ITEMS_FOOTER, Nodes.Text.class).text()).isEqualTo("0 of 0 items");
            panel.close();
        });
    }

    @Test void aSearchThatMatchesNothingKeepsTheHeadersAndCountsZero() throws Exception {
        edt(() -> {
            var panel = panel(); var view = panel.view();
            panel.refresh(rows(), List.of(), STATUS, true);
            emit(view, new UiEvent.Selected(ManagerPanel.ROWS, Set.of(amp.toString())));
            emit(view, new UiEvent.Selected(ManagerPanel.KIND, Set.of(VaultManager.Type.LOGIN.name())));
            emit(view, new UiEvent.Edited(ManagerPanel.SEARCH, "no such entry"));
            var table = node(view, ManagerPanel.ROWS, Nodes.Table.class);
            assertThat(table.rows()).isEmpty();
            assertThat(table.columns()).hasSize(4);
            assertThat(table.empty()).as("no empty state replaces the headers").isEmpty();
            assertThat(table.emptyText()).isEqualTo("No matching entries");
            assertThat(node(view, ManagerPanel.ITEMS_FOOTER, Nodes.Text.class).text()).isEqualTo("0 of 2 items");
            for (String verb : VERBS) assertThat(enabled(view, verb)).as(verb + " without a shown selection").isFalse();
            emit(view, new UiEvent.Selected(ManagerPanel.KIND, Set.of(ManagerPanel.ALL_TYPES)));
            assertThat(node(view, ManagerPanel.ITEMS_FOOTER, Nodes.Text.class).text()).isEqualTo("0 of 2 items");
            assertThat(node(view, ManagerPanel.ROWS, Nodes.Table.class).emptyText()).isEqualTo("No matching entries");
            panel.close();
        });
    }

    @Test void theSortSurvivesARefreshAndTheSelectionStays() throws Exception {
        edt(() -> {
            var panel = panel(); var view = panel.view();
            var rows = rows();
            panel.refresh(rows, List.of(), STATUS, true);
            emit(view, new UiEvent.Selected(ManagerPanel.ROWS, Set.of(proxmox.toString())));
            emit(view, new UiEvent.Sorted(ManagerPanel.ROWS, ManagerPanel.COLUMN_MODIFIED));
            assertThat(node(view, ManagerPanel.ROWS, Nodes.Table.class).rows()).extracting(TableRow::id).as("oldest first")
                .containsExactly(proxmox.toString(), amp.toString());
            emit(view, new UiEvent.Sorted(ManagerPanel.ROWS, ManagerPanel.COLUMN_MODIFIED));
            panel.refresh(rows, List.of(), STATUS, true);
            var table = node(view, ManagerPanel.ROWS, Nodes.Table.class);
            assertThat(table.rows()).extracting(TableRow::id).as("newest first, after the refresh").containsExactly(amp.toString(), proxmox.toString());
            assertThat(table.selectedIds()).containsExactly(proxmox.toString());
            assertThat(table.columns()).filteredOn(column -> column.sorted().isPresent()).singleElement().satisfies(column -> {
                assertThat(column.id()).isEqualTo(ManagerPanel.COLUMN_MODIFIED);
                assertThat(column.sorted()).hasValue(false);
            });
            panel.close();
        });
    }
```

Add to `ClassicVaultRenderTest` the imports `dev.jasper.sdk.ui.UiEvent` and `java.util.Set`, and:

```java
    static final String CATEGORIES = "0+34 #ECEDEF, 34+1 #C4C7CC, 35+24 #E1E3E6, 59+1 #C4C7CC, 60+4 #F7F7F8, 64+22 #2F65CA, 86+448 #F7F7F8, 534+1 #C4C7CC";
    static final String ITEMS = "0+20 #F0F1F3, 20+1 #D3D6DA, 21+22 #2F65CA, 43+22 #F7F8FA, 65+413 #FFFFFF, 478+1 #DDE0E4, 479+20 #F7F8FA, 499+1 #C4C7CC";

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theCategoriesAndItemsMatchTheItemSelectedMock() throws Exception {
        ClassicCompositionRenderTest.under(false, () -> {
            try (var vault = vault(root.resolve("columns"))) {
                var ids = vault.seedLogins(LOGINS);
                vault.dispatchToManager(new UiEvent.Selected("rows", Set.of(ids.getFirst().toString())));
                var render = render(vault.surface("Credential Vault"), SELECTED.logicalWidth(), SELECTED.logicalHeight());
                MockMatch.save("light/vault-columns", SELECTED.read(), render);
                bands("the Categories column", render, 6, 0, 1070, CATEGORIES);
                bands("the Items column", render, 854, 70, 1070, ITEMS);
                assertThat(MockMatch.present(render, Color.decode("#F0F1F3"))).as("the table's header band").isTrue();
                assertThat(MockMatch.present(render, Color.decode("#E1E3E6"))).as("the pane header strip").isTrue();
                assertThat(vault.problems).isEmpty();
            }
        });
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-vault:test --tests 'dev.jasper.vault.ui.ClassicManagerTest' --continue`
Expected: FAIL to compile: "cannot find symbol … ITEMS_FOOTER", "… COLUMN_NAME"; after those exist, the tree and table
assertions fail ("expected: Tree but was: ItemList").

- [ ] **Step 3: Compose the tree and the table**

```bash
python3 -I - <<'PY'
"""Plan 10b Task 4 step 3: the Categories tree, the item table, its sort and its footer."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
U = "plugins/vault/src/main/java/dev/jasper/vault/ui/ManagerPanel.java"
edit(U, [
    ('''import dev.jasper.sdk.ui.ControlState;\n''', '''import dev.jasper.sdk.ui.Column;\nimport dev.jasper.sdk.ui.ControlState;\n''', 1),
    ('''import dev.jasper.sdk.ui.UiEvent;\n''', '''import dev.jasper.sdk.ui.TableRow;\nimport dev.jasper.sdk.ui.TreeItem;\nimport dev.jasper.sdk.ui.UiEvent;\n''', 1),
    ('''import java.util.Comparator;\n''', '''import java.util.Comparator;\nimport java.util.HashSet;\n''', 1),
    ('''import java.util.Locale;\n''', '''import java.util.Locale;\nimport java.util.Map;\n''', 1),
    ('''    static final String ALL_TYPES = "all";\n''', '''    static final String ALL_TYPES = "all";
    /** The classic table's columns and footer (spec 14.8). */
    static final String COLUMN_NAME = "name", COLUMN_USERNAME = "username", COLUMN_TYPE = "type", COLUMN_MODIFIED = "modified",
        ITEMS_FOOTER = "items.footer";\n''', 1),
    ('''    private String query = "", kind = ALL_TYPES;\n''', '''    private String query = "", kind = ALL_TYPES;
    /** The classic table's sort (a header click) and the tree items the user collapsed; both survive refreshes. */
    private String sortColumn = COLUMN_NAME;
    private boolean ascending = true;
    private final Set<String> collapsed = new HashSet<>();\n''', 1),
    ('''            case UiEvent.Action action -> act(action.id());''', '''            case UiEvent.Sorted sorted when sorted.id().equals(ROWS) -> {
                if (sortColumn.equals(sorted.columnId())) ascending = !ascending; else { sortColumn = sorted.columnId(); ascending = true; }
                filter(); render();
            }
            case UiEvent.Expanded expanded when expanded.id().equals(KIND) -> {
                if (expanded.expanded()) collapsed.remove(expanded.itemId()); else collapsed.add(expanded.itemId());
                render();
            }
            case UiEvent.Action action -> act(action.id());''', 1),
    ('''            .sorted(Comparator.comparing(VaultManager.Row::type).thenComparing(VaultManager.Row::name, String.CASE_INSENSITIVE_ORDER)).toList();''',
     '''            .sorted(layout == Layout.CLASSIC ? classicOrder()
                : Comparator.comparing(VaultManager.Row::type).thenComparing(VaultManager.Row::name, String.CASE_INSENSITIVE_ORDER)).toList();''', 1),
    ('''    /** The classic columns: today's rail, list and detail with the search moved into the toolbar row (Tasks 4 and 5 recompose them). */
    private Nodes.Node classicEntries() {
        var split = (Nodes.Split) entries();
        var browser = (Nodes.Panel) split.children().get(1);
        return new Nodes.Split(split.id(), split.state(), split.kind(), List.of(split.children().get(0),
            new Nodes.Panel(browser.id(), browser.state(), browser.header(), Optional.empty(), browser.body(), browser.footer()),
            split.children().get(2)));
    }''', '''    /** The classic columns (spec 14.8): the Categories tree with counts, the item table over its footer, and the details. */
    private Nodes.Node classicEntries() {
        var kinds = new ArrayList<TreeItem>();
        for (var type : VaultManager.Type.values())
            kinds.add(new TreeItem(type.name(), plural(type), Optional.of(icon(type)), true, List.of(), OptionalInt.of(count(type)), false));
        var all = new TreeItem(ALL_TYPES, "All Items", Optional.of("menu"), false, kinds, OptionalInt.of(allRows.size()), false);
        var tree = new Nodes.Tree(KIND, ControlState.READY, List.of(all), collapsed.contains(ALL_TYPES) ? Set.of() : Set.of(ALL_TYPES), Set.of(kind), "");
        var rail = new Nodes.Panel("categories", ControlState.READY, header("categories.header", "Categories"), Optional.empty(), tree, Optional.empty());
        int scoped = kind.equals(ALL_TYPES) ? allRows.size() : count(VaultManager.Type.valueOf(kind));
        var browser = new Nodes.Panel("browser", ControlState.READY, hiddenHeader("browser.header", "Items"), Optional.empty(), classicTable(),
            Optional.of(text(ITEMS_FOOTER, visible.size() + " of " + scoped + (scoped == 1 ? " item" : " items"), Nodes.TextRole.CAPTION)));
        return new Nodes.Split("columns", ControlState.READY, Nodes.SplitKind.THREE_COLUMN, List.of(rail, browser, detail()));
    }

    /** The item table: the kind's swatch and the name, then the username, the kind and the date; a header click sorts it. */
    private Nodes.Table classicTable() {
        var columns = List.of(column(COLUMN_NAME, "Name", Nodes.TextRole.BODY, 111), column(COLUMN_USERNAME, "Username", Nodes.TextRole.MONO, 80),
            column(COLUMN_TYPE, "Type", Nodes.TextRole.DESCRIPTION, 72), column(COLUMN_MODIFIED, "Modified", Nodes.TextRole.DESCRIPTION, 85));
        Optional<Nodes.Empty> none = allRows.isEmpty() ? Optional.of(new Nodes.Empty(EMPTY, ControlState.READY,
            new Nodes.Icon(EMPTY + ".icon", ControlState.READY, "search", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Nothing to show"),
            "Nothing to show", "", List.of())) : Optional.empty();
        return new Nodes.Table(ROWS, ControlState.READY, columns, visible.stream().map(this::tableRow).toList(),
            selected().map(id -> Set.of(id.toString())).orElse(Set.of()), EDIT, List.of(), Nodes.SelectionMode.SINGLE,
            Nodes.ScrollPolicy.STANDARD, none, allRows.isEmpty() ? "" : "No matching entries");
    }

    private Column column(String id, String title, Nodes.TextRole role, double weight) {
        return new Column(id, title, role, false, weight, sortColumn.equals(id) ? Optional.of(ascending) : Optional.empty());
    }

    private TableRow tableRow(VaultManager.Row row) {
        String id = row.id().toString();
        var name = new Nodes.Stack("row." + id + ".name", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Icon("row." + id + ".icon", ControlState.READY, icon(row.type()), hue(row.type()), Nodes.IconSize.SWATCH, row.type().toString()),
            text("row." + id + ".title", row.name(), Nodes.TextRole.BODY)));
        return new TableRow(id, Map.of(COLUMN_USERNAME, username(row), COLUMN_TYPE, row.type().toString(),
                COLUMN_MODIFIED, modified(row).map(ManagerPanel::date).orElse("")),
            Optional.empty(), false, Optional.empty(), row.name() + ", " + row.type(), Map.of(COLUMN_NAME, name), Optional.empty());
    }

    /** The classic order: the sorted column, then the name and the id, so equal keys keep a stable order. */
    private Comparator<VaultManager.Row> classicOrder() {
        Comparator<VaultManager.Row> order = switch (sortColumn) {
            case COLUMN_USERNAME -> Comparator.comparing(this::username, String.CASE_INSENSITIVE_ORDER);
            case COLUMN_TYPE -> Comparator.comparing(VaultManager.Row::type);
            case COLUMN_MODIFIED -> Comparator.comparing(row -> modified(row).orElse(java.time.Instant.EPOCH));
            default -> Comparator.comparing(VaultManager.Row::name, String.CASE_INSENSITIVE_ORDER);
        };
        order = order.thenComparing(VaultManager.Row::name, String.CASE_INSENSITIVE_ORDER).thenComparing(row -> row.id().toString());
        return ascending ? order : order.reversed();
    }

    private String username(VaultManager.Row row) {
        return entry.apply(row.id()).map(value -> value instanceof Account account ? account.username() : "—").orElse("—");
    }

    private Optional<java.time.Instant> modified(VaultManager.Row row) {
        return entry.apply(row.id()).map(value -> switch (value) {
            case Account account -> account.updated();
            case SshKey key -> key.created();
            case ManagedSshKey key -> key.created();
            case Note note -> note.updated();
            default -> null;
        });
    }

    private int count(VaultManager.Type type) { return (int) allRows.stream().filter(row -> row.type() == type).count(); }

    private static String plural(VaultManager.Type type) {
        return switch (type) { case LOGIN -> "Logins"; case SSH_KEY -> "SSH Keys"; case NOTE -> "Secure Notes"; };
    }

    private static Nodes.Header header(String id, String title) {
        return new Nodes.Header(id, ControlState.READY, title, OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
    }''', 1)])
PY
```

Under ROWS nothing changes: `filter` keeps its order, and `handle`'s two new cases only see a Table's sort and a Tree's
expansion, which the rows composition never shows.

- [ ] **Step 4: Run the tests and compare with the mock**

Run: `./gradlew :jasper-plugin-vault:test --tests 'dev.jasper.vault.ui.*' :jasper-app:test --tests 'dev.jasper.app.plugins.ClassicVaultRenderTest' --continue`
Expected: PASS: `ClassicManagerTest` (5), `ClassicVaultRenderTest` (2), the Vault's other UI tests unchanged.

If the Categories band differs, the order of failure tells the cause: a strip that is not 24 (`#E1E3E6`) is plan 7's
strip height (`PANEL_HEADER` 24 in the classic catalog); a first row not 4 below the strip is the tree's top inset
(compare `COLUMN_RAIL_TOP`); a selected row not 22 is `ROW_HEIGHT`. If the Items band differs, a header band other
than 20 is 4c's table header; a missing zebra row is 4c's `ClassicTableFace` stripes; a footer other than 20 on
`#F7F8FA` is the panel footer. Fix the composition or a classic value, never the expected bands, and record any
residual with its measured bands in the banner.

- [ ] **Step 5: Commit**

```bash
git add plugins/vault/src jasper-app/src/test
git commit -F - <<'EOF'
Vault compositions: the Categories tree and the item table

Under the Enterprise layout the rail is a Categories tree with counts
under a header strip, and the list a Name/Username/Type/Modified table:
swatches in the Name cells, a header click sorts, the sort survives a
refresh, a search that matches nothing keeps the headers over "No
matching entries", and the footer counts what shows. The render matches
the mock's Categories and Items columns.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: The Details pane: the heading, the Fields group and Properties

Rulings 6 and 13: under a "Details" header strip the selected entry shows its swatch and name, its kind and method, a
"Fields" group of label and value rows (stored secrets through DETAIL `Secret`s, whose Show and Copy are Task 2's), the
untitled Properties "Modified" and "Used by" (10a's label-colon lines), and Edit… and Delete…; without a selection, one
line asks for one.

**Files:**
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/ui/ManagerPanel.java` (Task 4's `classicEntries()` and new
  methods)
- Modify: `plugins/vault/src/test/java/dev/jasper/vault/ui/ClassicManagerTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicVaultRenderTest.java`

**Interfaces:**
- Consumes: Task 2's classic reveal; Task 4's `username`, `modified`, `header`; 10a Task 3's untitled classic
  Properties; 3b's `Nodes.Properties` and `PropertyRow`.
- Produces: the classic detail ids `detail` (the panel), `detail.header`, `detail.none`, `detail.heading`, `detail.icon`,
  `detail.name`, `detail.type`, `detail.fields.section`, `detail.fields`, `detail.<field>.row`, `detail.username`,
  `detail.username.copy`, the `detail.secret.<id>` Secret, `detail.properties`, `detail.actions` with `EDIT`, `DELETE` and
  (for an SSH key) `COPY_PUBLIC`; private `classicDetail()`, `usedBy(UUID)`.

- [ ] **Step 1: Write the failing tests**

Add to `ClassicManagerTest` the import `dev.jasper.vault.model.Grant` and:

```java
    @Test void theDetailsPaneFollowsTheSelection() throws Exception {
        edt(() -> {
            var panel = panel(); var view = panel.view();
            panel.refresh(rows(), List.of(new Grant("dev.jasper.remote", amp)), STATUS, true);
            assertThat(node(view, "detail.header", Nodes.Header.class).title()).isEqualTo("Details");
            assertThat(node(view, "detail.none", Nodes.Text.class).text()).isEqualTo("Select an item to view its details.");
            emit(view, new UiEvent.Selected(ManagerPanel.ROWS, Set.of(amp.toString())));
            assertThat(find(view, "detail.none")).isEmpty();
            assertThat(node(view, "detail.icon", Nodes.Icon.class).size()).isEqualTo(Nodes.IconSize.SWATCH);
            assertThat(node(view, "detail.name", Nodes.Text.class).text()).isEqualTo("amp");
            assertThat(node(view, "detail.type", Nodes.Text.class).text()).isEqualTo("Login · Password");
            assertThat(node(view, "detail.fields.section", Nodes.Section.class).title()).isEqualTo("Fields");
            assertThat(node(view, "detail.username.row", Nodes.FormRow.class).label()).isEqualTo("Username");
            assertThat(node(view, "detail.username", Nodes.Text.class).role()).isEqualTo(Nodes.TextRole.MONO);
            assertThat(button(view, "detail.username.copy").label()).isEqualTo("Copy");
            assertThat(button(view, "detail.username.copy").action()).isEqualTo("detail.username.copy");
            String first = "detail.secret." + amp;
            assertThat(node(view, first, Nodes.Secret.class).kind()).isEqualTo(Nodes.SecretKind.DETAIL);
            assertThat(node(view, first, Nodes.Secret.class).copyAction()).isEqualTo("detail.password.copy");
            assertThat(secret(view, first)).isEqualTo("synthetic-amp");
            var properties = node(view, "detail.properties", Nodes.Properties.class);
            assertThat(properties.title()).as("untitled: label-colon lines (10a Rulings 4)").isEmpty();
            assertThat(properties.rows()).extracting(row -> row.label() + "=" + row.value()).containsExactly("Modified=" + date(MODIFIED), "Used by=Remote");
            assertThat(button(view, ManagerPanel.EDIT).label()).isEqualTo("Edit…");
            assertThat(button(view, ManagerPanel.DELETE).label()).isEqualTo("Delete…");
            assertThat(view.spec().toString()).as("no secret in the descriptor").doesNotContain("synthetic-amp");
            emit(view, new UiEvent.Selected(ManagerPanel.ROWS, Set.of(proxmox.toString())));
            assertThat(find(view, first)).as("the first entry's value is gone with its control").isEmpty();
            assertThat(secret(view, "detail.secret." + proxmox)).as("the selected entry's own value").isEqualTo("synthetic-Proxmox");
            assertThat(node(view, "detail.properties", Nodes.Properties.class).rows().get(1).value()).isEqualTo("Not used");
            press(view, ManagerPanel.DELETE);
            assertThat(asked).containsExactly("delete " + proxmox);
            panel.close();
        });
    }

    @Test void anSshKeysDetailsListItsFilesAndCopyThePublicKey() throws Exception {
        edt(() -> {
            var panel = panel(); var view = panel.view();
            UUID laptop = UUID.randomUUID();
            entries.put(laptop, new dev.jasper.vault.model.SshKey(laptop, "laptop", "ssh-ed25519", "SHA256:fixture", "",
                directory.resolve("laptop"), directory.resolve("laptop.pub"), MODIFIED));
            panel.refresh(List.of(new VaultManager.Row(laptop, "laptop", "SHA256:fixture", VaultManager.Type.SSH_KEY)), List.of(), STATUS, true);
            emit(view, new UiEvent.Selected(ManagerPanel.ROWS, Set.of(laptop.toString())));
            assertThat(node(view, "detail.type", Nodes.Text.class).text()).isEqualTo("SSH Key · ssh-ed25519");
            assertThat(node(view, "detail.private.row", Nodes.FormRow.class).label()).isEqualTo("Private key");
            assertThat(node(view, "detail.public.row", Nodes.FormRow.class).label()).isEqualTo("Public key");
            assertThat(node(view, "detail.fingerprint", Nodes.Text.class).text()).isEqualTo("SHA256:fixture");
            press(view, ManagerPanel.COPY_PUBLIC);
            assertThat(button(view, ManagerPanel.COPY_PUBLIC).label()).isEqualTo("Copy Public Key");
            assertThat(asked).containsExactly("copy-public " + laptop);
            panel.close();
        });
    }
```

Add to `ClassicVaultRenderTest` the imports `java.util.List` and `dev.jasper.app.testsupport.MockCrops` and:

```java
    static final String DETAILS = "0+24 #E1E3E6, 24+1 #C4C7CC, 25+77 #ECEDEF, 102+1 #D3D6DA, 103+26 #FFFFFF, 129+1 #E6E8EB, 130+26 #FFFFFF, "
        + "156+1 #D3D6DA, 157+8 #ECEDEF, 165+1 #C4C7CC, 166+61 #ECEDEF, 227+1 #B4B8BF, 228+22 #FEFEFE~#EEF0F2, 250+1 #B4B8BF, 251+248 #ECEDEF, "
        + "499+1 #C4C7CC";
    static final String DETAILS_NONE = "0+24 #E1E3E6, 24+1 #C4C7CC, 25+474 #ECEDEF, 499+1 #C4C7CC";

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theManagerMatchesItsMocksWithAndWithoutASelection() throws Exception {
        ClassicCompositionRenderTest.under(false, () -> {
            try (var vault = vault(root.resolve("details"))) {
                var ids = vault.seedLogins(LOGINS);
                var manager = vault.surface("Credential Vault");
                var none = render(manager, NO_SELECTION.logicalWidth(), NO_SELECTION.logicalHeight());
                MockMatch.save("light/vault-manager", NO_SELECTION.read(), none);
                bands("the Details pane without a selection", none, 1600, 70, 1070, DETAILS_NONE);
                MockCrops.fontsFloor("light/vault-manager edge agreement", MockMatch.agreement(NO_SELECTION.read(), none), 0.55);
                vault.dispatchToManager(new UiEvent.Selected("rows", Set.of(ids.getFirst().toString())));
                var selected = render(manager, SELECTED.logicalWidth(), SELECTED.logicalHeight());
                MockMatch.save("light/vault-manager-selected", SELECTED.read(), selected);
                bands("the Details pane of amp", selected, 1086, 70, 1070, DETAILS);
                bands("the Categories column", selected, 6, 0, 1070, CATEGORIES);
                bands("the Items column", selected, 854, 70, 1070, ITEMS);
                bands("the toolbar row", selected, 1600, 0, 70, TOOLBAR);
                bands("the status row and Done", selected, 1600, 1068, 1142, STATUS);
                for (String colour : List.of("#2F65CA", "#E1E3E6", "#F0F1F3", "#F7F8FA", "#E6E7EA", "#C4C7CC"))
                    assertThat(MockMatch.present(selected, Color.decode(colour))).as("shows " + colour).isTrue();
                MockCrops.fontsFloor("light/vault-manager-selected edge agreement", MockMatch.agreement(SELECTED.read(), selected), 0.55);
                assertThat(vault.problems).isEmpty();
            }
        });
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-vault:test --tests 'dev.jasper.vault.ui.ClassicManagerTest' --continue`
Expected: FAIL: `theDetailsPaneFollowsTheSelection` ("expected: Header visible", "detail.none" not found) and
`anSshKeysDetailsListItsFilesAndCopyThePublicKey` (no `detail.private.row`).

- [ ] **Step 3: Compose the Details pane**

```bash
python3 -I - <<'PY'
"""Plan 10b Task 5 step 3: the classic Details pane."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
U = "plugins/vault/src/main/java/dev/jasper/vault/ui/ManagerPanel.java"
edit(U, [
    ('''        return new Nodes.Split("columns", ControlState.READY, Nodes.SplitKind.THREE_COLUMN, List.of(rail, browser, detail()));
    }

    /** The item table:''', '''        return new Nodes.Split("columns", ControlState.READY, Nodes.SplitKind.THREE_COLUMN, List.of(rail, browser, classicDetail()));
    }

    /**
     * The classic Details pane (spec 14.8): the swatch and name, the kind and method, the Fields group, the untitled
     * Properties and the entry's buttons; one line without a selection. Stored secrets stay in DETAIL Secret nodes.
     */
    private Nodes.Node classicDetail() {
        var row = visible.stream().filter(item -> item.id().equals(selection)).findFirst().orElse(null);
        var content = new ArrayList<Nodes.Node>();
        if (row == null) content.add(text("detail.none", "Select an item to view its details.", Nodes.TextRole.DESCRIPTION));
        else entry.apply(row.id()).ifPresent(value -> {
            content.add(new Nodes.Stack("detail.heading", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
                new Nodes.Icon("detail.icon", ControlState.READY, icon(row.type()), hue(row.type()), Nodes.IconSize.SWATCH, row.type().toString()),
                text("detail.name", row.name(), Nodes.TextRole.DETAIL_TITLE))));
            content.add(text("detail.type", row.type() + method(value), Nodes.TextRole.DESCRIPTION));
            var fields = new ArrayList<Nodes.Node>();
            switch (value) {
                case Account account -> {
                    fields.add(field("username", "Username", new Nodes.Stack("detail.username.value", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
                        text("detail.username", account.username(), Nodes.TextRole.MONO),
                        new Nodes.Button("detail.username.copy", ControlState.READY, "Copy", "", Nodes.ButtonKind.SECONDARY, "detail.username.copy", "Copy username")))));
                    if (password(account.auth()) != null) fields.add(field("password", "Password",
                        new Nodes.Secret(secretId(), ControlState.READY, "", "Password", "", Nodes.SecretKind.DETAIL, "detail.password.copy")));
                    if (account.auth() instanceof Auth.Key key) fields.add(fieldText("private", "Private key", key.keyPath().toString()));
                    if (account.auth() instanceof Auth.KeyAndPassword key) fields.add(fieldText("private", "Private key", key.keyPath().toString()));
                }
                case SshKey key -> {
                    fields.add(fieldText("algorithm", "Algorithm", key.algorithm())); fields.add(fieldText("fingerprint", "Fingerprint", key.fingerprint()));
                    fields.add(fieldText("private", "Private key", key.privatePath().toString())); fields.add(fieldText("public", "Public key", key.publicPath().toString()));
                    if (!key.comment().isBlank()) fields.add(fieldText("comment", "Comment", key.comment()));
                }
                case ManagedSshKey key -> {
                    fields.add(fieldText("storage", "Storage", "Stored in Vault")); fields.add(fieldText("fingerprint", "Fingerprint", key.fingerprint()));
                }
                case Note note -> fields.add(field("note", "Note",
                    new Nodes.Secret(secretId(), ControlState.READY, "", "Secure note", "", Nodes.SecretKind.DETAIL, "detail.note.copy")));
                default -> { }
            }
            content.add(new Nodes.Section("detail.fields.section", ControlState.READY, "Fields",
                new Nodes.Card("detail.fields", ControlState.READY, Nodes.CardKind.FORM, fields)));
            content.add(new Nodes.Properties("detail.properties", ControlState.READY, "", List.of(
                new Nodes.PropertyRow("Modified", modified(row).map(ManagerPanel::date).orElse(""), false),
                new Nodes.PropertyRow("Used by", usedBy(row.id()), false))));
            var actions = new ArrayList<Nodes.Node>(List.of(
                new Nodes.Button(EDIT, ControlState.READY, "Edit…", "", Nodes.ButtonKind.SECONDARY, EDIT, "Edit entry"),
                new Nodes.Button(DELETE, ControlState.READY, "Delete…", "", Nodes.ButtonKind.SECONDARY, DELETE, "Delete entry")));
            if (row.type() == VaultManager.Type.SSH_KEY) actions.add(new Nodes.Button(COPY_PUBLIC, ControlState.READY, "Copy Public Key", "",
                Nodes.ButtonKind.SECONDARY, COPY_PUBLIC, "Copy public key"));
            content.add(new Nodes.Stack("detail.actions", ControlState.READY, Nodes.LayoutRole.INLINE, actions));
        });
        return new Nodes.Panel("detail", ControlState.READY, header("detail.header", "Details"), Optional.empty(),
            new Nodes.Stack("detail.content", ControlState.READY, Nodes.LayoutRole.DETAIL, content), Optional.empty());
    }

    /** " · Password", " · Private key", " · ssh-ed25519": what the kind's heading line adds. */
    private static String method(Object value) {
        return switch (value) {
            case Account account -> " · " + (account.auth() instanceof Auth.Password ? "Password" : account.auth() instanceof Auth.Key ? "Private key" : "Private key and password");
            case SshKey key -> " · " + key.algorithm();
            case ManagedSshKey _ -> " · Stored in Vault";
            default -> "";
        };
    }

    private static Nodes.Node field(String id, String label, Nodes.Node control) {
        return new Nodes.FormRow("detail." + id + ".row", ControlState.READY, label, "", control, Optional.empty(), false);
    }

    /** A Fields row whose value is data text. */
    private static Nodes.Node fieldText(String id, String label, String data) { return field(id, label, text("detail." + id, data, Nodes.TextRole.MONO)); }

    /** The plugins granted this entry, by their id's last segment capitalised ("Remote"); "Not used" without one (Rulings 6). */
    private String usedBy(UUID id) {
        var users = grants.stream().filter(grant -> grant.credentialId().equals(id)).map(grant -> {
            String plugin = grant.pluginId().substring(grant.pluginId().lastIndexOf('.') + 1);
            return plugin.isEmpty() ? grant.pluginId() : plugin.substring(0, 1).toUpperCase(Locale.ROOT) + plugin.substring(1);
        }).distinct().toList();
        return users.isEmpty() ? "Not used" : String.join(", ", users);
    }

    /** The item table:''', 1)])
PY
```

`secretId()` is `"detail.secret." + selection` (`ManagerPanel.java:185`), so another entry is another Secret control and
starts masked (Review Focus 4); `render()` writes the selected value as before.

- [ ] **Step 4: Run the tests and compare with the mocks**

Run: `./gradlew :jasper-plugin-vault:test --tests 'dev.jasper.vault.ui.*' :jasper-app:test --tests 'dev.jasper.app.plugins.ClassicVaultRenderTest' --continue`
Expected: PASS: `ClassicManagerTest` (7), `ClassicVaultRenderTest` (3).

The Details band is the most composed: read a mismatch from the top. A heading block (`25+77`) of another height is the
DETAIL stack's gaps (`DETAIL_HEADING_GAP`, `DETAIL_VALUE_GAP` in the classic catalog); a Fields box other than `102+1,
103+26, 129+1, 130+26, 156+1` is 4b's classic group (the group's title breaks its top rule) and the FORM card's rows (the
mock's rows are 26; the Show and Copy buttons are 24, Rulings 13); the Properties lines are 10a's (rows 18 apart); the
`228+22` gradient is the classic secondary button (Edit…, 4b). A Details column on `#F7F7F8` instead of `#ECEDEF` is
`Panels.Surface`'s `surface.panel` (Rulings 13, inside the band tolerance). Fix the composition or a classic value, never
the expected bands, iterate until the test passes, and record each residual with its measured bands and the edge
agreement of both renders in the banner.

- [ ] **Step 5: Commit**

```bash
git add plugins/vault/src jasper-app/src/test
git commit -F - <<'EOF'
Vault compositions: the Details pane

Under the Enterprise layout the Details pane shows the entry's swatch,
name and method, a Fields group whose stored secrets keep to DETAIL
Secret nodes (the classic reveal draws Show and Copy), the untitled
Properties Modified and Used by, and Edit... and Delete...; without a
selection one line asks for one. Both manager mocks match their renders.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: The Add Login, Add SSH Key and Add Secure Note sheets

Rulings 5, 7 and 10: under CLASSIC every editor sheet carries its kind's swatch (`DialogSpec.swatch`), drops its PROMPT
hero (plan 6's header strip shows the swatch and title), titles its untitled group ("Login", "SSH Key", "Secure Note"),
labels the Authentication choice "Method" in the label column and reads "Private key" and "Public key". The rows
composition passes `Hue.NONE` and builds today's forms.

**Files:**
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/ui/EditorForm.java:56,205-231`,
  `plugins/vault/src/main/java/dev/jasper/vault/ui/EntryEditor.java:46,54,83,104`,
  `plugins/vault/src/main/java/dev/jasper/vault/ui/EntryPresenter.java:9-24,33-34`,
  `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultManagerWindow.java:50,104-135`
- Create: `plugins/vault/src/test/java/dev/jasper/vault/ui/ClassicEditorsTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicVaultRenderTest.java`, `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Task 2's classic reveal (the discreet password's "Show"); Task 3's `VaultManagerWindow` layout and
  `dispatchToManager`; 3d's `DialogSpec(String, WindowOwner, boolean, Presentation, Nodes.Hue)` and
  `FakePluginHost.dialogSwatch(String, String)`; 10a Task 4's `ClassicCompositionRenderTest.check`.
- Produces: `final void EditorForm.layout(Layout)`, `protected final void classicGroup(String)`, `protected final void
  classicLabel(String, String)`, `final Nodes.Hue swatch()`; `EntryPresenter(PluginContext, Layout)` and
  `show(WindowOwner, String, Nodes.Hue, Function, Runnable)`; `VaultManagerWindow.dialog(String, Nodes.Hue, Function)`.

- [ ] **Step 1: Write the failing tests**

Create `plugins/vault/src/test/java/dev/jasper/vault/ui/ClassicEditorsTest.java`:

```java
package dev.jasper.vault.ui;

import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.vault.service.VaultService;
import dev.jasper.vault.workstation.Layout;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Plan 10b Rulings 5, 7 and 10: the classic editor sheets of the 07 Credential Vault mocks. */
class ClassicEditorsTest {
    @TempDir Path directory;
    static final PluginInfo INFO = new PluginInfo("dev.jasper.vault", "Vault", "0.1.0", Set.of());

    static List<String> groups(Nodes.Form form) {
        return form.sections().stream().filter(Nodes.Section.class::isInstance).map(n -> ((Nodes.Section) n).title()).toList();
    }

    @Test void addLoginTakesTheMocksGroupsLabelsAndSwatch() throws Exception {
        edt(() -> {
            var form = EntryEditor.login(new FakeKit("dev.jasper.vault"), null, account -> CompletableFuture.completedFuture(null), () -> { });
            form.layout(Layout.CLASSIC);
            var view = form.view();
            var root = (Nodes.Form) view.spec().root();
            assertThat(root.hero()).as("the header strip titles the sheet").isEmpty();
            assertThat(groups(root)).containsExactly("Login", "Authentication");
            assertThat(node(view, "row." + EntryEditor.NAME, Nodes.FormRow.class).label()).isEqualTo("Name");
            assertThat(node(view, "row." + EntryEditor.AUTH, Nodes.FormRow.class).label()).isEqualTo("Method");
            assertThat(node(view, EntryEditor.AUTH, Nodes.Choice.class).kind()).isEqualTo(Nodes.ChoiceKind.EXCLUSIVE);
            assertThat(node(view, EntryEditor.AUTH, Nodes.Choice.class).options()).extracting(o -> o.label()).containsExactly("Password", "Private Key");
            assertThat(node(view, "row." + EntryEditor.PRIVATE_PATH, Nodes.FormRow.class).label()).isEqualTo("Private key");
            assertThat(node(view, EntryEditor.PASSWORD, Nodes.Secret.class).kind()).as("the classic reveal shows its Show").isEqualTo(Nodes.SecretKind.DISCREET_INPUT);
            assertThat(form.swatch()).isEqualTo(Nodes.Hue.BLUE);
            form.close();
        });
    }

    @Test void addSshKeyAndAddSecureNoteTakeTheirGroups() throws Exception {
        edt(() -> {
            var kit = new FakeKit("dev.jasper.vault");
            var key = EntryEditor.key(kit, null, value -> CompletableFuture.completedFuture(null), () -> { });
            key.layout(Layout.CLASSIC);
            assertThat(groups((Nodes.Form) key.view().spec().root())).containsExactly("SSH Key");
            assertThat(node(key.view(), "row." + EntryEditor.PRIVATE_PATH, Nodes.FormRow.class).label()).isEqualTo("Private key");
            assertThat(node(key.view(), "row." + EntryEditor.PUBLIC_PATH, Nodes.FormRow.class).label()).isEqualTo("Public key");
            assertThat(key.swatch()).isEqualTo(Nodes.Hue.GREEN);
            key.close();
            var note = EntryEditor.note(kit, null, value -> CompletableFuture.completedFuture(null), () -> { });
            note.layout(Layout.CLASSIC);
            assertThat(groups((Nodes.Form) note.view().spec().root())).containsExactly("Secure Note");
            assertThat(note.swatch()).isEqualTo(Nodes.Hue.AMBER);
            note.close();
        });
    }

    @Test void theRowsLayoutKeepsTodaysForms() throws Exception {
        edt(() -> {
            var form = EntryEditor.login(new FakeKit("dev.jasper.vault"), null, account -> CompletableFuture.completedFuture(null), () -> { });
            var view = form.view();
            var root = (Nodes.Form) view.spec().root();
            assertThat(root.hero()).isPresent();
            assertThat(groups(root)).containsExactly("Authentication");
            assertThat(find(view, "row." + EntryEditor.AUTH)).as("the choice stays unlabelled").isEmpty();
            assertThat(node(view, "row." + EntryEditor.PRIVATE_PATH, Nodes.FormRow.class).label()).isEqualTo("Private Key");
            form.close();
        });
    }

    @Test void theSheetsCarryTheirSwatchUnderClassicOnly() throws Exception {
        assertThat(swatchOfAddLogin(Layout.CLASSIC)).hasValue(Nodes.Hue.BLUE);
        assertThat(swatchOfAddLogin(Layout.ROWS)).hasValue(Nodes.Hue.NONE);
    }

    Optional<Nodes.Hue> swatchOfAddLogin(Layout layout) throws Exception {
        var swatch = new AtomicReference<Optional<Nodes.Hue>>();
        edt(() -> {
            try (var f = new VaultUiFixture(directory.resolve(layout.name())); var host = new FakePluginHost()) {
                PluginContext context = host.start(INFO, Set.of(), Set.of(), c -> { });
                host.addTerminalWindow(); WindowHandle owner = context.terminals().windows().getFirst();
                var service = new VaultService(f.lock, () -> Optional.of(owner), prompt -> { }, p -> { }, p -> { },
                    new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerWindow(context, f.lock, f.manager, service,
                    () -> new VaultManagerWindow.Status("test", "15 min", "/synthetic/vault.jv"), () -> { }, layout);
                window.show(owner, Optional.empty());
                window.add(VaultManager.Type.LOGIN);
                swatch.set(host.dialogSwatch("dev.jasper.vault", "Add Login"));
                window.close();
            }
        });
        return swatch.get();
    }
}
```

If `VaultUiFixture` needs an existing directory, create it first (`java.nio.file.Files.createDirectories`) inside the
helper.

Add to `ClassicVaultRenderTest` the static import `dev.jasper.app.plugins.AppContractTest.onEdt` and:

```java
    static final MockMatch.Crop ADD_LOGIN = new MockMatch.Crop(V + "4 - Add Login.png", 382, 112, 880, 494),
        ADD_SSH_KEY = new MockMatch.Crop(V + "5 - Add SSH Key.png", 382, 112, 880, 430);

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theAddLoginAndAddSshKeySheetsMatchTheirMocks() throws Exception {
        ClassicCompositionRenderTest.under(false, () -> {
            try (var vault = vault(root.resolve("sheets"))) {
                vault.dispatchToManager(new UiEvent.Action("add.LOGIN"));
                var login = vault.awaitSurface("Add Login");
                var loginRender = render(login, ADD_LOGIN.logicalWidth(), ADD_LOGIN.logicalHeight());
                ClassicCompositionRenderTest.check("light/vault-add-login", ADD_LOGIN, loginRender,
                    18, "0+206 #ECEDEF, 206+1 #C4C7CC, 207+40 #E6E7EA", 1, 0.55, "#C4C7CC", "#E6E7EA", "#FFFFFF");
                bands("the Add Login groups", loginRender, 34, 0, ADD_LOGIN.height(), "0+16 #ECEDEF, 16+1 #C4C7CC, 17+80 #ECEDEF, "
                    + "97+1 #C4C7CC, 98+18 #ECEDEF, 116+1 #C4C7CC, 117+76 #ECEDEF, 193+1 #C4C7CC, 194+12 #ECEDEF, 206+1 #C4C7CC, 207+40 #E6E7EA");
                onEdt(login::requestClose);
                vault.dispatchToManager(new UiEvent.Action("add.SSH_KEY"));
                var key = vault.awaitSurface("Add SSH Key");
                var keyRender = render(key, ADD_SSH_KEY.logicalWidth(), ADD_SSH_KEY.logicalHeight());
                ClassicCompositionRenderTest.check("light/vault-add-ssh-key", ADD_SSH_KEY, keyRender,
                    18, "0+174 #ECEDEF, 174+1 #C4C7CC, 175+40 #E6E7EA", 1, 0.55, "#C4C7CC", "#E6E7EA", "#FFFFFF");
                bands("the Add SSH Key group", keyRender, 34, 0, ADD_SSH_KEY.height(),
                    "0+16 #ECEDEF, 16+1 #C4C7CC, 17+144 #ECEDEF, 161+1 #C4C7CC, 162+12 #ECEDEF, 174+1 #C4C7CC, 175+40 #E6E7EA");
                onEdt(key::requestClose);
                assertThat(vault.problems).isEmpty();
            }
        });
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-vault:compileTestJava --continue`
Expected: FAIL to compile: "cannot find symbol … layout(Layout)" and "… swatch()".

- [ ] **Step 3: The classic forms and the swatches**

```bash
python3 -I - <<'PY'
"""Plan 10b Task 6 step 3: the classic editor forms, their swatches and labels."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
U = "plugins/vault/src/main/java/dev/jasper/vault/ui/"
edit(U + "EditorForm.java", [
    ('''import dev.jasper.sdk.ui.ViewSpec;\n''', '''import dev.jasper.sdk.ui.ViewSpec;\nimport dev.jasper.vault.workstation.Layout;\n''', 1),
    ('''    protected final void discreetInputs() { discreet=true; }\n''', '''    protected final void discreetInputs() { discreet=true; }
    private Layout layout = Layout.ROWS;
    private String classicTitle = "";
    private final Map<String, String> classicLabels = new HashMap<>();
    /** The composition this form takes (plan 10b); its presenter sets it before the form's view is made. */
    final void layout(Layout value) { layout = Objects.requireNonNull(value); render(); }
    /** Under CLASSIC, the title of the form's untitled group (plan 10b Rulings 10). */
    protected final void classicGroup(String title) { classicTitle = Objects.requireNonNull(title); }
    /** Under CLASSIC, row {@code id}'s label in the label column (plan 10b Rulings 7 and 10). */
    protected final void classicLabel(String id, String label) { classicLabels.put(id, label); }
    /** The entry's hue for its sheet's swatch; NONE without an identity. */
    final Nodes.Hue swatch() { return icon.isEmpty() ? Nodes.Hue.NONE : hue; }
    private String label(Row row) { return layout == Layout.CLASSIC ? classicLabels.getOrDefault(row.id(), row.label()) : row.label(); }\n''', 1),
    ('''            case ChoiceRow choice when choice.kind() == Nodes.ChoiceKind.EXCLUSIVE -> control(row);''',
     '''            // The classic label column holds the choice's label too (plan 10b Rulings 10).
            case ChoiceRow choice when choice.kind() == Nodes.ChoiceKind.EXCLUSIVE -> layout == Layout.CLASSIC
                ? new Nodes.FormRow("row." + row.id(), state(row.id()), label(row), "", control(row), Optional.empty(), false) : control(row);''', 1),
    ('''            default -> new Nodes.FormRow("row." + row.id(), state(row.id()), row.label(), "", control(row), Optional.empty(), false);''',
     '''            default -> new Nodes.FormRow("row." + row.id(), state(row.id()), label(row), "", control(row), Optional.empty(), false);''', 1),
    ('''        if (groups.isEmpty()) sections.add(new Nodes.Card("fields", ControlState.READY, Nodes.CardKind.FORM, rows.stream().map(this::section).toList()));
        else for (Group group : groups) {
            Nodes.Node card = new Nodes.Card(group.id(), ControlState.READY, Nodes.CardKind.FORM,
                group.rows().stream().map(id -> rows.stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow()).map(this::section).toList());
            sections.add(group.title().isEmpty() ? card : new Nodes.Section(group.id() + ".section", ControlState.READY, group.title(), card));
        }''', '''        boolean classic = layout == Layout.CLASSIC;
        if (groups.isEmpty()) {
            Nodes.Node card = new Nodes.Card("fields", ControlState.READY, Nodes.CardKind.FORM, rows.stream().map(this::section).toList());
            sections.add(classic && !classicTitle.isEmpty() ? new Nodes.Section("fields.section", ControlState.READY, classicTitle, card) : card);
        } else for (Group group : groups) {
            Nodes.Node card = new Nodes.Card(group.id(), ControlState.READY, Nodes.CardKind.FORM,
                group.rows().stream().map(id -> rows.stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow()).map(this::section).toList());
            String heading = classic && group.title().isEmpty() ? classicTitle : group.title();
            sections.add(heading.isEmpty() ? card : new Nodes.Section(group.id() + ".section", ControlState.READY, heading, card));
        }''', 1),
    ('''        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.SHEET, title, icon.isEmpty() ? Optional.empty() : Optional.of(''',
     '''        // The classic dialog's header strip shows the swatch and title (spec 10.8), so the form has no hero.
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.SHEET, title, icon.isEmpty() || classic ? Optional.empty() : Optional.of(''', 1)])
edit(U + "EntryEditor.java", [
    ('''        form.group("authentication", "Authentication", AUTH, PRIVATE_PATH, PASSPHRASE, ALSO_PASSWORD, PASSWORD);\n''',
     '''        form.group("authentication", "Authentication", AUTH, PRIVATE_PATH, PASSPHRASE, ALSO_PASSWORD, PASSWORD);
        form.classicGroup("Login"); form.classicLabel(AUTH, "Method"); form.classicLabel(PRIVATE_PATH, "Private key");\n''', 1),
    ('''        form.identity("note", Nodes.Hue.AMBER);\n''', '''        form.identity("note", Nodes.Hue.AMBER);\n        form.classicGroup("Secure Note");\n''', 1),
    ('''        form.hint(COMMENT, "Optional");\n''', '''        form.hint(COMMENT, "Optional");
        form.classicGroup("SSH Key"); form.classicLabel(PRIVATE_PATH, "Private key"); form.classicLabel(PUBLIC_PATH, "Public key");\n''', 1)])
edit(U + "EntryPresenter.java", [
    ('''import dev.jasper.sdk.ui.*;\n''', '''import dev.jasper.sdk.ui.*;\nimport dev.jasper.vault.workstation.Layout;\n''', 1),
    ('''    EntryPresenter(PluginContext context){this.context=context;}
    void show(WindowOwner owner,String title,Function<Runnable,? extends EditorForm> factory,Runnable finished){
        var dialog=context.windows().dialog(new DialogSpec(title,owner,true,DialogSpec.Presentation.ATTACHED_SHEET));''',
     '''    private final Layout layout;
    EntryPresenter(PluginContext context){this(context,Layout.ROWS);}
    /** Editors in {@code layout}'s composition: under CLASSIC each sheet takes its form's layout and its kind's swatch (plan 10b). */
    EntryPresenter(PluginContext context,Layout layout){this.context=context;this.layout=layout;}
    void show(WindowOwner owner,String title,Function<Runnable,? extends EditorForm> factory,Runnable finished){show(owner,title,Nodes.Hue.NONE,factory,finished);}
    void show(WindowOwner owner,String title,Nodes.Hue hue,Function<Runnable,? extends EditorForm> factory,Runnable finished){
        var dialog=context.windows().dialog(new DialogSpec(title,owner,true,DialogSpec.Presentation.ATTACHED_SHEET,layout==Layout.CLASSIC?hue:Nodes.Hue.NONE));''', 1),
    ('''        try{form.setTitle(title);form.setFileChooser(dialog::chooseFile);dialog.setContent(form.view());}''',
     '''        try{form.layout(layout);form.setTitle(title);form.setFileChooser(dialog::chooseFile);dialog.setContent(form.view());}''', 1),
    ('''            case "create.login"->show(owner,"Add Login",close->''', '''            case "create.login"->show(owner,"Add Login",Nodes.Hue.BLUE,close->''', 1),
    ('''            case "create.key"->show(owner,"Add SSH Key",close->''', '''            case "create.key"->show(owner,"Add SSH Key",Nodes.Hue.GREEN,close->''', 1)])
edit(U + "VaultManagerWindow.java", [
    ('''import dev.jasper.sdk.ui.DialogSpec;\n''', '''import dev.jasper.sdk.ui.DialogSpec;\nimport dev.jasper.sdk.ui.Nodes;\n''', 1),
    ('''this.editors=new EntryPresenter(context);''', '''this.editors=new EntryPresenter(context, layout);''', 1),
    ('''    public void dialog(String title, Function<Runnable, ? extends EditorForm> factory) {
        if (window == null || lock.state() != LockState.UNLOCKED) return;
        editors.show(window,title,factory,()->{});
    }''', '''    public void dialog(String title, Function<Runnable, ? extends EditorForm> factory) { dialog(title, Nodes.Hue.NONE, factory); }
    /** As {@link #dialog(String, Function)}, the sheet's swatch in {@code hue} under the classic layout (plan 10b Rulings 5). */
    public void dialog(String title, Nodes.Hue hue, Function<Runnable, ? extends EditorForm> factory) {
        if (window == null || lock.state() != LockState.UNLOCKED) return;
        editors.show(window,title,hue,factory,()->{});
    }''', 1),
    ('''            case LOGIN -> dialog("Add Login", close ->''', '''            case LOGIN -> dialog("Add Login", Nodes.Hue.BLUE, close ->''', 1),
    ('''            case SSH_KEY -> dialog("Add SSH Key", close ->''', '''            case SSH_KEY -> dialog("Add SSH Key", Nodes.Hue.GREEN, close ->''', 1),
    ('''            case NOTE -> dialog("Add Secure Note", close ->''', '''            case NOTE -> dialog("Add Secure Note", Nodes.Hue.AMBER, close ->''', 1),
    ('''                case Account a -> dialog("Edit Login", close ->''', '''                case Account a -> dialog("Edit Login", Nodes.Hue.BLUE, close ->''', 1),
    ('''                case dev.jasper.vault.model.ManagedSshKey k -> dialog("Edit stored SSH key", close -> {''',
     '''                case dev.jasper.vault.model.ManagedSshKey k -> dialog("Edit stored SSH key", Nodes.Hue.GREEN, close -> {''', 1),
    ('''                case SshKey k -> dialog("Edit SSH Key", close ->''', '''                case SshKey k -> dialog("Edit SSH Key", Nodes.Hue.GREEN, close ->''', 1),
    ('''                case Note n -> dialog("Edit Secure Note", close ->''', '''                case Note n -> dialog("Edit Secure Note", Nodes.Hue.AMBER, close ->''', 1)])
PY
```

Task 3's constructor edit wrote `this.layout = layout;` before `this.editors=new EntryPresenter(context, layout);`, so the
presenter sees the window's layout. `EditorForm.form()` declares `classic` before its first use in the hero (the edited
block above precedes the `return`). Under ROWS every form is today's: `label(row)` is `row.label()`, no group gains a
title, the hero stays, and the presenter passes `Hue.NONE`, the 4-argument `DialogSpec`'s value (3d).

- [ ] **Step 4: Run the tests and compare with the mocks**

Add the ledger row first (Execution notes R6):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv")
text = path.read_text(encoding="utf-8")
source = "plugins/vault/src/test/java/dev/jasper/vault/ui/ClassicEditorsTest.java"
if source in {line.split(",", 1)[0] for line in text.splitlines()}: raise SystemExit("already in the ledger")
path.write_text(text + f"{source},dev.jasper.vault,ClassicEditorsTest,nonvisual-helper,none,none,{source},Test/fixture ownership; New in Enterprise plan 10b Task 6 (spec 14.8)\n", encoding="utf-8")
PY
```

Run: `./gradlew :jasper-plugin-vault:test --tests 'dev.jasper.vault.ui.*' :jasper-app:test --tests 'dev.jasper.app.plugins.ClassicVaultRenderTest' --tests 'dev.jasper.app.plugins.VaultRenderEvidenceTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `ClassicEditorsTest` (4), `EntryEditorTest` unchanged ("Password", "Private Key"), `ClassicVaultRenderTest`
(4), the ledger's coverage.

A sheet band other than the mock's is the form's height: `206` (Add Login) is the two groups' rows (30 apart, the
classic `SHEET_SECTION_GAP` 12 between groups, `FORM_TOP` 10 since 10a D11); `174` (Add SSH Key) one group of four rows.
The group rules down x 34 (the boxes' left edge, clear of their titles) are 10a's hand-off, re-measured on 2026-10-17
(Execution notes R13): Add Login `0+16 #ECEDEF, 16+1 #C4C7CC, 17+80 #ECEDEF, 97+1 #C4C7CC, 98+18 #ECEDEF, 116+1 #C4C7CC,
117+76 #ECEDEF, 193+1 #C4C7CC, 194+12 #ECEDEF, 206+1 #C4C7CC, 207+40 #E6E7EA`, Add SSH Key `0+16 #ECEDEF, 16+1 #C4C7CC,
17+144 #ECEDEF, 161+1 #C4C7CC, 162+12 #ECEDEF, 174+1 #C4C7CC, 175+40 #E6E7EA`; the render's first rule above or below
16 is `FORM_TOP` and the gap between the boxes `SHEET_SECTION_GAP`, both shared with 10a's native dialogs (R13 says how
to resolve a disagreement). A footer other
than 40 on `#E6E7EA` under a `#C4C7CC` rule is plan 6b's `Forms` band (`dialogBand`, `DIALOG_FOOTER_VERTICAL` and
`FORM_SIDES`), which `render` turns on as `NativeShells.classicContent` does; a footer with no band means the flag did not reach
the view. If `render` finds the header strip inside the holder, it starts the crop below it (its `above`); if the footer
is drawn outside the holder, render the surface plan 6 names and record it in the banner. Fix the composition or a classic value, never the expected bands, and record the
measured edge agreement of both sheets.

- [ ] **Step 5: Commit**

```bash
git add plugins/vault/src jasper-app/src/test docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Vault compositions: the classic editor sheets

Under the Enterprise layout Add Login, Add SSH Key and Add Secure Note
carry their kind's swatch, drop the prompt hero for the dialog's header
strip, title their groups as the mocks do, label the authentication
choice "Method" and read "Private key" and "Public key". The rows layout
keeps today's forms and passes no swatch. Both sheets match their mocks.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 7: Unlock Vault, the classic PROMPT overlay

Rulings 11: under CLASSIC the standalone unlock form (the OVERLAY that opens while the manager is closed) puts the lock
tile beside the intro without a heading, the master password in the label column with the host's "Show password" under
it, and the auto-lock as the footer's footnote; with auto-lock off there is none. Readiness and submission are today's.

**Files:**
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/ui/PasswordPanel.java:36-58,152-176`,
  `plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java:294`
- Create: `plugins/vault/src/test/java/dev/jasper/vault/ui/ClassicUnlockTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicVaultRenderTest.java`, `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Task 2's prompt reveal ("Show password"); 3b's 11-argument `Nodes.Form` with `FormSubmission` and `footnote`;
  `VaultSettings.autoLock()`.
- Produces: `public static PasswordPanel PasswordPanel.unlock(UiKit, UnlockPrompt, Layout, String footnote)` (the
  2-argument form means ROWS and no footnote); the classic ids `form.hero`, `form.icon`, `intro`, `form.row`, `password`,
  `message`, `cancel`, `submit`.

- [ ] **Step 1: Write the failing tests**

Create `plugins/vault/src/test/java/dev/jasper/vault/ui/ClassicUnlockTest.java`:

```java
package dev.jasper.vault.ui;

import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.vault.service.UnlockCaptureSupport;
import dev.jasper.vault.workstation.Layout;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Plan 10b Rulings 11: the classic Unlock Vault of the 07 mocks. */
class ClassicUnlockTest {
    @TempDir Path directory;
    static final String FOOTNOTE = "Auto-locks after 15 min idle";

    @Test void theTileSitsBesideTheIntroAndThePasswordInTheLabelColumn() throws Exception {
        edt(() -> {
            try (var panel = PasswordPanel.unlock(new FakeKit("dev.jasper.vault"), UnlockCaptureSupport.prompt(directory), Layout.CLASSIC, FOOTNOTE)) {
                var view = panel.view();
                var root = (Nodes.Form) view.spec().root();
                assertThat(root.kind()).isEqualTo(Nodes.FormKind.PROMPT_COMPACT);
                var top = (Nodes.Stack) root.hero().orElseThrow();
                assertThat(top.role()).isEqualTo(Nodes.LayoutRole.INLINE);
                assertThat(top.children()).extracting(Nodes.Node::id).containsExactly("form.icon", "intro");
                assertThat(find(view, "form.heading")).as("the header strip titles the overlay").isEmpty();
                assertThat(node(view, "form.row", Nodes.FormRow.class).label()).isEqualTo("Master password");
                assertThat(node(view, "password", Nodes.Secret.class).kind()).isEqualTo(Nodes.SecretKind.INPUT);
                assertThat(node(view, "password", Nodes.Secret.class).submitAction()).isEqualTo("submit");
                assertThat(root.footnote()).isEqualTo(FOOTNOTE);
                assertThat(root.actions()).extracting(Nodes.Button::label).containsExactly("Cancel", "Unlock");
                assertThat(root.cancelAction()).isEqualTo("cancel");
            }
        });
    }

    @Test void theFootnoteFollowsTheAutoLockAndUnlockNeedsAPassword() throws Exception {
        edt(() -> {
            try (var off = PasswordPanel.unlock(new FakeKit("dev.jasper.vault"), UnlockCaptureSupport.prompt(directory), Layout.CLASSIC, "")) {
                var root = (Nodes.Form) off.view().spec().root();
                assertThat(root.footnote()).as("auto-lock off: no footnote").isEmpty();
                assertThat(root.preview()).isEmpty();
            }
            try (var panel = PasswordPanel.unlock(new FakeKit("dev.jasper.vault"), UnlockCaptureSupport.prompt(directory), Layout.CLASSIC, FOOTNOTE)) {
                var view = panel.view();
                assertThat(button(view, "submit").state().enabled()).as("an empty field").isFalse();
                type(view, "password", "synthetic-wrong");
                assertThat(button(view, "submit").state().enabled()).isTrue();
                press(view, "submit");
                assertThat(secret(view, "password")).as("a submit clears the field").isEmpty();
                assertThat(button(view, "submit").state().enabled()).as("after a wrong password").isFalse();
                assertThat(view.spec().toString()).doesNotContain("synthetic-wrong");
            }
        });
    }

    @Test void theRowsUnlockKeepsItsHeroAndHasNoFootnote() throws Exception {
        edt(() -> {
            try (var panel = PasswordPanel.unlock(new FakeKit("dev.jasper.vault"), UnlockCaptureSupport.prompt(directory))) {
                var root = (Nodes.Form) panel.view().spec().root();
                assertThat(((Nodes.Stack) root.hero().orElseThrow()).role()).isEqualTo(Nodes.LayoutRole.PROMPT_HEADER);
                assertThat(node(panel.view(), "form.heading", Nodes.Text.class).text()).isEqualTo("Unlock Vault");
                assertThat(root.footnote()).isEmpty();
            }
        });
    }
}
```

Add to `ClassicVaultRenderTest`:

```java
    static final MockMatch.Crop UNLOCK = new MockMatch.Crop(V + "1 - Unlock Vault (over Workstation).png", 1106, 838, 800, 310);

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theUnlockOverlayMatchesItsMock() throws Exception {
        ClassicCompositionRenderTest.under(false, () -> {
            try (var vault = vault(root.resolve("unlock"))) {
                vault.invoke("dev.jasper.vault.lock");
                var manager = vault.surface("Credential Vault");
                onEdt(manager::requestClose);
                vault.invoke("dev.jasper.vault.open");
                var unlock = vault.awaitSurface("Unlock Vault");
                ClassicCompositionRenderTest.check("light/vault-unlock", UNLOCK, render(unlock, UNLOCK.logicalWidth(), UNLOCK.logicalHeight()),
                    14, "0+114 #ECEDEF, 114+1 #C4C7CC, 115+40 #E6E7EA", 1, 0.55, "#2F65CA", "#E6E7EA", "#FFFFFF");
                onEdt(unlock::requestClose);
                assertThat(vault.problems).isEmpty();
            }
        });
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-vault:test --tests 'dev.jasper.vault.ui.ClassicUnlockTest'`
Expected: FAIL to compile: "no suitable method found for unlock(FakeKit,UnlockPrompt,Layout,String)".

- [ ] **Step 3: The classic unlock form**

```bash
python3 -I - <<'PY'
"""Plan 10b Task 7 step 3: the classic Unlock Vault."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
U = "plugins/vault/src/main/java/dev/jasper/vault/ui/PasswordPanel.java"
edit(U, [
    ('''import dev.jasper.vault.service.UnlockPrompt;\n''', '''import dev.jasper.vault.service.UnlockPrompt;\nimport dev.jasper.vault.workstation.Layout;\n''', 1),
    ('''    private Host host;\n''', '''    private Host host;
    /** The standalone unlock form's composition and its footer line (plan 10b Rulings 11). */
    private Layout layout = Layout.ROWS;
    private String footnote = "";\n''', 1),
    ('''    /** The unlock form rendered inside {@code host} under {@code prefix}. */''',
     '''    /**
     * The unlock form in its own view in {@code layout}'s composition (plan 10b Rulings 11); under CLASSIC
     * {@code footnote} is the footer's one line ("Auto-locks after 15 min idle"), blank for none.
     */
    public static PasswordPanel unlock(UiKit kit, UnlockPrompt prompt, Layout layout, String footnote) {
        var panel = unlockPanel("", prompt);
        panel.layout = Objects.requireNonNull(layout);
        panel.footnote = Objects.requireNonNull(footnote);
        panel.own(kit);
        return panel;
    }

    /** The unlock form rendered inside {@code host} under {@code prefix}. */''', 1),
    ('''    /** The standalone prompt; domain policy remains in the submit handler. */
    Nodes.Form node() {
''', '''    /** The standalone prompt; domain policy remains in the submit handler. */
    Nodes.Form node() {
        if (layout == Layout.CLASSIC && !creating) return classicNode();
''', 1),
    ('''    /**
     * The unlock form inside a larger view: its fields and Cancel.''', '''    /** The classic Unlock Vault (spec 14.8): the lock tile beside the intro, the password in the label column, the footnote. */
    private Nodes.Form classicNode() {
        var top = new Nodes.Stack(form + ".hero", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Icon(form + ".icon", ControlState.READY, "lock.keyhole", Nodes.Hue.BLUE, Nodes.IconSize.PROMPT, "Locked vault"),
            new Nodes.Text(intro, ControlState.READY, introText, Nodes.TextRole.BODY)));
        var sections = List.<Nodes.Node>of(
            new Nodes.FormRow(form + ".row", ControlState.READY, "Master password", "", secret(password, "Master password"), Optional.empty(), false),
            statusLine());
        return new Nodes.Form(form, ControlState.READY, Nodes.FormKind.PROMPT_COMPACT, primaryTitle, Optional.of(top), sections, Optional.empty(),
            List.of(cancelButton(), submitButton()), cancel, Nodes.FormSubmission.PRIMARY, footnote);
    }

    /**
     * The unlock form inside a larger view: its fields and Cancel.''', 1)])
edit("plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java", [
    ('''        PasswordPanel panel = PasswordPanel.unlock(context.kit(), prompt);''',
     '''        // The footnote is the auto-lock (plan 10b Rulings 11); the rows layout ignores it.
        PasswordPanel panel = PasswordPanel.unlock(context.kit(), prompt, layout,
            settings.autoLock().isZero() ? "" : "Auto-locks after " + settings.autoLock().toMinutes() + " min idle");''', 1)])
PY
```

The two-argument `unlock(kit, prompt)` is unchanged (ROWS, no footnote), so `PasswordCompositionTest`,
`VaultManagerWindowTest` and the evidence captures keep today's form. The manager's own locked area keeps
`embedded()` in both layouts.

- [ ] **Step 4: Run the tests and compare with the mock**

Add the ledger row first (Execution notes R6):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv")
text = path.read_text(encoding="utf-8")
source = "plugins/vault/src/test/java/dev/jasper/vault/ui/ClassicUnlockTest.java"
if source in {line.split(",", 1)[0] for line in text.splitlines()}: raise SystemExit("already in the ledger")
path.write_text(text + f"{source},dev.jasper.vault,ClassicUnlockTest,nonvisual-helper,none,none,{source},Test/fixture ownership; New in Enterprise plan 10b Task 7 (spec 14.8)\n", encoding="utf-8")
PY
```

Run: `./gradlew :jasper-plugin-vault:test --tests 'dev.jasper.vault.ui.*' --tests 'dev.jasper.vault.VaultPluginTest' :jasper-app:test --tests 'dev.jasper.app.plugins.ClassicVaultRenderTest' --tests 'dev.jasper.app.plugins.VaultRenderEvidenceTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `ClassicUnlockTest` (3), `ClassicVaultRenderTest` (5), the ledger's coverage.

The body is 114 tall in the mock: 20 above the tile, the 35 tile, the field row and "Show password" under it, then the
form's bottom inset. A body of another height is the PROMPT_COMPACT form's insets (`FORM_TOP`, `FORM_SIDES`) or the
prompt reveal's gap (`ICON_GAP`, Task 2); the tile is `PROMPT_TILE` (30 in the classic catalog against the mock's 35:
record it as a residual with the measured width unless the band says otherwise). The focused field's ring is not
compared (no focus owner headless). Fix the composition or a classic value, never the expected bands; record the edge
agreement.

- [ ] **Step 5: Commit**

```bash
git add plugins/vault/src jasper-app/src/test docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Vault compositions: the classic Unlock Vault

Under the Enterprise layout the Unlock Vault overlay puts the lock tile
beside the intro, the master password in the label column with "Show
password" under it, and the auto-lock as the footer's footnote (none
when auto-lock is off). Readiness and submission are unchanged, and the
render matches the mock's body and footer.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 8: `CompositionParityTest` for the Vault

Spec 14.7 and 16.1.9: every action the rows manager can send, in every state (nothing selected, a login, an SSH key and
a note selected, locked, the grants card), has a classic path, and each runs the same callback; the search and the
category filter narrow both alike (Rulings 13 of plan 10a: one parity test per module).

**Files:**
- Create: `plugins/vault/src/test/java/dev/jasper/vault/ui/CompositionParityTest.java`
- Modify: `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Tasks 3 to 5's classic manager and its ids; `KitViews.all`, `emit`, `press`.
- Produces: none (the tests).

- [ ] **Step 1: Write the tests**

Create `plugins/vault/src/test/java/dev/jasper/vault/ui/CompositionParityTest.java`:

```java
package dev.jasper.vault.ui;

import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.workstation.Layout;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Spec 14.7: each default-look gesture of the Vault manager has a classic path with the same effect. Every gesture runs
 * on a rows panel and on a classic panel over the same entries, each recording what the plugin is asked to do.
 */
class CompositionParityTest {
    @TempDir Path directory;
    static final VaultManagerWindow.Status STATUS = new VaultManagerWindow.Status("keychain", "15 min", "/synthetic/vault.jv");
    final UUID login = UUID.randomUUID(), key = UUID.randomUUID(), note = UUID.randomUUID();
    final Map<UUID, Object> entries = new HashMap<>();

    record Run(ManagerPanel panel, List<String> asked) { }

    List<VaultManager.Row> rows() {
        entries.put(login, new Account(login, "amp", "ubuntu", new Auth.Password("synthetic-amp".toCharArray()), Instant.EPOCH, Instant.EPOCH));
        entries.put(key, new SshKey(key, "laptop", "ssh-ed25519", "SHA256:fixture", "", directory.resolve("laptop"), directory.resolve("laptop.pub"), Instant.EPOCH));
        entries.put(note, new Note(note, "Recovery", "synthetic-note".toCharArray(), Instant.EPOCH));
        return List.of(new VaultManager.Row(login, "amp", "ubuntu", VaultManager.Type.LOGIN),
            new VaultManager.Row(key, "laptop", "SHA256:fixture", VaultManager.Type.SSH_KEY),
            new VaultManager.Row(note, "Recovery", "", VaultManager.Type.NOTE));
    }

    Run run(Layout layout) {
        var asked = new ArrayList<String>();
        var panel = new ManagerPanel(new FakeKit("dev.jasper.vault"), layout, id -> Optional.ofNullable(entries.get(id)),
            type -> asked.add("add " + type), id -> asked.add("edit " + id), id -> asked.add("delete " + id), id -> asked.add("copy-public " + id),
            grant -> asked.add("revoke " + grant.pluginId()), () -> asked.add("generate"), () -> asked.add("lock"), () -> asked.add("unlock"),
            () -> asked.add("close"));
        return new Run(panel, asked);
    }

    /** Every action the shown, enabled nodes of {@code views} can send: buttons, menu items, activations and copies. */
    static Set<String> reachable(UiView... views) {
        var actions = new TreeSet<String>();
        for (var view : views) for (var node : all(view)) {
            if (!node.state().visible() || !node.state().enabled()) continue;
            switch (node) {
                case Nodes.Button button -> { if (!button.action().isBlank()) actions.add(button.action()); }
                case Nodes.MenuButton menu -> menu.items().stream().filter(item -> !item.separator()).map(MenuItem::action).forEach(actions::add);
                case Nodes.ItemList list -> { if (!list.activateAction().isBlank()) actions.add(list.activateAction()); }
                case Nodes.Table table -> { if (!table.activateAction().isBlank()) actions.add(table.activateAction()); }
                case Nodes.Secret secret -> { if (!secret.copyAction().isBlank()) actions.add(secret.copyAction()); }
                default -> { }
            }
        }
        return actions;
    }

    static void select(Run run, UUID id) { emit(run.panel().view(), new UiEvent.Selected(ManagerPanel.ROWS, Set.of(id.toString()))); }

    static void assertParity(String state, Run rows, Run classic) {
        assertThat(reachable(classic.panel().view())).as(state).containsAll(reachable(rows.panel().view(), rows.panel().titleActions()));
    }

    @Test void everyRowsActionHasAClassicPathInEachState() throws Exception {
        edt(() -> {
            var rows = run(Layout.ROWS); var classic = run(Layout.CLASSIC);
            var list = rows(); var grants = List.of(new Grant("dev.jasper.remote", login));
            for (var run : List.of(rows, classic)) run.panel().refresh(list, grants, STATUS, true);
            assertParity("nothing selected", rows, classic);
            for (UUID id : List.of(login, key, note)) {
                select(rows, id); select(classic, id);
                assertParity("selected " + entries.get(id).getClass().getSimpleName(), rows, classic);
            }
            for (var run : List.of(rows, classic)) run.panel().refresh(list, List.of(), STATUS, false);
            assertParity("locked", rows, classic);
            for (var run : List.of(rows, classic)) { run.panel().refresh(list, grants, STATUS, true); run.panel().showGrants(); }
            assertParity("the grants card", rows, classic);
            rows.panel().close(); classic.panel().close();
        });
    }

    @Test void eachGestureHasTheSameEffectInBothLayouts() throws Exception {
        edt(() -> {
            var rows = run(Layout.ROWS); var classic = run(Layout.CLASSIC);
            var list = rows();
            for (var run : List.of(rows, classic)) { run.panel().refresh(list, List.of(), STATUS, true); select(run, key); }
            var actions = new ArrayList<>(reachable(rows.panel().view(), rows.panel().titleActions()));
            // The copies go through the plugin's CopyCards by the same act() case in both layouts; Done is checked last.
            actions.removeIf(action -> action.startsWith("detail.") || action.equals(ManagerPanel.CLOSE));
            assertThat(actions).contains(ManagerPanel.EDIT, ManagerPanel.DELETE, ManagerPanel.COPY_PUBLIC, ManagerPanel.LOCK, "add.LOGIN",
                "add.SSH_KEY", "add.NOTE", ManagerPanel.ADD_GENERATE);
            for (String action : actions) { press(rows.panel().view(), action); press(classic.panel().view(), action); }
            assertThat(classic.asked()).isEqualTo(rows.asked());
            for (var run : List.of(rows, classic)) {
                emit(run.panel().view(), new UiEvent.Edited(ManagerPanel.SEARCH, "lap"));
                emit(run.panel().view(), new UiEvent.Selected(ManagerPanel.KIND, Set.of(VaultManager.Type.SSH_KEY.name())));
            }
            assertThat(classic.panel().visibleRows()).isEqualTo(rows.panel().visibleRows()).hasSize(1);
            for (var run : List.of(rows, classic)) { run.panel().refresh(list, List.of(), STATUS, false); press(run.panel().view(), ManagerPanel.UNLOCK); }
            press(rows.panel().view(), ManagerPanel.CLOSE); press(classic.panel().view(), ManagerPanel.CLOSE);
            assertThat(classic.asked()).isEqualTo(rows.asked());
            rows.panel().close(); classic.panel().close();
        });
    }
}
```

`press(view, action)` emits `UiEvent.Action(action)` to the view's handler, as a click on any node with that action
does; both of a panel's views share its handler (`ManagerPanel.java:71-72`).

- [ ] **Step 2: Run the tests**

Add the ledger row first (Execution notes R6):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/design/theme-engine/migration.csv")
text = path.read_text(encoding="utf-8")
source = "plugins/vault/src/test/java/dev/jasper/vault/ui/CompositionParityTest.java"
if source in {line.split(",", 1)[0] for line in text.splitlines()}: raise SystemExit("already in the ledger")
path.write_text(text + f"{source},dev.jasper.vault,CompositionParityTest,nonvisual-helper,none,none,{source},Test/fixture ownership; New in Enterprise plan 10b Task 8 (spec 14.7)\n", encoding="utf-8")
PY
```

Run: `./gradlew :jasper-plugin-vault:test --tests 'dev.jasper.vault.ui.CompositionParityTest' --tests 'dev.jasper.vault.ui.ManagerPanelTest' :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (2 parity tests, `ManagerPanelTest` unchanged, the ledger's coverage). A failing `assertParity` names the state and lists the action the classic view lacks: add its path
to the classic composition (Tasks 3 to 5), never remove it from the rows one.

- [ ] **Step 3: Commit**

```bash
git add plugins/vault/src/test docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Vault compositions: CompositionParityTest

Every action the rows manager can send, with nothing selected, with a
login, a key or a note selected, locked and on the grants card, has a
classic path, and each runs the same callback in both layouts; the
search and the category filter narrow both alike.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 9: Auxiliary frames without a menu row under a classic look

Rulings 14 (the orchestrator's ruling of 2026-10-17): under a look whose terminal windows show the classic menu row in
the window (`ShellChrome.classicMenus`: the Enterprise look), an auxiliary frame (the Credential Vault window, File
Server's and every other plugin window) shows no menu row, as the 07 Credential Vault mocks draw it, and binds Close
Window's key on its root pane. The default look keeps the minimal File row (or the unified title bar's menu) unchanged.
A plugin cannot ask for this through SDK 0.18, so the host does it once for every auxiliary frame.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/ShellChrome.java:7-8,13-14,18,52-55`,
  `jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java:305-308,323,714`,
  `jasper-app/src/main/java/dev/jasper/app/platform/ClassicMenuRow.java:29-30`,
  `jasper-app/src/main/java/dev/jasper/app/workspace/WindowPairs.java:23`,
  `jasper-app/src/test/java/dev/jasper/app/windows/ShellChromeTest.java:26-51`,
  `docs/app-architecture.md:223-224`, `docs/sdk-architecture.md:380-381`,
  `docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md` (its header and 10.1)

**Interfaces:**
- Consumes: `ShellChrome.classicMenus` (6a), `AuxiliarySurface.closeCommand()` and `onCloseCommand`,
  `WindowInput.captureShortcut` and `SHORTCUT_RECORDER`.
- Produces: package-private `boolean ShellChrome.frameMenuRow()` (replaces `styleMenus(JMenuBar)`, whose only callers
  were `NativeShells.frame`'s two lines) and `static void NativeShells.bindCloseWindow(JRootPane, AuxiliarySurface)`.

- [ ] **Step 1: Write the failing test**

Replace `ShellChromeTest.aClassicLookGivesAFramesMenuRowTheTerminalWindowsRow` (its javadoc and body, `:26-51`) with:

```java
    /** Plan 10b Rulings 14 (spec 10.1 as amended): a classic look's auxiliary frames show no menu row and close with their key. */
    @Test void aClassicLooksFramesHaveNoMenuRowAndCloseWithTheirKey() throws Exception {
        assertThat(ShellChrome.of(new ShellLook(false, true, true), true).frameMenuRow()).as("a classic look on macOS").isFalse();
        assertThat(ShellChrome.of(new ShellLook(false, true, true), false).frameMenuRow()).as("a classic look elsewhere").isFalse();
        assertThat(ShellChrome.of(new ShellLook(false, false, false), true).frameMenuRow()).as("the default look").isTrue();
        assertThat(ShellChrome.standard().frameMenuRow()).isTrue();
        edt(() -> {
            var surface = new AuxiliarySurface("dev.jasper.vault.manager", "Credential Vault", AuxiliarySurface.Kind.WINDOW, false,
                new java.awt.Dimension(820, 571), null, null, ignored -> { throw new AssertionError("No native shell in tests"); });
            var commands = new java.util.concurrent.atomic.AtomicInteger();
            surface.onCloseCommand(() -> { commands.incrementAndGet(); return true; });
            var root = new JRootPane();
            NativeShells.bindCloseWindow(root, surface);
            int menu = com.formdev.flatlaf.util.SystemInfo.isMacOS ? java.awt.event.InputEvent.META_DOWN_MASK : java.awt.event.InputEvent.CTRL_DOWN_MASK;
            var key = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, menu);
            var action = root.getActionMap().get(root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(key));
            assertThat(action).as("Close Window's key is bound on the frame").isNotNull();
            action.actionPerformed(new java.awt.event.ActionEvent(root, java.awt.event.ActionEvent.ACTION_PERFORMED, "close"));
            assertThat(commands).as("it runs the window's Close Window command").hasValue(1);
            root.putClientProperty(dev.jasper.app.platform.WindowInput.SHORTCUT_RECORDER,
                new dev.jasper.app.platform.WindowInput.ShortcutRecorder(stroke -> stroke.equals(key)));
            action.actionPerformed(new java.awt.event.ActionEvent(root, java.awt.event.ActionEvent.ACTION_PERFORMED, "close"));
            assertThat(commands).as("a window recording shortcuts keeps the key").hasValue(1);
            surface.close();
        });
    }
```

`AuxiliarySurface`'s constructor is package-private and the test is in its package; a surface that is never shown
needs no shell. Headless tests cannot create the `JFrame` itself (Verified facts 13), so `frame`'s three lines are
proved by review and the GUI check.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile: "cannot find symbol … frameMenuRow()" and "… bindCloseWindow".

- [ ] **Step 3: The frame without a menu row, its key, and the documents**

```bash
python3 -I - <<'PY'
"""Plan 10b Task 9 step 3: a classic look's auxiliary frames show no menu row and bind Close Window's key."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
W = "jasper-app/src/main/java/dev/jasper/app/windows/"
edit(W + "ShellChrome.java", [
    ('''import dev.jasper.app.platform.ClassicMenuRow;\nimport javax.swing.JMenuBar;\n''', '', 1),
    ('''whether a
 * frame's own menu row is the classic one, and keeps''', '''whether an
 * auxiliary frame shows a menu row of its own, and keeps''', 1),
    ('''@param classicMenus whether a frame's menu row is the classic row of the look's terminal windows''',
     '''@param classicMenus whether the look's terminal windows show the classic menu row; its auxiliary frames show none''', 1),
    ('''    /** Styles {@code bar}, a frame's own menu row, as the look's terminal windows style theirs; the default look's stays as it is. */
    void styleMenus(JMenuBar bar) {
        if (classicMenus) ClassicMenuRow.style(bar);
    }''',
     '''    /**
     * Whether an auxiliary frame shows its own menu row, the minimal File menu: the default look's frames do; a classic
     * look's show none, as the 07 Credential Vault mocks draw them, and bind Close Window's key instead (spec 10.1).
     */
    boolean frameMenuRow() { return !classicMenus; }''', 1)])
edit(W + "NativeShells.java", [
    ('''            // Under in-window menus (spec 10.1) the minimal File menu is a row of this frame, so Close Window keeps its key;
            // a classic look gives it its terminal windows' row.
            JMenuBar menus = menuBar(surface);
            if (bar != null) bar.setMenuBar(menus); else { frame.setJMenuBar(menus); shell.styleMenus(menus); }''',
     '''            // The minimal File menu is a row of this frame (spec 10.1); a classic look's frames show no menu row, as the 07
            // Credential Vault mocks draw them, and bind Close Window's key themselves (plan 10b Rulings 14).
            JMenuBar menus = bar != null || shell.frameMenuRow() ? menuBar(surface) : null;
            if (bar != null) bar.setMenuBar(menus);
            else if (menus != null) frame.setJMenuBar(menus);
            else bindCloseWindow(frame.getRootPane(), surface);''', 1),
    ('''                if (bar != null) bar.setLight(!resolved.chrome().dark()); else shell.styleMenus(menus);''',
     '''                if (bar != null) bar.setLight(!resolved.chrome().dark());''', 1),
    ('''    /** Remembered bounds are ignored when no current display shows them, for example after unplugging a monitor. */''',
     '''    /**
     * Close Window's shortcut on {@code root}, for a frame without a menu row (a classic look's auxiliary frames, plan 10b
     * Rulings 14): it runs the window's Close Window command unless the window is recording shortcuts, as the item does.
     */
    static void bindCloseWindow(JRootPane root, AuxiliarySurface surface) {
        var key = KeyStroke.getKeyStroke(KeyEvent.VK_W, SystemInfo.isMacOS ? java.awt.event.InputEvent.META_DOWN_MASK : java.awt.event.InputEvent.CTRL_DOWN_MASK);
        root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, "jasper.closeWindow");
        root.getActionMap().put("jasper.closeWindow", new javax.swing.AbstractAction("Close Window") {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                if (!dev.jasper.app.platform.WindowInput.captureShortcut(root, key)) surface.closeCommand();
            }
        });
    }

    /** Remembered bounds are ignored when no current display shows them, for example after unplugging a monitor. */''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/platform/ClassicMenuRow.java", [
    (''' Terminal windows and the auxiliary frames of a classic look
 * share it. EDT only.''', ''' A classic look's terminal
 * windows show it. EDT only.''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/workspace/WindowPairs.java", [
    ('''({@code ClassicMenuRow} paints it; the row is shared with auxiliary frames)''', '''({@code ClassicMenuRow} paints it)''', 1)])
edit("docs/app-architecture.md", [
    ('''so each window's menus are a row in the window (an auxiliary frame's File row takes the
classic row's look, `ClassicMenuRow`).''', '''so each terminal window's menus are a row in the window (an auxiliary frame shows none
and binds Close Window's shortcut itself).''', 1)])
edit("docs/sdk-architecture.md", [
    ('''Cmd/Ctrl+W, for example by closing a tab. `NativeShells`' Close Window item calls
`AuxiliarySurface.closeCommand()`, which asks the handlers newest first and otherwise closes like''',
     '''Cmd/Ctrl+W, for example by closing a tab. `NativeShells`' Close Window item (or, in a classic look's
frame, which has no menu row, its shortcut) calls `AuxiliarySurface.closeCommand()`, which asks the
handlers newest first and otherwise closes like''', 1)])
edit("docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md", [
    ('''Amended on 2026-10-17 with plan 7's bottom-strip ruling, made under the same authority: 10.7 (the bottom strip is 24 px plus its rule).\n''',
     '''Amended on 2026-10-17 with plan 7's bottom-strip ruling, made under the same authority: 10.7 (the bottom strip is 24 px plus its rule).
Amended on 2026-10-17 with plan 10b's ruling: 10.1 (auxiliary windows carry no menu row under Enterprise).\n''', 1),
    ('''- Under `menu_bar = "window"` the auxiliary frames keep their minimal File menu (`NativeShells.java:494-514`), now a row
  in the window as it already is on Windows and Linux, so Close Window keeps ⌘W.''',
     '''- Under `menu_bar = "window"` the auxiliary frames show no menu row, as the 07 Credential Vault mocks draw them; their
  actions stay on their own controls, and each frame binds Close Window's ⌘W itself.''', 1)])
PY
git diff --stat
```

Expected: eight files changed (with Step 1's test) and no anchor message. `SystemInfo`, `KeyStroke`, `KeyEvent` and `JRootPane` are
already imported in `NativeShells`; `JMenuBar` stays imported there (`menuBar` returns one). The spec's header line is
the orchestrator's text; the 10.1 bullet is the smallest edit that makes it accurate (its stale `NativeShells` line
reference goes with the sentence it belonged to).

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.windows.*' --tests 'dev.jasper.app.application.PresentationReachTest' --tests 'dev.jasper.app.workspace.WindowChromeMenusTest' --tests 'dev.jasper.app.workspace.EnterpriseMenuRowMockTest' --continue`
Expected: PASS: `ShellChromeTest` (3, the replaced test among them), the window and placement tests unchanged, the
terminal windows' classic menu row unchanged (`ClassicMenuBar.style` still calls `ClassicMenuRow.style`).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src docs/app-architecture.md docs/sdk-architecture.md docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md
git commit -F - <<'EOF'
Auxiliary frames: no menu row under the classic look

Under a look whose terminal windows show the classic menu row, an
auxiliary frame (the Credential Vault window and every plugin window)
shows no menu row, as the 07 Credential Vault mocks draw it, and binds
Close Window's key on its root pane; the default look keeps the File
row. Spec 10.1 is amended with the orchestrator's ruling.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 10: Documentation, the visual pairs, STATUS and the whole build

Spec 17's documents for this plan's part, the opt-in `tools/visual/pairs.json` entries for the Vault renders (spec
16.1.4), the default-look proof, the full build with its counts, and the STATUS entry.

**Files:**
- Modify: `docs/credential-vault.md`, `docs/ui-architecture.md:39`, `jasper-ui/README.md:40-42`, `tools/visual/pairs.json`,
  `docs/STATUS.md`, this plan's status banner (line 3)

- [ ] **Step 1: The documents**

Read each document first and keep its voice; make the smallest accurate edits:

- `docs/credential-vault.md`: one paragraph after the paragraph that describes the manager ("The manager uses a single
  list …"): under the Enterprise look the manager is a toolbar row (**New** with the add menu, **Edit…**, **Delete**,
  **Copy Username**, **Copy Password**, **Lock Vault** and the search) over a Categories tree with counts, a
  Name/Username/Type/Modified table that sorts by its headers and counts what shows, and a Details pane whose stored
  values have **Show** and **Copy**, with the lock state, auto-lock and vault file in the status row beside **Done**; the
  add sheets carry their kind's colour, and Unlock Vault shows the auto-lock under its field.
- `docs/ui-architecture.md:39` names the one point that chooses two faces ("the table point chooses both the table and
  the Properties faces"); extend that clause: "…and the Properties faces, the buttons point the separator face
  (separators, document-split hairlines, manager column edges and pane strips) and the toggle point the reveal face (a
  secret's reveal and copy)".
- `jasper-ui/README.md:40-42` lists the faces `KitFaces` holds; add "separator, reveal," before "ordered list" (or at the
  list's natural place), nothing more.

- [ ] **Step 2: The visual pairs**

```bash
python3 -I - <<'PY'
import json, pathlib
path = pathlib.Path("tools/visual/pairs.json")
pairs = json.loads(path.read_text(encoding="utf-8"))
if any(pair["name"].startswith("composition-light-vault-") for pair in pairs): raise SystemExit("pairs.json already has the Vault composition pairs")
V = "docs/design/enterprise/reference/07 Credential Vault/Credential Vault "
O = "jasper-app/build/enterprise-compositions/"
new = [("light/vault-manager", V + "2 - New menu.png", [2, 60, 1640, 1142]),
       ("light/vault-manager-selected", V + "3 - Item selected.png", [2, 60, 1640, 1142]),
       ("light/vault-add-login", V + "4 - Add Login.png", [382, 112, 880, 494]),
       ("light/vault-add-ssh-key", V + "5 - Add SSH Key.png", [382, 112, 880, 430]),
       ("light/vault-unlock", V + "1 - Unlock Vault (over Workstation).png", [1106, 838, 800, 310])]
before = len(pairs)
for name, mock, crop in new:
    pairs.append({"name": "composition-" + name.replace("/", "-"), "mock": mock, "crop": crop, "render": O + name + "@2x.png",
                  "min_agreement": 0.55, "colours": ["#ECEDEF", "#E6E7EA", "#C4C7CC"]})
path.write_text(json.dumps(pairs, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(before, "->", len(pairs))
PY
git diff --stat tools/visual/pairs.json
```

Expected: 5 pairs added and only insertions in the diff (restore and insert by hand if `json.dumps` reformatted existing
entries). The render names are `MockMatch.save`'s (10a Task 2: `<name>@2x.png` under `enterprise-compositions/`); if
`save` writes another file name, use it. Where Tasks 3 to 7 recorded an edge agreement below 0.55 with its cause, write
that value into the pair's `min_agreement`. Then run the opt-in comparison once:

```bash
python3 -I tools/visual/check_pairs.py jasper-app/build/composition-compare composition-light-vault-
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
    base, now = digests(pathlib.Path("jasper-ui/build/plan10b-base") / theme), digests(pathlib.Path("jasper-ui/build/gallery") / theme)
    bad += [f"{theme}/{name}" for name in sorted(set(base) | set(now)) if base.get(name) != now.get(name)]
print("changed:", bad if bad else "none"); sys.exit(1 if bad else 0)
PY
git diff --stat "$(git log --format=%H -1 --grep 'Plan 10b: refresh anchors for execution')" -- jasper-ui/src/test/resources | tail -3
```

Expected: `changed: none`; the resources diff names only `compact-roles.tsv`, `classic-roles.tsv` and `face-pairs.tsv`
(Tasks 1 and 2). The existing `tools/visual/pairs.json` renders are the default look's, which no task changed (every Vault
change is under `Layout.CLASSIC`, and both jasper-ui faces' default classes are today's code).

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

Add a new entry at the top of `docs/STATUS.md`, above plan 10a's, in the style of the earlier entries:

```markdown
**Enterprise presentation, plan 10b: the Credential Vault composition — <date>**
(`claude/enterprise-plugin-compositions`, worktree `.claude/worktrees/enterprise-10`, `<first>..<last>`, then this
documentation commit; [plan](superpowers/plans/2026-10-16-enterprise-10b-vault-compositions.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). Under the Enterprise look the Vault
manager is the 07 mocks' classic window (820 × 571, no title actions): a toolbar row (New with the add menu, Edit…,
Delete, Copy Username, Copy Password, Lock Vault and its shortcut, the search) over a Categories tree with counts, a
sortable Name/Username/Type/Modified table with swatches and an "N of M items" footer, and a Details pane (swatch, name
and method, a Fields group, the untitled Properties Modified and Used by, Edit… and Delete…), with the status row (lock
state, auto-lock, vault file) beside Done; the add and edit sheets carry their kind's swatch, drop the prompt hero, title
their groups and label the method; Unlock Vault puts the tile beside the intro and the auto-lock in its footnote. Under
the classic look every auxiliary frame shows no menu row and binds Close Window's key on its root pane
(`ShellChrome.frameMenuRow`, `NativeShells.bindCloseWindow`; the orchestrator's ruling, spec 10.1 amended).
jasper-ui gains two face pairs: `SeparatorFace` (buttons point: 18 px `border.window` separators, document-split
hairlines and manager column edges, pane header strips) and `RevealFace` (toggle point: Show, Show password, Show and
Copy); a document split fills with a Split child; the bar's search width and the classic manager's widths and bar
padding are catalog roles. `ClassicVaultRenderTest` renders the staged Vault under the shipped kit, Plex and theme
against the five mock crops (bands, colours, edge agreement: <the measured agreements>); `CompositionParityTest` checks
every rows gesture's classic path. Rulings (plan, "Rulings"): <one clause each, 1 to 14>; Execution notes (2026-10-17)
R1 to R20. Residuals: <Rulings 13's, with
their measured values, the tile size of Task 7, and any floor a task lowered, with its cause>. For the user: decision 17's
sentence case for "Private Key" and "Public Key" in the default look is not applied (Rulings 7). The default look is
byte-identical (gallery digests unchanged; no default render changed). `./gradlew check --continue`: **<counts>**. GUI
checks for the user: the Enterprise Vault manager against the Item selected and New menu mocks (sorting, the tree's
collapse, Show and Copy, the toolbar's verbs while locked), Add Login, Add SSH Key and Unlock Vault; under Enterprise, a
plugin window without a menu row closing with ⌘W (and Ctrl+W on Windows and Linux), and the default look's File row
unchanged. Not merged, not pushed.
```

Replace the angle-bracketed parts with the facts of the run. Set this plan's status banner (a new line 3) to "**Status:
executed on <date>** (`<first>..<last>`); see `docs/STATUS.md`." and list any deviation from the plan's text there.

```bash
git add docs jasper-ui/README.md tools/visual/pairs.json
git commit -F - <<'EOF'
Vault compositions: documentation, the visual pairs and status

The Credential Vault guide describes the classic manager, sheets and
Unlock Vault, tools/visual/pairs.json pairs the five Vault renders with
their mock crops (opt-in), and STATUS records plan 10b with its rulings,
residuals and the full build.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Self-review

- Spec coverage (14.8 and row 10's Vault half): toolbar row with + New MenuButton and swatches (Task 3; swatches in the
  menu's hued glyphs and the table, Rulings 5), Categories tree with counts (Task 4), item table with footer (Task 4),
  Details pane with Fields group and Properties (Task 5), status row with Done (Task 3), ATTACHED_SHEET editors with
  swatches (Task 6), Unlock Vault PROMPT overlay with its footnote (Task 7), CompositionParityTest (Task 8), auxiliary
  frames without a menu row (Task 9, Rulings 14), documents, pairs, STATUS and the full build (Task 10). Workstation
  consumption for the Vault is 10a Task 1.
- Review Focus: 1 → Task 3 `lockingDisablesTheToolbarAndClosesTheSheet`; 2 → Task 4
  `aSearchThatMatchesNothingKeepsTheHeadersAndCountsZero`; 3 → Task 4 `theSortSurvivesARefreshAndTheSelectionStays`;
  4 → Task 2 `aStoredValuesShowIsPerEntry` and Task 5 `theDetailsPaneFollowsTheSelection`; 5 → Task 7
  `theFootnoteFollowsTheAutoLockAndUnlockNeedsAPassword`.
- Names used across tasks: `SeparatorFace`/`KitFaces.separators`/`HeaderControl.pane` (Task 1 → Tasks 3 to 5),
  `RevealFace`/`KitFaces.reveal`/`SecretControl.box` (Task 2 → Tasks 5 to 7), `ManagerPanel.TOOLBAR`, `LOCK_SHORTCUT`,
  `ITEMS_FOOTER`, `COLUMN_*` (Tasks 3, 4 → 8), `StagedVaultHost(…, boolean enterprise)`, `seedLogins`, `dispatchToManager`
  and `ClassicVaultRenderTest.render`/`bands` (Task 3 → Tasks 4 to 7), `EditorForm.layout`, `swatch()` (Task 6),
  `PasswordPanel.unlock(UiKit, UnlockPrompt, Layout, String)` (Task 7), `ShellChrome.frameMenuRow()` and
  `NativeShells.bindCloseWindow` (Task 9).

## Execution notes (2026-10-17)

Refreshed at `f866788de` (this branch is `main` there: every other plan is merged, 10a, 6b, 4b, 5b, 7, 8b, 9b and 11
among them). Read first: the STATUS entries of plans 10a, 6b, 5b and 4b, 10a's Execution notes (R1 to R21, D1 to D22)
and 6a ruling 31. Method (as 10a R1): every Create, Add-to, Replace and edit script of Tasks 1 to 9 was applied in order
to an export of `f866788de`, and jasper-ui (main, testFixtures, test), the Vault (main, test) and jasper-app (main, test)
were compiled with the JetBrains Runtime 25 `javac` against the worktree's built classes of the other modules; the
ledger scripts were run on the export and its candidate scan repeated in Python; the compiled reconciler's constant
pool was read with `javap`. Before the fixes below: one edit anchor missing (Task 1's `DesignMetrics.classic()`), two
compile errors (Task 3's `EnterpriseLook` import), one architecture violation (Task 1's reconciler named `KitFaces`), five
candidate sources without ledger rows and a staged Vault rendered in the default theme; after them every anchor is
found its stated number of times, every module compiles with zero errors, the reconciler names no face, the ledger
scan finds nothing missing and source hygiene is clean. No Gradle and no JUnit ran: assertions, bands, renders and
floors are still to be proved by execution.

### Pre-flight scan

| Tasks | Shared file or interface | Finding | Ruling |
|---|---|---|---|
| 1 | `KitFaces`, `Separators`, `SplitViews`, `Panels`, `PanelHeaders`, `ViewReconciler`, `Stacks`, `DesignMetrics`, TSVs | `DesignMetrics.classic()` lost its `THEME_PREVIEW_CAPTION_GAP, 8` anchor (9b: 7); the reconciler edit names `KitFaces` and `SeparatorFace`, which `StrategyIsolationTest` forbids; the two architecture tests are jasper-app's, so `:jasper-ui:test --tests` for them finds nothing; other anchors and code compile | R2, R3, R18 |
| 2 | `SecretFields`, `KitFaces`, `face-pairs.tsv` | Every anchor holds; `DefaultRevealFace` is `refreshReveal`, `revealVisibility` and the mounting code verbatim; same test-run defect as Task 1; the classic reveal reaches every Enterprise secret | R3 |
| 3 | `ManagerPanel`, `VaultManagerWindow`, `VaultPlugin`, `StagedVaultHost`, ledger | `EnterpriseLook` is `testsupport`'s (10a R4); the staged Vault without an appearance renders in the bundled default theme; the render sets two of 6b's three in-window flags; "⇧⌘L" is raw in the script though the text says escape; two new candidate sources; Step 2's expected message names `<null>` | R5, R6, R7, R8 |
| 4 | `ManagerPanel` (Task 3's `classicEntries()`), `ClassicManagerTest`, `ClassicVaultRenderTest` | Anchors on Task 3's text hold; manager-column insets predictably differ from the mock | R11 |
| 5 | `ManagerPanel` (Task 4's `classicEntries()` end), tests | Anchors hold; the agreement asserts bypass `MockCrops.fontsFloor`; the New menu crop shows an open menu the render does not | R9, R10 |
| 6 | `EditorForm`, `EntryEditor`, `EntryPresenter`, `VaultManagerWindow`, ledger | Anchors hold (the presenter's two `create` cases included); 10a hands over `FORM_TOP` / `MIXED_ROW_GAP` re-measurement; one new candidate source | R6, R13 |
| 7 | `PasswordPanel`, `VaultPlugin`, ledger | Anchors hold; `VaultPlugin.showUnlock` moved to `:287-302`; one new candidate source | R6, R18 |
| 8 | `CompositionParityTest` (Vault), ledger | Compiles against Tasks 3 to 5's ids and the 12-argument constructor; one new candidate source | R6 |
| 9 (new) | `ShellChrome`, `NativeShells`, `ShellChromeTest`, `ClassicMenuRow`, `WindowPairs`, docs, spec 10.1 | The orchestrator's ruling; compiles; `styleMenus` loses its callers and goes | R14 |
| 10 (was 9) | docs, `pairs.json`, STATUS, banner | `HEAD~8` counts eight task commits where there are now nine; the conditional face-list edit is decided (both docs list faces); `pairs.json` round-trips through `json.dumps(indent=2)` byte for byte | R15, R16 |
| 1 and 2 | `KitFaces` (`separators` before `reveal`), `face-pairs.tsv`, `PaintSpecTest`, `all()` | Task 2 anchors on Task 1's `final SeparatorFace separators;`, `separators = switch` and `, separators,`: task order is binding; the two pair rows go after `ClassicPropertiesFace`'s in `all()` order | none |
| 1 and 3 to 5 | classic separators, bar padding and search width, manager widths, pane strips | Task 3's toolbar and status bands and Task 4's columns rest on Task 1's values; `MANAGER_LIST_WIDTH` also sizes SIDEBAR_DETAIL lists | R11, R12 |
| 1, 2 and 10a's suites | the classic catalog and faces every Enterprise surface uses | SFTP's separators, Add Host's and New File Server's passwords, 6b's fixture prompt, 5b's gallery secrets move | R3 |
| 2 and 5 | DETAIL secret's Show and Copy | `ClassicRevealFace.detailActions` and `button(...)`; the reveal's SECONDARY kind is not flat in 4b's face though it stays `inner` | none |
| 2 and 6 | DISCREET_INPUT "Show" | Add Login's password is discreet (`discreetInputs()`), and the classic face always shows its checkbox | none |
| 2 and 7 | prompt "Show password" | `SecretControl.prompt(true)` moves the box row under the field; `classicNode` keeps `secret(password, …)` | none |
| 3 and 4, 4 and 5 | `ManagerPanel` edit chain, `ClassicManagerTest` and `ClassicVaultRenderTest` additions | Task 4 replaces Task 3's `classicEntries()` verbatim, Task 5 anchors on Task 4's `detail()));` line and `/** The item table:`; Task 5's test uses Task 4's `date(...)`; render methods grow 1, 2, 3, 4, 5 | none |
| 3 and 6 | `VaultManagerWindow` constructor | Task 3 assigns `this.layout` before the `new EntryPresenter(context)` Task 6 edits (javac flow check passes) | none |
| 3 and 7 | `VaultPlugin` | Disjoint lines (`:140`, `:294`); both read the field `layout` from 10a | none |
| 3, 6, 7, 8 | `migration.csv` appends | Each script checks the set of first fields and appends one row; order-independent | R6 |
| 3 to 7 | `StagedVaultHost` Enterprise mode, `render`, `bands`, `check` | One appearance fixture and one flag set for every surface | R5, R7 |
| 3 to 5 and 8 | classic ids and callbacks | Every rows action has a classic node: toolbar verbs, the menu's items, the table's activation, the Details' copies, `COPY_PUBLIC`, `UNLOCK`, `BACK` | none |
| 6 and 10a | `FORM_TOP`, `SHEET_SECTION_GAP`, `MIXED_ROW_GAP` | Shared with Add Host's native dialog (10a D11) | R13 |
| 3 and 9 | the Vault window's frame | Rulings 12's 820 × 571 content now meets the mock's window under its 29 px title bar with no menu row between | R14 |
| 9 and 10 | docs and STATUS | Task 9 edits `app-architecture.md`, `sdk-architecture.md` and spec 10.1; Task 10's STATUS template names Task 9 | none |
| 1, 2 and 10 | the gallery base | Captured once in Task 1 Step 1, compared in Tasks 1, 2 and 10 | none |

### Rulings

"What — why — cost if wrong". They bind where they differ from the task text above, which has been edited to agree.

R1. **The refresh is a javac dry run** — what: the method above; why: the orchestrator forbids Gradle here and javac
proves anchors, names, types and flow (10a R1's method); cost if wrong: an assertion or render fails at execution, which
the TDD steps expect and fix in place.

R2. **The reconciler names no face: it sets `HeaderControl.pane = column >= 0`, and `flush()` asks the separator face**
— what: Task 1's `ViewReconciler` edit drops `view.context().faces().separators.paneStrips()`; `PanelHeaders.flush()`
reads `pane && context().faces().separators.paneStrips() || …`; `ClassicSeparatorsTest` asserts `pane` true and
`flush()` true under the classic kit, `flush()` false under the default kit and `pane` false for a dock panel; why: the
plan's version puts `KitContext.faces:()Ldev/jasper/ui/swing/KitFaces;` and `SeparatorFace` into the reconciler's
constant pool (read with `javap` from the dry run), which `StrategyIsolationTest` (spec 16.1.7) reports; cost if wrong:
none (the same pixels).

R3. **The architecture scans and the Enterprise suites run in jasper-app** — what: Tasks 1 and 2 run
`dev.jasper.app.architecture.StrategyIsolationTest` and `VariantReadTest` under `:jasper-app:test`, and once each the
Enterprise suites their classic faces reach (`ClassicCompositionRenderTest`, `EnterpriseKitRenderTest`,
`EnterpriseWorkstationMockTest`; for Task 2 also `EnterpriseDialogMockTest` and `VaultSheetRenderTest`); a band or floor
they move is fixed in the face, and only where the fix would contradict a 07 mock is the measured render value written
into that test with its cause and recorded as a deviation (10a R11's precedent), never silently; why: under
`:jasper-ui:test` the two scans are "No tests found", which fails the build, and 10a's STATUS asks for re-runs after
classic changes; cost if wrong: a few minutes per run.

R4. **A band that fails only on text ink may be re-sampled** — what: when a mismatch is runs of glyph ink inside a known
text element (a header strip's title, a label), the sample column may move within the same element to a column where
mock and render are both clear of text, re-measured on the mock with `MockMatch.bands` and recorded in the banner; why:
Plex's 2× glyphs leave 2 px runs where the mock's antialiasing left 1 px runs that `bands` drops (Details at x 1086
crosses "Details" in the strip); cost if wrong: one element is sampled a few pixels from the plan's column.

R5. **The staged Vault renders in the Enterprise theme** — what: `ClassicVaultRenderTest.vault` passes
`new WorkspaceAppearanceFixture(EnterpriseLook.resolved(false))` to `StagedVaultHost`, and imports
`dev.jasper.app.testsupport.EnterpriseLook`; why: with a `null` appearance `HostedUi.kitThemes` falls back to the
bundled default theme (Verified facts 10), so every band colour would be Slate Cobalt's; `application.EnterpriseLook`
does not exist (10a R4); cost if wrong: none.

R6. **Every new candidate source gets its ledger row in its own commit** — what: Tasks 3 (`ClassicManagerTest`,
`ClassicVaultRenderTest`), 6 (`ClassicEditorsTest`), 7 (`ClassicUnlockTest`) and 8 (the Vault's `CompositionParityTest`)
append `nonvisual-helper,none,none` rows ("Test/fixture ownership; New in Enterprise plan 10b Task N"), checked
against the set of first fields (10a R10), run `ThemeMigrationCoverageTest` and add `migration.csv` to their commits;
why: all five match the candidate pattern (checked on the dry run) and `sourceDiscoveryIncludesTestFixturesAndHelperFalsePositives`
fails otherwise; the orchestrator's rule; jasper-ui is outside the scan; cost if wrong: none.

R7. **`render` mirrors `NativeShells.classicContent`** — what: for a dialog surface it makes the holder transparent and
sets `hostTitled`, `dialogBand` and `dialogCard` (6b's `CLASSIC_FLAGS`); why: the host draws every in-window classic
dialog that way, and without `dialogCard` the form paints its own fill and frame inside the crop; cost if wrong: none.

R8. **"⇧⌘L" is written as the Java escapes `⇧⌘L`** — what: the raw Python string now holds the escape, as the
step's text always said; why: the script wrote the raw characters; both pass the hygiene check, the escape keeps the
source ASCII; cost if wrong: none.

R9. **Task 5's edge agreements go through `MockCrops.fontsFloor`** — what: as `ClassicCompositionRenderTest.check` (10a
R15): asserted under the shipped IBM Plex, printed only where an installed Plex hides the plugin's files; on this machine
Plex resolves, so mock matching under Plex is asserted for all five 07 mocks (Tasks 5, 6 and 7); cost if wrong: none.

R10. **The New menu mock is matched with its menu closed** — what: `light/vault-manager` renders the manager with no
popup (a `JPopupMenu` is not part of the holder and the menu's face is 4b's and 4c's); if its agreement falls under 0.55
only because of the open menu's region, that pair's floor (test and `pairs.json`) is the measured value rounded down to
two decimals, with the cause in the banner (10a D13's precedent); the bands at x 1600 lie clear of the menu; cost if
wrong: the open menu's pixels go unchecked against this mock.

R11. **Manager-column insets, if Task 4's first run shows them** — what: `Panels.refresh` (`Panels.java:73-76`) starts a
rail, and a list column without a search row, at `COLUMN_RAIL_TOP` (8): the Items table would stand 8 below the toolbar
rule where the mock's header band starts flush (`0+20 #F0F1F3`), and the Categories rows 8 below the strip where the
mock has 4 (`60+4`); the preferred fix is classic `COLUMN_RAIL_TOP` 4 (under Enterprise only manager columns read it:
the Vault's and the Licenses and Errors list columns, R12's reach) and top 0 for a manager column whose body is a `Table` (its header band is its top edge), in
`Panels.refresh`; no default view has a `Table` body in a manager column (the Vault's rows composition, Licenses and
Errors use item lists), so the gallery digests prove the default look; record it as a deviation; why: the mock wins on
layout and the plan's triage already names `COLUMN_RAIL_TOP`; cost if wrong: one condition.

R12. **The classic manager widths also reach SIDEBAR_DETAIL lists** — what: `MANAGER_LIST_WIDTH` 348 also sizes the list
column of the Licenses and Errors windows under Enterprise (`SplitViews.java:94`); accepted and named in STATUS (reach)
and the GUI checks; why: no mock covers those windows, and Rulings 4 sets the catalog value; cost if wrong: a role of its
own for a SIDEBAR_DETAIL list.

R13. **The 07 sheets re-measure `FORM_TOP` and `SHEET_SECTION_GAP`** (10a's hand-off) — what: Task 6 adds the group
rules down x 34 (first rule 16 below the strip, Add Login's boxes 19 apart, measured on 2026-10-17); these roles are
shared with 10a's native dialogs (D11: classic `FORM_TOP` 10, `MIXED_ROW_GAP` 8, measured on Add Host); a shared value
changes only if `ClassicCompositionRenderTest` still passes; where the 07 sheets and 10a's dialogs need different values,
10a's value stays, the x-34 expected band takes the render's measured value with the cause, and the difference is a
residual (10a R11's precedent); why: one catalog serves both, and 10a's mocks were matched first; cost if wrong: 1 or
2 px in the sheets.

R14. **Auxiliary frames show no menu row under a classic look (the orchestrator's ruling, Rulings 14)** — what: new Task
9: `ShellChrome.frameMenuRow()` (false while `classicMenus`) replaces `styleMenus`; `NativeShells.frame` installs the
File row only for a unified title bar or `frameMenuRow()`, else `bindCloseWindow` puts ⌘W (Ctrl+W off macOS, the
`SystemInfo` mask, headless-safe) on the root pane, running `closeCommand()` unless the window records shortcuts, as the
item does; `ShellChromeTest` replaces its styling test with one on a `JRootPane`; `app-architecture.md`,
`sdk-architecture.md`, `ClassicMenuRow`'s and `WindowPairs`' comments and spec 10.1 (one bullet, plus the header line
"Amended on 2026-10-17 with plan 10b's ruling: 10.1 (auxiliary windows carry no menu row under Enterprise).") follow;
the documentation task becomes Task 10; why: the mock wins on layout; SDK 0.18 gives a plugin no say over its frame's
menus, so the host's `NativeShells.frame` is the one clean place, and the change covers every auxiliary frame, as 6a
ruling 31's cost foresaw; headless tests cannot create a `JFrame`; spec 4.7's fallback under committed in-window menus
also has `classicMenus` and drops the row too, which is accepted; cost if wrong: New Window and (off macOS) Quit are no
longer in a plugin window, only in terminal windows' menus; one branch restores the row.

R15. **Task 10's resource diff starts at this refresh commit** — what: `git diff --stat "$(git log --format=%H -1 --grep
'Plan 10b: refresh anchors for execution')"`; why: `HEAD~8` assumed eight task commits; there are nine; cost if wrong:
none.

R16. **Task 10's face-list edits are named** — what: `docs/ui-architecture.md:39` and `jasper-ui/README.md:40-42` gain the
separator and reveal faces, and the commit adds `jasper-ui/README.md`; why: both documents list `KitFaces`' faces, so the
plan's conditional is decided; cost if wrong: none.

R17. **"Before you start" reads `dialogSwatch(String pluginId, String title)`** — what: the grep pattern; why: the testkit
declares the return type fully qualified, so `Optional<Nodes.Hue> dialogSwatch` counts 0; cost if wrong: none.

R18. **Line numbers and anchors are `f866788de`'s** — what: Verified facts 1 to 14 and every task's "Files" lines
(`DesignMetrics.classic()` now anchors on `int[] table = declared();`; `SecretFields` `:44`, `:50-51`, `:103-108`,
`:208-215`, `:220`, `:318-342`, `:383-387`; `Panels.java:343`; `ViewReconciler.java:197`; `Stacks.java:865-867`;
`KitView.java:62`, `:213`, `:432`; `VaultPlugin.java:140`, `:294`; `StagedVaultHost.java:47-71`, `:184`, `:274-275`;
`EditorForm.java:56`, `:205-231`); the "Before you start" refresh step is done here; why: 10a, 6b, 7, 8b and 9b moved
them; cost if wrong: none (every edit quotes its text).

R19. **Only plan 10b executes; no rebase, merge or push** — what: Tasks 1 to 10 on this branch from `f866788de`, every
command from the worktree root, never `./gradlew --stop`, no GUI and no AOT training; a timing-sensitive test that fails
once is re-run alone before it counts as a failure; why: the orchestrator; cost if wrong: one extra run.

R20. **"Remember in Keychain" is not built (spec 18)** — what: no checkbox or setting for it in Unlock Vault or elsewhere;
the status row's "Unlocked · Keychain" is the configured device-secret provider (`VaultManagerWindow.Status.deviceSecret`,
already shown by the rows composition); why: the orchestrator and spec 18 name it a fixture; cost if wrong: none.
