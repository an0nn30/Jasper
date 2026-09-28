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
