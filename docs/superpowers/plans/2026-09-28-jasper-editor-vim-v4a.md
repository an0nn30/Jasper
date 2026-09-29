# Editor Vim mode, plan V4a: extensions, and surround, commentary, exchange and ReplaceWithRegister — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The emulated extensions of spec section 8, first half: switching an extension on with `set {name}` or its `Plug '…'` line, its plugin's default mappings over `<Plug>` commands, the corpus's `ext:` cases recorded against the upstream plugins at pinned commits, and four extensions: ReplaceWithRegister (`gr`, `grr`, Visual `gr`), commentary (`gc{motion}`, `gcc`, Visual `gc`, `gcu`, the `gc` text object, with 'commentstring' from the tab's language), surround (`ds`, `cs`, `cS`, `ys`, `yS`, `yss`, `ySS`, Visual `S` and `gS`, tags with attributes) and exchange (`cx`, `cxx`, Visual `X`, `cxc`).

**Architecture:** Each extension is a port of its plugin's Vim script onto the engine: `Script` gives the ports `:normal!` (the keys run through the typeahead, unmapped, inside the command with its own state set aside by `Machine.nested`), getreg/getregtype/setreg and the marks g@ sets, so a port follows its plugin's steps on Jasper's own commands (`d2i"`, `""P`, `gv"zy`). The commands are `VimCommands` entries named by the plugins' `<Plug>` names; `Extensions` switches them on (the options `surround`, `commentary`, `exchange`, `replacewithregister`, and `Plug` lines) and adds the plugins' default mappings, with their hasmapto() checks and `g:…_no_mappings`. A command that reads keys after its own (surround's getchar() and input()) is suspended in the Machine (`readKey`, `readLine`) and completes after them, one undo step and one `.`. The recorder clones each plugin and tpope/vim-repeat at the commits `plugins/editor/vim-oracle.properties` pins and sources them for `ext:` cases.

**Tech Stack:** Java 25 (JBR), Swing, RSyntaxTextArea 3.6.0, JUnit 6 and AssertJ, Vim 9 and git (only for `recordVimCorpus`).

**Spec:** `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md` (plan V4 of section 13, split by the user into V4a and V4b on 2026-09-28; sections 8 and 11.1). Plans V1a–V3b are merged; their headers describe the engine, the Ex command line, the typeahead, mappings and the vimrc.

**Status:** Written 2026-09-28 on branch `claude/vim-v4a` (worktree `.worktrees/vim-v4a`, from `main` at 29ff6d2d). Every source file, test and corpus case below was compiled and run before the plan was written: against Vim 9.1 with the pinned plugins all 1880 corpus cases (138 new) passed. Applied to a fresh checkout of `29ff6d2d` exactly as written, `./gradlew check` passed (4,505 tests).

## Deviations from the spec (approved scope, adjusted in this plan)

Task 5 writes these into the spec as **Amended (V4a)**.

