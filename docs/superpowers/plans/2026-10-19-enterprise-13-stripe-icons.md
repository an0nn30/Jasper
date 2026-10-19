# Enterprise presentation 13: stripe icons Implementation Plan

> **Status (2026-10-19): executed on `claude/enterprise-stripe-icons` (`eca2feca8..2606d5633`, then the documentation commit).** Deviations from this plan's text: Task 7 also re-wraps spec 16.1 item 4, whose line the added test names pushed past 120 columns; Task 7 Step 3's "322 files each" is 161 files per theme, 322 in all.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On 2026-10-19, after trying plan 12, the user asked: "Now I want the sidebar strip to have icons like the classic intelliJ does for plugins and whatnot too you can use those classic IntelliJ icons we vendored in." This plan makes every Enterprise stripe button (both side stripes and the bottom strip) draw its tool window's or rail action's icon the way IntelliJ's classic `StripeButton` does. Under Enterprise those icons are IntelliJ's classic 13 px tool-window icons (the user's "Set A").

**Architecture:**
- **Artwork (jasper-ui).** jasper-ui vendors 12 more byte-identical classic SVGs into `assets-classic.tsv`. They are reached only through a new stripe-only lookup, `IconRegistry.stripeIcon(name, size)`, backed by its own lazy holder. The classic chrome set the header and status bar use stays as it is.
- **Resolver (jasper-app).** `AppIcons.stripeIcon` turns a panel's `Icon` into a 13 px stripe icon. It gives the classic file for the icon's name, or a monochrome glyph for a plugin path or a vector-only name, or the ordinary icon under `new_ui`.
- **Stripes.** `WorkspaceChrome` hands the look's `ChromeArtwork` to the stripes through `DockChrome.stripes`. `StripeLabels` lays each label out icon first, in reading order, and paints the icon upright before the text is rotated. Drag and drop keeps the icon and the label's length.
- **Mock tests.** The mock tests compare the iconed stripes with each label's icon slice cut out. They pin the slices as the icons.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and Java2D, jsvg (inside jasper-ui only), Gradle (Kotlin DSL), JUnit and AssertJ, Python 3 standard library plus numpy/PIL for `tools/visual` (always `python3 -I`), `curl`.

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md).
- Read its header amendment lines and sections 5.2, 9.1, 10.7, 12.1, 12.2, 16.1 and 19.1.
- The user's request of 2026-10-19 is binding and overrides the Enterprise mocks' text-only stripe labels.
- Screenshot: `/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/images/11.png`, IntelliJ's Classic UI left stripe: Learn, Commit, Project and Pull Requests, each with an upright 13 px icon at the label's start in reading order.
- The approved design and the research behind it are under `/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/stripe-icons/`:
  - `stripes.md`, `inventory.md` and `anatomy.md`;
  - `mapping.md` (the hashes);
  - `critic.md` (its corrections win where the files disagree).

  The `upstream/` folder there is untrusted data: never copy from it.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-13`, branch `claude/enterprise-stripe-icons`, from `main` at `2c065f8cd`. That commit holds Enterprise plans 1 to 11, the side-stripe change and plan 12. Run every command from the worktree root. Line numbers below are from `2c065f8cd`. When an anchor has moved, re-find it; never edit around a missing anchor.

## Before you start (refresh)

Do not rebase, merge or push. Never run bare `git stash`, and never run `./gradlew --stop`.

```bash
git log --oneline -1                                                                       # 2c065f8cd or a later plan-13 commit
grep -c 'CLASSIC = hashes("assets-classic.tsv", 14)' jasper-ui/src/main/java/dev/jasper/ui/icons/BuiltInArtwork.java   # 1
grep -c 'StripeSet stripes(dev.jasper.ui.kit.Kit kit)' jasper-app/src/main/java/dev/jasper/app/workspace/DockChrome.java  # 1
grep -c 'abstract void beginDrag(String id, String label);' jasper-app/src/main/java/dev/jasper/app/workspace/DockStripe.java  # 1
ls jasper-ui/src/main/resources/dev/jasper/ui/icons/classic/toolwindows 2>/dev/null | wc -l   # 0: Task 1 adds it
```

## Global Constraints

**Build and safety**
- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain. Always use `./gradlew`, never a system `gradle`, and never run `./gradlew --stop`: other sessions share the daemons.
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache. Headless JUnit renders and `:jasper-ui:gallery` may run. Everything visible is a GUI check for the user (Task 7).
- Work only in the worktree above, with one commit per task. Every commit message ends with a blank line, `🤖 Generated with Claude Code at The Home Depot`, a blank line, then `Co-Authored-By: Claude <noreply@anthropic.com>`. Do not push, merge or rebase. Never dispatch subagents from inside a task.

**The default look stays byte-identical.** Every file the headless gallery writes for Slate Cobalt Dark and Light (`dark-1x`, `light-1x`) must equal the Task 1 Step 0 baseline, and so must jasper-app's 11 default-look renders (Task 7 proves both). The changes that could reach the default look are each kept from it:
- the icon rails (`ToolStripe`) keep their `chromeReceiver` mark, their icons and their drag (`beginDrag` ignores the new argument);
- `StripeSet.IconRails.ghost(icon)` returns `icon` itself;
- `IconRegistry.prepareBuiltIns()` is unchanged and never loads the stripe table;
- the two new metric roles are read only by `StripeLabels`.

**Artwork and licence**
- Looks cannot ship artwork: the stripe table lives in jasper-ui and only the look's `[chrome] icons` selects it.
- The 12 new SVGs are byte-identical upstream files. Every one of them keeps its JetBrains copyright comment except `toolwindows/toolWindowServer.svg` and `toolWindowServer_dark.svg`, which upstream ships without one. They stay byte-identical: never add a comment. That exception is stated in `BuiltInArtworkTest`, `icons/SOURCE.md` and `ATTRIBUTION.txt`.
- No JetBrains product logo is vendored.
- The fetch downloads from the pinned raw URLs and checks every hash before any write; it never copies from the scratchpad.

**Scope**
- No SDK change and no version bump for the SDK, the plugins or Enterprise: no manifest changes. The Enterprise kit already sets `[chrome] icons = "classic"`.
- The orchestrator's rulings, already given to the user, stand:
  - the "N: " stripe numbers stay;
  - Event Log keeps its count pill (no `notificationsNew` dot icons).
- `WorkspaceLook.Icons` is still read only by `WorkspaceChrome` (`ProjectionReadTest`). The stripes receive jasper-ui's `ChromeArtwork` value, as the header's mark does.

**Code rules**
- Javadoc doclint fails the build on errors: jasper-app uses `-Xdoclint:all`, jasper-ui `-Xdoclint:all,-missing`.
  - Every `{@link}` must resolve.
  - No comment may contain a raw `<`, `>`, `&` or `->`.
  - New public methods carry a comment.
- `docs/design/theme-engine/migration.csv`: every new source under jasper-app or plugins that `ThemeMigrationCoverageTest.CANDIDATE` matches gets its row in the same commit.
  - A row has exactly eight fields. New test rows use `nonvisual-helper` and `none,none`, with the file itself as evidence.
  - The disposition begins `Test/fixture ownership; New in Enterprise plan 13 Task N` and uses semicolons, never commas.
- `ColourLiteralScanTest` stays green: no production Java gains a colour literal.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters.

**Tests and documents**
- Mock tests: no agreement floor moves. The cut render (Task 6) is pixel-identical to the text-only label (pinned by Task 3), so a changed agreement means the cut is wrong; stop and report. Never lower a floor to pass.
  - Plan 12's values are not recorded per pair, so Task 1 Step 0 saves them from `2c065f8cd`'s code: every `FONTS` line of `DockMockTest` (`$base/dock-fonts.txt`) and the `dock-` output of `check_pairs.py` (`$base/dock-pairs.txt`). Task 6 Steps 3 and 5 diff against those files.
  - The `FONTS` lines must read `FONTS <what>`, never `FONTS unchecked`: an installed IBM Plex makes `MockCrops.fontsFloor` print without asserting. If they are unchecked, stop and report.
- Documentation edits are the smallest that make each document accurate. The spec changes carry the one header amendment line Task 7 writes. Earlier `docs/STATUS.md` entries and `docs/superpowers/` history are not rewritten.
- MVP rule (no compatibility shims): signatures change outright (`StripeLabels.length`, `DockStripe.beginDrag`, the stripe constructors). Every call site is updated, with no delegating overloads.

## Review Focus

1. **A plugin panel whose icon is its own `IconAsset` (the bundled Sample's flask), or a vector-only built-in name, under Enterprise.**
   - The label must lead with that glyph at 13 px in the label's ink.
   - It must never throw: `IconRegistry.chromeIcon` throws "Unknown icon" for a plugin name.
   - A `StripGlyph` wrapping a plugin glyph is unwrapped first, so its plugin name never reaches `NAMED.panel`.
   - Tests: Task 2 `StripeIconTest.aPluginPathDrawsItsOwnGlyphInTheLabelInk` and `aStripGlyphAroundAPluginPathDrawsThatGlyph`; Task 3 `StripeIconsTest.aPluginPanelsOwnGlyphLeadsItsLabel`.
2. **The theme switches between Enterprise Light and Dark while stripes show.** The same label must repaint with the `_dark` file without being rebuilt, because the art is chosen from the receiving theme at paint time.
   - Test: Task 2 `StripeIconTest.theClassicArtFollowsTheReceivingTheme`.
3. **A badge arrives on an iconed label (Event Log's count).** The icon stays and the pill follows the text, 17 px further along.
   - Test: Task 4 `StripeIconsTest.aBadgeKeepsTheIconAndThePillFollowsTheText`.
4. **A very small or very large interface font.**
   - The side stripe is never thinner than the icon and its edges.
   - The icon stays centred across and is never clipped.
   - Tests: Task 3 `StripeIconsTest.aSmallFontKeepsTheIconInsideTheStripe` and `aLargeFontCentresTheIconAcross`.
5. **Dragging a window from the bottom strip onto a side stripe (a label the target stripe does not own).**
   - The gap must be as long as the label that will land, icon included.
   - The drag's ghost is the label's stripe icon.
   - Test: Task 5 `DockDragBottomStripTest.aForeignLabelsGapHoldsItsIconAndTheGhostIsItsStripeIcon`.

## Verified facts

At `2c065f8cd`. Paths are under `jasper-app/src/main/java/dev/jasper/app/` unless they start with a module.

**How a stripe is built and measured**
1. The look's artwork is chosen once per window but never reaches the dock.
   - `WorkspaceChrome.of` maps `look.icons()` to `ChromeArtwork` (`workspace/WorkspaceChrome.java:80-83`), keeps it as `artwork` (`:37`, `:53`) and exposes `chromeArtwork()` (`:98`). It marks only the status bar (`:122`); `WindowChrome` marks the header.
   - `DockChrome.stripes(Kit)` (`workspace/DockChrome.java:16`) builds `new StripeSet.LabelledStripes(kit)` or `new StripeSet.IconRails()`. `WindowContent.java:273` calls `look().dock().stripes(themes.kit())`; `look()` is at `:770`.
   - `StripeSet.LabelledStripes(Kit)` builds `new LabelledStripe(Side.LEFT, kit)`, `new LabelledStripe(Side.RIGHT, kit)` and `new BottomStrip(kit)` (`workspace/StripeSet.java:56-58`). `IconRails` is at `:39-46`.
2. `StripeLabels` (`workspace/StripeLabels.java`):
   - `length(text, badge)` = 2 × `STRIPE_LABEL_PADDING` + `setAdvance`, then the pill (`:56-61`).
   - `thickness()` = `line()` + 2 × `RAIL_EDGE` + `DOCK_RULE` (`:74-76`), which is 23 under IBM Plex 12 (`DockMockTest.java:106`).
   - `stripThickness` = max(`BOTTOM_STRIP_HEIGHT` 24, line + 6) + rule (`:83-87`).
   - `paint(g, text, badge, across, sideLabel, theme, open, hover)` draws the text from x = `STRIPE_LABEL_PADDING` in text space, then the pill after `measureText` (`:95-116`).
   - `Label(window, painter)` and `Label(action, painter)` are at `:132-147`.
3. `LabelledStripe` (`workspace/LabelledStripe.java`):
   - `render` builds `new StripeLabels.Label(window, this::paintLabel)` (`:86`) and the action labels (`:96-100`).
   - `updateBadge` copies the window record with `window.icon()` (`:121`).
   - `beginDrag(id, label)` (`:150-158`) measures `liftedLength`.
   - Length is read at `:154`, `:217`, `:236` and `:266`.
   - `paintLabel` fills the tile, then `translate(0, h)` and `rotate(-PI/2)` on the left, or `translate(w, 0)` and `rotate(PI/2)` on the right, then calls `labels.paint(..., label.getWidth(), true, ...)` (`:289-304`).
   - Labels are `across = width - rule` wide, at x 0 on the left and x 1 on the right (`:213`).
   - The second group starts at `max(y, height - RAIL_GAP - extent)` (`:219-220`).
4. `BottomStrip` (`workspace/BottomStrip.java`):
   - `render` is at `:71-91`, with the label at `:78`; `updateBadge` is at `:93-102` and `beginDrag` at `:120-128`.
   - `items` calls `length` at `:181`; `place` is at `:189-222` with `lead = thickness() - rule`.
   - `paintLabel` calls `paint(..., label.getHeight(), false, ...)` (`:237-250`).
5. Rail actions on the rails take `action.getValue(Command.ICON) instanceof Icon contributed ? contributed : AppIcons.icon("command")` (`workspace/ToolStripe.java:186-188`; `Command` is `dev.jasper.app.commands.Command`). `ToolStripe` marks itself `chromeReceiver(this)` (`:96`) and ignores the label in `beginDrag(id, label)` (`:247`).

**Drag**

6. `DockStripe.beginDrag(String id, String label)` (`workspace/DockStripe.java:61`) is called only by `DockDrag.start` with `labels.apply(dragging)` (`workspace/DockDrag.java:220`).
   - `DockDrag.icons` (`:64`) is `WindowContributions`' `id -> panels.get(id).entry.icon()` (`workspace/WindowContributions.java:87`), the panel's own icon.
   - `DockDrag.update` shows it as the ghost: `overlay.show(highlight, placeholder, icons.apply(dragging), ...)` (`:256`).
   - `DockDrag.set` is the window's `StripeSet` (`:54`); `finish()` is at `:294`.
   - `DockDropOverlay` keeps `ghost` (`:33`) and has `highlight()` and `placeholder()` accessors (`:74-76`) but none for the ghost.

**The icon resolver's inputs**

7. A panel's icon reaches the stripe as `ToolStripe.Button.icon` (`ToolStripe.java:54`).
   - App panel: `AppIcons.semantic("bell")` (`application/NotificationsFeature.java:60`).
   - Plugin panels: `HostedKit.icon(name)` makes `new PresentationIcon(name, pathDataOrNull)`. The names are `network` (Sessions), `folder` (SFTP), `tunnel` (Tunnels), `download` (Transfers), `server` (File Servers) and `dev.jasper.sample:flask` (Sample, a plugin path).
   - Test fixtures pass `null` (`LabelledStripeTest.java:33-35`, `DockFixture.java:116-122`, `DockMockTest.java:227-230`) or `new ImageIcon()` (-1 × -1: `EnterpriseDockWindowTest`, `StripeNumbersWindowTest`, `DockChromeMatrixTest`, `RunWindowWorkspaceRenderTest`).
8. `AppIcons` (`platform/AppIcons.java`):
   - `kitName(Icon)` names a `SemanticIcon`, `SurfaceIcon` or `StripGlyph` (`:61-66`).
   - `stripGlyph` draws `PresentationIcon.glyph(palette, ink, size)` for a presentation icon, else `NAMED.panel(name, palette, ink, size)` (`:73-93`).
   - `chromeArtwork(Component)` walks the parents (`:142-147`).
   - `prepare` accepts base sizes 8 and 16 only (`:192-193`).
   - The shared registry is `NAMED` (`:28`).
9. `PresentationIcon` (`platform/PresentationIcon.java`):
   - `name()` and `pathData()` are public (`:30`, `:33`).
   - `glyph(palette, ink, size)` is package-private and calls `artwork.panel` (`:32`).
   - Its own paint is `nativeArtwork.paint(..., TEXT_SECONDARY)` for a built-in name, else `artwork.icon(name, ThemeZone.theme(c), Nodes.Hue.NONE, size)` (`:36-39`).
10. `IconRegistry` (`jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java`):
    - `prepareBuiltIns()` loads only `catalog()` and `chrome()` (`:33`).
    - `chromeIcon(name, size, artwork)` (`:99-101`) throws "Unknown icon" for a name outside `names()` (`:106`), which includes every plugin name.
    - `prepared` builds a `PreparedIcon` from a `Pair` with two `painted(...)` calls (`:103-119`).
    - `PreparedIcon.icon(theme, tint)` returns the light or dark authored icon untinted (`:133-135`). Its constructor is private and accepts sizes 1 to 512 (`:127-128`).
    - `panel(name, palette, Color, size)` (`:164-167`) gives the authored catalog art for the identities `network`, `tunnel` and `more.vertical` (`select`, `:78-84`; `BuiltInArtwork.java:12`). Every other built-in gets its vector path.
    - `download`, `history`, `key`, `folder`, `server`, `tunnel` and `bell` are vector built-ins; `network` and `more.vertical` are not.
11. `BuiltInArtwork` (`jasper-ui/.../icons/BuiltInArtwork.java`):
    - `ClassicHolder.CLASSIC` holds six names (`:40-45`) and `classicChrome()` returns it (`:48`).
    - `pair(stem, hashes)` reads `stem.svg` and `stem_dark.svg` (`:49-51`).
    - `Hashes.CLASSIC = hashes("assets-classic.tsv", 14)` (`:60`) fails start-up on any other row count (`:69`).

**Artwork, licence and metrics**

12. `BuiltInArtworkTest` (`jasper-ui/src/test/java/dev/jasper/ui/icons/BuiltInArtworkTest.java`):
    - `direct(file, size, scale)` and `pixels(icon, scale)` are at `:13-25`.
    - The classic chrome test requires `folder`, `bell` and `terminal` to fall back to New UI under CLASSIC (`:106-109`).
    - `everyClassicFileIsTheRecordedUpstreamFileWithItsCopyright` checks `hasSize(14)` (`:119`), the path rule (`:122`), the hash (`:125`) and the copyright comment for every row (`:127`).
13. The classic set's documents:
    - `jasper-ui/src/main/resources/dev/jasper/ui/icons/SOURCE.md:37-46` (section "IntelliJ classic artwork");
    - `ATTRIBUTION.txt:8` ("The files are unmodified; each keeps its JetBrains copyright notice.");
    - `tools/icons/fetch-classic-icons.py`'s docstring (lines 6-9), which reads every row of `assets-classic.tsv`, so new rows need no code change.

    The About entry "IntelliJ Platform icons (New UI and Classic)" (`about/licenses/notices.toml:92-97`) names `ATTRIBUTION.txt`. That file does not enumerate files.
14. The 12 files, all `width="13" height="13" viewBox="0 0 13 13"` and using only `svg`, `g`, `path` and `rect`, are at `https://raw.githubusercontent.com/JetBrains/intellij-community/8062c061ff33e22041d9f1844db5b8b8a6b5e4d7/platform/icons/src/toolwindows/`. Their SHA-256 values were verified twice by the research (`mapping.md`, `critic.md` §1) and are listed in Task 1 Step 1. All except `toolWindowServer(.svg, _dark.svg)` start with `<!-- Copyright 2000-2021 JetBrains s.r.o.` and contain `Apache 2.0`.
15. `DesignMetrics.Role`:
    - It ends with plan 12's `CHROME_ICON_BUTTON(22), CHROME_ICON_CARET(4),` and the `TOOLBAR_COMBO_*` roles, the last being `TOOLBAR_COMBO_TEXT_MAX(240);` (`jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java:185-190`).
    - The frozen tables `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv` and `classic-roles.tsv` (705 rows each, no header, `NAME\tvalue`, last row `TOOLBAR_COMBO_TEXT_MAX\t240`) must list every role in enum order (`DesignMetricsTest.java:41`, `ClassicCatalogTest.java:24`).
    - `NativeStyles.metric(role)` is `UIScale.scale(kit.metrics().value(role))` (`jasper-ui/.../swing/NativeStyles.java:86`).
