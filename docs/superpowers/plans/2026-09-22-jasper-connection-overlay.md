# Connection Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Native execution was already requested; finish with one independent review.

**Status:** Complete, executed natively. Full check/installDist passed: 1,634 tests, 1,631 passed,
three expected skips; Remote 70/70. Default/18-point headless renders inspected. One independent
review and fix pass completed with no remaining findings. UI typography was separately approved
and implemented on this branch. Implementation refinements: shared platform root-input marker
avoids a workspace/windows package dependency; container-order focus traversal supports headless
roots; scrollpane border insets and validation-root propagation preserve natural layout; native menu
callbacks honor overlay containment. No merge, push or native GUI launch.

**Goal:** Present Remote connection progress in the center of its owning app window, with explicit Cancel, without creating a native progress window.

**Architecture:** Extend the existing SDK Windows facade and AuxiliarySurface lifetime with an overlay kind. A headless-testable Swing host mounts in the owning root pane. Remote retains its existing connection, retry, cancellation and prepared-shell ownership.

**Tech Stack:** Java 25 / JBR, Swing, existing Gradle wrapper and JUnit fixtures.

**Spec:** `docs/superpowers/specs/2026-09-22-jasper-connection-overlay-design.md` (approved, including Cancel).

## Global Constraints

- SDK-only plugin dependencies; app SDK references remain inside app.plugins.
- No native progress window, title bar, dragging, resizing, Escape or outside-click dismissal.
- Center horizontally and vertically in the owning window's layered pane, including resize and content changes.
- Explicit Cancel aborts and dismisses; failure offers Retry/Close; success dismisses.
- Required Vault and host-key native prompts remain usable above the overlay.
- At most one overlay per terminal window, including requests from different plugins.
- Owner close and plugin stop release the attempt, UI listeners and input interception.
- Tabs/splits are created only after the shell is ready; reconnect retains its original pane.
- UI fonts follow the app-wide typography setting. No GUI launches, merge or push.

## Review Focus

- Keyboard routing must block owner shortcuts/terminal input while permitting controls inside the overlay and separate native prompts.
- A second connection must focus the first attempt, without starting another network request or orphaning a shell.
- A window can close before a created overlay is shown; no stale root or dispatcher may survive.
- Large fonts, tiny windows and retry error text must preserve bounds and reachable actions.
- Closing after success must not restore focus to the old pane over the newly connected pane.

---

### Task 1: SDK and owned overlay lifetime

**Files:**
- Create `jasper-sdk/src/main/java/dev/jasper/sdk/ui/OverlaySpec.java`.
- Modify `Windows.java`, `PluginDialog.java`, `WindowSurface.java` in that package.
- Modify `jasper-app/src/main/java/dev/jasper/app/windows/AuxiliarySurface.java`, `AuxiliaryWindows.java`.
- Modify `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java` and `HostedTerminals.java` as needed for validated owner access.
- Modify `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java`, `FakePluginHost.java` and terminal-close integration.
- Modify SDK version and Remote SDK requirement to 0.7.3.
- Test `AuxiliaryWindowsTest`, `FakePanelsAndWindowsTest` and the app/testkit shared contract suite.

**Interfaces:**
- Produces `Windows.overlay(OverlaySpec): PluginDialog` and `AuxiliaryWindows.overlay(String, UUID): AuxiliarySurface`.
- Overlay creation requires an open terminal owner from the same host; show is nonblocking.
- `AuxiliaryWindows.closeOwned(UUID)` closes terminal-owned surfaces even before show.

- [x] Add regression coverage for singleton ownership, foreign/closed owners, context stop, owner close before show, idempotent close and reuse after close.

```java
@Test void onlyOneOverlayCanOwnATerminalWindow() {
    UUID owner = UUID.randomUUID();
    var first = windows.overlay("Connecting", owner);
    assertThat(first.kind()).isEqualTo(AuxiliarySurface.Kind.OVERLAY);
    assertThatIllegalStateException().isThrownBy(() -> windows.overlay("Another", owner));
    windows.closeOwned(owner);
    assertThat(first.closed()).isTrue();
    assertThat(windows.overlay("Retry", owner).closed()).isFalse();
}
```

- [x] Run the affected tests and confirm failure because the overlay API is absent.
- [x] Implement the SDK value and method, including Javadoc thread, owner and duplicate constraints.

```java
public record OverlaySpec(String title, WindowHandle owner) {
    public OverlaySpec {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("An overlay needs a title");
        Objects.requireNonNull(owner, "owner");
    }
}
```

```java
public AuxiliarySurface overlay(String title, UUID ownerWindow) {
    Objects.requireNonNull(ownerWindow);
    if (open.stream().anyMatch(surface -> surface.kind() == AuxiliarySurface.Kind.OVERLAY
            && surface.ownerWindow().filter(ownerWindow::equals).isPresent()))
        throw new IllegalStateException("This window already has an overlay");
    return track(new AuxiliarySurface("", title, AuxiliarySurface.Kind.OVERLAY, false,
        new Dimension(1, 1), ownerWindow, null, shells));
}
public void closeOwned(UUID ownerWindow) {
    for (AuxiliarySurface surface : List.copyOf(open))
        if (surface.ownerWindow().filter(ownerWindow::equals).isPresent()) surface.close();
}
```

