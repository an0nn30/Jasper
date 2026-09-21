# Jasper Plugin SDK Plan 3a: Rail, Panels and Plugin Windows — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on `claude/plugin-sdk-plan-3a`; native acceptance pending. Deviations from this text: (1) `PanelAndWindowValuesTest` needed `import dev.jasper.sdk.WindowOwner`, which the plan's test omitted. (2) `AuxiliaryWindowsTest`'s singleton case uses distinct ids for its singleton and non-singleton windows: a singleton request returns any open window with that id, so the plan's shared id made the expected count wrong. (3) The headless split-pane fallback in Task 4 was not needed; exact pixel sizes pass headlessly. (4) Test code reaches the headless `AuxiliaryWindows` through a new `AppContractTest.headlessWindows()` helper instead of repeating the construction, because `PluginRuntimeTest` has a parameter named `dev` that shadows the `dev.jasper` package in a qualified name. (5) `JasperApplicationPluginsTest.bindingProblemsSeparateUnknownUserIdsFromDroppedPluginDefaults` now awaits termination after `quit`: shutdown writes `ui-state.toml` into the temporary home, which raced JUnit's deletion of that directory.

**Goal:** Give plugins side and bottom panels toggled from a single left rail, rail action buttons, and application-built windows and dialogs with consistent chrome, with panel placement and window bounds remembered across launches; proven by the sample plugin.

**Architecture:** The SDK `ui` package gains `Panels`, `Rail` and `Windows`. SDK types stay confined to `dev.jasper.app.plugins`: panels and rail actions extend the app-native `contributions` model, and each window renders them through a new `WorkspaceRegions` (nested split panes rebuilt around the terminal deck) and `WindowRail`. Plugin windows live in a new app package `dev.jasper.app.windows`, split like `TerminalWindow`/`WindowContent`: a headless-testable `AuxiliarySurface` core and a thin untested native shell that adds the frame, icon, macOS title bar, menu bar, theme tracking and saved bounds. Layout state is application-owned in `persistence.UiState` (`ui-state.toml`), never in `config.toml`.

**Tech Stack:** Java 25 on JBR 25, Gradle wrapper, Swing, FlatLaf, JUnit 6.1.3, AssertJ 3.27.7, tomlj 1.1.1. No new dependency.

**Spec:** `docs/superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md` (section 4 items 2 and 6, section 5 "Rail and panels", "Windows and dialogs", "Look and feel", section 13 item 3). Earlier plans and their recorded deviations: `docs/superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-1-core-runtime.md`, `…-plan-2-actions-chrome.md`. Executors read all three.

## Global Constraints

- Use `./gradlew`, never a system Gradle. Java 25, JetBrains vendor toolchain. `./gradlew check` is headless and must pass at the end of every task.
- **Never launch the GUI** (`:jasper-app:run`, benchmarks, previews that open windows). GUI checks are the user's; the end of this plan lists them. `new JFrame()` and `new JDialog()` throw `HeadlessException` in tests: only `windows.NativeShells` and `workspace.TerminalWindow` may construct them.
- Work on branch `claude/plugin-sdk-plan-3a` in `.worktrees/plugin-sdk-plan-3a` (this plan is committed there). Run `git branch --show-current` before every commit. End commit messages with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- SDK modules reference only the JDK and `dev.jasper.sdk.*`. SDK types appear in the app only inside `dev.jasper.app.plugins`. `contributions`, `workspace`, `windows` and `persistence` must not import `dev.jasper.sdk`.
- No interface without two real implementations: new SDK interfaces are implemented by the testkit and by the app; app-native seams are final classes, records and JDK functional types.
- Panel ids and window ids start with `<plugin id>.` and match the namespaced-id rule used for actions. Every panel gets a contributed action `<panel id>.toggle`.
- One panel is visible per region per window. Regions are LEFT, RIGHT and BOTTOM; BOTTOM sits under the terminal only, between LEFT and RIGHT.
- A window with no panels and no rail actions shows no rail and lays out exactly as today.
- Threading: every new SDK registration call and handle mutator is EDT-only; panel factories, closing guards and handlers run on the EDT, contained.
- Layout state is app-owned: `<AppDirs.root>/ui-state.toml`, strict read, atomic write, never user-edited configuration. Unreadable state falls back to defaults with a logged warning; it never blocks startup.
- Source hygiene, package-info contracts and Javadoc doclint apply as in plans 1 and 2.
- `AppDocumentationTest` link-checks and example-checks every Markdown file under `docs/superpowers`, including this plan: keep documentation links inside code fences here and never write a literal example marker comment (Task 11 spells it `EXAMPLE-MARKER`).
- Never declare a directory that contains another Gradle project's `build/` as a task input (the plan-2 lesson).
- Known flake, not to be fixed here: `TerminalAppIntegrationTest` `"reflow"` case (see `docs/STATUS.md`). If it is the only failure, rerun.

### Deliberate scope decisions and deviations from the spec

1. **The spec's plan 3 is split.** This plan (3a) delivers the rail, panels, plugin windows and dialogs and their persistence. Plan 3b delivers the Plugins manager, zip install and consent, the restart banner, the retire request and "Restart normally". Each is a runnable deliverable of plan-2 size.
2. **`WindowOwner` is an ordinary interface in `dev.jasper.sdk`, not a sealed type.** Java forbids a sealed interface's permitted subtypes from living in other packages of the unnamed module, and `WindowHandle` (`terminal`) and `PluginWindow` (`ui`) are in different packages. Only the app and the testkit implement it.
3. `onClosing` and `onClosed` return a `Subscription`, consistent with every other registration.
4. Regions have no header or close button: a panel's content fills its region, and it is hidden from the rail, View → Panels, the palette or its shortcut.
5. Rail visibility is app state (`ui-state.toml`, toggled from View → Rail), not a `config.toml` key.
6. Quit closes plugin windows without consulting their `onClosing` guards (fast quit); closing a window by hand consults them.
7. `MacTitleBar` needs no new mode: a plugin window installs it with an empty tabs component and always shows the centered title.
8. `JasperSdk.VERSION` becomes `0.3.0`; the sample's range becomes `>=0.3, <0.4`.

## File Structure

```
jasper-sdk/src/main/java/dev/jasper/sdk/
  WindowOwner.java                                  create
  terminal/WindowHandle.java                        modify: extends WindowOwner
  ui/ Anchor.java PanelSpec.java PanelFactory.java PanelHost.java Panels.java Rail.java
      WindowSpec.java DialogSpec.java WindowSurface.java PluginWindow.java PluginDialog.java Windows.java   create
  plugin/PluginContext.java  JasperSdk.java         modify (Task 8)

jasper-sdk-testkit/.../testing/FakeUi.java FakePluginContext.java FakePluginHost.java    modify
jasper-sdk-testkit/.../contract/ContractHarness.java PluginContractTest.java               modify

jasper-app/src/main/java/dev/jasper/app/
  persistence/UiState.java                          create
  platform/AppDirs.java                             modify: uiState()
  contributions/ PanelRegion.java PanelSite.java PanelEntry.java                create
  contributions/Contributions.java                  modify: panels, rail actions, panel requests
  workspace/ WorkspaceRegions.java WindowRail.java  create
  workspace/ WindowContributions.java WindowContent.java WindowChrome.java WindowCommands.java   modify
  windows/ package-info.java AuxiliarySurface.java AuxiliaryWindows.java NativeShells.java       create
  plugins/ HostedUi.java HostedContext.java PluginHost.java PluginRuntime.java package-info.java  modify
  application/JasperApplication.java  workspace/TerminalWindow.java                                modify

plugins/sample/...SamplePlugin.java, plugin.toml, SamplePluginTest.java             modify
docs/plugin-authoring.md sdk-architecture.md app-architecture.md configuration.md STATUS.md, jasper-sdk/README.md   modify
```

---

### Task 0: Workspace

- [ ] **Step 1: Confirm the worktree and branch**

```bash
cd /Users/dustin/projects/moray/.worktrees/plugin-sdk-plan-3a && git branch --show-current
```

Expected: `claude/plugin-sdk-plan-3a`.

- [ ] **Step 2: Confirm the baseline is green**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL` (see the known flake above).

---

### Task 1: SDK panel, rail and window types

Types only; `PluginContext` gains its accessors in Task 8.

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/WindowOwner.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/WindowHandle.java`
- Create in `jasper-sdk/src/main/java/dev/jasper/sdk/ui/`: `Anchor`, `PanelSpec`, `PanelFactory`, `PanelHost`, `Panels`, `Rail`, `WindowSpec`, `DialogSpec`, `WindowSurface`, `PluginWindow`, `PluginDialog`, `Windows`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/PanelAndWindowValuesTest.java`

**Interfaces:**
- Produces:
  - `dev.jasper.sdk.WindowOwner` (marker); `WindowHandle extends WindowOwner`
  - `enum Anchor { LEFT, RIGHT, BOTTOM }`; `record PanelSpec(String id, String title, Icon icon, Anchor defaultAnchor)`
  - `@FunctionalInterface PanelFactory { JComponent create(PanelHost host); }`
  - `PanelHost { WindowHandle window(); void show(); void hide(); boolean visible(); Subscription onVisibility(Consumer<Boolean> handler); Subscription onClosed(Runnable handler); }`
  - `Panels { Subscription register(PanelSpec spec, PanelFactory factory); }`; `Rail { Subscription add(String actionId); }`
  - `record WindowSpec(String id, String title, Dimension preferredSize, boolean singleton)`; `record DialogSpec(String title, WindowOwner owner, boolean modal)`
  - `WindowSurface extends Subscription { void setContent(JComponent content); void show(); void toFront(); void setTitle(String title); Subscription onClosing(BooleanSupplier guard); Subscription onClosed(Runnable handler); }`
  - `PluginWindow extends WindowSurface, WindowOwner`; `PluginDialog extends WindowSurface`
  - `Windows { PluginWindow create(WindowSpec spec); PluginDialog dialog(DialogSpec spec); }`

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.sdk.ui;

import java.awt.Dimension;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PanelAndWindowValuesTest {
    @Test void panelSpecsValidate() {
        var spec = new PanelSpec("dev.x.tool.hosts", "Hosts", new ImageIcon(), Anchor.LEFT);
        assertThat(spec.defaultAnchor()).isEqualTo(Anchor.LEFT);
        assertThatIllegalArgumentException().isThrownBy(() -> new PanelSpec("nodot", "Hosts", new ImageIcon(), Anchor.LEFT));
        assertThatIllegalArgumentException().isThrownBy(() -> new PanelSpec("dev.x.tool.hosts", " ", new ImageIcon(), Anchor.LEFT));
        assertThatNullPointerException().isThrownBy(() -> new PanelSpec("dev.x.tool.hosts", "Hosts", null, Anchor.LEFT));
        assertThatNullPointerException().isThrownBy(() -> new PanelSpec("dev.x.tool.hosts", "Hosts", new ImageIcon(), null));
    }

    @Test void windowAndDialogSpecsValidateAndCopyTheSize() {
        var size = new Dimension(640, 480);
        var spec = new WindowSpec("dev.x.tool.manager", "Manager", size, true);
        size.width = 1;
        assertThat(spec.preferredSize()).isEqualTo(new Dimension(640, 480));
        assertThatIllegalArgumentException().isThrownBy(() -> new WindowSpec("nodot", "Manager", new Dimension(1, 1), false));
        assertThatIllegalArgumentException().isThrownBy(() -> new WindowSpec("dev.x.tool.manager", "", new Dimension(1, 1), false));
        assertThatIllegalArgumentException().isThrownBy(() -> new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(0, 10), false));
        WindowOwner owner = new WindowOwner() { };
        assertThat(new DialogSpec("Trust host?", owner, true).owner()).isSameAs(owner);
        assertThatNullPointerException().isThrownBy(() -> new DialogSpec("Trust host?", null, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new DialogSpec(" ", owner, true));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-sdk:test --tests '*PanelAndWindowValuesTest'`
Expected: compilation FAILS, `PanelSpec` not found.

- [ ] **Step 3: Write the types**

`dev/jasper/sdk/WindowOwner.java`:

```java
package dev.jasper.sdk;

/**
 * Something a dialog can be parented to: one of the application's terminal windows or a plugin's own
 * window. Implemented only by the application and the testkit; it deliberately exposes no frame.
 */
public interface WindowOwner { }
```

In `terminal/WindowHandle.java` change the declaration to `public interface WindowHandle extends dev.jasper.sdk.WindowOwner {` and update the package-info sentence "Depends on no other SDK package." to "Depends only on {@code dev.jasper.sdk}."

`ui/Anchor.java`:

```java
package dev.jasper.sdk.ui;

/** Where a panel appears by default; the user may move it. */
public enum Anchor {
    /** Beside the terminal, after the rail. */
    LEFT,
    /** Beside the terminal, at the far edge. */
    RIGHT,
    /** Under the terminal, between the side regions. */
    BOTTOM
}
```

`ui/PanelSpec.java`:

```java
package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.regex.Pattern;
import javax.swing.Icon;

/**
 * A panel as the rail shows it.
 *
 * @param id namespaced id that starts with the plugin's id and a dot; the application also registers
 *           the action {@code <id>.toggle}, which users may bind under {@code [keybindings]}
 * @param title non-blank title, used for the rail tooltip and View menu
 * @param icon rail icon, normally from {@link Appearance#icon}
 * @param defaultAnchor the region used until the user moves the panel
 */
public record PanelSpec(String id, String title, Icon icon, Anchor defaultAnchor) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates every component. */
    public PanelSpec {
        if (id == null || id.length() > 120 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced panel id: " + id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A panel needs a title");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(defaultAnchor, "defaultAnchor");
    }
}
```

`ui/PanelFactory.java`:

```java
package dev.jasper.sdk.ui;

import javax.swing.JComponent;

/** Builds a panel's content for one window. */
@FunctionalInterface
public interface PanelFactory {
    /**
     * Called on the UI thread once per window, the first time the panel is shown there. Build ordinary
     * Swing components; share your own model between the instances. A factory that throws yields an
     * error placeholder instead of breaking the window.
     *
     * @param host this instance's window and visibility
     * @return the content, which fills the panel's region
     */
    JComponent create(PanelHost host);
}
```

`ui/PanelHost.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.function.Consumer;

/** One panel instance's place in one window. All methods are UI-thread only. */
public interface PanelHost {
    /**
     * The window that holds this instance.
     *
     * @return the window
     */
    WindowHandle window();

    /** Shows the panel in this window, hiding whatever else occupied its region. */
    void show();

    /** Hides the panel in this window. */
    void hide();

    /**
     * Whether the panel is showing in this window.
     *
     * @return true while visible
     */
    boolean visible();

    /**
     * Runs the handler after each change of visibility in this window.
     *
     * @param handler receives the new visibility
     * @return the registration
     */
    Subscription onVisibility(Consumer<Boolean> handler);

    /**
     * Runs the handler when this instance is discarded because its window closed or the panel was removed.
     *
     * @param handler cleanup for this instance
     * @return the registration
     */
    Subscription onClosed(Runnable handler);
}
```

`ui/Panels.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;

/** Side and bottom panels. Each registered panel gets a rail icon in every window. */
public interface Panels {
    /**
     * Registers a panel on the UI thread.
     *
     * @param spec what the panel is
     * @param factory builds one instance per window, lazily
     * @return the registration; closing it removes the panel from every window
     * @throws IllegalArgumentException when the id does not start with the plugin's id and a dot, or is already registered
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    Subscription register(PanelSpec spec, PanelFactory factory);
}
```

`ui/Rail.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;

/** Plain action buttons on the rail, after the panel icons: for example a button that opens a plugin window. */
public interface Rail {
    /**
     * Adds a button for an action on the UI thread. The action should have an icon.
     *
     * @param actionId an action this plugin registered
     * @return the registration
     * @throws IllegalArgumentException when this plugin has not registered the action
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    Subscription add(String actionId);
}
```

`ui/WindowSpec.java`:

```java
package dev.jasper.sdk.ui;

import java.awt.Dimension;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A plugin window the application builds.
 *
 * @param id namespaced id that starts with the plugin's id and a dot; the window's bounds are remembered under it
 * @param title non-blank title
 * @param preferredSize size used until the user has resized the window; copied, treat as immutable
 * @param singleton when true, creating the window again while it is open returns the open one
 */
public record WindowSpec(String id, String title, Dimension preferredSize, boolean singleton) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates and copies. */
    public WindowSpec {
        if (id == null || id.length() > 128 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced window id: " + id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A window needs a title");
        Objects.requireNonNull(preferredSize, "preferredSize");
        if (preferredSize.width <= 0 || preferredSize.height <= 0) throw new IllegalArgumentException("A window needs a positive size");
        preferredSize = new Dimension(preferredSize);
    }
}
```

`ui/DialogSpec.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.WindowOwner;
import java.util.Objects;

/**
 * A dialog the application builds over one of its windows.
 *
 * @param title non-blank title
 * @param owner the terminal window or plugin window the dialog belongs to
 * @param modal whether the dialog blocks its owner; {@link WindowSurface#show()} then returns only after it closes
 */
public record DialogSpec(String title, WindowOwner owner, boolean modal) {
    /** Validates the title and owner. */
    public DialogSpec {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A dialog needs a title");
        Objects.requireNonNull(owner, "owner");
    }
}
```

