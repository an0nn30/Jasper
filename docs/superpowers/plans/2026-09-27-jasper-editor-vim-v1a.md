# Editor Vim mode, plan V1a: engine foundation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Vim-mode editing in the Editor, without Visual mode or search: a headless Vim engine on RSyntaxTextArea (keys, Normal/Insert/Replace modes, counts, motions, text objects, operators, registers, undo in Vim's steps, `.`), the Vim bar, the setting, toggle and status item, and a differential corpus that proves the engine against real Vim 9.

**Architecture:** A new package `dev.jasper.editor.vim` holds the engine. `VimTextArea` (every tab's text area) hands key events to an attached `VimEditor` before Swing's key bindings; `Machine` parses Normal-mode commands against the `VimCommands` table; motions and text objects are ports of Vim's C (search.c, textobject.c, normal.c, ops.c, edit.c). `VimUndoManager` makes each Vim change one undo step. The corpus (`src/test/resources/vim/corpus/*.case`) is recorded from real Vim by `recordVimCorpus` and replayed by `VimCorpusTest` in `check`.

**Tech Stack:** Java 25 (JBR), Swing, RSyntaxTextArea 3.6.0, JUnit 6 and AssertJ, Vim 9 (only for `recordVimCorpus`).

**Spec:** `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md` (plan V1a of section 13). Read sections 1–5, 9–13.

**Status:** Written 2026-09-27 on branch `claude/vim-mode` (worktree `.worktrees/vim-mode`). Every source file, test and corpus case below was compiled and run before the plan was written: against Vim 9.1, the 464 corpus cases (462 recorded, 2 divergent) and the 195 other Editor tests passed, and every command in the table had a case. Executed 2026-09-27; task reviews found three engine bugs, fixed beyond the plan's code in 543c7d89 (see docs/STATUS.md), and added three corpus cases.

## Deviations from the spec (approved scope, adjusted in this plan)

Task 8 writes these into the spec.

- **Moved to plan V1b:** the screen-line motions `H M L gj gk g0 g^ g$ gm`, which need the view's layout; `gi` (it uses the `'^` mark); `<C-g>` and `ga` (messages beyond the Vim bar's one line); and blockwise forced motions (`d<C-v>j`), which need blockwise registers. Keeping a selection as Visual mode when Vim turns on (spec 1.2) and entering Visual mode from a mouse drag (spec 3.1) also wait for V1b; until then, turning Vim on drops the selection. **Moved to plan V2:** `ZZ` and `ZQ` (they are `:x` and `:q!`). **Moved to plan V3:** `timeoutlen`, which only matters once mappings exist, and reading `sethandler` from the vimrc (V1a has the handler table and its default rule).
- **'autoindent' starts on** in Jasper's tabs, like the Editor's own auto-indent; spec 3.8 listed only `tabstop`, `shiftwidth`, `expandtab`, `wrap` and `number` as Editor-derived. Vim's `-u NONE` default is off, and the corpus uses Vim's defaults.
- **Vim 9 facts the corpus recorded**, followed here: a delete into a named register also fills register 1 (and the unnamed register points at register 1); `nrformats` defaults to `bin,octal,hex` with `-u NONE` (spec 4.1 said `bin,hex`; it matters from V1b's `<C-a>`); a doubled operator (`dd`, `gUU`) puts the cursor on the line's first non-blank before it runs, which decides where `u` puts the cursor.
- **The recorder** runs each case in its own `vim -u NONE -i NONE -N -n --not-a-term`, editing a temporary file rather than using `setline()`, so `u` cannot undo the case's setup. It reads the mode with `mode(1)` from a `<Cmd>` mapping appended to the keys. Spec 11.1's format gains `register x:` (a register's starting content) and `registers:` (the registers compared); `marks:`, `clipboard:`, `vimrc:`, `ext:` and `expect-message:` arrive with the plans that need them.
- **Typing in Insert mode:** printable characters go through RSyntaxTextArea's typing actions (spec 2.2), while `<CR>`, `<BS>`, `<Tab>`, `<Del>` and the Ctrl keys are Vim's own, so 'autoindent', 'expandtab' and 'softtabstop' behave as in Vim.

## Global Constraints

- Java 25 on the JetBrains Runtime; always `./gradlew`, never a system Gradle. Work in `.worktrees/vim-mode` on `claude/vim-mode`; check `git branch --show-current` before every commit (other sessions switch branches in the main checkout). Never commit on `main`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.
- The Editor plugin compiles against the SDK only (`verifyPluginArchitecture`). `dev.jasper.editor.vim` may use only the JDK and RSyntaxTextArea; `dev.jasper.editor.vim.ui` adds `dev.jasper.editor.vim`. No SDK change.
- No interface without two real implementations: the functional interfaces in `VimCommands` have dozens of lambda implementations; nothing else is an interface.
- Source hygiene: never put raw control, private-use or unpaired surrogate characters in `.java` files; use escapes (`"\uD83D"`, `(char) 27`). Corpus `.case` files are UTF-8 resources and may hold any text.
- **Do not launch the GUI.** GUI checks go in `docs/editor-verification.md` for the user.
- Engine code runs on the event thread in the app. Tests that type through a `VimEditor` run inside `SwingUtilities.invokeAndWait`, because the caret only follows document edits made on the event thread.
- The corpus is the authority on Vim behaviour. If a recorded case and the engine disagree, fix the engine; mark a case `divergent:` only for a documented, deliberate difference (Task 8).
- `vim` must be on `PATH` for `recordVimCorpus` (the dev Mac has Vim 9.1 at `/usr/bin/vim`). `check` never runs Vim.

## File structure

New, under `plugins/editor/src/main/java/dev/jasper/editor/vim/`:

| File | Responsibility |
|---|---|
| `KeyInput.java` | a key in Vim notation: parse, format, normalize |
| `KeyTranslator.java` | AWT key events to `KeyInput` (pressed vs typed, macOS Option, AltGr) |
| `Mode.java` | the reported mode, its corpus id and Vim-bar label |
| `Pos.java` | a mutable line/column, as Vim's `pos_T` |
| `Text.java` | one line's graphemes, display columns and indents |
| `Buffer.java` | Vim's line model over the document; every edit goes through it |
| `Keywords.java` | 'iskeyword' and Vim's character classes |
| `Walk.java` | Vim's `inc`/`dec`/`cls` and word movers (ported) |
| `Options.java`, `EditorDefaults.java` | options with global and local values; the Editor's defaults |
| `VimHost.java` | what the engine needs from the Editor (the clipboard, in V1a) |
| `Registers.java` | registers and Vim's rules for which a yank or delete writes |
| `VimUndoManager.java` | undo steps: RSyntaxTextArea's grouping with Vim off, Vim's with it on |
| `Target.java`, `Span.java`, `VimFailure.java`, `Context.java` | a motion's landing, an operator's text, a failed command, one command's inputs |
| `VimCommands.java` | the command table: parser lookups, coverage ids, the reference |
| `Vim.java`, `KeyHandlers.java` | global state; the handler table (`sethandler`) |
| `Machine.java` | the Normal-mode parser, counts, registers, operators, `.` recording, undo steps |
| `Motions.java`, `Matching.java`, `TextObjects.java`, `Operators.java`, `NormalCommands.java` | the commands (ported) |
| `InsertMode.java` | Insert and Replace mode (ported from edit.c) |
| `VimEditor.java` | Vim in one tab: mode, cursor rules, handler rule, undo, look |
| `VimTextArea.java` | every tab's text area; key events reach Vim first |
| `package-info.java` | package rules |

New, `plugins/editor/src/main/java/dev/jasper/editor/vim/ui/`: `VimBar.java`, `package-info.java`.
New, `plugins/editor/src/main/java/dev/jasper/editor/ui/`: `SettingsFile.java` (writes `vim = …`), `SystemClipboard.java` (`"+`, `"*`).
Changed: `EditorTab`, `EditorWindow`, `StatusLine`, `EditorSettings`, `ui/package-info.java`, `settings.toml`, `plugins/editor/build.gradle.kts`.
Tests: `plugins/editor/src/test/java/dev/jasper/editor/vim/` (unit tests, corpus harness `CorpusCase`, `CorpusFile`, `CorpusRunner`, `Json`, `RecordCorpus`, `VimCorpusTest`, `VimDocsTest`), `vim/ui/VimBarTest`, `ui/SettingsFileTest`, and updates to five existing Editor tests. Corpus: `plugins/editor/src/test/resources/vim/corpus/*.case` (13 files).
Docs: `docs/vim-divergences.md`, `docs/editor-vim-reference.md` (generated), `docs/editor.md`, `docs/README.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec.

---

### Task 1: Keys

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java`, `KeyTranslator.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/KeyInputTest.java`, `KeyTranslatorTest.java`

**Interfaces:**
- Produces: `public record KeyInput(String key, int modifiers)` with `SHIFT`, `CTRL`, `ALT`, `META`, `NAMES`, `of(int)`, `named(String)`, `ctrl(char)`, `isChar()`, `plainChar()`, `is(String)`, `notation()`, `static List<KeyInput> parse(String)`, `static String format(List<KeyInput>)`, package-private `static KeyInput normalize(String, int)`. `KeyTranslator.pressed(int keyCode, int modifiersEx, boolean mac)` and `typed(int codePoint, int modifiersEx, boolean mac)` return `Optional<KeyInput>`.

- [ ] **Step 1: Write the failing tests**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/KeyInputTest.java`:

```java
package dev.jasper.editor.vim;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyInputTest {
    @Test void readsCharactersAndNamedKeys() {
        assertThat(KeyInput.parse("d2w")).containsExactly(KeyInput.of('d'), KeyInput.of('2'), KeyInput.of('w'));
        assertThat(KeyInput.parse("ihi<Esc>")).containsExactly(KeyInput.of('i'), KeyInput.of('h'), KeyInput.of('i'), KeyInput.named("Esc"));
        assertThat(KeyInput.parse("<C-w><S-Tab><M-x><D-s>")).containsExactly(
            new KeyInput("w", KeyInput.CTRL), new KeyInput("Tab", KeyInput.SHIFT), new KeyInput("x", KeyInput.ALT), new KeyInput("s", KeyInput.META));
    }

    @Test void namesAreCaseInsensitiveAndHaveAliases() {
        assertThat(KeyInput.parse("<esc><ESCAPE><Return><enter><bs><Del><space><lt><Bar><Bslash><f12>")).containsExactly(
            KeyInput.named("Esc"), KeyInput.named("Esc"), KeyInput.named("CR"), KeyInput.named("CR"), KeyInput.named("BS"),
            KeyInput.named("Del"), KeyInput.of(' '), KeyInput.of('<'), KeyInput.of('|'), KeyInput.of('\\'), KeyInput.named("F12"));
    }

    @Test void normalizesCtrlAndShiftAsVimDoes() {
        assertThat(KeyInput.parse("<C-W>")).containsExactly(new KeyInput("w", KeyInput.CTRL));
        assertThat(KeyInput.parse("<C-[>")).containsExactly(KeyInput.named("Esc"));
        assertThat(KeyInput.parse("<C-i><C-m>")).containsExactly(KeyInput.named("Tab"), KeyInput.named("CR"));
        assertThat(KeyInput.parse("<S-a>")).containsExactly(KeyInput.of('A'));
        assertThat(KeyInput.parse("<C-S-a>")).containsExactly(new KeyInput("a", KeyInput.CTRL));
        assertThat(KeyInput.ctrl('[')).isEqualTo(KeyInput.named("Esc"));
    }

    @Test void aLessThanThatNamesNothingIsLiteral() {
        assertThat(KeyInput.parse("<a>")).containsExactly(KeyInput.of('<'), KeyInput.of('a'), KeyInput.of('>'));
        assertThat(KeyInput.parse("a<b")).containsExactly(KeyInput.of('a'), KeyInput.of('<'), KeyInput.of('b'));
        assertThat(KeyInput.parse("<")).containsExactly(KeyInput.of('<'));
    }

    @Test void supplementaryCharactersAreOneKey() {
        assertThat(KeyInput.parse("😀x")).containsExactly(KeyInput.of(0x1F600), KeyInput.of('x'));
    }

    @Test void formatsBackToTheSameKeys() {
        for (String notation : List.of("d2w", "ihello<Esc>", "<C-w>", "<S-Tab>", "<lt>", "a b", "<C-Space>", "<D-s>", "\"ayy", "f|"))
            assertThat(KeyInput.parse(KeyInput.format(KeyInput.parse(notation)))).isEqualTo(KeyInput.parse(notation));
        assertThat(KeyInput.of('<').notation()).isEqualTo("<lt>");
        assertThat(new KeyInput(" ", KeyInput.CTRL).notation()).isEqualTo("<C-Space>");
        assertThat(KeyInput.named("Esc").is("<esc>")).isTrue();
        assertThat(KeyInput.of('a').plainChar()).isEqualTo('a');
        assertThat(new KeyInput("a", KeyInput.CTRL).plainChar()).isEqualTo(-1);
    }

    @Test void rejectsWhatIsNotAKey() {
        assertThatThrownBy(() -> new KeyInput("ab", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KeyInput("", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
```

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/KeyTranslatorTest.java`:

```java
package dev.jasper.editor.vim;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class KeyTranslatorTest {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK, ALT = InputEvent.ALT_DOWN_MASK, SHIFT = InputEvent.SHIFT_DOWN_MASK, META = InputEvent.META_DOWN_MASK;

    @Test void namedKeysComeFromKeyPressed() {
        assertThat(KeyTranslator.pressed(KeyEvent.VK_ESCAPE, 0, true)).contains(KeyInput.named("Esc"));
        assertThat(KeyTranslator.pressed(KeyEvent.VK_TAB, SHIFT, false)).contains(new KeyInput("Tab", KeyInput.SHIFT));
        assertThat(KeyTranslator.pressed(KeyEvent.VK_F5, 0, false)).contains(KeyInput.named("F5"));
        assertThat(KeyTranslator.pressed(KeyEvent.VK_KP_LEFT, 0, false)).contains(KeyInput.named("Left"));
    }

    @Test void ctrlCombinationsComeFromKeyPressed() {
        assertThat(KeyTranslator.pressed(KeyEvent.VK_W, CTRL, true)).contains(new KeyInput("w", KeyInput.CTRL));
        assertThat(KeyTranslator.pressed(KeyEvent.VK_W, CTRL | SHIFT, false)).contains(new KeyInput("w", KeyInput.CTRL));
        assertThat(KeyTranslator.pressed(KeyEvent.VK_OPEN_BRACKET, CTRL, true)).contains(KeyInput.named("Esc"));
        assertThat(KeyTranslator.pressed(KeyEvent.VK_6, CTRL, false)).contains(new KeyInput("^", KeyInput.CTRL));
    }

    @Test void plainLettersArriveAsTypedCharacters() {
        assertThat(KeyTranslator.pressed(KeyEvent.VK_A, 0, false)).isEmpty();
        assertThat(KeyTranslator.pressed(KeyEvent.VK_A, SHIFT, false)).isEmpty();
        assertThat(KeyTranslator.typed('A', SHIFT, false)).contains(KeyInput.of('A'));
    }

    @Test void altIsAKeyOnLinuxAndWindowsButTypesCharactersOnMac() {
        assertThat(KeyTranslator.pressed(KeyEvent.VK_X, ALT, false)).contains(new KeyInput("x", KeyInput.ALT));
        assertThat(KeyTranslator.pressed(KeyEvent.VK_E, ALT, true)).isEmpty();
        assertThat(KeyTranslator.typed('é', ALT, true)).contains(KeyInput.of(0xe9));
        assertThat(KeyTranslator.typed('x', ALT, false)).isEmpty();
    }

    @Test void altGraphTypesCharacters() {
        assertThat(KeyTranslator.pressed(KeyEvent.VK_Q, CTRL | ALT, false)).isEmpty();
        assertThat(KeyTranslator.typed('@', CTRL | ALT, false)).contains(KeyInput.of('@'));
    }

    @Test void commandKeysCarryMeta() {
        assertThat(KeyTranslator.pressed(KeyEvent.VK_S, META, true)).contains(new KeyInput("s", KeyInput.META));
        assertThat(KeyTranslator.typed('s', META, true)).isEmpty();
    }

    @Test void controlCharactersAreNotTyped() {
        assertThat(KeyTranslator.typed(0x17, CTRL, true)).isEmpty();
        assertThat(KeyTranslator.typed(KeyEvent.CHAR_UNDEFINED, 0, false)).isEqualTo(Optional.empty());
        assertThat(KeyTranslator.typed(0x7f, 0, false)).isEmpty();
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest'
```

Expected: compilation fails: `KeyInput` and `KeyTranslator` do not exist.

- [ ] **Step 3: Write the implementation**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyInput.java`:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One key as Vim names it (spec 2.1): a character, or a named key such as {@code Esc}, with modifiers. A character
 * never carries Shift (it is part of the character), and Ctrl with a letter always holds the lower-case letter, so
 * equal keys are equal records.
 *
 * @param key one code point as a string, or a name from {@link #NAMES}
 * @param modifiers {@link #SHIFT}, {@link #CTRL}, {@link #ALT} and {@link #META} combined
 */
public record KeyInput(String key, int modifiers) {
    /** Shift, only on named keys ({@code <S-Tab>}). */
    public static final int SHIFT = 1;
    /** Ctrl ({@code <C-w>}). */
    public static final int CTRL = 2;
    /** Alt or Meta ({@code <M-x>}); never produced on macOS, where Option types characters. */
    public static final int ALT = 4;
    /** ⌘ on macOS ({@code <D-s>}). */
    public static final int META = 8;
    /** The named keys, in their canonical spelling. */
    public static final List<String> NAMES = List.of("Esc", "CR", "Tab", "BS", "Del", "Insert", "Home", "End", "PageUp", "PageDown",
        "Up", "Down", "Left", "Right", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12");

    /** Checks the key is one code point or a known name. */
    public KeyInput {
        Objects.requireNonNull(key, "key");
        if (key.isEmpty() || key.codePointCount(0, key.length()) != 1 && !NAMES.contains(key))
            throw new IllegalArgumentException("Not a key: " + key);
    }

    /**
     * @param codePoint a character
     * @return that character, unmodified
     */
    public static KeyInput of(int codePoint) { return new KeyInput(Character.toString(codePoint), 0); }

    /**
     * @param name one of {@link #NAMES}
     * @return that key, unmodified
     */
    public static KeyInput named(String name) { return new KeyInput(name, 0); }

    /**
     * @param letter a letter or punctuation character
     * @return Ctrl with it, normalized ({@code ctrl('[')} is {@code <Esc>})
     */
    public static KeyInput ctrl(char letter) { return normalize(String.valueOf(letter), CTRL); }

    /** @return whether this is a character rather than a named key */
    public boolean isChar() { return key.codePointCount(0, key.length()) == 1; }

    /** @return the character when this is a character without modifiers, otherwise -1 */
    public int plainChar() { return isChar() && modifiers == 0 ? key.codePointAt(0) : -1; }

    /**
     * @param notation one key in Vim notation, such as {@code <C-w>}
     * @return whether this is that key
     */
    public boolean is(String notation) {
        List<KeyInput> keys = parse(notation);
        return keys.size() == 1 && keys.getFirst().equals(this);
    }

    /** @return Vim notation: {@code a}, {@code <lt>}, {@code <C-w>}, {@code <S-Tab>} */
    public String notation() {
        if (modifiers == 0 && isChar()) return key.equals("<") ? "<lt>" : key;
        StringBuilder out = new StringBuilder("<");
        if ((modifiers & SHIFT) != 0) out.append("S-");
        if ((modifiers & CTRL) != 0) out.append("C-");
        if ((modifiers & ALT) != 0) out.append("M-");
        if ((modifiers & META) != 0) out.append("D-");
        out.append(switch (key) {
            case "<" -> "lt";
            case " " -> "Space";
            default -> key;
        });
        return out.append('>').toString();
    }

    @Override public String toString() { return notation(); }

    /**
     * Reads Vim notation. A {@code <} that does not start a key name is the character {@code <}.
     *
     * @param notation keys such as {@code d2w} or {@code ihello<Esc>}
     * @return the keys
     */
    public static List<KeyInput> parse(String notation) {
        List<KeyInput> keys = new ArrayList<>();
        int i = 0;
        while (i < notation.length()) {
            if (notation.charAt(i) == '<') {
                int close = notation.indexOf('>', i + 1);
                if (close > i + 1) {
                    KeyInput key = bracket(notation.substring(i + 1, close));
                    if (key != null) {
                        keys.add(key);
                        i = close + 1;
                        continue;
                    }
                }
            }
            int codePoint = notation.codePointAt(i);
            keys.add(of(codePoint));
            i += Character.charCount(codePoint);
        }
        return keys;
    }

    /**
     * @param keys keys
     * @return their Vim notation, which {@link #parse} reads back to the same keys
     */
    public static String format(List<KeyInput> keys) {
        StringBuilder out = new StringBuilder();
        for (KeyInput key : keys) out.append(key.notation());
        return out.toString();
    }

    /** @return the key a {@code <…>} body names, or null when it names none (the {@code <} is then literal) */
    private static KeyInput bracket(String body) {
        int modifiers = 0;
        String rest = body;
        while (rest.length() > 2 && rest.charAt(1) == '-') {
            int flag = switch (Character.toUpperCase(rest.charAt(0))) {
                case 'S' -> SHIFT;
                case 'C' -> CTRL;
                case 'M', 'A' -> ALT;
                case 'D' -> META;
                default -> -1;
            };
            if (flag < 0) break;
            modifiers |= flag;
            rest = rest.substring(2);
        }
        String name = rest.toLowerCase(Locale.ROOT);
        String key = switch (name) {
            case "esc", "escape" -> "Esc";
            case "cr", "return", "enter" -> "CR";
            case "tab" -> "Tab";
            case "bs", "backspace" -> "BS";
            case "del", "delete" -> "Del";
            case "insert", "ins" -> "Insert";
            case "home" -> "Home";
            case "end" -> "End";
            case "pageup" -> "PageUp";
            case "pagedown" -> "PageDown";
            case "up" -> "Up";
            case "down" -> "Down";
            case "left" -> "Left";
            case "right" -> "Right";
            case "space" -> " ";
            case "lt" -> "<";
            case "bar" -> "|";
            case "bslash" -> "\\";
            default -> {
                if (name.matches("f([1-9]|1[0-2])")) yield "F" + name.substring(1);
                yield modifiers != 0 && rest.codePointCount(0, rest.length()) == 1 ? rest : null;
            }
        };
        return key == null ? null : normalize(key, modifiers);
    }

    /**
     * @param key a character or a name from {@link #NAMES}
     * @param modifiers the modifiers held
     * @return the key as Vim sees it: Shift folded into characters, Ctrl letters lower case, and {@code <C-[>},
     *     {@code <C-i>} and {@code <C-m>} read as {@code <Esc>}, {@code <Tab>} and {@code <CR>}
     */
    static KeyInput normalize(String key, int modifiers) {
        if (key.codePointCount(0, key.length()) == 1) {
            if ((modifiers & SHIFT) != 0) {
                key = key.toUpperCase(Locale.ROOT);
                modifiers &= ~SHIFT;
            }
            if ((modifiers & CTRL) != 0) {
                key = key.toLowerCase(Locale.ROOT);
                if (modifiers == CTRL) {
                    switch (key) {
                        case "[" -> { return named("Esc"); }
                        case "i" -> { return named("Tab"); }
                        case "m" -> { return named("CR"); }
                        default -> { }
                    }
                }
            }
        }
        return new KeyInput(key, modifiers);
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyTranslator.java`:

```java
package dev.jasper.editor.vim;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;

/**
 * Turns AWT key events into {@link KeyInput}s (spec 2.1). Named keys and Ctrl, Alt and ⌘ combinations come from
 * {@code KEY_PRESSED}; characters come from {@code KEY_TYPED}, so layouts, Shift and macOS Option characters are right.
 * It takes the event's parts rather than the event, so tests need no component.
 */
final class KeyTranslator {
    private KeyTranslator() { }

    /**
     * @param keyCode the event's key code
     * @param modifiersEx the event's extended modifiers
     * @param mac whether this is macOS, where Option composes characters instead of acting as Alt
     * @return the key for a {@code KEY_PRESSED} event, or empty when the key arrives as a {@code KEY_TYPED} character
     */
    static Optional<KeyInput> pressed(int keyCode, int modifiersEx, boolean mac) {
        int modifiers = modifiers(modifiersEx, mac);
        String name = switch (keyCode) {
            case KeyEvent.VK_ESCAPE -> "Esc";
            case KeyEvent.VK_ENTER -> "CR";
            case KeyEvent.VK_TAB -> "Tab";
            case KeyEvent.VK_BACK_SPACE -> "BS";
            case KeyEvent.VK_DELETE -> "Del";
            case KeyEvent.VK_INSERT -> "Insert";
            case KeyEvent.VK_HOME -> "Home";
            case KeyEvent.VK_END -> "End";
            case KeyEvent.VK_PAGE_UP -> "PageUp";
            case KeyEvent.VK_PAGE_DOWN -> "PageDown";
            case KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> "Up";
            case KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> "Down";
            case KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT -> "Left";
            case KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT -> "Right";
            default -> keyCode >= KeyEvent.VK_F1 && keyCode <= KeyEvent.VK_F12 ? "F" + (keyCode - KeyEvent.VK_F1 + 1) : null;
        };
        if (name != null) return Optional.of(KeyInput.normalize(name, modifiers));
        if ((modifiers & (KeyInput.CTRL | KeyInput.ALT | KeyInput.META)) == 0) return Optional.empty();
        // AltGr (Ctrl+Alt on Windows) types characters such as @ on German layouts: those arrive as KEY_TYPED.
        if (altGraph(modifiersEx, mac)) return Optional.empty();
        String key = keyText(keyCode, modifiers);
        return key == null ? Optional.empty() : Optional.of(KeyInput.normalize(key, modifiers));
    }

    /**
     * @param codePoint the typed character (surrogate pairs already combined)
     * @param modifiersEx the event's extended modifiers
     * @param mac whether this is macOS
     * @return the character, or empty for control characters and for Ctrl, Alt and ⌘ combinations, which
     *     {@link #pressed} reported
     */
    static Optional<KeyInput> typed(int codePoint, int modifiersEx, boolean mac) {
        if (codePoint == KeyEvent.CHAR_UNDEFINED || codePoint < 0x20 || codePoint == 0x7f) return Optional.empty();
        int modifiers = modifiers(modifiersEx, mac);
        if (!altGraph(modifiersEx, mac) && (modifiers & (KeyInput.CTRL | KeyInput.ALT | KeyInput.META)) != 0) return Optional.empty();
        return Optional.of(KeyInput.of(codePoint));
    }

    static int modifiers(int modifiersEx, boolean mac) {
        int modifiers = 0;
        if ((modifiersEx & InputEvent.SHIFT_DOWN_MASK) != 0) modifiers |= KeyInput.SHIFT;
        if ((modifiersEx & InputEvent.CTRL_DOWN_MASK) != 0) modifiers |= KeyInput.CTRL;
        if (!mac && (modifiersEx & InputEvent.ALT_DOWN_MASK) != 0) modifiers |= KeyInput.ALT;
        if ((modifiersEx & InputEvent.META_DOWN_MASK) != 0) modifiers |= KeyInput.META;
        return modifiers;
    }

    private static boolean altGraph(int modifiersEx, boolean mac) {
        if ((modifiersEx & InputEvent.ALT_GRAPH_DOWN_MASK) != 0) return true;
        int both = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
        return !mac && (modifiersEx & both) == both;
    }

    /** @return the character a key code stands for with modifiers held, or null */
    private static String keyText(int keyCode, int modifiers) {
        if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) return String.valueOf((char) ('a' + keyCode - KeyEvent.VK_A));
        if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) {
            // Ctrl+6 is how keyboards type <C-^>.
            if (keyCode == KeyEvent.VK_6 && (modifiers & KeyInput.CTRL) != 0) return "^";
            return String.valueOf((char) ('0' + keyCode - KeyEvent.VK_0));
        }
        return switch (keyCode) {
            case KeyEvent.VK_OPEN_BRACKET -> "[";
            case KeyEvent.VK_CLOSE_BRACKET -> "]";
            case KeyEvent.VK_BACK_SLASH -> "\\";
            case KeyEvent.VK_SPACE -> " ";
            case KeyEvent.VK_MINUS -> "-";
            case KeyEvent.VK_EQUALS -> "=";
            case KeyEvent.VK_COMMA -> ",";
            case KeyEvent.VK_PERIOD -> ".";
            case KeyEvent.VK_SLASH -> "/";
            case KeyEvent.VK_SEMICOLON -> ";";
            case KeyEvent.VK_QUOTE -> "'";
            case KeyEvent.VK_BACK_QUOTE -> "`";
            case KeyEvent.VK_CIRCUMFLEX -> "^";
            default -> null;
        };
    }
}
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest'
```

Expected: 14 tests pass.

- [ ] **Step 5: Commit**

```bash
git add plugins/editor/src/main/java/dev/jasper/editor/vim plugins/editor/src/test/java/dev/jasper/editor/vim
git commit -m "Add Vim key notation and AWT key translation

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: The text model

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/Text.java`, `Pos.java`, `Buffer.java`, `Keywords.java`, `Walk.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/TextTest.java`, `BufferTest.java`, `KeywordsTest.java`

**Interfaces:**
- Produces: `Text` statics `next`, `previous`, `last`, `characters`, `column`, `width(int)`, `width(String,int,int,int)`, `virtcol`, `columnAt`, `firstNonBlank`, `blank`, `indent`, `makeIndent`. `final class Pos` (fields `line`, `col`; `copy`, `set`, `min`, `max`, comparable). `final class Buffer(Document)` with `close`, `document`, `text`, `length`, `count`, `line`, `start`, `end`, `offset(Pos)`, `offset(int,int)`, `lineOf`, `pos`, `lines(int,int)`, `insert`, `remove`, `replace`, `setLine`, `insertLines`, `deleteLines`. `Keywords.parse(String)`, `isWord`, `charClass`. `Walk(Buffer, Keywords)` with field `bigword` and Vim's `gchar`, `inc`, `dec`, `incl`, `decl`, `cls`, `lineEmpty`, `inIndent`, `fwdWord`, `bckWord`, `endWord`, `bckendWord`, `skipChars`, `backInLine`, `oneLeft`; `Walk.FAIL` is -1.

`Walk` is a line-by-line port of Vim's C; the corpus in Task 5 is its test. Keep the ported control flow as written (the `finished` flags stand for Vim's `goto finished`).

- [ ] **Step 1: Write the failing tests**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/TextTest.java`:

```java
package dev.jasper.editor.vim;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TextTest {
    @Test void stepsByGrapheme() {
        String combining = "café!";
        assertThat(Text.next(combining, 3)).isEqualTo(5);
        assertThat(Text.previous(combining, 5)).isEqualTo(3);
        String emoji = "a😀b";
        assertThat(Text.next(emoji, 1)).isEqualTo(3);
        assertThat(Text.previous(emoji, 3)).isEqualTo(1);
        assertThat(Text.last("abc")).isEqualTo(2);
        assertThat(Text.last("")).isZero();
        assertThat(Text.characters(combining, 5)).isEqualTo(4);
        assertThat(Text.column(combining, 4)).isEqualTo(5);
    }

    @Test void measuresDisplayColumnsWithTabsAndWideCharacters() {
        assertThat(Text.virtcol("a\tb", 2, 8)).isEqualTo(8);
        assertThat(Text.virtcol("日本x", 2, 8)).isEqualTo(4);
        assertThat(Text.columnAt("a\tb", 5, 8)).isEqualTo(1);
        assertThat(Text.columnAt("abc", 10, 8)).isEqualTo(3);
        assertThat(Text.width(0x0301)).isZero();
    }

    @Test void readsAndBuildsIndents() {
        assertThat(Text.firstNonBlank("  \tx")).isEqualTo(3);
        assertThat(Text.blank(" \t")).isTrue();
        assertThat(Text.indent("\t  x", 4)).isEqualTo(6);
        assertThat(Text.makeIndent(10, 4, false)).isEqualTo("\t\t  ");
        assertThat(Text.makeIndent(10, 4, true)).isEqualTo(" ".repeat(10));
        assertThat(Text.makeIndent(0, 4, false)).isEmpty();
    }
}
```

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/BufferTest.java`:

```java
package dev.jasper.editor.vim;

import java.util.List;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BufferTest {
    private static Buffer buffer(String text) throws Exception {
        var document = new PlainDocument();
        document.insertString(0, text, null);
        return new Buffer(document);
    }

    @Test void aFinalLineBreakEndsTheLastLine() throws Exception {
        Buffer b = buffer("a\nb\n");
        assertThat(b.count()).isEqualTo(2);
        assertThat(b.line(1)).isEqualTo("b");
        assertThat(b.end(1)).isEqualTo(3);
        assertThat(buffer("").count()).isEqualTo(1);
        assertThat(buffer("\n").line(0)).isEmpty();
        assertThat(buffer("a\n\n").count()).isEqualTo(2);
    }

    @Test void convertsBetweenOffsetsAndPositions() throws Exception {
        Buffer b = buffer("ab\ncd\n");
        assertThat(b.pos(4)).isEqualTo(new Pos(1, 1));
        assertThat(b.offset(new Pos(1, 1))).isEqualTo(4);
        assertThat(b.lineOf(6)).isEqualTo(1);
    }

    @Test void insertsAndDeletesWholeLinesKeepingTheFinalBreak() throws Exception {
        Buffer b = buffer("a\nb\n");
        b.insertLines(2, List.of("c"));
        assertThat(b.text()).isEqualTo("a\nb\nc\n");
        b.insertLines(0, List.of("z"));
        assertThat(b.text()).isEqualTo("z\na\nb\nc\n");
        b.deleteLines(3, 3);
        assertThat(b.text()).isEqualTo("z\na\nb\n");
        b.deleteLines(0, 0);
        assertThat(b.text()).isEqualTo("a\nb\n");
        b.deleteLines(0, 1);
        assertThat(b.text()).isEmpty();
        Buffer bare = buffer("x");
        bare.insertLines(1, List.of("y"));
        assertThat(bare.text()).isEqualTo("x\ny");
    }

    @Test void seesEditsMadeOutsideIt() throws Exception {
        var document = new PlainDocument();
        document.insertString(0, "one\n", null);
        Buffer b = new Buffer(document);
        assertThat(b.line(0)).isEqualTo("one");
        document.insertString(0, "zero\n", null);
        assertThat(b.count()).isEqualTo(2);
        b.close();
    }
}
```

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/KeywordsTest.java`:

```java
package dev.jasper.editor.vim;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeywordsTest {
    @Test void readsVimsDefault() {
        Keywords k = Keywords.parse("@,48-57,_,192-255");
        assertThat(k.isWord('a')).isTrue();
        assertThat(k.isWord('Z')).isTrue();
        assertThat(k.isWord('5')).isTrue();
        assertThat(k.isWord('_')).isTrue();
        assertThat(k.isWord('-')).isFalse();
        assertThat(k.isWord(0xe9)).isTrue();
        assertThat(k.charClass(' ')).isZero();
        assertThat(k.charClass('.')).isEqualTo(1);
        assertThat(k.charClass('a')).isEqualTo(2);
        assertThat(k.charClass(0x1F600)).isEqualTo(3);
        assertThat(k.charClass(0x65e5)).isEqualTo(0x4e00);
    }

    @Test void readsRangesExclusionsAndCharacters() {
        Keywords k = Keywords.parse("a-c,-,^b");
        assertThat(k.isWord('a')).isTrue();
        assertThat(k.isWord('b')).isFalse();
        assertThat(k.isWord('-')).isTrue();
        assertThat(Keywords.parse("@-@").isWord('@')).isTrue();
        assertThatThrownBy(() -> Keywords.parse("300")).hasMessageContaining("E474");
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.KeywordsTest'
```

Expected: compilation fails: `Text`, `Buffer` and `Keywords` do not exist.

- [ ] **Step 3: Write the implementation**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Text.java`:

```java
package dev.jasper.editor.vim;

import java.text.BreakIterator;

/** Pure helpers over one line's text: character boundaries, display columns and indents. */
final class Text {
    private Text() { }

    /**
     * @param line a line
     * @param col a boundary in it
     * @return the next character boundary (a grapheme: a surrogate pair or a letter with its combining marks is one
     *     character), or the line's length at its end
     */
    static int next(String line, int col) {
        if (col >= line.length()) return line.length();
        // Nothing below U+0300 combines or is a surrogate, so the common case needs no BreakIterator.
        if (line.charAt(col) < 0x300 && (col + 1 == line.length() || line.charAt(col + 1) < 0x300)) return col + 1;
        BreakIterator characters = BreakIterator.getCharacterInstance();
        characters.setText(line);
        int next = characters.following(col);
        return next == BreakIterator.DONE ? line.length() : next;
    }

    /**
     * @param line a line
     * @param col a boundary in it, or its length
     * @return the previous character boundary, or 0
     */
    static int previous(String line, int col) {
        if (col <= 0) return 0;
        col = Math.min(col, line.length());
        if (line.charAt(col - 1) < 0x300) return col - 1;
        BreakIterator characters = BreakIterator.getCharacterInstance();
        characters.setText(line);
        int previous = characters.preceding(col);
        return previous == BreakIterator.DONE ? 0 : previous;
    }

    /** @return the start of the last character, or 0 for an empty line */
    static int last(String line) { return previous(line, line.length()); }

    /** @return the number of characters (graphemes) before {@code col} */
    static int characters(String line, int col) {
        int count = 0;
        for (int i = 0; i < col && i < line.length(); i = next(line, i)) count++;
        return count;
    }

    /** @return the column of the {@code index}-th character (0-based), or the line's length */
    static int column(String line, int index) {
        int col = 0;
        for (int i = 0; i < index && col < line.length(); i++) col = next(line, col);
        return col;
    }

    /** @return the display width of a code point that is not a tab: 0 for combining marks, 2 for wide characters */
    static int width(int cp) {
        int type = Character.getType(cp);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.FORMAT) return 0;
        if (cp >= 0x1100 && (cp <= 0x115f || cp == 0x2329 || cp == 0x232a || cp >= 0x2e80 && cp <= 0xa4cf && cp != 0x303f
            || cp >= 0xac00 && cp <= 0xd7a3 || cp >= 0xf900 && cp <= 0xfaff || cp >= 0xfe30 && cp <= 0xfe6f
            || cp >= 0xff00 && cp <= 0xff60 || cp >= 0xffe0 && cp <= 0xffe6 || cp >= 0x1f300 && cp <= 0x1f64f
            || cp >= 0x1f900 && cp <= 0x1f9ff || cp >= 0x20000 && cp <= 0x3fffd)) return 2;
        return 1;
    }

    /** @return the display width of the character starting at {@code col}, which starts at display column {@code vcol} */
    static int width(String line, int col, int vcol, int tabstop) {
        if (line.charAt(col) == '\t') return tabstop - vcol % tabstop;
        int end = next(line, col), total = 0;
        for (int i = col; i < end; i += Character.charCount(line.codePointAt(i))) total += width(line.codePointAt(i));
        // A lone combining mark still takes a cell.
        return Math.max(total, 1);
    }

    /** @return the display column (0-based) at which the character at {@code col} starts */
    static int virtcol(String line, int col, int tabstop) {
        int vcol = 0;
        for (int i = 0; i < col && i < line.length(); i = next(line, i)) vcol += width(line, i, vcol, tabstop);
        return vcol;
    }

    /**
     * Vim's coladvance: the character covering display column {@code want}.
     *
     * @return its column, or the line's length when {@code want} is past the last character
     */
    static int columnAt(String line, int want, int tabstop) {
        int vcol = 0;
        for (int i = 0; i < line.length(); i = next(line, i)) {
            int width = width(line, i, vcol, tabstop);
            if (want < vcol + width) return i;
            vcol += width;
        }
        return line.length();
    }

    /** @return the column of the first character that is not a space or tab, or the line's length */
    static int firstNonBlank(String line) {
        int col = 0;
        while (col < line.length() && (line.charAt(col) == ' ' || line.charAt(col) == '\t')) col++;
        return col;
    }

    /** @return whether the line holds only spaces and tabs */
    static boolean blank(String line) { return firstNonBlank(line) == line.length(); }

    /** @return the display width of the line's leading spaces and tabs (Vim's get_indent) */
    static int indent(String line, int tabstop) { return virtcol(line, firstNonBlank(line), tabstop); }

    /** @return leading whitespace of display width {@code width}: tabs then spaces, or only spaces with 'expandtab' */
    static String makeIndent(int width, int tabstop, boolean expandtab) {
        if (width <= 0) return "";
        if (expandtab) return " ".repeat(width);
        return "\t".repeat(width / tabstop) + " ".repeat(width % tabstop);
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Pos.java`:

```java
package dev.jasper.editor.vim;

/**
 * A mutable position, as Vim's {@code pos_T}: a 0-based line and a 0-based column counted in UTF-16 units of that
 * line's text. The column may equal the line's length ("on the NUL" after the last character).
 */
final class Pos implements Comparable<Pos> {
    int line, col;

    Pos(int line, int col) {
        this.line = line;
        this.col = col;
    }

    Pos copy() { return new Pos(line, col); }

    void set(Pos other) {
        line = other.line;
        col = other.col;
    }

    @Override public int compareTo(Pos other) { return line != other.line ? Integer.compare(line, other.line) : Integer.compare(col, other.col); }

    @Override public boolean equals(Object other) { return other instanceof Pos pos && pos.line == line && pos.col == col; }

    @Override public int hashCode() { return line * 31 + col; }

    @Override public String toString() { return (line + 1) + "," + (col + 1); }

    static Pos min(Pos a, Pos b) { return a.compareTo(b) <= 0 ? a : b; }

    static Pos max(Pos a, Pos b) { return a.compareTo(b) >= 0 ? a : b; }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Buffer.java`:

```java
package dev.jasper.editor.vim;

import java.util.Arrays;
import java.util.List;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * The text as Vim sees it: lines without their newline, where a final newline ends the last line rather than
 * starting an empty one ({@code "a\nb\n"} is two lines, as a file is in Vim). An empty document is one empty line.
 * Offsets are the document's; lines and columns are 0-based. Every edit goes through the document, so the undo
 * manager sees it.
 */
final class Buffer {
    private final Document document;
    private final DocumentListener listener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { lines = null; }
        @Override public void removeUpdate(DocumentEvent event) { lines = null; }
        @Override public void changedUpdate(DocumentEvent event) { }
    };
    private String[] lines;
    private int[] starts;

    Buffer(Document document) {
        this.document = document;
        document.addDocumentListener(listener);
    }

    void close() { document.removeDocumentListener(listener); }

    Document document() { return document; }

    /** @return the whole text, including a final newline */
    String text() {
        try { return document.getText(0, document.getLength()); }
        catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
    }

    int length() { return document.getLength(); }

    /** @return the number of lines, at least 1 */
    int count() { return lines().length; }

    String line(int line) { return lines()[line]; }

    /** @return the offset of the line's first character */
    int start(int line) {
        lines();
        return starts[line];
    }

    /** @return the offset just after the line's last character (its newline, or the end of the text) */
    int end(int line) { return start(line) + line(line).length(); }

    int offset(Pos pos) { return start(pos.line) + pos.col; }

    int offset(int line, int col) { return start(line) + col; }

    /** @return the line holding {@code offset}; an offset past the last line is on the last line */
    int lineOf(int offset) {
        lines();
        int found = Arrays.binarySearch(starts, offset);
        int line = found >= 0 ? found : -found - 2;
        return Math.max(0, Math.min(line, lines.length - 1));
    }

    Pos pos(int offset) {
        int line = lineOf(offset);
        return new Pos(line, Math.min(Math.max(0, offset - start(line)), line(line).length()));
    }

    /** @return lines {@code first} to {@code last}, inclusive */
    List<String> lines(int first, int last) { return List.of(Arrays.copyOfRange(lines(), first, last + 1)); }

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

    /** Replaces one line's text, leaving its newline. */
    void setLine(int line, String text) {
        if (!text.equals(line(line))) replace(start(line), end(line), text);
    }

    /** Inserts whole lines before {@code line}; {@code line == count()} appends after the last line. */
    void insertLines(int line, List<String> text) {
        String block = String.join("\n", text);
        if (line < count()) {
            insert(start(line), block + "\n");
            return;
        }
        int length = length();
        boolean newline = length > 0 && text().charAt(length - 1) == '\n';
        insert(length, newline ? block + "\n" : "\n" + block);
    }

    /** Deletes lines {@code first} to {@code last}, keeping a final newline; deleting every line empties the text. */
    void deleteLines(int first, int last) {
        int count = count();
        if (first == 0 && last == count - 1) remove(0, length());
        else if (last < count - 1) remove(start(first), start(last + 1));
        else remove(end(first - 1), end(last));
    }

    private String[] lines() {
        if (lines == null) {
            String text = text();
            if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
            lines = text.split("\n", -1);
            starts = new int[lines.length];
            for (int i = 1; i < lines.length; i++) starts[i] = starts[i - 1] + lines[i - 1].length() + 1;
        }
        return lines;
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Keywords.java`:

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

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Walk.java`:

```java
package dev.jasper.editor.vim;

/**
 * Vim's character walking, ported from misc2.c, search.c and textobject.c so motions and text objects match Vim:
 * {@code inc}, {@code dec}, {@code incl}, {@code decl}, {@code gchar}, {@code cls} and the word movers. Positions
 * are mutated in place, as Vim's are. Return codes are Vim's: 0 moved within the line, 1 moved to another line,
 * 2 moved onto the end of the line, -1 could not move.
 */
final class Walk {
    static final int FAIL = -1;

    private final Buffer buffer;
    private final Keywords keywords;
    /** Vim's cls_bigword: every non-blank is one class, for W, B, E and gE. */
    boolean bigword;

    Walk(Buffer buffer, Keywords keywords) {
        this.buffer = buffer;
        this.keywords = keywords;
    }

    Buffer buffer() { return buffer; }

    Keywords keywords() { return keywords; }

    /** @return the character at the position, or 0 on the end of the line (Vim's NUL) */
    int gchar(Pos pos) {
        String line = buffer.line(pos.line);
        return pos.col < line.length() ? line.codePointAt(pos.col) : 0;
    }

    int inc(Pos pos) {
        String line = buffer.line(pos.line);
        if (pos.col < line.length()) {
            pos.col = Text.next(line, pos.col);
            return pos.col < line.length() ? 0 : 2;
        }
        if (pos.line < buffer.count() - 1) {
            pos.line++;
            pos.col = 0;
            return 1;
        }
        return FAIL;
    }

    int dec(Pos pos) {
        if (pos.col > 0) {
            pos.col = Text.previous(buffer.line(pos.line), pos.col);
            return 0;
        }
        if (pos.line > 0) {
            pos.line--;
            pos.col = buffer.line(pos.line).length();
            return 1;
        }
        return FAIL;
    }

    /** inc, skipping the end of a non-empty line. */
    int incl(Pos pos) {
        int result = inc(pos);
        if (result >= 1 && pos.col > 0) result = inc(pos);
        return result;
    }

    /** dec, skipping the end of a non-empty line. */
    int decl(Pos pos) {
        int result = dec(pos);
        if (result == 1 && pos.col > 0) result = dec(pos);
        return result;
    }

    /** @return the character class at the position: 0 blank (or end of line), 1 punctuation, 2 and up words */
    int cls(Pos pos) {
        int c = gchar(pos);
        if (c == ' ' || c == '\t' || c == 0) return 0;
        int cls = keywords.charClass(c);
        return cls != 0 && bigword ? 1 : cls;
    }

    boolean lineEmpty(int line) { return buffer.line(line).isEmpty(); }

    /** @return whether only spaces and tabs precede column {@code pos.col + extra} (Vim's inindent) */
    boolean inIndent(Pos pos, int extra) { return Text.firstNonBlank(buffer.line(pos.line)) >= pos.col + extra; }

    /** Vim's fwd_word: moves to the start of the {@code count}-th next word. */
    boolean fwdWord(Pos pos, int count, boolean eol) {
        while (--count >= 0) {
            int sclass = cls(pos);
            boolean lastLine = pos.line == buffer.count() - 1;
            int i = inc(pos);
            if (i == FAIL || i >= 1 && lastLine) return false;
            if (i >= 1 && eol && count == 0) return true;
            if (sclass != 0) {
                while (cls(pos) == sclass) {
                    i = inc(pos);
                    if (i == FAIL || i >= 1 && eol && count == 0) return true;
                }
            }
            while (cls(pos) == 0) {
                if (pos.col == 0 && lineEmpty(pos.line)) break;
                i = inc(pos);
                if (i == FAIL || i >= 1 && eol && count == 0) return true;
            }
        }
        return true;
    }

    /** Vim's bck_word: moves to the start of the {@code count}-th previous word. */
    boolean bckWord(Pos pos, int count, boolean stop) {
        while (--count >= 0) {
            int sclass = cls(pos);
            if (dec(pos) == FAIL) return false;
            boolean finished = false;
            if (!stop || sclass == cls(pos) || sclass == 0) {
                while (cls(pos) == 0) {
                    if (pos.col == 0 && lineEmpty(pos.line)) {
                        finished = true;
                        break;
                    }
                    if (dec(pos) == FAIL) return true;
                }
                if (!finished && skipChars(pos, cls(pos), false)) return true;
            }
            if (!finished) inc(pos);
            stop = false;
        }
        return true;
    }

    /** Vim's end_word: moves to the end of the {@code count}-th word. */
    boolean endWord(Pos pos, int count, boolean stop, boolean empty) {
        while (--count >= 0) {
            int sclass = cls(pos);
            if (inc(pos) == FAIL) return false;
            boolean finished = false;
            if (cls(pos) == sclass && sclass != 0) {
                if (skipChars(pos, sclass, true)) return false;
            } else if (!stop || sclass == 0) {
                while (cls(pos) == 0) {
                    if (pos.col == 0 && lineEmpty(pos.line) && empty) {
                        finished = true;
                        break;
                    }
                    if (inc(pos) == FAIL) return false;
                }
                if (!finished && skipChars(pos, cls(pos), true)) return false;
            }
            if (!finished) dec(pos);
            stop = false;
        }
        return true;
    }

    /** Vim's bckend_word: moves to the end of the {@code count}-th previous word. */
    boolean bckendWord(Pos pos, int count, boolean eol) {
        while (--count >= 0) {
            int sclass = cls(pos);
            int i = dec(pos);
            if (i == FAIL) return false;
            if (eol && i == 1) return true;
            if (sclass != 0) {
                while (cls(pos) == sclass) {
                    i = dec(pos);
                    if (i == FAIL || eol && i == 1) return true;
                }
            }
            while (cls(pos) == 0) {
                if (pos.col == 0 && lineEmpty(pos.line)) break;
                i = dec(pos);
                if (i == FAIL || eol && i == 1) return true;
            }
        }
        return true;
    }

    /** @return true when the start or end of the buffer was hit */
    boolean skipChars(Pos pos, int cclass, boolean forward) {
        while (cls(pos) == cclass) if ((forward ? inc(pos) : dec(pos)) == FAIL) return true;
        return false;
    }

    /** Vim's back_in_line: back to the start of the run of characters of the cursor's class, within the line. */
    void backInLine(Pos pos) {
        int sclass = cls(pos);
        while (pos.col != 0) {
            dec(pos);
            if (cls(pos) != sclass) {
                inc(pos);
                break;
            }
        }
    }

    /** Vim's oneleft: false at the start of the line. */
    boolean oneLeft(Pos pos) {
        if (pos.col == 0) return false;
        pos.col = Text.previous(buffer.line(pos.line), pos.col);
        return true;
    }
}
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.KeywordsTest'
```

Expected: 9 tests pass.

- [ ] **Step 5: Commit**

```bash
git add plugins/editor/src/main/java/dev/jasper/editor/vim plugins/editor/src/test/java/dev/jasper/editor/vim
git commit -m "Add Vim's line model, display columns and character walking

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: Options and registers

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, `EditorDefaults.java`, `VimHost.java`, `Registers.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/OptionsTest.java`, `RegistersTest.java`

**Interfaces:**
- Consumes: `Keywords` (Task 2).
- Produces: `public final class Options` (public constructor; package-private `local()`, `onChanged`, `flag`, `number`, `string`, `shiftwidth`, `has`, `keywords`, `get`, `set`, `setLocal`, `apply(String)`, `static def`). `public record EditorDefaults(int tabstop, boolean expandtab, boolean autoindent)` with `VIM`. `public record VimHost(Function<Character, Optional<String>> readClipboard, BiConsumer<Character, String> writeClipboard)` with `detached()`. `public final class Registers(VimHost)` with `Type {CHAR, LINE}`, `record Content(List<String> lines, Type type)` (`of`, `text`), `get`, `set`, and package-private `valid`, `yank`, `delete`, `lastInserted`, `effective`.

- [ ] **Step 1: Write the failing tests**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/OptionsTest.java`:

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
        assertThatThrownBy(() -> options.apply("sw!")).hasMessage("E474: Invalid argument: sw!");
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
}
```

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/RegistersTest.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Registers.Content;
import dev.jasper.editor.vim.Registers.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RegistersTest {
    private final Map<Character, String> clipboard = new HashMap<>();
    private final Registers registers = new Registers(new VimHost(r -> Optional.ofNullable(clipboard.get(r)), clipboard::put));

    private static Content chars(String text) { return Content.of(text, Type.CHAR); }

    private static Content lines(String text) { return Content.of(text, Type.LINE); }

    @Test void contentKeepsVimsShape() {
        assertThat(lines("a\nb\n").lines()).containsExactly("a", "b");
        assertThat(lines("a\nb\n").text()).isEqualTo("a\nb\n");
        assertThat(chars("a\nb").lines()).containsExactly("a", "b");
        assertThat(chars("").lines()).containsExactly("");
    }

    @Test void aYankWritesRegisterZeroAndTheUnnamedRegisterPointsAtIt() {
        registers.yank((char) 0, chars("word"), "");
        assertThat(registers.get('0')).contains(chars("word"));
        assertThat(registers.get('"')).contains(chars("word"));
        registers.yank('a', chars("named"), "");
        assertThat(registers.get('0')).contains(chars("word"));
        assertThat(registers.get('"')).contains(chars("named"));
    }

    @Test void deletesShiftTheNumberedRegistersOrUseTheSmallDeleteRegister() {
        registers.delete((char) 0, lines("one\n"), 1, false, "");
        registers.delete((char) 0, lines("two\n"), 1, false, "");
        assertThat(registers.get('1')).contains(lines("two\n"));
        assertThat(registers.get('2')).contains(lines("one\n"));
        registers.delete((char) 0, chars("x"), 1, false, "");
        assertThat(registers.get('-')).contains(chars("x"));
        assertThat(registers.get('1')).contains(lines("two\n"));
        assertThat(registers.get('"')).contains(chars("x"));
        registers.delete((char) 0, chars("sentence"), 1, true, "");
        assertThat(registers.get('1')).as("% ( ) { } always use register 1").contains(chars("sentence"));
    }

    @Test void aNamedDeleteAlsoFillsRegisterOneAndAppendsWithUpperCase() {
        registers.delete('a', chars("one "), 1, false, "");
        assertThat(registers.get('1')).contains(chars("one "));
        assertThat(registers.get('-')).isEmpty();
        registers.delete('A', chars("two "), 1, false, "");
        assertThat(registers.get('a')).contains(chars("one two "));
        assertThat(registers.get('"')).contains(chars("two "));
        registers.yank('A', lines("line\n"), "");
        assertThat(registers.get('a')).contains(new Content(java.util.List.of("one two ", "line"), Type.LINE));
    }

    @Test void theBlackHoleKeepsEverything() {
        registers.yank((char) 0, chars("keep"), "");
        registers.delete('_', chars("gone"), 1, false, "");
        assertThat(registers.get('"')).contains(chars("keep"));
        assertThat(registers.get('_')).isEmpty();
    }

    @Test void plusAndStarAreTheClipboardAndRememberLinewiseText() {
        registers.yank('+', lines("a line\n"), "");
        assertThat(clipboard.get('+')).isEqualTo("a line\n");
        assertThat(registers.get('+')).contains(lines("a line\n"));
        clipboard.put('*', "from elsewhere");
        assertThat(registers.get('*')).contains(chars("from elsewhere"));
        clipboard.put('+', "two\nlines\n");
        assertThat(registers.get('+').orElseThrow().type()).isEqualTo(Type.LINE);
    }

    @Test void clipboardUnnamedSendsUnnamedYanksAndDeletesToTheClipboard() {
        registers.yank((char) 0, chars("yanked"), "unnamed");
        assertThat(clipboard.get('*')).isEqualTo("yanked");
        assertThat(registers.get('0')).isEmpty();
        registers.delete((char) 0, chars("x"), 1, false, "unnamedplus");
        assertThat(clipboard.get('+')).isEqualTo("x");
        assertThat(registers.get('-')).contains(chars("x"));
        assertThat(Registers.effective((char) 0, "unnamed")).isEqualTo('*');
        assertThat(Registers.effective('a', "unnamed")).isEqualTo('a');
    }

    @Test void theDotRegisterHoldsTheLastInsert() {
        registers.lastInserted("typed");
        assertThat(registers.get('.')).contains(chars("typed"));
        assertThat(Registers.valid('.', false)).isTrue();
        assertThat(Registers.valid('.', true)).isFalse();
        assertThat(Registers.valid('!', false)).isFalse();
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest'
```

Expected: compilation fails: `Options`, `Registers` and `VimHost` do not exist.

- [ ] **Step 3: Write the implementation**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`:

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
 * ({@link EditorDefaults}) and 'showcmd', which is on.
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
        new Def("expandtab", "et", Type.BOOLEAN, false, true),
        new Def("iskeyword", "isk", Type.STRING, "@,48-57,_,192-255", true),
        new Def("joinspaces", "js", Type.BOOLEAN, true, false),
        new Def("matchpairs", "mps", Type.STRING, "(:),{:},[:]", true),
        new Def("nrformats", "nf", Type.STRING, "bin,octal,hex", true),
        new Def("report", "report", Type.NUMBER, 2, false),
        new Def("shiftround", "sr", Type.BOOLEAN, false, false),
        new Def("shiftwidth", "sw", Type.NUMBER, 8, true),
        new Def("showcmd", "sc", Type.BOOLEAN, true, false),
        new Def("showmode", "smd", Type.BOOLEAN, true, false),
        new Def("softtabstop", "sts", Type.NUMBER, 0, true),
        new Def("startofline", "sol", Type.BOOLEAN, true, false),
        new Def("tabstop", "ts", Type.NUMBER, 8, true),
        new Def("tildeop", "top", Type.BOOLEAN, false, false),
        new Def("whichwrap", "ww", Type.STRING, "b,s", false));

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

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/EditorDefaults.java`:

```java
package dev.jasper.editor.vim;

/**
 * Option defaults the Editor supplies for a tab (spec 3.8), used until the user sets the option.
 *
 * @param tabstop the Editor's tab size, used for 'tabstop' and 'shiftwidth'
 * @param expandtab whether the tab indents with spaces
 * @param autoindent whether new lines copy the indent (the Editor auto-indents, so Jasper passes true)
 */
public record EditorDefaults(int tabstop, boolean expandtab, boolean autoindent) {
    /** Vim's own defaults ({@code -u NONE}), which the corpus uses. */
    public static final EditorDefaults VIM = new EditorDefaults(8, false, false);
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/VimHost.java`:

```java
package dev.jasper.editor.vim;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * What the Vim engine needs from the Editor (spec 1.1). Plans V2 and V3 add saving, closing, tabs and actions.
 *
 * @param readClipboard reads {@code '+'} (the clipboard) or {@code '*'} (the selection, or the clipboard where there
 *     is none); empty when it holds no text or cannot be read
 * @param writeClipboard writes {@code '+'} or {@code '*'}
 */
public record VimHost(Function<Character, Optional<String>> readClipboard, BiConsumer<Character, String> writeClipboard) {
    /** @return a host with no clipboard: reads are empty and writes are dropped */
    public static VimHost detached() { return new VimHost(register -> Optional.empty(), (register, text) -> { }); }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Registers.java`:

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
    /** How a register's text is put: within a line, as whole lines (or, from plan V1b, as a block). */
    public enum Type { CHAR, LINE }

    /**
     * A register's text as Vim holds it: lines, and how they are put.
     *
     * @param lines at least one line; a character register's lines are joined by line breaks
     * @param type charwise or linewise
     */
    public record Content(List<String> lines, Type type) {
        /** Copies the lines. */
        public Content {
            lines = List.copyOf(lines);
            if (lines.isEmpty()) throw new IllegalArgumentException("A register holds at least one line");
        }

        /**
         * @param text register text; a linewise register's text ends with a line break, which is dropped
         * @param type the type
         * @return the content
         */
        public static Content of(String text, Type type) {
            String body = type == Type.LINE && text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
            return new Content(List.of(body.split("\n", -1)), type);
        }

        /** @return the text as {@code getreg()} gives it: linewise text ends with a line break */
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

    /** Sets a register directly, as {@code setreg()} or {@code :let @a = …}; the unnamed register then points at it. */
    public void set(char name, Content content) {
        write(name, content);
        previous = Character.toLowerCase(name) == '"' ? '0' : Character.toLowerCase(name);
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
        // Appending (ops.c): linewise wins; two charwise texts join their touching lines.
        Type type = content.type() == Type.LINE || old.type() == Type.LINE ? Type.LINE : Type.CHAR;
        List<String> lines = new ArrayList<>(old.lines());
        if (type == Type.CHAR) {
            lines.set(lines.size() - 1, lines.getLast() + content.lines().getFirst());
            lines.addAll(content.lines().subList(1, content.lines().size()));
        } else lines.addAll(content.lines());
        values.put(register, new Content(lines, type));
    }

    private Optional<Content> clipboard(char register) {
        Optional<String> text = host.readClipboard().apply(register);
        if (text.isEmpty()) return Optional.empty();
        if (clipboardWritten != null && clipboardWritten.text().equals(text.get())) return Optional.of(clipboardWritten);
        return Optional.of(Content.of(text.get(), text.get().endsWith("\n") ? Type.LINE : Type.CHAR));
    }
}
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest'
```

Expected: 13 tests pass.

- [ ] **Step 5: Commit**

```bash
git add plugins/editor/src/main/java/dev/jasper/editor/vim plugins/editor/src/test/java/dev/jasper/editor/vim
git commit -m "Add Vim options and registers

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: The engine

**Files:**
- Create in `plugins/editor/src/main/java/dev/jasper/editor/vim/`: `package-info.java`, `Mode.java`, `Target.java`, `Span.java`, `VimFailure.java`, `Context.java`, `KeyHandlers.java`, `VimCommands.java`, `Vim.java`, `VimUndoManager.java`, `VimTextArea.java`, `VimEditor.java`, `Machine.java`, `InsertMode.java`, `Matching.java`, `Motions.java`, `TextObjects.java`, `Operators.java`, `NormalCommands.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/VimUndoManagerTest.java`, `VimEditorTest.java`

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces (public, used by Tasks 6 and 7): `Vim(VimHost)` with `options()`, `registers()`, `handlers()`, `commands()`, `coverage(Consumer<String>)`; `VimEditor(Vim, VimTextArea, EditorDefaults)` with `close()`, `editorDefaults(EditorDefaults)`, `textReplaced()`, `offer(KeyInput)`, `feed(KeyInput)`, `mode()`, `showcmd()`, `message()`, `messageIsError()`, `bells()`, `addListener`, `removeListener`, `showmode()`, `options()`; `VimTextArea()` with `vim()`; `Mode` with `id()`, `label()`, `inserting()`; `KeyHandlers` with `Handler {VIM, IDE, DEFAULT}`, `set(KeyInput, String, Handler)`, `clear()`, `handler(KeyInput, char)`; `VimCommands.standard()`, `entries()`, `Kind`, `Entry` (`kind`, `keys`, `description`, `needsChar`, `change`, `id()`).
- Package-private, used by Task 5's harness: `VimEditor.setCursor(Pos)`, `cursor()`, `buffer()`, `dispatch(KeyInput)`; `Registers.set`.

This task creates the whole engine because its classes refer to one another (the machine to Insert mode, the normal commands to the machine's `alias`). The unit tests here cover the machinery around the commands: the handler rule, the caret, input methods, the bell, recovery from an unexpected exception, the clipboard, key events and undo steps. Task 5's corpus checks every command against Vim.

How the pieces fit:
- `VimTextArea.processKeyEvent` turns events into keys (`KeyTranslator`) and calls `VimEditor.offer`, which applies the handler rule (spec 2.2) and, when Vim takes the key, `feed`. A consumed event never reaches Swing's key bindings, so the Editor's shortcuts only see keys Vim declined.
- `feed` dispatches to `InsertMode` in Insert and Replace mode and to `Machine` otherwise. A `VimFailure` rings the bell (a counter the Vim bar watches) and shows its message; any other exception is logged, closes open undo steps, and returns to Normal mode.
- `Machine` parses `["x][count]command`. Motions return a `Target` (or null to fail); after an operator, `span` turns it into a `Span`, applying forced `v`/`V` and Vim's exclusive adjustment; text objects return a `Span` directly. `applyOperator` puts the cursor on the span's start, opens the undo step, runs the operator, and `complete` records the change for `.` unless it entered Insert mode, in which case `InsertMode` ends the step and the recording at `<Esc>`.
- `VimUndoManager` replaces RSyntaxTextArea's manager (`VimTextArea.createUndoManager`). With Vim on, only the engine's `beginStep`/`endStep` group edits. `VimEditor.undoRedo` watches the document during an undo to place the cursor as Vim's `u_undoredo` does.

- [ ] **Step 1: Write the failing tests**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/VimUndoManagerTest.java`:

```java
package dev.jasper.editor.vim;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VimUndoManagerTest {
    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    @Test void withVimOffNeighbouringTypingIsOneStepAsInRSyntaxTextArea() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            area.setText("x");
            area.discardAllEdits();
            area.setCaretPosition(1);
            area.replaceSelection("a");
            area.replaceSelection("b");
            area.setCaretPosition(0);
            area.replaceSelection("z");
            area.undoLastAction();
            assertThat(area.getText()).isEqualTo("xab");
            area.undoLastAction();
            assertThat(area.getText()).isEqualTo("x");
        });
    }

    @Test void withVimOnEachStepIsWhatTheEngineBracketed() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            area.setText("x");
            area.discardAllEdits();
            VimUndoManager undo = area.vimUndo();
            undo.vim(true);
            undo.beginStep(0, 1);
            area.setCaretPosition(1);
            area.replaceSelection("a");
            area.setCaretPosition(0);
            area.replaceSelection("z");
            undo.beginStep(0, 0);
            area.replaceSelection("y");
            undo.endStep();
            assertThat(undo.stepOpen()).isTrue();
            undo.endStep();
            area.setCaretPosition(0);
            area.replaceSelection("q");
            assertThat(undo.nextUndo().cursorLine).as("an edit outside a step is a step of its own").isEqualTo(-1);
            area.undoLastAction();
            assertThat(area.getText()).isEqualTo("zyxa");
            assertThat(undo.nextUndo().cursorCol).isEqualTo(1);
            area.undoLastAction();
            assertThat(area.getText()).isEqualTo("x");
            area.redoLastAction();
            assertThat(area.getText()).isEqualTo("zyxa");
        });
    }

    @Test void aStepWithoutEditsLeavesNothingToUndoAndUndoClosesAnOpenStep() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            area.setText("x");
            area.discardAllEdits();
            VimUndoManager undo = area.vimUndo();
            undo.vim(true);
            undo.beginStep(0, 0);
            undo.endStep();
            assertThat(area.canUndo()).isFalse();
            undo.beginStep(0, 0);
            area.replaceSelection("a");
            area.undoLastAction();
            assertThat(undo.stepOpen()).isFalse();
            assertThat(area.getText()).isEqualTo("x");
        });
    }
}
```

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/VimEditorTest.java`:

```java
package dev.jasper.editor.vim;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.fife.ui.rtextarea.CaretStyle;
import org.fife.ui.rtextarea.ConfigurableCaret;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VimEditorTest {
    private final Map<Character, String> clipboard = new HashMap<>();
    private Vim vim = new Vim(new VimHost(r -> Optional.ofNullable(clipboard.get(r)), clipboard::put));

    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private VimEditor editor(VimTextArea area, String text) {
        area.setText(text);
        area.discardAllEdits();
        area.setCaretPosition(0);
        return new VimEditor(vim, area, EditorDefaults.VIM);
    }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private static CaretStyle caret(VimTextArea area) { return ((ConfigurableCaret) area.getCaret()).getStyle(); }

    /** Remembers whether input methods are on, which a component does not report. */
    private static final class InputMethodArea extends VimTextArea {
        Boolean inputMethods;

        @Override public void enableInputMethods(boolean enable) {
            inputMethods = enable;
            super.enableInputMethods(enable);
        }
    }

    @Test void theCaretAndInputMethodsFollowTheMode() throws Exception {
        onEdt(() -> {
            var area = new InputMethodArea();
            CaretStyle before = caret(area);
            var editor = editor(area, "abc\n");
            assertThat(caret(area)).isEqualTo(CaretStyle.BLOCK_STYLE);
            assertThat(area.inputMethods).as("an IME must not eat Normal-mode commands").isFalse();
            type(editor, "d");
            assertThat(editor.mode()).isEqualTo(Mode.OPERATOR_PENDING);
            assertThat(caret(area)).isEqualTo(CaretStyle.UNDERLINE_STYLE);
            type(editor, "<Esc>i");
            assertThat(editor.mode()).isEqualTo(Mode.INSERT);
            assertThat(caret(area)).isEqualTo(CaretStyle.VERTICAL_LINE_STYLE);
            assertThat(area.inputMethods).isTrue();
            type(editor, "<Esc>R");
            assertThat(caret(area)).isEqualTo(CaretStyle.UNDERLINE_STYLE);
            type(editor, "<Esc>");
            editor.close();
            assertThat(caret(area)).isEqualTo(before);
            assertThat(area.inputMethods).isTrue();
            assertThat(area.vim()).isEmpty();
        });
    }

    @Test void theHandlerRuleGivesVimOnlyKeysItDefines() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            assertThat(editor.offer(new KeyInput("s", KeyInput.CTRL))).as("Vim has no <C-s>: the Editor saves").isFalse();
            assertThat(editor.offer(new KeyInput("s", KeyInput.META))).as("⌘ keys stay with the Editor").isFalse();
            assertThat(editor.offer(new KeyInput("r", KeyInput.CTRL))).as("<C-r> redoes").isTrue();
            assertThat(editor.offer(KeyInput.named("F5"))).isFalse();
            assertThat(editor.offer(KeyInput.of('i'))).isTrue();
            assertThat(editor.offer(new KeyInput("w", KeyInput.CTRL))).as("<C-w> deletes a word in Insert mode").isTrue();
            assertThat(editor.offer(new KeyInput("f", KeyInput.CTRL))).as("<C-f> is not an Insert-mode key").isFalse();
            editor.offer(KeyInput.named("Esc"));
            vim.handlers().set(new KeyInput("r", KeyInput.CTRL), "n", KeyHandlers.Handler.IDE);
            assertThat(editor.offer(new KeyInput("r", KeyInput.CTRL))).isFalse();
            vim.handlers().set(new KeyInput("s", KeyInput.CTRL), "a", KeyHandlers.Handler.VIM);
            assertThat(editor.offer(new KeyInput("s", KeyInput.CTRL))).isTrue();
        });
    }

    @Test void aFailingCommandRingsTheBellAndShowsVimsMessage() throws Exception {
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "abc\n");
            type(editor, "\"zp");
            assertThat(editor.bells()).isEqualTo(1);
            assertThat(editor.message()).isEqualTo("E353: Nothing in register z");
            assertThat(editor.messageIsError()).isTrue();
            type(editor, "h");
            assertThat(editor.bells()).isEqualTo(2);
            assertThat(editor.message()).isEmpty();
        });
    }

    @Test void anUnexpectedErrorCancelsTheCommandAndReturnsToNormalMode() throws Exception {
        vim = new Vim(new VimHost(r -> { throw new IllegalStateException("clipboard exploded"); }, (r, t) -> { }));
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            type(editor, "\"+p");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(editor.message()).isEqualTo("Internal error; the command was cancelled");
            assertThat(area.vimUndo().stepOpen()).isFalse();
            type(editor, "x");
            assertThat(area.getText()).isEqualTo("bc\n");
        });
    }

    @Test void yanksAndPutsReachTheClipboardThroughTheHost() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "one\ntwo\n");
            type(editor, "\"+yy");
            assertThat(clipboard.get('+')).isEqualTo("one\n");
            clipboard.put('+', "pasted");
            type(editor, "j\"+P");
            assertThat(area.getText()).isEqualTo("one\npastedtwo\n");
        });
    }

    @Test void showcmdShowsThePartlyTypedCommand() throws Exception {
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "abc\n");
            type(editor, "2\"a");
            assertThat(editor.showcmd()).isEqualTo("2\"a");
            type(editor, "d");
            assertThat(editor.showcmd()).isEqualTo("2\"ad");
            type(editor, "<Esc>");
            assertThat(editor.showcmd()).isEmpty();
            editor.options().set("showcmd", false);
            type(editor, "2");
            assertThat(editor.showcmd()).isEmpty();
        });
    }

    @Test void theTabsOptionsFollowTheEditorsDefaultsAndDriveTheTextArea() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            area.setText("x\n");
            var editor = new VimEditor(vim, area, new EditorDefaults(4, true, true));
            assertThat(editor.options().number("tabstop")).isEqualTo(4);
            assertThat(editor.options().number("shiftwidth")).isEqualTo(4);
            assertThat(area.getTabsEmulated()).isTrue();
            editor.editorDefaults(new EditorDefaults(2, false, true));
            assertThat(area.getTabSize()).isEqualTo(2);
            assertThat(area.getTabsEmulated()).isFalse();
        });
    }

    @Test void reloadingTheTextDropsAnInsertInProgress() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            type(editor, "ix");
            area.setText("new\n");
            editor.textReplaced();
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(area.vimUndo().stepOpen()).isFalse();
        });
    }

    @Test void keyEventsReachVimBeforeTheTextAreasBindings() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            var typedX = new KeyEvent(area, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'x');
            area.processKeyEvent(typedX);
            assertThat(typedX.isConsumed()).isTrue();
            assertThat(area.getText()).isEqualTo("bc\n");
            var escape = new KeyEvent(area, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
            area.processKeyEvent(escape);
            assertThat(escape.isConsumed()).isTrue();
            var escapeTyped = new KeyEvent(area, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, (char) 27);
            area.processKeyEvent(escapeTyped);
            assertThat(escapeTyped.isConsumed()).as("the character of a key Vim took is swallowed").isTrue();
            int bells = editor.bells();
            var command = new KeyEvent(area, KeyEvent.KEY_PRESSED, 0, InputEvent.META_DOWN_MASK, KeyEvent.VK_F7, KeyEvent.CHAR_UNDEFINED);
            area.processKeyEvent(command);
            assertThat(command.isConsumed()).as("a ⌘ key goes on to the text area's and the window's bindings").isFalse();
            assertThat(editor.bells()).isEqualTo(bells);
            type(editor, "i");
            area.processKeyEvent(new KeyEvent(area, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, '\uD83D'));
            area.processKeyEvent(new KeyEvent(area, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, '\uDE00'));
            assertThat(area.getText()).isEqualTo("😀bc\n");
        });
    }

    @Test void aMouseClickPastTheEndOfALineIsPulledBackInNormalMode() throws Exception {
        var area = new AtomicReference<VimTextArea>();
        onEdt(() -> {
            area.set(new VimTextArea());
            editor(area.get(), "abc\n");
            area.get().setCaretPosition(3);
        });
        onEdt(() -> assertThat(area.get().getCaretPosition()).isEqualTo(2));
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.VimEditorTest'
```