16. The stripe ink pairs: `ChromePainter.stripeLabel(theme, open, hover)` paints `STRIPE_LABEL`, `HOVERED_STRIPE_LABEL` or `OPEN_STRIPE_LABEL`, which are `TEXT_PRIMARY` under `Guard.TEXT` (`jasper-ui/.../swing/ChromePainter.java:305-307`, `:355-357`). `stripeTile(theme, open, hover)` is null for a closed, unhovered label (`:349-352`).

**Mock tests and pairs**

17. `DockMockTest` (`jasper-app/src/test/java/dev/jasper/app/workspace/DockMockTest.java`):
    - The left and right stripe tests are at `:98-128`. They render `fitted(top, second)` tall (`:136-140`) and call `compareStripe(..., List.of(29, 109, 151, 301))` and `List.of(29, 121, 164, 247)`.
    - `compareStripe` (`:158-203`) writes `<variant>-<name>-render.png` with `MockCrops.write` and asserts the runs, the line and `STRIPE_FLOOR` 0.85 (`:96`).
    - `theBottomStripMatchesTheMocks` (`:222-285`) renders 330 wide and crops `rows` 600 wide.
    - `theWholeDockIsArrangedAsTheMocks` (`:419-450`) uses `DockFixture`.
18. `tools/visual/pairs.json` holds nine stripe pairs, `dock-{light,dark,vault}-{left-stripe,right-stripe,bottom-strip}` (`:1663-2029`). Their `"render"` is `jasper-app/build/dock-renders/<variant>-<name>-render.png`, and `check_pairs.py` compares each with the mock crop through `compare_mock.py`, without resizing.

## Rulings

Decisions the user left open. Each reads "what — why — cost if wrong". Task 7 records them in `docs/STATUS.md`.

1. **One plan of seven tasks.** Artwork; resolver; side stripes; bottom strip; drag; mock tests; docs and checks — why: each has its own test cycle and could be rejected alone — cost if wrong: none.
2. **The 12 files are appended to `assets-classic.tsv` (26 rows), under `classic/toolwindows/`.** `tools/icons/fetch-classic-icons.py` fetches them unchanged; only its docstring changes — why: one manifest, one fetch and one hash check for the classic set — cost if wrong: none; a second manifest would be a rename.
3. **`toolWindowServer` keeps no comment.**
   - `BuiltInArtworkTest` lists exactly its two files as the comment-less exception and asserts that they carry none. Every other file must keep its comment.
   - `SOURCE.md` and `ATTRIBUTION.txt` state the exception and the module's Apache-2.0.

   why: the user approved byte-identical files; the file is part of intellij-community's Apache-2.0 `platform/icons` module — cost if wrong: none for the licence; a stricter reading would mean swapping File Servers' icon.
4. **A separate stripe table.**
   - `BuiltInArtwork.StripeHolder` (lazy, like `ClassicHolder`) is reached only through `IconRegistry.stripeIcon(name, size)`. That method returns empty, never throwing, for a name without a tool-window file, plugin names included.
   - `classicChrome()` and `chromeIcon` are unchanged, so the header and the status bar keep falling back to New UI for `folder`, `bell` and the rest.

   why: the user's design and `BuiltInArtworkTest:106-109`, and start-up speed (the prewarm never decodes them) — cost if wrong: none.
5. **Keyed by icon name (Set A).**
   - `network`→`webToolWindow`, `folder`→`toolWindowProject`, `tunnel`→`toolWindowModuleDependencies`, `download`→`toolWindowAnalyzeDataflow`, `server`→`toolWindowServer`, `bell`→`notifications`.
   - A third-party panel that names one of these gets that file too.

   why: IntelliJ's own Classic mapping (`PlatformIconMappings.json`) is by icon, and it needs no SDK change — cost if wrong: a plugin using `folder` for something else shows Project's folder.
6. **The resolver is `AppIcons.stripeIcon`, in `dev.jasper.app.platform`** — why: a plugin's path, `PresentationIcon.glyph` and the shared registry are reachable only there; the workspace stays free of icon internals — cost if wrong: none.
7. **The fallback glyph's ink is the label's own painted ink**, `ChromePainter.stripeLabel(theme, open, hover)`, read at paint time from the receiving label (closed and unhovered for anything else, such as the drag overlay). No `PaintPair` is added.
   - why: it is already contrast-guarded and in `PaintedContrastTest`, and "the stripe icon ink" needs no second guard.
   - cost if wrong: the Sample flask is black (white in Dark) next to IntelliJ's grey art. The alternative is three `TEXT_SECONDARY` `Guard.INDICATOR` pairs in `DOCK_PAIRS` and `PaintedContrastTest`.
8. **An identity name without stripe art draws its authored catalog art**, through `IconRegistry.panel`, as header strips do. The only such name today is `more.vertical`, which no panel uses — why: identities have no vector path to stroke (plan 12 E15) — cost if wrong: none today.
9. **Any other `Icon` with a positive size** (not a module glyph) is drawn as given, centred in the 13 px box and scaled down uniformly when larger. No production panel passes one — why: IntelliJ draws a tool window's icon as given — cost if wrong: none.
10. **Labelled stripes under `[chrome] icons = "new_ui"`** draw what the rails draw, at 13 px.
    - A built-in name gets `chromeIcon(name, 13).icon(theme, tint)`, with the icon's own tint: `TEXT_SECONDARY` for a `PresentationIcon`, `TEXT_PRIMARY` for a semantic icon.
    - A plugin path gets its ordinary glyph (`PresentationIcon.ordinary`).

    why: the user's design ("the panel's ordinary icon at 13 px"), and the rails are New UI receivers — cost if wrong: none; no shipped kit combines them.
11. **Metrics `STRIPE_ICON(13)` and `STRIPE_ICON_GAP(4)`, equal in both catalogs, are read only by `StripeLabels`** — why: IntelliJ's 13 × 13 classic tool-window icons, never rescaled, and the JDK's `iconTextGap` of 4, which `StripeButton` never sets (anatomy §1-2) — cost if wrong: one table row.
12. **Thickness is max(line, `STRIPE_ICON`) + 2 × `RAIL_EDGE` + `DOCK_RULE` on every labelled side stripe, icons or not** — why: the stripe's width must not jump when a window comes or goes; it stays 23 under IBM Plex — cost if wrong: a tiny-font, text-only stripe is up to 5 px thicker. The bottom strip's height is unchanged, since 24 ≥ 13 + 6.
13. **Placement.**
    - The icon box starts `STRIPE_LABEL_PADDING` (10) from the label's reading start, then the 4 px gap, then the text.
    - Across, the side stripes centre it with integer division, (22 − 13) / 2 = 4, as IntelliJ's 4 in its 21. The bottom strip centres it down, (24 − 13) / 2 = 5.
    - IntelliJ's `1.1f` stretch is not copied.

    why: the existing 10 px padding already matches IntelliJ's measured 10 / 10 for ordinary labels and the mocks' text-only padding — cost if wrong: a label as long as "Pull Requests" is 1 to 2 px shorter than IntelliJ's.
14. **The same icon in every state.**
    - Closed, hovered and open draw the same icon; only the tile changes.
    - Authored art is never tinted.
    - A label is never disabled, so there is no disabled variant.

    why: `StripeButtonUi.kt:103-147` — cost if wrong: none.
15. **Rail actions lead with their icon too**: `Command.ICON`, else `AppIcons.icon("command")`, read when the stripe renders, as the rails do (`ToolStripe.actionIcon`, extracted unchanged) — why: the user's "every stripe button — tool windows and rail actions alike" — cost if wrong: "About Sample" reads text only if the user prefers.
16. **Drag.**
    - `DockStripe.beginDrag(id, label, icon)` receives the panel's own icon (`DockDrag.icons`), and each stripe resolves it as it would its own label.
    - The ghost is `StripeSet.ghost(icon)`, computed once when the drag starts: the rails give the icon unchanged; labelled stripes give the 13 px stripe icon, or null (no ghost) for a window without one.

    why: the user's design 6; the rails keep today's ghost byte for byte — cost if wrong: none.
