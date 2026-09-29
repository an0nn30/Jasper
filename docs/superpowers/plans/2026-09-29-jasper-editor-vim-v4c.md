# Editor Vim mode, plan V4c: matchit — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The matchit extension of spec section 8, as Vim 9.1 ships it: `%` and `g%` go between the words of a group (`if`, `else`, `endif`; HTML tags; `\begin`, `\end`) as the tab's language defines them, `[%` and `]%` go to the enclosing group's ends, and Visual `a%` takes the group; switched on by `set matchit`, `packadd matchit` or a `Plug` line.

**Architecture:** The port follows matchit.vim (`matchit#Match_wrapper`, `matchit#MultiMatch` and their helpers) step by step: it builds Vim patterns out of `b:match_words`, resolves their back references and moves with searchpair(). So the engine first gains what the plugin needs: look-arounds and column items in Jasper's Vim regex (`\@<=`, `\%23c`; `/` and `:s` get them too), Vim script's string functions over it (`VimStrings`: `=~`, match(), matchend(), substitute(), split()), and searchpair() (`Search.searchpair`, on a `findOnce` taken out of searchit). Each language's `b:match_words`, `b:match_ignorecase`, `b:match_skip` and the pairs it adds to 'matchpairs' come from a table Vim 9.1 made with its filetype plugins loaded (`matchit-languages.tsv`), by the tab's RSyntaxTextArea style; syntax groups for the skip come from the tab's tokens. The corpus gains `filetype:`, which gives the tab that style and has Vim load the filetype plugin and syntax, and the pin `matchit = packadd` (Vim's own copy).

**Tech Stack:** Java 25 (JBR), Swing, RSyntaxTextArea 3.6.0, JUnit 6 and AssertJ, Vim 9 (only for `recordVimCorpus`).

**Spec:** `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md` (plan V4 of section 13, split into V4a–V4d; sections 3.7, 8 and 11.1). Plans V1a–V4b are merged; their headers describe the engine and the extension framework.

**Status:** Written 2026-09-29 on branch `claude/vim-v4c` (worktree `.worktrees/vim-v4c`, from `main` at 4d7efdfe). Every source file, test and corpus case below was compiled and run before the plan was written: against Vim 9.1 (with matchit, its filetype plugins and syntax for the language cases) all 2164 corpus cases (98 new) passed, with and without a final line break. Applied to a fresh checkout of `4d7efdfe` exactly as written, `./gradlew check` passed (7,031 tests).

## Deviations from the spec (approved scope, adjusted in this plan)

Task 4 writes these into the spec as **Amended (V4c)**.

- **V4c and V4d.** The user split the rest of V4 on 2026-09-29: V4c is matchit, V4d is sneak.
- **The regex gains look-arounds and columns** (spec 3.7 listed `\@` and `\%23c` as unsupported): `\@=`, `\@!`, `\@>`, `\@<=`, `\@<!` (a byte limit, `\@12<=`, is read and not needed) and `\%23c`, `\%<23c`, `\%>23c`. Columns count characters, which differs from Vim's bytes only in lines with characters beyond ASCII; repeats inside a look-behind reach back at most 1000 characters (Java needs a bound).
- **The languages** are the RSyntaxTextArea styles Vim has a filetype plugin for; their `b:match_words`, `b:match_ignorecase`, `b:match_skip` and 'matchpairs' additions are the table Vim 9.1 made. There are no buffer variables to set them by hand. A skip given as syntax groups (`s:comment\|string`) reads the tab's tokens as groups named `Comment`, `String`, `Character` and `Regex`; `r:` and `R:` are exact; any other skip expression (the shell's, Ruby's, PHP's) is taken as `s:comment\|string`.
- **'matchpairs' follows the language**, as the filetype plugins add to it (`<:>` for HTML, PHP, SQL, Markdown, Rust), with matchit on or off.
- **Not there:** `:MatchEnable`, `:MatchDisable` and `:MatchDebug`; `g:no_plugin_maps` keeps the mappings out when it is set and not 0.

## Global Constraints

