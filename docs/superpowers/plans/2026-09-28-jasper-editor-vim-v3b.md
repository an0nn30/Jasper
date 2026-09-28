# Editor Vim mode, plan V3b: the vimrc — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The rest of spec section 7 for the Editor's Vim mode: the vimrc (`plugins/dev.jasper.editor/data/vimrc`), read the first time Vim turns on and again with **Reload vimrc**, with comments, `\` continuation, `source`, `if has('ide')` blocks and the skipped-lines notice and `:messages`; `:let` for `mapleader`, registers, options and variables; IdeaVim's `sethandler` lines; `<Action>(id)`, `:action` and `:actionlist` through the Editor's commands; and the options vimrcs set.

**Architecture:** `Vimrc` ports Vim's do_source and getsourceline: it joins continued lines, follows `if`/`elseif`/`else`/`endif` (only `has('ide')`, `has('jasper')` and numbers are true), and runs each line with `Ex.sourceLine`, which gathers what the line says and returns its error; skipped lines are reported as Vim reports them (`Error detected while processing …`, `line    3:`, the error) into the message history. `ExVimrc` holds the commands (`:let`, `:source`, `:messages`, `:sethandler`, `:action`, `:actionlist`, and `:syntax` and `:filetype`, which do nothing). `VimHost.Actions` is how the engine reaches the Editor's commands; an `<Action>(id)` right-hand side becomes a key that runs the command where it is reached, in any mode. `EditorWindow` reads the vimrc into its `Vim`, shows the notice, and gives Reload vimrc and its commands to Vim.

**Tech Stack:** Java 25 (JBR), Swing, RSyntaxTextArea 3.6.0, JUnit 6 and AssertJ, Vim 9 (only for `recordVimCorpus`).

**Spec:** `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md` (plan V3b of section 13 as amended by V3a; sections 2.2, 5.5, 6.3, 7.1, 7.2 and 10). Plans V1a–V3a are merged; their headers describe the engine, the Ex command line, the typeahead and the mappings.

**Status:** Written 2026-09-28 on branch `claude/vim-v3b` (worktree `.worktrees/vim-v3b`, from `main` at c974ec4d). Every source file, test and corpus case below was compiled and run before the plan was written: against Vim 9.1 all 1742 corpus cases (28 new, in `vimrc.case`) passed, and what the recorder's Vim cannot do (files, `sethandler`, the Editor's commands) runs in `VimrcTest` and `EditorWindowTest`. Applied to a fresh checkout of `c974ec4d` exactly as written, `./gradlew check` passed (4,359 tests, 0 failures, 0 errors, 5 skipped). The first full run failed once in `TerminalAppIntegrationTest` "reflow", a terminal timing test this plan does not touch; it passed three reruns and the next full run.

## Deviations from the spec (approved scope, adjusted in this plan)

Task 5 writes these into the spec as **Amended (V3b)**.

- **`Plug '…'`, `packadd` and extension settings (`let g:…`, `set surround`) wait for plan V4.** `let g:…` is stored; `Plug` and `packadd` lines fail and are counted in the vimrc's notice, as Vim without a plugin manager reports them.
- **`:let` sets one literal:** a string (`'…'` or `"…"` with Vim's escapes, `\<Esc>` included) or a number, into a global variable (`mapleader` is `g:mapleader`), a register (`@a`, `@/`) or an option (`&tw`, `&l:sw`). Other Vim script fails with E319.
- **The vimrc runs in a tab:** the selected tab that has Vim on when it is read. Its mappings, options, variables and handler entries are global; a `:normal` in it acts on that tab.
- **`:messages`** keeps the errors given and the vimrc's report (Vim keeps every message), under Vim's "Messages maintainer" line.
- **`:source`**: a relative name is taken from the sourcing file's folder inside a vimrc and from the current file's folder when typed (Vim takes it from the current directory); it needs a name, and `:source!` fails.
- **Options vimrcs set are accepted and change nothing:** 'compatible', 'encoding', 'fileformat', 'ruler', 'laststatus', 'mouse', 'belloff', 'ttimeout', 'ttimeoutlen', 'syntax', 'undolevels' and 'visualbell' (spec 6.3 says 'undolevels' is capped by the Editor's limit; the Editor's limit simply applies). `:syntax` and `:filetype` do nothing.
- **An unknown `<Action>(id)`** fails with `Not an Editor command: id`, ending the mapping.
- **Fixed on the way:** `:set a? b?` with two queries on one line skipped the second option's first letters (V2a).

## Global Constraints

- Java 25 on the JetBrains Runtime; always `./gradlew`, never a system Gradle. Work in `.worktrees/vim-v3b` on `claude/vim-v3b`; check `git branch --show-current` before every commit (other sessions switch branches in the main checkout). Never commit on `main`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.
- The Editor plugin compiles against the SDK only (`verifyPluginArchitecture`, a root task). `dev.jasper.editor.vim` may use `vim.regex`, RSyntaxTextArea, Swing and the JDK; `dev.jasper.editor.vim.ui` adds `dev.jasper.editor.vim`; `dev.jasper.editor.ui` may use both. No SDK change.
- No interface without two real implementations; this plan adds none (`VimHost.Actions` is a record of callbacks, as `VimHost` is).
- Source hygiene: never put raw control, private-use or unpaired surrogate characters in `.java` files; use escapes. No run of three backticks may appear in any file this plan embeds (the docs link check strips fenced blocks lazily).
- **Do not launch the GUI.** GUI checks go in `docs/editor-verification.md` for the user.
- Engine code runs on the event thread in the app. Tests that type through a `VimEditor` run inside `SwingUtilities.invokeAndWait`.
- The corpus is the authority on Vim behaviour; mark a case `divergent:` only for a documented, deliberate difference.
- `vim` must be on `PATH` for `recordVimCorpus` (the dev Mac has Vim 9.1 at `/usr/bin/vim`). `check` never runs Vim.

## File structure

New, `plugins/editor/src/main/java/dev/jasper/editor/vim/`:

| File | Responsibility |
|---|---|
| `Vimrc.java` | reading a vimrc or sourced file: comments, continuation, `if has(…)`, `finish`, nesting, the report of skipped lines |
| `ExVimrc.java` | `:let`, `:source`, `:messages`, `:sethandler`, `:action`, `:actionlist`, `:syntax`, `:filetype` |

Changed there: `Vim` (variables, `leader`, the message history, the reading, `reset`), `Options` (the vimrc options; negative values Vim allows; the `:set a? b?` fix; `resetGlobal`), `Ex` (the commands, `sourceLine`), `ExTable` (IdeaVim's names), `ExMaps` (`<Leader>` from `mapleader`, `<Action>(id)`), `VimEditor` (errors join the history; `quietly`; action keys), `KeyInput` and `KeyCodes` (action keys), `VimHost` (`Actions`), `ExFiles` and `Completion` (`:source` paths).
Changed: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java` (reading the vimrc, Reload vimrc, the commands for Vim), `EditorSettings.java` (the `vim` setting names the vimrc).
Tests: `VimrcTest` (new); `ExMapsTest`, `ExTest`, `KeyInputTest`, `CompletionTest`, `VimCorpusTest`, `ui/EditorWindowTest`. Corpus: `vimrc.case`.
Docs: `docs/vim-divergences.md`, `docs/editor-vim-reference.md` (generated), `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec.

---

### Task 1: `:let`, the vimrc options and `:messages`

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/ExVimrc.java`, `plugins/editor/src/test/resources/vim/corpus/vimrc.case`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ExMapsTest.java`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Produces: `ExVimrc.let`, `ExVimrc.value(String) -> Object` (a String or a Long), `ExVimrc.messages`, `ExVimrc.ignored`; `Vim.variables() -> Map<String, Object>` (names without `g:`), `Vim.leader(name) -> String`, `Vim.remember(message)`, `Vim.messages() -> List<String>`; `ExMaps.parse(text, lhs, leader, localLeader)`; the options 'belloff', 'compatible', 'encoding', 'fileformat', 'laststatus', 'mouse', 'ruler', 'syntax', 'ttimeout', 'ttimeoutlen', 'undolevels', 'visualbell'; the Ex commands `:let`, `:messages`, `:syntax`, `:filetype`.

Every error message now joins Vim's message history, which `:messages` shows. These commands are what a vimrc says most; Task 2 reads them from the file.

- [ ] **Step 1: Write the failing tests and the corpus cases**

Create `plugins/editor/src/test/resources/vim/corpus/vimrc.case`:

```text
# The vimrc's commands typed on the command line: let, the vimrc options, syntax, filetype and messages.

=== let sets a register
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let @a = "xy"<CR>"ap
registers: a
--- vim 9.1
expect-text:
  oxyne two three
  four five six
  seven eight nine
expect-cursor: 1,3
expect-mode: normal
expect-register a: c "xy"

=== let sets a register linewise with a trailing line break
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let @a = "new\n"<CR>"ap
registers: a
--- vim 9.1
expect-text:
  one two three
  new
  four five six
  seven eight nine
expect-cursor: 2,1
expect-mode: normal
expect-register a: l "new\n"

=== let with a single-quoted string
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let @a = 'it''s \n'<CR>"ap
registers: a
--- vim 9.1
expect-text:
  oit's \nne two three
  four five six
  seven eight nine
expect-cursor: 1,8
expect-mode: normal
expect-register a: c "it's \\n"

=== let with escapes
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let @a = "a\tb\x41\u00e9"<CR>"ap
registers: a
--- vim 9.1
expect-text:
  oa	bAéne two three
  four five six
  seven eight nine
expect-cursor: 1,6
expect-mode: normal
expect-register a: c "a\tbAé"

=== let with a special key runs as a macro
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let @a = "A!\<lt>Esc>j"<CR>@a@a
registers: a
--- vim 9.1
expect-text:
  one two three!
  four five six!
  seven eight nine
expect-cursor: 3,14
expect-mode: normal
expect-register a: c "A!\u001bj"

=== let an upper-case register appends
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let @a = "x"<CR>:let @A = "y"<CR>"ap
registers: a
--- vim 9.1
expect-text:
  oxyne two three
  four five six
  seven eight nine
expect-cursor: 1,3
expect-mode: normal
expect-register a: c "xy"

=== let a number into a register
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let @a = 42<CR>"ap
registers: a
--- vim 9.1
expect-text:
  o42ne two three
  four five six
  seven eight nine
expect-cursor: 1,3
expect-mode: normal
expect-register a: c "42"

=== let sets the search register
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let @/ = "eight"<CR>n
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 3,7
expect-mode: normal

=== let with a comment after the value
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let @a = "q" " a comment<CR>"ap
registers: a
--- vim 9.1
expect-text:
  oqne two three
  four five six
  seven eight nine
expect-cursor: 1,2
expect-mode: normal
expect-register a: c "q"

=== let mapleader changes the Leader of later mappings
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let mapleader = ","<CR>:nmap <lt>Leader>d dd<CR>,d:nmap<CR>
output: yes
--- vim 9.1
expect-text:
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  ,d            dd

=== let g:mapleader is mapleader
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let g:mapleader = "_"<CR>:nmap <lt>Leader>x dd<CR>_x
--- vim 9.1
expect-text:
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal

=== a space as mapleader
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let mapleader = "\<lt>Space>"<CR>:nmap <lt>Leader>w dd<CR> w
--- vim 9.1
expect-text:
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal

=== let maplocalleader
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let maplocalleader = ";"<CR>:nmap <lt>LocalLeader>x dd<CR>:nmap<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  ;x            dd

=== mapleader applies when the mapping is made
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :nmap <lt>Leader>d dd<CR>:let mapleader = ","<CR>\d
--- vim 9.1
expect-text:
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal

=== let an option
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let &tw = 20<CR>:set tw?<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-output:
    textwidth=20

=== let a local option
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let &l:sw = 3<CR>:set sw?<CR>>>
output: yes
--- vim 9.1
expect-text:
     one two three
  four five six
  seven eight nine
expect-cursor: 1,4
expect-mode: normal
expect-output:
    shiftwidth=3

=== let a boolean option
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let &ic = 1<CR>:set ic?<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-output:
    ignorecase

=== let a variable does nothing visible
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :let g:loaded_foo = 1<CR>:let answer = 'yes'<CR>x
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal

=== syntax and filetype do nothing
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :syntax on<CR>:filetype plugin indent on<CR>x
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal

=== the vimrc options Vim has
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :set enc? ff? ru? ls? mouse? bo? ttimeout? ttm? vb? ul? cp? syntax?<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-output:
    encoding=utf-8
    fileformat=unix
  noruler
    laststatus=1
    mouse=
    belloff=
  nottimeout
    ttimeoutlen=-1
  novisualbell
    undolevels=1000
  nocompatible
    syntax=

=== setting the vimrc options
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :set nocompatible encoding=utf-8 ruler laststatus=2 mouse=a belloff=all ttimeout ttimeoutlen=50 visualbell undolevels=500 syntax=off<CR>:set cp? enc? ru? ls? mouse? bo? ttimeout? ttm? vb? ul? syntax?<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-output:
  nocompatible
    encoding=utf-8
    ruler
    laststatus=2
    mouse=a
    belloff=all
    ttimeout
    ttimeoutlen=50
    visualbell
    undolevels=500
    syntax=off

=== several options queried at once
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :set ts? sw? tw? ic?<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-output:
    tabstop=8
    shiftwidth=8
    textwidth=0
  noignorecase

=== negative ttimeoutlen and undolevels
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :set ttm=-1 ul=-1<CR>:set ttm? ul?<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-output:
    ttimeoutlen=-1
    undolevels=-1

=== fileformat must be unix, dos or mac
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :set ff=xx<CR>:set ff?<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E474: Invalid argument: ff=xx
    fileformat=unix

=== fileformat dos
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :set ff=dos<CR>:set ff?<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-output:
    fileformat=dos

=== messages shows the errors given
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :messages clear<CR>:foo<CR>:set xyz<CR>:messages<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E492: Not an editor command: foo
  E518: Unknown option: xyz
  Messages maintainer: The Vim Project
  E492: Not an editor command: foo
  E518: Unknown option: xyz

=== messages clear
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :foo<CR>:messages clear<CR>:messages<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E492: Not an editor command: foo
  Messages maintainer: The Vim Project

=== messages with a count shows the last ones
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: :messages clear<CR>:foo<CR>:bar<CR>:baz<CR>:2messages<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E492: Not an editor command: foo
  E492: Not an editor command: bar
  E492: Not an editor command: baz
  E492: Not an editor command: bar
  E492: Not an editor command: baz
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/ExMapsTest.java`, replace:

```java
    }

    @Test void showsKeysAsVimsListingDoes() {
        assertThat(ExMaps.display(KeyInput.parse("<Space>a b<Space>"), false)).isEqualTo("<Space>a b<Space>");
```

with:

```java
    }

    @Test void theLeadersAreTheirText() {
        assertThat(ExMaps.parse("<Leader>x<LocalLeader>", true, ",", " ")).isEqualTo(KeyInput.parse(",x<Space>"));
        assertThat(ExMaps.parse("<leader>\\", false, "\u00e9", "\\")).as("any text, even a backslash").isEqualTo(KeyInput.parse("\u00e9\\"));
    }

    @Test void showsKeysAsVimsListingDoes() {
        assertThat(ExMaps.display(KeyInput.parse("<Space>a b<Space>"), false)).isEqualTo("<Space>a b<Space>");
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExMapsTest'
```

Expected: FAIL: the test does not compile (`ExMaps.parse` takes no leaders).

- [ ] **Step 3: Write the commands**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ExVimrc.java`:

```java
package dev.jasper.editor.vim;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The vimrc's commands (spec 7.1), which also run typed after {@code :}: {@code :let} for variables
 * ({@code mapleader}), registers and options, {@code :source}, {@code :messages}, IdeaVim's {@code :sethandler},
 * {@code :action} and {@code :actionlist}, and {@code :syntax} and {@code :filetype}, which a vimrc may say and which
 * do nothing (the Editor highlights and indents by language).
 */
final class ExVimrc {
    private ExVimrc() { }

    private static final String NOT_AVAILABLE = "E319: Sorry, the command is not available in this version";

    /** {@code :let {name} = {value}}, where the name is a variable, {@code @r} or {@code &option}. */
    private static final Pattern LET = Pattern.compile("(g:)?([A-Za-z_][A-Za-z0-9_#.]*|@.|&(?:[lg]:)?[a-z]+)\\s*=\\s*(.*)");

    /**
     * ex_let for the forms spec 7.1 lists: a global variable ({@code mapleader} is {@code g:mapleader}), a register
     * ({@code let @a = "…"}; a value ending with a line break is linewise, as setreg makes it) or an option
     * ({@code let &tabstop = 4}), set to one string or number. Other Vim script fails with E319.
     */
    static void let(ExCommand cmd) {
        String arg = cmd.arg.strip();
        Matcher m = LET.matcher(arg);
        if (!m.matches()) throw new VimFailure(NOT_AVAILABLE);
        String name = m.group(2);
        Object value = value(m.group(3));
        VimEditor editor = cmd.editor;
        Vim vim = editor.vim();
        if (name.startsWith("@")) {
            if (m.group(1) != null) throw new VimFailure(NOT_AVAILABLE);
            char register = name.charAt(1);
            String text = value.toString();
            if (register == '/') {
                vim.search().save(Search.SEARCH, text);
                return;
            }
            if (!Registers.valid(register, true) || register == '_') throw new VimFailure("E354: Invalid register name: '" + register + "'");
            vim.registers().set(register, Registers.Content.of(text, text.endsWith("\n") ? Registers.Type.LINE : Registers.Type.CHAR));
            return;
        }
        if (name.startsWith("&")) {
            if (m.group(1) != null) throw new VimFailure(NOT_AVAILABLE);
            String option = name.substring(1);
            boolean local = option.startsWith("l:"), global = option.startsWith("g:");
            if (local || global) option = option.substring(2);
            Options.Def def = Options.def(option).orElseThrow(() -> new VimFailure("E355: Unknown option: " + name.substring(1)));
            Object set = switch (def.type()) {
                case BOOLEAN -> value instanceof Long n ? n != 0 : Boolean.valueOf(!value.toString().isEmpty() && !value.toString().equals("0"));
                case NUMBER -> value instanceof Long n ? (Object) (int) (long) n : number(value.toString());
                case STRING -> value.toString();
            };
            try {
                if (local) editor.options().setLocal(def.name(), set);
                else editor.options().set(def.name(), set);
            } catch (IllegalArgumentException bad) {
                throw new VimFailure(bad.getMessage());
            }
            return;
        }
        vim.variables().put(name, value);
    }

    private static int number(String text) {
        try { return Integer.parseInt(text.strip()); }
        catch (NumberFormatException notNumber) { return 0; }
    }

    /**
     * One literal value: a single-quoted string ({@code ''} is a quote), a double-quoted one with Vim's escapes
     * ({@code \n}, {@code \t}, {@code \e}, {@code \<Esc>}, {@code \x41}, {@code €}), or a decimal or hex number.
     * A trailing {@code "} comment is allowed; anything else after the value is an expression Jasper does not have.
     *
     * @return a String or a Long
     */
    static Object value(String text) {
        String t = text.strip();
        if (t.isEmpty()) throw new VimFailure("E15: Invalid expression: \"\"");
        int[] end = { 0 };
        Object value;
        char first = t.charAt(0);
        if (first == '\'') value = single(t, end);
        else if (first == '"') value = doubled(t, end);
        else {
            Matcher n = Pattern.compile("-?(0[xX][0-9a-fA-F]+|\\d+)").matcher(t);
            if (!n.lookingAt()) throw new VimFailure(NOT_AVAILABLE);
            String digits = n.group();
            boolean negative = digits.startsWith("-");
            String body = negative ? digits.substring(1) : digits;
            long v = body.startsWith("0x") || body.startsWith("0X") ? Long.parseLong(body.substring(2), 16) : Long.parseLong(body);
            value = negative ? -v : v;
            end[0] = n.end();
        }
        String rest = t.substring(end[0]).strip();
        if (!rest.isEmpty() && !rest.startsWith("\"")) throw new VimFailure(NOT_AVAILABLE);
        return value;
    }

    private static String single(String t, int[] end) {
        StringBuilder out = new StringBuilder();
        int i = 1;
        while (i < t.length()) {
            char c = t.charAt(i);
            if (c == '\'') {
                if (i + 1 < t.length() && t.charAt(i + 1) == '\'') {
                    out.append('\'');
                    i += 2;
                    continue;
                }
                end[0] = i + 1;
                return out.toString();
            }
            out.append(c);
            i++;
        }
        throw new VimFailure("E115: Missing single quote: " + t);
    }

    /** eval_string: a double-quoted string and its backslash escapes. */
    private static String doubled(String t, int[] end) {
        StringBuilder out = new StringBuilder();
        int i = 1;
        while (i < t.length()) {
            char c = t.charAt(i);
            if (c == '"') {
                end[0] = i + 1;
                return out.toString();
            }
            if (c != '\\' || i + 1 >= t.length()) {
                out.append(c);
                i++;
                continue;
            }
            char e = t.charAt(++i);
            i++;
            switch (e) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'e' -> out.append('\033');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'x', 'X', 'u', 'U' -> {
                    int max = e == 'u' ? 4 : e == 'U' ? 8 : 2;
                    int start = i;
                    while (i < t.length() && i - start < max && Character.digit(t.charAt(i), 16) >= 0) i++;
                    if (i == start) out.append(e);
                    else out.appendCodePoint(Integer.parseInt(t.substring(start, i), 16));
                }
                case '<' -> {
                    int close = t.indexOf('>', i);
                    List<KeyInput> key = close < 0 ? List.of() : KeyInput.parse("<" + t.substring(i, close + 1));
                    if (key.size() == 1 && !key.getFirst().equals(KeyInput.of('<'))) {
                        out.append(KeyCodes.text(key));
                        i = close + 1;
                    } else out.append('<');
                }
                default -> {
                    if (e >= '0' && e <= '7') {
                        int start = i - 1;
                        while (i < t.length() && i - start < 3 && t.charAt(i) >= '0' && t.charAt(i) <= '7') i++;
                        out.append((char) Integer.parseInt(t.substring(start, i), 8));
                    } else out.append(e);
                }
            }
        }
        throw new VimFailure("E114: Missing double quote: " + t);
    }

    /**
     * ex_messages: the message history under Vim's "Messages maintainer" line ({@code :messages clear} empties it;
     * {@code :3messages} shows the last three, without that line).
     */
    static void messages(ExCommand cmd) {
        List<String> history = cmd.editor.vim().messages();
        String arg = cmd.arg.strip();
        if (arg.equals("clear")) {
            history.clear();
            return;
        }
        if (!arg.isEmpty()) throw new VimFailure("E474: Invalid argument");
        if (cmd.addrCount == 0) cmd.editor.say("Messages maintainer: The Vim Project");
        int from = cmd.addrCount > 0 ? Math.max(0, history.size() - cmd.line2) : 0;
        for (String line : List.copyOf(history.subList(from, history.size()))) cmd.editor.say(line);
    }

    /** {@code :syntax} and {@code :filetype}: accepted, as vimrcs say them, and nothing happens. */
    static void ignored(ExCommand cmd) { }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, replace:

```java
        def("imapclear", EXTRA | TRLBAR, Addr.NONE, "remove every Insert-mode mapping", ExMaps::mapclear);
        def("cmapclear", EXTRA | TRLBAR, Addr.NONE, "remove every command-line mapping", ExMaps::mapclear);
    }
```

with:

```java
        def("imapclear", EXTRA | TRLBAR, Addr.NONE, "remove every Insert-mode mapping", ExMaps::mapclear);
        def("cmapclear", EXTRA | TRLBAR, Addr.NONE, "remove every command-line mapping", ExMaps::mapclear);
        def("let", EXTRA | NOTRLCOM, Addr.NONE, "`:let mapleader = \",\"`, `:let @a = \"…\"`, `:let &tw = 80`, `:let g:name = …`: a variable, register or option", ExVimrc::let);
        def("messages", RANGE | EXTRA | TRLBAR, Addr.OTHER, "the message history: errors, and the vimrc's skipped lines; `:messages clear`", ExVimrc::messages);
        def("syntax", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "accepted and ignored: the Editor highlights by language", ExVimrc::ignored);
        def("filetype", EXTRA | TRLBAR, Addr.NONE, "accepted and ignored: the Editor knows each file's language", ExVimrc::ignored);
    }