- **V4 is split.** V4a: the framework and surround, commentary, exchange and ReplaceWithRegister. V4b: argtextobj, textobj-entire, textobj-indent, matchit (with `packadd`), paragraph-motion, highlightedyank and sneak.
- **The extensions are ports of their plugins' scripts**, step by step on the engine's commands, and the corpus records them against the plugins themselves with tpope/vim-repeat (which the plugins use for `.`), pinned in `plugins/editor/vim-oracle.properties` (spec 11.1 names that file; it lives in the Editor plugin's folder).
- **`Plug` lines** for plugins Jasper does not emulate are ignored without an error (spec 8); `Plug` itself is a Jasper command, after Vim's names in the command table.
- **surround** has no Insert-mode `<C-s>` or `<C-g>s`, does not re-indent (as the plugin does not with Vim's `-u NONE` options), has no `ds/` (Jasper has no `[/` motion), and takes `g:surround_{n}` custom surroundings without their `\1`…`\7` prompts; there are no buffer variables (`b:surround_…`).
- **commentary**'s 'commentstring' is the tab's language's line comment (RSyntaxTextArea's comment tokens, as spec 8 says) and Vim's `/* %s */` otherwise; there is no `:Commentary` command and no `b:commentary_format`.
- **exchange** highlights the first text in the Editor's mark-all colour; `:XchangeHighlightToggle` and `g:exchange_indent = '=='` are not there.
- **ReplaceWithRegister** has no expression register (`"=`), as Jasper has no expressions.
- **`<Plug>` names** that start longer ones (`<Plug>Commentary`, `<Plug>CommentaryLine`) wait for the next key, as the plugins' mappings resolve to the longest match.
- **Fixed on the way:** after a put of several lines the `'[` mark was carried to the end of the new text (it was set before the text went in); `g'` and `` g` `` (to a mark without changing the jumplist, which the ports need) are new.

## Global Constraints

- Java 25 on the JetBrains Runtime; always `./gradlew`, never a system Gradle. Work in `.worktrees/vim-v4a` on `claude/vim-v4a`; check `git branch --show-current` before every commit (other sessions switch branches in the main checkout). Never commit on `main`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.
- The Editor plugin compiles against the SDK only (`verifyPluginArchitecture`, a root task). `dev.jasper.editor.vim` may use `vim.regex`, RSyntaxTextArea, Swing and the JDK; `dev.jasper.editor.vim.ui` adds `dev.jasper.editor.vim`; `dev.jasper.editor.ui` may use both. No SDK change.
- No interface without two real implementations; this plan adds none.
- Source hygiene: never put raw control, private-use or unpaired surrogate characters in `.java` files; use escapes. No run of three backticks may appear in any file this plan embeds (the docs link check strips fenced blocks lazily).
- **Do not launch the GUI.** GUI checks go in `docs/editor-verification.md` for the user.
- Engine code runs on the event thread in the app. Tests that type through a `VimEditor` run inside `SwingUtilities.invokeAndWait`.
- The corpus is the authority on Vim behaviour; mark a case `divergent:` only for a documented, deliberate difference.
- `vim` and `git` must be on `PATH` for `recordVimCorpus`, which clones the pinned plugins from GitHub into `plugins/editor/build/vim-oracle` the first time (the dev Mac has Vim 9.1 at `/usr/bin/vim`). `check` never runs Vim or git.

## File structure

New, `plugins/editor/src/main/java/dev/jasper/editor/vim/`:

| File | Responsibility |
|---|---|
| `Script.java` | what the ports use for Vim script: `:normal!`, getreg/getregtype/setreg, the g@ marks, v:register |
| `Extensions.java` | the extensions: their switches, `Plug` repositories and default mappings; `:Plug` |
| `ReplaceWithRegister.java` | `<Plug>ReplaceWithRegisterOperator`, `…Line`, `…Visual` |
| `Commentary.java` | `<Plug>Commentary` (operator and text object), `<Plug>CommentaryLine` |
| `Surround.java` | `<Plug>Dsurround`, `Csurround`, `CSurround`, `Ysurround`, `YSurround`, `Yssurround`, `YSsurround`, `VSurround`, `VgSurround` |
| `Exchange.java` | `<Plug>(Exchange)` (operator and Visual), `<Plug>(ExchangeLine)`, `<Plug>(ExchangeClear)` |

Changed there: `Machine` (`nested`; `<Plug>` operators set their own marks and do not double; the longest `<Plug>` name; `readKey` and `readLine`), `Vim` (`extensions()`), `Options` (the switches, 'commentstring'), `Ex` and `ExTable` (`:Plug`), `ExMaps` (`define`), `MarkCommands` (`g'`, `` g` ``), `ExLines` (`endPending`), `VimCommands`, `VimEditor` ('commentstring' from the language; input()'s prompt; exchange's highlight), `CommandLine` (`input`), `Put` (the `'[` mark).
Tests: `ExtensionsTest` (new); `CorpusCase`, `CorpusFile`, `CorpusRunner`, `RecordCorpus` (`ext:` and the oracle), `VimDocsTest` (the Extensions section), `VimrcTest`, `ExTest`, `ui/EditorWindowTest`. Corpus: `ext-replacewithregister.case`, `marks-keepjumps.case`, `ext-commentary.case`, `ext-surround.case`, `put-marks.case`, `ext-exchange.case`. New: `plugins/editor/vim-oracle.properties`.
Docs: `docs/vim-divergences.md`, `docs/editor-vim-reference.md` (generated), `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec.

---

### Task 1: Extensions, the oracle, and ReplaceWithRegister

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/Script.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/ReplaceWithRegister.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java`, `plugins/editor/src/test/resources/vim/corpus/ext-replacewithregister.case`, `plugins/editor/src/test/resources/vim/corpus/marks-keepjumps.case`, `plugins/editor/vim-oracle.properties`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/ExTable.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/MarkCommands.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/ExLines.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusRunner.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/VimDocsTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ExTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Produces: `Script.normal(editor, keys) -> boolean`, `Script.getreg`, `Script.regtype`, `Script.setreg(editor, name, text, type)`, `Script.operatorMarks(context, span) -> String` (`line`, `block` or `char`), `Script.register(context)`; `Machine.nested(Runnable)`; `Extensions` with `Extension(option, repository, noMappings, check, mappings)`, `Check {NONE, ALL, EACH}`, `ALL` (all four extensions), `byRepository`, `on(option)`, `sync()`, `plug(ExCommand)`, `require(context, option)`; `Vim.extensions()`; `ExMaps.define(mappings, mode, lhs, rhs, noremap, nowait) -> Mapping`; `ExLines.endPending(editor)`; the options `surround`, `commentary`, `exchange`, `replacewithregister`; the Ex command `:Plug`; the motions `g'` and `` g` ``; the corpus field `ext:` (`CorpusCase.ext()`, `extensions()`), which the runner switches on and the recorder loads from `plugins/editor/vim-oracle.properties`.

The `<Plug>` commands are ordinary `VimCommands` entries: the extensions' default mappings reach them, and a `<Plug>` operator sets `'[` and `']` itself, as g@ does. The recorder finds `vim-oracle.properties` in the nearest folder above the corpus that has one, clones each plugin at its commit into `build/vim-oracle` there, and sources its `plugin/*.vim` before the case's file is edited.

- [ ] **Step 1: Write the failing tests and the corpus cases**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java`:

```java
package dev.jasper.editor.vim;

import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** What the corpus cannot record about the extensions (spec 8): switching them, their mappings and their prompts. */
class ExtensionsTest {
    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private static VimEditor editor(String text) {
        var area = new VimTextArea();
        area.setText(text);
        area.setCaretPosition(0);
        return new VimEditor(new Vim(new VimHost(r -> Optional.empty(), (r, t) -> { })), area, EditorDefaults.VIM);
    }

    private static boolean mapped(VimEditor editor, int mode, String lhs) {
        return !editor.vim().mappings().starting(mode, KeyInput.parse(lhs).getFirst()).stream()
            .filter(m -> m.lhs.equals(KeyInput.parse(lhs))).toList().isEmpty();
    }

    @Test void aSwitchAddsTheDefaultMappingsAndTakesThemAway() throws Exception {
        onEdt(() -> {
            var editor = editor("x\n");
            assertThat(mapped(editor, Mappings.NORMAL, "ys")).isFalse();
            type(editor, ":set surround<CR>");
            assertThat(mapped(editor, Mappings.NORMAL, "ys")).isTrue();
            assertThat(mapped(editor, Mappings.VISUAL, "S")).isTrue();
            type(editor, ":set nosurround<CR>");
            assertThat(mapped(editor, Mappings.NORMAL, "ys")).isFalse();
            int bells = editor.bells();
            type(editor, "ds");
            assertThat(editor.bells()).as("ds is Vim's d, then s, which is no motion").isEqualTo(bells + 1);
        });
    }

    @Test void plugLinesSwitchOnWhatJasperHasAndIgnoreTheRest() throws Exception {
        onEdt(() -> {
            var editor = editor("x\n");
            type(editor, ":Plug 'tpope/vim-commentary'<CR>");
            assertThat(editor.options().flag("commentary")).isTrue();
            type(editor, ":Plug \"https://github.com/tommcdo/vim-exchange.git\"<CR>");
            assertThat(editor.options().flag("exchange")).isTrue();
            type(editor, ":Plug 'preservim/nerdtree', { 'on': 'NERDTreeToggle' }<CR>");
            assertThat(editor.message()).as("another plugin is ignored").isEmpty();
            type(editor, ":Plug<CR>");
            assertThat(editor.message()).isEqualTo("E471: Argument required");
            assertThat(Extensions.byRepository("vim-scripts/ReplaceWithRegister")).isPresent();
            assertThat(Extensions.byRepository("nobody/nothing")).isEmpty();
        });
    }

    @Test void noMappingsAndHasmaptoKeepTheDefaultsOut() throws Exception {
        onEdt(() -> {
            var editor = editor("x\n");
            type(editor, ":let g:surround_no_mappings = 1<CR>:set surround<CR>");
            assertThat(mapped(editor, Mappings.NORMAL, "ys")).isFalse();
            type(editor, ":nmap gx <lt>Plug>(Exchange)<CR>:set exchange<CR>");
            assertThat(mapped(editor, Mappings.NORMAL, "cx")).as("hasmapto(): the user's gx").isFalse();
            assertThat(mapped(editor, Mappings.NORMAL, "cxc")).isTrue();
            type(editor, ":nmap gc <lt>Plug>Commentary<CR>:set commentary<CR>");
            assertThat(mapped(editor, Mappings.NORMAL, "gcc")).as("commentary maps all or nothing").isFalse();
        });
    }

    @Test void reloadingSwitchesTheExtensionsOff() throws Exception {
        onEdt(() -> {
            var editor = editor("x\n");
            type(editor, ":set surround commentary<CR>");
            editor.vim().reset();
            assertThat(editor.options().flag("surround")).isFalse();
            assertThat(editor.vim().mappings().isEmpty()).isTrue();
            type(editor, ":set surround<CR>");
            assertThat(mapped(editor, Mappings.NORMAL, "ds")).as("and on again").isTrue();
        });
    }
}
```

Create `plugins/editor/src/test/resources/vim/corpus/ext-replacewithregister.case`:

```text
# ReplaceWithRegister (vim-scripts/ReplaceWithRegister): gr{motion}, grr and Visual gr.

=== gr replaces a word with the unnamed register
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
ext: replacewithregister
keys: yiwwgriw
registers: " 0
--- vim 9.1
expect-text:
  one one three
  four five six
  seven eight nine
expect-cursor: 1,7
expect-mode: normal
expect-register ": c "one"
expect-register 0: c "one"

=== gr with a named register
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
ext: replacewithregister
keys: "ayiwwgriw"agriw
registers: " a
--- vim 9.1
expect-text:
  one one three
  four five six
  seven eight nine
expect-cursor: 1,7
expect-mode: normal
expect-register ": c "one"
expect-register a: c "one"

=== gr with a register before it
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
ext: replacewithregister
keys: "ayiww"agriw
registers: " a
--- vim 9.1
expect-text:
  one one three
  four five six
  seven eight nine
expect-cursor: 1,7
expect-mode: normal
expect-register ": c "one"
expect-register a: c "one"

=== gr over a motion
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
ext: replacewithregister
keys: yiwwgr$
registers: "
--- vim 9.1
expect-text:
  one one
  four five six
  seven eight nine
expect-cursor: 1,7
expect-mode: normal
expect-register ": c "one"

=== gr with a linewise register over a word
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
ext: replacewithregister
keys: yyjwgriw
registers: "
--- vim 9.1
expect-text:
  one two three
  four one two three six
  seven eight nine
expect-cursor: 2,18
expect-mode: normal
expect-register ": l "one two three\n"

=== grr replaces the line
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
ext: replacewithregister
keys: yyjgrr
registers: "
--- vim 9.1
expect-text:
  one two three
  one two three
  seven eight nine
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "one two three\n"

=== grr with a count
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
ext: replacewithregister
keys: yyj2grr
registers: "
--- vim 9.1
expect-text:
  one two three
  one two three
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "one two three\n"

=== grr with a charwise register
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
ext: replacewithregister
keys: yiwjgrr
registers: "
--- vim 9.1
expect-text:
  one two three
  one
  seven eight nine
expect-cursor: 2,1
expect-mode: normal
expect-register ": c "one"

=== Visual gr
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
ext: replacewithregister
keys: yiwwviwgr
registers: "
--- vim 9.1
expect-text:
  one one three
  four five six
  seven eight nine
expect-cursor: 1,7
expect-mode: normal
expect-register ": c "one"

=== Visual gr over lines
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
ext: replacewithregister
keys: yiwjVjgr
registers: "
--- vim 9.1
expect-text:
  one two three
  one
expect-cursor: 2,1
expect-mode: normal
expect-register ": c "one"

=== gr over a block repeats the register
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
ext: replacewithregister
keys: yiw<C-v>jlgr
registers: "
--- vim 9.1
expect-text:
  onee two three
  oneur five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "one"

=== dot repeats gr
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
ext: replacewithregister
keys: yiwwgriwj0.
registers: "
--- vim 9.1
expect-text:
  one one three
  one five six
  seven eight nine
expect-cursor: 2,3
expect-mode: normal
expect-register ": c "one"

=== dot repeats grr
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
ext: replacewithregister
keys: yyjgrrj.
registers: "
--- vim 9.1
expect-text:
  one two three
  one two three
  one two three
expect-cursor: 3,1
expect-mode: normal
expect-register ": l "one two three\n"

=== gr over nothing puts before the cursor
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
ext: replacewithregister
keys: yiwgr0
--- vim 9.1
expect-text:
  twotwo three
  four five six
  seven eight nine
expect-cursor: 1,3
expect-mode: normal

=== the default mappings
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
ext: replacewithregister
keys: :nmap gr<CR>:xmap gr<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  grr           <Plug>ReplaceWithRegisterLine
  n  gr            <Plug>ReplaceWithRegisterOperator
  x  gr            <Plug>ReplaceWithRegisterVisual
```

Create `plugins/editor/src/test/resources/vim/corpus/marks-keepjumps.case`:

```text
# g` and g': to a mark without changing the jumplist.

=== g backtick goes to a mark
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: jwmaggg`a
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 2,6
expect-mode: normal

=== g backtick leaves the jumplist alone
text:
  one two three
  four five six
  seven eight nine
  ten
  eleven
cursor: 1,1
keys: jjmaggGg`a``
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten
  eleven
expect-cursor: 1,1
expect-mode: normal

=== g quote goes to a mark's line
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: jwwmaggg'a
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 2,1
expect-mode: normal

=== g quote leaves the jumplist alone
text:
  one two three
  four five six
  seven eight nine
  ten
  eleven
cursor: 1,1
keys: jjmaggGg'a''
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten
  eleven
expect-cursor: 1,1
expect-mode: normal

=== d g backtick
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: wwmagg0dg`a
registers: "
--- vim 9.1
expect-text:
  three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "one two "
```

A `Plug` line for a plugin Jasper emulates is no longer skipped, so the vimrc tests that used one as a failing line use
a command nobody has instead, and `ExTable` has one name more:

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java`, replace:

```java
 */
class VimrcTest {
    /** The commands whose cases need files or the Editor's commands, which this class runs instead of the corpus. */
    static final Set<String> NOT_IN_VIM = Set.of("ex source", "ex sethandler", "ex action", "ex actionlist");

    @TempDir Path folder;
```

with:

```java
 */
class VimrcTest {
    /** The commands whose cases need files, the Editor's commands or vim-plug, which this class runs instead of the corpus. */
    static final Set<String> NOT_IN_VIM = Set.of("ex source", "ex sethandler", "ex action", "ex actionlist", "ex Plug");

    @TempDir Path folder;
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java`, replace:

```java
        Path vimrc = file("vimrc",
            "set ts=4",
            "Plug 'tpope/vim-surround'",
            "set nosuchoption",
            "let s:x = 1",
```

with:

```java
        Path vimrc = file("vimrc",
            "set ts=4",
            "NERDTreeToggle",
            "set nosuchoption",
            "let s:x = 1",
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java`, replace:

```java
                "Error detected while processing " + vimrc + ":",
                "line    2:",
                "E492: Not an editor command: Plug 'tpope/vim-surround'",
                "line    3:",
                "E518: Unknown option: nosuchoption",
```

with:

```java
                "Error detected while processing " + vimrc + ":",
                "line    2:",
                "E492: Not an editor command: NERDTreeToggle",
                "line    3:",
                "E518: Unknown option: nosuchoption",
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java`, replace:

```java
            var editor = editor("x\n");
            type(editor, ":source " + script + "<CR>:sethandler <lt>C-d> n:ide<CR>:action save<CR>:actionlist<CR><CR>");
        });
        assertThat(covered).containsAll(NOT_IN_VIM);
```

with:

```java
            var editor = editor("x\n");
            type(editor, ":source " + script + "<CR>:sethandler <lt>C-d> n:ide<CR>:action save<CR>:actionlist<CR><CR>");
            type(editor, ":Plug 'tpope/vim-surround'<CR>");
        });
        assertThat(covered).containsAll(NOT_IN_VIM);
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`, replace:

```java
        Path vimrc = window.vimrcFile();
        assertThat(vimrc).isEqualTo(home.resolve("plugins/dev.jasper.editor/data/vimrc"));
        Files.writeString(vimrc, "nnoremap x dd\nbogus\nPlug 'tpope/vim-surround'\n");
        EditorTab tab = opened(file("rc.txt", "one\ntwo\nthree\n"));
        assertThat(host.notices()).as("Vim is off: nothing is read").isEmpty();
```

with:

```java
        Path vimrc = window.vimrcFile();
        assertThat(vimrc).isEqualTo(home.resolve("plugins/dev.jasper.editor/data/vimrc"));
        Files.writeString(vimrc, "nnoremap x dd\nbogus\nNERDTreeToggle\n");
        EditorTab tab = opened(file("rc.txt", "one\ntwo\nthree\n"));
        assertThat(host.notices()).as("Vim is off: nothing is read").isEmpty();
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/ExTest.java`, replace:

```java

    @Test void namesResolveAsVimAbbreviatesThem() {
        assertThat(ExTable.NAMES).as("Vim's 600 and IdeaVim's sethandler, action and actionlist").hasSize(603);
        assertThat(ExTable.resolve("s")).isEqualTo("substitute");
        assertThat(ExTable.resolve("d")).isEqualTo("delete");
```

with:

```java

    @Test void namesResolveAsVimAbbreviatesThem() {
        assertThat(ExTable.NAMES).as("Vim's 600, IdeaVim's sethandler, action and actionlist, and vim-plug's Plug").hasSize(604);
        assertThat(ExTable.resolve("s")).isEqualTo("substitute");
        assertThat(ExTable.resolve("d")).isEqualTo("delete");
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExtensionsTest'
```

Expected: FAIL: the test does not compile (`Extensions` does not exist).

- [ ] **Step 3: Teach the corpus `ext:`, the recorder the pinned plugins, and the reference the extensions**

Create `plugins/editor/vim-oracle.properties`:

```properties
# The Vim plugins the corpus's ext: cases are recorded against (spec 11.1), each pinned to a commit:
# name = git URL, then the commit. recordVimCorpus clones them into build/vim-oracle; check never does.
repeat = https://github.com/tpope/vim-repeat.git 65846025c15494983dafe5e3b46c8f88ab2e9635
surround = https://github.com/tpope/vim-surround.git 3d188ed2113431cf8dac77be61b842acb64433d9
commentary = https://github.com/tpope/vim-commentary.git 64a654ef4a20db1727938338310209b6a63f60c9
exchange = https://github.com/tommcdo/vim-exchange.git d6c1e9790bcb8df27c483a37167459bbebe0112e
replacewithregister = https://github.com/vim-scripts/ReplaceWithRegister.git 832efc23111d19591d495dc72286de2fb0b09345
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java`, replace:

```java
 * @param divergent why Jasper deliberately differs, or null; a divergent case's expectation is written by hand
 * @param set {@code :set} arguments applied first, or empty
 * @param given registers set first
 * @param text the starting lines
```

with:

```java
 * @param divergent why Jasper deliberately differs, or null; a divergent case's expectation is written by hand
 * @param set {@code :set} arguments applied first, or empty
 * @param ext the extensions switched on (spec 8), by their switches ({@code surround}), separated by spaces; empty
 *     for none. The recorder loads each one's plugin, at the commit {@code vim-oracle.properties} pins
 * @param given registers set first
 * @param text the starting lines
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java`, replace:

```java
 * @param expected what Vim did, or null when the case has not been recorded
 */
record CorpusCase(String file, String name, String divergent, String set, Map<Character, Register> given, List<String> text,
                  int line, int col, String keys, List<Character> registers, String window, String folds, boolean output, Expected expected) {
    /**
```

with:

```java
 * @param expected what Vim did, or null when the case has not been recorded
 */
record CorpusCase(String file, String name, String divergent, String set, String ext, Map<Character, Register> given, List<String> text,
                  int line, int col, String keys, List<Character> registers, String window, String folds, boolean output, Expected expected) {
    /**
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java`, replace:

```java

    CorpusCase withExpected(Expected value) {
        return new CorpusCase(file, name, divergent, set, given, text, line, col, keys, registers, window, folds, output, value);
    }

    /** @return the window's columns and rows, from {@link #window} */
```

with:

```java

    CorpusCase withExpected(Expected value) {
        return new CorpusCase(file, name, divergent, set, ext, given, text, line, col, keys, registers, window, folds, output, value);
    }

    /** @return the extensions switched on, from {@link #ext} */
    List<String> extensions() { return ext.isBlank() ? List.of() : List.of(ext.trim().split("\\s+")); }

    /** @return the window's columns and rows, from {@link #window} */
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java`, replace:

```java
            if (!line.startsWith("=== ")) throw error(file, i, "expected \"=== name\"");
            String name = line.substring(4).trim();
            String divergent = null, set = "", keys = null, window = null, folds = null;
            boolean output = false;
            Map<Character, CorpusCase.Register> given = new LinkedHashMap<>();
```

with:

```java
            if (!line.startsWith("=== ")) throw error(file, i, "expected \"=== name\"");
            String name = line.substring(4).trim();
            String divergent = null, set = "", ext = "", keys = null, window = null, folds = null;
            boolean output = false;
            Map<Character, CorpusCase.Register> given = new LinkedHashMap<>();
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java`, replace:

```java
                    case "divergent" -> divergent = value;
                    case "set" -> set = value;
                    case "window" -> window = value;
                    case "folds" -> folds = value;
```

with:

```java
                    case "divergent" -> divergent = value;
                    case "set" -> set = value;
                    case "ext" -> ext = value;
                    case "window" -> window = value;
                    case "folds" -> folds = value;
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java`, replace:

```java
            }
            if (text == null || keys == null) throw error(file, i, "case \"" + name + "\" needs text: and keys:");
            cases.add(new CorpusCase(file, name, divergent, set, given, text, cursorLine, cursorCol, keys, registers, window, folds, output, expected));
        }
        return cases;
```

with:

```java
            }
            if (text == null || keys == null) throw error(file, i, "case \"" + name + "\" needs text: and keys:");
            cases.add(new CorpusCase(file, name, divergent, set, ext, given, text, cursorLine, cursorCol, keys, registers, window, folds, output, expected));
        }
        return cases;
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusRunner.java`, replace:

```java
        editor.view().resize(size[1], size[0]);
        if (!c.set().isBlank()) editor.options().apply(c.set());
        for (var entry : c.given().entrySet()) {
            var register = entry.getValue();
```

with:

```java
        editor.view().resize(size[1], size[0]);
        if (!c.set().isBlank()) editor.options().apply(c.set());
        for (String extension : c.extensions()) editor.options().set(extension, true);
        for (var entry : c.given().entrySet()) {
            var register = entry.getValue();
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java`, replace:

```java
 * cursor, mode and registers as JSON. Divergent cases are skipped. Not part of {@code check}.
 *
 * <p>Arguments: the corpus folder, the Vim binary, and an optional case-name filter (a substring).
 */
public final class RecordCorpus {
    private RecordCorpus() { }

    public static void main(String[] arguments) throws Exception {
        Path folder = Path.of(arguments[0]);
        String vim = arguments.length > 1 && !arguments[1].isBlank() ? arguments[1] : "vim";
        String filter = arguments.length > 2 ? arguments[2] : "";
```

with:

```java
 * cursor, mode and registers as JSON. Divergent cases are skipped. Not part of {@code check}.
 *
 * <p>Arguments: the corpus folder, the Vim binary, and an optional case-name filter (a substring). A case with
 * {@code ext:} runs with its extensions' plugins, cloned at the commits {@code vim-oracle.properties} (in the Editor
 * plugin's folder) pins into {@code build/vim-oracle}, and tpope/vim-repeat, which lets {@code .} repeat them.
 */
public final class RecordCorpus {
    private RecordCorpus() { }

    /** The Editor plugin's folder: the nearest one above the corpus that holds {@code vim-oracle.properties}. */
    private static Path project;

    public static void main(String[] arguments) throws Exception {
        Path folder = Path.of(arguments[0]);
        project = folder.toAbsolutePath().normalize();
        while (project != null && !Files.exists(project.resolve("vim-oracle.properties"))) project = project.getParent();
        String vim = arguments.length > 1 && !arguments[1].isBlank() ? arguments[1] : "vim";
        String filter = arguments.length > 2 ? arguments[2] : "";
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java`, replace:

```java
        } finally { pool.shutdownNow(); }
        System.out.println("Recorded " + recorded + " cases with " + version + ".");
    }
```

with:

```java
        } finally { pool.shutdownNow(); }
        System.out.println("Recorded " + recorded + " cases with " + version + ".");
    }

    /**
     * The plugin an extension's cases are recorded against, cloned the first time at the commit
     * {@code vim-oracle.properties} pins ({@code name = url commit}).
     *
     * @return its folder under {@code build/vim-oracle}
     */
    static synchronized Path oracle(String name) {
        try {
            return clone(name);
        } catch (IOException | InterruptedException failure) {
            throw new IllegalStateException("Could not clone the plugin for " + name + ": " + failure.getMessage(), failure);
        }
    }

    private static Path clone(String name) throws IOException, InterruptedException {
        if (project == null) throw new IOException("no vim-oracle.properties above the corpus folder");
        var pins = new java.util.Properties();
        try (var in = Files.newBufferedReader(project.resolve("vim-oracle.properties"))) { pins.load(in); }
        String pin = pins.getProperty(name);
        if (pin == null) throw new IllegalStateException("vim-oracle.properties pins no plugin for " + name);
        String[] parts = pin.trim().split("\\s+");
        Path folder = project.resolve("build/vim-oracle").resolve(name);
        if (!Files.isDirectory(folder.resolve(".git"))) {
            Files.createDirectories(folder.getParent());
            run(folder.getParent(), "git", "clone", "--quiet", parts[0], folder.getFileName().toString());
        }
        run(folder, "git", "checkout", "--quiet", parts[1]);
        return folder;
    }

    private static void run(Path in, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(in.toFile()).redirectErrorStream(true).start();
        String said = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException(String.join(" ", command) + " failed: " + said);
    }
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java`, replace:

```java
        }
        if (!c.set().isBlank()) lines.add("set " + c.set());
        for (var entry : c.given().entrySet()) {
            CorpusCase.Register register = entry.getValue();
```

with:

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
            CorpusCase.Register register = entry.getValue();
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimDocsTest.java`, replace:

```java
            "In Visual mode, besides the motions, operators and text objects above. A change repeats with `.` on an area of the same size.");
        section(out, commands, VimCommands.Kind.INSERT, "Insert-mode keys", "Printable keys type text; these keys do more.");
        exCommands(out);
        return out.toString();
    }
```

with:

```java
            "In Visual mode, besides the motions, operators and text objects above. A change repeats with `.` on an area of the same size.");
        section(out, commands, VimCommands.Kind.INSERT, "Insert-mode keys", "Printable keys type text; these keys do more.");
        extensions(out, commands);
        exCommands(out);
        return out.toString();
    }

    /** The extensions' {@code <Plug>} commands, which their default keys (in each description) or a user's mapping reach. */
    private static void extensions(StringBuilder out, VimCommands commands) {
        out.append("\n## Extensions\n\n");
        out.append("Switched on with `set {name}` or the plugin's `Plug '…'` line, each extension maps its plugin's default keys to\n");
        out.append("these `<Plug>` commands, which mappings of your own can use too (`:nmap <Leader>s <Plug>Ysurround`).\n\n");
        out.append("| Command | What it does |\n|---|---|\n");
        for (VimCommands.Entry entry : commands.entries()) {
            if (!entry.keys().startsWith("<Plug>")) continue;
            String keys = entry.keys() + (entry.needsChar() ? "{char}" : "");
            String kind = switch (entry.kind()) {
                case OPERATOR -> "operator";
                case OBJECT -> "text object";
                case VISUAL -> "Visual";
                default -> "Normal";
            };
            out.append("| `").append(keys.replace("|", "\\|")).append("` (").append(kind).append(") | ")
                .append(entry.description().replace("|", "\\|")).append(" |\n");
        }
    }
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimDocsTest.java`, replace:

```java
        out.append("| Keys | What it does |\n|---|---|\n");
        for (VimCommands.Entry entry : commands.entries()) {
            if (entry.kind() != kind) continue;
            String keys = entry.keys().equals(" ") ? "<Space>" : entry.keys();
            if (entry.needsChar()) keys += "{char}";
```

with:

```java
        out.append("| Keys | What it does |\n|---|---|\n");
        for (VimCommands.Entry entry : commands.entries()) {
            if (entry.kind() != kind || entry.keys().startsWith("<Plug>")) continue;
            String keys = entry.keys().equals(" ") ? "<Space>" : entry.keys();
            if (entry.needsChar()) keys += "{char}";
```

- [ ] **Step 4: Write `Script`, `Extensions` and ReplaceWithRegister**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Script.java`:

```java
package dev.jasper.editor.vim;

import java.util.List;

/**
 * What the emulated extensions (spec 8) use in place of the Vim script they are ported from: {@code :normal!},
 * {@code getreg()}, {@code getregtype()} and {@code setreg()}, and the marks an operator function finds. The ports
 * follow their plugins' scripts step by step on Jasper's own commands, so they behave as the plugins do in Vim.
 */
final class Script {
    private Script() { }

    /**
     * {@code :normal! {keys}} inside a command: the keys run unmapped, and whatever waits for a key at the end gets
     * {@code <Esc>}. A failing key rings the bell and ends the keys, as in Vim; the command goes on.
     *
     * @param keys Vim notation, {@code <lt>} for {@code <}
     * @return whether no key failed
     */
    static boolean normal(VimEditor editor, String keys) { return normal(editor, KeyInput.parse(keys)); }

    static boolean normal(VimEditor editor, List<KeyInput> keys) {
        boolean[] ok = { true };
        editor.machine().nested(() -> {
            VimFailure failed = editor.typeahead().execute(keys, false);
            if (failed != null) {
                ok[0] = false;
                if (!failed.quiet) editor.bell();
                if (!editor.mode().inserting()) editor.machine().reset();
            }
            ExLines.endPending(editor);
        });
        return ok[0];
    }

    /** @return getreg(): the register's text, a linewise one ending with a line break; empty when it is empty */
    static String getreg(VimEditor editor, char name) {
        return editor.vim().registers().get(name).map(Registers.Content::text).orElse("");
    }

    /** @return getregtype(): {@code v}, {@code V}, or CTRL-V and the width; empty for an empty register */
    static String regtype(VimEditor editor, char name) {
        return editor.vim().registers().get(name).map(content -> switch (content.type()) {
            case CHAR -> "v";
            case LINE -> "V";
            case BLOCK -> "\u0016" + (content.width() + 1);
        }).orElse("");
    }

    /**
     * setreg(): sets a register's text; {@code type} is {@code v}, {@code V} or CTRL-V (a block as wide as its
     * widest line), and an empty type is charwise, or linewise for text ending with a line break.
     */
    static void setreg(VimEditor editor, char name, String text, String type) {
        Registers.Type kind = type.startsWith("V") ? Registers.Type.LINE : type.startsWith("\u0016") ? Registers.Type.BLOCK
            : type.isEmpty() && text.endsWith("\n") ? Registers.Type.LINE : Registers.Type.CHAR;
        Registers.Content content = Registers.Content.of(text, kind);
        if (kind == Registers.Type.BLOCK) {
            int ts = editor.options().number("tabstop");
            int width = 0;
            for (String line : content.lines()) width = Math.max(width, Text.virtcol(line, line.length(), ts));
            content = new Registers.Content(content.lines(), kind, Math.max(0, width - 1));
        }
        editor.vim().registers().set(name, content);
    }

    /**
     * op_function: the operator's area in the {@code '[} and {@code ']} marks, the end moved back a character for an
     * exclusive motion.
     *
     * @return the type an operator function gets: {@code line}, {@code block} or {@code char}
     */
    static String operatorMarks(Context c, Span span) {
        Marks marks = c.editor.marks();
        Pos start = span.start().copy(), end = span.end().copy();
        if (!span.linewise() && !span.blockwise() && span.kind() == Target.Kind.EXCLUSIVE) Search.decl(c.buffer, end);
        marks.set('[', start);
        marks.set(']', end);
        return span.blockwise() ? "block" : span.linewise() ? "line" : "char";
    }

    /** @return v:register: the register typed, or {@code "} (the clipboard's with 'clipboard' holding unnamed) */
    static char register(Context c) {
        if (c.register != 0) return c.register;
        char effective = Registers.effective((char) 0, c.options.string("clipboard"));
        return effective == 0 ? '"' : effective;
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Mappings.Mapping;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The emulated extensions (spec 8), as IdeaVim has them: each is switched on by {@code set {name}} or by its
 * {@code Plug '…'} line, and then adds its plugin's default mappings to its {@code <Plug>} commands (unless its
 * {@code g:…_no_mappings} is set, and, as the plugin checks with hasmapto(), unless the user mapped the
 * {@code <Plug>} name already). Switching it off takes those mappings away again.
 */
final class Extensions {
    /**
     * An extension.
     *
     * @param option the switch, as {@code :set} names it
     * @param repository its plugin's {@code owner/name}, which a {@code Plug} line names
     * @param noMappings the variable that keeps its default mappings out, or null
     * @param check how its plugin asks hasmapto() first: not at all, once for all its mappings, or for each
     * @param mappings its default mappings, in the order its plugin makes them: a mapping command, the keys and the
     *     {@code <Plug>} name
     */
    record Extension(String option, String repository, String noMappings, Check check, List<String[]> mappings) { }

    /** How a plugin checks hasmapto() before it maps. */
    enum Check { NONE, ALL, EACH }

    private static String[] map(String command, String lhs, String rhs) { return new String[] { command, lhs, rhs }; }

    /** Every extension Jasper has. */
    static final List<Extension> ALL = List.of(
        new Extension("surround", "tpope/vim-surround", "surround_no_mappings", Check.NONE, List.of(
            map("nmap", "ds", "<Plug>Dsurround"), map("nmap", "cs", "<Plug>Csurround"), map("nmap", "cS", "<Plug>CSurround"),
            map("nmap", "ys", "<Plug>Ysurround"), map("nmap", "yS", "<Plug>YSurround"), map("nmap", "yss", "<Plug>Yssurround"),
            map("nmap", "ySs", "<Plug>YSsurround"), map("nmap", "ySS", "<Plug>YSsurround"), map("xmap", "S", "<Plug>VSurround"),
            map("xmap", "gS", "<Plug>VgSurround"))),
        new Extension("commentary", "tpope/vim-commentary", null, Check.ALL, List.of(
            map("xmap", "gc", "<Plug>Commentary"), map("nmap", "gc", "<Plug>Commentary"), map("omap", "gc", "<Plug>Commentary"),
            map("nmap", "gcc", "<Plug>CommentaryLine"), map("nmap", "gcu", "<Plug>Commentary<Plug>Commentary"))),
        new Extension("exchange", "tommcdo/vim-exchange", "exchange_no_mappings", Check.EACH, List.of(
            map("nmap", "cx", "<Plug>(Exchange)"), map("xmap", "X", "<Plug>(Exchange)"), map("nmap", "cxc", "<Plug>(ExchangeClear)"),
            map("nmap", "cxx", "<Plug>(ExchangeLine)"))),
        new Extension("replacewithregister", "vim-scripts/ReplaceWithRegister", null, Check.EACH, List.of(
            map("nmap", "gr", "<Plug>ReplaceWithRegisterOperator"), map("nmap", "grr", "<Plug>ReplaceWithRegisterLine"),
            map("xmap", "gr", "<Plug>ReplaceWithRegisterVisual"))));

    private final Vim vim;
    /** The default mappings each switched-on extension added. */
    private final Map<String, List<Mapping>> added = new HashMap<>();

    Extensions(Vim vim) { this.vim = vim; }

    /**
     * @param repository what a {@code Plug} line names: {@code owner/name}, perhaps as a URL or with {@code .git}
     * @return the extension, or empty for a plugin Jasper does not emulate
     */
    static Optional<Extension> byRepository(String repository) {
        String name = repository.strip();
        if (name.endsWith(".git")) name = name.substring(0, name.length() - 4);
        if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
        String[] parts = name.split("/");
        if (parts.length < 2) return Optional.empty();
        String key = (parts[parts.length - 2] + "/" + parts[parts.length - 1]).toLowerCase(Locale.ROOT);
        return ALL.stream().filter(e -> e.repository().toLowerCase(Locale.ROOT).equals(key)).findFirst();
    }

    /** @return whether the extension whose switch this is is on */
    boolean on(String option) { return vim.options().flag(option); }

    /** The switches may have changed: extensions switched on get their mappings, those switched off lose them. */
    void sync() {
        for (Extension extension : ALL) {
            boolean on = on(extension.option());
            if (on && !added.containsKey(extension.option())) added.put(extension.option(), install(extension));
            else if (!on && added.containsKey(extension.option())) {
                Mappings mappings = vim.mappings();
                List<Mapping> current = mappings.all();
                for (Mapping m : added.remove(extension.option())) if (current.contains(m)) mappings.remove(m);
            }
        }
    }

    private List<Mapping> install(Extension extension) {
        List<Mapping> made = new ArrayList<>();
        Object skip = extension.noMappings() == null ? null : vim.variables().get(extension.noMappings());
        if (skip != null && !skip.toString().equals("0") && !skip.toString().isEmpty()) return made;
        Mappings mappings = vim.mappings();
        // commentary.vim: if !hasmapto('<Plug>Commentary') || maparg('gc','n') ==# '' (all its mappings, or none).
        if (extension.check() == Check.ALL) {
            List<KeyInput> plug = ExMaps.parse(extension.mappings().getFirst()[2], false), gc = ExMaps.parse(extension.mappings().getFirst()[1], true);
            boolean mapped = mappings.all().stream().anyMatch(m -> (m.modes & Mappings.MAP) != 0 && contains(m.rhs, plug));
            boolean gcTaken = mappings.all().stream().anyMatch(m -> (m.modes & Mappings.NORMAL) != 0 && m.lhs.equals(gc));
            if (mapped && gcTaken) return made;
        }
        for (String[] m : extension.mappings()) {
            int modes = ExMaps.mode(m[0], false);
            List<KeyInput> lhs = ExMaps.parse(m[1], true), rhs = ExMaps.parse(m[2], false);
            // hasmapto(): the user mapped this <Plug> name already, so the plugin leaves its default out.
            if (extension.check() == Check.EACH && mappings.all().stream().anyMatch(existing -> (existing.modes & modes) != 0 && contains(existing.rhs, rhs)))
                continue;
            made.add(ExMaps.define(mappings, modes, lhs, rhs, false, false));
        }
        return made;
    }

    private static boolean contains(List<KeyInput> keys, List<KeyInput> part) {
        for (int i = 0; i + part.size() <= keys.size(); i++) if (keys.subList(i, i + part.size()).equals(part)) return true;
        return false;
    }

    /**
     * {@code Plug '…'} (vim-plug's, as IdeaVim reads it): switches on the extension Jasper has for the plugin named;
     * a plugin Jasper does not emulate is ignored without an error (spec 8).
     */
    static void plug(ExCommand cmd) {
        String arg = cmd.arg.strip();
        if (arg.isEmpty() || (arg.charAt(0) != '\'' && arg.charAt(0) != '"')) throw new VimFailure("E471: Argument required");
        int close = arg.indexOf(arg.charAt(0), 1);
        if (close < 0) throw new VimFailure("E474: Invalid argument: " + arg);
        byRepository(arg.substring(1, close)).ifPresent(extension -> cmd.editor.options().set(extension.option(), true));
    }

    /** The extension is off: its {@code <Plug>} command fails, as a {@code <Plug>} name nothing defines does. */
    static void require(Context c, String option) {
        if (!c.vim.extensions().on(option)) throw Context.fail();
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ReplaceWithRegister.java`:

```java
package dev.jasper.editor.vim;

/**
 * The ReplaceWithRegister extension (spec 8), ported from Ingo Karkat's ReplaceWithRegister.vim 1.43:
 * {@code ["x]gr{motion}}, {@code grr} and Visual {@code gr} replace text with a register's, keeping the register and
 * the unnamed register as they were. As the plugin does, the replacement is a Visual-mode put over the text, with a
 * linewise register put charwise over a characterwise area.
 */
final class ReplaceWithRegister {
    private ReplaceWithRegister() { }

    private static final String OPTION = "replacewithregister";

    static void register(VimCommands commands) {
        commands.operator("<Plug>ReplaceWithRegisterOperator", "replace {motion}'s text with the register (`gr`, with `set ReplaceWithRegister`)",
            ReplaceWithRegister::operator);
        commands.action("<Plug>ReplaceWithRegisterLine", "replace [count] lines with the register (`grr`)", true, ReplaceWithRegister::line);
        commands.visual("<Plug>ReplaceWithRegisterVisual", "replace the Visual area with the register (`gr`)", false, true,
            ReplaceWithRegister::visual);
    }

    /** {@code gr{motion}}: g@ and ReplaceWithRegister#Operator for the motion's type. */
    private static void operator(Context c, Span span) {
        Extensions.require(c, OPTION);
        String type = Script.operatorMarks(c, span);
        replace(c, Script.register(c), type);
    }

    /** {@code grr}: {@code V{count}_<Esc>}, then the operator on the Visual area. */
    private static void line(Context c) {
        Extensions.require(c, OPTION);
        char register = Script.register(c);
        Script.normal(c.editor, "V" + c.count + "_<Esc>");
        replace(c, register, "visual");
    }

    /** Visual {@code gr}: the area as {@code :<C-u>} leaves it (the Visual marks), then the operator on it. */
    private static void visual(Context c) {
        Extensions.require(c, OPTION);
        char register = Script.register(c);
        Visual.end(c.editor);
        replace(c, register, "visual");
    }

    /** ReplaceWithRegister#Operator: the register, corrected for the area's type while it is put, then restored. */
    private static void replace(Context c, char register, String type) {
        VimEditor editor = c.editor;
        String pasteText = Script.getreg(editor, register), regType = Script.regtype(editor, register);
        boolean corrected = correctForRegtype(editor, type, register, regType, pasteText);
        try {
            put(c, register, type);
        } finally {
            if (corrected) Script.setreg(editor, register, pasteText, regType);
        }
    }

    /**
     * s:CorrectForRegtype: over a block, a single line is repeated for each of its lines and several lines are put
     * blockwise; over anything else a linewise register loses its last line break and is put charwise.
     *
     * @return whether the register was changed and must be put back
     */
    private static boolean correctForRegtype(VimEditor editor, String type, char register, String regType, String pasteText) {
        boolean block = type.equals("visual") ? editor.marks().visualMode == Mode.VISUAL_BLOCK : type.equals("block");
        if (block) {
            int lines = pasteText.isEmpty() ? 0 : pasteText.split("\n", -1).length - (pasteText.endsWith("\n") ? 1 : 0);
            if (regType.equals("v") || regType.equals("V") && lines == 1) {
                int height = Math.abs(MarkCommands.get(editor, '>').line - MarkCommands.get(editor, '<').line) + 1;
                if (height > 1) {
                    String line = pasteText.endsWith("\n") ? pasteText.substring(0, pasteText.length() - 1) : pasteText;
                    String[] parts = line.split("\n", -1);
                    StringBuilder repeated = new StringBuilder();
                    for (int i = 0; i < height; i++) {
                        if (i > 0) repeated.append('\n');
                        repeated.append(String.join("\n", parts));
                    }
                    Script.setreg(editor, register, repeated.toString(), "\u0016");
                    return true;
                }
            } else if (regType.equals("V") && lines > 1) {
                String text = pasteText.endsWith("\n") ? pasteText.substring(0, pasteText.length() - 1) : pasteText;
                Script.setreg(editor, register, text, "\u0016");
                return true;
            }
        } else if (regType.equals("V") && pasteText.endsWith("\n")) {
            Script.setreg(editor, register, pasteText.substring(0, pasteText.length() - 1), "v");
            return true;
        }
        return false;
    }

    /**
     * s:ReplaceWithRegister: a Visual put over the area ({@code gv"xp}, or {@code g`[vg`]"xp} for a motion; before the
     * cursor with {@code P} when the motion covered nothing), with 'clipboard' empty and the unnamed register put back.
     */
    private static void put(Context c, char register, String type) {
        VimEditor editor = c.editor;
        Options options = editor.options();
        String clipboard = options.string("clipboard");
        options.set("clipboard", "");
        String saved = Script.getreg(editor, '"'), savedType = Script.regtype(editor, '"');
        String paste = register == '"' ? "" : "\"" + register;
        try {
            if (type.equals("visual")) Script.normal(editor, "gv" + paste + "p");
            else {
                Pos start = editor.marks().get('['), end = editor.marks().get(']');
                if (start.compareTo(end) > 0) Script.normal(editor, paste + "P");
                else Script.normal(editor, "g`[" + (type.equals("line") ? "V" : "v") + "g`]" + paste + "p");
            }
        } finally {
            if (!savedType.isEmpty()) Script.setreg(editor, '"', saved, savedType);
            options.set("clipboard", clipboard);
        }
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
     * where the text was ({@code ']} in a block on its last line); the others mark the span, whole lines for a linewise one.
     */
    private void setChangeMarks(Span span) {
        if (operator.equals("gq") || operator.equals("gw")) return;
        Marks marks = editor.marks();
        Pos start = span.start().copy(), end = span.end().copy();
        end.line = Math.min(end.line, editor.buffer().count() - 1);
```

with:

```java
     * where the text was ({@code ']} in a block on its last line); the others mark the span, whole lines for a linewise one.
     */
    private void setChangeMarks(Span span) {
        // A ported plugin's operator sets the marks itself, as g@ does before the operator function runs.
        if (operator.equals("gq") || operator.equals("gw") || operator.startsWith("<Plug>")) return;
        Marks marks = editor.marks();
        Pos start = span.start().copy(), end = span.end().copy();
        end.line = Math.min(end.line, editor.buffer().count() - 1);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
        recordCount = 0;
        recordVisual = null;
        recordingInsert = true;
    }

    /** Drops the partly typed command, keeping an insert being recorded. */
```

with:

```java
        recordCount = 0;
        recordVisual = null;
        recordingInsert = true;
    }

    /**
     * Keys a ported plugin runs with {@code :normal!} in the middle of a command (surround's {@code norm! d2i(}): the
     * command's own state is kept aside while they run and comes back after. What they change joins the command's undo
     * step, and {@code .} goes on repeating the command, not them.
     */
    void nested(Runnable work) {
        int c1 = count1, c2 = count2, rc = recordCount;
        char reg = register, forc = forced, rr = recordRegister;
        String op = operator, k = keys, ln = line;
        List<KeyInput> sh = List.copyOf(shown), rec = List.copyOf(record);
        Consumer<KeyInput> arg = argument;
        Visual.Redo rv = recordVisual, vr = visualRedo;
        boolean ri = recordingInsert, rep = replaying;
        recordingInsert = false;
        reset();
        replaying = true;
        try {
            work.run();
        } finally {
            count1 = c1;
            count2 = c2;
            recordCount = rc;
            register = reg;
            forced = forc;
            recordRegister = rr;
            operator = op;
            keys = k;
            line = ln;
            shown.clear();
            shown.addAll(sh);
            record.clear();
            record.addAll(rec);
            argument = arg;
            recordVisual = rv;
            visualRedo = vr;
            recordingInsert = ri;
            replaying = rep;
        }
    }

    /** Drops the partly typed command, keeping an insert being recorded. */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, replace:

```java
    private final Mappings mappings = new Mappings();
    private final Macros macros = new Macros();
    /** The global variables {@code :let} sets ({@code g:mapleader}, extension settings), by name without {@code g:}. */
    private final java.util.Map<String, Object> variables = new java.util.HashMap<>();
```

with:

```java
    private final Mappings mappings = new Mappings();
    private final Macros macros = new Macros();
    private final Extensions extensions = new Extensions(this);
    /** The global variables {@code :let} sets ({@code g:mapleader}, extension settings), by name without {@code g:}. */
    private final java.util.Map<String, Object> variables = new java.util.HashMap<>();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, replace:

```java
        this.host = host;
        registers = new Registers(host);
    }
```

with:

```java
        this.host = host;
        registers = new Registers(host);
        // An extension's switch (set surround) adds or takes away its mappings.
        options.onChanged(extensions::sync);
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, replace:

```java
        variables.clear();
    }

    /** @return macro recording and execution */
```

with:

```java
        variables.clear();
    }

    /** @return the emulated extensions */
    Extensions extensions() { return extensions; }

    /** @return macro recording and execution */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("belloff", "bo", Type.STRING, "", false),
        new Def("clipboard", "cb", Type.STRING, "", false),
        new Def("comments", "com", Type.STRING, "s1:/*,mb:*,ex:*/,://,b:#,:%,:XCOMM,n:>,fb:-", true),
        new Def("compatible", "cp", Type.BOOLEAN, false, false),
        new Def("encoding", "enc", Type.STRING, "utf-8", false),
        new Def("expandtab", "et", Type.BOOLEAN, false, true),
        new Def("fileformat", "ff", Type.STRING, "unix", true),
```

with:

```java
        new Def("belloff", "bo", Type.STRING, "", false),
        new Def("clipboard", "cb", Type.STRING, "", false),
        new Def("commentary", "commentary", Type.BOOLEAN, false, false),
        new Def("comments", "com", Type.STRING, "s1:/*,mb:*,ex:*/,://,b:#,:%,:XCOMM,n:>,fb:-", true),
        new Def("compatible", "cp", Type.BOOLEAN, false, false),
        new Def("encoding", "enc", Type.STRING, "utf-8", false),
        new Def("exchange", "exchange", Type.BOOLEAN, false, false),
        new Def("expandtab", "et", Type.BOOLEAN, false, true),
        new Def("fileformat", "ff", Type.STRING, "unix", true),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("number", "nu", Type.BOOLEAN, true, true),
        new Def("relativenumber", "rnu", Type.BOOLEAN, false, true),
        new Def("report", "report", Type.NUMBER, 2, false),
        new Def("ruler", "ru", Type.BOOLEAN, false, false),
```

with:

```java
        new Def("number", "nu", Type.BOOLEAN, true, true),
        new Def("relativenumber", "rnu", Type.BOOLEAN, false, true),
        new Def("replacewithregister", "replacewithregister", Type.BOOLEAN, false, false),
        new Def("report", "report", Type.NUMBER, 2, false),
        new Def("ruler", "ru", Type.BOOLEAN, false, false),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("softtabstop", "sts", Type.NUMBER, 0, true),
        new Def("startofline", "sol", Type.BOOLEAN, true, false),
        new Def("syntax", "syn", Type.STRING, "", true),
        new Def("tabstop", "ts", Type.NUMBER, 8, true),
```

with:

```java
        new Def("softtabstop", "sts", Type.NUMBER, 0, true),
        new Def("startofline", "sol", Type.BOOLEAN, true, false),
        new Def("surround", "surround", Type.BOOLEAN, false, false),
        new Def("syntax", "syn", Type.STRING, "", true),
        new Def("tabstop", "ts", Type.NUMBER, 8, true),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, replace:

```java
        def("sethandler", EXTRA | TRLBAR, Addr.NONE, "`:sethandler {key} {modes}:{vim|ide} …`: who gets a key in each mode", ExVimrc::sethandler);
        def("action", EXTRA | NEEDARG | TRLBAR, Addr.NONE, "run the Editor command {id}, as `<Action>(id)` does in a mapping", ExVimrc::action);
        def("actionlist", EXTRA | TRLBAR, Addr.NONE, "list the Editor's command ids, or those holding [text]", ExVimrc::actionlist);
        def("syntax", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "accepted and ignored: the Editor highlights by language", ExVimrc::ignored);
```

with:

```java
        def("sethandler", EXTRA | TRLBAR, Addr.NONE, "`:sethandler {key} {modes}:{vim|ide} …`: who gets a key in each mode", ExVimrc::sethandler);
        def("action", EXTRA | NEEDARG | TRLBAR, Addr.NONE, "run the Editor command {id}, as `<Action>(id)` does in a mapping", ExVimrc::action);
        def("Plug", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "`Plug 'tpope/vim-surround'`: switch on the extension Jasper emulates for the plugin; others are ignored", Extensions::plug);
        def("actionlist", EXTRA | TRLBAR, Addr.NONE, "list the Editor's command ids, or those holding [text]", ExVimrc::actionlist);
        def("syntax", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "accepted and ignored: the Editor highlights by language", ExVimrc::ignored);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExTable.java`, replace:

```java
        "#", "&", "*", "<", "=", ">", "@", "{", "}", "~", "Next", "Print", "X", "++", "--",
        // IdeaVim's own, after Vim's so that no Vim abbreviation changes.
        "sethandler", "action", "actionlist");

    /**
```

with:

```java
        "#", "&", "*", "<", "=", ">", "@", "{", "}", "~", "Next", "Print", "X", "++", "--",
        // IdeaVim's own, after Vim's so that no Vim abbreviation changes.
        "sethandler", "action", "actionlist", "Plug");

    /**
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java`, replace:

```java
            throw new VimFailure("E31: No such mapping");
        }
        Mapping reused = null;
        for (Mapping m : mappings.all()) {
            if ((m.modes & mode) == 0 || !m.lhs.equals(lhs)) continue;
            if (unique) throw new VimFailure("E227: Mapping already exists for " + display(lhs, true));
            m.modes &= ~mode;
            if (m.modes == 0 && reused == null) {
```

with:

```java
            throw new VimFailure("E31: No such mapping");
        }
        if (unique && mappings.all().stream().anyMatch(m -> (m.modes & mode) != 0 && m.lhs.equals(lhs)))
            throw new VimFailure("E227: Mapping already exists for " + display(lhs, true));
        define(mappings, mode, lhs, rhs, noremap, nowait);
    }

    /**
     * Defines a mapping as do_map does: the mapping of the same keys in these modes loses them, and one left with no
     * modes takes the new right-hand side in its place.
     *
     * @return the mapping that now holds it
     */
    static Mapping define(Mappings mappings, int mode, List<KeyInput> lhs, List<KeyInput> rhs, boolean noremap, boolean nowait) {
        Mapping reused = null;
        for (Mapping m : mappings.all()) {
            if ((m.modes & mode) == 0 || !m.lhs.equals(lhs)) continue;
            m.modes &= ~mode;
            if (m.modes == 0 && reused == null) {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java`, replace:

```java
            } else if (m.modes == 0) mappings.remove(m);
        }
        if (reused == null) mappings.add(mode, lhs, rhs, noremap, nowait);
    }
```

with:

```java
            } else if (m.modes == 0) mappings.remove(m);
        }
        return reused != null ? reused : mappings.add(mode, lhs, rhs, noremap, nowait);
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/MarkCommands.java`, replace:

```java
        commands.motionWithChar("'", "to the first non-blank of mark {char}'s line", c -> go(c, true));
        commands.motionWithChar("`", "to mark {char}", c -> go(c, false));
        commands.action("<C-o>", "to an older position in the jumplist", false, c -> jump(c, -c.count));
        // CTRL-I arrives as <Tab>, as in Vim (KeyInput.normalized).
```

with:

```java
        commands.motionWithChar("'", "to the first non-blank of mark {char}'s line", c -> go(c, true));
        commands.motionWithChar("`", "to mark {char}", c -> go(c, false));
        commands.motionWithChar("g'", "to the first non-blank of mark {char}'s line, leaving the jumplist alone", c -> go(c, true, false));
        commands.motionWithChar("g`", "to mark {char}, leaving the jumplist alone", c -> go(c, false, false));
        commands.action("<C-o>", "to an older position in the jumplist", false, c -> jump(c, -c.count));
        // CTRL-I arrives as <Tab>, as in Vim (KeyInput.normalized).
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/MarkCommands.java`, replace:

```java

    /** nv_gomark: ' is linewise to the first non-blank, ` exclusive to the mark itself; both are jumps. */
    static Target go(Context c, boolean linewise) {
        int name = c.argumentChar();
        if (name < 0) throw Context.fail();
```

with:

```java

    /** nv_gomark: ' is linewise to the first non-blank, ` exclusive to the mark itself; both are jumps. */
    static Target go(Context c, boolean linewise) { return go(c, linewise, true); }

    /** nv_gomark: with {@code jump} false ({@code g'}, {@code g`}) the jumplist is left alone. */
    static Target go(Context c, boolean linewise, boolean jump) {
        int name = c.argumentChar();
        if (name < 0) throw Context.fail();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/MarkCommands.java`, replace:

```java
        if (linewise) {
            String line = c.buffer.line(pos.line);
            return Target.linewise(new Pos(pos.line, Text.firstNonBlank(line)), Target.Column.RESET).asJump();
        }
        String line = c.buffer.line(pos.line);
        int col = Math.min(pos.col, line.length());
        if (!c.inVisual() && col >= line.length() && !line.isEmpty()) col = Text.last(line);
        return Target.exclusive(new Pos(pos.line, col)).asJump().usingRegisterOne().withoutAdjust();
    }
```

with:

```java
        if (linewise) {
            String line = c.buffer.line(pos.line);
            Target target = Target.linewise(new Pos(pos.line, Text.firstNonBlank(line)), Target.Column.RESET);
            return jump ? target.asJump() : target;
        }
        String line = c.buffer.line(pos.line);
        int col = Math.min(pos.col, line.length());
        if (!c.inVisual() && col >= line.length() && !line.isEmpty()) col = Text.last(line);
        Target target = Target.exclusive(new Pos(pos.line, col)).usingRegisterOne().withoutAdjust();
        return jump ? target.asJump() : target;
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExLines.java`, replace:

```java
            }
        }
        // Out of keys: whatever still waits for one gets <Esc>.
        for (int n = 0; n < 100 && pending(editor); n++) {
            try {
```

with:

```java
            }
        }
        endPending(editor);
        if (failed != null && !failed.getMessage().isEmpty()) throw VimFailure.quiet();
    }

    /** Out of keys ({@code :normal}'s, or a ported plugin's): whatever still waits for one gets {@code <Esc>}. */
    static void endPending(VimEditor editor) {
        for (int n = 0; n < 100 && pending(editor); n++) {
            try {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExLines.java`, replace:

```java
            }
        }
        if (failed != null && !failed.getMessage().isEmpty()) throw VimFailure.quiet();
    }
```

with:

```java
            }
        }
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, replace:

```java
        Ex.register(commands);
        Macros.register(commands);
        return commands;
    }
```

with:

```java
        Ex.register(commands);
        Macros.register(commands);
        ReplaceWithRegister.register(commands);
        return commands;
    }
```

- [ ] **Step 5: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains `g'`, `` g` ``, `:Plug` and an Extensions section with ReplaceWithRegister's commands.

- [ ] **Step 6: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExtensionsTest' --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.ui.EditorWindowTest'
```

Expected: 56 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.vim.TypeaheadTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.ExtensionsTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 221 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1766 tests pass.

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

Expected: `git status` lists only `ext-replacewithregister.case` and `marks-keepjumps.case` (`??`), unchanged from what you created: Vim with the pinned ReplaceWithRegister agrees with every expectation. `plugins/editor/build/vim-oracle` now holds `repeat` and `replacewithregister`.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v4a
git add plugins/editor/src/main/java/dev/jasper/editor/vim/Script.java plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java plugins/editor/src/main/java/dev/jasper/editor/vim/ReplaceWithRegister.java plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java plugins/editor/src/main/java/dev/jasper/editor/vim/ExTable.java plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java plugins/editor/src/main/java/dev/jasper/editor/vim/MarkCommands.java plugins/editor/src/main/java/dev/jasper/editor/vim/ExLines.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java plugins/editor/src/test/java/dev/jasper/editor/vim plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java plugins/editor/src/test/resources/vim/corpus/ext-replacewithregister.case plugins/editor/src/test/resources/vim/corpus/marks-keepjumps.case plugins/editor/vim-oracle.properties docs/editor-vim-reference.md
git commit -m "Add Vim extensions, the pinned-plugin oracle, and ReplaceWithRegister

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: commentary

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/Commentary.java`, `plugins/editor/src/test/resources/vim/corpus/ext-commentary.case`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Consumes: `Script`, `Extensions` (Task 1).
- Produces: `Commentary.go(editor, first, last)`, `Commentary.surroundings(editor)`; the option 'commentstring' (`cms`, local, Vim's `/* %s */`), set from the tab's language by `VimEditor`; in the Machine, a `<Plug>` operator does not double, and a `<Plug>` name that starts a longer one waits for the next key (`<Plug>Commentary`, `<Plug>CommentaryLine`).

- [ ] **Step 1: Write the failing test and the corpus cases**

In `plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java`, replace:

```java
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
```

with:

```java
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
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

    @Test void commentstringFollowsTheTabsLanguage() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            area.setText("x\n");
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            assertThat(editor.options().string("commentstring")).isEqualTo("//%s");
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
            assertThat(editor.options().string("commentstring")).isEqualTo("#%s");
            type(editor, ":set commentary<CR>gcc");
            assertThat(editor.buffer().line(0)).isEqualTo("# x");
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            assertThat(editor.options().string("commentstring")).as("no line comment: kept").isEqualTo("#%s");
        });
    }
}
```

Create `plugins/editor/src/test/resources/vim/corpus/ext-commentary.case`:

```text
# commentary (tpope/vim-commentary): gc{motion}, gcc, Visual gc, gcu and the gc text object.

=== gcc comments a line
text:
  def f():
      x = 1
      y = 2
  $
      return x
cursor: 2,5
set: cms=#\ %s
ext: commentary
keys: gcc
--- vim 9.1
expect-text:
  def f():
      # x = 1
      y = 2
  $
      return x
expect-cursor: 2,5
expect-mode: normal

=== gcc uncomments a commented line
text:
  def f():
      # x = 1
      # y = 2
  $
      return x
cursor: 2,5
set: cms=#\ %s
ext: commentary
keys: gcc
--- vim 9.1
expect-text:
  def f():
      x = 1
      # y = 2
  $
      return x
expect-cursor: 2,5
expect-mode: normal

=== gcc with a count
text:
  def f():
      x = 1
      y = 2
  $
      return x
cursor: 2,1
set: cms=#\ %s
ext: commentary
keys: 2gcc
--- vim 9.1
expect-text:
  def f():
      # x = 1
      # y = 2
  $
      return x
expect-cursor: 2,1
expect-mode: normal

=== gc with a motion
text:
  def f():
      x = 1
      y = 2
  $
      return x
cursor: 2,1
set: cms=#\ %s
ext: commentary
keys: gcj
--- vim 9.1
expect-text:
  def f():
      # x = 1
      # y = 2
  $
      return x
expect-cursor: 2,1
expect-mode: normal

=== gc with a text object
text:
  def f():
      x = 1
      y = 2
  $
      return x
cursor: 2,1
set: cms=#\ %s
ext: commentary
keys: gcip
--- vim 9.1
expect-text:
  # def f():
  #     x = 1
  #     y = 2
  $
      return x
expect-cursor: 1,1
expect-mode: normal

=== gc over a paragraph leaves the blank line
text:
  def f():
      x = 1
      y = 2
  $
      return x
cursor: 1,1
set: cms=#\ %s
ext: commentary
keys: gcap
--- vim 9.1
expect-text:
  # def f():
  #     x = 1
  #     y = 2
  $
      return x
expect-cursor: 1,1
expect-mode: normal

=== Visual gc
text:
  def f():
      x = 1
      y = 2
  $
      return x
cursor: 2,1
set: cms=#\ %s
ext: commentary
keys: Vjgc
--- vim 9.1
expect-text:
  def f():
      # x = 1
      # y = 2
  $
      return x
expect-cursor: 2,1
expect-mode: normal

=== Visual gc charwise takes whole lines
text:
  def f():
      x = 1
      y = 2
  $
      return x
cursor: 2,7
set: cms=#\ %s
ext: commentary
keys: vjgc
--- vim 9.1
expect-text:
  def f():
      # x = 1
      # y = 2
  $
      return x
expect-cursor: 2,7
expect-mode: normal

=== gc uncomments when every line is commented
text:
  def f():
      # x = 1
      # y = 2
  $
      return x
cursor: 2,1
set: cms=#\ %s
ext: commentary
keys: gcj
--- vim 9.1
expect-text:
  def f():
      x = 1
      y = 2
  $
      return x
expect-cursor: 2,1
expect-mode: normal

=== gc comments when one line is not
text:
      # x = 1
      y = 2
cursor: 1,1
set: cms=#\ %s
ext: commentary
keys: gcj
--- vim 9.1
expect-text:
      # # x = 1
      # y = 2
expect-cursor: 1,1
expect-mode: normal

=== comments go at the first line's indent
text:
    a
      b
  c
cursor: 1,1
set: cms=#\ %s
ext: commentary
keys: 2gcc
--- vim 9.1
expect-text:
    # a
    #   b
  c
expect-cursor: 1,1
expect-mode: normal

=== gcu uncomments the comment block
text:
  def f():
      # x = 1
      # y = 2
  $
      return x
cursor: 2,1
set: cms=#\ %s
ext: commentary
keys: gcu
--- vim 9.1
expect-text:
  def f():
      x = 1
      y = 2
  $
      return x
expect-cursor: 2,1
expect-mode: normal

=== dot repeats gcc
text:
  def f():
      x = 1
      y = 2
  $
      return x
cursor: 2,1
set: cms=#\ %s
ext: commentary
keys: gccj.
--- vim 9.1
expect-text:
  def f():
      # x = 1
      # y = 2
  $
      return x
expect-cursor: 3,1
expect-mode: normal

=== dot repeats gc with a motion
text:
  def f():
      x = 1
      y = 2
  $
      return x
      z = 3
cursor: 2,1
set: cms=#\ %s
ext: commentary
keys: gcjjjj.
--- vim 9.1
expect-text:
  def f():
      # x = 1
      # y = 2
  $
      # return x
      # z = 3
expect-cursor: 5,1
expect-mode: normal

=== dgc deletes the comment block
text:
  def f():
      # x = 1
      # y = 2
  $
      return x
cursor: 3,1
set: cms=#\ %s
ext: commentary
keys: dgc
registers: "
--- vim 9.1
expect-text:
  def f():
      return x
expect-cursor: 2,5
expect-mode: normal
expect-register ": l "    # x = 1\n    # y = 2\n\n"

=== cgc changes the comment block
text:
  def f():
      # x = 1
      # y = 2
  $
      return x
cursor: 2,1
set: cms=#\ %s
ext: commentary
keys: cgcpass<Esc>
--- vim 9.1
expect-text:
  def f():
  pass
  $
      return x
expect-cursor: 2,4
expect-mode: normal

=== gcc on a blank line does nothing
text:
  def f():
      x = 1
      y = 2
  $
      return x
cursor: 4,1
set: cms=#\ %s
ext: commentary
keys: gcc
--- vim 9.1
expect-text:
  def f():
      x = 1
      y = 2
  $
      return x
expect-cursor: 4,1
expect-mode: normal

=== u undoes gcc
text:
  def f():
      x = 1
      y = 2
  $
      return x
cursor: 2,1
set: cms=#\ %s
ext: commentary
keys: gccu
--- vim 9.1
expect-text:
  def f():
      x = 1
      y = 2
  $
      return x
expect-cursor: 2,1
expect-mode: normal

=== the default mappings
text:
  def f():
      x = 1
      y = 2
  $
      return x
cursor: 1,1
set: cms=#\ %s
ext: commentary
keys: :nmap gc<CR>:omap gc<CR>:xmap gc<CR>
output: yes
--- vim 9.1
expect-text:
  def f():
      x = 1
      y = 2
  $
      return x
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  gcu           <Plug>Commentary<Plug>Commentary
  n  gcc           <Plug>CommentaryLine
  n  gc            <Plug>Commentary
  o  gc            <Plug>Commentary
  x  gc            <Plug>Commentary

=== the default commentstring
text:
  def f():
      x = 1
      y = 2
  $
      return x
cursor: 2,1
ext: commentary
keys: gcc
--- vim 9.1
expect-text:
  def f():
      /* x = 1 */
      y = 2
  $
      return x
expect-cursor: 2,1
expect-mode: normal

=== nested block comments are numbered
text:
  a /* b */ c
cursor: 1,1
ext: commentary
keys: gcc
--- vim 9.1
expect-text:
  /* a /1* b *1/ c */
expect-cursor: 1,1
expect-mode: normal

=== nested block comments are numbered back
text:
  /* a /1* b *1/ c */
cursor: 1,1
ext: commentary
keys: gcc
--- vim 9.1
expect-text:
  a /* b */ c
expect-cursor: 1,1
expect-mode: normal

=== a commentstring without spaces
text:
  x
  y
cursor: 1,1
set: cms=//%s
ext: commentary
keys: gcc
--- vim 9.1
expect-text:
  // x
  y
expect-cursor: 1,1
expect-mode: normal

=== uncommenting a comment without its space
text:
  //x
  //y
cursor: 1,1
set: cms=//%s
ext: commentary
keys: gcj
--- vim 9.1
expect-text:
  x
  y
expect-cursor: 1,1
expect-mode: normal

=== a commentstring with an end
text:
  <b>
  text
cursor: 1,1
set: cms=<!--%s-->
ext: commentary
keys: gcj
--- vim 9.1
expect-text:
  <!-- <b> -->
  <!-- text -->
expect-cursor: 1,1
expect-mode: normal
```

- [ ] **Step 2: Run it to see it fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExtensionsTest'
```

Expected: FAIL: `commentstringFollowsTheTabsLanguage` fails: E518, there is no 'commentstring' option yet.

- [ ] **Step 3: Write commentary**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Commentary.java`:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The commentary extension (spec 8), ported from Tim Pope's commentary.vim 1.3: {@code gc{motion}}, {@code gcc},
 * Visual {@code gc}, {@code gcu} and the {@code gc} text object comment and uncomment whole lines with
 * 'commentstring', which a tab takes from its language's line comment (RSyntaxTextArea's comment tokens).
 */
final class Commentary {
    private Commentary() { }

    private static final String OPTION = "commentary";

    static void register(VimCommands commands) {
        commands.operator("<Plug>Commentary", "comment or uncomment {motion}'s lines, or the Visual lines (`gc`, with `set commentary`)",
            Commentary::operator);
        commands.action("<Plug>CommentaryLine", "comment or uncomment [count] lines (`gcc`)", true, Commentary::line);
        commands.object("<Plug>Commentary", "the block of comment lines around the cursor (`gc` after an operator; `gcu` uncomments it)",
            Commentary::object);
    }

    /** {@code gc{motion}} and Visual {@code gc}: g@, and s:go on the lines of {@code '[} to {@code ']}. */
    private static void operator(Context c, Span span) {
        Extensions.require(c, OPTION);
        Script.operatorMarks(c, span);
        go(c.editor, c.editor.marks().get('[').line, c.editor.marks().get(']').line);
    }

    /** {@code gcc}: s:go() . '_', that is g@ with {@code [count]_}. */
    private static void line(Context c) {
        Extensions.require(c, OPTION);
        if (!Script.normal(c.editor, (c.hasCount ? String.valueOf(c.count) : "") + "<Plug>Commentary_")) throw Context.fail();
    }

    /** s:surroundings: 'commentstring' with a space either side of {@code %s}, split there. */
    static String[] surroundings(VimEditor editor) {
        String cms = editor.options().string("commentstring");
        if (cms.isEmpty()) cms = "%s";
        cms = cms.replaceFirst("(?<=\\S)%s", " %s").replaceFirst("%s(?=\\S)", "%s ");
        int at = cms.indexOf("%s");
        if (at < 0) return new String[] { cms, "" };
        String rest = cms.substring(at + 2);
        int next = rest.indexOf("%s");
        return new String[] { cms.substring(0, at), next < 0 ? rest : rest.substring(0, next) };
    }

    /** matchstr(line, '\S.*\s\@<!'): the line without its leading and trailing white space. */
    private static String trimmed(String line) { return line.strip(); }

    /** s:strip_white_space: a comment token loses its inner space when the line has the token without it. */
    private static String[] stripWhiteSpace(String l, String r, String line) {
        if (l.endsWith(" ") && (line + " ").indexOf(l) < 0 && line.startsWith(l.substring(0, l.length() - 1))) l = l.substring(0, l.length() - 1);
        if (r.startsWith(" ") && !slice(" " + line, -r.length() - 1, Integer.MAX_VALUE).equals(r)
            && slice(line, -r.length(), Integer.MAX_VALUE).equals(r.substring(1)))
            r = r.substring(1);
        return new String[] { l, r };
    }

    /** Vim's {@code s[from : to]}: {@code to} included, negative indexes counting from the end. */
    private static String slice(String s, int from, int to) {
        int n = s.length();
        int a = from < 0 ? Math.max(0, from + n) : from;
        int b = to == Integer.MAX_VALUE ? n - 1 : to < 0 ? to + n : to;
        if (a >= n || b < a) return "";
        return s.substring(a, Math.min(n, b + 1));
    }

    /** {@code line[strlen(line) - strlen(r) : -1]}: the line's tail as long as {@code r}. */
    private static String tail(String line, String r) { return slice(line, line.length() - r.length(), -1); }

    private static boolean isComment(String line, String l, String r) { return line.startsWith(l) && tail(line, r).equals(r); }

    /**
     * s:go: the lines are uncommented when every non-blank one is commented, else commented, each non-blank line
     * wrapped after the first line's indent; nested block comments are numbered ({@code /1*}) and back.
     */
    static void go(VimEditor editor, int first, int last) {
        Buffer b = editor.buffer();
        String[] lr = surroundings(editor);
        String l = lr[0], r = lr[1];
        boolean uncomment = true;
        for (int n = first; n <= last; n++) {
            String line = trimmed(b.line(n));
            String[] s = stripWhiteSpace(l, r, line);
            l = s[0];
            r = s[1];
            if (!line.isEmpty() && (!line.startsWith(l) || !tail(line, r).equals(r))) uncomment = false;
        }
        String indent = leadingWhite(b.line(first));
        List<String> lines = new ArrayList<>();
        for (int n = first; n <= last; n++) {
            String line = b.line(n);
            if (r.length() > 2 && !(l + r).contains("\\")) line = renumber(line, l, r, uncomment ? 2 : 0);
            if (uncomment) {
                Matcher m = Pattern.compile("\\S.*(?<!\\s)").matcher(line);
                if (m.find()) line = line.substring(0, m.start()) + slice(m.group(), l.length(), -r.length() - 1) + line.substring(m.end());
            } else {
                Matcher m = Pattern.compile("^(?:" + Pattern.quote(indent) + "|\\s*)(.*(?<=\\S))").matcher(line);
                if (m.find()) line = line.substring(0, m.start(1)) + l + m.group(1) + r + line.substring(m.end(1));
            }
            lines.add(line);
        }
        // setline() leaves the cursor where it is.
        Pos cursor = editor.cursor();
        for (int i = 0; i < lines.size(); i++) if (!lines.get(i).equals(b.line(first + i))) b.setLine(first + i, lines.get(i));
        editor.setCursor(new Pos(cursor.line, Math.min(cursor.col, Math.max(0, b.line(cursor.line).length() - 1))));
    }

    private static String leadingWhite(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return line.substring(0, i);
    }

    /**
     * The nested-comment numbering: the digits before the last character of the left token and after the first of the
     * right one ({@code /1*}, {@code *1/}) go up by one when commenting and down when uncommenting, vanishing at 0.
     */
    private static String renumber(String line, String l, String r, int uncomment) {
        Matcher last = Pattern.compile("\\S\\s*$").matcher(l);
        Matcher first = Pattern.compile("\\S").matcher(r);
        if (!last.find() || !first.find()) return line;
        int lk = last.start(), rk = first.start() + 1;
        Pattern digits = Pattern.compile("(?<=" + Pattern.quote(l.substring(0, lk)) + ")\\d*(?=" + Pattern.quote(l.substring(lk)) + ")"
            + "|(?<=" + Pattern.quote(r.substring(0, rk)) + ")\\d*(?=" + Pattern.quote(r.substring(rk)) + ")");
        Matcher m = digits.matcher(line);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            long n = (m.group().isEmpty() ? 0 : Long.parseLong(m.group())) + 1 - uncomment;
            m.appendReplacement(out, n <= 0 ? "" : Long.toString(n));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * s:textobject: the comment lines around the cursor, linewise; after {@code c} the blank lines at either end are
     * left out.
     */
    private static Span object(Context c) {
        Extensions.require(c, OPTION);
        VimEditor editor = c.editor;
        Buffer b = editor.buffer();
        boolean inner = "c".equals(c.operator);
        String[] lr = surroundings(editor);
        String l = lr[0], r = lr[1];
        int cursor = editor.cursor().line + 1, count = b.count();
        int[] lnums = { cursor + 1, cursor - 2 };
        int[][] passes = { { 0, -1, 1 }, { 1, 1, count } };
        for (int[] pass : passes) {
            int index = pass[0], dir = pass[1], bound = pass[2];
            String line = "";
            while (lnums[index] != bound && line.isEmpty() || isComment(line, l, r)) {
                lnums[index] += dir;
                line = trimmed(getline(b, lnums[index] + dir));
                String[] s = stripWhiteSpace(l, r, line);
                l = s[0];
                r = s[1];
            }
        }
        while ((inner || lnums[1] != count) && getline(b, lnums[0]).isEmpty()) lnums[0]++;
        while (inner && getline(b, lnums[1]).isEmpty()) lnums[1]--;
        if (lnums[0] > lnums[1]) throw Context.fail();
        return new Span(new Pos(lnums[0] - 1, 0), new Pos(lnums[1] - 1, 0), Target.Kind.LINEWISE);
    }

    /** getline(): a line by its number, empty outside the buffer. */
    private static String getline(Buffer b, int lnum) { return lnum < 1 || lnum > b.count() ? "" : b.line(lnum - 1); }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
    private char recordRegister;
    private int recordCount;
    /** For a Visual-mode change being recorded, the size of its area. */
    private Visual.Redo recordVisual, visualRedo;
```

with:

```java
    private char recordRegister;
    private int recordCount;
    /** A {@code <Plug>} command whose name starts a longer one, and its keys, while the next key decides. */
    private VimCommands.Entry shorter;
    private String shorterKeys;
    /** For a Visual-mode change being recorded, the size of its area. */
    private Visual.Redo recordVisual, visualRedo;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
        if (operator == null) entry = keys.equals("~") && editor.options().flag("tildeop") ? vim.commands().normal("g~") : vim.commands().normal(keys);
        else entry = vim.commands().pending(keys);
        if (entry == null) {
            if (vim.commands().prefix(keys, operator != null)) return;
            throw Context.fail();
        }
        if (needsChar(entry)) {
            VimCommands.Entry waiting = entry;
```

with:

```java
        if (operator == null) entry = keys.equals("~") && editor.options().flag("tildeop") ? vim.commands().normal("g~") : vim.commands().normal(keys);
        else entry = vim.commands().pending(keys);
        boolean longer = vim.commands().prefix(keys, operator != null);
        if (entry != null && longer && keys.startsWith("<Plug>")) {
            // A <Plug> name that starts a longer one (<Plug>Commentary, <Plug>CommentaryLine) waits for the next key,
            // as the plugin's mappings resolve to the longest match.
            shorter = entry;
            shorterKeys = keys;
            return;
        }
        if (entry == null) {
            if (longer) return;
            if (shorter != null) {
                // The longer name did not come: the shorter one runs, and this key goes again after it.
                VimCommands.Entry run = shorter;
                keys = shorterKeys;
                shorter = null;
                record.removeLast();
                shown.removeLast();
                start(run);
                key(key);
                return;
            }
            throw Context.fail();
        }
        shorter = null;
        start(entry);
    }

    /** A Normal-mode command or a motion after an operator, whose keys are complete: it reads what it needs, then runs. */
    private void start(VimCommands.Entry entry) {
        if (needsChar(entry)) {
            VimCommands.Entry waiting = entry;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
    /** {@code dd}, {@code gUU}, {@code gUgU}, and {@code ~~} with 'tildeop'. */
    private boolean doubled() {
        if (keys.equals(operator)) return true;
        if (operator.length() == 2 && operator.charAt(0) == 'g' && keys.equals(operator.substring(1))) return true;
```

with:

```java
    /** {@code dd}, {@code gUU}, {@code gUgU}, and {@code ~~} with 'tildeop'. */
    private boolean doubled() {
        // A ported plugin's operator is g@, whose second key there is a mapping of its own (commentary's gcu).
        if (operator.startsWith("<Plug>")) return false;
        if (keys.equals(operator)) return true;
        if (operator.length() == 2 && operator.charAt(0) == 'g' && keys.equals(operator.substring(1))) return true;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
        keys = "";
        argument = null;
        shown.clear();
        if (!recordingInsert) record.clear();
```

with:

```java
        keys = "";
        argument = null;
        shorter = null;
        shown.clear();
        if (!recordingInsert) record.clear();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("clipboard", "cb", Type.STRING, "", false),
        new Def("commentary", "commentary", Type.BOOLEAN, false, false),
        new Def("comments", "com", Type.STRING, "s1:/*,mb:*,ex:*/,://,b:#,:%,:XCOMM,n:>,fb:-", true),
        new Def("compatible", "cp", Type.BOOLEAN, false, false),
```

with:

```java
        new Def("clipboard", "cb", Type.STRING, "", false),
        new Def("commentary", "commentary", Type.BOOLEAN, false, false),
        new Def("commentstring", "cms", Type.STRING, "/* %s */", true),
        new Def("comments", "com", Type.STRING, "s1:/*,mb:*,ex:*/,://,b:#,:%,:XCOMM,n:>,fb:-", true),
        new Def("compatible", "cp", Type.BOOLEAN, false, false),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
        @Override public void focusLost(java.awt.event.FocusEvent event) { marks.set('"', cursor()); }
    };
    private final DocumentListener highlightListener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { textChanged(); }
```

with:

```java
        @Override public void focusLost(java.awt.event.FocusEvent event) { marks.set('"', cursor()); }
    };
    /** A new language gives the tab its line comment as 'commentstring'. */
    private final java.beans.PropertyChangeListener languageListener = event -> languageChanged();
    private final DocumentListener highlightListener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { textChanged(); }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
        originalCaret = area.getCaret() instanceof ConfigurableCaret caret ? caret.getStyle() : CaretStyle.VERTICAL_LINE_STYLE;
        editorDefaults(defaults);
        options.onChanged(this::syncArea);
        syncArea();
```

with:

```java
        originalCaret = area.getCaret() instanceof ConfigurableCaret caret ? caret.getStyle() : CaretStyle.VERTICAL_LINE_STYLE;
        editorDefaults(defaults);
        languageChanged();
        area.addPropertyChangeListener(org.fife.ui.rsyntaxtextarea.RSyntaxTextArea.SYNTAX_STYLE_PROPERTY, languageListener);
        options.onChanged(this::syncArea);
        syncArea();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
        machine.abort();
        area.removeCaretListener(caretListener);
        area.attach(null);
        area.setCaretStyle(RTextArea.INSERT_MODE, originalCaret);
```

with:

```java
        machine.abort();
        area.removeCaretListener(caretListener);
        area.removePropertyChangeListener(org.fife.ui.rsyntaxtextarea.RSyntaxTextArea.SYNTAX_STYLE_PROPERTY, languageListener);
        area.attach(null);
        area.setCaretStyle(RTextArea.INSERT_MODE, originalCaret);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
        vim.forget(this);
        buffer.close();
    }
```

with:

```java
        vim.forget(this);
        buffer.close();
    }

    /**
     * The tab's language changed: 'commentstring' becomes its line comment ({@code //%s}, {@code <!--%s-->}), as a
     * filetype plugin sets it in Vim; a language with none keeps the value it had.
     */
    private void languageChanged() {
        if (!(area.getDocument() instanceof org.fife.ui.rsyntaxtextarea.RSyntaxDocument document)) return;
        String[] tokens = document.getLineCommentStartAndEnd(0);
        if (tokens == null || tokens[0] == null) return;
        options.setLocal("commentstring", tokens[0].strip() + "%s" + (tokens[1] == null ? "" : tokens[1].strip()));
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, replace:

```java
        Macros.register(commands);
        ReplaceWithRegister.register(commands);
        return commands;
    }
```

with:

```java
        Macros.register(commands);
        ReplaceWithRegister.register(commands);
        Commentary.register(commands);
        return commands;
    }