- Java 25 on the JetBrains Runtime; always `./gradlew`, never a system Gradle. Work in `.worktrees/vim-v4c` on `claude/vim-v4c`; check `git branch --show-current` before every commit (other sessions switch branches in the main checkout). Never commit on `main`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.
- The Editor plugin compiles against the SDK only (`verifyPluginArchitecture`, a root task). `dev.jasper.editor.vim` may use `vim.regex`, RSyntaxTextArea, Swing and the JDK; `vim.regex` only the JDK. No SDK change.
- No interface without two real implementations; this plan adds none.
- Source hygiene: never put raw control, private-use or unpaired surrogate characters in `.java` files; use escapes. No run of three backticks may appear in any file this plan embeds (the docs link check strips fenced blocks lazily).
- **Do not launch the GUI.** GUI checks go in `docs/editor-verification.md` for the user.
- Engine code runs on the event thread in the app. Tests that type through a `VimEditor` run inside `SwingUtilities.invokeAndWait`.
- The corpus is the authority on Vim behaviour, and every case also runs without a final line break (`VimCorpusTest`); mark a case `divergent:` only for a documented, deliberate difference. Give new commands cases on the one empty line and on the last line.
- `vim` must be on `PATH` for `recordVimCorpus` (with `git` and the network for the pinned plugins; matchit is Vim's own). `check` never runs Vim or git.

## File structure

New, `plugins/editor/src/main/java/dev/jasper/editor/vim/`:

| File | Responsibility |
|---|---|
| `VimStrings.java` | Vim script's string functions with patterns: `=~`, match(), matchend(), matchstr(), substitute(), split(), escape(), strpart() |
| `Matchit.java` | the `<Plug>(Matchit…)` motions and `<Plug>(MatchitVisualTextObject)`; `:packadd` |
| `MatchitLanguages.java` | the languages' `b:match_*` and 'matchpairs', read from `matchit-languages.tsv` |

New resource: `plugins/editor/src/main/resources/dev/jasper/editor/vim/matchit-languages.tsv`. Changed: `vim/regex/VimRegex` (look-arounds, columns), `Search` (`findOnce`, `searchpair`, `Match.submatch`), `Target` (`area`), `Machine` (a motion's Visual area; 'foldopen' for matchit), `Options` (`matchit`), `Extensions`, `VimCommands`, `Ex` (`:packadd`), `VimEditor` ('matchpairs' by language).
Tests: `VimStringsTest`, `SearchPairTest` (new); `VimRegexTest`, `ExtensionsTest`, `CorpusCase`, `CorpusFile`, `CorpusRunner`, `RecordCorpus` (`filetype:`, `packadd` pins). Corpus: `regex-lookaround.case`, `ext-matchit.case`. Changed: `plugins/editor/vim-oracle.properties`.
Docs: `docs/vim-divergences.md`, `docs/editor-vim-reference.md` (generated), `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec.

---

### Task 1: Look-arounds and columns in Vim patterns

**Files:**
- Create: `plugins/editor/src/test/resources/vim/corpus/regex-lookaround.case`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/regex/VimRegex.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/regex/VimRegexTest.java`

**Interfaces:**
- Produces: in `VimRegex`, the multis `\@=`, `\@!`, `\@>`, `\@<=`, `\@<!` (and `\@N<=`, `\@N<!`) as Java look-arounds and an atomic group, and the items `\%Nc`, `\%<Nc`, `\%>Nc` as look-behinds on the line; `\@` with anything else fails with `E59: Invalid character after \@`. Every pattern user (`/`, `?`, `:s`, `:g`, matchit) gets them.

Java looks behind only as far as it can count, so repeats inside a look-behind are bounded (`Translator.bounded`). A column is a count of characters from the line's start, which Vim counts in bytes.

- [ ] **Step 1: Write the failing tests and cases**

In `plugins/editor/src/test/java/dev/jasper/editor/vim/regex/VimRegexTest.java`, replace:

```java
    }

    @Test void errorsNameTheProblem() {
        assertThatThrownBy(() -> VimRegex.compile("a\\%Vb", PLAIN)).hasMessage("E869: Unsupported in Jasper's Vim regex: \\%V");
        assertThatThrownBy(() -> VimRegex.compile("a\\@<=b", PLAIN)).hasMessage("E869: Unsupported in Jasper's Vim regex: \\@");
        assertThatThrownBy(() -> VimRegex.compile("a\\%23lb", PLAIN)).hasMessageStartingWith("E869: Unsupported in Jasper's Vim regex: \\%23l");
        assertThatThrownBy(() -> VimRegex.compile("\\(a", PLAIN)).hasMessage("E54: Unmatched \\(");
```

with:

```java
    }

    @Test void lookaroundsAndColumns() {
        assertThat(find("foo\\(bar\\)\\@=", "foobaz foobar")).as("\\@= looks ahead").isEqualTo("foo");
        assertThat(find("foo\\(bar\\)\\@!", "foobar foobaz")).isEqualTo("foo");
        assertThat(find("\\(foo\\)\\@<=bar", "xbar foobar")).as("\\@<= looks behind").isEqualTo("bar");
        assertThat(find("\\(foo\\)\\@1<!bar", "foobar xbar")).isEqualTo("bar");
        assertThat(find("\\(a*\\)\\@>a", "aaa")).as("\\@> keeps what the atom matched").isNull();
        assertThat(find("\\%3cx", "xxx")).as("the third column").isEqualTo("x");
        assertThat(find("\\%<3cx.*", "xxxx")).isEqualTo("xxxx");
        assertThat(find("\\%>2cx.*", "xxxx")).isEqualTo("xx");
        assertThat(find("^.*\\%3c\\%(ab\\)", "xxab")).as("matchit's column anchors").isEqualTo("xxab");
        assertThat(find("\\%(;\\s*\\|^\\s*\\)\\@<=if", "x; if")).as("repeats in a look-behind").isEqualTo("if");
        assertThat(find("\\%(\\\\\\@1<!\\%(\\\\\\\\\\)*\\)\\@<=\\\\(", "a\\(b")).as("matchit's s:notslash behind").isEqualTo("\\(");
    }

    @Test void errorsNameTheProblem() {
        assertThatThrownBy(() -> VimRegex.compile("a\\%Vb", PLAIN)).hasMessage("E869: Unsupported in Jasper's Vim regex: \\%V");
        assertThatThrownBy(() -> VimRegex.compile("a\\@xb", PLAIN)).hasMessage("E59: Invalid character after \\@");
        assertThatThrownBy(() -> VimRegex.compile("a\\%23lb", PLAIN)).hasMessageStartingWith("E869: Unsupported in Jasper's Vim regex: \\%23l");
        assertThatThrownBy(() -> VimRegex.compile("\\(a", PLAIN)).hasMessage("E54: Unmatched \\(");
```

Create `plugins/editor/src/test/resources/vim/corpus/regex-lookaround.case`:

```text
# Lookarounds and column items in patterns (plan V4c).

=== search with a lookahead
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 1,1
keys: /foo\(baz\)\@=<CR>
--- vim 9.1
expect-text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
expect-cursor: 1,8
expect-mode: normal

=== search with a negative lookahead
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 1,1
keys: /foo\(bar\)\@!<CR>
--- vim 9.1
expect-text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
expect-cursor: 1,8
expect-mode: normal

=== search with a lookbehind
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 1,1
keys: /\(foo\)\@<=bar<CR>
--- vim 9.1
expect-text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
expect-cursor: 1,4
expect-mode: normal

=== search with a negative lookbehind
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 1,1
keys: /\(foo\)\@<!bar<CR>
--- vim 9.1
expect-text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
expect-cursor: 2,2
expect-mode: normal

=== search with a lookbehind and a byte limit
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 1,1
keys: /\(foo\)\@3<=bar<CR>
--- vim 9.1
expect-text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
expect-cursor: 1,4
expect-mode: normal

=== search backward with a lookbehind
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 4,1
keys: ?\(foo\)\@<=ba.<CR>
--- vim 9.1
expect-text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
expect-cursor: 2,9
expect-mode: normal

=== search with an atom as a whole
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 1,1
keys: /\(o*\)\@>b<CR>
--- vim 9.1
expect-text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
expect-cursor: 1,2
expect-mode: normal

=== search in a column
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 1,1
keys: /\%5cabc<CR>
--- vim 9.1
expect-text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
expect-cursor: 4,5
expect-mode: normal

=== search before a column
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 4,5
keys: /\%<3cif<CR>
--- vim 9.1
expect-text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
expect-cursor: 4,5
expect-mode: normal

=== search after a column
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 1,1
keys: /\%>4cabc<CR>
--- vim 9.1
expect-text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
expect-cursor: 4,5
expect-mode: normal

=== search with a column and zs
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 1,1
keys: /^.*\%9c\zsabc<CR>
--- vim 9.1
expect-text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
expect-cursor: 4,9
expect-mode: normal

=== lookbehind across a line break
text:
  a
  b
  ab
cursor: 1,1
keys: /\(a\n\)\@<=b<CR>
--- vim 9.1
expect-text:
  a
  b
  ab
expect-cursor: 2,1
expect-mode: normal

=== lookbehind at the start of a line
text:
  ab
  b
cursor: 1,1
keys: /\(a\)\@<!b<CR>
--- vim 9.1
expect-text:
  ab
  b
expect-cursor: 2,1
expect-mode: normal

=== substitute with a lookbehind
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 1,1
keys: :%s/\(foo\)\@<=ba/BA/g<CR>
--- vim 9.1
expect-text:
  fooBAr fooBAz
  xbar fooBAr
    if x then
  abc abc abc
expect-cursor: 2,1
expect-mode: normal

=== substitute after a column
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 4,1
keys: :s/\%>4cabc/X/g<CR>
--- vim 9.1
expect-text:
  foobar foobaz
  xbar foobar
    if x then
  abc X X
expect-cursor: 4,1
expect-mode: normal

=== substitute before a column
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 4,1
keys: :s/\%<6cabc/X/g<CR>
--- vim 9.1
expect-text:
  foobar foobaz
  xbar foobar
    if x then
  X X abc
expect-cursor: 4,1
expect-mode: normal

=== a column on the one empty line
text:
  $
cursor: 1,1
keys: /\%1c<CR>
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal

=== a lookahead on the last line
text:
  x
  ab
cursor: 1,1
keys: /a\(b\)\@=<CR>
--- vim 9.1
expect-text:
  x
  ab
expect-cursor: 2,1
expect-mode: normal

=== an invalid item after at
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 1,1
keys: /a\@x<CR>
output: yes
--- vim 9.1
expect-text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
expect-cursor: 1,1
expect-mode: normal
expect-output:
  /a\@x $
  E59: Invalid character after \@

=== d with a lookahead search
text:
  foobar foobaz
  xbar foobar
    if x then
  abc abc abc
cursor: 1,1
keys: d/\(baz\)\@=<CR>
registers: " 0 1 -
--- vim 9.1
expect-text:
  baz
  xbar foobar
    if x then
  abc abc abc
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "foobar foo"
expect-register 0: none
expect-register 1: c "foobar foo"
expect-register -: c "foobar foo"
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.regex.VimRegexTest'
```

Expected: FAIL: `lookaroundsAndColumns` fails with `E869: Unsupported in Jasper's Vim regex: \@`, and `errorsNameTheProblem` expects E59.

- [ ] **Step 3: Translate the look-arounds and columns**

In `plugins/editor/src/main/java/dev/jasper/editor/vim/regex/VimRegex.java`, replace:

```java
                case '+' -> result = group(atom) + "+";
                case '=', '?' -> result = group(atom) + "?";
                case '@' -> throw unsupported("\\@");
                default -> result = group(atom) + brace();
            }
```

with:

```java
                case '+' -> result = group(atom) + "+";
                case '=', '?' -> result = group(atom) + "?";
                case '@' -> result = lookaround(atom);
                default -> result = group(atom) + brace();
            }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/regex/VimRegex.java`, replace:

```java

        private static String group(String atom) { return "(?:" + atom + ")"; }

        /** read_limits: {@code \{n,m}}, {@code \{-n,m}} and the rest, after the brace. */
```

with:

```java

        private static String group(String atom) { return "(?:" + atom + ")"; }

        /**
         * The {@code \@} multis, after the {@code @}: {@code \@=} and {@code \@!} look ahead, {@code \@<=} and
         * {@code \@<!} (with a byte limit, {@code \@12<=}, which Java does not need) look behind, {@code \@>} matches
         * the atom as a whole.
         */
        String lookaround(String atom) {
            while (Character.isDigit(at(pos))) pos++;
            char kind = at(pos);
            String java;
            if (kind == '=' || kind == '!' || kind == '>') {
                pos++;
                java = kind == '=' ? "(?=" : kind == '!' ? "(?!" : "(?>";
            } else if (kind == '<' && (at(pos + 1) == '=' || at(pos + 1) == '!')) {
                java = at(pos + 1) == '=' ? "(?<=" : "(?<!";
                pos += 2;
                // Java looks behind only as far as it can count: repeats inside get a bound.
                atom = bounded(atom);
            } else throw error("E59: Invalid character after \\@");
            resync();
            return java + atom + ")";
        }

        /** How far a repeat inside a look-behind may reach. */
        private static final int BEHIND = 1000;

        /** A Java pattern whose unbounded repeats ({@code *}, {@code +}, {@code {n,}}) outside classes reach {@link #BEHIND}. */
        static String bounded(String java) {
            StringBuilder out = new StringBuilder();
            int classes = 0;
            for (int i = 0; i < java.length(); i++) {
                char c = java.charAt(i);
                if (c == '\\' && i + 1 < java.length()) {
                    if (java.charAt(i + 1) == 'Q') {
                        int end = java.indexOf("\\E", i + 2);
                        end = end < 0 ? java.length() : end + 2;
                        out.append(java, i, end);
                        i = end - 1;
                    } else {
                        out.append(c).append(java.charAt(i + 1));
                        i++;
                    }
                } else if (c == '[') {
                    classes++;
                    out.append(c);
                } else if (c == ']' && classes > 0) {
                    classes--;
                    out.append(c);
                } else if (classes == 0 && (c == '*' || c == '+') && i > 0 && java.charAt(i - 1) != '(') {
                    out.append(c == '*' ? "{0," : "{1,").append(BEHIND).append('}');
                } else if (classes == 0 && c == '{') {
                    int close = java.indexOf('}', i);
                    String inside = close < 0 ? "" : java.substring(i + 1, close);
                    if (inside.matches("\\d+,")) {
                        out.append('{').append(inside).append(BEHIND).append('}');
                        i = close;
                    } else out.append(c);
                } else out.append(c);
            }
            return out.toString();
        }

        /** read_limits: {@code \{n,m}}, {@code \{-n,m}} and the rest, after the brace. */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/regex/VimRegex.java`, replace:

```java
                }
                default -> {
                    int end = pos + 1;
                    while (end < src.length() && (Character.isDigit(src.charAt(end)) || "<>'.".indexOf(src.charAt(end)) >= 0)) end++;
```

with:

```java
                }
                default -> {
                    String column = column();
                    if (column != null) return column;
                    int end = pos + 1;
                    while (end < src.length() && (Character.isDigit(src.charAt(end)) || "<>'.".indexOf(src.charAt(end)) >= 0)) end++;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/regex/VimRegex.java`, replace:

```java
                }
            }
        }
```

with:

```java
                }
            }
        }

        /**
         * {@code \%23c}, {@code \%<23c} and {@code \%>23c}: the column, counted from 1, is 23, before it or after it.
         * Jasper counts characters where Vim counts bytes, which differs only in lines with characters beyond ASCII.
         *
         * @return the Java pattern, or null when this is not a column item
         */
        String column() {
            int p = pos;
            char cmp = at(p);
            if (cmp == '<' || cmp == '>') p++;
            int start = p;
            while (Character.isDigit(at(p))) p++;
            if (p == start || at(p) != 'c') return null;
            long n = Long.parseLong(src.substring(start, p));
            pos = p + 1;
            resync();
            if (cmp == '<') return n <= 1 ? "(?!)" : "(?<=^.{0," + (n - 2) + "})";
            if (cmp == '>') return n <= 0 ? "" : "(?<!^.{0," + (n - 1) + "})";
            return n <= 0 ? "(?!)" : "(?<=^.{" + (n - 1) + "})";
        }
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.vim.TypeaheadTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.ExtensionsTest' --tests 'dev.jasper.editor.vim.HighlightedYankTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 237 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 4151 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`: the Editor compiles without warnings (`-Xlint:all`).

- [ ] **Step 5: Check the recording (needs `vim`)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: `git status` lists only `regex-lookaround.case` (`??`), unchanged from what you created.

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/vim-v4c
git add plugins/editor/src/main/java/dev/jasper/editor/vim/regex/VimRegex.java plugins/editor/src/test/java/dev/jasper/editor/vim/regex/VimRegexTest.java plugins/editor/src/test/resources/vim/corpus/regex-lookaround.case
git commit -m "Add look-arounds and column items to Vim patterns

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: Vim script's string functions and searchpair()

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/VimStrings.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/VimStringsTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/SearchPairTest.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Search.java`

**Interfaces:**
- Consumes: Task 1's patterns.
- Produces: `VimStrings(ignoreCase, keywordClass)` with `regex`, `matches`, `match`, `matchend`, `matchstr`, `substitute(string, pattern, sub, flags)`, `split`, and static `escape`, `strpart`; `Search.Match(start, end, submatch)` (the first `\(\)` group that took part); `Search.findOnce(buffer, matcher, regex, pos, forward, atEnd, wrap, onWrap)` (one step of searchit, which now uses it); `Search.searchpair(editor, strings, start, middle, end, backward, wrap, skip) -> int` (do_searchpair: the line of the match from 1, or 0 with the cursor left where it was).

`VimStringsTest`'s expectations are what Vim 9.1 returned for the same calls.

- [ ] **Step 1: Write the failing tests**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/VimStringsTest.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Vim script's string functions as matchit's port uses them; every expectation is what Vim 9.1 returned. */
class VimStringsTest {
    private final VimStrings s = new VimStrings(false, VimRegex.Settings.DEFAULT.keyword());

    @Test void substituteReplacesAsVimDoes() {
        assertThat(s.substitute("a,b,,c", ",", ";", "g")).isEqualTo("a;b;;c");
        assertThat(s.substitute("a,b,,c", ",", ";", "")).isEqualTo("a;b,,c");
        assertThat(s.substitute("abc", "x*", "-", "g")).as("an empty match at every character").isEqualTo("-a-b-c-");
        assertThat(s.substitute("foo(bar)", "\\\\\\@1<!(", "\\\\%(", "g")).isEqualTo("foo\\%(bar)");
        assertThat(s.substitute("ab", "\\(a\\)\\(b\\)", "\\2\\1&\\\\", "")).isEqualTo("baab\\");
        assertThat(s.substitute("a:b\\:c", "\\\\\\@1<!\\%(\\\\\\\\\\)*\\zs:", "|", "g")).as("s:notslash").isEqualTo("a|b\\:c");
    }

    @Test void splitDropsEmptyEnds() {
        assertThat(s.split("a,b,,c,", ",")).containsExactly("a", "b", "", "c");
        assertThat(s.split(",a,b", ",")).containsExactly("a", "b");
        assertThat(s.split("abc", "\\zs")).containsExactly("a", "b", "c");
        assertThat(s.split("x:y,z", "\\zs:.\\{-},")).containsExactly("x", "z");
    }

    @Test void matchFindsWhereAsVimDoes() {
        assertThat(s.match("foobar", "o\\+")).isEqualTo(1);
        assertThat(s.matchend("foobar", "o\\+")).isEqualTo(3);
        assertThat(s.match("foobar", "x")).isEqualTo(-1);
        assertThat(s.match("foobar", "foo\\zsbar")).isEqualTo(3);
        assertThat(s.matchstr("foobar", "o\\+b")).isEqualTo("oob");
        assertThat(s.matches("foobar", "^f")).isTrue();
        assertThat(new VimStrings(true, VimRegex.Settings.DEFAULT.keyword()).matches("FOO", "foo")).as("'ignorecase'").isTrue();
        assertThat(VimStrings.escape("a*b,c", "*,")).isEqualTo("a\\*b\\,c");
        assertThat(VimStrings.strpart("abcdef", 2, 3)).isEqualTo("cde");
        assertThat(VimStrings.strpart("abc", -1, 2)).isEqualTo("a");
        assertThat(VimStrings.strpart("abc", 5)).isEmpty();
    }
}
```

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/SearchPairTest.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** searchpair() (Search.searchpair), which matchit's port moves with. */
class SearchPairTest {
    private static final VimStrings STRINGS = new VimStrings(false, VimRegex.Settings.DEFAULT.keyword());

    private static VimEditor editor(String text, Pos cursor) {
        var area = new VimTextArea();
        area.setText(text);
        var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
        editor.setCursor(cursor);
        return editor;
    }

    @Test void nestedPairsArePassedOver() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor("(a (b) c)\nif\n  if\n  else\n  endif\nelse\nendif\n", new Pos(0, 0));
            assertThat(Search.searchpair(editor, STRINGS, "(", "", ")", false, false, p -> false)).isEqualTo(1);
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 8));
            assertThat(Search.searchpair(editor, STRINGS, "(", "", ")", true, false, p -> false)).as("back to the start").isEqualTo(1);
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 0));
            editor.setCursor(new Pos(1, 0));
            assertThat(Search.searchpair(editor, STRINGS, "\\<if\\>", "\\<else\\>", "\\<endif\\>", false, false, p -> false))
                .as("the outer else, past the inner group").isEqualTo(6);
            assertThat(editor.cursor()).isEqualTo(new Pos(5, 0));
        });
    }

    @Test void skippedMatchesAndNoMatch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor("(a ) b)\n", new Pos(0, 0));
            assertThat(Search.searchpair(editor, STRINGS, "(", "", ")", false, false, p -> p.col == 3)).as("the first ) skipped").isEqualTo(1);
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 6));
            editor.setCursor(new Pos(0, 1));
            assertThat(Search.searchpair(editor, STRINGS, "\\[", "", "]", false, false, p -> false)).as("none: the cursor stays").isZero();
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 1));
        });
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimStringsTest' --tests 'dev.jasper.editor.vim.SearchPairTest'
```

Expected: FAIL: the tests do not compile (`VimStrings` and `Search.searchpair` do not exist).

- [ ] **Step 3: Write the string functions and searchpair()**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/VimStrings.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Vim script's string functions with patterns, for the ports of plugins that build patterns out of patterns
 * (matchit): {@code =~}, match(), matchend(), matchstr(), substitute() and split(). Patterns are magic, with
 * 'ignorecase' as given and 'smartcase' not used, as these functions use them.
 */
final class VimStrings {
    private final VimRegex.Settings settings;
    private final Map<String, VimRegex> compiled = new HashMap<>();

    /**
     * @param ignoreCase 'ignorecase' while the plugin runs
     * @param keyword 'iskeyword' as a Java character-class body
     */
    VimStrings(boolean ignoreCase, String keyword) { settings = new VimRegex.Settings(ignoreCase, false, keyword, null); }

    /** @return the pattern compiled; a bad one fails as Vim reports it */
    VimRegex regex(String pattern) {
        VimRegex re = compiled.get(pattern);
        if (re != null) return re;
        try { re = VimRegex.compile(pattern, settings); }
        catch (IllegalArgumentException bad) { throw new VimFailure(bad.getMessage()); }
        compiled.put(pattern, re);
        return re;
    }

    /** @return {@code string =~ pattern} */
    boolean matches(String string, String pattern) { return regex(pattern).pattern().matcher(string).find(); }

    /** @return match(): where the first match starts, after {@code \zs}; -1 for none */
    int match(String string, String pattern) {
        VimRegex re = regex(pattern);
        Matcher m = re.pattern().matcher(string);
        return m.find() ? re.start(m) : -1;
    }

    /** @return matchend(): where the first match ends; -1 for none */
    int matchend(String string, String pattern) {
        VimRegex re = regex(pattern);
        Matcher m = re.pattern().matcher(string);
        return m.find() ? re.end(m) : -1;
    }

    /** @return matchstr(): the first match, empty for none */
    String matchstr(String string, String pattern) {
        VimRegex re = regex(pattern);
        Matcher m = re.pattern().matcher(string);
        return m.find() ? string.substring(re.start(m), re.end(m)) : "";
    }

    /**
     * substitute(): the first match, or every match with {@code g}, replaced. In the replacement {@code &} and
     * {@code \0} are the match, {@code \1} to {@code \9} its groups, and a backslash before another character makes it
     * plain ({@code \\}, {@code \&}).
     */
    String substitute(String string, String pattern, String sub, String flags) {
        VimRegex re = regex(pattern);
        Matcher m = re.pattern().matcher(string);
        StringBuilder out = new StringBuilder();
        boolean all = flags.contains("g");
        int done = 0, search = 0;
        while (search <= string.length() && m.find(search)) {
            int start = re.start(m), end = re.end(m);
            out.append(string, done, start).append(replacement(re, m, string, sub));
            done = end;
            if (!all) break;
            if (end == start) {
                // After an empty match the next character is kept and the search goes on after it.
                if (end >= string.length()) break;
                int next = end + Character.charCount(string.codePointAt(end));
                out.append(string, end, next);
                done = next;
            }
            search = done;
        }
        return out.append(string.substring(Math.min(done, string.length()))).toString();
    }

    private static String replacement(VimRegex re, Matcher m, String string, String sub) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < sub.length(); i++) {
            char c = sub.charAt(i);
            if (c == '&') out.append(string, re.start(m), re.end(m));
            else if (c == '\\' && i + 1 < sub.length()) {
                char n = sub.charAt(++i);
                if (n >= '0' && n <= '9') {
                    int s = re.groupStart(m, n - '0'), e = re.groupEnd(m, n - '0');
                    if (s >= 0) out.append(string, s, e);
                } else out.append(n);
            } else out.append(c);
        }
        return out.toString();
    }

    /** @return split() (f_split): the parts between the matches, without empty ones at the ends */
    List<String> split(String string, String pattern) {
        VimRegex re = regex(pattern);
        Matcher m = re.pattern().matcher(string);
        List<String> out = new ArrayList<>();
        int str = 0, col = 0;
        while (true) {
            boolean found = str + col <= string.length() && m.find(str + col);
            int end = found ? re.start(m) : string.length();
            if (end > str || !out.isEmpty() && str < string.length() && found && end < re.end(m)) out.add(string.substring(str, end));
            if (!found) break;
            // To just after the match; after an empty one, one character on.
            int matchEnd = re.end(m);
            col = matchEnd > str ? 0 : matchEnd < string.length() ? Character.charCount(string.codePointAt(matchEnd)) : 1;
            str = matchEnd;
        }
        return out;
    }

    /** escape(): a backslash before each of {@code chars}. */
    static String escape(String string, String chars) {
        StringBuilder out = new StringBuilder();
        for (char c : string.toCharArray()) {
            if (chars.indexOf(c) >= 0) out.append('\\');
            out.append(c);
        }
        return out.toString();
    }

    /** strpart(): the part from {@code start}, {@code len} long; out of range positions are clamped. */
    static String strpart(String string, int start, int len) {
        int from = Math.max(0, start), to = Math.min(string.length(), Math.max(from, start + len));
        return from >= string.length() ? "" : string.substring(from, to);
    }

    /** strpart() to the end. */
    static String strpart(String string, int start) { return strpart(string, start, Integer.MAX_VALUE / 2); }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Search.java`, replace:

```java
    }

    /** A match: its start, and the position after its end. */
    record Match(Pos start, Pos end) { }

    /** Vim's RE_SEARCH and RE_SUBST. */
```

with:

```java
    }

    /**
     * A match: its start, the position after its end, and the number of its first {@code \(\)} group that took part
     * (searchit's submatch, for searchpair()), 0 for none.
     */
    record Match(Pos start, Pos end, int submatch) {
        Match(Pos start, Pos end) { this(start, end, 0); }
    }

    /** Vim's RE_SEARCH and RE_SUBST. */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Search.java`, replace:

```java
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
```

with:

```java
    Pos searchit(Context c, Pos start, boolean forward, VimRegex re, int count, boolean atEnd, String shown) {
        Buffer b = c.buffer;
        Matcher m = re.pattern().matcher(b.text(0, b.length()));
        Pos pos = start.copy();
        boolean wrapscan = c.options.flag("wrapscan");
        Match found;
        do {
            found = findOnce(b, m, re, pos, forward, atEnd, wrapscan, () -> {
                lastWrapped = true;
                c.editor.message(forward ? "search hit BOTTOM, continuing at TOP" : "search hit TOP, continuing at BOTTOM", true);
            });
            if (found != null) pos = found.start();
        } while (--count > 0 && found != null);
        if (found == null) {
            if (wrapscan) throw new VimFailure("E486: Pattern not found: " + shown);
            if (!forward) throw new VimFailure("E384: Search hit TOP without match for: " + shown);
            throw new VimFailure("E385: Search hit BOTTOM without match for: " + shown);
        }
        int lastLine = b.count() - 1;
        if (pos.line > lastLine) {
            String l = b.line(lastLine);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Search.java`, replace:

```java
        }
        return pos;
    }
```

with:

