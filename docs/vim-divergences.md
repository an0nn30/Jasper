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
