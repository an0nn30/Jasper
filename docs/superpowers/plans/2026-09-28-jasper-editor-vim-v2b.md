# Editor Vim mode, plan V2b: files, buffers and tabs — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The rest of spec section 6 for the Editor's Vim mode: the file, buffer and tab commands (`:w`, `:q`, `:wq`, `:x`, `:e`, `:r`, `:ls`, `:b`, `:bn`, `:tabn`, `ZZ`, `ZQ`, `gt`, `gT` and their kin) through the Editor's own save, open and close; `<Tab>` completion on the command line; 'number' and 'relativenumber' on the gutter; and uppercase marks that reach across tabs.

**Architecture:** `VimHost` gains `Documents`, a record of callbacks through which the engine sees the Editor's tabs as numbered buffers (`Buffer`) and asks the Editor to save, save as, close, select, reload, open and read. `ExFiles` implements the commands on it, porting Vim's messages and errors (`"a.txt" 2L, 8B written`, E13, E37, E162, E484, E86, E93, E94); `EditorWindow` implements `Documents` with its existing save, conflict and close paths. `Completion` ports Vim's command-line completion contexts for the commands Jasper has, and `CommandLine` cycles through the matches as Vim's 'wildmode' "full" does without 'wildmenu'. `LineNumbers` drives RSyntaxTextArea's gutter through its `LineNumberFormatter`.

**Tech Stack:** Java 25 (JBR), Swing, RSyntaxTextArea 3.6.0, JUnit 6 and AssertJ, Vim 9 (only for `recordVimCorpus`).

**Spec:** `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md` (plan V2b of section 13 as amended by V2a; sections 3.6, 4.1, 5.4, 6.1 and 6.2). Plans V1a–V2a are merged; their headers describe the engine and the Ex command line.

**Status:** Written 2026-09-28 on branch `claude/vim-v2b` (worktree `.worktrees/vim-v2b`, from `main` at e62b4763). Every source file, test and corpus case below was compiled and run before the plan was written: against Vim 9.1 all 1582 corpus cases (17 new, in `ex-complete.case`) passed, and the file, buffer and tab commands, which need the Editor's tabs behind Vim, all run in `ExFilesTest` and `EditorWindowTest`. Applied to a fresh checkout of `e62b4763` exactly as written, `./gradlew check` passed (4,135 tests, 0 failures, 0 errors, 5 skipped).

## Deviations from the spec (approved scope, adjusted in this plan)

Task 5 writes these into the spec as **Amended (V2b)**.

