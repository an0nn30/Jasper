# Editor

The bundled Editor plugin (`dev.jasper.editor`) edits local and remote text files in tabs in one Editor window,
with syntax highlighting from RSyntaxTextArea. The [design](superpowers/specs/2026-09-27-jasper-editor-plugin-design.md)
is the binding reference.

## Opening files

- **Open in Editor…** (Cmd+O on macOS, Ctrl+O elsewhere) in a terminal window's File menu or the
  palette, and **Open…** inside the Editor window.
- **New Editor File**, or **New File** (Cmd/Ctrl+N) inside the Editor window, for an untitled tab.
- Drop files from the desktop onto the Editor window. Folders are ignored.

Opening a file that is already open selects its tab. Files larger than `max_file_mib` and binary
files (a NUL byte near the start) are refused with a message in the tab.

## Tabs and the window

Each tab shows the file's name, a ● when it has unsaved changes, and, when two open files share a
name, the parent folder's name. The ⋯ button at the right of the tab row lists every command with
its shortcut. The status line shows the caret's line and column, the language (click to choose
another), the encoding, the line endings (`Mixed → LF` until the first save converts them), the
indentation and Wrap (click to toggle soft wrap for the tab).

| Shortcut (macOS / others) | Command |
|---|---|
| ⌘S / Ctrl+S | Save |
| ⇧⌘S / Ctrl+Shift+S | Save As… (a local path) |
| ⌥⌘S / Ctrl+Alt+S | Save All |
| ⌘O / Ctrl+O | Open… |
| ⌘N / Ctrl+N | New File |
| ⌘W / Ctrl+W | Close Tab; with no tabs, close the window |
| ⌘F / Ctrl+F, ⌥⌘F / Ctrl+Alt+F | Find, Replace |
| ⌘G / Ctrl+G, ⇧⌘G / Ctrl+Shift+G | Next, previous match |
| ⌘L / Ctrl+L | Go to Line |
| ⌘/ / Ctrl+/ | Toggle Comment |
| ⇧⌘] / Ctrl+Shift+], ⇧⌘[ / Ctrl+Shift+[ | Next, previous tab |

Revert to Saved, Language… and Toggle Soft Wrap have no shortcut; they are in the ⋯ menu.

## Saving

Saving checks first whether the file changed since it was opened, and asks **Overwrite**,
**Reload** (discarding your edits) or **Cancel**; a deleted file asks **Save Anyway**. A save writes a
hidden sibling file and atomically replaces the original, keeping its permissions and any
symbolic link; for a hard-linked file, a folder you cannot write, or a different owner, it writes in
place instead. A read-only file is not replaced: the save fails with "permission denied" and the
file is left as it was. Text the file's encoding cannot hold offers **Convert to UTF-8 and Save**.

Closing a tab with unsaved changes asks **Save**, **Don't Save** or **Cancel**. Closing the Editor
window, or quitting or restarting Jasper, asks **Save All**, **Discard** or **Cancel** and waits for
saves already running.

## Outside changes

While the window is open the Editor checks open local files every 2 seconds. A tab without unsaved
changes reloads by itself; one with changes shows a banner offering **Reload** or **Keep Mine**, and
a deleted file's banner offers **Close** or **Keep**.

## Remote files

With Remote installed, double-click a file in the SFTP browser to edit it here (see
[Remote: editing remote files](remote.md#editing-remote-files)). A remote tab shows its host dimmed after the name,
saves back to the host with the same safe write, and asks before overwriting a file someone else changed. Remote files
are not watched for outside changes; Revert re-reads them. Save As writes a local copy.

## Languages

TOML, JSON, Python, shell (Bash) and YAML are detected from the file name, extension or a `#!` line,
as are the other languages RSyntaxTextArea knows by an unambiguous extension; the rest open as
plain text. TOML uses the Editor's own lexer.

## Settings

`plugins/dev.jasper.editor/dev.jasper.editor.toml` under Jasper's home, applied live:

| Key | Default | Meaning |
|---|---|---|
| `font_family` | first installed of JetBrains Mono, Menlo, Consolas, DejaVu Sans Mono, else Monospaced | editor font |
| `font_size` | 13 | points, 8–48 |
| `tab_size` | 4 | columns, 1–16 |
| `soft_wrap` | false | wrapping for new tabs |
| `max_file_mib` | 10 | larger files are refused, 1–256 |
| `vim` | false | Vim mode (below); the Vim status item and Toggle Vim Mode change it |

A bad value is reported and its default used.

## Vim mode

Vim mode edits the way IdeaVim and VSCodeVim do: Normal, Insert and Replace modes, counts, operators with motions
and text objects, registers, `.`, and undo in Vim's steps. Turn it on with `vim = true` in the settings, the **Vim**
item in the status bar, or **Toggle Vim Mode**: every tab switches at once, and the text and its undo history stay.
The [supported keys](editor-vim-reference.md) are generated from the engine's command table, and
[differences from Vim](vim-divergences.md) lists every deliberate one; the rest is checked against Vim 9 by a
recorded corpus.

- The strip under the text shows the mode (`-- INSERT --`), the partly typed command (`2d`), and messages such as
  `E353: Nothing in register q`. A failing command flashes it; Jasper never beeps.
- The caret is a block in Normal mode, a bar in Insert mode, and an underline after an operator and in Replace mode.
- **Which keys Vim takes.** ⌘ shortcuts on macOS always stay with the Editor. Otherwise Vim takes a key when it has
  a meaning for it in the current mode. In Insert and Replace mode the Editor gets the rest. In Normal mode (and
  after an operator, or `<C-o>` from Insert mode) the Editor gets only the keys of its own commands, such as Save,
  Find or Close Tab, and keys a `sethandler` entry gives it; any other key flashes the Vim bar and changes nothing,
  so Tab, Shift+Tab and, on Linux and Windows, Ctrl+D or Alt+Up do not edit the text. On Linux and Windows Ctrl+S
  still saves (Vim has no `<C-s>`), while `<C-r>` redoes in Normal mode and `<C-w>` deletes a word in Insert mode.
- `"+` is the clipboard and `"*` the selection (the clipboard where there is none).
- 'tabstop' and 'shiftwidth' start at `tab_size`, 'expandtab' follows the tab's Spaces or Tab setting, and
  'autoindent' is on.
- Not yet: Visual mode, search (`/`, `n`, `*`), marks, scrolling commands, the `:` command line, macros, a vimrc,
  and emulated plugins such as surround and commentary. They follow in later releases.

## For other plugins

The Editor exports `dev.jasper.editor.api`. Require `dev.jasper.editor`, look up `Editor` with
`services().find`, and call `open(Path)` for a local file or `open(EditorDocument)` for a file you
read and write yourself; `EditorDocument.write` documents the save contract a provider follows.
