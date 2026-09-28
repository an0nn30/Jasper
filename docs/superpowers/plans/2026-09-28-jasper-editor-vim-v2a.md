# Editor Vim mode, plan V2a: the Ex command line — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Vim's `:` command line in the Editor's Vim mode: ranges, `:s` with every flag (including confirmation), `:g`/`:v`, the line commands, marks, registers and lists, `:u`/`:red`/`:noh`, and `:set`, with a multi-line output panel, all behaving as Vim 9.1 does.

**Architecture:** `Ex` ports Vim's ex_docmd.c: do_cmdline and do_one_cmd (the command name resolved against `ExTable`, Vim's 600 names in table order, so abbreviations are Vim's), parse_cmd_address and get_address (ranges), and separate_nextcmd (`|`). A command line is one undo step; what it says is gathered and shown as a message, or in an output panel above the Vim bar when it is more than one line (Vim's hit-enter prompt). Questions (`:s///c`, "Backwards range given") pause the command: it returns with the question open and each caller leaves the rest of its work as a continuation, which runs once the key is answered. `ExInfo` (listings, marks, options), `ExLines` (line commands), `ExSubstitute` (ex_substitute, vim_regsub, regtilde) and `ExGlobal` (ex_global, global_exe) are ports of the Vim functions they name. `Options` gains do_set's full syntax and the three scopes. The corpus can now compare what the keys said.

**Tech Stack:** Java 25 (JBR), Swing, RSyntaxTextArea 3.6.0, JUnit 6 and AssertJ, Vim 9 (only for `recordVimCorpus`).

**Spec:** `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md` (plan V2a of section 13, sections 3.8, 5.2, 6 and 11). Plans V1a, V1b and V1c are merged; their headers describe the engine.

**Status:** Written 2026-09-28 on branch `claude/vim-v2a` (worktree `.worktrees/vim-v2a`, from `main` at d4c999e5). Every source file, test and corpus case below was compiled and run before the plan was written: against Vim 9.1 all 1565 corpus cases (440 new: ex-range 86, ex-lines 117, ex-info 87, ex-substitute 104, ex-global 43, and 3 in repeat-undo) passed, five of them divergent, and every command and Ex command has a case. Applied to a fresh checkout of `d4c999e5` exactly as written, `./gradlew check` passed (4,068 tests, 0 failures, 0 errors, 5 skipped).

## Deviations from the spec (approved scope, adjusted in this plan)

Task 6 writes these into the spec as **Amended (V2a)**.

- **Split:** the user split plan V2 on 2026-09-28. V2a (this plan): the command line, ranges, the line, substitute, global, mark, register and option commands, `:u`/`:red`/`:noh`, the output panel and 'hlsearch' on. V2b: files, buffers and tabs through the Editor (`:w :q :e :b :ls :tab…`, `ZZ`, `ZQ`), Tab completion on the command line, and the relative-number gutter.
- **Package:** the Ex classes live in `dev.jasper.editor.vim`, not `vim.ex` (spec 12): they use the engine's package-private API, and a separate package would need a public API or a dependency cycle.
- **Commands Jasper lacks:** every Ex command name Vim knows and Jasper does not run fails with `E319: Sorry, the command is not available in this version`, as Vim does for commands left out of its build; `\=` in a substitute string too (there is no Vim script).
- **The output panel** (spec 5.2) behaves as Vim's hit-enter prompt: `<CR>`, `<Space>` and `<Esc>` only close it, any other key closes it and runs; after a failed command line only the error stays in the bar, as Vim does not wait then.
- **Messages:** the 'report' messages ("3 fewer lines", "4 lines yanked", "3 lines >ed 1 time", "3 substitutions on 2 lines") are given, also after Normal-mode commands. A search echoes itself before it searches, with Vim's trailing space.
- **Options:** V2a adds 'gdefault'; `number` and `relativenumber` come with V2b's gutter; `timeout`, `timeoutlen`, `undolevels`, `visualbell` and the accepted-and-ignored vimrc options come with V3's vimrc.
- **Corpus format:** `output: yes` makes a case compare what the keys said; the recorder captures it with `:redir` and the case keeps it in `expect-output:`. The recorder types keys with `feedkeys()`, where Vim never waits at the hit-enter prompt, so `ExTest` covers the panel.
- **Fixes to V1 behaviour found on the way:** after `A`, `a`, `I` and the other insert commands, `u` puts the cursor where the insert began (Vim's u_save at the first typed character), and an insert sets `'[` and `']`; the corpus replay reads the mode before closing the editor, so an operator left pending is compared.

## Global Constraints

- Java 25 on the JetBrains Runtime; always `./gradlew`, never a system Gradle. Work in `.worktrees/vim-v2a` on `claude/vim-v2a`; check `git branch --show-current` before every commit (other sessions switch branches in the main checkout). Never commit on `main`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.
- The Editor plugin compiles against the SDK only (`verifyPluginArchitecture`, a root task). `dev.jasper.editor.vim` may use `vim.regex`, RSyntaxTextArea and the JDK; `dev.jasper.editor.vim.ui` adds `dev.jasper.editor.vim`. No SDK change.
- No interface without two real implementations; this plan adds none (`Ex.Handler` is a functional type for method references, as `VimCommands.Action` is).
- Source hygiene: never put raw control, private-use or unpaired surrogate characters in `.java` files; use escapes. No run of three backticks may appear in any file this plan embeds (the docs link check strips fenced blocks lazily).
- **Do not launch the GUI.** GUI checks go in `docs/editor-verification.md` for the user.
- Engine code runs on the event thread in the app. Tests that type through a `VimEditor` run inside `SwingUtilities.invokeAndWait`.
- The corpus is the authority on Vim behaviour; mark a case `divergent:` only for a documented, deliberate difference.
- `vim` must be on `PATH` for `recordVimCorpus` (the dev Mac has Vim 9.1 at `/usr/bin/vim`). `check` never runs Vim.
- The ports keep Vim's control flow and names (`do_one_cmd`, `get_address`, `ex_substitute`'s `copycol`/`matchcol`/`nmatch_tl`, `global_exe`, `do_set`); a reviewer's "simplification" is not a reason to change them — the corpus proves them.

## File structure

New, `plugins/editor/src/main/java/dev/jasper/editor/vim/`:

| File | Responsibility |
|---|---|
| `ExTable.java` | Vim's 600 Ex command names in table order (generated from ex_cmds.h); resolving an abbreviation |
| `ExCommand.java` | one parsed command (Vim's exarg_T): range, name, `!`, register, count, argument, flags |
| `Ex.java` | the command table Jasper runs, `:` and Visual `:`, do_cmdline/do_one_cmd, ranges, `|`, questions and continuations |
| `ExInfo.java` | `:p :P :l :nu :# := :k :mark :delm :marks :jumps :changes :reg :di :noh :set :setl :setg` |
| `ExLines.java` | `:d :y :> :< :pu :j :m :t :co :norm :sort :left :right :center :u :red` |
| `ExSubstitute.java` | `:s :& :&& :~`, `&`, `g&`; vim_regsub and regtilde |
| `ExGlobal.java` | `:g :g! :v` |

Changed there: `Search` (separate search and substitute patterns, Ex address searches, the echo), `Registers` (`":`), `Vim` (the `:` history, global marks for `:marks`), `Marks` (`:global`'s line marks, `:move` keeping marks, `:delm!`, pcmark frozen during `:g`), `MarkCommands` (`setmark` for `m` and `:mark`, digit marks), `VimUndoManager` (change numbers for `:undo N`, `sync` for `:s///c`), `InsertMode` (undo cursor and `'[ ']` after an insert), `CommandLine` (initial text), `VimFailure` (quiet failures), `Options` (do_set), `VimEditor` (output panel, questions, message gathering, 'report' messages), `Machine` (`:` keeps the column), `VimCommands` (registration), `Operators` and `Put` ('report' messages, `:>`'s cursor, `:put`'s cursor), `regex/VimRegex` (whether a pattern spans lines; group positions), `ui/VimBar` (the output panel and questions).
Tests: `vim/ExTest.java` and `vim/SearchPatternsTest.java` (new); `OptionsTest`, `VimBarTest`, `VimCorpusTest`, `VimDocsTest` and the corpus harness (`CorpusCase`, `CorpusFile`, `CorpusRunner`, `RecordCorpus`) change. Corpus: `ex-range.case`, `ex-lines.case`, `ex-info.case`, `ex-substitute.case`, `ex-global.case`, and three cases added to `repeat-undo.case`.
Docs: `docs/vim-divergences.md`, `docs/editor-vim-reference.md` (generated), `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec.

---

### Task 1: Engine groundwork: patterns, marks, undo and inserts

**Files:**
- Replace in `plugins/editor/src/main/java/dev/jasper/editor/vim/`: `Search.java`, `Registers.java`, `Vim.java`, `Marks.java`, `MarkCommands.java`, `VimUndoManager.java`, `InsertMode.java`, `CommandLine.java`, `VimFailure.java`, `regex/VimRegex.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/SearchPatternsTest.java` (new); `plugins/editor/src/test/resources/vim/corpus/repeat-undo.case` (three cases appended)

**Interfaces:**
- Consumes: plans V1a–V1c's engine.
- Produces (used by Tasks 3–5): `Search` constants `SEARCH`, `SUBSTITUTE`; `String pattern()` (last used), `searchPattern()`, `usedPattern()`, `void save(int idx, String pat)`, `String choose(String pat, int saveAs, int use)` (search_regcomp's choice: empty means the last pattern of kind `use`, -1 for the last used; a new one is remembered as `saveAs`, -1 for both; E35/E33 when there is none), `int[] address(Context, char dir, String text, int from)` (`{line, charsUsed}`), `int repeatAddress(Context, char which, int from)`, and the fields `oldSub`, `prevSub`, `subAll`, `subAsk`, `subCount`, `subError`, `subPrint`, `subList`, `subNumber`, `subIgnoreCase` (ex_substitute's remembered state). `VimRegex.multiline()`, `groupStart(Matcher, int)`, `groupEnd(Matcher, int)`. `Registers.lastCommand(String)` and the readable `":` register. `Vim.commandHistory()`, `rememberCommand(String)`, `clearGlobalMark(char)`, `globalMarks()` (a `SortedMap` of `Vim.GlobalMark(editor, pos)`). `Marks.markLines(List<Integer>)`, `firstMarked()`, `clearMarked()`, `moving(first, last, target, Runnable)`, `clearAll()`, `cleanupJumps()`, field `frozen`. `MarkCommands.setmark(VimEditor, char, Pos)`. `VimUndoManager.sync()`, `doneSeqs()`, `undoneSeqs()`. `CommandLine.start(type, history, initial, changed, done, cancelled)`. `VimFailure.quiet()` and field `quiet`.

The search keeps Vim's two last patterns: `/` sets the search pattern, `:s` the substitute pattern, and an empty pattern means the last used one, so `n` after `:s/foo/` finds `foo`; 'hlsearch' shows the last used. `VimRegex` records whether a pattern can span lines (`\n`, the `\_` items, `\%^`, `\%$`), so `:s` and `:g` can match a line alone. An insert now records the undo step's cursor where it begins and sets `'[` and `']` around what it inserted; the three cases below fail before this task.

- [ ] **Step 1: Write the failing tests**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/SearchPatternsTest.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Vim's two last patterns (search.c's spats and last_idx) and what the Ex commands need from a translated pattern. */
class SearchPatternsTest {
    @Test void theSearchAndSubstitutePatternsAreKeptApart() {
        var search = new Search();
        search.save(Search.SEARCH, "a");
        assertThat(search.pattern()).isEqualTo("a");
        assertThat(search.choose("b", Search.SUBSTITUTE, -1)).isEqualTo("b");
        assertThat(search.searchPattern()).as(":s leaves the search pattern").isEqualTo("a");
        assertThat(search.pattern()).as("the last used pattern, which 'hlsearch' shows").isEqualTo("b");
        assertThat(search.choose("", Search.SEARCH, -1)).as("empty means the last used, and it becomes the search pattern").isEqualTo("b");
        assertThat(search.searchPattern()).isEqualTo("b");
        assertThat(search.history()).containsExactly("b");
        assertThatThrownBy(() -> new Search().choose("", Search.SUBSTITUTE, Search.SUBSTITUTE))
            .hasMessage("E33: No previous substitute regular expression");
        assertThatThrownBy(() -> new Search().choose("", Search.SEARCH, -1)).hasMessage("E35: No previous regular expression");
    }

    @Test void aPatternKnowsWhetherItCanSpanLinesAndWhereItsGroupsAre() {
        var pattern = VimRegex.compile("a\\(b\\)c", VimRegex.Settings.DEFAULT);
        assertThat(pattern.multiline()).isFalse();
        var m = pattern.pattern().matcher("xabc");
        assertThat(m.find()).isTrue();
        assertThat(pattern.groupStart(m, 0)).isEqualTo(1);
        assertThat(pattern.groupStart(m, 1)).isEqualTo(2);
        assertThat(pattern.groupEnd(m, 1)).isEqualTo(3);
        assertThat(pattern.groupStart(m, 2)).as("a group the pattern does not have").isEqualTo(-1);
        assertThat(VimRegex.compile("a\\nb", VimRegex.Settings.DEFAULT).multiline()).isTrue();
        assertThat(VimRegex.compile("a\\_sb", VimRegex.Settings.DEFAULT).multiline()).isTrue();
        assertThat(VimRegex.compile("a\\%$", VimRegex.Settings.DEFAULT).multiline()).isTrue();
    }
}
```

Append to `plugins/editor/src/test/resources/vim/corpus/repeat-undo.case`:

```text

=== undo after A puts the cursor where the insert began
text:
  one
  two
cursor: 1,1
keys: Ax<Esc>u
--- vim 9.1
expect-text:
  one
  two
expect-cursor: 1,3
expect-mode: normal

=== undo after A on the second line puts the cursor where the insert began
text:
  one
  two
cursor: 2,1
keys: Axyz<Esc>u
--- vim 9.1
expect-text:
  one
  two
expect-cursor: 2,3
expect-mode: normal

=== undo after o
text:
  one
  two
cursor: 1,1
keys: oxyz<Esc>u
--- vim 9.1
expect-text:
  one
  two
expect-cursor: 1,1
expect-mode: normal
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.SearchPatternsTest'
```

Expected: compilation fails: `Search.SEARCH` does not exist.

- [ ] **Step 3: Replace the changed classes**

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Search.java` with:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Pattern search (spec 3.7), ported from Vim's search.c: searchit (the count loop, where a forward search may start
 * and 'wrapscan') and do_search (offsets and {@code ;}), with normal.c's normal_search, nv_next and nv_ident for
 * {@code n}, {@code N}, {@code *} and {@code #}. As in Vim, the last search pattern and the last substitute pattern
 * are kept apart (spats[RE_SEARCH] and spats[RE_SUBST]) with a note of which was used last; an empty pattern means the
 * last used one. The patterns, direction, offset and the substitute command's remembered state are global.
 */
final class Search {
    /** The last search's offset (Vim's soffset): a line offset, or a character offset from the match's start or end. */
    record Offset(boolean line, boolean end, long off) {
        static final Offset NONE = new Offset(false, false, 0);
    }

    /** A match: its start, and the position after its end. */
    record Match(Pos start, Pos end) { }

    /** Vim's RE_SEARCH and RE_SUBST. */
    static final int SEARCH = 0, SUBSTITUTE = 1;
    /** The last search and substitute patterns (spats), and which one was used last (last_idx). */
    private final String[] patterns = new String[2];
    private int lastIdx;
    /** The pattern the last search or substitute used, for messages (mr_pattern). */
    private String used;
    private char direction = '/';
    /** The last substitute string, for {@code :s} without a pattern (ex_cmds.c's old_sub), or null. */
    String oldSub;
    /** The last substitute string after {@code ~} expansion, for {@code ~} (regexp.c's reg_prev_sub), or null. */
    String prevSub;
    /** The flags {@code :s} keeps for {@code :&&} (ex_cmds.c's static subflags). */
    boolean subAll, subAsk, subCount, subError = true, subPrint, subList, subNumber;
    char subIgnoreCase;
    private Offset offset = Offset.NONE;
    private boolean noHighlight;
    private final List<String> history = new ArrayList<>();

    /** @return the last used pattern, search or substitute (last_search_pat), or null */
    String pattern() { return patterns[lastIdx]; }

    /** @return the last search pattern ({@code /} register), or null */
    String searchPattern() { return patterns[SEARCH]; }

    /** @return the pattern the last search or substitute used (get_search_pat) */
    String usedPattern() { return used; }

    /** save_re_pat: a pattern given to a search or substitute becomes the last one of its kind, and the last used. */
    void save(int idx, String pat) {
        patterns[idx] = pat;
        lastIdx = idx;
        noHighlight = false;
    }

    /**
     * search_regcomp's choice of pattern: an empty one means the last pattern of kind {@code use} (-1 for the last
     * used), which fails when there is none; a new one is remembered as {@code saveAs} (-1 for both kinds).
     *
     * @return the pattern to compile
     */
    String choose(String pat, int saveAs, int use) {
        if (pat == null || pat.isEmpty()) {
            int i = use < 0 ? lastIdx : use;
            if (patterns[i] == null)
                throw new VimFailure(use == SUBSTITUTE ? "E33: No previous substitute regular expression" : "E35: No previous regular expression");
            pat = patterns[i];
            if (saveAs >= 0 && saveAs != i) save(saveAs, pat);
            else if (saveAs < 0) {
                if (i != SEARCH) save(SEARCH, pat);
                if (i != SUBSTITUTE) save(SUBSTITUTE, pat);
            }
        } else {
            remember(pat);
            if (saveAs == SEARCH || saveAs < 0) save(SEARCH, pat);
            if (saveAs == SUBSTITUTE || saveAs < 0) save(SUBSTITUTE, pat);
        }
        used = pat;
        return pat;
    }

    /** @return {@code /} or {@code ?} */
    char direction() { return direction; }

    /** @return whether {@code :noh} hid the highlight until the next search */
    boolean highlightHidden() { return noHighlight; }

    void hideHighlight() { noHighlight = true; }

    /** @return the search history, oldest first */
    List<String> history() { return history; }

    void remember(String line) {
        if (line.isEmpty()) return;
        history.remove(line);
        history.add(line);
        if (history.size() > 50) history.removeFirst();
    }

    static void register(VimCommands commands) {
        commands.motionWithLine("/", "search forward for a pattern, with an offset", c -> c.editor.vim().search().command(c, '/', c.line));
        commands.motionWithLine("?", "search backward for a pattern, with an offset", c -> c.editor.vim().search().command(c, '?', c.line));
        commands.motion("n", "repeat the last search", c -> c.editor.vim().search().next(c, false));
        commands.motion("N", "repeat the last search in the other direction", c -> c.editor.vim().search().next(c, true));
        commands.motion("*", "search forward for the keyword under the cursor", c -> c.editor.vim().search().word(c, '*', false));
        commands.motion("#", "search backward for the keyword under the cursor", c -> c.editor.vim().search().word(c, '#', false));
        commands.motion("g*", "search forward for the keyword under the cursor, also inside words", c -> c.editor.vim().search().word(c, '*', true));
        commands.motion("g#", "search backward for the keyword under the cursor, also inside words", c -> c.editor.vim().search().word(c, '#', true));
    }

    /** nv_search: the typed line is a pattern, perhaps with an offset and {@code ;} searches after it. */
    Target command(Context c, char dir, String line) {
        remember(line);
        return normal(c, dir, line, true, false, false);
    }

    /** nv_next: n and N; if an offset leaves the cursor where it was, the search is repeated once more. */
    Target next(Context c, boolean reverse) {
        Pos old = c.cursor();
        Target target = normal(c, (char) 0, null, true, reverse, false);
        if (target != null && target.pos().equals(old) && !lastWrapped) {
            Context again = copy(c);
            again.count = c.count + 1;
            Target retry = normal(again, (char) 0, null, true, reverse, false);
            if (retry != null) return retry;
        }
        return target;
    }

    private static Context copy(Context c) {
        Context copy = new Context(c.editor);
        copy.count = c.count;
        copy.hasCount = c.hasCount;
        copy.operator = c.operator;
        copy.visual = c.visual;
        return copy;
    }

    /** nv_ident for * # g* g#: the keyword (or other text) under or after the cursor, as a whole word unless g. */
    Target word(Context c, char command, boolean g) {
        Buffer b = c.buffer;
        Pos p = c.cursor();
        String line = b.line(p.line);
        Keywords keywords = c.options.keywords();
        int[] found = identifier(line, p.col, keywords);
        if (found == null) throw new VimFailure("E348: No string under cursor");
        int start = found[0], end = found[1];
        String text = line.substring(start, end);
        StringBuilder pat = new StringBuilder();
        if (!g && keywords.isWord(text.codePointAt(0))) pat.append("\\<");
        String special = command == '#' ? "/?.*~[^$\\" : "/.*~[^$\\";
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (special.indexOf(ch) >= 0) pat.append('\\');
            pat.append(ch);
        }
        if (!g && keywords.isWord(text.codePointBefore(text.length()))) pat.append("\\>");
        remember(pat.toString());
        // The previous context is set first, so `` returns here; then the cursor goes to the word's start, so the
        // search skips the word.
        Context from = copy(c);
        c.editor.marks().setpcmark(p);
        c.editor.setCursor(new Pos(p.line, start), true);
        try {
            Target target = normal(from, command == '*' ? '/' : '?', pat.toString(), false, false, true);
            if (target == null) c.editor.setCursor(p, true);
            return target;
        } catch (RuntimeException failure) {
            c.editor.setCursor(p, true);
            throw failure;
        }
    }

    /**
     * find_ident_at_pos with FIND_IDENT|FIND_STRING.
     *
     * @return the start and end columns of the keyword, or other non-blank text, under or after {@code col}; or null
     */
    static int[] identifier(String line, int startcol, Keywords k) {
        int col = 0, thisClass = 0, i;
        for (i = 0; i < 2; i++) {
            col = startcol;
            while (col < line.length()) {
                thisClass = cls(k, line.codePointAt(col));
                if (thisClass != 0 && (i == 1 || thisClass != 1)) break;
                col += Character.charCount(line.codePointAt(col));
            }
            thisClass = col < line.length() ? cls(k, line.codePointAt(col)) : 0;
            while (col > 0 && thisClass != 0) {
                int prevcol = line.offsetByCodePoints(col, -1);
                int prevClass = cls(k, line.codePointAt(prevcol));
                // FIND_IDENT is set, so any change of class ends the text.
                if (thisClass != prevClass) break;
                col = prevcol;
            }
            if (thisClass > 2) thisClass = 2;
            if (thisClass == 2) break;
        }
        if (col >= line.length() || i == 0 && thisClass != 2) return null;
        int cls = cls(k, line.codePointAt(col));
        int end = col;
        while (end < line.length() && (i == 0 ? cls(k, line.codePointAt(end)) == cls : cls(k, line.codePointAt(end)) != 0))
            end += Character.charCount(line.codePointAt(end));
        return new int[] { col, end };
    }

    private static int cls(Keywords k, int c) { return k.charClass(c); }

    private boolean lastWrapped;

    /**
     * normal_search and do_search.
     *
     * @param dir {@code /} or {@code ?}, or 0 to use the last direction
     * @param pat the typed line (pattern, offset, {@code ;}…), or null to use the last pattern and offset
     * @param mark whether the search sets the previous-context mark (the jumplist)
     * @param reverse N: the other direction
     * @param noSmartcase for * and #, which ignore 'smartcase'
     * @return where the cursor goes, or null
     */
    Target normal(Context c, char dir, String pat, boolean mark, boolean reverse, boolean noSmartcase) {
        Buffer b = c.buffer;
        Pos pos = c.cursor();
        lastWrapped = false;
        int[] fold = c.editor.folds().closed(pos.line + 1);
        if (dir == 0) dir = direction;
        else direction = dir;
        if (reverse) dir = dir == '/' ? '?' : '/';
        // do_search: from inside a closed fold, a search starts after its end (forward) or at its start (backward).
        if (fold != null) pos = dir == '/' ? new Pos(fold[1] - 1, Integer.MAX_VALUE / 2) : new Pos(fold[0] - 1, 0);
        noHighlight = false;
        boolean end = false;
        int retval;
        String rest = pat;
        while (true) {
            String searchstr = rest;
            if (rest == null || rest.isEmpty() || rest.charAt(0) == dir) {
                // do_search: with no search pattern yet, the substitute pattern is searched for.
                if (patterns[SEARCH] == null) {
                    if (patterns[SUBSTITUTE] == null) throw new VimFailure("E35: No previous regular expression");
                    searchstr = patterns[SUBSTITUTE];
                } else searchstr = "";
            }
            String after = null;
            if (rest != null && !rest.isEmpty()) {
                int stop = skipPattern(rest, dir);
                searchstr = rest.substring(0, stop);
                String p = stop < rest.length() ? rest.substring(stop + 1) : "";
                boolean line = false, endOff = false;
                long off = 0;
                int i = 0;
                if (i < p.length() && (p.charAt(i) == '+' || p.charAt(i) == '-' || Character.isDigit(p.charAt(i)))) line = true;
                else if (i < p.length() && "esb".indexOf(p.charAt(i)) >= 0) {
                    endOff = p.charAt(i) == 'e';
                    i++;
                }
                if (i < p.length() && (Character.isDigit(p.charAt(i)) || p.charAt(i) == '+' || p.charAt(i) == '-')) {
                    int j = i + 1;
                    while (j < p.length() && Character.isDigit(p.charAt(j))) j++;
                    String number = p.substring(i, j);
                    if (number.equals("+")) off = 1;
                    else if (number.equals("-")) off = -1;
                    else off = Long.parseLong(number.startsWith("+") ? number.substring(1) : number);
                    i = j;
                }
                offset = new Offset(line, endOff, off);
                after = p.substring(i);
            }
            String pattern = searchstr.isEmpty() ? patterns[lastIdx] : searchstr;
            if (!searchstr.isEmpty()) save(SEARCH, searchstr);
            used = pattern;
            end = offset.end();
            // A character offset is taken off the start position, so ?pat?e+2 and /pat/s-2 do not get stuck.
            if (!offset.line() && offset.off() != 0) {
                long n = offset.off();
                if (n > 0) {
                    for (; n > 0; n--) if (!decl(b, pos)) break;
                    if (n > 0) pos = new Pos(-1, Integer.MAX_VALUE);
                } else {
                    for (; n < 0; n++) if (!incl(b, pos)) break;
                    if (n < 0) pos = new Pos(b.count(), 0);
                }
            }
            // do_search echoes the search ("/pattern/e ") before it searches; a warning or error then replaces it.
            c.editor.message(echo(dir) + " ", false);
            VimRegex re = compile(c, pattern, noSmartcase);
            Pos found = searchit(c, pos, dir == '/', re, c.count, end, pattern);
            if (found == null) return null;
            pos = found;
            retval = 1;
            if (offset.line()) {
                long line = pos.line + offset.off();
                pos = new Pos((int) Math.max(0, Math.min(b.count() - 1, line)), 0);
                retval = 2;
            } else {
                long n = offset.off();
                if (n > 0) {
                    for (; n > 0; n--) if (!incl(b, pos)) break;
                } else for (; n < 0; n++) if (!decl(b, pos)) break;
            }
            if (after == null || !after.startsWith(";")) break;
            if (after.length() < 2 || after.charAt(1) != '/' && after.charAt(1) != '?')
                throw new VimFailure("E386: Expected '?' or '/' after ';'");
            dir = after.charAt(1);
            rest = after.substring(2);
        }
        // check_cursor: in Normal mode and with an operator the cursor stays on a character.
        String text = b.line(pos.line);
        if (!c.inVisual() && pos.col >= text.length() && !text.isEmpty()) pos = new Pos(pos.line, Text.last(text));
        Target.Kind kind = retval == 2 ? Target.Kind.LINEWISE : end ? Target.Kind.INCLUSIVE : Target.Kind.EXCLUSIVE;
        Target target = new Target(pos, kind, Target.Column.RESET, mark, true, true);
        return target;
    }

    /**
     * An Ex address {@code /pat/} or {@code ?pat?} (get_address calling do_search without SEARCH_OPT): the pattern,
     * and a line offset after it, become the last search, and the search starts after the end of line {@code from}
     * going forward, or at its start going back, so it never matches in that line. Line 0 is before the first line.
     *
     * @param text the text after the {@code /} or {@code ?}
     * @return the line found (1-based) and how many characters of {@code text} the pattern and offset took
     */
    int[] address(Context c, char dir, String text, int from) {
        direction = dir;
        int stop = skipPattern(text, dir);
        String pat = text.substring(0, stop);
        int p = stop < text.length() ? stop + 1 : stop;
        boolean lineOffset = false;
        long off = 0;
        if (p < text.length() && (text.charAt(p) == '+' || text.charAt(p) == '-' || Character.isDigit(text.charAt(p)))) {
            lineOffset = true;
            char sign = text.charAt(p);
            if (Character.isDigit(sign) || p + 1 < text.length() && Character.isDigit(text.charAt(p + 1))) {
                int q = sign == '+' || sign == '-' ? p + 1 : p, e = q;
                while (e < text.length() && Character.isDigit(text.charAt(e))) e++;
                off = Long.parseLong(text.substring(q, Math.min(e, q + 18))) * (sign == '-' ? -1 : 1);
                p = e;
            } else {
                off = sign == '-' ? -1 : 1;
                p++;
            }
        }
        offset = new Offset(lineOffset, false, off);
        String pattern = choose(pat, SEARCH, -1);
        Buffer b = c.buffer;
        Pos start = from <= 0 ? new Pos(-1, 0) : new Pos(from - 1, dir == '/' ? Integer.MAX_VALUE : 0);
        lastWrapped = false;
        Pos found = searchit(c, start, dir == '/', compile(c, pattern, false), 1, false, pattern);
        int lnum = found.line + 1;
        if (lineOffset) lnum = (int) Math.max(1, Math.min(b.count(), lnum + off));
        return new int[] { lnum, p };
    }

    /**
     * {@code \/}, {@code \?} and {@code \&} as an Ex address: the next line after {@code from} (or before it)
     * matching the last search pattern, or for {@code \&} the last substitute pattern.
     *
     * @return the line found (1-based)
     */
    int repeatAddress(Context c, char which, int from) {
        String pattern = choose("", SEARCH, which == '&' ? SUBSTITUTE : SEARCH);
        Pos start = new Pos(Math.max(0, from) - 1, which != '?' ? Integer.MAX_VALUE : 0);
        if (from <= 0) start = new Pos(-1, 0);
        Pos found = searchit(c, start, which != '?', compile(c, pattern, false), 1, false, pattern);
        return found.line + 1;
    }

    /** @return the search as do_search echoes it: the pattern and any offset */
    private String echo(char dir) {
        StringBuilder out = new StringBuilder().append(dir).append(used);
        if (offset.line() || offset.end() || offset.off() != 0) {
            out.append(dir);
            if (offset.end()) out.append('e');
            else if (!offset.line()) out.append('s');
            if (offset.off() != 0 || offset.line()) out.append(offset.off() >= 0 ? "+" : "").append(offset.off());
        }
        return out.toString();
    }

    /** skip_regexp: the end of a pattern typed after {@code /} or {@code ?}, skipping brackets and backslashes. */
    static int skipPattern(String s, char delim) {
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == delim) return i;
            if (ch == '[') {
                int close = s.indexOf(']', i + 2);
                if (close > 0) {
                    i = close + 1;
                    continue;
                }
            }
            if (ch == '\\' && i + 1 < s.length()) i++;
            i++;
        }
        return s.length();
    }

    VimRegex compile(Context c, String pat, boolean noSmartcase) {
        Options o = c.options;
        try {
            return VimRegex.compile(pat, new VimRegex.Settings(o.flag("ignorecase"), o.flag("smartcase") && !noSmartcase,
                o.keywords().javaClass(), prevSub));
        } catch (IllegalArgumentException bad) {
            throw new VimFailure(bad.getMessage());
        }
    }

    /**
     * searchit: the {@code count}-th match from {@code start}, forward or backward, wrapping with 'wrapscan'.
     *
     * @param atEnd the 'e' offset: the match's last character counts, and the result is it
     * @return the match position (its start, or its last character with {@code atEnd}), or null with a message
     */
    Pos searchit(Context c, Pos start, boolean forward, VimRegex re, int count, boolean atEnd, String shown) {
        Buffer b = c.buffer;
        String text = b.text(0, b.length());
        Matcher m = re.pattern().matcher(text);
        Pos pos = start.copy();
        boolean wrapscan = c.options.flag("wrapscan");
        int lastLine = b.count() - 1;
        boolean found = false;
        int lnum = 0;
        do {
            int startCharLen;
            if (pos.col == Integer.MAX_VALUE) startCharLen = 0;
            else if (pos.line >= 0 && pos.line <= lastLine) {
                String l = b.line(pos.line);
                startCharLen = pos.col >= l.length() ? 1 : Character.charCount(l.codePointAt(pos.col));
            } else startCharLen = 1;
            int extraCol = forward ? startCharLen : 0;
            Pos startPos = pos.copy();
            found = false;
            boolean atFirstLine = true;
            if (pos.line < 0) {
                pos = new Pos(0, 0);
                atFirstLine = false;
            }
            if (!forward && startPos.col == 0) {
                lnum = pos.line - 1;
                atFirstLine = false;
            } else lnum = pos.line;
            if (pos.line > lastLine) atFirstLine = false;
            for (int loop = 0; loop <= 1; loop++) {
                for (; lnum >= 0 && lnum <= lastLine; lnum += forward ? 1 : -1, atFirstLine = false) {
                    Match match = forward ? forwardIn(b, m, re, lnum, atFirstLine, startPos, extraCol, atEnd)
                        : backwardIn(b, m, re, lnum, loop > 0, startPos, extraCol, atEnd);
                    if (match != null) {
                        if (atEnd && !match.start().equals(match.end())) {
                            Pos e = match.end().copy();
                            if (e.col == 0) {
                                if (e.line > 0) {
                                    e.line--;
                                    e.col = b.line(e.line).length();
                                }
                            } else e.col = Text.previous(b.line(e.line), e.col);
                            pos = e;
                        } else pos = match.start().copy();
                        found = true;
                        break;
                    }
                    if (loop > 0 && lnum == startPos.line) break;
                }
                if (!wrapscan || found || loop > 0) break;
                lnum = forward ? 0 : lastLine;
                lastWrapped = true;
                c.editor.message(forward ? "search hit BOTTOM, continuing at TOP" : "search hit TOP, continuing at BOTTOM", true);
            }
        } while (--count > 0 && found);
        if (!found) {
            if (wrapscan) throw new VimFailure("E486: Pattern not found: " + shown);
            if (lnum < 0) throw new VimFailure("E384: Search hit TOP without match for: " + shown);
            throw new VimFailure("E385: Search hit BOTTOM without match for: " + shown);
        }
        if (pos.line > lastLine) {
            String l = b.line(lastLine);
            pos = new Pos(lastLine, l.isEmpty() ? 0 : Text.last(l));
        }
        return pos;
    }

    /** The first match attempted at or after {@code col} of line {@code lnum}; null when no attempt there matches. */
    private static Matcher attempt(Buffer b, Matcher m, int lnum, int col) {
        int from = b.offset(lnum, col), lineEnd = b.end(lnum);
        if (from > b.length()) return null;
        if (!m.find(from) || m.start() > lineEnd) return null;
        // The text's final line break ends the last line; nothing starts after it.
        if (m.start() >= b.length() && b.length() > 0) return null;
        return m;
    }

    private static Match match(Buffer b, VimRegex re, Matcher m) {
        int s = Math.min(re.start(m), b.length()), e = Math.min(re.end(m), b.length());
        return new Match(position(b, s), position(b, Math.max(s, e)));
    }

    /** An offset as a position; the offset after the final line break is the end of the last line. */
    private static Pos position(Buffer b, int offset) {
        if (offset >= b.length() && b.length() > 0) {
            int last = b.count() - 1;
            return new Pos(last, b.line(last).length());
        }
        return b.pos(offset);
    }

    private static Match forwardIn(Buffer b, Matcher m, VimRegex re, int lnum, boolean atFirstLine, Pos startPos, int extraCol, boolean atEnd) {
        Matcher found = attempt(b, m, lnum, 0);
        if (found == null) return null;
        Match match = match(b, re, found);
        if (!atFirstLine) return match;
        boolean firstMatch = true;
        while (match.start().line == lnum && (atEnd && firstMatch
            ? endCol(b, match) - 1 < startPos.col + extraCol
            : match.start().col - (match.start().col >= b.line(lnum).length() ? 1 : 0) < startPos.col + extraCol)) {
            // Try again one character after where this attempt started.
            int matchcol = found.start() - b.start(lnum);
            String line = b.line(lnum);
            if (matchcol < line.length()) matchcol += Character.charCount(line.codePointAt(matchcol));
            else return null;
            found = attempt(b, m, lnum, matchcol);
            if (found == null) return null;
            match = match(b, re, found);
        }
        return match;
    }

    /** The end column of a match on its start line, or a large value when it ends on a later line. */
    private static int endCol(Buffer b, Match match) {
        return match.end().line == match.start().line ? match.end().col : Integer.MAX_VALUE / 2;
    }

    private static Match backwardIn(Buffer b, Matcher m, VimRegex re, int lnum, boolean loop, Pos startPos, int extraCol, boolean atEnd) {
        Matcher found = attempt(b, m, lnum, 0);
        if (found == null) return null;
        Match best = null;
        Match match = match(b, re, found);
        String line = b.line(lnum);
        while (true) {
            boolean ok;
            if (loop) ok = true;
            else if (atEnd) ok = match.end().line < startPos.line || match.end().line == startPos.line && match.end().col - 1 < startPos.col + extraCol;
            else ok = match.start().line < startPos.line || match.start().line == startPos.line && match.start().col < startPos.col + extraCol;
            if (!ok) break;
            best = match;
            if (match.start().line != lnum) break;
            int matchcol = match.start().col;
            if (matchcol < line.length()) matchcol += Character.charCount(line.codePointAt(matchcol));
            else break;
            found = attempt(b, m, lnum, matchcol);
            if (found == null) break;
            match = match(b, re, found);
        }
        return best;
    }

    /** incl: one character forward, onto the next line's start after the last; false at the end of the text. */
    static boolean incl(Buffer b, Pos p) {
        String line = b.line(p.line);
        if (p.col < line.length()) {
            p.col += Character.charCount(line.codePointAt(p.col));
            if (p.col < line.length()) return true;
        }
        if (p.line < b.count() - 1) {
            p.line++;
            p.col = 0;
            return true;
        }
        return false;
    }

    /** decl: one character back, onto the previous line's end; false at the start of the text. */
    static boolean decl(Buffer b, Pos p) {
        if (p.col == Integer.MAX_VALUE) p.col = b.line(p.line).length();
        if (p.col > 0) {
            String line = b.line(p.line);
            p.col = Math.min(p.col, line.length());
            if (p.col > 0) {
                p.col = Text.previous(line, p.col);
                return true;
            }
        }
        if (p.line > 0) {
            p.line--;
            p.col = b.line(p.line).length();
            return true;
        }
        return false;
    }

    /** @return every match in the text, for 'hlsearch'; empty when there is no pattern or it is invalid */
    List<Match> all(VimEditor editor) {
        String pattern = pattern();
        if (pattern == null || noHighlight) return List.of();
        VimRegex re = quietly(editor, pattern);
        return re == null ? List.of() : matches(editor.buffer(), re);
    }

    /**
     * 'incsearch': the match a search for the typed line would find from the cursor, without moving it, changing
     * the last pattern or giving messages.
     *
     * @return the match, or null
     */
    Match preview(VimEditor editor, char type, String line) {
        String pat = line.substring(0, skipPattern(line, type));
        if (pat.isEmpty()) pat = pattern();
        if (pat == null) return null;
        VimRegex re = quietly(editor, pat);
        if (re == null) return null;
        List<Match> found = matches(editor.buffer(), re);
        if (found.isEmpty()) return null;
        Pos cursor = editor.cursor();
        boolean wrap = editor.options().flag("wrapscan");
        if (type == '/') {
            for (Match m : found) if (m.start().compareTo(cursor) > 0) return m;
            return wrap ? found.getFirst() : null;
        }
        for (int i = found.size() - 1; i >= 0; i--) if (found.get(i).start().compareTo(cursor) < 0) return found.get(i);
        return wrap ? found.getLast() : null;
    }

    private static VimRegex quietly(VimEditor editor, String pat) {
        try {
            Options o = editor.options();
            return VimRegex.compile(pat, new VimRegex.Settings(o.flag("ignorecase"), o.flag("smartcase"), o.keywords().javaClass(), editor.vim().search().prevSub));
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }

    private static List<Match> matches(Buffer b, VimRegex re) {
        List<Match> out = new ArrayList<>();
        Matcher m = re.pattern().matcher(b.text(0, b.length()));
        int from = 0;
        while (from <= b.length() && m.find(from) && out.size() < 10_000) {
            if (m.start() >= b.length() && b.length() > 0) break;
            out.add(match(b, re, m));
            from = m.end() > m.start() ? m.end() : m.start() + 1;
        }
        return out;
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Registers.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Vim's registers (spec 3.5), with Vim's rules for which registers a yank or delete writes (ops.c: op_yank and
 * op_delete) and what the unnamed register points at. {@code "+} and {@code "*} are the host's clipboard.
 */
public final class Registers {
    /** How a register's text is put: within a line, as whole lines, or as a block of columns. */
    public enum Type { CHAR, LINE, BLOCK }

    /**
     * A register's text as Vim holds it: lines, and how they are put.
     *
     * @param lines at least one line; a character register's lines are joined by line breaks
     * @param type charwise, linewise or blockwise
     * @param width for a block, its width in display columns minus one (Vim's y_width); 0 otherwise
     */
    public record Content(List<String> lines, Type type, int width) {
        /** Copies the lines. */
        public Content {
            lines = List.copyOf(lines);
            if (lines.isEmpty()) throw new IllegalArgumentException("A register holds at least one line");
        }

        /**
         * @param lines at least one line
         * @param type charwise or linewise
         */
        public Content(List<String> lines, Type type) { this(lines, type, 0); }

        /**
         * @param text register text; a linewise register's text ends with a line break, which is dropped
         * @param type the type
         * @return the content
         */
        public static Content of(String text, Type type) {
            String body = type == Type.LINE && text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
            return new Content(List.of(body.split("\n", -1)), type);
        }

        /** @return the text as {@code getreg()} gives it: linewise text ends with a line break, a block's lines are joined */
        public String text() {
            String joined = String.join("\n", lines);
            return type == Type.LINE ? joined + "\n" : joined;
        }
    }

    private final Map<Character, Content> values = new HashMap<>();
    private final VimHost host;
    private char previous;
    private String lastInserted = "";
    private String lastCommand = "";
    private Content clipboardWritten;

    /** @param host reaches the clipboard */
    public Registers(VimHost host) { this.host = host; }

    /**
     * @param name a register name, or {@code "} for the unnamed register
     * @return its content, or empty for an empty or unknown register
     */
    public Optional<Content> get(char name) {
        char register = Character.toLowerCase(name);
        return switch (register) {
            case '"' -> previous == 0 ? Optional.empty() : get(previous);
            case '*', '+' -> clipboard(register);
            case '.' -> lastInserted.isEmpty() ? Optional.empty() : Optional.of(Content.of(lastInserted, Type.CHAR));
            case ':' -> lastCommand.isEmpty() ? Optional.empty() : Optional.of(Content.of(lastCommand, Type.CHAR));
            case '_' -> Optional.empty();
            default -> Optional.ofNullable(values.get(register));
        };
    }

    /**
     * Sets a register directly, as {@code setreg()} or {@code :let @a = …}: the unnamed register keeps pointing where
     * it did, unless the register set is the unnamed one ({@code "0} then holds the text).
     */
    public void set(char name, Content content) {
        write(name, content);
        if (name == '"') previous = '0';
    }

    /** @return the register a name reads: the unnamed register's target for {@code "}, else the name in lower case */
    char resolve(char name) {
        char register = Character.toLowerCase(name);
        return register == '"' ? (previous == 0 ? '0' : previous) : register;
    }

    /**
     * Puts a register's earlier content back without moving the unnamed register (Visual {@code p}, which must not
     * put the text it just deleted). A null content empties the register.
     */
    void restore(char name, Content content) {
        char register = Character.toLowerCase(name);
        if (content == null) values.remove(register);
        else if (register == '*' || register == '+') {
            clipboardWritten = content;
            host.writeClipboard().accept(register, content.text());
        } else values.put(register, content);
    }

    /** @return whether the name is a register a command may name */
    static boolean valid(char name, boolean writing) {
        if (Character.isLetterOrDigit(name) && name < 128 || "\"-_*+".indexOf(name) >= 0) return true;
        return !writing && (name == '.' || name == ':');
    }

    /**
     * A yank (op_yank): into the named register, or into {@code "0} when none is named.
     *
     * @param requested the register typed ({@code 0} for none)
     * @param content what was yanked
     * @param clipboard the 'clipboard' option
     */
    void yank(char requested, Content content, String clipboard) {
        char register = effective(requested, clipboard);
        if (register == '_') return;
        if (register == 0 || register == '"') {
            values.put('0', content);
            previous = '0';
            return;
        }
        write(register, content);
        previous = Character.toLowerCase(register);
    }

    /**
     * A delete or change (op_delete): the named register if any; register 1 (shifting 1 to 9) for a delete of a
     * line or more or with {@code regOne}; the small delete register {@code "-} for a delete within one line with no
     * register named.
     *
     * @param requested the register typed ({@code 0} for none)
     * @param content what was deleted
     * @param lineCount how many lines the deleted text touched
     * @param regOne the motion always uses register 1 ({@code % ( ) ` / ? n N { }})
     * @param clipboard the 'clipboard' option
     */
    void delete(char requested, Content content, int lineCount, boolean regOne, String clipboard) {
        char register = effective(requested, clipboard);
        if (register == '_') return;
        boolean named = register != 0 && register != '"';
        if (named) {
            write(register, content);
            previous = Character.toLowerCase(register);
        }
        // Vim 9 also puts a delete into a named register in register 1 (recorded by the corpus).
        if (content.type() == Type.LINE || lineCount > 1 || regOne || named && !clipboardRegister(register)) {
            for (char r = '9'; r > '1'; r--) {
                Content shifted = values.get((char) (r - 1));
                if (shifted == null) values.remove(r);
                else values.put(r, shifted);
            }
            values.put('1', content);
            previous = '1';
        }
        List<String> items = List.of(clipboard.split(","));
        boolean clipboardDefault = register == '*' && items.contains("unnamed") || register == '+' && items.contains("unnamedplus");
        if ((!named || clipboardDefault) && content.type() != Type.LINE && lineCount == 1) {
            values.put('-', content);
            previous = '-';
        }
    }

    private static boolean clipboardRegister(char register) { return register == '*' || register == '+'; }

    /** @param text the text of the last insert, for {@code ".} and {@code i_CTRL-A} */
    void lastInserted(String text) { lastInserted = text; }

    /** The {@code :} register: the last command line run. */
    void lastCommand(String text) { lastCommand = text; }

    String lastInserted() { return lastInserted; }

    /** With 'clipboard' holding unnamed or unnamedplus, a command naming no register uses the clipboard register. */
    static char effective(char requested, String clipboard) {
        if (requested != 0 && requested != '"') return requested;
        List<String> items = List.of(clipboard.split(","));
        if (items.contains("unnamedplus")) return '+';
        if (items.contains("unnamed")) return '*';
        return requested;
    }

    private void write(char name, Content content) {
        char register = Character.toLowerCase(name);
        if (register == '*' || register == '+') {
            clipboardWritten = content;
            host.writeClipboard().accept(register, content.text());
            return;
        }
        if (register == '"') register = '0';
        Content old = values.get(register);
        if (!Character.isUpperCase(name) || old == null) {
            values.put(register, content);
            return;
        }
        // Appending (op_yank): linewise text makes the register linewise; otherwise it keeps its type, and a
        // charwise register joins its last line with the first appended one.
        Type type = content.type() == Type.LINE ? Type.LINE : old.type();
        List<String> lines = new ArrayList<>(old.lines());
        if (type == Type.CHAR) {
            lines.set(lines.size() - 1, lines.getLast() + content.lines().getFirst());
            lines.addAll(content.lines().subList(1, content.lines().size()));
        } else lines.addAll(content.lines());
        values.put(register, new Content(lines, type, old.width()));
    }

    private Optional<Content> clipboard(char register) {
        Optional<String> read = host.readClipboard().apply(register);
        if (read.isEmpty()) return Optional.empty();
        // Windows (and old Mac) text arrives with \r\n or \r; Vim's registers hold \n only.
        String text = read.get().replace("\r\n", "\n").replace('\r', '\n');
        if (clipboardWritten != null && clipboardWritten.text().equals(text)) return Optional.of(clipboardWritten);
        return Optional.of(Content.of(text, text.endsWith("\n") ? Type.LINE : Type.CHAR));
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java` with:

```java
package dev.jasper.editor.vim;

import java.util.List;
import java.util.function.Consumer;

/**
 * Vim's global state, one per plugin (spec 1.1): options' global values, registers, the command table, the handler
 * table, the last {@code f}/{@code t} search and the last change for {@code .}.
 */
public final class Vim {
    /** The last character search, for {@code ;} and {@code ,}. */
    record Find(boolean forward, boolean till, int character) { }

    /**
     * The last change, for {@code .}: its keys without counts or register.
     *
     * @param register the register typed, or 0
     * @param count the count typed (the product of both counts), or 0 for none
     * @param keys the keys, including any text typed in Insert mode and its {@code <Esc>}
     * @param visual for a Visual-mode change, the size of its area (Vim's redo_VIsual); null otherwise
     */
    record Change(char register, int count, List<KeyInput> keys, Visual.Redo visual) {
        Change(char register, int count, List<KeyInput> keys) { this(register, count, keys, null); }
    }

    private final Options options = new Options();
    private final Registers registers;
    private final KeyHandlers handlers = new KeyHandlers();
    private final VimCommands commands = VimCommands.standard();
    private Consumer<String> coverage = id -> { };
    private Find lastFind;
    private Change lastChange;
    private final Search search = new Search();
    /** The command-line history ({@code :}), oldest first. */
    private final java.util.List<String> commandHistory = new java.util.ArrayList<>();
    /** Whether the last hex number CTRL-A changed had upper-case letters (do_addsub's static hexupper). */
    private boolean hexUpper;
    /** The uppercase marks: each remembers its editor (Vim's namedfm). */
    private final java.util.Map<Character, GlobalMark> globalMarks = new java.util.HashMap<>();

    /** An uppercase mark: its editor and where it is. */
    record GlobalMark(VimEditor editor, Pos pos) { }

    /** @param host what the engine needs from the Editor */
    public Vim(VimHost host) { registers = new Registers(host); }

    /** @return the global options */
    public Options options() { return options; }

    /** @return the registers */
    public Registers registers() { return registers; }

    /** @return the handler table */
    public KeyHandlers handlers() { return handlers; }

    /** @return the command table */
    public VimCommands commands() { return commands; }

    /** @param coverage told the id of every command that runs; the corpus coverage test listens */
    public void coverage(Consumer<String> coverage) { this.coverage = coverage; }

    void covered(String id) { coverage.accept(id); }

    Find lastFind() { return lastFind; }

    void lastFind(Find find) { lastFind = find; }

    Change lastChange() { return lastChange; }

    boolean hexUpper() { return hexUpper; }

    void hexUpper(boolean value) { hexUpper = value; }

    /** Sets an uppercase mark; it moves with that editor's text. */
    void globalMark(char name, VimEditor editor, Pos pos) {
        GlobalMark old = globalMarks.remove(name);
        if (old != null) old.editor().marks().untrack(old.pos());
        Pos tracked = pos.copy();
        editor.marks().track(tracked);
        globalMarks.put(name, new GlobalMark(editor, tracked));
    }

    /** @return an uppercase mark's position when it is in {@code editor}, or null */
    Pos globalMark(char name, VimEditor editor) {
        GlobalMark mark = globalMarks.get(name);
        return mark == null || mark.editor() != editor ? null : mark.pos().copy();
    }

    /** {@code :delmarks}: an uppercase mark goes. */
    void clearGlobalMark(char name) {
        GlobalMark old = globalMarks.remove(name);
        if (old != null) old.editor().marks().untrack(old.pos());
    }

    /** @return the uppercase and digit marks, by name, for {@code :marks} */
    java.util.SortedMap<Character, GlobalMark> globalMarks() { return new java.util.TreeMap<>(globalMarks); }

    /** An editor closed: its uppercase marks go. */
    void forget(VimEditor editor) { globalMarks.values().removeIf(mark -> mark.editor() == editor); }

    /** @return the last search pattern, direction and offset, and the search history */
    Search search() { return search; }

    void lastChange(Change change) { lastChange = change; }

    /** @return the {@code :} history, oldest first */
    java.util.List<String> commandHistory() { return commandHistory; }

    /** A typed command line joins the history (at its end, once), as Vim's add_to_history. */
    void rememberCommand(String line) {
        if (line.isEmpty()) return;
        commandHistory.remove(line);
        commandHistory.add(line);
        if (commandHistory.size() > 50) commandHistory.removeFirst();
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Marks.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * One editor's marks (spec 3.6): {@code a}–{@code z}, the special marks, the Visual area for {@code gv}, the
 * jumplist and the change list. They follow the text as Vim's mark_adjust and mark_col_adjust move them (mark.c):
 * by line, not by character. Adding or removing lines moves the marks below; deleting a line deletes the
 * lowercase, {@code '.} and {@code '^} marks on it, while the others (Vim's one_adjust_nodel) move to the first
 * line after the deletion. Joining and splitting lines move the marks on the joined or split part. Edits inside a
 * line leave columns alone, as in Vim.
 *
 * <p>The table keeps the positions of the text's line breaks so that a removal, which a document event reports only
 * after the text is gone, can still be read as lines and columns.
 */
final class Marks {
    /** Marks a line deletion removes (one_adjust) rather than moves (one_adjust_nodel). */
    private static final String DELETABLE = "abcdefghijklmnopqrstuvwxyz.^\"";

    private final Document document;
    private final Map<Character, Pos> marks = new HashMap<>();
    private final List<Pos> jumps = new ArrayList<>();
    private final List<Pos> changes = new ArrayList<>();
    private final List<Pos> extra = new ArrayList<>();
    /** The lines {@code :global} marked (Vim's ml_setmarked): they move with the text and go when deleted. */
    private final List<Pos> marked = new ArrayList<>();
    private final DocumentListener listener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { inserted(event.getOffset(), event.getLength()); }
        @Override public void removeUpdate(DocumentEvent event) { removed(event.getOffset(), event.getLength()); }
        @Override public void changedUpdate(DocumentEvent event) { }
    };
    /** Offsets of the line breaks, ascending. */
    private int[] breaks = new int[0];
    private int breakCount;
    /** The jumplist index: jumps.size() when not moving through the list (Vim's w_jumplistidx). */
    int jumpIndex;
    /** The change-list index (Vim's w_changelistidx). */
    int changeIndex;
    /** The mode of the last Visual area, for {@code gv}. */
    Mode visualMode;
    /** The remembered column of the last Visual area. */
    int visualCurswant = -1;
    /** The previous-context mark ({@code ''}) and the one before it (Vim's w_pcmark, w_prev_pcmark); null when unset. */
    private Pos pcmark, prevPcmark;
    /** Whether the next change starts a new change-list entry (Vim's b_new_change, set when an undo step begins). */
    private boolean newChange = true;
    /** Set while a join removes a line break, so a blank joined line's marks move rather than go (do_join). */
    private boolean joining;
    /** The jumplist's and change list's size limit (Vim's JUMPLISTSIZE). */
    static final int LIST_SIZE = 100;

    Marks(Document document) {
        this.document = document;
        pcmark = new Pos(0, 0);
        rebuild();
        document.addDocumentListener(listener);
    }

    void close() { document.removeDocumentListener(listener); }

    /**
     * @param name a mark name: {@code a}–{@code z}, or one of {@code [ ] < > ^ . " '}
     * @return the mark, or null when it is not set
     */
    Pos get(char name) {
        Pos pos = marks.get(name);
        return pos == null ? null : pos.copy();
    }

    /** Sets a mark to a copy of the position; null clears it. */
    void set(char name, Pos pos) {
        if (pos == null) marks.remove(name);
        else marks.put(name, pos.copy());
    }

    /** clrallmarks ({@code :delmarks!}): the lowercase marks, {@code '[ '] '^ '.} and the change list go; {@code '"} goes to the start. */
    void clearAll() {
        marks.keySet().removeIf(name -> name >= 'a' && name <= 'z' || "[]^.".indexOf(name) >= 0);
        marks.put('"', new Pos(0, 0));
        changes.clear();
        changeIndex = 0;
    }

    /** Clears every mark and list, after the text was replaced. */
    void clear() {
        marks.clear();
        jumps.clear();
        changes.clear();
        jumpIndex = changeIndex = 0;
        visualMode = null;
        pcmark = new Pos(0, 0);
        prevPcmark = null;
        newChange = true;
        rebuild();
    }

    /** @return the previous-context mark, or null */
    Pos pcmark() { return pcmark == null ? null : pcmark.copy(); }

    /** While {@code :global} runs, the previous context stays where the command started (setpcmark does nothing). */
    boolean frozen;

    /** setpcmark: the cursor becomes the previous context and a new jumplist entry. */
    void setpcmark(Pos cursor) {
        if (frozen) return;
        prevPcmark = pcmark;
        pcmark = cursor.copy();
        jumps.add(cursor.copy());
        if (jumps.size() > LIST_SIZE) jumps.removeFirst();
        jumpIndex = jumps.size();
    }

    /** m' and m`: like setpcmark, but the mark stays even when the cursor does not move. */
    void setpcmarkKeep(Pos cursor) {
        setpcmark(cursor);
        prevPcmark = pcmark.copy();
    }

    /** checkpcmark: after a jump that stayed on the same position, the previous context is kept. */
    void checkpcmark(Pos cursor) {
        if (prevPcmark != null && (pcmark == null || pcmark.equals(cursor))) pcmark = prevPcmark;
        prevPcmark = null;
    }

    /**
     * movemark: {@code count} entries along the jumplist (negative for {@code <C-o>}); the first move from the end
     * adds the cursor. Adjacent entries on the same line are merged first (cleanup_jumplist).
     *
     * @return the entry, or null when the list does not reach that far
     */
    Pos movemark(int count, Pos cursor) {
        cleanupJumps();
        if (jumps.isEmpty()) return null;
        if (jumpIndex + count < 0 || jumpIndex + count >= jumps.size()) return null;
        if (jumpIndex == jumps.size()) {
            setpcmark(cursor);
            jumpIndex--;
            if (jumpIndex + count < 0) return null;
        }
        jumpIndex += count;
        return jumps.get(jumpIndex).copy();
    }

    /** cleanup_jumplist: an entry with a later entry on the same line goes. */
    void cleanupJumps() {
        int to = 0;
        List<Pos> kept = new ArrayList<>();
        for (int from = 0; from < jumps.size(); from++) {
            if (jumpIndex == from) jumpIndex = to;
            boolean duplicate = false;
            for (int i = from + 1; i < jumps.size(); i++) {
                if (jumps.get(i).line == jumps.get(from).line) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                kept.add(jumps.get(from));
                to++;
            }
        }
        if (jumpIndex == jumps.size()) jumpIndex = to;
        jumps.clear();
        jumps.addAll(kept);
    }

    /**
     * movechangelist: {@code count} entries along the change list (negative for {@code g;}), stopping at its ends.
     *
     * @return the entry, or null when already at that end
     */
    Pos movechangelist(int count) {
        if (changes.isEmpty()) return null;
        int n = changeIndex;
        if (n + count < 0) {
            if (n == 0) return null;
            n = 0;
        } else if (n + count >= changes.size()) {
            if (n == changes.size() - 1) return null;
            n = changes.size() - 1;
        } else n += count;
        changeIndex = n;
        return changes.get(n).copy();
    }

    /** Runs a removal of line breaks that joins lines: marks on a joined line move onto the line above. */
    void joining(Runnable removal) {
        joining = true;
        try { removal.run(); }
        finally { joining = false; }
    }

    /** A new undo step began: its first change may start a new change-list entry (u_savecommon's b_new_change). */
    void newChange() { newChange = true; }

    /**
     * changed_common: the {@code '.} mark moves to the change, and the change list gains an entry for the first change
     * of an undo step unless it is on the same line as the last entry and within 79 columns of it.
     */
    private void changed(int line, int col) {
        Pos at = new Pos(line, col);
        marks.put('.', at.copy());
        if (!newChange && !changes.isEmpty()) return;
        boolean add;
        if (changes.isEmpty()) add = true;
        else {
            Pos last = changes.getLast();
            add = last.line != line || last.col + 79 < col || col + 79 < last.col;
        }
        if (add) {
            newChange = false;
            if (changes.size() == LIST_SIZE) changes.removeFirst();
            changes.add(at.copy());
        } else changes.set(changes.size() - 1, at.copy());
        changeIndex = changes.size();
    }

    List<Pos> jumps() { return jumps; }

    List<Pos> changes() { return changes; }

    /**
     * do_move's mark_adjust: the marks on lines {@code first}..{@code last} (0-based) follow the lines to where
     * {@code edit} moves them, the first to {@code target}. The edit deletes and inserts lines, which alone would
     * delete those marks. {@code :global}'s marks stay behind, as Vim's line flags do.
     */
    void moving(int first, int last, int target, Runnable edit) {
        Map<Character, Pos> named = new HashMap<>();
        for (var entry : marks.entrySet())
            if (entry.getKey() != '.' && entry.getValue().line >= first && entry.getValue().line <= last) named.put(entry.getKey(), entry.getValue());
        Map<Pos, Pos> was = new java.util.IdentityHashMap<>();
        for (Pos pos : all()) if (pos.line >= first && pos.line <= last && marked.stream().noneMatch(m -> m == pos)) was.put(pos, pos.copy());
        edit.run();
        for (var entry : was.entrySet()) {
            entry.getKey().line = target + entry.getValue().line - first;
            entry.getKey().col = entry.getValue().col;
        }
        marks.putAll(named);
    }

    /** ml_setmarked: marks lines (0-based) for {@code :global}'s second pass. */
    void markLines(List<Integer> lines) {
        marked.clear();
        for (int line : lines) marked.add(new Pos(line, 0));
    }

    /** ml_firstmarked: the first marked line, which is unmarked; -1 when none is left. */
    int firstMarked() {
        Pos first = null;
        for (Pos pos : marked) if (first == null || pos.line < first.line) first = pos;
        if (first == null) return -1;
        marked.remove(first);
        return first.line;
    }

    /** ml_clearmarked. */
    void clearMarked() { marked.clear(); }

    /** Positions owned elsewhere (uppercase marks in this document) that move with the text too. */
    void track(Pos pos) { extra.add(pos); }

    void untrack(Pos pos) { extra.removeIf(p -> p == pos); }

    private void inserted(int offset, int length) {
        String text;
        try { text = document.getText(offset, length); }
        catch (BadLocationException impossible) { rebuild(); return; }
        int line = lineBefore(offset), col = offset - lineStart(line);
        int newlines = 0, last = -1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') { newlines++; last = i; }
        int lineLength = lineLength(line);
        shiftBreaks(offset, length, text);
        if (newlines > 0) {
            int tail = text.length() - last - 1;
            if (col == 0 && text.endsWith("\n")) adjust(line, Integer.MAX_VALUE, newlines, 0);
            else {
                adjust(line + 1, Integer.MAX_VALUE, newlines, 0);
                for (Pos pos : all()) if (pos.line == line && pos.col >= col) {
                    pos.line += newlines;
                    pos.col = pos.col - col + tail;
                }
            }
        }
        // Lines added after a line's end are a change at the first new line (appended_lines_mark).
        if (newlines > 0 && col == lineLength && col > 0 && text.startsWith("\n")) changed(line + 1, 0);
        else changed(line, col);
    }

    private void removed(int offset, int length) {
        int line1 = lineBefore(offset), col1 = offset - lineStart(line1);
        int line2 = lineBefore(offset + length), col2 = offset + length - lineStart(line2);
        int len1 = lineLength(line1), len2 = lineLength(line2);
        dropBreaks(offset, length);
        int lines = line2 - line1;
        if (lines > 0) {
            if (col1 == 0 && col2 == 0 && !joining) {
                delete(line1, line2 - 1);
                adjust(line2, Integer.MAX_VALUE, -lines, 0);
            } else if (col1 == len1 && col2 == len2 && !joining) {
                delete(line1 + 1, line2);
                adjust(line2 + 1, Integer.MAX_VALUE, -lines, 0);
            } else {
                delete(line1 + 1, line2 - 1);
                // The joined line is gone, and with it its :global mark.
                marked.removeIf(pos -> pos.line == line2);
                for (Pos pos : all()) {
                    if (pos.line == line2) {
                        pos.line = line1;
                        pos.col = Math.max(0, pos.col - col2 + col1);
                    } else if (pos.line > line2) pos.line -= lines;
                }
            }
        }
        // Whole lines deleted after a line's end (the last lines) are a change at the first deleted line (del_lines).
        if (lines > 0 && col1 == len1 && col2 == len2 && col1 > 0 && !joining) changed(line1 + 1, 0);
        else changed(line1, col1);
    }

    /** mark_adjust with an amount: lines {@code from}..{@code to} move by {@code amount}. */
    private void adjust(int from, int to, int amount, int amountAfter) {
        for (Pos pos : all()) {
            if (pos.line >= from && pos.line <= to) pos.line += amount;
            else if (amountAfter != 0 && pos.line > to) pos.line += amountAfter;
        }
    }

    /** mark_adjust with MAXLNUM: marks on deleted lines {@code from}..{@code to} are deleted or moved to {@code from}. */
    private void delete(int from, int to) {
        if (to < from) return;
        marks.entrySet().removeIf(entry -> DELETABLE.indexOf(entry.getKey()) >= 0 && entry.getValue().line >= from && entry.getValue().line <= to);
        marked.removeIf(pos -> pos.line >= from && pos.line <= to);
        for (Pos pos : all()) if (pos.line >= from && pos.line <= to) {
            pos.line = from;
            pos.col = 0;
        }
    }

    private List<Pos> all() {
        List<Pos> all = new ArrayList<>(marks.values());
        if (pcmark != null) all.add(pcmark);
        if (prevPcmark != null) all.add(prevPcmark);
        all.addAll(jumps);
        all.addAll(changes);
        all.addAll(extra);
        all.addAll(marked);
        return all;
    }

    /** @return the line holding {@code offset}, by the break table as it was before the current change */
    private int lineBefore(int offset) {
        int found = Arrays.binarySearch(breaks, 0, breakCount, offset);
        return found >= 0 ? found : -found - 1;
    }

    private int lineStart(int line) { return line == 0 ? 0 : breaks[line - 1] + 1; }

    private int lineLength(int line) {
        int end = line < breakCount ? breaks[line] : documentLengthBefore;
        return end - lineStart(line);
    }

    private int documentLengthBefore;

    private void shiftBreaks(int offset, int length, String text) {
        int at = lineBefore(offset);
        int added = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') added++;
        int[] next = new int[Math.max(16, breakCount + added)];
        System.arraycopy(breaks, 0, next, 0, at);
        int n = at;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') next[n++] = offset + i;
        for (int i = at; i < breakCount; i++) next[n++] = breaks[i] + length;
        breaks = next;
        breakCount = n;
        documentLengthBefore = document.getLength();
    }

    private void dropBreaks(int offset, int length) {
        int n = 0;
        for (int i = 0; i < breakCount; i++) {
            int b = breaks[i];
            if (b < offset) breaks[n++] = b;
            else if (b >= offset + length) breaks[n++] = b - length;
        }
        breakCount = n;
        documentLengthBefore = document.getLength();
    }

    private void rebuild() {
        String text;
        try { text = document.getText(0, document.getLength()); }
        catch (BadLocationException impossible) { text = ""; }
        breaks = new int[Math.max(16, text.length() / 20)];
        breakCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '\n') continue;
            if (breakCount == breaks.length) breaks = Arrays.copyOf(breaks, breaks.length * 2);
            breaks[breakCount++] = i;
        }
        documentLengthBefore = text.length();
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/MarkCommands.java` with:

```java
package dev.jasper.editor.vim;

/**
 * Marks, the jumplist and the change list (spec 3.6), ported from Vim's normal.c (nv_mark, nv_gomark,
 * nv_cursormark, nv_pcmark, {@code gi}) and mark.c (setmark_pos, getmark_buf_fnum, check_mark).
 */
final class MarkCommands {
    private MarkCommands() { }

    static void register(VimCommands commands) {
        commands.actionWithChar("m", "set mark {char} at the cursor", false, MarkCommands::set);
        commands.motionWithChar("'", "to the first non-blank of mark {char}'s line", c -> go(c, true));
        commands.motionWithChar("`", "to mark {char}", c -> go(c, false));
        commands.action("<C-o>", "to an older position in the jumplist", false, c -> jump(c, -c.count));
        // CTRL-I arrives as <Tab>, as in Vim (KeyInput.normalized).
        commands.action("<Tab>", "to a newer position in the jumplist (CTRL-I)", false, c -> jump(c, c.count));
        commands.action("g;", "to an older position in the change list", false, c -> change(c, -c.count));
        commands.action("g,", "to a newer position in the change list", false, c -> change(c, c.count));
        commands.action("gi", "Insert mode where it was last left (the '^ mark)", true, MarkCommands::insertAgain);
        commands.visual("m", "set mark {char} at the cursor", true, false, MarkCommands::set);
    }

    /** nv_mark. */
    static void set(Context c) {
        int name = c.argumentChar();
        if (name < 0 || !setmark(c.editor, (char) name, c.cursor())) throw Context.fail();
    }

    /**
     * setmark_pos with the cursor at {@code pos}, for {@code m} and {@code :mark}.
     *
     * @return false for a name that is not a mark
     */
    static boolean setmark(VimEditor editor, char name, Pos pos) {
        Marks marks = editor.marks();
        if (name == '\'' || name == '`') marks.setpcmarkKeep(pos);
        else if (name >= 'a' && name <= 'z' || name == '[' || name == ']' || name == '"') marks.set(name, pos);
        else if (name == '<' || name == '>') {
            marks.set(name == '<' ? Visual.START : Visual.END, pos);
            if (marks.visualMode == null) marks.visualMode = Mode.VISUAL;
        } else if (name >= 'A' && name <= 'Z' || name >= '0' && name <= '9') editor.vim().globalMark(name, editor, pos);
        else return false;
        return true;
    }

    /**
     * getmark_buf_fnum.
     *
     * @return the mark's position, which may be past the end of its line or the text; null when it is not set
     */
    static Pos get(VimEditor editor, char name) {
        Marks marks = editor.marks();
        return switch (name) {
            case '\'', '`' -> marks.pcmark();
            case '<', '>' -> {
                Pos start = marks.get(Visual.START), end = marks.get(Visual.END);
                Pos pos;
                if (start != null && ((name == '<') == (end == null || start.compareTo(end) < 0))) pos = start;
                else pos = end;
                if (pos != null && marks.visualMode == Mode.VISUAL_LINE) pos = new Pos(pos.line, name == '<' ? 0 : Visual.MAX);
                yield pos;
            }
            default -> {
                if (name >= 'A' && name <= 'Z' || name >= '0' && name <= '9') yield editor.vim().globalMark(name, editor);
                yield marks.get(name);
            }
        };
    }

    /** nv_gomark: ' is linewise to the first non-blank, ` exclusive to the mark itself; both are jumps. */
    static Target go(Context c, boolean linewise) {
        int name = c.argumentChar();
        if (name < 0) throw Context.fail();
        Pos pos = get(c.editor, (char) name);
        pos = check(c.buffer, pos);
        if (linewise) {
            String line = c.buffer.line(pos.line);
            return Target.linewise(new Pos(pos.line, Text.firstNonBlank(line)), Target.Column.RESET).asJump();
        }
        String line = c.buffer.line(pos.line);
        int col = Math.min(pos.col, line.length());
        if (!c.inVisual() && col >= line.length() && !line.isEmpty()) col = Text.last(line);
        return Target.exclusive(new Pos(pos.line, col)).asJump().usingRegisterOne().withoutAdjust();
    }

    /** check_mark: E20 for a mark that is not set, E19 for one past the last line. */
    static Pos check(Buffer b, Pos pos) {
        if (pos == null || pos.line < 0) throw new VimFailure("E20: Mark not set");
        if (pos.line >= b.count()) throw new VimFailure("E19: Mark has invalid line number");
        return pos;
    }

    /** nv_pcmark for CTRL-O and CTRL-I; not with an operator or in Visual mode. */
    static void jump(Context c, int count) {
        if (c.operator != null || c.inVisual()) throw Context.fail();
        Pos pos = c.editor.marks().movemark(count, c.cursor());
        if (pos == null) throw Context.fail();
        moveTo(c, check(c.buffer, pos));
    }

    /** nv_pcmark for g; and g,. */
    static void change(Context c, int count) {
        if (c.operator != null || c.inVisual()) throw Context.fail();
        Marks marks = c.editor.marks();
        Pos pos = marks.movechangelist(count);
        if (pos == null) {
            if (marks.changes().isEmpty()) throw new VimFailure("E664: Changelist is empty");
            throw new VimFailure(count < 0 ? "E662: At start of changelist" : "E663: At end of changelist");
        }
        moveTo(c, check(c.buffer, pos));
    }

    private static void moveTo(Context c, Pos pos) {
        Pos before = c.cursor();
        String line = c.buffer.line(pos.line);
        int col = Math.min(pos.col, line.length());
        if (col >= line.length() && !line.isEmpty()) col = Text.last(line);
        c.editor.setCursor(new Pos(pos.line, col));
        c.editor.forgetColumn();
        c.editor.machine().foldOpenAfter("mark", before);
    }

    /** gi: Insert mode at the {@code '^} mark, or at the cursor when it is not set. */
    static void insertAgain(Context c) {
        Pos last = c.editor.marks().get('^');
        if (last != null) {
            int line = Math.min(last.line, c.buffer.count() - 1);
            int col = Math.min(last.col, c.buffer.line(line).length());
            c.editor.setCursor(new Pos(line, col), true);
        }
        c.editor.insert().begin("i", c.count, false, -1);
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/VimUndoManager.java` with:

```java
package dev.jasper.editor.vim;

import javax.swing.UIManager;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.BadLocationException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEdit;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RUndoManager;

/**
 * The text area's undo manager (spec 3.3). With Vim off it groups edits as RSyntaxTextArea's own manager does
 * (neighbouring typed characters form one step). With Vim on it never guesses: each Vim change the engine brackets
 * with {@link #beginStep} and {@link #endStep} is one step, and any other edit (the Editor's Replace, a paste
 * through ⌘V) is a step of its own. RSyntaxTextArea's atomic edits inside a Vim step join that step.
 */
final class VimUndoManager extends RUndoManager {
    /** One undo step, remembering where the cursor was before it, for Vim's cursor placement after undo. */
    static final class Step extends CompoundEdit {
        int cursorLine, cursorCol;
        int edits;
        /** Its change number, counting every step added (Vim's uh_seq). */
        int seq;

        Step(int cursorLine, int cursorCol) {
            this.cursorLine = cursorLine;
            this.cursorCol = cursorCol;
        }

        void take(UndoableEdit edit) {
            addEdit(edit);
            edits++;
        }

        // The manager treats a step as finished; addEdit above still works until end() because it reads the field.
        @Override public boolean isInProgress() { return false; }
        @Override public String getUndoPresentationName() { return UIManager.getString("AbstractUndoableEdit.undoText"); }
        @Override public String getRedoPresentationName() { return UIManager.getString("AbstractUndoableEdit.redoText"); }
    }

    private final RTextArea area;
    private boolean vim;
    private Step vimStep, internal, typing;
    private int stepDepth, internalDepth, lastOffset;
    /** The change numbers of the steps on the stack, oldest first; the first {@link #done} are done. */
    private final java.util.List<Integer> seqs = new java.util.ArrayList<>();
    private int done, seqLast;

    VimUndoManager(RTextArea area) {
        super(area);
        this.area = area;
        setLimit(1000);
    }

    /** @param on whether Vim mode groups the edits */
    void vim(boolean on) {
        vim = on;
        typing = seal(typing);
    }

    /**
     * The cursor moved before the open step's first change (an operator goes to its span's start, as Vim's
     * do_pending_operator does before u_save): the step records the cursor there.
     */
    void stepCursor(int cursorLine, int cursorCol) {
        if (vimStep != null && vimStep.edits == 0) {
            vimStep.cursorLine = cursorLine;
            vimStep.cursorCol = cursorCol;
        }
    }

    /** Opens a Vim step, or nests inside the open one. */
    void beginStep(int cursorLine, int cursorCol) {
        if (stepDepth++ == 0) {
            typing = seal(typing);
            vimStep = new Step(cursorLine, cursorCol);
        }
    }

    /** Closes one level; the outermost close puts the step on the undo stack if it changed anything. */
    void endStep() {
        if (stepDepth == 0 || --stepDepth > 0) return;
        Step step = vimStep;
        vimStep = null;
        step.end();
        if (step.edits > 0) add(step);
        updateActions();
    }

    /** Adds a new step to the stack, numbering it; the steps that were undone are gone. */
    private void add(Step step) {
        step.seq = ++seqLast;
        while (seqs.size() > done) seqs.removeLast();
        seqs.add(step.seq);
        done++;
        if (seqs.size() > getLimit()) {
            seqs.removeFirst();
            done--;
        }
        addEdit(step);
    }

    /** @return the change numbers of the done steps, oldest first */
    java.util.List<Integer> doneSeqs() { return java.util.List.copyOf(seqs.subList(0, done)); }

    /** @return the change numbers of the undone steps, in the order redo brings them back */
    java.util.List<Integer> undoneSeqs() { return java.util.List.copyOf(seqs.subList(done, seqs.size())); }

    boolean stepOpen() { return stepDepth > 0; }

    /** u_sync: the open step ends here if it changed anything, and a new one goes on at the same depth. */
    void sync() {
        if (vimStep == null || vimStep.edits == 0) return;
        int depth = stepDepth;
        closeAll();
        reopen(depth);
    }

    /** @return the step {@link #undo} would undo, or null when there is none or it is not a Vim step */
    Step nextUndo() { return editToBeUndone() instanceof Step step ? step : null; }

    /** @return the step {@link #redo} would redo, or null */
    Step nextRedo() { return editToBeRedone() instanceof Step step ? step : null; }

    @Override public void undoableEditHappened(UndoableEditEvent event) {
        UndoableEdit edit = event.getEdit();
        if (vimStep != null) {
            vimStep.take(edit);
            return;
        }
        if (internalDepth > 0) {
            internal.take(edit);
            return;
        }
        if (vim) {
            Step step = new Step(-1, -1);
            step.take(edit);
            step.end();
            add(step);
            updateActions();
            return;
        }
        // RSyntaxTextArea's grouping: an edit next to the previous one continues its step.
        int caret = area.getCaretPosition();
        if (typing != null && Math.abs(caret - lastOffset) <= 1) {
            typing.take(edit);
            lastOffset = caret;
            return;
        }
        typing = seal(typing);
        typing = new Step(-1, -1);
        typing.take(edit);
        add(typing);
        lastOffset = caret;
        updateActions();
    }

    @Override public void beginInternalAtomicEdit() {
        if (internalDepth++ == 0 && vimStep == null) {
            typing = seal(typing);
            internal = new Step(-1, -1);
        }
    }

    @Override public void endInternalAtomicEdit() {
        if (internalDepth == 0 || --internalDepth > 0 || internal == null) return;
        Step step = internal;
        internal = null;
        step.end();
        if (step.edits > 0) add(step);
        updateActions();
    }

    /** Counts an open step's edits, since RTextArea asks before it undoes (⌘Z in the middle of an insert). */
    @Override public synchronized boolean canUndo() {
        return vimStep != null && vimStep.edits > 0 || internal != null && internal.edits > 0 || super.canUndo();
    }

    /**
     * Nothing is redoable while an open step has edits: closing it for the redo would put it on the undo stack and
     * trim the redo stack, so the redo would fail (an outside ⌘⇧Z in the middle of an insert).
     */
    @Override public synchronized boolean canRedo() {
        if (vimStep != null && vimStep.edits > 0 || internal != null && internal.edits > 0) return false;
        return super.canRedo();
    }

    /**
     * An outside undo (⌘Z) while the engine has a step open: closes it so it is undoable, then reopens the same
     * nesting once the undo runs, so the engine's own {@link #endStep} later balances (spec: the rest of an insert
     * in progress stays one step).
     */
    @Override public void undo() {
        int depth = stepDepth;
        closeAll();
        try {
            super.undo();
            done--;
        } finally { reopen(depth); }
    }

    /** The redo counterpart of {@link #undo}. */
    @Override public void redo() {
        int depth = stepDepth;
        closeAll();
        try {
            super.redo();
            done++;
        } finally { reopen(depth); }
    }

    /** Reopens {@code depth} levels of Vim step after an outside undo/redo, cursor taken from the area's caret now. */
    private void reopen(int depth) {
        if (depth == 0) return;
        int line = 0, col = 0;
        try {
            int offset = area.getCaretPosition();
            line = area.getLineOfOffset(offset);
            col = offset - area.getLineStartOffset(line);
        } catch (BadLocationException ignored) {
            // Falls back to (0, 0); the reopened step's remembered cursor is only advisory.
        }
        for (int n = 0; n < depth; n++) beginStep(line, col);
    }

    @Override public void discardAllEdits() {
        stepDepth = internalDepth = 0;
        vimStep = internal = typing = null;
        seqs.clear();
        done = 0;
        super.discardAllEdits();
    }

    /** Ends every open step, as an undo from outside Vim (⌘Z during an insert) must. */
    private void closeAll() {
        while (stepDepth > 0) endStep();
        if (internal != null) {
            internalDepth = 1;
            endInternalAtomicEdit();
        }
        typing = seal(typing);
    }

    private static Step seal(Step step) {
        if (step != null) step.end();
        return null;
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/InsertMode.java` with:

```java
package dev.jasper.editor.vim;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * Insert and Replace mode (spec 3.4 and 4.1), ported from Vim's edit.c: an insert session records its keys for
 * {@code .} and for a count, typed characters go through the text area's own typing actions (so RSyntaxTextArea's
 * paired brackets apply), and {@code <CR>}, {@code <BS>}, {@code <Tab>} and the Ctrl keys behave as Vim's.
 */
final class InsertMode {
    private static final int CHAR = 0, WORD = 1, WORD_NOT_SPACE = 2, LINE = 3;

    /** One insert, from the command that started it to its {@code <Esc>}. */
    private static final class Session {
        final String command;
        int count;
        boolean replace;
        final boolean nested;
        /** Vim's Insstart_orig: where the insert started; <C-w> and <C-u> stop here once. */
        final Pos start;
        final List<KeyInput> keys = new ArrayList<>();
        final StringBuilder inserted = new StringBuilder();
        /** Characters Replace mode overwrote, oldest first; null for a character it appended. */
        final List<String> replaced = new ArrayList<>();
        /** The line whose indent autoindent made and nothing has been typed on (Vim's did_ai), or -1. */
        int autoIndentLine;
        /** Runs when the insert ends (a Visual block insert copies its text to the other lines). */
        Runnable onEnd;

        Session(String command, int count, boolean replace, boolean nested, Pos start, int autoIndentLine) {
            this.command = command;
            this.count = count;
            this.replace = replace;
            this.nested = nested;
            this.start = start;
            this.autoIndentLine = autoIndentLine;
        }
    }

    private final VimEditor editor;
    private Session session;
    /** Reads the next key as an argument ({@code <C-r>}'s register, {@code <C-v>}'s character). */
    private Consumer<KeyInput> argument;
    private boolean repeating;

    InsertMode(VimEditor editor) { this.editor = editor; }

    static void register(VimCommands commands) {
        commands.insert("<Esc>", "back to Normal mode, repeating the insert {count} times", (insert, key) -> insert.escape());
        commands.insert("<C-c>", "back to Normal mode", (insert, key) -> insert.escape());
        commands.insert("<CR>", "new line, with 'autoindent'", (insert, key) -> insert.newline());
        commands.insert("<C-j>", "new line", (insert, key) -> insert.newline());
        commands.insert("<BS>", "delete the character before the cursor", (insert, key) -> insert.backspace(CHAR));
        commands.insert("<C-h>", "delete the character before the cursor", (insert, key) -> insert.backspace(CHAR));
        commands.insert("<C-w>", "delete the word before the cursor", (insert, key) -> insert.backspace(WORD));
        commands.insert("<C-u>", "delete the text entered before the cursor in the line", (insert, key) -> insert.backspace(LINE));
        commands.insert("<Del>", "delete the character under the cursor", (insert, key) -> insert.delete());
        commands.insert("<Tab>", "insert a tab, or spaces with 'expandtab' or 'softtabstop'", (insert, key) -> insert.tab());
        commands.insert("<C-t>", "indent the line by 'shiftwidth'", (insert, key) -> insert.indent(true));
        commands.insert("<C-d>", "unindent the line by 'shiftwidth'", (insert, key) -> insert.indent(false));
        commands.insert("<C-r>", "insert a register", (insert, key) -> insert.register());
        commands.insert("<C-v>", "insert the next key literally, or a character by its code", (insert, key) -> insert.literal());
        commands.insert("<C-q>", "insert the next key literally, as CTRL-V", (insert, key) -> insert.literal());
        commands.insert("<C-a>", "insert the text of the last insert", (insert, key) -> insert.insertText(insert.editor.vim().registers().lastInserted(), true));
        commands.insert("<C-e>", "insert the character below the cursor", (insert, key) -> insert.copyFrom(1));
        commands.insert("<C-y>", "insert the character above the cursor", (insert, key) -> insert.copyFrom(-1));
        commands.insert("<C-o>", "run one Normal-mode command", (insert, key) -> insert.oneCommand());
        commands.insert("<Insert>", "switch between Insert and Replace mode", (insert, key) -> insert.toggleReplace());
        for (String arrow : List.of("<Left>", "<Right>", "<Up>", "<Down>", "<Home>", "<End>"))
            commands.insert(arrow, "move the cursor, starting a new insert for undo and .", InsertMode::arrow);
    }

    /**
     * Starts an insert at the cursor; the command has placed the cursor.
     *
     * @param command the command that started it ({@code i}, {@code o}, {@code c}, {@code R})
     * @param count how many times to repeat the typed text on {@code <Esc>}
     * @param replace whether this is Replace mode
     * @param autoIndentLine the line autoindent just indented (for Vim's did_ai), or -1
     */
    void begin(String command, int count, boolean replace, int autoIndentLine) {
        // Vim saves for undo at the first character typed, so undo puts the cursor where the insert began.
        Pos at = editor.cursor();
        editor.area().vimUndo().stepCursor(at.line, at.col);
        session = new Session(command, count, replace, false, editor.cursor(), autoIndentLine);
        editor.mode(replace ? Mode.REPLACE : Mode.INSERT);
    }

    /** @param hook runs when the current insert ends, after the cursor moves back */
    void onEnd(Runnable hook) { session.onEnd = hook; }

    /** An insert run by a Normal-mode command itself ({@code r<CR>}): not recorded, and its end tells no one. */
    void beginNested(String command) {
        session = new Session(command, 1, false, true, editor.cursor(), -1);
        editor.mode(Mode.INSERT);
    }

    /** o and O (open_line): a new line below or above with the current line's indent when 'autoindent' is set. */
    void openLine(boolean below) {
        Buffer b = editor.buffer();
        Pos p = editor.cursor();
        Options o = editor.options();
        String indent = o.flag("autoindent") ? Text.makeIndent(Text.indent(b.line(p.line), o.number("tabstop")), o.number("tabstop"), o.flag("expandtab")) : "";
        int line = below ? p.line + 1 : p.line;
        b.insertLines(line, List.of(indent));
        editor.setCursor(new Pos(line, indent.length()), true);
    }

    /** @return whether the key means something in Insert mode, for the handler rule */
    boolean wants(KeyInput key) {
        if (argument != null || key.plainChar() >= 0) return true;
        return editor.vim().commands().insertKey(key.notation()) != null;
    }

    void key(KeyInput key) {
        if (session == null) return;
        if (argument != null) {
            Consumer<KeyInput> handler = argument;
            argument = null;
            try { handler.accept(key); }
            catch (VimFailure failure) { editor.insertKeyFailed(failure); }
            return;
        }
        if (key.plainChar() >= 0) {
            character(key.key());
            record(key);
            return;
        }
        VimCommands.Entry entry = editor.vim().commands().insertKey(key.notation());
        if (entry == null) return;
        editor.vim().covered(entry.id());
        // These record themselves (or, like <Esc>, are recorded by the machine), or end the recorded insert.
        boolean selfRecorded = key.is("<Esc>") || key.is("<C-c>") || key.is("<C-o>") || key.is("<C-r>") || key.is("<C-v>") || key.is("<C-q>")
            || key.is("<C-a>") || key.is("<C-e>") || key.is("<C-y>") || arrowKey(key);
        // A key recorded only after its handler returns: a failing key (e.g. <BS> or <Del> with nothing to
        // remove) never joins the recorded insert, so <Esc>'s repeat and . never replay a no-op. A failing key
        // rings the bell and the insert goes on (Vim's ins_del and ins_bs beep without ending it), so a . whose
        // replayed key fails still reaches its <Esc>.
        try { ((VimCommands.InsertKey) entry.handler()).run(this, key); }
        catch (VimFailure failure) {
            editor.insertKeyFailed(failure);
            return;
        }
        if (!selfRecorded) record(key);
    }

    /** Text an input method committed; the text area has already inserted it. */
    void typedByInputMethod(String text) {
        if (session == null) return;
        text.codePoints().forEach(cp -> record(KeyInput.of(cp)));
        session.inserted.append(text);
        session.autoIndentLine = -1;
    }

    /** Ends the session without Vim's end-of-insert work, after an internal error. */
    void abort() {
        session = null;
        argument = null;
    }

    boolean active() { return session != null; }

    private void record(KeyInput key) {
        if (repeating || session == null) return;
        session.keys.add(key);
        if (!session.nested) editor.machine().recordInsert(key);
    }

    private void recordAll(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (cp == '\n') record(KeyInput.named("CR"));
            else if (cp < 0x20 || cp == 0x7f) {
                // Control characters replay literally, as Vim's AppendToRedobuffLit escapes them.
                record(KeyInput.ctrl('v'));
                record(KeyInput.of(cp));
            } else record(KeyInput.of(cp));
            i += Character.charCount(cp);
        }
    }

    /** A typed character: through the text area's typing action in Insert mode; overwriting in Replace mode. */
    private void character(String text) {
        Session s = session;
        s.autoIndentLine = -1;
        s.inserted.append(text);
        Buffer b = editor.buffer();
        Pos p = editor.cursor();
        if (s.replace) {
            String line = b.line(p.line);
            int offset = b.offset(p);
            if (p.col < line.length()) {
                int end = Text.next(line, p.col);
                s.replaced.add(line.substring(p.col, end));
                b.replace(offset, b.offset(p.line, end), text);
            } else {
                s.replaced.add(null);
                b.insert(offset, text);
            }
            editor.setCursorOffset(offset + text.length());
            return;
        }
        VimTextArea area = editor.area();
        // The text area's typing actions ignore control characters, so a tab or a literal goes straight in.
        if (text.length() == 1 && text.charAt(0) >= 0x20 && text.charAt(0) != 0x7f) {
            KeyStroke stroke = KeyStroke.getKeyStroke(text.charAt(0));
            Object name = area.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
            Action action = name == null ? null : area.getActionMap().get(name);
            if (action == null && area.getKeymap() != null) action = area.getKeymap().getDefaultAction();
            if (action != null) {
                area.setCaretPosition(b.offset(p));
                action.actionPerformed(new ActionEvent(area, ActionEvent.ACTION_PERFORMED, text));
                return;
            }
        }
        int offset = b.offset(p);
        b.insert(offset, text);
        editor.setCursorOffset(offset + text.length());
    }

    /** Text inserted as if typed ({@code <C-r>}, {@code <C-a>}): line breaks go through {@link #newline}. */
    private void insertText(String text, boolean recorded) {
        if (recorded) recordAll(text);
        boolean was = repeating;
        repeating = true;
        try {
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                if (cp == '\n') newline();
                else character(Character.toString(cp));
                i += Character.charCount(cp);
            }
        } finally { repeating = was; }
    }

    /**
     * ins_esc. A key recorded during the original typing succeeded then, but the buffer has moved on by the time
     * the count replays it (spec: repeated {@code <BS>}/{@code <Del>} at an edge); a failure part-way through the
     * repeat must still leave Insert mode, so the cleanup below runs in a {@code finally}.
     */
    private void escape() {
        Session s = session;
        repeating = true;
        try {
            for (int n = 1; n < s.count; n++) {
                if (s.command.equals("o") || s.command.equals("O")) {
                    openLine(true);
                    s.autoIndentLine = editor.options().flag("autoindent") ? editor.cursor().line : -1;
                }
                for (KeyInput key : s.keys) key(key);
            }
        } finally {
            repeating = false;
            removeAutoIndent();
            editor.vim().registers().lastInserted(s.inserted.toString());
            Pos p = editor.cursor();
            // The '^ mark: where Insert mode ended, before the cursor moves back (ins_esc); '[ and '] around the
            // inserted text (stop_insert).
            editor.marks().set('^', p);
            editor.marks().set('[', s.start);
            editor.marks().set(']', p);
            session = null;
            editor.mode(Mode.NORMAL);
            if (p.col > 0) editor.setCursor(new Pos(p.line, Text.previous(editor.buffer().line(p.line), p.col)));
            if (s.onEnd != null) s.onEnd.run();
            if (!s.nested) editor.machine().insertEnded();
        }
    }

    /** Vim's did_ai: an autoindented line that stayed blank loses its indent. */
    private void removeAutoIndent() {
        Pos p = editor.cursor();
        if (session.autoIndentLine == p.line && p.line < editor.buffer().count() && Text.blank(editor.buffer().line(p.line))) {
            editor.buffer().setLine(p.line, "");
            editor.setCursorInInsert(new Pos(p.line, 0));
        }
        session.autoIndentLine = -1;
    }

    /** open_line in Insert mode: splits the line, copying the indent with 'autoindent'. */
    private void newline() {
        Buffer b = editor.buffer();
        Options o = editor.options();
        Pos p = editor.cursor();
        String line = b.line(p.line);
        boolean ai = o.flag("autoindent");
        int ts = o.number("tabstop");
        String before = line.substring(0, p.col), after = line.substring(p.col);
        String indent = ai ? Text.makeIndent(Text.indent(line, ts), ts, o.flag("expandtab")) : "";
        if (ai) after = after.substring(Text.firstNonBlank(after));
        if (session.autoIndentLine == p.line && Text.blank(before)) before = "";
        b.setLine(p.line, before + "\n" + indent + after);
        editor.setCursorInInsert(new Pos(p.line + 1, indent.length()));
        session.autoIndentLine = ai ? p.line + 1 : -1;
        session.inserted.append('\n');
    }

    /** ins_bs: one character, a word or the line, stopping once where the insert started. */
    private void backspace(int mode) {
        Session s = session;
        Buffer b = editor.buffer();
        Options o = editor.options();
        Pos p = editor.cursor();
        boolean startOk = o.has("backspace", "start") || o.has("backspace", "2") || o.has("backspace", "3");
        if (p.line == 0 && p.col == 0) throw Context.fail();
        if (!startOk && p.line == s.start.line && p.col <= s.start.col) throw Context.fail();
        if (p.col == 0) {
            if (!(o.has("backspace", "eol") || o.has("backspace", "2") || o.has("backspace", "3"))) throw Context.fail();
            if (s.replace) {
                String above = b.line(p.line - 1);
                if (!s.replaced.isEmpty() && "\n".equals(s.replaced.getLast())) {
                    s.replaced.removeLast();
                    b.remove(b.end(p.line - 1), b.start(p.line));
                }
                editor.setCursorInInsert(new Pos(p.line - 1, above.length()));
                return;
            }
            int join = b.end(p.line - 1);
            int length = b.line(p.line - 1).length();
            b.remove(join, join + 1);
            if (s.start.line == p.line) {
                s.start.line--;
                s.start.col = length;
            }
            editor.setCursorOffset(join);
            trimInserted();
            return;
        }
        String line = b.line(p.line);
        int sts = o.number("softtabstop"), ts = o.number("tabstop");
        char before = line.charAt(p.col - 1);
        if (mode == CHAR && sts != 0 && (before == '\t' || before == ' ') && !s.replace) {
            int vcol = Text.virtcol(line, p.col, ts);
            int want = ((vcol - 1) / sts) * sts;
            int col = p.col;
            while (Text.virtcol(line, col, ts) > want && col > 0 && (line.charAt(col - 1) == ' ' || line.charAt(col - 1) == '\t')) col--;
            int reached = Text.virtcol(line, col, ts);
            String fill = reached < want ? " ".repeat(want - reached) : "";
            b.replace(b.offset(p.line, col), b.offset(p), fill);
            editor.setCursorInInsert(new Pos(p.line, col + fill.length()));
            trimInserted();
            return;
        }
        int mincol = 0;
        if (mode == LINE && o.flag("autoindent")) {
            int fnb = Text.firstNonBlank(line);
            if (fnb < p.col) mincol = fnb;
        }
        Keywords keywords = o.keywords();
        int cclass = p.col < line.length() ? keywords.charClass(line.codePointAt(p.col)) : 0;
        boolean wordState = false;
        int col = p.col;
        do {
            col = Text.previous(line, col);
            int cc = line.codePointAt(col);
            int prevClass = cclass;
            cclass = keywords.charClass(cc);
            if (mode == WORD && cc != ' ' && cc != '\t') {
                mode = WORD_NOT_SPACE;
                wordState = keywords.isWord(cc);
            } else if (mode == WORD_NOT_SPACE && (cc == ' ' || cc == '\t' || keywords.isWord(cc) != wordState || prevClass != cclass)) {
                col = Text.next(line, col);
                break;
            }
            if (s.replace) {
                String original = s.replaced.isEmpty() ? "" : s.replaced.removeLast();
                int end = Text.next(line, col);
                if (original == null) line = line.substring(0, col) + line.substring(end);
                else if (!original.isEmpty()) line = line.substring(0, col) + original + line.substring(end);
            } else {
                line = line.substring(0, col) + line.substring(Text.next(line, col));
                trimInserted();
            }
            if (mode == CHAR) break;
        } while (col > mincol && (p.line != s.start.line || col != s.start.col));
        b.setLine(p.line, line);
        editor.setCursorInInsert(new Pos(p.line, col));
        if (p.line == s.start.line && col < s.start.col) s.start.col = col;
    }

    private void trimInserted() {
        StringBuilder inserted = session.inserted;
        if (!inserted.isEmpty()) inserted.setLength(inserted.length() - 1);
    }

    /** {@code <Del>}: the character under the cursor, or the line break at the end of the line. */
    private void delete() {
        Buffer b = editor.buffer();
        Pos p = editor.cursor();
        String line = b.line(p.line);
        if (p.col < line.length()) b.remove(b.offset(p), b.offset(p.line, Text.next(line, p.col)));
        else if (p.line < b.count() - 1 && editor.options().has("backspace", "eol")) b.remove(b.end(p.line), b.end(p.line) + 1);
        else throw Context.fail();
        editor.setCursorInInsert(p);
    }

    /** ins_tab. */
    private void tab() {
        Options o = editor.options();
        boolean et = o.flag("expandtab");
        int sts = o.number("softtabstop"), ts = o.number("tabstop");
        session.autoIndentLine = -1;
        if (!et && sts == 0) {
            character("\t");
            return;
        }
        Buffer b = editor.buffer();
        Pos p = editor.cursor();
        String line = b.line(p.line);
        int vcol = Text.virtcol(line, p.col, ts);
        int width = sts != 0 ? sts : ts;
        int target = (vcol / width + 1) * width;
        if (et) {
            String spaces = " ".repeat(target - vcol);
            b.insert(b.offset(p), spaces);
            editor.setCursorInInsert(new Pos(p.line, p.col + spaces.length()));
            session.inserted.append(spaces);
            return;
        }
        // 'softtabstop' without 'expandtab': fill with spaces, then turn the white space before the cursor into tabs.
        int runStart = p.col;
        while (runStart > 0 && (line.charAt(runStart - 1) == ' ' || line.charAt(runStart - 1) == '\t')) runStart--;
        int startV = Text.virtcol(line, runStart, ts);
        StringBuilder white = new StringBuilder();
        int v = startV;
        while ((v / ts + 1) * ts <= target) {
            white.append('\t');
            v = (v / ts + 1) * ts;
        }
        white.append(" ".repeat(target - v));
        b.replace(b.offset(p.line, runStart), b.offset(p), white.toString());
        editor.setCursorInInsert(new Pos(p.line, runStart + white.length()));
        session.inserted.append('\t');
    }

    /** {@code <C-t>} and {@code <C-d>}: change the indent by 'shiftwidth', rounded, keeping the cursor on its text. */
    private void indent(boolean more) {
        Buffer b = editor.buffer();
        Options o = editor.options();
        Pos p = editor.cursor();
        String line = b.line(p.line);
        int ts = o.number("tabstop"), sw = o.shiftwidth();
        int indent = Text.indent(line, ts);
        int width = more ? (indent / sw + 1) * sw : Math.max(0, (indent % sw == 0 ? indent - sw : indent / sw * sw));
        int fnb = Text.firstNonBlank(line);
        String replaced = Text.makeIndent(width, ts, o.flag("expandtab"));
        b.setLine(p.line, replaced + line.substring(fnb));
        int col = p.col >= fnb ? p.col - fnb + replaced.length() : replaced.length();
        editor.setCursorInInsert(new Pos(p.line, col));
        session.autoIndentLine = -1;
    }

    /** {@code <C-r>{register}}: the register's text as if typed; {@code <C-r><C-r>} and friends insert it the same way. */
    private void register() {
        argument = key -> {
            if (key.is("<C-r>") || key.is("<C-o>") || key.is("<C-p>")) {
                argument = this::registerNamed;
                return;
            }
            registerNamed(key);
        };
    }

    private void registerNamed(KeyInput key) {
        int c = key.plainChar();
        if (c < 0 || !Registers.valid((char) c, false)) throw Context.fail();
        var content = editor.vim().registers().get((char) c);
        if (content.isEmpty()) throw Context.fail();
        insertText(content.get().text(), true);
    }

    /** {@code <C-v>}: the next key literally, or {@code u}/{@code x}/{@code o}/decimal digits for a code. */
    private void literal() {
        argument = key -> {
            int c = key.plainChar();
            if (c == 'u' || c == 'U' || c == 'x' || c == 'X' || c == 'o' || c == 'O' || c >= '0' && c <= '9') {
                record(KeyInput.ctrl('v'));
                record(key);
                int radix = c == 'u' || c == 'U' || c == 'x' || c == 'X' ? 16 : c == 'o' || c == 'O' ? 8 : 10;
                int max = c == 'u' ? 4 : c == 'U' ? 8 : c == 'x' || c == 'X' ? 2 : 3;
                StringBuilder digits = new StringBuilder(radix == 10 ? String.valueOf((char) c) : "");
                collectDigits(digits, radix, max);
                return;
            }
            record(KeyInput.ctrl('v'));
            record(key);
            String text = literalText(key);
            if (text == null) throw Context.fail();
            insertLiteral(text);
        };
    }

    private void collectDigits(StringBuilder digits, int radix, int max) {
        if (digits.length() >= max) {
            insertLiteral(Character.toString(Integer.parseInt(digits.toString(), radix)));
            return;
        }
        argument = key -> {
            int c = key.plainChar();
            if (c >= 0 && Character.digit(c, radix) >= 0) {
                record(key);
                digits.append((char) c);
                collectDigits(digits, radix, max);
                return;
            }
            if (!digits.isEmpty()) insertLiteral(Character.toString(Integer.parseInt(digits.toString(), radix)));
            key(key);
        };
    }

    private static String literalText(KeyInput key) {
        if (key.plainChar() >= 0) return key.key();
        if (key.modifiers() == KeyInput.CTRL && key.isChar()) {
            char c = key.key().charAt(0);
            if (c >= 'a' && c <= 'z') return String.valueOf((char) (c - 'a' + 1));
            if (c == '@') return "\u0000";
            if (c == '\\') return "\u001c";
            if (c == ']') return "\u001d";
            if (c == '^') return "\u001e";
            return null;
        }
        return switch (key.key()) {
            case "Tab" -> "\t";
            case "CR" -> "\r";
            case "Esc" -> "\u001b";
            case "BS" -> "\u0008";
            case "Del" -> "\u007f";
            default -> null;
        };
    }

    private void insertLiteral(String text) {
        Buffer b = editor.buffer();
        int offset = b.offset(editor.cursor());
        b.insert(offset, text);
        editor.setCursorOffset(offset + text.length());
        session.inserted.append(text);
        session.autoIndentLine = -1;
    }

    /** {@code <C-e>} and {@code <C-y>}: the character at the same display column in the line below or above. */
    private void copyFrom(int delta) {
        Buffer b = editor.buffer();
        Pos p = editor.cursor();
        int other = p.line + delta;
        if (other < 0 || other >= b.count()) throw Context.fail();
        int ts = editor.options().number("tabstop");
        int vcol = Text.virtcol(b.line(p.line), p.col, ts);
        String text = b.line(other);
        int col = Text.columnAt(text, vcol, ts);
        if (col >= text.length()) throw Context.fail();
        String copied = text.substring(col, Text.next(text, col));
        recordAll(copied);
        character(copied);
    }

    /** {@code <C-o>}: one Normal-mode command, then back to Insert mode (edit.c's ins_ctrl_o). */
    private void oneCommand() {
        Session s = session;
        removeAutoIndent();
        editor.vim().registers().lastInserted(s.inserted.toString());
        Pos p = editor.cursor();
        String line = editor.buffer().line(p.line);
        // At the end of the line the command runs on the last character; Insert mode comes back after it.
        boolean atEnd = !line.isEmpty() && p.col >= line.length();
        if (atEnd) editor.setCursor(new Pos(p.line, Text.last(line)), true);
        session = null;
        editor.mode(Mode.INSERT_NORMAL);
        editor.machine().insertEnded();
        editor.machine().pausedInsert(s.replace, atEnd ? p.line : -1);
    }

    private void toggleReplace() {
        session.replace = !session.replace;
        editor.mode(session.replace ? Mode.REPLACE : Mode.INSERT);
    }

    private static boolean arrowKey(KeyInput key) {
        return switch (key.key()) {
            case "Left", "Right", "Up", "Down", "Home", "End" -> key.modifiers() == 0;
            default -> false;
        };
    }

    /** An arrow key: moves, then starts a new insert for undo and {@code .} (Vim's start_arrow and stop_arrow). */
    private void arrow(KeyInput key) {
        Buffer b = editor.buffer();
        Pos p = editor.cursor();
        int ts = editor.options().number("tabstop");
        String line = b.line(p.line);
        switch (key.key()) {
            case "Left" -> {
                if (p.col == 0) throw Context.fail();
                p.col = Text.previous(line, p.col);
            }
            case "Right" -> {
                if (p.col >= line.length()) throw Context.fail();
                p.col = Text.next(line, p.col);
            }
            case "Up", "Down" -> {
                int other = p.line + (key.key().equals("Up") ? -1 : 1);
                if (other < 0 || other >= b.count()) throw Context.fail();
                p = new Pos(other, editor.insertColumn(other));
            }
            case "Home" -> p.col = 0;
            case "End" -> p.col = line.length();
            default -> { }
        }
        removeAutoIndent();
        editor.setCursor(p, true);
        if (!key.key().equals("Up") && !key.key().equals("Down")) editor.forgetColumn();
        Session s = session;
        editor.vim().registers().lastInserted(s.inserted.toString());
        session = new Session("i", 1, s.replace, s.nested, editor.cursor(), -1);
        if (!s.nested) editor.machine().restartInsert();
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/CommandLine.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The command line that {@code :}, {@code /} and {@code ?} read (spec 5.3), with Vim's editing keys (ex_getln.c): moving and
 * deleting, {@code <C-r>} for registers and the word under the cursor, {@code <C-v>} for a literal key, and history
 * walked by {@code <Up>}/{@code <Down>} filtered by the typed prefix. The Vim bar draws it; the editor feeds it keys
 * while it is active.
 */
final class CommandLine {
    private final VimEditor editor;
    private char type;
    private final StringBuilder text = new StringBuilder();
    private int cursor;
    private final List<KeyInput> typed = new ArrayList<>();
    private List<String> history = List.of();
    private int historyIndex;
    private String historyPrefix;
    private Consumer<KeyInput> argument;
    private Consumer<String> changed, done;
    private Runnable cancelled;

    CommandLine(VimEditor editor) { this.editor = editor; }

    /**
     * Opens the command line.
     *
     * @param type {@code /}, {@code ?} or {@code :}
     * @param history its history, oldest first
     * @param changed runs after each edit with the text (for 'incsearch')
     * @param done runs with the text on {@code <CR>}
     * @param cancelled runs on {@code <Esc>}, {@code <C-c>}, or {@code <BS>} on an empty line
     */
    void start(char type, List<String> history, Consumer<String> changed, Consumer<String> done, Runnable cancelled) {
        start(type, history, "", changed, done, cancelled);
    }

    /** Opens the command line with {@code initial} typed on it ({@code '<,'>} after Visual {@code :}). */
    void start(char type, List<String> history, String initial, Consumer<String> changed, Consumer<String> done, Runnable cancelled) {
        this.type = type;
        this.history = history;
        this.changed = changed;
        this.done = done;
        this.cancelled = cancelled;
        text.setLength(0);
        text.append(initial);
        cursor = text.length();
        typed.clear();
        historyIndex = history.size();
        historyPrefix = null;
        argument = null;
    }

    boolean active() { return done != null; }

    /** @return {@code /}, {@code ?} or {@code :} */
    char type() { return type; }

    String text() { return text.toString(); }

    /** @return the cursor's index in the text */
    int cursor() { return cursor; }

    /** @return the keys typed on this command line, for {@code .} */
    List<KeyInput> typed() { return List.copyOf(typed); }

    /** The keys that end the line, and whether each executes it. */
    void key(KeyInput key) {
        typed.add(key);
        if (argument != null) {
            Consumer<KeyInput> handler = argument;
            argument = null;
            handler.accept(key);
            edited();
            return;
        }
        int ch = key.plainChar();
        if (ch >= 0) {
            insert(new String(Character.toChars(ch)));
            return;
        }
        switch (key.notation()) {
            case "<CR>", "<C-j>", "<C-m>", "<NL>", "<kEnter>" -> finish();
            case "<Esc>", "<C-c>", "<C-[>" -> cancel();
            case "<BS>", "<C-h>" -> {
                if (text.isEmpty()) cancel();
                else if (cursor > 0) {
                    int from = text.offsetByCodePoints(cursor, -1);
                    text.delete(from, cursor);
                    cursor = from;
                    edited();
                }
            }
            case "<Del>" -> {
                if (text.isEmpty()) cancel();
                else if (cursor < text.length()) {
                    text.delete(cursor, text.offsetByCodePoints(cursor, 1));
                    edited();
                } else {
                    int from = text.offsetByCodePoints(cursor, -1);
                    text.delete(from, cursor);
                    cursor = from;
                    edited();
                }
            }
            case "<Left>" -> { if (cursor > 0) cursor = text.offsetByCodePoints(cursor, -1); }
            case "<Right>" -> { if (cursor < text.length()) cursor = text.offsetByCodePoints(cursor, 1); }
            case "<S-Left>", "<C-Left>" -> cursor = wordBack(cursor);
            case "<S-Right>", "<C-Right>" -> cursor = wordForward(cursor);
            case "<Home>", "<C-b>" -> cursor = 0;
            case "<End>", "<C-e>" -> cursor = text.length();
            case "<C-w>" -> {
                int from = wordBack(cursor);
                text.delete(from, cursor);
                cursor = from;
                edited();
            }
            case "<C-u>" -> {
                text.delete(0, cursor);
                cursor = 0;
                edited();
            }
            case "<C-v>", "<C-q>" -> argument = k -> {
                int c = k.plainChar();
                insertQuietly(c >= 0 ? new String(Character.toChars(c)) : literal(k));
            };
            case "<C-r>" -> argument = this::register;
            case "<Tab>" -> insert("\t");
            case "<Up>", "<S-Up>", "<PageUp>" -> recall(-1, key.is("<Up>"));
            case "<Down>", "<S-Down>", "<PageDown>" -> recall(1, key.is("<Down>"));
            default -> editor.bell();
        }
    }

    private static String literal(KeyInput key) {
        String n = key.notation();
        if (n.startsWith("<C-") && n.length() == 5) {
            char c = Character.toUpperCase(n.charAt(3));
            if (c >= '@' && c <= '_') return String.valueOf((char) (c - '@'));
        }
        return switch (n) {
            case "<CR>" -> "\r";
            case "<Tab>" -> "\t";
            case "<Esc>" -> "\033";
            default -> n;
        };
    }

    /** {@code <C-r>}: a register, or with {@code <C-w>}/{@code <C-a>} the word or WORD under the cursor. */
    private void register(KeyInput key) {
        if (key.is("<C-r>") || key.is("<C-o>") || key.is("<C-p>")) {
            argument = this::register;
            return;
        }
        if (key.is("<C-w>") || key.is("<C-a>")) {
            Pos p = editor.cursor();
            String line = editor.buffer().line(p.line);
            int[] found = key.is("<C-w>") ? Search.identifier(line, p.col, editor.options().keywords()) : nonBlank(line, p.col);
            if (found != null) insertQuietly(line.substring(found[0], found[1]));
            return;
        }
        int name = key.plainChar();
        if (name < 0 || !Registers.valid((char) name, false)) return;
        editor.vim().registers().get((char) name).ifPresent(content -> {
            List<String> lines = content.lines();
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                out.append(lines.get(i));
                if (content.type() == Registers.Type.LINE || i < lines.size() - 1) out.append('\r');
            }
            insertQuietly(out.toString());
        });
    }

    private static int[] nonBlank(String line, int col) {
        int start = col;
        while (start < line.length() && Character.isWhitespace(line.charAt(start))) start++;
        if (start >= line.length()) return null;
        while (start > 0 && !Character.isWhitespace(line.charAt(start - 1))) start--;
        int end = start;
        while (end < line.length() && !Character.isWhitespace(line.charAt(end))) end++;
        return new int[] { start, end };
    }

    private void recall(int step, boolean filtered) {
        if (historyPrefix == null) historyPrefix = filtered ? text.substring(0, cursor) : "";
        int i = historyIndex;
        while (true) {
            i += step;
            if (i < 0) return;
            if (i >= history.size()) {
                historyIndex = history.size();
                replace(historyPrefix);
                return;
            }
            if (history.get(i).startsWith(historyPrefix)) break;
        }
        historyIndex = i;
        replace(history.get(i));
    }

    /** A history entry replaces the text; the walk through the history goes on from it. */
    private void replace(String value) {
        text.setLength(0);
        text.append(value);
        cursor = text.length();
        if (changed != null) changed.accept(text.toString());
    }

    private int wordBack(int from) {
        int i = from;
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) i--;
        if (i > 0 && isWord(text.charAt(i - 1))) while (i > 0 && isWord(text.charAt(i - 1))) i--;
        else if (i > 0) i--;
        return i;
    }

    private int wordForward(int from) {
        int i = from;
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) i++;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        return i;
    }

    private boolean isWord(char c) { return editor.options().keywords().isWord(c); }

    private void insert(String value) {
        insertQuietly(value);
        edited();
    }

    private void insertQuietly(String value) {
        text.insert(cursor, value);
        cursor += value.length();
        historyPrefix = null;
    }

    private void edited() {
        historyIndex = history.size();
        if (changed != null) changed.accept(text.toString());
    }

    private void finish() {
        Consumer<String> then = done;
        close();
        then.accept(text.toString());
    }

    private void cancel() {
        Runnable then = cancelled;
        close();
        then.run();
    }

    private void close() {
        done = null;
        changed = null;
        argument = null;
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/VimFailure.java` with:

```java
package dev.jasper.editor.vim;

/**
 * A Vim command failed (spec 2.3): the engine rings the visual bell, shows the message if there is one, drops the
 * pending command and stops a running repeat. Thrown from anywhere inside a command; it carries no stack trace.
 */
final class VimFailure extends RuntimeException {
    /** A failure Vim gives no sign of: no message and no bell (an Ex address that stops short). */
    final boolean quiet;

    VimFailure(String message) { this(message, false); }

    private VimFailure(String message, boolean quiet) {
        super(message, null, false, false);
        this.quiet = quiet;
    }

    static VimFailure bell() { return new VimFailure(""); }

    static VimFailure quiet() { return new VimFailure("", true); }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/regex/VimRegex.java` with:

```java
package dev.jasper.editor.vim.regex;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A Vim pattern translated to {@link Pattern} (spec 3.7). The lexer follows Vim's regexp.c (peekchr, skipchr), so
 * the magic levels {@code \v \m \M \V}, the position rules for {@code ^ $ *} and the backslash toggles behave as in
 * Vim; the parser follows regexp.c's regbranch, regconcat, regpiece and regatom, writing Java syntax instead of a
 * program. Text is matched as one string whose lines end with {@code \n}: {@code ^} and {@code $} are line starts and
 * ends, and only the {@code \_x} forms, {@code \n} and {@code \_.} match a line break.
 *
 * <p>{@code \zs} and {@code \ze} become empty named groups; {@link #start} and {@link #end} narrow a match with them.
 * Items Jasper does not support fail with {@code E869}, naming the item.
 */
public final class VimRegex {
    /**
     * What a pattern needs from its editor.
     *
     * @param ignoreCase 'ignorecase'
     * @param smartCase 'smartcase': an upper-case letter in the pattern turns 'ignorecase' off
     * @param keyword a Java character-class body for 'iskeyword' ({@code \k}, {@code \<}, {@code \>})
     * @param lastSubstitute the last substitute string, for {@code ~}, or null
     */
    public record Settings(boolean ignoreCase, boolean smartCase, String keyword, String lastSubstitute) {
        /** Vim's defaults: case matters, and the default 'iskeyword'. */
        public static final Settings DEFAULT = new Settings(false, false, "0-9A-Za-z_\\u00c0-\\u00ff" + WIDE, null);
    }

    /** Characters above U+00FF that Vim counts as word characters (utf_class above 1): all but punctuation and blanks. */
    public static final String WIDE = "[\\x{100}-\\x{10ffff}&&[^\\p{P}\\p{Zs}\\u3000]]";

    private static final int MAGIC = 0x40000000;
    private static final int NONE = 1, OFF = 2, ON = 3, ALL = 4;
    private static final String META = "%&()*+.123456789<=>?@ACDFHIKLMOPSUVWXZ[_acdfhiklmnopsuvwxz{|~";
    private static final String ABBREVIATIONS = "rteb";

    private final Pattern pattern;
    private final boolean ignoreCase;
    private final boolean hasStart, hasEnd, multiline;
    private final int groups;

    private VimRegex(Pattern pattern, boolean ignoreCase, boolean hasStart, boolean hasEnd, boolean multiline, int groups) {
        this.pattern = pattern;
        this.ignoreCase = ignoreCase;
        this.hasStart = hasStart;
        this.hasEnd = hasEnd;
        this.multiline = multiline;
        this.groups = groups;
    }

    /**
     * @param vim a Vim pattern
     * @param settings the editor's options
     * @return the translated pattern
     * @throws IllegalArgumentException with Vim's error message for a bad or unsupported pattern
     */
    public static VimRegex compile(String vim, Settings settings) {
        Translator t = new Translator(vim, settings);
        String java = t.translate();
        boolean ic = t.forceIgnore || !t.forceMatch && settings.ignoreCase() && !(settings.smartCase() && hasUpper(vim));
        int flags = Pattern.MULTILINE | Pattern.UNIX_LINES;
        if (ic) flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        try {
            return new VimRegex(Pattern.compile(java, flags), ic, t.hasStart, t.hasEnd, t.multiline, t.groups);
        } catch (PatternSyntaxException bad) {
            throw new IllegalArgumentException("E486: Pattern not found: " + vim, bad);
        }
    }

    /** @return whether the pattern contains an upper-case letter that is not after a backslash (pat_has_uppercase) */
    static boolean hasUpper(String pattern) {
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\' && i + 1 < pattern.length()) {
                char next = pattern.charAt(i + 1);
                if (next == '_' && i + 2 < pattern.length()) i += 2;
                else if (next == '%' && i + 2 < pattern.length()) i += 2;
                else i++;
            } else if (Character.isUpperCase(c)) return true;
        }
        return false;
    }

    /** @return the Java pattern */
    public Pattern pattern() { return pattern; }

    /** @return whether the pattern ignores case */
    public boolean ignoreCase() { return ignoreCase; }

    /** @return whether a match may span lines (Vim's re_multiline) */
    public boolean multiline() { return multiline; }

    /**
     * @param m a matcher after a successful find
     * @param n a group number: 0 for the whole match (narrowed by {@code \zs} and {@code \ze}), 1 to 9 for
     *     {@code \(\)}
     * @return where the group starts, or -1 when it took no part in the match
     */
    public int groupStart(Matcher m, int n) {
        if (n == 0) return start(m);
        return n <= groups ? m.start("g" + n) : -1;
    }

    /** @return where the group ends, or -1 when it took no part in the match */
    public int groupEnd(Matcher m, int n) {
        if (n == 0) return end(m);
        return n <= groups ? m.end("g" + n) : -1;
    }

    /** @return where the match starts, after {@code \zs} */
    public int start(Matcher m) {
        if (hasStart && m.start("zs") >= 0) return m.start("zs");
        return m.start();
    }

    /** @return where the match ends, at {@code \ze} */
    public int end(Matcher m) {
        if (hasEnd && m.start("ze") >= 0) return m.start("ze");
        return m.end();
    }

    /** The lexer and parser: one use. */
    private static final class Translator {
        private final String src;
        private final Settings settings;
        private int pos;
        private int magic = ON;
        private int curchr = -1, prevchr = -1, prevprevchr = -1, nextchr = -1;
        private boolean atStart = true, prevAtStart;
        private int afterSlash;
        private int prevchrLen;
        boolean forceIgnore, forceMatch, hasStart, hasEnd;
        /** Whether a match may span lines ({@code \n}, the {@code \_} items, {@code \%^}, {@code \%$}): Vim's RF_HASNL. */
        boolean multiline;
        private int groups;

        Translator(String src, Settings settings) {
            this.src = src;
            this.settings = settings;
        }

        String translate() {
            String out = alternation(false);
            if (peek() != 0) {
                if (peek() == magic(')')) throw error("E16: Invalid range");
                throw error("E486: Pattern not found: " + src);
            }
            return out;
        }

        // --- the lexer (regexp.c: peekchr, skipchr, getchr) ---

        private static int magic(int c) { return c | MAGIC; }

        private static int noMagic(int c) { return c & ~MAGIC; }

        private static boolean isMagic(int c) { return c >= 0 && (c & MAGIC) != 0; }

        private static int toggle(int c) { return isMagic(c) ? noMagic(c) : magic(c); }

        private char at(int i) { return i < src.length() ? src.charAt(i) : 0; }

        /** @return the next item: a character, or {@code magic(c)} for a special one; 0 at the end */
        int peek() {
            if (curchr != -1) return curchr;
            char c = at(pos);
            curchr = c;
            switch (c) {
                case '.', '[', '~' -> { if (magic >= ON) curchr = magic(c); }
                case '(', ')', '{', '%', '+', '=', '?', '@', '!', '&', '|', '<', '>', '#', '"', '\'', ',', '-', ':', ';', '`', '/' -> {
                    if (magic == ALL) curchr = magic(c);
                }
                case '*' -> {
                    if (magic >= ON && !atStart && !(prevAtStart && prevchr == magic('^'))
                        && (afterSlash > 0 || prevchr != magic('(') && prevchr != magic('&') && prevchr != magic('|')))
                        curchr = magic('*');
                }
                case '^' -> {
                    if (magic >= OFF && (atStart || magic == ALL || prevchr == magic('(') || prevchr == magic('|')
                        || prevchr == magic('&') || prevchr == magic('n') || noMagic(prevchr) == '(' && prevprevchr == magic('%'))) {
                        curchr = magic('^');
                        atStart = true;
                        prevAtStart = false;
                    }
                }
                case '$' -> {
                    if (magic >= OFF) {
                        int p = pos + 1;
                        boolean all = magic == ALL;
                        while (at(p) == '\\' && "cCmMvVZ".indexOf(at(p + 1)) >= 0 && at(p + 1) != 0) {
                            if (at(p + 1) == 'v') all = true;
                            else if ("mMV".indexOf(at(p + 1)) >= 0) all = false;
                            p += 2;
                        }
                        if (at(p) == 0 || at(p) == '\\' && "|&)n".indexOf(at(p + 1)) >= 0 && at(p + 1) != 0
                            || all && "|&)".indexOf(at(p)) >= 0 && at(p) != 0 || magic == ALL)
                            curchr = magic('$');
                    }
                }
                case '\\' -> {
                    char n = at(pos + 1);
                    if (n == 0) curchr = '\\';
                    else if (META.indexOf(n) >= 0) {
                        curchr = -1;
                        prevAtStart = atStart;
                        atStart = false;
                        pos++;
                        afterSlash++;
                        peek();
                        pos--;
                        afterSlash--;
                        curchr = toggle(curchr);
                    } else if (ABBREVIATIONS.indexOf(n) >= 0) {
                        curchr = switch (n) {
                            case 'r' -> '\r';
                            case 't' -> '\t';
                            case 'e' -> 0x1b;
                            default -> '\b';
                        };
                    } else if (magic == NONE && (n == '$' || n == '^')) curchr = toggle(n);
                    else curchr = Character.codePointAt(src, pos + 1);
                }
                default -> {
                    if (c != 0) curchr = Character.codePointAt(src, pos);
                }
            }
            return curchr;
        }

        void skip() {
            prevchrLen = at(pos) == '\\' ? 1 : 0;
            if (pos + prevchrLen < src.length()) prevchrLen += Character.charCount(src.codePointAt(pos + prevchrLen));
            pos += prevchrLen;
            prevAtStart = atStart;
            atStart = false;
            prevprevchr = prevchr;
            prevchr = curchr;
            curchr = nextchr;
            nextchr = -1;
        }

        /** skipchr_keepstart: for \c and the magic switches, which do not end the pattern's start. */
        void skipKeepStart() {
            boolean as = prevAtStart;
            int pr = prevchr, prpr = prevprevchr;
            skip();
            atStart = as;
            prevchr = pr;
            prevprevchr = prpr;
        }

        int get() {
            int c = peek();
            skip();
            return c;
        }

        /** Forgets the lookahead, after reading raw characters at {@code pos}. */
        void resync() {
            curchr = -1;
            nextchr = -1;
        }

        IllegalArgumentException error(String message) { return new IllegalArgumentException(message); }

        IllegalArgumentException unsupported(String item) { return error("E869: Unsupported in Jasper's Vim regex: " + item); }

        // --- the parser ---

        /** reg: branches separated by \| (inside a group when {@code group}). */
        String alternation(boolean group) {
            StringBuilder out = new StringBuilder(branch());
            while (peek() == magic('|')) {
                skip();
                out.append('|').append(branch());
            }
            if (group && peek() != magic(')')) throw error("E54: Unmatched \\(");
            return out.toString();
        }

        /** regbranch: concats separated by \&; each but the last must match at the same position. */
        String branch() {
            String first = concat();
            if (peek() != magic('&')) return first;
            StringBuilder out = new StringBuilder("(?=").append(first).append(')');
            String last = null;
            while (peek() == magic('&')) {
                skip();
                if (last != null) out.append("(?=").append(last).append(')');
                last = concat();
            }
            return "(?:" + out + last + ")";
        }

        String concat() {
            StringBuilder out = new StringBuilder();
            while (true) {
                int c = peek();
                if (c == 0 || c == magic('|') || c == magic('&') || c == magic(')')) break;
                if (c == magic('Z')) skipKeepStart();
                else if (c == magic('c')) {
                    forceIgnore = true;
                    skipKeepStart();
                } else if (c == magic('C')) {
                    forceMatch = true;
                    skipKeepStart();
                } else if (c == magic('v') || c == magic('m') || c == magic('M') || c == magic('V')) {
                    magic = switch (noMagic(c)) {
                        case 'v' -> ALL;
                        case 'm' -> ON;
                        case 'M' -> OFF;
                        default -> NONE;
                    };
                    skipKeepStart();
                    curchr = -1;
                } else out.append(piece());
            }
            return out.toString();
        }

        /** regpiece: an atom and its multi. */
        String piece() {
            String atom = atom();
            int op = peek();
            if (!isMulti(op)) return atom;
            skip();
            String result;
            switch (noMagic(op)) {
                case '*' -> result = group(atom) + "*";
                case '+' -> result = group(atom) + "+";
                case '=', '?' -> result = group(atom) + "?";
                case '@' -> throw unsupported("\\@");
                default -> result = group(atom) + brace();
            }
            if (isMulti(peek())) throw error("E61: Nested " + (noMagic(peek()) == '*' ? "*" : "\\" + (char) noMagic(peek())));
            return result;
        }

        private static boolean isMulti(int c) {
            return c == magic('*') || c == magic('+') || c == magic('=') || c == magic('?') || c == magic('{') || c == magic('@');
        }

        private static String group(String atom) { return "(?:" + atom + ")"; }

        /** read_limits: {@code \{n,m}}, {@code \{-n,m}} and the rest, after the brace. */
        String brace() {
            boolean lazy = false;
            if (at(pos) == '-') {
                lazy = true;
                pos++;
            }
            int start = pos;
            while (Character.isDigit(at(pos))) pos++;
            String min = src.substring(start, pos);
            String max = min;
            boolean comma = false;
            if (at(pos) == ',') {
                comma = true;
                pos++;
                int s = pos;
                while (Character.isDigit(at(pos))) pos++;
                max = src.substring(s, pos);
            }
            if (at(pos) == '\\') pos++;
            if (at(pos) != '}') throw error("E554: Syntax error in \\{...}");
            pos++;
            resync();
            String java;
            if (min.isEmpty() && !comma) java = "*";
            else if (!comma) java = "{" + min + "}";
            else {
                int lo = min.isEmpty() ? 0 : Integer.parseInt(min);
                if (!max.isEmpty() && Integer.parseInt(max) < lo) {
                    // Vim swaps the limits: \{3,1} is \{1,3}.
                    java = "{" + max + "," + lo + "}";
                } else java = "{" + lo + "," + max + "}";
            }
            return lazy ? java + "?" : java;
        }

        /** regatom. */
        String atom() {
            int c = get();
            if (c == magic('^')) return "^";
            if (c == magic('$')) return "$";
            if (c == magic('<')) return "(?<![" + settings.keyword() + "])(?=[" + settings.keyword() + "])";
            if (c == magic('>')) return "(?<=[" + settings.keyword() + "])(?![" + settings.keyword() + "])";
            if (c == magic('_')) {
                multiline = true;
                char n = at(pos);
                if (n == '^') {
                    pos++;
                    resync();
                    return "^";
                }
                if (n == '$') {
                    pos++;
                    resync();
                    return "$";
                }
                if (n == '[') {
                    pos++;
                    resync();
                    return bracket(true);
                }
                if (n == '.') {
                    pos++;
                    resync();
                    return "[\\s\\S]";
                }
                String cls = classFor(n);
                if (cls == null) throw error("E63: Invalid use of \\_");
                pos++;
                resync();
                return "(?:\\n|" + cls + ")";
            }
            if (c == magic('.')) return ".";
            if (isMagic(c) && classFor(noMagic(c)) != null) return classFor(noMagic(c));
            if (c == magic('n')) {
                multiline = true;
                return "\\n";
            }
            if (c == magic('(')) {
                String name = "g" + ++groups;
                if (groups > 9) throw error("E51: Too many \\(");
                String inner = alternation(true);
                skip();
                return "(?<" + name + ">" + inner + ")";
            }
            if (c == 0 || c == magic('|') || c == magic('&') || c == magic(')')) throw error("E486: Pattern not found: " + src);
            if (c == magic('=') || c == magic('?') || c == magic('+') || c == magic('@') || c == magic('{') || c == magic('*'))
                throw error("E64: " + (noMagic(c) == '*' ? "*" : "\\" + (char) noMagic(c)) + " follows nothing");
            if (c == magic('~')) {
                String last = settings.lastSubstitute();
                return last == null || last.isEmpty() ? "" : Pattern.quote(last);
            }
            if (isMagic(c) && noMagic(c) >= '1' && noMagic(c) <= '9') {
                int n = noMagic(c) - '0';
                if (n > groups) throw error("E65: Illegal back reference");
                return "\\k<g" + n + ">";
            }
            if (c == magic('z')) {
                char n = at(pos);
                pos++;
                resync();
                if (n == 's') {
                    if (hasStart) throw unsupported("\\zs twice");
                    hasStart = true;
                    return "(?<zs>)";
                }
                if (n == 'e') {
                    if (hasEnd) throw unsupported("\\ze twice");
                    hasEnd = true;
                    return "(?<ze>)";
                }
                throw unsupported("\\z" + n);
            }
            if (c == magic('%')) return percent();
            if (c == magic('[')) return bracket(false);
            if (isMagic(c)) {
                int plain = noMagic(c);
                if (Character.isLetterOrDigit(plain) || plain == '_') throw unsupported("\\" + (char) plain);
                return literal(plain);
            }
            return literal(c);
        }

        /** The \% items. */
        String percent() {
            char n = at(pos);
            switch (n) {
                case '(' -> {
                    pos++;
                    resync();
                    prevchr = magic('(');
                    String inner = alternation(true);
                    skip();
                    return "(?:" + inner + ")";
                }
                case '^' -> {
                    pos++;
                    resync();
                    multiline = true;
                    return "\\A";
                }
                case '$' -> {
                    pos++;
                    resync();
                    multiline = true;
                    return "(?=\\n?\\z)";
                }
                case '[' -> {
                    pos++;
                    resync();
                    return optionalSequence();
                }
                case 'd', 'x', 'o', 'u', 'U' -> {
                    pos++;
                    int radix = n == 'd' ? 10 : n == 'o' ? 8 : 16;
                    int limit = n == 'x' ? 2 : n == 'u' ? 4 : n == 'U' ? 8 : Integer.MAX_VALUE;
                    int start = pos;
                    while (pos - start < limit && Character.digit(at(pos), radix) >= 0) pos++;
                    if (pos == start) throw error("E678: Invalid character after \\%[dxouU]");
                    resync();
                    return literal(Integer.parseInt(src.substring(start, pos), radix));
                }
                default -> {
                    int end = pos + 1;
                    while (end < src.length() && (Character.isDigit(src.charAt(end)) || "<>'.".indexOf(src.charAt(end)) >= 0)) end++;
                    throw unsupported("\\%" + src.substring(pos, Math.min(src.length(), Math.max(end, pos + 1) + (end > pos + 1 ? 1 : 0))));
                }
            }
        }

        /** \%[abc]: each of the atoms matches in turn, as far as they go. */
        String optionalSequence() {
            java.util.List<String> atoms = new java.util.ArrayList<>();
            while (true) {
                if (at(pos) == ']') {
                    pos++;
                    resync();
                    break;
                }
                if (at(pos) == 0) throw error("E69: Missing ] after \\%[");
                atoms.add(atom());
                pos = pos - (curchr != -1 ? 0 : 0);
                resync();
            }
            if (atoms.isEmpty()) throw error("E70: Empty \\%[]");
            String out = "";
            for (int i = atoms.size() - 1; i >= 0; i--) out = "(?:" + atoms.get(i) + out + ")?";
            return out;
        }

        /** A bracket expression, from after the {@code [}; {@code [} alone is a literal when there is no {@code ]}. */
        String bracket(boolean newline) {
            int start = pos;
            int end = bracketEnd(pos);
            if (end < 0) {
                // No closing ]: a literal [ (Vim's rule, even for \_[).
                if (newline) throw error("E769: Missing ] after \\_[");
                return "\\[";
            }
            StringBuilder out = new StringBuilder("[");
            int i = start;
            if (at(i) == '^') {
                out.append('^');
                i++;
            }
            if (at(i) == ']' || at(i) == '-') {
                out.append('\\').append(at(i));
                i++;
            }
            while (i < end) {
                char c = src.charAt(i);
                if (c == '[' && at(i + 1) == ':') {
                    int close = src.indexOf(":]", i + 2);
                    String name = src.substring(i + 2, close);
                    out.append(posixClass(name));
                    i = close + 2;
                    continue;
                }
                if (c == '[' && (at(i + 1) == '=' || at(i + 1) == '.')) throw unsupported("[" + at(i + 1) + "…" + at(i + 1) + "]");
                if (c == '-' && i + 1 < end && i > start) {
                    out.append('-');
                    i++;
                    continue;
                }
                if (c == '\\' && i + 1 < end) {
                    char n = src.charAt(i + 1);
                    int[] consumed = new int[1];
                    int code = bracketEscape(i + 1, consumed);
                    if (code >= 0) {
                        out.append(classLiteral(code));
                        i += 1 + consumed[0];
                        continue;
                    }
                    if (n == '\\' || n == ']' || n == '^' || n == '-') {
                        out.append(classLiteral(n));
                        i += 2;
                        continue;
                    }
                    out.append(classLiteral('\\'));
                    i++;
                    continue;
                }
                int cp = src.codePointAt(i);
                out.append(classLiteral(cp));
                i += Character.charCount(cp);
            }
            out.append(']');
            pos = end + 1;
            resync();
            String cls = out.toString();
            if (cls.startsWith("[^")) cls = "[^\\n" + cls.substring(2);
            return newline ? "(?:\\n|" + cls + ")" : cls;
        }

        /** @return the index of the ] closing a bracket expression that starts at {@code i}, or -1 */
        private int bracketEnd(int i) {
            if (at(i) == '^') i++;
            if (at(i) == ']' || at(i) == '-') i++;
            while (i < src.length()) {
                char c = src.charAt(i);
                if (c == ']') return i;
                if (c == '[' && at(i + 1) == ':') {
                    int close = src.indexOf(":]", i + 2);
                    if (close > 0 && posixName(src.substring(i + 2, close))) {
                        i = close + 2;
                        continue;
                    }
                }
                if (c == '\\' && i + 1 < src.length()) i++;
                i++;
            }
            return -1;
        }

        /** \e \t \r \b \n \d123 \o40 \x20 € inside brackets; -1 for anything else. */
        private int bracketEscape(int i, int[] consumed) {
            char n = at(i);
            consumed[0] = 1;
            switch (n) {
                case 'e': return 0x1b;
                case 't': return '\t';
                case 'r': return '\r';
                case 'b': return '\b';
                case 'n': return '\n';
                case 'd': case 'o': case 'x': case 'u': case 'U': {
                    int radix = n == 'd' ? 10 : n == 'o' ? 8 : 16;
                    int limit = n == 'x' ? 2 : n == 'u' ? 4 : n == 'U' ? 8 : 10;
                    int j = i + 1;
                    while (j - i - 1 < limit && Character.digit(at(j), radix) >= 0) j++;
                    if (j == i + 1) return -1;
                    consumed[0] = j - i;
                    return Integer.parseInt(src.substring(i + 1, j), radix);
                }
                default: return -1;
            }
        }

        private static boolean posixName(String name) {
            return switch (name) {
                case "alnum", "alpha", "blank", "cntrl", "digit", "graph", "lower", "print", "punct", "space", "upper", "xdigit",
                     "return", "tab", "escape", "backspace", "ident", "keyword", "fname" -> true;
                default -> false;
            };
        }

        private String posixClass(String name) {
            return switch (name) {
                case "alnum" -> "0-9A-Za-z";
                case "alpha" -> "A-Za-z";
                case "blank" -> " \\t";
                case "cntrl" -> "\\x00-\\x1f\\x7f";
                case "digit" -> "0-9";
                case "graph" -> "!-~";
                case "lower" -> "\\p{javaLowerCase}";
                case "print" -> " -~";
                case "punct" -> "!-/:-@\\[-`{-~";
                case "space" -> "\\t\\n\\x0b\\f\\r ";
                case "upper" -> "\\p{javaUpperCase}";
                case "xdigit" -> "0-9A-Fa-f";
                case "return" -> "\\r";
                case "tab" -> "\\t";
                case "escape" -> "\\x1b";
                case "backspace" -> "\\x08";
                case "ident" -> "0-9A-Za-z_\\u00c0-\\u00ff";
                case "keyword" -> settings.keyword();
                case "fname" -> "0-9A-Za-z/.\\-_+,#$%~=\\u00c0-\\u00ff";
                default -> throw error("E486: Pattern not found: " + src);
            };
        }

        /** The character classes \s \d \w and the rest; null for another letter. */
        String classFor(int c) {
            String k = settings.keyword();
            return switch (c) {
                case 's' -> "[ \\t]";
                case 'S' -> "[^ \\t\\n]";
                case 'd' -> "[0-9]";
                case 'D' -> "[^0-9\\n]";
                case 'w' -> "[0-9A-Za-z_]";
                case 'W' -> "[^0-9A-Za-z_\\n]";
                case 'a' -> "[A-Za-z]";
                case 'A' -> "[^A-Za-z\\n]";
                case 'l' -> "[a-z]";
                case 'L' -> "[^a-z\\n]";
                case 'u' -> "[A-Z]";
                case 'U' -> "[^A-Z\\n]";
                case 'x' -> "[0-9A-Fa-f]";
                case 'X' -> "[^0-9A-Fa-f\\n]";
                case 'o' -> "[0-7]";
                case 'O' -> "[^0-7\\n]";
                case 'h' -> "[A-Za-z_]";
                case 'H' -> "[^A-Za-z_\\n]";
                case 'k' -> "[" + k + "]";
                case 'K' -> "[" + k + "&&[^0-9]]";
                case 'i' -> "[0-9A-Za-z_\\u00c0-\\u00ff]";
                case 'I' -> "[A-Za-z_\\u00c0-\\u00ff]";
                case 'f' -> "[0-9A-Za-z/.\\-_+,#$%~=\\u00c0-\\u00ff]";
                case 'F' -> "[A-Za-z/.\\-_+,#$%~=\\u00c0-\\u00ff]";
                case 'p' -> "[^\\x00-\\x1f\\x7f-\\xa0\\n]";
                case 'P' -> "[^\\x00-\\x1f\\x7f-\\xa0\\n0-9]";
                default -> null;
            };
        }

        static String literal(int c) {
            if (c < 0x20 || c == 0x7f) return String.format("\\x{%x}", c);
            if (Character.isLetterOrDigit(c) || c > 0x7f) return new String(Character.toChars(c));
            return "\\" + (char) c;
        }

        static String classLiteral(int c) {
            if (c < 0x20 || c == 0x7f) return String.format("\\x{%x}", c);
            if (Character.isLetterOrDigit(c) || c > 0x7f) return new String(Character.toChars(c));
            return "\\" + (char) c;
        }
    }
}
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 144 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest'
```

Expected: 1130 tests pass, among them the three new repeat-undo cases.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`: the Editor compiles without warnings (`-Xlint:all`).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/vim-v2a
git add plugins/editor/src/main/java/dev/jasper/editor/vim plugins/editor/src/test/java/dev/jasper/editor/vim/SearchPatternsTest.java plugins/editor/src/test/resources/vim/corpus/repeat-undo.case
git commit -m "Keep Vim's search and substitute patterns apart and prepare marks and undo for Ex

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: `:set`: do_set and the three scopes

**Files:**
- Replace: `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/OptionsTest.java`

**Interfaces:**
- Produces (used by Task 3): `Options.Scope` (`BOTH`, `LOCAL`, `GLOBAL`) and `void set(String arguments, Scope scope, int columns, List<String> shown)`, which applies `:set`, `:setlocal` or `:setglobal` arguments and adds the lines they show; it throws `IllegalArgumentException` with Vim's message at the first bad argument (`E518: Unknown option: foo`), after the earlier ones took effect. `apply(String)` stays, for the corpus's `set:` field.

A port of option.c's do_set: `opt`, `noopt`, `invopt`, `opt!`, `opt&`, `opt<`, `opt?`, `opt=val`, `opt:val`, `+=`, `-=`, `^=` (with Vim's comma, no-duplicate and flag-list rules per option), a number option alone shows its value, `all` and `all&`, and `:set` alone lists what differs from the defaults in Vim's columns. The string options are checked as Vim checks them (`E474`, `E539`). 'hlsearch' is on by default now, and 'gdefault' is new. `sw!` now fails with `E488: Trailing characters: sw!`, as in Vim.

- [ ] **Step 1: Write the failing test**

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/vim/OptionsTest.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionsTest {
    @Test void defaultsAreVims() {
        var options = new Options();
        assertThat(options.number("tabstop")).isEqualTo(8);
        assertThat(options.flag("joinspaces")).isTrue();
        assertThat(options.string("whichwrap")).isEqualTo("b,s");
        assertThat(options.shiftwidth()).isEqualTo(8);
    }

    @Test void appliesSetArguments() {
        var options = new Options().local();
        options.apply("sw=4 et noai ts:2 isk+=- nf-=octal");
        assertThat(options.number("shiftwidth")).isEqualTo(4);
        assertThat(options.flag("expandtab")).isTrue();
        assertThat(options.flag("autoindent")).isFalse();
        assertThat(options.number("tabstop")).isEqualTo(2);
        assertThat(options.string("iskeyword")).endsWith(",-");
        assertThat(options.string("nrformats")).isEqualTo("bin,hex");
        options.apply("invet sw& js!");
        assertThat(options.flag("expandtab")).isFalse();
        assertThat(options.number("shiftwidth")).isEqualTo(8);
        assertThat(options.flag("joinspaces")).isFalse();
        options.apply("sw+=2 sw^=3");
        assertThat(options.number("shiftwidth")).isEqualTo(30);
        assertThat(options.shiftwidth()).isEqualTo(30);
        options.apply("sw=0");
        assertThat(options.shiftwidth()).isEqualTo(2);
    }

    @Test void reportsVimsErrors() {
        var options = new Options();
        assertThatThrownBy(() -> options.apply("nosuch")).hasMessage("E518: Unknown option: nosuch");
        assertThatThrownBy(() -> options.apply("sw=x")).hasMessage("E521: Number required after =: sw=x");
        assertThatThrownBy(() -> options.apply("et=1")).hasMessage("E474: Invalid argument: et=1");
        assertThatThrownBy(() -> options.apply("sw!")).hasMessage("E488: Trailing characters: sw!");
        assertThatThrownBy(() -> options.apply("ts=0")).hasMessageStartingWith("E487");
        assertThatThrownBy(() -> options.apply("isk=999")).hasMessageStartingWith("E474");
    }

    @Test void localValuesStayWithTheirEditorWhileSetAlsoSetsTheGlobalValue() {
        var global = new Options();
        var first = global.local();
        var second = global.local();
        first.set("shiftwidth", 2);
        assertThat(global.number("shiftwidth")).isEqualTo(2);
        assertThat(global.local().number("shiftwidth")).as("a new editor starts from the global value").isEqualTo(2);
        second.setLocal("shiftwidth", 6);
        assertThat(first.number("shiftwidth")).isEqualTo(2);
        assertThat(second.number("shiftwidth")).isEqualTo(6);
        first.set("joinspaces", false);
        assertThat(second.flag("joinspaces")).as("a global option is shared").isFalse();
    }

    @Test void tellsListenersAboutChanges() {
        var options = new Options().local();
        List<String> heard = new ArrayList<>();
        options.onChanged(() -> heard.add("changed"));
        options.set("tabstop", 4);
        options.set("tabstop", 4);
        assertThat(heard).hasSize(1);
        assertThat(options.has("whichwrap", "s")).isTrue();
    }

    @Test void setShowsValuesAndWorksOnEachScope() {
        var global = new Options();
        var local = global.local();
        List<String> shown = new ArrayList<>();
        local.set("sw? et ts", Options.Scope.BOTH, 80, shown);
        assertThat(shown).containsExactly("  shiftwidth=8", "  tabstop=8");
        assertThat(local.flag("expandtab")).isTrue();
        local.set("sw=4", Options.Scope.BOTH, 80, shown);
        local.set("sw=3", Options.Scope.LOCAL, 80, shown);
        shown.clear();
        local.set("sw?", Options.Scope.GLOBAL, 80, shown);
        local.set("sw?", Options.Scope.LOCAL, 80, shown);
        assertThat(shown).containsExactly("  shiftwidth=4", "  shiftwidth=3");
        local.set("sw=6", Options.Scope.GLOBAL, 80, shown);
        assertThat(local.number("shiftwidth")).as(":setglobal leaves the local value").isEqualTo(3);
        assertThat(global.local().number("shiftwidth")).isEqualTo(6);
        shown.clear();
        local.set("", Options.Scope.BOTH, 80, shown);
        assertThat(shown.getFirst()).isEqualTo("--- Options ---");
        assertThat(String.join("\n", shown)).contains("expandtab").contains("shiftwidth=3");
        assertThatThrownBy(() -> local.set("sw=2 foo sw=5", Options.Scope.BOTH, 80, new ArrayList<>())).hasMessage("E518: Unknown option: foo ");
        assertThat(local.number("shiftwidth")).as("the arguments before the error took effect").isEqualTo(2);
        local.set("fo+=ct nf-=octal ww+=h,l", Options.Scope.BOTH, 80, shown);
        assertThat(local.string("formatoptions")).isEqualTo("qct");
        assertThat(local.string("nrformats")).isEqualTo("bin,hex");
        assertThat(local.string("whichwrap")).isEqualTo("b,s,h,l");
        assertThatThrownBy(() -> local.apply("fo=tcqz")).hasMessage("E539: Illegal character <z>: fo=tcqz");
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.OptionsTest'
```

Expected: compilation fails: `Options.Scope` does not exist.

- [ ] **Step 3: Replace `Options`**

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Vim options (spec 3.8). The global instance holds global values; each editor has a local instance whose
 * buffer- and window-local options keep their own values. Setting an option on a local instance sets it there and
 * globally, as {@code :set} does. Defaults are Vim's with {@code -u NONE -N}, except where the Editor supplies them
 * ({@link EditorDefaults}) and 'showcmd', 'incsearch' and 'hlsearch', which are on (spec 3.8).
 */
public final class Options {
    enum Type { BOOLEAN, NUMBER, STRING }

    /**
     * @param name the full name
     * @param abbreviation the short name, or the full name when there is none
     * @param type the value type
     * @param fallback the default
     * @param local whether each editor keeps its own value
     */
    record Def(String name, String abbreviation, Type type, Object fallback, boolean local) { }

    /** Every option this version knows. */
    static final List<Def> DEFS = List.of(
        new Def("autoindent", "ai", Type.BOOLEAN, false, true),
        new Def("backspace", "bs", Type.STRING, "indent,eol,start", false),
        new Def("clipboard", "cb", Type.STRING, "", false),
        new Def("comments", "com", Type.STRING, "s1:/*,mb:*,ex:*/,://,b:#,:%,:XCOMM,n:>,fb:-", true),
        new Def("expandtab", "et", Type.BOOLEAN, false, true),
        new Def("foldopen", "fdo", Type.STRING, "block,hor,mark,percent,quickfix,search,tag,undo", false),
        new Def("formatoptions", "fo", Type.STRING, "tcq", true),
        new Def("gdefault", "gd", Type.BOOLEAN, false, false),
        new Def("hlsearch", "hls", Type.BOOLEAN, true, false),
        new Def("ignorecase", "ic", Type.BOOLEAN, false, false),
        new Def("incsearch", "is", Type.BOOLEAN, true, false),
        new Def("iskeyword", "isk", Type.STRING, "@,48-57,_,192-255", true),
        new Def("joinspaces", "js", Type.BOOLEAN, true, false),
        new Def("matchpairs", "mps", Type.STRING, "(:),{:},[:]", true),
        new Def("nrformats", "nf", Type.STRING, "bin,octal,hex", true),
        new Def("report", "report", Type.NUMBER, 2, false),
        new Def("scrolljump", "sj", Type.NUMBER, 1, false),
        new Def("scrolloff", "so", Type.NUMBER, 0, false),
        new Def("shiftround", "sr", Type.BOOLEAN, false, false),
        new Def("shiftwidth", "sw", Type.NUMBER, 8, true),
        new Def("showcmd", "sc", Type.BOOLEAN, true, false),
        new Def("showmode", "smd", Type.BOOLEAN, true, false),
        new Def("sidescroll", "ss", Type.NUMBER, 0, false),
        new Def("sidescrolloff", "siso", Type.NUMBER, 0, false),
        new Def("smartcase", "scs", Type.BOOLEAN, false, false),
        new Def("softtabstop", "sts", Type.NUMBER, 0, true),
        new Def("startofline", "sol", Type.BOOLEAN, true, false),
        new Def("tabstop", "ts", Type.NUMBER, 8, true),
        new Def("textwidth", "tw", Type.NUMBER, 0, true),
        new Def("tildeop", "top", Type.BOOLEAN, false, false),
        new Def("whichwrap", "ww", Type.STRING, "b,s", false),
        new Def("wrap", "wrap", Type.BOOLEAN, true, true),
        new Def("wrapscan", "ws", Type.BOOLEAN, true, false));

    private final Options global;
    private final Map<String, Object> values = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private String keywordSpec;
    private Keywords keywords;

    /** A global instance with Vim's defaults. */
    public Options() { this(null); }

    private Options(Options global) { this.global = global; }

    /** @return a local instance for one editor, backed by this global one */
    Options local() {
        if (global != null) throw new IllegalStateException("Only the global options have local copies");
        return new Options(this);
    }

    /** @param listener runs after any value of this instance changes */
    void onChanged(Runnable listener) { listeners.add(listener); }

    boolean flag(String name) { return (Boolean) get(name); }

    int number(String name) { return (Integer) get(name); }

    String string(String name) { return (String) get(name); }

    /** @return 'shiftwidth', or 'tabstop' when it is 0, as Vim's get_sw_value */
    int shiftwidth() {
        int sw = number("shiftwidth");
        return sw == 0 ? number("tabstop") : sw;
    }

    /** @return whether a comma-separated option lists {@code item}, such as 'clipboard' holding {@code unnamed} */
    boolean has(String name, String item) { return List.of(string(name).split(",")).contains(item); }

    Keywords keywords() {
        String spec = string("iskeyword");
        if (!spec.equals(keywordSpec)) {
            keywords = Keywords.parse(spec);
            keywordSpec = spec;
        }
        return keywords;
    }

    Object get(String name) {
        Def def = def(name).orElseThrow(() -> new IllegalArgumentException("E518: Unknown option: " + name));
        if (global != null && def.local() && values.containsKey(def.name())) return values.get(def.name());
        Options root = global != null ? global : this;
        return root.values.getOrDefault(def.name(), def.fallback());
    }

    /** Sets a value here and, for a local instance, globally too (as {@code :set}). */
    void set(String name, Object value) {
        Def def = def(name).orElseThrow(() -> new IllegalArgumentException("E518: Unknown option: " + name));
        Object checked = check(def, value);
        if (global != null) {
            global.put(def, checked);
            if (def.local()) put(def, checked);
        } else put(def, checked);
    }

    /** Sets a value in this local instance only (as {@code :setlocal}); a global option is set globally. */
    void setLocal(String name, Object value) {
        Def def = def(name).orElseThrow(() -> new IllegalArgumentException("E518: Unknown option: " + name));
        Object checked = check(def, value);
        if (global != null && !def.local()) global.put(def, checked);
        else put(def, checked);
    }

    /** Which values {@code :set}, {@code :setlocal} and {@code :setglobal} read and write (Vim's opt_flags). */
    enum Scope { BOTH, LOCAL, GLOBAL }

    /** A comma-separated list (P_COMMA), whose items may not repeat (P_NODUP), or a list of one-letter flags (P_FLAGLIST). */
    private static final int COMMA = 1, ONECOMMA = 2, NODUP = 4, FLAGLIST = 8;

    private static int flags(String name) {
        return switch (name) {
            case "backspace", "clipboard", "comments", "foldopen", "matchpairs", "nrformats" -> COMMA | ONECOMMA | NODUP;
            case "iskeyword" -> COMMA | NODUP;
            case "formatoptions" -> FLAGLIST;
            case "whichwrap" -> COMMA | ONECOMMA | FLAGLIST;
            default -> 0;
        };
    }

    /**
     * Applies {@code :set} arguments as {@link #set(String, Scope, int, List)} does, dropping what they show.
     *
     * @throws IllegalArgumentException with Vim's message for an unknown option or a bad value
     */
    void apply(String arguments) { set(arguments, Scope.BOTH, 80, new ArrayList<>()); }

    /**
     * do_set: {@code :set}, {@code :setlocal} or {@code :setglobal} with its arguments. Each argument is
     * {@code name}, {@code noname}, {@code invname}, {@code name!}, {@code name&}, {@code name<},
     * {@code name=value}, {@code name:value}, {@code name+=value}, {@code name-=value}, {@code name^=value} or
     * {@code name?}; a number or string option without a value is shown. {@code all} shows every option,
     * {@code all&} resets them, and no argument shows those that differ from their default.
     *
     * @param columns the window's width, for the columns of a listing
     * @param shown receives the lines shown
     * @throws IllegalArgumentException with Vim's message at the first bad argument, after the earlier ones took effect
     */
    void set(String arguments, Scope scope, int columns, List<String> shown) {
        String arg = arguments;
        int i = skipWhite(arg, 0);
        if (i >= arg.length()) {
            show(false, scope, columns, shown);
            return;
        }
        while (i < arg.length()) {
            if (arg.startsWith("all", i) && !(i + 3 < arg.length() && isAlpha(arg.charAt(i + 3)))) {
                i += 3;
                if (i < arg.length() && arg.charAt(i) == '&') {
                    i++;
                    for (Def def : DEFS) setScoped(def, def.fallback(), scope);
                } else show(true, scope, columns, shown);
            } else {
                int start = i;
                String error = null;
                try {
                    i = one(arg, i, scope, shown);
                } catch (IllegalArgumentException bad) {
                    error = bad.getMessage();
                }
                // Advance to the next argument: skip to white space (over backslashes), then one "=val" after it.
                int next = start;
                for (int n = 0; n < 2; n++) {
                    while (next < arg.length() && !Character.isWhitespace(arg.charAt(next))) {
                        if (arg.charAt(next++) == '\\' && next < arg.length()) next++;
                    }
                    next = skipWhite(arg, next);
                    if (next >= arg.length() || arg.charAt(next) != '=') break;
                }
                if (error != null) throw new IllegalArgumentException(error + ": " + arg.substring(start, next));
                i = Math.max(i, next);
            }
            i = skipWhite(arg, i);
        }
    }

    /** do_set_option: one argument from {@code i}; returns where it ends. */
    private int one(String arg, int i, Scope scope, List<String> shown) {
        char prefix = 0;
        if (arg.startsWith("no", i) && !arg.startsWith("novice", i)) {
            prefix = 'n';
            i += 2;
        } else if (arg.startsWith("inv", i)) {
            prefix = 'i';
            i += 3;
        }
        int len = 0;
        while (i + len < arg.length() && (isAlnum(arg.charAt(i + len)) || arg.charAt(i + len) == '_')) len++;
        Def def = def(arg.substring(i, i + len)).orElse(null);
        char afterchar = i + len < arg.length() ? arg.charAt(i + len) : 0;
        while (i + len < arg.length() && isWhite(arg.charAt(i + len))) len++;
        char op = 0;
        if (i + len + 1 < arg.length() && "+^-".indexOf(arg.charAt(i + len)) >= 0 && arg.charAt(i + len + 1) == '=') {
            op = arg.charAt(i + len);
            len++;
        }
        char nextchar = i + len < arg.length() ? arg.charAt(i + len) : 0;
        if (def == null) throw new IllegalArgumentException("E518: Unknown option");
        if ("?=:!&<".indexOf(nextchar) >= 0 && nextchar != 0) {
            i += len;
            if (nextchar == '&' && arg.startsWith("vi", i + 1)) i += arg.startsWith("vim", i + 1) ? 3 : 2;
            if ("?!&<".indexOf(nextchar) >= 0 && i + 1 < arg.length() && !isWhite(arg.charAt(i + 1)))
                throw new IllegalArgumentException("E488: Trailing characters");
        }
        if (nextchar == '?' || prefix == 0 && "=:&<".indexOf(nextchar) < 0 && def.type() != Type.BOOLEAN) {
            shown.add(showOne(def, scope));
            if (nextchar != '?' && nextchar != 0 && !isWhite(afterchar)) throw new IllegalArgumentException("E488: Trailing characters");
            return i + len;
        }
        Object old = value(def, scope);
        if (def.type() == Type.BOOLEAN) {
            if (nextchar == '=' || nextchar == ':') throw new IllegalArgumentException("E474: Invalid argument");
            boolean value;
            if (nextchar == '!') value = !(Boolean) old;
            else if (nextchar == '&') value = (Boolean) def.fallback();
            else if (nextchar == '<') value = (Boolean) global().get(def.name());
            else {
                if (nextchar != 0 && !isWhite(afterchar)) throw new IllegalArgumentException("E488: Trailing characters");
                value = prefix == 'i' ? !(Boolean) old : prefix != 'n';
            }
            setScoped(def, value, scope);
            return nextchar == 0 ? i + len : i + 1;
        }
        if ("=:&<".indexOf(nextchar) < 0 || nextchar == 0 || prefix != 0) throw new IllegalArgumentException("E474: Invalid argument");
        int p = i + 1;
        if (def.type() == Type.NUMBER) {
            int value;
            if (nextchar == '&') value = (Integer) def.fallback();
            else if (nextchar == '<') value = (Integer) global().get(def.name());
            else {
                int end = p;
                while (end < arg.length() && !isWhite(arg.charAt(end))) end++;
                Long parsed = parseNumber(arg.substring(p, end));
                if (parsed == null) throw new IllegalArgumentException("E521: Number required after =");
                long v = parsed, current = (Integer) old;
                v = switch (op) { case '+' -> current + v; case '^' -> current * v; case '-' -> current - v; default -> v; };
                value = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v));
                p = end;
            }
            setScoped(def, checkNumber(def, value), scope);
            return p;
        }
        String value;
        if (nextchar == '&') value = (String) def.fallback();
        else if (nextchar == '<') value = (String) global().get(def.name());
        else {
            StringBuilder copied = new StringBuilder();
            while (p < arg.length() && !isWhite(arg.charAt(p))) {
                if (arg.charAt(p) == '\\' && p + 1 < arg.length()) p++;
                copied.append(arg.charAt(p++));
            }
            value = combine((String) old, copied.toString(), op, flags(def.name()));
        }
        setScoped(def, checkString(def, value), scope);
        return p;
    }

    /** stropt_get_newval: adding, prepending or removing an item, as the option's list flags say. */
    private static String combine(String origval, String newval, char op, int flags) {
        if (op == 0) return (flags & FLAGLIST) != 0 ? dedupFlags(newval, flags) : newval;
        int s = -1;
        if (op == '-' || (flags & NODUP) != 0) {
            s = findDup(origval, newval, flags);
            if ((op == '+' || op == '^') && s >= 0) return origval;
            if (s < 0) s = origval.length();
        }
        String result;
        if (op == '+' || op == '^') {
            boolean comma = (flags & COMMA) != 0 && !origval.isEmpty() && !newval.isEmpty();
            if (op == '+') {
                String o = origval;
                if (comma && o.length() > 1 && (flags & ONECOMMA) != 0 && o.endsWith(",") && o.charAt(o.length() - 2) != '\\')
                    o = o.substring(0, o.length() - 1);
                result = o + (comma ? "," : "") + newval;
            } else result = newval + (comma ? "," : "") + origval;
        } else {
            result = origval;
            if (s < origval.length()) {
                int from = s, len = newval.length();
                if ((flags & COMMA) != 0) {
                    if (from == 0) {
                        if (len < origval.length() && origval.charAt(len) == ',') len++;
                    } else {
                        from--;
                        len++;
                    }
                }
                result = origval.substring(0, from) + origval.substring(Math.min(origval.length(), from + len));
            }
        }
        return (flags & FLAGLIST) != 0 ? dedupFlags(result, flags) : result;
    }

    /** find_dup_item: where {@code item} is in the list, or -1. */
    private static int findDup(String origval, String item, int flags) {
        int bs = 0;
        boolean comma = (flags & COMMA) != 0;
        for (int s = 0; s < origval.length(); s++) {
            if ((!comma || s == 0 || origval.charAt(s - 1) == ',' && (bs & 1) == 0) && origval.startsWith(item, s)
                && (!comma || s + item.length() == origval.length() || origval.charAt(s + item.length()) == ','))
                return s;
            if (s > 1 && origval.charAt(s - 1) == '\\' && origval.charAt(s - 2) != ',' || s == 1 && origval.charAt(0) == '\\') bs++;
            else bs = 0;
        }
        return -1;
    }

    /** stropt_remove_dupflags: a flag that appears again later goes. */
    private static String dedupFlags(String value, int flags) {
        StringBuilder s = new StringBuilder(value);
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((flags & ONECOMMA) != 0) {
                if (c != ',' && i + 1 < s.length() && s.charAt(i + 1) == ',' && s.indexOf(String.valueOf(c), i + 2) >= 0) {
                    s.delete(i, i + 2);
                    continue;
                }
            } else if (((flags & COMMA) == 0 || c != ',') && s.indexOf(String.valueOf(c), i + 1) >= 0) {
                s.deleteCharAt(i);
                continue;
            }
            i++;
        }
        return s.toString();
    }

    /** vim_str2nr with STR2NR_ALL: decimal, {@code 0x} hex, {@code 0b} binary, {@code 0o} or leading-zero octal. */
    private static Long parseNumber(String text) {
        String t = text;
        boolean negative = t.startsWith("-");
        if (negative) t = t.substring(1);
        if (t.isEmpty() || !Character.isDigit(t.charAt(0))) return null;
        int radix = 10;
        String digits = t;
        if (t.length() > 2 && t.charAt(0) == '0' && "xX".indexOf(t.charAt(1)) >= 0) { radix = 16; digits = t.substring(2); }
        else if (t.length() > 2 && t.charAt(0) == '0' && "bB".indexOf(t.charAt(1)) >= 0) { radix = 2; digits = t.substring(2); }
        else if (t.length() > 2 && t.charAt(0) == '0' && "oO".indexOf(t.charAt(1)) >= 0) { radix = 8; digits = t.substring(2); }
        else if (t.length() > 1 && t.charAt(0) == '0' && t.chars().allMatch(ch -> ch >= '0' && ch <= '7')) radix = 8;
        try {
            long v = Long.parseLong(digits, radix);
            return negative ? -v : v;
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    /** @return the value {@code :setlocal} or {@code :setglobal} reads: the local one, the global one, or the effective one */
    private Object value(Def def, Scope scope) {
        if (scope == Scope.GLOBAL) return global().get(def.name());
        return get(def.name());
    }

    private Options global() { return global != null ? global : this; }

    /** Sets a value as {@code :set} (both), {@code :setlocal} (this editor's) or {@code :setglobal} (the global one). */
    private void setScoped(Def def, Object value, Scope scope) {
        switch (scope) {
            case BOTH -> set(def.name(), value);
            case LOCAL -> setLocal(def.name(), value);
            case GLOBAL -> {
                // The editor keeps its own value of a local option (each Vim window and buffer has a copy).
                if (global != null && def.local() && !values.containsKey(def.name())) values.put(def.name(), get(def.name()));
                global().put(def, check(def, value));
            }
        }
    }

    private static int checkNumber(Def def, int value) {
        if (value < 0 || def.name().equals("tabstop") && value == 0) throw new IllegalArgumentException("E487: Argument must be positive");
        return value;
    }

    /** did_set_string_option's checks for the string options Jasper knows. */
    private static String checkString(Def def, String value) {
        switch (def.name()) {
            case "iskeyword" -> {
                try { Keywords.parse(value); }
                catch (IllegalArgumentException bad) { throw new IllegalArgumentException("E474: Invalid argument"); }
            }
            case "formatoptions" -> flagList(value, "tcro/q2vlb1mMBn,]:wjp");
            case "whichwrap" -> flagList(value, "bshl<>[],~");
            case "backspace" -> {
                if (!(value.length() == 1 && value.charAt(0) >= '0' && value.charAt(0) <= '3')) items(value, List.of("indent", "eol", "start", "nostop"));
            }
            case "clipboard" -> items(value, List.of("unnamed", "unnamedplus", "autoselect", "autoselectplus", "autoselectml", "html", "exclude"));
            case "foldopen" -> items(value, List.of("all", "block", "hor", "mark", "percent", "quickfix", "search", "tag", "insert", "undo", "jump"));
            case "nrformats" -> items(value, List.of("alpha", "octal", "hex", "bin", "unsigned", "blank"));
            case "matchpairs" -> {
                for (String pair : value.split(",", -1)) {
                    if (value.isEmpty()) break;
                    if (pair.codePointCount(0, pair.length()) != 3 || pair.charAt(pair.offsetByCodePoints(0, 1)) != ':')
                        throw new IllegalArgumentException("E474: Invalid argument");
                }
            }
            default -> { }
        }
        return value;
    }

    private static void flagList(String value, String allowed) {
        for (int i = 0; i < value.length(); i++)
            if (allowed.indexOf(value.charAt(i)) < 0) throw new IllegalArgumentException("E539: Illegal character <" + value.charAt(i) + ">");
    }

    private static void items(String value, List<String> allowed) {
        if (value.isEmpty()) return;
        for (String item : value.split(",", -1))
            if (!allowed.contains(item) && !item.startsWith("exclude:")) throw new IllegalArgumentException("E474: Invalid argument");
    }

    /** showoneopt: {@code noname}, {@code   name} or {@code   name=value}. */
    private String showOne(Def def, Scope scope) {
        Object value = value(def, scope);
        if (def.type() == Type.BOOLEAN) return ((Boolean) value ? "  " : "no") + def.name();
        return "  " + def.name() + "=" + value;
    }

    /**
     * showoptions: the options that differ from their default ({@code all}: every option), the short ones in
     * columns of 20 characters and then the long ones one to a line.
     */
    private void show(boolean all, Scope scope, int columns, List<String> shown) {
        shown.add(switch (scope) {
            case GLOBAL -> "--- Global option values ---";
            case LOCAL -> "--- Local option values ---";
            case BOTH -> "--- Options ---";
        });
        List<Def> items = new ArrayList<>();
        for (Def def : DEFS) if (all || !value(def, scope).equals(def.fallback())) items.add(def);
        List<String> shortItems = new ArrayList<>(), longItems = new ArrayList<>();
        for (Def def : items) {
            String text = showOne(def, scope);
            int len = def.type() == Type.BOOLEAN ? 1 : def.name().length() + String.valueOf(value(def, scope)).length() + 1;
            (len <= 17 ? shortItems : longItems).add(text);
        }
        int cols = Math.max(1, columns / 20);
        int rows = (shortItems.size() + cols - 1) / cols;
        for (int row = 0; row < rows; row++) {
            StringBuilder line = new StringBuilder();
            for (int i = row; i < shortItems.size(); i += rows) {
                int col = (i - row) / rows * 20;
                while (line.length() < col) line.append(' ');
                line.append(shortItems.get(i));
            }
            shown.add(line.toString());
        }
        shown.addAll(longItems);
    }

    private static int skipWhite(String s, int i) {
        while (i < s.length() && isWhite(s.charAt(i))) i++;
        return i;
    }

    private static boolean isWhite(char c) { return c == ' ' || c == '\t'; }

    private static boolean isAlpha(char c) { return c < 128 && Character.isLetter(c); }

    private static boolean isAlnum(char c) { return c < 128 && Character.isLetterOrDigit(c); }

    static Optional<Def> def(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return DEFS.stream().filter(def -> def.name().equals(lower) || def.abbreviation().equals(lower)).findFirst();
    }

    private void put(Def def, Object value) {
        Object old = values.put(def.name(), value);
        if (!value.equals(old)) for (Runnable listener : List.copyOf(listeners)) listener.run();
    }



    private static Object check(Def def, Object value) {
        return switch (def.type()) {
            case BOOLEAN -> (Boolean) value;
            case NUMBER -> {
                int number = (Integer) value;
                if (number < 0 || def.name().equals("tabstop") && number == 0) throw new IllegalArgumentException("E487: Argument must be positive: " + def.name() + "=" + number);
                yield number;
            }
            case STRING -> {
                String text = (String) value;
                if (def.name().equals("iskeyword")) Keywords.parse(text);
                yield text;
            }
        };
    }

}
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 143 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest'
```

Expected: 1130 tests pass.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/vim-v2a
git add plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java plugins/editor/src/test/java/dev/jasper/editor/vim/OptionsTest.java
git commit -m "Port Vim's :set syntax to the options

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: The command line, ranges, and the line and listing commands

**Files:**
- Create in `plugins/editor/src/main/java/dev/jasper/editor/vim/`: `ExTable.java`, `ExCommand.java`, `ExInfo.java`, `ExLines.java`, `Ex.java`
- Replace in `plugins/editor/src/main/java/dev/jasper/editor/vim/`: `VimEditor.java`, `Machine.java`, `VimCommands.java`, `Operators.java`, `Put.java`, `ui/VimBar.java`
- Replace in `plugins/editor/src/test/java/dev/jasper/editor/vim/`: `CorpusCase.java`, `CorpusFile.java`, `CorpusRunner.java`, `RecordCorpus.java`, `VimDocsTest.java`, `ui/VimBarTest.java`
- Modify: `plugins/editor/src/test/java/dev/jasper/editor/vim/VimCorpusTest.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/ExTest.java` (new)
- Create: `plugins/editor/src/test/resources/vim/corpus/ex-range.case`, `plugins/editor/src/test/resources/vim/corpus/ex-lines.case`, `plugins/editor/src/test/resources/vim/corpus/ex-info.case`
- Modify: `docs/vim-divergences.md`; regenerate `docs/editor-vim-reference.md`

**Interfaces:**
- Consumes: Tasks 1 and 2.
- Produces (used by Tasks 4 and 5): `Ex` with `execute(String)`, `boolean runLine(String)` (do_cmdline: false after an error), `later(Runnable)` (a continuation for after an open question), `fail(VimFailure)`, `setpcmark()`, `beginline(int lnum, boolean sol)`, `globalBusy()`/`globalBusy(boolean)`, `typed()`, fields `subNsubs`, `subNlines`, `globalNeedBeginline`, `normalDepth`; `Ex.Def(name, flags, addr, description, handler)` with `abbreviation()`; `static List<Ex.Def> implemented()`; the flag constants `RANGE`, `BANG`, `EXTRA`, `DFLALL`, `WHOLEFOLD`, `NEEDARG`, `TRLBAR`, `REGSTR`, `COUNT`, `NOTRLCOM`, `ZEROR`, `CTRLV`, `FLAGS` and `PRINT`, `LIST`, `NUMBER`. `ExCommand` (fields `line1`, `line2`, `addrCount`, `forceit`, `regname`, `amount`, `arg`, `flags`, `nextcmd`, `typed`; `name()`, `context()`). `ExInfo.line(VimEditor, int lnum, boolean number, boolean list)` and `mayPrint(ExCommand)`. `VimEditor.say(String)`, `msgmore(int)`, `ask(String, Consumer<KeyInput>)`, `prompting()`, `confirmMatch(int[])`, `ex()`; public `output()`, `prompt()` and the record `VimEditor.OutputLine(text, error)`.

How the pieces fit:
- **Running a line.** `:` (Normal mode, with a count `:.,.+N`; Visual mode `:'<,'>`, the cursor going to the area's start as op_colon puts it) opens the command line; `<CR>` adds a typed line to the `:` history and runs `Ex.execute`, which brackets it in one undo step and gathers what it says. `runLine` parses and runs one command at a time (`one`, then `finish`, a port of do_one_cmd's two halves) and stops at an error.
- **Names and ranges.** `find` resolves the name as find_ex_command does (`:k`, the one-letter `:s` forms, `:dl`/`:dp`); a name Vim knows but Jasper does not run fails with E319. `parseAddresses` and `address` port parse_cmd_address and get_address, including `;` (which moves the cursor, to 0 as well), searches from the previous address, closed folds, and `'<`/`*`.
- **Questions.** A command that asks (`VimEditor.ask`) returns with the question open. `runLine` then leaves the rest of the line as a continuation (`later`), and `execute` keeps the run's continuations and the end of its undo step in a frame; the answer runs the asking command's handler, then the frame. `:normal` settles a question from its keys, or with `<Esc>` when they run out, as Vim's :normal gets `<Esc>`.
- **Output.** `VimEditor.startCollecting`/`stopCollecting` gather the messages of a run: one line becomes the message, several the output panel (`output()`), which the next key takes away (`<CR>`, `<Space>`, `<Esc>` only take it away). After a failed line only the error stays. `VimBar` shows the panel above the strip and the question or "Press ENTER or type command to continue" in the strip.
- **Line commands** reuse the Normal-mode operators, put and join (`Operators`, `Put`, `NormalCommands.joinLines`); `Operators` and `Put` now give the 'report' messages, for Normal mode as well.
- **The corpus** compares what a case's keys said when it has `output: yes`: `CorpusRunner` reads `VimEditor.messageLog()`, and `RecordCorpus` wraps the keys in `:redir`, dropping empty lines and the script-error headers Vim adds while sourcing. The replay now reads the mode before closing the editor. `everyCommandHasACase` also requires a case for every Ex command, and the reference gains an "Ex commands" section.

- [ ] **Step 1: Write the failing tests**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/ExTest.java`:

```java
package dev.jasper.editor.vim;

import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Ex command line where the corpus cannot reach: the recorder's Vim runs its keys from feedkeys(), where Vim
 * never waits at the hit-enter prompt, so the output panel, the questions and the history are checked here.
 */
class ExTest {
    private final Vim vim = new Vim(VimHost.detached());

    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private VimEditor editor(VimTextArea area, String text) {
        area.setText(text);
        area.discardAllEdits();
        area.setCaretPosition(0);
        return new VimEditor(vim, area, EditorDefaults.VIM);
    }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private static List<String> output(VimEditor editor) { return editor.output().stream().map(VimEditor.OutputLine::text).toList(); }

    @Test void namesResolveAsVimAbbreviatesThem() {
        assertThat(ExTable.NAMES).hasSize(600);
        assertThat(ExTable.resolve("s")).isEqualTo("substitute");
        assertThat(ExTable.resolve("d")).isEqualTo("delete");
        assertThat(ExTable.resolve("p")).isEqualTo("print");
        assertThat(ExTable.resolve("P")).isEqualTo("Print");
        assertThat(ExTable.resolve("j")).isEqualTo("join");
        assertThat(ExTable.resolve("m")).isEqualTo("move");
        assertThat(ExTable.resolve("co")).isEqualTo("copy");
        assertThat(ExTable.resolve("g")).isEqualTo("global");
        assertThat(ExTable.resolve("v")).isEqualTo("vglobal");
        assertThat(ExTable.resolve("u")).isEqualTo("undo");
        assertThat(ExTable.resolve("noh")).isEqualTo("nohlsearch");
        assertThat(ExTable.resolve("reg")).isEqualTo("registers");
        assertThat(ExTable.resolve("se")).isEqualTo("set");
        assertThat(ExTable.resolve("setl")).isEqualTo("setlocal");
        assertThat(ExTable.resolve("zzz")).isNull();
        for (Ex.Def def : Ex.implemented())
            assertThat(ExTable.resolve(def.abbreviation())).as("the reference's abbreviation of :%s", def.name()).isEqualTo(def.name());
        var abbreviations = Ex.implemented().stream().map(def -> def.abbreviation()).toList();
        assertThat(abbreviations).contains("d", "y", "pu", "j", "m", "t", "co", "norm", "sor", "le", "ri", "ce", "k", "ma",
            "delm", "marks", "ju", "changes", "reg", "di", "u", "red", "noh", "se", "setl", "setg");
    }

    @Test void severalLinesStayInTheOutputPanelUntilAKey() throws Exception {
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "one\ntwo\nthree\n");
            type(editor, ":%p<CR>");
            assertThat(output(editor)).containsExactly("one", "two", "three");
            assertThat(editor.prompt()).contains("Press ENTER or type command to continue");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(editor.offer(KeyInput.named("CR"))).as("the panel takes the key").isTrue();
            assertThat(editor.output()).isEmpty();
            assertThat(editor.cursor()).as("<CR> only takes the output away").isEqualTo(new Pos(2, 0));
            type(editor, ":1,2p<CR>k");
            assertThat(editor.output()).isEmpty();
            assertThat(editor.cursor().line).as("another key takes the output away and runs").isEqualTo(0);
            type(editor, ":1,2p<CR>:3<CR>");
            assertThat(editor.cursor().line).as(": starts the next command line at once").isEqualTo(2);
            type(editor, ":p<CR>");
            assertThat(editor.output()).as("one line is a message").isEmpty();
            assertThat(editor.message()).isEqualTo("three");
            type(editor, ":%p|foo<CR>");
            assertThat(editor.output()).as("after an error Vim does not wait").isEmpty();
            assertThat(editor.message()).isEqualTo("E492: Not an editor command: foo");
            assertThat(editor.messageIsError()).isTrue();
        });
    }

    @Test void aBackwardsRangeAsksBeforeItIsSwapped() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "1\n2\n3\n4\n");
            type(editor, ":3,1d<CR>");
            assertThat(editor.prompt()).contains("Backwards range given, OK to swap (y/n)?");
            type(editor, "x");
            assertThat(editor.prompt()).as("another key asks again").isPresent();
            type(editor, "y");
            assertThat(area.getText()).isEqualTo("4\n");
            type(editor, "u:3,1d<CR>n");
            assertThat(area.getText()).isEqualTo("1\n2\n3\n4\n");
        });
    }

    @Test void theCommandLineHasAHistoryAndTheColonRegister() throws Exception {
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "1\n2\n3\n");
            type(editor, ":2<CR>:3<CR>");
            assertThat(vim.commandHistory()).containsExactly("2", "3");
            type(editor, ":<Up><Up><CR>");
            assertThat(editor.cursor().line).isEqualTo(1);
            assertThat(vim.commandHistory()).as("a line run again moves to the end").containsExactly("3", "2");
            assertThat(vim.registers().get(':').map(Registers.Content::text)).contains("2");
            type(editor, ":<Esc>");
            assertThat(vim.commandHistory()).as("a cancelled line is not remembered").hasSize(2);
        });
    }

    @Test void normalGivesEscapeWhenItsKeysRunOut() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            type(editor, ":norm Ax<CR>");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(area.getText()).isEqualTo("abcx\n");
            var lines = new VimTextArea();
            var other = editor(lines, "1\n2\n3\n4\n");
            type(other, ":norm :2,1d<C-v><CR>y<CR>");
            assertThat(other.prompt()).as("the answer came from the keys").isEmpty();
            assertThat(lines.getText()).isEqualTo("3\n4\n");
            type(other, ":norm :2,1d<C-v><CR><CR>");
            assertThat(other.prompt()).as("out of keys, the question got <Esc>").isEmpty();
            assertThat(lines.getText()).isEqualTo("3\n4\n");
        });
    }

    @Test void theReportMessagesFollowReport() throws Exception {
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "1\n2\n3\n4\n");
            type(editor, "3dd");
            assertThat(editor.message()).isEqualTo("3 fewer lines");
            type(editor, "u:set report=0<CR>yy");
            assertThat(editor.message()).isEqualTo("1 line yanked");
        });
    }
}
```

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/vim/ui/VimBarTest.java` with:

```java
package dev.jasper.editor.vim.ui;

import dev.jasper.editor.vim.EditorDefaults;
import dev.jasper.editor.vim.KeyInput;
import dev.jasper.editor.vim.Vim;
import dev.jasper.editor.vim.VimEditor;
import dev.jasper.editor.vim.VimHost;
import dev.jasper.editor.vim.VimTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VimBarTest {
    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    @Test void showsTheModeThePartlyTypedCommandAndMessages() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("abc\n");
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            assertThat(bar.statusText()).isEmpty();
            type(editor, "i");
            assertThat(bar.statusText()).isEqualTo("-- INSERT --");
            type(editor, "<Esc>2d");
            assertThat(bar.statusText()).isEmpty();
            assertThat(bar.commandText()).isEqualTo("2d");
            type(editor, "<Esc>\"qp");
            assertThat(bar.statusText()).isEqualTo("E353: Nothing in register q");
            assertThat(bar.flashing()).isTrue();
            type(editor, "R");
            assertThat(bar.statusText()).isEqualTo("-- REPLACE --");
            bar.detach();
            type(editor, "<Esc>i");
            assertThat(bar.statusText()).as("a detached bar no longer follows the editor").isEmpty();
        });
    }

    @Test void showsTheCommandLineWithItsCursor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("one two\n");
            area.setCaretPosition(0);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            type(editor, "/tw");
            assertThat(bar.statusText()).isEqualTo("/tw");
            assertThat(bar.commandLineCursor()).isEqualTo(3);
            type(editor, "<Left>");
            assertThat(bar.commandLineCursor()).isEqualTo(2);
            type(editor, "<C-v><C-a>");
            assertThat(bar.statusText()).as("a control character shows as ^A").isEqualTo("/t^Aw");
            type(editor, "<Esc>");
            assertThat(bar.commandLineCursor()).isEqualTo(-1);
            type(editor, "/two<CR>");
            assertThat(bar.statusText()).as("a search echoes its pattern").isEqualTo("/two");
            type(editor, "n");
            assertThat(bar.statusText()).isEqualTo("search hit BOTTOM, continuing at TOP");
        });
    }

    @Test void showsTheVisualAreasSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("ab\u00e9d\nefgh\nijkl\n");
            area.setCaretPosition(0);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            type(editor, "vl");
            assertThat(bar.statusText()).isEqualTo("-- VISUAL --");
            assertThat(bar.commandText()).isEqualTo("2");
            type(editor, "l");
            assertThat(bar.commandText()).as("characters, then UTF-8 bytes when they differ").isEqualTo("3-4");
            type(editor, "j");
            assertThat(bar.commandText()).isEqualTo("2");
            type(editor, "<C-v>");
            assertThat(bar.commandText()).isEqualTo("2x3");
            type(editor, "V");
            assertThat(bar.commandText()).isEqualTo("2");
            type(editor, "\"");
            assertThat(bar.commandText()).as("a pending key replaces the size").isEqualTo("\"");
        });
    }

    @Test void showsTheOutputPanelAndQuestions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("one\ntwo\n");
            area.setCaretPosition(0);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            type(editor, ":%p");
            assertThat(bar.statusText()).isEqualTo(":%p");
            type(editor, "<CR>");
            assertThat(bar.outputLines()).containsExactly("one", "two");
            assertThat(bar.statusText()).isEqualTo("Press ENTER or type command to continue");
            type(editor, "<CR>");
            assertThat(bar.outputLines()).isEmpty();
            type(editor, ":2,1p<CR>");
            assertThat(bar.statusText()).isEqualTo("Backwards range given, OK to swap (y/n)?");
            type(editor, "n");
            assertThat(bar.statusText()).isEmpty();
            type(editor, ":foo<CR>");
            assertThat(bar.statusText()).isEqualTo("E492: Not an editor command: foo");
        });
    }
}
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimCorpusTest.java`, replace:

```java
        List<String> missing = VimCommands.standard().entries().stream().map(VimCommands.Entry::id).filter(id -> !used.contains(id)).toList();
```

with:

```java
        List<String> ids = new java.util.ArrayList<>(VimCommands.standard().entries().stream().map(VimCommands.Entry::id).toList());
        for (Ex.Def def : Ex.implemented()) ids.add("ex " + def.name());
        List<String> missing = ids.stream().filter(id -> !used.contains(id)).toList();
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimCorpusTest.java`, replace:

```java
            "cursor: 1,2", "keys: d<Esc>", "registers: \" a", "--- vim 9.1", "expect-text:", "  done", "expect-cursor: 1,1",
            "expect-mode: insert", "expect-register \": c \"one\\ttwo\"", "expect-register a: none");
```

with:

```java
            "cursor: 1,2", "keys: d<Esc>", "registers: \" a", "output: yes", "--- vim 9.1", "expect-text:", "  done", "expect-cursor: 1,1",
            "expect-mode: insert", "expect-output:", "  said $", "expect-register \": c \"one\\ttwo\"", "expect-register a: none");
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimCorpusTest.java`, replace:

```java
        assertThat(CorpusFile.format(c, c.expected())).containsExactly("--- vim 9.1", "expect-text:", "  done", "expect-cursor: 1,1",
            "expect-mode: insert", "expect-register \": c \"one\\ttwo\"", "expect-register a: none");
```

with:

```java
        assertThat(c.output()).isTrue();
        assertThat(c.expected().output()).containsExactly("said ");
        assertThat(CorpusFile.format(c, c.expected())).containsExactly("--- vim 9.1", "expect-text:", "  done", "expect-cursor: 1,1",
            "expect-mode: insert", "expect-output:", "  said $", "expect-register \": c \"one\\ttwo\"", "expect-register a: none");
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest'
```

Expected: compilation fails: `ExTable` does not exist.

- [ ] **Step 3: Write the new classes**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ExTable.java`:

```java
package dev.jasper.editor.vim;

import java.util.List;

/**
 * Every Ex command name Vim 9.1 knows, in the order of its command table (ex_cmds.h). A typed name stands for the
 * first command in this order that starts with it, as Vim's find_ex_command resolves abbreviations: {@code :s} is
 * {@code :substitute} and {@code :d} is {@code :delete}. Jasper implements some of them ({@link Ex}); the others fail
 * with E319.
 */
final class ExTable {
    private ExTable() { }

    /** The names, in Vim's order. */
    static final List<String> NAMES = List.of(
        "append", "abbreviate", "abclear", "aboveleft", "abstract", "all", "amenu", "anoremenu", "args", "argadd",
        "argdelete", "argdo", "argdedupe", "argedit", "argglobal", "arglocal", "argument", "ascii", "autocmd",
        "augroup", "aunmenu", "buffer", "bNext", "ball", "badd", "balt", "bdelete", "behave", "belowright", "bfirst",
        "blast", "bmodified", "bnext", "botright", "bprevious", "brewind", "break", "breakadd", "breakdel",
        "breaklist", "browse", "buffers", "bufdo", "bunload", "bwipeout", "change", "cNext", "cNfile", "cabbrev",
        "cabclear", "cabove", "caddbuffer", "caddexpr", "caddfile", "cafter", "call", "catch", "cbuffer", "cbefore",
        "cbelow", "cbottom", "cc", "cclose", "cd", "cdo", "center", "cexpr", "cfile", "cfdo", "cfirst", "cgetfile",
        "cgetbuffer", "cgetexpr", "chdir", "changes", "checkpath", "checktime", "chistory", "clist", "clipreset",
        "clast", "class", "close", "clearjumps", "cmap", "cmapclear", "cmenu", "cnext", "cnewer", "cnfile",
        "cnoremap", "cnoreabbrev", "cnoremenu", "copy", "colder", "colorscheme", "command", "comclear", "compiler",
        "continue", "confirm", "const", "copen", "cprevious", "cpfile", "cquit", "crewind", "cscope", "cstag",
        "cunmap", "cunabbrev", "cunmenu", "cwindow", "delete", "delmarks", "debug", "debuggreedy", "def",
        "defcompile", "defer", "delcommand", "delfunction", "display", "diffupdate", "diffget", "diffoff",
        "diffpatch", "diffput", "diffsplit", "diffthis", "digraphs", "disassemble", "djump", "dlist", "doautocmd",
        "doautoall", "drop", "dsearch", "dsplit", "edit", "earlier", "echo", "echoerr", "echohl", "echomsg",
        "echoconsole", "echon", "echowindow", "else", "elseif", "emenu", "endif", "endinterface", "endclass",
        "enddef", "endenum", "endfunction", "endfor", "endtry", "endwhile", "enew", "enum", "eval", "ex", "execute",
        "exit", "export", "exusage", "file", "files", "filetype", "filter", "find", "final", "finally", "finish",
        "first", "fixdel", "fold", "foldclose", "folddoopen", "folddoclosed", "foldopen", "for", "function",
        "global", "goto", "grep", "grepadd", "gui", "gvim", "help", "helpclose", "helpfind", "helpgrep", "helptags",
        "hardcopy", "highlight", "hide", "history", "horizontal", "insert", "iabbrev", "iabclear", "if", "ijump",
        "ilist", "imap", "imapclear", "imenu", "import", "inoremap", "inoreabbrev", "inoremenu", "intro",
        "interface", "iput", "isearch", "isplit", "iunmap", "iunabbrev", "iunmenu", "join", "jumps", "k",
        "keepmarks", "keepjumps", "keeppatterns", "keepalt", "list", "lNext", "lNfile", "last", "labove", "language",
        "laddexpr", "laddbuffer", "laddfile", "lafter", "later", "lbuffer", "lbefore", "lbelow", "lbottom", "lcd",
        "lchdir", "lclose", "lcscope", "ldo", "left", "leftabove", "let", "lexpr", "legacy", "lfile", "lfdo",
        "lfirst", "lgetfile", "lgetbuffer", "lgetexpr", "lgrep", "lgrepadd", "lhelpgrep", "lhistory", "ll", "llast",
        "llist", "lmap", "lmapclear", "lmake", "lnoremap", "lnext", "lnewer", "lnfile", "loadview", "loadkeymap",
        "lockmarks", "lockvar", "lolder", "lopen", "lprevious", "lpfile", "lrewind", "ltag", "lunmap", "lua",
        "luado", "luafile", "lvimgrep", "lvimgrepadd", "lwindow", "ls", "move", "mark", "make", "map", "mapclear",
        "marks", "match", "menu", "menutranslate", "messages", "mkexrc", "mksession", "mkspell", "mkvimrc", "mkview",
        "mode", "mzscheme", "mzfile", "next", "nbkey", "nbclose", "nbstart", "new", "nmap", "nmapclear", "nmenu",
        "nnoremap", "nnoremenu", "noremap", "noautocmd", "nohlsearch", "noreabbrev", "noremenu", "noswapfile",
        "normal", "number", "nunmap", "nunmenu", "open", "oldfiles", "omap", "omapclear", "omenu", "only",
        "onoremap", "onoremenu", "options", "ounmap", "ounmenu", "ownsyntax", "print", "packadd", "packloadall",
        "pbuffer", "pclose", "perl", "perldo", "pedit", "pop", "popup", "ppop", "preserve", "previous", "promptfind",
        "promptrepl", "profile", "profdel", "psearch", "ptag", "ptNext", "ptfirst", "ptjump", "ptlast", "ptnext",
        "ptprevious", "ptrewind", "ptselect", "put", "public", "pwd", "python", "pydo", "pyfile", "py3", "py3do",
        "python3", "py3file", "pyx", "pyxdo", "pythonx", "pyxfile", "quit", "quitall", "qall", "read", "recover",
        "redo", "redir", "redraw", "redrawstatus", "redrawtabline", "redrawtabpanel", "registers", "resize", "retab",
        "return", "rewind", "right", "rightbelow", "runtime", "ruby", "rubydo", "rubyfile", "rundo", "rviminfo",
        "substitute", "sNext", "sargument", "sall", "sandbox", "saveas", "sbuffer", "sbNext", "sball", "sbfirst",
        "sblast", "sbmodified", "sbnext", "sbprevious", "sbrewind", "scriptnames", "scriptencoding", "scriptversion",
        "scscope", "set", "setfiletype", "setglobal", "setlocal", "sfind", "sfirst", "shell", "simalt", "sign",
        "silent", "sleep", "slast", "smagic", "smap", "smapclear", "smenu", "snext", "snomagic", "snoremap",
        "snoremenu", "source", "sort", "split", "spellgood", "spelldump", "spellinfo", "spellrepall", "spellrare",
        "spellundo", "spellwrong", "sprevious", "srewind", "stop", "stag", "startinsert", "startgreplace",
        "startreplace", "static", "stopinsert", "stjump", "stselect", "sunhide", "sunmap", "sunmenu", "suspend",
        "sview", "swapname", "syntax", "syntime", "syncbind", "smile", "t", "tNext", "tag", "tags", "tab",
        "tabclose", "tabdo", "tabedit", "tabfind", "tabfirst", "tabmove", "tablast", "tabnext", "tabnew", "tabonly",
        "tabprevious", "tabNext", "tabrewind", "tabs", "tcd", "tchdir", "tcl", "tcldo", "tclfile", "tearoff",
        "terminal", "tfirst", "throw", "this", "tjump", "tlast", "tlmenu", "tlnoremenu", "tlunmenu", "tmenu", "tmap",
        "tmapclear", "tnext", "tnoremap", "topleft", "tprevious", "trewind", "try", "tselect", "tunmenu", "tunmap",
        "type", "undo", "undojoin", "undolist", "unabbreviate", "unhide", "uniq", "unlet", "unlockvar", "unmap",
        "unmenu", "unsilent", "update", "vglobal", "var", "version", "verbose", "vertical", "visual", "view",
        "vimgrep", "vimgrepadd", "vim9cmd", "vim9script", "viusage", "vmap", "vmapclear", "vmenu", "vnoremap",
        "vnew", "vnoremenu", "vsplit", "vunmap", "vunmenu", "write", "wNext", "wall", "while", "winsize", "wincmd",
        "windo", "winpos", "wlrestore", "wnext", "wprevious", "wq", "wqall", "wundo", "wviminfo", "xit", "xall",
        "xmap", "xmapclear", "xmenu", "xnoremap", "xnoremenu", "xrestore", "xunmap", "xunmenu", "yank", "z", "!",
        "#", "&", "*", "<", "=", ">", "@", "{", "}", "~", "Next", "Print", "X", "++", "--");

    /**
     * @param typed a command name as typed, perhaps abbreviated
     * @return the full name of the first command it abbreviates, or null
     */
    static String resolve(String typed) {
        for (String name : NAMES) if (name.startsWith(typed)) return name;
        return null;
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ExCommand.java`:

```java
package dev.jasper.editor.vim;

/**
 * One parsed Ex command, as Vim's exarg_T holds it: its range, name, {@code !}, register, count and argument. Line
 * numbers are Vim's (1 is the first line; 0 is before it).
 */
final class ExCommand {
    final VimEditor editor;
    /** What Jasper runs for it. */
    Ex.Def def;
    /** The command as typed from its name on (eap->cmd), for {@code :g}'s {@code v} and {@code :s}'s {@code s}. */
    String typed;
    int line1 = 1, line2 = 1;
    /** How many addresses were given (0 for none). */
    int addrCount;
    /** Whether {@code !} followed the name. */
    boolean forceit;
    /** The register named after it, or 0. */
    char regname;
    /** For {@code :>} and {@code :<}, how many {@code >} or {@code <} were typed. */
    int amount;
    /** The argument, after the name, register, count and flags. */
    String arg = "";
    /** {@link Ex#PRINT}, {@link Ex#LIST} and {@link Ex#NUMBER}: print the cursor line afterwards. */
    int flags;
    /** The command after a {@code |}, or null. */
    String nextcmd;

    ExCommand(VimEditor editor) { this.editor = editor; }

    /** @return the command's full name ({@code substitute}) */
    String name() { return def.name(); }

    /** @return the context an operator or put runs with: the editor and this command's register */
    Context context() {
        Context c = new Context(editor);
        c.register = regname;
        return c;
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ExInfo.java`:

```java
package dev.jasper.editor.vim;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Ex commands that show things or set marks and options: {@code :print} and its kin, {@code :=}, {@code :marks},
 * {@code :jumps}, {@code :changes}, {@code :registers}, {@code :k}/{@code :mark}, {@code :delmarks},
 * {@code :nohlsearch} and {@code :set}. Listings are ports of mark.c's ex_marks, ex_jumps and ex_changes, register.c's
 * ex_display and ex_cmds.c's print_line; each line goes to the output panel. Columns are byte columns, as Vim shows.
 */
final class ExInfo {
    private ExInfo() { }

    /** ex_print: {@code :p}, {@code :l} (list), {@code :nu} and {@code :#} (numbered), with any flags. */
    static void print(ExCommand cmd) {
        boolean number = cmd.name().equals("number") || cmd.name().equals("#") || (cmd.flags & Ex.NUMBER) != 0;
        boolean list = cmd.name().equals("list") || (cmd.flags & Ex.LIST) != 0;
        printLines(cmd, number, list);
    }

    /** The lines of the range, then the cursor on the last one (ex_print). */
    static void printLines(ExCommand cmd, boolean number, boolean list) {
        VimEditor editor = cmd.editor;
        for (int lnum = cmd.line1; lnum <= cmd.line2; lnum++) editor.say(line(editor, lnum, number, list));
        editor.ex().setpcmark();
        editor.ex().beginline(cmd.line2, true);
    }

    /** ex_may_print: after a command with {@code p}, {@code l} or {@code #}, the cursor line is printed. */
    static void mayPrint(ExCommand cmd) {
        if (cmd.flags == 0) return;
        VimEditor editor = cmd.editor;
        editor.say(line(editor, editor.cursor().line + 1, (cmd.flags & Ex.NUMBER) != 0, (cmd.flags & Ex.LIST) != 0));
    }

    /**
     * print_line: a line as {@code :print} shows it: a tab as spaces (or {@code ^I} when listing, with {@code $} at
     * the end), control characters as {@code ^X}, and with a line number in a field of at least three columns.
     */
    static String line(VimEditor editor, int lnum, boolean number, boolean list) {
        StringBuilder out = new StringBuilder();
        if (number) {
            int width = Math.max(3, String.valueOf(editor.buffer().count()).length());
            out.append(String.format("%" + width + "d ", lnum));
        }
        String text = editor.buffer().line(lnum - 1);
        int ts = editor.options().number("tabstop"), col = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\t' && !list) {
                int n = ts - col % ts;
                out.append(" ".repeat(n));
                col += n;
            } else if (cp < 0x20 || cp == 0x7f) {
                out.append('^').append(cp == 0x7f ? '?' : (char) (cp + '@'));
                col += 2;
            } else {
                out.appendCodePoint(cp);
                col += Text.width(cp);
            }
        }
        if (list) out.append('$');
        return out.toString();
    }

    /** ex_equal: the range's last line number (the last line when no range is given). */
    static void equal(ExCommand cmd) {
        cmd.editor.say(String.valueOf(cmd.line2));
        mayPrint(cmd);
    }

    /** ex_mark: {@code :k} and {@code :mark} set a mark at the first non-blank of the range's last line. */
    static void mark(ExCommand cmd) {
        if (cmd.arg.isEmpty()) throw new VimFailure("E471: Argument required");
        if (cmd.arg.codePointCount(0, cmd.arg.length()) > 1) throw new VimFailure("E488: Trailing characters: " + cmd.arg);
        VimEditor editor = cmd.editor;
        int line = cmd.line2 - 1;
        Pos at = new Pos(line, Text.firstNonBlank(editor.buffer().line(line)));
        if (!MarkCommands.setmark(editor, cmd.arg.charAt(0), at)) throw new VimFailure("E191: Argument must be a letter or forward/backward quote");
    }

    /** ex_delmarks: marks, ranges of them ({@code a-d}), or with {@code !} all lowercase marks. */
    static void delmarks(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Marks marks = editor.marks();
        String arg = cmd.arg;
        if (arg.isEmpty() && cmd.forceit) {
            marks.clearAll();
            return;
        }
        if (cmd.forceit) throw new VimFailure("E474: Invalid argument");
        if (arg.isEmpty()) throw new VimFailure("E471: Argument required");
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            boolean lower = c >= 'a' && c <= 'z', digit = c >= '0' && c <= '9', upper = c >= 'A' && c <= 'Z';
            if (lower || digit || upper) {
                char from = c, to = c;
                if (i + 1 < arg.length() && arg.charAt(i + 1) == '-') {
                    to = i + 2 < arg.length() ? arg.charAt(i + 2) : 0;
                    boolean same = lower ? to >= 'a' && to <= 'z' : digit ? to >= '0' && to <= '9' : to >= 'A' && to <= 'Z';
                    if (!same || to < from) throw new VimFailure("E475: Invalid argument: " + arg.substring(i));
                    i += 2;
                }
                for (char m = from; m <= to; m++) {
                    if (lower) marks.set(m, null);
                    else editor.vim().clearGlobalMark(m);
                }
            } else switch (c) {
                case '"', '^', '.', '[', ']' -> marks.set(c, null);
                case '<' -> marks.set(Visual.START, null);
                case '>' -> marks.set(Visual.END, null);
                case ' ' -> { }
                default -> throw new VimFailure("E475: Invalid argument: " + arg.substring(i));
            }
        }
    }

    /** ex_marks: every mark that is set, or those named in the argument. */
    static void marks(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        String arg = cmd.arg.isEmpty() ? null : cmd.arg;
        Marks marks = editor.marks();
        List<String> lines = new ArrayList<>();
        mark(editor, lines, arg, '\'', marks.pcmark(), null);
        for (char c = 'a'; c <= 'z'; c++) mark(editor, lines, arg, c, marks.get(c), null);
        var global = editor.vim().globalMarks();
        for (char c = 'A'; c <= 'Z'; c++) globalMark(editor, lines, arg, c, global.get(c));
        for (char c = '0'; c <= '9'; c++) globalMark(editor, lines, arg, c, global.get(c));
        mark(editor, lines, arg, '"', marks.get('"'), null);
        mark(editor, lines, arg, '[', marks.get('['), null);
        mark(editor, lines, arg, ']', marks.get(']'), null);
        mark(editor, lines, arg, '^', marks.get('^'), null);
        mark(editor, lines, arg, '.', marks.get('.'), null);
        // The Visual marks as they will be jumped to: '< is the earlier end.
        Pos start = marks.get(Visual.START), end = marks.get(Visual.END);
        Pos first = start != null && (end == null || start.compareTo(end) < 0) ? start : end;
        mark(editor, lines, arg, '<', first, null);
        mark(editor, lines, arg, '>', first == start ? end : start, null);
        if (lines.isEmpty()) {
            if (arg == null) editor.say("No marks set");
            else throw new VimFailure("E283: No marks matching \"" + arg + "\"");
            return;
        }
        editor.say("mark line  col file/text");
        lines.forEach(editor::say);
    }

    private static void globalMark(VimEditor editor, List<String> lines, String arg, char name, Vim.GlobalMark mark) {
        if (mark == null) return;
        String other = mark.editor() == editor ? null : mark.editor().documentName();
        mark(other == null ? editor : mark.editor(), lines, arg, name, mark.pos(), other);
    }

    private static void mark(VimEditor editor, List<String> lines, String arg, char name, Pos pos, String fileName) {
        if (pos == null || arg != null && arg.indexOf(name) < 0) return;
        Buffer b = editor.buffer();
        String text = fileName != null ? fileName : markLine(editor, pos, 15);
        lines.add(String.format(" %c %6d %4d ", name, pos.line + 1, byteColumn(b, pos)) + text);
    }

    /** ex_jumps: the jumplist, {@code >} at the current entry. */
    static void jumps(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Marks marks = editor.marks();
        marks.cleanupJumps();
        editor.say(" jump line  col file/text");
        List<Pos> jumps = marks.jumps();
        int index = marks.jumpIndex;
        for (int i = 0; i < jumps.size(); i++) {
            Pos pos = jumps.get(i);
            editor.say(String.format("%c %2d %5d %4d ", i == index ? '>' : ' ', Math.abs(i - index), pos.line + 1, byteColumn(editor.buffer(), pos))
                + markLine(editor, pos, 16));
        }
        if (index == jumps.size()) editor.say(">");
    }

    /** ex_changes: the change list, {@code >} at the current entry. */
    static void changes(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Marks marks = editor.marks();
        editor.say("change line  col text");
        List<Pos> changes = marks.changes();
        int index = marks.changeIndex;
        for (int i = 0; i < changes.size(); i++) {
            Pos pos = changes.get(i);
            editor.say(String.format("%c %3d %5d %4d ", i == index ? '>' : ' ', Math.abs(i - index), pos.line + 1, byteColumn(editor.buffer(), pos))
                + markLine(editor, pos, 17));
        }
        if (index == changes.size()) editor.say(">");
    }

    /** @return a mark's column in bytes, as Vim counts it; a column past the line counts the line's bytes before it */
    private static int byteColumn(Buffer b, Pos pos) {
        if (pos.col == Visual.MAX) return Integer.MAX_VALUE;
        if (pos.line >= b.count()) return pos.col;
        String line = b.line(pos.line);
        int col = Math.min(pos.col, line.length());
        return line.substring(0, col).getBytes(StandardCharsets.UTF_8).length + Math.max(0, pos.col - line.length());
    }

    /** mark_line: the mark's line without its indent, cut to fit the window after {@code lead} columns. */
    private static String markLine(VimEditor editor, Pos pos, int lead) {
        Buffer b = editor.buffer();
        if (pos.line < 0 || pos.line >= b.count()) return "-invalid-";
        String line = b.line(pos.line);
        line = line.substring(Text.firstNonBlank(line));
        return fit(line, editor.view().width() - lead, true);
    }

    /**
     * The text as msg_outtrans shows it, cut before the character that reaches {@code room} cells ({@code strict})
     * or that goes past it.
     */
    private static String fit(String text, int room, boolean strict) {
        StringBuilder out = new StringBuilder();
        int len = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String shown = transchar(cp);
            int cells = cp < 0x20 || cp == 0x7f ? 2 : Text.width(cp);
            len += cells;
            if (strict ? len >= room : len > room) break;
            out.append(shown);
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    private static String transchar(int cp) {
        if (cp < 0x20) return "^" + (char) (cp + '@');
        if (cp == 0x7f) return "^?";
        return new String(Character.toChars(cp));
    }

    /** ex_display: {@code :registers} and {@code :display}, all registers or those named in the argument. */
    static void registers(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        String arg = cmd.arg.isEmpty() ? null : cmd.arg;
        Registers registers = editor.vim().registers();
        int columns = editor.view().width();
        editor.say("Type Name Content");
        StringBuilder names = new StringBuilder("\"0123456789abcdefghijklmnopqrstuvwxyz-*+");
        for (int i = 0; i < names.length(); i++) {
            char name = names.charAt(i);
            if (arg != null && arg.indexOf(name) < 0 && !(name == '*' && arg.indexOf('+') >= 0)) continue;
            var content = registers.get(name);
            if (content.isEmpty()) continue;
            Registers.Content c = content.get();
            char type = switch (c.type()) { case LINE -> 'l'; case CHAR -> 'c'; case BLOCK -> 'b'; };
            StringBuilder line = new StringBuilder("  ").append(type).append("  \"").append(name).append("   ");
            int n = columns - 11;
            List<String> lines = c.lines();
            for (int j = 0; j < lines.size() && n > 1; j++) {
                if (j > 0) {
                    line.append("^J");
                    n -= 2;
                }
                String text = lines.get(j);
                for (int k = 0; k < text.length(); ) {
                    int cp = text.codePointAt(k);
                    n -= cp < 0x20 || cp == 0x7f ? 2 : Text.width(cp);
                    if (n < 0) break;
                    line.append(transchar(cp));
                    k += Character.charCount(cp);
                }
            }
            if (n > 1 && c.type() == Registers.Type.LINE) line.append("^J");
            editor.say(line.toString());
        }
        String inserted = registers.lastInserted();
        if (!inserted.isEmpty() && (arg == null || arg.indexOf('.') >= 0)) {
            String text = inserted.endsWith("\033") ? inserted.substring(0, inserted.length() - 1) : inserted;
            editor.say("  c  \".   " + fit(text, columns - 6, false));
        }
        var command = registers.get(':');
        if (command.isPresent() && (arg == null || arg.indexOf(':') >= 0)) editor.say("  c  \":   " + fit(command.get().text(), columns - 6, false));
        String name = editor.documentName();
        if (!name.equals("[No Name]") && (arg == null || arg.indexOf('%') >= 0)) editor.say("  c  \"%   " + fit(name, columns - 6, false));
        String pattern = editor.vim().search().pattern();
        if (pattern != null && (arg == null || arg.indexOf('/') >= 0)) editor.say("  c  \"/   " + fit(pattern, columns - 6, false));
    }

    /** ex_nohlsearch: the 'hlsearch' matches are hidden until the next search. */
    static void nohlsearch(ExCommand cmd) { cmd.editor.vim().search().hideHighlight(); }

    /** {@code :set}, {@code :setlocal} and {@code :setglobal}. */
    static void set(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Options.Scope scope = switch (cmd.name()) {
            case "setlocal" -> Options.Scope.LOCAL;
            case "setglobal" -> Options.Scope.GLOBAL;
            default -> Options.Scope.BOTH;
        };
        List<String> shown = new ArrayList<>();
        try {
            editor.options().set(cmd.arg, scope, editor.view().width(), shown);
        } catch (IllegalArgumentException bad) {
            shown.forEach(editor::say);
            throw new VimFailure(bad.getMessage());
        }
        shown.forEach(editor::say);
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ExLines.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Ex commands that change lines: {@code :d}, {@code :y}, {@code :>} and {@code :<} (ex_operators), {@code :put},
 * {@code :join}, {@code :move} and {@code :copy} (ex_copymove, do_move, ex_copy), {@code :normal}, {@code :sort},
 * {@code :left}, {@code :right} and {@code :center} (ex_align), {@code :undo} and {@code :redo}. Ported from
 * ex_docmd.c and ex_cmds.c; the operators and put are the Normal-mode ones.
 */
final class ExLines {
    private ExLines() { }

    /** ex_operators: {@code :d}, {@code :y}, {@code :>} and {@code :<} on whole lines. */
    static void operators(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Context c = cmd.context();
        Span span = new Span(new Pos(cmd.line1 - 1, 0), new Pos(cmd.line2 - 1, 0), Target.Kind.LINEWISE);
        if (!cmd.name().equals("yank")) {
            editor.ex().setpcmark();
            editor.ex().beginline(cmd.line1, true);
            Pos p = editor.cursor();
            editor.area().vimUndo().stepCursor(p.line, p.col);
        }
        switch (cmd.name()) {
            case "delete" -> Operators.delete(c, span, false);
            case "yank" -> Operators.yank(c, span);
            default -> Operators.shift(c, span, cmd.name().equals("<"), cmd.amount, false);
        }
        setOperatorMarks(editor, cmd);
        ExInfo.mayPrint(cmd);
    }

    /** The {@code '[} and {@code ']} marks after a linewise operator, as op_delete, op_yank and op_shift set them. */
    private static void setOperatorMarks(VimEditor editor, ExCommand cmd) {
        Marks marks = editor.marks();
        Buffer b = editor.buffer();
        switch (cmd.name()) {
            case "delete" -> {
                Pos at = new Pos(Math.min(cmd.line1 - 1, b.count() - 1), 0);
                marks.set('[', at);
                marks.set(']', at);
            }
            case "yank" -> {
                marks.set('[', new Pos(cmd.line1 - 1, 0));
                marks.set(']', new Pos(cmd.line2 - 1, Visual.MAX));
            }
            default -> {
                String last = b.line(cmd.line2 - 1);
                marks.set('[', new Pos(cmd.line1 - 1, 0));
                marks.set(']', new Pos(cmd.line2 - 1, last.isEmpty() ? 0 : Text.last(last)));
            }
        }
    }

    /** ex_put: the register's text as lines below the line ({@code !}: above); {@code :0put} puts above the first. */
    static void put(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        int line = cmd.line2;
        boolean above = cmd.forceit;
        if (line == 0) {
            line = 1;
            above = true;
        }
        editor.setCursor(new Pos(line - 1, editor.cursor().col));
        Put.put(cmd.context(), !above, 1, Put.LINE | Put.CURSLINE, -1);
    }

    /** ex_join: the range's lines, at least two; with {@code !} without inserting spaces. */
    static void join(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Buffer b = editor.buffer();
        editor.setCursor(new Pos(cmd.line1 - 1, 0));
        if (cmd.line1 == cmd.line2) {
            if (cmd.addrCount >= 2) return;
            if (cmd.line2 == b.count()) throw VimFailure.bell();
            cmd.line2++;
        }
        NormalCommands.joinLines(cmd.context(), cmd.line1 - 1, cmd.line2 - cmd.line1 + 1, !cmd.forceit);
        editor.ex().beginline(editor.cursor().line + 1, false);
        ExInfo.mayPrint(cmd);
    }

    /** ex_copymove: {@code :move}, {@code :copy} and {@code :t} to below the address in the argument. */
    static void copyMove(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Buffer b = editor.buffer();
        int[] at = { 0 };
        int n = editor.ex().address(cmd.arg, at);
        String rest = cmd.arg.substring(at[0]);
        int f = 0;
        while (f < rest.length() && "lp#".indexOf(rest.charAt(f)) >= 0) {
            char flag = rest.charAt(f);
            cmd.flags |= flag == 'l' ? Ex.LIST : flag == 'p' ? Ex.PRINT : Ex.NUMBER;
            f = Ex.skipWhite(rest, f + 1);
        }
        if (n == Ex.NO_ADDRESS || n < 0 || n > b.count()) throw new VimFailure("E16: Invalid range");
        if (cmd.name().equals("move")) move(editor, cmd.line1, cmd.line2, n);
        else copy(editor, cmd.line1, cmd.line2, n);
        editor.ex().beginline(editor.cursor().line + 1, true);
        ExInfo.mayPrint(cmd);
    }

    /** do_move: lines {@code line1}..{@code line2} to below line {@code dest}, their marks with them. */
    private static void move(VimEditor editor, int line1, int line2, int dest) {
        Buffer b = editor.buffer();
        if (dest >= line1 && dest < line2) throw new VimFailure("E134: Cannot move a range of lines into itself");
        int num = line2 - line1 + 1;
        if (dest == line1 - 1 || dest == line2) {
            // Nothing moves, but the cursor goes where it would have.
            int to = dest >= line1 ? dest : dest + num;
            editor.setCursor(new Pos(Math.max(0, to - 1), 0));
            return;
        }
        List<String> lines = b.lines(line1 - 1, line2 - 1);
        Marks marks = editor.marks();
        editor.area().vimUndo().stepCursor(editor.cursor().line, editor.cursor().col);
        if (dest >= line2) {
            marks.moving(line1 - 1, line2 - 1, dest - num, () -> {
                b.deleteLines(line1 - 1, line2 - 1);
                b.insertLines(dest - num, lines);
            });
            marks.set('[', new Pos(dest - num, 0));
            marks.set(']', new Pos(dest - 1, 0));
        } else {
            marks.moving(line1 - 1, line2 - 1, dest, () -> {
                b.insertLines(dest, lines);
                b.deleteLines(line1 - 1 + num, line2 - 1 + num);
            });
            marks.set('[', new Pos(dest, 0));
            marks.set(']', new Pos(dest + num - 1, 0));
        }
        marks.set('.', new Pos(Math.min(line1, dest + 1) - 1, 0));
        if (!editor.ex().globalBusy() && num > editor.options().number("report"))
            editor.say(num + (num == 1 ? " line moved" : " lines moved"));
        int to = dest >= line1 ? dest : dest + num;
        editor.setCursor(new Pos(to - 1, 0));
    }

    /** ex_copy: a copy of lines {@code line1}..{@code line2} below line {@code n}. */
    private static void copy(VimEditor editor, int line1, int line2, int n) {
        Buffer b = editor.buffer();
        int count = line2 - line1 + 1;
        List<String> lines = b.lines(line1 - 1, line2 - 1);
        editor.area().vimUndo().stepCursor(editor.cursor().line, editor.cursor().col);
        b.insertLines(n, lines);
        Marks marks = editor.marks();
        marks.set('[', new Pos(n, 0));
        marks.set(']', new Pos(n + count - 1, 0));
        editor.setCursor(new Pos(n + count - 1, 0));
        editor.msgmore(count);
    }

    /**
     * ex_normal: the argument's characters run as Normal-mode keys, on each line of the range from its start, or
     * once. A command left incomplete is ended as with {@code <Esc>}, as Vim's :normal gets <Esc> when it runs out.
     * A failing command drops the rest of the keys.
     */
    static void normal(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Ex ex = editor.ex();
        if (ex.normalDepth >= 1000) throw new VimFailure("E192: Recursive use of :normal too deep");
        List<KeyInput> keys = keys(cmd.arg);
        ex.normalDepth++;
        try {
            int line = cmd.line1;
            do {
                if (cmd.addrCount != 0) {
                    editor.setCursor(new Pos(line - 1, 0));
                    editor.forgetColumn();
                    line++;
                }
                run(editor, keys);
            } while (cmd.addrCount > 0 && line <= cmd.line2);
        } finally {
            ex.normalDepth--;
        }
    }

    /** The characters of {@code :normal}'s argument as keys: a control character is its CTRL key. */
    static List<KeyInput> keys(String text) {
        List<KeyInput> keys = new ArrayList<>();
        text.codePoints().forEach(cp -> {
            if (cp < 0x20) keys.add(KeyInput.ctrl((char) Character.toLowerCase(cp + '@')));
            else if (cp == 0x7f) keys.add(KeyInput.named("Del"));
            else keys.add(KeyInput.of(cp));
        });
        return keys;
    }

    private static void run(VimEditor editor, List<KeyInput> keys) {
        VimFailure failed = null;
        for (KeyInput key : keys) {
            try {
                editor.dispatch(key);
            } catch (VimFailure failure) {
                failed = failure;
                break;
            }
        }
        if (failed != null) {
            if (!failed.quiet) editor.bell();
            if (!failed.getMessage().isEmpty()) editor.message(failed.getMessage(), true);
            if (!editor.mode().inserting()) {
                editor.machine().reset();
                editor.machine().resumeIfInsertNormal();
            }
        }
        // Out of keys: whatever still waits for one gets <Esc>.
        for (int n = 0; n < 100 && pending(editor); n++) {
            try {
                editor.dispatch(KeyInput.named("Esc"));
            } catch (VimFailure ignored) {
                editor.machine().reset();
            }
        }
        if (failed != null && !failed.getMessage().isEmpty()) throw VimFailure.quiet();
    }

    private static boolean pending(VimEditor editor) {
        return editor.prompting() || editor.commandLine().active() || editor.mode().inserting() || editor.machine().pending()
            || editor.mode() == Mode.INSERT_NORMAL;
    }

    /** ex_sort: the range's lines sorted by their text, a number in them ({@code n x o b f}), or after or at a pattern. */
    static void sort(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Buffer b = editor.buffer();
        int count = cmd.line2 - cmd.line1 + 1;
        if (count <= 1) return;
        boolean ic = false, rx = false, unique = false, flt = false, nr = false;
        int radix = 0, formats = 0;
        VimRegex regex = null;
        String arg = cmd.arg;
        for (int p = 0; p < arg.length(); p++) {
            char ch = arg.charAt(p);
            if (Ex.isWhite(ch)) continue;
            switch (ch) {
                case 'i' -> ic = true;
                case 'l' -> { }
                case 'r' -> rx = true;
                case 'n' -> { nr = true; formats++; }
                case 'f' -> { flt = true; formats++; }
                case 'b' -> { radix = 2; formats++; }
                case 'o' -> { radix = 8; formats++; }
                case 'x' -> { radix = 16; formats++; }
                case 'u' -> unique = true;
                default -> {
                    if (ch == '"') {
                        p = arg.length();
                        continue;
                    }
                    int next = nextCommand(arg, p);
                    if (next >= 0 && cmd.nextcmd == null) {
                        cmd.nextcmd = arg.substring(next);
                        p = arg.length();
                        continue;
                    }
                    if (!(ch < 128 && Character.isLetter(ch)) && regex == null) {
                        int end = p + 1 + Search.skipPattern(arg.substring(p + 1), ch);
                        String pat = arg.substring(p + 1, Math.min(end, arg.length()));
                        if (end >= arg.length() || arg.charAt(end) != ch) throw new VimFailure("E654: Missing delimiter after search pattern: " + arg.substring(p));
                        Search search = editor.vim().search();
                        if (pat.isEmpty()) {
                            pat = search.pattern();
                            if (pat == null) throw new VimFailure("E35: No previous regular expression");
                        }
                        regex = compile(editor, pat);
                        p = end;
                        continue;
                    }
                    throw new VimFailure("E475: Invalid argument: " + arg.substring(p));
                }
            }
        }
        if (formats > 1) throw new VimFailure("E474: Invalid argument");
        boolean number = nr || radix != 0;
        record Item(int lnum, String key, boolean isNumber, long value, double real) { }
        List<Item> items = new ArrayList<>();
        for (int lnum = cmd.line1; lnum <= cmd.line2; lnum++) {
            String s = b.line(lnum - 1);
            int start = 0, end = s.length();
            if (regex != null) {
                Matcher m = regex.pattern().matcher(s + "\n");
                if (m.find() && m.start() <= s.length()) {
                    int ms = Math.min(regex.start(m), s.length()), me = Math.min(regex.end(m), s.length());
                    if (rx) {
                        start = ms;
                        end = me;
                    } else start = me;
                } else end = 0;
            }
            String part = s.substring(Math.min(start, end), end);
            if (number) {
                long[] parsed = number(part, radix);
                items.add(new Item(lnum, null, parsed != null, parsed == null ? 0 : parsed[0], 0));
            } else if (flt) items.add(new Item(lnum, null, false, 0, real(part)));
            else items.add(new Item(lnum, part, false, 0, 0));
        }
        boolean ignoreCase = ic;
        Comparator<Item> order;
        if (number) order = (x, y) -> x.isNumber() != y.isNumber() ? (x.isNumber() ? 1 : -1) : Long.compare(x.value(), y.value());
        else if (flt) order = (x, y) -> Double.compare(x.real(), y.real()) == 0 ? 0 : x.real() > y.real() ? 1 : -1;
        else order = (x, y) -> compare(x.key(), y.key(), ignoreCase);
        order = order.thenComparingInt(Item::lnum);
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(order);
        List<String> lines = new ArrayList<>();
        String previous = null;
        for (int i = 0; i < count; i++) {
            Item item = sorted.get(cmd.forceit ? count - i - 1 : i);
            String s = b.line(item.lnum() - 1);
            if (!unique || i == 0 || compare(s, previous, ignoreCase) != 0) {
                lines.add(s);
                previous = s;
            }
        }
        List<String> old = b.lines(cmd.line1 - 1, cmd.line2 - 1);
        if (!lines.equals(old)) {
            editor.area().vimUndo().stepCursor(editor.cursor().line, editor.cursor().col);
            replaceLines(b, cmd.line1 - 1, old, lines);
        }
        int deleted = count - lines.size();
        if (deleted > 0) editor.msgmore(-deleted);
        editor.ex().beginline(cmd.line1, false);
    }

    /** Replaces lines {@code first}.. (which were {@code old}) with {@code lines}, changing only the lines that differ. */
    private static void replaceLines(Buffer b, int first, List<String> old, List<String> lines) {
        int common = Math.min(old.size(), lines.size());
        for (int i = 0; i < common; i++) b.setLine(first + i, lines.get(i));
        if (old.size() > lines.size()) b.deleteLines(first + lines.size(), first + old.size() - 1);
        else if (lines.size() > old.size()) b.insertLines(first + old.size(), lines.subList(old.size(), lines.size()));
    }

    /** STRCMP or STRICMP: by code point (UTF-8 byte order), folding ASCII letters for {@code i}. */
    private static int compare(String a, String b, boolean ignoreCase) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            int x = a.codePointAt(i), y = b.codePointAt(j);
            if (ignoreCase) {
                if (x < 128) x = Character.toLowerCase(x);
                if (y < 128) y = Character.toLowerCase(y);
            }
            if (x != y) return Integer.compare(x, y);
            i += Character.charCount(a.codePointAt(i));
            j += Character.charCount(b.codePointAt(j));
        }
        return Boolean.compare(i < a.length(), j < b.length());
    }

    /**
     * The number {@code :sort n} (decimal), {@code x}, {@code o} or {@code b} sorts on: the first digit and what
     * follows, with a {@code -} before it.
     *
     * @return {value}, or null when the text has no number
     */
    private static long[] number(String s, int radix) {
        String digits = radix == 16 ? "0123456789abcdefABCDEF" : radix == 2 ? "01" : "0123456789";
        int i = 0;
        while (i < s.length() && digits.indexOf(s.charAt(i)) < 0) i++;
        if (i >= s.length()) return null;
        boolean negative = i > 0 && s.charAt(i - 1) == '-';
        // A 0x or 0b prefix before hex or binary digits is skipped.
        char prefix = radix == 16 ? 'x' : radix == 2 ? 'b' : 0;
        if (prefix != 0 && s.charAt(i) == '0' && i + 2 < s.length() && Character.toLowerCase(s.charAt(i + 1)) == prefix
            && digits.indexOf(s.charAt(i + 2)) >= 0) i += 2;
        String allowed = radix == 8 ? "01234567" : digits;
        int base = radix == 0 ? 10 : radix;
        long value = 0;
        while (i < s.length() && allowed.indexOf(s.charAt(i)) >= 0) {
            int d = Character.digit(s.charAt(i++), base);
            value = value > (Long.MAX_VALUE - d) / base ? Long.MAX_VALUE : value * base + d;
        }
        return new long[] { negative ? -value : value };
    }

    /** strtod after white space and a {@code +}; an empty text sorts before every number. */
    private static double real(String s) {
        String t = s.stripLeading();
        if (t.startsWith("+")) t = t.substring(1).stripLeading();
        if (t.isEmpty()) return -Double.MAX_VALUE;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^[-]?(?:\\d+\\.?\\d*(?:[eE][-+]?\\d+)?|\\.\\d+(?:[eE][-+]?\\d+)?|inf(?:inity)?|nan)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(t);
        if (!m.find()) return 0;
        String n = m.group().toLowerCase(java.util.Locale.ROOT);
        if (n.contains("inf")) return n.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        if (n.contains("nan")) return Double.NaN;
        return Double.parseDouble(n);
    }

    private static VimRegex compile(VimEditor editor, String pat) {
        Options o = editor.options();
        try {
            return VimRegex.compile(pat, new VimRegex.Settings(o.flag("ignorecase"), false, o.keywords().javaClass(), editor.vim().search().prevSub));
        } catch (IllegalArgumentException bad) {
            throw new VimFailure(bad.getMessage());
        }
    }

    private static int nextCommand(String s, int i) {
        int j = Ex.skipWhite(s, i);
        return j < s.length() && s.charAt(j) == '|' ? j + 1 : -1;
    }

    /**
     * ex_align: {@code :left [indent]}, {@code :right [width]} and {@code :center [width]}; the width defaults to
     * 'textwidth', or 80. Blank lines are left alone by {@code :right} and {@code :center}.
     */
    static void align(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Buffer b = editor.buffer();
        int ts = editor.options().number("tabstop");
        boolean et = editor.options().flag("expandtab");
        int width = atoi(cmd.arg), indent = 0;
        boolean left = cmd.name().equals("left"), right = cmd.name().equals("right");
        if (left) {
            if (width >= 0) indent = width;
        } else {
            if (width <= 0) width = editor.options().number("textwidth");
            if (width <= 0) width = 80;
        }
        Pos saved = editor.cursor();
        editor.area().vimUndo().stepCursor(saved.line, saved.col);
        for (int lnum = cmd.line1; lnum <= cmd.line2; lnum++) {
            String line = b.line(lnum - 1);
            int newIndent;
            if (left) newIndent = indent;
            else {
                int len = lineLength(line, ts) - Text.indent(line, ts);
                if (len <= 0) continue;
                if (!right) newIndent = (width - len) / 2;
                else {
                    newIndent = width - len;
                    if (hasTab(line)) {
                        while (newIndent > 0) {
                            if (lineLength(indented(line, newIndent, ts, et), ts) <= width) {
                                do newIndent++;
                                while (lineLength(indented(line, newIndent, ts, et), ts) <= width);
                                newIndent--;
                                break;
                            }
                            newIndent--;
                        }
                    }
                }
            }
            b.setLine(lnum - 1, indented(line, Math.max(0, newIndent), ts, et));
        }
        editor.setCursor(saved);
        editor.ex().beginline(saved.line + 1, false);
    }

    private static String indented(String line, int indent, int ts, boolean et) {
        return Text.makeIndent(indent, ts, et) + line.substring(Text.firstNonBlank(line));
    }

    /** linelen: the display width of the line without trailing white space. */
    private static int lineLength(String line, int ts) {
        int last = line.length();
        while (last > 0 && Ex.isWhite(line.charAt(last - 1))) last--;
        return Text.virtcol(line.substring(0, last), last, ts);
    }

    private static boolean hasTab(String line) {
        int last = line.length();
        while (last > 0 && Ex.isWhite(line.charAt(last - 1))) last--;
        return line.substring(Text.firstNonBlank(line), Math.max(Text.firstNonBlank(line), last)).indexOf('\t') >= 0;
    }

    /** atoi: leading white space, a sign and digits; 0 when there are none. */
    private static int atoi(String s) {
        int i = Ex.skipWhite(s, 0);
        boolean negative = false;
        if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) negative = s.charAt(i++) == '-';
        long v = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) v = Math.min(Integer.MAX_VALUE, v * 10 + s.charAt(i++) - '0');
        return (int) (negative ? -v : v);
    }

    /** ex_undo: {@code :undo} undoes one change; {@code :undo N} goes to the text after change N (0: before all). */
    static void undo(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Pos before = editor.cursor();
        if (cmd.addrCount == 1) editor.undoTo(cmd.line2);
        else editor.undo(1);
        editor.machine().foldOpenAfter("undo", before);
    }

    /** ex_redo. */
    static void redo(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Pos before = editor.cursor();
        editor.redo(1);
        editor.machine().foldOpenAfter("undo", before);
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`:

```java
package dev.jasper.editor.vim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Ex command line (spec 5.3): {@code :} reads a line and this runs it, ported from Vim's ex_docmd.c (do_cmdline,
 * do_one_cmd, parse_cmd_address, get_address, find_ex_command and separate_nextcmd). A line is one undo step; what
 * its commands say goes to the output panel.
 *
 * <p>Vim asks questions in the middle of a command (the {@code :s} confirmation, "Backwards range given") and reads
 * the answer there. Jasper's keys arrive one at a time, so a command that asks returns with the question open, and
 * each caller that has more to do afterwards (the rest of the line, {@code :global}'s next line) leaves that as a
 * continuation with {@link #later}. When the answer comes, the asking command goes on, then the continuations run,
 * innermost first. Each run of a command line keeps its own continuations, so a question asked inside
 * {@code :normal} is settled before the command that ran {@code :normal} goes on.
 */
final class Ex {
    /** Argument flags (ex_cmds.h's EX_*). */
    static final int RANGE = 1, BANG = 1 << 1, EXTRA = 1 << 2, DFLALL = 1 << 3, WHOLEFOLD = 1 << 4, NEEDARG = 1 << 5,
        TRLBAR = 1 << 6, REGSTR = 1 << 7, COUNT = 1 << 8, NOTRLCOM = 1 << 9, ZEROR = 1 << 10, CTRLV = 1 << 11, FLAGS = 1 << 12;
    /** Flags after a command (EXFLAG_*): print, list or number the cursor line afterwards. */
    static final int PRINT = 1, LIST = 2, NUMBER = 4;
    /** Vim's MAXLNUM: no address was given. */
    static final int NO_ADDRESS = Integer.MIN_VALUE;

    /** What an address counts: lines, something else ({@code :undo}'s change number), or nothing. */
    enum Addr { LINES, OTHER, NONE }

    @FunctionalInterface interface Handler { void run(ExCommand cmd); }

    /**
     * A command Jasper implements.
     *
     * @param name its full name, as in Vim's table
     * @param flags its argument flags
     * @param addr what its addresses count
     * @param description what it does, for the reference
     * @param handler what runs it
     */
    record Def(String name, int flags, Addr addr, String description, Handler handler) {
        /** @return the shortest abbreviation Vim takes for it ({@code s} for {@code substitute}) */
        String abbreviation() {
            if (name.equals("k")) return "k";
            for (int n = 1; n < name.length(); n++) if (name.equals(ExTable.resolve(name.substring(0, n)))) return name.substring(0, n);
            return name;
        }
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    private static void def(String name, int flags, Addr addr, String description, Handler handler) {
        DEFS.put(name, new Def(name, flags, addr, description, handler));
    }

    static {
        int print = RANGE | WHOLEFOLD | COUNT | FLAGS | TRLBAR;
        def("print", print, Addr.LINES, "print the lines ([range], default the cursor line); `P` too", ExInfo::print);
        def("Print", print, Addr.LINES, "print the lines, as `:print`", ExInfo::print);
        def("list", print, Addr.LINES, "print the lines with tabs as `^I` and `$` at the end", ExInfo::print);
        def("number", print, Addr.LINES, "print the lines with their numbers", ExInfo::print);
        def("#", print, Addr.LINES, "print the lines with their numbers, as `:number`", ExInfo::print);
        def("=", RANGE | TRLBAR | DFLALL | FLAGS, Addr.LINES, "show the last line's number, or the range's last", ExInfo::equal);
        def("delete", RANGE | WHOLEFOLD | REGSTR | COUNT | TRLBAR, Addr.LINES, "delete the lines [into a register] [count]", ExLines::operators);
        def("yank", RANGE | WHOLEFOLD | REGSTR | COUNT | TRLBAR, Addr.LINES, "yank the lines [into a register] [count]", ExLines::operators);
        def(">", RANGE | WHOLEFOLD | COUNT | FLAGS | TRLBAR, Addr.LINES, "shift the lines right; `>>` twice, and so on", ExLines::operators);
        def("<", RANGE | WHOLEFOLD | COUNT | FLAGS | TRLBAR, Addr.LINES, "shift the lines left; `<<` twice, and so on", ExLines::operators);
        def("put", RANGE | WHOLEFOLD | BANG | REGSTR | TRLBAR | ZEROR, Addr.LINES, "put a register as lines below the line; `!` above; `:0put` above the first", ExLines::put);
        def("join", BANG | RANGE | WHOLEFOLD | COUNT | FLAGS | TRLBAR, Addr.LINES, "join the lines (at least two); `!` without spaces", ExLines::join);
        def("copy", RANGE | WHOLEFOLD | EXTRA | TRLBAR, Addr.LINES, "copy the lines below {address}", ExLines::copyMove);
        def("t", RANGE | WHOLEFOLD | EXTRA | TRLBAR, Addr.LINES, "copy the lines below {address}, as `:copy`", ExLines::copyMove);
        def("move", RANGE | WHOLEFOLD | EXTRA | TRLBAR, Addr.LINES, "move the lines below {address}", ExLines::copyMove);
        def("normal", RANGE | BANG | EXTRA | NEEDARG | NOTRLCOM | CTRLV, Addr.LINES, "run {keys} as Normal-mode keys, on each line of the range", ExLines::normal);
        def("sort", RANGE | DFLALL | WHOLEFOLD | BANG | EXTRA | NOTRLCOM, Addr.LINES,
            "sort the lines (all by default); `!` reverses; `i`, `n`, `x`, `o`, `b`, `f`, `u`, `r` and a `/pattern/`", ExLines::sort);
        def("left", TRLBAR | RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "align the lines left, with [indent]", ExLines::align);
        def("right", TRLBAR | RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "align the lines right at [width] ('textwidth', or 80)", ExLines::align);
        def("center", TRLBAR | RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "center the lines in [width] ('textwidth', or 80)", ExLines::align);
        // Vim's EX_WORD1 includes EX_EXTRA: one word of argument.
        def("k", RANGE | EXTRA | TRLBAR, Addr.LINES, "set a mark at the line (`:ka`, `:k a`)", ExInfo::mark);
        def("mark", RANGE | EXTRA | TRLBAR, Addr.LINES, "set a mark at the line", ExInfo::mark);
        def("delmarks", BANG | EXTRA | TRLBAR, Addr.NONE, "delete marks (`a b-d`); `!` all lowercase ones", ExInfo::delmarks);
        def("marks", EXTRA | TRLBAR, Addr.NONE, "list the marks, or those named", ExInfo::marks);
        def("jumps", TRLBAR, Addr.NONE, "list the jumplist", ExInfo::jumps);
        def("changes", TRLBAR, Addr.NONE, "list the change list", ExInfo::changes);
        def("registers", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "list the registers, or those named", ExInfo::registers);
        def("display", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "list the registers, as `:registers`", ExInfo::registers);
        def("undo", RANGE | COUNT | ZEROR | TRLBAR, Addr.OTHER, "undo one change; `:undo N` goes to the text after change N", ExLines::undo);
        def("redo", TRLBAR, Addr.NONE, "redo one change", ExLines::redo);
        def("nohlsearch", TRLBAR, Addr.NONE, "hide the 'hlsearch' matches until the next search", ExInfo::nohlsearch);
        def("set", BANG | TRLBAR | EXTRA, Addr.NONE, "show or set options: `opt`, `noopt`, `invopt`, `opt!`, `opt&`, `opt?`, `opt=val`, `+=`, `-=`, `^=`, `all`", ExInfo::set);
        def("setlocal", BANG | TRLBAR | EXTRA, Addr.NONE, "`:set` for this tab's local values only", ExInfo::set);
        def("setglobal", BANG | TRLBAR | EXTRA, Addr.NONE, "`:set` for the global values only", ExInfo::set);
    }

    /** @return the commands Jasper implements, in the reference's order */
    static List<Def> implemented() { return List.copyOf(DEFS.values()); }

    /** The keys that open a command line leave the remembered column alone ({@code :s} after {@code $} keeps it). */
    static final List<String> KEEP_COLUMN = List.of(":", "&", "g&");

    /** The Normal- and Visual-mode keys that reach the command line. */
    static void register(VimCommands commands) {
        commands.action(":", "a command line; with a count, for that many lines (`:.,.+N`)", false, c -> c.editor.ex().open(c));
        commands.visual(":", "a command line for the Visual lines (`:'<,'>`)", false, false, c -> c.editor.ex().open(c));
    }

    /** A continuation: work left for after a question, or the end of a command line's run (closing its undo step). */
    private record Step(Runnable work, boolean finisher) { }

    private static final Object SUSPENDED = new Object();

    private final VimEditor editor;
    /** The continuations of each suspended run, innermost run first. */
    private final Deque<ArrayDeque<Step>> frames = new ArrayDeque<>();
    /** The continuations registered in the stretch of work now running. */
    private List<Step> fresh = new ArrayList<>();
    /** :global's second pass is running (Vim's global_busy). */
    private int globalBusy;
    /** Whether a command of the running command line failed (do_cmdline's FAIL: no hit-enter prompt then). */
    private boolean failed;
    /** How deep {@code :normal} is running: its keys are not typed (Vim's KeyTyped is false). */
    int normalDepth;
    /** The substitutions and lines {@code :s} changed, for its message and {@code :global}'s (sub_nsubs, sub_nlines). */
    long subNsubs, subNlines;
    /** A {@code :s} inside {@code :global} wants the cursor on the first non-blank at the end (global_need_beginline). */
    boolean globalNeedBeginline;

    Ex(VimEditor editor) { this.editor = editor; }

    boolean globalBusy() { return globalBusy > 0; }

    void globalBusy(boolean busy) {
        globalBusy = busy ? 1 : 0;
        editor.marks().frozen = busy;
    }

    /** @return whether the keys running now were typed (Vim's KeyTyped) */
    boolean typed() { return normalDepth == 0; }

    /**
     * {@code :} in Normal or Visual mode: the command line opens. A count puts {@code .,.+N} on it, and Visual mode
     * ends with {@code '<,'>} on it (nv_colon, op_colon).
     */
    void open(Context c) {
        String initial = "";
        if (c.inVisual()) {
            // op_colon: the cursor goes to the start of the area (a line's start, a block's top left).
            Pos anchor = c.visual, cursor = c.cursor();
            Mode mode = c.editor.mode();
            Pos start = Pos.min(anchor, cursor).copy();
            if (mode == Mode.VISUAL_LINE) start = cursor.compareTo(anchor) <= 0 ? cursor.copy() : new Pos(anchor.line, 0);
            else if (mode == Mode.VISUAL_BLOCK) {
                int ts = c.tabstop();
                int left = Math.min(Visual.startVcol(c.buffer, anchor, ts), Visual.startVcol(c.buffer, cursor, ts));
                start.col = Text.columnAt(c.buffer.line(start.line), left, ts);
            }
            Visual.end(c.editor);
            c.editor.setCursor(start);
            initial = "'<,'>";
        } else if (c.hasCount) initial = c.count > 1 ? ".,.+" + (c.count - 1) : ".";
        Vim vim = editor.vim();
        editor.commandLine().start(':', vim.commandHistory(), initial, null, text -> {
            // Only a typed line joins the history; one typed by :normal does not.
            if (typed()) vim.rememberCommand(text);
            execute(text);
            editor.vim().registers().lastCommand(text);
        }, () -> { });
    }

    /** Runs a command line, as typed: one undo step, and what it says gathered for the output panel. */
    void execute(String line) {
        List<VimEditor.OutputLine> before = editor.startCollecting();
        boolean outerFailed = failed;
        if (before == null) failed = false;
        editor.beginUndoStep();
        Runnable finish = () -> {
            editor.endUndoStep();
            editor.stopCollecting(before, failed);
            if (before != null) failed |= outerFailed;
        };
        List<Step> saved = fresh;
        fresh = new ArrayList<>();
        boolean suspended = false;
        try {
            runLine(line);
            if (editor.prompting()) {
                ArrayDeque<Step> frame = new ArrayDeque<>(fresh);
                frame.addLast(new Step(finish, true));
                frames.push(frame);
                suspended = true;
            }
        } finally {
            fresh = saved;
            if (!suspended) finish.run();
        }
    }

    /**
     * do_cmdline: the commands of one line, separated by {@code |}; an error ends the line.
     *
     * @return false when a command failed
     */
    boolean runLine(String line) {
        String text = line;
        while (text != null) {
            Object next;
            try {
                next = one(text);
            } catch (VimFailure failure) {
                fail(failure);
                return false;
            }
            if (next == SUSPENDED) return true;
            String rest = (String) next;
            if (editor.prompting()) {
                if (rest != null) later(() -> runLine(rest));
                return true;
            }
            text = rest;
        }
        return true;
    }

    /** An error message, or the bell for a failure without one. */
    void fail(VimFailure failure) {
        if (!failure.getMessage().isEmpty()) failed = true;
        if (failure.quiet) return;
        if (failure.getMessage().isEmpty()) editor.bell();
        else editor.message(failure.getMessage(), true);
    }

    /** A command that returned with a question open leaves what it still has to do. */
    void later(Runnable work) { fresh.add(new Step(work, false)); }

    /** The answer to a question came: the asking command goes on, then what waited for it. */
    void answered(Consumer<KeyInput> handler, KeyInput key) {
        ArrayDeque<Step> frame = frames.peek();
        stretch(frame, () -> handler.accept(key));
        if (frame == null) return;
        while (!editor.prompting() && !frame.isEmpty()) {
            Step next = frame.pollFirst();
            stretch(frame, next.work());
        }
        if (frame.isEmpty()) frames.remove(frame);
    }

    /** Runs some work; continuations it registers go in front of the frame's. An error drops the frame's work. */
    private void stretch(ArrayDeque<Step> frame, Runnable body) {
        List<Step> saved = fresh;
        fresh = new ArrayList<>();
        try {
            body.run();
        } catch (VimFailure failure) {
            fail(failure);
            fresh.removeIf(step -> !step.finisher());
            if (frame != null) frame.removeIf(step -> !step.finisher());
        } finally {
            if (frame != null) for (int i = fresh.size() - 1; i >= 0; i--) frame.addFirst(fresh.get(i));
            fresh = saved;
        }
    }

    /** After an internal error or when the text is replaced: every run ends, its undo step closed. */
    void abort() {
        globalBusy(false);
        normalDepth = 0;
        while (!frames.isEmpty()) {
            for (Step step : frames.pop()) {
                if (!step.finisher()) continue;
                try { step.work().run(); }
                catch (RuntimeException ignored) { }
            }
        }
    }

    /**
     * do_one_cmd: parses one command and runs it.
     *
     * @return the text after its {@code |}, null when there is none, or {@link #SUSPENDED} when it asked a question
     *     and will run the rest of the line itself once answered
     */
    private Object one(String line) {
        ExCommand cmd = new ExCommand(editor);
        int start = skipColons(line, 0);
        String afterModifier = line.substring(start);
        // Find the command after the range first: it says what the addresses count.
        int nameStart = skipRange(line, start);
        Found found = find(line, nameStart);
        Addr addr = found.def != null ? found.def.addr() : Addr.LINES;
        int[] at = { start };
        parseAddresses(line, at, cmd, addr);
        int i = skipColons(line, at[0]);
        if (i >= line.length() || line.charAt(i) == '"' || nextCommand(line, i) >= 0) {
            int next = i < line.length() && line.charAt(i) != '"' ? nextCommand(line, i) : -1;
            withoutCommand(cmd, i < line.length() && line.charAt(i) == '|');
            return next >= 0 ? line.substring(next) : null;
        }
        if (found.name == null) throw new VimFailure("E492: Not an editor command: " + afterModifier);
        if (found.def == null) throw new VimFailure("E319: Sorry, the command is not available in this version");
        cmd.def = found.def;
        cmd.flags |= found.flags;
        cmd.typed = line.substring(i);
        int p = found.end;
        if (p < line.length() && line.charAt(p) == '!' && !cmd.name().equals("substitute")) {
            cmd.forceit = true;
            p++;
        }
        int argt = cmd.def.flags();
        if ((argt & RANGE) == 0 && cmd.addrCount > 0) throw new VimFailure("E481: No range allowed");
        if ((argt & BANG) == 0 && cmd.forceit) throw new VimFailure("E477: No ! allowed");
        int argStart = p;
        if ((argt & RANGE) != 0 && globalBusy == 0 && cmd.line1 > cmd.line2) {
            ask("Backwards range given, OK to swap (y/n)?", yes -> {
                int swap = cmd.line1;
                cmd.line1 = cmd.line2;
                cmd.line2 = swap;
                String rest;
                try {
                    rest = finish(cmd, line, argStart);
                } catch (VimFailure failure) {
                    fail(failure);
                    return;
                }
                if (editor.prompting()) {
                    if (rest != null) later(() -> runLine(rest));
                } else if (rest != null) runLine(rest);
            });
            return SUSPENDED;
        }
        return finish(cmd, line, argStart);
    }

    /** ask_yesno: {@code y} or {@code n} (or {@code <Esc>}); another key asks again. Runs {@code yes} for {@code y}. */
    private void ask(String question, Consumer<KeyInput> yes) {
        editor.ask(question, key -> {
            int c = key.plainChar();
            if (c == 'y') yes.accept(key);
            else if (c != 'n' && !key.is("<Esc>") && !key.is("<C-c>")) ask(question, yes);
        });
    }

    /** The rest of do_one_cmd, from the range checks on: the argument, and running the command. */
    private String finish(ExCommand cmd, String line, int p) {
        int argt = cmd.def.flags();
        Buffer b = editor.buffer();
        if ((argt & RANGE) != 0) {
            if (cmd.line1 < 0 || cmd.line2 < 0 || cmd.line1 > cmd.line2) throw new VimFailure("E16: Invalid range");
            if (cmd.def.addr() == Addr.LINES && cmd.line2 > b.count()) throw new VimFailure("E16: Invalid range");
        }
        if (cmd.def.addr() == Addr.OTHER && cmd.addrCount == 0) cmd.line2 = 1;
        if ((argt & ZEROR) == 0) {
            if (cmd.line1 == 0) cmd.line1 = 1;
            if (cmd.line2 == 0) cmd.line2 = 1;
        }
        if (((argt & WHOLEFOLD) != 0 || cmd.addrCount >= 2) && globalBusy == 0 && cmd.def.addr() == Addr.LINES) {
            int[] first = editor.folds().closed(cmd.line1), last = editor.folds().closed(cmd.line2);
            if (first != null) cmd.line1 = first[0];
            if (last != null) cmd.line2 = last[1];
        }
        String arg = line.substring(skipWhite(line, p));
        if (cmd.name().equals(">") || cmd.name().equals("<")) {
            cmd.amount = 1;
            char c = cmd.name().charAt(0);
            int n = 0;
            while (n < arg.length() && arg.charAt(n) == c) {
                n++;
                cmd.amount++;
            }
            arg = arg.substring(skipWhite(arg, n));
        }
        if ((argt & TRLBAR) != 0) {
            String[] split = separate(arg, argt);
            arg = split[0];
            cmd.nextcmd = split[1];
        }
        if ((argt & DFLALL) != 0 && cmd.addrCount == 0) {
            cmd.line1 = 1;
            cmd.line2 = b.count();
        }
        if ((argt & REGSTR) != 0 && !arg.isEmpty() && !((argt & COUNT) != 0 && Character.isDigit(arg.charAt(0)))) {
            char r = arg.charAt(0);
            if (Registers.valid(r, !cmd.name().equals("put"))) {
                cmd.regname = r;
                arg = arg.substring(skipWhite(arg, 1));
            }
        }
        if ((argt & COUNT) != 0 && !arg.isEmpty() && Character.isDigit(arg.charAt(0))) {
            int n = 0;
            long count = 0;
            while (n < arg.length() && Character.isDigit(arg.charAt(n))) count = Math.min(Integer.MAX_VALUE, count * 10 + arg.charAt(n++) - '0');
            arg = arg.substring(skipWhite(arg, n));
            if (count <= 0 && (argt & ZEROR) == 0) throw new VimFailure("E939: Positive count required");
            if (cmd.def.addr() != Addr.LINES) {
                cmd.line2 = (int) count;
                if (cmd.addrCount == 0) cmd.addrCount = 1;
            } else {
                cmd.line1 = cmd.line2;
                cmd.line2 = (int) Math.min((long) b.count(), cmd.line2 + count - 1);
                cmd.addrCount++;
            }
        }
        if ((argt & FLAGS) != 0) {
            int n = 0;
            while (n < arg.length() && "lp#".indexOf(arg.charAt(n)) >= 0) {
                char f = arg.charAt(n);
                cmd.flags |= f == 'l' ? LIST : f == 'p' ? PRINT : NUMBER;
                n = skipWhite(arg, n + 1);
            }
            arg = arg.substring(n);
        }
        if ((argt & EXTRA) == 0 && !arg.isEmpty() && arg.charAt(0) != '"' && (arg.charAt(0) != '|' || (argt & TRLBAR) == 0))
            throw new VimFailure("E488: Trailing characters: " + arg);
        if ((argt & NEEDARG) != 0 && arg.isEmpty()) throw new VimFailure("E471: Argument required");
        cmd.arg = arg;
        editor.vim().covered("ex " + cmd.name());
        cmd.def.handler().run(cmd);
        if (editor.cursor().line < 0) editor.setCursor(new Pos(0, 0));
        return cmd.nextcmd;
    }

    /**
     * separate_nextcmd: a {@code |} ends the argument (a backslash before it keeps it, dropping the backslash), and
     * {@code "} starts a comment unless the command takes it; CTRL-V quotes the next character.
     *
     * @return the argument without trailing white space, and the next command or null
     */
    private static String[] separate(String arg, int argt) {
        StringBuilder out = new StringBuilder();
        String next = null;
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == 0x16) {
                if ((argt & CTRLV) != 0) out.append(c);
                if (++i < arg.length()) out.append(arg.charAt(i));
                continue;
            }
            if (c == '"' && (argt & NOTRLCOM) == 0 || c == '|' || c == '\n') {
                if (c != '"' && !out.isEmpty() && out.charAt(out.length() - 1) == '\\') {
                    out.setLength(out.length() - 1);
                    out.append(c);
                    continue;
                }
                if (c == '"' && !out.isEmpty() && out.charAt(out.length() - 1) == '\\') {
                    out.setLength(out.length() - 1);
                    out.append(c);
                    continue;
                }
                int n = nextCommand(arg, i);
                next = n >= 0 ? arg.substring(n) : null;
                break;
            }
            out.append(c);
        }
        String result = out.toString();
        if ((argt & NOTRLCOM) == 0) {
            int end = result.length();
            while (end > 0 && isWhite(result.charAt(end - 1)) && !(end > 1 && result.charAt(end - 2) == '\\')) end--;
            result = result.substring(0, end);
        }
        return new String[] { result, next };
    }

    /** ex_range_without_command: {@code :3} goes to line 3; {@code :3|} and {@code :|} print it. */
    private void withoutCommand(ExCommand cmd, boolean bar) {
        Buffer b = editor.buffer();
        if (bar) {
            if (cmd.line1 < 0 || cmd.line2 < 0 || cmd.line1 > cmd.line2 || cmd.line2 > b.count()) throw new VimFailure("E16: Invalid range");
            if (cmd.line1 == 0) cmd.line1 = 1;
            if (cmd.line2 == 0) cmd.line2 = 1;
            ExInfo.printLines(cmd, false, false);
            return;
        }
        if (cmd.addrCount == 0) return;
        int line = Math.min(cmd.line2, b.count());
        if (line < 0) throw new VimFailure("E16: Invalid range");
        beginline(Math.max(1, line), true);
    }

    /**
     * beginline: the cursor to a line's first non-blank; with {@code sol} (BL_SOL) and 'nostartofline', to the
     * remembered column instead.
     */
    void beginline(int lnum, boolean sol) {
        int line = lnum - 1;
        if (sol && !editor.options().flag("startofline")) editor.setCursor(new Pos(line, editor.wantedColumn(line)));
        else {
            editor.setCursor(new Pos(line, Text.firstNonBlank(editor.buffer().line(line))));
            editor.forgetColumn();
        }
    }

    /** setpcmark, which does nothing while {@code :global} runs. */
    void setpcmark() {
        if (globalBusy == 0) editor.marks().setpcmark(editor.cursor());
    }

    private record Found(String name, Def def, int end, int flags) { }

    /** find_ex_command: the name at {@code i}, resolved like Vim's (one-letter {@code k} and {@code s} forms, {@code :dl}). */
    private static Found find(String line, int i) {
        int n = line.length();
        char c0 = i < n ? line.charAt(i) : 0;
        char c1 = i + 1 < n ? line.charAt(i + 1) : 0, c2 = i + 2 < n ? line.charAt(i + 2) : 0;
        char c3 = i + 3 < n ? line.charAt(i + 3) : 0, c4 = i + 4 < n ? line.charAt(i + 4) : 0;
        if (c0 == 'k' && (c1 != 'e' || c2 != 'e')) return found("k", i + 1, 0);
        if (c0 == 's' && (c1 == 'c' && (c2 == 0 || c2 != 's' && c2 != 'r' && (c3 == 0 || c3 != 'i' && c4 != 'p'))
            || c1 == 'g' || c1 == 'i' && c2 != 'm' && c2 != 'l' && c2 != 'g' || c1 == 'I' || c1 == 'r' && c2 != 'e'))
            return found("substitute", i + 1, 0);
        int p = i;
        while (p < n && isAlpha(line.charAt(p))) p++;
        if (p == i && p < n && "@*!=><&~#}".indexOf(line.charAt(p)) >= 0) p++;
        int len = p - i;
        int flags = 0;
        if (c0 == 'd' && len > 0 && (line.charAt(p - 1) == 'l' || line.charAt(p - 1) == 'p')) {
            int k = 0;
            while (k < len && k < 6 && line.charAt(i + k) == "delete".charAt(k)) k++;
            if (k == len - 1) {
                flags = line.charAt(p - 1) == 'l' ? LIST : PRINT;
                len--;
            }
        }
        if (len == 0) return new Found(null, null, i, 0);
        String name = ExTable.resolve(line.substring(i, i + len));
        // ":*" is the Visual range, not a command, unless 'cpoptions' has '*'.
        if ("*".equals(name)) name = null;
        return name == null ? new Found(null, null, i, 0) : found(name, p, flags);
    }

    private static Found found(String name, int end, int flags) { return new Found(name, DEFS.get(name), end, flags); }

    /** skip_range: past the characters a range may hold, to where the command's name starts. */
    private static int skipRange(String line, int start) {
        int i = start, n = line.length();
        while (i < n && " \t0123456789.$%'/?-+,;\\".indexOf(line.charAt(i)) >= 0) {
            char c = line.charAt(i);
            if (c == '\\') {
                if (i + 1 < n && "?/&".indexOf(line.charAt(i + 1)) >= 0) i++;
                else break;
            } else if (c == '\'') {
                // A quote starts a mark only at the start or after a separator.
                int q = i;
                while (q > start) {
                    q--;
                    if (!isWhite(line.charAt(q))) break;
                }
                if (i > start && !isWhite(line.charAt(q)) && line.charAt(q) != ',' && line.charAt(q) != ';') break;
                i++;
            } else if (c == '/' || c == '?') {
                i++;
                while (i < n && line.charAt(i) != c) if (line.charAt(i++) == '\\' && i < n) i++;
            }
            if (i < n) i++;
        }
        i = skipColons(line, i);
        if (i < n && line.charAt(i) == '*') i = skipWhite(line, i + 1);
        return i;
    }

    /** parse_cmd_address: the {@code ,} and {@code ;} separated addresses, {@code %} and {@code *}. */
    private void parseAddresses(String line, int[] at, ExCommand cmd, Addr addr) {
        Buffer b = editor.buffer();
        addressCursor = editor.cursor().line + 1;
        int addressCount = 1;
        int lnum = NO_ADDRESS;
        boolean movedCursor = false;
        while (true) {
            cmd.line1 = cmd.line2;
            cmd.line2 = defaultAddress(addr);
            at[0] = skipWhite(line, at[0]);
            lnum = address(line, at, addr, cmd.addrCount == 0, addressCount++);
            if (lnum == NO_ADDRESS) {
                char c = at[0] < line.length() ? line.charAt(at[0]) : 0;
                if (c == '%') {
                    at[0]++;
                    if (addr == Addr.LINES) {
                        cmd.line1 = 1;
                        cmd.line2 = b.count();
                    } else if (addr == Addr.OTHER) throw new VimFailure("E16: Invalid range");
                    cmd.addrCount++;
                } else if (c == '*') {
                    if (addr != Addr.LINES) throw new VimFailure("E16: Invalid range");
                    at[0]++;
                    cmd.line1 = MarkCommands.check(b, MarkCommands.get(editor, '<')).line + 1;
                    cmd.line2 = MarkCommands.check(b, MarkCommands.get(editor, '>')).line + 1;
                    cmd.addrCount++;
                }
            } else cmd.line2 = lnum;
            cmd.addrCount++;
            char c = at[0] < line.length() ? line.charAt(at[0]) : 0;
            if (c == ';') {
                // The cursor moves to the address, and the next one counts from there; 0 is kept, so 0;/pat/ can
                // find a match in the first line.
                addressCursor = Math.max(0, Math.min(cmd.line2, b.count()));
                if (addressCursor > 0) editor.setCursor(new Pos(addressCursor - 1, editor.cursor().col));
                movedCursor = true;
            } else if (c != ',') break;
            at[0]++;
        }
        if (cmd.addrCount == 1) {
            cmd.line1 = cmd.line2;
            if (lnum == NO_ADDRESS) cmd.addrCount = 0;
        }
        if (movedCursor) editor.setCursor(editor.cursor());
    }

    /** get_address for {@code :move} and {@code :copy}'s destination, from the start of {@code text}. */
    int address(String text, int[] at) {
        addressCursor = editor.cursor().line + 1;
        return address(text, at, Addr.LINES, false, 1);
    }

    /** The cursor line addresses count from (1-based); {@code ;} moves it, perhaps to 0. */
    private int addressCursor;

    private int defaultAddress(Addr addr) {
        return addr == Addr.NONE ? 0 : Math.min(addressCursor, editor.buffer().count());
    }

    /** get_address: one address and its offsets; {@link #NO_ADDRESS} when there is none. */
    private int address(String line, int[] at, Addr addr, boolean first, int addressCount) {
        Buffer b = editor.buffer();
        int i = skipWhite(line, at[0]), n = line.length();
        int lnum = NO_ADDRESS;
        do {
            char c = i < n ? line.charAt(i) : 0;
            switch (c) {
                case '.' -> {
                    i++;
                    if (addr == Addr.NONE) throw new VimFailure("E481: No range allowed");
                    lnum = addr == Addr.LINES ? addressCursor : lnum;
                }
                case '$' -> {
                    i++;
                    if (addr == Addr.NONE) throw new VimFailure("E481: No range allowed");
                    lnum = b.count();
                }
                case '\'' -> {
                    if (++i >= n) throw VimFailure.quiet();
                    if (addr != Addr.LINES) throw new VimFailure(addr == Addr.NONE ? "E481: No range allowed" : "E16: Invalid range");
                    Pos mark = MarkCommands.check(b, MarkCommands.get(editor, line.charAt(i)));
                    i++;
                    lnum = mark.line + 1;
                }
                case '/', '?' -> {
                    i++;
                    if (addr != Addr.LINES) throw new VimFailure(addr == Addr.NONE ? "E481: No range allowed" : "E16: Invalid range");
                    int from = lnum != NO_ADDRESS && lnum > 0 ? Math.min(lnum, b.count()) : addressCursor;
                    int[] result = editor.vim().search().address(new Context(editor), c, line.substring(i), from);
                    lnum = result[0];
                    i += result[1];
                }
                case '\\' -> {
                    i++;
                    if (addr != Addr.LINES) throw new VimFailure(addr == Addr.NONE ? "E481: No range allowed" : "E16: Invalid range");
                    char which = i < n ? line.charAt(i) : 0;
                    if ("&?/".indexOf(which) < 0 || which == 0) throw new VimFailure("E10: \\ should be followed by /, ? or &");
                    int from = lnum != NO_ADDRESS ? lnum : addressCursor;
                    lnum = editor.vim().search().repeatAddress(new Context(editor), which, from);
                    i++;
                }
                default -> {
                    if (Character.isDigit(c)) {
                        long v = 0;
                        while (i < n && Character.isDigit(line.charAt(i))) v = Math.min(Integer.MAX_VALUE - 1, v * 10 + line.charAt(i++) - '0');
                        lnum = (int) v;
                    }
                }
            }
            while (true) {
                i = skipWhite(line, i);
                char d = i < n ? line.charAt(i) : 0;
                if (d != '-' && d != '+' && !Character.isDigit(d)) break;
                if (lnum == NO_ADDRESS) lnum = addr == Addr.LINES ? addressCursor : addr == Addr.OTHER ? 1 : 0;
                char sign;
                if (Character.isDigit(d)) sign = '+';
                else {
                    sign = d;
                    i++;
                }
                long v = 1;
                if (i < n && Character.isDigit(line.charAt(i))) {
                    v = 0;
                    while (i < n && Character.isDigit(line.charAt(i))) v = Math.min(Integer.MAX_VALUE - 1, v * 10 + line.charAt(i++) - '0');
                }
                // After the first address, relative addresses count a closed fold as one line.
                if (addr == Addr.LINES && addressCount >= 2) {
                    int[] fold = editor.folds().closed(lnum);
                    if (fold != null) lnum = fold[1];
                }
                long moved = sign == '-' ? lnum - v : lnum + v;
                if (moved >= Integer.MAX_VALUE - 1) throw new VimFailure("E1247: Line number out of range");
                lnum = (int) moved;
            }
        } while (i < n && (line.charAt(i) == '/' || line.charAt(i) == '?'));
        at[0] = i;
        return lnum;
    }

    /** check_nextcmd: the index after a {@code |} at {@code i} (after white space), or -1. */
    private static int nextCommand(String s, int i) {
        int j = skipWhite(s, i);
        return j < s.length() && (s.charAt(j) == '|' || s.charAt(j) == '\n') ? j + 1 : -1;
    }

    private static int skipColons(String s, int i) {
        i = skipWhite(s, i);
        while (i < s.length() && s.charAt(i) == ':') i = skipWhite(s, i + 1);
        return i;
    }

    static int skipWhite(String s, int i) {
        while (i < s.length() && isWhite(s.charAt(i))) i++;
        return i;
    }

    static boolean isWhite(char c) { return c == ' ' || c == '\t'; }

    private static boolean isAlpha(char c) { return c < 128 && Character.isLetter(c); }
}
```

- [ ] **Step 4: Replace the changed classes and the harness**

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.fife.ui.rtextarea.CaretStyle;
import org.fife.ui.rtextarea.ConfigurableCaret;
import org.fife.ui.rtextarea.RTextArea;

/**
 * Vim in one tab (spec 1.1): the mode, the parser, the insert session, the remembered column, the tab's local
 * options and its messages. Attaching it to a {@link VimTextArea} turns Vim on for that area; {@link #close} turns
 * it off and leaves the text as it is. UI thread only.
 */
public final class VimEditor {
    private static final System.Logger LOG = System.getLogger(VimEditor.class.getName());

    private final Vim vim;
    private final VimTextArea area;
    private final Buffer buffer;
    private final Options options;
    private final Machine machine;
    private final InsertMode insert;
    private final CommandLine commandLine = new CommandLine(this);
    private final Ex ex = new Ex(this);
    /** A question waiting for its answer key (Vim's ask_yesno and the {@code :s} confirmation): its text and what takes the key. */
    private String promptText;
    private java.util.function.Consumer<KeyInput> promptHandler;
    /** The output panel: what a command line said, shown until a key is typed (Vim's hit-enter prompt). */
    private List<OutputLine> output = List.of();
    /** While a command line runs, what it says, gathered for the output panel; null otherwise. */
    private List<OutputLine> collecting;
    /** Every message given, for the corpus's output comparison. */
    private final List<String> messageLog = new ArrayList<>();
    private final Marks marks;
    private final List<Runnable> listeners = new ArrayList<>();
    private final CaretListener caretListener = event -> caretMoved();
    private final CaretStyle originalCaret;
    private Mode mode = Mode.NORMAL;
    /** The display column vertical motions aim for; {@link Integer#MAX_VALUE} for the end of the line, -1 to take it from the cursor. */
    private int curswant = -1;
    private String message = "";
    private boolean messageError;
    private int bells;
    private boolean busy;
    private EditorDefaults defaults;
    /** In Visual mode, the other end of the area (Vim's VIsual). */
    private Pos visualAnchor;
    private final Highlights highlights;
    private final View view = new View(this);
    private final ViewportSync viewport;
    private final Folds folds;
    /** The text area's word-wrapping, put back when Vim turns off (Vim wraps at any character). */
    private final boolean originalWrapStyleWord;
    /** The text area's fold-aware navigation, put back when Vim turns off (Vim's cursor may be inside a closed fold). */
    private final javax.swing.text.NavigationFilter originalNavigation;
    /** While a search pattern is typed with 'incsearch', the match it would find ({start, end} offsets), or null. */
    private int[] incsearch;
    /** While {@code :s} asks for confirmation, the match it asks about ({start, end} offsets), or null. */
    private int[] confirmMatch;
    /** While 'incsearch' previews a match, the view as it was when the command line opened (Vim's old_viewstate). */
    private int[] incsearchView;
    /** While the view scrolls to an 'incsearch' match, the match's start stands in for the cursor, which stays put. */
    private Pos previewCursor;
    /** Bumped by every text change, so the 'hlsearch' matches are found again only when needed. */
    private int textVersion;
    private List<Object> highlightedPattern;
    private int highlightedVersion = -1;
    private List<int[]> searchMatches = List.of();
    private boolean highlightPending;
    private java.util.function.Supplier<String> documentName = () -> "[No Name]";
    private java.util.function.BooleanSupplier documentModified = () -> false;
    /** Leaving the tab sets the {@code '"} mark (spec 3.6). */
    private final java.awt.event.FocusListener leaveListener = new java.awt.event.FocusAdapter() {
        @Override public void focusLost(java.awt.event.FocusEvent event) { marks.set('"', cursor()); }
    };
    private final DocumentListener highlightListener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { textChanged(); }
        @Override public void removeUpdate(DocumentEvent event) { textChanged(); }
        @Override public void changedUpdate(DocumentEvent event) { }
    };

    /**
     * Turns Vim on for the area.
     *
     * @param vim the global state
     * @param area the tab's text area
     * @param defaults the Editor's option defaults for the tab
     */
    public VimEditor(Vim vim, VimTextArea area, EditorDefaults defaults) {
        this.vim = vim;
        this.area = area;
        this.buffer = new Buffer(area.getDocument());
        this.marks = new Marks(area.getDocument());
        this.options = vim.options().local();
        this.machine = new Machine(this);
        this.insert = new InsertMode(this);
        this.highlights = new Highlights(area);
        this.folds = new Folds(area);
        this.viewport = new ViewportSync(this, area);
        this.originalWrapStyleWord = area.getWrapStyleWord();
        area.setWrapStyleWord(false);
        this.originalNavigation = area.getNavigationFilter();
        area.setNavigationFilter(null);
        area.getDocument().addDocumentListener(highlightListener);
        area.addFocusListener(leaveListener);
        originalCaret = area.getCaret() instanceof ConfigurableCaret caret ? caret.getStyle() : CaretStyle.VERTICAL_LINE_STYLE;
        editorDefaults(defaults);
        options.onChanged(this::syncArea);
        syncArea();
        area.attach(this);
        area.addCaretListener(caretListener);
        busy = true;
        try {
            area.select(area.getCaretPosition(), area.getCaretPosition());
            setCursor(cursor());
        } finally { busy = false; }
        look();
    }

    /** Turns Vim off: the caret and input methods return to the Editor's; the text is untouched. */
    public void close() {
        ex.abort();
        promptHandler = null;
        output = List.of();
        insert.abort();
        machine.abort();
        area.removeCaretListener(caretListener);
        area.attach(null);
        area.setCaretStyle(RTextArea.INSERT_MODE, originalCaret);
        area.enableInputMethods(true);
        marks.close();
        area.getDocument().removeDocumentListener(highlightListener);
        area.removeFocusListener(leaveListener);
        area.setWrapStyleWord(originalWrapStyleWord);
        area.setNavigationFilter(originalNavigation);
        viewport.close();
        highlights.clear();
        vim.forget(this);
        buffer.close();
    }

    /**
     * The Editor's defaults changed (its tab size, or Tabs/Spaces): the tab's options follow.
     *
     * @param changed the new defaults
     */
    public void editorDefaults(EditorDefaults changed) {
        if (changed.equals(defaults)) return;
        defaults = changed;
        options.setLocal("tabstop", changed.tabstop());
        options.setLocal("shiftwidth", changed.tabstop());
        options.setLocal("expandtab", changed.expandtab());
        options.setLocal("autoindent", changed.autoindent());
        options.setLocal("wrap", changed.wrap());
    }

    /** The text was replaced (a reload): any command or insert in progress is dropped. */
    public void textReplaced() {
        ex.abort();
        promptHandler = null;
        output = List.of();
        insert.abort();
        machine.abort();
        mode = Mode.NORMAL;
        visualAnchor = null;
        marks.clear();
        busy = true;
        try { setCursor(cursor()); }
        finally { busy = false; }
        look();
        fire();
    }

    /**
     * The handler rule (spec 2.2): gives the key to Vim when Vim takes it.
     *
     * @param key a key from the text area
     * @return whether Vim took it
     */
    public boolean offer(KeyInput key) {
        if (commandLine.active() || promptHandler != null || !output.isEmpty()) {
            // The command line takes every key but the ⌘ ones.
            if ((key.modifiers() & KeyInput.META) != 0) return false;
            feed(key);
            return true;
        }
        boolean mine = switch (vim.handlers().handler(key, handlerMode())) {
            case VIM -> true;
            case IDE -> false;
            case DEFAULT -> (key.modifiers() & KeyInput.META) == 0 && (mode.inserting() ? insert.wants(key) : machine.wants(key));
        };
        if (mine) feed(key);
        return mine;
    }

    private char handlerMode() {
        return switch (mode()) {
            case INSERT, REPLACE -> 'i';
            case OPERATOR_PENDING -> 'o';
            case VISUAL, VISUAL_LINE, VISUAL_BLOCK -> 'x';
            default -> 'n';
        };
    }

    /** @return whether the mode is Insert or Replace, where keys Vim declines go on to the text area */
    boolean editing() { return mode.inserting(); }

    /**
     * For a key {@link #offer} declined: whether it goes on to the text area anyway. In Insert and Replace mode every
     * declined key does; elsewhere only a ⌘ key or one a {@code sethandler} entry gives to the Editor. The text area
     * also passes on a key bound to one of the Editor's own commands (spec 2.2).
     */
    boolean passesOn(KeyInput key) {
        if (commandLine.active() || promptHandler != null || !output.isEmpty()) return (key.modifiers() & KeyInput.META) != 0;
        if (mode.inserting() || (key.modifiers() & KeyInput.META) != 0) return true;
        return vim.handlers().handler(key, handlerMode()) == KeyHandlers.Handler.IDE;
    }

    /** A key the text area held back outside Insert mode: Vim has no meaning for it, so the bell rings. */
    void declined() {
        bells++;
        fire();
    }

    /** @return the window onto the text (topline, size, 'wrap') */
    View view() { return view; }

    /**
     * The user scrolled the text area to show {@code topline} first: the view follows, and the cursor moves inside
     * the window when it was scrolled out, keeping its column, as with Vim's scroll wheel.
     */
    void viewScrolled(int topline) {
        if (mode.inserting() || commandLine.active()) {
            view.topline(topline);
            view.compBotline();
            return;
        }
        view.topline(topline);
        view.compBotline();
        int lnum = cursor().line + 1;
        int target = lnum < view.topline ? view.topline : lnum >= view.botline && view.botline <= buffer.count() ? view.botline - 1 : lnum;
        boolean was = busy;
        if (target != lnum) {
            busy = true;
            try { setCursor(new Pos(target - 1, wantedColumn(target - 1)), mode.visual()); }
            finally { busy = was; }
        }
        if (!was) fire();
    }

    /** The viewport's size changed the view's: outside a command, the view scrolls to keep the cursor shown. */
    void viewResized() {
        // The constructor sizes the view before it has its viewport sync.
        if (viewport == null || busy || commandLine.active()) return;
        view.updateTopline();
        view.cursColumns();
        viewport.show();
    }

    /** @return the tab's code folds */
    Folds folds() { return folds; }

    /** @return the remembered display column, computed from the cursor when it was forgotten (Vim's w_curswant) */
    int columnWanted() { return want(false); }

    /** Rings the bell without failing the command (a key the command line has no use for). */
    void bell() { bells++; }

    /** @return the command line, active while {@code :}, {@code /} or {@code ?} reads a line */
    CommandLine commandLine() { return commandLine; }

    /** A failing Insert-mode key: the bell rings and the insert goes on, as Vim's ins_del and ins_bs beep (spec 2.3). */
    void insertKeyFailed(VimFailure failure) {
        bells++;
        if (!failure.getMessage().isEmpty()) message(failure.getMessage(), true);
        fire();
    }

    /**
     * Runs one key, as typed. A failing command rings the bell; an unexpected exception cancels the command, closes
     * its undo step and returns to Normal mode (spec 10).
     *
     * @param key the key
     */
    public void feed(KeyInput key) {
        busy = true;
        message = "";
        messageError = false;
        boolean vertical = key.is("<Up>") || key.is("<Down>");
        if (mode.inserting() && !vertical) curswant = -1;
        try {
            dispatch(key);
        } catch (VimFailure failure) {
            if (!failure.quiet) bells++;
            if (!failure.getMessage().isEmpty()) message(failure.getMessage(), true);
            if (!mode.inserting()) {
                machine.reset();
                machine.resumeIfInsertNormal();
            }
        } catch (RuntimeException bug) {
            LOG.log(System.Logger.Level.ERROR, "Vim command failed", bug);
            ex.abort();
            promptHandler = null;
            insert.abort();
            machine.abort();
            mode = Mode.NORMAL;
            message("Internal error; the command was cancelled", true);
        } finally {
            busy = false;
            if (!commandLine.active()) marks.checkpcmark(cursor());
            if (mode == Mode.NORMAL || mode == Mode.OPERATOR_PENDING) setCursor(cursor());
            // While the command line reads a pattern the cursor stays and the view shows 'incsearch''s match.
            if (!commandLine.active()) {
                view.updateTopline();
                view.cursColumns();
                viewport.show();
            }
            look();
            fire();
        }
    }

    /** Runs one key without the failure handling, for {@code .}, a count's repeat and {@code :normal}. */
    void dispatch(KeyInput key) {
        if (promptHandler != null) {
            answer(key);
            return;
        }
        if (!output.isEmpty()) {
            // The hit-enter prompt: these keys only take the output away; any other key then runs as typed.
            output = List.of();
            if (key.is("<CR>") || key.is(" ") || key.is("<Esc>") || key.is("<C-j>") || key.is("<kEnter>")) return;
        }
        if (commandLine.active()) commandLine.key(key);
        else if (mode.inserting()) insert.key(key);
        else machine.key(key);
    }

    /** Text an input method committed in Insert mode, recorded for {@code .}. */
    void typedByInputMethod(String text) { insert.typedByInputMethod(text); }

    /** @return the mode */
    public Mode mode() {
        if (commandLine.active() || promptHandler != null) return Mode.COMMAND_LINE;
        return mode == Mode.NORMAL && machine.operatorPending() ? Mode.OPERATOR_PENDING : mode;
    }

    /** @return the partly typed command ('showcmd') */
    public String showcmd() {
        if (!options.flag("showcmd")) return "";
        String pending = machine.showcmd();
        return pending.isEmpty() && mode.visual() && visualAnchor != null ? visualSize() : pending;
    }

    /**
     * The Visual area's size as Vim's showcmd shows it: {@code 3x5} (lines by display columns) for a block, the line
     * count for a linewise or multi-line area, else the character count, with the UTF-8 byte count after it when it
     * differs ({@code 3-4}).
     */
    private String visualSize() {
        Pos cursor = cursor();
        int lines = Math.abs(cursor.line - visualAnchor.line) + 1;
        if (mode == Mode.VISUAL_BLOCK) {
            int ts = options.number("tabstop");
            int left = Math.min(Visual.startVcol(buffer, visualAnchor, ts), Visual.startVcol(buffer, cursor, ts));
            int right = Math.max(Visual.endVcol(buffer, visualAnchor, ts), Visual.endVcol(buffer, cursor, ts));
            return lines + "x" + (right - left + 1);
        }
        if (mode == Mode.VISUAL_LINE || lines > 1) return String.valueOf(lines);
        String line = buffer.line(cursor.line);
        int from = Math.min(cursor.col, visualAnchor.col), to = Math.max(cursor.col, visualAnchor.col);
        to = to < line.length() ? Text.next(line, to) : line.length();
        String part = line.substring(Math.min(from, line.length()), to);
        int chars = part.codePointCount(0, part.length()), bytes = part.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (from >= line.length()) chars = bytes = 1;
        return bytes == chars ? String.valueOf(chars) : chars + "-" + bytes;
    }

    /** @return the last message, or empty */
    public String message() { return message; }

    /** @return whether the message is an error */
    public boolean messageIsError() { return messageError; }

    /** @return how many times the bell has rung; the Vim bar flashes when it changes */
    public int bells() { return bells; }

    /** @param listener runs after every key and every change of mode or message */
    public void addListener(Runnable listener) { listeners.add(listener); }

    /** @param listener no longer runs */
    public void removeListener(Runnable listener) { listeners.remove(listener); }

    /** @return whether the Vim bar shows the mode ('showmode') */
    public boolean showmode() { return options.flag("showmode"); }

    /** @return the tab's options */
    public Options options() { return options; }

    Vim vim() { return vim; }

    VimTextArea area() { return area; }

    Buffer buffer() { return buffer; }

    Machine machine() { return machine; }

    Marks marks() { return marks; }

    /** @return in Visual mode, the other end of the area; null otherwise */
    Pos visualAnchor() { return visualAnchor == null ? null : visualAnchor.copy(); }

    void visualAnchor(Pos anchor) { visualAnchor = anchor == null ? null : anchor.copy(); }

    /** @return the remembered display column; {@link Integer#MAX_VALUE} after {@code $}, -1 when forgotten */
    int curswant() { return curswant; }

    void curswant(int value) { curswant = value; }

    InsertMode insert() { return insert; }

    void mode(Mode value) { mode = value; }

    void message(String text, boolean error) {
        messageLog.add(text);
        if (collecting != null) {
            collecting.add(new OutputLine(text, error));
            return;
        }
        message = text;
        messageError = error;
    }

    /** An informational line, such as one line of a listing; while a command line runs it goes to the output. */
    void say(String text) { message(text, false); }

    /**
     * msgmore: after lines were added or deleted, "3 more lines" or "3 fewer lines" when more than 'report' lines
     * changed; not while {@code :global} runs, which reports once at its end.
     */
    void msgmore(int n) {
        if (ex.globalBusy()) return;
        int pn = Math.abs(n);
        if (pn <= options.number("report")) return;
        say(n > 0 ? pn + (pn == 1 ? " more line" : " more lines") : pn + (pn == 1 ? " line less" : " fewer lines"));
    }

    /** @return every message given so far, for tests */
    List<String> messageLog() { return messageLog; }

    /** @return the Ex command line */
    Ex ex() { return ex; }

    /**
     * Starts gathering what is said for the output panel; returns the list gathered before, for {@link #stopCollecting}.
     * A command line run from inside another one ({@code :normal :s…}) adds to the outer one's output.
     */
    List<OutputLine> startCollecting() {
        List<OutputLine> before = collecting;
        if (collecting == null) collecting = new ArrayList<>();
        return before;
    }

    /**
     * Stops gathering: at the outermost level one line becomes the message and more lines the output panel, which
     * waits for a key (Vim's hit-enter prompt). After a failed command line only the last line is kept, as Vim
     * does not wait then.
     */
    void stopCollecting(List<OutputLine> before, boolean failed) {
        if (before != null) return;
        List<OutputLine> said = collecting;
        collecting = null;
        // After an error Vim does not wait for a key: the last line stays as the message.
        if (failed && !said.isEmpty()) said = List.of(said.getLast());
        if (said.size() == 1) {
            message = said.getFirst().text();
            messageError = said.getFirst().error();
        } else if (said.size() > 1) {
            output = List.copyOf(said);
            message = "";
            messageError = false;
        }
    }

    /**
     * A line of the output panel.
     *
     * @param text the line
     * @param error whether it is an error message
     */
    public record OutputLine(String text, boolean error) { }

    /** @return the output panel's lines, empty when it is not shown */
    public List<OutputLine> output() { return output; }

    /**
     * Asks a question that one key answers; {@code handler} gets the key. It may ask again. When no question is left
     * open, the command line goes on with what waited for the answer.
     */
    void ask(String question, java.util.function.Consumer<KeyInput> handler) {
        messageLog.add(question);
        promptText = question;
        promptHandler = handler;
    }

    /** @return whether a question waits for its answer */
    boolean prompting() { return promptHandler != null; }

    private void answer(KeyInput key) {
        var handler = promptHandler;
        promptHandler = null;
        promptText = null;
        ex.answered(handler, key);
    }

    /**
     * @return the question waiting for a key, or "Press ENTER or type command to continue" under the output panel;
     *     empty when neither is shown
     */
    public java.util.Optional<String> prompt() {
        if (promptText != null) return java.util.Optional.of(promptText);
        if (!output.isEmpty()) return java.util.Optional.of("Press ENTER or type command to continue");
        return java.util.Optional.empty();
    }

    /** @return the cursor as a position */
    Pos cursor() { return previewCursor != null ? previewCursor.copy() : buffer.pos(area.getCaretPosition()); }

    /** Moves the cursor, clamping it to the text; in Normal mode never past the last character. */
    void setCursor(Pos pos) { setCursor(pos, false); }

    /** @param pastEnd whether the cursor may sit after the last character (operators and Insert mode) */
    void setCursor(Pos pos, boolean pastEnd) {
        int line = Math.max(0, Math.min(pos.line, buffer.count() - 1));
        String text = buffer.line(line);
        int col = Math.max(0, Math.min(pos.col, text.length()));
        boolean normal = mode == Mode.NORMAL && !pastEnd && !machine.operatorPending();
        if (normal && col >= text.length() && !text.isEmpty()) col = Text.last(text);
        setCursorOffset(buffer.offset(line, col));
    }

    /** Moves the cursor in Insert mode, where it may sit after the last character. */
    void setCursorInInsert(Pos pos) { setCursor(pos, true); }

    void setCursorOffset(int offset) {
        boolean was = busy;
        busy = true;
        try { area.setCaretPosition(Math.max(0, Math.min(offset, buffer.length()))); }
        finally { busy = was; }
    }

    /** A motion without an operator: moves, and updates the remembered column (curswant). */
    void moveTo(Target target) {
        setCursor(target.pos());
        switch (target.column()) {
            case RESET -> curswant = -1;
            case END -> curswant = Integer.MAX_VALUE;
            case KEEP -> { }
        }
    }

    /**
     * @return the column a vertical motion lands on in {@code line}, from the remembered column; in Visual mode it
     *     may be the end of the line (coladvance's one_more)
     */
    int wantedColumn(int line) {
        String text = buffer.line(line);
        boolean visual = mode.visual();
        if (curswant == Integer.MAX_VALUE) return visual ? text.length() : Text.last(text);
        int col = Text.columnAt(text, want(false), options.number("tabstop"));
        return col >= text.length() && !text.isEmpty() && !visual ? Text.last(text) : col;
    }

    /** @return the column Insert mode's Up and Down land on: the remembered column, or the end of a shorter line */
    int insertColumn(int line) {
        String text = buffer.line(line);
        if (curswant == Integer.MAX_VALUE) return text.length();
        return Text.columnAt(text, want(true), options.number("tabstop"));
    }

    /** @return whether the remembered column is beyond the cursor, as after {@code $} */
    boolean wantsPastEnd() {
        if (curswant == Integer.MAX_VALUE) return true;
        Pos p = cursor();
        return curswant > Text.virtcol(buffer.line(p.line), p.col, options.number("tabstop"));
    }

    /** The next vertical move takes its column from the cursor (Vim's w_set_curswant). */
    void forgetColumn() { curswant = -1; }

    /** @return the remembered display column, set from the cursor if forgotten; in Normal mode a tab counts at its end */
    private int want(boolean inserting) {
        if (curswant >= 0) return curswant;
        int ts = options.number("tabstop");
        Pos p = cursor();
        String text = buffer.line(p.line);
        int vcol = Text.virtcol(text, p.col, ts);
        if (!inserting && p.col < text.length() && text.charAt(p.col) == '\t') vcol += Text.width(text, p.col, vcol, ts) - 1;
        curswant = vcol;
        return vcol;
    }

    void beginUndoStep() {
        marks.newChange();
        Pos p = cursor();
        area.vimUndo().beginStep(p.line, p.col);
    }

    void endUndoStep() { area.vimUndo().endStep(); }

    /** {@code u}: undoes {@code count} steps, placing the cursor as Vim's u_undoredo does. */
    void undo(int count) { undoRedo(count, true); }

    /** {@code CTRL-R}. */
    void redo(int count) { undoRedo(count, false); }

    /**
     * {@code :undo N}: undoes or redoes to the text after change N (0: before the first). Jasper keeps one line of
     * history, so only the changes on it can be reached.
     */
    void undoTo(int target) {
        VimUndoManager manager = area.vimUndo();
        List<Integer> done = manager.doneSeqs(), undone = manager.undoneSeqs();
        int current = done.isEmpty() ? 0 : done.getLast();
        if (target == current) return;
        if (target < current) {
            if (target != 0 && !done.contains(target)) throw new VimFailure("E830: Undo number " + target + " not found");
            int count = 0;
            for (int i = done.size() - 1; i >= 0 && done.get(i) > target; i--) count++;
            undo(count);
        } else {
            if (!undone.contains(target)) throw new VimFailure("E830: Undo number " + target + " not found");
            redo(undone.indexOf(target) + 1);
        }
    }

    private void undoRedo(int count, boolean undo) {
        VimUndoManager manager = area.vimUndo();
        for (int n = 0; n < count; n++) {
            if (undo ? !manager.canUndo() : !manager.canRedo()) {
                if (n == 0) throw new VimFailure(undo ? "Already at oldest change" : "Already at newest change");
                break;
            }
            VimUndoManager.Step step = undo ? manager.nextUndo() : manager.nextRedo();
            int[] top = {Integer.MAX_VALUE};
            DocumentListener watch = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { top[0] = Math.min(top[0], changedLine(event, true)); }
                @Override public void removeUpdate(DocumentEvent event) { top[0] = Math.min(top[0], changedLine(event, false)); }
                @Override public void changedUpdate(DocumentEvent event) { }
            };
            buffer.document().addDocumentListener(watch);
            try {
                if (undo) manager.undo();
                else manager.redo();
            } finally { buffer.document().removeDocumentListener(watch); }
            int line = Math.min(top[0] == Integer.MAX_VALUE ? cursor().line : top[0], buffer.count() - 1);
            Pos cursor;
            if (step != null && step.cursorLine >= 0) {
                if (step.cursorLine + 1 == line && line > 0) line--;
                cursor = step.cursorLine == line ? new Pos(line, step.cursorCol) : new Pos(line, Text.firstNonBlank(buffer.line(line)));
            } else cursor = new Pos(line, Text.firstNonBlank(buffer.line(line)));
            setCursor(cursor);
            curswant = -1;
        }
    }

    /** @return the first line an undo event changed; text inserted at a line's end that starts with a break changes the next line */
    private int changedLine(DocumentEvent event, boolean inserted) {
        int line = buffer.lineOf(event.getOffset());
        if (inserted && event.getOffset() == buffer.end(line) && buffer.charAt(event.getOffset()) == '\n') return line + 1;
        return line;
    }

    /** The caret moved by the mouse or the Editor: in Normal mode it is kept off the end of the line. */
    private void caretMoved() {
        if (busy || mode != Mode.NORMAL || machine.pending()) return;
        // An outside selection (a mouse drag, the Find bar, Select All) becomes a Visual area (spec 3.1).
        if (area.getSelectionStart() != area.getSelectionEnd()) {
            javax.swing.SwingUtilities.invokeLater(this::selectionToVisual);
            return;
        }
        if (area.getSelectionStart() != area.getSelectionEnd()) return;
        Pos p = cursor();
        String text = buffer.line(p.line);
        if (!text.isEmpty() && p.col >= text.length()) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (area.vim().orElse(null) != this) return;
                if (!busy && mode == Mode.NORMAL && area.getSelectionStart() == area.getSelectionEnd()) {
                    busy = true;
                    try { setCursor(cursor()); }
                    finally { busy = false; }
                }
            });
        }
        curswant = -1;
    }

    /** The text area's own selection becomes a charwise Visual area, which Vim then draws. */
    private void selectionToVisual() {
        if (area.vim().orElse(null) != this || busy || mode != Mode.NORMAL) return;
        int start = area.getSelectionStart(), end = area.getSelectionEnd();
        if (start == end) return;
        int dot = area.getCaretPosition();
        int mark = dot == start ? end : start;
        busy = true;
        try {
            // Vim's area includes the character at each end; the text area's selection excludes the one at its end, so
            // that end moves back one character (onto the line's end when it was the line break).
            Pos anchor = selectionEnd(mark, dot);
            Pos head = selectionEnd(dot, mark);
            area.select(dot, dot);
            visualAnchor = anchor;
            mode = Mode.VISUAL;
            setCursor(head, true);
        } finally { busy = false; }
        look();
        fire();
    }

    /** @return one end of a selection as a Visual position: the end that is further in the text steps back a character */
    private Pos selectionEnd(int offset, int other) {
        if (offset <= other) return buffer.pos(offset);
        Pos p = buffer.pos(offset - 1);
        String line = buffer.line(p.line);
        if (p.col < line.length() && Character.isLowSurrogate(line.charAt(p.col)) && p.col > 0) p = new Pos(p.line, p.col - 1);
        return p;
    }

    /** The caret shape and input methods for the mode (spec 5.1 and 2.1). */
    private void look() {
        CaretStyle style = switch (mode()) {
            case INSERT -> CaretStyle.VERTICAL_LINE_STYLE;
            case REPLACE, OPERATOR_PENDING -> CaretStyle.UNDERLINE_STYLE;
            default -> CaretStyle.BLOCK_STYLE;
        };
        area.setCaretStyle(RTextArea.INSERT_MODE, style);
        area.enableInputMethods(mode.inserting());
    }

    private void syncArea() {
        area.setTabSize(options.number("tabstop"));
        area.setTabsEmulated(options.flag("expandtab"));
    }

    private void fire() {
        refreshHighlights();
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private void textChanged() {
        textVersion++;
        if (highlightPending) return;
        highlightPending = true;
        javax.swing.SwingUtilities.invokeLater(() -> {
            highlightPending = false;
            if (area.vim().orElse(null) == this) refreshHighlights();
        });
    }

    /** Draws the Visual area, the 'hlsearch' matches and the incremental-search match. */
    void refreshHighlights() {
        List<int[]> ranges = new ArrayList<>();
        Search search = vim.search();
        if (options.flag("hlsearch") && search.pattern() != null && !search.highlightHidden()) {
            List<Object> key = List.of(search.pattern(), options.flag("ignorecase"), options.flag("smartcase"), options.string("iskeyword"));
            if (!key.equals(highlightedPattern) || highlightedVersion != textVersion) {
                List<int[]> found = new ArrayList<>();
                for (Search.Match match : search.all(this)) {
                    int start = buffer.offset(match.start()), end = buffer.offset(match.end());
                    // An empty match is drawn one character wide, as Vim does.
                    found.add(new int[] { 1, start, end > start ? end : start + 1 });
                }
                searchMatches = found;
                highlightedPattern = key;
                highlightedVersion = textVersion;
            }
            ranges.addAll(searchMatches);
        }
        ranges.addAll(Highlights.visual(this));
        if (incsearch != null) ranges.add(new int[] { 0, incsearch[0], incsearch[1] });
        if (confirmMatch != null) ranges.add(new int[] { 0, confirmMatch[0], confirmMatch[1] });
        highlights.show(ranges);
    }

    /** Shows the match {@code :s} asks about ({start, end} offsets), or none. */
    void confirmMatch(int[] match) {
        confirmMatch = match;
        refreshHighlights();
    }

    /** @return the drawn highlights as {kind, start, end}, for tests */
    List<int[]> highlights() { return highlights.shown(); }

    /**
     * 'incsearch': while {@code /} or {@code ?} reads a pattern, the match it would find is drawn and the view scrolls
     * to it from where it was when the command line opened (ex_getln.c's may_do_incsearch_highlighting); the cursor
     * stays where it is.
     */
    void incsearch(char type, String line) {
        incsearch = null;
        if (incsearchView == null) incsearchView = view.viewstate();
        view.viewstate(incsearchView);
        if (options.flag("incsearch") && !line.isEmpty()) {
            Search.Match match = vim.search().preview(this, type, line);
            if (match != null) {
                int start = buffer.offset(match.start()), end = buffer.offset(match.end());
                incsearch = new int[] { start, Math.max(end, start + 1) };
                // The caret itself does not move: a moved caret would scroll back to itself once the key is done.
                previewCursor = match.start().copy();
                try {
                    view.updateTopline();
                    view.cursColumns();
                } finally { previewCursor = null; }
            }
        }
        viewport.show();
        refreshHighlights();
    }

    /**
     * The command line closed: the incremental-search match goes and the view is put back as it was when the line
     * opened (finish_incsearch_highlighting); after the key the view scrolls to the cursor as after any command.
     */
    void incsearchDone() {
        incsearch = null;
        if (incsearchView != null) {
            view.viewstate(incsearchView);
            incsearchView = null;
        }
    }

    /** @return while a command line is typed, its text with the {@code :}, {@code /} or {@code ?} in front; else empty */
    public java.util.Optional<String> commandLineText() {
        return commandLine.active() ? java.util.Optional.of(commandLine.type() + commandLine.text()) : java.util.Optional.empty();
    }

    /** @return the command line's cursor, as an index into {@link #commandLineText()} */
    public int commandLineCursor() { return commandLine.cursor() + 1; }

    /**
     * @param name the document's name for {@code <C-g>}
     * @param modified whether the document has unsaved changes
     */
    public void documentInfo(java.util.function.Supplier<String> name, java.util.function.BooleanSupplier modified) {
        documentName = name;
        documentModified = modified;
    }

    String documentName() { return documentName.get(); }

    boolean documentModified() { return documentModified.getAsBoolean(); }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java` with:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Target.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Normal-mode command parser (spec 1): {@code ["x][count]command}, where a command is an action, a motion, or
 * an operator followed by {@code [count][v|V]} and a motion or text object. It records each change for {@code .}
 * and brackets it in one undo step; a change that enters Insert mode stays open until the insert ends.
 */
final class Machine {
    private final VimEditor editor;
    private final Vim vim;
    private int count1, count2;
    private char register;
    private String operator;
    private char forced;
    /** The forced motion type for a block ({@code d<C-v>j}). */
    static final char CTRL_V = (char) 0x16;
    private String keys = "";
    private final List<KeyInput> shown = new ArrayList<>();
    private Consumer<KeyInput> argument;
    /** The keys of the change being typed, without counts or register, for {@code .}. */
    private final List<KeyInput> record = new ArrayList<>();
    private char recordRegister;
    private int recordCount;
    /** For a Visual-mode change being recorded, the size of its area. */
    private Visual.Redo recordVisual, visualRedo;
    /** The command line typed for the motion being run ({@code /pattern}), or null. */
    private String line;
    private boolean recordingInsert, replaying, resumeReplace;
    private int resumeEndLine = -1;
    private int changeDepth;

    Machine(VimEditor editor) {
        this.editor = editor;
        this.vim = editor.vim();
    }

    boolean operatorPending() { return operator != null; }

    /** @return whether a command is partly typed */
    boolean pending() { return operator != null || argument != null || !keys.isEmpty() || count1 > 0 || count2 > 0 || register != 0; }

    /** @return the partly typed command, for 'showcmd' */
    String showcmd() { return KeyInput.format(shown); }

    boolean replaying() { return replaying; }

    /** @return whether the key means something now, for the handler rule */
    boolean wants(KeyInput key) {
        if (argument != null || key.plainChar() >= 0 || key.is("<Esc>")) return true;
        if (key.is("<C-c>")) return pending() || editor.mode() == Mode.INSERT_NORMAL || editor.mode().visual();
        String candidate = keys + key.notation();
        VimCommands commands = vim.commands();
        if (editor.mode().visual()) return commands.visualKey(candidate) != null || commands.visualPrefix(candidate);
        VimCommands.Entry entry = operator == null ? commands.normal(candidate) : commands.pending(candidate);
        return entry != null || commands.prefix(candidate, operator != null);
    }

    void key(KeyInput key) {
        shown.add(key);
        if (argument != null) {
            Consumer<KeyInput> handler = argument;
            argument = null;
            if (key.is("<Esc>")) {
                reset();
                resumeIfInsertNormal();
                return;
            }
            handler.accept(key);
            return;
        }
        if (key.is("<Esc>") || key.is("<C-c>")) {
            reset();
            if (editor.mode().visual()) Visual.end(editor);
            resumeIfInsertNormal();
            return;
        }
        int c = key.plainChar();
        if (keys.isEmpty() && c >= '0' && c <= '9' && (c != '0' || (operator == null ? count1 : count2) > 0)) {
            if (operator == null) count1 = Math.min(count1 * 10 + (c - '0'), 99_999_999);
            else count2 = Math.min(count2 * 10 + (c - '0'), 99_999_999);
            return;
        }
        if (keys.isEmpty() && operator == null && c == '"') {
            argument = name -> {
                int n = name.plainChar();
                if (n < 0 || !Registers.valid((char) n, false)) throw Context.fail();
                register = (char) n;
            };
            return;
        }
        if (editor.mode().visual()) {
            keys += key.notation();
            record.add(key);
            visualKey();
            return;
        }
        if (operator != null && keys.isEmpty() && (c == 'v' || c == 'V' || key.is("<C-v>"))) {
            forced = c == 'v' || c == 'V' ? (char) c : CTRL_V;
            record.add(key);
            return;
        }
        keys += key.notation();
        record.add(key);
        if (operator != null && doubled()) {
            applyOperator(context(null), lineSpan(context(null)));
            return;
        }
        VimCommands.Entry entry;
        if (operator == null) entry = keys.equals("~") && editor.options().flag("tildeop") ? vim.commands().normal("g~") : vim.commands().normal(keys);
        else entry = vim.commands().pending(keys);
        if (entry == null) {
            if (vim.commands().prefix(keys, operator != null)) return;
            throw Context.fail();
        }
        if (entry.needsChar()) {
            VimCommands.Entry waiting = entry;
            argument = arg -> {
                record.add(arg);
                run(waiting, arg);
            };
            return;
        }
        if (vim.commands().needsLine(entry)) {
            readLine(entry, false);
            return;
        }
        run(entry, null);
    }

    /**
     * {@code /} and {@code ?}: the command line reads the pattern; {@code <CR>} runs the motion with it, and
     * cancelling drops the command (in Visual mode the area stays).
     */
    private void readLine(VimCommands.Entry entry, boolean visual) {
        char type = entry.keys().charAt(0);
        editor.commandLine().start(type, vim.search().history(), text -> editor.incsearch(type, text), text -> {
            editor.incsearchDone();
            record.addAll(editor.commandLine().typed());
            line = text;
            if (visual) runVisual(entry, null);
            else run(entry, null);
        }, () -> {
            editor.incsearchDone();
            reset();
            resumeIfInsertNormal();
        });
    }

    /** Motions and the 'foldopen' item that makes them open the folds at the cursor (normal.c's FDO_ checks). */
    private static final java.util.Map<String, String> FOLDOPEN = foldopenTable();

    private static java.util.Map<String, String> foldopenTable() {
        var table = new java.util.HashMap<String, String>();
        for (String k : List.of("h", "l", "<Left>", "<Right>", "<BS>", " ", "$", "<End>", "f", "F", "t", "T", ";", ",", "w", "W", "b", "B",
            "e", "E", "0", "^", "<S-Right>", "<C-Right>", "<S-Left>", "<C-Left>")) table.put(k, "hor");
        for (String k : List.of("(", ")", "{", "}", "[[", "]]", "[]", "][", "[(", "[{", "])", "]}")) table.put(k, "block");
        table.put("%", "percent");
        for (String k : List.of("'", "`")) table.put(k, "mark");
        for (String k : List.of("/", "?", "n", "N", "*", "#", "g*", "g#")) table.put(k, "search");
        for (String k : List.of("G", "gg")) table.put(k, "jump");
        return table;
    }

    /**
     * 'foldopen': a typed motion (not replayed by {@code .}, not after an operator) opens the folds that hide the
     * cursor. Marks, % and <C-o> only when they changed the line; h and l only when they moved.
     */
    private void foldOpen(String keys, Pos before) {
        String item = FOLDOPEN.get(keys);
        if (item == null || replaying || !editor.options().has("foldopen", item)) return;
        Pos after = editor.cursor();
        if ((item.equals("mark") || item.equals("percent")) && after.line == before.line) return;
        if (item.equals("hor") && after.equals(before) && (keys.equals("h") || keys.equals("l") || keys.equals("<Left>") || keys.equals("<Right>"))) return;
        editor.folds().openCursor(after.line + 1);
    }

    /** 'foldopen' for commands that are not motions: undo and redo ({@code undo}), the jumplist and change list ({@code mark}). */
    void foldOpenAfter(String item, Pos before) {
        if (replaying || !editor.options().has("foldopen", item)) return;
        Pos after = editor.cursor();
        if (item.equals("mark") && after.line == before.line) return;
        editor.folds().openCursor(after.line + 1);
    }

    /** A key in Visual mode: a Visual command, a motion that moves the cursor, an operator, or a text object. */
    private void visualKey() {
        VimCommands.Entry entry = vim.commands().visualKey(keys);
        if (entry == null) {
            if (vim.commands().visualPrefix(keys)) return;
            throw Context.fail();
        }
        if (entry.needsChar()) {
            argument = arg -> {
                record.add(arg);
                runVisual(entry, arg);
            };
            return;
        }
        if (vim.commands().needsLine(entry)) {
            readLine(entry, true);
            return;
        }
        runVisual(entry, null);
    }

    private void runVisual(VimCommands.Entry entry, KeyInput argument) {
        Context context = context(argument);
        context.visual = editor.visualAnchor();
        vim.covered(entry.id());
        switch (entry.kind()) {
            case MOTION -> {
                Target target = ((VimCommands.Motion) entry.handler()).move(context);
                if (target == null) throw Context.fail();
                if (target.jump()) editor.marks().setpcmark(editor.cursor());
                Pos before = editor.cursor();
                editor.moveTo(target);
                foldOpen(entry.keys(), before);
                complete(false);
            }
            case OBJECT -> {
                if (((VimCommands.TextObject) entry.handler()).select(context) == null) throw Context.fail();
                complete(false);
            }
            case OPERATOR -> Visual.operate(context, entry.keys(), false);
            case VISUAL -> {
                ((VimCommands.Action) entry.handler()).run(context);
                if (!keys.isEmpty()) complete(false);
            }
            default -> throw Context.fail();
        }
    }

    /**
     * An operator on a Visual area (do_pending_operator with VIsual_active): Visual mode ends, the area is
     * remembered for gv, and the operator runs on the span, recorded for {@code .} with the area's size.
     */
    void applyVisual(Context context, String operatorKeys, Visual.Range range, Mode original) {
        Visual.remember(editor, original);
        editor.visualAnchor(null);
        editor.mode(Mode.NORMAL);
        operator = operatorKeys;
        visualRedo = range.redo();
        Span span = range.span();
        switch (operatorKeys) {
            case "J", "gJ", "r", "I", "A", "<C-a>", "<C-x>", "g<C-a>", "g<C-x>" -> {
                boolean change = true;
                editor.forgetColumn();
                editor.setCursor(span.start(), true);
                beginChange();
                try {
                    switch (operatorKeys) {
                        case "J", "gJ" -> Operators.joinVisual(context, span, operatorKeys.equals("J"));
                        case "r" -> Operators.replace(context, span);
                        case "<C-a>", "<C-x>", "g<C-a>", "g<C-x>" -> Numbers.visual(context, span, operatorKeys.endsWith("<C-x>"), operatorKeys.startsWith("g"));
                        default -> Operators.insert(context, span, operatorKeys.equals("A"));
                    }
                } catch (RuntimeException failure) {
                    endChangeUnlessInserting();
                    throw failure;
                }
                complete(change);
            }
            default -> applyOperator(context, span);
        }
    }

    /** A Visual-mode change that is not an operator (put): records it for {@code .} with the area's size. */
    void visualChange(Visual.Redo redo) {
        visualRedo = redo;
    }

    /** {@code dd}, {@code gUU}, {@code gUgU}, and {@code ~~} with 'tildeop'. */
    private boolean doubled() {
        if (keys.equals(operator)) return true;
        if (operator.length() == 2 && operator.charAt(0) == 'g' && keys.equals(operator.substring(1))) return true;
        return operator.equals("g~") && keys.equals("~") && editor.options().flag("tildeop");
    }

    private Context context(KeyInput argument) {
        var context = new Context(editor);
        context.hasCount = count1 > 0 || count2 > 0;
        context.count = Math.max(count1, 1) * Math.max(count2, 1);
        context.register = register;
        context.argument = argument;
        context.operator = operator;
        context.forced = forced;
        context.line = line;
        return context;
    }

    private void run(VimCommands.Entry entry, KeyInput argument) {
        Context context = context(argument);
        vim.covered(entry.id());
        switch (entry.kind()) {
            case OPERATOR -> {
                operator = entry.keys();
                count1 = context.hasCount ? context.count : 0;
                count2 = 0;
                keys = "";
            }
            case MOTION -> {
                Target target = ((VimCommands.Motion) entry.handler()).move(context);
                if (target == null) throw Context.fail();
                if (target.jump()) editor.marks().setpcmark(editor.cursor());
                if (operator != null && forced == CTRL_V) {
                    forcedBlock(target);
                    return;
                }
                if (operator == null) {
                    Pos before = editor.cursor();
                    editor.moveTo(target);
                    foldOpen(entry.keys(), before);
                    complete(false);
                } else applyOperator(context, span(context, target));
            }
            case OBJECT -> {
                Span span = ((VimCommands.TextObject) entry.handler()).select(context);
                if (span == null) throw Context.fail();
                applyOperator(context, span);
            }
            case ACTION -> {
                if (entry.keys().equals(".")) {
                    reset();
                    repeat(context);
                    resumeIfInsertNormal();
                    return;
                }
                boolean change = entry.change();
                if (!entry.keys().equals("u") && !entry.keys().equals("<C-r>") && !ViewCommands.KEEP_COLUMN.contains(entry.keys())
                    && !Ex.KEEP_COLUMN.contains(entry.keys()))
                    editor.forgetColumn();
                if (change) beginChange();
                try {
                    ((VimCommands.Action) entry.handler()).run(context);
                } catch (RuntimeException failure) {
                    if (change) endChangeUnlessInserting();
                    throw failure;
                }
                complete(change);
            }
            case INSERT -> throw new IllegalStateException("Insert-mode key in Normal mode: " + entry.keys());
        }
    }

    /**
     * The lines a doubled operator acts on (nv_lineop): the cursor line and {@code count - 1} more. Like Vim, the
     * "motion" lands on the last line's first non-blank (keeping the column for {@code yy}, and for {@code dd},
     * {@code <<} and {@code >>} with 'nostartofline'), so the span starts at whichever comes first.
     */
    private Span lineSpan(Context context) {
        Pos cursor = context.cursor();
        int last = editor.buffer().count() - 1;
        // cursor_down(count - 1): a closed fold counts as one line.
        int[] fold = editor.folds().closed(cursor.line + 1);
        if (context.count > 1 && (fold != null ? fold[1] - 1 : cursor.line) >= last) throw Context.fail();
        int end = editor.view().downFrom(cursor.line + 1, context.count - 1) - 1;
        if (forced == 'v') return new Span(cursor.copy(), new Pos(end, editor.wantedColumn(end)), Kind.EXCLUSIVE);
        String text = editor.buffer().line(end);
        boolean sol = operator.equals("d") || operator.equals(">") || operator.equals("<lt>");
        int col;
        if (operator.equals("y")) col = end == cursor.line ? cursor.col : editor.wantedColumn(end);
        else if (sol && !editor.options().flag("startofline")) col = editor.wantedColumn(end);
        else col = Text.firstNonBlank(text) >= text.length() && !text.isEmpty() ? Text.last(text) : Text.firstNonBlank(text);
        Pos target = new Pos(end, col);
        Pos start = Pos.min(cursor, target).copy(), stop = Pos.max(cursor, target).copy();
        // Closed folds are taken whole.
        int[] first = editor.folds().closed(start.line + 1), lastFold = editor.folds().closed(stop.line + 1);
        if (first != null) start = new Pos(first[0] - 1, 0);
        if (lastFold != null) stop = new Pos(lastFold[1] - 1, 0);
        return new Span(start, stop, Kind.LINEWISE);
    }

    /** Runs {@code x}, {@code D} and the other abbreviations as their operator and motion, inside their own step. */
    void alias(Context action, String operatorKeys, String motionKeys) {
        operator = operatorKeys;
        count1 = action.hasCount ? action.count : 0;
        count2 = 0;
        Context context = context(null);
        Span span;
        if (motionKeys.equals(operatorKeys)) span = lineSpan(context);
        else {
            VimCommands.Entry motion = vim.commands().pending(motionKeys);
            Target target = ((VimCommands.Motion) motion.handler()).move(context);
            if (target == null) throw Context.fail();
            span = span(context, target);
        }
        applyBody(context, span);
        operator = null;
    }

    /** The span an operator acts on for a motion: forced motion types and Vim's exclusive adjustment. */
    private Span span(Context context, Target target) {
        Pos cursor = context.cursor();
        Kind kind = target.kind();
        if (forced == 'v') kind = kind == Kind.LINEWISE ? Kind.EXCLUSIVE : kind == Kind.EXCLUSIVE ? Kind.INCLUSIVE : Kind.EXCLUSIVE;
        else if (forced == 'V') kind = Kind.LINEWISE;
        Pos orig = cursor.copy(), moved = target.pos().copy();
        // do_pending_operator: closed folds are taken whole.
        Folds folds = editor.folds();
        Buffer b = editor.buffer();
        if (orig.compareTo(moved) < 0) {
            int[] f = folds.closed(orig.line + 1);
            if (f != null) orig = new Pos(f[0] - 1, 0);
            int[] g = folds.closed(moved.line + 1);
            if ((moved.col > 0 || kind != Kind.EXCLUSIVE) && g != null) moved = new Pos(g[1] - 1, b.line(g[1] - 1).length());
        } else if (kind == Kind.LINEWISE) {
            int[] f = folds.closed(moved.line + 1);
            if (f != null) moved = new Pos(f[0] - 1, 0);
            int[] g = folds.closed(orig.line + 1);
            if (g != null) orig = new Pos(g[1] - 1, b.line(g[1] - 1).length());
        }
        Pos start = Pos.min(orig, moved).copy(), end = Pos.max(orig, moved).copy();
        boolean adjusted = false;
        if (kind == Kind.EXCLUSIVE && forced == 0 && target.adjust() && end.col == 0 && end.line > start.line) {
            adjusted = true;
            end.line--;
            if (context.walk(false).inIndent(start, 0)) kind = Kind.LINEWISE;
            else {
                String line = editor.buffer().line(end.line);
                end.col = line.length();
                if (end.col > 0) {
                    end.col = Text.previous(line, end.col);
                    kind = Kind.INCLUSIVE;
                }
            }
        }
        return new Span(start, end, kind, target.regOne(), null, false, adjusted);
    }

    /**
     * An operator with a motion forced blockwise ({@code d<C-v>2j}): as in Vim, the motion's two ends become a
     * Visual block and the operator runs on it; {@code .} repeats it on a block of the same size.
     */
    private void forcedBlock(Target target) {
        Pos anchor = editor.cursor();
        String keys = operator;
        editor.visualAnchor(anchor);
        editor.mode(Mode.VISUAL_BLOCK);
        editor.moveTo(target);
        Context context = context(null);
        context.visual = anchor;
        record.clear();
        record.addAll(KeyInput.parse(keys));
        applyVisual(context, keys, Visual.range(context, Mode.VISUAL_BLOCK, keys), Mode.VISUAL_BLOCK);
    }

    private void applyOperator(Context context, Span span) {
        boolean change = !operator.equals("y");
        context.cursorStart = editor.cursor();
        editor.forgetColumn();
        editor.setCursor(span.start(), true);
        if (change) beginChange();
        try {
            applyBody(context, span);
        } catch (RuntimeException failure) {
            if (change) endChangeUnlessInserting();
            throw failure;
        }
        complete(change);
    }

    private void applyBody(Context context, Span span) {
        VimCommands.Entry entry = vim.commands().normal(operator);
        vim.covered(entry.id());
        editor.setCursor(span.start(), true);
        editor.area().vimUndo().stepCursor(span.start().line, span.start().col);
        ((VimCommands.Operator) entry.handler()).apply(context, span);
        setChangeMarks(span);
    }

    /**
     * The {@code '[} and {@code ']} marks after an operator, as each of Vim's op_* functions sets them: a delete marks
     * where the text was ({@code ']} in a block on its last line); the others mark the span, whole lines for a linewise one.
     */
    private void setChangeMarks(Span span) {
        if (operator.equals("gq") || operator.equals("gw")) return;
        Marks marks = editor.marks();
        Pos start = span.start().copy(), end = span.end().copy();
        end.line = Math.min(end.line, editor.buffer().count() - 1);
        if (operator.equals("d") || operator.equals("c")) {
            marks.set('[', start);
            marks.set(']', span.blockwise() ? new Pos(end.line, start.col) : start);
            return;
        }
        if (span.linewise()) {
            start.col = 0;
            end.col = Visual.MAX;
            if (!operator.equals("y")) {
                // op_shift and op_tilde: the last line's last character.
                String last = editor.buffer().line(end.line);
                end.col = last.isEmpty() ? 0 : Text.last(last);
            }
        } else if (!span.blockwise() && span.kind() == Target.Kind.EXCLUSIVE) Search.decl(editor.buffer(), end);
        if (operator.equals(">") || operator.equals("<lt>")) {
            String last = editor.buffer().line(end.line);
            end.col = last.isEmpty() ? 0 : Text.last(last);
        }
        marks.set('[', start);
        marks.set(']', end);
    }

    /** A command completed: records it for {@code .} and closes its undo step, unless it entered Insert mode. */
    private void complete(boolean change) {
        if (change && !replaying) {
            recordRegister = register;
            recordCount = count1 > 0 || count2 > 0 ? Math.max(count1, 1) * Math.max(count2, 1) : 0;
            recordVisual = visualRedo;
            if (editor.mode().inserting()) recordingInsert = true;
            else vim.lastChange(new Vim.Change(recordRegister, recordCount, List.copyOf(record), recordVisual));
        }
        visualRedo = null;
        if (change) endChangeUnlessInserting();
        reset();
        resumeIfInsertNormal();
    }

    /** After {@code i_CTRL-O}'s command, or an {@code <Esc>} instead of one, Insert mode resumes as a new insert. */
    void resumeIfInsertNormal() {
        if (editor.mode() != Mode.INSERT_NORMAL) return;
        // edit.c: back after the end of the line when it was there before CTRL-O, or when the column wants it ($).
        Pos p = editor.cursor();
        String line = editor.buffer().line(p.line);
        if (!line.isEmpty() && p.col == Text.last(line) && (p.line == resumeEndLine || editor.wantsPastEnd())) editor.setCursor(new Pos(p.line, line.length()), true);
        resumeEndLine = -1;
        editor.insert().begin("i", 1, resumeReplace, -1);
        beginChange();
        if (replaying) return;
        record.clear();
        record.add(KeyInput.of(resumeReplace ? 'R' : 'i'));
        recordRegister = 0;
        recordCount = 0;
        recordVisual = null;
        recordingInsert = true;
    }

    /** Drops the partly typed command, keeping an insert being recorded. */
    void reset() {
        count1 = count2 = 0;
        register = 0;
        operator = null;
        forced = 0;
        visualRedo = null;
        line = null;
        keys = "";
        argument = null;
        shown.clear();
        if (!recordingInsert) record.clear();
    }

    /** After an internal error: forgets everything, including a recorded insert, and closes every undo step. */
    void abort() {
        recordingInsert = false;
        reset();
        while (changeDepth > 0) endChange();
    }

    void recordInsert(KeyInput key) {
        if (recordingInsert && !replaying) record.add(key);
    }

    /** The insert a recorded change entered has ended: stores the change and closes its undo step. */
    void insertEnded() {
        if (recordingInsert && !replaying) {
            record.add(KeyInput.named("Esc"));
            vim.lastChange(new Vim.Change(recordRegister, recordCount, List.copyOf(record), recordVisual));
        }
        recordingInsert = false;
        record.clear();
        endChange();
    }

    /**
     * {@code i_CTRL-O} was typed: Insert mode resumes after one command.
     *
     * @param replace whether it was Replace mode
     * @param endLine the line whose end the cursor was after, or -1
     */
    void pausedInsert(boolean replace, int endLine) {
        resumeReplace = replace;
        resumeEndLine = endLine;
    }

    /** An arrow key moved in Insert mode: a new undo step, and {@code .} repeats only what follows, as {@code i}. */
    void restartInsert() {
        endChange();
        beginChange();
        if (replaying) return;
        record.clear();
        record.add(KeyInput.of('i'));
        recordRegister = 0;
        recordCount = 0;
        recordVisual = null;
        recordingInsert = true;
    }

    /** {@code .}: the last change again, the new count replacing its count; {@code "1p.} puts register 2. */
    void repeat(Context context) {
        Vim.Change change = vim.lastChange();
        if (change == null) throw Context.fail();
        // A Visual change repeats with its own count on an area of the same size (redo_VIsual).
        int count = context.hasCount && change.visual() == null ? context.count : change.count();
        char reg = change.register();
        if (reg >= '1' && reg < '9') reg++;
        List<KeyInput> replay = new ArrayList<>();
        if (reg != 0) {
            replay.add(KeyInput.of('"'));
            replay.add(KeyInput.of(reg));
        }
        if (count > 0) String.valueOf(count).chars().forEach(d -> replay.add(KeyInput.of(d)));
        replay.addAll(change.keys());
        replaying = true;
        beginChange();
        try {
            if (change.visual() != null) Visual.reselectForRedo(editor, change.visual());
            for (KeyInput key : replay) editor.dispatch(key);
        } finally {
            replaying = false;
            endChange();
            recordingInsert = false;
            reset();
        }
        vim.lastChange(new Vim.Change(reg, count, change.keys(), change.visual()));
    }

    private void beginChange() {
        changeDepth++;
        editor.beginUndoStep();
    }

    private void endChange() {
        if (changeDepth == 0) return;
        changeDepth--;
        editor.endUndoStep();
    }

    private void endChangeUnlessInserting() {
        if (!editor.mode().inserting()) endChange();
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every key Vim mode knows (spec 11.1): the parser looks keys up here, the coverage guard checks each entry has a
 * corpus case, and the reference document is generated from it. Keys are Vim notation, as {@link KeyInput#format}
 * writes them.
 */
public final class VimCommands {
    /** What an entry does. */
    public enum Kind { MOTION, OPERATOR, ACTION, OBJECT, INSERT, VISUAL }

    @FunctionalInterface interface Motion { Target move(Context context); }

    @FunctionalInterface interface TextObject { Span select(Context context); }

    @FunctionalInterface interface Operator { void apply(Context context, Span span); }

    @FunctionalInterface interface Action { void run(Context context); }

    @FunctionalInterface interface InsertKey { void run(InsertMode insert, KeyInput key); }

    /**
     * One command.
     *
     * @param kind what it is
     * @param keys its keys in Vim notation
     * @param description what it does, for the reference
     * @param needsChar whether it reads one more key as its argument ({@code f}, {@code r})
     * @param change whether it changes text, so {@code .} repeats it and it is one undo step
     * @param handler a {@link Motion}, {@link TextObject}, {@link Operator}, {@link Action} or {@link InsertKey}
     */
    public record Entry(Kind kind, String keys, String description, boolean needsChar, boolean change, Object handler) {
        /** @return the coverage id, such as {@code motion w} */
        public String id() { return kind.name().toLowerCase(java.util.Locale.ROOT) + " " + keys; }
    }

    private final Map<String, Entry> normal = new LinkedHashMap<>();
    private final Map<String, Entry> objects = new LinkedHashMap<>();
    private final Map<String, Entry> insert = new LinkedHashMap<>();
    private final Map<String, Entry> visual = new LinkedHashMap<>();
    private final Set<String> normalPrefixes = new HashSet<>(), pendingPrefixes = new HashSet<>(), visualPrefixes = new HashSet<>();

    private VimCommands() { }

    /** @return the standard commands */
    public static VimCommands standard() {
        var commands = new VimCommands();
        Motions.register(commands);
        TextObjects.register(commands);
        Operators.register(commands);
        NormalCommands.register(commands);
        InsertMode.register(commands);
        Visual.register(commands);
        Search.register(commands);
        MarkCommands.register(commands);
        Numbers.register(commands);
        Format.register(commands);
        ViewCommands.register(commands);
        Ex.register(commands);
        return commands;
    }

    /** @return every entry, in registration order */
    public List<Entry> entries() {
        List<Entry> all = new ArrayList<>(normal.values());
        all.addAll(objects.values());
        all.addAll(visual.values());
        all.addAll(insert.values());
        return Collections.unmodifiableList(all);
    }

    void motion(String keys, String description, Motion motion) { add(normal, new Entry(Kind.MOTION, keys, description, false, false, motion)); }

    /** A motion that reads a command line first ({@code /} and {@code ?}); the line is in {@link Context#line}. */
    void motionWithLine(String keys, String description, Motion motion) {
        motion(keys, description, motion);
        lineCommands.add(keys);
    }

    /** @return whether the entry reads a command line before it runs */
    boolean needsLine(Entry entry) { return entry.kind() == Kind.MOTION && lineCommands.contains(entry.keys()); }

    private final java.util.Set<String> lineCommands = new java.util.HashSet<>();

    void motionWithChar(String keys, String description, Motion motion) { add(normal, new Entry(Kind.MOTION, keys, description, true, false, motion)); }

    void operator(String keys, String description, Operator operator) { add(normal, new Entry(Kind.OPERATOR, keys, description, false, !keys.equals("y"), operator)); }

    void action(String keys, String description, boolean change, Action action) { add(normal, new Entry(Kind.ACTION, keys, description, false, change, action)); }

    void actionWithChar(String keys, String description, boolean change, Action action) { add(normal, new Entry(Kind.ACTION, keys, description, true, change, action)); }

    void object(String keys, String description, TextObject object) { add(objects, new Entry(Kind.OBJECT, keys, description, false, false, object)); }

    void insert(String keys, String description, InsertKey key) { add(insert, new Entry(Kind.INSERT, keys, description, false, false, key)); }

    /** A Visual-mode command; {@code change} commands are repeated by {@code .} on an area of the same size. */
    void visual(String keys, String description, boolean needsChar, boolean change, Action action) {
        add(visual, new Entry(Kind.VISUAL, keys, description, needsChar, change, action));
    }

    /** @return the Normal-mode command with exactly these keys, or null */
    Entry normal(String keys) { return normal.get(keys); }

    /** @return the motion or text object with exactly these keys after an operator, or null */
    Entry pending(String keys) {
        Entry entry = normal.get(keys);
        if (entry != null && entry.kind() == Kind.MOTION) return entry;
        return objects.get(keys);
    }

    /** @return the Insert-mode key, or null */
    Entry insertKey(String keys) { return insert.get(keys); }

    /** @return in Visual mode: a Visual command, else a motion or operator, else a text object; or null */
    Entry visualKey(String keys) {
        Entry entry = visual.get(keys);
        if (entry != null) return entry;
        entry = normal.get(keys);
        if (entry != null && (entry.kind() == Kind.MOTION || entry.kind() == Kind.OPERATOR)) return entry;
        return objects.get(keys);
    }

    /** @return whether more keys could complete a Visual-mode command */
    boolean visualPrefix(String keys) { return visualPrefixes.contains(keys); }

    /** @return whether more keys could complete a command */
    boolean prefix(String keys, boolean pending) { return (pending ? pendingPrefixes : normalPrefixes).contains(keys); }

    private void add(Map<String, Entry> map, Entry entry) {
        if (map.putIfAbsent(entry.keys(), entry) != null) throw new IllegalStateException("Duplicate command " + entry.keys());
        if (entry.kind() == Kind.INSERT) return;
        List<KeyInput> keys = KeyInput.parse(entry.keys());
        for (int n = 1; n < keys.size(); n++) {
            String prefix = KeyInput.format(keys.subList(0, n));
            if (entry.kind() != Kind.OBJECT && entry.kind() != Kind.VISUAL) normalPrefixes.add(prefix);
            if (entry.kind() == Kind.MOTION || entry.kind() == Kind.OBJECT) pendingPrefixes.add(prefix);
            if (entry.kind() != Kind.ACTION) visualPrefixes.add(prefix);
        }
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Operators.java` with:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Registers.Content;
import dev.jasper.editor.vim.Target.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Operators (spec 4.1), ported from Vim's ops.c. The machine has already put the cursor on the span's start, as
 * do_pending_operator does; each operator then places it as Vim does.
 */
final class Operators {
    private Operators() { }

    static void register(VimCommands commands) {
        commands.operator("d", "delete", (c, span) -> delete(c, span, false));
        commands.operator("c", "change: delete, then Insert mode", Operators::change);
        commands.operator("y", "yank", Operators::yank);
        commands.operator(">", "shift right by 'shiftwidth'", (c, span) -> shift(c, span, false));
        commands.operator("<lt>", "shift left by 'shiftwidth'", (c, span) -> shift(c, span, true));
        commands.operator("=", "re-indent (not Vim's C-indenting; see docs/vim-divergences.md)", Operators::reindent);
        commands.operator("g~", "toggle case", (c, span) -> changeCase(c, span, Operators::toggle));
        commands.operator("gu", "lower case", (c, span) -> changeCase(c, span, Character::toLowerCase));
        commands.operator("gU", "upper case", (c, span) -> changeCase(c, span, Character::toUpperCase));
        commands.operator("g?", "rot13", (c, span) -> changeCase(c, span, Operators::rot13));
    }

    /** op_delete. {@code change} is true for {@code c}, which keeps charwise spans charwise. */
    static void delete(Context c, Span span, boolean change) {
        int before = c.buffer.count();
        deleteSpan(c, span, change);
        if (!change) c.editor.msgmore(c.buffer.count() - before);
    }

    private static void deleteSpan(Context c, Span span, boolean change) {
        Buffer b = c.buffer;
        Span s = span;
        if (s.blockwise()) {
            deleteBlock(c, s);
            return;
        }
        if (!change && !s.visual() && !s.linewise() && s.lineCount() > 1 && c.forced == 0) {
            // A charwise delete over lines that starts in the indent and ends at the end of a line becomes linewise.
            String endLine = b.line(s.end().line);
            int after = s.end().col;
            if (after < endLine.length() && s.kind() == Kind.INCLUSIVE) after = Text.next(endLine, after);
            if (Text.blank(endLine.substring(Math.min(after, endLine.length()))) && c.walk(false).inIndent(s.start(), 0))
                s = new Span(s.start(), s.end(), Kind.LINEWISE, s.regOne());
        }
        if (s.linewise()) {
            c.registers.delete(c.register, content(c, s), s.lineCount(), s.regOne(), c.options.string("clipboard"));
            b.deleteLines(s.start().line, s.end().line);
            int line = Math.min(s.start().line, b.count() - 1);
            c.editor.setCursor(new Pos(line, Text.firstNonBlank(b.line(line))));
            return;
        }
        int from = b.offset(s.start()), to = end(b, s);
        if (to <= from) return;
        c.registers.delete(c.register, content(c, s), s.lineCount(), s.regOne(), c.options.string("clipboard"));
        b.remove(from, to);
        c.editor.setCursor(b.pos(from));
    }

    /** op_delete's block mode: each line loses its part of the block; a tab cut by an edge leaves spaces. */
    private static void deleteBlock(Context c, Span s) {
        Buffer b = c.buffer;
        c.registers.delete(c.register, content(c, s), s.lineCount(), s.regOne(), c.options.string("clipboard"));
        Pos cursor = c.cursor();
        for (int line = s.start().line; line <= s.end().line; line++) {
            String old = b.line(line);
            Block bd = prep(c, s, old, true, Block.Op.OTHER);
            if (bd.textlen == 0) continue;
            if (line == cursor.line) cursor.col = bd.textcol + bd.startspaces;
            b.setLine(line, old.substring(0, bd.textcol) + " ".repeat(bd.startspaces + bd.endspaces)
                + old.substring(Math.min(old.length(), bd.textcol + bd.textlen)));
        }
        c.editor.setCursor(cursor);
    }

    /** block_prep for one line of a block span. */
    static Block prep(Context c, Span s, String line, boolean isDel, Block.Op op) {
        Span.Columns k = s.block();
        return Block.prep(line, k.startVcol(), k.endVcol(), c.tabstop(), isDel, op, s.kind() == Kind.INCLUSIVE);
    }

    /** op_change. */
    static void change(Context c, Span span) {
        Buffer b = c.buffer;
        if (span.blockwise()) {
            changeBlock(c, span);
            return;
        }
        if (span.linewise()) {
            c.registers.delete(c.register, content(c, span), span.lineCount(), span.regOne(), c.options.string("clipboard"));
            int first = span.start().line;
            if (span.lineCount() > 1) b.deleteLines(first + 1, span.end().line);
            String line = b.line(first);
            boolean ai = c.options.flag("autoindent");
            String keep = ai ? line.substring(0, Text.firstNonBlank(line)) : "";
            b.setLine(first, keep);
            c.editor.setCursor(new Pos(first, keep.length()), true);
            c.editor.insert().begin("c", 1, false, ai ? first : -1);
            return;
        }
        int from = b.offset(span.start());
        delete(c, span, true);
        c.editor.setCursor(b.pos(from), true);
        c.editor.insert().begin("c", 1, false, -1);
    }

    /**
     * op_change's block mode: the block is deleted, Insert mode starts on the first line, and when it ends (not with
     * {@code <C-c>}) the text typed there is inserted on every other line that reaches the block.
     */
    private static void changeBlock(Context c, Span span) {
        Buffer b = c.buffer;
        int startCol = span.start().col;
        delete(c, span, true);
        Pos p = c.cursor();
        if (startCol > p.col && !b.line(p.line).isEmpty()) {
            p.col = Text.next(b.line(p.line), p.col);
            c.editor.setCursor(p, true);
        }
        int first = span.start().line;
        int textcol = p.col;
        int preTextlen = b.line(first).length();
        c.editor.insert().begin("c", 1, false, -1);
        if (span.lineCount() == 1) return;
        c.editor.insert().onEnd(() -> {
            int insLen = b.line(first).length() - preTextlen;
            if (insLen <= 0) return;
            String text = b.line(first).substring(textcol, textcol + insLen);
            for (int line = first + 1; line <= span.end().line; line++) {
                String old = b.line(line);
                Block bd = prep(c, span, old, true, Block.Op.OTHER);
                if (bd.isShort) continue;
                b.setLine(line, old.substring(0, bd.textcol) + text + old.substring(bd.textcol));
            }
            c.editor.setCursor(c.cursor());
        });
    }

    /** op_yank, with its message when more than 'report' lines were yanked. */
    static void yank(Context c, Span span) {
        c.registers.yank(c.register, content(c, span), c.options.string("clipboard"));
        if (c.register == '_') return;
        int lines = span.lineCount();
        if (!span.linewise() && !span.blockwise() && lines == 1) lines = 0;
        if (lines <= c.options.number("report")) return;
        String into = c.register == 0 ? "" : " into \"" + c.register;
        c.editor.say((span.blockwise() ? "block of " : "") + lines + (lines == 1 ? " line" : " lines") + " yanked" + into);
    }

    /** op_shift, with shift_line's 'shiftround'; a Visual area shifts {count} times. */
    static void shift(Context c, Span span, boolean left) {
        shift(c, span, left, span.visual() && c.hasCount ? c.count : 1, true);
    }

    /**
     * op_shift: each line {@code times} 'shiftwidth's; the cursor goes to the first line ({@code >>}), or for
     * {@code :>} to the last line's indent. More than 'report' lines give a message.
     */
    static void shift(Context c, Span span, boolean left, int times, boolean cursorTop) {
        shiftLines(c, span, left, times, cursorTop);
        int lines = span.lineCount();
        if (lines > c.options.number("report"))
            c.editor.say(lines + (lines == 1 ? " line " : " lines ") + (left ? "<" : ">") + "ed " + times + (times == 1 ? " time" : " times"));
    }

    private static void shiftLines(Context c, Span span, boolean left, int times, boolean cursorTop) {
        Buffer b = c.buffer;
        int sw = c.options.shiftwidth(), ts = c.tabstop();
        boolean et = c.options.flag("expandtab"), round = c.options.flag("shiftround");
        if (span.blockwise()) {
            int col = c.cursor().col;
            for (int line = span.start().line; line <= span.end().line; line++)
                if (!b.line(line).isEmpty()) shiftBlock(c, span, line, left, times);
            c.editor.setCursor(new Pos(span.start().line, col));
            return;
        }
        for (int line = span.start().line; line <= span.end().line; line++) {
            String text = b.line(line);
            if (text.isEmpty()) continue;
            int indent = Text.indent(text, ts), amount = times, width;
            if (round) {
                int i = indent / sw, j = indent % sw;
                if (j != 0 && left) amount--;
                i = left ? Math.max(0, i - amount) : i + amount;
                width = i * sw;
            } else width = left ? Math.max(0, indent - sw * amount) : indent + sw * amount;
            b.setLine(line, Text.makeIndent(width, ts, et) + text.substring(Text.firstNonBlank(text)));
        }
        if (!cursorTop) {
            // ":>": set_indent leaves the cursor after the last line's new indent.
            int last = span.end().line;
            String text = b.line(last);
            c.editor.setCursor(new Pos(last, text.isEmpty() ? 0 : Text.firstNonBlank(text)));
            return;
        }
        int first = span.start().line;
        c.editor.setCursor(new Pos(first, c.options.flag("startofline") ? Text.firstNonBlank(b.line(first)) : c.editor.wantedColumn(first)));
    }

    /** shift_block: the text from the block's start moves right, or its white space shrinks to move it left. */
    private static void shiftBlock(Context c, Span span, int line, boolean left, int amount) {
        Buffer b = c.buffer;
        int ts = c.tabstop(), total = amount * c.options.shiftwidth();
        String old = b.line(line);
        Block bd = prep(c, span, old, true, Block.Op.OTHER);
        if (bd.isShort) return;
        if (!left) {
            total += bd.preWhitesp;
            int wsVcol = bd.startVcol - bd.preWhitesp;
            int textstart = bd.textcol;
            if (bd.startspaces != 0) textstart = Text.next(old, textstart);
            int vcol = bd.startVcol;
            while (textstart < old.length() && Block.white(old.charAt(textstart))) {
                int incr = Text.width(old, textstart, vcol, ts);
                total += incr;
                vcol += incr;
                textstart++;
            }
            int tabs = 0, spaces;
            if (!c.options.flag("expandtab")) tabs = (wsVcol % ts + total) / ts;
            spaces = tabs > 0 ? (wsVcol % ts + total) % ts : total;
            int textcol = bd.textcol - (bd.preWhitespC - (bd.startspaces != 0 ? 1 : 0));
            b.setLine(line, old.substring(0, textcol) + "\t".repeat(tabs) + " ".repeat(spaces) + old.substring(textstart));
            return;
        }
        int nonWhite = bd.textcol;
        if (bd.startspaces != 0) nonWhite = Text.next(old, nonWhite);
        int nonWhiteCol = bd.startVcol;
        while (nonWhite < old.length() && Block.white(old.charAt(nonWhite))) {
            nonWhiteCol += Text.width(old, nonWhite, nonWhiteCol, ts);
            nonWhite++;
        }
        int blockSpaceWidth = nonWhiteCol - span.block().startVcol();
        int shift = Math.min(blockSpaceWidth, total);
        int destination = nonWhiteCol - shift;
        int copyEnd = bd.textcol, copyWidth = bd.startVcol;
        if (bd.startspaces != 0) copyWidth -= bd.startCharVcols;
        while (copyWidth < destination) {
            int incr = Text.width(old, copyEnd, copyWidth, ts);
            if (copyWidth + incr > destination) break;
            copyWidth += incr;
            copyEnd = Text.next(old, copyEnd);
        }
        int fill = destination - copyWidth;
        b.setLine(line, old.substring(0, copyEnd) + " ".repeat(fill) + old.substring(nonWhite));
    }

    /**
     * {@code =}: each line gets the indent of the previous non-blank line, plus one 'shiftwidth' for each bracket
     * that line leaves open, minus one when the line starts with a closing bracket. Blank lines lose their indent.
     * A recorded divergence: Vim uses C-indenting.
     */
    static void reindent(Context c, Span span) {
        Buffer b = c.buffer;
        int sw = c.options.shiftwidth(), ts = c.tabstop();
        boolean et = c.options.flag("expandtab");
        for (int line = span.start().line; line <= span.end().line; line++) {
            String text = b.line(line);
            String body = text.substring(Text.firstNonBlank(text));
            if (body.isEmpty()) {
                b.setLine(line, "");
                continue;
            }
            int previous = line - 1;
            while (previous >= 0 && Text.blank(b.line(previous))) previous--;
            int width = 0;
            if (previous >= 0) {
                String above = b.line(previous);
                width = Text.indent(above, ts) + sw * Math.max(0, openBrackets(above));
            }
            if (")]}".indexOf(body.charAt(0)) >= 0) width -= sw;
            b.setLine(line, Text.makeIndent(Math.max(0, width), ts, et) + body);
        }
        int first = span.start().line;
        c.editor.setCursor(new Pos(first, Text.firstNonBlank(b.line(first))));
    }

    private static int openBrackets(String line) {
        int depth = 0;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if ("([{".indexOf(ch) >= 0) depth++;
            else if (")]}".indexOf(ch) >= 0 && depth > 0) depth--;
        }
        // A line that starts with a closer has already been dedented; its closer does not count again.
        return depth;
    }

    /** op_tilde: maps every character of the span; the cursor stays on the span's start. */
    static void changeCase(Context c, Span span, IntUnaryOperator map) {
        Buffer b = c.buffer;
        if (span.blockwise()) {
            for (int line = span.start().line; line <= span.end().line; line++) {
                String old = b.line(line);
                Block bd = prep(c, span, old, false, Block.Op.OTHER);
                int end = Math.min(old.length(), bd.textcol + bd.textlen);
                if (end > bd.textcol && end < old.length() && Character.isHighSurrogate(old.charAt(end - 1))) end++;
                String part = old.substring(bd.textcol, end);
                StringBuilder out = new StringBuilder();
                part.codePoints().forEach(cp -> out.appendCodePoint(map.applyAsInt(cp)));
                if (!out.toString().equals(part)) b.setLine(line, old.substring(0, bd.textcol) + out + old.substring(end));
            }
            c.editor.setCursor(span.start());
            return;
        }
        int from, to;
        if (span.linewise()) {
            from = b.start(span.start().line);
            to = b.end(span.end().line);
        } else {
            from = b.offset(span.start());
            to = end(b, span);
        }
        if (to <= from) return;
        String text = b.text(from, to);
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> out.appendCodePoint(map.applyAsInt(cp)));
        if (!out.toString().equals(text)) b.replace(from, to, out.toString());
        c.editor.setCursor(span.start());
    }

    static int toggle(int cp) {
        if (Character.isUpperCase(cp)) return Character.toLowerCase(cp);
        if (Character.isLowerCase(cp)) return Character.toUpperCase(cp);
        return cp;
    }

    static int rot13(int cp) {
        if (cp >= 'a' && cp <= 'z') return 'a' + (cp - 'a' + 13) % 26;
        if (cp >= 'A' && cp <= 'Z') return 'A' + (cp - 'A' + 13) % 26;
        return cp;
    }

    /**
     * op_replace ({@code r} on a Visual area): every character becomes {@code ch}; in a block, a tab cut by an edge
     * leaves spaces, and a line break splits each line. The cursor goes to the area's start.
     */
    static void replace(Context c, Span span) {
        Buffer b = c.buffer;
        String ch;
        if (c.argument != null && (c.argument.is("<CR>") || c.argument.is("<C-j>"))) ch = c.argument.is("<CR>") ? "\r" : "\n";
        else if (c.argumentChar() >= 0) ch = Character.toString(c.argumentChar());
        else throw Context.fail();
        if (span.blockwise()) {
            Span.Columns k = span.block();
            int end = span.end().line;
            for (int line = span.start().line; line <= end; line++) {
                String old = b.line(line);
                Block bd = prep(c, span, old, true, Block.Op.REPLACE);
                if (bd.textlen == 0) continue;
                int numc = k.endVcol() - k.startVcol() + 1;
                if (bd.isShort) numc -= (k.endVcol() - bd.endVcol) + 1;
                String head = old.substring(0, bd.textcol) + " ".repeat(bd.startspaces);
                String tail = old.substring(Math.min(old.length(), bd.textcol + bd.textlen));
                if (ch.equals("\r") || ch.equals("\n")) {
                    b.setLine(line, head + "\n" + tail);
                    line++;
                    end++;
                } else b.setLine(line, head + ch.repeat(Math.max(0, numc)) + (bd.isShort ? "" : " ".repeat(bd.endspaces) + tail));
            }
            c.editor.setCursor(span.start());
            return;
        }
        Pos start = span.start(), last = span.end().copy();
        if (span.linewise()) {
            start = new Pos(start.line, 0);
            last.col = Math.max(0, b.line(last.line).length() - 1);
        } else if (span.kind() != Kind.INCLUSIVE) {
            if (last.col > 0) last.col = Text.previous(b.line(last.line), last.col);
            else if (last.line > 0) last = new Pos(last.line - 1, b.line(last.line - 1).length());
        }
        for (int line = start.line; line <= last.line; line++) {
            String old = b.line(line);
            int from = line == start.line ? start.col : 0;
            int to = line == last.line ? Math.min(old.length(), last.col < old.length() ? Text.next(old, last.col) : old.length()) : old.length();
            if (to <= from) continue;
            int chars = old.codePointCount(from, to);
            String replaced = old.substring(0, from) + ch.repeat(chars) + old.substring(to);
            if (!replaced.equals(old)) b.setLine(line, replaced);
        }
        c.editor.setCursor(start);
    }

    /** Visual J and gJ: join the area's lines (at least two). */
    static void joinVisual(Context c, Span span, boolean spaces) {
        int count = Math.max(2, span.lineCount());
        if (span.start().line + count - 1 > c.buffer.count() - 1) throw Context.fail();
        NormalCommands.joinLines(c, span.start().line, count, spaces);
    }

    /**
     * op_insert (Visual I and A): Insert mode before the area's start, or after its end; in a block, when the insert
     * ends on the first line (and not with {@code <C-c>}), the typed text goes into every other line of the block.
     */
    static void insert(Context c, Span span, boolean append) {
        Buffer b = c.buffer;
        int first = span.start().line;
        c.editor.setCursor(span.start(), true);
        if (!span.blockwise()) {
            if (append) {
                // Like an 'i' on the character after the area's end, unless the area is one display column; a
                // linewise area's end keeps the cursor's column. Vim compares op_start's column only for one line.
                Pos end = span.end();
                String line = b.line(end.line);
                int col = Math.min(end.col, Math.max(0, line.length() - 1));
                int ts = c.tabstop();
                int startVcol = span.lineCount() <= 1 ? Visual.startVcol(b, span.start(), ts) : 0;
                if (!line.isEmpty() && startVcol != Visual.endVcol(b, new Pos(end.line, col), ts)) col = Text.next(line, col);
                c.editor.setCursor(new Pos(end.line, col), true);
            }
            c.editor.insert().begin(append ? "A" : "I", 1, false, -1);
            return;
        }
        Span.Columns k = span.block();
        boolean max = k.max();
        Block bd = prep(c, span, b.line(first), true, append ? Block.Op.APPEND : Block.Op.INSERT);
        int preTextlen = b.line(first).length() - bd.textcol;
        if (append) {
            preTextlen -= bd.textlen;
            Pos p = c.cursor();
            String line = b.line(first);
            while (p.col < line.length() && p.col < bd.textcol + bd.textlen) p.col++;
            if (bd.isShort && !max) {
                b.insert(b.offset(first, line.length()), " ".repeat(bd.endspaces));
                bd.textlen += bd.endspaces;
                p.col = b.line(first).length();
            }
            c.editor.setCursor(p, true);
        }
        int textlenBefore = bd.textlen, textcolBefore = bd.textcol;
        int preLen = preTextlen;
        c.editor.insert().begin(append ? "A" : "I", 1, false, -1);
        c.editor.insert().onEnd(() -> {
            if (c.cursor().line != first) return;
            String firstLine = b.line(first);
            Block bd2 = prep(c, span, firstLine, true, append ? Block.Op.APPEND : Block.Op.INSERT);
            int textcol = textcolBefore, textlen = textlenBefore, pre = preLen;
            if (!max || bd2.textlen < textlen) {
                if (append) {
                    pre += bd2.textlen - textlen;
                    if (bd2.endspaces != 0) bd2.textlen--;
                }
                textcol = bd2.textcol;
                textlen = bd2.textlen;
            }
            int add = textcol + (append ? textlen : 0);
            if (add > firstLine.length()) add = firstLine.length();
            int insLen = firstLine.length() - add - pre;
            if (pre < 0 || insLen <= 0) return;
            blockInsert(c, span, firstLine.substring(add, add + insLen), !append);
            c.editor.setCursor(new Pos(first, span.start().col));
        });
    }

    /** block_insert: the text goes before (I) or after (A) the block on every line after the first. */
    private static void blockInsert(Context c, Span span, String text, boolean before) {
        Buffer b = c.buffer;
        Span.Columns k = span.block();
        for (int line = span.start().line + 1; line <= span.end().line; line++) {
            String old = b.line(line);
            Block bd = prep(c, span, old, true, before ? Block.Op.INSERT : Block.Op.APPEND);
            if (bd.isShort && before) continue;
            int tsVal, spaces = 0, count = 0, offset;
            if (before) {
                tsVal = bd.startCharVcols;
                spaces = bd.startspaces;
                if (spaces != 0) count = tsVal - 1;
                offset = bd.textcol;
            } else {
                tsVal = bd.endCharVcols;
                if (!bd.isShort) {
                    spaces = bd.endspaces != 0 ? tsVal - bd.endspaces : 0;
                    if (spaces != 0) count = tsVal - 1;
                    offset = bd.textcol + bd.textlen - (spaces != 0 ? 1 : 0);
                } else {
                    if (!k.max()) spaces = k.endVcol() - bd.endVcol + 1;
                    count = spaces;
                    offset = bd.textcol + bd.textlen;
                }
            }
            if (spaces < 0) spaces = 0;
            offset = Math.min(offset, old.length());
            StringBuilder out = new StringBuilder(old.substring(0, offset));
            out.append(" ".repeat(spaces)).append(text);
            int rest = offset;
            if (spaces > 0 && !bd.isShort) {
                if (rest < old.length() && old.charAt(rest) == '\t') {
                    out.append(" ".repeat(tsVal - spaces));
                    rest++;
                }
            }
            out.append(old.substring(rest));
            b.setLine(line, out.toString());
        }
    }

    /** @return the span's text as a register holds it (op_yank) */
    static Content content(Context c, Span span) {
        Buffer b = c.buffer;
        if (span.blockwise()) {
            Span.Columns k = span.block();
            List<String> lines = new ArrayList<>();
            for (int line = span.start().line; line <= span.end().line; line++) {
                String text = b.line(line);
                lines.add(prep(c, span, text, false, Block.Op.OTHER).copy(text));
            }
            int width = k.endVcol() - k.startVcol();
            if (k.max() && width > 0) width--;
            return new Content(lines, Registers.Type.BLOCK, width);
        }
        if (span.linewise()) return new Content(b.lines(span.start().line, span.end().line), Registers.Type.LINE);
        // An exclusive span from column 0 to column 0 of a later line yanks as lines (not for Visual areas).
        if (!span.visual() && span.kind() == Kind.EXCLUSIVE && span.start().col == 0 && span.end().col == 0 && span.lineCount() > 1)
            return new Content(b.lines(span.start().line, span.end().line - 1), Registers.Type.LINE);
        return Content.of(b.text(b.offset(span.start()), end(b, span)), Registers.Type.CHAR);
    }

    /** @return the offset just after a charwise span; an inclusive end on the end of a line stops before its break */
    static int end(Buffer b, Span span) {
        String line = b.line(span.end().line);
        int col = Math.min(span.end().col, line.length());
        if (span.kind() == Kind.INCLUSIVE && col < line.length()) col = Text.next(line, col);
        return b.offset(span.end().line, col);
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Put.java` with:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Registers.Content;
import dev.jasper.editor.vim.Registers.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Putting a register's text: a port of Vim's do_put (register.c), for charwise, linewise and blockwise
 * registers, with the flags Visual-mode put uses. It sets the {@code '[} and {@code ']} marks and places the
 * cursor as Vim does.
 */
final class Put {
    /** gp and gP: the cursor goes after the new text. */
    static final int CURSEND = 1;
    /** ]p and friends: the lines take the current line's indent. */
    static final int FIXINDENT = 2;
    /** Put as lines whatever the register holds (p over a linewise Visual area). */
    static final int LINE = 4;
    /** Split the line at the cursor to put lines in between (p over a charwise Visual area). */
    static final int LINE_SPLIT = 8;
    /** Put lines below the Visual block (p over a block). */
    static final int LINE_FORWARD = 16;
    /** {@code :put}: the cursor goes to the first non-blank of the last new line. */
    static final int CURSLINE = 32;

    private Put() { }

    /**
     * @param c the command's context; its register, if any, is the one put
     * @param forward p rather than P
     * @param count how many copies
     * @param flags the flags above
     * @param visualEnd for {@code p} in Visual mode: the last line of the deleted Visual area, or -1
     */
    static void put(Context c, boolean forward, int count, int flags, int visualEnd) {
        int before = c.buffer.count();
        putText(c, forward, count, flags, visualEnd);
        // do_put's msgmore: more than 'report' new lines give a message.
        c.editor.msgmore(c.buffer.count() - before);
    }

    private static void putText(Context c, boolean forward, int count, int flags, int visualEnd) {
        Buffer b = c.buffer;
        Marks marks = c.editor.marks();
        char requested = Registers.effective(c.register, c.options.string("clipboard"));
        char name = requested == 0 ? '"' : requested;
        Pos cursor = c.cursor();
        marks.set('[', cursor);
        marks.set(']', cursor);
        Content content = c.registers.get(name).orElseThrow(() -> new VimFailure("E353: Nothing in register " + name));
        Type type = content.type();
        List<String> y = content.lines();
        int width = content.width();
        if (type == Type.LINE) {
            if ((flags & LINE_SPLIT) != 0) {
                String line = b.line(cursor.line);
                int split = forward && cursor.col < line.length() ? Text.next(line, cursor.col) : cursor.col;
                b.replace(b.offset(cursor.line, split), b.end(cursor.line), "\n" + line.substring(split));
                forward = true;
            }
            if ((flags & LINE_FORWARD) != 0) {
                cursor = new Pos(visualEnd, 0);
                forward = true;
            }
            marks.set('[', cursor);
            marks.set(']', cursor);
        }
        if ((flags & LINE) != 0) type = Type.LINE;
        int ts = c.tabstop();
        if (type == Type.BLOCK) {
            block(c, cursor, forward, count, flags, y, width, ts);
            return;
        }
        int lnum = cursor.line, col = cursor.col;
        String first = y.getFirst();
        if (type == Type.CHAR) {
            String line = b.line(lnum);
            if (forward && col < line.length()) {
                col = Text.next(line, col);
                if (!first.isEmpty()) cursor.col = col;
            }
            marks.set('[', cursor);
        } else if (!forward) lnum--;
        if (type == Type.CHAR && y.size() == 1) {
            simple(c, cursor, lnum, col, count, first, flags, visualEnd, ts);
            return;
        }
        Pos newCursor = cursor.copy();
        int newLine = newCursor.line;
        int origIndent = (flags & FIXINDENT) != 0 ? Text.indent(b.line(cursor.line), ts) : 0;
        boolean firstIndent = true;
        int indentDiff = 0, lenDiff = 0;
        for (int n = 1; n <= count; n++) {
            int i = 0;
            if (type == Type.CHAR) {
                // Split the line at the insert position, then join the register's first and last lines on.
                lnum = newCursor.line;
                String line = b.line(lnum);
                String tail = y.getLast() + line.substring(col);
                b.replace(b.offset(lnum, col), b.end(lnum), first + "\n" + tail);
                newLine++;
                cursor.line = lnum;
                i = 1;
            }
            List<String> block = new ArrayList<>();
            int insertAfter = lnum;
            for (; i < y.size(); i++) {
                if (type != Type.CHAR || i < y.size() - 1) {
                    block.add(y.get(i));
                    newLine++;
                }
                lnum++;
            }
            if (!block.isEmpty()) b.insertLines(insertAfter + 1, block);
            if ((flags & FIXINDENT) != 0) {
                for (int line = insertAfter + 1; line <= lnum; line++) {
                    String text = b.line(line);
                    boolean last = n == count && line == lnum;
                    int indent;
                    if (text.isEmpty()) indent = 0;
                    else if (firstIndent) {
                        indentDiff = origIndent - Text.indent(text, ts);
                        indent = origIndent;
                        firstIndent = false;
                    } else indent = Math.max(0, Text.indent(text, ts) + indentDiff);
                    String changed = text.isEmpty() ? text : Text.makeIndent(indent, ts, c.options.flag("expandtab")) + text.substring(Text.firstNonBlank(text));
                    if (last) lenDiff = text.length() - changed.length();
                    b.setLine(line, changed);
                }
            }
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
        if ((flags & CURSLINE) != 0) c.editor.setCursor(new Pos(lnum, Text.firstNonBlank(b.line(lnum))));
        else if ((flags & CURSEND) != 0) {
            if (type == Type.LINE) c.editor.setCursor(new Pos(Math.min(lnum + 1, b.count() - 1), 0), true);
            else c.editor.setCursor(new Pos(newLine, lastLen), true);
        } else if (type == Type.LINE) {
            int line = forward ? cursor.line + 1 : cursor.line;
            c.editor.setCursor(new Pos(line, Text.firstNonBlank(b.line(line))), true);
        } else c.editor.setCursor(newCursor, true);
        clampAfterPut(c);
    }

    /** The one-line charwise case, which in Visual block mode repeats on every line of the block. */
    private static void simple(Context c, Pos cursor, int lnum, int col, int count, String text, int flags, int visualEnd, int ts) {
        Buffer b = c.buffer;
        int start = lnum, end = visualEnd > start ? visualEnd : start;
        int vcol = Text.virtcol(b.line(lnum), col, ts);
        int total = count * text.length();
        int headOff = 0;
        if (count > 0 && !text.isEmpty()) {
            for (int line = start; line <= end; line++) {
                String old = b.line(line);
                int at = line == start ? col : Text.columnAt(old, vcol, ts);
                if (visualEnd >= 0 && at > old.length()) continue;
                at = Math.min(at, old.length());
                String inserted = text.repeat(count);
                b.insert(b.offset(line, at), inserted);
                headOff = inserted.length() - Text.last(inserted) - 1;
                if (line == cursor.line) cursor.col += total - 1;
            }
        }
        // '] is on the first unit of the last character put; the cursor there, or after it for gp.
        c.editor.marks().set(']', new Pos(cursor.line, Math.max(0, cursor.col - headOff)));
        if (total > 0 && (flags & CURSEND) != 0) cursor.col++;
        else cursor.col -= headOff;
        c.editor.setCursor(cursor, true);
        clampAfterPut(c);
    }

    /** The blockwise case: each register line goes into the next line at the cursor's display column. */
    private static void block(Context c, Pos cursor, boolean forward, int count, int flags, List<String> y, int width, int ts) {
        Buffer b = c.buffer;
        int lnum = cursor.line;
        String line0 = b.line(lnum);
        int col;
        if (forward && cursor.col < line0.length()) {
            int vcol = Text.virtcol(line0, cursor.col, ts);
            col = vcol + Text.width(line0, cursor.col, vcol, ts) - 1 + 1;
            cursor.col = Text.next(line0, cursor.col);
        } else col = Text.virtcol(line0, Math.min(cursor.col, line0.length()), ts);
        int totlen = 0, textcol = 0, firstStartspaces = 0;
        int row = lnum;
        for (int i = 0; i < y.size(); i++, row++) {
            if (row >= b.count()) b.insertLines(b.count(), List.of(""));
            String old = b.line(row);
            int vcol = 0, p = 0, incr = 0;
            while (vcol < col && p < old.length()) {
                incr = Text.width(old, p, vcol, ts);
                vcol += incr;
                p = Text.next(old, p);
            }
            textcol = p;
            boolean shortline = vcol < col || vcol == col && p >= old.length();
            int startspaces = 0, endspaces = 0, delcount = 0;
            if (vcol < col) startspaces = col - vcol;
            else if (vcol > col) {
                endspaces = vcol - col;
                startspaces = incr - endspaces;
                textcol = Text.previous(old, textcol);
                delcount = 1;
                if (old.charAt(textcol) != '\t') {
                    delcount = 0;
                    endspaces = 0;
                }
            }
            String text = y.get(i);
            int spaces = width + 1;
            int v = 0;
            for (int q = 0; q < text.length(); q = Text.next(text, q)) spaces -= Text.width(text, q, v, ts);
            if (spaces < 0) spaces = 0;
            StringBuilder out = new StringBuilder(old.substring(0, textcol));
            out.append(" ".repeat(startspaces));
            totlen = startspaces + endspaces;
            for (int j = 0; j < count; j++) {
                out.append(text);
                totlen += text.length();
                if ((j < count - 1 || !shortline) && spaces > 0) {
                    out.append(" ".repeat(spaces));
                    totlen += spaces;
                }
            }
            out.append(" ".repeat(endspaces));
            out.append(old.substring(Math.min(old.length(), textcol + delcount)));
            b.setLine(row, out.toString());
            if (i == 0) firstStartspaces = startspaces;
        }
        Marks marks = c.editor.marks();
        marks.set('[', new Pos(lnum, cursor.col + firstStartspaces));
        Pos opEnd = new Pos(row - 1, Math.max(0, textcol + totlen - 1));
        marks.set(']', opEnd);
        if ((flags & CURSEND) != 0) {
            Pos after = opEnd.copy();
            after.col = Math.min(after.col + 1, b.line(after.line).length());
            c.editor.setCursor(after, true);
        } else c.editor.setCursor(new Pos(lnum, cursor.col + firstStartspaces), true);
        clampAfterPut(c);
    }

    /** do_put's end: the cursor is never after the end of the line. */
    private static void clampAfterPut(Context c) {
        Pos p = c.cursor();
        int len = c.buffer.line(p.line).length();
        if (p.col > len) c.editor.setCursor(new Pos(p.line, len), true);
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/ui/VimBar.java` with:

```java
package dev.jasper.editor.vim.ui;

import dev.jasper.editor.vim.VimEditor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * The strip under a tab while Vim mode is on (spec 5.2): the mode or the last message on the left, the partly typed
 * command on the right. The bell flashes it briefly; Jasper never beeps. While {@code :}, {@code /} or {@code ?} reads
 * a line, the left side is the command line, with its cursor drawn as a thin bar. When a command line says more than
 * one line, the output panel above the strip shows it (errors in red) until a key is typed, and the strip says so;
 * a question waiting for its answer ({@code :s///c}) shows there too.
 */
public final class VimBar extends JPanel {
    private static final Color ERROR = new Color(0xD9, 0x4F, 0x4F);
    /** The output panel shows at most this many lines at once; more scroll. */
    private static final int OUTPUT_ROWS = 12;

    private final CommandLabel status = new CommandLabel();
    private final DefaultListModel<VimEditor.OutputLine> outputModel = new DefaultListModel<>();
    private final JList<VimEditor.OutputLine> outputList = new JList<>(outputModel);
    private final JScrollPane output = new JScrollPane(outputList);
    private final JLabel command = label();
    private final Runnable listener = this::refresh;
    private final Timer flash;
    private VimEditor editor;
    private int bells;
    private boolean flashing;

    /** An empty bar; {@link #attach} gives it an editor. */
    public VimBar() {
        super(new BorderLayout(12, 0));
        setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        add(status, BorderLayout.CENTER);
        add(command, BorderLayout.EAST);
        outputList.setFocusable(false);
        outputList.setCellRenderer((list, line, index, selected, focused) -> {
            JLabel label = label();
            label.setText(line.text().isEmpty() ? " " : visible(line.text()));
            label.setFont(list.getFont());
            label.setForeground(line.error() ? ERROR : UIManager.getColor("Label.foreground"));
            return label;
        });
        output.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        output.setVisible(false);
        add(output, BorderLayout.NORTH);
        flash = new Timer(150, event -> {
            flashing = false;
            colors();
        });
        flash.setRepeats(false);
        colors();
    }

    /** @param attached the tab's Vim editor, whose mode, messages and bell the bar shows */
    public void attach(VimEditor attached) {
        detach();
        editor = attached;
        bells = attached.bells();
        attached.addListener(listener);
        refresh();
    }

    /** Stops showing the editor. */
    public void detach() {
        if (editor != null) editor.removeListener(listener);
        editor = null;
        flash.stop();
        flashing = false;
        refresh();
    }

    /** @param font the Editor's font */
    public void apply(Font font) {
        status.setFont(font);
        command.setFont(font);
        outputList.setFont(font);
        colors();
    }

    String statusText() { return status.getText().strip(); }

    /** @return the command line's cursor index while one is shown, else -1 */
    int commandLineCursor() { return status.caret; }

    String commandText() { return command.getText().strip(); }

    boolean flashing() { return flashing; }

    /** @return the output panel's lines while it is shown, else empty */
    java.util.List<String> outputLines() {
        if (!output.isVisible()) return java.util.List.of();
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < outputModel.size(); i++) lines.add(outputModel.get(i).text());
        return lines;
    }

    private void refresh() {
        if (editor == null) {
            status.setText(" ");
            command.setText(" ");
            showOutput(java.util.List.of());
            return;
        }
        showOutput(editor.output());
        var line = editor.commandLineText();
        var prompt = editor.prompt();
        if (line.isPresent()) {
            status.setText(visible(line.get()));
            status.caret = editor.commandLineCursor();
            status.beforeCaret = visible(line.get().substring(0, Math.min(line.get().length(), status.caret)));
            status.repaint();
        } else if (prompt.isPresent()) {
            status.setText(visible(prompt.get()));
            status.caret = -1;
        } else {
            String message = editor.message();
            String text = message.isEmpty() ? editor.showmode() ? editor.mode().label() : "" : message;
            // A space keeps the bar one line high when there is nothing to show.
            status.setText(text.isEmpty() ? " " : text);
            status.caret = -1;
        }
        command.setText(editor.showcmd().isEmpty() ? " " : editor.showcmd());
        if (editor.bells() != bells) {
            bells = editor.bells();
            flashing = true;
            flash.restart();
        }
        colors();
    }

    /** The output panel: shown with the lines, up to {@link #OUTPUT_ROWS} of them at once, or hidden. */
    private void showOutput(java.util.List<VimEditor.OutputLine> lines) {
        boolean same = lines.size() == outputModel.size();
        for (int i = 0; same && i < lines.size(); i++) same = lines.get(i).equals(outputModel.get(i));
        if (!same) {
            outputModel.clear();
            outputModel.addAll(lines);
            outputList.setVisibleRowCount(Math.max(1, Math.min(OUTPUT_ROWS, lines.size())));
            if (!lines.isEmpty()) outputList.ensureIndexIsVisible(lines.size() - 1);
        }
        if (output.isVisible() != !lines.isEmpty()) {
            output.setVisible(!lines.isEmpty());
            revalidate();
        }
    }

    private void colors() {
        Color background = UIManager.getColor("Panel.background");
        Color highlight = UIManager.getColor("Component.focusColor");
        setBackground(flashing && highlight != null ? highlight : background);
        outputList.setBackground(background);
        output.getViewport().setBackground(background);
        Color foreground = UIManager.getColor("Label.foreground");
        status.setForeground(editor != null && editor.messageIsError() ? ERROR : foreground);
        command.setForeground(foreground);
    }

    /** Control characters typed with CTRL-V show as {@code ^X}, as on Vim's command line; the cursor index is kept. */
    private static String visible(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20) out.append('^').append((char) (c + '@'));
            else if (c == 0x7f) out.append("^?");
            else out.append(c);
        }
        return out.toString();
    }

    /** The left label, which draws the command line's cursor after {@link #beforeCaret}. */
    private static final class CommandLabel extends JLabel {
        int caret = -1;
        String beforeCaret = "";

        CommandLabel() {
            super(" ");
            putClientProperty("html.disable", true);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (caret < 0) return;
            FontMetrics metrics = g.getFontMetrics(getFont());
            int x = getInsets().left + metrics.stringWidth(beforeCaret);
            g.setColor(getForeground());
            g.fillRect(x, getInsets().top + 1, 1, Math.max(1, getHeight() - getInsets().top - getInsets().bottom - 2));
        }
    }

    private static JLabel label() {
        var label = new JLabel(" ");
        label.putClientProperty("html.disable", true);
        return label;
    }
}
```

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java` with:

```java
package dev.jasper.editor.vim;

import java.util.List;
import java.util.Map;

/**
 * One case of the differential corpus (spec 11.1): a starting text, cursor and options, the keys typed, and what
 * Vim did.
 *
 * @param file the file it came from, for messages
 * @param name its title
 * @param divergent why Jasper deliberately differs, or null; a divergent case's expectation is written by hand
 * @param set {@code :set} arguments applied first, or empty
 * @param given registers set first
 * @param text the starting lines
 * @param line the cursor's line, 1-based
 * @param col the cursor's column in characters, 1-based
 * @param keys the keys, in Vim notation
 * @param registers the registers whose contents are recorded and compared
 * @param window the window's size as {@code columnsxrows} ({@code 20x5}), or null; with it the view is compared
 * @param folds with {@code java}, the text's code folds as RSyntaxTextArea's Java fold parser finds them, followed
 *     by the first lines of the folds that start closed ({@code java 2 7}); null for none. The fold state is compared
 * @param output whether what the keys said (messages, listings, errors) is compared
 * @param expected what Vim did, or null when the case has not been recorded
 */
record CorpusCase(String file, String name, String divergent, String set, Map<Character, Register> given, List<String> text,
                  int line, int col, String keys, List<Character> registers, String window, String folds, boolean output, Expected expected) {
    /**
     * A register's content as the corpus writes it.
     *
     * @param type as getregtype() gives it, but in letters: {@code c} charwise, {@code l} linewise, or {@code b} and
     *     the width for a block ({@code b3})
     * @param text its text, as getreg() gives it
     */
    record Register(String type, String text) {
        /** @return the content a register holds for it */
        Registers.Content content() {
            if (type.startsWith("b")) {
                return new Registers.Content(java.util.List.of(text.split("\n", -1)), Registers.Type.BLOCK, Integer.parseInt(type.substring(1)) - 1);
            }
            return Registers.Content.of(text, type.equals("l") ? Registers.Type.LINE : Registers.Type.CHAR);
        }

        /** @return how the corpus writes a register's content */
        static Register of(Registers.Content content) {
            String type = switch (content.type()) {
                case CHAR -> "c";
                case LINE -> "l";
                case BLOCK -> "b" + (content.width() + 1);
            };
            return new Register(type, content.text());
        }
    }

    /**
     * The result.
     *
     * @param recordedBy the Vim that recorded it ({@code vim 9.1}), or {@code expected} for a divergent case
     * @param text the lines
     * @param line the cursor line, 1-based
     * @param col the cursor column in characters, 1-based
     * @param mode {@link Mode#id()}
     * @param visual in Visual mode, the other end of the area as {@code line,col} (1-based, in characters); else null
     * @param registers the compared registers; a missing value means empty
     * @param view for a case with a window, its first line and (without 'wrap') first column: {@code topline,leftcol}
     * @param folds for a case with folds, the closed folds as {@code first-last} line ranges, or {@code none}
     * @param output for a case that compares output, the lines said, empty lines left out; null otherwise
     */
    record Expected(String recordedBy, List<String> text, int line, int col, String mode, String visual, Map<Character, Register> registers,
                    String view, String folds, List<String> output) { }

    CorpusCase withExpected(Expected value) {
        return new CorpusCase(file, name, divergent, set, given, text, line, col, keys, registers, window, folds, output, value);
    }

    /** @return the window's columns and rows, from {@link #window} */
    int[] windowSize() {
        String[] parts = window.split("x");
        return new int[] { Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) };
    }

    /** @return the first lines of the folds that start closed, from {@link #folds} */
    List<Integer> closedAtStart() {
        List<Integer> out = new java.util.ArrayList<>();
        String[] parts = folds.trim().split("\\s+");
        for (int i = 1; i < parts.length; i++) out.add(Integer.parseInt(parts[i]));
        return out;
    }

    @Override public String toString() { return file + ": " + name; }
}
```

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java` with:

```java
package dev.jasper.editor.vim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes corpus files (spec 11.1). Lines starting with {@code #} outside a text block are comments. Text
 * lines are indented two spaces; one trailing {@code $} is dropped, so a line ending in white space or {@code $} is
 * written with a {@code $} after it. Everything from a case's {@code ---} line on is its expectation; writing
 * replaces only that part.
 */
final class CorpusFile {
    private CorpusFile() { }

    static List<CorpusCase> read(Path file) throws IOException {
        return parse(file.getFileName().toString(), Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    static List<CorpusCase> parse(String file, List<String> lines) {
        List<CorpusCase> cases = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) {
                i++;
                continue;
            }
            if (!line.startsWith("=== ")) throw error(file, i, "expected \"=== name\"");
            String name = line.substring(4).trim();
            String divergent = null, set = "", keys = null, window = null, folds = null;
            boolean output = false;
            Map<Character, CorpusCase.Register> given = new LinkedHashMap<>();
            List<String> text = null;
            int cursorLine = 1, cursorCol = 1;
            List<Character> registers = new ArrayList<>();
            CorpusCase.Expected expected = null;
            i++;
            while (i < lines.size() && !lines.get(i).startsWith("=== ")) {
                String field = lines.get(i);
                if (field.isBlank() || field.startsWith("#")) {
                    i++;
                    continue;
                }
                if (field.startsWith("---")) {
                    String by = field.substring(3).trim();
                    Parsed parsed = expectation(file, lines, i + 1, by);
                    expected = parsed.expected;
                    i = parsed.next;
                    break;
                }
                if (field.equals("text:")) {
                    List<String> block = new ArrayList<>();
                    i = block(lines, i + 1, block);
                    text = block;
                    continue;
                }
                int colon = field.indexOf(':');
                if (colon < 0) throw error(file, i, "expected \"field: value\"");
                String key = field.substring(0, colon), value = field.substring(colon + 1).trim();
                switch (key) {
                    case "divergent" -> divergent = value;
                    case "set" -> set = value;
                    case "window" -> window = value;
                    case "folds" -> folds = value;
                    case "output" -> output = value.equals("yes");
                    case "cursor" -> {
                        String[] parts = value.split(",");
                        cursorLine = Integer.parseInt(parts[0].trim());
                        cursorCol = Integer.parseInt(parts[1].trim());
                    }
                    case "keys" -> keys = field.substring(colon + 2);
                    case "registers" -> {
                        for (String name1 : value.split("\\s+")) if (!name1.isEmpty()) registers.add(name1.charAt(0));
                    }
                    default -> {
                        if (key.startsWith("register ")) given.put(key.charAt(9), register(value));
                        else throw error(file, i, "unknown field " + key);
                    }
                }
                i++;
            }
            if (text == null || keys == null) throw error(file, i, "case \"" + name + "\" needs text: and keys:");
            cases.add(new CorpusCase(file, name, divergent, set, given, text, cursorLine, cursorCol, keys, registers, window, folds, output, expected));
        }
        return cases;
    }

    private record Parsed(CorpusCase.Expected expected, int next) { }

    private static Parsed expectation(String file, List<String> lines, int i, String by) {
        List<String> text = List.of();
        int line = 1, col = 1;
        String mode = "normal", visual = null, view = null, closedFolds = null;
        List<String> output = null;
        Map<Character, CorpusCase.Register> registers = new LinkedHashMap<>();
        while (i < lines.size() && !lines.get(i).startsWith("=== ")) {
            String field = lines.get(i);
            if (field.isBlank() || field.startsWith("#")) {
                i++;
                continue;
            }
            if (field.equals("expect-text:")) {
                List<String> block = new ArrayList<>();
                i = block(lines, i + 1, block);
                text = block;
                continue;
            }
            if (field.equals("expect-output:")) {
                List<String> block = new ArrayList<>();
                i = block(lines, i + 1, block);
                output = block;
                continue;
            }
            int colon = field.indexOf(':', field.startsWith("expect-register ") ? 17 : 0);
            String key = field.substring(0, colon), value = field.substring(colon + 1).trim();
            switch (key) {
                case "expect-cursor" -> {
                    String[] parts = value.split(",");
                    line = Integer.parseInt(parts[0].trim());
                    col = Integer.parseInt(parts[1].trim());
                }
                case "expect-mode" -> mode = value;
                case "expect-visual" -> visual = value;
                case "expect-view" -> view = value;
                case "expect-folds" -> closedFolds = value;
                default -> {
                    if (!key.startsWith("expect-register ")) throw error(file, i, "unknown field " + key);
                    if (!value.equals("none")) registers.put(key.charAt(16), register(value));
                }
            }
            i++;
        }
        return new Parsed(new CorpusCase.Expected(by, text, line, col, mode, visual, registers, view, closedFolds, output), i);
    }

    private static CorpusCase.Register register(String value) {
        int space = value.indexOf(' ');
        return new CorpusCase.Register(value.substring(0, space), Json.unquote(value.substring(space + 1).trim()));
    }

    /** Reads indented text lines until a line that is not indented; returns the index after them. */
    private static int block(List<String> lines, int i, List<String> out) {
        while (i < lines.size() && lines.get(i).startsWith("  ")) {
            String content = lines.get(i).substring(2);
            if (content.endsWith("$")) content = content.substring(0, content.length() - 1);
            out.add(content);
            i++;
        }
        return i;
    }

    private static IllegalArgumentException error(String file, int line, String message) {
        return new IllegalArgumentException(file + ":" + (line + 1) + ": " + message);
    }

    /** Rewrites the file with each named case's expectation replaced by the lines given, keeping everything above it. */
    static void writeExpectations(Path file, Map<String, List<String>> byName) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();
        String current = null;
        boolean skipping = false;
        for (int i = 0; i <= lines.size(); i++) {
            String line = i < lines.size() ? lines.get(i) : null;
            boolean boundary = line == null || line.startsWith("=== ");
            if (boundary && current != null && byName.containsKey(current)) {
                while (!out.isEmpty() && out.getLast().isBlank()) out.removeLast();
                out.addAll(byName.get(current));
                out.add("");
            }
            if (line == null) break;
            if (boundary) {
                current = line.substring(4).trim();
                skipping = false;
                out.add(line);
                continue;
            }
            if (line.startsWith("---") && current != null && byName.containsKey(current)) skipping = true;
            if (!skipping) out.add(line);
        }
        while (!out.isEmpty() && out.getLast().isBlank()) out.removeLast();
        Files.write(file, out, StandardCharsets.UTF_8);
    }

    /** @return a case's expectation lines: its {@code ---} line, the text, cursor, mode and each compared register */
    static List<String> format(CorpusCase c, CorpusCase.Expected expected) {
        List<String> out = new ArrayList<>();
        out.add("--- " + expected.recordedBy());
        out.add("expect-text:");
        for (String line : expected.text()) out.add(textLine(line));
        out.add("expect-cursor: " + expected.line() + "," + expected.col());
        out.add("expect-mode: " + expected.mode());
        if (expected.visual() != null) out.add("expect-visual: " + expected.visual());
        if (expected.view() != null) out.add("expect-view: " + expected.view());
        if (expected.folds() != null) out.add("expect-folds: " + expected.folds());
        if (expected.output() != null) {
            out.add("expect-output:");
            for (String line : expected.output()) out.add(textLine(line));
        }
        for (char name : c.registers()) {
            CorpusCase.Register register = expected.registers().get(name);
            out.add("expect-register " + name + ": " + (register == null ? "none" : register.type() + " " + Json.quote(register.text())));
        }
        return out;
    }

    static String textLine(String content) {
        boolean mark = content.isEmpty() || content.endsWith(" ") || content.endsWith("\t") || content.endsWith("$");
        return "  " + content + (mark ? "$" : "");
    }
}
```

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusRunner.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Replays a corpus case on Jasper's engine (spec 11.1): a headless {@link VimTextArea} holding the text with a final
 * line break, Vim's own option defaults, and the keys fed as typed.
 */
final class CorpusRunner {
    private CorpusRunner() { }

    /** @return what the engine did, in the corpus's terms; runs on the event thread, as the Editor does */
    static CorpusCase.Expected run(CorpusCase c, Consumer<String> coverage) {
        var result = new java.util.concurrent.atomic.AtomicReference<CorpusCase.Expected>();
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> result.set(runHere(c, coverage)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (java.lang.reflect.InvocationTargetException failed) {
            if (failed.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failed.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(failed.getCause());
        }
        return result.get();
    }

    private static CorpusCase.Expected runHere(CorpusCase c, Consumer<String> coverage) {
        var vim = new Vim(VimHost.detached());
        vim.coverage(coverage);
        var area = new VimTextArea();
        area.setInsertPairedCharacters(false);
        area.setCloseCurlyBraces(false);
        area.setCloseMarkupTags(false);
        area.setText(String.join("\n", c.text()) + "\n");
        area.discardAllEdits();
        if (c.folds() != null) javaFolds(area, c.closedAtStart());
        var editor = new VimEditor(vim, area, EditorDefaults.VIM);
        // Vim's window when the recorder starts: 80 columns, 23 rows (24 lines, one for the command line).
        int[] size = c.window() != null ? c.windowSize() : new int[] { 80, 23 };
        editor.view().resize(size[1], size[0]);
        if (!c.set().isBlank()) editor.options().apply(c.set());
        for (var entry : c.given().entrySet()) {
            var register = entry.getValue();
            vim.registers().set(entry.getKey(), register.content());
        }
        String start = c.text().get(c.line() - 1);
        editor.setCursor(new Pos(c.line() - 1, Text.column(start, c.col() - 1)));
        // Vim scrolls to the cursor before it reads the first key.
        editor.view().updateTopline();
        editor.messageLog().clear();
        for (KeyInput key : KeyInput.parse(c.keys())) editor.feed(key);
        List<String> output = null;
        if (c.output()) {
            output = new ArrayList<>();
            for (String said : editor.messageLog()) for (String line : said.split("\n", -1)) if (!line.isEmpty()) output.add(line);
        }
        Buffer buffer = editor.buffer();
        List<String> text = new ArrayList<>();
        for (int line = 0; line < buffer.count(); line++) text.add(buffer.line(line));
        if (buffer.length() == 0) text = List.of("");
        Pos cursor = editor.cursor();
        int col = Text.characters(buffer.line(cursor.line), cursor.col) + 1;
        Map<Character, CorpusCase.Register> registers = new LinkedHashMap<>();
        for (char name : c.registers()) {
            vim.registers().get(name).ifPresent(content -> registers.put(name, CorpusCase.Register.of(content)));
        }
        String visual = null;
        Pos anchor = editor.visualAnchor();
        if (editor.mode().visual() && anchor != null)
            visual = (anchor.line + 1) + "," + (Text.characters(buffer.line(anchor.line), Math.min(anchor.col, buffer.line(anchor.line).length())) + 1);
        String view = c.window() == null ? null : editor.view().topline + "," + editor.view().leftcol;
        String folds = c.folds() == null ? null : closedFolds(editor);
        String mode = editor.mode().id();
        editor.close();
        return new CorpusCase.Expected("jasper", text, cursor.line + 1, col, mode, visual, registers, view, folds, output);
    }

    /** The text's code folds as RSyntaxTextArea's Java fold parser finds them, those starting on the given lines closed. */
    static void javaFolds(VimTextArea area, List<Integer> closed) {
        area.setSyntaxEditingStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA);
        area.setCodeFoldingEnabled(true);
        area.getFoldManager().reparse();
        for (int line : closed) collapse(area.getFoldManager(), line - 1);
    }

    private static void collapse(org.fife.ui.rsyntaxtextarea.folding.FoldManager manager, int startLine) {
        for (var fold : folds(manager)) if (fold.getStartLine() == startLine) fold.setCollapsed(true);
    }

    /** @return every fold, outer before inner */
    static List<org.fife.ui.rsyntaxtextarea.folding.Fold> folds(org.fife.ui.rsyntaxtextarea.folding.FoldManager manager) {
        List<org.fife.ui.rsyntaxtextarea.folding.Fold> out = new ArrayList<>();
        for (int i = 0; i < manager.getFoldCount(); i++) add(manager.getFold(i), out);
        return out;
    }

    private static void add(org.fife.ui.rsyntaxtextarea.folding.Fold fold, List<org.fife.ui.rsyntaxtextarea.folding.Fold> out) {
        out.add(fold);
        for (int i = 0; i < fold.getChildCount(); i++) add(fold.getChild(i), out);
    }

    /** @return the closed folds as Vim's foldclosed() reports them: {@code 2-5 8-9}, or {@code none} */
    static String closedFolds(VimEditor editor) {
        List<String> out = new ArrayList<>();
        int count = editor.buffer().count();
        for (int lnum = 1; lnum <= count; lnum++) {
            int[] fold = editor.folds().closed(lnum);
            if (fold != null && fold[0] == lnum) {
                out.add(fold[0] + "-" + fold[1]);
                lnum = fold[1];
            }
        }
        return out.isEmpty() ? "none" : String.join(" ", out);
    }

    /** @return the differences from the expectation, or empty */
    static String compare(CorpusCase.Expected expected, CorpusCase.Expected actual) {
        List<String> problems = new ArrayList<>();
        if (!expected.text().equals(actual.text())) problems.add("text " + actual.text() + " (Vim: " + expected.text() + ")");
        if (expected.line() != actual.line() || expected.col() != actual.col())
            problems.add("cursor " + actual.line() + "," + actual.col() + " (Vim: " + expected.line() + "," + expected.col() + ")");
        if (!expected.mode().equals(actual.mode())) problems.add("mode " + actual.mode() + " (Vim: " + expected.mode() + ")");
        if (!java.util.Objects.equals(expected.visual(), actual.visual())) problems.add("visual " + actual.visual() + " (Vim: " + expected.visual() + ")");
        if (!java.util.Objects.equals(expected.view(), actual.view())) problems.add("view " + actual.view() + " (Vim: " + expected.view() + ")");
        if (!java.util.Objects.equals(expected.folds(), actual.folds())) problems.add("folds " + actual.folds() + " (Vim: " + expected.folds() + ")");
        if (!expected.registers().equals(actual.registers())) problems.add("registers " + show(actual.registers()) + " (Vim: " + show(expected.registers()) + ")");
        if (expected.output() != null && !expected.output().equals(actual.output())) problems.add("output " + actual.output() + " (Vim: " + expected.output() + ")");
        return String.join("; ", problems);
    }

    private static String show(Map<Character, CorpusCase.Register> registers) {
        List<String> parts = new ArrayList<>();
        registers.forEach((name, register) -> parts.add(name + "=" + register.type() + Json.quote(register.text())));
        return "{" + String.join(", ", parts) + "}";
    }
}
```

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java` with:

```java
package dev.jasper.editor.vim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Records the corpus's expectations from real Vim (spec 11.1): {@code ./gradlew :jasper-plugin-editor:recordVimCorpus}.
 * Each case runs in its own {@code vim -u NONE -i NONE -N}, which types the keys with feedkeys() and writes the text,
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
        String version = version(vim);
        List<Path> files;
        try (Stream<Path> listing = Files.list(folder)) { files = listing.filter(p -> p.toString().endsWith(".case")).sorted().toList(); }
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
        int recorded = 0;
        try {
            for (Path file : files) {
                List<CorpusCase> cases = CorpusFile.read(file);
                Map<String, Future<CorpusCase.Expected>> pending = new LinkedHashMap<>();
                for (CorpusCase c : cases) {
                    if (c.divergent() != null || !c.name().contains(filter)) continue;
                    pending.put(c.name(), pool.submit(() -> record(vim, version, c)));
                }
                Map<String, List<String>> lines = new LinkedHashMap<>();
                for (CorpusCase c : cases) {
                    Future<CorpusCase.Expected> result = pending.get(c.name());
                    if (result == null) continue;
                    lines.put(c.name(), CorpusFile.format(c, result.get()));
                    recorded++;
                }
                if (!lines.isEmpty()) CorpusFile.writeExpectations(file, lines);
            }
        } finally { pool.shutdownNow(); }
        System.out.println("Recorded " + recorded + " cases with " + version + ".");
    }

    static String version(String vim) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(vim, "--version").redirectErrorStream(true).start();
        String first = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().findFirst().orElse("");
        process.waitFor();
        // "VIM - Vi IMproved 9.1 (2024 Jan 02, compiled ...)" -> "vim 9.1"
        String[] words = first.split("\\s+");
        return words.length > 4 ? "vim " + words[4] : "vim";
    }

    static CorpusCase.Expected record(String vim, String version, CorpusCase c) throws IOException, InterruptedException {
        Path folder = Files.createTempDirectory("jasper-vim-case");
        try {
            Path out = folder.resolve("out.json"), script = folder.resolve("case.vim");
            Files.writeString(script, script(c, out, folder.resolve("text.txt")), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(vim, "-u", "NONE", "-i", "NONE", "-N", "-n", "--not-a-term", "-S", script.toString())
                .redirectInput(ProcessBuilder.Redirect.from(Path.of(System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null").toFile()))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException(c + ": Vim did not finish within 20 s (keys left it waiting for input?)");
            }
            if (!Files.exists(out)) throw new IllegalStateException(c + ": Vim wrote no result; check the keys");
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) Json.parse(Files.readString(out, StandardCharsets.UTF_8).trim());
            if (!result.containsKey("text")) throw new IllegalStateException(c + ": the keys left Vim waiting for more (a prompt?)");
            @SuppressWarnings("unchecked")
            List<String> text = (List<String>) result.get("text");
            Map<Character, CorpusCase.Register> registers = new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, List<Object>> regs = (Map<String, List<Object>>) result.get("regs");
            for (var entry : regs.entrySet()) {
                String type = (String) entry.getValue().get(0);
                @SuppressWarnings("unchecked")
                List<String> lines = (List<String>) entry.getValue().get(1);
                if (type.isEmpty() || lines.isEmpty()) continue;
                String kind = type.equals("V") ? "l" : type.equals("v") ? "c" : "b" + type.substring(1);
                String joined = String.join("\n", lines) + (kind.equals("l") ? "\n" : "");
                registers.put(entry.getKey().charAt(0), new CorpusCase.Register(kind, joined));
            }
            @SuppressWarnings("unchecked")
            List<Long> visual = (List<Long>) result.get("visual");
            @SuppressWarnings("unchecked")
            List<Long> view = (List<Long>) result.get("view");
            @SuppressWarnings("unchecked")
            List<String> closed = (List<String>) result.get("folds");
            List<String> output = null;
            if (c.output()) {
                output = new ArrayList<>();
                @SuppressWarnings("unchecked")
                List<String> said = (List<String>) result.get("out");
                // The recorder's Vim runs the keys while sourcing its script, so each error message comes with a
                // header naming the script and line; a typed command line has none.
                for (String line : said) {
                    if (line.isEmpty() || line.startsWith("Error detected while processing ") || line.matches("line +\\d+:")) continue;
                    output.add(line);
                }
            }
            return new CorpusCase.Expected(version, text, ((Long) result.get("line")).intValue(), ((Long) result.get("col")).intValue(),
                mode((String) result.get("mode")), visual.isEmpty() ? null : visual.get(0) + "," + visual.get(1), registers,
                c.window() == null ? null : view.get(0) + "," + view.get(1),
                c.folds() == null ? null : closed.isEmpty() ? "none" : String.join(" ", closed), output);
        } finally {
            try (Stream<Path> files = Files.list(folder)) { for (Path p : files.toList()) Files.deleteIfExists(p); }
            Files.deleteIfExists(folder);
        }
    }

    static String mode(String vim) {
        if (vim.equals("n")) return "normal";
        if (vim.equals("i")) return "insert";
        if (vim.equals("R")) return "replace";
        if (vim.equals("niI") || vim.equals("niR")) return "insert-normal";
        if (vim.startsWith("no")) return "operator-pending";
        if (vim.equals("c")) return "command-line";
        if (vim.equals("v")) return "visual";
        if (vim.equals("V")) return "visual-line";
        if (vim.equals("\u0016")) return "visual-block";
        return vim;
    }

    /** @return a Vim script that sets the case up, types its keys and writes the result to {@code out} */
    static String script(CorpusCase c, Path out, Path file) {
        List<String> lines = new ArrayList<>();
        lines.add("set encoding=utf-8");
        lines.add("scriptencoding utf-8");
        lines.add("set nocompatible");
        lines.add("function! JasperRecord()");
        lines.add("  let g:jasper = {'text': getline(1, '$'), 'line': line('.'), 'col': charcol('.'), 'mode': mode(1), 'regs': {}, 'visual': []}");
        lines.add("  let g:jasper.view = [winsaveview().topline, winsaveview().leftcol]");
        lines.add("  let g:jasper.folds = []");
        lines.add("  let l = 1");
        lines.add("  while l <= line('$')");
        lines.add("    if foldclosed(l) == l");
        lines.add("      call add(g:jasper.folds, l . '-' . foldclosedend(l))");
        lines.add("      let l = foldclosedend(l)");
        lines.add("    endif");
        lines.add("    let l += 1");
        lines.add("  endwhile");
        lines.add("  if mode() =~# '^[vV\\x16]'");
        lines.add("    let g:jasper.visual = [line('v'), charcol('v')]");
        lines.add("  endif");
        lines.add("  for r in " + vimList(c.registers().stream().map(String::valueOf).toList()));
        lines.add("    let g:jasper.regs[r] = [getregtype(r), getreg(r, 1, 1)]");
        lines.add("  endfor");
        lines.add("endfunction");
        lines.add("for r in split('\"0123456789abcdefghijklmnopqrstuvwxyz-', '\\zs')");
        lines.add("  call setreg(r, [], 'c')");
        lines.add("endfor");
        if (c.window() != null) {
            int[] size = c.windowSize();
            lines.add("set columns=" + size[0] + " lines=" + (size[1] + 1));
        }
        if (!c.set().isBlank()) lines.add("set " + c.set());
        for (var entry : c.given().entrySet()) {
            CorpusCase.Register register = entry.getValue();
            String text = register.type().equals("l") && register.text().endsWith("\n") ? register.text().substring(0, register.text().length() - 1) : register.text();
            lines.add("call setreg(" + Json.quote(String.valueOf(entry.getKey())) + ", " + vimList(List.of(text.split("\n", -1))) + ", " + Json.quote(register.type()) + ")");
        }
        // Editing a file, rather than setline(), leaves no undo history for u to reach.
        lines.add("call writefile(" + vimList(c.text()) + ", " + Json.quote(file.toString()) + ")");
        lines.add("silent execute 'edit!' fnameescape(" + Json.quote(file.toString()) + ")");
        if (c.folds() != null) lines.addAll(foldCommands(c));
        lines.add("call setcursorcharpos(" + c.line() + ", " + c.col() + ")");
        lines.add("let g:jasper = {}");
        // What the keys say is captured; 'nomore' keeps a long listing from waiting for a key.
        lines.add("set nomore");
        lines.add("let g:jasper_out = ''");
        lines.add("redir => g:jasper_out");
        lines.add("call feedkeys(\"" + vimKeys(c.keys()) + "\\<Cmd>call JasperRecord()\\<CR>\", 'xt')");
        lines.add("redir END");
        lines.add("let g:jasper.out = split(g:jasper_out, \"\\n\", 1)");
        lines.add("call writefile([json_encode(g:jasper)], " + Json.quote(out.toString()) + ")");
        lines.add("qa!");
        return String.join("\n", lines) + "\n";
    }

    /**
     * The case's code folds as Vim manual folds: RSyntaxTextArea's Java fold parser finds them in the text, inner
     * folds are made first (a fold made over a closed one would take it whole), all are opened, and then the ones the
     * case lists are closed, innermost (last-starting) first.
     */
    static List<String> foldCommands(CorpusCase c) {
        List<String> out = new ArrayList<>();
        var area = new VimTextArea();
        area.setText(String.join("\n", c.text()) + "\n");
        CorpusRunner.javaFolds(area, List.of());
        List<org.fife.ui.rsyntaxtextarea.folding.Fold> folds = new ArrayList<>(CorpusRunner.folds(area.getFoldManager()));
        java.util.Collections.reverse(folds);
        for (var fold : folds) out.add((fold.getStartLine() + 1) + "," + (fold.getEndLine() + 1) + "fold");
        out.add("normal! zR");
        // Innermost first: closing a line's fold when an outer one is already closed would close nothing.
        List<Integer> closed = new ArrayList<>(c.closedAtStart());
        closed.sort(java.util.Comparator.reverseOrder());
        for (int line : closed) out.add(line + "foldclose");
        return out;
    }

    private static String vimList(List<String> items) {
        List<String> quoted = items.stream().map(Json::quote).toList();
        return "[" + String.join(", ", quoted) + "]";
    }

    /** @return the keys as the inside of a Vim double-quoted string: {@code <Esc>} becomes {@code \<Esc>} */
    static String vimKeys(String notation) {
        StringBuilder out = new StringBuilder();
        for (KeyInput key : KeyInput.parse(notation)) {
            if (key.isChar() && key.modifiers() == 0) {
                char c = key.key().charAt(0);
                if (c == '\\') out.append("\\\\");
                else if (c == '"') out.append("\\\"");
                else if (key.key().length() == 1 && c < 0x20) out.append(String.format("\\x%02x", (int) c));
                else out.append(key.key());
            } else out.append('\\').append(key.notation());
        }
        return out.toString();
    }
}
```

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/vim/VimDocsTest.java` with:

```java
package dev.jasper.editor.vim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the Vim documentation true (spec 11.1 and 12): every divergent corpus case is listed in
 * docs/vim-divergences.md, and docs/editor-vim-reference.md is generated from {@link VimCommands}. Regenerate the
 * reference with {@code ./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true}.
 */
class VimDocsTest {
    private static final Path DOCS = Path.of(System.getProperty("jasper.repoRoot", "../..")).toAbsolutePath().normalize().resolve("docs");

    @Test void everyDivergentCaseIsListedWithItsReason() throws IOException {
        String doc = Files.readString(DOCS.resolve("vim-divergences.md"), StandardCharsets.UTF_8);
        List<CorpusCase> divergent = VimCorpusTest.cases().stream().filter(c -> c.divergent() != null).toList();
        for (CorpusCase c : divergent)
            assertThat(doc).as("docs/vim-divergences.md lists %s", c).contains("| " + c.file() + " | " + c.name() + " | " + c.divergent() + " |");
        long rows = doc.lines().filter(line -> line.startsWith("| ") && line.contains(".case | ")).count();
        assertThat(rows).as("rows in docs/vim-divergences.md's corpus table").isEqualTo(divergent.size());
    }

    @Test void theReferenceIsGeneratedFromTheCommandTable() throws IOException {
        Path file = DOCS.resolve("editor-vim-reference.md");
        String expected = reference(VimCommands.standard());
        if (Boolean.parseBoolean(System.getProperty("jasper.updateDocs", "false"))) Files.writeString(file, expected, StandardCharsets.UTF_8);
        assertThat(Files.readString(file, StandardCharsets.UTF_8))
            .as("docs/editor-vim-reference.md is stale: run ./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true")
            .isEqualTo(expected);
    }

    static String reference(VimCommands commands) {
        StringBuilder out = new StringBuilder();
        out.append("# Editor Vim mode: supported keys\n\n");
        out.append("Generated from `VimCommands` by `VimDocsTest`; do not edit by hand. Regenerate with\n");
        out.append("`./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true`. How Vim mode works: [Editor](editor.md#vim-mode);\n");
        out.append("where it differs from Vim: [divergences](vim-divergences.md).\n");
        section(out, commands, VimCommands.Kind.MOTION, "Motions",
            "In Normal and Visual mode and after an operator. A count repeats or scales them. `/` and `?` read a pattern and an offset in the Vim bar, ended by `<CR>`.");
        section(out, commands, VimCommands.Kind.OPERATOR, "Operators",
            "Followed by a motion or text object, or on the Visual area; doubled (`dd`, `gUU`, `gUgU`) for whole lines.");
        section(out, commands, VimCommands.Kind.OBJECT, "Text objects", "After an operator, and in Visual mode, where they extend the area.");
        section(out, commands, VimCommands.Kind.ACTION, "Normal-mode commands", "");
        section(out, commands, VimCommands.Kind.VISUAL, "Visual-mode commands",
            "In Visual mode, besides the motions, operators and text objects above. A change repeats with `.` on an area of the same size.");
        section(out, commands, VimCommands.Kind.INSERT, "Insert-mode keys", "Printable keys type text; these keys do more.");
        exCommands(out);
        return out.toString();
    }

    /** The Ex commands, with Vim's shortest abbreviation outside the brackets ({@code :s[ubstitute]}). */
    private static void exCommands(StringBuilder out) {
        out.append("\n## Ex commands\n\n");
        out.append("Typed after `:` and ended by `<CR>`. A range goes before the name: `3`, `.`, `$`, `%`, `'x`, `/pattern/`, `?pattern?`,\n");
        out.append("`\\/`, `\\?` and `\\&`, each with `+N` or `-N` offsets, separated by `,` or `;`; `'<,'>` and `*` are the last Visual lines.\n");
        out.append("`|` separates commands. When a command line says more than one line, the output stays above the Vim bar until a\n");
        out.append("key is typed. A command Vim has and Jasper does not fails with E319.\n\n");
        out.append("| Command | What it does |\n|---|---|\n");
        for (Ex.Def def : Ex.implemented()) {
            String abbreviation = def.abbreviation();
            String name = abbreviation.equals(def.name()) ? ":" + def.name() : ":" + abbreviation + "[" + def.name().substring(abbreviation.length()) + "]";
            out.append("| `").append(name.replace("|", "\\|")).append("` | ").append(def.description().replace("|", "\\|")).append(" |\n");
        }
    }

    private static void section(StringBuilder out, VimCommands commands, VimCommands.Kind kind, String title, String note) {
        out.append("\n## ").append(title).append("\n\n");
        if (!note.isEmpty()) out.append(note).append("\n\n");
        out.append("| Keys | What it does |\n|---|---|\n");
        for (VimCommands.Entry entry : commands.entries()) {
            if (entry.kind() != kind) continue;
            String keys = entry.keys().equals(" ") ? "<Space>" : entry.keys();
            if (entry.needsChar()) keys += "{char}";
            String code = keys.contains("`") ? "`` " + keys + " ``" : "`" + keys + "`";
            out.append("| ").append(code.replace("|", "\\|")).append(" | ").append(entry.description().replace("|", "\\|")).append(" |\n");
        }
    }
}
```

- [ ] **Step 5: Add the cases**

The expectations were recorded from Vim 9.1; recording again must change nothing. One case is divergent (a command Vim has and Jasper lacks).

Create `plugins/editor/src/test/resources/vim/corpus/ex-range.case`:

```text
# Ex command line: addresses, ranges, counts and errors (V2a).

=== colon number goes to the line
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :5<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 5,1
expect-mode: normal

=== colon number keeps the first non-blank
text:
  alpha
    beta
      gamma
  delta
cursor: 1,1
keys: :3<CR>
--- vim 9.1
expect-text:
  alpha
    beta
      gamma
  delta
expect-cursor: 3,5
expect-mode: normal

=== colon number past the end goes to the last line
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :99<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 10,1
expect-mode: normal

=== colon zero goes to the first line
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 5,1
keys: :0<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== colon dollar
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :$<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 10,1
expect-mode: normal

=== colon dot plus
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :.+3<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 5,1
expect-mode: normal

=== colon plus alone
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :+<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal

=== colon plus plus
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :++<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 4,1
expect-mode: normal

=== colon minus
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 6,1
keys: :-2<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 4,1
expect-mode: normal

=== colon number is a plus offset
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :. 3<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 5,1
expect-mode: normal

=== colon dollar minus
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :$-2<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 8,1
expect-mode: normal

=== colon with spaces and colons
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: : : 4<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 4,1
expect-mode: normal

=== colon mark
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: 5Gmx1G:'x<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 5,1
expect-mode: normal

=== colon mark plus
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: 5Gmx1G:'x+1<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 6,1
expect-mode: normal

=== colon unset mark
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :'q<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E20: Mark not set

=== colon search forward
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :/six/<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 6,1
expect-mode: normal

=== colon search without the closing slash
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :/six<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 6,1
expect-mode: normal

=== colon search skips the cursor line
text:
  ab
  x
  ab
  ab
cursor: 1,1
keys: :/ab/<CR>
--- vim 9.1
expect-text:
  ab
  x
  ab
  ab
expect-cursor: 3,1
expect-mode: normal

=== colon search backward
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 8,1
keys: :?three?<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal

=== colon search then offset
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :/four/+2<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 6,1
expect-mode: normal

=== colon search sets the last pattern for n
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :/f/<CR>n
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 5,1
expect-mode: normal

=== colon search backward sets the direction for n
text:
  ab
  x
  ab
  x
  ab
cursor: 5,1
keys: :?ab?<CR>n
--- vim 9.1
expect-text:
  ab
  x
  ab
  x
  ab
expect-cursor: 1,1
expect-mode: normal

=== colon search then another
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :/t/;/t/<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal

=== colon two searches in a row
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :/two//e/<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal

=== colon search not found
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :/zzz/<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  search hit BOTTOM, continuing at TOP
  E486: Pattern not found: zzz

=== colon backslash slash repeats the search
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: /e<CR>:1<CR>:\/<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal

=== colon backslash question
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 9,1
keys: /e<CR>:9<CR>:\?<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 8,1
expect-mode: normal

=== colon bad backslash
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :\x<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E10: \ should be followed by /, ? or &

=== colon bar prints the line
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 3,1
keys: :|<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal
expect-output:
  three

=== colon number bar prints it
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :4|<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 4,1
expect-mode: normal
expect-output:
  four

=== colon comment does nothing
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 3,1
keys: :"hello<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal

=== colon range with a comment goes to it
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :6"x<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 6,1
expect-mode: normal

=== percent range prints all
text:
  a
  b
  c
cursor: 1,1
keys: :%p<CR>
output: yes
--- vim 9.1
expect-text:
  a
  b
  c
expect-cursor: 3,1
expect-mode: normal
expect-output:
  a
  b
  c

=== comma range
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :2,4d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "two\nthree\nfour\n"
expect-register 0: none
expect-register 1: l "two\nthree\nfour\n"
expect-register a: none
expect-register -: none

=== semicolon range counts from the first
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :3;+2d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  two
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal
expect-register ": l "three\nfour\nfive\n"
expect-register 0: none
expect-register 1: l "three\nfour\nfive\n"
expect-register a: none
expect-register -: none

=== comma range counts from the cursor
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :3,+2d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  two
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal
expect-register ": l "three\n"
expect-register 0: none
expect-register 1: l "three\n"
expect-register a: none
expect-register -: none

=== semicolon moves the cursor even if the command fails
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :4;foo<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 4,1
expect-mode: normal
expect-output:
  E492: Not an editor command: 4;foo

=== semicolon zero then search finds the first line
text:
  abc
  x
  abc
cursor: 2,1
keys: :0;/abc/d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  x
  abc
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "abc\n"
expect-register 0: none
expect-register 1: l "abc\n"
expect-register a: none
expect-register -: none

=== one address with comma
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 5,1
keys: :,d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  two
  three
  four
  six
  seven
  eight
  nine
  ten
expect-cursor: 5,1
expect-mode: normal
expect-register ": l "five\n"
expect-register 0: none
expect-register 1: l "five\n"
expect-register a: none
expect-register -: none

=== range dot comma dollar
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 8,1
keys: :.,$d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
expect-cursor: 7,1
expect-mode: normal
expect-register ": l "eight\nnine\nten\n"
expect-register 0: none
expect-register 1: l "eight\nnine\nten\n"
expect-register a: none
expect-register -: none

=== backwards range asks and y swaps
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :5,2d<CR>y
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "two\nthree\nfour\nfive\n"
expect-register 0: none
expect-register 1: l "two\nthree\nfour\nfive\n"
expect-register a: none
expect-register -: none

=== backwards range asks and n cancels
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :5,2d<CR>n
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== backwards range asks and Esc cancels
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :5,2d<CR><Esc>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== backwards range ignores other keys
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :5,2d<CR>xy
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "two\nthree\nfour\nfive\n"
expect-register 0: none
expect-register 1: l "two\nthree\nfour\nfive\n"
expect-register a: none
expect-register -: none

=== invalid range past the end
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :8,12d<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E16: Invalid range

=== negative address is invalid
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :-5d<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-output:
  E16: Invalid range

=== zero range for delete means line one
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 3,1
keys: :0d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "one\n"
expect-register 0: none
expect-register 1: l "one\n"
expect-register a: none
expect-register -: none

=== unknown command
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :foo<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E492: Not an editor command: foo

=== unknown command with a range
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :3foo bar<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E492: Not an editor command: 3foo bar

=== a Vim command Jasper lacks
divergent: Jasper has no abbreviations; a command it lacks fails with E319 (docs/vim-divergences.md)
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :ab x y<CR>
output: yes
--- expected
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E319: Sorry, the command is not available in this version

=== no range allowed
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :3marks<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E481: No range allowed

=== no range allowed with a dot
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :.marks<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E481: No range allowed

=== no bang allowed
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :delete!<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E477: No ! allowed

=== trailing characters
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :marks!<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E477: No ! allowed

=== trailing characters after a command
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :noh x<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E488: Trailing characters: x

=== count after the command
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :d 3<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "two\nthree\nfour\n"
expect-register 0: none
expect-register 1: l "two\nthree\nfour\n"
expect-register a: none
expect-register -: none

=== count after a range starts at its end
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :2,3d 2<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  two
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal
expect-register ": l "three\nfour\n"
expect-register 0: none
expect-register 1: l "three\nfour\n"
expect-register a: none
expect-register -: none

=== count past the end is cut
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 8,1
keys: :d 9<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
expect-cursor: 7,1
expect-mode: normal
expect-register ": l "eight\nnine\nten\n"
expect-register 0: none
expect-register 1: l "eight\nnine\nten\n"
expect-register a: none
expect-register -: none

=== zero count is an error
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :d 0<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-output:
  E939: Positive count required

=== register and count
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :d a 2<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "two\nthree\n"
expect-register 0: none
expect-register 1: l "two\nthree\n"
expect-register a: l "two\nthree\n"
expect-register -: none

=== star register takes the clipboard
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :d * <CR>
--- vim 9.1
expect-text:
  one
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal

=== bar separates commands
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :2d|3d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  three
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal
expect-register ": l "four\n"
expect-register 0: none
expect-register 1: l "four\n"
expect-register a: none
expect-register -: none

=== error stops the rest of the line
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :foo|2d<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E492: Not an editor command: foo|2d

=== count before colon
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: 3:d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "two\nthree\nfour\n"
expect-register 0: none
expect-register 1: l "two\nthree\nfour\n"
expect-register a: none
expect-register -: none

=== count one before colon
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: 1:d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "two\n"
expect-register 0: none
expect-register 1: l "two\n"
expect-register a: none
expect-register -: none

=== count before colon then backspace edits the range
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: 3:<BS><BS>5d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "two\nthree\nfour\nfive\nsix\nseven\n"
expect-register 0: none
expect-register 1: l "two\nthree\nfour\nfive\nsix\nseven\n"
expect-register a: none
expect-register -: none

=== visual colon uses the visual lines
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,2
keys: Vjj:d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "two\nthree\nfour\n"
expect-register 0: none
expect-register 1: l "two\nthree\nfour\n"
expect-register a: none
expect-register -: none

=== visual colon then escape ends visual mode
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,2
keys: vjj:<Esc>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,2
expect-mode: normal

=== visual colon backwards then escape
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 4,3
keys: vkk:<Esc>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,3
expect-mode: normal

=== linewise visual colon then escape
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,3
keys: Vjj:<Esc>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal

=== linewise visual colon backwards then escape
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 4,3
keys: Vkk:<Esc>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,3
expect-mode: normal

=== block visual colon then escape
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,3
keys: <C-v>jjh:<Esc>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,2
expect-mode: normal

=== block visual colon from the top right
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 4,2
keys: <C-v>kkl:<Esc>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,2
expect-mode: normal

=== block visual colon from the bottom right
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,2
keys: <C-v>jjl:<Esc>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,2
expect-mode: normal

=== visual colon then a command keeps the cursor from the start
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,3
keys: vj:p<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal

=== star range is the visual area
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: 2GVj<Esc>G:*d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "two\nthree\n"
expect-register 0: none
expect-register 1: l "two\nthree\n"
expect-register a: none
expect-register -: none

=== apostrophe angle range
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: 3GVj<Esc>1G:'<lt>,'>d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  two
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal
expect-register ": l "three\nfour\n"
expect-register 0: none
expect-register 1: l "three\nfour\n"
expect-register a: none
expect-register -: none

=== colon escape cancels
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :3d<Esc>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal

=== colon backspace on empty cancels
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :<BS>x
--- vim 9.1
expect-text:
  one
  wo
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal

=== colon history recalls
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :2d<CR>:<Up><CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "three\n"
expect-register 0: none
expect-register 1: l "three\n"
expect-register a: none
expect-register -: none

=== colon register holds the last line
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :3<CR>":p
registers: : "
--- vim 9.1
expect-text:
  one
  two
  t3hree
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,2
expect-mode: normal
expect-register :: c "3"
expect-register ": none

=== closed fold counts as one line in a range
text:
  class A {
    void f() {
      x();
    }
    int y;
  }
cursor: 1,1
folds: java 2
keys: :2d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  class A {
    int y;
  }
expect-cursor: 2,3
expect-mode: normal
expect-folds: none
expect-register ": l "  void f() {\n    x();\n  }\n"
expect-register 0: none
expect-register 1: l "  void f() {\n    x();\n  }\n"
expect-register a: none
expect-register -: none

=== a range ending in a closed fold takes all of it
text:
  class A {
    void f() {
      x();
    }
    int y;
  }
cursor: 1,1
folds: java 2
keys: :1,2d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
    int y;
  }
expect-cursor: 1,3
expect-mode: normal
expect-folds: none
expect-register ": l "class A {\n  void f() {\n    x();\n  }\n"
expect-register 0: none
expect-register 1: l "class A {\n  void f() {\n    x();\n  }\n"
expect-register a: none
expect-register -: none

=== relative address skips a closed fold
text:
  class A {
    void f() {
      x();
    }
    int y;
  }
cursor: 2,1
folds: java 2
keys: :.,+1d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  class A {
  }
expect-cursor: 2,1
expect-mode: normal
expect-folds: none
expect-register ": l "  void f() {\n    x();\n  }\n  int y;\n"
expect-register 0: none
expect-register 1: l "  void f() {\n    x();\n  }\n  int y;\n"
expect-register a: none
expect-register -: none

=== multiple colons and spaces before the command
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :: 3 : d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  two
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal
expect-register ": l "three\n"
expect-register 0: none
expect-register 1: l "three\n"
expect-register a: none
expect-register -: none

=== apostrophe after a number is not a mark
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: 5Gma1G:3'a<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E492: Not an editor command: 3'a
```

Create `plugins/editor/src/test/resources/vim/corpus/ex-lines.case`:

```text
# Ex line commands: :d :y :> :< :put :join :move :copy :normal :sort :left :right :center :undo (V2a).

=== d deletes the line
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 3,1
keys: :d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  two
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal
expect-register ": l "three\n"
expect-register 0: none
expect-register 1: l "three\n"
expect-register a: none
expect-register -: none

=== d range into a register
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :2,3d a<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "two\nthree\n"
expect-register 0: none
expect-register 1: l "two\nthree\n"
expect-register a: l "two\nthree\n"
expect-register -: none

=== d uppercase register appends
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :1d a<CR>:1d A<CR>
registers: a
--- vim 9.1
expect-text:
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-register a: l "one\ntwo\n"

=== d black hole
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :1d _<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-register ": none
expect-register 0: none
expect-register 1: none
expect-register a: none
expect-register -: none

=== d reports fewer lines
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :1,4d<CR>
output: yes
--- vim 9.1
expect-text:
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  4 fewer lines

=== d of three lines says nothing
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :1,3d<CR>
output: yes
--- vim 9.1
expect-text:
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  3 fewer lines

=== dl prints the line after
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :dl<CR>
output: yes
--- vim 9.1
expect-text:
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  two$$

=== dp prints the line after
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :dp<CR>
output: yes
--- vim 9.1
expect-text:
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  two

=== delete with the p flag spelled out
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :delp<CR>
output: yes
--- vim 9.1
expect-text:
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  two

=== d sets the jumplist
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: G:3d<CR><C-o>
--- vim 9.1
expect-text:
  one
  two
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 9,1
expect-mode: normal

=== d keeps the first non-blank
text:
  alpha
    beta
      gamma
  delta
cursor: 1,1
keys: :1d<CR>
--- vim 9.1
expect-text:
    beta
      gamma
  delta
expect-cursor: 1,3
expect-mode: normal

=== d with nostartofline keeps the column
text:
  abcdef
  abcdef
  abcdef
cursor: 1,5
set: nosol
keys: :1d<CR>
--- vim 9.1
expect-text:
  abcdef
  abcdef
expect-cursor: 1,1
expect-mode: normal

=== d of every line
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 3,1
keys: :%d<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten\n"
expect-register 0: none
expect-register 1: l "one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten\n"
expect-register a: none
expect-register -: none

=== y yanks lines
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 3,2
keys: :y<CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,2
expect-mode: normal
expect-register ": l "three\n"
expect-register 0: l "three\n"
expect-register 1: none
expect-register a: none
expect-register -: none

=== y range into a register keeps the cursor
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 3,2
keys: :1,2y b<CR>
registers: " 0 b
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,2
expect-mode: normal
expect-register ": l "one\ntwo\n"
expect-register 0: none
expect-register b: l "one\ntwo\n"

=== y reports the lines yanked
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :1,3y<CR>:1,3y x<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  3 lines yanked
  3 lines yanked into "x

=== yy reports lines yanked
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: 4yy
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  4 lines yanked

=== dd reports fewer lines
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: 4dd
output: yes
--- vim 9.1
expect-text:
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  4 fewer lines

=== p reports more lines
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: 4yyp
output: yes
--- vim 9.1
expect-text:
  one
  one
  two
  three
  four
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-output:
  4 lines yanked
  4 more lines

=== greater shifts right
text:
    one
  two
      three
  four
cursor: 1,1
keys: :><CR>
--- vim 9.1
expect-text:
  	  one
  two
      three
  four
expect-cursor: 1,4
expect-mode: normal

=== greater greater shifts twice
text:
    one
  two
      three
  four
cursor: 1,1
keys: :2,3>><CR>
--- vim 9.1
expect-text:
    one
  		two
  		    three
  four
expect-cursor: 3,7
expect-mode: normal

=== greater with a count
text:
    one
  two
      three
  four
cursor: 1,1
keys: :> 3<CR>
--- vim 9.1
expect-text:
  	  one
  	two
  	    three
  four
expect-cursor: 3,6
expect-mode: normal

=== greater reports
text:
    one
  two
      three
  four
cursor: 1,1
keys: :1,3><CR>
output: yes
--- vim 9.1
expect-text:
  	  one
  	two
  	    three
  four
expect-cursor: 3,6
expect-mode: normal
expect-output:
  3 lines >ed 1 time

=== less shifts left
text:
    one
  two
      three
  four
cursor: 3,1
keys: :<lt><CR>
--- vim 9.1
expect-text:
    one
  two
  three
  four
expect-cursor: 3,1
expect-mode: normal

=== less less on a range
text:
    one
  two
      three
  four
cursor: 1,1
keys: :%<lt><lt><CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
expect-cursor: 4,1
expect-mode: normal

=== greater leaves the cursor on the last line
text:
    one
  two
      three
  four
cursor: 1,1
keys: :1,3><CR>
--- vim 9.1
expect-text:
  	  one
  	two
  	    three
  four
expect-cursor: 3,6
expect-mode: normal

=== greater with p prints
text:
    one
  two
      three
  four
cursor: 1,1
keys: :>p<CR>
output: yes
--- vim 9.1
expect-text:
  	  one
  two
      three
  four
expect-cursor: 1,4
expect-mode: normal
expect-output:
            one

=== shift reports in normal mode
text:
    one
  two
      three
  four
cursor: 1,1
keys: 3>>
output: yes
--- vim 9.1
expect-text:
  	  one
  	two
  	    three
  four
expect-cursor: 1,4
expect-mode: normal
expect-output:
  3 lines >ed 1 time

=== put below
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: yy:3pu<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  one
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 4,1
expect-mode: normal

=== put above with bang
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: yy:3pu!<CR>
--- vim 9.1
expect-text:
  one
  two
  one
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal

=== put at zero goes above the first line
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 5,1
keys: yy:0pu<CR>
--- vim 9.1
expect-text:
  five
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== put a charwise register as a line
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: yiw:$pu<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
  one
expect-cursor: 11,1
expect-mode: normal

=== put a named register
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: "ayy:4pu a<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  one
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 5,1
expect-mode: normal

=== put a multiline charwise register
text:
  ab
  cd
cursor: 1,2
keys: vjy:2put<CR>
--- vim 9.1
expect-text:
  ab
  cd
  b
  cd
expect-cursor: 4,1
expect-mode: normal

=== put an empty register
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :pu x<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E353: Nothing in register x

=== put reports more lines
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: 3yy:pu<CR>
output: yes
--- vim 9.1
expect-text:
  one
  one
  two
  three
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 4,1
expect-mode: normal
expect-output:
  3 lines yanked
  3 more lines

=== put the colon register
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :3<CR>:pu :<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  3
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 4,1
expect-mode: normal

=== join joins two lines
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :j<CR>
--- vim 9.1
expect-text:
  one two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== join a range
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :2,4j<CR>
--- vim 9.1
expect-text:
  one
  two three four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal

=== join with a count
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :j 3<CR>
--- vim 9.1
expect-text:
  one two three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== join bang keeps the spaces out
text:
    one
  two
      three
  four
cursor: 1,1
keys: :j!<CR>
--- vim 9.1
expect-text:
    onetwo
      three
  four
expect-cursor: 1,3
expect-mode: normal

=== join of one address pair does nothing
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :2,2j<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal

=== join on the last line fails
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 10,1
keys: :j<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 10,1
expect-mode: normal

=== join with p prints
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :jp<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E492: Not an editor command: jp

=== join cursor goes to the first non-blank
text:
    one
  two
      three
  four
cursor: 1,1
keys: :1,3j<CR>
--- vim 9.1
expect-text:
    one two three
  four
expect-cursor: 1,3
expect-mode: normal

=== move down
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :m 3<CR>
--- vim 9.1
expect-text:
  two
  three
  one
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal

=== move up
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 5,1
keys: :m 1<CR>
--- vim 9.1
expect-text:
  one
  five
  two
  three
  four
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal

=== move to the top
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 5,1
keys: :m0<CR>
--- vim 9.1
expect-text:
  five
  one
  two
  three
  four
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== move to the end
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :m$<CR>
--- vim 9.1
expect-text:
  one
  three
  four
  five
  six
  seven
  eight
  nine
  ten
  two
expect-cursor: 10,1
expect-mode: normal

=== move a range
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :1,3m 6<CR>
--- vim 9.1
expect-text:
  four
  five
  six
  one
  two
  three
  seven
  eight
  nine
  ten
expect-cursor: 6,1
expect-mode: normal

=== move reports
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :1,3m$<CR>
output: yes
--- vim 9.1
expect-text:
  four
  five
  six
  seven
  eight
  nine
  ten
  one
  two
  three
expect-cursor: 10,1
expect-mode: normal
expect-output:
  3 lines moved

=== move into itself fails
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :1,4m 2<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E134: Cannot move a range of lines into itself

=== move to where it is
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 3,1
keys: :m 2<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal

=== move to below itself
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 3,1
keys: :m 3<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal

=== move with relative address
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 3,1
keys: :m+2<CR>
--- vim 9.1
expect-text:
  one
  two
  four
  five
  three
  six
  seven
  eight
  nine
  ten
expect-cursor: 5,1
expect-mode: normal

=== move keeps a mark on the moved line
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,3
keys: ma:m 5<CR>`a
--- vim 9.1
expect-text:
  one
  three
  four
  five
  two
  six
  seven
  eight
  nine
  ten
expect-cursor: 5,3
expect-mode: normal

=== move needs an address
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :m<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-output:
  E16: Invalid range

=== move past the end fails
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :m 20<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-output:
  E16: Invalid range

=== move then undo
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :1,2m$<CR>u
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== t copies below
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :t 3<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  one
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 4,1
expect-mode: normal

=== t dot copies the line
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :t.<CR>
--- vim 9.1
expect-text:
  one
  two
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal

=== copy a range
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :1,3co 5<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  one
  two
  three
  six
  seven
  eight
  nine
  ten
expect-cursor: 8,1
expect-mode: normal

=== copy to zero
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 4,1
keys: :co 0<CR>
--- vim 9.1
expect-text:
  four
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== copy into itself
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :1,3co 2<CR>
--- vim 9.1
expect-text:
  one
  two
  one
  two
  three
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 5,1
expect-mode: normal

=== copy reports more lines
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :1,3co$<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
  one
  two
  three
expect-cursor: 13,1
expect-mode: normal
expect-output:
  3 more lines

=== copy then p flag
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :t$p<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
  one
expect-cursor: 11,1
expect-mode: normal
expect-output:
  one

=== normal runs keys
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :norm x<CR>
--- vim 9.1
expect-text:
  ne
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== normal on a range
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :1,3norm Ax<CR>
--- vim 9.1
expect-text:
  onex
  twox
  threex
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,6
expect-mode: normal

=== normal bang
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :norm! dd<CR>
--- vim 9.1
expect-text:
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== normal on a range from the line start
text:
    one
  two
      three
  four
cursor: 1,3
keys: :%norm x<CR>
--- vim 9.1
expect-text:
   one
  wo
     three
  our
expect-cursor: 4,1
expect-mode: normal

=== normal ends insert mode
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :norm ihello<CR>
--- vim 9.1
expect-text:
  helloone
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,5
expect-mode: normal

=== normal ends a pending operator
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :norm d<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== normal ends a count
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :norm 3<CR>x
--- vim 9.1
expect-text:
  ne
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== normal leaves visual mode
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :norm vl<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,2
expect-mode: visual
expect-visual: 1,1

=== normal then undo is one step
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :%norm Ax<CR>u
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,3
expect-mode: normal

=== normal then dot repeats its change
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :norm Ax<CR>j.
--- vim 9.1
expect-text:
  onex
  twox
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,4
expect-mode: normal

=== normal failure drops the rest
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 10,1
keys: :norm jx<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 10,1
expect-mode: normal

=== normal needs an argument
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :norm<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E471: Argument required

=== normal with a colon command
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :norm :3d<C-v><CR><CR>
registers: " 0 1 a -
--- vim 9.1
expect-text:
  one
  two
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal
expect-register ": l "three\n"
expect-register 0: none
expect-register 1: l "three\n"
expect-register a: none
expect-register -: none

=== normal escape key by ctrl v
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :norm ix<C-v><Esc>dd<CR>
--- vim 9.1
expect-text:
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== sort sorts
text:
  pear 10
  apple 2
  Banana -3
  cherry x7
  apple 2
  date 0x1F
cursor: 3,1
keys: :sort<CR>
--- vim 9.1
expect-text:
  Banana -3
  apple 2
  apple 2
  cherry x7
  date 0x1F
  pear 10
expect-cursor: 1,1
expect-mode: normal

=== sort reverse
text:
  pear 10
  apple 2
  Banana -3
  cherry x7
  apple 2
  date 0x1F
cursor: 1,1
keys: :sort!<CR>
--- vim 9.1
expect-text:
  pear 10
  date 0x1F
  cherry x7
  apple 2
  apple 2
  Banana -3
expect-cursor: 1,1
expect-mode: normal

=== sort ignoring case
text:
  pear 10
  apple 2
  Banana -3
  cherry x7
  apple 2
  date 0x1F
cursor: 1,1
keys: :sort i<CR>
--- vim 9.1
expect-text:
  apple 2
  apple 2
  Banana -3
  cherry x7
  date 0x1F
  pear 10
expect-cursor: 1,1
expect-mode: normal

=== sort numbers
text:
  pear 10
  apple 2
  Banana -3
  cherry x7
  apple 2
  date 0x1F
cursor: 1,1
keys: :sort n<CR>
--- vim 9.1
expect-text:
  Banana -3
  date 0x1F
  apple 2
  apple 2
  cherry x7
  pear 10
expect-cursor: 1,1
expect-mode: normal

=== sort hex
text:
  pear 10
  apple 2
  Banana -3
  cherry x7
  apple 2
  date 0x1F
cursor: 1,1
keys: :sort x<CR>
--- vim 9.1
expect-text:
  apple 2
  apple 2
  cherry x7
  Banana -3
  date 0x1F
  pear 10
expect-cursor: 1,1
expect-mode: normal

=== sort unique
text:
  pear 10
  apple 2
  Banana -3
  cherry x7
  apple 2
  date 0x1F
cursor: 1,1
keys: :sort u<CR>
output: yes
--- vim 9.1
expect-text:
  Banana -3
  apple 2
  cherry x7
  date 0x1F
  pear 10
expect-cursor: 1,1
expect-mode: normal
expect-output:

=== sort a range
text:
  pear 10
  apple 2
  Banana -3
  cherry x7
  apple 2
  date 0x1F
cursor: 1,1
keys: :2,4sort<CR>
--- vim 9.1
expect-text:
  pear 10
  Banana -3
  apple 2
  cherry x7
  apple 2
  date 0x1F
expect-cursor: 2,1
expect-mode: normal

=== sort by pattern skips it
text:
  a3 z
  b1 y
  c2 x
cursor: 1,1
keys: :sort /./<CR>
--- vim 9.1
expect-text:
  b1 y
  c2 x
  a3 z
expect-cursor: 1,1
expect-mode: normal

=== sort on the pattern with r
text:
  a3 z
  b1 y
  c2 x
cursor: 1,1
keys: :sort /\d/ r<CR>
--- vim 9.1
expect-text:
  b1 y
  c2 x
  a3 z
expect-cursor: 1,1
expect-mode: normal

=== sort n with r
text:
  x10y2
  x9y30
  x1y4
cursor: 1,1
keys: :sort /y\d\+/ r n<CR>
--- vim 9.1
expect-text:
  x10y2
  x1y4
  x9y30
expect-cursor: 1,1
expect-mode: normal

=== sort floats
text:
  1.5
  -2
  1e2
  x
  0.25
cursor: 1,1
keys: :sort f<CR>
--- vim 9.1
expect-text:
  -2
  x
  0.25
  1.5
  1e2
expect-cursor: 1,1
expect-mode: normal

=== sort two formats fails
text:
  pear 10
  apple 2
  Banana -3
  cherry x7
  apple 2
  date 0x1F
cursor: 1,1
keys: :sort nx<CR>
output: yes
--- vim 9.1
expect-text:
  pear 10
  apple 2
  Banana -3
  cherry x7
  apple 2
  date 0x1F
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E474: Invalid argument

=== sort bad argument
text:
  pear 10
  apple 2
  Banana -3
  cherry x7
  apple 2
  date 0x1F
cursor: 1,1
keys: :sort q<CR>
output: yes
--- vim 9.1
expect-text:
  pear 10
  apple 2
  Banana -3
  cherry x7
  apple 2
  date 0x1F
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E475: Invalid argument: q

=== sort then bar
text:
  pear 10
  apple 2
  Banana -3
  cherry x7
  apple 2
  date 0x1F
cursor: 1,1
keys: :sort | 1d<CR>
--- vim 9.1
expect-text:
  apple 2
  apple 2
  cherry x7
  date 0x1F
  pear 10
expect-cursor: 1,1
expect-mode: normal

=== sort unique ignoring case
text:
  a
  A
  b
  B
  a
cursor: 1,1
keys: :sort ui<CR>
--- vim 9.1
expect-text:
  a
  b
expect-cursor: 1,1
expect-mode: normal

=== sort b
text:
  0b101 x
  11
  0b1
  z
cursor: 1,1
keys: :sort b<CR>
--- vim 9.1
expect-text:
  z
  0b1
  11
  0b101 x
expect-cursor: 1,1
expect-mode: normal

=== sort keeps equal lines in order
text:
  b 1
  a 1
  c 0
cursor: 1,1
keys: :sort /\a / n<CR>
--- vim 9.1
expect-text:
  c 0
  b 1
  a 1
expect-cursor: 1,1
expect-mode: normal

=== sort empty pattern uses the last search
text:
  a3
  b1
  c2
cursor: 1,1
keys: /\d<CR>:sort // r<CR>
--- vim 9.1
expect-text:
  b1
  c2
  a3
expect-cursor: 1,1
expect-mode: normal

=== sort cursor goes to the first line
text:
    one
  two
      three
  four
cursor: 4,1
keys: :sort<CR>
--- vim 9.1
expect-text:
      three
    one
  four
  two
expect-cursor: 1,5
expect-mode: normal

=== left removes indent
text:
    one
  two
      three
  four
cursor: 1,1
keys: :%left<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
expect-cursor: 1,1
expect-mode: normal

=== left with an indent
text:
    one
  two
      three
  four
cursor: 1,1
keys: :%left 3<CR>
--- vim 9.1
expect-text:
     one
     two
     three
     four
expect-cursor: 1,4
expect-mode: normal

=== right aligns to 80
text:
    one
  two
      three
  four
cursor: 1,1
keys: :%right<CR>
--- vim 9.1
expect-text:
  									     one
  									     two
  									   three
  									    four
expect-cursor: 1,15
expect-mode: normal

=== right aligns to a width
text:
    one
  two
      three
  four
cursor: 1,1
keys: :%right 20<CR>
--- vim 9.1
expect-text:
  		 one
  		 two
  	       three
  		four
expect-cursor: 1,4
expect-mode: normal

=== right uses textwidth
text:
    one
  two
      three
  four
cursor: 1,1
set: tw=30
keys: :%right<CR>
--- vim 9.1
expect-text:
  			   one
  			   two
  			 three
  			  four
expect-cursor: 1,7
expect-mode: normal

=== center centers
text:
    one
  two
      three
  four
cursor: 1,1
keys: :%center 20<CR>
--- vim 9.1
expect-text:
  	one
  	two
         three
  	four
expect-cursor: 1,2
expect-mode: normal

=== center with tabs
text:
    one
  two
      three
  four
cursor: 1,1
set: noet
keys: :%center 21<CR>
--- vim 9.1
expect-text:
  	 one
  	 two
  	three
  	four
expect-cursor: 1,3
expect-mode: normal

=== right with a tab inside
text:
  a	b
  ab
cursor: 1,1
keys: :%right 20<CR>
--- vim 9.1
expect-text:
  	      a	b
  		  ab
expect-cursor: 1,8
expect-mode: normal

=== center skips blank lines
text:
  x
     $
  y
cursor: 1,1
keys: :%center 10<CR>
--- vim 9.1
expect-text:
      x
     $
      y
expect-cursor: 1,5
expect-mode: normal

=== undo undoes
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: dd:u<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== undo then redo
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: dddd:u<CR>:u<CR>:red<CR>
--- vim 9.1
expect-text:
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== undo number goes back
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: dddddd:u 1<CR>
--- vim 9.1
expect-text:
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== undo zero undoes all
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: dddddd:u 0<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== undo number forward
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: dddddd:u 0<CR>:u 2<CR>
--- vim 9.1
expect-text:
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== undo unknown number
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: dd:u 7<CR>
output: yes
--- vim 9.1
expect-text:
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E830: Undo number 7 not found

=== redo with nothing to redo
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :red<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  Already at newest change

=== undo with nothing to undo
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :u<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  Already at oldest change

=== an ex command is one undo step
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :1d|1d|1d<CR>u
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
```

Create `plugins/editor/src/test/resources/vim/corpus/ex-info.case`:

```text
# Ex listings and settings: :print :number :list := :k :mark :delmarks :marks :jumps :changes :registers :nohlsearch :set (V2a).

=== p prints the line
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :p<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-output:
  two

=== capital P prints the line
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 2,1
keys: :P<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-output:
  two

=== p prints a range
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :2,3p<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal
expect-output:
  two
  three

=== p cursor goes to the last line
text:
  alpha
    beta
      gamma
  delta
cursor: 1,1
keys: :1,3p<CR>
--- vim 9.1
expect-text:
  alpha
    beta
      gamma
  delta
expect-cursor: 3,5
expect-mode: normal

=== p shows tabs as spaces
text:
  a	b
  	x
cursor: 1,1
keys: :%p<CR>
output: yes
--- vim 9.1
expect-text:
  a	b
  	x
expect-cursor: 2,2
expect-mode: normal
expect-output:
  a       b
          x

=== l lists tabs and ends
text:
  a	b
   x $
cursor: 1,1
keys: :%l<CR>
output: yes
--- vim 9.1
expect-text:
  a	b
   x $
expect-cursor: 2,2
expect-mode: normal
expect-output:
  a^Ib$$
   x $$

=== number prints numbers
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :2,3nu<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal
expect-output:
    2 two
    3 three

=== hash prints numbers
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :#<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    1 one

=== number width grows
text:
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
cursor: 998,1
keys: :.,$#<CR>
output: yes
--- vim 9.1
expect-text:
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
  x
expect-cursor: 1000,1
expect-mode: normal
expect-output:
   998 x
   999 x
  1000 x

=== p with l flag
text:
  a	b
cursor: 1,1
keys: :p l<CR>
output: yes
--- vim 9.1
expect-text:
  a	b
expect-cursor: 1,1
expect-mode: normal
expect-output:
  a^Ib$$

=== p with a count
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :p 2<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-output:
  one
  two

=== equals shows the last line number
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 3,1
keys: :=<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal
expect-output:
  10

=== equals with a range
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 3,1
keys: :.=<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal
expect-output:
  3

=== equals with a mark
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: 4Gma:'a=<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 4,1
expect-mode: normal
expect-output:
  4

=== k sets a mark
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: :3k a<CR>gg'a
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 3,5
expect-mode: normal

=== k without space
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: :3ka<CR>gg`a
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 3,5
expect-mode: normal

=== mark sets a mark at the first non-blank
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: :3mark b<CR>gg`b
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 3,5
expect-mode: normal

=== mark uppercase
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: :2mark Q<CR>gg'Q
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 2,1
expect-mode: normal

=== mark needs an argument
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: :mark<CR>
output: yes
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E471: Argument required

=== mark with two letters
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: :mark ab<CR>
output: yes
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E488: Trailing characters: ab

=== mark bad name
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: :mark %<CR>
output: yes
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E191: Argument must be a letter or forward/backward quote

=== mark previous context
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: :3mark '<CR>G''
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 1,3
expect-mode: normal

=== delmarks one
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: 3Gmagg:delm a<CR>'a
output: yes
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 1,3
expect-mode: normal
expect-output:
  E20: Mark not set

=== delmarks a range
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: 3Gma2Gmbgg:delm a-c<CR>'b
output: yes
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 1,3
expect-mode: normal
expect-output:
  E20: Mark not set

=== delmarks bang clears
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: 3Gmagg:delm!<CR>'a
output: yes
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 1,3
expect-mode: normal
expect-output:
  E20: Mark not set

=== delmarks bang with argument
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: :delm! a<CR>
output: yes
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E474: Invalid argument

=== delmarks without argument
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: :delm<CR>
output: yes
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E471: Argument required

=== delmarks bad range
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: :delm c-a<CR>
output: yes
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E475: Invalid argument: c-a

=== delmarks special
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: ix<Esc>:delm .<CR>`.
output: yes
--- vim 9.1
expect-text:
  x  first line
  second
      third indented
  fourth
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E20: Mark not set

=== marks lists marks
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: 3Gma4Gmb:marks ab<CR>
output: yes
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 4,1
expect-mode: normal
expect-output:
  mark line  col file/text
   a      3    4 third indented
   b      4    0 fourth

=== marks with the context mark
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: 3GG:marks '<CR>
output: yes
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 4,1
expect-mode: normal
expect-output:
  mark line  col file/text
   '      3    4 third indented

=== marks no match
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: :marks x<CR>
output: yes
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E283: No marks matching "x"

=== marks bytes in columns
text:
  é é x
cursor: 1,1
keys: $mc:marks c<CR>
output: yes
--- vim 9.1
expect-text:
  é é x
expect-cursor: 1,5
expect-mode: normal
expect-output:
  mark line  col file/text
   c      1    6 é é x

=== marks shows special marks
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: 2Gix<Esc>:marks .^[]<CR>
output: yes
--- vim 9.1
expect-text:
    first line
  xsecond
      third indented
  fourth
expect-cursor: 2,1
expect-mode: normal
expect-output:
  mark line  col file/text
   [      2    0 xsecond
   ]      2    1 xsecond
   ^      2    1 xsecond
   .      2    0 xsecond

=== marks shows visual marks
text:
    first line
  second
      third indented
  fourth
cursor: 1,1
keys: 2GVj<Esc>:marks <lt>><CR>
output: yes
--- vim 9.1
expect-text:
    first line
  second
      third indented
  fourth
expect-cursor: 3,1
expect-mode: normal
expect-output:
  mark line  col file/text
   <      2    0 second
   >      3    0 third indented

=== marks cuts long lines
text:
  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
cursor: 1,1
keys: $ma:marks a<CR>
output: yes
--- vim 9.1
expect-text:
  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
expect-cursor: 1,100
expect-mode: normal
expect-output:
  mark line  col file/text
   a      1   99 xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

=== jumps lists the jumplist
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: 5GG3G:jumps<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 3,1
expect-mode: normal
expect-output:
   jump line  col file/text
     3     1    0 one
     2     5    0 five
     1    10    0 ten
  >

=== jumps after moving back
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: 5GG3G<C-o><C-o>:jumps<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 5,1
expect-mode: normal
expect-output:
   jump line  col file/text
     1     1    0 one
  >  0     5    0 five
     1    10    0 ten
     2     3    0 three

=== changes lists the change list
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: xjjxGx:changes<CR>
output: yes
--- vim 9.1
expect-text:
  ne
  two
  hree
  four
  five
  six
  seven
  eight
  nine
  en
expect-cursor: 10,1
expect-mode: normal
expect-output:
  change line  col text
      3     1    0 ne
      2     3    0 hree
      1    10    0 en
  >

=== changes after g semicolon
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: xjjxGxg;g;:changes<CR>
output: yes
--- vim 9.1
expect-text:
  ne
  two
  hree
  four
  five
  six
  seven
  eight
  nine
  en
expect-cursor: 3,1
expect-mode: normal
expect-output:
  change line  col text
      1     1    0 ne
  >   0     3    0 hree
      1    10    0 en

=== registers lists registers
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: "ayy"byw:reg ab<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  Type Name Content
    l  "a   one^J
    c  "b   one

=== registers with display
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: "ayy:di a<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  Type Name Content
    l  "a   one^J

=== registers shows the unnamed and numbered
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: dd:reg "01<CR>
output: yes
--- vim 9.1
expect-text:
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  Type Name Content
    l  ""   one^J
    l  "1   one^J

=== registers shows control characters
text:
  a	b
cursor: 1,1
keys: "ay$:reg a<CR>
output: yes
--- vim 9.1
expect-text:
  a	b
expect-cursor: 1,1
expect-mode: normal
expect-output:
  Type Name Content
    c  "a   a^Ib

=== registers shows the last insert and command
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: ihi<Esc>:2<CR>:reg .:<CR>
output: yes
--- vim 9.1
expect-text:
  hione
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-output:
  Type Name Content
    c  ".   hi
    c  ":   2

=== registers shows the search pattern
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: /tw<CR>:reg /<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal
expect-output:
  /tw $
  Type Name Content
    c  "/   tw

=== registers with a block
text:
  abc
  def
cursor: 1,1
keys: <C-v>jl"ay:reg a<CR>
output: yes
--- vim 9.1
expect-text:
  abc
  def
expect-cursor: 1,1
expect-mode: normal
expect-output:
  Type Name Content
    b  "a   ab^Jde

=== registers cuts long text
text:
  yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy
cursor: 1,1
keys: "ayy:reg a<CR>
output: yes
--- vim 9.1
expect-text:
  yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy
expect-cursor: 1,1
expect-mode: normal
expect-output:
  Type Name Content
    l  "a   yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy

=== nohlsearch keeps the pattern
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: /tw<CR>:noh<CR>n
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal

=== set shows a number
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    shiftwidth=8

=== set shows a boolean
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set et?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  noexpandtab

=== set shows several
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw? ts?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    shiftwidth=8
    tabstop=8

=== set number without value shows it
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set ts<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    tabstop=8

=== set string without value shows it
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set fo<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    formatoptions=tcq

=== set sets and shows
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw=4 sw?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    shiftwidth=4

=== set unknown option
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set foo<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E518: Unknown option: foo

=== set unknown after a good one
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw=3 foo sw=5<CR>:set sw?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E518: Unknown option: foo $
    shiftwidth=3

=== set number required
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw=x<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E521: Number required after =: sw=x

=== set invalid boolean value
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set et=1<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E474: Invalid argument: et=1

=== set bang on a number
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw!<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    shiftwidth=8
  E488: Trailing characters: sw!

=== set no on a number
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set nosw<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E474: Invalid argument: nosw

=== set trailing after question
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set et?x<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E488: Trailing characters: et?x

=== set negative
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw=-1<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E487: Argument must be positive: sw=-1

=== set tabstop zero
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set ts=0<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E487: Argument must be positive: ts=0

=== set octal and hex
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw=010<CR>:set sw?<CR>:set ts=0x10<CR>:set ts?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    shiftwidth=8
    tabstop=16

=== set add and remove
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set nf+=alpha<CR>:set nf?<CR>:set nf-=octal<CR>:set nf?<CR>:set nf^=octal<CR>:set nf?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    nrformats=bin,octal,hex,alpha
    nrformats=bin,hex,alpha
    nrformats=octal,bin,hex,alpha

=== set add a duplicate
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set nf+=hex<CR>:set nf?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    nrformats=bin,octal,hex

=== set flags add and remove
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set fo+=ct<CR>:set fo?<CR>:set fo-=q<CR>:set fo?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    formatoptions=qct
    formatoptions=ct

=== set bad flag
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set fo=tcqz<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E539: Illegal character <z>: fo=tcqz

=== set whichwrap flags
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set ww+=h,l<CR>:set ww?<CR>:set ww-=b<CR>:set ww?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    whichwrap=b,s,h,l
    whichwrap=s,h,l

=== set bad whichwrap
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set ww=b,x<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E539: Illegal character <x>: ww=b,x

=== set bad backspace
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set bs=foo<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E474: Invalid argument: bs=foo

=== set bad matchpairs
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set mps=ab<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E474: Invalid argument: mps=ab

=== set bad clipboard
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set cb=bad<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E474: Invalid argument: cb=bad

=== set number add multiply subtract
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw+=2<CR>:set sw?<CR>:set sw^=3<CR>:set sw?<CR>:set sw-=4<CR>:set sw?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    shiftwidth=10
    shiftwidth=30
    shiftwidth=26

=== set default with ampersand
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw=3<CR>:set sw&<CR>:set sw?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    shiftwidth=8

=== set invert and bang
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set invet<CR>:set et?<CR>:set et!<CR>:set et?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    expandtab
  noexpandtab

=== set no with bang inverts
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set noet!<CR>:set et?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    expandtab

=== set with colon
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw:5<CR>:set sw?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    shiftwidth=5

=== setlocal and setglobal
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw=4<CR>:setlocal sw=3<CR>:setglobal sw?<CR>:setlocal sw?<CR>:set sw?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    shiftwidth=4
    shiftwidth=3
    shiftwidth=3

=== setglobal leaves the local value
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :setglobal sw=6<CR>:set sw?<CR>:setglobal sw?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    shiftwidth=8
    shiftwidth=6

=== setlocal global option
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :setlocal ws!<CR>:setglobal ws?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
  nowrapscan

=== set takes effect
text:
  	x
cursor: 1,1
keys: :set ts=4<CR>:p<CR>
output: yes
--- vim 9.1
expect-text:
  	x
expect-cursor: 1,2
expect-mode: normal
expect-output:
      x

=== set with space before question
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw ?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    shiftwidth=8

=== set changes the behaviour
text:
  abc abc
cursor: 1,1
keys: :set nows<CR>/abc<CR>n
output: yes
--- vim 9.1
expect-text:
  abc abc
expect-cursor: 1,5
expect-mode: normal
expect-output:
  /abc $
  /abc $
  E385: Search hit BOTTOM without match for: abc

=== set then bar
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw=2|set sw?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    shiftwidth=2

=== set with a trailing comment
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :set sw=2 "hi<CR>:set sw?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal
expect-output:
    shiftwidth=2
```

- [ ] **Step 6: List the divergent case and regenerate the reference**

In `docs/vim-divergences.md`, replace:

```markdown
| marks.case | backtick dot after o | '. takes each typed character's column, as when typing; Vim fed a whole string at once records where it started |
```

with:

```markdown
| marks.case | backtick dot after o | '. takes each typed character's column, as when typing; Vim fed a whole string at once records where it started |
| ex-range.case | a Vim command Jasper lacks | Jasper has no abbreviations; a command it lacks fails with E319 (docs/vim-divergences.md) |
```

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: `docs/editor-vim-reference.md` changes: `:` in Normal and Visual mode, and a new "Ex commands" section.

- [ ] **Step 7: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 150 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1422 tests pass: every case replays as Vim recorded it, and every command and Ex command has a case.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`.

Run:

```bash
./gradlew verifyPluginArchitecture -q
```

Expected: nothing is printed about the Editor (warnings from other plugins' sources may appear; they are not this plan's).

- [ ] **Step 8: Check the recording (needs `vim` on `PATH`)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: `git status` lists only the three new files (`??`), unchanged from what you created: Vim agrees with every expectation.

- [ ] **Step 9: Commit**

```bash
git branch --show-current   # must print claude/vim-v2a
git add plugins/editor/src/main/java/dev/jasper/editor/vim plugins/editor/src/test/java/dev/jasper/editor/vim plugins/editor/src/test/resources/vim/corpus docs/vim-divergences.md docs/editor-vim-reference.md
git commit -m "Add the Ex command line with ranges, line commands and listings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: `:s`, `:&`, `:&&`, `:~`, `&` and `g&`

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/ExSubstitute.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`
- Replace: `plugins/editor/src/test/java/dev/jasper/editor/vim/ExTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ui/VimBarTest.java`
- Create: `plugins/editor/src/test/resources/vim/corpus/ex-substitute.case`
- Modify: `docs/vim-divergences.md`; regenerate `docs/editor-vim-reference.md`

**Interfaces:**
- Consumes: Task 3's `Ex`, `ExCommand`, `ExInfo.line`, `ExInfo.mayPrint`, `VimEditor.ask`/`confirmMatch`/`say`, and Task 1's `Search` state and `VimRegex` groups.
- Produces (used by Task 5): `ExSubstitute.message(VimEditor, boolean countOnly)` (do_sub_msg: gives "N substitutions on M lines" when more than 'report' were made, and says whether it did).

`ExSubstitute.substitute` parses the command as ex_substitute does (a new pattern and string, or the last ones; the `:s/\n//` join shortcut; flags, kept for `:&&`; a count; `|`), then a `Run` holds the state of ex_substitute's two loops (`lnum`, `copycol`, `matchcol`, `prev_matchcol`, `nmatch`, `nmatch_tl`, `sub_firstline`, `new_start`) so the `c` question can pause them: `ask` shows the match, syncs undo (Vim without 'u' in 'cpoptions'), and `run(answer)` goes on where it stopped. Matching is against the line alone for a pattern that cannot span lines, else against the whole text rebuilt after each change; until a line is done the buffer holds its original text, as in Vim. `regsub` is vim_regsub with REGSUB_BACKSLASH (a CR breaks the line; `\n` inserts a NUL).

- [ ] **Step 1: Write the failing tests**

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/vim/ExTest.java` with:

```java
package dev.jasper.editor.vim;

import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Ex command line where the corpus cannot reach: the recorder's Vim runs its keys from feedkeys(), where Vim
 * never waits at the hit-enter prompt, so the output panel, the questions and the history are checked here.
 */
class ExTest {
    private final Vim vim = new Vim(VimHost.detached());

    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private VimEditor editor(VimTextArea area, String text) {
        area.setText(text);
        area.discardAllEdits();
        area.setCaretPosition(0);
        return new VimEditor(vim, area, EditorDefaults.VIM);
    }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private static List<String> output(VimEditor editor) { return editor.output().stream().map(VimEditor.OutputLine::text).toList(); }

    @Test void namesResolveAsVimAbbreviatesThem() {
        assertThat(ExTable.NAMES).hasSize(600);
        assertThat(ExTable.resolve("s")).isEqualTo("substitute");
        assertThat(ExTable.resolve("d")).isEqualTo("delete");
        assertThat(ExTable.resolve("p")).isEqualTo("print");
        assertThat(ExTable.resolve("P")).isEqualTo("Print");
        assertThat(ExTable.resolve("j")).isEqualTo("join");
        assertThat(ExTable.resolve("m")).isEqualTo("move");
        assertThat(ExTable.resolve("co")).isEqualTo("copy");
        assertThat(ExTable.resolve("g")).isEqualTo("global");
        assertThat(ExTable.resolve("v")).isEqualTo("vglobal");
        assertThat(ExTable.resolve("u")).isEqualTo("undo");
        assertThat(ExTable.resolve("noh")).isEqualTo("nohlsearch");
        assertThat(ExTable.resolve("reg")).isEqualTo("registers");
        assertThat(ExTable.resolve("se")).isEqualTo("set");
        assertThat(ExTable.resolve("setl")).isEqualTo("setlocal");
        assertThat(ExTable.resolve("zzz")).isNull();
        for (Ex.Def def : Ex.implemented())
            assertThat(ExTable.resolve(def.abbreviation())).as("the reference's abbreviation of :%s", def.name()).isEqualTo(def.name());
        var abbreviations = Ex.implemented().stream().map(def -> def.abbreviation()).toList();
        assertThat(abbreviations).contains("d", "y", "pu", "j", "m", "t", "co", "norm", "sor", "le", "ri", "ce", "k", "ma",
            "delm", "marks", "ju", "changes", "reg", "di", "u", "red", "noh", "se", "setl", "setg");
    }

    @Test void severalLinesStayInTheOutputPanelUntilAKey() throws Exception {
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "one\ntwo\nthree\n");
            type(editor, ":%p<CR>");
            assertThat(output(editor)).containsExactly("one", "two", "three");
            assertThat(editor.prompt()).contains("Press ENTER or type command to continue");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(editor.offer(KeyInput.named("CR"))).as("the panel takes the key").isTrue();
            assertThat(editor.output()).isEmpty();
            assertThat(editor.cursor()).as("<CR> only takes the output away").isEqualTo(new Pos(2, 0));
            type(editor, ":1,2p<CR>k");
            assertThat(editor.output()).isEmpty();
            assertThat(editor.cursor().line).as("another key takes the output away and runs").isEqualTo(0);
            type(editor, ":1,2p<CR>:3<CR>");
            assertThat(editor.cursor().line).as(": starts the next command line at once").isEqualTo(2);
            type(editor, ":p<CR>");
            assertThat(editor.output()).as("one line is a message").isEmpty();
            assertThat(editor.message()).isEqualTo("three");
            type(editor, ":%p|foo<CR>");
            assertThat(editor.output()).as("after an error Vim does not wait").isEmpty();
            assertThat(editor.message()).isEqualTo("E492: Not an editor command: foo");
            assertThat(editor.messageIsError()).isTrue();
        });
    }

    @Test void aConfirmationWaitsForItsAnswerThenTheRestOfTheLineRuns() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "a a\nb\n");
            type(editor, ":s/a/X/gc|2d<CR>");
            assertThat(editor.prompt()).contains("replace with X (y/n/a/q/l/^E/^Y)?");
            assertThat(editor.mode()).isEqualTo(Mode.COMMAND_LINE);
            assertThat(editor.highlights()).as("the match asked about is shown").anyMatch(h -> h[0] == 0 && h[1] == 0 && h[2] == 1);
            assertThat(editor.offer(KeyInput.of('y'))).as("the question takes the key").isTrue();
            type(editor, "n");
            assertThat(editor.prompt()).isEmpty();
            assertThat(area.getText()).as("the second match stayed, then :2d ran").isEqualTo("X a\n");
            type(editor, "u");
            assertThat(area.getText()).isEqualTo("a a\nb\n");
        });
    }

    @Test void aBackwardsRangeAsksBeforeItIsSwapped() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "1\n2\n3\n4\n");
            type(editor, ":3,1d<CR>");
            assertThat(editor.prompt()).contains("Backwards range given, OK to swap (y/n)?");
            type(editor, "x");
            assertThat(editor.prompt()).as("another key asks again").isPresent();
            type(editor, "y");
            assertThat(area.getText()).isEqualTo("4\n");
            type(editor, "u:3,1d<CR>n");
            assertThat(area.getText()).isEqualTo("1\n2\n3\n4\n");
        });
    }

    @Test void theCommandLineHasAHistoryAndTheColonRegister() throws Exception {
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "1\n2\n3\n");
            type(editor, ":2<CR>:3<CR>");
            assertThat(vim.commandHistory()).containsExactly("2", "3");
            type(editor, ":<Up><Up><CR>");
            assertThat(editor.cursor().line).isEqualTo(1);
            assertThat(vim.commandHistory()).as("a line run again moves to the end").containsExactly("3", "2");
            assertThat(vim.registers().get(':').map(Registers.Content::text)).contains("2");
            type(editor, ":<Esc>");
            assertThat(vim.commandHistory()).as("a cancelled line is not remembered").hasSize(2);
        });
    }

    @Test void normalGivesEscapeWhenItsKeysRunOut() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            type(editor, ":norm Ax<CR>");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(area.getText()).isEqualTo("abcx\n");
            type(editor, ":norm :s/b/B/c<C-v><CR>y<CR>");
            assertThat(editor.prompt()).as("the answer came from the keys").isEmpty();
            assertThat(area.getText()).isEqualTo("aBcx\n");
            type(editor, ":norm :s/c/C/c<C-v><CR><CR>");
            assertThat(editor.prompt()).as("out of keys, the question got <Esc>").isEmpty();
            assertThat(area.getText()).isEqualTo("aBcx\n");
        });
    }

    @Test void theReportMessagesFollowReport() throws Exception {
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "1\n2\n3\n4\n");
            type(editor, "3dd");
            assertThat(editor.message()).isEqualTo("3 fewer lines");
            type(editor, "u:set report=0<CR>yy");
            assertThat(editor.message()).isEqualTo("1 line yanked");
        });
    }
}
```

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/vim/ui/VimBarTest.java` with:

```java
package dev.jasper.editor.vim.ui;

import dev.jasper.editor.vim.EditorDefaults;
import dev.jasper.editor.vim.KeyInput;
import dev.jasper.editor.vim.Vim;
import dev.jasper.editor.vim.VimEditor;
import dev.jasper.editor.vim.VimHost;
import dev.jasper.editor.vim.VimTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VimBarTest {
    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    @Test void showsTheModeThePartlyTypedCommandAndMessages() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("abc\n");
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            assertThat(bar.statusText()).isEmpty();
            type(editor, "i");
            assertThat(bar.statusText()).isEqualTo("-- INSERT --");
            type(editor, "<Esc>2d");
            assertThat(bar.statusText()).isEmpty();
            assertThat(bar.commandText()).isEqualTo("2d");
            type(editor, "<Esc>\"qp");
            assertThat(bar.statusText()).isEqualTo("E353: Nothing in register q");
            assertThat(bar.flashing()).isTrue();
            type(editor, "R");
            assertThat(bar.statusText()).isEqualTo("-- REPLACE --");
            bar.detach();
            type(editor, "<Esc>i");
            assertThat(bar.statusText()).as("a detached bar no longer follows the editor").isEmpty();
        });
    }

    @Test void showsTheCommandLineWithItsCursor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("one two\n");
            area.setCaretPosition(0);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            type(editor, "/tw");
            assertThat(bar.statusText()).isEqualTo("/tw");
            assertThat(bar.commandLineCursor()).isEqualTo(3);
            type(editor, "<Left>");
            assertThat(bar.commandLineCursor()).isEqualTo(2);
            type(editor, "<C-v><C-a>");
            assertThat(bar.statusText()).as("a control character shows as ^A").isEqualTo("/t^Aw");
            type(editor, "<Esc>");
            assertThat(bar.commandLineCursor()).isEqualTo(-1);
            type(editor, "/two<CR>");
            assertThat(bar.statusText()).as("a search echoes its pattern").isEqualTo("/two");
            type(editor, "n");
            assertThat(bar.statusText()).isEqualTo("search hit BOTTOM, continuing at TOP");
        });
    }

    @Test void showsTheVisualAreasSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("ab\u00e9d\nefgh\nijkl\n");
            area.setCaretPosition(0);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            type(editor, "vl");
            assertThat(bar.statusText()).isEqualTo("-- VISUAL --");
            assertThat(bar.commandText()).isEqualTo("2");
            type(editor, "l");
            assertThat(bar.commandText()).as("characters, then UTF-8 bytes when they differ").isEqualTo("3-4");
            type(editor, "j");
            assertThat(bar.commandText()).isEqualTo("2");
            type(editor, "<C-v>");
            assertThat(bar.commandText()).isEqualTo("2x3");
            type(editor, "V");
            assertThat(bar.commandText()).isEqualTo("2");
            type(editor, "\"");
            assertThat(bar.commandText()).as("a pending key replaces the size").isEqualTo("\"");
        });
    }

    @Test void showsTheOutputPanelAndQuestions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("one\ntwo\n");
            area.setCaretPosition(0);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            type(editor, ":%p");
            assertThat(bar.statusText()).isEqualTo(":%p");
            type(editor, "<CR>");
            assertThat(bar.outputLines()).containsExactly("one", "two");
            assertThat(bar.statusText()).isEqualTo("Press ENTER or type command to continue");
            type(editor, "<CR>");
            assertThat(bar.outputLines()).isEmpty();
            type(editor, ":1s/one/1/c<CR>");
            assertThat(bar.statusText()).isEqualTo("replace with 1 (y/n/a/q/l/^E/^Y)?");
            type(editor, "y");
            assertThat(bar.statusText()).isEmpty();
            type(editor, ":foo<CR>");
            assertThat(bar.statusText()).isEqualTo("E492: Not an editor command: foo");
        });
    }
}
```

Create `plugins/editor/src/test/resources/vim/corpus/ex-substitute.case`:

```text
# Ex :s, :&, :&&, :~, & and g& (V2a).

=== s replaces the first match
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/<CR>
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s g replaces all in the line
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/g<CR>
--- vim 9.1
expect-text:
  X bar X
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s on a range
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/foo/X/<CR>
output: yes
--- vim 9.1
expect-text:
  X bar foo
  bar X
  baz
  X foo foo
expect-cursor: 4,1
expect-mode: normal
expect-output:
  3 substitutions on 3 lines

=== s g on all lines reports
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/foo/X/g<CR>
output: yes
--- vim 9.1
expect-text:
  X bar X
  bar X
  baz
  X X X
expect-cursor: 4,1
expect-mode: normal
expect-output:
  6 substitutions on 3 lines

=== s puts the cursor on the last changed line
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/foo/X/<CR>
--- vim 9.1
expect-text:
  X bar foo
  bar X
  baz
  X foo foo
expect-cursor: 4,1
expect-mode: normal

=== s cursor goes to the first non-blank
text:
    foo
    bar
cursor: 2,1
keys: :%s/foo/X/<CR>
--- vim 9.1
expect-text:
    X
    bar
expect-cursor: 1,3
expect-mode: normal

=== s not found
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/zzz/X/<CR>
output: yes
--- vim 9.1
expect-text:
  foo bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E486: Pattern not found: zzz

=== s not found with e is silent
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/zzz/X/e<CR>
output: yes
--- vim 9.1
expect-text:
  foo bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal
expect-output:

=== s with an empty pattern uses the last search
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 2,1
keys: /bar<CR>:s//X/<CR>
--- vim 9.1
expect-text:
  foo X foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s sets the last search for n
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/bar/X/<CR>n
--- vim 9.1
expect-text:
  foo X foo
  bar foo
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal

=== s pattern with a different delimiter
text:
  a/b/c
cursor: 1,1
keys: :s#/#-#g<CR>
--- vim 9.1
expect-text:
  a-b-c
expect-cursor: 1,1
expect-mode: normal

=== s delimiter escaped in the pattern
text:
  a/b/c
cursor: 1,1
keys: :s/\//-/<CR>
--- vim 9.1
expect-text:
  a-b/c
expect-cursor: 1,1
expect-mode: normal

=== s letters as delimiter fail
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :sxfooxbarx<CR>
output: yes
--- vim 9.1
expect-text:
  foo bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E492: Not an editor command: sxfooxbarx

=== s without closing delimiter
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X<CR>
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s without a substitute deletes
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo<CR>
--- vim 9.1
expect-text:
   bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,2
expect-mode: normal

=== s ampersand is the match
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/fo*/<&>/g<CR>
--- vim 9.1
expect-text:
  <foo> bar <foo>
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s backslash zero is the match
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/bar/[\0]/<CR>
--- vim 9.1
expect-text:
  foo [bar] foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s groups
text:
  john smith
cursor: 1,1
keys: :s/\(\w\+\) \(\w\+\)/\2, \1/<CR>
--- vim 9.1
expect-text:
  smith, john
expect-cursor: 1,1
expect-mode: normal

=== s upper case one
text:
  hello world
cursor: 1,1
keys: :s/\w\+/\u&/g<CR>
--- vim 9.1
expect-text:
  Hello World
expect-cursor: 1,1
expect-mode: normal

=== s upper case all
text:
  hello world
cursor: 1,1
keys: :s/o w/\U&/<CR>
--- vim 9.1
expect-text:
  hellO World
expect-cursor: 1,1
expect-mode: normal

=== s upper case until e
text:
  hello world
cursor: 1,1
keys: :s/\(hello\) \(world\)/\U\1\e \2/<CR>
--- vim 9.1
expect-text:
  HELLO world
expect-cursor: 1,1
expect-mode: normal

=== s lower case
text:
  HELLO WORLD
cursor: 1,1
keys: :s/\w\+/\L&/<CR>
--- vim 9.1
expect-text:
  hello WORLD
expect-cursor: 1,1
expect-mode: normal

=== s lower then upper first
text:
  hELLO
cursor: 1,1
keys: :s/.*/\L\u&/<CR>
--- vim 9.1
expect-text:
  Hello
expect-cursor: 1,1
expect-mode: normal

=== s backslash r splits the line
text:
  a,b,c
cursor: 1,1
keys: :s/,/\r/g<CR>
--- vim 9.1
expect-text:
  a
  b
  c
expect-cursor: 3,1
expect-mode: normal

=== s backslash n makes a nul
text:
  a,b
cursor: 1,1
keys: :s/,/-\t-/<CR>
--- vim 9.1
expect-text:
  a-	-b
expect-cursor: 1,1
expect-mode: normal

=== s literal ampersand
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/a\&b/<CR>
--- vim 9.1
expect-text:
  a&b bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s backslash backslash
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/a\\b/<CR>
--- vim 9.1
expect-text:
  a\b bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s tilde is the previous substitute
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/<CR>:s/bar/~Y/<CR>
--- vim 9.1
expect-text:
  X XY foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s escaped tilde
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/a\~b/<CR>
--- vim 9.1
expect-text:
  a~b bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s tilde in the pattern
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/bar/<CR>:s/~/Z/<CR>
--- vim 9.1
expect-text:
  Z bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s with a count
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/ 2<CR>
output: yes
--- vim 9.1
expect-text:
  X bar foo
  bar X
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal
expect-output:

=== s count after a range starts at its end
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :1,2s/foo/X/ 2<CR>
--- vim 9.1
expect-text:
  foo bar foo
  bar X
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal

=== s zero count
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/ 0<CR>
output: yes
--- vim 9.1
expect-text:
  foo bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E939: Positive count required

=== s trailing characters
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/ q<CR>
output: yes
--- vim 9.1
expect-text:
  foo bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E488: Trailing characters: q

=== s then bar
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/|2d<CR>
--- vim 9.1
expect-text:
  X bar foo
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal

=== s n counts matches
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 2,1
keys: :%s/foo//gn<CR>
output: yes
--- vim 9.1
expect-text:
  foo bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal
expect-output:
  6 matches on 3 lines

=== s n counts without moving
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 2,2
keys: :%s/foo//n<CR>
--- vim 9.1
expect-text:
  foo bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal

=== s i ignores case
text:
  Foo foo
cursor: 1,1
keys: :s/FOO/x/gi<CR>
--- vim 9.1
expect-text:
  x x
expect-cursor: 1,1
expect-mode: normal

=== s I keeps case with ignorecase
text:
  Foo foo
cursor: 1,1
set: ic
keys: :s/foo/x/gI<CR>
--- vim 9.1
expect-text:
  Foo x
expect-cursor: 1,1
expect-mode: normal

=== s with ignorecase
text:
  Foo foo
cursor: 1,1
set: ic
keys: :s/foo/x/<CR>
--- vim 9.1
expect-text:
  x foo
expect-cursor: 1,1
expect-mode: normal

=== s with smartcase
text:
  Foo foo
cursor: 1,1
set: ic scs
keys: :s/Foo/x/g<CR>
--- vim 9.1
expect-text:
  x foo
expect-cursor: 1,1
expect-mode: normal

=== s p prints the line
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/p<CR>
output: yes
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal
expect-output:
  X bar foo

=== s hash prints it numbered
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/#<CR>
output: yes
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal
expect-output:
    1 X bar foo

=== s l lists it
text:
  a	foo
cursor: 1,1
keys: :s/foo/X/l<CR>
output: yes
--- vim 9.1
expect-text:
  a	X
expect-cursor: 1,1
expect-mode: normal
expect-output:
  a^IX$$

=== s with gdefault replaces all
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
set: gd
keys: :s/foo/X/<CR>
--- vim 9.1
expect-text:
  X bar X
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s with gdefault and g replaces the first
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
set: gd
keys: :s/foo/X/g<CR>
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s repeat with s alone
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/<CR>j:s<CR>
--- vim 9.1
expect-text:
  X bar foo
  bar X
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal

=== s repeat keeps no flags
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/g<CR>:4s<CR>
--- vim 9.1
expect-text:
  X bar X
  bar foo
  baz
  X foo foo
expect-cursor: 4,1
expect-mode: normal

=== s repeat with ampersand keeps flags
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/g<CR>:4&&<CR>
--- vim 9.1
expect-text:
  X bar X
  bar foo
  baz
  X X X
expect-cursor: 4,1
expect-mode: normal

=== s repeat with new flags
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/<CR>:4s g<CR>
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  X X X
expect-cursor: 4,1
expect-mode: normal

=== sg is s with g
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/<CR>:4sg<CR>
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  X X X
expect-cursor: 4,1
expect-mode: normal

=== s without a previous substitute
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s<CR>
output: yes
--- vim 9.1
expect-text:
  foo bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E33: No previous substitute regular expression

=== s empty pattern without any previous
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s//x/<CR><CR>
output: yes
--- vim 9.1
expect-text:
  foo bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal
expect-output:
  E35: No previous regular expression
  E476: Invalid command

=== s bad pattern
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/\(/x/<CR><CR>
output: yes
--- vim 9.1
expect-text:
  foo bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal
expect-output:
  E54: Unmatched \(
  E476: Invalid command

=== s backslash slash uses the search pattern
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: /bar<CR>:s/foo/X/<CR>:2s\/Y/<CR>
--- vim 9.1
expect-text:
  X bar foo
  Y foo
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal

=== s backslash ampersand uses the substitute pattern
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: /bar<CR>:s/foo/X/<CR>/baz<CR>:4s\&Y/<CR>
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  Y/ foo foo
expect-cursor: 4,1
expect-mode: normal

=== s r uses the last search pattern
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/<CR>/bar<CR>:2s//Y/r<CR>
--- vim 9.1
expect-text:
  X bar foo
  Y foo
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal

=== tilde command uses the last pattern
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/<CR>/bar<CR>:~<CR>
--- vim 9.1
expect-text:
  X X foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== ampersand command
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/<CR>:4&<CR>
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  X foo foo
expect-cursor: 4,1
expect-mode: normal

=== ampersand normal repeats on the line
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/<CR>j&
--- vim 9.1
expect-text:
  X bar foo
  bar X
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal

=== ampersand normal with a count
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/<CR>j2&
--- vim 9.1
expect-text:
  X bar foo
  bar X
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal

=== ampersand normal without a previous
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: &
output: yes
--- vim 9.1
expect-text:
  foo bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E33: No previous substitute regular expression

=== g ampersand repeats everywhere with flags
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/g<CR>ug&
--- vim 9.1
expect-text:
  X bar X
  bar X
  baz
  X X X
expect-cursor: 4,1
expect-mode: normal

=== g ampersand uses the last search
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/g<CR>u/bar<CR>g&
--- vim 9.1
expect-text:
  foo X foo
  X foo
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal

=== s then undo
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/foo/X/g<CR>u
--- vim 9.1
expect-text:
  foo bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s then undo the cursor
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 3,2
keys: :%s/foo/X/g<CR>u
--- vim 9.1
expect-text:
  foo bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s empty match at every position
text:
  abc
cursor: 1,1
keys: :s/x*/-/g<CR>
--- vim 9.1
expect-text:
  -a-b-c
expect-cursor: 1,1
expect-mode: normal

=== s dollar appends
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/$/!/<CR>
--- vim 9.1
expect-text:
  foo bar foo!
  bar foo!
  baz!
  foo foo foo!
expect-cursor: 4,1
expect-mode: normal

=== s caret prepends
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/^/> /<CR>
--- vim 9.1
expect-text:
  > foo bar foo
  > bar foo
  > baz
  > foo foo foo
expect-cursor: 4,1
expect-mode: normal

=== s join lines with backslash n
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/\n//<CR>
output: yes
--- vim 9.1
expect-text:
  foo bar foobar foobazfoo foo foo
expect-cursor: 1,22
expect-mode: normal
expect-output:
  3 substitutions on 1 line

=== s join two lines
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/\n//<CR>
--- vim 9.1
expect-text:
  foo bar foobar foo
  baz
  foo foo foo
expect-cursor: 1,12
expect-mode: normal

=== s join with a flag
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :1,2s/\n//p<CR>
output: yes
--- vim 9.1
expect-text:
  foo bar foobar foobaz
  foo foo foo
expect-cursor: 1,19
expect-mode: normal
expect-output:
  foo bar foobar foobaz

=== s newline to comma
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/\n/,/<CR>
--- vim 9.1
expect-text:
  foo bar foo,bar foo,baz,foo foo foo,
expect-cursor: 1,1
expect-mode: normal

=== s multi line match
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/foo\nbar/X/<CR>
--- vim 9.1
expect-text:
  foo bar X foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s match across lines with g
text:
  a1
  b2
  a3
  b4
cursor: 1,1
keys: :%s/\d\n\(b\)/-\1/g<CR>
--- vim 9.1
expect-text:
  a-b2
  a-b4
expect-cursor: 2,1
expect-mode: normal

=== s zs
text:
  foobar
cursor: 1,1
keys: :s/foo\zsbar/X/<CR>
--- vim 9.1
expect-text:
  fooX
expect-cursor: 1,1
expect-mode: normal

=== s ze
text:
  foobar
cursor: 1,1
keys: :s/foo\zebar/X/<CR>
--- vim 9.1
expect-text:
  Xbar
expect-cursor: 1,1
expect-mode: normal

=== s zs after a newline
text:
  a
  b
cursor: 1,1
keys: :%s/a\n\zsb/X/<CR>
--- vim 9.1
expect-text:
  a
  X
expect-cursor: 2,1
expect-mode: normal

=== s group across lines
text:
  ab
  cd
cursor: 1,1
keys: :s/\(b\nc\)/[\1]/<CR>
--- vim 9.1
expect-text:
  a[b
  c]d
expect-cursor: 2,1
expect-mode: normal

=== s replaces a mark line and keeps marks
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 2,1
keys: ma:%s/foo/X/g<CR>'a
--- vim 9.1
expect-text:
  X bar X
  bar X
  baz
  X X X
expect-cursor: 2,1
expect-mode: normal

=== s sets the jumplist
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 4,1
keys: :1s/foo/X/<CR><C-o>
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 4,1
expect-mode: normal

=== s repeat keeps the cursor column after dollar
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/<CR>2G$:s<CR>
--- vim 9.1
expect-text:
  X bar foo
  bar X
  baz
  foo foo foo
expect-cursor: 2,5
expect-mode: normal

=== s confirm y
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/gc<CR>yy
--- vim 9.1
expect-text:
  X bar X
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s confirm n then y
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/gc<CR>ny
--- vim 9.1
expect-text:
  foo bar X
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s confirm q stops
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/foo/X/gc<CR>yq
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,7
expect-mode: normal

=== s confirm a does the rest
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/foo/X/gc<CR>na
output: yes
--- vim 9.1
expect-text:
  foo bar X
  bar X
  baz
  X X X
expect-cursor: 4,1
expect-mode: normal
expect-output:
  replace with X (y/n/a/q/l/^E/^Y)?
  replace with X (y/n/a/q/l/^E/^Y)?
  5 substitutions on 3 lines

=== s confirm l does this and stops
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/foo/X/gc<CR>nl
--- vim 9.1
expect-text:
  foo bar X
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s confirm Esc stops
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/foo/X/gc<CR>y<Esc>
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,7
expect-mode: normal

=== s confirm other keys ask again
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/gc<CR>xzyn
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,7
expect-mode: normal

=== s confirm across lines
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/foo/X/c<CR>ynyy
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  X foo foo
expect-cursor: 4,1
expect-mode: operator-pending

=== s confirm with a split
text:
  a,b,c
cursor: 1,1
keys: :s/,/\r/gc<CR>yny
--- vim 9.1
expect-text:
  a
  b,c
expect-cursor: 2,2
expect-mode: operator-pending

=== s confirm then undo
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/foo/X/gc<CR>yyyyyy<Esc>u
--- vim 9.1
expect-text:
  X bar X
  bar X
  baz
  foo foo foo
expect-cursor: 4,1
expect-mode: normal

=== s confirm cursor stays on the last match
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/gc<CR>ny
--- vim 9.1
expect-text:
  foo bar X
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s confirm reports
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :%s/foo/X/gc<CR>yyyy<Esc>
output: yes
--- vim 9.1
expect-text:
  X bar X
  bar X
  baz
  X foo foo
expect-cursor: 4,3
expect-mode: normal
expect-output:
  replace with X (y/n/a/q/l/^E/^Y)?
  replace with X (y/n/a/q/l/^E/^Y)?
  replace with X (y/n/a/q/l/^E/^Y)?
  replace with X (y/n/a/q/l/^E/^Y)?
  replace with X (y/n/a/q/l/^E/^Y)?
  4 substitutions on 3 lines

=== s confirm then more of the line
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/c|2d<CR>y
registers: " 0 1 a -
--- vim 9.1
expect-text:
  X bar foo
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "bar foo\n"
expect-register 0: none
expect-register 1: l "bar foo\n"
expect-register a: none
expect-register -: none

=== s on a visual range
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: Vj:s/foo/X/g<CR>
--- vim 9.1
expect-text:
  X bar X
  bar X
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal

=== colon backslash ampersand uses the substitute pattern
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :s/o/o/<CR>:\&<CR>
--- vim 9.1
expect-text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 2,1
expect-mode: normal

=== backslash bar is not a separator
text:
  one
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
cursor: 1,1
keys: :s/o/a\|b/<CR>
--- vim 9.1
expect-text:
  a|bne
  two
  three
  four
  five
  six
  seven
  eight
  nine
  ten
expect-cursor: 1,1
expect-mode: normal

=== s with a tab typed in the substitute
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/<C-v><Tab>/<CR>
--- vim 9.1
expect-text:
  	 bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,3
expect-mode: normal

=== s literal CR splits
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/ /<C-v><CR>/<CR>
--- vim 9.1
expect-text:
  foo
  bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 2,1
expect-mode: normal

=== s with a backslash bar in the pattern
text:
  a|b
cursor: 1,1
keys: :s/a\|b/X/g<CR>
--- vim 9.1
expect-text:
  X|X
expect-cursor: 1,1
expect-mode: normal

=== s reports lines with report 0
text:
  a
  b
cursor: 1,1
set: report=0
keys: :s/a/b/<CR>
output: yes
--- vim 9.1
expect-text:
  b
  b
expect-cursor: 1,1
expect-mode: normal
expect-output:
  1 substitution on 1 line

=== s keeps a trailing comment
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/X/ "hello<CR>
--- vim 9.1
expect-text:
  X bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal

=== s expression is not available
divergent: Jasper has no expressions: \= in a substitute string fails with E319 (docs/vim-divergences.md)
text:
  foo bar foo
  bar foo
  baz
  foo foo foo
cursor: 1,1
keys: :s/foo/\=1+1/<CR>
output: yes
--- expected
expect-text:
  foo bar foo
  bar foo
  baz
  foo foo foo
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E319: Sorry, the command is not available in this version
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest'
```

Expected: `ExTest.aConfirmationWaitsForItsAnswerThenTheRestOfTheLineRuns`, `ExTest.normalGivesEscapeWhenItsKeysRunOut` and `VimBarTest.showsTheOutputPanelAndQuestions` fail: `:s` gives E319 until this task registers it.

- [ ] **Step 3: Write `ExSubstitute`**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ExSubstitute.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import java.util.List;
import java.util.regex.Matcher;

/**
 * {@code :s}, {@code :&}, {@code :&&} and {@code :~}: a port of Vim's ex_substitute (ex_cmds.c) with vim_regsub and
 * regtilde (regexp.c). The pattern, the substitute string and the flags are remembered for the next {@code :s}
 * without them. Confirmation ({@code c}) asks before each substitution; the substitution waits for the answer and
 * then goes on where it stopped.
 *
 * <p>Lines are matched as Vim matches them: a pattern that cannot span lines against the line alone, one that can
 * against the whole text. Until a line is done its text in the buffer is the original, so later matches in it are
 * found in the original text, as in Vim.
 */
final class ExSubstitute {
    private ExSubstitute() { }

    /** CAR: in a substitute string it breaks the line. */
    private static final char CAR = '\r';

    /** ex_substitute. */
    static void substitute(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Ex ex = editor.ex();
        Search search = editor.vim().search();
        String arg = cmd.arg;
        if (!ex.globalBusy()) {
            ex.subNsubs = 0;
            ex.subNlines = 0;
        }
        long startNsubs = ex.subNsubs;
        int whichPat = cmd.name().equals("~") ? -1 : Search.SUBSTITUTE;
        String pat = null, sub;
        int i = 0;
        boolean endcolumn = false;
        char first = arg.isEmpty() ? 0 : arg.charAt(0);
        if (cmd.typed.startsWith("s") && first != 0 && !Ex.isWhite(first) && "0123456789cegriIp|\"".indexOf(first) < 0) {
            if (first < 128 && Character.isLetter(first)) throw new VimFailure("E146: Regular expressions can't be delimited by letters");
            char delimiter;
            if (first == '\\') {
                i++;
                char which = i < arg.length() ? arg.charAt(i) : 0;
                if (which == 0 || "/?&".indexOf(which) < 0) throw new VimFailure("E10: \\ should be followed by /, ? or &");
                if (which != '&') whichPat = Search.SEARCH;
                pat = "";
                delimiter = which;
                i++;
            } else {
                whichPat = -1;
                delimiter = first;
                i++;
                int end = i + Search.skipPattern(arg.substring(i), delimiter);
                pat = arg.substring(i, end);
                i = end < arg.length() ? end + 1 : end;
            }
            StringBuilder s = new StringBuilder();
            while (i < arg.length()) {
                char ch = arg.charAt(i);
                if (ch == delimiter) {
                    i++;
                    break;
                }
                if (ch == '\\' && i + 1 < arg.length()) {
                    s.append(ch);
                    i++;
                }
                s.append(arg.charAt(i++));
            }
            sub = s.toString();
            search.oldSub = sub;
        } else {
            if (search.oldSub == null) throw new VimFailure("E33: No previous substitute regular expression");
            sub = search.oldSub;
            endcolumn = editor.curswant() == Integer.MAX_VALUE;
        }
        String rest = arg.substring(Math.min(i, arg.length()));
        // ":s/\n//" joins lines.
        if (pat != null && pat.equals("\\n") && sub.isEmpty()
            && (rest.isEmpty() || rest.length() == 1 && "glp#".indexOf(rest.charAt(0)) >= 0)) {
            join(cmd, rest);
            search.save(Search.SUBSTITUTE, pat);
            search.remember(pat);
            return;
        }
        if (rest.startsWith("&")) rest = rest.substring(1);
        else {
            // 'gdefault' makes g the default; a g flag then turns it off.
            search.subAll = editor.options().flag("gdefault");
            search.subAsk = false;
            search.subError = true;
            search.subPrint = search.subList = search.subCount = search.subNumber = false;
            search.subIgnoreCase = 0;
        }
        int f = 0;
        for (; f < rest.length(); f++) {
            char ch = rest.charAt(f);
            switch (ch) {
                case 'g' -> search.subAll = !search.subAll;
                case 'c' -> search.subAsk = !search.subAsk;
                case 'n' -> search.subCount = true;
                case 'e' -> search.subError = !search.subError;
                case 'r' -> whichPat = -1;
                case 'p' -> search.subPrint = true;
                case '#' -> search.subPrint = search.subNumber = true;
                case 'l' -> search.subPrint = search.subList = true;
                case 'i', 'I' -> search.subIgnoreCase = ch;
                default -> { }
            }
            if ("gcnerp#liI".indexOf(ch) < 0) break;
        }
        rest = rest.substring(f);
        if (search.subCount) search.subAsk = false;
        boolean saveAll = search.subAll, saveAsk = search.subAsk;
        rest = rest.substring(Ex.skipWhite(rest, 0));
        if (!rest.isEmpty() && Character.isDigit(rest.charAt(0))) {
            int d = 0;
            long count = 0;
            while (d < rest.length() && Character.isDigit(rest.charAt(d))) count = Math.min(Integer.MAX_VALUE, count * 10 + rest.charAt(d++) - '0');
            rest = rest.substring(d);
            if (count <= 0 && search.subError) throw new VimFailure("E939: Positive count required");
            cmd.line1 = cmd.line2;
            cmd.line2 = (int) Math.min(editor.buffer().count(), cmd.line2 + count - 1);
        }
        rest = rest.substring(Ex.skipWhite(rest, 0));
        if (!rest.isEmpty() && rest.charAt(0) != '"') {
            if (rest.charAt(0) == '|') cmd.nextcmd = rest.substring(1);
            else throw new VimFailure("E488: Trailing characters: " + rest);
        }
        VimRegex regex;
        String used;
        try {
            used = search.choose(pat, Search.SUBSTITUTE, whichPat);
            Options o = editor.options();
            boolean ic = search.subIgnoreCase == 'i' ? true : search.subIgnoreCase != 'I' && o.flag("ignorecase");
            boolean scs = search.subIgnoreCase == 0 && o.flag("smartcase");
            regex = VimRegex.compile(used, new VimRegex.Settings(ic, scs, o.keywords().javaClass(), search.prevSub));
        } catch (VimFailure | IllegalArgumentException bad) {
            if (search.subError) {
                editor.message(bad.getMessage(), true);
                throw new VimFailure("E476: Invalid command");
            }
            return;
        }
        if (sub.startsWith("\\=")) throw new VimFailure("E319: Sorry, the command is not available in this version");
        sub = regtilde(search, sub);
        new Run(cmd, regex, used, sub, startNsubs, endcolumn, saveAll, saveAsk).start();
    }

    /** The {@code :%s/\n//} shortcut: the range's lines and the next one are joined, without spaces. */
    private static void join(ExCommand cmd, String flags) {
        VimEditor editor = cmd.editor;
        Ex ex = editor.ex();
        editor.setCursor(new Pos(cmd.line1 - 1, 0));
        if (flags.equals("l")) cmd.flags = Ex.LIST;
        else if (flags.equals("#")) cmd.flags = Ex.NUMBER;
        else if (flags.equals("p")) cmd.flags = Ex.PRINT;
        int joined = cmd.line2 - cmd.line1 + 1;
        if (cmd.line2 < editor.buffer().count()) joined++;
        if (joined > 1) {
            NormalCommands.joinLines(cmd.context(), cmd.line1 - 1, joined, false);
            ex.subNsubs = joined - 1;
            ex.subNlines = 1;
            message(editor, false);
            ExInfo.mayPrint(cmd);
        }
    }

    /**
     * regtilde: {@code ~} in the substitute string stands for the previous one; the result is remembered as the
     * previous one (reg_prev_sub), for the next {@code ~}.
     */
    private static String regtilde(Search search, String source) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '~') {
                if (search.prevSub != null) out.append(search.prevSub);
                continue;
            }
            if (c == '\\' && i + 1 < source.length()) {
                out.append(c);
                c = source.charAt(++i);
            }
            out.append(c);
        }
        String result = out.toString();
        search.prevSub = result.isEmpty() ? null : result;
        return result;
    }

    /**
     * do_sub_msg: "3 substitutions on 2 lines" (or "matches" for {@code n}) when more than 'report' substitutions
     * were made and the command was typed or changed more than one line.
     *
     * @return whether it gave the message
     */
    static boolean message(VimEditor editor, boolean countOnly) {
        Ex ex = editor.ex();
        long nsubs = ex.subNsubs, nlines = ex.subNlines;
        int report = editor.options().number("report");
        if (!(nsubs > report && (ex.typed() || nlines > 1 || report < 1) || countOnly)) return false;
        String what = countOnly ? (nsubs == 1 ? " match" : " matches") : (nsubs == 1 ? " substitution" : " substitutions");
        editor.say(nsubs + what + " on " + nlines + (nlines == 1 ? " line" : " lines"));
        return true;
    }

    /**
     * vim_regsub with REGSUB_BACKSLASH and REGSUB_MAGIC: {@code &} and {@code \0}–{@code \9} are the match and its
     * groups, <code>&#92;u &#92;U &#92;l &#92;L &#92;e &#92;E</code> change case, {@code \r} and a CR break the line, {@code \n} is a NUL and
     * {@code \t} a tab. Other backslashes stay, to be removed afterwards; a CR or backslash from the match is
     * escaped so it stays as it is, and a line break inside a group becomes a CR.
     */
    static String regsub(String source, java.util.function.IntFunction<String> group) {
        StringBuilder dst = new StringBuilder();
        char funcOne = 0, funcAll = 0;
        int n = source.length();
        for (int i = 0; i < n; ) {
            int c = source.codePointAt(i);
            i += Character.charCount(c);
            int no = -1;
            if (c == '&') no = 0;
            else if (c == '\\' && i < n) {
                char next = source.charAt(i);
                if (next >= '0' && next <= '9') {
                    no = next - '0';
                    i++;
                } else if ("uUlLeE".indexOf(next) >= 0) {
                    i++;
                    switch (next) {
                        case 'u' -> funcOne = 'u';
                        case 'U' -> funcAll = 'u';
                        case 'l' -> funcOne = 'l';
                        case 'L' -> funcAll = 'l';
                        default -> funcOne = funcAll = 0;
                    }
                    continue;
                }
            }
            if (no < 0) {
                if (c == '\\' && i < n) {
                    char next = source.charAt(i);
                    switch (next) {
                        case 'r' -> { c = CAR; i++; }
                        case 'n' -> { c = 0; i++; }
                        case 't' -> { c = '\t'; i++; }
                        case 'b' -> { c = 8; i++; }
                        default -> {
                            dst.append('\\');
                            c = source.codePointAt(i);
                            i += Character.charCount(c);
                        }
                    }
                }
                if (funcOne != 0) {
                    c = apply(funcOne, c);
                    funcOne = 0;
                } else if (funcAll != 0) c = apply(funcAll, c);
                dst.appendCodePoint(c);
            } else {
                String text = group.apply(no);
                if (text == null) continue;
                for (int k = 0; k < text.length(); ) {
                    int g = text.codePointAt(k);
                    k += Character.charCount(g);
                    if (g == '\n') dst.append(CAR);
                    else if (g == CAR || g == '\\') dst.append('\\').appendCodePoint(g);
                    else {
                        if (funcOne != 0) {
                            g = apply(funcOne, g);
                            funcOne = 0;
                        } else if (funcAll != 0) g = apply(funcAll, g);
                        dst.appendCodePoint(g);
                    }
                }
            }
        }
        return dst.toString();
    }

    private static int apply(char func, int c) { return func == 'u' ? Character.toUpperCase(c) : Character.toLowerCase(c); }

    /** One {@code :s} over its range: the state of ex_substitute's loops, so a confirmation can pause them. */
    private static final class Run {
        private final ExCommand cmd;
        private final VimEditor editor;
        private final Ex ex;
        private final Search search;
        private final Buffer b;
        private final VimRegex regex;
        private final String used, sub;
        private final long startNsubs;
        private final boolean endcolumn, saveAll, saveAsk;
        private final Pos oldCursor;
        private int line2, lnum;
        private boolean gotQuit, gotMatch;
        /** Inside a line's loop, and its state (Vim's names). */
        private boolean inLine;
        private int subFirstlnum, copycol, matchcol, prevMatchcol, nmatch, nmatchTl;
        private String subFirstline;
        private StringBuilder newStart;
        private boolean didSub, doAgain, skipMatch;
        private int cursorLine, cursorCol;
        /** The last match: its start and end, lines relative to where the search started. */
        private int startLine, startCol, endLine, endCol;
        private String[] groups = new String[10];
        /** For a pattern that can span lines: the text it is matched against, rebuilt after a change. */
        private String text;
        private int[] lineStarts;
        private int textVersion = -1, version;

        Run(ExCommand cmd, VimRegex regex, String used, String sub, long startNsubs, boolean endcolumn, boolean saveAll, boolean saveAsk) {
            this.cmd = cmd;
            this.editor = cmd.editor;
            this.ex = editor.ex();
            this.search = editor.vim().search();
            this.b = editor.buffer();
            this.regex = regex;
            this.used = used;
            this.sub = sub;
            this.startNsubs = startNsubs;
            this.endcolumn = endcolumn;
            this.saveAll = saveAll;
            this.saveAsk = saveAsk;
            this.oldCursor = editor.cursor();
            this.line2 = cmd.line2;
            this.lnum = cmd.line1;
            this.cursorLine = oldCursor.line + 1;
            this.cursorCol = oldCursor.col;
        }

        void start() { run(null); }

        /** The loops; returns when done, or with the confirmation question open. */
        private void run(KeyInput answer) {
            while (true) {
                if (!inLine) {
                    if (lnum > line2 || gotQuit) {
                        finish();
                        return;
                    }
                    nmatch = exec(lnum, 0);
                    if (nmatch == 0) {
                        lnum++;
                        continue;
                    }
                    subFirstlnum = lnum;
                    copycol = 0;
                    matchcol = 0;
                    prevMatchcol = Integer.MAX_VALUE;
                    newStart = null;
                    didSub = false;
                    nmatchTl = 0;
                    skipMatch = false;
                    subFirstline = null;
                    if (!gotMatch) {
                        editor.setCursor(new Pos(cursorLine - 1, cursorCol));
                        ex.setpcmark();
                        gotMatch = true;
                    }
                    inLine = true;
                }
                Flow flow = iteration(answer);
                answer = null;
                if (flow == Flow.ASK) return;
                if (flow == Flow.BREAK) {
                    if (didSub) ex.subNlines++;
                    inLine = false;
                    lnum++;
                }
            }
        }

        private enum Flow { NEXT, BREAK, ASK }

        /** One pass of the loop over a line's matches: 1. an empty match, 2. counting or asking, 3. substituting, 4. the next match. */
        private Flow iteration(KeyInput answer) {
            boolean toSkip = false;
            if (answer == null) {
                if (startLine > 0) {
                    lnum += startLine;
                    subFirstlnum += startLine;
                    nmatch -= startLine;
                    subFirstline = null;
                }
                if (lnum > b.count()) return Flow.BREAK;
                if (subFirstline == null) subFirstline = b.line(subFirstlnum - 1);
                cursorLine = lnum;
                doAgain = false;
                if (matchcol == prevMatchcol && endLine == 0 && matchcol == endCol) {
                    // An empty match where the last one ended does not count; look one character further.
                    if (matchcol >= subFirstline.length()) skipMatch = true;
                    else matchcol = Text.next(subFirstline, matchcol);
                    toSkip = true;
                } else {
                    matchcol = endCol;
                    prevMatchcol = matchcol;
                    if (search.subCount) {
                        if (nmatch > 1) {
                            matchcol = subFirstline.length();
                            nmatch = 1;
                            skipMatch = true;
                        }
                        ex.subNsubs++;
                        didSub = true;
                        toSkip = true;
                    } else if (search.subAsk) {
                        ask();
                        return Flow.ASK;
                    }
                }
            } else {
                int typed = answer.plainChar();
                if (typed == 'q' || answer.is("<Esc>") || answer.is("<C-c>")) gotQuit = true;
                else if (typed == 'l') {
                    search.subAll = false;
                    line2 = lnum;
                } else if (typed == 'a') search.subAsk = false;
                else if (typed != 'y' && typed != 'n') {
                    if (answer.is("<C-e>")) editor.view().scrollRedraw(true, 1);
                    else if (answer.is("<C-y>")) editor.view().scrollRedraw(false, 1);
                    ask();
                    return Flow.ASK;
                }
                editor.confirmMatch(null);
                if (typed == 'n') {
                    if (nmatch > 1) {
                        matchcol = subFirstline.length();
                        skipMatch = true;
                    }
                    toSkip = true;
                }
                if (gotQuit) toSkip = true;
            }
            if (!toSkip) substituteMatch();
            return skip();
        }

        /**
         * The question, with the match shown and the cursor on it. As in Vim (without 'u' in 'cpoptions'), what was
         * changed before it is its own undo step. The cursor column counts the substitutions already made in the line.
         */
        private void ask() {
            editor.area().vimUndo().sync();
            cursorCol = startCol;
            if (newStart != null) cursorCol += newStart.length() + subFirstline.length() - copycol - b.line(lnum - 1).length();
            editor.setCursor(new Pos(lnum - 1, Math.min(startCol, b.line(lnum - 1).length())));
            int from = b.offset(lnum - 1 + 0, Math.min(startCol, b.line(lnum - 1).length()));
            int endLineIndex = Math.min(b.count() - 1, lnum - 1 + endLine - startLine);
            int to = b.offset(endLineIndex, Math.min(endCol, b.line(endLineIndex).length()));
            editor.confirmMatch(new int[] { from, Math.max(to, from + 1) });
            editor.ask("replace with " + sub + " (y/n/a/q/l/^E/^Y)?", this::run);
        }

        /** Step 3: the substitution goes into the new line; a CR in it breaks the line there. */
        private void substituteMatch() {
            cursorCol = startCol;
            String replacement = regsub(sub, n -> groups[n]);
            if (nmatch > b.count() - subFirstlnum + 1) {
                nmatch = b.count() - subFirstlnum + 1;
                skipMatch = true;
            }
            if (nmatch > 1) nmatchTl += nmatch - 1;
            if (newStart == null) newStart = new StringBuilder();
            newStart.append(subFirstline, Math.min(copycol, subFirstline.length()), Math.min(startCol, subFirstline.length()));
            int newEnd = newStart.length();
            newStart.append(replacement);
            ex.subNsubs++;
            didSub = true;
            cursorCol = 0;
            if (nmatch > 1) {
                subFirstlnum += nmatch - 1;
                subFirstline = b.line(subFirstlnum - 1);
                if (subFirstlnum <= line2) doAgain = true;
                else search.subAll = false;
            }
            copycol = endCol;
            if (skipMatch) {
                subFirstline = "";
                copycol = 0;
            }
            for (int i = newEnd; i < newStart.length(); i++) {
                char c = newStart.charAt(i);
                if (c == '\\' && i + 1 < newStart.length()) newStart.deleteCharAt(i);
                else if (c == CAR) {
                    stepCursor();
                    b.insertLines(lnum - 1, List.of(newStart.substring(0, i)));
                    changed();
                    subFirstlnum++;
                    lnum++;
                    line2++;
                    cursorLine++;
                    newStart.delete(0, i + 1);
                    i = -1;
                }
            }
        }

        /** The "skip" part: decide whether this was the line's last match, and replace the line when it was. */
        private Flow skip() {
            boolean lastone = skipMatch || gotQuit || lnum > line2 || !(search.subAll || doAgain)
                || matchcol >= subFirstline.length() && nmatch <= 1 && !regex.multiline();
            nmatch = -1;
            boolean replace = lastone || nmatchTl > 0;
            if (!replace) {
                nmatch = exec(subFirstlnum, matchcol);
                replace = nmatch == 0 || startLine > 0;
            }
            if (replace) {
                if (newStart != null) {
                    newStart.append(subFirstline.substring(Math.min(copycol, subFirstline.length())));
                    matchcol = subFirstline.length() - matchcol;
                    prevMatchcol = subFirstline.length() - prevMatchcol;
                    stepCursor();
                    b.setLine(lnum - 1, newStart.toString());
                    changed();
                    if (nmatchTl > 0) {
                        b.deleteLines(lnum, lnum + nmatchTl - 1);
                        changed();
                        line2 -= nmatchTl;
                        nmatchTl = 0;
                    }
                    subFirstlnum = lnum;
                    subFirstline = newStart.toString();
                    newStart = null;
                    matchcol = subFirstline.length() - matchcol;
                    prevMatchcol = subFirstline.length() - prevMatchcol;
                    copycol = 0;
                }
                if (nmatch == -1 && !lastone) nmatch = exec(subFirstlnum, matchcol);
                if (nmatch <= 0) {
                    if (nmatch == -1) lnum -= startLine;
                    return Flow.BREAK;
                }
            }
            return Flow.NEXT;
        }

        /** The undo step starts with the cursor where Vim's u_save sees it: on the line, at its start. */
        private void stepCursor() { editor.area().vimUndo().stepCursor(lnum - 1, 0); }

        private void changed() { version++; }

        /**
         * vim_regexec_multi: the first match starting in line {@code line} (1-based) at or after {@code col}.
         *
         * @return how many lines the match spans (its end line minus its start line, plus one), or 0 for none
         */
        private int exec(int line, int col) {
            if (line < 1 || line > b.count()) return 0;
            String lineText = b.line(line - 1);
            if (col > lineText.length()) return 0;
            if (!regex.multiline()) {
                Matcher m = regex.pattern().matcher(lineText + "\n");
                if (!m.find(col) || m.start() > lineText.length()) return 0;
                int s = Math.min(regex.start(m), lineText.length()), e = Math.min(Math.max(regex.end(m), s), lineText.length());
                startLine = endLine = 0;
                startCol = s;
                endCol = e;
                for (int n = 0; n < 10; n++) {
                    int gs = regex.groupStart(m, n), ge = regex.groupEnd(m, n);
                    groups[n] = gs < 0 || ge < 0 ? null : (lineText + "\n").substring(gs, Math.max(gs, Math.min(ge, lineText.length())));
                }
                return 1;
            }
            if (textVersion != version || text == null) {
                String doc = b.text();
                text = doc.endsWith("\n") ? doc : doc + "\n";
                lineStarts = new int[b.count() + 1];
                for (int i = 0; i < b.count(); i++) lineStarts[i] = b.start(i);
                lineStarts[b.count()] = text.length();
                textVersion = version;
            }
            int base = lineStarts[line - 1];
            Matcher m = regex.pattern().matcher(text);
            if (!m.find(base + col) || m.start() > base + lineText.length()) return 0;
            int s = regex.start(m), e = Math.max(regex.end(m), s);
            int[] sp = position(s), ep = position(e);
            startLine = sp[0] - (line - 1);
            startCol = sp[1];
            endLine = ep[0] - (line - 1);
            endCol = ep[1];
            for (int n = 0; n < 10; n++) {
                int gs = regex.groupStart(m, n), ge = regex.groupEnd(m, n);
                groups[n] = gs < 0 || ge < 0 ? null : text.substring(gs, Math.max(gs, ge));
            }
            return endLine + 1;
        }

        /** An offset in {@link #text} as {line (0-based), column}; past the last line is the line after it. */
        private int[] position(int offset) {
            int count = lineStarts.length - 1;
            int lo = 0, hi = count;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (lineStarts[mid] <= offset) lo = mid;
                else hi = mid - 1;
            }
            if (lo >= count) return new int[] { count, 0 };
            return new int[] { lo, offset - lineStarts[lo] };
        }

        /** The end of ex_substitute: the cursor, the {@code '[ ']} marks, the message, and printing. */
        private void finish() {
            editor.confirmMatch(null);
            Pos cursor = new Pos(Math.max(0, Math.min(cursorLine, b.count()) - 1), cursorCol);
            if (search.subCount) cursor = oldCursor;
            editor.setCursor(cursor);
            if (ex.subNsubs > startNsubs) {
                Marks marks = editor.marks();
                marks.set('[', new Pos(cmd.line1 - 1, 0));
                marks.set(']', new Pos(Math.max(0, Math.min(line2, b.count()) - 1), 0));
                if (!ex.globalBusy()) {
                    if (!search.subAsk) {
                        if (endcolumn) {
                            String line = b.line(editor.cursor().line);
                            editor.setCursor(new Pos(editor.cursor().line, line.isEmpty() ? 0 : Text.last(line)));
                            editor.curswant(Integer.MAX_VALUE);
                        } else editor.ex().beginline(editor.cursor().line + 1, false);
                    }
                    message(editor, search.subCount);
                } else ex.globalNeedBeginline = true;
                if (search.subPrint)
                    editor.say(ExInfo.line(editor, editor.cursor().line + 1, search.subNumber, search.subList));
            } else if (!ex.globalBusy() && !gotMatch && search.subError) {
                search.subAll = saveAll;
                search.subAsk = saveAsk;
                throw new VimFailure("E486: Pattern not found: " + used);
            }
            search.subAll = saveAll;
            search.subAsk = saveAsk;
        }
    }
}
```

- [ ] **Step 4: Register the commands**

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, replace:

```java
        def("center", TRLBAR | RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "center the lines in [width] ('textwidth', or 80)", ExLines::align);
```

with:

```java
        def("center", TRLBAR | RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "center the lines in [width] ('textwidth', or 80)", ExLines::align);
        def("substitute", RANGE | WHOLEFOLD | EXTRA, Addr.LINES,
            "`:s/pattern/string/flags [count]`: replace matches; flags `& c e g i I n p # l r`", ExSubstitute::substitute);
        def("&", RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "repeat the last `:s` with the same pattern and string; `:&&` keeps its flags", ExSubstitute::substitute);
        def("~", RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "repeat the last `:s` with the last used pattern", ExSubstitute::substitute);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, replace:

```java
        commands.visual(":", "a command line for the Visual lines (`:'<,'>`)", false, false, c -> c.editor.ex().open(c));
```

with:

```java
        commands.visual(":", "a command line for the Visual lines (`:'<,'>`)", false, false, c -> c.editor.ex().open(c));
        commands.action("&", "repeat the last `:s` on the line, without its flags", false, c -> {
            String line = (c.hasCount ? c.count > 1 ? ".,.+" + (c.count - 1) : "." : "") + "s";
            c.editor.ex().execute(line);
            c.editor.vim().registers().lastCommand(line);
        });
        commands.action("g&", "repeat the last `:s` on every line, with its flags (`:%s//~/&`)", false,
            c -> c.editor.ex().execute("%s//~/&"));
```

- [ ] **Step 5: List the divergent case and regenerate the reference**

In `docs/vim-divergences.md`, replace:

```markdown
| ex-range.case | a Vim command Jasper lacks | Jasper has no abbreviations; a command it lacks fails with E319 (docs/vim-divergences.md) |
```

with:

```markdown
| ex-range.case | a Vim command Jasper lacks | Jasper has no abbreviations; a command it lacks fails with E319 (docs/vim-divergences.md) |
| ex-substitute.case | s expression is not available | Jasper has no expressions: \= in a substitute string fails with E319 (docs/vim-divergences.md) |
```

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains `&`, `g&`, `:s[ubstitute]`, `:&` and `:~`.

- [ ] **Step 6: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 151 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1526 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`.

- [ ] **Step 7: Check the recording (needs `vim` on `PATH`)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: `git status` lists only `ex-substitute.case` (`??`), unchanged from what you created.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v2a
git add plugins/editor/src/main/java/dev/jasper/editor/vim plugins/editor/src/test/java/dev/jasper/editor/vim plugins/editor/src/test/resources/vim/corpus/ex-substitute.case docs/vim-divergences.md docs/editor-vim-reference.md
git commit -m "Add :s with confirmation, :&, :~, & and g&

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 5: `:g`, `:g!` and `:v`

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/ExGlobal.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`
- Create: `plugins/editor/src/test/resources/vim/corpus/ex-global.case`
- Regenerate: `docs/editor-vim-reference.md`

**Interfaces:**
- Consumes: Task 3's `Ex.runLine`, `later`, `globalBusy`, `setpcmark`, `beginline`, fields `subNsubs`, `subNlines`, `globalNeedBeginline`; Task 1's `Marks.markLines`, `firstMarked`, `clearMarked`; Task 4's `ExSubstitute.message`.

The first pass marks the matching lines (`Marks.markLines`: the marks move with the text and go with deleted lines, as Vim's ml_setmarked flags do); the second pass (`Pass`, a port of global_exe) runs the command on each marked line in turn, with `Ex.globalBusy` set so `:s` and the line commands stay quiet, setpcmark does nothing and relative ranges are not widened by folds. A question inside (`:g/a/s//x/c`) pauses the pass; it continues from the answer. At the end it reports the substitutions, or the lines added or deleted.

- [ ] **Step 1: Write the failing cases**

Create `plugins/editor/src/test/resources/vim/corpus/ex-global.case`:

```text
# Ex :global and :vglobal (V2a).

=== g deletes matching lines
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/d<CR>
output: yes
--- vim 9.1
expect-text:
  b2
  b4
expect-cursor: 2,1
expect-mode: normal
expect-output:
  3 fewer lines

=== g bang keeps matching lines
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g!/a/d<CR>
--- vim 9.1
expect-text:
  a1
  a3
  a5
expect-cursor: 3,1
expect-mode: normal

=== v deletes other lines
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :v/a/d<CR>
--- vim 9.1
expect-text:
  a1
  a3
  a5
expect-cursor: 3,1
expect-mode: normal

=== g with a range
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :2,4g/a/d<CR>
--- vim 9.1
expect-text:
  a1
  b2
  b4
  a5
expect-cursor: 3,1
expect-mode: normal

=== g prints by default
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/b<CR>
output: yes
--- vim 9.1
expect-text:
  a1
  b2
  a3
  b4
  a5
expect-cursor: 4,1
expect-mode: normal
expect-output:
  b2
  b4

=== g with p
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/p<CR>
output: yes
--- vim 9.1
expect-text:
  a1
  b2
  a3
  b4
  a5
expect-cursor: 5,1
expect-mode: normal
expect-output:
  a1
  a3
  a5

=== g with number
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/#<CR>
output: yes
--- vim 9.1
expect-text:
  a1
  b2
  a3
  b4
  a5
expect-cursor: 5,1
expect-mode: normal
expect-output:
    1 a1
    3 a3
    5 a5

=== g not found
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/z/d<CR>
output: yes
--- vim 9.1
expect-text:
  a1
  b2
  a3
  b4
  a5
expect-cursor: 1,1
expect-mode: normal
expect-output:
  Pattern not found: z

=== v found everywhere
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :v/./d<CR>
output: yes
--- vim 9.1
expect-text:
  a1
  b2
  a3
  b4
  a5
expect-cursor: 1,1
expect-mode: normal
expect-output:
  Pattern found in every line: .

=== g missing pattern
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g<CR>
output: yes
--- vim 9.1
expect-text:
  a1
  b2
  a3
  b4
  a5
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E148: Regular expression missing from :global

=== g letter delimiter
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :gxaxd<CR>
output: yes
--- vim 9.1
expect-text:
  a1
  b2
  a3
  b4
  a5
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E492: Not an editor command: gxaxd

=== g other delimiter
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g#a#d<CR>
--- vim 9.1
expect-text:
  b2
  b4
expect-cursor: 2,1
expect-mode: normal

=== g empty pattern uses the last search
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: /b<CR>:g//d<CR>
--- vim 9.1
expect-text:
  a1
  a3
  a5
expect-cursor: 3,1
expect-mode: normal

=== g sets the search pattern
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/b/s/b/B/<CR>n
--- vim 9.1
expect-text:
  a1
  B2
  a3
  B4
  a5
expect-cursor: 4,1
expect-mode: normal

=== g with s
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/s/a/X/<CR>
output: yes
--- vim 9.1
expect-text:
  X1
  b2
  X3
  b4
  X5
expect-cursor: 5,1
expect-mode: normal
expect-output:
  3 substitutions on 3 lines

=== g with s reports the total
text:
  a a
  b
  a a a
cursor: 1,1
keys: :g/a/s/a/X/g<CR>
output: yes
--- vim 9.1
expect-text:
  X X
  b
  X X X
expect-cursor: 3,1
expect-mode: normal
expect-output:
  5 substitutions on 2 lines

=== g with s and an empty pattern
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/s//X/<CR>
--- vim 9.1
expect-text:
  X1
  b2
  X3
  b4
  X5
expect-cursor: 5,1
expect-mode: normal

=== g with normal
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/norm Ax<CR>
--- vim 9.1
expect-text:
  a1x
  b2
  a3x
  b4
  a5x
expect-cursor: 5,3
expect-mode: normal

=== g with normal then undo
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/norm Ax<CR>u
--- vim 9.1
expect-text:
  a1
  b2
  a3
  b4
  a5
expect-cursor: 1,2
expect-mode: normal

=== g move reverses the lines
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/^/m0<CR>
--- vim 9.1
expect-text:
  a5
  b4
  a3
  b2
  a1
expect-cursor: 1,1
expect-mode: normal

=== g copy duplicates
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/b/t.<CR>
--- vim 9.1
expect-text:
  a1
  b2
  b2
  a3
  b4
  b4
  a5
expect-cursor: 6,1
expect-mode: normal

=== g join
text:
  a
  1
  a
  2
cursor: 1,1
keys: :g/a/j<CR>
--- vim 9.1
expect-text:
  a 1
  a 2
expect-cursor: 2,1
expect-mode: normal

=== g delete next line
text:
  x
  y
  x
  y
cursor: 1,1
keys: :g/x/+1d<CR>
--- vim 9.1
expect-text:
  x
  x
expect-cursor: 2,1
expect-mode: normal

=== g delete that removes marked lines
text:
  x
  x
  x
  y
cursor: 1,1
keys: :g/x/.,+1d<CR>
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal

=== g with a bar in the command
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/s/a/X/|s/X/Y/<CR>
--- vim 9.1
expect-text:
  Y1
  b2
  Y3
  b4
  Y5
expect-cursor: 5,1
expect-mode: normal

=== g cursor ends on the last line it ran on
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/b/<CR>
--- vim 9.1
expect-text:
  a1
  b2
  a3
  b4
  a5
expect-cursor: 4,1
expect-mode: normal

=== g with s puts the cursor on the first non-blank
text:
    a
    b
    a
cursor: 1,1
keys: :g/a/s/a/X/<CR>
--- vim 9.1
expect-text:
    X
    b
    X
expect-cursor: 3,3
expect-mode: normal

=== g sets the jumplist
text:
  a1
  b2
  a3
  b4
  a5
cursor: 5,1
keys: :g/a/s/a/X/<CR><C-o>
--- vim 9.1
expect-text:
  X1
  b2
  X3
  b4
  X5
expect-cursor: 5,1
expect-mode: normal

=== g reports fewer lines
text:
  a
  a
  a
  b
cursor: 1,1
keys: :g/a/d<CR>
output: yes
--- vim 9.1
expect-text:
  b
expect-cursor: 1,1
expect-mode: normal
expect-output:
  3 fewer lines

=== g nested
text:
  ab
  a
  b
  abc
cursor: 1,1
keys: :g/a/v/b/d<CR>
--- vim 9.1
expect-text:
  ab
  b
  abc
expect-cursor: 3,1
expect-mode: normal

=== g nested with a range fails
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/2,3g/b/d<CR>
output: yes
--- vim 9.1
expect-text:
  a1
  b2
  a3
  b4
  a5
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E147: Cannot do :global recursive with a range

=== g backslash slash
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: /b<CR>:g\/d<CR>
--- vim 9.1
expect-text:
  a1
  a3
  a5
expect-cursor: 3,1
expect-mode: normal

=== g backslash ampersand
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :s/a/a/<CR>:g\&d<CR>
--- vim 9.1
expect-text:
  b2
  b4
expect-cursor: 2,1
expect-mode: normal

=== g error stops it
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/foo<CR>
output: yes
--- vim 9.1
expect-text:
  a1
  b2
  a3
  b4
  a5
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E492: Not an editor command: foo

=== g with a failing normal goes on
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/norm 3lx<CR>
--- vim 9.1
expect-text:
  a
  b2
  a
  b4
  a
expect-cursor: 5,1
expect-mode: normal

=== g with confirm
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/s/a/X/c<CR>yny
--- vim 9.1
expect-text:
  X1
  b2
  a3
  b4
  X5
expect-cursor: 5,1
expect-mode: normal

=== g with confirm then quit
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/s/a/X/c<CR>yqq
--- vim 9.1
expect-text:
  X1
  b2
  a3
  b4
  a5
expect-cursor: 5,1
expect-mode: normal

=== g with confirm stops on q and goes on
text:
  a
  a
  a
cursor: 1,1
keys: :g/a/s/a/X/c<CR>ynq
output: yes
--- vim 9.1
expect-text:
  X
  a
  a
expect-cursor: 3,1
expect-mode: normal
expect-output:
  replace with X (y/n/a/q/l/^E/^Y)?
  replace with X (y/n/a/q/l/^E/^Y)?
  replace with X (y/n/a/q/l/^E/^Y)?

=== g on the visual lines
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: Vjj:g/a/d<CR>
--- vim 9.1
expect-text:
  b2
  b4
  a5
expect-cursor: 2,1
expect-mode: normal

=== g dot
text:
  a1
  b2
  a3
  b4
  a5
cursor: 3,1
keys: :.g/a/d<CR>
--- vim 9.1
expect-text:
  a1
  b2
  b4
  a5
expect-cursor: 3,1
expect-mode: normal

=== g with k marks
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/b/k x<CR>'x
--- vim 9.1
expect-text:
  a1
  b2
  a3
  b4
  a5
expect-cursor: 4,1
expect-mode: normal

=== g then undo is one step
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/d<CR>u
--- vim 9.1
expect-text:
  a1
  b2
  a3
  b4
  a5
expect-cursor: 1,1
expect-mode: normal

=== g with d into a register appends
text:
  a1
  b2
  a3
  b4
  a5
cursor: 1,1
keys: :g/a/d A<CR>
registers: a
--- vim 9.1
expect-text:
  b2
  b4
expect-cursor: 2,1
expect-mode: normal
expect-register a: l "a1\na3\na5\n"
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest'
```

Expected: the `ex-global.case` cases fail: `:g` is not an editor command yet (E319).

- [ ] **Step 3: Write `ExGlobal` and register the commands**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ExGlobal.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * {@code :g/pat/cmd}, {@code :g!} and {@code :v}: a port of Vim's ex_global and global_exe (ex_cmds.c). The first
 * pass marks the lines that match (or do not); the second runs the command on each marked line in turn, the marks
 * moving with the text and going with deleted lines. The command defaults to {@code :p}.
 */
final class ExGlobal {
    private ExGlobal() { }

    static void global(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Ex ex = editor.ex();
        Buffer b = editor.buffer();
        if (ex.globalBusy() && (cmd.line1 != 1 || cmd.line2 != b.count()))
            throw new VimFailure("E147: Cannot do :global recursive with a range");
        char type = cmd.forceit ? 'v' : cmd.typed.charAt(0);
        String arg = cmd.arg;
        int whichPat = -1;
        String pat;
        int i;
        if (arg.startsWith("\\")) {
            char which = arg.length() > 1 ? arg.charAt(1) : 0;
            if (which == 0 || "/?&".indexOf(which) < 0) throw new VimFailure("E10: \\ should be followed by /, ? or &");
            whichPat = which == '&' ? Search.SUBSTITUTE : Search.SEARCH;
            pat = "";
            i = 2;
        } else if (arg.isEmpty()) throw new VimFailure("E148: Regular expression missing from :global");
        else if (arg.charAt(0) < 128 && Character.isLetter(arg.charAt(0))) throw new VimFailure("E146: Regular expressions can't be delimited by letters");
        else {
            char delim = arg.charAt(0);
            int end = 1 + Search.skipPattern(arg.substring(1), delim);
            pat = arg.substring(1, end);
            i = end < arg.length() ? end + 1 : end;
        }
        String command = arg.substring(Math.min(i, arg.length()));
        Search search = editor.vim().search();
        String used;
        VimRegex regex;
        try {
            used = search.choose(pat, -1, whichPat);
            Options o = editor.options();
            regex = VimRegex.compile(used, new VimRegex.Settings(o.flag("ignorecase"), o.flag("smartcase"), o.keywords().javaClass(), search.prevSub));
        } catch (VimFailure | IllegalArgumentException bad) {
            editor.message(bad.getMessage(), true);
            throw new VimFailure("E476: Invalid command");
        }
        if (ex.globalBusy()) {
            // Nested (:g/a/v/b/cmd): only the current line.
            int lnum = editor.cursor().line;
            if (matches(regex, b, lnum) == (type == 'g')) one(editor, command, lnum);
            return;
        }
        List<Integer> marked = new ArrayList<>();
        String text = regex.multiline() ? b.text() + (b.text().endsWith("\n") ? "" : "\n") : null;
        for (int lnum = cmd.line1 - 1; lnum <= cmd.line2 - 1; lnum++) {
            boolean match = text != null ? matchesIn(regex, b, text, lnum) : matches(regex, b, lnum);
            if (match == (type == 'g')) marked.add(lnum);
        }
        if (marked.isEmpty()) {
            editor.say((type == 'v' ? "Pattern found in every line: " : "Pattern not found: ") + used);
            return;
        }
        editor.marks().markLines(marked);
        new Pass(editor, command).start();
    }

    private static boolean matches(VimRegex regex, Buffer b, int lnum) {
        String line = b.line(lnum);
        Matcher m = regex.pattern().matcher(line + "\n");
        return m.find() && m.start() <= line.length();
    }

    private static boolean matchesIn(VimRegex regex, Buffer b, String text, int lnum) {
        Matcher m = regex.pattern().matcher(text);
        int start = b.start(lnum);
        return m.find(start) && m.start() <= start + b.line(lnum).length();
    }

    /** global_exe_one: the command on one line, from its start; an empty command prints it. */
    private static boolean one(VimEditor editor, String command, int lnum) {
        editor.setCursor(new Pos(lnum, 0));
        return editor.ex().runLine(command.isEmpty() ? "p" : command);
    }

    /** global_exe: the second pass, which a confirmation inside the command can pause. */
    private static final class Pass {
        private final VimEditor editor;
        private final Ex ex;
        private final String command;
        private int oldCount;

        Pass(VimEditor editor, String command) {
            this.editor = editor;
            this.ex = editor.ex();
            this.command = command;
        }

        void start() {
            ex.setpcmark();
            ex.subNsubs = 0;
            ex.subNlines = 0;
            ex.globalNeedBeginline = false;
            ex.globalBusy(true);
            oldCount = editor.buffer().count();
            loop();
        }

        private void loop() {
            ex.globalBusy(true);
            while (true) {
                int lnum = editor.marks().firstMarked();
                if (lnum < 0) break;
                if (!one(editor, command, lnum)) break;
                if (editor.prompting()) {
                    ex.later(this::loop);
                    return;
                }
            }
            end();
        }

        private void end() {
            ex.globalBusy(false);
            editor.marks().clearMarked();
            if (ex.globalNeedBeginline) ex.beginline(editor.cursor().line + 1, false);
            else editor.setCursor(editor.cursor());
            if (!ExSubstitute.message(editor, false)) editor.msgmore(editor.buffer().count() - oldCount);
        }
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, replace:

```java
        def("~", RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "repeat the last `:s` with the last used pattern", ExSubstitute::substitute);
```

with:

```java
        def("~", RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "repeat the last `:s` with the last used pattern", ExSubstitute::substitute);
        def("global", RANGE | WHOLEFOLD | BANG | EXTRA | DFLALL, Addr.LINES, "`:g/pattern/cmd`: run cmd (default `:p`) on the matching lines; `!` on the others", ExGlobal::global);
        def("vglobal", RANGE | WHOLEFOLD | EXTRA | DFLALL, Addr.LINES, "`:v/pattern/cmd`: run cmd on the lines that do not match", ExGlobal::global);
```

- [ ] **Step 4: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains `:g[lobal]` and `:v[global]`.

- [ ] **Step 5: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 151 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1569 tests pass.

- [ ] **Step 6: Check the recording (needs `vim` on `PATH`)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: `git status` lists only `ex-global.case` (`??`), unchanged from what you created.

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/vim-v2a
git add plugins/editor/src/main/java/dev/jasper/editor/vim plugins/editor/src/test/resources/vim/corpus/ex-global.case docs/editor-vim-reference.md
git commit -m "Add :g, :g! and :v

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 6: Documentation, the spec amendments and the final check

**Files:**
- Modify: `docs/vim-divergences.md`, `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec

- [ ] **Step 1: List the differences**

In `docs/vim-divergences.md`, replace:

```markdown
- **`go` counts characters.** Vim counts bytes.
```

with:

```markdown
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
```

- [ ] **Step 2: Describe the command line in the Editor guide**

In `docs/editor.md`, replace:

```markdown
- 'tabstop' and 'shiftwidth' start at `tab_size`, 'expandtab' follows the tab's Spaces or Tab setting, and
  'autoindent' is on. 'incsearch' is on; 'hlsearch' is off until the `:` command line (with `:noh`) arrives.
```

with:

```markdown
- 'tabstop' and 'shiftwidth' start at `tab_size`, 'expandtab' follows the tab's Spaces or Tab setting, and
  'autoindent' is on. 'incsearch' and 'hlsearch' are on; `:noh` hides the highlight until the next search.
- `:` opens the command line in the strip, with the same keys as `/` and its own history. It runs Vim's Ex commands
  for editing, with Vim's ranges (`:3,5`, `:%`, `:'<,'>` after Visual mode, `:/pat/`, `:.,+3`): `:s` with every flag,
  including `c`, which asks in the strip; `:g` and `:v`; `:d`, `:y`, `:m`, `:t`, `:j`, `:>`, `:pu`, `:norm`, `:sort`,
  `:left`, `:right` and `:center`; `:marks`, `:reg`, `:jumps` and `:changes`; `:u`, `:noh` and `:set`. The
  [reference](editor-vim-reference.md#ex-commands) lists them. When a command line says several lines (`:reg`,
  `:g/pat/`) they stay above the strip until you type a key; `:` goes straight on to the next command line.
```

In `docs/editor.md`, replace:

```markdown
- Not yet: the `:` command line, macros, a vimrc, and emulated plugins such as surround and commentary. They follow in
  later releases. An uppercase mark (`mA`) works within its own tab.
```

with:

```markdown
- Not yet: file, buffer and tab commands (`:w`, `:q`, `:e`, `ZZ`), Tab completion on the command line, relative line
  numbers, macros, a vimrc, and emulated plugins such as surround and commentary. They follow in later releases. An
  uppercase mark (`mA`) works within its own tab.
```

- [ ] **Step 3: Add the GUI checks for the user**

Append to `docs/editor-verification.md`:

```markdown

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
```

- [ ] **Step 4: Record the state**

In `docs/STATUS.md`, replace:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V1c** (branch `claude/vim-v1c`, plan
```

with:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V2a** (branch `claude/vim-v2a`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v2a.md`, written 2026-09-28): the Ex command line — ranges (`;`, searches, marks, folds, `*`), `|`, Vim's 600
  command names for abbreviations (E319 for those Jasper lacks), `:s` with every flag including confirmation, `:&`,
  `:&&`, `:~`, `&`, `g&`, `:g`/`:g!`/`:v`, `:d :y :> :< :pu :j :m :t :co :norm :sort :left :right :center`,
  `:p :P :l :nu :# :=`, `:k :mark :delm :marks :jumps :changes :reg :di`, `:u :red :noh`, `:set`/`:setlocal`/
  `:setglobal` with do_set's syntax, the output panel (Vim's hit-enter prompt), questions that pause a command,
  'report' messages, 'hlsearch' on and 'gdefault'. The corpus compares output (`output: yes`). Every file in the plan
  was compiled and run against Vim 9.1 before the plan was written: 1565 corpus cases
  (1560 recorded, 5 divergent).
  - Not done: execution; GUI checks 51–60 of the [checklist](editor-verification.md) (user-run); plans V2b, V3 and
    V4; push.
- **Editor Vim mode, plan V1c** (branch `claude/vim-v1c`, plan
```

- [ ] **Step 5: Amend the spec**

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
amends sections 3.1, 4.1 and 11.1, as marked **Amended (V1c)**.
```

with:

```markdown
amends sections 3.1, 4.1 and 11.1, as marked **Amended (V1c)**. Plan V2a (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v2a.md`) amends sections 3.8, 5.2, 6.1, 6.2, 6.3, 11.1, 12 and 13, as marked **Amended (V2a)**.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
**Amended (V1b):** `hlsearch` starts off until plan V2 adds `:noh` and `:set` to turn the highlight off.
```

with:

```markdown
**Amended (V1b):** `hlsearch` starts off until plan V2 adds `:noh` and `:set` to turn the highlight off. **Amended (V2a):** `hlsearch` is on, as this paragraph says; `:noh` hides the highlight until the next search.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- Multi-line output (`:reg`, `:marks`, `:ls`, `:jumps`, `:changes`, `:set all`) expands the bar upward into a scrollable read-only panel dismissed by any key, like Vim's more-prompt.
```

with:

```markdown
- Multi-line output (`:reg`, `:marks`, `:ls`, `:jumps`, `:changes`, `:set all`) expands the bar upward into a scrollable read-only panel dismissed by any key, like Vim's more-prompt. **Amended (V2a):** the panel shows any command line's output of more than one line, errors in the error colour, and behaves as Vim's hit-enter prompt: `<CR>`, `<Space>` and `<Esc>` only close it, and any other key closes it and runs (`:` starts the next command line). After a failed command line only the error stays in the strip, as Vim does not wait then. The strip shows questions (`replace with X (y/n/a/q/l/^E/^Y)?`) and "Press ENTER or type command to continue". `:ls` comes with plan V2b.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
`<Tab>`/`<S-Tab>` complete command names, option names after `:set`, and paths after `:e`, `:w`, `:r`, `:source`;
```

with:

```markdown
`<Tab>`/`<S-Tab>` complete command names, option names after `:set`, and paths after `:e`, `:w`, `:r`, `:source` (**Amended (V2a):** completion comes with plan V2b; until then `<Tab>` types a tab);
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
The `c` flag asks in the Vim bar with the current match highlighted: `replace with X (y/n/a/q/l/^E/^Y)?`.
```

with:

```markdown
The `c` flag asks in the Vim bar with the current match highlighted: `replace with X (y/n/a/q/l/^E/^Y)?`.

**Amended (V2a):** files and tabs, `ZZ` and `ZQ` move to plan V2b. Every other Ex command name Vim knows and Jasper does not run fails with `E319: Sorry, the command is not available in this version`, as does `\=` in a substitute string (there is no Vim script); a name Vim does not know fails with `E492`. `:undo N` reaches the changes on Jasper's linear history (`E830` otherwise). The 'report' messages ("3 fewer lines", "4 lines yanked", "3 lines >ed 1 time", "3 substitutions on 2 lines") are given, also after Normal-mode commands. The `c` flag's question highlights the match in the line as it stood; the line changes when it is done, as in Vim. A command that asks pauses and continues from the answer, with the rest of its line and `:g`'s remaining lines; `:normal` answers from its keys, or with `<Esc>` when they run out.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
Unknown options fail with `E518: Unknown option`.
```

with:

```markdown
Unknown options fail with `E518: Unknown option`. **Amended (V2a):** V2a adds `gdefault`; `number` and `relativenumber` come with the gutter in V2b; `timeout`, `timeoutlen`, `undolevels`, `visualbell` and the accepted-and-ignored vimrc options come with the vimrc in V3. `:set` and `:set all` list only the options Jasper has, in Vim's layout; `:setlocal` and `:setglobal` read and write the local and global values.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
`expect-view:` (`topline,leftcol`) and `expect-folds:` (the closed folds) are then compared.
```

with:

```markdown
`expect-view:` (`topline,leftcol`) and `expect-folds:` (the closed folds) are then compared. **Amended (V2a):** `output: yes` makes a case compare what its keys said: the recorder captures Vim's messages with `:redir`, dropping empty lines and the script-error headers of a sourced script, into `expect-output:`. The recorder types keys with `feedkeys()`, where Vim never waits at the hit-enter prompt, so unit tests cover the output panel.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
No SDK change is needed.
```

with:

```markdown
**Amended (V2a):** the Ex classes (`Ex`, `ExTable`, `ExCommand`, `ExInfo`, `ExLines`, `ExSubstitute`, `ExGlobal`) live in `dev.jasper.editor.vim`, not `vim.ex`: they use the engine's package-private API, and a separate package would need a public API or a dependency cycle. `vim.ui` depends on `vim` only.

No SDK change is needed.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- **Plan V2: ex command line.** Section 6, the output panel and the relative-number gutter; `ZZ` and `ZQ`.
```

with:

```markdown
- **Plan V2: ex command line.** Section 6, the output panel and the relative-number gutter; `ZZ` and `ZQ`. **Amended (V2a):** split in two by the user on 2026-09-28. Plan V2a: the command line, ranges, the line, substitute, global, mark, register and option commands, `:u`, `:red`, `:noh`, the output panel and 'hlsearch' on. Plan V2b: files, buffers and tabs through the Editor (`:w :q :e :b :ls :tab…`, `ZZ`, `ZQ`), Tab completion, and the relative-number gutter.
```

- [ ] **Step 6: Run everything**

Run:

```bash
./gradlew check
```

Expected: BUILD SUCCESSFUL (4,068 tests, 0 failures, 0 errors, 5 skipped).

- [ ] **Step 7: Check source hygiene**

Run the snippet in `AGENTS.md` ("Source hygiene"). Expected: no output.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v2a
git add docs
git commit -m "Document Vim mode V2a: the Ex command line

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