17. **Mock checks follow the user, not the text-only mocks.**
    - `DockMockTest` renders the shipped icons (`folder`, `server`, `network`, `tunnel`, `bell`, `download`; the deferred Problems fixture stays without one). Each label's icon slice, the icon and its gap across the label (17 px, 34 at 2x), is cut out, and the text-only mocks are compared with that cut. The cut is pixel-identical to a text-only label (Task 3 proves it), so every run expectation and floor stands.
    - The slices are asserted as the icons.
    - The nine stripe pairs in `pairs.json` read the cut render (`-text-render.png`), with a note. `-render.png` becomes the real iconed stripe.
    - Nine ungated pairs (no `min_agreement`), `dock-{light,dark,vault}-{left-stripe,right-stripe,bottom-strip}-iconed`, read the real `-render.png` with the mock crop widened by the slices, so `compare_all.py` shows the stripe as it ships, icons and moved labels included, beside the mock.
    - `DockFixture`'s panels carry the shipped icons.
    - The cut is a change of method relative to the approved design ("update honestly to the iconed stripes"). Task 7 lists it in STATUS as a deviation for the user to confirm, beside the 17 px longer labels.

    why: "update honestly to the iconed stripes" without fabricating mock pixels; the gated cut pairs hold the floors, the ungated pairs show what ships — cost if wrong: the gated pairs do not compare the icon pixels with anything (JUnit pins them); if the user rejects the cut, the floors of the iconed pairs must be measured and ruled on.
18. **Spec sections 9.1, 12.1 and 12.2 need no edit.**
    - Nothing in them describes stripe art.
    - `WorkspaceLook.Icons` is still read only by `WorkspaceChrome.of`; the stripes get the resulting `ChromeArtwork` as an argument, like the header's mark.
    - 5.2, 10.7, 16.1 and 19.1 change.

    why: checked against the text — cost if wrong: none.
19. **Both sides' label ink and the stripe surface are unchanged**, and the default look's rails, ghost and drag are untouched — why: the default look's byte identity — cost if wrong: none.

---

### Task 1: The classic tool-window icons in jasper-ui

**Files:**
- Modify: `jasper-ui/src/main/resources/dev/jasper/ui/icons/assets-classic.tsv` (12 rows appended), `jasper-ui/src/main/java/dev/jasper/ui/icons/BuiltInArtwork.java`, `jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java`, `jasper-ui/src/main/resources/dev/jasper/ui/icons/SOURCE.md`, `jasper-ui/src/main/resources/dev/jasper/ui/icons/ATTRIBUTION.txt`, `tools/icons/fetch-classic-icons.py` (docstring only)
- Create (fetched): the 12 SVGs under `jasper-ui/src/main/resources/dev/jasper/ui/icons/classic/toolwindows/`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/icons/BuiltInArtworkTest.java`

**Interfaces:**
- Produces:
  - `public java.util.Optional<IconRegistry.PreparedIcon> IconRegistry.stripeIcon(String name, int logicalSize)`. This is IntelliJ's classic 13 × 13 tool-window file for `name`, light and dark, authored and untinted. It is empty for any other name and never throws for an unknown or plugin name (it throws only when the registry is closed, or for a size outside 1 to 512).
  - `static Map<String, BuiltInArtwork.Pair> BuiltInArtwork.classicStripes()`, which loads lazily.

- [ ] **Step 0: Capture the default-look baseline** (no commit)

Before any source changes, while the worktree's code is still `2c065f8cd`'s, render the default look into the scratchpad. Task 7 Step 3 compares against these files.

```bash
base=/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan13-baseline
git diff --quiet 2c065f8cd HEAD -- . ':(exclude)docs' && echo "code is 2c065f8cd's"   # must print; otherwise stop
test -z "$(git status --porcelain)" && echo clean                                       # must print
rm -rf "$base" && mkdir -p "$base/app"
for theme in dark light; do
  ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=$theme && cp -R "jasper-ui/build/gallery/$theme-1x" "$base/gallery-$theme-1x"
done
./gradlew :jasper-app:test --tests '*ChromeRenderTest' --tests '*DefaultDockPixelsTest' --rerun
for f in jasper-app/build/chrome-renders/chrome-default*.png jasper-app/build/chrome-renders/settings-default*.png \
         jasper-app/build/chrome-renders/chrome-complement*.png jasper-app/build/chrome-renders/settings-complement*.png \
         jasper-app/build/chrome-renders/chrome-fallback*.png jasper-app/build/chrome-renders/settings-fallback*.png \
         jasper-app/build/dock-renders/default-dark.png jasper-app/build/dock-renders/default-light.png; do cp "$f" "$base/app/"; done
ls "$base/gallery-dark-1x" | wc -l; ls "$base/gallery-light-1x" | wc -l; ls "$base/app" | wc -l
# Plan 12's mock agreements, per pair (Global Constraints, Mock tests)
./gradlew :jasper-app:test --tests '*DockMockTest' --rerun
python3 -I - jasper-app/build/test-results/test/TEST-dev.jasper.app.workspace.DockMockTest.xml > "$base/dock-fonts.txt" <<'PY'
import sys, xml.etree.ElementTree as ET
out = "".join(e.text or "" for e in ET.parse(sys.argv[1]).getroot().iter("system-out"))
print("\n".join(sorted(line for line in out.splitlines() if line.startswith("FONTS "))))
PY
python3 -I tools/visual/check_pairs.py "$(mktemp -d)" dock- > "$base/dock-pairs.txt"
wc -l < "$base/dock-fonts.txt"; grep -c 'FONTS unchecked' "$base/dock-fonts.txt"; grep -c ': PASS ' "$base/dock-pairs.txt"
```

Expected: both prints, then the two gallery counts (322 each at plan 12) and `11`. Every `cp` must succeed. If one reports a missing file, read the names `ChromeRenderTest` and `DefaultDockPixelsTest` write and copy those, never dropping a default-look render. Then a positive count of `FONTS` lines, `0` unchecked and `25` passing `dock-` pairs. If any line reads `FONTS unchecked` (an installed IBM Plex hides the plugin's files), stop and report: the comparison Task 6 relies on would assert nothing.

- [ ] **Step 1: Append the manifest rows**

```bash
python3 -I - <<'PY'
rows = [
 ("toolwindows/webToolWindow.svg", "5ae853654ae3effd984d797f89115061708a140667cc1f2daa5cfbab09d7bd59"),
 ("toolwindows/webToolWindow_dark.svg", "690e3453e8ad4170894355914129e0fd03c4e7f61f93384ba3cb501661a576ea"),
 ("toolwindows/toolWindowProject.svg", "4b03c9e944e4155fc677cc396ed82d5f69bc7887612d5447e938808acf49d80c"),
 ("toolwindows/toolWindowProject_dark.svg", "01e1677f689e4493051716b6210ed4be58020d4245f062719dd50f8b9bfe2569"),
 ("toolwindows/toolWindowModuleDependencies.svg", "91320289f00fc590b2eddea7b441753c789454def7f3bd5b041995a00c2e4c10"),
 ("toolwindows/toolWindowModuleDependencies_dark.svg", "585febead26882c203272593a40fcd9d19c9b5ac07df21a911a205af3e15cb7b"),
 ("toolwindows/toolWindowAnalyzeDataflow.svg", "a153b594dacdff6f6d5a831947a155e854bd0bb3f0a3ad57bc613cb3df1b5c65"),
 ("toolwindows/toolWindowAnalyzeDataflow_dark.svg", "5e1104ecc516df8f1ca4f99c527f63e3c6d5cc950e06c0ce71ffa3f304bd7ee5"),
 ("toolwindows/toolWindowServer.svg", "4a0ab208bab8dc3ed487e5ad3ecdb524f41c9cd6584cbf814a720df12f660b3b"),
 ("toolwindows/toolWindowServer_dark.svg", "6ba30c027058a21e1b8b367e62a5c12fc0a8bf7b02f9f704b73ae4dbe470e66f"),
 ("toolwindows/notifications.svg", "a80e623a431a922f26645bb634396a58cb2999062095697e806ab5e104fbe3fd"),
 ("toolwindows/notifications_dark.svg", "6a450bfd870cdb1fb2435df9c6fb661bfa1aa676cacb68cfed6e7b1cc986bd49"),
]
import pathlib
out = pathlib.Path("jasper-ui/src/main/resources/dev/jasper/ui/icons/assets-classic.tsv")
text = out.read_text(encoding="utf-8")
assert text.endswith("\n") and len(text.splitlines()) == 14
out.write_text(text + "".join(f"classic/{p}\tplatform/icons/src/{p}\t{h}\n" for p, h in rows), encoding="utf-8")
print(len(out.read_text(encoding="utf-8").splitlines()))
PY
```

Expected: `26`.

- [ ] **Step 2: Fetch**

Run: `python3 -I tools/icons/fetch-classic-icons.py`
Expected: `26 files verified and written`. Then `git status --short jasper-ui/src/main/resources/dev/jasper/ui/icons/classic` lists exactly 12 new files under `toolwindows/`, and `git diff --stat -- jasper-ui/src/main/resources/dev/jasper/ui/icons/classic` shows no change to the existing 14. If the network is unavailable, stop and report: never hand-write, edit or copy an SVG.

- [ ] **Step 3: Write the failing tests** in `BuiltInArtworkTest`

Replace `everyClassicFileIsTheRecordedUpstreamFileWithItsCopyright` with the version below, and add the stripe test:

```java
    /** Upstream files that ship without a copyright comment; still intellij-community's Apache-2.0 platform/icons module (plan 13 Rulings 3). */
    static final Set<String> NO_COMMENT = Set.of("classic/toolwindows/toolWindowServer.svg", "classic/toolwindows/toolWindowServer_dark.svg");

    /** Global Constraints: byte-identical upstream files, each with its JetBrains copyright comment where upstream has one. */
    @Test void everyClassicFileIsTheRecordedUpstreamFileWithItsCopyright() throws Exception {
        List<String> lines;
        try (var in = getClass().getResourceAsStream("assets-classic.tsv")) {
            lines = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        }
        assertThat(lines).hasSize(26);
        for (String line : lines) {
            String[] columns = line.split("\t");
            assertThat(columns[1]).startsWith("platform/icons/src/").endsWith(columns[0].substring("classic/".length()));
            byte[] bytes;
            try (var in = getClass().getResourceAsStream(columns[0])) { bytes = in.readAllBytes(); }
            assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))).as(columns[0]).isEqualTo(columns[2]);
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            if (NO_COMMENT.contains(columns[0])) assertThat(text).as(columns[0] + ", shipped without a comment").startsWith("<svg").doesNotContain("Copyright");
            else assertThat(text).as(columns[0]).startsWith("<!-- Copyright 2000-").contains("JetBrains s.r.o.").contains("Apache 2.0");
        }
    }

    /** Plan 13 Rulings 4 and 5: the stripe table is Set A's six tool-window files, for labelled stripes only. */
    @Test void theClassicStripeIconsAreTheToolWindowFilesForSixNames() throws Exception {
        var files = Map.of("network", "classic/toolwindows/webToolWindow", "folder", "classic/toolwindows/toolWindowProject",
            "tunnel", "classic/toolwindows/toolWindowModuleDependencies", "download", "classic/toolwindows/toolWindowAnalyzeDataflow",
            "server", "classic/toolwindows/toolWindowServer", "bell", "classic/toolwindows/notifications");
        assertThat(BuiltInArtwork.classicStripes()).containsOnlyKeys(files.keySet());
        assertThat(BuiltInArtwork.classicChrome()).as("the header's classic set is unchanged").doesNotContainKeys(files.keySet().toArray(String[]::new));
        try (var registry = new IconRegistry()) {
            for (var entry : files.entrySet()) for (var theme : List.of(KitFixtures.light(), KitFixtures.dark())) {
                String file = entry.getValue() + (theme.dark() ? "_dark.svg" : ".svg");
                for (int scale : new int[] {1, 2}) {
                    var stripe = registry.stripeIcon(entry.getKey(), 13).orElseThrow().icon(theme, SemanticColor.STATUS_DANGER);
                    assertThat(pixels(stripe, scale)).as("%s at %s", file, scale).isEqualTo(direct(file, 13, scale));
                }
            }
            for (String name : List.of("add", "history", "more.vertical", "dev.jasper.sample:flask", "no-such-icon"))
                assertThat(registry.stripeIcon(name, 13)).as(name).isEmpty();
        }
    }
```

`STATUS_DANGER` proves the art is untinted. The existing `theClassicChromeSetIsTheVendoredArtworkForItsSixMeanings` stays unchanged: its `folder`, `bell` and `terminal` fall-backs are Rulings 4's guarantee.

- [ ] **Step 4: Run them to see them fail**

Run: `./gradlew :jasper-ui:test --tests '*BuiltInArtworkTest'`
Expected: compilation fails (`classicStripes` and `stripeIcon` are undefined).

- [ ] **Step 5: Implement**

- `BuiltInArtwork`:
  - `Hashes.CLASSIC = hashes("assets-classic.tsv", 26)`.
  - Add `private static final class StripeHolder { static final Map<String, Pair> STRIPES = Map.of(...); }` with Rulings 5's six names, each `pair("classic/toolwindows/<file>", Hashes.CLASSIC)`.
  - Add `static Map<String, Pair> classicStripes() { return StripeHolder.STRIPES; }`. Its comment says that these are IntelliJ's classic 13 px tool-window icons for a labelled stripe's buttons (Enterprise presentation spec 10.7). It also says they are admitted only by `IconRegistry.stripeIcon` and loaded on its first call, never by the start-up prewarm or a chrome receiver.
- `IconRegistry.stripeIcon(String name, int logicalSize)`:
  - It is `synchronized`, calls `requireOpen()` and looks the name up in `BuiltInArtwork.classicStripes()`.
  - With no pair it returns `Optional.empty()`, without consulting `names()`.
  - Otherwise it builds the `PreparedIcon` exactly as `prepared` does for a chrome pair (the two `painted(...)` calls at `:115-116`, `path` = `artworks.get(name)`).
  - Its comment says that it serves a labelled stripe under `ChromeArtwork.CLASSIC`, where every other name has no file and the caller draws its own glyph, and that it never changes `chromeIcon`.
- `SOURCE.md`: replace the "# IntelliJ classic artwork" section's body (`:39-46`) with:

```markdown
Twenty-six SVGs under `classic/`, listed in assets-classic.tsv with their upstream paths under `platform/icons/src/`
and SHA-256 values: `general/add`, `actions/splitVertically`, `general/settings`, `actions/moveToWindow`,
`actions/find` and `actions/execute`, each with its dark sibling, plus `general/expandComponent` and
`runConfigurations/application`; and the tool-window icons `toolwindows/webToolWindow`, `toolWindowProject`,
`toolWindowModuleDependencies`, `toolWindowAnalyzeDataflow`, `toolWindowServer` and `notifications`, each with its dark
sibling. They come from JetBrains/intellij-community at tag `idea/2026.2.3`
(commit `8062c061ff33e22041d9f1844db5b8b8a6b5e4d7`) and are byte-identical, each keeping its copyright comment, except
`toolwindows/toolWindowServer.svg` and its dark sibling, which upstream ships without one; they are part of the same
Apache-2.0 `platform/icons` module. `python3 -I tools/icons/fetch-classic-icons.py` reproduces them. A file without
`_dark` serves both variants.
Chrome receivers marked `ChromeArtwork.CLASSIC` get six of them (add, split, zoom, settings, window.add and search);
the catalog's `run` and `application` get two more; `IconRegistry.stripeIcon` gives the six tool-window icons to the
labelled stripes for network, folder, tunnel, download, server and bell.
```

- `ATTRIBUTION.txt:8`: replace the line with these two lines:

```text
The files are unmodified; each keeps the JetBrains copyright notice upstream gives it (classic
toolwindows/toolWindowServer.svg and toolWindowServer_dark.svg ship without one, under the same Apache-2.0).
```

- `tools/icons/fetch-classic-icons.py`, docstring lines 7-8: "checks each SHA-256 and only then writes them, byte for byte, so each keeps its JetBrains copyright comment. Builds and" becomes "checks each SHA-256 and only then writes them, byte for byte, so each stays the upstream file (with its JetBrains copyright comment where upstream has one). Builds and". Re-wrap so that no line passes 120 columns. The code is unchanged.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-ui:test --tests '*BuiltInArtworkTest' --tests '*IconRegistryTest' :jasper-app:test --tests '*ThirdPartyNoticesTest' --tests '*LicensesViewTest' :verifyThirdPartyNotices`
Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add tools/icons/fetch-classic-icons.py jasper-ui/src/main/resources/dev/jasper/ui/icons jasper-ui/src/main/java/dev/jasper/ui/icons \
  jasper-ui/src/test/java/dev/jasper/ui/icons/BuiltInArtworkTest.java
