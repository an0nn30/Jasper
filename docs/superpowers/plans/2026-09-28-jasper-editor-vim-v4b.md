# Editor Vim mode, plan V4b: paragraph-motion, textobj-entire, textobj-indent, argtextobj and highlightedyank — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Five more of spec section 8's extensions, on plan V4a's framework: paragraph-motion (`{` and `}` stop at lines of white space), textobj-entire (`ae`, `ie`), textobj-indent (`ai`, `ii`, `aI`, `iI`), argtextobj (`ia`, `aa`) and highlightedyank (the text a yank took is drawn for a moment), each switched on as IdeaVim names it (`set textobj-entire`) or by its `Plug '…'` line.

**Architecture:** As in V4a, each extension ports its plugin's script onto the engine's commands (`Script.normal`, `Machine.nested`), its commands are `VimCommands` entries under `<Plug>` names, and `Extensions` adds its default mappings. The plugins' text objects select from an omap's or xmap's `:call` by starting Visual mode; `Script.select` does the same, and the Machine then applies the pending operator to that Visual area, as Vim's do_pending_operator does (a forced CTRL-V makes it a block, and `.` runs the keys again). highlightedyank follows its plugin's TextYankPost path: once the yank's command has finished, the region from the marks and the register is drawn as a third kind of highlight until a change, Insert mode, the next yank or its timer clears it. The corpus gains a `let:` field for `g:` settings, and a pin in `plugins/editor/vim-oracle.properties` can name the pins it needs (textobj-entire needs kana/vim-textobj-user).

**Tech Stack:** Java 25 (JBR), Swing, RSyntaxTextArea 3.6.0, JUnit 6 and AssertJ, Vim 9 and git (only for `recordVimCorpus`).

**Spec:** `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md` (plan V4 of section 13, split into V4a, V4b and V4c; sections 8 and 11.1). Plans V1a–V4a and the final-line-break fix are merged; their headers describe the engine, the typeahead, mappings, the vimrc and the extension framework.

**Status:** Written 2026-09-28 on branch `claude/vim-v4b` (worktree `.worktrees/vim-v4b`, from `main` at 0651cc5c). Every source file, test and corpus case below was compiled and run before the plan was written: against Vim 9.1 with the pinned plugins all 2066 corpus cases (160 new) passed, with and without a final line break. Applied to a fresh checkout of `0651cc5c` exactly as written, `./gradlew check` passed (6,809 tests).

## Deviations from the spec (approved scope, adjusted in this plan)

Task 6 writes these into the spec as **Amended (V4b)**.