`ui/WindowSurface.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import java.util.function.BooleanSupplier;
import javax.swing.JComponent;

/**
 * What plugin windows and dialogs share. The application owns the frame, title bar, icon, menu bar,
 * theme tracking and saved bounds; the plugin fills the content. UI thread only. {@link #close()}
 * closes at once, without consulting {@link #onClosing} guards, and is idempotent.
 */
public interface WindowSurface extends Subscription {
    /**
     * Replaces the content.
     *
     * @param content ordinary Swing components
     */
    void setContent(JComponent content);

    /** Shows the window. For a modal dialog this returns after the dialog has closed. */
    void show();

    /** Brings the window to the front, restoring it if minimized. */
    void toFront();

    /**
     * Changes the title.
     *
     * @param title non-blank title
     */
    void setTitle(String title);

    /**
     * Adds a guard consulted when the user tries to close the window. Any guard returning false keeps
     * it open; a guard that throws is treated as allowing the close. Quit does not consult guards.
     *
     * @param guard returns whether the window may close
     * @return the registration
     */
    Subscription onClosing(BooleanSupplier guard);

    /**
     * Runs the handler once, after the window has closed for any reason.
     *
     * @param handler cleanup
     * @return the registration
     */
    Subscription onClosed(Runnable handler);
}
```

`ui/PluginWindow.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.WindowOwner;

/** A plugin's own top-level window; it can own dialogs. */
public interface PluginWindow extends WindowSurface, WindowOwner { }
```

`ui/PluginDialog.java`:

```java
package dev.jasper.sdk.ui;

/** A plugin's dialog over a terminal window or one of its own windows. */
public interface PluginDialog extends WindowSurface { }
```

`ui/Windows.java`:

```java
package dev.jasper.sdk.ui;

/** Application-built windows and dialogs with consistent chrome. */
public interface Windows {
    /**
     * Creates a window on the UI thread; it is not shown until {@link WindowSurface#show()}.
     *
     * @param spec what the window is
     * @return the window, or the open one for a singleton id
     * @throws IllegalArgumentException when the id does not start with the plugin's id and a dot
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    PluginWindow create(WindowSpec spec);

    /**
     * Creates a dialog on the UI thread; it is not shown until {@link WindowSurface#show()}.
     *
     * @param spec what the dialog is
     * @return the dialog
     * @throws IllegalArgumentException when the owner is a closed window or not one this application created
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    PluginDialog dialog(DialogSpec spec);
}
```

- [ ] **Step 4: Run the tests and guards**

Run: `./gradlew :jasper-sdk:check verifySdkArchitecture`
Expected: `BUILD SUCCESSFUL`; no package cycle (`terminal` → root; `ui` → `terminal`, root).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-sdk
git commit -m "feat: define the SDK panels, rail and windows API

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: Application-owned layout state

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/persistence/UiStateTest.java`

**Interfaces:**
- Produces (public, caller-thread-confined; the application uses it on the EDT):
  - `UiState.load(Path file)` (never throws; unreadable or invalid state logs a warning and yields defaults); `UiState.inMemory()` (never writes)
  - `record UiState.Panel(String region, boolean visible, int size)` with `region` one of `LEFT`, `RIGHT`, `BOTTOM` and `size` 80–4000; `record UiState.Bounds(int x, int y, int width, int height)` with positive width and height
  - `Optional<Panel> panel(String id)`, `void putPanel(String id, Panel value)`, `Optional<Bounds> window(String id)`, `void putWindow(String id, Bounds value)`, `boolean railVisible()`, `void setRailVisible(boolean value)`, `void save()` (atomic; failure is logged, never thrown)
  - `AppDirs.uiState()` → `root/ui-state.toml`

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class UiStateTest {
    @TempDir Path dir;

    @Test void roundTripsPanelsWindowsAndTheRail() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        assertThat(state.railVisible()).isTrue();
        assertThat(state.panel("dev.x.hosts")).isEmpty();
        state.putPanel("dev.x.hosts", new UiState.Panel("RIGHT", true, 300));
        state.putWindow("dev.x.manager", new UiState.Bounds(-20, 40, 800, 600));
        state.setRailVisible(false);
        state.save();
        assertThat(Files.readString(file)).isEqualTo("""
            version = 1
            rail_visible = false

            [panels."dev.x.hosts"]
            region = "RIGHT"
            visible = true
            size = 300

            [windows."dev.x.manager"]
            x = -20
            y = 40
            width = 800
            height = 600
            """);
        UiState again = UiState.load(file);
        assertThat(again.railVisible()).isFalse();
        assertThat(again.panel("dev.x.hosts")).hasValue(new UiState.Panel("RIGHT", true, 300));
        assertThat(again.window("dev.x.manager")).hasValue(new UiState.Bounds(-20, 40, 800, 600));
    }

    @Test void invalidValuesAreRejectedAndInvalidFilesFallBackToDefaults() throws Exception {
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Panel("TOP", true, 300));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Panel("LEFT", true, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Bounds(0, 0, 0, 10));
        Path file = dir.resolve("ui-state.toml");
        for (String bad : new String[]{"not toml = = =", "version = 2\n", "version = 1\n[panels.\"a.b\"]\nregion = \"TOP\"\nvisible = true\nsize = 300\n"}) {
            Files.writeString(file, bad);
            UiState state = UiState.load(file);
            assertThat(state.railVisible()).as(bad).isTrue();
            assertThat(state.panel("a.b")).as(bad).isEmpty();
        }
    }

    @Test void inMemoryStateNeverTouchesDisk() {
        UiState state = UiState.inMemory();
        state.putPanel("dev.x.hosts", new UiState.Panel("LEFT", false, 200));
        assertThatCode(state::save).doesNotThrowAnyException();
        assertThat(state.panel("dev.x.hosts")).isPresent();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*UiStateTest'`
Expected: compilation FAILS, `UiState` not found.

- [ ] **Step 3: Implement**

`UiState.java`:

```java
package dev.jasper.app.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Application-owned layout state: where each panel sits, whether the rail shows, and where auxiliary
 * windows were. It is not user configuration. Caller-thread-confined. Reads are strict; anything
 * unreadable falls back to defaults rather than blocking startup, and failed writes are only logged.
 */
public final class UiState {
    /** One panel's remembered place. */
    public record Panel(String region, boolean visible, int size) {
        public Panel {
            if (!REGIONS.contains(region)) throw new IllegalArgumentException("Unknown panel region: " + region);
            if (size < 80 || size > 4000) throw new IllegalArgumentException("Panel size must be 80 to 4000: " + size);
        }
    }

    /** One auxiliary window's remembered bounds; the position may be negative on multi-display setups. */
    public record Bounds(int x, int y, int width, int height) {
        public Bounds {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Window bounds need a positive size");
        }
    }

    private static final System.Logger LOG = System.getLogger(UiState.class.getName());
    private static final Set<String> REGIONS = Set.of("LEFT", "RIGHT", "BOTTOM");
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_.-]{0,127}");
    private static final int MAX_BYTES = 256 * 1024;
    private final Path file;
    private final Map<String, Panel> panels = new TreeMap<>();
    private final Map<String, Bounds> windows = new TreeMap<>();
    private boolean railVisible = true;

    private UiState(Path file) { this.file = file; }

    /** State that lives only for this process: tests, and windows that were never connected to a file. */
    public static UiState inMemory() { return new UiState(null); }

    public static UiState load(Path file) {
        UiState state = new UiState(file);
        try {
            Optional<String> text = TomlStateFile.readBounded(file, MAX_BYTES, "UI state");
            if (text.isPresent()) state.parse(text.get());
        } catch (IOException | RuntimeException invalid) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring unreadable UI state " + file, invalid);
            state.panels.clear(); state.windows.clear(); state.railVisible = true;
        }
        return state;
    }

    private void parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors() || !Long.valueOf(1).equals(toml.get(List.of("version")))) throw new IOException("Invalid UI state version or format");
        if (toml.get(List.of("rail_visible")) instanceof Boolean visible) railVisible = visible;
        if (toml.get(List.of("panels")) instanceof TomlTable table)
            for (String id : table.keySet())
                if (ID.matcher(id).matches() && table.get(List.of(id)) instanceof TomlTable entry)
                    panels.put(id, new Panel(String.valueOf(entry.get(List.of("region"))),
                        Boolean.TRUE.equals(entry.get(List.of("visible"))), number(entry, "size")));
        if (toml.get(List.of("windows")) instanceof TomlTable table)
            for (String id : table.keySet())
                if (ID.matcher(id).matches() && table.get(List.of(id)) instanceof TomlTable entry)
                    windows.put(id, new Bounds(number(entry, "x"), number(entry, "y"), number(entry, "width"), number(entry, "height")));
    }

    private static int number(TomlTable table, String key) throws IOException {
        if (table.get(List.of(key)) instanceof Long value && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return value.intValue();
        throw new IOException("UI state " + key + " must be an integer");
    }

    public Optional<Panel> panel(String id) { return Optional.ofNullable(panels.get(id)); }
    public void putPanel(String id, Panel value) { if (ID.matcher(id).matches()) panels.put(id, value); }
    public Optional<Bounds> window(String id) { return Optional.ofNullable(windows.get(id)); }
    public void putWindow(String id, Bounds value) { if (ID.matcher(id).matches()) windows.put(id, value); }
    public boolean railVisible() { return railVisible; }
    public void setRailVisible(boolean value) { railVisible = value; }

    /** Ids are restricted to characters that need no TOML escaping. */
    public void save() {
        if (file == null) return;
        var text = new StringBuilder("version = 1\nrail_visible = ").append(railVisible).append('\n');
        panels.forEach((id, panel) -> text.append("\n[panels.\"").append(id).append("\"]\nregion = \"").append(panel.region())
            .append("\"\nvisible = ").append(panel.visible()).append("\nsize = ").append(panel.size()).append('\n'));
        windows.forEach((id, bounds) -> text.append("\n[windows.\"").append(id).append("\"]\nx = ").append(bounds.x())
            .append("\ny = ").append(bounds.y()).append("\nwidth = ").append(bounds.width()).append("\nheight = ").append(bounds.height()).append('\n'));
        try { TomlStateFile.writeAtomically(file, ".ui-state-", text.toString()); }
        catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Could not save UI state to " + file, failure); }
    }
}
```

In `AppDirs`, after `pluginData()`:

```java
    /** Application-owned layout state: panel placement, rail visibility and auxiliary window bounds. */
    public Path uiState() {
        return root.resolve("ui-state.toml");
    }
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*UiStateTest'`
Expected: PASS, 3 tests. An invalid `Panel` inside a file throws `IllegalArgumentException` from the record, which `load` catches as `RuntimeException`.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: persist panel placement, rail visibility and window bounds

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 3: Panels and rail actions in the contributions model

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/contributions/{PanelRegion,PanelSite,PanelEntry}.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/contributions/PanelContributionsTest.java`

**Interfaces:**
- Produces:
  - `enum PanelRegion { LEFT, RIGHT, BOTTOM }`
  - `PanelSite(UUID windowId, Runnable show, Runnable hide, BooleanSupplier visible)`: `windowId()`, `show()`, `hide()`, `visible()`, `Subscription onVisibility(Consumer<Boolean>)`, `Subscription onClosed(Runnable)`, and for the owning window `notifyVisibility(boolean)`, `notifyClosed()` (once)
  - `PanelEntry`: `id()`, `title()`, `icon()`, `defaultRegion()`, `Function<PanelSite, JComponent> factory()`, `close()`
  - `Contributions`: `Kind` gains `PANELS`, `RAIL`; `record PanelRequest(UUID windowId, String panelId, Op op)` with `enum Op { SHOW, HIDE, TOGGLE }`; `PanelEntry addPanel(String id, String title, Icon icon, PanelRegion defaultRegion, Function<PanelSite, JComponent> factory)`; `List<PanelEntry> panels()`; `Subscription addRailAction(String actionId)`; `List<String> railActions()`; `Subscription onPanelRequest(Consumer<PanelRequest> listener)`; `void requestPanel(PanelRequest request)`
  - Adding a panel also registers the action `<id>.toggle` ("Toggle <title>") and lists it under a View → Panels submenu; closing the panel removes both.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.contributions;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class PanelContributionsTest {
    private final Contributions model = new Contributions();

    @Test void aPanelBringsItsToggleActionAndViewMenuEntryAndTakesThemWithIt() {
        List<Contributions.Kind> changes = new ArrayList<>();
        List<Contributions.PanelRequest> requests = new ArrayList<>();
        model.onChanged(changes::add);
        model.onPanelRequest(requests::add);
        PanelEntry hosts = model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), PanelRegion.LEFT, site -> new JLabel("hosts"));
        assertThatIllegalArgumentException().isThrownBy(() ->
            model.addPanel("dev.x.hosts", "Again", new ImageIcon(), PanelRegion.RIGHT, site -> new JLabel()));
        assertThat(model.panels()).containsExactly(hosts);
        assertThat(changes).contains(Contributions.Kind.PANELS, Contributions.Kind.ACTIONS, Contributions.Kind.MENUS);

        ActionEntry toggle = model.action("dev.x.hosts.toggle").orElseThrow();
        assertThat(toggle.title()).isEqualTo("Toggle Hosts");
        UUID window = UUID.randomUUID();
        toggle.invoke(new Contributions.Invocation(window, Optional.empty()));
        assertThat(requests).containsExactly(new Contributions.PanelRequest(window, "dev.x.hosts", Contributions.PanelRequest.Op.TOGGLE));
        assertThat(model.menus()).singleElement().satisfies(section -> {
            assertThat(section.target()).isEqualTo(MenuTarget.standard(MenuTarget.Slot.VIEW));
            assertThat(section.entries()).containsExactly(new MenuEntry.Submenu("Panels", List.of(new MenuEntry.Item("dev.x.hosts.toggle"))));
        });

        hosts.close();
        hosts.close();
        assertThat(model.panels()).isEmpty();
        assertThat(model.action("dev.x.hosts.toggle")).isEmpty();
        assertThat(model.menus()).isEmpty();
    }

    @Test void railActionsKeepOrderAndSitesNotifyOnce() {
        List<Contributions.Kind> changes = new ArrayList<>();
        model.onChanged(changes::add);
        var first = model.addRailAction("dev.x.open");
        model.addRailAction("dev.x.other");
        first.close();
        assertThat(model.railActions()).containsExactly("dev.x.other");
        assertThat(changes).containsOnly(Contributions.Kind.RAIL);

        List<String> events = new ArrayList<>();
        boolean[] visible = {false};
        var site = new PanelSite(UUID.randomUUID(), () -> events.add("show"), () -> events.add("hide"), () -> visible[0]);
        var watching = site.onVisibility(value -> events.add("visible:" + value));
        site.onVisibility(value -> { throw new IllegalStateException("listener failure"); });
        site.onClosed(() -> events.add("closed"));
        site.show(); site.hide();
        site.notifyVisibility(true);
        watching.close();
        site.notifyVisibility(false);
        site.notifyClosed(); site.notifyClosed();
        assertThat(events).containsExactly("show", "hide", "visible:true", "closed");
        assertThat(site.visible()).isFalse();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*PanelContributionsTest'`
Expected: compilation FAILS, `PanelEntry` not found.

- [ ] **Step 3: Implement**

`PanelRegion.java`:

```java
package dev.jasper.app.contributions;

/** The three places a panel can sit. BOTTOM is under the terminal only, between LEFT and RIGHT. */
public enum PanelRegion { LEFT, RIGHT, BOTTOM }
```

`PanelSite.java`:

```java
package dev.jasper.app.contributions;

import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One panel instance's place in one window, as its contributor sees it. Created and notified by that window. EDT only. */
public final class PanelSite {
    private static final System.Logger LOG = System.getLogger(PanelSite.class.getName());
    private final UUID windowId;
    private final Runnable show;
    private final Runnable hide;
    private final BooleanSupplier visible;
    private final List<Consumer<Boolean>> visibility = new ArrayList<>();
    private final List<Runnable> closed = new ArrayList<>();
    private boolean done;

    public PanelSite(UUID windowId, Runnable show, Runnable hide, BooleanSupplier visible) {
        this.windowId = Objects.requireNonNull(windowId); this.show = Objects.requireNonNull(show);
        this.hide = Objects.requireNonNull(hide); this.visible = Objects.requireNonNull(visible);
    }

    public UUID windowId() { return windowId; }
    public void show() { if (!done) show.run(); }
    public void hide() { if (!done) hide.run(); }
    public boolean visible() { return !done && visible.getAsBoolean(); }

    public Subscription onVisibility(Consumer<Boolean> listener) {
        visibility.add(Objects.requireNonNull(listener));
        return new Subscription(() -> visibility.remove(listener));
    }

    public Subscription onClosed(Runnable listener) {
        closed.add(Objects.requireNonNull(listener));
        return new Subscription(() -> closed.remove(listener));
    }

    /** Called by the owning window after the panel was shown or hidden there. */
    public void notifyVisibility(boolean value) {
        if (done) return;
        for (Consumer<Boolean> listener : List.copyOf(visibility)) {
            try { listener.accept(value); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A panel visibility listener failed", failure); }
        }
    }

    /** Called by the owning window, once, when the instance is discarded. */
    public void notifyClosed() {
        if (done) return;
        done = true;
        for (Runnable listener : List.copyOf(closed)) {
            try { listener.run(); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A panel close listener failed", failure); }
        }
        visibility.clear(); closed.clear();
    }
}
```

`PanelEntry.java`:

