# Editor Vim mode, plan V3a: macros and mappings — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Macros and key mappings for the Editor's Vim mode: `q{reg}` … `q` records typed keys, `@{reg}`, `@@`, `{n}@` and `@:` run them, and the Vim bar shows `recording @q`; the mapping commands typed on the command line (`:map`, `:nmap`, `:vmap`, `:xmap`, `:omap`, `:imap`, `:cmap`, `:map!`, their `noremap`, `unmap` and `mapclear` forms, and the listings) with Vim's recursion, `E223`, 'timeout' and 'timeoutlen', `<nowait>`, `<unique>`, `<Plug>`, `<Nop>` and `<Leader>`.

**Architecture:** Every key now waits in a `Typeahead` (one per tab), ported from Vim's getchar.c (vgetorpeek, handle_mapping, ins_typebuf, flush_buffers): typed keys join its end, a mapping's right-hand side or a macro's keys go in front, and a key sequence that starts a longer mapping waits on a Swing timer. `Mappings` is the table (Vim's maphash, with its listing order), `ExMaps` the commands and their notation (do_map, showmap, replace_termcodes), `Macros` recording and execution (do_record, stuff_yank, do_execreg), and `KeyCodes` Vim's encoding of keys in a register's text (`<80>kb` for `<BS>`). A failing command drops the keys that were not typed, which ends a mapping or macro, and the keys of one mapping or macro change the text as one undo step, as Vim syncs undo only on typed keys.

**Tech Stack:** Java 25 (JBR), Swing, RSyntaxTextArea 3.6.0, JUnit 6 and AssertJ, Vim 9 (only for `recordVimCorpus`).

**Spec:** `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md` (plan V3 of section 13, split by the user into V3a and V3b on 2026-09-28; sections 2.3, 4.3 and 7.2). Plans V1a–V2b are merged; their headers describe the engine and the Ex command line.

**Status:** Written 2026-09-28 on branch `claude/vim-v3a` (worktree `.worktrees/vim-v3a`, from `main` at bbef1dd7). Every source file, test and corpus case below was compiled and run before the plan was written: against Vim 9.1 all 1714 corpus cases (133 new, in `macros.case` and `mappings.case`; two of them divergent) passed, and re-recording the whole corpus with the recorder's new `mapclear` line changed no expectation. Applied to a fresh checkout of `bbef1dd7` exactly as written, `./gradlew check` passed (4,311 tests, 0 failures, 0 errors, 5 skipped).

## Deviations from the spec (approved scope, adjusted in this plan)

Task 5 writes these into the spec as **Amended (V3a)**.

- **V3 is split.** This plan (V3a) delivers section 4.3 and the mapping commands of section 7.2 typed on the command line, with 'timeout' and 'timeoutlen' (section 2.3). Plan V3b brings the vimrc (section 7.1), `sethandler` lines, `let mapleader`, `<Action>(id)`, `:action` and `:actionlist`.
- **`<Leader>` and `<LocalLeader>` are a backslash**, Vim's default, until V3b reads `let mapleader`.
- **`<silent>` is accepted and changes nothing:** Jasper never echoes the command line a mapping types. `<special>` is accepted too. `<buffer>`, `<expr>` and `<script>` are refused with `Not supported in Jasper: <expr>`.
- **`:smap`, `:lmap`, `:tmap` and their forms fail with E319** (no Select mode, Lang-Arg mode or terminal); `:vmap` and `:map` still carry Select mode, so listings show `v` as Vim's do.
- **'maxmapdepth'** (`mmd`, default 1000), Vim's option behind E223, joins `:set`.
- **The typeahead is per tab.** A mapping or macro that selects another tab goes on in the tab it started in; Vim's typeahead is global.
- **`q:`, `q/` and `q?` fail:** Jasper has no command-line window.
- **CTRL-C stops a mapping or macro that runs on**, as in Vim; Jasper looks for it among the window's pending key events every 1000 keys, since the keys run on the event thread.

## Global Constraints

- Java 25 on the JetBrains Runtime; always `./gradlew`, never a system Gradle. Work in `.worktrees/vim-v3a` on `claude/vim-v3a`; check `git branch --show-current` before every commit (other sessions switch branches in the main checkout). Never commit on `main`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.
- The Editor plugin compiles against the SDK only (`verifyPluginArchitecture`, a root task). `dev.jasper.editor.vim` may use `vim.regex`, RSyntaxTextArea, Swing and the JDK; `dev.jasper.editor.vim.ui` adds `dev.jasper.editor.vim`. No SDK change.
- No interface without two real implementations; this plan adds none.
- Source hygiene: never put raw control, private-use or unpaired surrogate characters in `.java` files; use escapes. No run of three backticks may appear in any file this plan embeds (the docs link check strips fenced blocks lazily).
- **Do not launch the GUI.** GUI checks go in `docs/editor-verification.md` for the user.
- Engine code runs on the event thread in the app. Tests that type through a `VimEditor` run inside `SwingUtilities.invokeAndWait`.
- The corpus is the authority on Vim behaviour; mark a case `divergent:` only for a documented, deliberate difference.
- `vim` must be on `PATH` for `recordVimCorpus` (the dev Mac has Vim 9.1 at `/usr/bin/vim`). `check` never runs Vim.

## File structure

New, `plugins/editor/src/main/java/dev/jasper/editor/vim/`:

| File | Responsibility |
|---|---|
| `KeyCodes.java` | keys as a register's text, as Vim records them (`<C-a>` is U+0001, `<BS>` is U+0080 `kb`), and how `:registers` shows that text |
| `Typeahead.java` | the keys waiting to run: mappings resolve here, 'timeoutlen' on a Swing timer, `:normal`'s keys, failures and CTRL-C |
| `Mappings.java` | the mapping table, in Vim's order for trying and listing mappings |
| `ExMaps.java` | `:map` and its forms: arguments, notation, the listing, `:unmap`, `:mapclear` |
| `Macros.java` | `q` and `@`: recording, `@@`, `@:`, `@.`, and Insert mode's `<C-o>` |

Changed there: `KeyInput` (`<NL>`, `<Nul>`, `<Char-N>`, `<Plug>`), `ExInfo` (`:registers` shows Vim's key codes), `VimEditor` (keys go through the typeahead; mapping modes; the undo step a mapping shares; `modeText`), `Machine`, `InsertMode`, `CommandLine` (which keys may be mapped), `Vim` (`mappings()`, `macros()`), `Ex` (the commands; errors drop mapped keys; KeyTyped), `ExLines` (`:normal` through the typeahead), `Options` ('maxmapdepth', 'timeout', 'timeoutlen'), `Registers` (`record`), `VimCommands`, and `ui/VimBar` (`recording @q`).
Tests: `KeyCodesTest`, `TypeaheadTest`, `ExMapsTest`, `MacrosTest` (new); `KeyInputTest`, `ui/VimBarTest`, `CorpusRunner`, `RecordCorpus`. Corpus: `mappings.case`, `macros.case`.
Docs: `docs/vim-divergences.md`, `docs/editor-vim-reference.md` (generated), `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec.

---

### Task 1: Keys as a register's text

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyCodes.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/KeyCodesTest.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/ExInfo.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/KeyInputTest.java`

**Interfaces:**
- Produces: `KeyCodes.text(List<KeyInput>) -> String` (a macro's keys as register text), `KeyCodes.keys(String) -> List<KeyInput>` (register text as keys), `KeyCodes.shown(String) -> List<String>` and `KeyCodes.cells(String) -> int` (how `:registers` shows the text); `KeyInput` reads `<NL>`, `<Nul>`, `<Char-N>` and `<Plug>` (`KeyInput.named("Plug")`).

- [ ] **Step 1: Write the failing tests**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/KeyCodesTest.java`:

```java
package dev.jasper.editor.vim;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Keys as a register's text, as Vim records them (spec 4.3). */
class KeyCodesTest {
    @Test void charactersAndControlKeysAreThemselves() {
        assertThat(KeyCodes.text(KeyInput.parse("ab<C-a><Esc><CR><Tab><C-j>é"))).isEqualTo("ab\u0001\u001b\r\t\né");
    }

    @Test void namedKeysAreVimsSpecialSequences() {
        assertThat(KeyCodes.text(KeyInput.parse("<BS><Left><End><F10><S-Tab>"))).isEqualTo("\u0080kb\u0080kl\u0080@7\u0080k;\u0080kB");
        assertThat(KeyCodes.text(KeyInput.parse("<S-Up><C-Left>"))).isEqualTo("\u0080ý\u0004\u0080ýU");
        assertThat(KeyCodes.text(KeyInput.parse("<D-s><C-Up>"))).as("other modified keys carry their modifiers")
            .isEqualTo("\u0080ü\u0080s\u0080ü\u0004\u0080ku");
    }

    @Test void theTextReadsBackAsTheSameKeys() {
        for (String notation : List.of("dw<Esc>", "i1<BS>2<Left><Esc>", "<S-Up><C-Left><D-s><C-Up><M-x><F12><S-F3><Plug>", "<C-@>x", "a<C-j>b"))
            assertThat(KeyCodes.keys(KeyCodes.text(KeyInput.parse(notation)))).as(notation).isEqualTo(KeyInput.parse(notation));
        assertThat(KeyCodes.keys("\u007f")).as("DEL").containsExactly(KeyInput.named("Del"));
        assertThat(KeyCodes.keys("\u0080")).as("a lone special byte").containsExactly(KeyInput.of(0x80));
        assertThat(KeyCodes.keys("\u0080zz")).as("an unknown code").containsExactly(KeyInput.of(0x80), KeyInput.of('z'), KeyInput.of('z'));
    }

    @Test void registersShowTheBytesAsVimDoes() {
        assertThat(String.join("", KeyCodes.shown("i\u0080kb\u001b\u0080ý\u0004é"))).isEqualTo("i<80>kb^[<80><fd>^Dé");
        assertThat(KeyCodes.cells("<80>")).isEqualTo(4);
        assertThat(KeyCodes.cells("日")).isEqualTo(2);
    }
}
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/KeyInputTest.java`, replace:

```java
            KeyInput.named("Esc"), KeyInput.named("Esc"), KeyInput.named("CR"), KeyInput.named("CR"), KeyInput.named("BS"),
            KeyInput.named("Del"), KeyInput.of(' '), KeyInput.of('<'), KeyInput.of('|'), KeyInput.of('\\'), KeyInput.named("F12"));
    }
```

with:

```java
            KeyInput.named("Esc"), KeyInput.named("Esc"), KeyInput.named("CR"), KeyInput.named("CR"), KeyInput.named("BS"),
            KeyInput.named("Del"), KeyInput.of(' '), KeyInput.of('<'), KeyInput.of('|'), KeyInput.of('\\'), KeyInput.named("F12"));
    }

    @Test void readsTheNotationMappingsUse() {
        assertThat(KeyInput.parse("<NL><nul><Char-65><char-0x42><Char-0103><Plug>")).containsExactly(
            new KeyInput("j", KeyInput.CTRL), new KeyInput("@", KeyInput.CTRL), KeyInput.of('A'), KeyInput.of('B'), KeyInput.of('C'),
            KeyInput.named("Plug"));
        assertThat(KeyInput.parse("<Char-x>")).as("not a number").hasSize(8);
        assertThat(KeyInput.named("Plug").notation()).isEqualTo("<Plug>");
    }
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.vim.KeyInputTest'
```

Expected: FAIL: the tests do not compile (`KeyCodes` does not exist).

- [ ] **Step 3: Write `KeyCodes`, read the new notation, and show the codes in `:registers`**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyCodes.java`:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keys as Vim keeps them in a register's text (spec 4.3): {@code q} records keys into a register as text, and
 * {@code @} reads the text back as keys. A character is itself; CTRL with a letter is its control character
 * ({@code <C-a>} is U+0001, {@code <Esc>} U+001B, {@code <CR>} U+000D, {@code <C-j>} a line break); other named keys
 * are Vim's K_SPECIAL sequences, with U+0080 standing for the 0x80 byte ({@code <BS>} is U+0080 {@code kb}), so
 * {@code :registers} shows a macro as Vim does ({@code <80>kb}).
 */
final class KeyCodes {
    private KeyCodes() { }

    private static final char SPECIAL = (char) 0x80, MODIFIER = (char) 0xFC, EXTRA = (char) 0xFD;
    /** Vim's MOD_MASK_* bits. */
    private static final int SHIFT = 0x02, CTRL = 0x04, ALT = 0x08, CMD = 0x80;

    /** Each key with its own code (keymap.h's termcap names and KE_ values), by notation. */
    private static final Map<String, String> CODES = new HashMap<>();
    private static final Map<String, KeyInput> KEYS = new HashMap<>();

    private static void code(String notation, String code) {
        CODES.put(notation, code);
        KEYS.put(code, KeyInput.parse(notation).getFirst());
    }

    private static void extra(String notation, int value) { code(notation, String.valueOf(EXTRA) + (char) value); }

    static {
        code("<BS>", "kb");
        code("<Del>", "kD");
        code("<Insert>", "kI");
        code("<Home>", "kh");
        code("<End>", "@7");
        code("<PageUp>", "kP");
        code("<PageDown>", "kN");
        code("<Up>", "ku");
        code("<Down>", "kd");
        code("<Left>", "kl");
        code("<Right>", "kr");
        for (int f = 1; f <= 9; f++) code("<F" + f + ">", "k" + f);
        code("<F10>", "k;");
        code("<F11>", "F1");
        code("<F12>", "F2");
        code("<S-Tab>", "kB");
        code("<S-Left>", "#4");
        code("<S-Right>", "%i");
        code("<S-Home>", "#2");
        code("<S-End>", "*7");
        extra("<S-Up>", 4);
        extra("<S-Down>", 5);
        for (int f = 1; f <= 12; f++) extra("<S-F" + f + ">", 5 + f);
        extra("<C-Left>", 85);
        extra("<C-Right>", 86);
        extra("<C-Home>", 87);
        extra("<C-End>", 88);
        extra("<Plug>", 83);
        extra("<S-BS>", 105);
    }

    /**
     * @param keys keys, as typed
     * @return them as a register's text
     */
    static String text(List<KeyInput> keys) {
        StringBuilder out = new StringBuilder();
        for (KeyInput key : keys) out.append(text(key));
        return out.toString();
    }

    private static String text(KeyInput key) {
        String code = CODES.get(key.notation());
        if (code != null) return SPECIAL + code;
        int modifiers = key.modifiers();
        if (key.isChar()) {
            if (modifiers == 0) return key.key();
            if (modifiers == KeyInput.CTRL) {
                char c = key.key().charAt(0);
                if (c == '?') return String.valueOf((char) 0x7f);
                if (c == '@' || c >= 'a' && c <= 'z' || c >= '[' && c <= '_') return String.valueOf((char) (Character.toUpperCase(c) - '@'));
            }
        } else if (modifiers == 0) {
            switch (key.key()) {
                case "Esc" -> { return "\033"; }
                case "CR" -> { return "\r"; }
                case "Tab" -> { return "\t"; }
                default -> { }
            }
        }
        // Any other key with modifiers: K_SPECIAL KS_MODIFIER {mask} and the key without them.
        int mask = ((modifiers & KeyInput.SHIFT) != 0 ? SHIFT : 0) | ((modifiers & KeyInput.CTRL) != 0 ? CTRL : 0)
            | ((modifiers & KeyInput.ALT) != 0 ? ALT : 0) | ((modifiers & KeyInput.META) != 0 ? CMD : 0);
        return "" + SPECIAL + MODIFIER + (char) mask + text(new KeyInput(key.key(), 0));
    }

