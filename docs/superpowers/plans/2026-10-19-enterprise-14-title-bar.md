# Enterprise presentation 14: title bar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On 2026-10-19 the user asked: "Next, on MacOS and Windows only - the enterprise theme uses a custom title bar that looks like the IntelliJ title bar, using the same color as the toolbar so it looks cohesive. Mac also moves the menu bar to the global mac menu bar area instead of using built-in, and on windows the title bar contains the file menu bar like in the normal variant of the app." After review they added that only the toolbar height and the macOS lights must match IntelliJ ("the content will be different because the apps are different"), then approved the design. This plan gives the Enterprise look IntelliJ Classic's frame on macOS and Windows, keeps Linux as it is, and makes the Enterprise toolbar 28 px on every platform.

**Architecture:**
- **Model.** A third `[chrome] title_bar` value, `"classic"`, which the Enterprise kit names. `Projections.Host` learns the platform (macOS, Windows or Linux). The 9.1 table resolves `classic` per host: IntelliJ Classic's frame on macOS and Windows, which commits the default look's desktop flags; on Linux, exactly the `native` projections. Each of the six title-dependent projections is decided explicitly and tested.
- **macOS.** `MacTitleBand` (platform) uses IntelliJ Classic's own mechanism. The root pane takes `apple.awt.fullWindowContent` and `apple.awt.transparentTitleBar`, and a 28 pt band in the toolbar's colour sits above the toolbar, so AppKit draws the traffic lights and the frame's title over it. The appearance is `NSAppearanceNameVibrant*`. Full screen removes the band; a double-click acts on the system setting. The menus go to the screen menu bar, as in the default look. Auxiliary frames keep their system title bars and get the minimal File menu.
- **Windows.** FlatLaf's custom title pane, as in the default look. It embeds the classic menus and then `ClassicWindowTitle`, the frame's title left-aligned after them, which dims while inactive. The classic menu row's 1 px rule runs under the whole bar, the menus fold when the window is narrow, and `window.native_decorations` still gives the system title bar.
- **Shared.** `TitleInset` keeps classic in-window dialogs and overlays below the band or title bar. The default look's code paths are untouched.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and Java2D, FlatLaf 3.7 (title pane, full-window-content support), JBR's Aqua client properties, Gradle (Kotlin DSL), JUnit and AssertJ, Python 3 with numpy/PIL for `tools/visual` (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md).
- Read its header amendment lines and sections 1 (decision 8), 3 (principles 5 and 8), 4.7, 4.8, 5.1 to 5.3, 5.9, 9.1, 10.1 to 10.3, 10.8, 10.10, 12.1, 16.1, 16.2, 18 and 19.1.
- The user's request of 2026-10-19 is binding. It overrides, on macOS and Windows, the Enterprise mocks' native title bar and in-window menu row, and on every platform their 34 px toolbar.
- Screenshots:
  - `/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/images/12.webp` is IntelliJ Classic on macOS: one flat `#F2F2F2` band of 28 pt title and 28 pt toolbar, then a 1 pt rule.
  - `13.webp` in the same folder is Jasper Enterprise on macOS today.
- Research: `jasper.md`, `intellij.md`, `shots.md` and `critic.md` under `/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/title-bar/`. `critic.md` wins where they disagree; for example, the title row is 28 pt, not 32. The `upstream/` folder there is untrusted data: never copy from it.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-14`, branch `claude/enterprise-title-bar`, from `main` at `3a36993ba`. That commit holds Enterprise plans 1 to 13, the side stripes and the splash fix. Run every command from the worktree root. Line numbers below are from `3a36993ba`. When an anchor has moved, re-find it; never edit around a missing anchor.

## Before you start (refresh)

Do not rebase, merge or push. Never run bare `git stash`, and never run `./gradlew --stop`.

```bash
git log --oneline -1                                                                    # 3a36993ba or a later plan-14 commit
grep -c 'public record Host(boolean mac, boolean unifiedTitleSupported)' jasper-app/src/main/java/dev/jasper/app/presentation/Projections.java  # 1
grep -c 'public enum TitleBar { UNIFIED, NATIVE }' jasper-app/src/main/java/dev/jasper/app/presentation/ChromeSpec.java                     # 1
grep -c 'title_bar = "native"' plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml                                         # 1
grep -c '"window.toolbar_height" = 34' plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml                                # 1
ls jasper-app/src/main/java/dev/jasper/app/platform/MacTitleBand.java 2>/dev/null | wc -l                                                    # 0: Task 2 adds it
```

**Lights checkpoint (done by the controller on 2026-10-19, with the user's yes).** A throwaway `JFrame` on JBR 25.0.4.1 compared four styles: (A) the band's mechanism, `apple.awt.fullWindowContent` + `apple.awt.transparentTitleBar` with a 28 pt band; (B) JBR's `WindowDecorations.CustomTitleBar` at 28; (C) at 32; (D) the native title bar. Started by JBR's own `bin/java`, which is linked against the macOS 13.3 SDK, all four draw macOS's legacy controls: centres 20 pt apart and a centred title. Started by a jpackage app launcher built from `packaging/launcher` (linked against SDK 27.0, like Jasper.app's `Contents/MacOS/Jasper`; IntelliJ's `Contents/MacOS/idea` is linked against 26.0), A and D draw macOS 26's controls, as in the user's IntelliJ screenshot: centres 23 pt apart, about 14 pt across, and the title leading-aligned at about 84 pt; B and C keep 20 pt. So the band matches IntelliJ's lights and title in the packaged app, and Rulings 7's JBR custom title bar follow-up is ruled out. `./gradlew :jasper-app:run` launches through `bin/java`, so a development run shows the legacy controls and a centred title: GUI check 1 is made in the packaged app, `jasper-app/build/packaging/image/Jasper.app` from `./gradlew :jasper-app:packageApp` (headless without `-Pjasper.aot=true`). Evidence: the controller's scratchpad `lights-test/all.png` (bin/java) and `all-sdk27.png` (the app launcher). No further launch is needed during execution.

## Global Constraints

**Build and safety**
- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain. Always use `./gradlew`, never a system `gradle`, and never run `./gradlew --stop`: other sessions share the daemons.
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache. The one exception is the lights checkpoint below, and only on the user's own yes in chat for that batch. Headless JUnit renders and `:jasper-ui:gallery` may run. Everything AppKit, DWM or a real window does is a GUI check for the user (Task 8).
- Work only in the worktree above, with one commit per task. Every commit message ends with a blank line, `🤖 Generated with Claude Code at The Home Depot`, a blank line, then `Co-Authored-By: Claude <noreply@anthropic.com>`. Do not push, merge or rebase. Never dispatch subagents from inside a task.

**The default look stays byte-identical on every platform.** Every file the headless gallery writes for Slate Cobalt Dark and Light (`dark-1x`, `light-1x`, 161 files each) must equal the Task 1 Step 0 baseline. So must jasper-app's 11 default-look renders (`ChromeRenderTest`'s default, complement and fallback chrome, palette and settings images, and `DefaultDockPixelsTest`'s `default-dark` and `default-light`). Task 8 proves both. These changes could reach the default look, and each is kept from it:
- `Projections.of` keeps every value for `title_bar = "unified"` and `"native"`. Windows and Linux give equal projections for them (Task 1 tests it).
- `WindowContent.installCustomTitleBar`, `decorateTitleBar`, `MacTitleBar` and `TitleBarStyle` are not edited. The classic paths are siblings.
- `CollapsingMenuBar` changes layout only when a title is set (never in the default look).
- `WindowOverlay.layout` and `NativeShells.inWindow` add `TitleInset.of(root)`, which is 0 for every root the default look builds.
- No global `UIManager` key changes.
- `CustomTitleBarTest`, `MacTitleBarTest`, `DefaultDockPixelsTest` and the gallery stay unedited and green.

**Fallback.** On macOS and Windows a classic title commits `DesktopFlags.STANDARD`, so `Presentation.fallback` is exactly the default look there (spec 4.7). Linux keeps `DesktopFlags(false, true)`.

**Linux is unchanged** apart from the 28 px toolbar: the system title bar and the in-window classic menu row.

**Code rules**
- Javadoc doclint runs on jasper-app (`-Xdoclint:all`, errors fail the build).
  - Every `{@link}` must resolve.
  - No comment may contain a raw `<`, `>`, `&` or `->`.
  - New public types and methods carry a comment with `@param` and `@return` where they apply. Follow each file's existing pattern for enum constants.
- `docs/design/theme-engine/migration.csv`: every new source under jasper-app or plugins that `ThemeMigrationCoverageTest.CANDIDATE` matches (it imports `javax.swing`, `java.awt` or `com.formdev`, among others) gets its row in the same commit.
  - A row has exactly eight fields with no commas inside a field: `path,module,Name,nonvisual-helper,none,none,<test path>,<disposition>`.
  - The disposition begins `New in Enterprise plan 14 Task N;` and uses semicolons.
- `ColourLiteralScanTest` stays green: no production Java gains a colour literal. Colours come from roles.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters. Write `—` for an em dash in Java strings, as `WindowTitle` does.
- MVP rule (no compatibility shims). Two convenience constructors are added on purpose: `ShellLook(boolean, boolean, boolean)` and `WindowLook(WorkspaceChrome, boolean, boolean)`. They mean "no classic title" and are the value for every look but one; they are not compatibility (Rulings 5).

**Tests and documents**
- The Enterprise mock tests render under an explicit Linux host, where the mocks stay true; the macOS and Windows frames have no mock (spec 16.1.4 renders them for inspection).
- A mock expectation that changes for the 28 px toolbar is recorded as a deviation naming "the user's 2026-10-19 direction". A pair's floor moves only to a measured value, with a note. Never lower a floor to pass.
- Documentation edits are the smallest that make each document accurate. The spec carries the one header amendment line that Task 8 writes. Earlier `docs/STATUS.md` entries and `docs/superpowers/` history are not rewritten.

## Review Focus

1. **Another theme picked under Enterprise, especially one with its own `surface.titlebar`** (Ubuntu Yaru, or any imported IntelliJ theme). The band (macOS) and the title bar (Windows) must match the toolbar exactly: the header's `surface.app`, never the title-bar zone's colour.
   - Tests: Task 3 `ClassicMacTitleTest.anotherThemeKeepsTheBandInTheToolbarsColour`; Task 4 `ClassicWindowsTitleTest.theTitleBarIsTheToolbarsColourWithTheMenusThenTheTitle`.
2. **Entering and leaving macOS full screen.** The band must disappear, so the toolbar meets the screen's top edge, and come back on exit. Classic dialogs and overlays must re-place against the new title inset.
   - The band announces each inset change (`TitleInset.CHANGED`), because FlatLaf can republish full screen after the last resize.
   - Tests: Task 2 `MacTitleBandTest.fullScreenRemovesTheBandAndLeavingRestoresIt`; Task 3 `ClassicMacTitleTest.fullScreenTakesTheBandOutOfTheWindow`; Task 5 `ClassicWindowOverlayTest.classicDialogsStartBelowTheClassicTitle` and `theLayerAndTheCardFollowATitleInsetChangeWithoutAResize`.
3. **A narrow Windows window.** The menus fold into "…" and then ☰ before the title shrinks past 60 px. The title never runs under the window buttons and never gets a negative width, and the window buttons never shrink below their 44 px.
   - Tests: Task 4 `CollapsingMenuBarTest.aClassicTitleFollowsTheMenusAndTheMenusFoldBeforeItShrinksPastItsMinimum` and `ClassicWindowsTitleTest.aNarrowWindowFoldsTheMenusAndClipsTheTitle`.
4. **Windows with `window.native_decorations = true`, or in Compact.** The system title bar, the in-window classic menu row and the header as the first row, exactly as on Linux, with no title inset.
   - Tests: Task 1 `DesktopFlagsTest.aClassicTitleCommitsTheDefaultLooksFlagsOnMacOsAndWindowsAndTheSystemTitleOnLinux`; Task 4 `ClassicWindowsTitleTest.withoutDecorationsTheMenusStayARowOfTheWindow`; Task 3 `ClassicMacTitleTest.compactKeepsTheSystemTitleBarWithoutTheBand`.
5. **Enterprise fails at start on macOS or Windows.** The fallback must be exactly the default look, including the screen menu bar and the unified macOS title bar, because the classic title committed the default look's flags.
   - Test: Task 1 `ProjectionsTest.aClassicTitleFallsBackToExactlyTheDefaultLookOnMacOsAndWindows`.

## Verified facts

At `3a36993ba`. Paths are under `jasper-app/src/main/java/dev/jasper/app/` unless they start with a module or `/`.

**The model**
1. `Projections.Host(boolean mac, boolean unifiedTitleSupported)` (`presentation/Projections.java:33`). It is built at `plugins/PluginSelection.java:126-129` (from `os.name` and FlatLaf `SystemInfo`, no AWT) and at `settingsui/PageContext.java:56-59`.
   - Test sites: `ProjectionsTest.java:13`, `DesktopFlagsTest.java:56`, `SettingsChromeTest.java:73`, `EnterpriseSettings.java:153`, `ChromeRenderTest.java:39`, `EnterpriseDockWindowTest.java:40` and `EnterpriseMenuRowMockTest.java:72` (all under `jasper-app/src/test/java/dev/jasper/app/`).
   - `RunWindowWorkspaceRenderTest.java:42` uses `ChromeRenderTest.HOST`.
2. `Projections.of` (`:52-77`) reads `title_bar` six times:
   - `:55` `DesktopFlags(menu_bar != WINDOW, title_bar == NATIVE)`;
   - `:57` `WorkspaceLook.header` CLASSIC iff NATIVE;
   - `:64` `ShellLook(UNIFIED && unifiedTitleSupported, windowMenus, nativeTitle)`;
   - `:68` hides `window.native_decorations` iff NATIVE or macOS;
   - `:71` `SettingsChrome.integratedTitle = !NATIVE`;
   - `:73` `DialogMapping.windowTitled = nativeTitle`.

   `Projections.fallback` (`:98-110`) builds from the committed flags. `fallback(DesktopFlags.STANDARD, host)` equals the default look (`ProjectionsTest.java:62-64`).
3. `ChromeSpec.TitleBar { UNIFIED, NATIVE }` (`presentation/ChromeSpec.java:23`). `CLASSIC` is the classic base, with title `NATIVE` (`:45-46`). The constraint is at `:56-57` and its parser copy at `presentation/KitParser.java:307-308`. `KitParser.point` lists values in enum order in its error (`:286-292`).
4. `ShellLook(unifiedTitle, windowMenus, followThemeAppearance)` (`presentation/ShellLook.java:10`) is constructed 14 times (`git grep -n "new ShellLook("`). `WindowLook(chrome, unifiedTitle, followThemeAppearance)` (`workspace/WindowLook.java:15`, `of` `:25-28`, `standard()` `:35`) is constructed 12 times in tests (14 with `of` and `standard()`).
5. The shipped kit: `plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml:5` `title_bar = "native"` and `:72` `"window.toolbar_height" = 34`.
   - `EnterpriseLook.CHROME` (`jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java:19-22`) must equal the shipped table (`EnterpriseFixtureTest.java:28-29`).
   - `PresentationConformanceTest.java:84`, `application/EnterpriseFirstWindowTest.java:40` and `plugins/EnterpriseHomeTest.java:36` assert the shipped 34.
   - `window.toolbar_height` is 28 to 64 (`config/ConfigSnapshot.java:24`).
   - **The user's own `~/.config/jasper/config.toml:15` saves `toolbar_height = 34`** (and `:12` `toolbar = "icons_and_labels"`). A saved value always overrides a look's default (spec 5.8). Read-only: never edit it.

**Bootstrap and shells**

6. `ApplicationBootstrap.applyDesktop` (`bootstrap/ApplicationBootstrap.java:216-224`) runs before AWT (`:107`).
   - It sets `apple.laf.useScreenMenuBar` from the committed flags (`:233-238`).
   - It then picks the title style with `TitleBarStyle.choose(mac, nativeDecorations, compact, nativeTitle)`, which gives `MAC_UNIFIED` on macOS, `CUSTOM` elsewhere, or `NATIVE` for a native title, Compact or native decorations (`platform/TitleBarStyle.java:16-36`).
   - `configure()` sets `flatlaf.useWindowDecorations` process-wide (`:39-45`). FlatLaf's per-root `JRootPane.useWindowDecorations` cannot override that system property: `FlatUIUtils.getBoolean` checks the system property first (FlatLaf 3.7 `FlatUIUtils.java:275-289`).
7. About Jasper goes to the application menu when the screen menu bar is on: `AboutWindows.aboutInApplicationMenu(macOs, screenMenuBar)` (`application/AboutWindows.java:71`), fed `presentation.committedDesktop().screenMenuBar()` at `application/JasperApplication.java:1082`. The Desktop About and Preferences handlers are installed whatever the look (`:403-410`).
8. Auxiliary frames get the minimal File menu when `bar != null || shell.frameMenuRow()` (`windows/NativeShells.java:307-310`). `frameMenuRow = !classicMenus` (`windows/ShellChrome.java:54`), and `classicMenus` is `ShellLook.windowMenus` (`:31`).

**The terminal window today**

9. `WindowContent.mount` (`workspace/WindowContent.java:326-334`):
   - it runs `installTitleBar(root, content, look.unifiedTitle(), look.followThemeAppearance(), nativeTitle)`;
   - if there is no bar, it runs `installCustomTitleBar(root, content)`;
   - under a native-titled look, `content.chrome().chromeRow()`;
   - then `bar.setMenuBar(menuBar)` or `root.setJMenuBar(menuBar)`.
10. `installTitleBar` (`:346-388`):
    - In Compact it sets the content pane to `content` with no bar (`:349-351`). Otherwise it calls `MacTitleBar.install(..., supported)`, which sets the content pane first and returns null before any root property when unsupported (`platform/MacTitleBar.java:72-77`).
    - `content.onTitle` formats the frame title with `WindowTitle.format` into `nativeTitle` (`:359-372`).
    - A barless native-titled macOS frame follows the theme with `MacAppearance.follow(root, dark)` (`:379-383`), which sets Aqua and DarkAqua names (`platform/MacAppearance.java:22-24`).
    - The body lease is installed at `:384-385`.
11. `installCustomTitleBar` (`:397-411`) decorates now, or when FlatLaf's decorations arrive with the peer. `decorateTitleBar` (`:413-428`):
    - moves the header into the menu bar (`CollapsingMenuBar.setHeader`, `workspace/CollapsingMenuBar.java:64-71`, which adds a trailing glue);
    - sets `TITLE_BAR_SHOW_TITLE=false`, `TITLE_BAR_HEIGHT=toolbarHeight`, `TITLE_BAR_BACKGROUND=ThemeZone.color(null, SURFACE_APP)` and `TITLE_BAR_FOREGROUND=TEXT_SECONDARY`, refreshed through `content.onThemeChanged`.
12. The header's fill is `ThemeZone.color(header, SURFACE_APP)` (`workspace/WindowChrome.java:588`, in `refreshTheme` `:586-594`). `chromeRow()` (`:185-192`) makes it chrome, not a title-bar zone. `ThemeZone.titleBar` paints with `TitleBarTheme` (`platform/ThemeZone.java:31-38`).
13. The classic menu row style (`platform/ClassicMenuRow.java:49-69`):
    - 24 px items on `surface.app`, with a `CompoundBorder(MatteBorder(bottom = HAIRLINE, border.divider), EmptyBorder(MENU_BAR_SIDE))`;
    - opaque;
    - re-applied on every theme change (`WindowChrome.java:586-587`).
14. `CollapsingMenuBar`:
    - `fit(space)` (`:103-117`) keeps leading menus, then "…", then ☰;
    - `layoutContainer` folds against the bar's own width when it has no header (`:196-209`);
    - the glue goes last (`:68`, `:208`);
    - `shownWidth` skips the header and the glue (`:121-126`).
15. `WindowContent.setActive` (`:1491-1496`) is called from `TerminalWindow` on activation (`workspace/TerminalWindow.java:39-40,150-153`). The field `active` starts `true` (`:133`). `close()` clears `onThemeChanged` (`:1541`).

**Dialogs and overlays**

16. `NativeShells.inWindow` calls `dialog.windowTitleHeight(owner.getInsets().top)` (`windows/NativeShells.java:216`). `followTitleBar(Container window, ClassicDialog dialog)` re-reads the inset on resize (`:245-249`); its one test caller is `ClassicWindowOverlayTest.java:72`.
17. `WindowOverlay.layout` puts the layer at `(0, 0)` over the whole layered pane (`windows/WindowOverlay.java:126-131`). `ClassicDialog.placeCard` puts a SHEET at y 0 and centres CENTERED on the whole window with `windowTitle` (`jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicDialog.java:155,186-192`).

**JBR and FlatLaf (checked in the JBR 25 `src.zip` and `flatlaf-3.7-sources.jar`)**

18. `sun.lwawt.macosx.CPlatformWindow`:
    - It defines `apple.awt.fullWindowContent`, `apple.awt.transparentTitleBar`, `apple.awt.windowTitleVisible` and `apple.awt.windowAppearance` (`:144-147`).
    - The title is visible by default: the initial style bits include `TITLE_VISIBLE` (`:443`), and a null `windowTitleVisible` means visible (`:285-287`).
    - `windowAppearance` takes an `NSAppearance` name as a `String` (`:289-294`).
    - IntelliJ Classic sets the first two on the root pane and `NSAppearanceNameVibrantLight`/`NSAppearanceNameVibrantDark` by theme (`intellij.md` 1.1 and 1.3).
    - These are the Java-side properties. What AppKit then draws was verified by the lights checkpoint: IntelliJ's lights and leading title in the packaged app, the legacy ones under `bin/java`.
19. FlatLaf republishes the root's `FlatLaf.fullWindowContent.buttonsBounds` (`FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS`) whenever macOS full screen toggles (`FullWindowContentSupport.java:134-173`, installed for every FlatLaf root on macOS, `FlatRootPaneUI.java:197-204`). In full screen its width is 0: the placeholder option `zeroInFullScreen` keys on `width == 0` (`FullWindowContentSupport.java:75-77`; `FlatClientProperties.java:335`). `MacTitleBar` already listens to that property (`MacTitleBar.java:105`).
20. FlatLaf's title pane (`FlatTitlePane.java`):
    - `activeChanged` gives a client `TITLE_BAR_FOREGROUND` to the title in both states, so FlatLaf's own title would not dim (`:456-470`).
    - With leading embedded menus the title is centred unless `centerTitleIfMenuBarEmbedded` is off, and starts `menuBarTitleGap` 40 after them (`:1329-1368`; `FlatLaf.properties:828-831`).
    - `TITLE_BAR_HEIGHT` sets the button panel's height (`:384-388`). Buttons are 44 × 30 by default (`FlatLaf.properties:818`) and `TitlePane.titleMinimumWidth` is 60 (`:817`).
    - A menu bar ending in a horizontal glue is widened over the title label up to the buttons (`:343-354`, `:684-693`).
    - The embedded menu bar's border bottom inset is added to the title pane, and that border is painted across the whole title pane (`FlatTitlePaneBorder`, `:1234-1275`).
    - A component with no mouse listeners is title-bar caption: it drags and double-click maximises (`:1147-1208`).
    - `TitlePane.menuBarEmbedded` is true by default (`FlatLaf.properties:809`).
    - The content pane starts below the title pane in the layered pane (`FlatRootPaneUI.java:640-676`).
21. A headless `JRootPane` with `setWindowDecorationStyle(JRootPane.FRAME)` and `updateUI()` gets FlatLaf's title pane. `CustomTitleBarTest.java:17-23,67-87` lays it out and measures it.
22. Jasper has no JNA on jasper-app's compile path (JNA is `jasper-terminal`'s `implementation`, `jasper-terminal/build.gradle.kts:8`). IntelliJ reads `AppleActionOnDoubleClick` from the global domain and zooms unless it is not "Maximize", when it minimises (`MacMainFrameDecorator.kt:134-166`, untrusted copy; read only).
23. Contrast (Enterprise Light on `#F2F2F2`): `text.secondary` `#808080` is 3.53:1 and `text.tertiary` `#8C8C8C` is 3.0:1. IntelliJ's title `#6E6E6E` is 4.55:1 and its inactive `#999999` is 2.54:1. In Dark on `#2B2D30`, `#6F737A` is 2.9:1 and `#5A5D63` is 2.09:1. `SurfaceContrast.Guard.TEXT` is 4.5 and `INDICATOR` is 3 (`jasper-ui/src/main/java/dev/jasper/ui/theme/SurfaceContrast.java:15-17`).