- **Tabs are the buffers.** `:e file` opens the file in a tab or selects its tab; `:w file` saves the tab as the file (spec 6.2 says so; Vim writes a copy); `:q` closes the tab and never quits Jasper; `:qa` closes the Editor's tabs. No command needs `!` to leave a tab with changes. `:w` on an untitled tab asks where to save, as the Editor's Save does. `:e newfile` opens an empty tab its first save creates. Relative names start from the current file's folder (the home folder for an untitled tab).
- **More commands than spec 6.2 lists**, all as in Vim: `:sav[eas]`, `:exi[t]`, `:quita[ll]`, `:files`, `:bN[ext]`, `:tabN[ext]`, `:tabe[dit]`, and the Normal keys `gt` and `gT`. `%`, `#` and `~` work in file names; `:2,5w file`, `:w >>`, `:w !cmd` and `:r !cmd` fail with E319.
- **Completion** (spec 6.1) covers the commands and options Jasper has, paths after the file commands and tab names after `:b`; where nothing completes `<Tab>` types a tab, as in Vim; `:source`'s paths come with V3.
- **'relativenumber'** (spec 5.4) is RSyntaxTextArea's own gutter with a line-number formatter, not a component of its own; the cursor line's number is right-aligned like the others.
- **Uppercase marks** (spec 3.6, amended in V1b): `'A` in another tab selects that tab; after an operator or in Visual mode it fails.
- **The corpus** cannot record the file, buffer and tab commands (the recorder's Vim has no Editor behind it); `ExFilesTest.NEEDS_TABS` lists them, `ExFilesTest` runs every one, and `everyCommandHasACase` leaves them to it.

## Global Constraints

- Java 25 on the JetBrains Runtime; always `./gradlew`, never a system Gradle. Work in `.worktrees/vim-v2b` on `claude/vim-v2b`; check `git branch --show-current` before every commit (other sessions switch branches in the main checkout). Never commit on `main`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.
- The Editor plugin compiles against the SDK only (`verifyPluginArchitecture`, a root task). `dev.jasper.editor.vim` may use `vim.regex`, RSyntaxTextArea and the JDK; `dev.jasper.editor.vim.ui` adds `dev.jasper.editor.vim`; `dev.jasper.editor.ui` may use both. No SDK change.
- No interface without two real implementations; this plan adds none (`VimHost.Documents` is a record of callbacks, as `VimHost` is).
- Source hygiene: never put raw control, private-use or unpaired surrogate characters in `.java` files; use escapes. No run of three backticks may appear in any file this plan embeds (the docs link check strips fenced blocks lazily).
- **Do not launch the GUI.** GUI checks go in `docs/editor-verification.md` for the user.
- Engine code runs on the event thread in the app. Tests that type through a `VimEditor` run inside `SwingUtilities.invokeAndWait` (the caret follows the text only there).
- The corpus is the authority on Vim behaviour; mark a case `divergent:` only for a documented, deliberate difference.
- `vim` must be on `PATH` for `recordVimCorpus` (the dev Mac has Vim 9.1 at `/usr/bin/vim`). `check` never runs Vim.

## File structure

New, `plugins/editor/src/main/java/dev/jasper/editor/vim/`:

| File | Responsibility |
|---|---|
| `ExFiles.java` | `:w :wq :x :exi :sav :wa :xa :wqa :q :qa :quita :e :ene :r :ls :buffers :files :b :bn :bN :bp :tabn :tabN :tabp :tabnew :tabe`, `gt`, `gT`; file names with `%`, `#`, `~` |
| `Completion.java` | what `<Tab>` completes: command names, options and values after `:set`, paths, tab names |
| `LineNumbers.java` | 'number' and 'relativenumber' on RSyntaxTextArea's gutter |

Changed there: `VimHost` (`Documents`, `Buffer`), `Vim` (`host()`), `VimEditor` (`afterKey`, `announce`, `cursorLine`, the line numbers), `Ex` (the commands, `ZZ`, `ZQ`, `gt`, `gT`, the completer), `MarkCommands` (uppercase marks in other tabs), `Options` ('number', 'relativenumber'), `CommandLine` (`<Tab>` and `<S-Tab>`).
Changed: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java` (buffer numbers, the alternate tab, `Documents`, `:e newfile`, `:q!`).
Tests: `vim/ExFilesTest.java`, `vim/CompletionTest.java`, `vim/LineNumbersTest.java` (new); `VimCorpusTest`, `ui/EditorWindowTest`. Corpus: `ex-complete.case`.
Docs: `docs/vim-divergences.md`, `docs/editor-vim-reference.md` (generated), `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec.

---

### Task 1: Tabs as buffers: the file, buffer and tab commands

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/ExFiles.java`
- Replace in `plugins/editor/src/main/java/dev/jasper/editor/vim/`: `VimHost.java`, `Vim.java`, `VimEditor.java`, `Ex.java`, `MarkCommands.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/ExFilesTest.java` (new); modify `plugins/editor/src/test/java/dev/jasper/editor/vim/VimCorpusTest.java`
- Regenerate: `docs/editor-vim-reference.md`

**Interfaces:**
- Consumes: plan V2a's `Ex`, `ExCommand`, `VimEditor.say`/`message`, the command table.
- Produces (used by Tasks 2–4): public `VimHost.Documents(buffers, save, saveAs, close, select, reload, open, read)` and `VimHost.Buffer(number, name, file, current, alternate, modified, untitled, line)`, `VimHost.Documents.NONE`, the three-argument `VimHost` constructor (the two-argument one stays, with no tabs); package-private `Vim.host()`, `VimEditor.afterKey(Runnable)`, `VimEditor.announce(String)`, `VimEditor.cursorLine()`; `Ex.skipRange(String, int)` (package-private now); `ExFiles.folder(Buffer)` and `ExFiles.resolve(String, Buffer)`; `ExFilesTest.NEEDS_TABS`.

The engine names a tab by its buffer number and always passes the editor the command runs in, so the Editor can tell which tab is current. A save completes later: `ExFiles` gives the write message (and, for `:wq`, `:x` and `ZZ`, closes the tab) when the future completes, through `VimEditor.announce`. Closing runs after the key is done (`VimEditor.afterKey`), so the editor that closes is not mid-command. An uppercase mark whose editor is another tab's selects that tab and moves its cursor there (`MarkCommands.elsewhere`); the current command then ends quietly.

- [ ] **Step 1: Write the failing test**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/ExFilesTest.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.VimHost.Buffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The file, buffer and tab commands against a fake set of tabs. The corpus cannot check them (the recorder's Vim has
 * no Editor behind it), so every one runs here, and {@link VimCorpusTest} leaves them to this class.
 */
class ExFilesTest {
    @TempDir Path folder;

    /** The commands whose cases need the Editor's tabs, which this class checks instead of the corpus. */
    static final Set<String> NEEDS_TABS = Set.of("action ZZ", "action ZQ", "action gt", "action gT", "ex write", "ex wq", "ex xit",
        "ex exit", "ex saveas", "ex wall", "ex xall", "ex wqall", "ex quit", "ex qall", "ex quitall", "ex edit", "ex enew", "ex read", "ex ls",
        "ex buffers", "ex files", "ex buffer", "ex bnext", "ex bNext", "ex bprevious", "ex tabnext", "ex tabNext", "ex tabprevious", "ex tabnew",
        "ex tabedit");

    /** A fake Editor: three tabs, the first being the editor under test; it records what Vim asks of it. */
    private final class Tabs {
        final List<String> calls = new ArrayList<>();
        final List<Buffer> others = new ArrayList<>();
        Optional<Path> file = Optional.empty();
        String name = "[No Name]";
        boolean untitled = true;
        boolean saves = true;
        int current = 1;

        VimHost.Documents documents() {
            return new VimHost.Documents(this::buffers,
                number -> result("save " + number),
                (number, target) -> {
                    var done = result("saveAs " + number + " " + folder.relativize(target));
                    if (saves) {
                        file = Optional.of(target);
                        name = target.toString();
                        untitled = false;
                    }
                    return done;
                },
                (number, discard) -> calls.add("close " + number + (discard ? " discard" : "")),
                number -> calls.add("select " + number),
                number -> calls.add("reload " + number),
                (editor, target) -> calls.add("open " + target.map(p -> folder.relativize(p).toString()).orElse("new")),
                path -> {
                    try { return Optional.of(Files.readString(path)); }
                    catch (java.io.IOException missing) { return Optional.empty(); }
                });
        }

        private CompletableFuture<Boolean> result(String call) {
            calls.add(call);
            return CompletableFuture.completedFuture(saves);
        }

        private List<Buffer> buffers(VimEditor editor) {
            List<Buffer> all = new ArrayList<>();
            all.add(new Buffer(1, name, file, current == 1, false, editor.documentModified(), untitled, editor.cursorLine()));
            for (Buffer other : others) all.add(new Buffer(other.number(), other.name(), other.file(), current == other.number(),
                other.alternate(), other.modified(), other.untitled(), other.line()));
            return all;
        }
    }

    private final Tabs tabs = new Tabs();
    private final Set<String> covered = new TreeSet<>();
    private boolean modified;

    private VimEditor editor(VimTextArea area, String text) {
        var vim = new Vim(new VimHost(r -> Optional.empty(), (r, t) -> { }, tabs.documents()));
        vim.coverage(covered::add);
        area.setText(text);
        area.discardAllEdits();
        area.setCaretPosition(0);
        var editor = new VimEditor(vim, area, EditorDefaults.VIM);
        editor.documentInfo(() -> tabs.name, () -> modified);
        return editor;
    }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private void named(String name) {
        tabs.file = Optional.of(folder.resolve(name));
        tabs.name = folder.resolve(name).toString();
        tabs.untitled = false;
    }

    private void others() {
        tabs.others.add(new Buffer(2, folder.resolve("two.txt").toString(), Optional.of(folder.resolve("two.txt")), false, true, false, false, 4));
        tabs.others.add(new Buffer(3, "Untitled 1", Optional.empty(), false, false, true, true, 1));
    }

    @Test void writeSavesAndSaysWhatItWrote() throws Exception {
        named("one.txt");
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "a\nbé\n");
            type(editor, ":w<CR>");
            assertThat(tabs.calls).containsExactly("save 1");
            assertThat(editor.message()).isEqualTo("\"" + folder.resolve("one.txt") + "\" 2L, 6B written");
            type(editor, ":w %<CR>");
            assertThat(tabs.calls).as("% is the current file").containsExactly("save 1", "save 1");
            tabs.saves = false;
            type(editor, ":w<CR>");
            assertThat(editor.message()).as("a save that failed says nothing; the Editor reported it").isEmpty();
        });
    }

    @Test void writeToAnotherFileSavesAsItUnlessItExists() throws Exception {
        named("one.txt");
        Files.writeString(folder.resolve("taken.txt"), "x");
        others();
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "a\nb\n");
            type(editor, ":w taken.txt<CR>");
            assertThat(editor.message()).isEqualTo("E13: File exists (add ! to override)");
            type(editor, ":w two.txt<CR>");
            assertThat(editor.message()).isEqualTo("E139: File is loaded in another buffer");
            type(editor, ":w new\\ file.txt<CR>");
            assertThat(tabs.calls).containsExactly("saveAs 1 new file.txt");
            assertThat(editor.message()).isEqualTo("\"new file.txt\" [New] 2L, 4B written");
            type(editor, ":w! taken.txt<CR>");
            assertThat(tabs.calls).last().isEqualTo("saveAs 1 taken.txt");
            type(editor, ":sav other.txt<CR>");
            assertThat(tabs.calls).last().isEqualTo("saveAs 1 other.txt");
            type(editor, ":sav<CR>");
            assertThat(editor.message()).isEqualTo("E471: Argument required");
            type(editor, ":w a b<CR>");
            assertThat(editor.message()).isEqualTo("E172: Only one file name allowed");
            type(editor, ":w #<CR>");
            assertThat(editor.message()).as("# is the alternate file, open in tab 2").isEqualTo("E139: File is loaded in another buffer");
            type(editor, ":1w part.txt<CR>");
            assertThat(editor.message()).as("a part of the text").isEqualTo("E319: Sorry, the command is not available in this version");
        });
    }

    @Test void quitClosesTheTabUnlessItHasChanges() throws Exception {
        named("one.txt");
        others();
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "a\n");
            modified = true;
            type(editor, ":q<CR>");
            assertThat(editor.message()).isEqualTo("E37: No write since last change (add ! to override)");
            assertThat(tabs.calls).isEmpty();
            type(editor, ":q!<CR>");
            assertThat(tabs.calls).containsExactly("close 1 discard");
            modified = false;
            type(editor, ":q<CR>");
            assertThat(tabs.calls).last().isEqualTo("close 1");
            type(editor, ":qa<CR>");
            assertThat(editor.message()).as("tab 3 has changes").isEqualTo("E162: No write since last change for buffer \"Untitled 1\"");
            tabs.calls.clear();
            type(editor, ":qa!<CR>");
            assertThat(tabs.calls).containsExactly("close 1 discard", "close 2 discard", "close 3 discard");
            tabs.calls.clear();
            type(editor, "ZQ");
            assertThat(tabs.calls).containsExactly("close 1 discard");
        });
    }

    @Test void writeQuitAndExitCloseAfterASuccessfulSave() throws Exception {
        named("one.txt");
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "a\n");
            type(editor, ":wq<CR>");
            assertThat(tabs.calls).containsExactly("save 1", "close 1");
            tabs.calls.clear();
            type(editor, ":x<CR>");
            assertThat(tabs.calls).as("no changes: no save").containsExactly("close 1");
            tabs.calls.clear();
            modified = true;
            type(editor, "ZZ");
            assertThat(tabs.calls).containsExactly("save 1", "close 1");
            tabs.calls.clear();
            tabs.saves = false;
            type(editor, ":wq<CR>");
            assertThat(tabs.calls).as("a failed save keeps the tab").containsExactly("save 1");
            tabs.saves = true;
            tabs.calls.clear();
            others();
            type(editor, ":wa<CR>");
            assertThat(tabs.calls).as("the tabs with changes").containsExactly("save 1", "save 3");
            tabs.calls.clear();
            type(editor, ":xa<CR>");
            assertThat(tabs.calls).containsExactly("save 1", "save 3", "close 1", "close 2", "close 3");
            tabs.calls.clear();
            type(editor, ":wqa<CR>");
            assertThat(tabs.calls).containsExactly("save 1", "save 3", "close 1", "close 2", "close 3");
            tabs.calls.clear();
            type(editor, ":exi<CR>");
            assertThat(tabs.calls).containsExactly("save 1", "close 1");
        });
    }

    @Test void editOpensAFileOrReadsTheTabsFileAgain() throws Exception {
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "a\n");
            type(editor, ":e<CR>");
            assertThat(editor.message()).isEqualTo("E32: No file name");
            type(editor, ":e ~<CR>");
            assertThat(tabs.calls).containsExactly("open " + folder.relativize(Path.of(System.getProperty("user.home"))));
        });
        tabs.calls.clear();
        named("one.txt");
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "a\n");
            type(editor, ":e<CR>");
            assertThat(tabs.calls).containsExactly("reload 1");
            modified = true;
            type(editor, ":e<CR>");
            assertThat(editor.message()).isEqualTo("E37: No write since last change (add ! to override)");
            type(editor, ":e!<CR>");
            assertThat(tabs.calls).containsExactly("reload 1", "reload 1");
            type(editor, ":e sub/other.txt<CR>");
            assertThat(tabs.calls).as("relative to the current file's folder").last().isEqualTo("open sub/other.txt");
            type(editor, ":e one.txt<CR>");
            assertThat(editor.message()).as("the current file: read again, which needs !").isEqualTo("E37: No write since last change (add ! to override)");
            type(editor, ":ene<CR>:tabnew<CR>:tabe x.txt<CR>:tabnew y.txt<CR>");
            assertThat(tabs.calls).containsSubsequence("open new", "open new", "open x.txt", "open y.txt");
        });
    }

    @Test void readPutsAFileBelowTheLine() throws Exception {
        Files.writeString(folder.resolve("r.txt"), "  r1\nr2\n");
        named("one.txt");
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "one\ntwo\n");
            type(editor, ":r r.txt<CR>");
            assertThat(area.getText()).isEqualTo("one\n  r1\nr2\ntwo\n");
            assertThat(editor.cursor()).as("the first new line's first non-blank").isEqualTo(new Pos(1, 2));
            assertThat(editor.marks().get('[').line).isEqualTo(1);
            assertThat(editor.marks().get(']').line).isEqualTo(2);
            assertThat(editor.message()).isEqualTo("\"r.txt\" 2L, 8B");
            type(editor, ":0r r.txt<CR>");
            assertThat(area.getText()).startsWith("  r1\nr2\none\n");
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 2));
            type(editor, "u");
            assertThat(area.getText()).isEqualTo("one\n  r1\nr2\ntwo\n");
            type(editor, ":r nosuch<CR>");
            assertThat(editor.message()).isEqualTo("E484: Can't open file nosuch");
            type(editor, ":r !ls<CR>");
            assertThat(editor.message()).isEqualTo("E319: Sorry, the command is not available in this version");
        });
    }

    @Test void listsTheTabsAsBuffers() throws Exception {
        named("one.txt");
        others();
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "a\nb\n");
            type(editor, "j:ls<CR>");
            String one = folder.resolve("one.txt").toString(), two = folder.resolve("two.txt").toString();
            assertThat(editor.output()).extracting(VimEditor.OutputLine::text).containsExactly(
                String.format("  1 %%a   \"%s\"", one) + " ".repeat(Math.max(1, 40 - 10 - one.length())) + "line 2",
                String.format("  2 #a   \"%s\"", two) + " ".repeat(Math.max(1, 40 - 10 - two.length())) + "line 4",
                "  3  a + \"Untitled 1\"" + " ".repeat(19) + "line 1");
            type(editor, "<CR>:buffers<CR><CR>:files<CR>");
            assertThat(editor.output()).hasSize(3);
        });
    }

    @Test void bufferSelectsATabByNumberOrName() throws Exception {
        named("one.txt");
        others();
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "a\n");
            type(editor, ":b 2<CR>:3b<CR>:b two<CR>:b Untitled<CR>:b<CR>");
            assertThat(tabs.calls).containsExactly("select 2", "select 3", "select 2", "select 3");
            type(editor, ":b 9<CR>");
            assertThat(editor.message()).isEqualTo("E86: Buffer 9 does not exist");
            type(editor, ":b zz<CR>");
            assertThat(editor.message()).isEqualTo("E94: No matching buffer for zz");
            type(editor, ":b t<CR>");
            assertThat(editor.message()).as("one.txt, two.txt and Untitled 1 all hold a t").isEqualTo("E93: More than one match for t");
        });
    }

    @Test void theNextAndPreviousTabWrapAround() throws Exception {
        named("one.txt");
        others();
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "a\n");
            type(editor, ":bn<CR>:bp<CR>:2bn<CR>:bN<CR>");
            assertThat(tabs.calls).containsExactly("select 2", "select 3", "select 3", "select 3");
            tabs.calls.clear();
            type(editor, ":tabn<CR>:tabn 3<CR>:tabp<CR>:tabN 2<CR>:tabp 3<CR>");
            assertThat(tabs.calls).as("tab 1 is current, so :tabp 3 stays").containsExactly("select 2", "select 3", "select 3", "select 2");
            type(editor, ":tabn 9<CR>");
            assertThat(editor.bells()).isPositive();
            type(editor, ":tabn x<CR>");
            assertThat(editor.message()).isEqualTo("E475: Invalid argument: x");
            tabs.calls.clear();
            type(editor, "gt3gtgT2gT");
            assertThat(tabs.calls).containsExactly("select 2", "select 3", "select 3", "select 2");
            int bells = editor.bells();
            type(editor, "4gt");
            assertThat(editor.bells()).isEqualTo(bells + 1);
        });
    }

    @Test void everyCommandThatNeedsTabsRunsHere() throws Exception {
        Files.writeString(folder.resolve("r.txt"), "r\n");
        named("one.txt");
        others();
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "a\n");
            type(editor, ":w<CR>:wq<CR>:x<CR>:exi<CR>:sav s.txt<CR>:wa<CR>:xa<CR>:wqa<CR>:q<CR>:qa!<CR>:quita!<CR>:e<CR>:ene<CR>:r r.txt<CR>");
            type(editor, ":ls<CR><CR>:buffers<CR><CR>:files<CR><CR>:b 2<CR>:bn<CR>:bN<CR>:bp<CR>:tabn<CR>:tabN<CR>:tabp<CR>:tabnew<CR>:tabe x<CR>");
            type(editor, "ZZZQgtgT");
        });
        assertThat(covered).containsAll(NEEDS_TABS);
    }
}
```

In `plugins/editor/src/test/java/dev/jasper/editor/vim/VimCorpusTest.java`, replace:

```java
        List<String> missing = ids.stream().filter(id -> !used.contains(id)).toList();
```

with:

```java
        // The file, buffer and tab commands need the Editor's tabs behind Vim; ExFilesTest checks them.
        List<String> missing = ids.stream().filter(id -> !used.contains(id) && !ExFilesTest.NEEDS_TABS.contains(id)).toList();
```

- [ ] **Step 2: Run it to see it fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExFilesTest'
```

Expected: compilation fails: `VimHost.Documents` does not exist.

- [ ] **Step 3: Write `ExFiles` and replace the changed classes**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ExFiles.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.VimHost.Buffer;
import dev.jasper.editor.vim.VimHost.Documents;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The file, buffer and tab commands (spec 6.2), through the Editor ({@link VimHost.Documents}): each Editor tab is a
 * buffer and a tab page. {@code :w} and the other writes use the Editor's save, with its conflict checks and dialogs;
 * {@code :q} closes the tab; {@code :e} opens a file in a tab or selects its tab. A file name may use {@code %} (the
 * current file), {@code #} (the alternate), {@code ~} and backslash-escaped spaces; a relative name is taken from the
 * current file's folder, or the home folder for an untitled tab.
 */
final class ExFiles {
    private ExFiles() { }

    private static Documents documents(ExCommand cmd) { return cmd.editor.vim().host().documents(); }

    private static List<Buffer> buffers(ExCommand cmd) { return documents(cmd).buffers().apply(cmd.editor); }

    private static Buffer current(List<Buffer> buffers) {
        return buffers.stream().filter(Buffer::current).findFirst().orElseThrow(() -> new IllegalStateException("No current buffer"));
    }

    /** {@code :w [file]}: saves the tab, or saves it as the file. */
    static void write(ExCommand cmd) {
        if (partial(cmd)) throw new VimFailure("E319: Sorry, the command is not available in this version");
        write(cmd, () -> { });
    }

    /** {@code :wq [file]}: writes, then closes the tab once the write succeeded. */
    static void writeQuit(ExCommand cmd) {
        if (partial(cmd)) throw new VimFailure("E319: Sorry, the command is not available in this version");
        int number = current(buffers(cmd)).number();
        write(cmd, () -> documents(cmd).close().accept(number, false));
    }

    /** {@code :x} and {@code :exit}: as {@code :wq}, but writes only when there are changes or a file is named. */
    static void exit(ExCommand cmd) {
        if (partial(cmd)) throw new VimFailure("E319: Sorry, the command is not available in this version");
        Buffer buffer = current(buffers(cmd));
        String arg = fileArg(cmd);
        if (!buffer.modified() && arg == null) {
            cmd.editor.afterKey(() -> documents(cmd).close().accept(buffer.number(), false));
            return;
        }
        writeQuit(cmd);
    }

    /** A range that is not the whole text: Jasper writes whole files only. */
    private static boolean partial(ExCommand cmd) { return cmd.addrCount > 0 && (cmd.line1 != 1 || cmd.line2 != cmd.editor.buffer().count()); }

    private static void write(ExCommand cmd, Runnable then) {
        String arg = fileArg(cmd);
        if (arg != null && (arg.startsWith(">>") || arg.startsWith("!"))) throw new VimFailure("E319: Sorry, the command is not available in this version");
        List<Buffer> buffers = buffers(cmd);
        Buffer buffer = current(buffers);
        VimEditor editor = cmd.editor;
        String text = editor.buffer().text();
        if (arg == null || buffer.file().filter(f -> same(f, resolve(arg, buffer))).isPresent()) {
            done(editor, documents(cmd).save().apply(buffer.number()), () -> written(editor, buffer.name(), false, text), then);
            return;
        }
        Path file = resolve(arg, buffer);
        if (buffers.stream().anyMatch(other -> !other.current() && other.file().filter(f -> same(f, file)).isPresent()))
            throw new VimFailure("E139: File is loaded in another buffer");
        boolean exists = Files.exists(file);
        if (exists && !cmd.forceit) throw new VimFailure("E13: File exists (add ! to override)");
        if (Files.isDirectory(file)) throw new VimFailure("E502: \"" + arg + "\" is a directory");
        done(editor, documents(cmd).saveAs().apply(buffer.number(), file), () -> written(editor, arg, !exists, text), then);
    }

    /** When a save completes: the message, then what follows it, on the UI thread where the Editor completes it. */
    private static void done(VimEditor editor, CompletableFuture<Boolean> save, Runnable message, Runnable then) {
        save.thenAccept(saved -> {
            if (!saved) return;
            message.run();
            then.run();
        });
    }

    /** The write message: {@code "name" 3L, 12B written}, with {@code [New]} for a file that did not exist. */
    private static void written(VimEditor editor, String name, boolean created, String text) {
        editor.announce("\"" + name + "\" " + (created ? "[New] " : "") + size(text) + " written");
    }

    /** @return the text's size as Vim's messages give it: {@code 3L, 12B} (lines, UTF-8 bytes) */
    static String size(String text) {
        int lines = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') lines++;
        if (!text.isEmpty() && !text.endsWith("\n")) lines++;
        return lines + "L, " + text.getBytes(StandardCharsets.UTF_8).length + "B";
    }

    /** {@code :wa}: saves every tab with changes, one after another. */
    static void writeAll(ExCommand cmd) {
        saveAll(cmd, () -> { });
    }

    /** {@code :xa} and {@code :wqa}: saves every tab with changes, then closes them all. */
    static void exitAll(ExCommand cmd) {
        saveAll(cmd, () -> closeAll(cmd, false));
    }

    private static void saveAll(ExCommand cmd, Runnable then) {
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
        for (Buffer buffer : buffers(cmd)) {
            if (!buffer.modified()) continue;
            chain = chain.thenCompose(ok -> ok ? documents(cmd).save().apply(buffer.number()) : CompletableFuture.completedFuture(false));
        }
        chain.thenAccept(saved -> { if (saved) then.run(); });
    }

    /** {@code :q}: closes the tab; with changes only with {@code !}, which discards them. */
    static void quit(ExCommand cmd) {
        Buffer buffer = current(buffers(cmd));
        if (buffer.modified() && !cmd.forceit) throw new VimFailure("E37: No write since last change (add ! to override)");
        cmd.editor.afterKey(() -> documents(cmd).close().accept(buffer.number(), cmd.forceit));
    }

    /** {@code :qa}: closes every tab; with changes in any only with {@code !}. */
    static void quitAll(ExCommand cmd) {
        if (!cmd.forceit) {
            List<Buffer> buffers = buffers(cmd);
            Optional<Buffer> changed = buffers.stream().filter(b -> b.current() && b.modified()).findFirst()
                .or(() -> buffers.stream().filter(Buffer::modified).findFirst());
            if (changed.isPresent()) {
                cmd.editor.message("E37: No write since last change", true);
                throw new VimFailure("E162: No write since last change for buffer \"" + changed.get().name() + "\"");
            }
        }
        cmd.editor.afterKey(() -> closeAll(cmd, cmd.forceit));
    }

    private static void closeAll(ExCommand cmd, boolean discard) {
        for (Buffer buffer : buffers(cmd)) documents(cmd).close().accept(buffer.number(), discard);
    }

    /** {@code :sav[eas] {file}}: saves the tab as the file. */
    static void saveAs(ExCommand cmd) {
        if (fileArg(cmd) == null) throw new VimFailure("E471: Argument required");
        write(cmd, () -> { });
    }

    /**
     * {@code :e [file]}: with no file, reads the tab's file again ({@code !} discards changes); with a file, opens it
     * in a tab or selects its tab.
     */
    static void edit(ExCommand cmd) {
        String arg = fileArg(cmd);
        Buffer buffer = current(buffers(cmd));
        if (arg != null) {
            Path file = resolve(arg, buffer);
            if (buffer.file().filter(f -> same(f, file)).isEmpty()) {
                documents(cmd).open().accept(cmd.editor, Optional.of(file));
                return;
            }
        }
        if (buffer.untitled() || buffer.file().isEmpty()) throw new VimFailure("E32: No file name");
        if (buffer.modified() && !cmd.forceit) throw new VimFailure("E37: No write since last change (add ! to override)");
        documents(cmd).reload().accept(buffer.number());
    }

    /** {@code :ene[w]} and {@code :tabnew}: a new untitled tab. {@code :tabnew file} and {@code :tabe file} open the file. */
    static void newTab(ExCommand cmd) {
        String arg = cmd.name().equals("enew") ? null : fileArg(cmd);
        if (arg == null) {
            documents(cmd).open().accept(cmd.editor, Optional.empty());
            return;
        }
        documents(cmd).open().accept(cmd.editor, Optional.of(resolve(arg, current(buffers(cmd)))));
    }

    /**
     * {@code :r [file]}: the file's lines below the range's line (above the first for {@code :0r}); the cursor goes to
     * the first new line's first non-blank, as readfile leaves it.
     */
    static void read(ExCommand cmd) {
        if (cmd.forceit || cmd.arg.startsWith("!")) throw new VimFailure("E319: Sorry, the command is not available in this version");
        String arg = fileArg(cmd);
        Buffer buffer = current(buffers(cmd));
        Path file;
        if (arg == null) {
            if (buffer.file().isEmpty()) throw new VimFailure("E32: No file name");
            file = buffer.file().get();
        } else file = resolve(arg, buffer);
        Optional<String> text = documents(cmd).read().apply(file);
        if (text.isEmpty()) throw new VimFailure("E484: Can't open file " + (arg == null ? buffer.name() : arg));
        String content = text.get();
        List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));
        if (lines.size() > 1 && lines.getLast().isEmpty()) lines.removeLast();
        if (content.isEmpty()) lines.clear();
        VimEditor editor = cmd.editor;
        if (!lines.isEmpty()) {
            editor.area().vimUndo().stepCursor(editor.cursor().line, editor.cursor().col);
            editor.buffer().insertLines(cmd.line2, lines);
            Marks marks = editor.marks();
            marks.set('[', new Pos(cmd.line2, 0));
            marks.set(']', new Pos(cmd.line2 + lines.size() - 1, 0));
            editor.ex().beginline(cmd.line2 + 1, false);
        }
        editor.say("\"" + (arg == null ? buffer.name() : arg) + "\" " + size(content));
    }

    /** {@code :ls}, {@code :buffers} and {@code :files}: the tabs as buffer_list shows them. */
    static void list(ExCommand cmd) {
        for (Buffer buffer : buffers(cmd)) {
            StringBuilder line = new StringBuilder(String.format("%3d %c%c %c \"%s\"", buffer.number(),
                buffer.current() ? '%' : buffer.alternate() ? '#' : ' ', 'a', buffer.modified() ? '+' : ' ', buffer.name()));
            int width = 0;
            for (int i = 0; i < line.length(); ) {
                int cp = line.codePointAt(i);
                width += Text.width(cp);
                i += Character.charCount(cp);
            }
            int pad = 40 - width;
            do line.append(' ');
            while (--pad > 0);
            cmd.editor.say(line.append("line ").append(buffer.line()).toString());
        }
    }

    /** {@code :b N} and {@code :b name}: selects a tab by its buffer number or a part of its name. */
    static void buffer(ExCommand cmd) {
        List<Buffer> buffers = buffers(cmd);
        int number;
        String name = cmd.arg.strip();
        if (!name.isEmpty()) number = match(buffers, name.replace("\\ ", " ")).number();
        else if (cmd.addrCount > 0) number = cmd.line2;
        else return;
        if (buffers.stream().noneMatch(b -> b.number() == number)) throw new VimFailure("E86: Buffer " + number + " does not exist");
        documents(cmd).select().accept(number);
    }

    /**
     * buflist_findpat: the buffer whose name is the pattern, else whose name ends with it, else starts with it, else
     * holds it; at the first of these that matches, one buffer must.
     */
    private static Buffer match(List<Buffer> buffers, String pattern) {
        List<java.util.function.Predicate<String>> levels = List.of(n -> n.equals(pattern), n -> n.endsWith(pattern),
            n -> n.startsWith(pattern), n -> n.contains(pattern));
        for (var level : levels) {
            List<Buffer> found = buffers.stream().filter(b -> level.test(b.name())
                || b.file().map(f -> level.test(String.valueOf(f.getFileName()))).orElse(false)).toList();
            if (found.size() == 1) return found.getFirst();
            if (found.size() > 1) throw new VimFailure("E93: More than one match for " + pattern);
        }
        throw new VimFailure("E94: No matching buffer for " + pattern);
    }

    /** {@code :bn} and {@code :bp} ({@code :bN}): the next or previous tab, {@code N} times, wrapping around. */
    static void step(ExCommand cmd) {
        List<Buffer> buffers = buffers(cmd);
        int count = cmd.addrCount > 0 ? cmd.line2 : 1;
        int direction = cmd.name().equals("bnext") ? 1 : -1;
        int at = buffers.indexOf(current(buffers));
        int to = Math.floorMod(at + direction * count, buffers.size());
        if (to != at) documents(cmd).select().accept(buffers.get(to).number());
    }

    /** {@code :tabn [N]}: tab N, or the next one, wrapping around. */
    static void tabNext(ExCommand cmd) {
        List<Buffer> buffers = buffers(cmd);
        int n = tabCount(cmd, -1);
        int at = buffers.indexOf(current(buffers));
        if (n < 0) goTo(cmd, buffers, Math.floorMod(at + 1, buffers.size()));
        else if (n == 0 || n > buffers.size()) throw VimFailure.bell();
        else goTo(cmd, buffers, n - 1);
    }

    /** {@code :tabp [N]} and {@code :tabN [N]}: N tabs back, wrapping around. */
    static void tabPrevious(ExCommand cmd) {
        List<Buffer> buffers = buffers(cmd);
        int n = tabCount(cmd, 1);
        int at = buffers.indexOf(current(buffers));
        goTo(cmd, buffers, Math.floorMod(at - n, buffers.size()));
    }

    /** @return the count after the command or before it, or {@code none} */
    private static int tabCount(ExCommand cmd, int none) {
        String arg = cmd.arg.strip();
        if (!arg.isEmpty()) {
            if (!arg.chars().allMatch(Character::isDigit)) throw new VimFailure("E475: Invalid argument: " + arg);
            return (int) Math.min(Integer.MAX_VALUE, Long.parseLong(arg.length() > 9 ? arg.substring(0, 9) : arg));
        }
        return cmd.addrCount > 0 ? cmd.line2 : none;
    }

    private static void goTo(ExCommand cmd, List<Buffer> buffers, int index) {
        Buffer target = buffers.get(index);
        if (!target.current()) documents(cmd).select().accept(target.number());
    }

    /** {@code gt}: the next tab, or with a count tab {count}; beyond the last it rings the bell. */
    static void gt(Context c) {
        List<Buffer> buffers = c.vim.host().documents().buffers().apply(c.editor);
        int at = buffers.indexOf(current(buffers));
        int index;
        if (c.hasCount) {
            if (c.count > buffers.size()) throw Context.fail();
            index = c.count - 1;
        } else index = Math.floorMod(at + 1, buffers.size());
        if (index != at) c.vim.host().documents().select().accept(buffers.get(index).number());
    }

    /** {@code gT}: {count} tabs back, wrapping around. */
    static void gT(Context c) {
        List<Buffer> buffers = c.vim.host().documents().buffers().apply(c.editor);
        int at = buffers.indexOf(current(buffers));
        int index = Math.floorMod(at - c.count, buffers.size());
        if (index != at) c.vim.host().documents().select().accept(buffers.get(index).number());
    }

    /**
     * The file argument as expand_filename leaves it: {@code %} is the current file and {@code #} the alternate
     * ({@code \%} and {@code \#} are themselves), and a backslash before a space keeps it; a second name fails.
     *
     * @return the name, or null when there is none
     */
    static String fileArg(ExCommand cmd) {
        String arg = cmd.arg.strip();
        if (arg.isEmpty()) return null;
        List<Buffer> buffers = null;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == '\\' && i + 1 < arg.length() && " %#\\".indexOf(arg.charAt(i + 1)) >= 0) {
                out.append(arg.charAt(++i));
            } else if (c == '%' || c == '#') {
                if (buffers == null) buffers = buffers(cmd);
                Optional<Buffer> which = c == '%' ? buffers.stream().filter(Buffer::current).findFirst()
                    : buffers.stream().filter(Buffer::alternate).findFirst();
                if (which.isEmpty() && c == '#') throw new VimFailure("E194: No alternate file name to substitute for '#'");
                Buffer b = which.orElseThrow();
                if (b.file().isEmpty() && b.untitled()) throw new VimFailure("E499: Empty file name for '%' or '#', only works with \":p:h\"");
                out.append(b.file().map(Path::toString).orElse(b.name()));
            } else if (Ex.isWhite(c)) throw new VimFailure("E172: Only one file name allowed");
            else out.append(c);
        }
        return out.toString();
    }

    /** @return whether two paths name the same file, once absolute and normalized */
    private static boolean same(Path a, Path b) { return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize()); }

    /** A file name as a path: {@code ~} is the home folder; a relative name is in the current file's folder. */
    static Path resolve(String name, Buffer current) {
        String home = System.getProperty("user.home");
        String expanded = name.equals("~") ? home : name.startsWith("~/") ? home + name.substring(1) : name;
        Path path = Path.of(expanded);
        if (path.isAbsolute()) return path.normalize();
        return folder(current).resolve(path).normalize();
    }

    /** @return the folder relative names start from: the current file's, or the home folder */
    static Path folder(Buffer current) {
        return current.file().map(Path::toAbsolutePath).map(Path::getParent).orElse(Path.of(System.getProperty("user.home")));
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/VimHost.java` with:

```java
package dev.jasper.editor.vim;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * What the Vim engine needs from the Editor (spec 1.1): the clipboard, and the Editor's tabs as Vim's buffers.
 *
 * @param readClipboard reads {@code '+'} (the clipboard) or {@code '*'} (the selection, or the clipboard where there
 *     is none); empty when it holds no text or cannot be read
 * @param writeClipboard writes {@code '+'} or {@code '*'}
 * @param documents the tabs, for the file, buffer and tab commands
 */
public record VimHost(Function<Character, Optional<String>> readClipboard, BiConsumer<Character, String> writeClipboard, Documents documents) {
    /**
     * A host with a clipboard and no tabs.
     *
     * @param readClipboard reads {@code '+'} or {@code '*'}
     * @param writeClipboard writes {@code '+'} or {@code '*'}
     */
    public VimHost(Function<Character, Optional<String>> readClipboard, BiConsumer<Character, String> writeClipboard) {
        this(readClipboard, writeClipboard, Documents.NONE);
    }

    /** @return a host with no clipboard and no tabs: reads are empty and writes are dropped */
    public static VimHost detached() { return new VimHost(register -> Optional.empty(), (register, text) -> { }); }

    /**
     * An Editor tab as a Vim buffer (spec 6.2).
     *
     * @param number its buffer number, which stays while the tab is open
     * @param name what {@code :ls} and the messages call it: the file's location, or the untitled tab's name
     * @param file its local file, when it has one (an untitled tab opened with {@code :e newfile} has the file it
     *     will be saved to)
     * @param current whether it is the tab the command runs in
     * @param alternate whether it is the tab that was selected before ({@code #})
     * @param modified whether it has unsaved changes
     * @param untitled whether it has never been saved
     * @param line its cursor line, 1-based
     */
    public record Buffer(int number, String name, Optional<Path> file, boolean current, boolean alternate, boolean modified,
                         boolean untitled, int line) { }

    /**
     * The Editor's tabs as Vim's buffers. Buffers are named by their numbers; the functions run on the UI thread, and
     * the futures complete there.
     *
     * @param buffers the open tabs in their order, the one {@code editor} belongs to marked current
     * @param save saves a buffer as the Editor's Save does (Save As for an untitled tab); completes with whether it saved
     * @param saveAs saves a buffer to a file, which it then edits; completes with whether it saved
     * @param close closes a buffer's tab; {@code true} discards its changes without asking
     * @param select selects a buffer's tab
     * @param reload reads a buffer's file again, discarding its changes
     * @param open opens a file in a tab (or selects its tab) next to {@code editor}'s, or with no file a new untitled
     *     tab; a file that does not exist yet gets an empty tab its first save creates
     * @param read the text of a local file, or empty when it cannot be read
     */
    public record Documents(Function<VimEditor, List<Buffer>> buffers, IntFunction<CompletableFuture<Boolean>> save,
                            BiFunction<Integer, Path, CompletableFuture<Boolean>> saveAs, BiConsumer<Integer, Boolean> close,
                            IntConsumer select, IntConsumer reload, BiConsumer<VimEditor, Optional<Path>> open,
                            Function<Path, Optional<String>> read) {
        /** No tabs: an editor alone, which is buffer 1 and cannot be saved, closed or left. */
        public static final Documents NONE = new Documents(
            editor -> List.of(new Buffer(1, editor.documentName(), Optional.empty(), true, false, editor.documentModified(), true,
                editor.cursorLine())),
            number -> CompletableFuture.completedFuture(false), (number, file) -> CompletableFuture.completedFuture(false),
            (number, discard) -> { }, number -> { }, number -> { }, (editor, file) -> { }, file -> Optional.empty());
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
    private final VimHost host;
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
    public Vim(VimHost host) {
        this.host = host;
        registers = new Registers(host);
    }

    /** @return what the engine needs from the Editor */
    VimHost host() { return host; }

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
        if (name >= 'A' && name <= 'Z' || name >= '0' && name <= '9') {
            Vim.GlobalMark mark = c.vim.globalMarks().get((char) name);
            if (mark != null && mark.editor() != c.editor) {
                elsewhere(c, mark, linewise);
                throw VimFailure.quiet();
            }
        }
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

    /**
     * An uppercase mark in another tab (spec 3.6): its tab is selected and its cursor goes to the mark. An operator
     * or Visual mode cannot reach into another tab, so there the command fails.
     */
    private static void elsewhere(Context c, Vim.GlobalMark mark, boolean linewise) {
        if (c.operator != null || c.inVisual()) throw Context.fail();
        VimEditor other = mark.editor();
        var buffers = c.vim.host().documents().buffers().apply(other);
        var buffer = buffers.stream().filter(VimHost.Buffer::current).findFirst().orElseThrow(() -> new VimFailure("E20: Mark not set"));
        Pos pos = check(other.buffer(), mark.pos());
        String line = other.buffer().line(pos.line);
        int col = linewise ? Text.firstNonBlank(line) : Math.min(pos.col, line.length());
        if (col >= line.length() && !line.isEmpty()) col = Text.last(line);
        other.marks().setpcmark(other.cursor());
        other.setCursor(new Pos(pos.line, col));
        other.forgetColumn();
        c.vim.host().documents().select().accept(buffer.number());
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
            List<Runnable> after = List.copyOf(afterKey);
            afterKey.clear();
            for (Runnable work : after) work.run();
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

    /** @return the cursor's line, 1-based */
    int cursorLine() { return cursor().line + 1; }

    /** Work for after the key being handled ({@code :q} closes the tab once the key is done); at once outside a key. */
    void afterKey(Runnable work) {
        if (busy) afterKey.add(work);
        else work.run();
    }

    private final List<Runnable> afterKey = new ArrayList<>();

    /** A message given outside a key (a save that completed later): shown at once. */
    void announce(String text) {
        message(text, false);
        if (!busy) fire();
    }

    boolean documentModified() { return documentModified.getAsBoolean(); }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java` with:

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
        def("substitute", RANGE | WHOLEFOLD | EXTRA, Addr.LINES,
            "`:s/pattern/string/flags [count]`: replace matches; flags `& c e g i I n p # l r`", ExSubstitute::substitute);
        def("&", RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "repeat the last `:s` with the same pattern and string; `:&&` keeps its flags", ExSubstitute::substitute);
        def("~", RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "repeat the last `:s` with the last used pattern", ExSubstitute::substitute);
        def("global", RANGE | WHOLEFOLD | BANG | EXTRA | DFLALL, Addr.LINES, "`:g/pattern/cmd`: run cmd (default `:p`) on the matching lines; `!` on the others", ExGlobal::global);
        def("vglobal", RANGE | WHOLEFOLD | EXTRA | DFLALL, Addr.LINES, "`:v/pattern/cmd`: run cmd on the lines that do not match", ExGlobal::global);
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
        int file = RANGE | WHOLEFOLD | BANG | EXTRA | DFLALL | TRLBAR;
        def("write", file, Addr.LINES, "save the tab (`:w file` saves it as the file; `!` replaces a file that exists)", ExFiles::write);
        def("wq", file, Addr.LINES, "save the tab, then close it", ExFiles::writeQuit);
        def("xit", file, Addr.LINES, "save the tab if it has changes, then close it", ExFiles::exit);
        def("exit", file, Addr.LINES, "as `:x`", ExFiles::exit);
        def("saveas", BANG | EXTRA | TRLBAR, Addr.NONE, "save the tab as {file}", ExFiles::saveAs);
        def("wall", BANG | TRLBAR, Addr.NONE, "save every tab with changes", ExFiles::writeAll);
        def("xall", BANG | TRLBAR, Addr.NONE, "save every tab with changes, then close them all", ExFiles::exitAll);
        def("wqall", BANG | TRLBAR, Addr.NONE, "as `:xa`", ExFiles::exitAll);
        def("quit", BANG | TRLBAR, Addr.NONE, "close the tab; `!` discards its changes", ExFiles::quit);
        def("qall", BANG | TRLBAR, Addr.NONE, "close every tab; `!` discards their changes", ExFiles::quitAll);
        def("quitall", BANG | TRLBAR, Addr.NONE, "as `:qa`", ExFiles::quitAll);
        def("edit", BANG | EXTRA | TRLBAR, Addr.NONE, "open {file} in a tab, or read the tab's file again (`!` discards changes)", ExFiles::edit);
        def("enew", BANG | TRLBAR, Addr.NONE, "a new untitled tab", ExFiles::newTab);
        def("read", BANG | RANGE | WHOLEFOLD | EXTRA | TRLBAR | ZEROR, Addr.LINES, "put {file}'s lines below the line (`:0r` above the first)", ExFiles::read);
        def("ls", BANG | EXTRA | TRLBAR, Addr.NONE, "list the tabs as buffers", ExFiles::list);
        def("buffers", BANG | EXTRA | TRLBAR, Addr.NONE, "as `:ls`", ExFiles::list);
        def("files", BANG | EXTRA | TRLBAR, Addr.NONE, "as `:ls`", ExFiles::list);
        def("buffer", BANG | RANGE | COUNT | EXTRA | TRLBAR, Addr.OTHER, "select the tab with buffer number N, or whose name holds {name}", ExFiles::buffer);
        def("bnext", BANG | RANGE | COUNT | TRLBAR, Addr.OTHER, "the next tab, [N] times", ExFiles::step);
        def("bNext", BANG | RANGE | COUNT | TRLBAR, Addr.OTHER, "the previous tab, [N] times", ExFiles::step);
        def("bprevious", BANG | RANGE | COUNT | TRLBAR, Addr.OTHER, "the previous tab, [N] times", ExFiles::step);
        def("tabnext", RANGE | ZEROR | EXTRA | TRLBAR, Addr.OTHER, "the next tab, or tab [N]", ExFiles::tabNext);
        def("tabNext", RANGE | ZEROR | EXTRA | TRLBAR, Addr.OTHER, "[N] tabs back", ExFiles::tabPrevious);
        def("tabprevious", RANGE | ZEROR | EXTRA | TRLBAR, Addr.OTHER, "[N] tabs back", ExFiles::tabPrevious);
        def("tabnew", BANG | EXTRA | RANGE | ZEROR | TRLBAR, Addr.OTHER, "a new untitled tab, or {file} in a tab", ExFiles::newTab);
        def("tabedit", BANG | EXTRA | RANGE | ZEROR | TRLBAR, Addr.OTHER, "as `:tabnew`", ExFiles::newTab);
    }

    /** @return the commands Jasper implements, in the reference's order */
    static List<Def> implemented() { return List.copyOf(DEFS.values()); }

    /** The keys that open a command line leave the remembered column alone ({@code :s} after {@code $} keeps it). */
    static final List<String> KEEP_COLUMN = List.of(":", "&", "g&");

    /** The Normal- and Visual-mode keys that reach the command line. */
    static void register(VimCommands commands) {
        commands.action(":", "a command line; with a count, for that many lines (`:.,.+N`)", false, c -> c.editor.ex().open(c));
        commands.visual(":", "a command line for the Visual lines (`:'<,'>`)", false, false, c -> c.editor.ex().open(c));
        commands.action("ZZ", "save the tab if it has changes and close it (`:x`)", false, c -> c.editor.ex().execute("x"));
        commands.action("ZQ", "close the tab, discarding its changes (`:q!`)", false, c -> c.editor.ex().execute("q!"));
        commands.action("gt", "the next tab, or tab {count}", false, ExFiles::gt);
        commands.action("gT", "{count} tabs back", false, ExFiles::gT);
        commands.action("&", "repeat the last `:s` on the line, without its flags", false, c -> {
            String line = (c.hasCount ? c.count > 1 ? ".,.+" + (c.count - 1) : "." : "") + "s";
            c.editor.ex().execute(line);
            c.editor.vim().registers().lastCommand(line);
        });
        commands.action("g&", "repeat the last `:s` on every line, with its flags (`:%s//~/&`)", false,
            c -> c.editor.ex().execute("%s//~/&"));
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
    static int skipRange(String line, int start) {
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

- [ ] **Step 4: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: the reference gains `ZZ`, `ZQ`, `gt`, `gT` and the file, buffer and tab commands.

- [ ] **Step 5: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 163 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1569 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`: the Editor compiles without warnings (`-Xlint:all`).

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/vim-v2b
git add plugins/editor/src/main/java/dev/jasper/editor/vim plugins/editor/src/test/java/dev/jasper/editor/vim/ExFilesTest.java plugins/editor/src/test/java/dev/jasper/editor/vim/VimCorpusTest.java docs/editor-vim-reference.md
git commit -m "Add Vim's file, buffer and tab commands over the Editor's tabs

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: The Editor behind the buffers

**Files:**
- Replace: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`, `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`

**Interfaces:**
- Consumes: Task 1's `VimHost.Documents` and `VimHost.Buffer`.

`EditorWindow` numbers each tab when it opens (numbers are not reused while Jasper runs), remembers the tab selected before the current one (Vim's alternate buffer), and implements `Documents` with what it already has: `saveSession` (Save, or Save As for an untitled tab), `DocumentSession.saveAs`, `closeTab` (which asks about changes through `approveTab`; a tab in `discarding` closes without asking, for `:q!`), `select`, `DocumentSession.reload`, `openLocal` and `newUntitled` in the workspace of the tab the command ran in. `:e` of a file that does not exist gives an untitled tab named after it, whose first save (`saveSession`) writes that file. `:r` reads a local file with the Editor's decoder. The tests type through the tab's `VimEditor` on the event thread.

- [ ] **Step 1: Write the failing tests**

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java` with:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON;

class EditorWindowTest {
    @TempDir Path home;
    private FakePluginHost host;
    private PluginContext context;
    private final Deque<String> answers = new ArrayDeque<>();
    private final List<Question> asked = new ArrayList<>();
    private final AtomicReference<EditorSettings> settings = new AtomicReference<>(new EditorSettings("Monospaced", 13, 4, false, 1, false));
    private EditorWindow window;
    private boolean windowClosed;
    private Runnable duringWait = () -> { };
    private Runnable duringAsk = () -> { };
    private boolean cancelWait;

    @BeforeEach void open() {
        Languages.register();
        host = new FakePluginHost(home.resolve("plugins"));
        context = host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of()), Set.of(), Set.of(), c -> { });
        var environment = new EditorEnvironment(context, Runnable::run,
            owner -> question -> {
                asked.add(question);
                Runnable hook = duringAsk;
                duringAsk = () -> { };
                hook.run();
                return answers.isEmpty() ? question.dismissed() : answers.removeFirst();
            },
            owner -> (message, saves) -> {
                asked.add(new Question("Wait", message, List.of("OK")));
                if (cancelWait) return false;
                duringWait.run();
                drain();
                return saves.getNow(false);
            },
            settings::get, false);
        window = new EditorWindow(environment, () -> windowClosed = true);
    }

    @AfterEach void close() { host.close(); }

    private void drain() { while (host.runBackground() > 0) { } }

    private Path file(String name, String text) throws Exception {
        Path file = home.resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text);
    }

    private EditorTab opened(Path file) {
        window.openLocal(file);
        drain();
        return window.selected().orElseThrow();
    }

    @Test void filesUseHostedTabsAndMovingPreservesDirtyTextAndUndo() throws Exception {
        var tab = opened(file("hosted.txt", "before"));
        assertThat(host.windows()).isEmpty();
        assertThat(host.contentTabs()).hasSize(1);
        var handle = host.contentTabs().getFirst();
        var component = host.contentComponent(handle);
        tab.area().append(" after");
        handle.move(true);
        assertThat(handle.isSeparate()).isTrue();
        assertThat(host.contentComponent(handle)).isSameAs(component);
        assertThat(tab.area().getText()).isEqualTo("before after");
        assertThat(tab.session().dirty()).isTrue();
        tab.area().undoLastAction();
        assertThat(tab.area().getText()).isEqualTo("before");
        handle.move(false);
        assertThat(handle.isSeparate()).isFalse();
    }

    @Test void openingAFileThatIsStillLoadingSelectsItsTab() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        window.openLocal(file);
        assertThat(host.windows()).isEmpty();
        assertThat(host.contentTabs()).hasSize(1);
        assertThat(window.selected().orElseThrow().session().state()).isEqualTo(DocumentSession.State.LOADING);
        window.openLocal(file);
        assertThat(window.sessions()).hasSize(1);
        assertThat(host.runBackground()).as("one read and one decode, not two reads").isEqualTo(2);
        EditorTab tab = window.selected().orElseThrow();
        assertThat(tab.area().getText()).isEqualTo("a = 1\n");
        assertThat(window.headerTitle(0)).isEqualTo("config.toml");
        assertThat(window.showingEmpty()).isFalse();
    }

    @Test void editingShowsTheMarkerAndSavingWritesTheFile() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        assertThat(window.headerTitle(0)).isEqualTo("● config.toml");
        window.save();
        drain();
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
        assertThat(window.headerTitle(0)).isEqualTo("config.toml");
    }

    @Test void theCloseCommandClosesTabsFirstAskingAboutUnsavedOnesThenTheWindow() throws Exception {
        opened(file("a.toml", "a = 1\n"));
        EditorTab second = opened(file("b.toml", "b = 1\n"));
        second.area().setText("b = 2\n");
        answers.add("Cancel");
        assertThat(host.contentTabs().getLast().requestClose().join()).as("handled by asking about the tab").isFalse();
        assertThat(asked.getLast()).isEqualTo(new Question("Unsaved changes", "Save changes to b.toml?", List.of("Save", "Don't Save", "Cancel")));
        assertThat(window.sessions()).hasSize(2);
        answers.add("Don't Save");
        assertThat(host.contentTabs().getLast().requestClose().join()).isTrue();
        assertThat(window.sessions()).hasSize(1);
        assertThat(host.contentTabs().getLast().requestClose().join()).isTrue();
        assertThat(window.sessions()).isEmpty();
        assertThat(window.showingEmpty()).isTrue();
        assertThat(host.contentTabs()).isEmpty();
        window.dispose();
        assertThat(windowClosed).isTrue();
    }

    @Test void quittingOrClosingTheWindowAsksSaveAllDiscardOrCancel() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        answers.add("Cancel");
        assertThat(host.requestQuit()).isFalse();
        assertThat(asked.getLast()).isEqualTo(new Question("Unsaved changes", "config.toml has unsaved changes.", List.of("Save All", "Discard", "Cancel")));
        answers.add("Save All");
        assertThat(host.requestQuit()).isTrue();
        assertThat(asked.getLast().message()).isEqualTo("Saving 1 file…");
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
        tab.area().setText("a = 3\n");
        answers.add("Discard");
        assertThat(host.requestQuit()).isTrue();
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void aFailedSaveAllKeepsJasperRunning() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        Files.delete(file);
        Files.createDirectory(file);
        answers.addAll(List.of("Save All", DocumentSession.OVERWRITE));
        assertThat(host.requestQuit()).isFalse();
        assertThat(asked).extracting(Question::title).containsExactly("Unsaved changes", "Wait", "File changed", "Save failed");
    }

    @Test void closingATabWhileItSavesClosesItAfterTheSave() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        window.save();
        window.closeCurrent();
        assertThat(window.sessions()).as("waits for the save").hasSize(1);
        assertThat(asked).as("asks nothing").isEmpty();
        drain();
        assertThat(window.sessions()).isEmpty();
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void quitWaitsForASaveThatIsStillRunning() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        window.save();
        assertThat(tab.session().saving()).isTrue();
        assertThat(host.requestQuit()).as("waits for the running save and asks nothing").isTrue();
        assertThat(asked).extracting(Question::message).containsExactly("Saving 1 file…");
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void closingATabWhoseSaveFinishedAsksAboutEditsMadeAfterItStarted() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        window.save();
        tab.area().setText("a = 3\n");
        window.closeCurrent();
        drain();
        assertThat(asked.getLast()).isEqualTo(new Question("Unsaved changes", "Save changes to config.toml?", List.of("Save", "Don't Save", "Cancel")));
        assertThat(window.sessions()).as("Cancel is the default dismissal").hasSize(1);
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void waitingForRunningSavesDoesNotSilentlySaveEditsMadeAfterTheyFinish() throws Exception {
        Path a = file("a.toml", "a = 1\n");
        Path b = file("b.toml", "b = 1\n");
        opened(a);
        opened(b);
        window.openLocal(a);
        EditorTab tabA = window.selected().orElseThrow();
        tabA.area().setText("a = 2\n");
        window.save();
        window.openLocal(b);
        EditorTab tabB = window.selected().orElseThrow();
        tabB.area().setText("b = 2\n");
        window.save();
        tabB.area().setText("b = 3\n");
        assertThat(host.requestQuit()).isFalse();
        assertThat(asked).extracting(Question::message).containsExactly("Saving 2 files…", "b.toml has unsaved changes.");
        assertThat(Files.readString(b)).isEqualTo("b = 2\n");
    }

    @Test void savingAllWaitsThenAsksAgainAboutEditsMadeDuringTheWait() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        answers.add("Save All");
        duringWait = () -> tab.area().setText("a = 3\n");
        assertThat(host.requestQuit()).isFalse();
        assertThat(asked).extracting(Question::title).filteredOn("Unsaved changes"::equals).hasSize(2);
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void saveAllDefersIndividualCloseWhileTheBatchIsPending() throws Exception {
        Path a = file("a.toml", "a = 1\n");
        Path b = file("b.toml", "b = 1\n");
        opened(a).area().setText("a = 2\n");
        EditorTab second = opened(b);
        second.area().setText("b = 2\n");
        answers.addAll(List.of("Save All", "Don't Save"));
        duringWait = () -> window.closeTab(second);
        assertThat(host.requestQuit()).isTrue();
        assertThat(Files.readString(a)).isEqualTo("a = 2\n");
        assertThat(Files.readString(b)).as("batch retains and saves both tabs").isEqualTo("b = 2\n");
        assertThat(host.contentTabs()).hasSize(2);
    }

    @Test void cancellingTheSaveAllWaitStartsNoFurtherSaves() throws Exception {
        Path a = file("a.toml", "a = 1\n");
        Path b = file("b.toml", "b = 1\n");
        opened(a).area().setText("a = 2\n");
        opened(b).area().setText("b = 2\n");
        answers.add("Save All");
        cancelWait = true;
        assertThat(host.requestQuit()).isFalse();
        drain();
        assertThat(Files.readString(a)).as("the save already running finishes").isEqualTo("a = 2\n");
        assertThat(Files.readString(b)).as("no save starts after Cancel").isEqualTo("b = 1\n");
        assertThat(asked).extracting(Question::title).containsExactly("Unsaved changes", "Wait");
    }

    @Test void aSaveAllStepThatThrowsFailsTheSaveInsteadOfHanging() throws Exception {
        window.newUntitled();
        window.selected().orElseThrow().area().setText("x\n");
        host.duringPathSelection(() -> { throw new IllegalStateException("the chooser failed"); });
        answers.add("Save All");
        assertThat(host.requestQuit()).isFalse();
        assertThat(host.failures()).isEmpty();
    }

    @Test void quittingAgainWhileTheClosingGuardAsksIsRefused() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        opened(file).area().setText("a = 2\n");
        List<Boolean> inner = new ArrayList<>();
        duringAsk = () -> inner.add(host.requestQuit());
        answers.add("Save All");
        assertThat(host.requestQuit()).isTrue();
        assertThat(inner).as("the inner quit is refused").containsExactly(false);
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void untitledTabsAreSavedAsALocalFileAndAnOpenPathIsRefused() throws Exception {
        window.newUntitled();
        assertThat(window.headerTitle(0)).isEqualTo("Untitled");
        EditorTab tab = window.selected().orElseThrow();
        tab.area().setText("print(1)\n");
        Path target = home.resolve("app.py");
        host.queuePathSelection(List.of(target));
        window.save();
        drain();
        assertThat(Files.readString(target)).isEqualTo("print(1)\n");
        assertThat(window.headerTitle(0)).isEqualTo("app.py");
        assertThat(tab.area().getSyntaxEditingStyle()).isEqualTo(SYNTAX_STYLE_PYTHON);
        window.newUntitled();
        host.queuePathSelection(List.of(target));
        window.saveAs();
        assertThat(asked.getLast().message()).isEqualTo(target + " is open in another tab.");
    }

    @Test void sameNamedFilesShowTheirFolders() throws Exception {
        opened(file("one/config.toml", "a = 1\n"));
        opened(file("two/config.toml", "a = 2\n"));
        assertThat(window.headerDetail(0)).isEqualTo("one");
        assertThat(window.headerDetail(1)).isEqualTo("two");
        window.closeCurrent();
        assertThat(window.headerDetail(0)).isEmpty();
    }

    @Test void theLanguagePickerSoftWrapGoToLineAndToggleCommentActOnTheCurrentTab() throws Exception {
        EditorTab tab = opened(file("notes.txt", "one\ntwo\nthree\n"));
        window.chooseLanguage();
        var picker = (LanguagePicker) host.windowContent("dev.jasper.editor", "Language").orElseThrow();
        picker.filter("tom");
        picker.chooseSelected();
        assertThat(tab.session().style()).isEqualTo(Languages.TOML);
        assertThat(host.windows()).isEmpty();
        assertThat(host.contentTabs()).hasSize(1);
        window.toggleWrap();
        assertThat(tab.area().getLineWrap()).isTrue();
        window.goToLine();
        var goTo = (GoToLinePanel) host.windowContent("dev.jasper.editor", "Go to Line").orElseThrow();
        goTo.field().setText("3");
        goTo.submit();
        assertThat(tab.area().getCaretLineNumber()).isEqualTo(2);
        window.toggleComment();
        assertThat(tab.area().getText()).contains("#three");
    }

    @Test void outsideChangesReachTabsThroughTheWatcher() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        Files.writeString(file, "a = 22\n");
        window.tick();
        drain();
        assertThat(tab.area().getText()).isEqualTo("a = 22\n");
    }

    @Test void theMenuListsEveryCommandAndSettingsApplyToOpenTabs() throws Exception {
        EditorTab tab = opened(file("a.json", "{}\n"));
        assertThat(window.menu().getComponents()).extracting(item -> ((JMenuItem) item).getText())
            .contains("Save", "Save As…", "Save All", "Open…", "New File", "Close Tab", "Find…", "Replace…", "Go to Line…",
                "Toggle Comment", "Revert to Saved", "Language…", "Toggle Soft Wrap", "Toggle Vim Mode");
        settings.set(new EditorSettings("Monospaced", 18, 2, false, 1, false));
        window.apply();
        assertThat(tab.area().getFont().getSize()).isEqualTo(18);
        assertThat(tab.area().getTabSize()).isEqualTo(2);
        assertThat(tab.area().getInputMap().get(KeyNames.stroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.SHIFT_DOWN_MASK)))
            .as("Save As overrides RSyntaxTextArea's macro binding").isEqualTo("save-as");
    }
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
        Path original = file("original.txt", "original");
        opened(original);
        host.queuePathSelection(List.of(home.resolve("renamed.txt")));
        window.saveAs(); drain();
        opened(original);
        assertThat(host.contentTabs()).hasSize(2);
        assertThat(window.sessions()).extracting(DocumentSession::name).containsExactly("renamed.txt", "original.txt");
        assertThat(host.contentComponent(host.contentTabs().getFirst())).isNotSameAs(host.contentComponent(host.contentTabs().getLast()));
    }
    @Test void statusActionsTargetTheirOwnDocumentAcrossWindows() throws Exception {
        var first = opened(file("first.txt", "one"));
        var firstHandle = host.contentTabs().getFirst();
        firstHandle.move(true);
        var wrap = host.contentStatus(firstHandle).stream().filter(item -> item.text().startsWith("Wrap:")).findFirst().orElseThrow();
        var second = opened(file("second.txt", "two"));
        boolean original = first.session().wrap();
        boolean other = second.session().wrap();
        wrap.run().run();
        assertThat(first.session().wrap()).isEqualTo(!original);
        assertThat(second.session().wrap()).isEqualTo(other);
    }

    /** Types Vim keys on the event thread, where the caret follows the text as in the app. */
    private static void vimKeys(EditorTab tab, String keys) throws Exception {
        var vim = tab.vimEditor().orElseThrow();
        javax.swing.SwingUtilities.invokeAndWait(() -> { for (var key : dev.jasper.editor.vim.KeyInput.parse(keys)) vim.feed(key); });
    }

    @Test void vimWritesOpensListsSelectsAndClosesTabs() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
        Path one = file("one.txt", "one\n");
        file("two.txt", "two\n");
        EditorTab first = opened(one);
        vimKeys(first, "Aa<Esc>:w<CR>");
        drain();
        assertThat(Files.readString(one)).isEqualTo("onea\n");
        assertThat(first.vimEditor().orElseThrow().message()).isEqualTo("\"" + one + "\" 1L, 5B written");
        vimKeys(first, ":e two.txt<CR>");
        drain();
        EditorTab second = window.selected().orElseThrow();
        assertThat(second).isNotSameAs(first);
        assertThat(second.area().getText()).isEqualTo("two\n");
        vimKeys(second, ":ls<CR>");
        assertThat(second.vimEditor().orElseThrow().output()).extracting(dev.jasper.editor.vim.VimEditor.OutputLine::text)
            .containsExactly(String.format("  1 #a   \"%s\"", one) + " ".repeat(Math.max(1, 30 - one.toString().length())) + "line 1",
                String.format("  2 %%a   \"%s\"", home.resolve("two.txt")) + " ".repeat(Math.max(1, 30 - home.resolve("two.txt").toString().length())) + "line 1");
        vimKeys(second, "<CR>:b 1<CR>");
        assertThat(window.selected()).contains(first);
        vimKeys(first, "gt");
        assertThat(window.selected()).contains(second);
        vimKeys(second, "x:q<CR>");
        assertThat(second.vimEditor().orElseThrow().message()).isEqualTo("E37: No write since last change (add ! to override)");
        assertThat(window.sessions()).hasSize(2);
        vimKeys(second, ":q!<CR>");
        assertThat(asked).as("no question").isEmpty();
        assertThat(window.sessions()).hasSize(1);
        assertThat(Files.readString(home.resolve("two.txt"))).isEqualTo("two\n");
        assertThat(window.selected()).contains(first);
    }

    @Test void vimOpensANewFileThatItsFirstWriteCreates() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
        EditorTab first = opened(file("one.txt", "one\n"));
        vimKeys(first, ":e new.txt<CR>");
        EditorTab created = window.selected().orElseThrow();
        assertThat(created.session().name()).isEqualTo("new.txt");
        vimKeys(created, "ihello<Esc>:wq<CR>");
        drain();
        assertThat(Files.readString(home.resolve("new.txt"))).isEqualTo("hello");
        assertThat(window.sessions()).as(":wq closed it once saved").hasSize(1);
        vimKeys(first, ":r new.txt<CR>");
        assertThat(first.area().getText()).isEqualTo("one\nhello\n");
        vimKeys(first, ":enew<CR>");
        assertThat(window.sessions()).hasSize(2);
        assertThat(window.selected().orElseThrow().session().untitled()).isTrue();
    }

    @Test void anUppercaseMarkSelectsItsTab() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
        EditorTab first = opened(file("one.txt", "one\n"));
        EditorTab second = opened(file("two.txt", "a\n  b\nc\n"));
        vimKeys(second, "jlmAgg");
        vimKeys(second, ":b 1<CR>");
        assertThat(window.selected()).contains(first);
        vimKeys(first, "'A");
        assertThat(window.selected()).contains(second);
        assertThat(second.area().getCaretLineNumber()).isEqualTo(1);
        assertThat(second.vimEditor().orElseThrow().message()).isEmpty();
        vimKeys(second, "gg:b 1<CR>");
        vimKeys(first, "`A");
        assertThat(window.selected()).contains(second);
        assertThat(second.area().getCaretPosition()).isEqualTo(3);
        vimKeys(second, ":b 1<CR>");
        vimKeys(first, "d'A");
        assertThat(first.area().getText()).as("an operator cannot reach another tab").isEqualTo("one\n");
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.EditorWindowTest'
```

Expected: `vimWritesOpensListsSelectsAndClosesTabs`, `vimOpensANewFileThatItsFirstWriteCreates` and `anUppercaseMarkSelectsItsTab` fail: the window gives Vim no tabs yet.

- [ ] **Step 3: Replace `EditorWindow`**

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java` with:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.local.LocalWatcher;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.EditorModel;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.editor.vim.Vim;
import dev.jasper.editor.vim.VimHost;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.ContentSpec;
import dev.jasper.sdk.ui.ContentPresentation;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;

/** Document sessions and commands for app-owned tabs, including closing and quitting. UI thread only. */
public final class EditorWindow {
    private static final Set<String> POPUP = Set.of("save", "find", "go-to-line", "toggle-comment");

    private final EditorEnvironment environment;
    private final PluginContext context;
    private final Runnable onClosed;
    private final EditorModel model = new EditorModel();
    private final EditorTheme theme = new EditorTheme();
    private final java.util.Map<EditorTab, ContentTab> hosted = new java.util.LinkedHashMap<>();
    private EditorTab selectedTab;
    private WindowOwner origin;
    private WindowOwner promptOwner;
    private dev.jasper.sdk.Subscription closeRegistration;
    private boolean disposed;
    private final Function<Question, String> ask;
    private final BiFunction<String, CompletableFuture<Boolean>, Boolean> await;
    private final FindBar findBar;
    private final StatusLine status;
    private final List<Command> commands;
    private final LocalWatcher watcher;
    private final Timer ticker;
    private final Timer noticeTimer;
    private final Icon fileIcon, serverIcon;
    private boolean showingEmpty = true;
    private boolean guarding;
    private final Vim vim;
    /** Each tab's Vim buffer number (Vim spec 6.2), kept while it is open. */
    private final java.util.Map<DocumentSession, Integer> numbers = new java.util.HashMap<>();
    private int nextNumber = 1;
    /** The tab selected before the current one: Vim's alternate buffer. */
    private EditorTab previous;
    /** Untitled tabs {@code :e} opened for files that do not exist yet: their first save creates the file. */
    private final java.util.Map<DocumentSession, Path> newFiles = new java.util.HashMap<>();
    /** Tabs {@code :q!} closes: their changes go without asking. */
    private final Set<EditorTab> discarding = new java.util.HashSet<>();
    /** Vim mode as toggled in this session, until the settings file says the same; null to follow the settings. */
    private Boolean vimToggled;

    /**
     * Creates the window, not yet shown.
     *
     * @param environment what the window needs from the plugin
     * @param onClosed runs once the window has closed
     */
    public EditorWindow(EditorEnvironment environment, Runnable onClosed) {
        this.environment = environment;
        this.context = environment.context();
        this.onClosed = onClosed;
        fileIcon = context.appearance().icon(IconName.FILE);
        serverIcon = context.appearance().icon(IconName.SERVER);
        ask = question -> environment.askers().apply(owner()).apply(question);
        await = (message, saves) -> environment.waits().apply(owner()).apply(message, saves);
        findBar = new FindBar(() -> selected().map(EditorTab::area));
        status = new StatusLine(this::chooseLanguage, this::toggleWrap, this::toggleVim);
        commands = List.of(
            command("save", "Save", KeyEvent.VK_S, 0, this::save),
            command("save-as", "Save As…", KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK, this::saveAs),
            command("save-all", "Save All", KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK, () -> saveAll()),
            command("open", "Open…", KeyEvent.VK_O, 0, this::openChooser),
            command("new", "New File", KeyEvent.VK_N, 0, this::newUntitled),
            new Command("close-tab", "Close Tab", Optional.of(KeyNames.stroke(KeyEvent.VK_W, 0)), false, this::closeCurrent),
            command("find", "Find…", KeyEvent.VK_F, 0, () -> findBar.open(false)),
            command("replace", "Replace…", KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK, () -> findBar.open(true)),
            command("find-next", "Find Next", KeyEvent.VK_G, 0, findBar::next),
            command("find-previous", "Find Previous", KeyEvent.VK_G, InputEvent.SHIFT_DOWN_MASK, findBar::previous),
            command("go-to-line", "Go to Line…", KeyEvent.VK_L, 0, this::goToLine),
            command("toggle-comment", "Toggle Comment", KeyEvent.VK_SLASH, 0, this::toggleComment),
            new Command("revert", "Revert to Saved", Optional.empty(), false, this::revert),
            new Command("language", "Language…", Optional.empty(), false, this::chooseLanguage),
            new Command("wrap", "Toggle Soft Wrap", Optional.empty(), false, this::toggleWrap),
            new Command("toggle-vim", "Toggle Vim Mode", Optional.empty(), false, this::toggleVim));
        vim = new Vim(new VimHost(SystemClipboard::read, SystemClipboard::write, documents()));
        watcher = new LocalWatcher(context.background(), environment.ui(), model::sessions);
        ticker = new Timer(2000, event -> watcher.tick());
        noticeTimer = new Timer(3000, event -> selected().ifPresent(tab -> tab.session().clearNotice()));
        noticeTimer.setRepeats(false);
        closeRegistration = context.windows().onTabsClosing((handles, batch) -> {
            var closing = hosted.entrySet().stream().filter(e -> handles.contains(e.getValue())).map(java.util.Map.Entry::getKey).toList();
            if (closing.isEmpty()) return CompletableFuture.completedFuture(true);
            if (!batch && closing.size() == 1) return approveTab(closing.getFirst());
            promptOwner = handles.getFirst();
            try { return CompletableFuture.completedFuture(mayClose(closing)); }
            finally { promptOwner = null; }
        });
        if (environment.timers()) ticker.start();
    }

    /** @param document opened in a new tab, or its open tab selected; the window comes to the front */
    public void open(EditorDocument document) { open(document, null); }

    /** Opens a file in the originating workspace; null chooses the last main workspace. */
    public void open(EditorDocument document, WindowOwner owner) {
        origin = owner;
        DocumentSession session = model.find(document.key()).orElseGet(() -> {
            var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
            var created = new DocumentSession(document, sessionEnvironment(ref));
            add(created, ref); created.load(); return created;
        });
        select(session);
    }
    private SessionEnvironment sessionEnvironment(java.util.concurrent.atomic.AtomicReference<WindowOwner> owner) {
        return new SessionEnvironment(context.background(), environment.ui(),
            question -> owner.get() instanceof ContentTab tab && tab.isClosed() ? question.dismissed() : environment.askers().apply(owner.get()).apply(question), () -> environment.settings().get().maxFileBytes());
    }
    private WindowOwner owner() { return promptOwner != null ? promptOwner : selectedTab == null ? origin : hosted.get(selectedTab); }
    private WindowOwner owner(DocumentSession session) { return hosted.entrySet().stream().filter(e -> e.getKey().session() == session).map(e -> (WindowOwner)e.getValue()).findFirst().orElseGet(this::owner); }
    public void openLocal(Path file) { openLocal(file, null); }
    public void openLocal(Path file, WindowOwner owner) { open(new LocalDocument(file, context.background()), owner); }
    public void openLocalFiles(List<Path> files) { openLocalFiles(files, owner()); }
    public void openLocalFiles(List<Path> files, WindowOwner owner) { for (Path file : files) openLocal(file, owner); }
    public void newUntitled() { newUntitled(owner()); }
    public void newUntitled(WindowOwner owner) {
        origin = owner;
        var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
        var session = DocumentSession.untitled(model.nextUntitledName(), sessionEnvironment(ref));
        add(session, ref); select(session);
    }
    public void show() { if (selectedTab == null) newUntitled(); else hosted.get(selectedTab).select(); }
    public void dispose() {
        if (disposed) return; disposed = true;
        for (var handle : List.copyOf(hosted.values())) handle.close();
        closeRegistration.close(); closed();
    }

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
            String reason = failed.getMessage() == null ? failed.getClass().getSimpleName() : failed.getMessage();
            ask.apply(new Question("Vim mode", "Vim mode is " + (on ? "on" : "off") + " until the Editor closes: the setting could not be saved ("
                + reason + ").", List.of("OK")));
        }
    }

    /** Checks open local files for outside changes; the window's timer calls this every 2 s. */
    public void tick() { watcher.tick(); }

    public void save() { selected().ifPresent(tab -> saveSession(tab.session())); }

    public void saveAs() { selected().ifPresent(tab -> saveAsSession(tab.session())); }

    /** @return completes with true when every tab with unsaved changes was saved */
    public CompletableFuture<Boolean> saveAll() {
        return saveSessions(model.sessions().stream().filter(DocumentSession::dirty).toList(), new AtomicBoolean());
    }

    public void openChooser() {
        Optional<Path> near = selected().flatMap(tab -> localFile(tab.session())).map(Path::getParent);
        openLocalFiles(context.windows().chooseFiles(owner(), "Open", near));
    }

    public void closeCurrent() { selected().ifPresent(this::closeTab); }

    /** @param tab closed after asking about unsaved changes; a tab that is saving closes when its save succeeds */
    public void closeTab(EditorTab tab) { var handle = hosted.get(tab); if (handle != null) handle.requestClose(); }

    private CompletableFuture<Boolean> approveTab(EditorTab tab) {
        if (!hosted.containsKey(tab)) return CompletableFuture.completedFuture(true);
        if (discarding.remove(tab)) return CompletableFuture.completedFuture(true);
        DocumentSession session = tab.session();
        var pending = session.pendingSave();
        if (pending.isPresent()) return pending.get().thenCompose(saved -> saved ? approveTab(tab) : CompletableFuture.completedFuture(false));
        if (!session.dirty()) return CompletableFuture.completedFuture(true);
        String answer = environment.askers().apply(hosted.get(tab)).apply(new Question("Unsaved changes", "Save changes to " + session.name() + "?", List.of("Save", "Don't Save", "Cancel")));
        if ("Don't Save".equals(answer)) return CompletableFuture.completedFuture(true);
        if (!"Save".equals(answer)) return CompletableFuture.completedFuture(false);
        return saveSession(session).thenCompose(saved -> saved ? approveTab(tab) : CompletableFuture.completedFuture(false));
    }

    public void goToLine() {
        selected().filter(tab -> tab.session().state() == DocumentSession.State.READY).ifPresent(tab -> {
            RSyntaxTextArea area = tab.area();
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Go to Line", owner(), true));
            dialog.setContent(new GoToLinePanel(area.getCaretLineNumber() + 1, area.getLineCount(), line -> {
                dialog.close();
                try { area.setCaretPosition(area.getLineStartOffset(line - 1)); }
                catch (BadLocationException impossible) { return; }
                area.requestFocusInWindow();
            }, dialog::close));
            dialog.show();
        });
    }

    public void chooseLanguage() {
        selected().ifPresent(tab -> {
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Language", owner(), true));
            dialog.setContent(new LanguagePicker(tab.session().style(), style -> { dialog.close(); tab.session().setStyle(style); }));
            dialog.show();
        });
    }

    public void toggleWrap() { selected().ifPresent(tab -> tab.session().setWrap(!tab.session().wrap())); }

    public void toggleComment() {
        selected().ifPresent(tab -> {
            Action action = tab.area().getActionMap().get(RSyntaxTextAreaEditorKit.rstaToggleCommentAction);
            if (action != null) action.actionPerformed(new ActionEvent(tab.area(), ActionEvent.ACTION_PERFORMED, "toggle-comment"));
        });
    }

    public void revert() { selected().ifPresent(tab -> tab.session().revert()); }

    public List<DocumentSession> sessions() { return model.sessions(); }

    public Optional<EditorTab> selected() {
        return Optional.ofNullable(selectedTab);
    }

    String headerTitle(int index) { var session = editorTabs().get(index).session(); return (session.dirty() ? "\u25cf " : "") + session.name(); }
    String headerDetail(int index) { return model.detail(editorTabs().get(index).session()); }
    boolean showingEmpty() { return showingEmpty; }

    JPopupMenu menu() {
        var menu = new JPopupMenu();
        for (Command command : commands) menu.add(EditorTab.menuItem(command));
        return menu;
    }

    /**
     * The closing guard (spec 2.3), asked when the window closes and when Jasper quits: waits for running saves,
     * then asks about unsaved tabs, and saves them all when asked to. Re-checks after each wait, since edits made
     * during it can leave a tab saving again or dirty again, rather than trusting a single pass to still hold.
     * Refuses while it is already asking or waiting: a quit or close that arrives during its prompt or wait
     * would otherwise wait on saves that cannot finish until the outer prompt returns.
     */
    boolean mayClose() { return mayClose(editorTabs()); }
    private boolean mayClose(List<EditorTab> closing) {
        if (guarding) {
            if (selectedTab != null) hosted.get(selectedTab).select();
            return false;
        }
        guarding = true;
        try {
            while (true) {
                List<DocumentSession> running = closing.stream().map(EditorTab::session).filter(model.sessions()::contains).filter(DocumentSession::saving).toList();
                if (!running.isEmpty()) {
                    // Waits only on the saves already in flight; it starts none of its own, so a tab whose save
                    // finishes and is then edited again is not silently written without asking.
                    List<CompletableFuture<Boolean>> inFlight = running.stream().map(session -> session.pendingSave().orElseThrow()).toList();
                    if (!await.apply(saving(running.size()), allSucceeded(inFlight))) return false;
                    continue;
                }
                List<DocumentSession> dirty = closing.stream().map(EditorTab::session).filter(model.sessions()::contains).filter(DocumentSession::dirty).toList();
                if (dirty.isEmpty()) return true;
                String answer = ask.apply(new Question("Unsaved changes", unsaved(dirty), List.of("Save All", "Discard", "Cancel")));
                if ("Discard".equals(answer)) return true;
                if (!"Save All".equals(answer)) return false;
                var cancelled = new AtomicBoolean();
                if (!await.apply(saving(dirty.size()), saveSessions(dirty, cancelled))) {
                    // Saves already running finish; the ones still queued do not start.
                    cancelled.set(true);
                    return false;
                }
                // Loops back rather than returning true: edits typed during the non-modal wait may have left a
                // tab dirty again, and that must be asked about too.
            }
        } finally {
            guarding = false;
        }
    }

    private void closed() {
        ticker.stop();
        noticeTimer.stop();
        for (DocumentSession session : model.sessions()) session.cancelLoad();
        onClosed.run();
    }

    private CompletableFuture<Boolean> saveSession(DocumentSession session) {
        Path created = newFiles.get(session);
        if (created != null && session.untitled())
            return session.saveAs(new LocalDocument(created, context.background())).thenApply(saved -> {
                if (saved) newFiles.remove(session);
                return saved;
            });
        return session.untitled() ? saveAsSession(session) : session.save();
    }

    /** The tabs as Vim's buffers (Vim spec 6.2): numbered, and saved, closed, selected and opened the Editor's way. */
    private VimHost.Documents documents() {
        return new VimHost.Documents(this::buffers,
            number -> tabNumbered(number).map(tab -> saveSession(tab.session())).orElse(CompletableFuture.completedFuture(false)),
            (number, file) -> tabNumbered(number).map(tab -> tab.session().saveAs(new LocalDocument(file, context.background())))
                .orElse(CompletableFuture.completedFuture(false)),
            (number, discard) -> tabNumbered(number).ifPresent(tab -> {
                if (discard) discarding.add(tab);
                closeTab(tab);
            }),
            number -> tabNumbered(number).ifPresent(tab -> select(tab.session())),
            number -> tabNumbered(number).ifPresent(tab -> tab.session().reload()),
            (editor, file) -> {
                WindowOwner owner = tabOf(editor).map(hosted::get).map(WindowOwner.class::cast).orElseGet(this::owner);
                if (file.isEmpty()) newUntitled(owner);
                else if (java.nio.file.Files.exists(file.get())) openLocal(file.get(), owner);
                else newFile(file.get(), owner);
            },
            EditorWindow::readText);
    }

    private List<VimHost.Buffer> buffers(dev.jasper.editor.vim.VimEditor editor) {
        var current = tabOf(editor).orElse(null);
        return editorTabs().stream().map(tab -> {
            DocumentSession session = tab.session();
            Optional<Path> file = localFile(session).or(() -> Optional.ofNullable(newFiles.get(session)));
            return new VimHost.Buffer(numbers.get(session), session.location(), file, tab == current, tab == previous && tab != current,
                session.dirty(), session.untitled() && !newFiles.containsKey(session), tab.area().getCaretLineNumber() + 1);
        }).toList();
    }

    private Optional<EditorTab> tabNumbered(int number) {
        return editorTabs().stream().filter(tab -> numbers.get(tab.session()) == number).findFirst();
    }

    private Optional<EditorTab> tabOf(dev.jasper.editor.vim.VimEditor editor) {
        return editorTabs().stream().filter(tab -> tab.vimEditor().orElse(null) == editor).findFirst();
    }

    /** {@code :e newfile}: an empty tab named after the file, which its first save creates. */
    private void newFile(Path file, WindowOwner owner) {
        origin = owner;
        var ref = new java.util.concurrent.atomic.AtomicReference<WindowOwner>();
        var session = DocumentSession.untitled(String.valueOf(file.getFileName()), sessionEnvironment(ref));
        newFiles.put(session, file);
        add(session, ref);
        select(session);
    }

    /** {@code :r}: a local file's text, decoded as the Editor decodes it; empty when it cannot be read. */
    private static Optional<String> readText(Path file) {
        try {
            return Optional.of(dev.jasper.editor.text.TextCodec.decode(java.nio.file.Files.readAllBytes(file)).text());
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    private CompletableFuture<Boolean> saveAsSession(DocumentSession session) {
        Optional<Path> initial = localFile(session).or(() -> Optional.of(Path.of(System.getProperty("user.home"), session.name())));
        Optional<Path> chosen = context.windows().chooseSaveFile(owner(session), "Save As", initial);
        if (chosen.isEmpty()) return CompletableFuture.completedFuture(false);
        if (model.find(LocalDocument.key(chosen.get())).filter(other -> other != session).isPresent()) {
            ask.apply(new Question("Already open", chosen.get() + " is open in another tab.", List.of("OK")));
            return CompletableFuture.completedFuture(false);
        }
        return session.saveAs(new LocalDocument(chosen.get(), context.background()));
    }

    /**
     * One after another, so each conflict prompt appears on its own; true only when every save succeeded. Once
     * {@code cancelled} is set, or for a tab closed meanwhile, a step starts no save; a step that throws fails.
     */
    private CompletableFuture<Boolean> saveSessions(List<DocumentSession> pending, AtomicBoolean cancelled) {
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
        for (DocumentSession session : pending)
            chain = chain.thenCompose(ok -> {
                var step = new CompletableFuture<Boolean>();
                environment.ui().execute(() -> {
                    if (cancelled.get() || !model.sessions().contains(session)) {
                        step.complete(ok);
                        return;
                    }
                    try {
                        session.pendingSave()
                            .orElseGet(() -> session.dirty() ? saveSession(session) : CompletableFuture.completedFuture(true))
                            .whenComplete((saved, error) -> step.complete(ok && error == null && Boolean.TRUE.equals(saved)));
                    } catch (RuntimeException failure) {
                        // Completes the step so the chain, and the wait on it, cannot hang.
                        step.complete(false);
                    }
                });
                return step;
            });
        return chain;
    }

    /** Combines saves already in flight without starting any new ones; true only when every one succeeds. */
    private static CompletableFuture<Boolean> allSucceeded(List<CompletableFuture<Boolean>> saves) {
        CompletableFuture<Boolean> combined = CompletableFuture.completedFuture(true);
        for (CompletableFuture<Boolean> save : saves)
            combined = combined.thenCombine(save.handle((saved, error) -> error == null && Boolean.TRUE.equals(saved)), (a, b) -> a && b);
        return combined;
    }

    private void add(DocumentSession session, java.util.concurrent.atomic.AtomicReference<WindowOwner> ownerRef) {
        EditorSettings settings = environment.settings().get();
        session.setWrap(settings.softWrap());
        var tab = new EditorTab(session, this::closeTab, environment.timers());
        tab.apply(settings, theme, context.appearance().variant(), context.appearance().editorPalette());
        tab.vim(vimOn() ? Optional.of(vim) : Optional.empty());
        var tabCommands = commands.stream().map(command -> new Command(command.id(), command.title(), command.stroke(), command.bound(),
            () -> { activate(tab); command.run().run(); })).toList();
        tab.bind(tabCommands);
        tab.addToPopup(tabCommands.stream().filter(command -> POPUP.contains(command.id())).toList());
        tab.area().setTransferHandler(new FileDrop(tab.area().getTransferHandler(), this::openLocalFiles));
        tab.area().addCaretListener(event -> { if (selected().orElse(null) == tab) refreshStatus(); });
        session.addListener(() -> {
            refreshHeaders();
            if (selected().orElse(null) == tab) refreshStatus();
        });
        model.add(session);
        numbers.put(session, nextNumber++);
        var handle = context.windows().openTab(new ContentSpec("document:" + java.util.UUID.randomUUID(), session.name()), origin, tab);
        hosted.put(tab, handle); ownerRef.set(handle);
        handle.setFocusTarget(tab.area()::requestFocusInWindow);
        handle.onActive(active -> { if (active) activate(tab); });
        handle.onClosed(() -> remove(tab));
        var descriptions = new ArrayList<dev.jasper.sdk.ui.ContentCommand>();
        for (var command : commands) {
            if (command.id().equals("next-tab") || command.id().equals("previous-tab")) continue;
            descriptions.add(new dev.jasper.sdk.ui.ContentCommand(command.id(), command.title(), command.stroke().orElse(null), true,
                () -> { activate(tab); command.run().run(); }));
        }
        descriptions.add(new dev.jasper.sdk.ui.ContentCommand("copy", "Copy", KeyNames.stroke(KeyEvent.VK_C, 0), true, tab.area()::copy));
        descriptions.add(new dev.jasper.sdk.ui.ContentCommand("paste", "Paste", KeyNames.stroke(KeyEvent.VK_V, 0), true, tab.area()::paste));
        handle.setCommands(descriptions);
        activate(tab);
        refresh();
    }

    private void remove(EditorTab tab) {
        if (hosted.remove(tab) == null) return;
        tab.dispose(); tab.session().cancelLoad(); model.remove(tab.session());
        numbers.remove(tab.session());
        newFiles.remove(tab.session());
        discarding.remove(tab);
        if (previous == tab) previous = null;
        if (selectedTab == tab) selectedTab = hosted.keySet().stream().findFirst().orElse(null);
        if (selectedTab != null) activate(selectedTab);
        refresh();
    }
    private void select(DocumentSession session) {
        for (var entry : hosted.entrySet()) if (entry.getKey().session() == session) { activate(entry.getKey()); entry.getValue().select(); return; }
    }
    private void activate(EditorTab tab) {
        if (!hosted.containsKey(tab)) return;
        if (selectedTab != tab && selectedTab != null) {
            selectedTab.remove(findBar);
            previous = selectedTab;
        }
        selectedTab = tab; tab.add(findBar, BorderLayout.SOUTH); tab.revalidate(); refreshStatus();
    }
    private void step(int delta) {
        var all = editorTabs(); if (all.size() > 1) select(all.get(Math.floorMod(all.indexOf(selectedTab) + delta, all.size())).session());
    }
    private List<EditorTab> editorTabs() { return List.copyOf(hosted.keySet()); }
    private void refresh() { showingEmpty = hosted.isEmpty(); refreshHeaders(); refreshStatus(); }

    private void refreshHeaders() {
        for (var entry : hosted.entrySet()) {
            DocumentSession session = entry.getKey().session();
            entry.getValue().setPresentation(new ContentPresentation(session.name(), model.detail(session),
                session.host().isPresent() ? serverIcon : fileIcon, session.dirty(), session.location(), session.host().orElse("")));
        }
    }

    private void refreshStatus() {
        status.display(selected(), environment.settings().get().tabSize(), vimOn());
        if (selectedTab != null && hosted.containsKey(selectedTab)) {
            var tab = selectedTab;
            hosted.get(tab).setStatus(status.items().stream().map(item -> new dev.jasper.sdk.ui.ContentStatus(
                item.text(), item.tooltip(), item.run() == null ? null : () -> {
                    if (!hosted.containsKey(tab)) return;
                    activate(tab); item.run().run();
                })).toList());
        }
        if (environment.timers() && selected().flatMap(tab -> tab.session().notice()).isPresent()) noticeTimer.restart();
    }

    private static Command command(String id, String title, int keyCode, int extraModifiers, Runnable run) {
        return new Command(id, title, Optional.of(KeyNames.stroke(keyCode, extraModifiers)), true, run);
    }

    private static Optional<Path> localFile(DocumentSession session) {
        return session.document().filter(LocalDocument.class::isInstance).map(document -> ((LocalDocument) document).file());
    }

    private static String unsaved(List<DocumentSession> dirty) {
        if (dirty.size() == 1) return dirty.getFirst().name() + " has unsaved changes.";
        StringBuilder message = new StringBuilder("These files have unsaved changes:");
        for (DocumentSession session : dirty.subList(0, Math.min(10, dirty.size()))) message.append("\n  ").append(session.name());
        if (dirty.size() > 10) message.append("\n  and ").append(dirty.size() - 10).append(" more");
        return message.toString();
    }

    private static String saving(int count) { return count == 1 ? "Saving 1 file…" : "Saving " + count + " files…"; }

}
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 166 tests pass.

Run:

```bash
./gradlew verifyPluginArchitecture -q
```

Expected: nothing is printed about the Editor (warnings from other plugins' sources may appear; they are not this plan's).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/vim-v2b
git add plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java
git commit -m "Give Vim the Editor's tabs as numbered buffers

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: 'number' and 'relativenumber'

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/LineNumbers.java`
- Replace: `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/LineNumbersTest.java` (new)

**Interfaces:**
- Produces: the window-local options 'number' ('nu', on by default, as the Editor shows line numbers) and 'relativenumber' ('rnu', off).

`LineNumbers` finds the text area's `RTextScrollPane` (also later, through a hierarchy listener), remembers whether it showed line numbers and its formatter, and on every option change shows line numbers while either option is on and installs its own `LineNumberFormatter` while 'relativenumber' is on. The formatter gives the cursor line its own number (with 'number') or 0, and every other line its distance from the cursor, a closed fold counting as one line. RSyntaxTextArea repaints only the lines whose highlight changes when the caret moves, so `LineNumbers` repaints the gutter when the cursor changes line. When Vim turns off, the gutter is as it was.

- [ ] **Step 1: Write the failing test**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/LineNumbersTest.java`:

```java
package dev.jasper.editor.vim;

import javax.swing.SwingUtilities;
import org.fife.ui.rtextarea.LineNumberList;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** 'number' and 'relativenumber' on the gutter of the text area's scroll pane. */
class LineNumbersTest {
    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    @Test void theGutterFollowsTheOptionsAndIsPutBack() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("a\nb\nc\nd\ne\n");
            area.setCaretPosition(0);
            var pane = new RTextScrollPane(area);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var gutter = pane.getGutter();
            assertThat(pane.getLineNumbersEnabled()).as("'number' starts on").isTrue();
            type(editor, ":set rnu<CR>jj");
            var formatter = gutter.getLineNumberFormatter();
            assertThat(formatter.format(3)).as("the cursor line with 'number'").isEqualTo("3");
            assertThat(formatter.format(1)).isEqualTo("2");
            assertThat(formatter.format(5)).isEqualTo("2");
            type(editor, ":set nonu<CR>");
            assertThat(formatter.format(3)).as("the cursor line without 'number'").isEqualTo("0");
            assertThat(pane.getLineNumbersEnabled()).isTrue();
            type(editor, ":set nornu<CR>");
            assertThat(pane.getLineNumbersEnabled()).as("neither option").isFalse();
            type(editor, ":set rnu<CR>");
            editor.close();
            assertThat(pane.getLineNumbersEnabled()).as("Vim off: as before").isTrue();
            assertThat(gutter.getLineNumberFormatter()).isSameAs(LineNumberList.DEFAULT_LINE_NUMBER_FORMATTER);
        });
    }

    @Test void aClosedFoldCountsAsOneLine() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("class A {\n  void f() {\n    x();\n  }\n  int y;\n}\n");
            area.setCaretPosition(0);
            var pane = new RTextScrollPane(area);
            area.setSyntaxEditingStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA);
            area.setCodeFoldingEnabled(true);
            area.getFoldManager().reparse();
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            type(editor, "jzc:set rnu<CR>gg");
            var formatter = pane.getGutter().getLineNumberFormatter();
            assertThat(formatter.format(2)).isEqualTo("1");
            assertThat(formatter.format(5)).as("lines 2 to 4 are one closed fold").isEqualTo("2");
        });
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.LineNumbersTest'
```

Expected: `theGutterFollowsTheOptionsAndIsPutBack` fails: `:set rnu` is `E518: Unknown option`, so the gutter still shows plain line numbers (`1` where `2` is expected).

- [ ] **Step 3: Write `LineNumbers`, add the options and install it**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/LineNumbers.java`:

```java
package dev.jasper.editor.vim;

import java.awt.event.HierarchyListener;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretListener;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.LineNumberFormatter;
import org.fife.ui.rtextarea.LineNumberList;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * 'number' and 'relativenumber' on the text area's gutter (spec 5.4). The gutter shows line numbers while either is
 * on; with 'relativenumber' each line shows its distance from the cursor line, counting a closed fold as one line,
 * and the cursor line shows its own number with 'number' and 0 without. When Vim turns off, the gutter is put back
 * as it was.
 */
final class LineNumbers {
    private final VimEditor editor;
    private final VimTextArea area;
    private RTextScrollPane pane;
    private boolean originalEnabled;
    private LineNumberFormatter originalFormatter;
    private boolean closed;
    private int shownLine = -1;
    private final HierarchyListener moved = event -> find();
    private final CaretListener caret = event -> caretMoved();
    private final LineNumberFormatter relative = new LineNumberFormatter() {
        @Override public String format(int lineNumber) { return LineNumbers.this.format(lineNumber); }

        @Override public int getMaxLength(int maxLineNumber) { return LineNumberList.DEFAULT_LINE_NUMBER_FORMATTER.getMaxLength(maxLineNumber); }
    };

    LineNumbers(VimEditor editor, VimTextArea area) {
        this.editor = editor;
        this.area = area;
        area.addHierarchyListener(moved);
        area.addCaretListener(caret);
        editor.options().onChanged(this::apply);
        find();
    }

    /** The text area may be put into its scroll pane after Vim turns on. */
    private void find() {
        if (closed) return;
        RTextScrollPane found = (RTextScrollPane) SwingUtilities.getAncestorOfClass(RTextScrollPane.class, area);
        if (found == pane) return;
        restore();
        pane = found;
        if (pane == null) return;
        originalEnabled = pane.getLineNumbersEnabled();
        originalFormatter = pane.getGutter().getLineNumberFormatter();
        apply();
    }

    /** The options changed: the gutter follows them. */
    void apply() {
        if (closed || pane == null) return;
        Options options = editor.options();
        boolean number = options.flag("number"), relativeNumber = options.flag("relativenumber");
        pane.setLineNumbersEnabled(number || relativeNumber);
        Gutter gutter = pane.getGutter();
        gutter.setLineNumberFormatter(relativeNumber ? relative : originalFormatter);
        gutter.repaint();
    }

    /** With 'relativenumber', every number changes when the cursor changes line. */
    private void caretMoved() {
        if (closed || pane == null || !editor.options().flag("relativenumber")) return;
        int line = editor.cursor().line;
        if (line == shownLine) return;
        shownLine = line;
        pane.getGutter().repaint();
    }

    /** @return what 'relativenumber' shows for a line (1-based) */
    String format(int lineNumber) {
        int cursor = editor.cursor().line + 1;
        if (lineNumber == cursor) return editor.options().flag("number") ? Integer.toString(lineNumber) : "0";
        int from = Math.min(lineNumber, cursor), to = Math.max(lineNumber, cursor);
        Folds folds = editor.folds();
        int distance = 0;
        for (int line = from + 1; line <= to; line++) {
            int[] fold = folds.closed(line);
            if (fold == null || fold[0] == line) distance++;
        }
        return Integer.toString(distance);
    }

    /** Vim turned off: the gutter is as it was. */
    void close() {
        restore();
        closed = true;
        area.removeHierarchyListener(moved);
        area.removeCaretListener(caret);
    }

    private void restore() {
        if (pane == null) return;
        pane.getGutter().setLineNumberFormatter(originalFormatter);
        pane.setLineNumbersEnabled(originalEnabled);
        pane = null;
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
        new Def("number", "nu", Type.BOOLEAN, true, true),
        new Def("relativenumber", "rnu", Type.BOOLEAN, false, true),
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

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
    private final Folds folds;
```

with:

```java
    private final Folds folds;
    private final LineNumbers lineNumbers;
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
        this.viewport = new ViewportSync(this, area);
```

with:

```java
        this.viewport = new ViewportSync(this, area);
        this.lineNumbers = new LineNumbers(this, area);
```

In `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java`, replace:

```java
        viewport.close();
```

with:

```java
        viewport.close();
        lineNumbers.close();
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 168 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1569 tests pass.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/vim-v2b
git add plugins/editor/src/main/java/dev/jasper/editor/vim plugins/editor/src/test/java/dev/jasper/editor/vim/LineNumbersTest.java
git commit -m "Add 'number' and 'relativenumber' on the gutter

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: `<Tab>` completion on the command line

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/vim/Completion.java`
- Replace: `plugins/editor/src/main/java/dev/jasper/editor/vim/CommandLine.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/CompletionTest.java` (new); `plugins/editor/src/test/resources/vim/corpus/ex-complete.case` (new)

**Interfaces:**
- Consumes: Task 1's `Ex.skipRange`, `ExFiles.folder`/`resolve` and `VimHost.Documents.buffers`; Task 3's options.
- Produces: `CommandLine.completer(Function<String, Completion.Result>)`; `Completion.complete(VimEditor, String before)` returning `Completion.Result(start, matches, completes)`.

`Completion` reads the text before the cursor as set_one_cmd_context does: after the range, a partly typed command name completes to the commands Jasper runs; after `:set`, `:setl` and `:setg`, an option name (with `no` or `inv` for a boolean) or, right after `=`, its current value; after `:e`, `:w`, `:r`, `:sav`, `:tabnew`, `:tabe`, `:wq`, `:x` and `:exi`, a path (from the current file's folder when relative; folders end with `/`; spaces are escaped; hidden files only for a name starting with a dot); after `:b`, the names of the tabs that hold the text. Elsewhere nothing completes, and `<Tab>` types a tab, as Vim does. `CommandLine` cycles as 'wildmode' "full" without 'wildmenu': the first match, the next on each `<Tab>`, back to the typed word after the last; `<S-Tab>` goes the other way; a single match stays; no match rings the bell. `/` and `?` keep typing the tab.

- [ ] **Step 1: Write the failing tests**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/CompletionTest.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.VimHost.Buffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/** {@code <Tab>} on the {@code :} command line where Vim's candidates differ from Jasper's or need files. */
class CompletionTest {
    @TempDir Path folder;

    private VimEditor editor() {
        Path file = folder.resolve("main.txt");
        var documents = new VimHost.Documents(
            editor -> List.of(new Buffer(1, file.toString(), Optional.of(file), true, false, false, false, 1),
                new Buffer(2, "/elsewhere/notes todo.md", Optional.of(Path.of("/elsewhere/notes todo.md")), false, false, false, false, 1),
                new Buffer(3, "Untitled 1", Optional.empty(), false, false, false, true, 1)),
            number -> CompletableFuture.completedFuture(true), (number, target) -> CompletableFuture.completedFuture(true),
            (number, discard) -> { }, number -> { }, number -> { }, (editor, target) -> { }, path -> Optional.empty());
        var area = new VimTextArea();
        area.setText("x\n");
        return new VimEditor(new Vim(new VimHost(r -> Optional.empty(), (r, t) -> { }, documents)), area, EditorDefaults.VIM);
    }

    private static List<String> matches(VimEditor editor, String line) { return Completion.complete(editor, line).matches(); }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    @Test void commandNamesAreTheCommandsJasperRuns() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor();
            assertThat(matches(editor, "tabn")).containsExactly("tabnew", "tabnext");
            assertThat(matches(editor, "  :3,$de")).containsExactly("delete", "delmarks");
            assertThat(Completion.complete(editor, "3,$de").start()).isEqualTo(3);
            assertThat(matches(editor, "ab")).as("Vim's :abbreviate is not Jasper's").isEmpty();
            assertThat(Completion.complete(editor, "s/a/b").completes()).as("nothing completes in :s").isFalse();
        });
    }

    @Test void optionsTheirNegationsAndTheirValues() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor();
            assertThat(matches(editor, "set sh")).containsExactly("shiftround", "shiftwidth", "showcmd", "showmode");
            assertThat(matches(editor, "setl nowr")).containsExactly("nowrap", "nowrapscan");
            assertThat(matches(editor, "set invhl")).containsExactly("invhlsearch");
            assertThat(matches(editor, "set a")).containsExactly("all", "autoindent");
            assertThat(matches(editor, "set ts=")).containsExactly("8");
            assertThat(Completion.complete(editor, "set ts=").start()).isEqualTo(7);
            assertThat(matches(editor, "set et=")).as("a boolean has no value").isEmpty();
            assertThat(matches(editor, "set sw=4 ts=")).containsExactly("8");
        });
    }

    @Test void pathsFromTheCurrentFilesFolder() throws Exception {
        Files.createDirectories(folder.resolve("src/deep"));
        Files.writeString(folder.resolve("src/one.java"), "");
        Files.writeString(folder.resolve("src/two words.txt"), "");
        Files.writeString(folder.resolve(".hidden"), "");
        Files.writeString(folder.resolve("main.txt"), "");
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor();
            assertThat(matches(editor, "e ")).containsExactly("main.txt", "src/");
            assertThat(matches(editor, "e .")).containsExactly(".hidden");
            assertThat(matches(editor, "w src/")).containsExactly("src/deep/", "src/one.java", "src/two\\ words.txt");
            assertThat(matches(editor, "r src/t")).containsExactly("src/two\\ words.txt");
            assertThat(matches(editor, "tabnew " + folder + "/m")).containsExactly(folder + "/main.txt");
            assertThat(matches(editor, "e nowhere/")).isEmpty();
        });
    }

    @Test void bufferNamesThatHoldTheText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor();
            assertThat(matches(editor, "b todo")).containsExactly("/elsewhere/notes\\ todo.md");
            assertThat(matches(editor, "b ti")).containsExactly("Untitled\\ 1");
        });
    }

    @Test void tabCyclesAndShiftTabGoesBack() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor();
            type(editor, ":tabn<Tab>");
            assertThat(editor.commandLineText()).contains(":tabnew");
            type(editor, "<Tab>");
            assertThat(editor.commandLineText()).contains(":tabnext");
            type(editor, "<Tab>");
            assertThat(editor.commandLineText()).as("back to what was typed").contains(":tabn");
            type(editor, "<S-Tab>");
            assertThat(editor.commandLineText()).contains(":tabnext");
            type(editor, "<C-u>zzz");
            int bells = editor.bells();
            type(editor, "<Tab>");
            assertThat(editor.bells()).as("nothing matches").isEqualTo(bells + 1);
            assertThat(editor.commandLineText()).contains(":zzz");
            type(editor, "<Esc>/a<Tab>");
            assertThat(editor.commandLineText()).as("a search types the tab").contains("/a\t");
        });
    }
}
```

Create `plugins/editor/src/test/resources/vim/corpus/ex-complete.case`:

```text
# Ex command line: Tab completion (V2b). Command names and options Jasper has; Vim has more, so only cases where both complete the same way.

=== tab completes a command name
text:
  pear
  apple
  fig
cursor: 1,1
keys: :sor<Tab><CR>
--- vim 9.1
expect-text:
  apple
  fig
  pear
expect-cursor: 1,1
expect-mode: normal

=== tab completes after a range
text:
  pear
  apple
  fig
cursor: 1,1
keys: :1,2sor<Tab><CR>
--- vim 9.1
expect-text:
  apple
  pear
  fig
expect-cursor: 1,1
expect-mode: normal

=== tab cycles through the matches
text:
  one
  two
cursor: 1,1
keys: 2Gmx:mar<Tab><Tab> x<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
expect-cursor: 2,1
expect-mode: normal
expect-output:
  mark line  col file/text
   x      2    0 two

=== tab comes back to what was typed
text:
  one
  two
cursor: 1,1
keys: :mar<Tab><Tab><Tab><CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
expect-cursor: 1,1
expect-mode: normal
expect-output:
  E471: Argument required

=== shift tab goes backwards
text:
  one
  two
cursor: 1,1
keys: 2Gmx:mar<S-Tab> x<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
expect-cursor: 2,1
expect-mode: normal
expect-output:
  mark line  col file/text
   x      2    0 two

=== tab completes an option name
text:
  one
  two
cursor: 1,1
keys: :set shiftw<Tab>=2<CR>>>
--- vim 9.1
expect-text:
    one
  two
expect-cursor: 1,3
expect-mode: normal

=== tab completes an option value
text:
  one
  two
cursor: 1,1
keys: :set sw=<Tab><BS>3<CR>>>
--- vim 9.1
expect-text:
     one
  two
expect-cursor: 1,4
expect-mode: normal

=== tab completes a no option
text:
  one
  two
cursor: 1,1
keys: :set noet<Tab>?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
expect-cursor: 1,1
expect-mode: normal
expect-output:
  noexpandtab

=== tab completes an inv option
text:
  one
  two
cursor: 1,1
keys: :set invet<Tab><CR>:set et?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
expect-cursor: 1,1
expect-mode: normal
expect-output:
    expandtab

=== tab with nothing to complete
text:
  one
  two
cursor: 1,1
keys: :s/o/0<Tab>/<CR>
--- vim 9.1
expect-text:
  0	ne
  two
expect-cursor: 1,1
expect-mode: normal

=== tab with no match keeps the text
text:
  one
  two
cursor: 1,1
keys: :sorx<Tab><BS><CR>
--- vim 9.1
expect-text:
  one
  two
expect-cursor: 1,1
expect-mode: normal

=== tab then more typing
text:
  pear
  apple
  fig
cursor: 1,1
keys: :so<Tab>!<CR>
--- vim 9.1
expect-text:
  pear
  fig
  apple
expect-cursor: 1,1
expect-mode: normal

=== tab in the middle of the line completes before the cursor
text:
  pear
  apple
  fig
cursor: 1,1
keys: :sor!<Left><Tab><CR>
--- vim 9.1
expect-text:
  pear
  fig
  apple
expect-cursor: 1,1
expect-mode: normal

=== gt with one tab stays
text:
  one
  two
cursor: 1,1
keys: gtx
--- vim 9.1
expect-text:
  ne
  two
expect-cursor: 1,1
expect-mode: normal

=== gT with one tab stays
text:
  one
  two
cursor: 1,1
keys: gTx
--- vim 9.1
expect-text:
  ne
  two
expect-cursor: 1,1
expect-mode: normal

=== relativenumber starts off
text:
  one
  two
cursor: 1,1
keys: :set rnu?<CR>
output: yes
--- vim 9.1
expect-text:
  one
  two
expect-cursor: 1,1
expect-mode: normal
expect-output:
  norelativenumber
```

- [ ] **Step 2: Run them to see them fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.CompletionTest'
```

Expected: compilation fails: `Completion` does not exist.

- [ ] **Step 3: Write `Completion`, replace `CommandLine` and give `:` its completer**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Completion.java`:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.VimHost.Buffer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * What {@code <Tab>} completes on the {@code :} command line (spec 6.1), as Vim's set_one_cmd_context decides: a
 * command name (among the commands Jasper runs), an option name or its value after {@code :set}, a path after the
 * file commands, and a tab's name after {@code :b}. Matches are sorted; the command line cycles through them.
 */
final class Completion {
    private Completion() { }

    /**
     * @param start where the completed word starts in the text
     * @param matches the words that may replace it, sorted; empty when nothing matches
     * @param completes false where nothing completes (Vim's EXPAND_NOTHING), where {@code <Tab>} types a tab
     */
    record Result(int start, List<String> matches, boolean completes) {
        Result(int start, List<String> matches) { this(start, matches, true); }

        /** Nothing completes here: {@code <Tab>} types a tab. */
        static final Result NOTHING = new Result(0, List.of(), false);
        /** Something completes here, but nothing matches: the bell rings. */
        static final Result NO_MATCH = new Result(0, List.of(), true);
    }

    private static final List<String> FILE_COMMANDS = List.of("edit", "write", "read", "saveas", "tabnew", "tabedit", "wq", "xit", "exit");

    /**
     * @param editor the editor the command line is in
     * @param before the command line's text before the cursor
     * @return what may replace the word before the cursor
     */
    static Result complete(VimEditor editor, String before) {
        int i = 0, n = before.length();
        while (i < n && (Ex.isWhite(before.charAt(i)) || before.charAt(i) == ':')) i++;
        i = Ex.skipRange(before, i);
        int nameStart = i;
        while (i < n && Character.isLetter(before.charAt(i)) && before.charAt(i) < 128) i++;
        if (i == n) return commands(before.substring(nameStart), nameStart);
        String typed = before.substring(nameStart, i);
        String name = typed.isEmpty() ? null : ExTable.resolve(typed);
        if (name == null) return Result.NO_MATCH;
        if (i < n && before.charAt(i) == '!') i++;
        if (i >= n || !Ex.isWhite(before.charAt(i))) return Result.NOTHING;
        int argStart = Ex.skipWhite(before, i);
        if (name.equals("set") || name.equals("setlocal") || name.equals("setglobal")) return options(editor, before, argStart);
        if (FILE_COMMANDS.contains(name)) return paths(editor, before, argStart);
        if (name.equals("buffer")) return buffers(editor, before, argStart);
        return Result.NOTHING;
    }

    /** Command names: the commands Jasper runs whose full name starts with the typed text. */
    private static Result commands(String prefix, int start) {
        List<String> names = Ex.implemented().stream().map(Ex.Def::name)
            .filter(name -> Character.isLetter(name.charAt(0)) && name.startsWith(prefix)).sorted().toList();
        return new Result(start, names);
    }

    /** After {@code :set}: an option name (with {@code no} or {@code inv} for a boolean), or after {@code =} its value. */
    private static Result options(VimEditor editor, String before, int argStart) {
        int start = before.length();
        while (start > argStart && !Ex.isWhite(before.charAt(start - 1))) start--;
        String word = before.substring(start);
        int equals = firstOf(word, "=:");
        if (equals > 0) {
            String option = word.substring(0, equals);
            if (option.endsWith("+") || option.endsWith("-") || option.endsWith("^")) option = option.substring(0, option.length() - 1);
            if (equals + 1 < word.length()) return Result.NO_MATCH;
            var def = Options.def(option);
            if (def.isEmpty() || def.get().type() == Options.Type.BOOLEAN) return Result.NO_MATCH;
            String value = String.valueOf(editor.options().get(def.get().name())).replace(" ", "\\ ");
            return new Result(start + equals + 1, List.of(value));
        }
        List<String> matches = new ArrayList<>();
        for (Options.Def def : Options.DEFS) {
            if (def.name().startsWith(word)) matches.add(def.name());
            if (def.type() != Options.Type.BOOLEAN) continue;
            for (String prefix : List.of("no", "inv"))
                if (word.startsWith(prefix) && def.name().startsWith(word.substring(prefix.length()))) matches.add(prefix + def.name());
        }
        if ("all".startsWith(word) && !word.isEmpty()) matches.add("all");
        return new Result(start, matches.stream().distinct().sorted().toList());
    }

    private static int firstOf(String text, String characters) {
        for (int i = 0; i < text.length(); i++) if (characters.indexOf(text.charAt(i)) >= 0) return i;
        return -1;
    }

    /**
     * A path: the entries of the typed folder (the current file's folder for a relative one) whose names start with
     * the typed name, hidden ones only when the name starts with a dot; folders end with a slash, spaces are escaped.
     */
    private static Result paths(VimEditor editor, String before, int argStart) {
        String word = before.substring(argStart);
        String unescaped = word.replace("\\ ", " ");
        int slash = unescaped.lastIndexOf('/');
        String folderPart = slash < 0 ? "" : unescaped.substring(0, slash + 1), namePart = unescaped.substring(slash + 1);
        Buffer current = current(editor);
        Path folder = folderPart.isEmpty() ? ExFiles.folder(current) : ExFiles.resolve(folderPart, current);
        List<String> matches = new ArrayList<>();
        try (Stream<Path> entries = Files.list(folder)) {
            for (Path entry : entries.toList()) {
                String name = String.valueOf(entry.getFileName());
                if (!name.startsWith(namePart) || name.startsWith(".") && !namePart.startsWith(".")) continue;
                matches.add((folderPart + name + (Files.isDirectory(entry) ? "/" : "")).replace(" ", "\\ "));
            }
        } catch (IOException | RuntimeException unreadable) {
            return Result.NO_MATCH;
        }
        matches.sort(String::compareTo);
        return new Result(argStart, matches);
    }

    /** After {@code :b}: the names of the tabs that hold the typed text. */
    private static Result buffers(VimEditor editor, String before, int argStart) {
        String word = before.substring(argStart).replace("\\ ", " ");
        List<String> matches = editor.vim().host().documents().buffers().apply(editor).stream().map(Buffer::name)
            .filter(name -> name.toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT))).map(name -> name.replace(" ", "\\ "))
            .sorted().toList();
        return new Result(argStart, matches);
    }

    private static Buffer current(VimEditor editor) {
        return editor.vim().host().documents().buffers().apply(editor).stream().filter(Buffer::current).findFirst().orElseThrow();
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
    /** For {@code :}, what {@code <Tab>} completes; null for {@code /} and {@code ?}, where it types a tab. */
    private java.util.function.Function<String, Completion.Result> completer;
    /** While {@code <Tab>} cycles: the matches, the one shown (-1 for the typed word), where the word starts, the typed word. */
    private List<String> matches;
    private int matchIndex, matchStart;
    private String typedWord;

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
        if (type != ':') completer = null;
        matches = null;
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

    /** @param completer what {@code <Tab>} completes on the next {@code :} line: the matches for the text before the cursor */
    void completer(java.util.function.Function<String, Completion.Result> completer) { this.completer = completer; }

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
        if (!key.is("<Tab>") && !key.is("<S-Tab>")) matches = null;
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
            case "<Tab>" -> {
                if (completer == null) insert("\t");
                else complete(1);
            }
            case "<S-Tab>" -> {
                if (completer == null) editor.bell();
                else complete(-1);
            }
            case "<Up>", "<S-Up>", "<PageUp>" -> recall(-1, key.is("<Up>"));
            case "<Down>", "<S-Down>", "<PageDown>" -> recall(1, key.is("<Down>"));
            default -> editor.bell();
        }
    }

    /**
     * {@code <Tab>} (step 1) and {@code <S-Tab>} (-1), as Vim's 'wildmode' "full" without 'wildmenu': the first match
     * replaces the word before the cursor, each further key the next match, and after the last the typed word comes
     * back; a single match stays. Where nothing completes ({@code :s/a/b}) {@code <Tab>} types a tab; where nothing
     * matches the bell rings.
     */
    private void complete(int step) {
        if (matches == null) {
            String before = text.substring(0, cursor);
            Completion.Result result = completer.apply(before);
            if (!result.completes()) {
                if (step > 0) insert("\t");
                else editor.bell();
                return;
            }
            if (result.matches().isEmpty()) {
                editor.bell();
                return;
            }
            matches = result.matches();
            matchStart = result.start();
            typedWord = before.substring(matchStart);
            matchIndex = step > 0 ? 0 : matches.size() - 1;
        } else if (matches.size() > 1) {
            matchIndex += step;
            if (matchIndex >= matches.size()) matchIndex = -1;
            else if (matchIndex < -1) matchIndex = matches.size() - 1;
        }
        String word = matchIndex < 0 ? typedWord : matches.get(matchIndex);
        text.replace(matchStart, cursor, word);
        cursor = matchStart + word.length();
        historyIndex = history.size();
        if (changed != null) changed.accept(text.toString());
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

In `plugins/editor/src/main/java/dev/jasper/editor/vim/Ex.java`, replace:

```java
        Vim vim = editor.vim();
        editor.commandLine().start(':', vim.commandHistory(), initial, null, text -> {
```

with:

```java
        Vim vim = editor.vim();
        editor.commandLine().completer(before -> Completion.complete(editor, before));
        editor.commandLine().start(':', vim.commandHistory(), initial, null, text -> {
```

- [ ] **Step 4: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.CompletionTest' --tests 'dev.jasper.editor.vim.LineNumbersTest' --tests 'dev.jasper.editor.vim.ExFilesTest' --tests 'dev.jasper.editor.vim.ExTest' --tests 'dev.jasper.editor.vim.SearchPatternsTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 173 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest' --tests 'dev.jasper.editor.vim.VimDocsTest'
```

Expected: 1585 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`.

- [ ] **Step 5: Check the recording (needs `vim` on `PATH`)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: `git status` lists only `ex-complete.case` (`??`), unchanged from what you created: Vim agrees with every expectation.

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/vim-v2b
git add plugins/editor/src/main/java/dev/jasper/editor/vim plugins/editor/src/test/java/dev/jasper/editor/vim/CompletionTest.java plugins/editor/src/test/resources/vim/corpus/ex-complete.case
git commit -m "Complete command names, options, paths and tabs with Tab

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 5: Documentation, the spec amendments and the final check

**Files:**
- Modify: `docs/vim-divergences.md`, `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec

- [ ] **Step 1: List the differences**

In `docs/vim-divergences.md`, replace:

```markdown
- **Tab on the command line** types a tab until plan V2b brings completion.
```

with:

```markdown
- **Tabs are the buffers.** Each Editor tab is a buffer and a tab page. `:e file` opens the file in a tab, or selects
  its tab, where Vim edits it in the window; `:w file` saves the tab as the file, which it then edits, where Vim
  writes a copy; `:q` closes the tab, and closing the last one leaves Jasper running. A tab never has to be written
  before another is shown, so `:e`, `:b` and `:bn` never need `!` ('hidden' as it were), and `:ls` shows every tab as
  loaded and shown (`a`), named by its location. `:w` on an untitled tab asks where to save it, as the Editor's Save
  does, where Vim fails with `E32`. `:e newfile` opens an empty tab its first save creates.
- **Relative file names** start from the current file's folder (the home folder for an untitled tab), as Jasper has no
  current directory.
- **Only whole files are written.** `:2,5w file`, `:w >> file`, `:w !cmd` and `:r !cmd` fail with E319.
- **The write message counts UTF-8.** `"a.txt" 2L, 8B written` counts the text's lines and UTF-8 bytes; Vim counts the
  bytes written, in the file's encoding and line endings.
- **`:e!` starts a new undo history**, as the Editor's reload does; Vim keeps the text before the reload for undo.
- **Completion.** `<Tab>` on the `:` command line completes the commands and options Jasper has, paths after the file
  commands and tab names after `:b`, cycling through the matches as Vim does without 'wildmenu'. Where nothing
  completes (`:s/a/b`) it types a tab, as Vim does, and in a search (`/`, `?`) it always types a tab, where Vim 9.1
  completes words from the text.
- **Line numbers.** 'number' starts on, as the Editor shows line numbers. With 'number' and 'relativenumber' the
  cursor line's number is right-aligned like the others; Vim puts it on the left.
- **Uppercase marks across tabs.** `'A` and `` `A `` select the tab the mark is in and go there; after an operator or
  in Visual mode they fail, where Vim also cannot operate across files.
```

- [ ] **Step 2: Describe the tabs, completion and line numbers in the Editor guide**

In `docs/editor.md`, replace:

```markdown
  [reference](editor-vim-reference.md#ex-commands) lists them. When a command line says several lines (`:reg`,
  `:g/pat/`) they stay above the strip until you type a key; `:` goes straight on to the next command line.
```

with:

```markdown
  [reference](editor-vim-reference.md#ex-commands) lists them. When a command line says several lines (`:reg`,
  `:g/pat/`) they stay above the strip until you type a key; `:` goes straight on to the next command line.
- The tabs are Vim's buffers. `:w` saves through the Editor's Save (`:w file` saves as the file), `:q` closes the tab
  (`:q!` without asking about changes), `:wq`, `:x` and `ZZ` save and close, `:e file` opens a file in a tab (a
  relative name starts from the current file's folder), `:r file` puts a file's lines below the cursor, `:ls` lists
  the tabs with their buffer numbers, and `:b N`, `:bn`, `:tabn`, `gt` and `gT` move between them. `<Tab>` completes
  command names, options after `:set`, file names and tab names. 'relativenumber' shows each line's distance from the
  cursor in the gutter.
```

In `docs/editor.md`, replace:

```markdown
- Not yet: file, buffer and tab commands (`:w`, `:q`, `:e`, `ZZ`), Tab completion on the command line, relative line
  numbers, macros, a vimrc, and emulated plugins such as surround and commentary. They follow in later releases. An
  uppercase mark (`mA`) works within its own tab.
```

with:

```markdown
- Not yet: macros, a vimrc, and emulated plugins such as surround and commentary. They follow in later releases. An
  uppercase mark (`mA`) set in one tab takes you back to that tab (`'A`).
```

- [ ] **Step 3: Add the GUI checks for the user**

Append to `docs/editor-verification.md`:

```markdown

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
```

- [ ] **Step 4: Record the state**

In `docs/STATUS.md`, replace:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V2a** (branch `claude/vim-v2a`, plan
```

with:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V2b** (branch `claude/vim-v2b`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v2b.md`, written 2026-09-28): the Editor's tabs as Vim's buffers — `:w :wq :x :exi :sav :wa :xa :wqa :q :qa
  :quita :e :ene :r :ls :buffers :files :b :bn :bN :bp :tabn :tabN :tabp :tabnew :tabe`, `ZZ`, `ZQ`, `gt`, `gT`,
  through the Editor's save, conflict and close paths (`VimHost.Documents`, implemented by `EditorWindow`); `<Tab>`
  completion of commands, options, paths and tab names; 'number' and 'relativenumber' on the gutter; uppercase marks
  that select their tab. Every file in the plan was compiled and run before the plan was written: 1582 corpus
  cases against Vim 9.1 (17 new), and the tab commands in `ExFilesTest` and `EditorWindowTest`.
  - Not done: execution; GUI checks 61–70 of the [checklist](editor-verification.md) (user-run); plans V3 and V4;
    push.
- **Editor Vim mode, plan V2a** (branch `claude/vim-v2a`, plan
```

- [ ] **Step 5: Amend the spec**

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
as marked **Amended (V2a)**.
```

with:

```markdown
as marked **Amended (V2a)**. Plan V2b (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v2b.md`) amends sections 3.6, 5.4, 6.1, 6.2 and 12, as marked **Amended (V2b)**.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
**Amended (V1b):** until plan V2's buffer commands, an uppercase mark works within its own tab.
```

with:

```markdown
**Amended (V1b):** until plan V2's buffer commands, an uppercase mark works within its own tab. **Amended (V2b):** `'A` and `` `A `` in another tab select the mark's tab and go to it; after an operator or in Visual mode they fail.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
with the current line absolute when `number` is also on. It follows folds, wrap and the theme's gutter colours.
```

with:

```markdown
with the current line absolute when `number` is also on. It follows folds, wrap and the theme's gutter colours. **Amended (V2b):** 'relativenumber' is RSyntaxTextArea's own gutter with a line-number formatter, not a component of its own; the cursor line's number is right-aligned like the others; 'number' starts on, as the Editor shows line numbers.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
(**Amended (V2a):** completion comes with plan V2b; until then `<Tab>` types a tab);
```

with:

```markdown
(**Amended (V2a):** completion comes with plan V2b; until then `<Tab>` types a tab. **Amended (V2b):** Jasper completes the commands and options it has (with `no` and `inv`, and an option's value after `=`), paths after the file commands and tab names after `:b`, cycling as Vim's 'wildmode' "full" without 'wildmenu'; where nothing completes, and in `/` and `?`, `<Tab>` types a tab; `:source`'s paths come with V3);
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
`:q!` discards through the Editor's close path without asking.
```

with:

```markdown
`:q!` discards through the Editor's close path without asking. **Amended (V2b):** each Editor tab is a buffer and a tab page, numbered while it is open; `:e file` opens the file in a tab or selects its tab (a missing file gets an empty tab its first save creates), `:w file` saves the tab as the file, `:q` closes the tab and never quits Jasper, and no command needs `!` to leave a tab with changes. Relative names start from the current file's folder (the home folder for an untitled tab); `%`, `#` and `~` work in them. Also `:sav[eas]`, `:exi[t]`, `:quita[ll]`, `:files`, `:bN[ext]`, `:tabN[ext]`, `:tabe[dit]`, `gt` and `gT`; `:2,5w file`, `:w >>`, `:w !cmd` and `:r !cmd` fail with E319. The corpus cannot record these commands; `ExFilesTest` runs them against a fake set of tabs and `EditorWindowTest` through the Editor.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
`vim.ui` depends on `vim` only.
```

with:

```markdown
`vim.ui` depends on `vim` only. **Amended (V2b):** `VimHost.Documents` (a record of callbacks) is how the engine reaches the Editor's tabs; `ExFiles`, `Completion` and `LineNumbers` join `dev.jasper.editor.vim`, and there is no `RelativeGutter`.
```

- [ ] **Step 6: Run everything**

Run:

```bash
./gradlew check
```

Expected: BUILD SUCCESSFUL (4,135 tests, 0 failures, 0 errors, 5 skipped).

- [ ] **Step 7: Check source hygiene**

Run the snippet in `AGENTS.md` ("Source hygiene"). Expected: no output.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/vim-v2b
git add docs
git commit -m "Document Vim mode V2b: files, buffers and tabs

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