Expected: compilation fails: `VimTextArea`, `VimEditor` and the rest do not exist.

- [ ] **Step 3: Write the engine, file by file**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/package-info.java`:

```java
/**
 * The Vim engine (spec docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md): keys, modes, motions, text
 * objects, operators, registers, undo steps and {@code .}, on an RSyntaxTextArea, with no Swing UI of its own. UI
 * thread only. May depend on: RSyntaxTextArea, the JDK.
 */
package dev.jasper.editor.vim;
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Mode.java`:

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
    INSERT_NORMAL("insert-normal", "-- (insert) --");

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
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Target.java`:

```java
package dev.jasper.editor.vim;

/**
 * Where a motion lands and how an operator treats the text up to it (Vim's oparg: motion_type and inclusive).
 *
 * @param pos the new cursor position
 * @param kind exclusive, inclusive or linewise
 * @param column what happens to the remembered column: reset from the new position, kept (j, k), or end of line ($)
 * @param jump whether the motion is a jump (plan V1b adds it to the jumplist)
 * @param regOne whether a delete with this motion always writes register 1
 * @param adjust whether Vim's exclusive-motion adjustment (spec: {@code :help exclusive-linewise}) applies
 */
record Target(Pos pos, Kind kind, Column column, boolean jump, boolean regOne, boolean adjust) {
    enum Kind { EXCLUSIVE, INCLUSIVE, LINEWISE }

    enum Column { RESET, KEEP, END }

    static Target exclusive(Pos pos) { return new Target(pos, Kind.EXCLUSIVE, Column.RESET, false, false, true); }

    static Target inclusive(Pos pos) { return new Target(pos, Kind.INCLUSIVE, Column.RESET, false, false, true); }

    static Target linewise(Pos pos, Column column) { return new Target(pos, Kind.LINEWISE, column, false, false, true); }

    Target asJump() { return new Target(pos, kind, column, true, regOne, adjust); }

    Target usingRegisterOne() { return new Target(pos, kind, column, jump, true, adjust); }

    Target withoutAdjust() { return new Target(pos, kind, column, jump, regOne, false); }

    Target withColumn(Column value) { return new Target(pos, kind, value, jump, regOne, adjust); }

    Target withKind(Kind value) { return new Target(pos, value, column, jump, regOne, adjust); }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Span.java`:

```java
package dev.jasper.editor.vim;

/**
 * The text an operator acts on (Vim's oap->start, oap->end, motion_type and inclusive).
 *
 * @param start the first position
 * @param end the last position: included when {@code kind} is inclusive, excluded when exclusive, and for linewise
 *     spans only its line counts
 * @param kind exclusive, inclusive or linewise
 * @param regOne whether a delete always writes register 1
 */
record Span(Pos start, Pos end, Target.Kind kind, boolean regOne) {
    Span(Pos start, Pos end, Target.Kind kind) { this(start, end, kind, false); }

    boolean linewise() { return kind == Target.Kind.LINEWISE; }

    int lineCount() { return end.line - start.line + 1; }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/VimFailure.java`:

```java
package dev.jasper.editor.vim;

/**
 * A Vim command failed (spec 2.3): the engine rings the visual bell, shows the message if there is one, drops the
 * pending command and stops a running repeat. Thrown from anywhere inside a command; it carries no stack trace.
 */
final class VimFailure extends RuntimeException {
    VimFailure(String message) { super(message, null, false, false); }

    static VimFailure bell() { return new VimFailure(""); }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Context.java`:

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

    static VimFailure fail() { return VimFailure.bell(); }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/KeyHandlers.java`:

```java
package dev.jasper.editor.vim;

import java.util.HashMap;
import java.util.Map;

/**
 * The handler table (spec 2.2): which keys go to Vim and which to the Editor, per mode, as {@code sethandler} sets
 * them (plan V3 reads the lines from the vimrc). A key with no entry follows the default rule.
 */
public final class KeyHandlers {
    /** Who gets a key. */
    public enum Handler { VIM, IDE, DEFAULT }

    /** The mode letters {@code sethandler} accepts; {@code a} is all of them. */
    static final String MODES = "nivxoc";

    private final Map<KeyInput, Map<Character, Handler>> table = new HashMap<>();