```java
        }
        return pos;
    }

    /**
     * One step of searchit: the next match from {@code pos}, forward or backward, wrapping once when {@code wrap}
     * ({@code onWrap} runs then).
     *
     * @return the match, whose start is its last character with {@code atEnd}; null when there is none
     */
    static Match findOnce(Buffer b, Matcher m, VimRegex re, Pos pos, boolean forward, boolean atEnd, boolean wrap, Runnable onWrap) {
        int lastLine = b.count() - 1;
        int startCharLen;
        if (pos.col == Integer.MAX_VALUE) startCharLen = 0;
        else if (pos.line >= 0 && pos.line <= lastLine) {
            String l = b.line(pos.line);
            startCharLen = pos.col >= l.length() ? 1 : Character.charCount(l.codePointAt(pos.col));
        } else startCharLen = 1;
        int extraCol = forward ? startCharLen : 0;
        Pos startPos = pos.copy();
        boolean atFirstLine = true;
        int lnum;
        if (pos.line < 0) atFirstLine = false;
        if (!forward && startPos.col == 0) {
            lnum = Math.max(pos.line, 0) - 1;
            atFirstLine = false;
        } else lnum = Math.max(pos.line, 0);
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
                        return new Match(e, match.end(), match.submatch());
                    }
                    return match;
                }
                if (loop > 0 && lnum == startPos.line) break;
            }
            if (!wrap || loop > 0) break;
            lnum = forward ? 0 : lastLine;
            onWrap.run();
        }
        return null;
    }

    /**
     * searchpair() (do_searchpair): from the cursor, the {@code end} that pairs with the {@code start} before it, or
     * going back, the start that pairs with the end after it; nested pairs are passed over, and a {@code middle}
     * of the outer pair stops the search too. A match {@code skip} accepts is passed over. The cursor goes to the
     * match; when there is none it stays.
     *
     * @param strings the patterns' settings, as the plugin's string functions have them
     * @param wrap whether the search wraps around the text (no {@code W} flag)
     * @return the match's line number, from 1; 0 when there is none
     */
    static int searchpair(VimEditor editor, VimStrings strings, String start, String middle, String end, boolean backward, boolean wrap,
                          java.util.function.Predicate<Pos> skip) {
        Buffer b = editor.buffer();
        String text = b.text(0, b.length());
        VimRegex pair = strings.regex("\\m\\(" + start + "\\m\\)\\|\\(" + end + "\\m\\)");
        VimRegex outer = middle.isEmpty() ? pair
            : strings.regex("\\m\\(" + start + "\\m\\)\\|\\(" + end + "\\m\\)\\|\\(" + middle + "\\m\\)");
        Matcher pairMatcher = pair.pattern().matcher(text), outerMatcher = outer.pattern().matcher(text);
        Pos pos = editor.cursor(), firstpos = null, foundpos = null;
        boolean nested = false;
        int nest = 1, retval = 0;
        while (true) {
            Match found = nested ? findOnce(b, pairMatcher, pair, pos, !backward, false, wrap, () -> { })
                : findOnce(b, outerMatcher, outer, pos, !backward, false, wrap, () -> { });
            if (found == null) break;
            pos = found.start().copy();
            if (firstpos != null && pos.equals(firstpos)) break;
            if (firstpos == null) firstpos = pos.copy();
            if (pos.equals(foundpos)) {
                // The same position again (a pattern ending in \zs, going back): one character on.
                if (backward) decl(b, pos);
                else incl(b, pos);
            }
            foundpos = pos.copy();
            if (skip.test(pos.copy())) continue;
            // searchit's result is the submatch + 1: 2 for a start, 3 for an end, 4 for a middle.
            int n = found.submatch() + 1;
            if (backward && n == 3 || !backward && n == 2) {
                nest++;
                nested = true;
            } else if (--nest == 1) nested = false;
            if (nest == 0) {
                retval = pos.line + 1;
                editor.setCursor(pos);
                break;
            }
        }
        return retval;
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Search.java`, replace:

```java
    private static Match match(Buffer b, VimRegex re, Matcher m) {
        int s = Math.min(re.start(m), b.length()), e = Math.min(re.end(m), b.length());
        return new Match(position(b, s), position(b, Math.max(s, e)));
    }
```

with:

```java
    private static Match match(Buffer b, VimRegex re, Matcher m) {
        int s = Math.min(re.start(m), b.length()), e = Math.min(re.end(m), b.length());
        int submatch = 0;
        for (int n = 1; n <= 9 && submatch == 0; n++) if (re.groupStart(m, n) >= 0) submatch = n;
        return new Match(position(b, s), position(b, Math.max(s, e)), submatch);
    }
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimStringsTest' --tests 'dev.jasper.editor.vim.SearchPairTest'
```