    /**
     * @param text a register's text
     * @return the keys it holds
     */
    static List<KeyInput> keys(String text) {
        List<KeyInput> keys = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int[] end = { i };
            keys.add(key(text, end));
            i = end[0];
        }
        return keys;
    }

    /** The key at {@code at[0]}, which moves past it. */
    private static KeyInput key(String text, int[] at) {
        int i = at[0];
        char c = text.charAt(i);
        if (c == SPECIAL && i + 2 < text.length() && text.charAt(i + 1) == MODIFIER && i + 3 < text.length()) {
            int mask = text.charAt(i + 2);
            at[0] = i + 3;
            KeyInput base = key(text, at);
            int modifiers = ((mask & SHIFT) != 0 ? KeyInput.SHIFT : 0) | ((mask & CTRL) != 0 ? KeyInput.CTRL : 0)
                | ((mask & ALT) != 0 ? KeyInput.ALT : 0) | ((mask & CMD) != 0 ? KeyInput.META : 0);
            return KeyInput.normalize(base.key(), base.modifiers() | modifiers);
        }
        if (c == SPECIAL && i + 2 < text.length()) {
            KeyInput named = KEYS.get(text.substring(i + 1, i + 3));
            if (named != null) {
                at[0] = i + 3;
                return named;
            }
        }
        int cp = text.codePointAt(i);
        at[0] = i + Character.charCount(cp);
        if (cp == 0) return new KeyInput("@", KeyInput.CTRL);
        if (cp == '\n') return new KeyInput("j", KeyInput.CTRL);
        if (cp < 0x20) return KeyInput.ctrl((char) Character.toLowerCase(cp + '@'));
        if (cp == 0x7f) return KeyInput.named("Del");
        return KeyInput.of(cp);
    }

    /**
     * How Vim shows a register's text (msg_outtrans on its bytes): control characters as {@code ^X}, and the bytes
     * of a K_SPECIAL sequence that are not ASCII as {@code <80>}.
     *
     * @return one piece for each code point of the text
     */
    static List<String> shown(String text) {
        List<String> pieces = new ArrayList<>();
        int special = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            boolean inSpecial = special > 0;
            special = cp == SPECIAL ? 2 : Math.max(0, special - 1);
            if (cp < 0x20) pieces.add("^" + (char) (cp + '@'));
            else if (cp == 0x7f) pieces.add("^?");
            else if (cp >= 0x80 && cp < 0xa0 || inSpecial && cp >= 0x80 && cp <= 0xff) pieces.add(String.format("<%02x>", cp));
            else pieces.add(new String(Character.toChars(cp)));
        }
        return pieces;
    }

    /** @return the cells a piece from {@link #shown} takes */
    static int cells(String piece) {
        return piece.codePointCount(0, piece.length()) == 1 ? Text.width(piece.codePointAt(0)) : piece.length();
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java`, replace:

```java
    /** The named keys, in their canonical spelling. */
    public static final List<String> NAMES = List.of("Esc", "CR", "Tab", "BS", "Del", "Insert", "Home", "End", "PageUp", "PageDown",
        "Up", "Down", "Left", "Right", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12");

    /** Checks the key is one code point or a known name. */
```

with:

```java
    /** The named keys, in their canonical spelling. */
    public static final List<String> NAMES = List.of("Esc", "CR", "Tab", "BS", "Del", "Insert", "Home", "End", "PageUp", "PageDown",
        "Up", "Down", "Left", "Right", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "Plug");

    /** Checks the key is one code point or a known name. */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java`, replace:

```java
        }
        String name = rest.toLowerCase(Locale.ROOT);
        String key = switch (name) {
            case "esc", "escape" -> "Esc";
```

with:

```java
        }
        String name = rest.toLowerCase(Locale.ROOT);
        if (modifiers == 0) {
            switch (name) {
                case "nl", "newline", "linefeed", "lf" -> { return new KeyInput("j", CTRL); }
                case "nul" -> { return new KeyInput("@", CTRL); }
                default -> { }
            }
            if (name.startsWith("char-")) return character(name.substring(5));
        }
        String key = switch (name) {
            case "esc", "escape" -> "Esc";
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java`, replace:

```java
            case "bar" -> "|";
            case "bslash" -> "\\";
            default -> {
                if (name.matches("f([1-9]|1[0-2])")) yield "F" + name.substring(1);
```

with:

```java
            case "bar" -> "|";
            case "bslash" -> "\\";
            case "plug" -> "Plug";
            default -> {
                if (name.matches("f([1-9]|1[0-2])")) yield "F" + name.substring(1);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java`, replace:

```java
        };
        return key == null ? null : normalize(key, modifiers);
    }
```

with:

```java
        };
        return key == null ? null : normalize(key, modifiers);
    }

    /** {@code <Char-65>}, {@code <Char-0x41>}, {@code <Char-0101>}: a character by its code, or null. */
    private static KeyInput character(String number) {
        try {
            int code = number.startsWith("0x") ? Integer.parseInt(number.substring(2), 16)
                : number.length() > 1 && number.startsWith("0") ? Integer.parseInt(number.substring(1), 8) : Integer.parseInt(number);
            return code > 0 && Character.isValidCodePoint(code) ? of(code) : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExInfo.java`, replace:

```java
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
```

with:

```java
        StringBuilder out = new StringBuilder();
        int len = 0;
        for (String shown : KeyCodes.shown(text)) {
            len += KeyCodes.cells(shown);
            if (strict ? len >= room : len > room) break;
            out.append(shown);
        }
        return out.toString();
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExInfo.java`, replace:

```java
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
```

with:

```java
                    n -= 2;
                }
                for (String shown : KeyCodes.shown(lines.get(j))) {
                    n -= KeyCodes.cells(shown);
                    if (n < 0) break;
                    line.append(shown);
                }
            }
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.vim.KeyInputTest'
```

Expected: 12 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1585 tests pass: the corpus is unchanged.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/vim-v3a
git add plugins/editor/src/main/java/dev/jasper/editor/vim/KeyCodes.java plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java plugins/editor/src/main/java/dev/jasper/editor/vim/ExInfo.java plugins/editor/src/test/java/dev/jasper/editor/vim/KeyCodesTest.java plugins/editor/src/test/java/dev/jasper/editor/vim/KeyInputTest.java
git commit -m "Keep keys as a register's text, as Vim records them

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: The typeahead: keys wait, and mappings apply

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/Mappings.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Typeahead.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/TypeaheadTest.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/InsertMode.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/CommandLine.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/ExLines.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`

**Interfaces:**
- Consumes: `KeyCodes.keys` (Task 1) for `:normal`'s argument.
- Produces: `Mappings` with `NORMAL VISUAL SELECT OP_PENDING INSERT CMDLINE`, `MAP`, `MAP_BANG`, `Mapping` (`modes`, `lhs`, `rhs`, `noremap`, `nowait`, `order`), `all()` (listing order), `starting(state, key)`, `add(modes, lhs, rhs, noremap, nowait)`, `remove(m)`, `clear(modes)`, `isEmpty()`, `firstByte(key)`; `Typeahead` with `Remap {YES, NONE, SKIP}`, `typed(key)`, `insert(keys, remap)`, `flush()`, `clear()`, `waiting()`, `timing()`, `keyTyped()`, `mapped()`, `timeout()`, `execute(keys, remap)`, `interruptCheck(check)`, `BREAK_CHECK`; `VimEditor.typeahead()`, `step(key)`, `mapState(key)`, `timeoutNow()`, `mappingFailed(failure)`, `beginUndoGroup()`; `Vim.mappings()`; `Machine.mappable()`, `counting()`; `InsertMode.readingArgument()`, `CommandLine.readingArgument()`; options 'maxmapdepth', 'timeout', 'timeoutlen'.

`VimEditor.feed` now hands the key to the typeahead, which runs each key through `step` (the old body of `feed`). Nothing defines a mapping yet except `Mappings.add`, which the test uses; Task 3 adds the commands.

- [ ] **Step 1: Write the failing test**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/TypeaheadTest.java`:

```java
package dev.jasper.editor.vim;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Keys waiting in the typeahead, mapped before they run (spec 2.3 and 7.2), with mappings added to the table directly. */
class TypeaheadTest {
    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private static VimEditor editor(String text) {
        var area = new VimTextArea();
        area.setText(text);
        area.setCaretPosition(0);
        return new VimEditor(new Vim(new VimHost(r -> Optional.empty(), (r, t) -> { })), area, EditorDefaults.VIM);
    }

    private static void map(VimEditor editor, int modes, String lhs, String rhs, boolean noremap, boolean nowait) {
        editor.vim().mappings().add(modes, KeyInput.parse(lhs), KeyInput.parse(rhs), noremap, nowait);
    }

    private static void nmap(VimEditor editor, String lhs, String rhs) { map(editor, Mappings.NORMAL, lhs, rhs, false, false); }