    /**
     * @param key a key
     * @param modes mode letters from {@link #MODES}, or {@code a}, joined with {@code -}: {@code n-v}
     * @param handler who gets the key in those modes
     * @throws IllegalArgumentException for an unknown mode letter
     */
    public void set(KeyInput key, String modes, Handler handler) {
        Map<Character, Handler> byMode = table.computeIfAbsent(key, k -> new HashMap<>());
        for (String part : modes.split("-")) {
            if (part.equals("a")) {
                for (char mode : MODES.toCharArray()) byMode.put(mode, handler);
            } else if (part.length() == 1 && MODES.indexOf(part.charAt(0)) >= 0) {
                byMode.put(part.charAt(0), handler);
            } else throw new IllegalArgumentException("Unknown sethandler mode: " + part);
        }
    }

    /** Forgets every entry (the vimrc is being read again). */
    public void clear() { table.clear(); }

    /**
     * @param key a key
     * @param mode a letter from {@link #MODES}
     * @return who gets it there, or {@link Handler#DEFAULT}
     */
    public Handler handler(KeyInput key, char mode) {
        Map<Character, Handler> byMode = table.get(key);
        return byMode == null ? Handler.DEFAULT : byMode.getOrDefault(mode, Handler.DEFAULT);
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`:

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
    public enum Kind { MOTION, OPERATOR, ACTION, OBJECT, INSERT }

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
    private final Set<String> normalPrefixes = new HashSet<>(), pendingPrefixes = new HashSet<>();

    private VimCommands() { }

    /** @return the standard commands */
    public static VimCommands standard() {
        var commands = new VimCommands();
        Motions.register(commands);
        TextObjects.register(commands);
        Operators.register(commands);
        NormalCommands.register(commands);
        InsertMode.register(commands);
        return commands;
    }

    /** @return every entry, in registration order */
    public List<Entry> entries() {
        List<Entry> all = new ArrayList<>(normal.values());
        all.addAll(objects.values());
        all.addAll(insert.values());
        return Collections.unmodifiableList(all);
    }

    void motion(String keys, String description, Motion motion) { add(normal, new Entry(Kind.MOTION, keys, description, false, false, motion)); }

    void motionWithChar(String keys, String description, Motion motion) { add(normal, new Entry(Kind.MOTION, keys, description, true, false, motion)); }

    void operator(String keys, String description, Operator operator) { add(normal, new Entry(Kind.OPERATOR, keys, description, false, !keys.equals("y"), operator)); }

    void action(String keys, String description, boolean change, Action action) { add(normal, new Entry(Kind.ACTION, keys, description, false, change, action)); }

    void actionWithChar(String keys, String description, boolean change, Action action) { add(normal, new Entry(Kind.ACTION, keys, description, true, change, action)); }

    void object(String keys, String description, TextObject object) { add(objects, new Entry(Kind.OBJECT, keys, description, false, false, object)); }

    void insert(String keys, String description, InsertKey key) { add(insert, new Entry(Kind.INSERT, keys, description, false, false, key)); }

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

    /** @return whether more keys could complete a command */
    boolean prefix(String keys, boolean pending) { return (pending ? pendingPrefixes : normalPrefixes).contains(keys); }

    private void add(Map<String, Entry> map, Entry entry) {
        if (map.putIfAbsent(entry.keys(), entry) != null) throw new IllegalStateException("Duplicate command " + entry.keys());
        if (entry.kind() == Kind.INSERT) return;
        List<KeyInput> keys = KeyInput.parse(entry.keys());
        for (int n = 1; n < keys.size(); n++) {
            String prefix = KeyInput.format(keys.subList(0, n));
            if (entry.kind() != Kind.OBJECT) normalPrefixes.add(prefix);
            if (entry.kind() == Kind.MOTION || entry.kind() == Kind.OBJECT) pendingPrefixes.add(prefix);
        }
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Vim.java`:

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
     */
    record Change(char register, int count, List<KeyInput> keys) { }

    private final Options options = new Options();
    private final Registers registers;
    private final KeyHandlers handlers = new KeyHandlers();
    private final VimCommands commands = VimCommands.standard();
    private Consumer<String> coverage = id -> { };
    private Find lastFind;
    private Change lastChange;

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

    void lastChange(Change change) { lastChange = change; }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/VimUndoManager.java`:

```java
package dev.jasper.editor.vim;

import javax.swing.UIManager;
import javax.swing.event.UndoableEditEvent;
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
        final int cursorLine, cursorCol;
        int edits;

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
        if (step.edits > 0) addEdit(step);
        updateActions();
    }

