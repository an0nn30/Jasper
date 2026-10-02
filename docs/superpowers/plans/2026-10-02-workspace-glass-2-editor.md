# Workspace Glass, Plan 2: Editor Glass — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Written 2026-10-02 on `claude/workspace-glass` (base `fcba0f46`); not started.

**Goal:** In a translucent ("glass") Jasper window, the editor plugin's text area and gutter are translucent like the terminal, through a new SDK 0.12.1 method that reports the opacity in effect behind a plugin component.

**Architecture:** The SDK gains `Appearance.workspaceOpacity(Component)` (default 1.0), and `onWorkspaceChanged` also reports changes of it. The host answers per window: a `GlassSource` (lookup + change subscription) built by `JasperApplication` from its `TerminalWindow`s is threaded into the plugin host like the theme supplier; `WindowContent` reports opacity changes and hosted content entering or leaving a translucent window. The editor's text column becomes a `GlassSurface` that fills the editor and gutter backgrounds once each with an unantialiased `Src` rectangle; the text area, viewports and gutter above it go transparent and non-opaque.

**Tech Stack:** Java 25 / Swing, RSyntaxTextArea 3.6.0, JUnit 5 + AssertJ, Gradle.

**Spec:** [`docs/superpowers/specs/2026-10-02-jasper-workspace-glass-design.md`](../specs/2026-10-02-jasper-workspace-glass-design.md) §3 "Editor" and the 2026-10-02 amendment. Read both.

**Deviation from the spec (recorded here and in `docs/STATUS.md` at the end):** the spec names `Appearance.workspaceOpacity()` with no argument. The opacity in effect is per window (each window has its own `WindowGlass`, which may refuse), and content moves between windows ("Open in Separate Window"), so the method takes the component: `workspaceOpacity(Component)`. A no-argument value could make the editor paint alpha in a window that stayed opaque, breaking the fail-opaque guarantee.

## Global Constraints

- Java 25, JBR 25; `./gradlew` only; tests headless; never launch the GUI or benchmarks.
- Work in `.worktrees/workspace-glass` on `claude/workspace-glass`; confirm `git branch --show-current` before each commit; commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- SDK and testkit stay JDK-only (`java.*`, `javax.*`, `jdk.*`, `dev.jasper.sdk.*`); every new public SDK member has complete JavaDoc with `@since 0.12.1` (doclint fails the build otherwise). SDK version becomes `0.12.1`; plugins keep `<0.13`.
- SDK types appear in the app only in `dev.jasper.app.plugins`; `dev.jasper.app.plugins` must not depend on `dev.jasper.app.workspace`.
- Fail-opaque: plugins see 1.0 for any component not in a translucent window (including detached components).
- Translucent in the editor: only the editor default background, the gutter background and the current-line highlight. Selection, caret, find bar, Vim bar, banner, loading/failure state and the host tab strip stay solid.
- Every translucent fill is an unantialiased rectangle with `AlphaComposite.Src` (Java2D renders antialiased or non-rectangular translucent `Src` fills in software, about 200 ms per frame). Each translucent pixel gets exactly one such fill; components above it are non-opaque and paint nothing there.
- Source hygiene per `AGENTS.md`.

---