    @Test void aMappingRunsItsRightHandSideMappedAgain() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\nthree\n");
            nmap(editor, "x", "dd");
            nmap(editor, "y", "x");
            map(editor, Mappings.NORMAL, "z", "x", true, false);
            type(editor, "y");
            assertThat(editor.buffer().line(0)).as("y is x is dd").isEqualTo("two");
            type(editor, "z");
            assertThat(editor.buffer().line(0)).as("noremap: z is the x command").isEqualTo("wo");
            type(editor, "2x");
            assertThat(editor.buffer().count()).as("a count applies to the first command").isEqualTo(1);
        });
    }

    @Test void mappingsThatMapEachOtherFailWithE223() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\n");
            nmap(editor, "x", "y");
            nmap(editor, "y", "x");
            int bells = editor.bells();
            type(editor, "xj");
            assertThat(editor.messageLog()).contains("E223: Recursive mapping");
            assertThat(editor.bells()).isEqualTo(bells + 1);
            assertThat(editor.cursor().line).as("the typed j still runs").isEqualTo(1);
        });
    }

    @Test void aFailingCommandDropsTheRestOfTheMappingButNotTypedKeys() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\n");
            nmap(editor, "x", "jjdd");
            type(editor, "xx");
            assertThat(editor.buffer().count()).as("dd never ran").isEqualTo(2);
            assertThat(editor.cursor().line).isEqualTo(1);
        });
    }

    @Test void aMappingsChangesUndoAsOneStep() throws Exception {
        onEdt(() -> {
            var editor = editor("a\nb\nc\nd\n");
            nmap(editor, "x", "ddjdd");
            type(editor, "xu");
            assertThat(editor.buffer().line(0)).isEqualTo("a");
            assertThat(editor.buffer().count()).as("both deletes undone").isEqualTo(4);
            type(editor, "ddddu");
            assertThat(editor.buffer().count()).as("typed commands undo one at a time").isEqualTo(3);
        });
    }

    @Test void ctrlCStopsAMappingThatRunsOn() throws Exception {
        onEdt(() -> {
            var editor = editor("one\n");
            nmap(editor, "x", "ggx");
            int[] looks = { 0 };
            editor.typeahead().interruptCheck(() -> ++looks[0] % 3 == 0);
            int bells = editor.bells();
            type(editor, "x");
            assertThat(looks[0]).as("a look every %d keys", Typeahead.BREAK_CHECK).isEqualTo(3);
            assertThat(editor.bells()).isEqualTo(bells + 1);
            assertThat(editor.typeahead().waiting()).isFalse();
            type(editor, ":normal x<CR>");
            assertThat(looks[0]).as(":normal looks too").isEqualTo(6);
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
        });
    }

    @Test void aPrefixWaitsUntilTheTimeoutResolvesIt() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\n");
            nmap(editor, "ab", "dd");
            type(editor, "2a");
            assertThat(editor.mode()).as("a waits for b").isEqualTo(Mode.NORMAL);
            assertThat(editor.showcmd()).as("the count and the waiting key").isEqualTo("2a");
            assertThat(editor.typeahead().timing()).isTrue();
            editor.timeoutNow();
            assertThat(editor.mode()).as("a alone appends").isEqualTo(Mode.INSERT);
            assertThat(editor.typeahead().timing()).isFalse();
        });
    }

    @Test void theNextKeyEndsTheWait() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\n");
            nmap(editor, "ab", "dd");
            type(editor, "a");
            type(editor, "b");
            assertThat(editor.buffer().line(0)).isEqualTo("two");
            assertThat(editor.typeahead().timing()).isFalse();
            nmap(editor, "xy", "yy");
            map(editor, Mappings.NORMAL, "x", "dd", false, true);
            type(editor, "x");
            assertThat(editor.buffer().count()).as("<nowait> does not wait for xy").isEqualTo(1);
        });
    }

    @Test void theTimerRunsOutAfterTimeoutlen() throws Exception {
        var editor = new AtomicReference<VimEditor>();
        onEdt(() -> {
            editor.set(editor("one\n"));
            nmap(editor.get(), "ab", "dd");
            type(editor.get(), ":set tm=40<CR>a");
        });
        long end = System.nanoTime() + 5_000_000_000L;
        var mode = new AtomicReference<Mode>();
        do {
            Thread.sleep(20);
            onEdt(() -> mode.set(editor.get().mode()));
        } while (mode.get() != Mode.INSERT && System.nanoTime() < end);
        assertThat(mode.get()).as("the Swing timer resolved a").isEqualTo(Mode.INSERT);
    }

    @Test void withNotimeoutKeysWaitForTheNextKey() throws Exception {
        onEdt(() -> {
            var editor = editor("one\n");
            nmap(editor, "ab", "dd");
            type(editor, ":set notimeout<CR>a");
            assertThat(editor.typeahead().timing()).isFalse();
            assertThat(editor.typeahead().waiting()).isTrue();
            type(editor, "x");
            assertThat(editor.mode()).as("a then x: append an x").isEqualTo(Mode.INSERT);
            assertThat(editor.buffer().line(0)).isEqualTo("oxne");
        });
    }

    @Test void theKeyAfterACountDigitOrACommandsFirstKeyIsNotMapped() throws Exception {
        onEdt(() -> {
            var editor = editor("a1\na2\na3\na4\na5\na6\na7\na8\na9\na10\na11\na12\n");
            nmap(editor, "0", "dd");
            nmap(editor, "e", "x");
            type(editor, "10j");
            assertThat(editor.cursor().line).as("10j, not 1 and dd").isEqualTo(10);
            type(editor, "fe");
            assertThat(editor.buffer().count()).as("f takes e as it is").isEqualTo(12);
            type(editor, "0");
            assertThat(editor.buffer().count()).as("0 alone is mapped").isEqualTo(11);
        });
    }

    @Test void mappingsApplyInTheirModes() throws Exception {
        onEdt(() -> {
            var editor = editor("one two\n");
            map(editor, Mappings.INSERT, "jk", "<Esc>", false, false);
            map(editor, Mappings.OP_PENDING, "w", "e", false, false);
            map(editor, Mappings.CMDLINE, "XX", "s/one/1/", false, false);
            type(editor, "iabjk");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(editor.buffer().line(0)).isEqualTo("abone two");
            type(editor, "0dw");
            assertThat(editor.buffer().line(0)).as("dw is de").isEqualTo(" two");
            type(editor, "u:XX<CR>");
            assertThat(editor.buffer().line(0)).isEqualTo("ab1 two");
        });
    }

    @Test void normalUsesMappingsUnlessBang() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\n");
            nmap(editor, "x", "dd");
            type(editor, ":normal! x<CR>");
            assertThat(editor.buffer().line(0)).isEqualTo("ne");
            type(editor, ":normal x<CR>");
            assertThat(editor.buffer().line(0)).isEqualTo("two");
        });
    }

    @Test void aMappedKeyIsVimsEvenWhenTheEditorWouldTakeIt() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\n");
            nmap(editor, "<F2>", "dd");
            nmap(editor, "<D-d>", "j");
            assertThat(editor.offer(KeyInput.named("F2"))).isTrue();
            assertThat(editor.buffer().line(0)).isEqualTo("two");
            assertThat(editor.offer(new KeyInput("d", KeyInput.META))).as("a mapped ⌘ key").isTrue();
            assertThat(editor.offer(new KeyInput("e", KeyInput.META))).as("an unmapped one").isFalse();
            nmap(editor, "gx", "dd");
            assertThat(editor.offer(KeyInput.of('g'))).isTrue();
            assertThat(editor.offer(KeyInput.named("F3"))).as("keys waiting for a mapping take the next key").isTrue();
        });
    }

    @Test void commandLinesFromMappingsDoNotJoinTheHistory() throws Exception {
        onEdt(() -> {
            var editor = editor("a\n");
            nmap(editor, "x", ":s/a/b/<CR>");
            type(editor, "x");
            assertThat(editor.buffer().line(0)).isEqualTo("b");
            assertThat(editor.vim().commandHistory()).isEmpty();
            assertThat(editor.vim().registers().get(':').orElseThrow().text()).as("the : register has it").isEqualTo("s/a/b/");
            type(editor, ":s/b/c/<CR>");
            assertThat(editor.vim().commandHistory()).containsExactly("s/b/c/");
        });
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.TypeaheadTest'
```

Expected: FAIL: the test does not compile (`Mappings` and `VimEditor.typeahead()` do not exist).

- [ ] **Step 3: Write the mapping table and the typeahead**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Mappings.java`:

```java
package dev.jasper.editor.vim;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The key mappings (spec 7.2), global as Vim's maphash: each has the modes it applies in, a left-hand side of keys
 * and a right-hand side of keys, as Vim's mapblock_T in map.c. The typeahead ({@link Typeahead}) looks mappings up
 * here; {@link ExMaps} defines, lists and removes them.
 */
final class Mappings {
    /** Vim's MODE_* bits for mappings: Normal, Visual, Select (only through {@code :vmap} and {@code :map}), Operator-pending, Insert, Command-line. */
    static final int NORMAL = 1, VISUAL = 2, SELECT = 4, OP_PENDING = 8, INSERT = 16, CMDLINE = 32;
    /** The modes of {@code :map} and of {@code :map!}. */
    static final int MAP = NORMAL | VISUAL | SELECT | OP_PENDING, MAP_BANG = INSERT | CMDLINE;

    /** One mapping; {@link ExMaps} changes its modes and right-hand side as Vim's do_map does. */
    static final class Mapping {
        int modes;
        final List<KeyInput> lhs;
        List<KeyInput> rhs;
        boolean noremap, nowait;
        /** Newer mappings are tried and listed first (Vim adds each in front of its hash list). */
        final long order;

        Mapping(int modes, List<KeyInput> lhs, List<KeyInput> rhs, boolean noremap, boolean nowait, long order) {
            this.modes = modes;
            this.lhs = List.copyOf(lhs);
            this.rhs = List.copyOf(rhs);
            this.noremap = noremap;
            this.nowait = nowait;
            this.order = order;
        }
    }

    private final List<Mapping> all = new ArrayList<>();
    private long next;

    /** @return every mapping, in Vim's listing order: by the hash of the first key, newest first */
    List<Mapping> all() {
        List<Mapping> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingInt(Mappings::hash).thenComparing(Comparator.comparingLong((Mapping m) -> m.order).reversed()));
        return sorted;
    }

    /**
     * @param state the mode being typed in, one of the mode bits
     * @param first the first key of the typeahead
     * @return the mappings for that mode that start with that key, newest first (the order handle_mapping tries them)
     */
    List<Mapping> starting(int state, KeyInput first) {
        List<Mapping> found = new ArrayList<>();
        for (Mapping m : all) if ((m.modes & state) != 0 && m.lhs.getFirst().equals(first)) found.add(m);
        found.sort(Comparator.comparingLong((Mapping m) -> m.order).reversed());
        return found;
    }

    boolean isEmpty() { return all.isEmpty(); }

    /**
     * @param modes the mode bits it applies in
     * @param lhs its left-hand side
     * @param rhs its right-hand side, empty for {@code <Nop>}
     * @param noremap whether the right-hand side is not mapped again
     * @param nowait whether it runs without waiting for a longer mapping
     * @return the new mapping, the newest
     */
    Mapping add(int modes, List<KeyInput> lhs, List<KeyInput> rhs, boolean noremap, boolean nowait) {
        Mapping m = new Mapping(modes, lhs, rhs, noremap, nowait, next++);
        all.add(m);
        return m;
    }

    void remove(Mapping m) { all.remove(m); }

    /** map_clear_mode: the modes go from every mapping; one left with none goes. */
    void clear(int modes) {
        for (Mapping m : List.copyOf(all)) {
            m.modes &= ~modes;
            if (m.modes == 0) all.remove(m);
        }
    }

    /** MAP_HASH: Normal-ish mappings by their first byte, Insert and Command-line ones by it with the top bit flipped. */
    private static int hash(Mapping m) {
        int c = firstByte(m.lhs.getFirst());
        return (m.modes & MAP) != 0 ? c : c ^ 0x80;
    }

    /**
     * The first byte Vim stores for a key: an ASCII character or control character is itself, other characters their
     * first UTF-8 byte, and a special key K_SPECIAL (0x80). CTRL with a letter written as {@code <C-a>} is listed
     * from its unsimplified form, which starts with K_SPECIAL.
     */
    static int firstByte(KeyInput key) {
        if (key.modifiers() == 0 && key.isChar()) return key.key().getBytes(StandardCharsets.UTF_8)[0] & 0xff;
        if (key.modifiers() == 0) {
            switch (key.key()) {
                case "Esc" -> { return 0x1b; }
                case "CR" -> { return 0x0d; }
                case "Tab" -> { return 0x09; }
                default -> { }
            }
        }
        return 0x80;
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Typeahead.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Mappings.Mapping;
import java.awt.AWTEvent;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.Timer;

/**
 * Vim's typeahead buffer (spec 2.3 and 7.2), one per editor, ported from getchar.c (vgetorpeek, handle_mapping,
 * ins_typebuf, flush_buffers). Every key waits here before it runs: typed keys at the end, the keys of a mapping's
 * right-hand side or of a macro in front. A key sequence that starts a mapping's left-hand side waits for more keys
 * up to 'timeoutlen' ms on a Swing timer; a full match is replaced by its right-hand side, which is mapped again
 * unless the mapping is {@code noremap}. A failing command drops the keys that were not typed ({@link #flush}), which
 * ends a mapping or macro; a CTRL-C typed while they run ends them too (ui_breakcheck).
 */
final class Typeahead {
    /** Whether a waiting key may start a mapping (tb_noremap): yes; no; or not as the first key of its own mapping's right-hand side. */
    enum Remap { YES, NONE, SKIP }

    /** A waiting key: how it may be mapped, and whether it was typed (not from a mapping or a macro). */
    private record Key(KeyInput key, Remap remap, boolean typed) { }

    private static final KeyInput PLUG = KeyInput.named("Plug");
    /** How many keys from mappings and macros run between looks for a typed CTRL-C. */
    static final int BREAK_CHECK = 1000;

    private final VimEditor editor;
    /** The waiting keys; the next one is first. */
    private final List<Key> keys = new ArrayList<>();
    private final Timer timer;
    /** The timer ran out: the next resolution takes what matches in full (Vim's timedout). */
    private boolean timeoutFired;
    /** How deep {@code :normal} runs: out of keys, a partial mapping resolves at once (ex_normal_busy). */
    private int exNormal;
    private boolean running;
    /** Whether CTRL-C was typed while keys run (got_int); the test replaces it. */
    private BooleanSupplier interrupted = Typeahead::ctrlCWaiting;
    /** Whether the last key taken was typed (Vim's KeyTyped). */
    private boolean keyTyped;
    /** Whether taking the last key took a typed key, perhaps as part of a mapping (gotchars ran). */
    private boolean tookTyped;

    Typeahead(VimEditor editor) {
        this.editor = editor;
        timer = new Timer(1000, event -> timeout());
        timer.setRepeats(false);
    }

    /** A key typed: it waits behind the others, then everything that can run does. */
    void typed(KeyInput key) {
        keys.add(new Key(key, Remap.YES, true));
        run();
    }

    /**
     * ins_typebuf: keys that were not typed go in front of the waiting ones.
     *
     * @param inserted the keys, first one first
     * @param remap whether they may be mapped
     */
    void insert(List<KeyInput> inserted, Remap remap) {
        List<Key> front = new ArrayList<>();
        for (int i = 0; i < inserted.size(); i++)
            front.add(new Key(inserted.get(i), remap == Remap.SKIP ? i == 0 ? Remap.SKIP : Remap.YES : remap, false));
        keys.addAll(0, front);
    }

    /** flush_buffers(FLUSH_MINIMAL), after an error: the keys at the front that were not typed go. */
    void flush() {
        while (!keys.isEmpty() && !keys.getFirst().typed()) keys.removeFirst();
    }

    /** Everything goes, typed keys too, and the timer stops (the editor closed, or the text was replaced). */
    void clear() {
        keys.clear();
        timer.stop();
    }

    /** @return whether the timer runs for 'timeoutlen' */
    boolean timing() { return timer.isRunning(); }

    /** @return whether keys wait for the rest of a mapping */
    boolean waiting() { return !running && !keys.isEmpty(); }

    /** @return the keys waiting for the rest of a mapping, for 'showcmd' */
    List<KeyInput> waitingKeys() { return keys.stream().map(Key::key).toList(); }

    /** @return whether the last key taken was typed, not from a mapping, a macro or {@code :normal} (KeyTyped) */
    boolean keyTyped() { return keyTyped; }

    /** @return whether keys that were not typed wait (a mapping's or a macro's; Vim's typebuf.tb_maplen) */
    boolean mapped() { return !keys.isEmpty() && !keys.getFirst().typed(); }

    /** @param check whether CTRL-C was typed while keys run, instead of looking at the AWT event queue */
    void interruptCheck(BooleanSupplier check) { interrupted = check; }

    /** ui_breakcheck: whether a CTRL-C key press waits in the event queue while the UI thread runs keys. */
    private static boolean ctrlCWaiting() {
        if (GraphicsEnvironment.isHeadless()) return false;
        AWTEvent event = Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent(KeyEvent.KEY_PRESSED);
        return event instanceof KeyEvent key && key.getKeyCode() == KeyEvent.VK_C && key.isControlDown();
    }

    /**
     * Every {@link #BREAK_CHECK} keys from mappings and macros: after a CTRL-C every waiting key goes, typed ones too
     * (flush_buffers(FLUSH_INPUT)), and the bell rings; the key to run instead is {@code <C-c>}, or {@code <Esc>}
     * in Insert mode and on the command line, which ends the command in progress.
     *
     * @return that key, or null when the keys go on
     */
    private KeyInput interrupted(int mappedKeys) {
        if (mappedKeys % BREAK_CHECK != 0 || !interrupted.getAsBoolean()) return null;
        keys.clear();
        editor.mappingFailed(VimFailure.bell());
        return editor.mode().inserting() || editor.mode() == Mode.COMMAND_LINE ? KeyInput.named("Esc") : KeyInput.ctrl('c');
    }

    /** 'timeoutlen' ran out: the waiting keys resolve as they are. */
    void timeout() {
        timer.stop();
        if (keys.isEmpty() || running) return;
        timeoutFired = true;
        run();
    }

    /**
     * Runs keys until none are left or the next ones wait for the rest of a mapping; then the timer starts. The keys
     * of a mapping or a macro change the text as one undo step: Vim closes an undo step only when it takes a typed
     * key outside Insert mode and the command line (u_sync in gotchars).
     */
    void run() {
        if (running) return;
        running = true;
        timer.stop();
        boolean grouped = false;
        int mappedKeys = 0;
        try {
            while (!keys.isEmpty()) {
                KeyInput key = next();
                if (key == null) break;
                KeyInput stop = keyTyped ? null : interrupted(++mappedKeys);
                if (stop != null) key = stop;
                if (grouped && tookTyped && !editor.mode().inserting() && !editor.commandLine().active()) {
                    editor.endUndoStep();
                    grouped = false;
                }
                if (!grouped && !keyTyped) {
                    editor.beginUndoGroup();
                    grouped = true;
                }
                editor.step(key);
            }
        } finally {
            if (grouped) editor.endUndoStep();
            running = false;
            timeoutFired = false;
        }
        Options options = editor.options();
        if (!keys.isEmpty() && options.flag("timeout")) {
            timer.setInitialDelay(Math.max(0, options.number("timeoutlen")));
            timer.restart();
        }
    }

    /**
     * ex_normal: the keys run now with the typeahead saved, as if typed but mapped only when {@code remap}; keys
     * that run out in the middle of a mapping resolve at once. The first failure ends the keys.
     *
     * @return the first failure, or null
     */
    VimFailure execute(List<KeyInput> run, boolean remap) {
        List<Key> saved = new ArrayList<>(keys);
        keys.clear();
        exNormal++;
        VimFailure failed = null;
        int mappedKeys = 0;
        try {
            insert(run, remap ? Remap.YES : Remap.NONE);
            while (!keys.isEmpty()) {
                KeyInput key = next();
                if (key == null) break;
                KeyInput stop = interrupted(++mappedKeys);
                if (stop != null) key = stop;
                try {
                    editor.dispatch(key);
                } catch (VimFailure failure) {
                    if (failed == null) failed = failure;
                    flush();
                }
            }
        } finally {
            exNormal--;
            keys.clear();
            keys.addAll(saved);
        }
        return failed;
    }

    /**
     * vgetorpeek and handle_mapping: the next key to run, after replacing any mapping the waiting keys start with;
     * null when they are a proper prefix of a mapping and more keys may come.
     */
    private KeyInput next() {
        boolean timedOut = timeoutFired;
        timeoutFired = false;
        tookTyped = false;
        int depth = 0;
        while (!keys.isEmpty()) {
            Key head = keys.getFirst();
            int state = editor.mapState(head.key());
            Mappings mappings = editor.vim().mappings();
            boolean plug = head.key().equals(PLUG);
            if (state != 0 && !mappings.isEmpty() && (head.remap() == Remap.YES || plug)) {
                Mapping match = null;
                boolean partial = false;
                for (Mapping m : mappings.starting(state, head.key())) {
                    int keylen = m.lhs.size();
                    int mlen = 1;
                    while (mlen < keys.size() && mlen < keylen && m.lhs.get(mlen).equals(keys.get(mlen).key())) mlen++;
                    if (mlen != keylen && !(mlen == keys.size() && keys.size() < keylen)) continue;
                    // A key that may not be mapped cannot be part of the left-hand side.
                    if (!plug && keys.subList(0, mlen).stream().anyMatch(k -> k.remap() != Remap.YES)) continue;
                    if (keylen > keys.size()) {
                        if (!timedOut && !(match != null && match.nowait)) {
                            partial = true;
                            break;
                        }
                    } else if (match == null || keylen > match.lhs.size()) match = m;
                }
                if (partial) {
                    if (exNormal == 0) return null;
                    // Out of keys inside :normal: as if the time ran out.
                    timedOut = true;
                    continue;
                }
                if (match != null) {
                    consume(match.lhs.size());
                    if (++depth >= editor.options().number("maxmapdepth")) {
                        editor.mappingFailed(new VimFailure("E223: Recursive mapping"));
                        flush();
                        depth = 0;
                        continue;
                    }
                    List<KeyInput> rhs = match.rhs;
                    Remap remap = match.noremap ? Remap.NONE
                        : rhs.size() >= match.lhs.size() && rhs.subList(0, match.lhs.size()).equals(match.lhs) ? Remap.SKIP : Remap.YES;
                    insert(rhs, remap);
                    continue;
                }
            }
            consume(1);
            keyTyped = head.typed();
            return head.key();
        }
        return null;
    }

    /** The first {@code n} keys are taken. */
    private void consume(int n) {
        for (int i = 0; i < n; i++) if (keys.removeFirst().typed()) tookTyped = true;
    }
}
```

- [ ] **Step 4: Say which keys may be mapped, and add the options**

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java

    boolean operatorPending() { return operator != null; }

    /** @return whether a command is partly typed */
```

with:

```java

    boolean operatorPending() { return operator != null; }

    /** @return whether the next key may start a mapping: it is not a command's argument or its second key (no_mapping) */
    boolean mappable() { return argument == null && keys.isEmpty(); }

    /** @return whether count digits are being typed, so that {@code 0} is one more and not mapped (no_zero_mapping) */
    boolean counting() { return operator == null ? count1 > 0 : count2 > 0; }

    /** @return whether a command is partly typed */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/InsertMode.java`, replace:

```java
        editor.setCursor(new Pos(line, indent.length()), true);
    }

    /** @return whether the key means something in Insert mode, for the handler rule */
```

with:

```java
        editor.setCursor(new Pos(line, indent.length()), true);
    }

    /** @return whether the next key is an argument ({@code <C-r>}'s register, {@code <C-v>}'s character), which is not mapped */
    boolean readingArgument() { return argument != null; }

    /** @return whether the key means something in Insert mode, for the handler rule */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/CommandLine.java`, replace:

```java
        argument = null;
    }

    boolean active() { return done != null; }
```

with:

```java
        argument = null;
    }

    /** @return whether the next key is an argument ({@code <C-r>}'s register, {@code <C-v>}'s character), which is not mapped */
    boolean readingArgument() { return argument != null; }

    boolean active() { return done != null; }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, replace:

```java
    private final Registers registers;
    private final KeyHandlers handlers = new KeyHandlers();
    private final VimCommands commands = VimCommands.standard();
    private Consumer<String> coverage = id -> { };
```

with:

```java
    private final Registers registers;
    private final KeyHandlers handlers = new KeyHandlers();
    private final Mappings mappings = new Mappings();
    private final VimCommands commands = VimCommands.standard();
    private Consumer<String> coverage = id -> { };
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, replace:

```java
    /** @return the handler table */
    public KeyHandlers handlers() { return handlers; }

    /** @return the command table */
```

with:

```java
    /** @return the handler table */
    public KeyHandlers handlers() { return handlers; }

    /** @return the key mappings */
    Mappings mappings() { return mappings; }

    /** @return the command table */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("joinspaces", "js", Type.BOOLEAN, true, false),
        new Def("matchpairs", "mps", Type.STRING, "(:),{:},[:]", true),
        new Def("nrformats", "nf", Type.STRING, "bin,octal,hex", true),
        new Def("number", "nu", Type.BOOLEAN, true, true),
```

with:

```java
        new Def("joinspaces", "js", Type.BOOLEAN, true, false),
        new Def("matchpairs", "mps", Type.STRING, "(:),{:},[:]", true),
        new Def("maxmapdepth", "mmd", Type.NUMBER, 1000, false),
        new Def("nrformats", "nf", Type.STRING, "bin,octal,hex", true),
        new Def("number", "nu", Type.BOOLEAN, true, true),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("textwidth", "tw", Type.NUMBER, 0, true),
        new Def("tildeop", "top", Type.BOOLEAN, false, false),
        new Def("whichwrap", "ww", Type.STRING, "b,s", false),
        new Def("wrap", "wrap", Type.BOOLEAN, true, true),
```

with:

```java
        new Def("textwidth", "tw", Type.NUMBER, 0, true),
        new Def("tildeop", "top", Type.BOOLEAN, false, false),
        new Def("timeout", "to", Type.BOOLEAN, true, false),
        new Def("timeoutlen", "tm", Type.NUMBER, 1000, false),
        new Def("whichwrap", "ww", Type.STRING, "b,s", false),
        new Def("wrap", "wrap", Type.BOOLEAN, true, true),
