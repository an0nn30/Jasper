# Vim mode: IdeaVim's clipboard, conflicts, Ctrl+Shift keys and status widget — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In the Editor's Vim mode, Copy/Cut/Paste act on the Visual area, Ctrl+Shift+letter is its own key, Vim keys that clash with Editor or window shortcuts post IdeaVim's one-time notification, and a "V" status widget with a menu replaces `Vim: On/Off`.

**Architecture:** Engine work stays in `dev.jasper.editor.vim` (`VimEditor`, `VimTextArea`, `KeyInput`, `KeyHandlers`); the notification and widget logic live in `dev.jasper.editor.ui` (new `ShortcutConflicts`, `StatusLine`, `EditorWindow`). The widget needs an SDK addition (`ContentStatus` icon/dimmed/menu, `PluginSettings.show()`) implemented by the app (`HostedUi`, `WindowStatusBar`, `DeclaredSettings`) and the testkit.

**Tech Stack:** Java 25 (JBR), Swing, RSyntaxTextArea 3.6.0, JUnit 5 + AssertJ, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-10-23-jasper-vim-ideavim-keys-design.md`

## Global Constraints

- Work in the worktree `.claude/worktrees/vim-ideavim-keys`, branch `claude/vim-ideavim-keys`; never commit on `main`; never launch the GUI or benchmarks.
- Every commit message ends with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Jasper is an MVP: change behaviour outright; no compatibility shims, deprecations or migration notes.
- No presentation code in plugin classes (`PluginPresentationCheck`): the Editor describes the widget and menu; the app draws them.
- No animations.
- SDK additions join the unreleased SDK 0.21.0: no version bump; plugin ranges stay `>=0.21.0, <0.22`.
- Source hygiene: no raw control/private-use characters in source (AGENTS.md script).
- Docs: smallest accurate edit; `docs/sdk-reference.html` only via `./gradlew sdkReference`; `docs/editor-vim-reference.md` only via `./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true`.
- Exact copy (spec): title "`<key>` is a shortcut for both Vim and the Editor" ("… and Jasper" when only the window binds it); content "It is now used by Vim, but you can change this."; actions "Use as Editor shortcut", "Configure…"; error "Could not update the vimrc"; widget tooltip "Vim mode"; menu "Enabled", "Open vimrc", "Reload vimrc", "Settings…".
- Mode words: `NORMAL` (Normal, operator-pending), `INSERT`, `REPLACE`, `(INSERT)` (insert-normal), `VISUAL`, `V-LINE`, `V-BLOCK`, `COMMAND` (command line, pattern).

## Review Focus

1. Visual Copy while the Visual area ends at the last line with no final line break — clipboard holds exactly what `y` would yank, no exception (Task 1 test `linewiseCopyOfTheLastLineWithoutABreak`).
2. Paste in Visual mode with an empty or unreadable clipboard — nothing changes, Visual mode ends, no exception (Task 1 test `pasteWithEmptyClipboardLeavesTheText`).
3. A vimrc that cannot be written (folder is a file / read-only) on "Use as Editor shortcut" — error notification, no reload, no crash (Task 3 test `unwritableVimrcPostsAnError`).
4. A corrupt or unreadable `shortcut-conflicts` file — treated as empty, notifications still work, no start-up failure (Task 3 test `unreadableConflictFileIsEmpty`).
5. Ctrl+Shift+letter mapped in a vimrc (`nnoremap <C-S-j> …`) — Vim takes it; `<C-S-J>` and `<S-C-j>` spell the same key (Task 2 test `ctrlShiftMappingsAreVimKeys`).

---

### Task 1: Copy, Cut and Paste act on the Visual area

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/VimTextArea.java` (overrides near `replaceSelection`, line ~144)
- Possibly: `plugins/editor/src/main/java/dev/jasper/editor/vim/Visual.java`, `Operators.java` (reuse `Visual.range`, `Operators.content`)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/VimClipboardTest.java` (new; fixture style of `VimEditorTest`: a `VimHost` over a `Map<Character,String>` clipboard, `onEdt`, `type`)
- Docs: `docs/editor.md` (Vim mode: "The Vim bar and the caret" Visual bullet, or a new bullet in "Registers, macros and mappings": Copy/Cut/Paste in Visual mode)

**Interfaces:**
- Produces (package `dev.jasper.editor.vim`, all UI thread):
  - `public boolean visual()` on `VimEditor` — whether the mode is VISUAL, VISUAL_LINE or VISUAL_BLOCK.
  - `void copyVisual()` — writes the area's yank text (`Operators.content(ctx, Visual.range(ctx, mode(), "y").span()).text()`) to the host clipboard `'+'`; mode and area unchanged; registers untouched.
  - `void cutVisual()` — `copyVisual()`, then deletes the area as `"_d` would (black-hole register, one undo step), Normal mode.
  - `void replaceVisual(String text)` — one undo step: characterwise replaces the span; linewise replaces the lines' text keeping the last line's break (one trailing `\n` of `text` dropped); blockwise deletes the block and inserts `text` once at its top-left. Ends in Normal mode, cursor on the last inserted character (start of the area when `text` is empty). Registers untouched.
  - `VimTextArea` overrides `copy()`, `copyAsStyledText()` (RSyntaxTextArea's Cmd/Ctrl+C action), `cut()`, `paste()` and `replaceSelection(String)` (ClipboardHistoryPopup calls it): while `vim().map(VimEditor::visual)` is true they call the methods above (`paste()` reads the clipboard through the host `'+'`; empty → `Visual.end`, nothing changes); otherwise `super`. Copy and Cut also add the text to RSyntaxTextArea's `ClipboardHistory.get()` so Paste from a list offers it.

- [ ] **Step 1: Write the failing tests** in `VimClipboardTest`:
  - `copyInCharwiseVisualCopiesAndStays`: text `"abc def\n"`, `type("vl")`, `area.copy()` → `clipboard.get('+')` is `"ab"`, `editor.mode()` is `VISUAL`, text unchanged; same via `area.copyAsStyledText()`.
  - `copyInLinewiseVisual`: `"one\ntwo\nthree\n"`, `type("Vj")`, `copy()` → `"one\ntwo\n"`.
  - `linewiseCopyOfTheLastLineWithoutABreak`: `"one\ntwo"`, `type("jV")`, `copy()` → `"two\n"`.
  - `copyInBlockwiseVisual`: `"abc\ndef\n"`, `type("l<C-v>j")`, `copy()` → `"b\ne"`.
  - `cutDeletesAndEndsVisual`: `"abc def\n"`, `type("vl")`, `cut()` → clipboard `"ab"`, text `"c def\n"`, mode `NORMAL`, unnamed register (`vim.registers()` / `:reg` content for `"`) unchanged from before, `type("u")` restores `"abc def\n"`.
  - `pasteReplacesCharwise`: clipboard `"XY"`, `"abc def\n"`, `type("vl")`, `paste()` → `"XYc def\n"`, `NORMAL`, cursor `Pos(0,1)`, one `u` restores.
  - `pasteReplacesLinesKeepingTheBreak`: clipboard `"new"`, `"one\ntwo\nthree\n"`, `type("Vj")`, `paste()` → `"new\nthree\n"`; clipboard `"new\n"` gives the same.
  - `pasteReplacesBlockAtItsCorner`: clipboard `"Z"`, `"abc\ndef\n"`, `type("l<C-v>j")`, `paste()` → `"aZc\ndf\n"`.
  - `pasteWithEmptyClipboardLeavesTheText`: no `'+'` entry, `type("vl")`, `paste()` → text unchanged, `NORMAL`.
  - `historyPasteReplacesTheArea`: `type("vl")`, `area.replaceSelection("Q")` → `"Qc def\n"`.
  - `outsideVisualTheyActAsBefore`: Normal mode, `area.select(0,2)` not used by Vim → `copy()` falls through to `super` (assert via a selection made with Vim off, or Insert mode `replaceSelection` inserts at the caret).
- [ ] **Step 2: Run** `./gradlew :jasper-plugin-editor:test --tests '*VimClipboardTest'` — expect FAIL (methods missing / nothing copied).
- [ ] **Step 3: Implement** the `VimEditor` methods and `VimTextArea` overrides above. Reuse `Visual.range`, `Operators.content`, and Vim's delete operator with register `'_'` for the delete parts so undo grouping and cursor rules match `d`; wrap each paste in one undo step the way existing Vim edits are grouped. Check `EditorTab.copy`/`paste` and the right-click menu end up in these methods.
- [ ] **Step 4: Run** the test class and `./gradlew :jasper-plugin-editor:test` — expect PASS.
- [ ] **Step 5: Docs** — one bullet in `docs/editor.md`'s Vim mode section: in Visual mode Copy puts the Visual text on the clipboard and stays in Visual mode; Cut copies and deletes it; Paste (and Paste from a list) replaces it; Vim's registers are untouched.
- [ ] **Step 6: Commit** `Editor: Copy, Cut and Paste act on Vim's Visual area`.

### Task 2: Ctrl+Shift+letter is its own key

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java` (`normalize` ~216, `notation` ~81)
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/VimTextArea.java` or `ui/EditorTab.java` (where `allowInNormalMode` is called)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/KeyInputTest.java`, `KeyTranslatorTest.java`, a `VimTextArea` key-routing test (existing class that drives `processKeyEvent`, e.g. `VimEditorTest`, or new `VimKeyRoutingTest`)
- Docs: `docs/editor.md` "Which keys Vim takes" (the "On Linux and Windows several … Ctrl+Shift with a letter as Ctrl with that letter" bullet, ~716-721); the commands table row for Paste from a list (~227) if it needs a Vim note; regenerate `docs/editor-vim-reference.md` if `VimDocsTest` reports a change.

**Interfaces:**
- Produces: `KeyInput.normalize(key, CTRL|SHIFT)` for a letter keeps `SHIFT` and lower-cases the letter; Shift alone still folds into the character; `notation()` prints `C-` before `S-` (`<C-S-v>`). `KeyInput.parse("<C-V>")` equals `parse("<C-v>")`; `parse("<C-S-v>")`, `parse("<C-S-V>")` and `parse("<S-C-v>")` are equal and differ from `<C-v>`.
- `RTextAreaEditorKit.clipboardHistoryAction` is passed to `allowInNormalMode` so a declined Ctrl+Shift+V (Cmd+Shift+V on macOS already passes as ⌘) runs Paste from a list in Normal and Visual mode.

- [ ] **Step 1: Write failing tests:**
  - `KeyInputTest.ctrlShiftLetterIsItsOwnKey`: the parse equalities above; `KeyInput.parse("<C-S-v>").getFirst().notation()` is `"<C-S-v>"`; `parse("<S-a>")` still equals `parse("A")`.
  - `KeyTranslatorTest.ctrlShiftLetter`: `pressed(VK_V, CTRL_DOWN_MASK|SHIFT_DOWN_MASK, false)` is `<C-S-v>`, not `<C-v>`.
  - `ctrlShiftMappingsAreVimKeys`: with `nnoremap <C-S-j> dd`, `offer(<C-S-j>)` is true and deletes a line; without the mapping `offer(<C-S-v>)` is false in Normal, Insert and Visual mode.
  - Routing (`processKeyEvent` with a real `KeyEvent` on a `VimTextArea` with Vim on, Normal mode, Linux modifiers): Ctrl+Shift+V reaches the action bound to `clipboardHistoryAction` (replace it in the action map with a recording action); in Insert mode Ctrl+Shift+W is not consumed by Vim (reaches a parent's `WHEN_ANCESTOR_OF_FOCUSED_COMPONENT` recording binding) and the text is unchanged.
- [ ] **Step 2: Run** the tests — expect FAIL.
- [ ] **Step 3: Implement** `normalize`/`notation` and the `allowInNormalMode(clipboardHistoryAction)` call. Search the Vim code and tests for places that relied on the fold (`<C-S-…>`, `ctrl(` with upper-case letters, corpus cases) and update them.
- [ ] **Step 4: Run** `./gradlew :jasper-plugin-editor:test` — expect PASS (including the Vim corpus and `VimDocsTest`; regenerate the reference with `-PupdateDocs=true` if it reports a diff).
- [ ] **Step 5: Docs** — rewrite the Linux/Windows bullet: Ctrl+Shift with a letter is its own key, which Vim leaves to the Editor and the window unless a mapping or `sethandler` names it (`<C-S-v>`); so Ctrl+Shift+G is Find Next, Ctrl+Shift+W closes the pane in Insert mode, Ctrl+Shift+V pastes from the list in every mode; keep the Ctrl+F/L/N/V/C facts that remain true.
- [ ] **Step 6: Commit** `Editor: Vim reads Ctrl+Shift with a letter as its own key`.

### Task 3: The shortcut-conflict notification

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/ui/ShortcutConflicts.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/VimTextArea.java` (report in `handled` when `offer` takes a `KEY_PRESSED` key), `VimEditor.java`, `KeyHandlers.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java` (create one `ShortcutConflicts`, hand it to each tab's area, `vimrcFile()`, `reloadVimrc()`, opening a file in an Editor tab)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/ShortcutConflictsTest.java` (new), a routing test in the vim test package
- Docs: `docs/editor.md` "Which keys Vim takes" and "The vimrc"

**Interfaces:**
- `KeyHandlers.has(KeyInput key)`: whether any `sethandler` entry names the key.
- `VimEditor.ownsByDefault(KeyInput key)` (package-private): Vim took the key through the `DEFAULT` handler (no entry for this mode) and not because the command line, a prompt or output owned every key.
- `VimTextArea.onConflict(java.util.function.BiConsumer<KeyInput, Boolean> listener)`: called after Vim takes a `KEY_PRESSED` key for which `ownsByDefault` is true and `!vim.handlers().has(key)`, and the stroke is bound outside Vim: the text area's `WHEN_FOCUSED` map (`bound(event)`), any ancestor's `WHEN_ANCESTOR_OF_FOCUSED_COMPONENT` map, the root pane's `WHEN_IN_FOCUSED_WINDOW` map, or a `JMenuBar` accelerator. The boolean is true when the Editor (text area or an ancestor inside the Editor's content) binds it, false when only the window does.
- `ShortcutConflicts(Path file, NotificationGroup group, Consumer<Path> openVimrc, Runnable reloadVimrc, Supplier<Path> vimrc)` with `void taken(KeyInput key, boolean editorBinds)`: notifies once per key ever. `file` is `dataDirectory().resolve("shortcut-conflicts")`: one `notation()` per line, read lazily once (unreadable → empty, logged), appended on each notification (write failure logged, the key still counts for this session). Title uses `KeyNames.describe(KeyStroke)` notation for the key (build the stroke from the `KeyInput`; add a small helper in `KeyNames` if needed). Actions: `NotificationAction.of("Use as Editor shortcut", …)` appends `sethandler <notation> a:ide` (create folders/file; prefix `\n` when the file is non-empty and does not end with one; UTF-8) then `reloadVimrc`; on `IOException` posts `group.error("Could not update the vimrc", reason)` and does not reload. `NotificationAction.of("Configure…", …)` creates the vimrc empty if missing and calls `openVimrc` (the Editor opens it in one of its own tabs).

- [ ] **Step 1: Write failing tests** in `ShortcutConflictsTest` (temp dir, a recording `NotificationGroup` — use the testkit's fake group or a small stub):
  - `firstTakeNotifiesOnce`: `taken(<C-f>, true)` twice → one notification titled `"Ctrl+F is a shortcut for both Vim and the Editor"` on Linux notation (inject the platform flag through `KeyNames.describe(stroke, mac)`), content `"It is now used by Vim, but you can change this."`, actions `["Use as Editor shortcut", "Configure…"]`; the file holds `<C-f>`.
  - `windowOnlyNamesJasper`: `taken(<C-t>, false)` → title ends `"for both Vim and Jasper"`.
  - `rememberedAcrossInstances`: a new `ShortcutConflicts` on the same file → `taken(<C-f>, true)` posts nothing.
  - `useAsEditorShortcutAppendsAndReloads`: vimrc missing → running the action creates it with `sethandler <C-f> a:ide\n` and the reload runnable ran once; vimrc `"set hlsearch"` (no final break) → `"set hlsearch\nsethandler <C-f> a:ide\n"`.
  - `unwritableVimrcPostsAnError`: vimrc path under a regular file → error notification "Could not update the vimrc", reload not run.
  - `unreadableConflictFileIsEmpty`: the conflict path is a directory → `taken` still notifies.
  - `configureCreatesAndOpens`: action creates an empty vimrc and passes its path to `openVimrc`.
- [ ] **Step 2: Routing tests** (vim package): with Vim on and a recording `onConflict`, Normal mode, Linux: Ctrl+F (bound in the text area's map to a recording action) is taken by Vim (scrolls, action not run) and reports `(<C-f>, true)`; `j` reports nothing; after `sethandler <C-f> n:vim` nothing is reported; with the command line open (`:`) Ctrl+F reports nothing; a key bound only in a parent `JRootPane`'s `WHEN_IN_FOCUSED_WINDOW` map reports `false`.
- [ ] **Step 3: Run** — expect FAIL.
- [ ] **Step 4: Implement** `KeyHandlers.has`, `VimEditor.ownsByDefault`, `VimTextArea.onConflict` + detection, `ShortcutConflicts`, and the wiring in `EditorWindow` (one instance per `EditorWindow`; `openVimrc` opens the path as an Editor tab the way `:e` / Open do).
- [ ] **Step 5: Run** `./gradlew :jasper-plugin-editor:test` — expect PASS.
- [ ] **Step 6: Docs** — "Which keys Vim takes": the notification, its two actions, once per key, what `shortcut-conflicts` records; "The vimrc": Use as Editor shortcut appends a `sethandler` line.
- [ ] **Step 7: Commit** `Editor: a Vim key that is also a shortcut posts IdeaVim's conflict notification`.

### Task 4: SDK — status items with an icon, dimmed state and menu; plugins open their Settings page

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentStatus.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/settings/PluginSettings.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java`
- Modify: testkit `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeContent.java`, `FakePluginHost.java`, the fake settings class, `contract/PluginContractTest.java`, `FakeUiTest.java`
- Modify app: `jasper-app/src/main/java/dev/jasper/app/contributions/ContentStatus.java`, `plugins/HostedUi.java` (~631, map icon via `icon(name)` and wrap every menu `run` in `containment.run`), `workspace/WindowStatusBar.java` (`setContent` ~103), `plugins/DeclaredSettings.java` + the `PluginRuntime`/`JasperApplication.openSettings(owner, PluginRuntime.settingsPageId(id))` path
- Tests: app tests beside `WindowStatusBar` and `DeclaredSettings`
- Regenerate: `docs/sdk-reference.html`; docs: `docs/plugin-authoring.md` (~590 `setStatus` bullet)

**Interfaces:**
- `public record ContentStatus(String text, String tooltip, Runnable run, String icon, boolean dimmed, List<ContentStatus.MenuEntry> menu)` with the existing 3-argument constructor kept as the plain-text form (`icon ""`, not dimmed, no menu). `icon`: a registered icon name or `""`; `menu` copied; non-empty `menu` means a click opens it anchored on the item and `run` is not used. Javadoc `@since 0.21.0` on the new components.
- `public record MenuEntry(String label, Runnable run, boolean checkable, boolean checked, boolean separator)` nested in `ContentStatus`, with `static MenuEntry item(String label, Runnable run)`, `static MenuEntry check(String label, boolean checked, Runnable run)`, `static MenuEntry separator()`; validates a visible label unless separator (`ModelChecks.label`), `run` non-null unless separator, `checked` only when `checkable`.
- `PluginSettings.show()`, abstract (only the app and the testkit implement it): "Opens Settings at this plugin's page over the active window. UI thread." Throws `IllegalStateException` off the UI thread or after the context closed.App: opens `PluginRuntime.settingsPageId(pluginId)` through the application's `openSettings(null, page)`. Testkit fake: counts calls (`int shown()` on the fake settings, exposed through `FakePluginHost`).
- App `WindowStatusBar`: an item with an icon shows it before the text (icon alone when text is empty); dimmed paints its icon/text with the disabled/muted role the kit already uses for disabled status items; a menu item opens the kit's themed popup menu (the same popup builder the kit uses for `Nodes.MenuButton` menus), check items as checkbox menu items, separators as separators, below/above the item as the popup's placement rule decides. Equality: `contributions.ContentStatus` gains the same fields so `items.equals(contentStatus)` still skips rebuilds.

- [ ] **Step 1: Failing SDK tests** (`UiValuesTest`): `ContentStatus` 3-arg form has `icon ""`, `dimmed false`, empty menu; `MenuEntry.check("Enabled", true, r)` is checkable+checked; `MenuEntry.item(" ", r)` throws; `separator()` has no run; the menu list is copied (mutating the source does not change the record).
- [ ] **Step 2: Failing testkit/contract tests**: `FakePluginHost.contentStatus(tab)` returns the icon/menu given; `settings().show()` increments the fake's count; the contract suite calls `show()` on the UI thread and expects no exception on both runtimes, and off the UI thread `IllegalStateException`.
- [ ] **Step 3: Failing app tests**: `WindowStatusBar.setContent` with an item `("NORMAL","Vim mode",null,"dev.jasper.test:v",false,[check("Enabled",true,r), separator(), item("Settings…",s)])` → the button has an icon and text `NORMAL`; clicking it shows a popup with 3 components (a checked `JCheckBoxMenuItem` "Enabled", a separator, an item "Settings…"); choosing "Settings…" runs `s`; a dimmed item's foreground is the muted/disabled colour. `HostedUi.setStatus` wraps menu runs in containment (a throwing run becomes an incident, not an exception). `DeclaredSettings.show()` calls the open-settings hook with `settingsPageId(id)`.
- [ ] **Step 4: Run** `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests '*StatusBar*' --tests '*DeclaredSettings*' --tests '*HostedUi*'` — expect FAIL.
- [ ] **Step 5: Implement** SDK, testkit, app.
- [ ] **Step 6: Regenerate** `./gradlew sdkReference`; update `docs/plugin-authoring.md`'s `setStatus` bullet (icon, dimmed, menu) and add `PluginSettings.show()` where the guide lists settings calls; link anchors as AGENTS.md describes.
- [ ] **Step 7: Run** `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:check`, then `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip` — expect BUILD SUCCESSFUL.
- [ ] **Step 8: Commit** `SDK: content status items carry an icon, a dimmed state and a menu; plugins open their Settings page`.

### Task 5: The Vim status widget

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/StatusLine.java`, `EditorWindow.java` (`refreshStatus` ~1229, constructor ~193, Vim listener per tab), `EditorPlugin.java` (register the icon), `EditorEnvironment.java` if `settings().show()` must be passed in
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Mode.java` (a `widgetText()` or a mapping in `StatusLine`)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/StatusLineTest.java`, `EditorWindowTest.java`
- Docs: `docs/editor.md` Status bar table (`Vim: On/Off` row), "Turning Vim mode on", "The vimrc"

**Interfaces:**
- Consumes Task 4's `ContentStatus(text, tooltip, run, icon, dimmed, menu)`, `ContentStatus.MenuEntry`, `PluginSettings.show()`.
- Icon `dev.jasper.editor:vim`: an `IconAsset` registered once in `EditorPlugin.start` with `context.kit().registerIcon`, path data a "V" on the 16-grid, e.g. `"M 3 3 L 8 13 L 13 3"` (validate with `IconPaths.validate`; adjust if the validator wants a different form).
- `StatusLine`: the last item becomes the widget: text = mode word (Global Constraints) when Vim is on, `""` when off; tooltip `"Vim mode"`; icon `dev.jasper.editor:vim`; dimmed when off; menu `[check("Enabled", vimOn, toggleVim), item("Open vimrc", openVimrc), item("Reload vimrc", reloadVimrc), separator(), item("Settings…", showSettings)]`. `StatusLine`'s constructor gains those runnables; `display` takes the selected tab's `Mode` (from `tab.area().vim().map(VimEditor::mode)`).
- `EditorWindow.openVimrc()`: on a worker, create the vimrc empty (with folders) if missing, then `context.platform().openInEditor(file)`; a creation failure posts "Could not open the vimrc" with the reason.
- `refreshStatus` runs when a tab's Vim mode changes (`VimEditor.addListener`), not only on caret moves.

- [ ] **Step 1: Failing tests:**
  - `StatusLineTest.vimWidgetShowsTheMode`: for each `Mode`, the last item's text is its mode word; tooltip `"Vim mode"`; icon `dev.jasper.editor:vim`; not dimmed.
  - `StatusLineTest.vimOffIsADimmedIcon`: text `""`, dimmed, menu's first entry is checkable and unchecked.
  - `StatusLineTest.menuRunsItsCommands`: running each entry calls the matching runnable; labels exactly `Enabled`, `Open vimrc`, `Reload vimrc`, separator, `Settings…`.
  - `EditorWindowTest.widgetFollowsTheMode`: with the testkit host, an Editor tab with Vim on, `type("v")` → the tab's content status ends with text `VISUAL`; `<Esc>` → `NORMAL`.
  - `EditorWindowTest.openVimrcCreatesTheFile`: running Open vimrc creates `data/vimrc` and the fake platform recorded `openInEditor` with it.
  - `EditorWindowTest.settingsOpensTheEditorPage`: running Settings… → fake settings `shown()` is 1.
  - The old `Vim: On`/`Vim: Off` assertions in `StatusLineTest`/`EditorWindowTest` are replaced.
- [ ] **Step 2: Run** `./gradlew :jasper-plugin-editor:test --tests '*StatusLineTest' --tests '*EditorWindowTest'` — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :jasper-plugin-editor:check` — expect PASS (includes `PluginPresentationCheck` via `verifyPluginArchitecture`; if not, run `./gradlew verifyPluginArchitecture`).
- [ ] **Step 5: Docs** — Status bar table row: `V` icon and the mode (`NORMAL`, …), dimmed when Vim is off | click opens a menu: Enabled, Open vimrc, Reload vimrc, Settings…; mention Open vimrc where the vimrc section names Reload vimrc.
- [ ] **Step 6: Commit** `Editor: a Vim status widget with the mode and a menu replaces Vim: On/Off`.

### Task 6: Whole-branch check and records

**Files:** `docs/STATUS.md`, this plan's status banner.

- [ ] **Step 1:** Run the AGENTS.md source-hygiene script over changed Java files — expect no output.
- [ ] **Step 2:** Run `./gradlew check` in the background (long) — expect BUILD SUCCESSFUL; then the external plugin check from Task 4 Step 7 again.
- [ ] **Step 3:** Add a `docs/STATUS.md` entry (what landed, GUI checks pending: Visual Cmd/Ctrl+C/X/V on macOS, Windows, Linux; the conflict notification and both actions; Ctrl+Shift+V paste list in Normal mode; Ctrl+Shift+W in Insert mode; the widget's look, dimmed state and menu in light and dark themes). Mark the plan banner done, noting deviations.
- [ ] **Step 4: Commit** `Docs: status for Vim's IdeaVim keys and widget`.