### Task 1: SDK 0.12.1 `workspaceOpacity` and the host

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Appearance.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java:6`
- Modify: SDK tests asserting the version: `jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java:8`, `.../ui/AppearanceContractTest.java:21`, `.../files/FilesValuesTest.java:54`, `.../palette/PaletteValuesTest.java:70`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java`, `FakePluginContext.java` (anonymous `Appearance`, ~194-225)
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`, `PluginContractTest.java`
- Modify: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java` (harness)
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/GlassSource.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java` (~225-238, ~384-387), `PluginHost.java` (`Environment`, ~29-45), `HostedContext.java` (~85), `HostedUi.java` (constructors ~90-118, `appearance()` ~288-328)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (`mountContent` ~610, `unmountContent` ~633, `applyBackgroundOpacity` ~1067, `backgroundOpacity` ~1078)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (~70, ~286-300, ~435)
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java` (Environment ~97, harness ~297-300)
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceGlassTest.java`

**Interfaces:**
- Produces: `default double Appearance.workspaceOpacity(java.awt.Component component)` (SDK 0.12.1); `FakePluginHost.setWorkspaceOpacity(double)`; `ContractHarness.setWorkspaceOpacity(double)`; `dev.jasper.app.plugins.GlassSource(ToDoubleFunction<Component> opacity, Function<Runnable, Subscription> subscribe)` with `GlassSource.NONE`; `PluginRuntime.glass(GlassSource)`; `public void WindowContent.setGlassListener(Runnable)`; `public float WindowContent.backgroundOpacity()`.

- [ ] **Step 1: SDK method and version**

In `Appearance.java`, after `syntaxPalette()`, add:

```java
    /** How opaque the workspace behind {@code component} is: 1 unless the window holding it is translucent, in which
     * case paint the component's own background with this alpha (the window shows the desktop through it). Changes,
     * including the component moving to another window, are reported through {@link #onWorkspaceChanged}. UI thread only.
     * @param component a component of this plugin's, shown or not
     * @return the opacity in effect, from 0.1 to 1
     * @since 0.12.1
     */
    default double workspaceOpacity(java.awt.Component component) {
        java.util.Objects.requireNonNull(component, "component");
        return 1.0;
    }
```

Extend the `onWorkspaceChanged` JavaDoc first sentence to: "Reports workspace changes, including changes within the same variant and of {@link #workspaceOpacity}, on the UI thread." Set `JasperSdk.VERSION = "0.12.1"` and update the four version asserts to `"0.12.1"`.

- [ ] **Step 2: Testkit fake (TDD inside the testkit)**

`FakePluginHost`: add a field `private double workspaceOpacity = 1.0;`, and

```java
    /** Sets the opacity every component reports through {@code Appearance.workspaceOpacity} and announces it as a
     * workspace change on the next {@link #flush()} (no announcement when unchanged).
     * @param opacity from 0.1 to 1
     * @since 0.12.1
     */
    public void setWorkspaceOpacity(double opacity) {
        if (!(opacity >= 0.1 && opacity <= 1)) throw new IllegalArgumentException("opacity must be from 0.1 to 1");
        if (opacity == workspaceOpacity) return;
        workspaceOpacity = opacity;
        // announce exactly as setWorkspace does: queue the current workspace for the next flush
    }
    double workspaceOpacity() { return workspaceOpacity; }
```

Implement the announcement by reusing the existing workspace publishing path (`publishWorkspace` / the queue `setWorkspace` uses, `FakePluginHost.java` ~103-140) so handlers run on `flush()`, failures are contained and the current `Workspace` record is delivered; adjust `publishWorkspace`'s duplicate check so an opacity-only change is not skipped (for example compare a pair of workspace and opacity). In `FakePluginContext`'s `Appearance`, add:

```java
            @Override public double workspaceOpacity(java.awt.Component component) {
                host.requireAppearanceThread("workspaceOpacity");
                java.util.Objects.requireNonNull(component, "component");
                return host.workspaceOpacity();
            }
```

(use the existing thread-check helper's real name; `requireAppearanceThread` is at ~99).

- [ ] **Step 3: Contract case (fails until both harnesses implement it)**

`ContractHarness`: add

```java
    /** Sets the opacity every plugin component reports and announces it as a workspace change.
     * @param opacity from 0.1 to 1
     */
    void setWorkspaceOpacity(double opacity);
```

`PluginContractTest`: add a case modelled on `independentWorkspaceChangesExposeValuesInsideCallbacksAndRespectLifetime` (~521-560), same plugin start and `h.ui(...)` structure, asserting:
1. Before any change, `a.workspaceOpacity(new javax.swing.JPanel())` is `1.0` (inside `h.ui`), and `a.workspaceOpacity(null)` throws `NullPointerException`.
2. After registering `onWorkspaceChanged`, `h.setWorkspaceOpacity(0.8); h.flush();` delivers exactly one callback, inside which `a.workspaceOpacity(component)` is `0.8`; `onChanged` (chrome) is not called.
3. `h.setWorkspaceOpacity(0.8); h.flush();` again delivers nothing.
4. After closing the subscription, `h.setWorkspaceOpacity(1.0); h.flush();` delivers nothing.

Name it `workspaceOpacityStartsOpaqueAndChangesAreWorkspaceChanges`. In `FakeContractTest`'s harness implement `setWorkspaceOpacity` with `host.setWorkspaceOpacity(opacity)`.

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check` — expected PASS (fake). `:jasper-app:test --tests "*AppContractTest"` must fail to compile until Step 5.

