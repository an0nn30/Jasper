# Title-bar command centre, plan B: centred field, one-colour palette, Windows/Linux title bar

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Written 2026-09-29 on `claude/titlebar-plan-b`. Not started.
Record every deviation from this text here and in `docs/STATUS.md`.

**Goal:** Keep the title search field dead centre on every window, make the palette card one colour, and give Windows and Linux a custom title bar: app icon, menus that fold into "…" and then ☰, the centred field, the toolbar, and the window buttons. A `window.native_decorations` setting opts out.

**Architecture:**
- `WindowHeader` always centres the field on the root pane. The sides give way first, and only then does the field shrink:
  - the right side: toolbar labels drop, then buttons move into ⋮;
  - the left side: the menus fold through a new leading hook.
- On Windows and Linux, FlatLaf's own window decorations (`TitleBarStyle.CUSTOM`) embed the menu bar in the title pane. A new `CollapsingMenuBar` hosts the header, which spans the bar underneath the menus.
- `TitleBarStyle` is chosen once at startup from the OS and the setting.

**Tech Stack:** Java 25 (JBR), Swing, FlatLaf 3.7 (bundled Windows and Linux natives), JUnit 5 and AssertJ, Gradle wrapper.

**Spec:** [`docs/superpowers/specs/2026-09-28-jasper-titlebar-command-center-design.md`](../specs/2026-09-28-jasper-titlebar-command-center-design.md), sections 1, 4 and 6 (Plan B), plus the 2026-09-29 amendments in Task 7.

## Global Constraints

- Build with `./gradlew`, never a system `gradle`; `./gradlew check` stays green. Tests are headless; never launch the GUI.
- Work in `.worktrees/titlebar-plan-b` on branch `claude/titlebar-plan-b`, and verify the branch before every commit. End commit messages with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- No SDK change. No raw control, private-use or unpaired surrogate characters in source.
- **The field is always centred on the window**, within ±1 px of the root pane's centre.
- Squeeze order:
  - right side: toolbar labels drop, then buttons go into ⋮;
  - left side (Windows/Linux): menus fold into "…", then into ☰;
  - after both sides: the field shrinks symmetrically to 240, then collapses to its magnifier, still centred.
- `window.native_decorations`:
  - boolean, default `false`, `Applies.RESTART`;
  - ignored on macOS, and its Settings row is hidden there.
- Folded menus are the same `JMenu` objects, never copies.
- The palette card uses `List.background` everywhere: query row, query field, list, step form and hint bar.

## Verified FlatLaf 3.7 behaviour (checked against the jar on 2026-09-29; do not re-derive)

- **Client properties:** `FlatClientProperties` has `TITLE_BAR_SHOW_TITLE`, `TITLE_BAR_HEIGHT`, `TITLE_BAR_BACKGROUND`, `TITLE_BAR_FOREGROUND`, `MENU_BAR_EMBEDDED` and `COMPONENT_TITLE_BAR_CAPTION` (`"JComponent.titleBarCaption"`).
- **System properties:** `FlatSystemProperties.USE_WINDOW_DECORATIONS` is `"flatlaf.useWindowDecorations"`.
- **Headless title pane:** a `JRootPane` with `setWindowDecorationStyle(JRootPane.FRAME)` gets a `FlatTitlePane` even headless on macOS. The menu bar is embedded after the 16 px icon and ends before the window buttons. It needs two or three layout passes before its bounds settle.
- **Embedded menu bar width:** the embedded menu bar takes its preferred width. If it contains a trailing `Box.Filler` at least 32767 px wide (a horizontal glue), it stretches to fill the title bar.
- **Caption hit test** (`FlatTitlePane.isTitleBarCaptionAt`) decides what drags the window:
  - A component with mouse listeners and no `titleBarCaption` property is **not** caption, so it takes clicks.
  - A `Boolean` property wins. A `Function<Point, Boolean>` returning null defers to the children.
  - A component without listeners defers to its children, and blank space ends up as caption.
- **Windows default:** FlatLaf decorates frames on Windows by default. Genuine native decorations need `flatlaf.useWindowDecorations=false` before any frame exists.

## File map

Main-code paths are relative to `jasper-app/src/main/java/dev/jasper/app/` and test paths to `jasper-app/src/test/java/dev/jasper/app/`.

| Task | Files | Change |
|---|---|---|
| 1 | `workspace/WindowHeader.java`, `workspace/WindowToolbar.java`, `workspace/WindowChrome.java`; tests `WindowHeaderTest`, `WindowToolbarTest` | always-centred field, leading hook, labels give way |
| 2 | `palette/CommandPalette.java`; test `CommandPaletteTest` | one-colour card |
| 3 | `config/CoreSettings.java`, `config/ConfigSnapshot.java`, `settingsui/SchemaPage.java`, `settingsui/CorePages.java`, `config.example.toml`, `docs/configuration.md`; tests `CoreSettingsTest`, `SchemaPageTest` | `window.native_decorations` |
| 4 | `platform/TitleBarStyle.java`, `bootstrap/ApplicationBootstrap.java`; test `platform/TitleBarStyleTest` | style chosen at startup |
| 5 | `workspace/CollapsingMenuBar.java`; test `CollapsingMenuBarTest` | "…" and ☰ folding |
| 6 | `workspace/WindowChrome.java`, `workspace/WindowContent.java`, `workspace/TerminalWindow.java`, `appearance/ThemeManager.java`; tests `CustomTitleBarTest`, `WindowChromeContributionsTest`, `ThemeManagerTest` | custom title bar wired |
| 7 | the spec, `docs/app-architecture.md`, `docs/STATUS.md`, this plan | docs |

---

### Task 1: The field is always centred; the sides give way

**Files:**
- Modify: `workspace/WindowHeader.java`, `workspace/WindowToolbar.java`, `workspace/WindowChrome.java` (`setToolbarMode`)
- Test: `workspace/WindowHeaderTest.java`, `workspace/WindowToolbarTest.java`

**Interfaces:**
- Produces:
  - `WindowHeader.setLeading(IntUnaryOperator fit, IntSupplier minimum)`. `fit` gets the room left of the field and returns the width it uses. `minimum` is the least that side can ever use. The default is no leading content.
  - `int WindowHeader.centre()`: the root pane's centre in header coordinates, or `width / 2` without a root.
  - `WindowToolbar.setLabelsWanted(boolean)`.
  - Toolbar labels now come and go in `WindowToolbar.doLayout`. A button is labelled when it has a `"label"` client property, its `"keepLabel"` is not `FALSE`, and it is not the overflow button.

- [ ] **Step 1: Rewrite the header tests (failing)**

In `WindowHeaderTest`, replace every test from `theFieldIsCentredOnTheWindowAndTheToolbarPacksRight` to `aHiddenToolbarLeavesTheFieldAndInsetsKeepClearOfNativeControls` with the tests below. Keep `barHeightChangesAreForwarded`, the helpers and the imports, and add `java.util.concurrent.atomic.AtomicInteger` if missing.

