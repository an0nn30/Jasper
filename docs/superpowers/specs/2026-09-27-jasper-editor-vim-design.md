# Editor Vim mode

**Status:** Approved by the user on 2026-09-27. Plan V1a (`docs/superpowers/plans/2026-09-27-jasper-editor-vim-v1a.md`) amends sections 3.8, 4.1 and 13, as marked **Amended (V1a)**. Plan V1b (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v1b.md`) amends sections 3.5, 3.6, 3.8, 5.2, 5.3, 11.1 and 13, as marked **Amended (V1b)**. Plan V1c (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v1c.md`) amends sections 3.1, 4.1 and 11.1, as marked **Amended (V1c)**. Plan V2a (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v2a.md`) amends sections 3.8, 5.2, 6.1, 6.2, 6.3, 11.1, 12 and 13, as marked **Amended (V2a)**. Plan V2b (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v2b.md`) amends sections 3.6, 5.4, 6.1, 6.2 and 12, as marked **Amended (V2b)**. Plan V3a (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v3a.md`) amends sections 2.3, 4.3, 6.3, 7.2, 12 and 13, as marked **Amended (V3a)**. Amends the [Editor plugin](2026-09-27-jasper-editor-plugin-design.md) spec (settings, key handling, package layout) as hosted by [app-owned workspace tabs](2026-09-27-hosted-workspace-tabs-design.md). Branch `claude/vim-mode`.

## Purpose and scope

The Editor (RSyntaxTextArea 3.6.0) has no modal editing, and RSyntaxTextArea has no vi mode. This spec adds a **Vim mode** to the Editor at the level of IdeaVim and VSCodeVim: a user who has Vim in their hands and their vimrc should be able to work in Jasper's Editor without noticing a different editor. We do not rebuild Vim; we emulate its editing model, its command line and the popular plugins IDE emulations ship, and we prove the emulation against real Vim.

Decisions the user made:
- **Scope:** core modal editing, the ex command line, macros and mappings (a vimrc), and emulated extensions (surround, commentary and the rest of section 8). One spec, several plans (section 13).
- **Key conflicts:** Vim wins by default, IdeaVim-style, with a per-key, per-mode override (`sethandler`). ⌘ shortcuts on macOS always stay with the Editor.
- **Switching on:** `vim = true` in the Editor's settings (default off), a clickable **Vim** status item and a **Toggle Vim Mode** command. The rc file lives in the plugin's data directory: `<home>/plugins/dev.jasper.editor/data/vimrc`. It may `source ~/.vimrc` itself.
- **Fidelity is measured, not asserted:** a differential corpus of cases whose expected results are recorded from real Vim 9 (`vim -u NONE -i NONE -N`) by a separate Gradle task and committed; `./gradlew check` replays them with no Vim installed. Intentional divergences are marked and documented.
- **Engine placement:** a headless engine inside the Editor plugin working directly on `RSyntaxTextArea` (approach A). A separate Vim plugin over an extended Editor API (B) and adopting jVi or IdeaVim code (C) were rejected: B exports text-area, undo and command-line API with no user benefit; IdeaVim is Kotlin bound to the IntelliJ platform and jVi is bound to NetBeans' editor.
- **Oracle is Vim, not Neovim:** IdeaVim and VSCodeVim follow Vim's defaults (for example `Y` is `yy`).

Out of scope:
- windows and splits (`:sp`, `:vs`, `<C-w>` window commands), tags, quickfix, location lists;
- `:!`, `!{motion}` and other shell filters;
- Vimscript beyond the vimrc forms in section 7 (no functions, conditionals, autocommands or `:execute`); the expression register `"=`;
- completion (`<C-n>`, `<C-p>`, `<C-x>` submodes are left to the Editor handler), spell, digraphs (`<C-k>`);
- Select mode, `virtualedit`, the undo tree (`g-`, `g+`, `:earlier`, `:later`) and `U`: undo is linear;
- manual folds (`zf`, `zd`, `zE`); fold commands act on RSyntaxTextArea's code folds only;
- easymotion, multiple-cursors, NERDTree;
- `=` with Vim's C-indenting (section 3.9 records the divergence).

## 1. Architecture

```
KeyEvent ─▶ VimTextArea.processKeyEvent
              │  handler rule (section 2.2): Vim or Editor?
              ▼
           KeyInput (normalized: <C-w>, <Esc>, "a")
              ▼
           Mappings (per mode; remap/noremap; <leader>; timeoutlen)
              ▼
           Machine (mode state machine; pending count, register, operator, motion)
              ▼
     Motions · TextObjects · Operators · Commands · Ex · Extensions
              ▼
     VimEditor (one RSyntaxTextArea and its per-tab state)
              ▼
   Registers · Marks · Jumplist · Search · Options · Undo groups · Macros · Dot repeat
```

### 1.1 Objects and scope

- **`Vim`** (one per plugin instance): global state, as in Vim: registers, options' global values, mappings, macros (registers), search pattern and direction, search and command-line history, the last `f`/`t`, the last substitute, the handler table, uppercase marks. It owns the parsed vimrc.
- **`VimEditor`** (one per `EditorTab` while Vim mode is on): the mode, the pending command, lowercase and special marks, the jumplist and change list, the last visual selection, buffer-local option values, the current insert session and undo group. It wraps the tab's `RSyntaxTextArea`; it is not an interface over it. Tests use a real headless `RSyntaxTextArea`, as the Editor's tests already do.
- **`VimTextArea extends RSyntaxTextArea`** is the tab's text area in every case. With Vim off it behaves exactly as today; with Vim on its `processKeyEvent` hands events to `VimEditor` first. Consuming the event there stops Swing's `WHEN_FOCUSED` and ancestor key bindings, which is how the handler rule reaches the Editor's own commands.
- **`VimHost`** is a record of callbacks the Editor supplies for everything outside one text area: save, save as, close tab (forced or not), open a path, list/select/step tabs, run an Editor command by id, the clipboard, a notice, and the visual bell. The engine calls the Editor, never the reverse. Tests supply a recording host.

### 1.2 Turning Vim mode on and off

- The effective switch is `vim` from settings (section 9), changed live, or the toggle command/status item, which writes the setting through the plugin config so it persists.
- Turning Vim on puts every open tab in Normal mode with the caret clamped (section 3.1) and any selection kept as Visual mode. Turning it off ends any insert session and pending command, restores the Editor's caret and removes Vim highlights; the text is untouched.
- The vimrc is read when Vim first turns on, on **Reload vimrc**, and on `:source`. It is not watched.

## 2. Key input

### 2.1 Normalizing keys into `KeyInput`

- Printable characters come from `KEY_TYPED`, so keyboard layouts, Shift and macOS Option-composed characters (`⌥e e` → `é`) are right. `<Esc>`, `<CR>`, `<Tab>`, `<BS>`, `<Del>`, `<Insert>`, `<Home>`, `<End>`, `<PageUp>`, `<PageDown>`, arrows, `<F1>`–`<F12>` and Ctrl-letters come from `KEY_PRESSED` by key code. When a `KEY_PRESSED` is consumed, the `KEY_TYPED` it produces is consumed too.
- Notation is Vim's, case-insensitive in names: `<C-w>`, `<S-Tab>`, `<Space>`, `<lt>`, `<Bar>`, `<Bslash>`, `<leader>`, `<Nop>`, `<Plug>`, `<D-s>` (⌘, mappable on macOS only). `<C-[>` is `<Esc>`; `<C-i>` is `<Tab>`; `<C-m>` is `<CR>`; `<C-h>` is distinct from `<BS>` only in mappings, as in Vim.
- Alt: on Linux and Windows `<M-x>`/`<A-x>` are keys. On macOS Option composes characters, as in terminal Vim.
- Input methods: in Insert, Replace and command-line modes composed text passes through unchanged and counts as typed text. In Normal, Visual and Operator-pending modes the text area disables its input method so commands work with an IME active.

### 2.2 The handler rule

For each normalized key, in order:
1. On macOS, a key with ⌘ goes to the Editor (unless the user mapped `<D-…>` in the current mode).
2. A `sethandler` entry for the key and current mode decides: `vim` or `ide`.
3. Otherwise, **Vim takes the key if Vim, a user mapping or a pending command gives it a meaning in the current mode**. **Amended (V1a review):** in Insert and Replace mode the Editor gets any other key; outside them (Normal, Operator-pending, Insert-Normal, and later Visual) a key the text area's own input map binds to anything but one of the Editor's commands (that is, one of RSyntaxTextArea's editing actions) is held back and rings the bell; ⌘ keys, the Editor's commands and every other key go on to the Editor's and the app's window bindings, and the bell rings when none takes a key Vim would otherwise own. Plain printable keys in Insert mode go to the text area's normal typing path (so RSyntaxTextArea's auto-indent and bracket handling apply) but are recorded by the insert session.

`sethandler {key} {modes}:{vim|ide} …` takes modes `n`, `i`, `v` (Visual), `x`, `o`, `c`, `a` (all), joined with `-` (`n-v:vim i:ide`). On Linux and Windows this default gives Ctrl+S to Save (Vim has no `<C-s>`), `<C-f>` to Vim in Normal mode and to Find in Insert mode, and `<C-w>` to Vim's delete-word in Insert mode.

### 2.3 Pending keys, timeouts and failure

- A key sequence that is a proper prefix of a mapping waits up to `timeoutlen` ms (default 1000, `timeout` on) on a Swing timer, then resolves as unmapped keys. `<nowait>` mappings resolve at once.
- A command that fails in Vim (motion past the buffer end, `f` with no match, a search with no match, an unknown register) **fails** here: it rings the visual bell (a short flash of the Vim bar; Jasper never beeps), discards the pending command, and aborts the rest of the macro, mapping or `:normal` being executed, as Vim does.

**Amended (V3a):** keys wait in a typeahead, one per tab, ported from Vim's getchar.c: a failing command drops the keys that were not typed (a mapping's, a macro's), typed keys stay. The keys of one mapping or macro change the text as one undo step, as Vim closes an undo step only when it takes a typed key. Ctrl+C typed while a mapping or macro runs stops it, as in Vim; Jasper looks for it among the window's pending key events every 1000 keys. At the end of a `:normal` argument keys waiting for a mapping resolve at once.

## 3. The editing model

### 3.1 Cursor

- The cursor is a buffer offset in the text area's `\n`-separated document. Positions are reported to the user 1-based (line, column in characters).
- A "character" is a grapheme: `BreakIterator.getCharacterInstance` decides boundaries, so surrogate pairs and a base letter with combining marks move and delete as one.
- In Normal and Visual modes the cursor never sits after the last character of a non-empty line. `curswant`: vertical motions keep the desired **display** column (tabs expanded with `tabstop`); `$` sets it to end-of-line.
- `j`/`k` move by buffer lines; `gj`/`gk`, `g0`, `g^`, `g$`, `gm` use screen lines when wrap is on. **Amended (V1c):** the window is Vim's model of the text area (its height and width in lines and characters of the Editor font), kept in step with the scroll pane; while Vim is on the text area wraps at any character, as Vim does, and the cursor may be inside a closed fold.
- Mouse: a click in Normal mode places the cursor and clamps it; a drag, a Find-bar match, or the Editor's Select All enters charwise Visual mode; clearing the selection returns to Normal.

### 3.2 Modes

Normal, Insert, Replace, Visual (charwise, linewise, blockwise), Operator-pending, Insert-Normal (`<C-o>` from Insert), and the command line (`:`, `/`, `?`). The mode drives the caret shape (section 5.1), the handler rule and mapping lookup.

### 3.3 Undo

- RTextArea's undo manager is linear; the engine groups edits with `beginAtomicEdit`/`endAtomicEdit` so that **one Normal-mode command, one insert session (including the command that started it, such as `cw` or `o`), one `:` command, one `.`, or one macro step as Vim counts it** is one undo step.
- `u` and `<C-r>` place the cursor where Vim does (start of the changed text); `u` with a count undoes that many steps.
- Moving the cursor with arrow keys inside Insert mode breaks the insert session into two undo steps, as in Vim.

### 3.4 Insert sessions and dot repeat

- An insert session records the keys typed in it (after mapping). `.` repeats the whole change: the entering command (`cw`, `A`, `o`, `3i`) and the recorded keys, replayed through the same path, so auto-indent behaves as it did. A count on `.` replaces the original count.
- Leaving Insert mode with `<Esc>` moves the cursor one character left unless at column 1; `<C-c>` leaves the same way, repeating a count as Vim 9 does (`3ix<C-c>` inserts three) (**Amended (V1a)**).
- The `.` register holds the last inserted text; `<C-a>` in Insert inserts it; `gi` returns to the `'^` mark.

### 3.5 Registers

| Register | Behaviour |
|---|---|
| `"` | unnamed; points at the last written register |
| `0` | last yank |
| `1`–`9` | deletes of a line or more, and deletes with `%` `(` `)` `` ` `` `/` `?` `n` `N` `{` `}` motions; shifted on each |
| `-` | deletes within one line |
| `a`–`z`, `A`–`Z` | named; uppercase appends (linewise if either part is) |
| `_` | black hole |
| `+`, `*` | system clipboard; `*` is the system selection where the toolkit has one, otherwise the clipboard |
| `.` `:` `%` `/` | read-only: last insert, last command line, file name, last search |

Each register holds text and a type: charwise, linewise or blockwise (with width). **Amended (V1b):** setting a register directly (`setreg()`, the corpus's `register x:`) leaves the unnamed register where it points, as Vim does. `clipboard=unnamed`/`unnamedplus` makes the unnamed register the clipboard. The clipboard is reached only through `VimHost`, so tests inject it; a clipboard read that fails reads as empty.

### 3.6 Marks, jumplist and change list

- `a`–`z` per tab; `A`–`Z` global, remembering the document key, so `'A` selects (or opens) that document's tab. **Amended (V1b):** until plan V2's buffer commands, an uppercase mark works within its own tab. **Amended (V2b):** `'A` and `` `A `` in another tab select the mark's tab and go to it; after an operator or in Visual mode they fail.
- Special marks: `` ` `` / `'` (previous context), `[` `]` (last change or yank), `<` `>` (last visual), `.` (last change), `^` (last insert exit), `"` (position when the tab was last left).
- Marks move with edits: an insertion or deletion before a mark shifts it; deleting the mark's line removes a lowercase mark (as Vim does for `:d`, and keeps it for character deletes).
- Jumplist: up to 100 entries per tab, filled by the motions Vim calls jumps (`G`, `gg`, `/`, `?`, `n`, `N`, `%`, `(`, `)`, `{`, `}`, `[[`, `]]`, `H`, `M`, `L`, `'x`, `` `x ``, `*`, `#`, `:{N}`), walked by `<C-o>`/`<C-i>` (with `<Tab>` = `<C-i>`). Change list: `g;`/`g,`.

### 3.7 Search and the Vim regex translator

- `/` and `?` read a pattern on the command line with incremental preview (`incsearch`) and history; `<CR>` with an empty pattern reuses the last. Offsets: `e`, `e+n`, `s`/`b`, `+n`/`-n` (linewise), and `;` chaining.
- `*`, `#`, `g*`, `g#` search for the keyword under the cursor using `iskeyword`.
- `hlsearch` highlights all matches until `:noh`; `ignorecase`, `smartcase` and `wrapscan` apply; wrapping shows "search hit BOTTOM, continuing at TOP" in the Vim bar.
- **`VimRegex`** (pure JDK) translates a Vim pattern to `java.util.regex.Pattern`, with an error naming the unsupported item otherwise. It supports:
  - magic levels `\v`, `\m` (default), `\M`, `\V`;
  - `\<`, `\>` (word boundaries by `iskeyword`), `\( \)`, `\%( \)`, `\|`, `*`, `\+`, `\?`, `\=`, `\{n,m}`, `\{-n,m}` (lazy), `\{}`;
  - `.`, `^`, `$` with Vim's position rules, `\_.`, `\_s` and the other `\_x` forms, `\n`, `\t`, `\e`, `\r`;
  - classes `\s \S \d \D \w \W \a \A \l \L \u \U \x \X \o \O \h \H \k \K \i \I \f \F \p \P`, bracket expressions with `[[:alpha:]]`-style classes, and `\%[…]` optional sequences;
  - `\c`, `\C`; `~` (last substitute string); `\zs`, `\ze` (the match is narrowed with capture groups).
  - Not supported (error): `\%V`, `\%#`, `\%23l`-style position atoms, `\@` look-around (Vim's syntax), `\z(`, equivalence classes `[[=a=]]`.

### 3.8 Options

Options have global and, where Vim has them, buffer-local values (`tabstop`, `shiftwidth`, `softtabstop`, `expandtab`, `autoindent`, `textwidth`, `iskeyword`, `nrformats`, `matchpairs`) and window-local ones (`number`, `relativenumber`, `wrap`, `scrolloff`, `sidescrolloff`), which live on the tab. The full list is in section 6.3.

Defaults where the Editor has its own setting: `tabstop` and `shiftwidth` = the Editor's `tab_size`; `expandtab` = the tab's Spaces state; `autoindent` on, like the Editor's own auto-indent (**Amended (V1a)**); `wrap` = the tab's Wrap state; `number` on. Setting one of these through `:set` also changes the Editor's matching per-tab state, so the status line stays true. Other options default to Vim's (`-u NONE`) values, except `incsearch`, `hlsearch`, `showcmd` and `showmode`, which are on, as in IdeaVim. **Amended (V1b):** `hlsearch` starts off until plan V2 adds `:noh` and `:set` to turn the highlight off. **Amended (V2a):** `hlsearch` is on, as this paragraph says; `:noh` hides the highlight until the next search.

### 3.9 Indentation

`>`, `<`, `<C-t>`, `<C-d>` and `autoindent` follow `shiftwidth`, `expandtab` and `tabstop` exactly as Vim. `=` re-indents each line to the previous non-blank line's indent plus one `shiftwidth` per unclosed `( [ {` and minus one for a line starting with a closer. That is a recorded divergence from Vim's C-indenting (section 11.4).

## 4. Command inventory

### 4.1 Core (plans V1a and V1b)

**Motions**
- Characters and lines: `h j k l <BS> <Space>`, arrows, `0 ^ $ g_ g0 g^ g$ gm |`, `+ - <CR> _`, `gg G {n}% H M L gj gk`, `go`.
- Words: `w b e ge W B E gE`, with Vim's special cases (`cw` on non-blank acts as `ce`; `w` at the last word of a line in an operator stops at end of line; empty lines are words).
- Find in line: `f F t T ; ,` (with `cpoptions` `;` behaviour: `;` after `t` skips an adjacent match).
- Sentences, paragraphs, sections: `( ) { } [[ ]] [] ][`.
- Brackets: `% [( [{ ]) ]}`; `%` uses `matchpairs` and skips quotes as Vim does without matchit.
- Search: `/ ? n N * # g* g#`, with offsets.
- Marks and jumps: `` `x 'x `` `` '' ``, `` `[ `] `< `> `. `^ `" ``, `<C-o> <C-i> g; g,`.
- Every motion is exclusive, inclusive or linewise as in `:help motion`, including the exclusive-to-inclusive and exclusive-to-linewise adjustments (`:help exclusive-linewise`).

**Text objects:** `iw aw iW aW is as ip ap`, `i( a( ib ab i) a)`, `i{ a{ iB aB i} a}`, `i[ a[ i] a]`, `i< a< i> a>`, `i" a" i' a' i` a``, `it at`, with counts and repeated application in Visual mode extending the selection.

**Operators:** `d c y > < = g~ gu gU g? gq gw`; doubled forms act on lines (`dd`, `gUU`, `gUgU`, `gqq`); forced motion types `v`, `V`, `<C-v>` between operator and motion (`dvj`, `d<C-v>2j`). `gq`/`gw` format to `textwidth` (79 when it is 0), joining and breaking paragraphs as Vim's internal formatter does with `formatoptions=tcq`.

**Other Normal-mode commands**
- Deleting, changing, replacing: `x X D C Y s S r{c} R ~ J gJ`, `r<CR>`, counts on each.
- Putting: `p P gp gP ]p [p ]P [P`, with counts and all three register types.
- Entering Insert or Visual: `i a I A o O gi gI v V <C-v> gv`.
- Undo and repeat: `u <C-r> .`, `&` (in V2).
- Numbers: `<C-a> <C-x>` with `nrformats` (default `bin,octal,hex`, Vim's value with `-u NONE`; **Amended (V1a)**), on the number under or after the cursor.
- Scrolling: `<C-e> <C-y> <C-d> <C-u> <C-f> <C-b> zt zz zb z<CR> z. z-`, `scrolloff` respected. **Amended (V1c):** also `z+ z^ <PageUp> <PageDown> <S-Up> <S-Down>`, the scroll keys in Visual mode, `g<Down> g<Up> g<Home> g<End>`; 'scrolljump', 'sidescroll' and 'sidescrolloff' are respected.
- Folds: `zo zc za zO zC zA zR zM zv zj zk [z ]z` on the tab's code folds. **Amended (V1c):** a code fold covers its first to last line; a closed fold counts as one line for vertical motions and scrolling, an operator takes it whole, word motions, `{`, `}` and searches start past it, and the motions Vim's default 'foldopen' names open it.
- Other: `<C-g>` (file name, line count, position in the Vim bar), `ga` (character code), `ZZ` (`:x`), `ZQ` (`:q!`), `m{mark}`. The command-line window (`q:`, `q/`) is not supported.

**Visual mode:** every operator; `o`, `O` (block corners), `$` in block mode, `r J gJ u U ~ > < p P y d c s x X D C S R Y`; block `I` and `A` (replayed on each line on `<Esc>`), block `c`; `gv`; `g<C-a>`/`g<C-x>` (progressive); text objects and motions extend the selection; `:` starts with `'<,'>`; `*`/`#` are Normal-mode only, as in Vim.

**Insert and Replace mode keys:** `<Esc> <C-[> <C-c> <BS> <C-h> <Del> <C-w> <C-u> <C-t> <C-d> <C-r>{reg} <C-r><C-r>{reg} <C-o>{cmd} <C-v>{char} <C-v>u{hex} <C-a> <C-e> <C-y> <Insert>`, arrows, `<Home> <End>`. `<BS>` in Replace mode restores the replaced character. `backspace=indent,eol,start` (Vim 9's defaults.vim value) is the default.

### 4.2 Ex (plan V2)

Section 6.

### 4.3 Macros and mappings (plan V3)

- `q{reg}` … `q` records typed keys (before mapping) into the register as text; `q{A-Z}` appends. `@{reg}`, `@@`, `{n}@{reg}`, `@:`. `"ap`, edit, `"ayy` redefines a macro. Recursive macros run until a command fails. The Vim bar shows `recording @q`.
- Mappings and the vimrc: section 7.

**Amended (V3a):** plan V3a delivers this section. `@` also works in Visual mode, `@.` runs the last inserted text and `@_` nothing, as in Vim; `q:`, `q/` and `q?` fail (there is no command-line window); a register's text keeps named keys in Vim's codes (`<80>kb` for `<BS>`); `recording @q` follows the mode in the Vim bar, with or without 'showmode'.

### 4.4 Extensions (plan V4)

Section 8.

## 5. UI

### 5.1 Caret

RTextArea's `CaretStyle`: block in Normal and Visual, underline in Operator-pending and Replace, thin bar in Insert and while the command line has focus. The block caret draws the character under it inverted, as the Editor theme's caret colour allows.

### 5.2 The Vim bar

A one-line strip at the bottom of each `EditorTab`, shown only while Vim mode is on, using the Editor font:
- left: the mode (`-- INSERT --`, `-- REPLACE --`, `-- VISUAL --`, `-- VISUAL LINE --`, `-- VISUAL BLOCK --`, `-- (insert) --`, plus `recording @q`) when `showmode` is on; messages (`3 lines yanked`, `search hit BOTTOM…`, errors as `E486: Pattern not found: foo` in the theme's error colour) replace it until the next key;
- right: pending keys (`showcmd`, such as `2d` or `"a`) and, for a Visual selection, its size (`3`, `2x5`);
- the command line: `:`, `/` and `?` turn the left part into an editable field with the prompt character. It has its own key handling (section 6.1) and returns focus to the text area when it closes. **Amended (V1b):** the bar draws the command line and its cursor while keys keep going through the text area, which gives every key to the command line; `/` and `?` arrive in V1b, `:` in V2.
- Multi-line output (`:reg`, `:marks`, `:ls`, `:jumps`, `:changes`, `:set all`) expands the bar upward into a scrollable read-only panel dismissed by any key, like Vim's more-prompt. **Amended (V2a):** the panel shows any command line's output of more than one line, errors in the error colour, and behaves as Vim's hit-enter prompt: `<CR>`, `<Space>` and `<Esc>` only close it, and any other key closes it and runs (`:` starts the next command line). After a failed command line only the error stays in the strip, as Vim does not wait then. The strip shows questions (`replace with X (y/n/a/q/l/^E/^Y)?`) and "Press ENTER or type command to continue". `:ls` comes with plan V2b.

### 5.3 Highlights

- Visual selections: charwise and linewise use the text area's selection; blockwise uses a highlighter painting one rectangle per line in display columns (tabs expanded), including past short lines when `$` was used. **Amended (V1b):** all three are drawn by a highlighter in the selection colour, one range per line for a block (a tab the block cuts is drawn whole); the text area's selection stays empty so the caret stays on the Vim cursor.
- `hlsearch` and incremental-search matches use two highlight colours taken from the Editor theme's mark-all and selection colours; they update on edits.
- highlightedyank (section 8) briefly highlights yanked text.

### 5.4 Line numbers

`number` maps to the gutter's line numbers. `relativenumber` replaces RSyntaxTextArea's line number list with a Vim-owned gutter component showing distances, with the current line absolute when `number` is also on. It follows folds, wrap and the theme's gutter colours. **Amended (V2b):** 'relativenumber' is RSyntaxTextArea's own gutter with a line-number formatter, not a component of its own; the cursor line's number is right-aligned like the others; 'number' starts on, as the Editor shows line numbers.

### 5.5 Status and commands

- A **Vim** status item published through the hosted status items: shows `Vim` (selected when on) and toggles on click. With Vim on, the file's line/column item stays as the Editor's.
- Editor commands: **Toggle Vim Mode** (unbound by default) and **Reload vimrc**, both in the shortcut editor and the command palette; `:action` ids (section 7.3) are the Editor's command ids.

## 6. The command line and ex commands (plan V2)

### 6.1 Editing the command line

`<Left> <Right> <S-Left> <S-Right> <Home> <End> <BS> <Del> <C-w> <C-u> <C-b> <C-e>`; `<C-r>{reg}`, `<C-r><C-w>` (word under the cursor), `<C-r><C-a>`; `<Up>`/`<Down>` walk history filtered by the typed prefix (separate histories for `:` and search, 50 entries each, kept for the plugin's lifetime); `<Tab>`/`<S-Tab>` complete command names, option names after `:set`, and paths after `:e`, `:w`, `:r`, `:source` (**Amended (V2a):** completion comes with plan V2b; until then `<Tab>` types a tab. **Amended (V2b):** Jasper completes the commands and options it has (with `no` and `inv`, and an option's value after `=`), paths after the file commands and tab names after `:b`, cycling as Vim's 'wildmode' "full" without 'wildmenu'; where nothing completes, and in `/` and `?`, `<Tab>` types a tab; `:source`'s paths come with V3); `<Esc>` and `<C-c>` cancel; `<BS>` on an empty line cancels.

### 6.2 Ranges and commands

**Ranges:** `N`, `.`, `$`, `%`, `'x` (including `'<` `'>`), `/pat/`, `?pat?`, `\/`, `\?`, `\&`, `+n`, `-n`, combinations (`.,+3`, `'a;/end/`), and a count after the command (`:d 3`). `:'<,'>` is inserted when `:` is typed in Visual mode. Invalid ranges fail with Vim's messages (`E16: Invalid range`).

**Commands** (abbreviations as Vim's; `!` and `|` where Vim allows; `|` separates commands except after `:g`, `:norm` and mappings' right-hand sides):

| Group | Commands |
|---|---|
| Files and tabs | `:w[!] [file]` (a path saves as), `:wa`, `:q[!]`, `:qa[!]`, `:wq[!]`, `:x`, `:xa`, `:wqa`, `:e[!] [file]`, `:enew`, `:r {file}`, `:bn`, `:bp`, `:b N`, `:b name`, `:ls`/`:buffers`, `:tabn`, `:tabp`, `:tabnew [file]` |
| Substitute | `:s/pat/rep/[flags] [count]` with flags `g c i I n e & r` and `gdefault`; any non-alphanumeric delimiter; replacement `\1`–`\9`, `\0`, `&`, `~`, `\n`, `\r`, `\t`, `\u \U \l \L \e \E`, `\&`; `:&`, `:&&`, `:~`, `&`, `g&` |
| Global | `:g/pat/cmd`, `:g!`, `:v`, with any ex command including `:norm` and `:s`; default command `:p` shows lines in the output panel |
| Lines | `:d [x] [n]`, `:y [x] [n]`, `:m {addr}`, `:t`/`:co {addr}`, `:>`, `:<` (repeatable `>>`), `:j[!]`, `:pu[!] [x]`, `:norm[!] {keys}`, `:sort[!] [b n o x u i r] [/pat/]`, `:left`, `:right`, `:center`, `:{N}`, `:p`, `:#`, `:=` |
| Marks and lists | `:k{x}`, `:mark {x}`, `:delm {marks}`, `:delm!`, `:marks`, `:jumps`, `:changes`, `:reg`/`:di [regs]` |
| Undo, search | `:u`, `:red`, `:noh` |
| Options | `:set`, `:setlocal`, `:setglobal` (section 6.3) |
| Vimrc | the vimrc commands of section 7, typed interactively |

`:w` and friends call `VimHost`, so they use the Editor's save path, conflict checks, remote write contract and dialogs unchanged. `:q` on an unsaved tab fails with `E37: No write since last change (add ! to override)`; `:q!` discards through the Editor's close path without asking. **Amended (V2b):** each Editor tab is a buffer and a tab page, numbered while it is open; `:e file` opens the file in a tab or selects its tab (a missing file gets an empty tab its first save creates), `:w file` saves the tab as the file, `:q` closes the tab and never quits Jasper, and no command needs `!` to leave a tab with changes. Relative names start from the current file's folder (the home folder for an untitled tab); `%`, `#` and `~` work in them. Also `:sav[eas]`, `:exi[t]`, `:quita[ll]`, `:files`, `:bN[ext]`, `:tabN[ext]`, `:tabe[dit]`, `gt` and `gT`; `:2,5w file`, `:w >>`, `:w !cmd` and `:r !cmd` fail with E319. The corpus cannot record these commands; `ExFilesTest` runs them against a fake set of tabs and `EditorWindowTest` through the Editor.

The `c` flag asks in the Vim bar with the current match highlighted: `replace with X (y/n/a/q/l/^E/^Y)?`.

**Amended (V2a):** files and tabs, `ZZ` and `ZQ` move to plan V2b. Every other Ex command name Vim knows and Jasper does not run fails with `E319: Sorry, the command is not available in this version`, as does `\=` in a substitute string (there is no Vim script); a name Vim does not know fails with `E492`. `:undo N` reaches the changes on Jasper's linear history (`E830` otherwise). The 'report' messages ("3 fewer lines", "4 lines yanked", "3 lines >ed 1 time", "3 substitutions on 2 lines") are given, also after Normal-mode commands. The `c` flag's question highlights the match in the line as it stood; the line changes when it is done, as in Vim. A command that asks pauses and continues from the answer, with the rest of its line and `:g`'s remaining lines; `:normal` answers from its keys, or with `<Esc>` when they run out.

### 6.3 `:set`

Forms: `opt`, `noopt`, `opt!`, `invopt`, `opt?`, `opt&`, `opt=val`, `opt:val`, `opt+=val`, `opt-=val`, `opt^=val`, several per line, `:set` (changed options) and `:set all`. Unknown options fail with `E518: Unknown option`. **Amended (V2a):** V2a adds `gdefault`; `number` and `relativenumber` come with the gutter in V2b; `timeout`, `timeoutlen`, `undolevels`, `visualbell` and the accepted-and-ignored vimrc options come with the vimrc in V3. **Amended (V3a):** `timeout`, `timeoutlen` and `maxmapdepth` (`mmd`, Vim's, default 1000) come with the mappings in V3a; the rest with the vimrc in V3b. `:set` and `:set all` list only the options Jasper has, in Vim's layout; `:setlocal` and `:setglobal` read and write the local and global values.

Options: `autoindent (ai)`, `backspace (bs)`, `clipboard (cb)`, `expandtab (et)`, `gdefault (gd)`, `hlsearch (hls)`, `ignorecase (ic)`, `incsearch (is)`, `iskeyword (isk)`, `joinspaces (js)`, `matchpairs (mps)`, `nrformats (nf)`, `number (nu)`, `relativenumber (rnu)`, `scrolljump (sj)`, `scrolloff (so)`, `shiftround (sr)`, `shiftwidth (sw)`, `showcmd (sc)`, `showmode (smd)`, `sidescroll (ss)`, `sidescrolloff (siso)`, `smartcase (scs)`, `softtabstop (sts)`, `startofline (sol)`, `tabstop (ts)`, `textwidth (tw)`, `tildeop (top)`, `timeout (to)`, `timeoutlen (tm)`, `undolevels (ul)` (accepted, capped by the Editor's undo limit), `visualbell (vb)`, `whichwrap (ww)`, `wrap`, `wrapscan (ws)`; extension switches (section 8); and accepted-and-ignored options common in vimrcs (`nocompatible`, `encoding`, `fileformat`, `ruler`, `laststatus`, `mouse`, `belloff`, `ttimeout`, `ttimeoutlen`, `syntax`), which are listed so they do not report errors.

## 7. The vimrc and mappings (plan V3)

### 7.1 Reading

- File: `PluginContext.dataDirectory().resolve("vimrc")`, UTF-8. A missing file is not an error. The Editor ships no vimrc; the plugin's `settings.toml` comments mention where it goes and link the documentation.
- Lines run in order as ex commands. `"` starts a comment line (and a trailing comment where Vim allows it); a line starting with `\` continues the previous one. `source {file}` reads another file (with `~` expanded), up to a nesting depth of 10.
- **Unknown or failing lines are skipped** and reported: one Editor notice ("vimrc: 3 lines were skipped; see :messages") and each line's number, text and reason in `:messages`. The rest of the file still applies.
- Supported vimrc-only forms: `let mapleader = "…"`, `let @{reg} = "…"`, `let g:… = …` for extension settings (section 8), `Plug '…'` and `packadd …` for extensions, and `if has('ide')` / `if has('jasper')` … `else` … `endif` blocks (true for both, so IdeaVim-guarded blocks apply; any other condition is false). Nothing else from Vimscript.
- Reloading resets mappings, options' global values, the handler table and extensions to defaults, then reads the file again; registers and marks are kept.

### 7.2 Mappings

- Commands: `map`, `nmap`, `vmap`, `xmap`, `omap`, `imap`, `cmap`, `map!`, their `noremap` forms (`noremap nnoremap nn vnoremap vn xnoremap xn onoremap ono inoremap ino cnoremap cno noremap!`), `unmap` forms, `mapclear` forms, and the listing form (`:nmap` with no arguments, or with a left-hand side prefix) in the output panel.
- Arguments `<silent>` (no command-line echo for the right-hand side), `<nowait>`, `<unique>`; `<buffer>`, `<expr>` and `<script>` are refused with a message.
- Semantics follow `:help map`: recursive mappings remap their right-hand side; `noremap` does not; a right-hand side starting with its own left-hand side is not remapped for that prefix; recursion is limited by `maxmapdepth` 1000 (then `E223: recursive mapping`); `<Plug>` names are unique left-hand sides; `<Nop>` does nothing; counts and registers typed before a mapped key apply to its first command.
- `<Action>(id)` in a right-hand side runs an Editor command by id through `VimHost` (as IdeaVim's `<Action>(…)`); `:action {id}` does the same from the command line; `:actionlist [pattern]` lists ids in the output panel.

**Amended (V3a):** plan V3a delivers the mapping commands typed on the command line; `<Action>(id)`, `:action` and `:actionlist` move to V3b with the vimrc. `<Leader>` and `<LocalLeader>` are a backslash until V3b reads `let mapleader`. `<silent>` and `<special>` are accepted; `<silent>` changes nothing, since Jasper never echoes the command line a mapping types. `:smap`, `:lmap`, `:tmap` and their forms fail with E319 (no Select, Lang-Arg or terminal mode); `:vmap` and `:map` still include Select mode for their listings. Listings follow Vim's layout and order (`n  x           * dd`). The recorder clears Vim's own mappings (`<D-v>` on macOS) before each case.

## 8. Extensions (plan V4)

Enabled by `set {name}` or by the matching `Plug '…'`/`Plug "…"` line (the owner/name in the table; other `Plug` lines are ignored without error). Each keeps its original's `g:` settings where listed and its `<Plug>` names, so user remappings of those work.

| Extension | Enabled by | Keys | Settings |
|---|---|---|---|
| surround | `surround`, `tpope/vim-surround` | `ys{motion}{char}`, `yss`, `yS`, `ySS`, `cs{old}{new}`, `ds{char}`, Visual `S` / `gS`; targets `( ) [ ] { } < > b B r a ' " `` t` (tags, with attributes kept on `cst`), spacing variants for opening brackets | `g:surround_no_mappings` |
| commentary | `commentary`, `tpope/vim-commentary` | `gc{motion}`, `gcc`, Visual `gc`, `gcu`, text object `gc` | comment tokens from RSyntaxTextArea's `getLineCommentStartAndEnd` for the tab's language |
| highlightedyank | `highlightedyank`, `machakann/vim-highlightedyank` | highlight after each yank | `g:highlightedyank_highlight_duration` (ms, default 300), `g:highlightedyank_highlight_color` |
| exchange | `exchange`, `tommcdo/vim-exchange` | `cx{motion}`, `cxx`, Visual `X`, `cxc` | |
| argtextobj | `argtextobj`, `vim-scripts/argtextobj.vim` | `ia`, `aa` | `g:argtextobj_pairs` |
| ReplaceWithRegister | `ReplaceWithRegister`, `vim-scripts/ReplaceWithRegister` | `["x]gr{motion}`, `grr`, Visual `gr` | |
| textobj-entire | `textobj-entire`, `kana/vim-textobj-entire` | `ae`, `ie` | |
| textobj-indent | `textobj-indent`, `michaeljsmith/vim-indent-object` | `ai`, `ii`, `aI`, `iI` | |
| matchit | `matchit`, `packadd matchit` | `%`, `g%`, `[%`, `]%` over keyword pairs (per language: `if`/`elif`/`else`/`fi`, `do`/`done`, HTML/XML tags, and the language table in the plan) | |
| paragraph-motion | `vim-paragraph-motion`, `dbakker/vim-paragraph-motion` | `{`/`}` treat whitespace-only lines as blank | |
| sneak | `sneak`, `justinmk/vim-sneak` | `s{c}{c}`, `S{c}{c}`, `;`, `,`, `z` as operator target; opt-in because it takes over `s` | `g:sneak#s_next` |

## 9. Settings

Added to the Editor's settings table (spec section 7):

| Key | Default | Meaning |
|---|---|---|
| `vim` | false | Vim mode; the Vim status item and Toggle Vim Mode change it |

Everything else about Vim is configured in the vimrc, as with IdeaVim.

## 10. Errors

| Situation | What the user sees |
|---|---|
| A Vim command fails (no match, bad motion) | Visual bell (the Vim bar flashes); for searches, `E486: Pattern not found: …` |
| An ex command fails | Vim's error number and message in the Vim bar's error colour (`E492: Not an editor command: foo`) |
| A known Vim command outside this spec's scope (`:sp`, `:!ls`, `"=`) | `Not supported in Jasper: :sp` in the Vim bar; the command does nothing |
| vimrc lines skipped | One Editor notice; details in `:messages` |
| vimrc unreadable | Editor notice with the reason; Vim runs with defaults |
| `:w` fails | The Editor's existing save failure dialog; the Vim bar shows `E212: Can't open file for writing` style text only when the Editor has no dialog for it |
| Regex feature not supported | `E869: Unsupported in Jasper's Vim regex: \%V` |
| Engine bug (unexpected exception while running a command) | The command is abandoned, the undo group closed, the mode reset to Normal, `Internal error; the command was cancelled` in the Vim bar, and the exception logged through the plugin log. Text already changed stays changed and is undoable. |

## 11. Testing

### 11.1 The differential corpus

- **Files:** `plugins/editor/src/test/resources/vim/corpus/*.case`, grouped by topic (`motions-word.case`, `operators-change.case`, `registers.case`, `ex-substitute.case`, `ext-surround.case`, …). Format, one or more cases per file:

  ```
  === dw deletes to the next word
  set: shiftwidth=4 expandtab
  text:
    hello world foo
  cursor: 1,7
  keys: dw
  registers: " - 1
  --- recorded by vim 9.1.1234
  expect-text:
    hello foo
  expect-cursor: 1,7
  expect-mode: normal
  expect-register ": c "world "
  expect-register -: c "world "
  expect-register 1: (empty)
  ```

  Keys use the notation of section 2.1. Text lines are indented two spaces; trailing spaces are shown with a `$` end marker option (`text$:`) when significant. Optional fields: `set:` (options), `marks:` (initial marks), `clipboard:`, `vimrc:` (lines for mapping and extension cases), `ext:` (extensions to enable), `expect-marks:`, `expect-search:`, `expect-message:`. **Amended (V1b):** `expect-visual:` gives a Visual area's other end; a blockwise register's type is `b` and its width (`b3`, `getregtype()` in letters); the mode `command-line` is Vim's `c`. **Amended (V1c):** `window:` sets the window's size (`20x5`, columns by rows); `folds: java 2 7` makes the text's code folds with RSyntaxTextArea's Java fold parser and closes those starting on the listed lines (the recorder makes the same folds in Vim as manual folds); `expect-view:` (`topline,leftcol`) and `expect-folds:` (the closed folds) are then compared. **Amended (V2a):** `output: yes` makes a case compare what its keys said: the recorder captures Vim's messages with `:redir`, dropping empty lines and the script-error headers of a sourced script, into `expect-output:`. The recorder types keys with `feedkeys()`, where Vim never waits at the hit-enter prompt, so unit tests cover the output panel.
- **Recording:** `./gradlew :jasper-plugin-editor:recordVimCorpus` (not part of `check`; needs `vim` on `PATH`, or `-PvimBinary=…`). For each case it runs `vim -u NONE -i NONE -N -Es` with a generated script that sets options and the buffer, places the cursor, runs the vimrc lines, feeds the keys with `feedkeys(keys, 'xt')`, and writes text, cursor, mode, registers, marks and messages as JSON; the task rewrites everything below each case's `---` line. For `ext:` cases it first clones each extension's upstream repository at the commit pinned in `plugins/editor/vim-oracle.properties` into `build/vim-oracle/` and adds it to `runtimepath`. `-Pcase=<pattern>` records a subset.
- **Replay:** a JUnit dynamic test per case builds a headless `VimTextArea` with a recording `VimHost`, applies the fields, feeds the keys through the same entry point real key events reach after normalization, and compares every recorded field. A case with no `---` block fails with "not recorded". `check` never runs Vim.
- **Divergences:** a case with `divergent: <reason>` keeps a hand-written expected block and is exempt from recording. Every divergent case is listed with its reason in `docs/vim-divergences.md`; a test fails if the document and the corpus disagree.
- **Coverage guard:** `VimCommands` (the table the engine's key parser is built from) lists every Normal, Visual, Operator-pending and Insert key, motion, text object, ex command and option. A test fails if any entry has no corpus case that uses it. Scale target: roughly 1,500 cases by the end of plan V4.

### 11.2 Unit tests

For what the oracle cannot see: key normalization and the handler rule (including ⌘ and platform differences, with the platform injected), the caret style per mode, Vim bar text and showcmd, timeouts (injected timer), IME enable/disable, the clipboard through the host, the block-visual highlighter's rectangles, the relative-number gutter, `VimRegex` translation tables, vimrc parsing and error reporting, `VimHost` calls for `:w`, `:q`, `:e`, `:bn` and `<Action>`, toggling Vim on and off, and the internal-error recovery path.

### 11.3 Architecture and hygiene

- New packages get `package-info.java` with allowed dependencies (section 12); `verifyPluginArchitecture` keeps the Editor on the SDK only.
- Source hygiene applies: key tables use Java escapes, never raw control characters.

### 11.4 Acceptance

GUI checks are the user's: a new section in `docs/editor-verification.md` (typing speed with Vim on, IME, Option-characters on macOS, clipboard with other apps, Ctrl keys on Linux/Windows, the vimrc round trip, extensions).

## 12. Package layout

| Package | Contents | May depend on |
|---|---|---|
| `dev.jasper.editor.vim.regex` | `VimRegex` (pure) | JDK |
| `dev.jasper.editor.vim` | `Vim`, `VimEditor`, `VimTextArea`, `VimHost`, `KeyInput`, `KeyNotation`, `Mappings`, `Machine`, `Motions`, `TextObjects`, `Operators`, `Registers`, `Marks`, `Search`, `Options`, `VimCommands` | `vim.regex`, RSyntaxTextArea, JDK |
| `dev.jasper.editor.vim.ex` | `ExParser`, `Ranges`, `ExCommands`, `Substitute`, `Vimrc` | `vim`, `vim.regex` |
| `dev.jasper.editor.vim.ext` | one class per extension | `vim`, `vim.ex` |
| `dev.jasper.editor.vim.ui` | `VimBar`, `BlockHighlighter`, `RelativeGutter`, caret styles | `vim`, `vim.ex`, RSyntaxTextArea, Swing |
| `dev.jasper.editor.ui` (existing) | `EditorTab` uses `VimTextArea` and `VimBar`; `EditorWindow` builds `VimHost` | adds `vim`, `vim.ui` |

**Amended (V2a):** the Ex classes (`Ex`, `ExTable`, `ExCommand`, `ExInfo`, `ExLines`, `ExSubstitute`, `ExGlobal`) live in `dev.jasper.editor.vim`, not `vim.ex`: they use the engine's package-private API, and a separate package would need a public API or a dependency cycle. `vim.ui` depends on `vim` only. **Amended (V2b):** `VimHost.Documents` (a record of callbacks) is how the engine reaches the Editor's tabs; `ExFiles`, `Completion` and `LineNumbers` join `dev.jasper.editor.vim`, and there is no `RelativeGutter`. **Amended (V3a):** `Typeahead`, `Mappings`, `ExMaps`, `Macros` and `KeyCodes` join `dev.jasper.editor.vim`; there is no `KeyNotation` (`KeyInput` reads the notation).

No SDK change is needed.

## 13. Delivery

One plan per runnable deliverable, each written with `superpowers:writing-plans` and executed in order. Each ends with `./gradlew check` green and its corpus recorded.

- **Plan V1a: engine foundation.** `VimTextArea`, key normalization, the handler rule, the `Machine`, Normal/Insert/Replace modes, counts, motions other than search and marks, text objects, operators, registers (including the clipboard), undo grouping, insert sessions and `.`, caret styles, the Vim bar without the command line, the setting, toggle and status item, the corpus format, recorder, replay test and coverage guard. Runnable: Vim-mode editing without Visual mode or search. **Amended (V1a):** the screen-line motions `H M L gj gk g0 g^ g$ gm`, `gi`, `<C-g>`, `ga` and blockwise forced motions (`d<C-v>j`) move to V1b; `ZZ` and `ZQ` to V2; `timeoutlen` and reading `sethandler` from the vimrc to V3.
- **Plan V1b: Visual mode, search and navigation.** Visual char/line/block with the block highlighter, `VimRegex`, `/ ? n N * #` with offsets, `hlsearch`/`incsearch`, marks, jumplist and change list, scrolling, folds, `<C-a>`/`<C-x>`, `gq`/`gw`; and, moved from V1a, the screen-line motions, `gi`, `<C-g>`, `ga` and blockwise forced motions. **Amended (V1b):** split in two by the user on 2026-09-28; scrolling, the screen-line motions (`H M L gj gk g0 g^ g$ gm`) and folds move to plan V1c.
- **Plan V1c: the view.** Scrolling (`<C-d> <C-u> <C-f> <C-b> <C-e> <C-y> zz zt zb`), `H M L` and the other screen-line motions, and folds.
- **Plan V2: ex command line.** Section 6, the output panel and the relative-number gutter; `ZZ` and `ZQ`. **Amended (V2a):** split in two by the user on 2026-09-28. Plan V2a: the command line, ranges, the line, substitute, global, mark, register and option commands, `:u`, `:red`, `:noh`, the output panel and 'hlsearch' on. Plan V2b: files, buffers and tabs through the Editor (`:w :q :e :b :ls :tab…`, `ZZ`, `ZQ`), Tab completion, and the relative-number gutter.
- **Plan V3: macros and mappings.** Section 4.3 and section 7, with `timeoutlen` (section 2.3) and `sethandler` lines. **Amended (V3a):** split in two by the user on 2026-09-28. Plan V3a: macros, the typeahead and the mapping commands typed on the command line, with 'timeout', 'timeoutlen' and 'maxmapdepth'. Plan V3b: the vimrc (section 7.1), `sethandler` lines, `let mapleader`, `<Action>(id)`, `:action`, `:actionlist` and the vimrc options.
- **Plan V4: extensions.** Section 8, with the pinned-upstream oracle.

Documentation lands with each plan: a Vim section in the Editor README (enabling, the vimrc location, `sethandler`, `<Action>`), `docs/vim-divergences.md`, the supported-command reference generated from `VimCommands` into `docs/editor-vim-reference.md` by a test that fails when it is stale, and `docs/STATUS.md` entries.