- [ ] **Step 4: Host plumbing (TDD with `HostedUiTest`)**

Create `jasper-app/src/main/java/dev/jasper/app/plugins/GlassSource.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.lifecycle.Subscription;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * How translucent the window behind a plugin component is, for {@code Appearance.workspaceOpacity}. EDT only.
 *
 * @param opacity the opacity in effect behind a component; 1 when it is in no translucent window
 * @param subscribe registers a callback for any change of it: a window's opacity, or hosted content entering or
 *                  leaving a translucent window
 */
public record GlassSource(ToDoubleFunction<Component> opacity, Function<Runnable, Subscription> subscribe) {
    /** Every component opaque, nothing ever changes. */
    public static final GlassSource NONE = new GlassSource(component -> 1.0, listener -> new Subscription(() -> {}));

    public GlassSource { Objects.requireNonNull(opacity); Objects.requireNonNull(subscribe); }
}
```

Thread it like `appearance`:
- `PluginRuntime`: field `private GlassSource glass = GlassSource.NONE;` and
  `/** Supplies window opacity for plugins before they start. UI thread. @param source the app's per-window lookup */ public void glass(GlassSource source) { if (host != null) throw new IllegalStateException("Plugins have already started"); glass = Objects.requireNonNull(source); }`;
  pass `glass` as the new last argument of `new PluginHost.Environment(...)` (~387).
- `PluginHost.Environment`: add a last component `GlassSource glass`; the canonical-constructor callers that already pass `appearance, appearanceSubscriber` gain `glass`; keep the existing compatibility constructor by passing `GlassSource.NONE` (and add one compatibility constructor with the current 17 arguments that passes `GlassSource.NONE`, so tests constructing it with appearance compile unchanged).
- `HostedContext` (~85): pass `host.environment.glass()` as a new last `HostedUi` argument.
- `HostedUi`: add a field `private final GlassSource glass;`, a new last constructor parameter `GlassSource glass` on the long constructor (keep the existing long signature delegating with `GlassSource.NONE`, and the short one unchanged). In the long constructor set `this.glass = glass == null ? GlassSource.NONE : glass;` and, after the theme subscription, `var glassRegistration = this.glass.subscribe().apply(this::announceWorkspace); closers.add(glassRegistration::close);`. Extract the existing handler loop into

```java
    private void announceWorkspace() {
        for (var handler : List.copyOf(workspaceHandlers)) {
            if (workspaceHandlers.contains(handler) && open.getAsBoolean())
                containment.run(pluginId, "workspace appearance", () -> handler.accept(workspaceVariant()));
        }
    }
```

  and call it from the theme subscription where the loop was. In `appearance()` add

```java
            @Override public double workspaceOpacity(java.awt.Component component) {
                requireUi("workspaceOpacity");
                return glass.opacity().applyAsDouble(java.util.Objects.requireNonNull(component, "component"));
            }
```

`HostedUiTest`: add a test with a mutable fake `GlassSource` (a `double[]` value and a `List<Runnable>` of listeners; subscribe adds and the returned `Subscription` removes): `workspaceOpacity(component)` returns the value; running the listeners calls an `onWorkspaceChanged` handler once (and not `onChanged`); after the handler's subscription closes, it is not called; after the plugin closes (`open` false), not called. Use the existing appearance-enabled `HostedUi` construction in that test class (the ones passing a `ResolvedTheme` supplier) as the model.

- [ ] **Step 5: App harness and window wiring**

`AppContractTest`: build the Environment with a mutable `GlassSource` (value + listeners); `setWorkspaceOpacity(opacity)` sets the value and runs the listeners on the EDT (`onEdt(...)`), matching how its `setWorkspace` drives `WorkspaceAppearanceFixture`.

`WindowContent`:

```java
    private Runnable glassListener = () -> {};

    /** Runs when this window's background opacity changes, or hosted content enters or leaves it while translucent. */
    public void setGlassListener(Runnable listener) { glassListener = Objects.requireNonNull(listener); }
```

make `backgroundOpacity()` `public`; at the end of `applyBackgroundOpacity` (after `repaint()`, only on a real change) call `glassListener.run();`; at the end of the `try` in `mountContent` add `if (backgroundOpacity < 1f) glassListener.run();`; at the end of `unmountContent` (after `WorkspaceTheme.clear(...)` and `update()`, before the empty check) add `if (backgroundOpacity < 1f) glassListener.run();`.

`JasperApplication`:

```java
    private final List<Runnable> glassListeners = new ArrayList<>();

    /** The opacity in effect behind {@code component}: its terminal window's, or 1 outside one. */
    private double glassOpacity(java.awt.Component component) {
        for (TerminalWindow window : windows) if (window.owns(component)) return window.content().backgroundOpacity();
        return 1.0;
    }

    private void glassChanged() { for (Runnable listener : List.copyOf(glassListeners)) listener.run(); }
```

before `plugins.appearance(...)` (~435) add

```java
        plugins.glass(new dev.jasper.app.plugins.GlassSource(this::glassOpacity, listener -> {
            glassListeners.add(listener);
            return new dev.jasper.app.lifecycle.Subscription(() -> glassListeners.remove(listener));
        }));
```

and in `newWindow(...)` after `connectTerminals(...)` add `window.content().setGlassListener(this::glassChanged);`. (Use the imports already present in the file; add `java.util.ArrayList` only if missing.)

`WorkspaceGlassTest`: add a test that `setGlassListener` runs once per real `applyBackgroundOpacity` change (0.5 → 0.5 does not run it; 0.5 → 1 runs it).

- [ ] **Step 6: Verify and commit**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:test --tests "*AppContractTest" --tests "*HostedUiTest" --tests "*WorkspaceGlassTest" --tests "*VersionRangeTest"` and `./gradlew verifyApplicationArchitecture verifySdkArchitecture` (find the exact gate task names in the root `build.gradle.kts`). Expected: PASS.

```bash
git branch --show-current   # must print claude/workspace-glass
git add -A jasper-sdk jasper-sdk-testkit jasper-app/src
git commit -m "feat(sdk): report the workspace opacity behind a plugin component (SDK 0.12.1)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Editor glass

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/ui/GlassSurface.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTheme.java`, `EditorTab.java` (~40-50 fields, ~107 `text` panel, ~174-189 `apply`), `EditorWindow.java` (~233-242 `apply`)
- Modify: `plugins/editor/src/main/resources/plugin.toml` (`version = "0.7.0"`, `sdk = ">=0.12.1, <0.13"`)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorThemeTest.java`, `EditorTabTest.java`, `EditorWindowTest.java`

**Interfaces:**
- Consumes: `Appearance.workspaceOpacity(Component)`, `FakePluginHost.setWorkspaceOpacity(double)` (Task 1).
- Produces: `EditorTheme.apply(RSyntaxTextArea, RTextScrollPane, EditorPalette, SyntaxPalette, double opacity)`; `EditorTab.apply(EditorSettings, EditorTheme, EditorPalette, SyntaxPalette, double opacity)`; package-private `GlassSurface` with `glass(double, Color, Color)` and `opacity()`. The previous four-argument `apply` methods stay and delegate with `1.0`.

- [ ] **Step 1: Write the failing tests**

`EditorThemeTest`: a test that `apply(area, scroll, EDITOR, syntax(...), 0.5)` leaves `area`, `scroll`, `scroll.getViewport()`, `scroll.getRowHeader()`, `scroll.getGutter()` and every `JComponent` child of the gutter non-opaque; `area.getBackground().getAlpha()` and `gutter.getBackground().getAlpha()` are 0; `area.getCurrentLineHighlightColor()` has alpha 128 and the RGB of `EDITOR.currentLine()`; and that a following `apply(..., 1.0)` restores opaque components and the palette's opaque colours.

