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
| `g'{char}` | to the first non-blank of mark {char}'s line, leaving the jumplist alone |
| `` g`{char} `` | to mark {char}, leaving the jumplist alone |
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
| `:` | a command line; with a count, for that many lines (`:.,.+N`) |
| `ZZ` | save the tab if it has changes and close it (`:x`) |
| `ZQ` | close the tab, discarding its changes (`:q!`) |
| `gt` | the next tab, or tab {count} |
| `gT` | {count} tabs back |
| `&` | repeat the last `:s` on the line, without its flags |
| `g&` | repeat the last `:s` on every line, with its flags (`:%s//~/&`) |
| `q{char}` | `q{reg}` records typed keys into a register; `q` again stops (`{A-Z}` appends) |
| `@{char}` | run a register's keys, [count] times; `@@` the last one run, `@:` the last command line |

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
| `:` | a command line for the Visual lines (`:'<,'>`) |
| `q{char}` | record into a register, or stop recording, as `q` |
| `@{char}` | run a register's keys in Visual mode, as `@` |

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

## Extensions

Switched on with `set {name}` or the plugin's `Plug '…'` line, each extension maps its plugin's default keys to
these `<Plug>` commands, which mappings of your own can use too (`:nmap <Leader>s <Plug>Ysurround`).

| Command | What it does |
|---|---|
| `<Plug>ReplaceWithRegisterOperator` (operator) | replace {motion}'s text with the register (`gr`, with `set ReplaceWithRegister`) |
| `<Plug>ReplaceWithRegisterLine` (Normal) | replace [count] lines with the register (`grr`) |
| `<Plug>Commentary` (operator) | comment or uncomment {motion}'s lines, or the Visual lines (`gc`, with `set commentary`) |
| `<Plug>CommentaryLine` (Normal) | comment or uncomment [count] lines (`gcc`) |
| `<Plug>Dsurround` (Normal) | delete the surroundings {char} (`ds`, with `set surround`) |
| `<Plug>Csurround` (Normal) | change the surroundings {char} to {char} (`cs`) |
| `<Plug>CSurround` (Normal) | change the surroundings, the text on lines of its own (`cS`) |
| `<Plug>Ysurround` (operator) | surround {motion} with {char} (`ys`) |
| `<Plug>YSurround` (operator) | surround {motion} with {char}, on lines of its own (`yS`) |
| `<Plug>Yssurround` (Normal) | surround [count] lines' text with {char} (`yss`) |
| `<Plug>YSsurround` (Normal) | surround [count] lines with {char}, on lines of their own (`ySS`) |
| `<Plug>(Exchange)` (operator) | mark {motion}'s text for an exchange, or swap it with the text marked (`cx`, with `set exchange`) |
| `<Plug>(ExchangeLine)` (Normal) | exchange [count] lines (`cxx`) |
| `<Plug>(ExchangeClear)` (Normal) | forget the text marked for an exchange (`cxc`) |
| `<Plug>(ParagraphNextMotion)` (Normal) | forward [count] paragraphs, lines of white space ending them too (`}`, with `set vim-paragraph-motion`) |
| `<Plug>(ParagraphPrevMotion)` (Normal) | back [count] paragraphs, lines of white space ending them too (`{`) |
| `<Plug>Commentary` (text object) | the block of comment lines around the cursor (`gc` after an operator; `gcu` uncomments it) |
| `<Plug>(textobj-entire-a)` (text object) | the whole text (`ae`, with `set textobj-entire`) |
| `<Plug>(textobj-entire-i)` (text object) | the whole text but the empty lines at either end (`ie`) |
| `<Plug>ReplaceWithRegisterVisual` (Visual) | replace the Visual area with the register (`gr`) |
| `<Plug>VSurround` (Visual) | surround the Visual area with {char} (`S`) |
| `<Plug>VgSurround` (Visual) | surround the Visual area with {char}, the other way about lines (`gS`) |
| `<Plug>(Exchange)` (Visual) | mark the Visual area for an exchange, or swap it (`X`) |

## Ex commands