- **V4b and V4c.** The user split the rest of V4 on 2026-09-28: V4b is paragraph-motion, textobj-entire, textobj-indent, argtextobj and highlightedyank; V4c is matchit (with `packadd` and the languages' `b:match_words`) and sneak.
- **Switches and names are IdeaVim's.** `set vim-paragraph-motion`, `textobj-entire`, `textobj-indent`, `argtextobj`, `highlightedyank`: `:set` takes a hyphen in a name (not before `=`, so `sw-=2` is still `-=`). The `<Plug>` names are the plugins' own where they have them (`<Plug>(textobj-entire-a)`, `-i`) and IdeaVim's where they do not (`<Plug>(ParagraphNextMotion)`, `<Plug>(ParagraphPrevMotion)`, `<Plug>textobj-indent-ai`, `-aI`, `-ii`, `-iI`, `<Plug>InnerArgument`, `<Plug>OuterArgument`); `:map` shows them where Vim shows the plugins' `:call`s.
- **argtextobj** is the plugin's: only parentheses, with its `g:argumentobject_force_toplevel`, not IdeaVim's `g:argtextobj_pairs`, which the spec named. A count is ignored (the plugin's omap has no `<C-u>`, so Vim calls it once for each line of a range). The plugin finds quoted strings by replacing them in the text and putting the line back; the port does that on a copy, so a yank leaves nothing to undo (the cursor after `u` is where the plugin's changes put it), and where the plugin's loop never ends (parentheses that do not pair on a line) the port stops.
- **highlightedyank**'s duration defaults to the plugin's 1000 ms (the spec said IdeaVim's 300); `g:highlightedyank_highlight_in_visual` and `g:highlightedyank_max_lines` are read too, the colour is IdeaVim's `g:highlightedyank_highlight_color` (`rgba(r, g, b, a)`), there are no `:Highlightedyank…` commands and no buffer variables.
- **Text objects select as the plugins do,** in Visual mode, and the pending operator acts on that area; Vim 9.1 has no v:motion_force for textobj-user to read, so `dvae` stays linewise, but a forced CTRL-V makes a block, as do_pending_operator does.
- **The corpus** gains `let:` (`g:` settings made before the plugins load); a pin may name the pins it needs after its commit; the recorder sources `plugin/**/*.vim`, as Vim's startup does.

## Global Constraints

- Java 25 on the JetBrains Runtime; always `./gradlew`, never a system Gradle. Work in `.worktrees/vim-v4b` on `claude/vim-v4b`; check `git branch --show-current` before every commit (other sessions switch branches in the main checkout). Never commit on `main`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.
- The Editor plugin compiles against the SDK only (`verifyPluginArchitecture`, a root task). `dev.jasper.editor.vim` may use `vim.regex`, RSyntaxTextArea, Swing and the JDK. No SDK change.
- No interface without two real implementations; this plan adds none.
- Source hygiene: never put raw control, private-use or unpaired surrogate characters in `.java` files; use escapes. No run of three backticks may appear in any file this plan embeds (the docs link check strips fenced blocks lazily).
- **Do not launch the GUI.** GUI checks go in `docs/editor-verification.md` for the user.
- Engine code runs on the event thread in the app. Tests that type through a `VimEditor` run inside `SwingUtilities.invokeAndWait`.
- The corpus is the authority on Vim behaviour, and every case also runs without a final line break (`VimCorpusTest`); mark a case `divergent:` only for a documented, deliberate difference. Give new commands cases on the one empty line and on the last line.
- `vim` and `git` must be on `PATH` for `recordVimCorpus`, which clones the pinned plugins from GitHub into `plugins/editor/build/vim-oracle` the first time. `check` never runs Vim or git.

## File structure

New, `plugins/editor/src/main/java/dev/jasper/editor/vim/`:

| File | Responsibility |
|---|---|
| `ParagraphMotion.java` | `<Plug>(ParagraphNextMotion)`, `<Plug>(ParagraphPrevMotion)` |
| `TextobjEntire.java` | `<Plug>(textobj-entire-a)`, `<Plug>(textobj-entire-i)` |
| `TextobjIndent.java` | `<Plug>textobj-indent-ai`, `-aI`, `-ii`, `-iI`, and the plugin's script variables |
| `Argtextobj.java` | `<Plug>InnerArgument`, `<Plug>OuterArgument` |
| `HighlightedYank.java` | a yank's highlight, its timer and settings |

Changed there: `Options` (the switches; hyphens in names), `Extensions` (the five, `<unique>` mappings, script variables), `VimCommands`, `Script` (`select`), `Machine` (an object's Visual area under an operator; the undo step's cursor), `TextObjects` (`aroundQuote`, `a"` on any line), `Operators` (a yank tells highlightedyank), `Highlights` (a third kind), `VimEditor` (the yank highlight; `textVersion()`).
Tests: `HighlightedYankTest` (new); `CorpusCase`, `CorpusFile`, `CorpusRunner`, `RecordCorpus` (`let:`, pins that need pins, `plugin/**`), `ExtensionsTest`, `CompletionTest`. Corpus: `ext-paragraph-motion.case`, `ext-textobj-entire.case`, `ext-textobj-indent.case`, `ext-argtextobj.case`. Changed: `plugins/editor/vim-oracle.properties`.
Docs: `docs/vim-divergences.md`, `docs/editor-vim-reference.md` (generated), `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec.

---

### Task 1: paragraph-motion, IdeaVim's switch names, and `let:` and pinned dependencies for the corpus

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/ParagraphMotion.java`, `plugins/editor/src/test/resources/vim/corpus/ext-paragraph-motion.case`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusRunner.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java`, `plugins/editor/vim-oracle.properties`, `docs/vim-divergences.md`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Consumes: V4a's `Extensions` (`Extension`, `Check`, `require`), `Script`, `VimCommands.motion`, `Target.exclusive(...).asJump()`.
- Produces: `Check.UNIQUE` (a mapping is left out when the user mapped its keys in that mode, as `<unique>`); option names with hyphens; the option `vim-paragraph-motion`; `<Plug>(ParagraphNextMotion)` and `<Plug>(ParagraphPrevMotion)`; the corpus field `let:` (`CorpusCase.let()`, run with `:let` before the extensions switch on); `RecordCorpus.needs(name)` (the pins named after a pin's commit, loaded first).

The plugin's motions are `:<C-U>call` mappings: exclusive, a jump (`m'`), and not one of the motions that put a delete in register 1. When a paragraph is the last, the plugin searches for `\%$`, past the last character, and Vim puts the cursor back on that character before the operator sees it. The `:map` listing is the one deliberate difference: the case that shows it is `divergent:`.

- [ ] **Step 1: Write the failing cases**

Create `plugins/editor/src/test/resources/vim/corpus/ext-paragraph-motion.case`:

```text
# paragraph-motion (plan V4b): { and } also stop at lines of white space.

=== } stops at a line of spaces
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 4,1
ext: vim-paragraph-motion
keys: }
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 5,1
expect-mode: normal

=== } stops at an empty line
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 1,2
ext: vim-paragraph-motion
keys: }
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 3,1
expect-mode: normal

=== } stops at a line of a tab
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 6,1
ext: vim-paragraph-motion
keys: }
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 8,1
expect-mode: normal

=== } with a count
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 1,1
ext: vim-paragraph-motion
keys: 3}
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 8,1
expect-mode: normal

=== } from a line of spaces
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 5,2
ext: vim-paragraph-motion
keys: }
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 8,1
expect-mode: normal

=== } in the last paragraph goes to the end
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 9,1
ext: vim-paragraph-motion
keys: }
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 9,3
expect-mode: normal

=== } with a count past the end
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 1,1
ext: vim-paragraph-motion
keys: 9}
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 9,3
expect-mode: normal

=== } from the middle of a line
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 1,2
ext: vim-paragraph-motion
keys: }
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 3,1
expect-mode: normal

=== { stops at a line of spaces
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 6,1
ext: vim-paragraph-motion
keys: {
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 5,1
expect-mode: normal

=== { stops at a line of a tab
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 9,1
ext: vim-paragraph-motion
keys: {
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 8,1
expect-mode: normal

=== { with a count
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 9,1
ext: vim-paragraph-motion
keys: 2{
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 5,1
expect-mode: normal

=== { in the first paragraph goes to the start
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 2,3
ext: vim-paragraph-motion
keys: {
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 1,1
expect-mode: normal

=== { from a line of spaces
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 5,3
ext: vim-paragraph-motion
keys: {
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 3,1
expect-mode: normal

=== { from the middle of a line
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 7,3
ext: vim-paragraph-motion
keys: {
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 5,1
expect-mode: normal

=== { with a count past the start
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 9,1
ext: vim-paragraph-motion
keys: 9{
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 1,1
expect-mode: normal

=== d} deletes to the line of spaces
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 4,1
ext: vim-paragraph-motion
keys: d}
registers: " 0 1 -
--- vim 9.1
expect-text:
  one
  two
  $
     $
  four
  five
  	$
  six
expect-cursor: 4,3
expect-mode: normal
expect-register ": l "three\n"
expect-register 0: none
expect-register 1: l "three\n"
expect-register -: none

=== d} in the last paragraph
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 9,1
ext: vim-paragraph-motion
keys: d}
registers: " 0 1 -
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  x
expect-cursor: 9,1
expect-mode: normal
expect-register ": c "si"
expect-register 0: none
expect-register 1: none
expect-register -: c "si"

=== d} from the middle of a line
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 1,2
ext: vim-paragraph-motion
keys: d}
registers: " 0 1 -
--- vim 9.1
expect-text:
  o
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "ne\ntwo"
expect-register 0: none
expect-register 1: c "ne\ntwo"
expect-register -: none

=== d} over two paragraphs
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 1,1
ext: vim-paragraph-motion
keys: 2d}
registers: " 0 1 -
--- vim 9.1
expect-text:
     $
  four
  five
  	$
  six
expect-cursor: 1,3
expect-mode: normal
expect-register ": l "one\ntwo\n\nthree\n"
expect-register 0: none
expect-register 1: l "one\ntwo\n\nthree\n"
expect-register -: none

=== d{ deletes back to the line of spaces
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 7,2
ext: vim-paragraph-motion
keys: d{
registers: " 0 1 -
--- vim 9.1
expect-text:
  one
  two
  $
  three
  ive
  	$
  six
expect-cursor: 5,1
expect-mode: normal
expect-register ": c "   \nfour\nf"
expect-register 0: none
expect-register 1: c "   \nfour\nf"
expect-register -: none

=== d{ in the first paragraph
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 2,2
ext: vim-paragraph-motion
keys: d{
registers: " 0 1 -
--- vim 9.1
expect-text:
  wo
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "one\nt"
expect-register 0: none
expect-register 1: c "one\nt"
expect-register -: none

=== y} keeps the cursor
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 4,1
ext: vim-paragraph-motion
keys: y}
registers: " 0 1 -
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 4,1
expect-mode: normal
expect-register ": l "three\n"
expect-register 0: l "three\n"
expect-register 1: none
expect-register -: none

=== c} changes to the line of spaces
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 4,1
ext: vim-paragraph-motion
keys: c}x<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  one
  two
  $
  x
     $
  four
  five
  	$
  six
expect-cursor: 4,1
expect-mode: normal
expect-register ": l "three\n"
expect-register 0: none
expect-register 1: l "three\n"
expect-register -: none

=== dot repeats d}
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 1,1
ext: vim-paragraph-motion
keys: d}.
registers: " 0 1 -
--- vim 9.1
expect-text:
     $
  four
  five
  	$
  six
expect-cursor: 1,3
expect-mode: normal
expect-register ": l "\nthree\n"
expect-register 0: none
expect-register 1: l "\nthree\n"
expect-register -: none

=== u after d}
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 4,1
ext: vim-paragraph-motion
keys: d}u
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 4,1
expect-mode: normal

=== v} extends the area
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 1,2
ext: vim-paragraph-motion
keys: v}
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 3,1
expect-mode: visual
expect-visual: 1,2

=== V} extends the area by lines
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 1,2
ext: vim-paragraph-motion
keys: V}
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 3,1
expect-mode: visual-line
expect-visual: 1,2

=== v{ extends the area back
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 7,2
ext: vim-paragraph-motion
keys: v{
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 5,1
expect-mode: visual
expect-visual: 7,2

=== V} then d
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 1,2
ext: vim-paragraph-motion
keys: V}d
registers: " 0 1 -
--- vim 9.1
expect-text:
  three
     $
  four
  five
  	$
  six
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "one\ntwo\n\n"
expect-register 0: none
expect-register 1: l "one\ntwo\n\n"
expect-register -: none

=== backtick backtick goes back after }
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 1,2
ext: vim-paragraph-motion
keys: }``
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 1,2
expect-mode: normal

=== } then j
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 1,3
ext: vim-paragraph-motion
keys: }j
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 4,1
expect-mode: normal

=== } on the one empty line
text:
  $
cursor: 1,1
ext: vim-paragraph-motion
keys: }
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal

=== { on the one empty line
text:
  $
cursor: 1,1
ext: vim-paragraph-motion
keys: {
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal

=== } on lines of white space only
text:
    $
  	$
   $
cursor: 1,1
ext: vim-paragraph-motion
keys: }
--- vim 9.1
expect-text:
    $
  	$
   $
expect-cursor: 3,1
expect-mode: normal

=== { on lines of white space only
text:
    $
  	$
   $
cursor: 3,1
ext: vim-paragraph-motion
keys: {
--- vim 9.1
expect-text:
    $
  	$
   $
expect-cursor: 1,1
expect-mode: normal

=== } with a line of spaces last
text:
  one
  two
     $
cursor: 1,1
ext: vim-paragraph-motion
keys: }
--- vim 9.1
expect-text:
  one
  two
     $
expect-cursor: 3,1
expect-mode: normal

=== } with an empty line last
text:
  one
  two
  $
cursor: 1,1
ext: vim-paragraph-motion
keys: }
--- vim 9.1
expect-text:
  one
  two
  $
expect-cursor: 3,1
expect-mode: normal

=== d} on the last line
text:
  one
  two
cursor: 2,2
ext: vim-paragraph-motion
keys: d}
registers: " 0 1 -
--- vim 9.1
expect-text:
  one
  to
expect-cursor: 2,2
expect-mode: normal
expect-register ": c "w"
expect-register 0: none
expect-register 1: none
expect-register -: c "w"

=== d{ on the first line
text:
  one
  two
cursor: 1,2
ext: vim-paragraph-motion
keys: d{
registers: " 0 1 -
--- vim 9.1
expect-text:
  ne
  two
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "o"
expect-register 0: none
expect-register 1: none
expect-register -: c "o"

=== the default mappings
divergent: the extension's mappings go to its <Plug> commands, where the plugin's call its function (docs/vim-divergences.md)
text:
  a
cursor: 1,1
ext: vim-paragraph-motion
keys: :nmap }<CR>:omap {<CR>:xmap }<CR>
output: yes
--- expected
expect-text:
  a
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  }             <Plug>(ParagraphNextMotion)
  o  {             <Plug>(ParagraphPrevMotion)
  x  }             <Plug>(ParagraphNextMotion)

=== } after nnoremap is still the extension's
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 4,1
ext: vim-paragraph-motion
keys: :nnoremap j k<CR>}
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 5,1
expect-mode: normal

=== g:no_mappings of another plugin leaves } alone
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 4,1
ext: vim-paragraph-motion
let: g:surround_no_mappings = 1
keys: }
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 5,1
expect-mode: normal

=== } without the extension passes a line of spaces
text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
cursor: 4,1
keys: }
--- vim 9.1
expect-text:
  one
  two
  $
  three
     $
  four
  five
  	$
  six
expect-cursor: 9,3
expect-mode: normal
```

- [ ] **Step 2: Teach the corpus `let:` and pins that need pins**

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java`, replace:

```java
 * @param ext the extensions switched on (spec 8), by their switches ({@code surround}), separated by spaces; empty
 *     for none. The recorder loads each one's plugin, at the commit {@code vim-oracle.properties} pins
 * @param given registers set first
 * @param text the starting lines
```

with:

```java
 * @param ext the extensions switched on (spec 8), by their switches ({@code surround}), separated by spaces; empty
 *     for none. The recorder loads each one's plugin, at the commit {@code vim-oracle.properties} pins
 * @param let {@code :let} commands run before the extensions' plugins load ({@code g:surround_no_mappings = 1}),
 *     one for each {@code let:} line
 * @param given registers set first
 * @param text the starting lines
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java`, replace:

```java
 * @param expected what Vim did, or null when the case has not been recorded
 */
record CorpusCase(String file, String name, String divergent, String set, String ext, Map<Character, Register> given, List<String> text,
                  int line, int col, String keys, List<Character> registers, String window, String folds, boolean output, Expected expected) {
    /**
```

with:

```java
 * @param expected what Vim did, or null when the case has not been recorded
 */
record CorpusCase(String file, String name, String divergent, String set, String ext, List<String> let, Map<Character, Register> given, List<String> text,
                  int line, int col, String keys, List<Character> registers, String window, String folds, boolean output, Expected expected) {
    /**
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java`, replace:

```java

    CorpusCase withExpected(Expected value) {
        return new CorpusCase(file, name, divergent, set, ext, given, text, line, col, keys, registers, window, folds, output, value);
    }
```

with:

```java

    CorpusCase withExpected(Expected value) {
        return new CorpusCase(file, name, divergent, set, ext, let, given, text, line, col, keys, registers, window, folds, output, value);
    }
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java`, replace:

```java
            String divergent = null, set = "", ext = "", keys = null, window = null, folds = null;
            boolean output = false;
            Map<Character, CorpusCase.Register> given = new LinkedHashMap<>();
            List<String> text = null;
```

with:

```java
            String divergent = null, set = "", ext = "", keys = null, window = null, folds = null;
            boolean output = false;
            List<String> let = new ArrayList<>();
            Map<Character, CorpusCase.Register> given = new LinkedHashMap<>();
            List<String> text = null;
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java`, replace:

```java
                    case "set" -> set = value;
                    case "ext" -> ext = value;
                    case "window" -> window = value;
                    case "folds" -> folds = value;
```

with:

```java
                    case "set" -> set = value;
                    case "ext" -> ext = value;
                    case "let" -> let.add(value);
                    case "window" -> window = value;
                    case "folds" -> folds = value;
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java`, replace:

```java
            }
            if (text == null || keys == null) throw error(file, i, "case \"" + name + "\" needs text: and keys:");
            cases.add(new CorpusCase(file, name, divergent, set, ext, given, text, cursorLine, cursorCol, keys, registers, window, folds, output, expected));
        }
        return cases;
```

with:

```java
            }
            if (text == null || keys == null) throw error(file, i, "case \"" + name + "\" needs text: and keys:");
            cases.add(new CorpusCase(file, name, divergent, set, ext, List.copyOf(let), given, text, cursorLine, cursorCol, keys, registers, window, folds, output, expected));
        }
        return cases;
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusRunner.java`, replace:

```java
        editor.view().resize(size[1], size[0]);
        if (!c.set().isBlank()) editor.options().apply(c.set());
        for (String extension : c.extensions()) editor.options().set(extension, true);
        for (var entry : c.given().entrySet()) {
```

with:

```java
        editor.view().resize(size[1], size[0]);
        if (!c.set().isBlank()) editor.options().apply(c.set());
        for (String let : c.let()) {
            String error = editor.ex().sourceLine("let " + let);
            if (error != null) throw new IllegalStateException(c + ": let " + let + ": " + error);
        }
        for (String extension : c.extensions()) editor.options().set(extension, true);
        for (var entry : c.given().entrySet()) {
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java`, replace:

```java
    }

    /**
     * The plugin an extension's cases are recorded against, cloned the first time at the commit
```

with:

```java
    }

    /** @return the pins a plugin needs loaded before it: the names after its commit ({@code name = url commit needed…}) */
    static List<String> needs(String name) {
        String[] parts = pin(name);
        return List.of(parts).subList(Math.min(2, parts.length), parts.length);
    }

    private static String[] pin(String name) {
        if (project == null) throw new IllegalStateException("no vim-oracle.properties above the corpus folder");
        var pins = new java.util.Properties();
        try (var in = Files.newBufferedReader(project.resolve("vim-oracle.properties"))) { pins.load(in); }
        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        String pin = pins.getProperty(name);
        if (pin == null) throw new IllegalStateException("vim-oracle.properties pins no plugin for " + name);
        return pin.trim().split("\\s+");
    }

    /**
     * The plugin an extension's cases are recorded against, cloned the first time at the commit
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java`, replace:

```java

    private static Path clone(String name) throws IOException, InterruptedException {
        if (project == null) throw new IOException("no vim-oracle.properties above the corpus folder");
        var pins = new java.util.Properties();
        try (var in = Files.newBufferedReader(project.resolve("vim-oracle.properties"))) { pins.load(in); }
        String pin = pins.getProperty(name);
        if (pin == null) throw new IllegalStateException("vim-oracle.properties pins no plugin for " + name);
        String[] parts = pin.trim().split("\\s+");
        Path folder = project.resolve("build/vim-oracle").resolve(name);
        if (!Files.isDirectory(folder.resolve(".git"))) {
```

with:

```java

    private static Path clone(String name) throws IOException, InterruptedException {
        String[] parts = pin(name);
        Path folder = project.resolve("build/vim-oracle").resolve(name);
        if (!Files.isDirectory(folder.resolve(".git"))) {
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java`, replace:

```java
        }
        if (!c.set().isBlank()) lines.add("set " + c.set());
        if (!c.extensions().isEmpty()) {
            // The plugins load as Vim's startup would load them, before the file is edited.
            List<Path> plugins = new ArrayList<>();
            plugins.add(oracle("repeat"));
            for (String extension : c.extensions()) plugins.add(oracle(extension.toLowerCase(java.util.Locale.ROOT)));
            for (Path plugin : plugins) lines.add("set rtp^=" + plugin.toString().replace(" ", "\\ "));
            for (Path plugin : plugins)
                lines.add("for s:f in glob(" + Json.quote(plugin + "/plugin/*.vim") + ", 0, 1) | execute 'source' fnameescape(s:f) | endfor");
        }
        for (var entry : c.given().entrySet()) {
```

with:

```java
        }
        if (!c.set().isBlank()) lines.add("set " + c.set());
        for (String let : c.let()) lines.add("let " + let);
        if (!c.extensions().isEmpty()) {
            // The plugins load as Vim's startup would load them, before the file is edited.
            List<Path> plugins = new ArrayList<>();
            plugins.add(oracle("repeat"));
            for (String extension : c.extensions()) {
                String name = extension.toLowerCase(java.util.Locale.ROOT);
                for (String needed : needs(name)) if (!plugins.contains(oracle(needed))) plugins.add(oracle(needed));
                plugins.add(oracle(name));
            }
            for (Path plugin : plugins) lines.add("set rtp^=" + plugin.toString().replace(" ", "\\ "));
            for (Path plugin : plugins)
                lines.add("for s:f in glob(" + Json.quote(plugin + "/plugin/**/*.vim") + ", 0, 1) | execute 'source' fnameescape(s:f) | endfor");
        }
        for (var entry : c.given().entrySet()) {
```

In `plugins/editor/vim-oracle.properties`, replace:

```properties
# The Vim plugins the corpus's ext: cases are recorded against (spec 11.1), each pinned to a commit:
# name = git URL, then the commit. recordVimCorpus clones them into build/vim-oracle; check never does.
repeat = https://github.com/tpope/vim-repeat.git 65846025c15494983dafe5e3b46c8f88ab2e9635
surround = https://github.com/tpope/vim-surround.git 3d188ed2113431cf8dac77be61b842acb64433d9
```

with:

```properties
# The Vim plugins the corpus's ext: cases are recorded against (spec 11.1), each pinned to a commit:
# name = git URL, then the commit, then any pins it needs loaded first. recordVimCorpus clones them into
# build/vim-oracle; check never does.
repeat = https://github.com/tpope/vim-repeat.git 65846025c15494983dafe5e3b46c8f88ab2e9635
surround = https://github.com/tpope/vim-surround.git 3d188ed2113431cf8dac77be61b842acb64433d9
```

In `plugins/editor/vim-oracle.properties`, replace:

```properties
exchange = https://github.com/tommcdo/vim-exchange.git d6c1e9790bcb8df27c483a37167459bbebe0112e
replacewithregister = https://github.com/vim-scripts/ReplaceWithRegister.git 832efc23111d19591d495dc72286de2fb0b09345
```

with:

```properties
exchange = https://github.com/tommcdo/vim-exchange.git d6c1e9790bcb8df27c483a37167459bbebe0112e
replacewithregister = https://github.com/vim-scripts/ReplaceWithRegister.git 832efc23111d19591d495dc72286de2fb0b09345
vim-paragraph-motion = https://github.com/dbakker/vim-paragraph-motion.git 2f6b36670327952b0bcc6915f4947feb85977af3
textobj-user = https://github.com/kana/vim-textobj-user.git 41a675ddbeefd6a93664a4dc52f302fe3086a933
textobj-entire = https://github.com/kana/vim-textobj-entire.git 64a856c9dff3425ed8a863b9ec0a21dbaee6fb3a textobj-user
textobj-indent = https://github.com/michaeljsmith/vim-indent-object.git 8ab36d5ec2a3a60468437a95e142ce994df598c6
argtextobj = https://github.com/vim-scripts/argtextobj.vim.git f3fbe427f7b4ec436416a5816d714dc917dc530b
```

- [ ] **Step 3: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: FAIL: every `ext-paragraph-motion.case` case fails with `E518: Unknown option` for `vim-paragraph-motion`, and the divergent case is not in `docs/vim-divergences.md`.

- [ ] **Step 4: Write paragraph-motion**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ParagraphMotion.java`:

```java
package dev.jasper.editor.vim;

/**
 * The paragraph-motion extension (spec 8), ported from Daniel Bakker's paragraphmotion.vim: {@code }} and {@code {}
 * stop at lines holding only white space, not only at empty ones. As the plugin's mappings are {@code :} commands,
 * the motion is exclusive and, unlike Vim's own {@code }}, a delete with it is not put in register 1.
 */
final class ParagraphMotion {
    private ParagraphMotion() { }

    private static final String OPTION = "vim-paragraph-motion";

    static void register(VimCommands commands) {
        commands.motion("<Plug>(ParagraphNextMotion)", "forward [count] paragraphs, lines of white space ending them too (`}`, with `set vim-paragraph-motion`)",
            c -> move(c, true));
        commands.motion("<Plug>(ParagraphPrevMotion)", "back [count] paragraphs, lines of white space ending them too (`{`)", c -> move(c, false));
    }

    /** s:ParagraphMove. */
    private static Target move(Context c, boolean forward) {
        Extensions.require(c, OPTION);
        Buffer b = c.editor.buffer();
        Pos cursor = c.cursor();
        int last = b.count() - 1;
        if (forward) {
            // normal! 0, then for each paragraph: the first non-space from there, and the first white line after it.
            int line = cursor.line;
            for (int i = 0; i < c.count; i++) {
                int text = nonSpaceFrom(b, line);
                int white = text < 0 ? -1 : whiteAfter(b, text);
                // search('\%$', 'W') goes past the last character, and Vim puts the cursor back on it before the
                // operator sees it, so the motion stops short of that character.
                if (white < 0) return Target.exclusive(new Pos(last, Math.max(0, Text.last(b.line(last))))).asJump();
                line = white;
            }
            return Target.exclusive(new Pos(line, 0)).asJump();
        }
        // normal! ^, then for each paragraph: the last non-space at or before there, and the white line before it.
        String here = b.line(cursor.line);
        int col = Math.min(Text.firstNonBlank(here), Math.max(0, here.length() - 1));
        int line = nonSpaceBack(b, cursor.line, col);
        for (int i = 0; i < c.count; i++) {
            int white = line < 0 ? -1 : whiteBefore(b, line);
            if (white < 0) return Target.exclusive(new Pos(0, 0)).asJump();
            line = i + 1 < c.count ? nonSpaceBack(b, white, 0) : white;
        }
        return Target.exclusive(new Pos(line, 0)).asJump();
    }

    /** [[:space:]]: isspace(). */
    private static boolean space(char ch) { return ch == ' ' || ch == '\t' || ch == '\n' || ch == 0x0b || ch == '\f' || ch == '\r'; }

    /** {@code ^[[:space:]]*$}. */
    private static boolean white(String line) {
        for (int i = 0; i < line.length(); i++) if (!space(line.charAt(i))) return false;
        return true;
    }

    /** search('[^[:space:]]', 'Wc') from the start of {@code line}: the line of the first non-space, or -1. */
    private static int nonSpaceFrom(Buffer b, int line) {
        for (int n = line; n < b.count(); n++) if (!white(b.line(n))) return n;
        return -1;
    }

    /** search('^[[:space:]]*$', 'W') from a line with text: the next white line, or -1. */
    private static int whiteAfter(Buffer b, int line) {
        for (int n = line + 1; n < b.count(); n++) if (white(b.line(n))) return n;
        return -1;
    }

    /** search('[^[:space:]]', 'bcW') from {@code col}: the line of the last non-space at or before it, or -1. */
    private static int nonSpaceBack(Buffer b, int line, int col) {
        String text = b.line(line);
        for (int i = Math.min(col, text.length() - 1); i >= 0; i--) if (!space(text.charAt(i))) return line;
        for (int n = line - 1; n >= 0; n--) if (!white(b.line(n))) return n;
        return -1;
    }

    /** search('^[[:space:]]*$', 'bW') from a line with text: the white line before it, or -1. */
    private static int whiteBefore(Buffer b, int line) {
        for (int n = line - 1; n >= 0; n--) if (white(b.line(n))) return n;
        return -1;
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("ttimeoutlen", "ttm", Type.NUMBER, -1, false),
        new Def("undolevels", "ul", Type.NUMBER, 1000, false),
        new Def("visualbell", "vb", Type.BOOLEAN, false, false),
        new Def("whichwrap", "ww", Type.STRING, "b,s", false),
```

with:

```java
        new Def("ttimeoutlen", "ttm", Type.NUMBER, -1, false),
        new Def("undolevels", "ul", Type.NUMBER, 1000, false),
        new Def("vim-paragraph-motion", "vim-paragraph-motion", Type.BOOLEAN, false, false),
        new Def("visualbell", "vb", Type.BOOLEAN, false, false),
        new Def("whichwrap", "ww", Type.STRING, "b,s", false),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        }
        int len = 0;
        while (i + len < arg.length() && (isAlnum(arg.charAt(i + len)) || arg.charAt(i + len) == '_')) len++;
        Def def = def(arg.substring(i, i + len)).orElse(null);
        char afterchar = i + len < arg.length() ? arg.charAt(i + len) : 0;
```

with:

```java
        }
        int len = 0;
        // IdeaVim's extension switches have hyphens (textobj-entire); a hyphen before '=' is the -= operator.
        while (i + len < arg.length() && (isAlnum(arg.charAt(i + len)) || arg.charAt(i + len) == '_'
            || arg.charAt(i + len) == '-' && len > 0 && i + len + 1 < arg.length() && isAlpha(arg.charAt(i + len + 1))))
            len++;
        Def def = def(arg.substring(i, i + len)).orElse(null);
        char afterchar = i + len < arg.length() ? arg.charAt(i + len) : 0;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, replace:

```java
    record Extension(String option, String repository, String noMappings, Check check, List<String[]> mappings) { }

    /** How a plugin checks hasmapto() before it maps. */
    enum Check { NONE, ALL, EACH }

    private static String[] map(String command, String lhs, String rhs) { return new String[] { command, lhs, rhs }; }
```

with:

```java
    record Extension(String option, String repository, String noMappings, Check check, List<String[]> mappings) { }

    /**
     * How a plugin checks before it maps: not at all, hasmapto() once for all its mappings or for each, or with
     * {@code <unique>}, which leaves out a key the user mapped already in that mode.
     */
    enum Check { NONE, ALL, EACH, UNIQUE }

    private static String[] map(String command, String lhs, String rhs) { return new String[] { command, lhs, rhs }; }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, replace:

```java
        new Extension("replacewithregister", "vim-scripts/ReplaceWithRegister", null, Check.EACH, List.of(
            map("nmap", "gr", "<Plug>ReplaceWithRegisterOperator"), map("nmap", "grr", "<Plug>ReplaceWithRegisterLine"),
            map("xmap", "gr", "<Plug>ReplaceWithRegisterVisual"))));

    private final Vim vim;
```

with:

```java
        new Extension("replacewithregister", "vim-scripts/ReplaceWithRegister", null, Check.EACH, List.of(
            map("nmap", "gr", "<Plug>ReplaceWithRegisterOperator"), map("nmap", "grr", "<Plug>ReplaceWithRegisterLine"),
            map("xmap", "gr", "<Plug>ReplaceWithRegisterVisual"))),
        new Extension("vim-paragraph-motion", "dbakker/vim-paragraph-motion", null, Check.UNIQUE, List.of(
            map("nmap", "}", "<Plug>(ParagraphNextMotion)"), map("omap", "}", "<Plug>(ParagraphNextMotion)"),
            map("xmap", "}", "<Plug>(ParagraphNextMotion)"), map("nmap", "{", "<Plug>(ParagraphPrevMotion)"),
            map("omap", "{", "<Plug>(ParagraphPrevMotion)"), map("xmap", "{", "<Plug>(ParagraphPrevMotion)"))));

    private final Vim vim;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, replace:

```java
            if (extension.check() == Check.EACH && mappings.all().stream().anyMatch(existing -> (existing.modes & modes) != 0 && contains(existing.rhs, rhs)))
                continue;
            made.add(ExMaps.define(mappings, modes, lhs, rhs, false, false));
        }
```

with:

```java
            if (extension.check() == Check.EACH && mappings.all().stream().anyMatch(existing -> (existing.modes & modes) != 0 && contains(existing.rhs, rhs)))
                continue;
            // <unique>: the key is mapped already in the mode (E227 in Vim, which the plugin's loading shows).
            if (extension.check() == Check.UNIQUE && mappings.all().stream().anyMatch(existing -> (existing.modes & modes) != 0 && existing.lhs.equals(lhs)))
                continue;
            made.add(ExMaps.define(mappings, modes, lhs, rhs, false, false));
        }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, replace:

```java
        Surround.register(commands);
        Exchange.register(commands);
        return commands;
    }
```

with:

```java
        Surround.register(commands);
        Exchange.register(commands);
        ParagraphMotion.register(commands);
        return commands;
    }
```

List the divergent case in `docs/vim-divergences.md`'s table, after its last row:

In `docs/vim-divergences.md`, replace:

```markdown
| macros.case | q colon is not a register | Jasper has no command-line window; q: fails (docs/vim-divergences.md) |
```

with:

```markdown
| macros.case | q colon is not a register | Jasper has no command-line window; q: fails (docs/vim-divergences.md) |
| ext-paragraph-motion.case | the default mappings | the extension's mappings go to its <Plug> commands, where the plugin's call its function (docs/vim-divergences.md) |
```

- [ ] **Step 5: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains paragraph-motion's two commands.

- [ ] **Step 6: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.vim.TypeaheadTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.ExtensionsTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 228 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 3897 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`: the Editor compiles without warnings (`-Xlint:all`).

- [ ] **Step 7: Check the recording (needs `vim`, `git` and the network)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: `git status` lists only `ext-paragraph-motion.case` (`??`), unchanged from what you created: Vim with the pinned plugin agrees with every expectation.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v4b
git add plugins/editor/src/main/java/dev/jasper/editor/vim/ParagraphMotion.java plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusRunner.java plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java plugins/editor/src/test/resources/vim/corpus/ext-paragraph-motion.case plugins/editor/vim-oracle.properties docs/vim-divergences.md docs/editor-vim-reference.md
git commit -m "Add the paragraph-motion extension, IdeaVim switch names, and let: for the corpus

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: textobj-entire, and objects that select as the plugins do

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/TextobjEntire.java`, `plugins/editor/src/test/resources/vim/corpus/ext-textobj-entire.case`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Script.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Consumes: Task 1's `Check.UNIQUE`; `TextObjects.VISUAL_DONE`; `Visual.operate(context, operator, linewise)`.
- Produces: `Script.select(context, wise, head, tail) -> Span` (Visual mode `v`, `V` or CTRL-V from `head` to `tail`; returns `TextObjects.VISUAL_DONE`); in the Machine, an object that returns `VISUAL_DONE` under a pending operator has the operator applied to that Visual area (a forced CTRL-V makes it a block; `.` repeats the keys); the option `textobj-entire`; `<Plug>(textobj-entire-a)`, `<Plug>(textobj-entire-i)`.

textobj-user runs the plugin's function from an omap or xmap `:<C-u>call`, and selects what it returns in Visual mode (`normal! V`, the start, `o`, the end). Vim then applies the pending operator to the Visual area. The pin for textobj-entire names textobj-user, which the recorder loads first.

- [ ] **Step 1: Write the failing cases**

Create `plugins/editor/src/test/resources/vim/corpus/ext-textobj-entire.case`:

```text
# textobj-entire (plan V4b): ae and ie, the whole text.

=== dae deletes every line
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: dae
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "\n  one\ntwo\n\nthree  \n\n\n"
expect-register 0: none
expect-register 1: l "\n  one\ntwo\n\nthree  \n\n\n"
expect-register -: none

=== die keeps the empty lines at either end
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: die
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
  $
  $
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "  one\ntwo\n\nthree  \n"
expect-register 0: none
expect-register 1: l "  one\ntwo\n\nthree  \n"
expect-register -: none

=== yae keeps the cursor
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: yae
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
    one
  two
  $
  three  $
  $
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "\n  one\ntwo\n\nthree  \n\n\n"
expect-register 0: l "\n  one\ntwo\n\nthree  \n\n\n"
expect-register 1: none
expect-register -: none

=== yie
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: yie
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
    one
  two
  $
  three  $
  $
  $
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "  one\ntwo\n\nthree  \n"
expect-register 0: l "  one\ntwo\n\nthree  \n"
expect-register 1: none
expect-register -: none

=== cae
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: caex<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  x
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "\n  one\ntwo\n\nthree  \n\n\n"
expect-register 0: none
expect-register 1: l "\n  one\ntwo\n\nthree  \n\n\n"
expect-register -: none

=== cie
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: ciex<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
  x
  $
  $
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "  one\ntwo\n\nthree  \n"
expect-register 0: none
expect-register 1: l "  one\ntwo\n\nthree  \n"
expect-register -: none

=== vae selects every line
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: vae
--- vim 9.1
expect-text:
  $
    one
  two
  $
  three  $
  $
  $
expect-cursor: 7,1
expect-mode: visual-line
expect-visual: 1,1

=== vie
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: vie
--- vim 9.1
expect-text:
  $
    one
  two
  $
  three  $
  $
  $
expect-cursor: 5,7
expect-mode: visual-line
expect-visual: 2,1

=== Vie then d
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: Vied
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
  $
  $
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "  one\ntwo\n\nthree  \n"
expect-register 0: none
expect-register 1: l "  one\ntwo\n\nthree  \n"
expect-register -: none

=== vae then o
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: vaeo
--- vim 9.1
expect-text:
  $
    one
  two
  $
  three  $
  $
  $
expect-cursor: 1,1
expect-mode: visual-line
expect-visual: 7,1

=== dvae deletes charwise
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: dvae
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "\n  one\ntwo\n\nthree  \n\n\n"
expect-register 0: none
expect-register 1: l "\n  one\ntwo\n\nthree  \n\n\n"
expect-register -: none

=== dvie deletes charwise
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: dvie
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
  $
  $
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "  one\ntwo\n\nthree  \n"
expect-register 0: none
expect-register 1: l "  one\ntwo\n\nthree  \n"
expect-register -: none

=== d CTRL-V ie deletes a block
text:
  ab
  cd
  ef
cursor: 2,2
ext: textobj-entire
keys: d<C-v>ie
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
  $
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": b2 "ab\ncd\nef"
expect-register 0: none
expect-register 1: b2 "ab\ncd\nef"
expect-register -: none

=== gUie
text:
  one
  two
cursor: 1,1
ext: textobj-entire
keys: gUie
--- vim 9.1
expect-text:
  ONE
  TWO
expect-cursor: 1,1
expect-mode: normal

=== >ae
text:
  one
  two
cursor: 1,1
ext: textobj-entire
keys: >ae
--- vim 9.1
expect-text:
  	one
  	two
expect-cursor: 1,2
expect-mode: normal

=== dae then u
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: daeu
--- vim 9.1
expect-text:
  $
    one
  two
  $
  three  $
  $
  $
expect-cursor: 1,1
expect-mode: normal

=== die then dot
text:
  $
  a
  b
  $
  c
  $
cursor: 2,1
ext: textobj-entire
keys: dieu4G.
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
  $
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "a\nb\n\nc\n"
expect-register 0: none
expect-register 1: l "a\nb\n\nc\n"
expect-register -: none

=== backtick backtick after yae
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: yae``
--- vim 9.1
expect-text:
  $
    one
  two
  $
  three  $
  $
  $
expect-cursor: 3,2
expect-mode: normal

=== gv after yae
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: yaegv
--- vim 9.1
expect-text:
  $
    one
  two
  $
  three  $
  $
  $
expect-cursor: 7,1
expect-mode: visual-line
expect-visual: 1,1

=== dae on the one empty line
text:
  $
cursor: 1,1
ext: textobj-entire
keys: dae
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "\n"
expect-register 0: none
expect-register 1: l "\n"
expect-register -: none

=== die on the one empty line
text:
  $
cursor: 1,1
ext: textobj-entire
keys: die
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "\n"
expect-register 0: none
expect-register 1: l "\n"
expect-register -: none

=== die on empty lines only
text:
  $
  $
  $
cursor: 2,1
ext: textobj-entire
keys: die
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "\n\n\n"
expect-register 0: none
expect-register 1: l "\n\n\n"
expect-register -: none

=== yie on lines of spaces
text:
    $
   $
cursor: 1,1
ext: textobj-entire
keys: yie
registers: " 0 1 -
--- vim 9.1
expect-text:
    $
   $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "  \n \n"
expect-register 0: l "  \n \n"
expect-register 1: none
expect-register -: none

=== die on one line
text:
  only
cursor: 1,3
ext: textobj-entire
keys: die
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "only\n"
expect-register 0: none
expect-register 1: l "only\n"
expect-register -: none

=== ae with a count
text:
  $
    one
  two
  $
  three  $
  $
  $
cursor: 3,2
ext: textobj-entire
keys: 2dae
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "\n  one\ntwo\n\nthree  \n\n\n"
expect-register 0: none
expect-register 1: l "\n  one\ntwo\n\nthree  \n\n\n"
expect-register -: none

=== the default mappings
text:
  a
cursor: 1,1
ext: textobj-entire
keys: :omap ae<CR>:xmap ie<CR>
output: yes
--- vim 9.1
expect-text:
  a
expect-cursor: 1,1
expect-mode: normal
expect-output:
  o  ae            <Plug>(textobj-entire-a)
  x  ie            <Plug>(textobj-entire-i)

=== no default mappings
text:
  a
  b
cursor: 1,1
ext: textobj-entire
let: g:textobj_entire_no_default_key_mappings = 1
keys: dae
registers: " 0 1 -
--- vim 9.1
expect-text:
  a
  b
expect-cursor: 1,1
expect-mode: normal
expect-register ": none
expect-register 0: none
expect-register 1: none
expect-register -: none

=== an own mapping of the plug
text:
  a
  b
cursor: 1,1
ext: textobj-entire
keys: :omap Q <lt>Plug>(textobj-entire-a)<CR>dQ
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "a\nb\n"
expect-register 0: none
expect-register 1: l "a\nb\n"
expect-register -: none
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: FAIL: the `ext-textobj-entire.case` cases fail with `E518: Unknown option` for `textobj-entire`.

- [ ] **Step 3: Write textobj-entire and the selection**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/TextobjEntire.java`:

```java
package dev.jasper.editor.vim;

/**
 * The textobj-entire extension (spec 8), ported from Kana Natsuno's textobj-entire 0.0.4 on vim-textobj-user:
 * {@code ae} is every line, {@code ie} every line but the empty ones at either end. Both select linewise, as the
 * plugin's functions return (Vim 9.1 has no v:motion_force for textobj-user to follow), and both set the jump mark
 * first.
 */
final class TextobjEntire {
    private TextobjEntire() { }

    private static final String OPTION = "textobj-entire";

    static void register(VimCommands commands) {
        commands.object("<Plug>(textobj-entire-a)", "the whole text (`ae`, with `set textobj-entire`)", c -> select(c, false));
        commands.object("<Plug>(textobj-entire-i)", "the whole text but the empty lines at either end (`ie`)", c -> select(c, true));
    }

    /** textobj#entire#select_a and select_i, through textobj-user's s:select_function_wrapper. */
    private static Span select(Context c, boolean inner) {
        Extensions.require(c, OPTION);
        Buffer b = c.editor.buffer();
        // normal! m'
        c.editor.marks().setpcmark(c.editor.cursor());
        int last = b.count() - 1;
        // keepjumps normal! gg0, then for ie search('^.', 'cW'): the first line with a character, if any.
        int first = 0;
        if (inner) for (int n = 0; n <= last; n++) if (!b.line(n).isEmpty()) {
            first = n;
            break;
        }
        // keepjumps normal! G$, then for ie search('^.', 'bcW') and normal! $: the last line with a character.
        int end = last;
        if (inner) for (int n = last; n >= 0; n--) if (!b.line(n).isEmpty()) {
            end = n;
            break;
        }
        return Script.select(c, "V", new Pos(first, 0), new Pos(end, Math.max(0, Text.last(b.line(end)))));
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Script.java`, replace:

```java
    }

    /** @return v:register: the register typed, or {@code "} (the clipboard's with 'clipboard' holding unnamed) */
    static char register(Context c) {
```

with:

```java
    }

    /**
     * A text object's selection, as a plugin makes it from an omap's or xmap's {@code :call}: Visual mode from
     * {@code head} to {@code tail} ({@code normal! V}, {@code cursor(head)}, {@code o}, {@code cursor(tail)}). After an
     * operator, Vim then applies the operator to that Visual area, and so does the Machine.
     *
     * @param wise {@code v}, {@code V} or CTRL-V
     * @return {@link TextObjects#VISUAL_DONE}
     */
    static Span select(Context c, String wise, Pos head, Pos tail) {
        VimEditor editor = c.editor;
        Mode mode = wise.equals("V") ? Mode.VISUAL_LINE : wise.equals("\u0016") ? Mode.VISUAL_BLOCK : Mode.VISUAL;
        editor.visualAnchor(head.copy());
        editor.mode(mode);
        editor.setCursor(tail.copy(), true);
        return TextObjects.VISUAL_DONE;
    }

    /** @return v:register: the register typed, or {@code "} (the clipboard's with 'clipboard' holding unnamed) */
    static char register(Context c) {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
    /** For a Visual-mode change being recorded, the size of its area. */
    private Visual.Redo recordVisual, visualRedo;
    /** The command line typed for the motion being run ({@code /pattern}), or null. */
    private String line;
```

with:

```java
    /** For a Visual-mode change being recorded, the size of its area. */
    private Visual.Redo recordVisual, visualRedo;
    /** Whether the Visual area an operator is about to act on is one an extension's object selected. */
    private boolean objectArea;
    /** The command line typed for the motion being run ({@code /pattern}), or null. */
    private String line;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
        editor.mode(Mode.NORMAL);
        operator = operatorKeys;
        visualRedo = range.redo();
        Span span = range.span();
        switch (operatorKeys) {
```

with:

```java
        editor.mode(Mode.NORMAL);
        operator = operatorKeys;
        visualRedo = objectArea ? null : range.redo();
        objectArea = false;
        Span span = range.span();
        switch (operatorKeys) {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
                Span span = ((VimCommands.TextObject) entry.handler()).select(context);
                if (span == null) throw Context.fail();
                applyOperator(context, span);
            }
            case ACTION -> {
```

with:

```java
                Span span = ((VimCommands.TextObject) entry.handler()).select(context);
                if (span == null) throw Context.fail();
                // An extension's object selects as its plugin's omap does, in Visual mode; the operator then acts
                // on the Visual area, as do_pending_operator does when a mapping leaves Visual mode active: a forced
                // CTRL-V makes the area a block (v and V are not applied to a Visual area), and as the operator
                // ended with the mapping's : command, . runs the keys again rather than acting on as much text.
                if (span == TextObjects.VISUAL_DONE && editor.mode().visual()) {
                    if (forced == CTRL_V) editor.mode(Mode.VISUAL_BLOCK);
                    objectArea = true;
                    Visual.operate(context, operator, false);
                } else applyOperator(context, span);
            }
            case ACTION -> {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("syntax", "syn", Type.STRING, "", true),
        new Def("tabstop", "ts", Type.NUMBER, 8, true),
        new Def("textwidth", "tw", Type.NUMBER, 0, true),
        new Def("tildeop", "top", Type.BOOLEAN, false, false),
```

with:

```java
        new Def("syntax", "syn", Type.STRING, "", true),
        new Def("tabstop", "ts", Type.NUMBER, 8, true),
        new Def("textobj-entire", "textobj-entire", Type.BOOLEAN, false, false),
        new Def("textwidth", "tw", Type.NUMBER, 0, true),
        new Def("tildeop", "top", Type.BOOLEAN, false, false),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, replace:

```java
            map("nmap", "}", "<Plug>(ParagraphNextMotion)"), map("omap", "}", "<Plug>(ParagraphNextMotion)"),
            map("xmap", "}", "<Plug>(ParagraphNextMotion)"), map("nmap", "{", "<Plug>(ParagraphPrevMotion)"),
            map("omap", "{", "<Plug>(ParagraphPrevMotion)"), map("xmap", "{", "<Plug>(ParagraphPrevMotion)"))));

    private final Vim vim;
