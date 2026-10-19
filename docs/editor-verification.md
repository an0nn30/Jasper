# Editor verification

Automated coverage runs in `./gradlew check` (`plugins/editor/src/test`, `plugins/remote/src/test` for remote files,
the SDK contract suite and `JasperApplicationPluginsTest`). These GUI checks are the user's; an unchecked item is
unverified.

## Plan A: local files

- [ ] 1. File → Open in Editor… (Cmd/Ctrl+O) in a Jasper window opens the chosen files in tabs in that window.
- [ ] 2. Dropping a file from Finder onto an editor tab opens it; dropping a folder does nothing.
- [ ] 3. TOML, JSON, Python, Bash and YAML samples are highlighted; `Cargo.lock` and a `#!/usr/bin/env python3` script without an extension are detected.
- [ ] 4. Editing shows ●; Cmd/Ctrl+S saves and clears it; `ls -l` shows the same permissions, and a symbolic link to the file is still a link.
- [ ] 5. Cmd/Ctrl+W closes the current tab, asking about unsaved changes; with no tabs it closes the window.
- [ ] 6. Cmd+Q with an unsaved tab asks Save All / Discard / Cancel; Cancel keeps Jasper running, Save All saves then quits.
- [ ] 7. Changing an open file from the terminal (`echo x >> file`) reloads a clean tab within a few seconds and shows the banner on an edited one.
- [ ] 8. Switching the theme between light and dark recolours the editor; the tab row, status line and find bar follow the theme.
- [ ] 9. Find (Cmd/Ctrl+F), Replace (Alt+Cmd/Ctrl+F), Go to Line (Cmd/Ctrl+L) and Toggle Comment (Cmd/Ctrl+/) work; Shift+Cmd/Ctrl+S opens the save dialog rather than anything else.
- [ ] 10. With two unsaved files, change one on disk from the terminal, then press Cmd+Q and choose Save All: the conflict prompt for that file appears over the "Saving 2 files…" wait and can be answered; Cancel on the wait leaves Jasper running.
- [ ] 11. Saving a read-only file (chmod 444) fails with "permission denied" and leaves the file unchanged.

## Plan B: remote files

- [ ] 12. In the SFTP browser, double-clicking a file (and Enter on a selected file) opens it in a new Editor tab with the host dimmed after its name; the tab's tooltip reads `user@host:/path`.
- [ ] 13. Editing and saving a remote file writes it back: `cat` on the host shows the new content, and `ls -l` shows the same owner, group and permissions.
- [ ] 14. Changing the same remote file from the terminal (`echo x >> file`) before saving makes the save ask Overwrite / Reload / Cancel.
- [ ] 15. Double-clicking a link to a file opens the file, and saving keeps the link (`ls -l` still shows `->`); double-clicking a link to a folder navigates into it.
- [ ] 16. Selecting several files and choosing Open in Editor opens each in a tab; with more than 20 selected, 20 open and the browser says "Opened 20 of N files."
- [ ] 17. Saving a read-only remote file (`chmod 444`) fails with "permission denied" and leaves it unchanged.
- [ ] 18. After the SSH connection ends (close its tabs and wait for it to drop), saving an edited remote tab reconnects, asking for sign-in in a Jasper window if needed.
- [ ] 19. With the Editor disabled in the Plugins manager (after the restart), double-clicking a file in the SFTP browser does nothing and Open in Editor is not in the menu.
- [ ] 20. On a host at least 50 ms away (ping), saving an edited remote file and Save All with about five remote tabs complete in reasonable time; note the times (each save currently costs several dozen sequential SFTP round trips).

## Vim mode (plan V1a)

- [ ] 21. The Vim status item and Toggle Vim Mode switch every open tab between Vim mode and plain editing, keeping text and undo; after a restart the choice holds (`vim = true` is in the settings file).
- [ ] 22. The caret is a block in Normal mode, a bar in Insert mode (`i`) and an underline after `d`; the strip under the text shows `-- INSERT --` and pending keys such as `2d`, and `"qp` on an empty register shows `E353: Nothing in register q` and flashes.
- [ ] 23. Typing fast in Insert mode keeps every character in order; `u` then undoes the whole insert, and `.` repeats it elsewhere.
- [ ] 24. macOS: Option characters (⌥e e for é) insert in Insert mode and work after `f`; ⌘S, ⌘F, ⌘Z and ⌘W still reach the Editor.
- [ ] 25. Linux or Windows: Ctrl+S saves in Normal mode, Ctrl+R redoes, and Ctrl+W in Insert mode deletes a word rather than closing the tab.
- [ ] 26. With a Japanese or Chinese input method on, `dd` and `x` work in Normal mode, and composed text inserts in Insert mode and repeats with `.`.
- [ ] 27. `"+yy` then pasting in another application gives the line; text copied elsewhere comes back with `"+p`.
- [ ] 28. Clicking past the end of a line in Normal mode puts the caret on the line's last character.
- [ ] 29. A remote file opened from the SFTP browser edits and saves in Vim mode as it does without it.
- [ ] 30. In Normal mode, Tab and Shift+Tab change nothing and flash the Vim bar; on Linux or Windows so do Ctrl+D, Ctrl+Y and Alt+Up. macOS: holding a letter for its accent menu, or the emoji picker, types nothing in Normal mode.

