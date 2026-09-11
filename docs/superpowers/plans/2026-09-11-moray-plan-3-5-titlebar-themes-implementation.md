# Moray Plan 3.5a — Title Bar and Themes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A native-behaving custom macOS title surface, coordinated live Atom-inspired dark/light themes, and the user-selected two-tone toolbar.
**Scope update:** The user selected toolbar study B during execution; Task 4 implements that choice in this same runnable deliverable.
**Architecture:** TerminalView accepts live palettes; app-owned ThemeController updates every owner and its retained panes; MacTitleBar uses the decorated JFrame's macOS full-content area.
**Tech Stack:** Java/JBR25, Gradle wrapper 9.7, FlatLaf/flatlaf-extras3.7, existing JUnit/AssertJ.
**Spec:** `docs/superpowers/specs/2026-09-11-moray-plan-3-5-titlebar-themes-design.md`. Parent: Phase 1 spec and Plan 3.5 roadmap.

## Global Constraints

- Java 25 on JetBrains Runtime; use ./gradlew and preserve pinned dependencies.
- moray-terminal never depends on moray-app; no public terminal method takes/returns JediTerm types.
- No interface without two real implementations, no plugin API or emulator abstraction.
- UI/theme/model mutations run on EDT; process launch remains off EDT.
- Preserve sessions, scrollback, selection/search, viewport, fonts, split ratios, zoom and shortcuts on theme changes.
- Native macOS traffic lights/menu/drag/resize/full screen remain native; no undecorated frame or synthetic controls.
- No GUI/benchmark launches by agents. Headless Swing/BufferedImage and PTY tests are allowed.
- No config parser, persistence, automatic appearance selection, packaging or SSH. Toolbar scope is the user-selected study B treatment only.
- Source hygiene per AGENTS.md; commits end with Co-Authored-By trailer. Work on codex/plan-3-5-chrome-themes.

### Task 1: Live terminal palettes

**Files:** Modify terminal Palette.java and TerminalView.java; add TerminalPaletteTest.java. All under moray-terminal/src/{main,test}/java/dev/moray/terminal.
**Consumes:** Current TerminalSession/snapshots/TerminalPainter, FakeConnector/Await.
**Produces:** `public Palette TerminalView.palette()`, `public void TerminalView.setPalette(Palette)`, `public static Palette Palette.morayLight()`; update morayDark values as spec.

- [ ] **Step 1: Add failing rendering/state regressions.** Use real FakeConnector session, EDT and BufferedImage. Test changed background/default/indexed colors vs retained explicit RGB; call font controls after switch to catch startup-palette reuse. Capture content, columns/rows, current selection, find result and font size around switch; they remain. A representative API assertion:
```java
SwingUtilities.invokeAndWait(() -> {
    view.setPalette(Palette.morayLight());
    assertThat(view.palette()).isEqualTo(Palette.morayLight());
    assertThat(view.getBackground()).isEqualTo(Palette.morayLight().background());
});
```
Feed indexed and truecolor full-block glyphs for unambiguous pixels; repeat existing TerminalAppearanceTest style. Verify selection/search/dim overlay colors use new palette and null input fails atomically. Run `./gradlew :moray-terminal:test --tests '*TerminalPaletteTest'`; record expected RED missing API or stale rendered colors.
- [ ] **Step 2: Implement current palette and built-ins.** Keep immutable startup options for fonts/input, add mutable `private Palette palette;`. Make search color fields mutable. All palette uses route through the current value. Setter body follows:
```java
Palette next = Objects.requireNonNull(value, "palette");
if (next.equals(palette)) return;
palette = next;
painter = new TerminalPainter(fonts, next);
setBackground(next.background());
Color yellow = next.ansi().get(3);
matchColor = CellStyle.blend(yellow, next.background(), 0.7f);
currentMatchColor = CellStyle.blend(yellow, next.background(), 0.35f);
repaint();
```
Use shared initialization helper if it simplifies construction; do not invoke half-initialized view state. Add null/entry validation to Palette and exact colors from spec. No resize, buffer mutation or search reset in setPalette.
- [ ] **Step 3: Run covering/module checks and commit.** `./gradlew :moray-terminal:check`; exact XML counts, RED/GREEN, lifecycle contracts and no-public-JediTerm check in task report. Commit only task source/tests.

### Task 2: Coordinated application themes

