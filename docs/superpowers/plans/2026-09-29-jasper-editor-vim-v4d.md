# Editor Vim mode, plan V4d: sneak — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The last extension of spec section 8: sneak (`set sneak` or its `Plug` line). `s{char}{char}` goes to the next place the two characters stand together, `S` back, `z` and `Z` after an operator, `;` and `,` repeat it (Vim's own again after `f` or `t`), a count keeps `s` to the columns near the cursor, and the matches ahead are drawn until the cursor moves on.

**Architecture:** As the other extensions, sneak ports its plugin's script (`sneak#wrap`, `sneak#to`, `sneak#rpt`, `s:getnchars`) onto the engine. Its commands are motions that read their characters first: the Machine gains `VimCommands.motionWithKeys`, which reads keys one at a time until the motion says it has enough (asked before the first key too, for `g:sneak#s_next`), so `s`, `z` after an operator and `s` in Visual mode are one motion. The plugin's search (`\V` and the characters, each letter an equivalence class, with a vertical scope's column bounds) is a direct scan with the same rules. The matches are a fourth kind of highlight, which lasts while the cursor stays where the sneak left it.

**Tech Stack:** Java 25 (JBR), Swing, RSyntaxTextArea 3.6.0, JUnit 6 and AssertJ, Vim 9 and git (only for `recordVimCorpus`).

**Spec:** `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md` (plan V4 of section 13, split into V4a–V4d; section 8). Plans V1a–V4c are merged; their headers describe the engine and the extension framework.

**Status:** Written 2026-09-29 on branch `claude/vim-v4d` (worktree `.worktrees/vim-v4d`, from `main` at ef024264). Every source file, test and corpus case below was compiled and run before the plan was written: against Vim 9.1 with the pinned sneak all 2211 corpus cases (47 new) passed, with and without a final line break. Applied to a fresh checkout of `ef024264` exactly as written, `./gradlew check` passed (7,147 tests).

## Deviations from the spec (approved scope, adjusted in this plan)

Task 2 writes these into the spec as **Amended (V4d)**.

- **No label mode.** `<Plug>SneakLabel_s`, `<Plug>SneakLabel_S`, `g:sneak#label` and the `<Plug>(SneakStreak…)` names are not there (the plugin's default is off); nor are `g:sneak#prompt` and the plugin's not-found message.
- **`.` repeats the keys**, as Jasper records them, where the plugin sets vim-repeat to its `<Plug>SneakRepeat`; the result is the same. The corpus cannot record `.` after a sneak (vim-repeat's replay reads its keys with getchar() again), so a unit test checks it.
- **The equivalence classes** (`a` also finds `à`) are the letters whose Unicode decomposition starts with the letter, where Vim has its own table.
- **The default mappings** check mapcheck() and hasmapto() in the mode each is for; `,` does not fall back to `\` when the user took `,`. The compatibility names (`<Plug>SneakForward` and the rest) are mapped as the plugin maps them.
- **The highlight** is magenta, light enough to read the text through; it ends when the cursor moves, Insert mode starts or the tab changes its text.

## Global Constraints

- Java 25 on the JetBrains Runtime; always `./gradlew`, never a system Gradle. Work in `.worktrees/vim-v4d` on `claude/vim-v4d`; check `git branch --show-current` before every commit (other sessions switch branches in the main checkout). Never commit on `main`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.
- The Editor plugin compiles against the SDK only (`verifyPluginArchitecture`, a root task). `dev.jasper.editor.vim` may use `vim.regex`, RSyntaxTextArea, Swing and the JDK. No SDK change.
- No interface without two real implementations; this plan adds none.
- Source hygiene: never put raw control, private-use or unpaired surrogate characters in `.java` files; use escapes. No run of three backticks may appear in any file this plan embeds (the docs link check strips fenced blocks lazily).
- **Do not launch the GUI.** GUI checks go in `docs/editor-verification.md` for the user.
- Engine code runs on the event thread in the app. Tests that type through a `VimEditor` run inside `SwingUtilities.invokeAndWait`.
- The corpus is the authority on Vim behaviour, and every case also runs without a final line break (`VimCorpusTest`); mark a case `divergent:` only for a documented, deliberate difference. Give new commands cases on the one empty line and on the last line.
- `vim` and `git` must be on `PATH` for `recordVimCorpus`, which clones the pinned sneak from GitHub the first time. `check` never runs Vim or git.

## File structure

New: `plugins/editor/src/main/java/dev/jasper/editor/vim/Sneak.java` (`<Plug>Sneak_s`, `_S`, `_f`, `_F`, `_t`, `_T`, `_;`, `_,`; the plugin's state and search). Changed: `Machine` and `VimCommands` (motions that read keys), `Context` (`keys`), `Motions` (`f`, `F`, `t`, `T` give `;` back), `Options` (`sneak`), `Extensions` (sneak's mappings; the `MAPCHECK` check), `VimEditor` and `Highlights` (the sneak highlight). Tests: `ExtensionsTest`. Corpus: `ext-sneak.case`. Changed: `plugins/editor/vim-oracle.properties`.
Docs: `docs/vim-divergences.md`, `docs/editor-vim-reference.md` (generated), `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec.

---

### Task 1: sneak

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/Sneak.java`, `plugins/editor/src/test/resources/vim/corpus/ext-sneak.case`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Context.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Motions.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, `plugins/editor/src/main/java/dev/jasper/editor/vim/Highlights.java`, `plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java`, `plugins/editor/vim-oracle.properties`, `docs/editor-vim-reference.md` (generated)

**Interfaces:**
- Consumes: V4a–V4c's `Extensions` (`Extension`, `Check`, `require`, `script`), `Script.normal`, `KeyCodes.text`.
- Produces: `VimCommands.motionWithKeys(keys, description, enough, motion)` with `enough` a `BiPredicate<VimEditor, List<KeyInput>>` and `keyReader(entry)`; `Context.keys` (the keys the motion read); `Extensions.Check.MAPCHECK` and the `always` mappings; the option `sneak`; `<Plug>Sneak_s`, `_S`, `_f`, `_F`, `_t`, `_T` (motions that read keys), `<Plug>Sneak_;`, `<Plug>Sneak_,`; `Sneak.findTyped(context)`; `VimEditor.sneakHighlight(ranges, at)`; highlight kind 3 in `Highlights`.

A sneak after an operator is the plugin's `:` motion: exclusive, and for the `f`-like forms one character further (`norm! l`, past the last character with 'virtualedit' onemore). Not found leaves the cursor, with no error. The search sets the jump mark only for a new sneak, not while one is going on.

- [ ] **Step 1: Write the failing tests and cases**

Create `plugins/editor/src/test/resources/vim/corpus/ext-sneak.case`:

```text
# sneak (plan V4d): s, S, z, Z, ; and , over two characters.

=== s goes to the next two characters
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: stw
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,5
expect-mode: normal

=== s past the line
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,15
ext: sneak
keys: stw
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 2,6
expect-mode: normal

=== S goes back
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 4,5
ext: sneak
keys: Stw
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 4,1
expect-mode: normal

=== s then ; goes on
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: stw;
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,15
expect-mode: normal

=== s then ; twice
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: stw;;
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 2,6
expect-mode: normal

=== s then , goes back
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: stw;;,
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,15
expect-mode: normal

=== S then ; goes on backwards
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 4,1
ext: sneak
keys: Stw;
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 2,6
expect-mode: normal

=== s not found stays
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: sxy
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,1
expect-mode: normal

=== s with a count keeps to the columns near the cursor
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,5
ext: sneak
keys: 3stw
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 2,6
expect-mode: normal

=== s with a count then ;
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,5
ext: sneak
keys: 3stw;
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 4,1
expect-mode: normal

=== s Enter repeats the last input
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: stw0s<CR>
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,5
expect-mode: normal

=== s Esc cancels
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: st<Esc>
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,1
expect-mode: normal

=== s with one character and Enter
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: sf<CR>
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 2,1
expect-mode: normal

=== dz deletes up to the match
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: dztw
registers: " 0 1 -
--- vim 9.1
expect-text:
  two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "one "
expect-register 0: none
expect-register 1: none
expect-register -: c "one "

=== dZ deletes back to the match
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,15
ext: sneak
keys: dZtw
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two
  four twofold two
  five
  two and tw
expect-cursor: 1,5
expect-mode: normal
expect-register ": c "two three "
expect-register 0: none
expect-register 1: none
expect-register -: c "two three "

=== yz yanks up to the match
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,3
ext: sneak
keys: yztw
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,3
expect-mode: normal
expect-register ": c "e "
expect-register 0: c "e "
expect-register 1: none
expect-register -: none

=== cz changes up to the match
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: cztwX<Esc>
registers: " 0 1 -
--- vim 9.1
expect-text:
  Xtwo three two
  four twofold two
  five
  two and tw
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "one "
expect-register 0: none
expect-register 1: none
expect-register -: c "one "

=== dz not found
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: dzxy
registers: " 0 1 -
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,1
expect-mode: normal
expect-register ": none
expect-register 0: none
expect-register 1: none
expect-register -: none

=== d; after s
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: stw0d;
registers: " 0 1 -
--- vim 9.1
expect-text:
  two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "one "
expect-register 0: none
expect-register 1: none
expect-register -: c "one "

=== vs extends the area
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: vstw
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,5
expect-mode: visual
expect-visual: 1,1

=== vs then ;
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: vstw;
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,15
expect-mode: visual
expect-visual: 1,1

=== vZ goes back
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 2,10
ext: sneak
keys: vZon
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,1
expect-mode: visual
expect-visual: 2,10

=== vs then d
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: vstwd
registers: " 0 1 -
--- vim 9.1
expect-text:
  wo three two
  four twofold two
  five
  two and tw
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "one t"
expect-register 0: none
expect-register 1: none
expect-register -: c "one t"

=== f resets ; to Vim's
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: stw0fe;
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,12
expect-mode: normal

=== t resets ; too
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: stw0to;
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,16
expect-mode: normal

=== ; before any sneak is Vim's
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: fe;
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,12
expect-mode: normal

=== backtick backtick after s
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: stw``
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,1
expect-mode: normal

=== s twice then backtick backtick
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: stwstw``
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,1
expect-mode: normal

=== s on the last line
text:
  ab ab
  ab
cursor: 2,1
ext: sneak
keys: sab
--- vim 9.1
expect-text:
  ab ab
  ab
expect-cursor: 2,1
expect-mode: normal

=== S on the first line
text:
  ab ab
  ab
cursor: 1,4
ext: sneak
keys: Sab
--- vim 9.1
expect-text:
  ab ab
  ab
expect-cursor: 1,1
expect-mode: normal

=== s on the one empty line
text:
  $
cursor: 1,1
ext: sneak
keys: sab
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal

=== s matches an accented letter
text:
  x é y e
cursor: 1,1
ext: sneak
keys: s y
--- vim 9.1
expect-text:
  x é y e
expect-cursor: 1,4
expect-mode: normal

=== s matches a letter with an accent
text:
  o cafe café
cursor: 1,1
ext: sneak
keys: sfe;
--- vim 9.1
expect-text:
  o cafe café
expect-cursor: 1,10
expect-mode: normal

=== s matches case
text:
  Ab ab
cursor: 1,1
ext: sneak
keys: sab
--- vim 9.1
expect-text:
  Ab ab
expect-cursor: 1,4
expect-mode: normal

=== s with use_ic_scs and ignorecase
text:
  Ab ab AB
cursor: 1,2
set: ic
ext: sneak
let: g:sneak#use_ic_scs = 1
keys: sab
--- vim 9.1
expect-text:
  Ab ab AB
expect-cursor: 1,4
expect-mode: normal

=== s over a tab
text:
  a	b a	b
cursor: 1,2
ext: sneak
keys: sa<Tab>
--- vim 9.1
expect-text:
  a	b a	b
expect-cursor: 1,5
expect-mode: normal

=== s with s_next goes on
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
let: g:sneak#s_next = 1
keys: stws
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,15
expect-mode: normal

=== u after dz
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: dztwu
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,1
expect-mode: normal

=== Sneak_f mapped to f
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: :nmap f <lt>Plug>Sneak_f<CR>fo;
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,17
expect-mode: normal

=== Sneak_t mapped to t
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: :nmap t <lt>Plug>Sneak_t<CR>to;
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,16
expect-mode: normal

=== Sneak_F mapped to F
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,17
ext: sneak
keys: :nmap F <lt>Plug>Sneak_F<CR>Fo;
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,1
expect-mode: normal

=== Sneak_T mapped to T
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,17
ext: sneak
keys: :nmap T <lt>Plug>Sneak_T<CR>To;
--- vim 9.1
expect-text:
  one two three two
  four twofold two
  five
  two and tw
expect-cursor: 1,2
expect-mode: normal

=== Sneak_t after an operator
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: :omap t <lt>Plug>Sneak_t<CR>dto
registers: " 0 1 -
--- vim 9.1
expect-text:
  o three two
  four twofold two
  five
  two and tw
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "one tw"
expect-register 0: none
expect-register 1: none
expect-register -: c "one tw"

=== Sneak_f after an operator
text:
  one two three two
  four twofold two
  five
  two and tw
cursor: 1,1
ext: sneak
keys: :omap f <lt>Plug>Sneak_f<CR>dfo
registers: " 0 1 -
--- vim 9.1
expect-text:
   three two
  four twofold two
  five
  two and tw
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "one two"
expect-register 0: none
expect-register 1: none
expect-register -: c "one two"

=== Sneak_f to the last character
text:
  abc
cursor: 1,1
ext: sneak
keys: :omap f <lt>Plug>Sneak_f<CR>dfc
registers: " 0 1 -
--- vim 9.1
expect-text:
  $
expect-cursor: 1,1
expect-mode: normal
expect-register ": c "abc"
expect-register 0: none
expect-register 1: none
expect-register -: c "abc"

=== the default mappings
text:
  a
cursor: 1,1
ext: sneak
keys: :nmap s<CR>:omap z<CR>:xmap Z<CR>:nmap ;<CR>
output: yes
--- vim 9.1
expect-text:
  a
expect-cursor: 1,1
expect-mode: normal
expect-output:
  n  s             <Plug>Sneak_s
  o  z             <Plug>Sneak_s
  x  Z             <Plug>Sneak_S
  n  ;             <Plug>Sneak_;

=== s taken by the user keeps it
text:
  ab ab
cursor: 1,1
ext: sneak
keys: sx<Esc>
--- vim 9.1
expect-text:
  ab ab
expect-cursor: 1,1
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

    @Test void sneakRepeatsWithDotAndDrawsTheMatchesAhead() throws Exception {
        onEdt(() -> {
            var editor = editor("one two three two four two\n");
            type(editor, ":Plug 'justinmk/vim-sneak'<CR>");
            assertThat(editor.options().flag("sneak")).isTrue();
            type(editor, "dztw");
            assertThat(editor.area().getText()).isEqualTo("two three two four two\n");
            type(editor, "w.");
            assertThat(editor.area().getText()).as(". deletes to the next tw again").isEqualTo("two two four two\n");
            type(editor, "0stw");
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 4));
            assertThat(editor.highlights().stream().filter(r -> r[0] == 3).map(r -> r[1] + "-" + r[2]).toList())
                .as("the matches ahead and the one reached").containsExactly("13-15", "4-6");
            type(editor, "l");
            assertThat(editor.highlights().stream().filter(r -> r[0] == 3).toList()).as("moving on ends it").isEmpty();
        });
    }

    @Test void sneakLeavesTheUsersKeysAlone() throws Exception {
        onEdt(() -> {
            var editor = editor("x\n");
            type(editor, ":nnoremap sa x<CR>:set sneak<CR>");
            assertThat(mapped(editor, Mappings.NORMAL, "s")).as("mapcheck(): sa starts with s").isFalse();
            assertThat(mapped(editor, Mappings.NORMAL, "S")).isTrue();
            assertThat(mapped(editor, Mappings.OP_PENDING, "z")).isTrue();
            assertThat(mapped(editor, Mappings.NORMAL, "<Plug>SneakForward")).as("the old names, always").isTrue();
        });
    }
}
```

In `plugins/editor/vim-oracle.properties`, replace:

```properties
argtextobj = https://github.com/vim-scripts/argtextobj.vim.git f3fbe427f7b4ec436416a5816d714dc917dc530b
matchit = packadd
```

with:

```properties
argtextobj = https://github.com/vim-scripts/argtextobj.vim.git f3fbe427f7b4ec436416a5816d714dc917dc530b
matchit = packadd
sneak = https://github.com/justinmk/vim-sneak.git feea86adcfbf8e6b5e71fdd5f4f5736fd8819fdb
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExtensionsTest'
```

Expected: FAIL: `sneakRepeatsWithDotAndDrawsTheMatchesAhead` and `sneakLeavesTheUsersKeysAlone` fail: there is no `sneak` extension.

- [ ] **Step 3: Write sneak and the motions that read keys**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Sneak.java`:

```java
package dev.jasper.editor.vim;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * The sneak extension (spec 8), ported from Justin M. Keyes's vim-sneak: {@code s{c}{c}} goes to the next place the
 * two characters stand together, {@code S} to the one before, {@code z} and {@code Z} after an operator; {@code ;}
 * and {@code ,} repeat it (Vim's own {@code ;} and {@code ,} again after an {@code f} or {@code t}), and
 * {@code <Plug>Sneak_f} and the rest are one-character forms for mapping to {@code f}, {@code t} and the like. A
 * count before {@code s} keeps it to the columns that near the cursor ("vertical scope"). The matches ahead are drawn
 * until the cursor moves on. Label mode is not there.
 */
final class Sneak {
    private static final String OPTION = "sneak";

    /** The plugin's s:st: the last sneak, for {@code ;} and {@code ,}; {@code rst} when f or t took them back. */
    private boolean rst = true, reverse, hooked;
    private String input = "";
    private int inputlen, inclusive;
    private int[] bounds = { 0, 0 };
    /** Whether a sneak's highlight and state last until the cursor moves (the plugin's sneak autocommands). */
    private VimEditor sneakingIn;
    private Pos sneakingAt;

    static void register(VimCommands commands) {
        sneak(commands, "<Plug>Sneak_s", "to the next place {char}{char} stand together (`s`, `z` after an operator, with `set sneak`)", 2, false, 2);
        sneak(commands, "<Plug>Sneak_S", "back to the place {char}{char} stand together (`S`, `Z` after an operator)", 2, true, 2);
        sneak(commands, "<Plug>Sneak_f", "to the next {char}, as sneak repeats it", 1, false, 1);
        sneak(commands, "<Plug>Sneak_F", "back to the {char}, as sneak repeats it", 1, true, 1);
        sneak(commands, "<Plug>Sneak_t", "till the next {char}, as sneak repeats it", 1, false, 0);
        sneak(commands, "<Plug>Sneak_T", "back till the {char}, as sneak repeats it", 1, true, 0);
        commands.motion("<Plug>Sneak_;", "repeat the last sneak (`;`); after `f` or `t`, their `;`", c -> repeat(c, false));
        commands.motion("<Plug>Sneak_,", "repeat the last sneak the other way (`,`)", c -> repeat(c, true));
    }

    private static void sneak(VimCommands commands, String name, String description, int inputlen, boolean reverse, int inclusive) {
        commands.motionWithKeys(name, description, (editor, keys) -> enough(editor, keys, inputlen, inclusive), c -> wrap(c, inputlen, reverse, inclusive));
    }

    /**
     * s:getnchars: {@code n} characters, fewer with {@code <CR>}; a key that cancels ends it at once. With
     * {@code g:sneak#s_next} a sneak like the last, while that one is going on, reads none: it repeats it.
     */
    private static boolean enough(VimEditor editor, List<KeyInput> keys, int n, int inclusive) {
        if (keys.isEmpty()) {
            Vim vim = editor.vim();
            if (!vim.extensions().on(OPTION) || number(vim, "sneak#s_next", 0) == 0 || editor.machine().operatorPending()) return false;
            Sneak st = vim.extensions().script(Sneak.class, Sneak::new);
            return n == st.inputlen && inclusive == st.inclusive && st.sneaking(editor);
        }
        KeyInput last = keys.getLast();
        return keys.size() >= n || cancels(last) || last.is("<CR>");
    }

    private static boolean cancels(KeyInput key) {
        return key.is("<Esc>") || key.is("<C-c>") || key.is("<C-g>") || key.is("<BS>") || key.is("<Del>");
    }

    private static Sneak state(Context c) { return c.vim.extensions().script(Sneak.class, Sneak::new); }

    /** f, F, t and T in Normal or Visual mode give {@code ;} and {@code ,} back to them (the plugin's sneak#reset). */
    static void findTyped(Context c) {
        if (c.operator != null || !c.vim.extensions().on(OPTION)) return;
        Sneak st = state(c);
        if (!st.hooked) return;
        st.rst = true;
        st.reverse = false;
    }

    /** sneak#wrap: the keys read become the input; {@code <CR>} first repeats the last one. */
    private static Target wrap(Context c, int n, boolean reverse, int inclusive) {
        Extensions.require(c, OPTION);
        Sneak st = state(c);
        StringBuilder s = new StringBuilder();
        for (KeyInput key : c.keys) {
            if (cancels(key)) return cancelled(c);
            if (key.is("<CR>")) {
                if (s.isEmpty()) s.append(st.input);
                break;
            }
            s.append(KeyCodes.text(List.of(key)));
        }
        if (c.keys.isEmpty()) return st.rpt(c, reverse);
        return st.to(c, s.toString(), n, c.count, false, reverse, inclusive);
    }

    /** An empty input: nothing moves. */
    private static Target cancelled(Context c) { throw VimFailure.quiet(); }

    /** sneak#rpt: {@code ;} and {@code ,}. */
    private static Target repeat(Context c, boolean reverse) {
        Extensions.require(c, OPTION);
        return state(c).rpt(c, reverse);
    }

    private Target rpt(Context c, boolean reverseKey) {
        VimEditor editor = c.editor;
        if (rst) {
            // exec "norm! " . v:count1 . (a:reverse ? "," : ";"): Vim's own, as a : motion.
            Pos start = editor.cursor();
            Script.normal(editor, c.count + (reverseKey ? "," : ";"));
            Pos to = editor.cursor();
            editor.setCursor(start);
            return Target.exclusive(to);
        }
        boolean relativeReverse = reverseKey != reverse;
        boolean absolute = number(c.vim, "sneak#absolute_dir", 0) != 0;
        return to(c, input, inputlen, c.count, true, absolute ? reverseKey : relativeReverse, inclusive);
    }

    /** sneak#to: the search, from where the cursor is, as the plugin moves it. */
    private Target to(Context c, String in, int len, int count, boolean repeatmotion, boolean rev, int incl) {
        VimEditor editor = c.editor;
        Buffer b = editor.buffer();
        if (in.isEmpty()) return cancelled(c);
        boolean isOp = c.operator != null;
        Pos cur = editor.cursor();
        int ts = c.tabstop();
        int vcol = vcol(b, cur, ts);
        boolean ic = number(c.vim, "sneak#use_ic_scs", 0) != 0 && c.options.flag("ignorecase")
            && !(c.options.flag("smartcase") && in.chars().anyMatch(Character::isUpperCase));
        int skip = isOp || repeatmotion || len < 2 ? Math.min(999, count) : 0;
        int[] bnds = repeatmotion ? bounds.clone() : new int[] { 0, 0 };
        if ((skip == 0 && count > 1 || Math.max(bnds[0], bnds[1]) > 0) && !isOp && Math.max(bnds[0], bnds[1]) == 0) {
            bnds[0] = Math.max(0, vcol - count - 1);
            bnds[1] = count + vcol + 1;
        }
        int leftbound = Math.max(bnds[0], bnds[1]) > 0 ? Math.max(0, bnds[0] - len + 1) : -1;
        Search search = new Search(b, in, ic, leftbound, bnds[1], ts, rev);
        boolean setMark = !repeatmotion && !sneaking(editor);
        if (!repeatmotion) {
            rst = false;
            input = in;
            inputlen = len;
            reverse = rev;
            bounds = bnds;
            inclusive = incl;
            hooked = true;
        }
        Pos nextchar = nextChar(b, cur, !rev);
        boolean nudge = incl == 0 && repeatmotion && nextchar != null && nextchar.equals(search.next(cur));
        if (nudge) cur = nextchar;
        Pos matchpos = null;
        for (int i = 0; i < Math.max(1, skip); i++) {
            Pos found = search.next(cur);
            if (found == null) {
                matchpos = null;
                break;
            }
            matchpos = found;
            cur = found;
            nudge = incl == 0;
        }
        sneakingIn = editor;
        if (matchpos == null) {
            // Not found: the cursor stays where the last match found put it (for t, a step back), and nothing fails.
            if (nudge) {
                Pos back = nextChar(b, cur, rev);
                if (back != null) cur = back;
            }
            sneakingAt = cur;
            editor.sneakHighlight(List.of(), cur);
            return target(cur, setMark && !cur.equals(editor.cursor()));
        }
        if (nudge) {
            Pos back = nextChar(b, cur, rev);
            if (back != null) cur = back;
        }
        if (isOp && incl != 2 && !rev) {
            if (c.forced == 'v') {
                Pos back = nextChar(b, cur, false);
                if (back != null) cur = back;
            } else {
                // norm! l; on the last character only an f-like sneak goes past it ('virtualedit' onemore).
                String line = b.line(cur.line);
                int next = cur.col < line.length() ? Text.next(line, cur.col) : line.length();
                if (next < line.length() || next == line.length() && cur.col < line.length() && incl == 1) cur = new Pos(cur.line, next);
            }
        }
        sneakingAt = cur;
        editor.sneakHighlight(isOp ? List.of() : search.highlight(cur, editor, in.codePointCount(0, in.length())), cur);
        return target(cur, setMark);
    }

    private static Target target(Pos pos, boolean jump) {
        Target t = Target.exclusive(pos);
        return jump ? t.asJump() : t;
    }

    /** Whether the last sneak is still going on: the cursor has stayed where it left it (sneak#is_sneaking). */
    private boolean sneaking(VimEditor editor) {
        return sneakingIn == editor && sneakingAt != null && sneakingAt.equals(editor.cursor()) && !editor.mode().inserting();
    }

    /** searchpos('\_.', 'nW'): the next position, the end of a line and the next line's start counted. */
    private static Pos nextChar(Buffer b, Pos p, boolean forward) {
        String line = b.line(p.line);
        if (forward) {
            if (p.col < line.length()) return new Pos(p.line, p.col < line.length() - 1 ? Text.next(line, p.col) : line.length());
            return p.line + 1 < b.count() ? new Pos(p.line + 1, 0) : null;
        }
        if (p.col > 0) return new Pos(p.line, Text.previous(line, Math.min(p.col, line.length())));
        return p.line > 0 ? new Pos(p.line - 1, b.line(p.line - 1).length()) : null;
    }

    /** The display column, from 1, of a position (virtcol()). */
    private static int vcol(Buffer b, Pos p, int ts) {
        String line = b.line(p.line);
        return Text.virtcol(line, Math.min(p.col, line.length()), ts) + 1;
    }

    /** @return a {@code g:} number, or the default */
    private static long number(Vim vim, String name, long fallback) {
        Object value = vim.variables().get(name);
        if (value == null) return fallback;
        try { return Long.parseLong(value.toString().strip()); }
        catch (NumberFormatException notNumber) { return fallback; }
    }

    /**
     * The plugin's search: {@code \V} and the input with each letter an equivalence class ({@code [[=a=]]}), matched
     * with case unless {@code g:sneak#use_ic_scs} says otherwise, starting in the columns of a vertical scope, in the
     * one direction and without wrapping.
     */
    private record Search(Buffer b, String input, boolean ignoreCase, int leftbound, int right, int ts, boolean reverse) {
        /** @return the next match's start from {@code from}, not at it; null for none */
        Pos next(Pos from) {
            if (!reverse) {
                for (int line = from.line; line < b.count(); line++) {
                    String text = b.line(line);
                    for (int col = line == from.line ? next(text, from.col) : 0; col < text.length(); col = next(text, col))
                        if (at(line, col)) return new Pos(line, col);
                }
                return null;
            }
            for (int line = from.line; line >= 0; line--) {
                String text = b.line(line);
                int start = line == from.line ? Math.min(from.col, text.length()) : text.length();
                for (int col = start > 0 ? Text.previous(text, start) : -1; col >= 0; col = col > 0 ? Text.previous(text, col) : -1)
                    if (at(line, col) && (line != from.line || col < from.col)) return new Pos(line, col);
            }
            return null;
        }

        private static int next(String text, int col) { return col < text.length() ? Text.next(text, col) : col + 1; }

        /** Whether the input matches at the position. */
        boolean at(int line, int col) {
            String text = b.line(line);
            if (leftbound >= 0) {
                int v = Text.virtcol(text, col, ts) + 1;
                if (!(v > leftbound && v < right)) return false;
            }
            int i = col;
            for (int k = 0; k < input.length(); ) {
                if (i >= text.length()) return false;
                int want = input.codePointAt(k), got = text.codePointAt(i);
                if (!same(want, got)) return false;
                k += Character.charCount(want);
                i += Character.charCount(got);
            }
            return true;
        }

        private boolean same(int want, int got) {
            if (ignoreCase) {
                want = Character.toLowerCase(want);
                got = Character.toLowerCase(got);
            }
            if (want == got) return true;
            // \a: an ASCII letter matches its equivalence class, the letters with it as their base.
            return want < 128 && Character.isLetter(want) && base(got) == want;
        }

        private int base(int c) {
            String nfd = Normalizer.normalize(new String(Character.toChars(c)), Normalizer.Form.NFD);
            int base = nfd.codePointAt(0);
            return ignoreCase ? Character.toLowerCase(base) : base;
        }

        /** The matches ahead of the cursor, as the plugin draws them (within 99 lines of the view), and the current one. */
        List<int[]> highlight(Pos cursor, VimEditor editor, int matchlen) {
            List<int[]> out = new ArrayList<>();
            int top = Math.max(0, editor.view().topline - 1 - 99), bottom = Math.min(b.count() - 1, editor.view().botline - 1 + 99);
            Pos p = cursor;
            while (true) {
                Pos m = next(p);
                if (m == null || m.line < top || m.line > bottom) break;
                out.add(range(m, 3));
                p = m;
            }
            if (matchlen > 1) out.add(range(cursor, 3));
            return out;
        }

        private int[] range(Pos p, int kind) {
            String text = b.line(p.line);
            int end = p.col;
            for (int k = 0; k < input.codePointCount(0, input.length()) && end < text.length(); k++) end = Text.next(text, end);
            return new int[] { kind, b.offset(p), b.offset(p.line, end) };
        }
    }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
    /** Whether the Visual area an operator is about to act on is one an extension's object selected. */
    private boolean objectArea;
    /** Where the undo step's cursor is, when an extension's script changed the text before the operator did. */
    private Pos undoCursor;
```