```

- [ ] **Step 4: Keep variables and the message history, add the options, and take `<Leader>` from `mapleader`**

The option parser also had a bug the new cases find: after `opt?` it skipped the option's length a second time, so
`:set ts? sw?` lost the start of `sw?`.

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, replace:

```java
    private final Mappings mappings = new Mappings();
    private final Macros macros = new Macros();
    private final VimCommands commands = VimCommands.standard();
    private Consumer<String> coverage = id -> { };
```

with:

```java
    private final Mappings mappings = new Mappings();
    private final Macros macros = new Macros();
    /** The global variables {@code :let} sets ({@code g:mapleader}, extension settings), by name without {@code g:}. */
    private final java.util.Map<String, Object> variables = new java.util.HashMap<>();
    /** The message history {@code :messages} shows, oldest first (Vim's 'msghistory', 500). */
    private final java.util.List<String> messages = new java.util.ArrayList<>();
    private final VimCommands commands = VimCommands.standard();
    private Consumer<String> coverage = id -> { };
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, replace:

```java
    /** @return the handler table */
    public KeyHandlers handlers() { return handlers; }

    /** @return macro recording and execution */
```

with:

```java
    /** @return the handler table */
    public KeyHandlers handlers() { return handlers; }

    /** @return the global variables, by name without {@code g:} */
    java.util.Map<String, Object> variables() { return variables; }

    /**
     * @param name {@code mapleader} or {@code maplocalleader}
     * @return what {@code <Leader>} or {@code <LocalLeader>} stands for: the variable, or a backslash when it is
     *     not set, empty or longer than Vim's 48 bytes
     */
    String leader(String name) {
        Object value = variables.get(name);
        String text = value == null ? "" : value.toString();
        return text.isEmpty() || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 48 ? "\\" : text;
    }

    /** An error given, or a line the vimrc skipped, joins the message history. */
    void remember(String message) {
        messages.add(message);
        if (messages.size() > 500) messages.removeFirst();
    }

    /** @return the message history, oldest first */
    java.util.List<String> messages() { return messages; }

    /** @return macro recording and execution */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    void message(String text, boolean error) {
        messageLog.add(text);
        if (collecting != null) {
            collecting.add(new OutputLine(text, error));
```

with:

```java
    void message(String text, boolean error) {
        messageLog.add(text);
        if (error) vim.remember(text);
        if (collecting != null) {
            collecting.add(new OutputLine(text, error));
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("autoindent", "ai", Type.BOOLEAN, false, true),
        new Def("backspace", "bs", Type.STRING, "indent,eol,start", false),
        new Def("clipboard", "cb", Type.STRING, "", false),
        new Def("comments", "com", Type.STRING, "s1:/*,mb:*,ex:*/,://,b:#,:%,:XCOMM,n:>,fb:-", true),
        new Def("expandtab", "et", Type.BOOLEAN, false, true),
        new Def("foldopen", "fdo", Type.STRING, "block,hor,mark,percent,quickfix,search,tag,undo", false),
        new Def("formatoptions", "fo", Type.STRING, "tcq", true),
```

with:

```java
        new Def("autoindent", "ai", Type.BOOLEAN, false, true),
        new Def("backspace", "bs", Type.STRING, "indent,eol,start", false),
        new Def("belloff", "bo", Type.STRING, "", false),
        new Def("clipboard", "cb", Type.STRING, "", false),
        new Def("comments", "com", Type.STRING, "s1:/*,mb:*,ex:*/,://,b:#,:%,:XCOMM,n:>,fb:-", true),
        new Def("compatible", "cp", Type.BOOLEAN, false, false),
        new Def("encoding", "enc", Type.STRING, "utf-8", false),
        new Def("expandtab", "et", Type.BOOLEAN, false, true),
        new Def("fileformat", "ff", Type.STRING, "unix", true),
        new Def("foldopen", "fdo", Type.STRING, "block,hor,mark,percent,quickfix,search,tag,undo", false),
        new Def("formatoptions", "fo", Type.STRING, "tcq", true),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("iskeyword", "isk", Type.STRING, "@,48-57,_,192-255", true),
        new Def("joinspaces", "js", Type.BOOLEAN, true, false),
        new Def("matchpairs", "mps", Type.STRING, "(:),{:},[:]", true),
        new Def("maxmapdepth", "mmd", Type.NUMBER, 1000, false),
        new Def("nrformats", "nf", Type.STRING, "bin,octal,hex", true),
        new Def("number", "nu", Type.BOOLEAN, true, true),
        new Def("relativenumber", "rnu", Type.BOOLEAN, false, true),
        new Def("report", "report", Type.NUMBER, 2, false),
        new Def("scrolljump", "sj", Type.NUMBER, 1, false),
        new Def("scrolloff", "so", Type.NUMBER, 0, false),
```

with:

```java
        new Def("iskeyword", "isk", Type.STRING, "@,48-57,_,192-255", true),
        new Def("joinspaces", "js", Type.BOOLEAN, true, false),
        new Def("laststatus", "ls", Type.NUMBER, 1, false),
        new Def("matchpairs", "mps", Type.STRING, "(:),{:},[:]", true),
        new Def("maxmapdepth", "mmd", Type.NUMBER, 1000, false),
        new Def("mouse", "mouse", Type.STRING, "", false),
        new Def("nrformats", "nf", Type.STRING, "bin,octal,hex", true),
        new Def("number", "nu", Type.BOOLEAN, true, true),
        new Def("relativenumber", "rnu", Type.BOOLEAN, false, true),
        new Def("report", "report", Type.NUMBER, 2, false),
        new Def("ruler", "ru", Type.BOOLEAN, false, false),
        new Def("scrolljump", "sj", Type.NUMBER, 1, false),
        new Def("scrolloff", "so", Type.NUMBER, 0, false),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("softtabstop", "sts", Type.NUMBER, 0, true),
        new Def("startofline", "sol", Type.BOOLEAN, true, false),
        new Def("tabstop", "ts", Type.NUMBER, 8, true),
        new Def("textwidth", "tw", Type.NUMBER, 0, true),
```

with:

```java
        new Def("softtabstop", "sts", Type.NUMBER, 0, true),
        new Def("startofline", "sol", Type.BOOLEAN, true, false),
        new Def("syntax", "syn", Type.STRING, "", true),
        new Def("tabstop", "ts", Type.NUMBER, 8, true),
        new Def("textwidth", "tw", Type.NUMBER, 0, true),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("timeout", "to", Type.BOOLEAN, true, false),
        new Def("timeoutlen", "tm", Type.NUMBER, 1000, false),
        new Def("whichwrap", "ww", Type.STRING, "b,s", false),
        new Def("wrap", "wrap", Type.BOOLEAN, true, true),
```

with:

```java
        new Def("timeout", "to", Type.BOOLEAN, true, false),
        new Def("timeoutlen", "tm", Type.NUMBER, 1000, false),
        new Def("ttimeout", "ttimeout", Type.BOOLEAN, false, false),
        new Def("ttimeoutlen", "ttm", Type.NUMBER, -1, false),
        new Def("undolevels", "ul", Type.NUMBER, 1000, false),
        new Def("visualbell", "vb", Type.BOOLEAN, false, false),
        new Def("whichwrap", "ww", Type.STRING, "b,s", false),
        new Def("wrap", "wrap", Type.BOOLEAN, true, true),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
            shown.add(showOne(def, scope));
            if (nextchar != '?' && nextchar != 0 && !isWhite(afterchar)) throw new IllegalArgumentException("E488: Trailing characters");
            return i + len;
        }
        Object old = value(def, scope);
```

with:

```java
            shown.add(showOne(def, scope));
            if (nextchar != '?' && nextchar != 0 && !isWhite(afterchar)) throw new IllegalArgumentException("E488: Trailing characters");
            // After "opt?" the index is already on the "?".
            return nextchar == '?' ? i + 1 : i + len;
        }
        Object old = value(def, scope);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
    }

    private static int checkNumber(Def def, int value) {
        if (value < 0 || def.name().equals("tabstop") && value == 0) throw new IllegalArgumentException("E487: Argument must be positive");
        return value;
    }
```

with:

```java
    }

    /** The number options Vim lets go below zero ('ttimeoutlen' -1 is "use 'timeoutlen'", 'undolevels' -1 no undo). */
    private static final List<String> NEGATIVE = List.of("laststatus", "maxmapdepth", "ttimeoutlen", "undolevels");

    private static int checkNumber(Def def, int value) {
        if (value < 0 && !NEGATIVE.contains(def.name()) || def.name().equals("tabstop") && value == 0)
            throw new IllegalArgumentException("E487: Argument must be positive");
        return value;
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
            case NUMBER -> {
                int number = (Integer) value;
                if (number < 0 || def.name().equals("tabstop") && number == 0) throw new IllegalArgumentException("E487: Argument must be positive: " + def.name() + "=" + number);
                yield number;
            }
```

with:

```java
            case NUMBER -> {
                int number = (Integer) value;
                if (number < 0 && !NEGATIVE.contains(def.name()) || def.name().equals("tabstop") && number == 0)
                    throw new IllegalArgumentException("E487: Argument must be positive: " + def.name() + "=" + number);
                yield number;
            }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
                String text = (String) value;
                if (def.name().equals("iskeyword")) Keywords.parse(text);
                yield text;
            }
```

with:

```java
                String text = (String) value;
                if (def.name().equals("iskeyword")) Keywords.parse(text);
                if (def.name().equals("fileformat") && !List.of("unix", "dos", "mac").contains(text))
                    throw new IllegalArgumentException("E474: Invalid argument");
                yield text;
            }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java`, replace:

```java
        boolean hasKey = !lhsText.isEmpty(), hasArg = !rhsText.isEmpty();
        if (unmap && !hasKey) throw new VimFailure("E474: Invalid argument");
        List<KeyInput> lhs = parse(lhsText, true);
        List<KeyInput> rhs = rhsText.equalsIgnoreCase("<Nop>") ? List.of() : parse(rhsText, false);
        if (hasKey && byteLength(lhs) > MAXMAPLEN) throw new VimFailure("E474: Invalid argument");
        if (!unmap && (!hasKey || !hasArg)) {
```

with:

```java
        boolean hasKey = !lhsText.isEmpty(), hasArg = !rhsText.isEmpty();
        if (unmap && !hasKey) throw new VimFailure("E474: Invalid argument");
        Vim vim = editor.vim();
        String leader = vim.leader("mapleader"), localLeader = vim.leader("maplocalleader");
        List<KeyInput> lhs = parse(lhsText, true, leader, localLeader);
        List<KeyInput> rhs = rhsText.equalsIgnoreCase("<Nop>") ? List.of() : parse(rhsText, false, leader, localLeader);
        if (hasKey && byteLength(lhs) > MAXMAPLEN) throw new VimFailure("E474: Invalid argument");
        if (!unmap && (!hasKey || !hasArg)) {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java`, replace:

```java
    }

    /**
     * replace_termcodes: a left- or right-hand side as typed after {@code :map}, as keys. {@code <>} names a key
     * ({@code <lt>} is {@code <}); {@code <Leader>} and {@code <LocalLeader>} are a backslash (there is no
     * {@code mapleader} yet); {@code <Nop>} is no key; CTRL-V takes the next character literally (a CTRL-V ending a
     * left-hand side stays); a left-hand side starting {@code #1} is {@code <F1>}. A control character is its CTRL
     * key. A backslash is an ordinary character, as with Vim's default 'cpoptions'.
     */
    static List<KeyInput> parse(String text, boolean lhs) {
        List<KeyInput> keys = new ArrayList<>();
        int i = 0;
```

with:

```java
    }

    /** {@link #parse(String, boolean, String, String)} with no {@code mapleader}: {@code <Leader>} is a backslash. */
    static List<KeyInput> parse(String text, boolean lhs) { return parse(text, lhs, "\\", "\\"); }

    /**
     * replace_termcodes: a left- or right-hand side as typed after {@code :map}, as keys. {@code <>} names a key
     * ({@code <lt>} is {@code <}); {@code <Leader>} and {@code <LocalLeader>} are {@code leader} and
     * {@code localLeader}; {@code <Nop>} is no key; CTRL-V takes the next character
     * literally (a CTRL-V ending a left-hand side stays); a left-hand side starting {@code #1} is
     * {@code <F1>}. A control character is its CTRL key. A backslash is an ordinary character, as with Vim's default
     * 'cpoptions'.
     *
     * @param leader what {@code <Leader>} stands for ({@code g:mapleader}, a backslash when it is not set)
     * @param localLeader what {@code <LocalLeader>} stands for ({@code g:maplocalleader})
     */
    static List<KeyInput> parse(String text, boolean lhs, String leader, String localLeader) {
        List<KeyInput> keys = new ArrayList<>();
        int i = 0;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java`, replace:

```java
                    String body = text.substring(i + 1, close).toLowerCase(Locale.ROOT);
                    if (body.equals("leader") || body.equals("localleader")) {
                        keys.add(KeyInput.of('\\'));
                        i = close + 1;
                        continue;
```

with:

```java
                    String body = text.substring(i + 1, close).toLowerCase(Locale.ROOT);
                    if (body.equals("leader") || body.equals("localleader")) {
                        keys.addAll(KeyCodes.keys(body.equals("leader") ? leader : localLeader));
                        i = close + 1;
                        continue;
```

- [ ] **Step 5: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains `:let`, `:messages`, `:syntax` and `:filetype`.

- [ ] **Step 6: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.CompletionTest'
```

Expected: 15 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1746 tests pass, 28 of them `vimrc.case`'s.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`: the Editor compiles without warnings (`-Xlint:all`).

- [ ] **Step 7: Check the recording (needs `vim` on `PATH`)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: `git status` lists only `vimrc.case` (`??`), unchanged from what you created: Vim agrees with every expectation.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v3b
git add plugins/editor/src/main/java/dev/jasper/editor/vim/ExVimrc.java plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java plugins/editor/src/test/java/dev/jasper/editor/vim/ExMapsTest.java plugins/editor/src/test/resources/vim/corpus/vimrc.case docs/editor-vim-reference.md
git commit -m "Add :let, the vimrc options and :messages

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: Reading the vimrc, and `:source`

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/Vimrc.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/ExVimrc.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/ExFiles.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Completion.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/CompletionTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/VimCorpusTest.java`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Consumes: `ExVimrc`, `Vim.remember` and `Vim.messages` (Task 1).
- Produces: `public final class Vimrc` with `public static Result read(VimEditor editor, Path file)` and `public record Result(boolean found, String error, int skipped)`, `Vimrc.source(editor, file, reading)`, `Vimrc.Reading`, `Vimrc.MAX_DEPTH` (10); `Ex.sourceLine(line) -> String` (the line's error, or null); `VimEditor.quietly(Runnable) -> List<OutputLine>`; `Vim.reading()`, `Vim.reading(Reading)`, `public void Vim.reset()`; `Options.resetGlobal()`; `ExVimrc.source` and the Ex command `:source`; `ExFiles.buffers` and `ExFiles.current` (now package-private); `VimrcTest.NOT_IN_VIM`, the commands the corpus leaves to `VimrcTest`.

- [ ] **Step 1: Write the failing tests**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java`:

```java
package dev.jasper.editor.vim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The vimrc and the commands the corpus cannot record (spec 7.1): reading files, {@code :source} and Reload
 * vimrc.
 */
class VimrcTest {
    /** The commands whose cases need files, which this class runs instead of the corpus. */
    static final Set<String> NOT_IN_VIM = Set.of("ex source");

    @TempDir Path folder;
    private final Set<String> covered = new java.util.HashSet<>();

    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private VimEditor editor(String text) {
        var vim = new Vim(new VimHost(r -> Optional.empty(), (r, t) -> { }));
        vim.coverage(covered::add);
        var area = new VimTextArea();
        area.setText(text);
        area.setCaretPosition(0);
        return new VimEditor(vim, area, EditorDefaults.VIM);
    }

    private Path file(String name, String... lines) throws Exception {
        Path file = folder.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", lines) + "\n");
        return file;
    }

    @Test void theLinesRunInOrderWithCommentsAndContinuations() throws Exception {
        Path vimrc = file("vimrc",
            "\" a comment",
            "set ts=4",
            "  \" an indented comment",
            "let mapleader = \",\"",
            "nnoremap <Leader>d dd",
            ":nmap x",
            "    \\ dd",
            "",
            "nmap y",
            "  \"\\ a comment inside a continued line",
            "  \\ yy");
        onEdt(() -> {
            var editor = editor("one\ntwo\nthree\n");
            var read = Vimrc.read(editor, vimrc);
            assertThat(read).isEqualTo(new Vimrc.Result(true, null, 0));
            assertThat(editor.options().number("tabstop")).isEqualTo(4);
            assertThat(editor.vim().variables()).containsEntry("mapleader", ",");
            type(editor, ",dx");
            assertThat(editor.buffer().line(0)).as(",d then x, both dd").isEqualTo("three");
            assertThat(editor.vim().mappings().starting(Mappings.NORMAL, KeyInput.of('y')).getFirst().rhs).isEqualTo(KeyInput.parse("yy"));
        });
    }

    @Test void onlyIdeaVimsConditionsAreTrue() throws Exception {
        Path vimrc = file("vimrc",
            "if has('ide')",
            "  set ts=3",
            "  if has(\"nvim\")",
            "    set ts=99",
            "  else",
            "    set sw=5",
            "  endif",
            "elseif has('jasper')",
            "  set ts=98",
            "else",
            "  set ts=97",
            "endif",
            "if exists('g:loaded')",
            "  set tw=1",
            "elsei has('jasper')",
            "  set tw=40",
            "endif",
            "if 0",
            "  this is not run",
            "en",
            "if !has('ide')",
            "  set tw=2",
            "endif");
        onEdt(() -> {
            var editor = editor("x\n");
            assertThat(Vimrc.read(editor, vimrc).skipped()).isZero();
            assertThat(editor.options().number("tabstop")).isEqualTo(3);
            assertThat(editor.options().number("shiftwidth")).isEqualTo(5);
            assertThat(editor.options().number("textwidth")).isEqualTo(40);
        });
    }

    @Test void failingLinesAreSkippedAndReportedAsVimReportsThem() throws Exception {
        Path vimrc = file("vimrc",
            "set ts=4",
            "Plug 'tpope/vim-surround'",
            "set nosuchoption",
            "let s:x = 1",
            "set sw=2",
            "endif");
        onEdt(() -> {
            var editor = editor("x\n");
            var read = Vimrc.read(editor, vimrc);
            assertThat(read.skipped()).isEqualTo(4);
            assertThat(editor.options().number("shiftwidth")).as("the rest of the file still runs").isEqualTo(2);
            assertThat(editor.vim().messages()).containsExactly(
                "Error detected while processing " + vimrc + ":",
                "line    2:",
                "E492: Not an editor command: Plug 'tpope/vim-surround'",
                "line    3:",
                "E518: Unknown option: nosuchoption",
                "line    4:",
                "E319: Sorry, the command is not available in this version",
                "line    6:",
                "E580: :endif without :if");
            assertThat(editor.message()).as("reading says nothing in the Vim bar").isEmpty();
            assertThat(editor.output()).isEmpty();
        });
    }

    @Test void aMissingIfEndAndAMissingOrUnreadableFile() throws Exception {
        Path vimrc = file("vimrc", "if has('ide')", "set ts=4");
        Path folderAsFile = Files.createDirectories(folder.resolve("dir"));
        onEdt(() -> {
            var editor = editor("x\n");
            assertThat(Vimrc.read(editor, vimrc).skipped()).isEqualTo(1);
            assertThat(editor.vim().messages().getLast()).isEqualTo("E171: Missing :endif");
            assertThat(Vimrc.read(editor, folder.resolve("none"))).isEqualTo(new Vimrc.Result(false, null, 0));
            var unreadable = Vimrc.read(editor, folderAsFile);
            assertThat(unreadable.found()).isTrue();
            assertThat(unreadable.error()).isNotNull();
        });
    }

    @Test void finishEndsTheFile() throws Exception {
        Path vimrc = file("vimrc", "set ts=4", "if has('ide')", "  finish", "endif", "set ts=9");
        onEdt(() -> {
            var editor = editor("x\n");
            assertThat(Vimrc.read(editor, vimrc).skipped()).isZero();
            assertThat(editor.options().number("tabstop")).isEqualTo(4);
        });
    }

    @Test void sourceReadsAnotherFileRelativeToTheOne() throws Exception {
        Path vimrc = file("vimrc", "source more/mappings.vim", "set ts=4");
        file("more/mappings.vim", "nmap x dd", "source ../deeper.vim", "bogus");
        file("deeper.vim", "set sw=6");
        onEdt(() -> {
            var editor = editor("x\n");
            assertThat(Vimrc.read(editor, vimrc).skipped()).as("bogus in mappings.vim").isEqualTo(1);
            assertThat(editor.options().number("shiftwidth")).isEqualTo(6);
            assertThat(editor.options().number("tabstop")).isEqualTo(4);
            assertThat(editor.vim().messages()).containsExactly(
                "Error detected while processing " + folder.resolve("more/mappings.vim") + ":",
                "line    3:",
                "E492: Not an editor command: bogus");
        });
    }

    @Test void sourceNestsTenDeep() throws Exception {
        Path loop = file("loop.vim", "source loop.vim");
        onEdt(() -> {
            var editor = editor("x\n");
            assertThat(Vimrc.read(editor, loop).skipped()).isEqualTo(1);
            assertThat(editor.vim().messages()).contains("E169: Command too recursive");
            assertThat(editor.vim().messages().stream().filter(line -> line.startsWith("line")).count()).isEqualTo(1);
        });
    }

    @Test void sourceTypedShowsWhatFailed() throws Exception {
        Path script = file("script.vim", "set ts=5", "bogus");
        onEdt(() -> {
            var editor = editor("x\n");
            type(editor, ":source " + script + "<CR>");
            assertThat(editor.options().number("tabstop")).isEqualTo(5);
            assertThat(editor.output()).extracting(VimEditor.OutputLine::text).containsExactly(
                "Error detected while processing " + script + ":", "line    2:", "E492: Not an editor command: bogus");
            type(editor, "<CR>:so " + folder.resolve("none.vim") + "<CR>");
            assertThat(editor.message()).isEqualTo("E484: Can't open file " + folder.resolve("none.vim"));
            type(editor, ":source<CR>");
            assertThat(editor.message()).isEqualTo("E471: Argument required");
        });
    }

    @Test void resetForgetsWhatTheVimrcSetButNotRegisters() throws Exception {
        onEdt(() -> {
            var editor = editor("one\n");
            type(editor, ":nmap x dd<CR>:set ic tm=50<CR>:let mapleader = ','<CR>\"ayy");
            editor.vim().reset();
            assertThat(editor.vim().mappings().isEmpty()).isTrue();
            assertThat(editor.options().flag("ignorecase")).isFalse();
            assertThat(editor.options().number("timeoutlen")).isEqualTo(1000);
            assertThat(editor.vim().variables()).isEmpty();
            assertThat(editor.vim().registers().get('a')).isPresent();
        });
    }

    @Test void letFormsJasperDoesNotHaveFail() throws Exception {
        onEdt(() -> {
            var editor = editor("one\n");
            for (String line : List.of("let s:x = 1", "let x = y", "let x = 1 + 2", "let", "let g:@a = 1"))  {
                type(editor, ":" + line + "<CR>");
                assertThat(editor.message()).as(line).isEqualTo("E319: Sorry, the command is not available in this version");
            }
            type(editor, ":let @_ = 'x'<CR>");
            assertThat(editor.message()).isEqualTo("E354: Invalid register name: '_'");
            type(editor, ":let &nosuch = 1<CR>");
            assertThat(editor.message()).isEqualTo("E355: Unknown option: nosuch");
            type(editor, ":let @a = 'open<CR>");
            assertThat(editor.message()).isEqualTo("E115: Missing single quote: 'open");
        });
    }

    @Test void everyCommandThatNeedsFilesRunsHere() throws Exception {
        Path script = file("s.vim", "set ts=4");
        onEdt(() -> {
            var editor = editor("x\n");
            type(editor, ":source " + script + "<CR>");
        });
        assertThat(covered).containsAll(NOT_IN_VIM);
    }
}
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CompletionTest.java`, replace:

```java
            assertThat(matches(editor, "w src/")).containsExactly("src/deep/", "src/one.java", "src/two\\ words.txt");
            assertThat(matches(editor, "r src/t")).containsExactly("src/two\\ words.txt");
            assertThat(matches(editor, "tabnew " + folder + "/m")).containsExactly(folder + "/main.txt");
            assertThat(matches(editor, "e nowhere/")).isEmpty();
```

with:

```java
            assertThat(matches(editor, "w src/")).containsExactly("src/deep/", "src/one.java", "src/two\\ words.txt");
            assertThat(matches(editor, "r src/t")).containsExactly("src/two\\ words.txt");
            assertThat(matches(editor, "so src/o")).as(":source completes paths").containsExactly("src/one.java");
            assertThat(matches(editor, "tabnew " + folder + "/m")).containsExactly(folder + "/main.txt");
            assertThat(matches(editor, "e nowhere/")).isEmpty();
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimCorpusTest.java`, replace:

```java
        for (Ex.Def def : Ex.implemented()) ids.add("ex " + def.name());
        // The file, buffer and tab commands need the Editor's tabs behind Vim; ExFilesTest checks them.
        List<String> missing = ids.stream().filter(id -> !used.contains(id) && !ExFilesTest.NEEDS_TABS.contains(id)).toList();
        assertThat(missing).as("commands with no corpus case").isEmpty();
    }
```

with:

```java
        for (Ex.Def def : Ex.implemented()) ids.add("ex " + def.name());
        // The file, buffer and tab commands need the Editor's tabs behind Vim; ExFilesTest checks them.
        List<String> missing = ids.stream().filter(id -> !used.contains(id) && !ExFilesTest.NEEDS_TABS.contains(id)
            && !VimrcTest.NOT_IN_VIM.contains(id)).toList();
        assertThat(missing).as("commands with no corpus case").isEmpty();
    }
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimrcTest'
```

Expected: FAIL: the test does not compile (`Vimrc` does not exist).

