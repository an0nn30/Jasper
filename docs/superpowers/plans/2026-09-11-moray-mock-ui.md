# Screenshot-matched Moray UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Match the user's screenshot with integrated native title tabs, horizontal toolbar and seamless terminal/status surface.
**Architecture:** Retain JTabbedPane selection/content ownership, add a shared WindowTabs surface, integrate JBR native title height, then align remaining chrome and render real Swing previews.
**Tech Stack:** JBR25, Gradle9.7, FlatLaf/extras3.7, jbr-api1.9.0, JUnit/AssertJ.
**Spec:** docs/superpowers/specs/2026-09-11-moray-mock-ui-design.md. Supersedes Plan3.5 visual geometry only.

## Global Constraints
- No public JediTerm types or terminal-to-app dependency; no new interfaces without two real implementations.
- All UI/model mutations on EDT; process launch off EDT; preserve sessions, selection/find, font overrides, zoom/split state and shortcuts.
- Native controls/drag/fullscreen remain native; no undecorated frame, synthetic traffic lights or manual window drag.
- No GUI/benchmark launches. Headless Swing and controlled PTY fixtures allowed; native acceptance remains user-run.
- Source hygiene per AGENTS; explicit coauthor trailers, isolated codex/mock-ui branch. No config/SSH/plugins scope.
- Reference geometry is2×; measured dark colors and layout in spec, light same geometry. Do not hardcode user session metadata in production.

### Task 1: Integrated title tabs and native title height
**Files:** Create WindowTabs.java and TerminalDeck.java under moray-app/src/main/java/dev/moray/app; modify WindowContent.java, MacTitleBar.java, TerminalWindow.java and moray-app/build.gradle.kts. Add WindowTabsTest.java and revise MacTitleBarTest.java. Add plain terminal-2.svg/x.svg/plus.svg assets with attribution as needed, isolated from Task2's toolbar assets.
**Consumes:** WindowContent.tabStrip(), selectTab(TerminalTab), closeTab(TerminalTab), reorderTab(int,int), action(NEW_TAB), update callbacks; protected FlatTabbedPaneUI.hideTabArea().
**Produces:** WindowContent.windowTabs(): WindowTabs; WindowTabs.refresh(), setActive(boolean); retained JTabbedPane deck with hidden header; MacTitleBar hosts WindowTabs on supportedMac. Native attachment method on MacTitleBar receives actual JFrame, used only by TerminalWindow; headless install remains testable.
- [ ] Write EDT failures for selection/close/middle-click/reorder against actual model, overflow/long labels, plus action, new54px title geometry/native insets and single visible header. Existing component identities must remain across refresh/theme. Example:
```java
owner.newTab(HOME);
TerminalTab selected=owner.currentTab();
owner.reorderTab(1,0);
assertThat(owner.currentTab()).isSameAs(selected);
owner.selectTheme(BuiltinTheme.LIGHT);
assertThat(owner.currentTab()).isSameAs(selected);
assertThat(owner.windowTabs().getPreferredSize().height).isEqualTo(54);
```
Run focused WindowTabsTest/MacTitleBarTest, record RED. Replace superseded symmetric28px centered-title expectations.
- [ ] Implement TerminalDeck updateUI with FlatTabbedPaneUI override:
```java
setUI(new FlatTabbedPaneUI() {
    @Override protected boolean hideTabArea() { return true; }
    @Override protected Insets getTabAreaInsets(int placement) { return new Insets(0,0,0,0); }
});
```
Use WRAP layout, no per-tab visual headers. Keep model APIs. WindowTabs uses actual controls (tab icon/title/close, plus, overflow navigation), identity-based entries and bounded sizing. Update WindowContent layout north container to WindowTabs then toolbar; refresh metadata without rebuilding controls per terminal output. MacTitleBar moves that same strip into54px native header with98px safeleftminimum and bounded trailing title, removes old duplicate header. Native titlemetadata `<title> — Moray` atthisboundary (keep Main.windowTitle existing contract). Accessible titles literal, HTMLdisabled.
- [ ] Add app dependency jbr-api1.9.0 and supported native attachment:
```java
if (JBR.isWindowDecorationsSupported()) {
    var decorations=JBR.getWindowDecorations();
    var title=decorations.createCustomTitleBar();
    title.setHeight(54);
    decorations.setCustomTitleBar(frame,title);
}
```
Set scaledlogicalheight based on actualheader, account nativeleft/rightinsets; attach/detach lifecycle, refresh bounds after peer/fullscreen/layout. Use verifiedpublicAPI; graceful root-property fallback. Interactive tab mouse listeners provide JBRclient hit testing; blank title has no mouse listeners. Retain native decoration minimum propagation. Verify pinned source for integration details rather than assumptions.
- [ ] Run appcheck, report exact counts/RED/GREEN and limits; commit task files withcoauthor. Taskreview beforeTask2.