`EditorTabTest`: on the EDT build an untitled `EditorTab` as the existing tests do, apply with opacity `0.5` and the test palettes, `setSize(600, 400)`, lay it out (validate twice), paint into a `TYPE_INT_ARGB` image, and assert:
- a pixel inside the text area near its bottom right (well below the first line, so neither text nor the current-line highlight is there) has alpha 128 and the RGB of the editor background;
- a pixel inside the gutter near its bottom has alpha 128 and the RGB of the gutter colour;
- after `apply(..., 1.0)` the same text-area pixel has alpha 255.

`EditorWindowTest`: with the existing `FakePluginHost` setup, open a tab, then `host.setWorkspaceOpacity(0.7); host.flush();` and assert the tab's `GlassSurface` reports `opacity()` 0.7 (reach it the way the test reaches other tab internals; add a package-private `EditorTab.surface()` accessor if needed).

Run: `./gradlew :jasper-plugin-editor:test --tests "*EditorThemeTest" --tests "*EditorTabTest" --tests "*EditorWindowTest"` (check the editor project's Gradle path in `settings.gradle.kts`). Expected: compilation FAILS.

- [ ] **Step 2: `GlassSurface`**

```java
package dev.jasper.editor.ui;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * The editor's text column. In a translucent window it fills the editor background, and then the gutter's, once each
 * at the workspace opacity, replacing what lies beneath; the text area, viewports and gutter above it stay clear.
 * Java2D renders a translucent Src fill in software unless it is an unantialiased rectangle, so both are rectangles.
 */
final class GlassSurface extends JPanel {
    private final RTextScrollPane scroll;
    private double opacity = 1;
    private Color background = Color.BLACK, gutter = Color.BLACK;

    GlassSurface(RTextScrollPane scroll) {
        super(new BorderLayout());
        this.scroll = scroll;
    }

    /** Below 1 this surface paints the glass; at 1 it is an ordinary opaque panel. */
    void glass(double opacity, Color background, Color gutter) {
        this.opacity = opacity;
        this.background = background;
        this.gutter = gutter;
        setOpaque(opacity >= 1);
        repaint();
    }

    double opacity() { return opacity; }

    @Override protected void paintComponent(Graphics graphics) {
        if (opacity >= 1) { super.paintComponent(graphics); return; }
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setComposite(AlphaComposite.Src);
            g.setColor(translucent(background));
            g.fillRect(0, 0, getWidth(), getHeight());
            var header = scroll.getRowHeader();
            if (header != null && header.isVisible() && header.getParent() != null) {
                Rectangle bounds = SwingUtilities.convertRectangle(header.getParent(), header.getBounds(), this);
                g.setColor(translucent(gutter));
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        } finally {
            g.dispose();
        }
    }

    private Color translucent(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) Math.round(255 * opacity));
    }
}
```

- [ ] **Step 3: `EditorTheme` translucency**

Keep the four-argument `apply` delegating to a new five-argument `apply(..., double opacity)` that contains today's body with these changes (after the syntax scheme is set):

```java
        boolean glass = opacity < 1;
        Color background = glass ? CLEAR : fixed(editor.background());
        Color gutterBackground = glass ? CLEAR : fixed(editor.gutter());
        area.setBackground(background);
        area.setOpaque(!glass);
        area.setForeground(fixed(editor.foreground()));
        area.setCaretColor(fixed(editor.caret()));
        area.setSelectionColor(fixed(editor.selection()));
        area.setCurrentLineHighlightColor(glass ? translucent(editor.currentLine(), opacity) : fixed(editor.currentLine()));
        scroll.setOpaque(!glass);
        scroll.setBackground(background);
        scroll.putClientProperty("FlatLaf.style", Map.of("background", background));
        scroll.getViewport().setOpaque(!glass);
        scroll.getViewport().setBackground(background);
        if (scroll.getRowHeader() != null) {
            scroll.getRowHeader().setOpaque(!glass);
            scroll.getRowHeader().setBackground(gutterBackground);
        }
        Gutter gutter = scroll.getGutter();
        gutter.setOpaque(!glass);
        gutter.setBackground(gutterBackground);
        // Line numbers and fold indicators fill with the gutter background themselves; clear, they leave the glass.
        for (var child : gutter.getComponents()) if (child instanceof javax.swing.JComponent component) component.setOpaque(!glass);
        gutter.setBorderColor(fixed(editor.gutter()));
        gutter.setFoldBackground(gutterBackground);
        gutter.setLineNumberColor(fixed(editor.lineNumbers()));
```

with

```java
    private static final Color CLEAR = new Color(0, 0, 0, 0);

    private static Color translucent(Color color, double opacity) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) Math.round(255 * opacity));
    }
```

and JavaDoc on the five-argument method: "Below 1, {@code opacity} makes the editor and gutter backgrounds clear and their components non-opaque, so the enclosing {@link GlassSurface} paints the only glass; the current-line highlight takes that alpha."

- [ ] **Step 4: `EditorTab` and `EditorWindow`**

`EditorTab`: replace the local `var text = new JPanel(new BorderLayout());` with a field `private final GlassSurface text;` initialised in the constructor as `text = new GlassSurface(scroll);` (after `scroll` exists), keeping the same children. Keep the four-argument `apply` delegating with `1.0`, and add the five-argument version (its body is today's, with `theme.apply(area, scroll, editor, syntax, opacity);` and, at the end, `text.glass(opacity, editor.background(), editor.gutter());`), with `@param opacity the workspace opacity behind this tab; below 1 its text and gutter backgrounds are translucent`. Add `GlassSurface surface() { return text; }` if Step 1's test needs it.

`EditorWindow.apply()`: pass `context.appearance().workspaceOpacity(tab)` as the new last argument of `tab.apply(...)`.

`plugin.toml`: `version = "0.7.0"`, `sdk = ">=0.12.1, <0.13"`. Search the repository (`grep -rn '0\.6\.1'` in `plugins/editor`, `docs`, `jasper-app/src`) for places that pin the editor's version (tests or docs) and update them.

- [ ] **Step 5: Verify and commit**

Run the Step 1 tests, then the editor plugin's full `check`. Expected: PASS.

```bash
git branch --show-current   # must print claude/workspace-glass
git add plugins/editor
git commit -m "feat(editor): translucent text and gutter in glass windows (Editor 0.7.0)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Documentation, status and full verification

**Files:** `jasper-sdk/README.md`, `docs/sdk-architecture.md`, `docs/plugin-authoring.md` (~700-707 Appearance guidance), `docs/editor.md` (if it describes theming), `docs/app-maintenance.md` ("Workspace glass" recipe), `docs/STATUS.md`, the spec and both plan Status lines.

- [ ] **Step 1: Docs**

- `jasper-sdk/README.md`: a short "## Workspace opacity (SDK 0.12.1)" section after the 0.12.0 section: what `workspaceOpacity(Component)` returns, that `onWorkspaceChanged` reports its changes (including moving to another window), and the painting rule (paint your own background once with that alpha, `AlphaComposite.Src`, unantialiased rectangles; keep components above it non-opaque).
- `docs/sdk-architecture.md`: the matching paragraph next to the 0.12.0 section, naming `GlassSource`, `WindowContent.setGlassListener` and the contract case.
- `docs/plugin-authoring.md`: one bullet in the Appearance guidance.
- `docs/app-maintenance.md` "Workspace glass": a bullet for plugins (`GlassSource`, when `WindowContent` notifies).
- Spec: Status line mentions plan 2 executed; add one line to the amendment section recording the `workspaceOpacity(Component)` deviation.
- This plan's Status: executed; plan 1's Status unchanged.

- [ ] **Step 2: Full check and hygiene**

Run `./gradlew check --continue`; read totals from all `*/build/test-results/test/*.xml`; run the AGENTS.md hygiene script and `git diff --check fcba0f46..HEAD`. Any failure: stop and report, do not commit a green STATUS.

- [ ] **Step 3: STATUS and commit**

Update the workspace-glass bullet in `docs/STATUS.md`: plan 2 done (SDK 0.12.1 `workspaceOpacity(Component)`, Editor 0.7.0 translucent text and gutter), the deviation, the new totals, and GUI checks: an editor tab in Clear Dark / Clear Light, a split of terminal and editor tabs, moving an editor tab to a separate window and back, opacity changes live, current-line and selection look.

```bash
git branch --show-current   # must print claude/workspace-glass
git add docs jasper-sdk/README.md
git commit -m "docs: record workspace glass plan 2 (editor)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