git commit -m "Icons: vendor IntelliJ's classic tool-window icons for the stripes

Twelve unmodified 13 px SVGs from intellij-community at idea/2026.2.3
(webToolWindow, toolWindowProject, toolWindowModuleDependencies,
toolWindowAnalyzeDataflow, toolWindowServer, notifications, light and dark),
hash-checked in assets-classic.tsv, behind a stripe-only lazy table
(IconRegistry.stripeIcon); toolWindowServer ships without a comment upstream.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 2: The stripe icon resolver

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/AppIcons.java`, `jasper-app/src/main/java/dev/jasper/app/platform/PresentationIcon.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/platform/StripeIconTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (one row)

**Interfaces:**
- Consumes: `IconRegistry.stripeIcon(name, size)` (Task 1).
- Produces:
  - `public static java.util.Optional<Icon> AppIcons.stripeIcon(Icon icon, ChromeArtwork artwork, int logicalSize, java.util.function.Function<Component, Color> ink)`. A header strip's `StripGlyph` is first unwrapped to its source. It is empty when that source is null or its width or height is not positive. The icon it returns is `UIScale.scale(logicalSize)` square and resolves its art and ink at paint time from the receiving component (`ThemeZone.theme(c)`, `ink.apply(c)`).
  - `Icon PresentationIcon.ordinary(dev.jasper.ui.theme.GeneratedTheme theme, int size)` (package-private): `artwork.icon(name, theme, Nodes.Hue.NONE, size)`, what its own paint draws for a plugin path, at another size.

- [ ] **Step 1: Write the failing test** `StripeIconTest`

Model the zone set-up on `ZoneIconsTest.chromeReceiversAloneShowOriginalArtworkWithoutChangingIconValues` (`:131-160`):
- `ThemeTestSupport.controller(true)`;
- a `JPanel` zone with client properties `"Jasper.zone" = "workspace"` and `"Jasper.workspaceTheme" = ThemeTestSupport.generated(dark)`, holding a `JLabel` that receives the paint;
- a private `pixels(Icon, Component)` copied from `ZoneIconsTest:193-198`.

```java
/** Plan 13: the icon a labelled stripe's button draws for a panel's or action's icon (spec 10.7). */
class StripeIconTest {
    static final java.util.function.Function<Component, Color> INK = c -> new Color(0xD0, 0x30, 0x30);
    static final Map<String, String> SET_A = Map.of("network", "network", "folder", "folder", "tunnel", "tunnel",
        "download", "download", "server", "server", "bell", "bell");

    @Test void underClassicSetANamesDrawTheirToolWindowFileUntinted() { /* for each name, both themes:
        AppIcons.stripeIcon(new PresentationIcon(name, null), CLASSIC, 13, INK).orElseThrow() painted in the zone
        equals registry.stripeIcon(name, 13).orElseThrow().icon(theme, TEXT_PRIMARY); its width and height are UIScale.scale(13).
        AppIcons.semantic("bell") and AppIcons.named("FOLDER") give the same pixels as "bell" and "folder" (kitName). */ }

    /** Review Focus 2. */
    @Test void theClassicArtFollowsTheReceivingTheme() { /* one stripeIcon(new PresentationIcon("server", null), CLASSIC, ...)
        instance, painted in a Light zone, then the same instance in a Dark zone: the light file's pixels, then the dark
        file's (registry.stripeIcon("server", 13).orElseThrow().icon(light / dark, TEXT_PRIMARY)); they differ. */ }

    /** Review Focus 1. */
    @Test void aPluginPathDrawsItsOwnGlyphInTheLabelInk() { /* var mark = new PresentationIcon("test:mark", "M 2 2 L 14 14 M 2 14 L 14 2");
        CLASSIC: pixels equal mark.glyph(theme.semantic(), INK.apply(label), UIScale.scale(13)) painted in the same zone, and
        assertThatCode(...).doesNotThrowAnyException(); NEW_UI: pixels equal mark.ordinary(theme, UIScale.scale(13)). */ }

    /** Review Focus 1: a header strip's wrapper around a plugin glyph is unwrapped, never resolved by its name alone. */
    @Test void aStripGlyphAroundAPluginPathDrawsThatGlyph() { /* var mark as above; CLASSIC:
        AppIcons.stripeIcon(AppIcons.stripGlyph(mark, 10, INK), CLASSIC, 13, INK).orElseThrow() painted in the zone
        does not throw (assertThatCode) and equals the pixels of AppIcons.stripeIcon(mark, CLASSIC, 13, INK), that is
        mark.glyph(theme.semantic(), INK.apply(label), UIScale.scale(13)); its width is UIScale.scale(13), not 10's. */ }

    @Test void aNameWithoutStripeArtDrawsItsMonochromeGlyphUnderClassic() { /* "history" and "key": equal to
        new IconRegistry().panel(name, theme.semantic(), INK.apply(label), 13); "more.vertical" (an identity, Rulings 8) equals the
        same call too, which gives its authored catalog art. */ }

    @Test void underNewUiTheStripeDrawsWhatTheRailsDraw() { /* new PresentationIcon("folder", null): equal to
        registry.chromeIcon("folder", 13).icon(theme, TEXT_SECONDARY); AppIcons.semantic("bell"): chromeIcon("bell", 13).icon(theme, TEXT_PRIMARY);
        new PresentationIcon("download", null): chromeIcon("download", 13).icon(theme, TEXT_SECONDARY) (its vector path). */ }

    @Test void noIconForNullOrAnEmptyIcon() {
        for (var artwork : ChromeArtwork.values()) {
            assertThat(AppIcons.stripeIcon(null, artwork, 13, INK)).isEmpty();
            assertThat(AppIcons.stripeIcon(new javax.swing.ImageIcon(), artwork, 13, INK)).isEmpty();
        }
    }

    @Test void anotherIconIsDrawnAsGivenScaledIntoTheBox() { /* a 26 x 26 opaque blue ImageIcon: a 13 x 13 stripe icon
        whose 13 x 13 paint is all blue, and nothing outside it; a 9 x 9 one is centred (2 px of transparency each side). */ }
}
```

Write each body from its comment, with the exact calls and values named there. `registry` is a `new dev.jasper.ui.icons.IconRegistry()` closed by try-with-resources. Restore the controller in `finally` as `ZoneIconsTest` does.

Add the `migration.csv` row:

```text
jasper-app/src/test/java/dev/jasper/app/platform/StripeIconTest.java,app.platform,StripeIconTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/platform/StripeIconTest.java,Test/fixture ownership; New in Enterprise plan 13 Task 2; a labelled stripe's icon: classic tool-window art by name; the label-ink glyph; New UI at 13 px
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-app:test --tests '*StripeIconTest'`
Expected: compilation fails (`stripeIcon` and `ordinary` are undefined).

- [ ] **Step 3: Implement**

`AppIcons.stripeIcon` decides once, by the icon's kind. Write it as one small private `Icon` record per case. Each paints at `UIScale.scale(logicalSize)` device pixels with the receiving component's theme:

```text
source = icon instanceof StripGlyph strip ? strip.source() : icon   (unwrap first, as AppIcons.stripGlyph does)
source null, or width <= 0 or height <= 0                -> empty
name = PresentationIcon.name() | kitName(source); path = PresentationIcon.pathData() (null for built-ins and semantic icons)
  (name, path and size are all read from source, never from the StripGlyph wrapper)
CLASSIC:
  name present, path null, NAMED.stripeIcon(name, size) present -> that PreparedIcon.icon(theme, TEXT_PRIMARY) (authored)
  PresentationIcon with a path                                -> presentation.glyph(theme.semantic(), ink.apply(c), size)
  other name present                                          -> NAMED.panel(name, theme.semantic(), ink.apply(c), size)
NEW_UI:
  name present, path null  -> NAMED.chromeIcon(name, size).icon(theme, PresentationIcon ? TEXT_SECONDARY : TEXT_PRIMARY)
  PresentationIcon with a path -> presentation.ordinary(theme, size)
any other Icon             -> painted as given, centred in the box, scaled down uniformly when larger (Rulings 9)
```

- Prepare each `PreparedIcon` once per (artwork, name, device size) in a private `ConcurrentHashMap`, on first paint. The CLASSIC path is the only caller of `NAMED.stripeIcon`, so the default look never loads the stripe table.
- The method comment names spec 10.7, both artworks' rules and "empty for no icon". Use no raw `->` in it.
- `PresentationIcon.ordinary` gets the comment "This artwork as its own paint draws it, at {@code size} device pixels (a labelled stripe under New UI artwork)."

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*StripeIconTest' --tests '*ZoneIconsTest' --tests '*AppIconsTest' --tests '*ThemeMigrationCoverageTest'`
Expected: PASS.

- [ ] **Step 5: Commit**: message `Icons: a labelled stripe's icon for a panel's icon`, with a body naming the classic files by name, the label-ink glyph for plugin paths and other names, and New UI at 13 px, plus the trailers.