with:

```java
    /** Whether the Visual area an operator is about to act on is one an extension's object selected. */
    private boolean objectArea;
    /** The keys the running motion read ({@link VimCommands#motionWithKeys}). */
    private List<KeyInput> readKeys = List.of();
    /** Where the undo step's cursor is, when an extension's script changed the text before the operator did. */
    private Pos undoCursor;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
    /** A Normal-mode command or a motion after an operator, whose keys are complete: it reads what it needs, then runs. */
    private void start(VimCommands.Entry entry) {
        if (needsChar(entry)) {
            VimCommands.Entry waiting = entry;
```

with:

```java
    /** A Normal-mode command or a motion after an operator, whose keys are complete: it reads what it needs, then runs. */
    private void start(VimCommands.Entry entry) {
        if (vim.commands().keyReader(entry) != null) {
            readKeys(entry, new ArrayList<>(), false);
            return;
        }
        if (needsChar(entry)) {
            VimCommands.Entry waiting = entry;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
            if (vim.commands().visualPrefix(keys)) return;
            throw Context.fail();
        }
        if (needsChar(entry)) {
```

with:

```java
            if (vim.commands().visualPrefix(keys)) return;
            throw Context.fail();
        }
        if (vim.commands().keyReader(entry) != null) {
            readKeys(entry, new ArrayList<>(), true);
            return;
        }
        if (needsChar(entry)) {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
    }

    /** Whether the command reads a key as its argument; {@code q} does not while recording or while a register runs. */
    private boolean needsChar(VimCommands.Entry entry) {
```

