# Enterprise presentation 12: IntelliJ classic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After trying Enterprise the user asked for IntelliJ's classic look in five places; this plan delivers all five. The Enterprise toolbar shows IntelliJ's classic icons, which are vendored and attributed on the About page. Remote's session view becomes IntelliJ's run-configuration combo with the green Run button (SDK 0.18.1). The Enterprise theme becomes a port of IntelliJ Light and the New UI Dark theme. A clicked tool window takes the focused header tint, exactly #DFE2E9 in Light. The Run window opens centred across the window, in its upper part.

**Architecture:**
- **Artwork.** The artwork is host-owned. jasper-ui gains a third chrome tier: 14 byte-identical classic SVGs from JetBrains/intellij-community at `8062c061ff33`, checked against a hash manifest. A new `[chrome] icons` variant point is projected as `WorkspaceLook.Icons` and read only by `WorkspaceChrome`. `WorkspaceChrome` marks the header and the status bar as receivers of that set. Two authored catalog names, `run` and `application`, carry the Run triangle and the run-configuration icon into kit views.
- **Toolbar and SDK.** The classic header draws classic icons: 22 px icon buttons, or icon plus label. SDK 0.18.1 lets a toolbar view hold icon-only GHOST buttons. The classic buttons face draws a rich DROPDOWN in a toolbar strip as IntelliJ's run-configuration combo. Remote moves to that combo and a GHOST `run` button.
- **Theme.** The converter gains `convert_port`, which builds `enterprise.json` from IntelliJ's own theme files, with a golden test like Catppuccin's. Every Enterprise mock test reads colours from the shipped theme's roles rather than from mock pixels, so it keeps testing geometry and which role is painted while the theme follows IntelliJ.
- **Active tool window.** Under flush frames, `ToolWindowArea` activates a frame on any mouse press inside it.
- **Run window.** The Run window's home becomes `UpperHome`.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and Java2D, jsvg (inside jasper-ui only), tomlj, Gradle (Kotlin DSL),
JUnit and AssertJ, Python 3.11+ standard library for the converter, scripts and checks (always `python3 -I`), `curl`.

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md).
Read its header amendment lines and sections 1 (decisions 4 and 14), 5.2, 5.8, 7.1 to 7.7, 8, 10.3, 10.7, 11.5, 14.4, 16.1 and 19.1. The user's requests of 2026-10-18 are binding and override the mocks where they differ:
- (1) classic IntelliJ icons, vendored and attributed on the About license page, shown in the classic toolbar;
- (2) the session dropdown as IntelliJ's run-configuration combo, with IntelliJ's Run button in place of Connect;
- (3) an active (clicked-into) tool window header tinted exactly #DFE2E9;
- (4) the Enterprise theme replaced by ports of the classic IntelliJ Light and the New UI Dark theme, in one file;
- (5) the Run window centred horizontally, in the upper half of the app.

Screenshots: `/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/images/9.png` (the classic toolbar) and `10.png` (the focused header). The research, with every icon path, licence fact and the combo's measurements, is `/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan12-research.json`.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-12`, branch `claude/enterprise-intellij-classic`, from `main` at `faeec76f3`. Enterprise plans 1 to 11, the side-stripe change and upstream's CI and Slate Cobalt cursor commits are all merged there. Run every command from the worktree root. Line numbers below are from `faeec76f3`. When an anchor has moved, re-find it; never edit around a missing anchor.

## Before you start (refresh)

Do not rebase, merge or push.

```bash
git log --oneline -1                                                     # faeec76f3 or a later plan-12 commit
grep -c '^\*\*Enterprise presentation, plan 11:' docs/STATUS.md           # 1
grep -c 'record ChromeSpec(TitleBar titleBar, MenuBar menuBar, String windowTitle, ToolWindows toolWindows, EditorTabs editorTabs,' \
  jasper-app/src/main/java/dev/jasper/app/presentation/ChromeSpec.java  # 1
grep -c 'VERSION = "0.18.0"' jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java   # 1
ls jasper-ui/src/main/resources/dev/jasper/ui/icons/classic 2>/dev/null | wc -l       # 0: Task 1 adds it
```

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain. Always use `./gradlew`, never a system `gradle`, and never run `./gradlew --stop`: other sessions share the daemons.
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache. Headless JUnit renders and `:jasper-ui:gallery` may run. Everything visible is a GUI check for the user (Task 12).
- Work only in the worktree above, with one commit per task. Every commit message ends with `🤖 Generated with Claude Code at The Home Depot`, a blank line, then `Co-Authored-By: Claude <noreply@anthropic.com>`. Do not push or merge.
- **The default look stays byte-identical.** Every file the headless gallery writes for Slate Cobalt Dark and Light must equal the files written at `faeec76f3` (Task 12 proves it). The changes that could reach the default look are each kept away from it:
  - `IconRegistry.chromeIcon(name, size)` keeps its artwork.
  - `DefaultButtonFace.toolbarCombo` returns null.
  - The rail-size glyph applies only to an icon-only button in a toolbar strip, which no default-look view has.
  - `ChromeArtwork.NEW_UI` is today's receiver mark.
- Looks cannot ship artwork: the classic set lives in jasper-ui and a kit only selects it. The vendored SVGs are byte-identical to upstream, each keeping its JetBrains copyright comment. No JetBrains product logo is vendored.
- The licence shipped for the icons is the standard Apache License 2.0 text, `jasper-ui/.../icons/LICENSE.txt`, which is already there. It is never the upstream root `LICENSE.txt`, which holds JetBrains' build terms. The upstream NOTICE text is kept verbatim.
- `WorkspaceLook.Icons` is read only by `WorkspaceChrome` and the presentation package (`ProjectionReadTest`). Faces never name `Kit`, `ControlVariants` or `ChromeSpec` (`StrategyIsolationTest`, `VariantReadTest`).
- SDK 0.18.1 is additive: no record shape changes. Only Remote's manifest moves to `sdk = ">=0.18.1, <0.19"`; every other manifest stays `>=0.18.0`.
- Mock tests: geometry probes and agreement floors stay. A floor is re-measured only with a measured value and a note naming the user's 2026-10-18 direction. Colours follow the shipped theme's roles (Task 3), so the theme follows IntelliJ (Task 9), not the mocks.
- Javadoc doclint fails the build on errors: jasper-app uses `-Xdoclint:all`, jasper-ui `-Xdoclint:all,-missing`, the SDK `all`. Every `{@link}` must resolve. No comment may contain a raw `<`, `>`, `&` or `->`. New public types carry a class comment, and record components carry an `@param`.
- `docs/design/theme-engine/migration.csv`: every new or renamed source under jasper-app or plugins that `ThemeMigrationCoverageTest.CANDIDATE` matches gets its row in the same commit.
  - A row has exactly eight fields. New rows use `nonvisual-helper` and `none,none`, with the file's test as evidence.
  - The disposition begins `New in Enterprise plan 12 Task N` (`Test/fixture ownership; New in …` for a test) and uses semicolons, never commas.
- `ColourLiteralScanTest` stays green. No production Java gains a colour literal, and new face colours are roles. `themes/enterprise.json` is exempt by its `plugin.toml` entry.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters. The ellipsis, ▼ and × in copy are ordinary characters, written as is.
- Documentation edits are the smallest that make each document accurate. Each spec change carries the one header amendment line Task 12 writes. Earlier `docs/STATUS.md` entries and `docs/superpowers/` history are not rewritten.
- MVP rule (no compatibility shims): removed things go outright, with no aliases. This plan removes `RUN_WINDOW_HOME_SIDE`/`_BOTTOM`, `ClassicToolbarButton.Glyph`, the `session.label` node, the `dark-terminal` scheme and `CornerHome`'s name.

## Review Focus

1. **The keyboard is in the terminal and the user presses a spot in a tool window that takes no focus.** Examples: blank space under the rows, the header strip or one of its actions, a pointer-only kit view, a label. The frame's header must take the focused tint and the keyboard must move into the frame. A later press in the terminal hands the tint back. Task 10: `PressedFrameTest.aPressOnBlankSpaceOrTheHeaderStripActivatesTheFrame`, `thePressMovesTheKeyboardToTheFramesFirstKeyedControlOrTheFrame` and `aPressInTheTerminalHandsTheTintBack`.
2. **A short or narrow window opens the Run window.** It must stay inside the dock work area, never over the toolbar row or off the area. In a window where it fits, it stays entirely in the upper half. Task 11: `UpperHomeTest.aShortWindowKeepsTheRunWindowInsideTheWorkArea` and `aWindowTooTallForASeventhDownEndsAtTheMiddle`.
3. **A host with a very long name, or no saved sessions at all.** The combo stays 24 px tall and elides the name at its cap. With nothing to run it reads "No saved sessions" and Run is disabled. Task 7: `ClassicToolbarComboTest.aLongNameIsElidedAtTheCap`; Task 8: `SessionToolbarViewTest.withNoSavedSessionsTheComboSaysSoAndRunIsDisabled`.
4. **A disabled or hovered Run button.** The green triangle fades as one layer, never recoloured; at rest the button is flat; hovered it shows the classic hover frame. A labelled GHOST button in a toolbar view is still refused. Task 7: `KitViewToolbarStripTest.aDisabledRunButtonFadesItsTriangleWithoutRecolouringIt` and `anIconOnlyGhostButtonInAToolbarStripIsFlatWithARailGlyph`; Task 6: `PluginContractTest.aToolbarViewAdmitsIconOnlyGhostButtons`.
5. **`icons` mode in a window narrowing to its minimum.** The icon buttons keep their 22 px squares, and Split keeps its corner ▼ and its menu until it goes into the overflow menu; then the view hides and the field collapses, in today's order. Task 4: `ClassicHeaderTest.asTheRowNarrowsIconButtonsKeepTheirSquaresAndSplitItsCornerArrow`.

## Verified facts

At `faeec76f3` unless marked.

1. `ClassicHeader.build` (`ClassicHeader.java:39-58`) places, in order:
   - `classicButton(toolbar, NEW_TAB, "New Session", Glyph.PLUS, "square-plus", 90)`;
   - a separator;
   - `chrome.classicSplit()`;
   - `classicButton(ZOOM_PANE, "Maximize", NONE, "maximize", 10)`;
   - the plugin group;
   - and, in the trailing toolbar, `classicButton(OPEN_SETTINGS, "Settings…", NONE, "settings", 100)`.

   `toolbarMode` (`:145-152`) sets `labels = mode == ICONS_AND_LABELS` on both bars. `contributed` (`:165-170`) builds a `ClassicToolbarButton` with `AppIcons.icon("command")` when the action has no icon.
2. `ClassicToolbarButton` (`ClassicToolbarButton.java:26-106`):
   - `Glyph { NONE, PLUS }` (`:28`); every button gets `setToolTipText(label)` (`:41`).
   - `getPreferredSize` (`:54-61`) returns a `side × side` square when the text is null, `side()` being the bar's `buttonSide()` (`BUTTON_HEIGHT`, 24 under classic, from `WindowToolbar.Leading.classic`, `WindowToolbar.java:66-69`).
   - The labelled paint draws text only, with a drawn "+" for PLUS and a ▼ for a caret. The icon-only paint draws the icon centred and no caret (`:85-90`).
   - `WindowChrome.classicButton` and `classicSplit` are at `WindowChrome.java:135-152`.