```java
    @Test void theFieldIsCentredAndTheToolbarTakesTheRightSide() {
        var header = header();
        layout(header, 1400);
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(420, 4, 560, 22));
        assertThat(header.toolbar().getBounds()).isEqualTo(new Rectangle(992, 0, 408, 30));
        assertThat(header.toolbar().overflowButton().isVisible()).isFalse();
    }

    @Test void theFieldStaysCentredWhileToolbarButtonsOverflow() {
        var header = header();
        layout(header, 900);
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(170, 4, 560, 22));
        assertThat(header.toolbar().getBounds()).isEqualTo(new Rectangle(742, 0, 158, 30));
        assertThat(header.toolbar().overflowButton().isVisible()).isTrue();
        layout(header, 700);
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(70, 4, 560, 22));
        assertThat(header.toolbar().getWidth()).isEqualTo(58);
    }

    @Test void theFieldShrinksAroundTheCentreOnlyWhenASideCannotGiveWayAndThenCollapses() {
        var header = header();
        layout(header, 600);
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(52, 4, 496, 22));
        assertThat(header.field().collapsed()).isFalse();
        layout(header, 250);
        assertThat(header.field().collapsed()).isTrue();
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(114, 4, 22, 22));
        layout(header, 1400);
        assertThat(header.field().collapsed()).isFalse();
        assertThat(header.field().getWidth()).isEqualTo(560);
    }

    @Test void aHiddenToolbarAndInsetsLeaveTheFieldCentred() {
        var header = header();
        header.toolbar().setVisible(false);
        layout(header, 900);
        assertThat(header.toolbar().getWidth()).isZero();
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(170, 4, 560, 22));
        header.toolbar().setVisible(true);
        header.setBorder(BorderFactory.createEmptyBorder(0, 100, 0, 50));
        layout(header, 1400);
        assertThat(header.field().getX()).isEqualTo(420);
        assertThat(header.toolbar().getX() + header.toolbar().getWidth()).isEqualTo(1350);
        assertThat(header.getMinimumSize().width).as("symmetric around the centre").isEqualTo(2 * (100 + 12) + 22);
    }

    @Test void theLeadingSideGetsTheRoomLeftOfTheFieldAndItsMinimumCanShrinkTheField() {
        var header = header();
        var room = new AtomicInteger(-1);
        header.setLeading(space -> { room.set(space); return Math.min(space, 300); }, () -> 100);
        layout(header, 1400);
        assertThat(room).hasValue(408);
        assertThat(header.field().getX()).isEqualTo(420);
        layout(header, 600);
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(112, 4, 376, 22));
        assertThat(room).hasValue(100);
    }

    @Test void theCentreIsTheRootPanesNotTheHeaders() {
        var header = header();
        var root = new JRootPane();
        var content = new JPanel(null);
        root.setContentPane(content);
        root.setSize(1400, 800); root.doLayout(); root.getLayeredPane().doLayout();
        content.add(header);
        header.setBounds(100, 0, 1000, 30); header.doLayout();
        assertThat(header.centre()).isEqualTo(600);
        assertThat(header.field().getX()).isEqualTo(600 - 280);
    }
```

In `WindowToolbarTest`, add:

```java
    @Test void labelsGiveWayBeforeAnyControlOverflowsAndComeBackWithRoom() {
        var bar = new WindowToolbar(() -> {});
        var calls = new AtomicInteger();
        var labelled = new JButton(action("Sessions", calls)) {
            @Override public Dimension getPreferredSize() { return new Dimension(getText() == null ? 24 : 90, 24); }
        };
        labelled.putClientProperty("label", "Sessions");
        WindowToolbar.entry(labelled, 80, () -> WindowToolbar.menuItem(labelled.getAction()));
        bar.add(labelled);
        button(bar, action("Find", calls), 100);
        bar.finish();
        bar.setSize(16 + 90 + 4 + 60, 30); bar.doLayout();
        assertThat(labelled.getText()).isEqualTo("Sessions");
        bar.setSize(16 + 24 + 4 + 60, 30); bar.doLayout();
        assertThat(labelled.getText()).as("icons before overflow").isNull();
        assertThat(bar.overflowButton().isVisible()).isFalse();
        bar.setSize(60, 30); bar.doLayout();
        assertThat(bar.overflowButton().isVisible()).isTrue();
        bar.setSize(1000, 30); bar.doLayout();
        assertThat(labelled.getText()).as("labels return with room").isEqualTo("Sessions");
        bar.setLabelsWanted(false); bar.doLayout();
        assertThat(labelled.getText()).isNull();
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.WindowHeaderTest --tests dev.jasper.app.workspace.WindowToolbarTest`
Expected: compilation FAILS on `setLeading`, `centre` and `setLabelsWanted`.

- [ ] **Step 3: Implement `WindowHeader`**

1. Change the class Javadoc's last two sentences to: `The field is always centred on the window (the root pane), whatever the header's own bounds. As space runs out each side gives way first: toolbar labels, then toolbar controls into the overflow menu, and the leading content (the Windows and Linux menus) folds; only then does the field shrink around the centre to its minimum and collapse to its magnifier.`
2. Add the imports `java.util.function.IntSupplier`, `java.util.function.IntUnaryOperator`, `javax.swing.JRootPane` and `javax.swing.SwingUtilities`.
3. Add the fields and methods:

```java
    private IntUnaryOperator leading = space -> 0;
    private IntSupplier leadingMinimum = () -> 0;

    /**
     * Content left of the field (the Windows and Linux menus): {@code fit} receives the room left of the field and
     * returns the width it uses; {@code minimum} is the least it can ever use.
     */
    void setLeading(IntUnaryOperator fit, IntSupplier minimum) {
        leading = fit; leadingMinimum = minimum;
        revalidate(); repaint();
    }

    /** The window's horizontal centre in this header's coordinates. */
    int centre() {
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root == null) return getWidth() / 2;
        return root.getWidth() / 2 - SwingUtilities.convertPoint(this, 0, 0, root).x;
    }