```

- [ ] **Step 4: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains commentary's commands.

- [ ] **Step 5: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExtensionsTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.CompletionTest'
```

Expected: 16 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1791 tests pass.

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

Expected: `git status` lists only `ext-commentary.case` (`??`), unchanged from what you created.

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/vim-v4a
git add plugins/editor/src/main/java/dev/jasper/editor/vim/Commentary.java plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java plugins/editor/src/test/resources/vim/corpus/ext-commentary.case docs/editor-vim-reference.md
git commit -m "Add the commentary extension and commentstring

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: surround

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/Surround.java`, `plugins/editor/src/test/resources/vim/corpus/ext-surround.case`, `plugins/editor/src/test/resources/vim/corpus/put-marks.case`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/CommandLine.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Put.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Consumes: `Script`, `Extensions`, `Machine.nested` (Task 1).
- Produces: `Machine.readKey(Consumer<KeyInput>)` and `Machine.readLine(prompt, initial, end, Consumer<String>)`, which suspend the command until they have run; `CommandLine.input(prompt, initial, end, done, cancelled)` and `CommandLine.prompt()`; `VimEditor.commandLineText()` shows the prompt; `Surround.wrap(...)`.

`Put` set `'[` before a put of several lines went in, so the mark moved to the new text's end; surround's ``""P`[`` found that, and `put-marks.case` records the right marks.