## Vim mode (plan V1b)

- [ ] 31. `v`, `V` and `<C-v>` draw the area in the theme's selection colour, the block following tabs and `$`; `<Esc>` clears it; the caret stays a block on the Vim cursor (the text area's own selection stays empty).
- [ ] 32. Dragging the mouse in Normal mode starts Visual mode over exactly the dragged characters (`d` deletes them); dragging backwards works the same.
- [ ] 33. `/` shows `/` and a thin cursor in the Vim bar; typing highlights the match it would find and scrolls to it without moving the caret; `<Esc>` returns the view; `<CR>` jumps and the bar shows `/pattern`.
- [ ] 34. `n` past the last match shows `search hit BOTTOM, continuing at TOP` in the error colour; a pattern with no match shows `E486: Pattern not found: …` and flashes.
- [ ] 35. `*` on a word, `n` and `N` cycle through its occurrences; `<C-o>` then Tab (`<C-i>`) walk back and forward.
- [ ] 36. `<C-v>jjI// <Esc>` comments three lines; `<C-v>jj$A;<Esc>` appends to each.
- [ ] 37. `gqap` on a long paragraph wraps it at 79 columns, keeping `//` or `#` comment leaders.
- [ ] 38. `<C-g>` shows the file's path, line count and position (`[Modified]` after an edit); `ga` shows the code of the character under the cursor.
- [ ] 39. `<C-a>` on `0x0f` gives `0x10`; `V` over three lines of `0` then `g<C-a>` numbers them 1, 2, 3.
- [ ] 40. After edits in two places, `g;` returns to each in turn and `` `. `` to the last; `ma`, then `'a` elsewhere, returns to it.

## Vim mode (plan V1c)

- [ ] 41. `<C-d>`, `<C-u>`, `<C-f>` and `<C-b>` scroll by half or whole windows and the cursor follows; `<C-e>` and `<C-y>` scroll one line, the cursor staying put until it would leave the window.
- [ ] 42. `zt`, `zz` and `zb` put the cursor line at the top, middle and bottom of the window; after resizing the window, `zz` still centres.
- [ ] 43. `H`, `M` and `L` go to the top, middle and bottom lines on screen; `3H` to the third.
- [ ] 44. With Wrap on, a long line wraps at any character; `gj`/`gk` move by screen line and `g0`/`g$` go to the start and end of the screen line.
- [ ] 45. Scrolling with the mouse wheel or the scroll bar moves the view; the caret moves into the window when it was scrolled out, and the next `j` does not jump back.
- [ ] 46. In a Java file, `zc` inside a method folds it (the fold marker in the gutter shows it), `j` steps over the closed fold, `zo` opens it; `zM` folds everything and `zR` opens everything; clicking the gutter's fold markers still works.
- [ ] 47. With a method folded, `dd` on it deletes the whole method, `u` brings it back, and `/` for text inside it opens the fold.
- [ ] 48. With 'incsearch' (on by default), typing `/` and text that matches off screen shows the match while typing, and `<Esc>` returns the view to where it was.
- [ ] 49. `G` `zt` and `<C-f>` at the end show blank rows below the last line, and `H` goes to the top line shown.
- [ ] 50. The caret is visible on a closed fold's line when the cursor is inside the fold.

## Vim mode (plan V2a)