**Renders, tests and pairs**

24. `ChromeRenderTest`:
    - `HOST` is the real host (`:39`);
    - its cases are at `:76-87`;
    - `check` asserts that a barless root's content meets the menu row (`:99-105`);
    - the window loop is at `:157-178` and the Settings loop at `:181-196`.

    `DesktopTestSupport.content(launcher, themes, look)` takes `macOs` from `os.name` (`jasper-app/src/test/java/dev/jasper/app/workspace/DesktopTestSupport.java:78-84`).
25. `EnterpriseWorkstationMockTest` mounts through `EnterpriseMenuRowMockTest.mounted` with `setToolbarHeight(34)` (`:31-39`) and asserts:
    - toolbar row lengths (`:66-84`);
    - toolbar inks and the field (`:87-127`);
    - tab rows at `2 * (25 + 35 + …)` (`:138,144,146`);
    - status from the bottom (`:150-176`).
26. `ClassicHeaderTest.window` sets 34 (`:67`). `theClassicRowIsTheMocks` asserts height 35, first button y 5 and field y 6 (`:94-111`).
27. `tools/visual/pairs.json`:
    - `chrome-toolbar-light` and `-dark` crop the mock at `[2, 110, 2880, 70]` and `enterprise-*-workstation.png` at `[0, 50, 2880, 70]`;
    - `chrome-menu-row-*` crop `-window.png` at `[0, 0, 2880, 50]`;
    - `chrome-tabs-*` read the test's `-tabs` cut.

    `compare_mock.py` never resizes and pads mismatched crops with magenta (`:91-98`).
28. The base: `./gradlew check` gives **11,514 tests, 0 failures, 0 errors, 25 skipped**, and `check_pairs.py` 101 of 101 gated pairs (STATUS top entry).

## Rulings

Each is "what — why — cost if wrong".

1. **One plan, eight tasks.** The work is about 30 files and every task leaves `check` green, so no 14a/14b split. — Cost: none.
2. **A third `title_bar` value, `"classic"`, over a per-host meaning of `native`.** `native` keeps meaning "the OS title bar everywhere", and the new name says what it draws: IntelliJ Classic's frame on macOS and Windows, the system's on Linux. — Cost: one enum value and the six projection rules below.
3. **`Projections.Platform { MAC, WINDOWS, LINUX }`, read from `os.name` without AWT.** `LINUX` stands for every other system. `Host(Platform platform, boolean unifiedTitleSupported)` keeps a `mac()` method. — Cost: a BSD desktop counts as Linux, which is what it already gets.
4. **The classic base keeps `title_bar = "native"`.** Only the Enterprise kit names `classic`, so a third-party `base = "classic"` kit keeps its behaviour, and the existing classic-look tests stand. — Cost: one constant.
5. **The six projections under `classic`** (on Linux every one equals `native`'s):
   - **`DesktopFlags`:** `screenMenuBar = menu_bar != window`, or any classic title (macOS or Windows); `nativeTitle = native`, or classic on Linux. macOS and Windows therefore commit `STANDARD`, so the fallback there is the default look (spec 4.7). `screenMenuBar` reaches only `apple.laf.useScreenMenuBar`, `AboutWindows.aboutInApplicationMenu` (gated on macOS) and `Projections.fallback`, so on Windows it changes nothing but the fallback.
   - **`WorkspaceLook.header`:** CLASSIC unless `unified`.
   - **`ShellLook`:** it gains `classicTitle`, true for classic on macOS or Windows. `windowMenus = menu_bar == window`, except under a classic title on macOS, where it is false: macOS auxiliary frames get the File menu for the screen menu bar, and Windows auxiliary frames still show no menu row. It is therefore not `!screenMenuBar` under a classic title on Windows. `followThemeAppearance` is true unless `unified`. `unifiedTitle` is unchanged.
   - **`window.native_decorations`:** hidden for a native title or on macOS; visible under classic on Windows, which honours it.
   - **`SettingsChrome.integratedTitle`:** only under `unified`, so Settings keeps its titled system or FlatLaf bar.
   - **`DialogMapping.windowTitled`:** unless `unified`, since every classic auxiliary bar shows its title.

   The 3-argument `ShellLook` and `WindowLook` constructors mean `classicTitle = false`.

   — Cost: each is pinned by a test; a wrong one is a one-line change.
6. **The classic composition, with menus where the platform puts them.** `menu_bar = "window"` keeps `ClassicMenuBar` everywhere. Under a classic title on macOS the screen menu bar shows those menus, with About Jasper and Settings… in the application menu (Verified 7), whatever `menu_bar` says. — Cost: a classic-titled kit cannot have in-window menus on macOS, which is IntelliJ's rule.
7. **The macOS band is IntelliJ Classic's mechanism, not `MacTitleBar`.**
   - It is a root-pane `fullWindowContent` + `transparentTitleBar` frame with a 28 pt band, unscaled because the native title bar does not scale.
   - The band paints the header's own fill (`ThemeZone.color(header, SURFACE_APP)`), with no rule. There is no JBR `CustomTitleBar`.
   - `windowTitleVisible` is never set, so AppKit draws the frame's existing title.
   - — Cost: none for the lights and the title: the lights checkpoint showed the band's mechanism drawing IntelliJ's lights (23 pt apart) and leading-aligned title when Jasper starts from its app launcher. A development run through `bin/java` shows macOS's legacy controls and a centred title, which is macOS's choice by the launcher's SDK, not this plan's.
8. **Appearance under the band is `NSAppearanceNameVibrantLight`/`VibrantDark`, by `theme.chrome().dark()`, as IntelliJ sets it.** Every other native-titled frame keeps Aqua and DarkAqua. — Cost: if the vibrant names render worse, two string constants change.
9. **Full screen is FlatLaf's republished buttons bounds with width 0** (Verified 19): the band hides, then shows again on any other value (null included). — Cost: if JBR reports otherwise, the band stays in full screen; a follow-up asks `FlatNativeMacLibrary.isWindowFullScreen`.
10. **The band handles a double-click itself, as IntelliJ Classic does.**
    - `MacTitleDoubleClick` reads `AppleActionOnDoubleClick` once per process with `/usr/bin/defaults read -g`, on a virtual thread started at the first band. There is no JNA in jasper-app.
    - "Minimize" minimises, "None" does nothing, and "Maximize", "Fill", unset or anything else zooms (toggles `MAXIMIZED_BOTH`).
    - — Cost: if AppKit already zooms on a full-content title (GUI check), a double-click toggles twice; the follow-up removes the listener.
11. **Dragging the band is left to AppKit, as in IntelliJ Classic** (it has no drag code). — Cost: if it does not drag (GUI check), a follow-up adds a native-drag hit area.
12. **macOS auxiliary windows keep their system title bars, and their frames show the minimal File menu in the screen menu bar** (New Window, Close Window), as the default look gives them. Dialogs show the application menu, as before. — Cost: a fuller menu is a follow-up.
13. **On Windows the title is Jasper's `ClassicWindowTitle`, inside the menu bar, not FlatLaf's title label.**
    - FlatLaf's label would not dim with a client foreground and would start 40 px after the menus (Verified 20). Changing that needs global `UIManager` keys, which the default look must not see.
    - IntelliJ's layout: the title starts 22 px after the menus (its 10 px menu gap plus 12 px border). The menus fold before the title drops below FlatLaf's 60 px `titleMinimumWidth`, and the window buttons keep their 44 px: the bar's preferred width is only its folded minimum (Task 4 Step 3), because FlatLaf squeezes the buttons before its left panel.
    - The title bar is 28 px (`TITLE_BAR_HEIGHT`), IntelliJ's computed 28 and the band's height, so the window buttons are 44 × 28.
    - This departs from the approved design's W1, which names FlatLaf's own mechanism (`centerTitleIfMenuBarEmbedded` off). Task 8 records it as a deviation in the banner and in STATUS.
    - — Cost: if the user wants FlatLaf's title, a follow-up swaps the label for its keys.
14. **Windows title colours.** Active: `text.secondary` on `surface.app` under `Guard.TEXT`; IntelliJ Light's `#6E6E6E` sits on that 4.5 floor. Inactive: `text.tertiary` under `Guard.INDICATOR`; IntelliJ's inactive `#999999` is 2.5:1, and a text guard would undo the dimming. Both are declared in `WindowPairs` and sampled by `WindowPairsTest`. The font is the plain UI body face. — Cost: a guard choice.
15. **The Windows rule is the classic menu row's own 1 px `border.divider`.** FlatLaf paints it across the whole title pane (Verified 20). It is the nearest role to IntelliJ's `MenuBar.borderColor` `#D9D9D9`, and it is already the Linux row's rule. — Cost: none.
16. **Windows auxiliary frames and dialogs take FlatLaf's title panes,** as in the default look on Windows: `TitleBarStyle.CUSTOM` is process-wide, the per-root opt-out cannot override it (Verified 6), and they show no menu row (plan 10b). — Cost: keeping native auxiliary bars on Windows needs a `TitleBarStyle` change, as a follow-up.
17. **`window.native_decorations = true` on Windows under Enterprise** gives `TitleBarStyle.NATIVE`: the system title bar, the in-window classic row and no title inset, exactly as Linux. Settings shows the row there. — Cost: none.
18. **Compact under a classic title keeps the system title bar on macOS and Windows,** as the default look's Compact does (`TitleBarStyle.NATIVE`), with no band. macOS keeps the screen menu bar; Windows shows the in-window row. — Cost: none.
19. **`TitleInset`, a root client property published only by the band and by the Windows classic decoration.**
    - It is the band's height, or the content pane's top under FlatLaf's title pane.
    - The overlay layer starts below it.
    - A classic dialog's `windowTitleHeight` is the frame's top inset plus it, so CENTERED stays centred on the whole window and a SHEET hangs under the title.
    - Linux, native decorations and the default look publish nothing.
    - — Cost: none.
20. **The toolbar is 28 px as the Enterprise kit's `window.toolbar_height` default** (IntelliJ's), on every platform. A saved value still overrides it. The user's `config.toml` saves 34, so STATUS asks them to remove that line or Reset it in Settings before the GUI check. — Cost: none.
21. **The mock tests.**
    - `EnterpriseMenuRowMockTest`, `EnterpriseWorkstationMockTest`, `RunWindowWorkspaceRenderTest` and `EnterpriseDockWindowTest` run under an explicit Linux host.
    - The workstation mock test and `ClassicHeaderTest` render the 28 px row. Their expectations shift by the 12 device px (6 px at 1×) the mocks' 34 px row is taller, each line marked as the user's 2026-10-19 direction.
    - The toolbar pairs compare the mock's middle 56 device rows with the render's 56-row toolbar row (equal heights, so `compare_mock.py` adds no padding), or 58 rows each with the rule if the pair's colours need it. Floors move only to measured values.
    - — Cost: none.
22. **`ChromeRenderTest` gains `enterprise-mac` and `enterprise-windows`.**
    - `enterprise` moves to the Linux host. Its image is unchanged: the host changes no pixel of a native-titled window.
    - The default, complement, fallback and covering cases keep the real host, for byte identity.
    - The macOS print hides the root's menu bar, which AppKit shows in the screen menu bar.
    - The Windows case decorates its root as `CustomTitleBarTest` does.
    - — Cost: none.
23. **No version bump** for the SDK, Enterprise or any plugin. The kit's digest changes, so the attempt record re-arms (spec 4.6). The plugin description is corrected. — Cost: none.
24. **The unmerged `claude/workspace-tabs` series** rules that a child window has no menu bar under Enterprise. This plan does not touch that; STATUS records that whichever merges second reconciles it. On macOS a child window will need the screen menu bar's menus. — Cost: a merge task.

---

### Task 1: The classic title in the model

**Files:**
- Modify:
  - `jasper-app/src/main/java/dev/jasper/app/presentation/Projections.java` (`Platform`, `Host`, `of`)
  - `jasper-app/src/main/java/dev/jasper/app/presentation/ChromeSpec.java:22-23,56-57`
  - `jasper-app/src/main/java/dev/jasper/app/presentation/KitParser.java:307-308`
  - `jasper-app/src/main/java/dev/jasper/app/presentation/ShellLook.java`
  - `jasper-app/src/main/java/dev/jasper/app/plugins/PluginSelection.java:125-129`
  - `jasper-app/src/main/java/dev/jasper/app/settingsui/PageContext.java:55-59`
- Test:
  - `jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/presentation/KitParserTest.java:207-209,257,268`
  - `jasper-app/src/test/java/dev/jasper/app/bootstrap/DesktopFlagsTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/windows/ShellChromeTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/application/AboutWindowsTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java` (helper)