```

with:

```java
            map("nmap", "}", "<Plug>(ParagraphNextMotion)"), map("omap", "}", "<Plug>(ParagraphNextMotion)"),
            map("xmap", "}", "<Plug>(ParagraphNextMotion)"), map("nmap", "{", "<Plug>(ParagraphPrevMotion)"),
            map("omap", "{", "<Plug>(ParagraphPrevMotion)"), map("xmap", "{", "<Plug>(ParagraphPrevMotion)"))),
        new Extension("textobj-entire", "kana/vim-textobj-entire", "textobj_entire_no_default_key_mappings", Check.UNIQUE, List.of(
            map("xmap", "ae", "<Plug>(textobj-entire-a)"), map("omap", "ae", "<Plug>(textobj-entire-a)"),
            map("xmap", "ie", "<Plug>(textobj-entire-i)"), map("omap", "ie", "<Plug>(textobj-entire-i)"))));

    private final Vim vim;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, replace:

```java
        Exchange.register(commands);
        ParagraphMotion.register(commands);
        return commands;
    }
```

with:

```java
        Exchange.register(commands);
        ParagraphMotion.register(commands);
        TextobjEntire.register(commands);
        return commands;
    }
```

- [ ] **Step 4: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains textobj-entire's two objects.

- [ ] **Step 5: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.vim.TypeaheadTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.ExtensionsTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 228 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 3935 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`.

- [ ] **Step 6: Check the recording (needs `vim`, `git` and the network)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: `git status` lists only `ext-textobj-entire.case` (`??`), unchanged from what you created; `plugins/editor/build/vim-oracle` now holds `textobj-user` too.

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/vim-v4b
git add plugins/editor/src/main/java/dev/jasper/editor/vim/TextobjEntire.java plugins/editor/src/main/java/dev/jasper/editor/vim/Script.java plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java plugins/editor/src/test/resources/vim/corpus/ext-textobj-entire.case docs/editor-vim-reference.md
git commit -m "Add the textobj-entire extension; extension objects select as their plugins do

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: textobj-indent

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/TextobjIndent.java`, `plugins/editor/src/test/resources/vim/corpus/ext-textobj-indent.case`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Consumes: Task 2's `Script.select` and the Machine's object areas.
- Produces: `Extensions.script(type, make)` (an extension's script variables, one set for each Vim); the option `textobj-indent`; `<Plug>textobj-indent-ai`, `-aI`, `-ii`, `-iI`.

vim-indent-object keeps what it selected last in script variables, so that `ii` again in Visual mode takes the enclosing block; a linewise area's `'<` and `'>` read as columns 1 and MAXCOL. `g:indent_object_except_first_level` (1) keeps the blank lines between top-level blocks apart.

- [ ] **Step 1: Write the failing cases**

Create `plugins/editor/src/test/resources/vim/corpus/ext-textobj-indent.case`:

```text
# textobj-indent (plan V4b): ii, ai, aI and iI, blocks of lines by indent.

=== dii deletes the block
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: dii
registers: " 0 1 -
--- vim 9.1
expect-text:
  def f():
      if x:
      return a
  $
  def g():
      pass
expect-cursor: 3,5
expect-mode: normal
expect-register ": l "        a = 1\n\n        b = 2\n"
expect-register 0: none
expect-register 1: l "        a = 1\n\n        b = 2\n"
expect-register -: none

=== dai takes the line above
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: dai
registers: " 0 1 -
--- vim 9.1
expect-text:
  def f():
      return a
  $
  def g():
      pass
expect-cursor: 2,5
expect-mode: normal
expect-register ": l "    if x:\n        a = 1\n\n        b = 2\n"
expect-register 0: none
expect-register 1: l "    if x:\n        a = 1\n\n        b = 2\n"
expect-register -: none

=== daI takes the lines above and below
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: daI
registers: " 0 1 -
--- vim 9.1
expect-text:
  def f():
  $
  def g():
      pass
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "    if x:\n        a = 1\n\n        b = 2\n    return a\n"
expect-register 0: none
expect-register 1: l "    if x:\n        a = 1\n\n        b = 2\n    return a\n"
expect-register -: none

=== diI is dii
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: diI
registers: " 0 1 -
--- vim 9.1
expect-text:
  def f():
      if x:
      return a
  $
  def g():
      pass
expect-cursor: 3,5
expect-mode: normal
expect-register ": l "        a = 1\n\n        b = 2\n"
expect-register 0: none
expect-register 1: l "        a = 1\n\n        b = 2\n"
expect-register -: none

=== dii over a blank line inside
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 5,9
ext: textobj-indent
keys: dii
registers: " 0 1 -
--- vim 9.1
expect-text:
  def f():
      if x:
      return a
  $
  def g():
      pass
expect-cursor: 3,5
expect-mode: normal
expect-register ": l "        a = 1\n\n        b = 2\n"
expect-register 0: none
expect-register 1: l "        a = 1\n\n        b = 2\n"
expect-register -: none

=== dii on the outer block
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 2,5
ext: textobj-indent
keys: dii
registers: " 0 1 -
--- vim 9.1
expect-text:
  def f():
  $
  def g():
      pass
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "    if x:\n        a = 1\n\n        b = 2\n    return a\n"
expect-register 0: none
expect-register 1: l "    if x:\n        a = 1\n\n        b = 2\n    return a\n"
expect-register -: none

=== dai on the outer block
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 6,5
ext: textobj-indent
keys: dai
registers: " 0 1 -
--- vim 9.1
expect-text:
  def g():
      pass
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "def f():\n    if x:\n        a = 1\n\n        b = 2\n    return a\n\n"
expect-register 0: none
expect-register 1: l "def f():\n    if x:\n        a = 1\n\n        b = 2\n    return a\n\n"
expect-register -: none

=== daI on the outer block
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 6,5
ext: textobj-indent
keys: daI
registers: " 0 1 -
--- vim 9.1
expect-text:
      pass
expect-cursor: 1,5
expect-mode: normal
expect-register ": l "def f():\n    if x:\n        a = 1\n\n        b = 2\n    return a\n\ndef g():\n"
expect-register 0: none
expect-register 1: l "def f():\n    if x:\n        a = 1\n\n        b = 2\n    return a\n\ndef g():\n"
expect-register -: none

=== dii on a blank line in a block
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 4,1
ext: textobj-indent
keys: dii
registers: " 0 1 -
--- vim 9.1
expect-text:
  def f():
      if x:
      return a
  $
  def g():
      pass
expect-cursor: 3,5
expect-mode: normal
expect-register ": l "        a = 1\n\n        b = 2\n"
expect-register 0: none
expect-register 1: l "        a = 1\n\n        b = 2\n"
expect-register -: none

=== dai on a blank line between blocks
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 7,1
ext: textobj-indent
keys: dai
registers: " 0 1 -
--- vim 9.1
expect-text:
  def g():
      pass
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "def f():\n    if x:\n        a = 1\n\n        b = 2\n    return a\n\n"
expect-register 0: none
expect-register 1: l "def f():\n    if x:\n        a = 1\n\n        b = 2\n    return a\n\n"
expect-register -: none

=== dii at the top level
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 1,1
ext: textobj-indent
keys: dii
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
          b = 2
      return a
  $
  def g():
      pass
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "def f():\n    if x:\n        a = 1\n"
expect-register 0: none
expect-register 1: l "def f():\n    if x:\n        a = 1\n"
expect-register -: none

=== dai at the top level
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 8,1
ext: textobj-indent
keys: dai
registers: " 0 1 -
--- vim 9.1
expect-text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
expect-cursor: 6,5
expect-mode: normal
expect-register ": l "\ndef g():\n    pass\n"
expect-register 0: none
expect-register 1: l "\ndef g():\n    pass\n"
expect-register -: none

=== dii at the top level with every level
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 1,1
ext: textobj-indent
let: g:indent_object_except_first_level = 0
keys: dii
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "def f():\n    if x:\n        a = 1\n\n        b = 2\n    return a\n\ndef g():\n    pass\n"
expect-register 0: none
expect-register 1: l "def f():\n    if x:\n        a = 1\n\n        b = 2\n    return a\n\ndef g():\n    pass\n"
expect-register -: none

=== dai on the last line
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 9,5
ext: textobj-indent
keys: dai
registers: " 0 1 -
--- vim 9.1
expect-text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
expect-cursor: 7,1
expect-mode: normal
expect-register ": l "def g():\n    pass\n"
expect-register 0: none
expect-register 1: l "def g():\n    pass\n"
expect-register -: none

=== yii keeps the cursor at the start
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 5,9
ext: textobj-indent
keys: yii
registers: " 0 1 -
--- vim 9.1
expect-text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
expect-cursor: 3,1
expect-mode: normal
expect-register ": l "        a = 1\n\n        b = 2\n"
expect-register 0: l "        a = 1\n\n        b = 2\n"
expect-register 1: none
expect-register -: none

=== cii
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: ciix<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  def f():
      if x:
  x
      return a
  $
  def g():
      pass
expect-cursor: 3,1
expect-mode: normal
expect-register ": l "        a = 1\n\n        b = 2\n"
expect-register 0: none
expect-register 1: l "        a = 1\n\n        b = 2\n"
expect-register -: none

=== >ii
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: >ii
--- vim 9.1
expect-text:
  def f():
      if x:
  		a = 1
  $
  		b = 2
      return a
  $
  def g():
      pass
expect-cursor: 3,3
expect-mode: normal

=== 2dii takes the enclosing block
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: 2dii
registers: " 0 1 -
--- vim 9.1
expect-text:
  def f():
      if x:
      return a
  $
  def g():
      pass
expect-cursor: 3,5
expect-mode: normal
expect-register ": l "        a = 1\n\n        b = 2\n"
expect-register 0: none
expect-register 1: l "        a = 1\n\n        b = 2\n"
expect-register -: none

=== vii selects the block
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: vii
--- vim 9.1
expect-text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
expect-cursor: 5,1
expect-mode: visual-line
expect-visual: 3,1

=== vii then ii grows it
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: viiii
--- vim 9.1
expect-text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
expect-cursor: 6,1
expect-mode: visual-line
expect-visual: 2,1

=== vii then ii then ii
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: viiiiii
--- vim 9.1
expect-text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
expect-cursor: 6,1
expect-mode: visual-line
expect-visual: 1,1

=== vai
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: vai
--- vim 9.1
expect-text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
expect-cursor: 5,1
expect-mode: visual-line
expect-visual: 2,1

=== vaI
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: vaI
--- vim 9.1
expect-text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
expect-cursor: 6,1
expect-mode: visual-line
expect-visual: 2,1

=== Vii
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: Vii
--- vim 9.1
expect-text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
expect-cursor: 5,1
expect-mode: visual-line
expect-visual: 3,1

=== vii then d
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: viid
registers: " 0 1 -
--- vim 9.1
expect-text:
  def f():
      if x:
      return a
  $
  def g():
      pass
expect-cursor: 3,5
expect-mode: normal
expect-register ": l "        a = 1\n\n        b = 2\n"
expect-register 0: none
expect-register 1: l "        a = 1\n\n        b = 2\n"
expect-register -: none

=== v over two lines then ii
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 2,5
ext: textobj-indent
keys: vjii
--- vim 9.1
expect-text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
expect-cursor: 6,1
expect-mode: visual-line
expect-visual: 2,1

=== v2ii
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: v2ii
--- vim 9.1
expect-text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
expect-cursor: 5,1
expect-mode: visual-line
expect-visual: 3,1

=== dii with tabs
text:
  if a {
  	if b {
  		x
  	}
  }
cursor: 3,3
ext: textobj-indent
keys: dii
registers: " 0 1 -
--- vim 9.1
expect-text:
  if a {
  	if b {
  	}
  }
expect-cursor: 3,2
expect-mode: normal
expect-register ": l "\t\tx\n"
expect-register 0: none
expect-register 1: l "\t\tx\n"
expect-register -: none

=== dai with tabs
text:
  if a {
  	if b {
  		x
  	}
  }
cursor: 3,3
ext: textobj-indent
keys: dai
registers: " 0 1 -
--- vim 9.1
expect-text:
  if a {
  	}
  }
expect-cursor: 2,2
expect-mode: normal
expect-register ": l "\tif b {\n\t\tx\n"
expect-register 0: none
expect-register 1: l "\tif b {\n\t\tx\n"
expect-register -: none

=== daI with tabs
text:
  if a {
  	if b {
  		x
  	}
  }
cursor: 3,3
ext: textobj-indent
keys: daI
registers: " 0 1 -
--- vim 9.1
expect-text:
  if a {
  }
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "\tif b {\n\t\tx\n\t}\n"
expect-register 0: none
expect-register 1: l "\tif b {\n\t\tx\n\t}\n"
expect-register -: none

=== dii then dot
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: diij.
registers: " 0 1 -
--- vim 9.1
expect-text:
  def f():
  $
  def g():
      pass
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "    if x:\n    return a\n"
expect-register 0: none
expect-register 1: l "    if x:\n    return a\n"
expect-register -: none

=== dii then u
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: diiu
--- vim 9.1
expect-text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
expect-cursor: 3,1
expect-mode: normal

=== dii on the one empty line
text:
  $
cursor: 1,1
ext: textobj-indent
keys: dii
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "\n"
expect-register 0: none
expect-register 1: l "\n"
expect-register -: none

=== dai on the one empty line
text:
  $
cursor: 1,1
ext: textobj-indent
keys: dai
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "\n"
expect-register 0: none
expect-register 1: l "\n"
expect-register -: none

=== vii on the one empty line
text:
  $
cursor: 1,1
ext: textobj-indent
keys: vii
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: visual-line
expect-visual: 1,1

=== dii on one line
text:
  only
cursor: 1,2
ext: textobj-indent
keys: dii
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "only\n"
expect-register 0: none
expect-register 1: l "only\n"
expect-register -: none

=== dii on the last line of a block at the end
text:
  a
    b
    c
cursor: 3,3
ext: textobj-indent
keys: dii
registers: " 0 1 -
--- vim 9.1
expect-text:
  a
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "  b\n  c\n"
expect-register 0: none
expect-register 1: l "  b\n  c\n"
expect-register -: none

=== dai on the last line of a block at the end
text:
  a
    b
    c
cursor: 3,3
ext: textobj-indent
keys: dai
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "a\n  b\n  c\n"
expect-register 0: none
expect-register 1: l "a\n  b\n  c\n"
expect-register -: none

=== daI on the last line of a block at the end
text:
  a
    b
    c
cursor: 3,3
ext: textobj-indent
keys: daI
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "a\n  b\n  c\n"
expect-register 0: none
expect-register 1: l "a\n  b\n  c\n"
expect-register -: none

=== dii on blank lines only
text:
  $
    $
  $
cursor: 2,1
ext: textobj-indent
keys: dii
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
  $
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "  \n"
expect-register 0: none
expect-register 1: l "  \n"
expect-register -: none

=== dii with a blank last line
text:
  a
    b
  $
cursor: 2,3
ext: textobj-indent
keys: dii
registers: " 0 1 -
--- vim 9.1
expect-text:
  a
  $
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "  b\n"
expect-register 0: none
expect-register 1: l "  b\n"
expect-register -: none

=== gv after dii
text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
cursor: 3,9
ext: textobj-indent
keys: yiigv
--- vim 9.1
expect-text:
  def f():
      if x:
          a = 1
  $
          b = 2
      return a
  $
  def g():
      pass
expect-cursor: 5,1
expect-mode: visual-line
expect-visual: 3,1
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: FAIL: the `ext-textobj-indent.case` cases fail with `E518: Unknown option` for `textobj-indent`.

- [ ] **Step 3: Write textobj-indent**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/TextobjIndent.java`:

```java
package dev.jasper.editor.vim;

/**
 * The textobj-indent extension (spec 8), ported from Michael Smith's vim-indent-object: {@code ii} is the block of
 * lines at the cursor's indent or deeper, {@code ai} also the line above it, {@code aI} the lines above and below,
 * and {@code iI} the same as {@code ii}. The selection is linewise; in Visual mode the object again takes the block
 * around the area, so {@code ii} repeated grows it. As in the plugin, lines are 1-based and columns are counted
 * from 1, and what was selected last is kept between uses ({@code s:l0} to {@code s:c1}).
 */
final class TextobjIndent {
    private static final String OPTION = "textobj-indent";
    /** col() of the {@code '>} mark of a linewise area. */
    private static final int MAXCOL = Integer.MAX_VALUE;

    /** The plugin's script variables, one set for each Vim. */
    private int l0 = -1, l1 = -1, c0 = -1, c1 = -1;

    static void register(VimCommands commands) {
        commands.object("<Plug>textobj-indent-ai", "the lines at the cursor's indent and deeper, and the line above (`ai`, with `set textobj-indent`)",
            c -> select(c, false, false));
        commands.object("<Plug>textobj-indent-aI", "the lines at the cursor's indent and deeper, and the lines above and below (`aI`)",
            c -> select(c, false, true));
        commands.object("<Plug>textobj-indent-ii", "the lines at the cursor's indent and deeper (`ii`)", c -> select(c, true, false));
        commands.object("<Plug>textobj-indent-iI", "the lines at the cursor's indent and deeper (`iI`)", c -> select(c, true, true));
    }

    /** s:HandleTextObjectMapping: the range the mapping passes, the cursor or the Visual area's marks. */
    private static Span select(Context c, boolean inner, boolean incbelow) {
        Extensions.require(c, OPTION);
        VimEditor editor = c.editor;
        int[] range;
        if (c.visual != null) {
            Pos start = Pos.min(c.visual, editor.cursor()), end = Pos.max(c.visual, editor.cursor());
            boolean lines = editor.mode() == Mode.VISUAL_LINE;
            range = new int[] { start.line + 1, end.line + 1, lines ? 1 : start.col + 1, lines ? MAXCOL : end.col + 1 };
        } else {
            Pos p = editor.cursor();
            range = new int[] { p.line + 1, p.line + 1, p.col + 1, p.col + 1 };
        }
        TextobjIndent script = editor.vim().extensions().script(TextobjIndent.class, TextobjIndent::new);
        // :<C-u> ends Visual mode, setting '< and '>.
        if (c.visual != null) Visual.end(editor);
        if (!script.textObject(c, inner, incbelow, range, c.count)) {
            // The plugin returns without selecting: Visual mode gets its area back (the mapping's <Esc>gv), and an
            // operator gets the : motion, which moved nowhere.
            if (c.visual != null) {
                Script.normal(editor, "gv");
                return TextObjects.VISUAL_DONE;
            }
            return new Span(editor.cursor(), editor.cursor(), Target.Kind.EXCLUSIVE);
        }
        return Script.select(c, "V", new Pos(script.l0 - 1, 0), new Pos(script.l1 - 1, 0));
    }

    /** s:TextObject; returns false where the plugin returns without selecting. */
    private boolean textObject(Context c, boolean inner, boolean incbelow, int[] range, int count) {
        Buffer b = c.editor.buffer();
        int ts = c.tabstop();
        boolean exceptFirstLevel = !String.valueOf(c.vim.variables().getOrDefault("indent_object_except_first_level", 1L)).equals("0");
        boolean newVis = l0 != range[0] || l1 != range[1] || c0 != range[2] || c1 != range[3];
        l0 = range[0];
        l1 = range[1];
        c0 = range[2];
        c1 = range[3];
        int itrCnt = 0, cnt = count;
        while (cnt > 0) {
            // The least indent of the lines selected, blank ones aside.
            int idntInvalid = 1000, idnt = idntInvalid;
            for (int l = l0; l <= l1; l++) if (!blank(getline(b, l))) idnt = Math.min(idnt, indent(b, l, ts));
            int lm1 = l0, lm1o = lm1, l2 = l1, l2o = l2;
            // Only blank lines: the indent of the nearest lines with text, the deeper one.
            if (idnt == idntInvalid) {
                idnt = 0;
                int pnb = prevnonblank(b, l0);
                if (pnb != 0) {
                    idnt = Math.max(idnt, indent(b, pnb, ts));
                    lm1 = pnb;
                }
                int nnb = nextnonblank(b, l0);
                if (nnb != 0) idnt = Math.max(idnt, indent(b, nnb, ts));
                if (idnt > indent(b, pnb, ts)) lm1 = nnb;
                if (idnt > indent(b, nnb, ts)) l2 = pnb;
            }
            // Up to the first line with less indent, blank lines aside.
            boolean blnk = blank(getline(b, lm1));
            while (lm1 > 0 && (blnk || indent(b, lm1, ts) >= idnt)) {
                if (exceptFirstLevel && idnt == 0 && blnk) break;
                if (!blnk || !inner) lm1o = lm1;
                lm1--;
                blnk = blank(getline(b, lm1));
            }
            // Down to the first line with less indent, blank lines aside.
            int lineCnt = b.count();
            blnk = blank(getline(b, l2));
            while (l2 <= lineCnt && (blnk || indent(b, l2, ts) >= idnt)) {
                if (exceptFirstLevel && idnt == 0 && blnk) break;
                if (!blnk || !inner) l2o = l2;
                l2++;
                blnk = blank(getline(b, l2));
            }
            // The lines around the block: neither for inner, the one below only for aI.
            int idnt2 = Math.max(indent(b, lm1, ts), indent(b, l2, ts));
            if (indent(b, lm1, ts) < idnt2 || inner) lm1 = lm1o;
            if (indent(b, l2, ts) < idnt2 || inner || !incbelow) l2 = l2o;
            lm1 = Math.max(lm1, 1);
            l2 = Math.min(l2, b.count());
            int cm1 = 1;
            if (inner) cm1 = firstNonWhite(getline(b, lm1)) + 1;
            int cc2 = getline(b, l2).length();
            if (!inner) cc2++;
            // No real change in linewise mode keeps the columns, so that the selection can grow.
            if (itrCnt == 0 && l0 == lm1 && l1 == l2) {
                cm1 = c0;
                cc2 = c1;
            }
            boolean chg = l0 != lm1 || l1 != l2 || c0 != cm1 || c1 != cc2 || newVis;
            l0 = lm1;
            l1 = l2;
            c0 = cm1;
            c1 = cc2;
            if (chg) cnt--;
            else {
                // Nothing changed: from the line above, to get the enclosing block.
                if (l0 == 0) return false;
                l0--;
                c0 = getline(b, l0).length();
            }
            itrCnt++;
        }
        // The area is selected linewise and left with '< and '> set: col("'<") is 1 and col("'>") MAXCOL.
        c0 = 1;
        c1 = MAXCOL;
        return true;
    }

    /** getline(): empty outside the text. */
    private static String getline(Buffer b, int lnum) { return lnum < 1 || lnum > b.count() ? "" : b.line(lnum - 1); }

    /** indent(): -1 outside the text. */
    private static int indent(Buffer b, int lnum, int ts) { return lnum < 1 || lnum > b.count() ? -1 : Text.indent(b.line(lnum - 1), ts); }

    /** {@code =~ "^\\s*$"}. */
    private static boolean blank(String line) { return Text.firstNonBlank(line) == line.length(); }

    /** match(line, '\S'), -1 for none. */
    private static int firstNonWhite(String line) {
        int col = Text.firstNonBlank(line);
        return col == line.length() ? -1 : col;
    }

    /** prevnonblank(): the line at or above with more than white space, or 0. */
    private static int prevnonblank(Buffer b, int lnum) {
        for (int l = Math.min(lnum, b.count()); l >= 1; l--) if (!blank(b.line(l - 1))) return l;
        return 0;
    }

    /** nextnonblank(): the line at or below with more than white space, or 0. */
    private static int nextnonblank(Buffer b, int lnum) {
        for (int l = Math.max(lnum, 1); l <= b.count(); l++) if (!blank(b.line(l - 1))) return l;
        return 0;
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("tabstop", "ts", Type.NUMBER, 8, true),
        new Def("textobj-entire", "textobj-entire", Type.BOOLEAN, false, false),
        new Def("textwidth", "tw", Type.NUMBER, 0, true),
        new Def("tildeop", "top", Type.BOOLEAN, false, false),
```

with:

```java
        new Def("tabstop", "ts", Type.NUMBER, 8, true),
        new Def("textobj-entire", "textobj-entire", Type.BOOLEAN, false, false),
        new Def("textobj-indent", "textobj-indent", Type.BOOLEAN, false, false),
        new Def("textwidth", "tw", Type.NUMBER, 0, true),
        new Def("tildeop", "top", Type.BOOLEAN, false, false),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, replace:

```java
        new Extension("textobj-entire", "kana/vim-textobj-entire", "textobj_entire_no_default_key_mappings", Check.UNIQUE, List.of(
            map("xmap", "ae", "<Plug>(textobj-entire-a)"), map("omap", "ae", "<Plug>(textobj-entire-a)"),
            map("xmap", "ie", "<Plug>(textobj-entire-i)"), map("omap", "ie", "<Plug>(textobj-entire-i)"))));

    private final Vim vim;
    /** The default mappings each switched-on extension added. */
    private final Map<String, List<Mapping>> added = new HashMap<>();

    Extensions(Vim vim) { this.vim = vim; }
