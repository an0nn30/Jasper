# Theme System Plan 3: Plugin Pane Restyler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Planned, not executed. Requires completed plan 2; proposed details are in the remaining-work assessment.

**Goal:** Ordinary Swing controls hosted in workspace panes follow the workspace theme while preserving plugin styles.

**Architecture:** A workspace-owned, EDT-confined restyler attaches to mounted content, tracks its descendants and
owns only its generated style layer. Component-aware SDK palette reads handle renderers that paint their own colors.

**Tech Stack:** JBR 25, Swing, FlatLaf 3.7 `FlatStylingSupport`, SDK 0.12.0, headless JUnit.

**Spec:** `docs/superpowers/specs/2026-10-01-jasper-theme-system-design.md`, sections 1, 5.3–5.5 and 9.
**Prerequisite:** [Plan 2](2026-10-01-theme-system-2-workspace.md).

## Global Constraints

- One global LAF, always chrome; `match` removes the override layer completely.
- Workspace: center tabs/panes. Chrome: rail panels, menus, dialogs, tooltips and Settings.
- Preserve `FlatLaf.style` (String and Map) and `FlatLaf.styleClass`; plugin values win over generated values.
- `Jasper.workspace.exempt = true` exempts a subtree; `Jasper.workspaceStyle` identifies the host-owned layer.
- Container listeners cover later children and detach on removal. All mutation and lifetime calls run on EDT.
- SDK types remain confined to app.plugins; workspace uses app-native `GeneratedTheme` and `Token`.
- No new production interfaces, external dependencies, shell processes or agent-launched GUI/benchmark.
- Work on a branch; per-task red/green checks, `./gradlew check`, review and commit with
  `Co-Authored-By: Codex <noreply@openai.com>`. Record deviations here and in `docs/STATUS.md`.

## Review Focus

1. A plugin changes String/Map/class styles after mounting: its latest values survive reload and unmount (Task 2).
2. Children are added, removed, reparented or exempted after mounting: exactly one owner and no retained listeners (Task 2).
3. Same-darkness theme changes update selected/disabled/focused controls, including combo popups and table headers (Task 1).
4. A plugin pane also appears in a chrome sidebar: only workspace mounts receive workspace colors (Task 3).
5. Native/custom renderers bypass normal Swing color inheritance: semantic SDK colors resolve from their mounting zone (Task 3).

## File map

App paths are relative to `jasper-app/src/main/java/dev/jasper/app/`; tests mirror under `src/test/java/dev/jasper/app/`.

| Files | Responsibility |
|---|---|
| `workspace/WorkspaceStyles.java` (new) | Per-component generated FlatLaf style maps and direct-color fallbacks |
| `workspace/WorkspaceStyler.java` (new) | Tree listeners, style ownership, update and close |
| `workspace/WindowContent`, `WorkspaceTheme` | Mount/unmount lifetime and theme publication |
| `workspace/WorkspaceStylesTest`, `WorkspaceStylerTest` (new tests) | Real FlatLaf control coverage and reversible state |
| Remote/File Server/Vault renderer files in Task 3 | Zone-aware semantic colors |

## Task 1: Prove control coverage and define style recipes

**Files:** new `workspace/WorkspaceStyles.java`, `workspace/WorkspaceStylesTest.java`.

**Interfaces:** `static Map<String,Object> WorkspaceStyles.style(JComponent component, GeneratedTheme theme)` returns
an immutable FlatLaf style map; `static Map<String,Color> directColors(JComponent component, GeneratedTheme theme)`
returns only the tested fallback properties (`background`, `foreground`, `selectionBackground`, `selectionForeground`).
No tree listeners or mutation here. Consume plan 2's complete generated token values.

- [ ] **Step 1: Write `representativeControlsAcceptWorkspaceStylesWithoutChangingChrome`.** On EDT install a light
chrome once, construct every component below, assign a dark theme's maps, size/layout and paint each into a
BufferedImage. Assert generated properties through public getters or FlatLaf UI delegate style inspection, then pin
representative selected/disabled/focused painted pixels. Do not merely compare the map returned by the implementation.

```java
assertThat(list.getSelectionBackground()).isEqualTo(workspace.tokens().get(Token.SELECTION));
assertThat(list.getSelectionForeground()).isEqualTo(workspace.tokens().get(Token.SELECTION_TEXT));
assertThat(table.getTableHeader().getBackground()).isEqualTo(workspace.tokens().get(Token.SURFACE_RAISED));
assertThat(UIManager.getLookAndFeel()).isSameAs(chromeLaf);
assertThat(UIManager.getColor("Panel.background")).isEqualTo(chromeBackground);
```