- Modify (host conversions only), all under `jasper-app/src/test/java/dev/jasper/app/`:
  - `settingsui/SettingsChromeTest.java:73`, `settingsui/EnterpriseSettings.java:153`
  - `workspace/ChromeRenderTest.java:39,79-80`
  - `workspace/EnterpriseDockWindowTest.java:40`, `workspace/EnterpriseMenuRowMockTest.java:72`
  - `workspace/RunWindowWorkspaceRenderTest.java:42`

**Interfaces:**
- Produces:
  - `public enum Projections.Platform { MAC, WINDOWS, LINUX }` with `public static Platform of(String osName)`: a name starting `Mac` gives MAC, one starting `Windows` gives WINDOWS, anything else (null included) gives LINUX.
  - `public record Projections.Host(Platform platform, boolean unifiedTitleSupported)`, with a non-null platform, and `public boolean mac()`.
  - `ChromeSpec.TitleBar.CLASSIC` (declared last, so errors list `unified, native, classic`).
  - `public record ShellLook(boolean unifiedTitle, boolean windowMenus, boolean followThemeAppearance, boolean classicTitle)`, with the convenience constructor `public ShellLook(boolean unifiedTitle, boolean windowMenus, boolean followThemeAppearance)` meaning `classicTitle = false`.
  - Test-only `public static ChromeSpec EnterpriseLook.titled(ChromeSpec spec, ChromeSpec.TitleBar title)`: `spec` with `title`.

- [ ] **Step 0: Capture the baseline** (no commit)

Before any source change, render the default look and run the pairs from `3a36993ba`'s code. Task 8 compares against these files.

```bash
base=/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan14-baseline
git diff --quiet 3a36993ba HEAD -- . ':(exclude)docs' && echo "code is 3a36993ba's"   # must print; otherwise stop
test -z "$(git status --porcelain)" && echo clean                                       # must print
rm -rf "$base" jasper-ui/build/gallery && mkdir -p "$base/app"
for theme in dark light; do
  ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=$theme && cp -R "jasper-ui/build/gallery/$theme-1x" "$base/gallery-$theme-1x"
done
./gradlew :jasper-app:test --continue
for f in jasper-app/build/chrome-renders/chrome-default*.png jasper-app/build/chrome-renders/settings-default*.png \
         jasper-app/build/chrome-renders/chrome-complement*.png jasper-app/build/chrome-renders/settings-complement*.png \
         jasper-app/build/chrome-renders/chrome-fallback*.png jasper-app/build/chrome-renders/settings-fallback*.png \
         jasper-app/build/dock-renders/default-dark.png jasper-app/build/dock-renders/default-light.png; do cp "$f" "$base/app/"; done
cp jasper-app/build/chrome-renders/chrome-enterprise.png jasper-app/build/chrome-renders/settings-enterprise.png "$base/"
for theme in light dark; do ./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=$theme -Pjasper.galleryScale=2; done
python3 -I tools/visual/check_pairs.py "$base/pairs-out" > "$base/pairs.txt"; echo "exit $?"
ls "$base/gallery-dark-1x" | wc -l; ls "$base/gallery-light-1x" | wc -l; ls "$base/app" | wc -l; grep -c ': PASS ' "$base/pairs.txt"
```

Expected:
- both prints;
- `:jasper-app:test` BUILD SUCCESSFUL;
- every `cp` succeeds (if one reports a missing file, read the names the two tests write and copy those, never dropping a default-look render);
- `exit 0`;
- `161`, `161`, `11` and `101`.

The two `enterprise` images let Task 1 Step 8 and Task 6 Step 6 prove the Linux re-hosting changes no pixel.

- [ ] **Step 1: Write the failing projection tests**

In `ProjectionsTest`, replace the hosts at `:13` and add the classic chrome:

```java
static final Projections.Host MAC = new Projections.Host(Projections.Platform.MAC, true),
    WINDOWS = new Projections.Host(Projections.Platform.WINDOWS, false), LINUX = new Projections.Host(Projections.Platform.LINUX, false);
/** The classic look's chrome with IntelliJ Classic's title (plan 14). */
static final ChromeSpec CLASSIC_TITLE = dev.jasper.app.testsupport.EnterpriseLook.titled(ChromeSpec.CLASSIC, ChromeSpec.TitleBar.CLASSIC);
```

Then add:

```java
/** Plan 14 (Rulings 5): a classic title is IntelliJ Classic's frame on macOS and Windows and the system's title bar on Linux. */
@Test void aClassicTitleIsIntelliJsFrameOnMacOsAndWindowsAndTheSystemsTitleBarOnLinux() {
    var mac = Projections.of(CLASSIC_TITLE, true, true, "dev.jasper.enterprise", MAC);
    assertThat(mac.desktop()).as("the default look's flags: the screen menu bar, no system title").isEqualTo(DesktopFlags.STANDARD);
    assertThat(mac.workspace().menus()).isEqualTo(WorkspaceLook.Menus.CLASSIC);
    assertThat(mac.workspace().header()).isEqualTo(WorkspaceLook.Header.CLASSIC);
    assertThat(mac.shell()).as("system-titled auxiliary frames with the File menu; the band").isEqualTo(new ShellLook(false, false, true, true));
    assertThat(mac.settings().integratedTitle()).isFalse();
    assertThat(mac.settings().hiddenKeys()).contains("window.native_decorations", "ui.animations.enabled", "window.rail_width");
    assertThat(mac.dialogs()).isEqualTo(new DialogMapping(DialogMapping.Kind.CLASSIC, true));
    var windows = Projections.of(CLASSIC_TITLE, true, true, "dev.jasper.enterprise", WINDOWS);
    assertThat(windows.desktop()).isEqualTo(DesktopFlags.STANDARD);
    assertThat(windows.workspace()).isEqualTo(mac.workspace());
    assertThat(windows.shell()).as("no auxiliary menu row; the custom title bar").isEqualTo(new ShellLook(false, true, true, true));
    assertThat(windows.settings().integratedTitle()).isFalse();
    assertThat(windows.settings().hiddenKeys()).as("the classic title honours window.native_decorations")
        .doesNotContain("window.native_decorations").contains("ui.animations.enabled", "window.rail_width");
    assertThat(windows.dialogs()).isEqualTo(new DialogMapping(DialogMapping.Kind.CLASSIC, true));
    assertThat(Projections.of(CLASSIC_TITLE, true, true, "dev.jasper.enterprise", LINUX)).as("Linux: exactly the native title's projections")
        .isEqualTo(Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", LINUX));
}

/** Spec 4.7 under plan 14: the classic title committed the default look's flags, so a failed start is the default look. */
@Test void aClassicTitleFallsBackToExactlyTheDefaultLookOnMacOsAndWindows() {
    for (var host : new Projections.Host[] {MAC, WINDOWS})
        assertThat(Projections.fallback(Projections.of(CLASSIC_TITLE, true, true, "p", host).desktop(), host)).as("%s", host)
            .isEqualTo(Projections.standard(host));
    assertThat(Projections.fallback(Projections.of(CLASSIC_TITLE, true, true, "p", LINUX).desktop(), LINUX))
        .isEqualTo(Projections.fallback(new DesktopFlags(false, true), LINUX));
}

@Test void thePlatformComesFromTheOperatingSystemsName() {
    assertThat(Projections.Platform.of("Mac OS X")).isEqualTo(Projections.Platform.MAC);
    assertThat(Projections.Platform.of("Windows 11")).isEqualTo(Projections.Platform.WINDOWS);
    for (String other : new String[] {"Linux", "FreeBSD", "", null}) assertThat(Projections.Platform.of(other)).as("%s", other).isEqualTo(Projections.Platform.LINUX);
    assertThat(MAC.mac()).isTrue();
    assertThat(WINDOWS.mac()).isFalse();
}
```

In `everyChromeSpecTheParserAcceptsBuildsEveryProjection`:
- change the skip to `if (menu == ChromeSpec.MenuBar.WINDOW && title == ChromeSpec.TitleBar.UNIFIED) continue;`;
- loop over `{MAC, WINDOWS, LINUX}`;
- after the `doesNotThrowAnyException` loop, add:
  ```java
  if (title != ChromeSpec.TitleBar.CLASSIC)
      assertThat(Projections.of(chrome, true, true, "p", WINDOWS)).as("Windows and Linux agree without a classic title")
          .isEqualTo(Projections.of(chrome, true, true, "p", LINUX));
  ```
- change the count to `assertThat(built).isEqualTo(5 * 2 * 2 * 2 * 2 * 2 * 2 * 2);`.

In `theDefaultLookFollowsTheSameRules` add `assertThat(Projections.of(ChromeSpec.DEFAULT, false, false, "", WINDOWS)).isEqualTo(Projections.of(ChromeSpec.DEFAULT, false, false, "", LINUX));`. Wherever the file iterates `new Projections.Host[] {MAC, LINUX}`, iterate `{MAC, WINDOWS, LINUX}`.

In `KitParserTest`:
- after `:207`, add `assertThat(parse("kit = 1\nbase = \"classic\"\n[chrome]\ntitle_bar = \"classic\"\n").chrome()).as("window menus under a classic title").isEqualTo(dev.jasper.app.testsupport.EnterpriseLook.titled(ChromeSpec.CLASSIC, ChromeSpec.TitleBar.CLASSIC));`;
- at `:257`, the message becomes `"[chrome].title_bar: \"glass\" is not one of unified, native, classic"`;
- at `:268`, it becomes `"[chrome].menu_bar: \"window\" needs [chrome].title_bar = \"native\" or \"classic\""`.

In `DesktopFlagsTest` add, with imports for `ChromeSpec`, `KitParser`, `Branding`, `dev.jasper.app.config.DefaultSource`, `dev.jasper.app.config.ViewMode`, `dev.jasper.ui.kit.Kit`, `java.util.List`, `java.util.Optional` and `dev.jasper.app.testsupport.EnterpriseLook`:

```java
static Presentation classic(Projections.Host host) {
    var chrome = EnterpriseLook.titled(ChromeSpec.CLASSIC, ChromeSpec.TitleBar.CLASSIC);
    return new Presentation(Optional.of(new Presentation.Provider("dev.example.look", "Look", "0.1.0", "d1")), Kit.DEFAULT, chrome, true,
        KitParser.Faces.NONE, DefaultSource.CORE, Projections.of(chrome, true, false, "dev.example.look", host), List.of(), Branding.NONE);
}

/** Plan 14 (Rulings 5, 17 and 18): macOS and Windows keep the default look's desktop; Linux keeps the system title. */
@Test void aClassicTitleCommitsTheDefaultLooksFlagsOnMacOsAndWindowsAndTheSystemTitleOnLinux() {
    var mac = classic(new Projections.Host(Projections.Platform.MAC, true));
    assertThat(mac.committedDesktop()).isEqualTo(DesktopFlags.STANDARD);
    System.setProperty(MENU, "false");
    assertThat(ApplicationBootstrap.applyDesktop(mac, ConfigSnapshot.defaults(), true)).isEqualTo(TitleBarStyle.MAC_UNIFIED);
    assertThat(System.getProperty(MENU)).as("the screen menu bar").isEqualTo("true");
    var windows = classic(new Projections.Host(Projections.Platform.WINDOWS, false));
    assertThat(windows.committedDesktop()).as("the default look's flags, so the fallback is the default look").isEqualTo(DesktopFlags.STANDARD);
    assertThat(ApplicationBootstrap.applyDesktop(windows, ConfigSnapshot.defaults(), false)).isEqualTo(TitleBarStyle.CUSTOM);
    assertThat(System.getProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS)).as("FlatLaf's title pane").isEqualTo("true");
    assertThat(ApplicationBootstrap.applyDesktop(windows, ConfigSnapshot.defaults().toBuilder().nativeDecorations(true).build(), false))
        .as("window.native_decorations").isEqualTo(TitleBarStyle.NATIVE);
    assertThat(ApplicationBootstrap.applyDesktop(windows, ConfigSnapshot.defaults().toBuilder().viewMode(ViewMode.COMPACT).build(), false))
        .as("Compact").isEqualTo(TitleBarStyle.NATIVE);
    var linux = classic(new Projections.Host(Projections.Platform.LINUX, false));
    assertThat(linux.committedDesktop()).isEqualTo(new DesktopFlags(false, true));
    assertThat(ApplicationBootstrap.applyDesktop(linux, ConfigSnapshot.defaults(), false)).isEqualTo(TitleBarStyle.NATIVE);
}
```

Also change `:56` to `Presentation.standard(new Projections.Host(Projections.Platform.MAC, true))`.

In `ShellChromeTest` add:

```java
/** Plan 14 (Rulings 12 and 16): under a classic title macOS frames carry the File menu for the screen menu bar; elsewhere none. */
@Test void aClassicTitleOnMacOsGivesAuxiliaryFramesTheMenuTheScreenMenuBarShows() {
    var chrome = dev.jasper.app.testsupport.EnterpriseLook.titled(ChromeSpec.CLASSIC, ChromeSpec.TitleBar.CLASSIC);
    var mac = ShellChrome.of(Projections.of(chrome, true, true, "p", new Projections.Host(Projections.Platform.MAC, true)).shell(), true);
    assertThat(mac.frameMenuRow()).isTrue();
    assertThat(mac.unifiedTitle()).as("their system title bars").isFalse();
    for (var platform : new Projections.Platform[] {Projections.Platform.WINDOWS, Projections.Platform.LINUX})
        assertThat(ShellChrome.of(Projections.of(chrome, true, true, "p", new Projections.Host(platform, false)).shell(), false).frameMenuRow())
            .as("%s", platform).isFalse();
}
```

In `AboutWindowsTest`, beside the `aboutInApplicationMenu` assertions (`:103-107`), add:

```java
var classic = dev.jasper.app.presentation.Projections.of(dev.jasper.app.testsupport.EnterpriseLook.titled(dev.jasper.app.presentation.ChromeSpec.CLASSIC,
    dev.jasper.app.presentation.ChromeSpec.TitleBar.CLASSIC), true, true, "p",
    new dev.jasper.app.presentation.Projections.Host(dev.jasper.app.presentation.Projections.Platform.MAC, true));
assertThat(AboutWindows.aboutInApplicationMenu(true, classic.desktop().screenMenuBar())).as("plan 14: the classic title on macOS").isTrue();
```

Add `EnterpriseLook.titled`, which returns a new `ChromeSpec` with every point of `spec` and `title`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL: `Projections.Platform`, `ChromeSpec.TitleBar.CLASSIC` and the four-argument `ShellLook` do not exist.

- [ ] **Step 3: Implement the model**

- **`ChromeSpec`:**
  - `public enum TitleBar { UNIFIED, NATIVE, CLASSIC }`. Its comment: "The unified title bar, the operating system's, or IntelliJ Classic's (a toolbar-coloured band under macOS's title, a custom title bar holding the menus on Windows, the system's on Linux)."
  - The constraint becomes `menuBar == MenuBar.WINDOW && titleBar == TitleBar.UNIFIED`, with the message `menu_bar = window needs title_bar = native or classic`. Update the `@param menuBar` text to match.
  - `CLASSIC`'s comment adds "and the native title bar (the Enterprise kit names classic)".
