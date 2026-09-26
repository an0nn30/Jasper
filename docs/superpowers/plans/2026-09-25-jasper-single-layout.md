# One layout (remove retro) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the retro appearance style completely and collapse Jasper to its single IntelliJ-style layout. That covers chrome, icons, SDK, configuration, tests and docs.

**Architecture:** This is a subtractive refactor done in dependency order, so that every commit builds and passes:
1. Collapse all retro branches in the chrome, and every retro-parametrized test.
2. Remove the SDK retro API (SDK 0.8.0).
3. Remove the raster icon catalogs and `SwingAppearance`.
4. Remove the style itself: `ThemeStyle`, the config key, `ThemeController`'s style, Metal and the retro palette.
5. Clean the docs and run the removal gates.

No new behaviour is introduced. Modern Light and Dark look and act exactly as before.

**Tech Stack:** Java 25 on JBR 25, Swing, FlatLaf 3.7, JUnit 5, AssertJ, Gradle wrapper.

**Spec:** [2026-09-25-jasper-single-layout-design.md](../specs/2026-09-25-jasper-single-layout-design.md)

**Status:** All tasks and the final-review fix wave complete; `./gradlew check` passed (1871 tests, 0 failures, 0 errors, 3 skipped). Deviation: the title-row constant is `MacTitleBar.TITLE_HEIGHT`, not `HEIGHT`, because `HEIGHT` hid `ImageObserver.HEIGHT`, which every component inherits. Branch `claude/theme-engine`, worktree `/Users/RQ7RQVF/projects/moray/.worktrees/theme-engine`, cut from `main` at `eb0b2959`. Other sessions use the main checkout and other worktrees, so never run git in `/Users/RQ7RQVF/projects/moray` itself.

## Global Constraints

- **Build and run:** build with `./gradlew` only. The gate is `./gradlew check`; read counts from `*/build/test-results/test/*.xml` and `plugins/*/build/test-results/test/*.xml`. Never launch the GUI.
- **Commits:** one commit per task, ending with the trailer below. Do not push. Run the AGENTS.md source-hygiene Python check before each commit.
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- **MVP rule:** Jasper is unpublished. Remove things outright: no deprecation, no migration notice, no "was removed" documentation. `ui.theme.style` simply becomes an unknown key.
- **SDK:** `JasperSdk.VERSION = "0.8.0"`, and every bundled `plugin.toml` declares `sdk = ">=0.8.0, <0.9"`. The SDK and testkit stay JDK-only.
- **Behaviour is preserved:** modern Light and Dark, `ui.theme.terminal`, the IntelliJ toolbar, tabs and find bar, and the plugin `Variant` API behave exactly as before. No modern-path assertion may be weakened. If one must change, record why in the task report.
- **Test transformation rules** (apply them literally):
  - **R1:** A test like `@ParameterizedTest @ValueSource(booleans = {false, true}) void name(boolean retro)` becomes `@Test void name()`. Substitute `retro = false` throughout:
    - keep the `false`/else branch;
    - delete statements reached only when `retro` is true;
    - replace `retro ? A : B` with `B`, and `!retro` with `true`;
    - replace `setRetroIcons(retro)` with nothing.
  - **R2:** `for (boolean retro : new boolean[]{false, true}) { BODY }` becomes `BODY`, with `retro = false` substituted as in R1. If the loop wraps a `try (…)` resource, keep the `try`.
  - **R3:** Delete a test method whose subject is retro. That means its name mentions retro, Metal or OldGNOME, or it constructs the retro style or retro artwork as the thing under test.
  - **R4:** `new ThemeController(ThemeStyle.MODERN, X)` becomes `new ThemeController(X)`. This is done by script in Task 4.
- **Removal gate:** at the end of each task, run the `git grep` in its final step. It must print nothing except the lines it explicitly allows.

## Review Focus

1. **Menus live on the macOS screen menu bar in every window,** including plugin windows made by `NativeShells`. Task 1 asserts that `MacTitleBar.setMenuBar` installs the menu bar on the root. Task 4 asserts that `configureDesktopProperties()` sets `apple.laf.useScreenMenuBar=true`.
2. **A config that still says `ui.theme.style = "retro"` starts normally,** in the saved variant, with exactly one unknown-key warning. Task 4 test.
3. **Plugins built for SDK 0.7.x are refused,** while every bundled plugin loads. Task 2 relies on `BundledSamplePluginTest` loading all bundled plugins with the new ranges, plus a resolver check that `>=0.7.4, <0.8` is incompatible with 0.8.0.
4. **Light↔dark switches and failed installs still behave** now that the Metal capture and restore is gone. The live switch still reinstalls FlatLaf, and a failed install still rolls back. Task 4 keeps `reloadActionRetriesFailedSavedInstallationWithUnchangedFiles` and the ThemeController failure tests green, unchanged.
5. **Icon sizes stay at 16 px,** in the toolbar, rail, status items and menus, after `forToolbar`/`toolbarIcon` are removed. A custom plugin `Icon` still passes through by identity. Task 3 keeps the `WindowChromeContributionsTest` identity and 16 px assertions.

---

### Task 1: Collapse the chrome and every retro-parametrized test to the modern path

**Files:**
- Delete: `jasper-app/src/main/java/dev/jasper/app/workspace/RetroToolbar.java`, `RetroTabs.java`; `jasper-app/src/test/java/dev/jasper/app/workspace/RetroChromeTest.java`, `RetroTabsTest.java`
- Modify production: `workspace/WindowChrome.java`, `WindowContent.java`, `FindBar.java`, `TerminalDeck.java`, `WindowStatusBar.java`, `WindowRail.java`, `WindowCommandPalette.java`, `WindowCommands.java`; `palette/CommandPalette.java`; `platform/MacTitleBar.java`; `plugins/remote/src/main/java/dev/jasper/remote/ui/FlatToolBar.java` (Javadoc)
- Modify tests:
  - R3 deletions: `MacTitleBarTest` (the retro methods near lines 160 and 200), `jasper-app/src/test/java/dev/jasper/app/windows/NativeShellsChromeTest` (`retroSupported…`, `retroUnsupported…`), `WindowPanelsTest` (the retro method near line 162), `FindBarModernTest.retroKeepsTheTextButtonRow`, `TerminalColorsTest.retroShowsTheTerminalChoicesDisabled`, `CommandPaletteTest` (the retro method near line 456, and retro entries of the render matrix near lines 481-483);
  - R1/R2 collapses in every file listed under "Parametrized over retro" in Step 1.

**Interfaces:**
- Produces:
  - `MacTitleBar.HEIGHT = 28`, which replaces `MODERN_HEIGHT` and removes `RETRO_HEIGHT`;
  - `MacTitleBar.isSupported()` returns `SystemInfo.isMacFullWindowContentSupported`;
  - `WindowContent.retro()` no longer exists.
- After this task, `SwingAppearance.retro()` is referenced only by `platform/AppIcons.java`. `AppIcons.toolbarIcon`, `forToolbar` and `skin` still exist and are removed in Tasks 2 and 3.

- [ ] **Step 1: Collapse the retro-parametrized and retro-looped tests (R1/R2)**