```

with:

```java
        new Extension("textobj-entire", "kana/vim-textobj-entire", "textobj_entire_no_default_key_mappings", Check.UNIQUE, List.of(
            map("xmap", "ae", "<Plug>(textobj-entire-a)"), map("omap", "ae", "<Plug>(textobj-entire-a)"),
            map("xmap", "ie", "<Plug>(textobj-entire-i)"), map("omap", "ie", "<Plug>(textobj-entire-i)"))),
        new Extension("textobj-indent", "michaeljsmith/vim-indent-object", null, Check.NONE, List.of(
            map("omap", "ai", "<Plug>textobj-indent-ai"), map("omap", "ii", "<Plug>textobj-indent-ii"),
            map("xmap", "ai", "<Plug>textobj-indent-ai"), map("xmap", "ii", "<Plug>textobj-indent-ii"),
            map("omap", "aI", "<Plug>textobj-indent-aI"), map("omap", "iI", "<Plug>textobj-indent-iI"),
            map("xmap", "aI", "<Plug>textobj-indent-aI"), map("xmap", "iI", "<Plug>textobj-indent-iI"))));

    private final Vim vim;
    /** The default mappings each switched-on extension added. */
    private final Map<String, List<Mapping>> added = new HashMap<>();
    /** The extensions' script variables ({@code s:}), which last as long as Vim does. */
    private final Map<Class<?>, Object> scripts = new HashMap<>();

    Extensions(Vim vim) { this.vim = vim; }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, replace:

```java
        return ALL.stream().filter(e -> e.repository().toLowerCase(Locale.ROOT).equals(key)).findFirst();
    }

    /** @return whether the extension whose switch this is is on */
```

with:

```java
        return ALL.stream().filter(e -> e.repository().toLowerCase(Locale.ROOT).equals(key)).findFirst();
    }

    /** @return an extension's script variables, made the first time */
    <T> T script(Class<T> type, java.util.function.Supplier<T> make) { return type.cast(scripts.computeIfAbsent(type, key -> make.get())); }

    /** @return whether the extension whose switch this is is on */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, replace:

```java
        ParagraphMotion.register(commands);
        TextobjEntire.register(commands);
        return commands;
    }
```

with:

```java
        ParagraphMotion.register(commands);
        TextobjEntire.register(commands);
        TextobjIndent.register(commands);
        return commands;
    }
```

- [ ] **Step 4: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains textobj-indent's four objects.

- [ ] **Step 5: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.vim.TypeaheadTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.ExtensionsTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 228 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 4017 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`.

- [ ] **Step 6: Check the recording (needs `vim`, `git` and the network)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: `git status` lists only `ext-textobj-indent.case` (`??`), unchanged from what you created.

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/vim-v4b
git add plugins/editor/src/main/java/dev/jasper/editor/vim/TextobjIndent.java plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java plugins/editor/src/test/resources/vim/corpus/ext-textobj-indent.case docs/editor-vim-reference.md
git commit -m "Add the textobj-indent extension

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: argtextobj

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/Argtextobj.java`, `plugins/editor/src/test/resources/vim/corpus/ext-argtextobj.case`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/TextObjects.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/CompletionTest.java`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Consumes: Task 2's `Script.select` and object areas.
- Produces: `TextObjects.aroundQuote(line, col, quote) -> int[]` (what `v` then `a"` selects on any line, `{start, end}` included, or null; `quote()` now finds its pair with the same helpers); `Machine.undoCursor(pos)` (where the undo step's cursor is when an extension's script changed the text before the operator); the option `argtextobj`; `<Plug>InnerArgument`, `<Plug>OuterArgument`.

The plugin replaces every `a"` area on the line with `_` and puts the line back to learn whether the cursor was in a string; the port does it on a copy. Its first change (the replacement, or `setline()`) is where Vim's undo step remembers the cursor, so `u` after `dia` puts it there. `CompletionTest` checks that option names with hyphens complete, now that `textobj-entire`, `textobj-indent` and `argtextobj` exist.

- [ ] **Step 1: Write the failing cases and test**

Create `plugins/editor/src/test/resources/vim/corpus/ext-argtextobj.case`:

```text
# argtextobj (plan V4b): ia and aa, function arguments.

=== dia on the first argument
text:
  foo(one, two, three)
cursor: 1,6
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo(, two, three)
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "one"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "one"

=== dia on the middle argument
text:
  foo(one, two, three)
cursor: 1,11
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo(one, , three)
expect-cursor: 1,10
expect-mode: normal
expect-register ": c "two"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "two"

=== dia on the last argument
text:
  foo(one, two, three)
cursor: 1,17
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo(one, two, )
expect-cursor: 1,15
expect-mode: normal
expect-register ": c "three"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "three"

=== daa on the first argument
text:
  foo(one, two, three)
cursor: 1,6
ext: argtextobj
keys: daa
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo(two, three)
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "one, "
expect-register 0: c ""
expect-register 1: none
expect-register -: c "one, "

=== daa on the middle argument
text:
  foo(one, two, three)
cursor: 1,11
ext: argtextobj
keys: daa
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo(one, three)
expect-cursor: 1,8
expect-mode: normal
expect-register ": c ", two"
expect-register 0: c ""
expect-register 1: none
expect-register -: c ", two"

=== daa on the last argument
text:
  foo(one, two, three)
cursor: 1,17
ext: argtextobj
keys: daa
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo(one, two)
expect-cursor: 1,13
expect-mode: normal
expect-register ": c ", three"
expect-register 0: c ""
expect-register 1: none
expect-register -: c ", three"

=== daa on the only argument
text:
  f(x)
cursor: 1,3
ext: argtextobj
keys: daa
registers: " 0 1 -
--- vim 9.1
expect-text:
  f()
expect-cursor: 1,3
expect-mode: normal
expect-register ": c "x"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "x"

=== dia on the only argument
text:
  f(xyz)
cursor: 1,4
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f()
expect-cursor: 1,3
expect-mode: normal
expect-register ": c "xyz"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "xyz"

=== dia on the opening parenthesis
text:
  foo(one, two, three)
cursor: 1,4
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo(, two, three)
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "one"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "one"

=== dia on a comma
text:
  foo(one, two, three)
cursor: 1,8
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo(one, , three)
expect-cursor: 1,10
expect-mode: normal
expect-register ": c "two"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "two"

=== dia on the space after a comma
text:
  foo(one, two, three)
cursor: 1,9
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo(one, , three)
expect-cursor: 1,10
expect-mode: normal
expect-register ": c "two"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "two"

=== dia on the closing parenthesis
text:
  foo(one, two, three)
cursor: 1,20
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo(one, two, )
expect-cursor: 1,15
expect-mode: normal
expect-register ": c "three"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "three"

=== cia
text:
  foo(one, two, three)
cursor: 1,11
ext: argtextobj
keys: ciaX<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo(one, X, three)
expect-cursor: 1,10
expect-mode: normal
expect-register ": c "two"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "two"

=== yia keeps the cursor at the start
text:
  foo(one, two, three)
cursor: 1,11
ext: argtextobj
keys: yia
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo(one, two, three)
expect-cursor: 1,10
expect-mode: normal
expect-register ": c "two"
expect-register 0: c "two"
expect-register 1: none
expect-register -: none

=== via selects the argument
text:
  foo(one, two, three)
cursor: 1,11
ext: argtextobj
keys: via
--- vim 9.1
expect-text:
  foo(one, two, three)
expect-cursor: 1,12
expect-mode: visual
expect-visual: 1,10

=== vaa selects the argument and its comma
text:
  foo(one, two, three)
cursor: 1,11
ext: argtextobj
keys: vaa
--- vim 9.1
expect-text:
  foo(one, two, three)
expect-cursor: 1,12
expect-mode: visual
expect-visual: 1,8

=== vaa on the first argument
text:
  foo(one, two, three)
cursor: 1,6
ext: argtextobj
keys: vaa
--- vim 9.1
expect-text:
  foo(one, two, three)
expect-cursor: 1,9
expect-mode: visual
expect-visual: 1,5

=== via then d
text:
  foo(one, two, three)
cursor: 1,11
ext: argtextobj
keys: viad
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo(one, , three)
expect-cursor: 1,10
expect-mode: normal
expect-register ": c "two"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "two"

=== dia in a nested call
text:
  f(a, g(b, c), d)
cursor: 1,11
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f(a, g(b, ), d)
expect-cursor: 1,11
expect-mode: normal
expect-register ": c "c"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "c"

=== dia on a nested call
text:
  f(a, g(b, c), d)
cursor: 1,6
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f(a, , d)
expect-cursor: 1,6
expect-mode: normal
expect-register ": c "g(b, c)"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "g(b, c)"

=== daa on a nested call
text:
  f(a, g(b, c), d)
cursor: 1,6
ext: argtextobj
keys: daa
registers: " 0 1 -
--- vim 9.1
expect-text:
  f(a, d)
expect-cursor: 1,4
expect-mode: normal
expect-register ": c ", g(b, c)"
expect-register 0: c ""
expect-register 1: none
expect-register -: c ", g(b, c)"

=== dia on a nested call with force_toplevel
text:
  f(a, g(b, c), d)
cursor: 1,11
ext: argtextobj
keys: :let g:argumentobject_force_toplevel = 1<CR>dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f(a, , d)
expect-cursor: 1,6
expect-mode: normal
expect-register ": c "g(b, c)"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "g(b, c)"

=== dia in parentheses that are not a call
text:
  f(a, (b, c), d)
cursor: 1,8
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f(a, , d)
expect-cursor: 1,6
expect-mode: normal
expect-register ": c "(b, c)"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "(b, c)"

=== dia over a string with a comma
text:
  f("a,b", c)
cursor: 1,3
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f(, c)
expect-cursor: 1,3
expect-mode: normal
expect-register ": c "\"a,b\""
expect-register 0: c ""
expect-register 1: none
expect-register -: c "\"a,b\""

=== dia inside a string with a comma
text:
  f("a,b", c)
cursor: 1,5
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f(, c)
expect-cursor: 1,3
expect-mode: normal
expect-register ": c "\"a,b\""
expect-register 0: c ""
expect-register 1: none
expect-register -: c "\"a,b\""

=== dia on the argument after a string
text:
  f("a,b", c)
cursor: 1,10
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f("a,b", )
expect-cursor: 1,10
expect-mode: normal
expect-register ": c "c"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "c"

=== dia on the space after a string
text:
  f("a,b", c)
cursor: 1,9
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f("a,b", )
expect-cursor: 1,10
expect-mode: normal
expect-register ": c "c"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "c"

=== dia over single quotes
text:
  f('a,b', c)
cursor: 1,11
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f('a,b', )
expect-cursor: 1,10
expect-mode: normal
expect-register ": c "c"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "c"

=== dia over brackets
text:
  f([1, 2], c)
cursor: 1,12
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f([1, 2], )
expect-cursor: 1,11
expect-mode: normal
expect-register ": c "c"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "c"

=== dia on brackets
text:
  f([1, 2], c)
cursor: 1,4
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f(, c)
expect-cursor: 1,3
expect-mode: normal
expect-register ": c "[1, 2]"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "[1, 2]"

=== dia over angle brackets
text:
  f(List<A, B> x, y)
cursor: 1,17
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f(List<A, B> x, )
expect-cursor: 1,17
expect-mode: normal
expect-register ": c "y"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "y"

=== dia with spaces inside
text:
  f(  a  ,  b  )
cursor: 1,4
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f(  ,  b  )
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "a  "
expect-register 0: c ""
expect-register 1: none
expect-register -: c "a  "

=== daa with spaces inside
text:
  f(  a  ,  b  )
cursor: 1,12
ext: argtextobj
keys: daa
registers: " 0 1 -
--- vim 9.1
expect-text:
  f(  a  )
expect-cursor: 1,8
expect-mode: normal
expect-register ": c ",  b  "
expect-register 0: c ""
expect-register 1: none
expect-register -: c ",  b  "

=== dia outside any call
text:
  x = 1, 2
cursor: 1,5
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  x = 1, 2
expect-cursor: 1,5
expect-mode: normal
expect-register ": none
expect-register 0: none
expect-register 1: none
expect-register -: none

=== dia on a comma outside any call
text:
  x = 1, 2
cursor: 1,6
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  x = 1 2
expect-cursor: 1,6
expect-mode: normal
expect-register ": c ","
expect-register 0: none
expect-register 1: none
expect-register -: c ","

=== cia outside any call
text:
  x = 1
cursor: 1,3
ext: argtextobj
keys: ciaY<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  x Y= 1
expect-cursor: 1,3
expect-mode: normal
expect-register ": none
expect-register 0: none
expect-register 1: none
expect-register -: none

=== via outside any call
text:
  x = 1, 2
cursor: 1,1
ext: argtextobj
keys: vlvia
--- vim 9.1
expect-text:
  xa = 1, 2
expect-cursor: 1,3
expect-mode: insert

=== dia on an empty call
text:
  f()
cursor: 1,2
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f(
expect-cursor: 1,2
expect-mode: normal
expect-register ": c ")"
expect-register 0: c ""
expect-register 1: none
expect-register -: c ")"

=== dia then dot
text:
  f(one, two, three)
  g(four, five)
cursor: 1,11
ext: argtextobj
keys: diaj0fi.
registers: " 0 1 -
--- vim 9.1
expect-text:
  f(one, two, )
  g(four, )
expect-cursor: 2,9
expect-mode: normal
expect-register ": c "five"
expect-register 0: c "three"
expect-register 1: none
expect-register -: c "five"

=== dia then u
text:
  foo(one, two, three)
cursor: 1,11
ext: argtextobj
keys: diau
--- vim 9.1
expect-text:
  foo(one, two, three)
expect-cursor: 1,11
expect-mode: normal

=== dia after a string then u
text:
  f("a,b", c, d)
cursor: 1,13
ext: argtextobj
keys: diau
--- vim 9.1
expect-text:
  f("a,b", c, d)
expect-cursor: 1,3
expect-mode: normal

=== dia on a comma then u
text:
  foo(one, two, three)
cursor: 1,8
ext: argtextobj
keys: diau
--- vim 9.1
expect-text:
  foo(one, two, three)
expect-cursor: 1,9
expect-mode: normal

=== dia keeps the unnamed register
text:
  foo(one, two, three)
cursor: 1,11
ext: argtextobj
keys: yyjdia
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo(one, , three)
expect-cursor: 1,10
expect-mode: normal
expect-register ": c "two"
expect-register 0: l "foo(one, two, three)\n"
expect-register 1: none
expect-register -: c "two"

=== dia on the one empty line
text:
  $
cursor: 1,1
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": none
expect-register 0: none
expect-register 1: none
expect-register -: none

=== dia with the call on the last line
text:
  x
  f(a, b)
cursor: 2,6
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  x
  f(a, )
expect-cursor: 2,6
expect-mode: normal
expect-register ": c "b"
expect-register 0: c ""
expect-register 1: none
expect-register -: c "b"

=== dia over arguments on two lines
text:
  f(a,
    b)
cursor: 2,3
ext: argtextobj
keys: dia
registers: " 0 1 -
--- vim 9.1
expect-text:
  f(a,
    b)
expect-cursor: 2,3
expect-mode: normal
expect-register ": none
expect-register 0: none
expect-register 1: none
expect-register -: none

=== gv after yia
text:
  foo(one, two, three)
cursor: 1,11
ext: argtextobj
keys: yiagv
--- vim 9.1
expect-text:
  foo(one, two, three)
expect-cursor: 1,12
expect-mode: visual
expect-visual: 1,10
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CompletionTest.java`, replace:

```java
            assertThat(matches(editor, "setl nowr")).containsExactly("nowrap", "nowrapscan");
            assertThat(matches(editor, "set invhl")).containsExactly("invhlsearch");
            assertThat(matches(editor, "set a")).containsExactly("all", "autoindent");
            assertThat(matches(editor, "set ts=")).containsExactly("8");
            assertThat(Completion.complete(editor, "set ts=").start()).isEqualTo(7);
```

with:

```java
            assertThat(matches(editor, "setl nowr")).containsExactly("nowrap", "nowrapscan");
            assertThat(matches(editor, "set invhl")).containsExactly("invhlsearch");
            assertThat(matches(editor, "set a")).containsExactly("all", "argtextobj", "autoindent");
            assertThat(matches(editor, "set textobj-")).as("IdeaVim's switches have hyphens").containsExactly("textobj-entire", "textobj-indent");
            assertThat(matches(editor, "set notextobj-e")).containsExactly("notextobj-entire");
            assertThat(matches(editor, "set ts=")).containsExactly("8");
            assertThat(Completion.complete(editor, "set ts=").start()).isEqualTo(7);
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: FAIL: the `ext-argtextobj.case` cases fail with `E518: Unknown option` for `argtextobj`.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.CompletionTest'
```

Expected: FAIL: `optionsTheirNegationsAndTheirValues`: `set a` does not offer `argtextobj`.

- [ ] **Step 3: Write argtextobj**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Argtextobj.java`:

```java
package dev.jasper.editor.vim;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The argtextobj extension (spec 8), ported from Takahiro Suzuki's argtextobj.vim 1.1.1: {@code ia} is the function
 * argument at the cursor, {@code aa} the argument with its comma and the space after it. Like the plugin it walks
 * out with {@code [(} to the parenthesis a name stands before (all the way out with
 * {@code g:argumentobject_force_toplevel}), finds its pair with {@code %}, yanks what is between, and splits that at
 * the commas outside quotes and brackets; the cursor then moves with {@code h} and {@code l} and the area is selected
 * charwise. Quoted strings are found on a copy of the line, where the plugin replaces them in the text and puts the
 * line back.
 */
final class Argtextobj {
    private Argtextobj() { }

    private static final String OPTION = "argtextobj";
    /** Vim's {@code [^']} and {@code .}: neither matches a line break. */
    private static final Pattern SINGLE = Pattern.compile("'([^'\\n]*?)'"), SQUARE = Pattern.compile("\\[([^'\\n]*?)\\]"),
        ANGLE = Pattern.compile("<([^'\\n]*?)>"), DOUBLE = Pattern.compile("\"([^'\\n]*?)\""), PARENS = Pattern.compile("\\(([^()\\n]*?)\\)");

    static void register(VimCommands commands) {
        commands.object("<Plug>InnerArgument", "the function argument at the cursor (`ia`, with `set argtextobj`)", c -> select(c, true));
        commands.object("<Plug>OuterArgument", "the function argument with its comma and the space after it (`aa`)", c -> select(c, false));
    }

    /**
     * The mappings: in Visual mode {@code <Esc>} first, then {@code :call s:MotionArgument()}. When it selects
     * nothing, an operator gets the {@code :} motion to wherever the cursor went, and Visual mode has ended.
     */
    private static Span select(Context c, boolean inner) {
        Extensions.require(c, OPTION);
        VimEditor editor = c.editor;
        if (c.visual != null) Visual.end(editor);
        Pos start = editor.cursor();
        if (motionArgument(c, inner) || c.visual != null) return TextObjects.VISUAL_DONE;
        Pos end = editor.cursor();
        return new Span(Pos.min(start, end), Pos.max(start, end), Target.Kind.EXCLUSIVE);
    }

    /** s:MotionArgument; returns false where it returns before selecting. */
    private static boolean motionArgument(Context c, boolean inner) {
        VimEditor editor = c.editor;
        char current = under(editor);
        if (current == ',' || current == '(') Script.normal(editor, "l");
        Pos undoCursor = getOutOfDoubleQuote(editor);
        Pos rightup = outerFunctionParenthesis(c);
        // The plugin looks for the parenthesis on the cursor's line, at the column it found.
        if (at(editor.buffer().line(editor.cursor().line), rightup.col) != '(') return false;
        Pos rightupPair = pair(editor, rightup);
        String arglist = innerText(editor, rightup, rightupPair);
        int offset = editor.cursor().col - rightup.col - 1;
        String sub = replace(SINGLE, arglist, m -> "(" + underscores(m.group(1)) + ")");
        sub = replace(SQUARE, sub, m -> "(" + underscores(m.group(1)) + ")");
        sub = replace(ANGLE, sub, m -> "(" + underscores(m.group(1)) + ")");
        sub = replace(DOUBLE, sub, m -> "(" + m.group(1) + ")");
        while (sub.indexOf('(') >= 0 && sub.indexOf(')') >= 0) {
            String next = replace(PARENS, sub, m -> "<" + m.group(1).replace(',', '_') + ">");
            // Parentheses that no longer pair up: Vim would loop for ever here.
            if (next.equals(sub)) break;
            sub = next;
        }
        int begin = Math.max(sub.lastIndexOf(',', offset) + 1, 0);
        int comma = offset >= sub.length() ? -1 : sub.indexOf(',', Math.max(0, offset));
        int end = comma == -1 ? sub.length() - 1 : comma - 1;
        int left = offset - begin, right = end - begin;
        boolean deleteTrailingSpace = false;
        if (inner) {
            moveLeft(editor, left);
            right -= moveToNextNonSpace(editor);
        } else if (begin == 0 && end == sub.length() - 1) moveLeft(editor, left);
        else if (begin == 0) {
            moveLeft(editor, left);
            right++;
            deleteTrailingSpace = true;
        } else {
            moveLeft(editor, left + 1);
            right++;
        }
        Script.normal(editor, "v");
        moveRight(editor, right);
        if (deleteTrailingSpace) {
            Script.normal(editor, "l");
            moveToNextNonSpace(editor);
            Script.normal(editor, "h");
        }
        editor.machine().undoCursor(undoCursor);
        return true;
    }

    /**
     * s:GetOutOfDoubleQuote: every {@code a"} area on the line, taken in turn from its first non-blank, is filled
     * with {@code _} ({@code ?} when the cursor is on a {@code _}); when that covered the cursor, the cursor goes to
     * the quote before it ({@code F"}), or left ({@code h}) from where the first non-blank then was.
     *
     * @return where the cursor was when the plugin first changed the text: the start of the first {@code a"} area,
     *     which {@code gvr_} replaced, or else the cursor, where {@code setline()} put the line back
     */
    private static Pos getOutOfDoubleQuote(VimEditor editor) {
        Pos cursor = editor.cursor();
        String work = editor.buffer().line(cursor.line);
        char repl = at(work, cursor.col) == '_' ? '?' : '_';
        int first;
        Pos changed = cursor;
        boolean replaced = false;
        while (true) {
            first = Math.min(Text.firstNonBlank(work), Math.max(0, work.length() - 1));
            int[] area = TextObjects.aroundQuote(work, first, '"');
            if (area == null) break;
            if (!replaced) changed = new Pos(cursor.line, area[0]);
            replaced = true;
            StringBuilder filled = new StringBuilder(work);
            for (int i = area[0]; i <= area[1]; i++) filled.setCharAt(i, repl);
            work = filled.toString();
        }
        if (at(work, cursor.col) == repl) Script.normal(editor, cursor.col == first ? "h" : "F\"");
        return changed;
    }

    /** s:GetOuterFunctionParenthesis: {@code [(} until the parenthesis follows a name, or as far out as it goes. */
    private static Pos outerFunctionParenthesis(Context c) {
        VimEditor editor = c.editor;
        boolean toplevel = !String.valueOf(c.vim.variables().getOrDefault("argumentobject_force_toplevel", 0L)).equals("0");
        Pos save = editor.cursor(), before = save;
        Script.normal(editor, "[(");
        Pos rightup = editor.cursor();
        while (!rightup.equals(before)) {
            Pos p = editor.cursor();
            if (!toplevel && p.col > 0 && word(editor.buffer().line(p.line).charAt(p.col - 1))) break;
            before = rightup;
            Script.normal(editor, "[(");
            rightup = editor.cursor();
        }
        editor.setCursor(save);
        return rightup;
    }

    /** s:GetPair: {@code %h} from the parenthesis. */
    private static Pos pair(VimEditor editor, Pos pos) {
        Pos save = editor.cursor();
        editor.setCursor(pos);
        Script.normal(editor, "%h");
        Pos pair = editor.cursor();
        editor.setCursor(save);
        return pair;
    }

    /** s:GetInnerText: {@code lv}, to the other end, {@code y}; the unnamed register then gets back its text. */
    private static String innerText(VimEditor editor, Pos from, Pos to) {
        Pos save = editor.cursor();
        String regSave = Script.getreg(editor, '"');
        editor.setCursor(from);
        Script.normal(editor, "lv");
        editor.setCursor(to);
        Script.normal(editor, "y");
        String val = Script.getreg(editor, '"');
        editor.setCursor(save);
        // let @@ = reg_save
        Script.setreg(editor, '"', regSave, "");
        return val;
    }

    /** s:MoveToNextNonSpace: {@code l} over spaces; the number of moves. */
    private static int moveToNextNonSpace(VimEditor editor) {
        Pos old = editor.cursor();
        int moved = 0;
        while (under(editor) == ' ') {
            Script.normal(editor, "l");
            if (old.equals(editor.cursor())) break;
            old = editor.cursor();
            moved++;
        }
        return moved;
    }

    private static void moveLeft(VimEditor editor, int num) { if (num > 0) Script.normal(editor, num + "h"); }

    private static void moveRight(VimEditor editor, int num) { if (num > 0) Script.normal(editor, num + "l"); }

    private static char under(VimEditor editor) {
        Pos p = editor.cursor();
        return at(editor.buffer().line(p.line), p.col);
    }

    private static char at(String line, int col) { return col >= 0 && col < line.length() ? line.charAt(col) : 0; }

    /** {@code [a-zA-Z0-9_]}. */
    private static boolean word(char ch) { return ch < 128 && (Character.isLetterOrDigit(ch) || ch == '_'); }

    /** substitute(s, ".", "_", "g"): every character but a line break. */
    private static String underscores(String s) { return s.replaceAll("[^\\n]", "_"); }

    /** substitute(s, pattern, '\=…', 'g'). */
    private static String replace(Pattern pattern, String s, Function<Matcher, String> with) {
        Matcher m = pattern.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(with.apply(m)));
        m.appendTail(out);
        return out.toString();
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/TextObjects.java`, replace:

```java
                }
            }
        } else if (at(line, colStart) == quote || !visEmpty) {
            int firstCol = colStart;
            if (!visEmpty) firstCol = visBefore ? nextQuote(line, colStart, quote, false) : previousQuote(line, colStart, quote, false);
            colStart = 0;
            while (true) {
                colStart = nextQuote(line, colStart, quote, false);
                if (colStart < 0 || colStart > firstCol) return null;
                colEnd = nextQuote(line, colStart + 1, quote, true);
                if (colEnd < 0) return null;
                if (colStart <= firstCol && firstCol <= colEnd) break;
                colStart = colEnd + 1;
            }
        } else {
            colStart = previousQuote(line, colStart, quote, true);
            if (at(line, colStart) != quote) {
                colStart = nextQuote(line, colStart, quote, false);
                if (colStart < 0) return null;
            }
            colEnd = nextQuote(line, colStart + 1, quote, true);
            if (colEnd < 0) return null;
        }
        if (include) {
            if (white(at(line, colEnd + 1))) {
                while (white(at(line, colEnd + 1))) colEnd++;
            } else while (colStart > 0 && white(at(line, colStart - 1))) colStart--;
        }
        if (!include && c.count < 2 && (visEmpty || !insideQuotes)) colStart++;
```

with:

```java
                }
            }
        } else {
            int firstCol = colStart;
            if (!visEmpty) firstCol = visBefore ? nextQuote(line, colStart, quote, false) : previousQuote(line, colStart, quote, false);
            int[] pair = at(line, colStart) == quote || !visEmpty ? pairAround(line, firstCol, quote) : pairFrom(line, colStart, quote);
            if (pair == null) return null;
            colStart = pair[0];
            colEnd = pair[1];
        }
        if (include) {
            int[] area = withWhite(line, colStart, colEnd);
            colStart = area[0];
            colEnd = area[1];
        }
        if (!include && c.count < 2 && (visEmpty || !insideQuotes)) colStart++;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/TextObjects.java`, replace:

```java
        }
        return new Span(start, end, inclusive ? Kind.INCLUSIVE : Kind.EXCLUSIVE);
    }