```

- [ ] **Step 5: Send every key through the typeahead**

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    private final CommandLine commandLine = new CommandLine(this);
    private final Ex ex = new Ex(this);
    /** A question waiting for its answer key (Vim's ask_yesno and the {@code :s} confirmation): its text and what takes the key. */
    private String promptText;
```

with:

```java
    private final CommandLine commandLine = new CommandLine(this);
    private final Ex ex = new Ex(this);
    private final Typeahead typeahead = new Typeahead(this);
    /** How many keys have run, so {@link #feed} knows whether one did. */
    private long steps;
    /** A question waiting for its answer key (Vim's ask_yesno and the {@code :s} confirmation): its text and what takes the key. */
    private String promptText;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    /** Turns Vim off: the caret and input methods return to the Editor's; the text is untouched. */
    public void close() {
        ex.abort();
        promptHandler = null;
```

with:

```java
    /** Turns Vim off: the caret and input methods return to the Editor's; the text is untouched. */
    public void close() {
        typeahead.clear();
        ex.abort();
        promptHandler = null;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    /** The text was replaced (a reload): any command or insert in progress is dropped. */
    public void textReplaced() {
        ex.abort();
        promptHandler = null;
```

with:

```java
    /** The text was replaced (a reload): any command or insert in progress is dropped. */
    public void textReplaced() {
        typeahead.clear();
        ex.abort();
        promptHandler = null;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
            case VIM -> true;
            case IDE -> false;
            case DEFAULT -> (key.modifiers() & KeyInput.META) == 0 && (mode.inserting() ? insert.wants(key) : machine.wants(key));
        };
        if (mine) feed(key);
        return mine;
    }
```

with:

```java
            case VIM -> true;
            case IDE -> false;
            // Keys that wait for the rest of a mapping, and a key that starts one (even a ⌘ key), are Vim's.
            case DEFAULT -> typeahead.waiting() || startsMapping(key)
                || (key.modifiers() & KeyInput.META) == 0 && (mode.inserting() ? insert.wants(key) : machine.wants(key));
        };
        if (mine) feed(key);
        return mine;
    }

    /** @return whether a mapping for the mode Vim is in starts with the key */
    private boolean startsMapping(KeyInput key) {
        int state = mapState(key);
        return state != 0 && !vim.mappings().starting(state, key).isEmpty();
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java

    /**
     * Runs one key, as typed. A failing command rings the bell; an unexpected exception cancels the command, closes
     * its undo step and returns to Normal mode (spec 10).
     *
     * @param key the key
     */
    public void feed(KeyInput key) {
        busy = true;
        message = "";
```

with:

```java

    /**
     * A key typed: it goes through the typeahead, where mappings apply (spec 7.2), and then runs, unless it starts a
     * mapping and waits for the keys after it.
     *
     * @param key the key
     */
    public void feed(KeyInput key) {
        long before = steps;
        typeahead.typed(key);
        // Nothing ran (keys wait for the rest of a mapping, or a mapping did nothing): the Vim bar shows what waits.
        if (steps == before || typeahead.waiting()) fire();
    }

    /** The keys waiting for the rest of a mapping resolve now, as when 'timeoutlen' runs out. */
    void timeoutNow() {
        typeahead.timeout();
        fire();
    }

    /** @return the typeahead, where keys wait and mappings and macros insert theirs */
    Typeahead typeahead() { return typeahead; }

    /**
     * The mapping mode for the next key, as Vim's get_real_state, or 0 when the key is not mapped (no_mapping): a
     * question's answer, the key after one that reads an argument ({@code f}, {@code "}, {@code i_CTRL-V}), the second
     * key of a two-key command ({@code gx}), and {@code 0} after count digits. At the output panel's hit-enter
     * prompt Normal-mode mappings apply to any key but {@code <CR>} and {@code <Space>}.
     */
    int mapState(KeyInput next) {
        if (promptHandler != null) return 0;
        if (!output.isEmpty()) return next.is("<CR>") || next.is(" ") ? 0 : Mappings.NORMAL;
        if (commandLine.active()) return commandLine.readingArgument() ? 0 : Mappings.CMDLINE;
        if (mode.inserting()) return insert.readingArgument() ? 0 : Mappings.INSERT;
        if (!machine.mappable() || next.plainChar() == '0' && machine.counting()) return 0;
        if (mode.visual()) return Mappings.VISUAL;
        return machine.operatorPending() ? Mappings.OP_PENDING : Mappings.NORMAL;
    }

    /** Mapped keys failed (E223) or were interrupted: the bell, and the message if there is one, as for a failing command. */
    void mappingFailed(VimFailure failure) {
        bells++;
        if (!failure.getMessage().isEmpty()) message(failure.getMessage(), true);
        fire();
    }

    /**
     * Runs one key from the typeahead. A failing command rings the bell and drops the keys of the mapping or macro
     * running (spec 2.3); an unexpected exception cancels the command, closes its undo step and returns to Normal
     * mode (spec 10).
     */
    void step(KeyInput key) {
        steps++;
        busy = true;
        message = "";
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
            if (!failure.quiet) bells++;
            if (!failure.getMessage().isEmpty()) message(failure.getMessage(), true);
            if (!mode.inserting()) {
                machine.reset();
```

with:

```java
            if (!failure.quiet) bells++;
            if (!failure.getMessage().isEmpty()) message(failure.getMessage(), true);
            // beep_flush and emsg drop the keys of a running mapping or macro; a beep in Insert mode does not.
            if (!mode.inserting() || !failure.getMessage().isEmpty()) typeahead.flush();
            if (!mode.inserting()) {
                machine.reset();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
        } catch (RuntimeException bug) {
            LOG.log(System.Logger.Level.ERROR, "Vim command failed", bug);
            ex.abort();
            promptHandler = null;
```

with:

```java
        } catch (RuntimeException bug) {
            LOG.log(System.Logger.Level.ERROR, "Vim command failed", bug);
            typeahead.clear();
            ex.abort();
            promptHandler = null;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
        if (!options.flag("showcmd")) return "";
        String pending = machine.showcmd();
        return pending.isEmpty() && mode.visual() && visualAnchor != null ? visualSize() : pending;
    }
```

with:

```java
        if (!options.flag("showcmd")) return "";
        String pending = machine.showcmd();
        // Keys waiting for the rest of a mapping show after the partly typed command.
        if (typeahead.waiting()) return pending + KeyInput.format(typeahead.waitingKeys());
        return pending.isEmpty() && mode.visual() && visualAnchor != null ? visualSize() : pending;
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java

    void endUndoStep() { area.vimUndo().endStep(); }

    /** {@code u}: undoes {@code count} steps, placing the cursor as Vim's u_undoredo does. */
```

with:

```java

    void endUndoStep() { area.vimUndo().endStep(); }

    /** Opens an undo step that the commands run from a mapping or macro share ({@link #endUndoStep} closes it). */
    void beginUndoGroup() {
        Pos p = cursor();
        area.vimUndo().beginStep(p.line, p.col);
    }

    /** {@code u}: undoes {@code count} steps, placing the cursor as Vim's u_undoredo does. */
```

- [ ] **Step 6: Let errors end a mapping, and `:normal` use mappings**

An error in a command line drops the keys of a running mapping or macro (Vim's emsg calls flush_buffers), and a
command line counts as typed only when the key that ran it was typed (KeyTyped), for the history and `:s`'s report.
`:normal` runs its keys through the typeahead with it saved, mapped unless `:normal!`.

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, replace:

```java

    /** @return whether the keys running now were typed (Vim's KeyTyped) */
    boolean typed() { return normalDepth == 0; }

    /**
```

with:

```java

    /** @return whether the keys running now were typed (Vim's KeyTyped) */
    boolean typed() { return normalDepth == 0 && editor.typeahead().keyTyped(); }

    /**
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, replace:

```java
        if (!failure.getMessage().isEmpty()) failed = true;
        if (failure.quiet) return;
        if (failure.getMessage().isEmpty()) editor.bell();
        else editor.message(failure.getMessage(), true);
```

with:

```java
        if (!failure.getMessage().isEmpty()) failed = true;
        if (failure.quiet) return;
        // emsg and beep_flush drop the keys of a running mapping or macro.
        editor.typeahead().flush();
        if (failure.getMessage().isEmpty()) editor.bell();
        else editor.message(failure.getMessage(), true);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExLines.java`, replace:

```java
        Ex ex = editor.ex();
        if (ex.normalDepth >= 1000) throw new VimFailure("E192: Recursive use of :normal too deep");
        List<KeyInput> keys = keys(cmd.arg);
        ex.normalDepth++;
        try {
```

with:

```java
        Ex ex = editor.ex();
        if (ex.normalDepth >= 1000) throw new VimFailure("E192: Recursive use of :normal too deep");
        List<KeyInput> keys = KeyCodes.keys(cmd.arg);
        ex.normalDepth++;
        try {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExLines.java`, replace:

```java
                    line++;
                }
                run(editor, keys);
            } while (cmd.addrCount > 0 && line <= cmd.line2);
        } finally {
```

with:

```java
                    line++;
                }
                run(editor, keys, !cmd.forceit);
            } while (cmd.addrCount > 0 && line <= cmd.line2);
        } finally {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ExLines.java`, replace:

```java
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
```

with:

```java
    }

    /**
     * The keys run with the typeahead saved (mapped unless {@code :normal!}); a failure drops the rest of them.
     * Out of keys, whatever still waits for one gets {@code <Esc>}.
     */
    private static void run(VimEditor editor, List<KeyInput> keys, boolean remap) {
        VimFailure failed = editor.typeahead().execute(keys, remap);
        if (failed != null) {
            if (!failed.quiet) editor.bell();
```

- [ ] **Step 7: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.TypeaheadTest'
```

Expected: 14 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 174 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1585 tests pass: every key of the corpus now goes through the typeahead, with the same results.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`: the Editor compiles without warnings (`-Xlint:all`).

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v3a
git add plugins/editor/src/main/java/dev/jasper/editor/vim/Mappings.java plugins/editor/src/main/java/dev/jasper/editor/vim/Typeahead.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java plugins/editor/src/main/java/dev/jasper/editor/vim/InsertMode.java plugins/editor/src/main/java/dev/jasper/editor/vim/CommandLine.java plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java plugins/editor/src/main/java/dev/jasper/editor/vim/ExLines.java plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java plugins/editor/src/test/java/dev/jasper/editor/vim/TypeaheadTest.java
git commit -m "Run every Vim key through a typeahead where mappings apply

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: The mapping commands

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ExMapsTest.java`, `plugins/editor/src/test/resources/vim/corpus/mappings.case`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusRunner.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Consumes: `Mappings` and `Typeahead` (Task 2).
- Produces: `ExMaps.map(ExCommand)`, `ExMaps.mapclear(ExCommand)`, `ExMaps.parse(text, lhs)`, `ExMaps.display(keys, lhs)`, `ExMaps.name(key)`, `ExMaps.modeChars(modes)`, `ExMaps.line(mapping)`, `ExMaps.MAXMAPLEN`; the Ex commands `:map :nmap :vmap :xmap :omap :imap :cmap :noremap :nnoremap :vnoremap :xnoremap :onoremap :inoremap :cnoremap :unmap :nunmap :vunmap :xunmap :ounmap :iunmap :cunmap :mapclear :nmapclear :vmapclear :xmapclear :omapclear :imapclear :cmapclear`.

The corpus runner resolves keys still waiting for a mapping after a case's keys, as the recorder's `<Cmd>` ends them in Vim. The recorder clears Vim's own mappings first: Vim on macOS maps `<D-v>` and Visual `<BS>` even with `-u NONE`, and Jasper starts with none (re-recording the whole corpus with this line changes nothing).

- [ ] **Step 1: Write the failing tests and the corpus cases**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/ExMapsTest.java`:

```java
package dev.jasper.editor.vim;

import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** What the corpus cannot record about the mapping commands: notation details, and what Jasper refuses (spec 7.2). */
class ExMapsTest {
    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private static VimEditor editor(String text) {
        var area = new VimTextArea();
        area.setText(text);
        area.setCaretPosition(0);
        return new VimEditor(new Vim(new VimHost(r -> Optional.empty(), (r, t) -> { })), area, EditorDefaults.VIM);
    }

    @Test void readsTheNotationOfBothSides() {
        assertThat(ExMaps.parse("<Leader>x<LocalLeader>", true)).isEqualTo(KeyInput.parse("\\x\\"));
        assertThat(ExMaps.parse("a<Nop>b", false)).as("<Nop> inside is no key").isEqualTo(KeyInput.parse("ab"));
        assertThat(ExMaps.parse("\u0016<Esc>", false)).as("CTRL-V quotes the <").isEqualTo(KeyInput.parse("<lt>Esc>"));
        assertThat(ExMaps.parse("#1", true)).containsExactly(KeyInput.named("F1"));
        assertThat(ExMaps.parse("#0", true)).containsExactly(KeyInput.named("F10"));
        assertThat(ExMaps.parse("#1", false)).as("only in a left-hand side").isEqualTo(KeyInput.parse("#1"));
        assertThat(ExMaps.parse("x\u0016", true)).as("a CTRL-V ending a left-hand side stays").containsExactly(KeyInput.of('x'), KeyInput.ctrl('v'));
        assertThat(ExMaps.parse("x\u0016", false)).containsExactly(KeyInput.of('x'));
        assertThat(ExMaps.parse("<Char-65><char-0x42><Plug>\u0001", false))
            .containsExactly(KeyInput.of('A'), KeyInput.of('B'), KeyInput.named("Plug"), KeyInput.ctrl('a'));
    }

    @Test void showsKeysAsVimsListingDoes() {
        assertThat(ExMaps.display(KeyInput.parse("<Space>a b<Space>"), false)).isEqualTo("<Space>a b<Space>");
        assertThat(ExMaps.display(KeyInput.parse("a b"), true)).isEqualTo("a<Space>b");
        assertThat(ExMaps.display(KeyInput.parse("<C-a><C-j><C-@><C-Left><M-C-x><D-s><lt>"), false))
            .isEqualTo("<C-A><NL><Nul><C-Left><M-C-x><D-s><");
        assertThat(ExMaps.modeChars(Mappings.NORMAL | Mappings.OP_PENDING | Mappings.VISUAL)).isEqualTo("nox");
        assertThat(ExMaps.modeChars(Mappings.SELECT)).isEqualTo("s");
    }

    @Test void refusesWhatJasperDoesNotHave() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor("abc\n");
            for (String argument : new String[] { "<buffer>", "<expr>", "<script>" }) {
                type(editor, ":nmap " + argument.replace("<", "<lt>") + " x dd<CR>");
                assertThat(editor.message()).isEqualTo("Not supported in Jasper: " + argument);
            }
            type(editor, ":nmap <lt>silent> <lt>buffer> x dd<CR>");
            assertThat(editor.message()).as("after the arguments Jasper has").isEqualTo("Not supported in Jasper: <buffer>");
            assertThat(editor.vim().mappings().isEmpty()).isTrue();
            type(editor, ":mapclear <lt>buffer><CR>");
            assertThat(editor.message()).as("there are no buffer-local mappings to clear").isEmpty();
            type(editor, ":nmap " + "x".repeat(51) + " y<CR>");
            assertThat(editor.message()).as("MAXMAPLEN").isEqualTo("E474: Invalid argument");
            type(editor, ":nmap <lt>special><lt>silent> x dd<CR>x");
            assertThat(editor.buffer().line(0)).as("<special> and <silent> are taken").isEmpty();
        });
    }
}
```

Create `plugins/editor/src/test/resources/vim/corpus/mappings.case`:

```text
# Mappings: :map and its forms define, list and remove mappings; keys are mapped before they run.

=== nmap maps a key
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 2,1
keys: :nmap x dd<CR>x
--- vim 9.1
expect-text:
  one two three
  seven eight nine
  ten eleven twelve
expect-cursor: 2,1
expect-mode: normal

=== a mapping's changes undo together
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :nmap x ddjdd<CR>xu
--- vim 9.1
expect-text:
  a1
  b2
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal

=== a typed key after a mapping starts a new undo step
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :nmap x dd<CR>xxu
--- vim 9.1
expect-text:
  b2
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal

=== nnoremap does not map its right-hand side again
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x dd<CR>:nnoremap y x<CR>y
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== nmap maps its right-hand side again
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x dd<CR>:nmap y x<CR>y
--- vim 9.1
expect-text:
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== a right-hand side starting with its left-hand side
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :nmap j jj<CR>j
--- vim 9.1
expect-text:
  a1
  b2
  c3
  d4
  e5
  f6
expect-cursor: 6,1
expect-mode: normal

=== mappings that map each other fail with E223
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :nmap x y<CR>:nmap y x<CR>xj
output: yes
--- vim 9.1
expect-text:
  a1
  b2
  c3
  d4
  e5
  f6
expect-cursor: 2,1
expect-mode: normal
expect-output:
  E223: Recursive mapping