```java
package dev.jasper.app.contributions;

import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JComponent;

/** A contributed panel. Each window asks the factory for its own instance, lazily, on first show. */
public final class PanelEntry {
    private final Contributions owner;
    private final String id;
    private final String title;
    private final Icon icon;
    private final PanelRegion defaultRegion;
    private final Function<PanelSite, JComponent> factory;
    private boolean closed;

    PanelEntry(Contributions owner, String id, String title, Icon icon, PanelRegion defaultRegion,
               Function<PanelSite, JComponent> factory) {
        this.owner = owner; this.id = id; this.title = title; this.icon = icon;
        this.defaultRegion = defaultRegion; this.factory = factory;
    }

    public String id() { return id; }
    public String title() { return title; }
    public Icon icon() { return icon; }
    public PanelRegion defaultRegion() { return defaultRegion; }
    /** May return null or throw; the window then shows a placeholder. */
    public Function<PanelSite, JComponent> factory() { return factory; }

    public void close() {
        if (closed) return;
        closed = true;
        owner.remove(this);
    }
}
```

In `Contributions`:

- extend the enum: `public enum Kind { ACTIONS, TOOLBAR, MENUS, STATUS, PANELS, RAIL }`
- add the record, fields and methods (imports `java.util.function.Function`, `javax.swing.JComponent`):

```java
    /** A request to show, hide or toggle a panel in one window; each window answers only its own. */
    public record PanelRequest(UUID windowId, String panelId, Op op) {
        public enum Op { SHOW, HIDE, TOGGLE }
    }

    private final Map<String, PanelEntry> panels = new LinkedHashMap<>();
    private final Map<String, ActionEntry> panelToggles = new LinkedHashMap<>();
    /** Single-element holders: the same action may be placed twice, and removal is by identity. */
    private final List<String[]> railActions = new ArrayList<>();
    private final List<Consumer<PanelRequest>> panelListeners = new ArrayList<>();
    private MenuSection panelsMenu;

    public PanelEntry addPanel(String id, String title, Icon icon, PanelRegion defaultRegion,
                               Function<PanelSite, JComponent> factory) {
        requireEdt();
        Objects.requireNonNull(factory, "factory");
        if (panels.containsKey(id)) throw new IllegalArgumentException("Panel already registered: " + id);
        var entry = new PanelEntry(this, id, title, icon, Objects.requireNonNull(defaultRegion, "defaultRegion"), factory);
        panels.put(id, entry);
        panelToggles.put(id, addAction(id + ".toggle", "Toggle " + title, icon, List.of("panel", "show", "hide"), Optional.empty(),
            invocation -> requestPanel(new PanelRequest(invocation.windowId(), id, PanelRequest.Op.TOGGLE))));
        rebuildPanelsMenu();
        changed(Kind.PANELS);
        return entry;
    }

    public List<PanelEntry> panels() { return List.copyOf(panels.values()); }

    public Subscription addRailAction(String actionId) {
        requireEdt();
        String[] placed = {Objects.requireNonNull(actionId, "actionId")};
        railActions.add(placed);
        changed(Kind.RAIL);
        return new Subscription(() -> {
            for (int i = 0; i < railActions.size(); i++) if (railActions.get(i) == placed) { railActions.remove(i); break; }
            changed(Kind.RAIL);
        });
    }

    public List<String> railActions() { return railActions.stream().map(placed -> placed[0]).toList(); }

    public Subscription onPanelRequest(Consumer<PanelRequest> listener) {
        requireEdt();
        panelListeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> panelListeners.remove(listener));
    }

    public void requestPanel(PanelRequest request) {
        requireEdt();
        for (Consumer<PanelRequest> listener : List.copyOf(panelListeners)) {
            try { listener.accept(request); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A panel request listener failed", failure); }
        }
    }

    void remove(PanelEntry entry) {
        if (!panels.remove(entry.id(), entry)) return;
        ActionEntry toggle = panelToggles.remove(entry.id());
        if (toggle != null) toggle.close();
        rebuildPanelsMenu();
        changed(Kind.PANELS);
    }

    /** One app-owned View section, "Panels", listing every panel's toggle; gone when there are no panels. */
    private void rebuildPanelsMenu() {
        if (panels.isEmpty()) {
            if (panelsMenu != null) { panelsMenu.close(); panelsMenu = null; }
            return;
        }
        if (panelsMenu == null) panelsMenu = addMenuSection(MenuTarget.standard(MenuTarget.Slot.VIEW));
        List<MenuEntry> toggles = new ArrayList<>();
        for (String id : panels.keySet()) toggles.add(new MenuEntry.Item(id + ".toggle"));
        panelsMenu.set(List.of(new MenuEntry.Submenu("Panels", toggles)));
    }
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.workspace.*'`
Expected: PASS. `WindowContributions.changed` is a statement switch over the enum, so the two new constants need no case yet.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: model contributed panels, their toggles and rail actions

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Panel regions around the terminal

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceRegions.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceRegionsTest.java`

**Interfaces:**
- Produces (package-private): `WorkspaceRegions(JComponent center)`; `void show(PanelRegion region, JComponent content, int size)`; `void hide(PanelRegion region)`; `JComponent content(PanelRegion region)` (null when empty); `int size(PanelRegion region)` (current size in pixels, the default when never shown); constants `DEFAULT_SIDE = 260`, `DEFAULT_BOTTOM = 200`, `MINIMUM = 120` (unscaled)

The tree is rebuilt on every change rather than hiding split-pane children, because a rebuilt tree has no zero-width dividers or stale divider locations to reason about: bottom wraps the center, right wraps that, left wraps that.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Rectangle;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class WorkspaceRegionsTest {
    private static Rectangle in(WorkspaceRegions regions, java.awt.Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), regions);
    }

    private static void layout(WorkspaceRegions regions) {
        regions.setSize(1000, 600);
        // Twice: a trailing split learns its width in the first pass and places its divider in the second.
        LayoutTestSupport.layoutTree(regions);
        LayoutTestSupport.layoutTree(regions);
    }

    @Test void theCenterFillsEverythingUntilARegionIsShownAndAgainAfterItIsHidden() {
        var deck = new JPanel();
        var regions = new WorkspaceRegions(deck);
        layout(regions);
        assertThat(in(regions, deck)).isEqualTo(new Rectangle(0, 0, 1000, 600));
        assertThat(regions.content(PanelRegion.LEFT)).isNull();
        assertThat(regions.size(PanelRegion.LEFT)).isEqualTo(WorkspaceRegions.DEFAULT_SIDE);

        var left = new JLabel("left");
        regions.show(PanelRegion.LEFT, left, 250);
        layout(regions);
        assertThat(regions.content(PanelRegion.LEFT)).isSameAs(left);
        assertThat(in(regions, left).width).isEqualTo(250);
        assertThat(in(regions, left).x).isZero();
        assertThat(in(regions, deck).x).isGreaterThan(250);
        assertThat(in(regions, deck).x + in(regions, deck).width).isEqualTo(1000);

        regions.hide(PanelRegion.LEFT);
        layout(regions);
        assertThat(in(regions, deck)).isEqualTo(new Rectangle(0, 0, 1000, 600));
        assertThat(regions.size(PanelRegion.LEFT)).as("the last size is remembered").isEqualTo(250);
    }

    @Test void bottomSitsUnderTheCenterOnlyBetweenTheSideRegions() {
        var deck = new JPanel();
        var regions = new WorkspaceRegions(deck);
        var left = new JLabel("left"); var right = new JLabel("right"); var bottom = new JLabel("bottom");
        regions.show(PanelRegion.LEFT, left, 200);
        regions.show(PanelRegion.RIGHT, right, 300);
        regions.show(PanelRegion.BOTTOM, bottom, 150);
        layout(regions);
        assertThat(in(regions, left).width).isEqualTo(200);
        assertThat(in(regions, left).height).isEqualTo(600);
        assertThat(in(regions, right).width).isEqualTo(300);
        assertThat(in(regions, right).x + 300).isEqualTo(1000);
        assertThat(in(regions, right).height).isEqualTo(600);
        assertThat(in(regions, bottom).height).isEqualTo(150);
        assertThat(in(regions, bottom).y + 150).isEqualTo(600);
        assertThat(in(regions, bottom).x).isEqualTo(in(regions, deck).x);
        assertThat(in(regions, bottom).width).isEqualTo(in(regions, deck).width);
        assertThat(in(regions, deck).y).isZero();

        var replacement = new JLabel("other");
        regions.show(PanelRegion.RIGHT, replacement, 320);
        layout(regions);
        assertThat(regions.content(PanelRegion.RIGHT)).isSameAs(replacement);
        assertThat(right.getParent()).isNull();
        assertThat(in(regions, replacement).width).isEqualTo(320);
        assertThat(regions.size(PanelRegion.BOTTOM)).isEqualTo(150);
    }

    @Test void sizesAreClampedToAUsableMinimum() {
        var regions = new WorkspaceRegions(new JPanel());
        regions.show(PanelRegion.LEFT, new JLabel("left"), 5);
        layout(regions);
        assertThat(regions.size(PanelRegion.LEFT)).isGreaterThanOrEqualTo(WorkspaceRegions.MINIMUM);
    }
}
```

If the headless split-pane UI does not apply a divider location until it has been painted, `in(regions, left).width` will differ from the requested size; in that case assert through the enclosing `JSplitPane.getDividerLocation()` instead and record the deviation. The production behavior (a showing window) is what the native checklist verifies.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*WorkspaceRegionsTest'`
Expected: compilation FAILS, `WorkspaceRegions` not found.

- [ ] **Step 3: Implement**

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.PanelRegion;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.EnumMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

/**
 * The terminal deck with up to three panel regions around it. BOTTOM wraps the center, RIGHT wraps
 * that, LEFT wraps that, so the bottom region spans only the terminal. With no region shown the
 * center is the only child and the layout is exactly what it was before regions existed. EDT only.
 */
final class WorkspaceRegions extends JPanel {
    static final int DEFAULT_SIDE = 260, DEFAULT_BOTTOM = 200, MINIMUM = 120;

    /** A split whose second component keeps a fixed size: the divider is placed once the split knows its own size. */
    private static final class Split extends JSplitPane {
        private int pendingTrailing = -1;

        Split(int orientation, JComponent first, JComponent second, boolean trailing, int size) {
            super(orientation, true, first, second);
            setBorder(null);
            setDividerSize(UIScale.scale(5));
            setResizeWeight(trailing ? 1.0 : 0.0);
            if (trailing) pendingTrailing = size; else setDividerLocation(size);
        }

        @Override public void doLayout() {
            int total = getOrientation() == HORIZONTAL_SPLIT ? getWidth() : getHeight();
            if (pendingTrailing >= 0 && total > 0) {
                setDividerLocation(Math.max(0, total - pendingTrailing - getDividerSize()));
                pendingTrailing = -1;
            }
            super.doLayout();
        }
    }

    private final JComponent center;
    private final EnumMap<PanelRegion, JComponent> contents = new EnumMap<>(PanelRegion.class);
    private final EnumMap<PanelRegion, JPanel> hosts = new EnumMap<>(PanelRegion.class);
    private final EnumMap<PanelRegion, Integer> sizes = new EnumMap<>(PanelRegion.class);

    WorkspaceRegions(JComponent center) {
        super(new BorderLayout());
        this.center = center;
        rebuild();
    }

    void show(PanelRegion region, JComponent content, int size) {
        capture();
        contents.put(region, content);
        sizes.put(region, Math.max(UIScale.scale(MINIMUM), size));
        rebuild();
    }

    void hide(PanelRegion region) {
        capture();
        if (contents.remove(region) != null) rebuild();
    }

    JComponent content(PanelRegion region) { return contents.get(region); }

    int size(PanelRegion region) {
        capture();
        return sizes.getOrDefault(region, UIScale.scale(region == PanelRegion.BOTTOM ? DEFAULT_BOTTOM : DEFAULT_SIDE));
    }

    /** Remembers what the user dragged the dividers to, for regions that are laid out. */
    private void capture() {
        hosts.forEach((region, host) -> {
            int current = region == PanelRegion.BOTTOM ? host.getHeight() : host.getWidth();
            if (contents.containsKey(region) && host.getParent() != null && current > 0) sizes.put(region, current);
        });
    }

    private JPanel host(PanelRegion region) {
        JPanel host = hosts.computeIfAbsent(region, key -> {
            var panel = new JPanel(new BorderLayout());
            panel.setMinimumSize(new Dimension(UIScale.scale(MINIMUM), UIScale.scale(MINIMUM)));
            return panel;
        });
        host.removeAll();
        host.add(contents.get(region), BorderLayout.CENTER);
        return host;
    }

    private void rebuild() {
        removeAll();
        hosts.values().forEach(JPanel::removeAll);
        JComponent current = center;
        if (contents.containsKey(PanelRegion.BOTTOM))
            current = new Split(JSplitPane.VERTICAL_SPLIT, current, host(PanelRegion.BOTTOM), true, sizes.get(PanelRegion.BOTTOM));
        if (contents.containsKey(PanelRegion.RIGHT))
            current = new Split(JSplitPane.HORIZONTAL_SPLIT, current, host(PanelRegion.RIGHT), true, sizes.get(PanelRegion.RIGHT));
        if (contents.containsKey(PanelRegion.LEFT))
            current = new Split(JSplitPane.HORIZONTAL_SPLIT, host(PanelRegion.LEFT), current, false, sizes.get(PanelRegion.LEFT));
        add(current, BorderLayout.CENTER);
        revalidate(); repaint();
    }
}
```

`hosts.values().forEach(JPanel::removeAll)` detaches a replaced content from its host, which is what `right.getParent()` being null asserts.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*WorkspaceRegionsTest'`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: lay out left, right and bottom panel regions around the terminal

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: The rail and per-window panels

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowRail.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/{WindowContributions,WindowContent,WindowCommands,WindowChrome}.java`, `workspace/package-info.java` (add `dev.jasper.app.persistence`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowPanelsTest.java`

**Interfaces:**
- Consumes: `WorkspaceRegions` (Task 4), `Contributions` panels and requests (Task 3), `UiState` (Task 2).
- Produces:
  - `WindowRail(Action settings)`: `record PanelButton(String id, String title, Icon icon, PanelRegion region, boolean selected)`; fields `Consumer<String> onToggle`, `BiConsumer<String, PanelRegion> onMove`; `void render(List<PanelButton> panels, List<Action> actions)`; `boolean empty()`; `List<AbstractButton> buttons()` (panel buttons then action buttons, in visual order); `void refreshTheme()`
  - `WindowContent`: `public void connectContributions(Contributions model, UiState state)`; the one-argument form keeps working with in-memory state; package-private `WorkspaceRegions regions()`, `WindowRail rail()`, `boolean railVisible()`, `void setRailVisible(boolean)`, `void syncRailVisibility()`
  - `WindowContributions(WindowContent owner, Contributions model, UiState state)`; package-private `void showPanel(String id)`, `void hidePanel(String id)`, `void movePanel(String id, PanelRegion region)`, `void refreshTheme()`
  - command `view.rail` and a View → Rail check item

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.persistence.UiState;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class WindowPanelsTest {
    @TempDir Path dir;

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    private static WindowContent window() { return DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>())); }

    private static void toggle(Contributions model, WindowContent owner, String id) {
        model.requestPanel(new Contributions.PanelRequest(owner.id(), id, Contributions.PanelRequest.Op.TOGGLE));
    }

    @Test void anUnconnectedOrEmptyWindowHasNoRailAndTheOldLayout() throws Exception {
        edt(() -> {
            WindowContent owner = window();
            assertThat(owner.rail().isVisible()).isFalse();
            owner.connectContributions(new Contributions());
            assertThat(owner.rail().isVisible()).isFalse();
            assertThat(owner.regions().content(PanelRegion.LEFT)).isNull();
        });
    }

    @Test void panelsAreLazyPerWindowToggleFromTheRailAndShareARegionOneAtATime() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            List<PanelSite> sites = new ArrayList<>();
            List<String> events = new ArrayList<>();
            var hostsView = new JLabel("hosts");
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), PanelRegion.LEFT, site -> {
                sites.add(site);
                site.onVisibility(visible -> events.add("hosts:" + visible));
                site.onClosed(() -> events.add("hosts:closed"));
                return hostsView;
            });
            PanelEntry files = model.addPanel("dev.x.files", "Files", new ImageIcon(), PanelRegion.LEFT, site -> new JLabel("files"));
            assertThat(owner.rail().isVisible()).isTrue();
            assertThat(owner.rail().buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Hosts", "Files");
            assertThat(sites).as("no instance until first shown").isEmpty();

            owner.rail().buttons().get(0).doClick();
            assertThat(sites).singleElement().satisfies(site -> {
                assertThat(site.windowId()).isEqualTo(owner.id());
                assertThat(site.visible()).isTrue();
            });
            assertThat(SwingUtilities.isDescendingFrom(hostsView, owner)).isTrue();
            assertThat(owner.regions().content(PanelRegion.LEFT)).isSameAs(hostsView);
            assertThat(owner.rail().buttons().get(0).isSelected()).isTrue();

            toggle(model, owner, "dev.x.files");
            assertThat(owner.regions().content(PanelRegion.LEFT)).isNotSameAs(hostsView);
            assertThat(owner.rail().buttons()).extracting(AbstractButton::isSelected).containsExactly(false, true);
            toggle(model, owner, "dev.x.hosts");
            assertThat(sites).as("the instance is reused").hasSize(1);
            sites.get(0).hide();
            assertThat(owner.regions().content(PanelRegion.LEFT)).isNull();
            assertThat(events).containsExactly("hosts:true", "hosts:false", "hosts:true", "hosts:false");

            model.requestPanel(new Contributions.PanelRequest(java.util.UUID.randomUUID(), "dev.x.hosts", Contributions.PanelRequest.Op.SHOW));
            assertThat(owner.regions().content(PanelRegion.LEFT)).as("another window's request").isNull();

            files.close();
            assertThat(owner.rail().buttons()).hasSize(1);
        });
    }

    @Test void movesPersistAndANewWindowRestoresThem() throws Exception {
        edt(() -> {
            var model = new Contributions();
            Path file = dir.resolve("ui-state.toml");
            WindowContent first = window();
            first.connectContributions(model, UiState.load(file));
            List<String> created = new ArrayList<>();
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), PanelRegion.LEFT, site -> { created.add("hosts"); return new JLabel("hosts"); });
            toggle(model, first, "dev.x.hosts");
            first.rail().onMove.accept("dev.x.hosts", PanelRegion.RIGHT);
            assertThat(first.regions().content(PanelRegion.LEFT)).isNull();
            assertThat(first.regions().content(PanelRegion.RIGHT)).isNotNull();
            assertThat(UiState.load(file).panel("dev.x.hosts")).get().satisfies(panel -> {
                assertThat(panel.region()).isEqualTo("RIGHT");
                assertThat(panel.visible()).isTrue();
            });

            WindowContent second = window();
            second.connectContributions(model, UiState.load(file));
            assertThat(second.regions().content(PanelRegion.RIGHT)).as("restored visible on the right").isNotNull();
            assertThat(created).hasSize(2);
        });
    }

    @Test void aFailedFactoryShowsAPlaceholderAndRemovalOrCloseNotifiesTheInstance() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            List<String> events = new ArrayList<>();
            model.addPanel("dev.x.broken", "Broken", new ImageIcon(), PanelRegion.BOTTOM, site -> null);
            PanelEntry live = model.addPanel("dev.x.live", "Live", new ImageIcon(), PanelRegion.RIGHT, site -> {
                site.onClosed(() -> events.add("closed"));
                return new JLabel("live");
            });
            toggle(model, owner, "dev.x.broken");
            JComponent placeholder = owner.regions().content(PanelRegion.BOTTOM);
            assertThat(placeholder).isInstanceOf(JLabel.class);
            assertThat(((JLabel) placeholder).getText()).contains("could not be loaded");

            toggle(model, owner, "dev.x.live");
            live.close();
            assertThat(owner.regions().content(PanelRegion.RIGHT)).isNull();
            assertThat(events).containsExactly("closed");
        });
    }

    @Test void railActionsAndTheRailToggleCommand() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var state = UiState.inMemory();
            WindowContent owner = window();
            owner.connectContributions(model, state);
            owner.setActive(true);
            List<Contributions.Invocation> seen = new ArrayList<>();
            model.addAction("dev.x.open", "Open Manager", new ImageIcon(), List.of(), Optional.empty(), seen::add);
            model.addRailAction("dev.x.open");
            model.addRailAction("dev.x.never-registered");
            assertThat(owner.rail().isVisible()).isTrue();
            assertThat(owner.rail().buttons()).singleElement().satisfies(button -> {
                assertThat(button.getToolTipText()).isEqualTo("Open Manager");
                button.doClick();
            });
            assertThat(seen).hasSize(1);

            owner.dispatchCommand(owner.commands().find("view.rail").orElseThrow());
            assertThat(owner.rail().isVisible()).isFalse();
            assertThat(state.railVisible()).isFalse();
            owner.setRailVisible(true);
            assertThat(owner.rail().isVisible()).isTrue();
        });
    }
}
```

`owner.setActive(true)` is needed because view commands run only in the active window.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*WindowPanelsTest'`
Expected: compilation FAILS (`rail()`, `regions()` not found).