with:

```java
    }

    /** A motion reads its keys one at a time until it has enough ({@link VimCommands#motionWithKeys}), then runs. */
    private void readKeys(VimCommands.Entry entry, List<KeyInput> got, boolean visual) {
        if (vim.commands().keyReader(entry).test(editor, got)) {
            readKeys = List.copyOf(got);
            if (visual) runVisual(entry, null);
            else run(entry, null);
            return;
        }
        argument = key -> {
            record.add(key);
            got.add(key);
            readKeys(entry, got, visual);
        };
    }

    /** Whether the command reads a key as its argument; {@code q} does not while recording or while a register runs. */
    private boolean needsChar(VimCommands.Entry entry) {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
        context.forced = forced;
        context.line = line;
        return context;
    }
```

with:

```java
        context.forced = forced;
        context.line = line;
        context.keys = readKeys;
        return context;
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java`, replace:

```java
        suspendedChange = false;
        undoCursor = null;
        shown.clear();
        if (!recordingInsert) record.clear();
```

with:

```java
        suspendedChange = false;
        undoCursor = null;
        readKeys = List.of();
        shown.clear();
        if (!recordingInsert) record.clear();
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, replace:

```java
        Argtextobj.register(commands);
        Matchit.register(commands);
        return commands;
    }
```

with:

```java
        Argtextobj.register(commands);
        Matchit.register(commands);
        Sneak.register(commands);
        return commands;
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java`, replace:

```java
        lineCommands.add(keys);
    }

    /** @return whether the entry reads a command line before it runs */