```

4. Replace `getPreferredSize`, `getMinimumSize` and `doLayout`:

```java
    @Override public Dimension getPreferredSize() {
        Insets insets = getInsets();
        int side = Math.max(insets.left + leadingMinimum.getAsInt(), insets.right + (toolbar.isVisible() ? toolbar.fullWidth() : 0));
        return new Dimension(2 * (side + UIScale.scale(GAP)) + UIScale.scale(TitleSearchField.PREFERRED_WIDTH), height());
    }

    @Override public Dimension getMinimumSize() {
        Insets insets = getInsets();
        int side = Math.max(insets.left + leadingMinimum.getAsInt(), insets.right + (toolbar.isVisible() ? toolbar.minimumWidth() : 0));
        return new Dimension(2 * (side + UIScale.scale(GAP)) + fieldHeight(height()), height());
    }

    @Override public void doLayout() {
        Insets insets = getInsets();
        int width = getWidth(), height = getHeight(), gap = UIScale.scale(GAP);
        int left = Math.min(insets.left, width), right = Math.max(left, width - insets.right);
        int centre = centre();
        int preferred = UIScale.scale(TitleSearchField.PREFERRED_WIDTH), minimum = UIScale.scale(TitleSearchField.MINIMUM_WIDTH);
        int toolbarMinimum = toolbar.isVisible() ? toolbar.minimumWidth() : 0;
        // Each side gives way before the field does; the field keeps its centre.
        int half = Math.min(centre - left - leadingMinimum.getAsInt() - gap, right - centre - gap - toolbarMinimum);
        int fieldHeight = fieldHeight(height), y = (height - fieldHeight) / 2;
        int fieldWidth = Math.min(preferred, 2 * half);
        boolean collapsed = fieldWidth < minimum;
        if (collapsed) fieldWidth = 2 * half >= fieldHeight ? fieldHeight : 0;
        field.setCollapsed(collapsed);
        int fieldX = centre - fieldWidth / 2;
        field.setBounds(fieldX, y, fieldWidth, fieldWidth == 0 ? 0 : fieldHeight);
        leading.applyAsInt(Math.max(0, fieldX - gap - left));
        int toolbarX = Math.min(right, fieldX + fieldWidth + gap);
        toolbar.setBounds(toolbarX, 0, toolbar.isVisible() ? right - toolbarX : 0, height);
    }
```

- [ ] **Step 4: Implement label give-way in `WindowToolbar`**

Add:

```java
    private boolean labelsWanted = true;

    /** Whether labelled controls (Split, contributed buttons and dropdowns) show their labels when they fit. */
    void setLabelsWanted(boolean value) {
        labelsWanted = value;
        applyLabels(value);
        revalidate(); repaint();
    }

    private boolean labelled(JButton button) {
        return button != overflow && !Boolean.FALSE.equals(button.getClientProperty("keepLabel"))
            && button.getClientProperty("label") instanceof String;
    }

    private void applyLabels(boolean on) {
        for (Component child : getComponents())
            if (child instanceof JButton button && labelled(button)) {
                String text = on ? (String) button.getClientProperty("label") : null;
                if (!java.util.Objects.equals(button.getText(), text)) button.setText(text);
            }
    }

    private int groupWidth(int gap) {
        int width = 0, count = 0;
        for (Component child : getComponents())
            if (child instanceof JButton button && button != overflow) { width += button.getPreferredSize().width + gap; count++; }
        return count == 0 ? 0 : width - gap;
    }
```

In `doLayout`, directly after the line that declares `available`, insert:

```java
        // Labels give way before any control moves into the overflow menu.
        applyLabels(labelsWanted);
        if (labelsWanted && groupWidth(gap) > available) applyLabels(false);
```

In `WindowChrome.setToolbarMode`, replace the `for (var component : toolbar.getComponents()) ...` loop, which sets button texts, with:

```java
        toolbar.setLabelsWanted(mode == ToolbarMode.ICONS_AND_LABELS);
```

Keep the `toolbar.setVisible(...)` line and the `toolbarModes` line.

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :jasper-app:test`
Expected: PASS. The Plan A tests that place things around the field assert relations, not slide positions, so they keep passing: `MacTitleBarTest`, `WindowCommandPaletteTest`, `MockUiTest`, `BarHeightTest`, `WindowChromeTest` and `WindowChromeContributionsTest`. Any other failure: stop and report it.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Keep the title search field centred; labels and controls give way first

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: One colour for the whole palette card

**Files:**
- Modify: `palette/CommandPalette.java` (`refreshTheme`)
- Test: `palette/CommandPaletteTest.java` (`everySurfaceTakesItsColourFromTheTheme`)

- [ ] **Step 1: Update the test (failing)**

In `everySurfaceTakesItsColourFromTheTheme`:
- Replace the field-row pixel expectation `"SearchEverywhere.SearchField.background"` with `"List.background"`.
- Replace the hint-bar pixel expectation `"SearchEverywhere.Advertiser.background"` with `"List.background"`.
- Directly after the hint-bar assertion, add:

```java
                    assertThat(palette.queryField().getBackground()).as("%s: the field is the card's colour", name)
                        .isEqualTo(UIManager.getColor("List.background"));
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.palette.CommandPaletteTest`
Expected: FAIL on the field-row pixel.

- [ ] **Step 3: Implement**

In `CommandPalette.refreshTheme`:
- Delete the `advertiserBackground` local.
- Replace `Color fieldBackground = color("SearchEverywhere.SearchField.background", "TextField.background");` with:

```java
        // One surface throughout, like VS Code's quick input: the field keeps only its border.
        Color fieldBackground = listBackground;
```

- Replace `hintBar.setBackground(advertiserBackground);` with `hintBar.setBackground(listBackground);`.
- After the `query.putClientProperty(FlatClientProperties.STYLE, ...)` statement, add `query.setBackground(listBackground);`.

Update the class Javadoc's colour sentence to say the card is `List.background` throughout, the field has the `SearchEverywhere.SearchField.borderColor` border, and the hint bar has the separator rule.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.workspace.*Palette*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Give the palette card one surface colour

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The `window.native_decorations` setting

**Files:**
- Modify: `config/CoreSettings.java`, `config/ConfigSnapshot.java`, `settingsui/SchemaPage.java`, `settingsui/CorePages.java`, `config.example.toml` (repository root), `docs/configuration.md`
- Test: `config/CoreSettingsTest.java`, `settingsui/SchemaPageTest.java`

**Interfaces:**
- Produces:
  - `CoreSettings.WINDOW_NATIVE_DECORATIONS` (`Setting<Boolean>`).
  - `ConfigSnapshot.nativeDecorations()`, added as the last record component, with `Builder.nativeDecorations(boolean)`.
  - `SchemaPage.Spec.shownWhen(boolean)`, which applies to the previous row.

- [ ] **Step 1: Write the failing tests**

In `CoreSettingsTest`, change `hasSize(34)` to `hasSize(35)`, and add:

```java
    @Test void nativeDecorationsIsARestartOnlyWindowSettingOffByDefault() {
        var setting = CoreSettings.WINDOW_NATIVE_DECORATIONS;
        assertThat(setting.name()).isEqualTo("window.native_decorations");
        assertThat(setting.defaultValue()).isFalse();
        assertThat(setting.applies()).isEqualTo(dev.jasper.app.settings.Applies.RESTART);
        assertThat(dev.jasper.app.config.ConfigSnapshot.defaults().nativeDecorations()).isFalse();
        assertThat(dev.jasper.app.config.ConfigSnapshot.defaults().toBuilder().nativeDecorations(true).build().nativeDecorations()).isTrue();
    }
```

In `SchemaPageTest`, add:

```java
    @Test void aRowShownOnlyElsewhereKeepsItsSettingButBuildsNoControl() {
        var spec = SchemaPage.page("window", "Window")
            .row(CoreSettings.WINDOW_STATUS_BAR)
            .row(CoreSettings.WINDOW_NATIVE_DECORATIONS).shownWhen(false);
        assertThat(spec.settings()).containsExactly(CoreSettings.WINDOW_STATUS_BAR, CoreSettings.WINDOW_NATIVE_DECORATIONS);
        var page = spec.build(context);
        page.component();
        assertThat(page.rows()).hasSize(1);
        assertThat(page.searchTerms()).doesNotContain(CoreSettings.WINDOW_NATIVE_DECORATIONS.label());
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.config.CoreSettingsTest --tests dev.jasper.app.settingsui.SchemaPageTest`
Expected: compilation FAILS on `WINDOW_NATIVE_DECORATIONS`, `nativeDecorations` and `shownWhen`.

- [ ] **Step 3: Add the setting**

In `CoreSettings`, after `WINDOW_LINES`:

```java
    public static final Setting<Boolean> WINDOW_NATIVE_DECORATIONS = Setting.of("window.native_decorations", SettingKind.bool(), false)
        .label("Use the system title bar").keywords("title bar", "decorations", "window frame", "menu bar")
        .applies(Applies.RESTART)
        .description("Windows and Linux only: use the operating system's title bar, with the menus and the toolbar in\n"
            + "rows of their own, instead of Jasper's title bar. macOS always uses its own title bar.").build();
```

In `SCHEMA`'s list, insert `WINDOW_NATIVE_DECORATIONS` directly after `WINDOW_LINES`.

- [ ] **Step 4: Add it to `ConfigSnapshot`**

- **Record header:** append `, boolean nativeDecorations` after `int statusBarHeight`.
- **The one convenience constructor that ends `DEFAULT_TOOLBAR_HEIGHT, DEFAULT_STATUS_BAR_HEIGHT);`:** it becomes `DEFAULT_TOOLBAR_HEIGHT, DEFAULT_STATUS_BAR_HEIGHT, false);`. It is the only constructor that calls the canonical one; check with `grep -n "DEFAULT_STATUS_BAR_HEIGHT)" ConfigSnapshot.java`, which must print one line.
- **`from(...)`:** its last line becomes:

```java
            values.get(CoreSettings.WINDOW_TOOLBAR_HEIGHT), values.get(CoreSettings.WINDOW_STATUS_BAR_HEIGHT),
            values.get(CoreSettings.WINDOW_NATIVE_DECORATIONS));
```

- **Builder:**
  - add the field `private boolean nativeDecorations;`;
  - in the copy constructor, `nativeDecorations = source.nativeDecorations();`;
  - add the setter `public Builder nativeDecorations(boolean value) { nativeDecorations = value; return this; }`;
  - in `build()`, append `, nativeDecorations` after `statusBarHeight`.

- [ ] **Step 5: Add `shownWhen` to `SchemaPage` and the row to `CorePages`**

In `SchemaPage`:
- `RowItem` becomes `private record RowItem(Setting<?> setting, Editors.Factory<?> editor, String note, Predicate<SettingsDraft> enabled, boolean shown) implements Item { }`.
- Every `new RowItem(...)` passes the previous row's `row.shown()`. `row(...)` passes `true`.
- Add:

```java
        /** Builds the previous row only when {@code shown}; the page still owns its setting (for example a setting
         * that only one platform uses). */
        public Spec shownWhen(boolean shown) {
            if (items.isEmpty() || !(items.getLast() instanceof RowItem)) throw new IllegalStateException("A condition follows a row");
            RowItem row = (RowItem) items.removeLast();
            items.add(new RowItem(row.setting(), row.editor(), row.note(), row.enabled(), shown));
            return this;
        }
```

- In `component()`, the case becomes `case RowItem row -> { if (row.shown()) y = addRow(row, y); }`.
- In `searchTerms()`, the condition becomes `if (item instanceof RowItem row && row.shown()) {`. Keep the `else if (item instanceof SectionItem ...)` chain as it is.

In `CorePages`, on the Window page, after `.row(WINDOW_STATUS_BAR_HEIGHT, Editors.integer("px"))`, add:

```java
                .row(WINDOW_NATIVE_DECORATIONS).shownWhen(!com.formdev.flatlaf.util.SystemInfo.isMacOS)
```

- [ ] **Step 6: The example config and docs**

In `config.example.toml`, after `lines = 35` in `[window]`, add:

```toml
# Restart required. Windows and Linux only: true uses the operating system's title bar, with the menus and
# the toolbar in rows of their own, instead of Jasper's title bar. macOS ignores it.
native_decorations = false
```

In `docs/configuration.md`, after the `window.lines` row, add:

```markdown
| `window.native_decorations` | `false` | Boolean; Windows and Linux only | Next start |
```

- [ ] **Step 7: Run to verify it passes**

Run: `./gradlew :jasper-app:test`
Expected: PASS. `ConfigTemplateTest` covers the example file and the docs row, and `CorePagesTest` still finds every setting on one page.

- [ ] **Step 8: Commit**

```bash
git branch --show-current
git add -A jasper-app/src config.example.toml docs/configuration.md
git commit -m "Add window.native_decorations

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: `TitleBarStyle`, chosen at startup

**Files:**
- Create: `platform/TitleBarStyle.java`
- Modify: `bootstrap/ApplicationBootstrap.java` (`prepareDesktop`)
- Test: `platform/TitleBarStyleTest.java`

**Interfaces:**
- Produces: `public enum TitleBarStyle { MAC_UNIFIED, CUSTOM, NATIVE }` with `static TitleBarStyle choose(boolean macOs, boolean nativeDecorations)` and `void configure()`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.platform;

import com.formdev.flatlaf.FlatSystemProperties;
import javax.swing.JDialog;
import javax.swing.JFrame;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TitleBarStyleTest {
    @Test void macOsAlwaysUsesItsOwnTitleRow() {
        assertThat(TitleBarStyle.choose(true, false)).isEqualTo(TitleBarStyle.MAC_UNIFIED);
        assertThat(TitleBarStyle.choose(true, true)).isEqualTo(TitleBarStyle.MAC_UNIFIED);
        assertThat(TitleBarStyle.choose(false, false)).isEqualTo(TitleBarStyle.CUSTOM);
        assertThat(TitleBarStyle.choose(false, true)).isEqualTo(TitleBarStyle.NATIVE);
    }

    @Test void configureSetsTheDecorationDefaultsBeforeAnyWindowExists() {
        String saved = System.getProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS);
        boolean frames = JFrame.isDefaultLookAndFeelDecorated(), dialogs = JDialog.isDefaultLookAndFeelDecorated();
        try {
            TitleBarStyle.CUSTOM.configure();
            assertThat(System.getProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS)).isEqualTo("true");
            assertThat(JFrame.isDefaultLookAndFeelDecorated()).isTrue();
            assertThat(JDialog.isDefaultLookAndFeelDecorated()).isTrue();
            TitleBarStyle.NATIVE.configure();
            assertThat(System.getProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS)).as("Windows defaults to true").isEqualTo("false");
            assertThat(JFrame.isDefaultLookAndFeelDecorated()).isFalse();
            System.clearProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS);
            TitleBarStyle.MAC_UNIFIED.configure();
            assertThat(System.getProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS)).as("macOS is left alone").isNull();
        } finally {
            if (saved == null) System.clearProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS);
            else System.setProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS, saved);
            JFrame.setDefaultLookAndFeelDecorated(frames);
            JDialog.setDefaultLookAndFeelDecorated(dialogs);
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.platform.TitleBarStyleTest`
Expected: compilation FAILS, "cannot find symbol TitleBarStyle".

