# Floating tool windows 3: SDK panel placement Implementation Plan

**Status: executed on 2026-10-26** (`ecb632dd8`, `bd2f01e92`, `a8f542f78`, then the documentation commit, then the
final review's fixes `5d3e0071b` and their documentation commit); see `docs/STATUS.md`.
Deviations from the plan's text: (1) Task 1's review fixes: `PanelHost.window()` and the class Javadoc no longer say the
host owns the main window or that the place is in it, and a panel's baseline is recorded after its factory returns
(`bd2f01e92`); (2) `PanelSite.notifyPlacement` has a round counter, so a listener whose own move started a newer
notification ends the round; (3) the Sample's body comes from `panelBody(boolean ownWindow)`, not `spec(placement)`; (4)
Task 3 also corrects the other statements that a tool window lives only in the main window, and the ones about a toggle
from a child window (`Panels` and `PanelFactory`'s Javadoc, guides 07, 08 and 13, plugin authoring); (5) the final
review: `placePanel` on a panel that is not open records the placement, which `openPanel` builds it with, also after
`closePanel` (the plan's Task 2 has it do nothing), and a child window's plain toggle of a detached tool window raises
its window.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A plugin's tool window panel can read where it is — its anchor and half, docked or in a window of its own (Float or Window) — and hear when that changes, so it can describe another layout in its own window.

**Architecture:** SDK 0.21.0 (unreleased) gains `PanelMode` and `PanelPlacement` and two `PanelHost` methods, as IntelliJ's `ToolWindow.getType()`/`getAnchor()` and `ToolWindowManagerListener.stateChanged`. The app's `PanelSite` carries a placement supplier and `notifyPlacement`; `WindowContributions` notifies from `DockState.Change.placed` (plan 1). The testkit's fake implements it with a driver; a contract test runs on both runtimes.

**Tech Stack:** Java 25, the Jasper SDK and testkit, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-10-23-jasper-floating-tool-windows-design.md` (section "SDK")

**Depends on:** plans 1 and 2 executed on the same branch.

## Global Constraints

- New SDK members are tagged `@since 0.21.0`; no version bump (0.21.0 is unreleased; `JasperSdk.VERSION` stays).
- SDK signatures use SDK types only (`verifySdkArchitecture`); Javadoc passes `-Xdoclint:all`.
- `PanelHost` methods are UI-thread only; handlers are contained (a throwing handler is logged, others still run);
  nothing is delivered after the instance closes.
- A placement notification fires once per effective change of slot or mode, after the panel's component is in its new
  place; never for visibility alone; never before the factory returns.
- `docs/sdk-reference.html` is regenerated with `./gradlew sdkReference`, never hand-edited.
- Plugin code describes layouts with `ViewSpec`s only (`PluginPresentationCheck`).
- Commit messages end with `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>`.

## Review Focus

1. A panel built while already detached (restored at start-up as Float) reads FLOAT from `placement()` inside its
   factory — Task 1.
2. A move within the same slot (reordering stripe buttons) sends nothing — Task 1.
3. A handler that calls `host.hide()` or moves the panel itself: no duplicate or stale notification — Task 1.
4. A handler registered after a change hears only later changes — Task 2 contract test.
5. The main window closing: `onClosed` runs and no placement notification follows — Task 2.

---

### Task 1: SDK types, `PanelHost`, and the app's notifications

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelMode.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelPlacement.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelHost.java`,
  `jasper-app/src/main/java/dev/jasper/app/contributions/PanelSite.java`,
  `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java` (`host`),
  `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java` (`build`, `docked`),
  `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java` (`PanelInstance` implements the two methods:
  placement from the spec, docked; Task 2 adds the driver),
  `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java` (its `PanelSite` construction compiles)
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/PanelPlacementTest.java` (new),
  `jasper-app/src/test/java/dev/jasper/app/workspace/PanelPlacementNotificationsTest.java` (new; real `WindowContent`,
  contributions, plan 1's recording shells)

**Interfaces:**
- Produces:
  ```java
  /** Where a tool window is: docked in the main window's dock, or in a window of its own. @since 0.21.0 */
  public enum PanelMode { DOCKED, FLOAT, WINDOW }
  /**
   * A tool window's place: its side and half (its stripe button's while it has a window of its own) and its mode.
   * @since 0.21.0
   */
  public record PanelPlacement(Anchor anchor, boolean split, PanelMode mode) {
      public PanelPlacement { Objects.requireNonNull(anchor, "anchor"); Objects.requireNonNull(mode, "mode"); }
      /** @return whether the panel has a window of its own (Float or Window) */
      public boolean ownWindow() { return mode != PanelMode.DOCKED; }
  }
  // PanelHost (both @since 0.21.0):
  PanelPlacement placement();
  Subscription onPlacement(Consumer<PanelPlacement> handler);
  ```
  `PanelHost.window()`'s Javadoc gains: the main window also while the panel has a window of its own.
  App: `PanelSite.record Placement(Slot slot, ToolWindowMode mode)`; a new constructor
  `PanelSite(UUID windowId, Runnable show, Runnable hide, BooleanSupplier visible, Consumer<PanelHeaderState> headerChanged,
  Supplier<Placement> placement)`, which `WindowContributions.build` and `AppContractTest` use; the existing 4- and
  5-argument constructors stay for the sites tests and fixtures build outside a dock (12 test files) and report
  `new Placement(Slot.LEFT_TOP, ToolWindowMode.DOCKED)`; `Placement placement()`;
  `Subscription onPlacement(Consumer<Placement>)`; `void notifyPlacement(Placement)` (contained, inert after close).

- [ ] **Step 1: Write the failing tests:**
  - `PanelPlacementTest`: `ownWindow()` false for DOCKED, true for FLOAT and WINDOW; nulls rejected.
  - `PanelPlacementNotificationsTest` (a plugin-style factory through `HostedUi` is not needed: use `model.addPanel` and
    read the `PanelSite` the factory receives):
    - `theFactoryReadsTheCurrentPlacement`: a panel restored FLOAT (saved state) → `site.placement()` inside the factory
      is `(its slot, FLOAT)`.
    - `movingToAnotherSlotNotifiesOnce`: Move to ▸ Right Top → one notification `(RIGHT_TOP, DOCKED)`; reordering within
      a slot → none.
    - `floatingAndDockingNotify`: View Mode ▸ Float → `(slot, FLOAT)`; ▸ Window → `(slot, WINDOW)`; ▸ Docked →
      `(slot, DOCKED)`; the frame is already where it belongs when each arrives (component's parent is the detached
      frame or the slot's frame).
    - `visibilityAloneNotifiesNothing`; `aHandlerThatMovesThePanelDoesNotGetAStaleSecondCall`;
      `nothingAfterClose`.
  - App adapter (`HostedUiTest` or the class that tests `HostedUi.panels()`): `PanelHost.placement()` maps `Slot` to
    `Anchor` + split and `ToolWindowMode` to `PanelMode`; `onPlacement` handlers run under containment.
- [ ] **Step 2: Run** `./gradlew :jasper-sdk:test --tests '*PanelPlacementTest' :jasper-app:test --tests '*PanelPlacementNotificationsTest'` — failures.
- [ ] **Step 3: Implement.** `WindowContributions.build` passes `() -> new PanelSite.Placement(dock.slot(id).orElse(entry.defaultSlot()), dock.mode(id))`;
  `PanelInstance` keeps the last placement told (as `notified` does for visibility); `docked(change)` notifies each id in
  `change.placed()` whose instance is built and whose current placement differs from the last told, after the
  visibility notifications. `HostedUi.host`: `placement()` converts
  (`Anchor.valueOf(slot.side().name())`, `slot.split()`, `PanelMode.valueOf(mode.name())`); `onPlacement` wraps with
  `containment.run(pluginId, "panel placement", …)` and `requireUi("onPlacement")`.
- [ ] **Step 4: Run** `./gradlew :jasper-sdk:check :jasper-app:test` — pass (`verifySdkReference` may fail until Task 3
  regenerates the page; if so run `./gradlew sdkReference` here and include the page).
- [ ] **Step 5: Commit** — `SDK: PanelHost.placement() and onPlacement (0.21.0)`.

---

### Task 2: The testkit's driver and the contract test

**Files:**
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java`,
  `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java`,
  `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`,
  `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`,
  `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`, `jasper-sdk-testkit/README.md`
- Test: the contract test runs in both `FakeContractTest` and `AppContractTest`.

**Interfaces:**
- Produces: `public void FakePluginHost.placePanel(String panelId, PanelPlacement placement)` — moves the open panel as
  the main window would: a different placement updates `placement()` and notifies once; the same is silent; nothing when
  the panel is not open. `ContractHarness`: `void placePanel(String panelId, PanelPlacement placement)` (UI thread).
  `AppContractTest` keeps a placement per open panel (default `(defaultSlot, DOCKED)`) and calls `site.notifyPlacement`.

- [ ] **Step 1: Write the failing contract test** `aPanelReadsItsPlacementAndHearsItChange` (after
  `aKitPanelsHeaderIsReplacedContainedAndIgnoredAfterClose`): register `test.alpha.hosts` at `Anchor.RIGHT` with
  `withSplit()`; open it; `host.placement()` = `(RIGHT, true, DOCKED)` inside and after the factory; a handler records;
  `placePanel(…, (RIGHT, true, FLOAT))` → one record, `placement().ownWindow()` true; the same again → none; a second
  handler that throws is contained and the first still hears `(LEFT, false, DOCKED)`; a handler added late hears only
  later changes; `closePanel` → a later `placePanel` delivers nothing.
- [ ] **Step 2: Run** `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests '*AppContractTest'` — failures.
- [ ] **Step 3: Implement** both harnesses and the fake; document `placePanel` in the testkit README's Contributions
  table.
- [ ] **Step 4: Run** the same — pass.
- [ ] **Step 5: Commit** — `Testkit: placePanel and the panel placement contract`.

---

### Task 3: The Sample plugin's example and the documentation

**Files:**
- Modify: `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java` (its panel describes another body when
  `ownWindow()`: on `onPlacement` it calls `view.update(spec(placement))`), its test, `docs/plugin-authoring.md`
  ("## Tool windows": the example block `<!-- example:pluginpanels -->` mirrors the Sample code; the `PanelHost`
  bullets gain `placement()`/`onPlacement`; drop the sentence that a drag reaches the panel only through
  `onVisibility`), `jasper-sdk/README.md` (the Tool windows row lists `PanelPlacement`, `PanelMode`),
  `tools/sdk-reference/guide/07-contributions.html` ("Tool windows and the rail": View Mode, own windows, placement),
  `docs/sdk-architecture.md` ("Panels, the rail and plugin dialogs"), `docs/sdk-reference.html` (regenerated),
  `docs/STATUS.md` (top entry), `migration.csv` rows for new test classes.
- [ ] **Step 1: Write the failing test** in the Sample plugin's test: with the testkit, `placePanel(…, FLOAT)` changes
  the view's body text; `DOCKED` changes it back.
- [ ] **Step 2: Run** `./gradlew :jasper-plugin-sample:test` — fails. **Step 3: Implement** the Sample change and the
  docs; run `./gradlew sdkReference`.
- [ ] **Step 4: Run** `./gradlew check --continue` and the external example
  (`./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`); record the
  counts in the STATUS entry.
- [ ] **Step 5: Commit** — `Docs: panel placement, and the Sample plugin's own-window layout`.