```

with:

```java
        lineCommands.add(keys);
    }

    /**
     * A motion that reads keys first (a ported plugin's getchar()), one at a time until {@code enough} says the keys
     * read so far are enough (asked before the first one too); they are in {@link Context#keys}. {@code <Esc>} as the
     * first key cancels it.
     */
    void motionWithKeys(String keys, String description, java.util.function.BiPredicate<VimEditor, java.util.List<KeyInput>> enough, Motion motion) {
        motion(keys, description, motion);
        keyReaders.put(keys, enough);
    }

    /** @return what decides when an entry has read enough keys, or null for an entry that reads none */
    java.util.function.BiPredicate<VimEditor, java.util.List<KeyInput>> keyReader(Entry entry) {
        return entry.kind() == Kind.MOTION ? keyReaders.get(entry.keys()) : null;
    }

    private final java.util.Map<String, java.util.function.BiPredicate<VimEditor, java.util.List<KeyInput>>> keyReaders = new java.util.HashMap<>();

    /** @return whether the entry reads a command line before it runs */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Context.java`, replace:

```java
    /** The key read as the command's argument ({@code f}'s character), or null. */
    KeyInput argument;
    /** The pending operator's keys ({@code d}, {@code gU}), or null in Normal mode. */
    String operator;
```

with:

```java
    /** The key read as the command's argument ({@code f}'s character), or null. */
    KeyInput argument;
    /** The keys a motion read before it ran ({@link VimCommands#motionWithKeys}); empty otherwise. */
    java.util.List<KeyInput> keys = java.util.List.of();
    /** The pending operator's keys ({@code d}, {@code gU}), or null in Normal mode. */
    String operator;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Motions.java`, replace:

```java
        commands.motion("ge", "back to the end of the previous word", c -> backEnd(c, false));
        commands.motion("gE", "back to the end of the previous WORD", c -> backEnd(c, true));
        commands.motionWithChar("f", "to the {count}-th {char} to the right", c -> find(c, true, false, false, false));
        commands.motionWithChar("F", "to the {count}-th {char} to the left", c -> find(c, false, false, false, false));
        commands.motionWithChar("t", "till before the {count}-th {char} to the right", c -> find(c, true, true, false, false));
        commands.motionWithChar("T", "till after the {count}-th {char} to the left", c -> find(c, false, true, false, false));
        commands.motion(";", "repeat the last f, F, t or T", c -> find(c, true, false, true, false));
        commands.motion(",", "repeat the last f, F, t or T the other way", c -> find(c, true, false, true, true));