- [ ] **Step 3: Implement**

```java
package dev.jasper.app.platform;

import com.formdev.flatlaf.FlatSystemProperties;
import javax.swing.JDialog;
import javax.swing.JFrame;

/** Which title bar windows get; chosen once at startup, before any window exists. */
public enum TitleBarStyle {
    /** macOS: the native title row with the traffic lights, hosting the workspace header ({@link MacTitleBar}). */
    MAC_UNIFIED,
    /** Windows and Linux: FlatLaf's title pane holding the app icon, menus, header and window buttons. */
    CUSTOM,
    /** Windows and Linux with {@code window.native_decorations}: the system title bar, then the menu and header rows. */
    NATIVE;

    public static TitleBarStyle choose(boolean macOs, boolean nativeDecorations) {
        return macOs ? MAC_UNIFIED : nativeDecorations ? NATIVE : CUSTOM;
    }

    /** Sets the look-and-feel decoration defaults; FlatLaf decorates Windows frames unless told not to. */
    public void configure() {
        if (this == MAC_UNIFIED) return;
        boolean custom = this == CUSTOM;
        System.setProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS, Boolean.toString(custom));
        JFrame.setDefaultLookAndFeelDecorated(custom);
        JDialog.setDefaultLookAndFeelDecorated(custom);
    }
}
```

In `ApplicationBootstrap.prepareDesktop`, directly after `configureDesktopProperties();`, add:

```java
            // Before the look and feel and any window: a restart-only choice, like residency.
            dev.jasper.app.platform.TitleBarStyle.choose(System.getProperty("os.name").startsWith("Mac"),
                service.initialState().snapshot().nativeDecorations()).configure();
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.platform.TitleBarStyleTest --tests 'dev.jasper.app.bootstrap.*'` and `./gradlew :jasper-app:verifyApplicationArchitecture`
Expected: PASS. The architecture check allows bootstrap to depend on platform; if it refuses, stop and report.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Choose the title bar style at startup

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: `CollapsingMenuBar`

**Files:**
- Create: `workspace/CollapsingMenuBar.java`
- Test: `workspace/CollapsingMenuBarTest.java`

**Interfaces:**
- Consumes: `WindowHeader.setLeading` (Task 1).
- Produces (package-private):
  - `CollapsingMenuBar()`, with `enum Fold { NONE, MORE, ALL }` and `Fold fold()`.
  - `int fit(int space)` and `int minimumMenusWidth()`.
  - `List<JMenu> menus()`, `JMenu overflowMenu()` and `JMenu hamburgerMenu()`.
  - `void setHeader(WindowHeader)` and `void restoreMenus()`.
  - Added `JMenu`s (the app's and contributed ones) always go before the fold controls.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import javax.swing.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class CollapsingMenuBarTest {
    @BeforeAll static void theme() throws Exception { SwingUtilities.invokeAndWait(() -> ThemeTestSupport.install(Theme.DARK)); }

    private static JMenu menu(String name, int width) {
        var menu = new JMenu(name);
        menu.setPreferredSize(new Dimension(width, 24));
        menu.add(new JMenuItem(name + " item"));
        return menu;
    }

    /** File 40, Edit 40, View 50, Pane 50, Tab 40 (220 in all); both fold controls 30. */
    private static CollapsingMenuBar bar() {
        var bar = new CollapsingMenuBar();
        for (var menu : new JMenu[] {menu("File", 40), menu("Edit", 40), menu("View", 50), menu("Pane", 50), menu("Tab", 40)}) bar.add(menu);
        bar.overflowMenu().setPreferredSize(new Dimension(30, 24));
        bar.hamburgerMenu().setPreferredSize(new Dimension(30, 24));
        return bar;
    }

    private static boolean[] shown(CollapsingMenuBar bar) {
        var menus = bar.menus();
        var shown = new boolean[menus.size()];
        for (int i = 0; i < shown.length; i++) shown[i] = menus.get(i).isVisible();
        return shown;
    }

    @Test void trailingMenusFoldIntoMoreThenEverythingIntoTheHamburger() {
        var bar = bar();
        assertThat(bar.fit(220)).isEqualTo(220);
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.NONE);
        assertThat(bar.overflowMenu().isVisible()).isFalse();
        assertThat(bar.fit(219)).isEqualTo(180 + 30);
        assertThat(shown(bar)).containsExactly(true, true, true, true, false);
        assertThat(bar.overflowMenu().isVisible()).isTrue();
        assertThat(bar.fit(100)).as("File always stays").isEqualTo(40 + 30);
        assertThat(shown(bar)).containsExactly(true, false, false, false, false);
        assertThat(bar.fit(60)).isEqualTo(30);
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.ALL);
        assertThat(shown(bar)).containsExactly(false, false, false, false, false);
        assertThat(bar.hamburgerMenu().isVisible()).isTrue();
        assertThat(bar.getMenu(0)).as("F10 opens the first showing menu").isSameAs(bar.hamburgerMenu());
        assertThat(bar.minimumMenusWidth()).isEqualTo(30);
        bar.fit(500);
        assertThat(bar.getMenu(0).getText()).isEqualTo("File");
    }

    @Test void openingAFoldMenuMovesTheSameMenusInAndClosingPutsThemBackInOrder() {
        var bar = bar();
        var menus = bar.menus();
        bar.fit(100);
        bar.overflowMenu().setSelected(true);
        assertThat(bar.overflowMenu().getMenuComponents()).containsExactly(menus.get(1), menus.get(2), menus.get(3), menus.get(4));
        assertThat(menus.get(1).isVisible()).isTrue();
        bar.overflowMenu().setSelected(false);
        bar.restoreMenus();
        assertThat(Arrays.asList(bar.getComponents()).subList(0, 5)).containsExactlyElementsOf(menus);
        assertThat(bar.overflowMenu().getMenuComponentCount()).isZero();
        bar.fit(60);
        bar.hamburgerMenu().setSelected(true);
        assertThat(bar.hamburgerMenu().getMenuComponents()).containsExactlyElementsOf(menus);
        bar.restoreMenus();
        assertThat(bar.menus()).containsExactlyElementsOf(menus);
    }

    @Test void contributedMenusGoBeforeTheFoldControlsAndLeaveEvenWhileFolded() {
        var bar = bar();
        var tool = menu("Tool", 40);
        bar.add(tool);
        assertThat(bar.menus()).endsWith(tool);
        assertThat(bar.getComponentIndex(tool)).isEqualTo(5);
        assertThat(bar.getComponentIndex(bar.overflowMenu())).isEqualTo(6);
        bar.fit(100);
        bar.overflowMenu().setSelected(true);
        bar.remove(tool);
        assertThat(bar.menus()).doesNotContain(tool);
        assertThat(tool.getParent()).isNull();
        bar.restoreMenus();
        assertThat(bar.menus()).hasSize(5);
    }

    @Test void withoutAHeaderTheBarFoldsAgainstItsOwnWidth() {
        var bar = bar();
        bar.setSize(100, 24); bar.doLayout();
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.MORE);
        assertThat(bar.menus().getFirst().getBounds()).isEqualTo(new Rectangle(0, 0, 40, 24));
        assertThat(bar.overflowMenu().getX()).isEqualTo(40);
    }

    @Test void aHeaderSpansTheBarUnderTheMenusAndDecidesHowManyShow() {
        var bar = bar();
        var toolbar = new WindowToolbar(() -> { });
        for (int i = 0; i < 4; i++) {
            var action = new AbstractAction("Action " + i) { @Override public void actionPerformed(ActionEvent event) { } };
            var button = new JButton(action);
            button.setPreferredSize(new Dimension(60, 24));
            WindowToolbar.entry(button, i, () -> WindowToolbar.menuItem(action));
            toolbar.add(button);
        }
        toolbar.finish();
        var header = new WindowHeader(new TitleSearchField(() -> { }, () -> ""), toolbar);
        bar.setHeader(header);
        assertThat(bar.isOptimizedDrawingEnabled()).as("menus overlap the header").isFalse();
        bar.setSize(1400, 30); bar.doLayout();
        assertThat(header.getBounds()).isEqualTo(new Rectangle(0, 0, 1400, 30));
        assertThat(header.field().getX()).isEqualTo(420);
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.NONE);
        assertThat(bar.getComponentIndex(header)).as("under every menu").isGreaterThan(bar.getComponentIndex(bar.hamburgerMenu()));
        bar.setSize(900, 30); bar.doLayout();
        assertThat(header.field().getX()).isEqualTo(170);
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.MORE);
        assertThat(shown(bar)).containsExactly(true, true, false, false, false);
        bar.setSize(600, 30); bar.doLayout();
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.ALL);
        assertThat(header.field().getX() + header.field().getWidth() / 2).as("still centred").isEqualTo(300);
    }
}
```

Numbers for the header test:
- At 900 px, the field is at 170, so the menus' room is 170 − 12 = 158. File + Edit + "…" = 110 fits; adding View needs 160.
- At 600 px: `half = min(300 − 30 − 12, 600 − 300 − 12 − 40) = 248`, so the field is 496 wide at 52. The room is 40, below File + "…" (70), so everything folds to ☰.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.CollapsingMenuBarTest`
Expected: compilation FAILS, "cannot find symbol CollapsingMenuBar".