- [ ] **Step 3: Write `WindowRail`**

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.commands.Command;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.platform.AppIcons;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

/**
 * The single icon rail on the window's left edge: a toggle per panel grouped by region, then plain
 * action buttons, with Settings pinned at the bottom. It owns no state; the window re-renders it.
 */
final class WindowRail extends JPanel {
    record PanelButton(String id, String title, Icon icon, PanelRegion region, boolean selected) { }

    Consumer<String> onToggle = id -> { };
    BiConsumer<String, PanelRegion> onMove = (id, region) -> { };
    private final List<AbstractButton> buttons = new ArrayList<>();
    private final JButton settings;

    WindowRail(Action settingsAction) {
        super(null);
        settings = new JButton(settingsAction);
        style(settings, AppIcons.icon("settings"), (String) settingsAction.getValue(Action.NAME));
        getAccessibleContext().setAccessibleName("Window tools");
        render(List.of(), List.of());
        refreshTheme();
    }

    private static void style(AbstractButton button, Icon icon, String name) {
        button.setHideActionText(true);
        button.setText(null);
        button.setIcon(icon);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusable(false);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
    }

    void render(List<PanelButton> panels, List<Action> actions) {
        removeAll();
        buttons.clear();
        PanelRegion previous = null;
        for (PanelRegion region : PanelRegion.values()) {
            for (PanelButton panel : panels) {
                if (panel.region() != region) continue;
                if (previous != null && previous != region) add(new JSeparator());
                previous = region;
                var button = new JToggleButton();
                style(button, panel.icon(), panel.title());
                button.setSelected(panel.selected());
                button.addActionListener(event -> onToggle.accept(panel.id()));
                var menu = new JPopupMenu();
                for (PanelRegion target : PanelRegion.values()) {
                    if (target == region) continue;
                    String label = "Move to " + target.name().charAt(0) + target.name().substring(1).toLowerCase(java.util.Locale.ROOT);
                    var item = new JMenuItem(label);
                    item.addActionListener(event -> onMove.accept(panel.id(), target));
                    menu.add(item);
                }
                button.setComponentPopupMenu(menu);
                buttons.add(button); add(button);
            }
        }
        if (!actions.isEmpty() && !buttons.isEmpty()) add(new JSeparator());
        for (Action action : actions) {
            var button = new JButton(action);
            Icon icon = action.getValue(Command.ICON) instanceof Icon contributed ? contributed : AppIcons.icon("command");
            style(button, icon, (String) action.getValue(Action.NAME));
            buttons.add(button); add(button);
        }
        add(settings);
        revalidate(); repaint();
    }

    /** True when there is nothing but Settings, in which case the window hides the rail. */
    boolean empty() { return buttons.isEmpty(); }

    List<AbstractButton> buttons() { return List.copyOf(buttons); }

    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(36), UIScale.scale(80)); }
    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        int x = UIScale.scale(3), side = UIScale.scale(30), y = UIScale.scale(4);
        for (Component child : getComponents()) {
            if (child == settings) continue;
            if (child instanceof JSeparator) {
                child.setBounds(UIScale.scale(7), y + UIScale.scale(3), UIScale.scale(22), UIScale.scale(1));
                y += UIScale.scale(8);
            } else {
                child.setBounds(x, y, side, side);
                y += UIScale.scale(34);
            }
        }
        settings.setBounds(x, Math.max(y, getHeight() - UIScale.scale(34)), side, side);
    }

    void refreshTheme() {
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, UIScale.scale(1), UIManager.getColor("Separator.foreground")));
        repaint();
    }
}
```

- [ ] **Step 4: Manage panels in `WindowContributions`**

Change the constructor to `WindowContributions(WindowContent owner, Contributions model, UiState state)` and add imports `dev.jasper.app.contributions.PanelEntry`, `PanelRegion`, `PanelSite`, `dev.jasper.app.persistence.UiState`, `javax.swing.JComponent`, `javax.swing.JLabel`, `javax.swing.SwingConstants`, `javax.swing.SwingUtilities`. Add:

```java
    private static final class PanelInstance {
        final PanelEntry entry;
        PanelRegion region;
        boolean visible;
        int size;
        PanelSite site;
        JComponent component;
        PanelInstance(PanelEntry entry) { this.entry = entry; }
    }

    private final UiState state;
    private final Map<String, PanelInstance> panels = new LinkedHashMap<>();
    private Subscription panelRequests;
```

At the end of the constructor (after `renderStatus();`):

```java
        owner.rail().onToggle = this::togglePanel;
        owner.rail().onMove = this::movePanel;
        panelRequests = model.onPanelRequest(this::requested);
        syncPanels();
        renderRail();
```

and assign `this.state = state;` with the other fields. Add the behavior:

```java
    private void requested(Contributions.PanelRequest request) {
        if (!request.windowId().equals(owner.id())) return;
        switch (request.op()) {
            case SHOW -> showPanel(request.panelId());
            case HIDE -> hidePanel(request.panelId());
            case TOGGLE -> togglePanel(request.panelId());
        }
    }

    private void togglePanel(String id) {
        PanelInstance panel = panels.get(id);
        if (panel == null) return;
        if (panel.visible) hidePanel(id); else showPanel(id);
    }

    /** Adds instances for new panels, restoring their saved place, and discards instances of removed panels. */
    private void syncPanels() {
        Map<String, PanelEntry> current = new LinkedHashMap<>();
        for (PanelEntry entry : model.panels()) current.put(entry.id(), entry);
        for (String id : List.copyOf(panels.keySet())) {
            if (current.containsKey(id)) continue;
            PanelInstance gone = panels.remove(id);
            if (gone.visible) owner.regions().hide(gone.region);
            if (gone.site != null) gone.site.notifyClosed();
        }
        for (PanelEntry entry : current.values()) {
            if (panels.containsKey(entry.id())) continue;
            var panel = new PanelInstance(entry);
            var saved = state.panel(entry.id());
            panel.region = saved.map(value -> PanelRegion.valueOf(value.region())).orElse(entry.defaultRegion());
            panel.size = saved.map(UiState.Panel::size).orElse(owner.regions().size(panel.region));
            panels.put(entry.id(), panel);
            if (saved.map(UiState.Panel::visible).orElse(false)) showPanel(entry.id());
        }
    }

    void showPanel(String id) {
        PanelInstance panel = panels.get(id);
        if (panel == null || panel.visible) return;
        for (PanelInstance other : panels.values())
            if (other != panel && other.visible && other.region == panel.region) hidePanel(other.entry.id());
        if (panel.component == null) {
            panel.site = new PanelSite(owner.id(), () -> showPanel(id), () -> hidePanel(id), () -> panel.visible);
            JComponent built = null;
            try { built = panel.entry.factory().apply(panel.site); }
            catch (RuntimeException failure) { /* The contributor's adapter has logged it; the window only needs a placeholder. */ }
            panel.component = built != null ? built : new JLabel("This panel could not be loaded.", SwingConstants.CENTER);
        }
        owner.regions().show(panel.region, panel.component, panel.size);
        panel.visible = true;
        persist(panel);
        renderRail();
        panel.site.notifyVisibility(true);
    }

    void hidePanel(String id) {
        PanelInstance panel = panels.get(id);
        if (panel == null || !panel.visible) return;
        panel.size = owner.regions().size(panel.region);
        owner.regions().hide(panel.region);
        panel.visible = false;
        persist(panel);
        renderRail();
        panel.site.notifyVisibility(false);
        if (owner.currentTab() != null) owner.currentTab().focusTerminal();
    }

    void movePanel(String id, PanelRegion region) {
        PanelInstance panel = panels.get(id);
        if (panel == null || panel.region == region) return;
        if (panel.visible) {
            // A move is not a change of visibility, so the instance hears nothing; only the region changes.
            for (PanelInstance other : panels.values())
                if (other != panel && other.visible && other.region == region) hidePanel(other.entry.id());
            owner.regions().hide(panel.region);
            panel.region = region;
            panel.size = owner.regions().size(region);
            owner.regions().show(region, panel.component, panel.size);
        } else {
            panel.region = region;
            panel.size = owner.regions().size(region);
        }
        persist(panel);
        renderRail();
    }

    private void persist(PanelInstance panel) {
        state.putPanel(panel.entry.id(), new UiState.Panel(panel.region.name(), panel.visible, Math.clamp(panel.size, 80, 4000)));
        state.save();
    }

    private void renderRail() {
        List<WindowRail.PanelButton> buttons = new ArrayList<>();
        for (PanelInstance panel : panels.values())
            buttons.add(new WindowRail.PanelButton(panel.entry.id(), panel.entry.title(), panel.entry.icon(), panel.region, panel.visible));
        List<Action> railActions = new ArrayList<>();
        for (String id : model.railActions()) if (actions.get(id) != null) railActions.add(actions.get(id));
        owner.rail().render(buttons, railActions);
        owner.syncRailVisibility();
    }

    /** Panel content that is not showing is outside the window's component tree and must be updated by hand. */
    void refreshTheme() {
        for (PanelInstance panel : panels.values())
            if (panel.component != null && !panel.visible) SwingUtilities.updateComponentTreeUI(panel.component);
    }
```

In `changed`, add `case PANELS -> { syncPanels(); renderRail(); }` and `case RAIL -> renderRail();`, and call `renderRail();` at the end of the `ACTIONS` branch (a rail action may have appeared or vanished). In `close()`, before `actions.clear();`:

```java
        if (panelRequests != null) { panelRequests.close(); panelRequests = null; }
        for (PanelInstance panel : panels.values()) {
            if (panel.visible) { panel.size = owner.regions().size(panel.region); state.putPanel(panel.entry.id(),
                new UiState.Panel(panel.region.name(), true, Math.clamp(panel.size, 80, 4000))); }
            if (panel.site != null) panel.site.notifyClosed();
        }
        state.save();
        panels.clear();
        owner.rail().onToggle = id -> { }; owner.rail().onMove = (id, region) -> { };
        owner.rail().render(List.of(), List.of());
        owner.syncRailVisibility();
```

- [ ] **Step 5: Integrate with `WindowContent`, `WindowCommands` and `WindowChrome`**

In `WindowContent` add imports `dev.jasper.app.persistence.UiState` and fields:

```java
    private final WorkspaceRegions regions;
    private final WindowRail rail;
    private UiState uiState = UiState.inMemory();
```

In the constructor replace `add(north, BorderLayout.NORTH); add(tabs); add(chrome.status(), BorderLayout.SOUTH);` with:

```java
        regions = new WorkspaceRegions(tabs);
        rail = new WindowRail(action(ActionId.OPEN_SETTINGS));
        rail.setVisible(false);
        var body = new JPanel(new BorderLayout());
        body.add(rail, BorderLayout.WEST); body.add(regions, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH); add(body); add(chrome.status(), BorderLayout.SOUTH);
```

Replace `connectContributions` with the two forms:

```java
    /** Connects with layout state that lives only as long as this window. */
    public void connectContributions(Contributions model) { connectContributions(model, UiState.inMemory()); }

    /**
     * Connects this window to the application-wide contributions model, once, remembering panel
     * placement and rail visibility in {@code state}.
     */
    public void connectContributions(Contributions model, UiState state) {
        if (closed || contributed != null) return;
        uiState = Objects.requireNonNull(state);
        contributed = new WindowContributions(this, Objects.requireNonNull(model), state);
        rebind();
    }

    WorkspaceRegions regions() { return regions; }
    WindowRail rail() { return rail; }
    boolean railVisible() { return uiState.railVisible(); }

    void setRailVisible(boolean value) {
        uiState.setRailVisible(value);
        uiState.save();
        syncRailVisibility();
        updateActions();
    }

    /** The rail shows only when it has something besides Settings and the user has not hidden it. */
    void syncRailVisibility() {
        boolean wanted = !rail.empty() && uiState.railVisible();
        if (rail.isVisible() != wanted) { rail.setVisible(wanted); revalidate(); repaint(); }
    }
```

In `applyTheme`, after the `updateComponentTreeUI` calls, add `rail.refreshTheme(); if (updateDelegates && contributed != null) contributed.refreshTheme();`.

In `WindowCommands`, after the `view.status_bar` line:

```java
        add(registry, "view.rail", "Rail", "Hide Rail", () -> owner.setRailVisible(!owner.railVisible()), List.of("panels", "sidebar"));
```

and in `refresh()`:

```java
        view("view.rail").putValue(Command.TITLE, owner.railVisible() ? "Hide Rail" : "Show Rail");
        view("view.rail").putValue(Action.SELECTED_KEY, owner.railVisible());