```

with:

```java
        commands.motion("ge", "back to the end of the previous word", c -> backEnd(c, false));
        commands.motion("gE", "back to the end of the previous WORD", c -> backEnd(c, true));
        commands.motionWithChar("f", "to the {count}-th {char} to the right", c -> typedFind(c, true, false));
        commands.motionWithChar("F", "to the {count}-th {char} to the left", c -> typedFind(c, false, false));
        commands.motionWithChar("t", "till before the {count}-th {char} to the right", c -> typedFind(c, true, true));
        commands.motionWithChar("T", "till after the {count}-th {char} to the left", c -> typedFind(c, false, true));
        commands.motion(";", "repeat the last f, F, t or T", c -> find(c, true, false, true, false));
        commands.motion(",", "repeat the last f, F, t or T the other way", c -> find(c, true, false, true, true));
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Motions.java`, replace:

```java
        Pos p = c.cursor();
        return c.walk(bigword).bckendWord(p, c.count, false) ? Target.inclusive(p) : null;
    }
```

with:

```java
        Pos p = c.cursor();
        return c.walk(bigword).bckendWord(p, c.count, false) ? Target.inclusive(p) : null;
    }

    /** f, F, t and T typed: with sneak on, they take {@code ;} and {@code ,} back from it. */
    private static Target typedFind(Context c, boolean forward, boolean till) {
        Sneak.findTyped(c);
        return find(c, forward, till, false, false);
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`, replace:

```java
        new Def("sidescrolloff", "siso", Type.NUMBER, 0, false),
        new Def("smartcase", "scs", Type.BOOLEAN, false, false),
        new Def("softtabstop", "sts", Type.NUMBER, 0, true),
        new Def("startofline", "sol", Type.BOOLEAN, true, false),
```

with:

```java
        new Def("sidescrolloff", "siso", Type.NUMBER, 0, false),
        new Def("smartcase", "scs", Type.BOOLEAN, false, false),
        new Def("sneak", "sneak", Type.BOOLEAN, false, false),
        new Def("softtabstop", "sts", Type.NUMBER, 0, true),
        new Def("startofline", "sol", Type.BOOLEAN, true, false),
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, replace:

```java
    /**
     * How a plugin checks before it maps: not at all, hasmapto() once for all its mappings or for each, or with
     * {@code <unique>}, which leaves out a key the user mapped already in that mode.
     */
    enum Check { NONE, ALL, EACH, UNIQUE }

    private static String[] map(String command, String lhs, String rhs) { return new String[] { command, lhs, rhs }; }

    /** Every extension Jasper has. */
```

with:

```java
    /**
     * How a plugin checks before it maps: not at all, hasmapto() once for all its mappings or for each, or with
     * {@code <unique>}, which leaves out a key the user mapped already in that mode, or with hasmapto() and
     * mapcheck(), which also leaves out a key that starts a mapping in that mode or that a mapping starts with (a
     * mapping marked {@code always} is made anyway).
     */
    enum Check { NONE, ALL, EACH, UNIQUE, MAPCHECK }

    private static String[] map(String command, String lhs, String rhs) { return new String[] { command, lhs, rhs }; }

    private static String[] always(String command, String lhs, String rhs) { return new String[] { command, lhs, rhs, "always" }; }

    /** Every extension Jasper has. */
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, replace:

```java
            map("xmap", "[%", "<Plug>(MatchitVisualMultiBackward)"), map("xmap", "]%", "<Plug>(MatchitVisualMultiForward)"),
            map("omap", "[%", "<Plug>(MatchitOperationMultiBackward)"), map("omap", "]%", "<Plug>(MatchitOperationMultiForward)"),
            map("xmap", "a%", "<Plug>(MatchitVisualTextObject)"))));

    private final Vim vim;
```

with:

```java
            map("xmap", "[%", "<Plug>(MatchitVisualMultiBackward)"), map("xmap", "]%", "<Plug>(MatchitVisualMultiForward)"),
            map("omap", "[%", "<Plug>(MatchitOperationMultiBackward)"), map("omap", "]%", "<Plug>(MatchitOperationMultiForward)"),
            map("xmap", "a%", "<Plug>(MatchitVisualTextObject)"))),
        new Extension("sneak", "justinmk/vim-sneak", null, Check.MAPCHECK, List.of(
            map("nmap", "s", "<Plug>Sneak_s"), map("nmap", "S", "<Plug>Sneak_S"), map("omap", "z", "<Plug>Sneak_s"),
            map("omap", "Z", "<Plug>Sneak_S"), map("nmap", ";", "<Plug>Sneak_;"), map("omap", ";", "<Plug>Sneak_;"),
            map("xmap", ";", "<Plug>Sneak_;"), map("nmap", ",", "<Plug>Sneak_,"), map("omap", ",", "<Plug>Sneak_,"),
            map("xmap", ",", "<Plug>Sneak_,"), map("xmap", "s", "<Plug>Sneak_s"), map("xmap", "Z", "<Plug>Sneak_S"),
            always("nmap", "<Plug>SneakForward", "<Plug>Sneak_s"), always("nmap", "<Plug>SneakBackward", "<Plug>Sneak_S"),
            always("xmap", "<Plug>VSneakForward", "<Plug>Sneak_s"), always("xmap", "<Plug>VSneakBackward", "<Plug>Sneak_S"),
            always("xmap", "<Plug>VSneakNext", "<Plug>Sneak_;"), always("xmap", "<Plug>VSneakPrevious", "<Plug>Sneak_,"),
            always("map", "<Plug>SneakNext", "<Plug>Sneak_;"), always("map", "<Plug>SneakPrevious", "<Plug>Sneak_,"))));

    private final Vim vim;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java`, replace:

```java
            if (extension.check() == Check.UNIQUE && mappings.all().stream().anyMatch(existing -> (existing.modes & modes) != 0 && existing.lhs.equals(lhs)))
                continue;
            made.add(ExMaps.define(mappings, modes, lhs, rhs, false, false));
        }
        return made;
    }