Expected: 5 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.vim.TypeaheadTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.ExtensionsTest' --tests 'dev.jasper.editor.vim.HighlightedYankTest' --tests 'dev.jasper.editor.vim.VimStringsTest' --tests 'dev.jasper.editor.vim.SearchPairTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 242 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 4151 tests pass: searchit, now on `findOnce`, finds what it did.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/vim-v4c
git add plugins/editor/src/main/java/dev/jasper/editor/vim/VimStrings.java plugins/editor/src/main/java/dev/jasper/editor/vim/Search.java plugins/editor/src/test/java/dev/jasper/editor/vim/VimStringsTest.java plugins/editor/src/test/java/dev/jasper/editor/vim/SearchPairTest.java
git commit -m "Add Vim script string functions and searchpair()

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: matchit

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/Matchit.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/MatchitLanguages.java`, `plugins/editor/src/main/resources/dev/jasper/editor/vim/matchit-languages.tsv`, `plugins/editor/src/test/resources/vim/corpus/ext-matchit.case`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Target.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusRunner.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java`, `plugins/editor/vim-oracle.properties`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Consumes: Task 1's patterns, Task 2's `VimStrings` and `Search.searchpair`; V4a's `Extensions`, `Script.normal`.
- Produces: the option `matchit`; `<Plug>(MatchitNormalForward)`, `…Backward`, `…MultiBackward`, `…MultiForward` and the same for `Visual` and `Operation` (motions), `<Plug>(MatchitVisualTextObject)`; `:packadd` (`matchit` switches it on; any other name fails with E919); `MatchitLanguages.of(editor)`, `of(style)`, `byFiletype(name)` and `Language(style, filetype, ignoreCase, skip, matchpairs, words)` with `addedPairs()`; `Target.area` and `asArea()` (the operator's text is a Visual area: a multi-line delete stays charwise); the corpus field `filetype:` (`CorpusCase.filetype()`), and a pin `name = packadd` for a plugin that comes with Vim.

After an operator, matchit's `:call` makes the text a Visual area (`norm! v`) unless `v` or `V` forced the type: the motion is inclusive and a Visual area, or with `v` exclusive (which `v` then turns inclusive), and a forced CTRL-V makes the usual block. `matchit-languages.tsv` is what Vim 9.1 made with `packadd matchit`, `filetype plugin on` and `syntax on` for each RSyntaxTextArea style it has a filetype plugin for.

- [ ] **Step 1: Write the failing tests and cases**

Create `plugins/editor/src/test/resources/vim/corpus/ext-matchit.case`:

```text
# matchit (plan V4c): %, g%, [%, ]% and a% over the words of a group.

=== % on a parenthesis
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,6
ext: matchit
keys: %
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,13
expect-mode: normal

=== % on the closing parenthesis
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,13
ext: matchit
keys: %
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,6
expect-mode: normal

=== % on an inner parenthesis
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,10
ext: matchit
keys: %
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,12
expect-mode: normal

=== % before a parenthesis on the line
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,1
ext: matchit
keys: %
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,13
expect-mode: normal

=== % on a brace
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,15
ext: matchit
keys: %
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,15
expect-mode: normal

=== % on a comment start
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 3,3
ext: matchit
keys: %
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 3,8
expect-mode: normal

=== % on a comment end
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 3,8
ext: matchit
keys: %
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 3,3
expect-mode: normal

=== % on if goes to else
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 1,2
ext: matchit
keys: %
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 4,1
expect-mode: normal

=== % on else goes to endif
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 4,3
ext: matchit
keys: %
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 6,1
expect-mode: normal

=== % on endif goes back to if
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 6,1
ext: matchit
keys: %
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 1,1
expect-mode: normal

=== g% on if goes to endif
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 1,1
ext: matchit
keys: g%
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 6,1
expect-mode: normal

=== g% on endif goes to else
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 6,3
ext: matchit
keys: g%
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 4,1
expect-mode: normal

=== % where the line has no word
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 5,1
ext: matchit
keys: %
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 5,5
expect-mode: normal

=== % with a count goes to a line
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 1,1
ext: matchit
keys: 50%
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 3,3
expect-mode: normal

=== backtick backtick after %
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,6
ext: matchit
keys: %``
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,6
expect-mode: normal

=== d% on a parenthesis
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,6
ext: matchit
keys: d%
registers: " 0 1 -
--- vim 9.1
expect-text:
  #if A
  int f {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,6
expect-mode: normal
expect-register ": c "(a, (b))"
expect-register 0: none
expect-register 1: none
expect-register -: c "(a, (b))"

=== d% on if takes the whole else
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 1,1
ext: matchit
keys: d%
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
    g();
  #endif
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "#if A\nint f(a, (b)) {\n  /* x */\n#else"
expect-register 0: none
expect-register 1: c "#if A\nint f(a, (b)) {\n  /* x */\n#else"
expect-register -: none

=== d% on endif
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 6,3
ext: matchit
keys: d%
registers: " 0 1 -
--- vim 9.1
expect-text:
  dif
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "#if A\nint f(a, (b)) {\n  /* x */\n#else\n  g();\n#en"
expect-register 0: none
expect-register 1: c "#if A\nint f(a, (b)) {\n  /* x */\n#else\n  g();\n#en"
expect-register -: none

=== dg% on endif
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 6,1
ext: matchit
keys: dg%
registers: " 0 1 -
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  endif
expect-cursor: 4,1
expect-mode: normal
expect-register ": c "#else\n  g();\n#"
expect-register 0: none
expect-register 1: c "#else\n  g();\n#"
expect-register -: none

=== d% where nothing matches takes a character
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 5,3
ext: matchit
keys: d%
registers: " 0 1 -
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    ;
  #endif
expect-cursor: 5,3
expect-mode: normal
expect-register ": c "g()"
expect-register 0: none
expect-register 1: none
expect-register -: c "g()"

=== dv% on a parenthesis
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,6
ext: matchit
keys: dv%
registers: " 0 1 -
--- vim 9.1
expect-text:
  #if A
  int f {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,6
expect-mode: normal
expect-register ": c "(a, (b))"
expect-register 0: none
expect-register 1: none
expect-register -: c "(a, (b))"

=== dV% on if
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 1,1
ext: matchit
keys: dV%
registers: " 0 1 -
--- vim 9.1
expect-text:
    g();
  #endif
expect-cursor: 1,3
expect-mode: normal
expect-register ": l "#if A\nint f(a, (b)) {\n  /* x */\n#else\n"
expect-register 0: none
expect-register 1: l "#if A\nint f(a, (b)) {\n  /* x */\n#else\n"
expect-register -: none

=== d CTRL-V % on a parenthesis
text:
  (ab
  cd)
cursor: 1,1
ext: matchit
keys: d<C-v>%
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": b3 "(ab\ncd)"
expect-register 0: none
expect-register 1: b3 "(ab\ncd)"
expect-register -: none

=== y% keeps the cursor
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,6
ext: matchit
keys: y%
registers: " 0 1 -
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,6
expect-mode: normal
expect-register ": c "(a, (b))"
expect-register 0: c "(a, (b))"
expect-register 1: none
expect-register -: none

=== c% on a parenthesis
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,6
ext: matchit
keys: c%X<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  #if A
  int fX {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,6
expect-mode: normal
expect-register ": c "(a, (b))"
expect-register 0: none
expect-register 1: none
expect-register -: c "(a, (b))"

=== d% then dot
text:
  (a) (b)
cursor: 1,1
ext: matchit
keys: d%x.
registers: " 0 1 -
--- vim 9.1
expect-text:
  b)
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "("
expect-register 0: none
expect-register 1: none
expect-register -: c "("

=== v% extends the area
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,6
ext: matchit
keys: v%
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,13
expect-mode: visual
expect-visual: 2,6

=== V% on if
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 1,1
ext: matchit
keys: V%
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 4,1
expect-mode: visual-line
expect-visual: 1,1

=== vg% on endif
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 6,1
ext: matchit
keys: vg%
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 4,1
expect-mode: visual
expect-visual: 6,1

=== [% goes to the enclosing start
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,11
ext: matchit
keys: [%
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,10
expect-mode: normal

=== ]% goes to the enclosing end
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,8
ext: matchit
keys: ]%
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,13
expect-mode: normal

=== [% in an if block
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 5,3
ext: matchit
keys: [%
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 4,1
expect-mode: normal

=== ]% in an if block
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,1
ext: matchit
keys: ]%
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,1
expect-mode: normal

=== 2[% goes out two levels
text:
  ((a) (b))
cursor: 1,4
ext: matchit
keys: 2[%
--- vim 9.1
expect-text:
  ((a) (b))
expect-cursor: 1,1
expect-mode: normal

=== d]% deletes to the end
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,8
ext: matchit
keys: d]%
registers: " 0 1 -
--- vim 9.1
expect-text:
  #if A
  int f(a {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,8
expect-mode: normal
expect-register ": c ", (b))"
expect-register 0: none
expect-register 1: none
expect-register -: c ", (b))"

=== d[% deletes back to the start
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 2,11
ext: matchit
keys: d[%
registers: " 0 1 -
--- vim 9.1
expect-text:
  #if A
  int f(a, )) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 2,10
expect-mode: normal
expect-register ": c "(b"
expect-register 0: none
expect-register 1: none
expect-register -: c "(b"

=== Visual a% takes the group
text:
  x (a (b) c) y
cursor: 1,7
ext: matchit
keys: va%
--- vim 9.1
expect-text:
  x (a (b) c) y
expect-cursor: 1,8
expect-mode: visual
expect-visual: 1,6

=== Visual a% then d
text:
  x (a (b) c) y
cursor: 1,7
ext: matchit
keys: va%d
registers: " 0 1 -
--- vim 9.1
expect-text:
  x (a  c) y
expect-cursor: 1,6
expect-mode: normal
expect-register ": c "(b)"
expect-register 0: none
expect-register 1: none
expect-register -: c "(b)"

=== % on the one empty line
text:
  $
cursor: 1,1
ext: matchit
keys: %
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal

=== d% on the one empty line
text:
  $
cursor: 1,1
ext: matchit
keys: d%
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

=== % on the last line
text:
  x
  (a)
cursor: 2,1
ext: matchit
keys: %
--- vim 9.1
expect-text:
  x
  (a)
expect-cursor: 2,3
expect-mode: normal

=== d% on the last line
text:
  x
  (a)
cursor: 2,3
ext: matchit
keys: d%
registers: " 0 1 -
--- vim 9.1
expect-text:
  x
  $
expect-cursor: 2,1
expect-mode: normal
expect-register ": c "(a)"
expect-register 0: none
expect-register 1: none
expect-register -: c "(a)"

=== % with a bracket in matchpairs set
text:
  <a>
cursor: 1,1
ext: matchit
keys: :set mps+=<:><CR>%
--- vim 9.1
expect-text:
  <a>
expect-cursor: 1,3
expect-mode: normal

=== % across lines
text:
  f(
    a,
  )
cursor: 1,2
ext: matchit
keys: %
--- vim 9.1
expect-text:
  f(
    a,
  )
expect-cursor: 3,1
expect-mode: normal

=== the default mappings
text:
  a
cursor: 1,1
ext: matchit
keys: :nmap %<CR>:omap ]%<CR>:xmap a%<CR>
output: yes
--- vim 9.1
expect-text:
  a
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  %             <Plug>(MatchitNormalForward)
  o  ]%            <Plug>(MatchitOperationMultiForward)
  x  a%            <Plug>(MatchitVisualTextObject)

=== no plugin maps
text:
  (a)
cursor: 1,1
ext: matchit
let: g:no_plugin_maps = 1
keys: %
--- vim 9.1
expect-text:
  (a)
expect-cursor: 1,3
expect-mode: normal

=== packadd matchit switches it on
text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
cursor: 1,1
keys: :packadd matchit<CR>%
--- vim 9.1
expect-text:
  #if A
  int f(a, (b)) {
    /* x */
  #else
    g();
  #endif
expect-cursor: 4,1
expect-mode: normal

=== packadd of another package
text:
  a
cursor: 1,1
keys: :packadd nothing<CR>
output: yes
--- vim 9.1
expect-text:
  a
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E919: Directory not found in 'packpath': "pack/*/opt/nothing"

=== html % on a tag goes to its end tag
text:
  <div class="a">
    <p>x <b>y</b></p>
    <br/>
  </div>
cursor: 1,2
ext: matchit
filetype: html
keys: %
--- vim 9.1
expect-text:
  <div class="a">
    <p>x <b>y</b></p>
    <br/>
  </div>
expect-cursor: 4,2
expect-mode: normal

=== html % on the end tag goes back
text:
  <div class="a">
    <p>x <b>y</b></p>
    <br/>
  </div>
cursor: 4,3
ext: matchit
filetype: html
keys: %
--- vim 9.1
expect-text:
  <div class="a">
    <p>x <b>y</b></p>
    <br/>
  </div>
expect-cursor: 1,2
expect-mode: normal

=== html % on an inner tag
text:
  <div class="a">
    <p>x <b>y</b></p>
    <br/>
  </div>
cursor: 2,4
ext: matchit
filetype: html
keys: %
--- vim 9.1
expect-text:
  <div class="a">
    <p>x <b>y</b></p>
    <br/>
  </div>
expect-cursor: 2,17
expect-mode: normal

=== html % on a nested tag
text:
  <div class="a">
    <p>x <b>y</b></p>
    <br/>
  </div>
cursor: 2,9
ext: matchit
filetype: html
keys: %
--- vim 9.1
expect-text:
  <div class="a">
    <p>x <b>y</b></p>
    <br/>
  </div>
expect-cursor: 2,13
expect-mode: normal

=== html % on angle brackets
text:
  <div class="a">
    <p>x <b>y</b></p>
    <br/>
  </div>
cursor: 1,1
ext: matchit
filetype: html
keys: %
--- vim 9.1
expect-text:
  <div class="a">
    <p>x <b>y</b></p>
    <br/>
  </div>
expect-cursor: 1,15
expect-mode: normal

=== html d% on a tag
text:
  <div class="a">
    <p>x <b>y</b></p>
    <br/>
  </div>
cursor: 2,3
ext: matchit
filetype: html
keys: d%
registers: " 0 1 -
--- vim 9.1
expect-text:
  <div class="a">
    x <b>y</b></p>
    <br/>
  </div>
expect-cursor: 2,3
expect-mode: normal
expect-register ": c "<p>"
expect-register 0: none
expect-register 1: none
expect-register -: c "<p>"

=== html [% in a tag
text:
  <div class="a">
    <p>x <b>y</b></p>
    <br/>
  </div>
cursor: 2,8
ext: matchit
filetype: html
keys: [%
--- vim 9.1
expect-text:
  <div class="a">
    <p>x <b>y</b></p>
    <br/>
  </div>
expect-cursor: 2,3
expect-mode: normal

=== html % ignores case
text:
  <DIV>
  </div>
cursor: 1,2
ext: matchit
filetype: html
keys: %
--- vim 9.1
expect-text:
  <DIV>
  </div>
expect-cursor: 2,2
expect-mode: normal

=== sh % on if goes to elif
text:
  if [ a ]; then
    echo "if"
  elif b; then
    :
  else
    for x in y; do
      :
    done
  fi
cursor: 1,1
ext: matchit
filetype: sh
keys: %
--- vim 9.1
expect-text:
  if [ a ]; then
    echo "if"
  elif b; then
    :
  else
    for x in y; do
      :
    done
  fi
expect-cursor: 3,1
expect-mode: normal

=== sh % on elif goes to else
text:
  if [ a ]; then
    echo "if"
  elif b; then
    :
  else
    for x in y; do
      :
    done
  fi
cursor: 3,1
ext: matchit
filetype: sh
keys: %
--- vim 9.1
expect-text:
  if [ a ]; then
    echo "if"
  elif b; then
    :
  else
    for x in y; do
      :
    done
  fi
expect-cursor: 5,1
expect-mode: normal

=== sh % on else goes to fi
text:
  if [ a ]; then
    echo "if"
  elif b; then
    :
  else
    for x in y; do
      :
    done
  fi
cursor: 5,1
ext: matchit
filetype: sh
keys: %
--- vim 9.1
expect-text:
  if [ a ]; then
    echo "if"
  elif b; then
    :
  else
    for x in y; do
      :
    done
  fi
expect-cursor: 9,1
expect-mode: normal

=== sh % on fi goes to if
text:
  if [ a ]; then
    echo "if"
  elif b; then
    :
  else
    for x in y; do
      :
    done
  fi
cursor: 9,1
ext: matchit
filetype: sh
keys: %
--- vim 9.1
expect-text:
  if [ a ]; then
    echo "if"
  elif b; then
    :
  else
    for x in y; do
      :
    done
  fi
expect-cursor: 1,1
expect-mode: normal

=== sh % on for goes to done
text:
  if [ a ]; then
    echo "if"
  elif b; then
    :
  else
    for x in y; do
      :
    done
  fi
cursor: 6,3
ext: matchit
filetype: sh
keys: %
--- vim 9.1
expect-text:
  if [ a ]; then
    echo "if"
  elif b; then
    :
  else
    for x in y; do
      :
    done
  fi
expect-cursor: 8,3
expect-mode: normal

=== sh ]% from inside
text:
  if [ a ]; then
    echo "if"
  elif b; then
    :
  else
    for x in y; do
      :
    done
  fi
cursor: 4,3
ext: matchit
filetype: sh
keys: ]%
--- vim 9.1
expect-text:
  if [ a ]; then
    echo "if"
  elif b; then
    :
  else
    for x in y; do
      :
    done
  fi
expect-cursor: 5,1
expect-mode: normal

=== lua % on function
text:
  function f()
    if a then
      return 1
    else
    end
  end
cursor: 1,1
ext: matchit
filetype: lua
keys: %
--- vim 9.1
expect-text:
  function f()
    if a then
      return 1
    else
    end
  end
expect-cursor: 6,1
expect-mode: normal

=== lua % on if goes to else
text:
  function f()
    if a then
      return 1
    else
    end
  end
cursor: 2,3
ext: matchit
filetype: lua
keys: %
--- vim 9.1
expect-text:
  function f()
    if a then
      return 1
    else
    end
  end
expect-cursor: 3,5
expect-mode: normal

=== lua g% on end
text:
  function f()
    if a then
      return 1
    else
    end
  end
cursor: 6,1
ext: matchit
filetype: lua
keys: g%
--- vim 9.1
expect-text:
  function f()
    if a then
      return 1
    else
    end
  end
expect-cursor: 1,1
expect-mode: normal

=== ruby % on def goes to end
text:
  def f
    if x
      y
    end
  end
cursor: 1,1
ext: matchit
filetype: ruby
keys: %
--- vim 9.1
expect-text:
  def f
    if x
      y
    end
  end
expect-cursor: 5,1
expect-mode: normal

=== ruby % on if goes to end
text:
  def f
    if x
      y
    end
  end
cursor: 2,3
ext: matchit
filetype: ruby
keys: %
--- vim 9.1
expect-text:
  def f
    if x
      y
    end
  end
expect-cursor: 4,3
expect-mode: normal

=== make % on ifeq
text:
  ifeq ($(A),1)
  X = 1
  else
  X = 2
  endif
cursor: 1,1
ext: matchit
filetype: make
keys: %
--- vim 9.1
expect-text:
  ifeq ($(A),1)
  X = 1
  else
  X = 2
  endif
expect-cursor: 3,1
expect-mode: normal

=== make % on else
text:
  ifeq ($(A),1)
  X = 1
  else
  X = 2
  endif
cursor: 3,1
ext: matchit
filetype: make
keys: %
--- vim 9.1
expect-text:
  ifeq ($(A),1)
  X = 1
  else
  X = 2
  endif
expect-cursor: 5,1
expect-mode: normal

=== tex % on begin goes to end
text:
  \begin{itemize}
    \item a
  \end{itemize}
cursor: 1,2
ext: matchit
filetype: tex
keys: %
--- vim 9.1
expect-text:
  \begin{itemize}
    \item a
  \end{itemize}
expect-cursor: 3,1
expect-mode: normal

=== tex % on end goes to begin
text:
  \begin{itemize}
    \item a
  \end{itemize}
cursor: 3,2
ext: matchit
filetype: tex
keys: %
--- vim 9.1
expect-text:
  \begin{itemize}
    \item a
  \end{itemize}
expect-cursor: 1,1
expect-mode: normal

=== c % passes a parenthesis in a string
text:
  f("(", a);
  g(/* ) */ b);
  #if X
  #endif
cursor: 1,2
ext: matchit
filetype: c
keys: %
--- vim 9.1
expect-text:
  f("(", a);
  g(/* ) */ b);
  #if X
  #endif
expect-cursor: 1,9
expect-mode: normal

=== c % passes a parenthesis in a comment
text:
  f("(", a);
  g(/* ) */ b);
  #if X
  #endif
cursor: 2,2
ext: matchit
filetype: c
keys: %
--- vim 9.1
expect-text:
  f("(", a);
  g(/* ) */ b);
  #if X
  #endif
expect-cursor: 2,12
expect-mode: normal

=== c % on a parenthesis in a string
text:
  f("(", a);
  g(/* ) */ b);
  #if X
  #endif
cursor: 1,4
ext: matchit
filetype: c
keys: %
--- vim 9.1
expect-text:
  f("(", a);
  g(/* ) */ b);
  #if X
  #endif
expect-cursor: 1,9
expect-mode: normal

=== c % on #if
text:
  f("(", a);
  g(/* ) */ b);
  #if X
  #endif
cursor: 3,1
ext: matchit
filetype: c
keys: %
--- vim 9.1
expect-text:
  f("(", a);
  g(/* ) */ b);
  #if X
  #endif
expect-cursor: 4,1
expect-mode: normal

=== xml % on a tag
text:
  <a>
    <b/>
    <c x="1">t</c>
  </a>
cursor: 1,2
ext: matchit
filetype: xml
keys: %
--- vim 9.1
expect-text:
  <a>
    <b/>
    <c x="1">t</c>
  </a>
expect-cursor: 4,2
expect-mode: normal

=== xml % on a tag with attributes
text:
  <a>
    <b/>
    <c x="1">t</c>
  </a>
cursor: 3,4
ext: matchit
filetype: xml
keys: %
--- vim 9.1
expect-text:
  <a>
    <b/>
    <c x="1">t</c>
  </a>
expect-cursor: 3,14
expect-mode: normal

=== xml % on a self-closing tag
text:
  <a>
    <b/>
    <c x="1">t</c>
  </a>
cursor: 2,4
ext: matchit
filetype: xml
keys: %
--- vim 9.1
expect-text:
  <a>
    <b/>
    <c x="1">t</c>
  </a>
expect-cursor: 2,4
expect-mode: normal
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

    @Test void matchitSwitchesOnWithPackaddAsWell() throws Exception {
        onEdt(() -> {
            var editor = editor("(a)\n");
            type(editor, ":packadd matchit<CR>");
            assertThat(editor.options().flag("matchit")).isTrue();
            assertThat(mapped(editor, Mappings.NORMAL, "%")).isTrue();
            assertThat(mapped(editor, Mappings.VISUAL, "a%")).isTrue();
            type(editor, ":packadd nothing<CR>");
            assertThat(editor.message()).isEqualTo("E919: Directory not found in 'packpath': \"pack/*/opt/nothing\"");
            var plugged = editor("x\n");
            type(plugged, ":Plug 'chrisbra/matchit'<CR>");
            assertThat(plugged.options().flag("matchit")).isTrue();
        });
    }

    @Test void matchpairsFollowTheLanguageAsItsFiletypePluginAddsThem() throws Exception {
        onEdt(() -> {
            var editor = editor("<a>\n");
            var area = (org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) editor.area();
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_HTML);
            assertThat(editor.options().string("matchpairs")).as("html adds <:>").isEqualTo("(:),{:},[:],<:>");
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
            assertThat(editor.options().string("matchpairs")).isEqualTo("(:),{:},[:]");
            type(editor, ":set mps+=\u00ab:\u00bb<CR>");
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PHP);
            assertThat(editor.options().string("matchpairs")).as("added to the global value").isEqualTo("(:),{:},[:],\u00ab:\u00bb,<:>");
            assertThat(MatchitLanguages.byFiletype("sh")).map(MatchitLanguages.Language::style).contains(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
            assertThat(MatchitLanguages.of(SyntaxConstants.SYNTAX_STYLE_HTML).words()).contains("<\\@<=/\\1>");
        });
    }
}
```

- [ ] **Step 2: Teach the corpus `filetype:` and `packadd` pins**

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java`, replace:

```java
 * @param let {@code :let} commands run before the extensions' plugins load ({@code g:surround_no_mappings = 1}),
 *     one for each {@code let:} line
 * @param given registers set first
 * @param text the starting lines
```

with:

```java
 * @param let {@code :let} commands run before the extensions' plugins load ({@code g:surround_no_mappings = 1}),
 *     one for each {@code let:} line
 * @param filetype Vim's name for the text's language ({@code html}): the tab gets the RSyntaxTextArea style
 *     {@link MatchitLanguages} gives it, and the recorder has Vim load its filetype plugin and syntax; null for none
 * @param given registers set first
 * @param text the starting lines
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java`, replace:

```java
 * @param expected what Vim did, or null when the case has not been recorded
 */
record CorpusCase(String file, String name, String divergent, String set, String ext, List<String> let, Map<Character, Register> given, List<String> text,
                  int line, int col, String keys, List<Character> registers, String window, String folds, boolean output, Expected expected) {
    /**
```

with:

```java
 * @param expected what Vim did, or null when the case has not been recorded
 */
record CorpusCase(String file, String name, String divergent, String set, String ext, List<String> let, String filetype, Map<Character, Register> given, List<String> text,
                  int line, int col, String keys, List<Character> registers, String window, String folds, boolean output, Expected expected) {
    /**
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java`, replace:

```java

    CorpusCase withExpected(Expected value) {
        return new CorpusCase(file, name, divergent, set, ext, let, given, text, line, col, keys, registers, window, folds, output, value);
    }
```

with:

```java

    CorpusCase withExpected(Expected value) {
        return new CorpusCase(file, name, divergent, set, ext, let, filetype, given, text, line, col, keys, registers, window, folds, output, value);
    }
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java`, replace:

```java
            if (!line.startsWith("=== ")) throw error(file, i, "expected \"=== name\"");
            String name = line.substring(4).trim();
            String divergent = null, set = "", ext = "", keys = null, window = null, folds = null;
            boolean output = false;
            List<String> let = new ArrayList<>();
```

with:

```java
            if (!line.startsWith("=== ")) throw error(file, i, "expected \"=== name\"");
            String name = line.substring(4).trim();
            String divergent = null, set = "", ext = "", keys = null, window = null, folds = null, filetype = null;
            boolean output = false;
            List<String> let = new ArrayList<>();
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java`, replace:

```java
                    case "ext" -> ext = value;
                    case "let" -> let.add(value);
                    case "window" -> window = value;
                    case "folds" -> folds = value;
```

with:

```java
                    case "ext" -> ext = value;
                    case "let" -> let.add(value);
                    case "filetype" -> filetype = value;
                    case "window" -> window = value;
                    case "folds" -> folds = value;
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java`, replace:

```java
            }
            if (text == null || keys == null) throw error(file, i, "case \"" + name + "\" needs text: and keys:");
            cases.add(new CorpusCase(file, name, divergent, set, ext, List.copyOf(let), given, text, cursorLine, cursorCol, keys, registers, window, folds, output, expected));
        }
        return cases;
```

with:

```java
            }
            if (text == null || keys == null) throw error(file, i, "case \"" + name + "\" needs text: and keys:");
            cases.add(new CorpusCase(file, name, divergent, set, ext, List.copyOf(let), filetype, given, text, cursorLine, cursorCol, keys, registers, window, folds, output, expected));
        }
        return cases;
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusRunner.java`, replace:

```java
        area.discardAllEdits();
        if (c.folds() != null) javaFolds(area, c.closedAtStart());
        var editor = new VimEditor(vim, area, EditorDefaults.VIM);
        // Vim's window when the recorder starts: 80 columns, 23 rows (24 lines, one for the command line).
```

with:

```java
        area.discardAllEdits();
        if (c.folds() != null) javaFolds(area, c.closedAtStart());
        if (c.filetype() != null) area.setSyntaxEditingStyle(MatchitLanguages.byFiletype(c.filetype())
            .orElseThrow(() -> new IllegalStateException(c + ": no language for filetype " + c.filetype())).style());
        var editor = new VimEditor(vim, area, EditorDefaults.VIM);
        // Vim's window when the recorder starts: 80 columns, 23 rows (24 lines, one for the command line).
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java`, replace:

```java
            for (String extension : c.extensions()) {
                String name = extension.toLowerCase(java.util.Locale.ROOT);
                for (String needed : needs(name)) if (!plugins.contains(oracle(needed))) plugins.add(oracle(needed));
                plugins.add(oracle(name));
```

with:

```java
            for (String extension : c.extensions()) {
                String name = extension.toLowerCase(java.util.Locale.ROOT);
                // A plugin that comes with Vim (matchit) is pinned as packadd: Vim's own copy is loaded.
                if (pin(name)[0].equals("packadd")) {
                    lines.add("packadd " + name);
                    continue;
                }
                for (String needed : needs(name)) if (!plugins.contains(oracle(needed))) plugins.add(oracle(needed));
                plugins.add(oracle(name));
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java`, replace:

```java
            lines.add("call setreg(" + Json.quote(String.valueOf(entry.getKey())) + ", " + vimList(List.of(text.split("\n", -1))) + ", " + Json.quote(register.type()) + ")");
        }
        // Editing a file, rather than setline(), leaves no undo history for u to reach.
        lines.add("call writefile(" + vimList(c.text()) + ", " + Json.quote(file.toString()) + ")");
        lines.add("silent execute 'edit!' fnameescape(" + Json.quote(file.toString()) + ")");
        if (c.folds() != null) lines.addAll(foldCommands(c));
        lines.add("call setcursorcharpos(" + c.line() + ", " + c.col() + ")");
```

with:

```java
            lines.add("call setreg(" + Json.quote(String.valueOf(entry.getKey())) + ", " + vimList(List.of(text.split("\n", -1))) + ", " + Json.quote(register.type()) + ")");
        }
        // A filetype: the filetype plugin and syntax load as they do with filetype plugin on and syntax on.
        if (c.filetype() != null) lines.add("filetype plugin on | syntax on");
        // Editing a file, rather than setline(), leaves no undo history for u to reach.
        lines.add("call writefile(" + vimList(c.text()) + ", " + Json.quote(file.toString()) + ")");
        lines.add("silent execute 'edit!' fnameescape(" + Json.quote(file.toString()) + ")");
        if (c.filetype() != null) lines.add("set filetype=" + c.filetype());
        if (c.folds() != null) lines.addAll(foldCommands(c));
        lines.add("call setcursorcharpos(" + c.line() + ", " + c.col() + ")");
```

In `plugins/editor/vim-oracle.properties`, replace:

```properties
# The Vim plugins the corpus's ext: cases are recorded against (spec 11.1), each pinned to a commit:
# name = git URL, then the commit, then any pins it needs loaded first. recordVimCorpus clones them into
# build/vim-oracle; check never does.
repeat = https://github.com/tpope/vim-repeat.git 65846025c15494983dafe5e3b46c8f88ab2e9635
```

with:

```properties
# The Vim plugins the corpus's ext: cases are recorded against (spec 11.1), each pinned to a commit:
# name = git URL, then the commit, then any pins it needs loaded first; or name = packadd for a plugin that comes
# with Vim, whose own copy is loaded. recordVimCorpus clones the others into
# build/vim-oracle; check never does.
repeat = https://github.com/tpope/vim-repeat.git 65846025c15494983dafe5e3b46c8f88ab2e9635
```

In `plugins/editor/vim-oracle.properties`, replace:

```properties
textobj-indent = https://github.com/michaeljsmith/vim-indent-object.git 8ab36d5ec2a3a60468437a95e142ce994df598c6
argtextobj = https://github.com/vim-scripts/argtextobj.vim.git f3fbe427f7b4ec436416a5816d714dc917dc530b
```

with:

```properties
textobj-indent = https://github.com/michaeljsmith/vim-indent-object.git 8ab36d5ec2a3a60468437a95e142ce994df598c6
argtextobj = https://github.com/vim-scripts/argtextobj.vim.git f3fbe427f7b4ec436416a5816d714dc917dc530b
matchit = packadd
```

- [ ] **Step 3: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExtensionsTest'
```

Expected: FAIL: the test does not compile (`MatchitLanguages` does not exist).

- [ ] **Step 4: Write the languages and matchit**

Create `plugins/editor/src/main/resources/dev/jasper/editor/vim/matchit-languages.tsv`:

```text
# Generated from Vim 9.1's ftplugins with matchit loaded (plan V4c): a language's b:match_words, b:match_ignorecase,
# b:match_skip and 'matchpairs'. Columns: RSyntaxTextArea style, Vim filetype, ignorecase (- when not set), skip (- when
# not set), matchpairs, match_words. Tabs separate the columns.
text/asm	asm	1	s:comment\|string\|character\|special	(:),{:},[:]	^\s*%\s*if\%(\|num\|idn\|nidn\)\>:^\s*%\s*elif\>:^\s*%\s*else\>:^\s*%\s*endif\>,^\s*%\s*macro\>:^\s*%\s*endmacro\>,^\s*%\s*rep\>:^\s*%\s*endrep\>
text/c	c	-	s:comment\|string\|character\|special	(:),{:},[:]	^\s*#\s*if\%(\|def\|ndef\)\>:^\s*#\s*elif\%(\|def\|ndef\)\>:^\s*#\s*else\>:^\s*#\s*endif\>
text/cpp	cpp	-	s:comment\|string\|character\|special	(:),{:},[:]	^\s*#\s*if\%(\|def\|ndef\)\>:^\s*#\s*elif\%(\|def\|ndef\)\>:^\s*#\s*else\>:^\s*#\s*endif\>
text/clojure	clojure	-	s:comment\|string\|regex\|character	(:),{:},[:]	(:),{:},[:]
text/cs	cs	0	-	(:),{:},[:]	\%(^\s*\)\@<=#\s*region\>:\%(^\s*\)\@<=#\s*endregion\>,
text/dtd	dtd	-	-	(:),{:},[:]	<!--:-->,<!:>
text/fortran	fortran	1	-	(:),{:},[:]	(:),\%(\<end\s\+\)\@<!\<select\s\+type\>:\%(\<select\s\+\)\@<!\<type\|class\>:\<end\s*select\>,\%(\<end\s\+\)\@<!\<select\s\+rank\>:\%(\<select\s\+\)\@<!\<rank\>:\<end\s*select\>,\%(\<end\s\+\)\@<!\<select\>:\%(\<select\s\+\)\@<!\<case\>:\<end\s*select\>,\%(\<end\s\+\|\<else\s\+\)\@<!\<if\s*(.\+)\s*then\>:\%(^\s*#\s*\)\@<!\<else\s*\%(if\s*(.\+)\s*then\)\=\>:\%(^\s*#\s*\)\@<!\<end\s*if\>,do\s\+\(\d\+\):\%(^\s*\)\@<=\1\s,\%(\<end\s\+\)\@<!\<do\>:\<end\s*do\>,\%(\<end\s\+\|\<else\s\+\)\@<!\<where\>:\<elsewhere\>:\<end\s*where\>,\%(\<end\s\+\)\@<!\<type\s*[^(]:\<end\s*type\>,\%(\<end\s\+\)\@<!\<forall\>:\<end\s*forall\>,\%(\<end\s\+\)\@<!\<associate\>:\<end\s*associate\>,\%(\<end\s\+\)\@<!\<change\s\+team\>:\<end\s*team\>,\%(\<end\s\+\)\@<!\<critical\>:\<end\s*critical\>,\%(\<end\s\+\)\@<!\<block\>:\<end\s*block\>,\%(\<end\s\+\)\@<!\<enum\>:\<end\s*enum\>,\%(\<end\s\+\)\@<!\<interface\>:\<end\s*interface\>,\%(\<end\s\+\)\@<!\<subroutine\>:\<end\s*subroutine\>,\%(\<end\s\+\)\@<!\<function\>:\<end\s*function\>,\%(\<end\s\+\)\@<!\<module\>\%(\s\+procedure\>\)\@!:\<end\s*module\>,\%(\<end\s\+\)\@<!\<program\>:\<end\s*program\>,\%(^\s*\)\@<=#\s*if\%(def\|ndef\)\=\>:\%(^\s*\)\@<=#\s*\%(elif\|else\)\>:\%(^\s*\)\@<=#\s*endif\>
text/html	html	1	-	(:),{:},[:],<:>	<!--:-->,<:>,<\@<=[ou]l\>[^>]*\%(>\|$\):<\@<=li\>:<\@<=/[ou]l>,<\@<=dl\>[^>]*\%(>\|$\):<\@<=d[td]\>:<\@<=/dl>,<\@<=\([^/!][^ \t>]*\)[^>]*\%(>\|$\):<\@<=/\1>
text/javascript	javascript	0	-	(:),{:},[:]	\<do\>:\<while\>,<\@<=\([^ \t>/]\+\)\%(\s\+[^>]*\%([^/]>\|$\)\|>\|$\):<\@<=/\1>,<\@<=\%([^ \t>/]\+\)\%(\s\+[^/>]*\|$\):/>
text/jsp	jsp	1	-	(:),{:},[:],<:>	<!--:-->,<:>,<\@<=[ou]l\>[^>]*\%(>\|$\):<\@<=li\>:<\@<=/[ou]l>,<\@<=dl\>[^>]*\%(>\|$\):<\@<=d[td]\>:<\@<=/dl>,<\@<=\([^/!][^ \t>]*\)[^>]*\%(>\|$\):<\@<=/\1>
text/latex	tex	0	r:\\\@<!\%(\\\\\)*%	(:),{:},[:]	(:),\[:],{:},\\(:\\),\\\[:\\],\\{:\\},\\begin\s*\({\a\+\*\=}\):\\end\s*\1
text/lua	lua	0	-	(:),{:},[:]	\<\%(do\|function\|if\)\>:\<\%(return\|else\|elseif\)\>:\<end\>,\<repeat\>:\<until\>,\%(--\)\=\[\(=*\)\[:]\1]
text/makefile	make	-	-	(:),{:},[:]	^ *ifn\=\(eq\|def\)\>:^ *else\(\s\+ifn\=\(eq\|def\)\)\=\>:^ *endif\>,\<define\>:\<endef\>,^!\s*if\(n\=def\)\=\>:^!\s*else\(if\(n\=def\)\=\)\=\>:^!\s*endif\>
text/nsis	nsis	1	-	(:),{:},[:]	\${\%(If\|IfNot\|Unless\)}:\${\%(Else\|ElseIf\|ElseIfNot\|ElseUnless\)}:\${\%(EndIf\|EndUnless\)},\${Select}:\${EndSelect},\${Switch}:\${EndSwitch},\${\%(Do\|DoWhile\|DoUntil\)}:\${\%(Loop\|LoopWhile\|LoopUntil\)},\${\%(For\|ForEach\)}:\${Next},\<Function\>:\<FunctionEnd\>,\<Section\>:\<SectionEnd\>,\<SectionGroup\>:\<SectionGroupEnd\>,\<PageEx\>:\<PageExEnd\>,\${MementoSection}:\${MementoSectionEnd},!if\%(\%(macro\)\?n\?def\)\?\>:!else\>:!endif\>,!macro\>:!macroend\>
text/perl	perl	-	s:comment\|string\|perlQQ\|perlShellCommand\|perlHereDoc\|perlSubstitution\|perlTranslation\|perlMatch\|perlFormatField	(:),{:},[:]	\<if\>:\<elsif\>:\<else\>
text/php	php	1	PhpMatchSkip('php')	(:),{:},[:],<:>	<!--:-->,<:>,<\@<=[ou]l\>[^>]*\%(>\|$\):<\@<=li\>:<\@<=/[ou]l>,<\@<=dl\>[^>]*\%(>\|$\):<\@<=d[td]\>:<\@<=/dl>,<\@<=\([^/!][^ \t>]*\)[^>]*\%(>\|$\):<\@<=/\1>
text/ruby	ruby	0	synIDattr(synID(line('.'),col('.'),0),'name') =~ '\<ruby\%(String\|.+Delimiter\|Character\|.+Escape\|Regexp\|Interpolation\|Comment\|Documentation\|ConditionalModifier\|RepeatModifier\|RescueModifier\|OptionalDo\|MethodName\|BlockArgument\|KeywordAsMethod\|ClassVariable\|InstanceVariable\|GlobalVariable\|Symbol\)\>'	(:),{:},[:]	{\|\<\%(if\|unless\|case\|while\|until\|for\|do\|class\|module\|def\|=\@<!begin\)\>=\@!:\<\%(else\|elsif\|ensure\|when\|rescue\|break\|redo\|next\|retry\)\>:}\|\%(^\|[^.\:@$=]\)\@<=\<end\:\@!\>,^=begin\>:^=end\>,,\[:\],(:)
text/sql	sql	1	-	(:),{:},[:],<:>	(:),{:},[:],<:>,\%(\<begin\)\%(\s\+\%(try\|catch\)\>\)\@!:\<end\>\W*$,\<begin\s\+try\>:\<end\s\+try\>:\<begin\s\+catch\>:\<end\s\+catch\>,\%(\<end\s\+\)\@<!\<if\>:\<elsif\>\|\<elseif\>\|\<else\>:\<end\s\+if\>,\(^\s*\)\@<=\(\<\%(do\|for\|while\|loop\)\>.*\):\%(\<exit\>\|\<leave\>\|\<break\>\|\<continue\>\):\%(\<doend\>\|\%(\<end\s\+\%(for\|while\|loop\>\)\)\),\%(\%(\<end\s\+\)\@<!\<case\>\):\%(\%(\<when\>\%(\s\+\%(\%(\<not\>\s\+\)\?<matched\>\)\|\<others\>\)\@!\)\):\%(\<when\s\+others\>\|\<end\s\+case\>\),\<merge\>:\<when\s\+not\s\+matched\>:\<when\s\+matched\>,\%(\<create\s\+\%(or\s\+replace\s\+\)\?\)\?\%(function\|procedure\|event\):\<returns\?\>
text/unix	sh	0	synIDattr(synID(line('.'),col('.'),0),'name') =~ 'shSnglCase'	(:),{:},[:]	\%(;\s*\|^\s*\)\@<=if\>:\%(;\s*\|^\s*\)\@<=elif\>:\%(;\s*\|^\s*\)\@<=else\>:\%(;\s*\|^\s*\)\@<=fi\>,\%(;\s*\|^\s*\)\@<=\%(for\|while\)\>:\%(;\s*\|^\s*\)\@<=done\>,\%(;\s*\|^\s*\)\@<=case\>:\%(;\s*\|^\s*\)\@<=esac\>
text/vb	vb	1	-	(:),{:},[:]	\%(^\s*\)\@<=\<if\>.*\<then\>\s*\%($\|'\):\%(^\s*\)\@<=\<else\>:\%(^\s*\)\@<=\<elseif\>:\%(^\s*\)\@<=\<end\>\s\+\<if\>,\%(^\s*\)\@<=\<for\>:\%(^\s*\)\@<=\<next\>,\%(^\s*\)\@<=\<while\>:\%(^\s*\)\@<=\<wend\>,\%(^\s*\)\@<=\<do\>:\%(^\s*\)\@<=\<loop\>\s\+\<while\>,\%(^\s*\)\@<=\<select\>\s\+\<case\>:\%(^\s*\)\@<=\<case\>:\%(^\s*\)\@<=\<end\>\s\+\<select\>,\%(^\s*\)\@<=\<enum\>:\%(^\s*\)\@<=\<end\>\s\<enum\>,\%(^\s*\)\@<=\<with\>:\%(^\s*\)\@<=\<end\>\s\<with\>,\%(^\s*\)\@<=\%(\<\%(private\|public\)\>\s\+\)\=\<function\>\s\+\([^ \t(]\+\):\%(^\s*\)\@<=\<\1\>\s*=:\%(^\s*\)\@<=\<end\>\s\+\<function\>,\%(^\s*\)\@<=\%(\<\%(private\|public\)\>\s\+\)\=\<sub\>\s\+:\%(^\s*\)\@<=\<end\>\s\+\<sub\>
text/vhdl	vhdl	1	-	(:),{:},[:]	\%(\<end\s\+\)\@<!\<if\>:\<elsif\>:\<else\>:\<end\s\+if\>,\%(\<end\s\+\)\@<!\<case\>:\<when\>:\<end\s\+case\>,\%(\<end\s\+\)\@<!\<loop\>:\<end\s\+loop\>,\%(\<end\s\+\)\@<!\<for\>:\<end\s\+for\>,\%(\<end\s\+\)\@<!\<generate\>:\<end\s\+generate\>,\%(\<end\s\+\)\@<!\<record\>:\<end\s\+record\>,\%(\<end\s\+\)\@<!\<units\>:\<end\s\+units\>,\%(\<end\s\+\)\@<!\<process\>:\<end\s\+process\>,\%(\<end\s\+\)\@<!\<block\>:\<end\s\+block\>,\%(\<end\s\+\)\@<!\<function\>:\<end\s\+function\>,\%(\<end\s\+\)\@<!\<entity\>:\<end\s\+entity\>,\%(\<end\s\+\)\@<!\<component\>:\<end\s\+component\>,\%(\<end\s\+\)\@<!\<architecture\>:\<end\s\+architecture\>,\%(\<end\s\+\)\@<!\<package\>:\<end\s\+package\>,\%(\<end\s\+\)\@<!\<procedure\>:\<end\s\+procedure\>,\%(\<end\s\+\)\@<!\<configuration\>:\<end\s\+configuration\>
text/xml	xml	0	-	(:),{:},[:]	<:>,<\@<=!\[CDATA\[:]]>,<\@<=!--:-->,<\@<=?\k\+:?>,<\@<=\([^ \t>/]\+\)\%(\s\+[^>]*\%([^/]>\|$\)\|>\|$\):<\@<=/\1>,<\@<=\%([^ \t>/]\+\)\%(\s\+[^/>]*\|$\):/>
text/delphi	pascal	1	-	(:),{:},[:]	\<\%(asm\|begin\|case\|\%(\%(=\|packed\)\s*\)\@<=\%(class\|object\)\|\%(=\s*\)\@<=interface\|record\|try\)\>:\%(^\s*\)\@<=\%(except\|finally\|else\|otherwise\)\>:\<end\>\.\@!,\<repeat\>:\<until\>,\<unit\>:\<\%(\%(^\s*\)\@<=interface\|implementation\|initialization\|finalization\)\>:\<end\.
text/markdown	markdown	1	-	(:),{:},[:],<:>	<!--:-->,<:>,<\@<=[ou]l\>[^>]*\%(>\|$\):<\@<=li\>:<\@<=/[ou]l>,<\@<=dl\>[^>]*\%(>\|$\):<\@<=d[td]\>:<\@<=/dl>,<\@<=\([^/!][^ \t>]*\)[^>]*\%(>\|$\):<\@<=/\1>
text/rust	rust	-	s:comment\|string\|rustCharacter\|rustArrow	(:),{:},[:],<:>	
text/java	java	-	-	(:),{:},[:]	
text/python	python	-	-	(:),{:},[:]	
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/MatchitLanguages.java`:

```java
package dev.jasper.editor.vim;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What Vim's filetype plugins tell matchit about each language (spec 8): {@code b:match_words},
 * {@code b:match_ignorecase}, {@code b:match_skip} and 'matchpairs', by the tab's RSyntaxTextArea style. The table,
 * {@code matchit-languages.tsv}, was made by Vim 9.1 with its filetype plugins and matchit loaded.
 */
final class MatchitLanguages {
    private MatchitLanguages() { }

    /**
     * A language.
     *
     * @param style the RSyntaxTextArea style ({@code text/html})
     * @param filetype Vim's name for it ({@code html})
     * @param ignoreCase b:match_ignorecase, or null when the plugin leaves 'ignorecase' to decide
     * @param skip b:match_skip, or null for matchit's own ({@code s:comment\|string})
     * @param matchpairs 'matchpairs' as the filetype plugin leaves it
     * @param words b:match_words, empty for none
     */
    record Language(String style, String filetype, Boolean ignoreCase, String skip, String matchpairs, String words) {
        /** @return the pairs its filetype plugin adds to Vim's default 'matchpairs' */
        java.util.List<String> addedPairs() {
            java.util.List<String> added = new java.util.ArrayList<>(java.util.List.of(matchpairs.split(",")));
            added.removeAll(java.util.List.of("(:)", "{:}", "[:]", ""));
            return added;
        }
    }

    /** A language no filetype plugin changes: plain text, and languages without an entry. */
    static final Language NONE = new Language("text/plain", "", null, null, "(:),{:},[:]", "");

    private static final Map<String, Language> BY_STYLE = load();

    private static Map<String, Language> load() {
        Map<String, Language> out = new LinkedHashMap<>();
        try (InputStream in = MatchitLanguages.class.getResourceAsStream("matchit-languages.tsv")) {
            if (in == null) throw new IllegalStateException("matchit-languages.tsv is missing");
            var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] f = line.split("\t", -1);
                Boolean ic = f[2].equals("-") ? null : !f[2].equals("0");
                out.put(f[0], new Language(f[0], f[1], ic, f[3].equals("-") ? null : f[3], f[4], f[5]));
            }
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
        return out;
    }

    /** @return the language of the editor's tab */
    static Language of(VimEditor editor) {
        return editor.area() instanceof org.fife.ui.rsyntaxtextarea.RSyntaxTextArea area ? of(area.getSyntaxEditingStyle()) : NONE;
    }

    /** @return the language of an RSyntaxTextArea style */
    static Language of(String style) { return BY_STYLE.getOrDefault(style, NONE); }

    /** @return the language Vim calls {@code filetype} */
    static Optional<Language> byFiletype(String filetype) {
        return BY_STYLE.values().stream().filter(language -> language.filetype().equals(filetype)).findFirst();
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Matchit.java`:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The matchit extension (spec 8), ported from Vim's own matchit.vim (the {@code pack/dist/opt/matchit} of Vim 9.1):
 * {@code %} and {@code g%} go between the words of a group ({@code if}, {@code else}, {@code endif}; HTML tags)
 * as the tab's language defines them in {@code b:match_words} ({@link MatchitLanguages}), besides 'matchpairs',
 * C comments and {@code #if}; {@code [%} and {@code ]%} go to the enclosing group's start and end, and Visual
 * {@code a%} takes the group. Like the plugin, the port builds Vim patterns out of the words, resolves their
 * back references, and moves with searchpair(), passing over matches in comments and strings as the language says.
 */
final class Matchit {
    private static final String OPTION = "matchit";
    /** s:notslash: not after an odd number of backslashes. */
    private static final String NOTSLASH = "\\\\\\@1<!\\%(\\\\\\\\\\)*";
    private static final String DEFAULT_WRAPPER = "\\/\\*:\\*\\/,#\\s*if\\%(n\\=def\\)\\=:#\\s*else\\>:#\\s*elif\\%(n\\=def\\)\\=\\>:#\\s*endif\\>";
    private static final String DEFAULT_MULTI = "\\/\\*:\\*\\/,#\\s*if\\%(n\\=def\\)\\=:#\\s*else\\>:#\\s*elif\\>:#\\s*endif\\>";

    /** The plugin's script variables: the parsed words, which each use builds again. */
    private boolean doBR;
    private String pat = "", all = "", patBR = "";

    static void register(VimCommands commands) {
        for (String mode : new String[] { "Normal", "Visual", "Operation" }) {
            commands.motion("<Plug>(Matchit" + mode + "Forward)", "to the next word of the group at the cursor (`%`, with `set matchit` or `packadd matchit`)",
                c -> wrapper(c, true));
            commands.motion("<Plug>(Matchit" + mode + "Backward)", "to the previous word of the group at the cursor (`g%`)", c -> wrapper(c, false));
            commands.motion("<Plug>(Matchit" + mode + "MultiBackward)", "back to the start of the group around the cursor (`[%`)", c -> multi(c, true));
            commands.motion("<Plug>(Matchit" + mode + "MultiForward)", "on to the end of the group around the cursor (`]%`)", c -> multi(c, false));
        }
        commands.visual("<Plug>(MatchitVisualTextObject)", "the group around the Visual area, from its start to its end (`a%`)", false, false, c -> {
            Extensions.require(c, OPTION);
            // xmap <Plug>(MatchitVisualTextObject) <Plug>(MatchitVisualMultiBackward)o<Plug>(MatchitVisualMultiForward)
            Script.normal(c.editor, "<Plug>(MatchitVisualMultiBackward)o<Plug>(MatchitVisualMultiForward)");
        });
    }

    /** {@code :packadd matchit} switches matchit on; another package is not there. */
    static void packadd(ExCommand cmd) {
        String name = cmd.arg.strip();
        if (name.isEmpty()) throw new VimFailure("E471: Argument required");
        if (!name.equals("matchit")) throw new VimFailure("E919: Directory not found in 'packpath': \"pack/*/opt/" + name + "\"");
        cmd.editor.options().set(OPTION, true);
    }

    /** What the mode means for a motion: an operator's text is the area Visual mode would take. */
    private static Target target(Context c, Pos pos, boolean jump) {
        // In Vim the operator's : motion becomes Visual mode (norm! v) unless the type was forced with v or V;
        // v then makes the exclusive : motion inclusive, which an inclusive target turned exclusive by v here is.
        Target t = c.operator != null && c.forced == 'v' ? Target.exclusive(pos) : Target.inclusive(pos);
        t = t.withoutAdjust();
        if (c.operator != null && c.forced == 0) t = t.asArea();
        return jump ? t.asJump() : t;
    }

    /** matchit#Match_wrapper: {@code %} and {@code g%}. */
    private static Target wrapper(Context c, boolean forward) {
        Extensions.require(c, OPTION);
        VimEditor editor = c.editor;
        Pos startpos = editor.cursor();
        Buffer b = editor.buffer();
        if (c.hasCount) {
            // normal! {count}%: a line by percentage.
            int line = Math.max(0, Math.min(b.count() - 1, (c.count * b.count() + 99) / 100 - 1));
            return Target.exclusive(new Pos(line, Text.firstNonBlank(b.line(line)))).asJump();
        }
        MatchitLanguages.Language language = MatchitLanguages.of(editor);
        VimStrings s = strings(editor, language);
        Matchit script = editor.vim().extensions().script(Matchit.class, Matchit::new);
        String matchWords = language.words();
        String mps = editor.options().string("matchpairs");
        String dflt = VimStrings.escape(mps, "[$^.*~\\/?]") + (mps.isEmpty() ? "" : ",") + DEFAULT_WRAPPER;
        matchWords = matchWords + (matchWords.isEmpty() ? "" : ",") + dflt;
        script.parse(s, matchWords, true);
        String matchline = b.line(startpos.line);
        String regexp = wholematch(s, matchline, script.all, startpos.col);
        int curcol = s.match(matchline, regexp);
        if (curcol == -1) return target(c, startpos, false);
        int endcol = s.matchend(matchline, regexp);
        int suf = matchline.length() - endcol;
        String prefix = curcol != 0 ? "^.*\\%" + (curcol + 1) + "c\\%(" : "^\\%(";
        String suffix = suf != 0 ? "\\)\\%" + (endcol + 1) + "c.*$" : "\\)$";
        String group = choose(s, script.pat, matchline, ",", ":", prefix, suffix, script.patBR);
        if (group == null) return target(c, startpos, false);
        int i = s.matchend(group, NOTSLASH + ",");
        String groupBR = VimStrings.strpart(group, i);
        group = VimStrings.strpart(group, 0, i - 1);
        if (script.doBR) group = insertRefs(s, groupBR, prefix, group, suffix, matchline);
        i = s.matchend(group, NOTSLASH + ":");
        int j = s.matchend(group, ".*" + NOTSLASH + ":");
        String ini = VimStrings.strpart(group, 0, i - 1);
        String mid = s.substitute(VimStrings.strpart(group, i, j - i - 1), NOTSLASH + "\\zs:", "\\\\|", "g");
        String fin = VimStrings.strpart(group, j);
        ini = plain(s, ini);
        mid = plain(s, mid);
        fin = plain(s, fin);
        boolean atFin = s.matches(matchline, prefix + fin + suffix), atIni = s.matches(matchline, prefix + ini + suffix);
        if (forward && atFin || !forward && atIni) mid = "";
        boolean backward = forward && atFin || !forward && !atIni;
        Predicate<Pos> skip = skip(editor, s, language.skip());
        Pos at = new Pos(startpos.line, curcol);
        // The skip expression is dropped when the cursor itself is where it would skip (in a comment).
        if (skip.test(at)) skip = p -> false;
        editor.setCursor(at);
        int found = Search.searchpair(editor, s, ini, mid, fin, backward, false, skip);
        Pos finalPos = found > 0 ? editor.cursor() : startpos;
        editor.setCursor(startpos);
        // normal! m' at the start, then the match; s:CleanUp takes an operator to the end of the word found.
        if (c.operator != null && finalPos.compareTo(startpos) > 0) {
            String line = b.line(finalPos.line);
            String end = wholematch(s, line, mid + "\\|" + fin, finalPos.col);
            int endCol = s.matchend(line, end);
            if (endCol > finalPos.col + 1) finalPos = new Pos(finalPos.line, endCol - 1);
        }
        return target(c, finalPos, true);
    }

    /** matchit#MultiMatch: {@code [%} ({@code bW}) and {@code ]%} ({@code W}), {@code v:count1} levels out. */
    private static Target multi(Context c, boolean backward) {
        Extensions.require(c, OPTION);
        VimEditor editor = c.editor;
        Pos startpos = editor.cursor();
        MatchitLanguages.Language language = MatchitLanguages.of(editor);
        VimStrings s = strings(editor, language);
        Matchit script = editor.vim().extensions().script(Matchit.class, Matchit::new);
        String matchWords = language.words();
        String mps = editor.options().string("matchpairs");
        String dflt = VimStrings.escape(mps, "[$^.*~\\/?]") + (mps.isEmpty() ? "" : ",") + DEFAULT_MULTI;
        matchWords = matchWords + (matchWords.isEmpty() ? "" : ",") + dflt;
        script.parse(s, matchWords, false);
        List<String> openlist = s.split(script.pat + ",", NOTSLASH + "\\zs:.\\{-}" + NOTSLASH + ",");
        List<String> closelist = new ArrayList<>(), middlelist = new ArrayList<>();
        for (String midclo : s.split("," + script.pat, NOTSLASH + "\\zs,.\\{-}" + NOTSLASH + ":")) {
            List<String> parts = s.split(midclo, NOTSLASH + ":");
            if (parts.isEmpty()) continue;
            closelist.add(parts.getLast());
            middlelist.addAll(parts.subList(0, parts.size() - 1));
        }
        String open = join(s, openlist), middle = join(s, middlelist), close = join(s, closelist);
        String openpat = s.substitute(s.substitute(open, "\\%(" + NOTSLASH + "\\)\\@<=\\\\(", "\\\\%(", "g"), ",", "\\\\|", "g");
        String closepat = s.substitute(s.substitute(close, "\\%(" + NOTSLASH + "\\)\\@<=\\\\(", "\\\\%(", "g"), ",", "\\\\|", "g");
        String middlepat = s.substitute(s.substitute(middle, "\\%(" + NOTSLASH + "\\)\\@<=\\\\(", "\\\\%(", "g"), ",", "\\\\|", "g");
        Predicate<Pos> skip = skip(editor, s, language.skip());
        if (skip.test(startpos)) skip = p -> false;
        // mark ', then each level out; a level not found ends it where the one before stopped.
        for (int level = c.count; level > 0; level--) {
            if (Search.searchpair(editor, s, openpat, middlepat, closepat, backward, false, skip) < 1) break;
        }
        Pos finalPos = editor.cursor();
        editor.setCursor(startpos);
        return target(c, finalPos, true);
    }

    /** The patterns of a list, those with {@code \|} in {@code \%(\)}, joined with commas. */
    private static String join(VimStrings s, List<String> list) {
        List<String> out = new ArrayList<>();
        for (String v : list) out.add(s.matches(v, NOTSLASH + "\\\\|") ? "\\%(" + v + "\\)" : v);
        return String.join(",", out);
    }

    /** ini, mid and fin: escaped {@code :} and {@code ,} plain, and groups not capturing. */
    private static String plain(VimStrings s, String part) {
        part = s.substitute(part, NOTSLASH + "\\zs\\\\\\(:\\|,\\)", "\\1", "g");
        return s.substitute(part, NOTSLASH + "\\zs\\\\(", "\\\\%(", "g");
    }

    /** The string functions with 'ignorecase' as b:match_ignorecase says (s:RestoreOptions). */
    private static VimStrings strings(VimEditor editor, MatchitLanguages.Language language) {
        boolean ic = language.ignoreCase() != null ? language.ignoreCase() : editor.options().flag("ignorecase");
        return new VimStrings(ic, editor.options().keywords().javaClass());
    }

    /**
     * The words parsed, as each use does: {@code s:pat} (with back references resolved by s:ParseWords when there are
     * any), {@code s:all} (every word, for finding the one at the cursor) and {@code s:patBR}.
     */
    private void parse(VimStrings s, String matchWords, boolean wrapper) {
        if (!s.matches(matchWords, NOTSLASH + "\\\\\\d")) {
            doBR = false;
            pat = matchWords;
        } else {
            doBR = true;
            pat = parseWords(s, matchWords);
        }
        if (wrapper) {
            all = s.substitute(pat, NOTSLASH + "\\zs[,:]\\+", "\\\\|", "g");
            all = s.substitute(all, "\\\\,", ",", "g");
            all = s.substitute(all, "\\\\(", "\\\\%(", "g");
        } else all = s.substitute(pat, "[,:]\\+", "\\\\|", "g");
        all = "\\%(" + all + "\\)";
        patBR = s.substitute(matchWords + ",", NOTSLASH + "\\zs[,:]*,[,:]*", ",", "g");
        patBR = s.substitute(patBR, NOTSLASH + "\\zs:\\{2,}", ":", "g");
        if (wrapper) patBR = s.substitute(patBR, "\\\\,", ",", "g");
    }

    /**
     * s:ParseSkip and its evaluation: {@code s:} and {@code S:} name syntax groups, which Jasper takes from the tab's
     * tokens ({@code Comment}, {@code String}, {@code Character}, {@code Regex}); {@code r:} and {@code R:} test the
     * line up to the position; any other expression is taken as {@code s:comment\|string}.
     */
    static Predicate<Pos> skip(VimEditor editor, VimStrings s, String skip) {
        if (skip == null || skip.isEmpty()) skip = "s:comment\\|string";
        if (skip.length() < 2 || skip.charAt(1) != ':' || "sSrR".indexOf(skip.charAt(0)) < 0) skip = "s:comment\\|string";
        char kind = skip.charAt(0);
        String pattern = skip.substring(2);
        Buffer b = editor.buffer();
        if (kind == 'r' || kind == 'R') {
            return p -> {
                String line = b.line(p.line);
                boolean m = s.matches(VimStrings.strpart(line, 0, p.col + 1), pattern);
                return kind == 'r' ? m : !m;
            };
        }
        VimStrings ci = new VimStrings(true, editor.options().keywords().javaClass());
        return p -> {
            String group = syntaxGroup(editor, p);
            if (group.isEmpty()) return false;
            boolean m = ci.matches(group, pattern);
            return kind == 's' ? m : !m;
        };
    }

    /** The syntax group at a position, as far as the tab's language tokens tell it; empty in plain text. */
    private static String syntaxGroup(VimEditor editor, Pos p) {
        if (!(editor.area() instanceof org.fife.ui.rsyntaxtextarea.RSyntaxTextArea area)) return "";
        if (org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE.equals(area.getSyntaxEditingStyle())) return "";
        org.fife.ui.rsyntaxtextarea.Token token = area.modelToToken(editor.buffer().offset(p));
        if (token == null) return "";
        return switch (token.getType()) {
            case org.fife.ui.rsyntaxtextarea.TokenTypes.COMMENT_EOL, org.fife.ui.rsyntaxtextarea.TokenTypes.COMMENT_MULTILINE,
                org.fife.ui.rsyntaxtextarea.TokenTypes.COMMENT_DOCUMENTATION, org.fife.ui.rsyntaxtextarea.TokenTypes.COMMENT_KEYWORD,
                org.fife.ui.rsyntaxtextarea.TokenTypes.COMMENT_MARKUP, org.fife.ui.rsyntaxtextarea.TokenTypes.MARKUP_COMMENT -> "Comment";
            case org.fife.ui.rsyntaxtextarea.TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, org.fife.ui.rsyntaxtextarea.TokenTypes.LITERAL_BACKQUOTE,
                org.fife.ui.rsyntaxtextarea.TokenTypes.ERROR_STRING_DOUBLE -> "String";
            case org.fife.ui.rsyntaxtextarea.TokenTypes.LITERAL_CHAR, org.fife.ui.rsyntaxtextarea.TokenTypes.ERROR_CHAR -> "Character";
            case org.fife.ui.rsyntaxtextarea.TokenTypes.REGEX -> "Regex";
            default -> "";
        };
    }

    /** s:Wholematch: the pattern that finds the word ending on or after {@code start}, starting at or before it. */
    private static String wholematch(VimStrings s, String string, String pattern, int start) {
        String group = "\\%(" + pattern + "\\)";
        String prefix = start != 0 ? "\\(^.*\\%<" + (start + 2) + "c\\)\\zs" : "^";
        String suffix = start + 1 < string.length() ? "\\(\\%>" + (start + 1) + "c.*$\\)\\@=" : "$";
        if (!s.matches(string, prefix + group + suffix)) prefix = "";
        return prefix + group + suffix;
    }

    /**
     * s:Choose: the first of the comma-separated groups whose words (separated by {@code branch}) match the string
     * between prefix and suffix, with the corresponding group of {@code alt} after a comma; null for none.
     */
    private static String choose(VimStrings s, String patterns, String string, String comma, String branch, String prefix, String suffix,
                                 String alt) {
        String tail = s.matches(patterns, comma + "$") ? patterns : patterns + comma;
        int i = s.matchend(tail, NOTSLASH + comma);
        String alttail = null;
        int j = 0;
        if (alt != null) {
            alttail = s.matches(alt, comma + "$") ? alt : alt + comma;
            j = s.matchend(alttail, NOTSLASH + comma);
        }
        String current = VimStrings.strpart(tail, 0, i - 1);
        String currpat = branchPattern(s, current, branch);
        while (!s.matches(string, prefix + currpat + suffix)) {
            tail = VimStrings.strpart(tail, i);
            i = s.matchend(tail, NOTSLASH + comma);
            if (i == -1) return null;
            current = VimStrings.strpart(tail, 0, i - 1);
            currpat = branchPattern(s, current, branch);
            if (alt != null) {
                alttail = VimStrings.strpart(alttail, j);
                j = s.matchend(alttail, NOTSLASH + comma);
            }
        }
        if (alt != null) current = current + comma + VimStrings.strpart(alttail, 0, j - 1);
        return current;
    }

    private static String branchPattern(VimStrings s, String current, String branch) {
        String currpat = branch.isEmpty() ? current : s.substitute(current, NOTSLASH + branch, "\\\\|", "g");
        return s.substitute(currpat, "\\\\,", ",", "g");
    }

    /** s:InsertRefs: the group's back references filled in with what the words at the cursor matched. */
    private static String insertRefs(VimStrings s, String groupBR, String prefix, String group, String suffix, String matchline) {
        if (!s.matches(matchline, prefix + s.substitute(group, NOTSLASH + "\\zs:", "\\\\|", "g") + suffix)) return group;
        int i = s.matchend(groupBR, NOTSLASH + ":");
        String ini = VimStrings.strpart(groupBR, 0, i - 1);
        String tailBR = VimStrings.strpart(groupBR, i);
        String word = choose(s, group, matchline, ":", "", prefix, suffix, groupBR);
        if (word == null) return group;
        i = s.matchend(word, NOTSLASH + ":");
        String wordBR = VimStrings.strpart(word, i);
        word = VimStrings.strpart(word, 0, i - 1);
        String table;
        if (!wordBR.equals(ini)) table = resolve(s, ini, wordBR, "table");
        else {
            StringBuilder t = new StringBuilder();
            for (int d = 0; d < 10; d++) t.append(s.matches(tailBR, NOTSLASH + "\\\\" + d) ? (char) ('0' + d) : '-');
            table = t.toString();
        }
        for (int d = 9; d > 0; d--) {
            if (table.charAt(d) == '-') continue;
            String backref = s.substitute(matchline, prefix + word + suffix, "\\" + table.charAt(d), "");
            backref = VimStrings.escape(backref, "*,:");
            int[] startLen = refStartLen(s, ini, d);
            if (startLen == null) continue;
            ini = VimStrings.strpart(ini, 0, startLen[0]) + backref + VimStrings.strpart(ini, startLen[0] + startLen[1]);
            tailBR = s.substitute(tailBR, NOTSLASH + "\\zs\\\\" + d, VimStrings.escape(backref, "\\&"), "g");
        }
        return ini + ":" + tailBR;
    }

    /** s:ParseWords: each word's back references to its group's first word resolved. */
    private static String parseWords(VimStrings s, String groupsIn) {
        String groups = s.substitute(groupsIn + ",", NOTSLASH + "\\zs[,:]*,[,:]*", ",", "g");
        groups = s.substitute(groups, NOTSLASH + "\\zs:\\{2,}", ":", "g");
        StringBuilder parsed = new StringBuilder();
        while (s.matches(groups, "[^,:]")) {
            int i = s.matchend(groups, NOTSLASH + ":");
            int j = s.matchend(groups, NOTSLASH + ",");
            String ini = VimStrings.strpart(groups, 0, i - 1);
            String tail = VimStrings.strpart(groups, i, j - i - 1) + ":";
            groups = VimStrings.strpart(groups, j);
            parsed.append(ini);
            i = s.matchend(tail, NOTSLASH + ":");
            while (i != -1) {
                String word = VimStrings.strpart(tail, 0, i - 1);
                tail = VimStrings.strpart(tail, i);
                i = s.matchend(tail, NOTSLASH + ":");
                parsed.append(':').append(resolve(s, ini, word, "word"));
            }
            parsed.append(',');
        }
        return s.substitute(parsed.toString(), ",$", "", "");
    }

    /**
     * s:Ref with {@code "start", "len"}: where the {@code d}-th {@code \(\)} group of the pattern starts and how long
     * it is, the {@code \(} and {@code \)} included; null when there is none.
     */
    private static int[] refStartLen(VimStrings s, String string, int d) {
        int len = string.length();
        if (d == 0) return new int[] { 0, len };
        String match = string;
        for (int cnt = d; cnt > 0; cnt--) {
            int index = s.matchend(match, NOTSLASH + "\\\\(");
            if (index == -1) return null;
            match = VimStrings.strpart(match, index);
        }
        int start = len - match.length();
        int cnt = 1;
        while (cnt != 0) {
            int index = s.matchend(match, NOTSLASH + "\\\\(\\|\\\\)") - 1;
            if (index == -2) return null;
            cnt += match.charAt(index) == '(' ? 1 : -1;
            match = VimStrings.strpart(match, index + 1);
        }
        start -= 2;
        return new int[] { start, len - start - match.length() };
    }

    /** s:Ref with no more arguments: the {@code d}-th group's text; empty when there is none. */
    private static String ref(VimStrings s, String string, int d) {
        int[] startLen = refStartLen(s, string, d);
        return startLen == null ? "" : VimStrings.strpart(string, startLen[0], startLen[1]);
    }

    /** s:Count(string, '\(', '1'): the number of {@code \(} in the string. */
    private static int countOpen(VimStrings s, String string) {
        int result = 0;
        String foo = string;
        int index = s.matchend(foo, "\\\\(");
        while (index != -1) {
            result++;
            foo = VimStrings.strpart(foo, index);
            index = s.matchend(foo, "\\\\(");
        }
        return result;
    }

    /**
     * s:Resolve: the target word's back references ({@code \1}) to the source's groups replaced by those groups, and
     * the table saying which group of the word each of the source's groups became.
     */
    private static String resolve(VimStrings s, String source, String target, String output) {
        String word = target;
        int i = s.matchend(word, NOTSLASH + "\\\\\\d") - 1;
        StringBuilder table = new StringBuilder("----------");
        while (i != -2) {
            int d = word.charAt(i) - '0';
            String backref = ref(s, source, d);
            int w = countOpen(s, s.substitute(VimStrings.strpart(word, 0, i - 1), "\\\\\\\\", "", "g"));
            int b = 1, sn = d;
            while (b <= countOpen(s, s.substitute(backref, "\\\\\\\\", "", "g")) && sn < 10) {
                if (table.charAt(sn) == '-') {
                    if (w + b < 10) table.setCharAt(sn, (char) ('0' + w + b));
                    b++;
                    sn++;
                } else {
                    int[] startLen = refStartLen(s, backref, b);
                    if (startLen == null) break;
                    String refText = VimStrings.strpart(backref, startLen[0], startLen[1]);
                    backref = VimStrings.strpart(backref, 0, startLen[0]) + ":" + table.charAt(sn) + VimStrings.strpart(backref, startLen[0] + startLen[1]);
                    sn += countOpen(s, s.substitute(refText, "\\\\\\\\", "", "g"));
                }
            }
            word = VimStrings.strpart(word, 0, i - 1) + backref + VimStrings.strpart(word, i + 1);
            i = s.matchend(word, NOTSLASH + "\\\\\\d") - 1;
        }
        word = s.substitute(word, NOTSLASH + "\\zs:", "\\\\", "g");
        return switch (output) {
            case "table" -> table.toString();
            case "word" -> word;
            default -> table + word;
        };
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Target.java`, replace:

```java
 * @param regOne whether a delete with this motion always writes register 1
 * @param adjust whether Vim's exclusive-motion adjustment (spec: {@code :help exclusive-linewise}) applies
 */
record Target(Pos pos, Kind kind, Column column, boolean jump, boolean regOne, boolean adjust) {
    enum Kind { EXCLUSIVE, INCLUSIVE, LINEWISE }
```

with:

```java
 * @param regOne whether a delete with this motion always writes register 1
 * @param adjust whether Vim's exclusive-motion adjustment (spec: {@code :help exclusive-linewise}) applies
 * @param area whether an operator's text is a Visual area, as when an extension's mapping starts Visual mode for it
 *     (matchit's {@code norm! v}): a delete of several lines then stays charwise (Vim's is_VIsual)
 */
record Target(Pos pos, Kind kind, Column column, boolean jump, boolean regOne, boolean adjust, boolean area) {
    Target(Pos pos, Kind kind, Column column, boolean jump, boolean regOne, boolean adjust) { this(pos, kind, column, jump, regOne, adjust, false); }

    enum Kind { EXCLUSIVE, INCLUSIVE, LINEWISE }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Target.java`, replace:

```java
    static Target linewise(Pos pos, Column column) { return new Target(pos, Kind.LINEWISE, column, false, false, true); }

    Target asJump() { return new Target(pos, kind, column, true, regOne, adjust); }

    Target usingRegisterOne() { return new Target(pos, kind, column, jump, true, adjust); }

    Target withoutAdjust() { return new Target(pos, kind, column, jump, regOne, false); }

    Target withColumn(Column value) { return new Target(pos, kind, value, jump, regOne, adjust); }

    Target withKind(Kind value) { return new Target(pos, value, column, jump, regOne, adjust); }
}
```

with:

```java
    static Target linewise(Pos pos, Column column) { return new Target(pos, Kind.LINEWISE, column, false, false, true); }

    Target asJump() { return new Target(pos, kind, column, true, regOne, adjust, area); }

    Target usingRegisterOne() { return new Target(pos, kind, column, jump, true, adjust, area); }

    Target withoutAdjust() { return new Target(pos, kind, column, jump, regOne, false, area); }

    Target withColumn(Column value) { return new Target(pos, kind, value, jump, regOne, adjust, area); }

    Target withKind(Kind value) { return new Target(pos, value, column, jump, regOne, adjust, area); }

    Target asArea() { return new Target(pos, kind, column, jump, regOne, adjust, true); }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
        for (String k : List.of("(", ")", "{", "}", "[[", "]]", "[]", "][", "[(", "[{", "])", "]}")) table.put(k, "block");
        table.put("%", "percent");
        for (String k : List.of("'", "`")) table.put(k, "mark");
        for (String k : List.of("/", "?", "n", "N", "*", "#", "g*", "g#")) table.put(k, "search");
```

with:

```java
        for (String k : List.of("(", ")", "{", "}", "[[", "]]", "[]", "][", "[(", "[{", "])", "]}")) table.put(k, "block");
        table.put("%", "percent");
        // matchit's % and g% (its s:CleanUp opens folds with 'foldopen' holding percent).
        for (String mode : List.of("Normal", "Visual"))
            for (String way : List.of("Forward", "Backward")) table.put("<Plug>(Matchit" + mode + way + ")", "percent");
        for (String k : List.of("'", "`")) table.put(k, "mark");
        for (String k : List.of("/", "?", "n", "N", "*", "#", "g*", "g#")) table.put(k, "search");
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
            }
        }
        return new Span(start, end, kind, target.regOne(), null, false, adjusted);
    }
```

with:

```java
            }
        }
        return new Span(start, end, kind, target.regOne(), null, target.area(), adjusted);
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("joinspaces", "js", Type.BOOLEAN, true, false),
        new Def("laststatus", "ls", Type.NUMBER, 1, false),
        new Def("matchpairs", "mps", Type.STRING, "(:),{:},[:]", true),
        new Def("maxmapdepth", "mmd", Type.NUMBER, 1000, false),