Also pin chrome unchanged after two different dark workspaces. Use real FlatLaf delegates and capture unsupported
style errors: a logged unsupported key is a failed coverage result, even if the component happens to look correct.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*WorkspaceStylesTest'`; expect missing helper/incorrect colors.
- [ ] **Step 3: Implement these semantic recipes.** Use FlatLaf's actual styleable property names discovered in the
3.7 delegates and keep the verified key list in the test's parameter table.

| Swing type | Required coverage |
|---|---|
| JPanel, JLabel | SURFACE/TEXT; disabled text uses TEXT_DISABLED |
| JList, JTable, JTree | SURFACE_SUNKEN/TEXT; SELECTION/SELECTION_TEXT; inactive selection; border/focus |
| JTableHeader | SURFACE_RAISED/TEXT/BORDER; separately visited even outside table children |
| JTextField, JPasswordField, JTextArea, JTextPane, JEditorPane | INPUT_BACKGROUND/TEXT/TEXT_SELECTION/TEXT_SELECTION_TEXT; caret, disabled text, border/focus |
| JComboBox | Input/selection/disabled/arrow/button colors and `popupBackground`; renderer selections |
| JButton, JToggleButton | BUTTON_BACKGROUND/BUTTON_TEXT/BUTTON_BORDER; HOVER/PRESSED/FOCUS_RING; default button and disabled states |
| JCheckBox, JRadioButton | TEXT, check/radio icon colors, focus, selected and disabled states |
| JScrollPane, JViewport | SURFACE_SUNKEN and BORDER; viewport/empty region matches content |
| JScrollBar | SCROLLBAR_TRACK/SCROLLBAR_THUMB; hover/pressed thumb |
| JSplitPane | SURFACE/DIVIDER; one-touch controls if present |
| JTabbedPane | TAB_BAR/TAB_ACTIVE/TAB_ACTIVE_TEXT/TAB_HOVER/TAB_UNDERLINE; content area and separator |
| JToolBar, JSeparator | SURFACE/SEPARATOR; toolbar separator and rollover buttons |

Use `Color` values, not locale-dependent string formatting. A control unsupported by FlatLaf styling gets a narrow,
tested direct-property fallback, tracked by Task 2. Specialized custom painters use SDK palettes in Task 3; do not
pretend a background setter recolors arbitrary canvas drawing. RSyntaxTextArea syntax remains Editor-owned.

**Popup interpretation to review:** the spec explicitly names combo `popupBackground` while putting popups in
chrome. Treat a combo's own dropdown as the specified control-owned exception; menus, tooltips and dialogs remain
chrome. Do not recursively follow popup invokers or change global popup defaults. Record this clarification in the
spec if accepted; cover the combo and JPopupMenu separately in tests.
- [ ] **Step 4: Run** the coverage test and `./gradlew check`; require no styling errors and exit 0.
- [ ] **Step 5: Commit** `feat(workspace): define verified Swing workspace styles` with the required trailer.

## Task 2: Reversible tree styling and cleanup

**Files:** new `workspace/WorkspaceStyler.java`, `workspace/WorkspaceStylerTest.java`; Task 1 helper as needed.

**Interfaces:** `public final class WorkspaceStyler implements AutoCloseable`, constructor
`WorkspaceStyler(JComponent root)`, `void apply(ResolvedTheme theme)`, `void close()`. Idempotent close; applying after
close throws IllegalStateException. Constructor attaches ownership on EDT, apply handles match without losing the
ability to restyle later. Each node stores latest plugin style, generated style, last published composite and fallback
original/last-applied values. Use an IdentityHashMap owned by the styler, not static component retention.

- [ ] **Step 1: Add red tests:** `pluginEditsWinAndSurviveClose`, `styleClassWinsOverGeneratedDefaults`,
`lateChildrenAndExemptSubtreesTrackTheirOwner`, `matchAndCloseRestoreWithoutOverwritingPluginChanges`.
Build String and Map variants, then change the plugin's style while mounted. Concrete assertions:

```java
button.putClientProperty("FlatLaf.style", "arc: 13; background: #123456");
styler.apply(darkResolved);
assertThat(button.getBackground()).isEqualTo(new Color(0x123456));
button.putClientProperty("FlatLaf.style", java.util.Map.of("arc", 17, "background", new Color(0x654321)));
styler.apply(otherDarkResolved);
styler.close();
assertThat(button.getClientProperty("FlatLaf.style"))
        .isEqualTo(java.util.Map.of("arc", 17, "background", new Color(0x654321)));
assertThat(button.getClientProperty("Jasper.workspaceStyle")).isNull();
```