=== maxmapdepth limits the depth
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :set mmd=3<CR>:nmap a b<CR>:nmap b c<CR>:nmap c dd<CR>a
output: yes
--- vim 9.1
expect-text:
  a1
  b2
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E223: Recursive mapping

=== maxmapdepth deep enough
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :set mmd=4<CR>:nmap a b<CR>:nmap b c<CR>:nmap c dd<CR>a
--- vim 9.1
expect-text:
  b2
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal

=== a count applies to the first command of a mapping
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :nmap x ddj<CR>2x
--- vim 9.1
expect-text:
  c3
  d4
  e5
  f6
expect-cursor: 2,1
expect-mode: normal

=== zero after count digits is not mapped
text:
  a1
  b2
  c3
  d4
  e5
  f6
  g7
  h8
  i9
  j10
  k11
  l12
cursor: 1,1
keys: :nmap 0 dd<CR>10j0
--- vim 9.1
expect-text:
  a1
  b2
  c3
  d4
  e5
  f6
  g7
  h8
  i9
  j10
  l12
expect-cursor: 11,1
expect-mode: normal

=== a register applies to a mapping
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x yy<CR>"ax
registers: a
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-register a: l "one two three\n"

=== the character after f is not mapped
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap e x<CR>fe
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,3
expect-mode: normal

=== the key after g is not mapped
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,9
keys: :nmap e w<CR>ge
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,7
expect-mode: normal

=== the key after an operator's i is not mapped
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,6
keys: :nmap w e<CR>diw
--- vim 9.1
expect-text:
  one  three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,5
expect-mode: normal

=== omap maps a motion after an operator
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :omap w e<CR>dw
--- vim 9.1
expect-text:
   two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== omap does not apply in Normal mode
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :omap w e<CR>w
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,5
expect-mode: normal

=== xmap maps in Visual mode
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :xmap x y<CR>vlx
registers: "
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "on"

=== vmap maps in Visual mode
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :vmap x y<CR>vlx
registers: "
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "on"

=== nmap does not apply in Visual mode
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x y<CR>vlx
--- vim 9.1
expect-text:
  e two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== map applies after an operator
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :map x w<CR>dx
--- vim 9.1
expect-text:
  two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== map applies in Visual mode
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :map x w<CR>vxd
--- vim 9.1
expect-text:
  wo three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== imap maps in Insert mode
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :imap jk <lt>Esc><CR>iabjkx
--- vim 9.1
expect-text:
  aone two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,2
expect-mode: normal

=== inoremap does not map again
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :imap b c<CR>:inoremap a b<CR>iaa<Esc>
--- vim 9.1
expect-text:
  bbone two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,2
expect-mode: normal

=== imap in Replace mode
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :imap a Z<CR>Raa<Esc>
--- vim 9.1
expect-text:
  ZZe two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,2
expect-mode: normal

=== an Insert-mode mapping that is not completed
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :imap ab X<CR>iacb<Esc>
--- vim 9.1
expect-text:
  acbone two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,3
expect-mode: normal

=== the key after CTRL-V is not mapped in Insert mode
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :imap a X<CR>i<C-v>a<Esc>
--- vim 9.1
expect-text:
  aone two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== cmap maps on the command line
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :cmap XX s/$/!/<CR>:XX<CR>
--- vim 9.1
expect-text:
  a1!
  b2
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal

=== cnoremap does not map again
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :cmap Y s/$/!/<CR>:cnoremap XX Y<CR>:XX<CR>
output: yes
--- vim 9.1
expect-text:
  a1!
  b2
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal
expect-output:

=== map! maps in Insert mode and on the command line
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :map! ab AB<CR>iab<Esc>:s/$/ab/<CR>
--- vim 9.1
expect-text:
  ABa1AB
  b2
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal

=== nunmap removes a mapping
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x dd<CR>:nunmap x<CR>x
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== unmap of a missing mapping fails with E31
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nunmap x<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E31: No such mapping

=== unmap with no argument fails with E474
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :unmap<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E474: Invalid argument

=== nunmap of a map leaves its other modes
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :map x dd<CR>:nunmap x<CR>:map<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  ov x             dd

=== xunmap of a vmap leaves Select mode
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :vmap x y<CR>:xunmap x<CR>:map<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  s  x             y

=== unmap by the right-hand side
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x abc<CR>:nunmap abc<CR>:nmap<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  No mapping found

=== mapclear removes the mappings
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x dd<CR>:mapclear<CR>x
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== imapclear leaves Normal-mode mappings
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x dd<CR>:imap x y<CR>:imapclear<CR>:map!<CR>x
output: yes
--- vim 9.1
expect-text:
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  No mapping found

=== mapclear! removes Insert-mode mappings
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :imap a b<CR>:mapclear!<CR>ia<Esc>
--- vim 9.1
expect-text:
  aone two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== mapclear with an argument fails with E474
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :mapclear x<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E474: Invalid argument

=== the listing
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x dd<CR>:nnoremap y <lt>C-a><lt>Esc><CR>:imap jk <lt>Esc><CR>:omap w e<CR>:map ,f <lt>Space>a <CR>:map<CR>:map!<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
     ,f            <Space>a<Space>
  o  w             e
  n  x             dd
  n  y           * <C-A><Esc>
  i  jk            <Esc>

=== the listing orders mappings by their first key, newest first
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap ab 1<CR>:nmap aa 2<CR>:nmap ac 3<CR>:nmap <lt>F5> 5<CR>:nmap <lt>Del> 6<CR>:nmap <lt>C-]> 4<CR>:nmap Z 7<CR>:nmap é 8<CR>:nmap<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  Z             7
  n  ac            3
  n  aa            2
  n  ab            1
  n  <C-]>         4
  n  <Del>         6
  n  <F5>          5
  n  é             8

=== the listing shows special keys as Vim does
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x <lt>Tab><lt>lt>\|<lt>Bar><lt>Bslash><lt>C-v><lt>C-a><lt>NL><lt>S-Tab><CR>:nmap y <lt>Leader>x<lt>LocalLeader><CR>:nmap<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  x             <Tab><||\<C-V><C-A><NL><S-Tab>
  n  y             \x\

=== a listing for a prefix
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap ab x<CR>:nmap ac y<CR>:nmap b z<CR>:nmap a<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  ac            y
  n  ab            x

=== a listing for keys longer than the mapping
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap ab x<CR>:nmap abc<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  ab            x

=== an empty listing
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  No mapping found

=== unique fails when the mapping exists
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x dd<CR>:nmap <lt>unique> x yy<CR>x
output: yes
--- vim 9.1
expect-text:
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E227: Mapping already exists for x

=== unique adds a new mapping
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap <lt>unique> <lt>silent> x dd<CR>x
--- vim 9.1
expect-text:
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== a mapping replaced keeps its place
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap ab 1<CR>:nmap ac 2<CR>:nmap ab 3<CR>:nmap<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  ac            2
  n  ab            3

=== nmap after map splits the mapping
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :map x a<CR>:nmap x b<CR>:map<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  x             b
  ov x             a

=== Nop does nothing
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x <lt>Nop><CR>xw
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,5
expect-mode: normal

=== Nop in the listing
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x <lt>nop><CR>:nmap<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  x             <Nop>

=== a longer mapping waits for its keys
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap a x<CR>:nmap ab dd<CR>ab
--- vim 9.1
expect-text:
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== a shorter mapping when the longer one does not follow
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap a x<CR>:nmap ab dd<CR>aw
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,4
expect-mode: normal

=== nowait does not wait for a longer mapping
divergent: the recorder types every key at once, so Vim sees ab whole and never waits; Jasper gets the keys one at a time, as typing does
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap ab dd<CR>:nmap <lt>nowait> a x<CR>ab
--- expected
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== nowait on an older mapping still waits
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap <lt>nowait> a x<CR>:nmap ab dd<CR>ab
--- vim 9.1
expect-text:
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== keys at the end that start a mapping run unmapped
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap ab dd<CR>a
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,2
expect-mode: insert

=== a Plug mapping is mapped even from nnoremap
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nnoremap <lt>Plug>(del) dd<CR>:nnoremap x <lt>Plug>(del)<CR>x
--- vim 9.1
expect-text:
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== the leader is a backslash
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap <lt>Leader>d dd<CR>\d
--- vim 9.1
expect-text:
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== a space in a mapping
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap <lt>Space>x dd<CR> x
--- vim 9.1
expect-text:
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== a special key as the left-hand side
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap <lt>F2> dd<CR><F2>
--- vim 9.1
expect-text:
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== CTRL keys in a mapping
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap <lt>C-x> dd<CR>:nmap y <lt>C-x><CR>y
--- vim 9.1
expect-text:
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== a mapping to a command line
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :nmap x :s/$/!/<lt>CR><CR>x
--- vim 9.1
expect-text:
  a1!
  b2
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal

=== a mapping with two commands joined by a bar
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :nmap x :s/$/!/\|s/^/-/<lt>CR><CR>x
--- vim 9.1
expect-text:
  -a1!
  b2
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal

=== a mapping with the Bar key
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :nmap x :s/$/!/<lt>Bar>s/^/-/<lt>CR><CR>x
--- vim 9.1
expect-text:
  -a1!
  b2
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal

=== a failing command ends the mapping
text:
  a1
  b2
cursor: 1,1
keys: :nmap x jjjx<CR>x
--- vim 9.1
expect-text:
  a1
  b2
expect-cursor: 2,1
expect-mode: normal

=== a failing command line ends the mapping
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :nmap x :foo<lt>CR>x<CR>x
output: yes
--- vim 9.1
expect-text:
  a1
  b2
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E492: Not an editor command: foo

=== normal uses mappings
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x dd<CR>:normal x<CR>
--- vim 9.1
expect-text:
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== normal! does not
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x dd<CR>:normal! x<CR>
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== normal ends a mapping it only started
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap ab dd<CR>:normal a<CR>
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== a mapping in Insert-Normal mode
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x dd<CR>i<C-o>xZ<Esc>
--- vim 9.1
expect-text:
  Zfour five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== the second key of z is not mapped
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 2,1
keys: :nmap t x<CR>zt
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 2,1
expect-mode: normal

=== a trailing space is part of the right-hand side
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x dd <CR>x
--- vim 9.1
expect-text:
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,2
expect-mode: normal

=== a mapping of a digit
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :nmap 2 dd<CR>2
--- vim 9.1
expect-text:
  b2
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal

=== a mapping of a count digit after an operator
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :omap 2 j<CR>d2
--- vim 9.1
expect-text:
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal

=== a mapping of the register key
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap " x<CR>"
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== the hit-enter prompt maps the key
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :nmap x dd<CR>:nmap<CR>x
--- vim 9.1
expect-text:
  b2
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal

=== noremap does not map its right-hand side again
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x dd<CR>:noremap y x<CR>y
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== vnoremap does not map its right-hand side again
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :vmap x y<CR>:vnoremap z x<CR>vlz
--- vim 9.1
expect-text:
  e two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== xnoremap maps in Visual mode
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :xnoremap z d<CR>vlz
--- vim 9.1
expect-text:
  e two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== onoremap maps after an operator
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :onoremap w e<CR>dw
--- vim 9.1
expect-text:
   two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== vunmap removes a Visual-mode mapping
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :vmap x y<CR>:vunmap x<CR>vlx
--- vim 9.1
expect-text:
  e two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== ounmap removes an Operator-pending mapping
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :omap w e<CR>:ounmap w<CR>dw
--- vim 9.1
expect-text:
  two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== iunmap removes an Insert-mode mapping
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :imap a b<CR>:iunmap a<CR>ia<Esc>
--- vim 9.1
expect-text:
  aone two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== cunmap removes a command-line mapping
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :cmap XX s/$/!/<CR>:cunmap XX<CR>:cmap<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  No mapping found

=== nmapclear leaves the other modes
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :nmap x dd<CR>:omap w e<CR>:nmapclear<CR>:map<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  o  w             e

=== vmapclear removes Visual-mode mappings
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :vmap x y<CR>:nmap y z<CR>:vmapclear<CR>:map<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  y             z

=== xmapclear leaves Select mode
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :vmap x y<CR>:xmapclear<CR>:map<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  s  x             y