```

with:

```java
            if (extension.check() == Check.UNIQUE && mappings.all().stream().anyMatch(existing -> (existing.modes & modes) != 0 && existing.lhs.equals(lhs)))
                continue;
            if (extension.check() == Check.MAPCHECK && !(m.length > 3 && m[3].equals("always")) && mappings.all().stream().anyMatch(existing ->
                (existing.modes & modes) != 0 && (contains(existing.rhs, rhs) || startsWith(existing.lhs, lhs) || startsWith(lhs, existing.lhs))))
                continue;
            made.add(ExMaps.define(mappings, modes, lhs, rhs, false, false));
        }
        return made;
    }

    private static boolean startsWith(List<KeyInput> keys, List<KeyInput> prefix) {
        return keys.size() >= prefix.size() && keys.subList(0, prefix.size()).equals(prefix);
    }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    /** Whether the tab's 'matchpairs' has pairs its language added. */
    private boolean languagePairs;
    /** The highlightedyank extension's highlight of the last yank. */
    private final HighlightedYank highlightedYank = new HighlightedYank(this);
```

with:

```java
    /** Whether the tab's 'matchpairs' has pairs its language added. */
    private boolean languagePairs;
    /** The sneak extension's matches, drawn while the cursor stays where the sneak left it. */
    private List<int[]> sneakRanges = List.of();
    private Pos sneakAt;
    /** The highlightedyank extension's highlight of the last yank. */
    private final HighlightedYank highlightedYank = new HighlightedYank(this);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    HighlightedYank highlightedYank() { return highlightedYank; }

    /** @return a number that changes with every change to the text (Vim's b:changedtick) */
    int textVersion() { return textVersion; }
```

with:

```java
    HighlightedYank highlightedYank() { return highlightedYank; }

    /** @param ranges the sneak's matches as {kind 3, start, end}, drawn until the cursor leaves {@code at} */
    void sneakHighlight(List<int[]> ranges, Pos at) {
        sneakRanges = ranges;
        sneakAt = at.copy();
    }

    /** @return a number that changes with every change to the text (Vim's b:changedtick) */
    int textVersion() { return textVersion; }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
        ranges.addAll(Exchange.highlight(this, exchange));
        ranges.addAll(highlightedYank.ranges());
        ranges.addAll(Highlights.visual(this));
        if (incsearch != null) ranges.add(new int[] { 0, incsearch[0], incsearch[1] });
```

with:

```java
        ranges.addAll(Exchange.highlight(this, exchange));
        ranges.addAll(highlightedYank.ranges());
        if (!sneakRanges.isEmpty() && (!cursor().equals(sneakAt) || mode.inserting())) sneakRanges = List.of();
        ranges.addAll(sneakRanges);
        ranges.addAll(Highlights.visual(this));
        if (incsearch != null) ranges.add(new int[] { 0, incsearch[0], incsearch[1] });
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Highlights.java`, replace:

```java
    private List<int[]> shown = List.of();
    private java.awt.Color yankColor;

    Highlights(VimTextArea area) { this.area = area; }
