# Full windows 4: layouts Implementation Plan

> **Status (2026-10-27):** Executed, Tasks 1-4, on `claude/full-windows` (not merged or pushed). Additions to the plan
> text: `Saved` and `LayoutApplied` result enums; `LayoutsFile.fits` (the size check a save asks before it writes);
> `LayoutPalette` in `workspace`, whose palette ids are digests of the layout names; `leftOutCount` (how many tabs a layout
> leaves out, which the New Layout dialog states); a fresh start with nothing saved opens the default layout. Controller
> Rulings: 1, a save that would pass the 256 KiB limit is refused with "This layout is too large to save."; 2, a fresh
> start opens the default layout; 3, a failure partway through applying shows "Jasper could not apply the layout
> “<name>”.", `openEmpty` is exact when no window is open, and `leftOutCount` counts what a layout leaves out.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A default layout for new windows and any number of named layouts, saved from the active window and applied
to it through Window › Layouts, each holding a window's tabs, panes and tool windows.

**Architecture:** A layout is a `WorkspaceFile.WindowRecord` without bounds. `layouts.toml` (beside `workspace.toml`)
keeps an optional default and a list of named layouts through a `LayoutStore`. `WindowManager` turns a window into a
layout and applies one to a window (guards, then close its tabs, restore the layout's tabs and replace its tool
windows). New windows, Empty State and a failed restore open the default layout; with none saved, the built-in layout.
A Window menu holds Layouts ▸.

**Tech Stack:** Java 25 (JBR 25), Swing, tomlj 1.1.1, JUnit 5 + AssertJ, Gradle wrapper.

**Spec:** [`docs/superpowers/specs/2026-10-26-jasper-full-windows-design.md`](../specs/2026-10-26-jasper-full-windows-design.md)
sections 5.2, 5.5, 6.2 and 7 (decisions 9 to 12). Plans 1 to 3 are done on this branch.

## Global Constraints

- Worktree `.claude/worktrees/full-windows`, branch `claude/full-windows`; never build in `~/projects/moray`; never
  launch the GUI or a benchmark; `./gradlew` only. One commit per task ending with
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>`.
- A **layout** is a window's state without its bounds: its tabs and panes and its tool windows. It keeps each
  terminal's renamed title, profile and last local directory and each plugin view's restore state, as the last state
  does; an untitled editor and Jasper's own tabs (the Errors tab) are left out.
- **The default layout** is what New Window and Empty State open. Until the user saves one it is the **built-in
  layout**: one terminal tab starting where a new shell starts (the active terminal's directory for New Window) and
  `ToolWindows.BUILT_IN`.
- `layouts.toml`, beside `workspace.toml`: `version = 1`; an optional `[default]` layout and a `[[layouts]]` array of
  `name` plus layout, with the window keys `workspace.toml` uses (no bounds). 256 KiB limit, bounded read, atomic
  write. A bad entry is dropped alone and logged; an unreadable file is kept as `layouts.toml.bad` and read as empty.
- **Window › Layouts**, in this order: **Default**; separator; each named layout ▸ **Apply**, **Rename…**, separator,
  **Delete…**; separator; **Save Current Layout as New…**; **Save Current Layout as Default**; **Reset Default
  Layout**.
- Actions (bindable, no default keys): `apply_default_layout` ("Apply Default Layout"; menu label **Default**),
  `save_layout_as_new` ("Save Current Layout as New…"), `save_layout_as_default` ("Save Current Layout as Default"),
  `reset_default_layout` ("Reset Default Layout"); the palette lists **Apply Layout: <name>** per named layout.
- The **New Layout** dialog: message "Layout name:", first value "Custom". A name is 1 to 64 characters after
  trimming; a name in use is refused with "A layout named “<name>” already exists." and the dialog asks again. Rename…
  uses the same rules. When the window holds tabs a layout cannot keep, the dialog's message adds "<n> tab(s) can't be
  kept in a layout and are left out." Delete… asks "Delete the layout “<name>”?" with **Delete**; Reset Default Layout
  asks "Reset the default layout to the built-in layout?" with **Reset**.
- **Applying** a layout replaces the active window's contents: every tab's guards run first; a refusal leaves the
  window unchanged; then its tabs close and the layout's tabs, panes and tool windows take their place; its bounds
  stay. A tab whose plugin is missing or cannot restore it comes back as a placeholder.
- *Ruling (this plan):* a window opened while another is open (New Window, tear-out, `NEW_WINDOW`) applies
  `forNewWindow()` to the default layout's tool windows (Zen off, Float/Window tool windows hidden), so a default saved
  with floating tool windows does not stack identical native windows; Apply and Empty State use the layout exactly.
- *Ruling (this plan):* a torn-out tab's or pane's window, and a plugin's `NEW_WINDOW` window, take only the default
  layout's tool windows (spec 5.2's ruling); New Window takes its tabs too.
- *Ruling (this plan):* a failed restore and Empty State open the default layout (spec 6.2), replacing plan 3's
  built-in layout there; with no default saved, still the built-in layout.

## Review Focus

1. **A layout saved from a window with an unsaved untitled editor** keeps every other tab and says how many it left
   out; applying it later never shows an empty editor. → Task 3 `saveAsNewSaysWhichTabsAreLeftOut`.
2. **Applying a layout while an editor has unsaved changes** asks first, and Cancel leaves every tab and tool window as
   it was. → Task 2 `applyingALayoutAGuardRefusesChangesNothing`.
3. **Deleting or renaming a layout the palette lists** updates the palette and the menu at once. → Task 3
   `renamingALayoutRenamesItsPaletteEntry`.
4. **A corrupt `layouts.toml`** never stops Jasper from starting or opening a window; New Window then opens the
   built-in layout and the bad file is kept. → Task 1 `anUnreadableFileIsKeptAsideAndReadAsEmpty`, Task 2
   `newWindowWithNoUsableDefaultOpensTheBuiltInLayout`.
5. **A default layout holding several terminals** starts each in its saved directory, falling back to where a new
   shell starts when that directory is gone or remote. → Task 2 `newWindowStartsTheDefaultLayoutsTerminalsWhereTheyWere`.

---

### Task 1: `layouts.toml` and the layout store

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/persistence/LayoutsFile.java`, `jasper-app/src/main/java/dev/jasper/app/workspace/LayoutStore.java`
- Modify: `persistence/WorkspaceFile.java` (package-private encode/decode of one `WindowRecord` under a table prefix, reused by `LayoutsFile`), `platform/AppDirs.java` (`layouts()` = `root.resolve("layouts.toml")`)
- Test: `persistence/LayoutsFileTest.java`, `workspace/LayoutStoreTest.java`, `platform/AppDirsTest.java`

