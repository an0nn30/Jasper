# Vim mode: where Jasper differs from Vim

The Editor's Vim mode is checked against Vim 9 by the corpus in `plugins/editor/src/test/resources/vim/corpus`:
each case's expected result was recorded by running real Vim ([Vim mode spec](superpowers/specs/2026-09-27-jasper-editor-vim-design.md),
section 11). This page lists every deliberate difference. `VimDocsTest` fails when the corpus's divergent cases and the
table below disagree. The supported keys are in the [reference](editor-vim-reference.md).

## Corpus cases

A divergent case keeps a hand-written expectation, and `recordVimCorpus` skips it.

| File | Case | Why |
|---|---|---|
| operators-other.case | equals reindents a block | = uses previous-line indent and bracket depth, not Vim's C-indenting |
| operators-other.case | equals equals on a blank line | = uses previous-line indent and bracket depth, not Vim's C-indenting |
| marks.case | backtick dot after o | '. takes each typed character's column, as when typing; Vim fed a whole string at once records where it started |
| ex-range.case | a Vim command Jasper lacks | Jasper has no abbreviations; a command it lacks fails with E319 (docs/vim-divergences.md) |
| ex-substitute.case | s expression is not available | Jasper has no expressions: \= in a substitute string fails with E319 (docs/vim-divergences.md) |
| mappings.case | nowait does not wait for a longer mapping | the recorder types every key at once, so Vim sees ab whole and never waits; Jasper gets the keys one at a time, as typing does |
| macros.case | q colon is not a register | Jasper has no command-line window; q: fails (docs/vim-divergences.md) |
| ext-paragraph-motion.case | the default mappings | the extension's mappings go to its <Plug> commands, where the plugin's call its function (docs/vim-divergences.md) |

## Other differences

- **`=` re-indents simply.** Each line gets the previous non-blank line's indent, one 'shiftwidth' more for each
  bracket that line leaves open, and one less when the line starts with a closing bracket. Vim uses C-indenting.
- **`%` matches brackets only.** Vim also jumps between `/*` and `*/` and between `#if`, `#else` and `#endif`;
  with matchit switched on (`set matchit`, `packadd matchit`) Jasper does too.
- **Typing goes through the Editor.** In Insert mode a typed character runs RSyntaxTextArea's typing, so when the Editor
  closes brackets and quotes as you type, typing `(` also types `)`. The corpus runs with that off.
- **Tabs start from the Editor.** 'tabstop' and 'shiftwidth' start at the Editor's `tab_size`, 'expandtab' follows the
  tab's Spaces or Tab setting, and 'autoindent' is on. Vim's own defaults are 8, 8, off and off. The corpus uses Vim's.
- **The bell is visual.** A failing command flashes the Vim bar; Jasper never beeps. `<Esc>` in Normal mode is silent.
- **Undo is linear.** There is no undo tree (`g-`, `g+`, `:earlier`) and no `U`.
- **`go` counts characters.** Vim counts bytes.
- **Ex commands Jasper lacks fail with E319.** Vim knows 600 Ex commands; the [reference](editor-vim-reference.md#ex-commands)
  lists the ones Jasper runs. Any other name Vim knows (`:ab`, or `:w` until plan V2b) fails with
  `E319: Sorry, the command is not available in this version`; a name Vim does not know fails with `E492`, as in Vim.
- **No Vim script.** `\=` in a substitute string fails with E319, and so do `:let`, `:echo` and `:if`.
- **`:s///c` asks about the line as it was.** Vim redraws the line with the substitutions already made while it asks;
  Jasper highlights the match in the line as it stood and changes the line when it is done, as Vim does, so the text
  and cursor end the same. As in Vim, what changed before each question is its own undo step.
- **The output panel.** When a command line says more than one line, the lines stay above the Vim bar until a key is
  typed: `<CR>`, `<Space>` and `<Esc>` only take them away, and any other key also runs, as at Vim's hit-enter prompt.
  Vim's scroll keys at that prompt (`k`, `u`, `b`, `g`) run as commands instead; scroll the panel with the mouse.
  After a command line that failed only the error stays, as Vim does not wait then. The corpus records Vim through
  `feedkeys()`, where Vim never waits at the hit-enter prompt, so `ExTest` checks the panel.
- **`:undo N` follows one line of history.** Undo is linear, so `:undo N` reaches only the changes on it; another
  number fails with `E830`. Vim's message after an undo ("1 line less; before #2") is not shown.
- **`:sort` compares code points.** `:sort l` does not use the system's collation.
- **Marks and registers in other tabs.** `:marks` shows an uppercase mark in another tab with the tab's name, and
  `:registers` shows `"%` as the tab's name, where Vim shows file names.
- **`:` is not a motion.** After an operator (`d:`) it fails; Vim runs the command and deletes to where it went.
- **Options.** `:set` knows only the options in the [spec](superpowers/specs/2026-09-27-jasper-editor-vim-design.md)'s
  section 3.8 that Jasper has; an option Vim has and Jasper lacks fails with `E518: Unknown option`, and `:set` and
  `:set all` list only Jasper's. 'hlsearch' starts on (Vim's default is off); `:noh` hides the highlight until the next
  search, and so does an `<Esc>` in Normal mode with no count, register or operator pending (in Vim it only beeps).