    boolean stepOpen() { return stepDepth > 0; }

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
            addEdit(step);
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
        addEdit(typing);
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
        if (step.edits > 0) addEdit(step);
        updateActions();
    }

    /** Counts an open step's edits, since RTextArea asks before it undoes (⌘Z in the middle of an insert). */
    @Override public synchronized boolean canUndo() {
        return vimStep != null && vimStep.edits > 0 || internal != null && internal.edits > 0 || super.canUndo();
    }

    @Override public void undo() {
        closeAll();
        super.undo();
    }

    @Override public void redo() {
        closeAll();
        super.redo();
    }

    @Override public void discardAllEdits() {
        stepDepth = internalDepth = 0;
        vimStep = internal = typing = null;
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

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/VimTextArea.java`:

```java
package dev.jasper.editor.vim;

import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.text.CharacterIterator;
import java.util.Locale;
import java.util.Optional;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RUndoManager;

/**
 * Every Editor tab's text area (spec 1.1). With Vim off it is an ordinary RSyntaxTextArea. With Vim on, key events
 * reach the attached {@link VimEditor} before Swing's key bindings: a key Vim takes is consumed here, so neither the
 * text area's bindings nor the Editor's window shortcuts see it (the handler rule, spec 2.2).
 */
public class VimTextArea extends RSyntaxTextArea {
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");

    // Assigned from the superclass constructor through createUndoManager, so it has no initializer.
    private VimUndoManager undo;
    private VimEditor vim;
    private boolean swallowTyped;
    private char highSurrogate;

    /** An empty text area. */
    public VimTextArea() { }

    @Override protected RUndoManager createUndoManager() {
        undo = new VimUndoManager(this);
        return undo;
    }

    VimUndoManager vimUndo() { return undo; }

    /** @return the attached Vim editor, if Vim is on */
    public Optional<VimEditor> vim() { return Optional.ofNullable(vim); }

    void attach(VimEditor editor) {
        vim = editor;
        undo.vim(editor != null);
    }

    @Override protected void processKeyEvent(KeyEvent event) {
        VimEditor editor = vim;
        if (editor != null && handled(editor, event)) {
            event.consume();
            return;
        }
        super.processKeyEvent(event);
    }

    private boolean handled(VimEditor editor, KeyEvent event) {
        switch (event.getID()) {
            case KeyEvent.KEY_PRESSED -> {
                swallowTyped = false;
                Optional<KeyInput> key = KeyTranslator.pressed(event.getKeyCode(), event.getModifiersEx(), MAC);
                swallowTyped = key.isPresent() && editor.offer(key.get());
                return swallowTyped;
            }
            case KeyEvent.KEY_TYPED -> {
                if (swallowTyped) return true;
                char c = event.getKeyChar();
                if (Character.isHighSurrogate(c)) {
                    highSurrogate = c;
                    return true;
                }
                int codePoint = Character.isLowSurrogate(c) && highSurrogate != 0 ? Character.toCodePoint(highSurrogate, c) : c;
                highSurrogate = 0;
                Optional<KeyInput> key = KeyTranslator.typed(codePoint, event.getModifiersEx(), MAC);
                return key.isPresent() && editor.offer(key.get());
            }
            default -> {
                return false;
            }
        }
    }

    @Override protected void processInputMethodEvent(InputMethodEvent event) {
        VimEditor editor = vim;
        String committed = editor != null && event.getID() == InputMethodEvent.INPUT_METHOD_TEXT_CHANGED ? committed(event) : "";
        super.processInputMethodEvent(event);
        if (!committed.isEmpty()) editor.typedByInputMethod(committed);
    }

    private static String committed(InputMethodEvent event) {
        StringBuilder text = new StringBuilder();
        CharacterIterator iterator = event.getText();
        if (iterator == null) return "";
        char c = iterator.first();
        for (int i = 0; i < event.getCommittedCharacterCount() && c != CharacterIterator.DONE; i++, c = iterator.next()) text.append(c);
        return text.toString();
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`:

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
        this.options = vim.options().local();
        this.machine = new Machine(this);
        this.insert = new InsertMode(this);
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
        char handlerMode = switch (mode()) {
            case INSERT, REPLACE -> 'i';
            case OPERATOR_PENDING -> 'o';
            default -> 'n';
        };
        boolean mine = switch (vim.handlers().handler(key, handlerMode)) {
            case VIM -> true;
            case IDE -> false;
            case DEFAULT -> (key.modifiers() & KeyInput.META) == 0 && (mode.inserting() ? insert.wants(key) : machine.wants(key));
        };
        if (mine) feed(key);
        return mine;
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
            if (mode == Mode.NORMAL || mode == Mode.OPERATOR_PENDING) setCursor(cursor());
            look();
            fire();
        }
    }

    /** Runs one key without the failure handling, for {@code .} and a count's repeat. */
    void dispatch(KeyInput key) {
        if (mode.inserting()) insert.key(key);
        else machine.key(key);
    }

    /** Text an input method committed in Insert mode, recorded for {@code .}. */
    void typedByInputMethod(String text) { insert.typedByInputMethod(text); }

    /** @return the mode */
    public Mode mode() { return mode == Mode.NORMAL && machine.operatorPending() ? Mode.OPERATOR_PENDING : mode; }

    /** @return the partly typed command ('showcmd') */
    public String showcmd() { return options.flag("showcmd") ? machine.showcmd() : ""; }

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

    /** @return the column a vertical motion lands on in {@code line}, from the remembered column */
    int wantedColumn(int line) {
        String text = buffer.line(line);
        if (curswant == Integer.MAX_VALUE) return Text.last(text);
        int col = Text.columnAt(text, want(false), options.number("tabstop"));
        return col >= text.length() && !text.isEmpty() ? Text.last(text) : col;
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
        if (inserted && event.getOffset() == buffer.end(line)) {
            String text = buffer.text();
            if (event.getOffset() < text.length() && text.charAt(event.getOffset()) == '\n') return line + 1;
        }
        return line;
    }

    /** The caret moved by the mouse or the Editor: in Normal mode it is kept off the end of the line. */
    private void caretMoved() {
        if (busy || mode != Mode.NORMAL || machine.pending()) return;
        if (area.getSelectionStart() != area.getSelectionEnd()) return;
        Pos p = cursor();
        String text = buffer.line(p.line);
        if (!text.isEmpty() && p.col >= text.length()) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (!busy && mode == Mode.NORMAL && area.getSelectionStart() == area.getSelectionEnd()) {
                    busy = true;
                    try { setCursor(cursor()); }
                    finally { busy = false; }
                }
            });
        }
        curswant = -1;
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

    private void fire() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`:

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
    private String keys = "";
    private final List<KeyInput> shown = new ArrayList<>();
    private Consumer<KeyInput> argument;
    /** The keys of the change being typed, without counts or register, for {@code .}. */
    private final List<KeyInput> record = new ArrayList<>();
    private char recordRegister;
    private int recordCount;
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
        if (key.is("<C-c>")) return pending() || editor.mode() == Mode.INSERT_NORMAL;
        String candidate = keys + key.notation();
        VimCommands commands = vim.commands();
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
        if (operator != null && keys.isEmpty() && (c == 'v' || c == 'V')) {
            forced = (char) c;
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
        run(entry, null);
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
        if (kind == Kind.EXCLUSIVE && forced == 0 && target.adjust() && end.col == 0 && end.line > start.line) {
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
        return new Span(start, end, kind, target.regOne());
    }

    private void applyOperator(Context context, Span span) {
        boolean change = !operator.equals("y");
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
    }

    /** A command completed: records it for {@code .} and closes its undo step, unless it entered Insert mode. */
    private void complete(boolean change) {
        if (change && !replaying) {
            recordRegister = register;
            recordCount = count1 > 0 || count2 > 0 ? Math.max(count1, 1) * Math.max(count2, 1) : 0;
            if (editor.mode().inserting()) recordingInsert = true;
            else vim.lastChange(new Vim.Change(recordRegister, recordCount, List.copyOf(record)));
        }
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
        recordingInsert = true;
    }

    /** Drops the partly typed command, keeping an insert being recorded. */
    void reset() {
        count1 = count2 = 0;
        register = 0;
        operator = null;
        forced = 0;
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
            vim.lastChange(new Vim.Change(recordRegister, recordCount, List.copyOf(record)));
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
        recordingInsert = true;
    }

    /** {@code .}: the last change again, the new count replacing its count; {@code "1p.} puts register 2. */
    void repeat(Context context) {
        Vim.Change change = vim.lastChange();
        if (change == null) throw Context.fail();
        int count = context.hasCount ? context.count : change.count();
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
            for (KeyInput key : replay) editor.dispatch(key);
        } finally {
            replaying = false;
            endChange();
            recordingInsert = false;
            reset();
        }
        vim.lastChange(new Vim.Change(reg, count, change.keys()));
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

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/InsertMode.java`:

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
            handler.accept(key);
            return;
        }
        if (key.plainChar() >= 0) {
            record(key);
            character(key.key());
            return;
        }
        VimCommands.Entry entry = editor.vim().commands().insertKey(key.notation());
        if (entry == null) return;
        editor.vim().covered(entry.id());
        // These record themselves (or, like <Esc>, are recorded by the machine), or end the recorded insert.
        boolean selfRecorded = key.is("<Esc>") || key.is("<C-c>") || key.is("<C-o>") || key.is("<C-r>") || key.is("<C-v>") || key.is("<C-q>")
            || key.is("<C-a>") || key.is("<C-e>") || key.is("<C-y>") || arrowKey(key);
        if (!selfRecorded) record(key);
        ((VimCommands.InsertKey) entry.handler()).run(this, key);
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

    /** ins_esc. */
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
        }
        removeAutoIndent();
        editor.vim().registers().lastInserted(s.inserted.toString());
        Pos p = editor.cursor();
        session = null;
        editor.mode(Mode.NORMAL);
        if (p.col > 0) editor.setCursor(new Pos(p.line, Text.previous(editor.buffer().line(p.line), p.col)));
        if (!s.nested) editor.machine().insertEnded();
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

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Matching.java`:

```java
package dev.jasper.editor.vim;

/**
 * Bracket matching, ported from Vim's findmatchlimit (search.c) for 'matchpairs' brackets: nesting, backslash
 * escapes (a bracket only matches one escaped the same way) and, when {@code quotes} is on, Vim's skipping of
 * brackets inside double quotes and {@code 'x'} character literals. Vim's C comment and preprocessor matching is not
 * ported (docs/vim-divergences.md).
 */
final class Matching {
    private Matching() { }

    /**
     * {@code %} without a count: from the first 'matchpairs' bracket at or after the cursor on its line, its partner.
     *
     * @return the partner's position, or null
     */
    static Pos percent(Buffer buffer, Pos cursor, String matchpairs) {
        String line = buffer.line(cursor.line);
        for (int col = cursor.col; col < line.length(); col++) {
            char c = line.charAt(col);
            for (String pair : matchpairs.split(",")) {
                if (pair.length() != 3) continue;
                char open = pair.charAt(0), close = pair.charAt(2);
                if (c == open) return scan(buffer, new Pos(cursor.line, col), open, close, false, true, escaped(line, col));
                if (c == close) return scan(buffer, new Pos(cursor.line, col), close, open, true, true, escaped(line, col));
            }
        }
        return null;
    }

    /**
     * {@code [(}, {@code ])} and the block text objects: the unmatched bracket around the cursor.
     *
     * @param open the opening bracket
     * @param close the closing bracket
     * @param findOpen true to search backward for the unmatched {@code open}, false to search forward for {@code close}
     * @param quotes whether brackets in double quotes are skipped
     * @return its position, or null
     */
    static Pos unmatched(Buffer buffer, Pos cursor, char open, char close, boolean findOpen, boolean quotes) {
        return findOpen ? scan(buffer, cursor.copy(), close, open, true, quotes, false) : scan(buffer, cursor.copy(), open, close, false, quotes, false);
    }

    /**
     * The next {@code find} at nesting level 0 counting {@code nest} as opening a level, starting after {@code from}.
     * This is findmatchlimit's main loop.
     */
    static Pos scan(Buffer buffer, Pos from, char nest, char find, boolean backward, boolean quotes, boolean matchEscaped) {
        Pos pos = from.copy();
        String line = buffer.line(pos.line);
        int doQuotes = -1;
        boolean inQuote = false, startInQuotes = false;
        int count = 0;
        while (true) {
            if (backward) {
                if (pos.col == 0) {
                    if (pos.line == 0) return null;
                    pos.line--;
                    line = buffer.line(pos.line);
                    pos.col = line.length();
                    doQuotes = -1;
                } else pos.col--;
            } else {
                if (pos.col >= line.length()) {
                    if (pos.line == buffer.count() - 1) return null;
                    pos.line++;
                    line = buffer.line(pos.line);
                    pos.col = 0;
                    doQuotes = -1;
                } else pos.col++;
            }
            if (!quotes) doQuotes = 0;
            else if (doQuotes == -1) doQuotes = evenQuotes(line) ? 1 : 0;
            int c = pos.col < line.length() ? line.charAt(pos.col) : 0;
            switch (c) {
                case 0 -> {
                    if (pos.col == 0 || line.charAt(pos.col - 1) != '\\') {
                        inQuote = false;
                        startInQuotes = false;
                    }
                }
                case '"' -> {
                    if (doQuotes == 1 && !escaped(line, pos.col)) {
                        inQuote = !inQuote;
                        startInQuotes = false;
                    }
                }
                case '\'' -> {
                    if (quotes && nest != '\'' && find != '\'') {
                        if (backward) {
                            if (pos.col >= 3 && line.charAt(pos.col - 1) != '\'' && line.charAt(pos.col - 2) == '\\' && line.charAt(pos.col - 3) == '\'') pos.col -= 3;
                            else if (pos.col >= 2 && line.charAt(pos.col - 2) == '\'') pos.col -= 2;
                        } else if (pos.col + 1 < line.length()) {
                            if (line.charAt(pos.col + 1) == '\\' && pos.col + 3 < line.length() && line.charAt(pos.col + 3) == '\'') pos.col += 3;
                            else if (pos.col + 2 < line.length() && line.charAt(pos.col + 2) == '\'') pos.col += 2;
                        }
                    }
                }
                default -> {
                    if ((!inQuote || startInQuotes) && (c == nest || c == find) && escaped(line, pos.col) == matchEscaped) {
                        if (c == nest) count++;
                        else if (count == 0) return pos;
                        else count--;
                    }
                }
            }
        }
    }

    /** @return whether an odd number of backslashes precede the column */
    static boolean escaped(String line, int col) {
        int backslashes = 0;
        for (int i = col - 1; i >= 0 && line.charAt(i) == '\\'; i--) backslashes++;
        return (backslashes & 1) == 1;
    }

    /** @return whether the line has an even number of double quotes, skipping {@code \"} and {@code '"'} */
    private static boolean evenQuotes(String line) {
        int quotes = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\'' || i + 1 >= line.length() || line.charAt(i + 1) != '\'')) quotes++;
            if (c == '\\' && i + 1 < line.length()) i++;
        }
        return quotes % 2 == 0;
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Motions.java`:

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
        for (int n = c.count; n > 0; n--) {
            String line = c.buffer.line(p.line);
            int next = Text.next(line, p.col);
            if (p.col < line.length() && next < line.length()) {
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
        return new Target(new Pos(line, Text.last(c.buffer.line(line))), Target.Kind.INCLUSIVE, Column.END, false, false, true);
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

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/TextObjects.java`:

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
 * Text objects after an operator (spec 4.1), ported from Vim's textobject.c (current_word, current_sent,
 * current_par, current_block, current_quote). Plan V1b adds their Visual-mode branches. Tags are Jasper's own
 * scanner (Vim's current_tagblock is regex-driven). An object returns null to fail.
 */
final class TextObjects {
    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z][^\\s/>]*)[^>]*?(/?)>");

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

    /** current_word. */
    static Span word(Context c, boolean include, boolean bigword) {
        Walk walk = c.walk(bigword);
        Pos cursor = c.cursor();
        int count = c.count;
        boolean inclusive = true, includeWhite = false;
        walk.backInLine(cursor);
        Pos start = cursor.copy();
        if ((walk.cls(cursor) == 0) == include) {
            if (!walk.endWord(cursor, 1, true, true)) return null;
        } else {
            walk.fwdWord(cursor, 1, true);
            if (cursor.col == 0) walk.decl(cursor);
            else walk.oneLeft(cursor);
            if (include) includeWhite = true;
        }
        count--;
        while (count > 0) {
            inclusive = true;
            if (walk.incl(cursor) == Walk.FAIL) return null;
            if (include != (walk.cls(cursor) == 0)) {
                if (!walk.fwdWord(cursor, 1, true) && count > 1) return null;
                if (!walk.oneLeft(cursor)) inclusive = false;
            } else if (!walk.endWord(cursor, 1, true, true)) return null;
            count--;
        }
        if (includeWhite && (walk.cls(cursor) != 0 || cursor.col == 0 && !inclusive)) {
            // Take white space before the word instead ("daw" on the last word of a sentence), but not indent.
            Pos back = start.copy();
            if (walk.oneLeft(back)) {
                walk.backInLine(back);
                if (walk.cls(back) == 0 && back.col > 0) start = back;
            }
        }
        return new Span(start, cursor, inclusive ? Kind.INCLUSIVE : Kind.EXCLUSIVE);
    }

    /** current_sent. */
    static Span sentence(Context c, boolean include) {
        Walk walk = c.walk(false);
        Pos startPos = c.cursor(), pos = startPos.copy(), cursor = startPos.copy();
        Motions.findSentence(walk, cursor, true, 1);
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
        int ncount = include ? c.count * 2 : c.count - (startBlank ? 1 : 0);
        if (ncount > 0) findSentenceForward(walk, cursor, ncount, true);
        else walk.decl(cursor);
        if (include) {
            if (startBlank) {
                findFirstBlank(walk, cursor);
                ch = walk.gchar(cursor);
                if (ch == ' ' || ch == '\t') walk.decl(cursor);
            } else if ((ch = walk.gchar(cursor)) != ' ' && ch != '\t') findFirstBlank(walk, startPos);
        }
        boolean inclusive = walk.incl(cursor) == Walk.FAIL;
        return new Span(startPos, cursor, inclusive ? Kind.INCLUSIVE : Kind.EXCLUSIVE);
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
        return new Span(new Pos(start, 0), new Pos(end, 0), Kind.LINEWISE);
    }

    private static boolean white(Buffer b, int line) { return Text.blank(b.line(line)); }

    /** current_block. */
    static Span block(Context c, boolean include, char open, char close) {
        Buffer b = c.buffer;
        Walk walk = c.walk(false);
        Pos cursor = c.cursor();
        if (open == '{') while (walk.inIndent(cursor, 1)) if (walk.inc(cursor) != 0) break;
        if (walk.gchar(cursor) == open) cursor.col++;
        Pos start = null;
        Pos found = Matching.unmatched(b, cursor, open, close, true, false);
        if (found != null) {
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
        Pos end = Matching.unmatched(b, cursor, open, close, false, false);
        if (end == null) return null;
        boolean sol = false;
        if (!include) {
            walk.incl(start);
            sol = end.col == 0;
            walk.decl(end);
            while (walk.inIndent(end, 1)) {
                sol = true;
                if (walk.decl(end) != 0) break;
            }
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
        String line = c.buffer.line(cursor.line);
        int colStart = cursor.col, colEnd;
        if (colStart < line.length() && line.charAt(colStart) == quote) {
            int firstCol = colStart;
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
            colStart = previousQuote(line, colStart, quote);
            if (colStart >= line.length() || line.charAt(colStart) != quote) {
                colStart = nextQuote(line, colStart, quote, false);
                if (colStart < 0) return null;
            }
            colEnd = nextQuote(line, colStart + 1, quote, true);
            if (colEnd < 0) return null;
        }
        if (include) {
            if (colEnd + 1 < line.length() && white(line.charAt(colEnd + 1))) {
                while (colEnd + 1 < line.length() && white(line.charAt(colEnd + 1))) colEnd++;
            } else while (colStart > 0 && white(line.charAt(colStart - 1))) colStart--;
        }
        if (!include && c.count < 2) colStart++;
        Pos start = new Pos(cursor.line, colStart), end = new Pos(cursor.line, colEnd);
        boolean inclusive = false;
        if (include || c.count > 1) {
            int next = Text.next(line, colEnd);
            if (next >= line.length()) inclusive = true;
            end.col = next;
        }
        return new Span(start, end, inclusive ? Kind.INCLUSIVE : Kind.EXCLUSIVE);
    }

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

    /** find_prev_quote. */
    private static int previousQuote(String line, int colStart, char quote) {
        while (colStart > 0) {
            colStart = Text.previous(line, colStart);
            int n = 0;
            while (colStart - n > 0 && line.charAt(colStart - n - 1) == '\\') n++;
            if ((n & 1) == 1) colStart -= n;
            else if (line.charAt(colStart) == quote) break;
        }
        return colStart;
    }

    /** it and at: the {@code count}-th tag block around the cursor, by Jasper's tag scanner. */
    static Span tag(Context c, boolean include) {
        Buffer b = c.buffer;
        String text = b.text();
        int cursor = b.offset(c.cursor());
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
                if (openStart <= cursor && cursor < m.end()) around.add(new int[] {openStart, openEnd, m.start(), m.end()});
                break;
            }
        }
        if (around.size() < c.count) return null;
        around.sort((x, y) -> Integer.compare(y[0], x[0]));
        int[] pair = around.get(c.count - 1);
        int from = include ? pair[0] : pair[1], to = include ? pair[3] : pair[2];
        return new Span(b.pos(from), b.pos(to), Kind.EXCLUSIVE);
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Operators.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Registers.Content;
import dev.jasper.editor.vim.Target.Kind;
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
        if (!change && !s.linewise() && s.lineCount() > 1 && c.forced == 0) {
            // A charwise delete over lines that starts in the indent and ends at the end of a line becomes linewise.
            String endLine = b.line(s.end().line);
            int after = s.end().col;
            if (after < endLine.length() && s.kind() == Kind.INCLUSIVE) after = Text.next(endLine, after);
            if (Text.blank(endLine.substring(Math.min(after, endLine.length()))) && c.walk(false).inIndent(s.start(), 0))
                s = new Span(s.start(), s.end(), Kind.LINEWISE, s.regOne());
        }
        if (s.linewise()) {
            c.registers.delete(c.register, content(b, s), s.lineCount(), s.regOne(), c.options.string("clipboard"));
            b.deleteLines(s.start().line, s.end().line);
            int line = Math.min(s.start().line, b.count() - 1);
            c.editor.setCursor(new Pos(line, Text.firstNonBlank(b.line(line))));
            return;
        }
        int from = b.offset(s.start()), to = end(b, s);
        if (to <= from) return;
        c.registers.delete(c.register, content(b, s), s.lineCount(), s.regOne(), c.options.string("clipboard"));
        b.remove(from, to);
        c.editor.setCursor(b.pos(from));
    }

    /** op_change. */
    static void change(Context c, Span span) {
        Buffer b = c.buffer;
        if (span.linewise()) {
            c.registers.delete(c.register, content(b, span), span.lineCount(), span.regOne(), c.options.string("clipboard"));
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

    /** op_yank. */
    static void yank(Context c, Span span) {
        c.registers.yank(c.register, content(c.buffer, span), c.options.string("clipboard"));
    }

    /** op_shift, with shift_line's 'shiftround'. */
    static void shift(Context c, Span span, boolean left) {
        Buffer b = c.buffer;
        int sw = c.options.shiftwidth(), ts = c.tabstop();
        boolean et = c.options.flag("expandtab"), round = c.options.flag("shiftround");
        for (int line = span.start().line; line <= span.end().line; line++) {
            String text = b.line(line);
            if (text.isEmpty()) continue;
            int indent = Text.indent(text, ts), amount = 1, width;
            if (round) {
                int i = indent / sw, j = indent % sw;
                if (j != 0 && left) amount--;
                i = left ? Math.max(0, i - amount) : i + amount;
                width = i * sw;
            } else width = left ? Math.max(0, indent - sw) : indent + sw;
            b.setLine(line, Text.makeIndent(width, ts, et) + text.substring(Text.firstNonBlank(text)));
        }
        int first = span.start().line;
        c.editor.setCursor(new Pos(first, c.options.flag("startofline") ? Text.firstNonBlank(b.line(first)) : c.editor.wantedColumn(first)));
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
        int from, to;
        if (span.linewise()) {
            from = b.start(span.start().line);
            to = b.end(span.end().line);
        } else {
            from = b.offset(span.start());
            to = end(b, span);
        }
        if (to <= from) return;
        String text = b.text().substring(from, to);
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

    /** @return the span's text as a register holds it */
    static Content content(Buffer b, Span span) {
        if (span.linewise()) return new Content(b.lines(span.start().line, span.end().line), Registers.Type.LINE);
        return Content.of(b.text().substring(b.offset(span.start()), end(b, span)), Registers.Type.CHAR);
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

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/NormalCommands.java`:

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

    static void joinLines(Context c, int first, int count, boolean insertSpace) {
        Buffer b = c.buffer;
        boolean js = c.options.flag("joinspaces");
        StringBuilder out = new StringBuilder();
        int col = 0, endcurr1 = 0, endcurr2 = 0;
        for (int t = 0; t < count; t++) {
            String curr = b.line(first + t);
            int spaces = 0;
            if (insertSpace && t > 0) {
                curr = curr.substring(Text.firstNonBlank(curr));
                if (!curr.isEmpty() && curr.charAt(0) != ')' && !out.isEmpty() && endcurr1 != '\t') {
                    if (endcurr1 == ' ') endcurr1 = endcurr2;
                    else spaces++;
                    if (js && (endcurr1 == '.' || endcurr1 == '?' || endcurr1 == '!')) spaces++;
                }
            }
            if (t == count - 1) col = out.length();
            out.append(" ".repeat(spaces)).append(curr);
            endcurr1 = endcurr2 = 0;
            if (insertSpace && !curr.isEmpty()) {
                int lastStart = curr.offsetByCodePoints(curr.length(), -1);
                endcurr1 = curr.codePointAt(lastStart);
                if (lastStart > 0) endcurr2 = curr.codePointBefore(lastStart);
            }
        }
        b.replace(b.start(first), b.end(first + count - 1), out.toString());
        c.editor.setCursor(new Pos(first, col));
    }

    /** do_put. */
    static void put(Context c, boolean after, boolean cursorAfter, boolean fixIndent) {
        char requested = Registers.effective(c.register, c.options.string("clipboard"));
        char name = requested == 0 ? '"' : requested;
        Content content = c.registers.get(name).orElseThrow(() -> new VimFailure("E353: Nothing in register " + name));
        Buffer b = c.buffer;
        Pos p = c.cursor();
        if (content.type() == Registers.Type.LINE) {
            List<String> lines = new ArrayList<>();
            for (int n = 0; n < c.count; n++) lines.addAll(content.lines());
            if (fixIndent) lines = reindented(c, lines, Text.indent(b.line(p.line), c.tabstop()));
            int at = after ? p.line + 1 : p.line;
            b.insertLines(at, lines);
            if (cursorAfter) {
                int line = at + lines.size();
                c.editor.setCursor(new Pos(Math.min(line, b.count() - 1), 0));
            } else c.editor.setCursor(new Pos(at, Text.firstNonBlank(b.line(at))));
            return;
        }
        String line = b.line(p.line);
        int col = after && !line.isEmpty() ? Text.next(line, p.col) : p.col;
        String text = content.text().repeat(c.count);
        int offset = b.offset(p.line, col);
        b.insert(offset, text);
        if (content.lines().size() == 1) {
            if (cursorAfter) c.editor.setCursor(new Pos(p.line, col + text.length()));
            else c.editor.setCursor(new Pos(p.line, col + Text.last(text)));
        } else c.editor.setCursor(cursorAfter ? b.pos(offset + text.length()) : new Pos(p.line, col));
    }

    /** ]p: the first line gets {@code target} indent and the others keep their indent relative to it. */
    private static List<String> reindented(Context c, List<String> lines, int target) {
        int ts = c.tabstop();
        boolean et = c.options.flag("expandtab");
        int delta = target - Text.indent(lines.getFirst(), ts);
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (Text.blank(line)) out.add(line);
            else out.add(Text.makeIndent(Math.max(0, Text.indent(line, ts) + delta), ts, et) + line.substring(Text.firstNonBlank(line)));
        }
        return out;
    }
}
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.*'
```

Expected: every test in `dev.jasper.editor.vim` passes (36 tests from Tasks 1–3, plus 13 new). `VimEditorTest.anUnexpectedErrorCancelsTheCommandAndReturnsToNormalMode` logs "Vim command failed" with a stack trace on purpose.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava :jasper-plugin-editor:verifyPluginArchitecture 2>&1 | grep -i "warning\|error\|FAIL" || echo clean
```

Expected: `clean`: no compiler warnings (the build uses `-Xlint:all`), and the Editor still uses only the SDK and RSyntaxTextArea. (If `verifyPluginArchitecture` is not a task of the plugin project, run `./gradlew verifyPluginArchitecture`.)

- [ ] **Step 5: Commit**

```bash
git add plugins/editor/src/main/java/dev/jasper/editor/vim plugins/editor/src/test/java/dev/jasper/editor/vim
git commit -m "Add the Vim engine: parser, modes, motions, text objects, operators and undo steps

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 5: The differential corpus

**Files:**
- Modify: `plugins/editor/build.gradle.kts`
- Create in `plugins/editor/src/test/java/dev/jasper/editor/vim/`: `Json.java`, `CorpusCase.java`, `CorpusFile.java`, `CorpusRunner.java`, `RecordCorpus.java`, `VimCorpusTest.java`
- Create in `plugins/editor/src/test/resources/vim/corpus/`: `motions-char.case`, `motions-word.case`, `motions-find.case`, `motions-text.case`, `operators-delete.case`, `operators-change.case`, `operators-yank-put.case`, `operators-other.case`, `objects.case`, `insert.case`, `replace.case`, `repeat-undo.case`, `edges.case`

**Interfaces:**
- Consumes: the engine (Task 4), including the package-private `VimEditor.setCursor`, `cursor`, `buffer`, `Registers.set` and `Vim.coverage`.
- Produces: `VimCorpusTest.cases()` (Task 8 reads the divergent cases); `CorpusRunner.run(CorpusCase, Consumer<String>)` and `compare`; the Gradle task `recordVimCorpus`.

The corpus format is spec 11.1's, with the fields listed in this plan's deviations. Text lines are indented two spaces; a line that is empty or ends in white space or `$` is written with a `$` after it, and one trailing `$` is dropped when read. The case files below have no expectations yet (except the two divergent `=` cases, whose expectations are hand-written): Step 4 records them from Vim.

- [ ] **Step 1: Add the recording task and the docs properties to the build**

Replace the whole of `plugins/editor/build.gradle.kts` with:

```kotlin
// A plugin compiles against the SDK only; the application supplies it at run time. RSyntaxTextArea is bundled:
// stagePlugins copies the runtime classpath beside the plugin jar, and PluginClassLoader loads it.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    implementation("com.fifesoft:rsyntaxtextarea:3.6.0")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
}

tasks.test {
    // VimDocsTest reads, and with -PupdateDocs=true rewrites, files in the repository's docs folder.
    systemProperty("jasper.repoRoot", rootDir.absolutePath)
    systemProperty("jasper.updateDocs", providers.gradleProperty("updateDocs").getOrElse("false"))
    inputs.files(rootDir.resolve("docs/vim-divergences.md"), rootDir.resolve("docs/editor-vim-reference.md")).optional()
}

// Records the Vim corpus's expected results by running real Vim (Vim spec 11.1). Needs vim on PATH, or
// -PvimBinary=/path/to/vim; -Pcase=<text> records only the cases whose names contain the text. Not part of check.
tasks.register<JavaExec>("recordVimCorpus") {
    group = "verification"
    description = "Records the expected results of the Vim corpus by running real Vim."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.jasper.editor.vim.RecordCorpus"
    args(layout.projectDirectory.dir("src/test/resources/vim/corpus").asFile.absolutePath,
        providers.gradleProperty("vimBinary").getOrElse("vim"), providers.gradleProperty("case").getOrElse(""))
}
```

- [ ] **Step 2: Write the harness and the replay test**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/Json.java`:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Just enough JSON for the corpus: quoting strings, and reading what Vim's json_encode writes. */
final class Json {
    private final String text;
    private int at;

    private Json(String text) { this.text = text; }

    /** @return a JSON string literal for the text */
    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c < 0x20 || c == 0x7f || Character.isSurrogate(c) || c >= 0xe000 && c <= 0xf8ff) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    /** @return strings, longs, booleans, null, lists and maps */
    static Object parse(String text) {
        var json = new Json(text);
        Object value = json.value();
        json.space();
        if (json.at != text.length()) throw new IllegalArgumentException("Trailing JSON at " + json.at + ": " + text);
        return value;
    }

    /** @return the string a JSON string literal holds */
    static String unquote(String literal) { return (String) parse(literal); }

    private Object value() {
        space();
        char c = text.charAt(at);
        switch (c) {
            case '{' -> {
                at++;
                Map<String, Object> map = new LinkedHashMap<>();
                space();
                if (text.charAt(at) == '}') {
                    at++;
                    return map;
                }
                while (true) {
                    space();
                    String key = string();
                    space();
                    expect(':');
                    map.put(key, value());
                    space();
                    if (text.charAt(at) == ',') at++;
                    else {
                        expect('}');
                        return map;
                    }
                }
            }
            case '[' -> {
                at++;
                List<Object> list = new ArrayList<>();
                space();
                if (text.charAt(at) == ']') {
                    at++;
                    return list;
                }
                while (true) {
                    list.add(value());
                    space();
                    if (text.charAt(at) == ',') at++;
                    else {
                        expect(']');
                        return list;
                    }
                }
            }
            case '"' -> { return string(); }
            default -> {
                if (text.startsWith("true", at)) { at += 4; return true; }
                if (text.startsWith("false", at)) { at += 5; return false; }
                if (text.startsWith("null", at) || text.startsWith("v:null", at)) { at += text.startsWith("null", at) ? 4 : 6; return null; }
                int start = at;
                while (at < text.length() && "-+.0123456789eE".indexOf(text.charAt(at)) >= 0) at++;
                return Long.parseLong(text.substring(start, at));
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = text.charAt(at++);
            if (c == '"') return out.toString();
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = text.charAt(at++);
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                }
                default -> out.append(e);
            }
        }
    }

    private void expect(char c) {
        if (text.charAt(at) != c) throw new IllegalArgumentException("Expected " + c + " at " + at + " in " + text);
        at++;
    }

    private void space() { while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++; }
}
```

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java`:

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
     * @param type {@code c} charwise or {@code l} linewise
     * @param text its text, as getreg() gives it
     */
    record Register(char type, String text) { }

    /**
     * The result.
     *
     * @param recordedBy the Vim that recorded it ({@code vim 9.1}), or {@code expected} for a divergent case
     * @param text the lines
     * @param line the cursor line, 1-based
     * @param col the cursor column in characters, 1-based
     * @param mode {@link Mode#id()}
     * @param registers the compared registers; a missing value means empty
     */
    record Expected(String recordedBy, List<String> text, int line, int col, String mode, Map<Character, Register> registers) { }

    CorpusCase withExpected(Expected value) { return new CorpusCase(file, name, divergent, set, given, text, line, col, keys, registers, value); }

    @Override public String toString() { return file + ": " + name; }
}
```

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java`:

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
        String mode = "normal";
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
                default -> {
                    if (!key.startsWith("expect-register ")) throw error(file, i, "unknown field " + key);
                    if (!value.equals("none")) registers.put(key.charAt(16), register(value));
                }
            }
            i++;
        }
        return new Parsed(new CorpusCase.Expected(by, text, line, col, mode, registers), i);
    }

    private static CorpusCase.Register register(String value) {
        return new CorpusCase.Register(value.charAt(0), Json.unquote(value.substring(1).trim()));
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

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusRunner.java`:

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
            vim.registers().set(entry.getKey(), Registers.Content.of(register.text(), register.type() == 'l' ? Registers.Type.LINE : Registers.Type.CHAR));
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
            vim.registers().get(name).ifPresent(content -> registers.put(name,
                new CorpusCase.Register(content.type() == Registers.Type.LINE ? 'l' : 'c', content.text())));
        }
        editor.close();
        return new CorpusCase.Expected("jasper", text, cursor.line + 1, col, editor.mode().id(), registers);
    }

    /** @return the differences from the expectation, or empty */
    static String compare(CorpusCase.Expected expected, CorpusCase.Expected actual) {
        List<String> problems = new ArrayList<>();
        if (!expected.text().equals(actual.text())) problems.add("text " + actual.text() + " (Vim: " + expected.text() + ")");
        if (expected.line() != actual.line() || expected.col() != actual.col())
            problems.add("cursor " + actual.line() + "," + actual.col() + " (Vim: " + expected.line() + "," + expected.col() + ")");
        if (!expected.mode().equals(actual.mode())) problems.add("mode " + actual.mode() + " (Vim: " + expected.mode() + ")");
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

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java`:

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
                char kind = type.equals("V") ? 'l' : type.equals("v") ? 'c' : 'b';
                String joined = String.join("\n", lines) + (kind == 'l' ? "\n" : "");
                registers.put(entry.getKey().charAt(0), new CorpusCase.Register(kind, joined));
            }
            return new CorpusCase.Expected(version, text, ((Long) result.get("line")).intValue(), ((Long) result.get("col")).intValue(),
                mode((String) result.get("mode")), registers);
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
        return vim;
    }

    /** @return a Vim script that sets the case up, types its keys and writes the result to {@code out} */
    static String script(CorpusCase c, Path out, Path file) {
        List<String> lines = new ArrayList<>();
        lines.add("set encoding=utf-8");
        lines.add("scriptencoding utf-8");
        lines.add("set nocompatible");
        lines.add("function! JasperRecord()");
        lines.add("  let g:jasper = {'text': getline(1, '$'), 'line': line('.'), 'col': charcol('.'), 'mode': mode(1), 'regs': {}}");
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
            String text = register.type() == 'l' && register.text().endsWith("\n") ? register.text().substring(0, register.text().length() - 1) : register.text();
            lines.add("call setreg(" + Json.quote(String.valueOf(entry.getKey())) + ", " + vimList(List.of(text.split("\n", -1))) + ", " + (register.type() == 'l' ? "'l'" : "'c'") + ")");
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

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/VimCorpusTest.java`:

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
        assertThat(c.given().get('a')).isEqualTo(new CorpusCase.Register('l', "x\n"));
        assertThat(c.expected().registers()).containsOnlyKeys('"');
        assertThat(c.expected().registers().get('"').text()).isEqualTo("one\ttwo");
        assertThat(CorpusFile.format(c, c.expected())).containsExactly("--- vim 9.1", "expect-text:", "  done", "expect-cursor: 1,1",
            "expect-mode: insert", "expect-register \": c \"one\\ttwo\"", "expect-register a: none");
        assertThat(RecordCorpus.vimKeys("a\"b\\<Esc><C-w>")).isEqualTo("a\\\"b\\\\\\<Esc>\\<C-w>");
        assertThat(RecordCorpus.mode("no")).isEqualTo("operator-pending");
    }
}
```

- [ ] **Step 3: Write the corpus**

Create `plugins/editor/src/test/resources/vim/corpus/motions-char.case`:

````text
# Character and line motions without an operator.

=== h moves left
text:
  hello world
cursor: 1,5
keys: h

=== 3h stops at column 1
text:
  hello
cursor: 1,2
keys: 3h

=== h at column 1 fails and stays
text:
  hello
cursor: 1,1
keys: hx

=== l moves right
text:
  hello
cursor: 1,1
keys: 2l

=== l stops on the last character
text:
  hello
cursor: 1,4
keys: 5l

=== Left and Right arrows
text:
  hello
cursor: 1,3
keys: <Left><Left><Right>

=== BS wraps to the end of the previous line
text:
  one
  two
cursor: 2,1
keys: <BS>

=== Space wraps to the next line
text:
  one
  two
cursor: 1,3
keys: <Space>

=== 5 Space crosses lines
text:
  ab
  cd
cursor: 1,1
keys: 5<Space>

=== C-h moves left like BS
text:
  one
  two
cursor: 2,2
keys: <C-h><C-h>

=== j keeps the column
text:
  hello world
  hi there friend
cursor: 1,7
keys: j

=== j onto a shorter line lands on its last character
text:
  hello world
  hi
  goodbye world
cursor: 1,9
keys: j

=== j then j again restores the column
text:
  hello world
  hi
  goodbye world
cursor: 1,9
keys: jj

=== k keeps the column
text:
  hello world
  hi there friend
cursor: 2,9
keys: k

=== 5j stops on the last line
text:
  a
  b
  c
cursor: 1,1
keys: 5j

=== j on the last line fails
text:
  a
  b
cursor: 2,1
keys: jx

=== k on the first line fails
text:
  a
  b
cursor: 1,1
keys: kx

=== Down, Up, C-n, C-p and C-j
text:
  one
  two
  three
cursor: 1,2
keys: <Down><C-n><Up><C-p><C-j>

=== j across a tab keeps the display column
text:
  a	b
  12345678901
cursor: 2,9
keys: k

=== j from a tab lands at the same display column
text:
  	x
  abcdefghij
cursor: 1,1
keys: j

=== j across wide characters
text:
  日本語です
  abcdefghij
cursor: 1,3
keys: j

=== zero goes to column 1
text:
    indented
cursor: 1,8
keys: 0

=== Home goes to column 1
text:
    indented
cursor: 1,8
keys: <Home>

=== caret goes to the first non-blank
text:
    indented line
cursor: 1,12
keys: ^

=== caret on a blank line
text:
  one
      $
cursor: 2,1
keys: ^

=== dollar goes to the last character
text:
  hello world
cursor: 1,1
keys: $

=== 2 dollar goes to the end of the next line
text:
  one
  three
cursor: 1,1
keys: 2$

=== dollar then j keeps the end
text:
  hi
  hello world
  a
cursor: 1,1
keys: $jj

=== End goes to the last character
text:
  hello
cursor: 1,1
keys: <End>

=== g underscore goes to the last non-blank
text:
  hello   $
cursor: 1,1
keys: g_

=== 2g underscore on the next line
text:
  one
  two   $
cursor: 1,1
keys: 2g_

=== bar goes to a display column
text:
  hello world
cursor: 1,1
keys: 7|

=== bar past the end stops on the last character
text:
  hello
cursor: 1,1
keys: 20|

=== bar without a count goes to column 1
text:
  hello
cursor: 1,4
keys: |

=== plus goes to the next line's first non-blank
text:
  one
     two
cursor: 1,2
keys: +

=== Enter goes down to the first non-blank
text:
  one
    two
    three
cursor: 1,1
keys: 2<CR>

=== minus goes up to the first non-blank
text:
    one
  two
cursor: 2,3
keys: -

=== underscore stays on the line at its first non-blank
text:
     one
cursor: 1,7
keys: _

=== 2 underscore goes down one line
text:
  one
    two
cursor: 1,1
keys: 2_

=== G goes to the last line's first non-blank
text:
  one
  two
    three
cursor: 1,1
keys: G

=== 2G goes to line 2
text:
  one
    two
  three
cursor: 3,3
keys: 2G

=== 9G past the end goes to the last line
text:
  one
  two
cursor: 1,1
keys: 9G

=== gg goes to the first line
text:
    one
  two
cursor: 2,2
keys: gg

=== 3gg goes to line 3
text:
  one
  two
  three
  four
cursor: 1,1
keys: 3gg

=== G with nostartofline keeps the column
set: nostartofline
text:
  one two
  three four
cursor: 1,5
keys: G

=== 50 percent goes to the middle line
text:
  1
  2
  3
  4
cursor: 1,1
keys: 50%

=== 100 percent goes to the last line
text:
  1
  2
  3
cursor: 1,1
keys: 100%

=== go goes to a character of the text
text:
  abc
  def
cursor: 1,1
keys: 6go

=== x after moving shows where the cursor was
text:
  abcdef
cursor: 1,1
keys: 3lx

=== C-l does nothing
text:
  abc
cursor: 1,2
keys: <C-l>x
````

Create `plugins/editor/src/test/resources/vim/corpus/motions-word.case`:

````text
# Word motions, alone and with operators.

=== w moves to the next word
text:
  one two three
cursor: 1,1
keys: w

=== w stops at punctuation
text:
  foo.bar baz
cursor: 1,1
keys: w

=== 3w crosses lines
text:
  one two
  three four
cursor: 1,1
keys: 3w

=== w stops on an empty line
text:
  one
  $
  two
cursor: 1,1
keys: w

=== w on the last word goes to its end
text:
  one two
cursor: 1,5
keys: w

=== w on the last character of the text fails
text:
  one two
cursor: 1,7
keys: wx

=== W skips punctuation
text:
  foo.bar baz
cursor: 1,1
keys: W

=== b goes back to the start of the word
text:
  one two
cursor: 1,6
keys: b

=== b from a word start goes to the previous word
text:
  one two
cursor: 1,5
keys: b

=== 2b crosses lines
text:
  one two
  three
cursor: 2,3
keys: 2b

=== b stops on an empty line
text:
  one
  $
  two
cursor: 3,1
keys: b

=== B skips punctuation
text:
  foo.bar baz
cursor: 1,9
keys: B

=== b at the start fails
text:
  one
cursor: 1,1
keys: bx

=== e goes to the end of the word
text:
  one two
cursor: 1,1
keys: e

=== e from the end goes to the next end
text:
  one two
cursor: 1,3
keys: e

=== e over punctuation
text:
  foo.bar
cursor: 1,1
keys: ee

=== E goes to the end of the WORD
text:
  foo.bar baz
cursor: 1,1
keys: E

=== ge goes back to the previous end
text:
  one two
cursor: 1,6
keys: ge

=== gE goes back to the previous WORD end
text:
  foo.bar baz
cursor: 1,10
keys: gE

=== w with keywords set to include dashes
set: iskeyword+=-
text:
  foo-bar baz
cursor: 1,1
keys: w

=== w between CJK and Latin
text:
  日本abc def
cursor: 1,1
keys: w

=== dw deletes to the next word
text:
  one two three
cursor: 1,5
keys: dw
registers: " -

=== dw on the last word of a line stops at the end of the line
text:
  one two
  three
cursor: 1,5
keys: dw
registers: " -

=== 2dw crosses a line end
text:
  one two
  three four
cursor: 1,5
keys: 2dw
registers: " 1

=== dw on an empty line deletes the line
text:
  one
  $
  two
cursor: 2,1
keys: dw
registers: " 1

=== dw on the last word of the text
text:
  one two
cursor: 1,5
keys: dw

=== dw on the last character of the text
text:
  one two
cursor: 1,7
keys: dw

=== dW deletes a WORD
text:
  foo.bar baz
cursor: 1,1
keys: dW

=== cw changes to the end of the word
text:
  one two three
cursor: 1,5
keys: cwTWO<Esc>
registers: "

=== cw on a one-letter word
text:
  a b c
cursor: 1,3
keys: cwx<Esc>

=== cw on white space
text:
  one   two
cursor: 1,4
keys: cwX<Esc>

=== 2cw changes two words
text:
  one two three
cursor: 1,1
keys: 2cwX<Esc>

=== cW changes a WORD
text:
  foo.bar baz
cursor: 1,1
keys: cWX<Esc>

=== de deletes to the end of the word
text:
  one two
cursor: 1,1
keys: de

=== db deletes back to the word start
text:
  one two
cursor: 1,7
keys: db

=== dge deletes back to the previous end
text:
  one two
cursor: 1,6
keys: dge

=== yw yanks a word
text:
  one two
cursor: 1,1
keys: yw
registers: " 0
````

Create `plugins/editor/src/test/resources/vim/corpus/motions-find.case`:

````text
# f, F, t, T, ; and , within a line.

=== f finds a character to the right
text:
  hello world
cursor: 1,1
keys: fo

=== 2f finds the second occurrence
text:
  hello world
cursor: 1,1
keys: 2fo

=== f with no match fails
text:
  hello
cursor: 1,1
keys: fzx

=== F finds a character to the left
text:
  hello world
cursor: 1,11
keys: Fo

=== t stops before the character
text:
  hello world
cursor: 1,1
keys: tw

=== T stops after the character
text:
  hello world
cursor: 1,11
keys: To

=== semicolon repeats f
text:
  a-b-c-d
cursor: 1,1
keys: f-;;

=== comma repeats f backwards
text:
  a-b-c-d
cursor: 1,1
keys: f-;;,

=== semicolon after t skips the adjacent match
text:
  a-b-c-d
cursor: 1,1
keys: t-;

=== comma after F goes right
text:
  a-b-c-d
cursor: 1,7
keys: F-,

=== 2 semicolon
text:
  a-b-c-d-e
cursor: 1,1
keys: f-2;

=== f finds a tab
text:
  a	b
cursor: 1,1
keys: f<Tab>x

=== df deletes through the character
text:
  one, two
cursor: 1,1
keys: df,

=== dt deletes up to the character
text:
  one, two
cursor: 1,1
keys: dt,

=== dF deletes back to the character, not the cursor's
text:
  one, two
cursor: 1,8
keys: dF,

=== dT deletes back to after the character
text:
  one, two
cursor: 1,8
keys: dT,

=== c semicolon uses the last search
text:
  a(b)c(d)
cursor: 1,1
keys: f(;cfX)<Esc>

=== semicolon without a previous search fails
text:
  abc
cursor: 1,1
keys: ;x

=== f with a count past the matches fails
text:
  a-b
cursor: 1,1
keys: 3f-x
````

Create `plugins/editor/src/test/resources/vim/corpus/motions-text.case`:

````text
# Sentences, paragraphs, sections and brackets.

=== close paren goes to the next sentence
text:
  One two.  Three four.  Five.
cursor: 1,1
keys: )

