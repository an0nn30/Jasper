# Enterprise presentation 15b: look polish Implementation Plan

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
- Plan 15a (`2026-10-19-enterprise-15a-look-query.md`) is assumed merged. Its `PluginLook`, `HostedPresentation`, `FakePresentations` and SDK 0.18.2 are in place.
- Audit: `kit.md`, `surface.md`, `plugins.md` and `critic.md` under `/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/look-audit/` (`critic.md` items 3, 6 and 8).

**Workspace:** the plan-15 worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-15`, branch `claude/enterprise-look-query`, at plan 15a's last commit. If the user asks for a separate branch, branch it from the commit that holds 15a. Run every command from the worktree root. Line numbers below are from `f78bb0385`, before 15a. 15a touched none of these files except `Projections.java`, `HostedUi.java` and the testkit's `FakePresentations.java`, so re-find those anchors; never edit around a missing anchor.

## Before you start (refresh)

Do not rebase, merge or push. Never run bare `git stash`, and never run `./gradlew --stop`.

```bash
grep -c 'public record Presentation(' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Presentation.java                   # 1: plan 15a is in
grep -c 'record PluginLook(' jasper-app/src/main/java/dev/jasper/app/presentation/PluginLook.java                    # 1
grep -c 'ACTIVATING_TOGGLE' jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java                            # 0: Task 1 adds it
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
- `docs/design/theme-engine/migration.csv`: a new or edited `.java` file that `ThemeMigrationCoverageTest.CANDIDATE` matches gets a row in the same commit, its disposition beginning `New in Enterprise plan 15b Task N;`. Run `./gradlew :jasper-app:test --tests '*ThemeMigrationCoverageTest'` in each task that touches Java files.
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
   - Tests: Task 1 `HostedUiTest.aPanelsOwnToggleActionActivatesAndAnythingElseToggles`, `HostedUiTest.aShowInsideThePanelsOwnToggleActionActivates`, `WindowContributionsTest.anActivatingToggleThatHidesThePanelActivatesNothing` and `anActivatingShowUnderFlushFramesActivatesThePanel`.
2. **A narrow Enterprise window.** The search button stays in the trailing group beside Settings… while the leading toolbar gives way; it is never hidden by the field's collapse rule and never overlaps Settings….
   - Test: Task 4 `ClassicHeaderTest.underTheSearchIconANarrowWindowKeepsTheButtonBesideSettings`.
3. **`window.toolbar = "icons_and_labels"` and `hidden` under the search icon.** Under labels the button stays icon-only, as IntelliJ's. Under `hidden` it goes with the row, and ⇧⌘P still opens the Run window.
   - Tests: Task 4 `theSearchButtonStaysIconOnlyInEveryToolbarMode` and `underAHiddenToolbarTheShortcutStillOpensTheRunWindow`.