- **A final line break.** A text without one edits as Vim edits such a file, and is saved as it is, where Vim (with
  'fixendofline', on by default) adds one when it writes. An edit that leaves the last line empty adds the final break,
  which that empty line needs to be a line.
- **Tabs are the buffers.** Each Editor tab is a buffer and a tab page. `:e file` opens the file in a tab, or selects
  its tab, where Vim edits it in the window; `:w file` saves the tab as the file, which it then edits, where Vim
  writes a copy; `:q` closes the tab, and closing the last one leaves Jasper running. A tab never has to be written
  before another is shown, so `:e`, `:b` and `:bn` never need `!` ('hidden' as it were), and `:ls` shows every tab as
  loaded and shown (`a`), named by its location. `:w` on an untitled tab asks where to save it, as the Editor's Save
  does, where Vim fails with `E32`. `:e newfile` opens an empty tab its first save creates.
- **Relative file names** start from the current file's folder (the home folder for an untitled tab), as Jasper has no
  current directory.
- **Only whole files are written.** `:2,5w file`, `:w >> file`, `:w !cmd` and `:r !cmd` fail with E319.
- **The write message counts UTF-8.** `"a.txt" 2L, 8B written` counts the text's lines and UTF-8 bytes; Vim counts the
  bytes written, in the file's encoding and line endings.
- **`:e!` starts a new undo history**, as the Editor's reload does; Vim keeps the text before the reload for undo.
- **Completion.** `<Tab>` on the `:` command line completes the commands and options Jasper has, paths after the file
  commands and tab names after `:b`, cycling through the matches as Vim does without 'wildmenu'. Where nothing
  completes (`:s/a/b`) it types a tab, as Vim does, and in a search (`/`, `?`) it always types a tab, where Vim 9.1
  completes words from the text.
- **Line numbers.** 'number' starts on, as the Editor shows line numbers. With 'number' and 'relativenumber' the
  cursor line's number is right-aligned like the others; Vim puts it on the left. The text area draws an empty row
  after a file's final line break; it is not a line (Vim shows none), so it has no number and the cursor cannot reach
  it.