```

with:

```java
        new Def("joinspaces", "js", Type.BOOLEAN, true, false),
        new Def("laststatus", "ls", Type.NUMBER, 1, false),
        new Def("matchit", "matchit", Type.BOOLEAN, false, false),
        new Def("matchpairs", "mps", Type.STRING, "(:),{:},[:]", true),
        new Def("maxmapdepth", "mmd", Type.NUMBER, 1000, false),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, replace:

```java
            map("xmap", "ia", "<Plug>InnerArgument"), map("xmap", "aa", "<Plug>OuterArgument"),
            map("omap", "ia", "<Plug>InnerArgument"), map("omap", "aa", "<Plug>OuterArgument"))),
        new Extension("highlightedyank", "machakann/vim-highlightedyank", null, Check.NONE, List.of()));

    private final Vim vim;
```

with:

```java
            map("xmap", "ia", "<Plug>InnerArgument"), map("xmap", "aa", "<Plug>OuterArgument"),
            map("omap", "ia", "<Plug>InnerArgument"), map("omap", "aa", "<Plug>OuterArgument"))),
        new Extension("highlightedyank", "machakann/vim-highlightedyank", null, Check.NONE, List.of()),
        new Extension("matchit", "chrisbra/matchit", "no_plugin_maps", Check.NONE, List.of(
            map("nmap", "%", "<Plug>(MatchitNormalForward)"), map("nmap", "g%", "<Plug>(MatchitNormalBackward)"),
            map("xmap", "%", "<Plug>(MatchitVisualForward)"), map("xmap", "g%", "<Plug>(MatchitVisualBackward)"),
            map("omap", "%", "<Plug>(MatchitOperationForward)"), map("omap", "g%", "<Plug>(MatchitOperationBackward)"),
            map("nmap", "[%", "<Plug>(MatchitNormalMultiBackward)"), map("nmap", "]%", "<Plug>(MatchitNormalMultiForward)"),
            map("xmap", "[%", "<Plug>(MatchitVisualMultiBackward)"), map("xmap", "]%", "<Plug>(MatchitVisualMultiForward)"),
            map("omap", "[%", "<Plug>(MatchitOperationMultiBackward)"), map("omap", "]%", "<Plug>(MatchitOperationMultiForward)"),
            map("xmap", "a%", "<Plug>(MatchitVisualTextObject)"))));

    private final Vim vim;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, replace:

```java
        TextobjIndent.register(commands);
        Argtextobj.register(commands);
        return commands;
    }