=== omapclear removes Operator-pending mappings
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :omap w e<CR>:omapclear<CR>dw
--- vim 9.1
expect-text:
  two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== cmapclear removes command-line mappings
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: :cmap XX s/$/!/<CR>:cmapclear<CR>:cmap<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  No mapping found
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExMapsTest'
```

Expected: FAIL: the test does not compile (`ExMaps` does not exist).

- [ ] **Step 3: Write `ExMaps` and add the commands**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Mappings.Mapping;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The mapping commands (spec 7.2), ported from Vim's map.c (do_map, get_map_mode, map_clear, showmap) and term.c
 * (replace_termcodes): {@code :map}, {@code :noremap}, {@code :unmap} and {@code :mapclear} in their
 * {@code n v x o i c} and {@code !} forms, which define, list and remove {@link Mappings}.
 */
final class ExMaps {
    private ExMaps() { }

    /** Vim's MAXMAPLEN: the longest left-hand side, in bytes. */
    static final int MAXMAPLEN = 50;

    /** get_map_mode: the modes a command's name gives, from its first letter; {@code !} gives Insert and Command-line. */
    static int mode(String name, boolean bang) {
        char c = name.charAt(0);
        if (c == 'n' && name.charAt(1) != 'o') return Mappings.NORMAL;
        return switch (c) {
            case 'i' -> Mappings.INSERT;
            case 'c' -> Mappings.CMDLINE;
            case 'v' -> Mappings.VISUAL | Mappings.SELECT;
            case 'x' -> Mappings.VISUAL;
            case 'o' -> Mappings.OP_PENDING;
            default -> bang ? Mappings.MAP_BANG : Mappings.MAP;
        };
    }

    /** ex_map and ex_unmap: do_map for the command, with its errors. */
    static void map(ExCommand cmd) {
        String name = cmd.name();
        int mode = mode(name, cmd.forceit);
        String rest = mode == Mappings.MAP || mode == Mappings.MAP_BANG ? name : name.substring(1);
        doMap(cmd.editor, cmd.arg, mode, rest.startsWith("u"), rest.startsWith("no"));
    }

    /** ex_mapclear: only {@code <buffer>} may follow, and Jasper has no buffer-local mappings to clear. */
    static void mapclear(ExCommand cmd) {
        String arg = cmd.arg;
        boolean local = arg.equals("<buffer>");
        if (!local && !arg.isEmpty()) throw new VimFailure("E474: Invalid argument");
        if (local) return;
        cmd.editor.vim().mappings().clear(mode(cmd.name(), cmd.forceit));
    }

    /**
     * do_map: {@code <nowait>}, {@code <silent>}, {@code <special>} and {@code <unique>} first, in any order; then
     * the left-hand side up to white space (for {@code :unmap} the whole rest, white space and all), then the
     * right-hand side. Without a right-hand side it lists; with one it defines, replacing the mapping of the same
     * keys in these modes. Jasper never echoes a mapping's command line, so {@code <silent>} changes nothing.
     */
    static void doMap(VimEditor editor, String arg, int mode, boolean unmap, boolean noremap) {
        Mappings mappings = editor.vim().mappings();
        boolean nowait = false, unique = false;
        String keys = arg;
        while (true) {
            for (String refused : List.of("<buffer>", "<expr>", "<script>"))
                if (keys.startsWith(refused)) throw new VimFailure("Not supported in Jasper: " + refused);
            if (keys.startsWith("<nowait>")) nowait = true;
            else if (keys.startsWith("<unique>")) unique = true;
            else if (!keys.startsWith("<silent>") && !keys.startsWith("<special>")) break;
            keys = keys.substring(Ex.skipWhite(keys, keys.indexOf('>') + 1));
        }
        int p = 0;
        while (p < keys.length() && (unmap || !Ex.isWhite(keys.charAt(p)))) {
            if (keys.charAt(p) == 0x16 && p + 1 < keys.length()) p++;
            p++;
        }
        String lhsText = keys.substring(0, p);
        String rhsText = p < keys.length() ? keys.substring(Ex.skipWhite(keys, p + 1)) : "";
        boolean hasKey = !lhsText.isEmpty(), hasArg = !rhsText.isEmpty();
        if (unmap && !hasKey) throw new VimFailure("E474: Invalid argument");
        List<KeyInput> lhs = parse(lhsText, true);
        List<KeyInput> rhs = rhsText.equalsIgnoreCase("<Nop>") ? List.of() : parse(rhsText, false);
        if (hasKey && byteLength(lhs) > MAXMAPLEN) throw new VimFailure("E474: Invalid argument");
        if (!unmap && (!hasKey || !hasArg)) {
            list(editor, mappings, mode, hasKey ? lhs : null);
            return;
        }
        if (unmap) {
            // The left-hand side first; failing that, a mapping whose right-hand side is exactly the keys.
            for (int round = 0; round < 2; round++) {
                boolean did = false;
                for (Mapping m : mappings.all()) {
                    if ((m.modes & mode) == 0 || !(round == 0 ? m.lhs : m.rhs).equals(lhs)) continue;
                    m.modes &= ~mode;
                    if (m.modes == 0) mappings.remove(m);
                    did = true;
                }
                if (did) return;
            }
            throw new VimFailure("E31: No such mapping");
        }
        Mapping reused = null;
        for (Mapping m : mappings.all()) {
            if ((m.modes & mode) == 0 || !m.lhs.equals(lhs)) continue;
            if (unique) throw new VimFailure("E227: Mapping already exists for " + display(lhs, true));
            m.modes &= ~mode;
            if (m.modes == 0 && reused == null) {
                // A mapping left with no modes takes the new right-hand side, keeping its place.
                m.rhs = List.copyOf(rhs);
                m.noremap = noremap;
                m.nowait = nowait;
                m.modes = mode;
                reused = m;
            } else if (m.modes == 0) mappings.remove(m);
        }
        if (reused == null) mappings.add(mode, lhs, rhs, noremap, nowait);
    }

    /**
     * The listing: every mapping for the modes, or those whose left-hand side and {@code prefix} start alike (the
     * shorter a prefix of the longer), or "No mapping found".
     */
    private static void list(VimEditor editor, Mappings mappings, int mode, List<KeyInput> prefix) {
        boolean any = false;
        for (Mapping m : mappings.all()) {
            if ((m.modes & mode) == 0) continue;
            if (prefix != null) {
                int n = Math.min(prefix.size(), m.lhs.size());
                if (!m.lhs.subList(0, n).equals(prefix.subList(0, n))) continue;
            }
            editor.say(line(m));
            any = true;
        }
        if (!any) editor.say("No mapping found");
    }

    /** showmap: the modes in three columns, the left-hand side in twelve, {@code *} for noremap, the right-hand side. */
    static String line(Mapping m) {
        StringBuilder out = new StringBuilder(modeChars(m.modes));
        while (out.length() < 3) out.append(' ');
        String keys = display(m.lhs, true);
        out.append(keys);
        int len = 0;
        for (int i = 0; i < keys.length(); ) {
            int cp = keys.codePointAt(i);
            len += Text.width(cp);
            i += Character.charCount(cp);
        }
        do {
            out.append(' ');
            len++;
        } while (len < 12);
        out.append(m.noremap ? '*' : ' ').append(' ');
        return out.append(m.rhs.isEmpty() ? "<Nop>" : display(m.rhs, false)).toString();
    }

    /** map_mode_to_chars: {@code n}, {@code v}, {@code !}, or blank for {@code :map}'s four modes. */
    static String modeChars(int modes) {
        if ((modes & Mappings.MAP_BANG) == Mappings.MAP_BANG) return "!";
        if ((modes & Mappings.INSERT) != 0) return "i";
        if ((modes & Mappings.CMDLINE) != 0) return "c";
        if ((modes & Mappings.MAP) == Mappings.MAP) return " ";
        StringBuilder out = new StringBuilder();
        if ((modes & Mappings.NORMAL) != 0) out.append('n');
        if ((modes & Mappings.OP_PENDING) != 0) out.append('o');
        if ((modes & (Mappings.VISUAL | Mappings.SELECT)) == (Mappings.VISUAL | Mappings.SELECT)) out.append('v');
        else {
            if ((modes & Mappings.VISUAL) != 0) out.append('x');
            if ((modes & Mappings.SELECT) != 0) out.append('s');
        }
        return out.toString();
    }

    /**
     * msg_outtrans_special: keys as a listing shows them. Special keys and control characters in {@code <>} form
     * ({@code <C-A>}, {@code <Esc>}); a space as {@code <Space>} in a left-hand side and at either end of a
     * right-hand side.
     */
    static String display(List<KeyInput> keys, boolean lhs) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            KeyInput key = keys.get(i);
            if (key.plainChar() == ' ' && (lhs || i == 0 || i == keys.size() - 1)) out.append("<Space>");
            else out.append(name(key));
        }
        return out.toString();
    }

    /** get_special_key_name for one key (modifiers in Vim's order M, C, S, D), or the character itself. */
    static String name(KeyInput key) {
        int modifiers = key.modifiers();
        if (key.isChar() && modifiers == 0) return key.key();
        if (key.isChar() && modifiers == KeyInput.CTRL) {
            char c = key.key().charAt(0);
            if (c == '@') return "<Nul>";
            if (c == 'j') return "<NL>";
            if (c >= 'a' && c <= 'z' || c >= '[' && c <= '_') return "<C-" + Character.toUpperCase(c) + ">";
        }
        StringBuilder out = new StringBuilder("<");
        if ((modifiers & KeyInput.ALT) != 0) out.append("M-");
        if ((modifiers & KeyInput.CTRL) != 0) out.append("C-");
        if ((modifiers & KeyInput.SHIFT) != 0) out.append("S-");
        if ((modifiers & KeyInput.META) != 0) out.append("D-");
        out.append(switch (key.key()) {
            case " " -> "Space";
            case "<" -> "lt";
            default -> key.key();
        });
        return out.append('>').toString();
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
        if (lhs && text.length() >= 2 && text.charAt(0) == '#' && Character.isDigit(text.charAt(1))) {
            keys.add(KeyInput.named(text.charAt(1) == '0' ? "F10" : "F" + text.charAt(1)));
            i = 2;
        }
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '<') {
                int close = text.indexOf('>', i + 1);
                if (close > i + 1) {
                    String body = text.substring(i + 1, close).toLowerCase(Locale.ROOT);
                    if (body.equals("leader") || body.equals("localleader")) {
                        keys.add(KeyInput.of('\\'));
                        i = close + 1;
                        continue;
                    }
                    if (body.equals("nop")) {
                        i = close + 1;
                        continue;
                    }
                    List<KeyInput> named = KeyInput.parse(text.substring(i, close + 1));
                    if (named.size() == 1) {
                        keys.add(named.getFirst());
                        i = close + 1;
                        continue;
                    }
                }
            }
            if (c == 0x16 && ++i >= text.length()) {
                if (lhs) keys.add(KeyInput.ctrl('v'));
                break;
            }
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            keys.addAll(KeyCodes.keys(Character.toString(cp)));
        }
        return keys;
    }

    /** @return the bytes Vim would store for the keys, for {@link #MAXMAPLEN} */
    static int byteLength(List<KeyInput> keys) {
        int n = 0;
        for (KeyInput key : keys) {
            if (key.modifiers() == 0 && key.isChar()) n += key.key().getBytes(StandardCharsets.UTF_8).length;
            else if (key.modifiers() == 0 && Mappings.firstByte(key) != 0x80 || key.modifiers() == KeyInput.CTRL && key.isChar()) n += 1;
            else n += key.modifiers() == 0 ? 3 : 6;
        }
        return n;
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, replace:

```java
        def("tabnew", BANG | EXTRA | RANGE | ZEROR | TRLBAR, Addr.OTHER, "a new untitled tab, or {file} in a tab", ExFiles::newTab);
        def("tabedit", BANG | EXTRA | RANGE | ZEROR | TRLBAR, Addr.OTHER, "as `:tabnew`", ExFiles::newTab);
    }
```

with:

```java
        def("tabnew", BANG | EXTRA | RANGE | ZEROR | TRLBAR, Addr.OTHER, "a new untitled tab, or {file} in a tab", ExFiles::newTab);
        def("tabedit", BANG | EXTRA | RANGE | ZEROR | TRLBAR, Addr.OTHER, "as `:tabnew`", ExFiles::newTab);
        // The mapping commands take | only escaped and keep CTRL-V (EX_TRLBAR, EX_NOTRLCOM, EX_CTRLV).
        int map = EXTRA | TRLBAR | NOTRLCOM | CTRLV;
        def("map", BANG | map, Addr.NONE, "map {lhs} to {rhs} in Normal, Visual and Operator-pending mode (`!`: Insert and Command-line); list mappings", ExMaps::map);
        def("nmap", map, Addr.NONE, "map {lhs} to {rhs} in Normal mode; list the mappings", ExMaps::map);
        def("vmap", map, Addr.NONE, "map {lhs} to {rhs} in Visual mode; list the mappings", ExMaps::map);
        def("xmap", map, Addr.NONE, "map {lhs} to {rhs} in Visual mode, as `:vmap`; list the mappings", ExMaps::map);
        def("omap", map, Addr.NONE, "map {lhs} to {rhs} in Operator-pending mode; list the mappings", ExMaps::map);
        def("imap", map, Addr.NONE, "map {lhs} to {rhs} in Insert mode; list the mappings", ExMaps::map);
        def("cmap", map, Addr.NONE, "map {lhs} to {rhs} on the command line; list the mappings", ExMaps::map);
        def("noremap", BANG | map, Addr.NONE, "as `:map`, but {rhs} is not mapped again", ExMaps::map);
        def("nnoremap", map, Addr.NONE, "as `:nmap`, but {rhs} is not mapped again", ExMaps::map);
        def("vnoremap", map, Addr.NONE, "as `:vmap`, but {rhs} is not mapped again", ExMaps::map);
        def("xnoremap", map, Addr.NONE, "as `:xmap`, but {rhs} is not mapped again", ExMaps::map);
        def("onoremap", map, Addr.NONE, "as `:omap`, but {rhs} is not mapped again", ExMaps::map);
        def("inoremap", map, Addr.NONE, "as `:imap`, but {rhs} is not mapped again", ExMaps::map);
        def("cnoremap", map, Addr.NONE, "as `:cmap`, but {rhs} is not mapped again", ExMaps::map);
        def("unmap", BANG | map, Addr.NONE, "remove the mapping of {lhs} in Normal, Visual and Operator-pending mode (`!`: Insert and Command-line)", ExMaps::map);
        def("nunmap", map, Addr.NONE, "remove the Normal-mode mapping of {lhs}", ExMaps::map);
        def("vunmap", map, Addr.NONE, "remove the Visual-mode mapping of {lhs}", ExMaps::map);
        def("xunmap", map, Addr.NONE, "remove the Visual-mode mapping of {lhs}, as `:vunmap`", ExMaps::map);
        def("ounmap", map, Addr.NONE, "remove the Operator-pending mapping of {lhs}", ExMaps::map);
        def("iunmap", map, Addr.NONE, "remove the Insert-mode mapping of {lhs}", ExMaps::map);
        def("cunmap", map, Addr.NONE, "remove the command-line mapping of {lhs}", ExMaps::map);
        def("mapclear", BANG | EXTRA | TRLBAR, Addr.NONE, "remove every mapping of `:map` (`!`: of `:map!`)", ExMaps::mapclear);
        def("nmapclear", EXTRA | TRLBAR, Addr.NONE, "remove every Normal-mode mapping", ExMaps::mapclear);
        def("vmapclear", EXTRA | TRLBAR, Addr.NONE, "remove every Visual-mode mapping", ExMaps::mapclear);
        def("xmapclear", EXTRA | TRLBAR, Addr.NONE, "remove every Visual-mode mapping, as `:vmapclear`", ExMaps::mapclear);
        def("omapclear", EXTRA | TRLBAR, Addr.NONE, "remove every Operator-pending mapping", ExMaps::mapclear);
        def("imapclear", EXTRA | TRLBAR, Addr.NONE, "remove every Insert-mode mapping", ExMaps::mapclear);
        def("cmapclear", EXTRA | TRLBAR, Addr.NONE, "remove every command-line mapping", ExMaps::mapclear);
    }
```

- [ ] **Step 4: End waiting keys in the corpus runner, clear Vim's mappings in the recorder, and list the divergent case**

In `docs/vim-divergences.md`, replace:

```markdown
| ex-substitute.case | s expression is not available | Jasper has no expressions: \= in a substitute string fails with E319 (docs/vim-divergences.md) |
```

with:

```markdown
| ex-substitute.case | s expression is not available | Jasper has no expressions: \= in a substitute string fails with E319 (docs/vim-divergences.md) |
| mappings.case | nowait does not wait for a longer mapping | the recorder types every key at once, so Vim sees ab whole and never waits; Jasper gets the keys one at a time, as typing does |
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusRunner.java`, replace:

```java
        editor.messageLog().clear();
        for (KeyInput key : KeyInput.parse(c.keys())) editor.feed(key);
        List<String> output = null;
        if (c.output()) {
```

with:

```java
        editor.messageLog().clear();
        for (KeyInput key : KeyInput.parse(c.keys())) editor.feed(key);
        // The recorder's <Cmd> after the keys ends a mapping they only started: they resolve as if the time ran out.
        if (editor.typeahead().waiting()) editor.timeoutNow();
        List<String> output = null;
        if (c.output()) {
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java`, replace:

```java
        lines.add("scriptencoding utf-8");
        lines.add("set nocompatible");
        lines.add("function! JasperRecord()");
        lines.add("  let g:jasper = {'text': getline(1, '$'), 'line': line('.'), 'col': charcol('.'), 'mode': mode(1), 'regs': {}, 'visual': []}");
```

with:

```java
        lines.add("scriptencoding utf-8");
        lines.add("set nocompatible");
        // Vim on macOS maps <D-v> and Visual <BS> even with -u NONE; Jasper starts with no mappings.
        lines.add("mapclear | mapclear!");
        lines.add("function! JasperRecord()");
        lines.add("  let g:jasper = {'text': getline(1, '$'), 'line': line('.'), 'col': charcol('.'), 'mode': mode(1), 'regs': {}, 'visual': []}");
```

- [ ] **Step 5: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains the mapping commands.

- [ ] **Step 6: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.TypeaheadTest'
```

Expected: 17 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1675 tests pass, 90 of them `mappings.case`'s (one divergent).

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

Expected: `git status` lists only `mappings.case` (`??`), unchanged from what you created: Vim agrees with every expectation, and the other files are unchanged.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v3a
git add plugins/editor/src/main/java/dev/jasper/editor/vim/ExMaps.java plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java plugins/editor/src/test/java/dev/jasper/editor/vim/ExMapsTest.java plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusRunner.java plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java plugins/editor/src/test/resources/vim/corpus/mappings.case docs/editor-vim-reference.md docs/vim-divergences.md
git commit -m "Add the mapping commands: :map, :noremap, :unmap, :mapclear and their forms

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: Macros

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/Macros.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/MacrosTest.java`, `plugins/editor/src/test/resources/vim/corpus/macros.case`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Registers.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Typeahead.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/ui/VimBar.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ui/VimBarTest.java`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Consumes: `KeyCodes` (Task 1); `Typeahead.insert`, `mapped()` and `Remap` (Task 2).
- Produces: `Macros` (`recording()`, `qTakesNoRegister()`, `nextKey()`, `typed(key)`, `doneExecuting()`) with the Normal commands `q{char}` and `@{char}` and the Visual ones `q{char}` and `@{char}`; `Registers.record(name, text)`; `Vim.macros()`; `Machine.pausedReplace()`; `VimEditor.modeText()` (public), which the Vim bar shows.

`q` reads no register while recording (it stops) or while a register runs (it does nothing), as normal.c special-cases it. The typeahead records typed keys as it takes them and tells `Macros` when a register's keys are done.

- [ ] **Step 1: Write the failing tests and the corpus cases**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/MacrosTest.java`:

```java
package dev.jasper.editor.vim;

import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Recording what the corpus cannot type: input-method text, and the mode text the Vim bar shows (spec 4.3). */
class MacrosTest {
    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private static VimEditor editor(String text) {
        var area = new VimTextArea();
        area.setText(text);
        area.setCaretPosition(0);
        return new VimEditor(new Vim(new VimHost(r -> Optional.empty(), (r, t) -> { })), area, EditorDefaults.VIM);
    }

    @Test void recordingIsShownWithTheMode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor("abc\n");
            type(editor, "qa");
            assertThat(editor.modeText()).isEqualTo("recording @a");
            type(editor, "i");
            assertThat(editor.modeText()).isEqualTo("-- INSERT --recording @a");
            type(editor, "<Esc>:set nosmd<CR>");
            assertThat(editor.modeText()).as("without 'showmode' too").isEqualTo("recording @a");
            type(editor, "q");
            assertThat(editor.modeText()).isEmpty();
        });
    }

    @Test void inputMethodTextIsRecorded() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor("abc\n");
            type(editor, "qai");
            editor.typedByInputMethod("日本");
            type(editor, "<Esc>q");
            assertThat(editor.vim().registers().get('a').orElseThrow().text()).isEqualTo("i日本\u001b");
            assertThat(editor.buffer().line(0)).as("the text area inserts what the input method commits").isEqualTo("abc");
            type(editor, "@a");
            assertThat(editor.buffer().line(0)).isEqualTo("日本abc");
        });
    }
}
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/ui/VimBarTest.java`, replace:

```java
            type(editor, "<Esc>i");
            assertThat(bar.statusText()).as("a detached bar no longer follows the editor").isEmpty();
        });
    }
