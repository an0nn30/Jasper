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