- [ ] **Step 1: Write the failing test and the corpus cases**

In `plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java`, replace:

```java
    }

    @Test void reloadingSwitchesTheExtensionsOff() throws Exception {
        onEdt(() -> {
```

with:

```java
    }

    @Test void anExtensionThatIsOffCannotRun() throws Exception {
        onEdt(() -> {
            var editor = editor("(a)\n");
            type(editor, ":nmap Q <lt>Plug>Dsurround<CR>");
            int bells = editor.bells();
            type(editor, "lQ(");
            assertThat(editor.bells()).isEqualTo(bells + 1);
            assertThat(editor.buffer().line(0)).isEqualTo("(a)");
            type(editor, ":set surround<CR>Q(");
            assertThat(editor.buffer().line(0)).isEqualTo("a");
        });
    }

    @Test void reloadingSwitchesTheExtensionsOff() throws Exception {
        onEdt(() -> {
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

    @Test void surroundAsksForATagOnTheCommandLine() throws Exception {
        onEdt(() -> {
            var editor = editor("one two\n");
            type(editor, ":set surround<CR>ysiwt");
            assertThat(editor.commandLineText()).contains("<");
            type(editor, "em");
            assertThat(editor.commandLineText()).contains("<em");
            type(editor, ">");
            assertThat(editor.commandLineText()).as("> ends the tag").isEmpty();
            assertThat(editor.buffer().line(0)).isEqualTo("<em>one</em> two");
            type(editor, "wdst");
            assertThat(editor.buffer().line(0)).isEqualTo("one two");
            type(editor, "ysiwT");
            assertThat(editor.commandLineText()).as("T offers the last tag removed").contains("<em");
            type(editor, "<Esc>");
            assertThat(editor.buffer().line(0)).as("no tag: nothing around it").isEqualTo("one two");
        });
    }
}
```