- **Uppercase marks across tabs.** `'A` and `` `A `` select the tab the mark is in and go there; after an operator or
  in Visual mode they fail, where Vim also cannot operate across files.
- **Folds are the Editor's code folds.** There are no manual folds (`zf`, `zd`, `zE`); `zo`, `zc` and the other fold
  commands act on the folds the Editor finds in the code. A closed fold shows its first line with the Editor's fold
  marker, where Vim shows a one-line summary such as `+--  4 lines: void f() {`.
- **The window is the text area's.** Its height and width are the lines and characters of the Editor's font that the
  text area shows. Vim mode wraps long lines at any character, as Vim does ('linebreak' is not supported); with a
  font whose characters differ in width, a screen line can hold a different number of characters than Vim's would.
- **`ga` shows no digraph.** Vim ends the line with the character's digraph; Jasper has no digraph table.
- **Search patterns are translated.** `VimRegex` turns a Vim pattern into a Java one, and the corpus's `regex.case`
  checks the result against Vim. `\%V`, `\%#`, the `\%23l` and `\%23v` position atoms, `\z(` and equivalence
  classes (`[[=a=]]`) report `E869`. Columns (`\%23c`) count characters where Vim counts bytes, which differs only
  in lines with characters beyond ASCII, and a repeat inside a look-behind (`\(a*\)\@<=`) reaches back at most 1000
  characters. Character classes such as `\w` and `[:alpha:]` are ASCII, as in Vim;
  `\k` and `\<` follow 'iskeyword', treating every letter above U+00FF as a word character.
- **The `'.` mark and the change list follow each typed character**, as when typing in Vim. Keys fed to Vim all at
  once (a mapping, `feedkeys()`) are inserted as one string, so there the mark stays at the insert's start.
- **One typeahead per tab.** Keys wait, and mappings and macros insert theirs, in the tab's own typeahead; a mapping or
  macro that selects another tab (`:bn`, `gt`) goes on in the tab it started in. Vim's typeahead is global.
- **`q:`, `q/` and `q?` fail.** Jasper has no command-line window.
- **Mapping arguments.** `<buffer>`, `<expr>` and `<script>` are refused with `Not supported in Jasper: <expr>`.
  `<silent>` is accepted and changes nothing, since Jasper never echoes the command line a mapping types. `<Leader>`
  and `<LocalLeader>` are a backslash until the vimrc brings `let mapleader`.
- **No Select, Lang-Arg or terminal mappings.** `:smap`, `:lmap`, `:tmap` and their forms fail with E319; `:vmap` and
  `:map` still include Select mode, so their listings show `v` as Vim's do.
- **Listings show Jasper's keys.** Jasper reads `<C-i>`, `<C-m>` and `<C-[>` as `<Tab>`, `<CR>` and `<Esc>`, and
  `:map` lists them so; `<M-x>` is listed as `<M-x>`, where Vim shows the character with its top bit set (`ø`).
- **Recorded keys.** A macro keeps named keys in Vim's codes (`:reg` shows `<BS>` as `<80>kb`). A key with modifiers
  that Vim has no code of its own for is kept with Vim's modifier prefix (`<80><fc>`), which Vim would write only for
  some of them.
- **CTRL-C in a mapping or macro that runs on** is noticed every 1000 keys, when Jasper looks for it among the window's
  pending key events; it cannot read keys while the others run.
- **`<nowait>` in the corpus.** The recorder types a case's keys all at once, so Vim sees a longer mapping's keys
  together and `<nowait>` never matters there; Jasper's corpus runner gives the keys one at a time, as typing does, and
  the case that shows the difference is divergent.
- **The vimrc runs in a tab.** Its lines run as command lines in the selected tab that has Vim on when it is read, so
  a `:normal` or `:s` in a vimrc changes that tab; mappings, options, variables and `sethandler` entries are global.
- **Vim script is literals only.** `:let` sets a global variable (`mapleader`, `g:name`), a register (`@a`, `@/`) or
  an option (`&tw`, `&l:sw`) to one string or number; expressions, `s:` variables, functions and `:echo` fail with
  E319. In a vimrc `if` is true only for `has('ide')`, `has('jasper')` and a number other than 0.
- **Extensions are ports.** surround, commentary, exchange, ReplaceWithRegister, paragraph-motion, textobj-entire,
  textobj-indent, argtextobj and highlightedyank follow their plugins' scripts on Jasper's own commands, and the
  corpus's `ext:` cases are recorded against the plugins, with tpope/vim-repeat, at the commits
  `plugins/editor/vim-oracle.properties` pins. They are switched on by IdeaVim's names (`set textobj-entire`,
  `set vim-paragraph-motion`), and a plugin that has no `<Plug>` names gets IdeaVim's (`<Plug>textobj-indent-ii`,
  `<Plug>(ParagraphNextMotion)`, `<Plug>InnerArgument`), which `:map` shows where Vim shows the plugin's `:call`. A `Plug` line switches on an extension Jasper has and is
  otherwise ignored; `packadd` fails with E319 until matchit comes. `let g:…` settings are kept for the extensions.
- **`:messages` keeps errors.** Jasper's history holds the errors given and the vimrc's report of skipped lines;
  Vim's holds every message.
- **`:source` paths.** In a vimrc a relative name starts from the sourcing file's folder, and typed from the current
  file's folder; Vim starts from the current directory. `:source` needs a name (Vim 9 sources the current buffer
  without one), and `:source!` fails.