4. **A header action or toolbar-view button named outside the classic set** (`folder`, `more`, `collapse`, a plugin's own path), and the same six names in a panel's body. These keep exactly today's art under Enterprise.
   - Tests: Task 2 `KitClassicChromeIconsTest.onlyTheSixNamesInHeadersAndToolbarStripsChange` and `ToolWindowHeaderTest.otherNamesKeepTheirGlyphUnderClassicIcons`.
5. **A third-party kit that names `toolbar_search = "icon"` with the anchored palette or a unified title bar.** It is refused with one message naming the key, and the default look applies.
   - Test: Task 3 `KitParserTest.everyInvalidKitGivesOneMessageNamingTheKey` (the new cases).

## Verified facts

At `f78bb0385` unless stated. Paths are under `jasper-app/src/main/java/dev/jasper/app/` unless they start with a module.

**Activation**
1. `Contributions.PanelRequest.Op` has `SHOW, HIDE, TOGGLE, ACTIVATING_TOGGLE` (`contributions/Contributions.java:55-60`). Only the host-made `<id>.toggle` (`:158-160`) and Event Log (`application/NotificationsFeature.java:63-64`) send `ACTIVATING_TOGGLE`. `HostedUi`'s `PanelHost.show()` calls `PanelSite.show()` (`plugins/HostedUi.java:448`), whose runnable is `WindowContributions.showPanel(id)`, a plain `dock.show` (`workspace/WindowContributions.java:327`, `:210`).
2. `HostedUi`'s action handler runs `handler.accept(new Context(...))` inside `containment.run(pluginId, "action " + spec.id(), …)` (`plugins/HostedUi.java:200-203`). `Panels.toggle` sends `Op.TOGGLE` (`:426-432`). `HostedUi` runs on the UI thread, and registration needs it (`guard`).
3. `WindowContributions.requested` maps `ACTIVATING_TOGGLE` to `toggleAndActivate` (`workspace/WindowContributions.java:183-191`). That method activates only when `dock.visible(id)` after the toggle, and sets `activating` only under flush frames (`:196-201`). `ToolWindowArea.activate` returns at once when not flush (`workspace/ToolWindowArea.java:491-492`).
4. The bundled toggle actions that call `Panels.toggle`: `RemotePlugin.java:684`, `SftpUi.java:121`, `TunnelsUi.java:118`, `TransfersPanels.java:40` and `FileServerPlugin.java:158` (plan 12 STATUS, `docs/STATUS.md:224-227`). Transfers' is a show, not a toggle: `TransferUi.SHOW` (⌘⌥3, `TransferUi.java:43-45`) runs `TransfersPanels.show` (`RemotePlugin.java:181`), which calls `Panels.toggle` only while the window has no `PanelHost` yet and `host.show()` after that (`TransfersPanels.java:38-41`), so a second press keeps the panel open. `HostedUiTest.panelsAndRailReachTheModelWithContainedFactories` asserts a plain toggle's request (`jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java:396-399`).

**Icons**
5. The classic set is `BuiltInArtwork.ClassicHolder.CLASSIC`'s six names (`jasper-ui/src/main/java/dev/jasper/ui/icons/BuiltInArtwork.java:41-45`). `IconRegistry.chromeIcon(name, size, CLASSIC)` returns them, else the New UI chrome original, else the catalog (`IconRegistry.java:94-101,117-125`). So a full `CLASSIC` receiver would change the other names' art too.
6. Receivers today:
   - `AppIcons.chromeReceiver` marks the window header (`workspace/WindowChrome.java:74`) and the status bar (`workspace/WorkspaceChrome.java:122`).
   - `NativePreparation.paint` draws a built-in `PresentationIcon` by the nearest receiver's artwork, and outside every receiver by its ordinary artwork (`platform/AppIcons.java:251-268`).
   - The tool-window area is in no receiver.
7. `ToolWindowHeader` shows each contributed action's `action.icon()`, under flush frames through `stripGlyph` (`workspace/ToolWindowHeader.java:219-235`, `:155-158`). Its constructors are at `:84` and `:95`. `ToolWindowArea` makes it at `:666`, and its constructors are at `:297` and `:307`. `WindowContent` makes the area at `:283` and knows `look().chromeArtwork()` (`:280`).
8. Kit-drawn icons:
   - A borrowed kit header's host actions carry `PanelHostHeader.Artwork(name, pathData)`; a null path means canonical art (`jasper-ui/src/main/java/dev/jasper/ui/swing/PanelHostHeader.java:44-60`). `Buttons.HostArtwork.icon` paints them with `icons.panel(name, …)` (`Buttons.java:35-41`).
   - The panel's own header controls have `button.panel = true` (`PanelHeaders.java:254,260,306`).
   - A button's named icon is chosen in `ButtonControl.refresh` (`Buttons.java:84-92`). `KitView.toolbarStrip(boolean)` (`KitView.java:411-418`) marks a toolbar view, and `WindowChrome` sets it when it mounts one (`workspace/WindowChrome.java:328-331`).
   - `IconRegistry.PreparedIcon.icon(GeneratedTheme, SemanticColor)` exists (`IconRegistry.java:147`).
9. `ClassicHeaderTest.assertPaintsClassicAdd` compares a button's centre with `new IconRegistry().chromeIcon("add", size, CLASSIC).icon(theme, TEXT_PRIMARY)` pixel for pixel (`jasper-app/src/test/java/dev/jasper/app/workspace/ClassicHeaderTest.java:289-311`). It is the model for every icon test here.

**The search field**
10. The classic header:
    - `ClassicHeader.build` fills the leading toolbar and puts "Settings…" (`ActionId.OPEN_SETTINGS`, icon `settings`, priority 100) in its trailing `WindowToolbar` (`workspace/ClassicHeader.java:39-56`).
    - `layout` places the trailing group at the right, the field before it, and the toolbar before the field; the field collapses below `searchMinimum` (`:64-88`).
    - The field's text is the mocks' "Search everywhere" (`:99`).
    - `WindowChrome.classicButton(into, id, label, icon, priority)` makes a `ClassicToolbarButton` (`workspace/WindowChrome.java:136-141`).
    - `AppIcons.icon("search")` is the `SEARCH` meaning (`platform/AppIcons.java:25`), which a `CLASSIC` receiver draws as IntelliJ's classic find icon (fact 5).
    - The field runs `owner.action(ActionId.COMMAND_PALETTE)` (`WindowChrome.java:70-72`).
11. `WindowToolbar.applyLabels` sets a button's text to its `label` property, or null, unless `keepLabel` is `FALSE` (`workspace/WindowToolbar.java:199-210`). A `ClassicToolbarButton` whose text is null is a 22 px icon square (`ClassicToolbarButton.java:52-53`). Its tooltip is its label (`:40`). `TitleSearchField` computes its tooltip on demand with `composition.searchTooltip(…, shortcutText())` (`TitleSearchField.java:93`). `WindowContent.paletteShortcut()` is at `:808`.
12. The model:
    - `ChromeSpec` is a ten-component record (`presentation/ChromeSpec.java:21-22`), and its constraint is at `:63-64`. `new ChromeSpec(` appears in `ChromeSpec.java` (2), `KitParser.java:309`, `KitParserTest`, `ProjectionsTest` (2), `testsupport/EnterpriseLook.java` (2) and `ChromeRenderTest.java:72`.
    - `KitParser.CHROME` lists the keys (`presentation/KitParser.java:51-52`), and the parser's constraint is at `:307-308`.
    - `WorkspaceLook` has eight components (`presentation/WorkspaceLook.java:18-19`) and about 12 test constructions.
    - `chrome-covering-array.tsv` has nine columns and holds `icons` at `classic` (`ChromeRenderTest.java:64-77`).
    - `PresentationConformanceTest.KEYS` lists `chrome/...` (`jasper-app/src/test/java/dev/jasper/app/plugins/PresentationConformanceTest.java:30-31`).
13. The mocks:
    - `EnterpriseWorkstationMockTest` asserts the field (x 2116-2715 and y 122-165 in the mock; `jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseWorkstationMockTest.java:123-131`).
    - `tools/visual/pairs.json`'s `chrome-toolbar-light` and `chrome-toolbar-dark` (`:893-940`) crop the toolbar row, field included, at a 0.30 floor (measured 0.581 and 0.577 at plan 14).

**Identity**
14. No production class in jasper-app or jasper-ui names Enterprise outside comments (critic.md "OK"). The architecture tests read the app and ui jars through `System.getProperty("jasper.appJar")` and `"jasper.uiJar"` with `StaticDefaultTest.classFiles(Path, int)` (`architecture/VariantReadTest.java:37-41`, `StaticDefaultTest.java:160-166`). The staged plugins are under `System.getProperty("jasper.stagedPlugins")/<id>/`, one folder per plugin, each holding its own jar and its bundled libraries (`jasper-app/build.gradle.kts:91`).

## Rulings

Each ruling is what was decided, why, and the cost if it is wrong.

1. **Activation.** A `Panels.toggle`, or a `PanelHost.show()`, made synchronously inside the handler of the panel's own declared toggle action counts as activating: the same plugin, while that action's handler is running, for any window it names. A deferred call, another action's, or one with no running action stays plain. This is IntelliJ's ⌘1, and it answers the plan-12 STATUS question (the user: yes). The show path is host-only, so Transfers' action activates on every press without a Remote change; it still only shows, so a second ⌘⌥3 keeps Transfers open. Cost: a plugin that toggles from `invokeLater` loses activation.
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
  - `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java:193-212,415-450`
  - `jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java:55-60`
  - `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java:183-210`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/workspace/WindowContributionsTest.java`
  - `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Panels.java:20-30`
  - `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelSpec.java:15-17`
  - `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelHost.java:23`

**Interfaces:**
- Produces:
  - in `HostedUi`, a private `String running` field (the id of this plugin's action whose handler is running on the UI thread, or null). The action handler sets it before `handler.accept` and restores the previous value in `finally`. `Panels.toggle` sends `Op.ACTIVATING_TOGGLE` when `running` equals the toggled panel's `PanelEntry.toggleActionId()`, else `Op.TOGGLE`.
  - `Contributions.PanelRequest.Op.ACTIVATING_SHOW`, javadoc "A show by the panel's own toggle action: the panel becomes the active tool window under flush frames (plan 15b Rulings 1)."
  - `HostedUi.host(site)` becomes `host(site, spec)`, called from the panel factory where the `PanelSpec` is known. Its `show()` sends `model.requestPanel(new Contributions.PanelRequest(site.windowId(), spec.id(), Op.ACTIVATING_SHOW))` when the host is not closed and `running` equals `spec.toggleActionId().orElse(null)`; otherwise it calls `site.show()` as today.
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

Also add `aShowInsideThePanelsOwnToggleActionActivates`: register a panel `dev.x.tool.queue` with `withToggleActionId("dev.x.tool.queue.show")` whose factory keeps its `PanelHost`; build it in a window, as the file's existing panel-host tests do; register `dev.x.tool.queue.show`, whose handler calls the kept host's `show()`, and `dev.x.tool.queue.other`, whose handler does the same. Invoking the first records one request with `Op.ACTIVATING_SHOW` for that window and panel. Invoking the second, and calling `show()` outside any action, record no request: they go straight to the site, as today.

In `WindowContributionsTest`, using the file's window fixture:
- `aPanelsActivatingToggleUnderFlushFramesActivatesIt`: a flush-framed window gets `ACTIVATING_TOGGLE` for a hidden panel. The panel shows, and the area marks it focused; read `ToolWindowArea`'s focused slot as the plan-12 activation tests do.
- `anActivatingToggleThatHidesThePanelActivatesNothing`: the same request for a showing panel hides it, and no slot is focused.
- `underStandardFramesAnActivatingToggleIsAPlainToggle`: under standard frames the request toggles, and nothing is focused.
- `anActivatingShowUnderFlushFramesActivatesThePanel`: `ACTIVATING_SHOW` for a hidden panel shows and focuses it; sent again while it shows, it stays shown and focused (a show never hides).
- `underStandardFramesAnActivatingShowIsAPlainShow`: the panel shows, and nothing is focused.

Copy the plan-12 tests' way of reading the focused slot.

Run: `./gradlew :jasper-app:test --tests '*HostedUiTest' --tests '*WindowContributionsTest'`
Expected: FAIL to compile (`Op.ACTIVATING_SHOW` does not exist). With the constant alone, `aPanelsOwnToggleActionActivatesAndAnythingElseToggles` would fail on `TOGGLE` first, and the three `ACTIVATING_TOGGLE` cases in `WindowContributionsTest` pass already, since the window handles that op; keep them as the guard.

- [ ] **Step 2: Implement**

`HostedUi`:
- the action handler becomes `containment.run(pluginId, "action " + spec.id(), () -> { String previous = running; running = spec.id(); try { handler.accept(...); } finally { running = previous; } })`;
- `toggle` keeps the `PanelEntry` its existing `noneMatch` check finds, and sends `ACTIVATING_TOGGLE` when `running` equals that entry's `toggleActionId()`, else `TOGGLE`.
- `host(site, spec)` and its `show()` as in Interfaces.

`Contributions.PanelRequest.Op.ACTIVATING_SHOW` and `WindowContributions.showAndActivate` as in Interfaces; `ACTIVATING_TOGGLE`'s javadoc stays.

Docs in the same commit:
- `Panels.toggle`'s javadoc gains: "Called from the panel's own toggle action ({@link PanelSpec#withToggleActionId}), as its handler runs, a toggle that shows the panel also makes it the active tool window under a look with flush tool windows, as a stripe click does (since 0.18.2)."
- `PanelSpec`'s `@param toggleActionId` gains: "; under a look with flush tool windows its toggle or show also activates the panel".
- `PanelHost.show()`'s javadoc gains: "Called from the panel's own toggle action as its handler runs, it also makes the panel the active tool window under a look with flush tool windows (since 0.18.2)."

Run: `./gradlew :jasper-app:test --tests '*HostedUiTest' --tests '*WindowContributionsTest' --tests '*AppContractTest' && ./gradlew :jasper-sdk:javadoc`
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
  - `jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java:94-101`
  - `jasper-ui/src/main/java/dev/jasper/ui/swing/KitView.java:411-418`
  - `jasper-ui/src/main/java/dev/jasper/ui/swing/Buttons.java:17-41,84-92`
- Modify (app):
  - `jasper-app/src/main/java/dev/jasper/app/platform/AppIcons.java`
  - `workspace/ToolWindowHeader.java:84-100,219-235`
  - `workspace/ToolWindowArea.java:297-315,580-590,666`
  - `workspace/WindowContent.java:283`
  - `workspace/WindowChrome.java:328-331`
- Create:
  - `jasper-ui/src/test/java/dev/jasper/ui/swing/KitClassicChromeIconsTest.java`
- Modify (tests):
  - `jasper-app/src/test/java/dev/jasper/app/workspace/ToolWindowHeaderTest.java`
  - `ClassicHeaderTest.java`
  - `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Produces:
  - jasper-ui `IconRegistry`:
    - `public java.util.Optional<PreparedIcon> classicChromeIcon(String name, int logicalSize)`, which returns `chromeIcon(name, logicalSize, ChromeArtwork.CLASSIC)` for one of the six classic names, else empty;
    - `public static java.util.Set<String> classicChromeNames()`, the six names (`BuiltInArtwork.classicChrome().keySet()`).
  - `KitView`:
    - `public void classicChromeIcons(boolean value)`, which refreshes and repaints on change, as `toolbarStrip` does;
    - `public boolean classicChromeIcons()`.

    While set, a `Button` or `MenuButton` in a toolbar strip or in the panel header (`button.panel`), and a host header action with canonical artwork, draw a classic name's `classicChromeIcon` art, tinted `TEXT_PRIMARY` like the window header. In a header strip the art stays authored: the strip ink is not applied. Every other icon is unchanged.
  - App `AppIcons`: `public static java.util.Optional<Icon> classicChromeIcon(Icon icon)`. For a built-in `PresentationIcon` or `SemanticIcon` whose kit name is one of the six (`kitName`, as `stripeIcon` reads it), it returns an `Icon` of the same size. That icon paints `NAMED.classicChromeIcon(name, size)` in the receiving component's theme, prepared on first paint at the device size. Any other icon gives empty.
  - `ToolWindowHeader(String text, Supplier<JPopupMenu> menu, Runnable onHide, Kit kit, boolean flush, ChromeArtwork artwork)`. The five-argument form means `NEW_UI`. Under `CLASSIC` a contributed action whose `AppIcons.classicChromeIcon` is present shows that icon, in place of `stripGlyph` or the action's icon.
  - `ToolWindowArea(JComponent, DockState, Kit, boolean flush, ChromeArtwork artwork)`. The four-argument form means `NEW_UI`. Under `CLASSIC` it passes the artwork to each header and calls `view.classicChromeIcons(true)` on a borrowed kit view beside `setPanelHostActions` (`:586`).
  - `WindowContent:283` passes `look().chromeArtwork()`. `WindowChrome:329` adds `kit.classicChromeIcons(owner.look().chromeArtwork() == ChromeArtwork.CLASSIC);`.

- [ ] **Step 1: Write the failing jasper-ui test**

`KitClassicChromeIconsTest`. Use the jasper-ui swing tests' `KitContext` fixture, as the `Buttons` tests do. Render a `KitView` of an INLINE stack of icon-only GHOST buttons named `search`, `add`, `folder` and `more`, with `toolbarStrip(true)`.
- `theSixNamesInAToolbarStripDrawIntelliJsClassicArt`: with `classicChromeIcons(true)`, the `search` and `add` buttons' centres equal `context.icons().classicChromeIcon(name, size).orElseThrow().icon(theme, SemanticColor.TEXT_PRIMARY)` pixel for pixel, as `ClassicHeaderTest.assertPaintsClassicAdd` checks.
- `onlyTheSixNamesInHeadersAndToolbarStripsChange`:
  - with the flag, `folder` and `more` paint exactly what they paint without it;
  - the same `add` button in a panel body, a VERTICAL stack outside any header, paints exactly what it paints without the flag;
  - in a `Nodes.Panel` header, an `add` header control and a host action set through `setPanelHostActions` with `PanelHostHeader.Artwork("add", null)` paint the classic art.
- `withoutTheFlagNothingChanges`: every button paints the same with the flag off as a view that never had it.

Run: `./gradlew :jasper-ui:test --tests '*KitClassicChromeIconsTest'`
Expected: FAIL to compile (`classicChromeIcons` does not exist).

- [ ] **Step 2: Implement jasper-ui**

- `IconRegistry.classicChromeIcon` and `classicChromeNames`.
- `KitView`'s flag.
- `ButtonControl.refresh`: when `view.classicChromeIcons()` and (`button.toolbarStrip` or `button.panel`) and the icon name is one of the six, use the classic art at the extent it computes. In `HostArtwork.icon`, do the same when the semantic artwork has a null path and its name is one of the six. Pass the view's flag in from the caller that has it, and leave the method's signature otherwise alone.
- The `MenuButton` path in `Menus.java` gets the same check where it sets its icon.

Run: `./gradlew :jasper-ui:test`
Expected: PASS. The gallery is unchanged: no gallery view sets the flag.

- [ ] **Step 3: Write the failing app tests**

`ToolWindowHeaderTest`. Make headers with the six-argument constructor and the Enterprise kit (`EnterpriseLook.kit()`, flush), under `EnterpriseLook.controller(false)`, and restore the default look after.
- `underClassicIconsAContributedAddActionDrawsIntelliJsClassicArt`: a `PanelHeaderState` with one action whose icon is `AppIcons.named("ADD")`. Its button's centre equals `new IconRegistry().classicChromeIcon("add", size).orElseThrow().icon(theme, TEXT_PRIMARY)`, painted as in `assertPaintsClassicAdd`.
- `otherNamesKeepTheirGlyphUnderClassicIcons`: an action iconed `folder`, and one with a plugin path icon, paint the same under `CLASSIC` as under `NEW_UI`.
- `underNewUiNothingChanges`: the six-argument constructor with `NEW_UI` paints the add action exactly as the five-argument one does.

In `ClassicHeaderTest`, add `aToolbarViewUnderClassicIconsIsMarkedAndUnderNewUiIsNot`: a `KitView` toolbar entry is mounted with `classicChromeIcons()` true under `WorkspaceLook.Icons.CLASSIC` and false under `NEW_UI`. Mount a real `KitView` through the window's `Contributions` as `enterprisesDefaultToolbarIsClassicIconSquares` does.

Run: `./gradlew :jasper-app:test --tests '*ToolWindowHeaderTest' --tests '*ClassicHeaderTest'`
Expected: FAIL to compile.

- [ ] **Step 4: Implement the app side**

Implement as in Interfaces, and add the migration rows the test reports.

Run: `./gradlew :jasper-app:test --tests '*ToolWindowHeaderTest' --tests '*ClassicHeaderTest' --tests '*DockMockTest' --tests '*ClassicCompositionRenderTest' --tests '*ClassicVaultRenderTest' --tests '*EnterpriseWorkstationMockTest' --tests '*DefaultDockPixelsTest' --tests '*ChromeRenderTest' --tests '*ThemeMigrationCoverageTest' --tests '*ProjectionReadTest' --tests '*VariantReadTest'`
Expected: PASS.
- `DefaultDockPixelsTest` and the default `ChromeRenderTest` images are unchanged.
- Where an Enterprise mock test's probe falls on a classic `add` in Sessions' or Tunnels' header, update the probe to the classic art and note the user's 2026-10-19 direction in a comment.
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
  - `presentation/ChromeSpec.java`
  - `presentation/KitParser.java:51-52,296-310`
  - `presentation/WorkspaceLook.java`
  - `presentation/Projections.java` (`of`: `WorkspaceLook` and `PluginLook.search`)
  - `workspace/WorkspaceChrome.java:20-68,128-136`
  - `jasper-app/src/test/java/dev/jasper/app/presentation/KitParserTest.java`
  - `ProjectionsTest.java`
  - `architecture/ProjectionReadTest.java:24-27,96-108`
  - `testsupport/EnterpriseLook.java:22-41`
  - `workspace/ChromeRenderTest.java:72-75`

**Interfaces:**
- Produces:
  - `ChromeSpec`:
    - `public enum ToolbarSearch { FIELD, ICON }`, javadoc "The toolbar's Search Everywhere field, or IntelliJ Classic's icon button.";
    - the record's last component `ToolbarSearch toolbarSearch`;
    - `DEFAULT` and `CLASSIC` take `FIELD`;
    - the compact constructor refuses `ICON` unless `palette == RUN_WINDOW && titleBar != UNIFIED`, with `IllegalArgumentException("toolbar_search = icon needs palette = run_window and title_bar = native or classic")`.
  - `KitParser.CHROME` gains `"toolbar_search"`, parsed by `point`. After the `menu_bar` check, the parser refuses `ICON` with `[chrome].toolbar_search: "icon" needs [chrome].palette = "run_window" and [chrome].title_bar = "native" or "classic"`.
  - `WorkspaceLook`:
    - `public enum Search { FIELD, ICON }`, javadoc "The classic toolbar's search field, or its icon button.";
    - a ninth component `Search search`;
    - the eight-argument constructor means `Search.FIELD`.
  - `Projections.of`: `WorkspaceLook.search` is `ICON` when `chrome.toolbarSearch() == ICON` (the constraint guarantees a classic header), and `PluginLook`'s `search` is the same.
  - `WorkspaceChrome`:
    - a `boolean iconSearch` read once in `of` from `look.search()`;
    - `header()` returns `new ClassicHeader(iconSearch)` for `CLASSIC`. Until Task 4, `ClassicHeader` takes and ignores the flag, through a one-line constructor storing it. Its no-argument constructor stays and means the field (`WindowPairsTest.java:93` uses it);
    - `equals` and `hashCode` include `iconSearch`.
  - Test-only `EnterpriseLook.titled` copies `toolbarSearch`. `EnterpriseLook.CHROME` stays `FIELD` until Task 4, when the shipped kit names `icon`.

- [ ] **Step 1: Write the failing tests**

- `KitParserTest`:
  - `theToolbarSearchPointDefaultsToTheFieldAndTakesTheIcon`. `kit = 1`, `base = "classic"` and no `[chrome]` give `FIELD`. `[chrome]` with `toolbar_search = "icon"` (the classic base has `run_window` and `native`) gives `ICON`.
  - New `cases` in `everyInvalidKitGivesOneMessageNamingTheKey`:
    - `head + "[chrome]\ntoolbar_search = \"icon\"\n"` on a default base gives `[chrome].toolbar_search: "icon" needs [chrome].palette = "run_window" and [chrome].title_bar = "native" or "classic"`;
    - `"kit = 1\nbase = \"classic\"\n[chrome]\ntoolbar_search = \"icon\"\npalette = \"anchored\"\n"` gives the same message;
    - `toolbar_search = "box"` gives `[chrome].toolbar_search: "box" is not one of field, icon`.
  - In `everyChromeAndControlPointTakesBothItsValues`, give `toolbar_search = "icon"` a classic base, so the constraint holds.
- `ProjectionsTest.theToolbarSearchReachesTheWorkspaceAndPlugins`: a classic-base `ChromeSpec` with `ICON` gives `workspace().search() == ICON` and `plugins().search() == ICON` on each host. `ChromeSpec.DEFAULT` gives `FIELD` for both.
- `ProjectionReadTest`: the `WorkspaceLook` pattern's alternation gains `Search`. In `aLookReadElsewhereIsReported`, add a `WorkspaceLook$Search` probe reported on its own, as the `Icons` one is.

Run: `./gradlew :jasper-app:test --tests '*KitParserTest' --tests '*ProjectionsTest' --tests '*ProjectionReadTest'`
Expected: FAIL to compile.

- [ ] **Step 2: Implement**

Implement as in Interfaces. Update every `new ChromeSpec(` of fact 12 with `ChromeSpec.ToolbarSearch.FIELD`. `ChromeRenderTest.covering()` passes `FIELD` (Rulings 8); add a comment beside the `Icons.CLASSIC` it already passes.

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
  - `workspace/ClassicHeader.java`
  - `workspace/ClassicToolbarButton.java:31-42`
  - `workspace/WindowChrome.java` (a `paletteShortcut()` accessor)
  - `plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml:5-14`
  - `jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java:22-25`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/PresentationConformanceTest.java:30-31`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/HostedPresentationTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/workspace/ClassicHeaderTest.java`
  - `EnterpriseWorkstationMockTest.java:120-135`
  - `tools/visual/pairs.json` (the two `chrome-toolbar-*` entries)
  - `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePresentations.java`

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
    - A package-private `JButton searchButton()` returns the button, or null, for tests.

- [ ] **Step 1: Write the failing tests**

In `ClassicHeaderTest`:
- Give `classic(...)` and `window(...)` an overload taking `WorkspaceLook.Search`, keeping today's overloads at `FIELD`.
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
- `PresentationConformanceTest.KEYS` gains `"chrome/toolbar_search"`.

Run: `./gradlew :jasper-app:test --tests '*ClassicHeaderTest' --tests '*HostedPresentationTest' --tests '*PresentationConformanceTest'`
Expected: FAIL.

- [ ] **Step 2: Implement and name it in Enterprise's kit**

- `ClassicHeader`, `ClassicToolbarButton.tooltip` and `WindowChrome.paletteShortcut` as in Interfaces. The class javadoc gains: "Under `toolbar_search = "icon"` (the user, 2026-10-19), an icon-only "Search Everywhere" button with IntelliJ's classic magnifier leads the trailing group in place of the field."
- `enterprise.kit.toml`: after `icons = "classic"` add `toolbar_search = "icon"`.
- `EnterpriseLook.CHROME` gains `ChromeSpec.ToolbarSearch.ICON` (`EnterpriseFixtureTest` holds it equal to the shipped table).
- `FakePresentations.CLASSIC` takes `ToolbarSearch.ICON`, and its javadoc says "Enterprise's on Linux" (Rulings 11).

Run: `./gradlew :jasper-app:test --tests '*ClassicHeaderTest' --tests '*HostedPresentationTest' --tests '*PresentationConformanceTest' --tests '*EnterpriseFixtureTest' && ./gradlew :jasper-sdk-testkit:test`
Expected: PASS.

- [ ] **Step 3: Re-measure the Enterprise mock expectations**

Run: `./gradlew :jasper-app:test --tests '*EnterpriseWorkstationMockTest' --tests '*EnterpriseMenuRowMockTest' --tests '*WindowHeaderTest' --tests '*CollapsingMenuBarTest' --tests '*RunWindowModelessTest' --tests '*ChromeRenderTest' --tests '*EnterpriseFirstWindowTest' --tests '*EnterpriseHomeTest'`

Expected: `EnterpriseWorkstationMockTest`'s search-field assertions (`:123-131`) FAIL, since the field is gone. Replace them:
- assert the search button's right edge is 6 px (12 mock px) before Settings…'s left, the trailing group's gap;
- assert its top and size match the other 22 px icon squares, as the toolbar's icon run is asserted;
- comment: "The user's 2026-10-19 direction: IntelliJ Classic's Search Everywhere icon in place of the mocks' field (x 2116-2715)."

Fix any other failure the same way, each with that note. Do not move an expectation that is not about the field or the new button.

Then the pairs:

```bash
python3 -I tools/visual/check_pairs.py "$(mktemp -d)" | grep 'chrome-toolbar'
```

Record both `chrome-toolbar-*` agreements.
- If either is below its 0.30 floor, set that pair's `min_agreement` to its measured value rounded down to two places. Append to its `note`: " The user's 2026-10-19 direction: IntelliJ Classic's Search Everywhere icon in place of the mocks' field; the floor is the measured <value>."
- If both pass, add only the note's first sentence.
- No other pair's floor moves.

Run: `./gradlew :jasper-app:test --tests '*EnterpriseWorkstationMockTest' --tests '*ChromeRenderTest' && python3 -I tools/visual/check_pairs.py "$(mktemp -d)" > /dev/null; echo "exit $?"`
Expected: PASS and `exit 0`.

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
- Modify: `docs/design/theme-engine/migration.csv` (only if the test matches `CANDIDATE`)

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
                for (Path jar : ManagedVaultLoadingTest.jars(plugin))
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

The two `{ ... }` bodies are the obvious loops. `ManagedVaultLoadingTest.jars` and `StaticDefaultTest.classFiles` are package-private in other test packages. If a helper is not visible, copy its two lines in, noting the original.

Run: `./gradlew :jasper-app:test --tests '*LookIdentityTest'`
Expected: PASS at once: plan 15a removed the last references. If it fails, the report names the class. Fix that class (a look decision belongs in the kit), not the test.

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
  - `docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`
  - `docs/sdk-icons.md:21-24`
  - `docs/plugin-authoring.md:196-224`
  - `docs/plugin-ui.md:61-65`
  - `docs/command-palette.md:44`
  - `docs/app-architecture.md:242-243`
  - `jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java:16-19`
  - `PanelSpec.java:8,13`
  - `Panels.java:6`
  - `Rail.java:5`
  - `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Presentation.java` (the `toolbarSearch` javadoc)
  - `docs/STATUS.md`
  - this plan's status banner

- [ ] **Step 1: Amend the spec** (smallest accurate edits, re-wrapped at 120 columns)

- **Header:** in plan 15a's "Amended on 2026-10-19 at the user's direction (plan 15): …" line, add 5.2, 5.3, 10.3, 10.7 and 16.2 to its list in order. Append to its last sentence: "; a panel's own toggle action activates its tool window, IntelliJ's classic icons draw the six classic names in tool-window headers and toolbar views, and Enterprise's toolbar shows IntelliJ Classic's Search Everywhere icon in place of the field (`[chrome] toolbar_search = "icon"`)".
- **5.1:** in the listing, after `icons = "classic"` add `toolbar_search = "icon"`.
- **5.2:**
  - "Twenty-four points: 10 chrome points" becomes "Twenty-five points: 11 chrome points".
  - In the chrome table, after `icons`, add: `` | `toolbar_search` | `field` | `icon` | IntelliJ Classic's Search Everywhere: an icon-only button with the classic magnifier, before Settings…, in place of the "Search everywhere" field (10.3) | ``.
  - The `icons` row's last cell gains ", and for the six classic names in tool-window headers and toolbar views (10.3, 10.7)".
- **5.3:** append "`toolbar_search = "icon"` requires `palette = "run_window"` and `title_bar = "native"` or `"classic"`: the anchored card's home is the field, and the unified header holds it."
- **5.9:** "- `menu_bar = "window"` with `title_bar = "unified"`;" gains a following bullet: "- `toolbar_search = "icon"` without `palette = "run_window"` and a native or classic title bar;".
- **9.1:**
  - the `WorkspaceLook` consumer row's fields gain "search (FIELD, ICON)";
  - the field table gains `` | `WorkspaceLook.search`, `PluginLook.search` | ICON when `toolbar_search = "icon"` | FIELD | ``.
- **10.3:**
  - After "then, trailing, the title search field reading "Search everywhere" with the COMMAND_PALETTE keycap, and "Settings…"." add: "Under `toolbar_search = "icon"` (the user, 2026-10-19; Enterprise) the field is IntelliJ Classic's Search Everywhere icon: an icon-only 22 px button with the classic magnifier, tooltip "Search Everywhere" and the palette's shortcut, leading the trailing group before Settings… and doing what the field does; it stays icon-only under `icons_and_labels`."
  - After "The icons are the look's `[chrome] icons` set (5.2), IntelliJ's classic icons under Enterprise." add: "Tool-window headers and toolbar views draw the same classic art for those six names; every other name, and every panel body, keeps its catalog art."
- **10.7:** after the sentence on activation ("…a View toggle the host declares (`PanelRequest.Op.ACTIVATING_TOGGLE`)…"), add: "A plugin's toggle or `PanelHost.show()` made while the panel's own declared toggle action runs (`PanelSpec.withToggleActionId`) is activating too (`PanelRequest.Op.ACTIVATING_SHOW` for the show; the user, 2026-10-19; plan 15), so View › Tool Windows and the stripe-number shortcuts activate the bundled tool windows, as IntelliJ's ⌘1 does."
- **16.1:**
  - item 4: "across 9 of the ten chrome points" becomes "across 9 of the eleven chrome points", and after "`icons` is held at `classic` in every row" insert "and `toolbar_search` at `field`";
  - item 7: add the bullet "- `LookIdentityTest`: no production class of the app, jasper-ui or a bundled plugin other than Enterprise names "enterprise" (plan 15).".
- **16.2:** add "Enterprise's Search Everywhere icon (its tooltip, the Run window it opens, a narrow window); ⌘⌥1 to ⌘⌥3 activating the bundled tool windows; the classic `add` icon in Sessions' and Tunnels' headers;" after "the Run window's drag, …".

Check:

```bash
spec=docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md
python3 -I - "$spec" <<'PY'
import re, sys
text = re.sub(r"\s+", " ", open(sys.argv[1], encoding="utf-8").read())
for phrase in ("Twenty-four points",):
    print(text.count(phrase), "(want 0)", phrase)
for phrase in ("Twenty-five points: 11 chrome points", "| `toolbar_search` | `field` | `icon` |", "`toolbar_search = \"icon\"` requires",
               "IntelliJ Classic's Search Everywhere icon", "`LookIdentityTest`", "a panel's own toggle action activates its tool window",
               "is activating too (`PanelRequest.Op.ACTIVATING_SHOW` for the show; the user, 2026-10-19; plan 15)"):
    print(text.count(phrase), "(want at least 1)", phrase)