### Task 2: Reference toolbar, terminal/status surface and visual verification
**Files:** Modify WindowChrome.java, AppIcons.java, WindowContent.java, TerminalPane.java, theme properties, original seven toolbar SVGs/SOURCE.txt; Palette.java background and directly affected palette tests. Create WindowStatusBar.java, MockUiTest.java and test-only MockUiPreview.java; add moray-app Gradle headless preview task. Update AppIconsTest/WindowChromeTest and any obsolete appearance assertions. Commit actual previews docs/design/mock-ui-{dark,light}.png and measured comparison report.
**Consumes:** Task1 WindowTabs54px and nativeheader/deck model; existing WindowContent actions/themes/statusmetadata and TerminalPane lifecycle.
**Produces:** Exact measured toolbar/status/content appearance and reproducible headless comparison artifacts; unchanged interaction contracts.
- [ ] Add failing actual-component/rendered tests for54px toolbar,16px outline icons, inline sentence-case labels, new-tab hint, separators/flexible rightalignment; toolbar mode/disabled/accessibility/action behavior. Status textleft/right splitandclip, backgroundcontinuity and terminal24px padding, appfont16/reset16. Example geometry assertions using actual layout:
```java
owner.setSize(958,958); layoutTree(owner);
assertThat(owner.toolbar().getHeight()).isEqualTo(54);
assertThat(owner.status().getBackground()).isEqualTo(owner.currentPane().view().palette().background());
```
Run focusedtests recordRED. Replace two-tone tests deliberately supersededbyuser with outline/live-neutraltheme tests.
- [ ] Implement horizontal toolbar with16px plain Tabler SVGs,8pxgap,12pxfont,54pxheight,12pxsidepadding,30pxbuttons. New tab roundedprimary background #3a404b, trailingCmd+T mutedhint; Splitdropdownchevron; rightanchoredSettings/Reload; separators. Set sentence-case display label independentlyofsharedActions. No disappearingcommands atnarrowwidth: compactbuttonsorhorizontaloverflow whilemenusremainfunctional. Icons-onlyhideshint/chevronlabelappropriately; Hiddenremovestoolbarheight only. Use small focused custombuttonpaint ifneededforsecondaryhint, notpaintedfakebuttons. PreservefullMIT/provenance.
- [ ] WindowStatusBar holds left/rightmetadata with0minwidth and30pxheight; greenrunningdot, slashseparators,10pxmutedtext. WindowContent.update passes shell/path/grid/runningstate directly, getText() compatibility for existingtest. TerminalPane uses empty24pxcontentinsets/samebackground, nofocusoutlinearoundsolepane; viewoptionscopydefaultswithfont16 soreset16, allotherterminaloptionssame. Applytheme updatespaddingbackgroundandpalette. Dark palettebackground #292c34 and themes per spec; lightlayoutretainedandcontrasting. Do not touch renderercoordinate/inputlogic.
- [ ] Create testfixture headless actualUI at958×958logical 2× raster. Render MacTitleBar+WindowContent and controlledPTYtestprompt, actual tabs/toolbar/status. Nativecontrolsnotfabricated; metadatafixtures onlyinpreview. Add explicit Gradletask:
```kotlin
tasks.register<JavaExec>("mockUiPreview") {
    dependsOn(tasks.testClasses)
    classpath=sourceSets["test"].runtimeClasspath
    mainClass="dev.moray.app.MockUiPreview"
    jvmArgs("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
}
```
Use deterministicclose/waitforPTYoutput, noappGUI. Comparegeometry/background samples toreference withICCnormalization. InspectPNGwithview_image and iterateactualcomponentsuntil reference match; report font/native exclusions and actual differences honestly. Run fullcheck oncefinal, sourcehygiene/diffchecks, commit/report.

## Root acceptance
- [ ] Task reviews and final whole-branch review complete, fixes verified.
- [ ] Fresh full headlesscheck and visualcomparison artifacts inspected; reportmeasurabledifferences.
- [ ] UpdateSTATUS/README/manualcheck fornewreference overridingearlierlayout, preserve decisions.
- [ ] User-run nativeacceptance/subjective pixelcomparison (no GUIlaunch byagents).