Create `plugins/editor/src/test/resources/vim/corpus/ext-surround.case`:

```text
# surround (tpope/vim-surround): ds, cs, cS, ys, yS, yss, ySS and Visual S and gS.

=== ds removes double quotes
text:
  He said "hello" to (them) now
cursor: 1,11
ext: surround
keys: ds"
registers: "
--- vim 9.1
expect-text:
  He said hello to (them) now
expect-cursor: 1,9
expect-mode: normal
expect-register ": c ""

=== ds removes parentheses
text:
  He said "hello" to (them) now
cursor: 1,22
ext: surround
keys: ds(
--- vim 9.1
expect-text:
  He said "hello" to them now
expect-cursor: 1,20
expect-mode: normal

=== ds with b
text:
  He said "hello" to (them) now
cursor: 1,22
ext: surround
keys: dsb
--- vim 9.1
expect-text:
  He said "hello" to them now
expect-cursor: 1,20
expect-mode: normal

=== ds with a closing parenthesis
text:
  He said "hello" to (them) now
cursor: 1,22
ext: surround
keys: ds)
--- vim 9.1
expect-text:
  He said "hello" to them now
expect-cursor: 1,20
expect-mode: normal

=== ds removes brackets
text:
  x [a, b] {c} <d> y
cursor: 1,4
ext: surround
keys: ds[
--- vim 9.1
expect-text:
  x a, b {c} <d> y
expect-cursor: 1,3
expect-mode: normal

=== ds with r
text:
  x [a, b] {c} <d> y
cursor: 1,4
ext: surround
keys: dsr
--- vim 9.1
expect-text:
  x a, b {c} <d> y
expect-cursor: 1,3
expect-mode: normal

=== ds removes braces
text:
  x [a, b] {c} <d> y
cursor: 1,11
ext: surround
keys: ds{
--- vim 9.1
expect-text:
  x [a, b] c <d> y
expect-cursor: 1,10
expect-mode: normal

=== ds with B
text:
  x [a, b] {c} <d> y
cursor: 1,11
ext: surround
keys: dsB
--- vim 9.1
expect-text:
  x [a, b] c <d> y
expect-cursor: 1,10
expect-mode: normal

=== ds removes angle brackets
text:
  x [a, b] {c} <d> y
cursor: 1,15
ext: surround
keys: ds<lt>
--- vim 9.1
expect-text:
  x [a, b] {c} d y
expect-cursor: 1,14
expect-mode: normal

=== ds with a
text:
  x [a, b] {c} <d> y
cursor: 1,15
ext: surround
keys: dsa
--- vim 9.1
expect-text:
  x [a, b] {c} d y
expect-cursor: 1,14
expect-mode: normal

=== ds removes single quotes
text:
  'single' `tick` z
cursor: 1,3
ext: surround
keys: ds'
--- vim 9.1
expect-text:
  single `tick` z
expect-cursor: 1,1
expect-mode: normal

=== ds removes backticks
text:
  'single' `tick` z
cursor: 1,11
ext: surround
keys: ds`
--- vim 9.1
expect-text:
  'single' tick z
expect-cursor: 1,10
expect-mode: normal

=== ds with an opening bracket trims spaces
text:
  f( a b )
cursor: 1,5
ext: surround
keys: ds(
--- vim 9.1
expect-text:
  fa b
expect-cursor: 1,2
expect-mode: normal

=== ds with a closing bracket keeps spaces
text:
  f( a b )
cursor: 1,5
ext: surround
keys: ds)
--- vim 9.1
expect-text:
  f a b $
expect-cursor: 1,2
expect-mode: normal

=== ds with a count
text:
  ((a) b)