```

In `WindowChrome`, add a field `private final JCheckBoxMenuItem railVisible = new JCheckBoxMenuItem("Rail", true);`, change `view.add(modes); view.add(statusVisible); view.add(buddyVisible);` to `view.add(modes); view.add(statusVisible); view.add(railVisible); view.add(buddyVisible);` and set its action beside the others: `railVisible.setAction(owner.windowCommands().view("view.rail"));`.

The View menu gained one built-in item. `WindowChromeContributionsTest` measures `viewBefore` at runtime, so it is unaffected; if another pre-existing test hard-codes the View menu's item count or order, update that expectation and record it.

In `workspace/package-info.java` add `dev.jasper.app.persistence` to the allowed outgoing dependencies, after `dev.jasper.app.palette.builtin`.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.palette.*' verifyApplicationArchitecture`
Expected: PASS, including every pre-existing workspace, layout and preview test: an unconnected window's deck now sits inside `body` → `regions`, both `BorderLayout` centers, so its bounds are unchanged.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: add the rail and per-window panels with remembered placement

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 6: Auxiliary windows: a headless core and a native shell

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/windows/{package-info,AuxiliarySurface,AuxiliaryWindows,NativeShells}.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalWindow.java` (one accessor)
- Test: `jasper-app/src/test/java/dev/jasper/app/windows/AuxiliaryWindowsTest.java`

**Interfaces:**
- Produces (public, EDT only, no SDK types):
  - `AuxiliarySurface`: `record Shell(Runnable show, Runnable toFront, Runnable dispose, Consumer<String> title, Supplier<Rectangle> bounds)`; `enum Kind { WINDOW, DIALOG }`; `String id()` (empty for dialogs), `Kind kind()`, `boolean modal()`, `Dimension preferredSize()`, `Optional<UUID> ownerWindow()`, `Optional<AuxiliarySurface> ownerSurface()`, `JComponent holder()`, `String title()`, `void setContent(JComponent)`, `void setTitle(String)`, `void show()`, `void toFront()`, `boolean shown()`, `boolean requestClose()`, `void close()`, `boolean closed()`, `Subscription onClosing(BooleanSupplier)`, `Subscription onClosed(Runnable)`
  - `AuxiliaryWindows(UiState state, Function<AuxiliarySurface, AuxiliarySurface.Shell> shells)`: `AuxiliarySurface window(String id, String title, Dimension preferred, boolean singleton)`, `AuxiliarySurface dialog(String title, boolean modal, UUID ownerWindow)`, `AuxiliarySurface dialog(String title, boolean modal, AuxiliarySurface owner)`, `List<AuxiliarySurface> open()`, `void close()`
  - `NativeShells(ThemeController themes, UiState state, Function<UUID, java.awt.Window> terminalWindows, Runnable newWindow, Runnable quit)` with `AuxiliarySurface.Shell create(AuxiliarySurface surface)` — the native boundary, not unit-tested, like `TerminalWindow`
  - `TerminalWindow.nativeWindow()` → `java.awt.Window`

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.windows;

import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class AuxiliaryWindowsTest {
    private final List<String> shellEvents = new ArrayList<>();
    private final UiState state = UiState.inMemory();
    private final AuxiliaryWindows windows = new AuxiliaryWindows(state, surface -> {
        shellEvents.add("create:" + surface.title());
        return new AuxiliarySurface.Shell(() -> shellEvents.add("show"), () -> shellEvents.add("front"),
            () -> shellEvents.add("dispose"), title -> shellEvents.add("title:" + title), () -> new Rectangle(10, 20, 640, 480));
    });

    @Test void theShellIsCreatedOnFirstShowAndFollowsTheSurface() {
        AuxiliarySurface manager = windows.window("dev.x.manager", "Manager", new Dimension(640, 480), true);
        var content = new JLabel("content");
        manager.setContent(content);
        manager.setTitle("Manager (2)");
        assertThat(shellEvents).as("nothing native before show").isEmpty();
        assertThat(content.getParent()).isSameAs(manager.holder());
        manager.show(); manager.show(); manager.toFront();
        manager.setTitle("Manager (3)");
        assertThat(shellEvents).containsExactly("create:Manager (2)", "show", "show", "front", "title:Manager (3)");
        assertThat(manager.shown()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> manager.setTitle(" "));
    }

    @Test void singletonsAreReusedWhileOpenAndBoundsAreRememberedOnClose() {
        AuxiliarySurface first = windows.window("dev.x.manager", "Manager", new Dimension(640, 480), true);
        assertThat(windows.window("dev.x.manager", "Manager", new Dimension(1, 1), true)).isSameAs(first);
        assertThat(windows.window("dev.x.manager", "Another", new Dimension(640, 480), false)).isNotSameAs(first);
        first.show();
        first.close(); first.close();
        assertThat(shellEvents).containsOnlyOnce("dispose");
        assertThat(state.window("dev.x.manager")).hasValue(new UiState.Bounds(10, 20, 640, 480));
        assertThat(windows.window("dev.x.manager", "Manager", new Dimension(640, 480), true)).isNotSameAs(first);
        assertThat(windows.open()).hasSize(2);
    }

    @Test void guardsVetoAUserCloseButNotAProgrammaticOne() {
        AuxiliarySurface manager = windows.window("dev.x.manager", "Manager", new Dimension(640, 480), true);
        List<String> events = new ArrayList<>();
        var veto = manager.onClosing(() -> false);
        manager.onClosing(() -> { throw new IllegalStateException("guard failure"); });
        manager.onClosed(() -> events.add("closed"));
        assertThat(manager.requestClose()).isFalse();
        assertThat(manager.closed()).isFalse();
        veto.close();
        assertThat(manager.requestClose()).as("a throwing guard cannot trap the window open").isTrue();
        assertThat(manager.requestClose()).isTrue();
        assertThat(events).containsExactly("closed");

        AuxiliarySurface stubborn = windows.window("dev.x.stubborn", "Stubborn", new Dimension(10, 10), false);
        stubborn.onClosing(() -> false);
        stubborn.close();
        assertThat(stubborn.closed()).isTrue();
    }

    @Test void dialogsNeedALiveOwnerAndCloseAllClosesEverything() {
        AuxiliarySurface manager = windows.window("dev.x.manager", "Manager", new Dimension(640, 480), true);
        AuxiliarySurface prompt = windows.dialog("Unlock", true, manager);
        UUID terminalWindow = UUID.randomUUID();
        AuxiliarySurface trust = windows.dialog("Trust host?", false, terminalWindow);
        assertThat(prompt.kind()).isEqualTo(AuxiliarySurface.Kind.DIALOG);
        assertThat(prompt.modal()).isTrue();
        assertThat(prompt.ownerSurface()).containsSame(manager);
        assertThat(trust.ownerWindow()).contains(terminalWindow);
        assertThat(windows.open()).containsExactly(manager, prompt, trust);

        manager.onClosing(() -> false);
        windows.close();
        assertThat(windows.open()).isEmpty();
        assertThat(manager.closed() && prompt.closed() && trust.closed()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> windows.dialog("Late", true, manager));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*AuxiliaryWindowsTest'`
Expected: compilation FAILS, `AuxiliaryWindows` not found.

- [ ] **Step 3: Write the core**

`package-info.java`:

```java
/**
 * Application-built auxiliary windows and dialogs with consistent chrome. {@code AuxiliarySurface} is
 * the headless core; {@code NativeShells} is the only class here that creates frames and dialogs.
 * The application owns {@code AuxiliaryWindows} and closes it at shutdown; creators close their own surfaces.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.appearance, dev.jasper.app.lifecycle, dev.jasper.app.persistence, dev.jasper.app.platform.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.windows;
```

`AuxiliarySurface.java`:

```java
package dev.jasper.app.windows;

import dev.jasper.app.lifecycle.Subscription;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Everything about an auxiliary window that does not need a native frame: its content holder, title,
 * closing guards and lifetime. The native shell is created on first show. EDT only.
 */
public final class AuxiliarySurface {
    /** The native side, as plain functions so tests can stand in for a frame. */
    public record Shell(Runnable show, Runnable toFront, Runnable dispose, Consumer<String> title, Supplier<Rectangle> bounds) { }

    /** Whether this is a top-level window or a dialog over another window. */
    public enum Kind { WINDOW, DIALOG }

    private static final System.Logger LOG = System.getLogger(AuxiliarySurface.class.getName());
    private final String id;
    private final Kind kind;
    private final boolean modal;
    private final Dimension preferredSize;
    private final UUID ownerWindow;
    private final AuxiliarySurface ownerSurface;
    private final Function<AuxiliarySurface, Shell> shells;
    private final JPanel holder = new JPanel(new BorderLayout());
    private final List<BooleanSupplier> guards = new ArrayList<>();
    private final List<Runnable> closedListeners = new ArrayList<>();
    private String title;
    private Shell shell;
    private boolean shown;
    private boolean closed;
    private Rectangle lastBounds;

    AuxiliarySurface(String id, String title, Kind kind, boolean modal, Dimension preferredSize, UUID ownerWindow,
                     AuxiliarySurface ownerSurface, Function<AuxiliarySurface, Shell> shells) {
        this.id = id; this.kind = kind; this.modal = modal; this.preferredSize = new Dimension(preferredSize);
        this.ownerWindow = ownerWindow; this.ownerSurface = ownerSurface; this.shells = shells;
        this.title = requireTitle(title);
    }

    private static String requireTitle(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A window needs a title");
        return value;
    }

    public String id() { return id; }
    public Kind kind() { return kind; }
    public boolean modal() { return modal; }
    public Dimension preferredSize() { return new Dimension(preferredSize); }
    public Optional<UUID> ownerWindow() { return Optional.ofNullable(ownerWindow); }
    public Optional<AuxiliarySurface> ownerSurface() { return Optional.ofNullable(ownerSurface); }
    /** The stable content pane the native shell adopts; {@link #setContent} swaps its child. */
    public JComponent holder() { return holder; }
    public String title() { return title; }
    public boolean shown() { return shown && !closed; }
    public boolean closed() { return closed; }
    Optional<Rectangle> lastBounds() { return Optional.ofNullable(lastBounds); }

    public void setContent(JComponent content) {
        holder.removeAll();
        if (content != null) holder.add(content, BorderLayout.CENTER);
        holder.revalidate(); holder.repaint();
    }

    public void setTitle(String value) {
        title = requireTitle(value);
        if (shell != null && !closed) shell.title().accept(value);
    }

    /** Creates the native shell on first use. For a modal dialog this returns after the dialog closed. */
    public void show() {
        if (closed) return;
        if (shell == null) shell = shells.apply(this);
        shown = true;
        shell.show().run();
    }

    public void toFront() {
        if (!closed && shell != null) shell.toFront().run();
    }

    /** A user's attempt to close: every guard is consulted; one that throws cannot trap the window open. */
    public boolean requestClose() {
        if (closed) return true;
        for (BooleanSupplier guard : List.copyOf(guards)) {
            try { if (!guard.getAsBoolean()) return false; }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A window closing guard failed", failure); }
        }
        close();
        return true;
    }

    /** Closes at once, without consulting guards. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        if (shell != null) {
            try { lastBounds = shell.bounds().get(); } catch (RuntimeException ignored) { lastBounds = null; }
            shell.dispose().run();
        }
        guards.clear();
        for (Runnable listener : List.copyOf(closedListeners)) {
            try { listener.run(); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A window closed listener failed", failure); }
        }
        closedListeners.clear();
    }

    public Subscription onClosing(BooleanSupplier guard) {
        guards.add(java.util.Objects.requireNonNull(guard));
        return new Subscription(() -> guards.remove(guard));
    }

    public Subscription onClosed(Runnable listener) {
        closedListeners.add(java.util.Objects.requireNonNull(listener));
        return new Subscription(() -> closedListeners.remove(listener));
    }
}
```

`AuxiliaryWindows.java`:

```java
package dev.jasper.app.windows;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Creates and tracks auxiliary surfaces, reuses open singletons and remembers window bounds. EDT only. */
public final class AuxiliaryWindows implements AutoCloseable {
    private final UiState state;
    private final Function<AuxiliarySurface, AuxiliarySurface.Shell> shells;
    private final List<AuxiliarySurface> open = new ArrayList<>();

    public AuxiliaryWindows(UiState state, Function<AuxiliarySurface, AuxiliarySurface.Shell> shells) {
        this.state = Objects.requireNonNull(state);
        this.shells = Objects.requireNonNull(shells);
    }

    public AuxiliarySurface window(String id, String title, Dimension preferred, boolean singleton) {
        if (singleton)
            for (AuxiliarySurface existing : open)
                if (existing.kind() == AuxiliarySurface.Kind.WINDOW && existing.id().equals(id)) return existing;
        return track(new AuxiliarySurface(id, title, AuxiliarySurface.Kind.WINDOW, false, preferred, null, null, shells));
    }

    public AuxiliarySurface dialog(String title, boolean modal, UUID ownerWindow) {
        return track(new AuxiliarySurface("", title, AuxiliarySurface.Kind.DIALOG, modal, new Dimension(1, 1),
            Objects.requireNonNull(ownerWindow), null, shells));
    }

    public AuxiliarySurface dialog(String title, boolean modal, AuxiliarySurface owner) {
        if (owner.closed() || !open.contains(owner)) throw new IllegalArgumentException("A dialog needs an open owner window");
        return track(new AuxiliarySurface("", title, AuxiliarySurface.Kind.DIALOG, modal, new Dimension(1, 1), null, owner, shells));
    }

    private AuxiliarySurface track(AuxiliarySurface surface) {
        open.add(surface);
        surface.onClosed(() -> {
            open.remove(surface);
            // Dialogs die with their owner, as native dialogs do.
            for (AuxiliarySurface other : List.copyOf(open))
                if (other.ownerSurface().filter(owner -> owner == surface).isPresent()) other.close();
            if (surface.kind() != AuxiliarySurface.Kind.WINDOW) return;
            surface.lastBounds().ifPresent(bounds -> {
                if (bounds.width <= 0 || bounds.height <= 0) return;
                state.putWindow(surface.id(), new UiState.Bounds(bounds.x, bounds.y, bounds.width, bounds.height));
                state.save();
            });
        });
        return surface;
    }

    public List<AuxiliarySurface> open() { return List.copyOf(open); }

    /** Shutdown: closes everything, newest first, without consulting guards. */
    @Override public void close() {
        List<AuxiliarySurface> closing = new ArrayList<>(open);
        for (int i = closing.size() - 1; i >= 0; i--) closing.get(i).close();
    }
}
```

- [ ] **Step 4: Write the native shell**

`NativeShells.java` constructs real frames, so it has no unit test; keep it thin and keep logic out of it.

```java
package dev.jasper.app.windows;

import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.app.appearance.BuiltinTheme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.platform.ApplicationIcon;
import dev.jasper.app.platform.MacTitleBar;
import java.awt.Dialog;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * The native boundary for auxiliary windows: frame or dialog, application icon, the unified macOS title
 * bar, a menu bar so the screen menu does not vanish, theme tracking and remembered bounds. Everything
 * testable lives in {@link AuxiliarySurface}. EDT only; never constructed in headless tests.
 */
public final class NativeShells {
    private static final int TITLE_HEIGHT = 38;
    private final ThemeController themes;
    private final UiState state;
    private final Function<UUID, Window> terminalWindows;
    private final Runnable newWindow;
    private final Runnable quit;
    private final Map<AuxiliarySurface, Window> natives = new HashMap<>();

    /**
     * Binds the shells to the application.
     *
     * @param themes the application's look
     * @param state remembered window bounds
     * @param terminalWindows finds a terminal window's native window by id, or returns null
     * @param newWindow opens a terminal window
     * @param quit quits the application
     */
    public NativeShells(ThemeController themes, UiState state, Function<UUID, Window> terminalWindows,
                        Runnable newWindow, Runnable quit) {
        this.themes = Objects.requireNonNull(themes); this.state = Objects.requireNonNull(state);
        this.terminalWindows = Objects.requireNonNull(terminalWindows);
        this.newWindow = Objects.requireNonNull(newWindow); this.quit = Objects.requireNonNull(quit);
    }

    /**
     * Builds the native window for a surface.
     *
     * @param surface the headless core
     * @return the functions the core drives
     */
    public AuxiliarySurface.Shell create(AuxiliarySurface surface) {
        return surface.kind() == AuxiliarySurface.Kind.WINDOW ? frame(surface) : dialog(surface);
    }

    private AuxiliarySurface.Shell frame(AuxiliarySurface surface) {
        var frame = new JFrame(surface.title());
        frame.setIconImages(ApplicationIcon.images(SystemInfo.isMacOS));
        MacTitleBar bar = MacTitleBar.install(frame.getRootPane(), surface.holder(), new JPanel(), () -> TITLE_HEIGHT,
            () -> { }, SystemInfo.isMacFullWindowContentSupported);
        if (bar != null) bar.setTitle(surface.title(), true);
        frame.setJMenuBar(menuBar(surface));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { surface.requestClose(); }
            @Override public void windowActivated(WindowEvent event) { if (bar != null) bar.setActive(true); }
            @Override public void windowDeactivated(WindowEvent event) { if (bar != null) bar.setActive(false); }
        });
        if (bar != null) bar.attach(frame);
        frame.pack();
        frame.setSize(surface.preferredSize());
        frame.setLocationByPlatform(true);
        state.window(surface.id()).map(saved -> new Rectangle(saved.x(), saved.y(), saved.width(), saved.height()))
            .filter(NativeShells::onSomeScreen).ifPresent(frame::setBounds);
        Subscription theme = themes.subscribe((resolved, chromeChanged) -> {
            if (chromeChanged) SwingUtilities.updateComponentTreeUI(frame);
            if (bar != null) bar.setLight(resolved.chrome() == BuiltinTheme.LIGHT);
        });
        natives.put(surface, frame);
        return new AuxiliarySurface.Shell(() -> frame.setVisible(true), () -> {
            if ((frame.getExtendedState() & java.awt.Frame.ICONIFIED) != 0) frame.setExtendedState(frame.getExtendedState() & ~java.awt.Frame.ICONIFIED);
            frame.toFront(); frame.requestFocus();
        }, () -> {
            theme.close();
            if (bar != null) bar.close();
            natives.remove(surface);
            frame.dispose();
        }, title -> { frame.setTitle(title); if (bar != null) bar.setTitle(title, true); }, frame::getBounds);
    }

    private AuxiliarySurface.Shell dialog(AuxiliarySurface surface) {
        Window owner = surface.ownerSurface().map(natives::get)
            .orElseGet(() -> surface.ownerWindow().map(terminalWindows).orElse(null));
        var dialog = new JDialog(owner, surface.title(), surface.modal() ? Dialog.ModalityType.DOCUMENT_MODAL : Dialog.ModalityType.MODELESS);
        dialog.setContentPane(surface.holder());
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { surface.requestClose(); }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        Subscription theme = themes.subscribe((resolved, chromeChanged) -> { if (chromeChanged) SwingUtilities.updateComponentTreeUI(dialog); });
        natives.put(surface, dialog);
        return new AuxiliarySurface.Shell(() -> { dialog.pack(); dialog.setLocationRelativeTo(owner); dialog.setVisible(true); },
            dialog::toFront, () -> { theme.close(); natives.remove(surface); dialog.dispose(); }, dialog::setTitle, dialog::getBounds);
    }

    /** A minimal menu bar: macOS otherwise shows only the application menu while this window has focus. */
    private JMenuBar menuBar(AuxiliarySurface surface) {
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        var file = new JMenu("File");
        var open = new JMenuItem("New Window");
        open.addActionListener(event -> newWindow.run());
        var close = new JMenuItem("Close Window");
        close.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcut));
        close.addActionListener(event -> surface.requestClose());
        file.add(open); file.add(close);
        if (!SystemInfo.isMacOS) {
            var exit = new JMenuItem("Quit");
            exit.addActionListener(event -> quit.run());
            file.addSeparator(); file.add(exit);
        }
        var bar = new JMenuBar();
        bar.add(file);
        return bar;
    }

    /** Remembered bounds are ignored when no current display shows them, for example after unplugging a monitor. */
    private static boolean onSomeScreen(Rectangle bounds) {
        for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
            if (device.getDefaultConfiguration().getBounds().intersects(bounds)) return true;
        return false;
    }
}
```

