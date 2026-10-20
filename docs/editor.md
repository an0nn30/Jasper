# Editor

The bundled Editor plugin (`dev.jasper.editor`) edits local and remote text files in workspace tabs,
with syntax highlighting from RSyntaxTextArea. The [design](superpowers/specs/2026-09-27-jasper-editor-plugin-design.md)
is the binding reference.

## Opening files

- **Open in Editor…** (Cmd+O on macOS, Ctrl+O elsewhere) in a terminal window's File menu or the
  palette, and **Open…** inside the Editor window.
- **Open File…** in a terminal window's File menu or the palette (no default shortcut) opens the file you choose in
  the [default editor](#default-editor), as a Cmd-click does.
- **New Editor File**, or **New File** (Cmd/Ctrl+N) inside the Editor window, for an untitled tab.
- Drop files from the desktop onto the Editor window. Folders are ignored.
- `jasper editor FILE[:LINE[:COLUMN]]...` from a terminal, once the
  [jasper command](configuration.md#the-jasper-command) is installed. The caret goes to the
  line and column. A file that does not exist yet opens as an empty tab and is created when you save it. A folder, or
  a file in a folder that does not exist, is reported and the other files still open. With `--wait` (`-w`) the command
  returns once every tab it opened has closed, so `git config --global core.editor "jasper editor --wait"` edits commit
  messages here; Ctrl-C stops waiting and leaves the tabs open.
- Cmd-click (Ctrl-click on Linux and Windows) a file path in a local terminal pane: `src/Foo.java:42`,
  `Foo.java:42:5`, `Foo.cs(42,5)`, `./notes.md`, `~/a.txt`, `/tmp/x.log`, on Windows also `C:\…` and backslash
  paths, or a `file://` link to this machine, as `ls --hyperlink` prints; a plain-text `file://` URL may end in
  `:LINE[:COLUMN]`, as Kotlin's compiler prints. A relative path is relative to the directory
  the shell last reported, else the one the pane started in. An existing file opens in the
  [default editor](#default-editor), at the line when the editor takes one; anything else does nothing. Where a file
  would open with the system's default app, a clicked file opens in the system's editor for its type (on macOS, the
  default text editor), else is shown in the file manager; where the desktop supports neither, as on most Linux
  desktops, the click opens nothing. On Windows, a path on a network share (`\\server\share\…`) opens nothing, except
  a WSL share in a WSL tab. A path with spaces opens only from a link. Inside a program that uses the mouse, use Ctrl+Shift-click on
  Linux and Windows. A pane whose shell reports a directory on another host, and a session a plugin provides, such as
  Remote's ssh, open nothing.

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
| `font_family` | first installed of JetBrains Mono, Menlo, Consolas, DejaVu Sans Mono, else Monospaced; IBM Plex Mono under the Enterprise look | editor font |
| `font_size` | 13 | finite fractional points, 6–72 |
| `font_fallback` | `[]` | ordered installed fallback families |
| `font_ligatures` | false | shaped font ligatures |
| `font_line_height` | 1.0 | natural line height multiplied by 0.5–3.0 |
| `follow_terminal_font` | false | follow saved terminal typography across windows |
| `markdown_view_mode` | `"source"` | default for newly eligible Markdown tabs: `source`, `split`, `preview` |
| `tab_size` | 4 | columns, 1–16 |
| `soft_wrap` | false | wrapping for new tabs |
| `max_file_mib` | 10 | larger files are refused, 1–256 |
| `vim` | false | Vim mode (below); the Vim status item and Toggle Vim Mode change it |
| `recent_files_days` | 30 | Recent files not opened for this many days are forgotten, 0–365; 0 keeps none |
| `default_editor` | `"jasper"` | the [default editor](#default-editor): `jasper`, `system`, `custom` or an installed editor's id |
| `custom_command` | `""` | for `custom`: the command; the file's path is added at the end |
| `custom_in_terminal` | true | for `custom`: run it in a terminal tab |
| `keep_environment_editor` | true | when Jasper's own environment has `EDITOR` or `VISUAL`, its shells keep both |

A bad value is reported and its default used. Follow terminal font settings displays the saved terminal family,
size, fallback order, ligatures and line height and disables the independent controls. Turning it off restores the
Editor values, including look defaults. Temporary terminal zoom stays local to the terminal pane. Tight line heights
can overlap tall glyphs.

## Markdown preview

For Markdown, the document toolbar offers Source, Source and preview, and Preview. Split places the source on the left;
its divider ratio stays with the tab when it moves. Explicit mode choices save the default for future Markdown tabs;
existing tabs keep their own modes. The Synchronize scrolling toggle links corresponding source passages in both
directions and stays local to the tab. Changing the language away from Markdown releases preview work.

Preview renders unsaved text after a 250 ms pause. Source and preview share the same document session and undo history.
Find searches the pane last focused; preview search includes rendered code and excludes mapping markers. Replace and
Go to Line reveal Source in Split without changing the saved default. Tab/Shift+Tab select preview links and Enter
activates one. Save, Save As and close use the existing file and dirty-prompt flow.

Headings, lists, quotes, tables, task marks, strikethrough and syntax-colored fenced code use a native Swing view.
Raw HTML is escaped and comments are omitted. Body text follows the host UI family at the effective Editor size;
code follows Editor typography. Theme, selection, glass and line spacing update live. Long code and tables scroll
horizontally; PNG, JPEG and the first GIF frame scale down to fit.

Relative images and file links resolve through the open document's provider, preserving an SFTP connection's exact
route and login. Relative Markdown links reuse already-open dirty tabs; fragments navigate to headings. Web links
open through the host on activation. Web images require **Load external images** for that document and tab lifetime;
consent survives reload and moves and resets on Save As or close. Failed images retain their alt text and a reason,
with Retry available. Untitled documents must be saved before relative resources can resolve.

Preview limits are 2,097,152 UTF-16 units, 16 MiB of generated UTF-8 HTML and 50,000 mapped blocks. Exceeding a limit
shows a Source action. Each image is limited to 8 MiB encoded and 16 million pixels; four loads and 64 MiB of retained
plus in-flight decoded pixels are shared across the plugin. Hidden tabs cancel preview and image work. See
[verification](editor-verification.md#fonts-and-markdown-preview) for automated evidence and pending native checks.

## Default editor

**Default editor** on the Editor's page (Settings › Tools › Editor) chooses the editor for the files Jasper opens itself
(Open in Editor for `config.toml`, from the configuration banner and notices, for a plugin's settings file and for the
VM options file, and plugins' Open in Editor) and the `EDITOR` of Jasper's shells, which `git commit`,
`crontab -e` and `kubectl edit` use:

- **Jasper Editor**, the default: files open in a tab here. `EDITOR` is the `jasper` command's path followed by
  `editor --wait`.
- **An installed editor**, by name. Neovim, Vim, vi, nano, micro, Helix and Emacs open in a new terminal tab, at the
  line when one is given, in the window the file was opened from, else the active one; the tab closes when the editor
  exits cleanly. They run with Jasper's own `PATH`, so when Jasper starts from the Dock their `:!` commands may need
  full paths. Visual Studio Code, Cursor, Zed, Sublime Text, BBEdit, TextMate, IntelliJ IDEA, Notepad++ and Notepad
  start on their own, at the line when one is given (Notepad takes none). `EDITOR` is the editor's path, with `-nw`
  for Emacs and the app editors' wait options (`--wait`, `-w`, `-multiInst -nosession`).
- **System default app**: files open with your system's app for them, and Jasper sets no `EDITOR`.
- **Custom command**: `custom_command` with the file's path added, in a terminal tab, or on its own when
  `custom_in_terminal` is off. `/bin/sh` runs it with Jasper's `PATH` plus `/opt/homebrew/bin`, `/usr/local/bin` and
  `~/.local/bin`; on Windows, `cmd` runs it. `EDITOR` is the command as written. Until you enter one, files open with
  the system's default app.

The page lists the editors Jasper finds on its `PATH` and, except on Windows, in `/opt/homebrew/bin`,
`/usr/local/bin` and `~/.local/bin`, as the command-line tools inside the apps in `/Applications` and `~/Applications`
on macOS, and in Notepad++'s and Notepad's own folders on Windows; BBEdit and IntelliJ IDEA are found only through their
`bbedit` and `idea` commands in the folders listed first. It looks when the Editor starts and each time the page opens.
A saved editor it no longer finds shows "(not found)" and acts as System
default app. When Jasper itself was started with `EDITOR` or `VISUAL` set, for example from a terminal, its shells keep
both and Jasper sets neither until you turn off `keep_environment_editor`; then Jasper replaces `EDITOR`, and `VISUAL`
too when Jasper was started with one. Jasper sets `VISUAL` only then, so an export of `EDITOR` or `VISUAL` in a shell
startup file, such as `~/.zshrc`, still wins. The page shows what Jasper's environment has. When the chosen editor
cannot open a file (a terminal editor with no terminal window open, an app that does not start) or the Editor is off,
the file opens with the system's default app. An app editor or custom command started on its own that exits with an
error posts a notification naming its program.

## Vim mode

Vim mode edits the way IdeaVim and VSCodeVim do: Normal, Insert, Replace and Visual modes, counts, operators with
motions and text objects, registers, `.`, search, marks, and undo in Vim's steps. Turn it on with `vim = true` in the
settings, the **Vim** item in the status bar, or **Toggle Vim Mode**: every tab switches at once, and the text and its
undo history stay. The [supported keys](editor-vim-reference.md) are generated from the engine's command table, and
[differences from Vim](vim-divergences.md) lists every deliberate one; the rest is checked against Vim 9 by a
recorded corpus.

- The strip under the text shows the mode (`-- INSERT --`, `-- VISUAL --`), the partly typed command (`2d`) or the
  Visual area's size (`3`, `2x5`), and messages such as `E353: Nothing in register q`. A failing command flashes it;
  Jasper never beeps.
- `/` and `?` type their pattern in the strip, with Vim's command-line keys (`<C-r>` for a register, `<C-r><C-w>` for
  the word under the cursor, `<Up>` and `<Down>` for history). While you type, the match it would find is highlighted
  ('incsearch'); `<Esc>` puts everything back. Patterns are Vim's (`\v`, `\<`, `\zs`, `\{-}`, look-arounds such as
  `\(foo\)\@<=bar`, columns such as `\%>8c`, and the rest); the few items Jasper does not translate say so (`E869`).
- Visual mode (`v`, `V`, `<C-v>`) draws the area in the theme's selection colour. Dragging the mouse in Normal mode
  starts it.
- The caret is a block in Normal and Visual mode, a bar in Insert mode, and an underline after an operator and in
  Replace mode.
- **Which keys Vim takes.** ⌘ shortcuts on macOS always stay with the Editor. Otherwise Vim takes a key when it has
  a meaning for it in the current mode, and every key while the strip reads a pattern. In Insert and Replace mode the
  Editor gets the rest. In Normal mode (and after an operator, or `<C-o>` from Insert mode) a key Vim does not use
  never runs one of the text area's own editing actions: Shift+Tab and, on Linux and Windows, Ctrl+D or Alt+Up flash
  the Vim bar and change nothing. The Editor's commands (Save, Find, Close Tab), Jasper's window shortcuts (such as
  Ctrl+Shift+T on Linux and Windows) and keys a `sethandler` entry gives the Editor still work; a key nothing takes
  flashes the Vim bar. On Linux and Windows Ctrl+S still saves (Vim has no `<C-s>`), while `<C-r>` redoes in Normal
  mode, `<C-w>` deletes a word in Insert mode, and Tab (`<C-i>`) goes forward in the jumplist.
- `"+` is the clipboard and `"*` the selection (the clipboard where there is none).
- 'tabstop' and 'shiftwidth' start at `tab_size`, 'expandtab' follows the tab's Spaces or Tab setting, and
  'autoindent' is on. 'incsearch' and 'hlsearch' are on; `:noh`, or `<Esc>` in Normal mode,
  hides the highlight until the next search.
- `:` opens the command line in the strip, with the same keys as `/` and its own history. It runs Vim's Ex commands
  for editing, with Vim's ranges (`:3,5`, `:%`, `:'<,'>` after Visual mode, `:/pat/`, `:.,+3`): `:s` with every flag,
  including `c`, which asks in the strip; `:g` and `:v`; `:d`, `:y`, `:m`, `:t`, `:j`, `:>`, `:pu`, `:norm`, `:sort`,
  `:left`, `:right` and `:center`; `:marks`, `:reg`, `:jumps` and `:changes`; `:u`, `:noh` and `:set`. The
  [reference](editor-vim-reference.md#ex-commands) lists them. When a command line says several lines (`:reg`,
  `:g/pat/`) they stay above the strip until you type a key; `:` goes straight on to the next command line.
- The tabs are Vim's buffers. `:w` saves through the Editor's Save (`:w file` saves as the file), `:q` closes the tab
  (`:q!` without asking about changes), `:wq`, `:x` and `ZZ` save and close, `:e file` opens a file in a tab (a
  relative name starts from the current file's folder), `:r file` puts a file's lines below the cursor, `:ls` lists
  the tabs with their buffer numbers, and `:b N`, `:bn`, `:tabn`, `gt` and `gT` move between them. `<Tab>` completes
  command names, options after `:set`, file names and tab names. 'relativenumber' shows each line's distance from the
  cursor in the gutter.
- `<C-g>` shows the file, its line count and the cursor's position; `ga` the character's code.
- Scrolling (`<C-e>`, `<C-d>`, `<C-f>`, `zt`, `zz`), `H`, `M`, `L` and the screen-line motions (`gj`, `g0`, `g$`)
  work on a window as many lines high and characters wide as the text area shows. Scrolling with the mouse or the
  scroll bar keeps the cursor inside it, as in Vim. With Vim on, long lines wrap at any character, as Vim wraps them.
- The fold commands (`zo`, `zc`, `za`, `zR`, `zM`, `zj`, `[z`) work on the Editor's code folds. A closed fold counts as
  one line for `j`, `k` and scrolling, an operator takes it whole, and the motions Vim's 'foldopen' names (`l`, `w`,
  searches, marks, `%`, `{`, `u`) open the fold they land in.
- Macros: `qa` … `q` records the keys you type into register `a` (the strip shows `recording @a`), `@a` runs them,
  `5@a` five times, `@@` the last register again and `@:` the last command line; `qA` appends to `a`. A macro stops
  at the first command that fails, so one that ends with `@a` runs to the end of the file; `u` undoes everything a
  macro or mapping changed at once. Ctrl+C stops a macro or mapping that runs on.
- Mappings: `:nmap`, `:vmap`, `:xmap`, `:omap`, `:imap`, `:cmap`, `:map` and `:map!`, their `noremap` forms, and the
  `unmap` and `mapclear` forms, as in Vim (`:nnoremap <Space>w :w<CR>`); without a right-hand side they list the
  mappings. Keys that start a longer mapping wait 'timeoutlen' (1000 ms) for the rest, and the strip shows them.
  `<nowait>`, `<unique>`, `<Plug>`, `<Nop>` and `<Leader>` (`let mapleader`, a backslash by default) work as in Vim. A key that starts a
  mapping is Vim's, even a ⌘ key.
- The vimrc: `plugins/dev.jasper.editor/data/vimrc` under Jasper's home, in UTF-8, read the first time Vim turns on
  and again with **Reload vimrc** (in the ⋯ menu and the command palette), which first puts mappings, options and
  `sethandler` entries back to their defaults. It holds command lines, as a Vim or IdeaVim vimrc does: `set`,
  `let mapleader = ","`, the mapping commands, `sethandler`, `source ~/.vimrc`, and `if has('ide')` … `endif` blocks
  (true in Jasper, so IdeaVim's blocks apply). Lines Jasper cannot run are skipped: a notice says how many, and
  `:messages` lists each with its line and the reason.
- `sethandler <C-f> n:vim i:ide` decides who gets a key in each mode (`n i v x o c`, or `a` for all), as in IdeaVim.
- `<Action>(id)` in a mapping runs one of the Editor's commands (`:nnoremap <Space>w <Action>(save)`), in any mode;
  `:action id` runs one, and `:actionlist` lists the ids.
- Extensions, as IdeaVim has them: `set surround` (or the plugin's `Plug 'tpope/vim-surround'` line) in the vimrc
  switches one on, with its plugin's keys. surround: `ds"`, `cs"'`, `ysiw)`, `yss]`, Visual `S`, tags with `t`
  (`cst<em>`). commentary: `gcc`, `gc{motion}`, Visual `gc`, `gcu`, and `dgc` for a comment block, with the tab's
  language's comments. exchange: `cx{motion}` twice swaps two texts; `cxx` lines, Visual `X`, `cxc` to forget.
  ReplaceWithRegister (`set ReplaceWithRegister`): `gr{motion}`, `grr` and Visual `gr` put a register over text. Each
  extension's `<Plug>` names can be mapped as in Vim (`:nmap <Leader>s <Plug>Ysurround`).
- More extensions, by IdeaVim's names: `set vim-paragraph-motion` makes `{` and `}` stop at lines of white space too;
  `set textobj-entire` gives `ae` (the whole text) and `ie` (without the empty lines at either end); `set
  textobj-indent` gives `ii` (the lines at the cursor's indent and deeper; again in Visual mode, the enclosing
  block), `ai` (with the line above) and `aI` (with the lines above and below); `set argtextobj` gives `ia` (a
  function argument) and `aa` (with its comma); `set highlightedyank` draws the text a yank took for a second
  (`let g:highlightedyank_highlight_duration = 300` for less).
- `set matchit` (or `packadd matchit`) makes `%` go between the words of a group as the tab's language defines them:
  `if`, `else` and `fi` in shell scripts, a tag and its end tag in HTML and XML, `\begin` and `\end` in TeX, `#if`
  and `#endif` and `/*` and `*/` everywhere; `g%` goes the other way, `[%` and `]%` to the start and end of the group
  around the cursor, and Visual `a%` takes it. Matches in comments and strings are passed over.
- `set sneak` (opt-in, as it takes over `s` and `S`): `s` and two characters goes to the next place they stand together,
  `S` back; after an operator `z` and `Z` do the same (`dzab`); `;` and `,` repeat it, and after `f` or `t` they are
  Vim's again. A count keeps `s` to the columns near the cursor (`5sab`). The matches ahead are drawn until the
  cursor moves on. Map `f` and `t` to `<Plug>Sneak_f` and `<Plug>Sneak_t` for one-character sneaks that `;` repeats.
- An uppercase mark (`mA`) set in one tab takes you back to that tab (`'A`).

## For other plugins

The Editor exports `dev.jasper.editor.api`. Require `dev.jasper.editor`, look up `Editor` with
`services().find`, and call `open(Path)` for a local file or `open(EditorDocument)` for a file you
read and write yourself; `EditorDocument.write` documents the save contract a provider follows.

A plugin with a file source (SDK 0.10) calls `locationDocuments(sourceId, resolver)` (Editor 0.6): Open in Editor…
then offers the source's places in Jasper's file chooser and opens what the user picks there through the
`LocationResolver`, which also tells the Editor where one of its documents is so the chooser can start in its folder.
Remote uses this for its saved hosts.

Save As uses the same chooser in Save mode: it starts in the document's folder with its name filled in and can save to
any offered place, so a local file can be saved to a remote host and a remote file to this computer. A file that does
not exist yet is created by the first write.