PY
```

Expected: one `0` line, and every other count at least `1`.

- [ ] **Step 2: The guides and the SDK javadoc**

- `docs/sdk-icons.md`: after the stripe paragraph (`:21-24`), add: "Under the Enterprise look IntelliJ's classic icons also draw `"add"`, `"split"`, `"zoom"`, `"settings"`, `"window.add"` and `"search"` in the toolbar (actions and toolbar views), the status bar and tool-window headers (your `PanelHeader` actions and your panel's header controls); in a panel's or window's body these names keep their ordinary art."
- `docs/plugin-authoring.md`:
  - `:196`'s "stripes of icons on the window's left and right edges" becomes "stripes on the window's left and right edges: icons, or under a classic look such as Enterprise labelled buttons that start with the icon and read the panel's title".
  - After "and Jasper registers no `<panel id>.toggle`." (`:206`) insert: "Under a look with flush tool windows (Enterprise), when your action shows the panel (through `Panels.toggle` or `PanelHost.show()`) it also makes it the active tool window, as a stripe click does; a toggle or show from anything else does not."
  - `:222-224`: "A rail button, on the left stripe after the panel icons, runs one of your actions. A plugin window is a frame Jasper builds: icon, macOS title bar, menu bar, theme tracking and remembered bounds are its job; the content is yours." becomes "A rail button, on the left stripe after the panel icons (a labelled entry under a classic look), runs one of your actions. A plugin window is a frame Jasper builds: icon, title bar, menus (in the window, or under the Enterprise look in the screen menu bar on macOS and none on Windows and Linux), theme tracking and remembered bounds are its job; the content is yours."
- `docs/command-palette.md:44`: "the toolbar's Search everywhere field open it" becomes "the toolbar's Search Everywhere icon open it".
- `docs/app-architecture.md:242`: "a toolbar row of left-packed buttons with the search field and Settings… trailing" becomes "a toolbar row of left-packed buttons with the search field, or under `toolbar_search = "icon"` a Search Everywhere button, and Settings… trailing".
- `docs/plugin-ui.md:61-65`: after "a panel's stripe label starts with its icon" and the plugin-icons link that follows it, add ", and the six classic icon names draw IntelliJ's classic art in headers and toolbar views".
- `StandardMenu.java:16-19`: "after Tab, Session and Transfer and before plugins' own menus" becomes "after Tab, Session and Transfer and before plugins' own menus, or after plugins' menus in a classic menu bar".
- `PanelSpec.java:8`: "A panel as the stripes show it: an icon on a tool window stripe and, while it shows, a tool window with a header." becomes "A panel as the stripes show it: an icon on a tool window stripe (under a classic look a labelled button starting with the icon) and, while it shows, a tool window with a header." `:13`'s `@param title` gains "and, under a classic look, the stripe button's label".
- `Panels.java:6`: "Each registered panel gets a stripe icon in every window." becomes "Each registered panel gets a stripe button in every window."
- `Rail.java:5`: "Plain action buttons on the left stripe, after the panel icons" becomes "Plain action buttons on the left stripe, after the panels' buttons".
- `Presentation.java`'s `toolbarSearch` `@param`: "how the toolbar offers Search Everywhere: the field, or IntelliJ Classic's icon button".

Run: `./gradlew :jasper-app:test --tests '*AppDocumentationTest' && ./gradlew :jasper-sdk:javadoc`
Expected: PASS.

- [ ] **Step 3: Prove the default look unchanged**

Run plan 15a Task 9 Step 3's block unchanged; it compares against `plan15-baseline`.
Expected: `dark: identical (161 files)`, `light: identical (161 files)` and `jasper-app default look: identical (11 files)`, with no `diff` output.

- [ ] **Step 4: Run the whole build and count**

Run: `./gradlew check --continue`, then plan 15a Task 9 Step 4's counting script.
Expected: BUILD SUCCESSFUL, 0 failures and 0 errors. Record the counts against plan 15a's.

- [ ] **Step 5: Render the Enterprise gallery and check the pairs**

```bash
base=/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan15-baseline
out=$(mktemp -d)
for theme in light dark; do ./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=$theme -Pjasper.galleryScale=2; done
python3 -I tools/visual/check_pairs.py "$(mktemp -d)" > "$out/pairs.txt"; echo "exit $?"
diff <(sed 's/ agreement=.*//' "$base/pairs.txt") <(sed 's/ agreement=.*//' "$out/pairs.txt") && echo "same verdicts"
diff "$base/pairs.txt" "$out/pairs.txt"
```

Expected:
- `exit 0` and `same verdicts` (101 of 101 PASS);
- the last `diff` shows only the two `chrome-toolbar-*` lines and any pair whose crop holds a header `add` icon (Task 2).

Record each changed line.

- [ ] **Step 6: Write `docs/STATUS.md`'s new top entry and this plan's banner**

The entry goes above plan 15a's and starts as below. The template stays fenced, as in plan 15a.

```markdown
**Enterprise presentation, plan 15b: look polish at the user's direction — 2026-10-19** (`claude/enterprise-look-query`, worktree `.claude/worktrees/enterprise-15`, after plan 15a; [plan](superpowers/plans/2026-10-19-enterprise-15b-look-polish.md), [spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md))
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

Then this plan's status banner: "> **Status (2026-10-19): executed on `claude/enterprise-look-query` (`<first>..<last>`, then the Task 6 documentation commit).** Deviations from this plan's text: …".

Run: `./gradlew :jasper-app:test --tests '*AppDocumentationTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add docs jasper-sdk/src/main/java/dev/jasper/sdk/ui
git commit -m "Docs: Enterprise plan 15b, look polish, at the user's direction

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