```

with:

```java
        }
        return new Span(start, end, inclusive ? Kind.INCLUSIVE : Kind.EXCLUSIVE);
    }

    /** The quoted string holding {@code firstCol}, a quote itself, counting pairs of quotes from the line's start. */
    private static int[] pairAround(String line, int firstCol, char quote) {
        int colStart = 0;
        while (true) {
            colStart = nextQuote(line, colStart, quote, false);
            if (colStart < 0 || colStart > firstCol) return null;
            int colEnd = nextQuote(line, colStart + 1, quote, true);
            if (colEnd < 0) return null;
            if (colStart <= firstCol && firstCol <= colEnd) return new int[] { colStart, colEnd };
            colStart = colEnd + 1;
        }
    }

    /** The quoted string {@code col} is in (from the quote before it), or else the next one on the line. */
    private static int[] pairFrom(String line, int col, char quote) {
        int colStart = previousQuote(line, col, quote, true);
        if (at(line, colStart) != quote) {
            colStart = nextQuote(line, colStart, quote, false);
            if (colStart < 0) return null;
        }
        int colEnd = nextQuote(line, colStart + 1, quote, true);
        return colEnd < 0 ? null : new int[] { colStart, colEnd };
    }

    /** {@code a"}'s white space: after the closing quote, or before the opening one when there is none after. */
    private static int[] withWhite(String line, int colStart, int colEnd) {
        if (white(at(line, colEnd + 1))) while (white(at(line, colEnd + 1))) colEnd++;
        else while (colStart > 0 && white(at(line, colStart - 1))) colStart--;
        return new int[] { colStart, colEnd };
    }

    /**
     * What {@code v} then {@code a"} selects at {@code col} (current_quote with an empty Visual area), on a line
     * that need not be the text's: the string and its white space, as {@code {start, end}}, both included; null
     * when there is none.
     */
    static int[] aroundQuote(String line, int col, char quote) {
        int[] pair = at(line, col) == quote ? pairAround(line, col, quote) : pairFrom(line, col, quote);
        return pair == null ? null : withWhite(line, pair[0], pair[1]);
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
    /** Whether the Visual area an operator is about to act on is one an extension's object selected. */
    private boolean objectArea;
    /** The command line typed for the motion being run ({@code /pattern}), or null. */
    private String line;
```

with:

```java
    /** Whether the Visual area an operator is about to act on is one an extension's object selected. */
    private boolean objectArea;
    /** Where the undo step's cursor is, when an extension's script changed the text before the operator did. */
    private Pos undoCursor;
    /** The command line typed for the motion being run ({@code /pattern}), or null. */
    private String line;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
    }

    /** A Visual-mode change that is not an operator (put): records it for {@code .} with the area's size. */
    void visualChange(Visual.Redo redo) {
```

with:

```java
    }

    /**
     * An extension's script changed the text (and put it back) before the pending operator changes it: the undo
     * step remembers the cursor as it was then, where {@code u} puts it back.
     */
    void undoCursor(Pos at) { undoCursor = at.copy(); }

    /** A Visual-mode change that is not an operator (put): records it for {@code .} with the area's size. */
    void visualChange(Visual.Redo redo) {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
        vim.covered(entry.id());
        editor.setCursor(span.start(), true);
        editor.area().vimUndo().stepCursor(span.start().line, span.start().col);
        ((VimCommands.Operator) entry.handler()).apply(context, span);
        setChangeMarks(span);
```

with:

```java
        vim.covered(entry.id());
        editor.setCursor(span.start(), true);
        Pos at = undoCursor != null ? undoCursor : span.start();
        undoCursor = null;
        editor.area().vimUndo().stepCursor(at.line, at.col);
        ((VimCommands.Operator) entry.handler()).apply(context, span);
        setChangeMarks(span);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
        suspended = false;
        suspendedChange = false;
        shown.clear();
        if (!recordingInsert) record.clear();
```

with:

```java
        suspended = false;
        suspendedChange = false;
        undoCursor = null;
        shown.clear();
        if (!recordingInsert) record.clear();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
    /** Every option this version knows. */
    static final List<Def> DEFS = List.of(
        new Def("autoindent", "ai", Type.BOOLEAN, false, true),
        new Def("backspace", "bs", Type.STRING, "indent,eol,start", false),
```

with:

```java
    /** Every option this version knows. */
    static final List<Def> DEFS = List.of(
        new Def("argtextobj", "argtextobj", Type.BOOLEAN, false, false),
        new Def("autoindent", "ai", Type.BOOLEAN, false, true),
        new Def("backspace", "bs", Type.STRING, "indent,eol,start", false),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, replace:

```java
            map("xmap", "ai", "<Plug>textobj-indent-ai"), map("xmap", "ii", "<Plug>textobj-indent-ii"),
            map("omap", "aI", "<Plug>textobj-indent-aI"), map("omap", "iI", "<Plug>textobj-indent-iI"),
            map("xmap", "aI", "<Plug>textobj-indent-aI"), map("xmap", "iI", "<Plug>textobj-indent-iI"))));

    private final Vim vim;
```

with:

```java
            map("xmap", "ai", "<Plug>textobj-indent-ai"), map("xmap", "ii", "<Plug>textobj-indent-ii"),
            map("omap", "aI", "<Plug>textobj-indent-aI"), map("omap", "iI", "<Plug>textobj-indent-iI"),
            map("xmap", "aI", "<Plug>textobj-indent-aI"), map("xmap", "iI", "<Plug>textobj-indent-iI"))),
        new Extension("argtextobj", "vim-scripts/argtextobj.vim", null, Check.NONE, List.of(
            map("xmap", "ia", "<Plug>InnerArgument"), map("xmap", "aa", "<Plug>OuterArgument"),
            map("omap", "ia", "<Plug>InnerArgument"), map("omap", "aa", "<Plug>OuterArgument"))));

    private final Vim vim;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, replace:

```java
        TextobjEntire.register(commands);
        TextobjIndent.register(commands);
        return commands;
    }
```

with:

```java
        TextobjEntire.register(commands);
        TextobjIndent.register(commands);
        Argtextobj.register(commands);
        return commands;
    }
```

- [ ] **Step 4: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains argtextobj's two objects.

- [ ] **Step 5: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.vim.TypeaheadTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.ExtensionsTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 228 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 4111 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`.

- [ ] **Step 6: Check the recording (needs `vim`, `git` and the network)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: `git status` lists only `ext-argtextobj.case` (`??`), unchanged from what you created.

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/vim-v4b
git add plugins/editor/src/main/java/dev/jasper/editor/vim/Argtextobj.java plugins/editor/src/main/java/dev/jasper/editor/vim/TextObjects.java plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java plugins/editor/src/test/java/dev/jasper/editor/vim/CompletionTest.java plugins/editor/src/test/resources/vim/corpus/ext-argtextobj.case docs/editor-vim-reference.md
git commit -m "Add the argtextobj extension

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 5: highlightedyank

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/HighlightedYank.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/HighlightedYankTest.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Operators.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Highlights.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java`

**Interfaces:**
- Consumes: `Operators.yank` (every yank, `:yank` too), `VimEditor.refreshHighlights()`, `MarkCommands.get`, `Block.prep`.
- Produces: `HighlightedYank.yanked(context, visual, content)`, `ranges()` (kind 2), `color()`, `expireNow()` (the timer runs out, for tests), `close()`; `VimEditor.highlightedYank()` and `textVersion()` (b:changedtick); `Highlights.show(ranges, yankColor)`; the option `highlightedyank`.

highlightedyank only draws, so the corpus cannot see it; `HighlightedYankTest` checks what the plugin does. `ExtensionsTest` checks every V4b switch, their `Plug` lines, and that `<unique>` leaves the user's keys alone.

- [ ] **Step 1: Write the failing tests**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/HighlightedYankTest.java`:

```java
package dev.jasper.editor.vim;

import java.awt.Color;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The highlightedyank extension (spec 8), which only draws, so the corpus cannot see it. */
class HighlightedYankTest {
    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    /** Runs the keys, then lets the highlight be drawn as it is once the command has finished, then checks. */
    private static void after(VimEditor editor, String keys, Consumer<List<String>> check) throws Exception {
        SwingUtilities.invokeAndWait(() -> type(editor, keys));
        SwingUtilities.invokeAndWait(() -> check.accept(yank(editor)));
    }

    private static VimEditor editor(String text, String... settings) throws Exception {
        VimEditor[] made = new VimEditor[1];
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText(text);
            area.discardAllEdits();
            area.setCaretPosition(0);
            made[0] = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            for (String setting : settings) type(made[0], ":" + setting + "<CR>");
            type(made[0], ":set highlightedyank<CR>");
        });
        return made[0];
    }

    /** @return the yank's highlight as start-end offsets */
    private static List<String> yank(VimEditor editor) {
        return editor.highlights().stream().filter(r -> r[0] == 2).map(r -> r[1] + "-" + r[2]).toList();
    }

    @Test void aYankIsHighlightedOnceItHasFinished() throws Exception {
        var editor = editor("one two\nthree\n");
        SwingUtilities.invokeAndWait(() -> {
            type(editor, "wyiw");
            assertThat(yank(editor)).as("drawn after the command, as the plugin's timer does").isEmpty();
        });
        after(editor, "", ranges -> assertThat(ranges).containsExactly("4-7"));
        after(editor, "yy", ranges -> assertThat(ranges).as("a line with its break").containsExactly("0-8"));
        after(editor, "y$", ranges -> assertThat(ranges).containsExactly("4-7"));
        after(editor, "2yy", ranges -> assertThat(ranges).containsExactly("0-14"));
        after(editor, "0vjy", ranges -> assertThat(ranges).as("from Visual mode, the area").containsExactly("0-9"));
        after(editor, "gg0<C-v>jly", ranges -> assertThat(ranges).as("a block").containsExactly("0-2", "8-10"));
        after(editor, ":2y<CR>", ranges -> assertThat(ranges).containsExactly("8-14"));
        after(editor, "gg0yj", ranges -> assertThat(ranges).containsExactly("0-14"));
        after(editor, "gg0yvj", ranges -> assertThat(ranges).as("charwise across lines, to the line break").containsExactly("0-8"));
    }

    @Test void onlyYanksAndOnlyWhenSwitchedOn() throws Exception {
        var editor = editor("one two\n");
        after(editor, "dw", ranges -> assertThat(ranges).as("a delete is not a yank").isEmpty());
        after(editor, "\"_yiw", ranges -> assertThat(ranges).as("the black hole takes nothing").isEmpty());
        after(editor, ":set nohighlightedyank<CR>yiw", ranges -> assertThat(ranges).isEmpty());
    }

    @Test void theLastYankOfACommandIsDrawnAndAChangeOrInsertModeClearsIt() throws Exception {
        var editor = editor("one two three\n");
        after(editor, "yiwwyiw", ranges -> assertThat(ranges).as("only the last yank").containsExactly("4-7"));
        after(editor, "x", ranges -> assertThat(ranges).as("a change clears it").isEmpty());
        after(editor, "0yiw", ranges -> assertThat(ranges).containsExactly("0-3"));
        after(editor, "i", ranges -> assertThat(ranges).as("Insert mode clears it").isEmpty());
        after(editor, "<Esc>yiwx", ranges -> assertThat(ranges).as("a change before it is drawn: nothing").isEmpty());
    }

    @Test void theDurationAndTheSettings() throws Exception {
        var editor = editor("one\ntwo\nthree\n");
        after(editor, "yy", ranges -> assertThat(ranges).containsExactly("0-4"));
        SwingUtilities.invokeAndWait(() -> {
            editor.highlightedYank().expireNow();
            assertThat(yank(editor)).as("after g:highlightedyank_highlight_duration").isEmpty();
        });
        after(editor, ":let g:highlightedyank_max_lines = 2<CR>3yy", ranges -> assertThat(ranges).as("too many lines").isEmpty());
        after(editor, "2yy", ranges -> assertThat(ranges).containsExactly("0-8"));
        SwingUtilities.invokeAndWait(() -> editor.highlightedYank().expireNow());
        after(editor, ":let g:highlightedyank_highlight_in_visual = 0<CR>Vy", ranges -> assertThat(ranges).as("not from Visual mode").isEmpty());
        after(editor, ":let g:highlightedyank_highlight_duration = 0<CR>yy", ranges -> assertThat(ranges).as("0: never").isEmpty());
        after(editor, ":let g:highlightedyank_highlight_duration = -1<CR>yy", ranges -> assertThat(ranges).containsExactly("0-4"));
        SwingUtilities.invokeAndWait(() -> {
            editor.highlightedYank().expireNow();
            assertThat(yank(editor)).as("below 0: no timer").containsExactly("0-4");
        });
        after(editor, "Vy", ranges -> assertThat(ranges).as("a yank the plugin leaves out keeps what is drawn").containsExactly("0-4"));
        after(editor, ":let g:highlightedyank_highlight_color = 'rgba(10, 20, 30, 40)'<CR>", ranges ->
            assertThat(editor.highlightedYank().color()).isEqualTo(new Color(10, 20, 30, 40)));
    }

    @Test void theEmptyTextAndTheLastLine() throws Exception {
        var empty = editor("");
        after(empty, "yy", ranges -> assertThat(ranges).as("nothing to draw in an empty text").isEmpty());
        var last = editor("a\nb");
        after(last, "Gyy", ranges -> assertThat(ranges).as("the last line, with no line break").containsExactly("2-3"));
        after(last, "yl", ranges -> assertThat(ranges).containsExactly("2-3"));
    }
}
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java`, replace:

```java
        });
    }
}
```

with:

```java
        });
    }

    @Test void theTextObjectsAndParagraphMotionSwitchAsIdeaVimNamesThem() throws Exception {
        onEdt(() -> {
            var editor = editor("x\n");
            type(editor, ":set textobj-entire textobj-indent argtextobj vim-paragraph-motion highlightedyank<CR>");
            for (String option : new String[] { "textobj-entire", "textobj-indent", "argtextobj", "vim-paragraph-motion", "highlightedyank" })
                assertThat(editor.options().flag(option)).as(option).isTrue();
            assertThat(mapped(editor, Mappings.OP_PENDING, "ae")).isTrue();
            assertThat(mapped(editor, Mappings.VISUAL, "ii")).isTrue();
            assertThat(mapped(editor, Mappings.OP_PENDING, "aa")).isTrue();
            type(editor, ":set notextobj-entire<CR>");
            assertThat(mapped(editor, Mappings.OP_PENDING, "ae")).isFalse();
            type(editor, ":set sw-=2<CR>");
            assertThat(editor.options().number("shiftwidth")).as("a hyphen before = is still -=").isEqualTo(6);
            var plugged = editor("x\n");
            for (String repository : new String[] { "kana/vim-textobj-entire", "michaeljsmith/vim-indent-object", "vim-scripts/argtextobj.vim",
                "dbakker/vim-paragraph-motion", "machakann/vim-highlightedyank" })
                type(plugged, ":Plug '" + repository + "'<CR>");
            for (String option : new String[] { "textobj-entire", "textobj-indent", "argtextobj", "vim-paragraph-motion", "highlightedyank" })
                assertThat(plugged.options().flag(option)).as("Plug switches on " + option).isTrue();
        });
    }

    @Test void uniqueMappingsLeaveTheUsersKeysAlone() throws Exception {
        onEdt(() -> {
            var editor = editor("a\n\nb\n");
            type(editor, ":nnoremap } j<CR>:omap ae iw<CR>:set vim-paragraph-motion textobj-entire<CR>");
            assertThat(editor.vim().mappings().starting(Mappings.NORMAL, KeyInput.parse("}").getFirst()).getFirst().rhs)
                .as("<unique>: the user's } stays").isEqualTo(KeyInput.parse("j"));
            assertThat(mapped(editor, Mappings.OP_PENDING, "}")).as("in other modes the extension maps }").isTrue();
            assertThat(editor.vim().mappings().starting(Mappings.OP_PENDING, KeyInput.parse("a").getFirst()).stream()
                .filter(m -> m.lhs.equals(KeyInput.parse("ae"))).map(m -> m.rhs).toList()).containsExactly(KeyInput.parse("iw"));
            type(editor, ":set novim-paragraph-motion<CR>");
            assertThat(editor.vim().mappings().starting(Mappings.NORMAL, KeyInput.parse("}").getFirst()))
                .as("switching off keeps the user's own").hasSize(1);
        });
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.HighlightedYankTest' --tests 'dev.jasper.editor.vim.ExtensionsTest'
```

Expected: FAIL: the tests do not compile (`highlightedYank()` does not exist).

- [ ] **Step 3: Write highlightedyank**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/HighlightedYank.java`:

```java
package dev.jasper.editor.vim;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * The highlightedyank extension (spec 8), ported from Machakann's vim-highlightedyank (its TextYankPost path): the
 * text a yank took is drawn for {@code g:highlightedyank_highlight_duration} milliseconds (1000; 0 for never, below
 * 0 until something else clears it). As in the plugin, the highlight is drawn once the command has finished (only
 * the last yank of a macro, and only if the text has not changed since), a yank from Visual mode is drawn unless
 * {@code g:highlightedyank_highlight_in_visual} is 0, one of more than {@code g:highlightedyank_max_lines} lines
 * (10000) is not, and a change to the text, Insert mode or the next yank clears it. The colour is IdeaVim's
 * {@code g:highlightedyank_highlight_color} ({@code rgba(r, g, b, a)}), else the incremental-search match's.
 */
final class HighlightedYank {
    private static final String OPTION = "highlightedyank";
    private static final Pattern RGBA = Pattern.compile("rgba\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");

    /** A yank to draw: whether it was from Visual mode, what it took, and the text's version then (b:changedtick). */
    private record Yank(boolean visual, Registers.Content content, int version) { }

    private final VimEditor editor;
    private Yank pending;
    private boolean scheduled;
    private List<int[]> shown = List.of();
    private int shownVersion;
    private Timer timer;

    HighlightedYank(VimEditor editor) { this.editor = editor; }

    /** highlightedyank#debounce: a yank ended; it is drawn when the command has finished, the last one only. */
    static void yanked(Context c, boolean visual, Registers.Content content) {
        if (!c.vim.extensions().on(OPTION)) return;
        if (visual && number(c.vim, "highlightedyank_highlight_in_visual", 1) == 0) return;
        c.editor.highlightedYank().debounce(visual, content);
    }

    private void debounce(boolean visual, Registers.Content content) {
        pending = new Yank(visual, content, editor.textVersion());
        if (scheduled) return;
        scheduled = true;
        SwingUtilities.invokeLater(this::highlight);
    }

    /** s:highlight: the region from the marks and the register's text. */
    private void highlight() {
        scheduled = false;
        Yank yank = pending;
        pending = null;
        if (yank == null || yank.version() != editor.textVersion() || editor.area().vim().orElse(null) != editor) return;
        Marks marks = editor.marks();
        Pos start = yank.visual() ? MarkCommands.get(editor, '<') : marks.get('[');
        Pos end = yank.visual() ? MarkCommands.get(editor, '>') : marks.get(']');
        if (start == null || end == null) return;
        List<int[]> ranges = region(start, end, yank.content());
        int lines = yank.content().type() == Registers.Type.LINE ? end.line - start.line + 1 : yank.content().lines().size();
        if (ranges.isEmpty() || lines > number(editor.vim(), "highlightedyank_max_lines", 10000)) return;
        long duration = number(editor.vim(), "highlightedyank_highlight_duration", 1000);
        if (duration == 0) return;
        clear();
        shown = ranges;
        shownVersion = yank.version();
        editor.refreshHighlights();
        if (duration > 0) {
            timer = new Timer((int) Math.min(duration, Integer.MAX_VALUE), e -> clearAndDraw());
            timer.setRepeats(false);
            timer.start();
        }
    }

    /** @return the highlight as {kind 2, start, end} ranges; a change to the text or Insert mode clears it first */
    List<int[]> ranges() {
        if (!shown.isEmpty() && (shownVersion != editor.textVersion() || editor.mode().inserting())) clear();
        return shown;
    }

    /** @return the highlight's colour, or null for the incremental-search match's */
    Color color() {
        Object value = editor.vim().variables().get("highlightedyank_highlight_color");
        Matcher m = value == null ? null : RGBA.matcher(value.toString().strip());
        if (m == null || !m.matches()) return null;
        int[] v = new int[4];
        for (int i = 0; i < 4; i++) v[i] = Math.min(255, Integer.parseInt(m.group(i + 1)));
        return new Color(v[0], v[1], v[2], v[3]);
    }

    private void clear() {
        if (timer != null) timer.stop();
        timer = null;
        shown = List.of();
    }

    private void clearAndDraw() {
        clear();
        if (editor.area().vim().orElse(null) == editor) editor.refreshHighlights();
    }

    /** Vim is off: the highlight and its timer go. */
    void close() { clear(); }

    /** The timer runs out now, for tests. */
    void expireNow() { if (timer != null) clearAndDraw(); }

    /** s:get_region: charwise from the start as far as the register's text reaches, whole lines, or a block. */
    private List<int[]> region(Pos start, Pos end, Registers.Content content) {
        Buffer b = editor.buffer();
        List<int[]> out = new ArrayList<>();
        List<String> lines = content.lines();
        if (lines.isEmpty() || start.line >= b.count()) return out;
        switch (content.type()) {
            case LINE -> out.add(new int[] { 2, b.start(start.line), Math.min(b.length(), b.end(Math.min(end.line, b.count() - 1)) + 1) });
            case CHAR -> {
                int len = lines.size(), endLine = start.line, endCol;
                if (len == 1) endCol = start.col + lines.getFirst().length() - 1;
                else if (len == 2 && lines.get(1).isEmpty()) endCol = start.col + lines.getFirst().length();
                else if (lines.getLast().isEmpty()) {
                    endLine += len - 2;
                    endCol = lines.get(len - 2).length() - 1;
                } else {
                    endLine += len - 1;
                    endCol = lines.getLast().length() - 1;
                }
                if (endLine >= b.count()) return out;
                String last = b.line(endLine);
                int from = b.offset(start.line, Math.min(start.col, b.line(start.line).length()));
                if (endLine == start.line && endCol < start.col) return out;
                int to = endCol < last.length() ? b.offset(endLine, Text.next(last, Math.max(0, endCol))) : Math.min(b.length(), b.end(endLine) + 1);
                out.add(new int[] { 2, from, to });
            }
            case BLOCK -> {
                int ts = editor.options().number("tabstop");
                int width = 0;
                for (String line : lines) width = Math.max(width, Text.virtcol(line, line.length(), ts));
                int left = Visual.startVcol(b, start, ts), right = left + width - 1;
                for (int line = start.line; line < Math.min(b.count(), start.line + lines.size()); line++) {
                    String text = b.line(line);
                    Block bd = Block.prep(text, left, right, ts, true, Block.Op.OTHER, true);
                    if (bd.isShort && bd.textlen == 0) continue;
                    int from = b.offset(line, Math.min(bd.textcol, text.length()));
                    int until = b.offset(line, Math.min(text.length(), bd.textcol + bd.textlen));
                    if (until > from) out.add(new int[] { 2, from, until });
                }
            }
        }
        out.removeIf(range -> range[2] <= range[1]);
        return out;
    }

    /** @return a {@code g:} number, or the default when it is not set or not a number */
    private static long number(Vim vim, String name, long fallback) {
        Object value = vim.variables().get(name);
        if (value == null) return fallback;
        try { return Long.parseLong(value.toString().strip()); }
        catch (NumberFormatException notNumber) { return fallback; }
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Operators.java`, replace:

```java
    /** op_yank, with its message when more than 'report' lines were yanked. */
    static void yank(Context c, Span span) {
        c.registers.yank(c.register, content(c, span), c.options.string("clipboard"));
        if (c.register == '_') return;
        int lines = span.lineCount();
        if (!span.linewise() && !span.blockwise() && lines == 1) lines = 0;
```

with:

```java
    /** op_yank, with its message when more than 'report' lines were yanked. */
    static void yank(Context c, Span span) {
        Registers.Content yanked = content(c, span);
        c.registers.yank(c.register, yanked, c.options.string("clipboard"));
        if (c.register == '_') return;
        HighlightedYank.yanked(c, span.visual(), yanked);
        int lines = span.lineCount();
        if (!span.linewise() && !span.blockwise() && lines == 1) lines = 0;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Highlights.java`, replace:

```java
    private final List<Object> tags = new ArrayList<>();
    private List<int[]> shown = List.of();

    Highlights(VimTextArea area) { this.area = area; }

    /** Draws the ranges ({@code [kind, start, end)} with kind 0 for Visual and incsearch, 1 for 'hlsearch'). */
    void show(List<int[]> ranges) {
        if (same(ranges)) return;
        clear();
        Highlighter highlighter = area.getHighlighter();
```

with:

```java
    private final List<Object> tags = new ArrayList<>();
    private List<int[]> shown = List.of();
    private java.awt.Color yankColor;

    Highlights(VimTextArea area) { this.area = area; }

    /**
     * Draws the ranges ({@code [kind, start, end)} with kind 0 for Visual and incsearch, 1 for 'hlsearch' and 2 for
     * a yank's highlight, in {@code yank} or else the selection colour).
     */
    void show(List<int[]> ranges, java.awt.Color yank) {
        if (same(ranges) && java.util.Objects.equals(yank, yankColor)) return;
        yankColor = yank;
        clear();
        Highlighter highlighter = area.getHighlighter();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Highlights.java`, replace:

```java
        var selection = new SmartHighlightPainter(area.getSelectionColor());
        var search = new SmartHighlightPainter(area.getMarkAllHighlightColor());
        int length = area.getDocument().getLength();
        for (int[] range : ranges) {
            int start = Math.min(range[1], length), end = Math.min(range[2], length);
            if (end <= start) continue;
            try { tags.add(highlighter.addHighlight(start, end, range[0] == 0 ? selection : search)); }
            catch (BadLocationException ignored) { }
        }
```

with:

```java
        var selection = new SmartHighlightPainter(area.getSelectionColor());
        var search = new SmartHighlightPainter(area.getMarkAllHighlightColor());
        var yanked = yank == null ? selection : new SmartHighlightPainter(yank);
        int length = area.getDocument().getLength();
        for (int[] range : ranges) {
            int start = Math.min(range[1], length), end = Math.min(range[2], length);
            if (end <= start) continue;
            try { tags.add(highlighter.addHighlight(start, end, range[0] == 0 ? selection : range[0] == 1 ? search : yanked)); }
            catch (BadLocationException ignored) { }
        }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    /** The text exchange's first {@code cx} marked (the plugin's b:exchange), or null. */
    private Exchange.Region exchange;
    /** A new language gives the tab its line comment as 'commentstring'. */
    private final java.beans.PropertyChangeListener languageListener = event -> languageChanged();
```

with:

```java
    /** The text exchange's first {@code cx} marked (the plugin's b:exchange), or null. */
    private Exchange.Region exchange;
    /** The highlightedyank extension's highlight of the last yank. */
    private final HighlightedYank highlightedYank = new HighlightedYank(this);
    /** A new language gives the tab its line comment as 'commentstring'. */
    private final java.beans.PropertyChangeListener languageListener = event -> languageChanged();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
        area.enableInputMethods(true);
        marks.close();
        area.getDocument().removeDocumentListener(highlightListener);
        area.removeFocusListener(leaveListener);
```

with:

```java
        area.enableInputMethods(true);
        marks.close();
        highlightedYank.close();
        area.getDocument().removeDocumentListener(highlightListener);
        area.removeFocusListener(leaveListener);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    Exchange.Region exchange() { return exchange; }

    /** @param region the text an exchange has marked, highlighted until the second {@code cx}; null forgets it */
    void exchange(Exchange.Region region) {
```

with:

```java
    Exchange.Region exchange() { return exchange; }

    HighlightedYank highlightedYank() { return highlightedYank; }

    /** @return a number that changes with every change to the text (Vim's b:changedtick) */
    int textVersion() { return textVersion; }

    /** @param region the text an exchange has marked, highlighted until the second {@code cx}; null forgets it */
    void exchange(Exchange.Region region) {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    }

    /** Draws the Visual area, the 'hlsearch' matches, the incremental-search match and the text exchange marked. */
    void refreshHighlights() {
        List<int[]> ranges = new ArrayList<>();
```

with:

```java
    }

    /**
     * Draws the Visual area, the 'hlsearch' matches, the incremental-search match, the text exchange marked and the
     * last yank's highlight.
     */
    void refreshHighlights() {
        List<int[]> ranges = new ArrayList<>();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
        }
        ranges.addAll(Exchange.highlight(this, exchange));
        ranges.addAll(Highlights.visual(this));
        if (incsearch != null) ranges.add(new int[] { 0, incsearch[0], incsearch[1] });
        if (confirmMatch != null) ranges.add(new int[] { 0, confirmMatch[0], confirmMatch[1] });
        highlights.show(ranges);
    }
```

with:

```java
        }
        ranges.addAll(Exchange.highlight(this, exchange));
        ranges.addAll(highlightedYank.ranges());
        ranges.addAll(Highlights.visual(this));
        if (incsearch != null) ranges.add(new int[] { 0, incsearch[0], incsearch[1] });
        if (confirmMatch != null) ranges.add(new int[] { 0, confirmMatch[0], confirmMatch[1] });
        highlights.show(ranges, highlightedYank.color());
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("formatoptions", "fo", Type.STRING, "tcq", true),
        new Def("gdefault", "gd", Type.BOOLEAN, false, false),
        new Def("hlsearch", "hls", Type.BOOLEAN, true, false),
        new Def("ignorecase", "ic", Type.BOOLEAN, false, false),
```

with:

```java
        new Def("formatoptions", "fo", Type.STRING, "tcq", true),
        new Def("gdefault", "gd", Type.BOOLEAN, false, false),
        new Def("highlightedyank", "highlightedyank", Type.BOOLEAN, false, false),
        new Def("hlsearch", "hls", Type.BOOLEAN, true, false),
        new Def("ignorecase", "ic", Type.BOOLEAN, false, false),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, replace:

```java
        new Extension("argtextobj", "vim-scripts/argtextobj.vim", null, Check.NONE, List.of(
            map("xmap", "ia", "<Plug>InnerArgument"), map("xmap", "aa", "<Plug>OuterArgument"),
            map("omap", "ia", "<Plug>InnerArgument"), map("omap", "aa", "<Plug>OuterArgument"))));

    private final Vim vim;
```

with:

```java
        new Extension("argtextobj", "vim-scripts/argtextobj.vim", null, Check.NONE, List.of(
            map("xmap", "ia", "<Plug>InnerArgument"), map("xmap", "aa", "<Plug>OuterArgument"),
            map("omap", "ia", "<Plug>InnerArgument"), map("omap", "aa", "<Plug>OuterArgument"))),
        new Extension("highlightedyank", "machakann/vim-highlightedyank", null, Check.NONE, List.of()));

    private final Vim vim;
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.HighlightedYankTest' --tests 'dev.jasper.editor.vim.ExtensionsTest'
```

Expected: 15 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.vim.TypeaheadTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.ExtensionsTest' --tests 'dev.jasper.editor.vim.HighlightedYankTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 235 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 4111 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/vim-v4b
git add plugins/editor/src/main/java/dev/jasper/editor/vim/HighlightedYank.java plugins/editor/src/main/java/dev/jasper/editor/vim/Operators.java plugins/editor/src/main/java/dev/jasper/editor/vim/Highlights.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java plugins/editor/src/test/java/dev/jasper/editor/vim/HighlightedYankTest.java plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java
git commit -m "Add the highlightedyank extension

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 6: Documentation, the spec amendments and the final check

**Files:**
- Modify: `docs/vim-divergences.md`, `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec

- [ ] **Step 1: List the differences**

In `docs/vim-divergences.md`, replace:

```markdown
- **Extensions are ports.** surround, commentary, exchange and ReplaceWithRegister follow their plugins' scripts on
  Jasper's own commands, and the corpus's `ext:` cases are recorded against the plugins, with tpope/vim-repeat, at the
  commits `plugins/editor/vim-oracle.properties` pins.
```

with:

```markdown
- **Extensions are ports.** surround, commentary, exchange, ReplaceWithRegister, paragraph-motion, textobj-entire,
  textobj-indent, argtextobj and highlightedyank follow their plugins' scripts on Jasper's own commands, and the
  corpus's `ext:` cases are recorded against the plugins, with tpope/vim-repeat, at the commits
  `plugins/editor/vim-oracle.properties` pins. They are switched on by IdeaVim's names (`set textobj-entire`,
  `set vim-paragraph-motion`), and a plugin that has no `<Plug>` names gets IdeaVim's (`<Plug>textobj-indent-ii`,
  `<Plug>(ParagraphNextMotion)`, `<Plug>InnerArgument`), which `:map` shows where Vim shows the plugin's `:call`.
```

Append to `docs/vim-divergences.md`:

```markdown
- **argtextobj** knows parentheses only, as the plugin does; IdeaVim's `g:argtextobj_pairs` is not there, and the
  plugin's `g:argumentobject_force_toplevel` is. A count is ignored (the plugin's mapping calls it once for each line
  of a range). The plugin finds quoted strings by filling them in the text and putting the line back, so in Vim even
  `yia` leaves a change to undo; Jasper looks at a copy of the line. Where the plugin loops for ever (parentheses that
  do not pair on one line) Jasper stops.
- **highlightedyank** draws for `g:highlightedyank_highlight_duration` milliseconds, 1000 unless set (IdeaVim's is
  300), in IdeaVim's `g:highlightedyank_highlight_color` (`rgba(r, g, b, a)`) or else the colour of the incremental
  search's match; there are no `:HighlightedyankOn`, `Off` or `Toggle` commands and no buffer variables.
- **textobj-entire** stays linewise after `dv` (`dvae`), as the plugin does in Vim 9.1, which has no v:motion_force.
```

- [ ] **Step 2: Describe the extensions in the Editor guide**

In `docs/editor.md`, replace:

```markdown
- Not yet: argtextobj, textobj-entire, textobj-indent, matchit, paragraph-motion, highlightedyank and sneak. They
  follow in a later release.
```

with:

```markdown
- More extensions, by IdeaVim's names: `set vim-paragraph-motion` makes `{` and `}` stop at lines of white space too;
  `set textobj-entire` gives `ae` (the whole text) and `ie` (without the empty lines at either end); `set
  textobj-indent` gives `ii` (the lines at the cursor's indent and deeper; again in Visual mode, the enclosing
  block), `ai` (with the line above) and `aI` (with the lines above and below); `set argtextobj` gives `ia` (a
  function argument) and `aa` (with its comma); `set highlightedyank` draws the text a yank took for a second
  (`let g:highlightedyank_highlight_duration = 300` for less).
- Not yet: matchit and sneak. They follow in a later release.
```

- [ ] **Step 3: Add the GUI checks for the user**

Append to `docs/editor-verification.md`:

```markdown

## Vim mode (plan V4b)

- [ ] 97. With `set vim-paragraph-motion`, `}` stops at a line holding only spaces; without it, `}` passes it.
- [ ] 98. With `set textobj-entire`, `yae` then `p` in a new tab copies the whole text; `die` keeps empty lines at either end.
- [ ] 99. With `set textobj-indent` in a Python file, `dii` inside a function's body deletes the body; `vii` then `ii` again widens to the enclosing block.
- [ ] 100. With `set argtextobj`, `cia` on the middle argument of `f(a, b, c)` changes only `b`; `daa` deletes `b, `.
- [ ] 101. With `set highlightedyank`, `yiw` flashes the word for about a second; `let g:highlightedyank_highlight_duration = 300` makes it shorter, and `x` right after a yank clears it at once.
- [ ] 102. `Plug 'kana/vim-textobj-entire'` (and the other plugins' `Plug` lines) in the vimrc switches the extension on.
- [ ] 103. `u` after `dii`, `dae` and `dia` undoes each in one step, and `.` repeats them at the new cursor.
```

- [ ] **Step 4: Record the state**

In `docs/STATUS.md`, replace:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode: files without a final line break**
```

with:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V4b** (branch `claude/vim-v4b`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v4b.md`, written 2026-09-28): the extensions paragraph-motion, textobj-entire,
  textobj-indent, argtextobj and highlightedyank, switched on by IdeaVim's names (`:set` takes hyphens) or `Plug`
  lines. Their text objects select in Visual mode as the plugins do and the pending operator acts on that area
  (`Script.select`); highlightedyank draws a third kind of highlight after a yank. The corpus gains `let:`, pins that
  need pins (textobj-user) and `plugin/**`. Every file in the plan was compiled and run against Vim 9.1 with the pinned
  plugins before the plan was written: 2066 corpus cases (160 new), with and without a final line break.
  - Not done: executing the plan; GUI checks 97–103 of the [checklist](editor-verification.md) (user-run); plan V4c
    (matchit, sneak); push.
- **Editor Vim mode: files without a final line break**
```

- [ ] **Step 5: Amend the spec**

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
Plan V4a (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v4a.md`) amends sections 6.3, 8, 11.1, 12 and 13, as marked **Amended (V4a)**.
```

with:

```markdown
Plan V4a (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v4a.md`) amends sections 6.3, 8, 11.1, 12 and 13, as marked **Amended (V4a)**. Plan V4b (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v4b.md`) amends sections 6.3, 8, 11.1 and 13, as marked **Amended (V4b)**.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
**Amended (V4a):** plan V4a delivers the framework and surround, commentary, exchange and ReplaceWithRegister; the rest come with V4b. Each extension is a port of its plugin's script onto the engine's commands (`Script`, `Machine.nested`), with the plugin's `<Plug>` names as its commands and its default mappings (with their hasmapto() checks and `g:…_no_mappings`); switching it off takes the mappings away. surround has no Insert-mode mappings, no re-indent, no `ds/` and no `\1`…`\7` prompts in `g:surround_{n}`; commentary's 'commentstring' is the tab's language's line comment, else Vim's `/* %s */`; exchange highlights with the 'hlsearch' colour and has none of its `:Xchange…` commands; ReplaceWithRegister has no `"=`. A `<Plug>` name that starts a longer one waits for the next key.
```

with:

```markdown
**Amended (V4a):** plan V4a delivers the framework and surround, commentary, exchange and ReplaceWithRegister; the rest come with V4b. Each extension is a port of its plugin's script onto the engine's commands (`Script`, `Machine.nested`), with the plugin's `<Plug>` names as its commands and its default mappings (with their hasmapto() checks and `g:…_no_mappings`); switching it off takes the mappings away. surround has no Insert-mode mappings, no re-indent, no `ds/` and no `\1`…`\7` prompts in `g:surround_{n}`; commentary's 'commentstring' is the tab's language's line comment, else Vim's `/* %s */`; exchange highlights with the 'hlsearch' colour and has none of its `:Xchange…` commands; ReplaceWithRegister has no `"=`. A `<Plug>` name that starts a longer one waits for the next key.

**Amended (V4b):** plan V4b delivers paragraph-motion, textobj-entire, textobj-indent, argtextobj and highlightedyank; matchit and sneak come with V4c. The switches are IdeaVim's names (`vim-paragraph-motion`, `textobj-entire`, `textobj-indent`, `argtextobj`, `highlightedyank`); plugins without `<Plug>` names get IdeaVim's (`<Plug>(ParagraphNextMotion)`, `<Plug>(ParagraphPrevMotion)`, `<Plug>textobj-indent-ai`/`aI`/`ii`/`iI`, `<Plug>InnerArgument`, `<Plug>OuterArgument`), textobj-entire keeps `<Plug>(textobj-entire-a)` and `-i`. Their objects select in Visual mode as the plugins' mappings do, and the pending operator acts on that area. argtextobj is the plugin's: parentheses only, `g:argumentobject_force_toplevel` rather than `g:argtextobj_pairs`, counts ignored. highlightedyank's duration defaults to the plugin's 1000 ms, not 300; it reads `g:highlightedyank_highlight_in_visual` and `g:highlightedyank_max_lines` too, and has no commands.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- **Recording:** `./gradlew :jasper-plugin-editor:recordVimCorpus` (not part of `check`; needs `vim` on `PATH`, or `-PvimBinary=…`). For each case it runs `vim -u NONE -i NONE -N -Es` with a generated script that sets options and the buffer, places the cursor, runs the vimrc lines, feeds the keys with `feedkeys(keys, 'xt')`, and writes text, cursor, mode, registers, marks and messages as JSON; the task rewrites everything below each case's `---` line. For `ext:` cases it first clones each extension's upstream repository at the commit pinned in `plugins/editor/vim-oracle.properties` into `build/vim-oracle/` and adds it to `runtimepath`. `-Pcase=<pattern>` records a subset. **Amended (V4a):** the pins are `name = git URL commit` lines in `plugins/editor/vim-oracle.properties`, and every `ext:` case also loads tpope/vim-repeat, which the plugins use for `.`; the recorder finds the file in the nearest folder above the corpus that has one and clones into its `build/vim-oracle`. `ext:` names extensions by their switches (`ext: surround`); the recorder clears Vim's own mappings first, and there is no `vimrc:` field.
```

with:

```markdown
- **Recording:** `./gradlew :jasper-plugin-editor:recordVimCorpus` (not part of `check`; needs `vim` on `PATH`, or `-PvimBinary=…`). For each case it runs `vim -u NONE -i NONE -N -Es` with a generated script that sets options and the buffer, places the cursor, runs the vimrc lines, feeds the keys with `feedkeys(keys, 'xt')`, and writes text, cursor, mode, registers, marks and messages as JSON; the task rewrites everything below each case's `---` line. For `ext:` cases it first clones each extension's upstream repository at the commit pinned in `plugins/editor/vim-oracle.properties` into `build/vim-oracle/` and adds it to `runtimepath`. `-Pcase=<pattern>` records a subset. **Amended (V4a):** the pins are `name = git URL commit` lines in `plugins/editor/vim-oracle.properties`, and every `ext:` case also loads tpope/vim-repeat, which the plugins use for `.`; the recorder finds the file in the nearest folder above the corpus that has one and clones into its `build/vim-oracle`. `ext:` names extensions by their switches (`ext: surround`); the recorder clears Vim's own mappings first, and there is no `vimrc:` field. **Amended (V4b):** a pin may name, after its commit, the pins it needs loaded first (textobj-entire needs kana/vim-textobj-user); plugins load `plugin/**/*.vim`; `let:` lines run `:let` before they load (`let: g:indent_object_except_first_level = 0`).
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
 **Amended (V4a):** the extension switches
```

with:

```markdown
 **Amended (V4b):** the switches `vim-paragraph-motion`, `textobj-entire`, `textobj-indent`, `argtextobj` and `highlightedyank` come with V4b; option names may have hyphens. **Amended (V4a):** the extension switches
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
 Plan V4b: argtextobj, textobj-entire, textobj-indent, matchit (with `packadd`), paragraph-motion, highlightedyank and sneak.
```

with:

```markdown
 Plan V4b: argtextobj, textobj-entire, textobj-indent, matchit (with `packadd`), paragraph-motion, highlightedyank and sneak. **Amended (V4b):** split again by the user on 2026-09-28: plan V4b is paragraph-motion, textobj-entire, textobj-indent, argtextobj and highlightedyank; plan V4c is matchit (with `packadd` and the languages' `b:match_words`) and sneak.
```

- [ ] **Step 6: Run everything**

Run:

```bash
./gradlew check
```

Expected: BUILD SUCCESSFUL (6,809 tests).

`TerminalAppIntegrationTest`'s "reflow" case is known to fail now and then under the full check, unrelated to the Editor; if it does, run `./gradlew check` again.

- [ ] **Step 7: Check source hygiene**

Run the snippet in `AGENTS.md` ("Source hygiene"). Expected: no output.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v4b
git add docs
git commit -m "Document Vim mode V4b: five more extensions

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