=== 2 close paren
text:
  One two.  Three four.  Five.
cursor: 1,1
keys: 2)

=== open paren goes to the sentence start
text:
  One two.  Three four.  Five.
cursor: 1,17
keys: (

=== open paren from a sentence start goes to the previous one
text:
  One two.  Three four.  Five.
cursor: 1,11
keys: (

=== sentences across lines
text:
  First line ends here.
  Second sentence starts.
cursor: 1,1
keys: )

=== sentence end with a closing quote
text:
  He said "stop."  Then left.
cursor: 1,1
keys: )

=== close paren stops at an empty line
text:
  One sentence
  $
  Next paragraph.
cursor: 1,1
keys: )

=== d close paren deletes a sentence
text:
  One two.  Three four.
cursor: 1,1
keys: d)
registers: " 1 -

=== close brace goes to the next empty line
text:
  one
  two
  $
  three
cursor: 1,1
keys: }

=== close brace skips a white-space line
text:
  one
     $
  two
  $
  three
cursor: 1,1
keys: }

=== 2 close brace
text:
  a
  $
  b
  $
  c
cursor: 1,1
keys: 2}

=== close brace on the last paragraph goes to the last character
text:
  a
  $
  b c
cursor: 3,1
keys: }

=== open brace goes to the previous empty line
text:
  one
  $
  two
  three
cursor: 4,3
keys: {

=== open brace at the top goes to line 1
text:
  one
  two
cursor: 2,2
keys: {

=== d close brace deletes to the paragraph end
text:
  one
  two
  $
  three
cursor: 1,2
keys: d}
registers: " 1

=== d close brace from column 1 is linewise
text:
  one
  two
  $
  three
cursor: 1,1
keys: d}
registers: " 1

=== y close brace uses register 1 only for deletes
text:
  one
  $
  two
cursor: 1,1
keys: y}
registers: " 0 1

=== close brackets go to a brace in column 1
text:
  int a;
  {
    body;
  }
  int b;
  {
cursor: 1,1
keys: ]]

=== open brackets go back to a brace in column 1
text:
  {
    body;
  }
  int b;
cursor: 4,3
keys: [[

=== bracket pair go to a closing brace in column 1
text:
  {
    body;
  }
  int b;
cursor: 1,1
keys: ][

=== reverse bracket pair go back to a closing brace
text:
  {
  }
  int b;
cursor: 3,3
keys: []

=== percent jumps to the matching paren
text:
  if (a && (b || c)) x;
cursor: 1,4
keys: %

=== percent from a closing bracket
text:
  if (a && (b || c)) x;
cursor: 1,18
keys: %

=== percent finds the first bracket after the cursor
text:
  call(arg);
cursor: 1,1
keys: %

=== percent across lines
text:
  fn main() {
      body();
  }
cursor: 1,11
keys: %

=== percent skips brackets in quotes
text:
  f("a)b", c)
cursor: 1,2
keys: %

=== percent with no bracket fails
text:
  plain text
cursor: 1,1
keys: %x

=== d percent deletes the bracketed text
text:
  x(a, b)y
cursor: 1,2
keys: d%

=== percent with a count goes to a line
text:
  1
  2
  3
  4
  5
  6
  7
  8
  9
  10
cursor: 1,1
keys: 30%

=== open bracket paren goes back to the unmatched paren
text:
  f(a, (b), c)
cursor: 1,11
keys: [(

=== close bracket paren goes to the unmatched paren
text:
  f(a, (b), c)
cursor: 1,4
keys: ])

=== open bracket brace goes back to the unmatched brace
text:
  {
    if (x) { y; }
    z;
  }
cursor: 3,3
keys: [{

=== close bracket brace goes to the unmatched brace
text:
  {
    a;
  }
cursor: 2,3
keys: ]}

=== d close bracket paren
text:
  f(a, b)
cursor: 1,3
keys: d])
````

Create `plugins/editor/src/test/resources/vim/corpus/operators-delete.case`:

````text
# d with motions and lines, and the registers it writes.

=== dd deletes the line
text:
  one
    two
  three
cursor: 1,2
keys: dd
registers: " 1 -

=== 2dd deletes two lines
text:
  one
  two
  three
cursor: 1,1
keys: 2dd
registers: " 1

=== dd on the last line moves up
text:
  one
    two
cursor: 2,1
keys: dd

=== 5dd near the end deletes to the end
text:
  one
  two
  three
cursor: 2,1
keys: 5dd

=== 2dd on the last line fails
text:
  one
  two
cursor: 2,1
keys: 2dd

=== dd on the only line empties the text
text:
  only
cursor: 1,1
keys: dd

=== dj deletes two lines
text:
  one
  two
  three
cursor: 1,1
keys: dj

=== dk deletes the line above too
text:
  one
  two
  three
cursor: 2,1
keys: dk

=== dj on the last line fails
text:
  one
  two
cursor: 2,1
keys: dj

=== d2j equals 3dd
text:
  1
  2
  3
  4
cursor: 1,1
keys: d2j

=== 2d3l deletes six characters
text:
  abcdefghij
cursor: 1,1
keys: 2d3l

=== dl deletes the last character
text:
  abc
cursor: 1,3
keys: dl

=== x deletes the character
text:
  abc
cursor: 1,2
keys: x
registers: " -

=== 3x deletes three characters
text:
  abcdef
cursor: 1,2
keys: 3x

=== x past the end deletes to the end
text:
  abc
cursor: 1,2
keys: 9x

=== x on an empty line does nothing
text:
  one
  $
cursor: 2,1
keys: x

=== Del deletes like x
text:
  abc
cursor: 1,1
keys: <Del>

=== X deletes before the cursor
text:
  abcdef
cursor: 1,4
keys: 2X

=== X at column 1 fails
text:
  abc
cursor: 1,1
keys: X

=== D deletes to the end of the line
text:
  one two three
cursor: 1,5
keys: D
registers: " -

=== 2D deletes into the next line
text:
  one two
  three
  four
cursor: 1,5
keys: 2D

=== d dollar on an empty line
text:
  $
  x
cursor: 1,1
keys: d$

=== dh at column 1 does nothing
text:
  abc
cursor: 1,1
keys: dh

=== d zero deletes to column 1
text:
  one two
cursor: 1,5
keys: d0

=== d caret deletes to the first non-blank
text:
     one two
cursor: 1,9
keys: d^

=== dG deletes to the end
text:
  one
  two
  three
cursor: 2,1
keys: dG

=== dgg deletes to the top
text:
  one
  two
  three
cursor: 2,1
keys: dgg

=== dv j deletes charwise
text:
  one two
  three four
cursor: 1,5
keys: dvj

=== dV w deletes the line
text:
  one two
  three
cursor: 1,5
keys: dVw

=== dv e excludes the last character
text:
  one two
cursor: 1,1
keys: dve

=== BS with d joins lines
text:
  one
  two
cursor: 2,1
keys: d<BS>

=== d Space at the end of a line deletes the break
set: whichwrap=b,s
text:
  ab
  cd
cursor: 1,2
keys: 2d<Space>

=== named register delete
text:
  one two
cursor: 1,1
keys: "adw
registers: " a - 1

=== append to a named register
text:
  one two three
cursor: 1,1
keys: "adw"Adw
registers: " a

=== linewise delete into a named register writes register 1 too
text:
  one
  two
cursor: 1,1
keys: "add
registers: " a 1

=== black hole delete leaves the registers
register a: c "keep"
text:
  one two
cursor: 1,1
keys: "ayiw"_dw
registers: " a 0 -

=== numbered registers shift
text:
  1
  2
  3
cursor: 1,1
keys: dddddd
registers: " 1 2 3

=== small deletes do not shift the numbered registers
text:
  line
  a b c
cursor: 1,1
keys: ddxx
registers: " 1 2 -

=== multi-line charwise delete ending at a line end becomes linewise
text:
  one two
  three
  four
cursor: 1,1
keys: d3e
registers: " 1
````

Create `plugins/editor/src/test/resources/vim/corpus/operators-change.case`:

````text
# c, its abbreviations, and Insert mode entered by them.

=== cc replaces the line
text:
  one
  two
cursor: 1,2
keys: ccnew<Esc>
registers: " 1

=== cc keeps the indent with autoindent
set: autoindent
text:
      indented
cursor: 1,7
keys: ccx<Esc>

=== cc without autoindent drops the indent
text:
      indented
cursor: 1,7
keys: ccx<Esc>

=== cc with autoindent and nothing typed removes the indent
set: autoindent
text:
      indented
  next
cursor: 1,7
keys: cc<Esc>

=== 2cc replaces two lines
text:
  one
  two
  three
cursor: 1,1
keys: 2ccX<Esc>

=== S replaces the line
text:
  one
  two
cursor: 2,1
keys: SX<Esc>

=== C changes to the end of the line
text:
  one two three
cursor: 1,5
keys: CX<Esc>
registers: " -

=== s substitutes a character
text:
  abc
cursor: 1,2
keys: sX<Esc>

=== 3s substitutes three characters
text:
  abcdef
cursor: 1,2
keys: 3sX<Esc>

=== s at the end of the line
text:
  abc
cursor: 1,3
keys: sX<Esc>

=== c dollar leaves Insert mode at the end
text:
  one two
cursor: 1,5
keys: c$

=== cj changes two lines
text:
  one
  two
  three
cursor: 1,2
keys: cjX<Esc>

=== c zero changes to the line start
text:
  one two
cursor: 1,5
keys: c0X<Esc>

=== c with a named register
text:
  one two
cursor: 1,1
keys: "bcwX<Esc>
registers: " b -

=== cw at the end of a line
text:
  one two
  three
cursor: 1,5
keys: cwX<Esc>

=== c then Esc without typing moves left
text:
  one two
cursor: 1,5
keys: cw<Esc>
````

Create `plugins/editor/src/test/resources/vim/corpus/operators-yank-put.case`:

````text
# y and the put commands.

=== yy then p puts the line below
text:
  one
  two
cursor: 1,2
keys: yyp
registers: " 0

=== yy keeps the cursor
text:
  one two
cursor: 1,5
keys: yy

=== Y yanks the line
text:
  one two
  three
cursor: 1,5
keys: Yjp

=== 2yy then P puts above
text:
  one
  two
  three
cursor: 1,1
keys: 2yyjjP

=== yk moves the cursor up
text:
  one
  two
cursor: 2,2
keys: yk
registers: "

=== yiw moves to the word start
text:
  one two
cursor: 1,6
keys: yiw
registers: " 0

=== yw then P puts before the cursor
text:
  one two
cursor: 1,5
keys: ywP

=== p puts a word after the cursor
text:
  ab
cursor: 1,1
keys: ylp

=== 3p puts three times
text:
  ab
cursor: 1,1
keys: yl3p

=== p on an empty line
register a: c "xyz"
text:
  $
cursor: 1,1
keys: "ap

=== gp puts and moves after the text
text:
  abc def
cursor: 1,1
keys: yiwwgp

=== gP puts before and moves after the text
text:
  abc def
cursor: 1,1
keys: yiwwgP

=== gp linewise moves to the line after
text:
  one
  two
  three
cursor: 1,1
keys: yyjgp

=== gp linewise at the end of the text
text:
  one
  two
cursor: 1,1
keys: yyjgp

=== multi-line charwise put
register a: c "X\nY"
text:
  abc
cursor: 1,1
keys: "ap

=== multi-line charwise gp
register a: c "X\nY"
text:
  abc
cursor: 1,1
keys: "agp

=== linewise put of several lines
register a: l "one\ntwo\n"
text:
  x
cursor: 1,1
keys: "ap

=== bracket p adjusts the indent
register a: l "a\n  b\n"
text:
      here
cursor: 1,1
keys: "a]p

=== bracket P puts above with the indent
register a: l "a\n"
text:
    here
cursor: 1,1
keys: "a[P

=== put from an empty register fails
text:
  abc
cursor: 1,1
keys: "zp

=== xp swaps two characters
text:
  ab
cursor: 1,1
keys: xp

=== ddp swaps two lines
text:
  one
  two
cursor: 1,1
keys: ddp

=== yank into a named register and put it
text:
  one two
cursor: 1,1
keys: "qywwP
registers: " q 0

=== uppercase register appends linewise
text:
  one
  two
cursor: 1,1
keys: "ayyj"Ayy
registers: a

=== appending a line to a charwise register makes it linewise
text:
  one two
  three
cursor: 1,1
keys: "ayiwj"Ayy
registers: a

=== y then p of the zero register after a delete
text:
  one two
cursor: 1,1
keys: yiwwdiw"0P
registers: " 0 -

=== unnamed after a small delete is the minus register
text:
  one two
cursor: 1,1
keys: yiwxP
registers: " 0 -

=== open bracket p puts before with the indent
register a: l "x\n"
text:
    here
cursor: 1,1
keys: "a[p

=== close bracket P puts before with the indent
register a: l "x\n"
text:
    here
cursor: 1,1
keys: "a]P
````

Create `plugins/editor/src/test/resources/vim/corpus/operators-other.case`:

````text
# Shifting, case, joining and the other operators.

=== double greater-than shifts right
set: shiftwidth=4 expandtab
text:
  one
  two
cursor: 1,1
keys: >>

=== 3 greater-than shifts three lines
set: shiftwidth=2 expandtab
text:
  a
  b
  c
  d
cursor: 1,1
keys: 3>>

=== greater-than skips empty lines
set: shiftwidth=4 expandtab
text:
  a
  $
  b
cursor: 1,1
keys: >2j

=== shift with tabs
set: shiftwidth=4 tabstop=8
text:
      a
cursor: 1,1
keys: >>

=== less-than shifts left
set: shiftwidth=4
text:
          x
cursor: 1,1
keys: <lt><lt>

=== less-than past zero stops at zero
set: shiftwidth=4
text:
    x
cursor: 1,1
keys: 3<lt><lt>

=== shiftround rounds to a multiple
set: shiftwidth=4 expandtab shiftround
text:
     x
cursor: 1,1
keys: >>

=== greater-than with a paragraph
set: shiftwidth=2 expandtab
text:
  a
  b
  $
  c
cursor: 1,1
keys: >}

=== tilde toggles one character
text:
  abc
cursor: 1,1
keys: ~

=== 5 tilde stops at the end
text:
  aBc
cursor: 1,1
keys: 5~

=== tilde on an empty line fails
text:
  $
cursor: 1,1
keys: ~

=== g tilde tilde toggles the line
text:
  Hello World
cursor: 1,4
keys: g~~

=== g tilde with a word
text:
  hello world
cursor: 1,1
keys: g~w

=== gu lowers a word
text:
  HELLO WORLD
cursor: 1,3
keys: guiw

=== gU uppercases to the end
text:
  hello world
cursor: 1,7
keys: gU$

=== gUU uppercases the line
text:
  hello world
cursor: 1,7
keys: gUU

=== gUgU uppercases the line
text:
  hello world
cursor: 1,7
keys: gUgU

=== guu lowers two lines
text:
  ONE
  TWO
  THREE
cursor: 1,2
keys: 2guu

=== gU k moves the cursor up
text:
  one
  two
cursor: 2,2
keys: gUk

=== rot13 with g question mark
text:
  Hello, World
cursor: 1,1
keys: g??

=== tildeop makes tilde an operator
set: tildeop
text:
  hello world
cursor: 1,1
keys: ~w

=== tildeop tilde tilde
set: tildeop
text:
  hello
cursor: 1,3
keys: ~~

=== J joins with a space
text:
  one
    two
cursor: 1,1
keys: J

=== J with a count joins three lines
text:
  a
  b
  c
  d
cursor: 1,1
keys: 3J

=== J after a period adds two spaces with joinspaces
text:
  End.
  Next
cursor: 1,1
keys: J

=== J after a period with nojoinspaces
set: nojoinspaces
text:
  End.
  Next
cursor: 1,1
keys: J

=== J with a trailing space adds none
text:
  one $
  two
cursor: 1,1
keys: J

=== J before a closing paren adds no space
text:
  f(a
  )
cursor: 1,1
keys: J

=== J with an empty line
text:
  one
  $
  two
cursor: 1,1
keys: 3J

=== J on the last line fails
text:
  one
cursor: 1,1
keys: J

=== 10J past the end joins to the end
text:
  a
  b
  c
cursor: 1,1
keys: 10J

=== gJ joins without spaces
text:
  one
    two
cursor: 1,1
keys: gJ

=== equals reindents a block
divergent: = uses previous-line indent and bracket depth, not Vim's C-indenting
set: shiftwidth=4 expandtab
text:
  if (x) {
  y;
  z;
  }
cursor: 1,1
keys: =3j
--- expected
expect-text:
  if (x) {
      y;
      z;
  }
expect-cursor: 1,1
expect-mode: normal

=== equals equals on a blank line
divergent: = uses previous-line indent and bracket depth, not Vim's C-indenting
text:
  a
     $
cursor: 2,1
keys: ==
--- expected
expect-text:
  a
  $
expect-cursor: 2,1
expect-mode: normal
````

Create `plugins/editor/src/test/resources/vim/corpus/objects.case`:

````text
# Text objects after an operator.

=== diw deletes the word
text:
  one two three
cursor: 1,6
keys: diw

=== diw on white space deletes the white space
text:
  one   two
cursor: 1,5
keys: diw

=== daw deletes the word and a space
text:
  one two three
cursor: 1,6
keys: daw

=== daw on the last word takes the space before
text:
  one two
cursor: 1,6
keys: daw

=== d2aw deletes two words
text:
  one two three four
cursor: 1,1
keys: d2aw

=== diW deletes a WORD
text:
  a foo.bar b
cursor: 1,5
keys: diW

=== daW deletes a WORD with space
text:
  a foo.bar b
cursor: 1,5
keys: daW

=== ciw changes the word
text:
  one two three
cursor: 1,5
keys: ciwX<Esc>

=== yiw on punctuation
text:
  a...b
cursor: 1,3
keys: yiw
registers: "

=== dis deletes a sentence
text:
  One two.  Three four.  Five.
cursor: 1,13
keys: dis

=== das deletes a sentence and its space
text:
  One two.  Three four.  Five.
cursor: 1,13
keys: das

=== dip deletes a paragraph
text:
  a
  b
  $
  c
cursor: 1,1
keys: dip

=== dap deletes a paragraph and the blank line
text:
  a
  b
  $
  c
cursor: 2,1
keys: dap

=== dap on the last paragraph takes the blank line before
text:
  a
  $
  b
cursor: 3,1
keys: dap

=== dip on blank lines deletes them
text:
  a
  $
     $
  b
cursor: 2,1
keys: dip

=== di paren deletes inside parentheses
text:
  f(a, b)
