# Theme System Plan 3: Plugin Pane Restyler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make ordinary plugin controls in center panes follow the workspace theme, preserve plugin-owned styling,
and restore the correct chrome appearance when content leaves the workspace or the choice returns to `match`.

**Architecture:** Add verified component recipes, a reversible per-component style layer, and an EDT-owned subtree
styler in `dev.jasper.app.workspace`. `WindowContent` owns those stylers for hosted content only. Bundled custom
painters read the existing SDK palette using their mounted component; Editor retains ownership of syntax surfaces.

**Tech Stack:** JBR 25, Gradle wrapper, Swing, pinned FlatLaf 3.7, existing SDK 0.12.0; no new dependencies.

**Spec:** [Theme design](../specs/2026-10-01-jasper-theme-system-design.md), §§1, 5.3–5.6, 9.
**Prerequisite:** [Plan 2](2026-10-01-theme-system-2-workspace.md), implemented and merged locally.
**Status:** Implementation complete; Tasks 1–4 independently reviewed, Task 5 and final whole-branch review pending. Revised against `main@14f9913d` after the GitHub Dark startup fix.
The user clarified on 2026-10-01 that a workspace combo's dropdown follows the workspace; menus, tooltips and dialogs
remain chrome. This replaces the previous three-task outline; the five tasks below are sequential review gates.
**Execution:** Preserve the repository's per-task implementer/reviewer workflow and final whole-branch review.