Typed after `:` and ended by `<CR>`. A range goes before the name: `3`, `.`, `$`, `%`, `'x`, `/pattern/`, `?pattern?`,
`\/`, `\?` and `\&`, each with `+N` or `-N` offsets, separated by `,` or `;`; `'<,'>` and `*` are the last Visual lines.
`|` separates commands. When a command line says more than one line, the output stays above the Vim bar until a
key is typed. A command Vim has and Jasper does not fails with E319.

| Command | What it does |
|---|---|
| `:p[rint]` | print the lines ([range], default the cursor line); `P` too |
| `:P[rint]` | print the lines, as `:print` |
| `:l[ist]` | print the lines with tabs as `^I` and `$` at the end |
| `:nu[mber]` | print the lines with their numbers |
| `:#` | print the lines with their numbers, as `:number` |
| `:=` | show the last line's number, or the range's last |
| `:d[elete]` | delete the lines [into a register] [count] |
| `:y[ank]` | yank the lines [into a register] [count] |
| `:>` | shift the lines right; `>>` twice, and so on |
| `:<` | shift the lines left; `<<` twice, and so on |
| `:pu[t]` | put a register as lines below the line; `!` above; `:0put` above the first |
| `:j[oin]` | join the lines (at least two); `!` without spaces |
| `:co[py]` | copy the lines below {address} |
| `:t` | copy the lines below {address}, as `:copy` |
| `:m[ove]` | move the lines below {address} |
| `:norm[al]` | run {keys} as Normal-mode keys, on each line of the range |
| `:sor[t]` | sort the lines (all by default); `!` reverses; `i`, `n`, `x`, `o`, `b`, `f`, `u`, `r` and a `/pattern/` |
| `:le[ft]` | align the lines left, with [indent] |
| `:ri[ght]` | align the lines right at [width] ('textwidth', or 80) |
| `:ce[nter]` | center the lines in [width] ('textwidth', or 80) |
| `:s[ubstitute]` | `:s/pattern/string/flags [count]`: replace matches; flags `& c e g i I n p # l r` |
| `:&` | repeat the last `:s` with the same pattern and string; `:&&` keeps its flags |
| `:~` | repeat the last `:s` with the last used pattern |
| `:g[lobal]` | `:g/pattern/cmd`: run cmd (default `:p`) on the matching lines; `!` on the others |
| `:v[global]` | `:v/pattern/cmd`: run cmd on the lines that do not match |
| `:k` | set a mark at the line (`:ka`, `:k a`) |
| `:ma[rk]` | set a mark at the line |
| `:delm[arks]` | delete marks (`a b-d`); `!` all lowercase ones |
| `:marks` | list the marks, or those named |
| `:ju[mps]` | list the jumplist |
| `:changes` | list the change list |
| `:reg[isters]` | list the registers, or those named |
| `:di[splay]` | list the registers, as `:registers` |
| `:u[ndo]` | undo one change; `:undo N` goes to the text after change N |
| `:red[o]` | redo one change |
| `:noh[lsearch]` | hide the 'hlsearch' matches until the next search |
| `:se[t]` | show or set options: `opt`, `noopt`, `invopt`, `opt!`, `opt&`, `opt?`, `opt=val`, `+=`, `-=`, `^=`, `all` |
| `:setl[ocal]` | `:set` for this tab's local values only |
| `:setg[lobal]` | `:set` for the global values only |
| `:w[rite]` | save the tab (`:w file` saves it as the file; `!` replaces a file that exists) |
| `:wq` | save the tab, then close it |
| `:x[it]` | save the tab if it has changes, then close it |
| `:exi[t]` | as `:x` |
| `:sav[eas]` | save the tab as {file} |
| `:wa[ll]` | save every tab with changes |
| `:xa[ll]` | save every tab with changes, then close them all |
| `:wqa[ll]` | as `:xa` |
| `:q[uit]` | close the tab; `!` discards its changes |
| `:qa[ll]` | close every tab; `!` discards their changes |
| `:quita[ll]` | as `:qa` |
| `:e[dit]` | open {file} in a tab, or read the tab's file again (`!` discards changes) |
| `:ene[w]` | a new untitled tab |
| `:r[ead]` | put {file}'s lines below the line (`:0r` above the first) |
| `:ls` | list the tabs as buffers |
| `:buffers` | as `:ls` |
| `:files` | as `:ls` |
| `:b[uffer]` | select the tab with buffer number N, or whose name holds {name} |
| `:bn[ext]` | the next tab, [N] times |
| `:bN[ext]` | the previous tab, [N] times |
| `:bp[revious]` | the previous tab, [N] times |
| `:tabn[ext]` | the next tab, or tab [N] |
| `:tabN[ext]` | [N] tabs back |
| `:tabp[revious]` | [N] tabs back |
| `:tabnew` | a new untitled tab, or {file} in a tab |
| `:tabe[dit]` | as `:tabnew` |
| `:map` | map {lhs} to {rhs} in Normal, Visual and Operator-pending mode (`!`: Insert and Command-line); list mappings |
| `:nm[ap]` | map {lhs} to {rhs} in Normal mode; list the mappings |
| `:vm[ap]` | map {lhs} to {rhs} in Visual mode; list the mappings |
| `:xm[ap]` | map {lhs} to {rhs} in Visual mode, as `:vmap`; list the mappings |
| `:om[ap]` | map {lhs} to {rhs} in Operator-pending mode; list the mappings |
| `:im[ap]` | map {lhs} to {rhs} in Insert mode; list the mappings |
| `:cm[ap]` | map {lhs} to {rhs} on the command line; list the mappings |
| `:no[remap]` | as `:map`, but {rhs} is not mapped again |
| `:nn[oremap]` | as `:nmap`, but {rhs} is not mapped again |
| `:vn[oremap]` | as `:vmap`, but {rhs} is not mapped again |
| `:xn[oremap]` | as `:xmap`, but {rhs} is not mapped again |
| `:ono[remap]` | as `:omap`, but {rhs} is not mapped again |
| `:ino[remap]` | as `:imap`, but {rhs} is not mapped again |
| `:cno[remap]` | as `:cmap`, but {rhs} is not mapped again |
| `:unm[ap]` | remove the mapping of {lhs} in Normal, Visual and Operator-pending mode (`!`: Insert and Command-line) |
| `:nun[map]` | remove the Normal-mode mapping of {lhs} |
| `:vu[nmap]` | remove the Visual-mode mapping of {lhs} |
| `:xu[nmap]` | remove the Visual-mode mapping of {lhs}, as `:vunmap` |
| `:ou[nmap]` | remove the Operator-pending mapping of {lhs} |
| `:iu[nmap]` | remove the Insert-mode mapping of {lhs} |
| `:cu[nmap]` | remove the command-line mapping of {lhs} |
| `:mapc[lear]` | remove every mapping of `:map` (`!`: of `:map!`) |
| `:nmapc[lear]` | remove every Normal-mode mapping |
| `:vmapc[lear]` | remove every Visual-mode mapping |
| `:xmapc[lear]` | remove every Visual-mode mapping, as `:vmapclear` |
| `:omapc[lear]` | remove every Operator-pending mapping |
| `:imapc[lear]` | remove every Insert-mode mapping |
| `:cmapc[lear]` | remove every command-line mapping |
| `:let` | `:let mapleader = ","`, `:let @a = "…"`, `:let &tw = 80`, `:let g:name = …`: a variable, register or option |
| `:so[urce]` | run {file}'s lines as command lines, as the vimrc's |
| `:mes[sages]` | the message history: errors, and the vimrc's skipped lines; `:messages clear` |
| `:seth[andler]` | `:sethandler {key} {modes}:{vim\|ide} …`: who gets a key in each mode |
| `:ac[tion]` | run the Editor command {id}, as `<Action>(id)` does in a mapping |
| `:Pl[ug]` | `Plug 'tpope/vim-surround'`: switch on the extension Jasper emulates for the plugin; others are ignored |
| `:actionl[ist]` | list the Editor's command ids, or those holding [text] |
| `:sy[ntax]` | accepted and ignored: the Editor highlights by language |
| `:filet[ype]` | accepted and ignored: the Editor knows each file's language |
