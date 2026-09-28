# Editor Vim mode, plan V1b: Visual mode, search and marks — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Visual mode (charwise, linewise and blockwise), search with `/ ? n N * #` and a Vim-to-Java regex translator, marks with the jumplist and change list, `<C-a>`/`<C-x>`, `gq`/`gw`, `gi`, `<C-g>`, `ga`, blockwise registers and blockwise forced motions, with the highlights and the search command line they need, all proven against real Vim 9 by the differential corpus.

**Architecture:** Everything builds on plan V1a's engine in `dev.jasper.editor.vim`. `Visual` turns a Visual area into an operator's `Span` as Vim's do_pending_operator does (with `Block`, a port of block_prep, for display columns) and `Put` ports do_put; `Machine` gains a Visual-mode branch and a command line for `/` and `?` (`CommandLine`). `Search` ports searchit and do_search over `VimRegex` (new package `dev.jasper.editor.vim.regex`, pure JDK), which lexes Vim patterns the way regexp.c's peekchr does and writes `java.util.regex` syntax. `Marks` moves marks with the text as mark_adjust does and keeps the jumplist and change list; `Numbers` ports do_addsub; `Format` ports format_lines and internal_format with comment leaders. `Highlights` paints the Visual area and search matches; the Vim bar draws the command line.

**Tech Stack:** Java 25 (JBR), Swing, RSyntaxTextArea 3.6.0, JUnit 6 and AssertJ, Vim 9 (only for `recordVimCorpus`).

**Spec:** `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md` (plan V1b of section 13, sections 3.5–3.8, 4.1, 5.2, 5.3, 11). Plan V1a (`docs/superpowers/plans/2026-09-27-jasper-editor-vim-v1a.md`) is merged; read its header for the engine's shape.

**Status:** Written 2026-09-28 on branch `claude/vim-v1b` (worktree `.worktrees/vim-v1b`, from `main` at 16ec0f86). Every source file, test and corpus case below was compiled and run before the plan was written: against Vim 9.1, all 946 corpus cases (477 new: visual 113, visual-block 55, search 80, regex 58, marks 73, numbers 50, format 48) passed, three of them divergent, and every command in the table has a case. Applied to a fresh checkout of `16ec0f86` exactly as written, `./gradlew check` passed (3,372 tests, 0 failed, 5 skipped). **Executed 2026-09-28:** the marks case "backtick backtick twice toggles" first had the keys `G` followed by four backticks; a run of three backticks inside this plan broke `AppDocumentationTest`'s link check (it strips fenced blocks with a lazy pattern), so its keys became `G`, two backticks, `0`, two backticks, and it was re-recorded (Vim's expectation is unchanged).

## Deviations from the spec (approved scope, adjusted in this plan)

Task 5 writes these into the spec as **Amended (V1b)**.

