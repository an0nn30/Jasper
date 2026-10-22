# Editor

The bundled Editor plugin (`dev.jasper.editor`) edits local and remote text files in workspace tabs and panes, beside
terminals: syntax highlighting, find and replace, a [Markdown preview](#markdown-preview), [fonts](#fonts) of its own or the
terminal's, saves that check for outside changes and replace the file atomically, recent files in the command palette,
and an optional Vim mode. It is also Jasper's [default editor](#default-editor):
the files Jasper opens itself go to it, or to another editor you choose, and it sets `EDITOR` for Jasper's shells.
Other plugins open files in it through its API: Remote for files on SFTP hosts, Project Index for the palette's
**Files** chip. Like every bundled plugin it can be turned off on the Plugins page ([Plugins](plugins.md#editor)).

## Opening files

- **Open in Editor…** (File menu and command palette; Cmd+O, or Ctrl+Shift+O on Linux and Windows) shows Jasper's
  [file chooser](workspace.md#the-file-chooser), titled "Open in Editor", where you can choose several files. From an
  Editor tab it starts in that file's folder, a remote file's on its host; from a terminal pane, where the pane is.
  Besides this computer it offers the places of plugins' file sources, such as Remote's saved hosts. **Open…** in an
  Editor tab's menu does the same.
- **New Editor Tab** (File menu and command palette), or **New File** (Cmd+N, or Ctrl+N on Linux and Windows) in an
  Editor tab, opens an empty file named "Untitled", "Untitled 2" and so on in a new tab. **Split Right with Editor**
  (**New Editor Pane** in the File menu) and **Split Down with Editor** put one in a new pane beside the focused pane,
  from the command palette or as **Editor Right** and **Editor Down** in the Split submenus of the Tab menu and of a
  terminal's or an editor's right-click menu. **New Editor Window** (File menu, command palette and the toolbar's New
  Editor Window button) opens one in a new child window. None of these has a default shortcut. A new file's first save
  asks where to save it.
- **Open File Right…** and **Open File Down…**, in the Split submenus (the command palette calls them **Split Right
  with Open File…** and **Split Down with Open File…**), and **Open in Editor Pane…** in the File menu, which does what
  Open File Right does, show the file chooser for one file and open it in a new pane beside the focused pane; the new
  pane takes the focus. A file that is open already shows in another [view](#views-of-one-file); cancelling opens
  nothing. None of these has a default shortcut.
- **Recent Files…** (command palette) reopens a file the Editor opened before; see [Recent files](#recent-files).
- **Show Editor** (command palette) selects the Editor tab you were last in, or opens an untitled tab when there is
  none.
- In the command palette's **Files** chip, from [Project Index](project-index.md#finding-files), Enter opens the file.
- In Remote's SFTP browser, Enter or a double click opens the file; see [Remote files](#remote-files).
- Drag files from the desktop onto an Editor tab's source text: each regular file opens in a tab, and folders are
  ignored.
- Cmd-click (Ctrl-click on Linux and Windows) a file path in a terminal pane, **File › Open File…**, and the files
  Jasper opens itself (`config.toml`, a plugin's settings file, the custom VM options file) go to the
  [default editor](#default-editor), which is this Editor unless you choose another. A click opens only an existing
  file, at the line the path names; [Links and file paths](terminal.md#links-and-file-paths) lists the forms Jasper
  recognises.
- `jasper editor FILE[:LINE[:COLUMN]]...`, the [jasper command](command-line.md#jasper-editor), opens each file in the
  window of the Jasper pane it ran in, with the caret on LINE and COLUMN. A file that does not exist yet, in a folder
  that does, opens as an empty tab that its first save creates. With `--wait` (`-w`) the command returns once the last
  view of each file it opened has closed, so `git config --global core.editor "jasper editor --wait"` edits commit
  messages here; Ctrl+C stops waiting and leaves the tabs open. With no file it does what **Show Editor** does.

A file opens in a new tab of the window it was opened from, otherwise of the main window. Opening a file that is already
open selects its tab, of several [views](#views-of-one-file) the one you were in last; `jasper editor` and a Cmd-click
also move the caret to the line. While a file loads, its tab shows "Loading *name*…" ("Loading *name* from *host*…" for
a remote file) with **Cancel**, which closes the tab. A file that cannot be opened shows **Could Not Open File** with
the reason; see [Large, binary and unreadable files](#large-binary-and-unreadable-files).

## Tabs

Each file is the item of a pane, or of several as [views](#views-of-one-file): a tab of its own, or a pane beside
others in a tab ([Plugin tabs](workspace.md#plugin-tabs)). A tab shows its active pane's file:

- the file's name, with ● before it while the file has unsaved changes (updated just after you stop typing);
- after the name, the parent folder's name when another open tab has the same name, and a remote file's host, joined
  by ` · `;
- a file icon, or a server icon for a remote file. Its tooltip is the file's full location.

Right-click the tab for **Move Tab to New Window**, **Move Tab Into** and **Close Tab**, then every
[command](#commands-and-shortcuts) with its shortcut, **Copy**, **Paste**, **Select All**, **Cut**, **Delete**,
**Undo** and **Redo**; a child window's ⋮ menu also has **Move to Main Window**. Moving a tab keeps its text, undo
history, unsaved changes and Markdown view.
Right-click the text for Undo, Redo, Cut, Copy, Paste, Delete, Select All and the **Folding** submenu (Toggle Current
Fold, Collapse All Comments, Collapse All Folds, Expand All Folds), followed by **Save**, **Find…**, **Go to Line…**,
**Toggle Comment**, **Revert to Saved**, **Split File Right** and **Split File Down**, then **Split** (Terminal Right,
Terminal Down, Editor Right, Editor Down, Open File Right…, Open File Down…, then each plugin's split items), which
opens the choice in a new pane beside the editor, **Move Pane to New Tab**, which gives the editor's pane a tab of its
own while its tab holds another pane, and **Move Pane to Tab**, which moves it into another tab of its window. This menu
names a tab whose active pane shows an Editor file and lists any other tab as "Tab 3"; the Tab menu names them all.

While an Editor tab is selected its own shortcuts take precedence over the window's: on macOS Cmd+N makes a new file
instead of a new terminal window. The Edit menu's **Copy**, **Paste**, **Find**, **Find Next** and **Find Previous**
act on the Editor tab; the File menu's **Close Pane** closes the file's pane, and **Close Tab** its whole tab.
**Next Tab**, **Previous Tab** and **Select Tab 1**–**9** count the window's tabs, whatever their panes hold.

When Jasper restores its windows, each file comes back in its tab and pane: a local file is read at once, and one
deleted since comes back as a failed read with **Retry**; a remote one, through Remote, waits until its tab is first
active or you press **Load**, and then reads only if signing in asks nothing (an open connection, or the SSH agent);
otherwise it says so, and **Retry** signs in. An untitled file that was never saved does not come back, nor does a
document whose key is too long to save. A restored file is not added to [Recent files](#recent-files).

## Views of one file

**Split File Right** and **Split File Down** show the file in another view, in a new pane to the right of its own or
below it: from the text's right-click menu, the tab's menu or the command palette, with no default shortcut (Settings ›
Keymap can give them one). **Open File Right…** and **Open File Down…** of a file that is already open put another view
of it beside the focused pane. A split view starts where its source view is, with its caret, selection and scroll
position, and one that Open File Right… or Down… opens starts where the file's view you were in last is; with Vim on,
its cursor is on that view's line, and its window scrolls to show the cursor when the new pane is shorter. In Vim mode
`:action split-file-right` and `:action split-file-down` run them.

A file's views show one text: an edit in one shows in the others at once, and Undo and Redo in any view undo and redo
the last edit made in any of them, the undoing view's caret going to the change while the others stay where they were.
An edit that adds or removes lines above another view leaves that view showing the same text, not the same pixels:
without Vim its first line stays on top; with Vim its cursor stays on its text in every mode but the command line, and
the window scrolls the least that shows it, keeping `scrolloff` lines around it. An insert at the start of a view's
first line, or at the start of the text, keeps that line on top; an edit that takes away its first line shows the text
after the deletion; and **Revert to Saved**, a reload and the undo of either leave each view on its line number. A view
that comes back from the Markdown preview shows the text it showed.

The ● marker, **Save**, **Save As…**, **Revert to Saved** and a reload act on the file from any view, and a reload keeps
each view's caret on its line. A remote file's sign-in prompt shows over the window the file was first opened in,
whichever view asked. Each view has its own caret, selection, scroll position, folds, Vim state and Markdown view; the
find bar belongs to the selected view and closes when you select another; the language and soft wrap are the file's.
Vim's marks `a` to `z`, `'.` and its change list are the file's too, as they are a Vim buffer's, and so are its
buffer-local [options](#vim-options); its jumplist, `''` and place in the change list are each view's, a split view
starting with its source's. Closing a view closes only that view, and the others keep the order you were last in them:
Jasper asks about unsaved changes when the file's last view closes, and a window that closes with one view of a file
another window still shows asks nothing. **Cancel** in one view of a file that is still loading closes that view, and
the file goes on loading for the others. **Open in Editor…** of an open file selects the view you were in last, and
Recent files lists the file once. When Jasper restores its windows every view comes back, and the file is read once for
all of them.

## Status bar

While an Editor tab is selected, the window's [status bar](workspace.md#the-status-bar) shows the file's location and
these items:

| Item | Shows | Click |
|---|---|---|
| `Ln 12, Col 5` | The caret's line and column; `Source Ln 12, Col 5` in a Markdown tab's Preview view | |
| Language, such as `Python` | The tab's language | Opens **Language…** |
| Encoding: `UTF-8`, `UTF-8 BOM`, `UTF-16LE BOM`, `UTF-16BE BOM`, `ISO-8859-1` | How the file is stored | |
| `LF`, `CRLF` or `CR` | The line endings saves write; `Mixed → LF` (or `CRLF`, `CR`) until a save makes mixed endings one kind | |
| `Spaces: 4` or `Tab: 4` | Whether the Tab key inserts spaces or a tab, and `tab_size` | |
| `Wrap: On` or `Wrap: Off` | Soft wrap in this tab | Toggles it |
| `Vim: On` or `Vim: Off` | [Vim mode](#vim-mode) | Toggles it, in every tab |

Before them, "Saving…" shows while a save runs, and for 3 seconds "Reloaded: changed on disk" after an
[outside change](#outside-changes) reloaded the file, "Could not reload: *reason*", or why a
[Markdown link](#links-in-the-preview) could not be followed.

## Commands and shortcuts

The Editor's commands are in the tab's right-click menu and take their shortcuts while an Editor tab is selected. In
Vim mode, `:action id` and `<Action>(id)` run them by the id in the last column.

| Command | macOS | Linux and Windows | Id |
|---|---|---|---|
| Save | Cmd+S | Ctrl+S | `save` |
| Save As… | Cmd+Shift+S | Ctrl+Shift+S | `save-as` |
| Save All | Cmd+Option+S | Ctrl+Alt+S | `save-all` |
| Open… | | | `open` |
| New File | Cmd+N | Ctrl+N | `new` |
| Close Pane | | | `close-pane` |
| Find… | Cmd+F | Ctrl+F | `find` |
| Replace… | Cmd+Option+F | Ctrl+Alt+F | `replace` |
| Find Next | Cmd+G | F3 | `find-next` |
| Find Previous | Cmd+Shift+G | Shift+F3 | `find-previous` |
| Go to Line… | Cmd+L | Ctrl+L | `go-to-line` |
| Toggle Comment | Cmd+/ | Ctrl+/ | `toggle-comment` |
| Markdown: Source | | | `markdown.source` |
| Markdown: Source and Preview | | | `markdown.split` |
| Markdown: Preview | | | `markdown.preview` |
| Revert to Saved | | | `revert` |
| Split File Right | | | `split-file-right` |
| Split File Down | | | `split-file-down` |
| Language… | | | `language` |
| Toggle Soft Wrap | | | `wrap` |
| Toggle Vim Mode | | | `toggle-vim` |
| Reload vimrc | | | `reload-vimrc` |
| Copy | Cmd+C | Ctrl+C | |
| Paste | Cmd+V | Ctrl+V | |

The three Markdown commands are available once a Markdown tab's text has loaded, and are dimmed in other tabs. In Vim
mode a mapping such as `:nnoremap <Leader>p <Action>(markdown.preview)` gives one a key.

The plugin's actions are in the command palette and can be given other shortcuts in Settings › Keymap or
`[keybindings]` ([Shortcuts](configuration.md#shortcuts)):

| Action | Id | macOS | Linux and Windows | Also in |
|---|---|---|---|---|
| Open in Editor… | `dev.jasper.editor.open` | Cmd+O | Ctrl+Shift+O | File menu |
| New Editor Tab | `dev.jasper.editor.new_editor_tab` | | | File menu |
| Split Right with Editor | `dev.jasper.editor.split_right_editor` | | | File menu (as New Editor Pane), the Split submenus |
| Split Down with Editor | `dev.jasper.editor.split_down_editor` | | | The Split submenus |
| Split Right with Open File… | `dev.jasper.editor.split_right_open-file` | | | The Split submenus (as Open File Right…) |
| Split Down with Open File… | `dev.jasper.editor.split_down_open-file` | | | The Split submenus (as Open File Down…) |
| Open in Editor Pane… | `dev.jasper.editor.open_in_pane` | | | File menu |
| New Editor Window | `dev.jasper.editor.new_window` | | | File menu, toolbar |
| Revert to Saved | `dev.jasper.editor.revert` | | | |
| Reload vimrc | `dev.jasper.editor.reload_vimrc` | | | |
| Split File Right | `dev.jasper.editor.split_file_right` | | | The text's right-click menu |
| Split File Down | `dev.jasper.editor.split_file_down` | | | The text's right-click menu |
| Recent Files… | `dev.jasper.editor.recent-files` | | | |
| Show Editor | `dev.jasper.editor.show` | | | |

The text area has its own editing keys:

| Action | macOS | Linux and Windows |
|---|---|---|
| Undo | Cmd+Z | Ctrl+Z |
| Redo | Cmd+Shift+Z or Cmd+Y | Ctrl+Y |
| Indent the selected lines | Tab | Tab |
| Unindent the line or the selected lines | Shift+Tab | Shift+Tab |
| Delete the line or the selected lines | Cmd+D | Ctrl+D |
| Join the line with the next | Cmd+J | Ctrl+J |
| Select the next or previous occurrence of the selected text | Cmd+K, Cmd+Shift+K | Ctrl+K, Ctrl+Shift+K |
| Go to the matching bracket | Cmd+[ | Ctrl+[ |
| Move the line or the selected lines up or down | Option+Up, Option+Down | Alt+Up, Alt+Down |
| Paste from a list of the text last copied or cut in Editor tabs | Cmd+Shift+V | Ctrl+Shift+V |

While the text has focus these keys take precedence over the window's shortcuts: on macOS Cmd+D and Cmd+J act in the text
instead of running Split Right and Snippets…, and on Linux and Windows Ctrl+Shift+V opens the list instead of running
Paste. Use the menus or the command palette for those.

## Find and replace

**Find…** opens the find bar at the bottom of the tab, with the selection as its query when it is one line. It searches
the text, or in a Markdown tab the pane that last had the focus: the source, or the rendered preview
([Finding in the preview](#finding-in-the-preview)). **Replace…** opens the bar with a second row for the replacement
and always searches the source; in the Preview view it first switches the tab to Source and preview. The bar has the
query field, **Match Case**, **Whole Word** and **Regular Expression** (Java's regular expressions) toggles, the match
count ("No matches", "1 match", "*n* matches", or "Invalid pattern"), **Previous Match**, **Next Match** and **Close**.
Every match is highlighted, and the search wraps around the end of the file. In the replace row, **Replace** replaces
the next match after the caret and **Replace All** replaces every one and reports "Replaced *n*". With Regular
Expression on, `$1`, `$2` and so on in the replacement stand for the pattern's groups, `\n` and `\t` for a new line and
a tab, and `\$` for a dollar sign.

| Key | In the find bar |
|---|---|
| Enter | Next match |
| Shift+Enter | Previous match |
| Escape | Close the bar and return to the searched pane |

The query and toggles are shared by every Editor tab. **Find Next** and **Find Previous** search for the query while the
bar is closed too. The bar closes when you select another tab, or when a Markdown view change hides the pane it
searches.

## Go to Line and Language

**Go to Line…** asks for a line from 1 to the file's line count, filled in with the caret's line. **Go** (or Enter)
puts the caret at the start of that line; another value shows "Enter a line from 1 to *n*" and keeps the dialog open.
Escape cancels. In a Markdown tab's Preview view it first switches the tab to Source and preview.

**Language…**, or a click on the language in the status bar, lists every language with Plain Text first. Type to
filter, Up and Down to move, Enter or a double click to choose, Escape to cancel. A language you choose stays with the
tab: reloading and Save As do not detect it again.

## Languages and syntax highlighting

The Editor highlights with RSyntaxTextArea's languages and its own TOML lexer. It chooses a language from the file's
name, then its extension, then a `#!` first line; anything else is Plain Text.

| Language | Detected from |
|---|---|
| TOML | `.toml`; `Cargo.lock`, `Pipfile`, `poetry.lock` |
| Shell (Bash) | `.sh`, `.bash`, `.zsh`; `.bashrc`, `.bash_profile`, `.profile`, `.zshrc`; `#!` naming `sh`, `bash`, `zsh`, `dash` or `ksh` |
| Python | `.py`, `.pyi`, `.pyw`; `#!` naming `python`, `python3`, `python3.12` and so on |
| JSON, JSON with Comments | `.json`; `.jsonc` |
| YAML | `.yml`, `.yaml` |
| Dockerfile, Makefile, .htaccess, Hosts | `Dockerfile`; `Makefile`, `GNUmakefile`; `.htaccess`; `hosts` |
| Go, Rust, Java, Kotlin, Groovy, Scala | `.go`; `.rs`; `.java`; `.kt`, `.kts`; `.groovy`, `.gradle`; `.scala` |
| JavaScript, TypeScript | `.js`, `.mjs`, `.cjs`; `.ts` |
| C, C++, C# | `.c`, `.h`; `.cpp`, `.cc`, `.cxx`, `.hpp`; `.cs` |
| HTML, XML, CSS, Less | `.html`, `.htm`; `.xml`, `.xsd`, `.svg`, `.plist`; `.css`; `.less` |
| SQL, Markdown, Ruby, Perl, PHP, Lua, Dart | `.sql`; `.md`, `.markdown`; `.rb`; `.pl`, `.pm`; `.php`; `.lua`; `.dart` |
| Protocol Buffers, Properties, INI, Windows Batch, CSV | `.proto`; `.properties`; `.ini`; `.bat`, `.cmd`; `.csv` |
| Tcl, Visual Basic, LaTeX, Clojure, DTD, JSP, Handlebars | `.tcl`; `.vb`; `.tex`; `.clj`; `.dtd`; `.jsp`; `.hbs` |

File names and extensions match in any case. `#!/usr/bin/env python3` counts as `python3`. The other languages
Language… lists, such as ActionScript, Assembly, D, Delphi, Fortran, Lisp and VHDL, are chosen by hand.

Every tab has line numbers, code folding with fold markers in the gutter, bracket matching, and a highlight on the
caret's line and on the other occurrences of the identifier under the caret. **Toggle Comment** comments or uncomments
the selected lines with the language's comment markers.

Typing also edits around what you type, in every tab:

- A quote that starts a string, typed outside a string or comment, also types the closing quote; typing that closing
  quote steps over it.
- A bracket (`(`, `[`, `{`) or quote typed with text selected surrounds the selection.
- Enter keeps the line's indent and adds a level after a line ending in `{` or `(` in a language that uses braces
  (`:` in Python). After a `{` that the text does not close yet, it also adds the closing `}` below.
- A `}` typed after nothing but white space takes the indent of the line with its `{`.
- `</` in XML closes the open tag.

With Vim mode on, Enter in Insert mode is Vim's `<CR>`, which only keeps the indent ('autoindent').

## Fonts

The Editor has font settings of its own, in the Font section of Settings › Tools › Editor and in its
[settings file](#settings): **Font** (`font_family`), **Font size** (`font_size`, 6 to 72 points, fractions allowed),
**Fallback fonts** (`font_fallback`), **Font ligatures** (`font_ligatures`) and **Line height** (`font_line_height`).
Each character is drawn in the first of the font and the fallback fonts, in their order, that has it, else in the Java
runtime's default font; a fallback font that is not installed is skipped. **Font ligatures** draws the character
sequences a font joins, such as `->` or `!=`, as one glyph. **Line height** multiplies the fonts' own line height, from
0.5 to 3; below 1, tall glyphs can overlap the lines beside them. The line numbers use the same font and line height. A
change applies to every tab at once.

**Follow terminal font** (`follow_terminal_font`) shows every Editor tab in the terminal's saved font instead: the
family, size, fallback fonts, ligatures and line height of [`[terminal.font]`](configuration.md#terminal-font), following
each change to them at once. **Increase Font Size**, **Decrease Font Size** and **Reset Font Size** change a terminal
pane for the session only and do not reach the Editor. While it is on, the Font section shows the terminal's values in
place of the Editor's, and they cannot be edited there; the Editor's own values stay saved and apply again when you turn
it off.

## Markdown preview

A tab whose language is Markdown (a `.md` or `.markdown` file, or Markdown chosen with **Language…**) can show its text
rendered beside or instead of the source. A bar above the text holds three buttons at its right, named by their
tooltips:

| Button | Shows |
|---|---|
| **Source** | The source |
| **Source and preview** | The source on the left and the preview on the right, with a divider you can drag |
| **Preview** | The preview |

In Source and preview a fourth button, **Synchronize scrolling**, is on in every tab to begin with: scrolling either
pane brings the other to the same passage. The tab menu's **Markdown: Source**, **Markdown: Source and Preview** and
**Markdown: Preview** do what the first three buttons do ([Commands and shortcuts](#commands-and-shortcuts)). Under a
look with IntelliJ's classic icons, such as [Enterprise](enterprise.md), the buttons draw IntelliJ's classic layout
icons; under the default look, its New UI ones.

Each tab keeps its own view, divider position and Synchronize scrolling, also when it moves to another window. Choosing
a view with a button or command also saves it as **Default view** (`markdown_view_mode`), the view a tab starts in the
first time it shows Markdown; tabs already open keep theirs. A choice that cannot be saved posts "Could not save the
Markdown default view". When **Language…** changes a tab to another language, the bar hides and the tab shows its
source; changing it back to Markdown brings back the tab's last view.

### What the preview shows

The preview renders CommonMark with GitHub's tables, strikethrough (`~~text~~`) and task lists (☑ and ☐): headings,
paragraphs, emphasis, block quotes, numbered and bulleted lists, inline code, code blocks, rules, links and images. HTML
in the text shows as text, tags included, and HTML comments are left out. A fenced code block whose info string starts
with a language the Editor highlights, by its name in **Language…** (such as `python` or `yaml`) or as `js`, `ts`, `py`,
`sh`, `bash`, `shell`, `c++` or `c#`, is coloured as the Editor colours that language; other code is plain. Code does
not wrap: the preview scrolls sideways when a code line or a table is wider than the pane.

Text is in the interface font's family at the Editor's font size, with larger headings; code is in the Editor's font,
with its fallback fonts and ligatures, and **Line height** spaces the preview's lines too. Colours come from the UI
theme ([Theme colours](#theme-colours)). A change of theme or font applies to the preview at once.

The preview cannot be edited. Copy copies the text selected in it, and **Select All** in the tab menu selects all of it
while it has the focus. In the Preview view, the commands that change or locate text (Paste, and **Cut**, **Delete**,
**Undo** and **Redo** in the tab menu, **Replace…**, **Go to Line…**, **Toggle Comment** and **Revert to Saved**) first
switch the tab to Source and preview, without changing Default view, and act on the source.

### Live preview

The preview shows the tab's text as it is, saved or not. It renders again 250 ms after the text last changed, whether
you typed or the file was reloaded; switching to a view with the preview, or back to the tab, renders it at once. Only
a preview that shows renders, beside a focused pane or not: one in a tab you leave stops rendering and loading images,
and renders again when its tab shows.

When the preview cannot be rendered, a line above the text gives the reason, with **Source**, which switches the tab to
Source as the button does, and **Retry**. A preview rendered earlier stays in place, and the line then begins "Preview
is out of date." A document of more than 2,097,152 characters (UTF-16 code units), or one whose rendering would pass
16 MiB or 50,000 blocks, is not previewed: the line says "This document is too large to preview", and the source stays
editable.

### Links in the preview

Click a link to follow it. Tab and Shift+Tab select the next and previous link, and leave the preview after the last
and before the first; Enter follows the selected link.

| Link | Followed by |
|---|---|
| `https://…` or `http://…`, with a host | Opening it in your browser |
| `#heading` | Going to that heading in this document |
| A relative path, such as `notes.md`, `../README.md` or `guide.md#setup` | Opening the file in an Editor tab in the same window, then going to the heading after `#`, if any. A file that is already open has its tab selected, unsaved changes and all. |

A heading's name is its text in lower case, with each run of spaces made one `-` and every character but letters,
digits, `-` and `_` left out: `## Getting started` is `#getting-started`. A second heading with the same name gets `-1`
added, the next `-2`, and a heading with none of those characters is `#section`. In a linked file that shows only its
source, the caret goes to the heading's line.

A relative path uses `/` and starts in the document's folder; percent escapes such as `%20` are decoded. A remote
file's links open on its host, over the same connection and login ([Remote files](#remote-files)). A link that cannot
be followed says why in the status bar for 3 seconds:

| Status bar | When |
|---|---|
| "Only absolute HTTP and HTTPS URLs are supported" | Any other scheme, such as `mailto:` or `file:`, or an HTTP(S) URL without a host or with a user name |
| "Invalid or unsupported destination" | An absolute path, a backslash, a query (`?`) or a bad percent escape |
| "Empty destination" | A link with no target, such as `[text]()` |
| "Save this document before opening relative links" | A relative link in an untitled tab |
| "This document cannot resolve relative links" | A relative link in a document another plugin opened without support for them |
| "Heading not found" | No heading has that name, or the linked file is not Markdown |
| "Could not navigate to heading" | The document with the heading could not be previewed, for example because it is too large |
| "Could not open link in browser", "Could not open linked document" | Opening it failed |

### Images in the preview

Images are PNG, JPEG or GIF (its first frame), drawn at their own size or scaled down to fit the pane's width. A
relative path loads the image as the document itself is read: from this computer, or for a remote file from its host
over the same connection; an untitled tab must be saved first. Reloading the file loads its relative images again.

Web images (`https://…` or `http://…`) load only once you click **Load external images** above the text. That allows
them for this document while its tab is open, including ones you add later; a reload and a move to another window keep
it, and **Save As…** ends it. Until an image loads, and when it fails, its place shows its alternative text and a
reason, such as "Load external images to display", "Image exceeds 8 MiB" or "Image server returned 404"; an image
without alternative text shows "Image" and its file name before the reason. **Retry images**, above the text while an
image has failed, loads the failed ones again.

| Limit | Value |
|---|---|
| One image's file | 8 MiB ("Image exceeds 8 MiB") |
| One image's size | 16 million pixels ("Image exceeds 16 million pixels") |
| Images loading at once, across every tab | 4 |
| Decoded images held, across every tab | 64 MiB ("Image memory budget exhausted") |
| A web image's request | 20 seconds ("Image request timed out"), at most 3 redirects, none from HTTPS to HTTP |

### Finding in the preview

While the preview has the focus, **Find…** searches its rendered text, code blocks included, with the same **Match
Case**, **Whole Word** and **Regular Expression** options. Every match is highlighted in the selection colour, Enter
and Shift+Enter select the next and previous one, and the count follows when the preview renders again. **Replace…**
works on the source ([Find and replace](#find-and-replace)).

## Encodings and line endings

A byte-order mark makes a file UTF-8, UTF-16LE or UTF-16BE; a file without one is read as UTF-8 when it is valid UTF-8,
else as ISO-8859-1, which reads every byte. Line endings become one kind while you edit, and the most common of LF,
CRLF and CR in the file is what saves write. A file with one kind of line ending, saved without edits, keeps exactly
the bytes it had, its byte-order mark included. New files are UTF-8 without a byte-order mark, with LF.

A file whose indented lines start with a tab more often than with a space, and every Makefile, indents with tabs
(`Tab: 4` in the status bar); others indent with spaces.

The encoding and line-ending items in the status bar cannot be clicked: a file keeps both. When you type a character
the file's encoding cannot store, saving asks "Line *n* of *name* has a character that *encoding* cannot store." with
**Convert to UTF-8 and Save**, which saves it as UTF-8 without a byte-order mark and with the same line endings, or
**Cancel**.

## Large, binary and unreadable files

A file larger than `max_file_mib` (10 MiB unless you change it, at most 256) is refused before it is read, and so is one
that grows past the limit while it is read. A file without a byte-order mark that has a NUL byte in its first
8 KiB is treated as binary and refused. The tab then shows **Could Not Open File** with one of these reasons, **Close**
and, except for a binary file, **Retry**:

| Reason | When |
|---|---|
| "*name* is *size*; the limit is *limit* (max_file_mib)." | Too large. Raise `max_file_mib`, then **Retry**. |
| "*name* looks like a binary file." | A NUL byte in the first 8 KiB |
| "*name* does not exist." | Gone before it was read |
| "Permission denied: *name*." | Not readable |
| "*name*: Is a folder." or "*name*: Not a regular file." | A folder, or a device or pipe |
| "*name*: *reason*." | Any other read error, or a remote host's |

## Saving

**Save** first checks whether the file changed since the Editor read it, by its modification time and size:

| Dialog | Message | Choices |
|---|---|---|
| File changed | "*name* changed on disk since you opened it." | **Overwrite**, **Reload** (discards your edits), **Cancel** |
| File deleted | "*name* no longer exists on disk." | **Save Anyway**, **Cancel** |
| Save failed | "Could not save *name*: *reason*." | **OK** |

For a remote file the messages say "on *host*" instead of "on disk". In the first two Enter presses **Cancel**; Escape
answers **Cancel** in every Editor dialog that has one.

A local save follows symbolic links (up to 40) and saves the file they lead to, so a link stays a link. It writes the
text to a hidden file beside the target, `.<name>.jasper-<random>.tmp`, flushes it to the disk, gives it the target's
permissions and renames it over the target in one step, so the file is never half written. On macOS and Linux only you
can read the hidden file until it takes the target's permissions. The save writes the file in place instead, keeping
its owner, group and links, when the file has more than one hard link, the folder does not let Jasper create the hidden
file, the hidden file would get another owner or group, or the file system cannot rename atomically. A read-only file
is not replaced: the save fails with "permission denied" and leaves the file as it was. A folder is refused. A file
that does not exist is created.

**Save As…** shows the file chooser in Save mode, titled "Save As", in the file's folder with its name filled in. It
offers this computer and plugins' file sources, so a local file can be saved to a remote host and a remote file to this
computer; the chooser asks before replacing a file. A file that is open in another tab is refused: "*location* is open
in another tab." Afterwards the tab edits the new file, and its language is detected again unless you chose one.
**Save** on an untitled tab is Save As; a tab opened for a file that does not exist yet saves straight to its path.

**Save All** saves every Editor tab with unsaved changes, in every window, one after another, so each dialog comes on
its own.

Closing the last [view](#views-of-one-file) of a file with unsaved changes asks "Save changes to *name*?" with **Save**
(Enter), **Don't Save** and **Cancel**. A tab whose save is running closes once the save succeeds. Closing a window that
holds Editor tabs, quitting and restarting first wait for saves already running ("Saving 2 files…", with **Cancel**),
then list the files with unsaved changes (up to ten, then "and *n* more") and ask **Save All** (Enter), **Discard** or
**Cancel**.

**Revert to Saved** reads the file again. With unsaved changes it first asks "Discard your changes to *name*?" with
**Discard Changes** and **Cancel** (Enter).

## Outside changes

Every 2 seconds the Editor checks the modification time and size of each open local file.

- A tab without unsaved changes reloads by itself, keeping the caret's line, and the status bar says "Reloaded:
  changed on disk". When that read fails, the tab keeps its text and shows the banner below, with "Could not reload:
  *reason*" in the status bar.
- A tab with unsaved changes shows the banner "*name* changed on disk." with **Reload**, which reads the file again and
  drops your edits, and **Keep Mine**, after which the next save overwrites the file without asking.
- A deleted file's tab shows "*name* was deleted." with **Close** and **Keep**. Keep marks the tab unsaved; its next
  save creates the file again.

Text you type while a reload reads the file is kept, and the banner offers the reload instead. Remote files are not
checked.

## Remote files

With the Remote plugin on, a file on a saved host opens from the SFTP browser (Enter or a double click), from **Open in
Editor…**, whose file chooser lists your saved hosts, and from [Recent files](#recent-files). A remote tab shows the host
after the file's name and a server icon, and reads "Loading *name* from *host*…" while it loads. Saving writes the file
back to the host after the same check, which says "*name* changed on *host* since you opened it."; Save As can save it
to this computer or another host. Remote files are not checked for outside changes; **Revert to Saved** reads them
again. A remote Markdown file's relative links and images open from the same host, over the same connection and
login. How Remote writes the file and reconnects: [Remote: editing remote files](remote.md#editing-remote-files).

## Recent files

The Editor remembers each file it opens, local or remote, the first time its tab loads it and each time you open it
again: at most 50, newest first, and none that you have not opened for `recent_files_days` days (30 unless you change
it; 0 remembers none). Untitled tabs are not remembered.

**Recent Files…** opens the command palette on the **Recent files** chip; its rows also appear in **All** searches and
among the palette's recent items ([Command palette](command-palette.md#bundled-scopes)). Typing matches the file's
name, host or location, ignoring case, with name matches first. Each row shows the name, the location and, for a remote
file, the host.

| Key | Does |
|---|---|
| Enter | **Open**: open the file, or select its tab |
| Cmd+Enter (Ctrl+Enter) | **Remove from recent files** |

A row whose file nothing can open now, because its host was deleted or the plugin that opened it is off, is dimmed with
"Can't reopen: host or plugin unavailable" and can only be removed. A file that fails to reopen posts "Could not reopen
a recent file".

## Default editor

**Default editor** on the Editor's settings page (Settings › Tools › Editor) chooses the editor for the files Jasper
opens itself: `config.toml` (**Open in Editor** in Settings › Appearance & Behavior › System Settings › Configuration
file, **Open config.toml** on the Settings banner and the configuration notification), a plugin's settings file, the
custom VM options file, **File › Open File…**, Cmd-clicked paths in terminal panes and other plugins' requests. It also
sets the `EDITOR` of Jasper's shells, which `git commit`, `crontab -e` and `kubectl edit` use.

- **Jasper Editor**, the default: files open in a tab here. `EDITOR` is the path of this Jasper's own `jasper` command
  (the one inside the installed app) followed by `editor --wait`, so it works whether or not the command is installed
  on your `PATH`; when Jasper has no `jasper` command, it sets no `EDITOR`.
- **An installed editor**, by name. Terminal editors open in a new terminal tab in the file's folder, in the window the
  file was opened from, else the active one; the tab closes when the editor exits with status 0 and stays open
  otherwise. They run with Jasper's own `PATH`, so when Jasper starts from the Dock their `:!` commands may need full
  paths. App editors start on their own; one that exits with an error posts "Could not open a file in your default
  editor", naming its program. `EDITOR` is the editor's path with the options below.
- **System default app**: files open with your system's app for them, and Jasper sets no `EDITOR`.
- **Custom command**: `custom_command` with the file's quoted path added, in a terminal tab, or on its own when
  `custom_in_terminal` is off. `/bin/sh` runs it with Jasper's `PATH` plus `/opt/homebrew/bin`, `/usr/local/bin` and
  `~/.local/bin`; on Windows, `cmd` runs it. `EDITOR` is the command as written. Until you enter one, files open with
  the system's default app.

| Id | Editor | Program | Opens in | At a line | `EDITOR` adds |
|---|---|---|---|---|---|
| `nvim` | Neovim | `nvim` | Terminal tab | `+LINE FILE` | |
| `vim` | Vim | `vim` | Terminal tab | `+LINE FILE` | |
| `vi` | vi | `vi` | Terminal tab | `+LINE FILE` | |
| `nano` | nano | `nano` | Terminal tab | `+LINE FILE` | |
| `micro` | micro | `micro` | Terminal tab | `+LINE FILE` | |
| `helix` | Helix | `hx`, else `helix` | Terminal tab | `FILE:LINE` | |
| `emacs` | Emacs | `emacs -nw` | Terminal tab | `+LINE FILE` | `-nw` |
| `code` | Visual Studio Code | `code` | Its own window | `-g FILE:LINE` | `--wait` |
| `cursor` | Cursor | `cursor` | Its own window | `-g FILE:LINE` | `--wait` |
| `zed` | Zed | `zed` | Its own window | `FILE:LINE` | `--wait` |
| `sublime` | Sublime Text | `subl` | Its own window | `FILE:LINE` | `-w` |
| `bbedit` | BBEdit | `bbedit` | Its own window | `+LINE FILE` | `--wait` |
| `textmate` | TextMate | `mate` | Its own window | `-l LINE FILE` | `-w` |
| `idea` | IntelliJ IDEA | `idea` | Its own window | `--line LINE FILE` | `--wait` |
| `notepadpp` | Notepad++ | `notepad++` | Its own window | `-nLINE FILE` | `-multiInst -nosession` |
| `notepad` | Notepad | `notepad` | Its own window | (no line) | |

The page lists the editors Jasper finds, in this order. It looks for each program in the folders of Jasper's `PATH`,
then, except on Windows, in `/opt/homebrew/bin`, `/usr/local/bin` and `~/.local/bin`; on macOS also for the
command-line tool inside Visual Studio Code, Cursor, Zed, Sublime Text and TextMate in `/Applications` and
`~/Applications`; on Windows also in Notepad++'s folder under `%ProgramFiles%` and `%ProgramFiles(x86)%` and Notepad's in
`%SystemRoot%\System32`, trying `.exe`, `.cmd` and `.bat`. It looks when the Editor starts and each time the page opens.
A saved editor it no longer finds shows "(not found)" and acts as System default app.

When Jasper itself was started with `EDITOR` or `VISUAL` set, for example from a terminal, its shells keep both and
Jasper sets neither, until you turn off `keep_environment_editor`; then Jasper replaces `EDITOR`, and `VISUAL` too when
Jasper was started with one. Jasper sets `VISUAL` only then, so an export of `EDITOR` or `VISUAL` in a shell startup
file, such as `~/.zshrc`, still wins. The page shows what Jasper's environment has. `EDITOR` reaches shells started
after a change; [Environment](terminal.md#environment) gives the order in which variables apply.

When the chosen editor cannot take a file (a terminal editor with no Jasper window open, an app that does not start)
or the Editor is off, Jasper hands the file to the desktop: its edit action, then its open action, then showing the file
in the file manager. A Cmd-clicked path and a file from **File › Open File…** are never opened that way, only edited or
shown.

## Settings

The settings live in `plugins/dev.jasper.editor/dev.jasper.editor.toml` in [Jasper home](getting-started.md#jasper-home)
and on Settings › Tools › Editor, in the sections Font, Editing, Markdown, Files and Default editor. Jasper creates the
file, with every setting commented out, when it is missing; changes in the file or on the page apply without a restart.
A bad value is reported and its default used ([Plugin settings](plugins.md#plugin-settings)).

| Key | Default | Values | On the page | Applies to |
|---|---|---|---|---|
| `follow_terminal_font` | `false` | | Follow terminal font | Every tab, at once ([Fonts](#fonts)) |
| `font_family` | The first installed of JetBrains Mono, Menlo, Consolas and DejaVu Sans Mono, else `Monospaced`; IBM Plex Mono under the Enterprise look | An installed font family; the page lists monospaced ones | Font | Every tab, at once |
| `font_size` | `13` | 6–72 points, fractions allowed | Font size | Every tab, at once |
| `font_fallback` | `[]` | Font family names, tried in order for characters the font lacks | Fallback fonts | Every tab, at once |
| `font_ligatures` | `false` | | Font ligatures | Every tab, at once |
| `font_line_height` | `1.0` | 0.5–3: a multiple of the fonts' own line height | Line height | Every tab, at once |
| `tab_size` | `4` | 1–16 columns | Tab size | Every tab, at once; Vim's 'tabstop' and 'shiftwidth' |
| `soft_wrap` | `false` | | Soft wrap | Tabs opened afterwards; **Toggle Soft Wrap** changes one tab |
| `vim` | `false` | | Vim mode | Every tab, at once; the Vim status item and **Toggle Vim Mode** set it |
| `markdown_view_mode` | `"source"` | `source`, `split` (Source and preview), `preview` | Default view | Tabs that show Markdown for the first time; choosing a view in a tab sets it ([Markdown preview](#markdown-preview)) |
| `max_file_mib` | `10` | 1–256 MiB | Largest file (MiB) | The next read |
| `recent_files_days` | `30` | 0–365 days; 0 keeps none | Keep recent files for (days) | At once |
| `default_editor` | `"jasper"` | `jasper`, `system`, `custom` or an editor's id from the [table](#default-editor) | Default editor | The next file opened; `EDITOR` in shells started afterwards |
| `custom_command` | `""` | A command line; the file's path is added at the end | Custom command (while Custom command is chosen) | As `default_editor` |
| `custom_in_terminal` | `true` | | Run in a terminal tab (while Custom command is chosen) | The next file opened |
| `keep_environment_editor` | `true` | | Keep EDITOR and VISUAL from Jasper's environment | Shells started afterwards |

## Files

Under [Jasper home](getting-started.md#jasper-home):

| Path | Holds |
|---|---|
| `plugins/dev.jasper.editor/dev.jasper.editor.toml` | The [settings](#settings) |
| `plugins/dev.jasper.editor/data/recent-files.toml` | [Recent files](#recent-files): `[[file]]` tables with `key`, `name`, `location`, `host` for a remote file, and `opened`. Jasper rewrites it; one it cannot read is renamed `recent-files.toml.bad` and the list starts empty. |
| `plugins/dev.jasper.editor/data/vimrc` | Vim mode's [vimrc](#the-vimrc), which you create |

While a local file is saved, its hidden `.<name>.jasper-<random>.tmp` stands beside it; the save removes it.

## Theme colours

The Editor takes its colours from the UI theme the window shows, Dark or Light: the theme's editor scheme gives the
background, text, caret, selection, current line, gutter and line numbers, and a colour with bold or italic for each
syntax role, such as keywords, strings and comments. The terminal scheme (`terminal.scheme`) does not change the
Editor. A theme change applies to open tabs at once, without touching their text. On macOS a translucent workspace
(`ui.workspace.opacity`) makes the text and gutter backgrounds translucent too. The find bar's and 'hlsearch' match
highlight, and Vim sneak's magenta matches, keep their own colours.

The [Markdown preview](#markdown-preview) takes its page from the UI theme's roles instead: `surface.panel` behind it
(translucent too, with `ui.workspace.opacity`), `text.primary` for text, `accent` for links, `text.secondary` for block
quotes and image placeholders, `surface.card` behind code blocks, `border.window` and `surface.panel.raised` for table
borders and header cells, and `selection.bg` and `text.onSelection` for selected text; Find's matches in it are
highlighted in `selection.bg`. Its code blocks take the editor scheme's syntax colours. Writing an editor scheme:
[Creating themes](creating-themes.md); the SDK's view of it:
[`Appearance.editorPalette()`](sdk-reference.html#dev.jasper.sdk.ui.Appearance.editorPalette()) and
[`Appearance.syntaxPalette()`](sdk-reference.html#dev.jasper.sdk.ui.Appearance.syntaxPalette()).

## Vim mode

Vim mode is modal editing as in IdeaVim: Normal, Insert, Replace and Visual modes, counts, operators with motions and
text objects, registers, `.`, search, marks, folds, macros, mappings, a command line with Ex commands and a vimrc. The
[supported keys and Ex commands](editor-vim-reference.md) are generated from the engine's command table, and
[differences from Vim](vim-divergences.md) lists every deliberate one; the rest is checked against Vim 9.1 by a
recorded corpus.

### Turning Vim mode on

Set `vim = true`, turn on **Vim mode** in Settings › Tools › Editor, click `Vim: Off` in the status bar, or choose
**Toggle Vim Mode** in an Editor tab's menu. Every Editor tab switches at once, and each keeps its text and undo
history. Toggling saves `vim` in the settings file; when that fails, Vim mode stays as toggled until the Editor closes
and a dialog says "Vim mode is on until the Editor closes: the setting could not be saved (*reason*)." ("off" when
you turned it off).

### The Vim bar and the caret

- The strip under the text shows the mode (`-- INSERT --`, `-- VISUAL --`), the partly typed command (`2d`) or the
  Visual area's size (`3`, `2x5`), `recording @a` while a macro records, and messages such as
  `E353: Nothing in register q`. A failing command flashes it; Jasper never beeps.
- When a command line says several lines (`:reg`, `:g/pat/`) they stay above the strip until you type a key; `:` goes
  straight on to the next command line.
- The caret is a block in Normal and Visual mode, a bar in Insert mode, and an underline after an operator and in
  Replace mode.
- Visual mode (`v`, `V`, `<C-v>`) draws the area in the theme's selection colour. In Normal mode a selection made by
  dragging the mouse, by the find bar or by Select All becomes a Visual area.

### Which keys Vim takes

- Vim takes a key when it has a meaning for it in the current mode, and every key while the strip reads a pattern or a
  command line. A key that starts a mapping is Vim's, even one held with Cmd.
- On macOS, shortcuts held with Cmd go on to the Editor and Jasper.
- In Insert and Replace mode the Editor gets the keys Vim does not use.
- In Normal mode (and after an operator, or `<C-o>` from Insert mode) a key Vim does not use never runs one of the text
  area's own [editing keys](#commands-and-shortcuts): Shift+Tab and, on Linux and Windows, Alt+Up flash the Vim bar
  and change nothing. The Editor's commands, Jasper's window shortcuts (such as Ctrl+Shift+T on Linux and Windows) and
  keys a `sethandler` entry gives the Editor still work; a key nothing takes flashes the Vim bar.
- On Linux and Windows several of the Editor's and the window's shortcuts are Vim keys, and Vim reads Ctrl+Shift with a
  letter as Ctrl with that letter: in Normal mode Ctrl+F scrolls, Ctrl+Shift+G (the window's Find Next) shows the file's
  position, Ctrl+L does nothing (Vim's redraw), Ctrl+N moves down and Ctrl+V starts blockwise Visual mode; in Insert
  mode Ctrl+Shift+W (the window's Close Pane) deletes a word, Ctrl+V inserts the next key literally and Ctrl+C returns
  to Normal mode, so neither Ctrl+V nor Ctrl+Shift+V pastes there. Ctrl+S and Ctrl+Shift+S still save (Vim has no
  `<C-s>`). Use the tab's menu, `:action find` or a `sethandler` entry such as `sethandler <C-f> n:ide` for the others.
- `sethandler <C-f> n:vim i:ide` decides who gets a key in each mode (`n i v x o c`, or `a` for all, joined by `-`), as
  in IdeaVim.

### Searching and the command line

- `/` and `?` type their pattern in the strip, with Vim's command-line keys (`<C-r>` for a register, `<C-r><C-w>` for
  the word under the cursor, `<Up>` and `<Down>` for history). While you type, the match it would find is highlighted
  ('incsearch'); `<Esc>` puts everything back. Patterns are Vim's (`\v`, `\<`, `\zs`, `\{-}`, look-arounds such as
  `\(foo\)\@<=bar`, columns such as `\%>8c`, and the rest); the few items Jasper does not translate say so (`E869`).
- 'hlsearch' highlights every match; `:noh`, or `<Esc>` in Normal mode, hides the highlight until the next search.
- `:` opens the command line in the strip, with the same keys as `/` and its own history. It runs Vim's Ex commands for
  editing, with Vim's ranges (`:3,5`, `:%`, `:'<,'>` after Visual mode, `:/pat/`, `:.,+3`): `:s` with every flag,
  including `c`, which asks in the strip; `:g` and `:v`; `:d`, `:y`, `:m`, `:t`, `:j`, `:>`, `:pu`, `:norm`, `:sort`,
  `:left`, `:right` and `:center`; `:marks`, `:reg`, `:jumps` and `:changes`; `:u`, `:noh` and `:set`. The
  [reference](editor-vim-reference.md#ex-commands) lists them all. `<Tab>` completes command names, options after
  `:set`, file names and tab names.
- `:action id` runs one of the Editor's [commands](#commands-and-shortcuts), `<Action>(id)` in a mapping runs one in any
  mode (`:nnoremap <Space>w <Action>(save)`), and `:actionlist` lists the ids.

### Tabs, files and the window

- The open files are Vim's buffers, one each however many [views](#views-of-one-file) show it, and the views are its
  windows. `:w` saves through the Editor's Save (`:w file` saves as the file), `:q` closes the pane (in a file's last
  view, with unsaved changes, it fails with `E37`; `:q!` closes it and discards them), `:wq`, `:x` and `ZZ` save and
  close the pane, `:qa` closes every view, `:e file` opens a file in a tab (a relative name starts from the current
  file's folder), `:r file` puts a file's lines below the cursor, `:ls` lists the files with their buffer numbers, and
  `:b N`, `:bn`, `:tabn`, `gt` and `gT` move between them, each to the file's view you were in last. An uppercase mark
  (`mA`) set in another file takes you back to the view it was set in (`'A`); one set in another view of the same file
  acts in the current view, as in Vim, and a mark whose view closes passes to the file's most recent other view.
- `<C-g>` shows the file, its line count and the cursor's position; `ga` the character's code.
- Scrolling (`<C-e>`, `<C-d>`, `<C-f>`, `zt`, `zz`), `H`, `M`, `L` and the screen-line motions (`gj`, `g0`, `g$`) work
  on a window as many lines high and characters wide as the text area shows, and the text scrolls past its last line as
  Vim's window does. Scrolling with the mouse or the scroll bar keeps the cursor inside it, as in Vim. With Vim on, long
  lines wrap at any character, as Vim wraps them. 'relativenumber' shows each line's distance from the cursor in the
  gutter.
- The fold commands (`zo`, `zc`, `za`, `zR`, `zM`, `zj`, `[z`) work on the Editor's code folds. A closed fold counts as
  one line for `j`, `k` and scrolling, an operator takes it whole, and the motions Vim's 'foldopen' names (`l`, `w`,
  searches, marks, `%`, `{`, `u`) open the fold they land in.

### Registers, macros and mappings

- `"+` is the system clipboard and `"*` the selection (the clipboard where there is none); `set clipboard=unnamed`
  (`"*`) or `unnamedplus` (`"+`) makes yanks, deletes and puts without a register use them.
- Macros: `qa` … `q` records the keys you type into register `a`, `@a` runs them, `5@a` five times, `@@` the last
  register again and `@:` the last command line; `qA` appends to `a`. A macro stops at the first command that fails, so
  one that ends with `@a` runs to the end of the file; `u` undoes everything a macro or mapping changed at once.
  Ctrl+C stops a macro or mapping that runs on.
- Mappings: `:nmap`, `:vmap`, `:xmap`, `:omap`, `:imap`, `:cmap`, `:map` and `:map!`, their `noremap` forms, and the
  `unmap` and `mapclear` forms, as in Vim (`:nnoremap <Space>w :w<CR>`); without a right-hand side they list the
  mappings. Keys that start a longer mapping wait 'timeoutlen' (1000 ms) for the rest, and the strip shows them.
  `<nowait>`, `<unique>`, `<Plug>`, `<Nop>` and `<Leader>` (`let mapleader`, a backslash by default) work as in Vim.

### The vimrc

The vimrc is `plugins/dev.jasper.editor/data/vimrc` in Jasper home, in UTF-8. Jasper reads it the first time a tab has
Vim mode on, running its lines in that tab, and again on **Reload vimrc** (an Editor tab's menu, the command
palette, or `:action reload-vimrc`), which first puts mappings, options, `sethandler` entries and variables back to their defaults;
registers and marks stay. It holds command lines, as a Vim or IdeaVim vimrc does: `set`, `let mapleader = ","`, the
mapping commands, `sethandler`, `"` comments, lines continued with a leading `\`, `source ~/.vimrc` (relative names
start from the sourcing file's folder; ten levels deep at most), `finish`, and `if` … `elseif` … `else` … `endif`
blocks, where `has('ide')` and `has('jasper')` are true (so IdeaVim's blocks apply), a number is true when it is not 0,
and anything else is false. Lines Jasper cannot run are skipped: a notification "Some vimrc lines were skipped" says how
many, and `:messages` lists each with its line and the reason. A vimrc that cannot be read posts "Could not read the
vimrc"; a missing one is not reported.

### Vim options

`:set`, `:setlocal` and `:setglobal` know these options; any other fails with `E518: Unknown option`. Names are not
case-sensitive. "Per file" options keep a value for each file, which all its views share, and "Per pane" options keep
one in each view.

| Option | Default | Notes |
|---|---|---|
| `argtextobj` | off | [Extension](#extensions) |
| `autoindent` (`ai`) | on | Per file; Jasper's default, Vim's is off |
| `backspace` (`bs`) | `indent,eol,start` | |
| `belloff` (`bo`) | empty | Accepted; changes nothing |
| `clipboard` (`cb`) | empty | |
| `commentary` | off | Extension |
| `commentstring` (`cms`) | The tab's language's comment (`//%s`, `#%s`), else `/* %s */` | Per file; used by [commentary](#extensions) |
| `comments` (`com`) | Vim's | Per file |
| `compatible` (`cp`) | off | Accepted; changes nothing |
| `encoding` (`enc`) | `utf-8` | Accepted; changes nothing |
| `exchange` | off | Extension |
| `expandtab` (`et`) | the tab's `Spaces` or `Tab` | Per file |
| `fileformat` (`ff`) | `unix` | Per file; accepted, changes nothing |
| `foldopen` (`fdo`) | `block,hor,mark,percent,quickfix,search,tag,undo` | |
| `formatoptions` (`fo`) | `tcq` | Per file |
| `gdefault` (`gd`) | off | |
| `highlightedyank` | off | Extension |
| `hlsearch` (`hls`) | on | Vim's is off |
| `ignorecase` (`ic`) | off | |
| `incsearch` (`is`) | on | |
| `iskeyword` (`isk`) | `@,48-57,_,192-255` | Per file |
| `joinspaces` (`js`) | on | |
| `laststatus` (`ls`) | `1` | Accepted; changes nothing |
| `matchit` | off | Extension |
| `matchpairs` (`mps`) | `(:),{:},[:]` | Per file |
| `maxmapdepth` (`mmd`) | `1000` | |
| `mouse` | empty | Accepted; changes nothing |
| `nrformats` (`nf`) | `bin,octal,hex` | Per file |
| `number` (`nu`) | on | Per pane; Vim's is off |
| `relativenumber` (`rnu`) | off | Per pane |
| `replacewithregister` | off | Extension |
| `report` | `2` | |
| `ruler` (`ru`) | off | Accepted; changes nothing |
| `scrolljump` (`sj`) | `1` | |
| `scrolloff` (`so`) | `0` | |
| `shiftround` (`sr`) | off | |
| `shiftwidth` (`sw`) | `tab_size` | Per file |
| `showcmd` (`sc`) | on | |
| `showmode` (`smd`) | on | |
| `sidescroll` (`ss`) | `0` | |
| `sidescrolloff` (`siso`) | `0` | |
| `smartcase` (`scs`) | off | |
| `sneak` | off | Extension |
| `softtabstop` (`sts`) | `0` | Per file |
| `startofline` (`sol`) | on | |
| `surround` | off | Extension |
| `syntax` (`syn`) | empty | Per file; accepted, changes nothing |
| `tabstop` (`ts`) | `tab_size` | Per file |
| `textobj-entire` | off | Extension |
| `textobj-indent` | off | Extension |
| `textwidth` (`tw`) | `0` | Per file |
| `tildeop` (`top`) | off | |
| `timeout` (`to`) | on | |
| `timeoutlen` (`tm`) | `1000` | |
| `ttimeout` | off | Accepted; changes nothing |
| `ttimeoutlen` (`ttm`) | `-1` | Accepted; changes nothing |
| `undolevels` (`ul`) | `1000` | Accepted; the Editor's undo limit applies |
| `vim-paragraph-motion` | off | Extension |
| `visualbell` (`vb`) | off | Accepted; the bell is always the flash |
| `whichwrap` (`ww`) | `b,s` | |
| `wrap` | the tab's Wrap | Per pane |
| `wrapscan` (`ws`) | on | |

### Extensions

Jasper emulates these Vim plugins, as IdeaVim does. `set <name>` in the vimrc, or the plugin's
`Plug '<owner>/<repo>'` line, switches one on with its plugin's default keys; a `Plug` line for any other plugin is
ignored. Each extension's `<Plug>` names can be mapped as in Vim (`:nmap <Leader>s <Plug>Ysurround`), and the
variables the plugins read to leave out their default keys work: `g:surround_no_mappings`, `g:exchange_no_mappings`,
`g:textobj_entire_no_default_key_mappings` and, for matchit, `g:no_plugin_maps`.

| `set` name | Plugin | Keys |
|---|---|---|
| `surround` | `tpope/vim-surround` | `ds`, `cs`, `cS`, `ys`, `yS`, `yss`, `ySs`, `ySS`; Visual `S`, `gS` |
| `commentary` | `tpope/vim-commentary` | `gc{motion}`, `gcc`, `gcu`; Visual and after an operator `gc` |
| `exchange` | `tommcdo/vim-exchange` | `cx{motion}`, `cxx`, `cxc`; Visual `X` |
| `replacewithregister` | `vim-scripts/ReplaceWithRegister` | `gr{motion}`, `grr`; Visual `gr` |
| `vim-paragraph-motion` | `dbakker/vim-paragraph-motion` | `{`, `}` |
| `textobj-entire` | `kana/vim-textobj-entire` | `ae`, `ie` |
| `textobj-indent` | `michaeljsmith/vim-indent-object` | `ai`, `ii`, `aI`, `iI` |
| `argtextobj` | `vim-scripts/argtextobj.vim` | `ia`, `aa` |
| `highlightedyank` | `machakann/vim-highlightedyank` | None |
| `matchit` | `chrisbra/matchit`, or `packadd matchit` | `%`, `g%`, `[%`, `]%`; Visual `a%` |
| `sneak` | `justinmk/vim-sneak` | `s`, `S`; after an operator `z`, `Z`; Visual `s`, `Z`; `;`, `,` |

- surround: `ds"` deletes the quotes around the cursor, `cs"'` changes them, `ysiw)` surrounds a word, `yss]` a line,
  Visual `S` the area; `t` stands for a tag (`cst<em>`).
- commentary: `gcc` comments a line, `gc{motion}` and Visual `gc` the lines they cover, `gcu` and `dgc` work on a block
  of comment lines; it uses the tab's language's comments.
- exchange: `cx{motion}` twice swaps two texts; `cxx` takes lines, Visual `X` the area, `cxc` forgets the first text.
- ReplaceWithRegister: `gr{motion}`, `grr` and Visual `gr` put a register over text.
- vim-paragraph-motion makes `{` and `}` stop at lines of white space too.
- textobj-entire: `ae` is the whole text, `ie` the text without the empty lines at either end.
- textobj-indent: `ii` is the lines at the cursor's indent and deeper (again in Visual mode, the enclosing block), `ai`
  adds the line above and `aI` the lines above and below.
- argtextobj: `ia` is a function argument and `aa` adds its comma.
- highlightedyank draws the text a yank took for a second (`let g:highlightedyank_highlight_duration = 300` for less;
  `g:highlightedyank_highlight_color` takes `rgba(r, g, b, a)`).
- matchit makes `%` go between the words of a group as the tab's language defines them: `if`, `else` and `fi` in shell
  scripts, a tag and its end tag in HTML and XML, `\begin` and `\end` in TeX, `#if` and `#endif` and `/*` and `*/`
  everywhere; `g%` goes the other way, `[%` and `]%` to the start and end of the group around the cursor, and Visual
  `a%` takes it. Matches in comments and strings are passed over. `packadd` of any other package fails with `E919`.
- sneak (opt-in, as it takes over `s` and `S`): `s` and two characters goes to the next place they stand together, `S`
  back; after an operator `z` and `Z` do the same (`dzab`); `;` and `,` repeat it, and after `f` or `t` they are Vim's
  again. A count keeps `s` to the columns near the cursor (`5sab`). The matches ahead are drawn until the cursor moves
  on. Map `f` and `t` to `<Plug>Sneak_f` and `<Plug>Sneak_t` for one-character sneaks that `;` repeats.

## For other plugins

The Editor exports `dev.jasper.editor.api`. A plugin that requires `dev.jasper.editor` (optionally, as Remote and
Project Index do) finds the `Editor` service with `services().find(Editor.class)` and, on the UI thread, calls
`open(Path)` for a local file or `open(EditorDocument)` for a file it reads and writes itself, each also with the
`WindowOwner` to open it in. `EditorDocument.write` documents the save contract a provider follows, and
`EditorDocument.resolveRelative(path)` (Editor 0.11.0) returns the document at a path relative to its folder, on the same
storage and login, for a Markdown preview's relative links and images; a provider checks the path with
`RelativePaths.requireValid`, and one that keeps the default, which returns empty, has links that say "This document
cannot resolve relative links" ([relative paths](plugin-apis.md#relative-paths)). A plugin with a file source calls
`locationDocuments(sourceId, resolver)` so that Open in Editor… and Save As… offer the source's places and open what
the user picks through the `LocationResolver`; `reopener(prefix, resolve)` lets Recent files reopen its
documents, and `opener(handler)` lets it answer Open in Editor… itself. [Plugin APIs](plugin-apis.md) describes the
package.
