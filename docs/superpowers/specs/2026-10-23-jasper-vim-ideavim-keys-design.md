# Vim mode: IdeaVim's clipboard, shortcut conflicts, Ctrl+Shift keys and status widget

Status: approved in conversation with the user on 2026-10-23 (pieces and behaviour below); calls marked
*(builder's call)* or *(my call)* were not asked about.

## Why

The user, in the Editor's Vim mode, cannot copy with keyboard shortcuts and falls back to right-click Paste. Measured
headless on `main` (5db3524b4):

- On macOS Cmd+V reaches paste in every mode; Cmd+C reaches RSyntaxTextArea's `CopyAsStyledTextAction`, but Vim's
  Visual area is not the text area's Swing selection (after `vl`, `getSelectionStart() == getSelectionEnd()`), so
  nothing is copied. The right-click Copy and the Edit menu's Copy fail the same way.
- On Windows and Linux Ctrl+C/Ctrl+V are Vim keys, and Ctrl+Shift+C/V are read as Ctrl+C/V (documented,
  `docs/editor.md` "Which keys Vim takes").

The user asked Jasper to behave as IntelliJ's IdeaVim does, and to add IntelliJ's Vim status bar widget. Jasper is an
MVP: change behaviour outright, no compatibility shims or migration notes.

## Goals

1. Copy, Cut and Paste act on Vim's Visual area.
2. IdeaVim's one-time shortcut-conflict notification.
3. Ctrl+Shift+letter is its own key, not Ctrl+letter.
4. A Vim status bar widget with a menu, replacing the `Vim: On` / `Vim: Off` item.

## Non-goals

- The Windows report that yanks miss the clipboard with `set clipboard=unnamed`: the registers work; it is most likely
  the vimrc's name or location there. Not part of this work.
- IntelliJ's Settings table of shortcut conflicts, and its widget's **Disable…** (turns off the IdeaVim plugin; Jasper
  has no separate Vim plugin).
- No animations (project rule).

## 1. Copy, Cut and Paste on the Visual area

While Vim is in Visual mode (`v`, `V`, `<C-v>`), the Swing-level Copy, Cut and Paste — whatever invokes them: Cmd+C/X/V
on macOS, the right-click menu, the Edit menu's items, the tab menu, the command palette's `copy`/`paste` commands,
and Ctrl+C/X/V on Windows and Linux once a `sethandler` entry gives them to the Editor — act on the Visual area:

- **Copy** puts the Visual text on the system clipboard and stays in Visual mode with the area unchanged.
- **Cut** puts it on the clipboard, deletes it (one undo step) and returns to Normal mode, the cursor where Vim's `d`
  would leave it.
- **Paste** replaces the Visual text with the clipboard's text (one undo step) and returns to Normal mode, the
  cursor at the end of the pasted text, as a plain IDE paste over a selection leaves it *(my call)*. Characterwise, the
  area's span is replaced. Linewise, the lines' text is replaced and the last line's break is kept, so the next line
  never joins the paste. Blockwise, the block is deleted and the text inserted once at its top-left corner. None of this
  goes through Vim's `p`, which would overwrite the unnamed register.

They use the system clipboard only, as IdeaVim's IDE actions do; Vim's registers are untouched (unless
`clipboard=unnamed`/`unnamedplus` already makes `"*`/`"+` the clipboard, which it is anyway). Linewise Visual copies
whole lines with their final line break; blockwise Visual copies the block's rows joined by line breaks, as `y` would
put them in a register *(builder: reuse the yank text the Visual operator computes)*. Outside Visual mode the commands
behave as today.

*(builder's call)* The likely seam is `VimTextArea` overriding `copy()`, `cut()`, `paste()` and
`copyAsStyledText()` (RSyntaxTextArea's actions call these) and delegating to `VimEditor` while it is in a Visual mode;
check `EditorTab.paste`/`copy` and the context menu use the same methods.

## 2. The shortcut-conflict notification

The first time Vim takes a key that is also bound outside Vim — in the text area's input map (an Editor command or one
of RSyntaxTextArea's keys), an ancestor's `WHEN_ANCESTOR_OF_FOCUSED_COMPONENT` map, or the window
(`WHEN_IN_FOCUSED_WINDOW` bindings and menu accelerators) — and the key has no `sethandler` entry for the current mode,
Vim still runs the key, and the Editor posts one notification in its default group:

- Title: "**Ctrl+F is a shortcut for both Vim and the Editor**" (the key in the platform's notation, as the Editor's
  shortcut tables write it; "and Jasper" instead of "and the Editor" when only the window binds it *(my call)*).
- Content: "It is now used by Vim, but you can change this."
- **Use as Editor shortcut**: appends `sethandler <C-f> a:ide` (the key in Vim notation, `<D-…>` for Cmd) as a new last
  line of `<home>/plugins/dev.jasper.editor/data/vimrc`, creating the file and its folders if missing and adding a line
  break first when the file does not end with one; then reloads the vimrc as **Reload vimrc** does. A write failure
  posts an error notification "Could not update the vimrc" with the reason. The action expires the notification.
- **Configure…**: opens the vimrc in an Editor tab (creating it empty if missing), where `sethandler` lines live.

Each key notifies once, ever: the Editor records the keys it has notified about, one Vim-notation key per line, in
`<home>/plugins/dev.jasper.editor/data/shortcut-conflicts` *(file name my call)*, read at start and appended on each
notification. A key with any `sethandler` entry never notifies. A key that only starts or continues a typed command or
mapping counts the same as one Vim runs at once. Keys Vim takes while the strip reads a pattern or command line do not
notify *(my call: there Vim takes every key)*.

Typical keys: on Windows and Linux Ctrl+C, Ctrl+V, Ctrl+F, Ctrl+A, Ctrl+Z…; on macOS only Cmd keys that start a
mapping.

## 3. Ctrl+Shift+letter is its own key

`KeyInput.normalize` stops folding Shift into Ctrl for letters: Ctrl+Shift+V is `<C-S-v>` *(builder: pick the
canonical spelling Vim/IdeaVim notation uses and make `parse` and `toString` agree; `<C-V>` in a vimrc stays
`<C-v>`, as in Vim)*. Vim has no meaning for it unless a mapping or `sethandler` names it, so it declines it and it goes
on to the Editor and the window:

- In Normal and Visual mode a declined key bound to one of RSyntaxTextArea's text-area keys is held back today. The
  clipboard-history paste (Ctrl+Shift+V, Cmd+Shift+V on macOS: "Paste from a list of the text last copied or cut") is
  allowed in Normal and Visual mode, as IdeaVim's Paste from History is (in Visual mode it replaces the area, as Paste
  does in piece 1 *(my call)*). Other held-back keys stay held back.
- Other Ctrl+Shift keys reach the window: on Windows and Linux Ctrl+Shift+W closes the pane even in Insert mode,
  Ctrl+Shift+G is the window's Find Next, Ctrl+Shift+T a new tab, and so on. The user confirmed this change.

`docs/editor.md` (the "On Linux and Windows several of the Editor's and the window's shortcuts are Vim keys…" bullet,
about lines 716-721) is rewritten to match; `docs/editor-vim-reference.md` is regenerated if its key tables change.

## 4. The Vim status widget

It replaces the `Vim: On` / `Vim: Off` item and shows whenever an Editor file's pane is active, on every platform:

- A "V" icon (a monochrome 16-grid `IconAsset` the Editor registers, `dev.jasper.editor:vim`) followed by the mode in
  capitals: `NORMAL` (also while an operator waits), `INSERT`, `REPLACE`, `(INSERT)` for `<C-o>` from Insert mode,
  `VISUAL`, `V-LINE`, `V-BLOCK`, `COMMAND` while the strip reads a command line or pattern *(mode words my call,
  following IdeaVim)*. With Vim off, the icon alone, dimmed. Tooltip "Vim mode". It updates as the mode changes.
- A click opens a menu (the app's own popup, styled by the theme):
  - **Enabled**, checked while Vim is on: toggles Vim mode in every tab (today's `toggleVim`, including its save-failure
    dialog).
  - **Open vimrc**: creates the vimrc empty if missing, then opens it with `Platform.openInEditor` (Jasper's default
    editor).
  - **Reload vimrc**: as the existing command.
  - separator, **Settings…**: opens Settings at the Editor's page.

The Vim bar under the text keeps `-- INSERT --` and the rest unchanged.

### SDK additions *(my call on the shape)*

- `ContentStatus` gains an icon name (blank for none), a dimmed flag and a menu: a list of items, each a label, a run
  action, and whether it is checkable and checked, or a separator. A non-empty menu makes a click open the menu
  anchored on the item instead of running `run`. Existing constructors keep working for other callers within this
  change (no external compatibility needed beyond what the build uses).
- `PluginSettings` (or the `Platform`, builder's call) gains a way to open Settings at the plugin's own page, UI thread.
- The testkit fake records both; the contract suite covers them. They join SDK 0.21.0, which is unreleased, as
  `Appearance.editorSchemes()` did in a53068710 *(my call)*; plugin ranges unchanged. `./gradlew sdkReference` and the
  external plugin check follow AGENTS.md.
- The app renders the icon, dimmed state and popup in `WindowStatusBar`; `PluginPresentationCheck` keeps all of this
  out of the plugin.

## Tests (headless)

- Visual Copy/Cut/Paste through `VimTextArea`'s methods and through the bound actions, with an injected clipboard
  (`SystemClipboard`): char-, line- and blockwise; stays/leaves Visual; one undo step.
- `KeyInput`: Ctrl+Shift+V parses, prints and normalizes as its own key; `<C-V>` still `<C-v>`.
- `VimTextArea`: Ctrl+Shift+V in Normal mode reaches the clipboard-history action; Ctrl+Shift+W reaches an ancestor
  binding in Insert mode.
- Conflict detection: notifies once per key across a restart (file), not for a `sethandler` key, not for unbound
  keys; "Use as Editor shortcut" appends the line (missing file, file without final newline) and reloads.
- Status widget items for each mode and Vim off; menu items run the right commands; SDK fake/contract tests.

## Docs

`docs/editor.md`: the Status bar table row, "Which keys Vim takes", the vimrc section (the conflict file and
notification), Copy/Cut/Paste in Visual mode; SDK Javadoc → `docs/sdk-reference.html`; `docs/plugin-authoring.md` or
SDK architecture where status items are described; `docs/STATUS.md` entry.

## GUI checks for the user (not run by the agent)

macOS, Windows, Linux: Cmd/Ctrl+C in Visual mode; the conflict notification and both actions; Ctrl+Shift+V paste list
in Normal mode; Ctrl+Shift+W in Insert mode; the widget's look, dimmed state and menu in light and dark themes.
