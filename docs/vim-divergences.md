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

## Other differences

- **`=` re-indents simply.** Each line gets the previous non-blank line's indent, one 'shiftwidth' more for each
  bracket that line leaves open, and one less when the line starts with a closing bracket. Vim uses C-indenting.
- **`%` matches brackets only.** Vim also jumps between `/*` and `*/` and between `#if`, `#else` and `#endif`.
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
  search.
- **Tab on the command line** types a tab until plan V2b brings completion.
- **Folds are the Editor's code folds.** There are no manual folds (`zf`, `zd`, `zE`); `zo`, `zc` and the other fold
  commands act on the folds the Editor finds in the code. A closed fold shows its first line with the Editor's fold
  marker, where Vim shows a one-line summary such as `+--  4 lines: void f() {`.
- **The window is the text area's.** Its height and width are the lines and characters of the Editor's font that the
  text area shows. Vim mode wraps long lines at any character, as Vim does ('linebreak' is not supported); with a
  font whose characters differ in width, a screen line can hold a different number of characters than Vim's would.
- **`ga` shows no digraph.** Vim ends the line with the character's digraph; Jasper has no digraph table.
- **Search patterns are translated.** `VimRegex` turns a Vim pattern into a Java one, and the corpus's `regex.case`
  checks the result against Vim. `\%V`, `\%#`, the `\%23l`-style position atoms, `\@` look-around, `\z(` and
  equivalence classes (`[[=a=]]`) report `E869`. Character classes such as `\w` and `[:alpha:]` are ASCII, as in Vim;
  `\k` and `\<` follow 'iskeyword', treating every letter above U+00FF as a word character.
- **The `'.` mark and the change list follow each typed character**, as when typing in Vim. Keys fed to Vim all at
  once (a mapping, `feedkeys()`) are inserted as one string, so there the mark stays at the insert's start.
