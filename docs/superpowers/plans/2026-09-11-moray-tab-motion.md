# Compact tabs and motion implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement task-by-task.

**Goal:** Configurable compact title tabs, quick entry/underline animation, and requested platform tab shortcuts.
**Architecture:** Extend existing WindowContent appearance and KeyBindings action engine; add presentation interpolation inside WindowTabs over unchanged tab/session ownership.
**Tech Stack:** Java25/JBR, Swing/FlatLaf, JUnit/AssertJ.
**Spec:** docs/superpowers/specs/2026-09-11-moray-tab-motion-design.md

## Global Constraints
- UI/model operations stay on EDT, shell launch off EDT. No GUI/benchmark by agents.
- No new dependency, emulator API, plugin/config framework, public JediTerm leak or terminal-to-app dependency.
- Work on codex/mock-ui with coauthor trailers. Native acceptance remains user-run.
- Same height drives tabs, Swing title row and native JBR title height. Default38 logical pixels, valid28–72, current-window/session-only.
- Presentation animations must not defer selection, focus, actions or session launch; no idle timer.

### Task 1: Configurable height and requested tab shortcut defaults
**Files:** WindowContent.java, WindowChrome.java, WindowTabs.java (height only), MacTitleBar.java, KeyBindings.java, ActionId.java if needed; existing KeyBindingsTest/MacTitleBarTest/WindowTabsTest/MockUiTest and new focused height tests under moray-app/src/test/java/dev/moray/app.
**Consumes:** Existing actions, theme callback pattern, native attachment/minimum propagation.
**Produces:** WindowContent.tabHeight():int and setTabHeight(int), default38; WindowTabs dimensions and MacTitleBar native/swing geometry derive from it; live numeric View control. Existing signatures remain compatible.
- [x] Write failing tests for38px initial geometry; setter changes title/strip height together, preserves tab/session/theme/font state; invalid values rejected; menu/action uses current owner height. Use actual headless root layout. Example:
```java
owner.setTabHeight(44);
MockUiTest.layoutTree(root);
assertThat(owner.windowTabs().getHeight()).isEqualTo(44);
assertThat(header.getHeight()).isEqualTo(44);
assertThatThrownBy(() -> owner.setTabHeight(27)).isInstanceOf(IllegalArgumentException.class);
```
- [x] Add failing platform shortcut regressions for Ctrl+2, Ctrl+Shift+[ / ] and MacMeta equivalents; brace overrides normalize and collisions still reject. Test real dispatch selecting tabs, wrapping and absent tab. Example:
```java
assertThat(KeyBindings.parse("ctrl+{", false)).contains(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
```
Run focusedtests RED; change obsolete54 default assertions intentionally.
- [x] Implement validated current-window height in WindowContent, update both surfaces through an explicit lifecycle-cleared geometry callback or property change listener, revalidate root/minimum and native height. View numeric dialog JSpinner28..72, apply onlyonOK, default/reset38. Preserve themes. WindowTabs uses UIScale.scale(owner.tabHeight()). MacTitleBar uses same height and immediately updates attachednativebar onlivechange. Set callback cleanup onclose. No persistent settings subsystem.
- [x] Extend defaults with platform-aware strings for tab actions only; normalize brace tokens to bracket keycodes plusSHIFT, retain othercmd defaults/override behavior. Use explicit ActionId.defaultBinding(boolean macOs) if useful; no ordinal classification beyond existing numbered actions. Existing shared Actions/root bindings automatically receive newstrokes. Run coveringtests, update README and report RED/GREEN; commit withcoauthor.

### Task 2: Tab entry and underline animation
**Files:** WindowTabs.java, small package-private TabMotion.java if interpolation state warrants separation; WindowContent close lifecycle if needed; WindowTabsTest.java/new TabMotionTest.java, MockUiPreview.java and comparison docs as needed.
**Consumes:** owner.tabHeight(), persistent Entry model, real selection and existing overflow layout fromTask1.
**Produces:** Visible180ms newtab/underline motion with shared easedovershoot, no idle or disposedtimer, reproducible deterministictests.
- [x] Write failing behavior/render tests using actual WindowTabs and explicit elapsed-time advancement. Cover entry midway/final bounds, underline old/intermediate/new positions, rapidretarget continuity, metadata refresh not restarting, overflow/removal/resize settling and timercompletion/disposal. Inject monotonic clock via package-private constructor or tick method; normal default uses System.nanoTime. Do not sleep. Existing immediate-selection tests retained.
```java
owner.selectTab(first);
// Advance the real strip's interpolation to a known elapsed instant.
assertThat(owner.currentTab()).isSameAs(first);
// Assert rendered intermediate underline differs from both endpoint positions,
// then exactly reaches the selected tab at duration and timer stops.
```
- [x] Implement single timer16ms onEDT withduration180ms; track shownentrywidth/targetwidth and underline rectangle. Firstlayout settles; newmembership triggersentrymotion afterinitiallayout. Use shared slow-fast-slow curve withsmallovershoot. A suitable piecewise curve uses smoothstep to1.035 through82% then smoothstep settles to1:
```java
double smooth(double t) { return t * t * (3 - 2 * t); }
double ease(double t) { return t < .82 ? 1.035 * smooth(t / .82) : 1.035 - .035 * smooth((t - .82) / .18); }
```
Clamp elapsed0..1; keep entrywidth nonnegative/withinclip. Capture renderedcurrentstate before retargeting. Draw one shared underline afterchildpaint (remove perEntryunderline) so movementcrossestabs; cliptoavailabletabregion andretainsemanticcolor. Entrybuttons retainrealhitboxes. Layoutretainsplus/navigation andactivevisibility. Resize/reorder/close settleorretarget coherently; terminaloutput refreshdoesnot resetmotion. Stop timeronfinish/removeNotify/close; reattachsettles. No manualnativehit-testing/drag or terminalrendererchanges.
- [x] Run focused tests RED/GREEN, fullcheck and sourcehygiene, regenerate actualheadlesspreviews atsettledstate, update comparison toexplain screenshot54px geometry supersededby38px. Add usermanual checks forquickmotion, keyboardselection andheight; commit/report.

## Root acceptance
- [ ] Separate reviews aftereach task; finalwholebranch review overthisfollowup range.
- [x] Fresh finalcheck/sourcehygiene; inspect actualpreviews; document nativeanimation acceptancepending.
- [x] STATUS/README/spec reflect followup and preserveddecisions. No mergerwithoutuserapproval.