- [ ] 51. `:` opens the command line in the Vim bar with a thin-bar cursor; `<Up>` recalls earlier command lines; `<Esc>` cancels.
- [ ] 52. `:%s/foo/bar/g` changes every line and the bar says how many substitutions on how many lines; `u` undoes it in one step.
- [ ] 53. `:%s/foo/bar/gc` highlights each match and asks `replace with bar (y/n/a/q/l/^E/^Y)?` in the bar; `y`, `n`, `a`, `q` and `l` answer as in Vim, and `<C-e>`/`<C-y>` scroll while it asks.
- [ ] 54. `:reg`, `:marks`, `:jumps` and `:g/pattern/` show their lines above the Vim bar in the Editor font, scrollable with the mouse when long, and the bar says "Press ENTER or type command to continue"; `<CR>` takes them away and `:` starts the next command line at once.
- [ ] 55. An error (`:foo`) shows in red in the bar, with no panel; `:%p|foo` shows only the error.
- [ ] 56. `V`, `jj`, `:` shows `:'<,'>` in the bar; `s/x/y/` then changes only those lines.
- [ ] 57. `:set sw?` shows the Editor's tab size; `:set sw=2` then `>>` indents by 2; `:set` lists the options that differ from their defaults.
- [ ] 58. After `/pattern`, every match is highlighted in the theme's mark-all colour; `:noh` hides them until the next `n`.
- [ ] 59. `:g/TODO/d` deletes matching lines, `:v/./d` deletes blank lines, `:%norm A;` appends to every line and `:sort u` sorts and removes duplicates.
- [ ] 60. `:5,1d` asks `Backwards range given, OK to swap (y/n)?` in the bar; `y` deletes lines 1 to 5.

## Vim mode (plan V2b)

