# Enterprise presentation 3d: SDK 0.18 chrome Implementation Plan

> **Status (2026-10-16): executed on `claude/enterprise-sdk-0-18-chrome` (`d6210499e..9f7a43ae8`), then the documentation
> commit and the final review's fixes.** Deviations from the spec, awaiting the user's approval: ruling 1 (toolbar view
> admission at mount and on update, not at registration; spec 10.3 and 15) and ruling 6 (no dialog swatch in the
> Windows and Linux FlatLaf title pane; spec 8.2). Deviations from this plan's text: none beyond its "Rulings", its
> "Execution notes (2026-10-16)" and the review fixes' toolbar-view title bar route (`ThemeRoute.TITLE_BAR`).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** SDK 0.18 gains its chrome additions of spec 8.2, each carried by the host, the testkit fake and the shared
contracts and rendered in today's default look: the Session and Transfer standard menus, toolbar views
(`ToolbarItem.View` with `ToolbarViewFactory`) in today's header, the palette's `ScopeSpec.hue`, `ScopeSpec.itemNoun`
and `PaletteVerb.role` with their app-native mirrors, `DialogSpec.swatch`, and the bundled scopes' nouns, hues and SPLIT
role of spec 14.6, with no existing pixel changed.

**Architecture:** Every addition follows the existing path of its family. A standard menu is a `MenuTarget.Slot` that
`WindowChrome` builds fresh on each pass and shows only while it has entries, as Tools already is. A toolbar view is an
app-model `ToolbarEntry.View` whose factory each window calls once, when its open event fires
(`WindowContent.connectTerminals`, which the warm-up rehearsal never calls), like a panel factory; `HostedUi.toolbar`
translates the SDK item into it, admitting the view's tree with `ToolbarViewAdmission` at mount and on every update,
and `WindowToolbar` learns to place a view without ever moving it into the overflow menu. The palette additions are SDK
record components validated in the SDK and mirrored, with effective values, by `dev.jasper.app.palette` through
`HostedPalette`; nothing draws them until plan 8's Run window. `DialogSpec.swatch` travels to `AuxiliarySurface` and is
drawn by `MacTitleBar` before the dialog title, the only dialog title the default look draws itself.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, Python 3.11+ standard library for the mechanical edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 8.2 rows `StandardMenu.SESSION`, `TRANSFER`, `ToolbarItem.View`, `ScopeSpec.hue`, `ScopeSpec.itemNoun`,
`PaletteVerb.role` and `DialogSpec.swatch`; 10.2's last paragraph; 10.3's toolbar-view rules; 10.8's swatch; 14.4's
last line and 14.6; 15's two toolbar-view rows; 16.1.9; 17; 19.1 row 3d). Plans 1, 2 and 3a (SDK 0.18 renames) are merged on
`main` (`deafed424`). Plan 3b (controls) executes in parallel in its own worktree and plan 3c (tables) later, so this
plan uses no 3b or 3c API (Execution notes, R1); shared files are reconciled when the second of 3b and 3d merges.
Moving Remote's SSH menu entries into Session and Transfer is plan 3e (section 13), not this plan; the palette draws
`hue`, `itemNoun` and `role` only in plan 8's Run window.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-3d`, branch
`claude/enterprise-sdk-0-18-chrome`, based on `main` at `deafed424` (plan 3a merged). Run every command from the
worktree root, never in `~/projects/moray` itself. Line numbers below are from `deafed424`, re-verified on 2026-10-16;
every edit quotes the exact text it replaces as it is at `deafed424`, and the edit scripts stop with a message when that
text is not found the stated number of times. If a script stops on an anchor, re-find the quoted text (it moves, it
does not change) and re-run; never edit around a missing anchor. Plan 3b, running in parallel, edits some of the same
shared files (`PluginContractTest`, `KitView`, `docs/plugin-ui.md`, `docs/plugin-authoring.md`, `jasper-sdk/README.md`,
`docs/STATUS.md`); this branch never takes 3b's commits, and the second of the two to merge reconciles them (Execution
notes, R1 and R8).

## Before you start

Do not rebase or merge: the branch stays on its base. Check the base and that this plan needs nothing beyond 3a:

```bash
git status --short
git merge-base HEAD main
grep -c 'VERSION = "0.18.0"' jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java
grep -c 'record Separator(' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java
```

Expected: no output from `git status --short`; the merge base starts `deafed424` (if `main` has moved on, still work
from this base and say so in the status banner); the version check prints 1 (3a's 0.18.0) and the `Separator` check
prints 0 (3b is not on this branch, and no task needs it: Execution notes, R1). No task uses a 3b or 3c type, and
Task 7's test-count base is this branch's base, `deafed424`.

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md). `:jasper-ui:gallery` is headless (`java.awt.headless=true`) and may be run.
- Work only in the worktree above on `claude/enterprise-sdk-0-18-chrome`, every command from its root; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push, merge or rebase, and never run a bare `git stash`.
- Zero pixel change for every existing fixture: no metric, type, colour or layout of an existing control changes. None of this plan's additions is a `Nodes` node, so the jasper-ui gallery gains no fixture and **no gallery file may change**: every PNG and JSON file the headless gallery writes for Slate Cobalt Dark and Light is byte-identical to the base (Task 4 checks it). A new code path paints only when a plugin asks for it (a toolbar view, a non-NONE swatch, a Session or Transfer entry).
- The additions are exactly spec 8.2's rows for this plan, with spec 8.2's names and shapes: `StandardMenu.SESSION`, `TRANSFER`; `record View(ToolbarViewFactory factory) implements ToolbarItem` and `@FunctionalInterface interface ToolbarViewFactory { UiView create(WindowHandle window); }`; `ScopeSpec.hue` (`Nodes.Hue hue`) and `ScopeSpec.itemNoun` (`String itemNoun`); `PaletteVerb.role` (`Role role`, `enum Role { STANDARD, SPLIT }`); `DialogSpec.swatch` (`Nodes.Hue swatch`); app mirrors `PaletteScope.hue()`, `itemNoun()` and `PaletteVerb.role()` in `dev.jasper.app.palette`, translated in `dev.jasper.app.plugins`.
- "Existing record constructors stay as conveniences with the new components at their empty values" (spec 8.2): `NONE`, `""` and `STANDARD`. SDK 0.18.0 is already the version (3a); every new member says `@since 0.18.0` or "(since 0.18.0)" and `JasperSdk.VERSION` does not change.
- Toolbar view admission and lifetime are spec 10.3's: the root is an INLINE `Stack` of LABEL `Text`, `Choice` (DROPDOWN, SEARCHABLE or PICKER), at most one PRIMARY `Button`, and `MenuButton`s (spec 10.3's `Separator` is admitted once plan 3b's `Nodes.Separator` exists: Execution notes, R1); the factory runs once per window when the window's open event fires, never for the rehearsal; the view closes with its window or its registration; it never goes into the overflow menu and hides while the toolbar is too narrow for it; a factory that throws is contained (spec 15) and that window shows no view.
- SDK types appear in the app only inside `dev.jasper.app.plugins` (AGENTS.md); `dev.jasper.app.palette`, `contributions`, `workspace`, `windows` and `platform` use jasper-ui model values (`dev.jasper.ui.model.Nodes.Hue`). The SDK's new `palette` → `ui` edge must keep the SDK package graph acyclic (`verifySdkArchitecture`); `ui` never imports `palette`.
- Javadoc doclint runs in `check`: the SDK and jasper-app with `-Xdoclint:all`, jasper-ui with `-Xdoclint:all,-missing`; every `{@link}` must resolve and no comment may contain a raw `<`, `>`, `&` or `->`.
- Every new candidate source under jasper-app, the SDK, the testkit or plugins gets its `docs/design/theme-engine/migration.csv` row in the same commit (`ThemeMigrationCoverageTest`); a CSV row has exactly eight comma-separated fields, so the disposition text uses semicolons, never commas. Edits to existing files use fully qualified names (`dev.jasper.sdk.ui.Nodes.Hue.GREEN`) instead of new imports, so no existing file becomes a new candidate.
- The compiled documentation example `pluginpalette` (`plugins/sample/.../SamplePlugin.java`) and its copy in `docs/plugin-authoring.md` change together.
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file.

## Review Focus

1. A plugin updates its toolbar view to a tree the toolbar does not admit (a second PRIMARY button, a VERTICAL root, a
   SECONDARY button): `update` throws `IllegalArgumentException` naming the node and the toolbar keeps the old
   controls. Task 5: `PluginContractTest.aToolbarViewHoldsOnlyAnInlineStackOfAdmittedControls` (both harnesses).
2. A window that never opens (the warm-up rehearsal) builds no view, and a factory that failed is not asked again on
   every re-render of the toolbar. Task 4: `WindowToolbarViewsTest.aWindowBuildsItsViewOnlyOnceItOpensAndOnlyOnce` and
   `aFactoryThatFailedIsNotAskedAgainForThatWindow`.
3. The user narrows the window: the view hides without entering the overflow menu or summoning an empty overflow
   button, a lower-priority button overflows first, and widening shows the view again. Task 4:
   `WindowToolbarTest.aViewHidesWhileTheRowIsTooNarrowAndNeverEntersTheOverflowMenu` and
   `aLowerPriorityButtonOverflowsBeforeAViewHides`.
4. The plugin closes its registration, stops, or the user closes the window while views are showing: each view closes
   exactly once and its controls leave the toolbar, also when the factory itself changes the contributions (registers
   or enables an action), which re-renders the toolbar from inside the pass that asked it: `renderContributedToolbar`
   defers that nested pass, as `renderContributedMenus` does, and reserves the window's slot before the factory runs.
   Task 4: `WindowToolbarViewsTest.aViewClosesWhenItsEntryGoesAndWhenItsWindowCloses` and
   `aFactoryThatChangesTheContributionsIsAskedOnceAndItsViewClosesOnce`;
   Task 5: `PluginContractTest.aToolbarViewIsBuiltOncePerWindowAndClosesWithItsWindowAndItsRegistration`.
5. A scope whose split verb comes first, or that has two, or a noun such as "Session" or " session": refused when the
   `ScopeSpec` is made, and the app registry refuses the same verbs; a scope with no hue or noun reaches the app with
   its plugin's `icon_hue` and its label. Task 2: `PaletteValuesTest.aScopeCarriesAHueAndAnItemNounAndAtMostOneSplitVariantAfterItsFirstVerb`,
   `PaletteScopeModelTest.theRegistryRefusesASplitVerbFirstOrTwiceAndScopesDefaultToNeutralAndTheirLabel`,
   `HostedPaletteTest.aScopeThatNamesNoHueTakesItsPluginsIconHueAndRolesAndNounsCarryOver`.

## Verified facts

At `deafed424` (`main` after plan 3a), re-verified on 2026-10-16.

1. `StandardMenu` is FILE, EDIT, VIEW, PANE, TAB, TOOLS, TOOLS documented as shown "only while a plugin has entries in
   it, after Tab and before plugins' own menus" (`jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java:5-21`);
   3a changed only its class comment, now lines 3-4. The app maps it by name: `MenuTarget.Slot` is `{ FILE, EDIT, VIEW, PANE,
   TAB, TOOLS }` (`jasper-app/.../contributions/MenuTarget.java:17`) and `HostedUi.menus().standard` calls
   `MenuTarget.Slot.valueOf(menu.name())` (`HostedUi.java:241-245`); the fake keys a section by `menu.name()`
   (`FakeUi.java:214`).
2. `WindowChrome.rebuildContributedMenus` builds Tools fresh on every pass, adds a separator only before a section that
   follows items, and adds Tools to the bar after Tab only while it has entries, then plugins' own menus
   (`WindowChrome.java:246-283`).
3. `ToolbarItem` permits `Button` and `Dropdown` (`ToolbarItem.java:7`); `HostedUi.toolbar` translates both into
   `ToolbarEntry` and tracks the model registration (`HostedUi.java:224-238`); the fake validates and keeps the items
   (`FakeUi.java:200-210`) and prints them in `FakePluginHost.toolbar()` (`FakePluginHost.java:529-548`); the app
   harness prints `contributions.toolbar()` (`AppContractTest.java:229-245`).
4. `ToolbarEntry` permits `Button` and `Dropdown` (`contributions/ToolbarEntry.java:7`). `Contributions.addToolbar`
   notifies listeners synchronously and removes by identity (`Contributions.java:90-99`); a window re-renders its
   toolbar on every `TOOLBAR` change (`WindowContributions.java:383`).
5. `WindowChrome.renderContributedToolbar` removes and re-adds every contributed control after `pluginSeparator`
   (`WindowChrome.java:164-204`); `WindowToolbar.doLayout` places only `JButton`s and `JSeparator`s, moving
   lower-priority buttons with a menu supplier into the overflow menu (`WindowToolbar.java:125-175`), and
   `minimumWidth` counts only buttons without one (`:91-98`). The button side is the bar height minus
   `TOOLBAR_BUTTON_TRIM` (`:48-51`): 30 − 6 = 24, the kit's `BUTTON_HEIGHT` and `FIELD_HEIGHT`
   (`jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv:35-36,372`).
6. A window's open event is `TerminalEvent.WindowOpened`, published when `WindowContent.connectTerminals` creates its
   `WindowTerminals` (`WindowContent.java:750-754`, `WindowTerminals.java:31`, `TerminalRegistry.java:31-36`).
   `composeWindow` (`JasperApplication.java:396`) connects contributions (`:415`) before terminals (`:422`) and shows the
   window at `:446`; the rehearsal window connects contributions (`:820`) but never terminals (`JasperApplication.java:808-829`).
   No pre-built window exists on `main` (`git grep -i prebuilt` finds none).
7. A window lets go of its contributions with `owner.chrome().connect(null)` at the end of
   `WindowContributions.close` (`WindowContributions.java:460`), which `WindowContent.close` calls (`:1407`).
8. Plugins build views only with `UiKit.create` (`UiKit.java:10`); `HostedKit.requireOwned` rejects a foreign view
   (`HostedKit.java:66`); `HostedKitView.mount` refuses a second mount target (`HostedKitView.java:21`), and `update`
   applies `hostSpec` before the view changes, keeping the old tree when it throws (`:25`, `:34`); `close` is
   idempotent (`:56`). The fake has the same `mount`, `hostSpec`, `close` and `emit` (`FakeKitView.java:22`, `:26`,
   `:51`, `:91`, `:100`). `Containment.run` contains a failure against the plugin (`Containment.java:54`).
9. `KitView.titleStrip` is an app-only flag (`KitView.java:319-330`) that `Menus` reads to draw an icon-only menu's
   indicator on the borderless control surface (`jasper-ui/.../swing/Menus.java:234-235`); `AuxiliarySurface` sets it
   on kit title actions (`AuxiliarySurface.java:61`). A `KitView` is not opaque and paints no surface unless asked
   (`KitView.java:47`, `:331-332`). Kit buttons emit `Action(node.action())` on click (`Buttons.java:58-62`), and kit
   components are named by node id (`WorkspaceContentRouteTest.named`, `WorkspaceContentRouteTest.java:26-31`).
10. The SDK `ui` package already depends on `terminal` (`ActionContext.java:3-4`, `ui/package-info.java:4`), so
    `ToolbarViewFactory` may take a `WindowHandle`; `palette` depends on `sdk` and `terminal` only
    (`palette/package-info.java:5`) and nothing in `ui` imports `palette`, so `palette` → `ui` stays acyclic;
    `verifySdkArchitecture` checks only that and JDK-only types (`gradle/sdk-architecture.gradle.kts:39-55`).
11. `ScopeSpec` is a 10-component record with a 9-argument 0.9.1 constructor, `of` and six derivers
    (`ScopeSpec.java:28-127`); after 3a its `monospaceRows` docs read "whether rows are data values". SDK `PaletteVerb`
    is `(id, label)` (`PaletteVerb.java:10-16`); app `PaletteVerb` is `(id, label)` (`app/palette/PaletteVerb.java:4-9`);
    app `PaletteScope` has defaults for icon, description, monospace rows, All and recents (`PaletteScope.java:14-60`);
    `ScopeRegistry.register` validates ids and a non-empty verb list (`ScopeRegistry.java:18-31`).
12. `HostedPalette.Adapted` maps a plugin scope onto the app's (`HostedPalette.java:87-106`); `HostedPalette` is built
    only in `HostedContext.java:93`, from a `HostedPlugin` (`HostedPlugin.java:13-20`), which `PluginRuntime` builds
    from the candidate's descriptor (`PluginRuntime.java:685-686`); the descriptor carries `iconHue`
    (`PluginDescriptor.java:8-11`), NEUTRAL when the manifest has no `icon_hue` and never NONE
    (`DescriptorParser.java:86-99`). Bundled `icon_hue`s: remote green, vault blue, fileserver blue, sample purple,
    snippets purple, editor, history and indexer neutral (`plugins/*/src/main/resources/plugin.toml:8`).
13. `CommandsScope` names its id, label, icon, description, placeholder and one verb (`CommandsScope.java:43-48`).
14. `DialogSpec` is `(title, owner, modal, presentation)` with a 3-argument convenience (`DialogSpec.java:15-41`); after
    3a its `Presentation` comment ends "a hint the presentation may place another way". `HostedUi.dialog` builds an
    `AuxiliarySurface` and sets `attachedSheet` from the presentation (`HostedUi.java:720-750`, the line at `:742`);
    `NativeShells.installTitleBar` (`NativeShells.java:331`) gives a dialog `MacTitleBar.Style.DIALOG` (`:346`) and its
    title (`:350`);
    `MacTitleBar` shows its title label only for WINDOW and DIALOG (`MacTitleBar.java:172`) and colours it through
    `ThemeZone.color` (`:298-300`). The modal overlay draws no title (`ModalOverlay.java:28-37`); an attached sheet is
    undecorated (`NativeShells.java:212-218`); off macOS there is no `MacTitleBar` (`MacTitleBar.java:69`, `:72-74`).
    The fake keeps a dialog's presentation (`FakeUi.java:276`, `:565-566`) and answers `dialogPresentation`
    (`FakePluginHost.java:1069-1074`).
15. `ContractHarness` (`jasper-sdk-testkit/src/testFixtures/.../contract/ContractHarness.java`) is implemented only by
    `FakeContractTest` and `AppContractTest`; `PluginContractTest` runs under both. The app harness keeps its own panel
    sites (`AppContractTest.java:274-306`) and finds app scopes with a private `scope(id)` (`:394-397`).
16. The fake closes a window when its last pane closes (`FakeWorkspace.java:126-132`).
17. Bundled scopes: Remote (`RemoteScope.java:30-32`, `:59-62`), Tunnels (`TunnelsScope.java:49-51`), Snippets
    (`SnippetsScope.java:37-40`), Indexer Files (`FilesScope.java:87-90`), Editor Recent files
    (`RecentFilesScope.java:69-73`), History (`HistoryScope.java:44-50`), Sample (`SamplePlugin.java:218-222`, inside the
    `pluginpalette` example copied at `docs/plugin-authoring.md:427-466`), Vault (`VaultScope.java:52-55`), File Server
    (`FileServerScope.java:41-44`). Each plugin's tests already print its scopes with `host.scopes()`.
18. `ThemeMigrationCoverageTest` makes a file a candidate when it imports Swing, AWT, FlatLaf, `dev.jasper.sdk.ui` or
    `dev.jasper.ui`, or names `new J…` or `new …View` and the like (`ThemeMigrationCoverageTest.java:17-18`), under
    jasper-app, the SDK, the testkit and plugins (`:61-66`); jasper-ui is not scanned.
19. `KitFixtures.context()` and `render(view, w, h, scale)` give jasper-ui tests a headless kit
    (`jasper-ui/src/testFixtures/.../KitFixtures.java:196-233`); `NativeShellsChromeTest` builds a title bar headlessly
    with `NativeShells.installTitleBar(root, surface, true)` (`NativeShellsChromeTest.java:33-52`).

## Rulings (where the spec is silent) and one deviation