Apply R1 or R2 to each of these. In every case the test keeps exactly its modern assertions:
- `jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeContributionsTest.java`, both parametrized tests (lines ~47 and ~141). In the second, keep `AppIcons.skin(...)` for now; Task 2 replaces it. Every `retro ? 28 : 16` becomes `16`.
- `jasper-app/src/test/java/dev/jasper/app/workspace/PaletteScopesTest.java`, two tests (~263, ~312).
- `jasper-app/src/test/java/dev/jasper/app/workspace/WindowStatusBarContributionsTest.java`, the parametrized test (~19) and the `for (boolean retro …)` loop (~98).
- `jasper-app/src/test/java/dev/jasper/app/palette/CommandPaletteTest.java`, the parametrized test (~368).
- `jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java`, `remoteLoadsWithoutVaultAndUsesHostIconsForEveryPlacement` (~78). The `new ThemeController(retro ? … RETRO : … MODERN, …)` becomes `new dev.jasper.app.appearance.ThemeController(dev.jasper.app.config.ThemeStyle.MODERN, dev.jasper.app.config.Appearance.LIGHT)`.
- `jasper-app/src/test/java/dev/jasper/app/plugins/ManagedVaultLoadingTest.java` (~23).
- `jasper-app/src/test/java/dev/jasper/app/platform/NamedIconsTest.java` (loop ~23).
- `jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java` (loop ~337).
- `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java` (~40), `ManagedImportIntegrationTest.java` (~30), `ui/sftp/SftpPanelTest.java` (~27).
- `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java`, the loop in `requestsBothIconFamiliesForEitherSkin`. Task 2 rewrites this test, so here only unwrap the loop with `retro = false`.
- `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java` (~265), `ui/VaultManagerWindowTest.java` (~119).

Leave `host.setRetroIcons(false)` lines and `FakeNamedIcon(name, false)` constructions in place; Task 2 removes the API. Then run:

Run: `./gradlew check`
Expected: pass. Only test code changed, and every modern case is unchanged.

- [ ] **Step 2: Delete the retro chrome tests (R3)**

Delete `RetroChromeTest.java` and `RetroTabsTest.java`, plus the retro-subject methods named in **Files**. Keep every modern method in those files.

- [ ] **Step 3: Collapse `WindowChrome`**

Make these edits in `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java`:
- In the constructor, replace
  ```java
          toolbar = owner.retro() ? new RetroToolbar() : new ReferenceToolbar();
          toolbar.setFloatable(false);
          if (!owner.retro()) toolbar.setBorder(BorderFactory.createEmptyBorder());
  ```
  with
  ```java
          toolbar = new ReferenceToolbar();
          toolbar.setFloatable(false);
          toolbar.setBorder(BorderFactory.createEmptyBorder());
  ```
- Delete the block
  ```java
          if (owner.retro()) {
              var note = new JMenuItem("Retro uses light Metal; change style in Settings and restart.");
              note.setEnabled(false); appearance.addSeparator(); appearance.add(note);
          }
  ```
- Replace `if (owner.retro()) buildRetroToolbar(); else buildModernToolbar();` with `buildToolbar();`.
- Delete the whole `buildRetroToolbar()` method and the whole `addToolbarSeparator()` method.
- Rename `buildModernToolbar()` to `buildToolbar()` and set its Javadoc to `/** IntelliJ grouping: tab and window, pane, plugin items, then Find and Settings pinned right. */`.
- In `contributedButton(...)`, replace the body from `if (owner.retro()) {` through `if (!owner.retro()) button.setFont(button.chromeFont());` with:
  ```java
          button.setBorder(BorderFactory.createEmptyBorder()); button.setContentAreaFilled(false);
          button.setIconTextGap(UIScale.scale(4));
          button.getAccessibleContext().setAccessibleName(label);
          button.setToolTipText(label);
          button.setFont(button.chromeFont());
  ```
- In `addButton(...)`, replace
  ```java
          if (owner.retro()) {
              RetroToolbar.styleButton(button);
          } else {
              button.setBorder(BorderFactory.createEmptyBorder()); button.setContentAreaFilled(false);
              button.setIconTextGap(UIScale.scale(4));
          }
  ```
  with
  ```java
          button.setBorder(BorderFactory.createEmptyBorder()); button.setContentAreaFilled(false);
          button.setIconTextGap(UIScale.scale(4));
  ```
- In `ReferenceButton`, delete the first line of `getPreferredSize()` (`if (dev.jasper.app.platform.SwingAppearance.retro()) return super.getPreferredSize();`) and the first line of `paintComponent(...)` (`if (dev.jasper.app.platform.SwingAppearance.retro()) { super.paintComponent(graphics); return; }`).
- In `setToolbarMode(...)`, delete `if (toolbar instanceof RetroToolbar retro) retro.labels(mode == ToolbarMode.ICONS_AND_LABELS);`.
- Replace `refreshTheme()` with:
  ```java
      void refreshTheme() {
          toolbar.setBackground(UIManager.getColor("Jasper.titleBackground"));
          for (Component child : toolbar.getComponents())
              if (child instanceof ReferenceButton button) button.setFont(button.chromeFont());
          status.setBackground(UIManager.getColor("Jasper.titleBackground"));
          themeItems.forEach((theme, item) -> item.setSelected(owner.appearance() == theme));
          terminalItems.forEach((colors, item) -> item.setSelected(owner.terminalColors() == colors));
      }
  ```

- [ ] **Step 4: Collapse `WindowContent`, `WindowCommands` and `WindowCommandPalette`**

In `WindowContent.java`:
- Delete the field `private final RetroTabs retroTabs;`.
- Replace
  ```java
          windowTabs = retro() ? null : new WindowTabs(this);
          retroTabs = retro() ? new RetroTabs(this) : null;
  ```
  with `windowTabs = new WindowTabs(this);`.
- Delete the method `boolean retro() { … }`.
- Change `refreshTabs()` to `private void refreshTabs() { windowTabs.refresh(); }`.
- In `applyTheme`, change `tabs.setBackground(retro() ? UIManager.getColor("TabbedPane.background") : theme.palette().background());` to `tabs.setBackground(theme.palette().background());`.
- In `close()`, delete `if (retroTabs != null) retroTabs.close();`.
- Leave the existing `windowTabs != null` guards unchanged.

In `WindowCommands.refresh()`, change every `action.setEnabled(!owner.retro());` to `action.setEnabled(true);`, and `view("view.tab_height").setEnabled(!owner.retro());` to `view("view.tab_height").setEnabled(true);`. That is exactly the value these expressions had in modern.

In `WindowCommandPalette`, delete the line `if (owner.retro()) return;` in the overlay's `paintComponent`.

- [ ] **Step 5: Collapse `FindBar`**

In `FindBar.java`:
- Delete the field `private final boolean modern = !SwingAppearance.retro();` and the import `dev.jasper.app.platform.SwingAppearance`.
- Change the three fields to:
  ```java
      private final JToggleButton regex = new JToggleButton();
      private final JToggleButton caseSensitive = new JToggleButton();
      private final JLabel count = new JLabel("");
  ```
- In the constructor, replace `if (modern) buildModern(); else buildRetro();` with `build();`, and rename `buildModern()` to `build()`.
- Delete `buildRetro()` and the static helper `button(String, Runnable)`. Remove any import that becomes unused, such as `java.awt.FlowLayout`.
- In `paintComponent`, delete `if (!modern) return;`.
- In `edited()`, change `if (modern) clear.setVisible(!query.getText().isEmpty());` to `clear.setVisible(!query.getText().isEmpty());`.
- In `showResult`, replace the four `count.setText` lines with:
  ```java
          if (found.error() != null) count.setText("Invalid regex");
          else if (found.count() > 0) count.setText(found.current() + "/" + found.count());
          else count.setText(missing ? "0 results" : "");
  ```
- In `miss`, delete `if (!modern) return;`.

- [ ] **Step 6: Collapse `TerminalDeck`, `WindowStatusBar`, `WindowRail` and `CommandPalette`**