- [ ] **Step 3: Implement**

```java
package dev.jasper.app.workspace;

import dev.jasper.app.platform.AppIcons;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/**
 * The window's menu bar. As its room shrinks the trailing menus fold into a "…" menu (File always stays), then every
 * menu folds into one ☰ menu. Folded menus are the same {@code JMenu} objects: they move into the fold menu while it is
 * open and back when it closes. With a header (the Windows and Linux title bar) the header spans the bar under the
 * menus and decides their room, so the search field stays centred on the window.
 */
final class CollapsingMenuBar extends JMenuBar {
    enum Fold { NONE, MORE, ALL }
    private final List<JMenu> menus = new ArrayList<>();
    private final List<JMenu> opened = new ArrayList<>();
    private final JMenu more = new JMenu(), hamburger = new JMenu();
    private final Component glue = Box.createHorizontalGlue();
    private WindowHeader header;
    private Fold fold = Fold.NONE;
    private int kept;
    private boolean moving;

    CollapsingMenuBar() {
        setLayout(new Layout());
        control(more, "menuOverflow", "More menus", AppIcons.named("MORE"));
        control(hamburger, "menuHamburger", "Application menu", AppIcons.bundled("general/menu"));
        super.addImpl(more, null, -1);
        super.addImpl(hamburger, null, -1);
    }

    private void control(JMenu control, String name, String description, Icon icon) {
        control.setName(name);
        control.setIcon(icon);
        control.setToolTipText(description);
        control.getAccessibleContext().setAccessibleName(description);
        control.setVisible(false);
        control.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent event) { openInto(control); }
            // Later, so an item's action runs before its menu moves back.
            @Override public void menuDeselected(MenuEvent event) { SwingUtilities.invokeLater(CollapsingMenuBar.this::restoreMenus); }
            @Override public void menuCanceled(MenuEvent event) { SwingUtilities.invokeLater(CollapsingMenuBar.this::restoreMenus); }
        });
    }

    Fold fold() { return fold; }
    List<JMenu> menus() { return List.copyOf(menus); }
    JMenu overflowMenu() { return more; }
    JMenu hamburgerMenu() { return hamburger; }

    /** Puts {@code value} under the menus, spanning the bar; the header then decides how many menus show. */
    void setHeader(WindowHeader value) {
        header = value;
        super.addImpl(value, null, -1);
        // FlatLaf stretches an embedded menu bar across the title bar only when it ends with a horizontal glue.
        super.addImpl(glue, null, -1);
        value.setLeading(this::fit, this::minimumMenusWidth);
        revalidate();
    }

    @Override public boolean isOptimizedDrawingEnabled() { return header == null; }

    @Override protected void addImpl(Component component, Object constraints, int index) {
        if (moving || !(component instanceof JMenu menu) || component == more || component == hamburger) {
            super.addImpl(component, constraints, index);
            return;
        }
        menus.add(menu);
        super.addImpl(menu, constraints, getComponentIndex(more));
    }

    @Override public void remove(Component component) {
        if (!moving && component instanceof JMenu menu && menus.remove(menu)) {
            opened.remove(menu);
            Container parent = menu.getParent();
            if (parent != null && parent != this) { parent.remove(menu); return; }
        }
        super.remove(component);
    }

    /** F10 selects {@code getMenu(0)}: make that the first menu that is showing. */
    @Override public JMenu getMenu(int index) {
        if (index == 0)
            for (Component child : getComponents()) if (child instanceof JMenu menu && menu.isVisible()) return menu;
        return super.getMenu(index);
    }

    /** Shows as many menus as fit in {@code space} and returns the width they take. */
    int fit(int space) {
        if (!opened.isEmpty()) return shownWidth();
        int all = 0;
        for (JMenu menu : menus) all += width(menu);
        if (menus.isEmpty() || all <= space) { apply(Fold.NONE, menus.size()); return all; }
        int used = 0, keep = 0;
        for (JMenu menu : menus) {
            if (used + width(menu) + width(more) > space) break;
            used += width(menu); keep++;
        }
        if (keep >= 1) { apply(Fold.MORE, keep); return used + width(more); }
        apply(Fold.ALL, 0);
        return width(hamburger);
    }

    int minimumMenusWidth() { return menus.isEmpty() ? 0 : width(hamburger); }

    private int shownWidth() {
        int width = 0;
        for (Component child : getComponents())
            if (child != header && child != glue && child.isVisible()) width += child.getPreferredSize().width;
        return width;
    }

    private static int width(Component component) { return component.getPreferredSize().width; }

    private void apply(Fold next, int keep) {
        fold = next; kept = keep;
        for (int i = 0; i < menus.size(); i++) show(menus.get(i), next == Fold.NONE || next == Fold.MORE && i < keep);
        show(more, next == Fold.MORE);
        show(hamburger, next == Fold.ALL);
    }

    private static void show(Component component, boolean visible) { if (component.isVisible() != visible) component.setVisible(visible); }

    private void openInto(JMenu control) {
        restoreMenus();
        List<JMenu> folded = control == hamburger ? menus : menus.subList(Math.min(kept, menus.size()), menus.size());
        moving = true;
        try {
            for (JMenu menu : List.copyOf(folded)) { control.add(menu); menu.setVisible(true); opened.add(menu); }
        } finally { moving = false; }
    }

    /** Puts folded menus back in the bar, in order; the next layout hides them again. */
    void restoreMenus() {
        if (opened.isEmpty()) return;
        moving = true;
        try {
            for (JMenu menu : opened) { Container parent = menu.getParent(); if (parent != null) parent.remove(menu); }
            for (JMenu menu : menus) {
                if (!opened.contains(menu)) continue;
                int index = 0;
                for (JMenu other : menus) { if (other == menu) break; if (other.getParent() == this) index++; }
                menu.setVisible(false);
                super.addImpl(menu, null, index);
            }
        } finally { moving = false; opened.clear(); }
        revalidate(); repaint();
    }

    private final class Layout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }

        @Override public Dimension preferredLayoutSize(Container parent) {
            int width = 0, height = 0;
            for (Component child : getComponents()) {
                if (child == glue || child == header || !child.isVisible()) continue;
                Dimension size = child.getPreferredSize();
                width += size.width; height = Math.max(height, size.height);
            }
            if (header != null) {
                Dimension size = header.getPreferredSize();
                width = Math.max(width, size.width); height = Math.max(height, size.height);
            }
            Insets insets = getInsets();
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }

        @Override public Dimension minimumLayoutSize(Container parent) {
            int width = header != null ? header.getMinimumSize().width : minimumMenusWidth();
            return new Dimension(width, preferredLayoutSize(parent).height);
        }

        @Override public void layoutContainer(Container parent) {
            Insets insets = getInsets();
            int height = getHeight() - insets.top - insets.bottom;
            if (header != null) { header.setBounds(0, 0, getWidth(), getHeight()); header.doLayout(); }
            else fit(getWidth() - insets.left - insets.right);
            int x = insets.left;
            for (Component child : getComponents()) {
                if (child == header || child == glue || !child.isVisible()) continue;
                int width = child.getPreferredSize().width;
                child.setBounds(x, insets.top, width, height);
                x += width;
            }
            glue.setBounds(getWidth(), 0, 0, 0);
        }
    }
}
```