In `TerminalWindow`, beside `size()`:

```java
    /** The native window, for parenting application-built dialogs; never exposed to extensions. */
    public java.awt.Window nativeWindow() { return frame; }
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*AuxiliaryWindowsTest' --tests '*AppDocumentationTest' verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS, 4 tests; the new package has a contract; no cycle (`windows` → `appearance`, `lifecycle`, `persistence`, `platform`).

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: add application-built auxiliary windows with a headless core

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: SDK adapters and testkit fakes for panels, rail and windows

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/{HostedUi,package-info}.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{FakeUi,FakePluginContext,FakePluginHost}.java`
- Test: extend `jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java`; create `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePanelsAndWindowsTest.java`

**Interfaces:**
- Produces:
  - `HostedUi` constructor gains a final parameter `AuxiliaryWindows windows`; new methods `Panels panels()`, `Rail rail()`, `Windows windows()`
  - `PluginHost.Environment` gains a ninth component `AuxiliaryWindows windows`; `PluginRuntime`'s constructor gains a fifth parameter `AuxiliaryWindows windows` (Step 5)
  - `FakePluginContext` concrete methods `panels()`, `rail()`, `windows()` (they become `@Override`s in Task 8)
  - `FakePluginHost`, in **the rendering format Task 8's application harness must reproduce**:
    - `List<String> panels()` → `"<id>|<title>|<LEFT|RIGHT|BOTTOM>"`, registration order
    - `JComponent openPanel(String panelId, UUID windowId)` → invokes the factory as a window would; null when there is no such panel or the factory failed (the failure is recorded)
    - `List<String> rail()` → action ids in placement order; closed actions omitted
    - `List<String> windows()` → `"<id or dialog>|<title>|<shown>"` for every open window and dialog, creation order
    - `boolean requestClose(String windowId)` → as the user would close the first open window with that id; false when vetoed or absent
  - The fake does not model the application's own `<panel id>.toggle` action or View → Panels submenu: those are application chrome, not plugin contributions.

- [ ] **Step 1: Write the failing tests**

Append to `HostedUiTest` (the field initializer for `ui` gains `, auxiliary` as its last constructor argument, with `private final dev.jasper.app.persistence.UiState uiState = dev.jasper.app.persistence.UiState.inMemory();` and `private final dev.jasper.app.windows.AuxiliaryWindows auxiliary = new dev.jasper.app.windows.AuxiliaryWindows(uiState, surface -> new dev.jasper.app.windows.AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { }, () -> new java.awt.Rectangle(0, 0, 10, 10)));` declared above it; do the same for the second `HostedUi` in `registrationOffTheUiThreadIsRejectedAndCloseIsPosted`):

```java
    @Test void panelsAndRailReachTheModelWithContainedFactories() {
        ui.actions().register(ActionSpec.of("dev.x.tool.open", "Open"), context -> { });
        List<dev.jasper.sdk.ui.PanelHost> hosts = new ArrayList<>();
        Subscription panel = ui.panels().register(new dev.jasper.sdk.ui.PanelSpec("dev.x.tool.hosts", "Hosts", new javax.swing.ImageIcon(),
            dev.jasper.sdk.ui.Anchor.RIGHT), host -> { hosts.add(host); return new javax.swing.JLabel("hosts"); });
        ui.panels().register(new dev.jasper.sdk.ui.PanelSpec("dev.x.tool.broken", "Broken", new javax.swing.ImageIcon(),
            dev.jasper.sdk.ui.Anchor.LEFT), host -> { throw new IllegalStateException("factory failure"); });
        assertThatIllegalArgumentException().isThrownBy(() -> ui.panels().register(new dev.jasper.sdk.ui.PanelSpec("dev.other.panel", "Foreign",
            new javax.swing.ImageIcon(), dev.jasper.sdk.ui.Anchor.LEFT), host -> new javax.swing.JLabel()));

        var entry = model.panels().get(0);
        assertThat(entry.defaultRegion()).isEqualTo(dev.jasper.app.contributions.PanelRegion.RIGHT);
        UUID window = UUID.randomUUID();
        boolean[] visible = {true};
        var site = new dev.jasper.app.contributions.PanelSite(window, () -> visible[0] = true, () -> visible[0] = false, () -> visible[0]);
        assertThat(entry.factory().apply(site)).isInstanceOf(javax.swing.JLabel.class);
        assertThat(hosts).singleElement().satisfies(host -> {
            assertThat(host.window().id()).isEqualTo(window);
            host.hide();
            assertThat(host.visible()).isFalse();
        });
        assertThat(model.panels().get(1).factory().apply(site)).as("a failed factory yields no component").isNull();
        assertThat(containment.failures("dev.x.tool")).isEqualTo(1);

        ui.rail().add("dev.x.tool.open");
        assertThatIllegalArgumentException().isThrownBy(() -> ui.rail().add("new_tab"));
        assertThat(model.railActions()).containsExactly("dev.x.tool.open");
        panel.close();
        assertThat(model.panels()).hasSize(1);
        ui.closeAll();
        assertThat(model.panels()).isEmpty();
        assertThat(model.railActions()).isEmpty();
    }

    @Test void windowsAndDialogsAreBuiltByTheApplicationAndClosedWithThePlugin() {
        var manager = ui.windows().create(new dev.jasper.sdk.ui.WindowSpec("dev.x.tool.manager", "Manager", new java.awt.Dimension(640, 480), true));
        assertThat(ui.windows().create(new dev.jasper.sdk.ui.WindowSpec("dev.x.tool.manager", "Manager", new java.awt.Dimension(640, 480), true)))
            .as("an open singleton is the same window").isSameAs(manager);
        assertThatIllegalArgumentException().isThrownBy(() ->
            ui.windows().create(new dev.jasper.sdk.ui.WindowSpec("dev.other.window", "Foreign", new java.awt.Dimension(10, 10), false)));
        manager.setContent(new javax.swing.JLabel("content"));
        manager.show();
        List<String> events = new ArrayList<>();
        manager.onClosing(() -> { throw new IllegalStateException("guard failure"); });
        manager.onClosed(() -> events.add("closed"));
        var prompt = ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Unlock", manager, true));
        var trust = ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Trust?", (dev.jasper.sdk.terminal.WindowHandle) UUID::randomUUID, false));
        assertThatIllegalArgumentException().as("an owner this application did not create")
            .isThrownBy(() -> ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Bad", new dev.jasper.sdk.WindowOwner() { }, true)));
        assertThat(auxiliary.open()).hasSize(3);
        assertThat(auxiliary.open().get(0).requestClose()).as("a throwing guard is contained and allows the close").isTrue();
        assertThat(events).containsExactly("closed");
        assertThat(containment.failures("dev.x.tool")).isEqualTo(1);
        assertThat(auxiliary.open()).as("the owner took its dialog with it").hasSize(1);
        ui.closeAll();
        assertThat(auxiliary.open()).isEmpty();
        assertThatCode(() -> { prompt.close(); trust.close(); manager.setTitle("after close"); }).doesNotThrowAnyException();
    }
```

`jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePanelsAndWindowsTest.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.WindowSpec;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakePanelsAndWindowsTest {
    private static final PluginInfo INFO = new PluginInfo("dev.x.tool", "Tool", "1.0.0", Set.of());

    @Test void recordsPanelsRailAndWindows() {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> { });
            List<PanelHost> hosts = new ArrayList<>();
            context.actions().register(ActionSpec.of("dev.x.tool.open", "Open"), invoked -> { });
            context.panels().register(new PanelSpec("dev.x.tool.hosts", "Hosts", new ImageIcon(), Anchor.RIGHT),
                panelHost -> { hosts.add(panelHost); return new JLabel("hosts"); });
            context.panels().register(new PanelSpec("dev.x.tool.broken", "Broken", new ImageIcon(), Anchor.LEFT),
                panelHost -> { throw new IllegalStateException("factory failure"); });
            context.rail().add("dev.x.tool.open");
            assertThat(host.panels()).containsExactly("dev.x.tool.hosts|Hosts|RIGHT", "dev.x.tool.broken|Broken|LEFT");
            assertThat(host.rail()).containsExactly("dev.x.tool.open");
            UUID window = UUID.randomUUID();
            assertThat(host.openPanel("dev.x.tool.hosts", window)).isInstanceOf(JLabel.class);
            assertThat(hosts.get(0).window().id()).isEqualTo(window);
            assertThat(hosts.get(0).visible()).isTrue();
            assertThat(host.openPanel("dev.x.tool.broken", window)).isNull();
            assertThat(host.openPanel("dev.x.tool.absent", window)).isNull();
            assertThat(host.failures()).hasSize(1);

            var manager = context.windows().create(new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(640, 480), true));
            assertThat(context.windows().create(new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(640, 480), true))).isSameAs(manager);
            var veto = manager.onClosing(() -> false);
            context.windows().dialog(new DialogSpec("Unlock", manager, true)).show();
            assertThat(host.windows()).containsExactly("dev.x.tool.manager|Manager|false", "dialog|Unlock|true");
            manager.show();
            assertThat(host.requestClose("dev.x.tool.manager")).isFalse();
            veto.close();
            assertThat(host.requestClose("dev.x.tool.manager")).isTrue();
            assertThat(host.windows()).as("the dialog went with its owner").isEmpty();
            assertThatIllegalArgumentException().isThrownBy(() -> context.windows().dialog(new DialogSpec("Late", manager, true)));
        }
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:compileTestJava :jasper-app:compileTestJava`
Expected: compilation FAILS (`panels()`, `windows()` not found; `HostedUi` constructor arity).

- [ ] **Step 3: Extend `HostedUi`**

Add the constructor parameter and field `private final AuxiliaryWindows windows;` (import `dev.jasper.app.windows.AuxiliaryWindows`, `dev.jasper.app.windows.AuxiliarySurface`, `dev.jasper.app.contributions.PanelRegion`, `dev.jasper.app.contributions.PanelSite`, the new SDK `ui` types, `dev.jasper.sdk.WindowOwner`, `java.util.function.BooleanSupplier`, `javax.swing.JComponent`), then:

```java
    private static WindowHandle handle(java.util.UUID id) { return () -> id; }

    /** Wraps an application subscription so closing it is contained to the UI thread like every other registration. */
    private Subscription wrap(dev.jasper.app.lifecycle.Subscription registration) { return subscription(registration::close); }

    Panels panels() {
        return (spec, factory) -> {
            guard("register");
            java.util.Objects.requireNonNull(factory, "factory");
            requireNamespace(spec.id(), "A panel");
            var entry = model.addPanel(spec.id(), spec.title(), spec.icon(), PanelRegion.valueOf(spec.defaultAnchor().name()), site -> {
                JComponent[] built = {null};
                containment.run(pluginId, "panel " + spec.id(), () -> built[0] = factory.create(host(site)));
                return built[0];
            });
            return tracked(entry::close);
        };
    }

    private PanelHost host(PanelSite site) {
        return new PanelHost() {
            @Override public WindowHandle window() { return handle(site.windowId()); }
            @Override public void show() { requireUi("show"); site.show(); }
            @Override public void hide() { requireUi("hide"); site.hide(); }
            @Override public boolean visible() { return site.visible(); }
            @Override public Subscription onVisibility(Consumer<Boolean> handler) {
                requireUi("onVisibility");
                return wrap(site.onVisibility(value -> containment.run(pluginId, "panel visibility", () -> handler.accept(value))));
            }
            @Override public Subscription onClosed(Runnable handler) {
                requireUi("onClosed");
                return wrap(site.onClosed(() -> containment.run(pluginId, "panel closed", handler)));
            }
        };
    }

    Rail rail() {
        return actionId -> {
            guard("add");
            requireOwn(actionId);
            var registration = model.addRailAction(actionId);
            return tracked(registration::close);
        };
    }

    /** One SDK view of an application surface; windows and dialogs differ only in their SDK type. */
    private class Surface implements WindowSurface {
        final AuxiliarySurface surface;
        Surface(AuxiliarySurface surface) { this.surface = surface; }
        @Override public void setContent(JComponent content) { requireUi("setContent"); surface.setContent(content); }
        @Override public void show() { requireUi("show"); surface.show(); }
        @Override public void toFront() { requireUi("toFront"); surface.toFront(); }
        @Override public void setTitle(String title) { requireUi("setTitle"); if (!surface.closed()) surface.setTitle(title); }
        @Override public Subscription onClosing(BooleanSupplier guard) {
            requireUi("onClosing");
            return wrap(surface.onClosing(() -> {
                boolean[] allowed = {true};
                containment.run(pluginId, "closing guard", () -> allowed[0] = guard.getAsBoolean());
                return allowed[0];
            }));
        }
        @Override public Subscription onClosed(Runnable handler) {
            requireUi("onClosed");
            return wrap(surface.onClosed(() -> containment.run(pluginId, "window closed", handler)));
        }
        @Override public void close() { subscription(surface::close).close(); }
    }

    private final class OwnedWindow extends Surface implements PluginWindow { OwnedWindow(AuxiliarySurface surface) { super(surface); } }
    private final class OwnedDialog extends Surface implements PluginDialog { OwnedDialog(AuxiliarySurface surface) { super(surface); } }

    private final java.util.Map<AuxiliarySurface, OwnedWindow> ownedWindows = new java.util.HashMap<>();

    Windows windows() {
        return new Windows() {
            @Override public PluginWindow create(WindowSpec spec) {
                guard("create");
                requireNamespace(spec.id(), "A window");
                AuxiliarySurface surface = windows.window(spec.id(), spec.title(), spec.preferredSize(), spec.singleton());
                OwnedWindow existing = ownedWindows.get(surface);
                if (existing != null) return existing;
                var window = new OwnedWindow(surface);
                ownedWindows.put(surface, window);
                surface.onClosed(() -> ownedWindows.remove(surface));
                closers.add(surface::close);
                return window;
            }
            @Override public PluginDialog dialog(DialogSpec spec) {
                guard("dialog");
                WindowOwner owner = spec.owner();
                AuxiliarySurface surface;
                if (owner instanceof OwnedWindow window && ownedWindows.get(window.surface) == window)
                    surface = windows.dialog(spec.title(), spec.modal(), window.surface);
                else if (owner instanceof WindowHandle terminalWindow) surface = windows.dialog(spec.title(), spec.modal(), terminalWindow.id());
                else throw new IllegalArgumentException("A dialog's owner must be an open window of this plugin or a terminal window");
                closers.add(surface::close);
                return new OwnedDialog(surface);
            }
        };
    }
```

A singleton id is namespaced by plugin, so another plugin can never be handed this plugin's window. `closers.add(surface::close)` is safe to leave behind after the window closes: `AuxiliarySurface.close` is idempotent.

In `plugins/package-info.java` add `dev.jasper.app.windows` to the allowed outgoing dependencies.

- [ ] **Step 4: Extend the fake**

In `FakeUi` add (imports for the new SDK `ui` types, `dev.jasper.sdk.WindowOwner`, `java.util.function.BooleanSupplier`, `javax.swing.JComponent`):