```

with:

```java
    private List<int[]> shown = List.of();
    private java.awt.Color yankColor;
    /** The sneak plugin's Sneak highlight: magenta, light enough to read the text through. */
    private static final java.awt.Color SNEAK = new java.awt.Color(255, 0, 255, 110);

    Highlights(VimTextArea area) { this.area = area; }
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Highlights.java`, replace:

```java
    /**
     * Draws the ranges ({@code [kind, start, end)} with kind 0 for Visual and incsearch, 1 for 'hlsearch' and 2 for
     * a yank's highlight, in {@code yank} or else the selection colour).
     */
    void show(List<int[]> ranges, java.awt.Color yank) {
```

with:

```java
    /**
     * Draws the ranges ({@code [kind, start, end)} with kind 0 for Visual and incsearch, 1 for 'hlsearch' and 2 for
     * a yank's highlight, in {@code yank} or else the selection colour, 3 for sneak's matches).
     */
    void show(List<int[]> ranges, java.awt.Color yank) {
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Highlights.java`, replace:

```java
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

with:

```java
        var search = new SmartHighlightPainter(area.getMarkAllHighlightColor());
        var yanked = yank == null ? selection : new SmartHighlightPainter(yank);
        var sneak = new SmartHighlightPainter(SNEAK);
        int length = area.getDocument().getLength();
        for (int[] range : ranges) {
            int start = Math.min(range[1], length), end = Math.min(range[2], length);
            if (end <= start) continue;
            try { tags.add(highlighter.addHighlight(start, end, range[0] == 0 ? selection : range[0] == 1 ? search : range[0] == 2 ? yanked : sneak)); }
            catch (BadLocationException ignored) { }
        }
```

- [ ] **Step 4: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains sneak's eight commands.

- [ ] **Step 5: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExtensionsTest'
```

Expected: 14 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.vim.TypeaheadTest' --tests 'dev.jasper.editor.vim.ExMapsTest' --tests 'dev.jasper.editor.vim.MacrosTest' --tests 'dev.jasper.editor.vim.KeyCodesTest' --tests 'dev.jasper.editor.vim.VimrcTest' --tests 'dev.jasper.editor.vim.ExtensionsTest' --tests 'dev.jasper.editor.vim.HighlightedYankTest' --tests 'dev.jasper.editor.vim.VimStringsTest' --tests 'dev.jasper.editor.vim.SearchPairTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 246 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 4401 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`: the Editor compiles without warnings (`-Xlint:all`).

- [ ] **Step 6: Check the recording (needs `vim`, `git` and the network)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: `git status` lists only `ext-sneak.case` (`??`), unchanged from what you created.

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/vim-v4d
git add plugins/editor/src/main/java/dev/jasper/editor/vim/Sneak.java plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java plugins/editor/src/main/java/dev/jasper/editor/vim/Context.java plugins/editor/src/main/java/dev/jasper/editor/vim/Motions.java plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java plugins/editor/src/main/java/dev/jasper/editor/vim/Extensions.java plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java plugins/editor/src/main/java/dev/jasper/editor/vim/Highlights.java plugins/editor/src/test/java/dev/jasper/editor/vim/ExtensionsTest.java plugins/editor/src/test/resources/vim/corpus/ext-sneak.case plugins/editor/vim-oracle.properties docs/editor-vim-reference.md
git commit -m "Add the sneak extension

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: Documentation, the spec amendments and the final check

**Files:**
- Modify: `docs/vim-divergences.md`, `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec

- [ ] **Step 1: List the differences**

Append to `docs/vim-divergences.md`:

```markdown
- **sneak** has no label mode (`<Plug>SneakLabel_s`, `g:sneak#label`), no `g:sneak#prompt` and no not-found message.
  `.` repeats the keys rather than vim-repeat's `<Plug>SneakRepeat`, with the same result. A letter finds the letters
  whose Unicode decomposition starts with it (`e` finds `é`), where Vim has its own table. Its default mappings check
  each mode on its own, and `,` does not move to `\` when you have mapped `,`.
```

- [ ] **Step 2: Describe sneak in the Editor guide**

In `docs/editor.md`, replace:

```markdown
- Not yet: sneak. It follows in a later release.
```

with:

```markdown
- `set sneak` (opt-in, as it takes over `s` and `S`): `s` and two characters goes to the next place they stand together,
  `S` back; after an operator `z` and `Z` do the same (`dzab`); `;` and `,` repeat it, and after `f` or `t` they are
  Vim's again. A count keeps `s` to the columns near the cursor (`5sab`). The matches ahead are drawn until the
  cursor moves on. Map `f` and `t` to `<Plug>Sneak_f` and `<Plug>Sneak_t` for one-character sneaks that `;` repeats.
```

- [ ] **Step 3: Add the GUI checks for the user**

Append to `docs/editor-verification.md`:

```markdown

## Vim mode (plan V4d)

- [ ] 110. With `set sneak` in the vimrc, `s` and two characters jumps to them, the other matches ahead are drawn in magenta, and `l` clears them.
- [ ] 111. `;` goes on to the next match, `,` back; after `fx`, `;` repeats the `f` again.
- [ ] 112. `dzab` deletes up to `ab`; `.` repeats it at the next `ab`.
- [ ] 113. `v` then `sab` extends the Visual area to `ab`.
- [ ] 114. `3sab` only stops at an `ab` within about three columns of the cursor.
```

- [ ] **Step 4: Record the state**

In `docs/STATUS.md`, replace:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V4c** (branch `claude/vim-v4c`, plan
```

with:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V4d** (branch `claude/vim-v4d`, plan
  `docs/superpowers/plans/2026-09-29-jasper-editor-vim-v4d.md`, written 2026-09-29): sneak, the last of spec 8's extensions (`s`, `S`, `z`,
  `Z`, `;`, `,`, vertical scope, the one-character `<Plug>Sneak_f` forms; no label mode), on motions that read their
  keys first (`VimCommands.motionWithKeys`). Every file in the plan was compiled and run against Vim 9.1 with the
  pinned sneak before the plan was written: 2211 corpus cases (47 new), with and without a final line break.
  - Not done: executing the plan; GUI checks 110–114 of the [checklist](editor-verification.md) (user-run); push.
- **Editor Vim mode, plan V4c** (branch `claude/vim-v4c`, plan
```

- [ ] **Step 5: Amend the spec**

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
Plan V4c (`docs/superpowers/plans/2026-09-29-jasper-editor-vim-v4c.md`) amends sections 3.7, 8, 11.1 and 13, as marked **Amended (V4c)**.
```

with:

```markdown
Plan V4c (`docs/superpowers/plans/2026-09-29-jasper-editor-vim-v4c.md`) amends sections 3.7, 8, 11.1 and 13, as marked **Amended (V4c)**. Plan V4d (`docs/superpowers/plans/2026-09-29-jasper-editor-vim-v4d.md`) amends sections 8 and 13, as marked **Amended (V4d)**.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
**Amended (V4c):** plan V4c delivers matchit (`set matchit`, `packadd matchit`, `Plug 'chrisbra/matchit'`), ported from Vim 9.1's matchit.vim with its `<Plug>(Matchit…)` names and default mappings; sneak comes with V4d. The "language table in the plan" is `matchit-languages.tsv`, what Vim 9.1's filetype plugins set for each RSyntaxTextArea style they know (`b:match_words`, `b:match_ignorecase`, `b:match_skip`, and the pairs they add to 'matchpairs', which Jasper adds too); a skip naming syntax groups reads the tab's tokens, another expression skips comments and strings. `:MatchEnable`, `:MatchDisable` and `:MatchDebug` are not there.
```

with:

```markdown
**Amended (V4c):** plan V4c delivers matchit (`set matchit`, `packadd matchit`, `Plug 'chrisbra/matchit'`), ported from Vim 9.1's matchit.vim with its `<Plug>(Matchit…)` names and default mappings; sneak comes with V4d. The "language table in the plan" is `matchit-languages.tsv`, what Vim 9.1's filetype plugins set for each RSyntaxTextArea style they know (`b:match_words`, `b:match_ignorecase`, `b:match_skip`, and the pairs they add to 'matchpairs', which Jasper adds too); a skip naming syntax groups reads the tab's tokens, another expression skips comments and strings. `:MatchEnable`, `:MatchDisable` and `:MatchDebug` are not there.

**Amended (V4d):** plan V4d delivers sneak (`set sneak`, `Plug 'justinmk/vim-sneak'`), ported from vim-sneak with its `<Plug>Sneak_…` names and its default mappings (`s`, `S`; `z`, `Z` after an operator; `;`, `,`; Visual `s`, `Z`), `g:sneak#s_next`, `g:sneak#use_ic_scs` and `g:sneak#absolute_dir`. There is no label mode (`g:sneak#label`, `<Plug>SneakLabel_s`) and no `g:sneak#prompt`; `.` repeats the keys typed; a letter's equivalence class is the letters whose decomposition starts with it.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
 **Amended (V4c):** split again by the user on 2026-09-29: plan V4c is matchit, plan V4d sneak.
```

with:

```markdown
 **Amended (V4c):** split again by the user on 2026-09-29: plan V4c is matchit, plan V4d sneak. **Amended (V4d):** with V4d every extension of section 8 is there.
```

- [ ] **Step 6: Run everything**

Run:

```bash
./gradlew check
```

Expected: BUILD SUCCESSFUL (7,147 tests).

`TerminalAppIntegrationTest`'s "reflow" case and the Remote plugin's `ConnectionsTest.callbacksUseUiAndJumpReleasesAfterLastShell` are known to fail now and then under the full check, unrelated to the Editor; if one does, run `./gradlew check` again.

- [ ] **Step 7: Check source hygiene**

Run the snippet in `AGENTS.md` ("Source hygiene"). Expected: no output.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v4d
git add docs
git commit -m "Document Vim mode V4d: sneak

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