```

with:

```java
            type(editor, "<Esc>i");
            assertThat(bar.statusText()).as("a detached bar no longer follows the editor").isEmpty();
        });
    }

    @Test void showsRecordingAfterTheMode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("abc\n");
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            type(editor, "qq");
            assertThat(bar.statusText()).isEqualTo("recording @q");
            type(editor, "i");
            assertThat(bar.statusText()).isEqualTo("-- INSERT --recording @q");
            type(editor, "<Esc>q");
            assertThat(bar.statusText()).isEmpty();
        });
    }
```

Create `plugins/editor/src/test/resources/vim/corpus/macros.case`:

```text
# Macros: q records typed keys into a register, @ runs a register's keys.

=== q records keys and @ runs them
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: qadwjq@a
registers: a
--- vim 9.1
expect-text:
  two three
  five six
  seven eight nine
  ten eleven twelve
expect-cursor: 3,1
expect-mode: normal
expect-register a: c "dwj"

=== @ with a count runs the register that many times
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: qaxq3@a
registers: a
--- vim 9.1
expect-text:
  two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-register a: c "x"

=== @@ runs the last register again
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: qaxjq@a@@
registers: a
--- vim 9.1
expect-text:
  ne two three
  our five six
  even eight nine
  ten eleven twelve
expect-cursor: 4,1
expect-mode: normal
expect-register a: c "xj"

=== q with an upper-case name appends
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: qaxqqAjq@a
registers: a
--- vim 9.1
expect-text:
  ne two three
  our five six
  seven eight nine
  ten eleven twelve
expect-cursor: 3,1
expect-mode: normal
expect-register a: c "xj"

=== q with an upper-case name appends to a linewise register
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
register a: l "yy\n"
keys: qAxq
registers: a
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-register a: l "yyx\n"

=== q with an upper-case name on an empty register
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: qAxq
registers: a
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-register a: c "x"

=== q with a double quote records into register 0
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: yiwq"xq
registers: " 0 -
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "o"
expect-register 0: c "x"
expect-register -: c "o"

=== q records into a digit register
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: q5xq@5
registers: 5
--- vim 9.1
expect-text:
  e two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-register 5: c "x"

=== recording keeps the unnamed register
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: yiwqaxq
registers: " a 0
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "o"
expect-register a: c "x"
expect-register 0: c "one"

=== special keys are recorded as Vim codes
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: qai1<BS>2<Left><Esc><C-Left><End>q:registers a<CR>
output: yes
--- vim 9.1
expect-text:
  2one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,14
expect-mode: normal
expect-output:
  Type Name Content
    c  "a   i1<80>kb2<80>kl^[<80><fd>U<80>@7

=== shifted and function keys are recorded as Vim codes
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: qa<S-Tab><S-Up><F10><F11><Home><PageUp><Esc>q:registers a<CR>
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  Type Name Content
    c  "a   <80>kB<80><fd>^D<80>k;<80>F1<80>kh<80>kP^[

=== a recursive macro runs until a command fails
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: qaqqajx@aq@a
registers: a
--- vim 9.1
expect-text:
  a1
  2
  3
  4
  5
  6
expect-cursor: 6,1
expect-mode: normal
expect-register a: c "jx@a"

=== a failing command ends the macro
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: qafzxjq@a
--- vim 9.1
expect-text:
  1
  b2
  c3
  d4
  e5
  f6
expect-cursor: 2,1
expect-mode: normal

=== @ with a count stops at the first failure
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: qajxq9@a
--- vim 9.1
expect-text:
  a1
  2
  3
  4
  5
  6
expect-cursor: 6,1
expect-mode: normal

=== @: runs the last command line again
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :s/$/!/<CR>j@:
registers: :
--- vim 9.1
expect-text:
  a1!
  b2!
  c3
  d4
  e5
  f6
expect-cursor: 2,1
expect-mode: normal
expect-register :: c "s/$/!/"

=== @@ after @: runs the command line again
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :s/$/!/<CR>j@:j@@
--- vim 9.1
expect-text:
  a1!
  b2!
  c3!
  d4
  e5
  f6
expect-cursor: 3,1
expect-mode: normal

=== @: with a count
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: :s/$/!/<CR>j3@:
--- vim 9.1
expect-text:
  a1!
  b2!!!
  c3
  d4
  e5
  f6
expect-cursor: 2,1
expect-mode: normal

=== @: in Visual mode drops the range it would add again
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: Vj:s/$/!/<CR>jjVj@:
--- vim 9.1
expect-text:
  a1!
  b2!
  c3
  d4!
  e5!
  f6
expect-cursor: 5,1
expect-mode: normal

=== @: with no command line fails
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: @:
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E30: No previous command line

=== @@ with no register run fails
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: @@
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E748: No previously used register

=== @ with an invalid register fails
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: @%
output: yes
--- vim 9.1
expect-text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E354: Invalid register name: '%'

=== @ with an empty register fails
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: @bx
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== @ of the black hole register does nothing
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: @_x
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== a yanked line runs as keys with a line break
text:
  x
  abc
  def
cursor: 1,1
keys: "ayyj@a
registers: a
--- vim 9.1
expect-text:
  x
  bc
  def
expect-cursor: 3,1
expect-mode: normal
expect-register a: l "x\n"

=== a register of several lines runs with line breaks
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
register a: c "x\nx"
keys: @a
--- vim 9.1
expect-text:
  ne two three
  our five six
  seven eight nine
  ten eleven twelve
expect-cursor: 2,1
expect-mode: normal

=== @. runs the last inserted text
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: ix<Esc>w@.
--- vim 9.1
expect-text:
  xone wo three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,6
expect-mode: normal

=== q in a macro does nothing
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
register a: c "xqx"
keys: @a
--- vim 9.1
expect-text:
  e two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== a macro's keys are mapped
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
register a: c "x"
keys: :nmap x dd<CR>@a
--- vim 9.1
expect-text:
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== CTRL-O @ runs the register in Normal mode then inserts
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
register a: c "wdw"
keys: i<C-o>@aZ<Esc>
--- vim 9.1
expect-text:
  one Zthree
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,5
expect-mode: normal

=== CTRL-O q stops recording without the CTRL-O
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: qaiab<C-o>qcd<Esc>
registers: a
--- vim 9.1
expect-text:
  abcdone two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,4
expect-mode: normal
expect-register a: c "iab"

=== CTRL-O q starts recording in Insert mode
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: iab<C-o>qacd<Esc>q
registers: a
--- vim 9.1
expect-text:
  abcdone two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,4
expect-mode: normal
expect-register a: c "cd\u001b"

=== q colon is not a register
divergent: Jasper has no command-line window; q: fails (docs/vim-divergences.md)
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: q:x
--- expected
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== q with an invalid register fails
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: q%x
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== q in Visual mode stops recording
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: qavlqd
registers: a
--- vim 9.1
expect-text:
  e two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-register a: c "vl"

=== q in Visual mode starts recording
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: vqalqd
registers: a
--- vim 9.1
expect-text:
  e two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal
expect-register a: c "l"

=== a macro that inserts text
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: qaA!<Esc>jq@a@@
--- vim 9.1
expect-text:
  one two three!
  four five six!
  seven eight nine!
  ten eleven twelve
expect-cursor: 4,17
expect-mode: normal

=== u undoes all of a macro's changes
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: qaxxq@au
--- vim 9.1
expect-text:
  e two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== u undoes all of a macro's changes over lines
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
keys: qaxjxq@au
--- vim 9.1
expect-text:
  1
  2
  c3
  d4
  e5
  f6
expect-cursor: 2,1
expect-mode: normal

=== dot after a macro repeats its last change
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: qaxwq@a.
--- vim 9.1
expect-text:
  ne wo hree
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,7
expect-mode: normal

=== gqq still formats while q records
text:
  a b
  c
cursor: 1,1
keys: qagqqq
registers: a
--- vim 9.1
expect-text:
  a b
  c
expect-cursor: 1,1
expect-mode: normal
expect-register a: c "gqq"

=== q after an operator fails
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
keys: dqx
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,1
expect-mode: normal

=== a macro running a command line
text:
  a1
  b2
  c3
  d4
  e5
  f6
cursor: 1,1
register a: c ":s/$/!/\r"
keys: @a
--- vim 9.1
expect-text:
  a1!
  b2
  c3
  d4
  e5
  f6
expect-cursor: 1,1
expect-mode: normal

=== a macro run from a macro
text:
  one two three
  four five six
  seven eight nine
  ten eleven twelve
cursor: 1,1
register a: c "x@b"
register b: c "w"
keys: @a
--- vim 9.1
expect-text:
  ne two three
  four five six
  seven eight nine
  ten eleven twelve
expect-cursor: 1,4
expect-mode: normal
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest'
```

Expected: FAIL: the tests do not compile (`VimEditor.modeText()` does not exist).

- [ ] **Step 3: Write `Macros`**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Macros.java`:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;

/**
 * Macros (spec 4.3), global as in Vim: {@code q{reg}} records the keys typed, before any mapping, into a register as
 * text, and {@code @{reg}} puts a register's text in front of the typeahead to run as keys, mapped. Ported from
 * register.c (do_record, stuff_yank, do_execreg, put_in_typebuf) and getchar.c (gotchars, get_recorded,
 * check_end_reg_executing).
 */
final class Macros {
    /** The register being recorded into, or 0 (Vim's reg_recording). */
    private char recording;
    private final List<KeyInput> recorded = new ArrayList<>();
    /** How many keys the last key that ran took from what was typed (last_recorded_len): {@code q} drops them. */
    private int lastRecorded;
    /** The register {@code @@} runs (execreg_lastc), or 0. */
    private char lastExecuted;
    /** The register running, or 0 (reg_executing); {@code q} does nothing while one runs. */
    private char executing;

    static void register(VimCommands commands) {
        commands.actionWithChar("q", "`q{reg}` records typed keys into a register; `q` again stops (`{A-Z}` appends)", false, Macros::q);
        commands.visual("q", "record into a register, or stop recording, as `q`", true, false, Macros::q);
        commands.actionWithChar("@", "run a register's keys, [count] times; `@@` the last one run, `@:` the last command line", false, Macros::at);
        commands.visual("@", "run a register's keys in Visual mode, as `@`", true, false, Macros::at);
    }

    /** @return the register being recorded into, or 0 */
    char recording() { return recording; }

    /** @return whether {@code q} reads no register: while recording (it stops) or while a register runs (it does nothing) */
    boolean qTakesNoRegister() { return recording != 0 || executing != 0; }

    /** A key is about to be taken from the typeahead (vgetc): the keys it takes from what was typed are counted anew. */
    void nextKey() { lastRecorded = 0; }

    /** A typed key was taken from the typeahead (gotchars): it is recorded. */
    void typed(KeyInput key) {
        if (recording == 0) return;
        recorded.add(key);
        lastRecorded++;
    }

    /** check_end_reg_executing: no keys of the register are left. */
    void doneExecuting() { executing = 0; }

    /** nv_record: {@code q{reg}} starts recording, {@code q} stops it; while a register runs {@code q} does nothing. */
    private static void q(Context c) {
        Macros macros = c.vim.macros();
        if (macros.recording != 0) {
            macros.stop(c.editor);
            return;
        }
        if (macros.executing != 0 || c.argument == null) return;
        int name = c.argument.plainChar();
        if (name == ':' || name == '/' || name == '?') throw VimFailure.bell();
        // Registers 0-9, a-z, A-Z and " may record.
        if (name < 0 || name >= 128 || !Character.isLetterOrDigit(name) && name != '"') throw VimFailure.bell();
        macros.recording = (char) name;
        macros.recorded.clear();
    }

    /**
     * do_record's stop: the keys recorded, without those that stopped it (and a {@code <C-o>} before them in Insert
     * mode), go into the register.
     */
    private void stop(VimEditor editor) {
        char name = recording;
        recording = 0;
        int end = Math.max(0, recorded.size() - lastRecorded);
        if (end > 0 && editor.mode() == Mode.INSERT_NORMAL && recorded.get(end - 1).is("<C-o>")) end--;
        editor.vim().registers().record(name, KeyCodes.text(recorded.subList(0, end)));
        recorded.clear();
    }

    /** nv_at: the register runs [count] times; the first failure stops. */
    private static void at(Context c) {
        Macros macros = c.vim.macros();
        for (int i = 0; i < c.count; i++) macros.execute(c.editor, c.argument.plainChar(), i == 0);
    }

    /**
     * do_execreg: the register's text goes in front of the typeahead, mapped ({@code @:}'s command line is not);
     * lines are joined by line breaks, and a linewise register ends with one.
     *
     * @param first whether this is the first time for {@code @}'s count, when Insert mode's {@code <C-o>} is handled
     */
    private void execute(VimEditor editor, int name, boolean first) {
        if (name == '@') {
            if (lastExecuted == 0) throw new VimFailure("E748: No previously used register");
            name = lastExecuted;
        }
        if (name < 0 || name == '%' || name == '#' || name == '=' || !valid((char) name))
            throw new VimFailure("E354: Invalid register name: '" + (name < 0 ? "" : Character.toString(name)) + "'");
        lastExecuted = (char) name;
        if (name == '_') return;
        Registers registers = editor.vim().registers();
        Typeahead typeahead = editor.typeahead();
        if (name == ':') {
            String line = registers.get(':').map(Registers.Content::text).orElse("");
            if (line.isEmpty()) throw new VimFailure("E30: No previous command line");
            if (editor.mode().visual() && line.startsWith("'<,'>")) line = line.substring(5);
            StringBuilder escaped = new StringBuilder(":");
            for (char ch : line.toCharArray()) {
                if (ch >= 1 && ch < 0x20) escaped.append((char) 0x16);
                escaped.append(ch);
            }
            reedit(editor, first);
            typeahead.insert(KeyCodes.keys(escaped + "\n"), Typeahead.Remap.NONE);
            return;
        }
        if (name == '.') {
            String inserted = registers.lastInserted();
            if (inserted.endsWith("\033")) inserted = inserted.substring(0, inserted.length() - 1);
            if (inserted.isEmpty()) throw new VimFailure("E29: No inserted text yet");
            reedit(editor, first);
            typeahead.insert(KeyCodes.keys(inserted), Typeahead.Remap.YES);
            return;
        }
        // get_yank_register: a name that is not a yank register reads register 0.
        char register = Character.isLetterOrDigit(name) || "\"-*+".indexOf(name) >= 0 ? (char) name : '0';
        Registers.Content content = registers.get(register).orElseThrow(VimFailure::bell);
        reedit(editor, first);
        String text = String.join("\n", content.lines()) + (content.type() == Registers.Type.LINE ? "\n" : "");
        typeahead.insert(KeyCodes.keys(text), Typeahead.Remap.YES);
        executing = (char) name;
    }

    /** valid_yank_reg for reading. */
    private static boolean valid(char name) {
        return name < 128 && Character.isLetterOrDigit(name) || "/.:\"-_*+".indexOf(name) >= 0;
    }

    /**
     * put_reedit_in_typebuf: after {@code i_CTRL-O} the register runs in Normal mode and Insert mode comes back after
     * it, with an {@code i} (or {@code R}) behind its keys, not mapped.
     */
    private static void reedit(VimEditor editor, boolean first) {
        if (!first || editor.mode() != Mode.INSERT_NORMAL) return;
        editor.typeahead().insert(List.of(KeyInput.of(editor.machine().pausedReplace() ? 'R' : 'i')), Typeahead.Remap.NONE);
        editor.mode(Mode.NORMAL);
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Registers.java`, replace:

```java
    void lastInserted(String text) { lastInserted = text; }

    /** The {@code :} register: the last command line run. */
    void lastCommand(String text) { lastCommand = text; }
```

with:

```java
    void lastInserted(String text) { lastInserted = text; }

    /**
     * stuff_yank: a recorded macro goes into a register as one charwise line; an upper-case name appends it to the
     * register's last line, keeping the register's type. {@code q"} records into register 0. The unnamed register
     * keeps pointing where it did.
     */
    void record(char name, String text) {
        char register = name == '"' ? '0' : Character.toLowerCase(name);
        Content old = values.get(register);
        Content added = Content.of(text, Type.CHAR);
        if (!Character.isUpperCase(name) || old == null) {
            values.put(register, added);
            return;
        }
        List<String> lines = new ArrayList<>(old.lines());
        lines.set(lines.size() - 1, lines.getLast() + added.lines().getFirst());
        lines.addAll(added.lines().subList(1, added.lines().size()));
        values.put(register, new Content(lines, old.type(), old.width()));
    }

    /** The {@code :} register: the last command line run. */
    void lastCommand(String text) { lastCommand = text; }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, replace:

```java
    private final KeyHandlers handlers = new KeyHandlers();
    private final Mappings mappings = new Mappings();
    private final VimCommands commands = VimCommands.standard();
    private Consumer<String> coverage = id -> { };
```

with:

```java
    private final KeyHandlers handlers = new KeyHandlers();
    private final Mappings mappings = new Mappings();
    private final Macros macros = new Macros();
    private final VimCommands commands = VimCommands.standard();
    private Consumer<String> coverage = id -> { };
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`, replace:

```java
    /** @return the handler table */
    public KeyHandlers handlers() { return handlers; }

    /** @return the key mappings */
```

with:

```java
    /** @return the handler table */
    public KeyHandlers handlers() { return handlers; }

    /** @return macro recording and execution */
    Macros macros() { return macros; }

    /** @return the key mappings */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, replace:

```java
        ViewCommands.register(commands);
        Ex.register(commands);
        return commands;
    }
```

with:

```java
        ViewCommands.register(commands);
        Ex.register(commands);
        Macros.register(commands);
        return commands;
    }