```java
    static final class Panel { final PanelSpec spec; final PanelFactory factory; Panel(PanelSpec spec, PanelFactory factory) { this.spec = spec; this.factory = factory; } }

    final class FakeWindow implements PluginWindow, PluginDialog {
        final String id; final FakeWindow owner; String title; boolean shown; boolean closed;
        final List<BooleanSupplier> guards = new ArrayList<>(); final List<Runnable> closedHandlers = new ArrayList<>();
        FakeWindow(String id, String title, FakeWindow owner) { this.id = id; this.title = title; this.owner = owner; }
        @Override public void setContent(JComponent content) { }
        @Override public void show() { if (!closed) shown = true; }
        @Override public void toFront() { }
        @Override public void setTitle(String value) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("A window needs a title");
            if (!closed) title = value;
        }
        @Override public Subscription onClosing(BooleanSupplier guard) { guards.add(guard); return () -> guards.remove(guard); }
        @Override public Subscription onClosed(Runnable handler) { closedHandlers.add(handler); return () -> closedHandlers.remove(handler); }
        boolean requestClose() {
            for (BooleanSupplier guard : List.copyOf(guards)) {
                try { if (!guard.getAsBoolean()) return false; }
                catch (RuntimeException failure) { host.recordFailure(pluginId() + " closing guard: " + failure); }
            }
            close();
            return true;
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            windows.remove(this);
            for (FakeWindow other : List.copyOf(windows)) if (other.owner == this) other.close();
            for (Runnable handler : List.copyOf(closedHandlers)) {
                try { handler.run(); } catch (RuntimeException failure) { host.recordFailure(pluginId() + " window closed: " + failure); }
            }
        }
    }

    final List<Panel> panels = new ArrayList<>();
    final List<String[]> railActions = new ArrayList<>();
    final List<FakeWindow> windows = new ArrayList<>();

    Panels panels() {
        return (spec, factory) -> {
            context.requireOpen();
            java.util.Objects.requireNonNull(factory, "factory");
            requireNamespace(spec.id(), "A panel");
            for (Panel existing : panels) if (existing.spec.id().equals(spec.id())) throw new IllegalArgumentException("Panel already registered: " + spec.id());
            var panel = new Panel(spec, factory);
            panels.add(panel);
            return () -> panels.remove(panel);
        };
    }

    JComponent openPanel(String panelId, UUID windowId) {
        for (Panel panel : panels) {
            if (!panel.spec.id().equals(panelId)) continue;
            boolean[] visible = {true};
            try {
                return panel.factory.create(new PanelHost() {
                    @Override public WindowHandle window() { return () -> windowId; }
                    @Override public void show() { visible[0] = true; }
                    @Override public void hide() { visible[0] = false; }
                    @Override public boolean visible() { return visible[0]; }
                    @Override public Subscription onVisibility(java.util.function.Consumer<Boolean> handler) { return () -> { }; }
                    @Override public Subscription onClosed(Runnable handler) { return () -> { }; }
                });
            } catch (RuntimeException | LinkageError failure) {
                host.recordFailure(pluginId() + " panel " + panelId + ": " + failure);
                return null;
            }
        }
        return null;
    }

    Rail rail() {
        return actionId -> {
            context.requireOpen();
            requireOwn(actionId);
            String[] placed = {actionId};
            railActions.add(placed);
            return () -> { for (int i = 0; i < railActions.size(); i++) if (railActions.get(i) == placed) { railActions.remove(i); break; } };
        };
    }

    Windows windows() {
        return new Windows() {
            @Override public PluginWindow create(WindowSpec spec) {
                context.requireOpen();
                requireNamespace(spec.id(), "A window");
                if (spec.singleton()) for (FakeWindow open : windows) if (open.id.equals(spec.id())) return open;
                var window = new FakeWindow(spec.id(), spec.title(), null);
                windows.add(window);
                return window;
            }
            @Override public PluginDialog dialog(DialogSpec spec) {
                context.requireOpen();
                WindowOwner owner = spec.owner();
                FakeWindow parent = null;
                if (owner instanceof FakeWindow window) {
                    if (window.closed || !windows.contains(window)) throw new IllegalArgumentException("A dialog needs an open owner window");
                    parent = window;
                } else if (!(owner instanceof WindowHandle))
                    throw new IllegalArgumentException("A dialog's owner must be an open window of this plugin or a terminal window");
                var dialog = new FakeWindow("dialog", spec.title(), parent);
                windows.add(dialog);
                return dialog;
            }
        };
    }
```

Extend `closeAll()` with `panels.clear(); railActions.clear(); for (FakeWindow window : List.copyOf(windows)) window.close();`.

In `FakePluginContext` add `public Panels panels() { return ui.panels(); }`, `public Rail rail() { return ui.rail(); }` and `public Windows windows() { return ui.windows(); }`, each with a one-line Javadoc. In `FakePluginHost` add the five methods, iterating `contexts.values()` in order and documenting the formats listed under Interfaces:

```java
    public List<String> panels() {
        List<String> lines = new ArrayList<>();
        for (FakePluginContext context : contexts.values())
            for (FakeUi.Panel panel : context.ui.panels) lines.add(panel.spec.id() + "|" + panel.spec.title() + "|" + panel.spec.defaultAnchor());
        return lines;
    }

    public javax.swing.JComponent openPanel(String panelId, UUID windowId) {
        for (FakePluginContext context : contexts.values()) {
            javax.swing.JComponent built = context.ui.openPanel(panelId, windowId);
            if (built != null) return built;
        }
        return null;
    }

    public List<String> rail() {
        List<String> lines = new ArrayList<>();
        for (FakePluginContext context : contexts.values())
            for (String[] placed : context.ui.railActions) if (actionExists(placed[0])) lines.add(placed[0]);
        return lines;
    }

    public List<String> windows() {
        List<String> lines = new ArrayList<>();
        for (FakePluginContext context : contexts.values())
            for (FakeUi.FakeWindow window : context.ui.windows) lines.add(window.id + "|" + window.title + "|" + window.shown);
        return lines;
    }

    public boolean requestClose(String windowId) {
        for (FakePluginContext context : contexts.values())
            for (FakeUi.FakeWindow window : List.copyOf(context.ui.windows)) if (window.id.equals(windowId)) return window.requestClose();
        return false;
    }
```

`FakeUi.FakeWindow` and `FakeUi.Panel` are referenced from `FakePluginHost`, so they must not be `private`; `FakeWindow` is an inner class, which is why `windows.remove(this)` reaches the enclosing `FakeUi`. Give each public method on `FakePluginHost` a Javadoc with `@param` and `@return`.

- [ ] **Step 5: Update `HostedContext`'s construction of `HostedUi`**

Task 8 adds `AuxiliaryWindows` to `PluginHost.Environment`. To keep this task compiling, pass a field now: in `HostedContext` change the `new HostedUi(...)` call to add `host.environment.windows()` as the last argument, and add the component `AuxiliaryWindows windows` as the ninth component of `PluginHost.Environment`. Update the three constructors of `Environment` in tests and `PluginRuntime` accordingly: `AppContractTest.host(...)` passes `onEdtValue(() -> new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { }, () -> new java.awt.Rectangle(0, 0, 10, 10))))`; `PluginRuntime` takes it as a fifth constructor parameter (document it as "builds plugin windows and dialogs") and `JasperApplication`, `PluginRuntimeTest` and `BundledSamplePluginTest` pass an instance built the same way as the harness's until Task 9 gives the application its real one.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-sdk-testkit:check :jasper-app:test --tests 'dev.jasper.app.plugins.*' verifySdkArchitecture verifyApplicationArchitecture`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add jasper-app/src jasper-sdk-testkit
git commit -m "feat: adapt panels, rail buttons and plugin windows in the app and the testkit

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: `PluginContext` accessors and the contract suite

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java`, `JasperSdk.java`; `plugins/sample/src/main/resources/plugin.toml`
- Modify: `jasper-sdk-testkit/.../FakePluginContext.java`, `.../contract/{ContractHarness,PluginContractTest}.java`, `.../FakeContractTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java`; `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`

**Interfaces:**
- Produces: `PluginContext.panels()`, `rail()`, `windows()`; `JasperSdk.VERSION` = `"0.3.0"`; sample `sdk = ">=0.3, <0.4"`; `ContractHarness` additions `List<String> panels()`, `JComponent openPanel(String panelId, UUID windowId)`, `List<String> rail()`, `List<String> windows()`, `boolean requestClose(String windowId)` in Task 7's format.

- [ ] **Step 1: Grow the harness and write the failing contract cases**

Add the five methods to `ContractHarness` with the format descriptions from Task 7, and append to `PluginContractTest` (imports `dev.jasper.sdk.ui.Anchor`, `DialogSpec`, `PanelHost`, `PanelSpec`, `PluginWindow`, `WindowSpec`, `java.awt.Dimension`, `javax.swing.ImageIcon`, `javax.swing.JLabel`):

```java
    @Test void panelsAreNamespacedLazyPerWindowAndContained() {
        List<PanelHost> hosts = Collections.synchronizedList(new ArrayList<>());
        var registration = new AtomicReference<Subscription>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            registration.set(context.panels().register(new PanelSpec("test.alpha.hosts", "Hosts", new ImageIcon(), Anchor.RIGHT),
                host -> { hosts.add(host); return new JLabel("hosts"); }));
            context.panels().register(new PanelSpec("test.alpha.broken", "Broken", new ImageIcon(), Anchor.BOTTOM),
                host -> { throw new IllegalStateException("factory failure"); });
            assertThatIllegalArgumentException().isThrownBy(() -> context.panels().register(
                new PanelSpec("test.alpha.hosts", "Twice", new ImageIcon(), Anchor.LEFT), host -> new JLabel()));
            assertThatIllegalArgumentException().isThrownBy(() -> context.panels().register(
                new PanelSpec("test.beta.panel", "Foreign", new ImageIcon(), Anchor.LEFT), host -> new JLabel()));
        });
        assertThat(h.panels()).containsExactly("test.alpha.hosts|Hosts|RIGHT", "test.alpha.broken|Broken|BOTTOM");
        assertThat(hosts).as("no instance until a window shows the panel").isEmpty();
        UUID window = UUID.randomUUID();
        h.ui(() -> {
            assertThat(h.openPanel("test.alpha.hosts", window)).isInstanceOf(JLabel.class);
            assertThat(h.openPanel("test.alpha.broken", window)).as("a throwing factory is contained").isNull();
            assertThat(h.openPanel("test.alpha.absent", window)).isNull();
        });
        assertThat(hosts).singleElement().satisfies(host -> assertThat(host.window().id()).isEqualTo(window));
        h.ui(() -> registration.get().close());
        assertThat(h.panels()).containsExactly("test.alpha.broken|Broken|BOTTOM");
        h.stopAll();
        assertThat(h.panels()).isEmpty();
    }

    @Test void railButtonsNameThePluginsOwnActions() {
        var open = new AtomicReference<PluginAction>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            open.set(context.actions().register(ActionSpec.of("test.alpha.open", "Open"), invoked -> { }));
            context.rail().add("test.alpha.open");
            assertThatIllegalArgumentException().isThrownBy(() -> context.rail().add("new_tab"));
            assertThatIllegalArgumentException().isThrownBy(() -> context.rail().add("test.alpha.absent"));
        });
        assertThat(h.rail()).containsExactly("test.alpha.open");
        h.ui(() -> open.get().close());
        assertThat(h.rail()).isEmpty();
    }

    @Test void windowsAreSingletonsGuardedOwnedAndClosedWithTheirPlugin() {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        var manager = new AtomicReference<PluginWindow>();
        var veto = new AtomicReference<Subscription>();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            PluginWindow window = context.windows().create(new WindowSpec("test.alpha.manager", "Manager", new Dimension(640, 480), true));
            manager.set(window);
            assertThat(context.windows().create(new WindowSpec("test.alpha.manager", "Manager", new Dimension(640, 480), true))).isSameAs(window);
            assertThatIllegalArgumentException().isThrownBy(() ->
                context.windows().create(new WindowSpec("test.beta.window", "Foreign", new Dimension(10, 10), false)));
            veto.set(window.onClosing(() -> false));
            window.onClosing(() -> { throw new IllegalStateException("guard failure"); });
            window.onClosed(() -> events.add("closed"));
            window.show();
            context.windows().dialog(new DialogSpec("Unlock", window, true));
        });
        assertThat(h.windows()).containsExactly("test.alpha.manager|Manager|true", "dialog|Unlock|false");
        h.ui(() -> {
            assertThat(h.requestClose("test.alpha.manager")).isFalse();
            veto.get().close();
            assertThat(h.requestClose("test.alpha.manager")).as("a throwing guard cannot trap the window open").isTrue();
            assertThat(h.requestClose("test.alpha.absent")).isFalse();
        });
        assertThat(events).containsExactly("closed");
        assertThat(h.windows()).as("the dialog went with its owner").isEmpty();
        h.ui(() -> {
            assertThatIllegalArgumentException().isThrownBy(() -> alpha.get().windows().dialog(new DialogSpec("Late", manager.get(), true)));
            alpha.get().windows().create(new WindowSpec("test.alpha.other", "Other", new Dimension(10, 10), false)).show();
        });
        assertThat(h.windows()).containsExactly("test.alpha.other|Other|true");
        h.stopAll();
        assertThat(h.windows()).isEmpty();
    }
```

In `FakeContractTest` delegate the five methods to the host.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-sdk-testkit:test`
Expected: compilation FAILS: `PluginContext` has no `panels()`.

- [ ] **Step 3: Grow the SDK and both implementations**

In `PluginContext` add, after `appearance()`, with imports for `Panels`, `Rail`, `Windows`:

```java
    /**
     * Side and bottom panels.
     *
     * @return the panels service
     */
    Panels panels();

    /**
     * Rail action buttons.
     *
     * @return the rail service
     */
    Rail rail();

    /**
     * Application-built windows and dialogs.
     *
     * @return the windows service
     */
    Windows windows();
```

Set `JasperSdk.VERSION = "0.3.0"` and the sample's `sdk = ">=0.3, <0.4"`. Add `@Override` to the fake's three methods. In `HostedContext` add:

```java
    @Override public Panels panels() { return ui.panels(); }
    @Override public Rail rail() { return ui.rail(); }
    @Override public Windows windows() { return ui.windows(); }
```

- [ ] **Step 4: Teach the application harness the five operations**

In `AppContractTest.newHarness()` keep a reference to the `AuxiliaryWindows` passed to `host(...)` (name it `auxiliary`) and add:

```java
            @Override public List<String> panels() {
                return onEdtValue(() -> contributions.panels().stream()
                    .map(panel -> panel.id() + "|" + panel.title() + "|" + panel.defaultRegion()).toList());
            }
            @Override public javax.swing.JComponent openPanel(String panelId, java.util.UUID windowId) {
                return onEdtValue(() -> contributions.panels().stream().filter(panel -> panel.id().equals(panelId)).findFirst()
                    .map(panel -> panel.factory().apply(new dev.jasper.app.contributions.PanelSite(windowId, () -> { }, () -> { }, () -> true)))
                    .orElse(null));
            }
            @Override public List<String> rail() {
                return onEdtValue(() -> contributions.railActions().stream().filter(id -> contributions.action(id).isPresent()).toList());
            }
            @Override public List<String> windows() {
                return onEdtValue(() -> auxiliary.open().stream()
                    .map(surface -> (surface.kind() == dev.jasper.app.windows.AuxiliarySurface.Kind.DIALOG ? "dialog" : surface.id())
                        + "|" + surface.title() + "|" + surface.shown()).toList());
            }
            @Override public boolean requestClose(String windowId) {
                return onEdtValue(() -> auxiliary.open().stream().filter(surface -> surface.id().equals(windowId)).findFirst()
                    .map(dev.jasper.app.windows.AuxiliarySurface::requestClose).orElse(false));
            }
```

`Optional.map` with a factory that returns null yields an empty optional, so `orElse(null)` covers both "no such panel" and "the factory failed".

- [ ] **Step 5: Run everything**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`; 17 contract cases pass for the fake and for the application.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A jasper-sdk jasper-sdk-testkit jasper-app plugins
git commit -m "feat: expose panels, the rail and windows through PluginContext

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 9: Application wiring

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/AuxiliaryWindows.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/{JasperApplication,package-info}.java`
- Test: extend `AuxiliaryWindowsTest`, `JasperApplicationPluginsTest`

**Interfaces:**
- Produces: `AuxiliaryWindows.onAllClosed` (public `Runnable` field, default no-op, run after a close leaves nothing open). `JasperApplication` loads `UiState` from `AppDirs.uiState()` in `startPlugins`, owns the real `AuxiliaryWindows` over `NativeShells`, connects every window with that state, stays alive while a plugin window is open, and closes plugin windows and saves state at shutdown.

Behavior worth stating: without residency, Jasper exits when its last terminal window closes. A plugin window (the Vault manager) is a reason to stay: the process now exits when the last terminal window **and** the last plugin window are gone.

- [ ] **Step 1: Write the failing tests**

Append to `AuxiliaryWindowsTest`:

```java
    @Test void reportsWhenTheLastSurfaceCloses() {
        List<String> events = new ArrayList<>();
        windows.onAllClosed = () -> events.add("empty");
        AuxiliarySurface first = windows.window("dev.x.a", "A", new Dimension(10, 10), false);
        AuxiliarySurface second = windows.window("dev.x.b", "B", new Dimension(10, 10), false);
        first.close();
        assertThat(events).isEmpty();
        second.close();
        assertThat(events).containsExactly("empty");
    }
```

Append to `JasperApplicationPluginsTest`:

```java
    @Test void layoutStateIsLoadedForPluginsAndSavedAtShutdown() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        java.nio.file.Files.writeString(dirs.uiState(), "version = 1\nrail_visible = false\n");
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, null, false, dirs);
        });
        java.nio.file.Files.delete(dirs.uiState());
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(dirs.uiState()).as("saved again at shutdown, keeping what was loaded").exists()
            .content().contains("rail_visible = false");
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*AuxiliaryWindowsTest' --tests '*JasperApplicationPluginsTest'`
Expected: compilation FAILS (`onAllClosed`), and the application test fails because nothing saves the state.

- [ ] **Step 3: Implement**

In `AuxiliaryWindows` add the field and call it at the end of the `onClosed` handler in `track`:

```java
    /** Run after a close leaves no surface open; the application uses it to decide whether to exit. */
    public Runnable onAllClosed = () -> { };
```

```java
    private boolean emptyReported = true;
```

Closing a window closes its dialogs from inside the same handler, so the innermost close may already find the list empty. Report the transition once: set `emptyReported = false;` in `track` when a surface is added, restructure the handler so the bounds are saved before its end (replace the early `return` for dialogs with an `if` around the bounds block), and finish the handler with:

```java
            if (open.isEmpty() && !emptyReported) { emptyReported = true; onAllClosed.run(); }
```

In `JasperApplication` add imports `dev.jasper.app.persistence.UiState`, `dev.jasper.app.windows.AuxiliaryWindows`, `dev.jasper.app.windows.NativeShells`, fields:

```java
    private UiState uiState = UiState.inMemory();
    private AuxiliaryWindows auxiliary;
```