### Task 3: Side stripe labels lead with their icon

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java`, `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv`, `classic-roles.tsv`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/StripeLabels.java`, `LabelledStripe.java`, `BottomStrip.java` (constructor, `Label`, and its `length` and `paint` calls; Execution notes R6), `StripeSet.java`, `DockChrome.java`, `WindowContent.java:273`, `ToolStripe.java` (extract `actionIcon`)
- Modify (constructor arguments): every `new LabelledStripe(`, `new BottomStrip(`, `new StripeSet.LabelledStripes(`, `new StripeLabels(` and `.stripes(` call in `jasper-app/src/test` (find them with `grep -rn "new LabelledStripe(\|new BottomStrip(\|new StripeSet.LabelledStripes(\|new StripeLabels(\|\.stripes(EnterpriseLook\|\.stripes(Kit" jasper-app/src/test`). The sites are in `DockMockTest`, `BottomStripTest`, `StripeSetTest`, `DockDragBottomStripTest`, `DockFixture` and `LabelledStripeTest`. Each adds `ChromeArtwork.CLASSIC`; `StripeSetTest:29`'s default case adds `ChromeArtwork.NEW_UI`. Every `labels.length(text, badge)` call for a window label adds `null`. The rail-action length at `LabelledStripeTest:251` becomes `labels.length("About Sample", Optional.empty(), labels.icon(ToolStripe.actionIcon(action)))`, because a rail action always leads with an icon.
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/StripeIconsTest.java`
- Test: `LabelledStripeTest` (a fixture overload, its `fitted(Kit)` helper taking `Math.max(line, STRIPE_ICON)` (Execution notes R8) and the `:250-251` edits), `DesignMetricsTest`, `ClassicCatalogTest`, `EnterpriseDockWindowTest` and `DockChromeMatrixTest` (one window-level case each, Step 1)
- Modify: `docs/design/theme-engine/migration.csv` (one row)

**Interfaces:**
- Consumes: `AppIcons.stripeIcon` (Task 2); `IconRegistry.stripeIcon` (Task 1).
- Produces:
  - `DesignMetrics.Role.STRIPE_ICON(13)` and `STRIPE_ICON_GAP(4)`, appended after `TOOLBAR_COMBO_TEXT_MAX(240)` with the comment "Plan 13: a labelled stripe button's icon (IntelliJ's classic 13 px tool-window icons) and its gap to the text (StripeButton's iconTextGap); only the labelled stripes read them." Both are the same in both catalogs.
  - `StripeLabels(Kit kit, ChromeArtwork artwork)`, with these members:
    - `Icon icon(Icon source)`: the stripe icon or null, via `AppIcons.stripeIcon(source, artwork, STRIPE_ICON's logical value, this::ink)`.
    - `Color ink(Component c)`: `painter.stripeLabel(theme, open, hover)` from `c`'s state when it is an `AbstractButton`, else closed and unhovered.
    - `int length(String text, Optional<PanelBadge> badge, Icon icon)`: adds `STRIPE_ICON + STRIPE_ICON_GAP` when `icon` is non-null.
    - `int thickness()`: `max(line(), STRIPE_ICON)` + edges + rule.
    - `java.awt.Point iconAt(Side side, int width, int height)`, in the label's own unrotated coordinates:
      - LEFT: `((width - size) / 2, height - padding - size)`;
      - RIGHT: `((width - size) / 2, padding)`;
      - BOTTOM: `(padding, (height - size) / 2)`.
    - `void paint(Graphics2D g, String text, Optional<PanelBadge> badge, boolean icon, int across, boolean sideLabel, GeneratedTheme theme, boolean open, boolean hover)`: the text starts at `padding + (icon ? size + gap : 0)`.
  - `StripeLabels.Label(ToolStripe.Button window, Icon icon, BiConsumer<Label, Graphics> painter)` and `Label(Action action, Icon icon, BiConsumer<Label, Graphics> painter)`, each with the field `final Icon icon` ("its stripe icon, or null: the label reads text only").
  - `LabelledStripe(Side side, Kit kit, ChromeArtwork artwork)`; `BottomStrip(Kit kit, ChromeArtwork artwork)`; `StripeSet.LabelledStripes(Kit kit, ChromeArtwork artwork)`; `StripeSet DockChrome.stripes(Kit kit, ChromeArtwork artwork)`, whose rails ignore `artwork`.
  - `static Icon ToolStripe.actionIcon(Action action)`, the rails' expression at `:188`, extracted and used there unchanged.
  - Test helpers in `StripeIconsTest`, which Tasks 4 to 6 use:
    - `static Rectangle iconSlice(Side side, Rectangle label)`: the icon and its gap across the whole label. LEFT: `(label.x, label.y + label.height - padding - size - gap, label.width, size + gap)`; RIGHT: `(label.x, label.y + padding, label.width, size + gap)`; BOTTOM: `(label.x + padding, label.y, size + gap, label.height)`.
    - `static BufferedImage withoutIcons(BufferedImage render, DockStripe stripe, int scale)`: `render` with every iconed label's slice removed, as rows on a side stripe and as columns on the bottom strip.
    - `static int[] reference(Icon icon, Component receiver, Color ground, int scale)`: the icon painted alone, upright, at `scale` over `ground`.
    - `static int[] box(BufferedImage image, int x, int y, int size)`.
  - `LabelledStripeTest.window(String id, String title, Slot slot, int number, Icon icon)`, which the existing four-argument `window` calls with `null`.

- [ ] **Step 1: Write the failing tests**

Append the two roles to both role tables (`STRIPE_ICON\t13`, `STRIPE_ICON_GAP\t4`, after `TOOLBAR_COMBO_TEXT_MAX\t240`). `DesignMetricsTest` and `ClassicCatalogTest` then fail until the enum has them.

`StripeIconsTest` (`@ExtendWith(EdtTestExtension.class)`; every test wraps `EnterpriseLook.install(false)` … `finally EnterpriseLook.restoreDefault()`):
- `metric(role)` is `UIScale.scale(EnterpriseLook.kit().metrics().value(role))`.
- `SLICE` is `metric(STRIPE_ICON) + metric(STRIPE_ICON_GAP)`.
- `static Icon classic(String name)` is `new IconRegistry().stripeIcon(name, metric(STRIPE_ICON)).orElseThrow().icon(EnterpriseLook.theme(false), SemanticColor.TEXT_PRIMARY)`. Every test here runs under Enterprise Light, and Task 5 reuses it.

```java
    @Test void eachSideLabelDrawsItsIconUprightAtItsReadingStart() throws Exception {
        try {
            EnterpriseLook.install(false);
            var theme = EnterpriseLook.theme(false);
            var painter = new ChromePainter(EnterpriseLook.kit());
            int size = metric(DesignMetrics.Role.STRIPE_ICON);
            for (Side side : List.of(Side.LEFT, Side.RIGHT)) {
                var stripe = new LabelledStripe(side, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
                stripe.render(List.of(LabelledStripeTest.window("sftp", "SFTP", Slot.on(side, Slot.Group.TOP), 2,
                    new PresentationIcon("folder", null))), List.of());
                var image = MockCrops.render(stripe, stripe.getPreferredSize().width, 200, 2);
                var label = (StripeLabels.Label) stripe.buttons().getFirst();
                Rectangle bounds = label.getBounds();
                var at = new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC).iconAt(side, bounds.width, bounds.height);
                assertThat(at.x).as(side + ": centred across, IntelliJ's 4").isEqualTo((bounds.width - size) / 2);
                assertThat(at.y).as(side + ": at the reading start")
                    .isEqualTo(side == Side.LEFT ? bounds.height - metric(DesignMetrics.Role.STRIPE_LABEL_PADDING) - size
                        : metric(DesignMetrics.Role.STRIPE_LABEL_PADDING));
                int[] expected = reference(classic("folder"), label, painter.stripeTile(theme, true, false), 2);
                int[] painted = box(image, 2 * (bounds.x + at.x), 2 * (bounds.y + at.y), 2 * size);
                assertThat(painted).as(side + ": Project's folder, upright").isEqualTo(expected);
                assertThat(painted).as(side + ": not turned with the text").isNotEqualTo(rotated(expected, 2 * size, side == Side.RIGHT));
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }
```

Here `rotated(int[] square, int size, boolean clockwise)` is a test helper that turns a square of pixels a quarter.

Further tests, each asserting what its name says with the exact values given:
- `cuttingTheIconSlicesLeavesTheTextOnlyStripe` (both sides). The stripe holds "1: SFTP" (top) and "2: File Servers" (second), with `folder` and `server` icons.
  - The iconed stripe is rendered `H + 2 * SLICE` tall at 2x, where `H = 2 * RAIL_GAP + both text-only lengths + 40`.
  - The same windows with null icons are rendered `H` tall.
  - `withoutIcons(iconed render, stripe, 2)`'s pixels equal the text-only render's exactly.
  - This is Rulings 17's premise.
- `anIconLengthensItsLabelBySeventeenAndKeepsTheThickness`:
  - an iconed label's height is the text-only length plus `SLICE` (17);
  - `getPreferredSize().width` is `Math.max(line, 13) + 2 * RAIL_EDGE + DOCK_RULE`, and `MockProfile.fonts("23 under IBM Plex", width, 23, 0)`;
  - the 4 px gap rows between the icon box and the text hold only the tile colour.
- `aNullOrEmptyIconDrawsNoIconAndNoGap`:
  - windows with `null` and with `new ImageIcon()` both give `label.icon == null`;
  - the length is `labels.length(text, Optional.empty(), null)`;
  - the two renders are pixel-identical.
- `theSecondGroupPacksIconedLabelsAtTheBottomEnd` (both sides):
  - the windows are "Top", "Alpha" and "Beta", each with an icon, the last two in the second group, and the stripe is 400 tall;
  - Beta ends `RAIL_GAP` above 400, and Alpha ends where Beta starts;
  - each length includes `SLICE`.
- `everyStateDrawsTheSameIcon`: for closed (a `ToolStripe.Button` with `selected` false and rollover off), hovered and open, the icon box equals `reference(classic("folder"), label, ground, 2)`. `ground` is that state's `stripeTile`, or `stripeSurface` when the tile is null.
- `aPluginPanelsOwnGlyphLeadsItsLabel` (Review Focus 1):
  - the window's icon is `new PresentationIcon("test:mark", "M 2 2 L 14 14 M 2 14 L 14 2")`;
  - `stripe.render` does not throw;
  - inside the icon box of a 4x render, `LabelledStripeTest.inks(...)` finds the exact `SurfaceContrast.painted(theme, ChromePainter.OPEN_STRIPE_LABEL)`.
- `underNewUiArtworkTheStripeDrawsTheOrdinaryIcon`: `new LabelledStripe(Side.LEFT, kit, ChromeArtwork.NEW_UI)` with `folder`; its icon box equals `reference(new IconRegistry().chromeIcon("folder", 13).icon(theme, SemanticColor.TEXT_SECONDARY), ...)`.
- `aSmallFontKeepsTheIconInsideTheStripe` (Review Focus 4):
  - a kit with `kit.type().withBody(6)`, as `LabelledStripeTest.aLargeFontThickensTheStripeAndNoLabelOverlaps` builds `big`;
  - the thickness is `13 + 2 * RAIL_EDGE + DOCK_RULE`, which is 20;
  - the icon box lies inside the label.
- `aLargeFontCentresTheIconAcross`: with `withBody(24)` the icon's x is `(label.width - 13) / 2`, and it is inside the label.
- `railActionsLeadWithTheirIcon`:
  - an `AbstractAction("About Sample")` with `putValue(Command.ICON, new PresentationIcon("server", null))` has a label icon and a length of the text-only length plus `SLICE`;
  - an action without `Command.ICON` still has a non-null label icon (`AppIcons.icon("command")`);
  - the box draws `classic("server")` for the first.

The window-level cases (design point 3 on the path users see: a real window hands the look's `ChromeArtwork` to its stripes, so a call that passed `NEW_UI`, or an artwork dropped in `DockChrome` or `StripeSet`, fails here):
- `EnterpriseDockWindowTest.aWindowUnderTheShippedLookDocksTheClassicWay`: the SFTP panel is registered with `new PresentationIcon("folder", null)` in place of `new ImageIcon()`. After the existing assertions, `var label = (StripeLabels.Label) owner.leftStripe().buttons().getFirst()`:
  - `label.icon` is non-null;
  - `StripeIconsTest.reference(label.icon, label, Color.WHITE, 2)` equals `StripeIconsTest.reference(new IconRegistry().stripeIcon("folder", 13).orElseThrow().icon(EnterpriseLook.theme(false), SemanticColor.TEXT_PRIMARY), label, Color.WHITE, 2)` (`toolWindowProject.svg`, the classic tool-window file).
- `DockChromeMatrixTest.labelledStripesUnderNewUiArtworkDrawTheNewUiIcon`, a new test: a window under `KitFixtures.enterprise()` and `DockChromeTest.CLASSIC_DOCK` (`tool_windows = stripes`, `icons = new_ui`), built as `everyKitDocksUnderBothDockStyles` builds it, with one panel `model.addPanel("test.left", "Left", new PresentationIcon("folder", null), Slot.LEFT_TOP, site -> new JLabel("left"))`. Its left label's `icon` is non-null and `StripeIconsTest.reference(label.icon, label, Color.WHITE, 2)` equals `StripeIconsTest.reference(new IconRegistry().chromeIcon("folder", 13).icon(ThemeZone.theme(label), SemanticColor.TEXT_SECONDARY), label, Color.WHITE, 2)` (the New UI art, not the classic file). Close the window and the controller in `finally`, as the existing test does.

`withoutIcons` reads the iconed labels from `stripe.buttons()`: those whose `((StripeLabels.Label) b).icon != null`. It keeps every row (side stripe) or column (bottom strip) of `render` that lies in no `scale ×` slice.

Add the `migration.csv` row:

```text
jasper-app/src/test/java/dev/jasper/app/workspace/StripeIconsTest.java,app.workspace,StripeIconsTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/workspace/StripeIconsTest.java,Test/fixture ownership; New in Enterprise plan 13 Task 3; a labelled stripe button's icon: place; size; upright; gap; art; the cut slices
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-ui:test --tests '*DesignMetricsTest' --tests '*ClassicCatalogTest' :jasper-app:test --tests '*StripeIconsTest' --tests '*EnterpriseDockWindowTest' --tests '*DockChromeMatrixTest'`
Expected: the jasper-ui tests fail on the two new table rows, and jasper-app fails to compile (no three-argument constructors, no `iconAt`, no `Label.icon`).

- [ ] **Step 3: Implement**

- `DesignMetrics`: the two roles, as above.
- `StripeLabels`: the constructor, `icon`, `ink`, `length`, `thickness`, `iconAt` and `paint`, as in Interfaces. `paint` places the pill after the shifted text, so it follows automatically.
- `LabelledStripe`:
  - `render` builds `new StripeLabels.Label(window, labels.icon(window.icon()), this::paintLabel)` and `new StripeLabels.Label(action, labels.icon(ToolStripe.actionIcon(action)), this::paintLabel)`.
  - Every `labels.length(...)` call (`:154`, `:217`, `:236`, `:266`) passes that label's `icon`. In `beginDrag`, a label the stripe does not own passes `null` until Task 5.
  - `paintLabel`, after the tile fill and before the rotation: `if (label.icon != null) { Point at = labels.iconAt(side, label.getWidth(), label.getHeight()); label.icon.paintIcon(label, g, at.x, at.y); }`. It then calls `labels.paint(g, label.text(), label.badge(), label.icon != null, label.getWidth(), true, theme, open, hover)`.
  - The class comment gains: "Each label starts, in reading order, with its window's or action's 13 px icon, upright, then a 4 px gap (the user's direction of 2026-10-19, as IntelliJ's classic StripeButton)."
- `BottomStrip`: the constructor takes and passes `artwork`; it builds `new StripeLabels.Label(window, null, this::paintLabel)`, passes `label.icon` to every `length` and `false` for `icon` to `paint`. Task 4 gives it icons.
- `StripeSet.LabelledStripes(Kit kit, ChromeArtwork artwork)` and `DockChrome.stripes(Kit kit, ChromeArtwork artwork)` (comment: "`artwork` is the look's icon set; only the labelled stripes draw it"). In `WindowContent:273` the call becomes `look().dock().stripes(themes.kit(), look().chromeArtwork())`.
- `ToolStripe.actionIcon(Action)`: extract `:188`'s expression; `action(...)` calls it.
- The test call sites listed under Files.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-ui:test --tests '*DesignMetricsTest' --tests '*ClassicCatalogTest' --tests '*NewRolesTest' :jasper-app:test --tests '*StripeIconsTest' --tests '*LabelledStripeTest' --tests '*BottomStripTest' --tests '*StripeSetTest' --tests '*DockMockTest' --tests '*DockDrag*' --tests '*ToolStripe*' --tests '*Enterprise*' --tests '*StripeNumbers*' --tests '*DockChrome*' --tests '*RunWindowWorkspaceRenderTest' --tests '*ThemeMigrationCoverageTest' --tests '*ProjectionReadTest' --tests '*VariantReadTest'` (the `*Enterprise*` and `*DockChrome*` filters include `EnterpriseDockWindowTest` and `DockChromeMatrixTest` with their new cases)
Expected: PASS. Only the window fixtures are icon-less (a null or empty icon), so their geometry is unchanged. Rail actions always lead with an icon (`ToolStripe.actionIcon` falls back to `AppIcons.icon("command")`), so the "About Sample" labels in `LabelledStripeTest`'s `aShortStripeGivesUpItsRailActionsBeforeItsWindows` and `railActionsFollowTheTopGroupAndNeverStaySelected` are `SLICE` longer; both still pass, and the first computes its `actions` length with the icon (Files).

- [ ] **Step 5: Commit**: message `Dock: side stripe labels lead with their tool window's icon`, with a body naming the user's 2026-10-19 direction, 13 px upright at the reading start, the 4 px gap and the unchanged 23 px stripe, plus the trailers.

### Task 4: The bottom strip's labels lead with their icon

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/BottomStrip.java`
- Test: `StripeIconsTest`, `BottomStripTest`

**Interfaces:**
- Consumes: `StripeLabels.icon`, `iconAt`, `length`, `paint` and `Label(window, icon, painter)` (Task 3); `StripeIconsTest`'s helpers.
- Produces: no new names.

- [ ] **Step 1: Write the failing tests** (in `StripeIconsTest`)

- `theBottomStripLeadsEachLabelWithItsIconCentredDown`. The strip holds "Event Log" (`AppIcons.semantic("bell")`) and "3: Transfers" (`new PresentationIcon("download", null)`) in BOTTOM_LEFT and "Terminal" (null) in BOTTOM_RIGHT, rendered 900 × 25 at 2x.
  - The three are closed buttons, never `LabelledStripeTest.window(...)` (whose windows are selected and open, so their boxes would sit on the open tile): `new ToolStripe.Button("log", "Event Log", AppIcons.semantic("bell"), Slot.BOTTOM_LEFT, false)`, `new ToolStripe.Button("transfers", "Transfers", new PresentationIcon("download", null), Slot.BOTTOM_LEFT, false, Optional.empty(), OptionalInt.of(3))` and `new ToolStripe.Button("terminal", "Terminal", null, Slot.BOTTOM_RIGHT, false)`.
  - Each iconed label's box is at `(label.x + 10, label.y + (label.height - 13) / 2)`, which is y 6 in the strip.
  - Each box equals `reference(classic("bell") / classic("download"), label, ground, 2)`, where `ground` is `painter.stripeTile(theme, false, false)`, falling back to `painter.stripeSurface(theme)` when that is null (as `everyStateDrawsTheSameIcon` takes it; a closed, unhovered label has no tile).
  - The strip stays 25 tall.
  - "Terminal" has no icon, and its length is text-only.
  - A divider follows each left label at `label.x + label.width`.
- `cuttingTheBottomStripsIconSlicesLeavesTheTextOnlyStrip`: as the side test, with the iconed strip `900 + 2 * SLICE` wide and the text-only one 900 wide. `withoutIcons` cuts columns.
- `aBadgeKeepsTheIconAndThePillFollowsTheText` (Review Focus 3):
  - `updateBadge("log", Optional.of(new PanelBadge(3, PanelBadge.Tone.ACCENT, "3 unread")))` keeps the same `label.icon` instance (`isSameAs`);
  - the label grows by exactly the pill (`STRIPE_PILL_GAP` + pill width);
  - in a 1x render, the first column holding `DerivedColor.INFO_GROUND` is `SLICE` further right than the same badge on a text-only label.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests '*StripeIconsTest'`
Expected: the three new tests FAIL (no icon drawn, no slice).

- [ ] **Step 3: Implement**

- `BottomStrip.render` builds `new StripeLabels.Label(window, labels.icon(window.icon()), this::paintLabel)`.
- `paintLabel`, after the tile: `if (label.icon != null) { Point at = labels.iconAt(Side.BOTTOM, label.getWidth(), label.getHeight()); label.icon.paintIcon(label, g, at.x, at.y); }`, then `labels.paint(g, label.text(), label.badge(), label.icon != null, label.getHeight(), false, theme, open, hover)`.
- `updateBadge` already keeps the `Label` (and so its icon).
- The class comment gains the icon sentence.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*StripeIconsTest' --tests '*BottomStripTest' --tests '*DockDragBottomStripTest' --tests '*DockMockTest' --tests '*EventLogTest'`
Expected: PASS.

- [ ] **Step 5: Commit**: message `Dock: the bottom strip's labels lead with their icon`, plus the trailers.

### Task 5: Drag keeps the icon; the ghost is the stripe icon

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/DockStripe.java:61`, `LabelledStripe.java`, `BottomStrip.java`, `ToolStripe.java:247`, `StripeSet.java`, `DockDrag.java`, `DockDropOverlay.java`
- Test: `StripeIconsTest`, `DockDragBottomStripTest`, `LabelledStripeTest` (the `beginDrag` calls at `:260`, `:283`, `:315`, `:328` and `:345` gain `, null`)

**Interfaces:**
- Consumes: `StripeLabels.icon` (Task 3).
- Produces:
  - `abstract void beginDrag(String id, String label, Icon icon)` on `DockStripe`, where `icon` is the dragged window's own icon (`DockDrag.icons`), which may be null;
  - `ToolStripe`'s override ignores `label` and `icon`;
  - `Icon LabelledStripe.stripeIcon(Icon source)`, which returns `labels.icon(source)`;
  - `Icon StripeSet.ghost(Icon icon)`, the drag's ghost for a window whose icon is `icon`: `IconRails` returns `icon`, and `LabelledStripes` returns `left.stripeIcon(icon)`;
  - `Icon DockDropOverlay.ghost()`, the shown ghost, a package-private accessor beside `highlight()`.

- [ ] **Step 1: Write the failing tests**

In `StripeIconsTest`:
- `aDraggedLabelKeepsItsIconAndLength`. The left stripe holds "Top" (`folder`) and "Second" (`server`, second group), 400 tall.
  - `stripe.beginDrag("t", "Top", null)`, then `showGap(Slot.LEFT_BOTTOM, 0)`: `gapBounds().height` is the "Top" label's iconed length, its own icon kept.
  - After `endDrag()`, `beginDrag("x", "Foreign", new PresentationIcon("download", null))` and the same gap: `labels.length("Foreign", Optional.empty(), null) + SLICE`.
  - With a null icon: the text-only length.
- `theGhostIsTheLabelsStripeIcon`:
  - `new StripeSet.LabelledStripes(kit, ChromeArtwork.CLASSIC).ghost(new PresentationIcon("folder", null))` paints `reference(classic("folder"), ...)`'s pixels;
  - `ghost(null)` and `ghost(new ImageIcon())` are null;
  - `new StripeSet.IconRails().ghost(icon)` `isSameAs(icon)` for a `PresentationIcon("folder", null)`.

In `DockDragBottomStripTest` (Review Focus 5):
- Give `Window` a `Map<String, Icon> icons` field, empty by default and used both by `render()` (in place of `null`) and as `drag.icons = id -> icons.get(id)`.
- Add the test `aForeignLabelsGapHoldsItsIconAndTheGhostIsItsStripeIcon`:
  - `new Window(true)`, with `icons.put("c", new PresentationIcon("download", null))` and `render()`;
  - `hover(label(bottomStrip, "c"), on(left, ...))` onto the left stripe's top group;
  - the left stripe's `gapBounds().height` is `new StripeLabels(kit, CLASSIC).length("C", Optional.empty(), null) + SLICE`;
  - `drag.overlay().ghost()` is non-null, and `StripeIconsTest.reference(drag.overlay().ghost(), window.content, Color.WHITE, 2)` equals `StripeIconsTest.reference(StripeIconsTest.classic("download"), window.content, Color.WHITE, 2)`;
  - release with Escape (`drag.cancel()`).

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests '*StripeIconsTest' --tests '*DockDragBottomStripTest'`
Expected: compilation fails (no three-argument `beginDrag`, no `ghost`).

- [ ] **Step 3: Implement**

- `DockStripe.beginDrag(String id, String label, Icon icon)`. Its comment: "…whose stripe label is {@code label} and whose icon is {@code icon} (the window's own; a labelled stripe resolves it as it would its own label's)…".
- `LabelledStripe` and `BottomStrip`: `liftedLength = own != null ? labels.length(own.text(), own.badge(), own.icon) : labels.length(label, Optional.empty(), labels.icon(icon))`.
- `ToolStripe`: `@Override void beginDrag(String id, String label, Icon icon) { beginDrag(id); }`.
- `StripeSet.ghost` as in Interfaces, with a comment saying the rails' ghost is the window's icon itself.
- `DockDrag`:
  - add a field `private Icon ghost`, set in `start()` to `set.ghost(icons.apply(dragging))` and cleared in `finish()`;
  - `:220` becomes `stripe.beginDrag(dragging, labels.apply(dragging), icons.apply(dragging))`;
  - `:256` passes `ghost` in place of `icons.apply(dragging)`.
- `DockDropOverlay.ghost()`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*StripeIconsTest' --tests '*Dock*' --tests '*ToolStripe*' --tests '*LabelledStripeTest' --tests '*BottomStripTest' --tests '*WindowDragTest'`
Expected: PASS. `DockDragTest`'s rail ghosts are unchanged.

- [ ] **Step 5: Commit**: message `Dock: a dragged stripe label keeps its icon; the ghost is its stripe icon`, plus the trailers.

### Task 6: The mock tests and pairs follow the iconed stripes

**Files:**
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/DockMockTest.java`, `DockFixture.java`, `tools/visual/pairs.json`

**Interfaces:**
- Consumes: `StripeIconsTest.iconSlice`, `withoutIcons`, `reference` and `box`; `LabelledStripeTest.window(..., Icon)` (Task 3).
- Produces: `jasper-app/build/dock-renders/<variant>-<name>-text-render.png` (the cut) beside `<variant>-<name>-render.png` (the iconed stripe).

- [ ] **Step 1: Change the stripe comparisons** (Rulings 17; the class comment gains: "Since the user's direction of 2026-10-19 the stripes' labels start with their icons, which the text-only mocks do not draw: each stripe is compared with its icon slices cut out, and the slices are asserted as the icons.")

- **`theLeftStripeMatchesTheMocks` and `theRightStripeMatchesTheMocks`**:
  - The fixtures carry the shipped icons: `folder` and `server` on the left, `network` and `tunnel` on the right, each a `new PresentationIcon(name, null)` made after `EnterpriseLook.install`.
  - The text-only height is `height = fitted(...)` (unchanged); the stripe is rendered `height + 2 * SLICE` tall.
  - `assertSecondAtTheEnd` takes `height + 2 * SLICE`.
  - Then a new helper `assertIcons(variant, name, render, stripe)` checks every label's icon box: `box(...)` equals `reference(the label's icon, label, the open tile under the variant's scrim, 2)`, which is `variant.seen` applied to the reference image.
  - `MockCrops.write(variant.name() + "-" + name, iconed window, variant.crop(..., 2 * (height + 2 * SLICE)))` writes the iconed stripe.
  - `compareStripe(variant, name + "-text", withoutIcons(render, stripe, 2), variant.crop(...same as today...), thickness, side, <same expected runs>)` compares the cut. Its expected runs (`29, 109, 151, 301` and `29, 121, 164, 247`) and `STRIPE_FLOOR` are unchanged.
- **`theBottomStripMatchesTheMocks`**:
  - Event Log takes `AppIcons.semantic("bell")` and Transfers `new PresentationIcon("download", null)`; Problems stays `null` (a fixture, Rulings 17).
  - The render is `330 + 2 * SLICE` wide.
  - `assertIcons` checks the two boxes over the closed surface.
  - The full render is written as `-bottom-strip`, `MockCrops.crop(render, shift, 0, 520 + 4 * SLICE, 50)` beside `variant.crop(2, 1706, 520 + 4 * SLICE, 50)`: the mock crop widened by `4 * SLICE`, the size Step 4's `-iconed` pair crops (588 at 2x).
  - Every existing assertion runs unchanged on `withoutIcons(render, strip, 2)`, written as `-bottom-strip-text`.
- **`fitted`** calls `labels.length(text, Optional.empty(), null)` with `new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC)`.

`SLICE` is `StripeIconsTest`'s, `metric(STRIPE_ICON) + metric(STRIPE_ICON_GAP)` in logical pixels (17 at 1x; Execution notes R9). At 2x the cut removes `2 * SLICE` device pixels per iconed label.

- [ ] **Step 2: Give `DockFixture`'s panels their shipped icons**

In the button loop (`:116-122`):
- sftp gets `folder`, servers `server`, sessions `network` and tunnels `tunnel`, each a `new PresentationIcon(...)`;
- log gets `AppIcons.semantic("bell")`, transfers `new PresentationIcon("download", null)`, and problems keeps `null`.

`theWholeDockIsArrangedAsTheMocks` and the header comparisons keep every expectation: the stripes stay 23 and the bottom strip 25.

- [ ] **Step 3: Run the dock tests**

```bash
base=/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan13-baseline
./gradlew :jasper-app:test --tests '*DockMockTest' --tests '*StripeIconsTest' --rerun
python3 -I - jasper-app/build/test-results/test/TEST-dev.jasper.app.workspace.DockMockTest.xml > "$base/task6-dock-fonts.txt" <<'PY'
import sys, xml.etree.ElementTree as ET
out = "".join(e.text or "" for e in ET.parse(sys.argv[1]).getroot().iter("system-out"))
# The side stripes' comparisons are now named "<side>-stripe-text"; the bottom strip's keep their name.
print("\n".join(sorted(line.replace("-stripe-text: ", "-stripe: ") for line in out.splitlines() if line.startswith("FONTS "))))
PY
grep -c 'FONTS unchecked' "$base/task6-dock-fonts.txt"
diff "$base/dock-fonts.txt" "$base/task6-dock-fonts.txt" && echo "FONTS lines identical"
```

Expected: PASS, `0` unchecked and `FONTS lines identical`, with no `diff` output: every line, the nine stripe comparisons (left, right and bottom, three variants) included, equals plan 12's from Task 1 Step 0. If any line reads `FONTS unchecked`, stop and report. If any agreement or run edge differs, the cut is not identical: stop and report; never move a floor.

- [ ] **Step 4: Point the nine stripe pairs at the cut and add the nine iconed pairs**

```bash
python3 -I - <<'PY'
import json, pathlib
path = pathlib.Path("tools/visual/pairs.json")
pairs = json.loads(path.read_text(encoding="utf-8"))
note = ("The user's direction of 2026-10-19 (plan 13): each label starts with its tool window's 13 px icon and a 4 px gap, "
        "which the text-only mocks do not draw; this pair reads the render with each label's icon slice cut out "
        "(-text-render.png, pixel-identical to the text-only label), and the iconed stripe is -render.png, its icons "
        "pinned by DockMockTest and StripeIconsTest.")
iconed_note = ("The user's direction of 2026-10-19 (plan 13): the stripe as it ships, each label led by its 13 px icon and "
               "a 4 px gap, beside the text-only mock with its crop widened by the two labels' slices (4 x 17 at 2x); "
               "ungated, for inspection with compare_all.py; the gated pair without -iconed compares the cut.")
WIDEN = 4 * 17   # two iconed labels per stripe, each 17 px (34 device pixels at 2x) longer
names = {f"dock-{v}-{s}" for v in ("light", "dark", "vault") for s in ("left-stripe", "right-stripe", "bottom-strip")}
out, done = [], 0
for pair in pairs:
    out.append(pair)
    if pair["name"] in names:
        assert pair["render"].endswith("-render.png"), pair["name"]
        iconed = {"name": pair["name"] + "-iconed", "mock": pair["mock"], "crop": list(pair["crop"]),
                  "render": pair["render"], "note": iconed_note}
        if pair["name"].endswith("-bottom-strip"): iconed["crop"][2] += WIDEN
        else: iconed["crop"][3] += WIDEN
        pair["render"] = pair["render"][: -len("-render.png")] + "-text-render.png"
        pair["note"] = (pair["note"] + " " + note) if pair.get("note") else note
        out.append(iconed)
        done += 1
assert done == 9, done
assert len({p["name"] for p in out}) == len(out)
path.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(done, sum(1 for p in out if p["name"].endswith("-iconed")))
PY
git diff --stat tools/visual/pairs.json
```

Expected: `9 9`, and a diff touching only those nine entries plus the nine new `-iconed` entries, each right after its gated pair, with no `min_agreement` and crops `[x, y, 44, 400]` (left), `[x, y, 44, 344]` (right) and `[x, y, 588, 50]` (bottom). Those are the sizes Step 1 writes the iconed `-render.png` at: `2 * (height + 2 * SLICE)` tall with `height` 166 and 138, and `520 + 4 * SLICE` wide. If the file's existing formatting differs from `json.dumps(indent=2)` (check `git diff` for whitespace churn), edit the entries by hand instead.

- [ ] **Step 5: Check the pairs**

```bash
base=/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan13-baseline
python3 -I tools/visual/check_pairs.py "$(mktemp -d)" dock- > "$base/task6-dock-pairs.txt"
diff "$base/dock-pairs.txt" "$base/task6-dock-pairs.txt" && echo "dock pairs identical"
iconed=$(mktemp -d); python3 -I tools/visual/compare_all.py "$iconed" -iconed; ls "$iconed" | head
```

Expected: every gated `dock-` pair PASS (25 of 25) and `dock pairs identical`, with no `diff` output: the nine stripe lines (the cut renders, under their unchanged names) and the 16 header lines equal plan 12's from Task 1 Step 0. `check_pairs.py` skips the ungated `-iconed` pairs. `compare_all.py` prints a result for each of the nine `-iconed` pairs, none `MISSING` and none a size error, and writes their side-by-side images. Look at them (each is the iconed stripe beside the mock). If `$base/dock-pairs.txt` or `dock-fonts.txt` is missing, make them as Task 7 Step 3's fallback describes (a detached worktree at `2c065f8cd`, running Task 1 Step 0's last four commands there); never from code after Task 1.

- [ ] **Step 6: Commit**: message `Dock tests: compare the iconed stripes with their icon slices cut out`, with a body naming the user's 2026-10-19 direction, the cut, the unchanged expectations and floors, the nine cut pairs and the nine ungated `-iconed` pairs, plus the trailers.

### Task 7: Documentation, STATUS and the whole build

**Files:**
- Modify:
  - `docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`;
  - `docs/sdk-icons.md`, `docs/plugin-ui.md`, `docs/ui-architecture.md`, `docs/app-architecture.md`;
  - `docs/STATUS.md`; this plan's status banner.

- [ ] **Step 1: Amend the spec** (each edit is the smallest accurate one)

- **Header:** after the plan 12 line (`:20`), add `Amended on 2026-10-19 at the user's direction after trying plan 12 (plan 13): 5.2, 10.7, 16.1 and 19.1 (every labelled stripe button starts with its tool window's icon, IntelliJ's classic tool-window icons under Enterprise, over the mocks' text-only labels).`
- **5.2:** the `icons` row's last cell becomes "The workspace chrome's icons: IntelliJ's classic icons in the header and status bar (10.3) and on the labelled stripes (10.7)".
- **10.7, Stripes bullet:**
  - "rotated text labels on" (`:1279`) becomes "rotated labels on".
  - The sentence that wraps across `:1282-1283`, quoted as it stands:

    ```text
    so today's bindings give "1: Sessions", "2: SFTP" and "3: Transfers". The stripe is as thick as its label's line and
      the rail's two edges, with its 1 px rule (the mocks' 22 + 1 under IBM Plex), the label centred across it;
    ```

    "as thick as its label's line and" + newline + "the rail's two edges" becomes "as thick as its label's line (at least `STRIPE_ICON`, 13) and the rail's two edges". Re-wrap the bullet at 120 columns with its two-space continuation indent; never search for the joined phrase as one line.
  - After "Left-stripe labels read bottom to top, right-stripe labels top to bottom." insert:

    "Each label starts, in reading order, with its window's or rail action's icon (the user, 2026-10-19, over the mocks' text-only labels), as IntelliJ's classic `StripeButton`: 13 px (`STRIPE_ICON`), upright, centred across, `STRIPE_LABEL_PADDING` from the label's start, then a 4 px gap (`STRIPE_ICON_GAP`) and the text, so the label grows by 17. It sits at the label's bottom end on the left stripe, its top end on the right and its left end on the bottom strip, and is the same in every state. Under `[chrome] icons = "classic"` the names `network`, `folder`, `tunnel`, `download`, `server` and `bell` draw IntelliJ's classic tool-window icons as authored (`webToolWindow`, `toolWindowProject`, `toolWindowModuleDependencies`, `toolWindowAnalyzeDataflow`, `toolWindowServer`, `notifications`), and any other name, a plugin's own path included, draws its monochrome glyph in the label's ink. Under `new_ui` the window's ordinary icon is drawn at 13 px. A window without an icon reads text only. A dragged label keeps its icon and length, and the drag's ghost is that icon."
- **10.7, Bottom strip bullet:** the phrase wraps across `:1287-1288`, quoted as it stands:

  ```text
  - **Bottom strip.** `LabelledStripes` adds a horizontal strip for the BOTTOM_LEFT and BOTTOM_RIGHT slots, with count
    pills for badges that carry a number. It is 24 px plus its 1 px rule, as the mocks draw it. `DockDrag` and the drop
  ```

  "with count" + newline + "pills for badges that carry a number" becomes "its labels led by their icons (centred down the strip), with count pills for badges that carry a number". Re-wrap the bullet as above, keeping "labels led by their icons" on one line.
- **16.1 item 4:** "covered by `ChromeArtworkTest` and `ZoneIconsTest`" becomes "covered by `ChromeArtworkTest`, `ZoneIconsTest`, `StripeIconTest` and `StripeIconsTest`".
- **19.1:**
  - "Twelve plans" becomes "Thirteen plans", and "Order: 1 to 12 in sequence" becomes "Order: 1 to 13 in sequence".
  - Add the row: `` | 13 | `stripe-icons` | The user's 2026-10-19 direction: every labelled stripe button starts with its tool window's 13 px icon, upright at the reading start, IntelliJ's classic tool-window icons (vendored, attributed) under `[chrome] icons = "classic"`, the label's monochrome glyph for any other name, drag and the ghost keeping it | ``.
- 9.1, 12.1 and 12.2 are unchanged (Rulings 18).

- [ ] **Step 2: Correct the guides**

- **`docs/sdk-icons.md`:** after the `"run"`/`"application"` paragraph (`:16-18`), add:

  "Under the Enterprise look a panel's stripe label starts with its icon at 13 px: `"network"`, `"folder"`, `"tunnel"`, `"download"`, `"server"` and `"bell"` draw IntelliJ's classic tool-window icons, and any other name, a registered path included, its monochrome glyph in the label's colour."
- **`docs/plugin-ui.md`:** in the "Under the Enterprise look …" paragraph (`:61-64`), before "; the descriptors and events are the same", insert the text below. It is fenced because the link resolves only from `docs/`:

  ```text
  , and a panel's stripe label starts with its icon ([plugin icons](sdk-icons.md))
  ```
- **`docs/ui-architecture.md`:** after `:253-254`'s classic sentence, add:

  "`IconRegistry.stripeIcon` gives a labelled stripe IntelliJ's classic 13 px tool-window icons (the `toolwindows/` rows of `assets-classic.tsv`) for network, folder, tunnel, download, server and bell; it is a table of its own, so the classic chrome set is unchanged."
- **`docs/app-architecture.md`:** after `:242` ("… (`assets-classic.tsv`)."), add:

  "The labelled stripes and the bottom strip are not receivers: `DockChrome.stripes` gives them the look's `ChromeArtwork`, and they draw each label's icon at 13 px through `AppIcons.stripeIcon`: under CLASSIC, IntelliJ's classic tool-window icon where `IconRegistry.stripeIcon` has one, else the icon's monochrome glyph in the label's ink; under NEW_UI, the icon the rails draw."

Check the removals and the additions:

```bash
spec=docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md
grep -n "rotated text labels\|Twelve plans\|1 to 12 in sequence" "$spec"   # prints nothing
grep -c 'STRIPE_ICON' "$spec"                                               # at least 2
grep -c 'labels led by their icons' "$spec"                                 # 1
python3 -I - "$spec" <<'PY'
import re, sys
text = re.sub(r"\s+", " ", open(sys.argv[1], encoding="utf-8").read())
for phrase in ("as thick as its label's line (at least `STRIPE_ICON`, 13) and the rail's two edges",
               "its labels led by their icons (centred down the strip), with count pills for badges that carry a number",
               "Each label starts, in reading order, with its window's or rail action's icon",
               "`StripeIconTest` and `StripeIconsTest`", "Thirteen plans", "Order: 1 to 13 in sequence"):
    print(text.count(phrase), phrase)