Add listener-count assertions after remove/close, and reparent one child between two styled roots. Toggle exemption on
an existing parent; adding descendants below it must remain untouched. A removed subtree may be reused outside the
workspace. Test plugin background edits after a direct fallback: close must preserve the newer value.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*WorkspaceStylerTest'`; expect absence of lifecycle behavior.
- [ ] **Step 3: Implement the layer algorithm.** For each node capture its plugin style before the first host write.
Resolve applicable style-class values with FlatStylingSupport's existing class resolver, then compose in this order:
`joinStyles(joinStyles(generated, classStyles), pluginStyle)`. Leave `FlatLaf.styleClass` itself unchanged. Mark
`Jasper.workspaceStyle` with the generated layer; a reentrancy guard distinguishes host writes from plugin writes.
On an external style/class change, replace the stored plugin layer and regenerate, never append onto the previously
published composite. Re-evaluate class styles after a chrome LAF rebuild. Unsupported style value types are left
untouched and reported once; never silently discard them.

On apply/match/exemption/removal/close, remove only owned layers and listeners. Restore a direct property only if its
current value still equals the last host-applied value. Recurse through current children with ContainerListeners;
also track scroll viewport/table header replacement properties. An exempt subtree needs only the parent observation
required to detect exemption removal; it receives no style layer. Stop at popup/menu/tooltip boundaries. Clear the
old owner before a reparented subtree joins a new owner. Use identity to avoid duplicate visitation.
- [ ] **Step 4: Run** coverage/lifecycle tests and `./gradlew check`; expect exit 0, no lingering host properties/listeners.
- [ ] **Step 5: Commit** `feat(workspace): add reversible plugin pane restyler` with the required trailer.

## Task 3: Mount ownership, bundled renderers and plugin guidance

**Files:** app `workspace/WindowContent`, `WorkspaceTheme`, `workspace/PluginPaneAppearanceTest.java` (new test);
`plugins/remote/src/main/java/dev/jasper/remote/ui/HostsPanel.java`, `ui/tunnels/TunnelsPanel.java`,
`ui/transfers/TransferStrip.java`; `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/FileServersPanel.java`;
`plugins/vault/src/main/java/dev/jasper/vault/ui/ManagerPanel.java`; their existing tests;
`docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/app-architecture.md`, `docs/STATUS.md`.

**Interfaces:** consume `WorkspaceStyler(root).apply(theme)` and `WorkspacePalette.current(Component)` from plan 2.
WindowContent owns one styler per mounted ContentEntry and closes it in unmount/dispose/plugin-stop paths. Sidebar
ToolWindowSurface remains chrome-owned. No new cross-package service or SDK dependency in workspace.

- [ ] **Step 1: Add red integration tests.** Mount the same fixture content in a workspace and rail host in turn.
Assert list/table selection, empty/loading labels, status colors, border and retained named icons resolve to that
mount's zone. Open new plugin content after theming; change dark→different dark; close the plugin and assert no
restyler callback runs. Chrome LAF update followed by workspace application must retain plugin styles.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*PluginPaneAppearanceTest'`; expect unstyled hosted content.
- [ ] **Step 3: Wire ownership and audit custom colors.** Construct after the host zone marker is installed, apply the
current theme immediately, update after chrome delegate refresh, close before clearing zone properties. Search all
bundled plugin UI production code for `UIManager.getColor`, fixed `Color` status constants and renderer setters;
record the resulting audit table in `docs/plugin-authoring.md`. For semantic surface/text/status values use
`WorkspacePalette.current(theMountedListOrTable)` on each renderer invocation; renderers are often detached, so
passing the renderer itself is wrong. Chrome-only dialogs/settings continue using chrome. Preserve domain colors
(color swatches, terminal output, syntax, user content); add a test for each changed painter.
- [ ] **Step 4: Document** style/class precedence, exemption, dynamic children, ownership cleanup, custom painting with
palettes and workspace events, and the combo dropdown exception. Update STATUS and this plan's banner with actual
results/deviations. Run `./gradlew check`, count XML results, run source hygiene and `git diff --check`, then complete
an independent whole-branch review and fix findings before claiming completion.
- [ ] **Step 5: Commit** `feat(plugins): apply workspace themes to hosted Swing content` with the required trailer.

**User-only visual acceptance:** light chrome/dark workspace and the reverse; lists/tables/trees, combo dropdown,
scrollbars and disabled buttons; SFTP/Tunnels/File Server/Vault sidebars stay chrome; Editor and hosted tabs follow
workspace; menus/tooltips remain chrome; switch back to match and close/reopen plugins. Do not launch GUI in the agent.