Replace `TerminalDeck.java` with:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatTabbedPaneUI;
import java.awt.Insets;
import javax.swing.JTabbedPane;

/** One retained selection/content model; the external WindowTabs strip draws the tabs. */
final class TerminalDeck extends JTabbedPane {
    TerminalDeck() {
        super(TOP, WRAP_TAB_LAYOUT);
        putClientProperty("html.disable", true);
    }
    @Override public void updateUI() {
        setUI(new FlatTabbedPaneUI() {
            @Override protected boolean hideTabArea() { return true; }
            @Override protected Insets getTabAreaInsets(int placement) { return new Insets(0, 0, 0, 0); }
        });
        putClientProperty("JTabbedPane.hasFullBorder", false);
        putClientProperty("JTabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0));
    }
}
```

In `WindowStatusBar.java`:
- `statusFont()` becomes `var font = UIManager.getFont("Label.font"); return font.deriveFont(font.getSize2D() - UIScale.scale(2f));`.
- In `refreshTheme()`, use `setBackground(UIManager.getColor("Jasper.titleBackground"));` and `item.setFont(statusFont());`.
- In `Segment.refreshTheme()`, use `label.setFont(statusFont());`.

In `WindowRail.style(...)`, unwrap `if (!dev.jasper.app.platform.SwingAppearance.retro()) { … }` so that both statements always run.

In `palette/CommandPalette.java`:
- Delete `retro()` and `radius(int)`.
- Replace each `radius(X)` call with `X`. There are four: `radius(12)` → `12`, and `radius(UIScale.scale(12))`/`radius(UIScale.scale(8))` → the inner expression.
- In `applyStepColors`, replace the `if (retro()) { … } else field.setBorder(…);` with just the `field.setBorder(BorderFactory.createCompoundBorder(…))` statement.
- Change `!retro()` in the `escape.setBorder(...)` call to `true`.
- Delete the `if (retro()) { … }` block near line 398.

- [ ] **Step 7: Collapse `MacTitleBar` and fix the Remote Javadoc**

In `platform/MacTitleBar.java`:
- Replace the two height constants with
  ```java
      /** Unscaled height of the title row. */
      public static final int HEIGHT = 28;
  ```
- Delete the field `private final boolean retro;` and the assignment `this.retro = SwingAppearance.retro();`, so the constructor line becomes `this.root = root;`.
- `isSupported()` becomes:
  ```java
      /** Full-window content with transparent title; other platforms retain native decorations. */
      public static boolean isSupported() { return SystemInfo.isMacFullWindowContentSupported; }
  ```
- `setMenuBar(...)` becomes:
  ```java
      /** Menus stay on the root, which the macOS screen menu bar displays. */
      public void setMenuBar(JMenuBar menuBar) {
          if (closed) return;
          root.setJMenuBar(menuBar);
      }
  ```
- `titleHeight()` returns `UIScale.scale(HEIGHT)`.
- `refreshColors()` uses `setBackground(UIManager.getColor("Jasper.titleBackground"));` and `title.setFont(SystemFonts.ui(Font.BOLD, 13f));`.
- `paintComponent` uses `g.setColor(UIManager.getColor("Jasper.titleSeparator"));`.
- Keep `import com.jetbrains.JBR;`, since `attach` still uses it. Remove any import that becomes unused.

Replace every other `MacTitleBar.MODERN_HEIGHT` reference in `jasper-app/src` with `MacTitleBar.HEIGHT`; the `git grep` in Step 8 lists them.

In the `NativeShellsChromeTest` modern test (the one that remains), add after `installTitleBar(...)` returns:

```java
            var menu = new javax.swing.JMenuBar();
            bar.setMenuBar(menu);
            assertThat(root.getJMenuBar()).as("menus live on the macOS screen menu bar").isSameAs(menu);
```

In `plugins/remote/src/main/java/dev/jasper/remote/ui/FlatToolBar.java`, change the Javadoc's second line to ` * FlatLaf draws them transparent with a hover highlight.`.

Delete `RetroToolbar.java` and `RetroTabs.java`.

- [ ] **Step 8: Verify**

Run: `./gradlew check`
Expected: pass.

Run: `git grep -nE 'RetroToolbar|RetroTabs|owner\.retro\(|\bretro\(\)|MODERN_HEIGHT|RETRO_HEIGHT|retroTabs' -- 'jasper-app/src' 'plugins' ':!jasper-app/src/main/java/dev/jasper/app/platform/AppIcons.java' ':!jasper-app/src/main/java/dev/jasper/app/platform/SwingAppearance.java'`
Expected: nothing. `AppIcons` and `SwingAppearance` keep their `retro()` until Task 3.

Run: `git grep -n 'SwingAppearance.retro' -- 'jasper-app/src/main'`
Expected: only lines in `platform/AppIcons.java`.

- [ ] **Step 9: Commit**

```bash
git add -A jasper-app/src plugins
git commit -m "refactor(chrome): collapse retro chrome branches to the IntelliJ layout

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: SDK 0.8.0 removes the retro icon API

**Files:**
- Delete: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/OldGnomeIcon.java`; `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceIconTest.java`, `IconCatalogTest.java`; `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeSkinIcon.java`; `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeSkinIconTest.java`
- Modify:
  - SDK: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Appearance.java`, `IconName.java`, `JasperSdk.java`; `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java`;
  - testkit: `FakeNamedIcon.java`, `FakePluginHost.java`, `FakePluginContext.java`;
  - sample plugin: `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java`;
  - all five `plugins/*/src/main/resources/plugin.toml`;
  - app: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java`, `platform/AppIcons.java` (`skin` only).
- Tests:
  - create `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java` and `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeNamedIconTest.java`;
  - rewrite `SamplePluginTest.requestsBothIconFamiliesForEitherSkin`;
  - remove `setRetroIcons`, `.retro()` and the two-argument `FakeNamedIcon` in the plugin and app tests listed in Step 5;
  - delete `HostedUiTest.selectsEverySdkCatalogChoiceAndValidatesBothArguments`;
  - in `WindowChromeContributionsTest`, `AppIcons.skin(…)` becomes `AppIcons.plugin(…)`.

**Interfaces:**
- Produces:
  - `Appearance` has `variant()`, `onChanged(...)`, `icon(IconName)` and `icon(String)` only;
  - `record FakeNamedIcon(IconName name)`;
  - `FakePluginHost` has no retro API;
  - `JasperSdk.VERSION = "0.8.0"`;
  - `AppIcons.skin` no longer exists.

- [ ] **Step 1: Write the failing tests**

Create `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java`:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.JasperSdk;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AppearanceContractTest {
    @Test void appearanceOffersOneIconFamilyInSdkZeroEight() {
        assertThat(JasperSdk.VERSION).isEqualTo("0.8.0");
        assertThat(Arrays.stream(Appearance.class.getMethods()).filter(method -> method.getName().equals("icon"))
            .map(method -> method.getParameterCount())).containsOnly(1);
    }
}
```

Create `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeNamedIconTest.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.ui.IconName;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FakeNamedIconTest {
    @Test void namedIconsAreInspectableByNameAlone() {
        try (var host = new FakePluginHost()) {
            var seen = new java.util.concurrent.atomic.AtomicReference<javax.swing.Icon>();
            host.start(new PluginInfo("dev.x.icons", "Icons", "1.0.0"), Set.of(), Set.of(), new dev.jasper.sdk.plugin.Plugin() {
                public void start(dev.jasper.sdk.plugin.PluginContext context) { seen.set(context.appearance().icon(IconName.LOCK)); }
                public void stop() { }
            });
            assertThat(seen.get()).isEqualTo(new FakeNamedIcon(IconName.LOCK));
            assertThat(seen.get().getIconWidth()).isEqualTo(16);
        }
    }
}
```

Check `PluginInfo`'s constructor against `jasper-sdk/src/main/java/dev/jasper/sdk/PluginInfo.java` and other testkit tests. If it differs, use the same form the existing `FakePluginHostTest` uses.

Change `PaletteValuesTest.java:64` to `isEqualTo("0.8.0")`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests '*AppearanceContractTest' --tests '*PaletteValuesTest' :jasper-sdk-testkit:test --tests '*FakeNamedIconTest'`
Expected: fail. The version is still 0.7.6, `icon` has a two-argument overload, and there's no one-argument `FakeNamedIcon` constructor.

- [ ] **Step 3: Implement the SDK and testkit changes**

In `Appearance.java`:
- delete the whole `icon(String modernSvgResourcePath, OldGnomeIcon retroIcon)` method and its Javadoc;
- replace the Javadoc of `icon(IconName name)` with:
  ```java
      /**
       * A host-owned 16 by 16 icon for a semantic name. Jasper owns the artwork; plugins need no
       * resource path or appearance check.
       *
       * @param name the meaning of the icon
       * @return the host's icon
       * @throws NullPointerException if name is null
       * @throws UnsupportedOperationException if an older custom host has no named catalog
       * @since 0.7.4
       */
  ```

In `IconName.java`, change the class Javadoc's first line to ` * Semantic icons supplied by Jasper; the host owns the artwork.`. Delete `OldGnomeIcon.java`. In `JasperSdk.java`, set `VERSION = "0.8.0"`. Delete `AppearanceIconTest.java` and `IconCatalogTest.java`.

In the testkit:
- `FakeNamedIcon.java` becomes:
  ```java
  package dev.jasper.sdk.testing;