PY
```

Expected: no output from the first `grep`, a count of at least 2, then `1`, and `1` before each of the six phrases (whitespace collapsed, so a re-wrap cannot hide a missed edit).

- [ ] **Step 3: Prove the default look unchanged**

```bash
base=/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan13-baseline
out=$(mktemp -d)
ls "$base/gallery-dark-1x" "$base/gallery-light-1x" "$base/app" > /dev/null || echo "NO BASELINE"
for theme in dark light; do
  ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=$theme && cp -R "jasper-ui/build/gallery/$theme-1x" "$out/gallery-$theme-1x"
  diff -r "$base/gallery-$theme-1x" "$out/gallery-$theme-1x" && echo "$theme: identical ($(ls "$out/gallery-$theme-1x" | wc -l) files)"
done
./gradlew :jasper-app:test --tests '*ChromeRenderTest' --tests '*DefaultDockPixelsTest' --rerun
mkdir -p "$out/app"
for f in jasper-app/build/chrome-renders/chrome-default*.png jasper-app/build/chrome-renders/settings-default*.png \
         jasper-app/build/chrome-renders/chrome-complement*.png jasper-app/build/chrome-renders/settings-complement*.png \
         jasper-app/build/chrome-renders/chrome-fallback*.png jasper-app/build/chrome-renders/settings-fallback*.png \
         jasper-app/build/dock-renders/default-dark.png jasper-app/build/dock-renders/default-light.png; do cp "$f" "$out/app/"; done