Wire terminal removal to `closeOwned`. HostedUi validates handles against its existing terminal registry before creating the surface and adds the surface to plugin cleanup. The fake uses host-wide owner reservations (not per-plugin reservations), releases them on close, and mirrors terminal and context cleanup. Keep native dialog behavior intact.

- [x] Run SDK, testkit and affected app lifecycle/contract tests. Commit with Co-Authored-By trailer.

### Task 2: Centered root-pane host and input containment

**Files:**
- Create `jasper-app/src/main/java/dev/jasper/app/windows/WindowOverlay.java` and its headless test.
- Modify `NativeShells.java` to route OVERLAY to this host instead of JFrame/JDialog.
- Modify the workspace keyboard entry point to honor root-level overlay containment before palette shortcuts.

**Interfaces:**
- Consumes `AuxiliarySurface.Kind.OVERLAY` and its content holder.
- Produces a shell whose show/front focus content, dispose detaches it, title updates accessibility, bounds report card bounds and file chooser uses the owner window.
- `WindowOverlay(JRootPane, JComponent)` owns its resize/focus/key listeners; `show()`, `focus()`, `close()` are EDT-confined and idempotent.

- [x] Add headless tests for exact center, resize/content/font relayout, tiny dimensions, outside clicks and Escape, keyboard containment, restoration only when overlay owned focus, and repeated show/close listener counts.

```java
@Test void centersAndClampsToTheAvailableWindow() {
    var root = new JRootPane();
    root.getLayeredPane().setSize(800, 600);
    var card = new JPanel(); card.setPreferredSize(new Dimension(400, 190));
    var overlay = new WindowOverlay(root, card);
    overlay.show(); root.getLayeredPane().doLayout();
    assertThat(card.getBounds()).isEqualTo(new Rectangle(200, 205, 400, 190));
    root.getLayeredPane().setSize(240, 140);
    overlay.layout();
    assertThat(card.getBounds().x).isGreaterThanOrEqualTo(0);
    assertThat(card.getBounds().y).isGreaterThanOrEqualTo(0);
    assertThat(card.getBounds().getMaxX()).isLessThanOrEqualTo(240);
    assertThat(card.getBounds().getMaxY()).isLessThanOrEqualTo(140);
    overlay.close(); overlay.close();
    assertThat(card.getParent()).isNull();
}
```

- [x] Confirm failure, then implement the layered host with null layout, a dimmed backdrop and the card bounds calculation below. Revalidate layout when the content changes. Wrap constrained content in a scroll container so Cancel/Retry remain reachable at large UI sizes.

```java
static Rectangle centered(Dimension preferred, Dimension available) {
    int width = Math.min(Math.max(0, preferred.width), Math.max(0, available.width));
    int height = Math.min(Math.max(0, preferred.height), Math.max(0, available.height));
    return new Rectangle((available.width - width) / 2, (available.height - height) / 2, width, height);
}
```

Use a layer above the palette. Swallow backdrop mouse/motion/wheel events without dismissal. Keep a focus cycle inside the content. Intercept owner keyboard shortcuts before the existing palette dispatcher; allow events belonging to a separate native prompt. Escape is consumed. Save prior focus on first show and restore it only when closing while the overlay owns focus; a successful handoff to a new pane must keep that pane focused. Remove root properties, listeners and dispatchers on close; never retain them globally after owner disposal. Test native prompt exemption via the event-source containment decision, without opening a GUI.

- [x] Route NativeShells overlay creation through the owner root pane and share existing file-picker ownership. Refresh overlay content on theme/font changes via the owning root; do not add an extra native window to the `natives` map.
- [x] Run headless overlay, window, keyboard and architecture tests. Commit with Co-Authored-By trailer.

### Task 3: Remote integration and acceptance

**Files:**
- Modify `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java` and `ui/ConnectionPanel.java`.
- Modify Remote connection lifecycle tests and SDK contract expectations.
- Update `docs/remote.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `jasper-sdk/README.md`, `docs/STATUS.md`.

**Interfaces:**
- Consumes `Windows.overlay(new OverlaySpec(title, window))`.
- Retains `ConnectAttempt` cancellation, retries, prepared-shell delivery and reconnect callbacks.

- [x] Extend existing connection tests to assert overlay surface, no early tab, failure/retry, Cancel during pending auth, owner/plugin stop, same-window duplicate request and successful handoff focus.
- [x] Run Remote tests and observe the new overlay expectation fail.
- [x] Replace only progress dialog creation:

```java
dialog = context.windows().overlay(new OverlaySpec("Connecting to " + host.name(), window));
```

Before creating any tab/split/reconnect attempt, find an existing attempt with the same window id; bring it forward and return without starting another shell request. Keep host-key, Vault, edit and import dialogs native. Report any rejected overlay reservation through existing notices without leaving an attempt in the set. Cancel remains explicit; no window decorations or automatic dismissal paths are added.

- [x] Run Remote tests, inspect an app-themed headless overlay render at default and enlarged UI fonts, then run `./gradlew check :jasper-app:installDist` serially.
- [x] Record XML test counts, limitations and native user acceptance steps in STATUS. Commit with Co-Authored-By trailer.
- [x] Obtain one independent whole-change review; address findings and rerun affected checks. Do not merge, push or launch the GUI.