In `startPlugins`, before constructing `PluginRuntime`:

```java
        uiState = UiState.load(dirs.uiState());
        auxiliary = new AuxiliaryWindows(uiState, new NativeShells(themes, uiState, this::nativeWindow,
            () -> newWindow(Path.of(System.getProperty("user.home"))), this::quit)::create);
        auxiliary.onAllClosed = () -> { if (windows.isEmpty() && !resident && !quitting) requestShutdown(); };
```

and pass `auxiliary` as `PluginRuntime`'s fifth constructor argument (replacing the placeholder from Task 7). Add:

```java
    /** A terminal window's native window, for parenting application-built dialogs; null when it is gone. */
    private java.awt.Window nativeWindow(java.util.UUID id) {
        for (TerminalWindow window : windows) if (window.content().id().equals(id)) return window.nativeWindow();
        return null;
    }
```

In `newWindow` change the connection to `window.content().connectContributions(contributions, uiState);`.

In `windowClosed`, a plugin window keeps the process alive:

```java
        boolean pluginWindowsOpen = auxiliary != null && !auxiliary.open().isEmpty();
        if (windows.isEmpty() && !resident && !pluginWindowsOpen) requestShutdown();
        else updateBuddyActions();
```

In `shutdown()`, directly after `List<CompletableFuture<?>> pluginWork = plugins == null ? List.of() : plugins.stop();`:

```java
        // Plugins closed their own windows while stopping; this is the safety net, and the state's last write.
        if (auxiliary != null) auxiliary.close();
        uiState.save();
```

`NativeShells` constructs no native object until `create` is called, so `startPlugins` stays headless-safe. In `application/package-info.java` add `dev.jasper.app.windows` to the allowed outgoing dependencies (`dev.jasper.app.persistence` is already listed).

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.windows.*' verifyApplicationArchitecture`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: own layout state and plugin windows in the application

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 10: The sample plugin's panel, rail button, window and dialog

**Files:**
- Modify: `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java`, `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java`

**Interfaces:**
- Produces, when `demo_ui = true`: panel `dev.jasper.sample.panel` ("Sample", LEFT); action `dev.jasper.sample.about` ("About Sample") on the rail, which opens the singleton window `dev.jasper.sample.about-window`; that window's "Details…" button opens a modal dialog over it.

- [ ] **Step 1: Write the failing tests**

Append to `SamplePluginTest`:

```java
    @Test void theDemoUiAddsAPanelARailButtonAndAWindow() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.sample", Map.of("demo_ui", true, "demo_step_millis", 0L));
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.panels()).containsExactly("dev.jasper.sample.panel|Sample|LEFT");
            assertThat(host.rail()).containsExactly("dev.jasper.sample.about");
            assertThat(host.openPanel("dev.jasper.sample.panel", java.util.UUID.randomUUID())).isNotNull();
            assertThat(host.windows()).isEmpty();
            assertThat(host.invoke("dev.jasper.sample.about", java.util.UUID.randomUUID(), null)).isTrue();
            assertThat(host.invoke("dev.jasper.sample.about", java.util.UUID.randomUUID(), null)).isTrue();
            assertThat(host.windows()).as("a singleton").containsExactly("dev.jasper.sample.about-window|About Sample|true");
            assertThat(host.failures()).isEmpty();
        }
    }
```

In `theDemoUiPlacesOneActionEverywhereAndItsStatusFollowsTheActivity` the action list now has a second entry, so every `assertThat(host.actions()).containsExactly(...)` in that test becomes `.contains(...)` with the same argument.

In `BundledSamplePluginTest`, inside the existing `onEdt` block that inspects `contributions`, add:

```java
            assertThat(contributions.panels()).singleElement().satisfies(panel -> assertThat(panel.id()).isEqualTo("dev.jasper.sample.panel"));
            assertThat(contributions.railActions()).containsExactly("dev.jasper.sample.about");
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-sample:test`
Expected: FAIL: no panels.

- [ ] **Step 3: Extend the plugin**

Add imports `dev.jasper.sdk.ui.Anchor`, `DialogSpec`, `PanelSpec`, `PluginDialog`, `PluginWindow`, `WindowSpec`, `java.awt.BorderLayout`, `java.awt.Dimension`, `javax.swing.BorderFactory`, `javax.swing.JButton`, `javax.swing.JLabel`, `javax.swing.JPanel`. At the end of `installUi` add `installPanelAndWindow(context, stepMillis);` and add the method:

```java
    // example:pluginpanels:start
    private static void installPanelAndWindow(PluginContext context, long stepMillis) {
        var icon = context.appearance().icon("dev/jasper/sample/flask.svg");
        // One instance per window, built the first time the panel is shown there.
        context.panels().register(new PanelSpec("dev.jasper.sample.panel", "Sample", icon, Anchor.LEFT), host -> {
            var run = new JButton("Run sample activity");
            run.addActionListener(event -> context.background().execute(() -> demo(context, stepMillis)));
            var panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            panel.add(new JLabel("Sample panel"), BorderLayout.NORTH);
            panel.add(run, BorderLayout.SOUTH);
            return panel;
        });

        context.actions().register(ActionSpec.of("dev.jasper.sample.about", "About Sample").withIcon(icon), invoked -> {
            // The application builds the frame, title bar and menu bar; a singleton comes back while it is open.
            PluginWindow window = context.windows().create(
                new WindowSpec("dev.jasper.sample.about-window", "About Sample", new Dimension(360, 200), true));
            var details = new JButton("Details…");
            details.addActionListener(event -> {
                PluginDialog dialog = context.windows().dialog(new DialogSpec("Sample details", window, true));
                var close = new JButton("Close");
                close.addActionListener(closing -> dialog.close());
                var body = new JPanel(new BorderLayout(0, 8));
                body.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
                body.add(new JLabel("Sample plugin " + context.plugin().version()), BorderLayout.CENTER);
                body.add(close, BorderLayout.SOUTH);
                dialog.setContent(body);
                dialog.show();
            });
            var content = new JPanel(new BorderLayout(0, 8));
            content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            content.add(new JLabel("This window's chrome belongs to Jasper; its content to the plugin."), BorderLayout.CENTER);
            content.add(details, BorderLayout.SOUTH);
            window.setContent(content);
            window.show();
            window.toFront();
        });
        context.rail().add("dev.jasper.sample.about");
    }
    // example:pluginpanels:end
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-sample:check :jasper-app:test --tests '*BundledSamplePluginTest' verifyPluginArchitecture`
Expected: PASS. Do not commit yet: the compiled documentation example changes with this source; Task 11 shares the commit.

---

### Task 11: Documentation

**Files:**
- Modify: `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/app-architecture.md`, `docs/configuration.md`, `docs/STATUS.md`, `jasper-sdk/README.md`, this plan's status banner

- [ ] **Step 1: Update the authoring guide**

In `docs/plugin-authoring.md`: change "(0.2)" to "(0.3)" and the sentence after it to end "…actions and their placements, panels, the rail, and application-built windows and dialogs. Terminal access arrives in a later SDK version."; change the descriptor range to `sdk = ">=0.3, <0.4"`; re-copy the `pluginui` example from the source (it gained one line); and add after "Actions and where they appear":

````markdown
## Panels, the rail and windows

A panel is a Swing component in the left, right or bottom region of every terminal window.
Register it once; Jasper asks your factory for one instance per window, the first time the
panel is shown there, and gives the panel a rail icon, a `<panel id>.toggle` action and a
View → Panels entry. One panel shows per region; the user can move a panel to another region
from its rail icon, and Jasper remembers where it was and how big.

A rail button runs one of your actions. A plugin window is a frame Jasper builds: icon, macOS
title bar, menu bar, theme tracking and remembered bounds are its job; the content is yours.
Dialogs belong to a terminal window or to one of your windows, so they are parented correctly
without your plugin ever seeing a frame.

<!-- EXAMPLE-MARKER:pluginpanels -->
```java
private static void installPanelAndWindow(PluginContext context, long stepMillis) {
    var icon = context.appearance().icon("dev/jasper/sample/flask.svg");
    // One instance per window, built the first time the panel is shown there.
    context.panels().register(new PanelSpec("dev.jasper.sample.panel", "Sample", icon, Anchor.LEFT), host -> {
        var run = new JButton("Run sample activity");
        run.addActionListener(event -> context.background().execute(() -> demo(context, stepMillis)));
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(new JLabel("Sample panel"), BorderLayout.NORTH);
        panel.add(run, BorderLayout.SOUTH);
        return panel;
    });

    context.actions().register(ActionSpec.of("dev.jasper.sample.about", "About Sample").withIcon(icon), invoked -> {
        // The application builds the frame, title bar and menu bar; a singleton comes back while it is open.
        PluginWindow window = context.windows().create(
            new WindowSpec("dev.jasper.sample.about-window", "About Sample", new Dimension(360, 200), true));
        var details = new JButton("Details…");
        details.addActionListener(event -> {
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Sample details", window, true));
            var close = new JButton("Close");
            close.addActionListener(closing -> dialog.close());
            var body = new JPanel(new BorderLayout(0, 8));
            body.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            body.add(new JLabel("Sample plugin " + context.plugin().version()), BorderLayout.CENTER);
            body.add(close, BorderLayout.SOUTH);
            dialog.setContent(body);
            dialog.show();
        });
        var content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(new JLabel("This window's chrome belongs to Jasper; its content to the plugin."), BorderLayout.CENTER);
        content.add(details, BorderLayout.SOUTH);
        window.setContent(content);
        window.show();
        window.toFront();
    });
    context.rail().add("dev.jasper.sample.about");
}
```

- Share your own model between panel instances; never share components.
- `PanelHost.onClosed` tells an instance its window is gone. `onVisibility` tells it when it
  is shown or hidden, which is the moment to start or stop refreshing.
- `WindowSurface.onClosing` guards run when the user closes the window; returning false keeps
  it open. Quitting Jasper does not consult them. `close()` closes at once.
- A modal dialog's `show()` returns after the dialog has closed.
- The application closes a plugin's panels, windows and dialogs when the plugin stops.
- Jasper stays running while any plugin window is open, even with no terminal window.
````

In the file you write, replace `EXAMPLE-MARKER` with `example` (it is spelled differently here because the documentation test scans this plan too, before the sample's new source exists). The block must equal the source text between `// example:pluginpanels:start` and `// example:pluginpanels:end` after `stripIndent().strip()`; if you changed `installPanelAndWindow` while implementing Task 10, copy from the source rather than from here.

In "Testing" add: "`host.panels()`, `host.openPanel(id, windowId)`, `host.rail()`, `host.windows()` and `host.requestClose(windowId)` cover panels and windows. The fake does not model Jasper's own `<panel id>.toggle` action or View → Panels menu."

- [ ] **Step 2: Update the other documents**

`jasper-sdk/README.md`: extend the `dev.jasper.sdk.ui` row with "`Panels`, `PanelSpec`, `PanelHost`, `Rail`, `Windows`, `WindowSpec`, `DialogSpec`, `PluginWindow`, `PluginDialog`" and the `dev.jasper.sdk` row with "`WindowOwner`".

`docs/sdk-architecture.md`: change the `dev.jasper.app.plugins` row's dependencies to "SDK, `contributions`, `notifications`, `persistence`, `platform`, `windows`"; add the row "`dev.jasper.app.windows` | Application-built auxiliary windows: headless `AuxiliarySurface`, native `NativeShells` | `appearance`, `lifecycle`, `persistence`, `platform`"; add before "Shutdown":

```markdown
## Panels, the rail and plugin windows

Panels and rail actions are part of the `Contributions` model. Each window's
`WindowContributions` keeps one lazily built instance per panel, shows at most one per region
through `WorkspaceRegions` (nested split panes rebuilt around the terminal deck: bottom wraps
the deck, right wraps that, left wraps that), and renders `WindowRail`. A window with nothing
contributed has no rail and its old layout. Requests to show, hide or toggle a panel travel
through the model tagged with a window id, so an action or a `PanelHost` reaches exactly one
window. Every panel gets an application-registered `<panel id>.toggle` action and a View →
Panels entry.

Plugin windows follow the `TerminalWindow`/`WindowContent` split: `AuxiliarySurface` holds the
content, title, closing guards and lifetime and is fully tested headlessly; `NativeShells`
is the only code that constructs a frame or dialog, and adds the icon, the macOS title bar,
a minimal menu bar, theme tracking and remembered bounds. Dialogs are parented through
`WindowOwner`, so an SDK type never exposes a frame.

`persistence.UiState` (`ui-state.toml`) holds panel region, visibility and size, rail
visibility and auxiliary window bounds. It is application state, not configuration: strict
read, atomic write, defaults when unreadable. The process stays alive while a plugin window
is open.
```

In "Not yet implemented" replace the plan 3 clause with "The Plugins manager with install, consent and restart (plan 3b)".

`docs/app-architecture.md`: add the row after `snippets`:

```markdown
| `windows` | Application-built auxiliary windows and dialogs: a headless surface core and the only other native frame boundary besides TerminalWindow. Application owns AuxiliaryWindows and closes it at shutdown. |
```

and extend the `workspace` row's first sentence with "panel regions and the rail".

`docs/configuration.md`: in the data-paths part of "Location and startup options" add "`ui-state.toml` beside `config.toml` remembers panel placement, rail visibility and plugin window bounds; it is written by Jasper and is not meant to be edited." In the plugin keybinding paragraph add "Every panel also has a `<panel id>.toggle` action you can bind the same way."

`docs/STATUS.md`: update the opening paragraph to say plan 3a is implemented on `claude/plugin-sdk-plan-3a` and that the spec's plan 3 was split into 3a and 3b; add a dated "Plugin SDK plan 3a" section with the eight scope decisions at the top of this plan, exact test counts, and native acceptance pending.

Set this plan's **Status** banner to "Implemented on `claude/plugin-sdk-plan-3a`; native acceptance pending" and list any deviation.

- [ ] **Step 3: Verify everything**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: PASS: three examples match `SamplePlugin.java`.

Run the AGENTS.md Python hygiene check. Expected: no output.

Run: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
Expected: `BUILD SUCCESSFUL` (rerun if the only failure is the known terminal flake). `--rerun-tasks` matters: it is what exposes Gradle wiring problems that up-to-date checks hide.

- [ ] **Step 4: Commit Tasks 10 and 11**

```bash
git branch --show-current
git add -A
git commit -m "feat: give the sample plugin a panel, a rail button and a window, and document them

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Native acceptance (user-run; agents must not launch the GUI)

With `demo_ui = true` under `[plugins."dev.jasper.sample"]`:

1. `./gradlew :jasper-app:run`. A narrow rail appears on the left with a flask toggle, a separator, a second flask button, and Settings pinned at the bottom. Without `demo_ui` there is no rail and the window looks exactly as before.
2. Click the first flask: the Sample panel opens on the left; the terminal reflows to the remaining width and keeps focus sensible. Drag the divider; click the flask again to hide; show it again: the size is remembered.
3. Right-click the flask → Move to Right, then Move to Bottom: the panel moves; bottom sits under the terminal only. Quit and relaunch: the panel is where it was, as wide as it was.
4. View → Panels → Toggle Sample, and the palette's "Toggle Sample", do the same; bind `"dev.jasper.sample.panel.toggle"` under `[keybindings]` and use the shortcut.
5. View → Rail hides and shows the rail; the choice survives a restart.
6. Open a second window: it has its own panel instance and its own visibility.
7. Click the second flask: "About Sample" opens with Jasper's title bar and icon; on macOS the menu bar still shows File. Click it again: the same window comes forward. Move and resize it, close it, reopen: bounds are remembered. "Details…" opens a modal dialog centered over it.
8. Close the last terminal window while About Sample is open (residency off): Jasper stays running; closing About Sample then exits.
9. Switch Appearance with the panel hidden, then show it: it has the new look. The About window follows the theme too.

## Self-review record

- **Spec coverage.** §4.2 rail and three regions in `WindowContent` with an app-native panel registry → Tasks 3–5. §4.6 title-only title bar → Task 6 (no `MacTitleBar` change needed). §5 Rail and panels (factory per window, lazy, one per region, move, persisted region/visibility/size outside `config.toml`, `<panel id>.toggle`, View → Panels, rail order with Settings pinned) → Tasks 1–5, 7–9; Windows and dialogs (app-owned frame, icon, title bar, menu bar, theme tracking, Cmd/Ctrl+W, remembered bounds, singleton, dialogs parented without exposing a frame, fast quit) → Tasks 1, 6–9; Look and feel (`updateComponentTreeUI` on plugin roots, including hidden panels) → Tasks 5, 6. §3 teardown closes windows, dialogs and panels → Task 7 (`tracked`/`closers`), contract in Task 8. §11 testkit and contract → Tasks 7, 8. §13 item 3 demo (panel toggles from the rail and moves between regions) → Task 10 and the checklist. Deferred to plan 3b by decision 1: Plugins manager, install and consent, restart banner, retire request, "Restart normally".
- **Type consistency.** `Contributions.addPanel(id, title, icon, region, factory)`, `PanelSite(windowId, show, hide, visible)`, `Contributions.PanelRequest(windowId, panelId, op)`, `UiState.Panel(region, visible, size)`, `AuxiliarySurface.Shell(show, toFront, dispose, title, bounds)`, `AuxiliaryWindows(state, shells)`, `WindowContributions(owner, model, state)`, `HostedUi`'s ten-argument constructor, `PluginHost.Environment`'s nine components, `PluginRuntime`'s five-argument constructor and the harness rendering formats are used identically wherever they appear.
- **Build stays green per task** except between Tasks 10 and 11, which share one commit.