Two points about this class:
- Components earlier in a container are painted last and take mouse events first. The menus sit before the header, so they are above it.
- The Javadoc's "…" and "☰" are ordinary Unicode, not private-use characters.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.CollapsingMenuBarTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/workspace/CollapsingMenuBar.java jasper-app/src/test/java/dev/jasper/app/workspace/CollapsingMenuBarTest.java
git commit -m "Fold menus into … and ☰ as the menu bar narrows

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Wire the Windows/Linux custom title bar

**Files:**
- Modify: `workspace/WindowChrome.java`, `workspace/WindowContent.java`, `workspace/TerminalWindow.java`, `appearance/ThemeManager.java`
- Test: create `workspace/CustomTitleBarTest.java`; modify `workspace/WindowChromeContributionsTest.java` and `appearance/ThemeManagerTest.java`

**Interfaces:**
- Consumes: `CollapsingMenuBar` (Task 5) and `WindowHeader.setLeading` (Task 1).
- Produces:
  - `static boolean WindowContent.installCustomTitleBar(JRootPane root, WindowContent content)`.
  - `WindowChrome.menuBar()` now returns `CollapsingMenuBar`.

- [ ] **Step 1: Write the failing tests**

Create `CustomTitleBarTest`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class CustomTitleBarTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private static JRootPane decorated(WindowContent owner) {
        var root = new JRootPane();
        root.setWindowDecorationStyle(JRootPane.FRAME);
        root.updateUI();
        WindowContent.installTitleBar(root, owner, false, title -> { });
        return root;
    }

    private static void layout(JRootPane root) { for (int i = 0; i < 3; i++) LayoutTestSupport.layoutTree(root); }

    private static double fieldCentre(WindowContent owner, JRootPane root) {
        var field = owner.chrome().search();
        Rectangle bounds = SwingUtilities.convertRectangle(field.getParent(), field.getBounds(), root);
        return bounds.getCenterX();
    }

    @Test void anUndecoratedRootKeepsTheHeaderRow() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            WindowContent.installTitleBar(root, owner, false, title -> { });
            assertThat(WindowContent.installCustomTitleBar(root, owner)).isFalse();
            assertThat(owner.chrome().header().getParent()).isSameAs(owner);
        });
    }

    @Test void theHeaderJoinsTheMenusInTheTitlePaneWithTheFieldCentredOnTheWindow() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = decorated(owner);
            assertThat(WindowContent.installCustomTitleBar(root, owner)).isTrue();
            root.setJMenuBar(owner.menuBar());
            assertThat(owner.chrome().header().getParent()).isSameAs(owner.chrome().menuBar());
            assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE)).isEqualTo(false);
            assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT)).isEqualTo(30);
            root.setSize(1400, 800); layout(root);
            assertThat(Math.abs(fieldCentre(owner, root) - 700)).isLessThanOrEqualTo(1);
            assertThat(owner.chrome().menuBar().fold()).isEqualTo(CollapsingMenuBar.Fold.NONE);
            root.setSize(700, 600); layout(root);
            assertThat(Math.abs(fieldCentre(owner, root) - 350)).isLessThanOrEqualTo(1);
            assertThat(owner.chrome().menuBar().fold()).isNotEqualTo(CollapsingMenuBar.Fold.NONE);
            owner.setToolbarHeight(40);
            assertThat(root.getClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT)).isEqualTo(40);
            assertThat(owner.toolbar().getClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION))
                .as("the toolbar's blank space drags the window").isInstanceOf(java.util.function.Function.class);
        });
    }
}
```

In `WindowChromeContributionsTest` (around line 116), replace `owner.menuBar().getMenu(owner.menuBar().getMenuCount() - 1).getText()` with `owner.chrome().menuBar().menus().getLast().getText()`.

In `ThemeManagerTest`, in any test that installs a theme, add:

```java
        assertThat(javax.swing.UIManager.getBoolean("TitlePane.centerTitle")).as("dialog titles are centred").isTrue();
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.CustomTitleBarTest`
Expected: compilation FAILS on `installCustomTitleBar` and `fold()` on the chrome's menu bar.

- [ ] **Step 3: Implement**

`WindowChrome`:
- Change the menu bar field to `private final CollapsingMenuBar menuBar = new CollapsingMenuBar();`.
- Change the accessor to `CollapsingMenuBar menuBar() { return menuBar; }`.
- In the constructor, after `header.setOpaque(true);`, add:

```java
        // In the Windows and Linux title bar the toolbar's blank space drags the window; its buttons stay clickable.
        toolbar.putClientProperty(com.formdev.flatlaf.FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION,
            (java.util.function.Function<Point, Boolean>) point -> null);
