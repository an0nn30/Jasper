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