  import dev.jasper.sdk.ui.IconName;
  import java.awt.Component;
  import java.awt.Graphics;
  import java.util.Objects;
  import javax.swing.Icon;

  /**
   * Inspectable, non-rendering selection from the host-owned catalog.
   * @param name requested semantic icon
   * @since 0.7.4
   */
  public record FakeNamedIcon(IconName name) implements Icon {
      /** Creates a validated selection. */
      public FakeNamedIcon { Objects.requireNonNull(name, "name"); }
      @Override public int getIconWidth() { return 16; }
      @Override public int getIconHeight() { return 16; }
      @Override public void paintIcon(Component component, Graphics graphics, int x, int y) { }
  }
  ```
- In `FakePluginHost.java`, delete the field `private boolean retroIcons;`, the method `boolean retroIcons()`, the whole `setRetroIcons` method with its Javadoc, and the line `if (retroIcons && next == Variant.DARK) throw new IllegalArgumentException("Retro uses light chrome");`.
- In `FakePluginContext.java`, delete the `OldGnomeIcon` import and the two-argument `icon(...)` override, and change the named-icon override to `return new FakeNamedIcon(java.util.Objects.requireNonNull(name, "name"));`.
- Delete `FakeSkinIcon.java` and `FakeSkinIconTest.java`.

- [ ] **Step 4: Update the plugins and the app**

In `SamplePlugin.java`, delete the `OldGnomeIcon` import and change both `icon("dev/jasper/sample/flask.svg", OldGnomeIcon.EXECUTE)` calls to `icon("dev/jasper/sample/flask.svg")`.

Set `sdk = ">=0.8.0, <0.9"` in each of `plugins/{history,snippets,sample,vault,remote}/src/main/resources/plugin.toml`.

In `HostedUi.java`, delete the override `icon(String modernSvgResourcePath, dev.jasper.sdk.ui.OldGnomeIcon retroIcon)`. In `AppIcons.java`, delete the method `skin(...)` and its Javadoc.

- [ ] **Step 5: Update the tests that used the retro API**

- Rewrite `SamplePluginTest.requestsBothIconFamiliesForEitherSkin` as:

```java
    @Test void theDemoActionAndStatusItemUseThePluginFlaskSvg() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.sample", Map.of("demo_ui", true));
            var delegate = new SamplePlugin();
            var paths = new java.util.ArrayList<String>();
            host.start(INFO, Set.of(), Set.of(), new dev.jasper.sdk.plugin.Plugin() {
                public void start(dev.jasper.sdk.plugin.PluginContext context) throws Exception {
                    var appearance = (dev.jasper.sdk.ui.Appearance) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[]{dev.jasper.sdk.ui.Appearance.class}, (proxy, method, args) -> {
                            if (method.getName().equals("icon") && args != null && args.length == 1 && args[0] instanceof String path)
                                paths.add(path);
                            try { return method.invoke(context.appearance(), args); }
                            catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                        });
                    var wrapped = (dev.jasper.sdk.plugin.PluginContext) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[]{dev.jasper.sdk.plugin.PluginContext.class}, (proxy, method, args) -> {
                            if (method.getName().equals("appearance")) return appearance;
                            try { return method.invoke(context, args); }
                            catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                        });
                    delegate.start(wrapped);
                }
                public void stop() { delegate.stop(); }
            });
            assertThat(host.failures()).isEmpty();
            assertThat(paths).containsExactly("dev/jasper/sample/flask.svg", "dev/jasper/sample/flask.svg");
        }
    }