**Interfaces:**
- Produces (`persistence.LayoutsFile`): `record Named(String name, WorkspaceFile.WindowRecord layout)`;
  `record Layouts(Optional<WorkspaceFile.WindowRecord> defaultLayout, List<Named> named)` with `Layouts.EMPTY`;
  `static Layouts read(Path file)` (never throws; unreadable → keeps `layouts.toml.bad`, returns `EMPTY`);
  `static void write(Path file, Layouts layouts) throws IOException`; `MAX_BYTES = 256 * 1024`. A layout's record has
  `Optional.empty()` bounds and extended state 0.
- Produces (`workspace.LayoutStore`, EDT): `LayoutStore(Path file, Executor writer)`; `Layouts layouts()` (read once,
  lazily); `Optional<WindowRecord> defaultLayout()`; `List<Named> named()`; `void saveAsDefault(WindowRecord)`;
  `void resetDefault()`; `boolean saveAsNew(String name, WindowRecord)` (false when the name is in use);
  `boolean rename(String from, String to)`; `void delete(String name)`; `void onChanged(Runnable)`;
  `static Optional<String> validName(String typed)` (trimmed, 1 to 64 characters). Every change writes on `writer`,
  in order.

- [ ] **Step 1: Write the failing tests**

```java
// LayoutsFileTest
@Test void aDefaultAndTwoNamedLayoutsRoundTrip() { /* write then read == the same Layouts; text has version = 1, [default], two [[layouts]] with name */ }
@Test void anUnreadableFileIsKeptAsideAndReadAsEmpty() { /* garbage text: read() == EMPTY; layouts.toml.bad holds the garbage */ }
@Test void aBadLayoutIsDroppedAlone() { /* one [[layouts]] entry with a bad tab: the other layouts and the default come back */ }

// LayoutStoreTest
@Test void aNameInUseIsRefusedAndNamesAreTrimmed() {
    assertThat(LayoutStore.validName("  Work  ")).contains("Work");
    assertThat(LayoutStore.validName("   ")).isEmpty();
    assertThat(LayoutStore.validName("x".repeat(65))).isEmpty();
    // saveAsNew("Work", r) true; saveAsNew("Work", r) false; rename("Work", "Play") true; named() names == ["Play"]
}
@Test void resetDefaultForgetsTheSavedDefault() { /* saveAsDefault then resetDefault: defaultLayout() empty, file rewritten */ }
@Test void changesAreWrittenInOrder() { /* three changes with a recording executor: the file ends with the last */ }
```

- [ ] **Step 2: Run them to verify they fail** — `./gradlew :jasper-app:test --tests '*LayoutsFileTest' --tests '*LayoutStoreTest'`, expected FAIL to compile.
- [ ] **Step 3: Implement** as the Interfaces block says, reusing `WorkspaceFile`'s window encoding and `TomlStateFile`.
- [ ] **Step 4: Run** the same tests and `./gradlew :jasper-app:test --tests 'dev.jasper.app.persistence.*'`, expected PASS.
- [ ] **Step 5: Commit** — `Layouts: layouts.toml keeps a default layout and named layouts`

