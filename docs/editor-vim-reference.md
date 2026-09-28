# Editor Vim mode: supported keys

Generated from `VimCommands` by `VimDocsTest`; do not edit by hand. Regenerate with
`./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true`. How Vim mode works: [Editor](editor.md#vim-mode);
where it differs from Vim: [divergences](vim-divergences.md).

## Motions

In Normal and Visual mode and after an operator. A count repeats or scales them. `/` and `?` read a pattern and an offset in the Vim bar, ended by `<CR>`.

| Keys | What it does |
|---|---|
| `h` | left |
| `<Left>` | left |
| `<BS>` | left, wrapping to the previous line ('whichwrap' b) |
| `<C-h>` | left, wrapping like BS |
| `l` | right |
| `<Right>` | right |
| `<Space>` | right, wrapping to the next line ('whichwrap' s) |
| `j` | down |
| `<Down>` | down |
| `<C-j>` | down |
| `<C-n>` | down |
| `k` | up |
| `<Up>` | up |
| `<C-p>` | up |
| `+` | down to the first non-blank |
| `<CR>` | down to the first non-blank |
| `-` | up to the first non-blank |
| `_` | count - 1 lines down, first non-blank |
| `G` | to the last line, or line {count} |
| `gg` | to the first line, or line {count} |
| `0` | to the first column |
| `<Home>` | to the first column |
| `^` | to the first non-blank |
| `$` | to the end of the line, {count} - 1 lines down |
| `<End>` | to the end of the line |
| `g_` | to the last non-blank, {count} - 1 lines down |
| `\|` | to display column {count} |
| `go` | to character {count} of the text |
| `w` | to the start of the next word |
| `W` | to the start of the next WORD |
| `e` | to the end of the word |
| `E` | to the end of the WORD |
| `b` | back to the start of the word |
| `B` | back to the start of the WORD |
| `ge` | back to the end of the previous word |
| `gE` | back to the end of the previous WORD |
| `f{char}` | to the {count}-th {char} to the right |
| `F{char}` | to the {count}-th {char} to the left |
| `t{char}` | till before the {count}-th {char} to the right |
| `T{char}` | till after the {count}-th {char} to the left |
| `;` | repeat the last f, F, t or T |
| `,` | repeat the last f, F, t or T the other way |
| `(` | back {count} sentences |
| `)` | forward {count} sentences |
| `{` | back {count} paragraphs |
| `}` | forward {count} paragraphs |
| `[[` | back to a { in column 1 |
| `]]` | forward to a { in column 1 |
| `[]` | back to a } in column 1 |
| `][` | forward to a } in column 1 |
| `%` | to the matching bracket, or to {count} percent of the text |
| `[(` | back to the unmatched ( |
| `[{` | back to the unmatched { |
| `])` | forward to the unmatched ) |
| `]}` | forward to the unmatched } |
| `/` | search forward for a pattern, with an offset |
| `?` | search backward for a pattern, with an offset |
| `n` | repeat the last search |
| `N` | repeat the last search in the other direction |
| `*` | search forward for the keyword under the cursor |
| `#` | search backward for the keyword under the cursor |
| `g*` | search forward for the keyword under the cursor, also inside words |
| `g#` | search backward for the keyword under the cursor, also inside words |
| `'{char}` | to the first non-blank of mark {char}'s line |
| `` `{char} `` | to mark {char} |
| `H` | to line {count} from the top of the window |
| `M` | to the middle line of the window |
| `L` | to line {count} from the bottom of the window |
| `gj` | down {count} screen lines |
| `gk` | up {count} screen lines |
| `g<Down>` | down {count} screen lines |
| `g<Up>` | up {count} screen lines |
| `g0` | to the first character of the screen line |
| `g<Home>` | to the first character of the screen line |
| `g^` | to the first non-blank of the screen line |
| `gm` | to the middle of the screen line |
| `g$` | to the last character of the screen line |
| `g<End>` | to the last non-blank of the screen line |
| `zj` | to the start of the next fold |
| `zk` | to the end of the previous fold |
| `[z` | to the start of the open fold |
| `]z` | to the end of the open fold |

## Operators

Followed by a motion or text object, or on the Visual area; doubled (`dd`, `gUU`, `gUgU`) for whole lines.

| Keys | What it does |
|---|---|
| `d` | delete |
| `c` | change: delete, then Insert mode |
| `y` | yank |
| `>` | shift right by 'shiftwidth' |
| `<lt>` | shift left by 'shiftwidth' |
| `=` | re-indent (not Vim's C-indenting; see docs/vim-divergences.md) |
| `g~` | toggle case |
| `gu` | lower case |
| `gU` | upper case |
| `g?` | rot13 |
| `gq` | format the lines to 'textwidth', the cursor on the last formatted line |
| `gw` | format the lines to 'textwidth', keeping the cursor |

## Text objects

After an operator, and in Visual mode, where they extend the area.

| Keys | What it does |
|---|---|
| `iw` | inner word |
| `aw` | a word, with white space |
| `iW` | inner WORD |
| `aW` | a WORD, with white space |
| `is` | inner sentence |
| `as` | a sentence, with white space |
| `ip` | inner paragraph |
| `ap` | a paragraph, with the blank lines after it |
| `i(` | inside ( ) |
| `i)` | inside ( ) |
| `ib` | inside ( ) |
| `a(` | a ( ) block |
| `a)` | a ( ) block |
| `ab` | a ( ) block |
| `i{` | inside { } |
| `i}` | inside { } |
| `iB` | inside { } |
| `a{` | a { } block |
| `a}` | a { } block |
| `aB` | a { } block |
| `i[` | inside [ ] |
| `i]` | inside [ ] |
| `a[` | a [ ] block |
| `a]` | a [ ] block |
| `i<lt>` | inside angle brackets |
| `i>` | inside angle brackets |
| `a<lt>` | an angle-bracket block |
| `a>` | an angle-bracket block |
| `i"` | inside " quotes |
| `a"` | a " string, with white space |
| `i'` | inside ' quotes |
| `a'` | a ' string, with white space |
| `` i` `` | inside ` quotes |
| `` a` `` | a ` string, with white space |
| `it` | inside a tag block |
| `at` | a tag block, with its tags |

## Normal-mode commands

| Keys | What it does |
|---|---|
| `x` | delete {count} characters (dl) |
| `<Del>` | delete {count} characters (dl) |
| `X` | delete {count} characters before the cursor (dh) |
| `D` | delete to the end of the line (d$) |
| `C` | change to the end of the line (c$) |
| `s` | substitute {count} characters (cl) |
| `S` | substitute {count} lines (cc) |
| `Y` | yank {count} lines (yy) |
| `r{char}` | replace {count} characters with {char} |
| `~` | toggle the case of {count} characters |
| `J` | join {count} lines, with a space |
| `<C-g>` | show the file name, line count and cursor position |
| `ga` | show the code of the character under the cursor |
| `gJ` | join {count} lines, without a space |
| `p` | put after the cursor |
| `P` | put before the cursor |
| `gp` | put after, cursor after the text |
| `gP` | put before, cursor after the text |
| `]p` | put after, with the current line's indent |
| `[p` | put before, with the current line's indent |
| `]P` | put before, with the current line's indent |
| `[P` | put before, with the current line's indent |
| `i` | insert before the cursor |
| `<Insert>` | insert before the cursor |
| `a` | append after the cursor |
| `I` | insert before the first non-blank |
| `gI` | insert in column 1 |
| `A` | append at the end of the line |
| `o` | open a line below |
| `O` | open a line above |
| `R` | Replace mode |
| `u` | undo {count} changes |
| `<C-r>` | redo {count} changes |
| `.` | repeat the last change, with {count} replacing its count |
| `<C-l>` | redraw (does nothing) |
| `v` | start charwise Visual mode |
| `V` | start linewise Visual mode |
| `<C-v>` | start blockwise Visual mode |
| `gv` | select the last Visual area again |
| `m{char}` | set mark {char} at the cursor |
| `<C-o>` | to an older position in the jumplist |
| `<Tab>` | to a newer position in the jumplist (CTRL-I) |
| `g;` | to an older position in the change list |
| `g,` | to a newer position in the change list |
| `gi` | Insert mode where it was last left (the '^ mark) |
| `<C-a>` | add {count} to the number at or after the cursor |
| `<C-x>` | subtract {count} from the number at or after the cursor |
| `<C-e>` | scroll the text up {count} lines |
| `<C-y>` | scroll the text down {count} lines |
| `<C-d>` | scroll down half a window ({count} sets 'scroll'), the cursor too |
| `<C-u>` | scroll up half a window ({count} sets 'scroll'), the cursor too |
| `<C-f>` | scroll down {count} pages |
| `<PageDown>` | scroll down {count} pages |
| `<S-Down>` | scroll down {count} pages |
| `<C-b>` | scroll up {count} pages |
| `<PageUp>` | scroll up {count} pages |
| `<S-Up>` | scroll up {count} pages |
| `zt` | line {count} (default the cursor line) to the top of the window |
| `z<CR>` | as zt, the cursor on the first non-blank |
| `zz` | line {count} to the middle of the window |
| `z.` | as zz, the cursor on the first non-blank |
| `zb` | line {count} to the bottom of the window |
| `z-` | as zb, the cursor on the first non-blank |
| `z+` | the line below the window to the top (line {count} with a count) |
| `z^` | the line above the window to the bottom (line {count} with a count) |
| `zo` | open {count} levels of folds at the cursor |
| `zO` | open every fold at the cursor |
| `zc` | close {count} levels of folds at the cursor |
| `zC` | close every fold at the cursor |
| `za` | open a closed fold, or close an open one |
| `zA` | open or close every fold at the cursor |
| `zv` | open the folds that hide the cursor line |
| `zR` | open every fold |
| `zM` | close every fold |

## Visual-mode commands

In Visual mode, besides the motions, operators and text objects above. A change repeats with `.` on an area of the same size.

| Keys | What it does |
|---|---|
| `v` | charwise Visual mode, or back to Normal mode |
| `V` | linewise Visual mode, or back to Normal mode |
| `<C-v>` | blockwise Visual mode, or back to Normal mode |
| `o` | go to the other end of the area |
| `O` | go to the other corner of the block (the other end otherwise) |
| `gv` | swap with the last Visual area |
| `x` | delete |
| `<Del>` | delete |
| `s` | change |
| `X` | delete the lines (in a block, the block) |
| `D` | delete the lines; in a block, to the end of each line |
| `Y` | yank the lines (in a block, the block) |
| `C` | change the lines; in a block, to the end of each line |
| `S` | change the lines (in a block, the block) |
| `R` | change the lines (in a block, the block) |
| `u` | lower case |
| `U` | upper case |
| `~` | toggle case |
| `J` | join the lines, with spaces |
| `gJ` | join the lines, without spaces |
| `r{char}` | replace every character with {char} |
| `p` | replace with the register |
| `P` | replace with the register, keeping the registers |
| `I` | insert before the area (in a block, on every line) |
| `A` | append after the area (in a block, on every line) |
| `m{char}` | set mark {char} at the cursor |
| `<C-a>` | add {count} to the first number in each line |
| `<C-x>` | subtract {count} from the first number in each line |
| `g<C-a>` | add {count}, 2 × {count}, … to the numbers of successive lines |
| `g<C-x>` | subtract {count}, 2 × {count}, … from the numbers of successive lines |
| `<C-e>` | scroll the text up {count} lines |
| `<C-y>` | scroll the text down {count} lines |
| `<C-d>` | scroll down half a window ({count} sets 'scroll'), the cursor too |
| `<C-u>` | scroll up half a window ({count} sets 'scroll'), the cursor too |
| `<C-f>` | scroll down {count} pages |
| `<PageDown>` | scroll down {count} pages |
| `<S-Down>` | scroll down {count} pages |
| `<C-b>` | scroll up {count} pages |
| `<PageUp>` | scroll up {count} pages |
| `<S-Up>` | scroll up {count} pages |

## Insert-mode keys

Printable keys type text; these keys do more.

| Keys | What it does |
|---|---|
| `<Esc>` | back to Normal mode, repeating the insert {count} times |
| `<C-c>` | back to Normal mode |
| `<CR>` | new line, with 'autoindent' |
| `<C-j>` | new line |
| `<BS>` | delete the character before the cursor |
| `<C-h>` | delete the character before the cursor |
| `<C-w>` | delete the word before the cursor |
| `<C-u>` | delete the text entered before the cursor in the line |
| `<Del>` | delete the character under the cursor |
| `<Tab>` | insert a tab, or spaces with 'expandtab' or 'softtabstop' |
| `<C-t>` | indent the line by 'shiftwidth' |
| `<C-d>` | unindent the line by 'shiftwidth' |
| `<C-r>` | insert a register |
| `<C-v>` | insert the next key literally, or a character by its code |
| `<C-q>` | insert the next key literally, as CTRL-V |
| `<C-a>` | insert the text of the last insert |
| `<C-e>` | insert the character below the cursor |
| `<C-y>` | insert the character above the cursor |
| `<C-o>` | run one Normal-mode command |
| `<Insert>` | switch between Insert and Replace mode |
| `<Left>` | move the cursor, starting a new insert for undo and . |
| `<Right>` | move the cursor, starting a new insert for undo and . |
| `<Up>` | move the cursor, starting a new insert for undo and . |
| `<Down>` | move the cursor, starting a new insert for undo and . |
| `<Home>` | move the cursor, starting a new insert for undo and . |
| `<End>` | move the cursor, starting a new insert for undo and . |