- **Split into V1b and V1c** (the user's choice on 2026-09-28): scrolling (`<C-d> <C-u> <C-f> <C-b> <C-e> <C-y> zz zt zb`), `H M L` and the other screen-line motions, and folds move to a new **plan V1c**. They need the view's layout, which nothing else here does.
- **'hlsearch' starts off** until plan V2 adds `:noh` and `:set`: with it on and no way to turn the highlight off, every search would leave the text covered. 'incsearch' starts on as the spec says.
- **Uppercase marks work within their own tab.** Jumping to a mark in another tab (and opening its document) needs plan V2's buffer commands and a host call.
- **All three Visual modes are drawn by a highlighter** in the theme's selection colour; the text area's own selection stays empty, so the caret stays on the Vim cursor (spec 5.3 had charwise and linewise use the text area's selection).
- **The command line is drawn by the Vim bar** while keys keep going through the text area, which gives every key to the command line (spec 5.2 described an editable field taking focus). `:` arrives in plan V2 on the same `CommandLine`.
- **Corpus format:** `expect-visual:` (the other end of a Visual area) and blockwise register types written as `b` plus the width (`b3`, Vim's `getregtype()` in letters); the mode id `command-line` is Vim's `c`. Vim's setreg() does not move the unnamed register, so `Registers.set` no longer does either.
- **Vim facts the corpus recorded**, followed here: `<C-c>` ends a block insert or change and still copies the text to the other lines (when keys are fed, as when typed without an interrupt); `.` after a Visual operator repeats it on an area of the same size, and a count typed before `.` is ignored; `*` sets the previous-context mark before moving to the word's start; a mark on a line joined by `J` moves onto the joined line.
- **`ga` shows no digraph** (Jasper has no digraph table), and **the `'.` mark takes each typed character's column** where Vim fed a whole string at once records the insert's start; both are listed in `docs/vim-divergences.md`.

## Global Constraints

- Java 25 on the JetBrains Runtime; always `./gradlew`, never a system Gradle. Work in `.worktrees/vim-v1b` on `claude/vim-v1b`; check `git branch --show-current` before every commit (other sessions switch branches in the main checkout). Never commit on `main`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.
- The Editor plugin compiles against the SDK only (`verifyPluginArchitecture`). `dev.jasper.editor.vim.regex` may use only the JDK; `dev.jasper.editor.vim` adds `vim.regex` and RSyntaxTextArea; `dev.jasper.editor.vim.ui` adds `dev.jasper.editor.vim`. No SDK change.
- No interface without two real implementations; this plan adds none.
- Source hygiene: never put raw control, private-use or unpaired surrogate characters in `.java` files; use escapes (`"\u0016"`, `(char) 0x16`). Corpus `.case` files are UTF-8 resources and may hold any text.
- **Do not launch the GUI.** GUI checks go in `docs/editor-verification.md` for the user.
- Engine code runs on the event thread in the app. Tests that type through a `VimEditor` run inside `SwingUtilities.invokeAndWait`.
- The corpus is the authority on Vim behaviour. If a recorded case and the engine disagree, fix the engine; mark a case `divergent:` only for a documented, deliberate difference.
- `vim` must be on `PATH` for `recordVimCorpus` (the dev Mac has Vim 9.1 at `/usr/bin/vim`). `check` never runs Vim.
- The ports keep Vim's control flow and names in comments (`do_put`, `block_prep`, `searchit`); keep them as written when a reviewer suggests "simplifying" — the corpus is what proves them.

## File structure

New, `plugins/editor/src/main/java/dev/jasper/editor/vim/regex/`: `VimRegex.java` (Vim pattern to `java.util.regex`), `package-info.java`.

New, `plugins/editor/src/main/java/dev/jasper/editor/vim/`:

| File | Responsibility |
|---|---|
| `Block.java` | one line's part of a block of display columns (block_prep) |
| `Put.java` | putting a register: charwise, linewise, blockwise, over a Visual area (do_put) |
| `Marks.java` | marks that move with the text, the jumplist, the change list, `'.`, the previous context |
| `Visual.java` | Visual mode: starting, switching, `o`/`O`, `gv`, the area as an operator's span, `p`/`P`, `.` on an area |
| `Search.java` | `/ ? n N * # g* g#`, offsets, `;`, wrapping, the 'hlsearch' and 'incsearch' matches |
| `CommandLine.java` | the line `/` and `?` read, with Vim's editing keys and history |
| `MarkCommands.java` | `m`, `'`, `` ` ``, `<C-o>`, `<Tab>`, `g;`, `g,`, `gi` |
| `Numbers.java` | `<C-a>`, `<C-x>`, and in Visual mode `g<C-a>`, `g<C-x>` (do_addsub) |
| `Format.java` | `gq` and `gw` (format_lines, internal_format, comment leaders) |
| `Highlights.java` | draws the Visual area and search matches on the text area |

Changed there: `package-info`, `Mode` (Visual modes, command line), `Keywords` (a Java class for `\k`), `Options` (search, 'textwidth', 'comments', 'formatoptions'), `Buffer` (`setLine` replaces only what differs), `Registers` (blockwise, `resolve`, `restore`), `Span` (block columns, Visual, end adjustment), `Context`, `Vim` (search, global marks), `VimCommands` (Visual table, command-line motions), `Motions` (Visual `$` and `l`), `TextObjects` (Visual branches), `Operators` (blocks, `r`, `I`/`A`, joins), `NormalCommands` (put through `Put`, joins, `<C-g>`, `ga`), `InsertMode` (end hook, `'^`), `Machine` (Visual branch, command line, forced blocks, marks), `VimEditor` (Visual anchor, marks, highlights, command line, showcmd size).
Changed, `plugins/editor/src/main/java/dev/jasper/editor/vim/ui/VimBar.java` (the command line) and `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java` (the file's name for `<C-g>`).
Tests: `vim/regex/VimRegexTest.java`, `vim/VisualSearchUiTest.java` (new); `vim/ui/VimBarTest.java` and the corpus harness (`CorpusCase`, `CorpusFile`, `CorpusRunner`, `RecordCorpus`, `VimCorpusTest`) changed. Corpus: seven new files in `plugins/editor/src/test/resources/vim/corpus/` (`visual.case`, `visual-block.case`, `search.case`, `regex.case`, `marks.case`, `numbers.case`, `format.case`).
Docs: `docs/vim-divergences.md`, `docs/editor-vim-reference.md` (generated), `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec.

---

### Task 1: The Vim regex translator

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/regex/VimRegex.java`, `package-info.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/regex/VimRegexTest.java`

**Interfaces:**
- Produces: `public final class VimRegex` with `public record Settings(boolean ignoreCase, boolean smartCase, String keyword, String lastSubstitute)` (`Settings.DEFAULT`), `public static final String WIDE` (the Java class for word characters above U+00FF), `static VimRegex compile(String, Settings)` (throws `IllegalArgumentException` with Vim's message), `pattern()`, `ignoreCase()`, `start(Matcher)`, `end(Matcher)` (a match narrowed by `\zs`/`\ze`).

How it works: the lexer is regexp.c's `peekchr`/`skipchr` — it decides, for the current magic level and position, whether a character is special (`magic(c)`) or literal, including the rules for `^` (only at a branch start), `$` (only at a branch end) and `*` (literal at the start). The parser follows `regbranch`, `regconcat`, `regpiece` and `regatom`. Text is matched as one string whose lines end with `\n`, with `MULTILINE | UNIX_LINES`; only the `\_x` forms, `\n` and `\_.` cross a line break. `\(` becomes a named group (`g1`…) so `\zs` and `\ze` (empty named groups) do not shift back-references. Task 3's `regex.case` checks the translator against Vim by searching; this test checks it on its own and pins the error messages.

- [ ] **Step 1: Write the failing test**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/regex/VimRegexTest.java`:

```java
package dev.jasper.editor.vim.regex;

import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The translator on its own; the corpus's regex.case checks it against Vim by searching. */
class VimRegexTest {
    private static final VimRegex.Settings PLAIN = VimRegex.Settings.DEFAULT;

    /** @return the first match of a Vim pattern in the text, as Vim narrows it with \zs and \ze; null for none */
    private static String find(String pattern, String text) { return find(pattern, text, PLAIN); }

    private static String find(String pattern, String text, VimRegex.Settings settings) {
        VimRegex re = VimRegex.compile(pattern, settings);
        Matcher m = re.pattern().matcher(text);
        return m.find() ? text.substring(re.start(m), re.end(m)) : null;
    }

    @Test void magicLevelsDecideWhichCharactersAreSpecial() {
        assertThat(find("a.c", "abc")).isEqualTo("abc");
        assertThat(find("\\Ma.c", "abc a.c")).isEqualTo("a.c");
        assertThat(find("\\Va*c", "aac a*c")).isEqualTo("a*c");
        assertThat(find("\\v(ab){2}", "ab abab")).isEqualTo("abab");
        assertThat(find("\\(ab\\)\\{2}", "ab abab")).isEqualTo("abab");
        assertThat(find("a\\{-1,}", "aaa")).as("a lazy brace takes as few as it can").isEqualTo("a");
        assertThat(find("a\\{2,1}", "aaa")).as("Vim swaps reversed limits").isEqualTo("aa");
    }

    @Test void caretDollarAndStarFollowVimsPositionRules() {
        assertThat(find("a^b", "a^b")).isEqualTo("a^b");
        assertThat(find("a$b", "a$b")).isEqualTo("a$b");
        assertThat(find("*b", "a*b")).isEqualTo("*b");
        assertThat(find("^b", "a\nb")).isEqualTo("b");
        assertThat(find("a$", "ab a\nx")).isEqualTo("a");
        assertThat(find("x\\|^b", "a\nb")).isEqualTo("b");
    }

    @Test void classesAndLineBreaks() {
        assertThat(find("\\d\\+", "ab 123")).isEqualTo("123");
        assertThat(find("\\S\\+", "  ab\ncd")).isEqualTo("ab");
        assertThat(find("o\\_s*b", "foo\n  bar")).isEqualTo("oo\n  b".substring(1));
        assertThat(find("f\\_.*z", "fa\nbz")).isEqualTo("fa\nbz");
        assertThat(find("[^a]", "a\nb")).as("a negated bracket never takes the line break").isEqualTo("b");
        assertThat(find("[[:digit:]]\\+", "x42")).isEqualTo("42");
        assertThat(find("[a", "x [a")).as("[ without ] is a literal").isEqualTo("[a");
        assertThat(find("\\%d65", "xA")).isEqualTo("A");
    }

    @Test void wordBoundariesUseIskeyword() {
        assertThat(find("\\<cat\\>", "concat cat")).isEqualTo("cat");
        var dashes = new VimRegex.Settings(false, false, "a-z\\-", null);
        assertThat(find("\\<a-b\\>", "xa-b a-b", dashes)).as("with - in 'iskeyword', a-b is one word").isEqualTo("a-b");
    }

    @Test void zsAndZeNarrowTheMatch() {
        assertThat(find("foo\\zsbar", "foobar")).isEqualTo("bar");
        assertThat(find("foo\\zebar", "foobaz foobar")).isEqualTo("foo");
        assertThat(find("\\<fu\\%[nction]\\>", "fun")).isEqualTo("fun");
    }

    @Test void caseFollowsTheOptionsAndTheFlags() {
        var ic = new VimRegex.Settings(true, false, VimRegex.Settings.DEFAULT.keyword(), null);
        var scs = new VimRegex.Settings(true, true, VimRegex.Settings.DEFAULT.keyword(), null);
        assertThat(find("abc", "ABC", ic)).isEqualTo("ABC");
        assertThat(find("Abc", "abc Abc", scs)).as("smartcase: an upper-case letter matches case").isEqualTo("Abc");
        assertThat(find("\\Sbc", "xABC", scs)).as("an escaped letter does not count").isEqualTo("ABC");
        assertThat(find("\\cabc", "ABC")).isEqualTo("ABC");
        assertThat(find("\\Cabc", "ABC abc", ic)).isEqualTo("abc");
    }

    @Test void errorsNameTheProblem() {
        assertThatThrownBy(() -> VimRegex.compile("a\\%Vb", PLAIN)).hasMessage("E869: Unsupported in Jasper's Vim regex: \\%V");
        assertThatThrownBy(() -> VimRegex.compile("a\\@<=b", PLAIN)).hasMessage("E869: Unsupported in Jasper's Vim regex: \\@");
        assertThatThrownBy(() -> VimRegex.compile("a\\%23lb", PLAIN)).hasMessageStartingWith("E869: Unsupported in Jasper's Vim regex: \\%23l");
        assertThatThrownBy(() -> VimRegex.compile("\\(a", PLAIN)).hasMessage("E54: Unmatched \\(");
        assertThatThrownBy(() -> VimRegex.compile("a**", PLAIN)).hasMessage("E61: Nested *");
        assertThatThrownBy(() -> VimRegex.compile("\\+", PLAIN)).hasMessage("E64: \\+ follows nothing");
        assertThatThrownBy(() -> VimRegex.compile("\\1", PLAIN)).hasMessage("E65: Illegal back reference");
        assertThatThrownBy(() -> VimRegex.compile("[[=a=]]", PLAIN)).hasMessageStartingWith("E869");
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.regex.VimRegexTest'
```

Expected: compilation fails: `VimRegex` does not exist.

- [ ] **Step 3: Write the translator**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/regex/package-info.java`:

```java
/**
 * The Vim regex translator (spec 3.7): Vim patterns to {@link java.util.regex.Pattern}. Pure; may depend on: the JDK.
 */
package dev.jasper.editor.vim.regex;
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/regex/VimRegex.java`:

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
    private final boolean hasStart, hasEnd;

    private VimRegex(Pattern pattern, boolean ignoreCase, boolean hasStart, boolean hasEnd) {
        this.pattern = pattern;
        this.ignoreCase = ignoreCase;
        this.hasStart = hasStart;
        this.hasEnd = hasEnd;
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
            return new VimRegex(Pattern.compile(java, flags), ic, t.hasStart, t.hasEnd);
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
            if (c == magic('n')) return "\\n";
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
                    return "\\A";
                }
                case '$' -> {
                    pos++;
                    resync();
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

- [ ] **Step 4: Run the test**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.regex.VimRegexTest'
```

Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/vim-v1b
git add plugins/editor/src/main/java/dev/jasper/editor/vim/regex plugins/editor/src/test/java/dev/jasper/editor/vim/regex
git commit -m "Add the Vim regex translator

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: The engine: Visual mode, search, marks, numbers and formatting

**Files:**
- Create in `plugins/editor/src/main/java/dev/jasper/editor/vim/`: `Block.java`, `Put.java`, `Marks.java`, `Visual.java`, `Search.java`, `CommandLine.java`, `MarkCommands.java`, `Numbers.java`, `Format.java`, `Highlights.java`
- Replace in `plugins/editor/src/main/java/dev/jasper/editor/vim/`: `package-info.java`, `Mode.java`, `Keywords.java`, `Options.java`, `Buffer.java`, `Registers.java`, `Span.java`, `Context.java`, `Vim.java`, `VimCommands.java`, `Motions.java`, `TextObjects.java`, `Operators.java`, `NormalCommands.java`, `InsertMode.java`, `Machine.java`, `VimEditor.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/VisualSearchUiTest.java`

**Interfaces:**
- Consumes: `VimRegex` (Task 1); plan V1a's engine.
- Produces (used by Tasks 3 and 4): `Mode.VISUAL`, `VISUAL_LINE`, `VISUAL_BLOCK`, `COMMAND_LINE`, `Mode.visual()`; `Registers.Type.BLOCK`, `Registers.Content(List<String>, Type, int width)`; public `VimEditor.commandLineText()` (`Optional<String>`: the prompt and text), `commandLineCursor()`, `documentInfo(Supplier<String> name, BooleanSupplier modified)`; package-private `VimEditor.visualAnchor()`, `highlights()` (`List<int[]>` of `{kind, start, end}`, kind 0 selection colour, 1 mark-all colour), `area()`.

The classes refer to one another (the machine to Visual mode and search, Visual mode to the machine, marks to every edit), so this task writes them together, like plan V1a's Task 4. How the pieces fit:
- **Visual mode.** `v`, `V`, `<C-v>` (Normal-mode actions in `Visual`) set `VimEditor`'s Visual anchor and mode. While a Visual mode is on, `Machine.visualKey` looks keys up with `VimCommands.visualKey`: the Visual table first, then Normal-mode motions (which move the cursor, `$` and `l` onto the line's end as Vim's `past_line` allows) and operators, then text objects (which set the anchor and cursor themselves and return `TextObjects.VISUAL_DONE`). An operator calls `Visual.operate`, which applies v_visop (uppercase commands become linewise, `D` and `C` reach each line's end in a block), and `Visual.range`, a port of do_pending_operator's Visual part: charwise areas include the character under the end and, at a line's end, the line break (not for `J`, `>`, `gq` and the other line operators); blocks become `Span.Columns` from get_op_vcol. `Machine.applyVisual` remembers the area for `gv` and `'<`/`'>`, runs the operator (or `J`, `r`, `I`, `A`, `<C-a>` and the rest, which are not operators in the table), and records the change with a `Visual.Redo` size so `.` reselects an area of that size first.
- **Blocks.** `Block.prep` is block_prep. `Operators` handles block spans in `delete`, `change` (the typed text goes to every line when the insert ends, through `InsertMode.onEnd`), `yank` (a `BLOCK` register with Vim's `y_width`), `shift`, `changeCase`, `replace`, `insert` (block `I`/`A`, block_insert). `Put` is do_put, used by `p` in Normal mode too; `Visual.put` is nv_put_opt, which saves and restores the register the delete overwrites. `d<C-v>j` (a forced block) becomes a Visual block operation, as in Vim.
- **Search.** `/` and `?` are motions registered with `VimCommands.motionWithLine`: `Machine.readLine` opens the `CommandLine`, which takes every key until `<CR>` (the typed keys go into the `.` record) or `<Esc>`. `Search.normal` is do_search (offsets `e`, `s`, `b`, `+n`, `;`), `searchit` its search (the first-line and last-line rules, the count loop, 'wrapscan' and its message). `*` and `#` find the identifier as find_ident_at_pos does and ignore 'smartcase'.
- **Marks.** `Marks` listens to the document: inserted and removed lines move marks as mark_adjust does, a deleted line deletes its lowercase marks, joins move marks onto the joined line (`Marks.joining`), and every change sets `'.` and, for an undo step's first change away from the last entry, a change-list entry. A motion whose `Target.jump()` is set calls `Marks.setpcmark` first; `VimEditor.feed` calls `checkpcmark` after each key. The operators set `'[` and `']` as Vim's op_* functions do (`Machine.setChangeMarks`).
- **Highlights.** `VimEditor.refreshHighlights` (after every key, and after text changes) draws the Visual area, the 'hlsearch' matches (cached by pattern and text version) and the 'incsearch' preview through `Highlights`, a thin layer over the text area's highlighter.

- [ ] **Step 1: Write the failing test**

`VisualSearchUiTest` checks what the corpus cannot see: the drawn highlights, messages, the command line's history, `<C-g>` and `ga`, and a mouse selection becoming a Visual area.

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/VisualSearchUiTest.java`:

```java
package dev.jasper.editor.vim;

import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** What the corpus cannot see: highlights, messages, the command line's history and the mouse (spec 5.1 and 5.3). */
class VisualSearchUiTest {
    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private static VimEditor editor(Vim vim, String text) {
        var area = new VimTextArea();
        area.setText(text);
        area.discardAllEdits();
        area.setCaretPosition(0);
        return new VimEditor(vim, area, EditorDefaults.VIM);
    }

    private static List<String> ranges(VimEditor editor) {
        return editor.highlights().stream().map(r -> r[0] + ":" + r[1] + "-" + r[2]).toList();
    }

    @Test void visualAreasAreDrawnAsVimSelectsThem() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "abc\n\tdef\nghi\n");
            type(editor, "vl");
            assertThat(ranges(editor)).containsExactly("0:0-2");
            type(editor, "$");
            assertThat(ranges(editor)).as("past the last character the line break is in the area").containsExactly("0:0-4");
            type(editor, "V");
            assertThat(ranges(editor)).containsExactly("0:0-4");
            type(editor, "<C-v>j");
            assertThat(ranges(editor)).as("a block covers the tab it cuts").containsExactly("0:0-3", "0:4-8");
            type(editor, "<Esc>");
            assertThat(ranges(editor)).isEmpty();
            assertThat(editor.area().getSelectionStart()).isEqualTo(editor.area().getSelectionEnd());
        });
    }

    @Test void hlsearchDrawsEveryMatchAndFollowsEdits() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "ab ab\nab\n");
            editor.options().set("hlsearch", true);
            type(editor, "/ab<CR>");
            assertThat(ranges(editor)).containsExactly("1:0-2", "1:3-5", "1:6-8");
            type(editor, "x");
            assertThat(ranges(editor)).containsExactly("1:0-2", "1:5-7");
            editor.options().set("hlsearch", false);
            type(editor, "l");
            assertThat(ranges(editor)).isEmpty();
        });
    }

    @Test void incsearchShowsTheMatchWithoutMovingTheCursor() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "one two\nthree two\n");
            type(editor, "/tw");
            assertThat(ranges(editor)).containsExactly("0:4-6");
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 0));
            type(editor, "<BS><BS>thr");
            assertThat(ranges(editor)).containsExactly("0:8-11");
            type(editor, "<Esc>");
            assertThat(ranges(editor)).isEmpty();
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 0));
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
        });
    }

    @Test void searchesReportLikeVim() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "abc\n");
            type(editor, "/zz<CR>");
            assertThat(editor.message()).isEqualTo("E486: Pattern not found: zz");
            assertThat(editor.messageIsError()).isTrue();
            editor.options().set("wrapscan", false);
            type(editor, "/abc<CR>");
            assertThat(editor.message()).isEqualTo("E385: Search hit BOTTOM without match for: abc");
            type(editor, "?b<CR>");
            assertThat(editor.message()).isEqualTo("E384: Search hit TOP without match for: b");
            type(editor, "/a\\%Vb<CR>");
            assertThat(editor.message()).isEqualTo("E869: Unsupported in Jasper's Vim regex: \\%V");
            type(editor, "/\\(a<CR>");
            assertThat(editor.message()).isEqualTo("E54: Unmatched \\(");
        });
    }

    @Test void theCommandLineKeepsAHistoryFilteredByPrefix() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "alpha beta alpine\n");
            type(editor, "/alpha<CR>/beta<CR>/alpine<CR>gg");
            type(editor, "/al<Up>");
            assertThat(editor.commandLineText()).hasValue("/alpine");
            type(editor, "<Up>");
            assertThat(editor.commandLineText()).hasValue("/alpha");
            type(editor, "<Down><Down>");
            assertThat(editor.commandLineText()).as("past the newest entry the typed prefix returns").hasValue("/al");
            type(editor, "<C-u><Up>");
            assertThat(editor.commandLineText()).hasValue("/alpine");
            type(editor, "<Esc>");
        });
    }

    @Test void ctrlGAndGaShowTheirMessages() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "a\tb\nxé́\n");
            editor.documentInfo(() -> "notes.txt", () -> false);
            type(editor, "ll<C-g>");
            assertThat(editor.message()).isEqualTo("\"notes.txt\" line 1 of 2 --50%-- col 3-9");
            editor.documentInfo(() -> "notes.txt", () -> true);
            type(editor, "j0<C-g>");
            assertThat(editor.message()).isEqualTo("\"notes.txt\" [Modified] line 2 of 2 --100%-- col 1");
            type(editor, "ga");
            assertThat(editor.message()).isEqualTo("<x>  120,  Hex 78,  Octal 170");
            type(editor, "lga");
            assertThat(editor.message()).isEqualTo("<é> 233, Hex 00e9, Oct 351 < ́> 769, Hex 0301, Oct 1401");
        });
    }

    @Test void leavingTheTabSetsTheQuoteMark() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "abc\ndef\n");
            type(editor, "`\"");
            assertThat(editor.message()).isEqualTo("E20: Mark not set");
            type(editor, "jl");
            for (var listener : editor.area().getFocusListeners())
                listener.focusLost(new java.awt.event.FocusEvent(editor.area(), java.awt.event.FocusEvent.FOCUS_LOST));
            type(editor, "gg`\"");
            assertThat(editor.cursor()).isEqualTo(new Pos(1, 1));
        });
    }

    @Test void aMouseSelectionBecomesAVisualAreaWithTheSameCharacters() throws Exception {
        var editor = new VimEditor[1];
        onEdt(() -> {
            editor[0] = editor(new Vim(VimHost.detached()), "abcd\nefgh\n");
            editor[0].area().select(1, 3);
        });
        onEdt(() -> { });
        onEdt(() -> {
            assertThat(editor[0].mode()).isEqualTo(Mode.VISUAL);
            assertThat(editor[0].visualAnchor()).isEqualTo(new Pos(0, 1));
            assertThat(editor[0].cursor()).isEqualTo(new Pos(0, 2));
            type(editor[0], "<Esc>");
            editor[0].area().setCaretPosition(7);
            editor[0].area().moveCaretPosition(2);
        });
        onEdt(() -> { });
        onEdt(() -> {
            assertThat(editor[0].mode()).isEqualTo(Mode.VISUAL);
            assertThat(editor[0].visualAnchor()).as("a backward drag: the anchor steps back").isEqualTo(new Pos(1, 1));
            assertThat(editor[0].cursor()).isEqualTo(new Pos(0, 2));
            type(editor[0], "<Esc>");
            editor[0].area().select(2, 5);
        });
        onEdt(() -> { });
        onEdt(() -> assertThat(editor[0].cursor()).as("ending on the line break: the line's end").isEqualTo(new Pos(0, 4)));
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VisualSearchUiTest'
```

Expected: compilation fails: `VimEditor.highlights`, `documentInfo` and `commandLineText` do not exist.

- [ ] **Step 3: Write the new classes**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Block.java`:

```java
package dev.jasper.editor.vim;

/**
 * The part of one line inside a block of display columns: Vim's {@code struct block_def}, filled by
 * {@link #prep}, a port of ops.c's block_prep. A tab that straddles the block's edge is counted as
 * {@link #startspaces} or {@link #endspaces}: the spaces that stand for the part of it inside (or, when deleting,
 * outside) the block.
 */
final class Block {
    /** Which operation the block is prepared for; block_prep's answers differ for these. */
    enum Op { OTHER, INSERT, APPEND, REPLACE, LSHIFT }

    /** Display columns before the first character wholly inside the block. */
    int startspaces;
    /** Display columns after the last character wholly inside the block. */
    int endspaces;
    /** Length of the text inside the block, in UTF-16 units. */
    int textlen;
    /** Column of the first character (partly) inside the block. */
    int textcol;
    /** Display column of the first character wholly inside the block. */
    int startVcol;
    /** Display column of the first character wholly after the block. */
    int endVcol;
    /** Whether the line ends before the block does. */
    boolean isShort;
    /** Whether the block lies inside one character (a tab). */
    boolean isOneChar;
    /** Whether {@code $} made the block reach every line's end (Vim's is_MAX). */
    boolean isMax;
    /** Display columns and characters of white space just before the block. */
    int preWhitesp, preWhitespC;
    /** Display widths of the character after, and before, the block. */
    int endCharVcols, startCharVcols;

    private Block() { }

    /** @return the text inside the block, as op_yank's yank_copy_line builds it (padding spaces included) */
    String copy(String line) {
        return " ".repeat(startspaces) + line.substring(textcol, Math.min(line.length(), textcol + textlen)) + " ".repeat(Math.max(0, endspaces));
    }

    /**
     * block_prep.
     *
     * @param line the line's text
     * @param opStart the block's first display column
     * @param opEnd the block's last display column (inclusive)
     * @param tabstop 'tabstop'
     * @param isDel whether the text will be removed (a partly covered tab is then counted from the outside)
     * @param op the operation
     * @param inclusive the operator's inclusive flag (for {@link Op#APPEND} on short lines)
     * @return the block's part of the line
     */
    static Block prep(String line, int opStart, int opEnd, int tabstop, boolean isDel, Op op, boolean inclusive) {
        Block b = new Block();
        int incr = 0, col = 0, vcol = 0, prevPstart = 0;
        while (vcol < opStart && col < line.length()) {
            incr = Text.width(line, col, vcol, tabstop);
            vcol += incr;
            if (white(line.charAt(col))) {
                b.preWhitesp += incr;
                b.preWhitespC++;
            } else {
                b.preWhitesp = 0;
                b.preWhitespC = 0;
            }
            prevPstart = col;
            col = Text.next(line, col);
        }
        b.startVcol = vcol;
        int pstart = col;
        b.startCharVcols = incr;
        if (b.startVcol < opStart) {
            b.endVcol = b.startVcol;
            b.isShort = true;
            if (!isDel || op == Op.APPEND) b.endspaces = opEnd - opStart + 1;
        } else {
            b.startspaces = b.startVcol - opStart;
            if (isDel && b.startspaces != 0) b.startspaces = b.startCharVcols - b.startspaces;
            int pend = pstart;
            b.endVcol = b.startVcol;
            if (b.endVcol > opEnd) {
                b.isOneChar = true;
                if (op == Op.INSERT) b.endspaces = b.startCharVcols - b.startspaces;
                else if (op == Op.APPEND) {
                    b.startspaces += opEnd - opStart + 1;
                    b.endspaces = b.startCharVcols - b.startspaces;
                } else {
                    b.startspaces = opEnd - opStart + 1;
                    if (isDel && op != Op.LSHIFT) {
                        b.startspaces = b.startCharVcols - (b.startVcol - opStart);
                        b.endspaces = b.endVcol - opEnd - 1;
                    }
                }
            } else {
                int v = b.endVcol, prevPend = pend;
                while (v <= opEnd && pend < line.length()) {
                    prevPend = pend;
                    incr = Text.width(line, pend, v, tabstop);
                    v += incr;
                    pend = Text.next(line, pend);
                }
                b.endVcol = v;
                if (b.endVcol <= opEnd && (!isDel || op == Op.APPEND || op == Op.REPLACE)) {
                    b.isShort = true;
                    b.endspaces = op == Op.APPEND ? opEnd - b.endVcol + (inclusive ? 1 : 0) : 0;
                } else if (b.endVcol > opEnd) {
                    b.endspaces = b.endVcol - opEnd - 1;
                    if (!isDel && b.endspaces != 0) {
                        b.endspaces = incr - b.endspaces;
                        if (pend != pstart) pend = prevPend;
                    }
                }
            }
            b.endCharVcols = incr;
            if (isDel && b.startspaces != 0) pstart = prevPstart;
            b.textlen = pend - pstart;
        }
        b.textcol = pstart;
        return b;
    }

    static boolean white(char c) { return c == ' ' || c == '\t'; }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Put.java`:

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

    private Put() { }

    /**
     * @param c the command's context; its register, if any, is the one put
     * @param forward p rather than P
     * @param count how many copies
     * @param flags the flags above
     * @param visualEnd for {@code p} in Visual mode: the last line of the deleted Visual area, or -1
     */
    static void put(Context c, boolean forward, int count, int flags, int visualEnd) {
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
        if ((flags & CURSEND) != 0) {
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

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Marks.java`:

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

    /** setpcmark: the cursor becomes the previous context and a new jumplist entry. */
    void setpcmark(Pos cursor) {
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
    private void cleanupJumps() {
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

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Visual.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Registers.Content;
import dev.jasper.editor.vim.Target.Kind;

/**
 * Visual mode (spec 4.1), ported from Vim's normal.c (nv_visual, v_visop, v_swap_corners, nv_put_opt, the
 * {@code gv} command) and ops.c's do_pending_operator, which turns the Visual area into an operator's span:
 * charwise areas include the character under the cursor and, when the cursor is on the end of a line, the line
 * break; linewise areas are whole lines; block areas are display columns.
 */
final class Visual {
    /** Vim's MAXCOL for the remembered column: {@code $} was used. */
    static final int MAX = Integer.MAX_VALUE;
    /** Mark names for the last Visual area's two ends (Vim's b_visual); not user marks. */
    static final char START = '\u0001', END = '\u0002';

    /**
     * The size of a Visual area, so {@code .} can repeat an operator on the same size (Vim's redo_VIsual).
     *
     * @param mode the Visual mode
     * @param lines the number of lines
     * @param vcol the width in display columns, the last column for multi-line charwise areas, or {@link #MAX}
     * @param count the count typed with the operator, or 0
     */
    record Redo(Mode mode, int lines, int vcol, int count) { }

    private Visual() { }

    static void register(VimCommands commands) {
        commands.action("v", "start charwise Visual mode", false, c -> start(c, Mode.VISUAL));
        commands.action("V", "start linewise Visual mode", false, c -> start(c, Mode.VISUAL_LINE));
        commands.action("<C-v>", "start blockwise Visual mode", false, c -> start(c, Mode.VISUAL_BLOCK));
        commands.action("gv", "select the last Visual area again", false, Visual::reselect);
        commands.visual("v", "charwise Visual mode, or back to Normal mode", false, false, c -> switchTo(c, Mode.VISUAL));
        commands.visual("V", "linewise Visual mode, or back to Normal mode", false, false, c -> switchTo(c, Mode.VISUAL_LINE));
        commands.visual("<C-v>", "blockwise Visual mode, or back to Normal mode", false, false, c -> switchTo(c, Mode.VISUAL_BLOCK));
        commands.visual("o", "go to the other end of the area", false, false, c -> swap(c, false));
        commands.visual("O", "go to the other corner of the block (the other end otherwise)", false, false, c -> swap(c, true));
        commands.visual("gv", "swap with the last Visual area", false, false, Visual::reselect);
        commands.visual("x", "delete", false, true, c -> operate(c, "d", false));
        commands.visual("<Del>", "delete", false, true, c -> operate(c, "d", false));
        commands.visual("s", "change", false, true, c -> operate(c, "c", false));
        commands.visual("X", "delete the lines (in a block, the block)", false, true, c -> operate(c, "d", true));
        commands.visual("D", "delete the lines; in a block, to the end of each line", false, true, c -> operate(c, "D", true));
        commands.visual("Y", "yank the lines (in a block, the block)", false, false, c -> operate(c, "y", true));
        commands.visual("C", "change the lines; in a block, to the end of each line", false, true, c -> operate(c, "C", true));
        commands.visual("S", "change the lines (in a block, the block)", false, true, c -> operate(c, "c", true));
        commands.visual("R", "change the lines (in a block, the block)", false, true, c -> operate(c, "c", true));
        commands.visual("u", "lower case", false, true, c -> operate(c, "gu", false));
        commands.visual("U", "upper case", false, true, c -> operate(c, "gU", false));
        commands.visual("~", "toggle case", false, true, c -> operate(c, "g~", false));
        commands.visual("J", "join the lines, with spaces", false, true, c -> operate(c, "J", false));
        commands.visual("gJ", "join the lines, without spaces", false, true, c -> operate(c, "gJ", false));
        commands.visual("r", "replace every character with {char}", true, true, c -> operate(c, "r", false));
        commands.visual("p", "replace with the register", false, true, c -> put(c, false));
        commands.visual("P", "replace with the register, keeping the registers", false, true, c -> put(c, true));
        commands.visual("I", "insert before the area (in a block, on every line)", false, true, c -> operate(c, "I", true));
        commands.visual("A", "append after the area (in a block, on every line)", false, true, c -> operate(c, "A", true));
    }

    /** v, V, CTRL-V in Normal mode: start Visual mode at the cursor (n_start_visual_mode). */
    static void start(Context c, Mode mode) {
        VimEditor editor = c.editor;
        editor.visualAnchor(editor.cursor());
        editor.mode(mode);
        if (c.hasCount && c.count > 1) {
            // With a count, select that many characters or lines (nv_visual).
            Context move = new Context(editor);
            move.count = c.count - 1;
            move.visual = editor.visualAnchor();
            Target target = mode == Mode.VISUAL_LINE ? Motions.down(move, move.count) : Motions.right(move, 'l');
            if (target != null) editor.setCursor(target.pos(), true);
        }
    }

    /** v, V, CTRL-V in Visual mode: the same mode ends Visual mode, another switches to it. */
    static void switchTo(Context c, Mode mode) {
        if (c.editor.mode() == mode) end(c.editor);
        else c.editor.mode(mode);
    }

    /** end_visual_mode: remembers the area for gv and '< '>, and keeps the cursor off the end of the line. */
    static void end(VimEditor editor) {
        remember(editor, editor.mode());
        editor.visualAnchor(null);
        editor.mode(Mode.NORMAL);
        editor.setCursor(editor.cursor());
    }

    static void remember(VimEditor editor, Mode mode) {
        Marks marks = editor.marks();
        marks.set(START, editor.visualAnchor());
        marks.set(END, editor.cursor());
        marks.visualMode = mode;
        marks.visualCurswant = editor.curswant();
    }

    /** v_swap_corners. */
    static void swap(Context c, boolean corner) {
        VimEditor editor = c.editor;
        Pos anchor = editor.visualAnchor(), cursor = editor.cursor();
        if (corner && editor.mode() == Mode.VISUAL_BLOCK) {
            int ts = c.tabstop();
            int left = Math.min(startVcol(c.buffer, anchor, ts), startVcol(c.buffer, cursor, ts));
            int right = Math.max(endVcol(c.buffer, anchor, ts), endVcol(c.buffer, cursor, ts));
            Pos newAnchor = new Pos(anchor.line, coladvance(c.buffer, anchor.line, left, ts));
            Pos newCursor = new Pos(cursor.line, coladvance(c.buffer, cursor.line, right, ts));
            if (newCursor.col == cursor.col) {
                newAnchor = new Pos(anchor.line, coladvance(c.buffer, anchor.line, right, ts));
                newCursor = new Pos(cursor.line, coladvance(c.buffer, cursor.line, left, ts));
                editor.curswant(left);
            } else editor.curswant(right);
            editor.visualAnchor(newAnchor);
            editor.setCursor(newCursor, true);
            return;
        }
        editor.visualAnchor(cursor);
        editor.setCursor(anchor, true);
        editor.forgetColumn();
    }

    /** gv: select the last Visual area again; in Visual mode, swap the current area with it. */
    static void reselect(Context c) {
        VimEditor editor = c.editor;
        Marks marks = editor.marks();
        Pos start = marks.get(START), end = marks.get(END);
        if (start == null || end == null || marks.visualMode == null) throw Context.fail();
        Mode mode = marks.visualMode;
        int curswant = marks.visualCurswant;
        if (editor.mode().visual()) remember(editor, editor.mode());
        editor.visualAnchor(clamp(c.buffer, start));
        editor.mode(mode);
        editor.setCursor(clamp(c.buffer, end), true);
        editor.curswant(curswant);
    }

    private static Pos clamp(Buffer b, Pos pos) {
        int line = Math.min(pos.line, b.count() - 1);
        return new Pos(line, Math.min(pos.col, b.line(line).length()));
    }

    /**
     * An operator on the Visual area: v_visop's uppercase variants ({@code linewise}: X, D, Y, C, S, R, I, A become
     * linewise, except in a block, where D and C go to each line's end), then do_pending_operator.
     */
    static void operate(Context c, String operator, boolean linewise) {
        VimEditor editor = c.editor;
        Mode original = editor.mode();
        Mode mode = original;
        // D and C: to the end of each line of a block; otherwise d and c.
        boolean toEnd = operator.equals("D") || operator.equals("C");
        if (toEnd) operator = operator.toLowerCase(java.util.Locale.ROOT);
        if (linewise) {
            if (mode != Mode.VISUAL_BLOCK) mode = Mode.VISUAL_LINE;
            else if (toEnd) editor.curswant(MAX);
        }
        editor.mode(mode);
        editor.machine().applyVisual(c, operator, range(c, mode, operator), original);
    }

    /** What an operator on the Visual area acts on, and the area's size for {@code .}. */
    record Range(Span span, Redo redo) { }

    static Range range(Context c, Mode mode, String operator) {
        Buffer b = c.buffer;
        int ts = c.tabstop();
        VimEditor editor = c.editor;
        Pos anchor = editor.visualAnchor(), cursor = editor.cursor();
        Pos start = anchor.copy();
        if (mode == Mode.VISUAL_LINE) start.col = 0;
        Pos end;
        if (start.compareTo(cursor) < 0) end = cursor.copy();
        else {
            end = start;
            start = cursor.copy();
        }
        int lines = end.line - start.line + 1;
        boolean max = editor.curswant() == MAX;
        Span.Columns block = null;
        if (mode == Mode.VISUAL_BLOCK) {
            int startVcol = startVcol(b, start, ts), endVcol = endVcol(b, start, ts);
            int s = startVcol(b, end, ts), e = endVcol(b, end, ts);
            if (s < startVcol) startVcol = s;
            if (e > endVcol) endVcol = e;
            if (max) {
                endVcol = 0;
                for (int line = start.line; line <= end.line; line++) endVcol = Math.max(endVcol, Text.virtcol(b.line(line), b.line(line).length(), ts));
            }
            end = new Pos(end.line, coladvance(b, end.line, endVcol, ts));
            start = new Pos(start.line, coladvance(b, start.line, startVcol, ts));
            block = new Span.Columns(startVcol, endVcol, max);
        }
        int vcol;
        if (max) vcol = MAX;
        else if (mode == Mode.VISUAL_BLOCK) vcol = block.endVcol() - block.startVcol() + 1;
        else {
            int endV = endVcol(b, end, ts);
            vcol = lines <= 1 ? endV - startVcol(b, start, ts) + 1 : endV;
        }
        Redo redo = new Redo(mode, lines, vcol, c.hasCount ? c.count : 0);
        Kind kind;
        if (mode == Mode.VISUAL_LINE) kind = Kind.LINEWISE;
        else {
            kind = Kind.INCLUSIVE;
            if (mode != Mode.VISUAL_BLOCK && end.col >= b.line(end.line).length()) {
                kind = Kind.EXCLUSIVE;
                if (!onLines(operator) && end.line < b.count() - 1) end = new Pos(end.line + 1, 0);
            }
        }
        return new Range(new Span(start, end, kind, false, block, true), redo);
    }

    /** op_on_lines: operators that work on whole lines, so a charwise area never takes the line break for them. */
    private static boolean onLines(String operator) {
        return switch (operator) {
            case "<lt>", ">", "=", "J", "gJ", "gq", "gw" -> true;
            default -> false;
        };
    }

    /** redo_VIsual_busy: {@code .} makes an area of the remembered size at the cursor. */
    static void reselectForRedo(VimEditor editor, Redo redo) {
        Buffer b = editor.buffer();
        int ts = editor.options().number("tabstop");
        Pos start = editor.cursor();
        editor.visualAnchor(start);
        editor.mode(redo.mode());
        Pos cursor = new Pos(Math.min(start.line + redo.lines() - 1, b.count() - 1), start.col);
        if (redo.vcol() == MAX) {
            editor.curswant(MAX);
            cursor.col = b.line(cursor.line).length();
        } else if (redo.mode() == Mode.VISUAL) {
            int want = redo.lines() <= 1 ? Text.virtcol(b.line(start.line), start.col, ts) + redo.vcol() - 1 : redo.vcol();
            editor.curswant(want);
            cursor.col = coladvance(b, cursor.line, want, ts);
        } else if (redo.mode() == Mode.VISUAL_BLOCK) {
            int want = startVcol(b, start, ts) + redo.vcol() - 1;
            editor.curswant(want);
            cursor.col = coladvance(b, cursor.line, want, ts);
        }
        editor.setCursor(cursor, true);
    }

    /** nv_put_opt in Visual mode: the register's text replaces the area. */
    static void put(Context c, boolean keepRegisters) {
        VimEditor editor = c.editor;
        Mode mode = editor.mode();
        char regname = Registers.effective(c.register, c.options.string("clipboard"));
        boolean saving = regname == 0 || regname == '"' || Character.isDigit(regname) || regname == '-'
            || (regname == '*' || regname == '+') && !c.options.string("clipboard").isEmpty();
        char name = regname == 0 ? '"' : regname;
        // The delete may overwrite the register being put: its content is saved first, put back for the put, and
        // the deleted text restored afterwards (nv_put_opt's reg1 and reg2).
        Content saved = saving ? c.registers.get(name).orElse(null) : null;
        Range range = range(c, mode, "d");
        remember(editor, mode);
        editor.visualAnchor(null);
        editor.mode(Mode.NORMAL);
        Context delete = new Context(editor);
        delete.register = keepRegisters ? '_' : 0;
        editor.setCursor(range.span().start(), true);
        Operators.delete(delete, range.span(), false);
        editor.marks().set('[', range.span().start());
        char target = 0;
        Content deleted = null;
        if (saving) {
            target = c.registers.resolve(name);
            deleted = c.registers.get(target).orElse(null);
            c.registers.restore(target, saved);
        }
        int flags = mode == Mode.VISUAL_LINE ? Put.LINE : mode == Mode.VISUAL ? Put.LINE_SPLIT : 0;
        boolean forward = false;
        if (mode == Mode.VISUAL_BLOCK) flags |= Put.LINE_FORWARD;
        Pos opStart = editor.marks().get('[');
        Pos cursor = editor.cursor();
        if (opStart != null && (mode != Mode.VISUAL_LINE && cursor.col < opStart.col || mode == Mode.VISUAL_LINE && cursor.line < opStart.line)) forward = true;
        Pos endMark = editor.marks().get(END), startMark = editor.marks().get(START);
        int visualEnd = Math.max(endMark == null ? -1 : endMark.line, startMark == null ? -1 : startMark.line);
        Context put = new Context(editor);
        put.register = c.register;
        put.count = c.count;
        put.hasCount = c.hasCount;
        try {
            Put.put(put, forward, c.count, flags, mode == Mode.VISUAL_BLOCK ? visualEnd : -1);
        } finally {
            if (saving) c.registers.restore(target, deleted);
            editor.machine().visualChange(range.redo());
        }
        editor.marks().set(START, editor.marks().get('['));
        editor.marks().set(END, editor.marks().get(']'));
        editor.machine().visualChange(range.redo());
    }

    /** getvvcol's start: the display column where the character at {@code pos} starts. */
    static int startVcol(Buffer b, Pos pos, int ts) { return Text.virtcol(b.line(pos.line), Math.min(pos.col, b.line(pos.line).length()), ts); }

    /** getvvcol's end: the last display column of the character at {@code pos} (the end of a line counts as one). */
    static int endVcol(Buffer b, Pos pos, int ts) {
        String line = b.line(pos.line);
        int col = Math.min(pos.col, line.length());
        int start = Text.virtcol(line, col, ts);
        return col < line.length() ? start + Text.width(line, col, start, ts) - 1 : start;
    }

    /** coladvance in Visual mode: the character covering display column {@code vcol}, or the end of the line. */
    static int coladvance(Buffer b, int line, int vcol, int ts) { return Text.columnAt(b.line(line), vcol, ts); }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Search.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Pattern search (spec 3.7), ported from Vim's search.c: searchit (the count loop, where a forward search may start
 * and 'wrapscan') and do_search (offsets and {@code ;}), with normal.c's normal_search, nv_next and nv_ident for
 * {@code n}, {@code N}, {@code *} and {@code #}. The last pattern, its direction and offset are global, as in Vim.
 */
final class Search {
    /** The last search's offset (Vim's soffset): a line offset, or a character offset from the match's start or end. */
    record Offset(boolean line, boolean end, long off) {
        static final Offset NONE = new Offset(false, false, 0);
    }

    /** A match: its start, and the position after its end. */
    record Match(Pos start, Pos end) { }

    private String pattern;
    private char direction = '/';
    private Offset offset = Offset.NONE;
    private boolean noHighlight;
    private final List<String> history = new ArrayList<>();

    /** @return the last search pattern, or null */
    String pattern() { return pattern; }

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
        if (dir == 0) dir = direction;
        else direction = dir;
        if (reverse) dir = dir == '/' ? '?' : '/';
        noHighlight = false;
        boolean end = false;
        int retval;
        String rest = pat;
        while (true) {
            String searchstr = rest;
            if (rest == null || rest.isEmpty() || rest.charAt(0) == dir) {
                if (pattern == null) throw new VimFailure("E35: No previous regular expression");
                searchstr = "";
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
            if (!searchstr.isEmpty()) pattern = searchstr;
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
        // do_search echoes the search ("/pattern/e"), unless wrapping gave its warning.
        if (!lastWrapped) c.editor.message(echo(dir), false);
        // check_cursor: in Normal mode and with an operator the cursor stays on a character.
        String text = b.line(pos.line);
        if (!c.inVisual() && pos.col >= text.length() && !text.isEmpty()) pos = new Pos(pos.line, Text.last(text));
        Target.Kind kind = retval == 2 ? Target.Kind.LINEWISE : end ? Target.Kind.INCLUSIVE : Target.Kind.EXCLUSIVE;
        Target target = new Target(pos, kind, Target.Column.RESET, mark, true, true);
        return target;
    }

    /** @return the search as do_search echoes it: the pattern and any offset */
    private String echo(char dir) {
        StringBuilder out = new StringBuilder().append(dir).append(pattern);
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
                o.keywords().javaClass(), null));
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
        if (pat.isEmpty()) pat = pattern;
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
            return VimRegex.compile(pat, new VimRegex.Settings(o.flag("ignorecase"), o.flag("smartcase"), o.keywords().javaClass(), null));
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

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/CommandLine.java`:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The command line that {@code /} and {@code ?} read (spec 5.3), with Vim's editing keys (ex_getln.c): moving and
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
        this.type = type;
        this.history = history;
        this.changed = changed;
        this.done = done;
        this.cancelled = cancelled;
        text.setLength(0);
        cursor = 0;
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

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/MarkCommands.java`:

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

    /** nv_mark and setmark_pos. */
    static void set(Context c) {
        int name = c.argumentChar();
        Marks marks = c.editor.marks();
        Pos cursor = c.cursor();
        if (name == '\'' || name == '`') marks.setpcmarkKeep(cursor);
        else if (name >= 'a' && name <= 'z' || name == '[' || name == ']' || name == '"') marks.set((char) name, cursor);
        else if (name == '<') setVisual(c, Visual.START, cursor);
        else if (name == '>') setVisual(c, Visual.END, cursor);
        else if (name >= 'A' && name <= 'Z') c.vim.globalMark((char) name, c.editor, cursor);
        else throw Context.fail();
    }

    private static void setVisual(Context c, char which, Pos cursor) {
        Marks marks = c.editor.marks();
        marks.set(which, cursor);
        if (marks.visualMode == null) marks.visualMode = Mode.VISUAL;
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
                if (name >= 'A' && name <= 'Z') yield editor.vim().globalMark(name, editor);
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
        String line = c.buffer.line(pos.line);
        int col = Math.min(pos.col, line.length());
        if (col >= line.length() && !line.isEmpty()) col = Text.last(line);
        c.editor.setCursor(new Pos(pos.line, col));
        c.editor.forgetColumn();
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

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Numbers.java`:

```java
package dev.jasper.editor.vim;

/**
 * {@code <C-a>} and {@code <C-x>} (spec 4.1), ported from Vim's ops.c (op_addsub, do_addsub) and charset.c
 * (vim_str2nr): decimal, and with 'nrformats' binary ({@code 0b}), octal (a leading 0) and hex ({@code 0x}), with
 * alpha, unsigned and blank; numbers are unsigned 64-bit as in Vim, so they wrap the same way. In Visual mode each
 * line's first number changes, and {@code g<C-a>} adds progressively more on each line.
 */
final class Numbers {
    private Numbers() { }

    static void register(VimCommands commands) {
        commands.action("<C-a>", "add {count} to the number at or after the cursor", true, c -> addsub(c, false));
        commands.action("<C-x>", "subtract {count} from the number at or after the cursor", true, c -> addsub(c, true));
        commands.visual("<C-a>", "add {count} to the first number in each line", false, true, c -> Visual.operate(c, "<C-a>", false));
        commands.visual("<C-x>", "subtract {count} from the first number in each line", false, true, c -> Visual.operate(c, "<C-x>", false));
        commands.visual("g<C-a>", "add {count}, 2 × {count}, … to the numbers of successive lines", false, true, c -> Visual.operate(c, "g<C-a>", false));
        commands.visual("g<C-x>", "subtract {count}, 2 × {count}, … from the numbers of successive lines", false, true, c -> Visual.operate(c, "g<C-x>", false));
    }

    /** op_addsub in Normal mode. */
    static void addsub(Context c, boolean subtract) {
        Pos p = c.cursor();
        if (!change(c, p, 0, c.count, subtract, false, false)) throw Context.fail();
    }

    /** op_addsub on a Visual area; {@code progressive} for g CTRL-A and g CTRL-X. */
    static void visual(Context c, Span span, boolean subtract, boolean progressive) {
        Buffer b = c.buffer;
        long step = c.count, amount = step;
        int changes = 0;
        Pos startpos = null;
        Pos end = span.end().copy();
        for (int line = span.start().line; line <= span.end().line; line++) {
            Pos pos;
            int length;
            if (span.blockwise()) {
                Block bd = Operators.prep(c, span, b.line(line), false, Block.Op.OTHER);
                pos = new Pos(line, bd.textcol);
                length = bd.textlen;
            } else if (span.linewise()) {
                c.editor.setCursor(new Pos(c.cursor().line, 0), true);
                pos = new Pos(line, 0);
                length = b.line(line).length();
            } else {
                if (line == span.start().line && span.kind() != Target.Kind.INCLUSIVE) Search.decl(b, end);
                length = b.line(line).length();
                pos = new Pos(line, 0);
                if (line == span.start().line) {
                    pos.col = span.start().col;
                    length -= span.start().col;
                }
                if (line == end.line) {
                    length = b.line(end.line).length();
                    if (end.col >= length) end.col = length - 1;
                    length = end.col - pos.col + 1;
                }
            }
            Pos before = c.editor.marks().get('[');
            boolean one = change(c, pos, length, amount, subtract, true, span.linewise());
            if (one) {
                if (changes == 0) startpos = c.editor.marks().get('[');
                changes++;
            } else if (before != null) c.editor.marks().set('[', before);
            if (progressive && one) amount += step;
        }
        if (changes > 0) c.editor.marks().set('[', startpos);
    }

    /**
     * do_addsub.
     *
     * @param length in Visual mode, how much of the line to look in
     * @return whether a number changed
     */
    private static boolean change(Context c, Pos pos, int length, long amount, boolean sub, boolean visual, boolean linewise) {
        Buffer b = c.buffer;
        String nf = c.options.string("nrformats");
        boolean hex = nf.contains("x"), oct = nf.contains("o"), bin = nf.contains("b"), alpha = nf.contains("p");
        boolean unsigned = nf.contains("u"), blank = nf.contains("k");
        boolean blankUnsigned = false, negative = false, wasPositive = true, didChange = false;
        Pos saveCursor = c.cursor();
        String ptr = b.line(pos.line);
        int linelen = ptr.length();
        int col = pos.col;
        try {
            if (col >= linelen) return false;
            if (!visual) {
                if (bin) while (col > 0 && isBin(at(ptr, col))) col--;
                if (hex) while (col > 0 && isHex(at(ptr, col))) col--;
                if (bin && hex && !(col > 0 && (at(ptr, col) == 'X' || at(ptr, col) == 'x') && at(ptr, col - 1) == '0' && isHex(at(ptr, col + 1)))) {
                    col = pos.col;
                    while (col > 0 && isDigit(at(ptr, col))) col--;
                }
                if (hex && col > 0 && (at(ptr, col) == 'X' || at(ptr, col) == 'x') && at(ptr, col - 1) == '0' && isHex(at(ptr, col + 1))
                    || bin && col > 0 && (at(ptr, col) == 'B' || at(ptr, col) == 'b') && at(ptr, col - 1) == '0' && isBin(at(ptr, col + 1))) {
                    col--;
                } else {
                    col = pos.col;
                    while (col < linelen && !isDigit(at(ptr, col)) && !(alpha && isAlpha(at(ptr, col))))
                        col += Character.charCount(ptr.codePointAt(col));
                    while (col > 0 && isDigit(at(ptr, col - 1)) && !(alpha && isAlpha(at(ptr, col)))) col--;
                }
            }
            if (visual) {
                while (col < linelen && length > 0 && !isDigit(at(ptr, col)) && !(alpha && isAlpha(at(ptr, col)))) {
                    int n = Character.charCount(ptr.codePointAt(col));
                    col += n;
                    length -= n;
                }
                if (length <= 0) return false;
                if (col > pos.col && at(ptr, col - 1) == '-' && !unsigned) {
                    if (blank && col >= 2 && !Block.white(at(ptr, col - 2))) blankUnsigned = true;
                    else {
                        negative = true;
                        wasPositive = false;
                    }
                }
            }
            char firstdigit = at(ptr, col);
            if (!isDigit(firstdigit) && !(alpha && isAlpha(firstdigit))) return false;
            Marks marks = c.editor.marks();
            if (alpha && isAlpha(firstdigit)) {
                int ord = Character.toLowerCase(firstdigit) - 'a';
                if (sub) firstdigit = ord < amount ? (Character.isUpperCase(firstdigit) ? 'A' : 'a') : (char) (firstdigit - amount);
                else firstdigit = 26 - ord - 1 < amount ? (Character.isUpperCase(firstdigit) ? 'Z' : 'z') : (char) (firstdigit + amount);
                b.replace(b.offset(pos.line, col), b.offset(pos.line, col + 1), String.valueOf(firstdigit));
                marks.set('[', new Pos(pos.line, col));
                marks.set(']', new Pos(pos.line, col));
                c.editor.setCursor(new Pos(pos.line, col));
                didChange = true;
                return true;
            }
            if (col > 0 && at(ptr, col - 1) == '-' && !visual && !unsigned) {
                if (blank && col >= 2 && !Block.white(at(ptr, col - 2))) blankUnsigned = true;
                else {
                    col--;
                    negative = true;
                }
            }
            int maxlen = 0;
            if (visual && !linewise) maxlen = c.editor.marks().visualCurswant == Visual.MAX ? linelen - col : length;
            Parsed parsed = str2nr(ptr, col, hex, oct, bin, maxlen);
            char pre = parsed.pre;
            length = parsed.length;
            long n = parsed.value;
            if (pre != 0 && negative) {
                col++;
                length--;
                negative = false;
            }
            boolean subtract = sub ^ negative;
            long oldn = n;
            if (!parsed.overflow) n = subtract ? n - amount : n + amount;
            if (pre == 0) {
                if (subtract) {
                    if (Long.compareUnsigned(n, oldn) > 0) {
                        n = 1 + ~n;
                        negative = !negative;
                    }
                } else if (Long.compareUnsigned(n, oldn) < 0) {
                    n = ~n;
                    negative = !negative;
                }
                if (n == 0) negative = false;
            }
            if ((unsigned || blankUnsigned) && negative) {
                n = subtract ? 0 : -1;
                negative = false;
            }
            if (visual && !wasPositive && !negative && col > 0) {
                col--;
                length++;
            }
            int todel = length;
            if (at(ptr, col) == '-') length--;
            for (int i = 0; i < todel && col + i < linelen; i++) {
                char ch = at(ptr, col + i);
                if (ch < 0x100 && Character.isLetter(ch)) c.vim.hexUpper(Character.isUpperCase(ch));
            }
            StringBuilder buf1 = new StringBuilder();
            if (negative && (!visual || wasPositive)) buf1.append('-');
            if (pre != 0) {
                buf1.append('0');
                length--;
            }
            if (pre == 'b' || pre == 'B' || pre == 'x' || pre == 'X') {
                buf1.append(pre);
                length--;
            }
            String buf2;
            if (pre == 'b' || pre == 'B') buf2 = n == 0 ? "" : Long.toBinaryString(n);
            else if (pre == 0) buf2 = Long.toUnsignedString(n);
            else if (pre == '0') buf2 = Long.toOctalString(n);
            else if (c.vim.hexUpper()) buf2 = Long.toHexString(n).toUpperCase(java.util.Locale.ROOT);
            else buf2 = Long.toHexString(n);
            length -= buf2.length();
            if (firstdigit == '0' && !(oct && pre == 0)) while (length-- > 0) buf1.append('0');
            buf1.append(buf2);
            int from = b.offset(pos.line, col);
            b.replace(from, from + Math.min(todel, linelen - col), buf1.toString());
            didChange = true;
            int endCol = col + buf1.length();
            marks.set('[', new Pos(pos.line, col));
            marks.set(']', new Pos(pos.line, Math.max(0, endCol - 1)));
            c.editor.setCursor(new Pos(pos.line, endCol > 0 ? endCol - 1 : 0));
            return true;
        } finally {
            if (visual) c.editor.setCursor(saveCursor, true);
            else if (didChange) c.editor.forgetColumn();
        }
    }

    private record Parsed(char pre, int length, long value, boolean overflow) { }

    /** vim_str2nr: the number at {@code start} (after an optional {@code -}); length counts the {@code -}. */
    private static Parsed str2nr(String s, int start, boolean hex, boolean oct, boolean bin, int maxlen) {
        int ptr = start;
        if (at(s, ptr) == '-') {
            ptr++;
            if (maxlen > 0) maxlen--;
        }
        char pre = 0;
        if (at(s, ptr) == '0' && at(s, ptr + 1) != '8' && at(s, ptr + 1) != '9' && (maxlen == 0 || maxlen > 1)) {
            pre = at(s, ptr + 1);
            if (hex && (pre == 'X' || pre == 'x') && isHex(at(s, ptr + 2)) && (maxlen == 0 || maxlen > 2)) ptr += 2;
            else if (bin && (pre == 'B' || pre == 'b') && isBin(at(s, ptr + 2)) && (maxlen == 0 || maxlen > 2)) ptr += 2;
            else {
                pre = 0;
                if (oct) {
                    for (int n = 1; n != maxlen && isDigit(at(s, ptr + n)); n++) {
                        if (at(s, ptr + n) > '7') {
                            pre = 0;
                            break;
                        }
                        pre = '0';
                    }
                }
            }
        }
        long un = 0;
        boolean overflow = false;
        int n = pre != 0 && pre != '0' ? 3 : 1;
        if (pre == 'B' || pre == 'b') {
            while (at(s, ptr) == '0' || at(s, ptr) == '1') {
                if (Long.compareUnsigned(un, Long.divideUnsigned(-1L, 2)) <= 0) un = 2 * un + (at(s, ptr) - '0');
                else { un = -1L; overflow = true; }
                ptr++;
                if (n++ == maxlen) break;
            }
        } else if (pre == '0') {
            while (at(s, ptr) >= '0' && at(s, ptr) <= '7') {
                if (Long.compareUnsigned(un, Long.divideUnsigned(-1L, 8)) <= 0) un = 8 * un + (at(s, ptr) - '0');
                else { un = -1L; overflow = true; }
                ptr++;
                if (n++ == maxlen) break;
            }
        } else if (pre != 0) {
            while (isHex(at(s, ptr))) {
                if (Long.compareUnsigned(un, Long.divideUnsigned(-1L, 16)) <= 0) un = 16 * un + Character.digit(at(s, ptr), 16);
                else { un = -1L; overflow = true; }
                ptr++;
                if (n++ == maxlen) break;
            }
        } else {
            long max10 = Long.divideUnsigned(-1L, 10), rem = Long.remainderUnsigned(-1L, 10);
            while (isDigit(at(s, ptr))) {
                int digit = at(s, ptr) - '0';
                if (Long.compareUnsigned(un, max10) < 0 || un == max10 && digit <= rem) un = 10 * un + digit;
                else { un = -1L; overflow = true; }
                ptr++;
                if (n++ == maxlen) break;
            }
        }
        return new Parsed(pre, ptr - start, un, overflow);
    }

    private static char at(String s, int i) { return i >= 0 && i < s.length() ? s.charAt(i) : 0; }

    private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }

    private static boolean isHex(char c) { return isDigit(c) || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F'; }

    private static boolean isBin(char c) { return c == '0' || c == '1'; }

    private static boolean isAlpha(char c) { return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'; }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Format.java`:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code gq} and {@code gw} (spec 4.1): Vim's internal formatter, ported from textformat.c (format_lines,
 * internal_format, fmt_check_par, same_leader), misc1.c (get_leader_len) and change.c (open_line's comment leader).
 * Paragraphs are joined and broken at 'textwidth' (79 when it is 0); with {@code q} in 'formatoptions' comment
 * leaders from 'comments' are kept, as with Vim's default {@code tcq}.
 */
final class Format {
    private final Context c;
    private final Buffer b;
    private final int tw, ts;
    private final boolean doComments, autoindent, expandtab;
    private final List<String[]> comments = new ArrayList<>();

    private Format(Context c) {
        this.c = c;
        this.b = c.buffer;
        int width = c.options.number("textwidth");
        this.tw = width > 0 ? width : 79;
        this.ts = c.tabstop();
        this.doComments = c.options.string("formatoptions").contains("q");
        this.autoindent = c.options.flag("autoindent");
        this.expandtab = c.options.flag("expandtab");
        for (String part : c.options.string("comments").split(",")) {
            int colon = part.indexOf(':');
            if (colon < 0) continue;
            comments.add(new String[] { part.substring(0, colon), part.substring(colon + 1) });
        }
    }

    static void register(VimCommands commands) {
        commands.operator("gq", "format the lines to 'textwidth', the cursor on the last formatted line", (c, span) -> op(c, span, false));
        commands.operator("gw", "format the lines to 'textwidth', keeping the cursor", (c, span) -> op(c, span, true));
    }

    /** op_format. */
    static void op(Context c, Span span, boolean keepCursor) {
        Pos saved = c.cursorStart;
        Format f = new Format(c);
        c.editor.setCursor(span.start(), true);
        c.editor.marks().set('[', span.start());
        f.formatLines(span.start().line, span.lineCount());
        Pos p = c.cursor();
        if (span.endAdjusted() && p.line < c.buffer.count() - 1) p = new Pos(p.line + 1, 0);
        p = new Pos(p.line, Text.firstNonBlank(c.buffer.line(p.line)));
        c.editor.setCursor(p);
        c.editor.marks().set(']', p);
        if (keepCursor && saved != null) c.editor.setCursor(saved);
    }

    // --- 'comments' (misc1.c's get_leader_len) ---

    /** A leader found at the start of a line: its length (with the white space after it) and its 'comments' part. */
    private record Leader(int length, int part) {
        static final Leader NONE = new Leader(0, -1);
    }

    private Leader leader(String line) {
        if (!doComments) return Leader.NONE;
        int i = 0, result = 0, part = -1;
        boolean gotCom = false;
        while (i < line.length() && Block.white(line.charAt(i))) i++;
        while (i < line.length()) {
            boolean found = false;
            int middleMatch = 0, savedPart = -1, matched = -1;
            for (int k = 0; k < comments.size(); k++) {
                String flags = comments.get(k)[0], string = comments.get(k)[1];
                if (middleMatch != 0 && flags.indexOf('m') < 0 && flags.indexOf('e') < 0) break;
                if (gotCom && flags.indexOf('n') < 0) continue;
                if (!string.isEmpty() && Block.white(string.charAt(0))) {
                    if (i == 0 || !Block.white(line.charAt(i - 1))) continue;
                    string = string.strip();
                }
                if (!line.startsWith(string, i)) continue;
                int j = string.length();
                if (flags.indexOf('b') >= 0 && i + j < line.length() && !Block.white(line.charAt(i + j))) continue;
                if (flags.indexOf('m') >= 0) {
                    if (middleMatch == 0) {
                        middleMatch = j;
                        savedPart = k;
                    }
                    continue;
                }
                if (middleMatch != 0 && j > middleMatch) middleMatch = 0;
                if (middleMatch == 0) i += j;
                found = true;
                matched = k;
                break;
            }
            if (middleMatch != 0) {
                if (!gotCom) matched = savedPart;
                i += middleMatch;
                found = true;
            }
            if (!found) break;
            if (!gotCom) part = matched;
            while (i < line.length() && Block.white(line.charAt(i))) i++;
            result = i;
            gotCom = true;
            if (matched < 0 || comments.get(matched)[0].indexOf('n') < 0) break;
        }
        return result == 0 ? Leader.NONE : new Leader(result, part);
    }

    private String flags(Leader l) { return l.part() < 0 ? "" : comments.get(l.part())[0]; }

    /** fmt_check_par: a blank line, a line holding only a leader, a comment end, or a paragraph macro is not text. */
    private boolean notParagraph(int line, Leader l) {
        String text = b.line(line);
        return Text.blank(text.substring(l.length())) || l.length() > 0 && flags(l).indexOf('e') >= 0
            || Motions.startPS(b, line, 0, false) && !text.isEmpty() && text.charAt(0) == '.';
    }

    /** same_leader. */
    private boolean sameLeader(int line, Leader l1, Leader l2) {
        if (l1.length() == 0) return l2.length() == 0;
        String f1 = flags(l1);
        if (f1.indexOf('f') >= 0) return l2.length() == 0;
        if (f1.indexOf('e') >= 0) return false;
        if (f1.indexOf('s') >= 0) {
            if (b.line(line).length() <= l1.length()) return false;
            return l2.length() != 0 && flags(l2).indexOf('m') >= 0;
        }
        String line1 = b.line(line), line2 = b.line(line + 1);
        int idx1 = 0, idx2;
        while (idx1 < line1.length() && Block.white(line1.charAt(idx1))) idx1++;
        for (idx2 = 0; idx2 < l2.length(); idx2++) {
            if (!Block.white(line2.charAt(idx2))) {
                if (idx1 >= line1.length() || line1.charAt(idx1++) != line2.charAt(idx2)) break;
            } else while (idx1 < line1.length() && Block.white(line1.charAt(idx1))) idx1++;
        }
        return idx2 == l2.length() && idx1 == l1.length();
    }

    // --- format_lines ---

    private void formatLines(int firstLine, int lineCount) {
        int maxLen = tw * 3;
        int lnum = firstLine;
        Leader leader = Leader.NONE, next;
        boolean isNotPar = lnum > 0 ? notParagraph(lnum - 1, leader = leader(b.line(lnum - 1))) : true;
        next = leader(b.line(lnum));
        boolean nextIsNotPar = notParagraph(lnum, next);
        boolean isEndPar = isNotPar || nextIsNotPar;
        boolean advance = true, needSetIndent = true, forceFormat = false;
        lnum--;
        for (int count = lineCount; count != 0; count--) {
            if (advance) {
                lnum++;
                isNotPar = nextIsNotPar;
                leader = next;
            }
            if (count == 1 || lnum == b.count() - 1) {
                nextIsNotPar = true;
                next = Leader.NONE;
            } else {
                next = leader(b.line(lnum + 1));
                nextIsNotPar = notParagraph(lnum + 1, next);
            }
            advance = true;
            isEndPar = isNotPar || nextIsNotPar;
            if (isNotPar) continue;
            if (lnum >= b.count() - 1 || !sameLeader(lnum, leader, next)) isEndPar = true;
            if (isEndPar || forceFormat) {
                if (needSetIndent) setIndent(lnum, Text.indent(b.line(lnum), ts));
                String line = b.line(lnum);
                int col = line.isEmpty() ? 0 : Text.last(line);
                while (col > 0 && Character.isWhitespace(line.charAt(col))) col = Text.previous(line, col);
                c.editor.setCursor(new Pos(lnum, col), true);
                lnum = internalFormat(lnum);
                needSetIndent = isEndPar;
                forceFormat = false;
            }
            if (!isEndPar) {
                advance = false;
                if (next.length() > 0) b.remove(b.start(lnum + 1), b.start(lnum + 1) + next.length());
                NormalCommands.joinLines(c, lnum, 2, true);
                forceFormat = b.line(lnum).length() > maxLen;
            }
        }
        c.editor.setCursor(new Pos(Math.max(0, Math.min(lnum, b.count() - 1)), c.cursor().col), true);
    }

    /** set_indent with SIN_CHANGED: the indent is rebuilt for 'expandtab' when it differs. */
    private void setIndent(int line, int width) {
        String text = b.line(line);
        int body = Text.firstNonBlank(text);
        String indent = Text.makeIndent(width, ts, expandtab);
        if (!text.substring(0, body).equals(indent)) b.setLine(line, indent + text.substring(body));
    }

    /**
     * internal_format with the cursor on the last non-blank of line {@code lnum}: breaks the line at the last white
     * space before 'textwidth', again on the new line, until it fits.
     *
     * @return the line the cursor ends on
     */
    private int internalFormat(int lnum) {
        boolean noLeader = false, comments = doComments;
        while (true) {
            Pos cursor = c.cursor();
            String line = b.line(cursor.line);
            int virtcol = Text.virtcol(line, cursor.col, ts) + (cursor.col < line.length() ? Text.width(line, cursor.col, Text.virtcol(line, cursor.col, ts), ts) : 1);
            if (virtcol <= tw) break;
            if (noLeader) comments = false;
            Leader leader = comments ? leader(line) : Leader.NONE;
            if (leader.length() == 0) noLeader = true;
            int startcol = cursor.col;
            if (startcol == 0) break;
            int wantcol = Text.columnAt(line, tw, ts);
            int col = startcol, foundcol = 0;
            while (true) {
                char cc = col < line.length() ? line.charAt(col) : 0;
                if (cc == ' ' || cc == '\t') {
                    while (col > 0 && (cc == ' ' || cc == '\t')) {
                        col = Text.previous(line, col);
                        cc = line.charAt(col);
                    }
                    if (col == 0 && (cc == ' ' || cc == '\t')) break;
                    if (col < leader.length()) break;
                    col = Text.next(line, col);
                    foundcol = col;
                    if (col <= wantcol) break;
                }
                if (col == 0) break;
                col = Text.previous(line, col);
            }
            if (foundcol == 0) {
                c.editor.setCursor(new Pos(cursor.line, startcol), true);
                break;
            }
            int wordStart = foundcol;
            while (wordStart < line.length() && Block.white(line.charAt(wordStart))) wordStart++;
            startcol = Math.max(0, startcol - wordStart);
            String prefix = newLinePrefix(line, comments ? leader : Leader.NONE);
            String head = line.substring(0, foundcol).stripTrailing();
            String tail = line.substring(wordStart);
            // open_line: the line is split at the break; its trailing white space goes, and the new line gets the
            // comment leader or the indent.
            b.replace(b.start(cursor.line) + head.length(), b.start(cursor.line) + wordStart, "\n" + prefix);
            int newLine = cursor.line + 1;
            int newCol = Math.min(prefix.length() + startcol, b.line(newLine).length());
            c.editor.setCursor(new Pos(newLine, newCol), true);
            lnum = newLine;
            if (tail.isEmpty()) break;
        }
        return c.cursor().line;
    }

    /** open_line's comment leader (and indent with 'autoindent') for the line after a break. */
    private String newLinePrefix(String line, Leader l) {
        int newindent = autoindent ? Text.indent(line, ts) : 0;
        if (l.length() == 0) return Text.makeIndent(newindent, ts, expandtab);
        String flags = flags(l);
        String leader = line.substring(0, l.length());
        String repl = null;
        boolean extraSpace = false, requireBlank = flags.indexOf('b') >= 0;
        int off = 0;
        char adjust = 0;
        for (int i = 0; i < flags.length(); i++) {
            char f = flags.charAt(i);
            if (f == 'r' || f == 'l') adjust = f;
            else if (Character.isDigit(f) || f == '-') {
                int j = i + 1;
                while (j < flags.length() && Character.isDigit(flags.charAt(j))) j++;
                off = Integer.parseInt(flags.substring(i, j));
                i = j - 1;
            }
        }
        if (flags.indexOf('s') >= 0 || flags.indexOf('m') >= 0) {
            int k = l.part();
            if (flags.indexOf('s') >= 0) {
                String middle = k + 1 < comments.size() ? comments.get(k + 1)[1] : "";
                repl = middle;
                requireBlank = k + 1 < comments.size() && comments.get(k + 1)[0].indexOf('b') >= 0;
            }
            if (!Block.white(leader.charAt(leader.length() - 1)) && requireBlank) extraSpace = true;
        } else if (flags.indexOf('f') >= 0) repl = "";
        if (repl != null) {
            int start = 0;
            while (start < leader.length() && Block.white(leader.charAt(start))) start++;
            StringBuilder p = new StringBuilder(leader);
            if (adjust == 'r') {
                int end = leader.length();
                while (end > start && Block.white(leader.charAt(end - 1))) end--;
                int from = Math.max(start, end - repl.length());
                p.replace(from, end, repl);
                for (int i = 0; i < from; i++) if (!Block.white(p.charAt(i))) p.setCharAt(i, ' ');
            } else {
                int fit = 0;
                String rest = leader.substring(start);
                while (fit < rest.length() && fit + 1 <= repl.length()) fit++;
                p.replace(start, start + fit, repl);
                for (int i = start + repl.length(); i < p.length(); i++) if (!Block.white(p.charAt(i))) p.setCharAt(i, ' ');
            }
            leader = p.toString();
            if (autoindent) newindent = Text.indent(leader, ts);
            if (newindent + off < 0) {
                off = -newindent;
                newindent = 0;
            } else newindent += off;
            while (off > 0 && !leader.isEmpty() && leader.charAt(leader.length() - 1) == ' ' && leader.strip().indexOf('\t') < 0) {
                leader = leader.substring(0, leader.length() - 1);
                off--;
            }
            if (!leader.isEmpty() && Block.white(leader.charAt(leader.length() - 1))) extraSpace = false;
        }
        if (extraSpace) leader += " ";
        if (newindent > 0) return Text.makeIndent(newindent, ts, expandtab) + leader.stripLeading();
        return leader;
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Highlights.java`:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import org.fife.ui.rtextarea.SmartHighlightPainter;

/**
 * What Vim draws on the text (spec 5.1): the Visual area (charwise, linewise or blockwise) in the theme's selection
 * colour, 'hlsearch' matches in its mark-all colour, and the incremental-search match in the selection colour. The
 * text area's own selection stays empty in Vim mode, so these do not move the caret.
 */
final class Highlights {
    private final VimTextArea area;
    private final List<Object> tags = new ArrayList<>();
    private List<int[]> shown = List.of();

    Highlights(VimTextArea area) { this.area = area; }

    /** Draws the ranges ({@code [kind, start, end)} with kind 0 for Visual and incsearch, 1 for 'hlsearch'). */
    void show(List<int[]> ranges) {
        if (same(ranges)) return;
        clear();
        Highlighter highlighter = area.getHighlighter();
        if (highlighter == null) return;
        var selection = new SmartHighlightPainter(area.getSelectionColor());
        var search = new SmartHighlightPainter(area.getMarkAllHighlightColor());
        int length = area.getDocument().getLength();
        for (int[] range : ranges) {
            int start = Math.min(range[1], length), end = Math.min(range[2], length);
            if (end <= start) continue;
            try { tags.add(highlighter.addHighlight(start, end, range[0] == 0 ? selection : search)); }
            catch (BadLocationException ignored) { }
        }
        shown = ranges;
    }

    private boolean same(List<int[]> ranges) {
        if (ranges.size() != shown.size()) return false;
        for (int i = 0; i < ranges.size(); i++) if (!java.util.Arrays.equals(ranges.get(i), shown.get(i))) return false;
        return true;
    }

    void clear() {
        Highlighter highlighter = area.getHighlighter();
        if (highlighter != null) for (Object tag : tags) highlighter.removeHighlight(tag);
        tags.clear();
        shown = List.of();
    }

    /** @return the drawn ranges, for tests */
    List<int[]> shown() { return shown; }

    /**
     * The Visual area as text ranges: a charwise area includes the character under its end (and the line break
     * when that end is past the last character); a linewise area whole lines; a block each line's part.
     */
    static List<int[]> visual(VimEditor editor) {
        List<int[]> out = new ArrayList<>();
        Pos anchor = editor.visualAnchor();
        Mode mode = editor.mode();
        if (anchor == null || !mode.visual()) return out;
        Buffer b = editor.buffer();
        Pos cursor = editor.cursor();
        Pos start = Pos.min(anchor, cursor), end = Pos.max(anchor, cursor);
        switch (mode) {
            case VISUAL -> {
                String last = b.line(end.line);
                int to = end.col < last.length() ? b.offset(end.line, Text.next(last, end.col)) : b.end(end.line) + 1;
                out.add(new int[] { 0, b.offset(start), to });
            }
            case VISUAL_LINE -> out.add(new int[] { 0, b.start(start.line), b.end(end.line) + 1 });
            default -> {
                int ts = editor.options().number("tabstop");
                int left = Math.min(Visual.startVcol(b, anchor, ts), Visual.startVcol(b, cursor, ts));
                int right = Math.max(Visual.endVcol(b, anchor, ts), Visual.endVcol(b, cursor, ts));
                boolean max = editor.curswant() == Visual.MAX;
                for (int line = start.line; line <= end.line; line++) {
                    String text = b.line(line);
                    int to = max ? Integer.MAX_VALUE / 2 : right;
                    Block bd = Block.prep(text, left, to, ts, true, Block.Op.OTHER, true);
                    if (bd.isShort && bd.textlen == 0) continue;
                    int from = b.offset(line, Math.min(bd.textcol, text.length()));
                    int until = b.offset(line, Math.min(text.length(), bd.textcol + bd.textlen));
                    if (until > from) out.add(new int[] { 0, from, until });
                }
            }
        }
        return out;
    }
}
```

- [ ] **Step 4: Replace the changed classes**

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/package-info.java` with:

```java
/**
 * The Vim engine (spec docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md): keys, modes, motions, text
 * objects, operators, Visual mode, search, marks, registers, undo steps and {@code .}, on an RSyntaxTextArea, with no
 * Swing UI of its own beyond the highlights it draws on the text. UI thread only. May depend on:
 * {@code dev.jasper.editor.vim.regex}, RSyntaxTextArea, the JDK.
 */
package dev.jasper.editor.vim;
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Mode.java` with:

```java
package dev.jasper.editor.vim;

/** The mode Vim reports (spec 3.2); it drives the caret, the handler rule and the Vim bar. */
public enum Mode {
    /** Normal mode. */
    NORMAL("normal", ""),
    /** An operator waits for its motion. */
    OPERATOR_PENDING("operator-pending", ""),
    /** Insert mode. */
    INSERT("insert", "-- INSERT --"),
    /** Replace mode ({@code R}). */
    REPLACE("replace", "-- REPLACE --"),
    /** One Normal-mode command from Insert mode ({@code i_CTRL-O}). */
    INSERT_NORMAL("insert-normal", "-- (insert) --"),
    /** Characterwise Visual mode ({@code v}). */
    VISUAL("visual", "-- VISUAL --"),
    /** Linewise Visual mode ({@code V}). */
    VISUAL_LINE("visual-line", "-- VISUAL LINE --"),
    /** Blockwise Visual mode ({@code CTRL-V}). */
    VISUAL_BLOCK("visual-block", "-- VISUAL BLOCK --"),
    /** A command line is being typed ({@code /}, {@code ?}). */
    COMMAND_LINE("command-line", "");

    private final String id, label;

    Mode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** @return the name the corpus uses, such as {@code insert} */
    public String id() { return id; }

    /** @return what the Vim bar shows with 'showmode', such as {@code -- INSERT --}; empty for Normal mode */
    public String label() { return label; }

    /** @return whether typed characters go into the text */
    public boolean inserting() { return this == INSERT || this == REPLACE; }

    /** @return whether this is one of the Visual modes */
    public boolean visual() { return this == VISUAL || this == VISUAL_LINE || this == VISUAL_BLOCK; }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Keywords.java` with:

```java
package dev.jasper.editor.vim;

/**
 * Word characters from 'iskeyword' and Vim's character classes (mbyte.c's utf_class): 0 for blanks, 1 for
 * punctuation, 2 for word characters, and higher classes for emoji and CJK scripts, so {@code w} stops between them.
 */
final class Keywords {
    private final boolean[] table = new boolean[256];

    /**
     * @param spec an 'iskeyword' value such as {@code @,48-57,_,192-255}
     * @return its word characters
     * @throws IllegalArgumentException for a malformed value (E474)
     */
    static Keywords parse(String spec) {
        var keywords = new Keywords();
        int i = 0;
        while (i < spec.length()) {
            boolean exclude = spec.charAt(i) == '^' && i + 1 < spec.length();
            if (exclude) i++;
            int from, to;
            if (spec.startsWith("@-@", i)) {
                from = to = '@';
                i += 3;
            } else if (spec.charAt(i) == '@' && (i + 1 == spec.length() || spec.charAt(i + 1) == ',')) {
                for (int c = 'A'; c <= 'Z'; c++) keywords.table[c] = !exclude;
                for (int c = 'a'; c <= 'z'; c++) keywords.table[c] = !exclude;
                i++;
                i = skipComma(spec, i);
                continue;
            } else {
                int[] first = item(spec, i);
                from = first[0];
                i = first[1];
                to = from;
                if (i < spec.length() && spec.charAt(i) == '-' && i + 1 < spec.length()) {
                    int[] second = item(spec, i + 1);
                    to = second[0];
                    i = second[1];
                }
            }
            if (from < 0 || to > 255 || from > to) throw new IllegalArgumentException("E474: Invalid argument: iskeyword=" + spec);
            for (int c = from; c <= to; c++) keywords.table[c] = !exclude;
            i = skipComma(spec, i);
        }
        return keywords;
    }

    /** @return a Java character-class body matching the same characters, for patterns ({@code \k}, {@code \<}) */
    String javaClass() {
        StringBuilder out = new StringBuilder();
        for (int c = 0; c < 256; c++) {
            if (!table[c]) continue;
            int end = c;
            while (end + 1 < 256 && table[end + 1]) end++;
            out.append(String.format("\\x{%x}", c));
            if (end > c) out.append(String.format("-\\x{%x}", end));
            c = end;
        }
        return out + dev.jasper.editor.vim.regex.VimRegex.WIDE;
    }

    /** @return whether Vim treats the character as a keyword character */
    boolean isWord(int c) { return c < 256 ? table[c] : charClass(c) >= 2; }

    /** @return Vim's class for the character: 0 blank, 1 punctuation, 2 word, 3 emoji, larger for CJK scripts */
    int charClass(int c) {
        if (c == ' ' || c == '\t' || c == 0 || c == 0xa0 || c == 0x3000) return 0;
        if (c < 0x100) return table[c] ? 2 : 1;
        if (c >= 0x1f000 && c <= 0x1faff || c >= 0x2600 && c <= 0x27bf) return 3;
        if (c >= 0x3040 && c <= 0x309f) return 0x3040;
        if (c >= 0x30a0 && c <= 0x30ff) return 0x30a0;
        if (c >= 0x4e00 && c <= 0x9fff || c >= 0x3400 && c <= 0x4dbf || c >= 0x20000 && c <= 0x2ffff) return 0x4e00;
        if (c >= 0xac00 && c <= 0xd7a3) return 0xac00;
        if (Character.isWhitespace(c)) return 0;
        return Character.isLetterOrDigit(c) || Character.getType(c) == Character.NON_SPACING_MARK ? 2 : 1;
    }

    private static int skipComma(String spec, int i) {
        if (i < spec.length() && spec.charAt(i) == ',') return i + 1;
        if (i < spec.length()) throw new IllegalArgumentException("E474: Invalid argument: iskeyword=" + spec);
        return i;
    }

    /** @return {value, index after it}: a decimal number or one character */
    private static int[] item(String spec, int i) {
        if (Character.isDigit(spec.charAt(i))) {
            int end = i;
            while (end < spec.length() && Character.isDigit(spec.charAt(end))) end++;
            return new int[] {Integer.parseInt(spec.substring(i, end)), end};
        }
        return new int[] {spec.charAt(i), i + 1};
    }
}
```

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
 * ({@link EditorDefaults}) and 'showcmd' and 'incsearch', which are on (spec 3.8; 'hlsearch' stays off until plan V2
 * brings {@code :noh}).
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
        new Def("formatoptions", "fo", Type.STRING, "tcq", true),
        new Def("hlsearch", "hls", Type.BOOLEAN, false, false),
        new Def("ignorecase", "ic", Type.BOOLEAN, false, false),
        new Def("incsearch", "is", Type.BOOLEAN, true, false),
        new Def("iskeyword", "isk", Type.STRING, "@,48-57,_,192-255", true),
        new Def("joinspaces", "js", Type.BOOLEAN, true, false),
        new Def("matchpairs", "mps", Type.STRING, "(:),{:},[:]", true),
        new Def("nrformats", "nf", Type.STRING, "bin,octal,hex", true),
        new Def("report", "report", Type.NUMBER, 2, false),
        new Def("shiftround", "sr", Type.BOOLEAN, false, false),
        new Def("shiftwidth", "sw", Type.NUMBER, 8, true),
        new Def("showcmd", "sc", Type.BOOLEAN, true, false),
        new Def("showmode", "smd", Type.BOOLEAN, true, false),
        new Def("smartcase", "scs", Type.BOOLEAN, false, false),
        new Def("softtabstop", "sts", Type.NUMBER, 0, true),
        new Def("startofline", "sol", Type.BOOLEAN, true, false),
        new Def("tabstop", "ts", Type.NUMBER, 8, true),
        new Def("textwidth", "tw", Type.NUMBER, 0, true),
        new Def("tildeop", "top", Type.BOOLEAN, false, false),
        new Def("whichwrap", "ww", Type.STRING, "b,s", false),
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

    /**
     * Applies {@code :set} arguments: {@code name}, {@code noname}, {@code invname}, {@code name!}, {@code name&},
     * {@code name=value} and {@code name:value}, separated by spaces.
     *
     * @throws IllegalArgumentException with Vim's message for an unknown option or a bad value
     */
    void apply(String arguments) {
        for (String argument : arguments.trim().split("\\s+")) {
            if (argument.isEmpty()) continue;
            int equals = indexOfAny(argument, "=:");
            if (equals > 0) {
                char how = "+-^".indexOf(argument.charAt(equals - 1)) >= 0 && argument.charAt(equals) == '=' ? argument.charAt(equals - 1) : '=';
                String name = argument.substring(0, how == '=' ? equals : equals - 1);
                String text = argument.substring(equals + 1);
                Def def = required(name);
                switch (def.type()) {
                    case NUMBER -> {
                        int number;
                        try { number = Integer.parseInt(text); }
                        catch (NumberFormatException bad) { throw new IllegalArgumentException("E521: Number required after =: " + argument); }
                        int old = number(def.name());
                        set(def.name(), switch (how) { case '+' -> old + number; case '-' -> old - number; case '^' -> old * number; default -> number; });
                    }
                    case STRING -> set(def.name(), combine(string(def.name()), text, how));
                    case BOOLEAN -> throw new IllegalArgumentException("E474: Invalid argument: " + argument);
                }
            } else if (argument.endsWith("&")) {
                Def def = required(argument.substring(0, argument.length() - 1));
                set(def.name(), def.fallback());
            } else if (argument.endsWith("!")) {
                Def def = required(argument.substring(0, argument.length() - 1));
                booleanOnly(def, argument);
                set(def.name(), !flag(def.name()));
            } else if (def(argument).isPresent()) {
                Def def = def(argument).get();
                booleanOnly(def, argument);
                set(def.name(), true);
            } else if (argument.startsWith("no") && def(argument.substring(2)).isPresent()) {
                Def def = def(argument.substring(2)).get();
                booleanOnly(def, argument);
                set(def.name(), false);
            } else if (argument.startsWith("inv") && def(argument.substring(3)).isPresent()) {
                Def def = def(argument.substring(3)).get();
                booleanOnly(def, argument);
                set(def.name(), !flag(def.name()));
            } else throw new IllegalArgumentException("E518: Unknown option: " + argument);
        }
    }

    /** {@code :set opt+=}, {@code -=} and {@code ^=} on a comma-separated option (option.c's add, remove and prepend). */
    private static String combine(String old, String item, char how) {
        List<String> items = new ArrayList<>(old.isEmpty() ? List.of() : List.of(old.split(",", -1)));
        switch (how) {
            case '+' -> {
                if (items.contains(item)) return old;
                items.add(item);
            }
            case '^' -> {
                if (items.contains(item)) return old;
                items.addFirst(item);
            }
            case '-' -> items.remove(item);
            default -> { return item; }
        }
        return String.join(",", items);
    }

    static Optional<Def> def(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return DEFS.stream().filter(def -> def.name().equals(lower) || def.abbreviation().equals(lower)).findFirst();
    }

    private void put(Def def, Object value) {
        Object old = values.put(def.name(), value);
        if (!value.equals(old)) for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private static Def required(String name) {
        return def(name).orElseThrow(() -> new IllegalArgumentException("E518: Unknown option: " + name));
    }

    private static void booleanOnly(Def def, String argument) {
        if (def.type() != Type.BOOLEAN) throw new IllegalArgumentException("E474: Invalid argument: " + argument);
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

    private static int indexOfAny(String text, String characters) {
        for (int i = 0; i < text.length(); i++) if (characters.indexOf(text.charAt(i)) >= 0) return i;
        return -1;
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Buffer.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;

/**
 * The text as Vim sees it: lines without their newline, where a final newline ends the last line rather than
 * starting an empty one ({@code "a\nb\n"} is two lines, as a file is in Vim). An empty document is one empty line.
 * Offsets are the document's; lines and columns are 0-based. Every edit goes through the document, so the undo
 * manager sees it.
 */
final class Buffer {
    private final Document document;
    private final Element root;

    Buffer(Document document) {
        this.document = document;
        this.root = document.getDefaultRootElement();
    }

    /** Nothing to release: the buffer reads the document's own line elements. */
    void close() { }

    Document document() { return document; }

    /** @return the whole text, including a final newline */
    String text() {
        try { return document.getText(0, document.getLength()); }
        catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
    }

    int length() { return document.getLength(); }

    /** @return the text from {@code from} to {@code to} */
    String text(int from, int to) {
        try { return document.getText(from, to - from); }
        catch (BadLocationException bad) { throw new IllegalStateException(bad); }
    }

    /** @return the character at {@code offset}, or -1 outside the text */
    int charAt(int offset) {
        if (offset < 0 || offset >= document.getLength()) return -1;
        return text(offset, offset + 1).charAt(0);
    }

    /**
     * @return the number of lines, at least 1: the document's line elements, less the empty one after a final
     *     newline
     */
    int count() {
        int elements = root.getElementCount();
        int length = document.getLength();
        return elements > 1 && length > 0 && charAt(length - 1) == '\n' ? elements - 1 : elements;
    }

    String line(int line) { return text(start(line), end(line)); }

    /** @return the offset of the line's first character */
    int start(int line) {
        check(line);
        return root.getElement(line).getStartOffset();
    }

    /** @return the offset just after the line's last character (its newline, or the end of the text) */
    int end(int line) {
        check(line);
        // A line element ends after its newline; the last one after the document's implied final break.
        return root.getElement(line).getEndOffset() - 1;
    }

    int offset(Pos pos) { return start(pos.line) + pos.col; }

    int offset(int line, int col) { return start(line) + col; }

    /** @return the line holding {@code offset}; an offset past the last line is on the last line */
    int lineOf(int offset) {
        int line = root.getElementIndex(Math.max(0, offset));
        return Math.max(0, Math.min(line, count() - 1));
    }

    Pos pos(int offset) {
        int line = lineOf(offset);
        int start = start(line);
        return new Pos(line, Math.min(Math.max(0, offset - start), end(line) - start));
    }

    /** @return lines {@code first} to {@code last}, inclusive */
    List<String> lines(int first, int last) {
        List<String> lines = new ArrayList<>(last - first + 1);
        for (int line = first; line <= last; line++) lines.add(line(line));
        return List.copyOf(lines);
    }

    void insert(int offset, String text) {
        if (text.isEmpty()) return;
        try { document.insertString(offset, text, null); }
        catch (BadLocationException bad) { throw new IllegalStateException(bad); }
    }

    void remove(int from, int to) {
        if (to <= from) return;
        try { document.remove(from, to - from); }
        catch (BadLocationException bad) { throw new IllegalStateException(bad); }
    }

    void replace(int from, int to, String text) {
        if (to <= from) {
            insert(from, text);
            return;
        }
        try {
            if (document instanceof AbstractDocument abstractDocument) abstractDocument.replace(from, to - from, text, null);
            else {
                document.remove(from, to - from);
                document.insertString(from, text, null);
            }
        } catch (BadLocationException bad) { throw new IllegalStateException(bad); }
    }

    /**
     * Replaces one line's text, leaving its newline. Only the part that differs is replaced, so the change starts
     * where Vim's does (for the {@code '.} mark) and the undo holds only the change.
     */
    void setLine(int line, String text) {
        String old = line(line);
        if (text.equals(old)) return;
        int prefix = 0, max = Math.min(old.length(), text.length());
        while (prefix < max && old.charAt(prefix) == text.charAt(prefix)) prefix++;
        if (prefix > 0 && Character.isHighSurrogate(old.charAt(prefix - 1))) prefix--;
        int suffix = 0;
        while (suffix < max - prefix && old.charAt(old.length() - 1 - suffix) == text.charAt(text.length() - 1 - suffix)) suffix++;
        if (suffix > 0 && Character.isLowSurrogate(old.charAt(old.length() - suffix))) suffix--;
        int start = start(line);
        replace(start + prefix, start + old.length() - suffix, text.substring(prefix, text.length() - suffix));
    }

    /** Inserts whole lines before {@code line}; {@code line == count()} appends after the last line. */
    void insertLines(int line, List<String> text) {
        String block = String.join("\n", text);
        if (line < count()) {
            insert(start(line), block + "\n");
            return;
        }
        int length = length();
        boolean newline = length > 0 && charAt(length - 1) == '\n';
        insert(length, newline ? block + "\n" : "\n" + block);
    }

    /** Deletes lines {@code first} to {@code last}, keeping a final newline; deleting every line empties the text. */
    void deleteLines(int first, int last) {
        int count = count();
        if (first == 0 && last == count - 1) remove(0, length());
        else if (last < count - 1) remove(start(first), start(last + 1));
        else remove(end(first - 1), end(last));
    }

    private void check(int line) {
        if (line < 0 || line >= count()) throw new ArrayIndexOutOfBoundsException("Line " + line + " of " + count());
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
        return !writing && name == '.';
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

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Span.java` with:

```java
package dev.jasper.editor.vim;

/**
 * The text an operator acts on (Vim's oap->start, oap->end, motion_type and inclusive, block_mode and its
 * display columns).
 *
 * @param start the first position
 * @param end the last position: included when {@code kind} is inclusive, excluded when exclusive, and for linewise
 *     spans only its line counts
 * @param kind exclusive, inclusive or linewise
 * @param regOne whether a delete always writes register 1
 * @param block for a Visual block, its display columns; null otherwise
 * @param visual whether the span is a Visual area (Vim's is_VIsual)
 * @param endAdjusted whether an exclusive motion's end at column 0 moved back to the line above (Vim's
 *     end_adjusted; {@code gq} then leaves the cursor a line lower)
 */
record Span(Pos start, Pos end, Target.Kind kind, boolean regOne, Columns block, boolean visual, boolean endAdjusted) {
    /**
     * A block's columns (Vim's start_vcol and end_vcol).
     *
     * @param startVcol the first display column
     * @param endVcol the last display column, inclusive
     * @param max whether {@code $} made each line's block reach its end
     */
    record Columns(int startVcol, int endVcol, boolean max) { }

    Span(Pos start, Pos end, Target.Kind kind, boolean regOne, Columns block, boolean visual) { this(start, end, kind, regOne, block, visual, false); }

    Span(Pos start, Pos end, Target.Kind kind, boolean regOne) { this(start, end, kind, regOne, null, false, false); }

    Span(Pos start, Pos end, Target.Kind kind) { this(start, end, kind, false, null, false, false); }

    boolean linewise() { return kind == Target.Kind.LINEWISE; }

    boolean blockwise() { return block != null; }

    int lineCount() { return end.line - start.line + 1; }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Context.java` with:

```java
package dev.jasper.editor.vim;

/** What one command runs with: the editor, its count, register and argument, and the operator waiting for it. */
final class Context {
    final VimEditor editor;
    final Buffer buffer;
    final Options options;
    final Registers registers;
    final Vim vim;
    /** The count, 1 when none was typed; after an operator, the product of both counts. */
    int count = 1;
    boolean hasCount;
    /** The register typed with {@code "x}, or 0. */
    char register;
    /** The key read as the command's argument ({@code f}'s character), or null. */
    KeyInput argument;
    /** The pending operator's keys ({@code d}, {@code gU}), or null in Normal mode. */
    String operator;
    /** The forced motion type after the operator: {@code v}, {@code V}, or 0. */
    char forced;
    /** In Visual mode, the other end of the area (Vim's VIsual); null otherwise. */
    Pos visual;
    /** The command line typed for {@code /} and {@code ?}, or null. */
    String line;
    /** For an operator, where the cursor was when it started (Vim's oap->cursor_start, for {@code gw}). */
    Pos cursorStart;

    Context(VimEditor editor) {
        this.editor = editor;
        this.buffer = editor.buffer();
        this.options = editor.options();
        this.registers = editor.vim().registers();
        this.vim = editor.vim();
    }

    /** @return a copy of the cursor position */
    Pos cursor() { return editor.cursor(); }

    Walk walk(boolean bigword) {
        var walk = new Walk(buffer, options.keywords());
        walk.bigword = bigword;
        return walk;
    }

    /** @return the argument's character, or -1 when the argument is a named key or has modifiers */
    int argumentChar() { return argument == null ? -1 : argument.plainChar(); }

    int tabstop() { return options.number("tabstop"); }

    /** @return whether Visual mode is active, where the cursor may sit on the end of a line (Vim's past_line) */
    boolean inVisual() { return visual != null; }

    static VimFailure fail() { return VimFailure.bell(); }
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
    /** Whether the last hex number CTRL-A changed had upper-case letters (do_addsub's static hexupper). */
    private boolean hexUpper;
    /** The uppercase marks: each remembers its editor (Vim's namedfm). */
    private final java.util.Map<Character, GlobalMark> globalMarks = new java.util.HashMap<>();

    private record GlobalMark(VimEditor editor, Pos pos) { }

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

    /** An editor closed: its uppercase marks go. */
    void forget(VimEditor editor) { globalMarks.values().removeIf(mark -> mark.editor() == editor); }

    /** @return the last search pattern, direction and offset, and the search history */
    Search search() { return search; }

    void lastChange(Change change) { lastChange = change; }
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

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Motions.java` with:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Target.Column;

/**
 * Normal-mode and operator-pending motions (spec 4.1), ported from Vim's normal.c and search.c so that where they
 * land, whether they fail, and how an operator treats them match Vim. A motion returns null to fail.
 */
final class Motions {
    private static final String PARAGRAPHS = "IPLPPPQPP TPHPLIPpLpItpplpipbp", SECTIONS = "SHNHH HUnhsh";

    private Motions() { }

    static void register(VimCommands commands) {
        commands.motion("h", "left", c -> left(c, false));
        commands.motion("<Left>", "left", c -> left(c, false));
        commands.motion("<BS>", "left, wrapping to the previous line ('whichwrap' b)", c -> left(c, true));
        commands.motion("<C-h>", "left, wrapping like BS", c -> left(c, true));
        commands.motion("l", "right", c -> right(c, 'l'));
        commands.motion("<Right>", "right", c -> right(c, '>'));
        commands.motion(" ", "right, wrapping to the next line ('whichwrap' s)", c -> right(c, 's'));
        commands.motion("j", "down", c -> down(c, c.count));
        commands.motion("<Down>", "down", c -> down(c, c.count));
        commands.motion("<C-j>", "down", c -> down(c, c.count));
        commands.motion("<C-n>", "down", c -> down(c, c.count));
        commands.motion("k", "up", c -> up(c, c.count));
        commands.motion("<Up>", "up", c -> up(c, c.count));
        commands.motion("<C-p>", "up", c -> up(c, c.count));
        commands.motion("+", "down to the first non-blank", c -> firstNonBlank(c, down(c, c.count)));
        commands.motion("<CR>", "down to the first non-blank", c -> firstNonBlank(c, down(c, c.count)));
        commands.motion("-", "up to the first non-blank", c -> firstNonBlank(c, up(c, c.count)));
        commands.motion("_", "count - 1 lines down, first non-blank", c -> firstNonBlank(c, c.count == 1 ? Target.linewise(c.cursor(), Column.KEEP) : down(c, c.count - 1)));
        commands.motion("G", "to the last line, or line {count}", c -> goToLine(c, c.hasCount ? c.count : c.buffer.count()));
        commands.motion("gg", "to the first line, or line {count}", c -> goToLine(c, c.hasCount ? c.count : 1));
        commands.motion("0", "to the first column", c -> Target.exclusive(new Pos(c.cursor().line, 0)));
        commands.motion("<Home>", "to the first column", c -> Target.exclusive(new Pos(c.cursor().line, 0)));
        commands.motion("^", "to the first non-blank", c -> Target.exclusive(new Pos(c.cursor().line, Text.firstNonBlank(c.buffer.line(c.cursor().line)))));
        commands.motion("$", "to the end of the line, {count} - 1 lines down", Motions::endOfLine);
        commands.motion("<End>", "to the end of the line", Motions::endOfLine);
        commands.motion("g_", "to the last non-blank, {count} - 1 lines down", Motions::lastNonBlank);
        commands.motion("|", "to display column {count}", c -> Target.exclusive(new Pos(c.cursor().line, Text.columnAt(c.buffer.line(c.cursor().line), c.count - 1, c.tabstop()))));
        commands.motion("go", "to character {count} of the text", Motions::goToOffset);
        commands.motion("w", "to the start of the next word", c -> word(c, false, false));
        commands.motion("W", "to the start of the next WORD", c -> word(c, true, false));
        commands.motion("e", "to the end of the word", c -> word(c, false, true));
        commands.motion("E", "to the end of the WORD", c -> word(c, true, true));
        commands.motion("b", "back to the start of the word", c -> backWord(c, false));
        commands.motion("B", "back to the start of the WORD", c -> backWord(c, true));
        commands.motion("ge", "back to the end of the previous word", c -> backEnd(c, false));
        commands.motion("gE", "back to the end of the previous WORD", c -> backEnd(c, true));
        commands.motionWithChar("f", "to the {count}-th {char} to the right", c -> find(c, true, false, false, false));
        commands.motionWithChar("F", "to the {count}-th {char} to the left", c -> find(c, false, false, false, false));
        commands.motionWithChar("t", "till before the {count}-th {char} to the right", c -> find(c, true, true, false, false));
        commands.motionWithChar("T", "till after the {count}-th {char} to the left", c -> find(c, false, true, false, false));
        commands.motion(";", "repeat the last f, F, t or T", c -> find(c, true, false, true, false));
        commands.motion(",", "repeat the last f, F, t or T the other way", c -> find(c, true, false, true, true));
        commands.motion("(", "back {count} sentences", c -> sentence(c, false));
        commands.motion(")", "forward {count} sentences", c -> sentence(c, true));
        commands.motion("{", "back {count} paragraphs", c -> paragraph(c, false, 0, false));
        commands.motion("}", "forward {count} paragraphs", c -> paragraph(c, true, 0, false));
        commands.motion("[[", "back to a { in column 1", c -> section(c, false, '{'));
        commands.motion("]]", "forward to a { in column 1", c -> section(c, true, '{'));
        commands.motion("[]", "back to a } in column 1", c -> section(c, false, '}'));
        commands.motion("][", "forward to a } in column 1", c -> section(c, true, '}'));
        commands.motion("%", "to the matching bracket, or to {count} percent of the text", Motions::percent);
        commands.motion("[(", "back to the unmatched (", c -> unmatched(c, '(', ')', true));
        commands.motion("[{", "back to the unmatched {", c -> unmatched(c, '{', '}', true));
        commands.motion("])", "forward to the unmatched )", c -> unmatched(c, '(', ')', false));
        commands.motion("]}", "forward to the unmatched }", c -> unmatched(c, '{', '}', false));
    }

    /** nv_left: fails only when it could not move at all and no operator waits. */
    static Target left(Context c, boolean backspace) {
        Pos p = c.cursor();
        boolean wrap = backspace && c.options.has("whichwrap", "b");
        boolean moved = false, noAdjust = false;
        for (int n = c.count; n > 0; n--) {
            if (p.col > 0) {
                p.col = Text.previous(c.buffer.line(p.line), p.col);
                moved = true;
            } else if (wrap && p.line > 0) {
                p.line--;
                String line = c.buffer.line(p.line);
                p.col = Text.last(line);
                // Deleting the line break before the first character: Vim puts the cursor past the end of the line above.
                if (("d".equals(c.operator) || "c".equals(c.operator)) && !line.isEmpty()) {
                    p.col = line.length();
                    noAdjust = true;
                }
                moved = true;
            } else break;
        }
        if (!moved && c.operator == null) return null;
        Target target = Target.exclusive(p);
        return noAdjust ? target.withoutAdjust() : target;
    }

    /** nv_right: with an operator, stopping at the last character makes the motion inclusive. */
    static Target right(Context c, char which) {
        Pos p = c.cursor();
        boolean wraps = c.options.has("whichwrap", String.valueOf(which));
        boolean inclusive = false;
        // In Visual mode l may go onto the end of the line (nv_right's past_line).
        boolean pastLine = c.inVisual();
        for (int n = c.count; n > 0; n--) {
            String line = c.buffer.line(p.line);
            int next = Text.next(line, p.col);
            if (p.col < line.length() && (next < line.length() || pastLine)) {
                p.col = next;
                continue;
            }
            if (wraps && p.line < c.buffer.count() - 1) {
                if (c.operator != null && !inclusive && !line.isEmpty()) inclusive = true;
                else {
                    p.line++;
                    p.col = 0;
                    inclusive = false;
                }
                continue;
            }
            if (c.operator == null) {
                if (n == c.count) return null;
            } else if (!line.isEmpty()) inclusive = true;
            break;
        }
        return inclusive ? Target.inclusive(p) : Target.exclusive(p);
    }

    /** cursor_down: fails only on the last line; a count past the end stops on the last line. */
    static Target down(Context c, int n) {
        Pos p = c.cursor();
        int last = c.buffer.count() - 1;
        if (n > 0 && p.line >= last) return null;
        int line = Math.min(last, p.line + n);
        return Target.linewise(new Pos(line, c.editor.wantedColumn(line)), Column.KEEP);
    }

    static Target up(Context c, int n) {
        Pos p = c.cursor();
        if (n > 0 && p.line == 0) return null;
        int line = Math.max(0, p.line - n);
        return Target.linewise(new Pos(line, c.editor.wantedColumn(line)), Column.KEEP);
    }

    private static Target firstNonBlank(Context c, Target target) {
        if (target == null) return null;
        int line = target.pos().line;
        return Target.linewise(new Pos(line, Text.firstNonBlank(c.buffer.line(line))), Column.RESET);
    }

    /** G, gg and N%: linewise to a line, the first non-blank with 'startofline'. */
    static Target goToLine(Context c, int number) {
        int line = Math.max(0, Math.min(number, c.buffer.count()) - 1);
        boolean sol = c.options.flag("startofline");
        int col = sol ? Text.firstNonBlank(c.buffer.line(line)) : c.editor.wantedColumn(line);
        return Target.linewise(new Pos(line, col), sol ? Column.RESET : Column.KEEP).asJump();
    }

    static Target endOfLine(Context c) {
        Pos p = c.cursor();
        int down = c.count - 1, last = c.buffer.count() - 1;
        if (down > 0 && p.line >= last) return null;
        int line = Math.min(last, p.line + down);
        // In Visual mode the cursor goes onto the end of the line, so the area takes the line break (coladvance).
        String text = c.buffer.line(line);
        return new Target(new Pos(line, c.inVisual() ? text.length() : Text.last(text)), Target.Kind.INCLUSIVE, Column.END, false, false, true);
    }

    static Target lastNonBlank(Context c) {
        Pos p = c.cursor();
        int down = c.count - 1, last = c.buffer.count() - 1;
        if (down > 0 && p.line >= last) return null;
        int line = Math.min(last, p.line + down);
        String text = c.buffer.line(line);
        int col = Text.last(text);
        while (col > 0 && (text.charAt(col) == ' ' || text.charAt(col) == '\t')) col = Text.previous(text, col);
        return Target.inclusive(new Pos(line, col));
    }

    static Target goToOffset(Context c) {
        int offset = Math.max(0, Math.min(c.count - 1, c.buffer.length() - 1));
        return Target.exclusive(c.buffer.pos(Math.max(0, offset))).asJump();
    }

    /** nv_wordcmd: w, W, e and E, with the cw special case and the stop at the end of a line for operators. */
    static Target word(Context c, boolean bigword, boolean end) {
        Pos start = c.cursor(), p = start.copy();
        Walk walk = c.walk(bigword);
        boolean wordEnd = end, stop = false;
        if (!end && "c".equals(c.operator)) {
            int ch = walk.gchar(p);
            if (ch != 0) {
                if (ch != ' ' && ch != '\t') wordEnd = true;
                stop = true;
            }
        }
        boolean ok = wordEnd ? walk.endWord(p, c.count, stop, false) : walk.fwdWord(p, c.count, c.operator != null);
        boolean inclusive = wordEnd;
        // adjust_cursor: never leave the cursor on the end of the line after moving forward.
        if (p.compareTo(start) > 0 && p.col > 0 && walk.gchar(p) == 0) {
            p.col = Text.previous(c.buffer.line(p.line), p.col);
            inclusive = true;
        }
        if (!ok && c.operator == null) return null;
        return inclusive ? Target.inclusive(p) : Target.exclusive(p);
    }

    static Target backWord(Context c, boolean bigword) {
        Pos p = c.cursor();
        return c.walk(bigword).bckWord(p, c.count, false) ? Target.exclusive(p) : null;
    }

    static Target backEnd(Context c, boolean bigword) {
        Pos p = c.cursor();
        return c.walk(bigword).bckendWord(p, c.count, false) ? Target.inclusive(p) : null;
    }

    /** searchc: f, F, t, T, ; and ,. */
    static Target find(Context c, boolean forward, boolean till, boolean repeat, boolean reverse) {
        int ch;
        boolean stop = true;
        if (repeat) {
            Vim.Find last = c.vim.lastFind();
            if (last == null) return null;
            forward = reverse != last.forward();
            till = last.till();
            ch = last.character();
            // Without ';' in 'cpoptions', ";" after t moves at least one character.
            if (c.count == 1 && till) stop = false;
        } else {
            ch = c.argumentChar();
            if (c.argument != null && c.argument.is("<Tab>")) ch = '\t';
            if (ch < 0) return null;
            c.vim.lastFind(new Vim.Find(forward, till, ch));
        }
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        int col = p.col;
        for (int count = c.count; count > 0; count--) {
            while (true) {
                if (forward) {
                    col = Text.next(line, col);
                    if (col >= line.length()) return null;
                } else {
                    if (col == 0) return null;
                    col = Text.previous(line, col);
                }
                if (line.codePointAt(col) == ch && stop) break;
                stop = true;
            }
        }
        if (till) col = forward ? Text.previous(line, col) : Text.next(line, col);
        Pos result = new Pos(p.line, col);
        return forward ? Target.inclusive(result) : Target.exclusive(result);
    }

    /** nv_brace: ( and ). */
    static Target sentence(Context c, boolean forward) {
        Pos p = c.cursor();
        Walk walk = c.walk(false);
        if (!findSentence(walk, p, forward, c.count)) return null;
        boolean inclusive = false;
        if (p.col > 0 && walk.gchar(p) == 0) {
            p.col = Text.previous(c.buffer.line(p.line), p.col);
            inclusive = true;
        }
        return (inclusive ? Target.inclusive(p) : Target.exclusive(p)).usingRegisterOne().asJump();
    }

    /** nv_findpar: { and }. */
    static Target paragraph(Context c, boolean forward, int what, boolean both) {
        Pos p = findParagraph(c.buffer, c.cursor().line, forward, c.count, what, both);
        if (p == null) return null;
        boolean inclusive = p.line == c.buffer.count() - 1 && what != '}' && forward && !c.buffer.line(p.line).isEmpty();
        return (inclusive ? Target.inclusive(p) : Target.exclusive(p)).usingRegisterOne().asJump();
    }

    /** nv_brackets: [[, ]], [] and ][. */
    static Target section(Context c, boolean forward, char what) {
        boolean both = c.operator != null && forward && what == '{';
        Pos p = findParagraph(c.buffer, c.cursor().line, forward, c.count, what, both);
        if (p == null) return null;
        boolean inclusive = p.line == c.buffer.count() - 1 && what != '}' && forward && !c.buffer.line(p.line).isEmpty();
        if (!inclusive && c.operator == null) p.col = Text.firstNonBlank(c.buffer.line(p.line));
        return (inclusive ? Target.inclusive(p) : Target.exclusive(p)).asJump();
    }

    static Target percent(Context c) {
        if (c.hasCount) {
            if (c.count > 100) return null;
            return goToLine(c, (int) ((c.count * (long) c.buffer.count() + 99) / 100)).withColumn(Column.RESET);
        }
        Pos match = Matching.percent(c.buffer, c.cursor(), c.options.string("matchpairs"));
        return match == null ? null : Target.inclusive(match).asJump().usingRegisterOne();
    }

    static Target unmatched(Context c, char open, char close, boolean backward) {
        Pos p = c.cursor();
        for (int n = c.count; n > 0; n--) {
            Pos found = Matching.unmatched(c.buffer, p, open, close, backward, true);
            if (found == null) {
                if (n == c.count) return null;
                break;
            }
            p = found;
        }
        return Target.exclusive(p).asJump();
    }

    /** findpar (textobject.c): the {@code count}-th paragraph or section boundary; null when there are too few. */
    static Pos findParagraph(Buffer buffer, int start, boolean forward, int count, int what, boolean both) {
        int curr = start, dir = forward ? 1 : -1, last = buffer.count() - 1;
        while (count-- > 0) {
            boolean didSkip = false;
            for (boolean first = true; ; first = false) {
                if (!buffer.line(curr).isEmpty()) didSkip = true;
                if (!first && didSkip && startPS(buffer, curr, what, both)) break;
                curr += dir;
                if (curr < 0 || curr > last) {
                    if (count > 0) return null;
                    curr -= dir;
                    break;
                }
            }
        }
        if (both && buffer.line(curr).startsWith("}")) curr = Math.min(curr + 1, last);
        Pos p = new Pos(curr, 0);
        if (curr == last && what != '}' && forward) p.col = Text.last(buffer.line(curr));
        return p;
    }

    /** startPS: a paragraph or section starts at the line (an empty line for {@code para} 0). */
    static boolean startPS(Buffer buffer, int line, int para, boolean both) {
        String s = buffer.line(line);
        int c = s.isEmpty() ? 0 : s.charAt(0);
        if (c == para || c == '\f' || both && c == '}') return true;
        return c == '.' && (inMacro(SECTIONS, s) || para == 0 && inMacro(PARAGRAPHS, s));
    }

    /** inmacro: the nroff macro after the line's leading dot is listed in {@code macros} (two characters each). */
    private static boolean inMacro(String macros, String line) {
        char s0 = line.length() > 1 ? line.charAt(1) : 0, s1 = line.length() > 2 ? line.charAt(2) : 0;
        for (int i = 0; i < macros.length(); i += 2) {
            char m0 = macros.charAt(i), m1 = i + 1 < macros.length() ? macros.charAt(i + 1) : 0;
            boolean first = m0 == s0 || m0 == ' ' && (s0 == 0 || s0 == ' ');
            boolean second = m1 == s1 || (m1 == 0 || m1 == ' ') && (s0 == 0 || s1 == 0 || s1 == ' ');
            if (first && second) return true;
        }
        return false;
    }

    /** findsent (textobject.c). */
    static boolean findSentence(Walk walk, Pos pos, boolean forward, int count) {
        Buffer buffer = walk.buffer();
        boolean noskip = false;
        while (count-- > 0) {
            boolean found = false;
            if (walk.gchar(pos) == 0) {
                do {
                    if ((forward ? walk.incl(pos) : walk.decl(pos)) == Walk.FAIL) break;
                } while (walk.gchar(pos) == 0);
                if (forward) found = true;
            } else if (forward && pos.col == 0 && startPS(buffer, pos.line, 0, false)) {
                if (pos.line == buffer.count() - 1) return false;
                pos.line++;
                found = true;
            } else if (!forward) walk.decl(pos);

            if (!found) {
                boolean foundDot = false;
                int c;
                while ((c = walk.gchar(pos)) == ' ' || c == '\t' || c != 0 && ".!?)]\"'".indexOf(c) >= 0) {
                    Pos t = pos.copy();
                    if (walk.decl(t) == Walk.FAIL || buffer.line(t.line).isEmpty() && forward) break;
                    if (foundDot) break;
                    if (".!?".indexOf(c) >= 0) foundDot = true;
                    int before = walk.gchar(t);
                    if (")]\"'".indexOf(c) >= 0 && (before == 0 || ".!?)]\"'".indexOf(before) < 0)) break;
                    walk.decl(pos);
                }
                int startLine = pos.line;
                while (true) {
                    c = walk.gchar(pos);
                    if (c == 0 || pos.col == 0 && startPS(buffer, pos.line, 0, false)) {
                        if (!forward && pos.line != startLine) {
                            pos.line++;
                            pos.col = Math.min(pos.col, buffer.line(pos.line).length());
                        }
                        break;
                    }
                    if (c == '.' || c == '!' || c == '?') {
                        Pos t = pos.copy();
                        int after;
                        while (true) {
                            if (walk.inc(t) == Walk.FAIL) {
                                after = -1;
                                break;
                            }
                            after = walk.gchar(t);
                            if (after == 0 || ")]\"'".indexOf(after) < 0) break;
                        }
                        if (after == -1 || after == ' ' || after == '\t' || after == 0) {
                            pos.set(t);
                            if (walk.gchar(pos) == 0) walk.inc(pos);
                            break;
                        }
                    }
                    if ((forward ? walk.incl(pos) : walk.decl(pos)) == Walk.FAIL) {
                        if (count > 0) return false;
                        noskip = true;
                        break;
                    }
                }
            }
            while (!noskip && (walk.gchar(pos) == ' ' || walk.gchar(pos) == '\t')) if (walk.incl(pos) == Walk.FAIL) break;
        }
        return true;
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/TextObjects.java` with:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Target.Kind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text objects (spec 4.1), ported from Vim's textobject.c (current_word, current_sent, current_par,
 * current_block, current_quote), with their Visual-mode branches: after an operator an object returns the span
 * it covers; in Visual mode it moves the area's ends and mode itself, as Vim's code does to VIsual, and returns
 * {@link #VISUAL_DONE}. Tags are Jasper's own scanner (Vim's current_tagblock searches with patterns). An object
 * returns null to fail.
 */
final class TextObjects {
    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z][^\\s/>]*)[^>]*?(/?)>");
    /** What an object returns when it has updated the Visual area. */
    static final Span VISUAL_DONE = new Span(new Pos(0, 0), new Pos(0, 0), Kind.INCLUSIVE);

    private TextObjects() { }

    static void register(VimCommands commands) {
        commands.object("iw", "inner word", c -> word(c, false, false));
        commands.object("aw", "a word, with white space", c -> word(c, true, false));
        commands.object("iW", "inner WORD", c -> word(c, false, true));
        commands.object("aW", "a WORD, with white space", c -> word(c, true, true));
        commands.object("is", "inner sentence", c -> sentence(c, false));
        commands.object("as", "a sentence, with white space", c -> sentence(c, true));
        commands.object("ip", "inner paragraph", c -> paragraph(c, false));
        commands.object("ap", "a paragraph, with the blank lines after it", c -> paragraph(c, true));
        for (String keys : List.of("i(", "i)", "ib")) commands.object(keys, "inside ( )", c -> block(c, false, '(', ')'));
        for (String keys : List.of("a(", "a)", "ab")) commands.object(keys, "a ( ) block", c -> block(c, true, '(', ')'));
        for (String keys : List.of("i{", "i}", "iB")) commands.object(keys, "inside { }", c -> block(c, false, '{', '}'));
        for (String keys : List.of("a{", "a}", "aB")) commands.object(keys, "a { } block", c -> block(c, true, '{', '}'));
        for (String keys : List.of("i[", "i]")) commands.object(keys, "inside [ ]", c -> block(c, false, '[', ']'));
        for (String keys : List.of("a[", "a]")) commands.object(keys, "a [ ] block", c -> block(c, true, '[', ']'));
        for (String keys : List.of("i<lt>", "i>")) commands.object(keys, "inside angle brackets", c -> block(c, false, '<', '>'));
        for (String keys : List.of("a<lt>", "a>")) commands.object(keys, "an angle-bracket block", c -> block(c, true, '<', '>'));
        for (char quote : new char[] {'"', '\'', '`'}) {
            commands.object("i" + quote, "inside " + quote + " quotes", c -> quote(c, false, quote));
            commands.object("a" + quote, "a " + quote + " string, with white space", c -> quote(c, true, quote));
        }
        commands.object("it", "inside a tag block", c -> tag(c, false));
        commands.object("at", "a tag block, with its tags", c -> tag(c, true));
    }

    /** Sets the Visual area and mode, as the Visual branches assign VIsual and VIsual_mode. */
    private static Span visual(Context c, Pos anchor, Pos cursor, Mode mode) {
        c.editor.visualAnchor(anchor);
        c.editor.mode(mode);
        c.editor.setCursor(cursor, true);
        return VISUAL_DONE;
    }

    /** V becomes v; other Visual modes stay (current_word, current_quote). */
    private static Mode charwise(Context c) {
        Mode mode = c.editor.mode();
        return mode == Mode.VISUAL_LINE ? Mode.VISUAL : mode;
    }

    /** current_word. */
    static Span word(Context c, boolean include, boolean bigword) {
        Walk walk = c.walk(bigword);
        Pos cursor = c.cursor();
        Pos visual = c.visual;
        int count = c.count;
        boolean inclusive = true, includeWhite = false;
        Pos start = new Pos(0, 0);
        if (visual == null || cursor.equals(visual)) {
            walk.backInLine(cursor);
            start = cursor.copy();
            if ((walk.cls(cursor) == 0) == include) {
                if (!walk.endWord(cursor, 1, true, true)) return null;
            } else {
                walk.fwdWord(cursor, 1, true);
                if (cursor.col == 0) walk.decl(cursor);
                else walk.oneLeft(cursor);
                if (include) includeWhite = true;
            }
            if (visual != null) visual = start.copy();
            count--;
        }
        while (count > 0) {
            inclusive = true;
            if (visual != null && cursor.compareTo(visual) < 0) {
                if (walk.decl(cursor) == Walk.FAIL) return null;
                if (include != (walk.cls(cursor) != 0)) {
                    if (!walk.bckWord(cursor, 1, true)) return null;
                } else {
                    if (!walk.bckendWord(cursor, 1, true)) return null;
                    walk.incl(cursor);
                }
            } else {
                if (walk.incl(cursor) == Walk.FAIL) return null;
                if (include != (walk.cls(cursor) == 0)) {
                    if (!walk.fwdWord(cursor, 1, true) && count > 1) return null;
                    if (!walk.oneLeft(cursor)) inclusive = false;
                } else if (!walk.endWord(cursor, 1, true, true)) return null;
            }
            count--;
        }
        if (includeWhite && (walk.cls(cursor) != 0 || cursor.col == 0 && !inclusive)) {
            // Take white space before the word instead ("daw" on the last word of a sentence), but not indent.
            Pos back = start.copy();
            if (walk.oneLeft(back)) {
                walk.backInLine(back);
                if (walk.cls(back) == 0 && back.col > 0) {
                    if (visual != null) visual = back;
                    else start = back;
                }
            }
        }
        if (visual != null) return visual(c, visual, cursor, charwise(c));
        return new Span(start, cursor, inclusive ? Kind.INCLUSIVE : Kind.EXCLUSIVE);
    }

    /** current_sent. */
    static Span sentence(Context c, boolean include) {
        Walk walk = c.walk(false);
        Pos startPos = c.cursor(), pos = startPos.copy(), cursor = startPos.copy();
        Pos visual = c.visual;
        int count = c.count;
        Motions.findSentence(walk, cursor, true, 1);
        if (visual != null && !startPos.equals(visual)) return extendSentence(c, walk, startPos, pos, cursor, count, include);
        int ch;
        while ((ch = walk.gchar(pos)) == ' ' || ch == '\t') walk.incl(pos);
        boolean startBlank;
        if (pos.equals(cursor)) {
            startBlank = true;
            findFirstBlank(walk, startPos);
        } else {
            startBlank = false;
            Motions.findSentence(walk, cursor, false, 1);
            startPos = cursor.copy();
        }
        int ncount = include ? count * 2 : count - (startBlank ? 1 : 0);
        if (ncount > 0) findSentenceForward(walk, cursor, ncount, true);
        else walk.decl(cursor);
        if (include) {
            if (startBlank) {
                findFirstBlank(walk, cursor);
                ch = walk.gchar(cursor);
                if (ch == ' ' || ch == '\t') walk.decl(cursor);
            } else if ((ch = walk.gchar(cursor)) != ' ' && ch != '\t') findFirstBlank(walk, startPos);
        }
        if (visual != null) {
            // Avoid getting stuck with "is" on a single space before a sentence.
            if (startPos.equals(cursor)) return extendSentence(c, walk, startPos, pos, cursor.copy(), count, include);
            return visual(c, startPos, cursor, Mode.VISUAL);
        }
        boolean inclusive = walk.incl(cursor) == Walk.FAIL;
        return new Span(startPos, cursor, inclusive ? Kind.INCLUSIVE : Kind.EXCLUSIVE);
    }

    /** current_sent's "extend" branch: a Visual area bigger than one character grows by whole sentences. */
    private static Span extendSentence(Context c, Walk walk, Pos startPos, Pos pos, Pos nextStart, int count, boolean include) {
        Pos visual = c.visual;
        // Vim's cursor here is where findsent(FORWARD) left it: the start of the next sentence.
        Pos cursor = nextStart.copy();
        int ch;
        if (startPos.compareTo(visual) < 0) {
            boolean atStart = true;
            walk.decl(pos);
            while (pos.compareTo(nextStart) < 0) {
                ch = walk.gchar(pos);
                if (ch != ' ' && ch != '\t') {
                    atStart = false;
                    break;
                }
                walk.incl(pos);
            }
            if (!atStart) {
                Motions.findSentence(walk, cursor, false, 1);
                if (cursor.equals(startPos)) atStart = true;
                else Motions.findSentence(walk, cursor, true, 1);
            }
            if (include) count *= 2;
            while (count-- > 0) {
                if (atStart) findFirstBlank(walk, cursor);
                ch = walk.gchar(cursor);
                if (!atStart || !include && ch != ' ' && ch != '\t') Motions.findSentence(walk, cursor, false, 1);
                atStart = !atStart;
            }
        } else {
            walk.incl(pos);
            boolean atStart = true;
            if (!pos.equals(cursor)) {
                atStart = false;
                while (pos.compareTo(cursor) < 0) {
                    ch = walk.gchar(pos);
                    if (ch != ' ' && ch != '\t') {
                        atStart = true;
                        break;
                    }
                    walk.incl(pos);
                }
                if (atStart) Motions.findSentence(walk, cursor, false, 1);
                else cursor = startPos.copy();
            }
            if (include) count *= 2;
            findSentenceForward(walk, cursor, count, atStart);
        }
        c.editor.setCursor(cursor, true);
        return VISUAL_DONE;
    }

    private static void findFirstBlank(Walk walk, Pos pos) {
        while (walk.decl(pos) != Walk.FAIL) {
            int ch = walk.gchar(pos);
            if (ch != ' ' && ch != '\t') {
                walk.incl(pos);
                break;
            }
        }
    }

    private static void findSentenceForward(Walk walk, Pos cursor, int count, boolean atStart) {
        while (count-- > 0) {
            Motions.findSentence(walk, cursor, true, 1);
            if (atStart) findFirstBlank(walk, cursor);
            if (count == 0 || atStart) walk.decl(cursor);
            atStart = !atStart;
        }
    }

    /** current_par. */
    static Span paragraph(Context c, boolean include) {
        Buffer b = c.buffer;
        int last = b.count() - 1;
        int start = c.cursor().line;
        Pos visual = c.visual;
        if (visual != null && start != visual.line) return extendParagraph(c, start, include);
        boolean whiteInFront = white(b, start);
        while (start > 0) {
            if (whiteInFront) {
                if (!white(b, start - 1)) break;
            } else if (white(b, start - 1) || Motions.startPS(b, start, 0, false)) break;
            start--;
        }
        int end = start;
        while (end <= last && white(b, end)) end++;
        end--;
        int i = c.count;
        if (!include && whiteInFront) i--;
        while (i-- > 0) {
            if (end == last) return null;
            boolean doWhite = !include && white(b, end + 1);
            if (include || !doWhite) {
                end++;
                while (end < last && !white(b, end + 1) && !Motions.startPS(b, end + 1, 0, false)) end++;
            }
            if (i == 0 && whiteInFront && include) break;
            if (include || doWhite) while (end < last && white(b, end + 1)) end++;
        }
        if (!whiteInFront && !white(b, end) && include) while (start > 0 && white(b, start - 1)) start--;
        if (visual != null) {
            // "Vipipip" on a single white line would get stuck: extend instead.
            if (c.editor.mode() == Mode.VISUAL_LINE && start == c.cursor().line) return extendParagraph(c, start, include);
            Pos anchor = visual.line != start ? new Pos(start, 0) : visual;
            return visual(c, anchor, new Pos(end, 0), Mode.VISUAL_LINE);
        }
        return new Span(new Pos(start, 0), new Pos(end, 0), Kind.LINEWISE);
    }

    /** current_par's "extend" branch: a Visual area over several lines grows by whole paragraphs. */
    private static Span extendParagraph(Context c, int startLine, boolean include) {
        Buffer b = c.buffer;
        int last = b.count() - 1;
        int dir = startLine < c.visual.line ? -1 : 1;
        boolean failed = false;
        for (int i = c.count; --i >= 0; ) {
            if (startLine == (dir < 0 ? 0 : last)) {
                failed = true;
                break;
            }
            int prevStartIsWhite = -1;
            for (int t = 0; t < 2; t++) {
                startLine += dir;
                int startIsWhite = white(b, startLine) ? 1 : 0;
                if (prevStartIsWhite == startIsWhite) {
                    startLine -= dir;
                    break;
                }
                while (startLine != (dir < 0 ? 0 : last)) {
                    if ((startIsWhite == 1) != white(b, startLine + dir)
                        || startIsWhite == 0 && Motions.startPS(b, startLine + (dir > 0 ? 1 : 0), 0, false)) break;
                    startLine += dir;
                }
                if (!include) break;
                if (startLine == (dir < 0 ? 0 : last)) break;
                prevStartIsWhite = startIsWhite;
            }
        }
        c.editor.setCursor(new Pos(startLine, 0), true);
        return failed ? null : VISUAL_DONE;
    }

    private static boolean white(Buffer b, int line) { return Text.blank(b.line(line)); }

    /** current_block. */
    static Span block(Context c, boolean include, char open, char close) {
        Buffer b = c.buffer;
        Walk walk = c.walk(false);
        Pos oldPos = c.cursor(), cursor = oldPos.copy();
        Pos oldStart = oldPos.copy(), oldEnd = oldPos.copy();
        Pos visual = c.visual;
        if (visual == null || visual.equals(cursor)) {
            if (open == '{') while (walk.inIndent(cursor, 1)) if (walk.inc(cursor) != 0) break;
            if (walk.gchar(cursor) == open) cursor.col++;
        } else if (visual.compareTo(cursor) < 0) {
            oldStart = visual.copy();
            cursor = visual.copy();
        } else oldEnd = visual.copy();
        Pos start = null;
        if (Matching.unmatched(b, cursor, open, close, true, false) != null) {
            for (int n = c.count; n > 0; n--) {
                Pos next = Matching.unmatched(b, cursor, open, close, true, false);
                if (next == null) break;
                cursor = next;
                start = next.copy();
            }
        } else {
            for (int n = c.count; n > 0; n--) {
                Pos next = Matching.scan(b, cursor, close, open, false, false, false);
                if (next == null) break;
                cursor = next;
                start = next.copy();
            }
        }
        if (start == null) return null;
        Pos endPos = Matching.unmatched(b, cursor, open, close, false, false);
        if (endPos == null) return null;
        Pos end = endPos.copy();
        boolean sol = false;
        while (!include) {
            walk.incl(start);
            sol = end.col == 0;
            walk.decl(end);
            while (walk.inIndent(end, 1)) {
                sol = true;
                if (walk.decl(end) != 0) break;
            }
            if (start.equals(endPos) && visual != null) return null;
            if (start.compareTo(oldStart) >= 0 && oldEnd.compareTo(end) >= 0 && !start.equals(end) && visual != null) {
                // The inner block is no bigger than the Visual area: take the enclosing block.
                cursor = oldStart.copy();
                walk.decl(cursor);
                Pos outer = Matching.unmatched(b, cursor, open, close, true, false);
                if (outer == null) return null;
                start = outer.copy();
                Pos outerEnd = Matching.unmatched(b, outer, open, close, false, false);
                if (outerEnd == null) return null;
                endPos = outerEnd;
                end = outerEnd.copy();
            } else break;
        }
        if (visual != null) {
            if (sol && walk.gchar(end) != 0) walk.inc(end);
            return visual(c, start, end, Mode.VISUAL);
        }
        if (sol) {
            walk.incl(end);
            return new Span(start, end, Kind.EXCLUSIVE);
        }
        if (start.compareTo(end) <= 0) return new Span(start, end, Kind.INCLUSIVE);
        return new Span(start, start.copy(), Kind.EXCLUSIVE);
    }

    /** current_quote, within the cursor's line. */
    static Span quote(Context c, boolean include, char quote) {
        Pos cursor = c.cursor();
        Pos visual = c.visual;
        String line = c.buffer.line(cursor.line);
        int colStart = cursor.col, colEnd;
        boolean visEmpty = true, visBefore = false, insideQuotes = false, selectedQuote = false;
        if (visual != null) {
            if (visual.line != cursor.line) return null;
            visBefore = visual.compareTo(cursor) < 0;
            visEmpty = visual.equals(cursor);
        }
        if (!visEmpty) {
            int i;
            if (visBefore) {
                insideQuotes = visual.col > 0 && at(line, visual.col - 1) == quote && cursor.col < line.length() && at(line, cursor.col + 1) == quote;
                i = visual.col;
                colEnd = cursor.col;
            } else {
                insideQuotes = cursor.col > 0 && at(line, cursor.col - 1) == quote && visual.col < line.length() && at(line, visual.col + 1) == quote;
                i = cursor.col;
                colEnd = visual.col;
            }
            while (i <= colEnd && i < line.length()) if (line.charAt(i++) == quote) {
                selectedQuote = true;
                break;
            }
        }
        if (!visEmpty && at(line, colStart) == quote) {
            if (visBefore) {
                colStart = nextQuote(line, colStart + 1, quote, false);
                if (colStart < 0) return null;
                colEnd = nextQuote(line, colStart + 1, quote, true);
                if (colEnd < 0) {
                    colEnd = colStart;
                    colStart = cursor.col;
                }
            } else {
                colEnd = previousQuote(line, colStart, quote, false);
                if (at(line, colEnd) != quote) return null;
                colStart = previousQuote(line, colEnd, quote, true);
                if (at(line, colStart) != quote) {
                    colStart = colEnd;
                    colEnd = cursor.col;
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
        Pos start = new Pos(cursor.line, colStart);
        Pos newVisual = visual;
        if (visual != null && (visEmpty || visBefore && !selectedQuote && (insideQuotes
            || at(line, visual.col) != quote && (visual.col == 0 || at(line, visual.col - 1) != quote)))) newVisual = start.copy();
        Pos end = new Pos(cursor.line, colEnd);
        boolean inclusive = false;
        if (include || c.count > 1 || !visEmpty && insideQuotes) {
            int next = Text.next(line, colEnd);
            if (next >= line.length()) inclusive = true;
            end.col = next;
        }
        if (visual != null) {
            Pos newCursor;
            if (visEmpty || visBefore) {
                newCursor = end.copy();
                if (newCursor.col > 0) newCursor.col = Text.previous(line, newCursor.col);
            } else {
                if (insideQuotes || !selectedQuote && at(line, visual.col) != quote && (visual.col >= line.length() || at(line, visual.col + 1) != quote)) {
                    Pos back = end.copy();
                    if (back.col > 0) back.col = Text.previous(line, back.col);
                    newVisual = back;
                }
                newCursor = new Pos(cursor.line, colStart);
            }
            return visual(c, newVisual, newCursor, charwise(c));
        }
        return new Span(start, end, inclusive ? Kind.INCLUSIVE : Kind.EXCLUSIVE);
    }

    private static char at(String line, int col) { return col >= 0 && col < line.length() ? line.charAt(col) : 0; }

    private static boolean white(char c) { return c == ' ' || c == '\t'; }

    /** find_next_quote, with 'quoteescape' {@code \} when {@code escape}. */
    private static int nextQuote(String line, int col, char quote, boolean escape) {
        while (true) {
            if (col >= line.length()) return -1;
            char c = line.charAt(col);
            if (escape && c == '\\') {
                col++;
                if (col >= line.length()) return -1;
            } else if (c == quote) return col;
            col = Text.next(line, col);
        }
    }

    /** find_prev_quote, with 'quoteescape' {@code \} when {@code escape}. */
    private static int previousQuote(String line, int colStart, char quote, boolean escape) {
        while (colStart > 0) {
            colStart = Text.previous(line, colStart);
            int n = 0;
            if (escape) while (colStart - n > 0 && line.charAt(colStart - n - 1) == '\\') n++;
            if ((n & 1) == 1) colStart -= n;
            else if (line.charAt(colStart) == quote) break;
        }
        return colStart;
    }

    /** it and at: the {@code count}-th tag block around the cursor (or around the Visual area). */
    static Span tag(Context c, boolean include) {
        Buffer b = c.buffer;
        String text = b.text();
        Pos visual = c.visual;
        int cursor = b.offset(c.cursor());
        int low = cursor, high = cursor;
        if (visual != null && !visual.equals(c.cursor())) {
            low = Math.min(cursor, b.offset(visual));
            high = Math.max(cursor, b.offset(visual));
        }
        List<int[]> around = new ArrayList<>();
        Deque<Object[]> open = new ArrayDeque<>();
        Matcher m = TAG.matcher(text);
        while (m.find()) {
            if (!m.group(3).isEmpty()) continue;
            String name = m.group(2).toLowerCase(Locale.ROOT);
            if (m.group(1).isEmpty()) {
                open.push(new Object[] {name, m.start(), m.end()});
                continue;
            }
            while (!open.isEmpty()) {
                Object[] top = open.pop();
                if (!top[0].equals(name)) continue;
                int openStart = (Integer) top[1], openEnd = (Integer) top[2];
                if (openStart <= low && high < m.end()) around.add(new int[] {openStart, openEnd, m.start(), m.end()});
                break;
            }
        }
        around.sort((x, y) -> Integer.compare(y[0], x[0]));
        int index = c.count - 1;
        if (visual != null) {
            // A Visual area that already covers the object takes the next enclosing one, as Vim's "do it again".
            while (index < around.size()) {
                int[] pair = around.get(index);
                int from = include ? pair[0] : pair[1], to = (include ? pair[3] : pair[2]) - 1;
                if (from == low && to == high) index++;
                else break;
            }
        }
        if (index >= around.size()) return null;
        int[] pair = around.get(index);
        int from = include ? pair[0] : pair[1], to = include ? pair[3] : pair[2];
        if (visual != null) {
            Pos start = b.pos(from), end = b.pos(Math.max(from, to - 1));
            return visual(c, start, to > from ? end : start, Mode.VISUAL);
        }
        return new Span(b.pos(from), b.pos(to), Kind.EXCLUSIVE);
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

    /** op_yank. */
    static void yank(Context c, Span span) {
        c.registers.yank(c.register, content(c, span), c.options.string("clipboard"));
    }

    /** op_shift, with shift_line's 'shiftround'; a Visual area shifts {count} times. */
    static void shift(Context c, Span span, boolean left) {
        Buffer b = c.buffer;
        int sw = c.options.shiftwidth(), ts = c.tabstop();
        boolean et = c.options.flag("expandtab"), round = c.options.flag("shiftround");
        int times = span.visual() && c.hasCount ? c.count : 1;
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

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/NormalCommands.java` with:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Registers.Content;
import java.util.ArrayList;
import java.util.List;

/** Normal-mode commands that are neither motions nor operators (spec 4.1), ported from Vim's normal.c and ops.c. */
final class NormalCommands {
    private NormalCommands() { }

    static void register(VimCommands commands) {
        commands.action("x", "delete {count} characters (dl)", true, c -> c.editor.machine().alias(c, "d", "l"));
        commands.action("<Del>", "delete {count} characters (dl)", true, c -> c.editor.machine().alias(c, "d", "l"));
        commands.action("X", "delete {count} characters before the cursor (dh)", true, c -> c.editor.machine().alias(c, "d", "h"));
        commands.action("D", "delete to the end of the line (d$)", true, c -> c.editor.machine().alias(c, "d", "$"));
        commands.action("C", "change to the end of the line (c$)", true, c -> c.editor.machine().alias(c, "c", "$"));
        commands.action("s", "substitute {count} characters (cl)", true, c -> c.editor.machine().alias(c, "c", "l"));
        commands.action("S", "substitute {count} lines (cc)", true, c -> c.editor.machine().alias(c, "c", "c"));
        commands.action("Y", "yank {count} lines (yy)", false, c -> c.editor.machine().alias(c, "y", "y"));
        commands.actionWithChar("r", "replace {count} characters with {char}", true, NormalCommands::replace);
        commands.action("~", "toggle the case of {count} characters", true, NormalCommands::tilde);
        commands.action("J", "join {count} lines, with a space", true, c -> join(c, true));
        commands.action("<C-g>", "show the file name, line count and cursor position", false, NormalCommands::fileInfo);
        commands.action("ga", "show the code of the character under the cursor", false, NormalCommands::ascii);
        commands.action("gJ", "join {count} lines, without a space", true, c -> join(c, false));
        commands.action("p", "put after the cursor", true, c -> put(c, true, false, false));
        commands.action("P", "put before the cursor", true, c -> put(c, false, false, false));
        commands.action("gp", "put after, cursor after the text", true, c -> put(c, true, true, false));
        commands.action("gP", "put before, cursor after the text", true, c -> put(c, false, true, false));
        commands.action("]p", "put after, with the current line's indent", true, c -> put(c, true, false, true));
        commands.action("[p", "put before, with the current line's indent", true, c -> put(c, false, false, true));
        commands.action("]P", "put before, with the current line's indent", true, c -> put(c, false, false, true));
        commands.action("[P", "put before, with the current line's indent", true, c -> put(c, false, false, true));
        commands.action("i", "insert before the cursor", true, c -> c.editor.insert().begin("i", c.count, false, -1));
        commands.action("<Insert>", "insert before the cursor", true, c -> c.editor.insert().begin("i", c.count, false, -1));
        commands.action("a", "append after the cursor", true, NormalCommands::append);
        commands.action("I", "insert before the first non-blank", true, c -> insertAt(c, Text.firstNonBlank(c.buffer.line(c.cursor().line)), "I"));
        commands.action("gI", "insert in column 1", true, c -> insertAt(c, 0, "gI"));
        commands.action("A", "append at the end of the line", true, c -> insertAt(c, c.buffer.line(c.cursor().line).length(), "A"));
        commands.action("o", "open a line below", true, c -> open(c, true));
        commands.action("O", "open a line above", true, c -> open(c, false));
        commands.action("R", "Replace mode", true, c -> c.editor.insert().begin("R", c.count, true, -1));
        commands.action("u", "undo {count} changes", false, c -> c.editor.undo(c.count));
        commands.action("<C-r>", "redo {count} changes", false, c -> c.editor.redo(c.count));
        commands.action(".", "repeat the last change, with {count} replacing its count", false, c -> c.editor.machine().repeat(c));
        commands.action("<C-l>", "redraw (does nothing)", false, c -> { });
    }

    /** nv_replace. */
    static void replace(Context c) {
        KeyInput key = c.argument;
        if (key.is("<Esc>")) return;
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        int end = p.col;
        for (int n = 0; n < c.count; n++) {
            if (end >= line.length()) throw Context.fail();
            end = Text.next(line, end);
        }
        if (key.is("<CR>") || key.is("<C-j>")) {
            c.buffer.remove(c.buffer.offset(p), c.buffer.offset(p.line, end));
            c.editor.setCursor(p);
            InsertMode insert = c.editor.insert();
            insert.beginNested("r");
            insert.key(KeyInput.named("CR"));
            insert.key(KeyInput.named("Esc"));
            return;
        }
        String with;
        if (key.is("<Tab>")) with = "\t";
        else if (key.plainChar() >= 0) with = key.key();
        else throw Context.fail();
        c.buffer.replace(c.buffer.offset(p), c.buffer.offset(p.line, end), with.repeat(c.count));
        c.editor.setCursor(new Pos(p.line, p.col + (c.count - 1) * with.length()));
    }

    /** n_swapchar, with 'notildeop'. */
    static void tilde(Context c) {
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        if (line.isEmpty()) throw Context.fail();
        int col = p.col;
        StringBuilder out = new StringBuilder(line.substring(0, col));
        for (int n = c.count; n > 0 && col < line.length(); n--) {
            int next = Text.next(line, col);
            line.substring(col, next).codePoints().forEach(cp -> out.appendCodePoint(Operators.toggle(cp)));
            col = next;
        }
        out.append(line.substring(col));
        c.buffer.setLine(p.line, out.toString());
        c.editor.setCursor(new Pos(p.line, col));
    }

    static void append(Context c) {
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        if (!line.isEmpty()) p.col = Text.next(line, p.col);
        c.editor.setCursor(p, true);
        c.editor.insert().begin("a", c.count, false, -1);
    }

    static void insertAt(Context c, int col, String command) {
        c.editor.setCursor(new Pos(c.cursor().line, col), true);
        c.editor.insert().begin(command, c.count, false, -1);
    }

    static void open(Context c, boolean below) {
        String command = below ? "o" : "O";
        c.editor.insert().openLine(below);
        c.editor.insert().begin(command, c.count, false, c.options.flag("autoindent") ? c.cursor().line : -1);
    }

    /** nv_join, do_join. */
    static void join(Context c, boolean spaces) {
        int count = Math.max(c.count, 2), first = c.cursor().line, last = c.buffer.count() - 1;
        if (first + count - 1 > last) {
            if (count <= 2) throw Context.fail();
            count = last - first + 1;
        }
        joinLines(c, first, count, spaces);
    }

    /**
     * do_join. Each line joins the first in turn: the separator goes after the first line's text, then the line break
     * and the next line's indent go, so marks on the joined line move as Vim's mark_col_adjust moves them.
     */
    static void joinLines(Context c, int first, int count, boolean insertSpace) {
        Buffer b = c.buffer;
        boolean js = c.options.flag("joinspaces");
        int col = 0, endcurr1 = 0, endcurr2 = 0;
        String head = b.line(first);
        if (insertSpace && !head.isEmpty()) {
            int lastStart = head.offsetByCodePoints(head.length(), -1);
            endcurr1 = head.codePointAt(lastStart);
            if (lastStart > 0) endcurr2 = head.codePointBefore(lastStart);
        }
        for (int t = 1; t < count; t++) {
            String curr = b.line(first + 1);
            int removed = 0, spaces = 0;
            if (insertSpace) {
                removed = Text.firstNonBlank(curr);
                curr = curr.substring(removed);
                if (!curr.isEmpty() && curr.charAt(0) != ')' && b.line(first).length() > 0 && endcurr1 != '\t') {
                    if (endcurr1 == ' ') endcurr1 = endcurr2;
                    else spaces++;
                    if (js && (endcurr1 == '.' || endcurr1 == '?' || endcurr1 == '!')) spaces++;
                }
            }
            int end = b.end(first);
            if (t == count - 1) col = b.line(first).length();
            if (spaces > 0) b.insert(end, " ".repeat(spaces));
            int from = b.end(first), to = b.start(first + 1) + removed;
            c.editor.marks().joining(() -> b.remove(from, to));
            endcurr1 = endcurr2 = 0;
            if (insertSpace && !curr.isEmpty()) {
                int lastStart = curr.offsetByCodePoints(curr.length(), -1);
                endcurr1 = curr.codePointAt(lastStart);
                if (lastStart > 0) endcurr2 = curr.codePointBefore(lastStart);
            }
        }
        c.editor.setCursor(new Pos(first, col));
    }

    /**
     * fileinfo (buffer.c) with 'noruler': {@code "name" [Modified] line 2 of 10 --20%-- col 3}, the column shown as
     * {@code 3-10} when the display column differs.
     */
    static void fileInfo(Context c) {
        Buffer b = c.buffer;
        Pos p = c.cursor();
        StringBuilder out = new StringBuilder("\"").append(c.editor.documentName()).append('"');
        boolean modified = c.editor.documentModified();
        out.append(modified ? " [Modified] " : " ");
        if (b.length() == 0) out.append("--No lines in buffer--");
        else {
            int lines = b.count();
            int percent = (int) ((long) (p.line + 1) * 100 / lines);
            String line = b.line(p.line);
            int col = p.col + 1, vcol = Text.virtcol(line, p.col, c.tabstop()) + 1;
            out.append("line ").append(p.line + 1).append(" of ").append(lines).append(" --").append(percent).append("%-- col ");
            out.append(col == vcol ? String.valueOf(col) : col + "-" + vcol);
        }
        c.editor.message(out.toString(), false);
    }

    /**
     * do_ascii: the character under the cursor as {@code <a>  97,  Hex 61,  Octal 141}, or for a character above
     * U+007F {@code <é> 233, Hex 00e9, Oct 351}, followed by any combining characters. Vim adds the character's
     * digraph; Jasper has no digraph table (docs/vim-divergences.md).
     */
    static void ascii(Context c) {
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        if (p.col >= line.length()) {
            c.editor.message("NUL", false);
            return;
        }
        StringBuilder out = new StringBuilder();
        int i = p.col;
        int cp = line.codePointAt(i);
        if (cp < 0x80) {
            String shown = cp < 0x20 ? "^" + (char) (cp + '@') : cp == 0x7f ? "^?" : String.valueOf((char) cp);
            out.append('<').append(shown).append(">  ").append(cp).append(",  Hex ").append(String.format("%02x", cp))
                .append(",  Octal ").append(String.format("%03o", cp));
            i += 1;
        } else {
            out.append('<').append(Character.toString(cp)).append("> ").append(cp).append(", Hex ").append(String.format("%04x", cp))
                .append(", Oct ").append(Integer.toOctalString(cp));
            i += Character.charCount(cp);
        }
        while (i < line.length() && Character.getType(line.codePointAt(i)) == Character.NON_SPACING_MARK) {
            int mark = line.codePointAt(i);
            out.append(" < ").append(Character.toString(mark)).append("> ").append(mark).append(", Hex ").append(String.format("%04x", mark))
                .append(", Oct ").append(Integer.toOctalString(mark));
            i += Character.charCount(mark);
        }
        c.editor.message(out.toString(), false);
    }

    /** do_put: see {@link Put}. */
    static void put(Context c, boolean after, boolean cursorAfter, boolean fixIndent) {
        Put.put(c, after, c.count, (cursorAfter ? Put.CURSEND : 0) | (fixIndent ? Put.FIXINDENT : 0), -1);
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
            // The '^ mark: where Insert mode ended, before the cursor moves back (ins_esc).
            editor.marks().set('^', p);
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
                editor.moveTo(target);
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
                    editor.moveTo(target);
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
                if (!entry.keys().equals("u") && !entry.keys().equals("<C-r>")) editor.forgetColumn();
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
        if (context.count > 1 && cursor.line >= last) throw Context.fail();
        int end = Math.min(last, cursor.line + context.count - 1);
        if (forced == 'v') return new Span(cursor.copy(), new Pos(end, editor.wantedColumn(end)), Kind.EXCLUSIVE);
        String text = editor.buffer().line(end);
        boolean sol = operator.equals("d") || operator.equals(">") || operator.equals("<lt>");
        int col;
        if (operator.equals("y")) col = end == cursor.line ? cursor.col : editor.wantedColumn(end);
        else if (sol && !editor.options().flag("startofline")) col = editor.wantedColumn(end);
        else col = Text.firstNonBlank(text) >= text.length() && !text.isEmpty() ? Text.last(text) : Text.firstNonBlank(text);
        Pos target = new Pos(end, col);
        return new Span(Pos.min(cursor, target).copy(), Pos.max(cursor, target).copy(), Kind.LINEWISE);
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
        Pos start = Pos.min(cursor, target.pos()).copy(), end = Pos.max(cursor, target.pos()).copy();
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
    /** While a search pattern is typed with 'incsearch', the match it would find ({start, end} offsets), or null. */
    private int[] incsearch;
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
        insert.abort();
        machine.abort();
        area.removeCaretListener(caretListener);
        area.attach(null);
        area.setCaretStyle(RTextArea.INSERT_MODE, originalCaret);
        area.enableInputMethods(true);
        marks.close();
        area.getDocument().removeDocumentListener(highlightListener);
        area.removeFocusListener(leaveListener);
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
    }

    /** The text was replaced (a reload): any command or insert in progress is dropped. */
    public void textReplaced() {
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
        if (commandLine.active()) {
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
        if (commandLine.active()) return (key.modifiers() & KeyInput.META) != 0;
        if (mode.inserting() || (key.modifiers() & KeyInput.META) != 0) return true;
        return vim.handlers().handler(key, handlerMode()) == KeyHandlers.Handler.IDE;
    }

    /** A key the text area held back outside Insert mode: Vim has no meaning for it, so the bell rings. */
    void declined() {
        bells++;
        fire();
    }

    /** Rings the bell without failing the command (a key the command line has no use for). */
    void bell() { bells++; }

    /** @return the command line, active while {@code /} or {@code ?} reads a pattern */
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
            bells++;
            if (!failure.getMessage().isEmpty()) message(failure.getMessage(), true);
            if (!mode.inserting()) {
                machine.reset();
                machine.resumeIfInsertNormal();
            }
        } catch (RuntimeException bug) {
            LOG.log(System.Logger.Level.ERROR, "Vim command failed", bug);
            insert.abort();
            machine.abort();
            mode = Mode.NORMAL;
            message("Internal error; the command was cancelled", true);
        } finally {
            busy = false;
            if (!commandLine.active()) marks.checkpcmark(cursor());
            if (mode == Mode.NORMAL || mode == Mode.OPERATOR_PENDING) setCursor(cursor());
            look();
            fire();
        }
    }

    /** Runs one key without the failure handling, for {@code .} and a count's repeat. */
    void dispatch(KeyInput key) {
        if (commandLine.active()) commandLine.key(key);
        else if (mode.inserting()) insert.key(key);
        else machine.key(key);
    }

    /** Text an input method committed in Insert mode, recorded for {@code .}. */
    void typedByInputMethod(String text) { insert.typedByInputMethod(text); }

    /** @return the mode */
    public Mode mode() {
        if (commandLine.active()) return Mode.COMMAND_LINE;
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
        message = text;
        messageError = error;
    }

    /** @return the cursor as a position */
    Pos cursor() { return buffer.pos(area.getCaretPosition()); }

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
        highlights.show(ranges);
    }

    /** @return the drawn highlights as {kind, start, end}, for tests */
    List<int[]> highlights() { return highlights.shown(); }

    /**
     * 'incsearch': while {@code /} or {@code ?} reads a pattern, the match it would find is drawn and scrolled to;
     * the cursor stays where it is.
     */
    void incsearch(char type, String line) {
        incsearch = null;
        if (options.flag("incsearch") && !line.isEmpty()) {
            Search.Match match = vim.search().preview(this, type, line);
            if (match != null) {
                int start = buffer.offset(match.start()), end = buffer.offset(match.end());
                incsearch = new int[] { start, Math.max(end, start + 1) };
                try {
                    var rect = area.modelToView2D(start);
                    if (rect != null) area.scrollRectToVisible(rect.getBounds());
                } catch (javax.swing.text.BadLocationException ignored) { }
            }
        }
        refreshHighlights();
    }

    /** The command line closed: the incremental-search match goes. */
    void incsearchDone() {
        incsearch = null;
        Pos p = cursor();
        try {
            var rect = area.modelToView2D(buffer.offset(p));
            if (rect != null) area.scrollRectToVisible(rect.getBounds());
        } catch (javax.swing.text.BadLocationException ignored) { }
    }

    /** @return while a command line is typed, its text with the {@code /} or {@code ?} in front; else empty */
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

- [ ] **Step 5: Run the engine tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest'
```

Expected: 77 tests pass. `VimEditorTest.anUnexpectedErrorCancelsTheCommandAndReturnsToNormalMode` logs "Vim command failed" with a stack trace on purpose.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest'
```

Expected: the 469 existing cases replay and pass; `everyCommandHasACase` fails, listing the new commands (`visual v`, `action /`, …). Task 3 adds their cases.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava verifyPluginArchitecture 2>&1 | grep -i "warning\|error\|FAIL" || echo clean
```

Expected: `clean`: no compiler warnings (`-Xlint:all`), and the Editor still uses only the SDK and RSyntaxTextArea.

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/vim-v1b
git add plugins/editor/src/main/java/dev/jasper/editor/vim plugins/editor/src/test/java/dev/jasper/editor/vim/VisualSearchUiTest.java
git commit -m "Add Visual mode, search, marks, numbers and formatting to the Vim engine

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: The corpus: Visual mode, search, regex, marks, numbers and formatting

**Files:**
- Replace: `plugins/editor/src/test/java/dev/jasper/editor/vim/{CorpusCase,CorpusFile,CorpusRunner,RecordCorpus,VimCorpusTest,VimDocsTest}.java`
- Create: `plugins/editor/src/test/resources/vim/corpus/visual.case`, `plugins/editor/src/test/resources/vim/corpus/visual-block.case`, `plugins/editor/src/test/resources/vim/corpus/search.case`, `plugins/editor/src/test/resources/vim/corpus/regex.case`, `plugins/editor/src/test/resources/vim/corpus/marks.case`, `plugins/editor/src/test/resources/vim/corpus/numbers.case`, `plugins/editor/src/test/resources/vim/corpus/format.case`

**Interfaces:**
- Consumes: Task 2's engine.
- Produces: `CorpusCase.Register(String type, String text)` with `content()` and `static of(Registers.Content)` (types `c`, `l`, `b3`); `CorpusCase.Expected` gains `String visual` (`line,col` of the Visual anchor, or null). `RecordCorpus` writes `expect-visual:` in Visual mode, reads block register types, maps Vim's modes `v`, `V`, CTRL-V and `c`. `VimDocsTest`'s generated reference gains the Visual-mode table (Task 5 regenerates the file).

The expectations below were recorded from Vim 9.1 with `recordVimCorpus`; `marks.case` has one divergent case (the `'.` column, listed in Task 5's divergences). Recording again must change nothing.

- [ ] **Step 1: Update the harness**

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
 * @param expected what Vim did, or null when the case has not been recorded
 */
record CorpusCase(String file, String name, String divergent, String set, Map<Character, Register> given, List<String> text,
                  int line, int col, String keys, List<Character> registers, Expected expected) {
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
     */
    record Expected(String recordedBy, List<String> text, int line, int col, String mode, String visual, Map<Character, Register> registers) { }

    CorpusCase withExpected(Expected value) { return new CorpusCase(file, name, divergent, set, given, text, line, col, keys, registers, value); }

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
            String divergent = null, set = "", keys = null;
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
            cases.add(new CorpusCase(file, name, divergent, set, given, text, cursorLine, cursorCol, keys, registers, expected));
        }
        return cases;
    }

    private record Parsed(CorpusCase.Expected expected, int next) { }

    private static Parsed expectation(String file, List<String> lines, int i, String by) {
        List<String> text = List.of();
        int line = 1, col = 1;
        String mode = "normal", visual = null;
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
                default -> {
                    if (!key.startsWith("expect-register ")) throw error(file, i, "unknown field " + key);
                    if (!value.equals("none")) registers.put(key.charAt(16), register(value));
                }
            }
            i++;
        }
        return new Parsed(new CorpusCase.Expected(by, text, line, col, mode, visual, registers), i);
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
        var editor = new VimEditor(vim, area, EditorDefaults.VIM);
        if (!c.set().isBlank()) editor.options().apply(c.set());
        for (var entry : c.given().entrySet()) {
            var register = entry.getValue();
            vim.registers().set(entry.getKey(), register.content());
        }
        String start = c.text().get(c.line() - 1);
        editor.setCursor(new Pos(c.line() - 1, Text.column(start, c.col() - 1)));
        for (KeyInput key : KeyInput.parse(c.keys())) editor.feed(key);
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
        editor.close();
        return new CorpusCase.Expected("jasper", text, cursor.line + 1, col, editor.mode().id(), visual, registers);
    }

    /** @return the differences from the expectation, or empty */
    static String compare(CorpusCase.Expected expected, CorpusCase.Expected actual) {
        List<String> problems = new ArrayList<>();
        if (!expected.text().equals(actual.text())) problems.add("text " + actual.text() + " (Vim: " + expected.text() + ")");
        if (expected.line() != actual.line() || expected.col() != actual.col())
            problems.add("cursor " + actual.line() + "," + actual.col() + " (Vim: " + expected.line() + "," + expected.col() + ")");
        if (!expected.mode().equals(actual.mode())) problems.add("mode " + actual.mode() + " (Vim: " + expected.mode() + ")");
        if (!java.util.Objects.equals(expected.visual(), actual.visual())) problems.add("visual " + actual.visual() + " (Vim: " + expected.visual() + ")");
        if (!expected.registers().equals(actual.registers())) problems.add("registers " + show(actual.registers()) + " (Vim: " + show(expected.registers()) + ")");
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
            return new CorpusCase.Expected(version, text, ((Long) result.get("line")).intValue(), ((Long) result.get("col")).intValue(),
                mode((String) result.get("mode")), visual.isEmpty() ? null : visual.get(0) + "," + visual.get(1), registers);
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
        if (!c.set().isBlank()) lines.add("set " + c.set());
        for (var entry : c.given().entrySet()) {
            CorpusCase.Register register = entry.getValue();
            String text = register.type().equals("l") && register.text().endsWith("\n") ? register.text().substring(0, register.text().length() - 1) : register.text();
            lines.add("call setreg(" + Json.quote(String.valueOf(entry.getKey())) + ", " + vimList(List.of(text.split("\n", -1))) + ", " + Json.quote(register.type()) + ")");
        }
        // Editing a file, rather than setline(), leaves no undo history for u to reach.
        lines.add("call writefile(" + vimList(c.text()) + ", " + Json.quote(file.toString()) + ")");
        lines.add("silent execute 'edit!' fnameescape(" + Json.quote(file.toString()) + ")");
        lines.add("call setcursorcharpos(" + c.line() + ", " + c.col() + ")");
        lines.add("let g:jasper = {}");
        lines.add("call feedkeys(\"" + vimKeys(c.keys()) + "\\<Cmd>call JasperRecord()\\<CR>\", 'xt')");
        lines.add("call writefile([json_encode(g:jasper)], " + Json.quote(out.toString()) + ")");
        lines.add("qa!");
        return String.join("\n", lines) + "\n";
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

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/vim/VimCorpusTest.java` with:

```java
package dev.jasper.editor.vim;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.assertj.core.api.Assertions.assertThat;

/** Replays the differential corpus (spec 11.1): every case must do what Vim did, and every command needs a case. */
class VimCorpusTest {
    static List<CorpusCase> cases() {
        try (Stream<Path> files = Files.list(Path.of(VimCorpusTest.class.getResource("/vim/corpus").toURI()))) {
            return files.filter(file -> file.toString().endsWith(".case")).sorted().flatMap(file -> {
                try { return CorpusFile.read(file).stream(); }
                catch (IOException failed) { throw new UncheckedIOException(failed); }
            }).toList();
        } catch (IOException | URISyntaxException failed) {
            throw new IllegalStateException(failed);
        }
    }

    @TestFactory Stream<DynamicTest> everyCaseDoesWhatVimDid() {
        return cases().stream().map(c -> DynamicTest.dynamicTest(c.toString(), () -> {
            assertThat(c.expected()).as("%s has no expectation; run ./gradlew :jasper-plugin-editor:recordVimCorpus", c).isNotNull();
            assertThat(CorpusRunner.compare(c.expected(), CorpusRunner.run(c, id -> { }))).as("%s, keys %s", c, c.keys()).isEmpty();
        }));
    }

    @Test void everyCommandHasACase() {
        Set<String> used = new TreeSet<>();
        for (CorpusCase c : cases()) CorpusRunner.run(c, used::add);
        List<String> missing = VimCommands.standard().entries().stream().map(VimCommands.Entry::id).filter(id -> !used.contains(id)).toList();
        assertThat(missing).as("commands with no corpus case").isEmpty();
    }

    @Test void theCorpusFormatReadsBackWhatItWrites() {
        var lines = List.of("=== trailing blanks", "set: sw=4", "register a: l \"x\\n\"", "text:", "  ends in space $", "  $", "  cost $$",
            "cursor: 1,2", "keys: d<Esc>", "registers: \" a", "--- vim 9.1", "expect-text:", "  done", "expect-cursor: 1,1",
            "expect-mode: insert", "expect-register \": c \"one\\ttwo\"", "expect-register a: none");
        CorpusCase c = CorpusFile.parse("inline", lines).getFirst();
        assertThat(c.text()).containsExactly("ends in space ", "", "cost $");
        assertThat(c.given().get('a')).isEqualTo(new CorpusCase.Register("l", "x\n"));
        assertThat(c.expected().registers()).containsOnlyKeys('"');
        assertThat(c.expected().registers().get('"').text()).isEqualTo("one\ttwo");
        assertThat(CorpusFile.format(c, c.expected())).containsExactly("--- vim 9.1", "expect-text:", "  done", "expect-cursor: 1,1",
            "expect-mode: insert", "expect-register \": c \"one\\ttwo\"", "expect-register a: none");
        assertThat(RecordCorpus.vimKeys("a\"b\\<Esc><C-w>")).isEqualTo("a\\\"b\\\\\\<Esc>\\<C-w>");
        assertThat(RecordCorpus.mode("no")).isEqualTo("operator-pending");
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
        return out.toString();
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

- [ ] **Step 2: Add the cases**

Create `plugins/editor/src/test/resources/vim/corpus/visual.case`:

```text
# Visual mode: v and V, operators on the area, text objects, put, gv and repeat.

=== v then l selects
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: vll
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,3
expect-mode: visual
expect-visual: 1,1

=== v with a count
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: 3v
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,3
expect-mode: visual
expect-visual: 1,1

=== V with a count
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: 2V
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 2,1
expect-mode: visual-line
expect-visual: 1,1

=== v then Esc ends Visual mode
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vll<Esc>
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,7
expect-mode: normal

=== v then v ends Visual mode
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vllv
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,7
expect-mode: normal

=== v then V switches to linewise
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vlV
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,6
expect-mode: visual-line
expect-visual: 1,5

=== v then C-v switches to blockwise
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vj<C-v>
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 2,5
expect-mode: visual-block
expect-visual: 1,5

=== v at the end of the line goes to the line end with $
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: v$
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,14
expect-mode: visual
expect-visual: 1,5

=== v then o goes to the other end
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vwo
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: visual
expect-visual: 1,9

=== v then w
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: vw
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: visual
expect-visual: 1,1

=== v then e then d
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: ved
registers: " 0 1 -
--- vim 9.1
expect-text:
   two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "one"
expect-register 0: none
expect-register 1: none
expect-register -: c "one"

=== v then j then d
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vjd
registers: " 0 1 -
--- vim 9.1
expect-text:
  one five six
  seven eight nine
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "two three\nfour "
expect-register 0: none
expect-register 1: c "two three\nfour "
expect-register -: none

=== v then $ then d takes the line break
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: v$d
registers: " 0 1 -
--- vim 9.1
expect-text:
  one four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "two three\n"
expect-register 0: none
expect-register 1: c "two three\n"
expect-register -: none

=== v then $ then d on the last line
text:
  one two three
  four five six
  seven eight nine
cursor: 3,5
keys: v$d
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three
  four five six
  seve
expect-cursor: 3,4
expect-mode: normal
expect-register ": c "n eight nine"
expect-register 0: none
expect-register 1: none
expect-register -: c "n eight nine"

=== v then x
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vlx
registers: " 0 1 -
--- vim 9.1
expect-text:
  one o three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "tw"
expect-register 0: none
expect-register 1: none
expect-register -: c "tw"

=== v then Del
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vl<Del>
registers: " 0 1 -
--- vim 9.1
expect-text:
  one o three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "tw"
expect-register 0: none
expect-register 1: none
expect-register -: c "tw"

=== v then y keeps the cursor at the start
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vwy
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "two t"
expect-register 0: c "two t"
expect-register 1: none
expect-register -: none

=== v backwards then y
text:
  one two three
  four five six
  seven eight nine
cursor: 1,9
keys: vby
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "two t"
expect-register 0: c "two t"
expect-register 1: none
expect-register -: none

=== v then y a register
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: v"ay
registers: " a 0
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "t"
expect-register a: c "t"
expect-register 0: none

=== v with a register then y
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: "bvey
registers: " b 0
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "two"
expect-register b: none
expect-register 0: c "two"

=== v then Y yanks lines
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vY
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "one two three\n"
expect-register 0: l "one two three\n"
expect-register 1: none
expect-register -: none

=== v then c
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vec<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  one  three
  four five six
  seven eight nine
expect-cursor: 1,4
expect-mode: normal
expect-register ": c "two"
expect-register 0: none
expect-register 1: none
expect-register -: c "two"

=== v then c and text
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vecTWO<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  one TWO three
  four five six
  seven eight nine
expect-cursor: 1,7
expect-mode: normal
expect-register ": c "two"
expect-register 0: none
expect-register 1: none
expect-register -: c "two"

=== v then s
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vls!<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  one !o three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "tw"
expect-register 0: none
expect-register 1: none
expect-register -: c "tw"

=== v then X deletes lines
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vjX
registers: " 0 1 -
--- vim 9.1
expect-text:
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "one two three\nfour five six\n"
expect-register 0: none
expect-register 1: l "one two three\nfour five six\n"
expect-register -: none

=== v then D deletes lines
text:
  one two three
  four five six
  seven eight nine
cursor: 2,5
keys: vD
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three
  seven eight nine
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "four five six\n"
expect-register 0: none
expect-register 1: l "four five six\n"
expect-register -: none

=== v then C changes lines
text:
  one two three
  four five six
  seven eight nine
cursor: 2,5
keys: vlCnew<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three
  new
  seven eight nine
expect-cursor: 2,3
expect-mode: normal
expect-register ": l "four five six\n"
expect-register 0: none
expect-register 1: l "four five six\n"
expect-register -: none

=== v then S changes lines
text:
  one two three
  four five six
  seven eight nine
cursor: 2,5
keys: vSnew<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three
  new
  seven eight nine
expect-cursor: 2,3
expect-mode: normal
expect-register ": l "four five six\n"
expect-register 0: none
expect-register 1: l "four five six\n"
expect-register -: none

=== v then R changes lines
text:
  one two three
  four five six
  seven eight nine
cursor: 2,5
keys: vRnew<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three
  new
  seven eight nine
expect-cursor: 2,3
expect-mode: normal
expect-register ": l "four five six\n"
expect-register 0: none
expect-register 1: l "four five six\n"
expect-register -: none

=== v then u lowers
text:
  ONE TWO
cursor: 1,1
keys: veu
--- vim 9.1
expect-text:
  one TWO
expect-cursor: 1,1
expect-mode: normal

=== v then U uppers
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: vjU
--- vim 9.1
expect-text:
  ONE TWO THREE
  Four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal

=== v then tilde toggles
text:
  One Two
cursor: 1,1
keys: v$~
--- vim 9.1
expect-text:
  oNE tWO
expect-cursor: 1,1
expect-mode: normal

=== v then J joins
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vjJ
--- vim 9.1
expect-text:
  one two three four five six
  seven eight nine
expect-cursor: 1,14
expect-mode: normal

=== v then J on one line joins two
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vJ
--- vim 9.1
expect-text:
  one two three four five six
  seven eight nine
expect-cursor: 1,14
expect-mode: normal

=== v then gJ
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vjgJ
--- vim 9.1
expect-text:
  one two threefour five six
  seven eight nine
expect-cursor: 1,14
expect-mode: normal

=== v then J on the last line fails
text:
  one two three
  four five six
  seven eight nine
cursor: 3,1
keys: vJ
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 3,1
expect-mode: normal

=== v then r replaces
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: ver-
--- vim 9.1
expect-text:
  one --- three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: normal

=== v over lines then r
text:
  one two three
  four five six
  seven eight nine
cursor: 1,9
keys: vjr*
--- vim 9.1
expect-text:
  one two *****
  ********* six
  seven eight nine
expect-cursor: 1,9
expect-mode: normal

=== V then r
text:
  one two three
  four five six
  seven eight nine
cursor: 1,9
keys: Vr.
--- vim 9.1
expect-text:
  .............
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal

=== v then greater shifts
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vj>
--- vim 9.1
expect-text:
  	one two three
  	four five six
  seven eight nine
expect-cursor: 1,2
expect-mode: normal

=== v then 3 greater
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vj3>
--- vim 9.1
expect-text:
  			one two three
  			four five six
  seven eight nine
expect-cursor: 1,4
expect-mode: normal

=== v then less
text:
          a
          b
cursor: 1,9
keys: vj<lt>
--- vim 9.1
expect-text:
  a
  b
expect-cursor: 1,1
expect-mode: normal

=== v then equals
text:
  if {
  x
  }
cursor: 1,1
keys: vjj=
--- vim 9.1
expect-text:
  if {
  	x
  }
expect-cursor: 1,1
expect-mode: normal

=== v then gU
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vegU
--- vim 9.1
expect-text:
  one TWO three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: normal

=== v then iw
text:
  one two three
  four five six
  seven eight nine
cursor: 1,6
keys: viw
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,7
expect-mode: visual
expect-visual: 1,5

=== v then iw iw extends
text:
  one two three
  four five six
  seven eight nine
cursor: 1,6
keys: viwiw
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,8
expect-mode: visual
expect-visual: 1,5

=== v then aw
text:
  one two three
  four five six
  seven eight nine
cursor: 1,6
keys: vaw
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,8
expect-mode: visual
expect-visual: 1,5

=== v then 2aw
text:
  one two three
  four five six
  seven eight nine
cursor: 1,6
keys: v2aw
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,13
expect-mode: visual
expect-visual: 1,4

=== v then ip
text:
  a
  b
  $
  c
cursor: 1,1
keys: vip
--- vim 9.1
expect-text:
  a
  b
  $
  c
expect-cursor: 2,1
expect-mode: visual-line
expect-visual: 1,1

=== v then ap then d
text:
  a
  b
  $
  c
cursor: 1,1
keys: vapd
registers: " 0 1 -
--- vim 9.1
expect-text:
  c
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "a\nb\n\n"
expect-register 0: none
expect-register 1: l "a\nb\n\n"
expect-register -: none

=== v then i( then d
text:
  f(a, b) x
cursor: 1,4
keys: vi(d
registers: " 0 1 -
--- vim 9.1
expect-text:
  f() x
expect-cursor: 1,3
expect-mode: normal
expect-register ": c "a, b"
expect-register 0: none
expect-register 1: none
expect-register -: c "a, b"

=== v then a quote
text:
  say "hi there" now
cursor: 1,7
keys: va"
--- vim 9.1
expect-text:
  say "hi there" now
expect-cursor: 1,15
expect-mode: visual
expect-visual: 1,5

=== v then i quote
text:
  say "hi there" now
cursor: 1,7
keys: vi"y
registers: " 0 1 -
--- vim 9.1
expect-text:
  say "hi there" now
expect-cursor: 1,6
expect-mode: normal
expect-register ": c "hi there"
expect-register 0: c "hi there"
expect-register 1: none
expect-register -: none

=== v then it
text:
  <a><b>x</b></a>
cursor: 1,8
keys: vit
--- vim 9.1
expect-text:
  <a><b>x</b></a>
expect-cursor: 1,7
expect-mode: visual
expect-visual: 1,7

=== v then it it
text:
  <a><b>x</b></a>
cursor: 1,8
keys: vitit
--- vim 9.1
expect-text:
  <a><b>x</b></a>
expect-cursor: 1,11
expect-mode: visual
expect-visual: 1,4

=== v then is
text:
  One. Two three. Four.
cursor: 1,8
keys: vis
--- vim 9.1
expect-text:
  One. Two three. Four.
expect-cursor: 1,15
expect-mode: visual
expect-visual: 1,6

=== v then as then d
text:
  One. Two three. Four.
cursor: 1,8
keys: vasd
--- vim 9.1
expect-text:
  One. Four.
expect-cursor: 1,6
expect-mode: normal

=== V then d
text:
  one two three
  four five six
  seven eight nine
cursor: 2,3
keys: Vd
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three
  seven eight nine
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "four five six\n"
expect-register 0: none
expect-register 1: l "four five six\n"
expect-register -: none

=== V then j then d
text:
  one two three
  four five six
  seven eight nine
cursor: 1,3
keys: Vjd
registers: " 0 1 -
--- vim 9.1
expect-text:
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "one two three\nfour five six\n"
expect-register 0: none
expect-register 1: l "one two three\nfour five six\n"
expect-register -: none

=== V then k then y
text:
  one two three
  four five six
  seven eight nine
cursor: 3,3
keys: Vky
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 2,3
expect-mode: normal
expect-register ": l "four five six\nseven eight nine\n"
expect-register 0: l "four five six\nseven eight nine\n"
expect-register 1: none
expect-register -: none

=== V then y a register append
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
register a: c "x"
keys: V"Ay
registers: " a
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "x\none two three\n"
expect-register a: l "x\none two three\n"

=== V then c
text:
  one two three
  four five six
  seven eight nine
cursor: 2,3
keys: Vcnew<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three
  new
  seven eight nine
expect-cursor: 2,3
expect-mode: normal
expect-register ": l "four five six\n"
expect-register 0: none
expect-register 1: l "four five six\n"
expect-register -: none

=== V then c with autoindent
text:
  alpha beta
    gamma delta
  epsilon
cursor: 2,5
set: autoindent
keys: Vcnew<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  alpha beta
    new
  epsilon
expect-cursor: 2,5
expect-mode: normal
expect-register ": l "  gamma delta\n"
expect-register 0: none
expect-register 1: l "  gamma delta\n"
expect-register -: none

=== V then J
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: VjJ
--- vim 9.1
expect-text:
  one two three four five six
  seven eight nine
expect-cursor: 1,14
expect-mode: normal

=== V then greater
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: Vj>
--- vim 9.1
expect-text:
  	one two three
  	four five six
  seven eight nine
expect-cursor: 1,2
expect-mode: normal

=== V then o
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: Vjo
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: visual-line
expect-visual: 2,5

=== V then I inserts at the start
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: VjI+<Esc>
--- vim 9.1
expect-text:
  +one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal

=== V then A appends at the end
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: VjA+<Esc>
--- vim 9.1
expect-text:
  one two three
  four +five six
  seven eight nine
expect-cursor: 2,6
expect-mode: normal

=== v then I on one line
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vlI+<Esc>
--- vim 9.1
expect-text:
  +one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal

=== v then A on one line
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vlA+<Esc>
--- vim 9.1
expect-text:
  one tw+o three
  four five six
  seven eight nine
expect-cursor: 1,7
expect-mode: normal

=== V from below then I
text:
  one two three
  four five six
  seven eight nine
cursor: 2,5
keys: VkI+<Esc>
--- vim 9.1
expect-text:
  one +two three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: normal

=== v then p replaces with a charwise register
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
register a: c "X"
keys: vep
registers: " 0 1 -
--- vim 9.1
expect-text:
  one  three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "two"
expect-register 0: none
expect-register 1: none
expect-register -: c "two"

=== yw then v then p
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: ywwvep
registers: " 0 1 -
--- vim 9.1
expect-text:
  one one  three
  four five six
  seven eight nine
expect-cursor: 1,8
expect-mode: normal
expect-register ": c "two"
expect-register 0: c "one "
expect-register 1: none
expect-register -: c "two"

=== yw then v then P keeps the register
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: ywwveP
registers: " 0 1 -
--- vim 9.1
expect-text:
  one one  three
  four five six
  seven eight nine
expect-cursor: 1,8
expect-mode: normal
expect-register ": c "one "
expect-register 0: c "one "
expect-register 1: none
expect-register -: none

=== yy then v then p splits
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: yyjwvep
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three
  four $
  one two three
   six
  seven eight nine
expect-cursor: 3,1
expect-mode: normal
expect-register ": c "five"
expect-register 0: l "one two three\n"
expect-register 1: none
expect-register -: c "five"

=== yy then V then p
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: yyjVp
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three
  one two three
  seven eight nine
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "four five six\n"
expect-register 0: l "one two three\n"
expect-register 1: l "four five six\n"
expect-register -: none

=== yw then V then p
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: ywjVp
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three
  one $
  seven eight nine
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "four five six\n"
expect-register 0: c "one "
expect-register 1: l "four five six\n"
expect-register -: none

=== v then p twice
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: ywwvepwvep
registers: " 0 1 -
--- vim 9.1
expect-text:
  one one  two
  four five six
  seven eight nine
expect-cursor: 1,12
expect-mode: normal
expect-register ": c "three"
expect-register 0: c "one "
expect-register 1: none
expect-register -: c "three"

=== v then 2p
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: ywwve2p
registers: " 0 1 -
--- vim 9.1
expect-text:
  one one one  three
  four five six
  seven eight nine
expect-cursor: 1,12
expect-mode: normal
expect-register ": c "two"
expect-register 0: c "one "
expect-register 1: none
expect-register -: c "two"

=== v then named register p
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
register a: c "X"
keys: wve"ap
registers: " a 0 1 -
--- vim 9.1
expect-text:
  one X three
  four five six
  seven eight nine
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "two"
expect-register a: c "X"
expect-register 0: none
expect-register 1: none
expect-register -: c "two"

=== v then gv reselects
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: vly<Esc>jgv
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,2
expect-mode: visual
expect-visual: 1,1

=== gv after V
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: Vjy<Esc>gv
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 2,1
expect-mode: visual-line
expect-visual: 1,1

=== gv in Visual mode swaps
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: vly<Esc>jvlgv
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,2
expect-mode: visual
expect-visual: 1,1

=== gv with nothing selected fails
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: gv
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal

=== v then dot repeats on the same size
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: vlldj0.
--- vim 9.1
expect-text:
   two three
  r five six
  seven eight nine
expect-cursor: 2,1
expect-mode: normal

=== V then dot repeats on the same lines
text:
  a
  b
  c
  d
  e
cursor: 1,1
keys: Vjdj.
--- vim 9.1
expect-text:
  c
expect-cursor: 1,1
expect-mode: normal

=== v then c then dot
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: vlcX<Esc>w.
--- vim 9.1
expect-text:
  Xe Xo three
  four five six
  seven eight nine
expect-cursor: 1,4
expect-mode: normal

=== v then greater then dot
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: vj>.
--- vim 9.1
expect-text:
  		one two three
  		four five six
  seven eight nine
expect-cursor: 1,3
expect-mode: normal

=== v then u then dot
text:
  ABC DEF
cursor: 1,1
keys: vlu$.
--- vim 9.1
expect-text:
  abC DEf
expect-cursor: 1,7
expect-mode: normal

=== v then r then dot
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: vlr-w.
--- vim 9.1
expect-text:
  ----two three
  four five six
  seven eight nine
expect-cursor: 1,3
expect-mode: normal

=== v then y then dot does not repeat the yank
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: dwvly.
--- vim 9.1
expect-text:
  three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal

=== v over lines then dot
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vjdk.
--- vim 9.1
expect-text:
  one  eight nine
expect-cursor: 1,5
expect-mode: normal

=== v then $ then dot
text:
  abc
  def
  ghi
cursor: 1,2
keys: v$dj.
--- vim 9.1
expect-text:
  adef
  g
expect-cursor: 2,1
expect-mode: normal

=== v then o then d
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vllohd
registers: " 0 1 -
--- vim 9.1
expect-text:
  one three
  four five six
  seven eight nine
expect-cursor: 1,4
expect-mode: normal
expect-register ": c " two"
expect-register 0: none
expect-register 1: none
expect-register -: c " two"

=== v then d on an empty line
text:
  a
  $
  b
cursor: 2,1
keys: vd
registers: " 0 1 -
--- vim 9.1
expect-text:
  a
  b
expect-cursor: 2,1
expect-mode: normal
expect-register ": c "\n"
expect-register 0: none
expect-register 1: c "\n"
expect-register -: none

=== v then d on the only empty line
text:
  $
cursor: 1,1
keys: vd
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

=== v then y on an empty line
text:
  a
  $
  b
cursor: 2,1
keys: vy
registers: " 0 1 -
--- vim 9.1
expect-text:
  a
  $
  b
expect-cursor: 2,1
expect-mode: normal
expect-register ": c "\n"
expect-register 0: c "\n"
expect-register 1: none
expect-register -: none

=== v then gg
text:
  one two three
  four five six
  seven eight nine
cursor: 2,5
keys: vgg
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: visual
expect-visual: 2,5

=== v then G
text:
  one two three
  four five six
  seven eight nine
cursor: 1,5
keys: vG
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 3,1
expect-mode: visual
expect-visual: 1,5

=== v then j keeps the column
text:
  one two three
  four five six
  seven eight nine
cursor: 1,9
keys: vj
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 2,9
expect-mode: visual
expect-visual: 1,9

=== v then j to a shorter line
text:
  long line here
  ab
cursor: 1,9
keys: vj
--- vim 9.1
expect-text:
  long line here
  ab
expect-cursor: 2,3
expect-mode: visual
expect-visual: 1,9

=== v then j then d to a shorter line
text:
  long line here
  ab
  c
cursor: 1,9
keys: vjd
--- vim 9.1
expect-text:
  long linc
expect-cursor: 1,9
expect-mode: normal

=== v then f
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: vfe
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
expect-cursor: 1,3
expect-mode: visual
expect-visual: 1,1

=== v then t then d
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: vtwd
--- vim 9.1
expect-text:
  wo three
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal

=== v then percent
text:
  (a (b) c)
cursor: 1,1
keys: v%
--- vim 9.1
expect-text:
  (a (b) c)
expect-cursor: 1,9
expect-mode: visual
expect-visual: 1,1

=== v then dollar then j
text:
  ab
  cdef
  g
cursor: 1,1
keys: v$j
--- vim 9.1
expect-text:
  ab
  cdef
  g
expect-cursor: 2,5
expect-mode: visual
expect-visual: 1,1

=== v then ESC keeps the cursor off the end
text:
  abc
cursor: 1,1
keys: v$<Esc>
--- vim 9.1
expect-text:
  abc
expect-cursor: 1,3
expect-mode: normal

=== v then l stops at the end
text:
  abc
cursor: 1,1
keys: vllll
--- vim 9.1
expect-text:
  abc
expect-cursor: 1,4
expect-mode: visual
expect-visual: 1,1

=== v then l at the end with d
text:
  abc
  def
cursor: 1,1
keys: vllld
--- vim 9.1
expect-text:
  def
expect-cursor: 1,1
expect-mode: normal

=== v then iw on space
text:
  a   b
cursor: 1,3
keys: viw
--- vim 9.1
expect-text:
  a   b
expect-cursor: 1,4
expect-mode: visual
expect-visual: 1,2

=== v then i bracket from outside fails
text:
  x (a) y
cursor: 1,1
keys: vi(d
--- vim 9.1
expect-text:
  x () y
expect-cursor: 1,4
expect-mode: normal

=== v then 2j then o then d
text:
  a
  b
  c
  d
cursor: 1,1
keys: v2jod
--- vim 9.1
expect-text:
  $
  d
expect-cursor: 1,1
expect-mode: normal

=== v with the unnamed clipboard off yanks to zero
text:
  one two three
  four five six
  seven eight nine
cursor: 1,1
keys: vey"_dd
registers: " 0 1 -
--- vim 9.1
expect-text:
  four five six
  seven eight nine
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "one"
expect-register 0: c "one"
expect-register 1: none
expect-register -: none
```

Create `plugins/editor/src/test/resources/vim/corpus/visual-block.case`:

```text
# Blockwise Visual mode and blockwise registers.

=== C-v then j then l
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jl
--- vim 9.1
expect-text:
  abcdef
  ghijkl
  mnopqr
expect-cursor: 2,3
expect-mode: visual-block
expect-visual: 1,2

=== C-v then d
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jld
registers: " 0 1 -
--- vim 9.1
expect-text:
  adef
  gjkl
  mnopqr
expect-cursor: 1,2
expect-mode: normal
expect-register ": b2 "bc\nhi"
expect-register 0: none
expect-register 1: b2 "bc\nhi"
expect-register -: none

=== C-v then x
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jjlx
registers: " 0 1 -
--- vim 9.1
expect-text:
  adef
  gjkl
  mpqr
expect-cursor: 1,2
expect-mode: normal
expect-register ": b2 "bc\nhi\nno"
expect-register 0: none
expect-register 1: b2 "bc\nhi\nno"
expect-register -: none

=== C-v then y
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jly
registers: " 0 1 -
--- vim 9.1
expect-text:
  abcdef
  ghijkl
  mnopqr
expect-cursor: 1,2
expect-mode: normal
expect-register ": b2 "bc\nhi"
expect-register 0: b2 "bc\nhi"
expect-register 1: none
expect-register -: none

=== C-v then y then P
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jlyP
registers: " 0 1 -
--- vim 9.1
expect-text:
  abcbcdef
  ghihijkl
  mnopqr
expect-cursor: 1,2
expect-mode: normal
expect-register ": b2 "bc\nhi"
expect-register 0: b2 "bc\nhi"
expect-register 1: none
expect-register -: none

=== C-v then y then p
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jly$p
registers: " 0 1 -
--- vim 9.1
expect-text:
  abcdefbc
  ghijklhi
  mnopqr
expect-cursor: 1,7
expect-mode: normal
expect-register ": b2 "bc\nhi"
expect-register 0: b2 "bc\nhi"
expect-register 1: none
expect-register -: none

=== C-v then y then p on the last line adds lines
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jlyGp
registers: " 0 1 -
--- vim 9.1
expect-text:
  abcdef
  ghijkl
  mbcnopqr
   hi
expect-cursor: 3,2
expect-mode: normal
expect-register ": b2 "bc\nhi"
expect-register 0: b2 "bc\nhi"
expect-register 1: none
expect-register -: none

=== C-v then y then 2p
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jly2p
registers: " 0 1 -
--- vim 9.1
expect-text:
  abbcbccdef
  ghhihiijkl
  mnopqr
expect-cursor: 1,3
expect-mode: normal
expect-register ": b2 "bc\nhi"
expect-register 0: b2 "bc\nhi"
expect-register 1: none
expect-register -: none

=== C-v then y then gp
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jlygp
registers: " 0 1 -
--- vim 9.1
expect-text:
  abbccdef
  ghhiijkl
  mnopqr
expect-cursor: 2,5
expect-mode: normal
expect-register ": b2 "bc\nhi"
expect-register 0: b2 "bc\nhi"
expect-register 1: none
expect-register -: none

=== C-v then y then gP
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jlygP
registers: " 0 1 -
--- vim 9.1
expect-text:
  abcbcdef
  ghihijkl
  mnopqr
expect-cursor: 2,4
expect-mode: normal
expect-register ": b2 "bc\nhi"
expect-register 0: b2 "bc\nhi"
expect-register 1: none
expect-register -: none

=== C-v y then p into a short line
text:
  abcdef
  ab
  abcdefgh
cursor: 1,3
keys: <C-v>jjly$p
registers: " 0 1 -
--- vim 9.1
expect-text:
  abcdefcd
  ab    $
  abcdefcdgh
expect-cursor: 1,7
expect-mode: normal
expect-register ": b2 "cd\n\ncd"
expect-register 0: b2 "cd\n\ncd"
expect-register 1: none
expect-register -: none

=== C-v over a short line then d
text:
  abcdef
  ab
  abcdefgh
cursor: 1,3
keys: <C-v>jjlld
registers: " 0 1 -
--- vim 9.1
expect-text:
  abf
  ab
  abfgh
expect-cursor: 1,3
expect-mode: normal
expect-register ": b3 "cde\n\ncde"
expect-register 0: none
expect-register 1: b3 "cde\n\ncde"
expect-register -: none

=== C-v over a short line then y
text:
  abcdef
  ab
  abcdefgh
cursor: 1,3
keys: <C-v>jjlly
registers: " 0 1 -
--- vim 9.1
expect-text:
  abcdef
  ab
  abcdefgh
expect-cursor: 1,3
expect-mode: normal
expect-register ": b3 "cde\n\ncde"
expect-register 0: b3 "cde\n\ncde"
expect-register 1: none
expect-register -: none

=== C-v then dollar then y
text:
  abcdef
  ab
  abcdefgh
cursor: 1,2
keys: <C-v>jj$y
registers: " 0 1 -
--- vim 9.1
expect-text:
  abcdef
  ab
  abcdefgh
expect-cursor: 1,2
expect-mode: normal
expect-register ": b7 "bcdef\nb\nbcdefgh"
expect-register 0: b7 "bcdef\nb\nbcdefgh"
expect-register 1: none
expect-register -: none

=== C-v then dollar then d
text:
  abcdef
  ab
  abcdefgh
cursor: 1,2
keys: <C-v>jj$d
registers: " 0 1 -
--- vim 9.1
expect-text:
  a
  a
  a
expect-cursor: 1,1
expect-mode: normal
expect-register ": b7 "bcdef\nb\nbcdefgh"
expect-register 0: none
expect-register 1: b7 "bcdef\nb\nbcdefgh"
expect-register -: none

=== C-v then dollar then A
text:
  abcdef
  ab
  abcdefgh
cursor: 1,2
keys: <C-v>jj$A!<Esc>
--- vim 9.1
expect-text:
  abcdef!
  ab!
  abcdefgh!
expect-cursor: 1,2
expect-mode: normal

=== C-v then c
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jlcXY<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  aXYdef
  gXYjkl
  mnopqr
expect-cursor: 1,3
expect-mode: normal
expect-register ": b2 "bc\nhi"
expect-register 0: none
expect-register 1: b2 "bc\nhi"
expect-register -: none

=== C-v then c ended with C-c
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jlcXY<C-c>
--- vim 9.1
expect-text:
  aXYdef
  gXYjkl
  mnopqr
expect-cursor: 1,3
expect-mode: normal

=== C-v then s
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jsZ<Esc>
--- vim 9.1
expect-text:
  aZcdef
  gZijkl
  mnopqr
expect-cursor: 1,2
expect-mode: normal

=== C-v then I
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,3
keys: <C-v>jjI--<Esc>
--- vim 9.1
expect-text:
  ab--cdef
  gh--ijkl
  mn--opqr
expect-cursor: 1,3
expect-mode: normal

=== C-v then I ended with C-c
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,3
keys: <C-v>jjI--<C-c>
--- vim 9.1
expect-text:
  ab--cdef
  gh--ijkl
  mn--opqr
expect-cursor: 1,3
expect-mode: normal

=== C-v then I over a short line
text:
  abcdef
  ab
  abcdefgh
cursor: 1,4
keys: <C-v>jjI+<Esc>
--- vim 9.1
expect-text:
  abc+def
  ab
  abc+defgh
expect-cursor: 1,4
expect-mode: normal

=== C-v then A
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jjlA+<Esc>
--- vim 9.1
expect-text:
  abc+def
  ghi+jkl
  mno+pqr
expect-cursor: 1,2
expect-mode: normal

=== C-v then A over a short line pads
text:
  abcdef
  ab
  abcdefgh
cursor: 1,4
keys: <C-v>jjA+<Esc>
--- vim 9.1
expect-text:
  abcd+ef
  ab  +
  abcd+efgh
expect-cursor: 1,4
expect-mode: normal

=== C-v then I then dot
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,1
keys: <C-v>jI#<Esc>j.
--- vim 9.1
expect-text:
  #abcdef
  ##ghijkl
  #mnopqr
expect-cursor: 2,1
expect-mode: normal

=== C-v then C
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,3
keys: <C-v>jCnew<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  abnew
  ghnew
  mnopqr
expect-cursor: 1,5
expect-mode: normal
expect-register ": b4 "cdef\nijkl"
expect-register 0: none
expect-register 1: b4 "cdef\nijkl"
expect-register -: none

=== C-v then D
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,3
keys: <C-v>jD
registers: " 0 1 -
--- vim 9.1
expect-text:
  ab
  gh
  mnopqr
expect-cursor: 1,2
expect-mode: normal
expect-register ": b4 "cdef\nijkl"
expect-register 0: none
expect-register 1: b4 "cdef\nijkl"
expect-register -: none

=== C-v then X
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,3
keys: <C-v>jX
registers: " 0 1 -
--- vim 9.1
expect-text:
  abdef
  ghjkl
  mnopqr
expect-cursor: 1,3
expect-mode: normal
expect-register ": b1 "c\ni"
expect-register 0: none
expect-register 1: b1 "c\ni"
expect-register -: none

=== C-v then Y
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,3
keys: <C-v>jY
registers: " 0 1 -
--- vim 9.1
expect-text:
  abcdef
  ghijkl
  mnopqr
expect-cursor: 1,3
expect-mode: normal
expect-register ": b1 "c\ni"
expect-register 0: b1 "c\ni"
expect-register 1: none
expect-register -: none

=== C-v then r
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jjlr.
--- vim 9.1
expect-text:
  a..def
  g..jkl
  m..pqr
expect-cursor: 1,2
expect-mode: normal

=== C-v over a short line then r
text:
  abcdef
  ab
  abcdefgh
cursor: 1,3
keys: <C-v>jjllr.
--- vim 9.1
expect-text:
  ab...f
  ab
  ab...fgh
expect-cursor: 1,3
expect-mode: normal

=== C-v then tilde
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jl~
--- vim 9.1
expect-text:
  aBCdef
  gHIjkl
  mnopqr
expect-cursor: 1,2
expect-mode: normal

=== C-v then U
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jlU
--- vim 9.1
expect-text:
  aBCdef
  gHIjkl
  mnopqr
expect-cursor: 1,2
expect-mode: normal

=== C-v then J
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jJ
--- vim 9.1
expect-text:
  abcdef ghijkl
  mnopqr
expect-cursor: 1,7
expect-mode: normal

=== C-v then greater
text:
  a  bc
  d  ef
cursor: 1,2
keys: <C-v>j>
--- vim 9.1
expect-text:
  a	   bc
  d	   ef
expect-cursor: 1,2
expect-mode: normal

=== C-v then greater past the text
text:
  ab cd
  ef gh
cursor: 1,4
keys: <C-v>j>
--- vim 9.1
expect-text:
  ab	   cd
  ef	   gh
expect-cursor: 1,4
expect-mode: normal

=== C-v then less
text:
  a    bc
  d    ef
cursor: 1,2
keys: <C-v>j<lt>
--- vim 9.1
expect-text:
  abc
  def
expect-cursor: 1,2
expect-mode: normal

=== C-v then o
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jlo
--- vim 9.1
expect-text:
  abcdef
  ghijkl
  mnopqr
expect-cursor: 1,2
expect-mode: visual-block
expect-visual: 2,3

=== C-v then O
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jlO
--- vim 9.1
expect-text:
  abcdef
  ghijkl
  mnopqr
expect-cursor: 2,2
expect-mode: visual-block
expect-visual: 1,3

=== C-v then O then d
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jlOhd
--- vim 9.1
expect-text:
  def
  jkl
  mnopqr
expect-cursor: 1,1
expect-mode: normal

=== C-v over a tab then d
text:
  a	bc
  defghijklm
  x
cursor: 1,1
keys: <C-v>jld
registers: " 0 1 -
--- vim 9.1
expect-text:
        bc
  fghijklm
  x
expect-cursor: 1,1
expect-mode: normal
expect-register ": b2 "a \nde"
expect-register 0: none
expect-register 1: b2 "a \nde"
expect-register -: none

=== C-v over a tab then y
text:
  a	bc
  defghijklm
  x
cursor: 2,2
keys: <C-v>kly
registers: " 0 1 -
--- vim 9.1
expect-text:
  a	bc
  defghijklm
  x
expect-cursor: 1,2
expect-mode: normal
expect-register ": b8 "\tb\nefghijkl"
expect-register 0: b8 "\tb\nefghijkl"
expect-register 1: none
expect-register -: none

=== C-v inside a tab then I
text:
  a	bc
  defghijklm
  x
cursor: 2,4
keys: <C-v>kI+<Esc>
--- vim 9.1
expect-text:
  a+	bc
  d+efghijklm
  x
expect-cursor: 1,2
expect-mode: normal

=== C-v then p replaces the block
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: yl<C-v>jlp
registers: " 0 1 -
--- vim 9.1
expect-text:
  abdef
  gbjkl
  mnopqr
expect-cursor: 1,2
expect-mode: normal
expect-register ": b2 "bc\nhi"
expect-register 0: c "b"
expect-register 1: b2 "bc\nhi"
expect-register -: none

=== C-v then p with a block register
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,1
keys: <C-v>jy<C-v>jlp
registers: " 0 1 -
--- vim 9.1
expect-text:
  acdef
  gijkl
  mnopqr
expect-cursor: 1,1
expect-mode: normal
expect-register ": b2 "ab\ngh"
expect-register 0: b1 "a\ng"
expect-register 1: b2 "ab\ngh"
expect-register -: none

=== C-v then p with a linewise register
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: yy<C-v>jp
registers: " 0 1 -
--- vim 9.1
expect-text:
  acdef
  gijkl
  abcdef
  mnopqr
expect-cursor: 3,1
expect-mode: normal
expect-register ": b1 "b\nh"
expect-register 0: l "abcdef\n"
expect-register 1: b1 "b\nh"
expect-register -: none

=== V then p with a block register
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,1
keys: <C-v>jyjVp
registers: " 0 1 -
--- vim 9.1
expect-text:
  abcdef
  a
  g
  mnopqr
expect-cursor: 2,1
expect-mode: normal
expect-register ": l "ghijkl\n"
expect-register 0: b1 "a\ng"
expect-register 1: l "ghijkl\n"
expect-register -: none

=== v then p with a block register
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,1
keys: <C-v>jyGvlp
registers: " 0 1 -
--- vim 9.1
expect-text:
  abcdef
  ghijkl
  aopqr
  g
expect-cursor: 3,1
expect-mode: normal
expect-register ": c "mn"
expect-register 0: b1 "a\ng"
expect-register 1: none
expect-register -: c "mn"

=== C-v then gv
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jl<Esc>gg0gv
--- vim 9.1
expect-text:
  abcdef
  ghijkl
  mnopqr
expect-cursor: 2,3
expect-mode: visual-block
expect-visual: 1,2

=== C-v then dot
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,1
keys: <C-v>jld$.
--- vim 9.1
expect-text:
  cde
  ijk
  mnopqr
expect-cursor: 1,3
expect-mode: normal

=== C-v with wide characters then d
text:
  a日本b
  abcdef
cursor: 1,2
keys: <C-v>jld
registers: " 0 1 -
--- vim 9.1
expect-text:
  a本b
  adef
expect-cursor: 1,2
expect-mode: normal
expect-register ": b2 "日\nbc"
expect-register 0: none
expect-register 1: b2 "日\nbc"
expect-register -: none

=== register a set blockwise then p
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
register a: b2 "xy\nzw"
keys: "ap
registers: a
--- vim 9.1
expect-text:
  abxycdef
  ghzwijkl
  mnopqr
expect-cursor: 1,3
expect-mode: normal
expect-register a: b2 "xy\nzw"

=== register a set blockwise then P on the last line
text:
  abcdef
  ghijkl
  mnopqr
cursor: 3,2
register a: b3 "xy\nz"
keys: "aP
registers: a
--- vim 9.1
expect-text:
  abcdef
  ghijkl
  mxy nopqr
   z
expect-cursor: 3,2
expect-mode: normal
expect-register a: b3 "xy\nz"

=== yank appended to a block register
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,1
keys: <C-v>j"ay<C-v>j"Ay
registers: a
--- vim 9.1
expect-text:
  abcdef
  ghijkl
  mnopqr
expect-cursor: 1,1
expect-mode: normal
expect-register a: b1 "a\ng\na\ng"

=== C-v then c with count text
text:
  abcdef
  ghijkl
  mnopqr
cursor: 1,2
keys: <C-v>jcA<CR>B<Esc>
--- vim 9.1
expect-text:
  aA
  Bcdef
  gijkl
  mnopqr
expect-cursor: 2,1
expect-mode: normal
```

Create `plugins/editor/src/test/resources/vim/corpus/search.case`:

```text
# Search: / ? n N * # g* g#, offsets, the command line, and case rules.

=== slash finds the next match
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /cat<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,5
expect-mode: normal

=== slash skips the match at the cursor
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,5
keys: /cat<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,13
expect-mode: normal

=== slash wraps to the top
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 3,1
keys: /cat<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,5
expect-mode: normal

=== slash with nowrapscan fails at the bottom
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 3,1
set: nowrapscan
keys: /cat<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 3,1
expect-mode: normal

=== slash not found keeps the cursor
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,5
keys: /zebra<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,5
expect-mode: normal

=== slash with a count
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: 2/the<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 3,1
expect-mode: normal

=== question mark finds backward
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 3,5
keys: ?cat<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,13
expect-mode: normal

=== question mark from column 0
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 2,1
keys: ?the<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,16
expect-mode: normal

=== question mark wraps to the bottom
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: ?end<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 3,5
expect-mode: normal

=== n repeats forward
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /the<CR>n
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 3,1
expect-mode: normal

=== N repeats backward
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /the<CR>N
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,1
expect-mode: normal

=== n after question mark goes backward
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 3,1
keys: ?the<CR>n
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,1
expect-mode: normal

=== N after question mark goes forward
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 3,1
keys: ?the<CR>N
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 3,1
expect-mode: normal

=== n with a count
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /a<CR>3n
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,1
expect-mode: normal

=== n with no previous pattern fails
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: n
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,1
expect-mode: normal

=== empty pattern reuses the last
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /the<CR>/<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 3,1
expect-mode: normal

=== slash with an e offset
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /cat/e<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,7
expect-mode: normal

=== slash with e+1
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /cat/e+1<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,8
expect-mode: normal

=== slash with e-1
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /cat/e-1<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,6
expect-mode: normal

=== slash with s+1
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /cat/s+1<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,6
expect-mode: normal

=== slash with b-1
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,6
keys: /cat/b-1<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,12
expect-mode: normal

=== slash with a line offset
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /dog/+1<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 3,1
expect-mode: normal

=== slash with a line offset minus
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /end/-1<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,1
expect-mode: normal

=== slash with a bare line offset
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /dog/0<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,1
expect-mode: normal

=== n keeps the offset
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /a/e<CR>n
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,10
expect-mode: normal

=== empty pattern with a new offset
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /cat<CR>//e<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,7
expect-mode: normal

=== slash with e offset then n does not stick
text:
  xab xab xab
cursor: 1,1
keys: /ab/e<CR>nn
--- vim 9.1
expect-text:
  xab xab xab
expect-cursor: 1,11
expect-mode: normal

=== question mark with e offset
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 3,1
keys: ?cat?e<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,15
expect-mode: normal

=== slash with semicolon chains
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /dog/;/cat<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,13
expect-mode: normal

=== slash with semicolon and offsets
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /dog/;/a/e<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,7
expect-mode: normal

=== slash semicolon question
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 3,1
keys: /end/;?cat<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,13
expect-mode: normal

=== d slash deletes up to the match
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: d/cat<CR>
registers: " 0 1 -
--- vim 9.1
expect-text:
  cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "the "
expect-register 0: none
expect-register 1: c "the "
expect-register -: c "the "

=== d slash with e offset is inclusive
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: d/cat/e<CR>
registers: " 0 1 -
--- vim 9.1
expect-text:
   sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "the cat"
expect-register 0: none
expect-register 1: c "the cat"
expect-register -: c "the cat"

=== d slash with a line offset is linewise
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,5
keys: d/dog/0<CR>
registers: " 0 1 -
--- vim 9.1
expect-text:
  the end
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "the cat sat on the mat\na dog and a cat\n"
expect-register 0: none
expect-register 1: l "the cat sat on the mat\na dog and a cat\n"
expect-register -: none

=== d slash to the next line
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,5
keys: d/dog<CR>
registers: " 0 1 -
--- vim 9.1
expect-text:
  the dog and a cat
  the end
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "cat sat on the mat\na "
expect-register 0: none
expect-register 1: c "cat sat on the mat\na "
expect-register -: none

=== c slash
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: c/sat<CR>X<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  Xsat on the mat
  a dog and a cat
  the end
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "the cat "
expect-register 0: none
expect-register 1: c "the cat "
expect-register -: c "the cat "

=== y slash then dot is not a change
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: y/cat<CR>
registers: " 0 1 -
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "the "
expect-register 0: c "the "
expect-register 1: none
expect-register -: none

=== d slash then dot
text:
  a1 a2 a3 a4 x
cursor: 1,1
keys: d/a<CR>.
--- vim 9.1
expect-text:
  a3 a4 x
expect-cursor: 1,1
expect-mode: normal

=== d n
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /on<CR>0dn
registers: " 0 1 -
--- vim 9.1
expect-text:
  on the mat
  a dog and a cat
  the end
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "the cat sat "
expect-register 0: none
expect-register 1: c "the cat sat "
expect-register -: c "the cat sat "

=== slash then Esc cancels
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /cat<Esc>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,1
expect-mode: normal

=== slash then BS on empty cancels
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /<BS>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,1
expect-mode: normal

=== slash with BS editing
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /caz<BS>t<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,5
expect-mode: normal

=== slash with C-u
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /xyz<C-u>dog<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,3
expect-mode: normal

=== slash with C-w
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /foo cat<C-w>dog<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,1
expect-mode: normal

=== slash with C-r register
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: yiw/<C-r>"<CR>n
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 3,1
expect-mode: normal

=== slash with C-r C-w
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,5
keys: /<C-r><C-w><CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,13
expect-mode: normal

=== slash with Left and insert
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /ct<Left>a<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,5
expect-mode: normal

=== slash with Home and End
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /at<Home>c<End><CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,5
expect-mode: normal

=== slash with Up recalls history
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /dog<CR>gg/<Up><CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,3
expect-mode: normal

=== slash to the end of the line
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: /$<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,22
expect-mode: normal

=== slash to the start of the line
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,5
keys: /^<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,1
expect-mode: normal

=== v slash extends the selection
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: v/cat<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,5
expect-mode: visual
expect-visual: 1,1

=== v slash then d
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: v/cat<CR>d
registers: " 0 1 -
--- vim 9.1
expect-text:
  at sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "the c"
expect-register 0: none
expect-register 1: none
expect-register -: c "the c"

=== v slash cancelled keeps Visual mode
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: vl/cat<Esc>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 1,2
expect-mode: visual
expect-visual: 1,1

=== star searches the word
text:
  foo bar foo
  baz foo
  foobar food
cursor: 1,1
keys: *
--- vim 9.1
expect-text:
  foo bar foo
  baz foo
  foobar food
expect-cursor: 1,9
expect-mode: normal

=== star skips words that contain it
text:
  foo bar foo
  baz foo
  foobar food
cursor: 1,1
keys: **
--- vim 9.1
expect-text:
  foo bar foo
  baz foo
  foobar food
expect-cursor: 2,5
expect-mode: normal

=== star with a count
text:
  foo bar foo
  baz foo
  foobar food
cursor: 1,1
keys: 2*
--- vim 9.1
expect-text:
  foo bar foo
  baz foo
  foobar food
expect-cursor: 2,5
expect-mode: normal

=== hash searches backward
text:
  foo bar foo
  baz foo
  foobar food
cursor: 2,5
keys: #
--- vim 9.1
expect-text:
  foo bar foo
  baz foo
  foobar food
expect-cursor: 1,9
expect-mode: normal

=== g star finds inside words
text:
  foo bar foo
  baz foo
  foobar food
cursor: 1,1
keys: g*g*
--- vim 9.1
expect-text:
  foo bar foo
  baz foo
  foobar food
expect-cursor: 2,5
expect-mode: normal

=== g hash
text:
  foo bar foo
  baz foo
  foobar food
cursor: 3,1
keys: g#
--- vim 9.1
expect-text:
  foo bar foo
  baz foo
  foobar food
expect-cursor: 3,1
expect-mode: normal

=== star on punctuation
text:
  a.b a.b
cursor: 1,2
keys: *
--- vim 9.1
expect-text:
  a.b a.b
expect-cursor: 1,7
expect-mode: normal

=== star after the cursor word
text:
    foo x foo
cursor: 1,1
keys: *
--- vim 9.1
expect-text:
    foo x foo
expect-cursor: 1,9
expect-mode: normal

=== star on a blank line fails
text:
  $
  foo
cursor: 1,1
keys: *
--- vim 9.1
expect-text:
  $
  foo
expect-cursor: 1,1
expect-mode: normal

=== star then n
text:
  foo bar foo
  baz foo
  foobar food
cursor: 1,1
keys: *n
--- vim 9.1
expect-text:
  foo bar foo
  baz foo
  foobar food
expect-cursor: 2,5
expect-mode: normal

=== star sets the pattern for slash
text:
  foo bar foo
  baz foo
  foobar food
cursor: 1,1
keys: */<CR>
--- vim 9.1
expect-text:
  foo bar foo
  baz foo
  foobar food
expect-cursor: 2,5
expect-mode: normal

=== star with ignorecase
text:
  Foo foo FOO
cursor: 1,1
set: ignorecase
keys: *
--- vim 9.1
expect-text:
  Foo foo FOO
expect-cursor: 1,5
expect-mode: normal

=== star ignores smartcase
text:
  Foo foo FOO
cursor: 1,1
set: ignorecase smartcase
keys: *
--- vim 9.1
expect-text:
  Foo foo FOO
expect-cursor: 1,5
expect-mode: normal

=== d star
text:
  foo bar foo
  baz foo
  foobar food
cursor: 1,1
keys: d*
registers: " 0 1 -
--- vim 9.1
expect-text:
  foo
  baz foo
  foobar food
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "foo bar "
expect-register 0: none
expect-register 1: c "foo bar "
expect-register -: c "foo bar "

=== ignorecase finds upper case
text:
  x ABC abc
cursor: 1,1
set: ignorecase
keys: /abc<CR>
--- vim 9.1
expect-text:
  x ABC abc
expect-cursor: 1,3
expect-mode: normal

=== smartcase with upper case matches case
text:
  x abc Abc
cursor: 1,1
set: ignorecase smartcase
keys: /Abc<CR>
--- vim 9.1
expect-text:
  x abc Abc
expect-cursor: 1,7
expect-mode: normal

=== smartcase with lower case ignores case
text:
  x Abc abc
cursor: 1,1
set: ignorecase smartcase
keys: /abc<CR>
--- vim 9.1
expect-text:
  x Abc abc
expect-cursor: 1,3
expect-mode: normal

=== backslash c ignores case
text:
  x ABC abc
cursor: 1,1
keys: /\cabc<CR>
--- vim 9.1
expect-text:
  x ABC abc
expect-cursor: 1,3
expect-mode: normal

=== backslash C matches case
text:
  x ABC abc
cursor: 1,1
set: ignorecase
keys: /\Cabc<CR>
--- vim 9.1
expect-text:
  x ABC abc
expect-cursor: 1,7
expect-mode: normal

=== smartcase ignores escaped letters
text:
  x Abc abc
cursor: 1,1
set: ignorecase smartcase
keys: /\Sbc<CR>
--- vim 9.1
expect-text:
  x Abc abc
expect-cursor: 1,3
expect-mode: normal

=== match on the next line when the cursor is on the last character
text:
  abc
  xabc
cursor: 1,3
keys: /abc<CR>
--- vim 9.1
expect-text:
  abc
  xabc
expect-cursor: 2,2
expect-mode: normal

=== match at the cursor with a count wraps
text:
  abc
cursor: 1,1
keys: /abc<CR>
--- vim 9.1
expect-text:
  abc
expect-cursor: 1,1
expect-mode: normal

=== empty match with star
text:
  aaa bbb
cursor: 1,1
keys: /b*<CR>
--- vim 9.1
expect-text:
  aaa bbb
expect-cursor: 1,2
expect-mode: normal

=== search in an empty line
text:
  $
  x
  $
cursor: 1,1
keys: /x<CR>
--- vim 9.1
expect-text:
  $
  x
  $
expect-cursor: 2,1
expect-mode: normal

=== backward search at column 1 of the first line
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 1,1
keys: ?cat<CR>
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,13
expect-mode: normal

=== n after wrap
text:
  the cat sat on the mat
  a dog and a cat
  the end
cursor: 2,15
keys: /cat<CR>n
--- vim 9.1
expect-text:
  the cat sat on the mat
  a dog and a cat
  the end
expect-cursor: 2,13
expect-mode: normal
```

Create `plugins/editor/src/test/resources/vim/corpus/regex.case`:

```text
# The Vim regex translator, checked by searching.

=== regex dot
text:
  abc axc
cursor: 1,2
keys: /a.c<CR>
--- vim 9.1
expect-text:
  abc axc
expect-cursor: 1,5
expect-mode: normal

=== regex star
text:
  xab abbbc
cursor: 1,1
keys: /ab*c<CR>
--- vim 9.1
expect-text:
  xab abbbc
expect-cursor: 1,5
expect-mode: normal

=== regex backslash plus
text:
  ac abbc
cursor: 1,1
keys: /ab\+c<CR>
--- vim 9.1
expect-text:
  ac abbc
expect-cursor: 1,4
expect-mode: normal

=== regex backslash equals
text:
  abd ad
cursor: 1,1
keys: /ab\=d<CR>
--- vim 9.1
expect-text:
  abd ad
expect-cursor: 1,5
expect-mode: normal

=== regex backslash question
text:
  abd ad
cursor: 1,2
keys: /ab\?d<CR>
--- vim 9.1
expect-text:
  abd ad
expect-cursor: 1,5
expect-mode: normal

=== regex brace exact
text:
  aa aaa
cursor: 1,1
keys: /a\{3}<CR>
--- vim 9.1
expect-text:
  aa aaa
expect-cursor: 1,4
expect-mode: normal

=== regex brace range
text:
  x aaaa
cursor: 1,1
keys: /a\{2,3}<CR>e
--- vim 9.1
expect-text:
  x aaaa
expect-cursor: 1,6
expect-mode: normal

=== regex brace lazy
text:
  x aaaa
cursor: 1,1
keys: /a\{-2,}<CR>
--- vim 9.1
expect-text:
  x aaaa
expect-cursor: 1,3
expect-mode: normal

=== regex brace lazy with e
text:
  x aaaa
cursor: 1,1
keys: /a\{-2,}/e<CR>
--- vim 9.1
expect-text:
  x aaaa
expect-cursor: 1,4
expect-mode: normal

=== regex empty brace
text:
  x aaaa
cursor: 1,1
keys: /xa\{}/e<CR>
--- vim 9.1
expect-text:
  x aaaa
expect-cursor: 1,1
expect-mode: normal

=== regex alternation
text:
  one two three
cursor: 1,1
keys: /three\|two<CR>
--- vim 9.1
expect-text:
  one two three
expect-cursor: 1,5
expect-mode: normal

=== regex group and backreference
text:
  ab abab
cursor: 1,1
keys: /\(ab\)\1<CR>
--- vim 9.1
expect-text:
  ab abab
expect-cursor: 1,4
expect-mode: normal

=== regex non capturing group
text:
  ab abab
cursor: 1,1
keys: /\%(ab\)\{2}<CR>
--- vim 9.1
expect-text:
  ab abab
expect-cursor: 1,4
expect-mode: normal

=== regex word boundaries
text:
  cat concat cat
cursor: 1,1
keys: /\<cat\><CR>
--- vim 9.1
expect-text:
  cat concat cat
expect-cursor: 1,12
expect-mode: normal

=== regex word start only
text:
  concat catalog
cursor: 1,1
keys: /\<cat<CR>
--- vim 9.1
expect-text:
  concat catalog
expect-cursor: 1,8
expect-mode: normal

=== regex very magic
text:
  ab abab
cursor: 1,1
keys: /\v(ab){2}<CR>
--- vim 9.1
expect-text:
  ab abab
expect-cursor: 1,4
expect-mode: normal

=== regex very magic alternation
text:
  one two three
cursor: 1,1
keys: /\vthree|two<CR>
--- vim 9.1
expect-text:
  one two three
expect-cursor: 1,5
expect-mode: normal

=== regex very magic word
text:
  cat concat cat
cursor: 1,5
keys: /\v<cat><CR>
--- vim 9.1
expect-text:
  cat concat cat
expect-cursor: 1,12
expect-mode: normal

=== regex nomagic
text:
  a.c abc
cursor: 1,2
keys: /\Ma.c<CR>
--- vim 9.1
expect-text:
  a.c abc
expect-cursor: 1,1
expect-mode: normal

=== regex very nomagic
text:
  a.c abc a*c
cursor: 1,2
keys: /\Va*c<CR>
--- vim 9.1
expect-text:
  a.c abc a*c
expect-cursor: 1,9
expect-mode: normal

=== regex very nomagic with magic dot
text:
  a.c abc
cursor: 1,2
keys: /\Va\.c<CR>
--- vim 9.1
expect-text:
  a.c abc
expect-cursor: 1,5
expect-mode: normal

=== regex caret in the middle is literal
text:
  a^b ab
cursor: 1,3
keys: /a^b<CR>
--- vim 9.1
expect-text:
  a^b ab
expect-cursor: 1,1
expect-mode: normal

=== regex dollar in the middle is literal
text:
  a$b ab
cursor: 1,3
keys: /a$b<CR>
--- vim 9.1
expect-text:
  a$b ab
expect-cursor: 1,1
expect-mode: normal

=== regex dollar at the end
text:
  ab
  xab
cursor: 1,1
keys: /ab$<CR>
--- vim 9.1
expect-text:
  ab
  xab
expect-cursor: 2,2
expect-mode: normal

=== regex caret at the start
text:
  xab
  ab
cursor: 1,1
keys: /^ab<CR>
--- vim 9.1
expect-text:
  xab
  ab
expect-cursor: 2,1
expect-mode: normal

=== regex caret after alternation
text:
  x ab
  ab
cursor: 1,1
keys: /zz\|^ab<CR>
--- vim 9.1
expect-text:
  x ab
  ab
expect-cursor: 2,1
expect-mode: normal

=== regex star at the start is literal
text:
  a*b *b
cursor: 1,1
keys: /*b<CR>
--- vim 9.1
expect-text:
  a*b *b
expect-cursor: 1,2
expect-mode: normal

=== regex newline
text:
  foo
  bar
cursor: 1,1
keys: /o\nb<CR>
--- vim 9.1
expect-text:
  foo
  bar
expect-cursor: 1,3
expect-mode: normal

=== regex underscore s
text:
  foo
    bar
cursor: 1,1
keys: /o\_s*bar<CR>
--- vim 9.1
expect-text:
  foo
    bar
expect-cursor: 1,3
expect-mode: normal

=== regex underscore dot
text:
  foo
  bar baz
cursor: 1,1
keys: /f\_.*z<CR>
--- vim 9.1
expect-text:
  foo
  bar baz
expect-cursor: 1,1
expect-mode: normal

=== regex class s
text:
  ab  cd
cursor: 1,1
keys: /\s\+c<CR>
--- vim 9.1
expect-text:
  ab  cd
expect-cursor: 1,3
expect-mode: normal

=== regex class d
text:
  ab 12
cursor: 1,1
keys: /\d\d<CR>
--- vim 9.1
expect-text:
  ab 12
expect-cursor: 1,4
expect-mode: normal

=== regex class w
text:
  -- ab_1
cursor: 1,1
keys: /\w\+<CR>
--- vim 9.1
expect-text:
  -- ab_1
expect-cursor: 1,4
expect-mode: normal

=== regex class a and l and u
text:
  12 aB
cursor: 1,1
keys: /\l\u<CR>
--- vim 9.1
expect-text:
  12 aB
expect-cursor: 1,4
expect-mode: normal

=== regex class x
text:
  zz 0xFF
cursor: 1,1
keys: /\x\x\x<CR>
--- vim 9.1
expect-text:
  zz 0xFF
expect-cursor: 1,1
expect-mode: normal

=== regex class k
text:
  -- ab
cursor: 1,1
keys: /\k<CR>
--- vim 9.1
expect-text:
  -- ab
expect-cursor: 1,4
expect-mode: normal

=== regex class h
text:
  12 _a
cursor: 1,1
keys: /\h\h<CR>
--- vim 9.1
expect-text:
  12 _a
expect-cursor: 1,4
expect-mode: normal

=== regex negated class S
text:
     ab
cursor: 1,1
keys: /\S<CR>
--- vim 9.1
expect-text:
     ab
expect-cursor: 1,4
expect-mode: normal

=== regex bracket
text:
  xyz abc
cursor: 1,1
keys: /[a-c]\+<CR>
--- vim 9.1
expect-text:
  xyz abc
expect-cursor: 1,5
expect-mode: normal

=== regex bracket negated
text:
  aaa aab
cursor: 1,1
keys: /a[^a ]<CR>
--- vim 9.1
expect-text:
  aaa aab
expect-cursor: 1,6
expect-mode: normal

=== regex bracket with dash first
text:
  x -y
cursor: 1,1
keys: /[-y]<CR>
--- vim 9.1
expect-text:
  x -y
expect-cursor: 1,3
expect-mode: normal

=== regex bracket with right bracket first
text:
  x ]y
cursor: 1,1
keys: /[]y]<CR>
--- vim 9.1
expect-text:
  x ]y
expect-cursor: 1,3
expect-mode: normal

=== regex bracket posix class
text:
  -- 42
cursor: 1,1
keys: /[[:digit:]]<CR>
--- vim 9.1
expect-text:
  -- 42
expect-cursor: 1,4
expect-mode: normal

=== regex bracket escape
text:
  a	b
cursor: 1,1
keys: /[\t]<CR>
--- vim 9.1
expect-text:
  a	b
expect-cursor: 1,2
expect-mode: normal

=== regex unmatched bracket is literal
text:
  x [a
cursor: 1,1
keys: /[a<CR>
--- vim 9.1
expect-text:
  x [a
expect-cursor: 1,3
expect-mode: normal

=== regex zs
text:
  foobar foobaz
cursor: 1,1
keys: /foo\zsbaz<CR>
--- vim 9.1
expect-text:
  foobar foobaz
expect-cursor: 1,11
expect-mode: normal

=== regex ze
text:
  foobar foobaz
cursor: 1,2
keys: /foo\zebaz<CR>
--- vim 9.1
expect-text:
  foobar foobaz
expect-cursor: 1,8
expect-mode: normal

=== regex zs with e offset
text:
  foobar foobaz
cursor: 1,1
keys: /foo\zsba/e<CR>
--- vim 9.1
expect-text:
  foobar foobaz
expect-cursor: 1,5
expect-mode: normal

=== regex tilde with no substitute
text:
  x~y
cursor: 1,1
keys: /\V~<CR>
--- vim 9.1
expect-text:
  x~y
expect-cursor: 1,2
expect-mode: normal

=== regex optional sequence
text:
  fu func
cursor: 1,1
keys: /\<fu\%[nction]\><CR>n
--- vim 9.1
expect-text:
  fu func
expect-cursor: 1,1
expect-mode: normal

=== regex decimal char
text:
  x A
cursor: 1,1
keys: /\%d65<CR>
--- vim 9.1
expect-text:
  x A
expect-cursor: 1,3
expect-mode: normal

=== regex hex char
text:
  x A
cursor: 1,1
keys: /\%x41<CR>
--- vim 9.1
expect-text:
  x A
expect-cursor: 1,3
expect-mode: normal

=== regex start of file
text:
  ab
  ab
cursor: 2,1
keys: /\%^ab<CR>
--- vim 9.1
expect-text:
  ab
  ab
expect-cursor: 1,1
expect-mode: normal

=== regex backslash t
text:
  a	b
cursor: 1,1
keys: /\t<CR>
--- vim 9.1
expect-text:
  a	b
expect-cursor: 1,2
expect-mode: normal

=== regex escaped slash
text:
  a/b
cursor: 1,1
keys: /\/<CR>
--- vim 9.1
expect-text:
  a/b
expect-cursor: 1,2
expect-mode: normal

=== regex backslash and
text:
  foobar barfoo
cursor: 1,1
keys: /.*bar\&foo<CR>
--- vim 9.1
expect-text:
  foobar barfoo
expect-cursor: 1,1
expect-mode: normal

=== regex multibyte
text:
  x é ab
cursor: 1,1
keys: /é<CR>
--- vim 9.1
expect-text:
  x é ab
expect-cursor: 1,3
expect-mode: normal

=== regex ignorecase with class
text:
  ABC abc
cursor: 1,1
set: ignorecase
keys: /[a-c]\{3}<CR>
--- vim 9.1
expect-text:
  ABC abc
expect-cursor: 1,5
expect-mode: normal
```

Create `plugins/editor/src/test/resources/vim/corpus/marks.case`:

`````text
# Marks, the jumplist, the change list and gi.

=== m then backtick returns to the mark
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,5
keys: majj`a
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,5
expect-mode: normal

=== m then quote goes to the first non-blank
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,7
keys: majj'a
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,3
expect-mode: normal

=== quote to an unset mark fails
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,5
keys: 'b
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,5
expect-mode: normal

=== backtick to an unset mark fails
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,5
keys: `b
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,5
expect-mode: normal

=== d backtick deletes to the mark
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,3
keys: majjl d`a
registers: " 0 1 -
--- vim 9.1
expect-text:
  li three
  line four
  line five
expect-cursor: 1,3
expect-mode: normal
expect-register ": c "ne one\n  line two\nline"
expect-register 0: none
expect-register 1: c "ne one\n  line two\nline"
expect-register -: none

=== d quote deletes lines to the mark
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,3
keys: majjd'a
registers: " 0 1 -
--- vim 9.1
expect-text:
  line four
  line five
expect-cursor: 1,1
expect-mode: normal
expect-register ": l "line one\n  line two\nline three\n"
expect-register 0: none
expect-register 1: l "line one\n  line two\nline three\n"
expect-register -: none

=== y backtick
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,3
keys: majly`a
registers: " 0 1 -
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 1,3
expect-mode: normal
expect-register ": c "ne one\n  l"
expect-register 0: c "ne one\n  l"
expect-register 1: none
expect-register -: none

=== mark follows inserted lines
text:
  line one
    line two
  line three
  line four
  line five
cursor: 3,3
keys: maggOnew<Esc>`a
--- vim 9.1
expect-text:
  new
  line one
    line two
  line three
  line four
  line five
expect-cursor: 4,3
expect-mode: normal

=== mark follows deleted lines above
text:
  line one
    line two
  line three
  line four
  line five
cursor: 3,3
keys: maggdd`a
--- vim 9.1
expect-text:
    line two
  line three
  line four
  line five
expect-cursor: 2,3
expect-mode: normal

=== mark on a deleted line is gone
text:
  line one
    line two
  line three
  line four
  line five
cursor: 3,3
keys: maddk'a
--- vim 9.1
expect-text:
  line one
    line two
  line four
  line five
expect-cursor: 2,1
expect-mode: normal

=== mark column stays after an edit in the line
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,6
keys: ma0x`a
--- vim 9.1
expect-text:
  ine one
    line two
  line three
  line four
  line five
expect-cursor: 1,6
expect-mode: normal

=== mark after a join moves to the joined line
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,5
keys: makJ`a
--- vim 9.1
expect-text:
  line one line two
  line three
  line four
  line five
expect-cursor: 1,12
expect-mode: normal

=== mark on a blank line moves when joined
text:
  a
  $
  b
cursor: 2,1
keys: makJ`a
--- vim 9.1
expect-text:
  a
  b
expect-cursor: 1,1
expect-mode: normal

=== mark on a white line moves when joined
text:
  a
     $
  b
cursor: 2,2
keys: makJ`a
--- vim 9.1
expect-text:
  a
  b
expect-cursor: 1,1
expect-mode: normal

=== mark after gJ
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,5
keys: makgJ`a
--- vim 9.1
expect-text:
  line one  line two
  line three
  line four
  line five
expect-cursor: 1,13
expect-mode: normal

=== uppercase mark
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,5
keys: mAG`A
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,5
expect-mode: normal

=== backtick backtick returns after G
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,5
keys: G``
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,5
expect-mode: normal

=== quote quote returns after G
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,5
keys: G''
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,3
expect-mode: normal

=== backtick backtick twice toggles
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,5
keys: G``0``
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 5,1
expect-mode: normal

=== backtick backtick after a search
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: /four<CR>``
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 1,1
expect-mode: normal

=== backtick backtick after star
text:
  a foo b
  foo
cursor: 1,4
keys: *``
--- vim 9.1
expect-text:
  a foo b
  foo
expect-cursor: 1,4
expect-mode: normal

=== backtick backtick at start fails
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,5
keys: ``
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 1,1
expect-mode: normal

=== m quote sets the previous context
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,5
keys: m'G``
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,5
expect-mode: normal

=== jumps with G and gg then C-o
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 3GGgg<C-o>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 5,1
expect-mode: normal

=== C-o twice
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 3GG<C-o><C-o>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 1,1
expect-mode: normal

=== C-o then C-i
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 3GG<C-o><C-o><C-i>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 3,1
expect-mode: normal

=== C-o then Tab
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 3GG<C-o><C-o><Tab>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 3,1
expect-mode: normal

=== C-g does not move the cursor
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,3
keys: <C-g>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,3
expect-mode: normal

=== ga does not move the cursor
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,3
keys: ga
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,3
expect-mode: normal

=== C-o with a count
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2G3G4G2<C-o>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,3
expect-mode: normal

=== C-o at the start fails
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: <C-o>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 1,1
expect-mode: normal

=== C-i at the end fails
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 3G<C-i>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 3,1
expect-mode: normal

=== C-o merges jumps on the same line
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 3G1G3G5G<C-o><C-o>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 1,1
expect-mode: normal

=== percent is a jump
text:
  (a
  b)
cursor: 1,1
keys: %<C-o>
--- vim 9.1
expect-text:
  (a
  b)
expect-cursor: 1,1
expect-mode: normal

=== brace is a jump
text:
  a
  $
  b
cursor: 1,1
keys: }<C-o>
--- vim 9.1
expect-text:
  a
  $
  b
expect-cursor: 1,1
expect-mode: normal

=== search then C-o
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: /three<CR><C-o>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 1,1
expect-mode: normal

=== C-o after an edit elsewhere
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 5Gx1G<C-o>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  ine five
expect-cursor: 5,1
expect-mode: normal

=== g semicolon goes to the last change
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 3Gx1Gg;
--- vim 9.1
expect-text:
  line one
    line two
  ine three
  line four
  line five
expect-cursor: 3,1
expect-mode: normal

=== g semicolon twice
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2Gx4Gx1Gg;g;
--- vim 9.1
expect-text:
  line one
    ine two
  line three
  ine four
  line five
expect-cursor: 2,3
expect-mode: normal

=== g semicolon then g comma
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2Gx4Gx1Gg;g;g,
--- vim 9.1
expect-text:
  line one
    ine two
  line three
  ine four
  line five
expect-cursor: 4,1
expect-mode: normal

=== g semicolon with no changes fails
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: g;
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 1,1
expect-mode: normal

=== g semicolon past the start fails
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2Gxg;g;
--- vim 9.1
expect-text:
  line one
    ine two
  line three
  line four
  line five
expect-cursor: 2,3
expect-mode: normal

=== g comma at the end fails
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2Gxg,
--- vim 9.1
expect-text:
  line one
    ine two
  line three
  line four
  line five
expect-cursor: 2,3
expect-mode: normal

=== changes on one line make one entry
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: xxxwxg;g;
--- vim 9.1
expect-text:
  e ne
    line two
  line three
  line four
  line five
expect-cursor: 1,3
expect-mode: normal

=== changes far apart on one line make two entries
text:
  abbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbc
cursor: 1,1
keys: x$xg;g;
--- vim 9.1
expect-text:
  bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
expect-cursor: 1,1
expect-mode: normal

=== quote dot goes to the last change
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 3Gx1G'.
--- vim 9.1
expect-text:
  line one
    line two
  ine three
  line four
  line five
expect-cursor: 3,1
expect-mode: normal

=== backtick dot
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 3G$x1G`.
--- vim 9.1
expect-text:
  line one
    line two
  line thre
  line four
  line five
expect-cursor: 3,9
expect-mode: normal

=== backtick dot after dd
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 3Gdd1G`.
--- vim 9.1
expect-text:
  line one
    line two
  line four
  line five
expect-cursor: 3,1
expect-mode: normal

=== backtick dot after o
divergent: '. takes each typed character's column, as when typing; Vim fed a whole string at once records where it started
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2Goxy<Esc>gg`.
--- expected
expect-text:
  line one
    line two
  xy
  line three
  line four
  line five
expect-cursor: 3,2
expect-mode: normal

=== backtick dot after p
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: yy3Gpgg`.
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line one
  line four
  line five
expect-cursor: 4,1
expect-mode: normal

=== backtick dot after dd at the end fails
text:
  line one
    line two
  line three
  line four
  line five
cursor: 5,1
keys: ddgg`.
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
expect-cursor: 1,1
expect-mode: normal

=== backtick caret after insert
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2GAxyz<Esc>gg`^
--- vim 9.1
expect-text:
  line one
    line twoxyz
  line three
  line four
  line five
expect-cursor: 2,13
expect-mode: normal

=== gi goes back to the insert
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2Gix<Esc>ggiY<Esc>3Ggiz<Esc>
--- vim 9.1
expect-text:
  Yzline one
    xline two
  line three
  line four
  line five
expect-cursor: 1,2
expect-mode: normal

=== gi without an insert
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,3
keys: giq<Esc>
--- vim 9.1
expect-text:
  line one
    qline two
  line three
  line four
  line five
expect-cursor: 2,3
expect-mode: normal

=== gi after the line got shorter
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2GA!<Esc>0Dgggiz<Esc>
--- vim 9.1
expect-text:
  line one
  z
  line three
  line four
  line five
expect-cursor: 2,1
expect-mode: normal

=== backtick bracket after yank
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2Gywgg`]
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,7
expect-mode: normal

=== quote bracket after yy
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2Gyjgg'[
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,3
expect-mode: normal

=== backtick bracket after put
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: yiw3Gpgg`[
--- vim 9.1
expect-text:
  line one
    line two
  llineine three
  line four
  line five
expect-cursor: 3,2
expect-mode: normal

=== backtick close bracket after put
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: yiw3Gpgg`]
--- vim 9.1
expect-text:
  line one
    line two
  llineine three
  line four
  line five
expect-cursor: 3,5
expect-mode: normal

=== backtick close bracket after linewise put
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: yj3Gpgg`]
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line one
    line two
  line four
  line five
expect-cursor: 5,10
expect-mode: normal

=== backtick bracket after d
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2Gwdwgg`[
--- vim 9.1
expect-text:
  line one
    line $
  line three
  line four
  line five
expect-cursor: 2,7
expect-mode: normal

=== backtick lt after Visual
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2Gwvegg`<lt>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 1,1
expect-mode: visual
expect-visual: 2,8

=== backtick gt after Visual
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2Gwv$<Esc>gg`>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,10
expect-mode: normal

=== quote gt after V
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,3
keys: Vj<Esc>gg'>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,3
expect-mode: normal

=== backtick gt after V
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,3
keys: Vj<Esc>gg`>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,10
expect-mode: normal

=== backtick lt after a backward Visual
text:
  line one
    line two
  line three
  line four
  line five
cursor: 3,5
keys: vk<Esc>G`<lt>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,5
expect-mode: normal

=== m in Visual mode
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: vjmx<Esc>gg`x
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,1
expect-mode: normal

=== m lt sets the Visual start
text:
  line one
    line two
  line three
  line four
  line five
cursor: 2,3
keys: m<lt>`<lt>
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 2,3
expect-mode: normal

=== marks survive undo
text:
  line one
    line two
  line three
  line four
  line five
cursor: 3,3
keys: maggddu`a
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 3,3
expect-mode: normal

=== marks after undo of a line insert
text:
  line one
    line two
  line three
  line four
  line five
cursor: 3,3
keys: maggOx<Esc>u`a
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 3,3
expect-mode: normal

=== d quote with an unset mark fails
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: d'q
registers: " 0 1 -
--- vim 9.1
expect-text:
  line one
    line two
  line three
  line four
  line five
expect-cursor: 1,1
expect-mode: normal
expect-register ": none
expect-register 0: none
expect-register 1: none
expect-register -: none

=== dot mark with count g semicolon
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2Gx3Gx4Gx1G2g;
--- vim 9.1
expect-text:
  line one
    ine two
  ine three
  ine four
  line five
expect-cursor: 3,1
expect-mode: normal

=== Visual backtick extends
text:
  line one
    line two
  line three
  line four
  line five
cursor: 1,1
keys: 2Gmagg0v`ad
registers: " 0 1 -
--- vim 9.1
expect-text:
  ine two
  line three
  line four
  line five
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "line one\n  l"
expect-register 0: none
expect-register 1: c "line one\n  l"
expect-register -: none
`````

Create `plugins/editor/src/test/resources/vim/corpus/numbers.case`:

```text
# CTRL-A and CTRL-X, in Normal and Visual mode.

=== C-a increments the number under the cursor
text:
  x 41 y
cursor: 1,4
keys: <C-a>
--- vim 9.1
expect-text:
  x 42 y
expect-cursor: 1,4
expect-mode: normal

=== C-a finds the number after the cursor
text:
  x 41 y
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  x 42 y
expect-cursor: 1,4
expect-mode: normal

=== C-a with a count
text:
  x 41 y
cursor: 1,1
keys: 5<C-a>
--- vim 9.1
expect-text:
  x 46 y
expect-cursor: 1,4
expect-mode: normal

=== C-x decrements
text:
  x 41 y
cursor: 1,1
keys: <C-x>
--- vim 9.1
expect-text:
  x 40 y
expect-cursor: 1,4
expect-mode: normal

=== C-x through zero
text:
  x 1 y
cursor: 1,1
keys: 3<C-x>
--- vim 9.1
expect-text:
  x -2 y
expect-cursor: 1,4
expect-mode: normal

=== C-a on a negative number
text:
  x -5 y
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  x -4 y
expect-cursor: 1,4
expect-mode: normal

=== C-a on a negative number to zero
text:
  x -1 y
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  x 0 y
expect-cursor: 1,3
expect-mode: normal

=== C-a with the cursor on the minus
text:
  x -5 y
cursor: 1,3
keys: <C-a>
--- vim 9.1
expect-text:
  x -4 y
expect-cursor: 1,4
expect-mode: normal

=== C-a on a word with a hyphen
text:
  foo-5
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  foo-4
expect-cursor: 1,5
expect-mode: normal

=== C-a on hex
text:
  x 0x0f y
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  x 0x10 y
expect-cursor: 1,6
expect-mode: normal

=== C-a on upper hex
text:
  x 0xFF y
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  x 0x100 y
expect-cursor: 1,7
expect-mode: normal

=== C-a on hex keeps the width
text:
  x 0x00ff
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  x 0x0100
expect-cursor: 1,8
expect-mode: normal

=== C-a with the cursor in hex digits
text:
  x 0x1f
cursor: 1,6
keys: <C-a>
--- vim 9.1
expect-text:
  x 0x20
expect-cursor: 1,6
expect-mode: normal

=== C-x on hex wraps
text:
  x 0x0
cursor: 1,1
keys: <C-x>
--- vim 9.1
expect-text:
  x 0xffffffffffffffff
expect-cursor: 1,20
expect-mode: normal

=== C-a on binary
text:
  x 0b101
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  x 0b110
expect-cursor: 1,7
expect-mode: normal

=== C-a on binary keeps the width
text:
  x 0b0011
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  x 0b0100
expect-cursor: 1,8
expect-mode: normal

=== C-x on binary zero
text:
  x 0b0
cursor: 1,1
keys: <C-x>
--- vim 9.1
expect-text:
  x 0b1111111111111111111111111111111111111111111111111111111111111111
expect-cursor: 1,68
expect-mode: normal

=== C-a on octal
text:
  x 007
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  x 010
expect-cursor: 1,5
expect-mode: normal

=== C-a on octal with 8 is decimal
text:
  x 008
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  x 9
expect-cursor: 1,3
expect-mode: normal

=== C-a keeps leading zeros
text:
  x 0099
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  x 100
expect-cursor: 1,5
expect-mode: normal

=== C-a on a number that grows
text:
  x 99
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  x 100
expect-cursor: 1,5
expect-mode: normal

=== C-x on a number that shrinks with zeros
text:
  x 0100
cursor: 1,1
keys: <C-x>
--- vim 9.1
expect-text:
  x 0077
expect-cursor: 1,6
expect-mode: normal

=== C-a without octal
text:
  x 007
cursor: 1,1
set: nrformats=bin,hex
keys: <C-a>
--- vim 9.1
expect-text:
  x 008
expect-cursor: 1,5
expect-mode: normal

=== C-a with alpha
text:
  x a
cursor: 1,1
set: nrformats+=alpha
keys: <C-a>
--- vim 9.1
expect-text:
  y a
expect-cursor: 1,1
expect-mode: normal

=== C-a with alpha at z
text:
  x z
cursor: 1,1
set: nrformats+=alpha
keys: <C-a>
--- vim 9.1
expect-text:
  y z
expect-cursor: 1,1
expect-mode: normal

=== C-x with alpha
text:
  x B
cursor: 1,1
set: nrformats+=alpha
keys: 2<C-x>
--- vim 9.1
expect-text:
  v B
expect-cursor: 1,1
expect-mode: normal

=== C-a with unsigned
text:
  x -5
cursor: 1,1
set: nrformats+=unsigned
keys: <C-a>
--- vim 9.1
expect-text:
  x -6
expect-cursor: 1,4
expect-mode: normal

=== C-x with unsigned at zero
text:
  x 0
cursor: 1,1
set: nrformats+=unsigned
keys: <C-x>
--- vim 9.1
expect-text:
  x 0
expect-cursor: 1,3
expect-mode: normal

=== C-a with blank after a letter
text:
  x-5
cursor: 1,1
set: nrformats+=blank
keys: <C-a>
--- vim 9.1
expect-text:
  x-6
expect-cursor: 1,3
expect-mode: normal

=== C-a with no number fails
text:
  abc
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  abc
expect-cursor: 1,1
expect-mode: normal

=== C-a past the number fails
text:
  12 abc
cursor: 1,4
keys: <C-a>
--- vim 9.1
expect-text:
  12 abc
expect-cursor: 1,4
expect-mode: normal

=== C-a on a big number
text:
  x 18446744073709551615
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  x -18446744073709551615
expect-cursor: 1,23
expect-mode: normal

=== C-a on an overflowing number
text:
  x 99999999999999999999
cursor: 1,1
keys: <C-a>
--- vim 9.1
expect-text:
  x 18446744073709551615
expect-cursor: 1,22
expect-mode: normal

=== C-a then dot
text:
  x 1 y 1
cursor: 1,1
keys: 3<C-a>.
--- vim 9.1
expect-text:
  x 7 y 1
expect-cursor: 1,3
expect-mode: normal

=== C-a then dot with a count
text:
  x 1
cursor: 1,1
keys: <C-a>5.
--- vim 9.1
expect-text:
  x 7
expect-cursor: 1,3
expect-mode: normal

=== C-a then u
text:
  x 1
cursor: 1,1
keys: <C-a>u
--- vim 9.1
expect-text:
  x 1
expect-cursor: 1,1
expect-mode: normal

=== Visual C-a on lines
text:
  1
  x 2
  3 4
cursor: 1,1
keys: Vjj<C-a>
--- vim 9.1
expect-text:
  2
  x 3
  4 4
expect-cursor: 1,1
expect-mode: normal

=== Visual g C-a
text:
  1
  1
  1
cursor: 1,1
keys: VGg<C-a>
--- vim 9.1
expect-text:
  2
  3
  4
expect-cursor: 1,1
expect-mode: normal

=== Visual C-x on lines
text:
  5
  x 7
cursor: 1,1
keys: Vj3<C-x>
--- vim 9.1
expect-text:
  2
  x 4
expect-cursor: 1,1
expect-mode: normal

=== Visual g C-x with a count
text:
  10
  10
  10
cursor: 1,1
keys: VG2g<C-x>
--- vim 9.1
expect-text:
  8
  6
  4
expect-cursor: 1,1
expect-mode: normal

=== Visual g C-a skips lines without numbers
text:
  1
  x
  1
cursor: 1,1
keys: VGg<C-a>
--- vim 9.1
expect-text:
  2
  x
  3
expect-cursor: 1,1
expect-mode: normal

=== Visual C-a in a charwise area
text:
  a1 b2 c3
cursor: 1,4
keys: vl<C-a>
--- vim 9.1
expect-text:
  a1 b3 c3
expect-cursor: 1,4
expect-mode: normal

=== Visual C-a on a negative in the area
text:
  x -5
cursor: 1,3
keys: vl<C-a>
--- vim 9.1
expect-text:
  x -4
expect-cursor: 1,3
expect-mode: normal

=== Visual C-a with the minus outside
text:
  x -5
cursor: 1,4
keys: v<C-a>
--- vim 9.1
expect-text:
  x -6
expect-cursor: 1,4
expect-mode: normal

=== Visual block C-a
text:
  a1 b2
  a1 b2
cursor: 1,4
keys: <C-v>jl<C-a>
--- vim 9.1
expect-text:
  a1 b3
  a1 b3
expect-cursor: 1,4
expect-mode: normal

=== Visual C-a then dot
text:
  1
  1
  1
  1
cursor: 1,1
keys: Vj<C-a>jj.
--- vim 9.1
expect-text:
  2
  2
  2
  2
expect-cursor: 3,1
expect-mode: normal

=== Visual C-a then gv
text:
  1 2
  3 4
cursor: 1,1
keys: Vj<C-a>gv<Esc>
--- vim 9.1
expect-text:
  2 2
  4 4
expect-cursor: 2,1
expect-mode: normal

=== Visual dollar C-a
text:
  x 12
  x 34
cursor: 1,3
keys: <C-v>j$<C-a>
--- vim 9.1
expect-text:
  x 13
  x 35
expect-cursor: 1,3
expect-mode: normal

=== Visual C-a with no number
text:
  abc
cursor: 1,1
keys: v<C-a>
--- vim 9.1
expect-text:
  abc
expect-cursor: 1,1
expect-mode: normal

=== C-a on hex then lower hex keeps case memory
text:
  0xAB 0xab
cursor: 1,1
keys: <C-a>w<C-a>
--- vim 9.1
expect-text:
  0xAC 0xac
expect-cursor: 1,9
expect-mode: normal
```

Create `plugins/editor/src/test/resources/vim/corpus/format.case`:

```text
# gq and gw, and blockwise forced motions.

=== gqq breaks a long line
text:
  lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua
cursor: 1,1
keys: gqq
--- vim 9.1
expect-text:
  lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor
  incididunt ut labore et dolore magna aliqua
expect-cursor: 2,1
expect-mode: normal

=== gqq with textwidth
text:
  lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua
cursor: 1,1
set: textwidth=30
keys: gqq
--- vim 9.1
expect-text:
  lorem ipsum dolor sit amet
  consectetur adipiscing elit
  sed do eiusmod tempor
  incididunt ut labore et dolore
  magna aliqua
expect-cursor: 5,1
expect-mode: normal

=== gqap joins and breaks a paragraph
text:
  one two
  three four five
  six
  $
  next para
cursor: 1,1
set: textwidth=12
keys: gqap
--- vim 9.1
expect-text:
  one two
  three four
  five six
  $
  next para
expect-cursor: 4,1
expect-mode: normal

=== gqap leaves the cursor after the paragraph
text:
  one two
  three
  $
  next
cursor: 1,1
set: textwidth=40
keys: gqap
--- vim 9.1
expect-text:
  one two three
  $
  next
expect-cursor: 2,1
expect-mode: normal

=== gwap keeps the cursor
text:
  one two
  three four five
  six
cursor: 2,3
set: textwidth=12
keys: gwap
--- vim 9.1
expect-text:
  one two
  three four
  five six
expect-cursor: 2,3
expect-mode: normal

=== gq with j
text:
  a b c
  d e f
  g h i
cursor: 1,1
set: textwidth=5
keys: gqj
--- vim 9.1
expect-text:
  a b c
  d e f
  g h i
expect-cursor: 2,1
expect-mode: normal

=== gq brace
text:
  a b c
  d e f
  $
  g
cursor: 1,1
set: textwidth=5
keys: gq}
--- vim 9.1
expect-text:
  a b c
  d e f
  $
  g
expect-cursor: 3,1
expect-mode: normal

=== gq keeps indent
text:
      one two three four five six
cursor: 1,1
set: textwidth=16
keys: gqq
--- vim 9.1
expect-text:
      one two
  three four five
  six
expect-cursor: 3,1
expect-mode: normal

=== gq keeps indent with autoindent
text:
      one two three four five six
cursor: 1,1
set: textwidth=16 autoindent
keys: gqq
--- vim 9.1
expect-text:
      one two
      three four
      five six
expect-cursor: 3,5
expect-mode: normal

=== gq converts space indent to a tab
text:
          one two three four five six seven
cursor: 1,1
set: textwidth=20
keys: gqq
--- vim 9.1
expect-text:
  	one two
  three four five six
  seven
expect-cursor: 3,1
expect-mode: normal

=== gq with expandtab keeps spaces
text:
          one two three four five six seven
cursor: 1,1
set: textwidth=20 expandtab
keys: gqq
--- vim 9.1
expect-text:
          one two
  three four five six
  seven
expect-cursor: 3,1
expect-mode: normal

=== gq a word longer than textwidth
text:
  short averyveryverylongword x
cursor: 1,1
set: textwidth=10
keys: gqq
--- vim 9.1
expect-text:
  short
  averyveryverylongword
  x
expect-cursor: 3,1
expect-mode: normal

=== gq removes trailing spaces at breaks
text:
  aaa   bbb   ccc   ddd
cursor: 1,1
set: textwidth=8
keys: gqq
--- vim 9.1
expect-text:
  aaa
  bbb
  ccc
  ddd
expect-cursor: 4,1
expect-mode: normal

=== gq joins with two spaces after a period
text:
  end.
  next
cursor: 1,1
keys: gqj
--- vim 9.1
expect-text:
  end.  next
expect-cursor: 1,1
expect-mode: normal

=== gq joins with one space with nojoinspaces
text:
  end.
  next
cursor: 1,1
set: nojoinspaces
keys: gqj
--- vim 9.1
expect-text:
  end. next
expect-cursor: 1,1
expect-mode: normal

=== gq short lines stay
text:
  a
  b
cursor: 1,1
keys: gqj
--- vim 9.1
expect-text:
  a b
expect-cursor: 1,1
expect-mode: normal

=== gq blank lines separate paragraphs
text:
  a
  $
  b
cursor: 1,1
keys: gqG
--- vim 9.1
expect-text:
  a
  $
  b
expect-cursor: 3,1
expect-mode: normal

=== gq with count
text:
  a
  b
  c
  d
cursor: 1,1
keys: 3gqq
--- vim 9.1
expect-text:
  a b c
  d
expect-cursor: 1,1
expect-mode: normal

=== gq on hash comments
text:
  # one two three four five six seven eight
cursor: 1,1
set: textwidth=20
keys: gqq
--- vim 9.1
expect-text:
  # one two three four
  # five six seven
  # eight
expect-cursor: 3,1
expect-mode: normal

=== gq joins hash comments
text:
  # one two
  # three four
cursor: 1,1
set: textwidth=40
keys: gqj
--- vim 9.1
expect-text:
  # one two three four
expect-cursor: 1,1
expect-mode: normal

=== gq on slash slash comments
text:
  // one two three four five six seven eight
cursor: 1,1
set: textwidth=20
keys: gqq
--- vim 9.1
expect-text:
  // one two three
  // four five six
  // seven eight
expect-cursor: 3,1
expect-mode: normal

=== gq on indented slash slash comments
text:
      // one two three four five six seven eight
cursor: 1,1
set: textwidth=24
keys: gqq
--- vim 9.1
expect-text:
      // one two three
      // four five six
      // seven eight
expect-cursor: 3,5
expect-mode: normal

=== gq on quoted mail
text:
  > one two three four five six seven eight
cursor: 1,1
set: textwidth=20
keys: gqq
--- vim 9.1
expect-text:
  > one two three four
  > five six seven
  > eight
expect-cursor: 3,1
expect-mode: normal

=== gq on nested quotes
text:
  > > one two three four five six seven
cursor: 1,1
set: textwidth=20
keys: gqq
--- vim 9.1
expect-text:
  > > one two three
  > > four five six
  > > seven
expect-cursor: 3,1
expect-mode: normal

=== gq on a list item
text:
  - one two three four five six seven eight
cursor: 1,1
set: textwidth=20
keys: gqq
--- vim 9.1
expect-text:
  - one two three four
    five six seven
  eight
expect-cursor: 3,1
expect-mode: normal

=== gq on a block comment start
text:
  /* one two three four five six seven eight
cursor: 1,1
set: textwidth=20
keys: gqq
--- vim 9.1
expect-text:
  /* one two three
   * four five six
   * seven eight
expect-cursor: 3,2
expect-mode: normal

=== gq on block comment middle lines
text:
  /*
   * one two three four five six seven eight
   */
cursor: 1,1
set: textwidth=20
keys: gqG
--- vim 9.1
expect-text:
  /*
   * one two three
   * four five six
   * seven eight
   */
expect-cursor: 5,2
expect-mode: normal

=== gq does not join different leaders
text:
  # one
  // two
cursor: 1,1
set: textwidth=40
keys: gqj
--- vim 9.1
expect-text:
  # one
  // two
expect-cursor: 2,1
expect-mode: normal

=== gq does not join a comment and text
text:
  # one
  two
cursor: 1,1
set: textwidth=40
keys: gqj
--- vim 9.1
expect-text:
  # one
  two
expect-cursor: 2,1
expect-mode: normal

=== gq with formatoptions without q
text:
  # one
  # two
cursor: 1,1
set: textwidth=40 formatoptions=tc
keys: gqj
--- vim 9.1
expect-text:
  # one # two
expect-cursor: 1,1
expect-mode: normal

=== Visual gq
text:
  one two
  three four five
  six
cursor: 1,1
set: textwidth=12
keys: Vjgq
--- vim 9.1
expect-text:
  one two
  three four
  five
  six
expect-cursor: 3,1
expect-mode: normal

=== Visual gw keeps the cursor
text:
  one two
  three four five
  six
cursor: 2,2
set: textwidth=12
keys: Vkgw
--- vim 9.1
expect-text:
  one two
  three four
  five
  six
expect-cursor: 1,2
expect-mode: normal

=== gq then dot
text:
  a b c d
  $
  e f g h
cursor: 1,1
set: textwidth=4
keys: gqqjj.
--- vim 9.1
expect-text:
  a b
  c d
  $
  e f
  g h
expect-cursor: 5,1
expect-mode: normal

=== gq then u
text:
  lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua
cursor: 1,1
set: textwidth=30
keys: gqqu
--- vim 9.1
expect-text:
  lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua
expect-cursor: 1,1
expect-mode: normal

=== gqgq
text:
  lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua
cursor: 1,1
set: textwidth=30
keys: gqgq
--- vim 9.1
expect-text:
  lorem ipsum dolor sit amet
  consectetur adipiscing elit
  sed do eiusmod tempor
  incididunt ut labore et dolore
  magna aliqua
expect-cursor: 5,1
expect-mode: normal

=== gww keeps the cursor
text:
  lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua
cursor: 1,20
set: textwidth=30
keys: gww
--- vim 9.1
expect-text:
  lorem ipsum dolor sit amet
  consectetur adipiscing elit
  sed do eiusmod tempor
  incididunt ut labore et dolore
  magna aliqua
expect-cursor: 1,20
expect-mode: normal

=== gq with tabs inside
text:
  a	b	c	d	e	f
cursor: 1,1
set: textwidth=20
keys: gqq
--- vim 9.1
expect-text:
  a	b	c
  d	e	f
expect-cursor: 2,1
expect-mode: normal

=== gq on wide characters
text:
  日本語 日本語 日本語 日本語 日本語
cursor: 1,1
set: textwidth=12
keys: gqq
--- vim 9.1
expect-text:
  日本語
  日本語
  日本語
  日本語
  日本語
expect-cursor: 5,1
expect-mode: normal

=== gq on a paragraph with a long joined line
text:
  a a a a a a a a a a a a a a a a a a a a a a a a a a a a a a a a a a a a a a a a $
  b b b b b b b b b b b b b b b b b b b b b b b b b b b b b b b b b b b b b b b b $
cursor: 1,1
set: textwidth=20
keys: gqj
--- vim 9.1
expect-text:
  a a a a a a a a a a
  a a a a a a a a a a
  a a a a a a a a a a
  a a a a a a a a a a
  b b b b b b b b b b
  b b b b b b b b b b
  b b b b b b b b b b
  b b b b b b b b b b $
expect-cursor: 8,1
expect-mode: normal

=== gq on nroff macro lines
text:
  .TH x
  a
  b
cursor: 1,1
keys: gqG
--- vim 9.1
expect-text:
  .TH x a b
expect-cursor: 1,1
expect-mode: normal

=== d C-v j deletes a block
text:
  abcd
  efgh
  ijkl
cursor: 1,2
keys: d<C-v>jl
--- vim 9.1
expect-text:
  acd
  egh
  ijkl
expect-cursor: 1,3
expect-mode: normal

=== y C-v then p
text:
  abcd
  efgh
  ijkl
cursor: 1,2
keys: y<C-v>jl$p
registers: "
--- vim 9.1
expect-text:
  abcdb
  efghf
  ijkl
expect-cursor: 1,5
expect-mode: normal
expect-register ": b1 "b\nf"

=== d C-v dollar
text:
  abcd
  ef
  ijkl
cursor: 1,2
keys: d<C-v>j$
--- vim 9.1
expect-text:
  acd
  e
  ijkl
expect-cursor: 1,3
expect-mode: normal

=== c C-v j
text:
  abcd
  efgh
cursor: 1,2
keys: c<C-v>jX<Esc>
--- vim 9.1
expect-text:
  aXcd
  eXgh
expect-cursor: 1,2
expect-mode: normal

=== d C-v j then dot
text:
  abcd
  efgh
  ijkl
  mnop
cursor: 1,2
keys: d<C-v>jjj.
--- vim 9.1
expect-text:
  acd
  egh
  ikl
  mop
expect-cursor: 3,2
expect-mode: normal

=== d C-v j then gv
text:
  abcd
  efgh
  ijkl
cursor: 1,2
keys: d<C-v>jgv
--- vim 9.1
expect-text:
  acd
  egh
  ijkl
expect-cursor: 2,2
expect-mode: visual-block
expect-visual: 1,2

=== d C-v with a count
text:
  abcd
  efgh
  ijkl
cursor: 1,1
keys: d<C-v>2j
--- vim 9.1
expect-text:
  bcd
  fgh
  jkl
expect-cursor: 1,1
expect-mode: normal

=== gU C-v j
text:
  abcd
  efgh
cursor: 1,2
keys: gU<C-v>jl
--- vim 9.1
expect-text:
  aBcd
  eFgh
expect-cursor: 1,3
expect-mode: normal
```

- [ ] **Step 3: Run the corpus**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest'
```

Expected: 948 tests pass: every case replays as Vim recorded it, and every command has a case. (`VimDocsTest` still fails until Task 5 regenerates the reference and lists the new divergent case.)

- [ ] **Step 4: Check the recording (needs `vim` on `PATH`)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: the task prints `Recorded 943 cases with vim 9.1.` and `git status` lists nothing: Vim agrees with every expectation. (If your Vim is not 9.1, differences in the `--- vim` line only are expected; restore them with `git checkout`.)

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/vim-v1b
git add plugins/editor/src/test/java/dev/jasper/editor/vim plugins/editor/src/test/resources/vim/corpus
git commit -m "Record Visual-mode, search, mark, number and formatting cases from Vim

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: The Vim bar and the Editor tab

**Files:**
- Replace: `plugins/editor/src/main/java/dev/jasper/editor/vim/ui/VimBar.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ui/VimBarTest.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`

**Interfaces:**
- Consumes: `VimEditor.commandLineText()`, `commandLineCursor()`, `showcmd()` (with the Visual area's size), `documentInfo(…)` (Task 2); `DocumentSession.location()` and `dirty()`.
- Produces: package-private `VimBar.commandLineCursor()` for tests.

While `/` or `?` reads a pattern, the bar's left label shows the command line (control characters as `^X`) and paints a one-pixel cursor after the text before it.

- [ ] **Step 1: Write the failing tests**

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
}
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ui.VimBarTest'
```

Expected: compilation fails: `VimBar.commandLineCursor` does not exist.

- [ ] **Step 3: Draw the command line**

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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * The strip under a tab while Vim mode is on (spec 5.2): the mode or the last message on the left, the partly typed
 * command on the right. The bell flashes it briefly; Jasper never beeps. While {@code /} or {@code ?} reads a pattern,
 * the left side is the command line, with its cursor drawn as a thin bar.
 */
public final class VimBar extends JPanel {
    private static final Color ERROR = new Color(0xD9, 0x4F, 0x4F);

    private final CommandLabel status = new CommandLabel();
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
        colors();
    }

    String statusText() { return status.getText().strip(); }

    /** @return the command line's cursor index while one is shown, else -1 */
    int commandLineCursor() { return status.caret; }

    String commandText() { return command.getText().strip(); }

    boolean flashing() { return flashing; }

    private void refresh() {
        if (editor == null) {
            status.setText(" ");
            command.setText(" ");
            return;
        }
        var line = editor.commandLineText();
        if (line.isPresent()) {
            status.setText(visible(line.get()));
            status.caret = editor.commandLineCursor();
            status.beforeCaret = visible(line.get().substring(0, Math.min(line.get().length(), status.caret)));
            status.repaint();
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

    private void colors() {
        Color background = UIManager.getColor("Panel.background");
        Color highlight = UIManager.getColor("Component.focusColor");
        setBackground(flashing && highlight != null ? highlight : background);
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

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, replace:

```java
            vim = new VimEditor(global.get(), area, defaults());
            vimBar.attach(vim);
```

with:

```java
            vim = new VimEditor(global.get(), area, defaults());
            vim.documentInfo(session::location, session::dirty);
            vimBar.attach(vim);
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: every test passes (3 in `VimBarTest`).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/vim-v1b
git add plugins/editor/src/main/java/dev/jasper/editor/vim/ui plugins/editor/src/test/java/dev/jasper/editor/vim/ui plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java
git commit -m "Show the search command line and the Visual size in the Vim bar

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 5: Documentation, the spec amendments and the final check

**Files:**
- Modify: `docs/vim-divergences.md`, `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`
- Regenerate: `docs/editor-vim-reference.md`

- [ ] **Step 1: List the divergences**

In `docs/vim-divergences.md`, replace:

```markdown
| operators-other.case | equals equals on a blank line | = uses previous-line indent and bracket depth, not Vim's C-indenting |
```

with:

```markdown
| operators-other.case | equals equals on a blank line | = uses previous-line indent and bracket depth, not Vim's C-indenting |
| marks.case | backtick dot after o | '. takes each typed character's column, as when typing; Vim fed a whole string at once records where it started |
```

In `docs/vim-divergences.md`, replace:

```markdown
- **`go` counts characters.** Vim counts bytes.
```

with:

```markdown
- **`go` counts characters.** Vim counts bytes.
- **`ga` shows no digraph.** Vim ends the line with the character's digraph; Jasper has no digraph table.
- **Search patterns are translated.** `VimRegex` turns a Vim pattern into a Java one, and the corpus's `regex.case`
  checks the result against Vim. `\%V`, `\%#`, the `\%23l`-style position atoms, `\@` look-around, `\z(` and
  equivalence classes (`[[=a=]]`) report `E869`. Character classes such as `\w` and `[:alpha:]` are ASCII, as in Vim;
  `\k` and `\<` follow 'iskeyword', treating every letter above U+00FF as a word character.
- **The `'.` mark and the change list follow each typed character**, as when typing in Vim. Keys fed to Vim all at
  once (a mapping, `feedkeys()`) are inserted as one string, so there the mark stays at the insert's start.
```

- [ ] **Step 2: Describe Vim mode in the Editor guide**

In `docs/editor.md`, replace:

```markdown
Vim mode edits the way IdeaVim and VSCodeVim do: Normal, Insert and Replace modes, counts, operators with motions
and text objects, registers, `.`, and undo in Vim's steps. Turn it on with `vim = true` in the settings, the **Vim**
item in the status bar, or **Toggle Vim Mode**: every tab switches at once, and the text and its undo history stay.
The [supported keys](editor-vim-reference.md) are generated from the engine's command table, and
[differences from Vim](vim-divergences.md) lists every deliberate one; the rest is checked against Vim 9 by a
recorded corpus.

- The strip under the text shows the mode (`-- INSERT --`), the partly typed command (`2d`), and messages such as
  `E353: Nothing in register q`. A failing command flashes it; Jasper never beeps.
- The caret is a block in Normal mode, a bar in Insert mode, and an underline after an operator and in Replace mode.
- **Which keys Vim takes.** ⌘ shortcuts on macOS always stay with the Editor. Otherwise Vim takes a key when it has
  a meaning for it in the current mode. In Insert and Replace mode the Editor gets the rest. In Normal mode (and
  after an operator, or `<C-o>` from Insert mode) a key Vim does not use never runs one of the text area's own
  editing actions: Tab, Shift+Tab and, on Linux and Windows, Ctrl+D or Alt+Up flash the Vim bar and change nothing.
  The Editor's commands (Save, Find, Close Tab), Jasper's window shortcuts (such as Ctrl+Shift+T on Linux and
  Windows) and keys a `sethandler` entry gives the Editor still work; a key nothing takes flashes the Vim bar. On Linux and Windows Ctrl+S
  still saves (Vim has no `<C-s>`), while `<C-r>` redoes in Normal mode and `<C-w>` deletes a word in Insert mode.
- `"+` is the clipboard and `"*` the selection (the clipboard where there is none).
- 'tabstop' and 'shiftwidth' start at `tab_size`, 'expandtab' follows the tab's Spaces or Tab setting, and
  'autoindent' is on.
- Not yet: Visual mode, search (`/`, `n`, `*`), marks, scrolling commands, the `:` command line, macros, a vimrc,
  and emulated plugins such as surround and commentary. They follow in later releases.
```

with:

```markdown
Vim mode edits the way IdeaVim and VSCodeVim do: Normal, Insert, Replace and Visual modes, counts, operators with
motions and text objects, registers, `.`, search, marks, and undo in Vim's steps. Turn it on with `vim = true` in the
settings, the **Vim** item in the status bar, or **Toggle Vim Mode**: every tab switches at once, and the text and its
undo history stay. The [supported keys](editor-vim-reference.md) are generated from the engine's command table, and
[differences from Vim](vim-divergences.md) lists every deliberate one; the rest is checked against Vim 9 by a
recorded corpus.

- The strip under the text shows the mode (`-- INSERT --`, `-- VISUAL --`), the partly typed command (`2d`) or the
  Visual area's size (`3`, `2x5`), and messages such as `E353: Nothing in register q`. A failing command flashes it;
  Jasper never beeps.
- `/` and `?` type their pattern in the strip, with Vim's command-line keys (`<C-r>` for a register, `<C-r><C-w>` for
  the word under the cursor, `<Up>` and `<Down>` for history). While you type, the match it would find is highlighted
  ('incsearch'); `<Esc>` puts everything back. Patterns are Vim's (`\v`, `\<`, `\zs`, `\{-}` and the rest); the few
  items Jasper does not translate say so (`E869`).
- Visual mode (`v`, `V`, `<C-v>`) draws the area in the theme's selection colour. Dragging the mouse in Normal mode
  starts it.
- The caret is a block in Normal and Visual mode, a bar in Insert mode, and an underline after an operator and in
  Replace mode.
- **Which keys Vim takes.** ⌘ shortcuts on macOS always stay with the Editor. Otherwise Vim takes a key when it has
  a meaning for it in the current mode, and every key while the strip reads a pattern. In Insert and Replace mode the
  Editor gets the rest. In Normal mode (and after an operator, or `<C-o>` from Insert mode) a key Vim does not use
  never runs one of the text area's own editing actions: Shift+Tab and, on Linux and Windows, Ctrl+D or Alt+Up flash
  the Vim bar and change nothing. The Editor's commands (Save, Find, Close Tab), Jasper's window shortcuts (such as
  Ctrl+Shift+T on Linux and Windows) and keys a `sethandler` entry gives the Editor still work; a key nothing takes
  flashes the Vim bar. On Linux and Windows Ctrl+S still saves (Vim has no `<C-s>`), while `<C-r>` redoes in Normal
  mode, `<C-w>` deletes a word in Insert mode, and Tab (`<C-i>`) goes forward in the jumplist.
- `"+` is the clipboard and `"*` the selection (the clipboard where there is none).
- 'tabstop' and 'shiftwidth' start at `tab_size`, 'expandtab' follows the tab's Spaces or Tab setting, and
  'autoindent' is on. 'incsearch' is on; 'hlsearch' is off until the `:` command line (with `:noh`) arrives.
- `<C-g>` shows the file, its line count and the cursor's position; `ga` the character's code.
- Not yet: scrolling commands (`<C-d>`, `zz`), `H`, `M`, `L` and the other screen-line motions, folds, the `:`
  command line, macros, a vimrc, and emulated plugins such as surround and commentary. They follow in later releases.
  An uppercase mark (`mA`) works within its own tab.
```

- [ ] **Step 3: Add the GUI checks for the user**

Append to `docs/editor-verification.md`:

```markdown

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
```

- [ ] **Step 4: Record the state**

In `docs/STATUS.md`, replace:

```markdown
## Current state — 2026-09-23

- **Editor Vim mode, plan V1a** (branch `claude/vim-mode`, plan
```

with:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V1b** (branch `claude/vim-v1b`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v1b.md`, written 2026-09-28): Visual mode (charwise,
  linewise, blockwise, with block `I`/`A`/`c`, `p` over an area, `gv` and `.` on an area of the same size), `/` and `?`
  with offsets and `;`, `n N * # g* g#`, the Vim regex translator (`dev.jasper.editor.vim.regex`), 'incsearch' and
  'hlsearch' highlights, marks, the jumplist and change list, `gi`, `<C-a>`/`<C-x>` (and `g<C-a>`), `gq`/`gw`,
  `<C-g>`, `ga`, blockwise registers and blockwise forced motions. Scrolling, the screen-line motions and folds moved
  to plan V1c (spec 13). Every file in the plan was compiled and run against Vim 9.1 before the plan was written: 946
  corpus cases (943 recorded, 3 divergent).
  - Not done: executing the plan; GUI checks 31–40 of the [checklist](editor-verification.md) (user-run); plans V1c,
    V2, V3 and V4.
- **Editor Vim mode, plan V1a** (branch `claude/vim-mode`, plan
```

- [ ] **Step 5: Amend the spec**

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
amends sections 3.8, 4.1 and 13, as marked **Amended (V1a)**.
```

with:

```markdown
amends sections 3.8, 4.1 and 13, as marked **Amended (V1a)**. Plan V1b (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v1b.md`) amends sections 3.5, 3.6, 3.8, 5.2, 5.3, 11.1 and 13, as marked **Amended (V1b)**.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- `a`–`z` per tab; `A`–`Z` global, remembering the document key, so `'A` selects (or opens) that document's tab.
```

with:

```markdown
- `a`–`z` per tab; `A`–`Z` global, remembering the document key, so `'A` selects (or opens) that document's tab. **Amended (V1b):** until plan V2's buffer commands, an uppercase mark works within its own tab.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
Each register holds text and a type: charwise, linewise or blockwise (with width).
```

with:

```markdown
Each register holds text and a type: charwise, linewise or blockwise (with width). **Amended (V1b):** setting a register directly (`setreg()`, the corpus's `register x:`) leaves the unnamed register where it points, as Vim does.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
except `incsearch`, `hlsearch`, `showcmd` and `showmode`, which are on, as in IdeaVim.
```

with:

```markdown
except `incsearch`, `hlsearch`, `showcmd` and `showmode`, which are on, as in IdeaVim. **Amended (V1b):** `hlsearch` starts off until plan V2 adds `:noh` and `:set` to turn the highlight off.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- the command line: `:`, `/` and `?` turn the left part into an editable field with the prompt character. It has its own key handling (section 6.1) and returns focus to the text area when it closes.
```

with:

```markdown
- the command line: `:`, `/` and `?` turn the left part into an editable field with the prompt character. It has its own key handling (section 6.1) and returns focus to the text area when it closes. **Amended (V1b):** the bar draws the command line and its cursor while keys keep going through the text area, which gives every key to the command line; `/` and `?` arrive in V1b, `:` in V2.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- Visual selections: charwise and linewise use the text area's selection; blockwise uses a highlighter painting one rectangle per line in display columns (tabs expanded), including past short lines when `$` was used.
```

with:

```markdown
- Visual selections: charwise and linewise use the text area's selection; blockwise uses a highlighter painting one rectangle per line in display columns (tabs expanded), including past short lines when `$` was used. **Amended (V1b):** all three are drawn by a highlighter in the selection colour, one range per line for a block (a tab the block cuts is drawn whole); the text area's selection stays empty so the caret stays on the Vim cursor.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
Optional fields: `set:` (options), `marks:` (initial marks), `clipboard:`, `vimrc:` (lines for mapping and extension cases), `ext:` (extensions to enable), `expect-marks:`, `expect-search:`, `expect-message:`.
```

with:

```markdown
Optional fields: `set:` (options), `marks:` (initial marks), `clipboard:`, `vimrc:` (lines for mapping and extension cases), `ext:` (extensions to enable), `expect-marks:`, `expect-search:`, `expect-message:`. **Amended (V1b):** `expect-visual:` gives a Visual area's other end; a blockwise register's type is `b` and its width (`b3`, `getregtype()` in letters); the mode `command-line` is Vim's `c`.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- **Plan V1b: Visual mode, search and navigation.** Visual char/line/block with the block highlighter, `VimRegex`, `/ ? n N * #` with offsets, `hlsearch`/`incsearch`, marks, jumplist and change list, scrolling, folds, `<C-a>`/`<C-x>`, `gq`/`gw`; and, moved from V1a, the screen-line motions, `gi`, `<C-g>`, `ga` and blockwise forced motions.
```

with:

```markdown
- **Plan V1b: Visual mode, search and navigation.** Visual char/line/block with the block highlighter, `VimRegex`, `/ ? n N * #` with offsets, `hlsearch`/`incsearch`, marks, jumplist and change list, scrolling, folds, `<C-a>`/`<C-x>`, `gq`/`gw`; and, moved from V1a, the screen-line motions, `gi`, `<C-g>`, `ga` and blockwise forced motions. **Amended (V1b):** split in two by the user on 2026-09-28; scrolling, the screen-line motions (`H M L gj gk g0 g^ g$ gm`) and folds move to plan V1c.
- **Plan V1c: the view.** Scrolling (`<C-d> <C-u> <C-f> <C-b> <C-e> <C-y> zz zt zb`), `H M L` and the other screen-line motions, and folds.
```

- [ ] **Step 6: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: `docs/editor-vim-reference.md` changes: it gains the Visual-mode table and the new Normal-mode commands.

- [ ] **Step 7: Run everything**

Run:

```bash
./gradlew check
```

Expected: BUILD SUCCESSFUL (3,372 tests, 0 failed, 5 skipped).

- [ ] **Step 8: Check source hygiene**

Run the snippet in `AGENTS.md` ("Source hygiene"). Expected: no output.

- [ ] **Step 9: Commit**

```bash
git branch --show-current   # must print claude/vim-v1b
git add docs
git commit -m "Document Vim mode V1b: Visual mode, search, marks and the plan V1c split

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