**Execution notes:** Task 1 (`38b946d0`, review fix `9d1e7079`) verifies 25 control types and FlatLaf subclasses,
including Jasper's own delegates. The initial full check passed 7,802 tests with zero failures/errors and six skips;
the scoped review fix passed 58 recipe tests. FlatLaf 3.7 exposes progress track/fill but no per-component text-color
style or setter, so progress text remains delegate-owned (the task's “as delegate supports” qualification).
Task 2 (`5d75b46a`, fixes `6a51960c`, `bab40d60`) preserves plugin styles/direct values through clear and delegate
refresh. Its last full check passed 7,842 tests, zero failures/errors, six skips; review fixes passed 74 focused tests.
A shared Swing delegate can reinstall without a UI event: direct-color observation narrowly checks synchronous
`JComponent.setUI` with public StackWalker. A silent new UIResource identity matching the current UI default remains
replaceable; an explicit assignment of that exact default without an event is indistinguishable and follows Swing's
UIResource convention. Ordinary Color identities and observable plugin setters remain authoritative. One intermediate
Gradle overlap invalidated that run; final evidence was serialized. Two minor diagnostics findings remain for final review.
Task 3 (`5bf2a468`, fix `756001d8`) owns dynamic descendants and combo dropdowns. Full check passed 7,860 tests,
zero failures/errors, six skips; the review fix passed 34 focused lifecycle tests. A targeted source-owned hierarchy
listener remains on a current header/editor association inside excluded ancestry so it can detect a return to eligible
ancestry. It retains no style/property/container listeners there and is removed on association replacement, source
removal or close. This narrow exception qualifies the no-descendant-listeners exemption rule; unrelated excluded
children stay unobserved. Bounded queued reconciliation now survives a newer theme generation.
Task 4 (`1690aaf1`, fix `c220f7f9`) integrates hosted lifetimes and Editor exemptions. Full check passed 7,875 tests,
zero failures/errors, six skips; scoped review fixes passed the 1,671-test app suite and architecture check. One earlier
full run hit the already documented terminal reflow callback timeout (STATUS's baseline-flake entry); later gates passed
without terminal changes. Acquisition cleanup also required narrow WorkspaceStyler constructor and ContentRegistry
changes: failed opens leave no registry entry; failed moves restore the source or another main host, otherwise preserve
a live unmounted entry/document for same-key reopen retry. Zone publication stops when a synchronous callback changes
ownership, preserving removed-root cleanup and destination values. These are reviewed extensions to the original file map.

Task 5 audits all seven plugins. Remote, File Server, Vault and Editor semantic renderers/borders now resolve their
mounted owners; domain/syntax and configured yank colors remain authored. Vim owns inline feedback style maps and
refreshes the complete palette on first paint after a move or same-darkness color change. Muted/normal notes retain
semantics after delegate rebuilds. Four local test-only palette fixtures keep module boundaries intact. One additional
`WorkspaceStyleLayerTest` verifies effective label/panel feedback colors through host application and UI refresh;
actual Vim tests independently pin state and published styles, avoiding a new plugin/app test dependency.
Seven per-plugin regressions were RED, then the focused gates passed **83 tests, zero failures/errors/skips**
(Remote 37, File Server 9, Vault 4, Editor 16, app layer 17). Logs: `/tmp/moray-p3-task5-red-*.log` and
`/tmp/moray-p3-task5-green-*.log`. The one final serialized `./gradlew check` passed in 3m36s: **7,889 tests, zero failures/errors, six skips**.
Exact module XML counts: app 1,672/0/0/1; Buddy 163/0/0/0; terminal 391/0/0/4; SDK 44/0/0/0;
testkit 84/0/0/0; Editor 4,786/0/0/0; File Server 159/0/0/0; History 41/0/0/0; Remote 385/0/0/0;
Sample 12/0/0/0; Snippets 11/0/0/0; Vault 141/0/0/1 (tests/failures/errors/skips). Counted explicit
module/plugin `build/test-results/test/TEST-*.xml` roots only, excluding `.worktrees` recursion. All four
architecture gates passed in check; Java source hygiene passed for 1,634 files; `git diff --check 5435069c` clean.
Evidence: `/tmp/moray-p3-task5-check.log`, `/tmp/moray-p3-task5-counts.log`, `/tmp/moray-p3-task5-hygiene.log`.
The full log retains dependency SLF4J/native-access warnings from plugin smoke paths; no test failures.
Remote's transfer bar does not paint text, so the progress text-color limitation has no bundled impact. No SDK/API,
plugin release, dependency, domain color, GUI or benchmark change. Step 4 remains open solely for controller-owned
independent review and its findings; headless verification does not claim user native visual acceptance.

## Global Constraints

- One installed LAF, always chrome. Never temporarily install the workspace LAF or change global colors to style a pane.
- `ResolvedTheme.matching()` means both generated slots are the same object. In match, remove all generated styles,
  direct-color overrides and combo popup overrides; retain only observation needed for a later independent choice.
- Workspace means center content tabs/panes. Rail/sidebar panels, native auxiliary windows, Settings, menus,
  tooltips and dialogs remain chrome. A workspace combo's own dropdown is the sole popup exception.
- Plugin `FlatLaf.style` values (String or Map) and `FlatLaf.styleClass` win over generated values. Restore the latest
  plugin style object/type, not the value that existed when the pane first mounted. Do not mutate plugin maps.
- `Jasper.workspace.exempt = Boolean.TRUE` exempts a subtree. `Jasper.workspaceStyle` identifies only generated style
  values. Preserve existing zone properties and the Plan 2 `WorkspaceTheme` / `ThemeZone` lookup boundaries.
- All ownership, Swing mutation and lifetime calls require the EDT. No static component/listener registry.
- SDK types remain confined to `app.plugins`; workspace consumes app-native `GeneratedTheme`/`Token`. Bundled plugins
  compile against SDK only and must not import FlatLaf or app classes. No new production interface.
- Preserve terminal sessions, documents, selection, undo, fonts, plugin styles and all Plan 2 regression tests.
- Optional IntelliJ UI keys are optional: preserve the `ToolWindow.background` → `Panel.background` startup fallback.
  Generate workspace colors from complete token maps; do not introduce another strict lookup for an optional key.
- Do not run the GUI, benchmark or a user's shell. Use actual lightweight controls and queued fixture launchers.
- Work on a branch; one implementation commit per task plus reviewed fixes. End every commit with
  `Co-Authored-By: Codex <noreply@openai.com>`. Run the task's red/green checks and serialized `./gradlew check`.
- Update this banner and `docs/STATUS.md` with actual deviations and results. Plans 4–5 remain separate deliverables.

## Review Focus

1. Plugin style changes while mounted, class-based values, and a chrome LAF rebuild must not lose styling or restore
   stale workspace colors on match/unmount — explicit tests in Tasks 2 and 4.
2. Late/reparented children, replaced table headers/viewports, exemption toggles and plugin shutdown must leave one
   owner and no orphan listeners, including hidden combo lists — explicit tests in Tasks 3 and 4.
3. A combo dropdown needs selected-row colors as well as a background; unrelated menus/tooltips must stay chrome —
   delegate and actual list tests in Tasks 1 and 3.
4. Generic text styling must not overwrite RSyntax syntax/editor surfaces, Vim errors/bell feedback, or document
   state; detached/auxiliary Editor content must also remain usable — explicit tests in Tasks 4 and 5.
5. Imported complete themes may omit optional UI keys; switching workspace themes of the same darkness and returning
   to match must work on first construction, not just after a built-in warmed UI defaults — Tasks 1 and 4.

## Baseline and verified implementation facts

- Plan 2 exposes `ThemeManager.generated(Theme)`, `ResolvedTheme(GeneratedTheme chrome, GeneratedTheme workspace)`,
  `WorkspaceTheme.apply(JComponent, ResolvedTheme)` / `clear(JComponent)`, and `WorkspacePalette.current(Component)`.
- `WindowContent.mountContent` marks a root, registers it and adds its tab. `unmountContent` removes the tab and clears
  its zone. `applyTheme` currently rebuilds chrome delegates **before** reapplying hosted zone properties. There is
  no generic subtree styler yet. `ContentHost.close()` invokes unmount; keep a defensive close of remaining owners.
- FlatLaf 3.7 sources in the local Gradle cache were inspected for this plan. Use its public
  `FlatStylingSupport.getStyleForClasses(Object, String)`, `joinStyles(Object, Object)`, `parse(String)`, and
  `StyleableUI.getStyleableInfos(JComponent)` / `getStyleableValue(JComponent, String)`.
  `getStyleForClasses` supports String, String[] and List<String> class values. `getResolvedStyle` includes the
  component's current style, so **do not use it to recover the plugin layer after installing a composite**.
- Style keys for delegate fields differ from UI-default keys: scrollbar `track`/`thumb`, tabbed-pane `hoverColor` /
  `underlineColor`, combo `popupBackground`. Common component setters can be styleable without appearing in the
  delegate's annotation map; verify their effective getters rather than filtering every key through that map.
- `FlatComboBoxUI` applies `popupBackground` to its popup list. That does not provide workspace selection colors.
  The JDK `ComboPopup.getList()` API provides the list through the UI's accessible child without displaying it.
- Editor's `EditorTheme` owns the RSyntax area, viewport and gutter colors. `EditorTab` and `VimBar` also contain
  cached chrome colors absent from the old outline. Other concrete audit targets appear in Task 5.

## File map and dependency order

All app paths below begin `jasper-app/src/main/java/dev/jasper/app/`; app test paths replace `main` with `test`.

| Task | Files | Responsibility |
|---|---|---|
| 1 | new `workspace/WorkspaceStyles.java`; new `workspace/WorkspaceStylesTest.java` and test-only `WorkspaceStyleFixtures.java` | Verified component recipes and real-control assertions |
| 2 | new `workspace/WorkspaceStyleLayer.java`, `workspace/WorkspaceStyleLayerTest.java` | One component's reversible plugin/generated layers and direct-color ownership |
| 3 | new `workspace/WorkspaceStyler.java`, `workspace/WorkspaceComboPopup.java`; matching tests | Dynamic tree ownership, boundaries, associated header/viewport/popup objects |
| 4 | `workspace/WindowContent.java`, `workspace/package-info.java`; new `workspace/PluginPaneAppearanceTest.java`; Editor files below | Hosted-content lifetime and Editor-owned surface protection |
| 5 | Remote/File Server/Vault/Editor files and tests below; authoring/architecture/status docs | Zone-aware custom rendering, regression and acceptance record |

No Task 1–3 helper depends on SDK types, window construction, configuration scanning or plugin classes.

## Task 1: Prove effective control coverage and define recipes

**Files:** Create `WorkspaceStyles.java`, `WorkspaceStylesTest.java`, `WorkspaceStyleFixtures.java` in the paths above.

**Interfaces produced** (package-private; all evaluated on EDT):

```java
final class WorkspaceStyles {
    record Recipe(String type, Map<String, Object> style, Map<String, Color> direct) {}
    static Recipe recipe(JComponent component, GeneratedTheme theme);
}
```

`type` is the FlatLaf style-class type (`Panel`, `Label`, `List`, `Table`, `TableHeader`, `Tree`, `TextField`,
`PasswordField`, `FormattedTextField`, `TextArea`, `TextPane`, `EditorPane`, `ComboBox`, `Button`, `ToggleButton`,
`CheckBox`, `RadioButton`, `ScrollPane`, `Viewport`, `ScrollBar`, `SplitPane`, `TabbedPane`, `ToolBar`, `Separator`,
`ProgressBar`). Test subclasses by supported Swing superclass, most specific first; do not assume custom UI IDs are
FlatLaf class names. Unsupported components yield `Recipe("", Map.of(), Map.of())`. Both maps are immutable. `direct` contains only
properties not handled by this component's real delegate; it is not a second copy of `style`.

- [x] **Step 1: Add reusable fixture and red effective-color tests.** Use the existing `DesktopTestSupport.edt`,
  restoring the LAF and any UI defaults modified by a test. Put this complete value fixture in `WorkspaceStyleFixtures`:

```java
static ResolvedTheme independent() {
    GeneratedTheme chrome = ThemeManager.generated(Theme.JASPER_LIGHT);
    GeneratedTheme workspace = ThemeManager.generated(Theme.DARK);
    return new ResolvedTheme(chrome, workspace);
}
static ResolvedTheme recolored(ResolvedTheme original, Token token, Color color) {
    GeneratedTheme old = original.workspace();
    var tokens = new java.util.EnumMap<Token, Color>(Token.class);
    tokens.putAll(old.tokens());
    tokens.put(token, color);
    var changed = new GeneratedTheme(old.name(), old.dark(), old.author(), tokens, old.syntax(),
        old.terminal(), old.uiKeys(), old.icons());
    return new ResolvedTheme(original.chrome(), changed);
}
```

The fixture also supplies `applyRecipe(JComponent, Recipe)` which sets `FlatLaf.style` to the recipe style and uses
explicit typed setters for each entry in `direct` (background/foreground; JList/JTable/JTextComponent selection;
JTextComponent caret/disabled text). No reflection or production code in that fixture. It supplies
`static BufferedImage paint(JComponent component, int width, int height)` using layout and a BufferedImage, disposing Graphics in `finally`.
Use JUnit parameterized tests with real controls to cover every row of the table below; instantiate models with at
least one selectable item. Pin key outputs, not merely the returned map:

```java
@Test void listAndHeaderUseWorkspaceColorsWithoutChangingChrome() throws Exception {
    edt(() -> {
        new ThemeController(Theme.JASPER_LIGHT);
        var resolved = independent();
        var laf = UIManager.getLookAndFeel();
        Color chrome = UIManager.getColor("Panel.background");
        var list = new JList<>(new String[] {"first", "second"});
        var table = new JTable(new Object[][] {{"value"}}, new Object[] {"column"});
        applyRecipe(list, WorkspaceStyles.recipe(list, resolved.workspace()));
        applyRecipe(table.getTableHeader(), WorkspaceStyles.recipe(table.getTableHeader(), resolved.workspace()));
        assertThat(list.getSelectionBackground()).isEqualTo(resolved.workspace().tokens().get(Token.SELECTION));
        assertThat(list.getSelectionForeground()).isEqualTo(resolved.workspace().tokens().get(Token.SELECTION_TEXT));
        assertThat(table.getTableHeader().getBackground()).isEqualTo(resolved.workspace().tokens().get(Token.SURFACE_RAISED));
        paint(list, 160, 60);
        paint(table.getTableHeader(), 160, 24);
        assertThat(UIManager.getLookAndFeel()).isSameAs(laf);
        assertThat(UIManager.getColor("Panel.background")).isEqualTo(chrome);
    });
}
```

Add `representativeControlsAcceptAllRecipeKeys`, `sameDarknessChangesEffectiveColors`, and
`completeThemeWithoutOptionalKeysSupportsRecipes`. Capture `com.formdev.flatlaf` SEVERE styling failures in a scoped
JUL handler; fail on an unsupported generated key. For focused/selected/disabled states assert delegate values and
representative interior pixels. Use `JComponent.focusOwner`'s supported FlatLaf test hook only where the delegate
supports it; do not require native focus. Reuse Plan 2's complete-theme fixture pattern that omits ToolWindow.background.

- [x] **Step 2: Run** `./gradlew :jasper-app:test --tests '*WorkspaceStylesTest'`; expect missing helper or wrong colors.
- [x] **Step 3: Implement the recipes against the pinned delegates.** Values are app-native `Color`s from tokens.
  Keep geometry, fonts, opacity, application listeners, models, cell renderers and icons unchanged.

| Component family | Required semantic values and verified key direction |
|---|---|
| Panel / label | `background` SURFACE; `foreground` TEXT; label `disabledForeground` TEXT_DISABLED |
| List / table / tree | surface SURFACE_SUNKEN; TEXT; SELECTION / SELECTION_TEXT; inactive SELECTION_INACTIVE; focus FOCUS_RING; grid/borders BORDER where supported |
| Table header | SURFACE_RAISED / TEXT; `separatorColor`, `bottomSeparatorColor` BORDER; hover HOVER |
| Text controls (including formatted/password) | INPUT_BACKGROUND / TEXT; selection TEXT_SELECTION / TEXT_SELECTION_TEXT; caret EDITOR_CARET; inactive/disabled fill INPUT_BACKGROUND; disabled TEXT_DISABLED; border BORDER / focus FOCUS_RING |
| Combo | INPUT_BACKGROUND / TEXT; disabled TEXT_DISABLED; button/editable fills INPUT_BACKGROUND; arrow TEXT; `popupBackground` SURFACE_SUNKEN; popup selection handled in Task 3 |
| Button / toggle | BUTTON_BACKGROUND / BUTTON_TEXT; border BUTTON_BORDER; hover HOVER; pressed PRESSED; selected SELECTION / SELECTION_TEXT; default BUTTON_DEFAULT / TEXT_ON_ACCENT; disabled TEXT_DISABLED; focus FOCUS_RING |
| Check / radio | TEXT / TEXT_DISABLED; actual styleable icon keys use INPUT_BACKGROUND, BORDER, ACCENT, TEXT_ON_ACCENT, HOVER, PRESSED, FOCUS_RING; do not replace plugin-supplied icons |
| Scroll pane / viewport | SURFACE_SUNKEN; border BORDER / focus FOCUS_RING; preserve transparency and plugin border geometry |
| Scrollbar | `track` SCROLLBAR_TRACK; `thumb` SCROLLBAR_THUMB; hover/pressed thumb HOVER/PRESSED; arrows TEXT / TEXT_DISABLED |
| Split pane | SURFACE; supported divider colors DIVIDER; leave Jasper's existing divider border ownership intact |
| Tabbed pane | TAB_BAR; selected TAB_ACTIVE / TAB_ACTIVE_TEXT; hover TAB_HOVER; underline TAB_UNDERLINE / TAB_UNDERLINE_INACTIVE; separator BORDER; content SURFACE |
| Toolbar / separator | SURFACE / TEXT; rule SEPARATOR; retain orientation and geometry |
| Progress bar | track SURFACE_SUNKEN; filled ACCENT; text TEXT or TEXT_ON_ACCENT as delegate supports (Remote transfer panes use this control) |

Do not guess keys from the table: validate the concrete delegate keys using the parameter table and source. Use
common component setters via style where supported; otherwise place only the missing property in `direct`. The
closed direct-property set is background, foreground, selectionBackground, selectionForeground, caretColor and
disabledTextColor, with explicit switch/instanceof readers/writers. Unsupported custom UI delegates receive only
valid direct setters; custom painting is documented/audited in Task 5. Do not reflect into custom component fields.
Record the final supported key list in the test's table, so future FlatLaf upgrades have an executable contract.
- [x] **Step 4: Run** the focused test and `./gradlew check`; require exit 0 and no generated-style errors.
- [x] **Step 5: Commit** `feat(workspace): define verified Swing workspace styles` with the required trailer.

## Task 2: Own and restore one component's style layer

**Files:** Create `WorkspaceStyleLayer.java` and `WorkspaceStyleLayerTest.java`; consume Task 1's recipe/fixtures.

**Interfaces produced** (package-private, EDT-only):

```java
final class WorkspaceStyleLayer implements AutoCloseable {
    WorkspaceStyleLayer(JComponent component);
    void apply(WorkspaceStyles.Recipe recipe);
    void clear();
    void beforeUiUpdate();
    void afterUiUpdate();
    public void close();
}
```

`clear` removes the generated layer but keeps observation; `close` restores and detaches, idempotently. `apply` after
close throws IllegalStateException. `beforeUiUpdate` suspends host writes and restores plugin-only state before a
chrome delegate rebuild; apply while suspended records the new recipe without writing it; `afterUiUpdate` captures the new baseline and reapplies the last active recipe. Store the
latest plugin style object, class value, recipe, published composite, direct originals/last-written values, and a
reentrancy depth in this owner. Restore exact null/String/Map plugin style and never modify its map in place.

- [x] **Step 1: Add red tests for String, Map, null and class styles.** Use this complete core test with fixture imports:

```java
@Test void pluginEditsWinAndSurviveClearAndClose() throws Exception {
    edt(() -> {
        new ThemeController(Theme.JASPER_LIGHT);
        var button = new JButton("Run");
        button.putClientProperty("FlatLaf.style", "arc: 13; background: #123456");
        var latest = java.util.Map.<String, Object>of("arc", 17, "background", new Color(0x654321));
        try (var layer = new WorkspaceStyleLayer(button)) {
            layer.apply(WorkspaceStyles.recipe(button, independent().workspace()));
            assertThat(button.getBackground()).isEqualTo(new Color(0x123456));
            button.putClientProperty("FlatLaf.style", latest);
            assertThat(button.getBackground()).isEqualTo(new Color(0x654321));
            layer.clear();
            assertThat(button.getClientProperty("FlatLaf.style")).isSameAs(latest);
            layer.apply(WorkspaceStyles.recipe(button, independent().workspace()));
        }
        assertThat(button.getClientProperty("FlatLaf.style")).isSameAs(latest);
        assertThat(button.getClientProperty("Jasper.workspaceStyle")).isNull();
        assertThat(button.getBackground()).isEqualTo(new Color(0x654321));
    });
}
```

Add `styleClassesOverrideGeneratedButInlineOverridesClasses` for String/String[]/List class forms;
`pluginRemovesItsStyleWhileMounted`; `directPluginColorEditSurvivesNextApplyAndClose`; and
`chromeDelegateRebuildThenClearUsesNewChromeDefaults`. Test both ordinary Color and ColorUIResource baselines, LAF
change without changing workspace, plugin style references such as `$Label.foreground`, and an initially null style.
After close, compare listener identities with the pre-owner set and confirm a new plugin edit triggers no host write.
A malformed or unsupported plugin style must remain installed unchanged and must not abort pane construction.

- [x] **Step 2: Run** `./gradlew :jasper-app:test --tests '*WorkspaceStyleLayerTest'`; expect missing ownership behavior.
- [x] **Step 3: Implement guarded recomposition.** This is the complete composition operation (static method on the layer):

```java
private static Object composite(JComponent component, WorkspaceStyles.Recipe recipe, Object pluginStyle) {
    Object classes = com.formdev.flatlaf.ui.FlatStylingSupport.getStyleForClasses(
        component.getClientProperty("FlatLaf.styleClass"), recipe.type());
    return com.formdev.flatlaf.ui.FlatStylingSupport.joinStyles(
        com.formdev.flatlaf.ui.FlatStylingSupport.joinStyles(recipe.style(), classes), pluginStyle);
}
```

Run every host write under a try/finally depth guard. External `FlatLaf.style` changes replace the stored plugin
object; external class changes re-resolve classes without changing `FlatLaf.styleClass`. Record only recipe.style in
`Jasper.workspaceStyle`, not the composite. Re-evaluate String references/classes on every application and after UI
updates. On malformed/unsupported plugin styles, release host values for that component, preserve the raw plugin
object, warn once per offending value, and retry when the plugin replaces it; do not suppress errors in generated keys.

For direct fallbacks, record original and last host-written values. Observe external writes and respect them as
plugin overrides for that ownership lifetime, not merely on close. A host restores an original only while the
current value is still host-owned; identity/UIResource provenance matters, not RGB equality alone. Under a LAF
refresh, remove host layers before the new UI is installed and recapture its defaults afterward so match cannot
restore old chrome. Support isolated `component.updateUI()` too: the `UI` event must rebase styling on the new
installed delegate without carrying a stale host baseline. Add a guard around any one-component UI reinstall needed
for that recovery; never recursively rebuild the whole subtree from a node listener. Pin the sequence in tests.

The layer never changes the component's zone, children, font, model, document, layout, renderer or icon.
- [x] **Step 4: Run** layer and recipe tests, then `./gradlew check`; expect exit 0 and restored effective colors.
- [x] **Step 5: Commit** `feat(workspace): preserve plugin styles in a reversible color layer` with the required trailer.

## Task 3: Track dynamic subtrees and the combo dropdown exception

**Files:** Create `WorkspaceStyler.java`, `WorkspaceComboPopup.java`, `WorkspaceStylerTest.java`,
`WorkspaceComboPopupTest.java`. Consume the two earlier helpers and existing `WorkspaceTheme`.

**Interfaces produced:**

```java
public final class WorkspaceStyler implements AutoCloseable {
    public WorkspaceStyler(JComponent root);
    public void apply(ResolvedTheme theme);
    public void beforeUiUpdate();
    public void afterUiUpdate();
    public void close();
}
final class WorkspaceComboPopup implements AutoCloseable {
    WorkspaceComboPopup(JComboBox<?> combo);
    void apply(ResolvedTheme theme);
    void beforeUiUpdate();
    void afterUiUpdate();
    public void close();
}
```

Both require EDT, have idempotent close and reject apply after close. The normal styler does not own root zone
properties; its caller does. Keep IdentityHashMap state per owner. Construction observes without theming; apply(match)
clears overrides and popup markings but can still observe future children. Never recurse into a nested chrome zone or another live styler root,
JPopupMenu, JMenu/MenuElement, JToolTip, JRootPane, Window, CellRendererPane, or an exempt subtree. Observe an exempt
root's exemption flag only, so removing it enables the existing descendants without retaining their listeners.

- [x] **Step 1: Write red tree and popup tests.** Required named cases:
  `lateChildReceivesCurrentTheme`, `removedChildRestoresAndDetaches`, `reparentingHasOneOwner`,
  `exemptionToggleRestoresAndResumes`, `headerViewportAndModelReplacementRestylesAssociatedComponents`,
  `matchLeavesNoGeneratedLayer`, `closeSuppressesQueuedRefresh`, and `nestedChromeAndTransientRendererStayUntouched`.
  Assert pre/post listener identity sets on nodes/containers; do not expose production counters for tests.
  Use two styled roots, direct Container.remove/add, and the same live JTextArea document.

```java
@Test void removedChildRestoresAndDetaches() throws Exception {
    edt(() -> {
        new ThemeController(Theme.JASPER_LIGHT);
        var root = new JPanel();
        var child = new JButton("Move");
        Object pluginStyle = "arc: 11";
        child.putClientProperty("FlatLaf.style", pluginStyle);
        root.add(child);
        var listeners = child.getPropertyChangeListeners();
        try (var styler = new WorkspaceStyler(root)) {
            styler.apply(independent());
            assertThat(child.getClientProperty("Jasper.workspaceStyle")).isNotNull();
            root.remove(child);
            assertThat(child.getClientProperty("FlatLaf.style")).isSameAs(pluginStyle);
            assertThat(child.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(child.getPropertyChangeListeners()).containsExactlyInAnyOrder(listeners);
        }
    });
}
```

For combos, retrieve `(ComboPopup) combo.getUI().getAccessibleChild(combo, 0)` without showing it. Assert its list's
background, foreground, selection colors and scrollbars, with different colors on two same-darkness workspaces.
Render selected/unselected rows and read `WorkspacePalette.current(popupList)` through a JDK-only test bridge in
`app.plugins` if needed; workspace tests must not import SDK. Alternatively use existing `AppContractTest` for that
cross-boundary assertion. Test editable combos, custom renderers, delegate replacement, returning to match and close.
A sibling JPopupMenu invoked from the same combo/pane, a tooltip and a dialog JRootPane retain chrome and no layers.

- [x] **Step 2: Run** `./gradlew :jasper-app:test --tests '*WorkspaceStylerTest' --tests '*WorkspaceComboPopupTest'`;
  expect missing implementation or wrong effective popup colors.
- [x] **Step 3: Implement identity-based ownership.** Use ContainerListeners for add/remove and PropertyChangeListeners
  for exemption, explicit zone boundaries, `UI`, viewport/rowHeader/columnHeader/tableHeader/editor replacement.
  Associated components (notably JTableHeader) are included even before a scroll pane parents them; reconcile from
  current tree/associations, with identity/reference accounting so one path's removal does not dispose a still-owned
  node. Table models are not owned; model replacement must leave renderer selection/color behavior correct.
  Release the old owner's layer before a subtree enters a new owner. Do not add a global retention map: a private
  client-property ownership marker can route release to the prior owner, and must be cleared on release/close.
  Snapshot children during mutation. Style changes can add/remove delegate children, so reconcile through a guarded
  pending-work loop rather than recursively traversing indefinitely. Any queued EDT work carries the closed/generation
  check. Close detaches observations, restores owned values and clears private markers for the whole reachable state.

  `beforeUiUpdate` suspends all current layers and releases helper-owned popup objects; apply during suspension
  updates the stored theme without writing any component; `afterUiUpdate` reconciles
  replaced delegates/children and reapplies only the current theme. Keep these calls nest-safe and bounded.

  A combo helper may inspect only **that combo's** UI accessible child, guarded by `instanceof ComboPopup`; no private
  reflection, native popup display or arbitrary invoker traversal. Style the popup's actual list/viewport/scrollbars
  with existing recipes and own any direct selection fallback. Explicitly mark the popup **list** as workspace using
  `WorkspaceTheme.apply(list, theme)` while independent, saving/restoring its previous zone properties. This allows
  its renderers/icons to resolve the zone without teaching all popups to follow invokers. The ordinary tree walker
  still stops at popup boundaries. Do not wrap or replace the plugin renderer. On match/close/UI replacement release
  every hidden-popup layer/listener/property too. Nonstandard popup UIs get the supported combo recipe and a documented
  custom-painter fallback; they must not fail startup.
- [x] **Step 4: Run** tree/popup/layer tests and `./gradlew check`; expect no leaked ownership, no global LAF changes.
- [x] **Step 5: Commit** `feat(workspace): track pane descendants and combo dropdown colors` with the required trailer.

## Task 4: Integrate mounted panes and protect Editor-owned surfaces

**Files:** Modify `workspace/WindowContent.java`, `workspace/package-info.java`; create
`workspace/PluginPaneAppearanceTest.java`; modify `WorkspaceThemeTest.java` if shared integration cases fit there.
Modify `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, `EditorTheme.java` and their
`EditorTabTest.java` / `EditorThemeTest.java` tests. No app dependency on Editor internals.

**Consumes:** Task 3's `WorkspaceStyler`, existing `WorkspaceTheme.apply/clear`, `ThemeController` subscription's
`updateDelegates` flag, and existing content registry mount/unmount callbacks.
**Produces:** One private `Map<JComponent, WorkspaceStyler>` in each WindowContent, owned exactly as its hosted entries.

- [x] **Step 1: Add red integration tests.** `newHostedContentUsesCurrentWorkspaceImmediately`,
  `sameDarknessUpdateRecolorsRetainedControls`, `movingContentBetweenWindowsRestoresThenRestyles`,
  `pluginStopAndWindowCloseReleaseAllStyles`, `chromeRebuildThenMatchUsesCurrentChrome`, and
  `sidebarAndAuxiliaryContentRemainChrome`. Reuse Contributions.content().open/remove/move and queued fixture launchers;
  never instantiate a top-level window. Include this concrete mount/remove assertion:

```java
@Test void newHostedContentUsesCurrentWorkspaceImmediately() throws Exception {
    edt(() -> {
        var themes = new ThemeController(Theme.JASPER_LIGHT);
        themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
        var owner = content(launcher(new java.util.ArrayDeque<>()), themes);
        var contributions = new dev.jasper.app.contributions.Contributions();
        owner.connectContributions(contributions);
        var panel = new JPanel();
        var list = new JList<>(new String[] {"entry"});
        panel.add(list);
        var entry = contributions.content().open("fixture", "pane", "Pane", owner.id(), panel);
        assertThat(list.getSelectionBackground()).isEqualTo(themes.current().workspace().tokens().get(Token.SELECTION));
        contributions.content().remove(entry);
        assertThat(list.getClientProperty("Jasper.workspaceStyle")).isNull();
        assertThat(panel.getClientProperty("Jasper.zone")).isNull();
    });
}
```

Use cleanup from DesktopTestSupport.closeOwners. Exercise no-op match immediately at first mount, not only after a
workspace override. Repeat startup with the complete-theme fixture lacking ToolWindow.background. Retain a document,
selection and undo edit across move/update. After disposal a theme callback must not touch old components.

For Editor, assert the area, viewport and gutter roots are exempt **before mounting**, while surrounding loading,
retry, search and document-tab controls remain eligible. App-side fixture tests use plain JComponents to model an
exempt editor; Editor tests verify its own actual client properties, palette colors, syntax/font flags and undo.
This preserves module boundaries while covering both sides of the contract.

- [x] **Step 2: Run** `./gradlew :jasper-app:test --tests '*PluginPaneAppearanceTest' :jasper-plugin-editor:test --tests '*EditorTabTest' --tests '*EditorThemeTest'`;
  expect hosted controls unstyled or missing Editor exemptions.
- [x] **Step 3: Wire exact lifetime order.** Mount: install zone → create/store styler → add tab → apply current
  resolved theme. Adding before apply lets component-aware code see its mounting location. On failure restore/close
  the partial styler and clear its zone without retaining an entry/listener. Unmount: remove/dispose styler before
  clearing zone or returning the component to the plugin; preserve normal content callbacks and tab selection.

  On a chrome delegate refresh, call every hosted styler's `beforeUiUpdate()` before
  `SwingUtilities.updateComponentTreeUI(...)`; after that rebuild, publish current zone values, update each owner's
  resolved theme while suspended, and call `afterUiUpdate()` in `finally` for successfully suspended owners. Handle
  workspace-only changes by applying values without a delegate rebuild. A callback cannot apply a disposed styler;
  iterate snapshots when content callbacks may mutate the maps. `WindowContent.close()` closes leftovers defensively
  after content-host disposal. Sidebar `ToolWindowSurface`, TerminalPane, FindBar and the native auxiliary-window
  host do not acquire generic stylers in this task.

  EditorTab marks `area`, `scroll.getViewport()` and `scroll.getGutter()` with `Jasper.workspace.exempt=true` directly
  after construction. Leave the outer scroll pane/scrollbars eligible; Editor owns their viewport and syntax, while
  standard scrollbars follow the workspace. Preserve the outer scroll background too: `EditorTheme.apply` publishes
  its editor background as that plugin-owned scroll pane's `FlatLaf.style` background, which outranks the generated
  recipe while allowing scrollbar styling. Verify this behavior in the existing Editor theme test.
  Do not exempt the entire Editor tab. Keep the Plan 2 ColorUIResource durability fix and existing onWorkspaceChanged subscription; do not add a redundant appearance subscription.
- [x] **Step 4: Run** integration, Editor and previous restyler tests, then `./gradlew check`; expect exit 0.
- [x] **Step 5: Commit** `feat(workspace): attach pane styling to content lifetimes` with the required trailer.

## Task 5: Audit bundled custom colors and close the deliverable

**Files to inspect/change with adjacent tests:**

| Production path under `plugins/` | Existing test | Required treatment |
|---|---|---|
| `remote/src/main/java/dev/jasper/remote/ui/HostsPanel.java` | `HostsPanelTest` | Active status and muted renderer text use palette for the mounted list |
| `remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsPanel.java` | `TunnelsPanelTest` | Status dots and row/error text use success/warning/error/textMuted for the mounted owner |
| `remote/src/main/java/dev/jasper/remote/ui/transfers/TransferStrip.java` | `TransferStripTest` | Cached Separator border becomes a zone-aware paint-time border; progress controls covered by Task 1 |
| `fileserver/src/main/java/dev/jasper/fileserver/ui/FileServersPanel.java` | `FileServersPanelTest` | Problem/header/state renderers use owner palette; preserve selected-row readability |
| `vault/src/main/java/dev/jasper/vault/ui/ManagerPanel.java` | `ManagerPanelTest` | Muted renderer values and cached borders become location-aware |
| `editor/src/main/java/dev/jasper/editor/ui/EditorTab.java` | `EditorTabTest` | Banner separator reads its mounted zone without losing padding |
| `editor/src/main/java/dev/jasper/editor/vim/ui/VimBar.java` | `vim/ui/VimBarTest` | Remove fixed error/chrome colors; errors, output renderer and bell flash use mounted palette |

Test paths mirror those production paths under `src/test/java/dev/jasper/<plugin>`.
Also update `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/app-architecture.md`, `docs/themes.md`,
`docs/STATUS.md`, this banner and the remaining-work assessment. Audit all seven bundled plugins, not just this table.
`vim/HighlightedYank.java`'s user-configured highlight color is document/domain styling and stays unchanged.

**Interfaces:** Use SDK `WorkspacePalette.current(Component)`; no new SDK API/version bump or plugin release bump.
Renderers pass their JList/JTable/JTree owner, never the transient renderer JLabel. Borders read the supplied paint
component. Chrome-only dialogs/settings retain chrome. All custom color calculations stay JDK-only.

- [x] **Step 1: Add red per-plugin zone tests.** Give chrome and workspace distinct sentinel UI colors, attach a pane
  under a workspace marker, invoke its actual renderer, then reparent it beneath a chrome marker and render again.
  Restore modified UI defaults in finally. Assert success/warning/error/muted values, ordinary and selected text,
  and a changed workspace color while darkness stays the same. Keep domain swatches and user-configured colors intact.
  A complete assertion in `FileServersPanelTest`, using its existing `panel`, `view`, and `views` fixtures:

```java
@Test void workspaceStatusColorUsesTheMountedPanel() throws Exception {
    javax.swing.SwingUtilities.invokeAndWait(() -> {
        Object previous = javax.swing.UIManager.get("Jasper.workspace.success");
        try {
            var expected = new java.awt.Color(0x12AB34);
            javax.swing.UIManager.put("Jasper.workspace.success", expected);
            var panel = panel();
            panel.putClientProperty("Jasper.zone", "workspace");
            view("docs", ServerState.RUNNING, false, false, Bind.LOOPBACK,
                java.util.List.of(java.net.URI.create("http://127.0.0.1:8080/")), java.util.Optional.empty());
            panel.setServers(views, java.util.Optional.empty());
            var row = (javax.swing.JPanel) panel.list.getCellRenderer().getListCellRendererComponent(
                panel.list, panel.list.getModel().getElementAt(0), 0, false, false);
            var dot = (javax.swing.JLabel) ((java.awt.BorderLayout) row.getLayout())
                .getLayoutComponent(java.awt.BorderLayout.WEST);
            assertThat(dot.getForeground()).isEqualTo(expected);
        } finally {
            javax.swing.UIManager.put("Jasper.workspace.success", previous);
        }
    });
}
```

Add the reverse chrome-zone assertion and error/muted row variants using the same real renderer; normal card text
continues to use the generic text recipe rather than acquiring an unrelated status color. For Vim, test error output, error status, normal state, bell highlight,
workspace palette change and first paint after reparenting with no intervening Vim command. Assert the generic host
style layer cannot override the current error/bell state. For Editor banner/TransferStrip/Vault borders, paint an
interior rule pixel before/after moving zones and retain insets.
- [x] **Step 2: Run** these focused checks serially; expect old chrome/fixed colors in the new assertions:

```sh
./gradlew :jasper-plugin-remote:test --tests '*HostsPanelTest' --tests '*TunnelsPanelTest' --tests '*TransferStripTest'
./gradlew :jasper-plugin-fileserver:test --tests '*FileServersPanelTest'
./gradlew :jasper-plugin-vault:test --tests '*ManagerPanelTest'
./gradlew :jasper-plugin-editor:test --tests '*EditorTabTest' --tests '*VimBarTest'
```
- [x] **Step 3: Replace only audited semantic colors.** Query WorkspacePalette per renderer/paint invocation; avoid
  constructors caching a palette before the host mounts the component. Preserve list selection background/text for
  selected rows; keep semantic colored dots separate where a status hue would make selected text unreadable.
  Border implementations are small AbstractBorder classes or existing owner methods, read palette at paint time and
  retain original insets. Do not change border geometry with a generic line-border replacement.

  VimBar keeps ownership of its temporary error/bell colors by publishing its own background/foreground colors via
  `FlatLaf.style` Map values on the affected components (literal client-property names are SDK-safe). These values
  outrank the generated layer; update them from `WorkspacePalette.current(this)` in `colors()`. Reuse its existing
  state/apply callbacks and refresh palette-dependent colors on first paint after a zone/palette change, comparing
  the immutable palette to avoid redundant writes. Its output renderer uses the supplied list's palette. No new
  global listener or subscription per label. Preserve the 150 ms bell timer and 12-row output limit.

  Execute the full audit with `rg -n 'UIManager.getColor|new Color\(|Color\.' plugins/*/src/main/java`; record each
  semantic/custom-painter occurrence as changed, chrome-only, domain-owned or inherited standard control in a short
  table in plugin authoring. Include note/updateUI overrides: styling after a delegate rebuild must still yield the
  intended muted/normal text. A documented custom painter limitation is acceptable for external plugins, not an
  excuse to leave the bundled workspace panes inconsistent.
- [ ] **Step 4: Document and verify.** Document layer precedence, style/class replacement, direct-color ownership,
  exemption, dynamic children, renderer-owner lookup, popup exception and resource cleanup. Add a plugin example:

```java
area.putClientProperty("Jasper.workspace.exempt", Boolean.TRUE);
list.setCellRenderer((owner, value, index, selected, focused) -> {
    WorkspacePalette palette = WorkspacePalette.current(owner);
    JLabel label = new JLabel(String.valueOf(value));
    label.setOpaque(true);
    label.setBackground(selected ? owner.getSelectionBackground() : palette.surfaceSunken());
    label.setForeground(selected ? owner.getSelectionForeground() : palette.text());
    return label;
});
```

  Run `./gradlew check`, all four architecture checks (included by check), and branch-wide `git diff --check
  <execution-base>`. Run AGENTS.md's Java source-hygiene scan. Count XML only in each top-level module's
  `build/test-results/test` and `plugins/*/build/test-results/test`; **do not recursively include `.worktrees`**.
  Record exact counts, source/log evidence and limitations in the plan and STATUS. Perform one independent final
  whole-branch review, resolve findings, then mark this plan executed. Do not claim native GUI verification.
- [x] **Step 5: Commit** `feat(plugins): use mounted workspace palettes in custom rendering` with the required trailer.

## User-only visual acceptance

After headless checks and review: light chrome/dark workspace and the reverse; two workspaces of the same darkness;
lists/tables/trees, headers, text selections, combo dropdowns, scrollbars, progress and disabled controls; menus and
tooltips stay chrome. SFTP/Tunnels/File Server/Vault rail panels stay chrome. Editor syntax, unsaved document/undo,
Vim errors/bell and banners retain their semantics. Move/detach/reopen a plugin pane, switch to match, then close it.
Repeat with GitHub Dark or another imported complete theme. Neither this planning task nor an unattended implementer
launches the GUI.

## Plan self-review / requirement coverage

| Requirement | Owning tests/task |
|---|---|
| §5.3 component coverage, same-dark changes, no second LAF | Task 1 real delegates/getters/paint/log assertions |
| Plugin style and class precedence, restoration, UI refresh | Task 2 component ownership; Task 4 installed refresh order |
| Late children, replacement, exemptions, removal | Task 3 ownership and listener assertions |
| Approved combo dropdown exception; all other popups chrome | Tasks 1/3 delegate/list/popup boundary tests |
| Every hosted center pane and plugin-stop cleanup | Task 4 real content registry integration |
| Editor-owned syntax and dynamic feedback | Tasks 4/5 Editor and Vim regressions |
| Custom renderers/SDK palette lookup by location | Task 5 audit and per-plugin tests |
| Optional theme keys, first-window creation | Tasks 1/4 plus retained Plan 2 startup regression |
| Architecture, documentation, review and user GUI handoff | Task 5 acceptance closure |

Plans 4–5 still own catalog conversion/vendoring/migration and the new Themes page/file reload. No runtime importer,
new theme format, SDK ABI change, native window theming, or generic renderer replacement is added here.
