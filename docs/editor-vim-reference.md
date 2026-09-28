# Editor Vim mode: supported keys

Generated from `VimCommands` by `VimDocsTest`; do not edit by hand. Regenerate with
`./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true`. How Vim mode works: [Editor](editor.md#vim-mode);
where it differs from Vim: [divergences](vim-divergences.md).

## Motions

In Normal mode and after an operator. A count repeats or scales them.

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

## Operators

Followed by a motion or text object; doubled (`dd`, `gUU`, `gUgU`) for whole lines.

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

## Text objects

After an operator.

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