cursor: 1,4
keys: di(

=== da paren deletes the parentheses too
text:
  f(a, b) x
cursor: 1,4
keys: da(

=== dib from the opening paren
text:
  f(a, b)
cursor: 1,2
keys: dib

=== di paren with nesting
text:
  f(a, (b), c)
cursor: 1,7
keys: di)

=== d2i paren takes the outer block
text:
  f(a, (b), c)
cursor: 1,7
keys: d2i(

=== di paren on empty parentheses
text:
  f()
cursor: 1,2
keys: di(

=== di brace across lines keeps the braces' lines
text:
  fn x() {
      body();
  }
cursor: 2,5
keys: di{

=== da brace across lines
text:
  fn x() {
      body();
  }
cursor: 2,5
keys: da{

=== diB inside braces on one line
text:
  {a b}
cursor: 1,3
keys: diB

=== di bracket
text:
  x[1, 2]
cursor: 1,4
keys: di[

=== di angle
text:
  <a href>
cursor: 1,3
keys: di<lt>

=== di paren outside any parentheses finds the next
text:
  x = f(a)
cursor: 1,1
keys: di(

=== di quote deletes inside quotes
text:
  say "hello there" now
cursor: 1,8
keys: di"

=== da quote takes the trailing space
text:
  say "hello" now
cursor: 1,7
keys: da"

=== da quote at the end takes the leading space
text:
  say "hello"
cursor: 1,7
keys: da"

=== di quote with the cursor before the string
text:
  x = "abc"
cursor: 1,1
keys: di"

=== di quote with the cursor on the closing quote
text:
  a "b" "c"
cursor: 1,5
keys: di"

=== di quote with an escaped quote
text:
  "a\"b"
cursor: 1,2
keys: di"

=== di single quote
text:
  x = 'abc'
cursor: 1,7
keys: di'

=== di backtick
text:
  run `cmd` now
cursor: 1,7
keys: di`

=== ci quote on an empty string
text:
  x = ""
cursor: 1,5
keys: ci"y<Esc>

=== dit deletes inside a tag
text:
  <b>bold</b>
cursor: 1,5
keys: dit

=== dat deletes the tag block
text:
  x <b>bold</b> y
cursor: 1,7
keys: dat

=== dit with nested tags
text:
  <div><b>x</b></div>
cursor: 1,10
keys: 2dit

=== diw with a count across white space
text:
  one two three
cursor: 1,1
keys: d3iw

=== da close paren
text:
  f(a) x
cursor: 1,3
keys: da)

=== dab
text:
  f(a) x
cursor: 1,3
keys: dab

=== di close brace
text:
  {a}
cursor: 1,2
keys: di}

=== da brace on one line
text:
  x {a} y
cursor: 1,4
keys: da{

=== da close brace
text:
  x {a} y
cursor: 1,4
keys: da}

=== daB
text:
  x {a} y
cursor: 1,4
keys: daB

=== di close bracket
text:
  [1, 2]
cursor: 1,2
keys: di]

=== da bracket
text:
  x [1] y
cursor: 1,4
keys: da[

=== da close bracket
text:
  x [1] y
cursor: 1,4
keys: da]

=== di greater-than
text:
  <tag>
cursor: 1,2
keys: di>

=== da less-than
text:
  x <tag> y
cursor: 1,4
keys: da<lt>

=== da greater-than
text:
  x <tag> y
cursor: 1,4
keys: da>

=== da single quote
text:
  x 'abc' y
cursor: 1,4
keys: da'

=== da backtick
text:
  x `abc` y
cursor: 1,4
keys: da`
````

Create `plugins/editor/src/test/resources/vim/corpus/insert.case`:

````text
# Entering Insert mode and the keys that work in it.

=== i inserts before the cursor
text:
  world
cursor: 1,1
keys: ihello <Esc>

=== a appends after the cursor
text:
  ac
cursor: 1,1
keys: ab<Esc>

=== a on an empty line
text:
  $
cursor: 1,1
keys: ax<Esc>

=== I inserts before the first non-blank
text:
     text
cursor: 1,7
keys: I- <Esc>

=== gI inserts in column 1
text:
     text
cursor: 1,7
keys: gIx<Esc>

=== A appends at the end
text:
  line
cursor: 1,1
keys: A end<Esc>

=== o opens a line below
text:
  one
  three
cursor: 1,1
keys: otwo<Esc>

=== O opens a line above
text:
  two
cursor: 1,1
keys: Oone<Esc>

=== o with autoindent copies the indent
set: autoindent
text:
      one
cursor: 1,1
keys: otwo<Esc>

=== o with autoindent and nothing typed leaves no indent
set: autoindent
text:
      one
cursor: 1,1
keys: o<Esc>

=== o with autoindent and tabs
set: autoindent tabstop=4
text:
  	x
cursor: 1,1
keys: oy<Esc>

=== o with autoindent and expandtab
set: autoindent expandtab tabstop=4
text:
  	x
cursor: 1,1
keys: oy<Esc>

=== 3i repeats the text
text:
  x
cursor: 1,1
keys: 3iab<Esc>

=== 2A repeats at the end
text:
  x
cursor: 1,1
keys: 2A-<Esc>

=== 3o opens three lines
text:
  a
cursor: 1,1
keys: 3ob<Esc>

=== 2O opens two lines above
text:
  z
cursor: 1,1
keys: 2Oy<Esc>

=== Esc at column 1 stays
text:
  abc
cursor: 1,1
keys: i<Esc>

=== C-c leaves Insert mode
text:
  abc
cursor: 1,3
keys: ix<C-c>

=== Insert mode is reported
text:
  abc
cursor: 1,2
keys: ix

=== Enter splits the line
text:
  onetwo
cursor: 1,4
keys: i<CR><Esc>

=== Enter with autoindent keeps the indent and drops leading blanks
set: autoindent
text:
      one   two
cursor: 1,10
keys: i<CR><Esc>

=== Enter twice with autoindent empties the middle line
set: autoindent
text:
    one
cursor: 1,6
keys: A<CR><CR>x<Esc>

=== C-j makes a new line
text:
  ab
cursor: 1,2
keys: i<C-j><Esc>

=== BS deletes the character before
text:
  abc
cursor: 1,3
keys: i<BS><Esc>

=== BS joins lines
text:
  one
  two
cursor: 2,1
keys: i<BS><Esc>

=== BS at the start of the text fails
text:
  abc
cursor: 1,1
keys: i<BS>x<Esc>

=== BS before the insert start with backspace=start
text:
  abc
cursor: 1,3
keys: ix<BS><BS><BS><Esc>

=== BS without start stops at the insert
set: backspace=indent,eol
text:
  abc
cursor: 1,3
keys: ix<BS><BS><Esc>

=== C-h deletes like BS
text:
  abc
cursor: 1,3
keys: a<C-h><Esc>

=== C-w deletes the word before
text:
  one two
cursor: 1,7
keys: a<C-w><Esc>

=== C-w deletes white space then the word
text:
  one two   $
cursor: 1,10
keys: a<C-w><Esc>

=== C-w stops at the insert start once
text:
  one two
cursor: 1,7
keys: a three<C-w><C-w><Esc>

=== C-w deletes punctuation as its own word
text:
  a.b
cursor: 1,3
keys: a<C-w><Esc>

=== C-u deletes the typed text
text:
  one
cursor: 1,3
keys: a two<C-u><Esc>

=== C-u deletes the line before the cursor
text:
  one two
cursor: 1,7
keys: a<C-u><Esc>

=== C-u keeps the indent with autoindent
set: autoindent
text:
      one two
cursor: 1,11
keys: a<C-u><Esc>

=== Del deletes under the cursor
text:
  abc
cursor: 1,1
keys: i<Del><Esc>

=== Del at the end joins the next line
text:
  ab
  cd
cursor: 1,2
keys: a<Del><Esc>

=== Tab inserts a tab
text:
  x
cursor: 1,1
keys: i<Tab><Esc>

=== Tab with expandtab inserts spaces
set: expandtab tabstop=4
text:
  ab
cursor: 1,2
keys: i<Tab><Esc>

=== Tab with softtabstop inserts spaces then tabs
set: softtabstop=4 tabstop=8
text:
  x
cursor: 1,1
keys: i<Tab><Tab>y<Esc>

=== BS with softtabstop deletes a soft tab
set: softtabstop=4 expandtab
text:
  x
cursor: 1,1
keys: i<Tab><Tab><BS>y<Esc>

=== C-t indents the line
set: shiftwidth=4 expandtab
text:
  one
cursor: 1,2
keys: i<C-t><Esc>

=== C-d unindents the line
set: shiftwidth=4 expandtab
text:
        one
cursor: 1,10
keys: i<C-d><Esc>

=== C-t rounds to shiftwidth
set: shiftwidth=4 expandtab
text:
     one
cursor: 1,6
keys: i<C-t><Esc>

=== C-r inserts a register
register a: c "hello"
text:
  x
cursor: 1,1
keys: a<C-r>a<Esc>

=== C-r inserts a linewise register with its line break
register a: l "one\n"
text:
  x
cursor: 1,1
keys: i<C-r>a<Esc>

=== C-r C-r inserts literally
register a: c "a b"
text:
  x
cursor: 1,1
keys: a<C-r><C-r>a<Esc>

=== C-r of the unnamed register
text:
  word
cursor: 1,1
keys: yiwA <C-r>"<Esc>

=== C-v inserts a tab with expandtab
set: expandtab
text:
  x
cursor: 1,1
keys: i<C-v><Tab><Esc>

=== C-v with a decimal code
text:
  x
cursor: 1,1
keys: i<C-v>065<Esc>

=== C-v u with a hex code
text:
  x
cursor: 1,1
keys: i<C-v>u00e9<Esc>

=== C-v x with a short hex code
text:
  x
cursor: 1,1
keys: i<C-v>x41z<Esc>

=== C-a inserts the last inserted text
text:
  x
cursor: 1,1
keys: ahi<Esc>A <C-a><Esc>

=== C-e copies the character below
text:
  ab
  xyz
cursor: 1,2
keys: a<C-e><Esc>

=== C-y copies the character above
text:
  abc
  x
cursor: 2,1
keys: a<C-y><Esc>

=== C-y with nothing above fails
text:
  abc
  x
cursor: 1,3
keys: a<C-y><Esc>

=== C-o runs one command
text:
  one two
cursor: 1,1
keys: i<C-o>wX<Esc>

=== C-o at the end of the line stays there
text:
  one
cursor: 1,3
keys: A<C-o>~X<Esc>

=== C-o reports insert-normal
text:
  one
cursor: 1,1
keys: i<C-o>

=== C-o then Esc returns to Insert mode
text:
  one
cursor: 1,1
keys: i<C-o><Esc>x<Esc>

=== C-o dd
text:
  one
  two
cursor: 1,1
keys: ix<C-o>ddy<Esc>

=== Insert key switches to Replace mode
text:
  abc
cursor: 1,1
keys: i<Insert>XY<Esc>

=== Insert key in Normal mode inserts
text:
  abc
cursor: 1,2
keys: <Insert>x<Esc>

=== Left arrow moves in Insert mode
text:
  abc
cursor: 1,3
keys: a<Left>X<Esc>

=== Right arrow can reach the end
text:
  abc
cursor: 1,2
keys: i<Right><Right>X<Esc>

=== Up and Down keep the column
text:
  abcdef
  xy
  123456
cursor: 1,5
keys: i<Down><Down>X<Esc>

=== Home and End
text:
  abc
cursor: 1,2
keys: i<End>X<Home>Y<Esc>

=== count is dropped after an arrow
text:
  ab
cursor: 1,1
keys: 3ix<Right>y<Esc>

=== typing a closing brace is literal
text:
  x
cursor: 1,1
keys: a{}()[]<Esc>

=== C-q inserts literally like C-v
set: expandtab
text:
  x
cursor: 1,1
keys: i<C-q><Tab><Esc>

=== Up in Insert mode keeps the column
text:
  abcdef
  xy
cursor: 2,2
keys: a<Up>Z<Esc>
````

Create `plugins/editor/src/test/resources/vim/corpus/replace.case`:

````text
# r and Replace mode.

=== r replaces one character
text:
  abc
cursor: 1,2
keys: rx

=== 3r replaces three characters
text:
  abcdef
cursor: 1,2
keys: 3rx

=== r with too few characters fails
text:
  abc
cursor: 1,2
keys: 5rx

=== r Esc cancels
text:
  abc
cursor: 1,2
keys: r<Esc>

=== r Enter splits the line
text:
  one two
cursor: 1,4
keys: r<CR>

=== 2r Enter replaces two characters with one break
text:
  one  two
cursor: 1,4
keys: 2r<CR>

=== r Tab inserts a tab
text:
  a b
cursor: 1,2
keys: r<Tab>

=== r on an empty line fails
text:
  $
cursor: 1,1
keys: rx

=== R overwrites
text:
  abcdef
cursor: 1,2
keys: RXY<Esc>

=== R past the end appends
text:
  ab
cursor: 1,2
keys: RXYZ<Esc>

=== R reports Replace mode
text:
  abc
cursor: 1,1
keys: RX

=== BS in Replace mode restores
text:
  abcdef
cursor: 1,2
keys: RXYZ<BS><BS><Esc>

=== BS in Replace mode past the start only moves
text:
  abcdef
cursor: 1,3
keys: RX<BS><BS><Esc>

=== BS in Replace mode removes appended characters
text:
  ab
cursor: 1,2
keys: RXYZ<BS><Esc>

=== 2R repeats the text as an insert
text:
  abcdef
cursor: 1,1
keys: 2RXY<Esc>

=== Enter in Replace mode breaks the line without replacing
text:
  abcd
cursor: 1,2
keys: R<CR>X<Esc>
````

Create `plugins/editor/src/test/resources/vim/corpus/repeat-undo.case`:

````text
# . and undo.

=== dot repeats dw
text:
  one two three four
cursor: 1,1
keys: dw.

=== dot with a count replaces the count
text:
  a b c d e f
cursor: 1,1
keys: 2dw3.

=== dot keeps the new count
text:
  a b c d e f g
cursor: 1,1
keys: dw2..

=== dot repeats an insert
text:
  x
  y
cursor: 1,1
keys: Aa<Esc>j.

=== dot repeats cw
text:
  one two
cursor: 1,1
keys: cwX<Esc>w.

=== dot repeats o
text:
  a
cursor: 1,1
keys: ob<Esc>.

=== dot repeats 3i
text:
  x
cursor: 1,1
keys: 3ia<Esc>.

=== dot repeats x
text:
  abcdef
cursor: 1,1
keys: 2x.

=== dot repeats r
text:
  abcdef
cursor: 1,1
keys: rxll.

=== dot repeats J
text:
  a
  b
  c
cursor: 1,1
keys: J.

=== dot repeats p
text:
  ab
cursor: 1,1
keys: ylp.

=== dot repeats a put from register 1 as register 2
text:
  1
  2
  3
cursor: 1,1
keys: dddd"1p.

=== dot repeats text after an arrow as an insert
text:
  ab
  cd
cursor: 1,1
keys: ix<Right>y<Esc>j0.

=== dot repeats a C-r insert with the text
register a: c "Q"
text:
  ab
  cd
cursor: 1,1
keys: i<C-r>a<Esc>j0.

=== dot repeats greater-than
set: shiftwidth=2 expandtab
text:
  a
  b
cursor: 1,1
keys: >>j.

=== dot repeats gUiw
text:
  one two
cursor: 1,1
keys: gUiww.

=== dot repeats a change with a named register
text:
  one two
cursor: 1,1
keys: "adw.
registers: a

=== dot without a change fails
text:
  abc
cursor: 1,1
keys: .x

=== dot does not repeat a yank
text:
  one two
cursor: 1,1
keys: xyw.

=== u undoes a delete
text:
  one two three
cursor: 1,5
keys: dwu

=== u undoes dd and restores the cursor
text:
  one
    two
  three
cursor: 2,4
keys: ddu

=== u undoes dd on the last line
text:
  one
  two
cursor: 2,2
keys: ddu

=== u undoes an insert as one step
text:
  x
cursor: 1,1
keys: ahello world<Esc>u

=== u undoes 3o as one step
text:
  a
cursor: 1,1
keys: 3ob<Esc>u

=== u undoes o and puts the cursor back
text:
  one
  two
cursor: 1,2
keys: onew<Esc>u

=== u undoes a put
text:
  one
cursor: 1,1
keys: yypu

=== 2u undoes two changes
text:
  abcdef
cursor: 1,1
keys: xxxx2u

=== u with nothing to undo fails
text:
  abc
cursor: 1,2
keys: ux

=== C-r redoes
text:
  one two
cursor: 1,1
keys: dwu<C-r>

=== C-r redoes dd
text:
  one
  two
  three
cursor: 2,1
keys: ddu<C-r>

=== u undoes a dot as one step
text:
  a b c d
cursor: 1,1
keys: 2dw.u

=== u after an arrow in Insert mode undoes only the second part
text:
  x
cursor: 1,1
keys: aab<Left>cd<Esc>u

=== u undoes J
text:
  one
  two
cursor: 1,1
keys: Ju

=== u undoes cw
text:
  one two
cursor: 1,5
keys: cwX<Esc>u

=== u undoes r
text:
  abc
cursor: 1,2
keys: rxu

=== u after moving puts the cursor at the change
text:
  one two three
cursor: 1,5
keys: xwwu
````

Create `plugins/editor/src/test/resources/vim/corpus/edges.case`:

````text
# Edge cases: text ends, counts past the end, and characters beyond ASCII.

=== x on an accented letter
text:
  café au lait
cursor: 1,4
keys: x

=== x on a combining sequence
text:
  café ok
cursor: 1,4
keys: x

=== l over a wide character
text:
  a日b
cursor: 1,1
keys: ll

=== dw over an emoji
text:
  a 😀 b
cursor: 1,3
keys: dw

=== e at the end of the text fails
text:
  one
cursor: 1,3
keys: ex

=== 9w runs to the end of the text
text:
  one two
  three
cursor: 1,1
keys: 9w

=== 9dw deletes to the end of the text
text:
  one two
  three
cursor: 1,5
keys: 9dw

=== cc on the last line
text:
  one
  two
cursor: 2,1
keys: ccX<Esc>

=== dd then p at the end
text:
  one
  two
cursor: 1,1
keys: ddp

=== dG from the first line empties the text
text:
  one
  two
cursor: 1,1
keys: dGix<Esc>

=== o on an empty text
text:
  $
cursor: 1,1
keys: ox<Esc>

=== J with a tab before
text:
  one	
  two
cursor: 1,1
keys: J

=== J with leading tabs
text:
  one
  		two
cursor: 1,1
keys: J

=== yy then P on the first line
text:
  one
  two
cursor: 2,1
keys: yyggP

=== 3p linewise
text:
  a
cursor: 1,1
keys: yy3p

=== r on the last character
text:
  abc
cursor: 1,3
keys: rX

=== tilde over a combining sequence
text:
  éx
cursor: 1,1
keys: ~

=== A on a line with a wide character
text:
  日本
cursor: 1,1
keys: Ax<Esc>

=== cw on the last word of the text
text:
  one two
cursor: 1,5
keys: cwX<Esc>

=== c percent
text:
  (a)(b)
cursor: 1,1
keys: c%X<Esc>

=== d2j from the second-to-last line fails
text:
  a
  b
  c
cursor: 2,1
keys: d2j

=== counts multiply across the operator
text:
  a b c d e f g h
cursor: 1,1
keys: 2d2w

=== dot with a count on an insert
text:
  x
cursor: 1,1
keys: ia<Esc>3.

=== undo then dot
text:
  one two three
cursor: 1,1
keys: dwu.

=== yank of a paragraph keeps the cursor
text:
  a
  b
  
  c
cursor: 2,1
keys: yip
registers: "

=== C-w across the line start joins
text:
  one
  two
cursor: 2,1
keys: i<C-w><Esc>

=== BS with softtabstop removes a tab
set: softtabstop=4 tabstop=8
text:
  x
cursor: 1,1
keys: i<Tab><Tab><BS><BS>y<Esc>

=== Esc after o with autoindent on a tab-indented line
set: autoindent
text:
  	a
cursor: 1,1
keys: o<Esc>
````

The `.case` files hold tabs, wide characters and combining marks in their text blocks (for example `edges.case`'s
`café` is `e` followed by U+0301). Save them as UTF-8 exactly as shown, and do not let an editor strip trailing
white space (the `$` markers make it visible, but tabs inside lines matter too).

- [ ] **Step 4: Record the expectations from Vim**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus
```

Expected: `Recorded 462 cases with vim 9.1.` Every non-divergent case in the 13 files now ends with a `--- vim 9.1` block (`expect-text`, `expect-cursor`, `expect-mode`, and `expect-register` lines for the registers it names). `git diff --stat` shows only the `.case` files changed by the recorder.

If a case reports "Vim did not finish within 20 s", its keys left Vim waiting for input (an unfinished command);
fix the case. A different Vim 9 patch level may record a different result for a few cases; then the engine follows
the recorded result, and the plan's claim that all cases pass is re-checked in Step 5.

- [ ] **Step 5: Replay the corpus**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest'
```

Expected: 466 tests pass: one dynamic test per case (464), `everyCommandHasACase`, and `theCorpusFormatReadsBackWhatItWrites`.

A failing case prints the difference, for example `text [lin ende] (Vim: [line end]); cursor 1,7 (Vim: 1,8)`. Fix
the engine, not the case. To iterate on one case, record and replay it by name:
`./gradlew :jasper-plugin-editor:recordVimCorpus -Pcase="dw deletes"` and
`./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest'`.

- [ ] **Step 6: Commit**

```bash
git add plugins/editor/build.gradle.kts plugins/editor/src/test/java/dev/jasper/editor/vim plugins/editor/src/test/resources/vim
git commit -m "Add the Vim corpus, recorded from Vim 9, and its replay test

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 6: The Vim bar

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/ui/VimBar.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/ui/package-info.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/ui/VimBarTest.java`

**Interfaces:**
- Consumes: `VimEditor` (`addListener`, `removeListener`, `mode`, `showmode`, `showcmd`, `message`, `messageIsError`, `bells`), `Mode.label()`.
- Produces: `public final class VimBar extends JPanel` with `attach(VimEditor)`, `detach()`, `apply(Font)`; package-private `statusText()`, `commandText()`, `flashing()`.

- [ ] **Step 1: Write the failing test**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/ui/VimBarTest.java`:

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
}
```

- [ ] **Step 2: Run it to see it fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ui.VimBarTest'
```

Expected: compilation fails: `VimBar` does not exist.

- [ ] **Step 3: Write the bar**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ui/package-info.java`:

```java
/** Vim mode's Swing parts: the Vim bar under each tab. UI thread only. May depend on: {@code vim}, the JDK. */
package dev.jasper.editor.vim.ui;
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ui/VimBar.java`:

```java
package dev.jasper.editor.vim.ui;

import dev.jasper.editor.vim.VimEditor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * The strip under a tab while Vim mode is on (spec 5.2): the mode or the last message on the left, the partly typed
 * command on the right. The bell flashes it briefly; Jasper never beeps. Plan V2 adds the command line.
 */
public final class VimBar extends JPanel {
    private static final Color ERROR = new Color(0xD9, 0x4F, 0x4F);

    private final JLabel status = label(), command = label();
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

    String commandText() { return command.getText().strip(); }

    boolean flashing() { return flashing; }

    private void refresh() {
        if (editor == null) {
            status.setText(" ");
            command.setText(" ");
            return;
        }
        String message = editor.message();
        String text = message.isEmpty() ? editor.showmode() ? editor.mode().label() : "" : message;
        // A space keeps the bar one line high when there is nothing to show.
        status.setText(text.isEmpty() ? " " : text);
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

    private static JLabel label() {
        var label = new JLabel(" ");
        label.putClientProperty("html.disable", true);
        return label;
    }
}
```

- [ ] **Step 4: Run the test**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ui.VimBarTest'
```

Expected: 1 test passes.

- [ ] **Step 5: Commit**

```bash
git add plugins/editor/src/main/java/dev/jasper/editor/vim/ui plugins/editor/src/test/java/dev/jasper/editor/vim/ui
git commit -m "Add the Vim bar: mode, pending keys, messages and the visual bell

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 7: Vim mode in the Editor

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/ui/SettingsFile.java`, `plugins/editor/src/main/java/dev/jasper/editor/ui/SystemClipboard.java`, `plugins/editor/src/test/java/dev/jasper/editor/ui/SettingsFileTest.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java`, `plugins/editor/src/main/java/dev/jasper/editor/ui/StatusLine.java`, `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, `plugins/editor/src/main/java/dev/jasper/editor/ui/package-info.java`, `plugins/editor/src/main/resources/settings.toml`
- Modify tests: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/ui/StatusLineTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorTabTest.java`, `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`

**Interfaces:**
- Consumes: `Vim`, `VimHost`, `VimEditor`, `VimTextArea`, `EditorDefaults`, `KeyInput` (Task 4), `VimBar` (Task 6).
- Produces: `EditorSettings` gains a sixth component `boolean vim` (settings key `vim`, default false). `EditorTab.area()` returns `VimTextArea`; `EditorTab.vim(Optional<Vim>)`, `vimEditor()`. `EditorWindow.vimOn()`, `toggleVim()`, and the command `toggle-vim` ("Toggle Vim Mode"). `StatusLine(Runnable, Runnable, Runnable)` and `display(Optional<EditorTab>, int, boolean)` with a "Vim: On"/"Vim: Off" item. `SettingsFile.setBoolean(Path, String, boolean)`. `SystemClipboard.read(char)`, `write(char, String)`.

Spec 1.2: Vim mode follows `vim` in the settings, live. Toggle Vim Mode and the status item switch every tab at once and write `vim = true|false` into the plugin's settings file (`PluginConfig.file()`), so the choice lasts; until the settings are read again, the window remembers the toggled value, and it forgets it once the settings agree.

- [ ] **Step 1: Write the failing tests**

Create `plugins/editor/src/test/java/dev/jasper/editor/ui/SettingsFileTest.java`:

```java
package dev.jasper.editor.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class SettingsFileTest {
    @TempDir Path folder;

    @Test void addsTheKeyAfterItsCommentedExample() throws Exception {
        Path file = Files.writeString(folder.resolve("editor.toml"), "# Vim mode.\n# vim = false\n\n# tab_size = 4\n");
        SettingsFile.setBoolean(file, "vim", true);
        assertThat(Files.readString(file)).isEqualTo("# Vim mode.\n# vim = false\nvim = true\n\n# tab_size = 4\n");
        SettingsFile.setBoolean(file, "vim", false);
        assertThat(Files.readString(file)).isEqualTo("# Vim mode.\n# vim = false\nvim = false\n\n# tab_size = 4\n");
    }

    @Test void createsTheFileAndKeepsTheKeyAboveTables() throws Exception {
        Path missing = folder.resolve("new/editor.toml");
        SettingsFile.setBoolean(missing, "vim", true);
        assertThat(Files.readString(missing)).isEqualTo("vim = true\n");
        Path tables = Files.writeString(folder.resolve("t.toml"), "font_size = 14\n[extra]\nx = 1\n");
        SettingsFile.setBoolean(tables, "vim", true);
        assertThat(Files.readString(tables)).isEqualTo("font_size = 14\nvim = true\n[extra]\nx = 1\n");
        assertThat(Files.list(folder).filter(p -> p.getFileName().toString().endsWith(".tmp"))).isEmpty();
    }
}
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsTest.java`, replace:

```java
                .isEqualTo(new EditorSettings("JetBrains Mono", 13, 4, false, 10));
```

with:

```java
                .isEqualTo(new EditorSettings("JetBrains Mono", 13, 4, false, 10, false));
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsTest.java`, replace:

```java
            host.setConfig("dev.jasper.editor", Map.of("font_family", "Nope Mono", "font_size", 99L, "tab_size", "four",
                "soft_wrap", true, "max_file_mib", 20L));
```

with:

```java
            host.setConfig("dev.jasper.editor", Map.of("font_family", "Nope Mono", "font_size", 99L, "tab_size", "four",
                "soft_wrap", true, "max_file_mib", 20L, "vim", "yes"));
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsTest.java`, replace:

```java
            assertThat(settings).isEqualTo(new EditorSettings("Menlo", 13, 4, true, 20));
```

with:

```java
            assertThat(settings).isEqualTo(new EditorSettings("Menlo", 13, 4, true, 20, false));
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsTest.java`, replace:

```java
                "dev.jasper.editor: tab_size: Use a whole number from 1 to 16; using 4.");
```

with:

```java
                "dev.jasper.editor: tab_size: Use a whole number from 1 to 16; using 4.",
                "dev.jasper.editor: vim: Use true or false; using false.");
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/StatusLineTest.java`, replace:

```java
        int[] clicked = {0, 0};
        var status = new StatusLine(() -> clicked[0]++, () -> clicked[1]++);
        status.display(Optional.of(tab), 4);
```

with:

```java
        int[] clicked = {0, 0, 0};
        var status = new StatusLine(() -> clicked[0]++, () -> clicked[1]++, () -> clicked[2]++);
        status.display(Optional.of(tab), 4, true);
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/StatusLineTest.java`, replace:

```java
        assertThat(status.noticeText()).isEmpty();
        status.display(Optional.empty(), 4);
```

with:

```java
        assertThat(status.noticeText()).isEmpty();
        assertThat(status.vimSelected()).isTrue();
        var vim = status.items().stream().filter(item -> item.text().equals("Vim: On")).findFirst().orElseThrow();
        vim.run().run();
        assertThat(clicked[2]).isEqualTo(1);
        status.display(Optional.empty(), 4, false);
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorTabTest.java`, replace:

```java
        tab.apply(new EditorSettings("Monospaced", 17, 2, false, 10), new EditorTheme(), Variant.DARK);
```

with:

```java
        tab.apply(new EditorSettings("Monospaced", 17, 2, false, 10, false), new EditorTheme(), Variant.DARK);
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorTabTest.java`, replace:

```java
    @Test void aFailureShowsRetryAndCloseAndABinaryFileHasNoRetry() throws Exception {
```

with:

```java
    @Test void vimModeShowsTheVimBarAndTurnsOffCleanly() throws Exception {
        var tab = open(Files.writeString(folder.resolve("v.txt"), "one\n"));
        drain();
        assertThat(tab.vimBarVisible()).isFalse();
        tab.vim(Optional.of(new dev.jasper.editor.vim.Vim(dev.jasper.editor.vim.VimHost.detached())));
        assertThat(tab.vimEditor()).isPresent();
        assertThat(tab.vimBarVisible()).isTrue();
        assertThat(tab.area().vim()).isPresent();
        tab.vim(Optional.empty());
        assertThat(tab.vimEditor()).isEmpty();
        assertThat(tab.vimBarVisible()).isFalse();
        assertThat(tab.area().vim()).isEmpty();
        assertThat(tab.area().getText()).isEqualTo("one\n");
    }

    @Test void aFailureShowsRetryAndCloseAndABinaryFileHasNoRetry() throws Exception {
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`, replace:

```java
    private FakePluginHost host;
```

with:

```java
    private FakePluginHost host;
    private PluginContext context;
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`, replace:

```java
        PluginContext context = host.start(
```

with:

```java
        context = host.start(
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`, replace:

```java
    private final AtomicReference<EditorSettings> settings = new AtomicReference<>(new EditorSettings("Monospaced", 13, 4, false, 1));
```

with:

```java
    private final AtomicReference<EditorSettings> settings = new AtomicReference<>(new EditorSettings("Monospaced", 13, 4, false, 1, false));
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`, replace:

```java
        settings.set(new EditorSettings("Monospaced", 18, 2, false, 1));
```

with:

```java
        settings.set(new EditorSettings("Monospaced", 18, 2, false, 1, false));
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`, replace:

```java
                "Toggle Comment", "Revert to Saved", "Language…", "Toggle Soft Wrap");
```

with:

```java
                "Toggle Comment", "Revert to Saved", "Language…", "Toggle Soft Wrap", "Toggle Vim Mode");
```

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`, replace:

```java
    @Test void saveAsDoesNotAliasTheOriginalFileToTheRenamedTab() throws Exception {
```

with:

```java
    @Test void toggleVimModeSwitchesEveryTabAndWritesTheSetting() throws Exception {
        EditorTab first = opened(file("one.txt", "one\n"));
        EditorTab second = opened(file("two.txt", "two\n"));
        assertThat(first.vimEditor()).isEmpty();
        window.toggleVim();
        assertThat(first.vimEditor()).isPresent();
        assertThat(second.vimEditor()).isPresent();
        assertThat(Files.readString(context.config().file())).contains("vim = true");
        var item = host.contentStatus(host.contentTabs().getLast()).stream().filter(status -> status.text().startsWith("Vim:")).findFirst().orElseThrow();
        assertThat(item.text()).isEqualTo("Vim: On");
        assertThat(opened(file("three.txt", "three\n")).vimEditor()).as("a new tab follows").isPresent();
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
        window.apply();
        assertThat(window.vimOn()).isTrue();
        item.run().run();
        assertThat(first.vimEditor()).isEmpty();
        assertThat(Files.readString(context.config().file())).contains("vim = false");
    }

    @Test void vimKeysEditTheDocumentAndUndoItsChange() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
        EditorTab tab = opened(file("vim.txt", "one two\n"));
        var vim = tab.vimEditor().orElseThrow();
        for (var key : dev.jasper.editor.vim.KeyInput.parse("dw")) vim.feed(key);
        assertThat(tab.area().getText()).isEqualTo("two\n");
        assertThat(tab.session().dirty()).isTrue();
        for (var key : dev.jasper.editor.vim.KeyInput.parse("u")) vim.feed(key);
        assertThat(tab.area().getText()).isEqualTo("one two\n");
    }

    @Test void saveAsDoesNotAliasTheOriginalFileToTheRenamedTab() throws Exception {
```

- [ ] **Step 2: Run the Editor UI tests to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.*'
```

Expected: compilation fails: the six-argument `EditorSettings`, the three-argument `StatusLine`, `EditorTab.vim`, `EditorWindow.toggleVim` and `SettingsFile` do not exist.

- [ ] **Step 3: Add the settings key, the settings writer and the clipboard bridge**

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java`, replace:

```java
 * @param maxFileMib the largest file opened, 1 to 256 MiB
 */
public record EditorSettings(String fontFamily, int fontSize, int tabSize, boolean softWrap, int maxFileMib) {
```

with:

```java
 * @param maxFileMib the largest file opened, 1 to 256 MiB
 * @param vim whether Vim mode is on (Vim spec 9)
 */
public record EditorSettings(String fontFamily, int fontSize, int tabSize, boolean softWrap, int maxFileMib, boolean vim) {
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java`, replace:

```java
        int max = number(config, "max_file_mib", 10, 1, 256);
        return new EditorSettings(font, size, tab, wrap, max);
```

with:

```java
        int max = number(config, "max_file_mib", 10, 1, 256);
        boolean vim = flag(config, "vim", false);
        return new EditorSettings(font, size, tab, wrap, max, vim);
```

In `plugins/editor/src/main/resources/settings.toml`, replace:

```toml
# Files larger than this many MiB (1 to 256) are refused.
# max_file_mib = 10
```

with:

```toml
# Files larger than this many MiB (1 to 256) are refused.
# max_file_mib = 10

# Vim mode: modal editing as in IdeaVim. The Vim item in the status bar and Toggle Vim Mode set this.
# vim = false
```

Create `plugins/editor/src/main/java/dev/jasper/editor/ui/SettingsFile.java`:

```java
package dev.jasper.editor.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Writes one top-level key of the Editor's settings file, keeping every other line, comments included. */
final class SettingsFile {
    private SettingsFile() { }

    /**
     * Sets {@code key = value}: replaces the key's line, or adds one after its commented-out example, or before the
     * first table, or at the end.
     *
     * @param file the settings file; created when missing
     * @param key a top-level key
     * @param value its new value
     * @throws IOException when the file cannot be read or written
     */
    static void setBoolean(Path file, String key, boolean value) throws IOException {
        List<String> lines = Files.exists(file) ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8)) : new ArrayList<>();
        String setting = key + " = " + value;
        Pattern live = Pattern.compile("^\\s*" + Pattern.quote(key) + "\\s*=.*$");
        Pattern example = Pattern.compile("^\\s*#\\s*" + Pattern.quote(key) + "\\s*=.*$");
        int at = find(lines, live);
        if (at >= 0) lines.set(at, setting);
        else {
            int comment = find(lines, example);
            int table = find(lines, Pattern.compile("^\\s*\\[.*$"));
            if (comment >= 0 && (table < 0 || comment < table)) lines.add(comment + 1, setting);
            else if (table >= 0) lines.add(table, setting);
            else lines.add(setting);
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".settings", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException notAtomic) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static int find(List<String> lines, Pattern pattern) {
        for (int i = 0; i < lines.size(); i++) if (pattern.matcher(lines.get(i)).matches()) return i;
        return -1;
    }
}
```

Create `plugins/editor/src/main/java/dev/jasper/editor/ui/SystemClipboard.java`:

```java
package dev.jasper.editor.ui;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Optional;

/** Vim's {@code "+} (the clipboard) and {@code "*} (the selection where there is one, else the clipboard). */
final class SystemClipboard {
    private SystemClipboard() { }

    /**
     * @param register {@code '+'} or {@code '*'}
     * @return its text, or empty when it holds none or cannot be read (another application has it, or headless)
     */
    static Optional<String> read(char register) {
        try {
            Clipboard clipboard = clipboard(register);
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return Optional.empty();
            return Optional.of((String) clipboard.getData(DataFlavor.stringFlavor));
        } catch (HeadlessException | IllegalStateException | UnsupportedFlavorException | IOException unavailable) {
            return Optional.empty();
        }
    }

    /**
     * @param register {@code '+'} or {@code '*'}
     * @param text written to it; dropped when it cannot be written
     */
    static void write(char register, String text) {
        try { clipboard(register).setContents(new StringSelection(text), null); }
        catch (HeadlessException | IllegalStateException unavailable) { }
    }

    private static Clipboard clipboard(char register) {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Clipboard selection = register == '*' ? toolkit.getSystemSelection() : null;
        return selection != null ? selection : toolkit.getSystemClipboard();
    }
}
```

- [ ] **Step 4: Give the status line a Vim item**

In `plugins/editor/src/main/java/dev/jasper/editor/ui/StatusLine.java`, replace:

```java
    private final Runnable languageAction, wrapAction;
    private String notice="",position="",language="",encoding="",separator="",indent="";
    private boolean ready,wrap;
    /** @param chooseLanguage opens the language chooser
     * @param toggleWrap toggles soft wrapping */
    public StatusLine(Runnable chooseLanguage,Runnable toggleWrap){languageAction=chooseLanguage;wrapAction=toggleWrap;}
    /** @param tab selected document, or empty
     * @param tabSize configured indentation width */
    public void display(Optional<EditorTab> tab,int tabSize){
```

with:

```java
    private final Runnable languageAction, wrapAction, vimAction;
    private String notice="",position="",language="",encoding="",separator="",indent="";
    private boolean ready,wrap,vim;
    /** @param chooseLanguage opens the language chooser
     * @param toggleWrap toggles soft wrapping
     * @param toggleVim toggles Vim mode */
    public StatusLine(Runnable chooseLanguage,Runnable toggleWrap,Runnable toggleVim){languageAction=chooseLanguage;wrapAction=toggleWrap;vimAction=toggleVim;}
    /** @param tab selected document, or empty
     * @param tabSize configured indentation width
     * @param vimOn whether Vim mode is on */
    public void display(Optional<EditorTab> tab,int tabSize,boolean vimOn){
        vim=vimOn;
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/StatusLine.java`, replace:

```java
            items.add(item(separator,null));items.add(item(indent,null));items.add(item(wrap?"Wrap: On":"Wrap: Off",wrapAction));}
```

with:

```java
            items.add(item(separator,null));items.add(item(indent,null));items.add(item(wrap?"Wrap: On":"Wrap: Off",wrapAction));
            items.add(item(vim?"Vim: On":"Vim: Off",vimAction));}
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/StatusLine.java`, replace:

```java
    boolean wrapSelected(){return wrap;}
```

with:

```java
    boolean wrapSelected(){return wrap;} boolean vimSelected(){return vim;}
```

- [ ] **Step 5: Make every tab a Vim-capable tab**

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, replace:

```java
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.sdk.Variant;
```

with:

```java
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.vim.EditorDefaults;
import dev.jasper.editor.vim.Vim;
import dev.jasper.editor.vim.VimEditor;
import dev.jasper.editor.vim.VimTextArea;
import dev.jasper.editor.vim.ui.VimBar;
import dev.jasper.sdk.Variant;
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, replace:

```java
import java.util.List;
import java.util.function.Consumer;
```

with:

```java
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, replace:

```java
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
```

with:

```java
import org.fife.ui.rtextarea.RTextScrollPane;
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, replace:

```java
/** One tab's view (spec 2, 2.3): the text area bound to its session, the loading and failure states, and the banner. */
```

with:

```java
/**
 * One tab's view (spec 2, 2.3): the text area bound to its session, the loading and failure states, the banner, and
 * with Vim mode on the Vim bar (Vim spec 5.2).
 */
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, replace:

```java
    private final RSyntaxTextArea area = new RSyntaxTextArea();
```

with:

```java
    private final VimTextArea area = new VimTextArea();
    private final VimBar vimBar = new VimBar();
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, replace:

```java
    private boolean replacing;
    private String showing = STATE;
```

with:

```java
    private boolean replacing;
    private String showing = STATE;
    private VimEditor vim;
    private int tabSize = 4;
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, replace:

```java
        body.add(scroll, TEXT);
```

with:

```java
        var text = new JPanel(new BorderLayout());
        text.add(scroll, BorderLayout.CENTER);
        text.add(vimBar, BorderLayout.SOUTH);
        vimBar.setVisible(false);
        body.add(text, TEXT);
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, replace:

```java
    /** Stops a pending typing notification on final disposal (not on a move). */
    public void dispose() { edits.stop(); }

    public DocumentSession session() { return session; }
    public RSyntaxTextArea area() { return area; }
```

with:

```java
    /** Stops a pending typing notification and Vim mode on final disposal (not on a move). */
    public void dispose() {
        edits.stop();
        vim(Optional.empty());
    }

    public DocumentSession session() { return session; }
    public VimTextArea area() { return area; }

    /**
     * Turns Vim mode on or off for this tab; the text and its undo history stay.
     *
     * @param global Vim's global state to turn it on with, or empty to turn it off
     */
    public void vim(Optional<Vim> global) {
        if (global.isPresent() == (vim != null)) return;
        if (global.isPresent()) {
            vim = new VimEditor(global.get(), area, defaults());
            vimBar.attach(vim);
        } else {
            vimBar.detach();
            vim.close();
            vim = null;
        }
        vimBar.setVisible(vim != null);
        revalidate();
    }

    /** @return this tab's Vim editor while Vim mode is on */
    public Optional<VimEditor> vimEditor() { return Optional.ofNullable(vim); }
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, replace:

```java
        area.setTabSize(settings.tabSize());
        Color rule = UIManager.getColor("Separator.foreground");
```

with:

```java
        area.setTabSize(settings.tabSize());
        tabSize = settings.tabSize();
        vimBar.apply(font);
        if (vim != null) vim.editorDefaults(defaults());
        Color rule = UIManager.getColor("Separator.foreground");
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, replace:

```java
        area.discardAllEdits();
        try { area.setCaretPosition(area.getLineStartOffset(Math.min(line, area.getLineCount() - 1))); }
        catch (BadLocationException impossible) { area.setCaretPosition(0); }
    }
```

with:

```java
        area.discardAllEdits();
        try { area.setCaretPosition(area.getLineStartOffset(Math.min(line, area.getLineCount() - 1))); }
        catch (BadLocationException impossible) { area.setCaretPosition(0); }
        if (vim != null) vim.textReplaced();
    }

    /** Vim's tab options start from the Editor's tab size and the tab's Tabs or Spaces (Vim spec 3.8). */
    private EditorDefaults defaults() { return new EditorDefaults(tabSize, !session.tabs(), true); }
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, replace:

```java
        area.setTabsEmulated(!session.tabs());
        switch (session.banner()) {
```

with:

```java
        area.setTabsEmulated(!session.tabs());
        if (vim != null) vim.editorDefaults(defaults());
        switch (session.banner()) {
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, replace:

```java
    boolean showingText() { return showing.equals(TEXT); }
```

with:

```java
    boolean showingText() { return showing.equals(TEXT); }
    boolean vimBarVisible() { return vimBar.isVisible(); }
```

- [ ] **Step 6: Wire the window: the shared Vim state, the toggle and new tabs**

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
import dev.jasper.editor.session.SessionEnvironment;
```

with:

```java
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.editor.vim.Vim;
import dev.jasper.editor.vim.VimHost;
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
import java.awt.event.KeyEvent;
import java.nio.file.Path;
```

with:

```java
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
    private boolean showingEmpty = true;
    private boolean guarding;
```

with:

```java
    private boolean showingEmpty = true;
    private boolean guarding;
    private final Vim vim = new Vim(new VimHost(SystemClipboard::read, SystemClipboard::write));
    /** Vim mode as toggled in this session, until the settings file says the same; null to follow the settings. */
    private Boolean vimToggled;
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
        status = new StatusLine(this::chooseLanguage, this::toggleWrap);
```

with:

```java
        status = new StatusLine(this::chooseLanguage, this::toggleWrap, this::toggleVim);
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
            new Command("wrap", "Toggle Soft Wrap", Optional.empty(), false, this::toggleWrap));
```

with:

```java
            new Command("wrap", "Toggle Soft Wrap", Optional.empty(), false, this::toggleWrap),
            new Command("toggle-vim", "Toggle Vim Mode", Optional.empty(), false, this::toggleVim));
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
    /** Settings or the theme changed: applies them to every tab. */
    public void apply() {
        EditorSettings settings = environment.settings().get();
        for (EditorTab tab : editorTabs()) tab.apply(settings, theme, context.appearance().variant(), context.appearance().editorPalette());
        refreshStatus();
    }
```

with:

```java
    /** Settings or the theme changed: applies them to every tab. */
    public void apply() {
        EditorSettings settings = environment.settings().get();
        if (vimToggled != null && vimToggled == settings.vim()) vimToggled = null;
        for (EditorTab tab : editorTabs()) {
            tab.apply(settings, theme, context.appearance().variant(), context.appearance().editorPalette());
            tab.vim(vimOn() ? Optional.of(vim) : Optional.empty());
        }
        refreshStatus();
    }

    /** @return whether Vim mode is on: as toggled here, or as the settings say */
    public boolean vimOn() { return vimToggled != null ? vimToggled : environment.settings().get().vim(); }

    /**
     * Toggle Vim Mode and the Vim status item: switches every tab at once and writes {@code vim} to the settings
     * file so the choice lasts (Vim spec 1.2).
     */
    public void toggleVim() {
        boolean on = !vimOn();
        vimToggled = on;
        for (EditorTab tab : editorTabs()) tab.vim(on ? Optional.of(vim) : Optional.empty());
        refreshStatus();
        try {
            SettingsFile.setBoolean(context.config().file(), "vim", on);
        } catch (IOException | RuntimeException failed) {
            ask.apply(new Question("Vim mode", "Vim mode is " + (on ? "on" : "off") + " until Jasper restarts: the setting could not be saved ("
                + failed.getMessage() + ").", List.of("OK")));
        }
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
        tab.apply(settings, theme, context.appearance().variant(), context.appearance().editorPalette());
        var tabCommands
```

with:

```java
        tab.apply(settings, theme, context.appearance().variant(), context.appearance().editorPalette());
        tab.vim(vimOn() ? Optional.of(vim) : Optional.empty());
        var tabCommands
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, replace:

```java
        status.display(selected(), environment.settings().get().tabSize());
```

with:

```java
        status.display(selected(), environment.settings().get().tabSize(), vimOn());
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/package-info.java`, replace:

```java
May depend on: {@code api}, {@code text}, {@code syntax}, {@code local}, {@code session}, the SDK, RSyntaxTextArea. */
```

with:

```java
May depend on: {@code api}, {@code text}, {@code syntax}, {@code local}, {@code session}, {@code vim}, {@code vim.ui}, the SDK, RSyntaxTextArea. */
```

- [ ] **Step 7: Run the whole Editor suite**

Run:

```bash
./gradlew :jasper-plugin-editor:test
```

Expected: BUILD SUCCESSFUL. With the corpus, 657 tests pass in `:jasper-plugin-editor` (Task 8 adds two more).

- [ ] **Step 8: Commit**

```bash
git add plugins/editor/src/main plugins/editor/src/test/java/dev/jasper/editor/ui
git commit -m "Turn on Vim mode in Editor tabs: setting, toggle, status item and Vim bar

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 8: Documentation, the spec amendments and the final check

**Files:**
- Create: `plugins/editor/src/test/java/dev/jasper/editor/vim/VimDocsTest.java`, `docs/vim-divergences.md`, `docs/editor-vim-reference.md` (generated)
- Modify: `docs/editor.md`, `docs/README.md`, `docs/editor-verification.md`, `docs/STATUS.md`, `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`

**Interfaces:**
- Consumes: `VimCorpusTest.cases()`, `VimCommands.standard().entries()`, the build's `jasper.repoRoot` and `jasper.updateDocs` properties (Task 5).

`AppDocumentationTest` checks the links in `docs/README.md`, `docs/STATUS.md` and every Markdown file under `docs/superpowers`: every relative link must resolve, and a `#fragment` must name a heading. Keep that in mind when editing those files.

- [ ] **Step 1: Write the failing docs test**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/VimDocsTest.java`:

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
        section(out, commands, VimCommands.Kind.MOTION, "Motions", "In Normal mode and after an operator. A count repeats or scales them.");
        section(out, commands, VimCommands.Kind.OPERATOR, "Operators", "Followed by a motion or text object; doubled (`dd`, `gUU`, `gUgU`) for whole lines.");
        section(out, commands, VimCommands.Kind.OBJECT, "Text objects", "After an operator.");
        section(out, commands, VimCommands.Kind.ACTION, "Normal-mode commands", "");
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

- [ ] **Step 2: Run it to see it fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: both tests fail with `NoSuchFileException` for `docs/vim-divergences.md` and `docs/editor-vim-reference.md`.

- [ ] **Step 3: Write the divergences page**

Create `docs/vim-divergences.md`:

```markdown
# Vim mode: where Jasper differs from Vim

The Editor's Vim mode is checked against Vim 9 by the corpus in `plugins/editor/src/test/resources/vim/corpus`:
each case's expected result was recorded by running real Vim ([Vim mode spec](superpowers/specs/2026-09-27-jasper-editor-vim-design.md),
section 11). This page lists every deliberate difference. `VimDocsTest` fails when the corpus's divergent cases and the
table below disagree. The supported keys are in the [reference](editor-vim-reference.md).

## Corpus cases

A divergent case keeps a hand-written expectation, and `recordVimCorpus` skips it.

| File | Case | Why |
|---|---|---|
| operators-other.case | equals reindents a block | = uses previous-line indent and bracket depth, not Vim's C-indenting |
| operators-other.case | equals equals on a blank line | = uses previous-line indent and bracket depth, not Vim's C-indenting |

## Other differences

- **`=` re-indents simply.** Each line gets the previous non-blank line's indent, one 'shiftwidth' more for each
  bracket that line leaves open, and one less when the line starts with a closing bracket. Vim uses C-indenting.
- **`%` matches brackets only.** Vim also jumps between `/*` and `*/` and between `#if`, `#else` and `#endif`.
- **Typing goes through the Editor.** In Insert mode a typed character runs RSyntaxTextArea's typing, so when the Editor
  closes brackets and quotes as you type, typing `(` also types `)`. The corpus runs with that off.
- **Tabs start from the Editor.** 'tabstop' and 'shiftwidth' start at the Editor's `tab_size`, 'expandtab' follows the
  tab's Spaces or Tab setting, and 'autoindent' is on. Vim's own defaults are 8, 8, off and off. The corpus uses Vim's.
- **The bell is visual.** A failing command flashes the Vim bar; Jasper never beeps. `<Esc>` in Normal mode is silent.
- **Undo is linear.** There is no undo tree (`g-`, `g+`, `:earlier`) and no `U`.
- **`go` counts characters.** Vim counts bytes.
```

- [ ] **Step 4: Generate the reference and run the docs test**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimDocsTest' -PupdateDocs=true
```

Expected: 2 tests pass, and `docs/editor-vim-reference.md` now exists: 198 lines, starting with the heading "Editor Vim mode: supported keys" and a table per kind (Motions, Operators, Text objects, Normal-mode commands, Insert-mode keys).

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 2 tests pass without `-PupdateDocs`, so the committed reference matches the table.

- [ ] **Step 5: Describe Vim mode in the Editor guide**

In `docs/editor.md`, replace:

```markdown
| `max_file_mib` | 10 | larger files are refused, 1–256 |
```

with:

```markdown
| `max_file_mib` | 10 | larger files are refused, 1–256 |
| `vim` | false | Vim mode (below); the Vim status item and Toggle Vim Mode change it |
```

In `docs/editor.md`, replace:

```markdown
## For other plugins
```

with:

```markdown
## Vim mode

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
  a meaning for it in the current mode, and the Editor gets the rest: on Linux and Windows Ctrl+S still saves (Vim
  has no `<C-s>`), while `<C-r>` redoes in Normal mode and `<C-w>` deletes a word in Insert mode.
- `"+` is the clipboard and `"*` the selection (the clipboard where there is none).
- 'tabstop' and 'shiftwidth' start at `tab_size`, 'expandtab' follows the tab's Spaces or Tab setting, and
  'autoindent' is on.
- Not yet: Visual mode, search (`/`, `n`, `*`), marks, scrolling commands, the `:` command line, macros, a vimrc,
  and emulated plugins such as surround and commentary. They follow in later releases.

## For other plugins
```

- [ ] **Step 6: Link the new pages from the documentation index**

In `docs/README.md`, replace:

```markdown
- [Editor](editor.md): editing local and remote files in tabs in the Editor window, saving safely, and outside changes; [verification](editor-verification.md).
```

with:

```markdown
- [Editor](editor.md): editing local and remote files in tabs in the Editor window, saving safely, and outside changes; [verification](editor-verification.md). Vim mode: [supported keys](editor-vim-reference.md) and [differences from Vim](vim-divergences.md).
```

- [ ] **Step 7: Add the GUI checks for the user**

Append to `docs/editor-verification.md`:

```markdown

## Vim mode (plan V1a)

- [ ] 20. The Vim status item and Toggle Vim Mode switch every open tab between Vim mode and plain editing, keeping text and undo; after a restart the choice holds (`vim = true` is in the settings file).
- [ ] 21. The caret is a block in Normal mode, a bar in Insert mode (`i`) and an underline after `d`; the strip under the text shows `-- INSERT --` and pending keys such as `2d`, and `"qp` on an empty register shows `E353: Nothing in register q` and flashes.
- [ ] 22. Typing fast in Insert mode keeps every character in order; `u` then undoes the whole insert, and `.` repeats it elsewhere.
- [ ] 23. macOS: Option characters (⌥e e for é) insert in Insert mode and work after `f`; ⌘S, ⌘F, ⌘Z and ⌘W still reach the Editor.
- [ ] 24. Linux or Windows: Ctrl+S saves in Normal mode, Ctrl+R redoes, and Ctrl+W in Insert mode deletes a word rather than closing the tab.
- [ ] 25. With a Japanese or Chinese input method on, `dd` and `x` work in Normal mode, and composed text inserts in Insert mode and repeats with `.`.
- [ ] 26. `"+yy` then pasting in another application gives the line; text copied elsewhere comes back with `"+p`.
- [ ] 27. Clicking past the end of a line in Normal mode puts the caret on the line's last character.
- [ ] 28. A remote file opened from the SFTP browser edits and saves in Vim mode as it does without it.
```

- [ ] **Step 8: Amend the spec**

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
**Status:** Draft for the user's review, 2026-09-27.
```

with:

```markdown
**Status:** Approved by the user on 2026-09-27. Plan V1a (`docs/superpowers/plans/2026-09-27-jasper-editor-vim-v1a.md`) amends sections 3.8, 4.1 and 13, as marked **Amended (V1a)**.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
Defaults where the Editor has its own setting: `tabstop` and `shiftwidth` = the Editor's `tab_size`; `expandtab` = the tab's Spaces state; `wrap` = the tab's Wrap state; `number` on.
```

with:

```markdown
Defaults where the Editor has its own setting: `tabstop` and `shiftwidth` = the Editor's `tab_size`; `expandtab` = the tab's Spaces state; `autoindent` on, like the Editor's own auto-indent (**Amended (V1a)**); `wrap` = the tab's Wrap state; `number` on.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- Numbers: `<C-a> <C-x>` with `nrformats` (default `bin,hex`, as in Vim 9), on the number under or after the cursor.
```

with:

```markdown
- Numbers: `<C-a> <C-x>` with `nrformats` (default `bin,octal,hex`, Vim's value with `-u NONE`; **Amended (V1a)**), on the number under or after the cursor.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
the setting, toggle and status item, the corpus format, recorder, replay test and coverage guard. Runnable: Vim-mode editing without Visual mode or search.
```

with:

```markdown
the setting, toggle and status item, the corpus format, recorder, replay test and coverage guard. Runnable: Vim-mode editing without Visual mode or search. **Amended (V1a):** the screen-line motions `H M L gj gk g0 g^ g$ gm`, `gi`, `<C-g>`, `ga` and blockwise forced motions (`d<C-v>j`) move to V1b; `ZZ` and `ZQ` to V2; `timeoutlen` and reading `sethandler` from the vimrc to V3.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
scrolling, folds, `<C-a>`/`<C-x>`, `gq`/`gw`.
```

with:

```markdown
scrolling, folds, `<C-a>`/`<C-x>`, `gq`/`gw`; and, moved from V1a, the screen-line motions, `gi`, `<C-g>`, `ga` and blockwise forced motions.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- **Plan V2: ex command line.** Section 6, the output panel and the relative-number gutter.
```

with:

```markdown
- **Plan V2: ex command line.** Section 6, the output panel and the relative-number gutter; `ZZ` and `ZQ`.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- **Plan V3: macros and mappings.** Section 4.3 and section 7.
```

with:

```markdown
- **Plan V3: macros and mappings.** Section 4.3 and section 7, with `timeoutlen` (section 2.3) and `sethandler` lines.
```

- [ ] **Step 9: Run the full check**

Run:

```bash
./gradlew check
```

Expected: BUILD SUCCESSFUL, including `verifyPluginArchitecture` and `AppDocumentationTest`.

Count the results, which Step 10 records:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t = f = e = s = 0
for path in glob.glob("*/build/test-results/test/*.xml") + glob.glob("plugins/*/build/test-results/test/*.xml"):
    r = ET.parse(path).getroot()
    t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
print(f"{t} tests, {t - f - e - s} passed, {f} failed, {e} errors, {s} skipped")
PY
```

Expected: 0 failed and 0 errors. `:jasper-plugin-editor` alone has 659 tests.

- [ ] **Step 10: Record the work in STATUS**

Add this entry at the top of the list under "## Current state" in `docs/STATUS.md`, putting the numbers Step 9 printed in place of `<T>` and `<P>`, and `<S>` for skipped:

```markdown
- **Editor Vim mode, plan V1a** (branch `claude/vim-mode`, plan
  `docs/superpowers/plans/2026-09-27-jasper-editor-vim-v1a.md`, spec
  `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, 2026-09-27): a headless Vim engine in
  `dev.jasper.editor.vim` on every tab's `VimTextArea`: Normal, Insert and Replace modes, counts, motions, text
  objects, operators, registers (with `"+` and `"*`), undo in Vim's steps, and `.`. The handler rule gives Vim only
  the keys it defines; ⌘ keys stay with the Editor. The Vim bar shows the mode, pending keys and messages. The
  `vim` setting, the Vim status item and Toggle Vim Mode switch it (the toggle writes the settings file).
  - Fidelity: 464 corpus cases in `plugins/editor/src/test/resources/vim/corpus`, recorded from Vim 9.1 by
    `./gradlew :jasper-plugin-editor:recordVimCorpus` (not part of `check`) and replayed by `VimCorpusTest`; every
    command in `VimCommands` has a case. Two `=` cases are divergent; `docs/vim-divergences.md` lists every
    difference and `docs/editor-vim-reference.md` is generated from the command table.
  - Deviations from the spec are in the plan's header and the spec's amended sections 3.8, 4.1 and 13.
  - `./gradlew check`: <T> tests, <P> passed, 0 failed, <S> skipped.
  - Not done: GUI checks 20–28 of the [checklist](editor-verification.md) (user-run); plans V1b, V2, V3 and V4; push.
```

- [ ] **Step 11: Commit**

```bash
git add plugins/editor/src/test/java/dev/jasper/editor/vim/VimDocsTest.java docs
git commit -m "Document Vim mode: guide, supported keys, divergences, checks and status

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

## After the plan

Offer the user `superpowers:finishing-a-development-branch`: merging `claude/vim-mode` into local `main` once they
agree, and running GUI checks 20–28 themselves. Plan V1b (Visual mode, search, marks, scrolling, folds and the moved
screen-line motions) is written next.