---

### Task 2: Windows open from layouts and apply them

**Files:**
- Modify: `workspace/WindowManager.java` (`layouts(LayoutStore)`; `layoutOf`; `applyLayout`; `openWindow`, `openEmpty`/`emptyWindow`, tear-out, `restore`'s Empty State and failure branches), `workspace/WindowContent.java` (closing every tab for an apply; restoring a layout into an open window, reusing `restore` and `replaceToolWindows`), `application/JasperApplication.java` (creates the `LayoutStore` on `dirs.layouts()` beside the workspace store and gives it to the manager)
- Test: `workspace/WindowManagerTest.java`, `workspace/LayoutApplyTest.java` (new), `application/JasperApplicationWorkspaceTest.java`

**Interfaces:**
- Consumes: Task 1's `LayoutStore`, `LayoutsFile`.
- Produces (`WindowManager`, EDT): `void layouts(LayoutStore store)`; `Optional<WorkspaceFile.WindowRecord> layoutOf(JasperWindow window)`
  (its record with bounds removed and extended state 0, the same tabs the last state would keep);
  `int leftOut(JasperWindow window)` (how many of its tabs a layout cannot keep); `boolean applyLayout(JasperWindow window, WorkspaceFile.WindowRecord layout)`
  (false when a guard refuses; otherwise the window holds exactly the layout's tabs, panes and tool windows);
  `WorkspaceFile.WindowRecord defaultLayout(Path directory)` (the saved default, else the built-in layout with its
  terminal in `directory`).
- New Window opens `defaultLayout(active terminal's directory)`: its tabs, and its tool windows through
  `forNewWindow()` when another window is open. Tear-out and `NEW_WINDOW` take only the default's tool windows (through
  `forNewWindow()`). Empty State and a failed restore open the default layout at the saved bounds.

- [ ] **Step 1: Write the failing tests**

```java
// LayoutApplyTest
@Test void applyingALayoutReplacesTheWindowsTabsAndToolWindows() {
    // Window with tabs A, B; a layout with tabs C, D and the rail hidden: applyLayout → true; tabs are C, D (new ids),
    // toolWindows().railVisible() false, placement unchanged.
}
@Test void applyingALayoutAGuardRefusesChangesNothing() {
    // Tab A guards and refuses: applyLayout → false; tabs still A, B; tool windows unchanged.
}
@Test void aLayoutTabWhosePluginIsMissingComesBackAsAPlaceholder() { }

// WindowManagerTest
@Test void layoutOfLeavesOutBoundsAndUnsavableTabs() { /* bounds empty, extendedState 0; an app-owned tab left out; leftOut == 1 */ }
@Test void newWindowOpensTheDefaultLayout() { /* a saved default with two tabs: openWindow → two tabs, the default's tools via forNewWindow */ }
@Test void newWindowWithNoUsableDefaultOpensTheBuiltInLayout() { /* no default: one terminal tab in the active terminal's directory, BUILT_IN */ }
@Test void newWindowStartsTheDefaultLayoutsTerminalsWhereTheyWere(@TempDir Path folder) {
    // A default with terminals in folder/a (exists) and folder/gone (missing): shells start in folder/a and where a new
    // shell starts.
}
@Test void aTornOutTabsWindowTakesOnlyTheDefaultsToolWindows() { /* default with two tabs and a hidden rail: tear-out → one tab (the moved one), rail hidden */ }
@Test void emptyStateOpensTheDefaultLayoutAtTheSavedBounds() { /* replaces plan 3's built-in expectation when a default is saved */ }
```

- [ ] **Step 2: Run them to verify they fail** — `./gradlew :jasper-app:test --tests '*LayoutApplyTest' --tests '*WindowManagerTest'`, expected FAIL.
- [ ] **Step 3: Implement** as the Interfaces block says. Applying closes the window's tabs without letting the
  window's empty workspace count as "emptied by a move" (the window stays), then restores the layout's tabs through the
  same path a restore uses (fresh tab ids, so a layout applied twice never clashes), then `replaceToolWindows`.
- [ ] **Step 4: Run** `./gradlew :jasper-app:test`, expected PASS.
- [ ] **Step 5: Commit** — `Layouts: new windows open the default layout; a window can apply a layout`

---

### Task 3: Window › Layouts, the commands and the palette

**Files:**
- Modify: `commands/ActionId.java` (the four actions), `workspace/MenuBarLayout.java` (`BuiltIn` gains `window`), `workspace/StandardMenuBar.java` (Window after Tab), `workspace/ClassicMenuBar.java` (Layouts ▸ in its Window menu), `workspace/WindowChrome.java` (the Window menu model; the Layouts submenu rebuilt each time it opens), `workspace/WorkspaceActions.java`, `workspace/WindowCommands.java` (palette keywords "layout"), `workspace/WindowContent.java` (the name, rename and confirm dialogs, as `askTabName` does, behind package-private seams tests set), `application/JasperApplication.java` or a new `application/LayoutsFeature.java` (registers **Apply Layout: <name>** palette actions through `Contributions.addAction`, re-registered on every store change)
- Modify: `docs/keyboard-shortcuts.md` (the four actions, no default keys)
- Test: `workspace/WindowMenuTest.java`, `workspace/LayoutCommandsTest.java` (new), `config/KeymapDefaultsTest.java`, `shortcuthelp/ShortcutHelpTest.java`

**Interfaces:**
- Consumes: Task 1's `LayoutStore`, Task 2's `layoutOf`, `leftOut`, `applyLayout`.
- Produces: `ActionId.APPLY_DEFAULT_LAYOUT("apply_default_layout", "Apply Default Layout", "none", "none")`,
  `SAVE_LAYOUT_AS_NEW("save_layout_as_new", "Save Current Layout as New…", "none", "none")`,
  `SAVE_LAYOUT_AS_DEFAULT("save_layout_as_default", "Save Current Layout as Default", "none", "none")`,
  `RESET_DEFAULT_LAYOUT("reset_default_layout", "Reset Default Layout", "none", "none")`; the dialogs' exact copy is in
  the Global Constraints. **Default** applies `defaultLayout(null)` to the active window.

- [ ] **Step 1: Write the failing tests**

```java
// WindowMenuTest
@Test void theLayoutsMenuIsInIntelliJsOrder() {
    // Two named layouts "Alpha", "Beta": Window › Layouts items == ["Default", "-", "Alpha ▸", "Beta ▸", "-",
    // "Save Current Layout as New…", "Save Current Layout as Default", "Reset Default Layout"]; Alpha's submenu ==
    // ["Apply", "Rename…", "-", "Delete…"]; the standard bar's menus are File, Edit, View, Tab, Window, … in that order.
}

// LayoutCommandsTest
@Test void saveAsNewAsksForANameStartingAtCustomAndRefusesOneInUse() { /* seam answers "Work", then "Work" again (refused with the copy), then "Home" */ }
@Test void saveAsNewSaysWhichTabsAreLeftOut() { /* a window with an app-owned tab: the dialog message ends "1 tab(s) can't be kept in a layout and are left out." */ }
@Test void deleteAndResetAskFirst() { /* confirm seam false: nothing changes; true: layout gone / default reset */ }
@Test void renamingALayoutRenamesItsPaletteEntry() { /* palette has "Apply Layout: Work"; rename to "Home": "Apply Layout: Home" only */ }
@Test void applyingANamedLayoutFromThePaletteReplacesTheActiveWindow() { }
```

- [ ] **Step 2: Run them to verify they fail** — `./gradlew :jasper-app:test --tests '*WindowMenuTest' --tests '*LayoutCommandsTest'`, expected FAIL.
- [ ] **Step 3: Implement** as the Interfaces block and Global Constraints say.
- [ ] **Step 4: Run** `./gradlew :jasper-app:test`, expected PASS.
- [ ] **Step 5: Commit** — `Layouts: Window › Layouts, its commands and the palette`

---

### Task 4: Documentation and the whole check

**Files:**
- Modify: `docs/workspace.md` (a "Layouts" section under Windows: what a layout holds, the menu, the dialogs, applying; New Window and Empty State open the default layout), `docs/configuration.md` (`layouts.toml` in the files table and its own section like `workspace.toml`'s; `system.reopen`'s Empty State now "the default layout"; the setting's description in `config/CoreSettings.java` and `config.example.toml` to match), `docs/keyboard-shortcuts.md` (if Task 3 did not), `docs/STATUS.md` (a plan 4 entry; drop plan 3's "until plan 4" interims), this plan's status banner
- [ ] **Step 1: Find stale mentions** — `grep -rniE 'built-in layout|until plan 4|copy of the active window' docs/*.md jasper-*/README.md`; each hit is updated or a dated record.
- [ ] **Step 2: Update the guides** with the smallest accurate edits (AGENTS.md "Documentation edits").
- [ ] **Step 3: Run** `./gradlew check`, expected BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** — `Docs: layouts (full windows plan 4)`
- [ ] **Step 5: Hand the GUI checks to the user** (not run by an agent): save a layout as new, apply it to another
  window, rename and delete it; save as default and open New Window; reset the default; Empty State with a saved
  default; the Window menu in the standard and Enterprise looks on macOS, Windows and Linux.