cursor: 1,3
ext: surround
keys: 2ds(
--- vim 9.1
expect-text:
  (a) b
expect-cursor: 1,1
expect-mode: normal

=== ds with a count in the target
text:
  ((a) b)
cursor: 1,3
ext: surround
keys: ds2(
--- vim 9.1
expect-text:
  (a) b
expect-cursor: 1,1
expect-mode: normal

=== ds removes a tag
text:
  <p><b>bold</b> text</p>
cursor: 1,8
ext: surround
keys: dst
--- vim 9.1
expect-text:
  <p>bold text</p>
expect-cursor: 1,4
expect-mode: normal

=== ds removes punctuation
text:
  x |a b| y
cursor: 1,5
ext: surround
keys: ds|
--- vim 9.1
expect-text:
  x a b y
expect-cursor: 1,3
expect-mode: normal

=== ds with no target does nothing
text:
  abc
cursor: 1,2
ext: surround
keys: ds(
--- vim 9.1
expect-text:
  abc
expect-cursor: 1,2
expect-mode: normal

=== ds with Esc does nothing
text:
  He said "hello" to (them) now
cursor: 1,11
ext: surround
keys: ds<Esc>
--- vim 9.1
expect-text:
  He said "hello" to (them) now
expect-cursor: 1,11
expect-mode: normal

=== ds over lines
text:
  f(
    a,
    b
  )
cursor: 2,3
ext: surround
keys: ds(
--- vim 9.1
expect-text:
  fa,
    b
  $
expect-cursor: 1,2
expect-mode: normal

=== cs changes double to single quotes
text:
  He said "hello" to (them) now
cursor: 1,11
ext: surround
keys: cs"'
--- vim 9.1
expect-text:
  He said 'hello' to (them) now
expect-cursor: 1,9
expect-mode: normal

=== cs changes parentheses to brackets
text:
  He said "hello" to (them) now
cursor: 1,22
ext: surround
keys: cs(]
--- vim 9.1
expect-text:
  He said "hello" to [them] now
expect-cursor: 1,20
expect-mode: normal

=== cs to an opening bracket adds spaces
text:
  He said "hello" to (them) now
cursor: 1,22
ext: surround
keys: cs)[
--- vim 9.1
expect-text:
  He said "hello" to [ them ] now
expect-cursor: 1,20
expect-mode: normal

=== cs to braces
text:
  He said "hello" to (them) now
cursor: 1,22
ext: surround
keys: cs(}
--- vim 9.1
expect-text:
  He said "hello" to {them} now
expect-cursor: 1,20
expect-mode: normal

=== cs to a tag
text:
  He said "hello" to (them) now
cursor: 1,11
ext: surround
keys: cs"<lt>em>
--- vim 9.1
expect-text:
  He said <em>hello</em> to (them) now
expect-cursor: 1,9
expect-mode: normal

=== cs with t reads the tag
text:
  He said "hello" to (them) now
cursor: 1,11
ext: surround
keys: cs"tem<CR>
--- vim 9.1
expect-text:
  He said <em>hello</em> to (them) now
expect-cursor: 1,9
expect-mode: normal

=== cs from a tag to quotes
text:
  <b>bold</b>
cursor: 1,5
ext: surround
keys: cst"
--- vim 9.1
expect-text:
  "bold"
expect-cursor: 1,1
expect-mode: normal

=== cs from a tag keeps its attributes
text:
  <a href="x">t</a>
cursor: 1,14
ext: surround
keys: csttb<CR>
--- vim 9.1
expect-text:
  <b href="x">t</b>
expect-cursor: 1,1
expect-mode: normal

=== cs from a tag drops the attributes with a closing bracket
text:
  <a href="x">t</a>
cursor: 1,14
ext: surround
keys: cstt<lt>b>
--- vim 9.1
expect-text:
  <<b>t</<b>
expect-cursor: 1,1
expect-mode: normal

=== cs to a function
text:
  He said "hello" to (them) now
cursor: 1,22
ext: surround
keys: cs(fprint<CR>
--- vim 9.1
expect-text:
  He said "hello" to print(them) now
expect-cursor: 1,20
expect-mode: normal

=== cs to a space-padded bracket
text:
  He said "hello" to (them) now
cursor: 1,22
ext: surround
keys: cs( [
--- vim 9.1
expect-text:
  He said "hello" to [  them  ] now
expect-cursor: 1,20
expect-mode: normal

=== cS puts the text on lines of its own
text:
  He said "hello" to (them) now
cursor: 1,22
ext: surround
keys: cS({
--- vim 9.1
expect-text:
  He said "hello" to {
  	them
  } now
expect-cursor: 1,20
expect-mode: normal

=== cs with Esc for the replacement does nothing
text:
  He said "hello" to (them) now
cursor: 1,11
ext: surround
keys: cs"<Esc>
--- vim 9.1
expect-text:
  He said "hello" to (them) now
expect-cursor: 1,11
expect-mode: normal

=== ys surrounds a word with parentheses
text:
  one two three
cursor: 1,5
ext: surround
keys: ysiw)
--- vim 9.1
expect-text:
  one (two) three
expect-cursor: 1,5
expect-mode: normal

=== ys with an opening parenthesis adds spaces
text:
  one two three
cursor: 1,5
ext: surround
keys: ysiw(
--- vim 9.1
expect-text:
  one ( two ) three
expect-cursor: 1,5
expect-mode: normal

=== ys with b
text:
  one two three
cursor: 1,5
ext: surround
keys: ysiwb
--- vim 9.1
expect-text:
  one (two) three
expect-cursor: 1,5
expect-mode: normal

=== ys with brackets
text:
  one two three
cursor: 1,5
ext: surround
keys: ysiw]
--- vim 9.1
expect-text:
  one [two] three
expect-cursor: 1,5
expect-mode: normal

=== ys with braces
text:
  one two three
cursor: 1,5
ext: surround
keys: ysiw}
--- vim 9.1
expect-text:
  one {two} three
expect-cursor: 1,5
expect-mode: normal

=== ys with angle brackets
text:
  one two three
cursor: 1,5
ext: surround
keys: ysiw>
--- vim 9.1
expect-text:
  one <two> three
expect-cursor: 1,5
expect-mode: normal

=== ys with quotes
text:
  one two three
cursor: 1,5
ext: surround
keys: ysiw"
--- vim 9.1
expect-text:
  one "two" three
expect-cursor: 1,5
expect-mode: normal

=== ys with a tag
text:
  one two three
cursor: 1,5
ext: surround
keys: ysiwtem<CR>
--- vim 9.1
expect-text:
  one <em>two</em> three
expect-cursor: 1,5
expect-mode: normal

=== ys with a tag closed by its bracket
text:
  one two three
cursor: 1,5
ext: surround
keys: ysiw<lt>em>
--- vim 9.1
expect-text:
  one <em>two</em> three
expect-cursor: 1,5
expect-mode: normal

=== ys with a tag and attributes
text:
  one two three
cursor: 1,5
ext: surround
keys: ysiwta href="x"<CR>
--- vim 9.1
expect-text:
  one <a href="x">two</a> three
expect-cursor: 1,5
expect-mode: normal

=== ys with a function
text:
  one two three
cursor: 1,5
ext: surround
keys: ysiwfprint<CR>
--- vim 9.1
expect-text:
  one print(two) three
expect-cursor: 1,5
expect-mode: normal

=== ys with a spaced function
text:
  one two three
cursor: 1,5
ext: surround
keys: ysiwFprint<CR>
--- vim 9.1
expect-text:
  one print( two ) three
expect-cursor: 1,5
expect-mode: normal

=== ys with a space before the character
text:
  one two three
cursor: 1,5
ext: surround
keys: ysiw )
--- vim 9.1
expect-text:
  one ( two ) three
expect-cursor: 1,5
expect-mode: normal

=== ys over a motion
text:
  one two three
cursor: 1,5
ext: surround
keys: ys$"
--- vim 9.1
expect-text:
  one "two three"
expect-cursor: 1,5
expect-mode: normal

=== ys over two words
text:
  one two three
cursor: 1,1
ext: surround
keys: ys2w*
--- vim 9.1
expect-text:
  *one two* three
expect-cursor: 1,1
expect-mode: normal

=== ys over a linewise motion
text:
  one
  two
  three
cursor: 1,1
ext: surround
keys: ysj)
--- vim 9.1
expect-text:
  (
  one
  two
  )
  three
expect-cursor: 1,1
expect-mode: normal

=== yss surrounds the line's text
text:
    one two  $
cursor: 1,4
ext: surround
keys: yss)
--- vim 9.1
expect-text:
    (one two)  $
expect-cursor: 1,3
expect-mode: normal

=== yss with a count
text:
  one
  two
  three
cursor: 1,1
ext: surround
keys: 2yss]
--- vim 9.1
expect-text:
  [one
  two]
  three
expect-cursor: 1,1
expect-mode: normal

=== yS puts the text on lines of its own
text:
  one two three
cursor: 1,5
ext: surround
keys: ySiw{
--- vim 9.1
expect-text:
  one {
  	two
  } three
expect-cursor: 1,5
expect-mode: normal

=== ySS surrounds the line on lines of its own
text:
    one two
cursor: 1,4
ext: surround
keys: ySS{
--- vim 9.1
expect-text:
    {
  	  one two
    }
expect-cursor: 1,1
expect-mode: normal

=== ys with Esc does nothing
text:
  one two three
cursor: 1,5
ext: surround
keys: ysiw<Esc>
--- vim 9.1
expect-text:
  one two three
expect-cursor: 1,5
expect-mode: normal

=== Visual S
text:
  one two three
cursor: 1,5
ext: surround
keys: viwS"
--- vim 9.1
expect-text:
  one "two" three
expect-cursor: 1,5
expect-mode: normal

=== Visual S with a tag
text:
  one two three
cursor: 1,5
ext: surround
keys: viwStb<CR>
--- vim 9.1
expect-text:
  one <b>two</b> three
expect-cursor: 1,5
expect-mode: normal

=== Visual line S
text:
  one
  two
cursor: 1,1
ext: surround
keys: VjS(
--- vim 9.1
expect-text:
  (
  	one
  	two
  )
expect-cursor: 1,1
expect-mode: normal

=== Visual block S
text:
  abc
  def
cursor: 1,1
ext: surround
keys: <C-v>jlS)
--- vim 9.1
expect-text:
  (ab)c
  (de)f
expect-cursor: 1,1
expect-mode: normal

=== Visual gS
text:
  one two three
cursor: 1,5
ext: surround
keys: viwgS{
--- vim 9.1
expect-text:
  one {
  	two
  } three
expect-cursor: 1,5
expect-mode: normal

=== dot repeats ds
text:
  "a" "b"
cursor: 1,2
ext: surround
keys: ds"w.
--- vim 9.1
expect-text:
  a b
expect-cursor: 1,3
expect-mode: normal

=== dot repeats cs
text:
  "a" "b"
cursor: 1,2
ext: surround
keys: cs"'w.
--- vim 9.1
expect-text:
  'a' 'b'
expect-cursor: 1,5
expect-mode: normal

=== dot repeats ys
text:
  one two three
cursor: 1,1
ext: surround
keys: ysiw)ww.
--- vim 9.1
expect-text:
  (one()) two three
expect-cursor: 1,5
expect-mode: normal

=== dot repeats cs with a tag
text:
  "a" "b"
cursor: 1,2
ext: surround
keys: cs"tp<CR>w.
--- vim 9.1
expect-text:
  <p>a</p> <p>b</p>
expect-cursor: 1,10
expect-mode: normal

=== u undoes ds in one step
text:
  He said "hello" to (them) now
cursor: 1,11
ext: surround
keys: ds"u
--- vim 9.1
expect-text:
  He said "hello" to (them) now
expect-cursor: 1,10
expect-mode: normal

=== the default mappings
text:
  x
cursor: 1,1
ext: surround
keys: :nmap ys<CR>:nmap cs<CR>:nmap ds<CR>:xmap S<CR>
output: yes
--- vim 9.1
expect-text:
  x
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  yss           <Plug>Yssurround
  n  ys            <Plug>Ysurround
  n  cs            <Plug>Csurround
  n  ds            <Plug>Dsurround
  x  S             <Plug>VSurround
```

Create `plugins/editor/src/test/resources/vim/corpus/put-marks.case`:

```text
# Put: the [ and ] marks after a put of several lines.

=== vp multi-line charwise then backtick bracket
text:
  one two three
  four five
cursor: 1,5
register a: c "x\ny"
keys: viw"ap`[
--- vim 9.1
expect-text:
  one x
  y three
  four five
expect-cursor: 1,5
expect-mode: normal

=== vp linewise then backtick bracket
text:
  one two three
  four five
cursor: 1,5
register a: l "x\ny\n"
keys: viw"ap`[
--- vim 9.1
expect-text:
  one $
  x
  y
   three
  four five
expect-cursor: 2,1
expect-mode: normal

=== Vp linewise then backtick bracket
text:
  one two three
  four five
cursor: 2,3
register a: l "x\ny\n"
keys: V"ap`[
--- vim 9.1
expect-text:
  one two three
  x
  y
expect-cursor: 2,1
expect-mode: normal

=== Vp charwise then backtick bracket
text:
  one two three
  four five
cursor: 2,3
register a: c "x\ny"
keys: V"ap`[
--- vim 9.1
expect-text:
  one two three
  x
  y
expect-cursor: 2,1
expect-mode: normal

=== P multi-line charwise then backtick bracket
text:
  one two three
  four five
cursor: 1,5
register a: c "x\ny"
keys: "aP`[
--- vim 9.1
expect-text:
  one x
  ytwo three
  four five
expect-cursor: 1,5
expect-mode: normal

=== P linewise then backtick bracket
text:
  one two three
  four five
cursor: 2,3
register a: l "x\ny\n"
keys: "aP`[
--- vim 9.1
expect-text:
  one two three
  x
  y
  four five
expect-cursor: 2,1
expect-mode: normal

=== p linewise then backtick bracket
text:
  one two three
  four five
cursor: 1,3
register a: l "x\ny\n"
keys: "ap`[
--- vim 9.1
expect-text:
  one two three
  x
  y
  four five
expect-cursor: 2,1
expect-mode: normal

=== marks after vp multi-line
text:
  one two three
  four five
cursor: 1,5
register a: c "x\ny"
keys: viw"ap:marks []<CR>
output: yes
--- vim 9.1
expect-text:
  one x
  y three
  four five
expect-cursor: 1,5
expect-mode: normal
expect-output:
  mark line  col file/text
   [      1    4 one x
   ]      2    0 y three

=== marks after Vp
text:
  one two three
  four five
cursor: 2,3
register a: l "x\ny\n"
keys: V"ap:marks []<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  x
  y
expect-cursor: 2,1
expect-mode: normal
expect-output:
  mark line  col file/text
   [      2    0 x
   ]      3    0 y
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExtensionsTest' --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: FAIL: `anExtensionThatIsOffCannotRun` and `surroundAsksForATagOnTheCommandLine` fail, and so do `ext-surround.case`, three of `put-marks.case`'s cases, and `everyCommandHasACase`.

- [ ] **Step 3: Let a command read keys and a line after its own, and fix the put's mark**

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
    private int resumeEndLine = -1;
    private int changeDepth;

    Machine(VimEditor editor) {
```

with:

```java
    private int resumeEndLine = -1;
    private int changeDepth;
    /**
     * A command that reads more keys (a ported plugin's getchar() or input()) is suspended: it completes when the
     * last of them has run; {@code suspendedChange} says whether it changes text.
     */
    private boolean suspended, suspendedChange;

    Machine(VimEditor editor) {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
            Consumer<KeyInput> handler = argument;
            argument = null;
            if (key.is("<Esc>")) {
                reset();
                resumeIfInsertNormal();
```

with:

```java
            Consumer<KeyInput> handler = argument;
            argument = null;
            // A suspended command gets <Esc> too: a plugin's getchar() returns it.
            if (key.is("<Esc>") && !suspended) {
                reset();
                resumeIfInsertNormal();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
    /** A command completed: records it for {@code .} and closes its undo step, unless it entered Insert mode. */
    private void complete(boolean change) {
        if (change && !replaying) {
            recordRegister = register;
```

with:

```java
    /** A command completed: records it for {@code .} and closes its undo step, unless it entered Insert mode. */
    private void complete(boolean change) {
        if (suspended) {
            suspendedChange |= change;
            return;
        }
        if (change && !replaying) {
            recordRegister = register;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java

    /**
     * Keys a ported plugin runs with {@code :normal!} in the middle of a command (surround's {@code norm! d2i(}): the
     * command's own state is kept aside while they run and comes back after. What they change joins the command's undo
```

with:

```java

    /**
     * A command asks for one more key, which is not mapped (a ported plugin's getchar()): {@code next} gets it,
     * {@code <Esc>} included. The command completes when {@code next} returns without asking again; the key joins what
     * {@code .} repeats and the command stays one undo step.
     */
    void readKey(Consumer<KeyInput> next) {
        suspended = true;
        argument = key -> resume(() -> {
            record.add(key);
            next.accept(key);
        });
    }

    /**
     * A command asks for a line (a ported plugin's input()): the command line shows {@code prompt} and
     * {@code initial}, and {@code <CR>} gives {@code next} the text, {@code <Esc>} an empty one. {@code end}, when not 0, also ends the line and is kept
     * (surround's tag, which {@code >} ends). The keys typed join what {@code .} repeats.
     */
    void readLine(String prompt, String initial, char end, Consumer<String> next) {
        suspended = true;
        CommandLine line = editor.commandLine();
        line.input(prompt, initial, end, text -> resume(() -> {
            record.addAll(line.typed());
            next.accept(text);
        }), () -> resume(() -> {
            record.addAll(line.typed());
            next.accept("");
        }));
    }

    /** A suspended command goes on; if it fails, its undo step closes, and if it asks nothing more, it completes. */
    private void resume(Runnable body) {
        suspended = false;
        boolean change = suspendedChange;
        suspendedChange = false;
        try {
            body.run();
        } catch (RuntimeException failure) {
            if (suspended) {
                suspended = false;
                change |= suspendedChange;
                suspendedChange = false;
            }
            if (change) endChangeUnlessInserting();
            throw failure;
        }
        complete(change);
    }

    /**
     * Keys a ported plugin runs with {@code :normal!} in the middle of a command (surround's {@code norm! d2i(}): the
     * command's own state is kept aside while they run and comes back after. What they change joins the command's undo
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
        Consumer<KeyInput> arg = argument;
        Visual.Redo rv = recordVisual, vr = visualRedo;
        boolean ri = recordingInsert, rep = replaying;
        recordingInsert = false;
        reset();
        replaying = true;
```

with:

```java
        Consumer<KeyInput> arg = argument;
        Visual.Redo rv = recordVisual, vr = visualRedo;
        boolean ri = recordingInsert, rep = replaying, sus = suspended, susc = suspendedChange;
        recordingInsert = false;
        suspended = false;
        suspendedChange = false;
        reset();
        replaying = true;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
            recordingInsert = ri;
            replaying = rep;
        }
    }
```

with:

```java
            recordingInsert = ri;
            replaying = rep;
            suspended = sus;
            suspendedChange = susc;
        }
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
        argument = null;
        shorter = null;
        shown.clear();
        if (!recordingInsert) record.clear();
```

with:

```java
        argument = null;
        shorter = null;
        suspended = false;
        suspendedChange = false;
        shown.clear();
        if (!recordingInsert) record.clear();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/CommandLine.java`, replace:

```java
    private final VimEditor editor;
    private char type;
    private final StringBuilder text = new StringBuilder();
    private int cursor;
```

with:

```java
    private final VimEditor editor;
    private char type;
    /** What shows before the text: the type, or input()'s prompt. */
    private String prompt = "";
    /** For input(): a character that also ends the line, kept in the text; 0 for none. */
    private char end;
    private final StringBuilder text = new StringBuilder();
    private int cursor;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/CommandLine.java`, replace:

```java
        matches = null;
        this.type = type;
        this.history = history;
        this.changed = changed;
```

with:

```java
        matches = null;
        this.type = type;
        prompt = String.valueOf(type);
        end = 0;
        this.history = history;
        this.changed = changed;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/CommandLine.java`, replace:

```java
    char type() { return type; }

    String text() { return text.toString(); }
```

with:

```java
    char type() { return type; }

    /** @return what shows before the text: {@code :}, {@code /}, {@code ?}, or input()'s prompt */
    String prompt() { return prompt; }

    /**
     * input(): reads a line after {@code prompt}, starting with {@code initial}, with no history; {@code end} (when
     * not 0) also ends it and is kept.
     *
     * @param done runs with the text on {@code <CR>}
     * @param cancelled runs on {@code <Esc>}
     */
    void input(String prompt, String initial, char end, Consumer<String> done, Runnable cancelled) {
        start('@', List.of(), initial, null, done, cancelled);
        this.prompt = prompt;
        this.end = end;
    }

    String text() { return text.toString(); }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/CommandLine.java`, replace:

```java
        if (ch >= 0) {
            insert(new String(Character.toChars(ch)));
            return;
        }
```

with:

```java
        if (ch >= 0) {
            insert(new String(Character.toChars(ch)));
            if (end != 0 && ch == end) finish();
            return;
        }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    /** @return while a command line is typed, its text with the {@code :}, {@code /} or {@code ?} in front; else empty */
    public java.util.Optional<String> commandLineText() {
        return commandLine.active() ? java.util.Optional.of(commandLine.type() + commandLine.text()) : java.util.Optional.empty();
    }

    /** @return the command line's cursor, as an index into {@link #commandLineText()} */
    public int commandLineCursor() { return commandLine.cursor() + 1; }

    /**
```

with:

```java
    /** @return while a command line is typed, its text with the {@code :}, {@code /} or {@code ?} in front; else empty */
    public java.util.Optional<String> commandLineText() {
        return commandLine.active() ? java.util.Optional.of(commandLine.prompt() + commandLine.text()) : java.util.Optional.empty();
    }

    /** @return the command line's cursor, as an index into {@link #commandLineText()} */
    public int commandLineCursor() { return commandLine.cursor() + commandLine.prompt().length(); }

    /**
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Put.java`, replace:

```java
            return;
        }
        Pos newCursor = cursor.copy();
        int newLine = newCursor.line;
```

with:

```java
            return;
        }
        // '[ as it is now: set again after the text goes in, which would carry a mark set before along with it.
        Pos opStart = cursor.copy();
        Pos newCursor = cursor.copy();
        int newLine = newCursor.line;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Put.java`, replace:

```java
            if (n == 1) newLine = lnum;
        }
        Pos opStart = marks.get('[');
        if (type == Type.LINE) {
            opStart.col = 0;
            if (forward) opStart.line++;
            marks.set('[', opStart);
        }
        int lastLen = Math.max(0, y.getLast().length() - lenDiff);
        marks.set(']', new Pos(newLine, lastLen > 1 ? Text.previous(y.getLast(), lastLen) : 0));
```

with:

```java
            if (n == 1) newLine = lnum;
        }
        if (type == Type.LINE) {
            opStart.col = 0;
            if (forward) opStart.line++;
        }
        marks.set('[', opStart);
        int lastLen = Math.max(0, y.getLast().length() - lenDiff);
        marks.set(']', new Pos(newLine, lastLen > 1 ? Text.previous(y.getLast(), lastLen) : 0));
```

- [ ] **Step 4: Write surround**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Surround.java`:

```java
package dev.jasper.editor.vim;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The surround extension (spec 8), ported from Tim Pope's surround.vim 2.2: {@code ds}, {@code cs}, {@code cS},
 * {@code ys{motion}}, {@code yS{motion}}, {@code yss}, {@code ySS} and Visual {@code S} and {@code gS}. As the plugin
 * does, it deletes and puts through the unnamed register with Vim's own commands ({@code d2i"}, {@code ""P},
 * {@code gv"xy}), and reads its targets and replacements as keys, not mapped, and a tag or function name as a line.
 */
final class Surround {
    private Surround() { }

    private static final String OPTION = "surround";
    private static final String ESC = "\033", CTRL_C = "\003";

    static void register(VimCommands commands) {
        commands.action("<Plug>Dsurround", "delete the surroundings {char} (`ds`, with `set surround`)", true, Surround::delete);
        commands.action("<Plug>Csurround", "change the surroundings {char} to {char} (`cs`)", true, c -> change(c, false));
        commands.action("<Plug>CSurround", "change the surroundings, the text on lines of its own (`cS`)", true, c -> change(c, true));
        commands.operator("<Plug>Ysurround", "surround {motion} with {char} (`ys`)", (c, span) -> operator(c, span, false));
        commands.operator("<Plug>YSurround", "surround {motion} with {char}, on lines of its own (`yS`)", (c, span) -> operator(c, span, true));
        commands.action("<Plug>Yssurround", "surround [count] lines' text with {char} (`yss`)", true, Surround::lines);
        commands.action("<Plug>YSsurround", "surround [count] lines with {char}, on lines of their own (`ySS`)", true, Surround::linesOnTheirOwn);
        commands.visual("<Plug>VSurround", "surround the Visual area with {char} (`S`)", false, true, c -> visual(c, false));
        commands.visual("<Plug>VgSurround", "surround the Visual area with {char}, the other way about lines (`gS`)", false, true,
            c -> visual(c, true));
    }

    // ---------------------------------------------------------------- input

    /** A key as the plugin's getchar() gives it: the character, or its Vim codes. */
    private static String text(KeyInput key) { return KeyCodes.text(List.of(key)); }

    /** s:inputtarget: a key; digits read on (a count), and a space reads one more; {@code <Esc>} gives nothing. */
    private static void inputTarget(Context c, Consumer<String> then) {
        c.editor.machine().readKey(key -> target(c, text(key), then));
    }

    private static void target(Context c, String got, Consumer<String> then) {
        if (got.matches("\\d+")) {
            c.editor.machine().readKey(key -> target(c, got + text(key), then));
            return;
        }
        if (got.equals(" ")) {
            c.editor.machine().readKey(key -> finishTarget(got + text(key), then));
            return;
        }
        finishTarget(got, then);
    }

    private static void finishTarget(String got, Consumer<String> then) {
        then.accept(got.contains(ESC) || got.contains(CTRL_C) || got.contains("\0") ? "" : got);
    }

    /** s:inputreplacement: a key, and one more after a space; {@code <Esc>} gives nothing. */
    private static void inputReplacement(Context c, Consumer<String> then) {
        c.editor.machine().readKey(key -> {
            String got = text(key);
            if (got.equals(" ")) c.editor.machine().readKey(next -> then.accept(replacement(got + text(next))));
            else then.accept(replacement(got));
        });
    }

    private static String replacement(String got) { return got.contains(ESC) || got.contains(CTRL_C) ? "" : got; }

    /** The line a replacement reads (a tag, a LaTeX environment, a function name), or null when it reads none. */
    private record Prompt(String prompt, String initial, char end) { }

    /** The last surroundings {@code ds} or {@code cs} removed, for {@code T}'s default tag (s:lastdel). */
    private static String lastdel = "";

    private static Prompt prompt(String newchar) {
        String c = newchar.startsWith(" ") ? newchar.substring(1) : newchar;
        if (c.equals("t") || c.equals("T") || c.equals("\u0014") || c.equals("<")) {
            String initial = "";
            if (c.equals("T")) {
                Matcher m = Pattern.compile("<(.*?)>", Pattern.DOTALL).matcher(lastdel);
                if (m.find()) initial = m.group(1);
            }
            return new Prompt("<", initial, '>');
        }
        if (c.equals("l") || c.equals("\\")) return new Prompt("\\begin{", "", (char) 0);
        if (c.equals("f") || c.equals("F") || c.equals("\u0006")) return new Prompt("function: ", "", (char) 0);
        return null;
    }

    /** Reads the replacement's line, when it has one, then goes on with it (null when it has none). */
    private static void withInput(Context c, String newchar, Consumer<String> then) {
        Prompt p = prompt(newchar);
        if (p == null) then.accept(null);
        else c.editor.machine().readLine(p.prompt(), p.initial(), p.end(), then);
    }

    // ---------------------------------------------------------------- commands

    /** {@code <Plug>Dsurround}: s:dosurround(s:inputtarget()). */
    private static void delete(Context c) {
        Extensions.require(c, OPTION);
        inputTarget(c, target -> dosurround(c, target, null, null, false));
    }

    /** {@code <Plug>Csurround}: s:changesurround(), a target and then a replacement. */
    private static void change(Context c, boolean special) {
        Extensions.require(c, OPTION);
        inputTarget(c, a -> {
            if (a.isEmpty()) throw Context.fail();
            inputReplacement(c, b -> {
                if (b.isEmpty()) throw Context.fail();
                withInput(c, b, input -> dosurround(c, a, b, input, special));
            });
        });
    }

    /** {@code <Plug>Ysurround} and {@code <Plug>YSurround}: g@, then s:opfunc on the motion's text. */
    private static void operator(Context c, Span span, boolean special) {
        Extensions.require(c, OPTION);
        String type = Script.operatorMarks(c, span);
        read(c, type, special);
    }

    /** {@code <Plug>Yssurround}: {@code ^}, then g@ over {@code [count]g_}. */
    private static void lines(Context c) {
        Extensions.require(c, OPTION);
        int count = c.count;
        VimEditor editor = c.editor;
        Script.normal(editor, "^");
        Pos start = editor.cursor();
        if (!Script.normal(editor, count + "g_")) throw Context.fail();
        Pos end = editor.cursor();
        editor.marks().set('[', start);
        editor.marks().set(']', end);
        editor.setCursor(start);
        read(c, "char", false);
    }

    /** {@code <Plug>YSsurround}: g@ over {@code [count]_}, linewise, on lines of its own. */
    private static void linesOnTheirOwn(Context c) {
        Extensions.require(c, OPTION);
        VimEditor editor = c.editor;
        Pos cursor = editor.cursor();
        int last = cursor.line + c.count - 1;
        if (last >= editor.buffer().count()) throw Context.fail();
        editor.marks().set('[', new Pos(cursor.line, 0));
        editor.marks().set(']', new Pos(last, 0));
        read(c, "line", true);
    }

    /** Visual {@code S} and {@code gS}: {@code :<C-u>} leaves Visual mode; s:opfunc on the area, by visualmode(). */
    private static void visual(Context c, boolean other) {
        Extensions.require(c, OPTION);
        Mode mode = c.editor.mode();
        Visual.end(c.editor);
        String type = mode == Mode.VISUAL_LINE ? "V" : mode == Mode.VISUAL_BLOCK ? "\u0016" : "v";
        boolean special = mode == Mode.VISUAL_LINE != other;
        read(c, type, special);
    }

    /** s:opfunc's input: the replacement and its line, then the rest. */
    private static void read(Context c, String type, boolean special) {
        inputReplacement(c, ch -> {
            if (ch.isEmpty()) throw Context.fail();
            withInput(c, ch, input -> opfunc(c, type, special, ch, input));
        });
    }

    // ---------------------------------------------------------------- s:opfunc and s:dosurround

    /** s:opfunc: yank the text into the unnamed register, wrap it, and put it back over the text. */
    private static void opfunc(Context c, String aType, boolean special, String ch, String input) {
        VimEditor editor = c.editor;
        char reg = '"';
        Options options = editor.options();
        String clipboard = options.string("clipboard");
        withoutUnnamedClipboard(options);
        String regSave = Script.getreg(editor, reg), regType = Script.regtype(editor, reg);
        try {
            String type = aType;
            switch (aType) {
                case "char" -> {
                    Script.normal(editor, "v`[o`]\"" + reg + "y");
                    type = "v";
                }
                case "line" -> {
                    Script.normal(editor, "`[V`]\"" + reg + "y");
                    type = "V";
                }
                case "v", "V", "\u0016" -> Script.normal(editor, "gv\"" + reg + "y");
                default -> throw Context.fail();
            }
            String keeper = Script.getreg(editor, reg);
            String append = "";
            if (type.equals("v") && !aType.equals("v")) {
                // '\_s\@<!\s*$': the trailing blanks; Vim's \s is a space or a tab.
                Matcher m = Pattern.compile("(?<![ \\t\\n])[ \\t]*\\z").matcher(keeper);
                if (m.find()) {
                    append = m.group();
                    keeper = keeper.substring(0, m.start());
                }
            }
            Script.setreg(editor, reg, keeper, type);
            wrapreg(editor, reg, ch, "", special, input);
            if (type.equals("v") && !aType.equals("v") && !append.isEmpty())
                Script.setreg(editor, reg, Script.getreg(editor, reg) + append, "v");
            Script.normal(editor, "gvp`[");
        } finally {
            Script.setreg(editor, reg, regSave, regType.isEmpty() ? "v" : regType);
            options.set("clipboard", clipboard);
        }
    }

    private static void withoutUnnamedClipboard(Options options) {
        String kept = String.join(",", java.util.Arrays.stream(options.string("clipboard").split(","))
            .filter(item -> !item.equals("unnamed") && !item.equals("unnamedplus") && !item.isEmpty()).toList());
        options.set("clipboard", kept);
    }

    /**
     * s:dosurround: the text inside the target is deleted, then the target with it, and the inside is put back, wrapped
     * in the replacement when there is one.
     */
    private static void dosurround(Context c, String target, String newchar, String input, boolean special) {
        VimEditor editor = c.editor;
        Options options = editor.options();
        boolean solSave = options.flag("startofline");
        options.set("startofline", true);
        String clipboard = options.string("clipboard");
        withoutUnnamedClipboard(options);
        try {
            surroundBody(c, target, newchar, input, special);
        } finally {
            options.set("clipboard", clipboard);
            options.set("startofline", solSave);
        }
    }

    private static void surroundBody(Context c, String target, String newchar, String input, boolean special) {
        VimEditor editor = c.editor;
        Buffer b = editor.buffer();
        long scount = c.count;
        String ch = target;
        boolean spc = false;
        Matcher digits = Pattern.compile("^\\d+").matcher(ch);
        if (digits.find()) {
            scount *= Long.parseLong(digits.group());
            ch = ch.substring(digits.end());
        }
        if (ch.startsWith(" ")) {
            ch = ch.substring(1);
            spc = true;
        }
        if (ch.equals("a")) ch = ">";
        if (ch.equals("r")) ch = "]";
        if (newchar != null && newchar.isEmpty()) throw Context.fail();
        String original = Script.getreg(editor, '"'), otype = Script.regtype(editor, '"');
        Script.setreg(editor, '"', "", "v");
        String strcount = scount == 1 ? "" : String.valueOf(scount);
        boolean punct = ch.length() == 1 && isPunctOrSpace(ch.charAt(0));
        if (ch.equals("/")) {
            Script.normal(editor, strcount + "[/d" + strcount + "]/");
        } else if (punct && "[](){}<>\"'`".indexOf(ch.charAt(0)) < 0) {
            Script.normal(editor, "T" + key(ch));
            Pos p = editor.cursor();
            String line = b.line(p.line);
            if (p.col < line.length() && line.charAt(p.col) == ch.charAt(0)) Script.normal(editor, "l");
            Script.normal(editor, "dt" + key(ch));
        } else {
            Script.normal(editor, "d" + strcount + "i" + key(ch));
        }
        String keeper = Script.getreg(editor, '"');
        String okeeper = keeper;
        if (keeper.isEmpty()) {
            Script.setreg(editor, '"', original, otype.isEmpty() ? "v" : otype);
            return;
        }
        String oldline = b.line(editor.cursor().line);
        int oldlnum = editor.cursor().line;
        if (ch.equals("p")) Script.setreg(editor, '"', "", "V");
        else if (ch.equals("s") || ch.equals("w") || ch.equals("W")) Script.setreg(editor, '"', "", "v");
        else if (ch.length() == 1 && "\"'`".indexOf(ch.charAt(0)) >= 0) {
            Script.normal(editor, "i <Esc>d2i" + key(ch));
            Script.setreg(editor, '"', Script.getreg(editor, '"').replaceFirst(" ", ""), Script.regtype(editor, '"'));
        } else if (ch.equals("/")) {
            Script.normal(editor, "\"_x");
            Script.setreg(editor, '"', "/**/", "v");
            keeper = keeper.replaceFirst("\\A/\\*[ \\t]?", "").replaceFirst("[ \\t]?\\*\\z", "");
        } else if (punct && "[](){}<>".indexOf(ch.charAt(0)) < 0) {
            Script.normal(editor, "F" + key(ch));
            Script.normal(editor, "df" + key(ch));
        } else {
            backOneCharacter(editor);
            Script.normal(editor, "da" + key(ch));
        }
        String removed = Script.getreg(editor, '"');
        String rem2 = removed.replaceFirst("\n[^\n]*", "");
        String oldhead = oldline.substring(0, Math.max(0, oldline.length() - rem2.length()));
        String regtype = Script.regtype(editor, '"');
        if (ch.length() == 1 && "[({<T".indexOf(ch.charAt(0)) >= 0 || spc) keeper = keeper.replaceFirst("\\A[ \\t]+", "").replaceFirst("[ \\t]+\\z", "");
        // col("']") == col("$") && virtcol('.') + 1 == virtcol('$'): the delete ended the cursor's line.
        Pos markEnd = editor.marks().get(']');
        Pos cursor = editor.cursor();
        String cursorLine = b.line(cursor.line);
        String pcmd;
        boolean atEnd = markEnd != null && markEnd.col == cursorLine.length() && !cursorLine.isEmpty() && cursor.col == Text.last(cursorLine);
        if (atEnd) {
            if (oldhead.matches("[ \\t]*") && newchar == null)
                keeper = keeper.replaceFirst("\\A\\n" + Pattern.quote(oldhead) + "([ \\t]*[^\\n]*?)\\n[ \\t]*\\z", "$1");
            pcmd = "p";
        } else pcmd = "P";
        if (cursor.line + 1 < oldlnum && regtype.equals("V")) pcmd = "p";
        Script.setreg(editor, '"', keeper, regtype.isEmpty() ? "v" : regtype);
        if (newchar != null) wrapreg(editor, '"', newchar, removed, special, input);
        Script.normal(editor, "\"\"" + pcmd + "`[");
        String now = b.line(editor.cursor().line);
        if (now.matches("[ \\t]+") && keeper.matches("(?s)[ \\t]*\\n.*")) Script.normal(editor, "cc<Esc>");
        Script.setreg(editor, '"', original, otype.isEmpty() ? "v" : otype);
        lastdel = removed;
    }

    /** search('\m.', 'bW'): the character before the cursor, on an earlier line if need be. */
    private static void backOneCharacter(VimEditor editor) {
        Buffer b = editor.buffer();
        Pos p = editor.cursor();
        if (p.col > 0) {
            editor.setCursor(new Pos(p.line, Text.previous(b.line(p.line), p.col)), true);
            return;
        }
        for (int line = p.line - 1; line >= 0; line--) {
            String text = b.line(line);
            if (!text.isEmpty()) {
                editor.setCursor(new Pos(line, Text.last(text)), true);
                return;
            }
        }
    }

    /** [[:punct:][:space:]] for one ASCII character. */
    private static boolean isPunctOrSpace(char c) {
        return c < 128 && (Character.isWhitespace(c) || c > ' ' && c < 127 && !Character.isLetterOrDigit(c));
    }

    /** One character as keys: {@code <} is {@code <lt>}. */
    private static String key(String ch) { return ch.equals("<") ? "<lt>" : ch; }

    // ---------------------------------------------------------------- s:wrap

    private static void wrapreg(VimEditor editor, char reg, String ch, String removed, boolean special, String input) {
        String orig = Script.getreg(editor, reg);
        String type = Script.regtype(editor, reg).replaceFirst("\\d+$", "");
        String wrapped = wrap(editor, orig, ch, type, removed, special, input);
        Script.setreg(editor, reg, wrapped, type);
    }

    /** The bracket pairs: {@code b}, {@code (} and {@code )} stand for parentheses, and so on; an opening one adds spaces. */
    private static final String PAIRS = "b()B{}r[]a<>";

    /**
     * s:wrap: the text with the replacement's two sides around it; linewise (and with {@code special}) on lines of their
     * own, with the first line's indent, and blockwise around each line.
     */
    static String wrap(VimEditor editor, String string, String newcharIn, String type, String removed, boolean special, String input) {
        String keeper = string;
        String newchar = newcharIn;
        Buffer b = editor.buffer();
        String before = "", after = "";
        String initspaces;
        if (type.equals("V")) initspaces = leadingWhite(keeper);
        else initspaces = leadingWhite(b.line(editor.cursor().line));
        String extraspace = "";
        if (newchar.startsWith(" ")) {
            newchar = newchar.substring(1);
            extraspace = " ";
        }
        int idx = newchar.length() == 1 ? PAIRS.indexOf(newchar.charAt(0)) : -1;
        Object custom = newchar.length() == 1 ? editor.vim().variables().get("surround_" + (int) newchar.charAt(0)) : null;
        if (newchar.equals(" ")) {
            before = "";
            after = "";
        } else if (custom != null) {
            String all = custom.toString();
            before = extractBefore(all);
            after = extractAfter(all);
        } else if (newchar.equals("p")) {
            before = "\n";
            after = "\n\n";
        } else if (newchar.equals("s")) {
            before = " ";
            after = "";
        } else if (newchar.equals(":")) {
            before = ":";
            after = "";
        } else if (newchar.equals("t") || newchar.equals("T") || newchar.equals("\u0014") || newchar.equals("<")) {
            String tag = input == null ? "" : input;
            if (!tag.isEmpty()) {
                boolean keepAttributes = !tag.endsWith(">");
                tag = tag.replaceFirst(">*$", "");
                String attributes = "";
                if (keepAttributes) {
                    Matcher m = Pattern.compile("<[^ \\t\\n]+([\\s\\S]*?)>").matcher(removed);
                    if (m.find()) attributes = m.group(1);
                }
                if (tag.endsWith("/")) {
                    tag = tag.substring(0, tag.length() - 1);
                    before = "<" + tag + attributes + " />";
                    after = "";
                } else {
                    before = "<" + tag + attributes + ">";
                    after = "</" + tag.replaceFirst(" .*", "") + ">";
                }
                if (newchar.equals("\u0014")) {
                    if (type.equals("v") || type.equals("V")) before += "\n\t";
                    if (type.equals("v")) after = "\n" + after;
                }
            }
        } else if (newchar.equals("l") || newchar.equals("\\")) {
            String env = input == null ? "" : input;
            if (!env.isEmpty()) {
                env = "{" + env;
                env += closematch(env);
                before = "\\begin" + env;
                Matcher m = Pattern.compile("[^}]*").matcher(env);
                after = "\\end" + (m.find() ? m.group() : "") + "}";
            }
        } else if (newchar.equals("f") || newchar.equals("F")) {
            String fnc = input == null ? "" : input;
            if (!fnc.isEmpty()) {
                before = fnc.replaceFirst("\\($", "") + "(";
                after = ")";
                if (newchar.equals("F")) {
                    before += " ";
                    after = " " + after;
                }
            }
        } else if (newchar.equals("\u0006")) {
            String fnc = input == null ? "" : input;
            before = "(" + fnc + " ";
            after = ")";
        } else if (idx >= 0) {
            String spc = idx % 3 == 1 ? " " : "";
            int i = idx / 3 * 3;
            before = PAIRS.charAt(i + 1) + spc;
            after = spc + PAIRS.charAt(i + 2);
        } else if (newchar.equals(ESC) || newchar.equals("\u001d")) {
            before = "{\n\t";
            after = "\n}";
        } else if (!newchar.matches("\\p{Alpha}")) {
            before = newchar;
            after = newchar;
        }
        after = after.replace("\n", "\n" + initspaces);
        if (type.equals("V") || special && type.equals("v")) {
            before = before.replaceFirst(" +\\z", "");
            after = after.replaceFirst("^ +", "");
            if (!after.startsWith("\n")) after = initspaces + after;
            if (!keeper.endsWith("\n") && !after.startsWith("\n")) keeper += "\n";
            else if (keeper.endsWith("\n") && after.startsWith("\n")) after = after.substring(1);
            if (!keeper.startsWith("\n") && !endsWithBreak(before)) {
                before += "\n";
                if (special) before += "\t";
            } else if (keeper.startsWith("\n") && endsWithBreak(before)) keeper = keeper.substring(1);
            if (type.equals("V") && Pattern.compile("\\n[ \\t]*\\n\\z").matcher(keeper).find()) keeper = keeper.substring(0, keeper.length() - 1);
        }
        if (type.equals("V")) before = initspaces + before;
        if (endsWithBreak(before)) {
            if (type.equals("v")) keeper = initspaces + keeper;
            Matcher pad = Pattern.compile("\\n([ \\t]+)\\z").matcher(before);
            String padding = pad.find() ? pad.group(1) : "";
            before = before.replaceFirst("\\n[ \\t]+\\z", "\n");
            keeper = fixindent(editor, keeper, padding);
        }
        if (type.equals("V")) return before + keeper + after;
        if (type.startsWith("\u0016")) {
            String open = before.replace("\n", " "), close = after.replace("\n", " ");
            String[] lines = (keeper + "\n").split("\n", -1);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < lines.length - 1; i++) {
                if (i > 0) out.append('\n');
                out.append(open).append(lines[i]).append(close);
            }
            return out.toString();
        }
        return before + extraspace + keeper + extraspace + after;
    }

    /** {@code =~ '\n\s*$'}: a line break, then only blanks, at the end. */
    private static boolean endsWithBreak(String text) { return Pattern.compile("\\n[ \\t]*\\z").matcher(text).find(); }

    private static String extractBefore(String all) {
        int cr = all.indexOf('\r');
        int nl = all.indexOf('\n');
        int at = cr >= 0 ? cr : nl;
        return at < 0 ? "" : all.substring(0, at);
    }

    private static String extractAfter(String all) {
        int cr = all.indexOf('\r');
        int nl = all.indexOf('\n');
        int at = cr >= 0 ? cr : nl;
        return at < 0 ? "" : all.substring(at + 1);
    }

    /** s:closematch: the bracket that closes one left open at the end of a LaTeX environment's name. */
    private static String closematch(String str) {
        Matcher m = Pattern.compile(".[^\\[\\](){}<>]*$").matcher(str);
        String tail = m.find() ? m.group() : "";
        if (tail.matches("\\[.+")) return "]";
        if (tail.matches("\\(.+")) return ")";
        if (tail.matches("\\{.+")) return "}";
        return "";
    }

    /** s:fixindent: tabs as 'shiftwidth' spaces, each line after {@code spc}, and tabs back without 'expandtab'. */
    private static String fixindent(VimEditor editor, String str, String spc) {
        Options o = editor.options();
        String sw = " ".repeat(o.shiftwidth());
        String s = str.replace("\t", sw);
        String pad = spc.replace("\t", sw);
        s = Pattern.compile("(\\n|\\A)(?=.)").matcher(s).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + pad));
        if (!o.flag("expandtab")) s = s.replaceAll("[ \\t]{" + o.number("tabstop") + "}", "\t");
        return s;
    }

    private static String leadingWhite(String text) {
        int i = 0;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
        return text.substring(0, i);
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, replace:

```java
        ReplaceWithRegister.register(commands);
        Commentary.register(commands);
        return commands;
    }
```

with:

```java
        ReplaceWithRegister.register(commands);
        Commentary.register(commands);
        Surround.register(commands);
        return commands;
    }
```

- [ ] **Step 5: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains surround's commands.

- [ ] **Step 6: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExtensionsTest'
```

Expected: 7 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.vim.TypeaheadTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.ExtensionsTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 224 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1866 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`.

- [ ] **Step 7: Check the recording (needs `vim`, `git` and the network)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: `git status` lists only `ext-surround.case` and `put-marks.case` (`??`), unchanged from what you created.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v4a
git add plugins/editor/src/main/java/dev/jasper/editor/vim/Surround.java plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java plugins/editor/src/main/java/dev/jasper/editor/vim/CommandLine.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java plugins/editor/src/main/java/dev/jasper/editor/vim/Put.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java plugins/editor/src/test/resources/vim/corpus/ext-surround.case plugins/editor/src/test/resources/vim/corpus/put-marks.case docs/editor-vim-reference.md
git commit -m "Add the surround extension; fix the put mark after several lines

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: exchange

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/Exchange.java`, `plugins/editor/src/test/resources/vim/corpus/ext-exchange.case`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Consumes: `Script`, `Extensions` (Task 1).
- Produces: `Exchange.Region(text, type, start, end)`, `Exchange.highlight(editor, region)`; `VimEditor.exchange()` and `exchange(region)`, the text the first `cx` marked (the plugin's `b:exchange`), drawn with the 'hlsearch' colour until the second.

- [ ] **Step 1: Write the failing test and the corpus cases**

In `plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java`, replace:

```java
    }

    @Test void surroundAsksForATagOnTheCommandLine() throws Exception {
        onEdt(() -> {
```

with:

```java
    }

    @Test void theTextToExchangeIsHighlightedUntilTheSecond() throws Exception {
        onEdt(() -> {
            var editor = editor("one two\n");
            type(editor, ":set exchange<CR>cxiw");
            assertThat(editor.highlights()).anySatisfy(range -> assertThat(range).containsExactly(1, 0, 3));
            type(editor, "cxc");
            assertThat(editor.highlights()).isEmpty();
            type(editor, "cxiwwcxiw");
            assertThat(editor.buffer().line(0)).isEqualTo("two one");
            assertThat(editor.highlights()).isEmpty();
        });
    }

    @Test void surroundAsksForATagOnTheCommandLine() throws Exception {
        onEdt(() -> {
```

Create `plugins/editor/src/test/resources/vim/corpus/ext-exchange.case`:

```text
# exchange (tommcdo/vim-exchange): cx{motion}, cxx, Visual X and cxc.

=== cx swaps two words
text:
  one two three four
cursor: 1,1
ext: exchange
keys: cxiwwwcxiw
--- vim 9.1
expect-text:
  three two one four
expect-cursor: 1,11
expect-mode: normal

=== cx swaps a later word first
text:
  one two three four
cursor: 1,15
ext: exchange
keys: cxiw0cxiw
--- vim 9.1
expect-text:
  four two three one
expect-cursor: 1,1
expect-mode: normal

=== cx swaps words of different lengths
text:
  a longer b
cursor: 1,1
ext: exchange
keys: cxiw$cxiw
--- vim 9.1
expect-text:
  b longer a
expect-cursor: 1,10
expect-mode: normal

=== cxx swaps two lines
text:
  one
  two
  three
cursor: 1,1
ext: exchange
keys: cxxjjcxx
--- vim 9.1
expect-text:
  three
  two
  one
expect-cursor: 3,1
expect-mode: normal

=== cxx with a count
text:
  a
  b
  c
  d
cursor: 1,1
ext: exchange
keys: 2cxxjjcxx
--- vim 9.1
expect-text:
  c
  a
  b
  d
expect-cursor: 2,1
expect-mode: normal

=== cx swaps lines keeping each one's indent
text:
    one
      two
cursor: 1,1
ext: exchange
keys: cxxjcxx
--- vim 9.1
expect-text:
    two
      one
expect-cursor: 2,1
expect-mode: normal

=== Visual X swaps
text:
  one two three four
cursor: 1,1
ext: exchange
keys: viwXwwviwX
--- vim 9.1
expect-text:
  three two one four
expect-cursor: 1,11
expect-mode: normal

=== Visual X then cx
text:
  one two three four
cursor: 1,1
ext: exchange
keys: viwXwwcxiw
--- vim 9.1
expect-text:
  three two one four
expect-cursor: 1,11
expect-mode: normal

=== cx over a motion
text:
  one two three four
cursor: 1,1
ext: exchange
keys: cxewcxe
--- vim 9.1
expect-text:
  two one three four
expect-cursor: 1,5
expect-mode: normal

=== cx with text inside the first swaps the inner
text:
  (a b) c
cursor: 1,1
ext: exchange
keys: cxa(lcxiw
--- vim 9.1
expect-text:
  a c
expect-cursor: 1,2
expect-mode: normal

=== cx with text around the first
text:
  (a b) c
cursor: 1,2
ext: exchange
keys: cxiw0cxa(
--- vim 9.1
expect-text:
  a c
expect-cursor: 1,1
expect-mode: normal

=== overlapping text is not exchanged
text:
  one two three four
cursor: 1,1
ext: exchange
keys: cx2wwcxiw
--- vim 9.1
expect-text:
  twothree four
expect-cursor: 1,5
expect-mode: normal

=== cxc forgets the first text
text:
  one two three four
cursor: 1,1
ext: exchange
keys: cxiwcxcwwcxiw
--- vim 9.1
expect-text:
  one two three four
expect-cursor: 1,9
expect-mode: normal

=== cx between lines
text:
  one two
  three four
cursor: 1,1
ext: exchange
keys: cxiwjcxiw
--- vim 9.1
expect-text:
  three two
  one four
expect-cursor: 2,1
expect-mode: normal

=== dot repeats cx
text:
  one two three four
cursor: 1,1
ext: exchange
keys: cxiwww.
--- vim 9.1
expect-text:
  three two one four
expect-cursor: 1,11
expect-mode: normal

=== u undoes the exchange
text:
  one two three four
cursor: 1,1
ext: exchange
keys: cxiwwwcxiwu
--- vim 9.1
expect-text:
  one two three four
expect-cursor: 1,9
expect-mode: normal

=== cx over a block
text:
  ab cd
  ef gh
cursor: 1,1
ext: exchange
keys: cx<C-v>jl$hcx<C-v>jl
--- vim 9.1
expect-text:
  cb ad
  gf eh
expect-cursor: 1,5
expect-mode: normal

=== the default mappings
text:
  x
cursor: 1,1
ext: exchange
keys: :nmap cx<CR>:xmap X<CR>
output: yes
--- vim 9.1
expect-text:
  x
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  cxx           <Plug>(ExchangeLine)
  n  cxc           <Plug>(ExchangeClear)
  n  cx            <Plug>(Exchange)
  x  X             <Plug>(Exchange)
```

- [ ] **Step 2: Run it to see it fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExtensionsTest'
```

Expected: FAIL: `theTextToExchangeIsHighlightedUntilTheSecond` fails: `cx` is not mapped to anything that exists.

- [ ] **Step 3: Write exchange**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Exchange.java`:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;

/**
 * The exchange extension (spec 8), ported from Tom McDonald's exchange.vim: {@code cx{motion}} marks some text (shown
 * highlighted) and a second {@code cx{motion}} swaps it with that; {@code cxx} takes lines, Visual {@code X} the
 * area, and {@code cxc} forgets the first. As the plugin does, it puts each text over the other with
 * {@code `[v`]"zp}, re-indents whole lines to each other's indent, and leaves the cursor where the plugin does.
 */
final class Exchange {
    private Exchange() { }

    private static final String OPTION = "exchange";

    /**
     * Some text an exchange takes: as the plugin's dictionary, its text, type ({@code v}, {@code V} or CTRL-V) and
     * ends, 0-based; a linewise one ends past its last line's last character.
     */
    record Region(String text, String type, Pos start, Pos end) { }

    static void register(VimCommands commands) {
        commands.operator("<Plug>(Exchange)", "mark {motion}'s text for an exchange, or swap it with the text marked (`cx`, with `set exchange`)",
            (c, span) -> {
                Extensions.require(c, OPTION);
                set(c, Script.operatorMarks(c, span), false);
            });
        commands.action("<Plug>(ExchangeLine)", "exchange [count] lines (`cxx`)", true, c -> {
            Extensions.require(c, OPTION);
            if (!Script.normal(c.editor, (c.hasCount ? String.valueOf(c.count) : "") + "<Plug>(Exchange)_")) throw Context.fail();
        });
        commands.action("<Plug>(ExchangeClear)", "forget the text marked for an exchange (`cxc`)", false, c -> {
            Extensions.require(c, OPTION);
            clear(c.editor);
        });
        commands.visual("<Plug>(Exchange)", "mark the Visual area for an exchange, or swap it (`X`)", false, true, c -> {
            Extensions.require(c, OPTION);
            Mode mode = c.editor.mode();
            Visual.end(c.editor);
            set(c, mode == Mode.VISUAL_LINE ? "V" : mode == Mode.VISUAL_BLOCK ? "\u0016" : "v", true);
        });
    }

    /** s:exchange_set: the first text is kept (and highlighted); the second is swapped with it. */
    private static void set(Context c, String type, boolean visual) {
        VimEditor editor = c.editor;
        Region first = editor.exchange();
        if (first == null) {
            editor.exchange(get(editor, type, visual));
            return;
        }
        Region x = first, y = get(editor, type, visual);
        boolean reverse = false, expand = false;
        switch (compare(x, y)) {
            case "overlap" -> {
                editor.say("Exchange aborted: overlapping text");
                clear(editor);
                return;
            }
            case "outer" -> {
                expand = true;
                reverse = true;
                Region swap = x;
                x = y;
                y = swap;
            }
            case "inner" -> expand = true;
            case "gt" -> {
                reverse = true;
                Region swap = x;
                x = y;
                y = swap;
            }
            default -> { }
        }
        exchange(editor, x, y, reverse, expand);
        clear(editor);
    }

    /** s:exchange_clear. */
    private static void clear(VimEditor editor) { editor.exchange(null); }

    /** s:exchange_get: the text yanked, with the unnamed and clipboard registers put back. */
    private static Region get(VimEditor editor, String type, boolean visual) {
        Saved saved = Saved.of(editor, '"', '*', '+');
        try {
            Pos start, end;
            String kind;
            if (visual) {
                kind = type;
                start = MarkCommands.get(editor, '<');
                end = MarkCommands.get(editor, '>');
                Script.normal(editor, "gvy");
            } else {
                start = editor.marks().get('[');
                end = editor.marks().get(']');
                switch (type) {
                    case "line" -> {
                        kind = "V";
                        Script.normal(editor, "'[V']y");
                    }
                    case "block" -> {
                        kind = "\u0016";
                        Script.normal(editor, "`[<C-v>`]y");
                    }
                    default -> {
                        kind = "v";
                        Script.normal(editor, "`[v`]y");
                    }
                }
            }
            String text = Script.getreg(editor, '"');
            // s:apply_type: a linewise region ends past its last line (col('$')).
            if (kind.equals("V")) end = new Pos(end.line, editor.buffer().line(end.line).length());
            if (kind.equals("V")) start = new Pos(start.line, Math.min(start.col, Math.max(0, editor.buffer().line(start.line).length())));
            return new Region(text, kind, start.copy(), end.copy());
        } finally {
            saved.restore(editor);
        }
    }

    /** s:exchange: y becomes x's text and (unless x holds y) x becomes y's; whole lines keep each other's indent. */
    private static void exchange(VimEditor editor, Region x, Region y, boolean reverse, boolean expand) {
        Saved saved = Saved.of(editor, 'z', '"', '*', '+');
        Buffer b = editor.buffer();
        try {
            Object setting = editor.vim().variables().getOrDefault("exchange_indent", 1L);
            boolean indent = !setting.toString().equals("0") && x.type().equals("V") && y.type().equals("V");
            String xindent = "", yindent = "";
            if (indent) {
                xindent = leadingWhite(b.line(nextnonblank(b, y.start().line)));
                yindent = leadingWhite(b.line(nextnonblank(b, x.start().line)));
            }
            Pos view = editor.cursor();
            putOver(editor, y, x);
            if (!expand) putOver(editor, x, y);
            if (indent) {
                int xlines = 1 + x.end().line - x.start().line;
                int ylines = expand ? xlines : 1 + y.end().line - y.start().line;
                if (!expand) reindent(editor, x.start().line, ylines, yindent);
                reindent(editor, y.start().line - xlines + ylines, xlines, xindent);
            }
            // winrestview()
            editor.setCursor(clamp(b, view));
            if (!expand) fixCursor(editor, x, y, reverse);
        } finally {
            saved.restore(editor);
        }
    }

    /** {@code `[{type}`]"zp}: the region's text replaced by {@code with}'s. */
    private static void putOver(VimEditor editor, Region region, Region with) {
        editor.marks().set('[', region.start());
        editor.marks().set(']', region.end());
        Script.setreg(editor, 'z', with.text(), with.type());
        String type = region.type().equals("\u0016") ? "<C-v>" : region.type();
        Script.normal(editor, "`[" + type + "`]\"zp");
    }

    /** s:fix_cursor: the cursor at the start of the text that moved, allowing for how its size changed. */
    private static void fixCursor(VimEditor editor, Region x, Region y, boolean reverse) {
        Buffer b = editor.buffer();
        if (reverse) editor.setCursor(clamp(b, x.start()));
        else if (x.start().line == y.start().line) {
            int offset = x.end().col - y.end().col;
            editor.setCursor(clamp(b, new Pos(x.start().line, x.start().col - offset)));
        } else if (x.end().line - x.start().line != y.end().line - y.start().line) {
            int offset = x.end().line - y.end().line;
            editor.setCursor(clamp(b, new Pos(x.start().line - offset, x.start().col)));
        }
    }

    /** s:reindent: the lines get {@code newIndent} in place of the indent of the first that is not blank. */
    private static void reindent(VimEditor editor, int start, int lines, String newIndent) {
        Buffer b = editor.buffer();
        int ts = editor.options().number("tabstop");
        String indent = leadingWhite(b.line(nextnonblank(b, start)));
        int want = Text.virtcol(newIndent, newIndent.length(), ts), have = Text.virtcol(indent, indent.length(), ts);
        int last = Math.min(b.count() - 1, start + lines - 1);
        if (want > have) {
            for (int n = start; n <= last; n++) b.setLine(n, newIndent + b.line(n).substring(Math.min(indent.length(), b.line(n).length())));
        } else if (want < have) {
            boolean canDedent = true;
            for (int n = start; n <= last; n++) if (!b.line(n).startsWith(newIndent) && nextnonblank(b, n) == n) canDedent = false;
            if (canDedent)
                for (int n = start; n <= last; n++)
                    if (b.line(n).startsWith(newIndent)) b.setLine(n, newIndent + b.line(n).substring(Math.min(indent.length(), b.line(n).length())));
        }
    }

    /** s:compare: {@code lt}, {@code gt}, {@code outer}, {@code inner} or {@code overlap}. */
    private static String compare(Region x, Region y) {
        if (x.type().equals("\u0016") && y.type().equals("\u0016")) {
            if (intersects(x, y)) return "overlap";
            return x.start().col - y.start().col <= 0 ? "lt" : "gt";
        }
        if (x.start().compareTo(y.start()) <= 0 && x.end().compareTo(y.end()) >= 0) return "outer";
        if (y.start().compareTo(x.start()) <= 0 && y.end().compareTo(x.end()) >= 0) return "inner";
        if (x.start().compareTo(y.end()) <= 0 && y.start().compareTo(x.end()) <= 0) return "overlap";
        int cmp = x.start().compareTo(y.start());
        return cmp == 0 ? "overlap" : cmp < 0 ? "lt" : "gt";
    }

    private static boolean intersects(Region x, Region y) {
        return !(x.end().col < y.start().col || x.end().line < y.start().line || x.start().col > y.end().col || x.start().line > y.end().line);
    }

    /** nextnonblank(): the first line from {@code line} on with a non-blank character, or the line itself when none has. */
    private static int nextnonblank(Buffer b, int line) {
        for (int n = line; n < b.count(); n++) if (!b.line(n).isBlank()) return n;
        return Math.min(line, b.count() - 1);
    }

    private static String leadingWhite(String text) {
        int i = 0;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
        return text.substring(0, i);
    }

    private static Pos clamp(Buffer b, Pos p) {
        int line = Math.max(0, Math.min(p.line, b.count() - 1));
        String text = b.line(line);
        return new Pos(line, Math.max(0, Math.min(p.col, text.isEmpty() ? 0 : Text.last(text))));
    }

    /** The first text marked, highlighted as the plugin's matchadd() does: its lines and columns as offsets. */
    static List<int[]> highlight(VimEditor editor, Region region) {
        List<int[]> out = new ArrayList<>();
        if (region == null) return out;
        Buffer b = editor.buffer();
        int count = b.count();
        if (region.start().line >= count) return out;
        int last = Math.min(region.end().line, count - 1);
        switch (region.type()) {
            case "V" -> out.add(new int[] { 1, b.start(region.start().line), b.end(last) + 1 });
            case "v" -> {
                String end = b.line(last);
                int to = region.end().col < end.length() ? b.offset(last, Text.next(end, region.end().col)) : b.end(last);
                out.add(new int[] { 1, b.offset(region.start().line, Math.min(region.start().col, b.line(region.start().line).length())), to });
            }
            default -> {
                int left = Math.min(region.start().col, region.end().col), right = Math.max(region.start().col, region.end().col);
                for (int line = region.start().line; line <= last; line++) {
                    String text = b.line(line);
                    if (left >= text.length()) continue;
                    out.add(new int[] { 1, b.offset(line, left), b.offset(line, Math.min(text.length(), Text.next(text, Math.min(right, text.length() - 1)))) });
                }
            }
        }
        return out;
    }

    /** s:save_reg and s:restore_reg for a few registers; an empty register is left alone. */
    private record Saved(List<Character> names, List<String> texts, List<String> types) {
        static Saved of(VimEditor editor, char... names) {
            List<Character> n = new ArrayList<>();
            List<String> t = new ArrayList<>(), ty = new ArrayList<>();
            for (char name : names) {
                n.add(name);
                t.add(Script.getreg(editor, name));
                ty.add(Script.regtype(editor, name));
            }
            return new Saved(n, t, ty);
        }

        void restore(VimEditor editor) {
            for (int i = 0; i < names.size(); i++) if (!types.get(i).isEmpty()) Script.setreg(editor, names.get(i), texts.get(i), types.get(i));
        }
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
        @Override public void focusLost(java.awt.event.FocusEvent event) { marks.set('"', cursor()); }
    };
    /** A new language gives the tab its line comment as 'commentstring'. */
    private final java.beans.PropertyChangeListener languageListener = event -> languageChanged();
```

with:

```java
        @Override public void focusLost(java.awt.event.FocusEvent event) { marks.set('"', cursor()); }
    };
    /** The text exchange's first {@code cx} marked (the plugin's b:exchange), or null. */
    private Exchange.Region exchange;
    /** A new language gives the tab its line comment as 'commentstring'. */
    private final java.beans.PropertyChangeListener languageListener = event -> languageChanged();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    }

    /** Draws the Visual area, the 'hlsearch' matches and the incremental-search match. */
    void refreshHighlights() {
        List<int[]> ranges = new ArrayList<>();
```

with:

```java
    }

    /** @return the text exchange's first {@code cx} marked, or null */
    Exchange.Region exchange() { return exchange; }

    /** @param region the text an exchange has marked, highlighted until the second {@code cx}; null forgets it */
    void exchange(Exchange.Region region) {
        exchange = region;
        refreshHighlights();
    }

    /** Draws the Visual area, the 'hlsearch' matches, the incremental-search match and the text exchange marked. */
    void refreshHighlights() {
        List<int[]> ranges = new ArrayList<>();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
            ranges.addAll(searchMatches);
        }
        ranges.addAll(Highlights.visual(this));
        if (incsearch != null) ranges.add(new int[] { 0, incsearch[0], incsearch[1] });
```

with:

```java
            ranges.addAll(searchMatches);
        }
        ranges.addAll(Exchange.highlight(this, exchange));
        ranges.addAll(Highlights.visual(this));
        if (incsearch != null) ranges.add(new int[] { 0, incsearch[0], incsearch[1] });
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, replace:

```java
        Commentary.register(commands);
        Surround.register(commands);
        return commands;
    }
```

with:

```java
        Commentary.register(commands);
        Surround.register(commands);
        Exchange.register(commands);
        return commands;
    }
```

- [ ] **Step 4: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains exchange's commands.

- [ ] **Step 5: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExtensionsTest'
```

Expected: 8 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1884 tests pass.

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

Expected: `git status` lists only `ext-exchange.case` (`??`), unchanged from what you created.

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/vim-v4a
git add plugins/editor/src/main/java/dev/jasper/editor/vim/Exchange.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java plugins/editor/src/test/resources/vim/corpus/ext-exchange.case docs/editor-vim-reference.md
git commit -m "Add the exchange extension

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 5: Documentation, the spec amendments and the final check

**Files:**
- Modify: `docs/vim-divergences.md`, `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec

- [ ] **Step 1: List the differences**

In `docs/vim-divergences.md`, replace:

```markdown
- **Extensions come later.** `Plug '…'` and `packadd` lines fail, and are counted in the vimrc's notice, as in a Vim
  without a plugin manager; `let g:…` settings for extensions are kept for when they come.
```

with:

```markdown
- **Extensions are ports.** surround, commentary, exchange and ReplaceWithRegister follow their plugins' scripts on
  Jasper's own commands, and the corpus's `ext:` cases are recorded against the plugins, with tpope/vim-repeat, at the
  commits `plugins/editor/vim-oracle.properties` pins. A `Plug` line switches on an extension Jasper has and is
  otherwise ignored; `packadd` fails with E319 until matchit comes. `let g:…` settings are kept for the extensions.
```

Append to `docs/vim-divergences.md`:

```markdown
- **surround** has no Insert-mode `<C-s>` or `<C-g>s`, does not re-indent after surrounding lines (the plugin does
  only with 'indentexpr', 'cindent' or 'smartindent', which Jasper does not have), has no `ds/` (there is no `[/`
  motion), and takes `g:surround_{n}` custom surroundings without their `\1`…`\7` prompts. There are no buffer
  variables, so no `b:surround_…`.
- **commentary** takes 'commentstring' from the tab's language: the line comment RSyntaxTextArea knows for it
  (`//%s`, `#%s`), or Vim's `/* %s */`. There is no `:Commentary` command and no `b:commentary_format`.
- **exchange** highlights the first text in the 'hlsearch' colour; `:XchangeHighlightToggle`,
  `:XchangeClear` and `g:exchange_indent = '=='` are not there.
- **ReplaceWithRegister** cannot use the expression register (`"=`): Jasper has no expressions.
```

- [ ] **Step 2: Describe the extensions in the Editor guide**

In `docs/editor.md`, replace:

```markdown
- Not yet: emulated plugins such as surround and commentary. They follow in a later release. An uppercase mark
  (`mA`) set in one tab takes you back to that tab (`'A`).
```

with:

```markdown
- Extensions, as IdeaVim has them: `set surround` (or the plugin's `Plug 'tpope/vim-surround'` line) in the vimrc
  switches one on, with its plugin's keys. surround: `ds"`, `cs"'`, `ysiw)`, `yss]`, Visual `S`, tags with `t`
  (`cst<em>`). commentary: `gcc`, `gc{motion}`, Visual `gc`, `gcu`, and `dgc` for a comment block, with the tab's
  language's comments. exchange: `cx{motion}` twice swaps two texts; `cxx` lines, Visual `X`, `cxc` to forget.
  ReplaceWithRegister (`set ReplaceWithRegister`): `gr{motion}`, `grr` and Visual `gr` put a register over text. Each
  extension's `<Plug>` names can be mapped as in Vim (`:nmap <Leader>s <Plug>Ysurround`).
- Not yet: argtextobj, textobj-entire, textobj-indent, matchit, paragraph-motion, highlightedyank and sneak. They
  follow in a later release. An uppercase mark (`mA`) set in one tab takes you back to that tab (`'A`).
```

- [ ] **Step 3: Add the GUI checks for the user**

Append to `docs/editor-verification.md`:

```markdown

## Vim mode (plan V4a)

- [ ] 89. With `set surround` in the vimrc, `ysiw)` puts a word in parentheses, `cs)]` changes them to brackets, `ds]` removes them, and `.` repeats each.
- [ ] 90. `ysiwt`, typing `em` and `>`: the Vim bar shows `<em` as you type, and the word gets `<em>`…`</em>`.
- [ ] 91. With `set commentary` in a Java file, `gcc` comments the line with `//`, `gcc` again uncomments it, and `gcip` comments a paragraph; in a Python file `#` is used.
- [ ] 92. `Plug 'tpope/vim-commentary'` in the vimrc (instead of `set commentary`) switches it on too; a `Plug` line for another plugin causes no notice.
- [ ] 93. With `set exchange`, `cxiw` on one word highlights it; `cxiw` on another swaps the two and the highlight goes; `cxc` forgets a first word.
- [ ] 94. With `set ReplaceWithRegister`, `yiw` on one word, then `griw` on another replaces it and `p` still puts the first word.
- [ ] 95. `:nmap <Leader>s <Plug>Ysurround` then `\siw"` quotes a word.
- [ ] 96. `u` after each of these undoes it in one step.
```

- [ ] **Step 4: Record the state**

In `docs/STATUS.md`, replace:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V3b** (branch `claude/vim-v3b`, plan
```

with:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V4a** (branch `claude/vim-v4a`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v4a.md`, written 2026-09-28): the extension framework (`set {name}` and `Plug` lines switch
  one on, with its plugin's default mappings over `<Plug>` commands, hasmapto() and `g:…_no_mappings`) and ports of
  surround, commentary ('commentstring' from the tab's language), exchange and ReplaceWithRegister, which follow
  their plugins' scripts on the engine's commands (`Script`, `Machine.nested`; surround's getchar() and input() through
  `Machine.readKey` and `readLine`). The corpus's `ext:` cases are recorded against the plugins and tpope/vim-repeat at
  the commits `plugins/editor/vim-oracle.properties` pins. Also `g'` and `` g` ``, and the `'[` mark after a put of
  several lines (fixed). Every file in the plan was compiled and run against Vim 9.1 with the pinned plugins before the
  plan was written: 1880 corpus cases (138 new).
  - Not done: executing the plan; GUI checks 89–96 of the [checklist](editor-verification.md) (user-run); plan V4b
    (argtextobj, textobj-entire, textobj-indent, matchit, paragraph-motion, highlightedyank, sneak); push.
- **Editor Vim mode, plan V3b** (branch `claude/vim-v3b`, plan
```

- [ ] **Step 5: Amend the spec**

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
Plan V3b (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v3b.md`) amends sections 6.3, 7.1, 7.2, 10, 12 and 13, as marked **Amended (V3b)**.
```

with:

```markdown
Plan V3b (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v3b.md`) amends sections 6.3, 7.1, 7.2, 10, 12 and 13, as marked **Amended (V3b)**. Plan V4a (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v4a.md`) amends sections 6.3, 8, 11.1, 12 and 13, as marked **Amended (V4a)**.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
| sneak | `sneak`, `justinmk/vim-sneak` | `s{c}{c}`, `S{c}{c}`, `;`, `,`, `z` as operator target; opt-in because it takes over `s` | `g:sneak#s_next` |
```

with:

```markdown
| sneak | `sneak`, `justinmk/vim-sneak` | `s{c}{c}`, `S{c}{c}`, `;`, `,`, `z` as operator target; opt-in because it takes over `s` | `g:sneak#s_next` |

**Amended (V4a):** plan V4a delivers the framework and surround, commentary, exchange and ReplaceWithRegister; the rest come with V4b. Each extension is a port of its plugin's script onto the engine's commands (`Script`, `Machine.nested`), with the plugin's `<Plug>` names as its commands and its default mappings (with their hasmapto() checks and `g:…_no_mappings`); switching it off takes the mappings away. surround has no Insert-mode mappings, no re-indent, no `ds/` and no `\1`…`\7` prompts in `g:surround_{n}`; commentary's 'commentstring' is the tab's language's line comment, else Vim's `/* %s */`; exchange highlights with the 'hlsearch' colour and has none of its `:Xchange…` commands; ReplaceWithRegister has no `"=`. A `<Plug>` name that starts a longer one waits for the next key.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- **Recording:** `./gradlew :jasper-plugin-editor:recordVimCorpus` (not part of `check`; needs `vim` on `PATH`, or `-PvimBinary=…`). For each case it runs `vim -u NONE -i NONE -N -Es` with a generated script that sets options and the buffer, places the cursor, runs the vimrc lines, feeds the keys with `feedkeys(keys, 'xt')`, and writes text, cursor, mode, registers, marks and messages as JSON; the task rewrites everything below each case's `---` line. For `ext:` cases it first clones each extension's upstream repository at the commit pinned in `plugins/editor/vim-oracle.properties` into `build/vim-oracle/` and adds it to `runtimepath`. `-Pcase=<pattern>` records a subset.
```

with:

```markdown
- **Recording:** `./gradlew :jasper-plugin-editor:recordVimCorpus` (not part of `check`; needs `vim` on `PATH`, or `-PvimBinary=…`). For each case it runs `vim -u NONE -i NONE -N -Es` with a generated script that sets options and the buffer, places the cursor, runs the vimrc lines, feeds the keys with `feedkeys(keys, 'xt')`, and writes text, cursor, mode, registers, marks and messages as JSON; the task rewrites everything below each case's `---` line. For `ext:` cases it first clones each extension's upstream repository at the commit pinned in `plugins/editor/vim-oracle.properties` into `build/vim-oracle/` and adds it to `runtimepath`. `-Pcase=<pattern>` records a subset. **Amended (V4a):** the pins are `name = git URL commit` lines in `plugins/editor/vim-oracle.properties`, and every `ext:` case also loads tpope/vim-repeat, which the plugins use for `.`; the recorder finds the file in the nearest folder above the corpus that has one and clones into its `build/vim-oracle`. `ext:` names extensions by their switches (`ext: surround`); the recorder clears Vim's own mappings first, and there is no `vimrc:` field.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
 **Amended (V3b):** the vimrc options come with V3b
```

with:

```markdown
 **Amended (V4a):** the extension switches `surround`, `commentary`, `exchange` and `replacewithregister` (`set ReplaceWithRegister` too, as names are not case-sensitive), and 'commentstring' (`cms`, local), come with V4a. **Amended (V3b):** the vimrc options come with V3b
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
**Amended (V2a):** the Ex classes (`Ex`, `ExTable`, `ExCommand`, `ExInfo`, `ExLines`, `ExSubstitute`, `ExGlobal`) live in `dev.jasper.editor.vim`, not `vim.ex`: they use the engine's package-private API, and a separate package would need a public API or a dependency cycle. `vim.ui` depends on `vim` only. **Amended (V2b):** `VimHost.Documents` (a record of callbacks) is how the engine reaches the Editor's tabs; `ExFiles`, `Completion` and `LineNumbers` join `dev.jasper.editor.vim`, and there is no `RelativeGutter`. **Amended (V3a):** `Typeahead`, `Mappings`, `ExMaps`, `Macros` and `KeyCodes` join `dev.jasper.editor.vim`; there is no `KeyNotation` (`KeyInput` reads the notation). **Amended (V3b):** `Vimrc` and `ExVimrc` join `dev.jasper.editor.vim`; there is no `vim.ex` package.
```

with:

```markdown
**Amended (V2a):** the Ex classes (`Ex`, `ExTable`, `ExCommand`, `ExInfo`, `ExLines`, `ExSubstitute`, `ExGlobal`) live in `dev.jasper.editor.vim`, not `vim.ex`: they use the engine's package-private API, and a separate package would need a public API or a dependency cycle. `vim.ui` depends on `vim` only. **Amended (V2b):** `VimHost.Documents` (a record of callbacks) is how the engine reaches the Editor's tabs; `ExFiles`, `Completion` and `LineNumbers` join `dev.jasper.editor.vim`, and there is no `RelativeGutter`. **Amended (V3a):** `Typeahead`, `Mappings`, `ExMaps`, `Macros` and `KeyCodes` join `dev.jasper.editor.vim`; there is no `KeyNotation` (`KeyInput` reads the notation). **Amended (V3b):** `Vimrc` and `ExVimrc` join `dev.jasper.editor.vim`; there is no `vim.ex` package. **Amended (V4a):** the extensions live in `dev.jasper.editor.vim` too (`Extensions`, `Script`, `Surround`, `Commentary`, `Exchange`, `ReplaceWithRegister`): they use the engine's package-private API, so there is no `vim.ext` package.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- **Plan V4: extensions.** Section 8, with the pinned-upstream oracle.
```

with:

```markdown
- **Plan V4: extensions.** Section 8, with the pinned-upstream oracle. **Amended (V4a):** split in two by the user on 2026-09-28. Plan V4a: the framework, the oracle, surround, commentary, exchange and ReplaceWithRegister. Plan V4b: argtextobj, textobj-entire, textobj-indent, matchit (with `packadd`), paragraph-motion, highlightedyank and sneak.
```

- [ ] **Step 6: Run everything**

Run:

```bash
./gradlew check
```

Expected: BUILD SUCCESSFUL (4,505 tests).

`TerminalAppIntegrationTest`'s "reflow" case is known to fail now and then under the full check, unrelated to the Editor; if it does, run `./gradlew check` again.

- [ ] **Step 7: Check source hygiene**

Run the snippet in `AGENTS.md` ("Source hygiene"). Expected: no output.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v4a
git add docs
git commit -m "Document Vim mode V4a: extensions

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