3. `WindowToolbar.doLayout` gives every placed button `height = side` and a view `min(height, preferred)` (`WindowToolbar.java:170`).
4. `AppIcons` marks chrome receivers with `CHROME_RECEIVER = Boolean.TRUE` (`AppIcons.java:116-128`). `NativePreparation.prepare` keeps `NAMED.themedIcon` and `NAMED.chromeIcon` (`:139`), and `paint` picks the chrome icon when `inChrome(c)` (`:141-143`). The receivers are `WindowHeader.java:40`, `WindowStatusBar.java:63` and `ToolStripe.java:96` (the default look's icon rails). `WindowChrome` builds the header at `WindowChrome.java:73`, and `WorkspaceChrome.statusBar` builds the status bar (`WorkspaceChrome.java:104-109`).
5. `IconRegistry`:
   - `chromeIcon(name, size)` (`IconRegistry.java:92`) uses `BuiltInArtwork.chrome()`, else the catalog (`:93-105`).
   - Authored catalog artwork is selected only by the ordinary theme overloads (`select(..., ordinary=true)` with a theme, `:73-79`); other contexts paint the vector path of the same name, so every catalog name needs a vector path in `builtIns()` (`play` at `:415`).
   - `FILLED_BUILT_INS` is at `:21`.
   - Buttons paint their glyph with `icons().icon(name, theme, role, extent)` (`Buttons.java:85-89`). `extent` is `IconSize.RAIL` only for embedded or panel icon-only buttons, else `CONTROL`.
6. `BuiltInArtwork` (`BuiltInArtwork.java`) has seven catalog names (`:14-18`) and ten chrome names (`:28-32`). Both read hash manifests with a fixed count (`assets-selected.tsv` 14, `assets-chrome.tsv` 20; `:39-40`). Every `Pair` reads a `_dark` sibling (`:35-37`). `BuiltInArtworkTest:19` pins the catalog at 7. Resources live under `dev/jasper/ui/icons/<expui path>`, so classic files that share an expui path (for example `actions/moveToWindow.svg`) need their own folder.
7. `ChromeSpec` has nine points (`ChromeSpec.java:19-20`; `DEFAULT` and `CLASSIC` at `:39-43`). `KitParser` lists `CHROME` (`KitParser.java:57-58`), parses each point with `point(...)`, which matches the lower-cased constant name (`:287-293`, `:295-309`), and allowlists four `[defaults]` keys (`:514-519`). `DefaultSource.ALLOWED` holds the same four (`DefaultSource.java:16-17`). `ConfigSnapshot.defaults(DefaultSource)` and `from(values, defaults, …)` apply them (`ConfigSnapshot.java:87-100`).
8. `WorkspaceLook` has seven components (`WorkspaceLook.java:17`). `Projections.of` builds it at `Projections.java:56-62` and `fallback` at `:101-104`. `WorkspaceChrome.of` reads its variants (`WorkspaceChrome.java:68-77`). `ProjectionReadTest` holds `WorkspaceLook$(Menus|Header|Stripes|Tabs|Status|FrameStyle)` to `WorkspaceChrome` (`ProjectionReadTest.java:24-27`) and lists the palette strategies at `:114-115`. `new ChromeSpec(` and `new WorkspaceLook(` appear in:
   - main: `ChromeSpec.java` (2), `KitParser.java` (1) and `Projections.java` (2);
   - tests: `KitParserTest`, `ProjectionsTest` (4), `testsupport/EnterpriseLook`, `ChromeRenderTest`, `ClassicHeaderTest`, `ClassicTabsTest`, `CornerHomeTest`, `DockChromeMatrixTest`, `DockChromeTest`, `WindowChromeMenusTest`, `WindowPairsTest` and `WindowTitleTest` (2).
9. The Enterprise kit file (`plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml`) sets the nine `[chrome]` keys and four `[defaults]`. `PresentationConformanceTest.KEYS` pins every key (`:28-41`) and the plugin version 0.1.0 (`:55`).
10. `ToolbarViewAdmission` admits a `Nodes.Button` only when it is PRIMARY, and at most one Button. The host copy is at `jasper-app/.../plugins/ToolbarViewAdmission.java:24` and `:30-31`; the testkit copy is identical apart from its class comment. The rule is documented in `ToolbarItem.View`'s Javadoc (`ToolbarItem.java:35-46`). `JasperSdk.VERSION = "0.18.0"` (`JasperSdk.java:6`). `FakeKit.BUILT_INS` (`FakeKit.java:21`) mirrors the host's built-in names. The shared contracts are `PluginContractTest.sessionBar` and the toolbar view tests (`:930-1030`). `ModelChecks.button` requires an icon and a tooltip when the label is blank (`ModelChecks.java:17-20`).
11. `ClassicButtonFace.paintPicker` (`ClassicButtonFace.java:199-218`) draws the button gradient, a ▼ and `PickerMenus.line`, with no icon. `flat(b)` is true for a GHOST in an INLINE stack (`:44-50`). `recolours` is true only for joined halves (`:156`), so authored artwork keeps its colours. A DROPDOWN's face is assigned at `Choices.java:131`. `ChoiceCombo.getPreferredSize` gives a faced combo `PICKER_DEFAULT_HEIGHT` (`:433-437`). `labelShown()` already reads `view.toolbarStrip()` (`:35`).
12. `SessionsToolbar.classic` (`SessionsToolbar.java:183-189`) is a LABEL "Session:", a DROPDOWN without a face and a PRIMARY "Connect". Remote is `version = "0.13.0"` with `sdk = ">=0.18.0, <0.19"`. `ClassicCompositionRenderTest:84-99` measures the session toolbar against the Workstation mocks (bands at x 130, floor 0.50).
13. Flush frames mark the frame holding the permanent focus owner (`ToolWindowArea.java:431-449`; `Host.focused` at `:85-89`). Nothing reacts to a mouse press. `WindowContributions.togglePanel` (`WindowContributions.java:192`) only toggles. `FlushFramesTest.area(boolean)` and `DockTestSupport.show(area, slot, content, size)` (id `DockTestSupport.id(slot)`) are the fixtures.
14. `ChromePainter.FOCUSED_HEADER = PaintPair(SURFACE_HEADER_ACTIVE, SURFACE_HEADER, Guard.beyond(1))` (`ChromePainter.java:316`). #DFE2E9 against the port's Light `surface.header` #F2F2F2 is about 5.9 L* apart (contrast 1.158), so the guard holds and #DFE2E9 paints as authored.
15. `CornerHome.home` (`CornerHome.java:16-25`) places the window at the bottom-left of the work area by `RUN_WINDOW_HOME_SIDE` and `_BOTTOM` (`DesignMetrics.java:155`). These two roles are read only there and in tests (`CornerHomeTest`, `RunWindowWorkspaceRenderTest`, `RunWindowRolesTest:38-41`, both role tables). `PaletteLook.Home { TITLE_FIELD, CORNER }` (`PaletteLook.java:15`, `RUN_WINDOW` at `:23`). The `migration.csv` rows are at `:1402-1403`.
16. The converter `tools/themes/jasper_theme.py`:
    - `overrides()` (`:977-989`) fills an override key only from keys the theme or a parent in its plugin sets, never from IntelliJ's own themes or `*.` wildcards. `OVERRIDES` (`:966-974`) maps the seven IntelliJ keys.
    - `convert_intellij_theme(theme, siblings, name, scheme_file, loose_dir, roles)` (`:992`) takes chosen `roles` (hex or a palette colour name). `Lookup.key` (`:648-663`) resolves exact key, then the longest `*.` wildcard, level by level. `validate` checks the role and override order.
    - The vendored inputs are `tools/themes/intellij/` at commit `f7377708` (`SOURCE.md`, `assets.tsv`). At `8062c061ff33` (tag idea/2026.2.3):
      - five of the seven inputs this plan reads are byte-identical: `Light.theme.json`, `intellijlaf.theme.json`, `darcula.theme.json`, `Light.xml` and `DefaultColorSchemesManager.xml`;
      - `expUI/expUI_dark.theme.json` and `expUI/expUI_darkScheme.xml` differ only in `DragAndDrop.hintBackground` and three `MARKDOWN_*` options, and converting the 8062c061 copies gives byte-identical output (checked 2026-10-18).
    - "IntelliJ Light" is `Light.theme.json` (parent `IntelliJ` = `intellijlaf.theme.json`, editor scheme `IntelliJ Light` = `Light.xml`). The New UI "Dark" is `expUI/expUI_dark.theme.json` (parent Darcula, scheme `Dark` = `expUI/expUI_darkScheme.xml`).
17. IntelliJ values at those files:
    - IntelliJ Light: `ToolWindow.Header.background` #E2E6EC, `inactiveBackground` `panel` = #F2F2F2. `Button.default.startBackground` #528CC7 (white on it 3.54:1). `List.selectionBackground` #2675BF (4.80:1). `contentBackground` #FFFFFF.
    - New UI Dark: no exact `ToolWindow.Header.background` (only `*.background` = Gray2 #2B2D30). `Button.default.startBackground` Blue6 #3574F0 (4.28:1). Blue5 #366ACE (5.11:1).
18. `test_jasper_theme.py:934-951` compares the converter's output with the built-in Catppuccin files byte for byte, rewriting them under `JASPER_THEME_UPDATE_GOLDEN=1`. Gradle's `gradle/theme-tool.gradle.kts` runs these tests in `check`.
19. `notices.toml:91-97` has "IntelliJ Platform icons" (Apache-2.0, text `/dev/jasper/ui/icons/LICENSE.txt`, notice `/dev/jasper/ui/icons/NOTICE.txt`). `LICENSE.txt` is the standard Apache text (SHA-256 `cfc7749b…`, the same file as `tools/themes/intellij/LICENSE.txt`). `NOTICE.txt` is upstream's three lines. `ThirdPartyNotices.Component` has no description field: `name`, `version`, `license`, `homepage`, `modules`, `text`, `notice`.
20. `tools/visual/check_pairs.py` passes a pair when every colour in `"colours"` is present in the mock crop (ΔE76 ≤ 6) and in the render (ΔE ≤ 1.5). The 101 gated pairs are all Enterprise renders.
21. `DerivedRolesTableTest` writes the actual table to `jasper-ui/build/derived-roles/derived-roles-enterprise.tsv`. `KitFixtures.enterpriseLight()`/`enterpriseDark()` and `EnterpriseLook.theme(dark)` read the shipped `enterprise.json`.
22. The 14 classic files this plan vendors were downloaded from `https://raw.githubusercontent.com/JetBrains/intellij-community/8062c061ff33e22041d9f1844db5b8b8a6b5e4d7/<path>` on 2026-10-18. Each starts with its `<!-- Copyright 2000-2021 JetBrains s.r.o. … Apache 2.0 license … -->` comment and uses only `svg`, `g`, `path`, `polygon` and `rect` (`SvgPreflight` admits them). Their SHA-256 values are in Task 1.

## Rulings

Decisions the user left open. Each reads "what — why — cost if wrong". Task 12 records them in `docs/STATUS.md`.

1. **One plan, twelve tasks.** The work fits under the "about 14 tasks" bar, and Task 12 is the single docs-and-check pass — why: a split would need two status entries and two whole builds for one user request — cost if wrong: none.
2. **The classic set is 14 unmodified files under `dev/jasper/ui/icons/classic/<platform/icons/src path>`.** A file without a `_dark` sibling (`expandComponent`, `application`) serves both variants — why: recolouring would modify the file under Apache-2.0 section 4(b), and IntelliJ itself shows these mid-tones on dark — cost if wrong: two icons a little dim in Dark.
3. **The classic chrome set covers `add`, `split`, `zoom`, `settings`, `window.add` and `search`.** The sources are `general/add`, `actions/splitVertically`, `general/expandComponent`, `general/settings`, `actions/moveToWindow` and `actions/find`. Any other chrome name falls back to the New UI chrome artwork, then the catalog — why: these are the names the classic header (and a unified header under the set) shows; IntelliJ has no classic counterpart in the research for the rest — cost if wrong: a status-bar glyph stays New UI.
4. **`run` and `application` are authored catalog names in both looks.** `run` is `actions/execute` with its dark sibling; `application` is `runConfigurations/application`. Both have Jasper vector fallbacks for panel, prompt and tile contexts — why: kit views resolve names in their own registry and never see chrome artwork, so a toolbar view reaches classic art only through authored names — cost if wrong: a default-look plugin using `run` gets the green triangle.
5. **One About entry for both icon sets.** The component is renamed "IntelliJ Platform icons (New UI and Classic)". Its notice becomes a new `ATTRIBUTION.txt` that names both source trees and pins, then quotes the upstream NOTICE verbatim. `LICENSE.txt` and `NOTICE.txt` stay as they are — why: the existing component already covers JetBrains' Apache artwork in jasper-ui, and the notice field holds one file — cost if wrong: one more component if the user wants them apart.
6. **`[chrome] icons = "new_ui" | "classic"` projects onto `WorkspaceLook.Icons`.** `WorkspaceChrome` maps it to jasper-ui's `ChromeArtwork` and marks each window's header and status bar with it. The default look's icon rails keep New UI — why: the receivers are per window, so a process-wide switch is not needed and the projection keeps one reader — cost if wrong: a rails-plus-classic look shows New UI rail icons.
7. **Classic header buttons.**
   - `icons`: 22 px squares (`CHROME_ICON_BUTTON`). Split marks its menu with a 4 px ▼ in its bottom-right corner (`CHROME_ICON_CARET`), as IntelliJ's ActionButton marks a popup group.
   - `icons_and_labels`: the icon, then the label (Split ending in ▾). The drawn "+" goes with `Glyph`.
   - Tooltips stay the labels.
   - why: the user's screenshot and the approved design B — cost if wrong: the 06 mocks' text-first row is gone (a user-directed deviation).
8. **`window.toolbar` joins spec 5.8's allowlist (five keys), and Enterprise defaults it to `icons`** — why: design B; a saved choice still wins — cost if wrong: none.
9. **SDK 0.18.1 admits any number of icon-only GHOST Buttons in a toolbar view.** An icon-only button has a blank label, an icon and a tooltip. A labelled GHOST is refused, and the one-PRIMARY rule stands — why: IntelliJ's toolbar actions are icon buttons; an unlimited count needs no cap the user did not ask for — cost if wrong: a plugin could crowd its view.
10. **The toolbar combo is IntelliJ's `ComboBoxButton`.** It is drawn only for a rich DROPDOWN (a `PickerFace`) in a toolbar strip under the classic buttons face:
    - 24 tall, with a white `surface.input` box inset 1 and a `border.control` ring of radius 3;
    - the icon 10 from the edge, a 4 gap, the name, 4, a 16 px arrow box with IntelliJ's 9 × 5 triangle in `text.secondary`, then 7;
    - the name capped at 240 with an ellipsis;
    - no hover change, as IntelliJ's.

    Every other dropdown and picker face is unchanged — why: research "comboLook", and design C limits the face to toolbar strips — cost if wrong: one face path to adjust.
11. **An icon-only GHOST button in a toolbar strip draws its glyph at `ICON_RAIL` (16) in a `BUTTON_HEIGHT` square (24 under classic; IntelliJ's is 22)** — why: it matches the 24 px combo beside it and needs no new button size — cost if wrong: a 2 px residual.
12. **Remote's classic view.**
    - No "Session:" label.
    - The DROPDOWN carries `PickerFace(<chosen row's label>, "", "application", NONE, COMPACT)`, and the options keep no icons.
    - Connect becomes `Button(CONNECT, state, "", "run", GHOST, CONNECT, "Connect")`.
    - Remote becomes 0.14.0 with `sdk >=0.18.1`, and Enterprise becomes 0.2.0, since its kit and theme change.

    why: design C — cost if wrong: popup rows without icons (IntelliJ shows them).
13. **The port reads the vendored converter inputs (`f7377708`).** Five are byte-identical at the pinned `8062c061ff33`, and the two that differ convert identically (fact 16). `tools/themes/enterprise/SOURCE.md` and the notice record both commits — why: no second copy of IntelliJ's files — cost if wrong: none (the output is identical).
14. **Port override rule.** An override key is filled when IntelliJ's own theme sets its key exactly, not through a `*.` wildcard. port.json's `overrides` then replace values. Light's `surface.header.active` is the user's #DFE2E9 (IntelliJ Light's is #E2E6EC). New UI Dark sets no exact header key, so Dark's focused header keeps its recipe — why: it is faithful to what each IntelliJ theme paints, and it keeps a tint in Dark — cost if wrong: Dark's tint differs from a hand-picked one.
15. **Port role choices.**
    - Light: `accent` #2675BF (IntelliJ Light's `List.selectionBackground`) and `surface.card` `contentBackground` (#FFFFFF, IntelliJ's tree and table ground).
    - Dark: `accent` `Blue5` #366ACE.

    This supersedes plan 5b's #3C74D6 ruling — why: IntelliJ's default-button blues (3.54:1 and 4.28:1 under white) would make the TEXT guard paint every label on the accent black, as 5b found — cost if wrong: primary buttons a shade darker than IntelliJ's.
16. **The theme keeps two schemes, `light` and `dark`.** `dark-terminal` is dropped. The author reads "JetBrains, ported by Jasper" — why: one IntelliJ console scheme per variant; MVP rule — cost if wrong: a saved `enterprise/dark-terminal` falls back with the unknown-scheme warning.
17. **`FOCUSED_HEADER`'s guard is unchanged** — why: with the port, #DFE2E9 is 5.9 L* from #F2F2F2 and paints exactly (fact 14), and Catppuccin keeps spec 10.7's guarantee — cost if wrong: none; `EnterpriseThemeTest` pins the exact colour.
18. **Activation follows IntelliJ.**
    - Under flush frames a `Toolkit` `AWTEventListener` sees every `MOUSE_PRESSED`. A press inside a frame activates it. After the press is dispatched, if the keyboard is not already inside the frame, it moves to the frame's first showing, enabled, focusable descendant with `WHEN_FOCUSED` key bindings, else to the frame itself (made focusable under flush frames).
    - A press elsewhere in the tool window area clears the tint.
    - Opening a tool window from its stripe or its toggle action activates it too.

    why: research "headerFocus" — cost if wrong: a click into a panel now takes the keys from the terminal, which is IntelliJ's behaviour.
19. **`UpperHome` replaces `CornerHome` (`PaletteLook.Home.UPPER`).**
    - The window is centred across the workspace, with its top a seventh of the workspace height down.
    - It moves up to end at the workspace's middle while that keeps it in the work area, and is clamped into the work area with the margin.
    - It re-attaches when dropped within reach of home in both axes.
    - The saved `[palette.run_window] left/bottom` keys are unchanged, and `RUN_WINDOW_HOME_SIDE`/`_BOTTOM` go.

    why: the user's "centred horizontally and in the upper half", near where IntelliJ's Search Everywhere and Run Anything open — cost if wrong: a different fraction.
20. **Colours are checked in the render only.** Enterprise tests read every colour target from the shipped theme's roles or painted pairs, and `check_pairs.py` looks for `"colours"` in the render only — why: the user's 2026-10-18 direction makes IntelliJ's themes, not the mocks, the colour authority, while the mocks keep the layout — cost if wrong: a role regression is still caught; a mock colour mismatch no longer fails.

---

### Task 1: Classic artwork in jasper-ui

**Files:**
- Create: `jasper-ui/src/main/resources/dev/jasper/ui/icons/assets-classic.tsv`, `tools/icons/fetch-classic-icons.py`, the 14 SVGs under `jasper-ui/src/main/resources/dev/jasper/ui/icons/classic/` (fetched), `jasper-ui/src/main/resources/dev/jasper/ui/icons/ATTRIBUTION.txt`, `jasper-ui/src/main/java/dev/jasper/ui/icons/ChromeArtwork.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/icons/BuiltInArtwork.java`, `IconRegistry.java`, `jasper-ui/src/main/resources/dev/jasper/ui/icons/SOURCE.md`, `jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/icons/BuiltInArtworkTest.java`, `jasper-app/src/test/java/dev/jasper/app/about/ThirdPartyNoticesTest.java`

**Interfaces:**
- Produces:
  - `public enum ChromeArtwork { NEW_UI, CLASSIC }` (`dev.jasper.ui.icons`);
  - `public PreparedIcon IconRegistry.chromeIcon(String name, int logicalSize, ChromeArtwork artwork)`, with `chromeIcon(name, size)` meaning `chromeIcon(name, size, ChromeArtwork.NEW_UI)`;
  - `static Map<String, Pair> BuiltInArtwork.classicChrome()`;
  - the built-in names `run` and `application` (authored catalog, with vector fallbacks).

- [ ] **Step 1: Write the manifest**

```bash
python3 -I - <<'PY'
rows = [
 ("general/add.svg", "2de42dc3bcde96aba2039fe94b25f3215139440ee65f8b8d2b1e5edc3af4a1ca"),
 ("general/add_dark.svg", "c3f09e8fe207eee1a2705e3ec45fc6953cc62ec6a59f31e2a0bac2f72eee3d3c"),
 ("actions/splitVertically.svg", "db06a4612064c449ffc3e63ed6ae746147bb584b153c13f43b5ec8e24ceb60c6"),
 ("actions/splitVertically_dark.svg", "19c0b44796429008f2c8928a893d7de4022f7e58d730e3b3f673645ec6d9f82d"),
 ("general/expandComponent.svg", "5561f884f6816a62e7a83c5fbd9d337c6632690fbc6f16eed07014ccb0ab90fd"),
 ("general/settings.svg", "0dc53b03ab480453162f92c9e31e53ba927924f265e9e40235548015165240b2"),
 ("general/settings_dark.svg", "9c76f148dabfe7b24aeff74ac58d1ea171f94a9c54a27ae85f34e78ec00a20d0"),
 ("actions/moveToWindow.svg", "4fd6e42111511fd5b10ccd09eb4049c95d178c2a367df6cc14b2b3b923536548"),
 ("actions/moveToWindow_dark.svg", "8e009fd80e00d24d52f9f047bd429d07a26ac2a423d25b43c4a811dd6baf580e"),
 ("actions/find.svg", "3e8e0fc19caad9a75fbcd47155c459704141745a059a7ff0a348b0a02973b346"),
 ("actions/find_dark.svg", "59ad6295f98567ce62359f6a4a8136c90807c6641c8b9ca12e97d64f17ec37b9"),
 ("actions/execute.svg", "415c8a0a77f473cc4e4ccbe80ac0409938abae512bdeea1839e25b5a3f55dba2"),
 ("actions/execute_dark.svg", "fb67af57f884713f96bdb6be9e6098ad6d2bc71834b1229c534cbcd44161af36"),
 ("runConfigurations/application.svg", "ab96cef5c23829096e126538ec37875dc24e0b6eaef8d45afde812b6a1809889"),
]
import pathlib
out = pathlib.Path("jasper-ui/src/main/resources/dev/jasper/ui/icons/assets-classic.tsv")
out.write_text("".join(f"classic/{p}\tplatform/icons/src/{p}\t{h}\n" for p, h in rows), encoding="utf-8")
print(len(rows))
PY
```

Expected: `14`. The format is `assets-chrome.tsv`'s: resource, upstream path, SHA-256, with no header line.

- [ ] **Step 2: Write `tools/icons/fetch-classic-icons.py`**

```python
#!/usr/bin/env python3
"""Fetch the IntelliJ classic icons Jasper vendors from JetBrains/intellij-community at a pinned commit.

    python3 -I tools/icons/fetch-classic-icons.py

Reads jasper-ui's assets-classic.tsv (resource, upstream path, SHA-256), downloads every file with curl into memory,
checks each SHA-256 and only then writes them, byte for byte, so each keeps its JetBrains copyright comment. Builds and
runtime never run this; it reproduces the vendoring. Apache-2.0: the shipped licence is icons/LICENSE.txt, never the
repository's root LICENSE.txt.
"""
import hashlib
import pathlib
import subprocess
import sys

COMMIT = "8062c061ff33e22041d9f1844db5b8b8a6b5e4d7"  # tag idea/2026.2.3
BASE = f"https://raw.githubusercontent.com/JetBrains/intellij-community/{COMMIT}/"
ICONS = pathlib.Path(__file__).resolve().parents[2] / "jasper-ui/src/main/resources/dev/jasper/ui/icons"


def main() -> int:
    rows = [line.split("\t") for line in (ICONS / "assets-classic.tsv").read_text(encoding="utf-8").splitlines()]
    staged = {}
    for resource, source, expected in rows:
        data = subprocess.run(["curl", "-fsSL", BASE + source], capture_output=True, check=True).stdout
        actual = hashlib.sha256(data).hexdigest()
        if actual != expected:
            print(f"{source}: SHA-256 {actual}, the manifest records {expected}", file=sys.stderr)
            return 1
        staged[resource] = data
    for resource, data in staged.items():
        target = ICONS / resource
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    print(f"{len(staged)} files verified and written")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 3: Fetch**

Run: `python3 -I tools/icons/fetch-classic-icons.py`
Expected: `14 files verified and written`, with `git status --short jasper-ui/src/main/resources/dev/jasper/ui/icons/classic` listing 14 new files.

- [ ] **Step 4: Write the failing tests**

In `BuiltInArtworkTest`:
- change `hasSize(7)` (`:19`) to `hasSize(9)`;
- add the helper `static int[] direct(String file, int size, int scale)`, the jsvg render the existing tests inline (`SVGLoader().load(...)` into a `size*scale` square), extracted so the new tests share it;
- add:

```java
    /** Rulings 3: the classic chrome set, its six meanings, and only CLASSIC receivers get it; others fall back to New UI. */
    @Test void theClassicChromeSetIsTheVendoredArtworkForItsSixMeanings() throws Exception {
        var files = Map.of("add", "classic/general/add", "split", "classic/actions/splitVertically", "zoom", "classic/general/expandComponent",
            "settings", "classic/general/settings", "window.add", "classic/actions/moveToWindow", "search", "classic/actions/find");
        assertThat(BuiltInArtwork.classicChrome()).containsOnlyKeys(files.keySet());
        try (var registry = new IconRegistry()) {
            for (var entry : files.entrySet()) for (var theme : List.of(KitFixtures.light(), KitFixtures.dark())) {
                boolean single = entry.getKey().equals("zoom");
                String file = entry.getValue() + (theme.dark() && !single ? "_dark.svg" : ".svg");
                for (int scale : new int[] {1, 2}) {
                    var classic = pixels(registry.chromeIcon(entry.getKey(), 16, ChromeArtwork.CLASSIC).icon(theme, SemanticColor.TEXT_PRIMARY), scale);
                    assertThat(classic).as("%s at %s", file, scale).isEqualTo(direct(file, 16, scale));
                    assertThat(pixels(registry.chromeIcon(entry.getKey(), 16, ChromeArtwork.NEW_UI).icon(theme, SemanticColor.TEXT_PRIMARY), scale))
                        .as("New UI %s", entry.getKey()).isNotEqualTo(classic);
                }
            }
            for (String name : List.of("folder", "bell", "terminal", "copy", "more.vertical"))
                assertThat(pixels(registry.chromeIcon(name, 16, ChromeArtwork.CLASSIC).icon(KitFixtures.dark(), SemanticColor.TEXT_PRIMARY), 2))
                    .as("%s falls back", name)
                    .isEqualTo(pixels(registry.chromeIcon(name, 16).icon(KitFixtures.dark(), SemanticColor.TEXT_PRIMARY), 2));
        }
    }

    /** Global Constraints: byte-identical upstream files, each with its JetBrains copyright comment. */
    @Test void everyClassicFileIsTheRecordedUpstreamFileWithItsCopyright() throws Exception {
        List<String> lines;
        try (var in = getClass().getResourceAsStream("assets-classic.tsv")) {
            lines = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        }
        assertThat(lines).hasSize(14);
        for (String line : lines) {
            String[] columns = line.split("\t");
            assertThat(columns[1]).startsWith("platform/icons/src/").endsWith(columns[0].substring("classic/".length()));
            byte[] bytes;
            try (var in = getClass().getResourceAsStream(columns[0])) { bytes = in.readAllBytes(); }
            assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))).as(columns[0]).isEqualTo(columns[2]);
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            assertThat(text).as(columns[0]).startsWith("<!-- Copyright 2000-").contains("JetBrains s.r.o.").contains("Apache 2.0");
        }
    }

    /** Rulings 4: run and application are authored catalog artwork with vector fallbacks for every other context. */
    @Test void runAndApplicationAreAuthoredClassicArtworkWithVectorFallbacks() throws Exception {
        try (var registry = new IconRegistry()) {
            assertThat(registry.names()).contains("run", "application");
            assertThat(registry.authored("run")).isTrue();
            assertThat(registry.authored("application")).isTrue();
            for (var theme : List.of(KitFixtures.light(), KitFixtures.dark())) {
                assertThat(pixels(registry.icon("run", theme, SemanticColor.TEXT_PRIMARY, 16), 2))
                    .isEqualTo(direct(theme.dark() ? "classic/actions/execute_dark.svg" : "classic/actions/execute.svg", 16, 2));
                assertThat(pixels(registry.icon("application", theme, SemanticColor.TEXT_PRIMARY, 16), 2))
                    .isEqualTo(direct("classic/runConfigurations/application.svg", 16, 2));
            }
            for (String name : List.of("run", "application")) assertThatCode(() -> {
                registry.panel(name, KitFixtures.light().semantic(), SemanticColor.TEXT_PRIMARY, 16);
                registry.prompt(name, KitFixtures.light().semantic(), Nodes.Hue.NONE, 24);
                registry.tile(dev.jasper.ui.metrics.DesignMetrics.DEFAULT, name, KitFixtures.light().semantic(), Nodes.Hue.GREEN, 28, false);
            }).as(name).doesNotThrowAnyException();
        }
    }