```

with:

```java
        TextobjIndent.register(commands);
        Argtextobj.register(commands);
        Matchit.register(commands);
        return commands;
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, replace:

```java
        def("sethandler", EXTRA | TRLBAR, Addr.NONE, "`:sethandler {key} {modes}:{vim|ide} …`: who gets a key in each mode", ExVimrc::sethandler);
        def("action", EXTRA | NEEDARG | TRLBAR, Addr.NONE, "run the Editor command {id}, as `<Action>(id)` does in a mapping", ExVimrc::action);
        def("Plug", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "`Plug 'tpope/vim-surround'`: switch on the extension Jasper emulates for the plugin; others are ignored", Extensions::plug);
        def("actionlist", EXTRA | TRLBAR, Addr.NONE, "list the Editor's command ids, or those holding [text]", ExVimrc::actionlist);
```

with:

```java
        def("sethandler", EXTRA | TRLBAR, Addr.NONE, "`:sethandler {key} {modes}:{vim|ide} …`: who gets a key in each mode", ExVimrc::sethandler);
        def("action", EXTRA | NEEDARG | TRLBAR, Addr.NONE, "run the Editor command {id}, as `<Action>(id)` does in a mapping", ExVimrc::action);
        def("packadd", BANG | EXTRA | NEEDARG | TRLBAR, Addr.NONE, "`packadd matchit`: switch on matchit (another package is not there)", Matchit::packadd);
        def("Plug", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "`Plug 'tpope/vim-surround'`: switch on the extension Jasper emulates for the plugin; others are ignored", Extensions::plug);
        def("actionlist", EXTRA | TRLBAR, Addr.NONE, "list the Editor's command ids, or those holding [text]", ExVimrc::actionlist);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    /** The text exchange's first {@code cx} marked (the plugin's b:exchange), or null. */
    private Exchange.Region exchange;
    /** The highlightedyank extension's highlight of the last yank. */
    private final HighlightedYank highlightedYank = new HighlightedYank(this);
```