```

- [ ] **Step 4: Record typed keys, let `q` take no register while recording, and list the divergent case**

In `docs/vim-divergences.md`, replace:

```markdown
| mappings.case | nowait does not wait for a longer mapping | the recorder types every key at once, so Vim sees ab whole and never waits; Jasper gets the keys one at a time, as typing does |
```

with:

```markdown
| mappings.case | nowait does not wait for a longer mapping | the recorder types every key at once, so Vim sees ab whole and never waits; Jasper gets the keys one at a time, as typing does |
| macros.case | q colon is not a register | Jasper has no command-line window; q: fails (docs/vim-divergences.md) |
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Typeahead.java`, replace:

```java
        tookTyped = false;
        int depth = 0;
        while (!keys.isEmpty()) {
            Key head = keys.getFirst();
```

with:

```java
        tookTyped = false;
        int depth = 0;
        Macros macros = editor.vim().macros();
        macros.nextKey();
        if (!mapped()) macros.doneExecuting();
        while (!keys.isEmpty()) {
            Key head = keys.getFirst();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Typeahead.java`, replace:

```java
    }

    /** The first {@code n} keys are taken. */
    private void consume(int n) {
        for (int i = 0; i < n; i++) if (keys.removeFirst().typed()) tookTyped = true;
    }
}
```

with:

```java
    }

    /** The first {@code n} keys are taken; the typed ones are recorded when a macro is being recorded. */
    private void consume(int n) {
        Macros macros = editor.vim().macros();
        for (int i = 0; i < n; i++) {
            Key key = keys.removeFirst();
            if (!key.typed()) continue;
            macros.typed(key.key());
            tookTyped = true;
        }
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java

    boolean replaying() { return replaying; }

    /** @return whether the key means something now, for the handler rule */
```

with:

```java

    boolean replaying() { return replaying; }

    /** @return whether {@code i_CTRL-O} was typed in Replace mode, so Replace mode comes back after the command */
    boolean pausedReplace() { return resumeReplace; }

    /** @return whether the key means something now, for the handler rule */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
            throw Context.fail();
        }
        if (entry.needsChar()) {
            VimCommands.Entry waiting = entry;
            argument = arg -> {
```

with:

```java
            throw Context.fail();
        }
        if (needsChar(entry)) {
            VimCommands.Entry waiting = entry;
            argument = arg -> {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
            throw Context.fail();
        }
        if (entry.needsChar()) {
            argument = arg -> {
                record.add(arg);
```

with:

```java
            throw Context.fail();
        }
        if (needsChar(entry)) {
            argument = arg -> {
                record.add(arg);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
    void visualChange(Visual.Redo redo) {
        visualRedo = redo;
    }
```

with:

```java
    void visualChange(Visual.Redo redo) {
        visualRedo = redo;
    }

    /** Whether the command reads a key as its argument; {@code q} does not while recording or while a register runs. */
    private boolean needsChar(VimCommands.Entry entry) {
        return entry.needsChar() && !(entry.keys().equals("q") && vim.macros().qTakesNoRegister());
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java

    /** Text an input method committed in Insert mode, recorded for {@code .}. */
    void typedByInputMethod(String text) { insert.typedByInputMethod(text); }

    /** @return the mode */
```

with:

```java

    /** Text an input method committed in Insert mode, recorded for {@code .}. */
    void typedByInputMethod(String text) {
        text.codePoints().forEach(cp -> vim.macros().typed(KeyInput.of(cp)));
        insert.typedByInputMethod(text);
    }

    /** @return the mode */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    /** @param listener no longer runs */
    public void removeListener(Runnable listener) { listeners.remove(listener); }

    /** @return whether the Vim bar shows the mode ('showmode') */
```

with:

```java
    /** @param listener no longer runs */
    public void removeListener(Runnable listener) { listeners.remove(listener); }

    /**
     * @return what the Vim bar shows when there is no message: the mode with 'showmode', then {@code recording @q}
     *     while a macro is recorded (showmode)
     */
    public String modeText() {
        String label = showmode() ? mode().label() : "";
        char recording = vim.macros().recording();
        return recording == 0 ? label : label + "recording @" + recording;
    }

    /** @return whether the Vim bar shows the mode ('showmode') */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/ui/VimBar.java`, replace:

```java
        } else {
            String message = editor.message();
            String text = message.isEmpty() ? editor.showmode() ? editor.mode().label() : "" : message;
            // A space keeps the bar one line high when there is nothing to show.
            status.setText(text.isEmpty() ? " " : text);
```

with:

```java
        } else {
            String message = editor.message();
            String text = message.isEmpty() ? editor.modeText() : message;
            // A space keeps the bar one line high when there is nothing to show.
            status.setText(text.isEmpty() ? " " : text);
```

- [ ] **Step 5: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains `q{char}` and `@{char}` in Normal and Visual mode.

- [ ] **Step 6: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.TypeaheadTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.KeyCodesTest'
```

Expected: 23 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 175 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1718 tests pass, 43 of them `macros.case`'s (one divergent).

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

Expected: `git status` lists only `macros.case` (`??`), unchanged from what you created.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v3a
git add plugins/editor/src/main/java/dev/jasper/editor/vim/Macros.java plugins/editor/src/main/java/dev/jasper/editor/vim/Registers.java plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java plugins/editor/src/main/java/dev/jasper/editor/vim/Typeahead.java plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java plugins/editor/src/main/java/dev/jasper/editor/vim/ui/VimBar.java plugins/editor/src/test/java/dev/jasper/editor/vim/MacrosTest.java plugins/editor/src/test/java/dev/jasper/editor/vim/ui/VimBarTest.java plugins/editor/src/test/resources/vim/corpus/macros.case docs/editor-vim-reference.md docs/vim-divergences.md
git commit -m "Add macros: q records typed keys, @ runs a register

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 5: Documentation, the spec amendments and the final check

**Files:**
- Modify: `docs/vim-divergences.md`, `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec

- [ ] **Step 1: List the differences**

Append to `docs/vim-divergences.md`:

```markdown
- **One typeahead per tab.** Keys wait, and mappings and macros insert theirs, in the tab's own typeahead; a mapping or
  macro that selects another tab (`:bn`, `gt`) goes on in the tab it started in. Vim's typeahead is global.
- **`q:`, `q/` and `q?` fail.** Jasper has no command-line window.
- **Mapping arguments.** `<buffer>`, `<expr>` and `<script>` are refused with `Not supported in Jasper: <expr>`.
  `<silent>` is accepted and changes nothing, since Jasper never echoes the command line a mapping types. `<Leader>`
  and `<LocalLeader>` are a backslash until the vimrc brings `let mapleader`.
- **No Select, Lang-Arg or terminal mappings.** `:smap`, `:lmap`, `:tmap` and their forms fail with E319; `:vmap` and
  `:map` still include Select mode, so their listings show `v` as Vim's do.
- **Listings show Jasper's keys.** Jasper reads `<C-i>`, `<C-m>` and `<C-[>` as `<Tab>`, `<CR>` and `<Esc>`, and
  `:map` lists them so; `<M-x>` is listed as `<M-x>`, where Vim shows the character with its top bit set (`ø`).
- **Recorded keys.** A macro keeps named keys in Vim's codes (`:reg` shows `<BS>` as `<80>kb`). A key with modifiers
  that Vim has no code of its own for is kept with Vim's modifier prefix (`<80><fc>`), which Vim would write only for
  some of them.
- **CTRL-C in a mapping or macro that runs on** is noticed every 1000 keys, when Jasper looks for it among the window's
  pending key events; it cannot read keys while the others run.
- **`<nowait>` in the corpus.** The recorder types a case's keys all at once, so Vim sees a longer mapping's keys
  together and `<nowait>` never matters there; Jasper's corpus runner gives the keys one at a time, as typing does, and
  the case that shows the difference is divergent.
```

- [ ] **Step 2: Describe macros and mappings in the Editor guide**

In `docs/editor.md`, replace:

```markdown
- Not yet: macros, a vimrc, and emulated plugins such as surround and commentary. They follow in later releases. An
  uppercase mark (`mA`) set in one tab takes you back to that tab (`'A`).
```

with:

```markdown
- Macros: `qa` … `q` records the keys you type into register `a` (the strip shows `recording @a`), `@a` runs them,
  `5@a` five times, `@@` the last register again and `@:` the last command line; `qA` appends to `a`. A macro stops
  at the first command that fails, so one that ends with `@a` runs to the end of the file; `u` undoes everything a
  macro or mapping changed at once. Ctrl+C stops a macro or mapping that runs on.
- Mappings: `:nmap`, `:vmap`, `:xmap`, `:omap`, `:imap`, `:cmap`, `:map` and `:map!`, their `noremap` forms, and the
  `unmap` and `mapclear` forms, as in Vim (`:nnoremap <Space>w :w<CR>`); without a right-hand side they list the
  mappings. Keys that start a longer mapping wait 'timeoutlen' (1000 ms) for the rest, and the strip shows them.
  `<nowait>`, `<unique>`, `<Plug>`, `<Nop>` and `<Leader>` (a backslash for now) work as in Vim. A key that starts a
  mapping is Vim's, even a ⌘ key.
- Not yet: a vimrc, `sethandler` lines and emulated plugins such as surround and commentary. They follow in later
  releases. An uppercase mark (`mA`) set in one tab takes you back to that tab (`'A`).
```

- [ ] **Step 3: Add the GUI checks for the user**

Append to `docs/editor-verification.md`:

```markdown

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
```

- [ ] **Step 4: Record the state**

In `docs/STATUS.md`, replace:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V2b** (branch `claude/vim-v2b`, plan
```

with:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V3a** (branch `claude/vim-v3a`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v3a.md`, written 2026-09-28): macros (`q`, `@`, `@@`, `{n}@`, `@:`, `@.`,
  `recording @q` in the Vim bar) and the mapping commands typed on the command line (`:map :nmap :vmap :xmap :omap
  :imap :cmap :map!`, their `noremap`, `unmap` and `mapclear` forms, and the listings), through a per-tab typeahead
  ported from Vim's getchar.c: recursion and E223 ('maxmapdepth'), 'timeout' and 'timeoutlen' on a Swing timer,
  `<nowait>`, `<unique>`, `<Plug>`, `<Nop>`, `<Leader>`; a failing command ends a mapping or macro, a mapping's changes
  undo as one step, and Ctrl+C stops one that runs on. Every file in the plan was compiled and run against Vim 9.1
  before the plan was written: 1714 corpus cases (133 new in `macros.case` and `mappings.case`, 2 divergent).
  - Not done: executing the plan; GUI checks 71–79 of the [checklist](editor-verification.md) (user-run); plan V3b
    (the vimrc, `sethandler` lines, `let mapleader`, `<Action>`); V4; push.
- **Editor Vim mode, plan V2b** (branch `claude/vim-v2b`, plan
```

- [ ] **Step 5: Amend the spec**

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
Plan V2b (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v2b.md`) amends sections 3.6, 5.4, 6.1, 6.2 and 12, as marked **Amended (V2b)**.
```

with:

```markdown
Plan V2b (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v2b.md`) amends sections 3.6, 5.4, 6.1, 6.2 and 12, as marked **Amended (V2b)**. Plan V3a (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v3a.md`) amends sections 2.3, 4.3, 6.3, 7.2, 12 and 13, as marked **Amended (V3a)**.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
aborts the rest of the macro, mapping or `:normal` being executed, as Vim does.
```

with:

```markdown
aborts the rest of the macro, mapping or `:normal` being executed, as Vim does.

**Amended (V3a):** keys wait in a typeahead, one per tab, ported from Vim's getchar.c: a failing command drops the keys that were not typed (a mapping's, a macro's), typed keys stay. The keys of one mapping or macro change the text as one undo step, as Vim closes an undo step only when it takes a typed key. Ctrl+C typed while a mapping or macro runs stops it, as in Vim; Jasper looks for it among the window's pending key events every 1000 keys. At the end of a `:normal` argument keys waiting for a mapping resolve at once.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- Mappings and the vimrc: section 7.
```

with:

```markdown
- Mappings and the vimrc: section 7.

**Amended (V3a):** plan V3a delivers this section. `@` also works in Visual mode, `@.` runs the last inserted text and `@_` nothing, as in Vim; `q:`, `q/` and `q?` fail (there is no command-line window); a register's text keeps named keys in Vim's codes (`<80>kb` for `<BS>`); `recording @q` follows the mode in the Vim bar, with or without 'showmode'.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
the accepted-and-ignored vimrc options come with the vimrc in V3.
```

with:

```markdown
the accepted-and-ignored vimrc options come with the vimrc in V3. **Amended (V3a):** `timeout`, `timeoutlen` and `maxmapdepth` (`mmd`, Vim's, default 1000) come with the mappings in V3a; the rest with the vimrc in V3b.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- `<Action>(id)` in a right-hand side runs an Editor command by id through `VimHost` (as IdeaVim's `<Action>(…)`); `:action {id}` does the same from the command line; `:actionlist [pattern]` lists ids in the output panel.
```

with:

```markdown
- `<Action>(id)` in a right-hand side runs an Editor command by id through `VimHost` (as IdeaVim's `<Action>(…)`); `:action {id}` does the same from the command line; `:actionlist [pattern]` lists ids in the output panel.

**Amended (V3a):** plan V3a delivers the mapping commands typed on the command line; `<Action>(id)`, `:action` and `:actionlist` move to V3b with the vimrc. `<Leader>` and `<LocalLeader>` are a backslash until V3b reads `let mapleader`. `<silent>` and `<special>` are accepted; `<silent>` changes nothing, since Jasper never echoes the command line a mapping types. `:smap`, `:lmap`, `:tmap` and their forms fail with E319 (no Select, Lang-Arg or terminal mode); `:vmap` and `:map` still include Select mode for their listings. Listings follow Vim's layout and order (`n  x           * dd`). The recorder clears Vim's own mappings (`<D-v>` on macOS) before each case.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
**Amended (V2a):** the Ex classes (`Ex`, `ExTable`, `ExCommand`, `ExInfo`, `ExLines`, `ExSubstitute`, `ExGlobal`) live in `dev.jasper.editor.vim`, not `vim.ex`: they use the engine's package-private API, and a separate package would need a public API or a dependency cycle. `vim.ui` depends on `vim` only. **Amended (V2b):** `VimHost.Documents` (a record of callbacks) is how the engine reaches the Editor's tabs; `ExFiles`, `Completion` and `LineNumbers` join `dev.jasper.editor.vim`, and there is no `RelativeGutter`.
```

with:

```markdown
**Amended (V2a):** the Ex classes (`Ex`, `ExTable`, `ExCommand`, `ExInfo`, `ExLines`, `ExSubstitute`, `ExGlobal`) live in `dev.jasper.editor.vim`, not `vim.ex`: they use the engine's package-private API, and a separate package would need a public API or a dependency cycle. `vim.ui` depends on `vim` only. **Amended (V2b):** `VimHost.Documents` (a record of callbacks) is how the engine reaches the Editor's tabs; `ExFiles`, `Completion` and `LineNumbers` join `dev.jasper.editor.vim`, and there is no `RelativeGutter`. **Amended (V3a):** `Typeahead`, `Mappings`, `ExMaps`, `Macros` and `KeyCodes` join `dev.jasper.editor.vim`; there is no `KeyNotation` (`KeyInput` reads the notation).
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- **Plan V3: macros and mappings.** Section 4.3 and section 7, with `timeoutlen` (section 2.3) and `sethandler` lines.
```

with:

```markdown
- **Plan V3: macros and mappings.** Section 4.3 and section 7, with `timeoutlen` (section 2.3) and `sethandler` lines. **Amended (V3a):** split in two by the user on 2026-09-28. Plan V3a: macros, the typeahead and the mapping commands typed on the command line, with 'timeout', 'timeoutlen' and 'maxmapdepth'. Plan V3b: the vimrc (section 7.1), `sethandler` lines, `let mapleader`, `<Action>(id)`, `:action`, `:actionlist` and the vimrc options.
```

- [ ] **Step 6: Run everything**

Run:

```bash
./gradlew check
```

Expected: BUILD SUCCESSFUL (4,311 tests, 0 failures, 0 errors, 5 skipped).

- [ ] **Step 7: Check source hygiene**

Run the snippet in `AGENTS.md` ("Source hygiene"). Expected: no output.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v3a
git add docs
git commit -m "Document Vim mode V3a: macros and mappings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