ls "$base/app" | wc -l
diff -r "$base/app" "$out/app" && echo "jasper-app default look: identical"
```

Expected: `dark: identical` and `light: identical` (322 files each), then `11` and `jasper-app default look: identical`, with no `diff` output.
- Only the default look's own gallery folders (`dark-1x`, `light-1x`) are compared.
- If the baseline is missing, make one from `2c065f8cd` with `git worktree add --detach "$TMPDIR/plan13-main" 2c065f8cd`, run the same commands with `./gradlew -p "$TMPDIR/plan13-main" …` from this worktree's root, compare, then `git worktree remove`. Never compare against renders made after Task 1.

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

Expected: 0 failures and 0 errors. Record the counts; the base is 11,475 tests, 25 skipped.

- [ ] **Step 5: Render the Enterprise gallery and check the pairs**

```bash
for theme in light dark; do ./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=$theme -Pjasper.galleryScale=2; done
python3 -I tools/visual/check_pairs.py "$(mktemp -d)"
```

Expected: every gated pair PASS; record "N of N" (101 at plan 12). A non-dock pair that fails because a stripe now shows an icon in its crop is a deviation: record the pair, the measured agreement and the reason in STATUS and this plan's banner. Re-measure its floor only with the measured value and a note naming the user's 2026-10-19 direction.

- [ ] **Step 6: Write `docs/STATUS.md`'s new top entry and this plan's banner**

The entry goes above plan 12's and starts as the fenced template shows. Inline links in a plan's template fail `AppDocumentationTest`, so the template stays fenced here and the links are written only in STATUS:

```markdown
**Enterprise presentation, plan 13: stripe icons at the user's direction — 2026-10-19** (`claude/enterprise-stripe-icons`, worktree `.claude/worktrees/enterprise-13`, from `main` at `2c065f8cd`; [plan](superpowers/plans/2026-10-19-enterprise-13-stripe-icons.md), [spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md))
```

Then, in the series' style, give:
- the request (quoted) and what it became: Set A; the 13 px upright icon at the reading start with the 4 px gap; the fallback glyph; New UI at 13 px; drag and the ghost; the 12 vendored files and the `toolWindowServer` exception;
- the rulings (this plan's 19, one clause each);
- the deviations from the Enterprise mocks, as the user's 2026-10-19 direction:
  - the stripes' labels carry icons, so each is 17 px longer;
  - the mock tests compare the cut render, with unchanged expectations and floors; this cut is a change of method relative to the approved design ("update honestly to the iconed stripes"), listed explicitly for the user to confirm, beside the 17 px longer labels;
  - the nine stripe pairs read `-text-render.png`, and nine ungated `-iconed` pairs show the real iconed `-render.png` beside the mock (`compare_all.py`);
  - any Step 5 pair;
- the check counts, "N of N" pairs, the byte-identical gallery and the identical 11 jasper-app default-look renders;
- the GUI checks for the user:
  - under Enterprise Light and Dark, each stripe label's icon against the user's screenshot (11.png): Sessions' globe, SFTP's folder, Tunnels' chain link, File Servers' server rack, Event Log's bell, Transfers' arrows, the Sample panel's flask and "About Sample" in the label's colour;
  - closed, hovered and open labels;
  - switching Light and Dark with the stripes showing;
  - dragging a label within a stripe, to the other stripe and between a side stripe and the bottom strip (the label keeps its icon, the gap fits it, the ghost is the icon);
  - Event Log's count pill after its bell;
  - a short window cutting off labels;
  - the About page's IntelliJ icons entry;
  - the default look's rails unchanged;
- end with "Not merged, not pushed."

Add a status banner under this plan's title: `> **Status (2026-10-19): executed on `claude/enterprise-stripe-icons` (`<first>..<last>`).** Deviations from this plan's text: <list, or none>.`

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
./gradlew :jasper-app:test --tests '*AppDocumentationTest'
git add docs
git commit -m "Docs: Enterprise plan 13, stripe icons, at the user's direction

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