- [ ] **Step 3: Write `Vimrc` and `:source`**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Vimrc.java`:

```java
package dev.jasper.editor.vim;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The vimrc and the files it sources (spec 7.1), ported from Vim's do_source and getsourceline: lines run in order as
 * command lines; {@code "} starts a comment line; a line starting with {@code \} continues the one before;
 * {@code if has('ide')} and {@code has('jasper')} are true (so IdeaVim's guarded blocks apply) and any other condition
 * false, with {@code elseif}, {@code else} and {@code endif}; {@code finish} ends the file. A line that fails is
 * skipped and reported as Vim reports it ({@code Error detected while processing …}, {@code line    3:}, the error);
 * the rest of the file still runs.
 */
public final class Vimrc {
    private Vimrc() { }

    /** How deep {@code source} may nest (spec 7.1). */
    static final int MAX_DEPTH = 10;

    /**
     * What reading a vimrc found.
     *
     * @param found whether the file exists (a missing vimrc is not an error)
     * @param error why it could not be read, or null
     * @param skipped how many lines failed and were skipped, in it and the files it sourced
     */
    public record Result(boolean found, String error, int skipped) { }

    /** A reading under way: the report so far, the file being read, and how deep sourcing is. */
    static final class Reading {
        final List<String> report = new ArrayList<>();
        int skipped;
        int depth;
        Path file;
        /** The file the last "Error detected while processing" line named. */
        private Path reported;

        void failed(int line, String error) {
            skipped++;
            if (!file.equals(reported)) {
                report.add("Error detected while processing " + file + ":");
                reported = file;
            }
            report.add(String.format("line %4d:", line));
            report.add(error);
        }
    }

    /**
     * Reads the vimrc into Vim's global state, running its lines in {@code editor}; each skipped line joins the
     * message history, for {@code :messages}.
     *
     * @param editor the tab whose editor runs the lines
     * @param file the vimrc
     * @return whether it was there, why it could not be read, and how many lines were skipped
     */
    public static Result read(VimEditor editor, Path file) {
        Reading reading = new Reading();
        try {
            source(editor, file, reading);
        } catch (NoSuchFileException missing) {
            return new Result(false, null, 0);
        } catch (IOException unreadable) {
            String reason = unreadable.getMessage() == null ? unreadable.getClass().getSimpleName() : unreadable.getMessage();
            return new Result(true, reason, 0);
        }
        for (String line : reading.report) editor.vim().remember(line);
        return new Result(true, null, reading.skipped);
    }

    /**
     * do_source: runs a file's lines, reporting the ones that fail into {@code reading}.
     *
     * @throws IOException when the file cannot be read
     */
    static void source(VimEditor editor, Path file, Reading reading) throws IOException {
        if (reading.depth >= MAX_DEPTH) throw new VimFailure("E169: Command too recursive");
        List<String> physical = lines(file);
        Vim vim = editor.vim();
        Reading outer = vim.reading();
        Path outerFile = reading.file;
        reading.depth++;
        reading.file = file;
        vim.reading(reading);
        try {
            run(editor, logical(physical), reading);
        } finally {
            reading.depth--;
            reading.file = outerFile;
            vim.reading(outer);
        }
    }

    /** The file's lines as UTF-8, with any malformed bytes replaced. */
    private static List<String> lines(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE).decode(ByteBuffer.wrap(bytes)).toString();
        if (text.startsWith("﻿")) text = text.substring(1);
        List<String> lines = new ArrayList<>(List.of(text.split("\r?\n", -1)));
        if (!lines.isEmpty() && lines.getLast().isEmpty()) lines.removeLast();
        return lines;
    }

    /** A line to run and the number of its first physical line. */
    private record Line(int number, String text) { }

    /**
     * getsourceline's line continuation: a line whose first non-blank is {@code \} joins the one before, without the
     * backslash; {@code "\ } starts a comment inside a continued line.
     */
    private static List<Line> logical(List<String> physical) {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < physical.size(); i++) {
            String text = physical.get(i);
            String trimmed = text.stripLeading();
            if (!lines.isEmpty() && trimmed.startsWith("\\")) {
                Line last = lines.removeLast();
                lines.add(new Line(last.number(), last.text() + trimmed.substring(1)));
            } else if (!lines.isEmpty() && trimmed.startsWith("\"\\ ")) {
                continue;
            } else lines.add(new Line(i + 1, text));
        }
        return lines;
    }

    /** The conditional commands, as Vim abbreviates them. */
    private static final Pattern CONDITIONAL = Pattern.compile("(if|elsei(?:f)?|el(?:se?)?|en(?:d(?:if?)?)?|fini(?:sh?)?)(?![A-Za-z])\\s*(.*)");

    /** One {@code if} being read: whether the lines around it run, whether a branch was taken, and whether this one runs. */
    private record Conditional(boolean outer, boolean taken, boolean active) { }

    private static void run(VimEditor editor, List<Line> lines, Reading reading) {
        Deque<Conditional> stack = new ArrayDeque<>();
        for (Line line : lines) {
            String text = stripColons(line.text());
            if (text.isEmpty() || text.startsWith("\"")) continue;
            boolean active = stack.isEmpty() || stack.peek().active();
            Matcher m = CONDITIONAL.matcher(text);
            if (m.matches()) {
                String word = m.group(1), condition = m.group(2);
                if (word.equals("if")) {
                    boolean truth = active && condition(condition);
                    stack.push(new Conditional(active, truth, truth));
                } else if (word.startsWith("elsei")) {
                    if (stack.isEmpty()) reading.failed(line.number(), "E582: :elseif without :if");
                    else {
                        Conditional c = stack.pop();
                        boolean truth = c.outer() && !c.taken() && condition(condition);
                        stack.push(new Conditional(c.outer(), c.taken() || truth, truth));
                    }
                } else if (word.startsWith("el")) {
                    if (stack.isEmpty()) reading.failed(line.number(), "E581: :else without :if");
                    else {
                        Conditional c = stack.pop();
                        stack.push(new Conditional(c.outer(), true, c.outer() && !c.taken()));
                    }
                } else if (word.startsWith("en")) {
                    if (stack.isEmpty()) reading.failed(line.number(), "E580: :endif without :if");
                    else stack.pop();
                } else if (active) return;
                continue;
            }
            if (!active) continue;
            String error = editor.ex().sourceLine(text);
            if (error != null) reading.failed(line.number(), error);
        }
        if (!stack.isEmpty()) reading.failed(lines.getLast().number(), "E171: Missing :endif");
    }

    private static String stripColons(String text) {
        int i = 0;
        while (i < text.length() && (Ex.isWhite(text.charAt(i)) || text.charAt(i) == ':')) i++;
        return text.substring(i);
    }

    /** A condition Jasper knows: {@code has('ide')} and {@code has('jasper')} are true, a number is itself; anything else is false. */
    static boolean condition(String text) {
        String c = text.strip();
        int comment = c.indexOf(" \"");
        if (comment >= 0) c = c.substring(0, comment).strip();
        if (c.matches("-?\\d+")) return Long.parseLong(c) != 0;
        Matcher has = Pattern.compile("has\\(\\s*(['\"])(ide|jasper)\\1\\s*\\)").matcher(c);
        return has.matches();
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExVimrc.java`, replace:

```java
package dev.jasper.editor.vim;

import java.util.List;
import java.util.Locale;
```

with:

```java
package dev.jasper.editor.vim;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExVimrc.java`, replace:

```java

    /**
     * ex_messages: the message history under Vim's "Messages maintainer" line ({@code :messages clear} empties it;
     * {@code :3messages} shows the last three, without that line).
```

with:

```java

    /**
     * ex_source: {@code :source {file}} runs the file's lines; the file is relative to the file being read, or typed,
     * to the current file's folder. Typed, what failed is shown as Vim shows it; inside a vimrc it joins the vimrc's
     * report.
     */
    static void source(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        String name = ExFiles.fileArg(cmd);
        if (name == null) throw new VimFailure("E471: Argument required");
        Vim vim = editor.vim();
        Vimrc.Reading reading = vim.reading();
        Path file;
        if (reading != null) {
            String home = System.getProperty("user.home");
            String expanded = name.equals("~") ? home : name.startsWith("~/") ? home + name.substring(1) : name;
            Path path = Path.of(expanded);
            file = path.isAbsolute() ? path.normalize() : reading.file.toAbsolutePath().getParent().resolve(path).normalize();
        } else file = ExFiles.resolve(name, ExFiles.current(ExFiles.buffers(cmd)));
        Vimrc.Reading into = reading != null ? reading : new Vimrc.Reading();
        try {
            Vimrc.source(editor, file, into);
        } catch (NoSuchFileException missing) {
            throw new VimFailure("E484: Can't open file " + name);
        } catch (IOException unreadable) {
            throw new VimFailure("E484: Can't open file " + name);
        }
        if (reading == null) for (String line : into.report) editor.message(line, true);
    }

    /**
     * ex_messages: the message history under Vim's "Messages maintainer" line ({@code :messages clear} empties it;
     * {@code :3messages} shows the last three, without that line).
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, replace:

```java
        def("cmapclear", EXTRA | TRLBAR, Addr.NONE, "remove every command-line mapping", ExMaps::mapclear);
        def("let", EXTRA | NOTRLCOM, Addr.NONE, "`:let mapleader = \",\"`, `:let @a = \"…\"`, `:let &tw = 80`, `:let g:name = …`: a variable, register or option", ExVimrc::let);
        def("messages", RANGE | EXTRA | TRLBAR, Addr.OTHER, "the message history: errors, and the vimrc's skipped lines; `:messages clear`", ExVimrc::messages);
        def("syntax", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "accepted and ignored: the Editor highlights by language", ExVimrc::ignored);
```

with:

```java
        def("cmapclear", EXTRA | TRLBAR, Addr.NONE, "remove every command-line mapping", ExMaps::mapclear);
        def("let", EXTRA | NOTRLCOM, Addr.NONE, "`:let mapleader = \",\"`, `:let @a = \"…\"`, `:let &tw = 80`, `:let g:name = …`: a variable, register or option", ExVimrc::let);
        def("source", EXTRA | NEEDARG | TRLBAR, Addr.NONE, "run {file}'s lines as command lines, as the vimrc's", ExVimrc::source);
        def("messages", RANGE | EXTRA | TRLBAR, Addr.OTHER, "the message history: errors, and the vimrc's skipped lines; `:messages clear`", ExVimrc::messages);
        def("syntax", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "accepted and ignored: the Editor highlights by language", ExVimrc::ignored);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, replace:

```java
        commands.action("g&", "repeat the last `:s` on every line, with its flags (`:%s//~/&`)", false,
            c -> c.editor.ex().execute("%s//~/&"));
    }
```

with:

```java
        commands.action("g&", "repeat the last `:s` on every line, with its flags (`:%s//~/&`)", false,
            c -> c.editor.ex().execute("%s//~/&"));
    }

    /**
     * A line of a sourced file (do_source's do_cmdline): it runs as a command line in one undo step, and what it says
     * is dropped.
     *
     * @return its error message, or null when it did not fail with one
     */
    String sourceLine(String line) {
        String error = null;
        // A line that fails is reported with the file's other errors; it does not fail the command line that sourced it.
        boolean outerFailed = failed;
        editor.beginUndoStep();
        try {
            for (VimEditor.OutputLine said : editor.quietly(() -> runLine(line))) if (said.error()) error = said.text();
        } finally {
            editor.endUndoStep();
            failed = outerFailed;
        }
        return error;
    }
```

- [ ] **Step 4: Run a line quietly, remember the reading, reset for Reload vimrc, and complete `:source`'s paths**

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    void message(String text, boolean error) {
        messageLog.add(text);
        if (error) vim.remember(text);
        if (collecting != null) {
            collecting.add(new OutputLine(text, error));
```

with:

```java
    void message(String text, boolean error) {
        messageLog.add(text);
        if (error && !quiet) vim.remember(text);
        if (collecting != null) {
            collecting.add(new OutputLine(text, error));
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    /** @return the Ex command line */
    Ex ex() { return ex; }

    /**
```

with:

```java
    /** @return the Ex command line */
    Ex ex() { return ex; }

    /** While a sourced file's line runs: what it says goes nowhere, and its errors do not join the history yet. */
    private boolean quiet;

    /**
     * Runs work with everything it says gathered and returned instead of shown (a line of a sourced file).
     *
     * @return what it said
     */
    List<OutputLine> quietly(Runnable work) {
        List<OutputLine> before = collecting;
        boolean wasQuiet = quiet;
        collecting = new ArrayList<>();
        quiet = true;
        try {
            work.run();
            return collecting;
        } finally {
            collecting = before;
            quiet = wasQuiet;
        }
    }

    /**
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, replace:

```java
    /** The global variables {@code :let} sets ({@code g:mapleader}, extension settings), by name without {@code g:}. */
    private final java.util.Map<String, Object> variables = new java.util.HashMap<>();
    /** The message history {@code :messages} shows, oldest first (Vim's 'msghistory', 500). */
    private final java.util.List<String> messages = new java.util.ArrayList<>();
```

with:

```java
    /** The global variables {@code :let} sets ({@code g:mapleader}, extension settings), by name without {@code g:}. */
    private final java.util.Map<String, Object> variables = new java.util.HashMap<>();
    /** The vimrc or {@code :source} file being read, or null. */
    private Vimrc.Reading reading;
    /** The message history {@code :messages} shows, oldest first (Vim's 'msghistory', 500). */
    private final java.util.List<String> messages = new java.util.ArrayList<>();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, replace:

```java
    }

    /** An error given, or a line the vimrc skipped, joins the message history. */
    void remember(String message) {
```

with:

```java
    }

    Vimrc.Reading reading() { return reading; }

    void reading(Vimrc.Reading value) { reading = value; }

    /** An error given, or a line the vimrc skipped, joins the message history. */
    void remember(String message) {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, replace:

```java
    /** @return the message history, oldest first */
    java.util.List<String> messages() { return messages; }

    /** @return macro recording and execution */
```

with:

```java
    /** @return the message history, oldest first */
    java.util.List<String> messages() { return messages; }

    /**
     * Reload vimrc (spec 7.1): mappings, options' global values, the handler table and the variables go back to
     * their defaults; registers, marks and history stay.
     */
    public void reset() {
        mappings.clear(Mappings.MAP | Mappings.MAP_BANG);
        options.resetGlobal();
        handlers.clear();
        variables.clear();
    }

    /** @return macro recording and execution */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
    }

    private void put(Def def, Object value) {
        Object old = values.put(def.name(), value);
```

with:

```java
    }

    /** Reload vimrc: every global value goes back to its default (spec 7.1); local instances keep theirs. */
    void resetGlobal() {
        if (global != null) throw new IllegalStateException("Only the global options are reset");
        if (values.isEmpty()) return;
        values.clear();
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private void put(Def def, Object value) {
        Object old = values.put(def.name(), value);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExFiles.java`, replace:

```java
    private static Documents documents(ExCommand cmd) { return cmd.editor.vim().host().documents(); }

    private static List<Buffer> buffers(ExCommand cmd) { return documents(cmd).buffers().apply(cmd.editor); }

    private static Buffer current(List<Buffer> buffers) {
        return buffers.stream().filter(Buffer::current).findFirst().orElseThrow(() -> new IllegalStateException("No current buffer"));
    }
```

with:

```java
    private static Documents documents(ExCommand cmd) { return cmd.editor.vim().host().documents(); }

    static List<Buffer> buffers(ExCommand cmd) { return documents(cmd).buffers().apply(cmd.editor); }

    static Buffer current(List<Buffer> buffers) {
        return buffers.stream().filter(Buffer::current).findFirst().orElseThrow(() -> new IllegalStateException("No current buffer"));
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Completion.java`, replace:

```java
    }

    private static final List<String> FILE_COMMANDS = List.of("edit", "write", "read", "saveas", "tabnew", "tabedit", "wq", "xit", "exit");

    /**
```

with:

```java
    }

    private static final List<String> FILE_COMMANDS = List.of("edit", "write", "read", "saveas", "tabnew", "tabedit", "wq", "xit", "exit", "source");

    /**
```

- [ ] **Step 5: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains `:source`.

- [ ] **Step 6: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.CompletionTest'
```

Expected: 16 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.vim.TypeaheadTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 198 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1746 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`.

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/vim-v3b
git add plugins/editor/src/main/java/dev/jasper/editor/vim/Vimrc.java plugins/editor/src/main/java/dev/jasper/editor/vim/ExVimrc.java plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java plugins/editor/src/main/java/dev/jasper/editor/vim/ExFiles.java plugins/editor/src/main/java/dev/jasper/editor/vim/Completion.java plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java plugins/editor/src/test/java/dev/jasper/editor/vim/CompletionTest.java plugins/editor/src/test/java/dev/jasper/editor/vim/VimCorpusTest.java docs/editor-vim-reference.md
git commit -m "Read vimrc files: comments, continuation, if has(), source

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: `:sethandler`, `<Action>(id)`, `:action` and `:actionlist`

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyCodes.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimHost.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/ExVimrc.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/ExTable.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/KeyInputTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ExMapsTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ExTest.java`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Consumes: `VimrcTest.NOT_IN_VIM` (Task 2), `KeyHandlers` (V1a).
- Produces: `KeyInput.action(id)`, `KeyInput.actionId() -> String` (null for other keys), notation `<Action>(id)`; `VimHost.Action(id, title)`, `VimHost.Actions(Supplier<List<Action>> list, BiPredicate<VimEditor, String> run)` with `Actions.NONE`, a fourth `VimHost` component with a 3-argument constructor that gives `Actions.NONE`; `ExVimrc.sethandler`, `ExVimrc.action`, `ExVimrc.actionlist`; `ExTable.NAMES` ends with IdeaVim's `sethandler`, `action`, `actionlist`.

- [ ] **Step 1: Write the failing tests**

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java`, replace:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
```

with:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java`, replace:

```java

/**
 * The vimrc and the commands the corpus cannot record (spec 7.1): reading files, {@code :source} and Reload
 * vimrc.
 */
class VimrcTest {
    /** The commands whose cases need files, which this class runs instead of the corpus. */
    static final Set<String> NOT_IN_VIM = Set.of("ex source");

    @TempDir Path folder;
    private final Set<String> covered = new java.util.HashSet<>();
```

with:

```java

/**
 * The vimrc and the commands the corpus cannot record (spec 7.1 and 7.2): reading files, {@code :source},
 * IdeaVim's {@code :sethandler}, {@code :action} and {@code :actionlist}, and Reload vimrc.
 */
class VimrcTest {
    /** The commands whose cases need files or the Editor's commands, which this class runs instead of the corpus. */
    static final Set<String> NOT_IN_VIM = Set.of("ex source", "ex sethandler", "ex action", "ex actionlist");

    @TempDir Path folder;
    private final List<String> ran = new ArrayList<>();
    private final Set<String> covered = new java.util.HashSet<>();
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java`, replace:

```java

    private VimEditor editor(String text) {
        var vim = new Vim(new VimHost(r -> Optional.empty(), (r, t) -> { }));
        vim.coverage(covered::add);
        var area = new VimTextArea();
```

with:

```java

    private VimEditor editor(String text) {
        var actions = new VimHost.Actions(() -> List.of(new VimHost.Action("save", "Save"), new VimHost.Action("find", "Find…"),
            new VimHost.Action("toggle-comment", "Toggle Comment")), (editor, id) -> {
                if (!List.of("save", "find", "toggle-comment").contains(id)) return false;
                ran.add(id);
                return true;
            });
        var vim = new Vim(new VimHost(r -> Optional.empty(), (r, t) -> { }, VimHost.Documents.NONE, actions));
        vim.coverage(covered::add);
        var area = new VimTextArea();
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java`, replace:

```java
    }

    @Test void resetForgetsWhatTheVimrcSetButNotRegisters() throws Exception {
        onEdt(() -> {
            var editor = editor("one\n");
            type(editor, ":nmap x dd<CR>:set ic tm=50<CR>:let mapleader = ','<CR>\"ayy");
            editor.vim().reset();
            assertThat(editor.vim().mappings().isEmpty()).isTrue();
```

with:

```java
    }

    @Test void sethandlerGivesKeysToVimOrTheEditor() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\n");
            type(editor, ":sethandler <lt>C-d> n:ide i-v:vim<CR>");
            KeyHandlers handlers = editor.vim().handlers();
            assertThat(handlers.handler(KeyInput.ctrl('d'), 'n')).isEqualTo(KeyHandlers.Handler.IDE);
            assertThat(handlers.handler(KeyInput.ctrl('d'), 'i')).isEqualTo(KeyHandlers.Handler.VIM);
            assertThat(handlers.handler(KeyInput.ctrl('d'), 'x')).isEqualTo(KeyHandlers.Handler.DEFAULT);
            assertThat(editor.offer(KeyInput.ctrl('d'))).as("Normal mode: the Editor's").isFalse();
            type(editor, ":sethandler <lt>C-e> a:ide<CR>");
            assertThat(handlers.handler(KeyInput.ctrl('e'), 'o')).isEqualTo(KeyHandlers.Handler.IDE);
            type(editor, ":sethandler <lt>C-e> q:ide<CR>");
            assertThat(editor.message()).isEqualTo("E474: Invalid argument: q:ide");
            type(editor, ":sethandler <lt>C-e> n:emacs<CR>");
            assertThat(editor.message()).isEqualTo("E474: Invalid argument: n:emacs");
            type(editor, ":sethandler <lt>C-e><CR>");
            assertThat(editor.message()).isEqualTo("E471: Argument required");
        });
    }

    @Test void actionsRunTheEditorsCommands() throws Exception {
        onEdt(() -> {
            var editor = editor("one\n");
            type(editor, ":action save<CR>");
            assertThat(ran).containsExactly("save");
            type(editor, ":action nothing<CR>");
            assertThat(editor.message()).isEqualTo("Not an Editor command: nothing");
            type(editor, ":inoremap <lt>C-s> <lt>Action>(save)<CR>:nnoremap gc <lt>Action>(toggle-comment)j<CR>");
            type(editor, "ia<C-s>b");
            assertThat(ran).containsExactly("save", "save");
            assertThat(editor.mode()).as("an action does not leave Insert mode").isEqualTo(Mode.INSERT);
            assertThat(editor.buffer().line(0)).isEqualTo("abone");
            type(editor, "<Esc>:nmap gc<CR>");
            assertThat(editor.message()).isEqualTo("n  gc          * <Action>(toggle-comment)j");
            type(editor, ":nmap x <lt>Action>(nothing)dd<CR>x");
            assertThat(editor.message()).as("an unknown action fails and ends the mapping").isEqualTo("Not an Editor command: nothing");
            assertThat(editor.buffer().count()).isEqualTo(1);
        });
    }

    @Test void actionlistListsTheIds() throws Exception {
        onEdt(() -> {
            var editor = editor("one\n");
            type(editor, ":actionlist<CR>");
            assertThat(editor.output()).extracting(VimEditor.OutputLine::text).containsExactly("--- Actions ---",
                "save                     Save", "find                     Find…", "toggle-comment           Toggle Comment");
            type(editor, "<CR>:actionlist COMM<CR>");
            assertThat(editor.output()).extracting(VimEditor.OutputLine::text).containsExactly("--- Actions ---", "toggle-comment           Toggle Comment");
        });
    }

    @Test void resetForgetsWhatTheVimrcSetButNotRegisters() throws Exception {
        onEdt(() -> {
            var editor = editor("one\n");
            type(editor, ":nmap x dd<CR>:set ic tm=50<CR>:let mapleader = ','<CR>:sethandler <lt>C-d> a:ide<CR>\"ayy");
            editor.vim().reset();
            assertThat(editor.vim().mappings().isEmpty()).isTrue();
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java`, replace:

```java
            assertThat(editor.options().number("timeoutlen")).isEqualTo(1000);
            assertThat(editor.vim().variables()).isEmpty();
            assertThat(editor.vim().registers().get('a')).isPresent();
        });
```

with:

```java
            assertThat(editor.options().number("timeoutlen")).isEqualTo(1000);
            assertThat(editor.vim().variables()).isEmpty();
            assertThat(editor.vim().handlers().handler(KeyInput.ctrl('d'), 'n')).isEqualTo(KeyHandlers.Handler.DEFAULT);
            assertThat(editor.vim().registers().get('a')).isPresent();
        });
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java`, replace:

```java
    }

    @Test void everyCommandThatNeedsFilesRunsHere() throws Exception {
        Path script = file("s.vim", "set ts=4");
        onEdt(() -> {
            var editor = editor("x\n");
            type(editor, ":source " + script + "<CR>");
        });
        assertThat(covered).containsAll(NOT_IN_VIM);
```

with:

```java
    }

    @Test void everyCommandThatNeedsFilesOrTheEditorRunsHere() throws Exception {
        Path script = file("s.vim", "set ts=4");
        onEdt(() -> {
            var editor = editor("x\n");
            type(editor, ":source " + script + "<CR>:sethandler <lt>C-d> n:ide<CR>:action save<CR>:actionlist<CR><CR>");
        });
        assertThat(covered).containsAll(NOT_IN_VIM);
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/KeyInputTest.java`, replace:

```java
        assertThat(KeyInput.parse("<Char-x>")).as("not a number").hasSize(8);
        assertThat(KeyInput.named("Plug").notation()).isEqualTo("<Plug>");
    }
```

with:

```java
        assertThat(KeyInput.parse("<Char-x>")).as("not a number").hasSize(8);
        assertThat(KeyInput.named("Plug").notation()).isEqualTo("<Plug>");
    }

    @Test void anActionKeyCarriesAnEditorCommandId() {
        assertThat(KeyInput.parse("<Action>(save)x")).containsExactly(KeyInput.action("save"), KeyInput.of('x'));
        assertThat(KeyInput.action("find").notation()).isEqualTo("<Action>(find)");
        assertThat(KeyInput.action("find").actionId()).isEqualTo("find");
        assertThat(KeyInput.of('a').actionId()).isNull();
        assertThat(KeyInput.parse("<Action>()")).as("no id: literal").hasSize(10);
    }
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/ExMapsTest.java`, replace:

```java
    }

    @Test void showsKeysAsVimsListingDoes() {
        assertThat(ExMaps.display(KeyInput.parse("<Space>a b<Space>"), false)).isEqualTo("<Space>a b<Space>");
```

with:

```java
    }

    @Test void actionsInARightHandSide() {
        assertThat(ExMaps.parse("<Action>(find)n", false)).containsExactly(KeyInput.action("find"), KeyInput.of('n'));
        assertThat(ExMaps.parse("<action>(find)", false)).containsExactly(KeyInput.action("find"));
        assertThat(ExMaps.parse("<Action>x", false)).as("not followed by an id").isEqualTo(KeyInput.parse("<lt>Action>x"));
        assertThat(ExMaps.display(ExMaps.parse("<Action>(save)", false), false)).isEqualTo("<Action>(save)");
    }

    @Test void showsKeysAsVimsListingDoes() {
        assertThat(ExMaps.display(KeyInput.parse("<Space>a b<Space>"), false)).isEqualTo("<Space>a b<Space>");
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/ExTest.java`, replace:

```java

    @Test void namesResolveAsVimAbbreviatesThem() {
        assertThat(ExTable.NAMES).hasSize(600);
        assertThat(ExTable.resolve("s")).isEqualTo("substitute");
        assertThat(ExTable.resolve("d")).isEqualTo("delete");
```

with:

```java

    @Test void namesResolveAsVimAbbreviatesThem() {
        assertThat(ExTable.NAMES).as("Vim's 600 and IdeaVim's sethandler, action and actionlist").hasSize(603);
        assertThat(ExTable.resolve("s")).isEqualTo("substitute");
        assertThat(ExTable.resolve("d")).isEqualTo("delete");
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.ExMapsTest'
```

Expected: FAIL: the tests do not compile (`VimHost.Actions` and `KeyInput.action` do not exist).

- [ ] **Step 3: Give keys an Editor command and the host the Editor's commands**

In `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java`, replace:

```java
 * equal keys are equal records.
 *
 * @param key one code point as a string, or a name from {@link #NAMES}
 * @param modifiers {@link #SHIFT}, {@link #CTRL}, {@link #ALT} and {@link #META} combined
 */
```

with:

```java
 * equal keys are equal records.
 *
 * @param key one code point as a string, a name from {@link #NAMES}, or {@code Action(id)} for an Editor command a
 *     mapping runs ({@code <Action>(id)}, spec 7.2)
 * @param modifiers {@link #SHIFT}, {@link #CTRL}, {@link #ALT} and {@link #META} combined
 */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java`, replace:

```java
    public KeyInput {
        Objects.requireNonNull(key, "key");
        if (key.isEmpty() || key.codePointCount(0, key.length()) != 1 && !NAMES.contains(key))
            throw new IllegalArgumentException("Not a key: " + key);
    }
```

with:

```java
    public KeyInput {
        Objects.requireNonNull(key, "key");
        if (key.isEmpty() || key.codePointCount(0, key.length()) != 1 && !NAMES.contains(key) && !isAction(key))
            throw new IllegalArgumentException("Not a key: " + key);
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java`, replace:

```java
    public static KeyInput ctrl(char letter) { return normalize(String.valueOf(letter), CTRL); }

    /** @return whether this is a character rather than a named key */
    public boolean isChar() { return key.codePointCount(0, key.length()) == 1; }
```

with:

```java
    public static KeyInput ctrl(char letter) { return normalize(String.valueOf(letter), CTRL); }

    /**
     * @param id an Editor command id
     * @return the key that runs it from a mapping, {@code <Action>(id)}
     */
    public static KeyInput action(String id) { return new KeyInput("Action(" + id + ")", 0); }

    private static boolean isAction(String key) { return key.startsWith("Action(") && key.endsWith(")") && key.length() > 8; }

    /** @return the Editor command id of an {@code <Action>(id)} key, or null for any other key */
    public String actionId() { return isAction(key) ? key.substring(7, key.length() - 1) : null; }

    /** @return whether this is a character rather than a named key */
    public boolean isChar() { return key.codePointCount(0, key.length()) == 1; }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java`, replace:

```java
    public String notation() {
        if (modifiers == 0 && isChar()) return key.equals("<") ? "<lt>" : key;
        StringBuilder out = new StringBuilder("<");
        if ((modifiers & SHIFT) != 0) out.append("S-");
```

with:

```java
    public String notation() {
        if (modifiers == 0 && isChar()) return key.equals("<") ? "<lt>" : key;
        if (isAction(key)) return "<Action>(" + actionId() + ")";
        StringBuilder out = new StringBuilder("<");
        if ((modifiers & SHIFT) != 0) out.append("S-");
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java`, replace:

```java
        int i = 0;
        while (i < notation.length()) {
            if (notation.charAt(i) == '<') {
                int close = notation.indexOf('>', i + 1);
```

with:

```java
        int i = 0;
        while (i < notation.length()) {
            if (notation.regionMatches(true, i, "<Action>(", 0, 9)) {
                int close = notation.indexOf(')', i + 9);
                if (close > i + 9) {
                    keys.add(action(notation.substring(i + 9, close)));
                    i = close + 1;
                    continue;
                }
            }
            if (notation.charAt(i) == '<') {
                int close = notation.indexOf('>', i + 1);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyCodes.java`, replace:

```java

    private static String text(KeyInput key) {
        String code = CODES.get(key.notation());
        if (code != null) return SPECIAL + code;
```

with:

```java

    private static String text(KeyInput key) {
        // An Editor command from a mapping is never typed, so never recorded; it has no text.
        if (key.actionId() != null) return "";
        String code = CODES.get(key.notation());
        if (code != null) return SPECIAL + code;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimHost.java`, replace:

```java
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * What the Vim engine needs from the Editor (spec 1.1): the clipboard, and the Editor's tabs as Vim's buffers.
 *
 * @param readClipboard reads {@code '+'} (the clipboard) or {@code '*'} (the selection, or the clipboard where there
```

with:

```java
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * What the Vim engine needs from the Editor (spec 1.1): the clipboard, the Editor's tabs as Vim's buffers, and its
 * commands for {@code <Action>(id)} and {@code :action}.
 *
 * @param readClipboard reads {@code '+'} (the clipboard) or {@code '*'} (the selection, or the clipboard where there
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimHost.java`, replace:

```java
 * @param writeClipboard writes {@code '+'} or {@code '*'}
 * @param documents the tabs, for the file, buffer and tab commands
 */
public record VimHost(Function<Character, Optional<String>> readClipboard, BiConsumer<Character, String> writeClipboard, Documents documents) {
    /**
     * A host with a clipboard and no tabs.
```

with:

```java
 * @param writeClipboard writes {@code '+'} or {@code '*'}
 * @param documents the tabs, for the file, buffer and tab commands
 * @param actions the Editor's commands, for {@code <Action>(id)}, {@code :action} and {@code :actionlist}
 */
public record VimHost(Function<Character, Optional<String>> readClipboard, BiConsumer<Character, String> writeClipboard, Documents documents,
                      Actions actions) {
    /**
     * A host with a clipboard and tabs and no commands.
     *
     * @param readClipboard reads {@code '+'} or {@code '*'}
     * @param writeClipboard writes {@code '+'} or {@code '*'}
     * @param documents the tabs
     */
    public VimHost(Function<Character, Optional<String>> readClipboard, BiConsumer<Character, String> writeClipboard, Documents documents) {
        this(readClipboard, writeClipboard, documents, Actions.NONE);
    }

    /**
     * A host with a clipboard and no tabs.
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimHost.java`, replace:

```java
    /** @return a host with no clipboard and no tabs: reads are empty and writes are dropped */
    public static VimHost detached() { return new VimHost(register -> Optional.empty(), (register, text) -> { }); }

    /**
```

with:

```java
    /** @return a host with no clipboard and no tabs: reads are empty and writes are dropped */
    public static VimHost detached() { return new VimHost(register -> Optional.empty(), (register, text) -> { }); }

    /**
     * An Editor command a mapping or {@code :action} can run (spec 7.2).
     *
     * @param id its id, as the Editor's commands name it ({@code save}, {@code find})
     * @param title what menus call it
     */
    public record Action(String id, String title) { }

    /**
     * The Editor's commands, on the UI thread.
     *
     * @param list every command, in the Editor's order
     * @param run runs a command, for the tab Vim is in; false when no command has the id
     */
    public record Actions(Supplier<List<Action>> list, BiPredicate<VimEditor, String> run) {
        /** No commands: every id is unknown. */
        public static final Actions NONE = new Actions(List::of, (editor, id) -> false);
    }

    /**
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    /** Runs one key without the failure handling, for {@code .}, a count's repeat and {@code :normal}. */
    void dispatch(KeyInput key) {
        if (promptHandler != null) {
            answer(key);
```

with:

```java
    /** Runs one key without the failure handling, for {@code .}, a count's repeat and {@code :normal}. */
    void dispatch(KeyInput key) {
        String action = key.actionId();
        if (action != null) {
            // <Action>(id) runs the Editor's command where it is reached, in any mode (IdeaVim).
            if (!vim.host().actions().run().test(this, action)) throw new VimFailure("Not an Editor command: " + action);
            return;
        }
        if (promptHandler != null) {
            answer(key);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java`, replace:

```java
    static String name(KeyInput key) {
        int modifiers = key.modifiers();
        if (key.isChar() && modifiers == 0) return key.key();
        if (key.isChar() && modifiers == KeyInput.CTRL) {
```

with:

```java
    static String name(KeyInput key) {
        int modifiers = key.modifiers();
        if (key.actionId() != null) return key.notation();
        if (key.isChar() && modifiers == 0) return key.key();
        if (key.isChar() && modifiers == KeyInput.CTRL) {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java`, replace:

```java
     * replace_termcodes: a left- or right-hand side as typed after {@code :map}, as keys. {@code <>} names a key
     * ({@code <lt>} is {@code <}); {@code <Leader>} and {@code <LocalLeader>} are {@code leader} and
     * {@code localLeader}; {@code <Nop>} is no key; CTRL-V takes the next character
     * literally (a CTRL-V ending a left-hand side stays); a left-hand side starting {@code #1} is
     * {@code <F1>}. A control character is its CTRL key. A backslash is an ordinary character, as with Vim's default
     * 'cpoptions'.
```

with:

```java
     * replace_termcodes: a left- or right-hand side as typed after {@code :map}, as keys. {@code <>} names a key
     * ({@code <lt>} is {@code <}); {@code <Leader>} and {@code <LocalLeader>} are {@code leader} and
     * {@code localLeader}; {@code <Action>(id)} runs an Editor command; {@code <Nop>} is no key; CTRL-V takes the
     * next character literally (a CTRL-V ending a left-hand side stays); a left-hand side starting {@code #1} is
     * {@code <F1>}. A control character is its CTRL key. A backslash is an ordinary character, as with Vim's default
     * 'cpoptions'.
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java`, replace:

```java
                        i = close + 1;
                        continue;
                    }
                    if (body.equals("nop")) {
```

with:

```java
                        i = close + 1;
                        continue;
                    }
                    // IdeaVim's <Action>(id): an Editor command, run where the key is reached.
                    if (body.equals("action") && close + 1 < text.length() && text.charAt(close + 1) == '(') {
                        int end = text.indexOf(')', close + 2);
                        if (end > close + 2) {
                            keys.add(KeyInput.action(text.substring(close + 2, end)));
                            i = end + 1;
                            continue;
                        }
                    }
                    if (body.equals("nop")) {
```

- [ ] **Step 4: Add the commands**

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExVimrc.java`, replace:

```java
    }

    /** {@code :syntax} and {@code :filetype}: accepted, as vimrcs say them, and nothing happens. */
    static void ignored(ExCommand cmd) { }
```

with:

```java
    }

    /**
     * IdeaVim's {@code :sethandler {key} {modes}:{vim|ide} …} (spec 2.2): who gets a key in each mode; modes
     * {@code n i v x o c}, or {@code a} for all, joined with {@code -}.
     */
    static void sethandler(ExCommand cmd) {
        String[] parts = cmd.arg.strip().split("\\s+");
        if (parts.length < 2 || parts[0].isEmpty()) throw new VimFailure("E471: Argument required");
        List<KeyInput> keys = KeyInput.parse(parts[0]);
        if (keys.size() != 1) throw new VimFailure("E474: Invalid argument: " + parts[0]);
        KeyHandlers handlers = cmd.editor.vim().handlers();
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int colon = part.indexOf(':');
            String who = colon < 0 ? "" : part.substring(colon + 1).toLowerCase(Locale.ROOT);
            if (colon <= 0 || !who.equals("vim") && !who.equals("ide")) throw new VimFailure("E474: Invalid argument: " + part);
            try {
                handlers.set(keys.getFirst(), part.substring(0, colon), who.equals("vim") ? KeyHandlers.Handler.VIM : KeyHandlers.Handler.IDE);
            } catch (IllegalArgumentException bad) {
                throw new VimFailure("E474: Invalid argument: " + part);
            }
        }
    }

    /** {@code :action {id}}: runs one of the Editor's commands, as {@code <Action>(id)} does. */
    static void action(ExCommand cmd) {
        String id = cmd.arg.strip();
        if (!cmd.editor.vim().host().actions().run().test(cmd.editor, id)) throw new VimFailure("Not an Editor command: " + id);
    }

    /** {@code :actionlist [pattern]}: the Editor's command ids and titles, those holding the text if one is given. */
    static void actionlist(ExCommand cmd) {
        String pattern = cmd.arg.strip().toLowerCase(Locale.ROOT);
        VimEditor editor = cmd.editor;
        editor.say("--- Actions ---");
        for (VimHost.Action action : editor.vim().host().actions().list().get()) {
            if (!pattern.isEmpty() && !action.id().toLowerCase(Locale.ROOT).contains(pattern)
                && !action.title().toLowerCase(Locale.ROOT).contains(pattern)) continue;
            editor.say(String.format("%-24s %s", action.id(), action.title()));
        }
    }

    /** {@code :syntax} and {@code :filetype}: accepted, as vimrcs say them, and nothing happens. */
    static void ignored(ExCommand cmd) { }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, replace:

```java
        def("source", EXTRA | NEEDARG | TRLBAR, Addr.NONE, "run {file}'s lines as command lines, as the vimrc's", ExVimrc::source);
        def("messages", RANGE | EXTRA | TRLBAR, Addr.OTHER, "the message history: errors, and the vimrc's skipped lines; `:messages clear`", ExVimrc::messages);
        def("syntax", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "accepted and ignored: the Editor highlights by language", ExVimrc::ignored);
        def("filetype", EXTRA | TRLBAR, Addr.NONE, "accepted and ignored: the Editor knows each file's language", ExVimrc::ignored);
```

with:

```java
        def("source", EXTRA | NEEDARG | TRLBAR, Addr.NONE, "run {file}'s lines as command lines, as the vimrc's", ExVimrc::source);
        def("messages", RANGE | EXTRA | TRLBAR, Addr.OTHER, "the message history: errors, and the vimrc's skipped lines; `:messages clear`", ExVimrc::messages);
        def("sethandler", EXTRA | TRLBAR, Addr.NONE, "`:sethandler {key} {modes}:{vim|ide} …`: who gets a key in each mode", ExVimrc::sethandler);
        def("action", EXTRA | NEEDARG | TRLBAR, Addr.NONE, "run the Editor command {id}, as `<Action>(id)` does in a mapping", ExVimrc::action);
        def("actionlist", EXTRA | TRLBAR, Addr.NONE, "list the Editor's command ids, or those holding [text]", ExVimrc::actionlist);
        def("syntax", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "accepted and ignored: the Editor highlights by language", ExVimrc::ignored);
        def("filetype", EXTRA | TRLBAR, Addr.NONE, "accepted and ignored: the Editor knows each file's language", ExVimrc::ignored);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExTable.java`, replace:

```java

/**
 * Every Ex command name Vim 9.1 knows, in the order of its command table (ex_cmds.h). A typed name stands for the
 * first command in this order that starts with it, as Vim's find_ex_command resolves abbreviations: {@code :s} is
 * {@code :substitute} and {@code :d} is {@code :delete}. Jasper implements some of them ({@link Ex}); the others fail
```

with:

```java

/**
 * Every Ex command name Vim 9.1 knows, in the order of its command table (ex_cmds.h), and IdeaVim's
 * {@code :sethandler}, {@code :action} and {@code :actionlist} after them. A typed name stands for the
 * first command in this order that starts with it, as Vim's find_ex_command resolves abbreviations: {@code :s} is
 * {@code :substitute} and {@code :d} is {@code :delete}. Jasper implements some of them ({@link Ex}); the others fail
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExTable.java`, replace:

```java
        "windo", "winpos", "wlrestore", "wnext", "wprevious", "wq", "wqall", "wundo", "wviminfo", "xit", "xall",
        "xmap", "xmapclear", "xmenu", "xnoremap", "xnoremenu", "xrestore", "xunmap", "xunmenu", "yank", "z", "!",
        "#", "&", "*", "<", "=", ">", "@", "{", "}", "~", "Next", "Print", "X", "++", "--");

    /**
```

with:

```java
        "windo", "winpos", "wlrestore", "wnext", "wprevious", "wq", "wqall", "wundo", "wviminfo", "xit", "xall",
        "xmap", "xmapclear", "xmenu", "xnoremap", "xnoremenu", "xrestore", "xunmap", "xunmenu", "yank", "z", "!",
        "#", "&", "*", "<", "=", ">", "@", "{", "}", "~", "Next", "Print", "X", "++", "--",
        // IdeaVim's own, after Vim's so that no Vim abbreviation changes.
        "sethandler", "action", "actionlist");

    /**
```

- [ ] **Step 5: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains `:sethandler`, `:action` and `:actionlist`.

- [ ] **Step 6: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.ExTest'
```

Expected: 35 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.vim.TypeaheadTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 200 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1746 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`.

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/vim-v3b
git add plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java plugins/editor/src/main/java/dev/jasper/editor/vim/KeyCodes.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimHost.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java plugins/editor/src/main/java/dev/jasper/editor/vim/ExVimrc.java plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java plugins/editor/src/main/java/dev/jasper/editor/vim/ExTable.java plugins/editor/src/test/java/dev/jasper/editor/vim/VimrcTest.java plugins/editor/src/test/java/dev/jasper/editor/vim/KeyInputTest.java plugins/editor/src/test/java/dev/jasper/editor/vim/ExMapsTest.java plugins/editor/src/test/java/dev/jasper/editor/vim/ExTest.java docs/editor-vim-reference.md
git commit -m "Add sethandler, <Action>(id), :action and :actionlist

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: The Editor reads the vimrc

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java`, `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`

**Interfaces:**
- Consumes: `Vimrc.read` and `Vim.reset` (Task 2); `VimHost.Actions` (Task 3).
- Produces: `EditorWindow.reloadVimrc()`, `EditorWindow.vimrcFile() -> Path` (`vimrc` in the Editor's data folder), the Editor command `reload-vimrc` ("Reload vimrc", in the ⋯ menu, the palette and `:actionlist`).

The window reads the vimrc the first time a tab has Vim on (at start with `vim = true`, or on Toggle Vim Mode), in the selected tab, and reports through the plugin's notices: `vimrc: 2 lines were skipped; see :messages`, or `vimrc: could not read … (reason); Vim runs with its defaults.`; a missing vimrc says nothing.

- [ ] **Step 1: Write the failing tests**

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`, replace:

```java
    }

    @Test void vimWritesOpensListsSelectsAndClosesTabs() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
```

with:

```java
    }

    @Test void theVimrcIsReadWhenVimFirstTurnsOnAndReloadReadsItAgain() throws Exception {
        Path vimrc = window.vimrcFile();
        assertThat(vimrc).isEqualTo(home.resolve("plugins/dev.jasper.editor/data/vimrc"));
        Files.writeString(vimrc, "nnoremap x dd\nbogus\nPlug 'tpope/vim-surround'\n");
        EditorTab tab = opened(file("rc.txt", "one\ntwo\nthree\n"));
        assertThat(host.notices()).as("Vim is off: nothing is read").isEmpty();
        window.toggleVim();
        assertThat(host.notices()).containsExactly("dev.jasper.editor: vimrc: 2 lines were skipped; see :messages");
        vimKeys(tab, "x");
        assertThat(tab.area().getText()).isEqualTo("two\nthree\n");
        vimKeys(tab, ":messages<CR>");
        assertThat(tab.vimEditor().orElseThrow().output()).extracting(dev.jasper.editor.vim.VimEditor.OutputLine::text)
            .contains("E492: Not an editor command: bogus");
        Files.writeString(vimrc, "nnoremap y dd\n");
        vimKeys(tab, "<CR>\"ayy");
        window.reloadVimrc();
        vimKeys(tab, "x");
        assertThat(tab.area().getText()).as("x is Vim's own again").isEqualTo("wo\nthree\n");
        vimKeys(tab, "y\"ap");
        assertThat(tab.area().getText()).as("the new vimrc's y, and register a kept").isEqualTo("three\ntwo\n");
        window.toggleVim();
        window.toggleVim();
        assertThat(host.notices()).as("read once, and again only on Reload vimrc").hasSize(1);
    }

    @Test void aVimrcThatCannotBeReadIsReported() throws Exception {
        Files.createDirectories(window.vimrcFile());
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
        opened(file("rc.txt", "one\n"));
        assertThat(host.notices()).singleElement().asString()
            .startsWith("dev.jasper.editor: vimrc: could not read " + window.vimrcFile()).endsWith("; Vim runs with its defaults.");
    }

    @Test void vimActionsAreTheEditorsCommands() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
        EditorTab tab = opened(file("act.txt", "one\n"));
        vimKeys(tab, ":actionlist vim<CR>");
        assertThat(tab.vimEditor().orElseThrow().output()).extracting(dev.jasper.editor.vim.VimEditor.OutputLine::text)
            .containsExactly("--- Actions ---", "toggle-vim               Toggle Vim Mode", "reload-vimrc             Reload vimrc");
        boolean wrap = tab.session().wrap();
        vimKeys(tab, "<CR>:action wrap<CR>");
        assertThat(tab.session().wrap()).isEqualTo(!wrap);
        vimKeys(tab, ":nnoremap <lt>Space>w <lt>Action>(wrap)<CR> w");
        assertThat(tab.session().wrap()).isEqualTo(wrap);
    }

    @Test void vimWritesOpensListsSelectsAndClosesTabs() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.EditorWindowTest'
```

Expected: FAIL: the test does not compile (`EditorWindow.vimrcFile()` does not exist).

- [ ] **Step 3: Read the vimrc, add Reload vimrc and give Vim the Editor's commands**

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
import dev.jasper.editor.vim.Vim;
import dev.jasper.editor.vim.VimHost;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.DialogSpec;
```

with:

```java
import dev.jasper.editor.vim.Vim;
import dev.jasper.editor.vim.VimHost;
import dev.jasper.editor.vim.Vimrc;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.DialogSpec;
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
    /** Vim mode as toggled in this session, until the settings file says the same; null to follow the settings. */
    private Boolean vimToggled;

    /**
```

with:

```java
    /** Vim mode as toggled in this session, until the settings file says the same; null to follow the settings. */
    private Boolean vimToggled;
    /** Whether the vimrc has been read into {@link #vim}: the first time Vim turns on, and again on Reload vimrc. */
    private boolean vimrcRead;

    /**
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
            new Command("language", "Language…", Optional.empty(), false, this::chooseLanguage),
            new Command("wrap", "Toggle Soft Wrap", Optional.empty(), false, this::toggleWrap),
            new Command("toggle-vim", "Toggle Vim Mode", Optional.empty(), false, this::toggleVim));
        vim = new Vim(new VimHost(SystemClipboard::read, SystemClipboard::write, documents()));
        watcher = new LocalWatcher(context.background(), environment.ui(), model::sessions);
        ticker = new Timer(2000, event -> watcher.tick());
```

with:

```java
            new Command("language", "Language…", Optional.empty(), false, this::chooseLanguage),
            new Command("wrap", "Toggle Soft Wrap", Optional.empty(), false, this::toggleWrap),
            new Command("toggle-vim", "Toggle Vim Mode", Optional.empty(), false, this::toggleVim),
            new Command("reload-vimrc", "Reload vimrc", Optional.empty(), false, this::reloadVimrc));
        vim = new Vim(new VimHost(SystemClipboard::read, SystemClipboard::write, documents(), actions()));
        watcher = new LocalWatcher(context.background(), environment.ui(), model::sessions);
        ticker = new Timer(2000, event -> watcher.tick());
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
            tab.vim(vimOn() ? Optional.of(vim) : Optional.empty());
        }
        refreshStatus();
    }
```

with:

```java
            tab.vim(vimOn() ? Optional.of(vim) : Optional.empty());
        }
        readVimrcOnce();
        refreshStatus();
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
        vimToggled = on;
        for (EditorTab tab : editorTabs()) tab.vim(on ? Optional.of(vim) : Optional.empty());
        refreshStatus();
        CompletableFuture<Void> saved;
```

with:

```java
        vimToggled = on;
        for (EditorTab tab : editorTabs()) tab.vim(on ? Optional.of(vim) : Optional.empty());
        readVimrcOnce();
        refreshStatus();
        CompletableFuture<Void> saved;
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
                + " until the Editor closes: the setting could not be saved (" + reason + ").", List.of("OK"))));
        });
    }
```

with:

```java
                + " until the Editor closes: the setting could not be saved (" + reason + ").", List.of("OK"))));
        });
    }

    /**
     * Reload vimrc: mappings, options, {@code sethandler} entries and variables go back to their defaults, then the
     * vimrc is read again, now if a tab has Vim on and otherwise when Vim next turns on; registers and marks stay
     * (Vim spec 7.1).
     */
    public void reloadVimrc() {
        vim.reset();
        vimrcRead = false;
        readVimrcOnce();
    }

    /** @return the vimrc: {@code vimrc} in the Editor's data folder, {@code <home>/plugins/dev.jasper.editor/data} */
    public Path vimrcFile() { return context.dataDirectory().resolve("vimrc"); }

    /**
     * Reads the vimrc the first time a tab has Vim on (Vim spec 1.2), running its lines in that tab (the selected one
     * if it can). Lines it skipped and a vimrc it cannot read are reported as a notice; a missing vimrc is not.
     */
    private void readVimrcOnce() {
        if (vimrcRead) return;
        Optional<dev.jasper.editor.vim.VimEditor> editor = selected().flatMap(EditorTab::vimEditor)
            .or(() -> editorTabs().stream().flatMap(tab -> tab.vimEditor().stream()).findFirst());
        if (editor.isEmpty()) return;
        vimrcRead = true;
        Path file;
        try { file = vimrcFile(); }
        catch (RuntimeException unavailable) {
            context.notices().error("vimrc: the Editor's data folder is not available; Vim runs with its defaults.");
            return;
        }
        Vimrc.Result read = Vimrc.read(editor.get(), file);
        if (read.error() != null) context.notices().error("vimrc: could not read " + file + " (" + read.error() + "); Vim runs with its defaults.");
        else if (read.skipped() > 0)
            context.notices().error("vimrc: " + read.skipped() + (read.skipped() == 1 ? " line was" : " lines were") + " skipped; see :messages");
    }

    /** The Editor's commands for Vim's {@code <Action>(id)}, {@code :action} and {@code :actionlist}, run for the tab Vim is in. */
    private VimHost.Actions actions() {
        return new VimHost.Actions(
            () -> commands.stream().map(command -> new VimHost.Action(command.id(), command.title())).toList(),
            (editor, id) -> {
                Optional<Command> command = commands.stream().filter(c -> c.id().equals(id)).findFirst();
                if (command.isEmpty()) return false;
                tabOf(editor).ifPresent(this::activate);
                command.get().run().run();
                return true;
            });
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
        handle.setCommands(descriptions);
        activate(tab);
        refresh();
    }
```

with:

```java
        handle.setCommands(descriptions);
        activate(tab);
        readVimrcOnce();
        refresh();
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java`, replace:

```java
                .label("Soft wrap").description("Wrap long lines in tabs opened from now on.").section("Editing").keywords("editor", "wrap"));
            vim = settings.declare(SettingSpec.of("vim", SettingKind.bool(), false)
                .label("Vim mode").description("Modal editing as in IdeaVim; the Vim status item and Toggle Vim Mode change it.")
                .section("Editing").keywords("editor", "vim", "modal"));
            maxFileMib = settings.declare(SettingSpec.of("max_file_mib", SettingKind.integer(1, 256), 10)
```

with:

```java
                .label("Soft wrap").description("Wrap long lines in tabs opened from now on.").section("Editing").keywords("editor", "wrap"));
            vim = settings.declare(SettingSpec.of("vim", SettingKind.bool(), false)
                .label("Vim mode").description("Modal editing as in IdeaVim; the Vim status item and Toggle Vim Mode change it. "
                    + "Its vimrc is the file vimrc in the Editor's data folder (plugins/dev.jasper.editor/data).")
                .section("Editing").keywords("editor", "vim", "modal"));
            maxFileMib = settings.declare(SettingSpec.of("max_file_mib", SettingKind.integer(1, 256), 10)
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.*'
```

Expected: 58 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1746 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/vim-v3b
git add plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java
git commit -m "Read the vimrc when Vim turns on, and add Reload vimrc

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 5: Documentation, the spec amendments and the final check

**Files:**
- Modify: `docs/vim-divergences.md`, `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec

- [ ] **Step 1: List the differences**

Append to `docs/vim-divergences.md`:

```markdown
- **The vimrc runs in a tab.** Its lines run as command lines in the selected tab that has Vim on when it is read, so
  a `:normal` or `:s` in a vimrc changes that tab; mappings, options, variables and `sethandler` entries are global.
- **Vim script is literals only.** `:let` sets a global variable (`mapleader`, `g:name`), a register (`@a`, `@/`) or
  an option (`&tw`, `&l:sw`) to one string or number; expressions, `s:` variables, functions and `:echo` fail with
  E319. In a vimrc `if` is true only for `has('ide')`, `has('jasper')` and a number other than 0.
- **Extensions come later.** `Plug '…'` and `packadd` lines fail, and are counted in the vimrc's notice, as in a Vim
  without a plugin manager; `let g:…` settings for extensions are kept for when they come.
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
```

- [ ] **Step 2: Describe the vimrc in the Editor guide**

In `docs/editor.md`, replace:

```markdown
- Not yet: a vimrc, `sethandler` lines and emulated plugins such as surround and commentary. They follow in later
  releases. An uppercase mark (`mA`) set in one tab takes you back to that tab (`'A`).
```

with:

```markdown
- The vimrc: `plugins/dev.jasper.editor/data/vimrc` under Jasper's home, in UTF-8, read the first time Vim turns on
  and again with **Reload vimrc** (in the ⋯ menu and the command palette), which first puts mappings, options and
  `sethandler` entries back to their defaults. It holds command lines, as a Vim or IdeaVim vimrc does: `set`,
  `let mapleader = ","`, the mapping commands, `sethandler`, `source ~/.vimrc`, and `if has('ide')` … `endif` blocks
  (true in Jasper, so IdeaVim's blocks apply). Lines Jasper cannot run are skipped: a notice says how many, and
  `:messages` lists each with its line and the reason.
- `sethandler <C-f> n:vim i:ide` decides who gets a key in each mode (`n i v x o c`, or `a` for all), as in IdeaVim.
- `<Action>(id)` in a mapping runs one of the Editor's commands (`:nnoremap <Space>w <Action>(save)`), in any mode;
  `:action id` runs one, and `:actionlist` lists the ids.
- Not yet: emulated plugins such as surround and commentary. They follow in a later release. An uppercase mark
  (`mA`) set in one tab takes you back to that tab (`'A`).
```

In `docs/editor.md`, replace:

```markdown
`<Leader>` (a backslash for now)
```

with:

```markdown
`<Leader>` (`let mapleader`, a backslash by default)
```

- [ ] **Step 3: Add the GUI checks for the user**

Append to `docs/editor-verification.md`:

```markdown

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
```

- [ ] **Step 4: Record the state**

In `docs/STATUS.md`, replace:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V3a** (branch `claude/vim-v3a`, plan
```

with:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V3b** (branch `claude/vim-v3b`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v3b.md`, written 2026-09-28): the vimrc (`plugins/dev.jasper.editor/data/vimrc`), read the
  first time Vim turns on and on Reload vimrc, with comments, continuation, `source` (10 deep), `if has('ide')`, and
  skipped lines counted in a notice and listed in `:messages`; `:let` for `mapleader`, registers, options and
  variables; `:sethandler`; `<Action>(id)`, `:action` and `:actionlist` over the Editor's commands; the options vimrcs
  set; `:syntax` and `:filetype` accepted. Every file in the plan was compiled and run against Vim 9.1 before the plan
  was written: 1742 corpus cases (28 new in `vimrc.case`), with files and the Editor's commands in `VimrcTest` and
  `EditorWindowTest`.
  - Not done: executing the plan; GUI checks 80–88 of the [checklist](editor-verification.md) (user-run); plan V4
    (extensions); push.
- **Editor Vim mode, plan V3a** (branch `claude/vim-v3a`, plan
```

- [ ] **Step 5: Amend the spec**

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
Plan V3a (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v3a.md`) amends sections 2.3, 4.3, 6.3, 7.2, 12 and 13, as marked **Amended (V3a)**.
```

with:

```markdown
Plan V3a (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v3a.md`) amends sections 2.3, 4.3, 6.3, 7.2, 12 and 13, as marked **Amended (V3a)**. Plan V3b (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v3b.md`) amends sections 6.3, 7.1, 7.2, 10, 12 and 13, as marked **Amended (V3b)**.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- Reloading resets mappings, options' global values, the handler table and extensions to defaults, then reads the file again; registers and marks are kept.
```

with:

```markdown
- Reloading resets mappings, options' global values, the handler table and extensions to defaults, then reads the file again; registers and marks are kept.

**Amended (V3b):** plan V3b delivers this section except `Plug` and `packadd`, which fail (and count as skipped) until the extensions of plan V4; `let g:…` is stored for them. The lines run in the selected tab that has Vim on. `:let` sets one literal (a string with Vim's quoting and escapes, or a number) into a global variable (`mapleader` is `g:mapleader`), a register or an option (`&tw`); `if` is also true for a nonzero number, and `elseif` and `finish` work. Skipped lines are reported as Vim reports them (`Error detected while processing {file}:`, `line    3:`, the error) in `:messages`, which keeps errors and this report under Vim's "Messages maintainer" line. A relative `source` name starts from the sourcing file's folder; nesting deeper than 10 fails with E169.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
**Amended (V3a):** plan V3a delivers the mapping commands typed on the command line; `<Action>(id)`, `:action` and `:actionlist` move to V3b with the vimrc. `<Leader>` and `<LocalLeader>` are a backslash until V3b reads `let mapleader`. `<silent>` and `<special>` are accepted; `<silent>` changes nothing, since Jasper never echoes the command line a mapping types. `:smap`, `:lmap`, `:tmap` and their forms fail with E319 (no Select, Lang-Arg or terminal mode); `:vmap` and `:map` still include Select mode for their listings. Listings follow Vim's layout and order (`n  x           * dd`). The recorder clears Vim's own mappings (`<D-v>` on macOS) before each case.
```

with:

```markdown
**Amended (V3a):** plan V3a delivers the mapping commands typed on the command line; `<Action>(id)`, `:action` and `:actionlist` move to V3b with the vimrc. `<Leader>` and `<LocalLeader>` are a backslash until V3b reads `let mapleader`. `<silent>` and `<special>` are accepted; `<silent>` changes nothing, since Jasper never echoes the command line a mapping types. `:smap`, `:lmap`, `:tmap` and their forms fail with E319 (no Select, Lang-Arg or terminal mode); `:vmap` and `:map` still include Select mode for their listings. Listings follow Vim's layout and order (`n  x           * dd`). The recorder clears Vim's own mappings (`<D-v>` on macOS) before each case.

**Amended (V3b):** `<Action>(id)`, `:action` and `:actionlist` come with V3b through `VimHost.Actions`, a record of callbacks the Editor fills with its commands (Reload vimrc among them). An `<Action>(id)` key runs the command where the typeahead reaches it, in any mode, without leaving the mode; an unknown id fails with `Not an Editor command: id`, which ends the mapping. `<Leader>` and `<LocalLeader>` take `g:mapleader` and `g:maplocalleader` when the mapping is made. `:sethandler` works typed as well as in the vimrc, and IdeaVim's `sethandler`, `action` and `actionlist` follow Vim's 600 names in the command table, so no Vim abbreviation changes.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
 **Amended (V3a):** `timeout`, `timeoutlen` and `maxmapdepth` (`mmd`, Vim's, default 1000) come with the mappings in V3a; the rest with the vimrc in V3b.
```

with:

```markdown
 **Amended (V3a):** `timeout`, `timeoutlen` and `maxmapdepth` (`mmd`, Vim's, default 1000) come with the mappings in V3a; the rest with the vimrc in V3b. **Amended (V3b):** the vimrc options come with V3b and change nothing (`undolevels` included: the Editor's undo limit applies); `ttimeoutlen`, `undolevels`, `laststatus` and `maxmapdepth` take negative values as in Vim; `fileformat` takes `unix`, `dos` or `mac`. `:syntax` and `:filetype` are accepted and do nothing.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
| Engine bug (unexpected exception while running a command) | The command is abandoned, the undo group closed, the mode reset to Normal, `Internal error; the command was cancelled` in the Vim bar, and the exception logged through the plugin log. Text already changed stays changed and is undoable. |
```

with:

```markdown
| Engine bug (unexpected exception while running a command) | The command is abandoned, the undo group closed, the mode reset to Normal, `Internal error; the command was cancelled` in the Vim bar, and the exception logged through the plugin log. Text already changed stays changed and is undoable. |

**Amended (V3b):** the notices are the plugin's error notices: `vimrc: 3 lines were skipped; see :messages` (`1 line was skipped`), and `vimrc: could not read {file} ({reason}); Vim runs with its defaults.`; a missing vimrc says nothing.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
**Amended (V2a):** the Ex classes (`Ex`, `ExTable`, `ExCommand`, `ExInfo`, `ExLines`, `ExSubstitute`, `ExGlobal`) live in `dev.jasper.editor.vim`, not `vim.ex`: they use the engine's package-private API, and a separate package would need a public API or a dependency cycle. `vim.ui` depends on `vim` only. **Amended (V2b):** `VimHost.Documents` (a record of callbacks) is how the engine reaches the Editor's tabs; `ExFiles`, `Completion` and `LineNumbers` join `dev.jasper.editor.vim`, and there is no `RelativeGutter`. **Amended (V3a):** `Typeahead`, `Mappings`, `ExMaps`, `Macros` and `KeyCodes` join `dev.jasper.editor.vim`; there is no `KeyNotation` (`KeyInput` reads the notation).
```

with:

```markdown
**Amended (V2a):** the Ex classes (`Ex`, `ExTable`, `ExCommand`, `ExInfo`, `ExLines`, `ExSubstitute`, `ExGlobal`) live in `dev.jasper.editor.vim`, not `vim.ex`: they use the engine's package-private API, and a separate package would need a public API or a dependency cycle. `vim.ui` depends on `vim` only. **Amended (V2b):** `VimHost.Documents` (a record of callbacks) is how the engine reaches the Editor's tabs; `ExFiles`, `Completion` and `LineNumbers` join `dev.jasper.editor.vim`, and there is no `RelativeGutter`. **Amended (V3a):** `Typeahead`, `Mappings`, `ExMaps`, `Macros` and `KeyCodes` join `dev.jasper.editor.vim`; there is no `KeyNotation` (`KeyInput` reads the notation). **Amended (V3b):** `Vimrc` and `ExVimrc` join `dev.jasper.editor.vim`; there is no `vim.ex` package.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- **Plan V3: macros and mappings.** Section 4.3 and section 7, with `timeoutlen` (section 2.3) and `sethandler` lines. **Amended (V3a):** split in two by the user on 2026-09-28. Plan V3a: macros, the typeahead and the mapping commands typed on the command line, with 'timeout', 'timeoutlen' and 'maxmapdepth'. Plan V3b: the vimrc (section 7.1), `sethandler` lines, `let mapleader`, `<Action>(id)`, `:action`, `:actionlist` and the vimrc options.
```

with:

```markdown
- **Plan V3: macros and mappings.** Section 4.3 and section 7, with `timeoutlen` (section 2.3) and `sethandler` lines. **Amended (V3a):** split in two by the user on 2026-09-28. Plan V3a: macros, the typeahead and the mapping commands typed on the command line, with 'timeout', 'timeoutlen' and 'maxmapdepth'. Plan V3b: the vimrc (section 7.1), `sethandler` lines, `let mapleader`, `<Action>(id)`, `:action`, `:actionlist` and the vimrc options. **Amended (V3b):** plan V3b delivered the vimrc, `sethandler`, `let`, `<Action>`, `:action`, `:actionlist` and the vimrc options; `Plug` and `packadd` move to V4 with the extensions.
```

- [ ] **Step 6: Run everything**

Run:

```bash
./gradlew check
```

Expected: BUILD SUCCESSFUL (4,359 tests, 0 failures, 0 errors, 5 skipped).

- [ ] **Step 7: Check source hygiene**

Run the snippet in `AGENTS.md` ("Source hygiene"). Expected: no output.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v3b
git add docs
git commit -m "Document Vim mode V3b: the vimrc

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