```

`WindowContent`: add next to `installTitleBar`:

```java
    /**
     * Windows and Linux custom title bar: the header joins the menus in FlatLaf's title pane, which embeds the menu bar.
     * The title pane shows no title text (the field shows the context) and follows the toolbar height and chrome colours.
     * Returns false, changing nothing, when {@code root} has no look-and-feel decorations.
     */
    static boolean installCustomTitleBar(JRootPane root, WindowContent content) {
        if (root.getWindowDecorationStyle() == JRootPane.NONE) return false;
        WindowHeader header = content.chrome.header();
        content.remove(header);
        content.chrome.menuBar().setHeader(header);
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_SHOW_TITLE, false);
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_HEIGHT, content.toolbarHeight());
        header.addPropertyChangeListener("barHeight",
            event -> root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_HEIGHT, event.getNewValue()));
        Runnable colors = () -> {
            root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_BACKGROUND, UIManager.getColor("Jasper.titleBackground"));
            root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_FOREGROUND, UIManager.getColor("Jasper.titleForeground"));
        };
        colors.run();
        content.onThemeChanged = theme -> colors.run();
        content.revalidate();
        return true;
    }
```

If `WindowToolbar.setBarHeight` fires `barHeight` with scaled values, the property must still get logical pixels. It does not: `setBarHeight(int height)` fires the logical value it is given.

`TerminalWindow`: directly after the `titleBar = WindowContent.installTitleBar(...)` line, add:

```java
        if (titleBar == null) WindowContent.installCustomTitleBar(frame.getRootPane(), content);
```

`ThemeManager.appDefaults()`: before `return`, add:

```java
        // Windows and Linux dialogs and auxiliary windows: centred titles, like the macOS title rows.
        defaults.put("TitlePane.centerTitle", "true");
```

`NativeShells` needs no change. Its frames and dialogs get FlatLaf decorations from the defaults `TitleBarStyle.configure()` sets. Their File menu embeds in the title pane, and the title shows, centred.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :jasper-app:test`
Expected: PASS. If the headless title pane does not embed the menu bar (the field centre stays at the old place), check that `root.updateUI()` ran after `setWindowDecorationStyle`. The 2026-09-29 spike needed three layout passes. Do not change assertions; report.

- [ ] **Step 5: Full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Record the counts from `*/build/test-results/test/*.xml`.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Put the menus, search field and toolbar in the Windows and Linux title bar

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Spec amendments and docs

**Files:** the spec, `docs/app-architecture.md`, `docs/STATUS.md`, this plan.

- [ ] **Step 1: Spec amendments (the user's 2026-09-29 decisions)**

In the spec:
- Replace the §1 bullet "It is centred on the **window**, not on the space left between the other regions." and the next bullet, "When the side regions would overlap…", with:

```markdown
- It is always centred on the **window**, never slid off centre (user decision, 2026-09-29). Each side gives way instead: toolbar labels drop, then toolbar controls move into ⋮; on Windows and Linux the menus fold into "…" and then ☰. Only when a side can give way no further does the field shrink around the centre, and then collapse to its magnifier, still centred.
```

- Replace the §1 squeeze-order list with:
  1. toolbar labels drop (icons only);
  2. toolbar buttons overflow into ⋮;
  3. menus fold into "…";
  4. menus collapse to ☰;
  5. the field shrinks around the centre;
  6. the field collapses to its magnifier.

  Note that the two sides give way independently.
- In §3, "Outer shape", add the bullet: `The card is one colour throughout — the query row, the field, the list, a step form and the hint bar all use List.background; the field keeps its border and the hint bar its top rule (user decision, 2026-09-29).`
- In §4:
  - Change "Alt and F10 mnemonics still open the first visible menu" to "F10 opens the first showing menu; Alt mnemonics work for menus that are showing".
  - Change the dialogs paragraph to keep their existing File menu, now embedded in the title pane, with the title shown and centred.
- Set the Status line to: `Plan A merged 2026-09-29; plan B written 2026-09-29 (amended by the user's centring and palette-colour decisions).`

- [ ] **Step 2: `docs/app-architecture.md` "Window chrome"**

Change the sentences about the squeeze and the platforms to:

```markdown
The field is always centred on the window; the sides give way first (toolbar labels, then controls into
the ⋮ menu; on Windows and Linux the menus fold into "…" and then ☰), and only then does the field shrink
and collapse to its magnifier. `TitleBarStyle` (platform) is chosen at startup: on macOS `MacTitleBar`
hosts the header in the native title row; on Windows and Linux FlatLaf's title pane embeds
`CollapsingMenuBar`, which carries the header under the menus (`window.native_decorations = true` keeps
the system title bar with the menu and header rows instead).
```

- [ ] **Step 3: `docs/STATUS.md` and this plan's banner**

Add a first bullet under the current state, with the counts from Task 6:

```markdown
- **Title-bar command centre, plan B** (branch `claude/titlebar-plan-b`, plan
  `docs/superpowers/plans/2026-09-29-jasper-titlebar-command-center-b.md`):
  - **Centring:** the search field is always centred on the window, and the sides give way first.
  - **Palette:** the card is one colour.
  - **Windows and Linux:** get FlatLaf's title bar with the app icon, menus (folding to "…" then ☰), field,
    toolbar and window buttons; `window.native_decorations` (restart) keeps the system title bar.
  - `./gradlew check` <N> tests green.
  - Not done: GUI checks on Windows and Linux (Snap Layouts, double-click maximise, dragging the empty title
    bar and toolbar gaps, both menu folds, F10), the macOS look after the centring change, merge and push.
```

Set this plan's **Status:** to `Implemented <date> (<first>..<last>); ./gradlew check <N> tests green.`, followed by any deviations.

- [ ] **Step 4: Commit**

```bash
git branch --show-current
git add docs
git commit -m "Document plan B: centred field, one-colour palette, Windows/Linux title bar

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
