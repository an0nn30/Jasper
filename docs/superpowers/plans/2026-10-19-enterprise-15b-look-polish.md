# Enterprise presentation 15b: look polish Implementation Plan

> **Status (2026-10-20): executed on `claude/enterprise-look-query` (`32ec4a171..807169d15`, then the Task 6 documentation commit).** Deviations from this plan's text: the execution rulings X1 to X23 below; in Task 4, the search button ends 8 px (16 mock px) before Settings…, the trailing group's `CHROME_BUTTON_GAP`, where the text assumed 6 px, `EnterpriseMenuRowMockTest.Mounted` captures Settings…'s bounds (`settings`) beside X9's `search`, and `chrome-toolbar-light` no longer lists `#FFFFFF`, the field's `surface.input`, which the row no longer paints (both toolbar pairs pass their unchanged 0.30 floors, at 0.427 and 0.423); in Task 6, spec 11.6's openers sentence, which named only the field, also names the icon, so 11.6 joins plan 15's header list, and its paragraph is re-wrapped only through the edited lines; the spec's anchors were re-found by quoted text (X19); Step 5's diff held no `composition-light-vault-manager` line this run. Final review fixes: a header's own controls refresh after `HeaderControl.children` marks them under the classic flag, so `KitView.update` keeps their classic art; `DockFixture` builds its area with `ChromeArtwork.CLASSIC`, as Enterprise windows do, and `DockMockTest.compareHeader` expects the classic `add`'s authored ink (Task 2 Step 4's probes); an `ACTIVATING_TOGGLE` of a shown tool window that is not the active one activates it, and only the active one hides, as IntelliJ's ActivateToolWindowAction; Rulings 1's cost names the nested-event-loop case.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Plan 15's second deliverable. The user asked on 2026-10-19: "in entperise I want to replace that text box command pallete in the toolbar with just search icon like in IntelliJ classic". They then approved plan 15: "Go ahead with plan 15 as proposed: SDK look query (and remove the Enterprise API), kit defaults for plugin settings, activation, classic icons everywhere, the search icon, versions, docs and the Sample plugin." Plan 15a did the SDK query, the API removal, the kit defaults, the versions and the Sample. This plan delivers the rest:
- a plugin's own panel toggle activates its tool window, as IntelliJ's ⌘1 does;
- the classic icons in tool-window headers and toolbar views;
- the toolbar's Search Everywhere icon in place of the field under Enterprise;
- a guard that production code never checks Enterprise's identity;
- the remaining documentation.

**Architecture:**
- **Activation (host only).** `HostedUi` knows which of the plugin's actions is running. A `Panels.toggle` made while the panel's own declared toggle action (`PanelSpec.withToggleActionId`) runs is sent as `PanelRequest.Op.ACTIVATING_TOGGLE`, which the window already handles. A `PanelHost.show()` made while that action runs is sent as a new `PanelRequest.Op.ACTIVATING_SHOW`, which `WindowContributions` maps to show-then-activate: Transfers' action (`TransferUi.SHOW`, ⌘⌥3) toggles only on its first open in a window and calls `host.show()` after that (`TransfersPanels.java:38-41`). Under flush frames the tool window then takes the tint and the keyboard. Under the default look's standard frames nothing activates, as today.
- **Classic icons everywhere.** Under `[chrome] icons = "classic"`, IntelliJ's classic art is drawn for the six classic names (`add`, `split`, `zoom`, `settings`, `window.add`, `search`) in three places, besides the window header and status bar that already draw it:
  - the app-drawn `ToolWindowHeader` actions;
  - a borrowed kit header's host actions and the panel's own header controls;
  - the buttons of toolbar views.

  jasper-ui gains `KitView.classicChromeIcons(boolean)` and `IconRegistry.classicChromeIcon`. The app gains `AppIcons.classicChromeIcon` and passes the window's `ChromeArtwork` to `ToolWindowArea`. Every other name, every panel body and the default look are unchanged.
- **The toolbar search icon.** An eleventh chrome point, `[chrome] toolbar_search = "field" | "icon"`, is `field` in both bases and `icon` in Enterprise's kit. It needs `palette = "run_window"` and a native or classic title bar. `WorkspaceLook.Search` (read only by `WorkspaceChrome`) gives `ClassicHeader` an icon-only "Search Everywhere" button with the classic magnifier, before Settings…, in place of the field. `PluginLook.search` reports it to plugins through SDK 0.18.2's `Presentation.toolbarSearch`.
- **Identity guard.** An architecture test scans the constant pools of the app's and jasper-ui's classes and of every bundled plugin's own classes except Enterprise's for "enterprise", so a look stays data.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and Java2D, FlatLaf 3.7, Gradle (Kotlin DSL), JUnit 5 and AssertJ, `java.lang.classfile`, Python 3 with numpy/PIL for `tools/visual` (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md).
- Read its header amendment lines, including plan 15a's, and sections 3 (principle 3), 5.2, 5.3, 5.9, 9.1, 10.3, 10.7, 16.1, 16.2 and 19.1.
- Plan 12's Rulings 3, 4 and 18 (`2026-10-18-enterprise-12-intellij-classic.md:134-196`) are what this plan revisits: the classic set, toolbar views drawing no chrome art, and activation not following a plugin's toggle.
- Plan 15a (`2026-10-19-enterprise-15a-look-query.md`) is complete on this branch (`f78bb0385..e858a4ecb`). Its `PluginLook` (with `Search`), `HostedPresentation`, `FakePresentations` and SDK 0.18.2 (with `Presentation.ToolbarSearch`) are in place.
- Audit: `kit.md`, `surface.md`, `plugins.md` and `critic.md` under `/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/look-audit/` (`critic.md` items 3, 6 and 8).