Expected: the hygiene script prints nothing, `AppDocumentationTest` passes and the commit succeeds.

## Execution notes (2026-10-19)

**Refresh.** The worktree is on `claude/enterprise-stripe-icons` at `0381b1f0b`, two plan commits on `2c065f8cd`. `git diff 2c065f8cd HEAD` outside `docs/` is empty, so every code anchor is `2c065f8cd`'s, and nothing has merged since. The four refresh greps print `1`, `1`, `1` and `0`. Every path, signature, quoted text and line number in Verified facts, the seven tasks and Task 7's documents was re-read against the worktree (the code, the tests, the role tables, `pairs.json`, the spec, the four guides, `SOURCE.md`, `ATTRIBUTION.txt`, `notices.toml` and the fetch script). They hold, except these, corrected in place:
- `DesignMetrics.Role`'s plan 12 roles end at `:185-190` (was `:186-189`).
- `DockMockTest`: the side stripe tests are `:98-128`, `compareStripe` `:158-203`, `theBottomStripMatchesTheMocks` `:222-285` and `theWholeDockIsArrangedAsTheMocks` `:419-450`.
- `pairs.json`'s nine stripe entries are `:1663-2029`.

The other text changes made in place are rulings R6, R8, R9, R12 and R14. The 12 hashes in Task 1 Step 1 match the research's `mapping.md`. No Gradle ran during the refresh.

**Tasks** (unchanged; one commit each): 1, the classic tool-window icons in jasper-ui; 2, the stripe icon resolver; 3, side stripe labels lead with their icon; 4, the bottom strip's labels lead with their icon; 5, drag keeps the icon and the ghost is the stripe icon; 6, the mock tests and pairs follow the iconed stripes; 7, documentation, STATUS and the whole build.

### Pre-flight scan

| Tasks | Shared file or interface | Finding | Ruling |
| --- | --- | --- | --- |
| 1 × 2 | `IconRegistry.stripeIcon(String, int)` returning `Optional<PreparedIcon>` | Task 2 calls it with the device size `UIScale.scale(13)` (inside 1 to 512) and `.icon(theme, TEXT_PRIMARY)`; agrees. When the presence check runs is not stated. | R1 |
| 1 × 3 | `IconRegistry.stripeIcon` in `StripeIconsTest.classic` and `EnterpriseDockWindowTest` | Agrees. | none |
| 1 × 7 | `SOURCE.md`, `ATTRIBUTION.txt`, the `ui-architecture.md` sentence, spec 10.7's file names | The same six names and twelve files. Agrees. | none |
| 2 × 3 | `AppIcons.stripeIcon(Icon, ChromeArtwork, int, Function)`; `StripeLabels.icon` and `ink` | The logical size `StripeLabels` passes and the state `ink` reads are not stated. | R2, R3 |
| 2 × 5 | The ghost, `labels.icon(icon)` through `AppIcons.stripeIcon`, painted in `DockDropOverlay` | The overlay is not a button, so the ink is the closed, unhovered one (Rulings 7). Agrees. | none |
| 2 × 7 | `docs/app-architecture.md`'s new sentence | Its "else" read as if NEW_UI stripes drew the monochrome glyph. | R14 |
| 3 × 4 | `BottomStrip.java`, `StripeIconsTest`'s helpers, `BottomStripTest` | Task 3's Files said "constructor and `Label` only" while its Step 3 changes the `length` and `paint` calls. Task 4 changes `render` and `paintLabel` only. | R6, R7 |
| 3 × 5 | `LabelledStripe` and `BottomStrip` `beginDrag`; `StripeSet`; `ToolStripe`; `LabelledStripeTest`'s five `beginDrag` calls (`:260`, `:283`, `:315`, `:328`, `:345`, confirmed); `DockDragBottomStripTest`'s constructor call | Task 3 passes `own.icon` for an owned label and `null` for a foreign one; Task 5 replaces only the foreign `null`. Agrees. | none |
| 3 × 6 | `DockMockTest` (Task 3: constructor arguments and `fitted`'s `null`; Task 6: the rewrite); `DockFixture` (Task 3: `CLASSIC`; Task 6: the icons); `StripeIconsTest`'s helpers; `LabelledStripeTest.window(..., Icon)` | `SLICE` is defined two ways (Task 3: sum of the scaled metrics; Task 6: the scaled sum). `reference` returns `int[]`, which Task 6 cannot put under the Vault scrim. | R9, R10 |
| 4 × 5 | `BottomStrip`: `beginDrag` (Task 5) and `render` and `paintLabel` (Task 4) | Disjoint methods. Agrees. | none |
| 4 × 6 | `BottomStrip`'s iconed render and `withoutIcons`' columns in `theBottomStripMatchesTheMocks`; the `-bottom-strip` write | Which existing write becomes `-text` is not stated. | R11 |
| 5 × 6 | none (Task 6 does not drag) | none | none |
| 6 × 7 | `pairs.json` counts | 101 gated pairs unchanged plus nine ungated `-iconed` pairs; Task 7 Step 5's "N of N" stays 101. Agrees. | none |
| 1 to 6 × 7 | Task 1 Step 0's and Task 7 Step 3's default-look renders | An up-to-date `:jasper-app:test` would skip the render tests and leave earlier files to be copied. | R12 |
| 1 | itself | Consistent. The network step is Step 2 (the fetch), not Step 3. | R4, R17 |
| 2 | itself | `aNameWithoutStripeArtDrawsItsMonochromeGlyphUnderClassic` names no `Icon` type and expects the logical 13 where the resolver passes the device size. | R1, R5 |
| 3 | itself | Files against Step 3 on `BottomStrip` (R6); `Point` is not imported (R7); `LabelledStripeTest.fitted(Kit)` keeps the old thickness rule (R8); `SLICE` as a class-load constant (R9). | R6 to R9 |
| 4 | itself | Files names `BottomStripTest`, which no step edits. | R15 |
| 5 | itself | Consistent: `DockDrag` has no `ghost` field today, `drag.overlay()` exists, and `DockDropOverlay` has `highlight()` and `placeholder()` beside which `ghost()` goes. | none |
| 6 | itself | `assertIcons` needs a scrimmed reference image; which writes are `-text`; the write order. | R10, R11 |
| 7 | itself | Consistent once R12 and R14 apply. | R12, R14 |

### Rulings

- **R1. When the resolver looks for a classic file.** `AppIcons.stripeIcon` decides the icon's kind when it is called: empty, a `StripGlyph` unwrapped, a `PresentationIcon` with a path, a named icon, or another `Icon`. For a named icon under CLASSIC, whether `NAMED.stripeIcon(name, deviceSize)` has a file is decided on first paint at that size and cached with the prepared icon (key: artwork, name, device size). — why: Task 2 Step 3 prepares on first paint, and Rulings 4 loads the table only when a CLASSIC stripe paints — cost if wrong: none; deciding at creation would move the first load from paint to render, both under Enterprise only.
- **R2. The logical icon size.** `StripeLabels(Kit, ChromeArtwork)` keeps the logical `kit.metrics().value(STRIPE_ICON)` and passes it to `AppIcons.stripeIcon` as `logicalSize`. `length`, `thickness` and `iconAt` use the scaled `styles.metric(...)`. — why: `AppIcons.stripeIcon` scales `logicalSize` itself, and `NativeStyles` gives only scaled values — cost if wrong: an icon scaled twice at a UI scale other than 1, which `StripeIconTest`'s `UIScale.scale(13)` checks would catch.
- **R3. The label ink's state.** `StripeLabels.ink(Component c)` takes `theme = ThemeZone.theme(c)`. For an `AbstractButton`, `open = isSelected()` and `hover = getModel().isRollover()`, the state `paintLabel` reads; for anything else both are false. — why: the glyph must take the text's ink in every state (Rulings 7 and 14) — cost if wrong: a plugin glyph in another state's ink; `aPluginPanelsOwnGlyphLeadsItsLabel` checks the open ink.
- **R4. The network.** Task 1 Step 2 (`python3 -I tools/icons/fetch-classic-icons.py`) is the only network step: curl from the pinned `raw.githubusercontent.com` URLs, every SHA-256 checked before any write. If the network is unavailable or a hash differs, stop and report. Never hand-write, edit, copy (the scratchpad's `upstream/` included) or substitute an SVG. — why: the user's byte-identical vendoring and the orchestrator's instruction — cost if wrong: unvetted artwork in the product.
- **R5. Task 2's monochrome-glyph test.** `aNameWithoutStripeArtDrawsItsMonochromeGlyphUnderClassic` uses `new PresentationIcon(name, null)` for `history`, `key` and `more.vertical`, and expects `panel(name, theme.semantic(), INK.apply(label), UIScale.scale(13))`, the device size the resolver passes. — why: the other Task 2 bodies use `UIScale.scale(13)`; the tests run at scale 1, so the value is 13 either way — cost if wrong: none at scale 1.
- **R6. `BottomStrip` in Task 3.** Task 3 changes its constructor, builds `new StripeLabels.Label(window, null, this::paintLabel)`, passes `label.icon` to every `length` call (`beginDrag`'s own label and `items`) and `false` for `icon` to `paint`. Step 3's text governs; the Files line now says so. — why: the changed `length` and `paint` signatures (MVP rule, no overloads) force these edits to compile — cost if wrong: none.
- **R7. Imports.** `paintLabel`'s `Point at = labels.iconAt(...)` needs `import java.awt.Point;` in `LabelledStripe` (Task 3) and `BottomStrip` (Task 4), and `StripeLabels` needs it for `iconAt`'s return type. — why: none of the three imports it today — cost if wrong: a compile error that the step's own run shows.
- **R8. `LabelledStripeTest.fitted(Kit)`.** Task 3 changes it to `Math.max(line, metric(STRIPE_ICON)) + 2 * RAIL_EDGE + DOCK_RULE` and its comment to match. Task 3's Test line now says so. — why: Rulings 12 changes the thickness rule. Today's helper equals the new rule only while the line is at least 13, which holds for both fonts it measures (IBM Plex 12 and body 24), so no expectation moves — cost if wrong: none; left alone, the helper would state the old rule.
- **R9. `SLICE`.** Everywhere (Tasks 3 to 6, `DockDragBottomStripTest`, `DockMockTest`) `SLICE` is `StripeIconsTest`'s `metric(STRIPE_ICON) + metric(STRIPE_ICON_GAP)`, the sum of the scaled metrics as `StripeLabels.length` adds them. It is read after `EnterpriseLook.install`: a static method (`slice()`) or a value computed inside each test, never a `static final` set when the class loads. Task 6's `UIScale.scale(STRIPE_ICON + STRIPE_ICON_GAP)` is replaced in place. — why: production's length is the sum of the two scaled metrics, and the two forms differ only at a fractional scale — cost if wrong: none at the tests' scale 1.
- **R10. `StripeIconsTest`'s helpers.** They are package-private `static`, since `DockMockTest`, `DockDragBottomStripTest`, `EnterpriseDockWindowTest` and `DockChromeMatrixTest` call them.
  - `reference(icon, receiver, ground, scale)` paints as `MockCrops.render` does: a `TYPE_INT_RGB` image `icon.getIconWidth() * scale` square, filled with `ground`, then `g.scale(scale, scale)` with default hints and `icon.paintIcon(receiver, g, 0, 0)`. It returns that image's pixels.
  - A companion `referenceImage(...)` returns the image itself. Task 6's `assertIcons` compares `box(render, ...)` with the pixels of `variant.seen(referenceImage(...))`; the scrim is per pixel.

  why: an `int[]` cannot be put under the scrim, and the reference must rasterise as the render does — cost if wrong: false failures under the Vault mock's scrim.
- **R11. Task 6's writes.**
  - Side stripes: before `compareStripe`, write the iconed stripe as `MockCrops.write(variant.name() + "-" + name, ...)`. It is cropped as `compareStripe` crops (x 0 on the left, `2 * thickness - 44` on the right, 44 wide, the full `2 * (height + 2 * SLICE)` height) beside `variant.crop(..., 2 * (height + 2 * SLICE))`. Then `compareStripe(..., name + "-text", withoutIcons(render, stripe, 2), ...)` writes `-text-render.png`.
  - Bottom strip: today's `MockCrops.write(variant.name() + "-bottom-strip", ...)` line becomes `-bottom-strip-text`, over `withoutIcons(render, strip, 2)`, with its unchanged 520 × 50 crops. A new write, `-bottom-strip`, holds the iconed render at `520 + 4 * SLICE` beside `variant.crop(2, 1706, 520 + 4 * SLICE, 50)`.
  - The iconed writes come before any assertion of their test.

  why: the gated pairs must read the cut at today's sizes and the `-iconed` pairs the iconed render at Step 4's sizes; writing first keeps both images when an assertion fails — cost if wrong: a gated pair reading the wrong file, which Step 5's `diff` would show.
- **R12. `--rerun` on the default-look renders.** Task 1 Step 0's and Task 7 Step 3's `ChromeRenderTest` and `DefaultDockPixelsTest` run now carries `--rerun`, as the `DockMockTest` runs already do. — why: an up-to-date `:jasper-app:test` would skip them and the copies would be an earlier run's files — cost if wrong: about a minute per run.
- **R13. Anchors.** The line numbers listed under Refresh are corrected in place; every other anchor stands as written. — why: the code is `2c065f8cd`'s — cost if wrong: none; an executor re-finds a moved anchor and never edits around a missing one.
- **R14. `docs/app-architecture.md`.** Task 7's sentence now reads "...through `AppIcons.stripeIcon`: under CLASSIC, IntelliJ's classic tool-window icon where `IconRegistry.stripeIcon` has one, else the icon's monochrome glyph in the label's ink; under NEW_UI, the icon the rails draw." — why: Rulings 10; the old wording read as if NEW_UI stripes drew the monochrome glyph — cost if wrong: none.
- **R15. `BottomStripTest` in Task 4.** It is run, not edited. Its windows have null icons, so its geometry is unchanged. — why: Task 4 Step 1 adds tests only to `StripeIconsTest` — cost if wrong: none.
- **R16. A flaky test.** A timing-sensitive test that fails once is re-run alone (`--tests` naming that class, with `--rerun`) before it counts as a failure; a second failure is real and is reported. — why: the orchestrator's note — cost if wrong: one extra run.
- **R17. Task 1 Step 0's preconditions.** `git diff --quiet 2c065f8cd HEAD -- . ':(exclude)docs'` still passes after this refresh commit, which touches only this plan, and the tree is clean. — why: checked — cost if wrong: none.