**Files:** Create app BuiltinTheme.java, ThemeController.java, theme resource properties and ThemeControllerTest.java; modify Main, MorayApplication, TerminalWindow, WindowContent, WindowChrome, TerminalPane, TerminalTab as needed for ownership. Update WindowChromeTest for shared theme ownership.
**Consumes:** Task1 palette API. Existing TerminalTab.panes(), Swing owner lifecycle.
**Produces:** Concrete `ThemeController` with `BuiltinTheme current()`, `void select(BuiltinTheme)`, `void register(WindowContent)`, `void unregister(WindowContent)`; `BuiltinTheme` DARK/LIGHT, `String id()`, `String label()`, `Palette palette()`. `WindowContent.applyTheme(BuiltinTheme)` traverses every tab/pane; package callback `Consumer<BuiltinTheme> onThemeChanged` lets Task3 update native properties/header. Controller registration/selection/close on EDT.

- [ ] **Step 1: Add failing actual-owner regressions.** Two headless WindowContent owners share controller. Use controlled launch queue and real test PTYs. Change theme with menu Actions, then assert current/all ready-view palettes; launch queued pane after switching and assert latest palette. Select hidden tab and zoom a split to ensure detached views update. Retain session identity/font/find/tab/focus/model ratio values; closed owners unregister. Verify new owner inherits current theme, and native text-field shortcuts remain unchanged. Run `./gradlew :moray-app:test --tests '*ThemeControllerTest'` and record RED.
- [ ] **Step 2: Add theme resources and ownership.** Register custom defaults before FlatLaf setup:
```java
FlatLaf.registerCustomDefaultsSource("dev.moray.app.themes");
boolean installed = theme == BuiltinTheme.LIGHT ? FlatLightLaf.setup() : FlatDarkLaf.setup();
if (!installed) throw new IllegalStateException("Could not apply theme");
```
Apply selected theme only after successful setup, report failure through existing owner error path and retain previous state. Do not overwrite preferences before installation. Resource base uses `Component.arc=6`, restrained borders and application color keys `Moray.titleBackground`, `Moray.titleForeground`, `Moray.titleInactiveForeground`. Dark resource starts:
```properties
@background = #282c34
@foreground = #abb2bf
@accentColor = #61afef
Moray.titleBackground = #21252b
Moray.titleForeground = #abb2bf
Moray.titleInactiveForeground = #8b929f
ToolBar.background = #21252b
TabbedPane.background = #21252b
TabbedPane.selectedBackground = #282c34
```
Light counterpart uses spec light values. Define relevant menu/popup/find/status/tab/focus/hover/disabled defaults using actual FlatLaf3.7 keys. Assert app/terminal backgrounds align and important text contrast (4.5:1 normal,3:1 inactive/disabled labels as design target). Iterate theme-specific properties rather than accumulating global overrides.
- [ ] **Step 3: Integrate all retained panes and preserve layout.** Production app owns one controller and passes it into owners. Register/unregister actual content; late pane-ready callback applies current palette. Recursively update UI delegates for hidden/zoomed trees explicitly; preserve/restoresplitratios around delegate changes without rebuilding sessions or losing find state. OnThemeChanged callback is initialized no-op, cleared onclose. Menus reflect controller current selection. Keep standalone headless constructor compatibility if needed with private controller, but cross-window tests share one.
- [ ] **Step 4: Verify and commit.** Run `./gradlew :moray-app:check` plus exact focused regressions after fixes. Report APIs, resources, live-update/cleanup behavior and exact test counts. No GUI. Commit source/resources/tests; task review before next task.

### Task 3: Native macOS custom title surface

**Files:** Create app MacTitleBar.java and MacTitleBarTest.java; modify TerminalWindow, WindowContent and Main; extend theme integration tests if needed.
**Consumes:** ThemeController and WindowContent.onThemeChanged from Task2; existing title/active/minimum callbacks. Public FlatLaf client-property constants.
**Produces:** A per-window MacTitleBar lightweight panel and static/package configuration helper callable on headless JRootPane; native window owns installation/removal. No terminal API change.