```

In `ThirdPartyNoticesTest`:

```java
    /** Plan 12 Rulings 5: one component names both IntelliJ icon sets, ships the Apache text and keeps upstream's NOTICE. */
    @Test void theIntelliJIconsComponentNamesBothSetsAndKeepsTheUpstreamNotice() {
        var icons = ThirdPartyNotices.thirdParty().stream().filter(c -> c.name().startsWith("IntelliJ Platform icons")).toList();
        assertThat(icons).singleElement().satisfies(c -> {
            assertThat(c.name()).isEqualTo("IntelliJ Platform icons (New UI and Classic)");
            assertThat(c.license()).isEqualTo("Apache-2.0");
            assertThat(ThirdPartyNotices.read(c.text())).contains("Apache License").contains("Version 2.0, January 2004")
                .doesNotContain("OPEN-SOURCE BUILD TERMS");
            assertThat(ThirdPartyNotices.read(c.notice().orElseThrow()))
                .contains("8062c061ff33e22041d9f1844db5b8b8a6b5e4d7", "60b8fbadc2f08ef18a6ecab0c138a849e14f13fb")
                .contains("This software includes code from IntelliJ IDEA\nCopyright (C) JetBrains s.r.o.\nhttps://www.jetbrains.com/idea/");
        });
    }
```

- [ ] **Step 5: Run them to see them fail**

Run: `./gradlew :jasper-ui:test --tests '*BuiltInArtworkTest' :jasper-app:test --tests '*ThirdPartyNoticesTest'`
Expected: compilation fails (no `ChromeArtwork`, no three-argument `chromeIcon`).

- [ ] **Step 6: Implement**

- `ChromeArtwork.java`: `public enum ChromeArtwork { NEW_UI, CLASSIC }`. The class comment reads: "The artwork the native workspace chrome's receivers draw (Enterprise presentation spec 5.2's `[chrome] icons`): IntelliJ's New UI originals, or its classic icons where the classic set has the meaning." Each constant gets a one-line comment.
- `BuiltInArtwork`:
  - add `Hashes.CLASSIC = hashes("assets-classic.tsv", 14)` and `private static Pair single(String stem, Map<String,String> hashes)`, a `Pair` of the same decoded file twice;
  - add `"run", pair("classic/actions/execute", Hashes.CLASSIC)` and `"application", single("classic/runConfigurations/application", Hashes.CLASSIC)` to `CATALOG`;
  - add a `ClassicHolder.CLASSIC` map of Rulings 3's six names (`zoom` through `single`) and `static Map<String, Pair> classicChrome()`. Its comment says the map is admitted only by `IconRegistry.chromeIcon` with `ChromeArtwork.CLASSIC`.
- `IconRegistry`:
  - `prepareBuiltIns()` also calls `BuiltInArtwork.classicChrome()`;
  - `chromeIcon(name, size)` delegates to `chromeIcon(name, size, ChromeArtwork.NEW_UI)`;
  - `prepared(name, size, ChromeArtwork artwork)` takes null for `themedIcon` and picks, in order, the classic pair (CLASSIC, when present), then the chrome pair, then the catalog pair;
  - `builtIns()` gains `Map.entry("run", "M 4.5 2.5 L 13.5 8 L 4.5 13.5 Z")` and `Map.entry("application", "M 1.5 2.5 H 14.5 V 13.5 H 1.5 Z M 1.5 5.5 H 14.5")` (Jasper originals), and `FILLED_BUILT_INS` gains `"run"`.
- `ATTRIBUTION.txt`, exactly:

```text
IntelliJ Platform icons, New UI and Classic
Copyright 2000-2026 JetBrains s.r.o. and contributors.
Licensed under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
New UI icons: https://github.com/JetBrains/intellij-community at commit 60b8fbadc2f08ef18a6ecab0c138a849e14f13fb,
files from platform/icons/src/expui/ and plugins/terminal/resources/icons/expui/.
Classic icons: https://github.com/JetBrains/intellij-community at tag idea/2026.2.3 (commit
8062c061ff33e22041d9f1844db5b8b8a6b5e4d7), files from platform/icons/src/.
The files are unmodified; each keeps its JetBrains copyright notice.

NOTICE (from the upstream NOTICE.txt):
This software includes code from IntelliJ IDEA
Copyright (C) JetBrains s.r.o.
https://www.jetbrains.com/idea/
```

- `notices.toml` (`:91-97`): `name = "IntelliJ Platform icons (New UI and Classic)"` and `notice = "/dev/jasper/ui/icons/ATTRIBUTION.txt"`.
- `SOURCE.md`: append a section `# IntelliJ classic artwork`. It names the 14 files of `assets-classic.tsv` under `classic/` and the commit and tag. It states that they are byte-identical with their copyright comments and that `tools/icons/fetch-classic-icons.py` reproduces them. It states that a file without `_dark` serves both variants, and that the chrome receivers marked `ChromeArtwork.CLASSIC` get six of them while the catalog's `run` and `application` get the rest.

- [ ] **Step 7: Run the tests**

Run: `./gradlew :jasper-ui:test --tests '*BuiltInArtworkTest' --tests '*IconRegistryTest' :jasper-app:test --tests '*ThirdPartyNoticesTest' --tests '*LicensesViewTest' :jasper-app:verifyThirdPartyNotices`
Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add tools/icons/fetch-classic-icons.py jasper-ui/src/main/resources/dev/jasper/ui/icons jasper-ui/src/main/java/dev/jasper/ui/icons \
  jasper-ui/src/test/java/dev/jasper/ui/icons/BuiltInArtworkTest.java jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml \
  jasper-app/src/test/java/dev/jasper/app/about/ThirdPartyNoticesTest.java
git commit -m "Icons: vendor IntelliJ's classic toolbar icons, run and application

Fourteen unmodified SVGs from intellij-community at idea/2026.2.3, checked
against assets-classic.tsv by a reproducible curl fetch; a CLASSIC chrome tier
for add, split, zoom, settings, window.add and search; authored run and
application catalog names; the About entry names both icon sets.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 2: The `[chrome] icons` point and the chrome receivers

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/presentation/ChromeSpec.java`, `KitParser.java`, `WorkspaceLook.java`, `Projections.java`; `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceChrome.java`, `WindowChrome.java`; `jasper-app/src/main/java/dev/jasper/app/platform/AppIcons.java`; `plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml`, `plugins/enterprise/src/main/resources/plugin.toml` (version `0.2.0`)
- Modify (one argument added): every `new ChromeSpec(` and `new WorkspaceLook(` site of Verified facts 8
- Test: `KitParserTest`, `ProjectionsTest`, `ProjectionReadTest`, `PresentationConformanceTest`, `jasper-app/src/test/java/dev/jasper/app/platform/ZoneIconsTest.java`, create `jasper-app/src/test/java/dev/jasper/app/workspace/ChromeArtworkTest.java`

**Interfaces:**
- Consumes: `ChromeArtwork`, `IconRegistry.chromeIcon(name, size, artwork)` (Task 1).
- Produces:
  - `ChromeSpec.Icons { NEW_UI, CLASSIC }` as the record's last component `icons` (DEFAULT `NEW_UI`, CLASSIC `CLASSIC`);
  - `WorkspaceLook.Icons { NEW_UI, CLASSIC }` as the last component `icons`;
  - `ChromeArtwork WorkspaceChrome.chromeArtwork()` (package-private);
  - `public static void AppIcons.chromeReceiver(JComponent, ChromeArtwork)`;
  - `public static Optional<ChromeArtwork> AppIcons.chromeArtwork(Component)`, the nearest receiver's mark;
  - `chromeReceiver(JComponent)` meaning `NEW_UI`.

- [ ] **Step 1: Write the failing tests**

`KitParserTest`:

```java
    /** Plan 12: [chrome] icons, by base and both values. */
    @Test void theIconsPointDefaultsByBaseAndTakesBothValues() throws Exception {
        assertThat(parse("kit = 1\nbase = \"default\"\n").chrome().icons()).isEqualTo(ChromeSpec.Icons.NEW_UI);
        assertThat(parse("kit = 1\nbase = \"classic\"\n").chrome().icons()).isEqualTo(ChromeSpec.Icons.CLASSIC);
        assertThat(parse("kit = 1\nbase = \"default\"\n[chrome]\nicons = \"classic\"\n").chrome().icons()).isEqualTo(ChromeSpec.Icons.CLASSIC);
        assertThat(parse("kit = 1\nbase = \"classic\"\n[chrome]\nicons = \"new_ui\"\n").chrome().icons()).isEqualTo(ChromeSpec.Icons.NEW_UI);
        assertThat(problem("kit = 1\nbase = \"default\"\n[chrome]\nicons = \"retro\"\n"))
            .isEqualTo("[chrome].icons: \"retro\" is not one of new_ui, classic");
    }
```

`ProjectionsTest`: `theIconsPointIsTheWorkspaceLooksIcons`. It asserts that `Projections.of(ChromeSpec.CLASSIC, true, false, "p", MAC).workspace().icons()` is `CLASSIC`, that `Projections.standard(MAC).workspace().icons()` is `NEW_UI`, and that `Projections.fallback(committed, MAC).workspace().icons()` is `NEW_UI` for both committed flags.

`ProjectionReadTest`: add `Icons` to the WorkspaceLook pattern's alternation, a field `icons` of `WorkspaceLook$Icons` to `aLookReadElsewhereIsReported`'s probe, and change `hasSize(5)` to `hasSize(6)`.

`PresentationConformanceTest`: `KEYS` gains `"chrome/icons"`; `theDescriptorIsSpecFourOnes` expects version `0.2.0`; `theKitListsEveryKeyAndParsesWithoutAProblem` adds `assertThat(parsed.chrome().icons()).isEqualTo(ChromeSpec.Icons.CLASSIC);`.

`ChromeArtworkTest` (workspace package):

```java
/** Plan 12 Rulings 6: the look's icon set reaches each window's header and status bar, and only through WorkspaceChrome. */
class ChromeArtworkTest {
    static WorkspaceLook look(WorkspaceLook.Icons icons) {
        return new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.CLASSIC, WorkspaceLook.Stripes.RAILS,
            WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.RULED, false, WorkspaceLook.FrameStyle.STANDARD, icons);
    }

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    @Test void aClassicLookMarksItsHeaderAndStatusBarForTheClassicSet() throws Exception {
        DesktopTestSupport.edt(() -> {
            for (var icons : WorkspaceLook.Icons.values()) {
                var chrome = WorkspaceChrome.of(look(icons), new TitleFormat("{context}"));
                var expected = icons == WorkspaceLook.Icons.CLASSIC ? ChromeArtwork.CLASSIC : ChromeArtwork.NEW_UI;
                assertThat(chrome.chromeArtwork()).isEqualTo(expected);
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), chrome);
                assertThat(AppIcons.chromeArtwork(owner.chrome().header())).contains(expected);
                assertThat(AppIcons.chromeArtwork(owner.chrome().statusBar())).contains(expected);
                assertThat(AppIcons.chromeArtwork(owner.chrome().windowToolbar())).as("inside the header").contains(expected);
            }
            assertThat(WorkspaceChrome.of(look(WorkspaceLook.Icons.CLASSIC), new TitleFormat("{context}")))
                .isNotEqualTo(WorkspaceChrome.of(look(WorkspaceLook.Icons.NEW_UI), new TitleFormat("{context}")));
        });
    }
}
```

(Use the accessor `WindowChrome` already has for its status bar; if it has none, add a package-private `WindowStatusBar statusBar()`.)

`ZoneIconsTest`: `aClassicReceiverPaintsTheClassicSet`, modelled on `chromeReceiversAloneShowOriginalArtworkWithoutChangingIconValues`. It paints `AppIcons.icon("square-plus")` inside a panel marked `chromeReceiver(panel, CLASSIC)`, inside one marked plain, and outside any. It asserts the pixels equal `new IconRegistry().chromeIcon("add", 16, CLASSIC)`, then `chromeIcon("add", 16)`, then `themedIcon("add", 16)`, each drawn for the same theme and tint.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests '*KitParserTest' --tests '*ProjectionsTest' --tests '*ProjectionReadTest' --tests '*PresentationConformanceTest' --tests '*ZoneIconsTest' --tests '*ChromeArtworkTest'`
Expected: compilation fails (`ChromeSpec.Icons`, `WorkspaceLook.Icons`, `chromeArtwork` are undefined).