Record each in `docs/STATUS.md` (Task 7) and in this plan's status banner when it is executed. Ruling 1 is not
gap-filling: it departs from binding spec text (10.3's first bullet, "refused at registration with a message naming
the node", and section 15's row "A toolbar view's root breaks admission": "`IllegalArgumentException` at registration
naming the node"). It needs the user's approval, or a spec amendment line, before this plan merges; the status banner
and the STATUS entry record it as a deviation awaiting that approval.

1. **Deviation (spec 10.3, 15; awaiting the user's approval): admission runs when the view is mounted, and on every
   update, not at registration.** `Toolbar.add` receives only a factory, so the tree exists only once a window asks for
   it: the host checks it when it mounts the factory's view (the refusal is contained like a throwing factory, logged
   against the plugin, the refused view closed and that window shows no view, so the plugin never receives the
   exception spec 15 names) and on every later `update` (an `IllegalArgumentException` naming the node to the caller;
   the view keeps its tree). The message names the node by kind and id. If the user rejects it, the spec's reading
   needs a factory call at `Toolbar.add`, which spec 10.3's second bullet ("never for the rehearsal") rules out for a
   real window.
2. **"At most one PRIMARY Button" admits PRIMARY buttons only.** Buttons in a toolbar view must be PRIMARY, at most one;
   a SECONDARY, GHOST or LINK button is refused.
3. **The window's open event is `WindowContent.connectTerminals`.** `WindowChrome.windowOpened()` runs from there, and a
   window builds its views only after it; a factory that failed is remembered and not asked again for that window.
4. **A view gives way at priority 40**: after contributed buttons (30), before contributed dropdowns (80) and the
   built-in New Tab, Split, Find and Settings (90 and 100).
5. **`KitView.toolbarStrip` in the default look** presents icon-only menus as a title strip does, and keeps the kit's
   control height, which equals the default toolbar's button side (24 px); the toolbar centres the view at its
   preferred height. A classic toolbar density, if one is needed, is plan 4's or 6's.
6. **Deviation (spec 8.2; awaiting the user's approval): the default look draws a swatch only in `MacTitleBar`'s window
   or dialog title**, the one dialog title it draws
   itself; overlays draw no title, attached sheets have no title bar, and Windows and Linux dialogs carry the system's
   or FlatLaf's title bar, which count as native title bars ("a native title bar draws none").
7. **`itemNoun` validation:** null or blank becomes `""`; any other value must equal its own `strip()` and its
   `toLowerCase(Locale.ROOT)`, have at most 40 characters and no control characters ("file server" is valid).
8. **The app mirrors carry effective values.** `PaletteScope.hue()` is never NONE: a plugin scope's NONE becomes its
   manifest `icon_hue` (carried by a new `HostedPlugin.iconHue` component; NEUTRAL without one), and app scopes default
   to NEUTRAL; `itemNoun()` is the label when the scope names none. Capitalising the noun for a detail label is the
   drawer's (plan 8). The fake, which reads no manifest, reports NONE as NEUTRAL.
9. **`StandardMenu` appends `SESSION` and `TRANSFER` after `TOOLS`** (the existing ordinals stay); the bar orders the
   menus Tab, Session, Transfer, Tools, plugins' menus.
10. **The fake builds toolbar views lazily**, the first time `FakePluginHost.toolbarViews(windowId)` is asked for a
    window, as a window builds them when it opens, and closes them when the fake window closes. New testkit API:
    `toolbarViews`, `closeToolbarViews`, `scopePresentation`, `dialogSwatch`. The contract harness models the window
    the same way the app harness does for panels; "none for the rehearsal" is checked on the real window chrome
    (`WindowToolbarViewsTest`), which the harness cannot model.
11. **No KitValues, ViewSpec or `ListItem` change.** Spec 8.2's mirroring list applies to nodes; none of this plan's
    additions is a node.
12. **`ToolbarViewAdmission` exists twice**, in `dev.jasper.app.plugins` and `dev.jasper.sdk.testing`, following the
    `KitIconNames` precedent; the shared contracts keep the two copies in agreement.

---

### Task 1: The Session and Transfer menus

`StandardMenu` gains `SESSION` and `TRANSFER`, built-in menus with no items of their own that show only while a plugin
has entries in them, after Tab and before Tools, exactly as Tools does today (spec 8.2, 10.2). The host maps them by
name; the fake already keys sections by name.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java:16-20`
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/MenuTarget.java:17`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java:253-272`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java:48-53` (doc), `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java:550-555` (doc)
- Test: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java` (one test, before `statusItemsAreOrderedGlobalAndInertAfterClose`), `jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeContributionsTest.java` (one test, before `titles`)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `StandardMenu.SESSION`, `StandardMenu.TRANSFER`; `MenuTarget.Slot.SESSION`, `MenuTarget.Slot.TRANSFER`;
  harness menu targets `"SESSION"` and `"TRANSFER"`.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3d Task 1 step 1: the Session and Transfer menus' tests."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java", [(
r'''    @Test void statusItemsAreOrderedGlobalAndInertAfterClose() {
''',
r'''    /** SDK 0.18: Session and Transfer are built-in menus with no items of their own, taking sections as Tools does. */
    @Test void theSessionAndTransferMenusTakeSectionsLikeTools() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.connect", "Connect"), invoked -> { });
            context.actions().register(ActionSpec.of("test.alpha.upload", "Upload"), invoked -> { });
            context.menus().standard(StandardMenu.SESSION).add("test.alpha.connect");
            context.menus().standard(StandardMenu.TRANSFER).add("test.alpha.upload");
        });
        h.start(info("test.beta"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.beta.connect", "Connect"), invoked -> { });
            context.menus().standard(StandardMenu.SESSION).add("test.beta.connect");
        });
        assertThat(h.menu("SESSION")).containsExactly("item:test.alpha.connect", "===", "item:test.beta.connect");
        assertThat(h.menu("TRANSFER")).containsExactly("item:test.alpha.upload");
        assertThat(h.menu("TOOLS")).isEmpty();
    }

    @Test void statusItemsAreOrderedGlobalAndInertAfterClose() {
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeContributionsTest.java", [(
r'''    private static List<String> titles(WindowContent owner) {
''',
r'''    /** Plan 3d: Session and Transfer show after Tab and before Tools, each only while a plugin has entries in it. */
    @Test void sessionAndTransferAppearAfterTabBeforeToolsOnlyWhileAPluginHasEntriesInThem() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model);
            model.addAction("dev.x.connect", "Connect", null, List.of(), Optional.empty(), invocation -> { });
            model.addAction("dev.x.upload", "Upload", null, List.of(), Optional.empty(), invocation -> { });
            model.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Own")).set(List.of(new MenuEntry.Item("dev.x.connect")));
            model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TOOLS)).set(List.of(new MenuEntry.Item("dev.x.upload")));
            assertThat(titles(owner)).as("no entries, no menus").doesNotContain("Session", "Transfer");
            MenuSection session = model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.SESSION));
            session.set(List.of(new MenuEntry.Item("dev.x.connect")));
            MenuSection transfer = model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TRANSFER));
            transfer.set(List.of(new MenuEntry.Item("dev.x.upload")));
            assertThat(titles(owner)).containsSubsequence("Tab", "Session", "Transfer", "Tools", "Own");
            assertThat(items(menu(owner, "Session"))).as("no separator before the first section").containsExactly("Connect");
            assertThat(items(menu(owner, "Transfer"))).containsExactly("Upload");
            session.close();
            assertThat(titles(owner)).doesNotContain("Session").containsSubsequence("Tab", "Transfer", "Tools");
            transfer.close();
            assertThat(titles(owner)).doesNotContain("Transfer");
        });
    }

    private static List<String> titles(WindowContent owner) {
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests dev.jasper.sdk.testing.FakeContractTest :jasper-app:test --tests dev.jasper.app.workspace.WindowChromeContributionsTest --continue`
Expected: FAIL. `:jasper-sdk-testkit:compileTestFixturesJava` stops with "cannot find symbol … SESSION" in
`PluginContractTest`. `:jasper-app:compileTestJava` depends on those test fixtures (`AppContractTest` extends
`PluginContractTest`), so Gradle does not run it, even with `--continue`, and reports it as not run because its
dependency failed; `WindowChromeContributionsTest` first compiles in Step 4.

- [ ] **Step 3: Add the menus**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3d Task 1 step 3: StandardMenu.SESSION and TRANSFER, their app slots and the menu bar."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java", [(
r'''    /**
     * The Tools menu, for utility commands. Jasper has none of its own, so it shows only while a plugin has entries in
     * it, after Tab and before plugins' own menus. @since 0.16.1
     */
    TOOLS
}''',
r'''    /**
     * The Tools menu, for utility commands. Jasper has none of its own, so it shows only while a plugin has entries in
     * it, after Tab, Session and Transfer and before plugins' own menus. @since 0.16.1
     */
    TOOLS,
    /**
     * The Session menu, for connecting to hosts and working with sessions. Jasper has none of its own, so it shows only
     * while a plugin has entries in it, after Tab. @since 0.18.0
     */
    SESSION,
    /**
     * The Transfer menu, for moving files: file browsers, transfers and file servers. Jasper has none of its own, so it
     * shows only while a plugin has entries in it, after Session. @since 0.18.0
     */
    TRANSFER
}''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/contributions/MenuTarget.java", [(
    "    public enum Slot { FILE, EDIT, VIEW, PANE, TAB, TOOLS }",
    "    public enum Slot { FILE, EDIT, VIEW, PANE, TAB, TOOLS, SESSION, TRANSFER }", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java", [
(r'''            // Tools holds only plugin entries and, like a plugin's own menu, is built fresh on every pass and added
            // to the bar only while it has any: a stale one would keep an old look and feel, and one JMenu would be
            // re-inserted into the macOS screen menu bar.
            standardMenus.put(MenuTarget.Slot.TOOLS, menu("Tools"));''',
r'''            // Session, Transfer and Tools hold only plugin entries and, like a plugin's own menu, are built fresh on
            // every pass and added to the bar only while they have any: a stale one would keep an old look and feel,
            // and one JMenu would be re-inserted into the macOS screen menu bar.
            standardMenus.put(MenuTarget.Slot.SESSION, menu("Session"));
            standardMenus.put(MenuTarget.Slot.TRANSFER, menu("Transfer"));
            standardMenus.put(MenuTarget.Slot.TOOLS, menu("Tools"));''', 1),
(r'''                // Tools has no items of its own, so its first section needs no separator.''',
 r'''                // Session, Transfer and Tools have no items of their own, so a first section there needs no separator.''', 1),
(r'''            // The bar orders menus as they are added: Tools after Tab, then plugins' own menus.
            JMenu tools = standardMenus.get(MenuTarget.Slot.TOOLS);
            if (tools.getMenuComponentCount() > 0) { menuBar.add(tools); contributedMenus.add(tools); }''',
r'''            // The bar orders menus as they are added: Session, Transfer and Tools after Tab, then plugins' own menus.
            for (MenuTarget.Slot slot : List.of(MenuTarget.Slot.SESSION, MenuTarget.Slot.TRANSFER, MenuTarget.Slot.TOOLS)) {
                JMenu built = standardMenus.get(slot);
                if (built.getMenuComponentCount() > 0) { menuBar.add(built); contributedMenus.add(built); }
            }''', 1)])
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java", [(
r'''     * One menu target ({@code FILE}, {@code EDIT}, {@code VIEW}, {@code PANE}, {@code TAB}, {@code context},''',
r'''     * One menu target ({@code FILE}, {@code EDIT}, {@code VIEW}, {@code PANE}, {@code TAB}, {@code TOOLS},
     * {@code SESSION}, {@code TRANSFER}, {@code context},''', 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java", [(
r'''     * @param target {@code FILE}, {@code EDIT}, {@code VIEW}, {@code PANE}, {@code TAB}, {@code context} or {@code top:<menuId>}''',
r'''     * @param target {@code FILE}, {@code EDIT}, {@code VIEW}, {@code PANE}, {@code TAB}, {@code TOOLS}, {@code SESSION},
     *     {@code TRANSFER}, {@code context} or {@code top:<menuId>}''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:javadoc :jasper-sdk-testkit:test --tests dev.jasper.sdk.testing.FakeContractTest :jasper-app:test --tests dev.jasper.app.workspace.WindowChromeContributionsTest --tests dev.jasper.app.plugins.AppContractTest --tests dev.jasper.app.plugins.HostedUiTest --continue`
Expected: BUILD SUCCESSFUL. `theSessionAndTransferMenusTakeSectionsLikeTools` passes under both harnesses; every
existing `WindowChromeContributionsTest` case, including the exact bar `File, Edit, View, Pane, Tab, Tools, SSH, Help`
of `menuRefreshDuringNativeMenuMutationDoesNotDuplicateTopLevelMenus`, still passes.

- [ ] **Step 5: Commit**

```bash
git add jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java jasper-app/src/main/java/dev/jasper/app/contributions/MenuTarget.java \
  jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java jasper-sdk-testkit/src \
  jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeContributionsTest.java
git commit -F - <<'EOF'
SDK 0.18: the Session and Transfer menus

StandardMenu gains SESSION and TRANSFER, built-in menus with no items of
their own. Like Tools, each is built fresh on every menu pass and shows
only while a plugin has entries in it; the bar reads Tab, Session,
Transfer, Tools, then plugins' own menus. The shared contract checks both
runtimes.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: Palette hue, item noun and verb role

`ScopeSpec` gains `hue` and `itemNoun`, and `PaletteVerb` gains `role` (STANDARD or SPLIT), validated by the SDK (spec
8.2): NONE and a blank noun are the empty values, a noun is lowercase, and a scope has at most one SPLIT verb, never its
first. The app's `PaletteScope.hue()`, `itemNoun()` and `PaletteVerb.role()` carry the effective values (ruling 8): a
plugin scope with no hue takes its manifest `icon_hue`, carried by a new `HostedPlugin.iconHue`, and one with no noun its
label. The registry refuses the same verb roles for app scopes. Nothing draws them yet.

**Files:**
- Modify (whole file): `jasper-sdk/src/main/java/dev/jasper/sdk/palette/ScopeSpec.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/palette/PaletteVerb.java`, `jasper-app/src/main/java/dev/jasper/app/palette/PaletteVerb.java`, `jasper-app/src/main/java/dev/jasper/app/plugins/HostedPlugin.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/palette/package-info.java:5`, `jasper-app/src/main/java/dev/jasper/app/palette/PaletteScope.java:27`, `jasper-app/src/main/java/dev/jasper/app/palette/ScopeRegistry.java:24`, `jasper-app/src/main/java/dev/jasper/app/plugins/HostedPalette.java:35-40,94,103`, `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java:93`, `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java:685-686`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java` (after `scopes()`), `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java` (after `scopes()`), `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java` (after `scopes()`)
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java` (two tests), `jasper-app/src/test/java/dev/jasper/app/palette/PaletteScopeModelTest.java` (helper and one test), `jasper-app/src/test/java/dev/jasper/app/plugins/HostedPaletteTest.java` (helper and one test), `PluginContractTest.java` (one test and helper)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: SDK `ScopeSpec(…, boolean inRecents, Nodes.Hue hue, String itemNoun)`, `ScopeSpec.withHue(Nodes.Hue)`,
  `withItemNoun(String)`; SDK `PaletteVerb(String id, String label, PaletteVerb.Role role)` and `PaletteVerb.Role
  { STANDARD, SPLIT }`; app `PaletteScope.hue()` (`dev.jasper.ui.model.Nodes.Hue`, never NONE), `itemNoun()`; app
  `PaletteVerb(id, label, PaletteVerb.Role role)` and `PaletteVerb.Role { STANDARD, SPLIT }`;
  `HostedPlugin.iconHue()`; `FakePluginHost.scopePresentation(String scopeId)` and the harness's
  `scopePresentation(String scopeId)`, both answering `HUE|itemNoun|ROLE,ROLE` (Task 6 reads them).

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3d Task 2 step 1: the palette presentation tests."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java", [(
r'''    @Test void rowsResultsAndStepsValidateLikeTheApplicationsOwn() {
''',
r'''    /** SDK 0.18 (spec 8.2): a hue and an item noun for presentations that colour and label rows by scope. */
    @Test void aScopeCarriesAHueAndAnItemNounAndAtMostOneSplitVariantAfterItsFirstVerb() {
        var none = dev.jasper.sdk.ui.Nodes.Hue.NONE;
        var green = dev.jasper.sdk.ui.Nodes.Hue.GREEN;
        var split = new PaletteVerb("split", "Connect in split", PaletteVerb.Role.SPLIT);
        ScopeSpec spec = ScopeSpec.of("dev.x.hosts", "Hosts", "Search hosts", List.of(PASTE, split));
        assertThat(spec.hue()).isEqualTo(none);
        assertThat(spec.itemNoun()).isEmpty();
        var named = spec.withHue(green).withItemNoun("session").withDescription("Saved hosts").withInAll(false).withIcon(null)
            .withMonospaceRows(true).withShortcutActionId("dev.x.open").withInRecents(false);
        assertThat(named.hue()).as("derivations keep the hue").isEqualTo(green);
        assertThat(named.itemNoun()).as("and the noun").isEqualTo("session");
        assertThat(spec.withItemNoun("file server").itemNoun()).isEqualTo("file server");
        assertThat(spec.withItemNoun("  ").itemNoun()).as("blank means the label").isEmpty();
        assertThat(spec.withItemNoun(null).itemNoun()).isEmpty();
        assertThat(new ScopeSpec("dev.x.old", "Old", "", "Search", List.of(PASTE), false, Optional.empty(), Optional.empty(), true, true).hue())
            .as("the 0.9.2 constructor still works").isEqualTo(none);
        assertThat(new ScopeSpec("dev.x.old", "Old", "", "Search", List.of(PASTE), false, Optional.empty(), Optional.empty(), true).itemNoun())
            .as("and the 0.9.1 one").isEmpty();
        assertThatIllegalArgumentException().as("lowercase").isThrownBy(() -> spec.withItemNoun("Session"));
        assertThatIllegalArgumentException().as("no surrounding space").isThrownBy(() -> spec.withItemNoun(" session"));
        assertThatIllegalArgumentException().as("no control characters").isThrownBy(() -> spec.withItemNoun("ses\tsion"));
        assertThatIllegalArgumentException().as("short").isThrownBy(() -> spec.withItemNoun("s".repeat(41)));
        assertThatNullPointerException().isThrownBy(() -> spec.withHue(null));
        assertThatIllegalArgumentException().as("never the first verb")
            .isThrownBy(() -> ScopeSpec.of("dev.x.hosts", "Hosts", "Search", List.of(split, PASTE)));
        assertThatIllegalArgumentException().as("at most one").isThrownBy(() -> ScopeSpec.of("dev.x.hosts", "Hosts", "Search",
            List.of(PASTE, split, new PaletteVerb("split_down", "Split down", PaletteVerb.Role.SPLIT))));
    }

    @Test void aVerbIsStandardUnlessMarkedSplit() {
        assertThat(PASTE.role()).isEqualTo(PaletteVerb.Role.STANDARD);
        assertThat(new PaletteVerb("split", "Split", PaletteVerb.Role.SPLIT).role()).isEqualTo(PaletteVerb.Role.SPLIT);
        assertThat(new PaletteVerb("paste", "Paste")).isEqualTo(new PaletteVerb("paste", "Paste", PaletteVerb.Role.STANDARD));
        assertThatNullPointerException().isThrownBy(() -> new PaletteVerb("split", "Split", null));
    }

    @Test void rowsResultsAndStepsValidateLikeTheApplicationsOwn() {
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/palette/PaletteScopeModelTest.java", [
(r'''    static PaletteScope scope(String id) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return id; }
            @Override public String placeholder() { return "Search " + id; }
            @Override public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("run", "Run")); }''',
 r'''    static PaletteScope scope(String id) { return scope(id, List.of(new PaletteVerb("run", "Run"))); }

    static PaletteScope scope(String id, List<PaletteVerb> verbs) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return id; }
            @Override public String placeholder() { return "Search " + id; }
            @Override public List<PaletteVerb> verbs() { return verbs; }''', 1),
(r'''    @Test void targetOfNothingIsInertAndNotLive() {
''',
 r'''    /** Plan 3d: the registry keeps a split verb after the first and alone; an app scope is NEUTRAL and named by its label. */
    @Test void theRegistryRefusesASplitVerbFirstOrTwiceAndScopesDefaultToNeutralAndTheirLabel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new ScopeRegistry()) {
                var plain = scope("a.plain");
                assertThat(plain.hue()).isEqualTo(dev.jasper.ui.model.Nodes.Hue.NEUTRAL);
                assertThat(plain.itemNoun()).isEqualTo("a.plain");
                var run = new PaletteVerb("run", "Run");
                var split = new PaletteVerb("split", "Split", PaletteVerb.Role.SPLIT);
                assertThat(run.role()).isEqualTo(PaletteVerb.Role.STANDARD);
                assertThatIllegalArgumentException().isThrownBy(() -> registry.register(scope("a.first", List.of(split, run))));
                assertThatIllegalArgumentException().isThrownBy(() -> registry.register(scope("a.twice",
                    List.of(run, split, new PaletteVerb("down", "Split down", PaletteVerb.Role.SPLIT)))));
                registry.register(scope("a.ok", List.of(run, split))).close();
            }
        });
    }

    @Test void targetOfNothingIsInertAndNotLive() {
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/HostedPaletteTest.java", [(
r'''    @Test void aContributedScopeIsAdaptedContainedAndHandsThePluginItsOwnRowsBack() throws Exception {
''',
r'''    static PaletteScope presented(ScopeSpec spec) {
        return new PaletteScope() {
            @Override public ScopeSpec spec() { return spec; }
            @Override public PaletteResults search(String query, PaletteQuery context) { return PaletteResults.none(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) { }
            @Override public dev.jasper.sdk.Subscription onChanged(Runnable listener) { return () -> { }; }
        };
    }

    /** Plan 3d: the app's scope has the plugin's hue or its manifest icon_hue, its noun or its label, and its verbs' roles. */
    @Test void aScopeThatNamesNoHueTakesItsPluginsIconHueAndRolesAndNounsCarryOver() throws Exception {
        PluginHost host = host();
        var split = new PaletteVerb("split", "Open in a split pane", PaletteVerb.Role.SPLIT);
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo("test.a", "A", "1.0.0", Set.of(Capabilities.PALETTE_CONTRIBUTE)), Set.of(), Set.of(),
            Set.of(getClass().getPackageName()), getClass().getClassLoader(), () -> c -> {
                c.palette().register(presented(ScopeSpec.of("test.a.plain", "Plain", "Search", List.of(PASTE, split))));
                c.palette().register(presented(ScopeSpec.of("test.a.hosts", "Hosts", "Search", List.of(PASTE))
                    .withHue(dev.jasper.sdk.ui.Nodes.Hue.GREEN).withItemNoun("session")));
            }, dev.jasper.ui.model.Nodes.Hue.AMBER)));
        onEdt(() -> {
            assertThat(contributions.scopes()).extracting(dev.jasper.app.palette.PaletteScope::id).containsExactly("test.a.plain", "test.a.hosts");
            var plain = contributions.scopes().get(0);
            var hosts = contributions.scopes().get(1);
            assertThat(plain.hue()).as("no hue of its own: the manifest's icon_hue").isEqualTo(dev.jasper.ui.model.Nodes.Hue.AMBER);
            assertThat(plain.itemNoun()).as("no noun: the label").isEqualTo("Plain");
            assertThat(plain.verbs()).extracting(dev.jasper.app.palette.PaletteVerb::role)
                .containsExactly(dev.jasper.app.palette.PaletteVerb.Role.STANDARD, dev.jasper.app.palette.PaletteVerb.Role.SPLIT);
            assertThat(hosts.hue()).isEqualTo(dev.jasper.ui.model.Nodes.Hue.GREEN);
            assertThat(hosts.itemNoun()).isEqualTo("session");
        });
    }

    @Test void aContributedScopeIsAdaptedContainedAndHandsThePluginItsOwnRowsBack() throws Exception {
''', 1)])
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java", [(
r'''        @Test void aScopeGetsItsOwnRowsAndHandlesBackAndAStepCanReopenAnotherScope() {
''',
r'''        /** SDK 0.18: a scope's hue, item noun and verb roles reach the host as the presentation will read them. */
        @Test void aScopeCarriesItsHueItemNounAndSplitVerbToTheHost() {
            var split = new PaletteVerb("split", "Open in a split pane", PaletteVerb.Role.SPLIT);
            h.start(info("test.a", Capabilities.PALETTE_CONTRIBUTE), Set.of(), Set.of(), context -> {
                context.palette().register(presented(ScopeSpec.of("test.a.hosts", "Hosts", "Search hosts", List.of(PASTE, split))
                    .withHue(dev.jasper.sdk.ui.Nodes.Hue.GREEN).withItemNoun("session")));
                context.palette().register(presented(ScopeSpec.of("test.a.plain", "Plain", "Search", List.of(PASTE))));
            });
            assertThat(h.scopes()).containsExactly("test.a.hosts|Hosts|paste,split", "test.a.plain|Plain|paste");
            assertThat(h.scopePresentation("test.a.hosts")).isEqualTo("GREEN|session|STANDARD,SPLIT");
            assertThat(h.scopePresentation("test.a.plain")).as("NONE takes the plugin's icon hue, NEUTRAL without one; no noun is the label")
                .isEqualTo("NEUTRAL|Plain|STANDARD");
        }

        private static PaletteScope presented(ScopeSpec spec) {
            return new PaletteScope() {
                @Override public ScopeSpec spec() { return spec; }
                @Override public PaletteResults search(String query, PaletteQuery context) { return PaletteResults.none(); }
                @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) { }
                @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
            };
        }

        @Test void aScopeGetsItsOwnRowsAndHandlesBackAndAStepCanReopenAnotherScope() {
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.palette.PaletteValuesTest :jasper-sdk-testkit:test --tests dev.jasper.sdk.testing.FakeContractTest --continue`
Expected: FAIL. `:jasper-sdk:compileTestJava` stops with "cannot find symbol … Role" (and `hue()`, `withItemNoun`) in
`PaletteValuesTest`; `:jasper-sdk-testkit:compileTestFixturesJava` stops on the same symbols and on
`scopePresentation` in `PluginContractTest`.

- [ ] **Step 3: The SDK records**

Check first: `grep -c 'drawn in the data face' jasper-sdk/src/main/java/dev/jasper/sdk/palette/ScopeSpec.java` prints 1
(3a's wording) and `grep -c itemNoun jasper-sdk/src/main/java/dev/jasper/sdk/palette/ScopeSpec.java` prints 0. Then
replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/palette/ScopeSpec.java` with:

```java
package dev.jasper.sdk.palette;

import dev.jasper.sdk.ui.Nodes;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a scope is: identity, wording, verbs and how it is reached. The palette shows an All tab first,
 * then one tab per scope.
 *
 * @param id               namespaced like an action id and starting with the plugin's id, such as
 *                         {@code dev.jasper.history.scope}; the host rejects any other prefix
 * @param label            the tab text, such as "History"
 * @param description      one line, shown as the tab's tooltip
 * @param placeholder      the query field's hint while this scope's tab is open
 * @param verbs            one to three verbs with distinct ids, bound to Enter, Cmd/Ctrl+Enter, Shift+Enter; at most
 *                         one is a {@link PaletteVerb.Role#SPLIT} verb, and never the first
 * @param monospaceRows    whether rows are data values (commands, paths), drawn in the data face
 * @param icon             the tab icon
 * @param shortcutActionId one of the plugin's own actions: while the palette is open, that action's
 *                         shortcut switches to or dismisses this scope's tab instead of running the handler
 * @param inAll            whether the All tab searches this scope too (since 0.8.0)
 * @param inRecents        whether the palette may offer this scope's rows before anything is typed, among the
 *                         recently and most used items (since 0.9.2)
 * @param hue              the hue of this scope's row squares where a presentation draws them; NONE takes the plugin's
 *                         manifest {@code icon_hue}, and NEUTRAL without one (since 0.18.0)
 * @param itemNoun         what one row is, as a lowercase singular noun such as "session", which a presentation shows
 *                         capitalised before a row's detail; blank uses the label (since 0.18.0)
 */
public record ScopeSpec(String id, String label, String description, String placeholder, List<PaletteVerb> verbs,
                        boolean monospaceRows, Optional<String> icon, Optional<String> shortcutActionId, boolean inAll,
                        boolean inRecents, Nodes.Hue hue, String itemNoun) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");
    /** The most characters an item noun has. */
    private static final int NOUN_LENGTH = 40;

    /** Validates every part and copies the verbs. */
    public ScopeSpec {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches()) throw new IllegalArgumentException("Not a namespaced scope id: " + id);
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A scope needs a label");
        description = description == null ? "" : description;
        if (placeholder == null || placeholder.isBlank()) throw new IllegalArgumentException("A scope needs a placeholder");
        verbs = List.copyOf(Objects.requireNonNull(verbs, "verbs"));
        if (verbs.isEmpty() || verbs.size() > 3) throw new IllegalArgumentException("A scope has one to three verbs");
        Set<String> ids = new HashSet<>();
        for (PaletteVerb verb : verbs) if (!ids.add(verb.id())) throw new IllegalArgumentException("Duplicate verb id: " + verb.id());
        if (verbs.getFirst().role() == PaletteVerb.Role.SPLIT)
            throw new IllegalArgumentException("A scope's first verb cannot be its split variant: " + verbs.getFirst().id());
        if (verbs.stream().filter(verb -> verb.role() == PaletteVerb.Role.SPLIT).count() > 1)
            throw new IllegalArgumentException("A scope has at most one split verb");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(shortcutActionId, "shortcutActionId");
        Objects.requireNonNull(hue, "hue");
        itemNoun = itemNoun == null || itemNoun.isBlank() ? "" : itemNoun;
        if (!itemNoun.isEmpty() && (!itemNoun.equals(itemNoun.strip()) || !itemNoun.equals(itemNoun.toLowerCase(Locale.ROOT))
                || itemNoun.length() > NOUN_LENGTH || itemNoun.chars().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("An item noun is a lowercase singular noun, such as \"session\": " + itemNoun);
    }

    /**
     * The 0.9.2 shape: no hue of its own and the label as the item noun.
     *
     * @param id               the scope id
     * @param label            the tab text
     * @param description      the tooltip line
     * @param placeholder      the query hint
     * @param verbs            the verbs
     * @param monospaceRows    whether rows are data values
     * @param icon             the tab icon
     * @param shortcutActionId the plugin's action whose shortcut reaches this scope
     * @param inAll            whether the All tab searches this scope
     * @param inRecents        whether the palette may offer this scope's rows before anything is typed
     */
    public ScopeSpec(String id, String label, String description, String placeholder, List<PaletteVerb> verbs,
                     boolean monospaceRows, Optional<String> icon, Optional<String> shortcutActionId, boolean inAll, boolean inRecents) {
        this(id, label, description, placeholder, verbs, monospaceRows, icon, shortcutActionId, inAll, inRecents, Nodes.Hue.NONE, "");
    }

    /**
     * The 0.9.1 shape, offered among recent items.
     *
     * @param id               the scope id
     * @param label            the tab text
     * @param description      the tooltip line
     * @param placeholder      the query hint
     * @param verbs            the verbs
     * @param monospaceRows    whether rows are data values
     * @param icon             the tab icon
     * @param shortcutActionId the plugin's action whose shortcut reaches this scope
     * @param inAll            whether the All tab searches this scope
     */
    public ScopeSpec(String id, String label, String description, String placeholder, List<PaletteVerb> verbs,
                     boolean monospaceRows, Optional<String> icon, Optional<String> shortcutActionId, boolean inAll) {
        this(id, label, description, placeholder, verbs, monospaceRows, icon, shortcutActionId, inAll, true, Nodes.Hue.NONE, "");
    }

    /**
     * The minimum: no description, icon or shortcut, proportional rows, searched by the All tab, no hue of its own and
     * the label as the item noun.
     *
     * @param id          the scope id
     * @param label       the tab text
     * @param placeholder the query hint
     * @param verbs       the verbs
     * @return the spec
     */
    public static ScopeSpec of(String id, String label, String placeholder, List<PaletteVerb> verbs) {
        return new ScopeSpec(id, label, "", placeholder, verbs, false, Optional.empty(), Optional.empty(), true, true, Nodes.Hue.NONE, "");
    }

    /**
     * Derives a value.
     *
     * @param value the tooltip line
     * @return a copy with that description
     */
    public ScopeSpec withDescription(String value) { return new ScopeSpec(id, label, value, placeholder, verbs, monospaceRows, icon, shortcutActionId, inAll, inRecents, hue, itemNoun); }

    /**
     * Derives a value.
     *
     * @param value whether rows are data values
     * @return a copy with that setting
     */
    public ScopeSpec withMonospaceRows(boolean value) { return new ScopeSpec(id, label, description, placeholder, verbs, value, icon, shortcutActionId, inAll, inRecents, hue, itemNoun); }

    /**
     * Derives a value.
     *
     * @param value the tab icon, or null for none
     * @return a copy with that icon
     */
    public ScopeSpec withIcon(String value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, Optional.ofNullable(value), shortcutActionId, inAll, inRecents, hue, itemNoun); }

    /**
     * Derives a value.
     *
     * @param value the plugin's action whose shortcut reaches this scope, or null for none
     * @return a copy naming it
     */
    public ScopeSpec withShortcutActionId(String value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, icon, Optional.ofNullable(value), inAll, inRecents, hue, itemNoun); }

    /**
     * Derives a value. A scope whose rows should not appear beside others, such as secrets, opts out.
     *
     * @param value whether the All tab searches this scope
     * @return a copy with that setting
     * @since 0.8.0
     */
    public ScopeSpec withInAll(boolean value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, icon, shortcutActionId, value, inRecents, hue, itemNoun); }

    /**
     * Derives a value. A scope whose rows should appear only when searched for, such as shell history, opts out.
     *
     * @param value whether the palette may offer this scope's rows before anything is typed
     * @return a copy with that setting
     * @since 0.9.2
     */
    public ScopeSpec withInRecents(boolean value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, icon, shortcutActionId, inAll, value, hue, itemNoun); }

    /**
     * Derives a value.
     *
     * @param value the hue of this scope's row squares; NONE takes the plugin's {@code icon_hue}
     * @return a copy with that hue
     * @since 0.18.0
     */
    public ScopeSpec withHue(Nodes.Hue value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, icon, shortcutActionId, inAll, inRecents, value, itemNoun); }

    /**
     * Derives a value.
     *
     * @param value what one row is, as a lowercase singular noun such as "session"; blank or null uses the label
     * @return a copy with that noun
     * @since 0.18.0
     */
    public ScopeSpec withItemNoun(String value) { return new ScopeSpec(id, label, description, placeholder, verbs, monospaceRows, icon, shortcutActionId, inAll, inRecents, hue, value); }
}
```

Replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/palette/PaletteVerb.java` with:

```java
package dev.jasper.sdk.palette;

import java.util.Objects;

/**
 * One thing a scope can do with a row. A scope's verbs are bound in order to Enter, Cmd/Ctrl+Enter
 * and Shift+Enter.
 *
 * @param id    lower-case identifier, unique within the scope: {@code [a-z][a-z0-9_.-]{0,127}}
 * @param label what the hint bar shows next to this verb's key, such as "Paste and run"
 * @param role  STANDARD, or SPLIT for the split-pane variant of the scope's first verb, which a presentation may offer
 *              as an option of that verb ("Open in a split pane"); a scope has at most one SPLIT verb, never its first
 *              (since 0.18.0)
 */
public record PaletteVerb(String id, String label, Role role) {
    /**
     * How a verb relates to its scope's first verb.
     *
     * @since 0.18.0
     */
    public enum Role {
        /** A verb of its own. */
        STANDARD,
        /** The split-pane variant of the scope's first verb. */
        SPLIT
    }

    /** Validates every part. */
    public PaletteVerb {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Invalid verb id: " + id);
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A verb needs a label");
        Objects.requireNonNull(role, "role");
    }

    /**
     * A verb of its own.
     *
     * @param id    the verb id
     * @param label the hint bar's text
     */
    public PaletteVerb(String id, String label) { this(id, label, Role.STANDARD); }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3d Task 2 step 3: the palette package's new edge."""
import pathlib
path = pathlib.Path("jasper-sdk/src/main/java/dev/jasper/sdk/palette/package-info.java"); text = path.read_text(encoding="utf-8")
old = " * and the list scrolls through them all. Depends on {@code dev.jasper.sdk} and {@code dev.jasper.sdk.terminal}.\n"
new = (" * and the list scrolls through them all. Depends on {@code dev.jasper.sdk}, {@code dev.jasper.sdk.terminal} and\n"
       " * {@code dev.jasper.sdk.ui} (for {@code Nodes.Hue}).\n")
if text.count(old) != 1: raise SystemExit("package-info anchor not found")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

- [ ] **Step 4: The app mirrors, the translation and the fake**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/palette/PaletteVerb.java` with:

```java
package dev.jasper.app.palette;

import java.util.Objects;

/**
 * One thing a scope can do with a row. The first verb is Enter, the second Cmd/Ctrl+Enter. A SPLIT verb is the
 * split-pane variant of the first; a scope has at most one, never first.
 */
public record PaletteVerb(String id, String label, Role role) {
    /** How a verb relates to its scope's first verb. */
    public enum Role { STANDARD, SPLIT }

    public PaletteVerb {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Invalid verb ID");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Verb needs a label");
        Objects.requireNonNull(role, "role");
    }

    /** A verb of its own. */
    public PaletteVerb(String id, String label) { this(id, label, Role.STANDARD); }
}
```

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/plugins/HostedPlugin.java` with:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * What the host needs to run one plugin, independent of how it was loaded: production passes a
 * {@link PluginClassLoader} and reflective instantiation, tests pass an in-memory plugin.
 * {@code requires} are hard dependencies; services flow from {@code requires} and {@code optional}.
 * {@code iconHue} is the manifest's {@code icon_hue}, never NONE: a palette scope that names no hue takes it.
 */
record HostedPlugin(PluginInfo info, Set<String> requires, Set<String> optional, Set<String> exports,
                    ClassLoader loader, Callable<Plugin> instantiate, dev.jasper.ui.model.Nodes.Hue iconHue) {
    HostedPlugin {
        requires = Set.copyOf(requires);
        optional = Set.copyOf(optional);
        exports = Set.copyOf(exports);
        iconHue = iconHue == null || iconHue == dev.jasper.ui.model.Nodes.Hue.NONE ? dev.jasper.ui.model.Nodes.Hue.NEUTRAL : iconHue;
    }

    /** A plugin whose manifest names no icon hue: NEUTRAL. */
    HostedPlugin(PluginInfo info, Set<String> requires, Set<String> optional, Set<String> exports,
                 ClassLoader loader, Callable<Plugin> instantiate) {
        this(info, requires, optional, exports, loader, instantiate, dev.jasper.ui.model.Nodes.Hue.NEUTRAL);
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3d Task 2 step 4: app mirrors, registry rule, translation, the fake and both harnesses."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/palette/PaletteScope.java", [(
r'''    default boolean monospaceRows() { return false; }
''',
r'''    default boolean monospaceRows() { return false; }
    /**
     * The hue of this scope's row squares where a presentation draws them: never NONE. A plugin scope that names no hue
     * takes its plugin's manifest {@code icon_hue}.
     */
    default dev.jasper.ui.model.Nodes.Hue hue() { return dev.jasper.ui.model.Nodes.Hue.NEUTRAL; }
    /** What one row is, as a lowercase singular noun such as "session"; the label when the scope names none. */
    default String itemNoun() { return label(); }
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/palette/ScopeRegistry.java", [(
r'''        if (scope.verbs().isEmpty()) throw new IllegalArgumentException("Scope needs at least one verb: " + id);
''',
r'''        if (scope.verbs().isEmpty()) throw new IllegalArgumentException("Scope needs at least one verb: " + id);
        if (scope.verbs().getFirst().role() == PaletteVerb.Role.SPLIT
                || scope.verbs().stream().filter(verb -> verb.role() == PaletteVerb.Role.SPLIT).count() > 1)
            throw new IllegalArgumentException("A scope's split verb follows its first verb, and it has at most one: " + id);
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/HostedPalette.java", [
(r'''    private final HostedTerminals terminals;
    private boolean audited;

    HostedPalette(String pluginId, CapabilityGate gate, Contributions model, Containment containment, HostedUi ui, HostedTerminals terminals) {
        this.pluginId = pluginId; this.gate = gate; this.model = model; this.containment = containment; this.ui = ui; this.terminals = terminals;
    }''',
 r'''    private final HostedTerminals terminals;
    /** The plugin's manifest {@code icon_hue}, which a scope that names no hue takes; never NONE. */
    private final dev.jasper.ui.model.Nodes.Hue ownerHue;
    private boolean audited;

    HostedPalette(String pluginId, CapabilityGate gate, Contributions model, Containment containment, HostedUi ui, HostedTerminals terminals,
                  dev.jasper.ui.model.Nodes.Hue ownerHue) {
        this.pluginId = pluginId; this.gate = gate; this.model = model; this.containment = containment; this.ui = ui; this.terminals = terminals;
        this.ownerHue = Objects.requireNonNull(ownerHue, "ownerHue");
    }''', 1),
(r'''            this.verbs = spec.verbs().stream().map(verb -> new dev.jasper.app.palette.PaletteVerb(verb.id(), verb.label())).toList();''',
 r'''            this.verbs = spec.verbs().stream().map(verb -> new dev.jasper.app.palette.PaletteVerb(verb.id(), verb.label(),
                dev.jasper.app.palette.PaletteVerb.Role.valueOf(verb.role().name()))).toList();''', 1),
(r'''        @Override public boolean monospaceRows() { return spec.monospaceRows(); }
''',
 r'''        @Override public boolean monospaceRows() { return spec.monospaceRows(); }
        @Override public dev.jasper.ui.model.Nodes.Hue hue() {
            return spec.hue() == dev.jasper.sdk.ui.Nodes.Hue.NONE ? ownerHue : dev.jasper.ui.model.Nodes.Hue.valueOf(spec.hue().name());
        }
        @Override public String itemNoun() { return spec.itemNoun().isEmpty() ? spec.label() : spec.itemNoun(); }
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java", [(
    "        this.palette = new HostedPalette(id, gate, host.environment.contributions(), host.containment, ui, terminals);",
    "        this.palette = new HostedPalette(id, gate, host.environment.contributions(), host.containment, ui, terminals, hosted.iconHue());", 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java", [(
    "                candidate.descriptor().exports(), unit.loader(), unit::instantiate));",
    "                candidate.descriptor().exports(), unit.loader(), unit::instantiate, candidate.descriptor().iconHue()));", 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java", [(
r'''        /** Registered scopes as {@code id|label|verbIds}, in registration order. */
        public List<String> scopes() {
            return scopes.values().stream().map(registered -> registered.spec().id() + "|" + registered.spec().label() + "|"
                + String.join(",", registered.spec().verbs().stream().map(PaletteVerb::id).toList())).toList();
        }
''',
r'''        /** Registered scopes as {@code id|label|verbIds}, in registration order. */
        public List<String> scopes() {
            return scopes.values().stream().map(registered -> registered.spec().id() + "|" + registered.spec().label() + "|"
                + String.join(",", registered.spec().verbs().stream().map(PaletteVerb::id).toList())).toList();
        }

        /**
         * How a registered scope presents its rows where a presentation colours and labels them by scope, as
         * {@code HUE|itemNoun|ROLE,ROLE}: its hue, NONE read as NEUTRAL (the fake reads no manifest {@code icon_hue});
         * its item noun, or its label when it names none; and each verb's role, in order.
         *
         * @param scopeId a registered scope
         * @return the presentation
         * @since 0.18.0
         */
        public String scopePresentation(String scopeId) {
            var spec = scope(scopeId).spec();
            return (spec.hue() == dev.jasper.sdk.ui.Nodes.Hue.NONE ? dev.jasper.sdk.ui.Nodes.Hue.NEUTRAL : spec.hue()).name() + "|"
                + (spec.itemNoun().isEmpty() ? spec.label() : spec.itemNoun()) + "|"
                + String.join(",", spec.verbs().stream().map(verb -> verb.role().name()).toList());
        }
''', 1)])
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java", [(
r'''        List<String> scopes();
''',
r'''        List<String> scopes();
        /**
         * A registered scope's row presentation as {@code HUE|itemNoun|ROLE,ROLE}: its hue (NONE taken as the plugin's
         * icon hue, NEUTRAL without one), its item noun (its label when it names none) and each verb's role, in order.
         */
        String scopePresentation(String scopeId);
''', 1)])
edit("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java", [(
r'''            @Override public List<String> scopes() { return host.scopes(); }
''',
r'''            @Override public List<String> scopes() { return host.scopes(); }
            @Override public String scopePresentation(String scopeId) { return host.scopePresentation(scopeId); }
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java", [(
r'''                        @Override public List<String> scopes() {
                            return onEdtValue(() -> contributions.scopes().stream().map(scope -> scope.id() + "|" + scope.label() + "|"
                                + String.join(",", scope.verbs().stream().map(dev.jasper.app.palette.PaletteVerb::id).toList())).toList());
                        }
''',
r'''                        @Override public List<String> scopes() {
                            return onEdtValue(() -> contributions.scopes().stream().map(scope -> scope.id() + "|" + scope.label() + "|"
                                + String.join(",", scope.verbs().stream().map(dev.jasper.app.palette.PaletteVerb::id).toList())).toList());
                        }
                        @Override public String scopePresentation(String scopeId) {
                            return onEdtValue(() -> { var scope = scope(scopeId); return scope.hue().name() + "|" + scope.itemNoun() + "|"
                                + String.join(",", scope.verbs().stream().map(verb -> verb.role().name()).toList()); });
                        }
''', 1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk:javadoc :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.plugins.*' verifySdkArchitecture --continue`
Expected: BUILD SUCCESSFUL. The two new `PaletteValuesTest` cases, `theRegistryRefusesASplitVerbFirstOrTwiceAndScopesDefaultToNeutralAndTheirLabel`,
`aScopeThatNamesNoHueTakesItsPluginsIconHueAndRolesAndNounsCarryOver` and `aScopeCarriesItsHueItemNounAndSplitVerbToTheHost`
(under both harnesses) pass; `verifySdkArchitecture` accepts the `palette` → `ui` edge; every existing palette and
plugin test passes unchanged.

- [ ] **Step 6: Commit**

```bash
git add jasper-sdk/src jasper-sdk-testkit/src jasper-app/src/main/java/dev/jasper/app/palette jasper-app/src/main/java/dev/jasper/app/plugins \
  jasper-app/src/test/java/dev/jasper/app/palette/PaletteScopeModelTest.java jasper-app/src/test/java/dev/jasper/app/plugins/HostedPaletteTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java
git commit -F - <<'EOF'
SDK 0.18: palette hue, item noun and verb role

ScopeSpec gains hue and itemNoun and PaletteVerb gains role, STANDARD or
SPLIT, validated in the SDK: a noun is lowercase and short, and a scope has
at most one split verb, never its first. The app mirrors them with their
effective values: a plugin scope without a hue takes its manifest's
icon_hue (HostedPlugin.iconHue), one without a noun its label. The
registry refuses the same verb roles; the fake and the shared contract
report the presentation. Nothing draws them until the Run window.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: The dialog swatch

`DialogSpec.swatch` (spec 8.2, 10.8) is a hue whose square sits before the dialog's title wherever Jasper draws that
title; NONE draws none. The host carries it on `AuxiliarySurface`, and `MacTitleBar` draws it in a window or dialog
title row, the only dialog title the default look draws itself (ruling 6). The fake records it.

**Files:**
- Modify (whole file): `jasper-sdk/src/main/java/dev/jasper/sdk/ui/DialogSpec.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/AuxiliarySurface.java:79-82`, `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java:742`, `jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java:350`, `jasper-app/src/main/java/dev/jasper/app/platform/MacTitleBar.java:145-150`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java:276,566`, `FakePluginHost.java` (after `dialogPresentation`), `ContractHarness.java` (after `windows()`), `FakeContractTest.java`, `AppContractTest.java` (after `windows()`)
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/PanelAndWindowValuesTest.java` (one test), `jasper-app/src/test/java/dev/jasper/app/windows/NativeShellsChromeTest.java` (one test), `PluginContractTest.java` (one test)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `DialogSpec(String title, WindowOwner owner, boolean modal, Presentation presentation, Nodes.Hue swatch)`;
  `AuxiliarySurface.swatch(dev.jasper.ui.model.Nodes.Hue)` and `swatch()`; `MacTitleBar.setTitleSwatch(dev.jasper.ui.model.Nodes.Hue)`;
  `FakePluginHost.dialogSwatch(String pluginId, String title)` (`Optional<Nodes.Hue>`); harness
  `dialogSwatch(String pluginId, String title)` answering a hue name or `-`.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3d Task 3 step 1: the dialog swatch tests."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-sdk/src/test/java/dev/jasper/sdk/ui/PanelAndWindowValuesTest.java", [(
r'''    @Test void oldNativeConstructorsPreserveCenteredAndOuterDefaults() throws Exception {
''',
r'''    /** SDK 0.18: a dialog's swatch is NONE unless asked for, and never null. */
    @Test void aDialogsSwatchDefaultsToNoneAndIsRequired() {
        WindowOwner owner = new WindowOwner() { };
        assertThat(new DialogSpec("Add Login", owner, true).swatch()).isEqualTo(Nodes.Hue.NONE);
        assertThat(new DialogSpec("Add Login", owner, true, DialogSpec.Presentation.OVERLAY).swatch()).isEqualTo(Nodes.Hue.NONE);
        assertThat(new DialogSpec("Add Login", owner, true, DialogSpec.Presentation.OVERLAY, Nodes.Hue.BLUE).swatch()).isEqualTo(Nodes.Hue.BLUE);
        assertThatNullPointerException().isThrownBy(() -> new DialogSpec("Add Login", owner, true, DialogSpec.Presentation.CENTERED, null));
    }

    @Test void oldNativeConstructorsPreserveCenteredAndOuterDefaults() throws Exception {
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/windows/NativeShellsChromeTest.java", [(
r'''    @Test void actualChromeMountsTitleActionsOutsideBodyWithCenteredNativeTitleAndFallback() {
''',
r'''    /** Plan 3d: DialogSpec.swatch is a square of its hue before a dialog's title in Jasper's title row; NONE draws none. */
    @Test void aDialogSwatchSitsBeforeItsTitleInItsHuesColour() {
        var controller = ThemeTestSupport.controller(true);
        try {
            for (var hue : java.util.List.of(dev.jasper.ui.model.Nodes.Hue.BLUE, dev.jasper.ui.model.Nodes.Hue.NONE)) {
                var surface = new AuxiliarySurface("", "Add Login", AuxiliarySurface.Kind.DIALOG, true, new Dimension(400, 300), null, null,
                    ignored -> { throw new AssertionError("No native shell in tests"); });
                assertThat(surface.swatch()).as("none until asked for").isEqualTo(dev.jasper.ui.model.Nodes.Hue.NONE);
                surface.swatch(hue);
                try (var bar = NativeShells.installTitleBar(new JRootPane(), surface, true)) {
                    var title = (JLabel) bar.getComponent(0);
                    assertThat(title.isVisible()).as("a dialog's title row shows its title").isTrue();
                    if (hue == dev.jasper.ui.model.Nodes.Hue.NONE) { assertThat(title.getIcon()).isNull(); continue; }
                    var icon = title.getIcon();
                    int side = com.formdev.flatlaf.util.UIScale.scale(12);
                    assertThat(icon.getIconWidth()).as("the caption icon size").isEqualTo(side);
                    assertThat(icon.getIconHeight()).isEqualTo(side);
                    var image = new java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics();
                    try { icon.paintIcon(title, graphics, 0, 0); } finally { graphics.dispose(); }
                    assertThat(new java.awt.Color(image.getRGB(side / 2, side / 2), true))
                        .isEqualTo(controller.current().chrome().semantic().color(dev.jasper.ui.theme.SemanticColor.HUE_BLUE));
                }
            }
        } finally { controller.close(); }
    }

    @Test void actualChromeMountsTitleActionsOutsideBodyWithCenteredNativeTitleAndFallback() {
''', 1)])
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java", [(
r'''    @Test void theCloseCommandAsksTheContentNewestFirstBeforeTheGuards() {
''',
r'''    /** SDK 0.18: a dialog's swatch reaches the host with the dialog; NONE asks for no square. */
    @Test void aDialogCarriesItsSwatchToTheHost() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            PluginWindow manager = context.windows().create(new WindowSpec("test.alpha.manager", "Manager", new Dimension(640, 480), true));
            PluginWindow other = context.windows().create(new WindowSpec("test.alpha.other", "Other", new Dimension(640, 480), true));
            context.windows().dialog(new DialogSpec("Add Login", manager, true, DialogSpec.Presentation.CENTERED, dev.jasper.sdk.ui.Nodes.Hue.BLUE));
            context.windows().dialog(new DialogSpec("Details", other, true));
        });
        h.ui(() -> {
            assertThat(h.dialogSwatch("test.alpha", "Add Login")).isEqualTo("BLUE");
            assertThat(h.dialogSwatch("test.alpha", "Details")).isEqualTo("NONE");
            assertThat(h.dialogSwatch("test.alpha", "Absent")).isEqualTo("-");
        });
    }

    @Test void theCloseCommandAsksTheContentNewestFirstBeforeTheGuards() {
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.PanelAndWindowValuesTest :jasper-app:test --tests dev.jasper.app.windows.NativeShellsChromeTest --continue`
Expected: FAIL. `:jasper-sdk:compileTestJava` stops with "cannot find symbol … swatch()" (and the five-argument
constructor) in `PanelAndWindowValuesTest`, and `:jasper-sdk-testkit:compileTestFixturesJava` stops on the five-argument
`DialogSpec` and `dialogSwatch` in `PluginContractTest`. `:jasper-app:compileTestJava` depends on those test fixtures
(`AppContractTest` extends `PluginContractTest`), so Gradle does not run it, even with `--continue`;
`NativeShellsChromeTest` first compiles in Step 5.

- [ ] **Step 3: The SDK record**

Check first: `grep -c 'a hint the presentation may place another way' jasper-sdk/src/main/java/dev/jasper/sdk/ui/DialogSpec.java`
prints 1 (3a's wording). Then replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/ui/DialogSpec.java` with:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.WindowOwner;
import java.util.Objects;

/**
 * A dialog the application builds over one of its windows.
 *
 * @param title non-blank title
 * @param owner the terminal window or plugin window the dialog belongs to
 * @param presentation explicit presentation; attached sheets and overlays require modality, and attached sheets a live
 *     checked owner at show
 * @param modal whether the dialog blocks its owner; {@link WindowSurface#show()} then returns only after it closes
 * @param swatch a small square of this hue drawn before the title wherever Jasper draws the dialog's title, as in a
 *     login dialog's coloured square; a title bar the system draws shows none, and {@link Nodes.Hue#NONE} draws none
 *     (since 0.18.0)
 */
public record DialogSpec(String title, WindowOwner owner, boolean modal, Presentation presentation, Nodes.Hue swatch) {
    /** Placement, independent of the form's content kind; a hint the presentation may place another way. */
    public enum Presentation {
        /** A native dialog centred over its owner, sized to its content; the user cannot resize it. */
        CENTERED,
        /** A native sheet hanging from the owner's title edge. */
        ATTACHED_SHEET,
        /**
         * Inside the owner's window, with no native window: the rest of the window dims under a scrim and the dialog,
         * centred above it, fades up into place, and rises on as it fades out when it closes (both instant with motion
         * off).
         * Modal only.
         *
         * @since 0.15.4
         */
        OVERLAY
    }
    /** Retains the centered presentation and original JVM constructor. */
    public DialogSpec(String title, WindowOwner owner, boolean modal) { this(title, owner, modal, Presentation.CENTERED); }
    /**
     * A dialog with no swatch.
     *
     * @param title non-blank title
     * @param owner the window the dialog belongs to
     * @param modal whether the dialog blocks its owner
     * @param presentation explicit presentation
     */
    public DialogSpec(String title, WindowOwner owner, boolean modal, Presentation presentation) {
        this(title, owner, modal, presentation, Nodes.Hue.NONE);
    }
    /** Validates the title and owner. */
    public DialogSpec {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A dialog needs a title");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(swatch, "swatch");
        if (presentation == Presentation.ATTACHED_SHEET && !modal) throw new IllegalArgumentException("Attached sheets must be modal");
        if (presentation == Presentation.OVERLAY && !modal) throw new IllegalArgumentException("Overlays must be modal");
    }
}
```

- [ ] **Step 4: The host, the title row and the fake**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3d Task 3 step 4: the swatch through AuxiliarySurface to MacTitleBar, and the fake."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/windows/AuxiliarySurface.java", [(
r'''    public void attachedSheet(boolean value) {
        if (value && !modal) throw new IllegalArgumentException("Attached sheets must be modal");
        attachedSheet = value;
    }
''',
r'''    public void attachedSheet(boolean value) {
        if (value && !modal) throw new IllegalArgumentException("Attached sheets must be modal");
        attachedSheet = value;
    }
    private dev.jasper.ui.model.Nodes.Hue swatch = dev.jasper.ui.model.Nodes.Hue.NONE;
    /** The hue of the square drawn before this surface's title wherever Jasper draws it; NONE draws none. Set before the first show. */
    public void swatch(dev.jasper.ui.model.Nodes.Hue value) { swatch = Objects.requireNonNull(value); }
    public dev.jasper.ui.model.Nodes.Hue swatch() { return swatch; }
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java", [(
r'''                surface.attachedSheet(spec.presentation() == DialogSpec.Presentation.ATTACHED_SHEET);
''',
r'''                surface.attachedSheet(spec.presentation() == DialogSpec.Presentation.ATTACHED_SHEET);
                surface.swatch(dev.jasper.ui.model.Nodes.Hue.valueOf(spec.swatch().name()));
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java", [(
r'''            bar.setTitle(surface.title());
''',
r'''            bar.setTitle(surface.title());
            bar.setTitleSwatch(surface.swatch());
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/platform/MacTitleBar.java", [(
r'''    /** Updates metadata independently of the window's workspace model. */
    public void setTitle(String value) {
        if (closed) return;
        if (!value.equals(title.getText())) title.setText(value);
        revalidate(); repaint();
    }
''',
r'''    /** Updates metadata independently of the window's workspace model. */
    public void setTitle(String value) {
        if (closed) return;
        if (!value.equals(title.getText())) title.setText(value);
        revalidate(); repaint();
    }

    /**
     * A square of {@code hue}'s theme colour before a window or dialog title ({@code DialogSpec.swatch}); NONE draws none.
     * A row that shows no title shows no square either.
     */
    public void setTitleSwatch(dev.jasper.ui.model.Nodes.Hue hue) {
        if (closed) return;
        SemanticColor role = switch (java.util.Objects.requireNonNull(hue)) {
            case BLUE -> SemanticColor.HUE_BLUE; case GREEN -> SemanticColor.HUE_GREEN; case AMBER -> SemanticColor.HUE_AMBER;
            case PURPLE -> SemanticColor.HUE_PURPLE; case NEUTRAL -> SemanticColor.HUE_NEUTRAL; case NONE -> null;
        };
        title.setIcon(role == null ? null : new TitleSwatch(role));
        title.setIconTextGap(dev.jasper.ui.swing.NativeStyles.installed().metric(dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP));
        revalidate(); repaint();
    }

    /** A solid square of a hue role at the caption icon size, coloured from the theme each time it paints. */
    private record TitleSwatch(SemanticColor role) implements Icon {
        private static int side() { return dev.jasper.ui.swing.NativeStyles.installed().metric(dev.jasper.ui.metrics.DesignMetrics.Role.ICON_CAPTION); }
        @Override public int getIconWidth() { return side(); }
        @Override public int getIconHeight() { return side(); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            graphics.setColor(ThemeZone.color(component, role));
            graphics.fillRect(x, y, side(), side());
        }
    }
''', 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java", [
(r'''        DialogSpec.Presentation presentation;
''',
 r'''        DialogSpec.Presentation presentation;
        /** The hue of the square a dialog asked for before its title; NONE for none. */
        dev.jasper.sdk.ui.Nodes.Hue swatch = dev.jasper.sdk.ui.Nodes.Hue.NONE;
''', 1),
(r'''                dialog.presentation = spec.presentation();
''',
 r'''                dialog.presentation = spec.presentation();
                dialog.swatch = spec.swatch();
''', 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java", [(
r'''        return context.ui.windows.stream().filter(w -> !w.closed && w.title.equals(title))
            .map(w -> w.presentation).filter(java.util.Objects::nonNull).findFirst();
    }
''',
r'''        return context.ui.windows.stream().filter(w -> !w.closed && w.title.equals(title))
            .map(w -> w.presentation).filter(java.util.Objects::nonNull).findFirst();
    }

    /**
     * The hue of the square an open plugin dialog draws before its title, as its {@code DialogSpec} asked. Call it on the
     * UI thread.
     * @param pluginId dialog owner
     * @param title exact dialog title
     * @return the swatch while the dialog (any presentation, {@code OVERLAY} dialogs included) is open; empty for plugin
     *     windows, {@code Windows.overlay} surfaces and closed or absent dialogs
     * @since 0.18.0
     */
    public java.util.Optional<dev.jasper.sdk.ui.Nodes.Hue> dialogSwatch(String pluginId, String title) {
        FakePluginContext context = contexts.get(pluginId);
        if (context == null) return java.util.Optional.empty();
        return context.ui.windows.stream().filter(w -> !w.closed && w.title.equals(title) && w.presentation != null)
            .map(w -> w.swatch).findFirst();
    }
''', 1)])
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java", [(
r'''    List<String> windows();
''',
r'''    List<String> windows();

    /** UI thread: the hue name of the square an open plugin dialog with this title draws before its title, or {@code -} when there is none. */
    String dialogSwatch(String pluginId, String title);
''', 1)])
edit("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java", [(
r'''            @Override public List<String> windows() { return host.windows(); }
''',
r'''            @Override public List<String> windows() { return host.windows(); }
            @Override public String dialogSwatch(String pluginId, String title) { return host.dialogSwatch(pluginId, title).map(Enum::name).orElse("-"); }
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java", [(
r'''                        + "|" + surface.title() + "|" + surface.shown()).toList());
            }
''',
r'''                        + "|" + surface.title() + "|" + surface.shown()).toList());
            }
            @Override public String dialogSwatch(String pluginId, String title) {
                return onEdtValue(() -> auxiliary.open().stream()
                    .filter(surface -> surface.kind() != AuxiliarySurface.Kind.WINDOW && surface.title().equals(title))
                    .findFirst().map(surface -> surface.swatch().name()).orElse("-"));
            }
''', 1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk:javadoc :jasper-sdk-testkit:test :jasper-app:test --tests dev.jasper.app.windows.NativeShellsChromeTest --tests dev.jasper.app.workspace.MacTitleBarTest --tests dev.jasper.app.plugins.AppContractTest --tests dev.jasper.app.architecture.StaticDefaultTest --continue`
Expected: BUILD SUCCESSFUL. `aDialogsSwatchDefaultsToNoneAndIsRequired`, `aDialogSwatchSitsBeforeItsTitleInItsHuesColour`
and `aDialogCarriesItsSwatchToTheHost` (both harnesses) pass; every existing title-bar test passes unchanged (no
swatch is set on them); `StaticDefaultTest` accepts `TitleSwatch`, which reads the installed kit, not the default
catalog.

- [ ] **Step 6: Commit**

```bash
git add jasper-sdk/src jasper-sdk-testkit/src jasper-app/src/main/java/dev/jasper/app/windows jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java \
  jasper-app/src/main/java/dev/jasper/app/platform/MacTitleBar.java jasper-app/src/test/java/dev/jasper/app/windows/NativeShellsChromeTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java
git commit -F - <<'EOF'
SDK 0.18: the dialog swatch

DialogSpec gains swatch, a hue whose square sits before the dialog's
title wherever Jasper draws that title; NONE, the default, draws none.
The host carries it on AuxiliarySurface and MacTitleBar draws it in a
window or dialog title row at the caption icon size, coloured from the
theme. The fake records it and the shared contract checks both runtimes.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: The header renders toolbar views

The app model gains `ToolbarEntry.View`: a factory each window calls once, with its id, when the window's open event
fires, returning the component it places and how to close it (spec 10.3, rulings 3 and 4). `WindowChrome` builds a
window's views from `windowOpened()` (called by `WindowContent.connectTerminals`, which the warm-up rehearsal never
reaches), closes each when its entry goes or the window lets go of its contributions, and presents a `KitView` as a
toolbar strip (ruling 5). `WindowToolbar` places a view at its preferred size, centred in the row, and when the row is
too narrow hides it instead of moving it into the overflow menu. No SDK change: Task 5 connects plugins.

**Files:**
- Modify (whole file): `jasper-app/src/main/java/dev/jasper/app/contributions/ToolbarEntry.java`, `jasper-app/src/main/java/dev/jasper/app/workspace/WindowToolbar.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitView.java:328-330`, `jasper-ui/src/main/java/dev/jasper/ui/swing/Menus.java:234-235`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java:47,143-147,164-199`, `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java:753`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java:237-240` (the exhaustive switch), `docs/design/theme-engine/migration.csv` (one row appended)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/KitViewToolbarStripTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/WindowToolbarViewsTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowToolbarTest.java` (helper and two tests)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `ToolbarEntry.View(Function<UUID, Optional<ToolbarEntry.Mounted>> factory)`;
  `ToolbarEntry.Mounted(JComponent component, Runnable closer) implements AutoCloseable` (`close()` runs `closer`);
  `KitView.toolbarStrip(boolean)` and `toolbarStrip()`; `WindowToolbar.view(JComponent, int priority)`;
  `WindowChrome.windowOpened()`. Task 5's `HostedUi.toolbar` produces `ToolbarEntry.View` entries and Task 5's app
  harness calls `View.factory()` and `Mounted.close()`.

- [ ] **Step 1: Write the failing tests**

First, before any Task 4 edit, record the gallery baseline in this worktree. Tasks 1 to 3 change nothing under
`jasper-ui`, and the gallery runs on jasper-ui's test classpath only, so this is the base's gallery (Execution notes, R6):

```bash
for theme in dark light; do
  rm -rf jasper-ui/build/gallery/$theme-1x jasper-ui/build/plan3d-gallery-base/$theme-1x
  ./gradlew -q :jasper-ui:gallery -Pjasper.galleryTheme=$theme
  mkdir -p jasper-ui/build/plan3d-gallery-base && cp -R jasper-ui/build/gallery/$theme-1x jasper-ui/build/plan3d-gallery-base/$theme-1x
done
```

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/KitViewToolbarStripTest.java`:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Plan 3d: a contributed toolbar view is a toolbar strip, which presents an icon-only menu as a title strip does. */
class KitViewToolbarStripTest {
    static ViewSpec iconMenu() {
        return new ViewSpec(new Nodes.MenuButton("more", ControlState.READY, "", "more", Nodes.ButtonKind.SECONDARY, "",
            List.of(new MenuItem("manage", "Manage Sessions", "", "manage", true, false)), "More"));
    }

    static int[] pixels(Consumer<KitView> presentation) {
        var context = KitFixtures.context();
        try (var view = new KitView(context, iconMenu(), event -> { })) {
            presentation.accept(view);
            return KitFixtures.render(view, 120, 30, 1).getRGB(0, 0, 120, 30, null, 0, 120);
        } finally { context.binding().close(); context.icons().close(); }
    }

    @Test void aToolbarStripPresentsAnIconOnlyMenuAsATitleStripDoes() {
        Edt.run(() -> {
            int[] plain = pixels(view -> { }), title = pixels(view -> view.titleStrip(true)), toolbar = pixels(view -> view.toolbarStrip(true));
            assertThat(title).as("a strip changes the icon-only menu").isNotEqualTo(plain);
            assertThat(toolbar).isEqualTo(title);
        });
    }

    @Test void theFlagIsReportedAndAClosedViewRefusesIt() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try {
                var view = new KitView(context, iconMenu(), event -> { });
                assertThat(view.toolbarStrip()).isFalse();
                view.toolbarStrip(true);
                assertThat(view.toolbarStrip()).isTrue();
                assertThat(view.titleStrip()).as("a separate flag").isFalse();
                view.close();
                assertThatIllegalStateException().isThrownBy(() -> view.toolbarStrip(false));
            } finally { context.binding().close(); context.icons().close(); }
        });
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WindowToolbarViewsTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.terminals.TerminalRegistry;
import java.awt.Dimension;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

/** Plan 3d: a contributed toolbar view is built once per window when the window opens, and closes with it or its entry. */
class WindowToolbarViewsTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    /** A view entry whose factory records each window it builds for, the components it made and each close. */
    static final class Recorder {
        final List<UUID> built = new ArrayList<>();
        final List<JComponent> components = new ArrayList<>();
        final AtomicInteger closed = new AtomicInteger();
        final ToolbarEntry.View entry = new ToolbarEntry.View(window -> {
            built.add(window);
            var component = new JPanel();
            component.setPreferredSize(new Dimension(120, 24));
            components.add(component);
            return Optional.of(new ToolbarEntry.Mounted(component, closed::incrementAndGet));
        });
    }

    static boolean inToolbar(WindowContent owner, JComponent component) {
        return Arrays.asList(owner.toolbar().getComponents()).contains(component);
    }

    @Test void aWindowBuildsItsViewOnlyOnceItOpensAndOnlyOnce() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var recorder = new Recorder();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model);
            model.addToolbar(recorder.entry);
            assertThat(recorder.built).as("not before the window opens, as for the warm-up rehearsal's window").isEmpty();
            owner.connectTerminals(new TerminalRegistry(), () -> { });
            assertThat(recorder.built).containsExactly(owner.id());
            assertThat(inToolbar(owner, recorder.components.getFirst())).isTrue();
            owner.chrome().renderContributedToolbar();
            model.addToolbar(new ToolbarEntry.Button("dev.x.absent"));
            assertThat(recorder.built).as("re-rendering keeps the window's view").containsExactly(owner.id());
            assertThat(inToolbar(owner, recorder.components.getFirst())).isTrue();
        });
    }

    @Test void aViewClosesWhenItsEntryGoesAndWhenItsWindowCloses() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var first = new Recorder();
            var second = new Recorder();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model);
            owner.connectTerminals(new TerminalRegistry(), () -> { });
            var registration = model.addToolbar(first.entry);
            model.addToolbar(second.entry);
            assertThat(first.built).as("an open window builds a new entry's view at once").containsExactly(owner.id());
            registration.close();
            assertThat(first.closed).hasValue(1);
            assertThat(inToolbar(owner, first.components.getFirst())).isFalse();
            assertThat(second.closed).hasValue(0);
            owner.close();
            assertThat(second.closed).as("the window's close closes the rest").hasValue(1);
            assertThat(first.closed).as("each view closes once").hasValue(1);
        });
    }

    @Test void aFactoryThatFailedIsNotAskedAgainForThatWindow() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var asked = new AtomicInteger();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model);
            owner.connectTerminals(new TerminalRegistry(), () -> { });
            model.addToolbar(new ToolbarEntry.View(window -> { asked.incrementAndGet(); return Optional.empty(); }));
            owner.chrome().renderContributedToolbar();
            assertThat(asked).hasValue(1);
            assertThat(WindowTabsTest.named(owner.toolbar(), "pluginSeparator").isVisible()).as("nothing shown, no plugin group").isFalse();
        });
    }

    /** A factory that changes the contributions while it runs re-renders the toolbar from inside the pass that asked it. */
    @Test void aFactoryThatChangesTheContributionsIsAskedOnceAndItsViewClosesOnce() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var asked = new AtomicInteger();
            var closed = new AtomicInteger();
            List<JComponent> components = new ArrayList<>();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model);
            owner.connectTerminals(new TerminalRegistry(), () -> { });
            var registration = model.addToolbar(new ToolbarEntry.View(window -> {
                int call = asked.incrementAndGet();
                // Both fire Contributions.Kind.ACTIONS synchronously, which renders the toolbar again.
                var action = model.addAction("dev.x.connect" + call, "Connect", null, List.of(), Optional.empty(), invocation -> { });
                action.setEnabled(false);
                var component = new JPanel();
                component.setPreferredSize(new Dimension(120, 24));
                components.add(component);
                return Optional.of(new ToolbarEntry.Mounted(component, closed::incrementAndGet));
            }));
            assertThat(asked).as("the nested pass does not ask the factory again").hasValue(1);
            assertThat(components).hasSize(1);
            assertThat(inToolbar(owner, components.getFirst())).isTrue();
            registration.close();
            assertThat(closed).as("the one view closes once").hasValue(1);
            assertThat(inToolbar(owner, components.getFirst())).isFalse();
        });
    }

    @Test void aKitViewIsPresentedAsAToolbarStrip() throws Exception {
        edt(() -> {
            var controller = ThemeTestSupport.controller(true);
            var binding = new dev.jasper.ui.theme.ThemeBinding(controller.current().pair(), failure -> { throw new AssertionError(failure); });
            var icons = new dev.jasper.ui.icons.IconRegistry();
            var context = dev.jasper.ui.swing.KitContext.of(dev.jasper.ui.kit.Kit.DEFAULT, binding, dev.jasper.ui.theme.ThemeRoute.CHROME,
                icons, () -> false, action -> "");
            var view = new dev.jasper.ui.swing.KitView(context, new dev.jasper.ui.model.ViewSpec(new dev.jasper.ui.model.Nodes.Text("label",
                dev.jasper.ui.model.ControlState.READY, "Session", dev.jasper.ui.model.Nodes.TextRole.LABEL)), event -> { });
            try {
                var model = new Contributions();
                WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), controller);
                owner.connectContributions(model);
                owner.connectTerminals(new TerminalRegistry(), () -> { });
                model.addToolbar(new ToolbarEntry.View(window -> Optional.of(new ToolbarEntry.Mounted(view, view::close))));
                assertThat(view.toolbarStrip()).isTrue();
                assertThat(inToolbar(owner, view)).isTrue();
                owner.close();
            } finally { if (!view.isClosed()) view.close(); binding.close(); icons.close(); controller.close(); }
        });
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3d Task 4 step 1: the toolbar's view layout tests."""
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/workspace/WindowToolbarTest.java"); text = path.read_text(encoding="utf-8")
old = r'''    @Test void overflowRetainsActionsSelectionShortcutsAndLiveEnabledState() {
'''
new = r'''    private JComponent view(WindowToolbar bar, int width, int height, int priority) {
        var view = new JPanel();
        view.setPreferredSize(new Dimension(width, height));
        WindowToolbar.view(view, priority);
        bar.add(view);
        return view;
    }

    /** Plan 3d: a contributed view lays out at its preferred size, hides when the row is too narrow and never overflows. */
    @Test void aViewHidesWhileTheRowIsTooNarrowAndNeverEntersTheOverflowMenu() {
        var calls = new AtomicInteger();
        var bar = new WindowToolbar(() -> {});
        var view = view(bar, 200, 24, 40);
        var settings = button(bar, action("Settings", calls), 100);
        bar.finish();
        assertThat(bar.fullWidth()).as("the view counts toward the full width").isEqualTo(16 + 200 + 4 + 60);
        assertThat(bar.minimumWidth()).as("not toward the minimum: it can hide").isEqualTo(16 + 24);
        bar.setSize(400, 30); bar.doLayout();
        assertThat(view.isVisible()).isTrue();
        assertThat(view.getBounds()).as("its preferred size, centred in the row").isEqualTo(new java.awt.Rectangle(128, 3, 200, 24));
        assertThat(settings.getX()).isEqualTo(332);
        bar.setSize(200, 30); bar.doLayout();
        assertThat(view.isVisible()).as("too narrow: the view hides").isFalse();
        assertThat(settings.isVisible()).isTrue();
        assertThat(bar.overflowButton().isVisible()).as("a hidden view needs no overflow button").isFalse();
        assertThat(bar.overflowMenu().getComponentCount()).as("and leaves nothing in the overflow menu").isZero();
        bar.setSize(400, 30); bar.doLayout();
        assertThat(view.isVisible()).as("room again: it shows").isTrue();
    }

    @Test void aLowerPriorityButtonOverflowsBeforeAViewHides() {
        var calls = new AtomicInteger();
        var bar = new WindowToolbar(() -> {});
        var zoom = button(bar, action("Zoom", calls), 10);
        var view = view(bar, 100, 24, 40);
        button(bar, action("Settings", calls), 100);
        bar.finish();
        bar.setSize(16 + 100 + 4 + 60 + 4 + 24, 30); bar.doLayout();
        assertThat(zoom.isVisible()).isFalse();
        assertThat(view.isVisible()).isTrue();
        assertThat(bar.overflowButton().isVisible()).isTrue();
    }

    @Test void overflowRetainsActionsSelectionShortcutsAndLiveEnabledState() {
'''
if text.count(old) != 1: raise SystemExit("WindowToolbarTest anchor not found")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests dev.jasper.ui.swing.KitViewToolbarStripTest :jasper-app:test --tests dev.jasper.app.workspace.WindowToolbarTest --tests dev.jasper.app.workspace.WindowToolbarViewsTest --continue`
Expected: FAIL. `:jasper-ui:compileTestJava` stops with "cannot find symbol … toolbarStrip"; `:jasper-app:compileTestJava`
stops with "cannot find symbol … View" (`ToolbarEntry.View`, `ToolbarEntry.Mounted`) and "… view(…)" (`WindowToolbar.view`).

- [ ] **Step 3: The model, the strip flag and the row**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/contributions/ToolbarEntry.java` with:

```java
package dev.jasper.app.contributions;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JComponent;

/** One contributed toolbar control. */
public sealed interface ToolbarEntry permits ToolbarEntry.Button, ToolbarEntry.Dropdown, ToolbarEntry.View {
    /** A button for one contributed action. */
    record Button(String actionId) implements ToolbarEntry { }

    /** A button that opens a menu of contributed actions. */
    record Dropdown(Icon icon, String title, List<String> actionIds) implements ToolbarEntry {
        public Dropdown { actionIds = List.copyOf(actionIds); }
    }

    /**
     * A contributed group of controls, one per window: each window asks {@code factory} once, with its id, when it opens,
     * and closes what it got when it closes or this entry goes. Empty means the contributor's factory failed, and that
     * window shows nothing for it.
     */
    record View(Function<UUID, Optional<Mounted>> factory) implements ToolbarEntry {
        public View { Objects.requireNonNull(factory, "factory"); }
    }

    /** One window's built view: the component the toolbar places, and its idempotent close. */
    record Mounted(JComponent component, Runnable closer) implements AutoCloseable {
        public Mounted {
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(closer, "closer");
        }

        @Override public void close() { closer.run(); }
    }
}
```

Check first: `grep -c 'Jasper.toolbarView' jasper-app/src/main/java/dev/jasper/app/workspace/WindowToolbar.java` prints 0.
Then replace the whole of `jasper-app/src/main/java/dev/jasper/app/workspace/WindowToolbar.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeStyles;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.platform.AppIcons;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.*;

/** One row of stable-size controls packed against the trailing edge; lower-priority controls move whole
 * into a live overflow menu behind a vertical ellipsis. A contributed view never does: it hides while the row is too
 * narrow for it. */
final class WindowToolbar extends JToolBar {
    private static final String MENU = "Jasper.overflowMenu", PRIORITY = "Jasper.toolbarPriority", VIEW = "Jasper.toolbarView";
    private final JButton overflow = new JButton(AppIcons.semantic("more.vertical"));
    private final Runnable refreshActions;
    private int barHeight = ConfigSnapshot.DEFAULT_TOOLBAR_HEIGHT;

    WindowToolbar(Runnable refreshActions) {
        this.refreshActions = refreshActions;
        setFloatable(false);
        setBorder(BorderFactory.createEmptyBorder());
        overflow.setName("toolbarOverflow");
        overflow.setText(null);
        overflow.putClientProperty("label", "More actions");
        NativeStyles.installed().button(overflow, () -> ThemeZone.theme(overflow));
        overflow.setToolTipText("More actions");
        overflow.getAccessibleContext().setAccessibleName("More actions");
        overflow.setFocusable(false);
        overflow.addActionListener(event -> overflowMenu().show(overflow, 0, overflow.getHeight()));
        overflow.setVisible(false);
    }

    void finish() { add(overflow); }
    void setBarHeight(int height) {
        int old = barHeight;
        barHeight = height;
        firePropertyChange("barHeight", old, height);
        revalidate(); repaint();
    }
    static int buttonSide(int height) {
        return UIScale.scale(height - dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.TOOLBAR_BUTTON_TRIM));
    }
    int buttonSide() { return buttonSide(barHeight); }
    JButton overflowButton() { return overflow; }

    private boolean labelsWanted = true;

    /** Whether labelled controls (Split, contributed buttons and dropdowns) show their labels when they fit. */
    void setLabelsWanted(boolean value) {
        labelsWanted = value;
        applyLabels(value);
        revalidate(); repaint();
    }

    private boolean labelled(JButton button) {
        return button != overflow && !Boolean.FALSE.equals(button.getClientProperty("keepLabel"))
            && button.getClientProperty("label") instanceof String;
    }

    private void applyLabels(boolean on) {
        for (Component child : getComponents())
            if (child instanceof JButton button && labelled(button)) {
                String text = on ? (String) button.getClientProperty("label") : null;
                if (!java.util.Objects.equals(button.getText(), text)) button.setText(text);
            }
    }

    private static boolean isView(Component child) {
        return child instanceof JComponent component && Boolean.TRUE.equals(component.getClientProperty(VIEW));
    }

    /** A control the row places: a button other than the overflow button, or a contributed view. */
    private boolean placed(Component child) { return child instanceof JButton button ? button != overflow : isView(child); }

    private int groupWidth(int gap) {
        int width = 0, count = 0;
        for (Component child : getComponents())
            if (placed(child)) { width += child.getPreferredSize().width + gap; count++; }
        return count == 0 ? 0 : width - gap;
    }

    /** The width that shows every control; the header never gives the toolbar more. */
    int fullWidth() {
        int padding = NativeStyles.installed().metric(DesignMetrics.Role.TOOLBAR_PADDING), gap = NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_ACTION_PADDING), width = 0;
        for (Component child : getComponents())
            if (placed(child)) width += child.getPreferredSize().width + gap;
        return width == 0 ? 0 : 2 * padding + width - gap;
    }

    /** The width with every hideable control in the overflow menu or hidden. */
    int minimumWidth() {
        int padding = NativeStyles.installed().metric(DesignMetrics.Role.TOOLBAR_PADDING), gap = NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_ACTION_PADDING), width = buttonSide();
        for (Component child : getComponents())
            if (child instanceof JButton button && button != overflow && !(button.getClientProperty(MENU) instanceof Supplier<?>))
                width += button.getPreferredSize().width + gap;
        return 2 * padding + width;
    }

    static void entry(JButton button, int priority, Supplier<JMenuItem> menu) {
        button.putClientProperty(PRIORITY, priority);
        button.putClientProperty(MENU, menu);
    }

    /**
     * Marks a contributed view (a plugin's toolbar view): the row places it at its preferred size like a button, and
     * {@code priority} decides when it gives way as a button's does, but it never moves into the overflow menu: it hides.
     */
    static void view(JComponent component, int priority) {
        component.putClientProperty(PRIORITY, priority);
        component.putClientProperty(VIEW, Boolean.TRUE);
    }

    static JMenuItem menuItem(Action action) {
        JMenuItem item = action.getValue(Action.SELECTED_KEY) instanceof Boolean
            ? new JCheckBoxMenuItem(action) : new JMenuItem(action);
        item.putClientProperty("html.disable", true);
        return item;
    }

    JPopupMenu overflowMenu() {
        refreshActions.run();
        var menu = new JPopupMenu();
        for (Component child : getComponents()) {
            if (!(child instanceof JButton button) || button == overflow || button.isVisible()) continue;
            if (button.getClientProperty(MENU) instanceof Supplier<?> factory) menu.add((JMenuItem) factory.get());
        }
        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme); return menu;
    }

    @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(barHeight)); }
    @Override public Dimension getPreferredSize() { return new Dimension(fullWidth(), UIScale.scale(barHeight)); }

    @Override public void doLayout() {
        int padding = NativeStyles.installed().metric(DesignMetrics.Role.TOOLBAR_PADDING), gap = NativeStyles.installed().metric(DesignMetrics.Role.NATIVE_ACTION_PADDING), side = buttonSide();
        int available = Math.max(0, getWidth() - 2 * padding);
        // Labels give way before any control moves into the overflow menu.
        applyLabels(labelsWanted);
        if (labelsWanted && groupWidth(gap) > available) applyLabels(false);
        List<Component> controls = new ArrayList<>();
        for (Component child : getComponents()) {
            if (placed(child)) {
                controls.add(child); child.setVisible(true);
            }
        }
        // Stable priority, with later controls giving way first within a priority tier. A button moves into the overflow
        // menu; a view hides, so only a button that moved makes room for the overflow button.
        var removable = new ArrayList<>(controls);
        java.util.Collections.reverse(removable);
        removable.sort(Comparator.comparingInt(control -> ((JComponent) control).getClientProperty(PRIORITY) instanceof Integer p ? p : 50));
        int group = controls.stream().mapToInt(control -> control.getPreferredSize().width + gap).sum() - (controls.isEmpty() ? 0 : gap);
        boolean hidden = false;
        for (Component control : removable) {
            if (group + (hidden ? gap + side : 0) <= available) break;
            boolean view = isView(control);
            if (!view && !(((JComponent) control).getClientProperty(MENU) instanceof Supplier<?>)) continue;
            control.setVisible(false); control.setBounds(0, 0, 0, 0);
            group -= control.getPreferredSize().width + gap;
            if (!view) hidden = true;
        }
        overflow.setVisible(hidden);
        int shown = Math.max(0, group);
        int trailing = hidden ? side + (shown > 0 ? gap : 0) : 0;
        // Separators share the inter-control spacing; they never consume button target space.
        int x = Math.max(padding, getWidth() - padding - trailing - shown), y = (getHeight() - side) / 2;
        boolean previousVisible = false;
        for (Component child : getComponents()) {
            if (child == overflow) continue;
            if (placed(child)) {
                if (!child.isVisible()) continue;
                int width = child.getPreferredSize().width;
                if (isView(child)) {
                    // A view keeps its own height, centred in the row.
                    int height = Math.min(getHeight(), child.getPreferredSize().height);
                    child.setBounds(x, (getHeight() - height) / 2, width, height);
                } else child.setBounds(x, y, width, side);
                x += width + gap; previousVisible = true;
            } else if (child instanceof JSeparator) {
                child.setBounds(Math.max(0, x - gap / 2), NativeStyles.installed().metric(DesignMetrics.Role.TOOLBAR_SEPARATOR_INSET), NativeStyles.installed().metric(DesignMetrics.Role.HAIRLINE),
                    Math.max(0, getHeight() - NativeStyles.installed().metric(DesignMetrics.Role.TOOLBAR_SEPARATOR_INSETS)));
                // Empty groups paint no rule; a hidden rule (the empty plugin group's) leaves the
                // previous group open, so the rule before Find still shows.
                boolean shownRule = previousVisible && hasFollowingVisibleControl(child);
                child.setVisible(shownRule);
                if (shownRule) previousVisible = false;
            }
        }
        if (hidden) overflow.setBounds(Math.max(0, getWidth() - padding - side), y, side, side);
        else overflow.setBounds(0, 0, 0, 0);
    }

    private boolean hasFollowingVisibleControl(Component separator) {
        for (int i = getComponentIndex(separator) + 1; i < getComponentCount(); i++) {
            Component next = getComponent(i);
            if (next instanceof JSeparator) return false;
            if (placed(next) && next.isVisible()) return true;
        }
        return false;
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3d Task 4 step 3: KitView.toolbarStrip, WindowChrome's views and the window's open event."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/KitView.java", [(
r'''    /** Whether this view is presented as a window title action strip. */
    public boolean titleStrip() { return titleStrip; }
    private boolean titleStrip;
''',
r'''    /** Whether this view is presented as a window title action strip. */
    public boolean titleStrip() { return titleStrip; }
    private boolean titleStrip;
    /**
     * App-only: this view is a toolbar strip, a contributed view in a window's main toolbar. Its icon-only menu buttons
     * show their menu indicator on the borderless control surface, as a title strip's do, and its controls keep the
     * kit's control height, which the toolbar row centres. EDT confined.
     */
    public void toolbarStrip(boolean value) {
        requireOpen();
        if (toolbarStrip == value) return;
        toolbarStrip = value; refresh(); repaint();
    }
    /** Whether this view is presented as a toolbar strip. */
    public boolean toolbarStrip() { return toolbarStrip; }
    private boolean toolbarStrip;
''', 1)])
edit("jasper-ui/src/main/java/dev/jasper/ui/swing/Menus.java", [(
r'''            // A title strip's icon-only menu shows its indicator on the borderless control surface.
            boolean strip = view.titleStrip() && menu.action().isBlank() && !embedded && menu.label().isBlank() && !menu.icon().isEmpty();''',
r'''            // A title or toolbar strip's icon-only menu shows its indicator on the borderless control surface.
            boolean strip = (view.titleStrip() || view.toolbarStrip()) && menu.action().isBlank() && !embedded && menu.label().isBlank() && !menu.icon().isEmpty();''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java", [
(r'''    private final ToolbarSeparator pluginSeparator = new ToolbarSeparator();
''',
 r'''    private final ToolbarSeparator pluginSeparator = new ToolbarSeparator();
    /** When a contributed view gives way on a narrow row: after contributed buttons (30), before dropdowns (80). */
    private static final int VIEW_PRIORITY = 40;
    /** This window's built toolbar views by entry; null where the contributor's factory failed. */
    private final java.util.Map<ToolbarEntry.View, ToolbarEntry.Mounted> views = new java.util.IdentityHashMap<>();
    /** Whether this window's open event fired, so that it builds toolbar views. */
    private boolean opened;
    /** Whether a toolbar pass is running, and whether a change during it asked for another. */
    private boolean renderingToolbar;
    private boolean toolbarDirty;
''', 1),
(r'''    void connect(WindowContributions source) {
        contributions = source;
        renderContributedToolbar();
        renderContributedMenus();
    }
''',
 r'''    void connect(WindowContributions source) {
        contributions = source;
        renderContributedToolbar();
        renderContributedMenus();
    }

    /** This window's open event fired (its terminals are registered): it builds its toolbar views now. */
    void windowOpened() {
        if (opened) return;
        opened = true;
        renderContributedToolbar();
    }
''', 1),
(r'''    /** Rebuilds the plugin section after the built-in controls. */
    void renderContributedToolbar() {
        contributedToolbar.forEach(toolbar::remove);
        contributedToolbar.clear();
        if (contributions != null) {
            for (ToolbarEntry entry : contributions.model().toolbar()) {
''',
 r'''    /**
     * Rebuilds the plugin section after the built-in controls. A contributed view is built once, after this window
     * opened, and closed here when its entry goes or the window lets go of its contributions. A view's factory and its
     * close are contributor code that may change the contributions (register an action, enable one, add a toolbar
     * item), which renders the toolbar again from inside this pass: that nested call only marks the toolbar dirty, and
     * this pass then runs again with the latest model, as {@code renderContributedMenus} does.
     */
    void renderContributedToolbar() {
        toolbarDirty = true;
        if (renderingToolbar) return;
        renderingToolbar = true;
        try {
            do {
                toolbarDirty = false;
                rebuildContributedToolbar();
            } while (toolbarDirty);
        } finally {
            renderingToolbar = false;
        }
    }

    private void rebuildContributedToolbar() {
        contributedToolbar.forEach(toolbar::remove);
        contributedToolbar.clear();
        List<ToolbarEntry> entries = contributions == null ? List.of() : contributions.model().toolbar();
        // Not "live": the Dropdown case below declares its own local of that name.
        var liveViews = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ToolbarEntry.View, Boolean>());
        for (ToolbarEntry entry : entries) if (entry instanceof ToolbarEntry.View view) liveViews.add(view);
        for (var built = views.entrySet().iterator(); built.hasNext(); ) {
            var next = built.next();
            if (liveViews.contains(next.getKey())) continue;
            if (next.getValue() != null) next.getValue().close();
            built.remove();
        }
        if (contributions != null) {
            for (ToolbarEntry entry : entries) {
''', 1),
(r'''                        contributedToolbar.add(button);
                    }
                }
            }
            int index = toolbar.getComponentIndex(pluginSeparator) + 1;''',
 r'''                        contributedToolbar.add(button);
                    }
                    case ToolbarEntry.View view -> {
                        // Built once per window and only after it opened, so never for the warm-up rehearsal's window.
                        if (!opened) break;
                        if (!views.containsKey(view)) {
                            // Reserve the slot before the contributor's factory runs, so that no pass asks it twice.
                            views.put(view, null);
                            views.put(view, view.factory().apply(owner.id()).orElse(null));
                        }
                        ToolbarEntry.Mounted mounted = views.get(view);
                        if (mounted == null) break;
                        if (mounted.component() instanceof dev.jasper.ui.swing.KitView kit && !kit.isClosed()) kit.toolbarStrip(true);
                        WindowToolbar.view(mounted.component(), VIEW_PRIORITY);
                        contributedToolbar.add(mounted.component());
                    }
                }
            }
            int index = toolbar.getComponentIndex(pluginSeparator) + 1;''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java", [(
r'''        terminals = new WindowTerminals(this, Objects.requireNonNull(registry), Objects.requireNonNull(toFront));
    }
''',
r'''        terminals = new WindowTerminals(this, Objects.requireNonNull(registry), Objects.requireNonNull(toFront));
        chrome.windowOpened();
    }
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java", [(
r'''                                if (!live.isEmpty()) lines.add("menu:" + dropdown.title() + ":" + String.join(",", live));
                            }
''',
r'''                                if (!live.isEmpty()) lines.add("menu:" + dropdown.title() + ":" + String.join(",", live));
                            }
                            case dev.jasper.app.contributions.ToolbarEntry.View view -> lines.add("view");
''', 1)])
row = ("jasper-app/src/test/java/dev/jasper/app/workspace/WindowToolbarViewsTest.java,app.workspace,WindowToolbarViewsTest,"
       "nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/workspace/WindowToolbarViewsTest.java,"
       "Test/fixture ownership; New in Enterprise presentation Plan 3d; drives contributed toolbar views through a window's chrome with plain panels and one kit view\n")
csv = pathlib.Path("docs/design/theme-engine/migration.csv"); text = csv.read_text(encoding="utf-8")
if not text.endswith("\n"): text += "\n"
csv.write_text(text + row, encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test :jasper-ui:javadoc :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests dev.jasper.app.plugins.AppContractTest --tests dev.jasper.app.appearance.ThemeMigrationCoverageTest --tests dev.jasper.app.architecture.StaticDefaultTest --continue`
Expected: BUILD SUCCESSFUL. Both `KitViewToolbarStripTest` cases, the two new `WindowToolbarTest` cases and the five
`WindowToolbarViewsTest` cases pass; every existing toolbar, header and chrome test (`WindowToolbarTest`,
`WindowHeaderTest`, `WindowChromeContributionsTest`, `MacTitleBarTest`, `ViewModeChromeTest`) passes unchanged, since a
row without views lays out exactly as before.

Then check that nothing painted changed: render the headless gallery again and compare it with Step 1's baseline:

```bash
for theme in dark light; do
  rm -rf jasper-ui/build/gallery/$theme-1x
  ./gradlew -q :jasper-ui:gallery -Pjasper.galleryTheme=$theme
  diff -r jasper-ui/build/plan3d-gallery-base/$theme-1x jasper-ui/build/gallery/$theme-1x && echo "$theme: identical"
done
```

Expected: `dark: identical` and `light: identical`, with no `diff` output.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/contributions/ToolbarEntry.java jasper-app/src/main/java/dev/jasper/app/workspace \
  jasper-ui/src/main/java/dev/jasper/ui/swing/KitView.java jasper-ui/src/main/java/dev/jasper/ui/swing/Menus.java \
  jasper-ui/src/test/java/dev/jasper/ui/swing/KitViewToolbarStripTest.java jasper-app/src/test/java/dev/jasper/app/workspace/WindowToolbarViewsTest.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/WindowToolbarTest.java jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java \
  docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Toolbar views in the default header

The contributions model gains ToolbarEntry.View: a factory each window
asks once, with its id, when its open event fires (connectTerminals,
which the warm-up rehearsal never reaches), and closes what it got when
the window closes or the entry goes. WindowToolbar places a view at its
preferred size, centred in the row, and when the row is too narrow hides
it rather than moving it into the overflow menu. A kit view becomes a
toolbar strip, presenting an icon-only menu as a title strip does. A row
without views lays out exactly as before; the gallery is unchanged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: ToolbarItem.View in the SDK, the host and the fake

`ToolbarItem` gains `View(ToolbarViewFactory factory)` and `ToolbarItem.view(factory)` (spec 8.2). `HostedUi.toolbar`
translates it into Task 4's `ToolbarEntry.View`: each window's factory call is contained, its view is mounted on that
window's toolbar and admitted by `ToolbarViewAdmission` (spec 10.3; rulings 1 and 2), and later updates are admitted
too; the registration's close closes every window's view. The fake does the same through `FakeUi` and
`FakePluginHost.toolbarViews` (ruling 10), and closes a window's views when its last pane closes. The shared contracts
cover admission refusals, one view per window, closing with the window and the registration, and event routing.

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ToolbarViewFactory.java`, `jasper-app/src/main/java/dev/jasper/app/plugins/ToolbarViewAdmission.java`, `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/ToolbarViewAdmission.java`
- Modify (whole file): `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ToolbarItem.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java:224-238`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java:134,157,200-210`, `FakePluginHost.java:530-548`, `FakeWorkspace.java:130-131`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java:45-46`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java` (before `rail()`), `docs/design/theme-engine/migration.csv` (three rows appended)
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java` (one test), `PluginContractTest.java` (three tests and a helper, before `railButtonsNameThePluginsOwnActions`)

**Interfaces:**
- Consumes: Task 4's `ToolbarEntry.View(Function<UUID, Optional<ToolbarEntry.Mounted>>)`,
  `ToolbarEntry.Mounted(JComponent, Runnable)` and its `close()`. No 3b type: `Nodes.Separator` joins both admission
  switches when 3b and 3d are reconciled (Execution notes, R1).
- Produces: `dev.jasper.sdk.ui.ToolbarViewFactory` (`UiView create(WindowHandle window)`), `ToolbarItem.View(ToolbarViewFactory factory)`,
  `ToolbarItem.view(ToolbarViewFactory)`; `FakePluginHost.toolbarViews(UUID windowId)` (`List<FakeKitView>`) and
  `closeToolbarViews(UUID windowId)`; harness `openToolbarViews(UUID)` (`List<String>` of root ids),
  `closeToolbarViews(UUID)`, `clickToolbarViewButton(UUID, String buttonId)`; the toolbar line `view`.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3d Task 5 step 1: the SDK value test and the toolbar view contracts."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java", [(
r'''    @Test void toolbarItemsAndStatusSpecsValidate() {
''',
r'''    /** SDK 0.18: a toolbar view is a factory the host asks once per window. */
    @Test void aToolbarViewNeedsAFactory() {
        ToolbarViewFactory factory = window -> { throw new AssertionError("not called"); };
        assertThat(ToolbarItem.view(factory)).isEqualTo(new ToolbarItem.View(factory));
        assertThat(((ToolbarItem.View) ToolbarItem.view(factory)).factory()).isSameAs(factory);
        assertThatNullPointerException().isThrownBy(() -> new ToolbarItem.View(null));
    }

    @Test void toolbarItemsAndStatusSpecsValidate() {
''', 1)])
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java", [(
r'''    @Test void railButtonsNameThePluginsOwnActions() {
''',
r'''    /** A toolbar view tree the toolbar admits: a label, a host choice and a PRIMARY Connect. */
    private static dev.jasper.sdk.ui.ViewSpec sessionBar(String id) {
        var ready = dev.jasper.sdk.ui.ControlState.READY;
        return new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack(id, ready, dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE, List.of(
            new dev.jasper.sdk.ui.Nodes.Text(id + ".label", ready, "Session", dev.jasper.sdk.ui.Nodes.TextRole.LABEL),
            new dev.jasper.sdk.ui.Nodes.Choice(id + ".host", ready, dev.jasper.sdk.ui.Nodes.ChoiceKind.DROPDOWN,
                List.of(new dev.jasper.sdk.ui.Option("mac", "Mac", Optional.empty())), "mac", "Session", "host"),
            new dev.jasper.sdk.ui.Nodes.Button(id + ".connect", ready, "Connect", "", dev.jasper.sdk.ui.Nodes.ButtonKind.PRIMARY, "connect", ""))));
    }

    /** SDK 0.18: a toolbar view is built once per open window and closes with its window and its registration. */
    @Test void aToolbarViewIsBuiltOncePerWindowAndClosesWithItsWindowAndItsRegistration() {
        List<UUID> asked = Collections.synchronizedList(new ArrayList<>());
        List<dev.jasper.sdk.ui.UiView> made = Collections.synchronizedList(new ArrayList<>());
        var registration = new AtomicReference<Subscription>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> registration.set(context.toolbar().add(ToolbarItem.view(window -> {
            asked.add(window.id());
            var view = context.kit().create(sessionBar("bar"), event -> { });
            made.add(view);
            return view;
        }))));
        assertThat(h.toolbar()).containsExactly("view");
        var first = new AtomicReference<UUID>();
        var second = new AtomicReference<UUID>();
        h.ui(() -> { first.set(h.addTerminalWindow()); second.set(h.addTerminalWindow()); });
        assertThat(asked).as("nothing is built before a window asks").isEmpty();
        h.ui(() -> {
            assertThat(h.openToolbarViews(first.get())).containsExactly("bar");
            assertThat(h.openToolbarViews(first.get())).as("once per window").containsExactly("bar");
            assertThat(h.openToolbarViews(second.get())).containsExactly("bar");
        });
        assertThat(asked).containsExactly(first.get(), second.get());
        h.ui(() -> {
            h.closeToolbarViews(first.get());
            assertThatIllegalStateException().as("the view closes with its window").isThrownBy(() -> made.get(0).spec());
            assertThat(made.get(1).spec().root().id()).isEqualTo("bar");
        });
        h.ui(() -> registration.get().close());
        assertThat(h.toolbar()).isEmpty();
        h.ui(() -> assertThatIllegalStateException().as("and with its registration").isThrownBy(() -> made.get(1).spec()));
    }

    /** SDK 0.18: the toolbar admits an INLINE stack of labels, choices, one PRIMARY button and menu buttons. */
    @Test void aToolbarViewHoldsOnlyAnInlineStackOfAdmittedControls() {
        var ready = dev.jasper.sdk.ui.ControlState.READY;
        List<dev.jasper.sdk.ui.UiView> refused = Collections.synchronizedList(new ArrayList<>());
        var admitted = new AtomicReference<dev.jasper.sdk.ui.UiView>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.toolbar().add(ToolbarItem.view(window -> {
                var view = context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("plain", ready, "Session",
                    dev.jasper.sdk.ui.Nodes.TextRole.LABEL)), event -> { });
                refused.add(view);
                return view;
            }));
            context.toolbar().add(ToolbarItem.view(window -> {
                var view = context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("quiet", ready,
                    dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE, List.of(new dev.jasper.sdk.ui.Nodes.Button("quiet.edit", ready, "Edit", "",
                        dev.jasper.sdk.ui.Nodes.ButtonKind.SECONDARY, "edit", "")))), event -> { });
                refused.add(view);
                return view;
            }));
            context.toolbar().add(ToolbarItem.view(window -> { throw new IllegalStateException("factory failure"); }));
            context.toolbar().add(ToolbarItem.view(window -> {
                var view = context.kit().create(sessionBar("bar"), event -> { });
                admitted.set(view);
                return view;
            }));
        });
        var window = new AtomicReference<UUID>();
        h.ui(() -> window.set(h.addTerminalWindow()));
        h.ui(() -> {
            assertThat(h.openToolbarViews(window.get())).as("refused trees and a throwing factory show nothing").containsExactly("bar");
            assertThat(refused).hasSize(2);
            for (var view : refused) assertThatIllegalStateException().as("a refused view is closed").isThrownBy(view::spec);
            var twoPrimaries = new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("bar", ready, dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE, List.of(
                new dev.jasper.sdk.ui.Nodes.Button("bar.connect", ready, "Connect", "", dev.jasper.sdk.ui.Nodes.ButtonKind.PRIMARY, "connect", ""),
                new dev.jasper.sdk.ui.Nodes.Button("bar.split", ready, "Split", "", dev.jasper.sdk.ui.Nodes.ButtonKind.PRIMARY, "split", ""))));
            assertThatIllegalArgumentException().isThrownBy(() -> admitted.get().update(twoPrimaries)).withMessageContaining("bar.split");
            assertThat(admitted.get().spec()).as("a refused update keeps the tree").isEqualTo(sessionBar("bar"));
        });
        assertThat(h.active("test.alpha")).as("refusals are contained").isTrue();
    }

    /** SDK 0.18: a toolbar view's controls report to the plugin that built it. */
    @Test void aToolbarViewsControlsReportToThePluginThatBuiltIt() {
        List<dev.jasper.sdk.ui.UiEvent> seen = new CopyOnWriteArrayList<>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context ->
            context.toolbar().add(ToolbarItem.view(window -> context.kit().create(sessionBar("bar"), seen::add))));
        var window = new AtomicReference<UUID>();
        h.ui(() -> window.set(h.addTerminalWindow()));
        h.ui(() -> {
            assertThat(h.openToolbarViews(window.get())).containsExactly("bar");
            h.clickToolbarViewButton(window.get(), "bar.connect");
        });
        assertThat(seen).containsExactly(new dev.jasper.sdk.ui.UiEvent.Action("connect"));
    }

    @Test void railButtonsNameThePluginsOwnActions() {
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.UiValuesTest :jasper-sdk-testkit:test --tests dev.jasper.sdk.testing.FakeContractTest --continue`
Expected: FAIL. `:jasper-sdk:compileTestJava` stops with "cannot find symbol … ToolbarViewFactory" in `UiValuesTest`;
`:jasper-sdk-testkit:compileTestFixturesJava` stops on `ToolbarItem.view`, `openToolbarViews`, `closeToolbarViews` and
`clickToolbarViewButton` in `PluginContractTest`.

- [ ] **Step 3: The SDK item**

Create `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ToolbarViewFactory.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.terminal.WindowHandle;

/**
 * Builds one window's toolbar view (see {@link ToolbarItem.View}). The host calls it on the UI thread, once per window,
 * when the window opens; a factory that throws is contained, and that window shows no view.
 *
 * @since 0.18.0
 */
@FunctionalInterface
public interface ToolbarViewFactory {
    /**
     * The window's view, made with this plugin's {@link UiKit} and shown nowhere else.
     *
     * @param window the window the view belongs to
     * @return the view
     */
    UiView create(WindowHandle window);
}
```

Replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ToolbarItem.java` with:

```java
package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Objects;

/** Something a plugin places in the main toolbar. The application draws it in the toolbar's own style. */
public sealed interface ToolbarItem permits ToolbarItem.Button, ToolbarItem.Dropdown, ToolbarItem.View {
    /**
     * A button for one action, using the action's title and icon.
     *
     * @param actionId an action this plugin registered
     */
    record Button(String actionId) implements ToolbarItem {
        /** Rejects a null id. */
        public Button { Objects.requireNonNull(actionId, "actionId"); }
    }

    /**
     * A button that opens a menu of actions.
     *
     * @param icon the button's icon
     * @param title non-blank label
     * @param actionIds one or more actions this plugin registered; copied
     */
    record Dropdown(String icon, String title, List<String> actionIds) implements ToolbarItem {
        /** Validates and copies. */
        public Dropdown {
            Objects.requireNonNull(icon, "icon");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("A toolbar menu needs a title");
            actionIds = List.copyOf(Objects.requireNonNull(actionIds, "actionIds"));
            if (actionIds.isEmpty()) throw new IllegalArgumentException("A toolbar menu needs at least one action");
        }
    }

    /**
     * A small group of the plugin's own controls, one per window, such as a session choice with a Connect button. The
     * factory runs when a window opens, never for a window nobody sees, and the view closes with its window or with this
     * registration. Its root is an INLINE {@link Nodes.Stack} of LABEL {@link Nodes.Text}s, DROPDOWN, SEARCHABLE or
     * PICKER {@link Nodes.Choice}s, at most one PRIMARY {@link Nodes.Button} and {@link Nodes.MenuButton}s: the host
     * refuses any other tree, naming the node, when it mounts the view and on every {@link UiView#update}. A view never moves into the overflow menu: it hides while the toolbar is too narrow for it.
     *
     * @param factory builds one window's view
     * @since 0.18.0
     */
    record View(ToolbarViewFactory factory) implements ToolbarItem {
        /** Rejects a null factory. */
        public View { Objects.requireNonNull(factory, "factory"); }
    }

    /**
     * A button for one action.
     *
     * @param actionId an action this plugin registered
     * @return the item
     */
    static ToolbarItem action(String actionId) { return new Button(actionId); }

    /**
     * A button that opens a menu of actions.
     *
     * @param icon the button's icon
     * @param title non-blank label
     * @param actionIds one or more actions this plugin registered
     * @return the item
     */
    static ToolbarItem menu(String icon, String title, List<String> actionIds) { return new Dropdown(icon, title, actionIds); }

    /**
     * A group of the plugin's own controls, one per window.
     *
     * @param factory builds one window's view
     * @return the item
     * @since 0.18.0
     */
    static ToolbarItem view(ToolbarViewFactory factory) { return new View(factory); }
}
```

- [ ] **Step 4: The admission rule, the host, the fake and the harnesses**

Create `jasper-app/src/main/java/dev/jasper/app/plugins/ToolbarViewAdmission.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.ViewSpec;

/**
 * What a toolbar view may hold (spec 10.3): an INLINE stack of LABEL text, DROPDOWN, SEARCHABLE or PICKER choices, at
 * most one PRIMARY button and menu buttons. The testkit keeps the same rule; the shared contracts check both.
 */
final class ToolbarViewAdmission {
    private ToolbarViewAdmission() { }

    /** Throws {@link IllegalArgumentException} naming the first node a toolbar does not admit. */
    static void require(ViewSpec spec) {
        if (!(spec.root() instanceof Nodes.Stack stack) || stack.role() != Nodes.LayoutRole.INLINE)
            throw new IllegalArgumentException("A toolbar view's root must be an INLINE Stack, not " + describe(spec.root()));
        int primaries = 0;
        for (Nodes.Node child : stack.children()) {
            boolean admitted = switch (child) {
                case Nodes.Text text -> text.role() == Nodes.TextRole.LABEL;
                case Nodes.Choice choice -> choice.kind() == Nodes.ChoiceKind.DROPDOWN || choice.kind() == Nodes.ChoiceKind.SEARCHABLE
                    || choice.kind() == Nodes.ChoiceKind.PICKER;
                case Nodes.Button button -> button.kind() == Nodes.ButtonKind.PRIMARY;
                case Nodes.MenuButton ignored -> true;
                default -> false;
            };
            if (!admitted) throw new IllegalArgumentException("A toolbar view does not admit " + describe(child));
            if (child instanceof Nodes.Button && ++primaries > 1)
                throw new IllegalArgumentException("A toolbar view has at most one PRIMARY Button: " + describe(child));
        }
    }

    private static String describe(Nodes.Node node) {
        String kind = switch (node) {
            case Nodes.Text text -> "a " + text.role() + " Text";
            case Nodes.Choice choice -> "a " + choice.kind() + " Choice";
            case Nodes.Button button -> "a " + button.kind() + " Button";
            case Nodes.Stack stack -> "a " + stack.role() + " Stack";
            default -> "a " + node.getClass().getSimpleName();
        };
        return kind + " (" + node.id() + ")";
    }
}
```

Create `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/ToolbarViewAdmission.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.ViewSpec;

/**
 * The fake's copy of the host's toolbar view rule (spec 10.3): an INLINE stack of LABEL text, DROPDOWN, SEARCHABLE or
 * PICKER choices, at most one PRIMARY button and menu buttons. The shared contracts keep the two in step.
 */
final class ToolbarViewAdmission {
    private ToolbarViewAdmission() { }

    /** Throws {@link IllegalArgumentException} naming the first node a toolbar does not admit. */
    static void require(ViewSpec spec) {
        if (!(spec.root() instanceof Nodes.Stack stack) || stack.role() != Nodes.LayoutRole.INLINE)
            throw new IllegalArgumentException("A toolbar view's root must be an INLINE Stack, not " + describe(spec.root()));
        int primaries = 0;
        for (Nodes.Node child : stack.children()) {
            boolean admitted = switch (child) {
                case Nodes.Text text -> text.role() == Nodes.TextRole.LABEL;
                case Nodes.Choice choice -> choice.kind() == Nodes.ChoiceKind.DROPDOWN || choice.kind() == Nodes.ChoiceKind.SEARCHABLE
                    || choice.kind() == Nodes.ChoiceKind.PICKER;
                case Nodes.Button button -> button.kind() == Nodes.ButtonKind.PRIMARY;
                case Nodes.MenuButton ignored -> true;
                default -> false;
            };
            if (!admitted) throw new IllegalArgumentException("A toolbar view does not admit " + describe(child));
            if (child instanceof Nodes.Button && ++primaries > 1)
                throw new IllegalArgumentException("A toolbar view has at most one PRIMARY Button: " + describe(child));
        }
    }

    private static String describe(Nodes.Node node) {
        String kind = switch (node) {
            case Nodes.Text text -> "a " + text.role() + " Text";
            case Nodes.Choice choice -> "a " + choice.kind() + " Choice";
            case Nodes.Button button -> "a " + button.kind() + " Button";
            case Nodes.Stack stack -> "a " + stack.role() + " Stack";
            default -> "a " + node.getClass().getSimpleName();
        };
        return kind + " (" + node.id() + ")";
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3d Task 5 step 4: HostedUi's translation, the fake, both harnesses and the ledger rows."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java", [(
r'''    Toolbar toolbar() {
        return item -> {
            guard("add");
            ToolbarEntry entry = switch (item) {
                case ToolbarItem.Button button -> { requireOwn(button.actionId()); yield new ToolbarEntry.Button(button.actionId()); }
                case ToolbarItem.Dropdown dropdown -> {
                    dropdown.actionIds().forEach(this::requireOwn);
                    yield new ToolbarEntry.Dropdown(icon(dropdown.icon()), dropdown.title(), dropdown.actionIds());
                }
            };
            var registration = model.addToolbar(entry);
            return tracked(registration::close);
        };
    }
''',
r'''    Toolbar toolbar() {
        return item -> {
            guard("add");
            return switch (item) {
                case ToolbarItem.Button button -> { requireOwn(button.actionId()); yield placed(new ToolbarEntry.Button(button.actionId())); }
                case ToolbarItem.Dropdown dropdown -> {
                    dropdown.actionIds().forEach(this::requireOwn);
                    yield placed(new ToolbarEntry.Dropdown(icon(dropdown.icon()), dropdown.title(), dropdown.actionIds()));
                }
                case ToolbarItem.View view -> toolbarView(view.factory());
            };
        };
    }

    private Subscription placed(ToolbarEntry entry) {
        var registration = model.addToolbar(entry);
        return tracked(registration::close);
    }

    /** A toolbar view's mount: the main toolbar of one terminal window. */
    private record ToolbarMount(java.util.UUID windowId) { }

    /**
     * A toolbar view: each window builds its own once, when it opens, and closing the registration closes every window's.
     * A view is admitted ({@link ToolbarViewAdmission}) when it is built and on every update after that.
     */
    private Subscription toolbarView(dev.jasper.sdk.ui.ToolbarViewFactory factory) {
        var built = new java.util.LinkedHashMap<java.util.UUID, HostedKitView>();
        var registration = model.addToolbar(new ToolbarEntry.View(windowId -> buildToolbarView(factory, windowId, built)));
        return tracked(() -> {
            registration.close();
            for (var view : List.copyOf(built.values())) view.close();
            built.clear();
        });
    }

    /**
     * One window's view, contained: empty when the factory threw or the toolbar does not admit its tree, and empty for
     * a window that already holds this registration's view (one built while the factory ran is kept, the newer closed).
     */
    private Optional<ToolbarEntry.Mounted> buildToolbarView(dev.jasper.sdk.ui.ToolbarViewFactory factory, java.util.UUID windowId,
                                                            java.util.Map<java.util.UUID, HostedKitView> built) {
        if (built.containsKey(windowId)) return Optional.empty();
        var mounted = new java.util.concurrent.atomic.AtomicReference<HostedKitView>();
        containment.run(pluginId, "toolbar view", () -> {
            var view = kit.requireOwned(java.util.Objects.requireNonNull(factory.create(terminals.windowHandle(windowId)), "view"));
            if (built.containsKey(windowId)) { view.close(); return; }
            view.mount(new ToolbarMount(windowId));
            try {
                ToolbarViewAdmission.require(view.spec());
                view.hostSpec = spec -> { ToolbarViewAdmission.require(spec); return spec; };
            } catch (RuntimeException refused) { view.close(); throw refused; }
            mounted.set(view);
        });
        var hosted = mounted.get();
        if (hosted == null) return Optional.empty();
        built.put(windowId, hosted);
        return Optional.of(new ToolbarEntry.Mounted(hosted.component(), () -> { built.remove(windowId, hosted); hosted.close(); }));
    }
''', 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java", [
(r'''    final List<ToolbarItem> toolbar = new ArrayList<>();
''',
 r'''    final List<ToolbarItem> toolbar = new ArrayList<>();
    /** Each toolbar view's built views, per terminal window; empty where the factory threw or the tree was refused. */
    final Map<ToolbarItem.View, Map<UUID, Optional<FakeKitView>>> toolbarBuilt = new java.util.IdentityHashMap<>();
    /** A toolbar view's mount: the toolbar of one terminal window. */
    record ToolbarMount(UUID windowId) { }
''', 1),
(r'''        actions.clear(); toolbar.clear(); sections.clear(); topLevelIds.clear(); status.clear();
''',
 r'''        for (var built : toolbarBuilt.values()) for (var view : built.values()) view.ifPresent(FakeKitView::close);
        toolbarBuilt.clear();
        actions.clear(); toolbar.clear(); sections.clear(); topLevelIds.clear(); status.clear();
''', 1),
(r'''    Toolbar toolbar() {
        return item -> {
            context.requireOpen();
            switch (item) {
                case ToolbarItem.Button button -> requireOwn(button.actionId());
                case ToolbarItem.Dropdown dropdown -> { context.kit().validateIcon(dropdown.icon());dropdown.actionIds().forEach(this::requireOwn); }
            }
            toolbar.add(item);
            return () -> { for (int i = 0; i < toolbar.size(); i++) if (toolbar.get(i) == item) { toolbar.remove(i); break; } };
        };
    }
''',
 r'''    Toolbar toolbar() {
        return item -> {
            context.requireOpen();
            switch (item) {
                case ToolbarItem.Button button -> requireOwn(button.actionId());
                case ToolbarItem.Dropdown dropdown -> { context.kit().validateIcon(dropdown.icon());dropdown.actionIds().forEach(this::requireOwn); }
                // A view's tree is admitted when a window builds it, and on every update after that.
                case ToolbarItem.View view -> { }
            }
            toolbar.add(item);
            return () -> {
                for (int i = 0; i < toolbar.size(); i++) if (toolbar.get(i) == item) { toolbar.remove(i); break; }
                if (item instanceof ToolbarItem.View view) {
                    var built = toolbarBuilt.remove(view);
                    if (built != null) for (var each : built.values()) each.ifPresent(FakeKitView::close);
                }
            };
        };
    }

    /** UI thread: the window's toolbar views in toolbar order, each built once for the window, as it builds them when it opens. */
    List<FakeKitView> toolbarViews(UUID windowId) {
        List<FakeKitView> views = new ArrayList<>();
        for (ToolbarItem item : List.copyOf(toolbar)) {
            if (!(item instanceof ToolbarItem.View view)) continue;
            var built = toolbarBuilt.computeIfAbsent(view, key -> new LinkedHashMap<>());
            if (!built.containsKey(windowId)) built.put(windowId, buildToolbarView(view, windowId));
            built.get(windowId).ifPresent(views::add);
        }
        return views;
    }

    /** Closes the window's toolbar views, as the window does when it closes. */
    void closeToolbarViews(UUID windowId) {
        for (var built : toolbarBuilt.values()) {
            var view = built.remove(windowId);
            if (view != null) view.ifPresent(FakeKitView::close);
        }
    }

    /** One window's view, admitted and mounted; empty, with the failure recorded, when the factory or the admission failed. */
    private Optional<FakeKitView> buildToolbarView(ToolbarItem.View item, UUID windowId) {
        try {
            var view = context.kit().requireOwned(java.util.Objects.requireNonNull(item.factory().create(context.terminals.windowHandle(windowId)), "view"));
            view.mount(new ToolbarMount(windowId));
            try {
                ToolbarViewAdmission.require(view.spec());
                view.hostSpec = spec -> { ToolbarViewAdmission.require(spec); return spec; };
            } catch (RuntimeException refused) { view.close(); throw refused; }
            return Optional.of(view);
        } catch (RuntimeException | LinkageError failure) {
            host.recordFailure(pluginId() + " toolbar view: " + failure);
            return Optional.empty();
        }
    }
''', 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java", [(
r'''     * @return {@code button:id} or {@code menu:title:id,id}; closed actions are omitted
     */
    public List<String> toolbar() {
        List<String> lines = new ArrayList<>();
        for (FakePluginContext context : contexts.values()) {
            for (ToolbarItem item : context.ui.toolbar) {
                switch (item) {
                    case ToolbarItem.Button button -> { if (actionExists(button.actionId())) lines.add("button:" + button.actionId()); }
                    case ToolbarItem.Dropdown dropdown -> {
                        List<String> live = dropdown.actionIds().stream().filter(this::actionExists).toList();
                        if (!live.isEmpty()) lines.add("menu:" + dropdown.title() + ":" + String.join(",", live));
                    }
                }
            }
        }
        return lines;
    }
''',
r'''     * @return {@code button:id}, {@code menu:title:id,id} or {@code view}; closed actions are omitted
     */
    public List<String> toolbar() {
        List<String> lines = new ArrayList<>();
        for (FakePluginContext context : contexts.values()) {
            for (ToolbarItem item : context.ui.toolbar) {
                switch (item) {
                    case ToolbarItem.Button button -> { if (actionExists(button.actionId())) lines.add("button:" + button.actionId()); }
                    case ToolbarItem.Dropdown dropdown -> {
                        List<String> live = dropdown.actionIds().stream().filter(this::actionExists).toList();
                        if (!live.isEmpty()) lines.add("menu:" + dropdown.title() + ":" + String.join(",", live));
                    }
                    case ToolbarItem.View view -> lines.add("view");
                }
            }
        }
        return lines;
    }

    /**
     * UI thread: the toolbar views a terminal window shows, in toolbar order. The window builds each registered
     * {@code ToolbarItem.View} once, the first time this is asked for it, as a window builds its views when it opens; a
     * view whose factory threw or whose tree the toolbar does not admit is left out and recorded in {@link #failures()}.
     *
     * @param windowId a terminal window
     * @return the live views
     * @since 0.18.0
     */
    public List<FakeKitView> toolbarViews(UUID windowId) {
        List<FakeKitView> views = new ArrayList<>();
        for (FakePluginContext context : contexts.values()) views.addAll(context.ui.toolbarViews(windowId));
        return views;
    }

    /**
     * UI thread: closes a window's toolbar views, as the window does when it closes; a fake window does when its last
     * pane closes.
     *
     * @param windowId a terminal window
     * @since 0.18.0
     */
    public void closeToolbarViews(UUID windowId) {
        for (FakePluginContext context : contexts.values()) context.ui.closeToolbarViews(windowId);
    }
''', 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeWorkspace.java", [(
r'''                if (lastActive == window) lastActive = null;
                host.publishApp(TerminalEvents.WINDOW_CLOSED, new TerminalEvents.WindowEvent(window.id));
''',
r'''                if (lastActive == window) lastActive = null;
                host.closeToolbarViews(window.id);
                host.publishApp(TerminalEvents.WINDOW_CLOSED, new TerminalEvents.WindowEvent(window.id));
''', 1)])
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java", [(
r'''    /** The toolbar's plugin section: {@code button:id} or {@code menu:title:id,id}; closed actions omitted. */
    List<String> toolbar();
''',
r'''    /** The toolbar's plugin section: {@code button:id}, {@code menu:title:id,id} or {@code view}; closed actions omitted. */
    List<String> toolbar();

    /**
     * UI thread: builds a window's toolbar views as the window does when it opens, once per window (a second call reports
     * the same views), and reports each built view as its root's id, in toolbar order; a view whose factory threw or whose
     * tree the toolbar does not admit is left out.
     */
    List<String> openToolbarViews(java.util.UUID windowId);

    /** UI thread: closes a window's toolbar views, as the window does when it closes. */
    void closeToolbarViews(java.util.UUID windowId);

    /** UI thread: clicks the button with this node id in one of a window's open toolbar views, as the user would. */
    void clickToolbarViewButton(java.util.UUID windowId, String buttonId);
''', 1)])
edit("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java", [(
r'''            @Override public List<String> toolbar() { return host.toolbar(); }
''',
r'''            @Override public List<String> toolbar() { return host.toolbar(); }
            @Override public List<String> openToolbarViews(java.util.UUID windowId) {
                return host.toolbarViews(windowId).stream().map(view -> view.spec().root().id()).toList();
            }
            @Override public void closeToolbarViews(java.util.UUID windowId) { host.closeToolbarViews(windowId); }
            @Override public void clickToolbarViewButton(java.util.UUID windowId, String buttonId) {
                for (var view : host.toolbarViews(windowId))
                    for (var child : ((dev.jasper.sdk.ui.Nodes.Stack) view.spec().root()).children())
                        if (child instanceof dev.jasper.sdk.ui.Nodes.Button button && button.id().equals(buttonId)) {
                            view.emit(new dev.jasper.sdk.ui.UiEvent.Action(button.action()));
                            return;
                        }
                throw new AssertionError("No toolbar view button " + buttonId + " in " + windowId);
            }
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java", [(
r'''            @Override public List<String> rail() {
''',
r'''            /** Each toolbar view's instance per window, built once as a window builds it when it opens; empty when it failed. */
            final java.util.Map<dev.jasper.app.contributions.ToolbarEntry.View, java.util.Map<UUID, Optional<dev.jasper.app.contributions.ToolbarEntry.Mounted>>> toolbarViews =
                new java.util.IdentityHashMap<>();
            @Override public List<String> openToolbarViews(UUID windowId) {
                return onEdtValue(() -> {
                    List<String> roots = new java.util.ArrayList<>();
                    for (var entry : contributions.toolbar()) {
                        if (!(entry instanceof dev.jasper.app.contributions.ToolbarEntry.View view)) continue;
                        var mounted = toolbarViews.computeIfAbsent(view, key -> new java.util.HashMap<>())
                            .computeIfAbsent(windowId, id -> view.factory().apply(id));
                        mounted.ifPresent(built -> roots.add(((dev.jasper.ui.swing.KitView) built.component()).spec().root().id()));
                    }
                    return roots;
                });
            }
            @Override public void closeToolbarViews(UUID windowId) {
                onEdt(() -> {
                    for (var built : toolbarViews.values()) {
                        var mounted = built.remove(windowId);
                        if (mounted != null) mounted.ifPresent(dev.jasper.app.contributions.ToolbarEntry.Mounted::close);
                    }
                });
            }
            @Override public void clickToolbarViewButton(UUID windowId, String buttonId) {
                onEdt(() -> {
                    for (var built : toolbarViews.values()) {
                        var button = built.getOrDefault(windowId, Optional.empty())
                            .map(mounted -> WorkspaceContentRouteTest.named(mounted.component(), buttonId, javax.swing.AbstractButton.class)).orElse(null);
                        if (button != null) { button.doClick(); return; }
                    }
                    throw new AssertionError("No toolbar view button " + buttonId + " in " + windowId);
                });
            }
            @Override public List<String> rail() {
''', 1)])
rows = [
    "jasper-sdk/src/main/java/dev/jasper/sdk/ui/ToolbarViewFactory.java,sdk,ToolbarViewFactory,nonvisual-helper,none,none,"
    "jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java,New in Enterprise presentation Plan 3d; builds one window's toolbar view from the plugin's own kit\n",
    "jasper-app/src/main/java/dev/jasper/app/plugins/ToolbarViewAdmission.java,app.plugins,ToolbarViewAdmission,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java,New in Enterprise presentation Plan 3d; the toolbar view admission rule (spec 10.3) over descriptors; shared host/fake contracts\n",
    "jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/ToolbarViewAdmission.java,sdk.testkit,ToolbarViewAdmission,nonvisual-helper,none,none,"
    "jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java,New in Enterprise presentation Plan 3d; fake toolbar view admission mirrors the host; shared contracts\n",
]
csv = pathlib.Path("docs/design/theme-engine/migration.csv"); text = csv.read_text(encoding="utf-8")
if not text.endswith("\n"): text += "\n"
csv.write_text(text + "".join(rows), encoding="utf-8")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk:javadoc :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.workspace.*' --tests dev.jasper.app.appearance.ThemeMigrationCoverageTest verifySdkArchitecture --continue`
Expected: BUILD SUCCESSFUL. `aToolbarViewNeedsAFactory` and the three toolbar view contracts pass under both harnesses;
`ThemeMigrationCoverageTest` finds the three new rows; every existing toolbar contract (`placementsNameOnlyThePluginsOwnActionsAndVanishWithThem`,
`stoppingOrFailingRemovesEveryContributionAndAppearanceFollowsTheHost`) passes unchanged.

- [ ] **Step 6: Commit**

```bash
git add jasper-sdk/src jasper-sdk-testkit/src jasper-app/src/main/java/dev/jasper/app/plugins \
  jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
SDK 0.18: toolbar views

ToolbarItem gains View, a ToolbarViewFactory the host asks once per
window when the window opens. HostedUi contains each call, mounts the
plugin's view on that window's toolbar and admits its tree: an INLINE
stack of labels, choices, at most one PRIMARY button and menu buttons,
at mount and on every update, naming any refused node. The
registration's close, and the window's, close the view. The fake builds
views the same way (FakePluginHost.toolbarViews) and the shared contracts
cover admission, lifetime and event routing in both runtimes.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: The bundled scopes' nouns, hues and split verb

Spec 14.6 and 14.4's last line, through Task 2's API: Remote `itemNoun "session"`, hue GREEN and its "Connect in split"
verb SPLIT; Snippets "snippet" PURPLE; Indexer Files "file" AMBER; Editor Recent files "file" AMBER; History "command"
PURPLE; Remote tunnels "tunnel"; Sample "greeting"; App Commands "command" BLUE; Vault "login"; File Server "file
server". Verb ids and labels do not change (Snippets' "Insert" is plan 3e's).

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/RemoteScope.java:31,61`, `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsScope.java:50`, `plugins/snippets/src/main/java/dev/jasper/snippets/SnippetsScope.java:39`, `plugins/indexer/src/main/java/dev/jasper/indexer/ui/FilesScope.java:90`, `plugins/editor/src/main/java/dev/jasper/editor/recent/RecentFilesScope.java:72`, `plugins/history/src/main/java/dev/jasper/history/HistoryScope.java:48`, `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java:221`, `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultScope.java:54`, `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/FileServerScope.java:43`, `jasper-app/src/main/java/dev/jasper/app/palette/CommandsScope.java:48`
- Modify: `docs/plugin-authoring.md:443` (the `pluginpalette` example's copy)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java:296`, `plugins/remote/src/test/java/dev/jasper/remote/TunnelsPluginTest.java:85`, `plugins/snippets/src/test/java/dev/jasper/snippets/SnippetsScopeTest.java:70`, `plugins/indexer/src/test/java/dev/jasper/indexer/ui/FilesScopeTest.java:64`, `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java:73`, `plugins/history/src/test/java/dev/jasper/history/HistoryScopeTest.java:46`, `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java:273`, `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java:95`, `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/FileServerScopeTest.java:53`, `jasper-app/src/test/java/dev/jasper/app/palette/CommandsScopeTest.java` (one test)

**Interfaces:**
- Consumes: Task 2's `ScopeSpec.withHue`, `withItemNoun`, `PaletteVerb.Role.SPLIT`, app `PaletteScope.hue()`,
  `itemNoun()`, and `FakePluginHost.scopePresentation` (`HUE|itemNoun|ROLE,ROLE`, NONE read as NEUTRAL).
- Produces: nothing new.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3d Task 6 step 1: each bundled scope's presentation, as spec 14.6 gives it."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
def after(name, anchor, line):
    edit(name, [(anchor, anchor + line, 1)])
after("plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java",
      '                "dev.jasper.remote.scope|SSH|connect,split,edit");\n',
      '            assertThat(host.scopePresentation("dev.jasper.remote.scope")).as("spec 14.4").isEqualTo("GREEN|session|STANDARD,SPLIT,STANDARD");\n')
after("plugins/remote/src/test/java/dev/jasper/remote/TunnelsPluginTest.java",
      '            assertThat(host.scopes()).contains(TunnelsScope.ID + "|Tunnels|toggle,browse,edit");\n',
      '            assertThat(host.scopePresentation(TunnelsScope.ID)).as("spec 14.6").isEqualTo("NEUTRAL|tunnel|STANDARD,STANDARD,STANDARD");\n')
after("plugins/snippets/src/test/java/dev/jasper/snippets/SnippetsScopeTest.java",
      '            assertThat(host.scopes()).containsExactly(SCOPE + "|Snippets|paste,paste_run,edit");\n',
      '            assertThat(host.scopePresentation(SCOPE)).as("spec 14.6").isEqualTo("PURPLE|snippet|STANDARD,STANDARD,STANDARD");\n')
after("plugins/indexer/src/test/java/dev/jasper/indexer/ui/FilesScopeTest.java",
      '            assertThat(host.scopes()).containsExactly("dev.jasper.indexer.files|Files|open,copy-path,terminal");\n',
      '            assertThat(host.scopePresentation(FilesScope.ID)).as("spec 14.6").isEqualTo("AMBER|file|STANDARD,STANDARD,STANDARD");\n')
after("plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java",
      '            assertThat(host.scopes()).contains(RECENT + "|Recent files|open,remove");\n',
      '            assertThat(host.scopePresentation(RECENT)).as("spec 14.6").isEqualTo("AMBER|file|STANDARD,STANDARD");\n')
after("plugins/history/src/test/java/dev/jasper/history/HistoryScopeTest.java",
      '            assertThat(host.scopes()).containsExactly(SCOPE + "|History|paste,paste_run");\n',
      '            assertThat(host.scopePresentation(SCOPE)).as("spec 14.6").isEqualTo("PURPLE|command|STANDARD,STANDARD");\n')
after("plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java",
      '                assertThat(host.scopes()).containsExactly("dev.jasper.sample.greetings|Greetings|paste,paste_run");\n',
      '                assertThat(host.scopePresentation("dev.jasper.sample.greetings")).as("spec 14.6").isEqualTo("NEUTRAL|greeting|STANDARD,STANDARD");\n')
after("plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java",
      '            assertThat(host.scopes()).containsExactly("dev.jasper.vault.scope|Vault|copy_password,copy_username,open");\n',
      '            assertThat(host.scopePresentation("dev.jasper.vault.scope")).as("spec 14.6").isEqualTo("NEUTRAL|login|STANDARD,STANDARD,STANDARD");\n')
after("plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/FileServerScopeTest.java",
      '        assertThat(scope.spec().shortcutActionId()).contains("dev.jasper.fileserver.servers");\n',
      '        assertThat(scope.spec().itemNoun()).as("spec 14.5").isEqualTo("file server");\n'
      '        assertThat(scope.spec().hue()).isEqualTo(dev.jasper.sdk.ui.Nodes.Hue.NONE);\n')
edit("jasper-app/src/test/java/dev/jasper/app/palette/CommandsScopeTest.java", [(
r'''    @Test void emptyQueryShowsStartersThenRecentsAndSearchRowsCarryShortcuts() throws Exception {
''',
r'''    /** Spec 14.6: the App Commands scope's rows are blue commands, with no split verb. */
    @Test void commandRowsAreBlueCommands() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new CommandRegistry(); var history = new CommandHistory()) {
                var scope = new CommandsScope(registry, history, true, command -> { }, null);
                assertThat(scope.hue()).isEqualTo(dev.jasper.ui.model.Nodes.Hue.BLUE);
                assertThat(scope.itemNoun()).isEqualTo("command");
                assertThat(scope.verbs()).extracting(PaletteVerb::role).containsExactly(PaletteVerb.Role.STANDARD);
            }
        });
    }

    @Test void emptyQueryShowsStartersThenRecentsAndSearchRowsCarryShortcuts() throws Exception {
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test :jasper-plugin-snippets:test :jasper-plugin-indexer:test :jasper-plugin-editor:test :jasper-plugin-history:test :jasper-plugin-sample:test :jasper-plugin-vault:test :jasper-plugin-fileserver:test :jasper-app:test --tests dev.jasper.app.palette.CommandsScopeTest --continue`
Expected: FAIL. Each plugin's new assertion fails with the scope's current presentation (for example "expected
GREEN|session|STANDARD,SPLIT,STANDARD but was NEUTRAL|SSH|STANDARD,STANDARD,STANDARD"), `FileServerScopeTest` with
"expected file server but was \"\"", and `commandRowsAreBlueCommands` with "expected BLUE but was NEUTRAL".

- [ ] **Step 3: Give each scope its noun, hue and roles**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3d Task 6 step 3: spec 14.6's nouns, hues and Remote's SPLIT verb."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("plugins/remote/src/main/java/dev/jasper/remote/ui/RemoteScope.java", [
    ('    public static final PaletteVerb SPLIT = new PaletteVerb("split", "Connect in split");',
     '    public static final PaletteVerb SPLIT = new PaletteVerb("split", "Connect in split", PaletteVerb.Role.SPLIT);', 1),
    ('            .withDescription("Connect to a saved SSH host").withShortcutActionId(CONNECT_ACTION);',
     '            .withDescription("Connect to a saved SSH host").withShortcutActionId(CONNECT_ACTION)\n'
     '            .withHue(dev.jasper.sdk.ui.Nodes.Hue.GREEN).withItemNoun("session");', 1)])
edit("plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsScope.java", [
    ('List.of(TOGGLE, BROWSE, EDIT)).withDescription("Start, stop or edit an SSH tunnel");',
     'List.of(TOGGLE, BROWSE, EDIT)).withDescription("Start, stop or edit an SSH tunnel")\n'
     '            .withItemNoun("tunnel");', 1)])
edit("plugins/snippets/src/main/java/dev/jasper/snippets/SnippetsScope.java", [
    ('            .withDescription("Paste or run a saved command").withShortcutActionId(openActionId);',
     '            .withDescription("Paste or run a saved command").withShortcutActionId(openActionId)\n'
     '            .withHue(dev.jasper.sdk.ui.Nodes.Hue.PURPLE).withItemNoun("snippet");', 1)])
edit("plugins/indexer/src/main/java/dev/jasper/indexer/ui/FilesScope.java", [
    ('            .withShortcutActionId(shortcutActionId).withInAll(true).withInRecents(true);',
     '            .withShortcutActionId(shortcutActionId).withInAll(true).withInRecents(true)\n'
     '            .withHue(dev.jasper.sdk.ui.Nodes.Hue.AMBER).withItemNoun("file");', 1)])
edit("plugins/editor/src/main/java/dev/jasper/editor/recent/RecentFilesScope.java", [
    ('            .withShortcutActionId(ACTION).withInAll(true).withInRecents(true);',
     '            .withShortcutActionId(ACTION).withInAll(true).withInRecents(true)\n'
     '            .withHue(dev.jasper.sdk.ui.Nodes.Hue.AMBER).withItemNoun("file");', 1)])
edit("plugins/history/src/main/java/dev/jasper/history/HistoryScope.java", [
    ('            .withMonospaceRows(true).withShortcutActionId(openActionId)\n',
     '            .withMonospaceRows(true).withShortcutActionId(openActionId)\n'
     '            .withHue(dev.jasper.sdk.ui.Nodes.Hue.PURPLE).withItemNoun("command")\n', 1)])
for name in ("plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java", "docs/plugin-authoring.md"):
    edit(name, [('.withShortcutActionId(GREETINGS_OPEN);', '.withShortcutActionId(GREETINGS_OPEN).withItemNoun("greeting");', 1)])
edit("plugins/vault/src/main/java/dev/jasper/vault/ui/VaultScope.java", [
    ('.withShortcutActionId(SEARCH_ACTION).withInAll(false);',
     '.withShortcutActionId(SEARCH_ACTION).withInAll(false)\n'
     '            .withItemNoun("login");', 1)])
edit("plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/FileServerScope.java", [
    ('.withDescription("Start or stop a file server").withShortcutActionId(shortcutActionId).withInRecents(false);',
     '.withDescription("Start or stop a file server").withShortcutActionId(shortcutActionId).withInRecents(false)\n'
     '            .withItemNoun("file server");', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/palette/CommandsScope.java", [(
    '    @Override public List<PaletteVerb> verbs() { return List.of(RUN); }\n',
    '    @Override public List<PaletteVerb> verbs() { return List.of(RUN); }\n'
    '    @Override public dev.jasper.ui.model.Nodes.Hue hue() { return dev.jasper.ui.model.Nodes.Hue.BLUE; }\n'
    '    @Override public String itemNoun() { return "command"; }\n', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test :jasper-plugin-snippets:test :jasper-plugin-indexer:test :jasper-plugin-editor:test :jasper-plugin-history:test :jasper-plugin-sample:test :jasper-plugin-vault:test :jasper-plugin-fileserver:test :jasper-app:test --tests dev.jasper.app.palette.CommandsScopeTest --tests 'dev.jasper.app.documentation.*' verifyPluginArchitecture --continue`
Expected: BUILD SUCCESSFUL. Every plugin's tests pass with the new assertion; the documentation tests find the
`pluginpalette` example equal to `SamplePlugin.java`; `verifyPluginArchitecture` accepts the plugins' SDK-only
`dev.jasper.sdk.ui.Nodes` references.

- [ ] **Step 5: Commit**

```bash
git add plugins/*/src jasper-app/src/main/java/dev/jasper/app/palette/CommandsScope.java \
  jasper-app/src/test/java/dev/jasper/app/palette/CommandsScopeTest.java docs/plugin-authoring.md
git commit -F - <<'EOF'
Bundled scopes name their rows and colour them

Spec 14.6's nouns and hues: Remote's hosts are green sessions and its
Connect in split verb is the split variant of Connect; snippets and shell
commands are purple, indexed and recent files amber, application commands
blue; tunnels, greetings, logins and file servers take their plugin's
hue. Nothing draws them until the Run window; verb ids and labels are
unchanged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: Documentation, STATUS and the whole build

The guides say what plugins can now do (spec 17: `docs/plugin-ui.md` and `docs/plugin-authoring.md` for the new
vocabulary, the SDK README's package table), `docs/app-maintenance.md` records the toolbar's new rule, and
`docs/STATUS.md` records the plan. The whole build runs and its counts are recorded.

**Files:**
- Modify: `docs/plugin-authoring.md:123-125` (the Tools bullet and two bullets after it), `docs/plugin-authoring.md:401` (one paragraph), `docs/plugin-authoring.md:639,660,1012-1013` (the fake's new helpers), `docs/plugin-ui.md:183` (two bullets), `jasper-sdk/README.md:21`, `docs/app-maintenance.md:523`
- Modify: `docs/STATUS.md:2` (a new entry above the previous top entry), `docs/superpowers/plans/2026-10-15-enterprise-3d-sdk-0-18-chrome.md:1` (the status banner)

**Interfaces:**
- Consumes: Tasks 1 to 6, committed.
- Produces: documentation only.

- [ ] **Step 1: The guides**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3d Task 7 step 1: the smallest guide edits for SDK 0.18's chrome additions."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/plugin-authoring.md", [
    ("so the menu shows only while some plugin has entries in it, after Tab and before\n",
     "so the menu shows only while some plugin has entries in it, after Tab, Session and Transfer and before\n", 1),
    ("- **Status items** are global: one handle updates the item in every window.\n",
     "- **The Session and Transfer menus** (SDK 0.18) hold commands for hosts and sessions and for moving files:\n"
     "  `StandardMenu.SESSION` and `TRANSFER`. Like Tools, each shows only while some plugin has entries in it, after Tab.\n"
     "- **Toolbar views** (SDK 0.18): `ToolbarItem.view(window -> context.kit().create(spec, events))` places a small group\n"
     "  of your own controls in the main toolbar, one per window, built when the window opens and closed with it or your\n"
     "  registration. Its root is an INLINE `Stack` of LABEL `Text`, DROPDOWN, SEARCHABLE or PICKER `Choice`s, at most one\n"
     "  PRIMARY `Button` and `MenuButton`s; the host refuses any other tree, naming the node, when it mounts the view and\n"
     "  on every `update`. A view never moves into the overflow menu: it hides while the toolbar is too narrow.\n"
     "- **Status items** are global: one handle updates the item in every window.\n", 1),
    ("row, no step or nothing done, and the palette keeps working.\n",
     "row, no step or nothing done, and the palette keeps working.\n\n"
     "`ScopeSpec.withHue` and `withItemNoun` (SDK 0.18) describe your rows to presentations that colour and label them by\n"
     "scope: the hue of the scope's row squares (NONE takes your manifest's `icon_hue`) and what one row is, as a lowercase\n"
     "singular noun (\"session\"; blank uses the label). A verb made with `PaletteVerb.Role.SPLIT` is the split-pane variant\n"
     "of your first verb; a scope has at most one, never first. The palette card draws none of them.\n", 1),
    ("`host.invoke(id, windowId, paneIdOrNull)` show and drive what the plugin contributed.\n",
     "`host.invoke(id, windowId, paneIdOrNull)` show and drive what the plugin contributed.\n"
     "`host.toolbarViews(windowId)` builds and returns a window's toolbar views, as the window does when it opens, and\n"
     "`closeToolbarViews(windowId)` closes them, as closing the window does.\n", 1),
    ("drive a contributed scope as the palette would; `paletteOpens()`, `openedInEditor()`\n",
     "drive a contributed scope as the palette would, and `scopePresentation(scopeId)` reports its hue, item noun and\n"
     "verb roles as `HUE|noun|ROLE,ROLE`; `paletteOpens()`, `openedInEditor()`\n", 1),
    # 3a moved this range to 0.18's and dropped "or later"; the line ends the dialogPresentation paragraph.
    ("`sdk = \">=0.18.0, <0.19\"` to use it.\n",
     "`sdk = \">=0.18.0, <0.19\"` to use it. `FakePluginHost.dialogSwatch(pluginId, title)` (SDK 0.18) reports the swatch\n"
     "an open dialog asked for.\n", 1)])
edit("docs/plugin-ui.md", [(
    "- **Settings pages.** Declared settings render as the standard Settings page; a custom settings panel is a\n",
    "- **Toolbar views.** An INLINE `Stack` of LABEL `Text`, choices, one PRIMARY `Button` and `MenuButton`s, placed in\n"
    "  the main toolbar with `ToolbarItem.view` (see [actions](plugin-authoring.md#actions-and-where-they-appear)).\n"
    "- **Dialog swatch.** `DialogSpec.swatch` puts a small square of a hue before the dialog's title where Jasper draws the\n"
    "  title (the macOS window title row); `Hue.NONE`, the default, draws none.\n"
    "- **Settings pages.** Declared settings render as the standard Settings page; a custom settings panel is a\n", 1)])
edit("jasper-sdk/README.md", [("`PluginAction`, `Toolbar`, `Menus`,", "`PluginAction`, `Toolbar`, `ToolbarViewFactory`, `Menus`,", 1)])
edit("docs/app-maintenance.md", [(
    "from the same Actions as their original placements. Do not retain a second plugin registry\n",
    "from the same Actions as their original placements. A contributed toolbar view (`ToolbarEntry.View`) is built once per\n"
    "window, when `WindowContent.connectTerminals` reports the window open (never for the warm-up rehearsal), and closed\n"
    "with its window or its entry; it lays out at its preferred size and hides, never overflows, when the row is too\n"
    "narrow. Do not retain a second plugin registry\n", 1)])
PY
```

Then run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: BUILD SUCCESSFUL (the guides' links resolve, including `plugin-authoring.md#actions-and-where-they-appear`,
and their examples match compiled source).

- [ ] **Step 2: Run the whole build**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Then print the totals:

```bash
python3 -I - <<'PY'
import pathlib, re
totals = [0, 0, 0, 0]
for path in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    suite = re.search(r"<testsuite\b[^>]*>", path.read_text(encoding="utf-8")).group(0)
    for i, key in enumerate(("tests", "failures", "errors", "skipped")):
        totals[i] += int(re.search(rf'\b{key}="(\d+)"', suite).group(1))
print(f"{totals[0]:,} tests, {totals[1]} failures, {totals[2]} errors, {totals[3]} skipped")
PY
```

Expected: 0 failures, 0 errors, and 30 more tests than the base (`deafed424`, `main` after 3a; neither 3b nor 3c is on
this branch), with its skipped count unchanged:
Task 1's contract (2 runs) and workspace test (3), Task 2's two SDK, one registry, one translation and one contract
(2 runs) tests (6), Task 3's SDK, title-row and contract (2 runs) tests (4), Task 4's two strip, two row and five
window tests (9), Task 5's SDK test and three contracts (2 runs each) (7), and Task 6's Commands scope test (1). Then
run the AGENTS.md source-hygiene script over every Java source; it must print nothing.

- [ ] **Step 3: Record the plan in STATUS and in this plan**

In `docs/STATUS.md`, insert after line 2 (the blank line under the title) this entry, with the line Step 2's totals
script printed in place of `TOTALS`, the first and last commit of Tasks 1-6 in place of `FIRST..LAST` and the day the
plan is executed (local time, `date +%F`) in place of `DATE`, followed by a blank line:

```markdown
**Enterprise presentation, plan 3d: SDK 0.18 chrome — DATE** (`claude/enterprise-sdk-0-18-chrome`, worktree
`.claude/worktrees/enterprise-3d`, `FIRST..LAST`, then this documentation commit;
[plan](superpowers/plans/2026-10-15-enterprise-3d-sdk-0-18-chrome.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). SDK 0.18's chrome additions (spec 8.2),
each carried by the host, the testkit fake and the shared contracts. `StandardMenu.SESSION` and `TRANSFER` are
built-in menus with no items of their own, shown after Tab and before Tools only while a plugin has entries in them.
`ToolbarItem.View` with a `ToolbarViewFactory` places a plugin's own control group in the main toolbar: each window
builds its view once when its open event fires (`WindowChrome.windowOpened`, from `WindowContent.connectTerminals`;
never the warm-up rehearsal's window), closes it with the window or the registration, shows a kit view as a toolbar
strip (`KitView.toolbarStrip`) at its preferred size, and hides it rather than moving it into the overflow menu when
the row is too narrow. `ToolbarViewAdmission` (host and testkit) admits an INLINE stack of LABEL text, DROPDOWN,
SEARCHABLE or PICKER choices, one PRIMARY button and menu buttons, at mount and on every update (separators join
when plan 3b's `Nodes.Separator` is reconciled with this plan).
`ScopeSpec.hue` and `itemNoun` and `PaletteVerb.role` (STANDARD or SPLIT: at most one, never the first verb) are
validated by the SDK and the app registry and mirrored as `PaletteScope.hue()`, `itemNoun()` and `PaletteVerb.role()`
with effective values (a scope's NONE becomes its manifest `icon_hue` through the new `HostedPlugin.iconHue`; a blank
noun the label); nothing draws them until plan 8's Run window. `DialogSpec.swatch` reaches `AuxiliarySurface.swatch`
and `MacTitleBar.setTitleSwatch` draws it before a window or dialog title. The bundled scopes carry spec 14.6's nouns
and hues and Remote's split verb is SPLIT. The testkit gains `FakePluginHost.toolbarViews`, `closeToolbarViews`,
`scopePresentation` and `dialogSwatch`. Zero pixel change: no addition is a node, and the headless gallery's files for
Slate Cobalt Dark and Light are byte-identical to the base. Deviation from spec 10.3 and 15, awaiting the user's
approval (plan, ruling 1): admission runs when a window mounts the view (contained and logged, the refused view
closed, no exception to the plugin) and on every update (an exception to the plugin), not at registration, since
`Toolbar.add` sees only the factory. Rulings (plan, "Rulings"): (2) a toolbar view's buttons are PRIMARY, at most one; (3) the open event is
`connectTerminals`, and a failed factory is not asked again for that window; (4) views give way at priority 40; (5)
`toolbarStrip` keeps the kit's 24 px control height, the default toolbar's button side; (6) the default look draws a
swatch only in `MacTitleBar`'s title row (overlays draw no title, sheets have no title bar, Windows and Linux title bars
are native); (7) an item noun is lowercase, stripped, at most 40 characters, without control characters; (8) the app
mirrors hold effective values and capitalising the noun is plan 8's; (9) `SESSION` and `TRANSFER` follow `TOOLS` in the
enum; (10) the fake builds views lazily per window and closes them with the window; (11) no KitValues, ViewSpec or
gallery change; (12) the admission rule exists in the host and the testkit. For plan 3e: Remote's SSH menu entries
move into `StandardMenu.SESSION` and `TRANSFER` there. For plan 10: Remote's "Session: [hosts] [Connect]" is a
`ToolbarItem.View`, and its first GUI check is that clicks reach the view's controls inside the macOS unified title bar
while the bar's blank space still drags the window. `./gradlew check --continue`: **TOTALS**. GUI checks for the user:
none new (no bundled plugin adds a toolbar view, a Session or Transfer entry or a swatch yet). Not merged, not pushed.
```

Then insert, after this plan's first line (`# Enterprise presentation 3d: SDK 0.18 chrome Implementation Plan`) and a
blank line:

```markdown
> **Status (DATE): executed on `claude/enterprise-sdk-0-18-chrome` (`FIRST..LAST`).** Deviation from the spec,
> awaiting the user's approval: ruling 1 (toolbar view admission at mount and on update, not at registration; spec
> 10.3 and 15). Deviations from this plan's text: none beyond its "Rulings" and its "Execution notes (2026-10-16)".
```

replacing `FIRST..LAST` and `DATE` the same way, and naming any step whose text had to change instead of "none" (and the same in
the STATUS entry).

- [ ] **Step 4: Commit**

```bash
git add docs/plugin-authoring.md docs/plugin-ui.md jasper-sdk/README.md docs/app-maintenance.md docs/STATUS.md \
  docs/superpowers/plans/2026-10-15-enterprise-3d-sdk-0-18-chrome.md
git commit -F - <<'EOF'
SDK 0.18 chrome: documentation and status

The plugin guides describe the Session and Transfer menus, toolbar views,
the palette's hue, item noun and split verb, and the dialog swatch; the
SDK README lists ToolbarViewFactory and the app maintenance guide the
toolbar's view rule; the testing text names the fake's new helpers.
STATUS records plan 3d with its rulings and its deviation.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Execution notes (2026-10-16)

Pre-flight for execution on `claude/enterprise-sdk-0-18-chrome` at `deafed424` (`main` after plan 3a). Plan 3b
(controls) executes in parallel in `.claude/worktrees/enterprise-3b` and plan 3c (tables) later, so nothing here may
use a 3b or 3c API. How this was checked: every edit script and every whole-file block of Tasks 1 to 7 was replayed,
in task order, on an exported copy of `deafed424`. Every anchor was found the stated number of times except Task 7's
`plugin-authoring.md` dialog sentence (R4). The replayed tree compiled with plain `javac` (no Gradle) for jasper-sdk,
jasper-ui, jasper-terminal, jasper-buddy and jasper-app (main and tests), and for the testkit's main, fixtures and tests
and jasper-ui's fixtures and tests; the original Task 4 text failed with "variable live is already defined" (R3).
`javadoc -Xdoclint:all` over the replayed SDK reports no error, the ledger scan finds a row for every candidate, and
the AGENTS.md hygiene scan is clean. The plugins were not compiled: their edits only append `ScopeSpec` deriver calls.
No test has been run. The text above now carries R1 to R7; ruling 1 (admission at mount and on update) still needs the
user's approval, or a spec amendment line, before this branch merges.

### Pre-flight scan

| Row | Shared file or interface | Finding | Ruling |
|---|---|---|---|
| Task 1 / Task 2 | `PluginContractTest`, `ContractHarness`, `FakePluginHost` | Different anchors (before `statusItemsAreOrderedGlobalAndInertAfterClose` and `aScopeGetsItsOwnRows…`; the menu-target doc and `scopes()`; `menu()`'s doc and `scopes()`); replay passes | none |
| Task 1 / Task 3 | `PluginContractTest`, `ContractHarness`, `FakePluginHost` | Different anchors; replay passes | none |
| Task 1 / Task 4 | `WindowChrome.java` | Task 1 edits `rebuildContributedMenus` (`:246-283`); Task 4 edits the fields (`:47`), `connect` (`:143-147`) and the toolbar pass (`:164-204`); no hunk overlaps | none |
| Task 1 / Task 5 | `ContractHarness`, `FakePluginHost` | Task 5's `toolbar()` anchors end on the line before Task 1's menu-target doc; both apply in order | none |
| Task 1 / Task 7 | `docs/plugin-authoring.md` Tools bullet; STATUS entry | Both say Session and Transfer come after Tab and before Tools (ruling 9) | none |
| Task 2 / Task 3 | testkit, both harnesses, `AppContractTest` | Different anchors (`scopes()` and `windows()`, `dialogPresentation`, `FakeUi`'s dialog lines); replay passes | none |
| Task 2 / Task 5 | `PluginContractTest`, `ContractHarness`, `FakeContractTest`, `AppContractTest` | Different anchors; replay passes | none |
| Task 2 / Task 6 | `withHue`, `withItemNoun`, `PaletteVerb.Role.SPLIT`, app `hue()` and `itemNoun()`, `scopePresentation` (`HUE\|noun\|ROLE,…`, NONE read as NEUTRAL in the fake) | Task 6's expectations follow ruling 8: Tunnels, Sample and Vault read NEUTRAL in the fake although the app gives their manifest `icon_hue`; File Server checks its spec's NONE; Remote's verbs are `connect,split,edit`, hence `STANDARD,SPLIT,STANDARD` | none |
| Task 2 / Task 7 | guide and STATUS wording | Matches the SDK docs (NONE takes the manifest's `icon_hue`; a blank noun is the label) | none |
| Task 3 / Task 5 | `HostedUi`, `FakeUi`, `FakePluginHost`, `ContractHarness`, `FakeContractTest`, `AppContractTest` | `HostedUi` `:742` and `:225-238`; `FakeUi` `:276`/`:566` and `:134`/`:157`/`:200-210`; in `AppContractTest`, Task 5 inserts before `rail()` and Task 3 after `windows()`, which follows `rail()`; no overlap | none |
| Task 3 / Task 7 | the guide's `dialogSwatch` sentence | Anchor missing at `deafed424` | R4 |
| Task 3 (fake / app) | `dialogSwatch` for `Windows.overlay` surfaces | The app harness reports an overlay surface's swatch (NONE); the fake answers `-`. No contract asks | R9 |
| Task 4 / Task 5 | `ToolbarEntry.View(Function<UUID, Optional<Mounted>>)`, `Mounted.close()`, `KitView.toolbarStrip`, the `view` toolbar line, `migration.csv` | `HostedKitView.component()` is a `KitView`, so the chrome's strip flag applies. The chrome reserves a window's slot and `HostedUi` returns empty for a window it already holds, so no second factory call. The app harness drives `TerminalFixture` windows, which have no `WindowChrome`, so it and a real window never both build a view. Task 4 appends 1 ledger row and Task 5 appends 3 | none |
| Task 4 / Task 7 | `docs/app-maintenance.md`, STATUS | Priority 40, built once on open, hides rather than overflows: these match `WindowToolbar.view` and `windowOpened` | none |
| Task 5 / Task 7 | toolbar-view guide bullets | The guides would name `Separator`s, which this branch lacks | R1 |
| Task 6 / Task 7 | `docs/plugin-authoring.md` | Task 6 edits the `pluginpalette` copy (`:443`); Task 7 edits `:123-125`, `:401`, `:639`, `:660` and `:1012-1013` | none |
| 3d / 3b (parallel) | `KitView.java`, `PluginContractTest`, `docs/plugin-ui.md`, `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `docs/STATUS.md`, `migration.csv`; `Nodes.Separator`, `IconSize.SWATCH` | 3b edits `KitView:163-166` (3d edits `:319-330`), `plugin-ui.md:165-177,199-205` (3d inserts before `:183`), `README.md:45` (3d edits `:21`), `plugin-authoring.md:611`; it adds no ledger row | R1, R5, R8 |
| Task 1 | itself | Files, interfaces, tests and commit agree; `StandardMenu` lines moved to `:16-20` | R10 |
| Task 2 | itself | Agrees; the noun rule in code is ruling 7; fact 11 has six derivers, not seven | R10 |
| Task 3 | itself | Agrees; line numbers moved (`AuxiliarySurface:79-82`, `NativeShells:350`) | R5, R10 |
| Task 4 | itself | `rebuildContributedToolbar` declared `live` twice (compile error). The gallery check used a temporary second worktree at the merge base | R3, R6 |
| Task 5 | itself | Used 3b's `Nodes.Separator` in both admission switches and in the `ToolbarItem.View` javadoc | R1 |
| Task 6 | itself | Agrees; all ten anchors are present | none |
| Task 7 | itself | One stale anchor, line numbers moved, and the test-count base named `main` after 3c | R4, R7, R10 |
| Before you start | itself | Asked for `git rebase main` and stopped unless 3b and 3c were on `main` | R2 |

### Rulings

- **R1.** What: this plan uses no plan 3b API. Both `ToolbarViewAdmission` copies drop `case Nodes.Separator ignored -> true;`, the `ToolbarItem.View` javadoc drops `{@link Nodes.Separator}`, and the guides, the commit message and the STATUS entry stop naming separators. Why: 3b runs in parallel and this branch must compile on `deafed424`, where `Nodes.Separator` does not exist; with no such node there is nothing to admit. Cost if wrong: once both branches merge, the `default -> false` arm compiles but refuses separators. So the second of 3b and 3d to merge restores the case in both copies, the javadoc link and the guide words, and adds a separator to `sessionBar` in `aToolbarViewHoldsOnlyAnInlineStackOfAdmittedControls`.
- **R2.** What: "Before you start" checks the base (`deafed424`), a clean tree, 3a's 0.18.0 and that 3b's `Separator` is absent, and it does not rebase. Why: the workflow forbids rebasing, and 3b and 3c are not on `main`. Cost if wrong: if `main` moves, this branch is merged onto it later; the code is the same.
- **R3.** What: in Task 4's `rebuildContributedToolbar`, the set of live view entries is `liveViews`, not `live`. Why: the Dropdown case declares a local `List<Action> live` in the same method, and `javac` rejects the redeclaration (checked on the replayed tree). Cost if wrong: none; it is a rename.
- **R4.** What: Task 7's dialog-swatch sentence attaches to "`sdk = \">=0.18.0, <0.19\"` to use it.", which ends the `dialogPresentation` paragraph (`plugin-authoring.md:1012-1013`) and occurs once. Why: 3a removed "or later". Cost if wrong: the sentence lands in another paragraph, which the documentation tests would not catch; the anchor is unique, so this is unlikely.
- **R5.** What: `DialogSpec.swatch` is drawn by `MacTitleBar`'s private `TitleSwatch`. It is a solid square at `ICON_CAPTION`, filled through `ThemeZone.color` with the existing `SemanticColor.HUE_*` roles, so it works on today's `main` without 3b's `IconSize.SWATCH`. Why: 3b's SWATCH is the same square (caption size, the hue's glyph colour), and it is not on this branch. Cost if wrong: two painters of one square could drift. After both merge, `TitleSwatch` can be replaced by the kit's SWATCH icon as a follow-up; nothing requires it.
- **R6.** What: Task 4's gallery check renders a baseline in this worktree before any Task 4 edit (Step 1) and compares against it in Step 4. It no longer creates a temporary detached worktree at the merge base. Why: every command runs from this worktree's root, Tasks 1 to 3 touch nothing under `jasper-ui`, and the gallery runs on jasper-ui's test classpath only. Cost if wrong: if an earlier task had changed jasper-ui, the baseline would include that change. The Files lists show that none does.
- **R7.** What: Task 7's test count is measured against `deafed424`, still +30. Why: neither 3b nor 3c is on this branch. Cost if wrong: a miscount shows at once in the totals.
- **R8.** What: the second of 3b and 3d to merge reconciles the shared files. `migration.csv`: keep 3d's four appended rows; 3b adds none. `docs/STATUS.md`: keep both top entries, the later merge on top. `PluginContractTest`: the two plans add different tests. `KitView.java`: 3b removes `setFieldReadOnly` and 3d adds `toolbarStrip` after `titleStrip`. `docs/plugin-ui.md`: 3d's two bullets go before "Settings pages", next to 3b's 165-177 edits, so keep both. `plugin-authoring.md` and `jasper-sdk/README.md` take different lines. R1 and R5 apply there too. Why: the plans run in parallel and edit these files independently. Cost if wrong: merge conflicts in documents, resolved by keeping both sides; R1's restore is the only semantic step.
- **R9.** What: the app harness's `dialogSwatch` keeps its `kind() != WINDOW` filter, so a `Windows.overlay` surface reports `NONE`, where the fake (which tracks dialogs by presentation) answers `-`. Why: an OVERLAY-presented dialog is an OVERLAY surface just as `Windows.overlay` is, so the app harness cannot tell them apart without new state; no contract asks about overlay surfaces, and the fake's javadoc names them out. Cost if wrong: a future contract about overlay swatches would fail under the app harness until it records which surfaces are dialogs.
- **R10.** What: the verified facts and file lists now cite `deafed424` lines: facts 1, 3, 6, 7, 9, 11 ("six derivers"), 14 and 18, and the file lists of Tasks 1, 3 and 7. Why: the plan's lines were taken at `222969926`, and some were already off there (for example `WindowContributions.java:466`, which is `:460`). Cost if wrong: an executor looks a few lines away; every edit anchors on quoted text, not on line numbers.