- [ ] **Step 1: Add failing root/panel tests.** Inject supported-platform boolean into package helper (production uses SystemInfo.isMacFullWindowContentSupported). Verify root properties, correct per-theme appearance, native title metadata retained, other-platform root unchanged. Set synthetic bounds rectangles to verify traffic-light space/minimumheight/symmetrictitleclipping, then change/fullscreenzero bounds and verify relayout. Long titles do not increase minwidth. Active/inactive theme changes update label/background. Disposal removes installed bounds listener/callback. Run `./gradlew :moray-app:test --tests '*MacTitleBarTest'` and record RED.
- [ ] **Step 2: Configure decorated native frame before pack.** The mac-supported path sets:
```java
root.putClientProperty("apple.awt.fullWindowContent", true);
root.putClientProperty("apple.awt.transparentTitleBar", true);
root.putClientProperty("apple.awt.windowTitleVisible", false);
root.putClientProperty("apple.awt.windowAppearance",
    theme == BuiltinTheme.LIGHT ? "NSAppearanceNameAqua" : "NSAppearanceNameDarkAqua");
```
Main sets startup system appearance before EDT/AWT initialization. Keep frame.setTitle for OSmetadata; forward same bounded display title to panel. Use FlatLaf FULL_WINDOW_CONTENT_BUTTONS_BOUNDS listener and pinned68×28 fallback beforeboundsavailable; account for scale via logical dimensions/UIScale, avoid double-scalingreportedbounds. Follow spec for 28-min height and centered title. Header must integrate above toolbar, independentof itsvisibility, and triggerexistingnative-minimum recalculation. No synthetic buttons/windowdraglisteners. Theme changes use onThemeChanged; activechanges followWindowAdapter. Otherplatforms keepnoheader andexistingdecorations.
- [ ] **Step 3: Verify full integration and create reviewable preview.** Run `./gradlew check --rerun-tasks`, exact XML totals, sourcehygiene and gitdiffcheck. Render the actual header+WindowContent in BufferedImages headlessly for dark/light previews (native buttons absent in headless preview, clearly label limitation; no JFrame/loginGUI). Use real short-lived headless test PTY only if necessary, alwaysclose. Report previewpaths and remaining user-native checks. Commit source/tests/resources and report; no GUI/benchmark.

### Task 4: User-selected two-tone colored toolbar

**Files:** Modify AppIcons.java, WindowChrome.java, theme resources, packaged SVG icons/SOURCE.txt and AppIconsTest.java; add toolbar theme/mode regressions in WindowChromeTest.java as needed.
**Consumes:** Task2 coordinated theme updates, existing seven ActionIds and icon names; approved study B and spec color pairs.
**Produces:** 28px two-tone toolbar icons matching both themes, with unchanged shared-action routing/modes.

- [ ] **Step 1: Add failing rendered-resource regressions.** Paint every real icon in dark/light into BufferedImage; check multiple opacity/color regions and contrasting per-theme strokes, not a uniform gray output. Verify no missing assets and accessible labels, modes, disabled Settings/Reload, Split behavior stay intact. Run `./gradlew :moray-app:test --tests '*AppIconsTest' --tests '*WindowChromeTest'` and record RED for existing20px/grayicons.
- [ ] **Step 2: Adapt bundled SVGs to approved study B.** Preserve source Tabler paths, add soft rounded field behind geometry and translucent enclosed-face fill where appropriate. Example SVG shape order:
```xml
<rect x="1" y="1" width="22" height="22" rx="5"
      fill="#61afef" fill-opacity="0.18" stroke="none"/>
```
Keep original foreground geometry after the backdrop. Replace hardcoded legacygray with theme-aware peractioncolor via FlatSVGIcon per-instance ColorFilter (verify pinned3.7 API) or separate packaged light/dark assets if simpler; avoid global filters that recolor unrelatedicons. Use full MITlicense and SOURCEchangeannotation. Set icon dimensions28×28 and retainbuttonlabelbelow. Apply normalFlatLafhover/disabled states and smallconsistent margins/spacing fromstudy. Theme updates must refresh icons immediately for allwindows; rebuilding toolbar must not duplicate listeners or remove actiontooltips/accessibility.
- [ ] **Step 3: Verify integration and commit.** Run coveringappchecks; report renderedicon evidence, all sevenassets, license and live-switch/modes tests. Fullfinalcheck belongs to root afterreview. Commit only tasksource/resources/tests. Update actual Swing dark/light preview if existingtask3harnesscanbereused withoutGUI.

## Root acceptance

- [ ] All task reviews and final whole-branch review approved; fix rounds as required.
- [ ] Full final headless check passes; count skips and preserve warnings/limitations.
- [ ] Update STATUS/README/roadmap for implemented title/themes and selected two-tone toolbar; record native acceptance pending.
- [ ] User native title-bar/light-dark checks, including fullscreen/scaling/drag and screen menus.
- [x] Toolbar alternatives shown; user selected B (fuller, two-tone colored icons).
- [ ] Selected toolbar treatment implemented, reviewed and included in final verification.
