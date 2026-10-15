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
            assertThat(tabs.calls).as("relative to the current file's folder").last().isEqualTo("open " + Path.of("sub", "other.txt"));
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
        folder = Path.of("/work").toAbsolutePath(); // :b matches the whole path, as Vim does, and a temporary folder's could hold zz
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
            type(editor, ":b work<CR>");
            assertThat(editor.message()).as("the folder counts too").isEqualTo("E93: More than one match for work");
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