**Workspace:** the plan-15 worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-15`, branch `claude/enterprise-look-query`, at plan 15a's last commit (`e858a4ecb`; `main` is still `f78bb0385`, so no merge is needed). If the user asks for a separate branch, branch it from the commit that holds 15a. Run every command from the worktree root. Line numbers below were re-verified at `e858a4ecb` (Execution notes, 2026-10-20). Of this plan's files, plan 15a touched `HostedUi.java`, `KitParser.java` (`CHROME` moved to `:63-64`), `Projections.java`, `KitParserTest.java`, `ProjectionsTest.java`, `ProjectionReadTest.java`, `PresentationConformanceTest.java`, `HostedPresentationTest.java`, the testkit's `FakePresentations.java`, the SDK's `Presentation.java` (new), `enterprise.kit.toml`, `migration.csv`, the spec, `docs/plugin-authoring.md`, `docs/plugin-ui.md` and `docs/STATUS.md`; the anchors below are its. Re-find every anchor by its quoted text before editing; never edit around a missing anchor.

## Before you start (refresh)

Do not rebase, merge or push. Never run bare `git stash`, and never run `./gradlew --stop`.

```bash
grep -c 'public record Presentation(' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Presentation.java                   # 1: plan 15a is in
grep -c 'record PluginLook(' jasper-app/src/main/java/dev/jasper/app/presentation/PluginLook.java                    # 1
grep -c 'ACTIVATING_TOGGLE' jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java                            # 0: Task 1 adds it
grep -c 'ACTIVATING_SHOW' jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java                  # 0: Task 1 adds it
grep -c 'toolbar_search' jasper-app/src/main/java/dev/jasper/app/presentation/KitParser.java                         # 0: Task 3 adds it
ls /private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan15-baseline/app | wc -l   # 11
```

If the baseline is missing, make it as plan 15a Task 1 Step 0 does, from `f78bb0385` in a detached worktree (`git worktree add --detach "$TMPDIR/plan15-main" f78bb0385`, the commands with `./gradlew -p`, then `git worktree remove`). Plan 15a proved its tip equal to that baseline, so this plan compares against the same files (Rulings 12).

## Global Constraints

**Build and safety**
- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain. Always use `./gradlew`, never a system `gradle`, and never run `./gradlew --stop`.
- Never launch the GUI and never train the AOT cache. Headless JUnit renders and `:jasper-ui:gallery` may run. Focus, real windows and the keyboard are GUI checks for the user (Task 6).
- One commit per task. Every commit message ends with a blank line, `🤖 Generated with Claude Code at The Home Depot`, a blank line, then `Co-Authored-By: Claude <noreply@anthropic.com>`. Do not push, merge or rebase. Never dispatch subagents from inside a task.

**The default look stays byte-identical.** The gallery's `dark-1x` and `light-1x` (161 files each) and jasper-app's 11 default-look renders must equal plan 15a's baseline (`plan15-baseline`). Task 6 proves it. Each change is kept from the default look as follows:
- `KitView.classicChromeIcons` is false unless the window's artwork is `CLASSIC`.
- `AppIcons.classicChromeIcon` is asked only under `CLASSIC`.
- `ToolWindowArea`'s and `ToolWindowHeader`'s existing constructors mean `NEW_UI`.
- `ChromeSpec.DEFAULT` and `ChromeSpec.CLASSIC` keep `toolbar_search = "field"`, and `ClassicHeader`'s field path is unchanged.
- Under standard frames `ACTIVATING_TOGGLE` and `ACTIVATING_SHOW` activate nothing (`ToolWindowArea.activate` returns at once when not flush, `ToolWindowArea.java:491-492`).

**Versions.** No SDK change: SDK 0.18.2's `Presentation.toolbarSearch` already exists, and `ICON` becomes reachable. Enterprise stays 0.3.0 (plan 15a Rulings 13), and its `sdk = ">=0.18.2, <0.19"` from plan 15a already covers `toolbar_search`, which arrives with the same host (plan 15a Rulings 14). No plugin code or manifest changes, so no plugin version moves. The testkit's `FakePresentations.CLASSIC` follows Enterprise's kit (`ICON`).

**Code rules**
- Javadoc doclint on jasper-ui, jasper-app and the testkit: every `{@link}` resolves, no raw `<`, `>`, `&` or `->` in comments, and new public members carry `@param` and `@return`.
- `docs/design/theme-engine/migration.csv`: a new or renamed `.java` file under the scanned roots (`jasper-app`, `jasper-sdk`, `jasper-sdk-testkit`, `plugins`; not jasper-ui, fact 15) that `ThemeMigrationCoverageTest.CANDIDATE` matches gets a row in the same commit, its disposition beginning `New in Enterprise plan 15b Task N;`. Edited files already have their rows, which the test checks only for presence (Execution notes X10). The only expected new row is `LookIdentityTest`'s (Task 5). Run `./gradlew :jasper-app:test --tests '*ThemeMigrationCoverageTest'` in each task that touches Java files.
- `ColourLiteralScanTest` stays green: colours come from roles. Source hygiene as AGENTS.md says.
- Spec 3's strategies rule: `WorkspaceLook.Search` is read only by `WorkspaceChrome` (a `ProjectionReadTest` pattern), and `PluginLook.Search` only by `HostedPresentation` (plan 15a's pattern already covers it).
- MVP rule: two convenience constructors are added on purpose, each meaning "the value every look but one has":
  - `WorkspaceLook`'s eight-argument constructor, meaning `Search.FIELD`;
  - `ToolWindowHeader`'s and `ToolWindowArea`'s existing constructors, meaning `ChromeArtwork.NEW_UI` (about 50 test constructions use them);
  - `ClassicHeader()`, meaning the field.

**Tests and documents**
- An Enterprise mock expectation or `tools/visual/pairs.json` crop that the search icon or a classic header icon changes is recorded as a deviation naming "the user's 2026-10-19 direction". A floor moves only to a measured value, with a note; never lower a floor to pass.
- Documentation edits are the smallest accurate ones. Earlier `docs/STATUS.md` entries are not rewritten.

## Review Focus

1. **A plugin toggle action that hides its panel, or toggles it in another window, and a toggle action that shows through `PanelHost.show()`.** Hiding activates nothing and hands the focus back as a plain toggle does. A toggle or show for another panel, or one made later from `invokeLater`, a background executor or another action, stays plain. A `show()` inside the panel's own toggle action activates, as Transfers' does after its first open.
   - Tests: Task 1 `HostedUiTest.aPanelsOwnToggleActionActivatesAndAnythingElseToggles`, `HostedUiTest.aShowInsideThePanelsOwnToggleActionActivates`, `WindowPanelsTest.anActivatingToggleThatHidesThePanelActivatesNothing` and `anActivatingShowUnderFlushFramesActivatesThePanel` (Execution notes X1).
2. **A narrow Enterprise window.** The search button stays in the trailing group beside Settings… while the leading toolbar gives way; it is never hidden by the field's collapse rule and never overlaps Settings….
   - Test: Task 4 `ClassicHeaderTest.underTheSearchIconANarrowWindowKeepsTheButtonBesideSettings`.
3. **`window.toolbar = "icons_and_labels"` and `hidden` under the search icon.** Under labels the button stays icon-only, as IntelliJ's. Under `hidden` it goes with the row, and ⇧⌘P still opens the Run window.
   - Tests: Task 4 `theSearchButtonStaysIconOnlyInEveryToolbarMode` and `underAHiddenToolbarTheShortcutStillOpensTheRunWindow`.
4. **A header action or toolbar-view button named outside the classic set** (`folder`, `more`, `collapse`, a plugin's own path), and the same six names in a panel's body. These keep exactly today's art under Enterprise.
   - Tests: Task 2 `KitClassicChromeIconsTest.onlyTheSixNamesInHeadersAndToolbarStripsChange` and `ToolWindowHeaderTest.otherNamesKeepTheirGlyphUnderClassicIcons`.
5. **A third-party kit that names `toolbar_search = "icon"` with the anchored palette or a unified title bar.** It is refused with one message naming the key, and the default look applies.
   - Test: Task 3 `KitParserTest.everyInvalidKitGivesOneMessageNamingTheKey` (the new cases).

## Verified facts

Re-verified at `e858a4ecb` (plan 15a's tip) on 2026-10-20. Paths are under `jasper-app/src/main/java/dev/jasper/app/` unless they start with a module.

**Activation**
1. `Contributions.PanelRequest.Op` has `SHOW, HIDE, TOGGLE, ACTIVATING_TOGGLE` (`contributions/Contributions.java:55-60`). Only the host-made `<id>.toggle` (`:158-160`) and Event Log (`application/NotificationsFeature.java:63-64`) send `ACTIVATING_TOGGLE`. `HostedUi`'s `PanelHost.show()` calls `PanelSite.show()` (`plugins/HostedUi.java:452`, in `host(site)` at `:440`), whose runnable is `WindowContributions.showPanel(id)`, a plain `dock.show` (`workspace/WindowContributions.java:327`, `:210`). `PanelEntry` has `toggleActionId()` (the declared action, or the host-made `<id>.toggle`) and `namedToggle()` (whether the plugin declared it; `contributions/PanelEntry.java:36-39`).
2. `HostedUi`'s action handler runs `handler.accept(new Context(...))` inside `containment.run(pluginId, "action " + spec.id(), …)` (`plugins/HostedUi.java:206-207`, in `actions()` at `:196-216`). `Panels.toggle` sends `Op.TOGGLE` (`:430-436`, in `panels()` at `:413-438`); `register` requires a declared toggle action to be registered already (`requireOwn`, `:419`, `:160-163`). `HostedUi` runs on the UI thread, and registration needs it (`guard`).
3. `WindowContributions.requested` maps `ACTIVATING_TOGGLE` to `toggleAndActivate` (`workspace/WindowContributions.java:183-191`). That method activates only when `dock.visible(id)` after the toggle, and sets `activating` only under flush frames (`:196-201`). `ToolWindowArea.activate` returns at once when not flush (`workspace/ToolWindowArea.java:491-492`).
4. The bundled toggle actions that call `Panels.toggle`: `RemotePlugin.java:684`, `SftpUi.java:121`, `TunnelsUi.java:118`, `TransfersPanels.java:40` and `FileServerPlugin.java:158` (plan 12 STATUS, `docs/STATUS.md:224-227`). Transfers' is a show, not a toggle: `TransferUi.SHOW` (⌘⌥3, `TransferUi.java:43-45`) runs `TransfersPanels.show` (`RemotePlugin.java:181`), which calls `Panels.toggle` only while the window has no `PanelHost` yet and `host.show()` after that (`TransfersPanels.java:38-41`), so a second press keeps the panel open. `HostedUiTest.panelsAndRailReachTheModelWithContainedFactories` asserts a plain toggle's request (`jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java:396-399`), and builds a panel instance with a `PanelSite` over a random window id (`:381-384`).
4a. Plan 12's window-level activation tests are in `WindowPanelsTest`, not `WindowContributionsTest` (which has no flush-framed fixture): `underFlushFramesTheViewToggleActivatesThePanelAndAPluginsToggleDoesNot` (`jasper-app/src/test/java/dev/jasper/app/workspace/WindowPanelsTest.java:124-141`) makes a flush window with `DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), ThemeTestSupport.controller(true), DockChromeTest.classicDock())`, connects a `Contributions`, and reads `owner.area().focusedSlot()` and `owner.area().content(slot)`; its `toggle(model, owner, id)` helper (`:47-49`) sends a plain `TOGGLE`. Its second test (`:147-172`) shows that a plain toggle still hands focus back.

**Icons**
5. The classic set is `BuiltInArtwork.ClassicHolder.CLASSIC`'s six names (`jasper-ui/src/main/java/dev/jasper/ui/icons/BuiltInArtwork.java:40-45`; `classicChrome()` at `:57`). The holder decodes its SVGs when first touched (`pair` reads both files, `:64-66`), and its javadoc promises the start-up prewarm never decodes it. `IconRegistry.chromeIcon(name, size, CLASSIC)` (`IconRegistry.java:99-101`) returns them, else the New UI chrome original, else the catalog (`prepared`, `:117-133`); `chromeIcon(name, size)` is `:93` and `stripeIcon` `:108`. So a full `CLASSIC` receiver would change the other names' art too.
6. Receivers today:
   - `AppIcons.chromeReceiver` marks the window header (`workspace/WindowChrome.java:74`, after `WindowHeader.java:40`'s NEW_UI mark), the stripes (`ToolStripe.java:96`, NEW_UI) and the status bar (`workspace/WorkspaceChrome.java:122`).
   - `NativePreparation.paint` draws a built-in `PresentationIcon` or `SemanticIcon` by the nearest receiver's artwork, and outside every receiver by its ordinary artwork (`platform/AppIcons.java:247-272`, `paint` at `:258-266`).
   - The tool-window area is in no receiver.
   - `AppIcons.kitName(Icon)` (`:61-66`) and `stripeIcon` (`:105-121`) read a built-in name; a built-in `PresentationIcon` has a null `pathData()`.
7. `ToolWindowHeader` shows each contributed action's `action.icon()`, under flush frames through `stripGlyph` (`workspace/ToolWindowHeader.java:219-236`, the icon at `:232`; `stripGlyph` at `:156-158`). Its constructors are the three-argument one at `:84` and the five-argument one at `:95`. `ToolWindowArea` makes it in `header(id, title)` at `:665-671`, and its constructors are the two-argument one at `:297` and the four-argument one at `:307`. `WindowContent` makes the area at `:283` and knows `look().chromeArtwork()` (`:280`; `look()` is the window's `WorkspaceChrome`).
8. Kit-drawn icons:
   - A borrowed kit header's host actions carry `PanelHostHeader.Artwork(name, pathData)`; a null path means canonical art (`jasper-ui/src/main/java/dev/jasper/ui/swing/PanelHostHeader.java:44-47`, `Action` at `:57`). `ToolWindowArea.hostHeader` gives a built-in `PresentationIcon` `Artwork(name, null)` in every frame and an app `SemanticIcon` one only on a header strip (`ToolWindowArea.java:627-634`). `Buttons.HostArtwork.icon` paints them with `icons.panel(name, …)` (`Buttons.java:17-43`, `icon` at `:37-42`), and `ButtonControl.refresh` sets it at `:84`.
   - `ToolWindowArea.mountHeader` calls `view.setPanelHostActions` at `:586` (Options `more`, Hide `remove` or `collapse`) and clears it on release at `:612`.
   - The panel's own header controls have `button.panel = true` (`PanelHeaders.java:254,260,306`; a header `MenuControl`'s `main.panel` at `:308`). **`button.panel` is also set on a document bar's buttons and menus in a panel body** (`ViewReconciler.java:305,307`, with `documentBar = true`), so "in the panel header" is `panel && !documentBar`.
   - A button's named icon is chosen in `ButtonControl.refresh` (`Buttons.java:74-100`, the named branch at `:85-92`). `KitView.toolbarStrip(boolean)` (`KitView.java:406-418`) marks a toolbar view, and `WindowChrome` sets it when it mounts one (`workspace/WindowChrome.java:328-331`). A `MenuButton` sets its icon in `Menus.MenuControl` (`Menus.java:216-228`).
   - `IconRegistry.PreparedIcon.icon(GeneratedTheme, SemanticColor)` exists (`IconRegistry.java:147`).
   - The jasper-ui swing tests build a view with `dev.jasper.ui.testsupport.KitFixtures.context()`, `KitFixtures.render(view, w, h, 1)` and `Edt.run`, as `KitViewToolbarStripTest` does (`jasper-ui/src/test/java/dev/jasper/ui/swing/KitViewToolbarStripTest.java:21-27`).
9. `ClassicHeaderTest.assertPaintsClassicAdd` compares a button's centre with `new IconRegistry().chromeIcon("add", size, CLASSIC).icon(theme, TEXT_PRIMARY)` pixel for pixel (`jasper-app/src/test/java/dev/jasper/app/workspace/ClassicHeaderTest.java:289-311`). It is the model for every icon test here. `enterprisesDefaultToolbarIsClassicIconSquares` (`:316-337`) mounts a plain `JPanel` toolbar view; the model for mounting a real `KitView` toolbar view is `WindowToolbarViewsTest.aKitViewIsPresentedAsAToolbarStrip` (`jasper-app/src/test/java/dev/jasper/app/workspace/WindowToolbarViewsTest.java:195-210`).

**The search field**
10. The classic header:
    - `ClassicHeader` has no declared constructor. `build` fills the leading toolbar and puts "Settings…" (`ActionId.OPEN_SETTINGS`, icon `settings`, priority 100) in its trailing `WindowToolbar` (`workspace/ClassicHeader.java:39-57`, Settings… at `:53`).
    - `layout` places the trailing group at the right, the field before it, and the toolbar before the field; the field collapses below `searchMinimum` (`:68-88`).
    - The field's text is the mocks' "Search everywhere" (`:99`).
    - `WindowChrome.classicButton(into, id, label, icon, priority)` makes a `ClassicToolbarButton` (`workspace/WindowChrome.java:136-141`), and `WindowChrome.composition()` returns the header composition (`:176`).
    - `AppIcons.icon("search")` is the `SEARCH` meaning (`platform/AppIcons.java:25`), which a `CLASSIC` receiver draws as IntelliJ's classic find icon (fact 5).
    - The field runs `owner.action(ActionId.COMMAND_PALETTE)` (`WindowChrome.java:70-72`).
    - `WorkspaceChrome.header()` makes `new ClassicHeader()` at `workspace/WorkspaceChrome.java:103`; its private constructor is `:39-54`, `STANDARD` `:17-19`, `of` `:74-87`, `equals` and `hashCode` `:130-136`. The only other construction is `WindowPairsTest.java:93`.
11. `WindowToolbar.applyLabels` sets a button's text to its `label` property, or null, unless `keepLabel` is `FALSE` (`workspace/WindowToolbar.java:200-211`). A `ClassicToolbarButton` whose text is null is a 22 px icon square (`ClassicToolbarButton.java:53-54`). Its tooltip is its label (`:39`). `TitleSearchField` computes its tooltip on demand with `composition.searchTooltip(…, shortcutText())` (`TitleSearchField.java:93`). `WindowContent.paletteShortcut()` is at `:808`.
12. The model:
    - `ChromeSpec` is a ten-component record (`presentation/ChromeSpec.java:21-22`; its javadoc says "The ten chrome variant points … eight two-valued points", `:6`), and its constraint is at `:63-64`. `new ChromeSpec(` appears in `ChromeSpec.java` (2, `:46`, `:52`), `KitParser.java:321`, `KitParserTest.java:157`, `ProjectionsTest.java:119,168`, `testsupport/EnterpriseLook.java:22,37` and `ChromeRenderTest.java:70`.
    - `KitParser.CHROME` lists the keys (`presentation/KitParser.java:63-64`); `chrome(table, base)` is `:306-322`, its `menu_bar` constraint `:319-320`.
    - `KitParserTest.everyChromeAndControlPointTakesBothItsValues` (`:206-231`) loops over the control points only; its chrome lines name `title_bar` and `menu_bar`. `everyInvalidKitGivesOneMessageNamingTheKey` is `:249`.
    - `WorkspaceLook` has eight components (`presentation/WorkspaceLook.java:17-18`) and 12 test constructions; `Projections.of` builds it at `:99-106` and `PluginLook` at `:115-118` (with `PluginLook.Search.FIELD`); `Projections.fallback` builds both at `:150-159`.
    - `ProjectionsTest.thePluginLookIsTheLookOnEachPlatform` (`:218-232`) expects `PluginLook.Search.FIELD` for `EnterpriseLook.CHROME` on macOS, Windows and Linux.
    - `chrome-covering-array.tsv` has nine columns and holds `icons` at `classic` (`ChromeRenderTest.java:63-77`, `Icons.CLASSIC` at `:74`).
    - `PresentationConformanceTest.KEYS` lists `chrome/...` (`jasper-app/src/test/java/dev/jasper/app/plugins/PresentationConformanceTest.java:29-31`).
    - `HostedPresentationTest.enterpriseIsReportedFromItsKitAndItsDefaults` expects `Presentation.ToolbarSearch.FIELD` (`:25-31`). `FakePresentations.CLASSIC` takes `ToolbarSearch.FIELD` and its javadoc already says "Enterprise's on Linux" (`jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePresentations.java:16-19`); no plugin test reads its `toolbarSearch`.
13. The mocks:
    - `EnterpriseWorkstationMockTest.theToolbarRowIsTheMocks` asserts the field (x 2116-2715 and y 122-165 in the mock; `jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseWorkstationMockTest.java:123-131`) and then crops Settings… from 44 mock px left of x 2722 (`:132-143`). The field's bounds come from `EnterpriseMenuRowMockTest.Mounted.field`, read before the window closes (`EnterpriseMenuRowMockTest.java:61`, `:83-87`).
    - `tools/visual/pairs.json`'s `chrome-toolbar-light` and `chrome-toolbar-dark` (`:893-938`) crop the toolbar row, field included, at a 0.30 floor (0.581 and 0.577 in `plan15-baseline/pairs.txt`).
    - The worktree's `jasper-ui/build/gallery` holds only `dark-1x` and `light-1x` now: the seven `enterprise-light-2x` pairs need `:jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryScale=2` before `check_pairs.py`.

**Identity**
14. No production class in jasper-app, jasper-ui or a bundled plugin other than Enterprise names Enterprise outside comments (checked at `e858a4ecb`: every mention in their `src/main/java` is a comment). The architecture tests read the app and ui jars through `System.getProperty("jasper.appJar")` and `"jasper.uiJar"` with `StaticDefaultTest.classFiles(Path, int)` (`architecture/VariantReadTest.java:37-38`, `StaticDefaultTest.java:160-167`, package-private in `architecture`). `ProjectionReadTest.Scanned` (`:53-59`) is package-private in `architecture` too. `ManagedVaultLoadingTest.jars(Path)` (`plugins/ManagedVaultLoadingTest.java:109`) is package-private in `plugins`. The staged plugins are under `System.getProperty("jasper.stagedPlugins")/<id>/`, one folder per plugin id (`dev.jasper.enterprise`, `dev.jasper.remote`, …), each holding its own jar and its bundled libraries (`jasper-app/build.gradle.kts:91`).
15. `ThemeMigrationCoverageTest` scans `jasper-app/src`, `jasper-sdk/src`, `jasper-sdk-testkit/src` and every `plugins/*/src`, not jasper-ui (`appearance/ThemeMigrationCoverageTest.java:64-66`). Its `CANDIDATE` matches `new ArrayList` (`new [A-Z]\w*List\b`), so a new test that builds an `ArrayList` needs a row.

## Rulings

Each ruling is what was decided, why, and the cost if it is wrong.

1. **Activation.** A `Panels.toggle`, or a `PanelHost.show()`, made synchronously inside the handler of the panel's own declared toggle action counts as activating: the same plugin, while that action's handler is running, for any window it names. A deferred call, another action's, or one with no running action stays plain. This is IntelliJ's ⌘1, and it answers the plan-12 STATUS question (the user: yes). The show path is host-only, so Transfers' action activates on every press without a Remote change; it still only shows, so a second ⌘⌥3 keeps Transfers open. Cost: a plugin that toggles from `invokeLater` loses activation; and a toggle or show made from an event that a nested event loop inside the handler dispatches (a modal dialog's, such as `WindowSurface.chooseFile`'s) counts as activating. No bundled toggle action does this. Telling them apart by `EventQueue.getCurrentEvent()` would make a toggle made after such a dialog closes plain, since the JDK does not restore the current event after a nested dispatch (final review).
2. **Activation needs no SDK or testkit change.** No SDK value reports it, and the testkit has no dock or focus. `Panels.toggle`'s javadoc and the guide say it. Cost: none.
3. **The scope of "classic icons everywhere".** The six classic names in tool-window headers (the app-drawn header's actions, a borrowed header's host actions and the panel's own header controls) and in toolbar views. Panel and window bodies keep the catalog: the classic set is IntelliJ's chrome set. Cost: a plugin's body "add" button differs from its header's.
4. **The mechanism.** Classic art only for the six names. Every other name keeps exactly today's art, with no New UI fallback, so the default look and the other names are byte-identical and Enterprise's other header icons do not move.
   - jasper-ui: a `KitView` flag set by the host, `KitView.classicChromeIcons(boolean)`.
   - The app: `AppIcons.classicChromeIcon(Icon)`, and `ToolWindowArea` taking the window's `ChromeArtwork`.
   - Cost: two marks to keep in step, the receiver and the view flag.
5. **The kit key is `toolbar_search`, values `field` and `icon`.** It is clearer than `search` beside the `palette` point. Cost: none.
6. **`toolbar_search = "icon"` needs `palette = "run_window"` and `title_bar` `native` or `classic`.** The anchored card's home is the field (`TitleFieldHome`), and the unified header holds the field in the title bar. Cost: a third-party look wanting the anchored card with an icon is refused.
7. **The search button.**
   - It is icon-only in every toolbar mode, as IntelliJ's.
   - Its accessible name and tooltip are IntelliJ's action name: "Search Everywhere", then " (" + the palette's shortcut + ")" when bound.
   - It sits first in the trailing group, before Settings…, at priority 100.
   - It runs `COMMAND_PALETTE`, exactly what activating the field does.
   - The field keeps the mocks' "Search everywhere".

   Cost: `icons_and_labels` users see no label on it.
8. **The covering array holds `toolbar_search` at `field`**, as it holds `icons` at `classic`. `icon` is covered by `ClassicHeaderTest` and the shipped `enterprise` renders. The array's strength is unchanged. Cost: `icon` is never pair-rendered with the other chrome points.
9. **No version moves** (Global Constraints). Cost: none.
10. **The identity guard.**
    - The scan covers the constant-pool UTF-8 entries of every class in the app and ui jars, and of every class under `dev/jasper/` in each staged plugin's jars except `dev.jasper.enterprise`'s, for "enterprise" in any case. Bundled third-party libraries are not ours to police.
    - Comments do not reach class files; data files (kit, theme, notices) are not scanned.
    - Cost: a future legitimate identifier containing "enterprise" must be renamed or exempted in the test.
11. **`FakePresentations.CLASSIC` moves to `ToolbarSearch.ICON`**, since it stands for Enterprise. Cost: a plugin test asserting `FIELD` under `CLASSIC` would break; none does.
12. **Reuse plan 15a's baseline.** 15a proved its tip identical to it. Cost: none.
13. **One spec header line for plan 15.** This plan adds its sections to plan 15a's line. Cost: none.

---

### Task 1: A panel's own toggle action activates it

**Files:**
- Modify:
  - `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java:196-216,413-462` (`actions()`, `panels()` and `host(site)`)
  - `jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java:55-60`
  - `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java:183-210`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/workspace/WindowPanelsTest.java` (beside plan 12's activation tests, `:124-172`; Execution notes X1)
  - `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Panels.java:19-28`
  - `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelSpec.java:16-18`
  - `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelHost.java:23`

**Interfaces:**
- Produces:
  - in `HostedUi`, a private `String running` field (the id of this plugin's action whose handler is running on the UI thread, or null). The action handler sets it before `handler.accept` and restores the previous value in `finally`. `Panels.toggle` sends `Op.ACTIVATING_TOGGLE` when the toggled panel's `PanelEntry.namedToggle()` is true and `running` equals its `toggleActionId()`, else `Op.TOGGLE` (Execution notes X2).
  - `Contributions.PanelRequest.Op.ACTIVATING_SHOW`, javadoc "A show by the panel's own toggle action: the panel becomes the active tool window under flush frames (plan 15b Rulings 1)."
  - `HostedUi.host(site)` becomes `host(site, spec)`, called from the panel factory where the `PanelSpec` is known. Its `show()` keeps `requireUi("show")`, then sends `model.requestPanel(new Contributions.PanelRequest(site.windowId(), spec.id(), Op.ACTIVATING_SHOW))` when the host is not closed and `spec.toggleActionId()` is present and equal to `running`; otherwise it calls `site.show()` as today.
  - `WindowContributions.requested` maps `ACTIVATING_SHOW` to a private `showAndActivate(id)`: like `toggleAndActivate`, it sets `activating` only under flush frames around `dock.show(id)`, then calls `owner.area().activate(id)` when `dock.visible(id)`.

- [ ] **Step 1: Write the failing tests**

In `HostedUiTest`:

```java
/** Plan 15b (Rulings 1): the panel's own toggle action activates it, as IntelliJ's ⌘1; anything else toggles. */
@Test void aPanelsOwnToggleActionActivatesAndAnythingElseToggles() {
    var window = UUID.randomUUID();
    var requests = new ArrayList<Contributions.PanelRequest>();
    model.onPanelRequest(requests::add);
    WindowHandle[] handle = {null};
    ui.actions().register(ActionSpec.of("dev.x.tool.hosts.show", "Hosts"), context -> { handle[0] = context.window(); ui.panels().toggle("dev.x.tool.hosts", context.window()); });
    ui.actions().register(ActionSpec.of("dev.x.tool.other", "Other"), context -> ui.panels().toggle("dev.x.tool.hosts", context.window()));
    ui.panels().register(new PanelSpec("dev.x.tool.hosts", "Hosts", "file", Anchor.RIGHT).withToggleActionId("dev.x.tool.hosts.show"), host -> view());
    model.action("dev.x.tool.hosts.show").orElseThrow().invoke(new Contributions.Invocation(window, Optional.empty()));
    model.action("dev.x.tool.other").orElseThrow().invoke(new Contributions.Invocation(window, Optional.empty()));
    ui.panels().toggle("dev.x.tool.hosts", handle[0]);
    assertThat(requests).extracting(Contributions.PanelRequest::op).containsExactly(
        Contributions.PanelRequest.Op.ACTIVATING_TOGGLE, Contributions.PanelRequest.Op.TOGGLE, Contributions.PanelRequest.Op.TOGGLE);
}
```

Qualify `PanelSpec`, `Anchor` and `WindowHandle` as the file does.

Also add `aShowInsideThePanelsOwnToggleActionActivates`: first register `dev.x.tool.queue.show` and `dev.x.tool.queue.other`, each of whose handlers calls `hosts[0].show()` on a one-element `PanelHost[] hosts` (`register` refuses a toggle action that is not registered yet, fact 2); then register a panel `dev.x.tool.queue` with `withToggleActionId("dev.x.tool.queue.show")` whose factory stores its `PanelHost` in `hosts[0]`; build it in a window with `entry.factory().apply(site)` over a `PanelSite` that records its `show` runnable, as `panelsAndRailReachTheModelWithContainedFactories` does (fact 4). Invoking the first records one request with `Op.ACTIVATING_SHOW` for that window and panel. Invoking the second, and calling `show()` outside any action, record no request: they go straight to the site, as today.

In `WindowPanelsTest`, using plan 12's flush fixture (fact 4a: `DockChromeTest.classicDock()` for flush frames, the default `DesktopTestSupport.content(launcher)` for standard ones), sending each request with `model.requestPanel(new Contributions.PanelRequest(owner.id(), id, op))`:
- `aPanelsActivatingToggleUnderFlushFramesActivatesIt`: a flush-framed window gets `ACTIVATING_TOGGLE` for a hidden panel. The panel shows, and the area marks it focused; read `ToolWindowArea`'s focused slot as the plan-12 activation tests do.
- `anActivatingToggleThatHidesThePanelActivatesNothing`: the same request for a showing panel hides it, and no slot is focused.
- `underStandardFramesAnActivatingToggleIsAPlainToggle`: under standard frames the request toggles, and nothing is focused.
- `anActivatingShowUnderFlushFramesActivatesThePanel`: `ACTIVATING_SHOW` for a hidden panel shows and focuses it; sent again while it shows, it stays shown and focused (a show never hides).
- `underStandardFramesAnActivatingShowIsAPlainShow`: the panel shows, and nothing is focused.

Copy the plan-12 tests' way of reading the focused slot (`owner.area().focusedSlot()`, and `owner.area().focusMoved(null)` to clear it between steps).

Run: `./gradlew :jasper-app:test --tests '*HostedUiTest' --tests '*WindowPanelsTest'`
Expected: FAIL to compile (`Op.ACTIVATING_SHOW` does not exist). With the constant alone, `aPanelsOwnToggleActionActivatesAndAnythingElseToggles` would fail on `TOGGLE` first, and the three `ACTIVATING_TOGGLE` cases in `WindowPanelsTest` pass already, since the window handles that op; keep them as the guard.

- [ ] **Step 2: Implement**

`HostedUi`:
- the action handler becomes `containment.run(pluginId, "action " + spec.id(), () -> { String previous = running; running = spec.id(); try { handler.accept(...); } finally { running = previous; } })`;
- `toggle` keeps the `PanelEntry` its existing `noneMatch` check finds (as a `filter(...).findFirst()`), and sends `ACTIVATING_TOGGLE` when that entry's `namedToggle()` is true and `running` equals its `toggleActionId()`, else `TOGGLE`.
- `host(site, spec)` and its `show()` as in Interfaces.

`Contributions.PanelRequest.Op.ACTIVATING_SHOW` and `WindowContributions.showAndActivate` as in Interfaces; `ACTIVATING_TOGGLE`'s javadoc stays.

Docs in the same commit:
- `Panels.toggle`'s javadoc gains: "Called from the panel's own toggle action ({@link PanelSpec#withToggleActionId}), as its handler runs, a toggle that shows the panel also makes it the active tool window under a look with flush tool windows, as a stripe click does (since 0.18.2)."
- `PanelSpec`'s `@param toggleActionId` gains: "; under a look with flush tool windows its toggle or show also activates the panel".
- `PanelHost.show()`'s javadoc gains: "Called from the panel's own toggle action as its handler runs, it also makes the panel the active tool window under a look with flush tool windows (since 0.18.2)."

Run: `./gradlew :jasper-app:test --tests '*HostedUiTest' --tests '*WindowPanelsTest' --tests '*WindowContributionsTest' --tests '*PanelContributionsTest' --tests '*NotificationsFeatureTest' --tests '*AppContractTest' && ./gradlew :jasper-sdk:javadoc`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add jasper-app jasper-sdk/src/main/java/dev/jasper/sdk/ui/Panels.java jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelSpec.java \
        jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelHost.java
git commit -m "Plan 15b Task 1: a panel's own toggle action activates its tool window

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Classic icons in tool-window headers and toolbar views

**Files:**
- Modify (jasper-ui):
  - `jasper-ui/src/main/java/dev/jasper/ui/icons/BuiltInArtwork.java:35-57` (a `CLASSIC_NAMES` constant outside the holder; Execution notes X4)
  - `jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java:86-108` (beside `chromeIcon` and `stripeIcon`)
  - `jasper-ui/src/main/java/dev/jasper/ui/swing/KitView.java:406-418`
  - `jasper-ui/src/main/java/dev/jasper/ui/swing/Buttons.java:17-43,74-92`
  - `jasper-ui/src/main/java/dev/jasper/ui/swing/Menus.java:216-228`
- Modify (app):
  - `jasper-app/src/main/java/dev/jasper/app/platform/AppIcons.java` (beside `stripeIcon`, `:96-121`)
  - `workspace/ToolWindowHeader.java:84-105,219-236`
  - `workspace/ToolWindowArea.java:297-318,578-614,665-671`
  - `workspace/WindowContent.java:283`
  - `workspace/WindowChrome.java:328-331`
- Create:
  - `jasper-ui/src/test/java/dev/jasper/ui/swing/KitClassicChromeIconsTest.java` (jasper-ui is outside `ThemeMigrationCoverageTest`'s scan, fact 15: no row)
- Modify (tests):
  - `jasper-ui/src/test/java/dev/jasper/ui/icons/BuiltInArtworkTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/workspace/ToolWindowHeaderTest.java`
  - `ClassicHeaderTest.java`
  - `docs/design/theme-engine/migration.csv` only if `ThemeMigrationCoverageTest` reports a missing row (none is expected: no new app source)

**Interfaces:**
- Produces:
  - jasper-ui `IconRegistry`:
    - `public java.util.Optional<PreparedIcon> classicChromeIcon(String name, int logicalSize)`, which returns `chromeIcon(name, logicalSize, ChromeArtwork.CLASSIC)` for one of the six classic names, else empty;
    - `public static java.util.Set<String> classicChromeNames()`, the six names, from a `BuiltInArtwork.CLASSIC_NAMES` `Set.of(...)` constant declared outside `ClassicHolder`, so asking never decodes the classic files (fact 5). `BuiltInArtworkTest` (same package, which already reads `classicChrome()`) gains `theClassicNamesAreTheClassicHoldersKeys`, asserting `IconRegistry.classicChromeNames()` equals `BuiltInArtwork.classicChrome().keySet()` (Execution notes X4); add it to this task's Files and run it with `KitClassicChromeIconsTest`.
  - `KitView`:
    - `public void classicChromeIcons(boolean value)`, which refreshes and repaints on change, as `toolbarStrip` does;
    - `public boolean classicChromeIcons()`.

    While set, a `Button` or `MenuButton` in a toolbar strip or in the panel header (`button.panel && !button.documentBar`, `main.panel && !main.documentBar` for a menu: a document bar in a panel body also sets `panel`, fact 8), and a host header action with canonical artwork, draw a classic name's `classicChromeIcon` art, tinted `TEXT_PRIMARY` like the window header. In a header strip the art stays authored: the strip ink is not applied. Every other icon is unchanged. Each check reads the flag first, so a view without it never asks for a classic name (Execution notes X3).
  - App `AppIcons`: `public static java.util.Optional<Icon> classicChromeIcon(Icon icon)`. For a built-in `PresentationIcon` or `SemanticIcon` whose kit name is one of the six (`kitName`, as `stripeIcon` reads it), it returns an `Icon` of the same size. That icon paints `NAMED.classicChromeIcon(name, size)` in the receiving component's theme, prepared on first paint at the device size. Any other icon gives empty.
  - `ToolWindowHeader(String text, Supplier<JPopupMenu> menu, Runnable onHide, Kit kit, boolean flush, ChromeArtwork artwork)`. The five-argument form means `NEW_UI`. Under `CLASSIC` a contributed action whose `AppIcons.classicChromeIcon` is present shows that icon, in place of `stripGlyph` or the action's icon.
  - `ToolWindowArea(JComponent, DockState, Kit, boolean flush, ChromeArtwork artwork)`. The two- and four-argument forms mean `NEW_UI`. Under `CLASSIC` it passes the artwork to each header (`header(id, title)`, `:665-671`) and calls `view.classicChromeIcons(true)` on a borrowed kit view in `mountHeader` just before `setPanelHostActions` (`:586`); the release path (`:609-613`) leaves the flag, since the view lives only in this window's area. Under `NEW_UI` it never calls it.
  - `WindowContent:283` passes `look().chromeArtwork()` (`look()` is the window's `WorkspaceChrome`). `WindowChrome:329` adds `kit.classicChromeIcons(owner.look().chromeArtwork() == ChromeArtwork.CLASSIC);` after `kit.toolbarStrip(true);`.

- [ ] **Step 1: Write the failing jasper-ui test**

`KitClassicChromeIconsTest`. Use `dev.jasper.ui.testsupport.KitFixtures.context()`, `KitFixtures.render(view, w, h, 1)` and `Edt.run`, closing the context's binding and icons after, as `KitViewToolbarStripTest.pixels` does (fact 8). Render a `KitView` of an INLINE stack of icon-only GHOST buttons named `search`, `add`, `folder` and `more`, with `toolbarStrip(true)`.
- `theSixNamesInAToolbarStripDrawIntelliJsClassicArt`: with `classicChromeIcons(true)`, the `search` and `add` buttons' centres equal `context.icons().classicChromeIcon(name, size).orElseThrow().icon(theme, SemanticColor.TEXT_PRIMARY)` pixel for pixel, as `ClassicHeaderTest.assertPaintsClassicAdd` checks.
- `onlyTheSixNamesInHeadersAndToolbarStripsChange`:
  - with the flag, `folder` and `more` paint exactly what they paint without it;
  - the same `add` button in a panel body, a VERTICAL stack outside any header, paints exactly what it paints without the flag;
  - in a `Nodes.Panel` header, an `add` header control and a host action with `PanelHostHeader.Artwork("add", null)` paint the classic art: host actions with artwork reach the view through `setPanelHostHeader(panel, new PanelHostHeader(detail, active, List.of(new PanelHostHeader.Action(label, icon, run, new PanelHostHeader.Artwork("add", null))), frame))`, as `ToolWindowArea.hostHeader` builds them (`PanelHostHeaderTest` has the fixture); `setPanelHostActions` takes plain `Nodes.Button`s;
  - an `add` button in a document bar inside the panel body (`button.panel` and `documentBar`, fact 8) paints exactly what it paints without the flag.
- `withoutTheFlagNothingChanges`: every button paints the same with the flag off as a view that never had it.

Run: `./gradlew :jasper-ui:test --tests '*KitClassicChromeIconsTest' --tests '*BuiltInArtworkTest'`
Expected: FAIL to compile (`classicChromeIcons` does not exist).

- [ ] **Step 2: Implement jasper-ui**

- `IconRegistry.classicChromeIcon` and `classicChromeNames`.
- `KitView`'s flag.
- `ButtonControl.refresh`: when `view.classicChromeIcons()` and (`button.toolbarStrip` or (`button.panel` and not `button.documentBar`)) and the icon name is one of the six, use the classic art at the extent it computes. In the `hostArtwork != null` branch (`:84`), do the same when `hostArtwork.semantic` is non-null with a null `pathData()` and its name is one of the six; `HostArtwork.icon`'s callers keep their signatures (add an overload taking the flag if that is simpler).
- The `MenuButton` path, `Menus.MenuControl`'s `main.setIcon(` (`Menus.java:216`), gets the same check (`view.toolbarStrip()` or (`main.panel` and not `main.documentBar`)).

Run: `./gradlew :jasper-ui:test`
Expected: PASS. The gallery is unchanged: no gallery view sets the flag.

- [ ] **Step 3: Write the failing app tests**

`ToolWindowHeaderTest`. Make headers with the six-argument constructor and the Enterprise kit (`EnterpriseLook.kit()`, flush), under `EnterpriseLook.controller(false)`, and restore the default look after.
- `underClassicIconsAContributedAddActionDrawsIntelliJsClassicArt`: a `PanelHeaderState` with one action whose icon is `AppIcons.named("ADD")`. Its button's centre equals `new IconRegistry().classicChromeIcon("add", size).orElseThrow().icon(theme, TEXT_PRIMARY)`, painted as in `assertPaintsClassicAdd`.
- `otherNamesKeepTheirGlyphUnderClassicIcons`: an action iconed `folder`, and one with a plugin path icon, paint the same under `CLASSIC` as under `NEW_UI`.
- `underNewUiNothingChanges`: the six-argument constructor with `NEW_UI` paints the add action exactly as the five-argument one does.

In `ClassicHeaderTest`, add `aToolbarViewUnderClassicIconsIsMarkedAndUnderNewUiIsNot`: a `KitView` toolbar entry is mounted with `classicChromeIcons()` true under `WorkspaceLook.Icons.CLASSIC` and false under `NEW_UI`. Build the window with `classic(icons)` as `window(themes, view, icons)` does, and make and mount the real `KitView` as `WindowToolbarViewsTest.aKitViewIsPresentedAsAToolbarStrip` does (`KitContext.of(...)`, `model.addToolbar(new ToolbarEntry.View(window -> Optional.of(new ToolbarEntry.Mounted(view, view::close))))`, after `connectTerminals`; fact 9), closing its binding and icons after.

Run: `./gradlew :jasper-app:test --tests '*ToolWindowHeaderTest' --tests '*ClassicHeaderTest'`
Expected: FAIL to compile.

- [ ] **Step 4: Implement the app side**

Implement as in Interfaces. Add a migration row only if `ThemeMigrationCoverageTest` reports one (none is expected).

Run: `./gradlew :jasper-app:test --tests '*ToolWindowHeaderTest' --tests '*ClassicHeaderTest' --tests '*DockMockTest' --tests '*ClassicCompositionRenderTest' --tests '*ClassicVaultRenderTest' --tests '*EnterpriseWorkstationMockTest' --tests '*DefaultDockPixelsTest' --tests '*ChromeRenderTest' --tests '*ThemeMigrationCoverageTest' --tests '*ProjectionReadTest' --tests '*VariantReadTest'`
Expected: PASS.
- `DefaultDockPixelsTest` and the default `ChromeRenderTest` images are unchanged.
- Where an Enterprise mock test's probe falls on a classic name (`add`, `settings`, `search`, …) in Sessions', Tunnels' or another tool window's header, update the probe to the classic art and note the user's 2026-10-19 direction in a comment. A document bar's `add` in a panel body (the Vault manager's) must not move; if it does, the `documentBar` exclusion is wrong.
- List every such change for Task 6's STATUS entry.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-ui jasper-app docs/design/theme-engine/migration.csv
git commit -m "Plan 15b Task 2: classic icons in tool-window headers and toolbar views

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: The `[chrome] toolbar_search` point

**Files:**
- Modify:
  - `presentation/ChromeSpec.java` (javadoc `:5-20`, record `:21-22`, constants `:46-53`, constructor `:56-65`)
  - `presentation/KitParser.java:63-64,306-322`
  - `presentation/WorkspaceLook.java`
  - `presentation/Projections.java:99-106,115-118` (`of`: `WorkspaceLook` and `PluginLook.search`; `fallback` at `:150-159` keeps `FIELD` through the eight-argument form)
  - `workspace/WorkspaceChrome.java:17-54,74-87,100-105,130-136`
  - `workspace/ClassicHeader.java` (the two constructors only)
  - `jasper-app/src/test/java/dev/jasper/app/presentation/KitParserTest.java` (`:157`, `:249-270`)
  - `ProjectionsTest.java` (`:119`, `:168`)
  - `architecture/ProjectionReadTest.java:25,104-118`
  - `testsupport/EnterpriseLook.java:22-39`
  - `workspace/ChromeRenderTest.java:70-74`

**Interfaces:**
- Produces:
  - `ChromeSpec`:
    - `public enum ToolbarSearch { FIELD, ICON }`, javadoc "The toolbar's Search Everywhere field, or IntelliJ Classic's icon button.";
    - the record's last component `ToolbarSearch toolbarSearch`;
    - `DEFAULT` and `CLASSIC` take `FIELD`;
    - the class javadoc's "The ten chrome variant points … eight two-valued points" becomes "The eleven chrome variant points … nine two-valued points", and it gains `@param toolbarSearch {@code toolbar_search}; {@code icon} needs the Run window and a native or classic title bar`;
    - the compact constructor refuses `ICON` unless `palette == RUN_WINDOW && titleBar != UNIFIED`, with `IllegalArgumentException("toolbar_search = icon needs palette = run_window and title_bar = native or classic")`.
  - `KitParser.CHROME` gains `"toolbar_search"`, parsed by `point`. After the `menu_bar` check, the parser refuses `ICON` with `[chrome].toolbar_search: "icon" needs [chrome].palette = "run_window" and [chrome].title_bar = "native" or "classic"`.
  - `WorkspaceLook`:
    - `public enum Search { FIELD, ICON }`, javadoc "The classic toolbar's search field, or its icon button.";
    - a ninth component `Search search` with `@param search how the classic toolbar offers Search Everywhere`, required non-null;
    - the eight-argument constructor means `Search.FIELD`. Under `Header.UNIFIED` the search is ignored (a parsed kit never pairs them, fact 12).
  - `Projections.of`: `WorkspaceLook.search` is `ICON` when `chrome.toolbarSearch() == ICON` (the constraint guarantees a classic header), and `PluginLook`'s `search` is the same.
  - `WorkspaceChrome`:
    - a `boolean iconSearch` read once in `of` from `look.search()` (a `switch`, as `look.icons()` is read), passed to the private constructor as its last argument; `STANDARD` passes `false`;
    - `header()` returns `new ClassicHeader(iconSearch)` for `CLASSIC`. Until Task 4, `ClassicHeader` takes and ignores the flag, through a one-line constructor storing it in a `private final boolean iconSearch`. `ClassicHeader` declares no constructor today, so Task 3 adds `ClassicHeader() { this(false); }` explicitly; it means the field (`WindowPairsTest.java:93` uses it);
    - `equals` and `hashCode` include `iconSearch`.
  - Test-only `EnterpriseLook.titled` copies `toolbarSearch`. `EnterpriseLook.CHROME` stays `FIELD` until Task 4, when the shipped kit names `icon`.

- [ ] **Step 1: Write the failing tests**

- `KitParserTest`:
  - `theToolbarSearchPointDefaultsToTheFieldAndTakesTheIcon`. `kit = 1`, `base = "classic"` and no `[chrome]` give `FIELD`. `[chrome]` with `toolbar_search = "icon"` (the classic base has `run_window` and `native`) gives `ICON`.
  - New `cases` in `everyInvalidKitGivesOneMessageNamingTheKey`:
    - `head + "[chrome]\ntoolbar_search = \"icon\"\n"` on a default base gives `[chrome].toolbar_search: "icon" needs [chrome].palette = "run_window" and [chrome].title_bar = "native" or "classic"`;
    - `"kit = 1\nbase = \"classic\"\n[chrome]\ntoolbar_search = \"icon\"\npalette = \"anchored\"\n"` gives the same message;
    - `toolbar_search = "box"` gives `[chrome].toolbar_search: "box" is not one of field, icon`.
  - `everyChromeAndControlPointTakesBothItsValues` is unchanged: it loops over the control points only (fact 12), and the new test above gives both values (Execution notes X6).
- `ProjectionsTest.theToolbarSearchReachesTheWorkspaceAndPlugins`: a classic-base `ChromeSpec` with `ICON` gives `workspace().search() == ICON` and `plugins().search() == ICON` on each host. `ChromeSpec.DEFAULT` gives `FIELD` for both.
- `ProjectionReadTest`: the `WorkspaceLook` pattern's alternation gains `Search`. In `aLookReadElsewhereIsReported`, add a `WorkspaceLook$Search` probe reported on its own, as the `Icons` one is.

Run: `./gradlew :jasper-app:test --tests '*KitParserTest' --tests '*ProjectionsTest' --tests '*ProjectionReadTest'`
Expected: FAIL to compile.

- [ ] **Step 2: Implement**

Implement as in Interfaces. Update every `new ChromeSpec(` of fact 12 with `ChromeSpec.ToolbarSearch.FIELD`, except `EnterpriseLook.titled`, which passes `spec.toolbarSearch()`. `ChromeRenderTest.covering()` passes `FIELD` (Rulings 8); add a comment beside the `Icons.CLASSIC` it already passes.

Run: `./gradlew :jasper-app:test --tests '*KitParserTest' --tests '*ProjectionsTest' --tests '*ProjectionReadTest' --tests '*ChromeRenderTest' --tests '*EnterpriseFixtureTest' --tests '*HostedPresentationTest' --tests '*ClassicHeaderTest'`
Expected: PASS. Every render is unchanged: nothing names `icon` yet.

- [ ] **Step 3: Commit**

```bash
git add -A jasper-app
git commit -m "Plan 15b Task 3: the [chrome] toolbar_search point

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: The classic header's Search Everywhere icon, and Enterprise names it

**Files:**
- Modify:
  - `workspace/ClassicHeader.java` (javadoc `:26-30`, `trailing()` `:36-37`, `build` `:39-57`, `layout` `:68-88`)
  - `workspace/ClassicToolbarButton.java:31-43`
  - `workspace/WindowChrome.java` (a `paletteShortcut()` accessor, beside `composition()` at `:176`)
  - `plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml:4-14`
  - `jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java:18-25`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/PresentationConformanceTest.java:29-31`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/HostedPresentationTest.java:25-31`
  - `jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java:218-232` (Execution notes X8)
  - `jasper-app/src/test/java/dev/jasper/app/workspace/ClassicHeaderTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseMenuRowMockTest.java:55-88` (`Mounted` gains the search button's bounds; Execution notes X9)
  - `EnterpriseWorkstationMockTest.java:99-144`
  - `tools/visual/pairs.json` (the two `chrome-toolbar-*` entries, `:893-938`)
  - `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePresentations.java:16-19`

**Interfaces:**
- Consumes: `WorkspaceChrome.header()` giving `new ClassicHeader(iconSearch)` (Task 3).
- Produces:
  - `ClassicToolbarButton.tooltip(java.util.function.Supplier<String> source)` (package-private). While set, `getToolTipText()` returns `source.get()`.
  - `String WindowChrome.paletteShortcut()` returns `owner.paletteShortcut()`.
  - `ClassicHeader(boolean iconSearch)`:
    - Under `iconSearch`, `build` first adds `chrome.classicButton(trailing, ActionId.COMMAND_PALETTE, "Search Everywhere", "search", 100)`, then Settings….
    - The search button's text is set to null, `keepLabel` to `false`, and its tooltip to `() -> chrome.paletteShortcut().isBlank() ? "Search Everywhere" : "Search Everywhere (" + chrome.paletteShortcut() + ")"`.
    - `layout` gives the field zero bounds and hides it, so the toolbar runs up to the trailing group.
    - The field path is unchanged when `iconSearch` is false.
    - A package-private `JButton searchButton()` returns the button, or null, for tests; tests reach the composition through `owner.chrome().composition()` (fact 10).
    - `trailing()`'s javadoc "The trailing group: "Settings…"." becomes "The trailing group: "Search Everywhere" under the icon, then "Settings…".".

- [ ] **Step 1: Write the failing tests**

In `ClassicHeaderTest`:
- Give `classic(...)` and `window(...)` an overload taking `WorkspaceLook.Search`, keeping today's overloads at `FIELD`; Task 2's `aToolbarViewUnderClassicIconsIsMarkedAndUnderNewUiIsNot` keeps using today's.
- Generalise `assertPaintsClassicAdd(button)` (`:289-311`) into `assertPaintsClassic(button, name)`, with `assertPaintsClassicAdd(button)` delegating with `"add"`, so its existing callers and Task 2's stay.
- Add, under `WorkspaceLook.Icons.CLASSIC` and `Search.ICON`:
  - `underTheSearchIconTheFieldIsAClassicMagnifierBeforeSettings`:
    - the field is invisible with zero width;
    - `labels(trailing)` is `["Search Everywhere", "Settings…"]`;
    - the search button's preferred size is the 22 px square;
    - it paints exactly `new IconRegistry().chromeIcon("search", size, ChromeArtwork.CLASSIC)`'s art at its centre (generalise `assertPaintsClassicAdd` to take the name);
    - its tooltip is "Search Everywhere" plus " (" + the shortcut + ")" when the window's palette shortcut is bound;
    - its accessible name is "Search Everywhere";
    - clicking it (`doClick()`) runs the window's `COMMAND_PALETTE` action exactly once, observed as `RunWindowModelessTest` observes the Run window opening, or through a counting action installed in the window's action map for `COMMAND_PALETTE`.
  - `theSearchButtonStaysIconOnlyInEveryToolbarMode`: under `setToolbarMode(ICONS_AND_LABELS)` its text stays null while Settings… shows its label. Under `HIDDEN` the header is invisible.
  - `underAHiddenToolbarTheShortcutStillOpensTheRunWindow`: with `HIDDEN`, invoking `COMMAND_PALETTE` through the window's action, as the shortcut does, still runs it.
  - `underTheSearchIconANarrowWindowKeepsTheButtonBesideSettings`: at widths 1440, 700 and 420, the search button stays visible, with its right edge at or left of Settings…'s left edge, and no overlap.
  - `theFieldIsUnchangedUnderSearchField`: with `FIELD` the existing field assertions hold, and the trailing group is `["Settings…"]`.
- `HostedPresentationTest.enterpriseIsReportedFromItsKitAndItsDefaults`: expect `Presentation.ToolbarSearch.ICON`.
- `ProjectionsTest.thePluginLookIsTheLookOnEachPlatform`: its three `EnterpriseLook.CHROME` expectations (macOS, Windows, Linux) take `PluginLook.Search.ICON`; the default-look and fallback expectations stay `FIELD`.
- `PresentationConformanceTest.KEYS` gains `"chrome/toolbar_search"`.

Run: `./gradlew :jasper-app:test --tests '*ClassicHeaderTest' --tests '*HostedPresentationTest' --tests '*PresentationConformanceTest' --tests '*ProjectionsTest'`
Expected: FAIL.

- [ ] **Step 2: Implement and name it in Enterprise's kit**

- `ClassicHeader`, `ClassicToolbarButton.tooltip` and `WindowChrome.paletteShortcut` as in Interfaces. The class javadoc gains: "Under `toolbar_search = "icon"` (the user, 2026-10-19), an icon-only "Search Everywhere" button with IntelliJ's classic magnifier leads the trailing group in place of the field."
- `enterprise.kit.toml`: after `icons = "classic"` add `toolbar_search = "icon"`.
- `EnterpriseLook.CHROME` gains `ChromeSpec.ToolbarSearch.ICON` (`EnterpriseFixtureTest` holds it equal to the shipped table).
- `FakePresentations.CLASSIC` takes `ToolbarSearch.ICON`; its javadoc already says "Enterprise's on Linux" (Rulings 11), so only the value changes.

Run: `./gradlew :jasper-app:test --tests '*ClassicHeaderTest' --tests '*HostedPresentationTest' --tests '*PresentationConformanceTest' --tests '*ProjectionsTest' --tests '*EnterpriseFixtureTest' && ./gradlew :jasper-sdk-testkit:test`
Expected: PASS.

- [ ] **Step 3: Re-measure the Enterprise mock expectations**

Run: `./gradlew :jasper-app:test --tests '*EnterpriseWorkstationMockTest' --tests '*EnterpriseMenuRowMockTest' --tests '*WindowHeaderTest' --tests '*CollapsingMenuBarTest' --tests '*RunWindowModelessTest' --tests '*ChromeRenderTest' --tests '*EnterpriseFirstWindowTest' --tests '*EnterpriseHomeTest'`

Expected: `EnterpriseWorkstationMockTest`'s search-field assertions (`:123-131`) FAIL, since the field is gone. First give `EnterpriseMenuRowMockTest.Mounted` a `Rectangle search` component (the search button's bounds in the root, read beside `fieldAt` from `((ClassicHeader) owner.chrome().composition()).searchButton()`, or an empty `Rectangle` when there is none), with its `@param`. Then replace the field assertions:
- assert the search button's right edge is 6 px (12 mock px) before Settings…'s left, the trailing group's gap (`ClassicHeaderTest`'s "6 before Settings…", `:113`);
- assert its top and size match the other 22 px icon squares, as the toolbar's icon run is asserted;
- comment: "The user's 2026-10-19 direction: IntelliJ Classic's Search Everywhere icon in place of the mocks' field (x 2116-2715)."

Fix any other failure the same way, each with that note. Do not move an expectation that is not about the field or the new button. The Settings… crop (`:132-143`) starts 44 mock px left of x 2722 and may now take the magnifier's ink as a third run: if so, start the render crop at the search button's right edge instead, with the note.

Then the pairs. The worktree has no Enterprise gallery (fact 13), so render it first; the other pairs read whatever renders `build/` holds, so only the two `chrome-toolbar-*` lines count here, and Task 6 Step 5 gives the full verdict:

```bash
for theme in light dark; do ./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=$theme -Pjasper.galleryScale=2; done
python3 -I tools/visual/check_pairs.py "$(mktemp -d)" | grep 'chrome-toolbar'
```

Record both `chrome-toolbar-*` agreements.
- If either is below its 0.30 floor, set that pair's `min_agreement` to its measured value rounded down to two places. Append to its `note`: " The user's 2026-10-19 direction: IntelliJ Classic's Search Everywhere icon in place of the mocks' field; the floor is the measured <value>."
- If both pass, add only the note's first sentence.
- No other pair's floor moves.

Run: `./gradlew :jasper-app:test --tests '*EnterpriseWorkstationMockTest' --tests '*ChromeRenderTest' && python3 -I tools/visual/check_pairs.py "$(mktemp -d)" > /dev/null; echo "exit $?"`
Expected: PASS and `exit 0`.

Then, since `EnterpriseLook.CHROME` and the shipped kit now name the icon for every Enterprise test, run the whole module once: `./gradlew :jasper-app:test`. Expected: PASS; fix any Enterprise expectation about the field as above (Execution notes X8). A timing-sensitive test (`StagedTransferLifecycleTest`, `StartupStepsApplicationTest`'s `@TempDir` clean-up) may flake once: re-run it alone before treating it as a failure.

- [ ] **Step 4: Commit**

```bash
git add -A jasper-app plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml tools/visual/pairs.json jasper-sdk-testkit docs/design/theme-engine/migration.csv
git commit -m "Plan 15b Task 4: Enterprise's toolbar shows IntelliJ Classic's Search Everywhere icon

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: A look stays data — the identity guard

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/architecture/LookIdentityTest.java`
- Modify: `docs/design/theme-engine/migration.csv`: the test builds `new ArrayList`, which `CANDIDATE` matches (fact 15), so it gets the row `jasper-app/src/test/java/dev/jasper/app/architecture/LookIdentityTest.java,app.architecture,LookIdentityTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/architecture/LookIdentityTest.java,New in Enterprise plan 15b Task 5; Test/fixture ownership; no production class names the Enterprise plugin` (Execution notes X10)

**Interfaces:**
- Produces: `static List<String> LookIdentityTest.mentions(List<byte[]> classes)`, which returns "internal name: string" for each class whose constant pool holds a UTF-8 entry containing `enterprise`, ignoring case.

- [ ] **Step 1: Write the test**

```java
/** Plan 15b (Rulings 10): production code never checks the Enterprise plugin's identity; a look is data the host reads. */
class LookIdentityTest {
    static final java.util.regex.Pattern IDENTITY = java.util.regex.Pattern.compile("(?i)enterprise");

    static List<String> mentions(List<byte[]> classes) { ... }   // ProjectionReadTest.Scanned.of(bytes).strings()

    static List<byte[]> pluginClasses() throws Exception {
        var classes = new ArrayList<byte[]>();
        try (var plugins = Files.list(Path.of(System.getProperty("jasper.stagedPlugins")))) {
            for (Path plugin : plugins.filter(Files::isDirectory).filter(p -> !p.getFileName().toString().equals("dev.jasper.enterprise")).toList())
                for (Path jar : jars(plugin))
                    for (byte[] bytes : StaticDefaultTest.classFiles(jar, 0))
                        if (ProjectionReadTest.Scanned.of(bytes).name().startsWith("dev/jasper/")) classes.add(bytes);
        }
        return classes;
    }

    @Test void noProductionClassNamesEnterprise() throws Exception {
        var app = StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.appJar")), 400);
        var ui = StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.uiJar")), 100);
        var plugins = pluginClasses();
        assertThat(plugins).as("the plugin scan is live").hasSizeGreaterThan(100);
        assertThat(mentions(app)).isEmpty();
        assertThat(mentions(ui)).isEmpty();
        assertThat(mentions(plugins)).isEmpty();
    }

    @Test void aMentionIsReported() {
        byte[] probe = ClassFile.of().build(ClassDesc.of("dev.jasper.app.workspace.LookProbe"), builder -> builder
            .withMethodBody("look", MethodTypeDesc.of(ConstantDescs.CD_String), ClassFile.ACC_STATIC,
                code -> code.ldc("dev.jasper.enterprise").areturn()));
        assertThat(mentions(List.of(probe))).containsExactly("dev/jasper/app/workspace/LookProbe: dev.jasper.enterprise");
    }
}
```

The `{ ... }` body is the obvious loop. `StaticDefaultTest.classFiles` and `ProjectionReadTest.Scanned` are package-private in `architecture`, this test's package, so they are used as they are. `ManagedVaultLoadingTest.jars(Path)` is package-private in `plugins` (fact 14), so copy its one line in as a private `static List<Path> jars(Path directory)`, with a comment naming the original. Imports: `java.lang.classfile.ClassFile`, `java.lang.constant.ClassDesc`, `ConstantDescs`, `MethodTypeDesc`, `java.nio.file.Files`, `Path`, `java.util.ArrayList`, `List`, JUnit's `Test` and AssertJ's `assertThat`. Add the migration row above.

Run: `./gradlew :jasper-app:test --tests '*LookIdentityTest' --tests '*ThemeMigrationCoverageTest'`
Expected: PASS at once: plan 15a removed the last references (fact 14), and Tasks 1 to 4 add none outside comments (Execution notes X11). If it fails, the report names the class. Fix that class (a look decision belongs in the kit), not the test.

- [ ] **Step 2: Commit**

```bash
git add jasper-app/src/test/java/dev/jasper/app/architecture/LookIdentityTest.java docs/design/theme-engine/migration.csv
git commit -m "Plan 15b Task 5: no production class names the Enterprise plugin

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: Documentation, STATUS and the whole build

**Files:**
- Modify:
  - `docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md` (header `:23`, 5.1 `:505`, 5.2 `:603`, 5.3 `:643`, 5.9 `:775`, 9.1 `:1161`, 10.3 `:1284`, 10.7 `:1338`, 16.1 `:1766`, 16.2 `:1859`; line numbers before this task's edits)
  - `docs/sdk-icons.md:21-24`
  - `docs/plugin-authoring.md:199-230` (15a moved it: the stripes sentence is `:200-201`, "registers no `<panel id>.toggle`." `:211`, the rail paragraph `:227-230`)
  - `docs/plugin-ui.md:64-65`
  - `docs/command-palette.md:43-44`
  - `docs/app-architecture.md:242-243`
  - `jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java:16-19`
  - `PanelSpec.java:8,13` (Task 1 edited `:16-18` of the same file)
  - `Panels.java:6` (Task 1 edited the `toggle` javadoc below it)
  - `Rail.java:5`
  - `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Presentation.java:17` (the `toolbarSearch` `@param`)
  - `docs/STATUS.md`
  - this plan's status banner

- [ ] **Step 1: Amend the spec** (smallest accurate edits, re-wrapped at 120 columns)

- **Header:** in plan 15a's "Amended on 2026-10-19 at the user's direction (plan 15): …" line, add 5.2, 5.3, 10.3, 10.7 and 16.2 to its list in order. Append to its last sentence: "; a panel's own toggle action activates its tool window, IntelliJ's classic icons draw the six classic names in tool-window headers and toolbar views, and Enterprise's toolbar shows IntelliJ Classic's Search Everywhere icon in place of the field (`[chrome] toolbar_search = "icon"`)".
- **5.1:** in the listing, after `icons = "classic"` add `toolbar_search = "icon"`.
- **5.2:**
  - "Twenty-four points: 10 chrome points" becomes "Twenty-five points: 11 chrome points".
  - In the chrome table, after `icons`, add: `` | `toolbar_search` | `field` | `icon` | IntelliJ Classic's Search Everywhere: an icon-only button with the classic magnifier, before Settings…, in place of the "Search everywhere" field (10.3) | ``.
  - The `icons` row's last cell gains ", and for the six classic names in tool-window headers and toolbar views (10.3, 10.7)".
- **5.3:** "It is the only cross-field constraint." becomes "`toolbar_search = "icon"` requires `palette = "run_window"` and `title_bar = "native"` or `"classic"`: the anchored card's home is the field, and the unified header holds it. These are the only cross-field constraints." (Execution notes X12: the old sentence would be false.)
- **5.9:** "- `menu_bar = "window"` with `title_bar = "unified"`;" gains a following bullet: "- `toolbar_search = "icon"` without `palette = "run_window"` and a native or classic title bar;".
- **9.1:**
  - the `WorkspaceLook` consumer row's fields gain "search (FIELD, ICON)";
  - the field table gains, after the `WorkspaceLook.icons` row, `` | `WorkspaceLook.search` | ICON when `toolbar_search = "icon"` | FIELD | ``;
  - in the `PluginLook` field row, "`search` FIELD" becomes "`search` ICON when `toolbar_search = "icon"`, else FIELD" (Execution notes X12: one row per field, and the row's old value would be false).
- **10.3:**
  - After "then, trailing, the title search field reading "Search everywhere" with the COMMAND_PALETTE keycap, and "Settings…"." add: "Under `toolbar_search = "icon"` (the user, 2026-10-19; Enterprise) the field is IntelliJ Classic's Search Everywhere icon: an icon-only 22 px button with the classic magnifier, tooltip "Search Everywhere" and the palette's shortcut, leading the trailing group before Settings… and doing what the field does; it stays icon-only under `icons_and_labels`."
  - After "The icons are the look's `[chrome] icons` set (5.2), IntelliJ's classic icons under Enterprise." add: "Tool-window headers and toolbar views draw the same classic art for those six names; every other name, and every panel body, keeps its catalog art."
- **10.7:** the Frames bullet's activation sentence ends "…dragging a divider keeps it, and a plugin's own toggle opens a frame without activating it." (spec `:1367-1368`; the spec has no `ACTIVATING_TOGGLE` sentence, Execution notes X13). Replace its last clause, from "and a plugin's own toggle opens", with: "and a plugin's toggle or `PanelHost.show()` activates the frame only when made while the panel's own declared toggle action runs (`PanelSpec.withToggleActionId`; `PanelRequest.Op.ACTIVATING_TOGGLE`, and `ACTIVATING_SHOW` for the show; the user, 2026-10-19; plan 15), so View › Tool Windows and the stripe-number shortcuts activate the bundled tool windows, as IntelliJ's ⌘1 does; any other plugin toggle opens a frame without activating it."
- **16.1:**
  - item 4: "across 9 of the ten chrome points" becomes "across 9 of the eleven chrome points", and "`icons` is held at `classic` in every row and covered by `ChromeArtworkTest`, `ZoneIconsTest`, `StripeIconTest` and `StripeIconsTest`)" becomes "`icons` is held at `classic` in every row and covered by `ChromeArtworkTest`, `ZoneIconsTest`, `StripeIconTest` and `StripeIconsTest`, and `toolbar_search` at `field` and covered by `ClassicHeaderTest`)" (Execution notes X14);
  - item 7: add the bullet "- `LookIdentityTest`: no production class of the app, jasper-ui or a bundled plugin other than Enterprise names "enterprise" (plan 15).".
- **16.2:** add "Enterprise's Search Everywhere icon (its tooltip, the Run window it opens, a narrow window); ⌘⌥1 to ⌘⌥3 activating the bundled tool windows; the classic `add` icon in Sessions' and Tunnels' headers;" after "the Run window's drag, attach to home, saved place, focus hand-back and modeless behaviour;".

Check:

```bash
spec=docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md
python3 -I - "$spec" <<'PY'
import re, sys
text = re.sub(r"\s+", " ", open(sys.argv[1], encoding="utf-8").read())
for phrase in ("Twenty-four points", "It is the only cross-field constraint", "a plugin's own toggle opens a frame without activating",
               "`search` FIELD |", "9 of the ten chrome points"):
    print(text.count(phrase), "(want 0)", phrase)
for phrase in ("Twenty-five points: 11 chrome points", "| `toolbar_search` | `field` | `icon` |", "`toolbar_search = \"icon\"` requires",
               "These are the only cross-field constraints", "| `WorkspaceLook.search` | ICON when", "IntelliJ Classic's Search Everywhere icon",
               "`LookIdentityTest`", "a panel's own toggle action activates its tool window",
               "`ACTIVATING_SHOW` for the show; the user, 2026-10-19; plan 15)", "any other plugin toggle opens a frame without activating it",
               "9 of the eleven chrome points", "`toolbar_search` at `field` and covered by `ClassicHeaderTest`"):
    print(text.count(phrase), "(want at least 1)", phrase)
PY
```

Expected: five `0` lines, and every other count at least `1`.

- [ ] **Step 2: The guides and the SDK javadoc**

- `docs/sdk-icons.md`: after the stripe paragraph (`:21-24`), add: "Under the Enterprise look IntelliJ's classic icons also draw `"add"`, `"split"`, `"zoom"`, `"settings"`, `"window.add"` and `"search"` in the toolbar (actions and toolbar views), the status bar and tool-window headers (your `PanelHeader` actions and your panel's header controls); in a panel's or window's body these names keep their ordinary art."
- `docs/plugin-authoring.md`:
  - `:201`'s "stripes of icons on the window's left and right edges" becomes "stripes on the window's left and right edges: icons, or under a classic look such as Enterprise labelled buttons that start with the icon and read the panel's title".
  - After "and Jasper registers no `<panel id>.toggle`." (`:211`) insert: "Under a look with flush tool windows (Enterprise), when your action shows the panel (through `Panels.toggle` or `PanelHost.show()`) it also makes it the active tool window, as a stripe click does; a toggle or show from anything else does not."
  - `:227-229`: "A rail button, on the left stripe after the panel icons, runs one of your actions. A plugin window is a frame Jasper builds: icon, macOS title bar, menu bar, theme tracking and remembered bounds are its job; the content is yours." becomes "A rail button, on the left stripe after the panel icons (a labelled entry under a classic look), runs one of your actions. A plugin window is a frame Jasper builds: icon, title bar, menus (in the window, or under the Enterprise look in the screen menu bar on macOS and none on Windows and Linux), theme tracking and remembered bounds are its job; the content is yours."
- `docs/command-palette.md:44`: "the toolbar's Search everywhere field open it" becomes "the toolbar's Search Everywhere icon open it".
- `docs/app-architecture.md:242`: "a toolbar row of left-packed buttons with the search field and Settings… trailing" becomes "a toolbar row of left-packed buttons with the search field, or under `toolbar_search = "icon"` a Search Everywhere button, and Settings… trailing".
- `docs/plugin-ui.md:64-65`: after "a panel's stripe label starts with its icon" and the plugin-icons link that follows it, add ", and the six classic icon names draw IntelliJ's classic art in headers and toolbar views".
- `StandardMenu.java:16-19`: "after Tab, Session and Transfer and before plugins' own menus" becomes "after Tab, Session and Transfer and before plugins' own menus, or after plugins' menus in a classic menu bar".
- `PanelSpec.java:8`: "A panel as the stripes show it: an icon on a tool window stripe and, while it shows, a tool window with a header." becomes "A panel as the stripes show it: an icon on a tool window stripe (under a classic look a labelled button starting with the icon) and, while it shows, a tool window with a header." `:13`'s `@param title` gains "and, under a classic look, the stripe button's label".
- `Panels.java:6`: "Each registered panel gets a stripe icon in every window." becomes "Each registered panel gets a stripe button in every window."
- `Rail.java:5`: "Plain action buttons on the left stripe, after the panel icons" becomes "Plain action buttons on the left stripe, after the panels' buttons".
- `Presentation.java`'s `toolbarSearch` `@param`: "how the toolbar offers Search Everywhere: the field, or IntelliJ Classic's icon button".

Run: `./gradlew :jasper-app:test --tests '*AppDocumentationTest' && ./gradlew :jasper-sdk:javadoc`
Expected: PASS.

- [ ] **Step 3: Prove the default look unchanged**

Run plan 15a Task 9 Step 3's block (`2026-10-19-enterprise-15a-look-query.md:1154-1170`), with one change, as plan 15a ran it: `out` is the fixed folder `/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan15b-check`, emptied first (`rm -rf "$out"; mkdir -p "$out"`), in place of `$(mktemp -d)`, so Step 5 writes beside it (Execution notes X15). It compares against `plan15-baseline`, which exists (11 files under `app`, 161 under each gallery, and `pairs.txt`).
Expected: `dark: identical (161 files)`, `light: identical (161 files)` and `jasper-app default look: identical (11 files)`, with no `diff` output.

- [ ] **Step 4: Run the whole build and count**

Run: `./gradlew check --continue`, then plan 15a Task 9 Step 4's counting script.
Expected: BUILD SUCCESSFUL, 0 failures and 0 errors. Record the counts against plan 15a's.

- [ ] **Step 5: Render the Enterprise gallery and check the pairs**

```bash
base=/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan15-baseline
out=/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan15b-check
mkdir -p "$out"
for theme in light dark; do ./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=$theme -Pjasper.galleryScale=2; done
python3 -I tools/visual/check_pairs.py "$(mktemp -d)" > "$out/pairs.txt"; echo "exit $?"
diff <(sed 's/ agreement=.*//' "$base/pairs.txt") <(sed 's/ agreement=.*//' "$out/pairs.txt") && echo "same verdicts"
diff "$base/pairs.txt" "$out/pairs.txt"
```

Expected:
- `exit 0` and `same verdicts` (101 of 101 PASS);
- the last `diff` shows only:
  - the two `chrome-toolbar-*` lines;
  - any pair whose crop holds a tool-window header's classic name, or a toolbar view's (Task 2);
  - the two lines plan 15a already recorded against this baseline: `enterprise-settings-13` (15a's kit default, 0.723 then 0.722) and `composition-light-vault-manager`, whose status bar prints a fresh JUnit temporary path on every run (Execution notes X16).

Record each changed line. Any other changed line: find its cause before going on.

- [ ] **Step 6: Write `docs/STATUS.md`'s new top entry and this plan's banner**

The entry goes above plan 15a's and starts as below. The template stays fenced, as in plan 15a.

```markdown
**Enterprise presentation, plan 15b: look polish at the user's direction — 2026-10-20** (`claude/enterprise-look-query`, worktree `.claude/worktrees/enterprise-15`, after plan 15a; [plan](superpowers/plans/2026-10-19-enterprise-15b-look-polish.md), [spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md))
```

Then give, in the series' style:
- the search-icon request quoted, and plan 15's approval;
- what they became:
  - activation by a panel's own toggle action, answering plan 12's question;
  - the six classic names in tool-window headers and toolbar views;
  - `toolbar_search` with its constraint;
  - the icon-only Search Everywhere button;
  - `LookIdentityTest`;
- the rulings (this plan's 13, one clause each);
- the deviations from the Enterprise mocks, as the user's 2026-10-19 direction:
  - the toolbar's field replaced by the icon: the `EnterpriseWorkstationMockTest` expectations and the two `chrome-toolbar-*` pairs, with their measured agreements and any floor moved;
  - each header-icon probe Task 2 changed;
- the check counts, the pairs, the byte-identical gallery and the 11 identical default-look renders;
- what is left for later: the splash's fixed colours and "Workstation…" step text for other branded looks; the THD plugin's port to SDK 0.18;
- **GUI checks for the user**, in this order:
  1. Under Enterprise Light and Dark, the toolbar's magnifier before the wrench, against IntelliJ Classic's:
     - its hover;
     - the tooltip "Search Everywhere (⇧⌘P)";
     - a click opens the Run window;
     - ⇧⌘P still does;
     - Icons & Labels keeps it icon-only;
     - a narrow window keeps it beside the wrench.
  2. View › Tool Windows › Sessions, SFTP, Tunnels, Transfers and File Servers, and ⌘⌥1 to ⌘⌥3: each opens its tool window with the focused header tint, and typing goes there. For Sessions, SFTP, Tunnels and File Servers a second press hides it and hands the keyboard back to the terminal. Transfers' action only shows: a second View › Transfers or ⌘⌥3 keeps it open and activates it again.
  3. Sessions' and Tunnels' header `+` and any header gear in IntelliJ's classic art, next to the toolbar's.
  4. Remote's session view's Run button unchanged.
  5. The default look: the title field, the rails, the headers and the shortcuts as before (a toggle shortcut opens without the tint).
- the merge state: "Not merged, not pushed."

Then this plan's status banner (Execution notes X17: the execution date): "> **Status (2026-10-20): executed on `claude/enterprise-look-query` (`<first>..<last>`, then the Task 6 documentation commit).** Deviations from this plan's text: …".

Run: `./gradlew :jasper-app:test --tests '*AppDocumentationTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add docs jasper-sdk/src/main/java/dev/jasper/sdk/ui
git commit -m "Docs: Enterprise plan 15b, look polish, at the user's direction

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Execution notes (2026-10-20)

Refreshed before execution, at the branch tip `e858a4ecb` (plan 15a complete; `main` still `f78bb0385`). Every anchor, quoted text, path, signature and line number above was re-found in the current code and corrected where it had moved; the facts section now says what was found. Plan 15a's "Plan 15a is assumed merged" holds on this branch: SDK 0.18.2's `Presentation` (with `ToolbarSearch`), `PluginLook.Search`, `HostedPresentation`, `FakePresentations` and `plan15-baseline` are all in place. The rulings below are the refresh's, made on the user's behalf under their overnight instruction ("make decisions on my behalf"); each reads "what — why — cost if wrong".

### Pre-flight scan

One row per pair of tasks that share a file or an interface, then one row per task.

| Scope | Shared file or interface | Finding | Verdict |
|---|---|---|---|
| Task 1 × Task 6 | `PanelSpec.java`, `Panels.java`; spec 10.7 | Task 1 edits `toggle`'s javadoc and `@param toggleActionId`; Task 6 edits `Panels.java:6`, `PanelSpec.java:8,13` and the spec's activation clause. Disjoint lines, but Task 1 shifts the files. | Consistent; Task 6 re-finds by quoted text (X19) |
| Task 1 × Task 2 | `ToolWindowArea` | Task 1's window tests build areas through `DesktopTestSupport`, which uses the constructors Task 2 keeps as `NEW_UI`. | No conflict |
| Task 2 × Task 3 | `ChromeRenderTest`, `ProjectionReadTest` | Task 2 runs both; Task 3 edits both. Either order compiles. | No conflict |
| Task 2 × Task 4 | `ClassicHeaderTest.java`, `WindowChrome.java`, the Enterprise mock probes, the STATUS deviation list | Task 2 adds a toolbar-view test; Task 4 adds `Search` overloads and generalises `assertPaintsClassicAdd`. Task 2 edits `WindowChrome:329`; Task 4 adds an accessor. Both may move Enterprise mock expectations. | Needed a ruling (X5) |
| Task 2 × Task 6 | `docs/sdk-icons.md`, spec 5.2 and 10.3, the pairs diff | The docs say a panel's body keeps its art; that holds only with X3's document-bar exclusion. Step 5's expected diff names Task 2's header icons. | Consistent with X3 |
| Task 3 × Task 4 | `ClassicHeader(boolean)`, `EnterpriseLook`, `ChromeSpec.ToolbarSearch`, `HostedPresentationTest`, `ProjectionsTest` | Task 3 stores the flag and keeps `CHROME` at `FIELD`; Task 4 implements it and moves `CHROME` and the kit to `ICON`, which flips `ProjectionsTest`'s Enterprise plugin looks too (the plan had missed them). | Needed rulings (X7, X8) |
| Task 3 × Task 6 | spec 5.2, 5.3, 5.9, 9.1; the parser's message | The constraint's message matches 5.9's new bullet. 5.3 says the `menu_bar` rule is "the only cross-field constraint"; 9.1's `PluginLook` row says "`search` FIELD". | Needed a ruling (X12) |
| Task 4 × Task 6 | STATUS deviations, `pairs.json`, `docs/command-palette.md`, spec 10.3, `FakePresentations` | Task 4 records measured agreements; Task 6 Step 5 diffs against `plan15-baseline`, which already differs from 15a's tip in two lines. | Needed a ruling (X16) |
| Task 4 × Task 5 | `migration.csv` | Task 4's `git add` names it; only Task 5 adds a row. | No conflict |
| Task 5 × Tasks 1-4 | every production class those tasks touch | `LookIdentityTest` fails on any non-comment "enterprise" in app, jasper-ui or non-Enterprise plugin classes. | Needed a ruling (X11) |
| Task 5 × Task 6 | spec 16.1 item 7 | Task 6 adds the `LookIdentityTest` bullet. | Consistent |
| Task 1 alone | `WindowContributionsTest`; `PanelEntry.toggleActionId()`; `aShowInsideThePanelsOwnToggleActionActivates` | `WindowContributionsTest` has no flush fixture and no plan-12 activation test. `toggleActionId()` is the host-made `<id>.toggle` for an undeclared panel. The show test registered the panel before its toggle action, which `register` refuses. | Fixed in text (X1, X2, X21) |
| Task 2 alone | `button.panel`; `classicChromeNames()`; the jasper-ui fixture; host actions with artwork; the `KitView` toolbar mount | `panel` is also set on document bars in panel bodies. Reading the holder's keys decodes the classic SVGs. The fixture is `KitFixtures`, not a "`KitContext` fixture". `setPanelHostActions` takes plain buttons; artwork comes through `setPanelHostHeader`. `enterprisesDefaultToolbarIsClassicIconSquares` mounts a `JPanel`, not a `KitView`. No migration row is due. | Fixed in text (X3, X4, X20) |
| Task 3 alone | `everyChromeAndControlPointTakesBothItsValues`; `ClassicHeader`'s constructor; `ChromeSpec`'s javadoc; `WorkspaceChrome.STANDARD` | The test has no chrome loop to extend. `ClassicHeader` declares no constructor, so a declared one would remove the no-argument one. The javadoc counts ten points. `STANDARD` calls the private constructor. | Fixed in text (X6, X7) |
| Task 4 alone | `EnterpriseMenuRowMockTest.Mounted`; the Settings… crop; the gallery; the whole module | The field's bounds are read before the window closes, so the button's must be too. The Settings… crop's left margin may now take the magnifier. The worktree has no Enterprise gallery for `check_pairs.py`. `CHROME` moving to `ICON` reaches every Enterprise test. `FakePresentations`' javadoc already says "Enterprise's on Linux". | Fixed in text (X8, X9, X18, X22) |
| Task 5 alone | the helpers; the `{ ... }` count; `CANDIDATE` | `ManagedVaultLoadingTest.jars` is in `plugins`, not visible; `classFiles` and `Scanned` are. The text said "two" bodies where there is one. `new ArrayList` matches `CANDIDATE`. | Fixed in text (X10) |
| Task 6 alone | spec 5.3, 9.1, 10.7, 16.1 item 4; the check script; `plugin-authoring.md` lines; Step 3's and Step 5's folders; dates | 5.3 and 9.1 as above. 10.7's quoted `ACTIVATING_TOGGLE` sentence does not exist, and its real clause ("a plugin's own toggle opens a frame without activating it") would become false. Item 4's insertion point split a parenthesis. 15a moved the guide's lines by 5. 15a ran Steps 3 and 5 in a fixed folder. The plan executes on 2026-10-20. | Fixed in text (X12 to X17) |

### Rulings

- **X1. Task 1's window-level tests go in `WindowPanelsTest`, beside plan 12's.** What: the five `WindowContributionsTest` cases become `WindowPanelsTest` cases with its flush fixture (`DockChromeTest.classicDock()`), `focusedSlot()` and `focusMoved(null)`; the Review Focus names follow. Why: that file holds plan 12's activation tests and the only flush-framed window fixture; `WindowContributionsTest` has neither, and the plan says to copy plan 12's way. Cost if wrong: none; the tests are found by name.
- **X2. An activating toggle also needs `PanelEntry.namedToggle()`.** What: `ACTIVATING_TOGGLE` when the panel declared its toggle and `running` equals it; the show path tests `spec.toggleActionId()` is present. Why: `toggleActionId()` is the host-made `<id>.toggle` for an undeclared panel, and Rulings 1 is about the panel's own declared action. Cost if wrong: none; the case is unreachable for a well-formed plugin.
- **X3. "In the panel header" is `panel && !documentBar`, and every check reads the flag first.** What: the classic art applies to toolbar strips, header controls and host actions; a document bar's buttons and menus in a panel body keep the catalog, with a new `KitClassicChromeIconsTest` case. Why: `ViewReconciler.java:305,307` sets `panel` on document bars (the Vault's + New), and Rulings 3 keeps bodies on the catalog; reading the flag first keeps the default look from asking. Cost if wrong: a header control also marked `documentBar` would keep its catalog art; none exists.
- **X4. `classicChromeNames()` comes from a `Set.of` constant outside `ClassicHolder`.** What: `BuiltInArtwork.CLASSIC_NAMES`, held equal to `classicChrome().keySet()` by `BuiltInArtworkTest.theClassicNamesAreTheClassicHoldersKeys`. Why: touching the holder decodes its SVGs (`pair`), and its contract is that the default look never decodes them; the default look must stay byte-identical and its start-up unchanged. Cost if wrong: two lists to keep in step, held by one test.
- **X5. `ClassicHeaderTest` is shared by Tasks 2 and 4 without conflict.** What: Task 2's new test uses today's `window(themes, view, icons)`; Task 4 adds `Search` overloads and turns `assertPaintsClassicAdd` into a delegate of `assertPaintsClassic(button, name)`. `WindowChrome` edits are disjoint (Task 2 `:329`, Task 4 a new accessor). Each task lists the Enterprise mock expectations it moves, and Task 6's STATUS entry takes both lists. Why: either order of review keeps both tasks green. Cost if wrong: none.
- **X6. `everyChromeAndControlPointTakesBothItsValues` is unchanged.** What: the bullet asking to give it a classic base is dropped. Why: it loops over control points only; the new `theToolbarSearchPointDefaultsToTheFieldAndTakesTheIcon` gives both values. Cost if wrong: none.
- **X7. Task 3 declares both `ClassicHeader` constructors.** What: `ClassicHeader() { this(false); }` and `ClassicHeader(boolean iconSearch)`; `WorkspaceChrome.STANDARD` passes `false`; `ChromeSpec`'s javadoc says eleven points. Why: the class has only the implicit constructor today, which a declared one removes (`WindowPairsTest:93` uses it). Cost if wrong: none.
- **X8. Task 4 flips `ProjectionsTest`'s Enterprise plugin looks and runs the whole app module once.** What: `thePluginLookIsTheLookOnEachPlatform`'s three `EnterpriseLook.CHROME` expectations take `Search.ICON`; Step 3 ends with `./gradlew :jasper-app:test`. Why: those read `CHROME`, which Task 4 moves; the plan's run lists missed them, and any other Enterprise test that reads the field shows up only in a full run. Cost if wrong: one module run's time.
- **X9. `EnterpriseMenuRowMockTest.Mounted` gains the search button's bounds.** What: a `Rectangle search` component read beside `fieldAt` through `owner.chrome().composition()`. Why: the window closes before the assertions run (its Execution notes R35), so the bounds must be captured like the field's. Cost if wrong: one more record component and one constructor call.
- **X10. Migration rows are for new candidate files only; `LookIdentityTest` gets one.** What: the row is given in Task 5; jasper-ui's `KitClassicChromeIconsTest` needs none; edited files keep their rows. Why: `ThemeMigrationCoverageTest` scans app, SDK, testkit and plugin sources only and checks presence, and `new ArrayList` matches `CANDIDATE`. Cost if wrong: a coverage failure in Task 5, which its run catches.
- **X11. Tasks 1 to 4 add no identifier or string containing "enterprise" to production code.** What: comments may name Enterprise; fields, locals, parameters, strings and class names may not. Why: `LookIdentityTest` reads constant pools, and Gradle compiles with debug information, so local variable names count. Cost if wrong: Task 5 fails and names the class; rename it there.
- **X12. Spec 5.3 and 9.1 are corrected, not just extended.** What: 5.3's "It is the only cross-field constraint." becomes the new constraint plus "These are the only cross-field constraints."; 9.1 gains a `WorkspaceLook.search` row, and the `PluginLook` row's "`search` FIELD" becomes "ICON when `toolbar_search = "icon"`, else FIELD". The check script asserts both. Why: appending would leave a false sentence and two rows for one field. Cost if wrong: none.
- **X13. Spec 10.7's edit replaces the clause that would become false.** What: "and a plugin's own toggle opens a frame without activating it" becomes the activating rule, ending "any other plugin toggle opens a frame without activating it". Why: the quoted `ACTIVATING_TOGGLE` sentence does not exist in the spec, and the real clause contradicts plan 15b. Cost if wrong: none.
- **X14. Spec 16.1 item 4 keeps its parenthesis whole.** What: `toolbar_search` is held at `field` and "covered by `ClassicHeaderTest`" after the `icons` clause's test list. Why: inserting after "in every row" split "held at `classic` … and covered by …". Cost if wrong: none.
- **X15. Task 6 Steps 3 and 5 share the fixed folder `scratchpad/plan15b-check`.** What: in place of `mktemp -d`, emptied at Step 3. Why: each command runs in a fresh shell, and plan 15a ran the same way, so Step 5's `pairs.txt` sits beside Step 3's renders for review. Cost if wrong: none.
- **X16. Step 5's expected diff includes plan 15a's two drifting lines.** What: `enterprise-settings-13` (0.723, 0.722 at 15a's tip) and `composition-light-vault-manager` (a fresh JUnit path every run) may differ from `plan15-baseline/pairs.txt` without a cause to find. Why: the baseline is `f78bb0385`'s, and 15a recorded both. Cost if wrong: a real change on those two lines could pass unexamined; their crops hold no header, toolbar or search art.
- **X17. Plan 15b's STATUS heading and banner carry 2026-10-20.** What: the day it executes; the user's direction stays dated 2026-10-19 everywhere. Why: the series dates an entry by its execution, and the spec's plan-15 header line (Rulings 13) keeps its date. Cost if wrong: one date to change.
- **X18. Task 4 Step 3 renders the Enterprise gallery before `check_pairs.py`, and only its `chrome-toolbar-*` lines count there.** What: the two `:jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryScale=2` runs come first; Task 6 Step 5 gives the full 101-pair verdict after `check`. Why: the worktree's gallery holds only the 1x default renders now, and the other pairs read whatever `build/` holds. Cost if wrong: a pair broken by Task 2 is caught in Task 6, not Task 4.
- **X19. Anchors in a file another task edited are found by quoted text.** What: Task 6's `Panels.java:6`, `PanelSpec.java:8,13` and the spec's sections, and every "line before this task's edits", are located by the quoted text. Why: Tasks 1 to 5 shift those lines. Cost if wrong: none.
- **X20. Task 2's fixtures are named as the code has them.** What: `KitFixtures.context()`/`render` with `Edt.run` (as `KitViewToolbarStripTest`); a host action with artwork through `setPanelHostHeader` (as `ToolWindowArea.hostHeader` and `PanelHostHeaderTest`); a real `KitView` toolbar view mounted as `WindowToolbarViewsTest.aKitViewIsPresentedAsAToolbarStrip` does. Why: the old text named a fixture and a mount that do not exist or carry no artwork. Cost if wrong: none.
- **X21. `aShowInsideThePanelsOwnToggleActionActivates` registers its actions before the panel.** What: the handlers call `hosts[0].show()` on a holder the factory fills. Why: `register` refuses a toggle action that is not registered yet (`requireOwn`). Cost if wrong: none.
- **X22. The Settings… crop may start at the search button's right edge.** What: only if the magnifier's ink now falls in its 44-mock-px left margin, with the 2026-10-19 note. Why: the button sits 6 px before Settings…, inside that margin. Cost if wrong: the crop checks a little less of the gap before Settings….
- **X23. No SDK, testkit or plugin version moves.** What: the SDK javadoc gains activation sentences marked "since 0.18.2", and `FakePresentations.CLASSIC` changes value only. Why: the API is unchanged and 0.18.2 is the version that ships with this host (Global Constraints, Rulings 2 and 9). Cost if wrong: a plugin cannot tell activation or the icon from the SDK version alone; it reads `Presentation.toolbarSearch`, and activation needs nothing from it.

Orchestrator constraints that apply to every task: work only in this worktree; `./gradlew`, never `--stop`; no GUI, no AOT training (headless renders and the gallery are allowed); no push, merge or rebase; no bare `git stash`; no subagents; source hygiene by AGENTS.md's Python check; doclint-safe javadoc; the commit trailer as in Global Constraints. A timing-sensitive test (`StagedTransferLifecycleTest`, `StartupStepsApplicationTest`'s `@TempDir` clean-up) may flake once: re-run it alone before treating it as a failure.