with:

```java
    /** The text exchange's first {@code cx} marked (the plugin's b:exchange), or null. */
    private Exchange.Region exchange;
    /** Whether the tab's 'matchpairs' has pairs its language added. */
    private boolean languagePairs;
    /** The highlightedyank extension's highlight of the last yank. */
    private final HighlightedYank highlightedYank = new HighlightedYank(this);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    /**
     * The tab's language changed: 'commentstring' becomes its line comment ({@code //%s}, {@code <!--%s-->}), as a
     * filetype plugin sets it in Vim; a language with none keeps the value it had.
     */
    private void languageChanged() {
        if (!(area.getDocument() instanceof org.fife.ui.rsyntaxtextarea.RSyntaxDocument document)) return;
        String[] tokens = document.getLineCommentStartAndEnd(0);
```

with:

```java
    /**
     * The tab's language changed: 'commentstring' becomes its line comment ({@code //%s}, {@code <!--%s-->}), as a
     * filetype plugin sets it in Vim; a language with none keeps the value it had. The pairs a language's filetype
     * plugin adds to 'matchpairs' ({@code setlocal matchpairs+=<:>} for HTML) are added to the global value.
     */
    private void languageChanged() {
        List<String> added = MatchitLanguages.of(this).addedPairs();
        if (!added.isEmpty() || languagePairs) {
            List<String> pairs = new ArrayList<>(List.of(vim.options().string("matchpairs").split(",")));
            for (String pair : added) if (!pairs.contains(pair)) pairs.add(pair);
            options.setLocal("matchpairs", String.join(",", pairs));
            languagePairs = !added.isEmpty();
        }
        if (!(area.getDocument() instanceof org.fife.ui.rsyntaxtextarea.RSyntaxDocument document)) return;
        String[] tokens = document.getLineCommentStartAndEnd(0);
```