- [ ] **Step 3: Implement**

- `ChromeSpec`:
  - add `/** IntelliJ's New UI chrome icons, or its classic ones. */ public enum Icons { NEW_UI, CLASSIC }`;
  - add the component `Icons icons` last, with `@param icons {@code icons}` and a `requireNonNull`;
  - give `DEFAULT` `Icons.NEW_UI` and `CLASSIC` `Icons.CLASSIC`;
  - change the class comment to "The ten chrome variant points … nine two-valued points and the window title template".
- `KitParser`: `CHROME` gains `"icons"`, and `chrome()` reads `point(table, "chrome", "icons", ChromeSpec.Icons.class, base.icons())`.
- `WorkspaceLook`: add `/** IntelliJ's New UI chrome icons, or its classic ones (spec 5.2's [chrome] icons). */ public enum Icons { NEW_UI, CLASSIC }` and the component `Icons icons` last (`@param icons the workspace chrome's icon set`).
- `Projections.of`: `chrome.icons() == ChromeSpec.Icons.CLASSIC ? WorkspaceLook.Icons.CLASSIC : WorkspaceLook.Icons.NEW_UI`; `fallback`: `NEW_UI`.
- `WorkspaceChrome`:
  - add a field `private final ChromeArtwork artwork`, read once in `of` with `switch (look.icons()) { case NEW_UI -> ChromeArtwork.NEW_UI; case CLASSIC -> ChromeArtwork.CLASSIC; }`;
  - `STANDARD` takes `NEW_UI`;
  - add the package-private `chromeArtwork()`;
  - include the field in `equals` and `hashCode`;
  - `statusBar(...)` calls `AppIcons.chromeReceiver(bar, artwork)` on the bar it returns.
- `WindowChrome` (after `header = new WindowHeader(...)`, `:73`): `AppIcons.chromeReceiver(header, owner.look().chromeArtwork());`.
- `AppIcons`:
  - the property holds the `ChromeArtwork`;
  - `chromeReceiver(c)` is `chromeReceiver(c, ChromeArtwork.NEW_UI)`;
  - `chromeArtwork(Component)` walks the parents for the first mark;
  - `NativeSnapshot` gains `classic` (`NAMED.chromeIcon(name, size, ChromeArtwork.CLASSIC)`);
  - `paint` picks `artwork` when unmarked, `chrome` for `NEW_UI` and `classic` for `CLASSIC`;
  - `inChrome` is replaced by `chromeArtwork(c).isPresent()` at its callers.
- `enterprise.kit.toml` `[chrome]`: add `icons = "classic"` after `motion`. `plugin.toml`: `version = "0.2.0"`.
- Add `WorkspaceLook.Icons.NEW_UI` (or `CLASSIC` where the test models the Enterprise look) and `ChromeSpec.Icons.NEW_UI` (or `CLASSIC`) as the new last argument at every site of Verified facts 8. For `testsupport/EnterpriseLook.CHROME`, `ChromeRenderTest` and the Enterprise-shaped `ProjectionsTest` expectations this is CLASSIC. Find them with `grep -rln "new WorkspaceLook(\|new ChromeSpec(" --include='*.java' jasper-app/src`.
- `migration.csv`: add a row for `jasper-app/src/test/java/dev/jasper/app/workspace/ChromeArtworkTest.java` (test ownership, Task 2).

- [ ] **Step 4: Run the tests**

Run: the Step 2 command, then `./gradlew :jasper-app:test --tests '*Workspace*' --tests '*Chrome*' --tests '*ThemeMigrationCoverageTest' --tests '*StrategyIsolationTest' --tests '*VariantReadTest'`
Expected: PASS.

- [ ] **Step 5: Commit** — message `Chrome: a look's [chrome] icons set marks each window's header and status bar` plus a body naming the projection and the Enterprise kit's `classic`, with the trailers.

### Task 3: Enterprise tests read the shipped theme's roles

The tests keep everything they measure, with two exceptions. A colour target that is an Enterprise theme value becomes the role it is, and a comparison of a render pixel with a mock pixel becomes a comparison with the role the render paints there. With today's theme every role resolves to today's literal, so every test passes before and after this task. Task 9 then changes the theme and these tests follow it.

**Files:**
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java`, `jasper-app/src/test/java/dev/jasper/app/testsupport/MockProfile.java`, `MockMatch.java`
- Modify (colour targets only): in jasper-app, `plugins/ClassicCompositionRenderTest`, `plugins/ClassicVaultRenderTest`, `settingsui/SettingsPagesMockTest`, `settingsui/SettingsChromeMockTest`, `settingsui/SettingsMockMatch`, `workspace/EnterpriseChromeTargetsTest`, `workspace/EnterpriseMenuRowMockTest`, `workspace/EnterpriseWorkstationMockTest`, `workspace/DockMockTest`, `workspace/RuledCellsTest`, `workspace/ConfigurationStatusTest`, `windows/EnterpriseDialogMockTest`, `windows/ClassicPlacementTest`, `palette/RunWindowRenderTest`, `settingsui/EnterpriseSettings`, `plugins/PanelSurfaceFixture`; in jasper-ui, `swing/SettingsShellFrameTest`, `RecordingBandTest`, `ClassicSeparatorsTest`, `ClassicDetailGridTest`, `ClassicStatusLineTest`, `ClassicListHeadingsTest`, `CollapsibleNavigationTest`, `HostedTitleTest`, `ClassicPropertiesTest`, `ClassicPanelRowTest`, `ClassicPanelBodyTest`, `ClassicDialogTest`. Leave `EnterpriseThemeTest` (Task 9 rewrites it), `ColourLiteralScanTest`, `KitFixturesTest`'s fixture palettes and `PaintedContrastTest` alone.
- Test: `jasper-app/src/test/java/dev/jasper/app/testsupport/MockMatchTest.java`, `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java`

**Interfaces:**
- Produces:
  - `public static Color KitFixtures.colour(GeneratedTheme theme, String key)`: a `SemanticColor` or `DerivedColor` key resolved on the theme, opaque roles as is; an unknown key throws `IllegalArgumentException` naming it;
  - `public static Color KitFixtures.painted(GeneratedTheme theme, PaintPair pair)`, which is `SurfaceContrast.painted`;
  - `public static Color MockProfile.role(boolean dark, String key)`, which is `KitFixtures.colour(EnterpriseLook.theme(dark), key)`;
  - `public static Color MockProfile.painted(boolean dark, PaintPair pair)`;
  - `public static List<Band> MockMatch.parse(String text, GeneratedTheme theme)`, where each colour cell is `#RRGGBB` or a role key and `a~b` takes either;
  - `parse(String)` stays the hex-only form.

- [ ] **Step 1: Write the failing tests**

```java
// KitFixturesTest
    @org.junit.jupiter.api.Test void colourResolvesSemanticAndDerivedKeysOnTheTheme() {
        var light = KitFixtures.enterpriseLight();
        assertThat(KitFixtures.colour(light, "surface.app")).isEqualTo(light.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
        assertThat(KitFixtures.colour(light, "surface.header.active")).isEqualTo(dev.jasper.ui.theme.DerivedColor.SURFACE_HEADER_ACTIVE.color(light));
        assertThatIllegalArgumentException().isThrownBy(() -> KitFixtures.colour(light, "surface.nowhere")).withMessageContaining("surface.nowhere");
    }
// MockMatchTest
    @Test void bandsMayNameThemeRoles() {
        var theme = dev.jasper.ui.testsupport.KitFixtures.enterpriseLight();
        var bands = MockMatch.parse("0+6 surface.app, 6+1 border.control, 7+20 button.secondary.bg~button.end, 27+1 #B4B8BF", theme);
        assertThat(bands).hasSize(4);
        assertThat(bands.get(0).first()).isEqualTo(dev.jasper.ui.testsupport.KitFixtures.colour(theme, "surface.app"));
        assertThat(bands.get(2).last()).isEqualTo(dev.jasper.ui.testsupport.KitFixtures.colour(theme, "button.end"));
        assertThat(bands.get(3).first()).isEqualTo(new java.awt.Color(0xB4B8BF));
    }
```

(Use the `Band` record's real accessor names; read `MockMatch.java:114-125` first.)

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-ui:test --tests '*KitFixturesTest' :jasper-app:test --tests '*MockMatchTest'`
Expected: compilation fails.

- [ ] **Step 3: Implement the helpers**, then run Step 2's command. Expected: PASS.

- [ ] **Step 4: Print today's value-to-role table**

```bash
python3 -I - <<'PY' > /private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/enterprise-roles.txt
import json, collections
theme = json.load(open("plugins/enterprise/src/main/resources/themes/enterprise.json"))
derived = [l.rstrip("\n").split("\t") for l in open("jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles-enterprise.tsv") if not l.startswith("#")]
for variant in ("light", "dark"):
    roles = collections.defaultdict(list)
    for key, value in theme["ui"][variant].items(): roles[value.upper()].append(key)
    for _, var, role, colour in derived:
        if var == variant and role not in roles[colour.upper()]: roles[colour.upper()].append(role)
    for colour in sorted(roles): print(variant, colour, ", ".join(roles[colour]))
PY
wc -l /private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/enterprise-roles.txt
```

Expected: about 150 lines, `light #ECEDEF surface.app, surface.dialog` among them.

- [ ] **Step 5: Convert the colour targets**

Apply these rules in each file listed above.
- **A literal that is a value in the table and stands for what the render paints** becomes `MockProfile.role(dark, "<key>")` in jasper-app or `KitFixtures.colour(theme, "<key>")` in jasper-ui. A band string becomes role keys through `MockMatch.parse(text, theme)`, and a colours list becomes keys. Where one value has several roles, take the one the probe paints; the test's own comment names it (for example "the header strip", "the card").
- **A guarded ink** (text, a rule or glyph a face holds to a floor) becomes `MockProfile.painted(dark, <the face's PaintPair>)`, for example `ChromePainter.HEADER_TITLE` or `WindowPairs.RULE`.
- **A render pixel compared with a mock pixel** (`deltaE(at(render, …), at(mock, …))`, or a run's colour against the mock's run) is compared with the role or pair the render paints there. The mock's run lengths and positions are still compared.
- **A literal that is not an Enterprise theme value**, such as a mock-only ink like `#2655AE` or a marker colour, keeps its comparison when the render paints that literal. When it does not, the test compares the render with the role it paints and gains a one-line comment naming the user's 2026-10-18 direction.

Then confirm that only intended literals remain:

```bash
grep -nE "0x[0-9A-Fa-f]{6}|#[0-9A-Fa-f]{6}" <each file above> | grep -v "parse(\"" | wc -l
```

Expected: the count falls to the non-theme literals only; review the remainder by eye.

- [ ] **Step 6: Run the Enterprise tests**

Run: `./gradlew :jasper-ui:test :jasper-app:test --tests '*Enterprise*' --tests '*Classic*' --tests '*Mock*' --tests '*Settings*' --tests '*RunWindow*' --tests '*Dock*' --tests '*RuledCells*' --tests '*ConfigurationStatus*'`
Expected: PASS with no assertion changed beyond its colour source.

- [ ] **Step 7: Commit** — `Tests: Enterprise renders read their colours from the shipped theme's roles`, with the trailers.

### Task 4: Classic toolbar buttons show their icons

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ClassicToolbarButton.java`, `ClassicHeader.java`, `WindowChrome.java` (`classicButton`, `classicSplit`), `WindowToolbar.java:170`; `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java`; `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv`, `classic-roles.tsv`
- Test: `ClassicHeaderTest`, `WindowToolbarLeadingTest`, `EnterpriseWorkstationMockTest`, `WindowPairsTest` (drop its `Glyph` argument)

**Interfaces:**
- Consumes: Task 2's receiver marks (the classic header is inside a `CLASSIC`-marked `WindowHeader`).
- Produces:
  - `ClassicToolbarButton(Action action, String label, boolean caret, Icon icon)` (no `Glyph`);
  - `WindowChrome.classicButton(WindowToolbar into, ActionId id, String label, String icon, int priority)`;
  - the roles `CHROME_ICON_BUTTON(22)` and `CHROME_ICON_CARET(4)`, appended after `LIST_TWO_LINE_ROW_HEIGHT` with the comment "Plan 12: the classic toolbar's icon-only button (IntelliJ's 22 px ActionButton) and the ▼ in an icon-only dropdown's corner; only ClassicToolbarButton reads them", each also appended to both role tables with the same value.

- [ ] **Step 1: Write the failing tests** in `ClassicHeaderTest`

Replace `theToolbarSettingChoosesLabelsIconsOrNoRow`'s ICONS assertions with the new size: `first.getPreferredSize()` is `new Dimension(scale(22), scale(22))` and `first.getY()` is `(bar.getHeight() - scale(22)) / 2`. Add:

```java
    static ClassicToolbarButton labelled(WindowToolbar bar, String label) {
        for (Component child : bar.getComponents())
            if (child instanceof ClassicToolbarButton button && label.equals(button.getClientProperty("label"))) return button;
        throw new AssertionError(label);
    }

    /** Rulings 7: icons and labels draws the icon, then the label; icons alone a 22 px square; Split marks its menu in its corner. */
    @Test void iconButtonsAreTwentyTwoSquaresAndIconsAndLabelsDrawTheIconThenTheLabel() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            var window = window(themes, false);
            var bar = window.owner().chrome().windowToolbar();
            window.owner().setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
            window.width(1440);
            var session = labelled(bar, "New Session");
            int text = session.getFontMetrics(session.getFont()).stringWidth("New Session");
            assertThat(session.getPreferredSize().width).isEqualTo(2 * scale(8) + scale(16) + scale(6) + text);
            session.setSize(session.getPreferredSize());
            assertThat(alphaIn(session, new Rectangle(scale(8), 0, scale(16), session.getHeight()))).as("the icon before the label").isPositive();
            window.owner().setToolbarMode(ToolbarMode.ICONS);
            window.width(1440);
            var split = labelled(bar, "Split");
            assertThat(split.getPreferredSize()).isEqualTo(new Dimension(scale(22), scale(22)));
            assertThat(split.getToolTipText()).isEqualTo("Split pane right or down");
            split.setSize(split.getPreferredSize());
            var corner = new Rectangle(scale(22) - scale(7), scale(22) - scale(5), scale(5), scale(3));
            assertThat(alphaIn(split, corner)).as("Split's corner ▼").isPositive();
            var maximize = labelled(bar, "Maximize");
            maximize.setSize(maximize.getPreferredSize());
            assertThat(alphaIn(maximize, corner)).as("no ▼ without a menu").isZero();
        });
    }

    /** Review Focus 5. */
    @Test void asTheRowNarrowsIconButtonsKeepTheirSquaresAndSplitItsCornerArrow() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            var window = window(themes, true);
            window.owner().setToolbarMode(ToolbarMode.ICONS);
            var bar = window.owner().chrome().windowToolbar();
            for (int width = 1440; width >= 420; width -= 40) {
                window.width(width);
                for (Component child : bar.getComponents())
                    if (child instanceof ClassicToolbarButton button && button.isVisible() && button.getWidth() > 0)
                        assertThat(button.getSize()).as(width + " " + button.getClientProperty("label")).isEqualTo(new Dimension(scale(22), scale(22)));
            }
        });
    }

    /** Rulings 3 and 7: under the classic set the buttons paint IntelliJ's classic artwork. */
    @Test void underTheClassicSetTheButtonsPaintIntelliJsClassicIcons() throws Exception {
        // window(...) under ClassicHeaderTest.classic(WorkspaceLook.Icons.CLASSIC); New Session's centre 16 x 16 equals
        // new IconRegistry().chromeIcon("add", 16, ChromeArtwork.CLASSIC).icon(theme, TEXT_PRIMARY) painted at the same place.
    }
```

Write the body of the last test as its comment says. `classic(icons)` is a parameterised form of `classic()`. `alphaIn(component, rect)` is the strongest alpha in `rect` of the component painted on a transparent image, next to `strongestAlpha`. `scale` is `UIScale::scale`.

In `EnterpriseWorkstationMockTest.theToolbarRowIsTheMocks`, with `icons_and_labels` (the core default these renders use), keep the probes and change only these expectations, each with a comment `// The user's 2026-10-18 direction: classic icon then label.`:
- ink 0 is the add icon at the mock's "+" start within 4, and its width is checked against `2 * 16` within 4 instead of the "+" glyph's;
- ink 1, the label, starts `2 * ((16 + 6) - (7 + 6)) = 18` to the right of the mock's;
- the search field's left is `2116 - 2 * (16 + 6) = 2072` within 4 (Settings… gains its icon and gap);
- "Settings…" keeps its end within 4 of the mock's and gains one more ink (its icon) before its label.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests '*ClassicHeaderTest' --tests '*EnterpriseWorkstationMockTest' --tests '*WindowToolbarLeadingTest'`
Expected: FAIL (24 px squares, no icon before the label, no corner ▼).

- [ ] **Step 3: Implement**

- `ClassicToolbarButton`:
  - remove `Glyph` and add the comment "Plan 12 (spec 10.3): …";
  - icon-only preferred size: `CHROME_ICON_BUTTON` square;
  - labelled width: `2 * CHROME_BUTTON_PADDING + (icon == null ? 0 : icon width + CHROME_GLYPH_GAP) + text`, plus `CHROME_CARET_GAP + CHROME_CARET` for a caret, with height `side()`;
  - labelled paint: the icon at `x = CHROME_BUTTON_PADDING`, vertically centred (faded at 0.42 when disabled), then the text and, for a caret, today's ▼;
  - icon-only paint keeps today's centred icon. A caret adds a filled ▼ `CHROME_ICON_CARET` wide and half as tall, its right edge 2 px and its bottom 2 px inside the button, in the label ink.