- [ ] 61. In a changed file, `:w` saves it (the tab's dot goes) and the Vim bar says `"…" NL, NB written`; `:w` in an untitled tab opens the Save As dialog.
- [ ] 62. `:e other.txt` (next to the current file) opens it in a new tab next to the current one; `:e` of a file already open selects its tab; `:e new.txt` opens an empty tab that `:w` creates on disk.
- [ ] 63. `:q` in a changed tab says `E37: No write since last change (add ! to override)`; `:q!` closes it without asking and without saving; `:wq` and `ZZ` save and close.
- [ ] 64. With three tabs, `:ls` lists them with numbers, `%a` on the current one and `#` on the one before; `:b 2`, `:bn`, `:bp`, `:tabn`, `gt`, `3gt` and `gT` select the tabs, including tabs moved to another workspace.
- [ ] 65. `:qa` with a changed tab says `E162`; `:qa!` closes every Editor tab without asking; the terminal tabs stay.
- [ ] 66. `:e ` then `<Tab>` completes file names from the current file's folder (folders end with `/`), `<Tab>` again cycles, `<S-Tab>` goes back; `:set sh<Tab>` cycles through the options; `:tabn<Tab>` completes `tabnew`, then `tabnext`.
- [ ] 67. `:set rnu` shows distances from the cursor line in the gutter, updating as the cursor moves; `:set nonu` shows 0 on the cursor line; `:set nonu nornu` hides the numbers; turning Vim off brings the Editor's numbers back.
- [ ] 68. `mA` in one tab, then `'A` in another tab, selects the first tab with the cursor on the mark.
- [ ] 69. `:r other.txt` puts the other file's lines below the cursor; `u` takes them away.
- [ ] 70. A remote file saved with `:w` goes through the Editor's remote save (conflict check and all).

## Vim mode (plan V3a)

- [ ] 71. `qa`, `dwj`, `q`: the Vim bar shows `recording @a` while recording (`-- INSERT --recording @a` in Insert mode); `@a` repeats it, `3@a` three times, `@@` once more.
- [ ] 72. `qaq`, then `qa0xj@aq`, then `@a`: the macro runs down to the last line and stops; `u` undoes everything it changed in one step.
- [ ] 73. `:nnoremap <Space>w :w<CR>`, then Space and `w` saves the tab; `:nmap` lists the mapping above the Vim bar.
- [ ] 74. `:imap jk <Esc>`: typing `jk` quickly leaves Insert mode; typing `j` and waiting a second inserts the `j`, and while it waits the right of the Vim bar shows `j`.
- [ ] 75. `:nmap ab dd`, then `a`: after a second Insert mode starts; after `:set tm=3000` it takes three seconds; after `:set notimeout` it waits for the next key.
- [ ] 76. `:nmap <F2> dd` makes F2 delete a line; `:nmap <D-k> dd` makes ⌘K delete a line in Normal mode, and ⌘K in Insert mode still does what the Editor does.
- [ ] 77. `:nmap x ggx` and then `x` runs on; Ctrl+C stops it, the Vim bar flashes, and the Editor answers again.
- [ ] 78. `:s/a/b/`, `j`, `@:` repeats the substitution on the next line.
- [ ] 79. `qai`, typing 日本 with the Japanese input method, `<Esc>q`, then `@a` on another line inserts 日本 again.

## Vim mode (plan V3b)

- [ ] 80. With `nnoremap <Space>w :w<CR>` in `plugins/dev.jasper.editor/data/vimrc` under Jasper's home, turning Vim on makes Space and `w` save the tab, and no notice appears.
- [ ] 81. A vimrc with a line `bogus` shows the notice `vimrc: 1 line was skipped; see :messages`; `:messages` shows `line    N:` and `E492: Not an editor command: bogus`.
- [ ] 82. After editing the vimrc, **Reload vimrc** (⋯ menu or command palette) applies the change without restarting; a mapping removed from the file is gone.
- [ ] 83. `let mapleader = ","` and then `nmap <Leader>d dd` in the vimrc: `,d` deletes a line.
- [ ] 84. `if has('ide')` … `endif` around a mapping applies it; the same block with `has('nvim')` does not.
- [ ] 85. `sethandler <C-d> n:ide` in the vimrc: in Normal mode Ctrl+D no longer scrolls half a page.
- [ ] 86. `:nnoremap <Space>f <Action>(find)`: Space and `f` opens the Find bar; `:actionlist` lists the Editor's command ids; `:action wrap` toggles soft wrap.
- [ ] 87. `source ~/.vimrc` in the vimrc reads a Vim vimrc; the lines Jasper lacks are counted in the notice.
- [ ] 88. A folder named `vimrc` in place of the file shows `vimrc: could not read …; Vim runs with its defaults.`, and Vim still works.

## Vim mode (plan V4a)

- [ ] 89. With `set surround` in the vimrc, `ysiw)` puts a word in parentheses, `cs)]` changes them to brackets, `ds]` removes them, and `.` repeats each.
- [ ] 90. `ysiwt`, typing `em` and `>`: the Vim bar shows `<em` as you type, and the word gets `<em>`…`</em>`.
- [ ] 91. With `set commentary` in a Java file, `gcc` comments the line with `//`, `gcc` again uncomments it, and `gcip` comments a paragraph; in a Python file `#` is used.
- [ ] 92. `Plug 'tpope/vim-commentary'` in the vimrc (instead of `set commentary`) switches it on too; a `Plug` line for another plugin causes no notice.
- [ ] 93. With `set exchange`, `cxiw` on one word highlights it; `cxiw` on another swaps the two and the highlight goes; `cxc` forgets a first word.
- [ ] 94. With `set ReplaceWithRegister`, `yiw` on one word, then `griw` on another replaces it and `p` still puts the first word.
- [ ] 95. `:nmap <Leader>s <Plug>Ysurround` then `\siw"` quotes a word.
- [ ] 96. `u` after each of these undoes it in one step.

## Vim mode (plan V4b)

- [ ] 97. With `set vim-paragraph-motion`, `}` stops at a line holding only spaces; without it, `}` passes it.
- [ ] 98. With `set textobj-entire`, `yae` then `p` in a new tab copies the whole text; `die` keeps empty lines at either end.
- [ ] 99. With `set textobj-indent` in a Python file, `dii` inside a function's body deletes the body; `vii` then `ii` again widens to the enclosing block.
- [ ] 100. With `set argtextobj`, `cia` on the middle argument of `f(a, b, c)` changes only `b`; `daa` deletes `b, `.
- [ ] 101. With `set highlightedyank`, `yiw` flashes the word for about a second; `let g:highlightedyank_highlight_duration = 300` makes it shorter, and `x` right after a yank clears it at once.
- [ ] 102. `Plug 'kana/vim-textobj-entire'` (and the other plugins' `Plug` lines) in the vimrc switches the extension on.
- [ ] 103. `u` after `dii`, `dae` and `dia` undoes each in one step, and `.` repeats them at the new cursor.

## Vim mode (plan V4c)

- [ ] 104. With `packadd matchit` in the vimrc, `%` on `<div>` in an HTML file goes to its `</div>`, and back.
- [ ] 105. In a shell script, `%` goes from `if` to `elif`, `else` and `fi` in turn; `]%` inside the block goes to `fi`.
- [ ] 106. In a C file, `%` on `(` passes a `)` inside a string or a comment.
- [ ] 107. `d%` on `#if` deletes up to and including `#else`; `u` undoes it in one step.
- [ ] 108. Visual `a%` on a word inside parentheses selects the parentheses and what is between them.
- [ ] 109. `/\(foo\)\@<=bar` finds the `bar` of `foobar` and not a `bar` alone.

## Vim mode (plan V4d)

- [ ] 110. With `set sneak` in the vimrc, `s` and two characters jumps to them, the other matches ahead are drawn in magenta, and `l` clears them.
- [ ] 111. `;` goes on to the next match, `,` back; after `fx`, `;` repeats the `f` again.
- [ ] 112. `dzab` deletes up to `ab`; `.` repeats it at the next `ab`.
- [ ] 113. `v` then `sab` extends the Visual area to `ab`.
- [ ] 114. `3sab` only stops at an `ab` within about three columns of the cursor.