- [ ] **Step 5: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains matchit's motions, `<Plug>(MatchitVisualTextObject)` and `:packadd`.

- [ ] **Step 6: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExtensionsTest'
```

Expected: 12 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.vim.TypeaheadTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.ExtensionsTest' --tests 'dev.jasper.editor.vim.HighlightedYankTest' --tests 'dev.jasper.editor.vim.VimStringsTest' --tests 'dev.jasper.editor.vim.SearchPairTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 244 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 4307 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`.

- [ ] **Step 7: Check the recording (needs `vim`)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: `git status` lists only `ext-matchit.case` (`??`), unchanged from what you created: Vim with its matchit, filetype plugins and syntax agrees with every expectation.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v4c
git add plugins/editor/src/main/java/dev/jasper/editor/vim/Matchit.java plugins/editor/src/main/java/dev/jasper/editor/vim/MatchitLanguages.java plugins/editor/src/main/resources/dev/jasper/editor/vim/matchit-languages.tsv plugins/editor/src/main/java/dev/jasper/editor/vim/Target.java plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusRunner.java plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java plugins/editor/src/test/resources/vim/corpus/ext-matchit.case plugins/editor/vim-oracle.properties docs/editor-vim-reference.md
git commit -m "Add the matchit extension

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: Documentation, the spec amendments and the final check

**Files:**
- Modify: `docs/vim-divergences.md`, `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec

- [ ] **Step 1: List the differences**

In `docs/vim-divergences.md`, replace:

```markdown
- **`%` matches brackets only.** Vim also jumps between `/*` and `*/` and between `#if`, `#else` and `#endif`.
```

with:

```markdown
- **`%` matches brackets only.** Vim also jumps between `/*` and `*/` and between `#if`, `#else` and `#endif`;
  with matchit switched on (`set matchit`, `packadd matchit`) Jasper does too.
```

In `docs/vim-divergences.md`, replace:

```markdown
  checks the result against Vim. `\%V`, `\%#`, the `\%23l`-style position atoms, `\@` look-around, `\z(` and
  equivalence classes (`[[=a=]]`) report `E869`.
```

with:

```markdown
  checks the result against Vim. `\%V`, `\%#`, the `\%23l` and `\%23v` position atoms, `\z(` and equivalence
  classes (`[[=a=]]`) report `E869`. Columns (`\%23c`) count characters where Vim counts bytes, which differs only
  in lines with characters beyond ASCII, and a repeat inside a look-behind (`\(a*\)\@<=`) reaches back at most 1000
  characters.
```

Append to `docs/vim-divergences.md`:

```markdown
- **matchit** takes each language's `b:match_words`, `b:match_ignorecase`, `b:match_skip` and 'matchpairs'
  additions from a table Vim 9.1's filetype plugins made (`matchit-languages.tsv`), by the tab's language; there are no
  buffer variables to change them. A skip naming syntax groups reads the tab's tokens as `Comment`, `String`,
  `Character` and `Regex`; a skip that is another Vim expression (the shell's, Ruby's, PHP's) skips comments and
  strings. `:MatchEnable`, `:MatchDisable` and `:MatchDebug` are not there, and `g:no_plugin_maps` counts only when it
  is not 0. 'matchpairs' gains the pairs the language's filetype plugin adds (`<:>` for HTML) whether matchit is on
  or not.
```

- [ ] **Step 2: Describe matchit in the Editor guide**

In `docs/editor.md`, replace:

```markdown
- Not yet: matchit and sneak. They follow in a later release.
```

with:

```markdown
- `set matchit` (or `packadd matchit`) makes `%` go between the words of a group as the tab's language defines them:
  `if`, `else` and `fi` in shell scripts, a tag and its end tag in HTML and XML, `\begin` and `\end` in TeX, `#if`
  and `#endif` and `/*` and `*/` everywhere; `g%` goes the other way, `[%` and `]%` to the start and end of the group
  around the cursor, and Visual `a%` takes it. Matches in comments and strings are passed over.
- Not yet: sneak. It follows in a later release.
```

In `docs/editor.md`, replace:

```markdown
  ('incsearch'); `<Esc>` puts everything back. Patterns are Vim's (`\v`, `\<`, `\zs`, `\{-}` and the rest); the few
  items Jasper does not translate say so (`E869`).
```

with:

```markdown
  ('incsearch'); `<Esc>` puts everything back. Patterns are Vim's (`\v`, `\<`, `\zs`, `\{-}`, look-arounds such as
  `\(foo\)\@<=bar`, columns such as `\%>8c`, and the rest); the few items Jasper does not translate say so (`E869`).
```

- [ ] **Step 3: Add the GUI checks for the user**

Append to `docs/editor-verification.md`:

```markdown

## Vim mode (plan V4c)

- [ ] 104. With `packadd matchit` in the vimrc, `%` on `<div>` in an HTML file goes to its `</div>`, and back.
- [ ] 105. In a shell script, `%` goes from `if` to `elif`, `else` and `fi` in turn; `]%` inside the block goes to `fi`.
- [ ] 106. In a C file, `%` on `(` passes a `)` inside a string or a comment.
- [ ] 107. `d%` on `#if` deletes up to and including `#else`; `u` undoes it in one step.
- [ ] 108. Visual `a%` on a word inside parentheses selects the parentheses and what is between them.
- [ ] 109. `/\(foo\)\@<=bar` finds the `bar` of `foobar` and not a `bar` alone.
```

- [ ] **Step 4: Record the state**

In `docs/STATUS.md`, replace:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V4b** (branch `claude/vim-v4b`, plan
```

with:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V4c** (branch `claude/vim-v4c`, plan
  `docs/superpowers/plans/2026-09-29-jasper-editor-vim-v4c.md`, written 2026-09-29): matchit, ported from Vim 9.1's matchit.vim (`%`, `g%`,
  `[%`, `]%`, Visual `a%`; `set matchit`, `packadd matchit`), with the languages' `b:match_*` from Vim's filetype
  plugins (`matchit-languages.tsv`); look-arounds and columns in Vim patterns (`\@<=`, `\%23c`), Vim script's string
  functions (`VimStrings`) and searchpair(). The corpus gains `filetype:` (Vim loads the filetype plugin and syntax).
  Every file in the plan was compiled and run against Vim 9.1 before the plan was written: 2164 corpus cases
  (98 new), with and without a final line break.
  - Not done: executing the plan; GUI checks 104–109 of the [checklist](editor-verification.md) (user-run); plan V4d
    (sneak); push.
- **Editor Vim mode, plan V4b** (branch `claude/vim-v4b`, plan
```

- [ ] **Step 5: Amend the spec**

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
Plan V4b (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v4b.md`) amends sections 6.3, 8, 11.1 and 13, as marked **Amended (V4b)**.
```

with:

```markdown
Plan V4b (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v4b.md`) amends sections 6.3, 8, 11.1 and 13, as marked **Amended (V4b)**. Plan V4c (`docs/superpowers/plans/2026-09-29-jasper-editor-vim-v4c.md`) amends sections 3.7, 8, 11.1 and 13, as marked **Amended (V4c)**.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
  - Not supported (error): `\%V`, `\%#`, `\%23l`-style position atoms, `\@` look-around (Vim's syntax), `\z(`, equivalence classes `[[=a=]]`.
```

with:

```markdown
  - Not supported (error): `\%V`, `\%#`, `\%23l`-style position atoms, `\@` look-around (Vim's syntax), `\z(`, equivalence classes `[[=a=]]`. **Amended (V4c):** `\@=`, `\@!`, `\@>`, `\@<=`, `\@<!` (with or without a byte limit) and `\%23c`, `\%<23c`, `\%>23c` are supported, for matchit; columns count characters, and repeats inside a look-behind reach back 1000 characters.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
**Amended (V4b):** plan V4b delivers paragraph-motion, textobj-entire, textobj-indent, argtextobj and highlightedyank; matchit and sneak come with V4c. The switches are IdeaVim's names (`vim-paragraph-motion`, `textobj-entire`, `textobj-indent`, `argtextobj`, `highlightedyank`); plugins without `<Plug>` names get IdeaVim's (`<Plug>(ParagraphNextMotion)`, `<Plug>(ParagraphPrevMotion)`, `<Plug>textobj-indent-ai`/`aI`/`ii`/`iI`, `<Plug>InnerArgument`, `<Plug>OuterArgument`), textobj-entire keeps `<Plug>(textobj-entire-a)` and `-i`. Their objects select in Visual mode as the plugins' mappings do, and the pending operator acts on that area. argtextobj is the plugin's: parentheses only, `g:argumentobject_force_toplevel` rather than `g:argtextobj_pairs`, counts ignored. highlightedyank's duration defaults to the plugin's 1000 ms, not 300; it reads `g:highlightedyank_highlight_in_visual` and `g:highlightedyank_max_lines` too, and has no commands.
```

with:

```markdown
**Amended (V4b):** plan V4b delivers paragraph-motion, textobj-entire, textobj-indent, argtextobj and highlightedyank; matchit and sneak come with V4c. The switches are IdeaVim's names (`vim-paragraph-motion`, `textobj-entire`, `textobj-indent`, `argtextobj`, `highlightedyank`); plugins without `<Plug>` names get IdeaVim's (`<Plug>(ParagraphNextMotion)`, `<Plug>(ParagraphPrevMotion)`, `<Plug>textobj-indent-ai`/`aI`/`ii`/`iI`, `<Plug>InnerArgument`, `<Plug>OuterArgument`), textobj-entire keeps `<Plug>(textobj-entire-a)` and `-i`. Their objects select in Visual mode as the plugins' mappings do, and the pending operator acts on that area. argtextobj is the plugin's: parentheses only, `g:argumentobject_force_toplevel` rather than `g:argtextobj_pairs`, counts ignored. highlightedyank's duration defaults to the plugin's 1000 ms, not 300; it reads `g:highlightedyank_highlight_in_visual` and `g:highlightedyank_max_lines` too, and has no commands.

**Amended (V4c):** plan V4c delivers matchit (`set matchit`, `packadd matchit`, `Plug 'chrisbra/matchit'`), ported from Vim 9.1's matchit.vim with its `<Plug>(Matchit…)` names and default mappings; sneak comes with V4d. The "language table in the plan" is `matchit-languages.tsv`, what Vim 9.1's filetype plugins set for each RSyntaxTextArea style they know (`b:match_words`, `b:match_ignorecase`, `b:match_skip`, and the pairs they add to 'matchpairs', which Jasper adds too); a skip naming syntax groups reads the tab's tokens, another expression skips comments and strings. `:MatchEnable`, `:MatchDisable` and `:MatchDebug` are not there.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- **Recording:** `./gradlew :jasper-plugin-editor:recordVimCorpus` (not part of `check`; needs `vim` on `PATH`, or `-PvimBinary=…`). For each case it runs `vim -u NONE -i NONE -N -Es` with a generated script that sets options and the buffer, places the cursor, runs the vimrc lines, feeds the keys with `feedkeys(keys, 'xt')`, and writes text, cursor, mode, registers, marks and messages as JSON; the task rewrites everything below each case's `---` line. For `ext:` cases it first clones each extension's upstream repository at the commit pinned in `plugins/editor/vim-oracle.properties` into `build/vim-oracle/` and adds it to `runtimepath`. `-Pcase=<pattern>` records a subset. **Amended (V4a):** the pins are `name = git URL commit` lines in `plugins/editor/vim-oracle.properties`, and every `ext:` case also loads tpope/vim-repeat, which the plugins use for `.`; the recorder finds the file in the nearest folder above the corpus that has one and clones into its `build/vim-oracle`. `ext:` names extensions by their switches (`ext: surround`); the recorder clears Vim's own mappings first, and there is no `vimrc:` field. **Amended (V4b):** a pin may name, after its commit, the pins it needs loaded first (textobj-entire needs kana/vim-textobj-user); plugins load `plugin/**/*.vim`; `let:` lines run `:let` before they load (`let: g:indent_object_except_first_level = 0`).
```

with:

```markdown
- **Recording:** `./gradlew :jasper-plugin-editor:recordVimCorpus` (not part of `check`; needs `vim` on `PATH`, or `-PvimBinary=…`). For each case it runs `vim -u NONE -i NONE -N -Es` with a generated script that sets options and the buffer, places the cursor, runs the vimrc lines, feeds the keys with `feedkeys(keys, 'xt')`, and writes text, cursor, mode, registers, marks and messages as JSON; the task rewrites everything below each case's `---` line. For `ext:` cases it first clones each extension's upstream repository at the commit pinned in `plugins/editor/vim-oracle.properties` into `build/vim-oracle/` and adds it to `runtimepath`. `-Pcase=<pattern>` records a subset. **Amended (V4a):** the pins are `name = git URL commit` lines in `plugins/editor/vim-oracle.properties`, and every `ext:` case also loads tpope/vim-repeat, which the plugins use for `.`; the recorder finds the file in the nearest folder above the corpus that has one and clones into its `build/vim-oracle`. `ext:` names extensions by their switches (`ext: surround`); the recorder clears Vim's own mappings first, and there is no `vimrc:` field. **Amended (V4b):** a pin may name, after its commit, the pins it needs loaded first (textobj-entire needs kana/vim-textobj-user); plugins load `plugin/**/*.vim`; `let:` lines run `:let` before they load (`let: g:indent_object_except_first_level = 0`). **Amended (V4c):** `filetype: html` gives the tab that language and has Vim run `filetype plugin on`, `syntax on` and `set filetype=html`; a pin `name = packadd` loads the plugin that comes with Vim (`packadd matchit`).
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
 plan V4c is matchit (with `packadd` and the languages' `b:match_words`) and sneak.
```

with:

```markdown
 plan V4c is matchit (with `packadd` and the languages' `b:match_words`) and sneak. **Amended (V4c):** split again by the user on 2026-09-29: plan V4c is matchit, plan V4d sneak.
```

- [ ] **Step 6: Run everything**

Run:

```bash
./gradlew check
```

Expected: BUILD SUCCESSFUL (7,031 tests).

`TerminalAppIntegrationTest`'s "reflow" case is known to fail now and then under the full check, unrelated to the Editor; if it does, run `./gradlew check` again.

- [ ] **Step 7: Check source hygiene**

Run the snippet in `AGENTS.md` ("Source hygiene"). Expected: no output.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v4c
git add docs
git commit -m "Document Vim mode V4c: matchit

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