- `WindowChrome.classicButton` loses its glyph argument and `classicSplit` passes `true` for its caret. `ClassicHeader.build` drops `Glyph.PLUS`/`NONE` (New Session's icon is `square-plus`, and the drawn "+" goes), and `contributed` loses `Glyph.NONE`.
- `WindowToolbar.java:170`: `int height = isView(child) ? Math.min(getHeight(), child.getPreferredSize().height) : child instanceof ClassicToolbarButton icon && icon.getText() == null ? Math.min(side, icon.getPreferredSize().height) : side;`
- Add the two roles to `DesignMetrics` and the two rows to both tsv files.

- [ ] **Step 4: Run the tests**

Run: the Step 2 command, then `./gradlew :jasper-ui:test --tests '*DesignMetricsTest' --tests '*ClassicCatalogTest' :jasper-app:test --tests '*WindowPairsTest' --tests '*ChromeRenderTest'`
Expected: PASS. `ChromeRenderTest` writes `jasper-app/build/chrome-renders/enterprise-light-workstation.png` with the icon-and-label row.

- [ ] **Step 5: Commit** — `Classic header: IntelliJ's classic icon buttons, icon and label, and Split's corner ▼`, with the trailers.

### Task 5: `window.toolbar` as a look default

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/DefaultSource.java`, `ConfigSnapshot.java`, `jasper-app/src/main/java/dev/jasper/app/presentation/KitParser.java` (`defaultsByKey`), `plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml`, `config.example.toml`
- Test: `DefaultSourceTest`, `DefaultsPrecedenceTest`, `KitParserTest`, `PresentationConformanceTest`

**Interfaces:**
- Produces: `DefaultSource.ALLOWED` gains `CoreSettings.WINDOW_TOOLBAR`. `ConfigSnapshot.defaults(source)` and `from(values, defaults, …)` read `toolbar` through it.

- [ ] **Step 1: Write the failing tests**

- `DefaultSourceTest`: the ALLOWED assertion gains `CoreSettings.WINDOW_TOOLBAR`. Add `assertThat(new DefaultSource(Map.of(CoreSettings.WINDOW_TOOLBAR, ToolbarMode.ICONS)).value(CoreSettings.WINDOW_TOOLBAR)).isEqualTo(ToolbarMode.ICONS);` and `assertThatThrownBy(() -> new DefaultSource(Map.of(CoreSettings.WINDOW_TOOLBAR, "icons")))`.
- `DefaultsPrecedenceTest`: `KIT` gains `CoreSettings.WINDOW_TOOLBAR, ToolbarMode.ICONS`. `aFreshHomesFirstSnapshotCarriesTheKitsDefaults` asserts `snapshot.toolbar()` is `ICONS`. Add `aSavedToolbarModeWinsOverTheLooks`: `ConfigLoader.parse(file, "[window]\ntoolbar = \"hidden\"\n", false, KIT).snapshot().toolbar()` is `HIDDEN`, and with an empty file it is `ICONS`. In `noAllowlistedKeyReachesACoreDefaultComparison`, the CorePages `contains` list gains `.row(WINDOW_TOOLBAR, Editors.segments(` (the editor list's `doesNotContain` loop is unchanged).
- `KitParserTest`: `[defaults]\n"window.toolbar" = "icons"` parses to `ToolbarMode.ICONS`, and `"window.toolbar" = "tiny"` gives one message starting `[defaults]."window.toolbar": `. The not-allowlisted message now ends `"window.toolbar_height" or "window.toolbar"`.
- `PresentationConformanceTest`: `KEYS` gains `"defaults/window.toolbar"`, and the parse test asserts `parsed.defaults().value(CoreSettings.WINDOW_TOOLBAR)` is `ToolbarMode.ICONS`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests '*DefaultSourceTest' --tests '*DefaultsPrecedenceTest' --tests '*KitParserTest' --tests '*PresentationConformanceTest'`
Expected: FAIL (not allowlisted).

- [ ] **Step 3: Implement**

- Add `CoreSettings.WINDOW_TOOLBAR` last in `ALLOWED` and in `defaultsByKey`'s list; the comment reads "both theme picks, the palette's rows, and the toolbar's height and mode".
- `ConfigSnapshot.defaults(source)` adds `.toolbar(source.value(CoreSettings.WINDOW_TOOLBAR))`, and `from(values, defaults, …)` adds `.toolbar(values.saved(CoreSettings.WINDOW_TOOLBAR).orElseGet(() -> defaults.value(CoreSettings.WINDOW_TOOLBAR)))`.
- `enterprise.kit.toml` `[defaults]` gains `"window.toolbar" = "icons"`.
- `config.example.toml`: add `# The default depends on the active look.` above the `toolbar =` line.

- [ ] **Step 4: Run the tests**

Run: the Step 2 command, then `./gradlew :jasper-app:test --tests '*ConfigTemplateTest' --tests '*ConfigSnapshot*' --tests '*Settings*'`
Expected: PASS.

- [ ] **Step 5: Commit** — `Defaults: a look may default window.toolbar; Enterprise starts with icons`, with the trailers.

### Task 6: SDK 0.18.1 — icon-only GHOST buttons in toolbar views

**Files:**
- Modify:
  - `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java` (`"0.18.1"`), `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ToolbarItem.java` (Javadoc);
  - `jasper-app/src/main/java/dev/jasper/app/plugins/ToolbarViewAdmission.java`;
  - `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/ToolbarViewAdmission.java`, `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeKit.java` (`BUILT_INS` gains `"run","application"`);
  - `docs/examples/external-plugin/build.gradle.kts` (default `0.18.1`).
- Test: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`, `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitBoundaryTest.java`, `CapabilitiesTest`, `files/FilesValuesTest`, `palette/PaletteValuesTest`, `jasper-app/src/test/java/dev/jasper/app/plugins/KitCompatibilityTest.java` (`this is SDK 0.18.1`), `VersionRangeTest` (adds `">=0.18.1, <0.19"` as Remote's range)

**Interfaces:**
- Consumes: the host's `run` and `application` names (Task 1).
- Produces: a toolbar view admits `Nodes.Button`s that are PRIMARY (at most one) or GHOST with a blank label. The fake's icon vocabulary includes `run` and `application`.

- [ ] **Step 1: Write the failing tests**

Change each version assertion from `"0.18.0"` to `"0.18.1"`. Add to `PluginContractTest`:

```java
    /** SDK 0.18.1: a toolbar view also admits icon-only GHOST buttons; a labelled GHOST is still refused. */
    @Test void aToolbarViewAdmitsIconOnlyGhostButtons() {
        var ready = dev.jasper.sdk.ui.ControlState.READY;
        List<dev.jasper.sdk.ui.UiEvent> seen = new CopyOnWriteArrayList<>();
        var admitted = new AtomicReference<dev.jasper.sdk.ui.UiView>();
        var run = new dev.jasper.sdk.ui.Nodes.Button("bar.run", ready, "", "run", dev.jasper.sdk.ui.Nodes.ButtonKind.GHOST, "run", "Connect");
        var spec = new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("bar", ready, dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE, List.of(
            new dev.jasper.sdk.ui.Nodes.Choice("bar.host", ready, dev.jasper.sdk.ui.Nodes.ChoiceKind.DROPDOWN,
                List.of(new dev.jasper.sdk.ui.Option("mac", "Mac", Optional.empty())), "mac", "Session", "host"),
            run,
            new dev.jasper.sdk.ui.Nodes.Button("bar.stop", ready, "", "close", dev.jasper.sdk.ui.Nodes.ButtonKind.GHOST, "stop", "Stop"))));
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> context.toolbar().add(ToolbarItem.view(window -> {
            var view = context.kit().create(spec, seen::add);
            admitted.set(view);
            return view;
        })));
        var window = new AtomicReference<UUID>();
        h.ui(() -> window.set(h.addTerminalWindow()));
        h.ui(() -> {
            assertThat(h.openToolbarViews(window.get())).containsExactly("bar");
            h.clickToolbarViewButton(window.get(), "bar.run");
            var labelled = new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("bar", ready, dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE, List.of(
                new dev.jasper.sdk.ui.Nodes.Button("bar.edit", ready, "Edit", "", dev.jasper.sdk.ui.Nodes.ButtonKind.GHOST, "edit", ""))));
            assertThatIllegalArgumentException().isThrownBy(() -> admitted.get().update(labelled)).withMessageContaining("bar.edit");
            assertThat(admitted.get().spec()).isEqualTo(spec);
        });
        assertThat(seen).containsExactly(new dev.jasper.sdk.ui.UiEvent.Action("run"));
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests '*AppContractTest' --tests '*KitCompatibilityTest' --tests '*VersionRangeTest'`
Expected: FAIL (version 0.18.0; the GHOST view is refused by both admissions).

- [ ] **Step 3: Implement**

- In both admissions: `case Nodes.Button button -> button.kind() == Nodes.ButtonKind.PRIMARY || button.kind() == Nodes.ButtonKind.GHOST && button.label().isBlank();`. The count becomes `if (child instanceof Nodes.Button button && button.kind() == Nodes.ButtonKind.PRIMARY && ++primaries > 1)`. Each class comment gains ", icon-only GHOST buttons (a blank label, with an icon and a tooltip)".
- `ToolbarItem.View`'s Javadoc becomes "…at most one PRIMARY {@link Nodes.Button} and any number of icon-only GHOST Buttons (a blank label with an icon and a tooltip, since 0.18.1), {@link Nodes.MenuButton}s and {@link Nodes.Separator}s…".
- `JasperSdk.VERSION = "0.18.1"`; `FakeKit.BUILT_INS` adds `"run","application"`; the example's default becomes `"0.18.1"`.

- [ ] **Step 4: Run the tests**

Run: the Step 2 command plus `./gradlew :jasper-sdk:javadoc :jasper-sdk-testkit:javadoc`
Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 5: Commit** — `SDK 0.18.1: toolbar views admit icon-only GHOST buttons; run and application icons`, with the trailers.

### Task 7: The toolbar combo face and toolbar-strip glyphs

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/ButtonFace.java`, `DefaultButtonFace.java`, `ClassicButtonFace.java`, `Choices.java`, `Buttons.java`; `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` and both role tables
- Test: create `jasper-ui/src/test/java/dev/jasper/ui/swing/ClassicToolbarComboTest.java`; modify `KitViewToolbarStripTest.java`

**Interfaces:**
- Consumes: the `application` and `run` names (Task 1).
- Produces:
  - `Dimension ButtonFace.toolbarCombo(Choices.ChoiceCombo combo)` ("The closed size of a rich dropdown in a toolbar strip, or null to keep the standard face's"): `DefaultButtonFace` returns null, `ClassicButtonFace` returns Rulings 10's size;
  - `boolean Choices.ChoiceCombo.toolbar`, set from `view.toolbarStrip()` beside the face (`Choices.java:131`);
  - `boolean Buttons.KitButton.toolbarStrip`, set in `ButtonControl.refresh` before the glyph extent;
  - the roles, appended after Task 4's with the comment "Plan 12: IntelliJ's run-configuration combo (ComboBoxButton) in a toolbar strip; only the classic buttons face reads them", in both tables with these values: `TOOLBAR_COMBO_HEIGHT(24)`, `TOOLBAR_COMBO_LEADING(10)`, `TOOLBAR_COMBO_ICON_GAP(4)`, `TOOLBAR_COMBO_ARROW(16)`, `TOOLBAR_COMBO_TRAILING(7)`, `TOOLBAR_COMBO_RADIUS(3)`, `TOOLBAR_COMBO_TEXT_MAX(240)`.

- [ ] **Step 1: Write the failing tests**

```java
/** Plan 12 Rulings 10: a rich dropdown in a toolbar strip is IntelliJ's run-configuration combo under the classic buttons. */
class ClassicToolbarComboTest {
    static ViewSpec bar(String name) {
        var ready = ControlState.READY;
        return new ViewSpec(new Nodes.Stack("bar", ready, Nodes.LayoutRole.INLINE, List.of(new Nodes.Choice("bar.host", ready, Nodes.ChoiceKind.DROPDOWN,
            List.of(new Option("mac", name, java.util.Optional.empty())), "mac", "Session", "", "", java.util.Optional.empty(),
            java.util.Optional.of(new Nodes.PickerFace(name, "", "application", Nodes.Hue.NONE, Nodes.PickerDensity.COMPACT))))));
    }
    static int s(int v) { return com.formdev.flatlaf.util.UIScale.scale(v); }

    @Test void aRichDropdownInAToolbarStripIsIntelliJsRunConfigurationCombo() {
        Edt.run(() -> {
            var theme = KitFixtures.enterpriseLight();
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            try (var view = new KitView(context, bar("Mac (192.168.1.119)"), event -> { })) {
                view.toolbarStrip(true);
                var combo = KitViewTest.find(view, "bar.host", Choices.ChoiceCombo.class);
                var size = combo.getPreferredSize();
                int text = ThemePainting.measureText(ThemePainting.font(context, Typography.Role.BODY), "Mac (192.168.1.119)");
                assertThat(size.height).isEqualTo(s(24));
                assertThat(size.width).isCloseTo(s(10) + s(16) + s(4) + text + s(4) + s(16) + s(7), within(1));
                combo.setSize(size);
                var image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); try { combo.paint(g); } finally { g.dispose(); }
                assertThat(new Color(image.getRGB(size.width - s(7) - s(16) - s(2), s(4)), true))
                    .as("the white box").isEqualTo(theme.semantic().color(SemanticColor.SURFACE_INPUT));
                assertThat(new Color(image.getRGB(size.width / 2, s(1)), true).getRGB() | 0xFF000000).as("the ring")
                    .isEqualTo(KitFixtures.painted(theme, new PaintPair(SemanticColor.BORDER_CONTROL, context.kit().colors().dialogSurface(), SurfaceContrast.Guard.CONTROL)).getRGB());
                var arrow = new Color(image.getRGB(size.width - s(7) - s(8), size.height / 2), true);
                assertThat(arrow.getAlpha()).as("the 9 x 5 triangle").isGreaterThan(200);
                var icon = BuiltInArtworkPixels.of(context.icons().icon("application", theme, SemanticColor.TEXT_SECONDARY, s(16)));
                assertThat(region(image, s(10), (size.height - s(16)) / 2, s(16))).as("IntelliJ's application icon").isEqualTo(icon);
            } finally { context.binding().close(); context.icons().close(); }
        });
    }

    @Test void aLongNameIsElidedAtTheCap() { /* name = "x".repeat(400): width is s(57) + s(240) within 1 */ }

    @Test void outsideAToolbarStripOrUnderTheDefaultFaceTheDropdownIsUnchanged() {
        /* the same spec without toolbarStrip: preferred height is the classic PICKER_DEFAULT_HEIGHT path (22) and combo.toolbar
           is false; under KitFixtures.context(KitFixtures.light()) in a strip: Kit.DEFAULT's face returns null, so the preferred
           size equals the size outside a strip */
    }
}
```

Write the two short bodies as their comments say. `region` and `BuiltInArtworkPixels.of` are small local helpers; `kit().colors().dialogSurface()` is the kit's dialog surface role. Use the accessors the code has, and keep the assertion meaning. Add to `KitViewToolbarStripTest`:

```java
    /** Plan 12 Rulings 11 and Review Focus 4: an icon-only GHOST button in a toolbar strip: flat at rest, a 16 px authored glyph. */
    @Test void anIconOnlyGhostButtonInAToolbarStripIsFlatWithARailGlyph() {
        // KitFixtures.context(enterpriseLight(), enterprise()); INLINE stack with Button("bar.run", READY, "", "run", GHOST, "run", "Connect");
        // toolbarStrip(true): the button's icon is scale(ICON_RAIL) wide; its preferred size is the BUTTON_HEIGHT square; at rest the
        // corner pixel (2, 2) is transparent; the centre pixel is green-dominant (g > r + 40 && g > b + 40), the authored #59A869.
    }

    @Test void aDisabledRunButtonFadesItsTriangleWithoutRecolouringIt() {
        // the same button with ControlState(true, false, false, ""): the centre pixel's alpha is at most 0.42 of the enabled one's + 1,
        // and its unpremultiplied colour is still green-dominant.
    }
```

Write these bodies from their comments with `KitFixtures.render`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-ui:test --tests '*ClassicToolbarComboTest' --tests '*KitViewToolbarStripTest'`
Expected: FAIL (the gradient face, a 22 px height, a 14 px glyph).

- [ ] **Step 3: Implement**

- `Choices`:
  - set `((ChoiceCombo) combo).toolbar = view.toolbarStrip();` before assigning the face;
  - in `getPreferredSize`, when `toolbar && face != null`, return `context.faces().buttons.toolbarCombo(this)` if it is not null.
- `ClassicButtonFace`:
  - add `comboEdge = new PaintPair(SemanticColor.BORDER_CONTROL, surface, Guard.CONTROL)`, `comboArrow = new PaintPair(SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_INPUT, Guard.INDICATOR)` and `comboText = new PaintPair(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_INPUT, Guard.TEXT)`, all listed in `pairs()`;
  - `paintPicker` starts with `if (owner instanceof Choices.ChoiceCombo combo && combo.toolbar) { toolbarCombo(combo, graphics, c, face, empty, focus || expanded); return; }`. The private `toolbarCombo` paints Rulings 10:
    - `ThemePainting.rounded(g, owner, c, surface.input, ring, ring is BORDER_FOCUS when focused or open, …, radius TOOLBAR_COMBO_RADIUS, inset 1, 0)`, where the ring is `painted(comboEdge)`, or `border.divider` when disabled;
    - the icon (`c.icons().icon(face.icon(), c.theme(), SemanticColor.TEXT_SECONDARY, ICON_RAIL)`, skipped when blank) at `TOOLBAR_COMBO_LEADING`, vertically centred;
    - the name (`"Choose…"` when empty) in `Typography.Role.BODY` and `painted(comboText)`, clipped to `TOOLBAR_COMBO_TEXT_MAX` with an ellipsis;
    - a `Path2D` triangle (3.5, 6), (12.5, 6), (8, 11) in a `TOOLBAR_COMBO_ARROW` box ending `TOOLBAR_COMBO_TRAILING` from the right edge, vertically centred, in `painted(comboArrow)`;
    - everything but the box faded to `DISABLED` while disabled;
  - `toolbarCombo(ChoiceCombo)` returns `(LEADING + (icon blank ? 0 : ICON_RAIL + ICON_GAP) + min(text, TEXT_MAX) + ICON_GAP + ARROW + TRAILING, HEIGHT)`, scaled.
- `DefaultButtonFace.toolbarCombo` returns null.
- `Buttons`: `button.toolbarStrip = view.toolbarStrip();`. The extent uses `IconSize.RAIL` when `value.label().isEmpty() && (embedded || button.panel || button.toolbarStrip)`.

- [ ] **Step 4: Run the tests**

Run: the Step 2 command, then `./gradlew :jasper-ui:test` (the face matrix, the calibration, the role tables and `KitMatrixTest`)
Expected: PASS.

- [ ] **Step 5: Commit** — `Classic buttons: IntelliJ's run-configuration combo in a toolbar strip; rail glyphs for icon buttons there`, with the trailers.

### Task 8: Remote's session view — the combo and Run

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/SessionsToolbar.java`, `plugins/remote/src/main/resources/plugin.toml` (`version = "0.14.0"`, `sdk = ">=0.18.1, <0.19"`)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/SessionToolbarViewTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/ClassicCompositionRenderTest.java`, `tools/visual/pairs.json` (the two `composition-*-session-toolbar` entries)

**Interfaces:**
- Consumes: Task 6's admission, Task 7's combo face and `run` and `application`.
- Produces: the classic view `INLINE[session.choice (DROPDOWN, PickerFace), session.connect (GHOST "run", tooltip "Connect")]`. `SessionsToolbar.LABEL` is removed.

- [ ] **Step 1: Write the failing tests**

In `underClassicOneViewListsHostsThenDistributionsThenManage`, replace the label assertion with:

```java
            assertThat(value(() -> view.spec().root() instanceof Nodes.Stack stack ? stack.children().stream().map(Nodes.Node::id).toList() : List.of()))
                .containsExactly("session.choice", "session.connect");
            var face = choice(view).face().orElseThrow();
            assertThat(face.primary()).isEqualTo("Mac (192.168.1.119)");
            assertThat(face.icon()).isEqualTo("application");
            assertThat(face.density()).isEqualTo(Nodes.PickerDensity.COMPACT);
            var run = read(view, "session.connect", Nodes.Button.class);
            assertThat(run.kind()).isEqualTo(Nodes.ButtonKind.GHOST);
            assertThat(run.label()).isEmpty();
            assertThat(run.icon()).isEqualTo("run");
            assertThat(run.tooltip()).isEqualTo("Connect");
```

After the distro selection in `connectActsOnTheChosenHostThenThePanesThenTheFirst`, assert `choice(view).face().orElseThrow().primary()` is `"Ubuntu (WSL 2)"`. Add:

```java
    /** Review Focus 3. */
    @Test void withNoSavedSessionsTheComboSaysSoAndRunIsDisabled() throws Exception {
        try (var host = new FakePluginHost()) {
            distros = List.of();
            start(host, Layout.CLASSIC);
            store.remove(mac.id()).join(); store.remove(minecraft.id()).join();
            edt(toolbar::refresh);
            var view = view(host, host.addTerminalWindow());
            assertThat(choice(view).face().orElseThrow().primary()).isEqualTo("No saved sessions");
            assertThat(read(view, "session.connect", Nodes.Button.class).state().enabled()).isFalse();
        }
    }
```

(Use `HostStore`'s real removal method and `ControlState`'s real enabled accessor.)

In `ClassicCompositionRenderTest.theSessionToolbarViewMatchesTheWorkstationMocks`:
- replace `TOOLBAR_LIGHT` and `TOOLBAR_DARK` with the bands the render measures at x 130, written in Task 3's role notation; expect `surface.app`, `border.control` (the ring), `surface.input` (the box), `border.control`, `surface.app`;
- set the colours to `"surface.input", "border.control"`;
- if the edge agreement falls under 0.50, set the floor to the measured value less 0.03, with the comment `// The user's 2026-10-18 direction: IntelliJ's run-configuration combo and Run replace the mock's "Session:", combo and Connect; measured <value>.`;
- give the two `pairs.json` session-toolbar entries the same floor and a `"note"` with the same words, plus `Edge agreement <old> before, <new> after.`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :plugins:remote:test --tests '*SessionToolbarViewTest' :jasper-app:test --tests '*ClassicCompositionRenderTest'`
Expected: FAIL (the label node and the PRIMARY Connect).

- [ ] **Step 3: Implement**

In `SessionsToolbar.classic(window)`:
- read `var options = options();`;
- take the face's primary as the label of the option whose id is `current`, else `options.getFirst().label()`;
- return an INLINE stack of `new Nodes.Choice(CHOICE, READY, DROPDOWN, options, current.orElse(NONE_OPTION), "Session", "", "", Optional.empty(), Optional.of(new Nodes.PickerFace(primary, "", "application", Nodes.Hue.NONE, Nodes.PickerDensity.COMPACT)))` and `new Nodes.Button(CONNECT, new ControlState(true, current.isPresent(), false, ""), "", "run", Nodes.ButtonKind.GHOST, CONNECT, "Connect")`.

Then:
- remove `LABEL`;
- change the class comment's quoted view to "the session combo and Run";
- bump `plugin.toml`.

- [ ] **Step 4: Run the tests**

Run: the Step 2 command, then `./gradlew :plugins:remote:test :jasper-app:test --tests '*VersionRangeTest' --tests '*Remote*'`
Expected: PASS.

- [ ] **Step 5: Commit** — `Remote 0.14.0: the classic session view is IntelliJ's run-configuration combo and Run`, with the trailers.

### Task 9: The Enterprise theme — IntelliJ Light and New UI Dark

**Files:**
- Modify: `tools/themes/jasper_theme.py`, `tools/themes/test_jasper_theme.py`, `plugins/enterprise/src/main/resources/themes/enterprise.json` (regenerated), `jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles-enterprise.tsv` (regenerated), `jasper-app/src/test/java/dev/jasper/app/appearance/EnterpriseThemeTest.java` (rewritten), `jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml`, `tools/visual/check_pairs.py`, `tools/visual/pairs.json`
- Create: `tools/themes/enterprise/port.json`, `tools/themes/enterprise/SOURCE.md`, `jasper-app/src/main/resources/dev/jasper/app/about/licenses/intellij-themes.NOTICE.txt`
- Modify as the run finds them: Enterprise tests whose guarded inks or merged runs change (see Step 7)

**Interfaces:**
- Consumes: Task 3's role helpers.
- Produces:
  - `convert_port(spec: dict, where: str) -> dict` (a Jasper theme document);
  - `exact_key(lookup: Lookup, name: str)`, the colour when the key resolves through an exact key, else None.

- [ ] **Step 1: Write `tools/themes/enterprise/port.json`**

```json
{
  "name": "Enterprise",
  "author": "JetBrains, ported by Jasper",
  "light": {
    "theme": "Light.theme.json", "scheme": "Light.xml", "scheme_name": "Enterprise Light",
    "roles": {"accent": "#2675BF", "surface.card": "contentBackground"},
    "overrides": {"surface.header.active": "#DFE2E9"}
  },
  "dark": {
    "theme": "expUI/expUI_dark.theme.json", "scheme": "expUI/expUI_darkScheme.xml", "scheme_name": "Enterprise Dark",
    "roles": {"accent": "Blue5"},
    "overrides": {}
  }
}
```

- [ ] **Step 2: Write the failing Python tests** (in `test_jasper_theme.py`, beside the Catppuccin ones)

```python
    ENTERPRISE = ROOT / "plugins/enterprise/src/main/resources/themes/enterprise.json"   # ROOT: the repository root the file already derives
    PORT = pathlib.Path(__file__).resolve().parent / "enterprise" / "port.json"

    def test_a_port_fills_override_keys_from_exact_keys_only(self):
        spec = json.loads(self.PORT.read_text(encoding="utf-8"))
        doc = tool.convert_port(spec, "port.json")
        light, dark = doc["ui"]["light"], doc["ui"]["dark"]
        self.assertEqual(light["surface.header"], "#F2F2F2")         # intellijlaf: ToolWindow.Header.inactiveBackground = panel
        self.assertEqual(light["surface.header.active"], "#DFE2E9")  # port.json replaces IntelliJ Light's #E2E6EC
        self.assertNotIn("surface.header.active", dark)               # New UI Dark has only "*.background": the recipe
        self.assertEqual(dark["surface.header"], "#2B2D30")
        self.assertEqual((light["accent"], dark["accent"]), ("#2675BF", "#366ACE"))
        self.assertEqual(list(doc["terminal"]), ["light", "dark"])
        self.assertEqual((doc["terminal"]["light"]["name"], doc["terminal"]["dark"]["name"]), ("Enterprise Light", "Enterprise Dark"))

    def test_the_enterprise_theme_is_the_port_of_intellij_light_and_new_ui_dark(self):
        if not self.ENTERPRISE.parent.is_dir():
            self.skipTest("not in the repository")
        text = tool.dump(tool.convert_port(json.loads(self.PORT.read_text(encoding="utf-8")), "port.json"))
        if UPDATE:
            self.ENTERPRISE.write_text(text, encoding="utf-8")
        self.assertEqual(text, self.ENTERPRISE.read_text(encoding="utf-8"))
```

- [ ] **Step 3: Run them to see them fail**

Run: `python3 -I -m unittest discover -s tools/themes -p 'test_*.py' -k port -k enterprise`
Expected: `AttributeError: module 'jasper_theme' has no attribute 'convert_port'`.

- [ ] **Step 4: Implement `exact_key` and `convert_port` in `jasper_theme.py`** (after `convert_intellij_theme`)

```python
def exact_key(lookup: Lookup, name: str):
    """The colour of IntelliJ key ``name`` when it resolves through an exact key at some level, as Lookup.key resolves it;
    None when a "*." wildcard wins first or nothing sets it."""
    for _, ui in lookup.effective.levels:
        if name in ui and lookup.colour(ui[name]) is not None:
            return lookup.colour(ui[name])
        if any(key.startswith("*.") and name.endswith(key[1:]) and lookup.colour(ui[key]) is not None for key in ui):
            return None
    return None


def convert_port(spec: dict, where: str) -> dict:
    """A Jasper theme ported from IntelliJ's own bundled themes in tools/themes/intellij (the Enterprise theme,
    tools/themes/enterprise/port.json): for each of "light" and "dark", the named theme converted with its editor scheme and
    the spec's chosen roles; then every override key whose IntelliJ key IntelliJ's own theme sets exactly (not through a
    "*." wildcard), laid over the role it stands on; then the spec's overrides. The terminal schemes are named by the spec."""
    doc = {"jasper-theme": FORMAT, "name": spec["name"], "author": spec["author"], "ui": {}, "terminal": {}, "editor": {}}
    for variant in ("light", "dark"):
        part = spec[variant]
        data = parse_json(read_file(INTELLIJ / part["theme"], part["theme"]), part["theme"])
        theme = ThemeFile(part["scheme_name"], part["theme"], data, None)
        result = convert_intellij_theme(theme, [], part["scheme_name"], INTELLIJ / part["scheme"], None, part.get("roles"))
        effective = resolve(theme, [], [], INTELLIJ / part["scheme"], None)
        if ("dark" if effective.dark else "light") != variant:
            raise ConversionError(f"{where}: {part['theme']} is not a {variant} theme")
        roles = {key: value for key, value in result.doc["ui"][variant].items() if key not in OVERRIDE_KEYS}
        lookup, filled = Lookup(effective), {}
        for key, intellij, under in OVERRIDES:
            colour = exact_key(lookup, intellij)
            if colour is not None:
                filled[key] = hex_of(colour if colour[3] == 255 else over(colour, parse_hex(roles[under])))
        filled.update(part.get("overrides", {}))
        doc["ui"][variant] = roles | {key: filled[key] for key in OVERRIDE_KEYS if key in filled}
        doc["terminal"][variant] = result.doc["terminal"][variant]
        doc["editor"][variant] = result.doc["editor"][variant]
    validate(doc, where)
    return doc
```

(Check `ThemeFile`'s real constructor and `parse_json`/`read_file`'s real signatures at `:303` and `:417`. A vendored theme here has `files = None`, as `vendored_theme` makes them.)

- [ ] **Step 5: Generate the theme and the derived table**

```bash
JASPER_THEME_UPDATE_GOLDEN=1 python3 -I -m unittest discover -s tools/themes -p 'test_*.py'
python3 -I -m unittest discover -s tools/themes -p 'test_*.py'
./gradlew :jasper-ui:test --tests '*DerivedRolesTableTest' || cp jasper-ui/build/derived-roles/derived-roles-enterprise.tsv jasper-ui/src/test/resources/dev/jasper/ui/theme/
./gradlew :jasper-ui:test --tests '*DerivedRolesTableTest'
grep -c '' plugins/enterprise/src/main/resources/themes/enterprise.json; grep 'surface.header.active' jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles-enterprise.tsv
```

Expected: the second unittest run passes and `DerivedRolesTableTest` passes. The last line shows `enterprise light surface.header.active #dfe2e9` and Dark's recipe value; record Dark's value for Task 12's spec table.

- [ ] **Step 6: Rewrite `EnterpriseThemeTest`**

Keep `ROOT` and `FILE`, and replace the tests:

```java
    /** Spec 7.6 (plan 12): the file is the port of IntelliJ Light and New UI Dark, with port.json's choices. */
    @Test void theFileIsThePortOfIntelliJLightAndNewUiDark() throws Exception {
        var theme = JasperTheme.parse(Files.readString(FILE), "enterprise");
        assertThat(theme.name()).isEqualTo("Enterprise");
        assertThat(theme.author()).isEqualTo("JetBrains, ported by Jasper");
        assertThat(theme.schemes()).extracting(scheme -> scheme.id().toString()).containsExactly("enterprise/light", "enterprise/dark");
        var light = theme.generated(false); var dark = theme.generated(true);
        Map<SemanticColor, Integer> lightRoles = Map.of(SemanticColor.SURFACE_APP, 0xF2F2F2, SemanticColor.SURFACE_CARD, 0xFFFFFF,
            SemanticColor.TEXT_PRIMARY, 0x000000, SemanticColor.ACCENT, 0x2675BF, SemanticColor.SELECTION_BG, 0x2675BF,
            SemanticColor.BORDER_WINDOW, 0xD1D1D1, SemanticColor.BORDER_CONTROL, 0xC4C4C4, SemanticColor.SURFACE_TERMINAL, 0xFFFFFF);
        Map<SemanticColor, Integer> darkRoles = Map.of(SemanticColor.SURFACE_APP, 0x2B2D30, SemanticColor.SURFACE_CARD, 0x2B2D30,
            SemanticColor.TEXT_PRIMARY, 0xDFE1E5, SemanticColor.ACCENT, 0x366ACE, SemanticColor.SELECTION_BG, 0x2E436E,
            SemanticColor.BORDER_WINDOW, 0x1E1F22, SemanticColor.BORDER_CONTROL, 0x4E5157, SemanticColor.SURFACE_TERMINAL, 0x1E1F22);
        lightRoles.forEach((role, rgb) -> assertThat(light.semantic().color(role).getRGB() & 0xFFFFFF).as("light " + role.key()).isEqualTo(rgb));
        darkRoles.forEach((role, rgb) -> assertThat(dark.semantic().color(role).getRGB() & 0xFFFFFF).as("dark " + role.key()).isEqualTo(rgb));
        assertThat(light.derivedOverrides()).containsOnly(entry(DerivedColor.SURFACE_HEADER, new Color(0xF2F2F2)),
            entry(DerivedColor.SURFACE_HEADER_ACTIVE, new Color(0xDFE2E9)), entry(DerivedColor.SURFACE_TABLE_HEADER, new Color(0xFFFFFF)),
            entry(DerivedColor.SURFACE_ZEBRA, new Color(0xF2F5F9)), entry(DerivedColor.SURFACE_ROW_HOVER, new Color(0xEDF5FC)));
        assertThat(dark.derivedOverrides()).containsOnly(entry(DerivedColor.SURFACE_HEADER, new Color(0x2B2D30)),
            entry(DerivedColor.SURFACE_ZEBRA, new Color(0x2B2D30)));
        assertThat(theme.matching(false).colors().background()).isEqualTo(new Color(0xFFFFFF));
        assertThat(theme.matching(true).colors().background()).isEqualTo(new Color(0x1E1F22));
    }

    /** Rulings 15: both accents keep their labels white under the TEXT guard. */
    @Test void bothAccentsKeepWhiteText() throws Exception {
        var theme = JasperTheme.parse(Files.readString(FILE), "enterprise");
        for (boolean dark : new boolean[] {false, true}) {
            var g = theme.generated(dark);
            assertThat(SurfaceContrast.painted(g, new PaintPair(SemanticColor.TEXT_ONACCENT, SemanticColor.ACCENT, SurfaceContrast.Guard.TEXT)))
                .as(dark ? "dark" : "light").isEqualTo(Color.WHITE);
        }
    }

    /** The user's request (3), Rulings 14 and 17: Light's focused header paints exactly #DFE2E9; Dark's keeps a visible tint. */
    @Test void theFocusedHeaderPaintsTheUsersColourInLightAndATintInDark() throws Exception {
        var theme = JasperTheme.parse(Files.readString(FILE), "enterprise");
        assertThat(SurfaceContrast.painted(theme.generated(false), ChromePainter.FOCUSED_HEADER)).isEqualTo(new Color(0xDFE2E9));
        var dark = theme.generated(true);
        assertThat(SurfaceContrast.painted(dark, ChromePainter.FOCUSED_HEADER)).isEqualTo(DerivedColor.SURFACE_HEADER_ACTIVE.color(dark))
            .isNotEqualTo(DerivedColor.SURFACE_HEADER.color(dark));
    }
```

(Use `theme.schemes()`'s real id accessor, which `ConvertedThemesTest` shows as `scheme.id().key()` for the key alone.)

- [ ] **Step 7: Run the whole jasper-ui and jasper-app suites and fix what follows the theme**

Run: `./gradlew :jasper-ui:test :jasper-app:test --continue`

Expected failures, and only these kinds; fix each one this way:

- (a) **A guarded ink compared with a role.** Compare it with `MockProfile.painted(dark, <the face's pair>)`.
- (b) **Two adjacent bands or runs that now share a value**, for example the Light menu row and toolbar row, both #F2F2F2, now separated only by their rule. Measure the boundary by the rule or the component's bounds, and add a comment naming the user's 2026-10-18 direction.
- (c) **An edge agreement under its floor.** Set the floor to the measured value less 0.03, with a comment `// The user's 2026-10-18 direction: the IntelliJ Light (Dark) port; measured <value>.`.
- (d) **A test that pins old theme facts**, for example `PluginThemesTest`, `KitFixturesTest` names or `PaintedContrastTest`'s Enterprise repairs. Update the pin to the port's value.

Record every (b), (c) and (d) change, as file and one line each, for Task 12's deviation list. Do not change a geometry expectation.

- [ ] **Step 8: Attribution, `check_pairs.py` and `pairs.json`**

- `intellij-themes.NOTICE.txt`:

```text
The Enterprise theme's Light and Dark variants are ports of IntelliJ IDEA's "IntelliJ Light" and New UI "Dark" themes and their
editor colour schemes, converted by Jasper's tools/themes/jasper_theme.py (the colour mapping is Jasper's).
Copyright 2000-2026 JetBrains s.r.o. and contributors.
Licensed under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
Source: https://github.com/JetBrains/intellij-community, platform/platform-resources/src/themes/Light.theme.json,
intellijlaf.theme.json, darcula.theme.json, Light.xml, expUI/expUI_dark.theme.json, expUI/expUI_darkScheme.xml and
platform/platform-resources/src/DefaultColorSchemesManager.xml, at commit f7377708b654b73b206da40bb382ecb4d44e8f12; the
port is identical at tag idea/2026.2.3 (commit 8062c061ff33e22041d9f1844db5b8b8a6b5e4d7).

NOTICE (from the upstream NOTICE.txt):
This software includes code from IntelliJ IDEA
Copyright (C) JetBrains s.r.o.
https://www.jetbrains.com/idea/
```

- `notices.toml`: a new component (keep the file's alphabetical neighbourhood):

```toml
[[component]]
name = "IntelliJ Platform themes (the Enterprise theme)"
version = ""
license = "Apache-2.0"
homepage = "https://github.com/JetBrains/intellij-community"
text = "apache-2.0.txt"
notice = "intellij-themes.NOTICE.txt"
```

- `tools/themes/enterprise/SOURCE.md`: four short paragraphs, as `tools/themes/catppuccin/SOURCE.md` does:
  - what port.json is;
  - which IntelliJ files each variant converts, with both commits;
  - the port rules (exact keys, chosen roles, overrides) and why the accents (Rulings 15);
  - that `test_jasper_theme.py` compares the conversion with `plugins/enterprise/.../enterprise.json` byte for byte, and `JASPER_THEME_UPDATE_GOLDEN=1` rewrites it.
- `check_pairs.py`: a colour in `"colours"` is looked for in the render only (`present(render_image, colour, RENDER_TOLERANCE, lines=True)`). The docstring's sentence becomes "every colour in "colours" is present in the render, which paints theme roles exactly, within Delta E 1.5 … (since the user's 2026-10-18 direction the Enterprise theme ports IntelliJ's, so the colours are the theme's, not the mocks')". Delete the mock check and `MOCK_TOLERANCE`.
- Re-theme `pairs.json`'s colours after rendering. First render: `./gradlew :jasper-app:test --tests '*Enterprise*' --tests '*Classic*' --tests '*Dock*' --tests '*RunWindow*' --tests '*Settings*Mock*' --tests '*ChromeRender*'`, plus `./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=light -Pjasper.galleryScale=2` and the same with `dark`. Then:

```bash
git show faeec76f3:plugins/enterprise/src/main/resources/themes/enterprise.json > "$TMPDIR/old-theme.json"
git show faeec76f3:jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles-enterprise.tsv > "$TMPDIR/old-derived.tsv"
python3 -I - "$TMPDIR/old-theme.json" "$TMPDIR/old-derived.tsv" <<'PY'
import json, sys, pathlib
sys.path.insert(0, "tools/visual"); import check_pairs as cp   # present(), crop()
old = json.load(open(sys.argv[1])); new = json.load(open("plugins/enterprise/src/main/resources/themes/enterprise.json"))
def table(theme, tsv):
    out = {v: dict((k, c.upper()) for k, c in theme["ui"][v].items()) for v in ("light", "dark")}
    for line in open(tsv):
        if line.startswith("#"): continue
        _, v, role, colour = line.rstrip("\n").split("\t"); out[v].setdefault(role, colour.upper())
    return out
before = table(old, sys.argv[2]); after = table(new, "jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles-enterprise.tsv")
path = pathlib.Path("tools/visual/pairs.json"); pairs = json.loads(path.read_text(encoding="utf-8"))
for pair in pairs:
    if "colours" not in pair: continue
    v = "dark" if "dark" in pair["name"] else "light"
    render = cp.crop(cp.ROOT / pair["render"], pair.get("render_crop"))
    wanted = []
    for colour in pair["colours"]:
        roles = [r for r, c in before[v].items() if c == colour.upper()]
        for candidate in sorted({after[v][r] for r in roles if r in after[v]}) or [colour.upper()]:
            if cp.present(render, candidate, cp.RENDER_TOLERANCE, lines=True) and candidate not in wanted: wanted.append(candidate)
    pair["colours"] = wanted
path.write_text(json.dumps(pairs, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
PY
python3 -I tools/visual/check_pairs.py "$TMPDIR/pairs"
```

Expected: every gated pair passes. Where an edge agreement falls under its floor, apply (c) above to that entry and add a `"note"` (`"The user's 2026-10-18 direction: the IntelliJ Light (Dark) port. Edge agreement <before> before, <after> after."`). A pair left with no colours keeps `[]` and gets a note. Check that the JSON keeps its two-space layout (`git diff --stat tools/visual/pairs.json`).

- [ ] **Step 9: Run the checks**

Run: `python3 -I -m unittest discover -s tools/themes -p 'test_*.py' && ./gradlew :jasper-ui:test :jasper-app:test :jasper-app:verifyThirdPartyNotices --continue`
Expected: OK, and BUILD SUCCESSFUL.

- [ ] **Step 10: Commit** — `Enterprise theme: ports of IntelliJ Light and New UI Dark`, with a body naming port.json, the accents, #DFE2E9, the dropped `dark-terminal` and the attribution, and the trailers.

### Task 10: A pressed tool window is the active one

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowArea.java`, `WindowContributions.java:192`
- Test: create `jasper-app/src/test/java/dev/jasper/app/workspace/PressedFrameTest.java`

**Interfaces:**
- Produces, all package-private in `ToolWindowArea`:
  - `void pressed(Component target)`;
  - `void activate(String id)`;
  - `java.util.function.Consumer<Component> focuser`, `Component::requestFocusInWindow` by default and replaced by tests;
  - `Component focusTarget(Slot slot)`.

  `WindowContributions.togglePanel` calls `owner.area().activate(id)` after a toggle that shows the panel.

- [ ] **Step 1: Write the failing tests**

```java
/** Plan 12 Rulings 18 (spec 10.7): any press inside a flush frame activates it, as IntelliJ activates a tool window. */
@ExtendWith(EdtTestExtension.class)
class PressedFrameTest {
    final List<Component> focused = new ArrayList<>();
    final JPanel terminal = new JPanel();

    ToolWindowArea area(boolean flush, JComponent top) {
        EnterpriseLook.install(false);
        var area = new ToolWindowArea(terminal, new DockState(), EnterpriseLook.kit(), flush);
        area.focuser = focused::add;
        DockTestSupport.show(area, Slot.LEFT_TOP, top, 300);
        DockTestSupport.show(area, Slot.RIGHT_TOP, new JLabel("right"), 350);
        MockCrops.render(area, 1200, 763, 1);
        return area;
    }

    static void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }

    /** Review Focus 1. */
    @Test void aPressOnBlankSpaceOrTheHeaderStripActivatesTheFrame() throws Exception {
        try {
            var area = area(true, new JLabel("top"));
            area.pressed(area.content(Slot.LEFT_TOP).getParent());
            assertThat(area.focusedSlot()).hasValue(Slot.LEFT_TOP);
            assertThat(area.headerOf(Slot.LEFT_TOP).focused()).isTrue();
            area.pressed(area.headerOf(Slot.RIGHT_TOP));
            assertThat(area.focusedSlot()).hasValue(Slot.RIGHT_TOP);
            assertThat(area.headerOf(Slot.LEFT_TOP).focused()).isFalse();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void thePressMovesTheKeyboardToTheFramesFirstKeyedControlOrTheFrame() throws Exception {
        try {
            var content = new JPanel(new BorderLayout());
            var list = new JList<>(new String[] {"Mac"});
            content.add(new JLabel("Hosts"), BorderLayout.NORTH); content.add(list, BorderLayout.CENTER);
            var area = area(true, content);
            area.pressed(content);
            flushEdt();
            assertThat(focused).containsExactly(list);
            focused.clear();
            area.pressed(area.content(Slot.RIGHT_TOP));
            flushEdt();
            assertThat(focused).as("nothing keyed: the frame itself").containsExactly(area.content(Slot.RIGHT_TOP).getParent());
            assertThat(focused.getFirst().isFocusable()).isTrue();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Review Focus 1. */
    @Test void aPressInTheTerminalHandsTheTintBack() throws Exception {
        try {
            var area = area(true, new JLabel("top"));
            area.pressed(area.content(Slot.LEFT_TOP));
            area.pressed(terminal);
            assertThat(area.focusedSlot()).isEmpty();
            assertThat(area.headerOf(Slot.LEFT_TOP).focused()).isFalse();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void aRealMousePressReachesTheArea() throws Exception {
        try {
            var area = area(true, new JLabel("top"));
            var label = area.content(Slot.LEFT_TOP);
            area.addNotify();
            label.dispatchEvent(new MouseEvent(label, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, 2, 2, 1, false, MouseEvent.BUTTON1));
            assertThat(area.focusedSlot()).hasValue(Slot.LEFT_TOP);
            area.removeNotify();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void openingFromTheStripeActivatesTheFrame() throws Exception {
        try {
            var area = area(true, new JLabel("top"));
            area.activate(DockTestSupport.id(Slot.RIGHT_TOP));
            flushEdt();
            assertThat(area.focusedSlot()).hasValue(Slot.RIGHT_TOP);
            assertThat(focused).containsExactly(area.content(Slot.RIGHT_TOP).getParent());
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void standardFramesIgnorePresses() throws Exception {
        try {
            var area = area(false, new JLabel("top"));
            area.pressed(area.content(Slot.LEFT_TOP));
            area.activate(DockTestSupport.id(Slot.LEFT_TOP));
            flushEdt();
            assertThat(area.focusedSlot()).isEmpty();
            assertThat(focused).isEmpty();
        } finally { EnterpriseLook.restoreDefault(); }
    }
}
```

In `aRealMousePressReachesTheArea`, `addNotify` needs no peer for the `AWTEventListener` registration; if it fails without a showing parent, mount the area in a `JRootPane` as `ChromeRenderTest` does. Keep the assertion: the press goes through `Component.dispatchEvent`, whose `Toolkit` listeners are the path the production code uses. Add a `WindowContributions` assertion where its tests toggle a panel (`WindowPanelsTest`): after `togglePanel` shows it under flush frames, `owner.area().focusedSlot()` is the panel's slot.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests '*PressedFrameTest' --tests '*WindowPanelsTest' --tests '*FlushFramesTest'`
Expected: compilation fails (`pressed`, `activate`, `focuser`).

- [ ] **Step 3: Implement** in `ToolWindowArea`

- Add a field `java.util.function.Consumer<Component> focuser = Component::requestFocusInWindow;` and an `AWTEventListener pressListener` that calls `pressed` for a `MOUSE_PRESSED` whose source is a `Component` descending from this area.
- `addNotify`/`removeNotify` add and remove the listener with `Toolkit.getDefaultToolkit().addAWTEventListener(pressListener, AWTEvent.MOUSE_EVENT_MASK)` when `flush`.
- Each host is `setFocusable(true)` under flush frames, where it is created.
- Refactor `focusMoved`'s tail into `private void mark(Slot slot)`, which sets `focused` and calls `focused(...)` on every host.
- `pressed(target)`:
  - return unless `flush`;
  - find the slot whose host holds `target`; none gives `mark(null)` and a return;
  - otherwise `mark(slot)`, then `SwingUtilities.invokeLater(() -> { if (focused == slot && !inside(currentPermanentFocusOwner, host)) focuser.accept(focusTarget(slot)); })`.
- `activate(id)`: the host whose `id` equals `id`, if any and under flush frames; `mark(slot)` and the same deferred focus.
- `focusTarget(slot)`: a depth-first walk of the host's content for the first component that is showing, enabled and focusable and is a `JComponent` whose `getInputMap(JComponent.WHEN_FOCUSED).allKeys()` is not empty; else the host.
- The class comment for 10.7 gains: "Under flush frames a frame is also activated by any press inside it or by its opening, which moves the keyboard into it."

In `WindowContributions.togglePanel`: `if (panels.containsKey(id)) { dock.toggle(id); if (dock.visible(id)) owner.area().activate(id); }`. Add a `migration.csv` row for `PressedFrameTest` (test ownership, Task 10).

- [ ] **Step 4: Run the tests**

Run: the Step 2 command, then `./gradlew :jasper-app:test --tests '*Dock*' --tests '*HeaderStrip*' --tests '*ToolWindow*' --tests '*DefaultDockPixelsTest'`
Expected: PASS. Standard frames are untouched, so `DefaultDockPixelsTest` is unchanged.

- [ ] **Step 5: Commit** — `Dock: a press inside a flush frame, or its opening, activates it`, with the trailers.

### Task 11: The Run window's upper home

**Files:**
- Rename: `jasper-app/src/main/java/dev/jasper/app/workspace/CornerHome.java` to `UpperHome.java` (`git mv`), and `CornerHomeTest.java` to `UpperHomeTest.java`
- Modify: `PaletteLook.java` (`Home { TITLE_FIELD, UPPER }`), `Projections.java`, `WorkspaceChrome.java`, `PaletteHome.java` (comments and `permits`), `DesignMetrics.java` (remove `RUN_WINDOW_HOME_SIDE`, `RUN_WINDOW_HOME_BOTTOM` and their comment), both role tables (remove the two rows), `RunWindowRolesTest`, `RunWindowWorkspaceRenderTest`, `ProjectionReadTest` (`PALETTE_STRATEGIES`), `ProjectionsTest`, `PaletteStrategiesTest`, `RunWindowModelessTest`, `docs/design/theme-engine/migration.csv` (`:1402-1403` renamed: paths, names and "the Run window's upper home; renamed from CornerHome in Enterprise plan 12 Task 11")

**Interfaces:**
- Produces:
  - `final class UpperHome implements PaletteHome`;
  - `PaletteLook.RUN_WINDOW = new PaletteLook(View.RUN_WINDOW, Home.UPPER)`;
  - `static final int UpperHome.TOP_FRACTION = 7`, a seventh down.

- [ ] **Step 1: Write the failing tests** in `UpperHomeTest`

Keep `chrome`, `owner`, `margin`, `mouse`, `grab`, `Opened` and `open`; remove `side()` and `bottom()`.

```java
    /** The user's request (5): centred across the window, a seventh down, entirely in the upper half. */
    @Test void theRunWindowOpensCentredInTheUpperPartOfTheWindow() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(1400, 900)) {
                var ws = o.workspace();
                assertThat(o.window().getX() + o.window().getWidth() / 2.0).isCloseTo(ws.x + ws.width / 2.0, within(1.0));
                assertThat(o.window().getY()).isEqualTo(ws.y + ws.height / UpperHome.TOP_FRACTION);
                assertThat(o.window().getY() + o.window().getHeight()).isLessThanOrEqualTo(ws.y + ws.height / 2);
                assertThat(o.window().getY()).isGreaterThanOrEqualTo(o.work().y + margin());
            }
        });
    }

    @Test void aWindowTooTallForASeventhDownEndsAtTheMiddle() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(1400, 620)) {
                var ws = o.workspace();
                assertThat(ws.y + ws.height / UpperHome.TOP_FRACTION + o.window().getHeight()).as("a seventh down would cross the middle")
                    .isGreaterThan(ws.y + ws.height / 2);
                assertThat(o.window().getY() + o.window().getHeight()).isEqualTo(Math.max(ws.y + ws.height / 2, o.work().y + margin() + o.window().getHeight()));
            }
        });
    }

    /** Review Focus 2. */
    @Test void aShortWindowKeepsTheRunWindowInsideTheWorkArea() throws Exception {
        DesktopTestSupport.edt(() -> {
            for (var size : new java.awt.Dimension[] {new java.awt.Dimension(1400, 380), new java.awt.Dimension(560, 900)})
                try (var o = open(size.width, size.height)) {
                    var work = o.work();
                    var bounds = o.window().getBounds();
                    assertThat(bounds.y).as(size + " never above the work area").isGreaterThanOrEqualTo(work.y + Math.min(margin(), work.height / 2));
                    assertThat(bounds.x).isGreaterThanOrEqualTo(work.x);
                    assertThat(bounds.x + bounds.width).isLessThanOrEqualTo(work.x + work.width);
                    assertThat(bounds.y + bounds.height).isLessThanOrEqualTo(work.y + work.height);
                }
        });
    }
```

Adapt the drag test (renamed `aDragAttachesNearItsHomeAndElsewhereSavesItsPlace`):
- the first drop (300 across, 250 up, which clamps inside) saves `left` and `bottom` as before;
- dragging back to `(dx, dy + 80)` stays (80 is beyond reach below home);
- `(dx + 12, dy + 12)` snaps home;
- the double click returns home as before.

Keep `aSavedPlaceFromALargerWindowStaysInside` and the list-fit test.

In `RunWindowWorkspaceRenderTest`, replace the four placement assertions (6 and 5, the mock's x 60 and y 1696) with:
- the window's centre within 1 of the workspace's centre;
- its top at `workspace.y + workspace.height / UpperHome.TOP_FRACTION`;
- `2 * stripAt.y == 1706` kept;
- a comment naming the user's 2026-10-18 direction.

The crop comparison is unchanged, since it crops the window where it stands. In `RunWindowRolesTest`, drop the two roles from the expected list and their value assertions.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests '*UpperHomeTest' --tests '*RunWindowWorkspaceRenderTest' --tests '*ProjectionReadTest' --tests '*PaletteStrategiesTest' --tests '*RunWindowModelessTest' :jasper-ui:test --tests '*RunWindowRolesTest'`
Expected: compilation fails (`UpperHome`, `Home.UPPER`).

- [ ] **Step 3: Implement `UpperHome`**

```java
/**
 * The Run window's home (spec 11.5, the user's 2026-10-18 direction): centred across the workspace with its top a seventh of
 * the workspace's height down, moved up to end at the workspace's middle while that keeps it in the dock work area, and
 * clamped into the work area by the margin, at the window's own size. A drag that ends within reach of it in both directions
 * re-attaches; there are no guides; the place is saved as {@code [palette.run_window] left/bottom}, fractions of the workspace.
 */
final class UpperHome implements PaletteHome {
    /** The home's top is the workspace's height over this, down from the workspace's top. */
    static final int TOP_FRACTION = 7;

    @Override public Rectangle home(Geometry g, Dimension preferred) {
        Rectangle work = g.workArea(), ws = g.workspace();
        if (work == null || work.width <= 0 || work.height <= 0) return null;
        Dimension size = WindowCommandPalette.fitted(work, preferred, g.margin());
        int top = ws.y + ws.height / TOP_FRACTION, middle = ws.y + ws.height / 2;
        if (top + size.height > middle) top = middle - size.height;
        return WindowCommandPalette.inside(new Rectangle(ws.x + (ws.width - size.width) / 2, top, size.width, size.height), work, g.margin());
    }

    /** Within {@code reach} of its home across and down. */
    @Override public boolean attaches(Rectangle home, Rectangle bounds, int reach) {
        return Math.abs(bounds.x - home.x) <= reach && Math.abs(bounds.y - home.y) <= reach;
    }
    // guides(), detachedSize, saved, save, forget, fallback and available: CornerHome's bodies, unchanged.
}
```

Then:
- `PaletteLook.Home.UPPER`, and its comment says "centred in the window's upper part";
- `Projections` uses `PaletteLook.Home.UPPER`, `WorkspaceChrome` uses `case UPPER -> new UpperHome();`, and `PaletteHome` uses `permits TitleFieldHome, UpperHome`, with "or centred in the window's upper part ({@link UpperHome})";
- remove the two roles and their rows.

- [ ] **Step 4: Run the tests**

Run: the Step 2 command, then `./gradlew :jasper-ui:test --tests '*DesignMetricsTest' --tests '*ClassicCatalogTest' :jasper-app:test --tests '*Palette*' --tests '*RunWindow*' --tests '*ThemeMigrationCoverageTest'`
Expected: PASS.

- [ ] **Step 5: Commit** — `Run window: its home is centred across the window, a seventh down`, with the trailers.

### Task 12: Documentation, STATUS and the whole build

**Files:**
- Modify:
  - `docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`;
  - `docs/plugin-ui.md`, `docs/plugin-authoring.md`, `docs/sdk-icons.md`, `jasper-sdk/README.md`;
  - `docs/themes.md`, `docs/configuration.md`, `docs/command-palette.md`, `docs/app-architecture.md`;
  - `docs/STATUS.md`; this plan's status banner.

- [ ] **Step 1: Amend the spec** (each edit is the smallest accurate one)

- **Header:** after the 2026-10-18 stripes line, add `Amended on 2026-10-18 at the user's direction after trying Enterprise (plan 12): 1 (decisions 4 and 14), 5.2, 5.8, 7.6 (the theme ports IntelliJ Light and New UI Dark; plan 5b's accent ruling is superseded), 7.7, 8.6, 10.3, 10.7, 11.5, 14.4 and 19.1.`
- **Decision 4:** "the toolbar "Session: [combo] [Connect]"" becomes "the toolbar's session combo and Run button".
- **Decision 14:** "Light comes from DESIGN_LANGUAGE §2; it ships a light terminal scheme and a `dark-terminal` scheme. Dark comes from the Dark Mode mock (7.6)." becomes "Since 2026-10-18 (the user) Light is a port of IntelliJ's IntelliJ Light and Dark of its New UI Dark, each with its terminal scheme (7.6)."
- **5.2:** "Twenty-three points: 9 chrome points" becomes "Twenty-four points: 10 chrome points". Add the table row `` | `icons` | `new_ui` | `classic` | The workspace chrome's icons: IntelliJ's classic icons in the header and status bar (10.3) | `` after `motion`.
- **5.1:** add `icons = "classic"` wherever the section shows the `[chrome]` table, and `"window.toolbar" = "icons"` in its `[defaults]` (check with `grep -n 'motion = \|toolbar_height' docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`).
- **5.8:** "an allowlist of four keys: `ui.theme.light`, `ui.theme.dark`, `palette.visible_rows` and `window.toolbar_height`" becomes "an allowlist of five keys: `ui.theme.light`, `ui.theme.dark`, `palette.visible_rows`, `window.toolbar_height` and `window.toolbar`". "say for the four keys" becomes "say for the five keys".
- **7.6:** replace the section body (from "`themes/enterprise.json`, named" to the end of the Dark paragraph) with:

```markdown
`themes/enterprise.json`, named "Enterprise", id `enterprise`, with `ui.light`, `ui.dark` and the terminal schemes `light`
and `dark`. At the user's direction (2026-10-18) both variants are ports of IntelliJ's own themes: `convert_port` in
`tools/themes/jasper_theme.py` builds the file from `tools/themes/enterprise/port.json` and IntelliJ's theme files in
`tools/themes/intellij/` (JetBrains/intellij-community, Apache-2.0; the port is identical at tag idea/2026.2.3, commit
`8062c061ff33`), and `test_jasper_theme.py` holds the file to it.

- **Light** is "IntelliJ Light": `Light.theme.json` over `intellijlaf.theme.json` and `darcula.theme.json`, with the editor
  scheme `Light.xml`.
- **Dark** is the New UI "Dark": `expUI/expUI_dark.theme.json` over `darcula.theme.json`, with the editor scheme
  `expUI/expUI_darkScheme.xml`.

Each variant is the converter's output for its theme, its terminal scheme the scheme's console colours. An override key is
filled when IntelliJ's own theme sets its key exactly (not through a `*.` wildcard); port.json chooses three roles and one
override. Key values:

| Role | Light | Dark |
|---|---|---|
| `surface.app`, `surface.dialog` | #F2F2F2 | #2B2D30 |
| `surface.panel`, `surface.card` | #FFFFFF | #2B2D30 |
| `surface.terminal` | #FFFFFF | #1E1F22 |
| `text.primary` | #000000 | #DFE1E5 |
| `accent` | #2675BF | #366ACE |
| `selection.bg` | #2675BF | #2E436E |
| `border.window` | #D1D1D1 | #1E1F22 |
| `border.control` | #C4C4C4 | #4E5157 |
| `surface.header` | #F2F2F2 | #2B2D30 |
| `surface.header.active` | #DFE2E9 | <Task 9's recorded value> (its recipe) |

port.json's choices: Light's `accent` is IntelliJ Light's `List.selectionBackground` and its `surface.card` IntelliJ's
`contentBackground`; Dark's `accent` is the palette's `Blue5`. IntelliJ's default-button blues (#528CC7, #3574F0) give
white text 3.54:1 and 4.28:1, which the TEXT guard would paint black; these give 4.80:1 and 5.11:1. Light's
`surface.header.active` is the user's #DFE2E9 (IntelliJ Light's own is #E2E6EC). New UI Dark sets no exact
`ToolWindow.Header.background`, so Dark's follows its recipe. `derived-roles-enterprise.tsv` freezes every derived role.
```

- **7.7:** add one sentence at its end: "`convert_port` (7.6) converts IntelliJ's own themes for the Enterprise theme and also fills the override keys they set exactly."
- **New 8.6 after 8.5:**

```markdown
### 8.6 SDK 0.18.1

An additive release (the user, 2026-10-18): `ToolbarItem.View` admits, besides at most one PRIMARY Button, any number of
icon-only GHOST Buttons (a blank label with an icon and a tooltip), drawn flat until hovered with their glyph at rail size;
and two authored built-in icon names, `run` (IntelliJ's classic Run triangle) and `application` (its run-configuration icon),
host artwork in both looks. Only Remote's manifest moves to `sdk = ">=0.18.1, <0.19"`.
```

- **10.3:**
  - In the first paragraph, `"+ New Session" (NEW_TAB), "Split ▾" (today's Split dropdown), "Maximize" (ZOOM_PANE)` becomes `New Session (NEW_TAB), Split (today's Split dropdown), Maximize (ZOOM_PANE)`.
  - The second paragraph's first sentence becomes: "The classic header honours `window.toolbar`. `icons` (Enterprise's default, 5.8) gives 22 px icon buttons whose tooltips are their labels, Split marking its menu with a small ▼ in its corner. `icons_and_labels` gives each button's icon, then its label (Split ending in ▾). `hidden` gives no row, so the Session view and Search everywhere go with it, and the Run window stays reachable by ⇧⌘P and "▶ Run…". The icons are the look's `[chrome] icons` set (5.2), IntelliJ's classic icons under Enterprise."
  - The admission bullet's "at most one PRIMARY Button," becomes "at most one PRIMARY Button, icon-only GHOST Buttons (8.6),".
- **10.7:** after the sentence defining the focused header strip (find it with `grep -n "focused" docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md | sed -n '1,40p'` inside 10.7), add: "A frame is also the focused one after any mouse press inside it (its content, blank space, its header strip, a control that takes no focus) and when it opens from its stripe or its toggle action; both move the keyboard into it (its first control that takes keys, else the frame), as IntelliJ activates a tool window, and a press elsewhere in the tool window area hands the tint back."
- **11.5:**
  - In the second sentence, `CornerHome` becomes `UpperHome`.
  - The `CornerHome` bullet becomes: "- `UpperHome` is centred across the window's workspace with its top a seventh of the workspace's height down, moved up to end at the workspace's middle while that keeps it in the dock work area, and clamped into the work area by `PALETTE_WINDOW_MARGIN` (the user, 2026-10-18), `RUN_WINDOW_WIDTH` wide. It attaches within reach of its home across and down: `|left − home.left| ≤ reach && |top − home.top| ≤ reach`. It has no centre or top-fifth guides; home is its only snap target. Double-clicking the title row forgets the saved place and returns home."
  - The saved-place paragraph's `CornerHome` becomes `UpperHome`.
- **14.4:** the toolbar view bullet's text up to "Choosing" becomes: "`SessionsToolbar` contributes a View in place of its Sessions dropdown: a DROPDOWN drawn as IntelliJ's run-configuration combo (its face the chosen row's label and the `application` icon) listing saved hosts as "name (address)", WSL distributions as "name (detail)", a separator and "Manage Sessions…", and an icon-only GHOST `run` button, tooltip "Connect", that runs `activateHost` for a host (`RemotePlugin.java:368-375`: focus a running pane for it, else open one) or `openDistro` for a distribution."
- **19.1:** "Eleven plans" becomes "Twelve plans". Add the row: `` | 12 | `intellij-classic` | The user's 2026-10-18 direction: IntelliJ's classic icons (vendored, attributed) under `[chrome] icons`, the classic header's icon buttons and `window.toolbar` as a look default, SDK 0.18.1's icon-only GHOST toolbar buttons with `run` and `application`, Remote's run-configuration combo and Run, the Enterprise theme as ports of IntelliJ Light and New UI Dark, a pressed tool window activated, the Run window's upper home | ``.

- [ ] **Step 2: Correct the guides**

```bash
grep -n "PRIMARY" docs/plugin-ui.md docs/plugin-authoring.md | grep -i toolbar
grep -n "built-in\|authored" docs/sdk-icons.md | head
grep -n "0.18.0" jasper-sdk/README.md | head -3
grep -n "Enterprise" docs/themes.md | head -3
grep -n "window.toolbar\`\|palette.run_window\|bottom-left" docs/configuration.md docs/command-palette.md
grep -n "CornerHome" docs/app-architecture.md
```

For each hit:
- The toolbar view rule gains icon-only GHOST buttons (SDK 0.18.1).
- `docs/sdk-icons.md` names `run` and `application` as authored host artwork, coloured, in both looks.
- `jasper-sdk/README.md`'s version line starts "Bundled manifests require `sdk = ">=0.18.0, <0.19"`; 0.18.1 adds icon-only GHOST buttons in toolbar views and the `run` and `application` icons (Remote requires it);".
- `docs/themes.md`'s Enterprise sentence becomes "brings **Enterprise** (Light and Dark, ports of IntelliJ's IntelliJ Light and New UI Dark)".
- `docs/configuration.md`'s `window.toolbar` row gains "; the default depends on the active look".
- `docs/command-palette.md:43` places the Run window "centred across the window, in its upper part".
- `docs/app-architecture.md:161` reads `UpperHome`.

- [ ] **Step 3: Prove the default look unchanged**

```bash
base=$(mktemp -d)
git worktree add --detach "$base/main" faeec76f3
for theme in dark light; do
  ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=$theme && rm -rf "$base/branch-$theme" && cp -R jasper-ui/build/gallery "$base/branch-$theme"
  (cd "$base/main" && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=$theme) && rm -rf "$base/main-$theme" && cp -R "$base/main/jasper-ui/build/gallery" "$base/main-$theme"
  diff -r "$base/main-$theme" "$base/branch-$theme" && echo "$theme: identical"
done
git worktree remove --force "$base/main"
```

Expected: `dark: identical` and `light: identical`, with no `diff` output (322 files).

- [ ] **Step 4: Run the whole build and count**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Then:

```bash
python3 -I - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = e = s = 0
for p in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    r = ET.parse(p).getroot(); t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
print(f"{t} tests, {f} failures, {e} errors, {s} skipped")
PY
```

Expected: 0 failures and 0 errors; record the counts (the base is 11,437 tests, 25 skipped).

- [ ] **Step 5: Render the Enterprise gallery and check the pairs**

```bash
for theme in light dark; do ./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=$theme -Pjasper.galleryScale=2; done
python3 -I tools/visual/check_pairs.py "$(mktemp -d)"
```

Expected: every gated pair PASS (record "N of N").

- [ ] **Step 6: Write `docs/STATUS.md`'s new top entry and this plan's banner**

The entry starts `**Enterprise presentation, plan 12: IntelliJ classic at the user's direction — 2026-10-18** (`claude/enterprise-intellij-classic`, worktree `.claude/worktrees/enterprise-12`, from `main` at `faeec76f3`; [plan](superpowers/plans/2026-10-18-enterprise-12-intellij-classic.md), [spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md))`. Then, in the series' style, give:
- the five requests and what each became;
- the rulings (this plan's 20, one clause each);
- Task 9 Step 7's deviations (file and one line each), with every re-measured floor;
- the recorded Dark `surface.header.active`;
- the check counts and "N of N" pairs, and the byte-identical gallery;
- the GUI checks for the user:
  - under Enterprise, the toolbar's classic icons in Light and Dark, with tooltips and Split's menu;
  - View › Toolbar's Icons & Labels;
  - the session combo against IntelliJ's (a long host name, no sessions);
  - Run's hover and disabled states, connecting by Run;
  - clicking blank space, a header strip and a stripe label in each tool window (the tint, and typing goes there), then clicking the terminal;
  - the Light focused header against the user's screenshot (#DFE2E9);
  - Enterprise Light and Dark as a whole against IntelliJ Light and New UI Dark;
  - the Run window opening centred in the upper part, dragged away and back, in a short window;
  - the About page's IntelliJ entries;
- end with "Not merged, not pushed."

Add a status banner under this plan's title: `> **Status (2026-10-18): executed on `claude/enterprise-intellij-classic` (`<first>..<last>`).** Deviations from this plan's text: <list, or none>.`

- [ ] **Step 7: Check the edits and commit**

```bash
python3 -I - <<'PY'
import pathlib
for root in ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins", "jasper-ui/src"):
    for p in pathlib.Path(root).rglob("*.java"):
        text = p.read_text(encoding="utf-8")
        bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
        if bad: print(p, "bad chars:", bad)
PY
git add docs jasper-sdk/README.md
git commit -m "Docs: Enterprise plan 12, IntelliJ classic, at the user's direction

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

Expected: the hygiene script prints nothing, and the commit succeeds.