```

- In `RemotePluginTest`, `ManagedImportIntegrationTest`, `SftpPanelTest`, `VaultPluginTest`, `VaultManagerWindowTest` and `jasper-app/src/test/java/dev/jasper/app/plugins/ManagedVaultLoadingTest`:
  - delete every `setRetroIcons(...)` line;
  - delete assertions about `.retro()`;
  - change `new FakeNamedIcon(X, false)` (or `, retro`) to `new FakeNamedIcon(X)`.
- In `HostedUiTest`, delete `selectsEverySdkCatalogChoiceAndValidatesBothArguments`.
- In `WindowChromeContributionsTest`, change `dev.jasper.app.platform.AppIcons.skin(getClass().getClassLoader(), "dev/jasper/app/icons/intellij/find.svg", "LOCK")` to `dev.jasper.app.platform.AppIcons.plugin(getClass().getClassLoader(), "dev/jasper/app/icons/intellij/find.svg")`.
- Append to `jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java`, which is in the same package as the package-private `Version` and `VersionRange`:

```java
    @Test void pluginsBuiltForSdkZeroSevenAreIncompatibleWithZeroEight() {
        var host = Version.parse(dev.jasper.sdk.JasperSdk.VERSION);
        assertThat(VersionRange.parse(">=0.7.4, <0.8").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.8.0, <0.9").contains(host)).isTrue();
    }
```

- [ ] **Step 6: Verify**

Run: `./gradlew check`
Expected: pass. This includes `verifySdkArchitecture` and `verifyPluginArchitecture`, and `BundledSamplePluginTest`, which loads every bundled plugin under 0.8.0.

Run: `git grep -nE 'OldGnomeIcon|FakeSkinIcon|setRetroIcons|retroIcons|AppIcons\.skin|<0\.8"' -- 'jasper-sdk' 'jasper-sdk-testkit' 'plugins' 'jasper-app/src'`
Expected: nothing, except `jasper-app/src/main/resources/dev/jasper/app/icons/oldgnome-sdk/NOTICE.md`, which Task 3 deletes.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-sdk jasper-sdk-testkit plugins jasper-app/src
git commit -m "feat(sdk)!: remove the retro icon API in SDK 0.8.0

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Remove the raster icon catalogs and `SwingAppearance`

**Files:**
- Delete:
  - `jasper-app/src/main/java/dev/jasper/app/platform/GnomeIcons.java`, `OldGnomeCatalog.java`, `SkinIcon.java`, `SwingAppearance.java`;
  - `jasper-app/src/main/resources/dev/jasper/app/icons/{gnome2,tango,oldgnome-sdk}/`;
  - `jasper-app/src/test/java/dev/jasper/app/platform/GnomeIconsTest.java`, `OldGnomeCatalogTest.java`, `SkinIconsTest.java`.
- Modify: `platform/AppIcons.java`, `workspace/WindowChrome.java` (three icon calls), `NamedIconsTest.java`, `PackagedResourcesTest.java` (if it names the removed resources), `AppIconsTest.java`, and any test calling `AppIcons.toolbarIcon`/`forToolbar`.

**Interfaces:**
- Consumes: Task 1 left `SwingAppearance.retro()` only in `AppIcons`, and Task 2 removed `AppIcons.skin`.
- Produces: `AppIcons` has `icon(String)`, `chrome(String)`, `plugin(ClassLoader, String)` and `named(String)`. `toolbarIcon` and `forToolbar` no longer exist.

- [ ] **Step 1: Write the failing test**

Append to `jasper-app/src/test/java/dev/jasper/app/platform/AppIconsTest.java`:

```java
    @Test void everyApplicationIconIsSixteenPixelIntellijArtworkAndUnknownNamesFail() throws Exception {
        edt(() -> {
            for (String name : new String[]{"square-plus", "app-window", "columns-2", "maximize", "search", "settings",
                    "refresh", "command", "history", "bookmark", "close", "exit"}) {
                var icon = AppIcons.icon(name);
                assertThat(icon).as(name).isInstanceOf(com.formdev.flatlaf.extras.FlatSVGIcon.class);
                assertThat(icon.getIconWidth()).as(name).isEqualTo(16);
            }
            assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.icon("unknown")).withMessageContaining("unknown");
            assertThat(java.util.Arrays.stream(AppIcons.class.getMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("toolbarIcon", "forToolbar");
        });
    }
```

Add `import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;` if the file lacks it.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.platform.AppIconsTest`
Expected: fail, because `toolbarIcon` and `forToolbar` still exist.

- [ ] **Step 3: Implement**

Replace `AppIcons.java` with:

```java
package dev.jasper.app.platform;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.util.Map;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Bundled IntelliJ and Tabler SVG icons; no network access is needed to render chrome. SVGs render
 * as authored: FlatLaf's global colour filter remaps IntelliJ palette colours for the running theme.
 */
public final class AppIcons {
    private static final String INTELLIJ = "dev/jasper/app/icons/intellij/";
    /** Artwork for the application's own chrome names. */
    private static final Map<String, String> CHROME = Map.ofEntries(
        Map.entry("square-plus", "add"), Map.entry("app-window", "moveToWindow"),
        Map.entry("columns-2", "splitVertically"), Map.entry("maximize", "expandComponent"),
        Map.entry("search", "find"), Map.entry("settings", "gearPlain"), Map.entry("refresh", "refresh"),
        Map.entry("command", "execute"), Map.entry("history", "history"), Map.entry("bookmark", "bookmark"),
        Map.entry("close", "close"), Map.entry("exit", "exit"));
    private AppIcons() {}

    /** A 16-pixel application chrome icon by Jasper name, for example {@code "square-plus"}. */
    public static Icon icon(String name) {
        String artwork = CHROME.get(name);
        if (artwork == null) throw new IllegalArgumentException("Unknown application icon: " + name);
        return chrome(artwork);
    }

    /** Chrome artwork by IntelliJ file name, for example {@code "closeHovered"}. */
    public static Icon chrome(String intellijName) {
        return svg(AppIcons.class.getClassLoader(), INTELLIJ + intellijName + ".svg");
    }

    /** A plugin's 16-pixel SVG drawn as authored; IntelliJ light-palette colours follow the theme. */
    public static Icon plugin(ClassLoader loader, String svgResourcePath) {
        Objects.requireNonNull(loader, "loader");
        return svg(loader, svgResourcePath);
    }

    /** Host-owned semantic artwork, independent of any plugin's resource loader. */
    public static Icon named(String name) {
        String resource = NamedIcons.resource(name);
        return NamedIcons.tinted(name) ? tinted(resource) : svg(AppIcons.class.getClassLoader(), resource);
    }

    private static FlatSVGIcon svg(ClassLoader loader, String path) {
        if (path == null || loader.getResource(path) == null)
            throw new IllegalArgumentException("No such icon resource: " + path);
        return new FlatSVGIcon(path, 16, 16, loader);
    }

    /** Monochrome Tabler fallback for names IntelliJ has no artwork for; it follows the chrome foreground. */
    private static Icon tinted(String path) {
        FlatSVGIcon icon = svg(AppIcons.class.getClassLoader(), path);
        return icon.setColorFilter(new FlatSVGIcon.ColorFilter(source -> themed("Jasper.chromeForeground", source)));
    }

    private static Color themed(String key, Color source) {
        Color target = UIManager.getColor(key);
        if (target == null) return source;
        return new Color(target.getRed(), target.getGreen(), target.getBlue(), source.getAlpha());
    }
}
```

In `WindowChrome.java`:
- `split.setIcon(AppIcons.toolbarIcon("columns-2"))` → `split.setIcon(AppIcons.icon("columns-2"))`
- `button.setIcon(icon != null ? AppIcons.forToolbar(icon) : AppIcons.toolbarIcon("command"))` → `button.setIcon(icon != null ? icon : AppIcons.icon("command"))`
- `button.setIcon(AppIcons.toolbarIcon(icon))` → `button.setIcon(AppIcons.icon(icon))`

Delete `GnomeIcons.java`, `OldGnomeCatalog.java`, `SkinIcon.java` and `SwingAppearance.java`, the three icon resource directories, and `GnomeIconsTest`, `OldGnomeCatalogTest` and `SkinIconsTest`.

In `NamedIconsTest`, delete every assertion that references `OldGnomeCatalog`, `SkinIcon` or a 28-pixel toolbar variant. Keep the modern assertions. Replace `AppIcons.forToolbar(x)` with `x`, so that a `forToolbar(icon).getIconWidth() == 16` check becomes `icon.getIconWidth() == 16`.

Fix every other test that calls `AppIcons.toolbarIcon(…)` (→ `AppIcons.icon(…)`) or `AppIcons.forToolbar(x)` (→ `x`). The `git grep` in Step 5 lists them. If `PackagedResourcesTest` requires any file under `icons/gnome2`, `icons/tango` or `icons/oldgnome-sdk` to be packaged, delete only those expectations.

Task 1 guaranteed that no production code other than `AppIcons` read `SwingAppearance`, and that is verified in Step 5. If a test references `SwingAppearance`, replace `SwingAppearance.retro()` with `false` and simplify the test by R1.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.*' --tests 'dev.jasper.app.workspace.WindowChromeContributionsTest' --tests 'dev.jasper.app.plugins.HostedUiTest'`
Expected: pass.

- [ ] **Step 5: Verify**

Run: `./gradlew check`
Expected: pass.

Run: `git grep -nE 'SwingAppearance|GnomeIcons|OldGnomeCatalog|SkinIcon|forToolbar|toolbarIcon|icons/gnome2|icons/tango|icons/oldgnome' -- 'jasper-app/src' 'plugins' 'jasper-buddy/src'`
Expected: nothing.

Run: `ls jasper-app/src/main/resources/dev/jasper/app/icons`
Expected: exactly `app`, `intellij` and `standard`.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app/src
git commit -m "refactor(icons): drop the GNOME, Tango and OldGNOME raster catalogs

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Remove the style: `ThemeStyle`, `ui.theme.style`, Metal and the retro palette

**Files:**
- Delete:
  - `jasper-app/src/main/java/dev/jasper/app/config/ThemeStyle.java`;
  - `appearance/MetalDefaults.java` and `appearance/RetroPalette.java`;
  - `jasper-app/src/test/java/dev/jasper/app/appearance/RetroThemeTest.java`.
- Modify production:
  - `appearance/BuiltinTheme.java`, `appearance/ThemeController.java`;
  - `config/ConfigLoader.java`, `config/ConfigSnapshot.java`, `config/ConfigTemplate.java`, `config.example.toml`;
  - `application/ConfigurationController.java`, `application/JasperApplication.java`;
  - `bootstrap/ApplicationBootstrap.java`.
- Tests:
  - the R4 script across all tests;
  - R3 deletions: `UiTypographyTest.retroFontReload…`, the retro test in `TerminalColorsThemeTest` (~line 53), the two style tests in `ConfigLoaderTest` (~353, ~362), the style test in `ConfigSnapshotBuilderTest` (~20), three retro tests in `ConfigurationControllerTest` (~618, ~638, ~649), the retro test and `RETRO_FIXTURE` in `JasperApplicationPluginsTest` (~235, ~252), and `renderMetalEditorAtBothScales` in `plugins/vault/src/test/java/dev/jasper/vault/ui/EntryEditorTest.java`;
  - edits to `ApplicationBootstrapTest` (the retro/modern loop), `ConfigTemplateTest` (`ui.theme` keys), `CommandPalettePreview` (`case RETRO`) and any other preview class.

**Interfaces:**
- Produces:
  - `ThemeController(Appearance saved)` and package-private `ThemeController(Appearance saved, Predicate<BuiltinTheme> installer)`;
  - `ThemeController()` and `ThemeController(Predicate<BuiltinTheme>)` are unchanged;
  - `ThemeController.style()` no longer exists;
  - `BuiltinTheme { DARK, LIGHT }`;
  - `ConfigSnapshot` has no `style` component and `Builder` has no `style(...)`;
  - `ApplicationBootstrap.configureDesktopProperties()` takes no parameter.

- [ ] **Step 1: Write the failing tests**

Append to `jasper-app/src/test/java/dev/jasper/app/config/ConfigLoaderTest.java`:

```java
    @Test void aLeftoverStyleLineIsAnOrdinaryUnknownSettingAndChangesNothing() {
        var result = parse("[ui.theme]\nstyle = 'retro'\nvariant = 'light'\n");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().variant()).isEqualTo(Appearance.LIGHT);
        assertThat(result.diagnostics()).singleElement().satisfies(problem -> {
            assertThat(problem.key()).isEqualTo("ui.theme.style");
            assertThat(problem.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
        });
    }
```

Confirm how unknown keys are reported in this file first; existing unknown-key tests show the key format and severity. If unknown keys use a different key format (for example quoted), match it and record it.

Append to `jasper-app/src/test/java/dev/jasper/app/bootstrap/ApplicationBootstrapTest.java`:

```java
    @Test void desktopPropertiesAlwaysUseTheMacScreenMenuBar() {
        String previous = System.getProperty("apple.laf.useScreenMenuBar");
        try {
            System.clearProperty("apple.laf.useScreenMenuBar");
            ApplicationBootstrap.configureDesktopProperties();
            assertThat(System.getProperty("apple.laf.useScreenMenuBar")).isEqualTo("true");
            assertThat(System.getProperty("apple.awt.application.appearance")).isEqualTo("system");
        } finally {
            if (previous == null) System.clearProperty("apple.laf.useScreenMenuBar");
            else System.setProperty("apple.laf.useScreenMenuBar", previous);
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*ConfigLoaderTest' --tests '*ApplicationBootstrapTest'`
Expected: fail. `style` is still a known key, and `configureDesktopProperties()` needs an argument.

- [ ] **Step 3: Implement the appearance core**

Replace `BuiltinTheme.java` with:

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.terminal.config.Palette;

/** The bundled Light and Dark presets; MATCH terminal colours use the preset's palette. */
public enum BuiltinTheme {
    DARK("dark", "Dark", Palette.jasperDark()),
    LIGHT("light", "Light", Palette.jasperLight());

    private final String id;
    private final String label;
    private final Palette palette;

    BuiltinTheme(String id, String label, Palette palette) {
        this.id = id; this.label = label; this.palette = palette;
    }

    static BuiltinTheme of(Appearance appearance) { return appearance == Appearance.LIGHT ? LIGHT : DARK; }

    String id() { return id; }
    String label() { return label; }
    public Palette palette() { return palette; }
    public Appearance appearance() { return this == DARK ? Appearance.DARK : Appearance.LIGHT; }
}
```

Replace `ThemeController.java` with:

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.config.UiFontConfig;
import java.awt.Font;
import javax.swing.plaf.FontUIResource;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import dev.jasper.app.lifecycle.Subscription;
import java.util.function.Predicate;
import javax.swing.*;

/** Application-owned, EDT-confined theme selection and value subscriptions. */
public final class ThemeController {

    public static final class InstallationFailure extends IllegalStateException {
        InstallationFailure(BuiltinTheme theme, RuntimeException cause) {
            super("Could not apply theme: " + theme.label(), cause);
        }
    }

    private final Set<BiConsumer<ResolvedTheme, Boolean>> listeners = new LinkedHashSet<>();
    private final Predicate<BuiltinTheme> installer;
    private ThemeState state = ThemeState.defaults();
    private UiFontConfig uiFont = UiFontConfig.defaults();
    private final Font platformFont;
    private final float platformLabelSize;
    private static final List<String> FORM_FONTS = List.of("Label.font", "List.font", "TextField.font",
        "PasswordField.font", "FormattedTextField.font", "TextArea.font", "ComboBox.font");

    public ThemeController() { this(Appearance.DARK); }
    public ThemeController(Appearance saved) { this(saved, ThemeController::install); }
    public ThemeController(Predicate<BuiltinTheme> installer) { this(Appearance.DARK, installer); }
    ThemeController(Appearance saved, Predicate<BuiltinTheme> installer) {
        requireEdt();
        this.installer = Objects.requireNonNull(installer);
        this.state = ThemeState.defaults().configure(Objects.requireNonNull(saved));
        // Swing otherwise tries the component's plugin loader for third-party LAF delegates.
        // Only UI delegate lookup belongs to the app; plugin class visibility stays isolated.
        UIManager.put("ClassLoader", ThemeController.class.getClassLoader());
        UIManager.put("defaultFont", null);
        FORM_FONTS.forEach(key -> UIManager.put(key, null));
        UIManager.put("Jasper.uiFontFamilyOverride", false);
        installOrThrow(state.resolve().chrome());
        platformFont = UIManager.getFont("defaultFont") != null ? UIManager.getFont("defaultFont") : UIManager.getFont("Label.font");
        platformLabelSize = UIManager.getFont("Label.font").getSize2D();
    }
    public ResolvedTheme current() { requireEdt(); return state.resolve(); }
    public Appearance choice() { requireEdt(); return state.choice(); }
    /** The effective terminal colours. */
    public TerminalColors terminalColors() { requireEdt(); return state.terminalChoice(); }
    public void selectAppearance(Appearance choice) {
        requireEdt(); apply(state.choose(Objects.requireNonNull(choice)));
    }
    /** A temporary View choice for every window. */
    public void selectTerminalColors(TerminalColors choice) {
        requireEdt(); apply(state.chooseTerminal(Objects.requireNonNull(choice)));
    }
    public void configure(Appearance saved) { configure(saved, uiFont); }
    public void configure(Appearance saved, UiFontConfig font) { configure(saved, state.terminalSaved(), font); }
    public void configure(Appearance saved, TerminalColors terminal, UiFontConfig font) {
        requireEdt();
        apply(state.configure(Objects.requireNonNull(saved)).configureTerminal(Objects.requireNonNull(terminal)),
            Objects.requireNonNull(font));
    }
    public void select(BuiltinTheme theme) { selectAppearance(Objects.requireNonNull(theme).appearance()); }
    private void apply(ThemeState candidate) { apply(candidate, uiFont); }
    private void apply(ThemeState candidate, UiFontConfig font) {
        ResolvedTheme previous = state.resolve(), next = candidate.resolve();
        boolean chromeChanged = previous.chrome() != next.chrome() || !uiFont.equals(font);
        boolean choiceChanged = state.choice() != candidate.choice() || state.terminalChoice() != candidate.terminalChoice();
        if (chromeChanged) {
            installFontDefaults(font);
            try { installOrThrow(next.chrome()); }
            catch (RuntimeException failure) { installFontDefaults(uiFont); throw failure; }
        }
        uiFont = font;
        UIManager.put("Jasper.uiFontFamilyOverride", !font.family().equalsIgnoreCase("system"));
        state = candidate;
        if (chromeChanged || !previous.equals(next) || choiceChanged)
            for (var listener : List.copyOf(listeners)) listener.accept(next, chromeChanged);
    }

    private FontUIResource resolveFont(UiFontConfig choice) {
        Font family = choice.family().equalsIgnoreCase("system") ? platformFont : new Font(choice.family(), Font.PLAIN, 13);
        if (family.getFamily().equals(Font.DIALOG) && !choice.family().equalsIgnoreCase(Font.DIALOG)) family = platformFont;
        // Retain the platform's title/menu offset relative to ordinary form text.
        float size = choice.size() == 0 ? platformFont.getSize2D()
            : choice.size() + platformFont.getSize2D() - platformLabelSize;
        return new FontUIResource(family.deriveFont(Font.PLAIN, size));
    }

    private void installFontDefaults(UiFontConfig choice) {
        boolean defaults = choice.equals(UiFontConfig.defaults());
        Font family = resolveFont(choice);
        UIManager.put("defaultFont", defaults ? null : family);
        // FlatLaf scales relative offsets with the UI scale. Explicit base-control fonts preserve
        // exact configured point sizes, including fractions, without changing unset defaults.
        FontUIResource form = new FontUIResource(family.deriveFont(choice.size() == 0 ? platformLabelSize : choice.size()));
        for (String key : FORM_FONTS) UIManager.put(key, defaults ? null : form);
    }

    /** Replays current appearance; the subscribing owner closes its registration on disposal. */
    public Subscription subscribe(BiConsumer<ResolvedTheme, Boolean> listener) {
        requireEdt(); Objects.requireNonNull(listener);
        if (!listeners.add(listener)) return new Subscription(() -> {});
        try { listener.accept(current(), true); }
        catch (RuntimeException | Error failure) { listeners.remove(listener); throw failure; }
        return new Subscription(() -> { requireEdt(); listeners.remove(listener); });
    }

    private void installOrThrow(BuiltinTheme theme) {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            if (!installer.test(theme)) throw new IllegalStateException("Could not apply theme: " + theme.label());
        } catch (RuntimeException failure) {
            // A failed setup may have installed a LAF before one of its initialization hooks failed.
            if (UIManager.getLookAndFeel() != previous) {
                try { UIManager.setLookAndFeel(previous); }
                catch (UnsupportedLookAndFeelException | RuntimeException restoreFailure) { failure.addSuppressed(restoreFailure); }
            }
            throw new InstallationFailure(theme, failure);
        }
    }

    private static boolean defaultsRegistered;
    static boolean install(BuiltinTheme theme) {
        requireEdt();
        if (!defaultsRegistered) {
            FlatLaf.registerCustomDefaultsSource("dev.jasper.app.themes");
            defaultsRegistered = true;
        }
        return theme == BuiltinTheme.LIGHT ? FlatLightLaf.setup() : FlatDarkLaf.setup();
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme operations require the EDT");
    }
}
```

Delete `MetalDefaults.java`, `RetroPalette.java` and `ThemeStyle.java`.

- [ ] **Step 4: Implement the configuration and startup changes**

In `ConfigLoader.java`:
- change the `ui.theme` known-keys entry to `Set.of("variant", "terminal")`;
- delete the field `private ThemeStyle style = ThemeStyle.MODERN;`;
- delete the `case "ui.theme.style" -> …` case, which spans two lines;
- in the snapshot construction, remove the `style, ` argument so that it reads `… backgroundEnabled, plugins, new UiFontConfig(uiFontFamily, uiFontSize), terminalColors);`.

In `ConfigSnapshot.java`:
- delete the `ThemeStyle style` record component, so the tail is `Map<String, Map<String, Object>> plugins, UiFontConfig uiFont, TerminalColors terminalColors)`;
- delete `Objects.requireNonNull(style, "style");`;
- delete the compatibility constructor whose last parameters are `ThemeStyle style, UiFontConfig uiFont` and the one whose last parameter is `ThemeStyle style`;
- the constructor ending `Map<String, Map<String, Object>> plugins, UiFontConfig uiFont)` now delegates with `plugins, uiFont, TerminalColors.MATCH);`;
- the constructor ending `Map<String, Map<String, Object>> plugins)` delegates with `plugins, UiFontConfig.defaults(), TerminalColors.MATCH);`;
- in `Builder`, delete the field `style`, the line `style = source.style();` and the setter `style(ThemeStyle)`, and change `build()` to pass `plugins, uiFont, terminalColors`.

In `ConfigTemplate.java`, the `[ui.theme]` block keeps only the variant and terminal lines. Delete the `style` comment lines and the `# style = "modern"` line, and change `# Variant applies only to modern; retro always uses light controls and a dark terminal.` to `# Theme variant for the UI chrome.`. Apply the same edit to `config.example.toml`: delete the style comments and `style = "modern"`, and remove "modern only"/"retro" wording from the variant and terminal comments.

In `ConfigurationController.java`, delete the whole `if (state.snapshot().style() != themes.style()) merged.add(…);` statement.

In `JasperApplication.java`, change `themes = new ThemeController(startup.style(), startup.variant());` to `themes = new ThemeController(startup.variant());`.

In `ApplicationBootstrap.java`:
- change the call to `configureDesktopProperties();`;
- replace the method with:
  ```java
      /** macOS reads these settings during toolkit/LAF initialization; never change them on reload. */
      static void configureDesktopProperties() {
          System.setProperty("apple.awt.application.appearance", "system");
          System.setProperty("apple.laf.useScreenMenuBar", "true");
      }
  ```
- remove the `ThemeStyle` import.

- [ ] **Step 5: Update the tests**

Run the R4 rewrite:

```bash
python3 - <<'PY'
import pathlib, re
roots = ["jasper-app/src/test", "plugins"]
pattern = re.compile(r"new\s+(dev\.jasper\.app\.appearance\.)?ThemeController\(\s*(dev\.jasper\.app\.config\.)?ThemeStyle\.MODERN\s*,\s*")
for root in roots:
    for path in pathlib.Path(root).rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        updated = pattern.sub(lambda m: "new " + (m.group(1) or "") + "ThemeController(", text)
        updated = re.sub(r"^import dev\.jasper\.app\.config\.ThemeStyle;\n", "", updated, flags=re.M)
        if updated != text:
            path.write_text(updated, encoding="utf-8"); print("updated", path)
PY
git grep -n 'ThemeStyle' -- 'jasper-app/src' 'plugins'
```

Every remaining `ThemeStyle` hit is in a retro-subject test or fixture. Apply R3 to each one: delete `RetroThemeTest`, and the methods and fixtures named in **Files**. Collapse the retro/modern loop in `ApplicationBootstrapTest` by R2 with the new no-argument `configureDesktopProperties()`. Delete `case RETRO` branches in preview classes such as `CommandPalettePreview`.

`ConfigSnapshot` constructor calls in tests that passed `ThemeStyle.X, uiFont` or `ThemeStyle.X` drop that argument. `ConfigSnapshot.Builder.style(...)` calls are deleted.

Additional test edits:
- In `ConfigTemplateTest`, change the `ui.theme` key expectation to `containsExactlyInAnyOrder("variant", "terminal")`.
- In `ConfigLoaderTest`, delete the two style tests (R3).
- Delete retro-only assertions in `ThemeControllerTest` and `TerminalColorsThemeTest`, for example `retroKeepsItsPaletteForEveryTerminalChoice`.
- `UiTypographyTest` loses its retro method.
- In `EntryEditorTest`, delete `renderMetalEditorAtBothScales`.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-app:test --tests '*ConfigLoaderTest' --tests '*ApplicationBootstrapTest'`
Expected: pass.

Run: `./gradlew check`
Expected: pass.

- [ ] **Step 7: Verify the removal**

Run: `git grep -nE 'ThemeStyle|MetalDefaults|RetroPalette|BuiltinTheme\.RETRO|Jasper\.retro|ui\.theme\.style|\bretro\b|\bRetro[A-Z]|MetalLookAndFeel|OceanTheme' -- 'jasper-app/src' 'jasper-terminal/src' 'jasper-buddy/src' 'jasper-sdk/src' 'jasper-sdk-testkit/src' 'plugins' 'config.example.toml'`
Expected: nothing, except the new `ConfigLoaderTest.aLeftoverStyleLineIsAnOrdinaryUnknownSettingAndChangesNothing`, which contains the strings `style = 'retro'` and `ui.theme.style`. List any other hit in the report and remove it.

- [ ] **Step 8: Commit**

```bash
git add -A jasper-app/src plugins config.example.toml
git commit -m "refactor(appearance): remove the retro style, Metal and ui.theme.style

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Documentation and final gates

**Files:**
- Modify: `README.md`, `jasper-app/README.md`, `jasper-sdk/README.md`, `docs/configuration.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/sdk-architecture.md`, `docs/sdk-icons.md`, `docs/plugin-authoring.md`, `docs/remote-7c-verification.md`, `docs/STATUS.md`; this plan's and the spec's status lines.

- [ ] **Step 1: Remove retro and style from current docs**

For each file, delete the listed material and fix any sentence that referred to it. Do not add text explaining the removal.
- **`README.md`:** delete any `style = …` line or retro mention in the configuration example.
- **`jasper-app/README.md`:** remove retro from the change-workflow notes, for example "check modern and retro".
- **`jasper-sdk/README.md`:**
  - rename "Icons for both skins (0.7.4; 0.7.6 adds SPLIT/ZOOM/TERMINAL/SERVER)" to "Icons (SDK 0.8)";
  - delete every mention of `OldGnomeIcon`, `icon(svg, OldGnomeIcon)`, retro and 28 px variants;
  - state that bundled plugins declare `sdk = ">=0.8.0, <0.9"`.
- **`docs/configuration.md`:**
  - delete the `style` line and its comment from the `[ui.theme]` example;
  - delete the `ui.theme.style` table row;
  - delete the whole "Retro Metal appearance" section;
  - in the variant and terminal rows and sections, delete "Live in modern mode; retained but ignored in retro", "retro ignores it" and similar, leaving "Live";
  - in "Interface typography", delete retro remarks.
- **`docs/app-architecture.md`:** delete the "Restart-required appearance style" section and any retro mention.
- **`docs/app-maintenance.md`:** replace "Maintaining SDK skin icons" with a short "Maintaining SDK icons" section. It says `IconName` artwork lives in `NamedIcons` (IntelliJ SVG or tinted Tabler SVG under `icons/standard/`), and that each name needs a manifest row when it uses standard artwork.
- **`docs/sdk-architecture.md`:**
  - remove `OldGnomeIcon` and retro from "Skin-aware icons (SDK 0.7.4)", "Host-owned semantic catalog" and "Chrome icons and session tab icons (SDK 0.7.6)";
  - add at the top of the icons material: "SDK 0.8.0 removed `OldGnomeIcon` and `Appearance.icon(String, OldGnomeIcon)`; plugins use `icon(IconName)` or `icon(String)`."
- **`docs/sdk-icons.md`:**
  - retitle it "Plugin icons";
  - delete "Named catalog: retro artwork" and every retro reference in "Custom artwork remains supported" and "Test selection without a GUI";
  - `FakeNamedIcon` equality examples become `new FakeNamedIcon(IconName.LOCK)`.
- **`docs/plugin-authoring.md`:** delete the retro, Metal and `OldGnomeIcon` sentences in "Actions and where they appear", "Consistent buttons, flexible layouts" and "Panels, the rail and windows". The SDK range examples become `sdk = ">=0.8.0, <0.9"`.
- **`docs/remote-7c-verification.md`:** remove retro from "Automated evidence" and "Build and run for acceptance".

- [ ] **Step 2: Run the final gates**

Run: `./gradlew check`
Expected: pass. Record the counts from the XML, using the Python counter in the AGENTS.md instructions or `docs/STATUS.md`.

Run: `git grep -niE '\bretro\b|oldgnome|ui\.theme\.style|metal look|metal/ocean' -- README.md jasper-app/README.md jasper-sdk/README.md docs/configuration.md docs/app-architecture.md docs/app-maintenance.md docs/sdk-architecture.md docs/sdk-icons.md docs/plugin-authoring.md docs/remote-7c-verification.md`
Expected: nothing.

Run the AGENTS.md source-hygiene check. It must print nothing.

- [ ] **Step 3: Record the status**

Add at the top of the current-work list in `docs/STATUS.md`:

```markdown
- **One layout** (branch `claude/theme-engine`, plan `docs/superpowers/plans/2026-09-25-jasper-single-layout.md`):
  the retro style, Metal, the GNOME/Tango/OldGNOME rasters and `ui.theme.style` are gone; every
  window uses the IntelliJ-style chrome. SDK 0.8.0 removes `OldGnomeIcon` and
  `Appearance.icon(String, OldGnomeIcon)`; bundled plugins require `>=0.8.0, <0.9`. Next: the
  theme engine with IntelliJ-format themes and IntelliJ Light (part 2). Not merged, not pushed.
```

Set the spec's status line to "Implemented on `claude/theme-engine`; visual acceptance pending." Set this plan's **Status** line to "All tasks complete; `./gradlew check` passed (N tests, 0 failures, 0 errors, S skipped)", using the counts from Step 2.

- [ ] **Step 4: Commit**

```bash
git add -A README.md jasper-app/README.md jasper-sdk/README.md docs
git commit -m "docs: describe the single layout and SDK 0.8 icons

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