- **Options vimrcs set change nothing.** 'compatible', 'encoding', 'fileformat', 'ruler', 'laststatus', 'mouse',
  'belloff', 'ttimeout', 'ttimeoutlen', 'syntax', 'undolevels' and 'visualbell' can be set and read back: the Editor
  keeps each file's encoding and line ends, the Vim bar always shows, the bell is always visual and the Editor's undo
  limit applies. `:syntax` and `:filetype` do nothing; the Editor highlights and indents by language.
- **`<Action>(id)`, `:action` and `:actionlist`** are IdeaVim's, with the Editor's command ids (`save`, `find`,
  `toggle-comment`, `reload-vimrc`); an unknown id fails with `Not an Editor command: id`.
- **surround** has no Insert-mode `<C-s>` or `<C-g>s`, does not re-indent after surrounding lines (the plugin does
  only with 'indentexpr', 'cindent' or 'smartindent', which Jasper does not have), has no `ds/` (there is no `[/`
  motion), and takes `g:surround_{n}` custom surroundings without their `\1`…`\7` prompts. There are no buffer
  variables, so no `b:surround_…`.
- **commentary** takes 'commentstring' from the tab's language: the line comment RSyntaxTextArea knows for it
  (`//%s`, `#%s`), or Vim's `/* %s */`. There is no `:Commentary` command and no `b:commentary_format`.
- **exchange** highlights the first text in the 'hlsearch' colour; `:XchangeHighlightToggle`,
  `:XchangeClear` and `g:exchange_indent = '=='` are not there.
- **ReplaceWithRegister** cannot use the expression register (`"=`): Jasper has no expressions.
- **argtextobj** knows parentheses only, as the plugin does; IdeaVim's `g:argtextobj_pairs` is not there, and the
  plugin's `g:argumentobject_force_toplevel` is. A count is ignored (the plugin's mapping calls it once for each line
  of a range). The plugin finds quoted strings by filling them in the text and putting the line back, so in Vim even
  `yia` leaves a change to undo; Jasper looks at a copy of the line. Where the plugin loops for ever (parentheses that
  do not pair on one line) Jasper stops.
- **highlightedyank** draws for `g:highlightedyank_highlight_duration` milliseconds, 1000 unless set (IdeaVim's is
  300), in IdeaVim's `g:highlightedyank_highlight_color` (`rgba(r, g, b, a)`) or else the colour of the incremental
  search's match; there are no `:HighlightedyankOn`, `Off` or `Toggle` commands and no buffer variables.
- **textobj-entire** stays linewise after `dv` (`dvae`), as the plugin does in Vim 9.1, which has no v:motion_force.
- **matchit** takes each language's `b:match_words`, `b:match_ignorecase`, `b:match_skip` and 'matchpairs'
  additions from a table Vim 9.1's filetype plugins made (`matchit-languages.tsv`), by the tab's language; there are no
  buffer variables to change them. A skip naming syntax groups reads the tab's tokens as `Comment`, `String`,
  `Character` and `Regex`; a skip that is another Vim expression (the shell's, Ruby's, PHP's) skips comments and
  strings. `:MatchEnable`, `:MatchDisable` and `:MatchDebug` are not there, and `g:no_plugin_maps` counts only when it
  is not 0. 'matchpairs' gains the pairs the language's filetype plugin adds (`<:>` for HTML) whether matchit is on
  or not.
- **sneak** has no label mode (`<Plug>SneakLabel_s`, `g:sneak#label`), no `g:sneak#prompt` and no not-found message.
  `.` repeats the keys rather than vim-repeat's `<Plug>SneakRepeat`, with the same result. A letter finds the letters
  whose Unicode decomposition starts with it (`e` finds `é`), where Vim has its own table. Its default mappings check
  each mode on its own, and `,` does not move to `\` when you have mapped `,`.
  A count's vertical scope has no highlight of its own (the plugin's SneakScope), and its columns are measured from
  the start of a tab or wide character under the cursor, where Vim measures from its end. The matches drawn in a tab
  stay until the cursor moves there, rather than clearing when you leave the tab. Your own mapping of `f` or `t` does
  not stop them resetting `;` and `,`. The cancel keys (`<BS>`, `<C-c>`, `<C-g>`, `<Del>`) end a macro or mapping
  they are in.