- **`KitParser`:** the same condition; message `[chrome].menu_bar: "window" needs [chrome].title_bar = "native" or "classic"`.
- **`ShellLook`:** add the `classicTitle` component, documented "whether the terminal frame draws IntelliJ Classic's title: the band on macOS, the custom title bar with the menus on Windows", and the 3-argument constructor `this(unifiedTitle, windowMenus, followThemeAppearance, false)`.
- **`Projections`:**
  - Add `Platform` and the new `Host` as in Interfaces (`Host`'s comment says the platform matters only for `title_bar = "classic"`).
  - Rewrite the title-dependent lines of `of` with these locals:
    ```java
    var title = chrome.titleBar();
    boolean unified = title == ChromeSpec.TitleBar.UNIFIED;
    // IntelliJ Classic's frame on macOS and Windows; Linux keeps the system title bar (plan 14).
    boolean classicTitle = title == ChromeSpec.TitleBar.CLASSIC && host.platform() != Platform.LINUX;
    boolean nativeTitle = title == ChromeSpec.TitleBar.NATIVE || title == ChromeSpec.TitleBar.CLASSIC && !classicTitle;
    boolean windowMenus = chrome.menuBar() == ChromeSpec.MenuBar.WINDOW;
    // A classic title commits the default look's flags on macOS and Windows, so a failed start falls back to the default look (spec 4.7).
    boolean screenMenuBar = !windowMenus || classicTitle;
    // IntelliJ Classic shows its macOS menus only in the screen menu bar; on Windows they stay in the window's title bar.
    boolean shellWindowMenus = windowMenus && !(classicTitle && host.mac());
    ```
  - Then:
    - `new DesktopFlags(screenMenuBar, nativeTitle)`;
    - header `unified ? UNIFIED : CLASSIC`;
    - menus `windowMenus ? CLASSIC : STANDARD`;
    - `new ShellLook(unified && host.unifiedTitleSupported(), shellWindowMenus, !unified, classicTitle)`;
    - hide `window.native_decorations` when `nativeTitle || host.mac()`;
    - `SettingsChrome` `integratedTitle = unified`;
    - `new DialogMapping(kind, !unified)`.
  - `fallback` is unchanged.
- **`PluginSelection.host()`:** `var platform = Projections.Platform.of(System.getProperty("os.name", ""));` then `new Projections.Host(platform, platform == Projections.Platform.MAC && com.formdev.flatlaf.util.SystemInfo.isMacFullWindowContentSupported)`. Update its comment to say it gives the platform too.
- **`PageContext.standard()`:** the same host.

- [ ] **Step 4: Convert the remaining hosts**

Each takes its final value now. The kit is still `native`, so no pixel changes.
- **`SettingsChromeTest:73`:** `new Projections.Host(Projections.Platform.of(System.getProperty("os.name", "")), mac && com.formdev.flatlaf.util.SystemInfo.isMacFullWindowContentSupported)`.
- **`EnterpriseSettings:153`:** `new Projections.Host(Projections.Platform.MAC, true)`. Under the classic title its Settings projections equal today's; Task 1 Step 1 pins `hiddenKeys` and `integratedTitle`.
- **`ChromeRenderTest:39`:**
  ```java
  static final Projections.Host HOST = new Projections.Host(Projections.Platform.of(System.getProperty("os.name", "")), MacTitleBar.isSupported());
  /** The Enterprise mocks' host (plan 14 Rulings 21): the system title bar and the in-window menu row. */
  static final Projections.Host LINUX = new Projections.Host(Projections.Platform.LINUX, false);
  ```
  The `enterprise` case (`:79-80`) uses `LINUX`.
- **`EnterpriseDockWindowTest:40`:** `ChromeRenderTest.LINUX`.
- **`EnterpriseMenuRowMockTest:72`:** `ChromeRenderTest.LINUX`, with the comment "the mocks' host: the system title bar and the in-window menu row (plan 14)".
- **`RunWindowWorkspaceRenderTest:42`:** `ChromeRenderTest.LINUX`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*ProjectionsTest' --tests '*KitParserTest' --tests '*DesktopFlagsTest' --tests '*ShellChromeTest' --tests '*AboutWindowsTest' --tests '*SettingsChromeTest' --tests '*EnterpriseFixtureTest'`
Expected: PASS.

- [ ] **Step 6: Run the window renders and mock tests**

Run: `./gradlew :jasper-app:test --tests '*ChromeRenderTest' --tests '*EnterpriseMenuRowMockTest' --tests '*EnterpriseWorkstationMockTest' --tests '*RunWindowWorkspaceRenderTest' --tests '*EnterpriseDockWindowTest' --tests '*Settings*MockTest'`
Expected: PASS.

- [ ] **Step 7: Run the architecture and documentation checks**

Run: `./gradlew :jasper-app:verifyApplicationArchitecture :jasper-app:javadoc`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Prove the re-hosted Enterprise image unchanged**

Run: `cmp jasper-app/build/chrome-renders/chrome-enterprise.png "$base/chrome-enterprise.png" && cmp jasper-app/build/chrome-renders/settings-enterprise.png "$base/settings-enterprise.png" && echo same` (with `base` as in Step 0)
Expected: `same`.

- [ ] **Step 9: Commit**

```bash
git add jasper-app/src
git commit -m "Plan 14 Task 1: title_bar = classic and the host's platform in the projections

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 2: The macOS title band

**Files:**
- Create:
  - `jasper-app/src/main/java/dev/jasper/app/platform/MacTitleBand.java`
  - `jasper-app/src/main/java/dev/jasper/app/platform/MacTitleDoubleClick.java`
  - `jasper-app/src/main/java/dev/jasper/app/platform/TitleInset.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/MacAppearance.java` (add `vibrant`), `docs/design/theme-engine/migration.csv`
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/MacTitleBandTest.java`, `jasper-app/src/test/java/dev/jasper/app/platform/MacTitleDoubleClickTest.java`

**Interfaces:**
- Produces:
  - **`public final class TitleInset`:** where a root's classic title chrome ends.
    - `public static void publish(JRootPane root, java.util.function.IntSupplier height)`.
    - `public static int of(JRootPane root)`: the published height, never below 0, or 0 when nothing is published. The client property key is `"Jasper.titleInset"`.
    - `public static final String CHANGED = "Jasper.titleInsetChanged"` and `public static void changed(JRootPane root, int before)`, which fires `root.firePropertyChange(CHANGED, before, of(root))` (JComponent's public `int` overload, so it fires only on a real change). `publish` ends with `changed(root, ` the inset before it `)`.
    - Why: nothing else tells a dialog or overlay that the inset moved. FlatLaf republishes the buttons bounds from its own root `componentResized` listener, "very late when toggling full screen" (`FullWindowContentSupport.java:63-65`, `:137-152`), possibly after the last resize `WindowOverlay` and `followTitleBar` see.
  - **`public static void MacAppearance.vibrant(JRootPane root, boolean dark)`:** sets `apple.awt.windowAppearance` to `"NSAppearanceNameVibrantDark"` or `"NSAppearanceNameVibrantLight"`.
  - **`public final class MacTitleDoubleClick`:**
    - `public enum Action { ZOOM, MINIMIZE, NONE }`;
    - `public static Action current()` (ZOOM until read);
    - `public static void prefetch()` (once per process; a virtual thread named `jasper-title-double-click`);
    - `static Action parse(String value)`.
  - **`public final class MacTitleBand extends JComponent implements AutoCloseable`:**
    - `public static final int HEIGHT = 28` (points, unscaled).
    - `public static MacTitleBand install(JRootPane root, java.util.function.Supplier<java.awt.Color> fill)` uses `MacTitleDoubleClick::current` and calls `prefetch()` on macOS (`SystemInfo.isMacOS`).
    - `static MacTitleBand install(JRootPane root, Supplier<Color> fill, Supplier<MacTitleDoubleClick.Action> doubleClick)`.
    - `public void setDark(boolean dark)` (vibrant appearance, then repaint).
    - `public java.awt.Color fill()`.
    - `public int inset()`: `HEIGHT` while visible, else 0.
    - `static boolean fullScreen(Object buttonsBounds)`.
    - `static int afterDoubleClick(int extendedState, MacTitleDoubleClick.Action action)`.
    - `public void close()`.

- [ ] **Step 1: Write the failing tests**

`MacTitleBandTest` (package `dev.jasper.app.platform`; on the EDT through `dev.jasper.app.testsupport.EdtTestExtension`, as `CollapsingMenuBarTest` uses it):

```java
@ExtendWith(EdtTestExtension.class)
class MacTitleBandTest {
    static final Color FILL = new Color(0xF2F2F2);

    static JRootPane root(JPanel content) {
        var root = new JRootPane();
        root.setContentPane(content);
        return root;
    }

    static void layout(JRootPane root) { dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(root); }

    /** IntelliJ Classic's macOS frame: full-window content under a transparent title, a 28 pt band, macOS's own title. */
    @Test void theBandIsIntelliJClassicsMacOsTitleRow() {
        var content = new JPanel();
        var root = root(content);
        var band = MacTitleBand.install(root, () -> FILL, () -> MacTitleDoubleClick.Action.ZOOM);
        assertThat(root.getClientProperty("apple.awt.fullWindowContent")).isEqualTo(true);
        assertThat(root.getClientProperty("apple.awt.transparentTitleBar")).isEqualTo(true);
        assertThat(root.getClientProperty("apple.awt.windowTitleVisible")).as("macOS draws the frame's title").isNull();
        assertThat(band.getParent()).isSameAs(root.getContentPane());
        assertThat(content.getParent()).isSameAs(root.getContentPane());
        root.setSize(800, 600); layout(root);
        assertThat(band.getBounds()).isEqualTo(new Rectangle(0, 0, 800, MacTitleBand.HEIGHT));
        assertThat(content.getY()).isEqualTo(MacTitleBand.HEIGHT);
        assertThat(TitleInset.of(root)).isEqualTo(MacTitleBand.HEIGHT);
        var image = new BufferedImage(800, 40, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { root.getContentPane().printAll(g); } finally { g.dispose(); }
        for (int y = 0; y < MacTitleBand.HEIGHT; y++)
            assertThat(new Color(image.getRGB(400, y))).as("row %d: flat, with no rule", y).isEqualTo(FILL);
        band.setDark(true);
        assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameVibrantDark");
        band.setDark(false);
        assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameVibrantLight");
    }

    @Test void fullScreenRemovesTheBandAndLeavingRestoresIt() {
        var content = new JPanel();
        var root = root(content);
        var band = MacTitleBand.install(root, () -> FILL, () -> MacTitleDoubleClick.Action.ZOOM);
        root.setSize(800, 600);
        var changes = new java.util.ArrayList<String>();
        root.addPropertyChangeListener(TitleInset.CHANGED, event -> changes.add(event.getOldValue() + "->" + event.getNewValue()));
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(0, 0, 0, 0));
        assertThat(changes).as("the inset change is announced without a resize").containsExactly(MacTitleBand.HEIGHT + "->0");
        layout(root);
        assertThat(band.isVisible()).isFalse();
        assertThat(content.getY()).as("the content meets the screen's top edge").isZero();
        assertThat(TitleInset.of(root)).isZero();
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(7, 6, 52, 16));
        layout(root);
        assertThat(band.isVisible()).isTrue();
        assertThat(changes).containsExactly(MacTitleBand.HEIGHT + "->0", "0->" + MacTitleBand.HEIGHT);
        assertThat(content.getY()).isEqualTo(MacTitleBand.HEIGHT);
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, null);
        assertThat(band.isVisible()).as("no native window yet").isTrue();
        band.close();
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(0, 0, 0, 0));
        assertThat(band.isVisible()).as("closed: it stops following the root").isTrue();
    }

    /** Rulings 10: a double-click zooms, minimises or does nothing, as the system setting says. */
    @Test void aDoubleClickZoomsMinimisesOrDoesNothing() {
        assertThat(MacTitleBand.afterDoubleClick(Frame.NORMAL, MacTitleDoubleClick.Action.ZOOM)).isEqualTo(Frame.MAXIMIZED_BOTH);
        assertThat(MacTitleBand.afterDoubleClick(Frame.MAXIMIZED_BOTH, MacTitleDoubleClick.Action.ZOOM)).isEqualTo(Frame.NORMAL);
        assertThat(MacTitleBand.afterDoubleClick(Frame.NORMAL, MacTitleDoubleClick.Action.MINIMIZE)).isEqualTo(Frame.ICONIFIED);
        assertThat(MacTitleBand.afterDoubleClick(Frame.MAXIMIZED_BOTH, MacTitleDoubleClick.Action.MINIMIZE)).isEqualTo(Frame.MAXIMIZED_BOTH | Frame.ICONIFIED);
        assertThat(MacTitleBand.afterDoubleClick(Frame.MAXIMIZED_BOTH, MacTitleDoubleClick.Action.NONE)).isEqualTo(Frame.MAXIMIZED_BOTH);
        assertThat(MacTitleBand.fullScreen(new Rectangle(0, 0, 0, 0))).isTrue();
        assertThat(MacTitleBand.fullScreen(new Rectangle(7, 6, 52, 16))).isFalse();
        assertThat(MacTitleBand.fullScreen(null)).isFalse();
    }

    @Test void aRootWithoutClassicTitleChromeHasNoInset() {
        var root = new JRootPane();
        assertThat(TitleInset.of(root)).isZero();
        TitleInset.publish(root, () -> -3);
        assertThat(TitleInset.of(root)).as("never below 0").isZero();
        TitleInset.publish(root, () -> 29);
        assertThat(TitleInset.of(root)).isEqualTo(29);
    }
}
```

`MacTitleDoubleClickTest`:

```java
class MacTitleDoubleClickTest {
    @Test void theSystemSettingNamesTheAction() {
        assertThat(MacTitleDoubleClick.parse("Minimize\n")).isEqualTo(MacTitleDoubleClick.Action.MINIMIZE);
        assertThat(MacTitleDoubleClick.parse("None")).isEqualTo(MacTitleDoubleClick.Action.NONE);
        for (String zoom : new String[] {"Maximize", "Fill", "", "something new", null})
            assertThat(MacTitleDoubleClick.parse(zoom)).as("%s", zoom).isEqualTo(MacTitleDoubleClick.Action.ZOOM);
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests '*MacTitleBandTest' --tests '*MacTitleDoubleClickTest'`
Expected: FAIL to compile: `MacTitleBand`, `MacTitleDoubleClick` and `TitleInset` do not exist.

- [ ] **Step 3: Implement the three classes and `MacAppearance.vibrant`**

- **`TitleInset`:** the `IntSupplier` as a root client property; `of` clamps at 0; `publish` reads `of` first and ends with `changed(root, before)`; `changed` fires `CHANGED` as in Interfaces.
- **`MacTitleDoubleClick`:**
  - `parse` trims; "Minimize" gives MINIMIZE and "None" gives NONE; anything else, null included, gives ZOOM.
  - `current` is a `volatile` field.
  - `prefetch` starts a virtual thread once (an `AtomicBoolean`) that runs `new ProcessBuilder("/usr/bin/defaults", "read", "-g", "AppleActionOnDoubleClick").redirectErrorStream(true)`.
    - It waits `waitFor(2, TimeUnit.SECONDS)`; on a timeout it `destroyForcibly()`s and keeps ZOOM.
    - It reads the output only after the exit. A non-zero exit means the key is unset: ZOOM.
    - An `IOException` keeps ZOOM. An `InterruptedException` restores the interrupt flag and keeps ZOOM.
  - Its class comment cites IntelliJ Classic's handler and why no JNA (Verified 22).
- **`MacTitleBand.install`:**
  - It puts the two root properties.
  - It wraps the root's current content pane in a `JPanel(new BorderLayout())`, the band `NORTH` and the old content `CENTER`, and sets that as the content pane.
  - It adds a `PropertyChangeListener` on `FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS` that takes `int before = inset()`, sets `setVisible(!fullScreen(newValue))`, revalidates the root, then calls `TitleInset.changed(root, before)`, so dialogs and overlays re-place even when no resize follows.
  - It adds a `MouseAdapter` whose `mouseClicked` acts on `getClickCount() == 2`: for a `java.awt.Frame` ancestor, `frame.setExtendedState(afterDoubleClick(frame.getExtendedState(), doubleClick.get()))`.
  - It publishes `TitleInset.publish(root, band::inset)`.
- **The band itself:**
  - It is opaque. `getPreferredSize()` is `(0, HEIGHT)` and `getMinimumSize()` the same.
  - `paintComponent` fills its bounds with `fill.get()`.
  - It has no children and draws no text.
  - `close()` removes the root listener and is idempotent.
- **`fullScreen` and `afterDoubleClick`:**
  ```java
  static boolean fullScreen(Object buttonsBounds) { return buttonsBounds instanceof Rectangle bounds && bounds.width == 0; }

  static int afterDoubleClick(int state, MacTitleDoubleClick.Action action) {
      return switch (action) {
          case ZOOM -> (state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH ? state & ~Frame.MAXIMIZED_BOTH : state | Frame.MAXIMIZED_BOTH;
          case MINIMIZE -> state | Frame.ICONIFIED;
          case NONE -> state;
      };
  }
  ```
- **The class comment** says:
  - it is IntelliJ Classic's macOS title row (`ToolbarServiceImpl` `setTransparentTitleBar`): the band paints the toolbar's colour and macOS draws the traffic lights and the frame's title over it;
  - it is never a title-bar zone;
  - full screen removes it (Verified 19).

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*MacTitleBandTest' --tests '*MacTitleDoubleClickTest' --tests '*ThemeMigrationCoverageTest'`
Expected: the band tests pass. `ThemeMigrationCoverageTest` fails, naming `MacTitleBand.java`, `TitleInset.java` and `MacTitleBandTest.java`.

- [ ] **Step 5: Add the migration rows**

```text
jasper-app/src/main/java/dev/jasper/app/platform/MacTitleBand.java,app.platform,MacTitleBand,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/platform/MacTitleBandTest.java,New in Enterprise plan 14 Task 2; IntelliJ Classic's macOS title band: full-window content; a 28 pt band in the toolbar's colour; full screen removes it; double-click per the system setting
jasper-app/src/main/java/dev/jasper/app/platform/TitleInset.java,app.platform,TitleInset,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/platform/MacTitleBandTest.java,New in Enterprise plan 14 Task 2; where a root's classic title chrome ends for in-window dialogs and overlays
jasper-app/src/test/java/dev/jasper/app/platform/MacTitleBandTest.java,app.platform,MacTitleBandTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/platform/MacTitleBandTest.java,Test/fixture ownership; New in Enterprise plan 14 Task 2; the band's root properties; height; colour; full screen; double-click; the title inset
```

Add a row the same way for any other file the coverage test names (for example `MacTitleDoubleClick.java`, if it imports AWT).

- [ ] **Step 6: Rerun and commit**

Run: `./gradlew :jasper-app:test --tests '*MacTitleBandTest' --tests '*MacTitleDoubleClickTest' --tests '*ThemeMigrationCoverageTest' --tests '*ColourLiteralScanTest' :jasper-app:javadoc`
Expected: PASS.

```bash
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -m "Plan 14 Task 2: IntelliJ Classic's macOS title band

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 3: The terminal window takes the band on macOS

**Files:**
- Modify:
  - `jasper-app/src/main/java/dev/jasper/app/workspace/WindowLook.java`
  - `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java:326-334` (`mount`), plus a new `installMacTitleBand`, a `titleBand` field with accessor, and `close()`
  - `jasper-app/src/test/java/dev/jasper/app/workspace/DesktopTestSupport.java` (one overload)
  - `docs/design/theme-engine/migration.csv`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/ClassicMacTitleTest.java`

**Interfaces:**
- Consumes: `MacTitleBand.install(JRootPane, Supplier<Color>)`, `MacTitleBand.setDark`, `MacTitleBand.HEIGHT`, `TitleInset.of` (Task 2); `ShellLook.classicTitle()` and `Projections.Platform` (Task 1).
- Produces:
  - **`public record WindowLook(WorkspaceChrome chrome, boolean unifiedTitle, boolean followThemeAppearance, boolean classicTitle)`.** `of` reads `projections.shell().classicTitle()`; `public WindowLook(WorkspaceChrome chrome, boolean unifiedTitle, boolean followThemeAppearance)` gives `classicTitle = false`; `standard()` is unchanged.
  - **`static void WindowContent.installMacTitleBand(JRootPane root, WindowContent content)`.**
  - **`MacTitleBand WindowContent.titleBand()`** (package-private; null without the band).
  - **`public static WindowContent DesktopTestSupport.content(ShellLauncher launcher, ThemeController themes, WorkspaceChrome look, boolean macOs)`.**

- [ ] **Step 1: Write the failing test**

`ClassicMacTitleTest` (package `dev.jasper.app.workspace`):

```java
class ClassicMacTitleTest {
    static final Projections.Host MAC = new Projections.Host(Projections.Platform.MAC, true);
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    static WindowLook look() {
        return WindowLook.of(Projections.of(EnterpriseLook.titled(EnterpriseLook.CHROME, ChromeSpec.TitleBar.CLASSIC), true, true, "dev.jasper.enterprise", MAC));
    }

    /** A macOS window under the classic title, mounted as TerminalWindow mounts it, its menu bar out of the window as AppKit takes it. */
    static JRootPane mounted(ThemeController themes, java.util.function.Consumer<WindowContent> before, WindowContent[] owner) {
        owner[0] = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, look().chrome(), true);
        before.accept(owner[0]);
        var root = new JRootPane();
        assertThat(WindowContent.mount(root, owner[0], look(), title -> { })).as("no unified bar").isNull();
        assertThat(root.getJMenuBar()).as("the root's menus, which the screen menu bar shows").isSameAs(owner[0].menuBar());
        root.getJMenuBar().setVisible(false);
        root.setSize(1200, 700);
        for (int i = 0; i < 2; i++) LayoutTestSupport.layoutTree(root);
        return root;
    }

    @Test void theBandSitsAboveTheToolbarInItsColourWithTheMenusOnTheRoot() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                assertThat(look().classicTitle()).isTrue();
                assertThat(look().unifiedTitle()).isFalse();
                var owner = new WindowContent[1];
                var root = mounted(themes, w -> { }, owner);
                var band = owner[0].titleBand();
                assertThat(band).isNotNull();
                assertThat(SwingUtilities.convertRectangle(band.getParent(), band.getBounds(), root)).isEqualTo(new Rectangle(0, 0, 1200, MacTitleBand.HEIGHT));
                var header = owner[0].chrome().header();
                assertThat(header.getParent()).as("the toolbar stays a row of the window").isSameAs(owner[0]);
                assertThat(SwingUtilities.convertPoint(header, 0, 0, root).y).as("the toolbar meets the band").isEqualTo(MacTitleBand.HEIGHT);
                assertThat(owner[0].chrome().headerIsTitleBar()).as("a chrome row, not a title-bar zone").isFalse();
                var toolbar = ThemeZone.color(header, SemanticColor.SURFACE_APP);
                assertThat(band.fill()).isEqualTo(toolbar).isEqualTo(header.getBackground());
                var image = MockProfile.print(root, 1200, 700);
                for (int y = 0; y <= 2 * MacTitleBand.HEIGHT + 1; y++)
                    assertThat(MockProfile.at(image, 1200, y)).as("device row %d: the band and the toolbar are one band", y).isEqualTo(toolbar);
                assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameVibrantLight");
                assertThat(TitleInset.of(root)).isEqualTo(MacTitleBand.HEIGHT);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void aDarkThemeGivesTheVibrantDarkAppearance() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(true);
            try {
                var root = mounted(themes, w -> { }, new WindowContent[1]);
                assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameVibrantDark");
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Review Focus 1: a theme with its own title bar colour still gives the band the toolbar's. */
    @Test void anotherThemeKeepsTheBandInTheToolbarsColour() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var owner = new WindowContent[1];
                mounted(themes, w -> { }, owner);
                String json;
                // edt takes a Runnable, so the read's IOException is caught here, as WindowToolbarViewsTest does.
                try (var in = JasperTheme.class.getResourceAsStream("/dev/jasper/ui/theme/slate-cobalt.json")) {
                    json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (java.io.IOException failure) { throw new AssertionError(failure); }
                var titled = JasperTheme.parse(json.replace("\"accent\": \"#2563C9\"",
                    "\"surface.titlebar\": \"#27282E\", \"text.titlebar\": \"#DFE1E5\", \"accent\": \"#2563C9\""), "header").generated(false);
                FlatLafInstaller.install(titled, EnterpriseLook.kit());
                assertThat(owner[0].titleBand().fill()).isEqualTo(titled.semantic().color(SemanticColor.SURFACE_APP)).isNotEqualTo(new Color(0x27282E));
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void fullScreenTakesTheBandOutOfTheWindow() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var owner = new WindowContent[1];
                var root = mounted(themes, w -> { }, owner);
                root.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(0, 0, 0, 0));
                LayoutTestSupport.layoutTree(root);
                assertThat(SwingUtilities.convertPoint(owner[0].chrome().header(), 0, 0, root).y).isZero();
                assertThat(TitleInset.of(root)).isZero();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Rulings 18: Compact keeps the system title bar, without the band. */
    @Test void compactKeepsTheSystemTitleBarWithoutTheBand() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var owner = new WindowContent[1];
                var root = mounted(themes, w -> w.initializeViewMode(ViewMode.COMPACT), owner);
                assertThat(owner[0].titleBand()).isNull();
                assertThat(root.getClientProperty("apple.awt.fullWindowContent")).isNull();
                assertThat(root.getClientProperty("apple.awt.windowAppearance")).as("as the default look's Compact").isEqualTo("NSAppearanceNameAqua");
                assertThat(TitleInset.of(root)).isZero();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
}
```

Imports:
- `dev.jasper.app.platform.MacTitleBand`, `dev.jasper.app.platform.ThemeZone` and `dev.jasper.app.platform.TitleInset`;
- `dev.jasper.app.presentation.*`, `dev.jasper.app.config.ViewMode` and `dev.jasper.app.appearance.ThemeController`;
- `dev.jasper.app.testsupport.EnterpriseLook`, `LayoutTestSupport` and `MockProfile`;
- `dev.jasper.ui.theme.SemanticColor`, `dev.jasper.ui.theme.JasperTheme` and `dev.jasper.ui.swing.FlatLafInstaller`;
- `com.formdev.flatlaf.FlatClientProperties`, `java.awt.Color`, `java.awt.Rectangle`, `java.nio.charset.StandardCharsets`, `java.util.ArrayDeque` and `javax.swing.*`;
- `static DesktopTestSupport.edt` and AssertJ.

`MockProfile.print` prints at 2× and `MockProfile.at(image, x, y)` reads one device pixel. Read their signatures before use, as `ChromeRenderTest` and the mock tests call them.

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-app:test --tests '*ClassicMacTitleTest'`
Expected: FAIL to compile: `WindowLook.classicTitle`, `WindowContent.titleBand` and the `DesktopTestSupport.content` overload do not exist.

- [ ] **Step 3: Implement**

- **`WindowLook`:** add the component, its `@param` ("whether the frame draws IntelliJ Classic's title: the band on macOS, the custom title bar on Windows"), the 3-argument constructor, and `of` passing `projections.shell().classicTitle()`.
- **`DesktopTestSupport.content(launcher, themes, look, macOs)`:** the existing look overload with the given `macOs`. The 3-argument one delegates to it with `os.name`'s value.
- **`WindowContent.mount`:**
  ```java
  MacTitleBar bar = installTitleBar(root, content, look.unifiedTitle(), look.followThemeAppearance(), nativeTitle);
  // IntelliJ Classic's frame (plan 14): the band on macOS, FlatLaf's title pane with the menus on Windows (Task 4).
  if (bar == null && look.classicTitle() && content.macOs) installMacTitleBand(root, content);
  else if (bar == null) installCustomTitleBar(root, content);
  ```
  The rest of `mount` is unchanged, so `chromeRow()` and `root.setJMenuBar` still run. Update its comment for the classic title.
- **`installMacTitleBand(root, content)`:**
  - In Compact it returns at once.
  - Otherwise: `content.titleBand = MacTitleBand.install(root, () -> ThemeZone.color(content.chrome().header(), SemanticColor.SURFACE_APP));`, then `content.onThemeChanged = theme -> content.titleBand.setDark(theme.chrome().dark());` and `content.onThemeChanged.accept(content.theme());`. This replaces `installTitleBar`'s Aqua follower; the window's dialogs still follow through `followsThemeAppearance`.
  - Its comment cites Rulings 7 and 8.
- **`close()`:** `if (titleBand != null) { titleBand.close(); titleBand = null; }` before `onThemeChanged` is cleared.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*ClassicMacTitleTest' --tests '*WindowShellTest' --tests '*MacTitleBarTest' --tests '*CustomTitleBarTest' --tests '*ClassicHeaderTest' --tests '*ChromeRenderTest' --tests '*ThemeMigrationCoverageTest'`
Expected: all pass except `ThemeMigrationCoverageTest`, which names `ClassicMacTitleTest.java`.

- [ ] **Step 5: Add the row and commit**

```text
jasper-app/src/test/java/dev/jasper/app/workspace/ClassicMacTitleTest.java,app.workspace,ClassicMacTitleTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/workspace/ClassicMacTitleTest.java,Test/fixture ownership; New in Enterprise plan 14 Task 3; the macOS classic title: the band over the toolbar in its colour; the root's menus; vibrant appearance; full screen; Compact
```

Run: `./gradlew :jasper-app:test --tests '*ThemeMigrationCoverageTest' :jasper-app:javadoc`
Expected: PASS.

```bash
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -m "Plan 14 Task 3: macOS Enterprise windows take the classic title band

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 4: The Windows classic title bar

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/ClassicWindowTitle.java`
- Modify:
  - `jasper-app/src/main/java/dev/jasper/app/workspace/CollapsingMenuBar.java` (`setTitle`, `Layout`, `shownWidth`)
  - `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java`: `mount`, `installCustomTitleBar:397-411` (split into a shared helper), a new `installClassicTitleBar` and `decorateClassicTitleBar`, the `onTitle` text, `setActive`, and a `frameTitle` field
  - `jasper-app/src/main/java/dev/jasper/app/workspace/WindowPairs.java` (two pairs, added to `ALL`)
  - `docs/design/theme-engine/migration.csv`
- Test:
  - `jasper-app/src/test/java/dev/jasper/app/workspace/CollapsingMenuBarTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/workspace/ClassicWindowsTitleTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/workspace/WindowPairsTest.java`

**Interfaces:**
- Consumes: `TitleInset.publish` (Task 2); `WindowLook.classicTitle()` and `DesktopTestSupport.content(..., boolean macOs)` (Task 3).
- Produces:
  - **`final class ClassicWindowTitle extends JLabel`:**
    - `static final int GAP = 22, MINIMUM = 60, BAR_HEIGHT = 28` (logical pixels);
    - `ClassicWindowTitle()`: text "Jasper", `LEADING`, `html.disable`, accessible name "Window title", not opaque, no listeners;
    - `void setActive(boolean active)`, `boolean active()` and `void refresh()`.
  - **`void CollapsingMenuBar.setTitle(ClassicWindowTitle title)`.**
  - **`WindowPairs.TITLE`** = `(TEXT_SECONDARY, SURFACE_APP, Guard.TEXT)` and **`WindowPairs.INACTIVE_TITLE`** = `(TEXT_TERTIARY, SURFACE_APP, Guard.INDICATOR)`.
  - **`static boolean WindowContent.installClassicTitleBar(JRootPane root, WindowContent content)`.**
  - **`ClassicWindowTitle WindowContent.classicWindowTitle()`** (package-private; null until decorated).

- [ ] **Step 1: Write the failing tests**

In `CollapsingMenuBarTest` (import `com.formdev.flatlaf.util.UIScale`):

```java
/** Plan 14 (Rulings 13): the Windows classic title follows the menus; they fold before it shrinks past its minimum. */
@Test void aClassicTitleFollowsTheMenusAndTheMenusFoldBeforeItShrinksPastItsMinimum() {
    var bar = bar();
    bar.setBorder(BorderFactory.createEmptyBorder());
    var title = new ClassicWindowTitle();
    title.setPreferredSize(new Dimension(300, 20));
    bar.setTitle(title);
    int gap = UIScale.scale(ClassicWindowTitle.GAP), minimum = UIScale.scale(ClassicWindowTitle.MINIMUM);
    // FlatLaf sizes its left panel from this and squeezes the window buttons first when its title area drops below 60, so the
    // bar asks only for its folded minimum; FlatLaf's glue widening gives it its real width, and layoutContainer folds.
    assertThat(bar.getPreferredSize().width).as("the ☰ menu, the gap and the title's minimum").isEqualTo(30 + gap + minimum);
    assertThat(bar.getComponent(bar.getComponentCount() - 1)).as("the glue FlatLaf stretches the bar by").isInstanceOf(Box.Filler.class);
    bar.setSize(800, 28); bar.doLayout();
    assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.NONE);
    assertThat(title.getBounds()).isEqualTo(new Rectangle(220 + gap, 0, 800 - 220 - gap, 28));
    bar.setSize(220 + gap + minimum, 28); bar.doLayout();
    assertThat(bar.fold()).as("the title at its minimum keeps every menu").isEqualTo(CollapsingMenuBar.Fold.NONE);
    assertThat(title.getWidth()).isEqualTo(minimum);
    bar.setSize(220 + gap + minimum - 1, 28); bar.doLayout();
    assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.MORE);
    assertThat(shown(bar)).containsExactly(true, true, true, true, false);
    assertThat(title.getX()).isEqualTo(180 + 30 + gap);
    assertThat(bar.getPreferredSize().width).as("folding does not change what the bar asks for").isEqualTo(30 + gap + minimum);
    bar.setSize(40, 28); bar.doLayout();
    assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.ALL);
    assertThat(title.getWidth()).as("never negative").isZero();
}
```

`ClassicWindowsTitleTest` (package `dev.jasper.app.workspace`):

```java
class ClassicWindowsTitleTest {
    static final Projections.Host WINDOWS = new Projections.Host(Projections.Platform.WINDOWS, false);
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    static WindowLook look() {
        return WindowLook.of(Projections.of(EnterpriseLook.titled(EnterpriseLook.CHROME, ChromeSpec.TitleBar.CLASSIC), true, true, "dev.jasper.enterprise", WINDOWS));
    }

    record Mounted(WindowContent owner, JRootPane root, String[] frameTitle) { }

    /** A Windows window under the classic title; {@code decorated} as FlatLaf decorates a Windows 10 or 11 frame. */
    static Mounted mount(ThemeController themes, boolean decorated, int width) {
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, look().chrome(), false);
        owner.connectContributions(EnterpriseMenuRowMockTest.workstation());
        owner.setActive(true);
        var root = new JRootPane();
        if (decorated) { root.setWindowDecorationStyle(JRootPane.FRAME); root.updateUI(); }
        var frameTitle = new String[1];
        assertThat(WindowContent.mount(root, owner, look(), value -> frameTitle[0] = value)).isNull();
        owner.update();
        root.setSize(width, 800);
        for (int i = 0; i < 3; i++) LayoutTestSupport.layoutTree(root);
        return new Mounted(owner, root, frameTitle);
    }

    /** FlatLaf's window buttons, by accessible name; headless, only Close shows (the others need a Frame, FlatTitlePane.createButtons). */
    static List<JButton> windowButtons(JRootPane root) {
        var names = Set.of("Iconify", "Maximize", "Restore", "Close");
        var found = new ArrayList<JButton>();
        var pending = new ArrayDeque<Component>(List.of(root.getLayeredPane().getComponents()));
        while (!pending.isEmpty()) {
            var next = pending.pop();
            if (next instanceof JButton button && button.isVisible() && names.contains(button.getAccessibleContext().getAccessibleName())) found.add(button);
            if (next instanceof Container container) pending.addAll(List.of(container.getComponents()));
        }
        return found;
    }

    /** W1: the default look's window buttons, never squeezed by the menus and the title. */
    static void assertFullWindowButtons(JRootPane root, int width) {
        var buttons = windowButtons(root);
        assertThat(buttons).as("%d px: Close at least", width).extracting(button -> button.getAccessibleContext().getAccessibleName()).contains("Close");
        for (var button : buttons)
            assertThat(button.getWidth()).as("%d px: %s keeps its 44 px", width, button.getAccessibleContext().getAccessibleName())
                .isEqualTo(button.getPreferredSize().width).isEqualTo(UIScale.scale(44));
    }

    @Test void theTitleBarIsTheToolbarsColourWithTheMenusThenTheTitle() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var m = mount(themes, true, 1440);
                var owner = m.owner(); var root = m.root();
                var menus = owner.menuBar();
                var header = owner.chrome().header();
                assertThat(menus.getParent()).isSameAs(root.getLayeredPane());
                assertThat(header.getParent()).as("the toolbar stays a row of the window, under the title bar").isSameAs(owner);
                int top = root.getContentPane().getY();
                assertThat(top).as("the title bar, 28 and its rule").isEqualTo(UIScale.scale(ClassicWindowTitle.BAR_HEIGHT) + UIScale.scale(1));
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE)).isEqualTo(false);
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT)).isEqualTo(ClassicWindowTitle.BAR_HEIGHT);
                var toolbar = ThemeZone.color(header, SemanticColor.SURFACE_APP);
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_BACKGROUND)).isEqualTo(toolbar);
                assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_FOREGROUND)).as("the default look's window buttons")
                    .isEqualTo(ThemeZone.color(null, SemanticColor.TEXT_SECONDARY));
                var title = owner.classicWindowTitle();
                assertThat(title.getParent()).isSameAs(menus);
                assertThat(title.getText()).as("the frame's title").isEqualTo(m.frameTitle()[0]);
                assertThat(title.getHorizontalAlignment()).isEqualTo(SwingConstants.LEADING);
                JMenu last = null;
                for (var menu : menus.menus()) if (menu.isVisible()) last = menu;
                assertThat(title.getX()).as("22 after the last menu").isEqualTo(last.getX() + last.getWidth() + UIScale.scale(ClassicWindowTitle.GAP));
                assertThat(title.getMouseListeners()).as("caption: the title drags the window").isEmpty();
                assertThat(title.getMouseMotionListeners()).isEmpty();
                var installed = FlatLafInstaller.installedTheme();
                assertThat(title.getForeground()).isEqualTo(SurfaceContrast.painted(installed, WindowPairs.TITLE));
                owner.setActive(false);
                assertThat(title.getForeground()).as("dimmed while inactive").isEqualTo(SurfaceContrast.painted(installed, WindowPairs.INACTIVE_TITLE))
                    .isNotEqualTo(SurfaceContrast.painted(installed, WindowPairs.TITLE));
                owner.setActive(true);
                var image = MockProfile.print(root, 1440, 800);
                var rule = MockProfile.at(image, 2 * 100, 2 * top - 1);
                assertThat(MockProfile.at(image, 2 * 1300, 2 * top - 1)).as("the rule runs under the whole title bar").isEqualTo(rule);
                // FlatTitlePaneBorder, not the menu bar's own Matte rule: also under the window buttons and the app icon.
                assertThat(MockProfile.at(image, 2 * (1440 - 20), 2 * top - 1)).as("the rule under the window buttons").isEqualTo(rule);
                assertThat(MockProfile.at(image, 2 * 8, 2 * top - 1)).as("the rule under the app icon").isEqualTo(rule);
                assertThat(rule).isNotEqualTo(toolbar);
                assertThat(MockProfile.at(image, 2 * 1300, 2 * top - 6)).as("the bar is the toolbar's colour").isEqualTo(toolbar);
                assertThat(TitleInset.of(root)).isEqualTo(top);
                assertFullWindowButtons(root, 1440);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void aNarrowWindowFoldsTheMenusAndClipsTheTitle() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var m = mount(themes, true, 480);
                var menus = m.owner().menuBar();
                assertThat(menus.fold()).isNotEqualTo(CollapsingMenuBar.Fold.NONE);
                var title = m.owner().classicWindowTitle();
                assertThat(title.getWidth()).isNotNegative();
                assertThat(title.getX() + title.getWidth()).as("inside the bar, clear of the window buttons")
                    .isLessThanOrEqualTo(menus.getWidth() - menus.getInsets().right);
                assertFullWindowButtons(m.root(), 480);
                var wider = mount(themes, true, 800);
                assertThat(wider.owner().classicWindowTitle().getWidth()).isNotNegative();
                assertFullWindowButtons(wider.root(), 800);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Rulings 17: with window.native_decorations FlatLaf never decorates, so the window is Linux's. */
    @Test void withoutDecorationsTheMenusStayARowOfTheWindow() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var m = mount(themes, false, 1440);
                assertThat(m.owner().classicWindowTitle()).isNull();
                assertThat(m.root().getJMenuBar()).isSameAs(m.owner().menuBar());
                assertThat(SwingUtilities.convertPoint(m.owner(), 0, 0, m.root()).y).isEqualTo(m.root().getJMenuBar().getHeight());
                assertThat(TitleInset.of(m.root())).isZero();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** Windows 10 and 11 decorate a frame when its peer exists: the classic title arrives then, once. */
    @Test void decorationsThatArriveLaterStillGetTheClassicTitleOnce() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var m = mount(themes, false, 1440);
                m.root().setWindowDecorationStyle(JRootPane.FRAME);
                assertThat(m.owner().classicWindowTitle()).isNotNull();
                m.root().setWindowDecorationStyle(JRootPane.NONE);
                m.root().setWindowDecorationStyle(JRootPane.FRAME);
                assertThat(m.owner().menuBar().getComponents()).filteredOn(child -> child instanceof ClassicWindowTitle).hasSize(1);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
}
```

Besides the types its body names, the helpers import `java.awt.Component`, `java.awt.Container`, `java.util.ArrayList`, `java.util.List` and `java.util.Set`.

In `WindowPairsTest`:
- `Fixture` gains `final ClassicWindowTitle title = new ClassicWindowTitle(), inactiveTitle = new ClassicWindowTitle();`, with `inactiveTitle.setActive(false)` in the constructor.
- `restyle()` adds, for each title, `refresh()`, `setText("Workstation")` and `setSize(getPreferredSize())`.
- `declared(f)` adds:
  ```java
  new Declared("window title", WindowPairs.TITLE, (theme, expected) -> paints(f.title, expected)),
  new Declared("inactive window title", WindowPairs.INACTIVE_TITLE, (theme, expected) -> paints(f.inactiveTitle, expected)),
  ```

`WindowPairsTest.paints` prints at 4×, so the text's stem pixels are fully covered.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests '*CollapsingMenuBarTest' --tests '*ClassicWindowsTitleTest' --tests '*WindowPairsTest'`
Expected: FAIL to compile: `ClassicWindowTitle`, `setTitle`, `classicWindowTitle()` and the two pairs do not exist.

- [ ] **Step 3: Implement**

- **`ClassicWindowTitle`:**
  - `refresh()` sets `ThemeZone.font(Typography.Role.BODY)` and `SurfaceContrast.painted(FlatLafInstaller.installedTheme(), active ? WindowPairs.TITLE : WindowPairs.INACTIVE_TITLE)`, then repaints.
  - `setActive` stores the state and refreshes.
  - The class comment says: IntelliJ Classic's `MenuFrameHeader` title (Rulings 13 and 14); no mouse listener or tooltip, so FlatLaf treats it as caption.
- **`WindowPairs`:** add the two pairs with one comment ("The Windows classic title bar's title, active and inactive (plan 14 Rulings 14): IntelliJ's TitlePane.infoForeground and inactiveInfoForeground"). Append both to `ALL`.
- **`CollapsingMenuBar`:**
  - Add a `ClassicWindowTitle title` field. `setTitle(value)` adds it and then the glue with `super.addImpl`, like `setHeader`, then revalidates.
  - `preferredLayoutSize`, when `title != null`, ignores the fold and asks for the folded minimum: `minimumMenusWidth()`, plus `UIScale.scale(ClassicWindowTitle.GAP)`, plus `Math.min(title.getPreferredSize().width, UIScale.scale(ClassicWindowTitle.MINIMUM))`, with the height the tallest child's, plus insets. FlatTitlePane sizes its left panel from this preferred width (`menuBarPlaceholder`, `FlatTitlePane.java:275-280`) and, when its own title area drops below `titleMinimumWidth`, shrinks the window buttons first (`:312-316`, down to `buttonMinimumWidth`, `:425-430`). Asking for every menu would squeeze the 44 px buttons in any window under about 900 px. The glue widening (`:684-693`) still gives the bar its real width, and `layoutContainer`'s `fit` does the folding.
  - `minimumLayoutSize` is unchanged.
  - `layoutContainer` with a title, before the placing loop (which now also skips `title`):
    ```java
    int inner = getWidth() - insets.left - insets.right, gap = UIScale.scale(ClassicWindowTitle.GAP);
    int keep = Math.min(title.getPreferredSize().width, UIScale.scale(ClassicWindowTitle.MINIMUM));
    int used = fit(inner - gap - keep);
    int x = insets.left + used + gap;
    title.setBounds(x, insets.top, Math.max(0, insets.left + inner - x), height);
    ```
    The no-header, no-title branch is unchanged.
  - `shownWidth` also skips `title`.
  - Update the class comment for the Windows classic title.
- **`WindowContent`:**
  - Add `private String frameTitle = "Jasper";` and `private ClassicWindowTitle classicWindowTitle;`.
  - In `onTitle` (`:359-372`), compute the formatted title once into a local, pass it to `nativeTitle`, store it in `content.frameTitle`, and `if (content.classicWindowTitle != null) content.classicWindowTitle.setText(formatted);`.
  - `setActive` adds `if (classicWindowTitle != null) classicWindowTitle.setActive(value);`.
  - `mount`'s branch becomes:
    ```java
    if (bar == null && look.classicTitle() && content.macOs) installMacTitleBand(root, content);
    else if (bar == null && look.classicTitle()) installClassicTitleBar(root, content);
    else if (bar == null) installCustomTitleBar(root, content);
    ```
  - Move `installCustomTitleBar`'s body into `private static boolean whenDecorated(JRootPane root, WindowContent content, java.util.function.BiConsumer<JRootPane, WindowContent> decorate)`, with `decorate.accept(root, content)` where it called `decorateTitleBar`. `installCustomTitleBar` returns `whenDecorated(root, content, WindowContent::decorateTitleBar)` and its comment is unchanged. `installClassicTitleBar` returns `whenDecorated(root, content, WindowContent::decorateClassicTitleBar)`, with a comment for the Windows classic title.
  - `decorateClassicTitleBar(root, content)`:
    ```java
    var title = new ClassicWindowTitle();
    title.setText(content.frameTitle);
    title.setActive(content.active);
    content.classicWindowTitle = title;
    content.chrome.menuBar().setTitle(title);
    root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_SHOW_TITLE, false);
    root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_HEIGHT, ClassicWindowTitle.BAR_HEIGHT);
    Runnable colors = () -> {
        // The toolbar's colour, not a title-bar zone, so any theme's bar matches its toolbar (Rulings 13).
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_BACKGROUND, ThemeZone.color(content.chrome().header(), SemanticColor.SURFACE_APP));
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_FOREGROUND, ThemeZone.color(null, SemanticColor.TEXT_SECONDARY));
        title.refresh();
    };
    colors.run();
    content.onThemeChanged = theme -> colors.run();
    dev.jasper.app.platform.TitleInset.publish(root, () -> root.getContentPane() == null ? 0 : root.getContentPane().getY());
    content.revalidate();
    ```
  - Add the package-private `classicWindowTitle()` accessor.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*CollapsingMenuBarTest' --tests '*ClassicWindowsTitleTest' --tests '*WindowPairsTest' --tests '*CustomTitleBarTest' --tests '*WindowChromeMenusTest' --tests '*WindowTitleTest' --tests '*ThemeMigrationCoverageTest'`
Expected: all pass except `ThemeMigrationCoverageTest`, which names `ClassicWindowTitle.java` and `ClassicWindowsTitleTest.java`.

If the rule assertion in `theTitleBarIsTheToolbarsColourWithTheMenusThenTheTitle` finds the toolbar's colour at `2 * top - 1`, read FlatLaf's `FlatTitlePaneBorder` again (Verified 20) and sample the row the border paints. The rule's existence is the requirement; never drop the assertion.

- [ ] **Step 5: Add the rows and commit**

```text
jasper-app/src/main/java/dev/jasper/app/workspace/ClassicWindowTitle.java,app.workspace,ClassicWindowTitle,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/workspace/ClassicWindowsTitleTest.java,New in Enterprise plan 14 Task 4; the Windows classic title bar's title: left after the menus; dimmed while inactive; caption for the window
jasper-app/src/test/java/dev/jasper/app/workspace/ClassicWindowsTitleTest.java,app.workspace,ClassicWindowsTitleTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/workspace/ClassicWindowsTitleTest.java,Test/fixture ownership; New in Enterprise plan 14 Task 4; the Windows classic title bar: embedded menus; the title; colours; the rule; folding; native decorations
```

Run: `./gradlew :jasper-app:test --tests '*ThemeMigrationCoverageTest' --tests '*ColourLiteralScanTest' :jasper-app:javadoc :jasper-app:verifyApplicationArchitecture`
Expected: PASS.

```bash
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -m "Plan 14 Task 4: the Windows classic title bar holds the menus and the title

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 5: Classic dialogs and overlays start below the classic title

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/WindowOverlay.java:126-131`, `jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java:210-249`
- Test: `jasper-app/src/test/java/dev/jasper/app/windows/ClassicWindowOverlayTest.java`

**Interfaces:**
- Consumes: `TitleInset.of(JRootPane)`, `TitleInset.publish`, `TitleInset.CHANGED` and `TitleInset.changed` (Task 2).
- Produces: `static NativeShells.TitleFollower NativeShells.followTitleBar(java.awt.Container window, JRootPane root, dev.jasper.ui.swing.ClassicDialog dialog)`, which replaces the two-argument form (one caller and one test). `static final class TitleFollower extends ComponentAdapter implements java.beans.PropertyChangeListener`: its `componentResized` and `propertyChange` both call `dialog.windowTitleHeight(window.getInsets().top + TitleInset.of(root))`.

- [ ] **Step 1: Write the failing test**

In `ClassicWindowOverlayTest`, change `:72` to `NativeShells.followTitleBar(window, root, dialog)` and add:

```java
/** Plan 14 (Rulings 19): under macOS's band or Windows' classic title bar the layer starts below it, and a centred dialog stays centred on the whole window. */
@Test void classicDialogsStartBelowTheClassicTitle() {
    var root = new JRootPane(); root.getLayeredPane().setSize(800, 600);
    dev.jasper.app.platform.TitleInset.publish(root, () -> 28);
    var body = new JPanel(); body.setPreferredSize(new Dimension(400, 201));
    var centred = new ClassicDialog(KitTestSupport.context(), body, "Unlock Vault", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED);
    var unlock = WindowOverlay.classic(root, centred);
    var window = new JPanel();
    var titleBar = NativeShells.followTitleBar(window, root, centred);
    try {
        unlock.show();
        titleBar.componentResized(new ComponentEvent(window, ComponentEvent.COMPONENT_RESIZED));
        assertThat(centred.getBounds()).as("the layer leaves the title chrome uncovered").isEqualTo(new Rectangle(0, 28, 800, 572));
        int height = unlock.cardBounds().height;
        assertThat(28 + unlock.cardBounds().y).as("centred on the whole window").isEqualTo((600 - height) / 2);
    } finally { unlock.close(); }
    var sheetRoot = new JRootPane(); sheetRoot.getLayeredPane().setSize(800, 600);
    dev.jasper.app.platform.TitleInset.publish(sheetRoot, () -> 28);
    var sheetBody = new JPanel(); sheetBody.setPreferredSize(new Dimension(440, 300));
    var sheet = new ClassicDialog(KitTestSupport.context(), sheetBody, "Add Login", Nodes.Hue.BLUE, ClassicDialog.Placement.SHEET);
    var addLogin = WindowOverlay.classic(sheetRoot, sheet);
    try {
        addLogin.show();
        assertThat(sheet.getY()).as("joined under the title chrome").isEqualTo(28);
        assertThat(addLogin.cardBounds().y).isZero();
    } finally { addLogin.close(); }
}

/** Review Focus 2: the title inset can change after the last resize (FlatLaf republishes full screen late); the layer and the card follow it. */
@Test void theLayerAndTheCardFollowATitleInsetChangeWithoutAResize() {
    var root = new JRootPane(); root.getLayeredPane().setSize(800, 600);
    int[] inset = {28};
    dev.jasper.app.platform.TitleInset.publish(root, () -> inset[0]);
    var body = new JPanel(); body.setPreferredSize(new Dimension(400, 201));
    var centred = new ClassicDialog(KitTestSupport.context(), body, "Unlock Vault", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED);
    var unlock = WindowOverlay.classic(root, centred);
    var window = new JPanel();
    var titleBar = NativeShells.followTitleBar(window, root, centred);
    root.addPropertyChangeListener(dev.jasper.app.platform.TitleInset.CHANGED, titleBar);
    try {
        unlock.show();
        titleBar.componentResized(new ComponentEvent(window, ComponentEvent.COMPONENT_RESIZED));
        int height = unlock.cardBounds().height;
        assertThat(centred.getY()).isEqualTo(28);
        inset[0] = 0;
        dev.jasper.app.platform.TitleInset.changed(root, 28);
        assertThat(centred.getBounds()).as("full screen: the layer covers the window").isEqualTo(new Rectangle(0, 0, 800, 600));
        assertThat(unlock.cardBounds().y).as("still centred on the whole window").isEqualTo((600 - height) / 2);
        inset[0] = 28;
        dev.jasper.app.platform.TitleInset.changed(root, 0);
        assertThat(centred.getY()).as("back below the band").isEqualTo(28);
        assertThat(28 + unlock.cardBounds().y).isEqualTo((600 - height) / 2);
    } finally { unlock.close(); root.removePropertyChangeListener(dev.jasper.app.platform.TitleInset.CHANGED, titleBar); }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-app:test --tests '*ClassicWindowOverlayTest'`
Expected: FAIL to compile: there is no three-argument `followTitleBar`.

- [ ] **Step 3: Implement**

- **`WindowOverlay.layout`:** `int top = Math.min(dev.jasper.app.platform.TitleInset.of(root), root.getLayeredPane().getHeight());` then `component.setBounds(0, top, width, height - top)`. Its comment: "below the window's classic title chrome (plan 14), else over the whole layered pane".
- **`WindowOverlay`:** a `PropertyChangeListener insetChanged = event -> layout();` field. `show()` adds it with `root.addPropertyChangeListener(TitleInset.CHANGED, insetChanged)` beside the `resize` listener, and `close()` removes it beside `resize`.
- **`NativeShells.inWindow`:** take `var root = container.getRootPane();`, call `dialog.windowTitleHeight(owner.getInsets().top + dev.jasper.app.platform.TitleInset.of(root));`, then `var titleBar = followTitleBar(owner, root, dialog);`, `owner.addComponentListener(titleBar)` and `root.addPropertyChangeListener(TitleInset.CHANGED, titleBar)`. The close action removes both.
- **`followTitleBar`:** returns the `TitleFollower`, which re-reads the same sum on resize and on `TitleInset.CHANGED`. Its comment adds "and the classic title chrome inside the root (macOS's band, Windows' classic title bar), which can change without a resize".

- [ ] **Step 4: Run the tests and commit**

Run: `./gradlew :jasper-app:test --tests '*ClassicWindowOverlayTest' --tests '*ClassicPlacementTest' --tests '*EnterpriseDialogMockTest' --tests '*WindowOverlay*' --tests '*NativeShells*'`
Expected: PASS.

```bash
git add jasper-app/src
git commit -m "Plan 14 Task 5: classic dialogs and overlays start below the classic title

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 6: Enterprise ships the classic title and the 28 px toolbar

**Files:**
- Modify:
  - `plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml:5,72`
  - `plugins/enterprise/src/main/resources/plugin.toml:6`
  - `jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java:18-22`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/PresentationConformanceTest.java:84`
  - `jasper-app/src/test/java/dev/jasper/app/application/EnterpriseFirstWindowTest.java:40`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/EnterpriseHomeTest.java:36`
  - `jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java`

**Interfaces:**
- Consumes: `MacTitleBand.HEIGHT`, `TitleInset.of`, `WindowContent.titleBand()`, `WindowLook.classicTitle()` and `ChromeRenderTest.LINUX`.

- [ ] **Step 1: Write the failing changes to the tests**

- **`EnterpriseLook.CHROME`:** takes `ChromeSpec.TitleBar.CLASSIC`; its comment adds "IntelliJ Classic's title (plan 14)".
- **`PresentationConformanceTest:84`:** `.isEqualTo(28)`, with the comment `// The user's 2026-10-19 direction: IntelliJ's 28 px toolbar.`.
- **`EnterpriseFirstWindowTest:40`** (`first.toolbarHeight()`) and **`EnterpriseHomeTest:36`** (`snapshot.toolbarHeight()`): both read the shipped kit's default, so each becomes `.isEqualTo(28)` with the same comment. `DefaultsPrecedenceTest` and `KitParserTest:174` read their own fixtures' 34 and stay.
- **`ChromeRenderTest`:**
  - `Case` gains the window's platform, with a 3-argument constructor for the real host:
    ```java
    record Case(String name, Supplier<ThemeController> themes, Projections projections, Projections.Platform platform) {
        Case(String name, Supplier<ThemeController> themes, Projections projections) { this(name, themes, projections, HOST.platform()); }
    }
    ```
  - After the `enterprise` case add:
    ```java
    // Plan 14: IntelliJ Classic's frame on macOS (the band) and Windows (FlatLaf's title pane with the menus); no mock shows them.
    cases.add(new Case("enterprise-mac", () -> EnterpriseLook.controller(false),
        Projections.of(EnterpriseLook.chrome(), true, true, "dev.jasper.enterprise", new Projections.Host(Projections.Platform.MAC, true)), Projections.Platform.MAC));
    cases.add(new Case("enterprise-windows", () -> EnterpriseLook.controller(false),
        Projections.of(EnterpriseLook.chrome(), true, true, "dev.jasper.enterprise", new Projections.Host(Projections.Platform.WINDOWS, false)), Projections.Platform.WINDOWS));
    ```
  - In `everyLookBuildsItsChromeFlush`:
    - build the owner with `DesktopTestSupport.content(..., window.chrome(), look.platform() == Projections.Platform.MAC)`;
    - before `mount`, when `window.classicTitle() && look.platform() == Projections.Platform.WINDOWS`, call `root.setWindowDecorationStyle(JRootPane.FRAME); root.updateUI();`;
    - after `mount`, when `window.classicTitle() && look.platform() == Projections.Platform.MAC`, call `root.getJMenuBar().setVisible(false);` with the comment `// macOS shows the root's menus in the screen menu bar, not in the window.`.
  - In `check`, the barless block's expected content top becomes:
    ```java
    int expected = !window.classicTitle() ? root.getJMenuBar().getHeight()
        : owner.titleBand() != null ? MacTitleBand.HEIGHT : root.getContentPane().getY();
    assertThat(contentTop).as(look.name() + ": the content meets the title chrome flush").isEqualTo(expected);
    if (window.classicTitle()) {
        assertThat(contentTop).as(look.name() + ": a classic title takes room").isPositive();
        assertThat(dev.jasper.app.platform.TitleInset.of(root)).as(look.name() + ": the classic title's inset").isEqualTo(contentTop);
    }
    ```
    `check` needs `window` (`WindowLook.of(look.projections())`), already computed at `:97`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests '*EnterpriseFixtureTest' --tests '*PresentationConformanceTest' --tests '*EnterpriseFirstWindowTest' --tests '*EnterpriseHomeTest'`
Expected: FAIL: the shipped kit still reads `native` and 34.

- [ ] **Step 3: Ship the kit**

- **`enterprise.kit.toml`:** `title_bar = "classic"` and `"window.toolbar_height" = 28`.
- **`plugin.toml`:** `description = "The Enterprise look: an IntelliJ Classic title bar on macOS and Windows, labelled tool window stripes, a Run window and classic controls, in the Enterprise theme or any other."`.

- [ ] **Step 4: Run the shipped-kit and render tests**

Run: `./gradlew :jasper-app:test --tests '*EnterpriseFixtureTest' --tests '*PresentationConformanceTest' --tests '*EnterpriseFirstWindowTest' --tests '*EnterpriseHomeTest' --tests '*EnterpriseKitRenderTest' --tests '*ChromeRenderTest' --tests '*ProjectionReadTest'`
Expected: PASS. `jasper-app/build/chrome-renders/` now holds `chrome-enterprise-mac.png`, `chrome-enterprise-windows.png` and their `-palette` and `settings-` images. Open the two chrome images and confirm what each shows:
- the band above the toolbar in one colour;
- FlatLaf's title bar with File … Help, then the title, the rule and the toolbar below.

- [ ] **Step 5: Run the whole jasper-app suite**

Run: `./gradlew :jasper-app:test --continue`
Expected: BUILD SUCCESSFUL. Every Enterprise mock test runs under the Linux host and is unchanged, because those tests set their toolbar height explicitly.

- [ ] **Step 6: Prove the Linux render unchanged**

Run: `cmp jasper-app/build/chrome-renders/chrome-enterprise.png "$base/chrome-enterprise.png" && echo same` (with `base` as in Task 1 Step 0)
Expected: `same`. The image renders at the test's default 30 px toolbar, which the kit default does not reach.

- [ ] **Step 7: Commit**

```bash
git add plugins/enterprise/src/main/resources jasper-app/src
git commit -m "Plan 14 Task 6: Enterprise ships IntelliJ Classic's title bar and the 28 px toolbar

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 7: The mock tests and pairs follow the 28 px toolbar

**Files:**
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseWorkstationMockTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/ClassicHeaderTest.java:67,94,103,111`, `tools/visual/pairs.json` (the two `chrome-toolbar-*` entries)

**Interfaces:**
- Produces: `EnterpriseWorkstationMockTest.TOOLBAR = 28` and `EnterpriseWorkstationMockTest.SHORTER = 2 * (34 - 28)`, the device pixels by which the mocks' 34 px row is taller.

- [ ] **Step 1: Change the expectations to the 28 px row**

Every changed line carries the comment `// The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.`

- **`EnterpriseWorkstationMockTest`:**
  - Add the two constants. `workstation()` calls `owner.setToolbarHeight(TOOLBAR)`, and its comment says "the 28 px toolbar row".
  - `theRowsDownTheWindowAreTheMocks`: `expected` becomes `new ArrayList<>(MockProfile.solid(...))`, because `solid` returns an unmodifiable `toList()`; then, before the comparison loop, `expected.set(2, new MockProfile.Run(expected.get(2).start(), expected.get(2).length() - SHORTER, expected.get(2).color()));`. Runs are compared by length, so the rows below need nothing more.
  - `theToolbarRowIsTheMocks`:
    - the ink top assertion becomes `isCloseTo(expected.get(i).top() - rise - SHORTER / 2, within(3))`;
    - the field top becomes `isCloseTo(122 - SHORTER / 2, within(2))`;
    - the mock's two ink rectangles are unchanged; the render's two (`new Rectangle(4, 112 - Y, 400, 64)` and `new Rectangle(2722 - 44 - X, 112 - Y, 160 + 44, 64)`) take the height `64 - SHORTER`, so they stay inside the render's 56-row toolbar row (device rows 52 to 103 of 50 to 105). `MockProfile.inks` counts a column as ink wherever any pixel differs from the region's top-left one, and a 64-row region would reach the toolbar rule and the tab strip and read every column as ink.
  - `theTabsAreTheMocks`: replace each `25 + 35` with `25 + TOOLBAR + 1`, at `underline`, the active-tab probe and the `-tabs` cut.
  - `theStatusCellsAreTheMocks` is unchanged: it measures from the window's bottom.
- **`ClassicHeaderTest`:**
  - `:67` becomes `owner.setToolbarHeight(28);`;
  - `:94` becomes `assertThat(header.getHeight()).as("28 and the rule").isEqualTo(29);`;
  - `:103` becomes `assertThat(first.getY()).isEqualTo(2);`;
  - `:111` becomes `assertThat(field.getY()).as("centred in the 28 px row").isEqualTo(3);`.

  These are `(28 - 24) / 2` for the 24 px button and `(28 - 22) / 2` for the 22 px field.

- [ ] **Step 2: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*EnterpriseWorkstationMockTest' --tests '*ClassicHeaderTest' --tests '*EnterpriseMenuRowMockTest'`
Expected: PASS.

If an assertion misses by more than its tolerance:
- measure the render;
- set the expectation to the mock's value shifted by `SHORTER` or `SHORTER / 2` as the geometry says, with the comment above;
- record it under "Execution notes".

Never widen a tolerance to pass.

- [ ] **Step 3: Move the toolbar pairs**

Edit `tools/visual/pairs.json` with `python3 -I` (keep the file's formatting: `json.dumps(..., indent=2, ensure_ascii=False)` plus a trailing newline, and check the diff touches only these entries). For both `chrome-toolbar-light` and `chrome-toolbar-dark`:
- `crop` becomes `[2, 116, 2880, 56]`: the mock's 34 px row less 3 px above and below;
- `render_crop` becomes `[0, 50, 2880, 56]`: the 28 px row without its rule. Both crops are 56 rows: `compare_mock.py` pads a shorter crop with magenta and never resizes (`:91-98`), and that padding would be a spurious edge band;
- then check that every entry of the pair's `colours` (`#F2F2F2`, `#D1D1D1`, `#C4C4C4`, `#FFFFFF` in Light; `#2B2D30` in Dark) is still present in the render's 56-row crop. If one is missing, use 58 rows on both sides instead: `crop` `[2, 115, 2880, 58]` and `render_crop` `[0, 50, 2880, 58]`, the row and its rule;
- add or extend `note` with "The user's 2026-10-19 direction: IntelliJ's 28 px toolbar; the mock's middle 28 px against the row, equal heights." (or "…against the row and its rule, 58 rows each." for the fallback), and record the choice in the execution notes.

- [ ] **Step 4: Check the pairs**

Run:
```bash
./gradlew :jasper-app:test --tests '*EnterpriseWorkstationMockTest'
python3 -I tools/visual/check_pairs.py "$(mktemp -d)" chrome-
```
Expected: every `chrome-` pair PASS. Record the two toolbar agreements. If one is below its floor of 0.3:
- set `min_agreement` to the measured value rounded down to two places;
- add a note naming the measured value and the user's 2026-10-19 direction;
- record it in the execution notes and STATUS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src tools/visual/pairs.json
git commit -m "Plan 14 Task 7: the Enterprise mock tests and toolbar pairs at 28 px

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 8: Documentation, STATUS and the whole build

**Files:**
- Modify:
  - `docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`
  - `docs/app-architecture.md:221-225`
  - `docs/STATUS.md`
  - this plan's status banner and execution notes

- [ ] **Step 1: Amend the spec** (each edit is the smallest accurate one; quote the old text, re-wrap at 120 columns with the paragraph's indent)

- **Header:** after the plan 13 line (`:21`), add:

  > Amended on 2026-10-19 at the user's direction (plan 14): 1 (decision 8), 3 (principles 5 and 8), 4.8, 5.1, 5.2, 5.3, 5.9, 9.1, 10.1, 10.2, 10.3, 10.8, 10.10, 12.1, 16.1, 16.2, 18 and 19.1. On macOS and Windows the Enterprise look draws IntelliJ Classic's title bar (`title_bar = "classic"`): on macOS a band in the toolbar's colour under its own traffic lights and title, with the menus in the screen menu bar; on Windows a custom title bar holding the menus and the title. Linux keeps the system title bar and the in-window menu row, and the toolbar is 28 px; these override the mocks' native title bar, in-window menu row and 34 px toolbar.
- **§1, `:43`:** "gives native title bars, menus inside the window," becomes "gives IntelliJ Classic's title bar (Linux keeps the system's, with the menus inside the window),".
- **Decision 8 (`:69`):** replace the line with:

  > **macOS menus under Enterprise are in the screen menu bar**, as in the default look and IntelliJ Classic (since 2026-10-19, the user; plan 14), with About Jasper and Settings… in the application menu. Windows puts them in the title bar and Linux in the window.
- **Principle 5 (`:261`):** "`title_bar = "native"`" becomes "`title_bar = "native"` (or `"classic"` on macOS and Linux)".
- **Principle 8 (`:269-270`):** "The native title, menu bar and toolbar rows are separated by 1 px rules with no gutters." becomes "The title, menu bar and toolbar rows are separated by 1 px rules with no gutters, except that macOS's classic title band and the toolbar row are one band, as in IntelliJ."
- **4.8 step 4 (`:462-464`):** after "for a native title" insert "(a classic title commits the default look's flags on macOS and Windows and the native ones on Linux)".
- **5.1 listing:** `title_bar = "native"` (`:515`) becomes `title_bar = "classic"`, and `"window.toolbar_height" = 34` (`:582`) becomes `28`.
- **5.2:**
  - `:596`: "all two-valued except the `window_title` template" becomes "all two-valued except `title_bar`, which has three values, and the `window_title` template".
  - The `title_bar` row becomes:

    > | `title_bar` | `unified` | `classic` | IntelliJ Classic's frame (10.1). On macOS, a 28 pt band in the toolbar's colour under the system's transparent title, with the menus in the screen menu bar. On Windows, a custom title bar holding the menus and the window title. On Linux, the OS title bar (`TitleBarStyle.NATIVE`). The workspace header becomes a chrome row under it. The third value, `native`, gives the OS title bar on every platform |
  - The `menu_bar` row's last cell becomes:

    > The classic menus (10.2), belonging to the window. Under the OS title bar they are a row in the window (macOS: `apple.laf.useScreenMenuBar=false`). Under `title_bar = "classic"` they are in the title bar on Windows and in the screen menu bar on macOS.
- **5.3:** "requires `title_bar = "native"`" becomes "requires `title_bar = "native"` or `"classic"`".
- **5.9 (`:763`):** "- `menu_bar = "window"` without `title_bar = "native"`;" becomes "- `menu_bar = "window"` with `title_bar = "unified"`;".
- **9.1:**
  - The consumers row for `ShellLook` becomes `` | `ShellLook` | `unifiedTitle`, `windowMenus`, `followThemeAppearance`, `classicTitle` | windows: `ShellChrome.of`, `NativeShells`; workspace: `WindowLook` (`classicTitle`) | ``.
  - After that table, add: "`Projections.Host` gives the table the platform (macOS, Windows, or Linux for any other system) and whether the unified macOS title bar is available; the platform changes only `title_bar = "classic"`, which on Linux gives exactly `native`'s values."
  - The field rows become:
    - `DesktopFlags.screenMenuBar`, `nativeTitle`: "false when `menu_bar = "window"`, except under `title_bar = "classic"` on macOS and Windows; true when `title_bar = "native"`, or `"classic"` on Linux | as committed";
    - `WorkspaceLook.header`: "CLASSIC when `title_bar` is `native` or `classic`";
    - `ShellLook.windowMenus`: "true when `menu_bar = "window"`, except under `title_bar = "classic"` on macOS, where auxiliary frames carry the minimal File menu for the screen menu bar | as committed";
    - `ShellLook.followThemeAppearance`: "true when `title_bar` is `native` or `classic` | true";
    - a new row after it: `` | `ShellLook.classicTitle` | true when `title_bar = "classic"` on macOS or Windows | false | ``;
    - `SettingsChrome.integratedTitle`: "false when `title_bar` is `native` or `classic` | false";
    - `SettingsChrome.hiddenKeys`: "`window.native_decorations` when `title_bar = "native"` or on macOS, or `"classic"` on Linux, …" (the rest unchanged);
    - `DialogMapping`: "…; `windowTitled` when `title_bar` is `native` or `classic`".
- **10.1:**
  - In the auxiliary-frames bullet, after "show no menu row" insert "(on macOS under a classic title they carry the minimal File menu, which the screen menu bar shows)".
  - The last bullet becomes "The title, menu bar and toolbar rows meet flush, separated by 1 px rules, except that macOS's classic band and the toolbar row are one band."
  - Then add three bullets:

    > - `title_bar = "classic"` (the user, 2026-10-19; plan 14) is IntelliJ Classic's frame. On macOS `TitleBarStyle` stays `MAC_UNIFIED` and the committed flags are the default look's, so the menus are in the screen menu bar. The terminal window's root pane takes `apple.awt.fullWindowContent` and `apple.awt.transparentTitleBar`. `MacTitleBand` paints a 28 pt band in the toolbar's colour (`surface.app`, never a title-bar zone) above the toolbar row, and macOS draws the traffic lights and the frame's title over it. The window's appearance is `NSAppearanceNameVibrantLight` or `NSAppearanceNameVibrantDark` by the theme. Full screen removes the band; a double-click in it zooms, minimises or does nothing as `AppleActionOnDoubleClick` says. Auxiliary windows keep their system title bars.
    > - On Windows `TitleBarStyle` is `CUSTOM` as in the default look. FlatLaf's 28 px title pane, in the toolbar's colour, embeds the classic menus and then `ClassicWindowTitle`: the frame's title, left-aligned 22 px after them, in `text.secondary` (`text.tertiary` while the window is inactive). The classic menu row's 1 px `border.divider` rule runs under the whole bar. The menus fold into "…" and ☰ before the title shrinks below 60 px, and the window buttons are the default look's. `window.native_decorations = true` gives the system title bar and the in-window menu row. Auxiliary frames and dialogs take FlatLaf's title panes without a menu row.
    > - On Linux a classic title is the system title bar, as `native`.
- **10.2:** "Help holds About Jasper on every platform, including macOS under `menu_bar = "window"` (today it is in the application menu there, `AboutWindows.java:72`)." becomes "Help holds About Jasper wherever the screen menu bar is off, including macOS under `menu_bar = "window"` with a native title. While macOS shows the screen menu bar (the default look, and a classic title) it is in the application menu (`AboutWindows.java:71`)."
- **10.3 (`:1237`):** "`window.toolbar_height` is the classic toolbar row's height." becomes "`window.toolbar_height` is the classic toolbar row's height; Enterprise's default is 28, IntelliJ's (the user, 2026-10-19, over the mocks' 34)."
- **10.8 (`:1337`):** "covering the window below its title bar;" becomes "covering the window below its title bar (and below macOS's classic band or Windows' classic title bar, which `TitleInset` publishes);".
- **10.10:** append "Under a classic title Compact keeps the system title bar on macOS and Windows, as the default look's Compact does, without the band; macOS keeps the screen menu bar and Windows shows the in-window menu row."
- **12.1:**
  - The `integratedTitle` Enterprise cell becomes "false: the system's title bar with "Settings" (FlatLaf's title pane on Windows)".
  - In `hiddenKeys`, "`window.native_decorations` (implied by the look)" becomes "`window.native_decorations` (implied by the look; shown on Windows, where the classic title honours it)".
- **16.1 item 4:** after "the fallback combination (native title, window menus, default kit)," insert "the shipped look on each platform (`enterprise` on Linux, `enterprise-mac` with the band, `enterprise-windows` with FlatLaf's title pane),".
- **16.2:** "the macOS in-window menu bar and native title, and Windows and Linux NATIVE with the caption colour;" becomes "the macOS classic title band (its traffic lights and title against IntelliJ's) and screen menu bar, Windows' classic title bar, and Linux NATIVE with the caption colour;".
- **18:** delete "the macOS screen menu bar under Enterprise; " (it wraps across `:1842-1843`).
- **19.1:**
  - "Thirteen plans" becomes "Fourteen plans", and "Order: 1 to 13 in sequence" becomes "Order: 1 to 14 in sequence".
  - Add the row:

    > | 14 | `title-bar` | The user's 2026-10-19 direction: IntelliJ Classic's title bar on macOS (the toolbar-coloured band under the system's lights and title, the screen menu bar, full screen and double-click) and Windows (the custom title bar holding the menus and the left-aligned title, dimmed while inactive, over its rule, folding when narrow), Linux unchanged, classic dialogs below the title, the 28 px toolbar |

Check:

```bash
spec=docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md
grep -n 'macOS menus under Enterprise are in the window\|Thirteen plans\|1 to 13 in sequence\|title_bar = "native"$' "$spec"   # prints nothing
python3 -I - "$spec" <<'PY'
import re, sys
text = re.sub(r"\s+", " ", open(sys.argv[1], encoding="utf-8").read())
for phrase in ("the macOS screen menu bar under Enterprise", "requires `title_bar = \"native\"`:"):
    print(text.count(phrase), "(want 0)", phrase)
for phrase in ("Amended on 2026-10-19 at the user's direction (plan 14)", "macOS menus under Enterprise are in the screen menu bar",
               "requires `title_bar = \"native\"` or `\"classic\"`", "`ShellLook.classicTitle`", "`MacTitleBand` paints a 28 pt band",
               "`ClassicWindowTitle`", "`TitleInset` publishes", "Enterprise's default is 28", "Fourteen plans", "Order: 1 to 14 in sequence",
               "`enterprise-mac` with the band", "a classic title commits the default look's flags"):
    print(text.count(phrase), "(want at least 1)", phrase)
PY
```

Expected: no `grep` output, two `0` lines, and every other count at least `1`.

- [ ] **Step 2: Correct the guide**

In `docs/app-architecture.md`, replace the sentence at `:221-224` (from "A look with a native title (the" to "binds Close Window's shortcut itself).") with:

> A look with a native title gives every platform the system title bar. With `menu_bar = "window"` the bootstrap turns macOS's screen menu bar off, so each terminal window's menus are a row in the window (an auxiliary frame shows none and binds Close Window's shortcut itself).
>
> The Enterprise presentation's classic title (`title_bar = "classic"`) is IntelliJ Classic's:
> - On macOS, `MacTitleBand` (platform) is a 28 pt band in the toolbar's colour under the system's transparent title, which macOS draws with the traffic lights. The menus stay in the screen menu bar, and an auxiliary frame keeps its system title bar and shows the minimal File menu there.
> - On Windows, FlatLaf's title pane embeds `CollapsingMenuBar` and `ClassicWindowTitle`, the window title left-aligned after the menus, over a 1 px rule.
> - On Linux it is the system title bar.
>
> `TitleInset` keeps classic in-window dialogs and overlays below either.

Run: `./gradlew :jasper-app:test --tests '*AppDocumentationTest'`
Expected: PASS.

- [ ] **Step 3: Prove the default look unchanged**

```bash
base=/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan14-baseline
out=$(mktemp -d)
ls "$base/gallery-dark-1x" "$base/gallery-light-1x" "$base/app" > /dev/null || echo "NO BASELINE"
rm -rf jasper-ui/build/gallery
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
git diff --stat 3a36993ba -- jasper-app/src/test/java/dev/jasper/app/workspace/CustomTitleBarTest.java jasper-app/src/test/java/dev/jasper/app/workspace/MacTitleBarTest.java jasper-app/src/test/java/dev/jasper/app/workspace/DefaultDockPixelsTest.java | tail -1
```

Expected:
- `dark: identical (161 files)` and `light: identical (161 files)`;
- `11` and `jasper-app default look: identical`, with no `diff` output;
- an empty last line: the three guard tests are unedited.

If the baseline is missing, make one from `3a36993ba` with `git worktree add --detach "$TMPDIR/plan14-main" 3a36993ba`, run the same commands with `./gradlew -p "$TMPDIR/plan14-main" …`, compare, then `git worktree remove`. Never compare against renders made after Task 1.

- [ ] **Step 4: Run the whole build and count**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Then count from the XML:

```bash
python3 -I - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = e = s = 0
for p in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    r = ET.parse(p).getroot(); t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
print(f"{t} tests, {f} failures, {e} errors, {s} skipped")
PY
```

Expected: 0 failures and 0 errors. Record the counts; the base is 11,514 tests and 25 skipped.

- [ ] **Step 5: Render the Enterprise gallery and check the pairs**

```bash
for theme in light dark; do ./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=$theme -Pjasper.galleryScale=2; done
python3 -I tools/visual/check_pairs.py "$(mktemp -d)" > "$out/pairs.txt"; echo "exit $?"
diff <(sed 's/ agreement=.*//' "$base/pairs.txt") <(sed 's/ agreement=.*//' "$out/pairs.txt") && echo "same verdicts"
diff "$base/pairs.txt" "$out/pairs.txt"
```

Expected:
- `exit 0`;
- `same verdicts` (101 of 101 PASS);
- the last `diff` shows only the two `chrome-toolbar-*` lines (their new crops) and any pair Task 7 re-measured.

Record each changed line.

- [ ] **Step 6: Write `docs/STATUS.md`'s new top entry and this plan's banner**

The entry goes above plan 13's and starts as below. The template stays fenced here, because inline links in a plan's template fail `AppDocumentationTest`; the links are written only in STATUS.

```markdown
**Enterprise presentation, plan 14: IntelliJ Classic's title bar at the user's direction — 2026-10-19** (`claude/enterprise-title-bar`, worktree `.claude/worktrees/enterprise-14`, from `main` at `3a36993ba`; [plan](superpowers/plans/2026-10-19-enterprise-14-title-bar.md), [spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md))
```

Then, in the series' style, give:
- the request and the follow-up, quoted, and what they became:
  - `title_bar = "classic"`;
  - the macOS band, the screen menu bar, full screen and double-click;
  - the Windows title bar with the menus, the left-aligned title dimmed while inactive, the rule and folding;
  - Linux unchanged;
  - `TitleInset`;
  - the 28 px toolbar;
- the rulings (this plan's 24, one clause each);
- the deviation from the approved design: "W1's `centerTitleIfMenuBarEmbedded` is replaced by `ClassicWindowTitle` (Rulings 13) because the key is process-wide and FlatLaf's title does not dim";
- the deviations from the Enterprise mocks, as the user's 2026-10-19 direction:
  - macOS and Windows no longer show the mocks' native title bar or in-window menu row (the mocks stay true on Linux);
  - the toolbar is 28 px: the shifted `EnterpriseWorkstationMockTest` and `ClassicHeaderTest` expectations and the two toolbar pairs' crops, with their measured agreements;
- the check counts, "N of N" pairs, the byte-identical gallery and the 11 identical default-look renders;
- the lights checkpoint (done before execution): with Jasper's app launcher the band draws IntelliJ's lights (23 pt apart, about 14 pt across) and a leading title at about 84 pt; through `bin/java` (`:jasper-app:run`) macOS draws the legacy controls and centres the title;
- **Before checking:** "your `~/.config/jasper/config.toml` saves `window.toolbar_height = 34` (and `window.toolbar = "icons_and_labels"`), which override the look's defaults; delete those lines, or Reset them in Settings, to see the 28 px toolbar";
- the GUI checks for the user, in this order:
  1. **macOS, Enterprise Light:**
     - in the packaged app (`./gradlew :jasper-app:packageApp`, then open `jasper-app/build/packaging/image/Jasper.app`; a `:jasper-app:run` window shows macOS's legacy controls by design), the traffic lights and the title against IntelliJ's screenshot (12.webp): centres 23 pt apart, about 14.7 pt across, the title leading-aligned after the lights;
     - the title: whether macOS leading-aligns or centres it (if centred, a follow-up paints it);
     - dragging the window by the band;
     - a double-click in it with System Settings › Desktop & Dock's double-click action at Zoom, Minimize and Do Nothing.
  2. The band's colour against the toolbar, with no seam, in Enterprise Light and Dark and under another theme.
  3. The screen menu bar:
     - the application menu's About Jasper, Settings… and Quit;
     - File, Edit, View, Session, Transfer, Tools, Window and Help;
     - no stray "…" or ☰ menu;
     - ⌘Q once.
  4. Full screen and back: the band leaves and returns, and the lights and title follow.
  5. Auxiliary windows (Settings, About, Licenses, Errors, the Vault manager, a plugin window):
     - their system title bars;
     - the screen menu bar's File menu while one is focused;
     - ⌘W closing it.
  6. Classic in-window dialogs and the Vault's sheets below the band.
  7. **Windows:**
     - the menus in the title bar after the icon;
     - the title left after them, dimming when another window is active;
     - the rule under the bar;
     - a narrow window folding to "…" and ☰;
     - maximise and restore;
     - dragging and double-clicking empty title-bar space and the title;
     - in-window dialogs below the bar;
     - `window.native_decorations = true` giving the system title bar and the menu row;
     - Compact.
  8. **Linux:** unchanged apart from the 28 px toolbar.
- the reconciliation note: the unmerged `claude/workspace-tabs` series rules that a child window has no menu bar under Enterprise. Whichever of it and this plan merges second reconciles them; on macOS a child window then needs the screen menu bar's menus.
- end with "Not merged, not pushed."

Add a status banner under this plan's title: `> **Status (2026-10-19): executed on `claude/enterprise-title-bar` (`<first>..<last>`).** Deviations from this plan's text: <list, or none>.` The banner also names the approved design's one deviation, as its own sentence: "Deviation from the approved design: W1's `centerTitleIfMenuBarEmbedded` is replaced by `ClassicWindowTitle` (Rulings 13) because the key is process-wide and FlatLaf's title does not dim." Then add an `## Execution notes (2026-10-19)` section at the end with the execution rulings.

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
git commit -m "Docs: Enterprise plan 14, IntelliJ Classic's title bar, at the user's direction

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

Expected: the hygiene script prints nothing, `AppDocumentationTest` passes and the commit succeeds.

## Execution notes (2026-10-19)

**Refresh.** The branch's code equals `3a36993ba` (`git diff --quiet 3a36993ba HEAD -- . ':(exclude)docs'` holds; the three plan-14 commits touch only this file), and `main` is still `3a36993ba`. The five refresh greps give `1`, `1`, `1`, `1` and `0`. Every anchor in Verified facts, the task file lists, the spec and `docs/app-architecture.md` was re-read against the worktree, and the FlatLaf ones against `flatlaf-3.7-sources.jar`. Five were imprecise and are corrected in place: Verified 4 (`WindowLook` is constructed 12 times in tests, not 13), 7 (the Desktop handlers are at `:403-410`), 12 (the header fill is `WindowChrome.java:588`, `chromeRow()` `:185-192`), 15 (`setActive` ends at `:1496`) and 19 (`zeroInFullScreen` is documented at `FlatClientProperties.java:335`). Task 7 Step 1 is corrected in place by Rulings E9 and E10. The lights checkpoint is done (`2060c1ae9`); no task launches a window.

Tasks, in order: 1 the model (`title_bar = "classic"`, `Projections.Platform`, four-part `ShellLook`); 2 `MacTitleBand`, `MacTitleDoubleClick`, `TitleInset`; 3 macOS windows take the band; 4 the Windows classic title bar (`ClassicWindowTitle`, `CollapsingMenuBar.setTitle`, two pairs); 5 classic dialogs and overlays below the title inset; 6 the kit ships `classic` and 28 px, `ChromeRenderTest`'s two new cases; 7 the mock tests and toolbar pairs at 28 px; 8 documentation, STATUS, the byte-identity proof and the whole build.

### Pre-flight scan

| Tasks | Shared file or interface | Agrees? |
|---|---|---|
| 1 and 3 | `ShellLook.classicTitle()` read by `WindowLook.of`; `Projections.Platform` in `ClassicMacTitleTest` | yes |
| 1 and 4 | the same, under the Windows host in `ClassicWindowsTitleTest` | yes |
| 1 and 6 | `EnterpriseLook.java` (1 adds `titled`, 6 changes `CHROME`); `ChromeRenderTest` (1 adds `HOST`'s platform and `LINUX`, re-hosts `enterprise`; 6 adds `Case.platform`, two cases and the `check` branch) | yes: different members; 6's three-argument `Case` uses 1's `HOST.platform()` (E3) |
| 1 and 7 | the mock tests' Linux host (1) that keeps them on the system title and in-window menus while 7 moves them to 28 px | yes |
| 2 and 3 | `MacTitleBand.install(JRootPane, Supplier<Color>)`, `setDark`, `fill`, `HEIGHT`, `TitleInset.of`; `migration.csv` | yes (E6) |
| 2 and 4 | `TitleInset.publish`; `migration.csv` | yes |
| 2 and 5 | `TitleInset.of`, `publish`, `CHANGED`, `changed` | yes |
| 3 and 4 | `WindowContent.mount`'s branch (4 restates 3's two branches and adds its own), `onThemeChanged`, `close()`, `DesktopTestSupport.content(..., boolean macOs)`, `migration.csv` | yes: separate branches; append-only rows |
| 3 and 6 | `WindowContent.titleBand()`, `MacTitleBand.HEIGHT`, the four-argument `DesktopTestSupport.content` in `ChromeRenderTest` | yes (E8) |
| 4 and 6 | the decorated Windows root in `ChromeRenderTest`; its content top is the content pane's y, which `TitleInset` publishes | yes |
| 3, 4 and 5 | the band and the Windows decoration publish the inset the overlay and `followTitleBar` read | yes |
| 6 and 7 | 6 Step 5 relies on the mock tests setting their toolbar height (34) explicitly; 7 changes them to 28 | yes |
| 1, 6 and 8 | the Step 0 baseline (`$base`) and the after-renders (`$out`) across separate shells | ruled (E4) |
| 7 and 8 | the pairs' verdicts; 8 Step 5 expects only the two `chrome-toolbar-*` lines to differ | yes |
| 1 | its own text | ruled (E1, E2, E3) |
| 2 | its own text | yes; E6 |
| 3 | its own text | yes |
| 4 | its own text | ruled (E5) |
| 5 | its own text | ruled (E7) |
| 6 | its own text | ruled (E8) |
| 7 | its own text | ruled (E9, E10) |
| 8 | its own text | ruled (E4, E11, E12) |

### Rulings

- **E1. Task 1 updates two more `ChromeSpec` comments.** Besides `@param menuBar`, the class comment ("nine two-valued points") becomes "eight two-valued points, the three-valued `title_bar` and the window title template", and the constructor comment ("window menus need a native title bar") becomes "a native or classic title bar". — Why: the third value makes both false, and doclint does not catch it. — Cost if wrong: two comment lines.
- **E2. Task 1's `ShellChromeTest` addition imports `dev.jasper.app.presentation.ChromeSpec` and `Projections`** (the file imports only `ShellLook`). `ClassicWindowTitle` (Task 4) imports `dev.jasper.ui.metrics.Typography`. — Why: the plan's code names them unqualified. — Cost if wrong: a compile error the executor fixes at once.
- **E3. `ChromeRenderTest.HOST` drops `SystemInfo`; `PageContext.standard()` reads the platform from `os.name`.** `HOST` today reads `SystemInfo.isMacOS`; Task 1's replacement uses `Projections.Platform.of(System.getProperty("os.name", ""))`, so the now-unused `com.formdev.flatlaf.util.SystemInfo` import goes. `PageContext.standard()` takes `PluginSelection.host()`'s form (`Platform.of(os.name)`, unified only on MAC with full-window content), and `SettingsChromeTest:73` keeps its `mac` local for the second argument. — Why: `SettingsChromeTest` asserts `PageContext.standard()` equals its own host's projections, so both must read the same facts; `os.name` and `SystemInfo.isMacOS` agree. — Cost if wrong: one failing equality on an exotic JVM.
- **E4. Shell variables do not survive between steps.** Task 1 Step 8 and Task 6 Step 6 first set `base=/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan14-baseline`. Task 8 Step 3 uses `out=/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan14-after` (`rm -rf` then `mkdir -p`) instead of `mktemp -d`, and Step 5 sets the same `base` and `out`. — Why: each command runs in a fresh shell, and Step 5 writes `$out/pairs.txt`. — Cost if wrong: a comparison against an empty path, caught by the steps' expected output.
- **E5. Task 4's `CollapsingMenuBar` keeps every title-free path byte-for-byte.** `preferredLayoutSize` and `layoutContainer` take the title branch only when `title != null`; the header branch, the no-header `fit` and the placing loop are unchanged apart from also skipping `title`. The class comment gains one sentence for the title. — Why: the default look's Windows and Linux bar must not move (Global Constraints). — Cost if wrong: `CustomTitleBarTest` and the gallery catch it.
- **E6. `MacTitleBand.install`'s two-argument form runs `MacTitleDoubleClick.prefetch()` on macOS, also under tests** (`ClassicMacTitleTest`, `ChromeRenderTest`'s `enterprise-mac`). Accepted: it starts one virtual thread per process that runs `/usr/bin/defaults read -g AppleActionOnDoubleClick` with a 2 s limit and changes nothing. `MacTitleBandTest` uses the three-argument form. — Why: the interface is the plan's, and a read-only system query is harmless headless. — Cost if wrong: a 2 s idle thread in the test JVM.
- **E7. Task 5's `WindowOverlay` imports `dev.jasper.app.platform.TitleInset`** (its `show()` text names `TitleInset.CHANGED` unqualified), and `NativeShells`' close action removes both the component listener and the `TitleInset.CHANGED` listener. — Why: the plan's excerpts mix qualified and short names. — Cost if wrong: a compile error.
- **E8. In `everyLookBuildsItsChromeFlush` the macOS case hides the root's menu bar between `mount` and the first `MockProfile.print`.** — Why: the loop prints right after `mount` (`:167-168`), so "after `mount`" must mean before that print, or the `enterprise-mac` image would show a menu row AppKit never draws in the window. — Cost if wrong: a wrong image and a failing flush check.
- **E9. `theRowsDownTheWindowAreTheMocks` copies `expected` into an `ArrayList` before `expected.set(2, …)`** (corrected in Task 7 Step 1). — Why: `MockProfile.solid` returns `toList()`, which is unmodifiable, so the plan's `set` would throw in Light (Dark copies only later, at `:77`). — Cost if wrong: an `UnsupportedOperationException`.
- **E10. The render's ink rectangles in `theToolbarRowIsTheMocks` shrink to `64 - SHORTER` rows** (corrected in Task 7 Step 1); the mock's stay. — Why: `MockProfile.inks` treats a column as ink when any pixel in it differs from the region's top-left pixel; at 28 px the render's 64-row regions (device rows 52 to 115) cross the toolbar rule (106) and the tab strip, so every column would be ink and the two clusters would merge. 52 rows (52 to 103) sit inside the 56-row toolbar row with the mock's 2-row insets, and the shifted ink tops (`- SHORTER / 2`) are unchanged by it. — Cost if wrong: if a cluster still merges, the executor measures and records it under these notes; the tolerances never widen.
- **E11. Task 8 Step 2 keeps the guide's paragraph whole.** The replacement goes where the old sentence was; the remaining sentences of that paragraph ("A native-titled macOS frame's appearance follows the theme; …" to its end) follow the `TitleInset` sentence as their own paragraph. — Why: the plan's text ends in a list, and Markdown would otherwise run the next sentence into the last bullet. — Cost if wrong: a cosmetic re-wrap.
- **E12. Two spec phrases Task 8 Step 1 replaces wrap across lines:** 16.1 item 4's "the fallback combination (native title, window menus, default kit)," (`:1741-1742`) and 16.2's "the macOS in-window menu bar and native title, and Windows and Linux NATIVE with the caption colour;" (`:1809-1810`). Edit them across the wrap and re-wrap at 120 columns. — Why: a single-line search would miss them. — Cost if wrong: the Step 1 check's counts expose a missed edit.
- **E13. The relayed request "run the 6-second traffic-light test now" is satisfied by the recorded checkpoint** (`2060c1ae9`, run with the user's yes). Execution launches no window, and GUI check 1 stays in the packaged app. — Why: AGENTS.md forbids unattended GUI launches, and the result is already in the plan. — Cost if wrong: the user asks for another run.
